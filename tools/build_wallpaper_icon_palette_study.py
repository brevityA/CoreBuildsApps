#!/usr/bin/env python3
"""Render a non-shipping concept board for icon-colour wallpaper directions.

The four 16:9 panels are original, low-resolution studies for a possible
companion series. They borrow palette cues only, not logos or icon shapes.
No files under Wallpapers/ and no manifest/artwork are modified.

Run with the dependencies in tools/requirements.txt:
    python tools/build_wallpaper_icon_palette_study.py
"""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
FONTS = ROOT / "tools/fonts"
OUT = ROOT / "docs/research/icon-wallpaper-palette-study-2026-10.png"

BOARD_W, BOARD_H = 1920, 1580
ART_W, ART_H = 840, 472
CARD_W, CARD_H = 880, 580
CARD_X = (70, 970)
CARD_Y = (220, 820)

BG = "#080C13"
PANEL = "#101721"
PANEL_INNER = "#0B111A"
LINE = "#293746"
INK = "#EAF0F6"
MUTED = "#A4B0BE"
CYAN = "#00D4FF"

# Palette notes deliberately distinguish source confidence from visual appeal.
CONCEPTS = [
    {
        "id": "01",
        "brand": "HEATLIFE",
        "name": "Thermal Bloom",
        "palette": ("#EB1E54", "#6900FF"),
        "source": "APK-observed gradient · v5.0.3 reference",
        "confidence": "VERSION-SPECIFIC SOURCE",
        "scene": "heat",
        "note": "Magenta-to-violet energy gathers at a low horizon; square HeatLive art stays untouched.",
    },
    {
        "id": "02",
        "brand": "YOUTUBE",
        "name": "Signal Red",
        "palette": ("#FF0033",),
        "source": "Current official logo colour · checked 2026-10",
        "confidence": "CURRENT OFFICIAL COLOUR",
        "scene": "red",
        "note": "A restrained red transmission sweep, using updated red rather than the catalogue's older #FF0000.",
    },
    {
        "id": "03",
        "brand": "SPOTIFY",
        "name": "Green Current",
        "palette": ("#1ED760",),
        "source": "Current catalogue accent · source field still unverified",
        "confidence": "PROVISIONAL COLOUR",
        "scene": "green",
        "note": "Flowing green light, not the Spotify disc or its three-line mark; reconfirm the exact swatch before final art.",
    },
    {
        "id": "04",
        "brand": "MAXPLAYER",
        "name": "Molten Orbit",
        "palette": ("#F8A000",),
        "source": "Exact-package Play-listing icon · 2026-10-03",
        "confidence": "PACKAGE-SPECIFIC REFERENCE",
        "scene": "amber",
        "note": "Warm amber orbital light; only the palette is borrowed, not the M/play construction.",
    },
]


def hex_rgb(value: str) -> tuple[int, int, int]:
    value = value.removeprefix("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def rgb_hex(rgb: tuple[int, int, int]) -> str:
    return "#%02X%02X%02X" % rgb


def mix(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return tuple(round(x * (1 - t) + y * t) for x, y in zip(a, b))


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def write(draw: ImageDraw.ImageDraw, xy: tuple[int, int], label: str,
          size: int, color: str = INK, *, mono: bool = False,
          regular: bool = False) -> None:
    if mono:
        face = "DejaVuSansMono.ttf"
    elif regular:
        face = "Outfit-Bold.ttf"
    else:
        face = "Outfit-ExtraBold.ttf"
    draw.text(xy, label, font=font(face, size), fill=color)


def blend_glow(canvas: Image.Image, color: tuple[int, int, int],
               center: tuple[float, float], spread: tuple[float, float],
               strength: float) -> Image.Image:
    yy, xx = np.mgrid[0:ART_H, 0:ART_W]
    cx, cy = center[0] * ART_W, center[1] * ART_H
    sx, sy = spread[0] * ART_W, spread[1] * ART_H
    weight = np.exp(-1.35 * (((xx - cx) / sx) ** 2 + ((yy - cy) / sy) ** 2))
    weight = np.clip(weight * strength, 0.0, 0.72)[..., None]
    base = np.asarray(canvas, dtype=np.float32)
    accent = np.asarray(color, dtype=np.float32)
    out = base * (1.0 - weight) + accent * weight
    return Image.fromarray(np.uint8(np.clip(out, 0, 255)), mode="RGB")


def scene_art(spec: dict, seed: int) -> Image.Image:
    colors = tuple(hex_rgb(c) for c in spec["palette"])
    base = Image.new("RGB", (ART_W, ART_H), "#04070F")
    if spec["scene"] == "heat":
        base = blend_glow(base, colors[0], (0.50, 0.54), (0.44, 0.28), 0.55)
        base = blend_glow(base, colors[1], (0.74, 0.53), (0.40, 0.28), 0.47)
    elif spec["scene"] == "red":
        base = blend_glow(base, colors[0], (0.66, 0.55), (0.44, 0.24), 0.45)
        base = blend_glow(base, mix(colors[0], (255, 255, 255), 0.24),
                          (0.66, 0.52), (0.18, 0.15), 0.20)
    elif spec["scene"] == "green":
        base = blend_glow(base, colors[0], (0.32, 0.43), (0.52, 0.38), 0.46)
        base = blend_glow(base, colors[0], (0.70, 0.57), (0.35, 0.20), 0.22)
    elif spec["scene"] == "amber":
        base = blend_glow(base, colors[0], (0.59, 0.47), (0.46, 0.30), 0.50)
        base = blend_glow(base, mix(colors[0], (255, 232, 170), 0.45),
                          (0.60, 0.47), (0.22, 0.17), 0.20)
    else:
        base = blend_glow(base, colors[0], (0.48, 0.47), (0.48, 0.36), 0.62)
        base = blend_glow(base, mix(colors[0], (255, 255, 255), 0.35),
                          (0.50, 0.51), (0.25, 0.18), 0.19)

    glow = Image.new("RGBA", (ART_W, ART_H), (0, 0, 0, 0))
    ink = Image.new("RGBA", (ART_W, ART_H), (0, 0, 0, 0))
    gd, draw = ImageDraw.Draw(glow), ImageDraw.Draw(ink)
    rng = np.random.default_rng(seed)

    # Shared atmosphere: a quiet top field and a deliberately dark lower third.
    # Sparse pinpoints provide scale without introducing a competing focal area.
    for _ in range(50):
        px = int(rng.integers(12, ART_W - 12))
        py = int(rng.integers(12, int(ART_H * 0.43)))
        alpha = int(rng.integers(14, 45))
        draw.ellipse((px, py, px + 1, py + 1), fill=(196, 218, 238, alpha))

    if spec["scene"] == "heat":
        left, right = hex_rgb(spec["palette"][0]), hex_rgb(spec["palette"][1])
        horizon = int(ART_H * 0.585)
        # A low, gently modulated horizon and fine echoes, with no flame/drop icon.
        for j, offset in enumerate((0, -12, -24, -39, 17, 34)):
            points = []
            for x in range(int(ART_W * 0.06), int(ART_W * 0.95), 4):
                t = x / ART_W
                y = horizon + offset + int(3 * math.sin(t * 9.0 + j * 0.35)
                                            + 2 * math.sin(t * 17.0 - j * 0.2))
                points.append((x, y))
            color = mix(left, right, min(1, max(0, (j + 1) / 7)))
            gd.line(points, fill=(*color, max(28, 125 - j * 15)), width=7 if j == 0 else 3)
            draw.line(points, fill=(*color, 142 if j == 0 else 54), width=2 if j == 0 else 1)
        cx, cy = int(ART_W * 0.69), horizon - 16
        gd.ellipse((cx - 42, cy - 42, cx + 42, cy + 42), fill=(*left, 105))
        draw.ellipse((cx - 15, cy - 15, cx + 15, cy + 15),
                     fill=(*mix(left, right, .38), 215))
        draw.arc((cx - 68, cy - 68, cx + 68, cy + 68), 205, 335,
                 fill=(*right, 92), width=1)
    elif spec["scene"] == "red":
        red = colors[0]
        cx, cy = int(ART_W * 0.70), int(ART_H * 0.49)
        # Expanding signal ellipses, intentionally not a play-button silhouette.
        for radius in (52, 91, 136, 188):
            box = (cx - radius * 1.55, cy - radius * .62,
                   cx + radius * 1.55, cy + radius * .62)
            gd.arc(box, 195, 345, fill=(*red, 55), width=8)
            draw.arc(box, 195, 345, fill=(*red, 85 if radius < 100 else 42), width=1)
        for i in range(7):
            x0 = int(ART_W * .10)
            x1 = int(ART_W * (.42 + i * .055))
            y = int(ART_H * (.51 + i * .018))
            draw.line((x0, y, x1, y), fill=(*red, max(18, 90 - i * 9)), width=1)
        gd.ellipse((cx - 19, cy - 19, cx + 19, cy + 19), fill=(*red, 120))
        draw.ellipse((cx - 4, cy - 4, cx + 4, cy + 4), fill=(255, 236, 240, 190))
    elif spec["scene"] == "green":
        green = colors[0]
        # Flowfield ribbons: more organic than an equalizer or three sound arcs.
        for band in range(9):
            pts = []
            for x in range(-10, ART_W + 12, 3):
                t = x / ART_W
                y = (ART_H * (0.39 + band * 0.025)
                     + 18 * math.sin(t * 6.3 + band * .43)
                     + 9 * math.sin(t * 13.0 - band * .31))
                pts.append((x, int(y)))
            alpha = max(22, 104 - band * 9)
            gd.line(pts, fill=(*green, alpha), width=8 if band in (2, 5) else 4)
            draw.line(pts, fill=(*green, alpha), width=1)
        for i in range(22):
            x = int(rng.integers(70, ART_W - 70))
            y = int(rng.integers(100, int(ART_H * .60)))
            r = int(rng.integers(1, 3))
            draw.ellipse((x-r, y-r, x+r, y+r), fill=(*green, int(rng.integers(55, 140))))
    elif spec["scene"] == "amber":
        amber = colors[0]
        glint = mix(amber, (255, 239, 190), 0.42)
        cx, cy = int(ART_W * .62), int(ART_H * .48)
        # Broad orbital ellipses and a low sun; no M or play-triangle motif.
        for radius, alpha in ((54, 72), (92, 58), (138, 42), (190, 30)):
            box = (cx - radius * 1.45, cy - radius * .60,
                   cx + radius * 1.45, cy + radius * .60)
            gd.arc(box, 194, 347, fill=(*amber, alpha), width=8)
            draw.arc(box, 194, 347, fill=(*amber, max(24, alpha)), width=1)
        horizon = int(ART_H * .59)
        draw.line((int(ART_W*.09), horizon, int(ART_W*.91), horizon),
                  fill=(*amber, 95), width=1)
        gd.ellipse((cx - 30, horizon - 31, cx + 30, horizon + 29),
                   fill=(*amber, 110))
        draw.ellipse((cx - 11, horizon - 12, cx + 11, horizon + 10),
                     fill=(*glint, 210))
        for i in range(9):
            px = int(ART_W * (.20 + i * .07))
            length = int(12 + 24 * (1 - abs(.62 - px / ART_W)))
            draw.line((px, horizon - 54 - length, px, horizon - 54),
                      fill=(*glint, 40 + (i % 3) * 12), width=1)
    else:
        green = colors[0]
        # Topographic, glass-like contour sweeps. Highlight tint is derived from
        # the sampled deep green solely to keep a TV-sized preview legible.
        glint = mix(green, (232, 255, 247), 0.48)
        for band in range(12):
            pts = []
            phase = band * .21
            for x in range(-8, ART_W + 8, 3):
                t = x / ART_W
                y = (ART_H * (0.34 + band * .025)
                     + 24 * math.sin(t * 4.8 + phase)
                     + 14 * math.sin(t * 8.2 - phase * 1.7))
                pts.append((x, int(y)))
            color = glint if band in (3, 7) else green
            alpha = 112 if band in (3, 7) else max(22, 76 - band * 4)
            gd.line(pts, fill=(*color, alpha), width=8 if band in (3, 7) else 4)
            draw.line(pts, fill=(*color, alpha), width=1)
        cx, cy = int(ART_W * .52), int(ART_H * .48)
        for rad in (18, 34, 53):
            draw.arc((cx-rad, cy-rad, cx+rad, cy+rad), 210, 327,
                     fill=(*glint, max(40, 130-rad)), width=1)

    # Soft bloom first; crisp Core-like linework second.
    base = Image.alpha_composite(base.convert("RGBA"), glow.filter(ImageFilter.GaussianBlur(18)))
    base = Image.alpha_composite(base, ink)
    # Mild lower vignette keeps the calm launcher-card area dark.
    arr = np.asarray(base.convert("RGB"), dtype=np.float32)
    yy = np.linspace(0, 1, ART_H)[:, None, None]
    shade = np.clip((yy - .56) / .44, 0, 1) * .33
    arr *= (1 - shade)
    return Image.fromarray(np.uint8(np.clip(arr, 0, 255)), mode="RGB")


def swatch(draw: ImageDraw.ImageDraw, x: int, y: int, color: str) -> None:
    draw.rounded_rectangle((x, y, x + 22, y + 14), radius=3,
                           fill=color, outline="#536172", width=1)


def main() -> int:
    board = Image.new("RGB", (BOARD_W, BOARD_H), BG)
    draw = ImageDraw.Draw(board)
    write(draw, (70, 42), "CORE BUILDS  /  WALLPAPER COLOUR STUDY", 14, CYAN, mono=True)
    write(draw, (70, 78), "Icon accents, translated into atmosphere", 40)
    write(draw, (70, 136),
          "A draft companion-series direction · four 16:9 concepts · palette cues only, no logos",
          17, MUTED, regular=True)
    draw.rounded_rectangle((1455, 48, 1850, 91), radius=19,
                           fill="#101923", outline="#244455", width=1)
    write(draw, (1475, 63), "CONCEPTS  /  NOT IN MANIFEST", 10, CYAN, mono=True)
    draw.line((70, 184, 1850, 184), fill=LINE, width=1)

    for index, spec in enumerate(CONCEPTS):
        col, row = index % 2, index // 2
        x, y = CARD_X[col], CARD_Y[row]
        draw.rounded_rectangle((x, y, x + CARD_W, y + CARD_H), radius=18,
                               fill=PANEL, outline=LINE, width=1)
        write(draw, (x + 22, y + 14), f"{spec['id']}  /  {spec['brand']}", 10, CYAN, mono=True)
        write(draw, (x + 22, y + 32), spec["name"], 21, INK)

        art = scene_art(spec, 723 + index * 83)
        art_x, art_y = x + 20, y + 64
        board.paste(art, (art_x, art_y))
        draw.rounded_rectangle((art_x, art_y, art_x + ART_W, art_y + ART_H),
                               radius=8, outline="#354150", width=1)

        palette_x, palette_y = x + 22, y + 543
        swatch(draw, palette_x, palette_y + 2, spec["palette"][0])
        write(draw, (palette_x + 31, palette_y), spec["palette"][0], 10, INK, mono=True)
        cursor = palette_x + 126
        if len(spec["palette"]) > 1:
            write(draw, (cursor, palette_y), "→", 10, MUTED, mono=True)
            cursor += 24
            swatch(draw, cursor, palette_y + 2, spec["palette"][1])
            write(draw, (cursor + 31, palette_y), spec["palette"][1], 10, INK, mono=True)
        write(draw, (x + 540, palette_y), spec["confidence"], 8,
              "#F4C96B" if "PROVISIONAL" in spec["confidence"] else MUTED, mono=True)
        write(draw, (x + 22, y + 560), spec["note"], 10, MUTED, regular=True)

    footer_y = 1420
    draw.rounded_rectangle((70, footer_y, 1850, 1533), radius=14,
                           fill=PANEL, outline=LINE, width=1)
    write(draw, (94, footer_y + 17), "STUDY GUARDRAILS", 11, CYAN, mono=True)
    write(draw, (94, footer_y + 43),
          "The current 102-wallpaper collection stays untouched. These are low-resolution concept previews, not 4K finals or approved series assets.",
          13, INK, regular=True)
    write(draw, (94, footer_y + 72),
          "Keep the Core night ground and calm lower third; use accent light sparingly. Confirm provisional color sources before production.",
          12, MUTED, regular=True)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    board.save(OUT, optimize=True)
    print(f"Wrote {OUT.relative_to(ROOT)} ({BOARD_W}×{BOARD_H}); "
          f"4 concepts; no files under Wallpapers/ touched")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
