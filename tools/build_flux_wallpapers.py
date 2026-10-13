#!/usr/bin/env python3
"""
Core Builds Icon Pack — series-14-flux.

Eight 4K abstract walls: domain-warped noise fields, flow ribbons, marbled
ink and silk folds in the house palette, on the night ground. Flux is the
abstract series the 2.4.0 feedback asked for - "abstract" meaning shape and
flow, not the gradient washes Fieldwork was retired for. All procedural and
seeded; a re-run is byte-identical.

House rules (Wallpapers/README.md): dark-coverage floor, a calm lower third
for launcher cards, no grain.

Writes:
  Wallpapers/series-14-flux/corebuilds-NN-slug.png   3840x2160
  Wallpapers/thumbs/corebuilds-NN-slug.jpg           480x270 (+ app copy)
  docs/flux-wallpapers.png                           contact sheet
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_classic_wallpapers import (  # noqa: E402
    BUILD_BLUE,
    CYAN,
    DUSK,
    EMBER,
    GLOW_CYAN,
    H,
    NIGHT,
    VOID,
    W,
    dark_coverage,
    save_wall,
)
from build_horizons_wallpapers import _fbm2d  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-14-flux"
CONTACT_SHEET = ROOT / "docs" / "flux-wallpapers.png"

ROSE = (217, 102, 163)
AMBER = (255, 176, 32)
JADE = (46, 184, 135)


def _col(c):
    return np.array(c, dtype="float32").reshape(1, 1, 3)


def _ramp(t: np.ndarray, stops: list[tuple]) -> np.ndarray:
    """Map a [0,1] field through a colour ramp (list of RGB stops)."""
    n = len(stops) - 1
    x = np.clip(t, 0, 1) * n
    i = np.minimum(np.floor(x).astype(int), n - 1)
    f = (x - i)[..., None]
    a = np.array(stops, dtype="float32")
    return a[i] * (1 - f) + a[i + 1] * f


def _warp(seed: int, scale: float, strength: float, octaves: int = 5) -> np.ndarray:
    """Domain-warped fBm: noise sampled through two more noise fields."""
    base = _fbm2d(seed, scale, octaves, w=W // 2, h=H // 2)
    wx = _fbm2d(seed + 1, scale * 1.3, 4, w=W // 2, h=H // 2)
    wy = _fbm2d(seed + 2, scale * 1.3, 4, w=W // 2, h=H // 2)
    h, w = base.shape
    yy, xx = np.mgrid[0:h, 0:w].astype("float32")
    sx = np.clip(xx + (wx - 0.5) * strength * w, 0, w - 1).astype(int)
    sy = np.clip(yy + (wy - 0.5) * strength * h, 0, h - 1).astype(int)
    out = base[sy, sx]
    return np.asarray(Image.fromarray(out, mode="F").resize((W, H), Image.BICUBIC),
                      dtype="float32")


def _calm(arr: np.ndarray, start: float = 0.62, depth: float = 0.85) -> np.ndarray:
    y0 = int(H * start)
    t = np.linspace(0, depth, H - y0, dtype="float32")[:, None, None]
    arr[y0:] = arr[y0:] * (1 - t) + _col(VOID) * t
    return arr


def _finish(arr: np.ndarray) -> Image.Image:
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))


# ---------------------------------------------------------------- walls ---

def w125_ink_tide() -> Image.Image:
    """Cyan ink folding into violet, the marbling of a domain warp."""
    f = _warp(12501, 1100, 0.35)
    g = _warp(12504, 500, 0.5, 4)
    t = np.clip(f * 0.75 + g * 0.35 - 0.15, 0, 1)
    arr = _ramp(t, [VOID, NIGHT, (16, 44, 70), DUSK, CYAN, GLOW_CYAN])
    edge = np.abs(np.gradient(t)[0]) + np.abs(np.gradient(t)[1])
    arr = arr + np.clip(edge * 30, 0, 1)[..., None] * _col(GLOW_CYAN) * 0.5
    return _finish(_calm(arr * 0.85))


def w126_silk() -> Image.Image:
    """Folded silk: a stretched warp lit along its ridges."""
    f = _warp(12601, 1600, 0.6, 4)
    s = np.asarray(Image.fromarray(f, mode="F").resize((W // 6, H), Image.BILINEAR)
                   .resize((W, H), Image.BICUBIC), dtype="float32")
    ridges = 1.0 - np.abs(np.sin(s * math.pi * 7.0))
    ridges = np.power(ridges, 6.0)
    base = _ramp(s, [VOID, (24, 18, 48), DUSK, (60, 30, 90), NIGHT])
    arr = base * 0.75 + ridges[..., None] * _col(ROSE) * 0.9
    arr = arr + np.power(ridges, 3)[..., None] * _col((255, 220, 240)) * 0.35
    return _finish(_calm(arr))


def w127_plasma_core() -> Image.Image:
    """A central ember core breaking into warped filaments."""
    f = _warp(12701, 700, 0.55, 6)
    yy, xx = np.mgrid[0:H, 0:W].astype("float32")
    d = np.sqrt(((xx - W * 0.5) / (W * 0.32)) ** 2 + ((yy - H * 0.42) / (H * 0.4)) ** 2)
    core = np.exp(-d * d * 1.8)
    t = np.clip(f * core * 1.9 - 0.1, 0, 1)
    arr = _ramp(t, [VOID, (40, 12, 10), EMBER, (255, 150, 90), (255, 230, 200)])
    arr = arr + np.exp(-d * d * 6)[..., None] * _col(AMBER) * 0.3
    return _finish(_calm(arr * 0.9))


def w128_flow_lines() -> Image.Image:
    """Streamlines through a noise field: thousands of short strokes."""
    f = _fbm2d(12801, 900, 5, w=W // 4, h=H // 4)
    ang = f * math.pi * 4.0
    im = Image.new("RGB", (W, H), VOID)
    dr = ImageDraw.Draw(im, "RGBA")
    rnd = np.random.RandomState(12802)
    h4, w4 = ang.shape
    for i in range(5200):
        x, y = rnd.uniform(0, W), rnd.uniform(0, H * 0.78)
        shade = rnd.uniform(0.25, 1.0)
        col = CYAN if i % 5 else (BUILD_BLUE if i % 2 else GLOW_CYAN)
        pts = []
        for _ in range(26):
            pts.append((x, y))
            a = ang[min(h4 - 1, int(y / 4)), min(w4 - 1, int(x / 4))]
            x += math.cos(a) * 9
            y += math.sin(a) * 9
            if not (0 <= x < W and 0 <= y < H):
                break
        if len(pts) > 2:
            dr.line(pts, fill=(*col, int(26 + 90 * shade)), width=2)
    arr = np.asarray(im).astype("float32")
    glow = _fbm2d(12803, 1300, 3, w=W // 4, h=H // 4)
    glow = np.asarray(Image.fromarray(glow, mode="F").resize((W, H)), dtype="float32")
    arr = arr + glow[..., None] * _col((10, 30, 50)) * 0.8
    return _finish(_calm(arr))


def w129_glass_shards() -> Image.Image:
    """Voronoi facets with lit edges, the circuit series' cousin in glass."""
    rnd = np.random.RandomState(12901)
    n = 70
    px = rnd.uniform(0, W, n)
    py = rnd.uniform(-H * 0.2, H * 0.9, n)
    yy, xx = np.mgrid[0:H:4, 0:W:4].astype("float32")
    d = np.stack([np.sqrt((xx - px[i]) ** 2 + (yy - py[i]) ** 2) for i in range(n)])
    order = np.argsort(d, axis=0)
    d1 = np.take_along_axis(d, order[:1], 0)[0]
    d2 = np.take_along_axis(d, order[1:2], 0)[0]
    cell = order[0]
    edge = np.exp(-np.clip(d2 - d1, 0, None) / 10.0)
    shade = (rnd.uniform(0.15, 0.6, n))[cell]
    tint = np.array([CYAN, BUILD_BLUE, DUSK, NIGHT, NIGHT], dtype="float32")[rnd.randint(0, 5, n)][cell]
    arr_s = tint * shade[..., None] * 0.6 + edge[..., None] * _col(GLOW_CYAN)[0] * 0.9
    arr = np.asarray(Image.fromarray(np.clip(arr_s, 0, 255).astype("uint8")).resize((W, H), Image.BICUBIC),
                     dtype="float32")
    return _finish(_calm(arr))


def w130_tidepool() -> Image.Image:
    """Jade and cyan caustics: two warps multiplied."""
    a = _warp(13001, 600, 0.4, 5)
    b = _warp(13004, 420, 0.4, 5)
    c = 1.0 - np.abs(a - b) * 3.0
    c = np.power(np.clip(c, 0, 1), 5.0)
    arr = _ramp(a, [VOID, (6, 28, 32), (10, 60, 60), NIGHT]) * 0.9
    arr = arr + c[..., None] * _col(JADE) * 0.8 + np.power(c, 3)[..., None] * _col(GLOW_CYAN) * 0.5
    return _finish(_calm(arr))


def w131_dusk_veil() -> Image.Image:
    """A violet veil over ember embers: soft warp, hard calm."""
    f = _warp(13101, 1400, 0.7, 4)
    g = _fbm2d(13105, 180, 4, w=W // 2, h=H // 2)
    g = np.asarray(Image.fromarray(g, mode="F").resize((W, H)), dtype="float32")
    arr = _ramp(np.power(np.clip(f, 0, 1), 1.8), [VOID, VOID, (28, 14, 44), DUSK, (120, 60, 140), (200, 120, 200)]) * 0.8
    sparks = np.power(np.clip(g - 0.62, 0, 1) * 2.6, 2.0) * (1 - f)
    arr = arr + sparks[..., None] * _col(EMBER) * 1.2
    return _finish(_calm(arr))


def w132_signal_grid() -> Image.Image:
    """A perspective grid bent by the field: order under flow."""
    f = _warp(13201, 1000, 0.5, 4)
    yy, xx = np.mgrid[0:H, 0:W].astype("float32")
    u = xx / W + (f - 0.5) * 0.12
    v = yy / H + (f - 0.5) * 0.12
    lines = np.minimum(np.abs(np.sin(u * math.pi * 28)), np.abs(np.sin(v * math.pi * 16)))
    grid = np.power(1.0 - lines, 24.0)
    fade = np.clip(1.4 - np.abs(v - 0.45) * 3.0, 0, 1)
    arr = _col(VOID) + f[..., None] * _col((8, 24, 40)) * 1.4
    arr = arr + (grid * fade)[..., None] * _col(CYAN) * 0.85
    arr = arr + np.power(grid * fade, 3)[..., None] * _col(GLOW_CYAN) * 0.4
    return _finish(_calm(arr))


WALLS = [
    (125, "ink-tide", "125 Ink Tide", w125_ink_tide),
    (126, "silk", "126 Silk", w126_silk),
    (127, "plasma-core", "127 Plasma Core", w127_plasma_core),
    (128, "flow-lines", "128 Flow Lines", w128_flow_lines),
    (129, "glass-shards", "129 Glass Shards", w129_glass_shards),
    (130, "tidepool", "130 Tidepool", w130_tidepool),
    (131, "dusk-veil", "131 Dusk Veil", w131_dusk_veil),
    (132, "signal-grid", "132 Signal Grid", w132_signal_grid),
]


def _contact_sheet(rendered):
    cols, tw, th, cap = 2, 430, 242, 20
    rows = (len(rendered) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * tw, rows * (th + cap)), VOID)
    dr = ImageDraw.Draw(sheet)
    for i, (num, title, im) in enumerate(rendered):
        x, y = (i % cols) * tw, (i // cols) * (th + cap)
        sheet.paste(im.resize((tw, th), Image.LANCZOS), (x, y))
        dr.text((x + 6, y + th + 4), title, fill=(200, 210, 225))
    sheet.save(CONTACT_SHEET, optimize=True)


def main() -> int:
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", type=int, action="append")
    args = parser.parse_args()
    SERIES.mkdir(parents=True, exist_ok=True)
    rendered, total = [], 0
    for num, slug, title, fn in WALLS:
        stem = f"corebuilds-{num}-{slug}"
        if args.only and num not in args.only:
            rendered.append((num, title, Image.open(SERIES / f"{stem}.png").convert("RGB")))
            continue
        im = fn()
        dc = dark_coverage(im)
        assert dc >= 0.45, f"{stem}: dark coverage {dc:.0%} below the 45% floor"
        total += save_wall(im, SERIES, stem)
        rendered.append((num, title, im))
    _contact_sheet(rendered)
    print(f"  contact sheet: {CONTACT_SHEET.relative_to(ROOT)}")
    print(f"\nseries-14-flux complete — {len(WALLS)} walls at 4K, {total / 1024 / 1024:.1f} MB.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
