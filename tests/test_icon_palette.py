#!/usr/bin/env python3
"""The Classic fallback palette: wide, even, legible and neighbour-distinct.

Icons with no published brand colour take a palette accent. Before 1.9.4 five
colours carried 456 of 689 such icons (one blue, 229), which read as a blue
smear on the grid. tools/icon_palette.py owns the palette and the assignment;
this holds both to the promise.

Run: python tests/test_icon_palette.py
"""
from __future__ import annotations

import json
import subprocess
import sys
import unittest
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

from icon_palette import HEXES, MIN_READABLE, PACK_PALETTE, PALETTE  # noqa: E402
from icon_style import contrast, display_accent  # noqa: E402

ICONS = json.loads((ROOT / "tools/catalog.json").read_text(encoding="utf-8"))["icons"]
ORDER = sorted(ICONS, key=lambda i: i["name"].lower())


def palette_icons():
    sourced_brands = {i["brand"] for i in ICONS
                      if i.get("brand") and i.get("color_source") != PACK_PALETTE}
    return [i for i in ICONS if i.get("color_source") == PACK_PALETTE
            and i.get("brand") not in sourced_brands]


class Palette(unittest.TestCase):
    def test_wide_and_legible(self):
        self.assertGreaterEqual(len(PALETTE), 18)
        self.assertEqual(len(set(HEXES)), len(HEXES))
        for name, hexv in PALETTE:
            with self.subTest(colour=name):
                self.assertGreaterEqual(contrast(hexv), MIN_READABLE)
                # Legible as drawn: display_accent never has to lift it.
                self.assertEqual(display_accent(hexv), hexv.upper())

    def test_catalog_matches_the_assignment(self):
        result = subprocess.run([sys.executable, str(ROOT / "tools/icon_palette.py"), "--check"],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_every_palette_icon_uses_the_palette(self):
        stray = [f"{i['name']} {i['color']}" for i in palette_icons()
                 if i["color"].upper() not in HEXES]
        self.assertEqual(stray, [])

    def test_no_colour_dominates(self):
        counts = Counter(i["color"].upper() for i in palette_icons())
        share = max(counts.values()) / sum(counts.values())
        # Even would be 1/18 = 5.6%; the old palette's blue was 33%.
        self.assertLessEqual(share, 0.08, counts.most_common(3))
        self.assertEqual(len(counts), len(PALETTE))

    def test_grid_neighbours_differ(self):
        # Neighbours in the catalogue grid never share a palette colour unless
        # they are one brand; sourced brand colours may legitimately match.
        clashes = []
        for a, b in zip(ORDER, ORDER[1:]):
            if a["color"].upper() != b["color"].upper():
                continue
            if (a.get("brand") or a["name"]) == (b.get("brand") or b["name"]):
                continue
            if PACK_PALETTE in (a.get("color_source"), b.get("color_source")):
                clashes.append((a["name"], b["name"], a["color"]))
        self.assertEqual(clashes, [])


if __name__ == "__main__":
    unittest.main()
