#!/usr/bin/env python3
"""
docs/concepts-5.png — banner styles from research, in Core Builds colours.

Sources:
  * developer.android.com TV app icon guidelines
      - banner is 16:9, "show your full logo, icon + text"
      - "Do not spill the logo out of safe area"
      - "Avoid adding any border around the logo as they get cropped
         and create unpolished visuals"
      - density set: 160x90 / 240x135 / 320x180 / 480x270 / 640x360
  * Android TV design writing (Medium/Exploring Android)
      - avoid thin or light font faces; TVs vary in contrast and sharpness
      - light text on dark ground reads better than the reverse
      - avoid large areas of pure white or highly saturated fill
  * Current icon-pack market taxonomy (Play Store / roundups 2025-26)
      - glassmorphism, neon glow, duotone, outline/monoline,
        Material You, claymorphism/3D, long shadow

Each row below is one of those market styles rendered in the Core Builds
palette, so the choice is between documented styles rather than moods.

Decision aid. Writes nothing into res/.
"""
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glyphs import GLYPHS                            # noqa: E402
import cairosvg                                      # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "concepts-5.png"

W, H = 1280, 720
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


def fit(t, budget, hi=124, lo=48):
    s = hi
    while s > lo and measure(t, s) > budget:
        s -= 2
    return s


def txt(x, y, t, size, fill=INK, fam=SANS, w="700", track=-1.5, anchor="start"):
    return (f'<text x="{x:.0f}" y="{y:.0f}" fill="{fill}" font-family="{fam}" '
            f'font-weight="{w}" font-size="{size}" letter-spacing="{track}" '
            f'text-anchor="{anchor}">{t}</text>')


def lockup(inner, name, cat, c, gx=130, box=280, tx=470, kick=ACCENT):
    """Shared 16:9 lockup: mark left, name + kicker right (Google: icon+text)."""
    s = fit(name, W - tx - 110, 120)
    return (f'<g transform="translate({gx},{H/2 - box/2:.0f}) '
            f'scale({box/512:.5f})">{inner}</g>'
            f'{txt(tx, H/2 + 10, name, s)}'
            f'{txt(tx, H/2 + 78, cat, 42, kick, MONO, "700", 6)}')


# ------------------------------------------------------------------ V
def V(name, g, c, cat):
    """V — Neon glow. The market's dominant 'neon' pack style, cyan-first."""
    body = GLYPHS[g](c)
    return ('<defs><filter id="nG" x="-60%" y="-60%" width="220%" height="220%">'
            '<feGaussianBlur stdDeviation="22"/></filter></defs>'
            + lockup(f'<g filter="url(#nG)" opacity=".85">{body}</g>'
                     f'<g filter="url(#nG)" opacity=".5">{body}</g>{body}',
                     name, cat, c))


# ------------------------------------------------------------------ W
def W_(name, g, c, cat):
    """W — Duotone. Brand cyan under-layer, app accent on top, offset."""
    body_a = GLYPHS[g](ACCENT)
    body_b = GLYPHS[g](c)
    return lockup(f'<g transform="translate(26,26)" opacity=".55">{body_a}</g>'
                  f'{body_b}', name, cat, c)


# ------------------------------------------------------------------ X
def X(name, g, c, cat):
    """X — Long shadow. Classic icon-pack idiom, 45deg into the accent."""
    body = GLYPHS[g](c)
    sh = "".join(f'<g transform="translate({i*3},{i*3})" opacity=".030">'
                 f'{GLYPHS[g](VIOLET)}</g>' for i in range(1, 26))
    return lockup(sh + body, name, cat, c)


# ------------------------------------------------------------------ Y
def Y(name, g, c, cat):
    """Y — Soft volume (claymorphism). Raised tile, light from top-left."""
    return ('<defs>'
            '<linearGradient id="clay" x1="0" y1="0" x2="0.4" y2="1">'
            '<stop offset="0%" stop-color="#1b2534"/>'
            '<stop offset="100%" stop-color="#0f1620"/></linearGradient>'
            '<filter id="clayS" x="-40%" y="-40%" width="180%" height="180%">'
            '<feDropShadow dx="0" dy="10" stdDeviation="14" '
            'flood-color="#000" flood-opacity=".55"/></filter>'
            '</defs>'
            f'<g filter="url(#clayS)"><rect x="112" y="{H/2-150:.0f}" '
            f'width="300" height="300" rx="86" fill="url(#clay)"/></g>'
            f'<rect x="126" y="{H/2-136:.0f}" width="272" height="136" rx="76" '
            f'fill="#ffffff" fill-opacity=".045"/>'
            + lockup(GLYPHS[g](c), name, cat, c, gx=182, box=180, tx=470))


# ------------------------------------------------------------------ Z
def Z(name, g, c, cat):
    """Z — Tonal container (Material You idiom). Accent-tinted rounded box."""
    return (f'<rect x="112" y="{H/2-150:.0f}" width="300" height="300" rx="96" '
            f'fill="{c}" fill-opacity=".16"/>'
            + lockup(GLYPHS[g](c), name, cat, c, gx=182, box=180, tx=470))


# ------------------------------------------------------------------ AA
def AA(name, g, c, cat):
    """AA — Monoline. Uniform stroke, no fill, no glow. Calmest and lightest."""
    body = GLYPHS[g](c)
    return lockup(body, name, cat, c, box=300)


CONCEPTS = [
    ("V \u00b7 Neon glow",
     "market's dominant neon idiom \u2014 stacked blurs. Brightest; heaviest file.", V),
    ("W \u00b7 Duotone offset",
     "brand cyan under-layer offset behind the app accent. Two colours, one mark.", W_),
    ("X \u00b7 Long shadow",
     "classic pack idiom, 45\u00b0 into violet. Adds depth without a container.", X),
    ("Y \u00b7 Soft volume",
     "claymorphism: raised tile, top-left light. Warmest; least flat.", Y),
    ("Z \u00b7 Tonal container",
     "Material You idiom \u2014 accent-tinted box, no stroke. No border to crop.", Z),
    ("AA \u00b7 Monoline",
     "uniform stroke, no glow, no host. Google's 'no border' advice, literally.", AA),
]

SAMPLES = ["Stremio", "Netflix", "TorBox", "Kodi"]


def main():
    cat = json.loads((ROOT / "tools" / "catalog.json").read_text())
    by = {i["name"]: i for i in cat["icons"]}
    picks = [(n, by[n]["glyph"], by[n]["color"], by[n].get("category", ""))
             for n in SAMPLES if n in by]

    CW, CH = 320, 180
    COLS, GAP, M_, HDR, ROWH = len(picks), 16, 40, 156, 268
    SW = M_ * 2 + COLS * CW + (COLS - 1) * GAP
    SH = HDR + len(CONCEPTS) * ROWH

    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{SW}" height="{SH}" '
         f'viewBox="0 0 {SW} {SH}">',
         f'<rect width="{SW}" height="{SH}" fill="{BG}"/>',
         f'<text x="{M_}" y="52" fill="{ACCENT}" font-family="{MONO}" '
         f'font-size="14" letter-spacing="2.4">CORE BUILDS · ICON PACK</text>',
         f'<text x="{M_}" y="96" fill="{INK}" font-family="{SANS}" '
         f'font-weight="700" font-size="34">Researched banner styles</text>',
         f'<text x="{M_}" y="122" fill="{TX2}" font-family="{MONO}" '
         f'font-size="13">market idioms in the Core Builds palette · '
         f'true 320\u00d7180</text>',
         f'<text x="{M_}" y="142" fill="{TX2}" font-family="{MONO}" '
         f'font-size="12">Google TV: 16:9 · full logo = icon + text · '
         f'keep inside safe area · avoid borders</text>']

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
    print(f"\u2713 docs/concepts-5.png written ({SW}\u00d7{SH})")


if __name__ == "__main__":
    main()
