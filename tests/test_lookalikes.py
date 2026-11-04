#!/usr/bin/env python3
"""Rules of tools/check_lookalikes.py, on synthetic masks.

The checker itself runs against the built pack in CI; this pins what it
counts as a look-alike, so a change to the exemptions shows up as a test
failure rather than as a quietly larger or smaller gate.

Run: python3 tests/test_lookalikes.py
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import check_lookalikes as cl  # noqa: E402

SQUARE = np.zeros((64, 64), dtype=np.float32)
SQUARE[16:48, 16:48] = 1
SHIFTED = np.roll(SQUARE, 2, axis=1)   # IoU 30/34 ~ 0.88 against SQUARE
FAR = np.zeros((64, 64), dtype=np.float32)
FAR[0:8, 0:8] = 1


def icon(glyph, brand=None):
    row = {"name": glyph, "drawable": glyph, "glyph": glyph}
    if brand:
        row["brand"] = brand
    return row


def found(*rows_and_masks):
    rows = [r for r, _ in rows_and_masks]
    vecs = np.array([m.ravel() for _, m in rows_and_masks], dtype=np.float32)
    return cl.pairs(rows, vecs)


class LookalikeRules(unittest.TestCase):
    def test_two_brands_with_one_shape_are_a_pair(self):
        self.assertIn(("a_mark", "b_mark"), found((icon("a_mark"), SQUARE), (icon("b_mark"), SHIFTED)))

    def test_distinct_shapes_are_not(self):
        self.assertEqual(found((icon("a_mark"), SQUARE), (icon("b_mark"), FAR)), {})

    def test_one_brand_may_share_a_shape(self):
        rows = ((icon("a_mark", "x"), SQUARE), (icon("b_mark", "x"), SHIFTED))
        self.assertEqual(found(*rows), {})

    def test_one_glyph_on_two_icons_is_not_a_pair(self):
        self.assertEqual(found((icon("a_mark"), SQUARE), (dict(icon("a_mark"), drawable="a2"), SQUARE)), {})

    def test_wordmarks_with_different_text_are_exempt(self):
        # tbs_wm and tnt_wm are real stroke-letter wordmarks with different text
        self.assertTrue(cl.exempt(icon("tbs_wm"), icon("tnt_wm")))
        self.assertFalse(cl.exempt(icon("tbs_wm"), icon("a_e_mark")))

    def test_baseline_ratchets_both_ways(self):
        new, gone = cl.compare({("a", "b"): 0.9, ("c", "d"): 0.85}, {("a", "b"), ("e", "f")})
        self.assertEqual(new, [("c", "d")])
        self.assertEqual(gone, [("e", "f")])


if __name__ == "__main__":
    unittest.main()
