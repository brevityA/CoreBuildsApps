#!/usr/bin/env python3
"""Presence pass: raster-only, interiors stay open, vectors stay monoline."""
from __future__ import annotations

import io
import sys
import unittest
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

from glyphs import render_svg
from presence import apply_presence
from svg_renderer import svg2png


def raster(glyph: str, colour: str) -> Image.Image:
    png = svg2png(bytestring=render_svg(glyph, colour).encode(),
                  output_width=512, output_height=512)
    return Image.open(io.BytesIO(png)).convert("RGBA")


class PresenceTests(unittest.TestCase):
    def test_vectors_stay_glowless(self):
        svg = render_svg("yt_play", "#FF0000")
        self.assertNotIn("feGaussianBlur", svg)
        self.assertNotIn("opacity=", svg)

    def test_open_interiors_stay_open(self):
        src = raster("yt_play", "#FF0000")
        lit = apply_presence(src)
        self.assertEqual(src.getpixel((256, 256))[3], 0)
        self.assertEqual(lit.getpixel((256, 256))[3], 0)
        self.assertEqual(lit.getpixel((100, 256))[3], 0)

    def test_stroke_pixels_keep_the_accent(self):
        lit = apply_presence(raster("yt_play", "#FF0000"))
        pixel = lit.getpixel((64, 256))
        self.assertGreater(pixel[0], 200)
        self.assertLess(pixel[1], 40)
        self.assertLess(pixel[2], 40)
        self.assertGreater(pixel[3], 200)

    def test_presence_adds_ink_around_the_stroke(self):
        src = raster("play_round", "#FFC107")
        lit = apply_presence(src)
        src_cov = sum(1 for p in src.getchannel("A").getdata() if p >= 16)
        lit_cov = sum(1 for p in lit.getchannel("A").getdata() if p >= 16)
        self.assertGreater(lit_cov, src_cov)
        self.assertLess(lit_cov / (512 * 512), 0.40)

    def test_mubi_dots_stay_seven_islands(self):
        lit = apply_presence(raster("mubi_mark", "#E6EDF3"))
        ink = {(x, y) for y in range(lit.height) for x in range(lit.width)
               if lit.getpixel((x, y))[3] > 128}
        components = 0
        while ink:
            start = ink.pop()
            todo = [start]
            components += 1
            while todo:
                x, y = todo.pop()
                for q in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                    if q in ink:
                        ink.remove(q)
                        todo.append(q)
        self.assertEqual(components, 7)

    def test_presence_never_pushes_ink_outside_the_safe_area(self):
        """The vector gate is not enough once a raster pass widens the mark.

        `test_no_glyph_puts_ink_outside_the_safe_area` measures `render_svg`,
        so it cannot see a keyline added after rasterisation. #110 left four
        marks flush at exactly SAFE; an unbounded 11px ring would have spent
        that margin and shipped outside it with the vector test still green.
        Every shipped PNG is checked here instead.
        """
        from glyphs import GRID, SAFE

        import json
        pad = (GRID - SAFE) / 2
        png_dir = ROOT / "app/src/main/res/drawable-nodpi"
        icons = json.loads((ROOT / "tools/catalog.json").read_text())["icons"]
        for icon in icons:
            path = png_dir / f"{icon['drawable']}.png"
            if not path.exists():
                continue
            box = Image.open(path).convert("RGBA").getchannel("A").getbbox()
            if box is None:
                continue
            margin = min(box[0], box[1], GRID - box[2], GRID - box[3])
            with self.subTest(icon=icon["drawable"]):
                self.assertGreaterEqual(
                    margin, pad,
                    f"{icon['drawable']}: shipped raster leaves {margin}px, "
                    f"SAFE={SAFE} requires {pad:.0f}")


if __name__ == "__main__":
    unittest.main()
