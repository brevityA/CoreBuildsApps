#!/usr/bin/env python3
"""Core EQ's Kotlin correction limits and constants must equal the Python DSP reference.

`tools/core_eq_dsp.py` is the reference the Android measurement chain has to
agree with (docs/CORE_EQ_PLAN.md, M2). This holds the shared numbers
together without an Android SDK: the trusted band and the boost/cut caps
in `CorrectionLimits.kt` and `dsp/DspConstants.kt` are read from source and compared.
"""
from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import core_eq_dsp as dsp  # noqa: E402

KOTLIN_LIMITS = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/CorrectionLimits.kt"
KOTLIN_CONSTANTS = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/dsp/DspConstants.kt"
KOTLIN_DSP = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/dsp"
# Measurement constants that live beside the code that uses them (1.3.2).
SOURCE_PAIRS = {
    ("SweepAnalysis.kt", "SINGLE_SEAT_BOOST_DB"): "SINGLE_SEAT_BOOST_DB",
    ("SweepAnalysis.kt", "MIN_DECAY_RANGE_DB"): "MIN_DECAY_RANGE_DB",
    ("SweepAnalysis.kt", "MIN_BAND_HZ"): "MIN_BAND_HZ",
    ("SweepAnalysis.kt", "RT60_MAX_TAIL_S"): "RT60_MAX_TAIL_S",
    ("Correction.kt", "MAX_NULL_SKIP"): "MAX_NULL_SKIP",
}
PAIRS = {
    "MIN_HZ": "F_MIN",
    "MAX_HZ": "F_MAX",
    "MAX_BOOST_DB": "MAX_BOOST_DB",
    "MAX_CUT_DB": "MAX_CUT_DB"
}
DSP_PAIRS = {
    "FS": "FS",
    "F_MIN": "F_MIN",
    "F_MAX": "F_MAX",
    "MAX_BOOST_DB": "MAX_BOOST_DB",
    "MAX_CUT_DB": "MAX_CUT_DB",
    "MAX_SHAPING_DB": "MAX_SHAPING_DB",
    "MAX_SLOPE_DB_PER_OCT": "MAX_SLOPE_DB_PER_OCT",
    "DEFAULT_TRANSITION_HZ": "DEFAULT_TRANSITION_HZ",
    "UNKNOWN_ROOM_TRANSITION_HZ": "UNKNOWN_ROOM_TRANSITION_HZ",
    # Added after the Kotlin port shipped ROLLOFF_DROP_DB = 10 against the
    # reference's 6, which raised every correction floor.
    "ROLLOFF_DROP_DB": "ROLLOFF_DROP_DB",
    "NULL_DEPTH_DB": "NULL_DEPTH_DB",
    "NFFT": "NFFT",
    "ESS_SECONDS": "ESS_SECONDS",
    "MIN_PHASE_TOLERANCE_MS": "MIN_PHASE_TOLERANCE_MS",
    # 1.7.1: graded trust and the seat-count boost budget. These change what the
    # corrector asks for, so the reference and the app must agree on them.
    "MULTI_SEAT_BOOST_DB": "MULTI_SEAT_BOOST_DB",
    "CONFIDENCE_LO_SNR_DB": "CONFIDENCE_LO_SNR_DB",
    "CONFIDENCE_FULL_SNR_DB": "CONFIDENCE_FULL_SNR_DB",
    "CONFIDENCE_MIN": "CONFIDENCE_MIN",
}


class CorrectionLimitParity(unittest.TestCase):
    def test_kotlin_limits_match_the_reference(self):
        src = KOTLIN_LIMITS.read_text(encoding="utf-8")
        for kotlin_name, py_name in PAIRS.items():
            with self.subTest(constant=kotlin_name):
                m = re.search(rf"const val {kotlin_name} = ([0-9.]+)", src)
                self.assertIsNotNone(m, f"{kotlin_name} missing from {KOTLIN_LIMITS.name}")
                self.assertEqual(float(m.group(1)), float(getattr(dsp, py_name)))

    def test_kotlin_dsp_constants_match_the_reference(self):
        src = KOTLIN_CONSTANTS.read_text(encoding="utf-8")
        for kotlin_name, py_name in DSP_PAIRS.items():
            with self.subTest(constant=kotlin_name):
                m = re.search(rf"const val {kotlin_name} = ([0-9.]+)", src)
                self.assertIsNotNone(m, f"{kotlin_name} missing from {KOTLIN_CONSTANTS.name}")
                self.assertEqual(float(m.group(1)), float(getattr(dsp, py_name)))

    def test_kotlin_measurement_constants_match_the_reference(self):
        for (file, kotlin_name), py_name in SOURCE_PAIRS.items():
            with self.subTest(constant=kotlin_name):
                src = (KOTLIN_DSP / file).read_text(encoding="utf-8")
                m = re.search(rf"const val {kotlin_name} = ([0-9.]+)", src)
                self.assertIsNotNone(m, f"{kotlin_name} missing from {file}")
                self.assertEqual(float(m.group(1)), float(getattr(dsp, py_name)))



    def test_kotlin_ceiling_choices_match_the_reference(self):
        src = KOTLIN_LIMITS.read_text(encoding="utf-8")
        m = re.search(r"CEILING_CHOICES_HZ = doubleArrayOf\(([^)]*)\)", src)
        self.assertIsNotNone(m, "CEILING_CHOICES_HZ missing from CorrectionLimits.kt")
        values = []
        for raw in m.group(1).split(","):
            raw = raw.strip()
            if not raw:
                continue
            if raw == "MAX_HZ":
                values.append(dsp.F_MAX)
            else:
                values.append(float(raw))
        self.assertEqual(values, list(dsp.CORRECTION_CEILING_CHOICES_HZ))

    def test_ceiling_never_widens_and_never_leaves_the_trusted_band(self):
        for requested in (-1.0, 0.0, 20.0, 40.0, 500.0, 8000.0, 20000.0, float("nan"),
                          float("inf"), float("-inf"), None):
            with self.subTest(requested=requested):
                value = dsp.ceiling_hz(requested)
                self.assertGreaterEqual(value, dsp.F_MIN)
                self.assertLessEqual(value, dsp.F_MAX)

    def test_effective_ceiling_takes_the_narrower_of_measurement_and_choice(self):
        self.assertEqual(dsp.effective_ceiling_hz(5600.0, None), 5600.0)
        self.assertEqual(dsp.effective_ceiling_hz(5600.0, 1000.0), 1000.0)
        self.assertEqual(dsp.effective_ceiling_hz(1000.0, 4000.0), 1000.0)
        self.assertEqual(dsp.effective_ceiling_hz(float("nan"), 500.0), 500.0)
        self.assertEqual(dsp.effective_ceiling_hz(0.0, None), dsp.F_MAX)

if __name__ == "__main__":
    unittest.main()
