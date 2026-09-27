#!/usr/bin/env python3
"""
Core EQ — the measurement → correction → export maths, as runnable reference.

Why this file exists
--------------------
Core EQ's promise is "measure the room, build an equaliser". The risky part of
that promise is not the Android UI or the manifest; it is whether the DSP chain
between "microphone samples" and "five numbers an
`android.media.audiofx.Equalizer` will accept" actually produces something
musical. That chain is pure maths, and maths that lives only inside a Kotlin
file cannot be checked in this repository — there is no Android SDK here, and
`tests/test_core_eq_dsp.py` has to run in CI next to `tools/validate.py`.

So the chain is written here first, in numpy, with no Android in sight. The
Kotlin port in `coreeq/` is a translation of these functions, not a
re-derivation. Every constant below carries the reason it has the value it
has; see `docs/research/core-eq-measurement-and-capability-2026-09.md` and
`docs/research/core-eq-correction-science-2026-09-27.md` for the research each
one came from.

What is deliberately in scope
-----------------------------
  * stimulus synthesis: an exponential sine sweep (primary) and pink noise
    (cross-check / live RTA);
  * sweep deconvolution to an impulse response, and the two things only an
    impulse response can give: RT60 (for the Schroeder frequency) and excess
    group delay (for the minimum-phase gate);
  * Welch PSD -> 1/N-octave band reduction (what a graph on a TV should show);
  * the room model: Schroeder frequency, transition frequency, null detection,
    low-frequency roll-off detection;
  * target curves (flat / B&K / room / olive / dialogue / house);
  * the two-regime correction curve, with the limits that stop it doing damage;
  * peaking-filter fitting, so the result survives export to a parametric EQ;
  * the 5-band collapse for the platform `Equalizer` API, which is the only
    thing most Android TV devices will actually accept.

The two-regime corrector
------------------------
This is the central design decision in the chain, and it comes from
`core-eq-correction-science-2026-09-27.md` §1–2. Below the room's transition
frequency the response is a handful of discrete standing waves: correcting
them is real work, and because low-frequency room modes are minimum phase,
cutting a modal peak also kills its ringing. Above it, the microphone is
measuring reflections as much as the speaker, and forcing the curve smooth
there equalises things an upstream filter cannot touch. So the corrector
inverts below the transition and only shapes above it, and it never tries to
fill a null at any frequency — a null is destructive interference and cannot
be filled at any gain.

What is out of scope
--------------------
Mixed-phase time-domain correction. The impulse response is used here to
*gate* the correction (is this region minimum phase? can it be fixed at all?)
and to measure decay, not to build linear-phase pre-ringing filters. A TV
loudspeaker's phase at the listening position is dominated by reflections a
5-band EQ cannot touch. Say so rather than imply otherwise.

Usage
-----
    python tools/core_eq_dsp.py --demo           # print a full worked example
    python tools/core_eq_dsp.py --selftest       # run the built-in invariants
    python tools/core_eq_dsp.py --stimulus sweep.wav   # write a 48k sweep WAV

The suite convention applies: paste the receipt.
"""
from __future__ import annotations
from pathlib import Path

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

#: Smoothing applied before the correction decision, and it is *variable* on
#: purpose (core-eq-correction-science-2026-09-27.md §4). Below the transition
#: the corrector needs enough detail to resolve a modal peak; above it, the
#: window is deliberately wide so that only broad tonal shaping survives and
#: comb structure — which changes if the listener moves 30 cm — is not chased.
#: REW's "Variable" smoothing is the same idea, and its documentation is
#: explicit that it is the one to use for room EQ.
SMOOTH_FINE_OCTAVES = 1.0 / 6.0
SMOOTH_COARSE_OCTAVES = 1.0

#: Legacy name kept so existing callers and the changelog's older bullets stay
#: truthful. It is the *display* resolution and the mid-band smoothing default.
CORRECTION_SMOOTHING_OCTAVES = 1.0 / 3.0

#: The room's transition frequency: above this, the corrector stops inverting
#: and only shapes. 400 Hz is the working ceiling the field converges on —
#: Toole puts the room-to-speaker transition at ~300–400 Hz, Dirac's ART stops
#: at 150 Hz, audioxpress recommends 20–400 Hz, and serious Audyssey users
#: limit the filter range to 250–500 Hz. When the room is unknown we fall back
#: to 300 Hz, which is inside all of those.
DEFAULT_TRANSITION_HZ = 400.0
UNKNOWN_ROOM_TRANSITION_HZ = 300.0

#: Above the transition the corrector is *shaping only*, and shaping has a
#: tighter budget than correction. A broad tilt can afford 3 dB; a modal notch
#: cannot.
MAX_SHAPING_DB = 3.0

#: A dip deeper than this below the local trend is a cancellation, not a
#: frequency the speaker failed to produce. We do not boost into it at any
#: gain (core-eq-correction-science-2026-09-27.md §2.1).
NULL_DEPTH_DB = 6.0

#: How far from minimum phase a region may be before we refuse to correct it.
#: REW's excess group delay plot is the same test; tens of milliseconds of
#: excess means the region is non-minimum-phase and inverting it distorts the
#: waveform without fixing the sound.
MIN_PHASE_TOLERANCE_MS = 5.0

#: Roll-off detection. A TV's thin drivers stop producing useful output well
#: before 40 Hz, and correcting under the roll-off is the "large bass boost
#: built on microphone noise" failure mode. The floor rises to wherever the
#: measurement says the speaker actually stops.
ROLLOFF_DROP_DB = 6.0

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

    # Inverse: time reversal, with an amplitude envelope rising +3 dB/octave
    # (i.e. doubling per octave) across the sweep's own frequency span.
    # The envelope scales with instantaneous frequency and is applied to the
    # sweep before time reversal, so that high frequencies receive the +3 dB/oct
    # boost in the resulting inverse filter.
    inst_f = f_start * np.exp(t / k)
    envelope = inst_f / inst_f[-1]
    inverse = (sweep * envelope)[::-1].copy()
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



def smooth_variable(freqs: np.ndarray, values_db: np.ndarray,
                    transition_hz_: float = DEFAULT_TRANSITION_HZ) -> np.ndarray:
    """Smoothing whose window widens with frequency: fine below the transition.

    ``SMOOTH_FINE_OCTAVES`` up to the transition, then a one-octave ramp out to
    ``SMOOTH_COARSE_OCTAVES`` at the top of the band. This is the smoothing the
    *corrector* runs on; the on-screen graph keeps its fixed 1/3 octave,
    because display resolution and correction resolution are deliberately
    different things.

    Widening rather than stepping matters: a step in smoothing is a step in the
    correction curve, and the slope limiter would then be fighting a
    discontinuity we put there ourselves.
    """
    f = np.asarray(freqs, dtype=float)
    lo_t = max(float(transition_hz_), 1.0)
    hi_t = lo_t * 2.0
    widths = np.where(
        f <= lo_t,
        SMOOTH_FINE_OCTAVES,
        np.where(
            f >= hi_t,
            SMOOTH_COARSE_OCTAVES,
            SMOOTH_FINE_OCTAVES + (SMOOTH_COARSE_OCTAVES - SMOOTH_FINE_OCTAVES)
            * np.log2(np.maximum(f, 1e-9) / lo_t),
        ),
    )

    linear = 10 ** (np.asarray(values_db, dtype=float) / 10.0)
    log_f = np.log2(np.maximum(f, 1e-9))
    out = np.empty_like(linear)
    for i in range(len(f)):
        half = float(widths[i]) / 2.0
        a = int(np.searchsorted(log_f, log_f[i] - half))
        b = int(np.searchsorted(log_f, log_f[i] + half))
        b = max(b, a + 1)
        out[i] = 10.0 * np.log10(max(linear[a:b].mean(), 1e-20))
    return out


# ---------------------------------------------------------------------------
# The room model
#
# Everything here is computable from a description of the room plus one
# measurement, and everything here changes what the corrector is allowed to do.
# ---------------------------------------------------------------------------

def schroeder_hz(volume_m3: float, rt60_s: float) -> float:
    """The Schroeder frequency: where a room stops being a set of resonances.

    ``f_s = 2000 * sqrt(RT60 / V)``. Below it the response is discrete standing
    waves that move 10-25 dB with position; above it the modes overlap and the
    field becomes statistical. A 54 m3 living room at RT60 = 0.5 s gives about
    192 Hz; a 30 m3 bedroom at 0.8 s gives about 327 Hz.

    Both inputs are validated because the failure mode is silent: a nonsense
    RT60 produces a nonsense ceiling and the correction quietly does the wrong
    thing across the whole spectrum.
    """
    if volume_m3 <= 0:
        raise ValueError(f"room volume must be positive, got {volume_m3}")
    if rt60_s <= 0:
        raise ValueError(f"RT60 must be positive, got {rt60_s}")
    return 2000.0 * math.sqrt(rt60_s / float(volume_m3))


def transition_hz(volume_m3: float | None = None, rt60_s: float | None = None) -> float:
    """The frequency above which the corrector only shapes.

    Two octaves above the Schroeder frequency is the practitioner reading of
    the transition, capped at ``DEFAULT_TRANSITION_HZ`` because that is the
    highest ceiling the field's evidence supports. With no room description we
    say so and use ``UNKNOWN_ROOM_TRANSITION_HZ`` — a stated fallback, not a
    silent guess.
    """
    if volume_m3 is None or rt60_s is None:
        return UNKNOWN_ROOM_TRANSITION_HZ
    return min(2.0 * schroeder_hz(volume_m3, rt60_s), DEFAULT_TRANSITION_HZ)


def detect_nulls(freqs: np.ndarray, measured_db: np.ndarray,
                 depth_db: float = NULL_DEPTH_DB,
                 trend_octaves: float = 2.0) -> np.ndarray:
    """Boolean mask of dips too deep to be anything but a cancellation.

    A dip more than [depth_db] below the broad local trend is destructive
    interference — the direct and reflected arrivals out of phase at the
    capsule. Boosting it raises both arrivals equally and the cancellation
    stays; the energy just goes into excursion and distortion. We mark these
    and refuse to boost into them, and the UI is expected to say so out loud.
    """
    trend = smooth_octave(freqs, measured_db, trend_octaves)
    return (trend - measured_db) > float(depth_db)


def detect_low_rolloff(freqs: np.ndarray, measured_db: np.ndarray,
                       plateau_hz: float = 250.0,
                       drop_db: float = ROLLOFF_DROP_DB) -> float:
    """Where the loudspeaker stops working, as opposed to where the room dips.

    Scans down from a reference plateau around [plateau_hz] and returns the
    lowest frequency still within [drop_db] of it. The correction floor is
    raised to that point: Audyssey does the same thing, and it is why a fixed
    40 Hz floor is wrong for a TV — below the roll-off there is nothing to
    correct but microphone noise.

    Returns ``F_MIN`` when the measurement never rolls off inside the band,
    which is the honest answer rather than zero.
    """
    f = np.asarray(freqs, dtype=float)
    db = np.asarray(measured_db, dtype=float)
    if f.size == 0:
        return F_MIN

    ref = (f >= plateau_hz / 1.5) & (f <= plateau_hz * 1.5)
    level = float(np.mean(db[ref])) if ref.any() else float(np.max(db))

    usable = np.where(db >= level - drop_db)[0]
    if usable.size == 0:
        return F_MIN
    return float(max(F_MIN, f[usable].min()))


# ---------------------------------------------------------------------------
# Target curves
# ---------------------------------------------------------------------------

def target_curve(kind: str, freqs: np.ndarray, bass_boost_db: float = 0.0,
                 tilt_db_per_oct: float = -1.0, pivot_hz: float = 630.0,
                 presence_db: float = 2.0, trim_db: float = 0.0) -> np.ndarray:
    """A target curve, in dB relative to its value at [pivot_hz].

    These are **loudspeaker in a room** curves. That is a different object
    from the Harman *headphone* target, which carries a 3 kHz ear-gain peak to
    simulate what a listener's own head does for an external source. Baking
    that peak into a speaker correction double-counts pinna gain and makes
    voices speak through a telephone. Do not import it here.

    ``kind``:
      ``flat``    0 dB everywhere. Honest but rarely what people prefer in a
                  reflective living room.
      ``bk``      Bruel & Kjaer 1974: flat to roughly 160 Hz, then about
                  -6 dB by 20 kHz.
      ``room``    Bass shelf plus a downward tilt — the family every in-room
                  preference study converges on. The 105 Hz shelf breakpoint
                  is the one used in Olive, Welti & McMullin (AES 2013).
      ``olive``   That study's published mean preference, numbers and all:
                  +6.6 dB of bass below 105 Hz and -2.4 dB of treble above
                  2.5 kHz, on the same family tilt. Shipped as a preset
                  rather than as the default, because the spread between the
                  eleven listeners was about 17 dB of bass — it is a starting
                  point with a preference knob, not a truth.
      ``dialogue``  ``room`` plus the SII-weighted presence shaping for TV:
                  a wide +2 dB shelf across the 2-3 kHz region where the 2 kHz
                  octave alone carries about 30 % of speech intelligibility,
                  a deliberate hard stop above 6 kHz (sibilance lives at
                  5-7 kHz), and an optional trim through 300-800 Hz for boxed
                  mixes. Cuts are preferred over boosts in that band.
      ``house``   Whatever [bass_boost_db], [tilt_db_per_oct] and [presence_db]
                  say.

    Tilt is expressed per octave around a pivot, which is how the literature
    and every house-curve control express it; 630 Hz is the conventional pivot.
    """
    f = np.asarray(freqs, dtype=float)
    out = np.zeros_like(f)

    if kind == "flat":
        return out

    octaves_from_pivot = np.log2(np.maximum(f, 1e-9) / pivot_hz)
    bass = lambda gain: gain * 0.5 * (1.0 - np.tanh((f - 105.0) / 55.0))

    if kind == "bk":
        # Flat below 160 Hz, then ~-6 dB over 160 Hz -> 20 kHz.
        slope = -6.0 / math.log2(20000.0 / 160.0)
        out = np.where(f <= 160.0, 0.0, slope * np.log2(np.maximum(f, 160.0) / 160.0))
    elif kind in ("room", "harman"):
        # "harman" is the old name and is kept only so existing profiles and
        # changelog bullets stay readable. It never was the Harman headphone
        # target; see the note above.
        out = bass(3.5) + tilt_db_per_oct * octaves_from_pivot
    elif kind == "olive":
        out = (bass(6.6) + tilt_db_per_oct * octaves_from_pivot
               + _shelf_db(f, 2500.0, -2.4))
    elif kind == "dialogue":
        out = bass(3.5) + tilt_db_per_oct * octaves_from_pivot
        out = out + _presence_db(f, presence_db)
        if trim_db:
            out = out - abs(trim_db) * _box_db(f, 300.0, 800.0)
    elif kind == "house":
        out = (bass(bass_boost_db) + tilt_db_per_oct * octaves_from_pivot
               + _presence_db(f, presence_db))
    else:
        raise ValueError(f"unknown target curve: {kind!r}")

    # Normalise so the pivot reads 0 dB; targets are shape, not level.
    at_pivot = np.interp(pivot_hz, f, out)
    return out - at_pivot


#: Target names this module will accept. `harman` is an alias for `room`.
TARGET_CURVES = ("flat", "bk", "room", "olive", "dialogue", "house")


def _shelf_db(f: np.ndarray, corner_hz: float, gain_db: float) -> np.ndarray:
    """A smooth shelf reaching [gain_db] above [corner_hz], 0 well below it."""
    return gain_db * 0.5 * (1.0 + np.tanh((np.log2(np.maximum(f, 1e-9) / corner_hz)) / 0.35))


def _box_db(f: np.ndarray, lo_hz: float, hi_hz: float) -> np.ndarray:
    """A raised-cosine plateau of 1.0 between [lo_hz] and [hi_hz], 0 outside."""
    f = np.asarray(f, dtype=float)
    out = np.zeros_like(f)
    lo_edge = lo_hz / 2 ** 0.5
    hi_edge = hi_hz * 2 ** 0.5
    rise = (f > lo_edge) & (f < lo_hz)
    out[rise] = 0.5 * (1.0 - np.cos(np.pi * np.log2(f[rise] / lo_edge)
                                    / math.log2(lo_hz / lo_edge)))
    out[(f >= lo_hz) & (f <= hi_hz)] = 1.0
    fall = (f > hi_hz) & (f < hi_edge)
    out[fall] = 0.5 * (1.0 + np.cos(np.pi * np.log2(f[fall] / hi_hz)
                                    / math.log2(hi_edge / hi_hz)))
    return out


def _presence_db(f: np.ndarray, gain_db: float) -> np.ndarray:
    """Dialogue presence: [gain_db] across 2-3 kHz, returning to zero by 6 kHz.

    The plateau is bounded above on purpose. Speech gains intelligibility up
    to about 4 kHz and gains sibilance between 5 and 7 kHz; a presence shelf
    that keeps rising trades clarity for hiss. It rises from 1.5 kHz, holds
    2-5 kHz, and is back to nothing by 6.5 kHz.
    """
    if not gain_db:
        return np.zeros_like(np.asarray(f, dtype=float))
    # The upper corner is chosen so the raised-cosine fall reaches zero at
    # about 6.4 kHz on its own. Cutting it with an explicit mask instead would
    # leave a step for the slope limiter to fight.
    return gain_db * _box_db(f, 2200.0, 4500.0)


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
    transition_hz_: float = DEFAULT_TRANSITION_HZ,
    max_boost: float = MAX_BOOST_DB,
    max_cut: float = MAX_CUT_DB,
    cut_only: bool = False,
    max_slope: float = MAX_SLOPE_DB_PER_OCT,
    null_mask: np.ndarray | None = None,
    min_phase_ok: np.ndarray | None = None,
) -> np.ndarray:
    """The correction to apply: what to add to the measured response to reach target.

    Two regimes, and the split is the point (core-eq-correction-science-2026-09-27.md
    §1). **Below** [transition_hz_] the room is a handful of discrete standing
    waves and correcting them is real work — because low-frequency modes are
    minimum phase, cutting a modal peak also kills its ringing. **Above** it the
    microphone is measuring reflections as much as the speaker, and forcing the
    curve smooth there is equalising things an upstream filter cannot touch. So
    above the transition the corrector only shapes, with a tighter budget.

    Order matters, and each step is here for a reason:

    1. smooth, with a window that widens at the transition — fine enough below
       it to resolve a modal peak, wide enough above it that only broad tonal
       shaping survives;
    2. invert the error;
    3. **refuse to fill nulls.** Where `detect_nulls` has marked a cancellation
       and the correction would boost, it is set to zero instead. This is not a
       preference; boosting a null raises the direct and the reflected arrival
       equally and the cancellation survives, at the cost of excursion and
       distortion;
    4. **honour the minimum-phase gate.** Where `min_phase_ok` is False the
       correction is zeroed: inverting a non-minimum-phase region distorts the
       waveform without fixing the sound;
    5. clamp to the budget — full boost and cut below the transition,
       shaping-only on *both* sides above it, with the two bounds ramped
       across the octave at the boundary so the regimes join without a step;
    6. limit slope, so no narrow feature becomes a ringing filter;
    7. re-centre so the mean correction over the usable band is 0 dB, which
       keeps the result a balance of cuts and lifts rather than a global gain
       change the user would have to undo with the volume control;
    8. clamp again — the shift in step 7 can carry a curve back over its own
       budget, which is exactly the kind of violation a caller would not
       expect from a function that documents a budget;
    9. zero outside [f_min, f_max] and taper the edges. The region the
       microphone cannot be trusted in is the region we refuse to touch.

    ``cut_only`` skips step 7. Re-centring is precisely what turns a set of
    cuts into a set of boosts, so in cut-only mode the curve stays at or below
    0 dB and the system simply loses loudness — which is the honest cost of
    refusing to boost, and why the export carries a preamp note.

    Returns the correction curve only. Callers that need to *tell* the user
    what was skipped should ask `detect_nulls` themselves; the mask is an
    input here so the same measurement can be explained and corrected without
    computing it twice.
    """
    f = np.asarray(freqs, dtype=float)
    smoothed = smooth_variable(f, measured_db, transition_hz_)
    raw = -(smoothed - target_db)

    if null_mask is None:
        null_mask = detect_nulls(f, measured_db)
    raw = np.where(null_mask & (raw > 0.0), 0.0, raw)

    if min_phase_ok is not None:
        raw = np.where(min_phase_ok, raw, 0.0)

    if cut_only:
        raw = np.minimum(raw, 0.0)

    # Per-frequency budget: full correction below the transition, shaping
    # only above it, ramped over the octave in between so the join is smooth.
    # Both sides ramp — a 12 dB notch in the treble is exactly the narrowband
    # inversion the research says to stop doing, so the cut budget shrinks too.
    lo_t = max(float(transition_hz_), 1.0)
    hi_t = lo_t * 2.0
    mix = np.zeros_like(f)
    ramp = (f > lo_t) & (f < hi_t)
    mix[ramp] = 0.5 * (1.0 - np.cos(np.pi * np.log2(f[ramp] / lo_t)))
    mix[f >= hi_t] = 1.0

    ceiling = float(max_boost) + (MAX_SHAPING_DB - float(max_boost)) * mix
    floor = -float(max_cut) + (float(max_cut) - MAX_SHAPING_DB) * mix
    if cut_only:
        ceiling = np.zeros_like(ceiling)

    raw = np.clip(raw, floor, ceiling)

    limited = _limit_slope(f, raw, max_slope)

    if not cut_only:
        recentre = (f >= max(100.0, f_min)) & (f <= min(10000.0, f_max))
        if recentre.any():
            limited = limited - limited[recentre].mean()

    limited = np.clip(limited, floor, ceiling)

    band = (f >= f_min) & (f <= f_max)
    return _taper_edges(f, np.where(band, limited, 0.0), f_min, f_max)


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
# Impulse response: what only a sweep can give us
#
# Pink noise yields a magnitude spectrum and nothing else. The exponential
# sine sweep yields an impulse response, and two things hang off it that the
# corrector actually needs: RT60 (for the Schroeder frequency) and excess
# group delay (for the minimum-phase gate). Neither is decoration — they are
# the inputs that decide the correction ceiling and whether a region may be
# corrected at all.
# ---------------------------------------------------------------------------

def deconvolve_ir(recorded: np.ndarray, inverse: np.ndarray,
                  fs: int = FS) -> np.ndarray:
    """Impulse response by convolving the capture with the Farina inverse filter.

    The harmonic-distortion products land at negative time and the linear
    response at positive time, which is the whole reason to use a sweep. A
    silence longer than the room's decay must follow the sweep in the capture,
    or the tail is lost — the same read-past-stop rule `AudioRecord` imposes.
    """
    if recorded.size == 0 or inverse.size == 0:
        raise ValueError("deconvolution needs both the capture and the inverse filter")
    n = recorded.size + inverse.size - 1
    nfft = 1 << (n - 1).bit_length()
    spec = np.fft.rfft(recorded, nfft) * np.fft.rfft(inverse, nfft)
    return np.fft.irfft(spec, nfft)


def ir_magnitude_db(ir: np.ndarray, fs: int = FS,
                    nfft: int = NFFT) -> tuple[np.ndarray, np.ndarray]:
    """Magnitude response of an impulse response, in dB, normalised to its peak."""
    ir = np.asarray(ir, dtype=float)
    if ir.size < 4:
        raise ValueError("impulse response too short to analyse")
    # Window around the direct arrival so the analysis is of the response, not
    # of whatever the room did after the useful part ended.
    peak = int(np.argmax(np.abs(ir)))
    lo = max(0, peak - nfft // 8)
    hi = min(ir.size, peak + nfft)
    chunk = np.zeros(nfft)
    seg = ir[lo:hi]
    chunk[: min(seg.size, nfft)] = seg[:nfft]

    spec = np.fft.rfft(chunk * np.hanning(nfft))
    freqs = np.fft.rfftfreq(nfft, 1.0 / fs)
    with np.errstate(divide="ignore"):
        db = 20.0 * np.log10(np.maximum(np.abs(spec), 1e-12))
    db -= db.max()
    return freqs, db


def _hilbert(x: np.ndarray) -> np.ndarray:
    """Discrete Hilbert transform along the first axis, via the analytic signal."""
    n = x.shape[-1]
    h = np.zeros(n)
    h[0] = 1.0
    if n % 2 == 0:
        h[n // 2] = 1.0
        h[1 : n // 2] = 2.0
    else:
        h[1 : (n + 1) // 2] = 2.0
    return np.imag(np.fft.ifft(np.fft.fft(x) * h))


def excess_group_delay_ms(ir: np.ndarray, fs: int = FS,
                          nfft: int = NFFT) -> tuple[np.ndarray, np.ndarray]:
    """Excess group delay: how far a region is from minimum phase, in ms.

    Minimum-phase behaviour is the condition for amplitude correction to fix
    anything. Where a response is minimum phase, correcting its magnitude also
    corrects its phase and therefore its ringing — that is the one thing room
    EQ genuinely does. Where it is not, inverting it distorts the waveform
    without fixing the sound, and REW's excess group delay plot is exactly this
    number.

    The minimum-phase reference is reconstructed from the measured magnitude
    by the Hilbert transform of its log, which is the standard result: a
    minimum-phase system is uniquely determined by its magnitude response.
    """
    freqs, db = ir_magnitude_db(ir, fs, nfft)
    mag = 10 ** (db / 20.0)
    log_mag = np.log(np.maximum(mag, 1e-12))

    phase_mp = -_hilbert(log_mag)

    ir = np.asarray(ir, dtype=float)
    peak = int(np.argmax(np.abs(ir)))
    lo = max(0, peak - nfft // 8)
    hi = min(ir.size, peak + nfft)
    chunk = np.zeros(nfft)
    seg = ir[lo:hi]
    chunk[: min(seg.size, nfft)] = seg[:nfft]
    spec = np.fft.rfft(chunk * np.hanning(nfft))
    phase_meas = np.unwrap(np.angle(spec))

    df = float(fs) / nfft
    omega_step = 2.0 * math.pi * df
    gd_meas = -np.diff(phase_meas, prepend=phase_meas[0]) / omega_step
    gd_mp = -np.diff(phase_mp, prepend=phase_mp[0]) / omega_step

    return freqs, (gd_meas - gd_mp) * 1000.0


def min_phase_gate(freqs: np.ndarray, excess_ms: np.ndarray,
                   tolerance_ms: float = MIN_PHASE_TOLERANCE_MS) -> np.ndarray:
    """Boolean mask: True where the response is close enough to minimum phase to correct.

    The corrector zeroes its output wherever this is False. It is deliberately
    conservative — a region we cannot fix is left alone rather than made
    different-but-not-better.
    """
    return np.asarray(excess_ms, dtype=float) <= float(tolerance_ms)


def rt60_from_ir(ir: np.ndarray, fs: int = FS) -> float:
    """Reverberation time by Schroeder backward integration, in seconds.

    This is the RT60 that `schroeder_hz` wants. Estimated rather than assumed,
    because an assumed RT60 underestimates the Schroeder frequency by 30-50 %
    in a reverberant room — which would set the correction ceiling too high by
    exactly the amount that matters.
    """
    ir = np.asarray(ir, dtype=float)
    if ir.size < 16:
        raise ValueError("impulse response too short for an RT60 estimate")
    peak = int(np.argmax(np.abs(ir)))
    tail = ir[peak:] ** 2
    energy = np.cumsum(tail[::-1])[::-1]
    with np.errstate(divide="ignore"):
        edb = 10.0 * np.log10(np.maximum(energy, 1e-20))
    edb -= edb[0]

    # Fit the -5 to -35 dB part of the decay: outside that range the estimate
    # is dominated by noise at the bottom and by the direct arrival at the top.
    t = np.arange(edb.size) / float(fs)
    mask = (edb <= -5.0) & (edb >= -35.0)
    if mask.sum() < 8:
        return 0.0
    slope, _ = np.polyfit(t[mask], edb[mask], 1)
    if slope >= 0:
        return 0.0
    return float(-60.0 / slope)


def average_measurements(measurements: list[np.ndarray]) -> np.ndarray:
    """Energy-mean several captures.

    Used for the three measurements taken within the seat envelope. Energy
    mean, not dB mean — averaging decibels is not the same operation and
    quietly flattens peaks, which is the opposite of what the corrector needs
    to see.
    """
    if not measurements:
        raise ValueError("nothing to average")
    stack = np.stack([10 ** (np.asarray(m, dtype=float) / 10.0) for m in measurements])
    with np.errstate(divide="ignore"):
        return 10.0 * np.log10(np.maximum(stack.mean(axis=0), 1e-20))


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


def export_parametric_txt(filters: list[tuple[float, float, float]],
                         band: tuple[float, float] = (F_MIN, F_MAX)) -> str:
    """AutoEq / squig.link-style parametric text, including the preamp.

    The preamp is not optional decoration: parametric filters produce positive
    gain, and without a matching negative preamp the boost clips.

    It is rounded *toward more negative*, not to nearest. A preamp of -5.45 dB
    against a +5.46 dB peak leaves 0.01 dB of the boost uncompensated, which is
    inaudible but is the wrong direction for the one number in the file whose
    job is safety.

    The file also carries its own correction band and the Poweramp instruction,
    because a filter set that travels without either will be applied over the
    wrong frequencies or in the wrong filter mode — and both failures sound
    like "the EQ made it worse" rather than like a missing comment line.
    """
    if not filters:
        return "# Core EQ: no filters\n"
    lines = [
        f"Preamp: {preamp_db(filters):.2f} dB",
        "# Core EQ: correction band " f"{band[0]:.0f}-{band[1]:.0f} Hz",
        "# Core EQ: set Bands Overlap to Cascade in Poweramp Equalizer,",
        "# Core EQ: or the filters will not sum the way this file assumes.",
    ]
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
    transition_hz_: float = DEFAULT_TRANSITION_HZ,
    correction_range_hz: tuple[float, float] = (F_MIN, F_MAX),
    nulls_hz: list[float] | None = None,
    room: dict | None = None,
) -> str:
    """The Core EQ profile: measurement, provenance and the honest verdict.

    Carrying the capability verdict inside the profile is deliberate. A profile
    that travelled without its "this device could only reach sessions my own
    app opened" note would read as a promise on the next device it was loaded
    onto.

    The same reasoning applies to the correction ceiling and the nulls. A
    profile that says "corrected to 8 kHz" and hides that the room had a
    15 dB cancellation at 84 Hz that was deliberately left alone would be
    quietly dishonest about what the equaliser is doing.
    """
    payload = {
        "format": "corebuilds.core-eq/1",
        "device": device,
        "microphone": mic,
        "stimulus": stimulus,
        "capture_seconds": seconds,
        "sample_rate": FS,
        "target": target,
        "correction_range_hz": [float(correction_range_hz[0]),
                                float(correction_range_hz[1])],
        "transition_hz": float(transition_hz_),
        "gain_limits_db": {"boost": MAX_BOOST_DB, "cut": MAX_CUT_DB,
                           "shaping": MAX_SHAPING_DB},
        "nulls_untouched_hz": [float(hz) for hz in (nulls_hz or [])],
        "room": room or {},
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


def read_wav(path: str) -> tuple[int, np.ndarray]:
    """Read a 16-bit, 24-bit, or 32-bit WAV file. Multi-channel is averaged to mono."""
    with wave.open(path, "rb") as w:
        n_channels = w.getnchannels()
        sampwidth = w.getsampwidth()
        fs = w.getframerate()
        n_frames = w.getnframes()
        raw = w.readframes(n_frames)

    if sampwidth == 2:
        data = np.frombuffer(raw, dtype="<i2").astype(float) / 32768.0
    elif sampwidth == 4:
        data = np.frombuffer(raw, dtype="<i4").astype(float) / 2147483648.0
    elif sampwidth == 3:
        a = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3)
        data = (a[:, 0].astype(int) | (a[:, 1].astype(int) << 8) | ((a[:, 2].astype(int) & 0x7F) << 16) - ((a[:, 2].astype(int) & 0x80) << 16)).astype(float) / 8388608.0
    else:
        raise ValueError(f"unsupported sample width: {sampwidth}")

    if n_channels > 1:
        data = data.reshape(-1, n_channels).mean(axis=1)

    return fs, data


def resample_signal(data: np.ndarray, fs_in: int, fs_out: int = FS) -> np.ndarray:
    """Resample a 1D signal to a target sample rate via linear interpolation."""
    if fs_in == fs_out:
        return data
    target_n = int(round(len(data) * float(fs_out) / float(fs_in)))
    orig_idx = np.arange(len(data))
    new_idx = np.linspace(0, len(data) - 1, target_n)
    return np.interp(new_idx, orig_idx, data)


def analyze_sweep_recording(
    recording: np.ndarray,
    fs: int = FS,
    sweep_seconds: float = ESS_SECONDS,
    target: str = "dialogue",
    room_volume_m3: float = 54.0,
    cut_only: bool = False,
    n_filters: int = PEAKING_FILTERS,
) -> dict:
    """Analyze a recorded room sweep and generate custom Poweramp EQ filters.

    Performs Farina deconvolution against the inverse sweep filter, extracts the
    impulse response direct arrival, calculates room RT60 (Schroeder backward
    integration), sets the room transition frequency, detects loudspeaker roll-off
    and acoustic cancellations (nulls), runs minimum-phase gating, and generates
    the two-regime correction curve and peaking filters.
    """
    if fs != FS:
        recording = resample_signal(recording, fs, FS)
        fs = FS

    _, inverse = sine_sweep(seconds=sweep_seconds, fs=fs)
    ir_full = deconvolve_ir(recording, inverse, fs=fs)
    peak = int(np.argmax(np.abs(ir_full)))

    # Estimate RT60 from decay tail
    rt60 = rt60_from_ir(ir_full[peak:], fs=fs)
    t_hz = transition_hz(room_volume_m3, rt60)

    # Crop IR starting at direct sound arrival
    ir_crop = ir_full[peak : peak + NFFT]
    freqs, mag_db = ir_magnitude_db(ir_crop, fs=fs)
    centres, measured_db = octave_bands(freqs, mag_db, n=3.0)

    nulls = detect_nulls(centres, measured_db)
    rolloff = detect_low_rolloff(centres, measured_db)
    target_db = target_curve(target, centres)

    ex_freqs, excess_ms = excess_group_delay_ms(ir_crop, fs=fs)
    excess_centres = np.interp(centres, ex_freqs, excess_ms)
    min_phase_ok = min_phase_gate(centres, excess_centres)

    f_floor = max(F_MIN, rolloff)
    corr = correction_curve(
        centres, measured_db, target_db,
        f_min=f_floor,
        transition_hz_=t_hz,
        cut_only=cut_only,
        null_mask=nulls,
        min_phase_ok=min_phase_ok,
    )

    filters = fit_peaking_filters(centres, corr, n_filters=n_filters)
    preset_txt = export_parametric_txt(filters, band=(f_floor, F_MAX))

    return {
        "fs": fs,
        "rt60_s": rt60,
        "transition_hz": t_hz,
        "rolloff_hz": rolloff,
        "f_floor_hz": f_floor,
        "nulls_detected": int(nulls.sum()),
        "min_phase_passed_fraction": float(min_phase_ok.mean()),
        "centres": centres,
        "measured_db": measured_db,
        "target_db": target_db,
        "correction_db": corr,
        "filters": filters,
        "preset_txt": preset_txt,
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _demo() -> dict:
    freqs, _ = welch_db(pink_noise(seconds=0.5), FS)
    centres, _ = octave_bands(freqs, np.zeros_like(freqs), n=3.0)

    measured = synthetic_room_db(centres)
    volume, rt60 = 54.0, 0.5
    t_hz = transition_hz(volume, rt60)
    nulls = detect_nulls(centres, measured)
    rolloff = detect_low_rolloff(centres, measured)

    target = target_curve("dialogue", centres)
    correction = correction_curve(centres, measured, target,
                                 transition_hz_=t_hz,
                                 f_min=max(F_MIN, rolloff),
                                 null_mask=nulls)
    filters = fit_peaking_filters(centres, correction, n_filters=6)

    bands = collapse_to_bands([60, 230, 910, 3600, 14000],
                              lambda hz: float(np.interp(hz, centres, correction)),
                              -1500, 1500)

    print("Core EQ reference chain — worked example\n")
    print(f"stimulus        exponential sine sweep, {ESS_SECONDS:.0f}s at {FS} Hz")
    print(f"analysis        deconvolution -> impulse response -> 1/3-octave display")
    print(f"room            {volume:.0f} m3, RT60 {rt60:.1f}s -> "
          f"Schroeder {schroeder_hz(volume, rt60):.0f} Hz, "
          f"transition {t_hz:.0f} Hz")
    print(f"loudspeaker     roll-off detected at {rolloff:.0f} Hz -> correction floor")
    print(f"target          dialogue")
    print(f"correction band {max(F_MIN, rolloff):.0f}-{F_MAX:.0f} Hz, "
          f"inversion below {t_hz:.0f} Hz, shaping above")
    print(f"nulls           {int(nulls.sum())} band(s) left alone "
          f"(cancellations; boosting them cannot work)")
    print(f"gain budget     +{MAX_BOOST_DB:.0f} / -{MAX_CUT_DB:.0f} dB below the transition, "
          f"+/-{MAX_SHAPING_DB:.0f} dB shaping above; slope <= {MAX_SLOPE_DB_PER_OCT:.0f} dB/oct")
    print()
    print(f"{'Hz':>8} {'measured':>10} {'target':>8} {'correction':>11}")
    for f, m, t, c in zip(centres, measured, target, correction):
        if 31 <= f <= 12500:
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
    print(export_parametric_txt(filters, band=(max(F_MIN, rolloff), F_MAX)))

    return {"filters": filters, "bands": bands, "bands_count": len(centres),
            "transition_hz": t_hz, "nulls": int(nulls.sum())}


def _delta_ir(delay: int) -> np.ndarray:
    """An impulse response that is a single unit sample at [delay]."""
    ir = np.zeros(4096)
    ir[delay] = 1.0
    return ir


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

    measured = synthetic_room_db(c)

    # Targets are shape, normalised at the pivot.
    for kind in TARGET_CURVES:
        t = target_curve(kind, c)
        check(f"target {kind} pivots at 0 dB",
              abs(float(np.interp(630.0, c, t))) < 0.05)

    # The room model: Schroeder, transition, nulls, roll-off.
    check("schroeder frequency matches the literature",
          abs(schroeder_hz(54.0, 0.5) - 192.0) < 6.0,
          f"{schroeder_hz(54.0, 0.5):.0f} Hz for 54 m3 at RT60 0.5 s")
    check("transition falls back honestly when the room is unknown",
          transition_hz() == UNKNOWN_ROOM_TRANSITION_HZ)
    check("transition is capped at the field's ceiling",
          transition_hz(10.0, 2.0) <= DEFAULT_TRANSITION_HZ,
          f"{transition_hz(10.0, 2.0):.0f} Hz for a 10 m3 room")

    nulls = detect_nulls(c, measured)
    check("null detection finds the synthetic cancellation",
          bool(nulls[np.argmin(np.abs(c - 84.0))]),
          f"{int(nulls.sum())} band(s) marked")
    check("null detection ignores ordinary ripple",
          float(nulls.mean()) < 0.35, f"{float(nulls.mean()):.2f} of bands marked")

    rolloff = detect_low_rolloff(c, measured)
    check("roll-off detection stays inside the band",
          F_MIN <= rolloff <= F_MAX, f"{rolloff:.0f} Hz")

    # Variable smoothing: fine below the transition, coarse above it.
    smooth_fine = smooth_variable(c, measured, transition_hz_=300.0)
    check("variable smoothing tracks the measurement",
          float(np.max(np.abs(smooth_fine - smooth_octave(c, measured, 1.0)))) < 20.0)
    check("variable smoothing is finite everywhere",
          bool(np.all(np.isfinite(smooth_fine))))

    # Sweep deconvolution really recovers a known system.
    sweep, inverse = sine_sweep(seconds=2.0)
    known = peaking_magnitude_db(c, 400.0, 1.2, 8.0)
    known_lin = 10 ** (known / 20.0)
    ir_known = np.fft.irfft(known_lin, 4096)
    captured = np.convolve(sweep, ir_known)[: sweep.size + 4096]
    recovered = deconvolve_ir(captured, inverse)
    check("deconvolution yields a finite impulse response",
          bool(np.all(np.isfinite(recovered))) and float(np.max(np.abs(recovered))) > 0)

    # A decay that falls 60 dB in 0.25 s: amplitude 10^(-12t), since
    # 20*log10(10^(-12t)) = -240t dB/s and 0.25 s of that is -60 dB.
    t = np.arange(FS) / float(FS)
    rt60 = rt60_from_ir(10 ** (-12.0 * t))
    check("RT60 estimate follows a synthetic decay",
          0.18 < rt60 < 0.32, f"{rt60:.2f} s for a decay of 60 dB in 0.25 s")

    # A minimum-phase region gates open; a delayed one does not. The fixtures
    # are deltas rather than filter responses on purpose: a delta at the origin
    # is the identity system, which is minimum phase by construction, and a
    # delta shifted by 400 samples (8.3 ms) is pure delay — flat magnitude, so
    # its *entire* group delay is excess.
    gd_f, gd_ms = excess_group_delay_ms(_delta_ir(0))
    check("minimum-phase gate exists and is boolean",
          min_phase_gate(gd_f, gd_ms).dtype == bool
          and min_phase_gate(gd_f, gd_ms).size == gd_f.size)
    check("an unshifted system gates open",
          float(np.nanmax(np.abs(gd_ms[1:200]))) < 1.0,
          f"{float(np.nanmax(np.abs(gd_ms[1:200]))):.2f} ms of excess")
    _, gd_late = excess_group_delay_ms(_delta_ir(400))
    check("a time-shifted system is rejected by the gate",
          not bool(min_phase_gate(gd_f, gd_late)[1:200].all()),
          f"{float(np.nanmedian(gd_late[1:200])):.2f} ms of excess "
          f"against a {MIN_PHASE_TOLERANCE_MS:.0f} ms tolerance")

    # Energy-mean averaging really is an energy mean. Averaging 10 dB and 0 dB
    # in the energy domain gives 10*log10((10 + 1)/2) = 7.4 dB; averaging in
    # the dB domain would give 5.0.
    avg = average_measurements([np.array([10.0, 0.0]), np.array([0.0, 10.0])])
    expected = 10.0 * math.log10((10 ** (10.0 / 10.0) + 10 ** (0.0 / 10.0)) / 2.0)
    check("averaging is done in energy, not in dB",
          abs(float(avg[0]) - expected) < 0.05,
          f"{float(avg[0]):.2f} dB, energy mean {expected:.2f} dB, "
          f"dB mean would be 5.00 dB")

    bk = target_curve("bk", c)
    check("B&K rolls off in the treble",
          float(np.interp(20000.0, c, bk)) < -4.0,
          f"{float(np.interp(20000.0, c, bk)):.2f} dB at 20 kHz")

    target = target_curve("room", c)
    corr = correction_curve(c, measured, target)
    inside = (c >= F_MIN) & (c <= F_MAX)

    check("correction respects gain budget",
          float(corr.max()) <= MAX_BOOST_DB + 1e-6
          and float(corr.min()) >= -MAX_CUT_DB - 1e-6,
          f"{float(corr.min()):.2f} .. {float(corr.max()):.2f} dB")
    check("correction zero outside its band",
          bool(np.all(np.abs(corr[~inside]) < 1e-9)))

    # Two regimes: full correction below the transition, shaping above it.
    t_hz = DEFAULT_TRANSITION_HZ
    below = corr[(c >= F_MIN) & (c <= t_hz / 2)]
    above = corr[c >= t_hz * 2]
    check("shaping budget above the transition is tighter than the correction budget",
          float(np.max(np.abs(above))) <= MAX_SHAPING_DB + 1e-6
          if above.size else True,
          f"max |{float(np.max(np.abs(above))) if above.size else 0:.2f}| dB above {t_hz:.0f} Hz")

    # Nulls are never filled: a measurement with a deep dip gets no boost there.
    spike_room = np.zeros_like(c)
    spike_room += -16.0 * np.exp(-((np.log2(c / 84.0)) ** 2) / (2 * 0.10 ** 2))
    corr_null = correction_curve(c, spike_room, np.zeros_like(c), f_min=F_MIN, f_max=F_MAX)
    at_null = int(np.argmin(np.abs(c - 84.0)))
    check("a deep null is not boosted into",
          float(corr_null[at_null]) <= 1e-6,
          f"{float(corr_null[at_null]):+.2f} dB at the cancellation")
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
              target="dialogue", freqs=c, measured_db=measured,
              correction_db=corr, filters=filt,
              capability={}))["preamp_db"] == preamp_db(filt))

    profile = json.loads(export_profile_json(
        device="test", mic="remote", stimulus="pink", seconds=PINK_SECONDS,
        target="dialogue", freqs=c, measured_db=measured, correction_db=corr,
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
                        help="write the sine-sweep WAV the app ships as an asset")
    parser.add_argument("--seconds", type=float, default=ESS_SECONDS)
    parser.add_argument("--pink", action="store_true",
                        help="write pink noise instead of the sweep (cross-check / RTA)")
    parser.add_argument("--process", metavar="WAV_PATH",
                        help="analyze a recorded sweep WAV and generate a Poweramp preset")
    parser.add_argument("--out", metavar="OUT_PATH", default="poweramp_preset.txt",
                        help="output path for the generated Poweramp preset (default: poweramp_preset.txt)")
    parser.add_argument("--target", choices=TARGET_CURVES, default="dialogue",
                        help="target curve (default: dialogue)")
    parser.add_argument("--volume", type=float, default=54.0,
                        help="estimated room volume in m3 (default: 54.0)")
    parser.add_argument("--cut-only", action="store_true",
                        help="cut-only mode (never boost)")
    args = parser.parse_args(argv)

    if args.stimulus:
        signal = pink_noise(seconds=args.seconds) if args.pink else sine_sweep(
            seconds=args.seconds)[0]
        write_wav(args.stimulus, signal)
        kind = "pink noise" if args.pink else "sine sweep"
        print(f"wrote {args.stimulus} ({args.seconds:.0f}s {kind}, {FS} Hz mono)")
        return 0
    if args.process:
        fs_in, raw_data = read_wav(args.process)
        print(f"Loaded {args.process}: {len(raw_data)/fs_in:.2f}s at {fs_in} Hz")
        res = analyze_sweep_recording(
            raw_data, fs=fs_in, sweep_seconds=args.seconds,
            target=args.target, room_volume_m3=args.volume,
            cut_only=args.cut_only
        )
        Path(args.out).write_text(res["preset_txt"], encoding="utf-8")
        print(f"Room analysis:")
        print(f"  RT60 decay:           {res['rt60_s']:.2f} s")
        print(f"  Schroeder transition: {res['transition_hz']:.0f} Hz (inversion below, shaping above)")
        print(f"  Loudspeaker roll-off: {res['rolloff_hz']:.0f} Hz (correction floor: {res['f_floor_hz']:.0f} Hz)")
        print(f"  Nulls left alone:     {res['nulls_detected']} band(s)")
        print(f"  Min-phase gate:       {res['min_phase_passed_fraction']*100:.0f}% bands valid")
        print(f"  Target curve:         {args.target}")
        print(f"Wrote Poweramp preset to {args.out}:")
        print(res["preset_txt"])
        return 0
    if args.selftest:
        return _selftest()
    _demo()
    return 0


if __name__ == "__main__":
    sys.exit(main())
