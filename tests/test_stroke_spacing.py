#!/usr/bin/env python3
"""Stroke-letter spacing and letter identity (2.1.2).

Until 2.1.2 every letter gap ran box edge to box edge, so open shapes set
loose ("To", "LY", "r.") beside closed ones, and l was the same bare stem
as I. These tests pin the optical spacing that replaced the box gap and the
foot that tells l from I.

Run: `python3 tests/test_stroke_spacing.py`
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import glyphs as G  # noqa: E402

LETTERS = [ch for ch in G._STROKE_LETTERS if G._STROKE_LETTERS[ch][1]]


class StrokeSpacing(unittest.TestCase):
    def test_l_is_not_I(self):
        self.assertNotEqual(G._STROKE_LETTERS["l"], G._STROKE_LETTERS["I"])
        # the foot turns right, so l's ink reaches past I's single stem
        self.assertGreater(G._STROKE_LETTERS["l"][0], G._STROKE_LETTERS["I"][0])

    def test_open_pairs_close_up_and_flat_pairs_do_not(self):
        for pair in ("To", "LY", "r.", "Pa", "Ve"):
            self.assertGreater(G._pair_kern(*pair), 0, pair)
        for pair in ("HH", "II", "Il", "nn"):
            self.assertEqual(G._pair_kern(*pair), 0, pair)

    def test_kern_never_brings_strokes_inside_the_gap(self):
        # In every band both letters ink, the white the kern gives back is
        # never more than the white the two shapes leave there.
        bad = []
        for a in LETTERS:
            ra = G._side_white(a)[1]
            for b in LETTERS:
                lb = G._side_white(b)[0]
                shared = [ra[y] + lb[y] for y in ra if y in lb]
                k = G._pair_kern(a, b)
                if k > 0 and shared and k > min(shared) + 1e-9:
                    bad.append(f"{a}{b}: kern {k} > white {min(shared):.3f}")
        self.assertEqual(bad, [])

    def test_figures_stay_tabular(self):
        for a in "0123456789":
            for b in "0123456789":
                self.assertEqual(G._pair_kern(a, b), 0, a + b)

    def test_measured_width_matches_the_drawn_advance(self):
        # _stroke_px and _stroke_line must agree, or fitted marks drift.
        for text in ("HBO Max", "TiviMate", "Pocket Casts", "Tubi", "LYNX"):
            s, gap = 100, 30
            d, _dots = G._stroke_line(text, 0, 0, s, gap)
            last = text[-1]
            start = G._stroke_px(text, s, gap) - G._STROKE_LETTERS[last][0] * s
            x, _ = G._stroke_line(text[:-1], 0, 0, s, gap)
            advance = G._stroke_px(text[:-1], s, gap) + gap - G._pair_kern(text[-2], last) * s
            self.assertAlmostEqual(start, advance, places=6, msg=text)

    def test_round_letters_overshoot_both_lines_and_flats_do_not(self):
        import re
        def ys(ch, s=100):
            d, _ = G._stroke_line(ch, 0, 0, s, 30)
            nums = [float(v) for v in re.findall(r"-?\d+\.?\d*", d)]
            return min(nums[1::2]), max(nums[1::2])
        top, foot = ys("O")
        self.assertLess(top, 0)          # above the cap line
        self.assertGreater(foot, 100)    # below the baseline
        self.assertAlmostEqual(-top, foot - 100, places=1)
        self.assertEqual(ys("H"), (0.0, 100.0))
        otop, ofoot = ys("o")
        self.assertLess(otop, 30)        # above the x-height
        self.assertGreater(ofoot, 100)

    def test_banner_names_track_like_the_marks(self):
        cap, _w, gap, _sp = G.stroke_label_metrics(100)
        self.assertAlmostEqual(gap / cap, G._WM_SPACING[0])


if __name__ == "__main__":
    unittest.main()
