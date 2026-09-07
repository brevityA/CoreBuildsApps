#!/usr/bin/env python3
"""Build the genuinely pixel-drawn Core Builds Pixel Neon icon pack.

This is intentionally *not* a pixelated export of the monoline pack. The
original pack's SVG masters, banners, paths, and layout are not read here.
Each catalog row is drawn from a small-pixel sprite recipe selected by its
category/name hash, with different silhouettes, poses, internal details, and
neon palettes. The catalog still owns names and component mappings; this file
owns the alternate art direction.

Design constraints:
  * 32x32 source sprites: a classic, readable icon scale rather than a
    downsampled 512px vector.
  * 4-8 practical sprite colours plus a controlled bloom.
  * silhouette first, no anti-aliasing, hard pixel edges, top-left highlights.
  * no baked background in app icons; launcher cards remain visible through
    transparent pixels.

Generated files live under ``pixel-neon/``. Run from the repository root:

    python tools/build_pixel_neon.py
    python tools/validate_pixel_neon.py

The regular icon generators are deliberately not a prerequisite. This pack
can never silently fall back to the monoline geometry because it has no import
or path to assets/svg or assets/banners.
"""
from __future__ import annotations

import colorsys
import hashlib
import io
import json
import random
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
# traces of the monoline glyph library. The same recipe has multiple positions,
# proportions, highlights, and internal patterns through SpritePainter.rng.
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
        "Each row gets a hash-seeded sprite recipe; this pack does not reuse the monoline SVG geometry or banner lockups.", "",
        "| App | Drawable | Neon source accent | Components |", "| --- | --- | --- | --- |",
    ]
    for icon in icons:
        comps = "<br>".join(f"`{c}`" for c in icon["components"])
        lines.append(f'| {icon["name"]} | `{icon["drawable"]}` | `{icon["color"]}` | {comps} |')
    return "\n".join(lines) + "\n"


def preview(icons: list[dict], rendered: dict[str, tuple], source_components: int):
    """Make a compact review sheet with hard-edged sample tiles."""
    from PIL import Image, ImageDraw, ImageFont

    sample = icons[:48]
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
    draw.text((24, 57), f"{len(icons)} sprites · {source_components} catalog components · 32px source grid", fill="#9AA8C7", font=sub_font)
    draw.text((24, 78), "unique silhouettes · hard pixels · transparent app art", fill="#00E5FF", font=sub_font)
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

    rendered: dict[str, tuple] = {}
    used_hashes: set[str] = set()
    for ordinal, icon in enumerate(icons):
        seed_text = f"{icon['drawable']}:{icon['name']}:{icon.get('category', 'APP')}:{ordinal}"
        seed = int.from_bytes(hashlib.sha256(seed_text.encode()).digest()[:8], "big")
        palette = palette_for(icon, ordinal)
        painter = SpritePainter(palette, seed)
        motif_name = choose_motif(icon, ordinal, painter.rng)
        motif = MOTIFS[motif_name]
        motif(painter)
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
        "catalogComponents": source_components,
        "appfilterEntries": emitted,
        "pixelGrid": SPRITE_GRID,
        "uniqueSprites": len(used_hashes),
        "artSource": "tools/build_pixel_neon.py sprite recipes",
        "generatedBy": "tools/build_pixel_neon.py",
    }
    write(DOC_DIR / "build-receipt.json", json.dumps(receipt, indent=2) + "\n")
    print(f"Pixel Neon complete — {len(icons)} unique sprites, {source_components} catalog components, "
          f"{emitted} appfilter entries, {SPRITE_GRID}px source grid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
