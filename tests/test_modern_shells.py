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
    ACCENT = "#4CC9F0"

    def test_modern_family_cues_are_present_in_the_adaptive_shells(self):
        tool = glyphs.GLYPHS["tool_A"](self.ACCENT)
        sport = glyphs.GLYPHS["sport_A"](self.ACCENT)
        browser = glyphs.GLYPHS["browser_A"](self.ACCENT)

        # A gear has a broad tooth/cut rhythm rather than the former shield-
        # shaped six-sided host. Keep this count explicit so a future edit
        # cannot silently return to a generic hexagon.
        polygon = re.search(r'<polygon points="([^"]+)"', tool)
        self.assertIsNotNone(polygon)
        self.assertEqual(len(polygon.group(1).split()), 16)

        # The sport shell has two halves of one open seam, kept beside the
        # adaptive mark so the cue does not cut through the monogram.
        self.assertIn('M 112 174 C 128 208 138 232 146 254', sport)
        self.assertIn('M 400 174 C 384 208 374 232 366 254', sport)

        # The browser shell retains the title bar and three low-detail window
        # controls; these survive the TV review size without a wordmark.
        self.assertIn('M 64 180 L 448 180', browser)
        self.assertEqual(browser.count('cy="142"'), 3)

    def test_modern_shells_pass_small_size_and_safe_area_review(self):
        for name in ("tool_A", "sport_A", "browser_A"):
            with self.subTest(name=name):
                # Family shells retain the established adaptive monogram
                # renderer; the strict Classic contract is exercised against
                # shipped catalogue constructions by the existing identity
                # tests. This check owns the shell's fit/counter behaviour.
                result = check(name, self.ACCENT, strict=False)
                self.assertEqual(result["problems"], [])
                self.assertGreaterEqual(result["holes"][2], 1)

    def test_modern_shells_remain_transparent_single_accent_geometry(self):
        for name in ("tool_A", "sport_A", "browser_A"):
            with self.subTest(name=name):
                body = glyphs.monoline(glyphs.GLYPHS[name](self.ACCENT))
                self.assertNotIn("fill=\"white\"", body.lower())
                self.assertEqual(set(re.findall(r'stroke="([#A-Fa-f0-9]+)"', body)),
                                 {self.ACCENT})
                self.assertNotIn("<image", body)


if __name__ == "__main__":
    unittest.main()
