#!/usr/bin/env python3
"""Build a visual receipt for the six-app vSeeBox Heat family.

Shows the square glyphs and the current 320x180 banners. The HeatLive square
must remain byte-identical to the v2.0.0 anchor; siblings use its canonical
drop/flame geometry with the researched play, cone, plus, or backup cue.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/research/heat-family-review-2026-10.png"
CATALOG = ROOT / "tools/catalog.json"
ASSETS = ROOT / "app/src/main/res/drawable-nodpi"
FONTS = ROOT / "tools/fonts"

WIDTH, HEIGHT = 1450, 1300
BACKGROUND = "#0D1117"
PANEL = "#111822"
CARD = "#151923"
LINE = "#2A3543"
INK = "#E6EDF3"
MUTED = "#95A3B5"
CYAN = "#00D4FF"

FAMILY = (
    ("heatlive", "HeatLive", "v2.0.0 anchor · flame"),
    ("heatvod", "HeatVod", "play · on demand"),
    ("heatvod_ultra", "HeatVod Ultra", "play + Ultra cone"),
    ("live_ultra", "Live Ultra", "flame + Ultra cone"),
    ("live_ultra_plus", "Live Ultra+", "flame + plus"),
    ("heatlive_backup", "HeatLive Backup", "flame + spare drop"),
)
HEATLIVE_V200_SHA256 = "fdd357992eaa30af8d5d940b7185848e9520d15b0e33863510d72605ffd1752e"


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def label(draw: ImageDraw.ImageDraw, xy: tuple[int, int], value: str,
          size: int, color: str = INK, *, mono: bool = False,
          regular: bool = False) -> None:
    filename = "DejaVuSansMono.ttf" if mono else (
        "Outfit-Bold.ttf" if regular else "Outfit-ExtraBold.ttf"
    )
    draw.text(xy, value, font=font(filename, size), fill=color)


def rgba(path: Path, size: tuple[int, int]) -> Image.Image:
    with Image.open(path) as source:
        return source.convert("RGBA").resize(size, Image.Resampling.LANCZOS)


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_drawable = {icon["drawable"]: icon for icon in catalog["icons"]}
    missing = [drawable for drawable, _, _ in FAMILY if drawable not in by_drawable]
    if missing:
        raise SystemExit(f"Heat family is incomplete in catalog.json: {missing}")

    heatlive_bytes = (ASSETS / "heatlive.webp").read_bytes()
    heatlive_sha256 = hashlib.sha256(heatlive_bytes).hexdigest()
    if heatlive_sha256 != HEATLIVE_V200_SHA256:
        raise SystemExit(
            "HeatLive square artwork no longer matches the v2.0.0 anchor: "
            f"{heatlive_sha256}"
        )

    canvas = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    draw = ImageDraw.Draw(canvas)

    label(draw, (52, 34), "CORE BUILDS  /  VSEEBOX HEAT FAMILY", 14, CYAN, mono=True)
    label(draw, (52, 68), "One 2.0.0 anchor. Six related marks.", 38)
    label(draw, (52, 127),
          "HeatLive's original drop + flame stays intact; each sibling adds one clear cue.",
          17, MUTED, regular=True)
    draw.rounded_rectangle((1052, 36, 1395, 78), radius=20,
                           fill="#101923", outline="#234455", width=1)
    label(draw, (1072, 50), "HEATLIVE SHA256 VERIFIED", 11, CYAN, mono=True)
    draw.line((52, 178, 1398, 178), fill=LINE, width=1)

    label(draw, (52, 205), "SQUARE GLYPHS", 13, CYAN, mono=True)
    label(draw, (52, 228), "Original 2.0.0 source art · current siblings · 512 × 512", 15, MUTED, regular=True)

    tile_x, tile_y, tile_w, tile_h, tile_gap = 52, 260, 210, 232, 16
    for index, (drawable, name, role) in enumerate(FAMILY):
        icon = by_drawable[drawable]
        x = tile_x + index * (tile_w + tile_gap)
        draw.rounded_rectangle((x, tile_y, x + tile_w, tile_y + tile_h), radius=14,
                               fill=PANEL, outline=LINE, width=1)
        art = rgba(ASSETS / f"{drawable}.webp", (148, 148))
        canvas.paste(art, (x + 31, tile_y + 12), art)
        label(draw, (x + 14, tile_y + 166), name, 16, INK)
        label(draw, (x + 14, tile_y + 196), role, 10,
              icon["color"], mono=True)

    label(draw, (52, 523), "BANNERS", 13, CYAN, mono=True)
    label(draw, (52, 546), "16:9 release art · current +15% over v2.1.1 · transparent", 15,
          MUTED, regular=True)

    banner_w, banner_h = 420, 236
    banner_xs = (52, 515, 978)
    banner_ys = (580, 842)
    for index, (drawable, name, role) in enumerate(FAMILY):
        icon = by_drawable[drawable]
        col, row = index % 3, index // 3
        x, y = banner_xs[col], banner_ys[row]
        draw.rounded_rectangle((x, y, x + banner_w, y + banner_h), radius=14,
                               fill=CARD, outline=LINE, width=1)
        art = rgba(ASSETS / f"{drawable}_banner.webp", (banner_w, banner_h))
        canvas.paste(art, (x, y), art)
        label(draw, (x, y + banner_h + 7), name, 15, INK)
        label(draw, (x + banner_w - 180, y + banner_h + 10), role, 10,
              icon["color"], mono=True)

    footer_y = 1120
    draw.rounded_rectangle((52, footer_y, 1398, footer_y + 105), radius=14,
                           fill=PANEL, outline=LINE, width=1)
    label(draw, (74, footer_y + 18), "DESIGN CHECK", 12, CYAN, mono=True)
    label(draw, (74, footer_y + 44),
          "Drop + flame are the v2.0.0 anchor. Play, cone, plus, and spare-drop cues keep variants distinct.",
          16, INK)
    label(draw, (74, footer_y + 73),
          "Square art is unchanged; the approved banner size increase applies consistently across this family.",
          12, MUTED, regular=True)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUT, optimize=True)
    print(f"Wrote {OUT.relative_to(ROOT)} ({WIDTH}×{HEIGHT}); HeatLive matches v2.0.0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
