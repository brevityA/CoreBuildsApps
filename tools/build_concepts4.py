#!/usr/bin/env python3
"""
docs/concepts-4.png — icons drawn as Core Builds UI components.

Sets 1-3 borrowed the site's *surfaces* and then its *layouts*. This set
borrows its COMPONENT GRAMMAR from Brand Guide §05, treating each card as a
piece of the product's own interface:

    the pill / truth chip     "✓ verified" — every claim gets a chip
    the stepper (watch-me)    3px segments · lit = cyan + glow
    the receipt row           mono values · status icon first
    the ghost                 #101923 · border .11 · never visually heavy
    the primary CTA           135deg gradient · ink #04202b · radius 11

The argument for this set: a Core Builds icon pack should look like it was cut
out of the Core Builds product, not merely painted in its colours.

Rendered at true 320x180. Decision aid; writes nothing into res/.
"""
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glyphs import GLYPHS, lit                      # noqa: E402
import cairosvg                                     # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "concepts-4.png"

W, H = 1280, 720
BG, CARD, LINE = "#0d1017", "#151923", "rgba(255,255,255,.06)"
ACCENT, VIOLET, INK, TX2 = "#00d4ff", "#a78bfa", "#E6EDF3", "#8b949e"
GHOST_BG, GHOST_BD, GHOST_TX = "#101923", "rgba(255,255,255,.11)", "#8492a3"
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


def fit(t, budget, hi=132, lo=44):
    s = hi
    while s > lo and measure(t, s) > budget:
        s -= 2
    return s


def txt(x, y, t, size, fill=INK, fam=SANS, w="700", track=-1.5, anchor="start"):
    return (f'<text x="{x:.0f}" y="{y:.0f}" fill="{fill}" font-family="{fam}" '
            f'font-weight="{w}" font-size="{size}" letter-spacing="{track}" '
            f'text-anchor="{anchor}">{t}</text>')


# ---------------------------------------------------------------- P
def P(name, g, c, cat):
    """P — Truth chip. §05's pill: the category becomes a real chip."""
    box = 268
    s = fit(name, W - 620, 116)
    cw = measure(cat, 42) + 76
    return (f'<g transform="translate(120,{H/2 - box/2:.0f}) '
            f'scale({box/512:.5f})">{lit(GLYPHS[g](c), c)}</g>'
            f'<rect x="470" y="{H/2 - 132:.0f}" width="{cw:.0f}" height="76" '
            f'rx="38" fill="{c}" fill-opacity=".12" stroke="{c}" '
            f'stroke-opacity=".45" stroke-width="3"/>'
            f'<circle cx="512" cy="{H/2 - 94:.0f}" r="11" fill="{c}"/>'
            f'{txt(540, H/2 - 80, cat, 42, c, MONO, "700", 6)}'
            f'{txt(470, H/2 + 74, name, s)}')


# ---------------------------------------------------------------- Q
def Q(name, g, c, cat):
    """Q — Stepper. §05's 3px lit segments as a progress bar under the name."""
    box = 258
    s = fit(name, W - 600, 112)
    segs = ""
    for i in range(5):
        x = 470 + i * 108
        on = i < 3
        segs += (f'<rect x="{x}" y="{H/2 + 92:.0f}" width="88" height="12" '
                 f'rx="6" fill="{c if on else "#ffffff"}" '
                 f'fill-opacity="{".95" if on else ".10"}"/>')
    return (f'<g transform="translate(120,{H/2 - box/2:.0f}) '
            f'scale({box/512:.5f})">{lit(GLYPHS[g](c), c)}</g>'
            f'{txt(470, H/2 + 26, name, s)}'
            f'{segs}')


# ---------------------------------------------------------------- R
def R(name, g, c, cat):
    """R — Receipt row. §05: mono values, status icon first."""
    box = 200
    return (f'<rect x="70" y="120" width="{W-140}" height="480" rx="20" '
            f'fill="#0b1119" stroke="{LINE}"/>'
            f'<g transform="translate(120,{H/2 - box/2:.0f}) '
            f'scale({box/512:.5f})">{GLYPHS[g](c)}</g>'
            f'<path d="M 372 {H/2-8:.0f} l 26 28 l 52 -60" fill="none" '
            f'stroke="#34d399" stroke-width="14" stroke-linecap="round" '
            f'stroke-linejoin="round"/>'
            f'{txt(486, H/2 - 8, name, 94, INK, MONO, "700", -2)}'
            f'{txt(486, H/2 + 74, cat.lower(), 46, TX2, MONO, "400", 2)}')


# ---------------------------------------------------------------- S
def S(name, g, c, cat):
    """S — Ghost. §05's secondary surface: light, bordered, never heavy."""
    box = 240
    s = fit(name, W - 600, 108)
    return (f'<rect x="70" y="130" width="{W-140}" height="460" rx="28" '
            f'fill="{GHOST_BG}" stroke="{GHOST_BD}" stroke-width="4"/>'
            f'<g transform="translate(130,{H/2 - box/2:.0f}) '
            f'scale({box/512:.5f})">{GLYPHS[g](c)}</g>'
            f'{txt(450, H/2 + 12, name, s, GHOST_TX)}'
            f'{txt(450, H/2 + 78, cat, 40, TX2, MONO, "700", 6)}')


# ---------------------------------------------------------------- T
def T(name, g, c, cat):
    """T — CTA slab. §05's primary action: gradient block, dark ink on it."""
    s = fit(name, W - 560, 120)
    return (f'<defs><linearGradient id="cta4" x1="0" y1="0" x2="1" y2="1">'
            f'<stop offset="0%" stop-color="#00abd3"/>'
            f'<stop offset="100%" stop-color="#00d4ff"/></linearGradient></defs>'
            f'<rect x="70" y="150" width="330" height="420" rx="26" '
            f'fill="url(#cta4)"/>'
            f'<g transform="translate(120,{H/2 - 115:.0f}) '
            f'scale({230/512:.5f})">{GLYPHS[g]("#04202b")}</g>'
            f'{txt(450, H/2 - 4, name, s)}'
            f'{txt(450, H/2 + 70, cat, 42, ACCENT, MONO, "700", 6)}')


# ---------------------------------------------------------------- U
def U(name, g, c, cat):
    """U — Corner stamp. Mark large left, tiny hex version stamped top-right."""
    import math
    box = 330
    s = fit(name, W - 620, 118)
    pts = " ".join(f"{1150 + 46 * math.cos(math.radians(60*i - 90)):.1f},"
                   f"{104 + 46 * math.sin(math.radians(60*i - 90)):.1f}"
                   for i in range(6))
    return (f'<g transform="translate(110,{H/2 - box/2:.0f}) '
            f'scale({box/512:.5f})">{lit(GLYPHS[g](c), c)}</g>'
            f'{txt(500, H/2 + 16, name, s)}'
            f'{txt(500, H/2 + 82, cat, 42, TX2, MONO, "700", 6)}'
            f'<polygon points="{pts}" fill="none" stroke="{ACCENT}" '
            f'stroke-opacity=".55" stroke-width="6" stroke-linejoin="round"/>'
            f'<polygon points="1150,84 1170,104 1150,124 1130,104" '
            f'fill="{ACCENT}" fill-opacity=".9"/>')


CONCEPTS = [
    ("P · Truth chip", "\u00a705's pill — the category becomes a real chip "
     "with a status dot. Most product-native.", P),
    ("Q · Stepper", "\u00a705's 3px lit segments under the name. Suggests "
     "state; decorative here unless wired to something.", Q),
    ("R · Receipt row", "\u00a705's receipt — mono name, status icon first, "
     "on a sunken panel. Most 'Core Builds'.", R),
    ("S · Ghost", "\u00a705's secondary surface: bordered, muted ink, "
     "deliberately never heavy.", S),
    ("T · CTA slab", "\u00a705's primary action as the mark's host. "
     "Loud, but spends the reserved gradient.", T),
    ("U · Corner stamp", "app mark leads; a small lit hex signs the "
     "corner. Brand present, never competing.", U),
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
         f'font-weight="700" font-size="34">Component grammar</text>',
         f'<text x="{M_}" y="122" fill="{TX2}" font-family="{MONO}" '
         f'font-size="13">icons drawn as \u00a705 UI components \u00b7 true '
         f'320\u00d7180</text>']

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
    print(f"\u2713 docs/concepts-4.png written ({SW}\u00d7{SH})")


if __name__ == "__main__":
    main()
