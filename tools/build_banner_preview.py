#!/usr/bin/env python3
"""
Renders docs/banner-preview.png — every 16:9 banner on night chrome, plus a
mock of how a Projectivy row actually looks with banner cards.

Composition only; generates no new artwork.
"""
import base64
import json
from pathlib import Path

import cairosvg

ROOT = Path(__file__).resolve().parent.parent
SVG_DIR = ROOT / "assets" / "banners"
OUT = ROOT / "docs" / "banner-preview.png"


def inner(path: Path) -> str:
    """Strip the outer <svg> wrapper so the content can be nested in a <g>."""
    s = path.read_text(encoding="utf-8")
    return s.split(">", 1)[1].rsplit("</svg>", 1)[0]


def main():
    data = json.loads((ROOT / "tools" / "catalog.json").read_text())
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())
    import sys as _sys
    if "--all" in _sys.argv:
        banners = icons
    else:
        banners = [i for i in icons if i.get("banner")]

    CARD_W, CARD_H = 470, 264          # 16:9 at preview scale
    SCALE = CARD_W / 1280
    GAP_X, GAP_Y = 22, 22
    COLS = 3
    MARGIN = 44
    HEADER = 150
    MOCK = 470

    rows = (len(banners) + COLS - 1) // COLS
    W = MARGIN * 2 + COLS * CARD_W + (COLS - 1) * GAP_X
    H = HEADER + rows * (CARD_H + GAP_Y) + MOCK

    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
         f'viewBox="0 0 {W} {H}">',
         f'<rect width="{W}" height="{H}" fill="#0d1117"/>']

    # header
    s.append(f'<text x="{MARGIN}" y="60" fill="#00d4ff" '
             f'font-family="ui-monospace,monospace" font-size="15" '
             f'letter-spacing="2.4">CORE BUILDS · ICON PACK</text>')
    s.append(f'<text x="{MARGIN}" y="106" fill="#e6edf3" '
             f'font-family="Georgia,serif" font-size="40">16:9 banners</text>')
    s.append(f'<text x="{MARGIN}" y="134" fill="#8b949e" '
             f'font-family="ui-monospace,monospace" font-size="15">'
             f'{len(banners)} banners · 1280×720 · transparent · '
             f'drawable "&lt;icon&gt;_banner"</text>')

    # grid
    for n, i in enumerate(banners):
        cx = MARGIN + (n % COLS) * (CARD_W + GAP_X)
        cy = HEADER + (n // COLS) * (CARD_H + GAP_Y)
        s.append(f'<rect x="{cx}" y="{cy}" width="{CARD_W}" height="{CARD_H}" '
                 f'rx="16" fill="#151923" stroke="#ffffff" '
                 f'stroke-opacity=".07"/>')
        s.append(f'<g transform="translate({cx},{cy}) scale({SCALE:.5f})">'
                 f'{inner(SVG_DIR / (i["drawable"] + ".svg"))}</g>')

    # ---- mock Projectivy row -------------------------------------------
    my = HEADER + rows * (CARD_H + GAP_Y) + 24
    s.append(f'<text x="{MARGIN}" y="{my + 30}" fill="#e6edf3" '
             f'font-family="Georgia,serif" font-size="27">'
             f'In context · a Projectivy row with 16:9 cards</text>')
    s.append(f'<text x="{MARGIN}" y="{my + 56}" fill="#8b949e" '
             f'font-family="ui-monospace,monospace" font-size="13">'
             f'cards are 16:9 by default — the banner fills the whole card'
             f'</text>')

    ry = my + 82
    s.append(f'<rect x="{MARGIN}" y="{ry}" width="{W - MARGIN * 2}" '
             f'height="308" rx="16" fill="#04070f"/>')

    # Three cards + a sliver of the fourth, the way a real row scrolls off the
    # edge. Four full cards did not fit the panel and clipped mid-wordmark.
    row = ["stremio", "kodi", "nuvio"]
    mw, mh = 420, 236
    mscale = mw / 1280
    mx = MARGIN + 26
    for k, name in enumerate(row):
        p = SVG_DIR / f"{name}.svg"
        if not p.exists():
            continue
        focused = (k == 0)
        s.append(f'<rect x="{mx}" y="{ry + 36}" width="{mw}" height="{mh}" '
                 f'rx="14" fill="#151923" stroke="'
                 f'{"#00d4ff" if focused else "#ffffff"}" '
                 f'stroke-opacity="{1 if focused else 0.07}" '
                 f'stroke-width="{2.5 if focused else 1}"/>')
        s.append(f'<g transform="translate({mx},{ry + 36}) '
                 f'scale({mscale:.5f})">{inner(p)}</g>')
        mx += mw + 20

    s.append('</svg>')

    OUT.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring="\n".join(s).encode(), write_to=str(OUT),
                     output_width=W, output_height=H,
                     background_color="#0d1117")
    print(f"\u2713 docs/banner-preview.png written "
          f"({W}\u00d7{H}, {len(banners)} banners)")


if __name__ == "__main__":
    main()
