#!/usr/bin/env python3
"""Generate Pixel Neon’s original 8-bit wallpaper collection.

The icon pack and the wallpaper pack share a palette language, not artwork. This
renderer works on a 240x135 logical canvas, uses a small indexed palette, then
upscales with nearest-neighbour to 3840x2160. Every scene is made from integer
pixel primitives and deterministic seeds, with an original game-character
render in the foreground; no Core Builds wallpaper source is read or recoloured.

Run from the repository root:

    python tools/build_pixel_neon_wallpapers.py

It writes the 70 full-resolution PNG sources, 70 lightweight JPEG thumbnails,
and the manifest consumed by the Pixel Neon Android module. The APK bundles
only the thumbs and manifest; the app downloads the PNG source on demand.
"""
from __future__ import annotations

import hashlib
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "PixelNeonWallpapers"
THUMBS = OUT / "thumbs"
WIDTH, HEIGHT = 240, 135
SCALE = 16
FULL_SIZE = (WIDTH * SCALE, HEIGHT * SCALE)
THUMB_SIZE = (320, 180)

# Indexed colours keep the collection visibly 8-bit: dark navy ground, one
# bright primary, one counter-accent, and a few deliberate shadow/highlight
# steps. Each scene uses a subset rather than a photographic gradient.
PALETTES = {
    "arcade-grid": [
        "#070916", "#101438", "#20205A", "#4A216E", "#FF3CBF", "#FF7A3D",
        "#FFE45E", "#00E5FF", "#65F7FF", "#9B6CFF", "#18275A", "#0B1028",
        "#D622A7", "#6C2B9B", "#C8FFFF", "#141A42",
    ],
    "cyber-night": [
        "#050812", "#0B1026", "#102A3B", "#164A55", "#00E5FF", "#52FF9A",
        "#C9FF75", "#FF39D7", "#9B6CFF", "#E9FFFF", "#172B48", "#081321",
        "#008DAD", "#287F78", "#7A4EC4", "#24345A",
    ],
    "space-run": [
        "#050611", "#10102A", "#1C1640", "#30205A", "#00E5FF", "#FF39D7",
        "#FF8A4C", "#FFE45E", "#E9FFFF", "#9B6CFF", "#27356B", "#111A38",
        "#7D2A8A", "#C72C83", "#277EAA", "#48518C",
    ],
    "neon-nature": [
        "#061016", "#0B2530", "#11464A", "#17615B", "#52FF9A", "#C9FF75",
        "#00E5FF", "#FFE45E", "#FF6F91", "#9B6CFF", "#143139", "#0B1822",
        "#218B73", "#42C98B", "#B8FFFF", "#293F4A",
    ],
    "boss-stage": [
        "#080611", "#170D2C", "#2B1552", "#421C63", "#FF39D7", "#9B6CFF",
        "#00E5FF", "#FF6B4A", "#FFE45E", "#E9FFFF", "#24173F", "#0D1025",
        "#A52A93", "#5E3BB5", "#168DAA", "#3A275F",
    ],
}

SERIES = [
    ("series-1-arcade-grid", "arcade-grid", [
        "Raster Rise", "Sunset Circuit", "Vector Boulevard", "Night Drive",
        "Grid Runner", "Vapor Skyline", "Pixel Highway", "Neon Overpass",
        "Afterimage City", "Quarter Turn", "Lumen District", "Checkpoint",
        "Coin Op Horizon", "Final Lap",
    ]),
    ("series-2-cyber-night", "cyber-night", [
        "Signal Rain", "Blue Terminal", "Circuit Bloom", "Midnight Relay",
        "Data Alley", "Ghost Protocol", "Neon Switchboard", "Packet Storm",
        "Chrome Relay", "Static Garden", "Byte Cathedral", "Pulse Array",
        "Deep Link", "Firewall Dawn",
    ]),
    ("series-3-space-run", "space-run", [
        "Star Hopper", "Orbit Breaker", "Moonbase Zero", "Comet Lane",
        "Void Courier", "Astro Arcade", "Red Planet Run", "Ring Station",
        "Cosmic Drift", "Nebula Sprint", "Gravity Well", "Sunflare",
        "Meteor Bloom", "Last Launch",
    ]),
    ("series-4-neon-nature", "neon-nature", [
        "Electric Pines", "Glow Valley", "Luminous Tide", "Moonlit Ridge",
        "Crystal Grove", "Aurora Steps", "Laser Waterfall", "Cyan Meadow",
        "Ember Canopy", "Signal River", "Night Orchard", "Violet Peaks",
        "Prism Rain", "Wild Frequency",
    ]),
    ("series-5-boss-stage", "boss-stage", [
        "Portal Keep", "Arcade Colossus", "Boss Chamber", "Final Gate",
        "Shield Core", "Phantom Arena", "Power Shrine", "Overdrive",
        "Star Fortress", "Rival Signal", "Titan Room", "Critical Hit",
        "Last Continue", "Victory Screen",
    ]),
]


def rgb(hex_colour: str) -> tuple[int, int, int]:
    value = hex_colour.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def canvas(palette: list[str]) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("P", (WIDTH, HEIGHT), 0)
    flat: list[int] = []
    for colour in palette:
        flat.extend(rgb(colour))
    flat.extend([0] * (768 - len(flat)))
    image.putpalette(flat)
    return image, ImageDraw.Draw(image)


def rect(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], fill: int) -> None:
    draw.rectangle(tuple(int(v) for v in box), fill=fill)


def line(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]], fill: int, width: int = 1) -> None:
    draw.line([(int(x), int(y)) for x, y in points], fill=fill, width=width)


def poly(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]], fill: int) -> None:
    draw.polygon([(int(x), int(y)) for x, y in points], fill=fill)


def stepped_circle(draw: ImageDraw.ImageDraw, cx: int, cy: int, radius: int, fill: int) -> None:
    """A deliberately square-edged disk rather than a smooth ellipse."""
    rows = [max(1, radius // 2), radius - 2, radius, radius, radius - 2, max(1, radius // 2)]
    for offset, half in enumerate(rows, start=-3):
        y = cy + offset
        rect(draw, (cx - half, y, cx + half, y), fill)
        if half > 2:
            rect(draw, (cx - half + 1, y, cx + half - 1, y), fill)
    for y in range(cy - radius + 3, cy + radius - 2):
        half = radius if abs(y - cy) < radius - 3 else radius - 2
        rect(draw, (cx - half, y, cx + half, y), fill)


def starfield(draw: ImageDraw.ImageDraw, rng: random.Random, colour: int, count: int = 45) -> None:
    for _ in range(count):
        x = rng.randrange(4, WIDTH - 4)
        y = rng.randrange(4, HEIGHT - 26)
        if rng.random() < 0.18:
            rect(draw, (x, y, x + 1, y), colour)
        else:
            rect(draw, (x, y, x, y), colour)


def banded_sky(draw: ImageDraw.ImageDraw, palette: list[str], top: int, bottom: int, steps: list[int]) -> None:
    span = max(1, bottom - top)
    for y in range(top, bottom + 1):
        fraction = (y - top) / span
        index = steps[min(len(steps) - 1, int(fraction * len(steps)))]
        rect(draw, (0, y, WIDTH - 1, y), index)


def scanline_dither(draw: ImageDraw.ImageDraw, rng: random.Random, colour: int, y0: int, y1: int) -> None:
    for y in range(y0, y1, 4):
        start = rng.randrange(0, 4)
        for x in range(start, WIDTH, 5):
            if rng.random() > 0.28:
                rect(draw, (x, y, x + rng.choice([0, 1]), y), colour)


def perspective_grid(draw: ImageDraw.ImageDraw, horizon: int, primary: int, secondary: int, rng: random.Random) -> None:
    # Horizontal runs are hand-stepped, with spacing opening toward the viewer.
    y = horizon + 4
    gap = 4
    while y < HEIGHT:
        line(draw, [(0, y), (WIDTH - 1, y)], secondary if y % 2 else primary)
        y += gap
        gap = min(15, gap + 1 + (y + rng.randrange(3)) % 2)
    van_x = rng.choice([WIDTH // 2 - 16, WIDTH // 2, WIDTH // 2 + 16])
    for x in range(-WIDTH, WIDTH * 2, 18):
        line(draw, [(van_x, horizon), (x, HEIGHT - 1)], primary)


def block_mountains(draw: ImageDraw.ImageDraw, rng: random.Random, base: int, shadow: int, highlight: int) -> None:
    points = [(0, base)]
    x = 0
    while x < WIDTH:
        x += rng.randrange(8, 20)
        y = rng.randrange(base - 32, base - 10)
        points.extend([(x - rng.randrange(4, 9), y), (x, base)])
    poly(draw, points + [(WIDTH - 1, HEIGHT - 1), (0, HEIGHT - 1)], shadow)
    for _ in range(8):
        x = rng.randrange(0, WIDTH)
        y = rng.randrange(base - 28, base - 5)
        line(draw, [(x, y), (x + rng.randrange(-8, 9), y + rng.randrange(4, 12))], highlight)


def circuit_trace(draw: ImageDraw.ImageDraw, rng: random.Random, colour: int, start_side: int) -> None:
    if start_side == 0:
        x, y = 0, rng.randrange(15, HEIGHT - 20)
        points = [(x, y)]
        for _ in range(rng.randrange(3, 6)):
            x += rng.randrange(7, 22)
            points.append((x, y))
            y += rng.choice([-1, 1]) * rng.randrange(4, 16)
            points.append((x, y))
    else:
        x, y = rng.randrange(20, WIDTH - 20), 0
        points = [(x, y)]
        for _ in range(rng.randrange(3, 6)):
            y += rng.randrange(7, 22)
            points.append((x, y))
            x += rng.choice([-1, 1]) * rng.randrange(5, 20)
            points.append((x, y))
    line(draw, points, colour)
    for px, py in points[1::2]:
        rect(draw, (px - 1, py - 1, px + 1, py + 1), colour)


def pixel_ship(draw: ImageDraw.ImageDraw, x: int, y: int, colour: int, glow: int, flip: bool) -> None:
    direction = -1 if flip else 1
    poly(draw, [(x, y), (x + 17 * direction, y), (x + 23 * direction, y + 4),
                (x + 12 * direction, y + 5), (x + 8 * direction, y + 9),
                (x - 3 * direction, y + 9), (x + 2 * direction, y + 4)], colour)
    rect(draw, (min(x + 5 * direction, x + 11 * direction), y + 2,
                max(x + 5 * direction, x + 11 * direction), y + 3), glow)
    rect(draw, (min(x - 7 * direction, x - direction), y + 6,
                max(x - 7 * direction, x - direction), y + 7), glow)


def avatar(
    draw: ImageDraw.ImageDraw,
    rng: random.Random,
    kind: int,
    cx: int,
    ground: int,
    pose: int,
    primary: int,
    accent: int,
    light: int,
    shadow: int,
) -> None:
    """Draw an original game-like character render on the logical pixel grid.

    The character is built from silhouette-first clusters rather than a traced
    game asset. Each archetype has one readable signature: helmet, hood, staff,
    shield, visor, antenna, bow, dagger, or horns. That keeps the foreground
    legible at TV distance while the pose/placement/colour seed makes each
    wallpaper a different scene.
    """
    x = cx - 15
    bob = (pose % 3) - 1
    top = ground - 52 + bob
    lean = -2 if pose % 4 == 1 else 2 if pose % 4 == 2 else 0
    # Feet and the two-pixel ground contact establish a strong silhouette.
    rect(draw, (x + 7 + lean, ground - 7, x + 13 + lean, ground + 1), shadow)
    rect(draw, (x + 20 + lean, ground - 7, x + 26 + lean, ground + 1), shadow)
    rect(draw, (x + 8 + lean, ground - 8, x + 12 + lean, ground - 1), primary)
    rect(draw, (x + 21 + lean, ground - 8, x + 25 + lean, ground - 1), primary)
    if pose % 2:
        rect(draw, (x + 5 + lean, ground - 2, x + 13 + lean, ground + 1), accent)

    # Cloak/coat silhouette and interior light-facing plane.
    poly(draw, [(x + 7, top + 22), (x + 23, top + 20), (x + 30, ground - 10),
                (x + 2, ground - 10)], shadow)
    if kind in {1, 5, 6, 8}:
        poly(draw, [(x + 4, top + 23), (x + 13, top + 20), (x + 12, ground - 9),
                    (x + 1, ground - 11)], primary)
    rect(draw, (x + 12 + lean, top + 23, x + 22 + lean, ground - 10), primary)
    rect(draw, (x + 14 + lean, top + 25, x + 20 + lean, ground - 12), light)
    rect(draw, (x + 13 + lean, top + 33, x + 21 + lean, top + 36), shadow)
    rect(draw, (x + 16 + lean, top + 29, x + 18 + lean, top + 32), accent)

    # Arms use stepped blocks; the alternating pose changes the gesture.
    if pose % 2 == 0:
        rect(draw, (x + 2, top + 25, x + 9, top + 31), primary)
        rect(draw, (x + 1, top + 30, x + 6, top + 34), shadow)
        rect(draw, (x + 23, top + 24, x + 30, top + 29), primary)
        rect(draw, (x + 29, top + 28, x + 34, top + 31), accent)
    else:
        rect(draw, (x + 2, top + 23, x + 8, top + 28), accent)
        rect(draw, (x + 0, top + 27, x + 5, top + 32), primary)
        rect(draw, (x + 24, top + 25, x + 31, top + 33), primary)
        rect(draw, (x + 29, top + 31, x + 33, top + 34), light)

    # Head, hair/helmet and the two-pixel face read.
    if kind == 2:  # mage hood and broad hat
        poly(draw, [(x + 4, top + 13), (x + 10, top + 5), (x + 22, top + 5),
                    (x + 28, top + 13)], shadow)
        rect(draw, (x + 2, top + 11, x + 30, top + 15), primary)
        rect(draw, (x + 8, top + 13, x + 24, top + 24), shadow)
        rect(draw, (x + 11, top + 15, x + 21, top + 24), primary)
    elif kind == 3:  # pilot visor
        stepped = [(x + 8, top + 6), (x + 22, top + 6), (x + 27, top + 11),
                   (x + 25, top + 23), (x + 7, top + 23), (x + 4, top + 15)]
        poly(draw, stepped, shadow)
        poly(draw, [(x + 9, top + 8), (x + 21, top + 8), (x + 24, top + 12),
                    (x + 22, top + 20), (x + 9, top + 20), (x + 7, top + 14)], primary)
        rect(draw, (x + 9, top + 12, x + 23, top + 16), accent)
        rect(draw, (x + 11, top + 12, x + 15, top + 13), light)
    elif kind == 4:  # android square head and antenna
        rect(draw, (x + 6, top + 6, x + 26, top + 23), shadow)
        rect(draw, (x + 8, top + 8, x + 24, top + 21), primary)
        rect(draw, (x + 11, top + 14, x + 13, top + 16), light)
        rect(draw, (x + 19, top + 14, x + 21, top + 16), light)
        line(draw, [(x + 16, top + 6), (x + 16, top + 1)], accent)
        rect(draw, (x + 15, top, x + 17, top + 2), light)
    elif kind == 7:  # alien ears and a tail-like silhouette
        poly(draw, [(x + 7, top + 14), (x + 2, top + 6), (x + 11, top + 10),
                    (x + 21, top + 9), (x + 29, top + 5), (x + 25, top + 16),
                    (x + 24, top + 23), (x + 8, top + 23)], shadow)
        rect(draw, (x + 9, top + 11, x + 24, top + 22), primary)
        rect(draw, (x + 12, top + 15, x + 14, top + 17), light)
        rect(draw, (x + 19, top + 15, x + 21, top + 17), light)
    else:  # runner, knight, ranger, rogue, and boss helmets/hoods
        poly(draw, [(x + 7, top + 12), (x + 11, top + 5), (x + 22, top + 5),
                    (x + 27, top + 12), (x + 25, top + 24), (x + 8, top + 24)], shadow)
        rect(draw, (x + 9, top + 10, x + 24, top + 22), primary)
        if kind == 1:  # knight crest
            poly(draw, [(x + 13, top + 7), (x + 16, top + 1), (x + 19, top + 7)], accent)
            rect(draw, (x + 9, top + 14, x + 24, top + 17), shadow)
        elif kind in {5, 6}:
            rect(draw, (x + 6, top + 10, x + 26, top + 14), shadow)
            rect(draw, (x + 12, top + 15, x + 14, top + 17), light)
            rect(draw, (x + 20, top + 15, x + 22, top + 17), light)
        else:
            rect(draw, (x + 12, top + 15, x + 14, top + 17), light)
            rect(draw, (x + 20, top + 15, x + 22, top + 17), accent)

    # Class prop: one iconographic object per character, all original shapes.
    if kind == 0:  # runner blaster + speed trail
        rect(draw, (x + 28, top + 24, x + 39, top + 28), shadow)
        rect(draw, (x + 30, top + 23, x + 38, top + 25), accent)
        rect(draw, (x + 38, top + 24, x + 42, top + 25), light)
        line(draw, [(x - 7, ground - 16), (x - 1, ground - 16)], accent)
        rect(draw, (x - 11, ground - 12, x - 4, ground - 11), primary)
    elif kind == 1:  # shield and sword
        poly(draw, [(x - 7, top + 26), (x + 1, top + 24), (x + 4, top + 34),
                    (x - 2, top + 40), (x - 8, top + 34)], shadow)
        poly(draw, [(x - 5, top + 27), (x + 0, top + 26), (x + 2, top + 33),
                    (x - 2, top + 37), (x - 6, top + 33)], accent)
        line(draw, [(x + 26, top + 31), (x + 39, top + 17)], light, 2)
        rect(draw, (x + 24, top + 31, x + 31, top + 33), accent)
    elif kind == 2:  # staff and spell orb
        line(draw, [(x + 33, top + 9), (x + 33, ground - 2)], shadow, 2)
        line(draw, [(x + 30, top + 12), (x + 36, top + 12)], accent)
        stepped_circle(draw, x + 33, top + 7, 5, accent)
        rect(draw, (x + 32, top + 5, x + 34, top + 7), light)
    elif kind == 3:  # jetpack exhaust
        rect(draw, (x + 3, top + 25, x + 6, ground - 11), shadow)
        rect(draw, (x + 25, top + 25, x + 28, ground - 11), shadow)
        poly(draw, [(x + 3, ground - 10), (x + 7, ground - 10), (x + 5, ground - 1)], accent)
        poly(draw, [(x + 25, ground - 10), (x + 29, ground - 10), (x + 27, ground - 1)], light)
    elif kind == 4:  # android chest port and cable
        rect(draw, (x + 15, top + 28, x + 19, top + 34), light)
        line(draw, [(x + 19, top + 31), (x + 29, top + 39), (x + 34, top + 37)], accent)
        rect(draw, (x + 32, top + 36, x + 35, top + 39), light)
    elif kind == 5:  # ranger bow and arrow
        line(draw, [(x - 4, top + 22), (x - 9, top + 31), (x - 4, top + 40)], accent)
        line(draw, [(x - 4, top + 22), (x - 4, top + 40)], light)
        line(draw, [(x - 4, top + 31), (x + 11, top + 31)], primary)
        rect(draw, (x + 10, top + 30, x + 14, top + 32), light)
    elif kind == 6:  # rogue twin daggers
        line(draw, [(x - 1, top + 31), (x - 8, top + 39)], light, 2)
        line(draw, [(x + 28, top + 31), (x + 35, top + 39)], accent, 2)
        rect(draw, (x - 4, top + 30, x + 2, top + 32), shadow)
        rect(draw, (x + 25, top + 30, x + 31, top + 32), shadow)
    elif kind == 7:  # alien tail and antenna glow
        line(draw, [(x + 24, ground - 14), (x + 34, ground - 10),
                    (x + 38, ground - 16), (x + 43, ground - 14)], accent, 2)
        line(draw, [(x + 13, top + 7), (x + 10, top + 1)], light)
        rect(draw, (x + 9, top, x + 11, top + 2), accent)
    else:  # boss horns and core
        poly(draw, [(x + 8, top + 9), (x + 3, top + 1), (x + 12, top + 5)], accent)
        poly(draw, [(x + 22, top + 5), (x + 30, top + 1), (x + 26, top + 11)], accent)
        rect(draw, (x + 13, top + 28, x + 21, top + 35), light)
        rect(draw, (x + 15, top + 30, x + 19, top + 33), accent)


def boss_render(draw: ImageDraw.ImageDraw, rng: random.Random, cx: int, ground: int, pose: int, primary: int, accent: int, light: int, shadow: int) -> None:
    """Large original boss silhouette for the arena series."""
    x = cx - 29
    top = ground - 67 + (pose % 3)
    # Broad wing/shoulder silhouette first.
    poly(draw, [(x + 19, top + 12), (x + 5, top + 20), (x, top + 42),
                (x + 13, top + 38), (x + 9, ground - 5), (x + 49, ground - 5),
                (x + 45, top + 38), (x + 58, top + 42), (x + 53, top + 20),
                (x + 39, top + 12)], shadow)
    poly(draw, [(x + 21, top + 17), (x + 9, top + 23), (x + 7, top + 35),
                (x + 17, top + 33), (x + 15, ground - 8), (x + 43, ground - 8),
                (x + 41, top + 33), (x + 51, top + 35), (x + 49, top + 23),
                (x + 37, top + 17)], primary)
    # Head/face and horns.
    poly(draw, [(x + 16, top + 16), (x + 22, top + 5), (x + 30, top + 12),
                (x + 38, top + 5), (x + 44, top + 16), (x + 40, top + 30),
                (x + 20, top + 30)], shadow)
    rect(draw, (x + 21, top + 16, x + 39, top + 27), accent)
    rect(draw, (x + 24, top + 19, x + 27, top + 21), light)
    rect(draw, (x + 33, top + 19, x + 36, top + 21), light)
    rect(draw, (x + 26, top + 25, x + 34, top + 27), shadow)
    # Energy claws/weapon and a ground contact glow.
    for side in (-1, 1):
        line(draw, [(cx + side * 22, top + 34), (cx + side * 34, top + 48)], accent, 2)
        rect(draw, (cx + side * 36 - 1, top + 48, cx + side * 36 + 1, top + 52), light)
    rect(draw, (x + 12, ground - 4, x + 46, ground - 1), light)
    for xx in range(x + 16, x + 45, 7):
        rect(draw, (xx, ground - 9 - ((xx + pose) % 4), xx + 2, ground - 6), accent)


def central_emblem(draw: ImageDraw.ImageDraw, rng: random.Random, primary: int, accent: int, light: int, mode: int) -> None:
    cx, cy = WIDTH // 2 + rng.randrange(-8, 9), rng.randrange(43, 70)
    if mode == 0:
        poly(draw, [(cx, cy - 22), (cx + 20, cy - 9), (cx + 15, cy + 16),
                    (cx, cy + 26), (cx - 15, cy + 16), (cx - 20, cy - 9)], primary)
        poly(draw, [(cx, cy - 13), (cx + 11, cy - 5), (cx + 8, cy + 10),
                    (cx, cy + 16), (cx - 8, cy + 10), (cx - 11, cy - 5)], 0)
        rect(draw, (cx - 3, cy - 7, cx + 3, cy + 8), accent)
        rect(draw, (cx - 8, cy - 2, cx + 8, cy + 3), accent)
    elif mode == 1:
        poly(draw, [(cx, cy - 25), (cx + 23, cy), (cx, cy + 25), (cx - 23, cy)], primary)
        poly(draw, [(cx, cy - 16), (cx + 14, cy), (cx, cy + 16), (cx - 14, cy)], 0)
        line(draw, [(cx - 11, cy), (cx, cy - 10), (cx + 11, cy)], accent)
        line(draw, [(cx - 11, cy), (cx, cy + 10), (cx + 11, cy)], accent)
        rect(draw, (cx - 2, cy - 2, cx + 2, cy + 2), light)
    else:
        rect(draw, (cx - 20, cy - 14, cx + 20, cy + 14), primary)
        rect(draw, (cx - 14, cy - 8, cx + 14, cy + 8), 0)
        for xx in range(cx - 10, cx + 11, 5):
            rect(draw, (xx, cy - 4, xx + 2, cy + 4), accent)
        rect(draw, (cx - 4, cy - 18, cx + 4, cy + 18), light)


def scene_arcade(draw: ImageDraw.ImageDraw, palette: list[str], rng: random.Random, variant: int) -> None:
    banded_sky(draw, palette, 0, 92, [1, 1, 2, 2, 3, 3])
    starfield(draw, rng, 8, 18)
    horizon = rng.randrange(76, 92)
    sun_x = rng.randrange(42, 198)
    stepped_circle(draw, sun_x, horizon - rng.randrange(17, 29), rng.randrange(12, 21), 6 if variant % 2 else 4)
    # Sun stripes are square gaps, not a smooth gradient.
    for yy in range(horizon - 8, horizon, 4):
        rect(draw, (sun_x - 13, yy, sun_x + 13, yy + 1), 3)
    perspective_grid(draw, horizon, 7 if variant % 3 else 4, 9, rng)
    for _ in range(7 + variant % 4):
        x = rng.randrange(0, WIDTH - 12)
        h = rng.randrange(8, 31)
        rect(draw, (x, horizon - h, x + rng.randrange(5, 14), horizon), rng.choice([1, 2, 10]))
        for wy in range(horizon - h + 4, horizon - 2, 6):
            if rng.random() > 0.25:
                rect(draw, (x + 2, wy, x + 3, wy + 1), rng.choice([5, 7, 8]))
    if variant % 3 == 0:
        line(draw, [(18, horizon - 23), (18, horizon - 37), (40, horizon - 37)], 8)
        rect(draw, (38, horizon - 39, 42, horizon - 35), 8)
    elif variant % 3 == 1:
        poly(draw, [(182, horizon - 20), (195, horizon - 35), (208, horizon - 20)], 7)
        line(draw, [(195, horizon - 35), (195, horizon - 10)], 8)
    else:
        for xx in range(20, WIDTH - 20, 18):
            rect(draw, (xx, horizon - 3, xx + 7, horizon - 2), 6)
    avatar(draw, rng, variant % 8, 120 + rng.randrange(-28, 29), 124, variant,
           4 if variant % 2 else 5, 7, 14, 2)


def scene_cyber(draw: ImageDraw.ImageDraw, palette: list[str], rng: random.Random, variant: int) -> None:
    banded_sky(draw, palette, 0, HEIGHT, [1, 1, 2, 2, 10, 10, 11])
    scanline_dither(draw, rng, 3, 8, 124)
    for _ in range(13):
        x = rng.randrange(4, WIDTH - 12)
        y = rng.randrange(60, 120)
        w = rng.randrange(5, 19)
        h = rng.randrange(8, 38)
        rect(draw, (x, y - h, x + w, y), rng.choice([2, 3, 10]))
        if rng.random() > 0.35:
            rect(draw, (x + 2, y - h + 3, x + w - 2, y - h + 4), rng.choice([4, 7]))
    for i in range(9):
        circuit_trace(draw, rng, 4 if i % 2 else 7, i % 2)
    central_emblem(draw, rng, 4 if variant % 2 else 7, 7 if variant % 2 else 4, 9, variant % 3)
    if variant % 2:
        for x in range(24, 216, 24):
            rect(draw, (x, 115, x + 8, 117), 8)
    else:
        line(draw, [(0, 118), (WIDTH - 1, 118)], 5)
        for x in range(8, WIDTH, 17):
            rect(draw, (x, 116, x + 2, 120), 5)
    avatar(draw, rng, (variant + 2) % 9, 120 + rng.randrange(-30, 31), 122, variant + 1,
           4 if variant % 2 == 0 else 7, 5 if variant % 2 == 0 else 4, 9, 2)


def scene_space(draw: ImageDraw.ImageDraw, palette: list[str], rng: random.Random, variant: int) -> None:
    banded_sky(draw, palette, 0, HEIGHT, [1, 1, 2, 2, 3, 3, 11])
    starfield(draw, rng, 8, 70)
    for _ in range(5):
        x, y = rng.randrange(16, WIDTH - 16), rng.randrange(15, 102)
        rect(draw, (x, y, x + rng.randrange(4, 18), y + rng.randrange(2, 5)), rng.choice([3, 4, 9]))
    planet_x = rng.randrange(35, 205)
    planet_y = rng.randrange(38, 82)
    radius = rng.randrange(10, 20)
    stepped_circle(draw, planet_x, planet_y, radius, 4 if variant % 2 else 6)
    for yy in range(planet_y - radius + 4, planet_y + radius, 5):
        rect(draw, (planet_x - radius + 4, yy, planet_x + radius - 4, yy + 1), 3 if variant % 2 else 12)
    if variant % 3 == 0:
        line(draw, [(planet_x - radius - 11, planet_y + 3), (planet_x + radius + 11, planet_y - 3)], 7)
        line(draw, [(planet_x - radius - 8, planet_y + 7), (planet_x + radius + 8, planet_y + 1)], 9)
    pixel_ship(draw, rng.randrange(35, 180), rng.randrange(92, 112), 7 if variant % 2 else 4, 8, variant % 2 == 0)
    if variant % 4 == 1:
        poly(draw, [(207, 24), (215, 31), (211, 41), (201, 34)], 8)
        rect(draw, (205, 30, 209, 33), 5)
    elif variant % 4 == 2:
        line(draw, [(26, 18), (26, 40), (39, 40)], 5)
        rect(draw, (23, 16, 29, 20), 8)
    avatar(draw, rng, (variant + 3) % 8, 122 + rng.randrange(-34, 35), 124, variant + 2,
           4 if variant % 2 else 6, 5 if variant % 2 else 7, 8, 2)


def scene_nature(draw: ImageDraw.ImageDraw, palette: list[str], rng: random.Random, variant: int) -> None:
    banded_sky(draw, palette, 0, 88, [1, 1, 2, 2, 3, 3])
    sun_x = rng.randrange(34, 205)
    sun_y = rng.randrange(24, 54)
    stepped_circle(draw, sun_x, sun_y, rng.randrange(9, 17), 7 if variant % 2 else 5)
    block_mountains(draw, rng, rng.randrange(77, 91), 2 if variant % 3 else 3, 4 if variant % 2 else 6)
    water_y = rng.randrange(91, 104)
    rect(draw, (0, water_y, WIDTH - 1, HEIGHT - 1), 1 if variant % 2 else 2)
    for _ in range(25):
        x = rng.randrange(0, WIDTH - 10)
        y = rng.randrange(water_y + 4, HEIGHT - 5)
        line(draw, [(x, y), (x + rng.randrange(3, 15), y)], rng.choice([4, 7, 8]))
    for _ in range(8):
        x = rng.randrange(0, WIDTH)
        base = rng.randrange(101, 129)
        height = rng.randrange(10, 31)
        poly(draw, [(x, base - height), (x - rng.randrange(4, 9), base),
                    (x + rng.randrange(4, 9), base)], rng.choice([4, 5, 9]))
        rect(draw, (x - 1, base - height + 6, x + 1, base - height + 8), 8)
    if variant % 3 == 0:
        for x in range(29, WIDTH, 42):
            poly(draw, [(x, 78), (x - 8, 97), (x + 8, 97)], 7)
            rect(draw, (x - 2, 84, x + 2, 94), 9)
    elif variant % 3 == 1:
        line(draw, [(0, 63), (32, 66), (57, 61), (91, 65)], 8)
        line(draw, [(147, 62), (181, 66), (239, 60)], 8)
    else:
        for x in range(18, 225, 22):
            rect(draw, (x, rng.randrange(102, 118), x + 2, 127), 6)
            rect(draw, (x - 4, 113, x + 6, 115), 7)
    avatar(draw, rng, (variant + 4) % 8, 120 + rng.randrange(-32, 33), 125, variant + 3,
           4 if variant % 2 == 0 else 5, 6, 14, 2)


def scene_boss(draw: ImageDraw.ImageDraw, palette: list[str], rng: random.Random, variant: int) -> None:
    banded_sky(draw, palette, 0, HEIGHT, [1, 1, 2, 2, 3, 10])
    for _ in range(12):
        x = rng.randrange(0, WIDTH - 10)
        y = rng.randrange(5, 102)
        rect(draw, (x, y, x + rng.randrange(3, 13), y + rng.randrange(1, 4)), rng.choice([4, 5, 6, 7]))
    floor = rng.randrange(90, 101)
    perspective_grid(draw, floor, 4 if variant % 2 else 7, 5, rng)
    # Four chunky arena pylons frame the central object.
    for x in [18, 43, 194, 219]:
        h = rng.randrange(20, 42)
        rect(draw, (x, floor - h, x + 6, floor), 2 if x < 100 else 3)
        rect(draw, (x - 2, floor - h, x + 8, floor - h + 3), 5 if x < 100 else 7)
        rect(draw, (x + 2, floor - h + 8, x + 4, floor - 6), 8)
    central_emblem(draw, rng, 5 if variant % 2 else 7, 7 if variant % 2 else 4, 9, (variant + 1) % 3)
    cx = WIDTH // 2 + rng.randrange(-10, 11)
    cy = rng.randrange(42, 69)
    if variant % 3 == 0:
        for radius, colour in [(28, 5), (22, 7), (15, 4)]:
            line(draw, [(cx - radius, cy), (cx - radius + 5, cy - 4), (cx + radius - 5, cy - 4), (cx + radius, cy)], colour)
            line(draw, [(cx - radius, cy), (cx - radius + 5, cy + 4), (cx + radius - 5, cy + 4), (cx + radius, cy)], colour)
    elif variant % 3 == 1:
        pixel_ship(draw, cx - 8, cy + 30, 7, 8, False)
        for xx in [cx - 29, cx + 25]:
            rect(draw, (xx, cy - 5, xx + 4, cy + 5), 8)
    else:
        for yy in range(cy - 20, cy + 22, 8):
            rect(draw, (cx - 31, yy, cx + 31, yy + 2), 5 if yy % 16 else 7)
        rect(draw, (cx - 5, cy - 26, cx + 5, cy + 26), 9)
    avatar(draw, rng, (variant + 5) % 7, 56 + rng.randrange(-8, 9), 125, variant + 4,
           5 if variant % 2 else 7, 6, 9, 2)
    boss_render(draw, rng, 164 + rng.randrange(-10, 11), 125, variant,
                5 if variant % 2 else 7, 7 if variant % 2 else 4, 9, 2)
    rect(draw, (0, floor + 2, WIDTH - 1, floor + 3), 8)


def render(series_key: str, palette: list[str], variant: int, seed: int) -> Image.Image:
    image, draw = canvas(palette)
    rng = random.Random(seed)
    if series_key == "arcade-grid":
        scene_arcade(draw, palette, rng, variant)
    elif series_key == "cyber-night":
        scene_cyber(draw, palette, rng, variant)
    elif series_key == "space-run":
        scene_space(draw, palette, rng, variant)
    elif series_key == "neon-nature":
        scene_nature(draw, palette, rng, variant)
    else:
        scene_boss(draw, palette, rng, variant)
    # Keep a tiny deterministic corner mark on every frame. It is an authored
    # sprite signature, not a watermark, and prevents accidental duplicates if
    # a future scene recipe collapses two seeds to the same raster.
    x = 4 + (seed % 18)
    y = 4 + ((seed // 19) % 12)
    rect(draw, (x, y, x + 1, y + 1), 14 if variant % 2 else 8)
    return image.resize(FULL_SIZE, Image.Resampling.NEAREST)


def slug(number: int, title: str) -> str:
    return f"pixel-neon-{number:02d}-" + "-".join(title.lower().split())


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    THUMBS.mkdir(parents=True, exist_ok=True)
    expected_full: set[Path] = set()
    ordinal = 1
    for series_name, _, titles in SERIES:
        for title in titles:
            expected_full.add(Path(series_name) / f"{slug(ordinal, title)}.png")
            ordinal += 1
    # Remove stale generated files if a future collection changes its names or
    # count; the README and manifest are hand-maintained source documentation.
    for old in OUT.glob("series-*/*.png"):
        if old.relative_to(OUT) not in expected_full:
            old.unlink()
    expected_thumbs = {path.with_suffix(".jpg").name for path in expected_full}
    for old in THUMBS.glob("*.jpg"):
        if old.name not in expected_thumbs:
            old.unlink()

    entries: list[dict[str, str]] = []
    seen: set[str] = set()
    number = 1
    for series_name, palette_key, titles in SERIES:
        series_dir = OUT / series_name
        series_dir.mkdir(parents=True, exist_ok=True)
        palette = PALETTES[palette_key]
        for variant, title in enumerate(titles):
            name = slug(number, title)
            seed = int.from_bytes(hashlib.sha256(name.encode()).digest()[:8], "big")
            full = render(palette_key, palette, variant, seed)
            raster_hash = hashlib.sha256(full.tobytes()).hexdigest()
            if raster_hash in seen:
                raise SystemExit(f"duplicate Pixel Neon wallpaper raster: {name}")
            seen.add(raster_hash)
            full_path = series_dir / f"{name}.png"
            thumb_path = THUMBS / f"{name}.jpg"
            full.save(full_path, format="PNG", optimize=True)
            # 3840x2160 -> 320x180 is an exact 12x nearest-neighbour reduction,
            # so the thumbnail retains the same intentional pixel blocks.
            full.resize(THUMB_SIZE, Image.Resampling.NEAREST).convert("RGB").save(
                thumb_path, format="JPEG", quality=84, optimize=True, progressive=False
            )
            base = "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main"
            entries.append({
                "name": f"{number:02d} {title}",
                "series": series_name,
                "url": f"{base}/PixelNeonWallpapers/{series_name}/{name}.png",
                "thumb": f"{base}/PixelNeonWallpapers/thumbs/{name}.jpg",
                "resolution": "3840x2160",
            })
            number += 1

    manifest = {
        "collection": "Core Builds Pixel Neon Wallpapers",
        "version": "1.0",
        "author": "brevityA",
        "brand_guide": "Core Builds Pixel Neon 8-bit Art Direction v1.0",
        "count": len(entries),
        "wallpapers": entries,
    }
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Pixel Neon wallpapers complete — {len(entries)} unique 8-bit scenes, {len(seen)} unique rasters.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
