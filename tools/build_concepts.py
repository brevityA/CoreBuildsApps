#!/usr/bin/env python3
"""
Renders docs/concepts.png — four icon directions, each built from tokens
scraped from the live configurator (brevitya.github.io/Core-Builds/configurator).

Tokens used (verbatim from its CSS):
    --th-bg        #0d1017      --th-card       #151923
    --th-card-alt  #111720      --ui-raised     #121c29
    --th-accent    #00d4ff      --th-purple     #a78bfa
    --th-border    rgba(255,255,255,.06)
    radius         8 / 10 / 12px (its three most-used values)
    CTA gradient   linear-gradient(135deg,#0891b2,#00d4ff)   (most used, x5)
    panel gradient linear-gradient(145deg,#111b27,#0d151f)
    glow           0 6px 28px rgba(0,212,255,.35)

This is a decision aid, not a build step — nothing here writes into res/.
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glyphs import GLYPHS, lit          # noqa: E402
import cairosvg                          # noqa: E402
from pathlib import Path                 # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "concepts.png"

BG, CARD, RAISED = "#0d1017", "#151923", "#121c29"
ACCENT, LINE, TX, TX2 = "#00d4ff", "rgba(255,255,255,.06)", "#e4e7ed", "#8b949e"

def _sample():
    """Pull glyph + colour from the catalogue so this can never drift."""
    import json
    cat = json.loads((ROOT / "tools" / "catalog.json").read_text())
    by = {i["name"]: i for i in cat["icons"]}
    return [(by[n]["glyph"], n, by[n]["color"])
            for n in ("Stremio", "Kodi", "Spotify", "YouTube") if n in by]


SAMPLE = _sample()

CW, CH, SC = 320, 180, 320 / 1280


def defs():
    return (
        '<defs>'
        '<linearGradient id="cta" x1="0" y1="0" x2="1" y2="1">'
        '<stop offset="0%" stop-color="#0891b2"/><stop offset="100%" stop-color="#00d4ff"/>'
        '</linearGradient>'
        '<linearGradient id="panel" x1="0" y1="0" x2="0.5" y2="1">'
        '<stop offset="0%" stop-color="#111b27"/><stop offset="100%" stop-color="#0d151f"/>'
        '</linearGradient>'
        '<linearGradient id="sweep" x1="0" y1="0" x2="1" y2="0">'
        f'<stop offset="0%" stop-color="{ACCENT}" stop-opacity=".55"/>'
        f'<stop offset="100%" stop-color="{ACCENT}" stop-opacity="0"/>'
        '</linearGradient>'
        '<filter id="soft" x="-40%" y="-40%" width="180%" height="180%">'
        '<feGaussianBlur stdDeviation="16"/></filter>'
        # corner blooms, verbatim from the configurator:
        #   radial cyan at 100% 0 · radial violet at 0% 100%
        '<radialGradient id="bloomC" cx="100%" cy="0%" r="70%">'
        f'<stop offset="0%" stop-color="{ACCENT}" stop-opacity=".13"/>'
        f'<stop offset="45%" stop-color="{ACCENT}" stop-opacity="0"/>'
        '</radialGradient>'
        '<radialGradient id="bloomV" cx="0%" cy="100%" r="70%">'
        '<stop offset="0%" stop-color="#a78bfa" stop-opacity=".09"/>'
        '<stop offset="45%" stop-color="#a78bfa" stop-opacity="0"/>'
        '</radialGradient>'
        '<linearGradient id="rail" x1="0" y1="0" x2="0" y2="1">'
        f'<stop offset="0%" stop-color="{ACCENT}"/>'
        '<stop offset="100%" stop-color="#a78bfa"/>'
        '</linearGradient>'
        '</defs>')


def wm(x, y, text, size=30, fill=TX, weight="700", anchor="start", op=1):
    return (f'<text x="{x}" y="{y}" fill="{fill}" fill-opacity="{op}" '
            f'font-family="-apple-system,BlinkMacSystemFont,\'Segoe UI\',Roboto,'
            f'\'DejaVu Sans\',sans-serif" font-weight="{weight}" '
            f'font-size="{size}" text-anchor="{anchor}">{text}</text>')


def card_A(x, y, g, name, col):
    """A — Flat card. --th-card ground, 10px radius, hairline border."""
    return (f'<g transform="translate({x},{y})">'
            f'<rect width="{CW}" height="{CH}" rx="10" fill="{CARD}" '
            f'stroke="{LINE}"/>'
            f'<g transform="translate(30,42) scale({96/512:.4f})">{lit(GLYPHS[g](col), col)}</g>'
            f'{wm(150, 104, name, 30)}</g>')


def card_B(x, y, g, name, col):
    """B — Panel gradient + cyan sweep rule (the configurator's section head)."""
    return (f'<g transform="translate({x},{y})">'
            f'<rect width="{CW}" height="{CH}" rx="12" fill="url(#panel)" '
            f'stroke="{LINE}"/>'
            f'<rect x="0" y="0" width="{CW}" height="3" rx="1.5" fill="url(#sweep)"/>'
            f'<g transform="translate(30,44) scale({92/512:.4f})">{lit(GLYPHS[g](col), col)}</g>'
            f'{wm(146, 100, name, 29)}'
            f'{wm(146, 126, "CORE", 13, TX2, "700")}</g>')


def card_C(x, y, g, name, col):
    """C — Glyph on a CTA-gradient chip, the configurator's primary action."""
    return (f'<g transform="translate({x},{y})">'
            f'<rect width="{CW}" height="{CH}" rx="10" fill="{CARD}" stroke="{LINE}"/>'
            f'<rect x="26" y="46" width="88" height="88" rx="12" fill="url(#cta)"/>'
            f'<g transform="translate(44,64) scale({52/512:.4f})">{GLYPHS[g]("#04202b")}</g>'
            f'{wm(136, 100, name, 29)}</g>')


def card_D(x, y, g, name, col):
    """D — Glow-forward: raised tile, big halo, no chrome. §02's lit look."""
    return (f'<g transform="translate({x},{y})">'
            f'<rect width="{CW}" height="{CH}" rx="8" fill="{RAISED}"/>'
            f'<ellipse cx="76" cy="90" rx="58" ry="46" fill="{col}" '
            f'opacity=".22" filter="url(#soft)"/>'
            f'<g transform="translate(30,44) scale({92/512:.4f})">{lit(GLYPHS[g](col), col, 18, .5)}</g>'
            f'{wm(146, 102, name, 30)}</g>')


CONCEPTS = [
    ("A · Flat card", "--th-card ground · 10px radius · hairline border. "
     "Closest to the configurator's default surface.", card_A),
    ("B · Panel + sweep", "145deg panel gradient · cyan sweep rule · CORE kicker. "
     "Mirrors its section headers.", card_B),
    ("C · CTA chip", "Glyph reversed out of the 135deg install gradient. "
     "Loudest; reads as an action.", card_C),
    ("D · Glow-forward", "--ui-raised tile · large accent halo · no border. "
     "The §02 lit look, pushed.", card_D),
]



# --------------------------------------------------------------------------
# Set two — drawn from tokens the first pass did not use: the corner blooms
# (radial cyan 100% 0 + violet 0% 100%), the dashed violet border, the
# uppercase mono kicker at .08em, and the brand's point-up hexagon (§02).
# --------------------------------------------------------------------------

def card_E(x, y, g, name, col):
    """E - Corner bloom. The configurator's hero card ground, verbatim."""
    return (f'<g transform="translate({x},{y})">'
            f'<rect width="{CW}" height="{CH}" rx="12" fill="{CARD}" stroke="{LINE}"/>'
            f'<rect width="{CW}" height="{CH}" rx="12" fill="url(#bloomC)"/>'
            f'<rect width="{CW}" height="{CH}" rx="12" fill="url(#bloomV)"/>'
            f'<g transform="translate(30,44) scale({92/512:.4f})">{lit(GLYPHS[g](col), col)}</g>'
            f'{wm(146, 100, name, 29)}</g>')


def card_F(x, y, g, name, col):
    """F - Hex host. The brand container from §02, point-up, never rotated."""
    import math
    cx0, cy0, r = 76, 90, 52
    pts = " ".join(f"{cx0 + r * math.cos(math.radians(60 * i - 90)):.1f},"
                   f"{cy0 + r * math.sin(math.radians(60 * i - 90)):.1f}"
                   for i in range(6))
    return (f'<g transform="translate({x},{y})">'
            f'<rect width="{CW}" height="{CH}" rx="10" fill="{CARD}" stroke="{LINE}"/>'
            f'<polygon points="{pts}" fill="{col}" fill-opacity=".10" '
            f'stroke="{col}" stroke-opacity=".55" stroke-width="2"/>'
            f'<g transform="translate(46,60) scale({60/512:.4f})">{GLYPHS[g](col)}</g>'
            f'{wm(150, 100, name, 28)}</g>')


def card_G(x, y, g, name, col):
    """G - Glass. Translucent panel + strong border; its blur(12px) surfaces."""
    return (f'<g transform="translate({x},{y})">'
            f'<rect width="{CW}" height="{CH}" rx="12" fill="#0e151f" '
            f'fill-opacity=".88" stroke="rgba(255,255,255,.15)"/>'
            f'<rect x="1" y="1" width="{CW-2}" height="34" rx="11" '
            f'fill="#ffffff" fill-opacity=".04"/>'
            f'<g transform="translate(30,50) scale({88/512:.4f})">{lit(GLYPHS[g](col), col)}</g>'
            f'{wm(142, 102, name, 28)}</g>')


def card_H(x, y, g, name, col):
    """H - Kicker rail. Vertical cyan->violet rail + uppercase mono kicker."""
    return (f'<g transform="translate({x},{y})">'
            f'<rect width="{CW}" height="{CH}" rx="10" fill="{CARD}" stroke="{LINE}"/>'
            f'<rect x="0" y="26" width="4" height="{CH-52}" rx="2" fill="url(#rail)"/>'
            f'<g transform="translate(34,44) scale({86/512:.4f})">{lit(GLYPHS[g](col), col)}</g>'
            f'<text x="146" y="84" fill="{ACCENT}" font-family="monospace" '
            f'font-size="12" letter-spacing="1.9">STREAM</text>'
            f'{wm(146, 118, name, 28)}</g>')


def card_I(x, y, g, name, col):
    """I - Dashed frame. Their 1.5px dashed violet, used for optional lanes."""
    return (f'<g transform="translate({x},{y})">'
            f'<rect width="{CW}" height="{CH}" rx="10" fill="{CARD}"/>'
            f'<rect x="10" y="10" width="{CW-20}" height="{CH-20}" rx="8" '
            f'fill="none" stroke="rgba(167,139,250,.3)" stroke-width="1.5" '
            f'stroke-dasharray="7 6"/>'
            f'<g transform="translate(38,48) scale({86/512:.4f})">{lit(GLYPHS[g](col), col)}</g>'
            f'{wm(150, 100, name, 28)}</g>')


SET_TWO = [
    ("E \u00b7 Corner bloom", "radial cyan at 100% 0 + violet at 0% 100% \u2014 "
     "the configurator's hero ground, verbatim.", card_E),
    ("F \u00b7 Hex host", "the \u00a702 point-up hexagon as container. Most "
     "unmistakably Core Builds.", card_F),
    ("G \u00b7 Glass", "translucent --ui-panel + strong border + top sheen. "
     "Its blur(12px) surfaces.", card_G),
    ("H \u00b7 Kicker rail", "cyan\u2192violet rail + uppercase mono kicker at "
     ".08em tracking.", card_H),
    ("I \u00b7 Dashed frame", "1.5px dashed violet \u2014 its 'optional lane' "
     "treatment. Lightest touch.", card_I),
]


def main():
    import sys as _sys
    two = "--set2" in _sys.argv
    rows = SET_TWO if two else CONCEPTS
    out = (ROOT / "docs" / ("concepts-2.png" if two else "concepts.png"))
    M, GAP, ROWH = 48, 20, 300
    W = M * 2 + 4 * CW + 3 * GAP
    H = 130 + len(rows) * ROWH
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
         f'viewBox="0 0 {W} {H}">', f'<rect width="{W}" height="{H}" fill="{BG}"/>',
         defs(),
         f'<text x="{M}" y="52" fill="{ACCENT}" font-family="monospace" '
         f'font-size="14" letter-spacing="2.4">CORE BUILDS · ICON PACK</text>',
         wm(M, 94, "More directions" if two else "Four directions", 36),
         f'<text x="{M}" y="118" fill="{TX2}" font-family="monospace" '
         f'font-size="13">built from live configurator tokens · v2.95</text>']
    y = 150
    for title, blurb, fn in rows:
        s.append(wm(M, y + 22, title, 24))
        s.append(f'<text x="{M}" y="{y + 46}" fill="{TX2}" font-family="monospace" '
                 f'font-size="12.5">{blurb}</text>')
        for k, (g, n, c) in enumerate(SAMPLE):
            s.append(fn(M + k * (CW + GAP), y + 66, g, n, c))
        y += ROWH
    s.append('</svg>')
    out.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring="\n".join(s).encode(), write_to=str(out),
                     output_width=W, output_height=H, background_color=BG)
    print(f"\u2713 docs/{out.name} written ({W}\u00d7{H})")


if __name__ == "__main__":
    main()
