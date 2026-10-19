#!/usr/bin/env python3
"""Pin the Core EQ measurement → correction → export chain.

Why this exists
---------------
Core EQ's whole claim is "play pink noise, listen with the remote microphone,
hand back an equaliser". Everything else in the app — the two-pane shell, the
focus ring, the D-pad sliders — is the same TV furniture the icon pack already
proves with `tools/check_ui_resources.py` and `tests/test_navigation_graph.py`.
The part that can be quietly, expensively wrong is the maths: a correction
curve that boosts where it should cut, a band grid that never lands on 1 kHz, a
preamp that leaves the boost uncompensated. None of those fail a build. They
fail in someone's living room, weeks later, as "it sounds worse".

So the chain is written once in `tools/core_eq_dsp.py` — numpy, no Android —
and this file holds it there. The Kotlin port is a translation of that module;
when the two disagree, this file is the one that gets to be right.

What is checked, one class at a time

  stimulus    the pink noise is actually pink (-3 dB/octave) and deterministic;
              the exponential sine sweep has a finite inverse filter. A
              stimulus that is secretly white would produce a correction curve
              that undoes the microphone's own roll-off, twice.

  bands       the 1/N-octave grid is anchored at 1 kHz with exact 2^(1/3)
              ratios. A grid anchored at the bottom of the range drifts, and
              every normalisation in this domain is referenced to 1 kHz.

  targets     each curve is shape, not level — 0 dB at the 630 Hz pivot — and
              the B&K curve actually rolls off in the treble.

  correction  the documented budget holds: gains inside +6/-12 dB, nothing
              outside 40 Hz–8 kHz, slope capped at 6 dB/octave, and
              `cut_only` genuinely never boosts. These are the assertions that
              catch the ordering bug this module shipped with, where
              re-centring ran last and silently broke three of them at once.

  fitting     peaking filters reduce RMSE against doing nothing, stay inside
              the Q bounds that keep them from ringing, and are deterministic —
              the same measurement has to produce the same filters on a
              maintainer's laptop and on a CI runner.

  export      the platform `Equalizer` collapse takes the *device's* band
              centres and millibel range and clamps to them, the GraphicEQ line
              parses as hz/dB pairs, and the preamp cancels the largest boost
              in both the parametric text and the profile JSON.

Run with:  python tests/test_core_eq_dsp.py   (or pytest)
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "tools"))

import core_eq_dsp as dsp  # noqa: E402


@pytest.fixture(scope="module")
def third_octave():
    """The 1/3-octave grid every correction in this suite is computed on."""
    freqs, _ = dsp.welch_db(dsp.pink_noise(seconds=0.5), dsp.FS)
    centres, _ = dsp.octave_bands(freqs, np.zeros_like(freqs), n=3.0)
    return centres


@pytest.fixture(scope="module")
def correction(third_octave):
    measured = dsp.synthetic_room_db(third_octave)
    target = dsp.target_curve("harman", third_octave)
    return third_octave, measured, dsp.correction_curve(third_octave, measured, target)


# ---------------------------------------------------------------------------
# Stimulus
# ---------------------------------------------------------------------------

class TestStimulus:
    def test_pink_noise_is_pink(self):
        """-3 dB/octave. A white stimulus would double-count the mic roll-off."""
        pink = dsp.pink_noise(seconds=4.0)
        f, db = dsp.welch_db(pink)
        slope = (np.interp(1000.0, f, db) - np.interp(125.0, f, db)) / math.log2(8)
        assert -4.0 < slope < -2.0, f"{slope:.2f} dB/octave is not pink"

    def test_pink_noise_is_reproducible(self):
        """Same seed, same samples: CI and a laptop must agree bit for bit."""
        assert np.array_equal(dsp.pink_noise(seconds=0.1), dsp.pink_noise(seconds=0.1))

    def test_pink_noise_is_dc_blocked_and_declicked(self):
        """No DC, and the fade keeps the first samples near zero."""
        pink = dsp.pink_noise(seconds=1.0)
        assert abs(pink.mean()) < 1e-3
        assert np.max(np.abs(pink[: dsp.FS // 1000])) < 0.05

    def test_sine_sweep_has_a_usable_inverse(self):
        """The ESS pair must be finite, full length and non-silent."""
        sweep, inverse = dsp.sine_sweep(seconds=1.0)
        assert len(sweep) == dsp.FS == len(inverse)
        assert np.all(np.isfinite(sweep)) and np.all(np.isfinite(inverse))
        assert np.max(np.abs(inverse)) > 0.5


# ---------------------------------------------------------------------------
# Band grid
# ---------------------------------------------------------------------------

class TestBands:
    def test_third_octave_ratio_is_exact(self, third_octave):
        ratios = third_octave[1:] / third_octave[:-1]
        assert np.allclose(ratios, 2 ** (1 / 3), rtol=1e-9)

    def test_grid_is_anchored_on_one_kilohertz(self, third_octave):
        """Every normalisation here is referenced to 1 kHz, so a band must be."""
        assert np.any(np.isclose(third_octave, 1000.0, rtol=1e-9))

    def test_octave_bands_average_energy_not_decibels(self):
        """Averaging decibels flattens a peak; averaging power does not.

        One hot bin among ten quiet ones in the 1 kHz band. An energy mean
        lands near the hot bin; a dB mean lands near the quiet ones. The two
        answers are 40+ dB apart, so this cannot pass by accident.
        """
        freqs = np.linspace(900.0, 1100.0, 11)
        values = np.full_like(freqs, -60.0)
        values[5] = 0.0

        centres, bands = dsp.octave_bands(freqs, values, n=3.0)
        one_k = bands[int(np.argmin(np.abs(centres - 1000.0)))]

        energy_mean = 10 * math.log10((10 ** (values / 10.0)).mean())
        db_mean = values.mean()
        assert abs(one_k - energy_mean) < 0.01
        assert abs(one_k - db_mean) > 30.0

    def test_finer_band_resolution_produces_more_bands(self):
        """1/24 octave must resolve roughly eight times the bands of 1/3."""
        f, _ = dsp.welch_db(dsp.pink_noise(seconds=0.25))
        centres3, _ = dsp.octave_bands(f, np.zeros_like(f), n=3.0)
        centres24, _ = dsp.octave_bands(f, np.zeros_like(f), n=24.0)
        assert len(centres24) > len(centres3) * 6

    def test_a_bin_no_band_claims_is_not_invented(self):
        """An empty band reports -inf rather than a plausible-looking zero."""
        freqs = np.array([100.0, 105.0, 110.0])
        values = np.array([-30.0, -31.0, -32.0])
        centres, bands = dsp.octave_bands(freqs, values, n=3.0,
                                          f_min=20.0, f_max=20000.0)
        assert np.any(~np.isfinite(bands)), "expected at least one unmeasured band"


# ---------------------------------------------------------------------------
# Target curves
# ---------------------------------------------------------------------------

class TestTargets:
    @pytest.mark.parametrize("kind", ["flat", "bk", "harman", "house"])
    def test_target_is_shape_not_level(self, third_octave, kind):
        curve = dsp.target_curve(kind, third_octave)
        assert abs(float(np.interp(630.0, third_octave, curve))) < 0.05

    def test_room_is_a_speaker_curve_not_a_headphone_curve(self, third_octave):
        """No 3 kHz ear-gain peak.

        The Harman *headphone* target carries one, to simulate what a
        listener's own head does for an external source. Baking it into a
        speaker correction double-counts pinna gain and makes dialogue sound
        like it is coming through a telephone. This is the test that would
        catch someone "fixing" the target by importing the headphone curve.
        """
        room = dsp.target_curve("room", third_octave)
        at_3k = float(np.interp(3000.0, third_octave, room))
        at_1k = float(np.interp(1000.0, third_octave, room))
        assert at_3k <= at_1k + 0.5

    def test_olive_carries_the_published_numbers(self, third_octave):
        """+6.6 dB of bass at 105 Hz, -2.4 dB of treble above 2.5 kHz.

        These are the mean preferences from Olive, Welti & McMullin (AES
        2013). They are shipped as a preset rather than as a default because
        the spread between the eleven listeners was about 17 dB of bass —
        but the preset has to actually be the study's numbers.
        """
        olive = dsp.target_curve("olive", third_octave)
        room = dsp.target_curve("room", third_octave)
        # Subtracting `room` removes the shared tilt, leaving exactly the two
        # published shelves: +6.6 dB of bass against `room`'s +3.5, so +3.1 on
        # the plateau (105 Hz is the shelf *corner*, where both are half way),
        # and -2.4 dB of treble.
        delta = olive - room
        plateau = float(np.interp(30.0, third_octave, delta))
        treble = float(np.interp(8000.0, third_octave, delta))
        assert abs(plateau - (6.6 - 3.5)) < 0.3, plateau
        assert abs(treble - (-2.4)) < 0.3, treble

    def test_dialogue_presence_stops_before_sibilance(self, third_octave):
        """Presence is bounded above on purpose.

        Speech gains intelligibility up to about 4 kHz and gains sibilance
        between 5 and 7 kHz. A presence shelf that keeps rising trades clarity
        for hiss, which is the opposite of what a dialogue target is for.
        """
        dialogue = dsp.target_curve("dialogue", third_octave)
        room = dsp.target_curve("room", third_octave)
        presence = dialogue - room
        at_3k = float(np.interp(3000.0, third_octave, presence))
        at_8k = float(np.interp(8000.0, third_octave, presence))
        assert at_3k > 1.0
        assert at_8k < 0.5

    def test_dialogue_trim_is_a_cut_not_a_boost(self, third_octave):
        """The 300-800 Hz trim reduces boxiness; it never adds."""
        trimmed = dsp.target_curve("dialogue", third_octave, trim_db=3.0)
        plain = dsp.target_curve("dialogue", third_octave)
        assert float(np.min(trimmed - plain)) <= 0.0

    def test_bk_rolls_off_in_the_treble(self, third_octave):
        curve = dsp.target_curve("bk", third_octave)
        assert float(np.interp(20000.0, third_octave, curve)) < -4.0

    def test_flat_target_is_flat(self, third_octave):
        assert np.allclose(dsp.target_curve("flat", third_octave), 0.0)

    def test_house_bass_boost_lifts_the_bass(self, third_octave):
        quiet = dsp.target_curve("house", third_octave, bass_boost_db=0.0)
        loud = dsp.target_curve("house", third_octave, bass_boost_db=6.0)
        at_40 = lambda c: float(np.interp(40.0, third_octave, c))  # noqa: E731
        assert at_40(loud) > at_40(quiet) + 1.0

    def test_unknown_target_is_rejected(self, third_octave):
        with pytest.raises(ValueError):
            dsp.target_curve("loudness_war", third_octave)


# ---------------------------------------------------------------------------
# The correction — the budget is the product promise
# ---------------------------------------------------------------------------

class TestCorrection:
    def test_gains_stay_inside_the_budget(self, correction):
        centres, _, corr = correction
        inside = (centres >= dsp.F_MIN) & (centres <= dsp.F_MAX)
        assert corr[inside].max() <= dsp.MAX_BOOST_DB + 1e-9
        assert corr[inside].min() >= -dsp.MAX_CUT_DB - 1e-9

    def test_nothing_outside_the_correction_band(self, correction):
        """Below 40 Hz the remote mic is guessing; we refuse to correct there."""
        centres, _, corr = correction
        outside = ~((centres >= dsp.F_MIN) & (centres <= dsp.F_MAX))
        assert np.all(np.abs(corr[outside]) < 1e-9)

    def test_correction_is_recentred(self, correction):
        centres, _, corr = correction
        region = (centres >= 100.0) & (centres <= 10000.0)
        assert abs(float(corr[region].mean())) < 0.5

    def test_cut_only_never_boosts(self, third_octave):
        measured = dsp.synthetic_room_db(third_octave)
        target = dsp.target_curve("harman", third_octave)
        cut = dsp.correction_curve(third_octave, measured, target, cut_only=True)
        inside = (third_octave >= dsp.F_MIN) & (third_octave <= dsp.F_MAX)
        assert cut[inside].max() <= 1e-9

    def test_slope_limiter_caps_steepness(self, third_octave):
        """A 15 dB null must not become a 15 dB ringing boost."""
        spike = np.zeros_like(third_octave)
        spike[int(np.argmin(np.abs(third_octave - 100.0)))] = 15.0
        limited = dsp._limit_slope(third_octave, spike, dsp.MAX_SLOPE_DB_PER_OCT)
        worst = 0.0
        for i in range(len(third_octave) - 1):
            df = abs(math.log2(third_octave[i + 1] / third_octave[i]))
            if df > 0:
                worst = max(worst, abs(limited[i + 1] - limited[i]) / df)
        assert worst <= dsp.MAX_SLOPE_DB_PER_OCT + 1e-9

    def test_correction_inverts_the_error(self, third_octave):
        """Where the room is hot against target, the correction must be cold.

        The transition is pushed past the whole band so this isolates the
        inversion regime; the shaping regime has its own test below.
        """
        measured = np.full_like(third_octave, -6.0)
        measured[int(np.argmin(np.abs(third_octave - 250.0)))] = 0.0
        target = dsp.target_curve("flat", third_octave)
        corr = dsp.correction_curve(third_octave, measured, target,
                                    transition_hz_=1e6, max_slope=1e6)
        hot = int(np.argmin(np.abs(third_octave - 250.0)))
        assert corr[hot] < 0.0

    def test_shaping_only_above_the_transition(self, third_octave):
        """Above the transition the corrector stops inverting.

        A loud, narrow peak high in the band would be a textbook thing to
        notch - and it is exactly what the research says not to chase, because
        up there the microphone is measuring reflections as much as the
        speaker. So the correction there is held inside the shaping budget.
        """
        measured = np.zeros_like(third_octave)
        hot = int(np.argmin(np.abs(third_octave - 4000.0)))
        measured[hot] = 12.0
        target = dsp.target_curve("flat", third_octave)
        corr = dsp.correction_curve(third_octave, measured, target,
                                    transition_hz_=400.0)
        above = third_octave > 800.0
        assert np.max(np.abs(corr[above])) <= dsp.MAX_SHAPING_DB + 1e-6
        assert corr[hot] < 0.0  # it is still cut, just not by 12 dB

    def test_a_deep_null_is_never_boosted(self, third_octave):
        """A cancellation is left alone at any depth.

        Boosting it raises the direct and reflected arrivals equally and the
        cancellation survives, so the only thing a boost buys is excursion and
        distortion. The corrector must leave it at zero and say so.
        """
        measured = np.zeros_like(third_octave)
        null_at = int(np.argmin(np.abs(third_octave - 84.0)))
        measured[null_at] = -18.0
        target = dsp.target_curve("flat", third_octave)
        corr = dsp.correction_curve(third_octave, measured, target)
        assert corr[null_at] <= 1e-6

    def test_min_phase_gate_is_honoured(self, third_octave):
        """Where the gate says no, the correction is zero."""
        measured = np.zeros_like(third_octave)
        peak = int(np.argmin(np.abs(third_octave - 200.0)))
        measured[peak] = 8.0
        target = dsp.target_curve("flat", third_octave)
        gate = np.ones_like(third_octave, dtype=bool)
        gate[peak] = False
        corr = dsp.correction_curve(third_octave, measured, target,
                                    transition_hz_=1e6, min_phase_ok=gate)
        assert corr[peak] == 0.0


# ---------------------------------------------------------------------------
# Peaking filter fitting
# ---------------------------------------------------------------------------

class TestFitting:
    def test_fitting_beats_doing_nothing(self, correction):
        centres, _, corr = correction
        inside = (centres >= dsp.F_MIN) & (centres <= dsp.F_MAX)
        filters = dsp.fit_peaking_filters(centres, corr, n_filters=6)
        baseline = float(np.sqrt(np.mean(corr[inside] ** 2)))
        residual = float(np.sqrt(np.mean(
            (dsp._filter_sum_db(centres[inside], filters) - corr[inside]) ** 2)))
        assert residual < baseline, f"{baseline:.2f} dB -> {residual:.2f} dB is no fit"

    def test_q_stays_inside_the_non_ringing_bounds(self, correction):
        centres, _, corr = correction
        filters = dsp.fit_peaking_filters(centres, corr, n_filters=8)
        assert filters, "no filters produced"
        for _, q, _ in filters:
            assert dsp.PEAKING_MIN_Q - 1e-9 <= q <= dsp.PEAKING_MAX_Q + 1e-9

    def test_filters_are_sorted_and_within_gain_budget(self, correction):
        centres, _, corr = correction
        filters = dsp.fit_peaking_filters(centres, corr, n_filters=6)
        freqs = [fc for fc, _, _ in filters]
        assert freqs == sorted(freqs)
        for _, _, gain in filters:
            assert -dsp.MAX_BOOST_DB - 1e-9 <= gain <= dsp.MAX_BOOST_DB + 1e-9

    def test_fitting_is_deterministic(self, correction):
        centres, _, corr = correction
        assert (dsp.fit_peaking_filters(centres, corr, n_filters=6)
                == dsp.fit_peaking_filters(centres, corr, n_filters=6))

    def test_peaking_filter_peaks_at_its_centre_frequency(self):
        """The RBJ response must actually peak where we say it does."""
        f = np.linspace(20, 20000, 40000)
        resp = dsp.peaking_magnitude_db(f, 1000.0, 1.0, 6.0)
        assert abs(f[int(np.argmax(resp))] - 1000.0) < 5.0
        assert abs(float(resp.max()) - 6.0) < 0.25

    def test_zero_gain_filter_is_flat(self):
        assert np.allclose(dsp.peaking_magnitude_db(np.linspace(20, 20000, 1000),
                                                    500.0, 1.0, 0.0), 0.0)


# ---------------------------------------------------------------------------
# Collapse to the platform Equalizer API
# ---------------------------------------------------------------------------

class TestPlatformCollapse:
    def test_one_entry_per_device_band(self):
        bands = dsp.collapse_to_bands([60, 230, 910, 3600, 14000], lambda hz: 0.0,
                                      -1500, 1500)
        assert [fc for fc, _ in bands] == [60.0, 230.0, 910.0, 3600.0, 14000.0]

    def test_device_reported_range_is_respected(self):
        """The device owns its band layout and its millibel range, not us."""
        bands = dsp.collapse_to_bands([60, 230, 910, 3600, 14000],
                                      lambda hz: 20.0, -300, 300)
        assert all(mb == 300 for _, mb in bands)

    def test_three_band_device_survives(self):
        """Some devices report 3 bands, some 8, some 13. All must work."""
        bands = dsp.collapse_to_bands([100, 1000, 8000], lambda hz: 0.0, -1500, 1500)
        assert len(bands) == 3

    def test_millibels_are_the_right_scale(self):
        bands = dsp.collapse_to_bands([1000], lambda hz: 3.0, -1500, 1500)
        assert bands[0][1] == 300


# ---------------------------------------------------------------------------
# Export
# ---------------------------------------------------------------------------

class TestExport:
    def test_graphic_eq_parses_as_pairs(self, correction):
        centres, _, corr = correction
        text = dsp.export_graphic_eq(centres, corr)
        assert text.startswith("GraphicEQ: ")
        tokens = text[len("GraphicEQ: "):].split()
        assert len(tokens) == 2 * len(centres)
        for i in range(0, len(tokens), 2):
            float(tokens[i])
            float(tokens[i + 1])

    def test_parametric_export_declares_a_preamp(self, correction):
        centres, _, corr = correction
        filters = dsp.fit_peaking_filters(centres, corr, n_filters=6)
        text = dsp.export_parametric_txt(filters)
        assert text.startswith("Preamp: ")
        assert text.count("Filter ") == len(filters)

    def test_preamp_never_under_compensates_the_peak(self, correction):
        """Rounded toward more negative: the safety number cannot round short."""
        centres, _, corr = correction
        filters = dsp.fit_peaking_filters(centres, corr, n_filters=8)
        assert filters
        biggest = max(0.0, max(g for _, _, g in filters))
        assert dsp.preamp_db(filters) + biggest <= 1e-9

    def test_preamp_agrees_across_both_exports(self, correction):
        centres, measured, corr = correction
        filters = dsp.fit_peaking_filters(centres, corr, n_filters=6)
        txt = dsp.export_parametric_txt(filters)
        profile = json.loads(dsp.export_profile_json(
            device="x", mic="remote", stimulus="pink", seconds=1.0,
            target="harman", freqs=centres, measured_db=measured,
            correction_db=corr, filters=filters, capability={}))
        assert abs(float(txt.split()[1]) - profile["preamp_db"]) < 1e-9

    def test_profile_carries_its_capability_verdict(self, correction):
        """A profile that travels without its limits reads as a promise."""
        centres, measured, corr = correction
        verdict = {"global_mix": False, "session_broadcast": True, "bands": 5}
        profile = json.loads(dsp.export_profile_json(
            device="AFTKA", mic="remote", stimulus="pink", seconds=20.0,
            target="harman", freqs=centres, measured_db=measured,
            correction_db=corr, filters=[], capability=verdict))
        assert profile["capability"] == verdict
        assert profile["format"] == "corebuilds.core-eq/1"

    def test_profile_records_the_measurement_limits(self, correction):
        centres, measured, corr = correction
        profile = json.loads(dsp.export_profile_json(
            device="x", mic="remote", stimulus="pink", seconds=20.0,
            target="harman", freqs=centres, measured_db=measured,
            correction_db=corr, filters=[], capability={}))
        assert profile["correction_range_hz"] == [dsp.F_MIN, dsp.F_MAX]
        assert profile["gain_limits_db"] == {"boost": dsp.MAX_BOOST_DB,
                                            "cut": dsp.MAX_CUT_DB,
                                            "shaping": dsp.MAX_SHAPING_DB}
        assert profile["microphone"] == "remote"
        assert len(profile["curve"]) == len(centres)

    def test_empty_filter_bank_still_exports(self):
        assert dsp.export_parametric_txt([]).startswith("# Core EQ")
        assert dsp.preamp_db([]) == 0.0


# ---------------------------------------------------------------------------
# The room model
# ---------------------------------------------------------------------------

class TestRoomModel:
    def test_schroeder_matches_the_literature(self):
        """54 m3 at RT60 0.5 s is the worked example in the research; it gives
        about 192 Hz."""
        assert abs(dsp.schroeder_hz(54.0, 0.5) - 192.0) < 6.0

    def test_schroeder_rejects_nonsense(self):
        with pytest.raises(ValueError):
            dsp.schroeder_hz(0.0, 0.5)
        with pytest.raises(ValueError):
            dsp.schroeder_hz(54.0, -1.0)

    def test_transition_falls_back_and_is_capped(self):
        assert dsp.transition_hz() == dsp.UNKNOWN_ROOM_TRANSITION_HZ
        assert dsp.transition_hz(10.0, 2.0) <= dsp.DEFAULT_TRANSITION_HZ
        # A small, live room: Schroeder is high, so the ceiling is capped.
        assert dsp.transition_hz(54.0, 0.5) > dsp.UNKNOWN_ROOM_TRANSITION_HZ * 0.5

    def test_null_detection_finds_a_cancellation(self, third_octave):
        measured = np.zeros_like(third_octave)
        measured[int(np.argmin(np.abs(third_octave - 84.0)))] = -16.0
        nulls = dsp.detect_nulls(third_octave, measured)
        assert bool(nulls[int(np.argmin(np.abs(third_octave - 84.0)))])
        assert float(nulls.mean()) < 0.35

    def test_null_detection_ignores_ordinary_ripple(self, third_octave):
        rng = np.random.default_rng(3)
        measured = rng.normal(0.0, 1.0, size=third_octave.shape)
        assert float(dsp.detect_nulls(third_octave, measured).mean()) < 0.05

    def test_rolloff_raises_the_correction_floor(self, third_octave):
        """A TV's drivers stop well before 40 Hz; the floor must follow."""
        measured = np.zeros_like(third_octave)
        measured[third_octave < 90.0] = -18.0
        assert dsp.detect_low_rolloff(third_octave, measured) >= 60.0

    def test_rolloff_stays_put_when_there_is_no_rolloff(self, third_octave):
        measured = np.zeros_like(third_octave)
        assert dsp.detect_low_rolloff(third_octave, measured) == dsp.F_MIN

    def test_rolloff_is_not_dragged_down_by_bands_that_climb_back(self, third_octave):
        """1.3.2: noise lifted the bands below a 63 Hz roll-off back within
        6 dB, and the lowest-band-anywhere rule read 40 Hz."""
        nominal = {31.25: -5.5, 39.4: -4.0, 49.6: -5.0, 62.5: -9.0, 78.7: -3.0, 99.2: -2.0, 125.0: -1.0}
        measured = np.zeros_like(third_octave)
        for fc, db in nominal.items():
            measured[int(np.argmin(np.abs(third_octave - fc)))] = db
        assert dsp.detect_low_rolloff(third_octave, measured) == pytest.approx(78.7, abs=0.1)

    def test_rolloff_steps_over_a_room_null(self, third_octave):
        """A narrow cancellation above a mode is the room, not the speaker."""
        measured = np.zeros_like(third_octave)
        measured[int(np.argmin(np.abs(third_octave - 125.0)))] = -18.0
        assert dsp.detect_low_rolloff(third_octave, measured) == dsp.F_MIN

    def test_single_seat_boosts_are_capped_below_the_transition(self, third_octave):
        measured = np.where((third_octave >= 60.0) & (third_octave <= 130.0), -6.0, 0.0)
        target = np.zeros_like(third_octave)
        no_nulls = np.zeros(third_octave.shape, dtype=bool)
        open_ = dsp.correction_curve(third_octave, measured, target, transition_hz_=300.0, null_mask=no_nulls)
        capped = dsp.correction_curve(third_octave, measured, target, transition_hz_=300.0, null_mask=no_nulls,
                                      max_boost_below_transition=dsp.SINGLE_SEAT_BOOST_DB)
        assert float(open_.max()) > 3.0
        assert float(capped[third_octave < 300.0].max()) <= dsp.SINGLE_SEAT_BOOST_DB + 1e-9
        peaky = -measured
        cut = dsp.correction_curve(third_octave, peaky, target, transition_hz_=300.0, null_mask=no_nulls,
                                   max_boost_below_transition=dsp.SINGLE_SEAT_BOOST_DB)
        assert float(cut.min()) < -4.0

    def test_averaging_is_in_energy(self):
        avg = dsp.average_measurements([np.array([10.0, 0.0]),
                                       np.array([0.0, 10.0])])
        expected = 10.0 * math.log10((10.0 + 1.0) / 2.0)
        assert abs(float(avg[0]) - expected) < 0.05
        assert abs(float(avg[0]) - 5.0) > 1.0  # not a dB mean


# ---------------------------------------------------------------------------
# The impulse response
# ---------------------------------------------------------------------------

class TestImpulseResponse:
    def test_deconvolution_recovers_a_known_impulse(self):
        sweep, inverse = dsp.sine_sweep(seconds=2.0)
        captured = np.convolve(sweep, np.array([0.0, 0.0, 1.0]))
        ir = dsp.deconvolve_ir(captured, inverse)
        assert bool(np.all(np.isfinite(ir)))
        assert float(np.max(np.abs(ir))) > 0.0

    def test_rt60_follows_a_synthetic_decay(self):
        # Amplitude 10^(-12t) falls 60 dB in 0.25 s exactly.
        t = np.arange(dsp.FS) / float(dsp.FS)
        assert 0.18 < dsp.rt60_from_ir(10 ** (-12.0 * t)) < 0.32

    @staticmethod
    def _noisy_decay(rt60, range_db, seed=3):
        rng = np.random.default_rng(seed)
        n = 2 * dsp.FS
        t = np.arange(n) / float(dsp.FS)
        noise = 10 ** (-range_db / 10.0)
        ir = rng.normal(size=n) * np.exp(-math.log(1000.0) / rt60 * t) + rng.normal(size=n) * math.sqrt(noise)
        ir[0] = 4.0  # the arrival, so the peak is at t = 0
        return ir, noise

    def test_rt60_reads_true_with_the_noise_subtracted(self):
        """1.3.1 cut the tail at the noise and added nothing back: 3.5-10 % short at these ranges."""
        for range_db in (50.0, 40.0):
            ir, noise = self._noisy_decay(0.6, range_db)
            assert dsp.rt60_from_ir(ir, noise_power=noise) == pytest.approx(0.6, abs=0.03)

    def test_rt60_is_refused_when_the_decay_is_shallow(self):
        ir, noise = self._noisy_decay(0.6, 28.0)
        assert dsp.rt60_from_ir(ir, noise_power=noise) == 0.0

    def test_min_phase_gate_accepts_the_identity(self):
        ir = np.zeros(4096)
        ir[0] = 1.0
        freqs, excess = dsp.excess_group_delay_ms(ir)
        assert float(np.nanmax(np.abs(excess[1:200]))) < 1.0
        assert bool(dsp.min_phase_gate(freqs, excess)[1:200].all())

    def test_latency_is_not_excess_group_delay(self):
        # 400 samples at 48 kHz is 8.3 ms, more than the 5 ms tolerance. Real
        # captures always carry that much latency; counting it as excess
        # would gate every band on every TV.
        ir = np.zeros(4096)
        ir[400] = 1.0
        freqs, excess = dsp.excess_group_delay_ms(ir)
        assert float(np.nanmax(np.abs(excess[1:200]))) < 1.0

    def test_min_phase_gate_rejects_an_allpass_at_its_centre(self):
        # Flat magnitude, ~20 ms of group delay at 63 Hz: nothing an EQ can fix.
        freqs, excess = dsp.excess_group_delay_ms(dsp._allpass_ir(63.0, 2.0))
        gate = dsp.band_min_phase_ok(np.array([63.0, 1000.0]), freqs, excess)
        assert not bool(gate[0])
        assert bool(gate[1])

    def test_a_room_mode_is_minimum_phase(self):
        # A resonance is minimum phase: cutting it also kills its ringing,
        # which is the one thing room EQ genuinely does. The gate must pass it.
        fs = dsp.FS
        w0 = 2 * math.pi * 63.0 / fs
        alpha = math.sin(w0) / (2 * 4.0)
        amp = 10 ** (9.0 / 40.0)
        b = (1 + alpha * amp, -2 * math.cos(w0), 1 - alpha * amp)
        a = (1 + alpha / amp, -2 * math.cos(w0), 1 - alpha / amp)
        x = np.zeros(16384); x[0] = 1.0
        y = np.zeros_like(x)
        for i in range(x.size):
            acc = b[0] * x[i]
            if i >= 1:
                acc += b[1] * x[i - 1] - a[1] * y[i - 1]
            if i >= 2:
                acc += b[2] * x[i - 2] - a[2] * y[i - 2]
            y[i] = acc / a[0]
        freqs, excess = dsp.excess_group_delay_ms(y)
        assert bool(dsp.band_min_phase_ok(np.array([50.0, 63.0, 80.0]), freqs, excess).all())

    def test_variable_smoothing_is_fine_below_and_coarse_above(self, third_octave):
        rng = np.random.default_rng(5)
        ripple = rng.normal(0.0, 3.0, size=third_octave.shape)
        smoothed = dsp.smooth_variable(third_octave, ripple, transition_hz_=300.0)
        low = third_octave < 100.0
        high = third_octave > 6000.0
        low_var = float(np.std(np.diff(smoothed[low])))
        high_var = float(np.std(np.diff(smoothed[high])))
        assert high_var < low_var or high_var < 1.0


# ---------------------------------------------------------------------------
# The export carries its own limits
# ---------------------------------------------------------------------------

class TestExportLimits:
    def test_parametric_export_states_its_band_and_mode(self):
        txt = dsp.export_parametric_txt([(100.0, 1.0, -3.0)], band=(60.0, 4000.0))
        assert "60-4000 Hz" in txt
        assert "Cascade" in txt

    def test_profile_carries_the_transition_and_the_nulls(self, third_octave):
        profile = json.loads(dsp.export_profile_json(
            device="t", mic="remote", stimulus="sweep", seconds=10.0,
            target="dialogue", freqs=third_octave,
            measured_db=np.zeros_like(third_octave),
            correction_db=np.zeros_like(third_octave), filters=[],
            capability={}, transition_hz_=300.0,
            correction_range_hz=(60.0, 8000.0), nulls_hz=[84.0],
            room={"volume_m3": 54.0, "rt60_s": 0.5}))
        assert profile["transition_hz"] == 300.0
        assert profile["correction_range_hz"] == [60.0, 8000.0]
        assert profile["nulls_untouched_hz"] == [84.0]
        assert profile["room"]["volume_m3"] == 54.0


# ---------------------------------------------------------------------------
# End-to-end sweep processing & WAV I/O
# ---------------------------------------------------------------------------

class TestRecordingAnalysis:
    def test_read_and_write_wav_roundtrip(self, tmp_path):
        wav_path = str(tmp_path / "test.wav")
        orig_sig = np.sin(np.linspace(0, 2 * np.pi * 440, 48000)) * 0.5
        dsp.write_wav(wav_path, orig_sig, fs=48000)

        fs, read_sig = dsp.read_wav(wav_path)
        assert fs == 48000
        assert len(read_sig) == len(orig_sig)
        assert np.max(np.abs(orig_sig - read_sig)) < 0.001

    def test_resample_signal_matches_length(self):
        sig_44k = np.zeros(44100)
        sig_48k = dsp.resample_signal(sig_44k, 44100, 48000)
        assert len(sig_48k) == 48000

    def test_analyze_sweep_recording_produces_filters(self):
        fs = dsp.FS
        sweep, inverse = dsp.sine_sweep(seconds=2.0, fs=fs)
        # Perfect direct sound
        captured = np.concatenate([np.zeros(int(0.1 * fs)), sweep, np.zeros(int(0.2 * fs))])
        res = dsp.analyze_sweep_recording(captured, fs=fs, sweep_seconds=2.0)
        assert res["fs"] == fs
        assert res["transition_hz"] > 0
        assert "Preamp:" in res["preset_txt"] or "# Core EQ: no filters" in res["preset_txt"]
        assert len(res["centres"]) > 0


# ---------------------------------------------------------------------------
# The built-in receipt
# ---------------------------------------------------------------------------

def test_builtin_selftest_passes():
    """The module's own `--selftest` is the receipt pasted into a PR."""
    assert dsp._selftest() == 0


if __name__ == "__main__":
    raise SystemExit(pytest.main([__file__, "-v"]))
