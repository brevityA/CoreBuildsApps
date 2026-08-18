#!/usr/bin/env python3
"""
Mark-only banner variant (concept L), for evaluation against a real device
screenshot.

Rationale, measured from a TCL Google TV home row:

    element                master   on screen (scale 0.139)
    glyph box                360px      50px
    wordmark                 124px      17px
    category kicker           46px     6.4px   <- below legibility floor
    rail                      16px     2.2px   <- reads as an artifact

Neighbouring icons (Apple TV, SYNC, TV Bro) render their marks at 48-100px.
Ours render at 30-39px because the wordmark consumes ~60% of the canvas to
repeat a name Projectivy already draws beneath the card.

This variant spends the whole canvas on the mark. Three weights so the
trade-off can be judged rather than argued:

    L1  monoline, mark only          - current stroke weight, no wordmark
    L2  heavier stroke, mark only    - matches the row's visual weight
    L3  filled/duotone, mark only    - matches TV Bro and SYNC

Writes only to /tmp. Nothing here touches res/.
"""
import json, os, re, sys
from pathlib import Path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glyphs import GLYPHS, monoline, lit          # noqa: E402
import cairosvg                                    # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
W, H = 1280, 720
OUT = Path("/tmp/variant")


def _reweight(body, mult):
    """Scale every stroke width by `mult` (used for the heavier variants)."""
    return re.sub(r'stroke-width="([\d.]+)"',
                  lambda m: f'stroke-width="{float(m.group(1)) * mult:.1f}"',
                  body)


def render(glyph, colour, mode="L1"):
    """
    Mark centred, filling the safe area.

    Google's TV guidance caps usable area inside the frame; 78% of height
    keeps the mark clear of the crop while roughly doubling its on-screen
    size versus the wordmark lockup.
    """
    box = H * 0.78                      # 562px -> ~78px on a 100px row
    body = monoline(GLYPHS[glyph](colour))

    if mode == "L2":
        body = _reweight(body, 1.45)
    elif mode == "L3":
        body = _reweight(body, 1.45)
        body = lit(body, colour, spread=16, opacity=0.45)

    scale = box / 512
    x = (W - box) / 2
    y = (H - box) / 2
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" '
            f'height="{H}" viewBox="0 0 {W} {H}">'
            f'<g transform="translate({x:.0f},{y:.0f}) scale({scale:.5f})">'
            f'{body}</g></svg>')


def main():
    cat = json.loads((ROOT / "tools" / "catalog.json").read_text())
    by = {i["name"]: i for i in cat["icons"]}
    want = ["WuPlay", "Plex", "TizenTube", "Stremio", "Kodi", "Netflix"]
    OUT.mkdir(parents=True, exist_ok=True)
    n = 0
    for name in want:
        i = by.get(name)
        if not i:
            continue
        for mode in ("L1", "L2", "L3"):
            svg = render(i["glyph"], i["color"], mode)
            cairosvg.svg2png(bytestring=svg.encode(),
                             write_to=str(OUT / f"{i['drawable']}_{mode}.png"),
                             output_width=320, output_height=180,
                             background_color=None)
            n += 1
    print(f"\u2713 {n} variant PNGs -> {OUT}")


if __name__ == "__main__":
    main()
