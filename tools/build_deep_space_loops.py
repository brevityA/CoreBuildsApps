#!/usr/bin/env python3
"""Deep Space loops — kept as the name the docs and changelog use.

The engine moved to tools/build_still_loops.py in 2.4.0, when the Motion and
Icon Echo sets started being rendered from their stills the same way. This
runs that engine for the Deep Space set only.

    python tools/build_deep_space_loops.py            # all twelve
    python tools/build_deep_space_loops.py --only 85  # one wall
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_still_loops import main as _main  # noqa: E402
from live_roster import DEEP_SPACE  # noqa: E402

# (wall, slug, title) — the shape build_motion_feed.py historically read.
WALLS = [(c.wall, c.slug, c.title) for c in DEEP_SPACE]


def clip_name(index: int, slug: str) -> str:
    return DEEP_SPACE[index].stem


def main(argv: list[str] | None = None) -> int:
    import argparse
    p = argparse.ArgumentParser()
    p.add_argument("--only", type=int, action="append")
    a = p.parse_args(argv)
    numbers = [c.number for c in DEEP_SPACE if not a.only or c.wall in a.only]
    return _main(sum([["--only", str(n)] for n in numbers], []))


if __name__ == "__main__":
    sys.exit(main())
