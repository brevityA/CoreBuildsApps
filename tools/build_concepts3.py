#!/usr/bin/env python3
"""
docs/concepts-3.png — structural directions.

Sets one and two varied surface treatment (bloom, glass, dash, rail) on a
single fixed layout: glyph left, wordmark right. This set varies the
STRUCTURE instead — where the mark sits, whether it is knocked out, whether
there is a wordmark at all.

Rendered at true 320x180 so the comparison is honest; anything that only
works when blown up is not a real option.

Decision aid. Writes nothing into res/.
"""
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glyphs import GLYPHS, lit                      # noqa: E402
import cairosvg                                     # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "concepts-3.png"

W, H = 1280, 720                    # master, downscaled to 320x180 for display
BG, CARD, LINE = "#0d1017", "#151923", "rgba(255,255,255,.06)"
ACCENT, VIOLET, INK, TX2 = "#00d4ff", "#a78bfa", "#E6EDF3", "#8b949e"
SANS = ("-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,"
        "'DejaVu Sans',sans-serif")
MONO = "ui-monospace,'DejaVu Sans Mono',monospace"

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def measure(t, s):
    try:
        from PIL import ImageFont
        return ImageFont.truetype(FONT, s).getlength(t)
    except Exception:
        return len(t) * s * 0.58


def hexpts(cx, cy, r):
    import math
    return " ".join(f"{cx + r * math.cos(math.radians(60 * i - 90)):.1f},"
                    f"{cy + r * math.sin(math.radians(60 * i - 90)):.1f}"
                    for i in range(6))


def txt(x, y, t, size, fill=INK, fam=SANS, w="700", track=-1.5, anchor="start"):
    return (f'<text x="{x:.0f}" y="{y:.0f}" fill="{fill}" font-family="{fam}" '
            f'font-weight="{w}" font-size="{size}" letter-spacing="{track}" '
            f'text-anchor="{anchor}">{t}</text>')


def fit(t, budget, hi=132, lo=44):
    s = hi
    while s > lo and measure(t, s) > budget:
        s -= 2
    return s


# ---------------------------------------------------------------- J
def J(name, g, c, cat):
    """J — Stacked centre. Mark over name, both centred. No side lockup."""
    box = 300
    s = fit(name, W * 0.86, 104)
    return (f'<polygon points="{hexpts(W/2, 250, 172)}" fill="{c}" '
            f'fill-opacity=".10" stroke="{c}" stroke-opacity=".42" '
            f'stroke-width="7" stroke-linejoin="round"/>'
            f'<g transform="translate({W/2 - box/2:.0f},{250 - box/2:.0f}) '
            f'scale({box/512:.5f})">{lit(GLYPHS[g](c), c)}</g>'
            f'{txt(W/2, 520, name, s, anchor="middle")}'
            f'{txt(W/2, 590, cat, 44, ACCENT, MONO, "700", 7, "middle")}')


# ---------------------------------------------------------------- K
def K(name, g, c, cat):
    """K — Solid hex, glyph knocked out. Loudest possible mark."""
    box = 250
    s = fit(name, W - 560, 124)
    return (f'<polygon points="{hexpts(300, H/2, 210)}" fill="{c}"/>'
            f'<g transform="translate({300 - box/2:.0f},{H/2 - box/2:.0f}) '
            f'scale({box/512:.5f})">{GLYPHS[g](BG)}</g>'
            f'{txt(520, H/2 + s*0.36, name, s)}')


# ---------------------------------------------------------------- L
def L(name, g, c, cat):
    """L — No wordmark. Mark only; the launcher draws its own label."""
    box = 400
    return (f'<polygon points="{hexpts(W/2, H/2, 268)}" fill="{c}" '
            f'fill-opacity=".10" stroke="{c}" stroke-opacity=".45" '
            f'stroke-width="9" stroke-linejoin="round"/>'
            f'<g transform="translate({W/2 - box/2:.0f},{H/2 - box/2:.0f}) '
            f'scale({box/512:.5f})">{lit(GLYPHS[g](c), c)}</g>')


# ---------------------------------------------------------------- M
def M(name, g, c, cat):
    """M — All-caps track. Name in wide caps under a small mark."""
    box = 190
    up = name.upper()
    s = fit(up, W * 0.80, 92)
    return (f'<g transform="translate({W/2 - box/2:.0f},170) '
            f'scale({box/512:.5f})">{lit(GLYPHS[g](c), c)}</g>'
            f'{txt(W/2, 470, up, s, INK, SANS, "700", 6, "middle")}'
            f'<rect x="{W/2 - 120:.0f}" y="530" width="240" height="5" rx="2.5" '
            f'fill="{c}" fill-opacity=".7"/>')


# ---------------------------------------------------------------- N
def N(name, g, c, cat):
    """N — Duotone. Brand cyan rail + app-accent mark, name in accent."""
    box = 300
    s = fit(name, W - 620, 120)
    return (f'<defs><linearGradient id="dt" x1="0" y1="0" x2="0" y2="1">'
            f'<stop offset="0%" stop-color="{ACCENT}"/>'
            f'<stop offset="100%" stop-color="{VIOLET}"/></linearGradient></defs>'
            f'<rect x="72" y="150" width="18" height="{H-300}" rx="9" '
            f'fill="url(#dt)"/>'
            f'<g transform="translate(170,{H/2 - box/2:.0f}) '
            f'scale({box/512:.5f})">{lit(GLYPHS[g](c), c)}</g>'
            f'{txt(520, H/2 - 6, name, s, c)}'
            f'{txt(520, H/2 + 74, cat, 46, TX2, MONO, "700", 7)}')


# ---------------------------------------------------------------- O
def O(name, g, c, cat):
    """O — Full-bleed mark, name overlaid bottom-left. Editorial."""
    box = 460
    s = fit(name, W * 0.62, 116)
    return (f'<g transform="translate({W - box - 40:.0f},{H/2 - box/2:.0f}) '
            f'scale({box/512:.5f})" opacity=".22">{GLYPHS[g](c)}</g>'
            f'<rect x="70" y="{H/2 - 96:.0f}" width="14" height="192" rx="7" '
            f'fill="{c}"/>'
            f'{txt(120, H/2 - 14, name, s)}'
            f'{txt(120, H/2 + 62, cat, 46, ACCENT, MONO, "700", 7)}')


CONCEPTS = [
    ("J · Stacked centre",
     "mark over name, both centred — no side lockup. Reads like a tile.", J),
    ("K · Solid knockout",
     "filled hex, glyph cut out of it. Loudest; highest contrast at distance.", K),
    ("L · Mark only",
     "no wordmark at all — Projectivy already draws the app name under the card.", L),
    ("M · Caps track",
     "small mark, wide-tracked caps, accent underline. Editorial and calm.", M),
    ("N · Duotone",
     "brand cyan→violet rail, app-accent mark AND name. Most colourful.", N),
    ("O · Full bleed",
     "oversized ghosted mark right, name overlaid left. Most magazine-like.", O),
]

SAMPLES = ["Stremio", "Netflix", "TorBox", "Kodi"]


def main():
    cat = json.loads((ROOT / "tools" / "catalog.json").read_text())
    by = {i["name"]: i for i in cat["icons"]}
    picks = [(n, by[n]["glyph"], by[n]["color"], by[n].get("category", ""))
             for n in SAMPLES if n in by]

    CW, CH = 320, 180
    COLS, GAP, M_, HDR, ROWH = len(picks), 16, 40, 140, 268
    SW = M_ * 2 + COLS * CW + (COLS - 1) * GAP
    SH = HDR + len(CONCEPTS) * ROWH

    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{SW}" height="{SH}" '
         f'viewBox="0 0 {SW} {SH}">',
         f'<rect width="{SW}" height="{SH}" fill="{BG}"/>',
         f'<text x="{M_}" y="52" fill="{ACCENT}" font-family="{MONO}" '
         f'font-size="14" letter-spacing="2.4">CORE BUILDS · ICON PACK</text>',
         f'<text x="{M_}" y="96" fill="{INK}" font-family="{SANS}" '
         f'font-weight="700" font-size="34">Structural directions</text>',
         f'<text x="{M_}" y="122" fill="{TX2}" font-family="{MONO}" '
         f'font-size="13">layout, not chrome · shown at true 320\u00d7180</text>']

    y = HDR
    for title, blurb, fn in CONCEPTS:
        s.append(f'<text x="{M_}" y="{y + 20}" fill="{INK}" font-family="{SANS}" '
                 f'font-weight="700" font-size="22">{title}</text>')
        s.append(f'<text x="{M_}" y="{y + 44}" fill="{TX2}" font-family="{MONO}" '
                 f'font-size="12">{blurb}</text>')
        for k, (n, g, c, ct) in enumerate(picks):
            x = M_ + k * (CW + GAP)
            s.append(f'<rect x="{x}" y="{y + 60}" width="{CW}" height="{CH}" '
                     f'rx="10" fill="{CARD}" stroke="{LINE}"/>')
            s.append(f'<svg x="{x}" y="{y + 60}" width="{CW}" height="{CH}" '
                     f'viewBox="0 0 {W} {H}">{fn(n, g, c, ct)}</svg>')
        y += ROWH

    s.append('</svg>')
    OUT.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring="\n".join(s).encode(), write_to=str(OUT),
                     output_width=SW, output_height=SH, background_color=BG)
    print(f"\u2713 docs/concepts-3.png written ({SW}\u00d7{SH})")


if __name__ == "__main__":
    main()
