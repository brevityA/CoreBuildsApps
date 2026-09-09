#!/usr/bin/env python3
"""
Core Builds Classic — new wallpapers for series-2-motion and series-3-horizons.

Adds 4 walls to each series (8 total), bringing both from 8 to 12.
All 3840x2160 PNG, dark by default, calm lower third.

Writes:
  Wallpapers/series-2-motion/corebuilds-NN-slug.png        3840x2160
  Wallpapers/series-3-horizons/corebuilds-NN-slug.png      3840x2160
  Wallpapers/thumbs/corebuilds-NN-slug.jpg                 480x270
  app/src/main/assets/wallpapers_thumbs/corebuilds-NN-slug.jpg  bundled thumbs

Does NOT rewrite the manifest — that is done separately after visual review.
"""
from __future__ import annotations

import io
import math
import os
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter
import numpy as np

ROOT = Path(__file__).resolve().parent.parent
THUMBS = ROOT / "Wallpapers" / "thumbs"
BUNDLED_THUMBS = ROOT / "app" / "src" / "main" / "assets" / "wallpapers_thumbs"

W, H = 3840, 2160
THUMB_W, THUMB_H = 480, 270

# Core Builds palette
NIGHT = (13, 17, 23)
VOID = (4, 7, 15)
PANEL = (26, 32, 48)
CYAN = (0, 212, 255)
EMBER = (192, 58, 32)
DUSK = (138, 72, 144)
BUILD_BLUE = (79, 172, 254)
GLOW_CYAN = (126, 238, 255)
SLATE = (139, 148, 158)


def lerp_color(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def dark_coverage(im) -> float:
    a = np.asarray(im.convert("L"), dtype="float32") / 255.0
    return float((a <= 0.14).mean())


def save_wall(im: Image.Image, series_dir: Path, stem: str):
    series_dir.mkdir(parents=True, exist_ok=True)
    THUMBS.mkdir(parents=True, exist_ok=True)
    BUNDLED_THUMBS.mkdir(parents=True, exist_ok=True)

    out = series_dir / f"{stem}.png"
    im.save(out, "PNG", optimize=True)
    size = out.stat().st_size

    thumb = im.resize((THUMB_W, THUMB_H), Image.LANCZOS)
    thumb_path = THUMBS / f"{stem}.jpg"
    thumb.save(thumb_path, "JPEG", quality=88, optimize=True)
    shutil.copy2(thumb_path, BUNDLED_THUMBS / f"{stem}.jpg")

    dc = dark_coverage(im)
    print(f"  ✓ {stem:42} {size / 1024:7.0f} KB  dark {dc:.0%}")
    return size


# ---------------------------------------------------------------------------
# Series 2: Motion — kinetic energy, orbital paths, dynamic fields
# ---------------------------------------------------------------------------

def _bottom_fade(arr, start_frac=0.45):
    """Fade pixel array to VOID from start_frac downward."""
    fade_start = int(H * start_frac)
    fade_len = H - fade_start
    if fade_len <= 0:
        return arr
    void = np.array(VOID, dtype="float32")
    t = np.linspace(0, 1, fade_len, dtype="float32") ** 1.2
    t = t[:, None, None]
    arr[fade_start:] = arr[fade_start:] * (1 - t) + void[None, None, :] * t
    return arr


def w51_vortex() -> Image.Image:
    """Three-arm spiral vortex, cyan trails on void. Drawn with thick lines + glow blur."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    cx, cy = W * 0.36, H * 0.30
    for arm in range(3):
        arm_offset = arm * 2.0 * math.pi / 3.0
        prev = None
        for i in range(800):
            t = i / 800.0
            angle = t * 16 * math.pi + arm_offset
            r = 30 + t * 2100
            px = cx + r * math.cos(angle)
            py = cy + r * math.sin(angle)
            if prev is not None:
                brightness = max(0.08, 1.0 - t * 0.9)
                c = lerp_color(VOID, GLOW_CYAN if t < 0.12 else CYAN, brightness)
                width = max(3, int(20 - t * 17))
                draw.line([prev, (px, py)], fill=c, width=width)
            prev = (px, py)
    glow = im.filter(ImageFilter.GaussianBlur(radius=12))
    im = Image.blend(im, glow, 0.5)
    arr = np.asarray(im, dtype="float32").copy()
    arr = _bottom_fade(arr, 0.42)
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))


def w52_shockwave() -> Image.Image:
    """Concentric rings expanding from center-right, ember to void. Thick rings + glow."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    cx, cy = W * 0.60, H * 0.34
    for i in range(60, 0, -1):
        r = i * 36
        t = i / 60.0
        c = lerp_color(VOID, EMBER, t * 0.85)
        width = max(3, int(10 * t + 4))
        bbox = [cx - r, cy - r, cx + r, cy + r]
        draw.ellipse(bbox, outline=c, width=width)
    for i in range(20):
        r = 15 + i * 12
        blend = i / 20.0
        c = lerp_color(GLOW_CYAN, CYAN, blend)
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=c, width=6)
    r_core = 25
    draw.ellipse([cx - r_core, cy - r_core, cx + r_core, cy + r_core], fill=GLOW_CYAN)
    glow = im.filter(ImageFilter.GaussianBlur(radius=10))
    im = Image.blend(im, glow, 0.45)
    arr = np.asarray(im, dtype="float32").copy()
    arr = _bottom_fade(arr, 0.40)
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))


def w53_cascade() -> Image.Image:
    """Falling particle streams — vertical lines of varying speed and brightness."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    rng = np.random.RandomState(42)
    for _ in range(600):
        x = rng.randint(0, W)
        y_start = rng.randint(0, int(H * 0.6))
        length = rng.randint(80, 600)
        width = rng.choice([2, 3, 3, 4, 5, 6])
        brightness = rng.uniform(0.15, 0.9)
        c = lerp_color(VOID, CYAN, brightness)
        draw.line([(x, y_start), (x, y_start + length)], fill=c, width=width)
    for _ in range(60):
        x = rng.randint(0, W)
        y = rng.randint(0, int(H * 0.45))
        r = rng.randint(3, 10)
        draw.ellipse([x - r, y - r, x + r, y + r], fill=GLOW_CYAN)
    im = im.filter(ImageFilter.GaussianBlur(radius=3))
    arr = np.asarray(im, dtype="float32")
    arr = _bottom_fade(arr.copy(), 0.45)
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))


def w54_drift_field() -> Image.Image:
    """Flowing vector field lines — particles swept by invisible current."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    rng = np.random.RandomState(7)
    for _ in range(800):
        x = rng.uniform(0, W)
        y = rng.uniform(0, H * 0.7)
        points = [(x, y)]
        brightness = rng.uniform(0.2, 0.75)
        for step in range(80):
            angle = (math.sin(x / 300) * math.cos(y / 200) * math.pi +
                     math.sin(y / 400 + x / 500) * 0.8)
            x += math.cos(angle) * 8
            y += math.sin(angle) * 8
            points.append((x, y))
            if x < -100 or x > W + 100 or y < -100 or y > H + 100:
                break
        if len(points) > 2:
            c = lerp_color(VOID, BUILD_BLUE, brightness)
            draw.line(points, fill=c, width=rng.choice([2, 2, 3, 3, 4]))
    for _ in range(50):
        x = rng.uniform(W * 0.1, W * 0.9)
        y = rng.uniform(H * 0.05, H * 0.45)
        points = [(x, y)]
        for step in range(100):
            angle = (math.sin(x / 300) * math.cos(y / 200) * math.pi +
                     math.sin(y / 400 + x / 500) * 0.8)
            x += math.cos(angle) * 8
            y += math.sin(angle) * 8
            points.append((x, y))
        if len(points) > 2:
            draw.line(points, fill=CYAN, width=rng.choice([3, 4, 5]))
    im = im.filter(ImageFilter.GaussianBlur(radius=2))
    arr = np.asarray(im, dtype="float32")
    arr = _bottom_fade(arr.copy(), 0.45)
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))


# ---------------------------------------------------------------------------
# Series 3: Horizons — atmospheric gradient landscapes, single accent
# ---------------------------------------------------------------------------

def _horizon(accent, glow_color, horizon_frac=0.32, accent_strength=0.55,
              glow_strength=0.35, glow_radius=100):
    """Build a horizon wallpaper with smooth gradient and additive glow.

    Uses numpy arrays: base gradient void->accent->void, then additive
    glow band, then hard fade to void for the lower third.
    """
    void = np.array(VOID, dtype="float32")
    acc = np.array(accent, dtype="float32")
    gl = np.array(glow_color, dtype="float32")
    arr = np.zeros((H, W, 3), dtype="float32")
    horizon_y = int(H * horizon_frac)
    ys = np.arange(H, dtype="float32")
    for y_idx in range(H):
        y = float(y_idx)
        if y_idx < horizon_y:
            t = (y / horizon_y) ** 1.6
            row_c = void + (acc - void) * t * accent_strength
        else:
            t = ((y - horizon_y) / (H - horizon_y))
            row_c = acc * accent_strength + void * (1 - accent_strength)
            fade = t ** 0.7
            row_c = row_c * (1 - fade) + void * fade
        arr[y_idx, :] = row_c[None, :]
    dist_from_horizon = np.abs(ys - horizon_y)
    glow_mask = np.exp(-(dist_from_horizon ** 2) / (2 * glow_radius ** 2))
    glow_mask = glow_mask * glow_strength
    arr += glow_mask[:, None, None] * (gl[None, None, :] - arr) * 0.6
    arr += glow_mask[:, None, None] * gl[None, None, :] * 0.15
    thin_mask = np.exp(-(dist_from_horizon ** 2) / (2 * 8 ** 2)) * 0.5
    arr += thin_mask[:, None, None] * gl[None, None, :] * 0.3
    lower_start = int(H * 0.55)
    for y_idx in range(lower_start, H):
        t = ((y_idx - lower_start) / (H - lower_start)) ** 1.0
        arr[y_idx] = arr[y_idx] * (1 - t) + void * t
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))


def w55_horizon_dusk() -> Image.Image:
    """Dusk violet horizon with purple-to-void gradient."""
    return _horizon(
        accent=DUSK,
        glow_color=(180, 100, 200),
        horizon_frac=0.32,
        accent_strength=0.6,
        glow_strength=0.4,
        glow_radius=120,
    )


def w56_horizon_frost() -> Image.Image:
    """Ice-blue horizon — cool whites fading to void."""
    return _horizon(
        accent=(90, 140, 180),
        glow_color=(170, 210, 235),
        horizon_frac=0.30,
        accent_strength=0.5,
        glow_strength=0.35,
        glow_radius=100,
    )


def w57_horizon_signal() -> Image.Image:
    """Signal cyan horizon — the brand colour as landscape."""
    return _horizon(
        accent=CYAN,
        glow_color=GLOW_CYAN,
        horizon_frac=0.31,
        accent_strength=0.4,
        glow_strength=0.4,
        glow_radius=130,
    )


def w58_horizon_ember() -> Image.Image:
    """Deep ember horizon — warm glow at the edge of night."""
    return _horizon(
        accent=(140, 40, 15),
        glow_color=(220, 90, 30),
        horizon_frac=0.33,
        accent_strength=0.5,
        glow_strength=0.4,
        glow_radius=90,
    )


# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------

MOTION_DIR = ROOT / "Wallpapers" / "series-2-motion"
HORIZONS_DIR = ROOT / "Wallpapers" / "series-3-horizons"

MOTION_WALLS = [
    ("51", "vortex", "51 Vortex", w51_vortex),
    ("52", "shockwave", "52 Shockwave", w52_shockwave),
    ("53", "cascade", "53 Cascade", w53_cascade),
    ("54", "drift-field", "54 Drift Field", w54_drift_field),
]

HORIZON_WALLS = [
    ("55", "horizon-dusk", "55 Horizon Dusk", w55_horizon_dusk),
    ("56", "horizon-frost", "56 Horizon Frost", w56_horizon_frost),
    ("57", "horizon-signal", "57 Horizon Signal", w57_horizon_signal),
    ("58", "horizon-ember-deep", "58 Horizon Ember Deep", w58_horizon_ember),
]


def main() -> int:
    total = 0
    print("Series 2: Motion (4 new)")
    for num, slug, title, fn in MOTION_WALLS:
        stem = f"corebuilds-{num}-{slug}"
        im = fn()
        total += save_wall(im, MOTION_DIR, stem)

    print("\nSeries 3: Horizons (4 new)")
    for num, slug, title, fn in HORIZON_WALLS:
        stem = f"corebuilds-{num}-{slug}"
        im = fn()
        total += save_wall(im, HORIZONS_DIR, stem)

    print(f"\n✓ 8 wallpapers · {total / 1e6:.1f} MB total")
    print("  Thumbs bundled to app/src/main/assets/wallpapers_thumbs/")
    print("  Next: review, update wallpapers.json manifest, commit.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
