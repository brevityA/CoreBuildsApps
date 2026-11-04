#!/usr/bin/env python3
"""Alternates: owner-supplied art a user sets by hand, never auto-applied.

tools/alternates.py defines the contract; this pins what ships:

1. the catalog block is sound — the supplied file matches its sha256, the
   name is `<icon>_alt`, the usage is manual-only;
2. every alternate is listed straight after its icon in both icon browsers
   (banners in the icon pack, squares in Core Builds Glyphs), in the legacy
   iconpack.xml and in the in-app grid the launcher's per-app picker opens,
   and is pinned against release resource shrinking;
3. no appfilter anywhere names one, so a launcher never applies it on its own
   and the catalog icon stays the default;
4. the art is the supplied file resampled and nothing else (Pillow; CI
   installs it before this runs, without it that check skips);
5. counts stay counts of apps: every place that counts browser rows or grid
   tiles leaves alternates out, with one pattern for "is an alternate".

Run: python3 tests/test_alternates.py
"""
from __future__ import annotations

import copy
import json
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import alternates  # noqa: E402

CATALOG = json.loads((ROOT / "tools" / "catalog.json").read_text(encoding="utf-8"))
ALTS = alternates.entries(CATALOG)
APP = ROOT / "app" / "src" / "main"
GLYPHS = ROOT / "glyphs" / "src" / "main"
GLYPH_FLAVOR = ROOT / "app" / "src" / "glyphs"
NODPI = APP / "res" / "drawable-nodpi"
ITEM = re.compile(r'<item drawable="([^"]+)" />')


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def array(name: str) -> list[str]:
    src = read(APP / "res" / "values" / "icon_pack.xml")
    body = re.search(rf'<(string|integer)-array name="{name}">(.*?)</\1-array>', src, re.S)
    return re.findall(r"<item>([^<]*)</item>", body.group(2))


class CatalogBlock(unittest.TestCase):
    def test_there_is_the_wuplay_alternate(self):
        self.assertIn("wuplay_alt", dict(ALTS))
        self.assertEqual(dict(ALTS)["wuplay_alt"]["of"], "wuplay")

    def test_block_is_sound(self):
        self.assertEqual(alternates.errors(CATALOG), [])

    def test_errors_name_each_broken_rule(self):
        def broken(**change):
            data = copy.deepcopy(CATALOG)
            data["alternates"]["wuplay_alt"].update(change)
            return " | ".join(alternates.errors(data))
        self.assertIn("sha256", broken(sha256="0" * 64))
        self.assertIn("manual-only", broken(usage="auto"))
        self.assertIn("unknown icon", broken(of="no_such_icon"))
        self.assertIn("square must be", broken(square=[0, 0, 10, 20]))
        # A malformed box is reported by name, never compared against the
        # source's size (None < int would raise instead).
        self.assertIn("square must be", broken(square=[None, 0, 10, 10]))
        self.assertIn("square must be", broken(square="459,752"))
        renamed = copy.deepcopy(CATALOG)
        renamed["alternates"]["wuplay_logo"] = renamed["alternates"].pop("wuplay_alt")
        self.assertIn("is named wuplay_alt", " | ".join(alternates.errors(renamed)))
        clash = copy.deepcopy(CATALOG)
        clash["icons"][0] = dict(clash["icons"][0], drawable="tenplay_alt")
        self.assertIn("may not end in _alt", " | ".join(alternates.errors(clash)))


class Listed(unittest.TestCase):
    """Beside its icon wherever a user picks by hand."""

    def assert_after(self, names: list[str], parent: str, alt: str, where: str):
        self.assertIn(alt, names, f"{alt} missing from {where}")
        self.assertEqual(names.count(alt), 1, f"{alt} listed twice in {where}")
        self.assertEqual(names[names.index(alt) - 1], parent,
                         f"{alt} is not straight after {parent} in {where}")

    def test_browsers(self):
        for d, spec in ALTS:
            of = spec["of"]
            for tree in (GLYPHS, GLYPH_FLAVOR):
                for rel in ("res/xml/drawable.xml", "assets/drawable.xml"):
                    path = tree / rel
                    self.assert_after(ITEM.findall(read(path)), of, d,
                                      str(path.relative_to(ROOT)))
            for rel in ("res/xml/drawable.xml", "assets/drawable.xml"):
                path = APP / rel
                self.assert_after(ITEM.findall(read(path)), f"{of}_banner",
                                  f"{d}_banner", str(path.relative_to(ROOT)))
            self.assert_after(ITEM.findall(read(APP / "res/xml/iconpack.xml")),
                              of, d, "iconpack.xml")

    def test_in_app_grid_row(self):
        pack, names = array("icon_pack"), array("icon_names")
        cats, bespoke = array("icon_categories"), array("icon_bespoke")
        self.assertEqual(len({len(pack), len(names), len(cats), len(bespoke)}), 1,
                         "the four grid arrays must stay the same length")
        icons = {i["drawable"]: i for i in CATALOG["icons"]}
        for d, spec in ALTS:
            self.assert_after(pack, spec["of"], d, "icon_pack array")
            row = pack.index(d)
            self.assertEqual(names[row], spec["name"])
            self.assertEqual(cats[row], icons[spec["of"]].get("category") or "APP")
            self.assertEqual(bespoke[row], "1")

    def test_pinned_against_release_shrinking(self):
        keep = read(APP / "res/raw/keep.xml")
        for d, _ in ALTS:
            for name in (d, f"{d}_banner"):
                self.assertIn(f"@drawable/{name},", keep + ",")


class NeverAutoApplied(unittest.TestCase):
    def test_no_appfilter_names_an_alternate(self):
        paths = [tree / rel for tree in (APP, GLYPHS, GLYPH_FLAVOR)
                 for rel in ("res/xml/appfilter.xml", "assets/appfilter.xml")]
        checked = 0
        for path in paths:
            if not path.is_file():
                continue
            checked += 1
            drawables = re.findall(r'drawable="([^"]+)"', read(path))
            self.assertTrue(drawables, f"{path} parsed no items")
            alts = [x for x in drawables if alternates.is_alternate(x)]
            self.assertEqual(alts, [], f"{path.relative_to(ROOT)} auto-applies {alts}")
        self.assertGreaterEqual(checked, 4)


class Art(unittest.TestCase):
    def test_files_exist_at_pack_sizes(self):
        for d, _ in ALTS:
            for name, size in ((d, (512, 512)), (f"{d}_banner", (320, 180))):
                path = NODPI / f"{name}.webp"
                with self.subTest(art=name):
                    self.assertTrue(path.is_file(), f"{path.name} missing")
                    data = path.read_bytes()
                    self.assertEqual(data[:4], b"RIFF")
                    self.assertEqual(data[8:12], b"WEBP")
                    self.assertGreater(len(data), 400)

    def test_art_is_the_supplied_file_resampled(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow not installed (CI installs it)")

        def same(a, b) -> bool:
            # Over two grounds, so alpha and every visible colour must match;
            # lossless WebP may rewrite the colour of fully clear pixels.
            for ground in ((0, 0, 0, 255), (255, 255, 255, 255)):
                pa, pb = (Image.alpha_composite(Image.new("RGBA", x.size, ground),
                                                x.convert("RGBA")) for x in (a, b))
                if pa.tobytes() != pb.tobytes():
                    return False
            return True

        for d, spec in ALTS:
            with self.subTest(art=d):
                with Image.open(NODPI / f"{d}.webp") as tile:
                    self.assertEqual((tile.size, tile.mode), ((512, 512), "RGBA"))
                    self.assertTrue(same(tile, alternates.square(spec)))
            with self.subTest(art=f"{d}_banner"):
                with Image.open(NODPI / f"{d}_banner.webp") as tile:
                    self.assertEqual((tile.size, tile.mode), ((320, 180), "RGBA"))
                    self.assertTrue(same(tile, alternates.banner(spec)))

    def test_square_stays_in_the_safe_area(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow not installed (CI installs it)")
        for d, _ in ALTS:
            with Image.open(NODPI / f"{d}.webp") as tile:
                box = tile.convert("RGBA").getchannel("A").getbbox()
            pad = (alternates.GRID - alternates.SAFE) // 2
            self.assertGreaterEqual(min(box[0], box[1], 512 - box[2], 512 - box[3]), pad)


class CountsAreApps(unittest.TestCase):
    """"1022 icons" means apps. An alternate is a second tile for one."""

    def test_generated_count_is_the_catalog(self):
        src = read(APP / "res/values/icon_pack.xml")
        count = int(re.search(r'<integer name="icon_count">(\d+)<', src).group(1))
        self.assertEqual(count, len(CATALOG["icons"]))
        self.assertEqual(len(array("icon_pack")), len(CATALOG["icons"]) + len(ALTS))

    def test_every_counter_uses_the_one_pattern(self):
        pattern = alternates.ALT_RE.pattern
        self.assertEqual(pattern, r"_alt\d*$")
        kotlin = read(APP / "java/tv/corebuilds/iconpack/IconAdapter.kt")
        self.assertIn('Regex("_alt\\\\d*$")', kotlin)
        java = read(GLYPHS / "java/tv/corebuilds/iconpack/glyphs/GlyphsHomeActivity.java")
        self.assertIn('Pattern.compile("_alt\\\\d*$")', java)
        self.assertIn("!ALTERNATE.matcher(value).find()", java)
        for tool in ("build_play_listing.py", "build_app_ui_mockups.py"):
            self.assertIn("from alternates import is_alternate",
                          read(ROOT / "tools" / tool), tool)

    def test_main_activity_shows_app_counts(self):
        src = read(APP / "java/tv/corebuilds/iconpack/MainActivity.kt")
        self.assertIn("iconCount = all.count { !it.alternate }", src)
        # A search hitting only "WuPlay (alternative)" still matched one app.
        self.assertIn("filtered.mapTo(HashSet()) { it.app }.size", src)
        # The empty state counts apps in the category the same way.
        self.assertIn("all.filter(::inCategory).mapTo(HashSet()) { it.app }.size", src)
        self.assertNotIn("all.count(::inCategory)", src)
        adapter = read(APP / "java/tv/corebuilds/iconpack/IconAdapter.kt")
        self.assertIn('val app: String get() = drawable.replace(ALTERNATE, "")', adapter)
        for fmt in ("R.string.pack_stats_fmt, all.size",
                    "R.string.search_hint_fmt, all.size",
                    "getString(R.string.chip_all), all.size",
                    "R.string.icon_filter_fmt, filtered.size, all.size"):
            self.assertNotIn(fmt, src, f"a count still uses grid tiles: {fmt}")

    def test_listing_counts_leave_alternates_out(self):
        import build_play_listing as listing
        glyph_rows = ITEM.findall(read(GLYPHS / "res/xml/drawable.xml"))
        self.assertEqual(listing.glyph_counts()["icons"],
                         len(glyph_rows) - len(ALTS))
        self.assertEqual(listing.glyph_counts()["icons"], len(CATALOG["icons"]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
