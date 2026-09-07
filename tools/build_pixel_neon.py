#!/usr/bin/env python3
"""Build the genuinely pixel-drawn Core Builds Pixel Neon icon pack.

This is intentionally *not* a pixelated export of the monoline pack. The
original pack's SVG masters, banners, paths, and layout are not read here.
Each catalog row is drawn from a small-pixel sprite recipe selected by its
brand glyph plus a category/name hash, with different silhouettes, poses,
internal details, and neon palettes. The catalog still owns names, brand glyph
cues, and component mappings; this file owns the alternate art direction.

Design constraints:
  * 32x32 source sprites: a classic, readable icon scale rather than a
    downsampled 512px vector.
  * 4-8 practical sprite colours plus a controlled bloom.
  * semantic brand cue first, then silhouette-first composition; no
    anti-aliasing, hard pixel edges, top-left highlights.
  * no baked background in app icons; launcher cards remain visible through
    transparent pixels.

Generated files live under ``pixel-neon/``. Run from the repository root:

    python tools/build_pixel_neon_wallpapers.py
    python tools/build_pixel_neon.py
    python tools/validate_pixel_neon.py

The regular icon generators are deliberately not a prerequisite. This pack
can never silently fall back to the monoline geometry because it has no import
or path to assets/svg or assets/banners. Its separate wallpaper renderer makes
an original 8-bit collection, then the pack builder mirrors that manifest and
its JPEG thumbnails into the APK assets; full-resolution wallpaper sources
stay outside the APK and are fetched by the app on demand.
"""
from __future__ import annotations

import colorsys
import hashlib
import io
import json
import random
import shutil
import sys
from pathlib import Path
from xml.sax.saxutils import escape as xml_escape

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"
OUT = ROOT / "pixel-neon"
PNG_DIR = OUT / "app" / "src" / "main" / "res" / "drawable-nodpi"
XML_DIR = OUT / "app" / "src" / "main" / "res" / "xml"
ASSETS_DIR = OUT / "app" / "src" / "main" / "assets"
VAL_DIR = OUT / "app" / "src" / "main" / "res" / "values"
DOC_DIR = OUT / "docs"
WALLPAPER_MANIFEST_SOURCE = ROOT / "PixelNeonWallpapers" / "manifest.json"
WALLPAPER_THUMBS_SOURCE = ROOT / "PixelNeonWallpapers" / "thumbs"
WALLPAPER_MANIFEST_OUT = ASSETS_DIR / "manifest" / "wallpapers.json"
WALLPAPER_THUMBS_OUT = ASSETS_DIR / "wallpapers_thumbs"

SPRITE_GRID = 32
ICON_SIZE = 512
BANNER_W, BANNER_H = 320, 180
BANNER_GRID_W, BANNER_GRID_H = 160, 90
VOID = "#070916"
OUTLINE = "#0B0D22"
HIGHLIGHT = "#E9FFFF"
NEON_FALLBACKS = ("#00E5FF", "#FF39D7", "#9B6CFF", "#FFE45E", "#52FF9A")

sys.path.insert(0, str(ROOT / "tools"))


def esc(value: str) -> str:
    return xml_escape(str(value), {"'": "&apos;", '"': "&quot;"})


def color_tuple(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def rgb_hex(rgb: tuple[int, int, int]) -> str:
    return "#%02X%02X%02X" % tuple(max(0, min(255, c)) for c in rgb)


def neon_color(source: str, seed: str) -> str:
    """Boost the catalog hue, using an arcade fallback for grey source marks."""
    rgb = color_tuple(source)
    h, saturation, value = colorsys.rgb_to_hsv(*(channel / 255 for channel in rgb))
    digest = hashlib.sha1(seed.encode()).digest()
    if saturation < 0.16 or value < 0.18:
        h = digest[0] / 255.0 * 0.95
    else:
        h = (h + ((digest[0] % 13) - 6) / 360.0) % 1.0
    saturation = max(0.82, min(1.0, saturation + 0.22))
    value = max(0.90, min(1.0, value + 0.15))
    return rgb_hex(tuple(round(channel * 255) for channel in colorsys.hsv_to_rgb(h, saturation, value)))


def palette_for(icon: dict, ordinal: int) -> dict[str, str]:
    seed = f"{icon['drawable']}:{icon['name']}:{ordinal}"
    primary = neon_color(icon["color"], seed)
    digest = hashlib.sha1((seed + ":secondary").encode()).digest()
    secondary = NEON_FALLBACKS[digest[1] % len(NEON_FALLBACKS)]
    if secondary == primary:
        secondary = NEON_FALLBACKS[(digest[1] + 2) % len(NEON_FALLBACKS)]
    # Shadow colours are deliberately chromatic, not a grey anti-aliased edge.
    base = color_tuple(primary)
    shadow = rgb_hex(tuple(round(channel * 0.28 + dark * 0.72)
                         for channel, dark in zip(base, color_tuple(VOID))))
    mid = rgb_hex(tuple(round(channel * 0.52 + dark * 0.48)
                      for channel, dark in zip(base, color_tuple(VOID))))
    return {
        "primary": primary,
        "secondary": secondary,
        "shadow": shadow,
        "mid": mid,
        "outline": OUTLINE,
        "highlight": HIGHLIGHT,
        "void": VOID,
    }


class SpritePainter:
    """Small integer-only drawing surface for one 32x32 pixel sprite."""

    def __init__(self, palette: dict[str, str], seed: int):
        from PIL import Image, ImageDraw

        self.image = Image.new("RGBA", (SPRITE_GRID, SPRITE_GRID), (0, 0, 0, 0))
        self.draw = ImageDraw.Draw(self.image)
        self.palette = palette
        self.rng = random.Random(seed)
        self.primary = palette["primary"]
        self.secondary = palette["secondary"]
        self.shadow = palette["shadow"]
        self.mid = palette["mid"]
        self.outline = palette["outline"]
        self.highlight = palette["highlight"]
        self.void = palette["void"]

    def r(self, box, fill=None, outline=None, width=1):
        self.draw.rectangle(tuple(int(v) for v in box), fill=fill or self.primary,
                            outline=outline, width=width)

    def poly(self, points, fill=None, outline=None, width=1):
        self.draw.polygon([(int(x), int(y)) for x, y in points], fill=fill or self.primary)
        if outline:
            self.draw.line([(int(x), int(y)) for x, y in points + [points[0]]],
                           fill=outline, width=width, joint="curve")

    def line(self, points, fill=None, width=1):
        self.draw.line([(int(x), int(y)) for x, y in points], fill=fill or self.primary,
                       width=width)

    def dot(self, x, y, fill=None):
        self.r((x, y, x, y), fill or self.primary)

    def stepped_box(self, x, y, w, h, fill=None, cut=2, outline=None):
        points = [(x + cut, y), (x + w - cut, y), (x + w, y + cut),
                  (x + w, y + h - cut), (x + w - cut, y + h),
                  (x + cut, y + h), (x, y + h - cut), (x, y + cut)]
        self.poly(points, fill or self.primary, outline)

    def stair(self, points, fill=None):
        """Draw a deliberately stepped polyline, one pixel per run."""
        for (x0, y0), (x1, y1) in zip(points, points[1:]):
            x0, y0, x1, y1 = map(int, (x0, y0, x1, y1))
            if x0 == x1 or y0 == y1:
                self.line([(x0, y0), (x1, y1)], fill=fill)
                continue
            dx, dy = abs(x1 - x0), abs(y1 - y0)
            sx, sy = (1 if x1 >= x0 else -1), (1 if y1 >= y0 else -1)
            if dx >= dy:
                for step in range(dx + 1):
                    x = x0 + step * sx
                    y = y0 + round(step * dy / max(dx, 1)) * sy
                    self.dot(x, y, fill)
            else:
                for step in range(dy + 1):
                    y = y0 + step * sy
                    x = x0 + round(step * dx / max(dy, 1)) * sx
                    self.dot(x, y, fill)

    def glint(self, x, y):
        self.dot(x, y, self.highlight)
        if self.rng.random() > 0.35:
            self.dot(x + 1, y, self.highlight)

    def signature(self, ordinal: int, attempt: int = 0):
        """A tiny deterministic sprite signature; also resolves hash collisions."""
        x = 2 + ((ordinal * 7 + attempt * 3) % 28)
        y = 2 + ((ordinal * 11 + attempt * 5) % 28)
        self.dot(x, y, self.secondary if (ordinal + attempt) % 2 else self.highlight)
        if (ordinal + attempt) % 3 == 0 and x < 30:
            self.dot(x + 1, y, self.secondary)


# ---------------------------------------------------------------------------
# Pixel sprite recipes. These are filled sprites and stepped silhouettes, not
# traces of the monoline glyph library. Brand-aware constructions below use the
# catalog's semantic glyph cue (tile, eye, shield, play, wave, etc.) and then
# vary positions, proportions, highlights, and internal patterns through
# SpritePainter.rng.
# ---------------------------------------------------------------------------
def motif_screen(p: SpritePainter):
    x, y = p.rng.choice([4, 5, 6]), p.rng.choice([5, 6, 7])
    w, h = p.rng.choice([20, 21, 22]), p.rng.choice([13, 14, 15])
    p.stepped_box(x + 1, y + 1, w, h, p.shadow, 2)
    p.stepped_box(x, y, w, h, p.primary, 2)
    p.r((x + 3, y + 3, x + w - 4, y + h - 4), p.void)
    for row in range(y + 5, y + h - 3, 3):
        p.r((x + 4, row, x + w - 5, row), p.mid)
    if p.rng.random() > 0.35:
        cx, cy = x + w // 2, y + h // 2
        p.poly([(cx - 3, cy - 4), (cx + 4, cy), (cx - 3, cy + 4)], p.secondary)
        p.dot(cx - 3, cy - 4, p.highlight)
    p.r((x + 7, y + h + 1, x + w - 7, y + h + 2), p.primary)
    p.r((x + w // 2 - 1, y + h + 3, x + w // 2 + 1, y + h + 4), p.secondary)
    p.glint(x + 3, y + 2)


def motif_portal(p: SpritePainter):
    cx, cy = p.rng.choice([14, 15, 16, 17]), p.rng.choice([14, 15, 16])
    outer = p.rng.choice([10, 11, 12])
    p.poly([(cx, cy - outer), (cx + outer - 3, cy - outer),
            (cx + outer, cy - outer + 3), (cx + outer, cy + outer - 3),
            (cx + outer - 3, cy + outer), (cx - outer + 3, cy + outer),
            (cx - outer, cy + outer - 3), (cx - outer, cy - outer + 3)], p.shadow)
    inner = outer - 2
    p.poly([(cx, cy - inner), (cx + inner - 2, cy - inner),
            (cx + inner, cy - inner + 2), (cx + inner, cy + inner - 2),
            (cx + inner - 2, cy + inner), (cx - inner + 2, cy + inner),
            (cx - inner, cy + inner - 2), (cx - inner, cy - inner + 2)], p.primary)
    p.r((cx - inner + 3, cy - inner + 3, cx + inner - 3, cy + inner - 3), p.void)
    p.r((cx - 2, cy - 5, cx + 2, cy - 3), p.secondary)
    p.r((cx + 3, cy - 2, cx + 5, cy + 2), p.secondary)
    p.r((cx - 2, cy + 3, cx + 2, cy + 5), p.mid)
    p.glint(cx - inner + 1, cy - inner + 1)


def motif_reel(p: SpritePainter):
    x, y = p.rng.choice([5, 6, 7]), p.rng.choice([7, 8, 9])
    w, h = p.rng.choice([20, 21]), p.rng.choice([15, 16])
    p.r((x + 1, y + 1, x + w, y + h), p.shadow)
    p.r((x, y, x + w - 1, y + h - 1), p.primary)
    p.r((x + 3, y + 3, x + w - 4, y + h - 4), p.void)
    for row in [y + 4, y + h - 5]:
        p.r((x + 4, row, x + w - 5, row + 1), p.secondary)
    for hole in range(x + 4, x + w - 4, 4):
        p.r((hole, y + 1, hole + 1, y + 2), p.highlight)
        p.r((hole, y + h - 3, hole + 1, y + h - 2), p.mid)
    p.r((x + 7, y + 6, x + w - 8, y + h - 7), p.secondary)
    p.r((x + 9, y + 7, x + w - 10, y + h - 8), p.void)
    p.glint(x + 3, y + 2)


def motif_ticket(p: SpritePainter):
    x, y = p.rng.choice([5, 6]), p.rng.choice([9, 10, 11])
    w, h = p.rng.choice([21, 22]), p.rng.choice([10, 11])
    p.poly([(x + 2, y), (x + w - 2, y), (x + w, y + 2),
            (x + w, y + h - 2), (x + w - 2, y + h), (x + 2, y + h),
            (x, y + h - 2), (x, y + 2)], p.shadow)
    p.poly([(x + 2, y - 1), (x + w - 2, y - 1), (x + w - 1, y + 1),
            (x + w - 1, y + h - 3), (x + w - 3, y + h - 1),
            (x + 2, y + h - 1), (x + 1, y + h - 3), (x + 1, y + 1)], p.primary)
    p.r((x + 4, y + 3, x + w - 5, y + 4), p.highlight)
    p.r((x + 4, y + 6, x + w - 8, y + 7), p.secondary)
    p.r((x + w - 6, y + 5, x + w - 4, y + 6), p.mid)
    p.r((x + 7, y + h + 2, x + 10, y + h + 3), p.secondary)
    p.glint(x + 3, y)


def motif_antenna(p: SpritePainter):
    cx = p.rng.choice([14, 15, 16, 17])
    base = p.rng.choice([23, 24, 25])
    p.r((cx - 7, base, cx + 7, base + 2), p.shadow)
    p.r((cx - 6, base - 1, cx + 6, base + 1), p.primary)
    p.r((cx - 1, base - 16, cx + 1, base - 2), p.secondary)
    p.dot(cx, base - 18, p.highlight)
    p.stair([(cx - 2, base - 13), (cx - 6, base - 10), (cx - 8, base - 6)], p.primary)
    p.stair([(cx + 2, base - 13), (cx + 6, base - 10), (cx + 8, base - 6)], p.primary)
    p.r((cx - 6, base - 6, cx - 4, base - 5), p.secondary)
    p.r((cx + 4, base - 6, cx + 6, base - 5), p.secondary)
    p.r((cx - 4, base - 5, cx + 4, base - 3), p.mid)


def motif_wave(p: SpritePainter):
    x = p.rng.choice([4, 5, 6])
    y = p.rng.choice([10, 11, 12])
    width = p.rng.choice([21, 22, 23])
    points = [(x, y + 3), (x + 3, y + 3), (x + 5, y + 1),
              (x + 8, y + 1), (x + 10, y + 4), (x + 13, y + 4),
              (x + 15, y + 2), (x + 18, y + 2), (x + width, y + 5)]
    p.stair(points, p.shadow)
    p.stair([(x, y + 2), (x + 3, y + 2), (x + 5, y),
              (x + 8, y), (x + 10, y + 3), (x + 13, y + 3),
              (x + 15, y + 1), (x + 18, y + 1), (x + width, y + 4)], p.primary)
    p.stair([(x + 2, y + 8), (x + 5, y + 8), (x + 7, y + 6),
              (x + 11, y + 6), (x + 13, y + 9), (x + 17, y + 9),
              (x + width - 1, y + 7)], p.secondary)
    for dot in range(2):
        p.dot(x + p.rng.randrange(3, 20), y + p.rng.choice([-4, 12]), p.highlight)


def motif_note(p: SpritePainter):
    x, y = p.rng.choice([8, 9, 10]), p.rng.choice([5, 6, 7])
    p.r((x + 8, y, x + 10, y + 17), p.shadow)
    p.r((x + 7, y, x + 9, y + 16), p.primary)
    p.r((x + 9, y, x + 18, y + 2), p.primary)
    p.r((x + 17, y + 1, x + 19, y + 11), p.secondary)
    p.r((x + 3, y + 15, x + 8, y + 19), p.primary)
    p.r((x + 14, y + 10, x + 18, y + 14), p.secondary)
    p.r((x + 4, y + 16, x + 7, y + 18), p.highlight)
    p.r((x + 15, y + 11, x + 17, y + 13), p.highlight)
    p.r((x + 3, y + 20, x + 7, y + 21), p.mid)
    p.r((x + 14, y + 16, x + 18, y + 17), p.mid)


def motif_folder(p: SpritePainter):
    x, y = p.rng.choice([4, 5]), p.rng.choice([8, 9])
    w, h = p.rng.choice([22, 23]), p.rng.choice([14, 15])
    p.poly([(x + 1, y + 2), (x + 8, y + 2), (x + 10, y),
            (x + 15, y), (x + 17, y + 2), (x + w, y + 2),
            (x + w, y + h), (x + 1, y + h)], p.shadow)
    p.poly([(x, y + 1), (x + 7, y + 1), (x + 9, y - 1),
            (x + 14, y - 1), (x + 16, y + 1), (x + w - 1, y + 1),
            (x + w - 1, y + h - 1), (x, y + h - 1)], p.primary)
    p.r((x + 3, y + 5, x + w - 4, y + 6), p.secondary)
    p.r((x + 3, y + 9, x + w - 8, y + 10), p.mid)
    p.r((x + w - 6, y + 4, x + w - 4, y + 5), p.highlight)
    p.glint(x + 2, y + 1)


def motif_terminal(p: SpritePainter):
    x, y = p.rng.choice([5, 6]), p.rng.choice([5, 6, 7])
    w, h = p.rng.choice([20, 21]), p.rng.choice([16, 17])
    p.stepped_box(x + 1, y + 1, w, h, p.shadow, 1)
    p.stepped_box(x, y, w, h, p.secondary, 1)
    p.r((x + 3, y + 3, x + w - 4, y + h - 4), p.void)
    p.r((x + 5, y + 6, x + 7, y + 7), p.primary)
    p.r((x + 8, y + 7, x + 10, y + 8), p.primary)
    p.r((x + 11, y + 8, x + 13, y + 9), p.highlight)
    p.r((x + 6, y + 12, x + w - 6, y + 13), p.mid)
    p.r((x + 5, y + h + 1, x + w - 5, y + h + 2), p.primary)
    p.glint(x + 2, y + 2)


def motif_shield(p: SpritePainter):
    cx = p.rng.choice([14, 15, 16, 17])
    top = p.rng.choice([4, 5, 6])
    p.poly([(cx, top), (cx + 9, top + 3), (cx + 8, top + 13),
            (cx + 4, top + 19), (cx, top + 22), (cx - 4, top + 19),
            (cx - 8, top + 13), (cx - 9, top + 3)], p.shadow)
    p.poly([(cx, top - 1), (cx + 8, top + 2), (cx + 7, top + 12),
            (cx + 3, top + 18), (cx, top + 20), (cx - 3, top + 18),
            (cx - 7, top + 12), (cx - 8, top + 2)], p.primary)
    p.r((cx - 2, top + 8, cx + 2, top + 13), p.void)
    p.r((cx - 4, top + 10, cx + 4, top + 12), p.secondary)
    p.r((cx - 1, top + 6, cx + 1, top + 10), p.secondary)
    p.dot(cx - 5, top + 4, p.highlight)


def motif_globe(p: SpritePainter):
    cx, cy = p.rng.choice([14, 15, 16, 17]), p.rng.choice([14, 15, 16])
    r = p.rng.choice([9, 10, 11])
    p.poly([(cx - r + 2, cy - r), (cx + r - 2, cy - r),
            (cx + r, cy - r + 2), (cx + r, cy + r - 2),
            (cx + r - 2, cy + r), (cx - r + 2, cy + r),
            (cx - r, cy + r - 2), (cx - r, cy - r + 2)], p.shadow)
    p.poly([(cx - r + 1, cy - r + 1), (cx + r - 3, cy - r + 1),
            (cx + r - 1, cy - r + 3), (cx + r - 1, cy + r - 3),
            (cx + r - 3, cy + r - 1), (cx - r + 3, cy + r - 1),
            (cx - r + 1, cy + r - 3), (cx - r + 1, cy - r + 3)], p.primary)
    p.line([(cx - r + 2, cy), (cx + r - 2, cy)], p.secondary)
    p.line([(cx, cy - r + 2), (cx, cy + r - 2)], p.secondary)
    p.line([(cx - r + 3, cy - 4), (cx + r - 3, cy - 4)], p.mid)
    p.dot(cx - r + 2, cy - r + 2, p.highlight)


def motif_game(p: SpritePainter):
    x, y = p.rng.choice([4, 5, 6]), p.rng.choice([9, 10, 11])
    p.poly([(x + 4, y), (x + 9, y), (x + 11, y + 2),
            (x + 14, y + 2), (x + 16, y), (x + 21, y),
            (x + 23, y + 4), (x + 21, y + 12), (x + 18, y + 13),
            (x + 14, y + 9), (x + 10, y + 9), (x + 6, y + 13),
            (x + 2, y + 12), (x, y + 4)], p.shadow)
    p.poly([(x + 4, y - 1), (x + 9, y - 1), (x + 11, y + 1),
            (x + 14, y + 1), (x + 16, y - 1), (x + 21, y - 1),
            (x + 22, y + 3), (x + 20, y + 11), (x + 17, y + 12),
            (x + 13, y + 8), (x + 10, y + 8), (x + 6, y + 12),
            (x + 3, y + 11), (x + 1, y + 3)], p.primary)
    p.r((x + 5, y + 4, x + 7, y + 5), p.highlight)
    p.r((x + 6, y + 3, x + 6, y + 6), p.highlight)
    p.dot(x + 17, y + 4, p.secondary)
    p.dot(x + 19, y + 6, p.secondary)


def motif_cart(p: SpritePainter):
    x, y = p.rng.choice([5, 6]), p.rng.choice([7, 8])
    p.r((x, y, x + 2, y + 3), p.secondary)
    p.line([(x + 2, y + 2), (x + 5, y + 4), (x + 20, y + 4), (x + 18, y + 14)], p.shadow, 3)
    p.r((x + 5, y + 3, x + 19, y + 12), p.primary)
    for col in range(x + 7, x + 19, 4):
        p.r((col, y + 5, col + 1, y + 10), p.mid)
    p.r((x + 7, y + 15, x + 10, y + 18), p.secondary)
    p.r((x + 17, y + 15, x + 20, y + 18), p.secondary)
    p.dot(x + 8, y + 15, p.highlight)


def motif_ball(p: SpritePainter):
    cx, cy = p.rng.choice([14, 15, 16, 17]), p.rng.choice([14, 15, 16])
    r = p.rng.choice([8, 9])
    p.poly([(cx, cy - r), (cx + r - 3, cy - r), (cx + r, cy - r + 3),
            (cx + r, cy + r - 3), (cx + r - 3, cy + r),
            (cx - r + 3, cy + r), (cx - r, cy + r - 3),
            (cx - r, cy - r + 3), (cx - r + 3, cy - r)], p.shadow)
    p.poly([(cx, cy - r + 1), (cx + r - 4, cy - r + 1),
            (cx + r - 1, cy - r + 4), (cx + r - 1, cy + r - 4),
            (cx + r - 4, cy + r - 1), (cx - r + 4, cy + r - 1),
            (cx - r + 1, cy + r - 4), (cx - r + 1, cy - r + 4)], p.primary)
    p.stair([(cx - 5, cy - 2), (cx - 2, cy), (cx + 2, cy), (cx + 5, cy + 3)], p.secondary)
    p.r((cx - 5, cy - 4, cx - 3, cy - 3), p.highlight)


def motif_remote(p: SpritePainter):
    x, y = p.rng.choice([9, 10, 11]), p.rng.choice([4, 5])
    w, h = p.rng.choice([11, 12]), p.rng.choice([22, 23])
    p.stepped_box(x + 1, y + 1, w, h, p.shadow, 2)
    p.stepped_box(x, y, w, h, p.secondary, 2)
    p.r((x + 3, y + 3, x + w - 4, y + 6), p.primary)
    p.dot(x + w // 2, y + 4, p.highlight)
    for row in range(y + 9, y + h - 3, 4):
        p.r((x + 3, row, x + 4, row + 1), p.primary)
        p.r((x + w - 5, row, x + w - 4, row + 1), p.mid)
    p.r((x + 4, y + h - 4, x + w - 5, y + h - 3), p.highlight)


def motif_crystal(p: SpritePainter):
    cx, cy = p.rng.choice([14, 15, 16, 17]), p.rng.choice([14, 15, 16])
    points = [(cx, cy - 12), (cx + 8, cy - 4), (cx + 6, cy + 8),
              (cx, cy + 12), (cx - 7, cy + 6), (cx - 8, cy - 5)]
    p.poly([(x + 1, y + 1) for x, y in points], p.shadow)
    p.poly(points, p.primary)
    p.poly([(cx, cy - 10), (cx + 3, cy - 3), (cx, cy + 7), (cx - 3, cy - 3)], p.secondary)
    p.poly([(cx, cy - 10), (cx + 2, cy - 4), (cx, cy - 2), (cx - 2, cy - 4)], p.highlight)
    p.r((cx - 11, cy + 10, cx - 8, cy + 11), p.secondary)


def motif_cloud(p: SpritePainter):
    x, y = p.rng.choice([5, 6]), p.rng.choice([9, 10])
    p.r((x + 3, y + 4, x + 19, y + 12), p.shadow)
    p.r((x + 6, y + 1, x + 12, y + 9), p.shadow)
    p.r((x + 14, y + 3, x + 19, y + 10), p.shadow)
    p.r((x + 2, y + 3, x + 18, y + 11), p.primary)
    p.r((x + 5, y, x + 11, y + 8), p.primary)
    p.r((x + 13, y + 2, x + 18, y + 9), p.primary)
    p.r((x + 5, y + 7, x + 16, y + 10), p.secondary)
    p.r((x + 10, y + 13, x + 11, y + 19), p.primary)
    p.r((x + 7, y + 17, x + 14, y + 18), p.secondary)
    p.dot(x + 7, y + 1, p.highlight)


def motif_trophy(p: SpritePainter):
    x, y = p.rng.choice([7, 8]), p.rng.choice([5, 6])
    p.r((x + 5, y, x + 14, y + 2), p.shadow)
    p.r((x + 6, y, x + 13, y + 10), p.primary)
    p.r((x + 2, y + 2, x + 5, y + 8), p.secondary)
    p.r((x + 14, y + 2, x + 17, y + 8), p.secondary)
    p.r((x + 7, y + 3, x + 12, y + 6), p.highlight)
    p.r((x + 8, y + 10, x + 11, y + 16), p.primary)
    p.r((x + 5, y + 16, x + 14, y + 18), p.secondary)
    p.r((x + 3, y + 19, x + 16, y + 20), p.mid)


def motif_camera(p: SpritePainter):
    x, y = p.rng.choice([5, 6]), p.rng.choice([9, 10])
    p.r((x + 5, y - 2, x + 11, y), p.secondary)
    p.stepped_box(x + 1, y + 1, 21, 13, p.shadow, 2)
    p.stepped_box(x, y, 21, 13, p.primary, 2)
    p.r((x + 5, y + 3, x + 15, y + 10), p.void)
    p.r((x + 8, y + 4, x + 12, y + 8), p.secondary)
    p.r((x + 9, y + 5, x + 11, y + 7), p.highlight)
    p.dot(x + 17, y + 3, p.highlight)


def motif_chat(p: SpritePainter):
    x, y = p.rng.choice([5, 6]), p.rng.choice([7, 8])
    p.poly([(x + 2, y), (x + 20, y), (x + 20, y + 12),
            (x + 14, y + 12), (x + 10, y + 17), (x + 10, y + 12),
            (x + 2, y + 12)], p.shadow)
    p.poly([(x + 1, y - 1), (x + 19, y - 1), (x + 19, y + 11),
            (x + 13, y + 11), (x + 9, y + 16), (x + 9, y + 11),
            (x + 1, y + 11)], p.primary)
    for dot in [5, 9, 13]:
        p.r((x + dot, y + 5, x + dot + 1, y + 6), p.secondary)
    p.dot(x + 3, y + 1, p.highlight)


MOTIFS = {
    "antenna": motif_antenna,
    "screen": motif_screen,
    "portal": motif_portal,
    "reel": motif_reel,
    "ticket": motif_ticket,
    "wave": motif_wave,
    "note": motif_note,
    "folder": motif_folder,
    "terminal": motif_terminal,
    "shield": motif_shield,
    "globe": motif_globe,
    "game": motif_game,
    "cart": motif_cart,
    "ball": motif_ball,
    "remote": motif_remote,
    "crystal": motif_crystal,
    "cloud": motif_cloud,
    "trophy": motif_trophy,
    "camera": motif_camera,
    "chat": motif_chat,
}

CATEGORY_MOTIFS = {
    "STREAM": ("portal", "screen", "wave", "chat"),
    "MEDIA": ("reel", "screen", "portal", "camera"),
    "VOD": ("ticket", "screen", "reel", "portal"),
    "LIVE": ("antenna", "screen", "wave", "camera"),
    "PLAYER": ("screen", "remote", "terminal", "wave"),
    "VIDEO": ("screen", "reel", "camera", "ticket"),
    "MUSIC": ("note", "wave", "screen", "portal"),
    "SPORT": ("ball", "trophy", "wave", "screen"),
    "GAMING": ("game", "crystal", "screen", "portal"),
    "DEBRID": ("crystal", "portal", "cloud", "terminal"),
    "FILES": ("folder", "cloud", "terminal", "crystal"),
    "TOOL": ("terminal", "crystal", "remote", "camera"),
    "STORE": ("cart", "ticket", "folder", "crystal"),
    "LAUNCHER": ("portal", "screen", "crystal", "remote"),
    "VPN": ("shield", "portal", "terminal", "crystal"),
    "BROWSER": ("globe", "portal", "screen", "wave"),
    "REMOTE": ("remote", "terminal", "screen", "game"),
    "SYSTEM": ("terminal", "shield", "crystal", "remote"),
    "TRACK": ("globe", "wave", "portal", "terminal"),
    "CORE": ("crystal", "portal", "terminal", "shield"),
    "APP": ("crystal", "screen", "portal", "camera", "terminal", "wave"),
}

# A handful of high-recognition entries get a deliberately chosen *abstract*
# sprite family. These are not logo traces; they only choose a distinct object.
SPECIAL_MOTIFS = {
    "corebuilds": "crystal", "stremio": "portal", "kodi": "reel",
    "jellyfin": "crystal", "emby": "shield", "plex": "portal",
    "netflix": "ticket", "spotify": "note", "youtube": "screen",
    "twitch": "chat", "vlc": "reel", "mx_player": "screen",
    "downloader": "terminal", "aurora_store": "crystal",
    "real_debrid": "crystal", "alldebrid": "portal", "trakt": "trophy",
}


def choose_motif(icon: dict, ordinal: int, rng: random.Random) -> str:
    # The catalog's glyph is the brand cue. Category recipes remain a safe
    # fallback for future rows without a glyph, but known catalog rows should
    # never become a random generic screen/portal just because they share a
    # category with another app.
    if icon.get("glyph"):
        return "brand"
    drawable = icon["drawable"]
    if drawable in SPECIAL_MOTIFS:
        return SPECIAL_MOTIFS[drawable]
    options = CATEGORY_MOTIFS.get(icon.get("category") or "APP", CATEGORY_MOTIFS["APP"])
    return options[rng.randrange(len(options))]


def non_wrapping_shift(mask, dx: int, dy: int):
    from PIL import Image

    out = Image.new("L", mask.size, 0)
    width, height = mask.size
    src_left = max(0, -dx)
    src_top = max(0, -dy)
    src_right = min(width, width - dx) if dx >= 0 else width
    src_bottom = min(height, height - dy) if dy >= 0 else height
    if src_right > src_left and src_bottom > src_top:
        out.paste(mask.crop((src_left, src_top, src_right, src_bottom)),
                  (src_left + dx, src_top + dy))
    return out


def solid_layer(color: str, mask):
    from PIL import Image

    layer = Image.new("RGBA", mask.size, color_tuple(color) + (0,))
    layer.putalpha(mask)
    return layer


def render_sprite(painter: SpritePainter):
    from PIL import Image, ImageChops, ImageFilter

    mask = painter.image.getchannel("A")
    expanded = mask.filter(ImageFilter.MaxFilter(3))
    shadow_ring = ImageChops.subtract(expanded, mask)
    bloom_wide = mask.filter(ImageFilter.GaussianBlur(2.5)).point(
        lambda pixel: min(155, round(pixel * 0.62)), mode="L")
    bloom_core = mask.filter(ImageFilter.GaussianBlur(1.0)).point(
        lambda pixel: min(215, round(pixel * 0.86)), mode="L")

    out = Image.new("RGBA", (SPRITE_GRID, SPRITE_GRID), (0, 0, 0, 0))
    out.alpha_composite(solid_layer("#542A9E", bloom_wide))
    out.alpha_composite(solid_layer(painter.primary, bloom_core))
    out.alpha_composite(solid_layer(painter.outline, shadow_ring))
    out.alpha_composite(painter.image)

    # Pixel-art lighting: a restrained top-left glint and a low-right shade.
    top_left = ImageChops.subtract(mask, non_wrapping_shift(mask, 1, 1))
    bottom_right = ImageChops.subtract(mask, non_wrapping_shift(mask, -1, -1))
    out.alpha_composite(solid_layer(painter.highlight, top_left.point(lambda p: round(p * .78))))
    out.alpha_composite(solid_layer(painter.shadow, bottom_right.point(lambda p: round(p * .48))))
    final = out.resize((ICON_SIZE, ICON_SIZE), Image.Resampling.NEAREST)
    return final, out, mask


# A tiny 3x5 bitmap alphabet keeps wide banners pixel-authentic too.
PIXEL_FONT = {
    "A": ("010", "101", "111", "101", "101"), "B": ("110", "101", "110", "101", "110"),
    "C": ("011", "100", "100", "100", "011"), "D": ("110", "101", "101", "101", "110"),
    "E": ("111", "100", "110", "100", "111"), "F": ("111", "100", "110", "100", "100"),
    "G": ("011", "100", "101", "101", "011"), "H": ("101", "101", "111", "101", "101"),
    "I": ("111", "010", "010", "010", "111"), "J": ("001", "001", "001", "101", "010"),
    "K": ("101", "101", "110", "101", "101"), "L": ("100", "100", "100", "100", "111"),
    "M": ("10001", "11011", "10101", "10101", "10101"), "N": ("1001", "1101", "1011", "1001", "1001"),
    "O": ("010", "101", "101", "101", "010"), "P": ("110", "101", "110", "100", "100"),
    "Q": ("010", "101", "101", "011", "001"), "R": ("110", "101", "110", "101", "101"),
    "S": ("011", "100", "010", "001", "110"), "T": ("111", "010", "010", "010", "010"),
    "U": ("101", "101", "101", "101", "010"), "V": ("101", "101", "101", "101", "010"),
    "W": ("10001", "10001", "10101", "11011", "10001"), "X": ("101", "101", "010", "101", "101"),
    "Y": ("101", "101", "010", "010", "010"), "Z": ("111", "001", "010", "100", "111"),
    "0": ("111", "101", "101", "101", "111"), "1": ("010", "110", "010", "010", "111"),
    "2": ("110", "001", "010", "100", "111"), "3": ("110", "001", "010", "001", "110"),
    "4": ("101", "101", "111", "001", "001"), "5": ("111", "100", "110", "001", "110"),
    "6": ("011", "100", "111", "101", "010"), "7": ("111", "001", "010", "010", "010"),
    "8": ("010", "101", "010", "101", "010"), "9": ("010", "101", "011", "001", "110"),
    "&": ("010", "101", "010", "101", "011"), "+": ("000", "010", "111", "010", "000"),
    "-": ("000", "000", "111", "000", "000"), ".": ("000", "000", "000", "000", "010"),
    " ": ("000", "000", "000", "000", "000"),
}


def text_pattern(text: str) -> list[tuple[str, ...]]:
    return [PIXEL_FONT.get(char, PIXEL_FONT["-"]) for char in text.upper()]


def text_width(text: str, scale: int = 1) -> int:
    patterns = text_pattern(text)
    return sum(len(pattern[0]) * scale + scale for pattern in patterns) - scale if patterns else 0


def draw_pixel_text(draw, text: str, x: int, y: int, color: str, scale: int = 1,
                    max_width: int | None = None) -> int:
    if max_width is not None:
        while text and text_width(text, scale) > max_width:
            text = text[:-1]
    cursor = x
    for pattern in text_pattern(text):
        for row, bits in enumerate(pattern):
            for col, bit in enumerate(bits):
                if bit == "1":
                    draw.rectangle((cursor + col * scale, y + row * scale,
                                    cursor + col * scale + scale - 1,
                                    y + row * scale + scale - 1), fill=color)
        cursor += len(pattern[0]) * scale + scale
    return cursor - x


def brand_initials(name: str) -> str:
    """Return a compact brand token that remains legible on the 32px grid."""
    words = [word for word in clean_label(name).split() if word]
    if not words:
        return "CB"
    if len(words) == 1:
        word = words[0]
        return word[:2]
    return "".join(word[0] for word in words)[:3]


def brand_text(p: SpritePainter, text: str, x: int, y: int, color: str,
               scale: int = 1, max_width: int | None = None) -> int:
    return draw_pixel_text(p.draw, text, x, y, color, scale=scale, max_width=max_width)


def brand_tile(p: SpritePainter, icon: dict, glyph: str):
    """Pixel-native brand monograms with varied containers for tile glyphs."""
    token = glyph.removeprefix("tile_")
    label = token if token in {"10", "7", "9", "N"} else brand_initials(icon["name"])
    variant = 0 if token in {"10", "7", "9"} else p.rng.randrange(4)

    if variant == 0:
        x, y = p.rng.choice([3, 4, 5]), p.rng.choice([3, 4, 5])
        w, h = p.rng.choice([22, 23, 24]), p.rng.choice([22, 23, 24])
        p.stepped_box(x + 1, y + 1, w, h, p.shadow, 2)
        p.stepped_box(x, y, w, h, p.primary, 2)
        p.r((x + 3, y + 3, x + w - 4, y + h - 4), p.void)
        tx, ty, max_width = x + 3, y + 3, w - 6
    elif variant == 1:
        x, y = p.rng.choice([3, 4]), p.rng.choice([8, 9, 10])
        w, h = p.rng.choice([24, 25]), p.rng.choice([13, 14])
        p.stepped_box(x + 1, y + 1, w, h, p.shadow, 3)
        p.stepped_box(x, y, w, h, p.primary, 3)
        p.r((x + 3, y + 3, x + w - 4, y + h - 4), p.void)
        p.r((x + 4, y + h - 4, x + w - 5, y + h - 3), p.secondary)
        tx, ty, max_width = x + 3, y + 3, w - 6
    elif variant == 2:
        cx, cy = p.rng.choice([15, 16, 17]), p.rng.choice([14, 15, 16])
        r = p.rng.choice([10, 11])
        p.poly([(cx, cy - r - 1), (cx + r, cy - 4), (cx + r + 1, cy + 4),
                (cx, cy + r + 1), (cx - r - 1, cy + 4), (cx - r, cy - 4)], p.shadow)
        p.poly([(cx, cy - r), (cx + r - 1, cy - 3), (cx + r, cy + 3),
                (cx, cy + r), (cx - r, cy + 3), (cx - r + 1, cy - 3)], p.primary)
        p.r((cx - r + 3, cy - 5, cx + r - 3, cy + 5), p.void)
        tx, ty, max_width = cx - r + 3, cy - 3, (r * 2) - 6
    else:
        x, y = 4, p.rng.choice([5, 6, 7])
        w, h = p.rng.choice([22, 23]), p.rng.choice([20, 21])
        p.r((x + 1, y + 1, x + w, y + h), p.shadow)
        p.r((x, y, x + w - 1, y + h - 1), p.primary)
        p.r((x + 3, y + 3, x + w - 4, y + h - 4), p.void)
        p.r((x + w - 6, y + 3, x + w - 4, y + h - 4), p.secondary)
        tx, ty, max_width = x + 3, y + 6, w - 10

    scale = 2 if text_width(label, 2) <= max_width else 1
    width = text_width(label, scale)
    tx += max(0, (max_width - width) // 2)
    brand_text(p, label, tx, ty + max(0, (10 - 5 * scale) // 2),
               p.secondary, scale=scale, max_width=max_width)
    p.glint(6, 6)


def brand_play(p: SpritePainter, icon: dict, glyph: str):
    """Rebuild play brands as stepped badges, not as the monoline play path."""
    if glyph == "play_store_tri":
        p.poly([(7, 4), (7, 28), (26, 16)], p.shadow)
        p.poly([(6, 3), (6, 27), (25, 15)], p.primary)
        p.poly([(8, 5), (8, 25), (17, 15)], p.secondary)
        p.poly([(8, 5), (17, 15), (22, 12)], p.highlight)
        return
    if glyph == "plus_star":
        # A tiny castle silhouette is the Disney+ cue; the star/plus is kept
        # abstract so it remains an independent pixel recipe.
        p.r((5, 15, 27, 26), p.shadow)
        p.r((6, 14, 26, 25), p.primary)
        p.r((9, 10, 13, 24), p.secondary)
        p.r((15, 7, 19, 24), p.secondary)
        p.r((21, 12, 23, 24), p.secondary)
        p.poly([(17, 3), (18, 6), (21, 6), (19, 8), (20, 11), (17, 9),
                (14, 11), (15, 8), (13, 6), (16, 6)], p.highlight)
        return
    x, y = p.rng.choice([4, 5, 6]), p.rng.choice([5, 6, 7])
    w, h = p.rng.choice([21, 22]), p.rng.choice([15, 16])
    p.stepped_box(x + 1, y + 1, w, h, p.shadow, 2)
    p.stepped_box(x, y, w, h, p.primary, 2)
    p.r((x + 3, y + 3, x + w - 4, y + h - 4), p.void)
    cx, cy = x + w // 2, y + h // 2
    p.poly([(cx - 3, cy - 5), (cx + 5, cy), (cx - 3, cy + 5)], p.secondary)
    p.dot(cx - 3, cy - 5, p.highlight)
    if glyph in {"play_round", "stremio_square", "smarttube_play", "yt_play"}:
        p.dot(x + w - 4, y + 3, p.highlight)


def brand_eye(p: SpritePainter, icon: dict, glyph: str):
    cx, cy = p.rng.choice([14, 15, 16, 17]), p.rng.choice([14, 15, 16])
    p.poly([(cx - 11, cy), (cx - 6, cy - 5), (cx, cy - 7),
            (cx + 7, cy - 5), (cx + 11, cy), (cx + 6, cy + 5),
            (cx, cy + 7), (cx - 7, cy + 5)], p.shadow)
    p.poly([(cx - 10, cy), (cx - 5, cy - 4), (cx, cy - 6),
            (cx + 6, cy - 4), (cx + 10, cy), (cx + 5, cy + 4),
            (cx, cy + 6), (cx - 6, cy + 4)], p.primary)
    p.stepped_box(cx - 4, cy - 4, 8, 8, p.void, 2)
    p.r((cx - 2, cy - 2, cx + 2, cy + 2), p.secondary)
    p.dot(cx - 2, cy - 2, p.highlight)


def brand_shield(p: SpritePainter, icon: dict, glyph: str):
    motif_shield(p)
    cx = 15 if glyph == "adguard_shield" else 16
    if glyph in {"adguard_shield", "mullvad_shield", "proton_shield"}:
        p.stair([(cx - 4, 15), (cx - 1, 18), (cx + 5, 11)], p.highlight)
    elif glyph == "emby_shield":
        p.poly([(cx - 3, 12), (cx + 4, 16), (cx - 3, 20)], p.secondary)
    elif glyph == "shield_key":
        p.r((cx - 1, 12, cx + 1, 20), p.secondary)
        p.r((cx - 4, 15, cx + 2, 17), p.secondary)
        p.r((cx + 2, 18, cx + 4, 20), p.highlight)


def brand_wave(p: SpritePainter, icon: dict, glyph: str):
    """Pixel reinterpretations of wave, arc, and equalizer brand cues."""
    if glyph == "spotify_arcs":
        p.poly([(16, 3), (24, 6), (28, 15), (24, 25), (16, 29), (8, 25),
                (4, 16), (8, 6)], p.shadow)
        p.poly([(16, 2), (23, 5), (27, 15), (23, 24), (16, 28), (9, 24),
                (5, 15), (9, 5)], p.primary)
        p.stair([(9, 11), (13, 10), (18, 10), (23, 12)], p.void)
        p.stair([(10, 15), (14, 14), (18, 14), (22, 16)], p.void)
        p.stair([(11, 19), (15, 18), (18, 18), (21, 20)], p.void)
        p.dot(9, 6, p.highlight)
        return
    if glyph in {"equalizer", "dazn_bars", "sbs_bars", "deezer_columns"}:
        x0 = p.rng.choice([7, 8, 9])
        for index, x in enumerate(range(x0, x0 + 20, 5)):
            height = p.rng.choice([5, 7, 9])
            p.r((x, 25 - height, x + 2, 25), p.secondary if index % 2 else p.primary)
            p.r((x, 25 - height, x + 2, 25 - height + 1), p.highlight)
        if glyph == "dazn_bars":
            p.stepped_box(5, 4, 22, 22, p.shadow, 2)
            p.stepped_box(4, 3, 22, 22, p.primary, 2)
            brand_text(p, "DAZN", 8, 13, p.secondary, scale=1, max_width=14)
        return
    if glyph in {"tidal_wave", "stan_wave", "binge_wave", "max_wave"}:
        cx, cy = 16, 15
        p.stair([(5, 15), (9, 11), (13, 15), (17, 19), (21, 15), (27, 9)], p.shadow)
        p.stair([(4, 14), (8, 10), (12, 14), (16, 18), (20, 14), (26, 8)], p.primary)
        p.stair([(6, 22), (11, 22), (15, 19), (20, 22), (25, 22)], p.secondary)
        p.dot(cx, cy, p.highlight)
        return
    motif_wave(p)
    # A second line keeps generic signal marks distinct while preserving the
    # brand glyph's wave language.
    p.stair([(7, 22), (11, 20), (15, 20), (19, 22), (24, 22)], p.highlight)


def brand_folder(p: SpritePainter, icon: dict, glyph: str):
    motif_folder(p)
    label = brand_initials(icon["name"])
    width = text_width(label, 1)
    brand_text(p, label, 15 - width // 2, 13, p.highlight, scale=1, max_width=14)
    if "wifi" in glyph:
        p.stair([(22, 7), (24, 5), (26, 7)], p.secondary)


def brand_globe(p: SpritePainter, icon: dict, glyph: str):
    motif_globe(p)
    label = brand_initials(icon["name"])
    if glyph == "browser_globe2":
        label = "B"
    width = text_width(label, 1)
    brand_text(p, label, 16 - width // 2, 13, p.highlight, scale=1, max_width=12)


def brand_note(p: SpritePainter, icon: dict, glyph: str):
    motif_note(p)
    if glyph == "qobuz_note":
        p.r((8, 8, 12, 10), p.highlight)
    else:
        label = brand_initials(icon["name"])
        brand_text(p, label[:2], 14, 10, p.highlight, scale=1, max_width=7)


def brand_sports(p: SpritePainter, icon: dict, glyph: str):
    if glyph in {"nba_ball", "nfl_ball", "mlb_homeplate", "tennis_mark"}:
        motif_ball(p)
    elif glyph in {"uefa_star", "redbull_sun", "discovery_sunburst", "peacock_fan"}:
        cx, cy = 16, 15
        for index in range(8):
            if index % 2 == 0:
                p.stair([(cx, cy), (cx + (index - 3) * 3, cy + (index % 3 - 1) * 4)], p.secondary)
        p.dot(cx, cy, p.highlight)
    else:
        motif_trophy(p)
    label = brand_initials(icon["name"])
    width = text_width(label, 1)
    brand_text(p, label, 16 - width // 2, 25, p.highlight, scale=1, max_width=12)


def brand_arrow(p: SpritePainter, icon: dict, glyph: str):
    if "bolt" in glyph or glyph in {"kayo_bolt", "debrid_bolt"}:
        p.poly([(18, 3), (8, 18), (14, 18), (11, 29), (24, 12), (18, 12)], p.shadow)
        p.poly([(17, 2), (7, 17), (13, 17), (10, 28), (23, 11), (17, 11)], p.primary)
        p.r((14, 15, 17, 17), p.highlight)
    else:
        p.r((14, 4, 17, 24), p.secondary)
        p.poly([(8, 19), (15, 27), (23, 19), (20, 19), (16, 23), (11, 19)], p.primary)
        p.r((8, 27, 23, 29), p.mid)


def brand_apple(p: SpritePainter, icon: dict, glyph: str):
    p.poly([(12, 10), (9, 12), (8, 18), (11, 24), (15, 27), (19, 25),
            (23, 25), (25, 19), (23, 13), (19, 10), (16, 12)], p.shadow)
    p.poly([(12, 9), (9, 11), (8, 17), (11, 23), (15, 26), (19, 24),
            (23, 24), (24, 18), (22, 12), (18, 9), (16, 11)], p.primary)
    p.r((20, 12, 23, 14), p.void)  # pixel bite
    p.poly([(16, 8), (17, 4), (21, 3), (20, 7)], p.secondary)
    p.r((13, 28, 19, 29), p.highlight)


def brand_amazon(p: SpritePainter, icon: dict, glyph: str):
    brand_text(p, "A", 12, 5, p.primary, scale=4, max_width=12)
    p.stair([(7, 23), (11, 25), (17, 26), (23, 24), (26, 21)], p.secondary)
    p.poly([(23, 21), (27, 20), (25, 24)], p.highlight)


def brand_acorn(p: SpritePainter, icon: dict, glyph: str):
    cx = p.rng.choice([15, 16, 17])
    p.poly([(cx, 4), (cx + 8, 10), (cx + 6, 22), (cx, 27),
            (cx - 7, 22), (cx - 8, 10)], p.shadow)
    p.poly([(cx, 3), (cx + 7, 9), (cx + 5, 21), (cx, 26),
            (cx - 6, 21), (cx - 7, 9)], p.primary)
    p.r((cx - 7, 8, cx + 7, 12), p.secondary)
    p.stair([(cx - 2, 6), (cx - 4, 3), (cx - 1, 2)], p.highlight)


def brand_netflix(p: SpritePainter, icon: dict, glyph: str):
    p.r((7, 4, 11, 28), p.shadow)
    p.r((20, 4, 24, 28), p.shadow)
    p.stair([(10, 5), (21, 27)], p.primary)
    p.r((7, 3, 11, 27), p.primary)
    p.r((20, 3, 24, 27), p.primary)
    p.stair([(10, 4), (21, 26)], p.secondary)
    p.r((8, 4, 10, 7), p.highlight)


def brand_castle(p: SpritePainter, icon: dict, glyph: str):
    p.r((5, 17, 27, 27), p.shadow)
    p.r((6, 16, 26, 26), p.primary)
    for x, h in ((8, 8), (14, 12), (21, 9)):
        p.r((x, 16 - h // 2, x + 4, 25), p.secondary)
        p.r((x + 1, 14 - h // 2, x + 3, 16 - h // 2), p.highlight)
    p.r((9, 21, 11, 26), p.void)
    p.r((17, 19, 19, 26), p.void)
    p.r((23, 21, 25, 26), p.void)


def brand_mark_badge(p: SpritePainter, icon: dict, glyph: str):
    """Fallback for a named mark: brand initials inside a glyph-specific badge."""
    label_overrides = {
        "a_e_mark": "A&E", "abcnews_mark": "ABC", "amc_a": "AMC",
        "c4_block": "C4", "cnn_mark": "CNN", "espn_e": "E",
        "f1_wing": "F1", "netflix_ribbon": "N", "nasa_mark": "NASA",
        "pbs_mark": "PBS", "tbs_mark": "TBS", "tnt_mark": "TNT",
        "ufc_octagon": "UFC", "uefa_star": "UEFA", "zee5_mark": "Z5",
    }
    label = label_overrides.get(glyph, brand_initials(icon["name"]))
    if glyph in {"c4_block", "ufc_octagon"} or "octagon" in glyph:
        p.stepped_box(4, 5, 23, 21, p.shadow, 3)
        p.stepped_box(3, 4, 23, 21, p.primary, 3)
    elif "circle" in glyph or "halo" in glyph or "ring" in glyph:
        p.poly([(16, 3), (25, 7), (28, 16), (25, 25), (16, 29), (7, 25),
                (4, 16), (7, 7)], p.shadow)
        p.poly([(16, 2), (24, 6), (27, 16), (24, 24), (16, 28), (8, 24),
                (5, 16), (8, 6)], p.primary)
    elif "star" in glyph or "sun" in glyph or "burst" in glyph:
        p.poly([(16, 2), (19, 11), (28, 8), (21, 15), (28, 21), (19, 20),
                (16, 29), (13, 20), (4, 22), (11, 15), (4, 9), (13, 11)], p.primary)
    elif "ribbon" in glyph or "swoosh" in glyph:
        p.stair([(6, 8), (12, 13), (18, 18), (26, 24)], p.primary)
        p.stair([(6, 12), (12, 17), (18, 22), (26, 27)], p.secondary)
    else:
        p.stepped_box(4, 5, 23, 21, p.shadow, 2)
        p.stepped_box(3, 4, 23, 21, p.primary, 2)
        p.r((6, 7, 23, 22), p.void)
    scale = 1
    if text_width(label, 2) <= 17:
        scale = 2
    width = text_width(label, scale)
    brand_text(p, label, max(2, 16 - width // 2), 13 if scale == 1 else 11,
               p.secondary, scale=scale, max_width=26)
    p.glint(7, 7)


BRAND_PALETTES = {
    "mubi": ("#5B35B5", "#F5F1FF"),
    "sbs": ("#F0A500", "#211B00"),
    "pluto tv": ("#7D4DFF", "#00E5FF"),
    "al jazeera": ("#E28B20", "#FFF3CE"),
    "france 24": ("#00AFF0", "#FFFFFF"),
    "sky news": ("#9C0000", "#FFFFFF"),
    "foxtel": ("#EE5100", "#FFFFFF"),
    "kayo": ("#00E676", "#FFE45E"),
    "rakuten tv": ("#BF0000", "#FFFFFF"),
    "vimeo": ("#1AB7EA", "#FFFFFF"),
    "mgm+": ("#D4AF37", "#211A00"),
    "netflix": ("#E50914", "#FFB3B9"),
    "discovery": ("#003B73", "#6ED6FF"),
    "disney+": ("#113CCF", "#FFFFFF"),
    "peacock": ("#00A651", "#F5A623"),
    "paramount+": ("#0064FF", "#FFFFFF"),
    "crunchyroll": ("#F47521", "#FFF3D6"),
    "youtube": ("#FF0000", "#FFFFFF"),
    "nfl": ("#013369", "#FFFFFF"),
    "mlb": ("#002D72", "#E31837"),
    "nba": ("#1D428A", "#C8102E"),
    "cnn": ("#CC0000", "#FFFFFF"),
    "espn": ("#CC0000", "#FFFFFF"),
    "red bull tv": ("#DB0A40", "#F7D117"),
    "mullvad vpn": ("#FFD500", "#171717"),
    "wireguard": ("#88171A", "#F3D36B"),
    "britbox": ("#C41A3B", "#19B5D1"),
    "hulu": ("#1CE783", "#FFFFFF"),
    "duckduckgo": ("#DE5833", "#FFCB05"),
    "tubi": ("#FFD400", "#171717"),
    "sling tv": ("#0084FF", "#FF6D01"),
    "max": ("#7B5CFF", "#FFFFFF"),
    "spotify": ("#1ED760", "#0B0D22"),
    "tidal": ("#00E5FF", "#FFFFFF"),
}


def use_brand_palette(p: SpritePainter, name: str) -> None:
    colors = BRAND_PALETTES.get(name.casefold())
    if colors is None:
        return
    p.primary, p.secondary = colors
    p.palette["primary"] = p.primary
    p.palette["secondary"] = p.secondary
    base = color_tuple(p.primary)
    dark = color_tuple(VOID)
    p.shadow = rgb_hex(tuple(round(channel * 0.28 + shade * 0.72)
                        for channel, shade in zip(base, dark)))
    p.mid = rgb_hex(tuple(round(channel * 0.52 + shade * 0.48)
                     for channel, shade in zip(base, dark)))
    p.palette["shadow"] = p.shadow
    p.palette["mid"] = p.mid


# The repository's logo-fidelity audit identifies these marks as the
# recognisable brands where a generic letter tile is not accurate enough. These
# are independent 32px reconstructions from the documented visual cues; they
# do not rasterise or import the monoline SVG library.
def research_mubi(p: SpritePainter, icon: dict):
    for x, y in ((8, 7), (13, 7), (18, 7), (8, 12), (13, 12), (18, 12), (13, 17)):
        p.r((x, y, x + 2, y + 2), p.shadow)
        p.r((x, y - 1, x + 2, y + 1), p.primary)
        p.dot(x, y - 1, p.highlight)
    brand_text(p, "MUBI", 8, 24, p.secondary, scale=1, max_width=16)


def research_sbs(p: SpritePainter, icon: dict):
    p.poly([(16, 3), (25, 7), (28, 16), (25, 25), (16, 29), (7, 25),
            (4, 16), (7, 7)], p.shadow)
    p.poly([(16, 2), (24, 6), (27, 16), (24, 24), (16, 28), (8, 24),
            (5, 16), (8, 6)], p.primary)
    p.r((8, 9, 24, 23), p.void)
    # Five curved Mercator-style globe splices.
    for x, bend in ((9, -3), (12, -1), (15, 0), (18, 1), (21, 3)):
        p.stair([(x, 9), (x + bend, 13), (x + bend, 19), (x, 23)], p.secondary)
    p.r((8, 15, 24, 16), p.mid)
    p.dot(8, 8, p.highlight)


def research_pluto(p: SpritePainter, icon: dict):
    p.stair([(4, 22), (9, 17), (15, 13), (22, 10), (28, 8)], p.secondary)
    p.poly([(16, 5), (23, 8), (26, 15), (23, 23), (16, 26), (9, 23),
            (6, 15), (9, 8)], p.shadow)
    p.poly([(16, 4), (22, 7), (25, 15), (22, 22), (16, 25), (10, 22),
            (7, 15), (10, 7)], p.primary)
    p.r((12, 12, 20, 19), p.void)
    brand_text(p, "TV", 13, 13, p.highlight, scale=1, max_width=7)
    p.stair([(5, 25), (12, 28), (20, 28), (27, 24)], p.secondary)


def research_al_jazeera(p: SpritePainter, icon: dict):
    p.stepped_box(4, 3, 24, 25, p.shadow, 2)
    p.stepped_box(3, 2, 24, 25, p.primary, 2)
    p.r((7, 6, 23, 23), p.void)
    # A pointed calligraphic/flame drop, not an A tile.
    p.poly([(16, 6), (20, 12), (19, 18), (16, 23), (12, 19), (13, 14)], p.highlight)
    p.stair([(15, 9), (14, 14), (16, 17), (14, 21)], p.secondary)
    p.r((10, 22, 21, 23), p.highlight)


def research_france24(p: SpritePainter, icon: dict):
    p.stepped_box(4, 3, 24, 25, p.shadow, 2)
    p.stepped_box(3, 2, 24, 25, p.primary, 2)
    brand_text(p, "24", 8, 10, p.highlight, scale=3, max_width=16)
    p.r((8, 25, 23, 26), p.secondary)


def research_sky(p: SpritePainter, icon: dict):
    brand_text(p, "SKY", 5, 9, p.highlight, scale=2, max_width=22)
    p.stair([(5, 24), (10, 26), (17, 26), (24, 23), (27, 18)], p.secondary)
    p.dot(26, 18, p.highlight)


def research_foxtel(p: SpritePainter, icon: dict):
    p.poly([(8, 13), (8, 8), (13, 11), (16, 7), (19, 11), (24, 8),
            (24, 14), (22, 22), (16, 26), (10, 22)], p.shadow)
    p.poly([(9, 12), (9, 7), (14, 10), (16, 6), (19, 10), (23, 7),
            (23, 13), (21, 21), (16, 24), (11, 21)], p.primary)
    p.dot(13, 14, p.highlight)
    p.dot(19, 14, p.highlight)
    p.r((14, 18, 18, 19), p.void)
    brand_text(p, "FOX", 10, 26, p.secondary, scale=1, max_width=12)


def research_kayo(p: SpritePainter, icon: dict):
    p.stair([(4, 19), (8, 14), (12, 17), (16, 21), (20, 17), (27, 11)], p.primary)
    p.stair([(5, 24), (10, 21), (14, 22), (19, 25), (25, 21)], p.secondary)
    p.poly([(25, 4), (26, 9), (30, 10), (27, 12), (28, 16), (25, 13),
            (22, 16), (23, 12), (20, 10), (24, 9)], p.highlight)
    brand_text(p, "K", 7, 6, p.highlight, scale=2, max_width=6)


def research_rakuten(p: SpritePainter, icon: dict):
    brand_text(p, "RAKU", 4, 7, p.highlight, scale=1, max_width=20)
    brand_text(p, "TV", 13, 14, p.secondary, scale=1, max_width=7)
    p.poly([(7, 23), (24, 23), (27, 20), (24, 26), (7, 26)], p.primary)
    p.dot(7, 23, p.highlight)


def research_vimeo(p: SpritePainter, icon: dict):
    brand_text(p, "VIMEO", 4, 11, p.highlight, scale=1, max_width=23)
    p.stair([(6, 22), (11, 24), (17, 24), (23, 22), (27, 18)], p.secondary)


def research_mgm(p: SpritePainter, icon: dict):
    p.poly([(16, 3), (24, 7), (28, 15), (24, 24), (16, 29), (8, 24),
            (4, 15), (8, 7)], p.shadow)
    p.poly([(16, 4), (23, 8), (26, 15), (23, 23), (16, 27), (9, 23),
            (6, 15), (9, 8)], p.primary)
    p.r((10, 10, 22, 21), p.void)
    p.r((12, 12, 20, 18), p.secondary)
    p.dot(14, 14, p.highlight)
    p.dot(18, 14, p.highlight)
    p.r((14, 17, 18, 18), p.outline)
    for x, y in ((8, 7), (24, 7), (6, 15), (26, 15), (8, 24), (24, 24)):
        p.dot(x, y, p.highlight)


def research_discovery(p: SpritePainter, icon: dict):
    brand_text(p, "DISC", 3, 12, p.highlight, scale=1, max_width=17)
    cx, cy = 25, 15
    p.poly([(cx, cy - 7), (cx + 5, cy - 3), (cx + 7, cy), (cx + 3, cy + 5),
            (cx, cy + 7), (cx - 5, cy + 3), (cx - 7, cy), (cx - 3, cy - 5)], p.primary)
    p.r((cx - 1, cy - 1, cx + 1, cy + 1), p.highlight)
    p.stair([(cx - 1, cy), (cx + 5, cy - 4)], p.secondary)


def research_disney(p: SpritePainter, icon: dict):
    # Pixel D plus arc/plus, rather than a generic star or castle.
    p.r((6, 7, 9, 25), p.primary)
    p.r((8, 6, 17, 9), p.primary)
    p.r((8, 23, 17, 26), p.primary)
    p.stair([(17, 8), (21, 11), (22, 16), (20, 21), (16, 24)], p.primary)
    p.stair([(7, 5), (12, 3), (18, 3), (24, 6), (28, 11)], p.secondary)
    p.r((24, 13, 26, 22), p.highlight)
    p.r((21, 16, 29, 18), p.highlight)


def research_peacock(p: SpritePainter, icon: dict):
    feathers = [(7, 14, p.secondary), (10, 9, p.primary), (14, 6, p.highlight),
                (18, 6, p.secondary), (22, 9, p.primary), (25, 14, p.highlight)]
    for x, top, color in feathers:
        p.poly([(16, 24), (x - 2, top + 4), (x, top), (x + 3, top + 3),
                (x + 3, top + 8)], color)
        p.r((x, top + 3, x + 1, top + 5), p.highlight)
    p.poly([(13, 21), (16, 17), (19, 21), (18, 27), (14, 27)], p.primary)
    p.dot(16, 21, p.highlight)


def research_paramount(p: SpritePainter, icon: dict):
    p.stair([(5, 23), (10, 19), (13, 14), (16, 7), (19, 14), (22, 19), (27, 23)], p.primary)
    p.poly([(16, 6), (24, 23), (8, 23)], p.secondary)
    p.stair([(5, 23), (8, 18), (12, 14), (16, 12), (20, 14), (24, 18), (27, 23)], p.highlight)
    for x, y in ((8, 8), (12, 5), (16, 3), (20, 5), (24, 8), (6, 14), (26, 14)):
        p.dot(x, y, p.highlight)


def research_crunchyroll(p: SpritePainter, icon: dict):
    # Orange eye/sushi-roll: the inner crescent is intentionally offset.
    p.poly([(16, 3), (24, 7), (28, 15), (24, 24), (16, 28), (8, 24),
            (4, 15), (8, 7)], p.shadow)
    p.poly([(16, 2), (23, 6), (27, 15), (23, 23), (16, 27), (9, 23),
            (5, 15), (9, 6)], p.primary)
    p.poly([(11, 9), (19, 8), (23, 12), (24, 17), (21, 21), (15, 23),
            (10, 19), (9, 14)], p.highlight)
    p.poly([(15, 10), (20, 10), (22, 13), (21, 17), (18, 19), (14, 20),
            (12, 17), (13, 13)], p.primary)
    p.dot(20, 11, p.secondary)


def research_youtube(p: SpritePainter, icon: dict):
    p.stepped_box(4, 7, 24, 18, p.shadow, 3)
    p.stepped_box(3, 6, 24, 18, p.primary, 3)
    p.poly([(13, 11), (13, 21), (22, 16)], p.highlight)
    p.dot(13, 11, p.highlight)


def research_nfl(p: SpritePainter, icon: dict):
    p.poly([(16, 3), (24, 7), (25, 20), (20, 26), (16, 29), (12, 26),
            (7, 20), (8, 7)], p.shadow)
    p.poly([(16, 2), (23, 6), (24, 19), (20, 25), (16, 28), (12, 25),
            (8, 19), (9, 6)], p.primary)
    p.r((10, 10, 22, 11), p.highlight)
    p.r((10, 14, 22, 15), p.highlight)
    p.r((11, 18, 21, 19), p.highlight)
    p.r((14, 11, 18, 20), p.secondary)
    p.r((14, 13, 18, 14), p.highlight)


def research_mlb(p: SpritePainter, icon: dict):
    # MLB's horizontal red/blue batter badge, reduced to a 32px capsule.
    p.stepped_box(3, 8, 26, 16, p.shadow, 3)
    p.stepped_box(2, 7, 26, 16, p.primary, 3)
    p.r((3, 8, 15, 22), p.secondary)
    p.r((16, 8, 25, 22), p.primary)
    p.dot(16, 11, p.highlight)
    p.r((15, 11, 17, 20), p.highlight)
    p.r((12, 13, 20, 15), p.highlight)
    p.stair([(15, 19), (11, 23)], p.highlight)
    p.stair([(17, 19), (21, 23)], p.highlight)
    p.stair([(18, 10), (23, 8)], p.highlight)


def research_nba(p: SpritePainter, icon: dict):
    # NBA's red/blue vertical logo plate with the white player silhouette.
    p.stepped_box(7, 3, 18, 25, p.shadow, 3)
    p.stepped_box(6, 2, 18, 25, p.primary, 3)
    p.r((7, 3, 15, 26), p.secondary)
    p.r((16, 3, 23, 26), p.primary)
    p.dot(16, 8, p.highlight)
    p.r((15, 10, 17, 19), p.highlight)
    p.r((12, 12, 20, 14), p.highlight)
    p.stair([(15, 18), (11, 24)], p.highlight)
    p.stair([(17, 18), (21, 24)], p.highlight)


def research_cnn(p: SpritePainter, icon: dict):
    p.stepped_box(3, 7, 25, 18, p.shadow, 2)
    p.stepped_box(2, 6, 25, 18, p.primary, 2)
    brand_text(p, "CNN", 6, 12, p.highlight, scale=2, max_width=19)
    p.stair([(7, 22), (12, 20), (17, 22), (22, 20)], p.secondary)


def research_espn(p: SpritePainter, icon: dict):
    p.stepped_box(4, 7, 23, 18, p.shadow, 2)
    p.stepped_box(3, 6, 23, 18, p.primary, 2)
    p.r((8, 11, 11, 21), p.highlight)
    p.r((9, 10, 22, 13), p.highlight)
    p.r((9, 15, 19, 17), p.highlight)
    p.r((9, 20, 22, 22), p.highlight)
    p.stair([(7, 22), (23, 10)], p.secondary)


def research_redbull(p: SpritePainter, icon: dict):
    p.poly([(16, 4), (23, 7), (27, 14), (26, 21), (21, 26), (16, 28),
            (11, 26), (6, 21), (5, 14), (9, 7)], p.secondary)
    p.poly([(16, 5), (22, 8), (25, 14), (24, 20), (20, 24), (16, 26),
            (12, 24), (8, 20), (7, 14), (10, 8)], p.highlight)
    # Two charging silhouettes around the sun.
    for side in (-1, 1):
        p.poly([(16 + side * 2, 15), (16 + side * 7, 12), (16 + side * 10, 14),
                (16 + side * 7, 16), (16 + side * 4, 18), (16 + side, 18)], p.primary)
        p.stair([(16 + side * 6, 12), (16 + side * 8, 10), (16 + side * 10, 11)], p.primary)
    p.dot(16, 15, p.highlight)


def research_mullvad(p: SpritePainter, icon: dict):
    # Mullvad's mark is a yellow duck head, not a shield.
    p.poly([(11, 7), (15, 4), (20, 6), (24, 11), (24, 17), (21, 22),
            (16, 25), (11, 22), (8, 18), (8, 12)], p.shadow)
    p.poly([(11, 6), (15, 3), (20, 5), (23, 10), (23, 16), (20, 21),
            (16, 24), (12, 21), (9, 17), (9, 11)], p.primary)
    p.poly([(18, 13), (27, 14), (23, 18), (18, 17)], p.secondary)
    p.dot(13, 12, p.void)
    p.dot(19, 11, p.void)
    p.r((12, 7, 15, 8), p.highlight)
    p.stair([(10, 8), (8, 6), (10, 4)], p.highlight)


def research_wireguard(p: SpritePainter, icon: dict):
    p.stair([(6, 9), (10, 6), (15, 7), (18, 11), (17, 16), (13, 20),
             (10, 24), (14, 27), (20, 26), (25, 22)], p.primary)
    p.stair([(6, 14), (10, 11), (14, 12), (16, 15), (15, 19), (12, 22)], p.secondary)
    p.stair([(18, 6), (22, 9), (25, 13), (24, 17), (21, 20)], p.highlight)
    p.dot(6, 9, p.highlight)


def research_britbox(p: SpritePainter, icon: dict):
    p.stepped_box(4, 5, 24, 22, p.shadow, 2)
    p.stepped_box(3, 4, 24, 22, p.primary, 2)
    p.r((7, 8, 23, 22), p.void)
    p.r((14, 8, 16, 22), p.secondary)
    p.r((7, 14, 23, 16), p.secondary)
    brand_text(p, "B", 9, 11, p.highlight, scale=1, max_width=4)


def research_hulu(p: SpritePainter, icon: dict):
    brand_text(p, "HULU", 4, 11, p.primary, scale=2, max_width=24)
    p.stair([(5, 22), (10, 24), (17, 24), (24, 22)], p.secondary)


def research_duckduckgo(p: SpritePainter, icon: dict):
    p.stepped_box(4, 4, 24, 24, p.shadow, 3)
    p.stepped_box(3, 3, 24, 24, p.secondary, 3)
    p.poly([(16, 7), (22, 10), (24, 17), (21, 23), (16, 26), (10, 22),
            (8, 16), (10, 10)], p.primary)
    p.poly([(17, 16), (26, 17), (23, 21), (17, 20)], p.highlight)
    p.dot(13, 14, p.void)
    p.dot(14, 14, p.highlight)
    p.stair([(11, 10), (14, 7), (18, 7)], p.highlight)


def research_tubi(p: SpritePainter, icon: dict):
    brand_text(p, "TUBI", 4, 10, p.highlight, scale=2, max_width=24)
    p.r((7, 22, 25, 24), p.primary)
    p.dot(7, 23, p.secondary)


def research_sling(p: SpritePainter, icon: dict):
    p.stair([(5, 10), (10, 14), (15, 18), (21, 21), (27, 20)], p.secondary)
    p.stair([(5, 15), (10, 19), (15, 23), (21, 26), (27, 23)], p.primary)
    p.stair([(7, 7), (12, 10), (17, 13), (22, 14), (26, 12)], p.highlight)


def research_tidal(p: SpritePainter, icon: dict):
    # TIDAL's compact black 3+1 diamond wave translated to hard pixels.
    p.poly([(16, 3), (27, 14), (27, 18), (16, 29), (5, 18), (5, 14)], p.shadow)
    p.poly([(16, 2), (26, 14), (26, 17), (16, 28), (6, 17), (6, 14)], p.primary)
    p.poly([(8, 14), (11, 9), (14, 14), (17, 9), (20, 14), (23, 9),
            (25, 14), (20, 14), (17, 19), (14, 14), (11, 19)], p.highlight)
    p.poly([(13, 18), (16, 23), (19, 18), (16, 20)], p.secondary)


def research_max(p: SpritePainter, icon: dict):
    brand_text(p, "MAX", 5, 9, p.highlight, scale=2, max_width=22)
    p.r((8, 23, 24, 25), p.primary)
    p.r((14, 22, 18, 26), p.secondary)


def research_netflix(p: SpritePainter, icon: dict):
    brand_netflix(p, icon, "netflix_ribbon")


RESEARCHED_BY_NAME = {
    "mubi": research_mubi,
    "sbs": research_sbs,
    "pluto tv": research_pluto,
    "al jazeera": research_al_jazeera,
    "france 24": research_france24,
    "sky news": research_sky,
    "foxtel": research_foxtel,
    "kayo": research_kayo,
    "rakuten tv": research_rakuten,
    "vimeo": research_vimeo,
    "mgm+": research_mgm,
    "netflix": research_netflix,
    "discovery": research_discovery,
    "disney+": research_disney,
    "peacock": research_peacock,
    "paramount+": research_paramount,
    "crunchyroll": research_crunchyroll,
    "youtube": research_youtube,
    "nfl": research_nfl,
    "mlb": research_mlb,
    "nba": research_nba,
    "cnn": research_cnn,
    "espn": research_espn,
    "red bull tv": research_redbull,
    "mullvad vpn": research_mullvad,
    "wireguard": research_wireguard,
    "britbox": research_britbox,
    "hulu": research_hulu,
    "duckduckgo": research_duckduckgo,
    "tubi": research_tubi,
    "sling tv": research_sling,
    "max": research_max,
    "tidal": research_tidal,
}


def researched_brand(p: SpritePainter, icon: dict) -> bool:
    key = icon["name"].casefold()
    renderer = RESEARCHED_BY_NAME.get(key)
    if renderer is None:
        return False
    use_brand_palette(p, key)
    renderer(p, icon)
    return True


def brand_symbol(p: SpritePainter, icon: dict, ordinal: int) -> None:
    """Select an independent pixel construction from the catalog brand cue."""
    glyph = (icon.get("glyph") or "").lower()
    if researched_brand(p, icon):
        return
    if glyph.startswith("tile_"):
        brand_tile(p, icon, glyph)
    elif glyph == "plus_star":
        brand_castle(p, icon, glyph)
    elif glyph in {"iptv_player", "monitor_wave", "tivimate_grid", "tv_stack", "google_tv"}:
        brand_play(p, icon, glyph)
    elif glyph in {"play_round", "play_rect", "play_hex", "stremio_square",
                   "play_store_tri", "iplayer_play", "smarttube_play", "yt_play",
                   "yt_kids", "yt_music", "nova_play", "janky_play", "bag_play"}:
        brand_play(p, icon, glyph)
    elif glyph in {"eye", "cbs_eye", "crunchyroll_eye",
                   "curiosity_eye", "nvidia_eye", "showmax_eye"}:
        brand_eye(p, icon, glyph)
    elif "shield" in glyph or glyph in {"adguard_shield", "emby_shield", "shield_key"}:
        brand_shield(p, icon, glyph)
    elif glyph in {"folder", "folder_fx", "folder_rs", "folder_solid", "folder_wifi",
                   "nas_stack", "nas_image", "nas_play"}:
        brand_folder(p, icon, glyph)
    elif "globe" in glyph:
        brand_globe(p, icon, glyph)
    elif "note" in glyph or glyph in {"music_note", "qobuz_note"}:
        brand_note(p, icon, glyph)
    elif any(token in glyph for token in ("wave", "arcs", "equalizer", "bars")):
        brand_wave(p, icon, glyph)
    elif any(token in glyph for token in ("ball", "star", "sun", "sports", "tennis")):
        brand_sports(p, icon, glyph)
    elif "arrow" in glyph or "bolt" in glyph or glyph in {"download_arrow", "send_arrow"}:
        brand_arrow(p, icon, glyph)
    elif glyph == "amazon_smile":
        brand_amazon(p, icon, glyph)
    elif glyph == "apple_tv":
        brand_apple(p, icon, glyph)
    elif glyph == "acorn_mark":
        brand_acorn(p, icon, glyph)
    elif glyph == "netflix_ribbon":
        brand_netflix(p, icon, glyph)
    elif glyph in {"satellite", "radar_dish", "dish_mark"}:
        motif_antenna(p)
        brand_text(p, brand_initials(icon["name"]), 10, 7, p.highlight, scale=1, max_width=13)
    elif glyph in {"gamepad", "gamelauncher_mark", "pacman_mark"}:
        motif_game(p)
    elif glyph in {"cloud_box", "soundcloud_cloud"}:
        motif_cloud(p)
        brand_text(p, brand_initials(icon["name"]), 10, 13, p.highlight, scale=1, max_width=12)
    elif glyph in {"camera", "instagram_camera"}:
        motif_camera(p)
    elif glyph == "podcast_mic":
        p.stepped_box(11, 5, 10, 15, p.shadow, 4)
        p.stepped_box(10, 4, 10, 15, p.primary, 4)
        p.r((14, 8, 17, 17), p.void)
        p.stair([(8, 15), (8, 22), (16, 27), (24, 22), (24, 15)], p.secondary)
    else:
        brand_mark_badge(p, icon, glyph)


def clean_label(name: str) -> str:
    label = "".join(char if char.isalnum() or char in "&+- ." else " " for char in name)
    return " ".join(label.upper().split())


def render_banner(sprite, palette: dict[str, str], icon: dict, ordinal: int):
    from PIL import Image, ImageDraw

    seed = hashlib.sha1(f"banner:{icon['drawable']}:{ordinal}".encode()).digest()
    layout = seed[0] % 3
    canvas = Image.new("RGBA", (BANNER_GRID_W, BANNER_GRID_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    mark = sprite.resize((48, 48), Image.Resampling.NEAREST)
    label = clean_label(icon["name"])
    category = clean_label(icon.get("category") or "APP")
    scale = 2 if len(label) <= 12 else 1
    label_width = text_width(label, scale)

    if layout == 0:
        canvas.alpha_composite(mark, (8, 20))
        text_x = 62
        draw_pixel_text(draw, label[:18], text_x, 31, palette["highlight"], scale=scale, max_width=91)
        draw_pixel_text(draw, category[:16], text_x, 48, palette["secondary"], scale=1, max_width=91)
        for x in range(62, 153, 6):
            draw.rectangle((x, 60, x + (seed[x % len(seed)] % 3), 61), fill=palette["primary"])
    elif layout == 1:
        canvas.alpha_composite(mark, (104, 20))
        max_width = 90
        text_x = max(5, 96 - min(label_width, max_width))
        draw_pixel_text(draw, label[:18], text_x, 31, palette["highlight"], scale=scale, max_width=max_width)
        draw_pixel_text(draw, category[:16], 8, 48, palette["secondary"], scale=1, max_width=max_width)
        for x in range(8, 94, 6):
            draw.rectangle((x, 60, x + (seed[(x + 3) % len(seed)] % 3), 61), fill=palette["primary"])
    else:
        canvas.alpha_composite(mark, (56, 2))
        x = max(3, (BANNER_GRID_W - min(label_width, 154)) // 2)
        draw_pixel_text(draw, label[:20], x, 56, palette["highlight"], scale=scale, max_width=154)
        cat_width = text_width(category[:18], 1)
        draw_pixel_text(draw, category[:18], max(3, (BANNER_GRID_W - cat_width) // 2), 65,
                        palette["secondary"], scale=1, max_width=154)
        for xx in range(8, 153, 7):
            draw.rectangle((xx, 78, xx + (seed[xx % len(seed)] % 2), 79), fill=palette["primary"])

    # A few isolated status pixels make banners feel like signs rather than a
    # repeated rail/wordmark lockup.
    for index in range(3):
        x = 3 + ((seed[index + 4] + index * 17) % 154)
        y = 4 + ((seed[index + 8] + index * 11) % 82)
        draw.rectangle((x, y, x + 1, y + 1), fill=palette["secondary"])
    return canvas.resize((BANNER_W, BANNER_H), Image.Resampling.NEAREST)


def write(path: Path, data: str | bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, bytes):
        path.write_bytes(data)
    else:
        path.write_text(data, encoding="utf-8")


def copy_wallpaper_assets() -> tuple[int, int]:
    """Bundle Pixel Neon wallpaper metadata without bundling the 4K sources.

    Pixel Neon gets an instant, offline browser with its own 8-bit wallpaper
    set, but its APK should not contain every full-resolution wallpaper. The
    repository's Pixel Neon manifest and small JPEG thumbs are copied into the
    companion module; WallpaperDownloader continues to fetch/cache the PNG
    source only after a user opens a preview or starts an export.
    """
    if not WALLPAPER_MANIFEST_SOURCE.exists():
        raise SystemExit(f"missing wallpaper manifest: {WALLPAPER_MANIFEST_SOURCE}")
    if not WALLPAPER_THUMBS_SOURCE.exists():
        raise SystemExit(f"missing wallpaper thumbs: {WALLPAPER_THUMBS_SOURCE}")

    manifest = json.loads(WALLPAPER_MANIFEST_SOURCE.read_text(encoding="utf-8"))
    entries = manifest.get("wallpapers", [])
    declared = manifest.get("count")
    if declared != len(entries):
        raise SystemExit(
            f"wallpaper manifest count {declared!r} != {len(entries)} entries"
        )
    expected_thumbs = {Path(entry["thumb"]).name for entry in entries}
    available_thumbs = {path.name for path in WALLPAPER_THUMBS_SOURCE.glob("*.jpg")}
    missing = sorted(expected_thumbs - available_thumbs)
    if missing:
        raise SystemExit("missing wallpaper thumbs: " + ", ".join(missing))

    WALLPAPER_MANIFEST_OUT.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(WALLPAPER_MANIFEST_SOURCE, WALLPAPER_MANIFEST_OUT)
    WALLPAPER_THUMBS_OUT.mkdir(parents=True, exist_ok=True)
    for old in WALLPAPER_THUMBS_OUT.glob("*.jpg"):
        if old.name not in expected_thumbs:
            old.unlink()
    for name in sorted(expected_thumbs):
        shutil.copy2(WALLPAPER_THUMBS_SOURCE / name, WALLPAPER_THUMBS_OUT / name)
    return len(entries), len(expected_thumbs)


def expand(component: str) -> list[str]:
    package, _, activity = component.partition("/")
    if not activity:
        return [component]
    if activity.startswith("."):
        return [f"{package}/{package}{activity}", component]
    if activity.startswith(package + "."):
        return [component, f"{package}/{activity[len(package):]}"]
    return [component]


def appfilter(icons: list[dict]) -> tuple[str, int, int]:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/build_pixel_neon.py. Do not edit by hand. -->',
        '<resources>',
        '    <!-- The pack maps its own launcher tile as well as catalog apps. -->',
        '    <item component="ComponentInfo{tv.corebuilds.pixelneon/tv.corebuilds.pixelneon.MainActivity}" drawable="corebuilds_banner"/>',
    ]
    source_components = sum(len(i["components"]) for i in icons)
    seen: set[str] = set()
    emitted = 1
    for icon in icons:
        lines.append(f'    <!-- {esc(icon["name"])} -->')
        for component in icon["components"]:
            for variant in expand(component):
                if variant in seen:
                    continue
                seen.add(variant)
                lines.append(
                    f'    <item component="ComponentInfo{{{esc(variant)}}}" '
                    f'drawable="{icon["drawable"]}_banner"/>')
                emitted += 1
    lines.append('</resources>')
    return "\n".join(lines) + "\n", source_components, emitted


def drawable_xml(icons: list[dict]) -> str:
    labels = {
        "STREAM": "Streaming", "MEDIA": "Media centres", "VOD": "On demand",
        "LIVE": "Live TV", "PLAYER": "Players", "VIDEO": "Video",
        "MUSIC": "Music", "SPORT": "Sport", "GAMING": "Gaming",
        "DEBRID": "Debrid", "FILES": "Files", "TOOL": "Tools",
        "STORE": "Stores", "LAUNCHER": "Launchers", "VPN": "VPN",
        "BROWSER": "Browsers", "REMOTE": "Remote", "SYSTEM": "System",
        "TRACK": "Tracking", "CORE": "Core Builds", "APP": "Apps",
    }
    preferred = ["CORE", "STREAM", "MEDIA", "VOD", "LIVE", "PLAYER", "VIDEO",
                 "MUSIC", "SPORT", "GAMING", "DEBRID", "FILES", "TOOL",
                 "STORE", "LAUNCHER", "VPN", "BROWSER", "REMOTE", "SYSTEM",
                 "TRACK", "APP"]
    by_category: dict[str, list[dict]] = {}
    for icon in icons:
        by_category.setdefault(icon.get("category") or "APP", []).append(icon)
    categories = [c for c in preferred if c in by_category]
    categories += [c for c in by_category if c not in categories]
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/build_pixel_neon.py. Do not edit by hand. -->',
        '<resources>',
    ]
    for prefix in ("Banner", "Square"):
        suffix = "_banner" if prefix == "Banner" else ""
        for category in categories:
            lines.append(f'    <category title="{prefix} · {esc(labels.get(category, category.title()))}" />')
            for icon in by_category[category]:
                lines.append(f'    <item drawable="{icon["drawable"]}{suffix}" />')
    lines.append('</resources>')
    return "\n".join(lines) + "\n"


def values_xml(icons: list[dict]) -> str:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/build_pixel_neon.py. Do not edit by hand. -->',
        '<resources>',
        '    <string-array name="icon_pack">',
    ]
    lines += [f'        <item>{esc(i["drawable"])}</item>' for i in icons]
    lines += ['    </string-array>', '    <string-array name="icon_names">']
    lines += [f'        <item>{esc(i["name"])}</item>' for i in icons]
    lines += ['    </string-array>', '    <string-array name="icon_categories">']
    lines += [f'        <item>{esc(i.get("category") or "APP")}</item>' for i in icons]
    lines += ['    </string-array>', f'    <integer name="icon_count">{len(icons)}</integer>', '</resources>']
    return "\n".join(lines) + "\n"


def docs_list(icons: list[dict], source_components: int) -> str:
    lines = [
        "# Core Builds Pixel Neon · supported applications", "",
        f"`{len(icons)}` individually generated pixel sprites · `{source_components}` catalog components · pack v0.1.0", "",
        "This is the alternate 8-bit neon treatment of the Core Builds catalog. "
        "Each row uses its semantic brand glyph cue, then gets a hash-seeded pixel recipe; "
        "this pack does not reuse the monoline SVG geometry or banner lockups.", "",
        "| App | Drawable | Neon source accent | Components |", "| --- | --- | --- | --- |",
    ]
    for icon in icons:
        comps = "<br>".join(f"`{c}`" for c in icon["components"])
        lines.append(f'| {icon["name"]} | `{icon["drawable"]}` | `{icon["color"]}` | {comps} |')
    return "\n".join(lines) + "\n"


def preview(icons: list[dict], rendered: dict[str, tuple], source_components: int):
    """Make a compact review sheet with hard-edged sample tiles."""
    from PIL import Image, ImageDraw, ImageFont

    featured = [icon for icon in icons if icon["name"].casefold() in RESEARCHED_BY_NAME]
    remainder = [icon for icon in icons if icon not in featured]
    sample = (featured + remainder)[:48]
    cols, cell, top = 8, 150, 112
    rows = (len(sample) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, top + rows * cell), color_tuple(VOID) + (255,))
    draw = ImageDraw.Draw(sheet)
    try:
        title_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf", 28)
        sub_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 14)
        label_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 12)
    except OSError:
        title_font = sub_font = label_font = ImageFont.load_default()
    draw.text((24, 18), "CORE BUILDS / PIXEL NEON", fill=HIGHLIGHT, font=title_font)
    draw.text((24, 57), f"{len(icons)} sprites · {source_components} components · {len(RESEARCHED_BY_NAME)} researched brand marks · 32px grid", fill="#9AA8C7", font=sub_font)
    draw.text((24, 78), "brand-aware marks · hard pixels · transparent app art", fill="#00E5FF", font=sub_font)
    for n, icon in enumerate(sample):
        x, y = (n % cols) * cell, top + (n // cols) * cell
        draw.rectangle((x + 8, y + 5, x + cell - 8, y + cell - 28),
                       fill="#10142A", outline="#252A4D", width=2)
        # A small category-colour corner marker keeps the sample sheet from
        # looking like a row of identical rounded cards.
        palette = rendered[icon["drawable"]][2]
        draw.rectangle((x + 9, y + 6, x + 13, y + 10), fill=palette["secondary"])
        mark = rendered[icon["drawable"]][0].resize((92, 92), Image.Resampling.NEAREST)
        sheet.alpha_composite(mark, (x + (cell - 92) // 2, y + 10))
        label = icon["name"][:18]
        bbox = draw.textbbox((0, 0), label, font=label_font)
        draw.text((x + (cell - (bbox[2] - bbox[0])) / 2, y + cell - 20), label,
                  fill="#9AA8C7", font=label_font)
    return sheet.convert("RGB")


def brand_assets(core_final, core_small) -> None:
    from PIL import Image, ImageDraw

    res = OUT / "app" / "src" / "main" / "res"
    cabinet = Image.new("RGBA", (512, 512), color_tuple(VOID) + (255,))
    cabinet.alpha_composite(core_final)
    drawer = ImageDraw.Draw(cabinet)
    for y in range(8, 512, 16):
        drawer.rectangle((0, y, 511, y + 1), fill=(0, 229, 255, 12))
    for folder, size in (("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144)):
        write(res / folder / "ic_launcher.png", _png_bytes(cabinet.resize((size, size), Image.Resampling.LANCZOS)))

    fg = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    mark = core_final.resize((318, 318), Image.Resampling.NEAREST)
    fg.alpha_composite(mark, ((512 - 318) // 2, (512 - 318) // 2))
    for folder, size in (("mipmap-xhdpi", 216), ("mipmap-xxhdpi", 324)):
        write(res / folder / "ic_launcher_foreground.png", _png_bytes(fg.resize((size, size), Image.Resampling.NEAREST)))

    adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/cb_night" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
'''
    write(res / "mipmap-anydpi-v26" / "ic_launcher.xml", adaptive)
    write(res / "mipmap-anydpi-v26" / "ic_launcher_round.xml", adaptive)

    small = Image.new("RGBA", (160, 90), color_tuple(VOID) + (255,))
    small.alpha_composite(core_small.resize((56, 56), Image.Resampling.NEAREST), (10, 17))
    draw = ImageDraw.Draw(small)
    draw_pixel_text(draw, "CORE BUILDS", 75, 28, HIGHLIGHT, scale=1, max_width=80)
    draw_pixel_text(draw, "PIXEL NEON", 75, 38, "#00E5FF", scale=1, max_width=80)
    draw_pixel_text(draw, "ICON PACK / ATV", 75, 51, "#9AA8C7", scale=1, max_width=80)
    banner = small.resize((640, 360), Image.Resampling.NEAREST)
    write(res / "drawable-nodpi" / "cb_banner.png", _png_bytes(banner))


def _png_bytes(image) -> bytes:
    stream = io.BytesIO()
    image.save(stream, format="PNG", optimize=False)
    return stream.getvalue()


def main() -> int:
    from PIL import Image

    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())
    for path in (PNG_DIR, XML_DIR, ASSETS_DIR, VAL_DIR, DOC_DIR):
        path.mkdir(parents=True, exist_ok=True)
    wallpaper_count, wallpaper_thumb_count = copy_wallpaper_assets()

    rendered: dict[str, tuple] = {}
    used_hashes: set[str] = set()
    for ordinal, icon in enumerate(icons):
        seed_text = f"{icon['drawable']}:{icon['name']}:{icon.get('category', 'APP')}:{ordinal}"
        seed = int.from_bytes(hashlib.sha256(seed_text.encode()).digest()[:8], "big")
        palette = palette_for(icon, ordinal)
        painter = SpritePainter(palette, seed)
        motif_name = choose_motif(icon, ordinal, painter.rng)
        if motif_name == "brand":
            brand_symbol(painter, icon, ordinal)
        else:
            MOTIFS[motif_name](painter)
        final, small, mask = render_sprite(painter)
        # The catalog has 924 rows; enforce one distinct raster for every row,
        # even if future recipe changes accidentally produce a collision.
        attempt = 0
        pixel_hash = hashlib.sha256(final.tobytes()).hexdigest()
        while pixel_hash in used_hashes:
            painter.signature(ordinal, attempt)
            final, small, mask = render_sprite(painter)
            pixel_hash = hashlib.sha256(final.tobytes()).hexdigest()
            attempt += 1
            if attempt > 8:
                raise SystemExit(f"could not make unique sprite for {icon['name']}")
        used_hashes.add(pixel_hash)
        rendered[icon["drawable"]] = (final, small, palette)
        write(PNG_DIR / f"{icon['drawable']}.png", _png_bytes(final))
        write(PNG_DIR / f"{icon['drawable']}_banner.png",
              _png_bytes(render_banner(small, palette, icon, ordinal)))

    filter_text, source_components, emitted = appfilter(icons)
    write(XML_DIR / "appfilter.xml", filter_text)
    write(ASSETS_DIR / "appfilter.xml", filter_text)
    draw_text = drawable_xml(icons)
    write(XML_DIR / "drawable.xml", draw_text)
    write(ASSETS_DIR / "drawable.xml", draw_text)
    write(XML_DIR / "iconpack.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<iconpack>\n" +
          "\n".join(f'    <item drawable="{i["drawable"]}" />' for i in icons) +
          "\n</iconpack>\n")
    write(VAL_DIR / "icon_pack.xml", values_xml(icons))
    write(DOC_DIR / "IconPackList.md", docs_list(icons, source_components))

    core_final, core_small, _ = rendered["corebuilds"]
    brand_assets(core_final, core_small)
    write(DOC_DIR / "preview.png", _png_bytes(preview(icons, rendered, source_components)))
    receipt = {
        "pack": "Core Builds Pixel Neon",
        "sourceCatalog": "tools/catalog.json",
        "version": "0.1.0",
        "icons": len(icons),
        "wallpapers": wallpaper_count,
        "wallpaperThumbs": wallpaper_thumb_count,
        "wallpaperSource": "PixelNeonWallpapers",
        "wallpaperStyle": "original layered 8-bit environment scenes",
        "catalogComponents": source_components,
        "appfilterEntries": emitted,
        "pixelGrid": SPRITE_GRID,
        "uniqueSprites": len(used_hashes),
        "researchedBrandRecipes": len(RESEARCHED_BY_NAME),
        "artSource": "tools/build_pixel_neon.py researched brand glyph sprite recipes",
        "generatedBy": "tools/build_pixel_neon.py",
    }
    write(DOC_DIR / "build-receipt.json", json.dumps(receipt, indent=2) + "\n")
    print(f"Pixel Neon complete — {len(icons)} unique sprites, {source_components} catalog components, "
          f"{emitted} appfilter entries, {wallpaper_count} wallpaper thumbs, "
          f"{SPRITE_GRID}px source grid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
