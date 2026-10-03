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
            cap = G.letter_size(text, cap)
            if not G._WM_CAP_MIN <= cap <= G._WM_CAP_MAX:
                bad.append(f"{d} '{text}': cap {cap:.0f}")
        self.assertEqual(bad, [])

    def test_stroke_follows_the_cap(self):
        ratios = []
        for text, cue in MARKS.values():
            _box, cap, weight, _g = G.wm_layout(text, cue)
            cap = G.letter_size(text, cap)
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

    def test_icon_letters_are_one_face(self):
        # 2.0.0: tiles, category shells, adaptive marks and bespoke glyphs
        # draw letters with the stroke alphabet. Outfit is the banner label's
        # face only; an icon that outlined it again would bring back a filled
        # second alphabet beside the monoline one.
        for name in ("monogram_body", "monogram_scaled", "monogram_text", "adaptive_lockup"):
            self.assertEqual(getattr(G, name).__module__, "glyphs", name)
        src = (Path(G.__file__)).read_text(encoding="utf-8")
        for outfit in ("monogram_outline", "wordmark_spans", "from typeface import monogram",
                       "from typeface import adaptive"):
            self.assertNotIn(outfit, src)
        body = G.GLYPHS["broadcast_K"]("#FF0000")
        self.assertNotIn('fill="#FF0000" stroke="none"/><path', body.split("<path")[-1])

    def test_x_height_marks_stand_as_tall_as_capitals(self):
        self.assertEqual(G.letter_body("no"), .7)
        self.assertEqual(G.letter_body("No"), 1.0)
        self.assertAlmostEqual(G.stroke_lockup_cap("c", 400, 400),
                               G.stroke_lockup_cap("C", 400, 400), delta=1)


if __name__ == "__main__":
    unittest.main()
