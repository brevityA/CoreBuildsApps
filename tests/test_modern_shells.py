"""Regression checks for the symbol-first family-shell geometry.

These are deliberately small, source-level checks: the full generated gates
still own catalogue parity, SVG validity, raster fit, and release resources.
"""
from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import glyphs
from check_glyph import check


class ModernFamilyShellTests(unittest.TestCase):
    """One open-corner tile per family; the function lives in a corner badge.

    The previous containers let seams, grips and aerials cross the monogram
    (and the sport seam read as a prohibition sign). These checks hold the
    replacement to its promises: the letters never share space with the
    badge, every family still fits and keeps its counters at TV sizes, and
    the art stays single-accent, transparent line work.
    """

    ACCENT = "#4CC9F0"
    BADGED = sorted(set(glyphs.FAMILY_SHELLS) - {"app"})

    def test_every_family_but_app_is_the_open_tile_plus_its_own_badge(self):
        tile = glyphs.open_tile(self.ACCENT)
        badges = set()
        for fam in self.BADGED:
            with self.subTest(family=fam):
                body = glyphs.FAMILY_SHELLS[fam][0](self.ACCENT)
                self.assertTrue(body.startswith(tile), fam)
                badges.add(body[len(tile):])
        # fourteen families, fourteen different badges
        self.assertEqual(len(badges), len(self.BADGED))
        self.assertEqual(glyphs.FAMILY_SHELLS["app"][0](self.ACCENT),
                         glyphs.closed_tile(self.ACCENT))

    def test_the_badge_stays_out_of_the_letter_box(self):
        # Letters are set at cy=300 with a 190px cap, so their tops sit at
        # y=205; the badge (centre BY, radius R, plus half its stroke) must
        # finish above that, or the corner cue is back on top of the mark.
        for fam in glyphs.FAMILY_SHELLS:
            with self.subTest(family=fam):
                _, cap_h, cy, _ = glyphs.FAMILY_SHELLS[fam]
                self.assertLess(glyphs.BY + glyphs.R + glyphs.BW / 2,
                                cy - cap_h / 2)

    def test_every_family_passes_small_size_and_safe_area_review(self):
        for fam in glyphs.FAMILY_SHELLS:
            for letter in "AMW8":
                with self.subTest(glyph=f"{fam}_{letter}"):
                    result = check(f"{fam}_{letter}", self.ACCENT, strict=False)
                    self.assertEqual(result["problems"], [])

    def test_modern_shells_remain_transparent_single_accent_geometry(self):
        for fam in glyphs.FAMILY_SHELLS:
            with self.subTest(family=fam):
                body = glyphs.monoline(glyphs.GLYPHS[f"{fam}_A"](self.ACCENT))
                self.assertNotIn('fill="white"', body.lower())
                self.assertEqual(set(re.findall(r'stroke="([#A-Fa-f0-9]+)"', body)),
                                 {self.ACCENT})
                self.assertNotIn("<image", body)


if __name__ == "__main__":
    unittest.main()
