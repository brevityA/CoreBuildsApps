#!/usr/bin/env python3
"""Optical uniformity gate for the Classic square icons.

Born from the user's Projectivy screenshots (RB4, 2026-09-19): "See the
uniformability. Also the new Janky icon seems like it is sized wrong."
Measured, the complaint was exact. The shipped Janky lockup sat in a
0.80 x 0.47 ink box (aspect 1.71) against a pack median of 0.78 x 0.74
(aspect 1.05): a full-width band where every container mark sits in a
near-square optical box. In a launcher's fixed slot a band that wide
crops at the edges and reads oversized next to its neighbours, and on
the 16:9 banner - whose template scales every glyph at a fixed
GLYPH_H/512 instead of normalising to each ink box - the same flat box
drew the mark at half the cap height of every mark beside it.

Two rules, both computed from the rendered vector (the same rasteriser
the generators use, so CI and local agree):

  container grammar   Glyphs whose vocabulary is a container - a ring or
                      frame drawn as the primary element - must span the
                      container band in BOTH axes (0.55-0.90 of GRID) and
                      stay near-square (aspect 0.80-1.30). A container at
                      half height is the Janky bug class: the eye reads
                      the long axis as size, so a flat container looks
                      oversized in every fixed slot. The list is explicit
                      on purpose; membership is a design decision, and a
                      new container mark has to be added here when it is
                      drawn, which is the review moment this gate exists
                      to create.

  full-width band     No shipped tile glyph may carry the exact signature
                      RB4 flagged: aspect > 1.65 with height in 0.44-0.50
                      and width >= 0.78 of GRID. The window is deliberately
                      narrow. The pack legitimately ships wordmark strips
                      (fitetv at 0.61 x 0.22, gothamsports at 0.59 x 0.16)
                      and wide lockups (magneticchen at 0.77 x 0.46), and
                      those read as type, not as oversized marks; only a
                      mark that fills the full safe width at half height
                      crops and reads wrong. The rule scans the catalog's
                      tile assignments, not the whole GLYPHS registry: a
                      dormant glyph (tubi_wordmark, vidio_wordmark - drawn
                      for research, shipped by nothing) cannot be
                      non-uniform on a launcher.

Usage:
    python tests/test_icon_uniformity.py
"""
from __future__ import annotations

import io
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

from PIL import Image  # noqa: E402

import glyphs  # noqa: E402
from glyphs import GRID  # noqa: E402

# Container-grammar marks: the container IS the mark's primary element.
# Add a glyph here when it is drawn with a ring/frame container; the band
# is the pack's measured container family (mpv 0.76, downloader 0.79,
# tivimate 0.83 x 0.66, browser_globe 0.84 x 0.72).
CONTAINER_GRAMMAR = {
    "janky_play",
    "mpv_play",
    "browser_globe",
    "tivimate_grid",
    "downloader_arrow",
}
CONTAINER_MIN = 0.55
CONTAINER_MAX = 0.90
CONTAINER_ASPECT = (0.80, 1.30)

# The RB4 signature: full safe width at half height.
BAND_ASPECT_MIN = 1.65
BAND_HEIGHT = (0.44, 0.50)
BAND_WIDTH_MIN = 0.78


def ink_box(glyph_name: str) -> tuple[float, float]:
    """Rendered ink box as (width, height) fractions of GRID."""
    from svg_renderer import svg2png

    svg = glyphs.render_svg(glyph_name, "#1E88E5")
    data = svg2png(bytestring=svg.encode(), output_width=GRID, output_height=GRID)
    alpha = Image.open(io.BytesIO(data)).convert("RGBA").getchannel("A")
    box = alpha.point(lambda p: 255 if p >= 110 else 0).getbbox()
    if not box:
        raise SystemExit(f"no ink rendered for {glyph_name}")
    return (box[2] - box[0]) / GRID, (box[3] - box[1]) / GRID


def main() -> int:
    problems: list[str] = []
    for name in sorted(CONTAINER_GRAMMAR):
        if name not in glyphs.GLYPHS:
            problems.append(f"{name}: listed in CONTAINER_GRAMMAR but not in the registry")
            continue
        w, h = ink_box(name)
        aspect = w / h
        if not CONTAINER_MIN <= w <= CONTAINER_MAX or not CONTAINER_MIN <= h <= CONTAINER_MAX:
            problems.append(
                f"{name}: container span {w:.2f} x {h:.2f} outside "
                f"[{CONTAINER_MIN:.2f}, {CONTAINER_MAX:.2f}] on the {GRID}px grid"
            )
        if not CONTAINER_ASPECT[0] <= aspect <= CONTAINER_ASPECT[1]:
            problems.append(
                f"{name}: container aspect {aspect:.2f} outside "
                f"{CONTAINER_ASPECT} - a flat container reads oversized in fixed slots"
            )

    catalog = json.loads((ROOT / "tools" / "catalog.json").read_text())
    tile_glyphs = {i["glyph"] for i in catalog["icons"]}
    scanned = 0
    for name in sorted(tile_glyphs):
        if name not in glyphs.GLYPHS:
            problems.append(f"{name}: catalog tile glyph missing from the registry")
            continue
        scanned += 1
        w, h = ink_box(name)
        aspect = w / h
        if (aspect > BAND_ASPECT_MIN
                and BAND_HEIGHT[0] <= h <= BAND_HEIGHT[1]
                and w >= BAND_WIDTH_MIN):
            problems.append(
                f"{name}: full-width band {w:.2f} x {h:.2f} (aspect {aspect:.2f}) - "
                "the RB4 signature; containers span both axes, wordmarks stay narrow"
            )

    if problems:
        print(f"uniformity gate: {len(problems)} problem(s)")
        for p in problems:
            print("  \u2717 " + p)
        return 1
    print(f"uniformity gate ok \u2014 {len(CONTAINER_GRAMMAR)} container marks in band, "
          f"{scanned} shipped tile glyphs clear of the full-width band signature")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
