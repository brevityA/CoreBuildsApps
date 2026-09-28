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


if __name__ == "__main__":
    unittest.main()
