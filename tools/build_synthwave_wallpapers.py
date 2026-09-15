#!/usr/bin/env python3
"""
Core Builds Classic — series-7-retrowave.

2026 field notes (tukkbook trend roundup, Pinterest colour forecast via
Elle Decor, picwand style census) put nostalgic retro-gradient / Y2K
synthwave among the most-searched device-wallpaper genres, and the pack
had none: its dark abstracts stop at mesh, aurora and circuit work. This
series adds twelve 4K walls in the genre's grammar — sliced gradient sun,
perspective grid, starfield, chrome ridge — kept on the pack's night
ground with a calm, dim lower third so launcher cards still hold.

Writes:
  Wallpapers/series-7-retrowave/corebuilds-NN-slug.png   3840x2160
  Wallpapers/thumbs/corebuilds-NN-slug.jpg               480x270
  app/src/main/assets/wallpapers_thumbs/corebuilds-NN-slug.jpg

Does NOT rewrite the manifest — that is done separately after visual
review, same as every other series.
"""
from __future__ import annotations

import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from build_classic_wallpapers import (H, NIGHT, VOID, W, lerp_color,
                                      save_wall)

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-7-retrowave"

# genre palettes: (sun top, sun bottom, grid/ink)
MIAMI = ((0, 212, 255), (255, 46, 136), (255, 46, 136))
DUSK = ((162, 56, 240), (192, 58, 32), (162, 56, 240))
CHROME = ((155, 232, 255), (79, 172, 254), (0, 212, 255))
ULTRA = ((255, 94, 158), (255, 154, 61), (255, 94, 158))
JADE = ((0, 184, 169), (0, 212, 255), (0, 184, 169))
NOIR = ((107, 45, 92), (226, 62, 87), (226, 62, 87))
WASABI = ((184, 230, 46), (0, 184, 169), (184, 230, 46))
COOL = ((38, 70, 160), (0, 212, 255), (126, 238, 255))
EMBER = ((255, 154, 61), (192, 58, 32), (255, 154, 61))
OUTRUN = ((255, 46, 136), (0, 212, 255), (0, 212, 255))


def _sky(horizon_y: int, top: tuple, mid: tuple) -> np.ndarray:
    """Vertical sky gradient: `top` at y=0 to `mid` at the horizon, VOID below."""
    arr = np.zeros((H, W, 3), dtype="float32")
    ys = np.arange(H, dtype="float32")
    t = np.clip(ys / max(1, horizon_y), 0, 1)[:, None]
    col = np.array(top, dtype="float32") * (1 - t) + np.array(mid, dtype="float32") * t
    arr[:horizon_y] = col[:horizon_y][:, None, :] * 0.34  # keep the sky moody
    arr[horizon_y:] = np.array(VOID, dtype="float32")
    return arr


def _stars(im: Image.Image, seed: int, n: int, ymax: int) -> None:
    rnd = random.Random(seed)
    px = im.load()
    for _ in range(n):
        x = rnd.randint(0, W - 1)
        y = int(rnd.triangular(0, ymax, ymax * 0.35))
        b = rnd.uniform(0.25, 0.95)
        c = tuple(int(v * b) for v in (200, 226, 255))
        px[x, y] = c
        if b > 0.8:
            px[min(W - 1, x + 1), y] = c
            px[x, min(H - 1, y + 1)] = tuple(v // 2 for v in c)
            px[min(W - 1, x + 1), min(H - 1, y + 1)] = tuple(v // 2 for v in c)


def _sun(im: Image.Image, cx: float, cy: float, r: float, ctop: tuple,
         cbot: tuple, sky_at, clip_y: int | None = None) -> None:
    """Gradient disc with retrowave slice gaps opening toward its base."""
    draw = ImageDraw.Draw(im)
    y0, y1 = int(cy - r), int(cy + r)
    if clip_y is not None:
        y1 = min(y1, clip_y)
    for y in range(max(0, y0), min(H, y1)):
        dy = y - cy
        hw = math.sqrt(max(0.0, r * r - dy * dy))
        t = (y - y0) / max(1, (y1 - y0))
        draw.line([(cx - hw, y), (cx + hw, y)], fill=lerp_color(ctop, cbot, t))
    # slices: gaps grow toward the base, revealing whatever sits behind
    y = cy + r * 0.05
    gap = max(4.0, r * 0.02)
    step = max(10.0, r * 0.09)
    while y < cy + r:
        dy = y - cy
        hw = math.sqrt(max(0.0, r * r - dy * dy))
        draw.line([(cx - hw, y), (cx + hw, y)], fill=sky_at(int(y)),
                  width=int(gap))
        y += step + gap
        step *= 1.12
        gap *= 1.25


def _grid(im: Image.Image, horizon_y: int, colour: tuple, vpx: float,
          density: int = 22, alpha: float = 0.5) -> None:
    mask = Image.new("L", (W, H), 0)
    dm = ImageDraw.Draw(mask)
    for i in range(density + 1):  # converging verticals
        xb = (i / density) * (W * 1.9) - W * 0.45
        xt = vpx + (xb - vpx) * 0.045
        dm.line([(xt, horizon_y), (xb, H)], fill=170, width=4)
    n = 16  # horizontals, quadratically spaced toward the viewer
    for k in range(1, n + 1):
        t = (k / n) ** 2.35
        y = horizon_y + (H - horizon_y) * t
        w = 2 + int(5 * t)
        dm.line([(0, y), (W, y)], fill=150 + int(80 * t), width=w)
    glow = mask.filter(ImageFilter.GaussianBlur(9))
    core = np.asarray(mask, dtype="float32") * alpha
    halo = np.asarray(glow, dtype="float32") * alpha * 0.55
    combined = np.clip(np.maximum(core, halo), 0, 255) / 255.0
    arr = np.asarray(im, dtype="float32")
    ink = np.array(colour, dtype="float32")
    out = arr * (1 - combined[..., None]) + ink[None, None, :] * combined[..., None]
    im.paste(Image.fromarray(np.clip(out, 0, 255).astype("uint8")))


def _ridge(im: Image.Image, horizon_y: int, seed: int, peak: float,
           edge: tuple) -> None:
    rnd = random.Random(seed)
    pts = [(0, horizon_y + 6)]
    x = 0.0
    while x < W:
        x += rnd.uniform(W * 0.05, W * 0.13)
        pts.append((min(W, x), horizon_y - rnd.uniform(peak * 0.25, peak)))
    pts += [(W, horizon_y + 6)]
    draw = ImageDraw.Draw(im)
    draw.polygon(pts, fill=NIGHT)
    draw.line(pts[:-1], fill=edge, width=5)


def wall(seed: int, pal, *, sun_r=0.30, sun_x=0.5, horizon=0.60, grid=True,
         ridge=None, stars=900, scan=False, grid_density=22,
         alpha=0.5) -> Image.Image:
    ctop, cbot, ink = pal
    horizon_y = int(H * horizon)
    arr = _sky(horizon_y, ctop, cbot)
    im = Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))
    _stars(im, seed, stars, horizon_y - 40)

    def sky_at(y: int):
        t = min(1.0, y / max(1, horizon_y))
        return tuple(int(v * 0.34) for v in lerp_color(ctop, cbot, t))

    r = H * sun_r
    drop = 0.85 if ridge else 0.18  # ridge walls set the sun on the skyline
    _sun(im, W * sun_x, horizon_y - r * drop, r, ctop, cbot, sky_at,
         clip_y=horizon_y if ridge else None)
    if ridge:
        _ridge(im, horizon_y, seed + 7, H * ridge, ink)
    if grid:
        _grid(im, horizon_y, ink, W * sun_x, grid_density, alpha)
    if scan:
        band = Image.new("RGB", (W, H), (0, 0, 0))
        bd = ImageDraw.Draw(band)
        for y in range(0, H, 8):
            bd.line([(0, y), (W, y)], fill=(6, 8, 12), width=2)
        im = Image.blend(im, band, 0.16)
    # calm the lower fifth so dock cards keep a quiet floor
    arr = np.asarray(im, dtype="float32")
    fade = np.linspace(0, 0.45, H - int(H * 0.80), dtype="float32")[:, None, None]
    arr[int(H * 0.80):] *= (1 - fade)
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))


WALLS = [
    (59, "synth-miami", dict(seed=11, pal=MIAMI)),
    (60, "synth-dusk", dict(seed=12, pal=DUSK, sun_r=0.34, sun_x=0.42)),
    (61, "synth-chrome", dict(seed=13, pal=CHROME, ridge=0.16, sun_r=0.26)),
    (62, "synth-ultra", dict(seed=14, pal=ULTRA, scan=True, sun_x=0.58)),
    (63, "synth-jade", dict(seed=15, pal=JADE, horizon=0.55, grid_density=28)),
    (64, "synth-noir", dict(seed=16, pal=NOIR, sun_r=0.38, alpha=0.38)),
    (65, "synth-wasabi", dict(seed=17, pal=WASABI, sun_x=0.36, ridge=0.12)),
    (66, "synth-outrun", dict(seed=18, pal=OUTRUN, scan=True, sun_r=0.33,
                              grid_density=26)),
    (67, "synth-ridge", dict(seed=19, pal=CHROME, ridge=0.22, sun_r=0.22,
                             sun_x=0.62, alpha=0.42)),
    (68, "synth-starfield", dict(seed=20, pal=COOL, grid=False, sun_r=0.18,
                                 stars=2600, sun_x=0.68)),
    (81, "synth-laser-dusk", dict(seed=21, pal=OUTRUN, horizon=0.64,
                                  sun_r=0.24, sun_x=0.28, ridge=0.18,
                                  scan=True, alpha=0.45)),
    (82, "synth-ember-horizon", dict(seed=22, pal=EMBER, horizon=0.58,
                                     sun_r=0.27, sun_x=0.70, stars=1400,
                                     grid_density=24, alpha=0.40)),
]


def main() -> int:
    for num, slug, kw in WALLS:
        stem = f"corebuilds-{num}-{slug}"
        im = wall(**kw)
        save_wall(im, SERIES, stem)
    print(f"\nseries-7-retrowave complete — {len(WALLS)} walls at 4K.")
    print("  Next: review, update wallpapers.json manifest, commit.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
