#!/usr/bin/env python3
"""Write the Core Builds Motion live-wallpaper feed and contact sheet.

Every loop is the moving form of a committed wall (tools/live_roster.py):
the Motion, Deep Space and Icon Echo sets by tools/build_still_loops.py,
the Cinema set by tools/build_cinema_wallpapers.py. This script renders
nothing; it lists what the roster says exists, in browser order, and fails
loudly if a clip or poster is missing so a stale feed cannot ship.

Until 2.4.0 the feed opened with ten ffmpeg test-pattern clips (gradients,
cellauto, mandelbrot, sierpinski). They were retired when the Live chip
became a set of its own: every loop now has a still it is the moving form
of, and none is a stock pattern with a Core Builds name on it.

Outputs:
- Motion/live/preview/live-preview.png   contact sheet, three across
- Motion/live-feed.json                  Overflight-compatible feed
                                          (url_img + url_1080p)
- app/src/main/assets/live_thumbs/       the posters the icon pack bundles
                                          for its grid and engine fallback,
                                          kept exactly the roster's set

Run:  python tools/build_motion_feed.py
"""
from __future__ import annotations

import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from live_roster import (  # noqa: E402
    BASE_URL, DURATION, FPS, H, ROOT, ROSTER, THUMB_H, THUMB_W, W)

__all__ = ["BASE_URL", "DURATION", "FPS", "W", "H", "THUMB_W", "THUMB_H",
           "write_feed", "build_sheet"]


def build_sheet(thumbs, out):
    """Every poster on one sheet, three across, in feed order."""
    cols = 3
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * THUMB_W, rows * THUMB_H), (4, 7, 15))
    for i, t in enumerate(thumbs):
        im = Image.open(t).convert("RGB").resize((THUMB_W, THUMB_H))
        sheet.paste(im, ((i % cols) * THUMB_W, (i // cols) * THUMB_H))
    sheet.save(out, optimize=True)


def feed_entries():
    return [{
        "location": c.location,
        "title": c.title,
        "author": "Core Builds",
        "url_img": f"{BASE_URL}/thumbs/{c.stem}.jpg",
        "url_1080p": f"{BASE_URL}/{c.stem}.mp4",
    } for c in ROSTER]


def write_feed():
    with open(ROOT / "Motion" / "live-feed.json", "w", encoding="utf-8") as f:
        json.dump(feed_entries(), f, indent=2)
        f.write("\n")


def sync_bundled_thumbs():
    """assets/live_thumbs == the roster's posters, byte for byte, no strays."""
    import shutil
    assets = ROOT / "app" / "src" / "main" / "assets" / "live_thumbs"
    assets.mkdir(parents=True, exist_ok=True)
    keep = {c.thumb.name for c in ROSTER}
    for old in assets.glob("*.jpg"):
        if old.name not in keep:
            old.unlink()
    for c in ROSTER:
        dst = assets / c.thumb.name
        if not dst.is_file() or dst.read_bytes() != c.thumb.read_bytes():
            shutil.copyfile(c.thumb, dst)


def main():
    missing = [c.stem for c in ROSTER if not (c.mp4.is_file() and c.thumb.is_file())]
    if missing:
        print("missing clip or poster (render first):\n  " + "\n  ".join(missing))
        return 1
    (ROOT / "Motion" / "live" / "preview").mkdir(exist_ok=True)
    build_sheet([c.thumb for c in ROSTER], ROOT / "Motion" / "live" / "preview" / "live-preview.png")
    write_feed()
    sync_bundled_thumbs()
    print(f"done: {len(ROSTER)} live wallpapers listed in Motion/live-feed.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
