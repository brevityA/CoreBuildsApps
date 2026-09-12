#!/usr/bin/env python3
"""Render one glyph and judge it, without building the pack.

Every check here already existed somewhere — validate.py enforces the safe
area, test_icon_identity.py measures rendered alpha, icon_style.py owns the
monoline contract — but all of them run against *shipped* assets. Seeing
whether a glyph works therefore cost a full regeneration: measure_pop_glyphs,
build_icons, banners, branding, previews, build_pop, build_pixel_neon. That
is eighteen to twenty-two minutes to answer "is this counter going to close".

Three things this catches that eyeballing the 512px master cannot:

  counters closing   a mark is judged at the size a Projectivy tile actually
                     occupies (~100px at 1080p), not at authoring size. Ink
                     that reads as a ring at 512 reads as a disc at 48.

  normalised weights monoline() snaps authored strokes to 32 / 26.2 / 21.8.
                     Seventeen distinct weights are authored across the pack
                     and fourteen of them are fiction. Tuning a glyph against
                     the numbers in the source rather than the numbers that
                     render is a real, repeated bug.

  safe area          SAFE promises a 40px margin on the 512 grid. The raster
                     presence pass then dilates every edge, so a mark that is
                     flush in the vector is outside it in the shipped PNG.

Usage:
    python tools/check_glyph.py janky_play
    python tools/check_glyph.py --all            every glyph in the catalog
    python tools/check_glyph.py --family broadcast
    python tools/check_glyph.py janky_play --png /tmp/look.png
"""
from __future__ import annotations

import argparse
import io
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

from PIL import Image  # noqa: E402

import glyphs  # noqa: E402
from glyphs import GRID, SAFE  # noqa: E402
from icon_style import core_monoline_errors, display_accent  # noqa: E402
from svg_renderer import svg2png  # noqa: E402

TILE = 48          # deliberately half a real tile: a margin of safety
REAL_TILE = 96     # about what Projectivy shows at 1080p
SLAB = 0.34        # above this the mark reads as a block, not a line
PAD = (GRID - SAFE) // 2


def _holes(mask: bytearray, w: int, h: int) -> int:
    """Background regions fully enclosed by ink, 4-connected.

    Flood from the border marks the outside; whatever background is left is a
    counter. Single-pixel specks are ignored — they are resampling noise, not
    shapes a viewer perceives.
    """
    seen = bytearray(w * h)
    stack = list(range(w)) + [(h - 1) * w + i for i in range(w)] \
        + [r * w for r in range(h)] + [r * w + w - 1 for r in range(h)]
    for s in stack:
        seen[s] = 1
    while stack:
        p = stack.pop()
        if mask[p]:
            continue
        y, x = divmod(p, w)
        for q in ((p - w) if y else -1, (p + w) if y < h - 1 else -1,
                  (p - 1) if x else -1, (p + 1) if x < w - 1 else -1):
            if q >= 0 and not seen[q] and not mask[q]:
                seen[q] = 1
                stack.append(q)
    n = 0
    for p in range(w * h):
        if not mask[p] and not seen[p]:
            seen[p] = 1
            size, st = 0, [p]
            while st:
                q = st.pop()
                size += 1
                y, x = divmod(q, w)
                for r in ((q - w) if y else -1, (q + w) if y < h - 1 else -1,
                          (q - 1) if x else -1, (q + 1) if x < w - 1 else -1):
                    if r >= 0 and not seen[r] and not mask[r]:
                        seen[r] = 1
                        st.append(r)
            if size >= 2:
                n += 1
    return n


def _measure(alpha: Image.Image, size: int) -> tuple[float, int]:
    a = alpha.resize((size, size), Image.LANCZOS)
    mask = bytearray(1 if p >= 110 else 0 for p in a.getdata())
    return sum(mask) / (size * size), _holes(mask, size, size)


def render(name: str, colour: str = "#4CC9F0") -> Image.Image:
    png = svg2png(bytestring=glyphs.render_svg(name, colour),
                  output_width=GRID, output_height=GRID)
    return Image.open(io.BytesIO(bytes(png))).convert("RGBA")


def normalised_weights(name: str, colour: str = "#4CC9F0") -> tuple[list, list]:
    """What the author wrote, and what actually renders."""
    raw = glyphs.GLYPHS[name](colour)
    before = sorted({float(w) for w in re.findall(r'stroke-width="([\d.]+)"', raw)})
    after = sorted({float(w) for w in
                    re.findall(r'stroke-width="([\d.]+)"', glyphs.monoline(raw))})
    return before, after


def check(name: str, colour: str = "#4CC9F0", strict: bool = False) -> dict:
    img = render(name, colour)
    alpha = img.getchannel("A")
    box = alpha.getbbox()
    big_ink, big_holes = _measure(alpha, 256)
    real_ink, real_holes = _measure(alpha, REAL_TILE)
    tile_ink, tile_holes = _measure(alpha, TILE)
    before, after = normalised_weights(name, colour)

    problems = []
    if not box:
        problems.append("renders empty")
    else:
        margin = min(box[0], box[1], GRID - box[2], GRID - box[3])
        if margin < PAD:
            problems.append(f"ink {PAD - margin}px outside SAFE (bbox {box})")
    if big_holes > real_holes:
        problems.append(f"{big_holes - real_holes} counter(s) close at {REAL_TILE}px")
    elif big_holes > tile_holes:
        problems.append(f"{big_holes - tile_holes} counter(s) close at {TILE}px")
    if tile_ink > SLAB:
        problems.append(f"reads as a slab ({tile_ink*100:.1f}% ink at {TILE}px)")
    if strict:
        accent = display_accent(colour)
        errs = core_monoline_errors(glyphs.monoline(glyphs.GLYPHS[name](accent)), accent)
        problems.extend(errs)

    return {"name": name, "bbox": box, "problems": problems,
            "ink": (big_ink, real_ink, tile_ink),
            "holes": (big_holes, real_holes, tile_holes),
            "weights": (before, after)}


def report(r: dict, verbose: bool) -> None:
    ok = not r["problems"]
    mark = "ok  " if ok else "FAIL"
    b, rl, t = r["ink"]
    hb, hr, ht = r["holes"]
    print(f"{mark} {r['name']:<24} ink {t*100:5.1f}%   "
          f"counters {hb} -> {hr} -> {ht}   bbox {r['bbox']}")
    if verbose:
        before, after = r["weights"]
        print(f"       authored weights {before}")
        print(f"       renders as       {after}")
    for p in r["problems"]:
        print(f"       - {p}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("names", nargs="*", help="glyph names to check")
    ap.add_argument("--all", action="store_true", help="every glyph used by the catalog")
    ap.add_argument("--family", help="every glyph in one category family, e.g. broadcast")
    ap.add_argument("--colour", default="#4CC9F0", help="accent to render with")
    ap.add_argument("--strict", action="store_true",
                    help="also apply the core_monoline contract")
    ap.add_argument("--png", help="write the 512px render here and stop")
    ap.add_argument("-v", "--verbose", action="store_true",
                    help="show authored vs normalised stroke weights")
    args = ap.parse_args()

    names = list(args.names)
    if args.all:
        cat = json.loads((ROOT / "tools/catalog.json").read_text())["icons"]
        names = sorted({i["glyph"] for i in cat})
    if args.family:
        names += sorted(n for n in glyphs.GLYPHS if n.startswith(args.family + "_"))
    if not names:
        ap.error("name a glyph, or pass --all / --family")

    unknown = [n for n in names if n not in glyphs.GLYPHS]
    if unknown:
        print(f"unknown glyph(s): {', '.join(unknown)}", file=sys.stderr)
        return 2

    if args.png:
        render(names[0], args.colour).save(args.png)
        print(f"wrote {args.png}")
        return 0

    failed = 0
    for n in names:
        r = check(n, args.colour, args.strict)
        if r["problems"]:
            failed += 1
        if r["problems"] or args.verbose or len(names) <= 12:
            report(r, args.verbose)
    print(f"\n{len(names)} checked · {failed} with problems")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
