#!/usr/bin/env python3
"""
Core Builds Icon Pack - series-15-spectra, the Core Builds Plus walls.

Twelve 4K walls of light split into its colours: a solar spectrum with
its absorption lines, dispersion fans, a soap film, a diffraction grating,
spectral contours, a moonbow. Every other series
keeps to the house palette; Spectra is the one that uses the whole spectrum,
which is what makes it read as its own set. All procedural and seeded; a
re-run is byte-identical.

The series is a Core Builds Plus extra: it is indexed by
Wallpapers/plus-manifest.json (not manifest.json), and only the Google Play
build bundles its thumbs (tools/sync_wallpaper_manifest.py, collection
"plus"). The files themselves are public in this repository like every
other wall.

House rules (Wallpapers/README.md): dark-coverage floor, a calm lower third
for launcher cards, no grain.

Writes:
  Wallpapers/series-15-spectra/corebuilds-NNN-slug.png   3840x2160
  Wallpapers/thumbs/corebuilds-NNN-slug.jpg              480x270
  docs/spectra-wallpapers.png                            contact sheet
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_classic_wallpapers import (  # noqa: E402
    GLOW_CYAN,
    H,
    NIGHT,
    THUMB_H,
    THUMB_W,
    VOID,
    W,
    dark_coverage,
)
from build_horizons_wallpapers import _fbm2d  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-15-spectra"
THUMBS = ROOT / "Wallpapers" / "thumbs"
CONTACT_SHEET = ROOT / "docs" / "spectra-wallpapers.png"

ASPECT = W / H


def _col(c):
    return np.array(c, dtype="float32").reshape(1, 1, 3)


def _grid(scale: int = 1):
    """Pixel-centre coordinates: x in [0, ASPECT], y in [0, 1]."""
    h, w = H // scale, W // scale
    yy, xx = np.mgrid[0:h, 0:w].astype("float32")
    return (xx + 0.5) / h, (yy + 0.5) / h


def spectrum(t: np.ndarray) -> np.ndarray:
    """t in [0, 1] -> RGB 0..255 from red (0) through violet (1).

    A smooth fit to the visible spectrum rather than an HSV wheel: the
    yellow band is narrow, the green broad, and the ends darken as the eye
    loses sensitivity, so a fan of it reads as light rather than a UI hue
    picker.
    """
    t = np.clip(t, 0, 1)
    wl = 700.0 - t * 320.0  # nm, 700 (red) .. 380 (violet)
    r = np.where(wl >= 580, 1.0,
        np.where(wl >= 510, (wl - 510) / 70.0,
        np.where(wl >= 440, 0.0, (440 - wl) / 60.0 * 0.6)))
    g = np.where(wl >= 645, 0.0,
        np.where(wl >= 580, (645 - wl) / 65.0,
        np.where(wl >= 490, 1.0,
        np.where(wl >= 440, (wl - 440) / 50.0, 0.0))))
    b = np.where(wl >= 510, 0.0,
        np.where(wl >= 490, (510 - wl) / 20.0, 1.0))
    edge = np.where(wl > 650, 0.35 + 0.65 * (700 - wl) / 50.0,
           np.where(wl < 420, 0.35 + 0.65 * (wl - 380) / 40.0, 1.0))
    rgb = np.stack([r, g, b], axis=-1) * edge[..., None]
    return np.power(np.clip(rgb, 0, 1), 0.8) * 255.0


def thin_film(t: np.ndarray) -> np.ndarray:
    """Interference colours of a soap film of thickness t (0..1): bronze,
    magenta, blue, green, gold - the sequence a real film runs through."""
    stops = np.array([
        (40, 30, 24), (150, 90, 40), (190, 60, 140), (60, 70, 220),
        (40, 170, 200), (70, 210, 120), (230, 200, 70), (220, 90, 150),
        (90, 120, 230), (60, 200, 170),
    ], dtype="float32")
    n = len(stops) - 1
    x = np.clip(t, 0, 1) * n
    i = np.minimum(np.floor(x).astype(int), n - 1)
    f = (x - i)[..., None]
    return stops[i] * (1 - f) + stops[i + 1] * f


def _up(a: np.ndarray) -> np.ndarray:
    """Resize a field (h, w) or image (h, w, 3) to full 4K, bicubic."""
    if a.ndim == 2:
        return np.asarray(Image.fromarray(a.astype("float32"), mode="F").resize((W, H), Image.BICUBIC),
                          dtype="float32")
    return np.stack([_up(a[..., c]) for c in range(3)], axis=-1)


def _seg_dist(x, y, x0, y0, x1, y1):
    """Distance from each pixel to the segment (x0,y0)-(x1,y1)."""
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    t = np.clip(((x - x0) * dx + (y - y0) * dy) / L2, 0, 1)
    return np.hypot(x - (x0 + t * dx), y - (y0 + t * dy))


def _calm(arr: np.ndarray, start: float = 0.64, depth: float = 0.9) -> np.ndarray:
    y0 = int(H * start)
    t = np.linspace(0, 1, H - y0, dtype="float32")[:, None, None]
    t = np.power(t, 1.3) * depth
    arr[y0:] = arr[y0:] * (1 - t) + _col(VOID) * t
    return arr


def _ground(seed: int, tint=(10, 14, 26), amount: float = 1.0) -> np.ndarray:
    """The night ground with a faint large-scale variation, so no wall is flat."""
    f = _up(_fbm2d(seed, 900, 3, w=W // 4, h=H // 4))
    return _col(VOID) + f[..., None] * _col(tint) * amount


def _tone(arr: np.ndarray) -> np.ndarray:
    """Soft-clip highlights so stacked glows roll off instead of flattening."""
    a = arr / 255.0
    a = 1.0 - np.exp(-a * 1.35)
    return a / (1.0 - math.exp(-1.35)) * 255.0


def _finish(arr: np.ndarray) -> Image.Image:
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))


# ---------------------------------------------------------------- walls ---

def w157_fraunhofer() -> Image.Image:
    """The solar spectrum as a spectrometer draws it: one lit band, crossed by
    the dark absorption lines that name the elements in the Sun."""
    x, y = _grid()
    arr = _ground(15701, (8, 12, 28))
    x0, x1 = ASPECT * 0.1, ASPECT * 0.9
    t = (x - x0) / (x1 - x0)
    cy, half = 0.38, 0.075
    inside = (t >= 0) & (t <= 1)
    profile = np.exp(-((y - cy) / half) ** 6)
    soft = np.exp(-((y - cy) / (half * 3.2)) ** 2) * 0.22
    # Fraunhofer lines (A, B, C, D, E, b, F, G, H, K), at their wavelengths.
    lines = [(759.4, 0.9, 0.0016), (686.7, 0.7, 0.0012), (656.3, 0.95, 0.0016),
             (589.3, 0.9, 0.0018), (527.0, 0.6, 0.0011), (518.4, 0.7, 0.0013),
             (486.1, 0.85, 0.0015), (430.8, 0.75, 0.0014), (396.8, 0.9, 0.0016),
             (393.4, 0.9, 0.0016)]
    absorb = np.ones_like(x)
    for nm, depth, width in lines:
        lt = (700.0 - nm) / 320.0
        if 0 <= lt <= 1:
            absorb *= 1 - depth * np.exp(-((t - lt) / width) ** 2)
    # The band fades out at both ends rather than stopping in a hard box.
    ends = np.clip(t / 0.04, 0, 1) * np.clip((1 - t) / 0.04, 0, 1)
    band = np.where(inside, profile * absorb * ends, 0.0)
    halo = np.where(inside, soft * ends, 0.0)
    colour = spectrum(t)
    arr = arr + band[..., None] * colour * 1.0 + halo[..., None] * colour
    # The slit it came through: a thin bright line above the band.
    slit = np.exp(-((y - (cy - 0.16)) / 0.0018) ** 2) * inside * ends * 0.55
    arr = arr + slit[..., None] * _col((210, 220, 240))
    return _finish(_calm(_tone(arr)))


def w158_dispersion() -> Image.Image:
    """Three beams from the top edge, each opening into its own narrow fan."""
    x, y = _grid()
    arr = _ground(15801, (8, 12, 30))
    for k, (sx, spread, rot) in enumerate([(0.55, 9, 62), (1.15, 12, 78), (1.75, 7, 96)]):
        sy = -0.02
        ang = np.degrees(np.arctan2(y - sy, x - sx))
        t = (ang - (rot - spread / 2)) / spread
        inside = (t > 0) & (t < 1)
        r = np.hypot(x - sx, y - sy)
        fan = np.where(inside, np.exp(-r * (0.9 + 0.2 * k)), 0.0)
        edge = np.exp(-((t - 0.5) / 0.62) ** 4)
        arr = arr + (fan * edge)[..., None] * spectrum(t) * 0.95
    return _finish(_calm(_tone(arr)))


def w159_hex_lens() -> Image.Image:
    """The Core hexagon in glass, point up, its rim breaking light into colour."""
    x, y = _grid()
    arr = _ground(15901, (8, 16, 28))
    cx, cy, R = ASPECT * 0.5, 0.40, 0.23
    # Hexagon, point up as the brand draws it (never rotated): its edge
    # centres sit at 0, 60, 120 ... degrees, so its vertices are top and bottom.
    ang = np.arctan2(y - cy, x - cx)
    sector = (ang + math.pi / 6) % (math.pi / 3) - math.pi / 6
    r = np.hypot(x - cx, y - cy)
    rad = R * math.cos(math.pi / 6) / np.cos(sector)
    d = r - rad
    rim = np.exp(-(d / 0.0035) ** 2)
    halo = np.exp(-np.clip(d, 0, None) / 0.05) * (d > 0)
    # Red meets violet at the bottom vertex, where the calm third swallows
    # the seam, rather than as a wedge on one side.
    hue = ((np.arctan2(y - cy, x - cx) / (2 * math.pi)) + 0.75) % 1.0
    arr = arr + rim[..., None] * spectrum(hue) * 1.1
    arr = arr + halo[..., None] * spectrum(hue) * 0.28
    inner = np.clip(-d / R, 0, 1)
    arr = arr + (np.exp(-((inner - 0.08) / 0.06) ** 2) * (d < 0))[..., None] * _col((40, 90, 140)) * 0.35
    core = np.exp(-(r / 0.03) ** 2)
    arr = arr + core[..., None] * _col((220, 245, 255)) * 0.6
    return _finish(_calm(_tone(arr)))


def w160_soap_film() -> Image.Image:
    """A soap bubble in the dark: thin-film colour swirling over its skin,
    brightest at the rim where the film is seen edge-on."""
    x, y = _grid(2)
    hw, hh = x.shape[1], x.shape[0]
    cx, cy, R = ASPECT * 0.6, 0.42, 0.31
    r = np.hypot(x - cx, y - cy) / R
    f = _fbm2d(16001, 260, 5, w=hw, h=hh)
    wx = _fbm2d(16002, 420, 4, w=hw, h=hh)
    wy = _fbm2d(16003, 420, 4, w=hw, h=hh)
    yy, xx = np.mgrid[0:hh, 0:hw].astype("float32")
    sx = np.clip(xx + (wx - 0.5) * 0.5 * hw, 0, hw - 1).astype(int)
    sy = np.clip(yy + (wy - 0.5) * 0.5 * hh, 0, hh - 1).astype(int)
    # Film thinner at the top (drainage), swirled by the warp.
    thick = f[sy, sx] * 1.6 + (y - (cy - R)) / (2 * R) * 0.9
    # Skip the film's dark first band: on a bubble it read as dirt.
    # A triangle wave, not a modulo: the film runs up and back down its
    # colour sequence, so no band ever ends in a hard step.
    wave = 1.0 - np.abs(2.0 * np.mod(thick * 0.8, 1.0) - 1.0)
    film = thin_film(0.14 + 0.86 * wave)
    disk = np.clip(1 - r, 0, 1) > 0
    # Nearly clear at the centre, coloured towards the rim (Fresnel).
    fresnel = np.where(disk, 0.06 + 0.94 * np.power(np.clip(r, 0, 1), 3.0), 0.0)
    rim = np.exp(-((r - 1.0) / 0.012) ** 2) * 0.8
    arr = _col(VOID) + film * fresnel[..., None] * 0.85 + rim[..., None] * _col((190, 210, 235))
    # A window reflection, upper left of the bubble.
    hx, hy = cx - R * 0.42, cy - R * 0.45
    win = np.exp(-(((x - hx) / (R * 0.16)) ** 2 + ((y - hy) / (R * 0.1)) ** 2) ** 2)
    arr = arr + win[..., None] * _col((235, 245, 255)) * 0.55
    return _finish(_calm(_up(arr)))


def w161_glory() -> Image.Image:
    """A glory: concentric spectral rings around a small bright source."""
    x, y = _grid()
    arr = _ground(16101, (10, 12, 26))
    cx, cy = ASPECT * 0.64, 0.34
    r = np.hypot(x - cx, y - cy)
    rings = np.zeros_like(r)
    col = np.zeros(r.shape + (3,), dtype="float32")
    for k, r0 in enumerate([0.07, 0.13, 0.205, 0.29]):
        width = 0.012 + 0.006 * k
        t = np.clip((r - (r0 - width)) / (2 * width), 0, 1)
        band = np.exp(-((r - r0) / width) ** 2) * (0.9 - 0.17 * k)
        col += band[..., None] * spectrum(1 - t)
        rings += band
    arr = arr + col * 0.9
    arr = arr + np.exp(-(r / 0.018) ** 2)[..., None] * _col((255, 248, 235)) * 0.9
    arr = arr + np.exp(-(r / 0.09) ** 2)[..., None] * _col((70, 80, 110)) * 0.35
    return _finish(_calm(_tone(arr)))


def w162_contours() -> Image.Image:
    """A terrain drawn only as contour lines, each coloured by its height:
    low ground red, the peaks violet."""
    f = _up(_fbm2d(16201, 1300, 4, w=W // 4, h=H // 4))
    f = (f - f.min()) / (f.max() - f.min())
    levels = 26
    gy, gx = np.gradient(f)
    slope = np.hypot(gx, gy) + 1e-6
    # Screen-space distance to the nearest contour, so every line has one width.
    frac = f * levels
    d = np.abs(frac - np.round(frac)) / levels / slope
    line = np.exp(-(d / 1.6) ** 2)
    hue = np.round(frac) / levels
    x, y = _grid()
    vign = np.exp(-(((x / ASPECT - 0.5) / 0.62) ** 2 + ((y - 0.38) / 0.5) ** 2))
    arr = _ground(16204, (8, 12, 26)) + (line * vign)[..., None] * spectrum(hue) * 0.9
    return _finish(_calm(_tone(arr)))


def w163_light_pillars() -> Image.Image:
    """Light pillars over still water: tall columns, each its own colour,
    each with a broken reflection below the shore line."""
    x, y = _grid()
    arr = _ground(16301, (8, 10, 24))
    rnd = np.random.RandomState(16302)
    shore = 0.58
    ripple = _up(_fbm2d(16303, 40, 3, w=W // 4, h=H // 4))
    n = 9
    for k in range(n):
        px = ASPECT * (0.12 + 0.76 * k / (n - 1)) + rnd.uniform(-0.04, 0.04)
        top = rnd.uniform(0.05, 0.2)
        width = rnd.uniform(0.006, 0.012)
        colour = spectrum(np.full((1, 1), k / (n - 1)))
        body = np.exp(-((x - px) / width) ** 2)
        rise = np.clip((y - top) / 0.28, 0, 1) * (y < shore)
        glow = np.exp(-((x - px) / (width * 7)) ** 2) * rise
        arr = arr + (body * rise)[..., None] * colour * 0.85 + glow[..., None] * colour * 0.12
        # Reflection: the same column, mirrored, broken by ripples, fading.
        depth = np.clip((y - shore) / 0.25, 0, 1)
        wob = (ripple - 0.5) * 0.02
        refl = np.exp(-((x - px + wob) / (width * 1.6)) ** 2) * (y >= shore) * (1 - depth) ** 2
        arr = arr + (refl * (0.5 + 0.5 * np.sin(y * 900 + ripple * 9)))[..., None] * colour * 0.4
    line = np.exp(-((y - shore) / 0.0015) ** 2) * 0.25
    arr = arr + line[..., None] * _col((90, 110, 140))
    return _finish(_calm(_tone(arr), start=0.62))


def w164_chromatic_ribbon() -> Image.Image:
    """One ribbon of light across the frame, split into red, green and blue."""
    x, y = _grid()
    arr = _ground(16401, (6, 10, 22))
    base = 0.36 + 0.08 * np.sin(x * 2.3 + 0.6) + 0.03 * np.sin(x * 6.1)
    for c, off in enumerate([-0.012, 0.0, 0.012]):
        d = y - (base + off)
        line = np.exp(-(d / 0.0045) ** 2) + 0.25 * np.exp(-(d / 0.03) ** 2)
        fade = np.clip(np.sin(x / ASPECT * math.pi) * 1.4, 0, 1)
        arr[..., c] += line * fade * 230
    return _finish(_calm(_tone(arr)))


def w165_spectral_aurora() -> Image.Image:
    """An aurora curtain whose colour runs the spectrum along its length."""
    x, y = _grid()
    f = _up(_fbm2d(16501, 1400, 4, w=W // 4, h=H // 4))
    g = _up(_fbm2d(16504, 260, 4, w=W // 4, h=H // 4))
    centre = 0.30 + (f - 0.5) * 0.35
    band = np.exp(-((y - centre) / 0.16) ** 2) * np.clip((y - centre + 0.2) / 0.2, 0, 1)
    rays = 0.5 + 0.5 * np.power(0.5 + 0.5 * np.sin((x + (g - 0.5) * 0.18) * 110), 1.8)
    t = np.clip(band * rays * (0.4 + f * 0.9), 0, 1)
    hue = np.clip(x / ASPECT * 0.85 + 0.08, 0, 1)
    arr = _ground(16507, (6, 10, 22)) + t[..., None] * spectrum(hue) * 0.95
    return _finish(_calm(_tone(arr), start=0.58))


def w166_diffraction() -> Image.Image:
    """A street light seen through a diffraction grating: a white source
    with its first and second spectral orders fanned out either side."""
    x, y = _grid()
    arr = _ground(16601, (8, 10, 24))
    cx, cy = ASPECT * 0.5, 0.36
    dy = y - cy
    vprof = np.exp(-(dy / 0.022) ** 2)
    for order, (lo, hi, gain) in {1: (0.16, 0.34, 0.95), 2: (0.40, 0.76, 0.5)}.items():
        for sign in (-1, 1):
            u = (x - cx) * sign
            t = (u - lo) / (hi - lo)
            inside = (t >= 0) & (t <= 1)
            # Violet bends least, red most: t runs violet (0) to red (1).
            band = np.where(inside, vprof * np.exp(-((t - 0.5) / 0.7) ** 4), 0.0)
            arr = arr + band[..., None] * spectrum(1 - np.clip(t, 0, 1)) * gain
    r = np.hypot(x - cx, y - cy)
    arr = arr + np.exp(-(r / 0.014) ** 2)[..., None] * _col((255, 250, 240)) * 1.1
    arr = arr + np.exp(-(r / 0.07) ** 2)[..., None] * _col((120, 130, 160)) * 0.35
    # Faint vertical spikes from the grating's cross-structure.
    spike = np.exp(-((x - cx) / 0.0025) ** 2) * np.exp(-np.abs(dy) / 0.12) * 0.35
    arr = arr + spike[..., None] * _col((200, 210, 240))
    return _finish(_calm(_tone(arr)))


def w167_spectrum_orbit() -> Image.Image:
    """Long-exposure orbits, each trail a different colour of the spectrum."""
    x, y = _grid(2)
    cx, cy = ASPECT * 0.5, 0.40
    arr = np.zeros(x.shape + (3,), dtype="float32")
    for k in range(11):
        a = 0.34 + k * 0.05
        b = a * 0.36
        tilt = math.radians(-14 + k * 1.6)
        u = (x - cx) * math.cos(tilt) + (y - cy) * math.sin(tilt)
        v = -(x - cx) * math.sin(tilt) + (y - cy) * math.cos(tilt)
        e = np.sqrt((u / a) ** 2 + (v / b) ** 2)
        theta = np.arctan2(v / b, u / a)
        trail = np.exp(-((e - 1.0) / 0.0045) ** 2) + 0.18 * np.exp(-((e - 1.0) / 0.02) ** 2)
        head = (theta % (2 * math.pi)) / (2 * math.pi)
        arc = np.power(head, 2.5)
        arr += (trail * arc)[..., None] * spectrum(np.full_like(x, k / 10.0)) * 1.1
    arr = _col(VOID) + arr
    arr = arr + np.exp(-(np.hypot(x - cx, y - cy) / 0.02) ** 2)[..., None] * _col((255, 245, 230)) * 0.7
    return _finish(_calm(_tone(_up(arr))))


def w168_moonbow() -> Image.Image:
    """A moonbow over a dark ridge: the faint night rainbow, under stars."""
    x, y = _grid()
    arr = _ground(16801, (8, 12, 30))
    rnd = np.random.RandomState(16802)
    field = Image.new("L", (W, H), 0)
    dr = ImageDraw.Draw(field)
    for sx, sy, b in zip(rnd.randint(0, W, 2600), rnd.randint(0, int(H * 0.6), 2600),
                         rnd.uniform(0.2, 1.0, 2600)):
        rad = 1.2 + 1.8 * b
        dr.ellipse([sx - rad, sy - rad, sx + rad, sy + rad], fill=int(255 * b))
    stars = np.asarray(field.filter(ImageFilter.GaussianBlur(1.4)), dtype="float32") / 255.0
    arr = arr + stars[..., None] * _col((200, 215, 255)) * 0.9
    cx, cy = ASPECT * 0.5, 0.86
    r = np.hypot(x - cx, y - cy)
    R0, w = 0.62, 0.045
    t = (r - (R0 - w)) / (2 * w)
    band = np.exp(-((r - R0) / w) ** 4) * (y < cy)
    arr = arr + band[..., None] * spectrum(1 - np.clip(t, 0, 1)) * 0.45
    arr = arr + np.exp(-((r - R0 + 0.1) / 0.06) ** 2)[..., None] * _col((40, 50, 70)) * 0.3
    ridge_y = 0.66 + 0.04 * np.sin(x * 3.1 + 1.2) + 0.02 * np.sin(x * 9.7)
    ridge = np.clip((y - ridge_y) / 0.004, 0, 1)
    arr = arr * (1 - ridge[..., None]) + _col(VOID) * ridge[..., None]
    return _finish(_calm(_tone(arr), start=0.66))


WALLS = [
    (157, "fraunhofer", "157 Fraunhofer", w157_fraunhofer),
    (158, "dispersion", "158 Dispersion", w158_dispersion),
    (159, "hex-lens", "159 Hex Lens", w159_hex_lens),
    (160, "soap-film", "160 Soap Film", w160_soap_film),
    (161, "glory", "161 Glory", w161_glory),
    (162, "contours", "162 Contours", w162_contours),
    (163, "light-pillars", "163 Light Pillars", w163_light_pillars),
    (164, "chromatic-ribbon", "164 Chromatic Ribbon", w164_chromatic_ribbon),
    (165, "spectral-aurora", "165 Spectral Aurora", w165_spectral_aurora),
    (166, "diffraction", "166 Diffraction", w166_diffraction),
    (167, "spectrum-orbit", "167 Spectrum Orbit", w167_spectrum_orbit),
    (168, "moonbow", "168 Moonbow", w168_moonbow),
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
        dr.text((x + 6, y + th + 5), title, fill=(200, 210, 225))
    sheet.save(CONTACT_SHEET, optimize=True)


def main() -> int:
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", type=int, action="append")
    args = parser.parse_args()
    rendered, total = [], 0
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
        rendered.append((num, title, im))
    _contact_sheet(rendered)
    print(f"  contact sheet: {CONTACT_SHEET.relative_to(ROOT)}")
    print(f"\nseries-15-spectra complete — {len(WALLS)} walls at 4K, {total / 1024 / 1024:.1f} MB.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
