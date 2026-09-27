"""Regression checks for the evidence-backed generic Glyph pass."""
from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

from classify_families import classify  # noqa: E402


class GenericGlyphTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.icons = json.loads((ROOT / "tools/catalog.json").read_text())["icons"]
        cls.by_name = {icon["name"]: icon for icon in cls.icons}

    def test_no_generic_app_letter_rows_remain(self):
        leftovers = [icon["name"] for icon in self.icons
                     if icon.get("glyph", "").startswith("app_")]
        self.assertEqual(leftovers, [])

    def test_researched_function_examples(self):
        expected = {
            "Ace Stream": "broadcast",
            "AllSaves Social": "tool",
            "Acontra Plus": "broadcast",
            "F Droid": "store",
            "Fluffy": "files",
            "Gain": "film",
            "GenPlay": "gaming",
            "Oilers+": "sport",
        }
        for name, family in expected.items():
            with self.subTest(name=name):
                self.assertEqual(classify(self.by_name[name]), family)
                self.assertTrue(self.by_name[name]["glyph"].startswith(family + "_"))


if __name__ == "__main__":
    unittest.main()
