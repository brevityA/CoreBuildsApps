#!/usr/bin/env python3
"""
Core EQ — the measurement → correction → export maths, as runnable reference.

Why this file exists
--------------------
Core EQ's promise is "measure the room with pink noise, build an equaliser".
The risky part of that promise is not the Android UI or the manifest; it is
whether the DSP chain between "microphone samples" and "five numbers an
`android.media.audiofx.Equalizer` will accept" actually produces something
musical. That chain is pure maths, and maths that lives only inside a Kotlin
file cannot be checked in this repository — there is no Android SDK here, and
`tests/test_core_eq_dsp.py` has to run in CI next to `tools/validate.py`.

So the chain is written here first, in numpy, with no Android in sight. The
Kotlin port in `coreeq/` is a translation of these functions, not a
re-derivation. Every constant below carries the reason it has the value it
has; see `docs/research/core-eq-measurement-and-capability-2026-09.md` for the
research each one came from.

What is deliberately in scope
-----------------------------
  * pink-noise and exponential-sine-sweep synthesis (the two stimuli);
  * Welch PSD -> 1/N-octave band reduction (what a graph on a TV should show);
  * target curves (flat / B&K / Harman / house tilt+bass);
  * the correction curve, with the limits that stop it doing damage;
  * peaking-filter fitting, so the result survives export to a parametric EQ;
  * the 5-band collapse for the platform `Equalizer` API, which is the only
    thing most Android TV devices will actually accept.

What is out of scope
--------------------
Phase and time-domain correction. Pink noise gives a magnitude spectrum and
nothing else; an exponential sine sweep gives an impulse response and could,
but a TV loudspeaker's phase at the listening position is dominated by
reflections a 5-band EQ cannot touch. Say so rather than imply otherwise.

Usage
-----
    python tools/core_eq_dsp.py --demo           # print a full worked example
    python tools/core_eq_dsp.py --selftest       # run the built-in invariants
    python tools/core_eq_dsp.py --stimulus pink.wav   # write a 48k pink WAV

The suite convention applies: paste the receipt.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
import wave

import numpy as np

# ---------------------------------------------------------------------------
# Measurement constants
#
# Each one is a decision, and each decision has a source. Do not tune these
# without updating the research doc; they are asserted by
# tests/test_core_eq_dsp.py.
# ---------------------------------------------------------------------------

#: Playback/record rate. 48 kHz is what Android TV audio paths are built
#: around, and it leaves 24 kHz of headroom above the 8 kHz correction ceiling.
FS = 48000

#: Pink noise is the default stimulus: equal energy per octave, so it does not
#: lean on a TV tweeter the way a linear sweep to 20 kHz does.
PINK_SECONDS = 20.0

#: Exponential sine sweep duration, for the optional phase-capable pass.
ESS_SECONDS = 10.0

#: FFT/Welch analysis. 8192 samples at 48 kHz is 170 ms per frame: long enough
#: to resolve 60 Hz with some margin, short enough to average many frames
#: inside a 20 s capture.
NFFT = 8192

#: Welch overlap. 50 % with a Hann window is the textbook choice and keeps the
#: frame count high without biasing the estimate.
NOVERLAP = NFFT // 2

#: Correction band edges.
#:
#: F_MIN is not "as low as we can measure" — it is "as low as an uncalibrated
#: remote microphone can be trusted". Consumer electret capsules roll off hard
#: below ~80 Hz and their sub-40 Hz output is noise, and a correction curve
#: built on that noise becomes a large bass boost, which is the single worst
#: failure mode available to us.
F_MIN = 40.0

#: F_MAX is bounded by the microphone, not by hearing. Measurement work on
#: built-in phone-class mics finds the response usable to roughly 4 kHz and
#: unreliable above it; a remote capsule is not better. 8 kHz keeps the
#: "air" region adjustable by hand while refusing to auto-correct a curve the
#: mic could not have measured.
F_MAX = 8000.0

#: Gain limits, in dB. Asymmetric on purpose: room correction should cut, not
#: boost. Boosting a modal peak costs headroom and drives a small TV speaker
#: into distortion; cutting one costs a little loudness.
MAX_BOOST_DB = 6.0
MAX_CUT_DB = 12.0

#: Slope limit for the correction curve, dB per octave. AutoEq's trick: walk
#: the curve left to right clamping steepness, then right to left, then keep
#: the lower of the two. Without it a narrow 15 dB room null becomes a narrow
#: 15 dB boost, which is a ringing filter and not a fix.
MAX_SLOPE_DB_PER_OCT = 6.0

#: Smoothing applied before the correction decision. 1/3 octave is the
#: resolution at which broad tonal trends separate from non-minimum-phase
#: ripple that no EQ can remove.
CORRECTION_SMOOTHING_OCTAVES = 1.0 / 3.0

#: Filters the parametric export is fitted with. Matches the "8 peaking plus
#: shelves" preset shape that parametric-EQ users already recognise.
PEAKING_FILTERS = 8

#: Peaking-filter Q bounds. High Q + high gain is where ringing lives.
PEAKING_MIN_Q = 0.4
PEAKING_MAX_Q = 4.0


# ---------------------------------------------------------------------------
# Stimuli
# ---------------------------------------------------------------------------

def pink_noise(seconds: float = PINK_SECONDS, fs: int = FS, seed: int = 20260927) -> np.ndarray:
    """Deterministic pink noise, -3 dB/octave, peak-normalised to 0.7.

    Shaping in the frequency domain rather than with a Voss-McCartney cascade,
    because the FFT route is exact about the slope (the cascade is only
    approximately pink) and it is one line to assert in a test.

    The signal is DC-blocked and de-clicked with short raised-cosine ramps so
    the TV's speaker does not get a step at t=0.
    """
    n = int(seconds * fs)
    rng = np.random.default_rng(seed)

    white = rng.standard_normal(n)
    spectrum = np.fft.rfft(white)
    freqs = np.fft.rfftfreq(n, 1.0 / fs)

    # 1/sqrt(f) amplitude scaling == -3 dB/octave. Guard the bin at 0 Hz.
    shape = np.ones_like(freqs)
    nz = freqs > 0
    shape[nz] = np.sqrt(freqs[nz][0] / freqs[nz])
    spectrum *= shape

    pink = np.fft.irfft(spectrum, n=n)
    pink -= pink.mean()

    # 10 ms raised-cosine fade in/out.
    ramp = int(0.010 * fs)
    env = np.ones(n)
    env[:ramp] = 0.5 * (1 - np.cos(np.pi * np.arange(ramp) / ramp))
    env[-ramp:] = env[:ramp][::-1]
    pink *= env

    return pink * (0.7 / np.max(np.abs(pink)))


def sine_sweep(
    seconds: float = ESS_SECONDS,
    fs: int = FS,
    f_start: float = 20.0,
    f_end: float = 20000.0,
) -> tuple[np.ndarray, np.ndarray]:
    """Exponential sine sweep and its time-reversed inverse filter (Farina).

    Returns ``(sweep, inverse)``. Convolve the recorded response with
    ``inverse`` to recover the impulse response, with the harmonic-distortion
    products landing at negative time where they can be discarded.

    The inverse carries a +3 dB/octave amplitude ramp because the sweep's own
    spectrum is pink — it dwells longer at low frequencies — and without that
    ramp the recovered impulse response is not flat.
    """
    n = int(seconds * fs)
    t = np.arange(n) / fs
    k = seconds / math.log(f_end / f_start)

    phase = 2 * math.pi * f_start * k * (np.exp(t / k) - 1.0)
    sweep = np.sin(phase) * 0.7

    # Inverse: time reversal, then an amplitude envelope rising +3 dB/octave
    # (i.e. doubling per octave) across the sweep's own frequency span.
    inverse = sweep[::-1].copy()
    inst_f = f_start * np.exp(t / k)
    envelope = inst_f / inst_f[0]
    inverse *= envelope
    inverse /= np.max(np.abs(inverse))
    return sweep, inverse


# ---------------------------------------------------------------------------
# Spectrum
# ---------------------------------------------------------------------------

def welch_db(signal: np.ndarray, fs: int = FS) -> tuple[np.ndarray, np.ndarray]:
    """Welch periodogram in dB, using a Hann window at 50 % overlap.

    Returns ``(freqs, db)``. Absolute calibration is not attempted: without a
    calibrated microphone there is no honest SPL reference, so the spectrum is
    normalised to its own maximum. Everything downstream is relative.
    """
    window = np.hanning(NFFT)
    step = NFFT - NOVERLAP
    frames = range(0, max(1, len(signal) - NFFT + 1), step)

    acc = None
    count = 0
    for start in frames:
        chunk = signal[start : start + NFFT] * window
        power = np.abs(np.fft.rfft(chunk)) ** 2
        acc = power if acc is None else acc + power
        count += 1

    if acc is None or count == 0:
        freqs = np.fft.rfftfreq(NFFT, 1.0 / fs)
        return freqs, np.full_like(freqs, -np.inf)

    acc /= count
    freqs = np.fft.rfftfreq(NFFT, 1.0 / fs)
    with np.errstate(divide="ignore"):
        db = 10.0 * np.log10(np.maximum(acc, 1e-20))
    db -= db.max()
    return freqs, db


def octave_bands(freqs: np.ndarray, values_db: np.ndarray, n: float = 3.0,
                 f_min: float = 20.0, f_max: float = 20000.0) -> tuple[np.ndarray, np.ndarray]:
    """Reduce a fine spectrum to 1/N-octave bands by in-band power averaging.

    Band edges are ``fc * 2^(±1/(2N))``, and the in-band reduction is an energy
    mean, not a dB mean — averaging decibels is not the same operation and
    quietly flattens peaks.

    The grid is anchored at 1 kHz, the way the nominal 1/3-octave series is.
    Anchoring at the bottom of the range instead drifts: stepping up from 20 Hz,
    the band nearest 1 kHz lands on 1008 Hz and no band is ever exactly 1 kHz,
    which is the one frequency every measurement in this domain normalises to.
    """
    step = 2 ** (1.0 / n)

    centres: list[float] = []
    fc = 1000.0
    while fc >= f_min:
        centres.append(fc)
        fc /= step
    centres.reverse()
    fc = 1000.0 * step
    while fc <= f_max * 1.0001:
        centres.append(fc)
        fc *= step
    centres_arr = np.asarray(centres)

    out = np.empty_like(centres_arr)
    for i, fc in enumerate(centres_arr):
        lo = fc * 2 ** (-1.0 / (2 * n))
        hi = fc * 2 ** (1.0 / (2 * n))
        mask = (freqs >= lo) & (freqs < hi)
        if not mask.any():
            out[i] = -np.inf
            continue
        linear = 10 ** (values_db[mask] / 10.0)
        out[i] = 10.0 * math.log10(max(linear.mean(), 1e-20))
    return centres_arr, out


def smoothing_window(freqs: np.ndarray, octaves: float) -> np.ndarray:
    """Per-bin smoothing span, in bins, for a log-spaced frequency axis."""
    if len(freqs) < 3:
        return np.ones_like(freqs)
    log_f = np.log2(np.maximum(freqs, 1e-9))
    span = np.empty_like(freqs)
    for i, lf in enumerate(log_f):
        half = octaves / 2.0
        lo = np.searchsorted(log_f, lf - half)
        hi = np.searchsorted(log_f, lf + half)
        span[i] = max(hi - lo, 3)
    return span


def smooth_octave(freqs: np.ndarray, values_db: np.ndarray, octaves: float) -> np.ndarray:
    """Moving-average smoothing in energy domain over a fixed octave window."""
    span = smoothing_window(freqs, octaves)
    linear = 10 ** (values_db / 10.0)
    out = np.empty_like(values_db)
    for i in range(len(values_db)):
        w = int(span[i]) // 2
        lo = max(0, i - w)
        hi = min(len(linear), i + w + 1)
        out[i] = 10.0 * math.log10(max(linear[lo:hi].mean(), 1e-20))
    return out


# ---------------------------------------------------------------------------
# Target curves
# ---------------------------------------------------------------------------

def target_curve(kind: str, freqs: np.ndarray, bass_boost_db: float = 0.0,
                 tilt_db_per_oct: float = -1.0, pivot_hz: float = 630.0) -> np.ndarray:
    """A target curve, in dB relative to its value at [pivot_hz].

    ``kind``:
      ``flat``    0 dB everywhere. Honest but rarely what people prefer in a
                  reflective living room.
      ``bk``      Bruel & Kjaer 1974: flat to roughly 160 Hz, then about
                  -6 dB by 20 kHz (~0.9 dB/octave).
      ``harman``  A bass shelf plus a ~1 dB/octave downward tilt — the shape
                  the Harman/Olive preference work points at.
      ``house``   Whatever [bass_boost_db] and [tilt_db_per_oct] say.

    Tilt is expressed per octave around a pivot, which is how the literature
    and every house-curve control express it; 630 Hz is the conventional pivot.
    """
    f = np.asarray(freqs, dtype=float)
    out = np.zeros_like(f)

    if kind == "flat":
        return out

    octaves_from_pivot = np.log2(np.maximum(f, 1e-9) / pivot_hz)

    if kind == "bk":
        # Flat below 160 Hz, then ~-6 dB over 160 Hz -> 20 kHz.
        slope = -6.0 / math.log2(20000.0 / 160.0)
        out = np.where(f <= 160.0, 0.0, slope * np.log2(np.maximum(f, 160.0) / 160.0))
    elif kind == "harman":
        bass = 3.5 * 0.5 * (1.0 - np.tanh((f - 105.0) / 55.0))
        out = bass + tilt_db_per_oct * octaves_from_pivot
    elif kind == "house":
        bass = bass_boost_db * 0.5 * (1.0 - np.tanh((f - 105.0) / 55.0))
        out = bass + tilt_db_per_oct * octaves_from_pivot
    else:
        raise ValueError(f"unknown target curve: {kind!r}")

    # Normalise so the pivot reads 0 dB; targets are shape, not level.
    at_pivot = np.interp(pivot_hz, f, out)
    return out - at_pivot


# ---------------------------------------------------------------------------
# The correction
# ---------------------------------------------------------------------------

def _limit_slope(freqs: np.ndarray, curve: np.ndarray, max_db_per_oct: float) -> np.ndarray:
    """Clamp a curve's steepness in both directions, keeping the lower result.

    The AutoEq regularisation. Left-to-right pass caps rises and falls, then
    right-to-left, then elementwise minimum. This is what stops a 15 dB modal
    null from becoming a 15 dB boost.
    """
    log_f = np.log2(np.maximum(freqs, 1e-9))

    def clamp(direction: int) -> np.ndarray:
        out = curve.copy()
        idx = range(len(curve) - 1) if direction > 0 else range(len(curve) - 1, 0, -1)
        for i in idx:
            j = i + direction
            df = abs(log_f[j] - log_f[i])
            if df <= 0:
                continue
            limit = max_db_per_oct * df
            delta = np.clip(out[j] - out[i], -limit, limit)
            out[j] = out[i] + delta
        return out

    return np.minimum(clamp(1), clamp(-1))


def correction_curve(
    freqs: np.ndarray,
    measured_db: np.ndarray,
    target_db: np.ndarray,
    f_min: float = F_MIN,
    f_max: float = F_MAX,
    max_boost: float = MAX_BOOST_DB,
    max_cut: float = MAX_CUT_DB,
    cut_only: bool = False,
    smooth_octaves: float = CORRECTION_SMOOTHING_OCTAVES,
    max_slope: float = MAX_SLOPE_DB_PER_OCT,
) -> np.ndarray:
    """The correction to apply: what to add to the measured response to reach target.

    Order matters, and each step is here for a reason:

    1. smooth — at 1/3 octave, so the correction chases tonal balance and not
       the ripple that a 5-band EQ cannot represent anyway;
    2. invert the error and clamp to the boost/cut budget;
    3. limit slope, so no narrow feature becomes a ringing filter;
    4. re-centre so the mean correction over the usable band is 0 dB, which
       keeps the result a balance of cuts and lifts rather than a global gain
       change the user would have to undo with the volume control;
    5. clamp again — the shift in step 4 can carry a curve back over its own
       budget, which is exactly the kind of violation a caller would not
       expect from a function that documents a budget;
    6. zero outside [f_min, f_max] — the region the microphone cannot be
       trusted in is the region we refuse to touch.

    Steps 4–6 in that order. Re-centring last, as a first draft did, un-zeroed
    the out-of-band region and pushed the peak past `max_boost`; clamping last
    would have let the slope limiter be defeated by the shift.

    ``cut_only`` skips step 4. Re-centring is precisely what turns a set of
    cuts into a set of boosts, so in cut-only mode the curve stays at or below
    0 dB and the system simply loses loudness — which is the honest cost of
    refusing to boost, and why the export carries a preamp note.
    """
    smoothed = smooth_octave(freqs, measured_db, smooth_octaves)
    raw = -(smoothed - target_db)
    ceiling = 0.0 if cut_only else max_boost

    if cut_only:
        raw = np.minimum(raw, 0.0)
    raw = np.clip(raw, -max_cut, ceiling)

    limited = _limit_slope(freqs, raw, max_slope)

    if not cut_only:
        recentre = (freqs >= max(100.0, f_min)) & (freqs <= min(10000.0, f_max))
        if recentre.any():
            limited = limited - limited[recentre].mean()

    limited = np.clip(limited, -max_cut, ceiling)

    band = (freqs >= f_min) & (freqs <= f_max)
    return _taper_edges(freqs, np.where(band, limited, 0.0), f_min, f_max)


def _taper_edges(freqs: np.ndarray, curve: np.ndarray, f_min: float, f_max: float,
                 octaves: float = 1.0) -> np.ndarray:
    """Fade the correction to zero across the octave at each end of its band.

    Without this the curve steps from a full correction to nothing at exactly
    [f_min] and [f_max]. In a 1/3-octave GraphicEQ that step is one band wide
    and usually inaudible — but it lands at the two frequencies where the
    microphone is *least* trustworthy, which is the worst place to leave a
    discontinuity. A raised-cosine taper over one octave costs nothing and
    makes the correction go out the way it came in.
    """
    out = curve.copy()
    lo_edge = f_min * 2 ** octaves
    hi_edge = f_max / 2 ** octaves

    ramp_lo = (freqs >= f_min) & (freqs < lo_edge)
    if ramp_lo.any():
        t = np.log2(freqs[ramp_lo] / f_min) / octaves
        out[ramp_lo] *= 0.5 * (1 - np.cos(np.pi * t))

    ramp_hi = (freqs > hi_edge) & (freqs <= f_max)
    if ramp_hi.any():
        t = np.log2(f_max / freqs[ramp_hi]) / octaves
        out[ramp_hi] *= 0.5 * (1 - np.cos(np.pi * t))

    return out


# ---------------------------------------------------------------------------
# Peaking filters
# ---------------------------------------------------------------------------

def peaking_magnitude_db(freqs: np.ndarray, fc: float, q: float, gain_db: float) -> np.ndarray:
    """Magnitude response of an RBJ-cookbook peaking filter, in dB.

    Derived from the cookbook coefficients rather than approximated, because
    this is the curve the Kotlin port has to match bit for bit if the exported
    `.txt` and the on-device filter are to agree.

    With ``b0 = 1+alpha*A``, ``b2 = 1-alpha*A``, ``b1 = a1 = -2cos(w0)``,
    ``a0 = 1+alpha/A``, ``a2 = 1-alpha/A`` and ``alpha = sin(w0)/(2Q)``, the
    common ``e^{-jw}`` factors cancel and both numerator and denominator
    collapse to ``(cos w - cos w0)^2 + (k*sin w)^2`` with ``k`` equal to
    ``alpha*A`` above and ``alpha/A`` below. Those are the *squared*
    magnitudes divided by 4, so the ratio is ``|H|^2`` and the conversion to
    decibels is ``10*log10``, not 5. Getting that factor wrong halves every
    gain in the file: a +6 dB filter that ships as +3 dB.

    At ``w = w0`` the ratio is ``A^4`` and the response reads exactly
    ``gain_db``, which is the invariant `tests/test_core_eq_dsp.py` pins.
    """
    f = np.maximum(np.asarray(freqs, dtype=float), 1e-9)
    if abs(gain_db) < 1e-9:
        return np.zeros_like(f)

    amp = 10 ** (gain_db / 40.0)
    w0 = 2 * math.pi * min(fc, FS * 0.4999) / FS
    alpha = math.sin(w0) / (2 * q)

    w = 2 * math.pi * f / FS
    common = (np.cos(w) - math.cos(w0)) ** 2
    num = common + (alpha * amp * np.sin(w)) ** 2
    den = common + (alpha / amp * np.sin(w)) ** 2

    with np.errstate(divide="ignore"):
        return 10.0 * np.log10(np.maximum(num, 1e-20) / np.maximum(den, 1e-20))


def _filter_sum_db(freqs: np.ndarray, filters: list[tuple[float, float, float]]) -> np.ndarray:
    total = np.zeros_like(freqs, dtype=float)
    for fc, q, gain in filters:
        total = total + peaking_magnitude_db(freqs, fc, q, gain)
    return total


def fit_peaking_filters(
    freqs: np.ndarray,
    target_db: np.ndarray,
    n_filters: int = PEAKING_FILTERS,
    min_q: float = PEAKING_MIN_Q,
    max_q: float = PEAKING_MAX_Q,
    max_gain: float = MAX_BOOST_DB,
    seed: int = 7,
    iterations: int = 400,
) -> list[tuple[float, float, float]]:
    """Fit peaking filters to a correction curve.

    Peak-detect initialisation followed by deterministic coordinate descent —
    the same two-phase shape AutoEq uses, with the numerical optimiser
    replaced by something with no scipy dependency and no random restart, so
    the same measurement always produces the same filters on every machine.

    Two penalties shape the result beyond plain least squares: one on high-Q
    high-gain filters, because that combination rings, and one that holds
    filters a quarter octave apart, because unconstrained descent otherwise
    parks two of them on the same frequency and wastes a band.

    Returns a list of ``(fc_hz, q, gain_db)`` ordered by frequency.
    """
    band = (freqs >= F_MIN) & (freqs <= F_MAX)
    if band.sum() < 8:
        return []

    bf = freqs[band]
    bt = target_db[band]

    # --- initialisation: seed a filter at each local extremum worth fixing ---
    seeds: list[tuple[float, float, float]] = []
    for i in range(1, len(bt) - 1):
        if bt[i] == bt[i - 1] == bt[i + 1]:
            continue
        is_peak = bt[i] > bt[i - 1] and bt[i] >= bt[i + 1]
        is_valley = bt[i] < bt[i - 1] and bt[i] <= bt[i + 1]
        if (is_peak or is_valley) and abs(bt[i]) >= 0.5:
            q = min(max_q, max(min_q, 1.0 / max(abs(bt[i]), 0.5)))
            seeds.append((float(bf[i]), float(q), float(np.clip(bt[i], -max_gain, max_gain))))

    # Bass is usually smooth and therefore seedless; give it two slots anyway,
    # because that is where the audible problems live.
    if not any(fc < 120 for fc, _, _ in seeds):
        seeds.append((35.0, 0.8, float(np.clip(np.interp(35.0, bf, bt), -max_gain, max_gain))))
        seeds.append((80.0, 0.8, float(np.clip(np.interp(80.0, bf, bt), -max_gain, max_gain))))

    if len(seeds) > n_filters:
        # Drop the least useful first: smallest gain, then merge nearest pairs.
        seeds.sort(key=lambda s: -abs(s[2]))
        seeds = seeds[:n_filters]
    while len(seeds) < n_filters:
        seeds.append((float(np.interp(len(seeds) / max(1, n_filters - 1), [0, 1],
                                      [bf[0], bf[-1]])), 1.0, 0.0))

    filt = sorted(seeds, key=lambda s: s[0])

    # --- refinement: coordinate descent on fc, q, gain per filter ---
    rng = np.random.default_rng(seed)
    step = 0.35
    for _ in range(iterations):
        improved = False
        for k in range(len(filt)):
            base = _filter_sum_db(bf, filt)
            err = float(np.mean((base - bt) ** 2))
            fc, q, gain = filt[k]
            best = (fc, q, gain, err)
            for _ in range(24):
                cand_fc = float(np.clip(best[0] * (1 + rng.normal(0, step * 0.4)),
                                        bf[0], bf[-1]))
                cand_q = float(np.clip(best[1] * (1 + rng.normal(0, step * 0.25)),
                                       min_q, max_q))
                cand_g = float(np.clip(best[2] + rng.normal(0, step * 1.5),
                                       -max_gain, max_gain))
                trial = list(filt)
                trial[k] = (cand_fc, cand_q, cand_g)
                e = float(np.mean((_filter_sum_db(bf, trial) - bt) ** 2))
                # Penalise high-Q high-gain filters: that combination rings.
                e += 0.02 * max(0.0, cand_q - 2.0) * abs(cand_g)
                # Keep filters a quarter octave apart. Unconstrained, the
                # descent parks two of them on the same frequency, which
                # wastes a band and reads as a bug in the exported list.
                for j, (other_fc, _, _) in enumerate(trial):
                    if j == k or other_fc <= 0:
                        continue
                    spread = abs(math.log2(cand_fc / other_fc))
                    if spread < 0.25:
                        e += 0.5 * (0.25 - spread)
                if e < best[3] - 1e-9:
                    best = (cand_fc, cand_q, cand_g, e)
                    improved = True
            filt[k] = best[:3]
        step *= 0.94
        if not improved and step < 0.02:
            break

    return [(round(fc, 1), round(q, 2), round(g, 2))
            for fc, q, g in sorted(filt, key=lambda s: s[0]) if abs(g) >= 0.05]


# ---------------------------------------------------------------------------
# Collapse to the platform Equalizer API
# ---------------------------------------------------------------------------

def collapse_to_bands(
    band_centres_hz: list[float],
    correction: "callable",
    min_millibel: int,
    max_millibel: int,
) -> list[tuple[float, int]]:
    """Map the correction curve onto a device's real Equalizer band layout.

    `android.media.audiofx.Equalizer` reports its own band centres and its own
    millibel range, both device-dependent — 5 bands is common, 8 and 13 occur,
    and the range is frequently ±1500 mB. This takes the device's own numbers
    rather than assuming any layout, which is the whole reason the measurement
    is worth doing.

    ``correction`` is called with a frequency in Hz and returns dB.
    """
    out: list[tuple[float, int]] = []
    for fc in band_centres_hz:
        db = float(correction(fc))
        db = float(np.clip(db, -MAX_CUT_DB, MAX_BOOST_DB))
        mb = int(round(db * 100.0))
        out.append((float(fc), int(np.clip(mb, min_millibel, max_millibel))))
    return out


# ---------------------------------------------------------------------------
# Export
# ---------------------------------------------------------------------------

def export_graphic_eq(freqs: np.ndarray, correction_db: np.ndarray) -> str:
    """EqualizerAPO `GraphicEQ:` line — the widest-compatibility text format."""
    parts = []
    for f, db in zip(freqs, correction_db):
        parts.append(f"{f:.0f} {db:+.2f}")
    return "GraphicEQ: " + " ".join(parts) + "\n"


def preamp_db(filters: list[tuple[float, float, float]]) -> float:
    """The negative preamp a filter bank needs, in dB.

    Shared by the parametric export and the profile JSON so the two can never
    disagree about headroom: one number, one rounding rule, both outputs.
    """
    if not filters:
        return 0.0
    biggest = max(0.0, max(g for _, _, g in filters))
    return math.floor(-biggest * 100.0) / 100.0


def export_parametric_txt(filters: list[tuple[float, float, float]]) -> str:
    """AutoEq / squig.link-style parametric text, including the preamp.

    The preamp is not optional decoration: parametric filters produce positive
    gain, and without a matching negative preamp the boost clips.

    It is rounded *toward more negative*, not to nearest. A preamp of -5.45 dB
    against a +5.46 dB peak leaves 0.01 dB of the boost uncompensated, which is
    inaudible but is the wrong direction for the one number in the file whose
    job is safety.
    """
    if not filters:
        return "# Core EQ: no filters\n"
    lines = [f"Preamp: {preamp_db(filters):.2f} dB"]
    for i, (fc, q, gain) in enumerate(filters):
        lines.append(f"Filter {i + 1}: ON PK Fc {fc:.0f} Hz Gain {gain:+.2f} dB Q {q:.2f}")
    return "\n".join(lines) + "\n"


def export_profile_json(
    *,
    device: str,
    mic: str,
    stimulus: str,
    seconds: float,
    target: str,
    freqs: np.ndarray,
    measured_db: np.ndarray,
    correction_db: np.ndarray,
    filters: list[tuple[float, float, float]],
    capability: dict,
) -> str:
    """The Core EQ profile: measurement, provenance and the honest verdict.

    Carrying the capability verdict inside the profile is deliberate. A profile
    that travelled without its "this device could only reach sessions my own
    app opened" note would read as a promise on the next device it was loaded
    onto.
    """
    payload = {
        "format": "corebuilds.core-eq/1",
        "device": device,
        "microphone": mic,
        "stimulus": stimulus,
        "capture_seconds": seconds,
        "sample_rate": FS,
        "target": target,
        "correction_range_hz": [F_MIN, F_MAX],
        "gain_limits_db": {"boost": MAX_BOOST_DB, "cut": MAX_CUT_DB},
        "preamp_db": preamp_db(filters),
        "filters": [{"fc": fc, "q": q, "gain": g} for fc, q, g in filters],
        "capability": capability,
        "curve": [
            {"hz": round(float(f), 1),
             "measured_db": round(float(m), 2),
             "correction_db": round(float(c), 2)}
            for f, m, c in zip(freqs, measured_db, correction_db)
        ],
    }
    return json.dumps(payload, indent=2, sort_keys=False) + "\n"


# ---------------------------------------------------------------------------
# A synthetic room, so the chain can be exercised with no hardware
# ---------------------------------------------------------------------------

def synthetic_room_db(freqs: np.ndarray, seed: int = 11) -> np.ndarray:
    """A plausible untreated living-room response, for tests and the demo.

    Two modal peaks, one deep null, a boundary bass rise and a treble roll-off
    — the shape real measurements have. Not a claim about any specific room.
    """
    f = np.maximum(np.asarray(freqs, dtype=float), 1e-9)
    curve = np.zeros_like(f)
    curve += 4.5 * np.exp(-((np.log2(f / 62.0)) ** 2) / (2 * 0.25 ** 2))
    curve += 6.0 * np.exp(-((np.log2(f / 118.0)) ** 2) / (2 * 0.12 ** 2))
    curve += -14.0 * np.exp(-((np.log2(f / 84.0)) ** 2) / (2 * 0.10 ** 2))
    curve += -5.0 * np.log2(np.maximum(f, 1.0) / 1000.0) * 0.6
    rng = np.random.default_rng(seed)
    curve += rng.normal(0, 0.35, size=f.shape)
    return curve


def write_wav(path: str, signal: np.ndarray, fs: int = FS) -> None:
    """Write a 16-bit mono WAV the TV can play from the app's assets."""
    pcm = np.clip(signal * 32767.0, -32768, 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(fs)
        w.writeframes(pcm.tobytes())


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _demo() -> dict:
    freqs, _ = welch_db(pink_noise(seconds=0.5), FS)
    centres, _ = octave_bands(freqs, np.zeros_like(freqs), n=3.0)

    measured = synthetic_room_db(centres)
    target = target_curve("harman", centres)
    correction = correction_curve(centres, measured, target)
    filters = fit_peaking_filters(centres, correction, n_filters=6)

    bands = collapse_to_bands([60, 230, 910, 3600, 14000],
                              lambda hz: float(np.interp(hz, centres, correction)),
                              -1500, 1500)

    print("Core EQ reference chain — worked example\n")
    print(f"stimulus        pink noise, {PINK_SECONDS:.0f}s at {FS} Hz, seeded")
    print(f"analysis        Welch, NFFT {NFFT}, Hann, 50% overlap")
    print(f"reduction       1/3 octave -> {len(centres)} bands")
    print(f"target          harman")
    print(f"correction band {F_MIN:.0f}-{F_MAX:.0f} Hz")
    print(f"gain budget     +{MAX_BOOST_DB:.0f} / -{MAX_CUT_DB:.0f} dB, "
          f"slope <= {MAX_SLOPE_DB_PER_OCT:.0f} dB/oct")
    print()
    print(f"{'Hz':>8} {'measured':>10} {'target':>8} {'correction':>11}")
    for f, m, t, c in zip(centres, measured, target, correction):
        if 31 <= f <= 12500 and abs(round(math.log2(f / 1000) * 3)) % 1 == 0:
            print(f"{f:8.0f} {m:10.2f} {t:8.2f} {c:11.2f}")
    print()
    print("fitted peaking filters (parametric export):")
    for fc, q, g in filters:
        print(f"  Fc {fc:8.1f} Hz   Q {q:4.2f}   {g:+6.2f} dB")
    print()
    print("collapsed to a 5-band platform Equalizer (millibels):")
    for fc, mb in bands:
        print(f"  {fc:6.0f} Hz   {mb:+5d} mB  ({mb / 100.0:+.1f} dB)")
    print()
    print("--- EqualizerAPO export (first 400 chars) ---")
    print(export_graphic_eq(centres, correction)[:400] + " ...")
    print()
    print("--- parametric export ---")
    print(export_parametric_txt(filters))

    return {"filters": filters, "bands": bands, "bands_count": len(centres)}


def _is_num(tok: str) -> bool:
    try:
        float(tok)
        return True
    except ValueError:
        return False


def _selftest() -> int:
    """The invariants that make this file trustworthy, runnable with no pytest."""
    failures: list[str] = []

    def check(name: str, ok: bool, detail: str = "") -> None:
        print(f"  {'ok  ' if ok else 'FAIL'} {name}{(' — ' + detail) if detail else ''}")
        if not ok:
            failures.append(name)

    print("core_eq_dsp self-test\n")

    # Pink noise really is pink: about -3 dB per octave.
    pink = pink_noise(seconds=4.0)
    f, db = welch_db(pink)
    b125 = float(np.interp(125.0, f, db))
    b1000 = float(np.interp(1000.0, f, db))
    slope = (b1000 - b125) / math.log2(1000 / 125)
    check("pink noise slope ~ -3 dB/octave", -4.0 < slope < -2.0, f"{slope:.2f} dB/oct")

    # Deterministic: same seed, same signal.
    check("pink noise deterministic",
          np.array_equal(pink_noise(seconds=0.1), pink_noise(seconds=0.1)))

    # ESS inverse filter is finite and non-trivial.
    sweep, inverse = sine_sweep(seconds=1.0)
    check("ESS pair generated",
          len(sweep) == FS and len(inverse) == FS
          and np.all(np.isfinite(sweep)) and np.max(np.abs(inverse)) > 0.5)

    # 1/3-octave centres: each is 2^(1/3) above the last, 1000 Hz is one.
    c, _ = octave_bands(f, db, n=3.0)
    ratios = c[1:] / c[:-1]
    check("1/3 octave ratio",
          np.allclose(ratios, 2 ** (1 / 3), rtol=1e-9),
          f"mean {float(ratios.mean()):.6f}")
    check("1 kHz is a band centre",
          bool(np.any(np.isclose(c, 1000.0, rtol=1e-3))))

    # Targets are shape, normalised at the pivot.
    for kind in ("flat", "bk", "harman", "house"):
        t = target_curve(kind, c)
        check(f"target {kind} pivots at 0 dB",
              abs(float(np.interp(630.0, c, t))) < 0.05)

    bk = target_curve("bk", c)
    check("B&K rolls off in the treble",
          float(np.interp(20000.0, c, bk)) < -4.0,
          f"{float(np.interp(20000.0, c, bk)):.2f} dB at 20 kHz")

    measured = synthetic_room_db(c)
    target = target_curve("harman", c)
    corr = correction_curve(c, measured, target)
    inside = (c >= F_MIN) & (c <= F_MAX)

    check("correction respects gain budget",
          float(corr.max()) <= MAX_BOOST_DB + 1e-6
          and float(corr.min()) >= -MAX_CUT_DB - 1e-6,
          f"{float(corr.min()):.2f} .. {float(corr.max()):.2f} dB")
    check("correction zero outside its band",
          bool(np.all(np.abs(corr[~inside]) < 1e-9)))
    check("correction recentred on 100 Hz-10 kHz",
          abs(float(corr[(c >= 100) & (c <= 10000)].mean())) < 0.5)

    cut = correction_curve(c, measured, target, cut_only=True)
    check("cut-only never boosts",
          float(cut[(c >= F_MIN) & (c <= F_MAX)].max()) <= 1e-9)

    # Slope limiting actually limits.
    spike = np.zeros_like(c)
    spike[int(np.argmin(np.abs(c - 100.0)))] = 15.0
    limited = _limit_slope(c, spike, MAX_SLOPE_DB_PER_OCT)
    worst = 0.0
    for i in range(len(c) - 1):
        df = abs(math.log2(c[i + 1] / c[i]))
        if df > 0:
            worst = max(worst, abs(limited[i + 1] - limited[i]) / df)
    check("slope limiter caps steepness",
          worst <= MAX_SLOPE_DB_PER_OCT + 1e-6, f"{worst:.2f} dB/oct")

    # The RBJ response must peak at its own centre frequency, at its own gain.
    # A wrong dB conversion factor halves this and nothing else notices.
    grid = np.linspace(20, 20000, 40000)
    resp = peaking_magnitude_db(grid, 1000.0, 1.0, 6.0)
    check("peaking filter peaks at its own gain",
          abs(float(resp.max()) - 6.0) < 0.25
          and abs(float(grid[int(np.argmax(resp))]) - 1000.0) < 5.0,
          f"{float(resp.max()):.2f} dB at {float(grid[int(np.argmax(resp))]):.0f} Hz")

    # Fitting beats doing nothing.
    filt = fit_peaking_filters(c, corr, n_filters=6)
    band = (c >= F_MIN) & (c <= F_MAX)
    baseline = float(np.sqrt(np.mean(corr[band] ** 2)))
    fitted = _filter_sum_db(c[band], filt)
    residual = float(np.sqrt(np.mean((fitted - corr[band]) ** 2)))
    check("peaking fit reduces RMSE", residual < baseline,
          f"{baseline:.2f} dB -> {residual:.2f} dB with {len(filt)} filters")
    check("filter Q inside bounds",
          all(PEAKING_MIN_Q - 1e-9 <= q <= PEAKING_MAX_Q + 1e-9 for _, q, _ in filt))
    check("fitting is deterministic",
          fit_peaking_filters(c, corr, n_filters=6) == filt)

    # Platform collapse clamps to the device's own millibel range.
    bands = collapse_to_bands([60, 230, 910, 3600, 14000],
                              lambda hz: float(np.interp(hz, c, corr)), -1500, 1500)
    check("5-band collapse has one entry per device band", len(bands) == 5)
    check("millibels inside device range",
          all(-1500 <= mb <= 1500 for _, mb in bands))
    tight = collapse_to_bands([60, 230, 910, 3600, 14000],
                              lambda hz: 20.0, -300, 300)
    check("millibels clamped on a narrow-range device",
          all(mb == 300 for _, mb in tight))

    # Exports.
    geq = export_graphic_eq(c, corr)
    check("GraphicEQ header present", geq.startswith("GraphicEQ: "))
    tokens = geq[len("GraphicEQ: "):].split()
    check("GraphicEQ carries one pair per band",
          len(tokens) == 2 * len(c), f"{len(tokens)} tokens for {len(c)} bands")
    check("GraphicEQ tokens parse as hz/db pairs",
          all(_is_num(tokens[i]) and _is_num(tokens[i + 1])
              for i in range(0, len(tokens), 2)))

    peq = export_parametric_txt(filt)
    check("parametric export declares a preamp", peq.startswith("Preamp: "))
    biggest = max(g for _, _, g in filt)
    check("parametric preamp cancels the largest boost",
          abs(float(peq.split()[1]) + max(0.0, biggest)) < 1e-6,
          f"preamp {peq.split()[1]} dB against a {biggest:+.2f} dB peak")
    check("profile preamp matches the parametric export",
          json.loads(export_profile_json(
              device="t", mic="remote", stimulus="pink", seconds=1.0,
              target="harman", freqs=c, measured_db=measured,
              correction_db=corr, filters=filt,
              capability={}))["preamp_db"] == preamp_db(filt))

    profile = json.loads(export_profile_json(
        device="test", mic="remote", stimulus="pink", seconds=PINK_SECONDS,
        target="harman", freqs=c, measured_db=measured, correction_db=corr,
        filters=filt, capability={"global_mix": False}))
    check("profile records the capability verdict",
          profile["capability"] == {"global_mix": False})
    check("profile carries the whole curve", len(profile["curve"]) == len(c))
    check("profile names its format", profile["format"] == "corebuilds.core-eq/1")

    print()
    if failures:
        print(f"{len(failures)} FAILED: {', '.join(failures)}")
        return 1
    print("all invariants hold")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Core EQ reference DSP chain")
    parser.add_argument("--demo", action="store_true", help="print a worked example")
    parser.add_argument("--selftest", action="store_true", help="run the invariants")
    parser.add_argument("--stimulus", metavar="PATH",
                        help="write a pink-noise WAV the app can ship as an asset")
    parser.add_argument("--seconds", type=float, default=PINK_SECONDS)
    args = parser.parse_args(argv)

    if args.stimulus:
        write_wav(args.stimulus, pink_noise(seconds=args.seconds))
        print(f"wrote {args.stimulus} ({args.seconds:.0f}s pink noise, {FS} Hz mono)")
        return 0
    if args.selftest:
        return _selftest()
    _demo()
    return 0


if __name__ == "__main__":
    sys.exit(main())
