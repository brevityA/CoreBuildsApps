#!/usr/bin/env python3
"""
Core Builds Classic — optical fit for the square glyphs.

The Classic pack draws every mark on one 512 grid and aims it at one ink box
(~352 across, the median glyph). Most marks land there; a tail did not: 81
glyphs drew their ink under 280 across (the smallest, MX Player's, at 153),
and 76 sat more than 16 units off the canvas centre. On a launcher row that
reads as icons of different sizes rattling around their cards.

Core Builds Pop (retired 2026-09-24) normalised every mark. This does the
Classic equivalent, conservatively:

  * a mark whose ink box is under FLOOR across is scaled up towards TARGET,
    never past MAX_SCALE and never past the SAFE zone a launcher's iconmask
    may clip to;
  * a mark whose centre is more than CENTRE_TOLERANCE off the canvas centre
    (or that is being scaled anyway) is centred;
  * stroke widths are divided by the scale in the renderer, so the line
    weight stays the pack's one weight after scaling.

Left alone, by rule:
  * glyphs any `core_monoline` icon uses - those were reviewed stroke by
    stroke against their reference and their geometry is the contract
    (tools/icon_style.py checks the exact weights);
  * category monograms (`<family>_<L>`), whose shell is fixed by
    FAMILY_SHELLS and whose mark is fitted by the adaptive type.

Input is tools/glyph_metrics.json (committed geometry ink boxes, so the
renderer stays a pure function of committed files). Output is
tools/classic_glyph_fit.json, which glyphs.render_svg reads.

    python tools/fit_classic_glyphs.py          # write
    python tools/fit_classic_glyphs.py --check  # fail on drift (CI)
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from glyphs import GRID, SAFE, family_glyph_for  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"
METRICS = ROOT / "tools" / "glyph_metrics.json"
OUT = ROOT / "tools" / "classic_glyph_fit.json"

FLOOR = 280.0             # geometry ink box below this is undersized
TARGET = 300.0            # ... and is scaled up to this, within the limits below
MAX_SCALE = 1.5           # never more than half again: a mark is not a new drawing
CENTRE_TOLERANCE = 16.0   # grid units; a half-stroke of drift is optical, not error
HALF_STROKE = 16.0        # the monoline primary stroke is 32; it overhangs the geometry


def fits() -> dict[str, list[float]]:
    icons = json.loads(CATALOG.read_text(encoding="utf-8"))["icons"]
    metrics = json.loads(METRICS.read_text(encoding="utf-8"))["metrics"]
    reviewed = {i["glyph"] for i in icons if i.get("style") == "core_monoline"}
    used = sorted({i["glyph"] for i in icons})
    centre = GRID / 2
    out: dict[str, list[float]] = {}
    for glyph in used:
        if glyph in reviewed or family_glyph_for(glyph) is not None:
            continue
        box = metrics.get(glyph)
        if box is None:
            raise SystemExit(f"{glyph}: no metrics - run tools/measure_glyphs.py")
        x0, y0, x1, y1 = box
        span = max(x1 - x0, y1 - y0)
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        scale = 1.0
        if span < FLOOR:
            # Largest scale that keeps the stroked ink inside SAFE when centred.
            safe = (SAFE / 2 - HALF_STROKE) / (span / 2)
            scale = min(TARGET / span, MAX_SCALE, safe)
        off = max(abs(cx - centre), abs(cy - centre))
        if scale == 1.0 and off <= CENTRE_TOLERANCE:
            continue
        out[glyph] = [round(scale, 4), round(cx, 2), round(cy, 2)]
    return out


def render() -> str:
    body = {
        "note": ("Optical fit of Classic square glyphs: [scale, ink centre x, "
                 "ink centre y] on the 512 grid. The renderer maps the ink "
                 "centre to the canvas centre and scales about it. Generated "
                 "by tools/fit_classic_glyphs.py from tools/glyph_metrics.json "
                 "- do not hand-edit."),
        "floor": FLOOR, "target": TARGET, "max_scale": MAX_SCALE,
        "centre_tolerance": CENTRE_TOLERANCE,
        "fits": fits(),
    }
    return json.dumps(body, indent=2, sort_keys=False) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument("--check", action="store_true", help="fail on drift")
    args = parser.parse_args(argv)
    text = render()
    n = len(json.loads(text)["fits"])
    if args.check:
        if not OUT.is_file() or OUT.read_text(encoding="utf-8") != text:
            print("::error::tools/classic_glyph_fit.json is stale; "
                  "run python tools/fit_classic_glyphs.py")
            return 1
        print(f"classic glyph fit in sync - {n} glyphs fitted")
        return 0
    OUT.write_text(text, encoding="utf-8")
    print(f"classic glyph fit written - {n} glyphs fitted")
    return 0


if __name__ == "__main__":
    sys.exit(main())
