#!/usr/bin/env python3
"""Objective identity checks for glyphs: reduction, safe area, collision.

Three questions a glyph has to answer, none of which eyeballing a 512px
master can settle:

  does it survive reduction   counters are counted at 96px (about what a
                              Projectivy tile occupies at 1080p) and at 48px.
                              A mark whose counters close on the way down has
                              become a blob at the size people actually see.

  does it stay inside SAFE    the raster presence pass dilates every edge, so
                              ink that is flush in the vector lands outside the
                              safe area in the shipped PNG.

  is it distinguishable       a new mark that renders nearly identically to an
                              existing one adds a file, not an identity. Every
                              glyph is compared against every other by
                              downsampled alpha, and near-twins are reported.

Usage:
    python tools/test_glyph_identity.py                 # the tranche set
    python tools/test_glyph_identity.py --all           # every catalog glyph
    python tools/test_glyph_identity.py a_glyph b_glyph
"""
from __future__ import annotations

import argparse
import io
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

from PIL import Image  # noqa: E402

import glyphs  # noqa: E402
from glyphs import GRID, SAFE  # noqa: E402
from svg_renderer import svg2png  # noqa: E402

REAL_TILE = 96      # roughly a Projectivy tile at 1080p
SMALL_TILE = 48     # deliberately twice as strict as the hardware
SLAB = 0.34         # above this the mark reads as a block, not a line

# The repo already fixed a reduction standard in
# docs/research/icon-tranche-research-2026-09-15.md section 5: marks are
# rendered at 160 / 80 / 48 px and "a 48 px mark whose total ink height falls
# below 8 px is too dependent on tiny letter detail to advance as-is".
# Reusing those numbers rather than inventing a second, competing standard.
REVIEW_SIZES = (160, 80, 48)
INK_HEIGHT_FLOOR = 8    # px, measured at 48
PAD = (GRID - SAFE) // 2
SIG = 16            # signature grid for collision comparison
TWIN = 0.965        # cosine similarity above which two marks are near-twins

# Glyphs added by the 2026-09-16 redraw tranche.
TRANCHE = [
    "crossy_chicken", "blokada_shield", "pia_robot", "mpv_play",
    "audiomack_wave", "atres_chevrons", "kinopoisk_k", "aida_sixty_four",
]

# Known, argued exceptions. REAL_TILE is the size hardware actually shows;
# SMALL_TILE is deliberately twice as strict, so a glyph that holds every
# counter at 96px and loses one only at 48px is a judgement call, not a
# defect. Recording them here keeps the judgement visible and reviewable
# instead of silently loosening the threshold for everyone. A glyph that
# fails at REAL_TILE is never eligible.
ACCEPTED_48PX = {
    "crossy_chicken":
        "the comb notch closes at 48px; all three counters hold at 96px. "
        "Deepening the notch far enough to survive 48px costs the serrated "
        "comb edge, which is the cue that reads the mark as a bird rather "
        "than a rounded blob.",
}


def _holes(mask: bytearray, w: int, h: int) -> int:
    """Background regions fully enclosed by ink, 4-connected."""
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


def render(name: str, colour: str = "#4CC9F0") -> Image.Image:
    png = svg2png(bytestring=glyphs.render_svg(name, colour),
                  output_width=GRID, output_height=GRID)
    return Image.open(io.BytesIO(bytes(png))).convert("RGBA")


def _measure(alpha: Image.Image, size: int) -> tuple[float, int]:
    a = alpha.resize((size, size), Image.LANCZOS)
    mask = bytearray(1 if p >= 110 else 0 for p in a.getdata())
    return sum(mask) / (size * size), _holes(mask, size, size)


def signature(alpha: Image.Image) -> list[float]:
    a = alpha.resize((SIG, SIG), Image.LANCZOS)
    return [p / 255.0 for p in a.getdata()]


def cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = sum(x * x for x in a) ** 0.5
    nb = sum(y * y for y in b) ** 0.5
    return 0.0 if na == 0 or nb == 0 else dot / (na * nb)


def check(name: str) -> dict:
    img = render(name)
    alpha = img.getchannel("A")
    box = alpha.getbbox()
    _, big_holes = _measure(alpha, 256)
    real_ink, real_holes = _measure(alpha, REAL_TILE)
    small_ink, small_holes = _measure(alpha, SMALL_TILE)

    problems: list[str] = []
    waived: list[str] = []
    if not box:
        problems.append("renders empty")
    else:
        margin = min(box[0], box[1], GRID - box[2], GRID - box[3])
        if margin < PAD:
            problems.append(
                f"ink {PAD - margin}px outside SAFE (bbox {box})")
    if big_holes > real_holes:
        problems.append(
            f"{big_holes - real_holes} counter(s) close at {REAL_TILE}px "
            f"- the size a real tile occupies")
    elif big_holes > small_holes:
        msg = (f"{big_holes - small_holes} counter(s) close at {SMALL_TILE}px")
        if name in ACCEPTED_48PX:
            waived.append(f"{msg} - accepted: {ACCEPTED_48PX[name]}")
        else:
            problems.append(msg)
    if small_ink > SLAB:
        problems.append(
            f"reads as a slab ({small_ink * 100:.1f}% ink at {SMALL_TILE}px)")

    # Section-5 reduction standard: ink bounds at 160 / 80 / 48, with an 8px
    # ink-height floor at 48.
    bounds = []
    for s_ in REVIEW_SIZES:
        bb = alpha.resize((s_, s_), Image.LANCZOS).point(
            lambda v: 255 if v >= 110 else 0).getbbox()
        bounds.append((0, 0) if not bb else (bb[2] - bb[0], bb[3] - bb[1]))
    if bounds[-1][1] < INK_HEIGHT_FLOOR:
        problems.append(
            f"ink height {bounds[-1][1]}px at 48px is below the "
            f"{INK_HEIGHT_FLOOR}px floor (research section 5)")

    return {
        "name": name, "bbox": box, "problems": problems, "waived": waived,
        "holes": (big_holes, real_holes, small_holes),
        "bounds": bounds,
        "ink": (real_ink, small_ink),
        "sig": signature(alpha),
    }


def main() -> int:
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("names", nargs="*")
    ap.add_argument("--all", action="store_true",
                    help="every glyph referenced by the catalog")
    ap.add_argument("--no-collision", action="store_true")
    args = ap.parse_args()

    names = list(args.names) or (
        sorted({i["glyph"] for i in
                json.loads((ROOT / "tools/catalog.json").read_text())["icons"]})
        if args.all else list(TRANCHE))

    unknown = [n for n in names if n not in glyphs.GLYPHS]
    if unknown:
        print(f"unknown glyph(s): {', '.join(unknown)}", file=sys.stderr)
        return 2

    results = [check(n) for n in names]
    failed = 0
    for r in results:
        ok = not r["problems"]
        hb, hr, hs = r["holes"]
        print(f"{'ok  ' if ok else 'FAIL'} {r['name']:<20} "
              f"counters {hb} -> {hr} -> {hs}   "
              f"ink {r['ink'][1] * 100:5.1f}%   "
              f"bounds {' / '.join(f'{w}x{h}' for w, h in r['bounds'])}")
        for p in r["problems"]:
            print(f"       - {p}")
        for w in r["waived"]:
            print(f"       ~ {w}")
        failed += 0 if ok else 1

    # Collision: compare every checked glyph against the whole registry, so a
    # new mark cannot quietly duplicate one that was already shipping.
    if not args.no_collision:
        print("\ncollision check (vs every glyph in the registry)")
        universe = sorted(glyphs.GLYPHS)
        sigs = {}
        for n in universe:
            try:
                sigs[n] = signature(render(n).getchannel("A"))
            except Exception:
                continue
        collisions = 0
        for r in results:
            worst, score = None, 0.0
            for n, s in sigs.items():
                if n == r["name"]:
                    continue
                c = cosine(r["sig"], s)
                if c > score:
                    worst, score = n, c
            flag = "FAIL" if score >= TWIN else "ok  "
            if score >= TWIN:
                collisions += 1
            print(f"{flag} {r['name']:<20} nearest: {worst:<22} "
                  f"similarity {score:.3f}")
        failed += collisions

    print(f"\n{len(results)} glyph(s) checked · {failed} problem(s)")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
