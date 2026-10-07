#!/usr/bin/env python3
"""Build a visual receipt for the selected follow-up banner scale.

Compares the previous approved +10% treatment, the adopted +5% follow-up, and
the larger +10% alternative. The selected production WebPs are read from the
app resources; the other two scales render in memory. Category, accents,
transparency, and the glyph/name gap stay fixed. No production art is written.

Run with the dependencies in tools/requirements.txt:
    python tools/build_banner_scale_options.py
"""
from __future__ import annotations

import io
import json
import sys
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
sys.path.insert(0, str(TOOLS))

import build_banners  # noqa: E402
from icon_style import display_accent  # noqa: E402
from svg_renderer import svg2png  # noqa: E402

OUT = ROOT / "docs/research/banner-scale-options-2026-10.png"
CATALOG = TOOLS / "catalog.json"
RES = ROOT / "app/src/main/res/drawable-nodpi"
FONTS = TOOLS / "fonts"

WIDTH, HEIGHT = 1450, 1765
BACKGROUND = "#0D1117"
PANEL = "#111822"
CARD = "#151923"
LINE = "#2A3543"
INK = "#E6EDF3"
MUTED = "#95A3B5"
CYAN = "#00D4FF"

SAMPLES = (
    "tvplayer",                     # screen-shaped glyph
    "nobuffr",                      # distinctive word cue
    "nuvio",                        # gradient glyph
    "livingroom",                   # long, two-word name
    "tubi",                         # compact monogram
    "ultimatefilemanager",          # long displayed-name fit stress case
)
OPTIONS = (
    ("PREVIOUS", 1.00),
    ("SELECTED +5%", 1.05),
    ("ALT +10%", 1.10),
)
CARD_W, CARD_H = 320, 180
CARD_X = (245, 650, 1055)
ROW_START, ROW_STEP = 282, 218
BASE = {
    "GLYPH_H": 396,
    "MAX_TYPE": 136,
    "MIN_TYPE": 68,
    "INK_W": build_banners.W * 0.84,
}


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size)


def label(draw: ImageDraw.ImageDraw, xy: tuple[int, int], value: str,
          size: int, color: str = INK, *, mono: bool = False,
          regular: bool = False, spacing: int = 4) -> None:
    filename = "DejaVuSansMono.ttf" if mono else (
        "Outfit-Bold.ttf" if regular else "Outfit-ExtraBold.ttf"
    )
    draw.multiline_text(xy, value, font=font(filename, size), fill=color,
                        spacing=spacing)


def production_banner(icon: dict) -> Image.Image:
    path = RES / f"{icon['drawable']}_banner.webp"
    if not path.is_file():
        raise FileNotFoundError(f"Current production banner is missing: {path}")
    with Image.open(path) as source:
        image = source.convert("RGBA")
    if image.size != (CARD_W, CARD_H):
        raise ValueError(f"{icon['name']}: expected 320x180, got {image.size}")
    return image


def render_at_scale(icon: dict, factor: float) -> Image.Image:
    """Render a frozen decision scale with temporary settings, then restore."""
    old = {key: getattr(build_banners, key) for key in BASE}
    try:
        build_banners.GLYPH_H = round(BASE["GLYPH_H"] * factor)
        build_banners.MAX_TYPE = round(BASE["MAX_TYPE"] * factor)
        build_banners.MIN_TYPE = round(BASE["MIN_TYPE"] * factor)
        # Preserve proportional width growth until the 90% safe-area ceiling.
        width_ratio = min(0.84 * factor, 0.90)
        build_banners.INK_W = build_banners.W * width_ratio
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


def ink_percent(image: Image.Image) -> tuple[float, float]:
    box = image.getchannel("A").getbbox()
    if not box:
        return 0.0, 0.0
    left, top, right, bottom = box
    return ((right - left) / image.width * 100,
            (bottom - top) / image.height * 100)


def main() -> int:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_drawable = {icon["drawable"]: icon for icon in catalog["icons"]}
    missing = [drawable for drawable in SAMPLES if drawable not in by_drawable]
    if missing:
        raise SystemExit(f"Comparison entries are missing: {', '.join(missing)}")
    icons = [by_drawable[drawable] for drawable in SAMPLES]

    variants: list[list[Image.Image]] = [[], [], []]
    for icon in icons:
        variants[0].append(render_at_scale(icon, 1.00))
        variants[1].append(production_banner(icon))
        variants[2].append(render_at_scale(icon, 1.10))

    bounds = []
    for images in variants:
        boxes = [ink_percent(image) for image in images]
        bounds.append((max(box[0] for box in boxes), max(box[1] for box in boxes)))

    canvas = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    draw = ImageDraw.Draw(canvas)

    label(draw, (50, 32), "CORE BUILDS  /  BANNER SCALE OPTIONS", 14, CYAN, mono=True)
    label(draw, (50, 66), "A modest +5% step is selected.", 38)
    label(draw, (50, 123),
          "Previous scale, adopted production scale, and the larger alternative.",
          17, MUTED, regular=True)
    draw.rounded_rectangle((1120, 36, 1400, 78), radius=20,
                           fill="#101923", outline="#234455", width=1)
    label(draw, (1141, 50), "DECISION  ·  +5% SELECTED", 11, CYAN, mono=True)
    draw.line((50, 177, 1400, 177), fill=LINE, width=1)

    column_titles = (
        ("PREVIOUS  /  +10% vs v2.1.1", "before the follow-up increase"),
        ("SELECTED  /  ~+15% TOTAL", "current production: +5% follow-up"),
        ("ALTERNATIVE  /  ~+21% TOTAL", "another +10%; width cap at 90%"),
    )
    for x, (title, subline) in zip(CARD_X, column_titles):
        label(draw, (x, 202), title, 13, CYAN, mono=True)
        label(draw, (x, 226), subline, 13, INK, regular=True)
    label(draw, (50, 226), "SAMPLE", 12, MUTED, mono=True)

    for row, icon in enumerate(icons):
        y = ROW_START + row * ROW_STEP
        accent = display_accent(icon["color"],
                                monochrome=icon.get("color_note") == "monochrome")
        name_lines = "\n".join(textwrap.wrap(icon["name"], width=17))
        label(draw, (50, y + 54), name_lines, 14, INK, spacing=2)
        category_y = y + 105 if "\n" not in name_lines else y + 137
        label(draw, (50, category_y), icon.get("category", "APP"),
              11, accent, mono=True)
        label(draw, (50, category_y + 24), f"0{row + 1}  ·  320 × 180",
              10, MUTED, mono=True)

        for column, x in enumerate(CARD_X):
            draw.rounded_rectangle((x, y, x + CARD_W, y + CARD_H), radius=12,
                                   fill=CARD, outline=LINE, width=1)
            canvas.paste(variants[column][row], (x, y), variants[column][row])

        if row < len(icons) - 1:
            draw.line((50, y + 198, 1400, y + 198), fill="#202A36", width=1)

    footer_y = ROW_START + len(icons) * ROW_STEP + 8
    panel_w = 650
    draw.rounded_rectangle((50, footer_y, 50 + panel_w, footer_y + 126), radius=14,
                           fill=PANEL, outline=LINE, width=1)
    draw.rounded_rectangle((720, footer_y, 1400, footer_y + 126), radius=14,
                           fill=PANEL, outline=LINE, width=1)
    label(draw, (72, footer_y + 17), "SAMPLE INK BOUNDS", 12, CYAN, mono=True)
    for column, x in enumerate(CARD_X):
        max_w, max_h = bounds[column]
        value = f"{OPTIONS[column][0]}  {max_w:.1f}% wide  ·  {max_h:.1f}% high"
        label(draw, (72, footer_y + 42 + column * 21), value, 12,
              INK if column == 0 else MUTED, mono=True)
    label(draw, (742, footer_y + 17), "HELD FIXED / NEXT STEP", 12, CYAN, mono=True)
    label(draw, (742, footer_y + 42),
          "Category, accent, transparency, and glyph/name gap are unchanged.",
          13, INK, regular=True)
    label(draw, (742, footer_y + 69),
          "The selected +5% is in production; the larger alternative remains preview-only.",
          12, MUTED, regular=True)
    label(draw, (742, footer_y + 96),
          "Six examples shown; the +10% alternative still needs full-catalog validation.",
          11, MUTED, regular=True)

    label(draw, (50, footer_y + 147),
          "Previous = first +10% approval  /  selected = additional +5%  /  sample bounds only",
          11, MUTED, mono=True)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUT, optimize=True)
    max_width = max(value[0] for value in bounds)
    print(f"Wrote {OUT.relative_to(ROOT)} ({WIDTH}×{HEIGHT}); "
          f"previous/selected/alternative compared, sample max ink width {max_width:.1f}%; "
          "only the review image is written by this tool")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
