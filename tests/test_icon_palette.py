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


    def test_sourcing_one_icon_does_not_reroll_the_rest(self):
        """A real brand colour for one app must leave every other app alone.

        Assignment used to walk the pack in name order from scratch, so
        sourcing 239 colours recoloured 424 unrelated palette icons. It is
        sticky now: an icon keeps its palette colour while that colour still
        clears the neighbour and same-picture rules.
        """
        import copy
        from icon_palette import assign
        icons = copy.deepcopy(ICONS)
        palette = [i for i in icons if i.get("color_source") == PACK_PALETTE
                   and not i.get("brand")]
        target = palette[len(palette) // 2]
        target["color"] = "#123456"
        target["color_source"] = "sampled for the test"
        after = assign(icons)
        moved = [i["name"] for i in icons if id(i) in after
                 and after[id(i)].upper() != i["color"].upper()]
        # the two grid neighbours may need to step aside; nobody else moves
        self.assertLessEqual(len(moved), 2, moved)

if __name__ == "__main__":
    unittest.main()
