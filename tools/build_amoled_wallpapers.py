#!/usr/bin/env python3
"""Build Core Builds Classic ``series-8-amoled``.

Twelve deterministic 3840x2160 wallpapers use exact RGB ``#000000`` as their
canvas. Accents are deliberately narrow and sparse: an OLED pixel only turns
off at true black, while broad near-black gradients defeat the point of an
AMOLED set and can make low-level smearing more visible.

Every render is required to retain at least 50% exact-black pixels. In practice
the set is substantially blacker; the assertion is the permanent contract.

Writes full PNGs, both thumbnail copies, and ``docs/amoled-wallpapers.png``.
The manifest remains a separately reviewed source of truth.
"""
from __future__ import annotations

import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from build_classic_wallpapers import BUNDLED_THUMBS, H, THUMBS, W, save_wall

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-8-amoled"
CONTACT_SHEET = ROOT / "docs" / "amoled-wallpapers.png"
BLACK = (0, 0, 0)
CYAN = (0, 212, 255)
GLOW = (126, 238, 255)
BLUE = (79, 172, 254)
VIOLET = (138, 72, 144)
EMBER = (192, 58, 32)
WHITE = (216, 236, 242)
MIN_TRUE_BLACK = 0.50


def true_black_fraction(im: Image.Image) -> float:
    pixels = np.asarray(im.convert("RGB"))
    return float(np.all(pixels == 0, axis=2).mean())


def _glow_mask(im: Image.Image, mask: Image.Image, colour: tuple[int, int, int],
               blur: int = 20, strength: float = 0.42) -> None:
    """Composite a restrained coloured halo and then a crisp mask core."""
    if blur:
        halo = mask.filter(ImageFilter.GaussianBlur(blur)).point(
            lambda p: min(255, int(p * strength)))
        wash = Image.new("RGB", im.size, colour)
        im.paste(wash, (0, 0), halo)
    im.paste(Image.new("RGB", im.size, colour), (0, 0), mask)


def _line(im: Image.Image, points, colour=CYAN, width=4, blur=18,
          strength=0.38) -> None:
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).line(points, fill=255, width=width, joint="curve")
    _glow_mask(im, mask, colour, blur, strength)


def void_horizon() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    y = 1010
    _line(im, [(430, y), (3410, y)], CYAN, 3, 24, 0.35)
    _line(im, [(1470, y + 1), (2370, y + 1)], GLOW, 2, 8, 0.45)
    return im


def one_pixel() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    mask = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(mask)
    d.rectangle((1898, 978, 1941, 1021), fill=255)
    _glow_mask(im, mask, CYAN, 34, 0.44)
    # Four one-pixel witnesses make the central signal feel deliberately placed.
    px = im.load()
    for x, y in ((1120, 620), (2780, 710), (1240, 1440), (2660, 1510)):
        px[x, y] = (36, 84, 96)
    return im


def off_grid() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    mask = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(mask)
    left, top, right, bottom = 420, 300, 1660, 1120
    for x in range(left, right + 1, 124):
        d.line((x, top, x, bottom), fill=105, width=2)
    for y in range(top, bottom + 1, 102):
        d.line((left, y, right, y), fill=105, width=2)
    d.line((right + 70, top - 50, right + 70, bottom + 80), fill=255, width=5)
    d.line((right + 70, bottom + 80, right + 430, bottom + 80), fill=255, width=5)
    _glow_mask(im, mask, BLUE, 12, 0.30)
    return im


def orbit() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    mask = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(mask)
    box = (890, 300, 2940, 1640)
    d.ellipse(box, outline=150, width=3)
    d.arc((1160, 480, 2670, 1460), 188, 344, fill=255, width=5)
    d.ellipse((2731, 917, 2751, 937), fill=255)
    _glow_mask(im, mask, CYAN, 16, 0.36)
    return im


def starfield() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    d = ImageDraw.Draw(im)
    rnd = random.Random(7308)
    for _ in range(420):
        x = rnd.randrange(220, W - 220)
        y = rnd.randrange(150, int(H * 0.72))
        b = rnd.choice((52, 68, 82, 104, 136, 188))
        c = (int(b * 0.62), int(b * 0.86), b)
        r = 1 if b < 130 else 2
        d.ellipse((x - r, y - r, x + r, y + r), fill=c)
    _line(im, [(2480, 690), (2800, 602)], CYAN, 3, 10, 0.34)
    return im


def ember_sliver() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    mask = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(mask)
    d.polygon([(590, 1560), (3020, 430), (3060, 442), (640, 1591)], fill=255)
    _glow_mask(im, mask, EMBER, 28, 0.36)
    _line(im, [(680, 1558), (2200, 850)], (255, 116, 65), 2, 8, 0.32)
    return im


def signal_sweep() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    for index, (colour, width) in enumerate(((BLUE, 3), (CYAN, 4), (GLOW, 2))):
        points = []
        for i in range(220):
            t = i / 219
            x = 460 + t * 2870
            y = 530 + index * 92 + 470 * math.sin((t - 0.12) * math.pi * 1.16)
            points.append((x, y))
        _line(im, points, colour, width, 17, 0.28)
    return im


def underglow() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    mask = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle((1210, 1660, 2630, 1670), radius=5, fill=255)
    _glow_mask(im, mask, VIOLET, 54, 0.38)
    _line(im, [(1510, 1664), (2330, 1664)], CYAN, 2, 14, 0.25)
    return im


def monoline() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    # One unbroken construction line: bracket, open hex, and descending rail.
    points = [(590, 520), (1040, 520), (1280, 920), (1040, 1320),
              (580, 1320), (360, 950), (580, 580), (1040, 580),
              (1240, 920), (1040, 1260), (720, 1260), (720, 1510),
              (2850, 1510), (3180, 1180)]
    _line(im, points, CYAN, 5, 20, 0.34)
    _line(im, [(2850, 1510), (3180, 1180)], GLOW, 2, 8, 0.38)
    return im


def twin_horizons() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    _line(im, [(380, 845), (3460, 845)], CYAN, 3, 22, 0.32)
    _line(im, [(820, 1215), (3020, 1215)], VIOLET, 4, 28, 0.38)
    return im


def eclipse() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    mask = Image.new("L", im.size, 0)
    d = ImageDraw.Draw(mask)
    box = (1320, 300, 2640, 1620)
    d.arc(box, 108, 252, fill=255, width=9)
    d.arc((1360, 340, 2600, 1580), 112, 248, fill=145, width=3)
    _glow_mask(im, mask, CYAN, 28, 0.38)
    return im


def corner_signal() -> Image.Image:
    im = Image.new("RGB", (W, H), BLACK)
    cyan = [(360, 340), (1040, 340), (1290, 590), (1290, 1060)]
    violet = [(3480, 1480), (2810, 1480), (2560, 1230), (2560, 820)]
    _line(im, cyan, CYAN, 4, 20, 0.34)
    _line(im, violet, VIOLET, 4, 24, 0.38)
    return im


WALLS = [
    (69, "void-horizon", "Void Horizon", void_horizon),
    (70, "one-pixel", "One Pixel", one_pixel),
    (71, "off-grid", "Off Grid", off_grid),
    (72, "orbit", "Orbit", orbit),
    (73, "starfield", "Starfield", starfield),
    (74, "ember-sliver", "Ember Sliver", ember_sliver),
    (75, "signal-sweep", "Signal Sweep", signal_sweep),
    (76, "underglow", "Underglow", underglow),
    (77, "monoline", "Monoline", monoline),
    (78, "twin-horizons", "Twin Horizons", twin_horizons),
    (83, "eclipse", "Eclipse", eclipse),
    (84, "corner-signal", "Corner Signal", corner_signal),
]


def _contact_sheet(images: list[tuple[int, str, Image.Image]]) -> None:
    cell_w, art_h, label_h = 768, 432, 58
    rows = math.ceil(len(images) / 2)
    sheet = Image.new("RGB", (cell_w * 2, (art_h + label_h) * rows), (8, 11, 16))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=22)
    for idx, (num, title, im) in enumerate(images):
        x = (idx % 2) * cell_w
        y = (idx // 2) * (art_h + label_h)
        sheet.paste(im.resize((cell_w, art_h), Image.Resampling.LANCZOS), (x, y))
        draw.text((x + 22, y + art_h + 16), f"{num}  {title}", fill=(210, 226, 232), font=font)
    CONTACT_SHEET.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(CONTACT_SHEET, "PNG", optimize=True)


def main() -> int:
    rendered: list[tuple[int, str, Image.Image]] = []
    total = 0
    for num, slug, title, fn in WALLS:
        im = fn()
        black = true_black_fraction(im)
        if black < MIN_TRUE_BLACK:
            raise AssertionError(
                f"{num} {title}: {black:.1%} true black is below {MIN_TRUE_BLACK:.0%}")
        stem = f"corebuilds-{num}-{slug}"
        total += save_wall(im, SERIES, stem)
        print(f"    exact #000000 {black:.1%}")
        rendered.append((num, title, im))
    _contact_sheet(rendered)
    print(f"\nseries-8-amoled complete — {len(WALLS)} walls at 4K, {total / 1e6:.2f} MB.")
    print(f"  contact sheet: {CONTACT_SHEET.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
