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
5. the contact sheet actually shows every icon;
6. owner-supplied art ships as supplied: WuPlay's square and banner are a
   resample of wu-pack/source/wuplay.png, a file pinned by hash, with no
   glyph, mask, wordmark or keyline drawn over it.

Stdlib only: like tests/test_glyphs_pack.py, this must run without a
rasteriser, an Android SDK or the generator's venv. The one pixel check (6)
needs Pillow, which CI installs first; without it that check skips.

Run: python3 tests/test_wu_pack.py
"""
from __future__ import annotations

import base64
import hashlib
import json
import re
import struct
import sys
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

# Owner-supplied art: drawable -> sha256 of the file its owner supplied.
# Replacing the art means replacing the file and this hash, on purpose.
PROVIDED = {
    "wuplay": "9aa21e1d112b8b1919a46893497b70eb276ef606b4738ced1e5bda3fa9eb3d9a",
}
DATA_PNG_RE = re.compile(r'href="data:image/png;base64,([A-Za-z0-9+/=]+)"')


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def catalog_drawables() -> list[str]:
    icons = json.loads(read(ROOT / "tools" / "catalog.json"))["icons"]
    return sorted(i["drawable"] for i in icons)


def alias_map(path: Path | None = None) -> dict[str, str]:
    path = path or (WU / "values" / "aliases.xml")
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
            if path.stem in PROVIDED:
                continue    # its own art, pinned in WuProvidedArt
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
        # one grid coin per icon plus the brand mark and the size ladder;
        # owner-supplied art takes a coin's place as its own image
        coins = (len(re.findall(r'mask id="wuDisc-', sheet))
                 + len(re.findall(r'<image id="wuArt-', sheet)))
        self.assertGreaterEqual(coins, len(icons) + 5)
        self.assertIn(">wu pack<", sheet)

    def test_preview_png_exists(self) -> None:
        png = WU / "preview.png"
        self.assertTrue(png.is_file() and png.stat().st_size > 100_000)
        # the README embed — GitHub renders a 19k-pixel sheet as a sliver
        hero = WU / "preview-hero.png"
        self.assertTrue(hero.is_file() and hero.stat().st_size > 30_000)


class WuBanners(unittest.TestCase):
    """16:9 banners in the Wu lockup: coin + light wordmark + accent kicker."""

    def test_every_icon_has_a_banner_master_and_nothing_else_does(self) -> None:
        want = set(catalog_drawables())
        have = {p.stem for p in (WU / "banners").glob("*.svg")}
        self.assertEqual(want - have, set(), "icons with no banner master")
        self.assertEqual(have - want, set(), "banner masters with no icon")

    def test_banner_masters_are_the_wu_lockup_in_brand_paint(self) -> None:
        """Coin (mask knockout), Classic's ink wordmark, and at most the one
        declared accent for the kicker — the whole 'Wu style but true to
        brand' contract, per icon."""
        sys.path.insert(0, str(ROOT / "tools"))
        from icon_style import display_accent
        icons = {i["drawable"]: i
                 for i in json.loads(read(ROOT / "tools" / "catalog.json"))["icons"]}
        for path in sorted((WU / "banners").glob("*.svg")):
            if path.stem in PROVIDED:
                continue    # the owner's own lockup, pinned in WuProvidedArt
            src = read(path)
            icon = icons[path.stem]
            with self.subTest(drawable=path.stem):
                self.assertIn('width="1280" height="720"', src)
                self.assertIn('mask id="wuDisc-', src, "coin missing")
                self.assertIn("#E6EDF3", src, "wordmark must set in pack ink")
                allowed = {"#FFFFFF", "#000000", "#E6EDF3"}
                if icon.get("category"):
                    accent = display_accent(
                        icon["color"],
                        monochrome=icon.get("color_note") == "monochrome")
                    allowed.add(accent.upper())
                stray = [h for h in HEX_RE.findall(src)
                         if h.upper() not in allowed]
                self.assertEqual(
                    stray, [],
                    f"{path.stem}: paint outside white/ink/the brand accent "
                    f"({stray[:3]})")

    def test_banner_mapping_matches_the_shipped_pack(self) -> None:
        """Same components, same _banner suffix as the pack a launcher
        already applies — item for item."""
        def items(path: Path):
            return ITEM_RE.findall(read(path))
        shipped = items(ROOT / "app" / "src" / "main" / "res" / "xml" / "appfilter.xml")
        wu = items(WU / "appfilter-banners.xml")
        self.assertTrue(shipped, "shipped banner appfilter is empty")
        self.assertEqual(wu, shipped,
                         "Wu banner mapping drifted from the shipped pack")
        self.assertNotIn("<iconback", read(WU / "appfilter-banners.xml"))

    def test_banner_browser_matches_the_square_browser(self) -> None:
        squares = DRAWABLE_RE.findall(read(WU / "drawable.xml"))
        banners = DRAWABLE_RE.findall(read(WU / "drawable-banners.xml"))
        self.assertEqual(banners, [f"{s}_banner" for s in squares])
        self.assertNotIn('title="Wu · ', read(WU / "drawable-banners.xml"))
        self.assertRegex(read(WU / "drawable-banners.xml"),
                         r'title="Wu Banners · ')

    def test_every_banner_resolves_to_art(self) -> None:
        aliases = alias_map(WU / "values" / "banner_aliases.xml")
        missing = []
        for name in catalog_drawables():
            if (WEBP_DIR / f"{name}_banner.webp").is_file():
                continue
            target = aliases.get(f"{name}_banner")
            if not target or not (WEBP_DIR / f"{target}.webp").is_file():
                missing.append(name)
        self.assertEqual(missing, [],
                         "banners with neither WebP nor a resolving alias")
        for alias, canon in aliases.items():
            self.assertFalse((WEBP_DIR / f"{alias}.webp").exists(),
                             f"{alias}: aliased but the duplicate is back")

    def test_banner_preview_exists(self) -> None:
        png = WU / "preview-banners.png"
        self.assertTrue(png.is_file() and png.stat().st_size > 20_000)


def png_size(data: bytes) -> tuple[int, int]:
    """Width and height from a PNG's IHDR, stdlib only."""
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    return struct.unpack(">II", data[16:24])


class WuProvidedArt(unittest.TestCase):
    """WuPlay ships the art its owner supplied, unchanged: the square is the
    mark, the banner the whole lockup. Not a glyph, not a coin, no keyline."""

    def spec(self, drawable: str) -> dict:
        src = read(TOOL)
        m = re.search(rf'"{drawable}": \{{"source": OUT / "source" / '
                      rf'"([^"]+)",\s*"mark": \((\d+), (\d+), (\d+), (\d+)\),'
                      rf'\s*"word_x": (\d+)\}}', src)
        self.assertIsNotNone(m, f"{drawable} missing from PROVIDED")
        return {"source": WU / "source" / m.group(1),
                "mark": tuple(int(m.group(i)) for i in range(2, 6)),
                "word_x": int(m.group(6))}

    def test_tool_and_test_pin_the_same_art(self) -> None:
        src = read(TOOL)
        block = re.search(r"^PROVIDED = \{\n(.*?)^\}", src, re.M | re.S)
        self.assertIsNotNone(block, "PROVIDED missing from build_wu_pack.py")
        have = set(re.findall(r'^    "([a-z0-9_]+)": \{', block.group(1), re.M))
        self.assertEqual(have, set(PROVIDED),
                         "owner-supplied art changed without its hash here")
        self.assertTrue(set(PROVIDED) <= set(catalog_drawables()))

    def test_source_is_the_supplied_file(self) -> None:
        for drawable, digest in PROVIDED.items():
            with self.subTest(drawable=drawable):
                data = self.spec(drawable)["source"].read_bytes()
                self.assertEqual(hashlib.sha256(data).hexdigest(), digest,
                                 "the supplied art was edited or replaced")
                self.assertEqual(png_size(data), (5120, 2880),
                                 "the supplied lockup is 16:9 at 4x the master")

    def test_masters_embed_the_art_and_draw_nothing_over_it(self) -> None:
        for drawable in PROVIDED:
            for path, size in ((SVG_DIR / f"{drawable}.svg", (512, 512)),
                               (WU / "banners" / f"{drawable}.svg", (1280, 720))):
                with self.subTest(master=str(path.relative_to(ROOT))):
                    src = read(path)
                    self.assertEqual(src.count(f'<image id="wuArt-{drawable}"'), 1)
                    pngs = DATA_PNG_RE.findall(src)
                    self.assertEqual(len(pngs), 1)
                    self.assertEqual(png_size(base64.b64decode(pngs[0])), size)
                    for drawn in ("<mask", "<path", "<circle", "<rect",
                                  "<text", "stroke="):
                        self.assertNotIn(drawn, src,
                                         f"something is drawn over the art ({drawn})")

    def test_shipped_tiles_are_a_resample_of_the_source(self) -> None:
        """Pixels, not just files: the square is the mark's circle scaled
        onto the r=211 disc (the wordmark's W cleared first), the banner the
        whole lockup at 320x180, and nothing else — no keyline either."""
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

        for drawable in PROVIDED:
            spec = self.spec(drawable)
            with Image.open(spec["source"]) as src:
                src = src.convert("RGBA")
            banner = src.resize((320, 180), Image.LANCZOS)
            clear = src.copy()
            clear.paste((0, 0, 0, 0), (spec["word_x"], 0, clear.width, clear.height))
            square = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
            square.paste(clear.crop(spec["mark"]).resize((422, 422), Image.LANCZOS),
                         (45, 45))
            with self.subTest(drawable=drawable, art="square"):
                with Image.open(WEBP_DIR / f"{drawable}.webp") as tile:
                    self.assertTrue(same(tile, square), "square is not the mark as supplied")
            with self.subTest(drawable=drawable, art="banner"):
                with Image.open(WEBP_DIR / f"{drawable}_banner.webp") as tile:
                    self.assertTrue(same(tile, banner), "banner is not the lockup as supplied")


if __name__ == "__main__":
    unittest.main(verbosity=2)
