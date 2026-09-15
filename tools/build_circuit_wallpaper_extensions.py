#!/usr/bin/env python3
"""Build the two Circuit Core additions that complete its 12-wall set.

The original ten series-6 files pre-date a committed renderer. These two
procedural additions preserve their authored 1376x768 resolution, near-black
field, luminous circuit geometry, and quiet lower third. Output is deterministic
and includes both repository and APK thumbnail copies.
"""
from __future__ import annotations

import random
import shutil
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-6-circuit-core"
THUMBS = ROOT / "Wallpapers" / "thumbs"
BUNDLED_THUMBS = ROOT / "app/src/main/assets/wallpapers_thumbs"
W, H = 1376, 768
CYAN = (0, 226, 246)
BLUE = (79, 172, 254)
VIOLET = (138, 72, 144)
EMBER = (232, 70, 38)


def base(seed: int, top: tuple[int, int, int]) -> Image.Image:
    rnd = np.random.default_rng(seed)
    y = np.linspace(0, 1, H, dtype=np.float32)[:, None, None]
    upper = np.array(top, dtype=np.float32)[None, None, :] * (1 - y * 0.74)
    lower_fade = np.clip((y - 0.55) / 0.45, 0, 1)
    arr = upper * (1 - lower_fade * 0.92)
    arr = np.broadcast_to(arr, (H, W, 3)).copy()
    grain = rnd.normal(0, 2.0, (H, W, 1)).astype(np.float32)
    arr += grain * (1 - lower_fade * 0.75)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")


def glow_lines(im: Image.Image, routes, colour, width=2, blur=8,
               intensity=0.48) -> None:
    mask = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(mask)
    for route in routes:
        d.line(route, fill=255, width=width, joint="curve")
    halo = mask.filter(ImageFilter.GaussianBlur(blur)).point(
        lambda p: min(255, int(p * intensity)))
    ink = Image.new("RGB", im.size, colour)
    im.paste(ink, (0, 0), halo)
    im.paste(ink, (0, 0), mask)


def circuit_nexus() -> Image.Image:
    im = base(7901, (5, 24, 31))
    rnd = random.Random(7901)
    cx, cy = 790, 282
    cyan_routes = []
    violet_routes = []
    starts = [(rnd.randint(60, 1280), rnd.randint(25, 135)) for _ in range(18)]
    starts += [(rnd.choice((35, 1340)), rnd.randint(120, 470)) for _ in range(12)]
    for i, (sx, sy) in enumerate(starts):
        elbow_y = rnd.randint(165, 430)
        elbow_x = cx + rnd.randint(-260, 260)
        route = [(sx, sy), (sx, elbow_y), (elbow_x, elbow_y),
                 (elbow_x, cy), (cx, cy)]
        (cyan_routes if i % 4 else violet_routes).append(route)
    glow_lines(im, cyan_routes, CYAN, 2, 9, 0.38)
    glow_lines(im, violet_routes, VIOLET, 3, 11, 0.44)

    d = ImageDraw.Draw(im)
    # Layered dark hardware blocks around the convergence point.
    for _ in range(25):
        x = rnd.randint(410, 1080)
        y = rnd.randint(125, 445)
        w = rnd.randint(35, 115)
        h = rnd.randint(25, 90)
        d.rectangle((x, y, x + w, y + h), fill=(5, 12, 18),
                    outline=(20, 95, 112), width=2)
        if rnd.random() < 0.55:
            d.ellipse((x + 8, y + 8, x + 14, y + 14), fill=CYAN)
    hub = Image.new("L", im.size, 0)
    hd = ImageDraw.Draw(hub)
    hd.regular_polygon((cx, cy, 78), n_sides=6, rotation=30, fill=255)
    hd.regular_polygon((cx, cy, 52), n_sides=6, rotation=30, fill=0)
    halo = hub.filter(ImageFilter.GaussianBlur(18))
    im.paste(Image.new("RGB", im.size, BLUE), (0, 0), halo)
    im.paste(Image.new("RGB", im.size, CYAN), (0, 0), hub)
    return im


def circuit_vault() -> Image.Image:
    im = base(8002, (23, 10, 28))
    cx, cy = 688, 295
    cyan_routes = []
    ember_routes = []
    # Nested angular frames form a circuit-board vault around a dark portal.
    for i in range(11):
        inset = i * 43
        left, right = 95 + inset, 1281 - inset
        top = 45 + inset * 0.43
        floor = 560 - inset * 0.37
        route = [(left, floor), (left, top + 90), (left + 95, top),
                 (right - 95, top), (right, top + 90), (right, floor)]
        (cyan_routes if i % 3 else ember_routes).append(route)
    glow_lines(im, cyan_routes, CYAN, 2, 8, 0.33)
    glow_lines(im, ember_routes, EMBER, 3, 13, 0.47)

    rnd = random.Random(8002)
    side_routes = []
    for side in (-1, 1):
        for row in range(9):
            y = 105 + row * 43
            x0 = cx + side * (130 + row * 18)
            x1 = 45 if side < 0 else W - 45
            mid = cx + side * rnd.randint(290, 480)
            side_routes.append([(x0, y), (mid, y), (mid, y + 24), (x1, y + 24)])
    glow_lines(im, side_routes, VIOLET, 2, 7, 0.34)

    d = ImageDraw.Draw(im)
    d.polygon([(cx - 140, 205), (cx + 140, 205), (cx + 205, 510),
               (cx - 205, 510)], fill=(2, 5, 10), outline=EMBER, width=4)
    for x in (cx - 165, cx + 155):
        for y in range(250, 470, 52):
            d.ellipse((x - 5, y - 5, x + 5, y + 5), fill=CYAN)
    return im


WALLS = [
    (79, "circuit-nexus", "Circuit Nexus", circuit_nexus),
    (80, "circuit-vault", "Circuit Vault", circuit_vault),
]


def save(im: Image.Image, stem: str) -> int:
    SERIES.mkdir(parents=True, exist_ok=True)
    THUMBS.mkdir(parents=True, exist_ok=True)
    BUNDLED_THUMBS.mkdir(parents=True, exist_ok=True)
    out = SERIES / f"{stem}.jpg"
    im.save(out, "JPEG", quality=94, optimize=True, subsampling=0)
    thumb = im.resize((480, 270), Image.Resampling.LANCZOS)
    thumb_path = THUMBS / f"{stem}.jpg"
    thumb.save(thumb_path, "JPEG", quality=88, optimize=True)
    shutil.copy2(thumb_path, BUNDLED_THUMBS / thumb_path.name)
    print(f"  ✓ {stem:36} {out.stat().st_size / 1024:.0f} KB")
    return out.stat().st_size


def main() -> int:
    total = 0
    for num, slug, _title, fn in WALLS:
        total += save(fn(), f"corebuilds-{num}-{slug}")
    print(f"series-6-circuit-core additions complete — 2 walls, {total / 1e6:.2f} MB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
