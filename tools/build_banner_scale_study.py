#!/usr/bin/env python3
"""Build the first approved banner scale comparison from the v2.1.1 baseline.

The left column loads shipped 320x180 WebPs from v2.1.1. The right renders the
first approved +10% scale as a historical design receipt; production later
received a further +5%. No Android resource is written by this study tool.

Run with the image dependencies pinned in tools/requirements.txt:
    python tools/build_banner_scale_study.py
"""
from __future__ import annotations

import io
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
sys.path.insert(0, str(TOOLS))

import build_banners  # noqa: E402
from icon_style import display_accent  # noqa: E402
from svg_renderer import svg2png  # noqa: E402

OUT = ROOT / "docs/research/banner-scale-study-2026-10.png"
BASELINE_REF = "v2.1.1"
CATALOG = TOOLS / "catalog.json"
FONTS = TOOLS / "fonts"

WIDTH, HEIGHT = 1280, 1600
BACKGROUND = "#0D1117"
PANEL = "#111822"
CARD = "#151923"
LINE = "#2A3543"
INK = "#E6EDF3"
MUTED = "#95A3B5"
CYAN = "#00D4FF"

SAMPLE_DRAWABLES = ("tvplayer", "nobuffr", "nuvio", "livingroom", "tubi")
CARD_A_X = 270
CARD_B_X = 770
ROW_START = 286
ROW_STEP = 214
CARD_W, CARD_H = 320, 180


def load_font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def text(draw: ImageDraw.ImageDraw, xy: tuple[int, int], value: str,
         size: int, fill: str = INK, *, mono: bool = False,
         bold: bool = True) -> None:
    font_name = "DejaVuSansMono.ttf" if mono else (
        "Outfit-ExtraBold.ttf" if bold else "Outfit-Bold.ttf"
    )
    draw.text(xy, value, font=load_font(font_name, size), fill=fill)


def baseline_banner(icon: dict) -> Image.Image:
    """Load the exact v2.1.1 image so the comparison survives regeneration."""
    asset = f"app/src/main/res/drawable-nodpi/{icon['drawable']}_banner.webp"
    try:
        raw = subprocess.check_output(
            ["git", "show", f"{BASELINE_REF}:{asset}"], cwd=ROOT,
            stderr=subprocess.PIPE,
        )
    except subprocess.CalledProcessError as exc:
        raise RuntimeError(
            f"Cannot read {BASELINE_REF}:{asset}; fetch the v2.1.1 tag first"
        ) from exc
    with Image.open(io.BytesIO(raw)) as source:
        image = source.convert("RGBA")
    if image.size != (CARD_W, CARD_H):
        raise ValueError(f"{icon['name']}: expected a 320x180 baseline, got {image.size}")
    return image


def first_approved_banner(icon: dict) -> Image.Image:
    """Render the initial +10% treatment, frozen for this historical receipt."""
    settings = {
        "GLYPH_H": 396,
        "MAX_TYPE": 136,
        "MIN_TYPE": 68,
        "INK_W": build_banners.W * 0.84,
    }
    old = {key: getattr(build_banners, key) for key in settings}
    try:
        for key, value in settings.items():
            setattr(build_banners, key, value)
        svg = build_banners.render(
            icon.get("banner_name", icon["name"]),
            icon.get("banner_glyph", icon["glyph"]),
            icon["color"],
            icon.get("category"),
            monochrome=icon.get("color_note") == "monochrome",
            gradient=icon.get("gradient"),
            mark=icon.get("mark"),
            style=icon.get("mark_style"),
            secondary=icon.get("secondary"),
        )
        svg = build_banners.recentre(svg)
        png = svg2png(
            bytestring=svg.encode("utf-8"),
            output_width=CARD_W,
            output_height=CARD_H,
            background_color=None,
        )
        with Image.open(io.BytesIO(png)) as source:
            return source.convert("RGBA")
    finally:
        for key, value in old.items():
            setattr(build_banners, key, value)


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_drawable = {icon["drawable"]: icon for icon in catalog["icons"]}
    missing = [drawable for drawable in SAMPLE_DRAWABLES if drawable not in by_drawable]
    if missing:
        raise SystemExit(f"Study entries are missing from catalog.json: {', '.join(missing)}")
    icons = [by_drawable[drawable] for drawable in SAMPLE_DRAWABLES]

    canvas = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    draw = ImageDraw.Draw(canvas)

    text(draw, (50, 32), "CORE BUILDS  /  BANNER SCALE STUDY", 14, CYAN, mono=True)
    text(draw, (50, 65), "Make the mark and name easier to read.", 38)
    text(draw, (50, 125),
         "First approval: same banner, roughly 10% more logo + name.",
         17, MUTED, bold=False)
    draw.rounded_rectangle((1010, 36, 1230, 76), radius=20,
                           fill="#101923", outline="#234455", width=1)
    text(draw, (1028, 48), "DESIGN RECEIPT  ·  01", 11, CYAN, mono=True)
    draw.line((50, 177, 1230, 177), fill=LINE, width=1)

    text(draw, (CARD_A_X, 202), "01  /  BASELINE  ·  v2.1.1", 14, CYAN, mono=True)
    text(draw, (CARD_A_X, 226), "mark  +  category  +  name", 15, INK, bold=False)
    text(draw, (CARD_B_X, 202), "02  /  FIRST APPROVAL  ·  ~1.1×", 14, CYAN, mono=True)
    text(draw, (CARD_B_X, 226), "mark  +  category  +  name", 15, INK, bold=False)
    text(draw, (50, 226), "SAMPLE", 12, MUTED, mono=True)

    for row, icon in enumerate(icons):
        y = ROW_START + row * ROW_STEP
        accent = display_accent(icon["color"],
                                monochrome=icon.get("color_note") == "monochrome")
        category = icon.get("category", "APP")

        draw.line((50, y + 60, 72, y + 60), fill=accent, width=3)
        text(draw, (50, y + 74), icon["name"], 18, INK)
        text(draw, (50, y + 104), category, 11, accent, mono=True)
        text(draw, (50, y + 126), f"0{row + 1}  ·  320 × 180", 10, MUTED, mono=True)

        for x in (CARD_A_X, CARD_B_X):
            draw.rounded_rectangle((x, y, x + CARD_W, y + CARD_H), radius=12,
                                   fill=CARD, outline=LINE, width=1)

        baseline = baseline_banner(icon)
        first_approved = first_approved_banner(icon)
        canvas.paste(baseline, (CARD_A_X, y), baseline)
        canvas.paste(first_approved, (CARD_B_X, y), first_approved)

        if row < len(icons) - 1:
            draw.line((50, y + 194, 1230, y + 194), fill="#202A36", width=1)

    footer_y = ROW_START + len(icons) * ROW_STEP + 2
    draw.rounded_rectangle((50, footer_y, 615, footer_y + 118), radius=14,
                           fill=PANEL, outline=LINE, width=1)
    draw.rounded_rectangle((635, footer_y, 1230, footer_y + 118), radius=14,
                           fill=PANEL, outline=LINE, width=1)
    text(draw, (72, footer_y + 18), "RESULT", 12, CYAN, mono=True)
    text(draw, (72, footer_y + 43), "The mark and name get more presence.", 16)
    text(draw, (72, footer_y + 70), "Category, color, and transparent canvas are retained.",
         12, MUTED, bold=False)
    text(draw, (657, footer_y + 18), "SCOPE", 12, CYAN, mono=True)
    text(draw, (657, footer_y + 43), "Applied to the banner generator pack-wide.", 16)
    text(draw, (657, footer_y + 70), "Square glyphs, wallpapers, and mappings are unchanged.",
         12, MUTED, bold=False)

    text(draw, (50, footer_y + 137),
         "v2.1.1 release assets vs first +10% approval  /  320 × 180  /  five representative entries",
         11, MUTED, mono=True)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUT, optimize=True)
    print(f"Wrote {OUT.relative_to(ROOT)} ({WIDTH}×{HEIGHT}); "
          f"{len(icons)} v2.1.1/first-approval pairs")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
