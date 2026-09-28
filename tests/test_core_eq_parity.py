#!/usr/bin/env python3
"""Core EQ's Kotlin correction limits must equal the Python DSP reference.

`tools/core_eq_dsp.py` is the reference the Android measurement chain has to
agree with (docs/CORE_EQ_PLAN.md, M2). This holds the first shared numbers
together today, without an Android SDK: the trusted band and the boost/cut caps
in `coreeq/.../CorrectionLimits.kt` are read from source and compared.
"""
from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import core_eq_dsp as dsp  # noqa: E402

KOTLIN = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/CorrectionLimits.kt"
PAIRS = {"MIN_HZ": "F_MIN", "MAX_HZ": "F_MAX",
         "MAX_BOOST_DB": "MAX_BOOST_DB", "MAX_CUT_DB": "MAX_CUT_DB"}


class CorrectionLimitParity(unittest.TestCase):
    def test_kotlin_limits_match_the_reference(self):
        src = KOTLIN.read_text(encoding="utf-8")
        for kotlin_name, py_name in PAIRS.items():
            with self.subTest(constant=kotlin_name):
                m = re.search(rf"const val {kotlin_name} = ([0-9.]+)", src)
                self.assertIsNotNone(m, f"{kotlin_name} missing from {KOTLIN.name}")
                self.assertEqual(float(m.group(1)), float(getattr(dsp, py_name)))


if __name__ == "__main__":
    unittest.main()
