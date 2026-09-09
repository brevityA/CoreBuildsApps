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

def w51_vortex() -> Image.Image:
    """Tight spiral vortex pulling toward upper-left, cyan trails on void."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    cx, cy = W * 0.38, H * 0.32
    for i in range(600):
        t = i / 600.0
        angle = t * 14 * math.pi
        r = 40 + t * 1800
        x = cx + r * math.cos(angle)
        y = cy + r * math.sin(angle)
        # Trail width thins outward
        width = max(1, int(8 - t * 7))
        alpha = max(0.05, 1.0 - t * 1.1)
        c = lerp_color(CYAN, VOID, 1.0 - alpha)
        # Short segment from previous point
        t_prev = max(0, (i - 1) / 600.0)
        angle_prev = t_prev * 14 * math.pi
        r_prev = 40 + t_prev * 1800
        x_prev = cx + r_prev * math.cos(angle_prev)
        y_prev = cy + r_prev * math.sin(angle_prev)
        draw.line([(x_prev, y_prev), (x, y)], fill=c, width=width)
    # Fade bottom half to void
    for y in range(H // 2, H):
        t = (y - H // 2) / (H // 2)
        alpha = int(t * 255)
        draw.line([(0, y), (W, y)], fill=(*VOID, 255), width=1)
    overlay = Image.new("RGB", (W, H), VOID)
    mask = Image.new("L", (W, H), 0)
    mask_draw = ImageDraw.Draw(mask)
    for y in range(H // 2, H):
        t = (y - H // 2) / (H // 2)
        val = int(t * 255)
        mask_draw.line([(0, y), (W, y)], fill=val)
    im = Image.composite(overlay, im, mask)
    return im


def w52_shockwave() -> Image.Image:
    """Concentric rings expanding from center-right, ember to void."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    cx, cy = W * 0.62, H * 0.36
    for i in range(80, 0, -1):
        r = i * 28
        t = i / 80.0
        c = lerp_color(VOID, EMBER, t * 0.7)
        width = max(2, int(4 * t))
        bbox = [cx - r, cy - r, cx + r, cy + r]
        draw.ellipse(bbox, outline=c, width=width)
    # Bright core
    for i in range(12):
        r = 20 + i * 8
        c = lerp_color(GLOW_CYAN, CYAN, i / 12.0)
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=c, width=3)
    # Fade bottom
    overlay = Image.new("RGB", (W, H), VOID)
    mask = Image.new("L", (W, H), 0)
    mask_draw = ImageDraw.Draw(mask)
    for y in range(int(H * 0.45), H):
        t = (y - H * 0.45) / (H * 0.55)
        mask_draw.line([(0, y), (W, y)], fill=int(min(1, t * 1.3) * 255))
    im = Image.composite(overlay, im, mask)
    return im


def w53_cascade() -> Image.Image:
    """Falling particle streams — vertical lines of varying speed and brightness."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    rng = np.random.RandomState(42)
    for _ in range(280):
        x = rng.randint(0, W)
        y_start = rng.randint(0, int(H * 0.65))
        length = rng.randint(60, 400)
        width = rng.choice([1, 1, 2, 2, 3])
        brightness = rng.uniform(0.15, 0.85)
        c = lerp_color(VOID, CYAN, brightness)
        draw.line([(x, y_start), (x, y_start + length)], fill=c, width=width)
    # Bright accent particles
    for _ in range(30):
        x = rng.randint(0, W)
        y = rng.randint(0, int(H * 0.5))
        r = rng.randint(2, 5)
        draw.ellipse([x - r, y - r, x + r, y + r], fill=GLOW_CYAN)
    # Fade bottom
    overlay = Image.new("RGB", (W, H), VOID)
    mask = Image.new("L", (W, H), 0)
    mask_draw = ImageDraw.Draw(mask)
    for y in range(int(H * 0.5), H):
        t = (y - H * 0.5) / (H * 0.5)
        mask_draw.line([(0, y), (W, y)], fill=int(min(1, t * 1.4) * 255))
    im = Image.composite(overlay, im, mask)
    # Subtle blur for glow effect
    im = im.filter(ImageFilter.GaussianBlur(radius=2))
    return im


def w54_drift_field() -> Image.Image:
    """Flowing vector field lines — particles swept by invisible current."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    rng = np.random.RandomState(7)
    # Flow field: each particle traces a path influenced by noise-like angles
    for _ in range(400):
        x = rng.uniform(0, W)
        y = rng.uniform(0, H * 0.7)
        points = [(x, y)]
        brightness = rng.uniform(0.2, 0.7)
        for step in range(60):
            # Angle from a simple flow pattern
            angle = (math.sin(x / 300) * math.cos(y / 200) * math.pi +
                     math.sin(y / 400 + x / 500) * 0.8)
            x += math.cos(angle) * 8
            y += math.sin(angle) * 8
            points.append((x, y))
            if x < -100 or x > W + 100 or y < -100 or y > H + 100:
                break
        if len(points) > 2:
            c = lerp_color(VOID, BUILD_BLUE, brightness)
            draw.line(points, fill=c, width=1)
    # A few bright accent lines
    for _ in range(20):
        x = rng.uniform(W * 0.2, W * 0.8)
        y = rng.uniform(H * 0.1, H * 0.4)
        points = [(x, y)]
        for step in range(80):
            angle = (math.sin(x / 300) * math.cos(y / 200) * math.pi +
                     math.sin(y / 400 + x / 500) * 0.8)
            x += math.cos(angle) * 8
            y += math.sin(angle) * 8
            points.append((x, y))
        if len(points) > 2:
            draw.line(points, fill=CYAN, width=2)
    # Fade bottom
    overlay = Image.new("RGB", (W, H), VOID)
    mask = Image.new("L", (W, H), 0)
    mask_draw = ImageDraw.Draw(mask)
    for y in range(int(H * 0.5), H):
        t = (y - H * 0.5) / (H * 0.5)
        mask_draw.line([(0, y), (W, y)], fill=int(min(1, t * 1.5) * 255))
    im = Image.composite(overlay, im, mask)
    im = im.filter(ImageFilter.GaussianBlur(radius=1.5))
    return im


# ---------------------------------------------------------------------------
# Series 3: Horizons — atmospheric gradient landscapes, single accent
# ---------------------------------------------------------------------------

def w55_horizon_dusk() -> Image.Image:
    """Dusk violet horizon with purple-to-void gradient."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    horizon_y = H * 0.35
    # Sky gradient: void -> dusk violet -> void
    for y in range(H):
        if y < horizon_y:
            t = y / horizon_y
            # Dark at top, dusk at horizon
            c = lerp_color(VOID, DUSK, t ** 1.5 * 0.6)
        else:
            t = (y - horizon_y) / (H - horizon_y)
            c = lerp_color(DUSK, VOID, t ** 0.6)
            # Fade to near-black quickly
            dark_t = min(1, t * 1.8)
            c = lerp_color(c, VOID, dark_t * 0.7)
        draw.line([(0, y), (W, y)], fill=c)
    # Bright horizon line
    for dy in range(-4, 5):
        y = int(horizon_y) + dy
        alpha = 1.0 - abs(dy) / 5.0
        c = lerp_color(VOID, (180, 100, 200), alpha * 0.5)
        draw.line([(0, y), (W, y)], fill=c)
    # Subtle glow at horizon
    for dy in range(-40, 41):
        y = int(horizon_y) + dy
        if 0 <= y < H:
            alpha = (1.0 - abs(dy) / 40.0) ** 2 * 0.15
            c = lerp_color(VOID, (160, 80, 180), alpha)
            draw.line([(0, y), (W, y)], fill=c)
    return im


def w56_horizon_frost() -> Image.Image:
    """Ice-blue horizon — cool whites fading to void."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    horizon_y = H * 0.33
    frost = (180, 210, 230)
    ice = (100, 160, 200)
    for y in range(H):
        if y < horizon_y:
            t = y / horizon_y
            c = lerp_color(VOID, ice, t ** 1.8 * 0.45)
        else:
            t = (y - horizon_y) / (H - horizon_y)
            c = lerp_color(ice, VOID, t ** 0.5)
            c = lerp_color(c, VOID, min(1, t * 1.6) * 0.8)
        draw.line([(0, y), (W, y)], fill=c)
    # Bright frost band at horizon
    for dy in range(-30, 31):
        y = int(horizon_y) + dy
        if 0 <= y < H:
            alpha = (1.0 - abs(dy) / 30.0) ** 2 * 0.25
            c = lerp_color(VOID, frost, alpha)
            draw.line([(0, y), (W, y)], fill=c)
    return im


def w57_horizon_signal() -> Image.Image:
    """Signal cyan horizon — the brand colour as landscape."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    horizon_y = H * 0.34
    for y in range(H):
        if y < horizon_y:
            t = y / horizon_y
            c = lerp_color(NIGHT, CYAN, t ** 2.0 * 0.35)
        else:
            t = (y - horizon_y) / (H - horizon_y)
            base = lerp_color(CYAN, VOID, t ** 0.4)
            c = lerp_color(base, VOID, min(1, t * 1.5) * 0.85)
        draw.line([(0, y), (W, y)], fill=c)
    # Cyan glow band
    for dy in range(-50, 51):
        y = int(horizon_y) + dy
        if 0 <= y < H:
            alpha = (1.0 - abs(dy) / 50.0) ** 2 * 0.3
            c = lerp_color(VOID, GLOW_CYAN, alpha)
            draw.line([(0, y), (W, y)], fill=c)
    return im


def w58_horizon_ember() -> Image.Image:
    """Deep ember horizon — warm glow at the edge of night."""
    im = Image.new("RGB", (W, H), VOID)
    draw = ImageDraw.Draw(im)
    horizon_y = H * 0.36
    warm = (220, 90, 30)
    deep_ember = (140, 40, 15)
    for y in range(H):
        if y < horizon_y:
            t = y / horizon_y
            c = lerp_color(VOID, deep_ember, t ** 1.6 * 0.4)
        else:
            t = (y - horizon_y) / (H - horizon_y)
            base = lerp_color(deep_ember, VOID, t ** 0.5)
            c = lerp_color(base, VOID, min(1, t * 1.4) * 0.75)
        draw.line([(0, y), (W, y)], fill=c)
    # Warm glow at horizon
    for dy in range(-35, 36):
        y = int(horizon_y) + dy
        if 0 <= y < H:
            alpha = (1.0 - abs(dy) / 35.0) ** 2 * 0.35
            c = lerp_color(VOID, warm, alpha)
            draw.line([(0, y), (W, y)], fill=c)
    return im


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
