#!/usr/bin/env python3
"""Build the three Core Builds ``icon-echo`` live loops.

Companions to series-11-icon-echo (tools/build_icon_echo_wallpapers.py):
each loop is a slow procedural field in one icon accent from the catalogue,
shifted a step toward the night ground the same way the stills' tones are
(see wall_tones there), so the moving wall and the still read as the same
colour family.

Numbering continues after the Cinema loops (23-28):
  29 signal-sweep   YouTube #FF0033, a linear drift
  30 green-current  Spotify #1ED760, a slow spiral
  31 molten-orbit   MaxPlayer #F8A000, a radial bloom

Writes Motion/live/coremotion-live-NN-<slug>.mp4 + poster thumbs, using the
same ffmpeg pipeline as tools/build_motion_feed.py (which lists these clips
in the feed and the contact sheet — it does not re-render them).

Run:  pip install imageio-ffmpeg && python tools/build_icon_echo_loops.py
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_motion_feed import (  # noqa: E402
    DURATION, FPS, H, THUMB_H, THUMB_W, W, build_clip, build_thumb)
from build_icon_echo_wallpapers import wall_tones  # noqa: E402

LIVE = ROOT / "Motion" / "live"
FIRST_CLIP = 29


def _hex(rgb: tuple[int, int, int]) -> str:
    return f"0x{rgb[0]:02x}{rgb[1]:02x}{rgb[2]:02x}"


def icon_echo_clips():
    """(slug, title, ffmpeg src, crf) for the three icon-echo loops.

    c1 is the icon's accent shifted a step toward the night ground (the
    wall_tones `mid`), so the field is the icon's hue, TV-safe.
    """
    red = _hex(wall_tones((255, 0, 51))[1])        # YouTube
    green = _hex(wall_tones((30, 215, 96))[1])     # Spotify
    amber = _hex(wall_tones((248, 160, 0))[1])     # MaxPlayer
    night = "0x04070f"
    return [
        dict(slug="signal-sweep", title="Signal Sweep",
             src=(f"gradients=size={W}x{H}:rate={FPS}:type=linear:nb_colors=3:"
                  f"c0={night}:c1={red}:c2={night}:"
                  f"duration={DURATION}:speed=0.02:seed=881"),
             vf=None, crf=20),
        dict(slug="green-current", title="Green Current",
             src=(f"gradients=size={W}x{H}:rate={FPS}:type=spiral:nb_colors=3:"
                  f"c0={night}:c1={green}:c2={night}:"
                  f"duration={DURATION}:speed=0.02:seed=882"),
             vf=None, crf=20),
        dict(slug="molten-orbit", title="Molten Orbit",
             src=(f"gradients=size={W}x{H}:rate={FPS}:type=radial:nb_colors=3:"
                  f"c0={night}:c1={amber}:c2={night}:"
                  f"duration={DURATION}:speed=0.02:seed=883"),
             vf=None, crf=20),
    ]


def clip_name(index: int, slug: str) -> str:
    return f"coremotion-live-{FIRST_CLIP + index:02d}-{slug}"


def entries():
    """(file stem, title) for the feed, in loop order."""
    return [(clip_name(i, e["slug"]), e["title"])
            for i, e in enumerate(icon_echo_clips())]


def main() -> int:
    (LIVE / "thumbs").mkdir(parents=True, exist_ok=True)
    for i, e in enumerate(icon_echo_clips()):
        mp4 = LIVE / f"{clip_name(i, e['slug'])}.mp4"
        thumb = LIVE / "thumbs" / f"{clip_name(i, e['slug'])}.jpg"
        print(f"building {mp4.name} ...")
        build_clip(e, str(mp4))
        build_thumb(str(mp4), str(thumb))
    print(f"done: {len(icon_echo_clips())} icon-echo loops")
    return 0


if __name__ == "__main__":
    sys.exit(main())
