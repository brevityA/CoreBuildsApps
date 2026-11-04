#!/usr/bin/env python3
"""Fail when a new icon is a near copy of another brand's icon.

Why this exists
---------------
The validator checks each icon on its own: palette, safe area, stroke weight.
Nothing compared icons with each other, so marks drifted together. The
2026-10-18 sweep found TBS and TNT drawing the same bare T, AMC+, A&E and
ABC News the same A, Kayo, Fox News and Kanopy the same K, and Aptoide,
Aurora Store and APKPure the same empty bag. Their docstrings named the cue
that told them apart (TNT's crash bar, ABC's wave, Aptoide's play) and the
geometry never drew it. On a launcher row only the colour separated them.

This compares the alpha of every built square icon at 64 px (the size a
Projectivy tile reads at) and lists each pair of distinct glyphs whose
intersection over union reaches THRESHOLD. Exempt, because a viewer can
tell them apart:

  * two icons of one brand (`brand`), which share a mark on purpose;
  * two wordmarks with different text: the letters differ even where the
    outline of a block of letters does not.

Every other pair must be listed in tools/lookalike_baseline.json, which
holds the pairs accepted when the gate was written (shared-family marks
such as NBC's peacock, and generic marks shared across categories). The
baseline may only shrink: a pair that no longer collides fails until it is
removed, so a fix is recorded rather than left as slack for the next copy.

    python tools/check_lookalikes.py          # check (CI)
    python tools/check_lookalikes.py --write  # rewrite the baseline
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))

from drawable_art import art_path, read_aliases  # noqa: E402
from glyphs import GLYPHS  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "tools" / "catalog.json"
BASELINE = ROOT / "tools" / "lookalike_baseline.json"
ART = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
VALUES = ROOT / "app" / "src" / "main" / "res" / "values"

THRESHOLD = 0.80   # the 2026-10-15 audit's bar: above it two marks read as one at tile size
TILE = 64
INK = 40           # alpha above this counts as ink


def wordmark(glyph: str):
    """The text of a stroke-letter wordmark glyph, or None."""
    wm = getattr(GLYPHS.get(glyph), "wm", None)
    return wm[0] if wm else None


def exempt(a: dict, b: dict) -> bool:
    if a.get("brand") and a.get("brand") == b.get("brand"):
        return True
    ta, tb = wordmark(a["glyph"]), wordmark(b["glyph"])
    return ta is not None and tb is not None and ta.casefold() != tb.casefold()


def masks(icons: list[dict]) -> tuple[list[dict], np.ndarray]:
    aliases = read_aliases(VALUES)
    rows, vecs = [], []
    for icon in icons:
        path = art_path(ART, icon["drawable"], aliases)
        if not path.exists():
            continue   # validate.py reports missing art by name
        alpha = Image.open(path).convert("RGBA").resize((TILE, TILE), Image.BOX).getchannel("A")
        rows.append(icon)
        vecs.append((np.asarray(alpha) > INK).ravel())
    return rows, np.array(vecs, dtype=np.float32)


def pairs(icons: list[dict], vecs: np.ndarray, threshold: float = THRESHOLD) -> dict[tuple[str, str], float]:
    """{(glyph, glyph): best IoU} over every non-exempt pair of distinct glyphs."""
    inter = vecs @ vecs.T
    ink = vecs.sum(1)
    iou = inter / np.maximum(ink[:, None] + ink[None, :] - inter, 1)
    found: dict[tuple[str, str], float] = {}
    hits = np.argwhere(np.triu(iou >= threshold, k=1))
    for i, j in hits:
        a, b = icons[i], icons[j]
        if a["glyph"] == b["glyph"] or exempt(a, b):
            continue
        key = tuple(sorted((a["glyph"], b["glyph"])))
        found[key] = max(found.get(key, 0.0), round(float(iou[i, j]), 3))
    return found


def compare(found: dict, baseline: set) -> tuple[list, list]:
    """(new pairs, baseline pairs that no longer collide)."""
    new = sorted(k for k in found if k not in baseline)
    gone = sorted(k for k in baseline if k not in found)
    return new, gone


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--write", action="store_true", help="rewrite the baseline from the built pack")
    args = parser.parse_args(argv)

    icons = json.loads(CATALOG.read_text(encoding="utf-8"))["icons"]
    rows, vecs = masks(icons)
    found = pairs(rows, vecs)
    users: dict[str, list[str]] = {}
    for icon in rows:
        users.setdefault(icon["glyph"], []).append(icon["name"])

    if args.write:
        BASELINE.write_text(json.dumps({
            "threshold": THRESHOLD,
            "note": "Glyph pairs accepted as look-alikes when tools/check_lookalikes.py was written. "
                    "Remove a pair when it is fixed; never add one to let a new icon in.",
            "pairs": [{"glyphs": list(k), "iou": v, "icons": [users[k[0]][0], users[k[1]][0]]}
                      for k, v in sorted(found.items(), key=lambda kv: (-kv[1], kv[0]))],
        }, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"look-alike baseline written - {len(found)} accepted glyph pairs at >= {THRESHOLD}")
        return 0

    baseline = {tuple(sorted(p["glyphs"])) for p in json.loads(BASELINE.read_text(encoding="utf-8"))["pairs"]}
    new, gone = compare(found, baseline)
    for a, b in new:
        print(f"NEW look-alike {found[(a, b)]:.3f}: {a} ({', '.join(users[a][:2])}) ~ "
              f"{b} ({', '.join(users[b][:2])}) - give one of them a cue the other lacks")
    for a, b in gone:
        print(f"FIXED, remove from tools/lookalike_baseline.json: {a} ~ {b}")
    if new or gone:
        return 1
    print(f"look-alikes ok - {len(rows)} icons, {len(found)} accepted glyph pairs at >= {THRESHOLD}, none new")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
