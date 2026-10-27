#!/usr/bin/env python3
"""Core Builds Wu Pack: the monochrome disc variant stays true to the style.

The Wu pack is generated from the same single source as the shipped Classic
pack — tools/catalog.json — and takes its component mappings from the glyph
companion's generated appfilter, so the two packs can never disagree about
which component belongs to which app. It is deliberately a sibling, not a
suite product: it ships art and mappings, not an APK.

What this pins, and why:

1. the style constants — a disc is not a disc if someone nudges the radius,
   the rim gap or the primary weight and nobody re-renders the pack;
2. every master is really Wu style: white cut/keep colours only, a luminance
   mask over the r=211 disc, no accent hue left anywhere (hue is the first
   thing a "quick variant" would keep, and it would break both the style and
   the mask — mask luminance would only half-open dark-blue holes);
3. the mapping is a copy of the shipped companion's, item for item, minus
   the fallback furniture whose drawables live in the Classic app;
4. every drawable the XML names resolves — to a WebP on disk or through
   values/aliases.xml, exactly like Classic's duplicate renders;
5. the contact sheet actually shows every icon.

Stdlib only: like tests/test_glyphs_pack.py, this must run without a
rasteriser, an Android SDK or the generator's venv.

Run: python3 tests/test_wu_pack.py
"""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WU = ROOT / "wu-pack"
SVG_DIR = WU / "svg"
WEBP_DIR = WU / "webp"
TOOL = ROOT / "tools" / "build_wu_pack.py"
GLYPH_APPFILTER = ROOT / "glyphs" / "src" / "main" / "res" / "xml" / "appfilter.xml"

HEX_RE = re.compile(r"#[0-9A-Fa-f]{6}")
ITEM_RE = re.compile(r'<item component="([^"]+)" drawable="([^"]+)"')
DRAWABLE_RE = re.compile(r'drawable="([^"]+)"')


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def catalog_drawables() -> list[str]:
    icons = json.loads(read(ROOT / "tools" / "catalog.json"))["icons"]
    return sorted(i["drawable"] for i in icons)


def alias_map() -> dict[str, str]:
    path = WU / "values" / "aliases.xml"
    if not path.is_file():
        return {}
    return dict(re.findall(r'<drawable name="([^"]+)">@drawable/([^<]+)</drawable>',
                           read(path)))


class WuStyleConstants(unittest.TestCase):
    def test_style_constants_are_pinned(self) -> None:
        """The numbers the README and the receipt both promise."""
        src = read(TOOL)
        def const(name: str) -> str:
            m = re.search(rf"^{name}\s*=\s*(.+?)\s*(?:#.*)?$", src, re.M)
            self.assertIsNotNone(m, f"{name} missing from build_wu_pack.py")
            return m.group(1)
        self.assertEqual(const("DISC_R"), "211")
        self.assertEqual(const("INSET"), "20")
        self.assertEqual(const("PRIMARY"), "40")
        self.assertIn('"#FFFFFF"', src)   # the one ink
        self.assertIn('"#000000"', src)   # the one mask cut
        self.assertIn("TARGET_R = DISC_R - INSET", src)


class WuMasters(unittest.TestCase):
    def test_every_icon_has_a_master_and_nothing_else_does(self) -> None:
        want = set(catalog_drawables())
        have = {p.stem for p in SVG_DIR.glob("*.svg")}
        self.assertEqual(want - have, set(), "icons with no Wu master")
        self.assertEqual(have - want, set(), "Wu masters with no catalog icon")

    def test_masters_are_monochrome_masked_discs(self) -> None:
        for path in sorted(SVG_DIR.glob("*.svg")):
            src = read(path)
            with self.subTest(drawable=path.stem):
                self.assertIn('viewBox="0 0 512 512"', src)
                # the whole style: keep the disc, cut the glyph
                self.assertIn('mask id="wuDisc-', src)
                self.assertIn('r="211"', src)
                self.assertIn('mask="url(#wuDisc-', src)
                stray = [h for h in HEX_RE.findall(src)
                         if h.upper() not in ("#FFFFFF", "#000000")]
                self.assertEqual(
                    stray, [],
                    f"{path.stem}: accent hue leaked into the Wu master "
                    f"({stray[:3]}) — the style is white and cut, and a "
                    f"coloured stroke would only half-open the mask")


class WuMappings(unittest.TestCase):
    def test_mapping_is_the_shipped_companion_copy(self) -> None:
        """Same components, same drawables, item for item — the Wu pack
        changes the art, never the assignment."""
        shipped = ITEM_RE.findall(read(GLYPH_APPFILTER))
        wu = ITEM_RE.findall(read(WU / "appfilter.xml"))
        self.assertTrue(shipped, "glyph companion appfilter is empty")
        self.assertEqual(wu, shipped,
                         "Wu appfilter drifted from the shipped companion")
        src = read(WU / "appfilter.xml")
        for furniture in ("<iconback", "<iconmask", "<iconupon"):
            self.assertNotIn(furniture, src,
                             "fallback furniture references Classic's "
                             "cb_back_* drawables, which this folder does not carry")
        self.assertIn('<scale factor="0.70"/>', src,
                      "the companion's scale keeps the discs at the size a "
                      "launcher already expects from this pack")

    def test_browser_grid_lists_every_icon_under_wu_titles(self) -> None:
        src = read(WU / "drawable.xml")
        names = DRAWABLE_RE.findall(src)
        self.assertEqual(len(names), len(set(names)), "duplicate grid entries")
        self.assertEqual(sorted(names), catalog_drawables())
        self.assertNotIn("Square · ", src)
        self.assertRegex(src, r'title="Wu · ')


class WuArt(unittest.TestCase):
    def test_every_drawable_resolves_to_art(self) -> None:
        aliases = alias_map()
        missing = []
        for name in catalog_drawables():
            if (WEBP_DIR / f"{name}.webp").is_file():
                continue
            target = aliases.get(name)
            if not target or not (WEBP_DIR / f"{target}.webp").is_file():
                missing.append(name)
        self.assertEqual(missing, [],
                         "drawables with neither WebP nor a resolving alias")

    def test_aliases_point_at_real_canonical_art(self) -> None:
        for alias, canon in alias_map().items():
            with self.subTest(alias=alias):
                self.assertIn(alias, catalog_drawables())
                self.assertIn(canon, catalog_drawables())
                self.assertTrue((WEBP_DIR / f"{canon}.webp").is_file())

    def test_alias_names_never_carry_their_own_file(self) -> None:
        """alias_identical() deletes the duplicate; a resurrected copy would
        ship two answers for one name."""
        for alias in alias_map():
            self.assertFalse((WEBP_DIR / f"{alias}.webp").exists(),
                             f"{alias}: aliased but the duplicate file is back")


class WuPreview(unittest.TestCase):
    def test_contact_sheet_shows_every_icon(self) -> None:
        sheet = read(WU / "preview.svg")
        icons = catalog_drawables()
        # one grid coin per icon plus the brand mark and the size ladder
        coins = len(re.findall(r'mask id="wuDisc-', sheet))
        self.assertGreaterEqual(coins, len(icons) + 5)
        self.assertIn(">wu pack<", sheet)

    def test_preview_png_exists(self) -> None:
        png = WU / "preview.png"
        self.assertTrue(png.is_file() and png.stat().st_size > 100_000)
        # the README embed — GitHub renders a 19k-pixel sheet as a sliver
        hero = WU / "preview-hero.png"
        self.assertTrue(hero.is_file() and hero.stat().st_size > 30_000)


if __name__ == "__main__":
    unittest.main(verbosity=2)
