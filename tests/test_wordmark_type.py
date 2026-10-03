#!/usr/bin/env python3
"""Stroke-letter wordmarks keep one set of proportions.

Before 2.0.1 every _WM mark filled its box at a fixed 30px stroke: cap
heights ran 48-272px, so the stroke was 11% of one letter's height and 63%
of a five-letter word's. These tests pin the band that replaced it.

Run: `python3 tests/test_wordmark_type.py`
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import glyphs as G  # noqa: E402

MARKS = {**G._WM, **G._WM2, **G._WM3}


class WordmarkType(unittest.TestCase):
    def test_every_mark_is_in_the_band(self):
        bad = []
        for d, (text, cue) in MARKS.items():
            _box, cap, _w, _g = G.wm_layout(text, cue)
            if not G._WM_CAP_MIN <= cap <= G._WM_CAP_MAX:
                bad.append(f"{d} '{text}': cap {cap:.0f}")
        self.assertEqual(bad, [])

    def test_stroke_follows_the_cap(self):
        ratios = []
        for text, cue in MARKS.values():
            _box, cap, weight, _g = G.wm_layout(text, cue)
            self.assertTrue(26 <= weight <= 34, (text, weight))
            ratios.append(weight / cap)
        # Was .11-.63 at a fixed 30px stroke.
        self.assertGreaterEqual(min(ratios), .15)
        self.assertLessEqual(max(ratios), .28)

    def test_corner_cues_keep_their_box(self):
        # Plus, play and dot sit beside the text: widening into the safe area
        # would run a word into them.
        for text, cue in MARKS.values():
            if cue in ("plus", "play", "dot", "ring", "frame"):
                box, *_ = G.wm_layout(text, cue)
                self.assertEqual(box, G._WM_BOX[cue], (text, cue))

    def test_glyph_carries_its_mark(self):
        for d, (text, cue) in MARKS.items():
            self.assertEqual(G.GLYPHS[f"{d}_wm"].wm, (text, cue))


if __name__ == "__main__":
    unittest.main()
