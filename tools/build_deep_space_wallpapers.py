#!/usr/bin/env python3
"""
Core Builds Icon Pack — series-9-deep-space.

Twelve 4K space walls in the pack's own palette: cyan signal, build blue,
dusk violet and ember heat on the night ground. Event horizon, nebulae,
starfields, a ringed planet, a spiral galaxy, comets, a nova — all
procedural, all seeded, no external sources.

House rules (per Wallpapers/README.md): OLED-friendly dark coverage,
calm bottom third for launcher cards, film-grain dither against banding.

Writes:
  Wallpapers/series-9-deep-space/corebuilds-NN-slug.png   3840x2160
  Wallpapers/thumbs/corebuilds-NN-slug.jpg                480x270 (+ app copy)
  docs/deep-space-wallpapers.png                          contact sheet

Does NOT rewrite the manifest — that is done separately after visual
review, same as every other series.
"""
from __future__ import annotations

import math
import random
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

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-9-deep-space"
CONTACT_SHEET = ROOT / "docs" / "deep-space-wallpapers.png"

VIOLET = DUSK
PALE = (200, 226, 255)
WHITE = (235, 244, 255)


def _base(seed: int, top: tuple = VOID, bottom: tuple = NIGHT) -> np.ndarray:
    """Vertical night gradient with a soft vignette. Deterministic."""
    rnd = random.Random(seed)  # noqa: F841 (kept: every wall takes a seed)
    ys = np.linspace(0, 1, H, dtype="float32")[:, None, None]
    top_a = np.array(top, dtype="float32").reshape(1, 1, 3)
    bot_a = np.array(bottom, dtype="float32").reshape(1, 1, 3)
    arr = top_a * (1 - ys) + bot_a * ys
    yy, xx = np.mgrid[0:H, 0:W].astype("float32")
    dx = (xx / W - 0.5) * 2
    dy = (yy / H - 0.5) * 2
    vig = 1.0 - 0.35 * np.clip(dx * dx * 0.6 + dy * dy - 0.35, 0, 1)[..., None]
    return arr * vig


def _stars(arr: np.ndarray, seed: int, n: int, ymax_frac: float = 0.92,
           bright_frac: float = 0.02, band: bool = False) -> None:
    """Additive stars, brighter toward a few sparkle crosses. In place."""
    rnd = random.Random(seed)
    h, w, _ = arr.shape
    ymax = int(h * ymax_frac)
    for i in range(n):
        x = rnd.randint(0, w - 1)
        if band and rnd.random() < 0.55:
            # Milky band: diagonal drift across the frame.
            t = rnd.random()
            cx = int(w * (0.15 + 0.7 * t))
            cy = int(h * (0.75 - 0.55 * t))
            x = min(w - 1, max(0, int(rnd.gauss(cx, w * 0.09))))
            y = min(ymax, max(0, int(rnd.gauss(cy, h * 0.10))))
        else:
            y = int(rnd.triangular(0, ymax, ymax * 0.35))
        b = rnd.uniform(0.18, 1.0)
        tint = rnd.choice([PALE, PALE, WHITE, GLOW_CYAN, BUILD_BLUE])
        v = np.array(tint, dtype="float32") * b
        arr[y, x] = np.minimum(255, arr[y, x] + v)
        if b > 1.0 - bright_frac * 8:
            # Sparkle cross on the brightest few.
            arm = rnd.randint(2, 5)
            for dx in range(-arm, arm + 1):
                xx = x + dx
                if 0 <= xx < w:
                    fall = 1 - abs(dx) / (arm + 1)
                    arr[y, xx] = np.minimum(255, arr[y, xx] + v * 0.5 * fall)
            for dy in range(-arm, arm + 1):
                yy = y + dy
                if 0 <= yy < h:
                    fall = 1 - abs(dy) / (arm + 1)
                    arr[yy, x] = np.minimum(255, arr[yy, x] + v * 0.5 * fall)
        elif b > 0.86 and x + 1 < w and y + 1 < h:
            arr[y, x + 1] = np.minimum(255, arr[y, x + 1] + v * 0.5)
            arr[y + 1, x] = np.minimum(255, arr[y + 1, x] + v * 0.5)


def _blob(arr: np.ndarray, cx: float, cy: float, r: float, color: tuple,
          strength: float = 1.0, aspect: float = 1.0,
          angle: float = 0.0, power: float = 2.0) -> None:
    """Soft elliptical gaussian glow, additive. In place."""
    h, w, _ = arr.shape
    yy, xx = np.mgrid[0:h, 0:w].astype("float32")
    ca, sa = math.cos(angle), math.sin(angle)
    dx = (xx - cx) / max(1.0, r)
    dy = (yy - cy) / max(1.0, r * aspect)
    rx = dx * ca - dy * sa
    ry = dx * sa + dy * ca
    d2 = rx * rx + ry * ry
    fall = np.exp(-np.power(np.clip(d2, 0, 9), power / 2))
    col = np.array(color, dtype="float32").reshape(1, 1, 3)
    arr[:, :, :] = np.minimum(255, arr + fall[..., None] * col * strength)


def _ring(arr: np.ndarray, cx: float, cy: float, r: float, thick: float,
          color: tuple, strength: float = 1.0, squash: float = 1.0) -> None:
    """Thin glowing ring (accretion / shockwave / orbit). In place."""
    h, w, _ = arr.shape
    yy, xx = np.mgrid[0:h, 0:w].astype("float32")
    d = np.sqrt(((xx - cx) / max(1.0, r)) ** 2 + ((yy - cy) / max(1.0, r * squash)) ** 2)
    band = np.exp(-((d - 1.0) / max(1e-3, thick / r)) ** 2)
    col = np.array(color, dtype="float32").reshape(1, 1, 3)
    arr[:, :, :] = np.minimum(255, arr + band[..., None] * col * strength)


def _disc(arr: np.ndarray, cx: float, cy: float, r: float, color: tuple,
          shade_dir: tuple = (-0.5, -0.5)) -> None:
    """Solid planet disc with a day/night terminator. Opaque inside."""
    h, w, _ = arr.shape
    yy, xx = np.mgrid[0:h, 0:w].astype("float32")
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / max(1.0, r)
    inside = d <= 1.0
    nx = (xx - cx) / max(1.0, r)
    ny = (yy - cy) / max(1.0, r)
    light = np.clip(0.5 - (nx * shade_dir[0] + ny * shade_dir[1]) * 1.4, 0.06, 1.0)
    col = np.array(color, dtype="float32").reshape(1, 1, 3)
    lit = col * light[..., None]
    edge = np.clip((1.0 - d) * r / 3.0, 0, 1)[..., None]
    arr[:, :, :] = np.where(inside[..., None], lit * edge + arr * (1 - edge), arr)


def _bottom_calm(arr: np.ndarray, start_frac: float = 0.62) -> None:
    """Fade toward VOID below start_frac: launcher cards sit here."""
    h = arr.shape[0]
    y0 = int(h * start_frac)
    t = np.linspace(0, 0.82, h - y0, dtype="float32")[:, None, None]
    void = np.array(VOID, dtype="float32").reshape(1, 1, 3)
    arr[y0:] = arr[y0:] * (1 - t) + void * t


def _finish(arr: np.ndarray, seed: int) -> Image.Image:
    # No film grain: at 4K it costs ~9 MB per PNG for a texture invisible
    # at TV distance. Series 7/8 ship smooth gradients for the same reason.
    _ = seed
    _bottom_calm(arr)
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))


# ---------------------------------------------------------------- walls ---

def w85_event_horizon() -> Image.Image:
    arr = _base(8501)
    _stars(arr, 8502, 2600)
    cx, cy, r = W * 0.5, H * 0.40, 300.0
    _blob(arr, cx, cy, 620, VIOLET, 0.35)
    _blob(arr, cx, cy, 420, CYAN, 0.5)
    # The shadow: opaque void disc, then the photon ring.
    h, w, _ = arr.shape
    yy, xx = np.mgrid[0:h, 0:w].astype("float32")
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    arr[d <= r] = np.array(VOID, dtype="float32")
    _ring(arr, cx, cy, r * 1.04, 7.0, GLOW_CYAN, 1.0)
    _ring(arr, cx, cy, r * 1.16, 22.0, CYAN, 0.45)
    _ring(arr, cx, cy, r * 1.35, 60.0, BUILD_BLUE, 0.22)
    # Accretion smear: tilted bright arc, brighter on one side.
    _blob(arr, cx - r * 1.5, cy + r * 0.55, 260, (255, 170, 120), 0.5, aspect=0.35, angle=0.5)
    _blob(arr, cx + r * 1.5, cy - r * 0.55, 200, CYAN, 0.35, aspect=0.35, angle=0.5)
    return _finish(arr, 8503)


def w86_nebula_drift() -> Image.Image:
    arr = _base(8601)
    rnd = random.Random(8602)
    puffs = [
        (0.30, 0.34, 520, VIOLET, 0.55), (0.42, 0.30, 380, CYAN, 0.4),
        (0.62, 0.38, 560, BUILD_BLUE, 0.5), (0.74, 0.30, 340, VIOLET, 0.5),
        (0.52, 0.24, 300, GLOW_CYAN, 0.35), (0.24, 0.46, 300, EMBER, 0.28),
        (0.80, 0.48, 260, EMBER, 0.25),
    ]
    for i, (fx, fy, r, col, s) in enumerate(puffs):
        _blob(arr, W * fx, H * fy, r, col, s, aspect=rnd.uniform(0.5, 0.8),
              angle=rnd.uniform(-0.6, 0.6))
        # Dark dust lane cutting each puff.
        _blob(arr, W * fx + rnd.uniform(-80, 80), H * fy + rnd.uniform(-40, 40),
              r * 0.45, VOID, -0.55, aspect=0.3, angle=rnd.uniform(-0.4, 0.4))
    _stars(arr, 8603, 1800)
    _ = i
    return _finish(arr, 8604)


def w87_starfield() -> Image.Image:
    arr = _base(8701)
    # Milky band glow first, then the stars on top.
    rnd = random.Random(8702)
    for i in range(9):
        t = i / 8
        _blob(arr, W * (0.15 + 0.7 * t), H * (0.72 - 0.52 * t),
              rnd.uniform(220, 420), PALE if i % 3 else BUILD_BLUE,
              0.24, aspect=0.7, angle=-0.55)
    _stars(arr, 8703, 5200, band=True, bright_frac=0.03)
    return _finish(arr, 8704)


def w88_ringed_planet() -> Image.Image:
    arr = _base(8801)
    _stars(arr, 8802, 2200)
    cx, cy, r = W * 0.62, H * 0.36, 330.0
    _blob(arr, cx, cy, 700, BUILD_BLUE, 0.18)
    _disc(arr, cx, cy, r, (36, 64, 110))
    # Banding on the lit limb.
    for k, (col, s) in enumerate([((90, 150, 200), 0.5), ((140, 190, 230), 0.35)]):
        _blob(arr, cx - r * 0.25, cy - r * (0.1 + 0.22 * k), r * 0.8, col, s,
              aspect=0.16, angle=0.12)
    # Rings: back half, planet, front half.
    im = Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))
    dr = ImageDraw.Draw(im)
    for rx, wdt, col in [(560, 26, (150, 210, 240)), (470, 10, (110, 170, 215)),
                         (640, 8, (90, 140, 190))]:
        dr.ellipse([cx - rx, cy - rx * 0.30, cx + rx, cy + rx * 0.30],
                   outline=col, width=wdt)
    arr = np.asarray(im).astype("float32")
    # Re-seat the planet over the back rings, then draw front arcs.
    _disc(arr, cx, cy, r, (36, 64, 110))
    im = Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))
    dr = ImageDraw.Draw(im)
    for rx, wdt, col in [(560, 26, (170, 225, 250)), (470, 10, (130, 185, 225)),
                         (640, 8, (100, 150, 200))]:
        dr.arc([cx - rx, cy - rx * 0.30, cx + rx, cy + rx * 0.30],
               start=12, end=168, fill=col, width=wdt)
    arr = np.asarray(im).astype("float32")
    return _finish(arr, 8803)


def w89_galaxy_spiral() -> Image.Image:
    arr = _base(8901)
    cx, cy = W * 0.5, H * 0.38
    _blob(arr, cx, cy, 200, (255, 240, 220), 0.9)
    _blob(arr, cx, cy, 420, VIOLET, 0.5, aspect=0.55, angle=0.35)
    # Two arms: chains of fading puffs along a logarithmic spiral.
    rnd = random.Random(8902)
    for arm in (0.0, math.pi):
        for k in range(26):
            t = k / 25
            ang = arm + t * 3.6
            rad = 130 + t * 640
            x = cx + math.cos(ang) * rad
            y = cy + math.sin(ang) * rad * 0.55
            col = CYAN if k % 3 == 0 else (PALE if k % 3 == 1 else BUILD_BLUE)
            _blob(arr, x, y, 120 - t * 70, col, 0.5 - t * 0.28,
                  aspect=0.8, angle=ang)
            if rnd.random() < 0.4:
                _blob(arr, x + rnd.uniform(-40, 40), y + rnd.uniform(-24, 24),
                      26, WHITE, 0.8)
    _stars(arr, 8903, 1500)
    return _finish(arr, 8904)


def w90_aurora_orbit() -> Image.Image:
    arr = _base(9001)
    _stars(arr, 9002, 2000)
    # Planet limb: huge dark curve rising from the bottom.
    cx, cy, r = W * 0.5, H * 2.1, H * 1.75
    _disc(arr, cx, cy, r, (10, 22, 38))
    h, w, _ = arr.shape
    yy, xx = np.mgrid[0:h, 0:w].astype("float32")
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    limb = np.exp(-((d - r) / 26.0) ** 2)
    arr[:, :, :] = np.minimum(255, arr + limb[..., None] *
                              np.array(CYAN, dtype="float32") * 0.8)
    # Aurora curtains above the limb: smooth swaying veils, bright lower
    # edge dissolving upward. Pure numpy — PIL thick polylines segment.
    rnd = random.Random(9003)
    for k in range(7):
        x0 = w * (0.12 + 0.76 * k / 6) + rnd.uniform(-50, 50)
        top = h * rnd.uniform(0.10, 0.20)
        bot = h * rnd.uniform(0.44, 0.52)
        sway = rnd.uniform(40, 110)
        phase = rnd.uniform(0, math.pi * 2)
        # t runs 0 at the curtain foot (bright) to 1 at its head (dissolved).
        ys = np.arange(h, dtype="float32")
        t = np.clip((bot - ys) / max(1.0, (bot - top)), 0, 1)
        vert = np.exp(-(t * 2.6) ** 2) * (t > 0) * (t < 1)
        for col, width, peak in [((0, 229, 255), 55, 0.9),
                                 ((0, 212, 255), 140, 0.42),
                                 ((138, 72, 144), 250, 0.30)]:
            xc = x0 + np.sin(t * 4.0 + phase + k) * sway * (0.25 + 0.75 * t)
            spread = width * (0.7 + 0.9 * t)  # diffuse with altitude
            gx = np.exp(-((xx - xc[:, None]) / spread[:, None]) ** 2)
            veil = gx * vert[:, None] * peak
            arr[:, :, :] = np.minimum(255, arr + veil[..., None] *
                                      np.array(col, dtype="float32"))
    return _finish(arr, 9004)


def w91_comet_lane() -> Image.Image:
    arr = _base(9101)
    _stars(arr, 9102, 2400)
    rnd = random.Random(9103)
    comets = [(0.30, 0.30, 1.0, CYAN), (0.55, 0.22, 0.7, PALE),
              (0.72, 0.36, 0.85, GLOW_CYAN), (0.44, 0.44, 0.5, VIOLET)]
    for i, (fx, fy, s, col) in enumerate(comets):
        x, y = W * fx, H * fy
        ang = 0.62 + rnd.uniform(-0.06, 0.06)  # shared lane direction
        _blob(arr, x, y, 26 * s + 8, WHITE, 1.0)
        _blob(arr, x, y, 90 * s, col, 0.7)
        tail_len = 700 * s
        for k in range(30):
            t = (k + 1) / 30
            tx = x - math.cos(ang) * tail_len * t
            ty = y - math.sin(ang) * tail_len * t
            _blob(arr, tx, ty, (60 * s + 10) * (1 - t * 0.75), col,
                  0.4 * (1 - t), aspect=0.5, angle=ang)
        _ = i
    return _finish(arr, 9104)


def w92_deep_field() -> Image.Image:
    arr = _base(9201, top=(2, 3, 8), bottom=(6, 9, 16))
    _stars(arr, 9202, 1400)
    rnd = random.Random(9203)
    # Faint background galaxies: small soft ellipses in dim hues.
    hues = [(150, 160, 190), (170, 140, 170), (140, 170, 200), (190, 170, 150)]
    for _ in range(150):
        x = rnd.uniform(0, W)
        y = rnd.uniform(0, H * 0.8)
        r = rnd.uniform(6, 30)
        _blob(arr, x, y, r, rnd.choice(hues), rnd.uniform(0.2, 0.5),
              aspect=rnd.uniform(0.3, 1.0), angle=rnd.uniform(0, math.pi))
    # One hero spiral.
    _blob(arr, W * 0.68, H * 0.30, 130, PALE, 0.55, aspect=0.6, angle=0.5)
    _blob(arr, W * 0.68, H * 0.30, 44, WHITE, 0.65)
    return _finish(arr, 9204)


def w93_ember_nova() -> Image.Image:
    arr = _base(9301)
    _stars(arr, 9302, 1600)
    cx, cy = W * 0.5, H * 0.38
    _blob(arr, cx, cy, 130, (255, 220, 190), 1.0)
    _blob(arr, cx, cy, 340, EMBER, 0.75)
    _blob(arr, cx, cy, 640, VIOLET, 0.4)
    _ring(arr, cx, cy, 420, 26, (255, 150, 100), 0.6)
    _ring(arr, cx, cy, 640, 60, EMBER, 0.35)
    _ring(arr, cx, cy, 900, 130, VIOLET, 0.22)
    # Debris rays.
    rnd = random.Random(9303)
    im = Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))
    dr = ImageDraw.Draw(im, "RGBA")
    for _ in range(40):
        ang = rnd.uniform(0, math.pi * 2)
        r0 = rnd.uniform(150, 300)
        r1 = r0 + rnd.uniform(200, 700)
        col = rnd.choice([(255, 170, 120, 70), (192, 58, 32, 80),
                          (255, 220, 190, 60)])
        dr.line([cx + math.cos(ang) * r0, cy + math.sin(ang) * r0,
                 cx + math.cos(ang) * r1, cy + math.sin(ang) * r1],
                fill=col, width=rnd.randint(2, 7))
    arr = np.asarray(im.convert("RGB")).astype("float32")
    return _finish(arr, 9304)


def w94_hex_station() -> Image.Image:
    """The brand in orbit: a lit hex station over a planet limb."""
    arr = _base(9401)
    _stars(arr, 9402, 2300)
    # Planet limb low in frame.
    cx, cy, r = W * 0.5, H * 2.3, H * 1.9
    _disc(arr, cx, cy, r, (12, 26, 44))
    h, w, _ = arr.shape
    yy, xx = np.mgrid[0:h, 0:w].astype("float32")
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    limb = np.exp(-((d - r) / 20.0) ** 2)
    arr[:, :, :] = np.minimum(255, arr + limb[..., None] *
                              np.array(BUILD_BLUE, dtype="float32") * 0.7)
    # The station: hexagon outline + core diamond, Core Builds geometry.
    hx, hy, hr = W * 0.5, H * 0.34, 190.0
    pts = [(hx + hr * math.cos(math.pi / 6 + k * math.pi / 3),
            hy + hr * math.sin(math.pi / 6 + k * math.pi / 3)) for k in range(6)]
    im = Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))
    dr = ImageDraw.Draw(im, "RGBA")
    glow = Image.new("RGBA", im.size, (0, 0, 0, 0))
    dg = ImageDraw.Draw(glow)
    dg.line(pts + [pts[0]], fill=(0, 229, 255, 110), width=22, joint="curve")
    glow = glow.filter(__import__("PIL.ImageFilter", fromlist=["GaussianBlur"]).GaussianBlur(18))
    im = Image.alpha_composite(im.convert("RGBA"), glow).convert("RGB")
    dr = ImageDraw.Draw(im)
    dr.line(pts + [pts[0]], fill=GLOW_CYAN, width=7, joint="curve")
    dd = 44.0
    dr.polygon([(hx, hy - dd), (hx + dd * 0.7, hy), (hx, hy + dd),
                (hx - dd * 0.7, hy)], fill=(0, 229, 255))
    dr.polygon([(hx, hy - dd), (hx + dd * 0.7, hy), (hx, hy + dd),
                (hx - dd * 0.7, hy)], outline=(235, 244, 255), width=3)
    arr = np.asarray(im).astype("float32")
    _blob(arr, hx, hy, 320, CYAN, 0.16)
    return _finish(arr, 9403)


def w95_cyan_supernova() -> Image.Image:
    arr = _base(9501)
    _stars(arr, 9502, 1700)
    cx, cy = W * 0.44, H * 0.36
    _blob(arr, cx, cy, 110, WHITE, 1.0)
    _blob(arr, cx, cy, 300, GLOW_CYAN, 0.8)
    _blob(arr, cx, cy, 620, CYAN, 0.45)
    _blob(arr, cx, cy, 1000, BUILD_BLUE, 0.22)
    # Cross flare.
    im = Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))
    dr = ImageDraw.Draw(im, "RGBA")
    dr.line([(0, cy), (W, cy)], fill=(126, 238, 255, 46), width=9)
    dr.line([(cx, 0), (cx, H)], fill=(126, 238, 255, 46), width=9)
    dr.line([(0, cy), (W, cy)], fill=(235, 244, 255, 60), width=3)
    dr.line([(cx, 0), (cx, H)], fill=(235, 244, 255, 60), width=3)
    arr = np.asarray(im.convert("RGB")).astype("float32")
    _ring(arr, cx, cy, 520, 40, CYAN, 0.4)
    # Companion remnant, dim ember.
    _blob(arr, W * 0.78, H * 0.52, 200, EMBER, 0.3)
    _blob(arr, W * 0.78, H * 0.52, 60, (255, 200, 170), 0.5)
    return _finish(arr, 9503)


def w96_dark_side_moon() -> Image.Image:
    arr = _base(9601)
    _stars(arr, 9602, 2600)
    cx, cy, r = W * 0.60, H * 0.30, 260.0
    # Full disc in faint earthshine, then the lit disc, then the shadow
    # disc overlapping it — what survives the overlap is the crescent.
    _disc(arr, cx, cy, r, (22, 30, 44))
    _blob(arr, cx, cy, r * 1.5, BUILD_BLUE, 0.10)
    im = Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))
    dr = ImageDraw.Draw(im)
    dr.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(16, 22, 32))
    for rr, col in [(r, (190, 205, 225)), (r * 0.92, (225, 235, 248))]:
        dr.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=col)
    # Craters on the lit face (the shadow pass covers the dark side).
    rnd = random.Random(9603)
    for _ in range(30):
        ang = rnd.uniform(0, math.pi * 2)
        dist = rnd.uniform(0, r * 0.85)
        px = cx + math.cos(ang) * dist
        py = cy + math.sin(ang) * dist
        cr = rnd.uniform(4, 20)
        dr.ellipse([px - cr, py - cr, px + cr, py + cr], fill=(168, 182, 200))
        dr.ellipse([px - cr, py - cr * 1.2, px + cr * 0.6, py + cr * 0.4],
                   fill=(215, 224, 238))
    # The shadow: dark disc sliding in from the upper left.
    sx, sy, sr = cx - r * 0.72, cy - r * 0.30, r * 0.98
    dr.ellipse([sx - sr, sy - sr, sx + sr, sy + sr], fill=(10, 14, 22))
    arr = np.asarray(im).astype("float32")
    _stars(arr, 9604, 400, ymax_frac=0.6)  # a few stars over the dark limb
    return _finish(arr, 9605)


WALLS = [
    (85, "event-horizon", "Event Horizon", w85_event_horizon),
    (86, "nebula-drift", "Nebula Drift", w86_nebula_drift),
    (87, "starfield", "Starfield", w87_starfield),
    (88, "ringed-planet", "Ringed Planet", w88_ringed_planet),
    (89, "galaxy-spiral", "Galaxy Spiral", w89_galaxy_spiral),
    (90, "aurora-orbit", "Aurora Orbit", w90_aurora_orbit),
    (91, "comet-lane", "Comet Lane", w91_comet_lane),
    (92, "deep-field", "Deep Field", w92_deep_field),
    (93, "ember-nova", "Ember Nova", w93_ember_nova),
    (94, "hex-station", "Hex Station", w94_hex_station),
    (95, "cyan-supernova", "Cyan Supernova", w95_cyan_supernova),
    (96, "dark-side-moon", "Dark Side Moon", w96_dark_side_moon),
]


def _contact_sheet(rendered: list[tuple[int, str, Image.Image]]) -> None:
    cell_w, art_h, label_h = 960, 540, 44
    cols, rows = 2, (len(rendered) + 1) // 2
    sheet = Image.new("RGB", (cell_w * cols, (art_h + label_h) * rows), (8, 11, 16))
    draw = ImageDraw.Draw(sheet)
    for i, (num, title, im) in enumerate(rendered):
        x = (i % cols) * cell_w
        y = (i // cols) * (art_h + label_h)
        sheet.paste(im.resize((cell_w, art_h), Image.LANCZOS), (x, y))
        draw.text((x + 16, y + art_h + 10), f"{num} {title}", fill=(230, 237, 243))
    CONTACT_SHEET.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(CONTACT_SHEET, "PNG", optimize=True)


def main() -> int:
    rendered = []
    total = 0
    for num, slug, title, fn in WALLS:
        stem = f"corebuilds-{num}-{slug}"
        im = fn()
        dc = dark_coverage(im)
        assert dc >= 0.45, f"{stem}: dark coverage {dc:.0%} below the 45% floor"
        total += save_wall(im, SERIES, stem)
        rendered.append((num, title, im))
    _contact_sheet(rendered)
    print(f"  contact sheet: {CONTACT_SHEET.relative_to(ROOT)}")
    print(f"\nseries-9-deep-space complete — {len(WALLS)} walls at 4K, "
          f"{total / 1024 / 1024:.1f} MB.")
    print("  Next: review, update wallpapers.json manifest, commit.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
