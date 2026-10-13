#!/usr/bin/env python3
"""
Core Builds Motion — every still-derived live loop, from one engine.

Each loop in `tools/live_roster.py` (bar Cinema, which draws its own layers)
is a committed wall given time, not a stand-in pattern that borrows its
name:

  * its own points of light twinkle, each on its own phase and rate;
  * its soft light shimmers with a slow travelling wave;
  * the camera drifts and breathes, a few pixels, for parallax.

Every motion completes a whole number of cycles over the clip, so frame N
equals frame 0 and the loop has no seam. Deterministic: same still, same
seed, same frames. The committed still is the source of truth for the
composition; this only adds time. Per-set tuning (how much twinkle, how much
shimmer) lives on the roster: Deep Space wants its stars, Icon Echo's soft
washes want calm, Motion sits between.

How the still is split, at a 1.1x working canvas:
  detail = still - blur(still)   (clipped >= 0): points and hairline edges
  glow   = still - detail        everything soft
  frame  = glow * shimmer(x, y, t) + detail + star * twinkle(t)
then a moving crop is resampled to 1920x1080 and piped to ffmpeg.

Writes, per clip:
  Motion/live/<stem>.mp4          silent H.264 1080p 60fps 20s, CRF 22
  Motion/live/thumbs/<stem>.jpg   poster frame (1 s in)

The feed is written by tools/build_motion_feed.py from the same roster.

    pip install imageio-ffmpeg numpy pillow
    python tools/build_still_loops.py                 # every still loop
    python tools/build_still_loops.py --set "Motion"  # one set
    python tools/build_still_loops.py --only 32       # one clip number
    python tools/build_still_loops.py --stale         # only clips older than their still
"""
from __future__ import annotations

import argparse
import math
import os
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from live_roster import (  # noqa: E402
    DURATION, FPS, H as OUT_H, LIVE, ROOT, STILL_LOOPS, THUMB_H, THUMB_W,
    W as OUT_W, Clip)

FRAMES = FPS * DURATION
CANVAS = 1.1                  # working canvas over the output, room to drift
MARGIN = 40                   # canvas px the crop keeps clear of the edge
ZOOM = 0.035                  # breathing zoom amplitude (3.5% at the peak)
PAN_X, PAN_Y = 26.0, 14.0     # drift amplitude, canvas px
DETAIL_BLUR = 2.2             # px; what counts as a point
CRF = 22


def ffmpeg() -> str:
    try:
        import imageio_ffmpeg  # type: ignore
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"


def _box_count(mask: np.ndarray, size: int) -> np.ndarray:
    """How many True pixels fall in each pixel's size x size window."""
    pad = size // 2
    m = np.pad(mask.astype("int32"), pad)
    c = m.cumsum(0).cumsum(1)
    c = np.pad(c, ((1, 0), (1, 0)))
    h, w = mask.shape
    return (c[size:size + h, size:size + w] - c[:h, size:size + w]
            - c[size:size + h, :w] + c[:h, :w])


class Scene:
    """One still, split into layers, ready to render any t in [0, 1)."""

    def __init__(self, still: Path, seed: int, twinkle: float, shimmer: float):
        self.twinkle_amp = twinkle
        self.shimmer_amp = shimmer
        img = Image.open(still).convert("RGB")
        cw, ch = round(OUT_W * CANVAS), round(OUT_H * CANVAS)
        img = img.resize((cw, ch), Image.LANCZOS)
        src = np.asarray(img, dtype="float32")
        blur = np.asarray(img.filter(ImageFilter.GaussianBlur(DETAIL_BLUR)),
                          dtype="float32")
        self.detail = np.clip(src - blur, 0, None)
        self.glow = src - self.detail
        self.w, self.h = cw, ch

        rng = np.random.default_rng(seed)
        # Stars: pixels where the point layer is a real peak. Each gets a
        # phase and a whole-number rate, so every star returns to its start
        # at t = 1. Neighbouring pixels of one sparkle share a 3x3 block's
        # phase, so a star pulses as one thing rather than fizzing.
        luma = self.detail.mean(axis=2)
        ys, xs = np.nonzero(luma > 6.0)
        self.star_y, self.star_x = ys, xs
        block = (ys // 3) * (cw // 3 + 1) + (xs // 3)
        uniq, inverse = np.unique(block, return_inverse=True)
        phase = rng.random(len(uniq)).astype("float32")
        rate = rng.integers(1, 4, len(uniq)).astype("float32")
        self.star_phase = phase[inverse]
        self.star_rate = rate[inverse]
        self.star_rgb = self.detail[ys, xs]
        # Only isolated points twinkle. A hairline (a photon ring, an orbit,
        # a planet's limb) is dense with detail pixels and stays steady;
        # letting it twinkle breaks it into a dotted line.
        density = _box_count(luma > 6.0, 7)[ys, xs]
        self.star_amp = (self.twinkle_amp * np.exp(-np.maximum(density - 4, 0) / 3.0)
                         ).astype("float32")

        # Shimmer: two slow waves at fixed angles across the canvas, in
        # units of "fraction of the loop", precomputed per pixel.
        yy, xx = np.mgrid[0:ch, 0:cw].astype("float32")
        a1, a2 = rng.uniform(0, math.pi, 2)
        self.wave1 = (xx * math.cos(a1) + yy * math.sin(a1)) / (cw * 0.9)
        self.wave2 = (xx * math.cos(a2) + yy * math.sin(a2)) / (cw * 0.55)
        # Keep the launcher-card third calm: shimmer fades out below 60%.
        calm = np.clip((0.72 - yy / ch) / 0.12, 0, 1)
        self.calm = calm

    def frame(self, t: float) -> Image.Image:
        two_pi = 2 * math.pi
        shimmer = 1.0 + self.shimmer_amp * self.calm * (
            0.6 * np.sin(two_pi * (t - self.wave1))
            + 0.4 * np.sin(two_pi * (2 * t + self.wave2)))
        # All the still's detail, every frame (dim edges included); the
        # twinkle only adds its swing on top of the stars' own light.
        canvas = self.glow * shimmer[..., None] + self.detail
        tw = self.star_amp * np.sin(two_pi * (self.star_rate * t + self.star_phase))
        canvas[self.star_y, self.star_x] += self.star_rgb * tw[:, None]
        img = Image.fromarray(np.clip(canvas, 0, 255).astype("uint8"))

        # Camera: breathing zoom (one cosine period) plus a Lissajous drift
        # (1 and 2 cycles), all back to rest at t = 1.
        z = 1.0 + ZOOM * (1 - math.cos(two_pi * t)) / 2
        cw = (self.w - 2 * MARGIN) / z
        ch = cw * OUT_H / OUT_W
        cx = self.w / 2 + PAN_X * math.sin(two_pi * t)
        cy = self.h / 2 + PAN_Y * math.sin(2 * two_pi * t)
        box = (cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2)
        return img.transform((OUT_W, OUT_H), Image.EXTENT, box, Image.BICUBIC)


def render(clip: Clip) -> Path:
    still = clip.still
    if not still.is_file():
        raise SystemExit(f"missing still {still.relative_to(ROOT)}")
    out, thumb = clip.mp4, clip.thumb
    out.parent.mkdir(parents=True, exist_ok=True)
    thumb.parent.mkdir(parents=True, exist_ok=True)

    scene = Scene(still, seed=clip.wall * 1009, twinkle=clip.twinkle, shimmer=clip.shimmer)
    cmd = [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
           "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{OUT_W}x{OUT_H}",
           "-r", str(FPS), "-i", "-",
           "-c:v", "libx264", "-preset", "slow", "-crf", str(CRF),
           "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an",
           str(out)]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    assert proc.stdin is not None
    poster = None
    # Reap ffmpeg on every path, and never leave a half-written MP4 behind:
    # a failed rerun must not sit in Motion/live/ beside its old thumbnail.
    ok = False
    try:
        for f in range(FRAMES):
            img = scene.frame(f / FRAMES)
            if f == FPS:            # 1 s in
                poster = img
            proc.stdin.write(img.tobytes())
        ok = True
    finally:
        try:
            proc.stdin.close()
        except OSError:
            pass
        rc = proc.wait()
        if not ok or rc != 0:
            out.unlink(missing_ok=True)
    if rc != 0:
        raise SystemExit(f"ffmpeg failed on {clip.stem}")
    assert poster is not None
    poster.resize((THUMB_W, THUMB_H), Image.LANCZOS).save(thumb, quality=88)
    size = out.stat().st_size / 1e6
    print(f"✓ {out.relative_to(ROOT)}  {size:.1f} MB", flush=True)
    return out


def stale(clip: Clip) -> bool:
    """True when the clip is missing or older than the still it came from."""
    return (not clip.mp4.is_file() or not clip.thumb.is_file()
            or clip.mp4.stat().st_mtime < clip.still.stat().st_mtime)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument("--only", type=int, action="append",
                        help="clip number(s) to render, e.g. --only 32")
    parser.add_argument("--set", action="append", dest="sets",
                        help='set name(s): "Motion", "Deep Space", "Icon Echo"')
    parser.add_argument("--stale", action="store_true",
                        help="render only clips missing or older than their still")
    args = parser.parse_args(argv)
    todo = [c for c in STILL_LOOPS
            if (not args.only or c.number in args.only)
            and (not args.sets or c.set_name in args.sets)
            and (not args.stale or stale(c))]
    print(f"{len(todo)} clip(s) to render", flush=True)
    for c in todo:
        render(c)
    return 0


if __name__ == "__main__":
    sys.exit(main())
