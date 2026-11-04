#!/usr/bin/env python3
"""
Core Builds Icon Pack - series-16-aurum, the second Core Builds Plus series.

Twelve 4K walls in gold on warm black: Art Deco geometry (a sunburst, scallop
fans, a stepped spire, chevrons, ziggurat arches, a lattice), engraving
(a guilloche rosette) and gold as a material (kintsugi seams, crumpled leaf,
molten ribbons, an eclipse corona, a fall of gold glints). Spectra is the one
series in the whole spectrum; Aurum is the one in a single metal, which is
what keeps the two Plus sets apart from each other and from the house palette.
All procedural and seeded; a re-run is byte-identical.

Like Spectra it is a Core Builds Plus extra: indexed by
Wallpapers/plus-manifest.json, bundled (thumbs) only into the Google Play
build by tools/sync_wallpaper_manifest.py. The files are public in this
repository like every other wall.

House rules (Wallpapers/README.md): dark-coverage floor, a calm lower third
for launcher cards, no grain.

Writes:
  Wallpapers/series-16-aurum/corebuilds-NNN-slug.png   3840x2160
  Wallpapers/thumbs/corebuilds-NNN-slug.jpg            480x270
  docs/aurum-wallpapers.png                            contact sheet
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_classic_wallpapers import H, THUMB_H, THUMB_W, VOID, W, dark_coverage  # noqa: E402
from build_horizons_wallpapers import _fbm2d  # noqa: E402
from build_plus_wallpapers import (  # noqa: E402
    ASPECT,
    _calm,
    _col,
    _finish,
    _grid,
    _ground,
    _tone,
    _up,
)

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-16-aurum"
THUMBS = ROOT / "Wallpapers" / "thumbs"
CONTACT_SHEET = ROOT / "docs" / "aurum-wallpapers.png"

# Warm black: the night ground leaned toward umber, so gold sits on it
# rather than on the house blue-black.
WARM = (14, 9, 4)

# Shadow, bronze, old gold, gold, highlight.
GOLD_STOPS = np.array([
    (20, 13, 6), (96, 60, 20), (178, 126, 46), (230, 186, 98), (255, 238, 190),
], dtype="float32")


def gold(t: np.ndarray) -> np.ndarray:
    """t in [0, 1] -> RGB 0..255 along the metal's ramp, shadow to highlight."""
    n = len(GOLD_STOPS) - 1
    x = np.clip(t, 0, 1) * n
    i = np.minimum(np.floor(x).astype(int), n - 1)
    f = (x - i)[..., None]
    return GOLD_STOPS[i] * (1 - f) + GOLD_STOPS[i + 1] * f


def sheen(x: np.ndarray, y: np.ndarray, angle: float = 0.6, freq: float = 3.0,
          phase: float = 0.0) -> np.ndarray:
    """A broad travelling highlight, so a gold line reads as metal catching
    light along its length rather than as a flat yellow stroke."""
    u = x * math.cos(angle) + y * math.sin(angle)
    return 0.5 + 0.5 * np.cos(u * freq * 2 * math.pi + phase)


def stroke(d: np.ndarray, w: float, glow: float = 0.22) -> np.ndarray:
    """A line of half-width ~w at distance d, with a soft bloom around it."""
    return np.exp(-(d / w) ** 2) + glow * np.exp(-(d / (w * 7)) ** 2)


def _paint(arr, intensity, t, gain=1.0):
    return arr + intensity[..., None] * gold(t) * gain


# ---------------------------------------------------------------- walls ---

def w169_sunburst() -> Image.Image:
    """A Deco sunburst rising from the horizon: alternate rays lit, a ringed
    half-sun, three stepped arcs around it."""
    x, y = _grid()
    arr = _ground(16901, WARM)
    cx, cy, R0 = ASPECT * 0.5, 0.60, 0.12
    r = np.hypot(x - cx, y - cy)
    up = y < cy
    ang = np.arctan2(cy - y, x - cx)  # 0 (right) .. pi (left) above the line
    n = 30
    a = np.clip(ang, 0, math.pi) / math.pi * n
    frac = a - np.floor(a)
    lit = (np.floor(a) % 2 == 0) & up & (r > R0 * 1.15)
    reach = np.exp(-((r - R0) / 0.62) ** 2)
    fill = np.where(lit, reach * (0.10 + 0.08 * np.cos((frac - 0.5) * math.pi)), 0.0)
    edge_d = r * (math.pi / n) * np.minimum(frac, 1 - frac)
    edges = np.where(up & (r > R0 * 1.15), stroke(edge_d, 0.0011, 0.12) * reach * 0.55, 0.0)
    arr = _paint(arr, fill, 0.55 + 0.25 * sheen(x, y, 1.2, 1.4))
    arr = _paint(arr, edges, 0.6 + 0.35 * sheen(x, y, 0.4, 2.2))
    # The half-sun: a disc of concentric rings.
    disc = (r < R0) & up
    rings = np.abs(r / R0 * 6 - np.round(r / R0 * 6)) * R0 / 6
    arr = _paint(arr, np.where(disc, 0.18 + stroke(rings, 0.0013, 0.0) * 0.7, 0.0),
                 0.5 + 0.45 * (1 - r / R0))
    for k, rr in enumerate((0.165, 0.205, 0.245)):
        arc = np.where(up, stroke(np.abs(r - rr), 0.0016, 0.18), 0.0) * (0.9 - 0.18 * k)
        arr = _paint(arr, arc, 0.7 + 0.3 * sheen(x, y, 2.0, 1.1))
    horizon = stroke(np.abs(y - cy), 0.0014, 0.1) * np.exp(-((x - cx) / 1.1) ** 4) * 0.8
    arr = _paint(arr, horizon, 0.85)
    return _finish(_calm(_tone(arr)))


def w170_scallop_fans() -> Image.Image:
    """Rows of overlapping scallop fans, each ringed like a shell, the rows
    fading into the dark before the lower third."""
    x, y = _grid()
    arr = _ground(17001, WARM)
    R = 0.115
    rloc = np.full(x.shape, np.inf, dtype="float32")
    row = np.zeros(x.shape, dtype="float32")
    for j in range(17):
        yc = -0.04 + j * R * 0.5
        off = R if j % 2 else 0.0
        dx = np.mod(x - off, 2 * R) - R
        rr = np.hypot(dx, y - yc)
        inside = rr < R
        rloc = np.where(inside, rr, rloc)
        row = np.where(inside, j, row)
    has = np.isfinite(rloc)
    rl = np.where(has, rloc, 0.0)
    bands = 5
    d = np.abs(rl / R * bands - np.round(rl / R * bands)) * R / bands
    lines = np.where(has & (rl > R * 0.12), stroke(d, 0.0012, 0.15), 0.0)
    rim = np.where(has, stroke(np.abs(rl - R), 0.0016, 0.2), 0.0)
    fade = np.clip((0.66 - y) / 0.22, 0, 1) * (0.55 + 0.45 * np.exp(-((x - ASPECT * 0.5) / 1.2) ** 2))
    t = 0.45 + 0.4 * sheen(x, y, 0.9, 1.6, row * 0.7)
    arr = _paint(arr, (lines * 0.6 + rim) * fade, t)
    arr = _paint(arr, np.where(has, 0.05 * (1 - rl / R), 0.0) * fade, 0.5)
    return _finish(_calm(_tone(arr), start=0.62))


def w171_spire() -> Image.Image:
    """A stepped Deco crown at night: arch on arch, each set with sunburst
    windows, a needle at the top."""
    x, y = _grid()
    arr = _ground(17101, WARM)
    cx = ASPECT * 0.5
    base, R = 0.66, 0.26
    for k in range(6):
        up = y < base
        r = np.hypot(x - cx, y - base)
        arc = np.where(up, stroke(np.abs(r - R), 0.0016, 0.2), 0.0)
        inner = np.where(up, stroke(np.abs(r - R * 0.9), 0.0010, 0.0), 0.0) * 0.45
        # Sunburst windows: short radial ticks inside the band.
        n = 9 + 2 * k
        ang = np.arctan2(base - y, x - cx) / math.pi * n
        tick_d = r * (math.pi / n) * np.abs(ang - np.round(ang))
        band = (r > R * 0.66) & (r < R * 0.86) & up
        ticks = np.where(band, stroke(tick_d, 0.0011, 0.0), 0.0) * 0.6
        glow = np.where(up & (r < R), np.exp(-((R - r) / (R * 0.5))), 0.0) * 0.05
        bright = 1.0 - 0.1 * k
        arr = _paint(arr, (arc + inner + ticks) * bright + glow, 0.55 + 0.4 * sheen(x, y, 1.5, 2.0, k))
        # Shoulders: the setback walls under each arch.
        for sx in (cx - R, cx + R):
            wall = stroke(np.abs(x - sx), 0.0014, 0.15) * ((y > base) & (y < base + 0.05 + 0.02 * k))
            arr = _paint(arr, wall * 0.7, 0.6)
        base -= R * 0.62
        R *= 0.74
    needle_w = 0.0012 + 0.004 * np.clip((y - 0.03) / (base - 0.03), 0, 1)
    needle = stroke(np.abs(x - cx), 1.0, 0.0) * 0  # placeholder shape below
    needle = np.exp(-((x - cx) / needle_w) ** 2) * ((y > 0.03) & (y < base + R)) * 0.9
    arr = _paint(arr, needle, 0.85)
    return _finish(_calm(_tone(arr), start=0.66))


def w172_chevrons() -> Image.Image:
    """Nested chevrons climbing to a point, alternately bold and fine."""
    x, y = _grid()
    arr = _ground(17201, WARM)
    cx, slope, sp = ASPECT * 0.5, 0.62, 0.045
    u = y + slope * np.abs(x - cx)
    m = np.round(u / sp)
    d = np.abs(u - m * sp) / math.sqrt(1 + slope * slope)
    width = np.where(m % 3 == 0, 0.0024, 0.0011)
    lines = np.exp(-(d / width) ** 2) + 0.16 * np.exp(-(d / (width * 8)) ** 2)
    region = np.clip((0.62 - u + 0.35) / 0.25, 0, 1) * np.clip((u - 0.10) / 0.10, 0, 1)
    region *= np.exp(-((x - cx) / 1.25) ** 2)
    arr = _paint(arr, lines * region * (0.85 - 0.25 * (m % 3 != 0)), 0.5 + 0.45 * sheen(x, y, 0.3, 1.8))
    spine = stroke(np.abs(x - cx), 0.0014, 0.2) * (y < 0.58) * np.clip(y / 0.1, 0, 1) * 0.6
    arr = _paint(arr, spine, 0.8)
    return _finish(_calm(_tone(arr)))


def w173_guilloche() -> Image.Image:
    """A guilloche rosette, as engraved on a banknote: dozens of rotated
    waves interlacing into two rings and a flower."""
    x, y = _grid()
    arr = _ground(17301, WARM)
    cx, cy = ASPECT * 0.64, 0.38
    r = np.hypot(x - cx, y - cy)
    th = np.arctan2(y - cy, x - cx)
    acc = np.zeros_like(r)
    for R0, A, n, K, w in ((0.30, 0.026, 13, 28, 0.0010), (0.20, 0.034, 9, 24, 0.0011),
                           (0.085, 0.040, 5, 16, 0.0010)):
        for k in range(K):
            ph = n * th + 2 * math.pi * k / K
            rk = R0 + A * np.sin(ph)
            dr = A * n * np.cos(ph)
            d = np.abs(r - rk) / np.sqrt(1 + (dr / np.maximum(r, 1e-3)) ** 2)
            acc += np.exp(-(d / w) ** 2)
    acc = 1 - np.exp(-acc * 0.9)
    halo = np.exp(-(r / 0.38) ** 2) * 0.06
    arr = _paint(arr, acc * 0.85 + halo, 0.45 + 0.5 * (0.5 + 0.5 * np.cos(th * 2 + 0.8)))
    return _finish(_calm(_tone(arr)))


def w174_kintsugi() -> Image.Image:
    """Dark glaze mended with gold: meandering seams, thick and thin, catching
    the light along their length."""
    x, y = _grid()
    q = (W // 4, H // 4)
    glaze = _up(_fbm2d(17401, 700, 4, w=q[0], h=q[1]))
    arr = _col(VOID) + glaze[..., None] * _col((16, 14, 18)) + _col((6, 5, 4))
    seams = np.zeros_like(x)
    for seed, scale, level, w in ((17402, 1500, 0.5, 2.6), (17403, 900, 0.45, 1.6), (17404, 520, 0.55, 1.0)):
        f = _up(_fbm2d(seed, scale, 4, w=q[0], h=q[1]))
        gy, gx = np.gradient(f)
        d = np.abs(f - level) / (np.hypot(gx, gy) + 1e-6)
        vary = _up(_fbm2d(seed + 50, 600, 3, w=q[0], h=q[1]))
        width = w * (0.5 + vary)
        seams += np.exp(-(d / width) ** 2)
    seams = np.clip(seams, 0, 1.4)
    arr = _paint(arr, seams * 0.95, 0.45 + 0.5 * sheen(x, y, 0.8, 2.6))
    return _finish(_calm(_tone(arr), start=0.62))


def w175_ziggurat() -> Image.Image:
    """Nested ziggurat arches: stepped outlines, one inside the next, with a
    glow rising behind the summit."""
    x, y = _grid()
    arr = _ground(17501, WARM)
    cx, base = ASPECT * 0.5, 0.64
    steps = 5
    for k, L in enumerate((1.0, 0.8, 0.62, 0.46, 0.32)):
        sdf = np.full(x.shape, np.inf, dtype="float32")
        for i in range(steps):
            hw = L * 0.78 * (1 - i / steps)
            top = base - L * 0.105 * (i + 1)
            # Box SDF for the rectangle [cx-hw, cx+hw] x [top, base].
            qx = np.abs(x - cx) - hw
            qy = np.abs(y - (top + base) / 2) - (base - top) / 2
            out = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0))
            ins = np.minimum(np.maximum(qx, qy), 0)
            sdf = np.minimum(sdf, out + ins)
        line = stroke(np.abs(sdf), 0.0015, 0.18) * (y < base)
        arr = _paint(arr, line * (1.0 - 0.12 * k), 0.5 + 0.4 * sheen(x, y, 1.1, 1.5, k))
    summit = np.exp(-(((x - cx) / 0.28) ** 2 + ((y - (base - 0.53)) / 0.16) ** 2)) * 0.22
    arr = _paint(arr, summit, 0.7)
    return _finish(_calm(_tone(arr)))


def w176_gold_leaf() -> Image.Image:
    """Crumpled gold leaf laid on the dark in torn patches, lit from the
    upper left."""
    x, y = _grid()
    q = (W // 2, H // 2)
    arr = _ground(17601, WARM)
    mask_f = _up(_fbm2d(17602, 1100, 4, w=q[0], h=q[1]))
    bias = np.clip((x / ASPECT - 0.35) * 1.6, 0, 1) * np.clip((0.62 - y) / 0.3, 0, 1)
    mask = np.clip((mask_f * 0.6 + bias * 0.55 - 0.62) / 0.03, 0, 1)
    hgt = _up(_fbm2d(17603, 70, 5, w=q[0], h=q[1]))
    gy, gx = np.gradient(hgt)
    nz = 1.0 / 120.0
    norm = np.sqrt(gx * gx + gy * gy + nz * nz)
    lx, ly, lz = -0.55, -0.6, 0.58
    light = np.clip((gx * lx + gy * ly + nz * lz) / norm, 0, 1)
    spec = np.power(light, 14)
    leaf = gold(0.25 + 0.55 * light) + spec[..., None] * _col((255, 245, 215)) * 0.5
    arr = arr * (1 - mask[..., None]) + leaf * mask[..., None] * 0.9
    rim = np.exp(-((mask - 0.5) / 0.25) ** 2) * (mask > 0.02) * 0.25
    arr = _paint(arr, rim, 0.9)
    return _finish(_calm(arr, start=0.62))


def w177_lattice() -> Image.Image:
    """A diamond lattice with a ring at every crossing, lit from its centre."""
    x, y = _grid()
    arr = _ground(17701, WARM)
    cx, cy, s = ASPECT * 0.5, 0.34, 0.085
    u, v = (x - cx) + (y - cy), (x - cx) - (y - cy)
    du = np.abs(u / s - np.round(u / s)) * s / math.sqrt(2)
    dv = np.abs(v / s - np.round(v / s)) * s / math.sqrt(2)
    major_u = (np.round(u / s) % 4 == 0)
    major_v = (np.round(v / s) % 4 == 0)
    lines = (stroke(du, np.where(major_u, 0.0019, 0.0009), 0.12)
             + stroke(dv, np.where(major_v, 0.0019, 0.0009), 0.12))
    nu, nv = np.round(u / s) * s, np.round(v / s) * s
    nx, ny = cx + (nu + nv) / 2, cy + (nu - nv) / 2
    rn = np.hypot(x - nx, y - ny)
    rings = stroke(np.abs(rn - 0.011), 0.0011, 0.0) * 0.8
    reach = np.exp(-(((x - cx) / 1.05) ** 2 + ((y - cy) / 0.42) ** 2))
    arr = _paint(arr, (lines * 0.7 + rings) * reach, 0.45 + 0.5 * reach)
    arr = _paint(arr, np.exp(-(((x - cx) / 0.5) ** 2 + ((y - cy) / 0.22) ** 2)) * 0.07, 0.6)
    return _finish(_calm(_tone(arr)))


def w178_molten() -> Image.Image:
    """Three ribbons of molten gold folding across the frame, each shaded as
    a round bar with a bright edge."""
    x, y = _grid()
    arr = _ground(17801, WARM)
    specs = [(0.20, 0.06, 1.7, 0.4, 0.030), (0.33, 0.07, 1.25, 2.1, 0.042), (0.46, 0.05, 2.1, 4.0, 0.026)]
    for k, (yb, A, om, ph, th) in enumerate(specs):
        yc = yb + A * np.sin(x * om + ph) + 0.018 * np.sin(x * om * 2.7 + ph * 1.3)
        thick = th * (0.75 + 0.25 * np.sin(x * om * 0.6 + ph))
        v = (y - yc) / thick
        inside = np.clip((1 - np.abs(v)) / 0.08, 0, 1)
        body = np.sqrt(np.clip(1 - v * v, 0, 1))
        hl = np.exp(-((v + 0.38) / 0.16) ** 2)
        fade = np.clip(np.sin(np.clip(x / ASPECT, 0, 1) * math.pi) * 1.6, 0, 1)
        t = 0.18 + 0.6 * body + 0.25 * hl
        colour = gold(t) * inside[..., None]
        arr = arr * (1 - (inside * fade * 0.92)[..., None]) + colour * (fade * 0.92)[..., None]
        glow = np.exp(-(np.clip(np.abs(v) - 1, 0, None) * thick / 0.03) ** 2) * (np.abs(v) > 1) * 0.10 * fade
        arr = _paint(arr, glow, 0.6)
    return _finish(_calm(_tone(arr)))


def w179_eclipse() -> Image.Image:
    """A total eclipse in gold: a black disc, its corona streaming out, and
    the diamond-ring bead where the sun breaks through."""
    x, y = _grid()
    arr = _ground(17901, WARM)
    cx, cy, R = ASPECT * 0.5, 0.36, 0.125
    r = np.hypot(x - cx, y - cy)
    th = np.arctan2(y - cy, x - cx)
    rnd = np.random.RandomState(17902)
    streamers = np.zeros_like(th)
    for f in (3, 5, 8, 13, 21):
        streamers += np.cos(th * f + rnd.uniform(0, 2 * math.pi)) * (1.0 / f ** 0.6)
    streamers = 0.5 + 0.5 * streamers / 2.2
    out = r > R
    corona = np.where(out, np.exp(-(r - R) / (0.045 + 0.06 * streamers)) * (0.35 + 0.65 * streamers), 0.0)
    rim = stroke(np.abs(r - R), 0.0022, 0.3) * 0.9
    arr = _paint(arr, corona * 0.9 + rim, 0.4 + 0.55 * np.exp(-(r - R) / 0.08))
    bead_a = math.radians(-38)
    bx, by = cx + R * math.cos(bead_a), cy + R * math.sin(bead_a)
    rb = np.hypot(x - bx, y - by)
    bead = np.exp(-(rb / 0.010) ** 2) * 1.4 + np.exp(-(rb / 0.05) ** 2) * 0.35
    spikes = (np.exp(-np.abs(y - by) / 0.0016) * np.exp(-np.abs(x - bx) / 0.22)
              + np.exp(-np.abs(x - bx) / 0.0016) * np.exp(-np.abs(y - by) / 0.12)) * 0.35
    arr = arr + (bead + spikes)[..., None] * _col((255, 236, 190))
    disc = np.clip((R - r) / 0.002, 0, 1)
    arr = arr * (1 - disc[..., None]) + _col(VOID) * disc[..., None]
    return _finish(_calm(_tone(arr)))


def w180_gold_rain() -> Image.Image:
    """A slow fall of gold glints: streaks of every length, thinning out
    toward the lower third, with a few out-of-focus discs."""
    rnd = np.random.RandomState(18001)
    layer = Image.new("L", (W, H), 0)
    dr = ImageDraw.Draw(layer)
    for _ in range(1100):
        sx = rnd.uniform(0, W)
        # Some streaks start just above the frame; keep the sign through the
        # power (a negative base to 1.25 is NaN, which PIL cannot draw).
        v = rnd.uniform(-0.05, 0.62)
        sy = math.copysign(abs(v) ** 1.25, v) * H
        length = rnd.uniform(0.015, 0.11) * H
        b = rnd.uniform(0.25, 1.0)
        dr.line([(float(sx), float(sy)), (float(sx + length * 0.06), float(sy + length))],
                fill=int(255 * b), width=int(rnd.choice([2, 3, 4])))
    streaks = np.asarray(layer, dtype="float32") / 255.0
    glow = np.asarray(layer.filter(ImageFilter.GaussianBlur(9)), dtype="float32") / 255.0
    bokeh = Image.new("L", (W, H), 0)
    db = ImageDraw.Draw(bokeh)
    for _ in range(26):
        bx, by = rnd.uniform(0, W), rnd.uniform(0.02, 0.5) * H
        rad = rnd.uniform(18, 70)
        db.ellipse([bx - rad, by - rad, bx + rad, by + rad], fill=int(rnd.uniform(40, 110)))
    bokeh_a = np.asarray(bokeh.filter(ImageFilter.GaussianBlur(6)), dtype="float32") / 255.0
    x, y = _grid()
    arr = _ground(18002, WARM)
    t = 0.5 + 0.45 * sheen(x, y, 1.4, 2.5)
    arr = _paint(arr, streaks * 0.85 + glow * 0.5, t)
    arr = _paint(arr, bokeh_a * 0.35, 0.7)
    return _finish(_calm(_tone(arr), start=0.6))


WALLS = [
    (169, "sunburst", "169 Sunburst", w169_sunburst),
    (170, "scallop-fans", "170 Scallop Fans", w170_scallop_fans),
    (171, "spire", "171 Spire", w171_spire),
    (172, "chevrons", "172 Chevrons", w172_chevrons),
    (173, "guilloche", "173 Guilloche", w173_guilloche),
    (174, "kintsugi", "174 Kintsugi", w174_kintsugi),
    (175, "ziggurat", "175 Ziggurat", w175_ziggurat),
    (176, "gold-leaf", "176 Gold Leaf", w176_gold_leaf),
    (177, "lattice", "177 Lattice", w177_lattice),
    (178, "molten", "178 Molten", w178_molten),
    (179, "eclipse", "179 Eclipse", w179_eclipse),
    (180, "gold-rain", "180 Gold Rain", w180_gold_rain),
]


def save(im: Image.Image, stem: str) -> int:
    SERIES.mkdir(parents=True, exist_ok=True)
    THUMBS.mkdir(parents=True, exist_ok=True)
    out = SERIES / f"{stem}.png"
    im.save(out, "PNG", optimize=True)
    im.resize((THUMB_W, THUMB_H), Image.LANCZOS).save(
        THUMBS / f"{stem}.jpg", "JPEG", quality=88, optimize=True)
    size = out.stat().st_size
    print(f"  ✓ {stem:42} {size / 1024:7.0f} KB  dark {dark_coverage(im):.0%}")
    return size


def _contact_sheet(rendered):
    cols, tw, th, cap = 3, 480, 270, 22
    rows = (len(rendered) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * tw, rows * (th + cap)), VOID)
    dr = ImageDraw.Draw(sheet)
    for i, (num, title, im) in enumerate(rendered):
        x, y = (i % cols) * tw, (i // cols) * (th + cap)
        sheet.paste(im.resize((tw, th), Image.LANCZOS), (x, y))
        dr.text((x + 6, y + th + 5), title, fill=(225, 205, 160))
    sheet.save(CONTACT_SHEET, optimize=True)


def main() -> int:
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", type=int, action="append")
    args = parser.parse_args()
    rendered, total, drawn = [], 0, 0
    for num, slug, title, fn in WALLS:
        stem = f"corebuilds-{num}-{slug}"
        if args.only and num not in args.only:
            # The contact sheet needs every wall; the skipped ones come from disk.
            src = SERIES / f"{stem}.png"
            if not src.exists():
                raise SystemExit(f"{src.relative_to(ROOT)} is missing; run without --only first")
            rendered.append((num, title, Image.open(src).convert("RGB")))
            continue
        im = fn()
        dc = dark_coverage(im)
        assert dc >= 0.45, f"{stem}: dark coverage {dc:.0%} below the 45% floor"
        total += save(im, stem)
        drawn += 1
        rendered.append((num, title, im))
    _contact_sheet(rendered)
    print(f"  contact sheet: {CONTACT_SHEET.relative_to(ROOT)}")
    print(f"\nseries-16-aurum: {drawn} of {len(WALLS)} walls drawn at 4K, "
          f"{total / 1024 / 1024:.1f} MB.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
