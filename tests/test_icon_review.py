#!/usr/bin/env python3
"""Every shipped square icon sits on the grid and fills it.

Born from the 2.0.0 full review, which measured all 975 shipped squares:
median ink-box offset 1px, 95th percentile 14.5px - except CNBC, whose fan
pivoted near the bottom edge and sat 96px below centre for releases. Folder
tabs, antennas and bell clappers legitimately pull an ink box off centre by
up to ~36px; a misplaced mark lands far beyond that, so the gate sits at
40px. Size has the same shape: the smallest correct marks (a lone play
triangle, a single x-height letter) span ~264px; anything under 256px is a
glyph drawn for a different grid.

Run: `python3 tests/test_icon_review.py`
"""
import json
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ICONS = json.loads((ROOT / "tools" / "catalog.json").read_text(encoding="utf-8"))["icons"]
DRAWABLES = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
MAX_OFFSET = 40
MIN_SIDE = 256


def ink_box(path):
    a = np.asarray(Image.open(path).convert("RGBA"))[:, :, 3]
    k = 512 / a.shape[1]
    ys, xs = np.nonzero(a > 25)
    return xs.min() * k, (xs.max() + 1) * k, ys.min() * k, (ys.max() + 1) * k


class IconReview(unittest.TestCase):
    def test_every_square_is_centred_and_sized(self):
        bad, seen = [], 0
        for icon in ICONS:
            path = DRAWABLES / f"{icon['drawable']}.webp"
            if not path.exists():      # an alias shares another drawable's art
                continue
            seen += 1
            x0, x1, y0, y1 = ink_box(path)
            off = max(abs((x0 + x1) / 2 - 256), abs((y0 + y1) / 2 - 256))
            side = max(x1 - x0, y1 - y0)
            if off > MAX_OFFSET:
                bad.append(f"{icon['drawable']}: ink box {off:.0f}px off centre")
            if side < MIN_SIDE:
                bad.append(f"{icon['drawable']}: ink spans {side:.0f}px, under {MIN_SIDE}")
        self.assertGreater(seen, 900)
        self.assertEqual(bad, [], "\n" + "\n".join(bad))


if __name__ == "__main__":
    unittest.main()
