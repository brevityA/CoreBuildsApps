#!/usr/bin/env python3
"""
Core Builds Pop — wallpaper series.

Twelve 3840x2160 wallpapers in the same visual language as the icons, so a
Projectivy home screen made of Pop cards has a background that belongs to them
rather than one borrowed from the classic pack's mesh gradients.

Design constraints, and why each is here:

  * Dark by default. r/Amoledbackgrounds is the single biggest wallpaper
    audience in Android theming, and TV panels are watched in dark rooms.
    Every wall keeps 70-90% of the frame at or below the night-chrome value.
  * Calm bottom third. Projectivy draws its card rows across the lower half.
    Detail there competes with the thing the wallpaper exists to sit behind.
  * Overscan-safe. Google's TV layout guidance budgets a 10% margin on all
    sides; nothing load-bearing is placed inside it.
  * Flat, not smooth. Pop art is flat colour and mechanical screens. That is
    also why these need no film-grain dither: there are no smooth ramps to
    band, so they survive an indexed palette losslessly and ship at a fraction
    of the size of the existing 4K series.
  * Palette-locked. Every colour is a Pop swatch, ink, or cream. The same
    sixteen that the icons use.

Writes:
  Wallpapers/series-5-pop/corepop-NN-slug.png     3840x2160 indexed PNG
  Wallpapers/thumbs/corepop-NN-slug.jpg           480x270
  Wallpapers/pop-manifest.json                    the index
  pop/src/main/assets/manifest/wallpapers.json    bundled copy (in-app browser)
  pop/src/main/assets/wallpapers_thumbs/*.jpg     bundled thumbs
  docs/pop-wallpapers.png                         contact sheet
"""
from __future__ import annotations

import io
import json
import math
import os
import shutil
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import popart  # noqa: E402
from popart import CREAM, INK, SWATCHES, shade  # noqa: E402
from svg_renderer import svg2png  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-5-pop"
THUMBS = ROOT / "Wallpapers" / "thumbs"
MANIFEST = ROOT / "Wallpapers" / "pop-manifest.json"
BUNDLED_MANIFEST = ROOT / "pop" / "src" / "main" / "assets" / "manifest" / "wallpapers.json"
BUNDLED_THUMBS = ROOT / "pop" / "src" / "main" / "assets" / "wallpapers_thumbs"
DOCS = ROOT / "docs"

W, H = 3840, 2160
THUMB_W, THUMB_H = 480, 270
NIGHT = "#0D1117"        # Core Builds night chrome, shared with the classic pack
VOID = "#06080D"

RAW = ("https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/"
       "Wallpapers")

S = SWATCHES


def _svg(body: str, bg: str = NIGHT) -> str:
    return (f'{popart._SVG_OPEN} width="{W}" height="{H}" '
            f'viewBox="0 0 {W} {H}">'
            f'<rect width="{W}" height="{H}" fill="{bg}"/>'
            f'{body}</svg>')


def _halftone(uid: str, colour: str, pitch: float, radius: float,
              angle: float = 45.0) -> str:
    return (f'<pattern id="{uid}" width="{pitch}" height="{pitch}" '
            f'patternUnits="userSpaceOnUse" patternTransform="rotate({angle})">'
            f'<circle cx="{pitch / 2:.1f}" cy="{pitch / 2:.1f}" r="{radius}" '
            f'fill="{colour}"/></pattern>')


def _fade(uid: str, x1, y1, x2, y2, stops) -> str:
    body = "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    return (f'<linearGradient id="{uid}" x1="{x1}" y1="{y1}" x2="{x2}" '
            f'y2="{y2}">{body}</linearGradient>')


def _mask(uid: str, grad: str) -> str:
    return (f'<mask id="{uid}"><rect width="{W}" height="{H}" '
            f'fill="url(#{grad})"/></mask>')


# --------------------------------------------------------------------------
# The twelve
# --------------------------------------------------------------------------
def w01_benday_dawn() -> str:
    """A single swatch as a Ben-Day screen that dissolves into night."""
    c = S["pop_red"]
    return _svg(
        f'<defs>{_halftone("h", c, 46, 17)}'
        f'{_fade("g", 0, 0, 0.55, 1, [("0", "#FFFFFF"), ("0.52", "#5A5A5A"), ("1", "#000000")])}'
        f'{_mask("m", "g")}</defs>'
        f'<rect width="{W}" height="{H}" fill="url(#h)" mask="url(#m)"/>')


def w02_speed_lines() -> str:
    """Comic motion lines converging on a point above the card rows."""
    cx, cy = W * 0.5, H * 0.34
    rays = []
    for i in range(180):
        a = 2 * math.pi * i / 180
        x0 = cx + math.cos(a) * 60
        y0 = cy + math.sin(a) * 60
        x1 = cx + math.cos(a) * 3400
        y1 = cy + math.sin(a) * 3400
        wgt = 5 + (i % 7) * 3.2
        rays.append(f'<path d="M {x0:.0f} {y0:.0f} L {x1:.0f} {y1:.0f}" '
                    f'stroke="{S["pop_aqua"]}" stroke-width="{wgt:.1f}" '
                    f'stroke-linecap="round"/>')
    return _svg(
        f'<defs>{_fade("g", 0, 0, 0, 1, [("0", "#FFFFFF"), ("0.46", "#9A9A9A"), ("0.82", "#000000")])}'
        f'{_mask("m", "g")}</defs>'
        f'<g mask="url(#m)">{"".join(rays)}</g>', bg=VOID)


def w03_impact_burst() -> str:
    """The starburst behind the sound effect, without the sound effect."""
    cx, cy = W * 0.5, H * 0.40
    pts = []
    for i in range(28):
        a = 2 * math.pi * i / 28 - math.pi / 2
        r = 1500 if i % 2 == 0 else 880
        pts.append(f"{cx + r * math.cos(a):.0f},{cy + r * math.sin(a):.0f}")
    star = "M " + " L ".join(pts) + " Z"
    return _svg(
        f'<defs>{_halftone("h", shade(S["pop_sun"], 0.62), 40, 14)}'
        f'{_fade("g", 0, 0, 0, 1, [("0", "#FFFFFF"), ("0.62", "#6E6E6E"), ("1", "#000000")])}'
        f'{_mask("m", "g")}</defs>'
        f'<g mask="url(#m)">'
        f'<path d="{star}" fill="{S["pop_sun"]}"/>'
        f'<path d="{star}" fill="url(#h)"/>'
        f'<path d="{star}" fill="none" stroke="{INK}" stroke-width="34" '
        f'stroke-linejoin="round"/></g>', bg=VOID)


def w04_panel_grid() -> str:
    """Comic panels with ink gutters. Only the top row is lit; the rows the
    launcher draws over stay near-black."""
    cells = [
        (0.06, 0.10, 0.40, 0.30, S["pop_grape"]),
        (0.48, 0.10, 0.20, 0.30, S["pop_blaze"]),
        (0.70, 0.10, 0.24, 0.30, S["pop_jade"]),
        (0.06, 0.42, 0.26, 0.20, S["pop_magenta"]),
        (0.34, 0.42, 0.60, 0.20, shade(S["pop_marine"], 0.5)),
    ]
    out = []
    for n, (x, y, w, h, c) in enumerate(cells):
        px, py, pw, ph = x * W, y * H, w * W, h * H
        out.append(f'<rect x="{px:.0f}" y="{py:.0f}" width="{pw:.0f}" '
                   f'height="{ph:.0f}" fill="{c}"/>')
        out.append(f'<rect x="{px:.0f}" y="{py:.0f}" width="{pw:.0f}" '
                   f'height="{ph:.0f}" fill="url(#h{n})"/>')
        out.append(f'<rect x="{px:.0f}" y="{py:.0f}" width="{pw:.0f}" '
                   f'height="{ph:.0f}" fill="none" stroke="{INK}" '
                   f'stroke-width="26"/>')
    defs = "".join(_halftone(f"h{n}", shade(c[4], 0.66), 38, 12.5)
                   for n, c in enumerate(cells))
    return _svg(f'<defs>{defs}</defs>{"".join(out)}')


def w05_ink_bleed() -> str:
    """Heavy ink with one colour bleeding up from the horizon."""
    return _svg(
        f'<defs>'
        f'{_fade("g", 0, 1, 0, 0, [("0", "#000000"), ("0.30", "#3A3A3A"), ("0.58", "#FFFFFF"), ("1", "#FFFFFF")])}'
        f'{_mask("m", "g")}'
        f'{_halftone("h", S["pop_orchid"], 52, 19)}</defs>'
        f'<rect width="{W}" height="{H}" fill="url(#h)" mask="url(#m)"/>'
        f'<rect y="{H * 0.58:.0f}" width="{W}" height="{H * 0.42:.0f}" '
        f'fill="{VOID}" opacity="0.55"/>', bg=VOID)


def w06_dot_ramp() -> str:
    """A dot-size ramp — the mechanical screen itself, made the subject."""
    bands = []
    cols = 14
    for i in range(cols):
        r = 4 + i * 2.9
        bands.append(_halftone(f"d{i}", S["pop_aqua"], 44, r))
    rects = []
    for i in range(cols):
        x = i * (W / cols)
        rects.append(f'<rect x="{x:.0f}" y="0" width="{W / cols + 1:.0f}" '
                     f'height="{H}" fill="url(#d{i})"/>')
    return _svg(
        f'<defs>{"".join(bands)}'
        f'{_fade("g", 0, 0, 0, 1, [("0", "#FFFFFF"), ("0.50", "#8C8C8C"), ("0.94", "#000000")])}'
        f'{_mask("m", "g")}</defs>'
        f'<g mask="url(#m)">{"".join(rects)}</g>', bg=VOID)


def w07_hex_pop() -> str:
    """The Core Builds hex, inked. The one wall that carries the mark."""
    mark = popart.inked_mark("core_mark", target=1180.0, outline=86.0)
    return _svg(
        f'<defs>{_halftone("h", shade(S["pop_red"], 0.66), 44, 15)}'
        f'{_fade("g", 0, 0, 0.25, 1, [("0", "#FFFFFF"), ("0.40", "#B4B4B4"), ("0.66", "#2C2C2C"), ("0.84", "#000000")])}'
        f'{_mask("m", "g")}</defs>'
        f'<rect width="{W}" height="{H}" fill="{S["pop_red"]}" mask="url(#m)"/>'
        f'<rect width="{W}" height="{H}" fill="url(#h)" mask="url(#m)"/>'
        f'<g transform="translate({W * 0.5},{H * 0.33})">{mark}</g>')


def w08_screen_tone() -> str:
    """Two mechanical screens crossed at different angles — manga tone."""
    return _svg(
        f'<defs>{_halftone("a", S["pop_marine"], 72, 22, 15)}'
        f'{_halftone("b", S["pop_magenta"], 96, 17, 75)}'
        f'{_fade("g", 0, 0, 0.4, 1, [("0", "#FFFFFF"), ("0.55", "#707070"), ("1", "#000000")])}'
        f'{_mask("m", "g")}</defs>'
        f'<g mask="url(#m)">'
        f'<rect width="{W}" height="{H}" fill="url(#a)"/>'
        f'<rect width="{W}" height="{H}" fill="url(#b)"/></g>', bg=VOID)


def w09_colour_blocks() -> str:
    """Flat blocks with ink keylines, weighted to the top two thirds."""
    blocks = [
        (0.00, 0.00, 0.30, 0.24, S["pop_blue"]),
        (0.30, 0.00, 0.18, 0.40, S["pop_sun"]),
        (0.48, 0.00, 0.30, 0.16, S["pop_green"]),
        (0.78, 0.00, 0.22, 0.32, S["pop_rose"]),
        (0.00, 0.24, 0.20, 0.26, S["pop_jade"]),
        (0.48, 0.16, 0.14, 0.30, S["pop_amber"]),
    ]
    defs, out = [], []
    for n, (x, y, w, h, c) in enumerate(blocks):
        defs.append(_halftone(f"h{n}", shade(c, 0.64), 40, 13))
        px, py, pw, ph = x * W, y * H, w * W, h * H
        out.append(f'<g><rect x="{px:.0f}" y="{py:.0f}" width="{pw:.0f}" '
                   f'height="{ph:.0f}" fill="{c}"/>'
                   f'<rect x="{px:.0f}" y="{py:.0f}" width="{pw:.0f}" '
                   f'height="{ph:.0f}" fill="url(#h{n})"/>'
                   f'<rect x="{px:.0f}" y="{py:.0f}" width="{pw:.0f}" '
                   f'height="{ph:.0f}" fill="none" stroke="{INK}" '
                   f'stroke-width="30"/></g>')
    return _svg(
        f'<defs>{"".join(defs)}'
        f'{_fade("g", 0, 0, 0, 1, [("0", "#FFFFFF"), ("0.44", "#C8C8C8"), ("0.72", "#000000")])}'
        f'{_mask("m", "g")}</defs>'
        f'<g mask="url(#m)">{"".join(out)}</g>')


def w10_zip_ribbon() -> str:
    """One inked ribbon of colour across the upper frame."""
    y = H * 0.30
    ribbon = (f"M -100 {y + 260:.0f} "
              f"C {W * 0.28:.0f} {y - 300:.0f} {W * 0.62:.0f} {y + 520:.0f} "
              f"{W + 100:.0f} {y - 60:.0f} "
              f"L {W + 100:.0f} {y + 400:.0f} "
              f"C {W * 0.62:.0f} {y + 980:.0f} {W * 0.28:.0f} {y + 160:.0f} "
              f"-100 {y + 720:.0f} Z")
    return _svg(
        f'<defs>{_halftone("h", shade(S["pop_grape"], 0.62), 44, 15)}</defs>'
        f'<path d="{ribbon}" fill="{S["pop_grape"]}"/>'
        f'<path d="{ribbon}" fill="url(#h)"/>'
        f'<path d="{ribbon}" fill="none" stroke="{INK}" stroke-width="34"/>',
        bg=VOID)


def w11_sunburst() -> str:
    """Alternating rays from the top edge — the oldest trick in the medium."""
    cx, cy = W * 0.5, -H * 0.10
    wedges = []
    n = 36
    for i in range(0, n, 2):
        a0 = math.pi * i / n
        a1 = math.pi * (i + 1) / n
        r = 4200
        x0, y0 = cx + r * math.cos(a0), cy + r * math.sin(a0)
        x1, y1 = cx + r * math.cos(a1), cy + r * math.sin(a1)
        wedges.append(f'<path d="M {cx:.0f} {cy:.0f} L {x0:.0f} {y0:.0f} '
                      f'L {x1:.0f} {y1:.0f} Z" fill="{S["pop_blaze"]}"/>')
    return _svg(
        f'<defs>{_halftone("h", shade(S["pop_blaze"], 0.6), 46, 16)}'
        f'{_fade("g", 0, 0, 0, 1, [("0", "#FFFFFF"), ("0.26", "#8E8E8E"), ("0.52", "#1E1E1E"), ("0.68", "#000000")])}'
        f'{_mask("m", "g")}</defs>'
        f'<g mask="url(#m)">{"".join(wedges)}'
        f'<rect width="{W}" height="{H}" fill="url(#h)" opacity="0.5"/></g>',
        bg=VOID)


def w12_night_panel() -> str:
    """Almost entirely ink, with one lit panel high and left. The quietest
    wall in the set, and the one that will end up on most home screens."""
    px, py = W * 0.08, H * 0.12
    pw, ph = W * 0.34, H * 0.40
    return _svg(
        f'<defs>{_halftone("h", shade(S["pop_acid"], 0.66), 38, 12)}</defs>'
        f'<rect x="{px:.0f}" y="{py:.0f}" width="{pw:.0f}" height="{ph:.0f}" '
        f'fill="{S["pop_acid"]}"/>'
        f'<rect x="{px:.0f}" y="{py:.0f}" width="{pw:.0f}" height="{ph:.0f}" '
        f'fill="url(#h)"/>'
        f'<rect x="{px:.0f}" y="{py:.0f}" width="{pw:.0f}" height="{ph:.0f}" '
        f'fill="none" stroke="{INK}" stroke-width="30"/>'
        f'<rect x="{px + 44:.0f}" y="{py + 44:.0f}" width="{pw - 88:.0f}" '
        f'height="{ph - 88:.0f}" fill="none" stroke="{CREAM}" '
        f'stroke-width="10" opacity="0.5"/>', bg=VOID)


WALLS = [
    ("01", "benday-dawn", "01 Ben-Day Dawn", w01_benday_dawn),
    ("02", "speed-lines", "02 Speed Lines", w02_speed_lines),
    ("03", "impact-burst", "03 Impact Burst", w03_impact_burst),
    ("04", "panel-grid", "04 Panel Grid", w04_panel_grid),
    ("05", "ink-bleed", "05 Ink Bleed", w05_ink_bleed),
    ("06", "dot-ramp", "06 Dot Ramp", w06_dot_ramp),
    ("07", "hex-pop", "07 Hex Pop", w07_hex_pop),
    ("08", "screen-tone", "08 Screen Tone", w08_screen_tone),
    ("09", "colour-blocks", "09 Colour Blocks", w09_colour_blocks),
    ("10", "zip-ribbon", "10 Zip Ribbon", w10_zip_ribbon),
    ("11", "sunburst", "11 Sunburst", w11_sunburst),
    ("12", "night-panel", "12 Night Panel", w12_night_panel),
]


def dark_coverage(im) -> float:
    """Fraction of pixels at or below night-chrome luminance.

    Reported per wall rather than asserted, because one bright wall in twelve
    is a choice; eight bright walls is a mistake, and this is how you notice.
    """
    import numpy as np
    a = np.asarray(im.convert("L"), dtype="float32") / 255.0
    return float((a <= 0.14).mean())


def main() -> int:
    from PIL import Image

    SERIES.mkdir(parents=True, exist_ok=True)
    THUMBS.mkdir(parents=True, exist_ok=True)
    BUNDLED_THUMBS.mkdir(parents=True, exist_ok=True)
    BUNDLED_MANIFEST.parent.mkdir(parents=True, exist_ok=True)

    entries = []
    total_bytes = 0
    for num, slug, title, fn in WALLS:
        stem = f"corepop-{num}-{slug}"
        png = svg2png(bytestring=fn().encode("utf-8"),
                      output_width=W, output_height=H, background_color=None)
        im = Image.open(io.BytesIO(png)).convert("RGB")

        out = SERIES / f"{stem}.png"
        # Flat art, mechanical screens, no smooth ramps: an indexed palette is
        # lossless here in every way that matters and roughly a tenth the size.
        im.quantize(colors=128, method=Image.FASTOCTREE).save(
            out, "PNG", optimize=True)
        size = out.stat().st_size
        total_bytes += size

        thumb = im.resize((THUMB_W, THUMB_H), Image.LANCZOS)
        thumb.save(THUMBS / f"{stem}.jpg", "JPEG", quality=88, optimize=True)
        shutil.copy2(THUMBS / f"{stem}.jpg", BUNDLED_THUMBS / f"{stem}.jpg")

        entries.append({
            "name": title,
            "series": "series-5-pop",
            "url": f"{RAW}/series-5-pop/{stem}.png",
            "thumb": f"{RAW}/thumbs/{stem}.jpg",
            "resolution": f"{W}x{H}",
        })
        print(f"  \u2713 {stem:34} {size / 1024:7.0f} KB  "
              f"dark {dark_coverage(im):.0%}")

    manifest = {
        "collection": "Core Builds Pop Wallpapers",
        "version": "1.0",
        "author": "brevityA",
        "brand_guide": "Core Builds Pop — 16 locked swatches, ink and cream",
        "count": len(entries),
        "wallpapers": entries,
    }
    text = json.dumps(manifest, indent=2) + "\n"
    MANIFEST.write_text(text, encoding="utf-8")
    BUNDLED_MANIFEST.write_text(text, encoding="utf-8")
    print(f"\u2713 {len(entries)} wallpapers \u00b7 {total_bytes / 1e6:.1f} MB total "
          f"\u2192 Wallpapers/series-5-pop/")
    print(f"\u2713 manifest + {len(entries)} bundled thumbs written "
          f"\u2192 pop/src/main/assets/")

    _sheet()
    return 0


def _sheet() -> None:
    from PIL import Image
    cols, cw, ch = 4, 480, 270
    rows = (len(WALLS) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cw, rows * ch), (13, 17, 23))
    for n, (num, slug, _t, _f) in enumerate(WALLS):
        t = Image.open(THUMBS / f"corepop-{num}-{slug}.jpg").convert("RGB")
        sheet.paste(t, ((n % cols) * cw, (n // cols) * ch))
    sheet.quantize(colors=128, method=Image.FASTOCTREE).save(
        DOCS / "pop-wallpapers.png", "PNG", optimize=True)
    print("\u2713 docs/pop-wallpapers.png written (contact sheet)")


if __name__ == "__main__":
    raise SystemExit(main())
