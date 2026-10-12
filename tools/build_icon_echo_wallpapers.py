#!/usr/bin/env python3
"""Build Core Builds ``series-11-icon-echo``.

Six 3840x2160 wallpapers, each lit by one accent taken from the icon pack's
own catalogue (tools/catalog.json) and *shifted a step for the wall*: the
icon keeps its brand hex on the launcher, the wall carries the same hue a
shade deeper, so a home row and its background glow the same colour without
the wall competing with the icon. The shift is the documented transform in
:func:`wall_tones` — deep wash, main light, bright core — never a recolour.

Production boundary (per docs/research/icon-wallpaper-palette-study-2026-10.md):
palette cues only — no vendor logos, icon outlines or copied icon geometry;
the existing 102-wallpaper collection is untouched.

Accent provenance (catalog entries, checked 2026-10-11):
  103 Thermal Bloom  HeatLive #EB1E54 (+ companion violet #6900FF)
  104 Signal Red     YouTube #FF0033
  105 Green Current  Spotify #1ED760
  106 Molten Orbit   MaxPlayer #F8A000
  107 Frost Line     TiviMate #97C1FF
  108 Signal Azure   ARD Sounds #2D92FF

Writes full PNGs, both thumbnail copies, and ``docs/icon-echo-wallpapers.png``.
The manifest remains a separately reviewed source of truth.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_classic_wallpapers import H, THUMBS, W, save_wall  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-11-icon-echo"
CONTACT_SHEET = ROOT / "docs" / "icon-echo-wallpapers.png"

VOID = (4, 7, 15)
NIGHT = (13, 17, 23)
OFFWHITE = (230, 237, 243)

# Icon accents, straight from tools/catalog.json (see module docstring).
HEATLIFE_MAGENTA = (235, 30, 84)     # #EB1E54
COMPANION_VIOLET = (105, 0, 255)     # #6900FF, the HeatLive gradient partner
YOUTUBE_RED = (255, 0, 51)           # #FF0033
SPOTIFY_GREEN = (30, 215, 96)        # #1ED760
MAXPLAYER_AMBER = (248, 160, 0)      # #F8A000
TIVIMATE_BLUE = (151, 193, 255)      # #97C1FF
ARD_AZURE = (45, 146, 255)           # #2D92FF


def wall_tones(accent: tuple[int, int, int]):
    """The icon's accent, shifted a step for the wall.

    Returns (deep, mid, bright): deep is the ambient wash, mid the main
    light, bright the highlight core. Each is the icon's own hue held at a
    different distance from the night ground, so the wall reads as the icon
    dimmed — not as a different colour.
    """
    a = np.array(accent, dtype="float32")
    v = np.array(VOID, dtype="float32")
    o = np.array(OFFWHITE, dtype="float32")
    return (tuple(int(x) for x in (a * 0.45 + v * 0.55)),
            tuple(int(x) for x in (a * 0.72 + v * 0.28)),
            tuple(int(x) for x in (a * 0.82 + o * 0.18)))


def _base(top=VOID, bottom=NIGHT) -> Image.Image:
    """The Core night ground: a faint vertical lift, VOID up top."""
    ys = np.linspace(0, 1, H, dtype="float32")[:, None, None]
    t = ys ** 1.6
    arr = (np.array(top, dtype="float32") * (1 - t)
           + np.array(bottom, dtype="float32") * t)
    return Image.fromarray(np.broadcast_to(arr, (H, W, 3)).astype("uint8").copy())


def _wash(im: Image.Image, cx: float, cy: float, r: float,
          colour: tuple, strength: float) -> None:
    """A soft radial wash, composited by alpha over the ground."""
    mask = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(r * 0.55))
    mask = mask.point(lambda p: min(255, int(p * strength)))
    im.paste(Image.new("RGB", im.size, colour), (0, 0), mask)


def _line(im: Image.Image, points, colour, width=4, blur=16, strength=0.5) -> None:
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).line(points, fill=255, width=width, joint="curve")
    if blur:
        halo = mask.filter(ImageFilter.GaussianBlur(blur)).point(
            lambda p: min(255, int(p * strength)))
        im.paste(Image.new("RGB", im.size, colour), (0, 0), halo)
    im.paste(Image.new("RGB", im.size, colour), (0, 0), mask)


def _dot(im: Image.Image, x: float, y: float, r: float, colour,
         blur=20, strength=0.5) -> None:
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).ellipse((x - r, y - r, x + r, y + r), fill=255)
    if blur:
        halo = mask.filter(ImageFilter.GaussianBlur(blur)).point(
            lambda p: min(255, int(p * strength)))
        im.paste(Image.new("RGB", im.size, colour), (0, 0), halo)
    im.paste(Image.new("RGB", im.size, colour), (0, 0), mask)


def w103_thermal_bloom() -> Image.Image:
    """Magenta energy gathering at a low horizon, violet ambient above.

    HeatLive #EB1E54 with its #6900FF gradient partner; the study's
    'Thermal Bloom' treatment at full resolution.
    """
    deep, mid, bright = wall_tones(HEATLIFE_MAGENTA)
    vdeep, vmid, _vbright = wall_tones(COMPANION_VIOLET)
    im = _base()
    # The bloom: a wide low glow rising from left of centre.
    _wash(im, 1450, 1430, 1750, deep, 0.85)
    _wash(im, 1450, 1300, 900, mid, 0.38)
    # The horizon the energy sits on.
    _line(im, [(700, 1250), (3140, 1250)], mid, 3, 26, 0.55)
    _line(im, [(1240, 1251), (1980, 1251)], bright, 2, 8, 0.5)
    # The violet partner: one long arc across the upper third.
    _line(im, [(900, 760), (1560, 560), (2260, 560), (2900, 740)],
          vmid, 3, 22, 0.5)
    # A few sparks where the bloom meets the horizon.
    for x, y, r in ((1180, 1210, 4), (1760, 1180, 3), (2330, 1216, 4),
                    (2760, 1188, 3)):
        _dot(im, x, y, r, mid, 12, 0.5)
    return im


def w104_signal_red() -> Image.Image:
    """A restrained red transmission sweep: fine lines, one lit.

    YouTube #FF0033 — the study's 'Signal Red': a sweep, not a play
    button or logo.
    """
    deep, mid, bright = wall_tones(YOUTUBE_RED)
    im = _base()
    # A faint haze band where the signal travels.
    _wash(im, 1920, 860, 1500, deep, 0.34)
    # Four staggered lines; the second is the lit carrier with its node.
    _line(im, [(620, 620), (2540, 620)], mid, 2, 14, 0.42)
    _line(im, [(980, 780), (2860, 780)], bright, 3, 22, 0.6)
    _dot(im, 2860, 780, 7, bright, 18, 0.6)
    _line(im, [(760, 940), (2160, 940)], mid, 2, 14, 0.34)
    _line(im, [(1300, 1100), (2560, 1100)], deep, 2, 12, 0.5)
    return im


def w105_green_current() -> Image.Image:
    """Two organic ribbons flowing across the frame.

    Spotify #1ED760 — the study's 'Green Current': flowing ribbons, not
    the disc or the three-wave mark.
    """
    deep, mid, bright = wall_tones(SPOTIFY_GREEN)
    im = _base()
    _wash(im, 1500, 1050, 1600, deep, 0.30)
    # Ribbon A: the main current, a long sweep with a slow bend.
    _line(im, [(160, 840), (980, 800), (1900, 980), (2880, 1120),
               (3700, 1080)], mid, 5, 26, 0.5)
    _line(im, [(160, 840), (980, 800)], bright, 3, 10, 0.45)
    # Ribbon B: thinner, offset, with one small loop before it joins on.
    _line(im, [(220, 1260), (1100, 1220), (1700, 1080), (2080, 1180),
               (2420, 1120), (3380, 1240)], mid, 3, 18, 0.4)
    _dot(im, 2080, 1180, 5, bright, 14, 0.5)
    return im


def w106_molten_orbit() -> Image.Image:
    """Warm amber orbital light: two rings, one lit node.

    MaxPlayer #F8A000 — the study's 'Molten Orbit': no M/play
    construction.
    """
    deep, mid, bright = wall_tones(MAXPLAYER_AMBER)
    im = _base()
    _wash(im, 1920, 980, 1500, deep, 0.42)
    # Two crossing orbital rings (drawn as ellipse outlines, tilted).
    for rx, ry, tilt, colour, width in (
            (1500, 480, -18, mid, 3), (1750, 380, -9, deep, 2)):
        mask = Image.new("L", im.size, 0)
        d = ImageDraw.Draw(mask)
        cx, cy = 1920, 980
        d.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), outline=255,
                  width=width)
        mask = mask.rotate(-tilt, resample=Image.BICUBIC)
        halo = mask.filter(ImageFilter.GaussianBlur(14)).point(
            lambda p: min(255, int(p * 0.5)))
        im.paste(Image.new("RGB", im.size, colour), (0, 0), halo)
        im.paste(Image.new("RGB", im.size, colour), (0, 0), mask)
    # The lit node: where the first ring passes right of centre.
    nx, ny = 1920 + 1500 * math.cos(math.radians(243)), 980 + 480 * math.sin(math.radians(243))
    _dot(im, nx, ny, 9, bright, 26, 0.65)
    # A small dimmer companion on the second ring.
    mx, my = 1920 - 1750 * math.cos(math.radians(30)), 980 + 380 * math.sin(math.radians(30))
    _dot(im, mx, my, 5, mid, 16, 0.5)
    return im


def w107_frost_line() -> Image.Image:
    """A pale horizon with sparse ticks standing on it.

    TiviMate #97C1FF — the light blue of its wordmark on the night
    ground; the ticks read as a guide of programme bars, not as numbers.
    """
    deep, mid, bright = wall_tones(TIVIMATE_BLUE)
    im = _base()
    _wash(im, 1920, 1180, 1700, deep, 0.36)
    # The line itself.
    _line(im, [(400, 1080), (3440, 1080)], mid, 3, 24, 0.55)
    _line(im, [(1300, 1081), (2200, 1081)], bright, 2, 8, 0.5)
    # Sparse ticks above the line, varying height, evenly spaced.
    for x, h, colour in ((760, 60, mid), (1160, 120, bright), (1560, 80, mid),
                         (1960, 160, bright), (2360, 90, mid), (2760, 130, mid),
                         (3160, 70, mid)):
        _line(im, [(x, 1080 - h), (x, 1080)], colour, 4, 10, 0.4)
    return im


def w108_signal_azure() -> Image.Image:
    """A vertical beam of azure light with thin cross-sweeps.

    ARD Sounds #2D92FF — the broadcast blue of the ARD brand; the beam
    and sweeps read as a signal, not as any channel's mark.
    """
    deep, mid, bright = wall_tones(ARD_AZURE)
    im = _base()
    # The beam: a soft wide column plus a crisp bright core.
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rectangle((1920 - 260, 140, 1920 + 260, 1460), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(90)).point(lambda p: min(255, int(p * 0.5)))
    im.paste(Image.new("RGB", im.size, deep), (0, 0), mask)
    _line(im, [(1920, 180), (1920, 1420)], mid, 4, 18, 0.6)
    _line(im, [(1921, 300), (1921, 1050)], bright, 2, 8, 0.5)
    # Three cross-sweeps at staggered heights.
    _line(im, [(1180, 520), (2660, 520)], mid, 2, 14, 0.42)
    _line(im, [(1420, 820), (2420, 820)], bright, 3, 18, 0.55)
    _line(im, [(1300, 1120), (2540, 1120)], mid, 2, 14, 0.36)
    # The bright crossing.
    _dot(im, 1920, 820, 7, bright, 20, 0.65)
    return im


WALLS = [
    ("103", "thermal-bloom", "103 Thermal Bloom", w103_thermal_bloom),
    ("104", "signal-red", "104 Signal Red", w104_signal_red),
    ("105", "green-current", "105 Green Current", w105_green_current),
    ("106", "molten-orbit", "106 Molten Orbit", w106_molten_orbit),
    ("107", "frost-line", "107 Frost Line", w107_frost_line),
    ("108", "signal-azure", "108 Signal Azure", w108_signal_azure),
]


def build_sheet() -> None:
    """2x3 contact sheet at 960x540 per panel for the README/review."""
    pw, ph = 960, 540
    sheet = Image.new("RGB", (pw * 3 + 40, ph * 2 + 30), (9, 12, 18))
    for i, (num, slug, title, fn) in enumerate(WALLS):
        im = fn().resize((pw, ph), Image.LANCZOS)
        x = 10 + (i % 3) * (pw + 10)
        y = 10 + (i // 3) * (ph + 10)
        sheet.paste(im, (x, y))
    sheet.save(CONTACT_SHEET, "PNG")
    print(f"  contact sheet -> {CONTACT_SHEET.relative_to(ROOT)}")


def main() -> int:
    SERIES.mkdir(parents=True, exist_ok=True)
    total = 0
    for num, slug, title, fn in WALLS:
        stem = f"corebuilds-{num}-{slug}"
        im = fn()
        total += save_wall(im, SERIES, stem)
    print(f"\n6 icon-echo walls · {total / 1e6:.1f} MB total")
    print("  Thumbs bundled to app/src/main/assets/wallpapers_thumbs/")
    print("  Next: add manifest entries, run sync_wallpaper_manifest.py.")
    build_sheet()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
