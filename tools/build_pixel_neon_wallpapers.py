#!/usr/bin/env python3
"""Generate Pixel Neon’s original environment-led 8-bit wallpaper collection.

The icon pack and the wallpaper pack share a palette language, not artwork. This
renderer builds each scene on a 320x180 logical canvas, then upscales with
nearest-neighbour to 3840x2160. Every frame is made from integer pixel clusters,
layered depth, deliberate negative space, and deterministic seeds; no Core
Builds wallpaper source is read or recoloured.

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
WIDTH, HEIGHT = 320, 180
SCALE = 12
FULL_SIZE = (WIDTH * SCALE, HEIGHT * SCALE)
THUMB_SIZE = (320, 180)

# Each palette is a small set of value ramps. Index 0 is the deepest outline,
# indices 1–3 are atmospheric distance, 4–9 are localized neon/light colours,
# and 10–15 are foreground, shadow, and highlight steps. The renderer uses the
# same role order in every series so the collection feels designed rather than
# like 70 unrelated random images.
PALETTES = {
    "arcade-grid": [
        "#070A1B", "#111638", "#25205A", "#3C2566", "#D832A8", "#F064B7",
        "#FF8B55", "#FFC857", "#27D6E8", "#72F3E4", "#7160C9", "#18264B",
        "#0B1027", "#AE277F", "#C2FFF3", "#283767",
    ],
    "cyber-night": [
        "#040A13", "#091525", "#10283B", "#1B4150", "#008DA8", "#09C9D8",
        "#FF3BA8", "#FF6A76", "#99FFB2", "#D2FFE4", "#255373", "#0B1C2B",
        "#06101B", "#7F2995", "#C5FAFF", "#2E5870",
    ],
    "space-run": [
        "#050711", "#0D1025", "#17183B", "#292254", "#B92C9F", "#F044B7",
        "#FF8055", "#FFD463", "#1ACFE7", "#D8FBFF", "#6E5FCC", "#171D3C",
        "#090C1E", "#72276F", "#EDF2FF", "#3B4B86",
    ],
    "neon-nature": [
        "#061318", "#0B2830", "#12484A", "#23655A", "#1DAD81", "#54E49B",
        "#B7F36A", "#F8DC68", "#2BC8DE", "#D2FFCE", "#367A68", "#0D252A",
        "#07161D", "#467C55", "#D9FFF1", "#437E7E",
    ],
    "boss-stage": [
        "#070711", "#100D24", "#221343", "#3A1B5D", "#D52AAE", "#984FDE",
        "#1CBFDB", "#FF6A55", "#FFD05A", "#E9F8FF", "#5A3D8D", "#120D27",
        "#090917", "#8A247C", "#F2E6FF", "#453162",
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


def pixel_disc(draw: ImageDraw.ImageDraw, cx: int, cy: int, radius: int, fill: int) -> None:
    """Draw a stepped disk with intentional horizontal pixel bands."""
    radius = max(2, radius)
    for dy in range(-radius, radius + 1):
        distance = abs(dy)
        if distance >= radius - 1:
            half = max(1, radius // 3)
        elif distance >= radius - 4:
            half = max(2, radius // 2)
        elif distance >= radius // 2:
            half = max(3, (radius * 3) // 4)
        else:
            half = radius
        rect(draw, (cx - half, cy + dy, cx + half, cy + dy), fill)


def layered_disc(
    draw: ImageDraw.ImageDraw,
    cx: int,
    cy: int,
    radius: int,
    outer: int,
    glow: int,
    core: int,
) -> None:
    pixel_disc(draw, cx, cy, radius + 7, outer)
    pixel_disc(draw, cx, cy, radius + 4, glow)
    pixel_disc(draw, cx, cy, radius, core)


def banded_sky(draw: ImageDraw.ImageDraw, bands: list[tuple[int, int]]) -> None:
    start = 0
    for end, colour in bands:
        end = min(HEIGHT - 1, end)
        if end >= start:
            rect(draw, (0, start, WIDTH - 1, end), colour)
        start = end + 1
        if start >= HEIGHT:
            break
    if start < HEIGHT:
        rect(draw, (0, start, WIDTH - 1, HEIGHT - 1), bands[-1][1])


def dither_transition(
    draw: ImageDraw.ImageDraw,
    rng: random.Random,
    y0: int,
    y1: int,
    colour: int,
    density: float = 0.38,
) -> None:
    """Add sparse, horizontal pixel clusters only where two sky bands meet."""
    for y in range(y0, min(y1, HEIGHT), 2):
        offset = rng.randrange(0, 5)
        for x in range(offset, WIDTH, 5):
            if rng.random() < density:
                rect(draw, (x, y, x + rng.choice([0, 1]), y), colour)


def starfield(
    draw: ImageDraw.ImageDraw,
    rng: random.Random,
    colours: tuple[int, ...],
    count: int = 40,
    y0: int = 5,
    y1: int = 105,
) -> None:
    for _ in range(count):
        x = rng.randrange(5, WIDTH - 5)
        y = rng.randrange(y0, min(y1, HEIGHT - 1))
        colour = rng.choice(colours)
        if rng.random() < 0.13:
            rect(draw, (x - 1, y, x + 1, y), colour)
            rect(draw, (x, y - 1, x, y + 1), colour)
        else:
            rect(draw, (x, y, x + rng.choice([0, 1]), y), colour)


def pixel_cloud(draw: ImageDraw.ImageDraw, x: int, y: int, width: int, height: int, fill: int) -> None:
    points = [
        (x, y + height), (x + 3, y + height - 6), (x + width // 5, y + height - 6),
        (x + width // 3, y + 2), (x + width // 2, y + 5),
        (x + (width * 3) // 5, y), (x + (width * 4) // 5, y + 6),
        (x + width, y + 8), (x + width - 3, y + height),
    ]
    poly(draw, points, fill)


def mountain_ridge(
    draw: ImageDraw.ImageDraw,
    rng: random.Random,
    base: int,
    colour: int,
    highlight: int | None = None,
    peaks: int = 13,
) -> None:
    points = [(0, base)]
    x = -8
    ridge_points: list[tuple[int, int]] = []
    for _ in range(peaks):
        x += rng.randrange(18, 34)
        shoulder = x - rng.randrange(8, 15)
        peak = max(20, base - rng.randrange(14, 42))
        ridge_points.extend([(shoulder, base - rng.randrange(3, 10)), (x, peak),
                             (x + rng.randrange(8, 17), base)])
    poly(draw, points + ridge_points + [(WIDTH + 1, base), (WIDTH + 1, HEIGHT), (0, HEIGHT)], colour)
    if highlight is not None:
        for px, py in ridge_points[1::3]:
            line(draw, [(px, py), (px - rng.randrange(5, 12), py + rng.randrange(5, 12))], highlight)


def city_layer(
    draw: ImageDraw.ImageDraw,
    rng: random.Random,
    base: int,
    top_min: int,
    bodies: tuple[int, ...],
    windows: tuple[int, ...],
    detail: bool = True,
) -> None:
    """Build a non-repeating skyline from chunky, individually lit blocks."""
    x = -12
    while x < WIDTH + 10:
        width = rng.randrange(13, 31)
        top = rng.randrange(top_min, max(top_min + 2, base - 8))
        body = rng.choice(bodies)
        rect(draw, (x, top, x + width, base), body)
        if rng.random() < 0.42:
            rect(draw, (x + 3, top - 3, x + width - 3, top), body)
        if rng.random() < 0.2:
            line(draw, [(x + width // 2, top - rng.randrange(4, 12)),
                        (x + width // 2, top)], rng.choice(windows))
        if detail:
            for wy in range(top + 7, base - 5, rng.choice([7, 9, 11])):
                for wx in range(x + 4, x + width - 2, 7):
                    if rng.random() < 0.31:
                        rect(draw, (wx, wy, wx + rng.choice([1, 2]), wy + rng.choice([1, 2])),
                             rng.choice(windows))
        x += width + rng.randrange(3, 9)


def pine(draw: ImageDraw.ImageDraw, x: int, base: int, height: int, body: int, light: int | None = None) -> None:
    trunk = max(1, height // 13)
    rect(draw, (x - trunk, base - height // 3, x + trunk, base), body)
    tiers = max(3, height // 10)
    for row in range(tiers):
        y = base - height + row * 6
        half = max(3, int((row + 2) * height / (tiers * 2.5)))
        poly(draw, [(x, y), (x - half, y + height // 3), (x + half, y + height // 3)], body)
        if light is not None and row % 2 == 0:
            line(draw, [(x, y + 4), (x - half // 2, y + height // 4)], light)


def pine_line(
    draw: ImageDraw.ImageDraw,
    rng: random.Random,
    base: int,
    height_range: tuple[int, int],
    bodies: tuple[int, ...],
    light: int | None = None,
    spacing: tuple[int, int] = (12, 24),
) -> None:
    x = -10
    while x < WIDTH + 10:
        height = rng.randrange(*height_range)
        pine(draw, x, base, height, rng.choice(bodies), light if rng.random() < 0.55 else None)
        x += rng.randrange(*spacing)


def perspective_floor(
    draw: ImageDraw.ImageDraw,
    horizon: int,
    van_x: int,
    floor_colour: int,
    edge_colour: int,
    line_colour: int,
    stripe_colour: int | None = None,
) -> None:
    rect(draw, (0, horizon, WIDTH - 1, HEIGHT - 1), floor_colour)
    # A few wide horizontal steps give depth without turning the entire image
    # into a repetitive wireframe grid.
    for index in range(1, 10):
        ratio = (index / 10) ** 1.65
        y = horizon + max(2, int((HEIGHT - horizon) * ratio))
        line(draw, [(0, y), (WIDTH - 1, y)], edge_colour if index % 3 else line_colour)
    for bottom_x in range(-40, WIDTH + 80, 27):
        line(draw, [(van_x, horizon), (bottom_x, HEIGHT - 1)], line_colour)
    line(draw, [(van_x - 18, horizon), (-18, HEIGHT - 1)], edge_colour)
    line(draw, [(van_x + 18, horizon), (WIDTH + 18, HEIGHT - 1)], edge_colour)
    if stripe_colour is not None:
        for side in (-1, 1):
            for index in range(3, 8):
                ratio = (index / 9) ** 1.55
                y = horizon + int((HEIGHT - horizon) * ratio)
                span = max(2, int((y - horizon) * 0.12))
                cx = van_x + side * int((y - horizon) * 0.36)
                rect(draw, (cx - span, y, cx + span, y + 2), stripe_colour)


def wet_reflections(
    draw: ImageDraw.ImageDraw,
    rng: random.Random,
    y0: int,
    colours: tuple[int, ...],
    count: int = 45,
) -> None:
    for _ in range(count):
        x = rng.randrange(0, WIDTH - 5)
        y = rng.randrange(y0, HEIGHT - 3)
        length = rng.randrange(2, 15)
        rect(draw, (x, y, min(WIDTH - 1, x + length), y + rng.choice([0, 1])), rng.choice(colours))


def rain(draw: ImageDraw.ImageDraw, rng: random.Random, y0: int, y1: int, colours: tuple[int, ...], count: int) -> None:
    for _ in range(count):
        x = rng.randrange(0, WIDTH)
        y = rng.randrange(y0, max(y0 + 1, y1))
        length = rng.randrange(3, 11)
        line(draw, [(x, y), (x - rng.choice([0, 1]), min(y1, y + length))], rng.choice(colours))


def neon_sign(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    width: int,
    height: int,
    frame: int,
    glow: int,
    core: int,
    style: int,
) -> None:
    rect(draw, (x - 2, y - 2, x + width + 2, y + height + 2), frame)
    rect(draw, (x, y, x + width, y + height), glow)
    rect(draw, (x + 3, y + 3, x + width - 3, y + height - 3), frame)
    if style % 3 == 0:
        poly(draw, [(x + width // 2, y + 4), (x + width - 5, y + height // 2),
                    (x + width // 2, y + height - 4), (x + 5, y + height // 2)], core)
    elif style % 3 == 1:
        rect(draw, (x + 5, y + height // 3, x + width - 5, y + height // 3 + 2), core)
        rect(draw, (x + width // 3, y + 5, x + width // 3 + 2, y + height - 5), core)
    else:
        for offset in range(5, width - 3, 7):
            rect(draw, (x + offset, y + 4, x + offset + 2, y + height - 4), core)


def cable(draw: ImageDraw.ImageDraw, points: list[tuple[int, int]], colour: int, node: int | None = None) -> None:
    line(draw, points, colour)
    if node is not None:
        for x, y in points[1::2]:
            rect(draw, (x - 1, y - 1, x + 1, y + 1), node)


def draw_roadside_lamps(draw: ImageDraw.ImageDraw, positions: list[tuple[int, int]], post: int, light: int) -> None:
    for x, y in positions:
        line(draw, [(x, y), (x, y + 24)], post, 2)
        rect(draw, (x - 4, y - 2, x + 4, y + 2), light)
        rect(draw, (x - 2, y + 3, x + 2, y + 5), light)


def draw_train(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    width: int,
    body: int,
    trim: int,
    window: int,
    light: int,
) -> None:
    rect(draw, (x + 5, y - 4, x + width - 8, y + 3), body)
    poly(draw, [(x, y + 5), (x + 8, y - 1), (x + width - 8, y - 1),
                (x + width, y + 8), (x + width, y + 25), (x, y + 25)], body)
    rect(draw, (x + 3, y + 6, x + width - 4, y + 10), trim)
    for wx in range(x + 12, x + width - 16, 21):
        rect(draw, (wx, y + 12, wx + 13, y + 19), window)
        rect(draw, (wx + 2, y + 14, wx + 4, y + 16), light)
    rect(draw, (x + width - 5, y + 15, x + width - 1, y + 20), light)
    for wheel_x in range(x + 14, x + width - 8, 25):
        rect(draw, (wheel_x, y + 24, wheel_x + 7, y + 29), 0)
        rect(draw, (wheel_x + 2, y + 25, wheel_x + 5, y + 27), trim)


def draw_bridge(draw: ImageDraw.ImageDraw, y: int, x0: int, x1: int, body: int, trim: int, light: int) -> None:
    rect(draw, (x0, y, x1, y + 8), body)
    rect(draw, (x0, y - 3, x1, y), trim)
    for x in range(x0 + 6, x1, 15):
        rect(draw, (x, y + 3, x + 7, y + 5), light)
    for x in range(x0 + 12, x1, 34):
        poly(draw, [(x, y + 8), (x + 8, y + 8), (x + 5, HEIGHT), (x + 2, HEIGHT)], body)


def draw_arcade_machine(
    draw: ImageDraw.ImageDraw,
    x: int,
    base: int,
    body: int,
    trim: int,
    screen: int,
    glow: int,
) -> None:
    poly(draw, [(x, base), (x + 4, base - 54), (x + 31, base - 54),
                (x + 39, base - 46), (x + 39, base),], body)
    rect(draw, (x + 5, base - 50, x + 31, base - 30), trim)
    rect(draw, (x + 8, base - 47, x + 28, base - 34), screen)
    rect(draw, (x + 11, base - 44, x + 17, base - 42), glow)
    rect(draw, (x + 19, base - 40, x + 25, base - 38), glow)
    rect(draw, (x + 7, base - 26, x + 34, base - 21), trim)
    rect(draw, (x + 13, base - 24, x + 17, base - 20), glow)
    rect(draw, (x + 25, base - 24, x + 28, base - 20), glow)
    rect(draw, (x + 5, base - 4, x + 12, base), trim)
    rect(draw, (x + 28, base - 4, x + 35, base), trim)


def draw_tunnel(draw: ImageDraw.ImageDraw, horizon: int, body: int, inner: int, trim: int, van_x: int) -> None:
    # A nested, stepped opening gives the road a clear destination and keeps
    # the saturated colour localized at the vanishing point.
    poly(draw, [(0, 0), (78, 0), (112, horizon), (0, horizon + 27)], body)
    poly(draw, [(WIDTH, 0), (WIDTH - 78, 0), (van_x + 30, horizon),
                (WIDTH, horizon + 27)], body)
    poly(draw, [(62, 0), (89, 0), (van_x + 22, horizon), (van_x - 22, horizon),
                (27, 0)], inner)
    line(draw, [(78, 0), (van_x - 22, horizon)], trim, 3)
    line(draw, [(WIDTH - 78, 0), (van_x + 22, horizon)], trim, 3)
    for y in range(28, horizon, 24):
        fraction = (y / max(1, horizon))
        left = int(78 - fraction * 52)
        right = WIDTH - left
        line(draw, [(left, y), (right, y)], trim)


def draw_lighthouse(
    draw: ImageDraw.ImageDraw,
    x: int,
    base: int,
    height: int,
    body: int,
    trim: int,
    light: int,
    beam: int,
) -> None:
    rect(draw, (x - 8, base - height + 4, x + 10, base - height + 13), body)
    rect(draw, (x - 5, base - height - 1, x + 7, base - height + 5), trim)
    poly(draw, [(x - 5, base - height + 13), (x + 7, base - height + 13),
                (x + 12, base), (x - 11, base)], body)
    for stripe_y in range(base - height + 18, base - 1, 13):
        rect(draw, (x - 7 + (stripe_y - (base - height)) // 8,
                    stripe_y, x + 9 - (stripe_y - (base - height)) // 8, stripe_y + 4), trim)
    rect(draw, (x - 2, base - height + 6, x + 4, base - height + 10), light)
    line(draw, [(x, base - height + 3), (x - 56, base - height - 7)], beam)
    line(draw, [(x + 3, base - height + 6), (x + 61, base - height + 2)], beam)
    rect(draw, (x - 16, base, x + 17, base + 3), trim)


def draw_ringed_planet(
    draw: ImageDraw.ImageDraw,
    cx: int,
    cy: int,
    radius: int,
    body: int,
    shadow: int,
    ring: int,
    highlight: int,
) -> None:
    line(draw, [(cx - radius - 25, cy + 7), (cx - radius - 10, cy + 2),
                (cx + radius + 25, cy - 8), (cx + radius + 37, cy - 4)], ring, 3)
    line(draw, [(cx - radius - 25, cy + 14), (cx - radius - 8, cy + 9),
                (cx + radius + 19, cy - 1), (cx + radius + 35, cy + 4)], highlight)
    layered_disc(draw, cx, cy, radius, shadow, body, body)
    for offset in range(-radius // 2, radius // 2, 7):
        rect(draw, (cx - radius + 4, cy + offset, cx + radius - 4, cy + offset + 2), shadow if offset < 0 else ring)
    rect(draw, (cx - radius // 2, cy - radius // 2, cx - radius // 4, cy - radius // 2 + 3), highlight)


def draw_rocket(
    draw: ImageDraw.ImageDraw,
    x: int,
    base: int,
    height: int,
    body: int,
    trim: int,
    window: int,
    flame: int,
) -> None:
    poly(draw, [(x, base - height), (x + 10, base - height + 10),
                (x + 13, base - 17), (x + 20, base - 11), (x + 18, base - 4),
                (x - 18, base - 4), (x - 20, base - 11), (x - 13, base - 17),
                (x - 10, base - height + 10)], body)
    rect(draw, (x - 10, base - height + 14, x + 10, base - 12), trim)
    pixel_disc(draw, x, base - height + 21, 5, window)
    rect(draw, (x - 4, base - height + 19, x + 1, base - height + 21), flame)
    poly(draw, [(x - 10, base - 4), (x - 4, base + 14), (x, base + 2),
                (x + 5, base + 14), (x + 10, base - 4)], flame)
    rect(draw, (x - 28, base, x + 28, base + 3), trim)


def draw_space_station(draw: ImageDraw.ImageDraw, x: int, y: int, body: int, trim: int, light: int) -> None:
    rect(draw, (x - 22, y - 4, x + 22, y + 5), body)
    rect(draw, (x - 9, y - 11, x + 9, y + 12), body)
    poly(draw, [(x - 41, y - 7), (x - 22, y - 4), (x - 22, y + 4), (x - 41, y + 8)], trim)
    poly(draw, [(x + 41, y - 7), (x + 22, y - 4), (x + 22, y + 4), (x + 41, y + 8)], trim)
    for wx in range(x - 34, x + 35, 12):
        rect(draw, (wx, y - 1, wx + 5, y + 2), light)
    line(draw, [(x, y - 11), (x, y - 27)], trim)
    rect(draw, (x - 3, y - 29, x + 3, y - 26), light)


def draw_satellite_dish(draw: ImageDraw.ImageDraw, x: int, base: int, body: int, trim: int, signal: int) -> None:
    poly(draw, [(x - 24, base - 37), (x, base - 26), (x + 19, base - 42),
                (x + 12, base - 14), (x - 17, base - 15)], body)
    line(draw, [(x - 5, base - 27), (x + 4, base)], trim, 2)
    rect(draw, (x - 12, base, x + 15, base + 4), trim)
    line(draw, [(x + 1, base - 38), (x + 31, base - 57)], signal)
    line(draw, [(x + 8, base - 32), (x + 43, base - 42)], signal)
    rect(draw, (x + 30, base - 59, x + 33, base - 56), signal)


def draw_crystals(draw: ImageDraw.ImageDraw, rng: random.Random, base: int, colours: tuple[int, ...]) -> None:
    for _ in range(12):
        x = rng.randrange(8, WIDTH - 8)
        h = rng.randrange(10, 33)
        width = rng.randrange(4, 10)
        poly(draw, [(x, base), (x - width, base), (x - width // 2, base - h),
                    (x + width // 3, base - h - rng.randrange(2, 9)), (x + width, base)],
             rng.choice(colours))
        if rng.random() < 0.7:
            line(draw, [(x - width // 3, base - 3), (x + width // 3, base - h)],
                 rng.choice(colours))


def draw_waterfall(draw: ImageDraw.ImageDraw, x: int, top: int, bottom: int, width: int, water: int, foam: int) -> None:
    poly(draw, [(x - width, top), (x + width, top), (x + width + 6, bottom),
                (x + width // 2, bottom), (x, bottom - 9), (x - width // 2, bottom),
                (x - width - 6, bottom)], water)
    for offset in range(-width, width + 1, 7):
        line(draw, [(x + offset, top + 4), (x + offset + (offset % 3), bottom - 2)], foam)
    rect(draw, (x - width - 10, bottom - 3, x + width + 10, bottom + 1), foam)


def draw_cabin(draw: ImageDraw.ImageDraw, x: int, base: int, body: int, roof: int, window: int, glow: int) -> None:
    rect(draw, (x - 25, base - 28, x + 25, base), body)
    poly(draw, [(x - 34, base - 27), (x, base - 54), (x + 35, base - 27)], roof)
    rect(draw, (x - 15, base - 20, x - 2, base - 7), window)
    rect(draw, (x + 8, base - 20, x + 21, base - 7), window)
    rect(draw, (x - 9, base - 38, x + 6, base - 27), glow)
    rect(draw, (x + 20, base - 57, x + 25, base - 36), roof)
    rect(draw, (x + 19, base - 59, x + 27, base - 56), glow)
    rect(draw, (x - 6, base - 8, x + 5, base), roof)


def draw_portal_gate(draw: ImageDraw.ImageDraw, cx: int, floor: int, outer: int, inner: int, core: int, beam: int, variant: int) -> None:
    left = cx - 47
    right = cx + 47
    poly(draw, [(left, floor), (left + 5, floor - 78), (left + 20, floor - 96),
                (cx, floor - 108), (right - 20, floor - 96), (right - 5, floor - 78),
                (right, floor)], outer)
    poly(draw, [(left + 13, floor), (left + 17, floor - 69), (left + 29, floor - 83),
                (cx, floor - 91), (right - 29, floor - 83), (right - 17, floor - 69),
                (right - 13, floor)], inner)
    poly(draw, [(cx - 30, floor - 4), (cx - 26, floor - 59), (cx, floor - 75),
                (cx + 26, floor - 59), (cx + 30, floor - 4)], core)
    for x in (left + 9, right - 9):
        rect(draw, (x - 3, floor - 54, x + 3, floor - 15), beam)
    if variant % 2:
        line(draw, [(cx, floor - 74), (cx, floor - 111)], beam, 2)
        rect(draw, (cx - 4, floor - 116, cx + 4, floor - 111), beam)


def draw_reactor(draw: ImageDraw.ImageDraw, cx: int, floor: int, body: int, trim: int, core: int, light: int) -> None:
    rect(draw, (cx - 50, floor - 76, cx + 50, floor), body)
    rect(draw, (cx - 41, floor - 88, cx + 41, floor - 76), trim)
    rect(draw, (cx - 30, floor - 66, cx + 30, floor - 16), trim)
    pixel_disc(draw, cx, floor - 41, 21, core)
    pixel_disc(draw, cx, floor - 41, 12, light)
    rect(draw, (cx - 6, floor - 47, cx + 6, floor - 35), core)
    for x in range(cx - 43, cx + 44, 16):
        rect(draw, (x, floor - 9, x + 7, floor - 2), light)
    line(draw, [(cx - 42, floor - 72), (cx - 59, floor - 100)], trim, 2)
    line(draw, [(cx + 42, floor - 72), (cx + 59, floor - 100)], trim, 2)


def draw_shrine(draw: ImageDraw.ImageDraw, cx: int, floor: int, body: int, trim: int, light: int, accent: int) -> None:
    rect(draw, (cx - 52, floor - 8, cx + 52, floor), trim)
    rect(draw, (cx - 41, floor - 15, cx + 41, floor - 8), body)
    poly(draw, [(cx - 31, floor - 15), (cx - 24, floor - 79), (cx, floor - 103),
                (cx + 24, floor - 79), (cx + 31, floor - 15)], body)
    poly(draw, [(cx - 19, floor - 17), (cx - 14, floor - 66), (cx, floor - 82),
                (cx + 14, floor - 66), (cx + 19, floor - 17)], trim)
    pixel_disc(draw, cx, floor - 47, 12, light)
    rect(draw, (cx - 4, floor - 52, cx + 4, floor - 42), accent)
    for side in (-1, 1):
        line(draw, [(cx + side * 29, floor - 76), (cx + side * 44, floor - 98)], light, 2)
        rect(draw, (cx + side * 42 - 2, floor - 102, cx + side * 42 + 2, floor - 97), accent)


def draw_obelisk(draw: ImageDraw.ImageDraw, x: int, base: int, height: int, body: int, edge: int, glow: int) -> None:
    poly(draw, [(x, base - height), (x + 14, base - height + 13),
                (x + 17, base), (x - 17, base), (x - 14, base - height + 13)], body)
    line(draw, [(x, base - height), (x - 3, base - 3)], edge, 2)
    rect(draw, (x - 3, base - height + 17, x + 3, base - 22), glow)
    rect(draw, (x - 23, base, x + 23, base + 4), edge)


def draw_arena_floor(draw: ImageDraw.ImageDraw, horizon: int, van_x: int, floor: int, body: int, line_colour: int, glow: int) -> None:
    perspective_floor(draw, horizon, van_x, body, line_colour, glow)
    rect(draw, (0, floor, WIDTH - 1, floor + 5), line_colour)
    for x in range(12, WIDTH, 31):
        rect(draw, (x, floor - 5, x + 12, floor - 2), glow)


def draw_nebula(draw: ImageDraw.ImageDraw, rng: random.Random, colour_a: int, colour_b: int, y0: int, y1: int) -> None:
    for _ in range(6):
        x = rng.randrange(-40, WIDTH - 20)
        y = rng.randrange(y0, y1)
        pixel_cloud(draw, x, y, rng.randrange(35, 90), rng.randrange(8, 23), colour_a if rng.random() < 0.6 else colour_b)


def scene_arcade(draw: ImageDraw.ImageDraw, palette: list[str], rng: random.Random, variant: int) -> None:
    mode = variant % 7
    if mode in (0, 1, 2, 4):
        banded_sky(draw, [(35, 1), (72, 2), (103, 3), (HEIGHT - 1, 12)])
        dither_transition(draw, rng, 35, 80, 3, 0.25)
        starfield(draw, rng, (7, 8, 9), 31, 7, 91)
    else:
        banded_sky(draw, [(42, 1), (88, 2), (111, 3), (HEIGHT - 1, 12)])
        dither_transition(draw, rng, 43, 92, 3, 0.3)
        starfield(draw, rng, (7, 8, 9), 24, 7, 99)

    if mode in (0, 3, 5):
        sun_x = [72, 244, 188][(variant // 2) % 3]
        layered_disc(draw, sun_x, 55 + (variant % 3) * 3, 24 + variant % 4, 3, 5, 7)
        for y in range(48, 69, 5):
            rect(draw, (sun_x - 20, y, sun_x + 20, y + 1), 3)
    else:
        layered_disc(draw, 254 - (variant % 3) * 23, 47, 17, 1, 2, 9)

    mountain_ridge(draw, rng, 106, 3, 10, 10)
    city_layer(draw, rng, 122, 76, (1, 2, 11), (4, 7, 8), True)

    if mode == 0:  # Sunset boulevard: a single road and the sun do the directing.
        perspective_floor(draw, rng.randrange(108, 116), 74 + rng.randrange(-8, 9), 12, 3, 4, 6)
        draw_roadside_lamps(draw, [(27, 92), (285, 95)], 11, 7)
        neon_sign(draw, 19, 72, 26, 22, 12, 4, 7, variant)
        neon_sign(draw, 273, 67, 29, 20, 12, 8, 5, variant + 1)
    elif mode == 1:  # Elevated train, with the street deliberately quieter below it.
        draw_bridge(draw, 101, -5, WIDTH + 5, 11, 4, 7)
        draw_train(draw, 41 + rng.randrange(-15, 24), 75, 133, 12, 4, 2, 7)
        perspective_floor(draw, 121, 193, 12, 11, 4, None)
        wet_reflections(draw, rng, 128, (4, 7, 8), 29)
        cable(draw, [(4, 59), (73, 73), (147, 61), (224, 72), (316, 56)], 10, 8)
    elif mode == 2:  # Rooftop arcade: one large object replaces the old central emblem.
        rect(draw, (0, 116, WIDTH - 1, HEIGHT - 1), 11)
        rect(draw, (0, 113, WIDTH - 1, 118), 4)
        draw_arcade_machine(draw, 43 + rng.randrange(-7, 8), 167, 12, 4, 2, 8)
        neon_sign(draw, 228, 76, 50, 25, 12, 4, 7, variant + 2)
        draw_roadside_lamps(draw, [(132, 93), (184, 88), (302, 95)], 12, 8)
        for x in range(7, WIDTH, 28):
            rect(draw, (x, 134 + (x % 3), x + 10, 136 + (x % 3)), 10)
    elif mode == 3:  # Rainy intersection, with puddles and two crossing light paths.
        rain(draw, rng, 45, 132, (8, 9, 10), 80)
        perspective_floor(draw, 110, 222, 12, 3, 4, 6)
        for x in (80, 246):
            rect(draw, (x, 109, x + 7, 130), 11)
            rect(draw, (x - 5, 108, x + 12, 111), 7)
        wet_reflections(draw, rng, 132, (4, 5, 7, 8), 58)
        neon_sign(draw, 15, 85, 34, 21, 12, 5, 8, variant)
        neon_sign(draw, 270, 82, 33, 24, 12, 4, 7, variant + 1)
    elif mode == 4:  # Bridge overlook: the architecture frames empty sky and distant lights.
        mountain_ridge(draw, rng, 111, 2, 10, 9)
        draw_bridge(draw, 109, 32, 288, 11, 4, 8)
        perspective_floor(draw, 115, 164, 12, 11, 3, None)
        neon_sign(draw, 12, 82, 24, 19, 12, 4, 7, variant)
        neon_sign(draw, 282, 59, 24, 20, 12, 8, 5, variant + 1)
    elif mode == 5:  # Tunnel: the bright exit is the only high-contrast focal point.
        draw_tunnel(draw, 111, 12, 3, 4, 157 + rng.randrange(-8, 9))
        layered_disc(draw, 158, 92, 11, 3, 5, 7)
        perspective_floor(draw, 111, 158, 12, 3, 4, 6)
        for x in (39, 278):
            rect(draw, (x, 76, x + 8, 116), 11)
            rect(draw, (x - 5, 73, x + 13, 78), 4)
    else:  # Harbor edge: a low skyline, water, and a single lit tower.
        water_y = 109
        city_layer(draw, rng, water_y, 75, (1, 2, 11), (4, 7), True)
        rect(draw, (0, water_y, WIDTH - 1, HEIGHT - 1), 12)
        wet_reflections(draw, rng, water_y + 7, (3, 4, 7, 8), 53)
        draw_lighthouse(draw, 243, 127, 48, 11, 4, 7, 8)
        cable(draw, [(0, 107), (54, 103), (113, 108), (176, 101), (320, 106)], 10, 8)


def scene_cyber(draw: ImageDraw.ImageDraw, palette: list[str], rng: random.Random, variant: int) -> None:
    mode = variant % 7
    banded_sky(draw, [(37, 1), (76, 2), (111, 3), (HEIGHT - 1, 12)])
    starfield(draw, rng, (4, 5, 9), 17, 8, 86)
    dither_transition(draw, rng, 40, 98, 3, 0.33)
    city_layer(draw, rng, 119, 61, (1, 2, 3), (4, 5, 8), mode not in (0, 5))
    rain(draw, rng, 9, 133, (4, 5, 8), 74 if mode != 2 else 48)

    if mode == 0:  # Narrow alley framed by two walls and a bright wet vanishing point.
        rect(draw, (0, 48, 69, 138), 12)
        rect(draw, (251, 38, WIDTH - 1, 138), 12)
        for y in range(58, 127, 14):
            rect(draw, (7, y, 57, y + 2), 11)
            rect(draw, (263, y + 5, 313, y + 7), 11)
        neon_sign(draw, 23, 69, 31, 24, 12, 4, 5, variant)
        neon_sign(draw, 267, 57, 34, 25, 12, 7, 8, variant + 1)
        perspective_floor(draw, 109, 158, 12, 11, 4, 6)
        wet_reflections(draw, rng, 123, (4, 5, 7), 56)
        cable(draw, [(0, 31), (59, 63), (159, 48), (319, 69)], 10, 5)
    elif mode == 1:  # Monorail through a deep stack of lit towers.
        draw_bridge(draw, 91, -5, WIDTH + 5, 11, 5, 8)
        draw_train(draw, 74 + rng.randrange(-22, 18), 62, 176, 12, 5, 2, 8)
        rect(draw, (0, 116, WIDTH - 1, HEIGHT - 1), 12)
        wet_reflections(draw, rng, 126, (4, 5, 8), 40)
        for x in range(13, WIDTH, 38):
            rect(draw, (x, 115, x + 4, 151), 11)
            rect(draw, (x - 6, 113, x + 10, 117), 4)
        cable(draw, [(8, 42), (77, 62), (182, 45), (316, 55)], 5, 8)
    elif mode == 2:  # Rooftop relay with a water tank and disciplined sign clusters.
        rect(draw, (0, 107, WIDTH - 1, HEIGHT - 1), 11)
        rect(draw, (0, 103, WIDTH - 1, 108), 4)
        rect(draw, (63, 72, 117, 106), 12)
        rect(draw, (73, 64, 108, 72), 11)
        rect(draw, (78, 59, 103, 65), 4)
        line(draw, [(90, 59), (90, 35)], 5, 2)
        rect(draw, (86, 31, 94, 35), 8)
        neon_sign(draw, 203, 61, 48, 27, 12, 5, 8, variant)
        neon_sign(draw, 17, 80, 28, 21, 12, 4, 5, variant + 1)
        draw_roadside_lamps(draw, [(150, 78), (172, 71), (300, 76)], 12, 5)
        cable(draw, [(0, 47), (76, 59), (155, 51), (237, 64), (320, 44)], 4, 8)
    elif mode == 3:  # Canal district, using reflected signs instead of a bright floor grid.
        water_y = 112
        rect(draw, (0, water_y, WIDTH - 1, HEIGHT - 1), 12)
        city_layer(draw, rng, water_y, 68, (1, 2, 11), (4, 5, 8), True)
        for x, y, w, h, glow in [(31, 70, 25, 26, 5), (264, 63, 34, 24, 8), (122, 88, 22, 18, 4)]:
            neon_sign(draw, x, y, w, h, 12, glow, 9 if glow == 8 else 7, variant + x)
        wet_reflections(draw, rng, water_y + 5, (4, 5, 7, 8), 68)
        cable(draw, [(2, 39), (84, 74), (176, 53), (319, 76)], 5, 8)
        for x in (92, 226):
            rect(draw, (x, water_y - 5, x + 5, water_y + 16), 11)
    elif mode == 4:  # Server tower: a single vertical mass breaks the horizontal city rhythm.
        rect(draw, (104, 39, 213, 126), 12)
        rect(draw, (114, 30, 203, 40), 11)
        rect(draw, (126, 24, 191, 31), 4)
        for y in range(49, 113, 12):
            rect(draw, (117, y, 200, y + 3), 3)
            rect(draw, (125, y + 1, 135, y + 2), 5)
            rect(draw, (174, y + 1, 190, y + 2), 8)
        line(draw, [(158, 24), (158, 8)], 5, 2)
        pixel_disc(draw, 158, 6, 4, 8)
        perspective_floor(draw, 122, 160, 12, 11, 3, 5)
        neon_sign(draw, 22, 67, 35, 22, 12, 4, 5, variant)
        neon_sign(draw, 257, 81, 37, 25, 12, 7, 8, variant + 1)
    elif mode == 5:  # Market canopy with large awnings and a calmer lower frame.
        rect(draw, (0, 105, WIDTH - 1, HEIGHT - 1), 12)
        for x in range(-10, WIDTH, 53):
            poly(draw, [(x, 76 + (x % 9)), (x + 43, 76), (x + 51, 96), (x + 4, 98)], 11)
            rect(draw, (x + 5, 91, x + 42, 95), 4 if x % 2 else 7)
            rect(draw, (x + 18, 98, x + 22, 129), 11)
        neon_sign(draw, 28, 56, 42, 18, 12, 4, 8, variant)
        neon_sign(draw, 244, 51, 49, 21, 12, 7, 5, variant + 2)
        perspective_floor(draw, 118, 161, 12, 11, 3, 6)
        wet_reflections(draw, rng, 131, (4, 5, 7), 43)
    else:  # Underpass, with cables and a hard-edged light box in the distance.
        draw_tunnel(draw, 112, 12, 2, 4, 160)
        rect(draw, (137, 83, 183, 111), 4)
        rect(draw, (145, 88, 175, 106), 8)
        perspective_floor(draw, 112, 160, 12, 3, 4, 5)
        rain(draw, rng, 10, 116, (4, 5), 34)
        for x in (32, 286):
            neon_sign(draw, x, 70, 25, 18, 12, 4 if x < 100 else 7, 8, variant + x)


def scene_space(draw: ImageDraw.ImageDraw, palette: list[str], rng: random.Random, variant: int) -> None:
    mode = variant % 7
    banded_sky(draw, [(43, 1), (88, 2), (133, 3), (HEIGHT - 1, 11)])
    starfield(draw, rng, (7, 8, 9), 66, 5, 143)
    draw_nebula(draw, rng, 2, 3, 35, 103)
    dither_transition(draw, rng, 43, 96, 3, 0.2)

    if mode == 0:  # Ringed planet balanced by a small, crisp orbital station.
        draw_ringed_planet(draw, 239, 64, 35, 4, 3, 9, 8)
        draw_space_station(draw, 72, 105, 11, 8, 9)
        line(draw, [(0, 132), (WIDTH - 1, 132)], 11)
        for x in range(11, WIDTH, 33):
            rect(draw, (x, 128, x + 10, 131), 4 if x % 2 else 8)
        cable(draw, [(16, 118), (52, 97), (99, 116)], 10, 8)
    elif mode == 1:  # Launch pad, with the rocket held against large negative sky.
        layered_disc(draw, 79, 51, 25, 2, 3, 7)
        for y in range(45, 68, 5):
            rect(draw, (59, y, 99, y + 1), 2)
        rect(draw, (0, 128, WIDTH - 1, HEIGHT - 1), 12)
        draw_rocket(draw, 231, 147, 76, 11, 4, 9, 7)
        rect(draw, (207, 148, 255, 152), 4)
        line(draw, [(196, 157), (270, 157)], 8)
        for x in range(34, 151, 38):
            rect(draw, (x, 122, x + 14, 127), 11)
            line(draw, [(x + 7, 122), (x + 7, 105)], 10)
    elif mode == 2:  # Porthole view, a graphic frame around a quieter star field.
        rect(draw, (0, 0, WIDTH - 1, HEIGHT - 1), 12)
        pixel_disc(draw, 74, 83, 78, 11)
        pixel_disc(draw, 74, 83, 68, 2)
        pixel_disc(draw, 74, 83, 59, 1)
        starfield(draw, rng, (7, 8, 9), 31, 25, 140)
        draw_ringed_planet(draw, 211, 70, 29, 4, 2, 8, 9)
        rect(draw, (0, 0, 12, HEIGHT - 1), 0)
        rect(draw, (308, 0, 319, HEIGHT - 1), 0)
        for x in range(18, WIDTH - 18, 47):
            rect(draw, (x, 14, x + 4, 20), 11)
            rect(draw, (x - 2, 18, x + 6, 21), 4 if x % 2 else 8)
    elif mode == 3:  # Asteroid route, with a small ship as an environmental object.
        for _ in range(13):
            x = rng.randrange(20, WIDTH - 20)
            y = rng.randrange(30, 133)
            size = rng.randrange(4, 13)
            poly(draw, [(x - size, y), (x - size // 2, y - size // 2), (x + size, y - size // 3),
                        (x + size // 2, y + size // 2), (x - size // 2, y + size)], rng.choice([2, 3, 11]))
        pixel_disc(draw, 245, 51, 31, 3)
        pixel_disc(draw, 245, 51, 23, 2)
        pixel_ship_x = 74 + rng.randrange(-12, 18)
        poly(draw, [(pixel_ship_x, 113), (pixel_ship_x + 24, 109), (pixel_ship_x + 37, 116),
                    (pixel_ship_x + 16, 119), (pixel_ship_x + 8, 125),
                    (pixel_ship_x - 8, 122)], 8)
        rect(draw, (pixel_ship_x + 10, 113, pixel_ship_x + 17, 116), 9)
        line(draw, [(pixel_ship_x - 22, 122), (pixel_ship_x - 5, 120)], 6, 2)
        line(draw, [(pixel_ship_x - 35, 126), (pixel_ship_x - 12, 123)], 5)
    elif mode == 4:  # Moonbase domes beneath a huge quiet planet.
        draw_ringed_planet(draw, 97, 48, 29, 5, 3, 9, 8)
        rect(draw, (0, 122, WIDTH - 1, HEIGHT - 1), 12)
        for x, width, height in [(41, 39, 22), (107, 56, 31), (196, 44, 25), (268, 61, 35)]:
            rect(draw, (x, 147 - height, x + width, 149), 11)
            pixel_disc(draw, x + width // 2, 147 - height + 5, width // 2, 3)
            rect(draw, (x + width // 2 - 3, 147 - height, x + width // 2 + 3, 147), 8)
        line(draw, [(171, 130), (171, 91)], 11, 2)
        rect(draw, (164, 87, 178, 92), 4)
        rect(draw, (167, 84, 175, 87), 8)
    elif mode == 5:  # Comet over a cratered foreground.
        pixel_disc(draw, 240, 50, 17, 7)
        line(draw, [(218, 59), (178, 69), (130, 91), (91, 104)], 5, 4)
        line(draw, [(224, 54), (183, 58), (141, 75), (101, 94)], 8, 2)
        mountain_ridge(draw, rng, 132, 3, 10, 9)
        rect(draw, (0, 132, WIDTH - 1, HEIGHT - 1), 12)
        for _ in range(21):
            x = rng.randrange(0, WIDTH)
            y = rng.randrange(137, 178)
            pixel_disc(draw, x, y, rng.randrange(2, 7), 11)
        line(draw, [(0, 142), (64, 136), (130, 144), (203, 138), (320, 146)], 4)
    else:  # Relay dish and orbital debris create a deliberate diagonal composition.
        draw_satellite_dish(draw, 89, 148, 11, 4, 8)
        draw_space_station(draw, 242, 84, 11, 5, 9)
        for x in range(173, 319, 33):
            rect(draw, (x, 129 + (x % 7), x + 9, 132 + (x % 7)), 4 if x % 2 else 8)
        cable(draw, [(101, 91), (157, 69), (214, 82), (320, 55)], 10, 8)
        pixel_disc(draw, 39, 45, 15, 3)


def scene_nature(draw: ImageDraw.ImageDraw, palette: list[str], rng: random.Random, variant: int) -> None:
    mode = variant % 7
    banded_sky(draw, [(38, 1), (78, 2), (111, 3), (HEIGHT - 1, 11)])
    dither_transition(draw, rng, 39, 83, 3, 0.3)
    starfield(draw, rng, (6, 7, 9), 26, 5, 93)
    if mode in (0, 2, 4, 6):
        layered_disc(draw, 251 - (variant % 3) * 28, 48, 20, 2, 3, 7)
    else:
        layered_disc(draw, 82 + (variant % 3) * 32, 57, 15, 1, 2, 9)
    mountain_ridge(draw, rng, 103, 2, 10, 11)
    mountain_ridge(draw, rng, 120, 3, 4, 14)

    if mode == 0:  # Quiet lake and cabin, with a warm point inside cool terrain.
        water_y = 119
        rect(draw, (0, water_y, WIDTH - 1, HEIGHT - 1), 12)
        draw_cabin(draw, 83, 151, 11, 3, 4, 7)
        pine_line(draw, rng, 133, (35, 65), (11, 3), 4, (17, 31))
        wet_reflections(draw, rng, water_y + 7, (3, 4, 8), 48)
        line(draw, [(205, 133), (249, 128), (292, 134)], 8)
    elif mode == 1:  # Lighthouse coast, using the beam as the compositional line.
        rect(draw, (0, 122, WIDTH - 1, HEIGHT - 1), 2)
        mountain_ridge(draw, rng, 120, 3, 4, 9)
        draw_lighthouse(draw, 246, 133, 63, 11, 4, 7, 8)
        for x in range(8, 195, 27):
            poly(draw, [(x, 139), (x + 8, 127 - (x % 11)), (x + 17, 139)], 11)
        wet_reflections(draw, rng, 147, (3, 4, 8), 42)
        line(draw, [(0, 143), (54, 138), (123, 145), (198, 139)], 4)
    elif mode == 2:  # Waterfall valley, with hard vertical light against dark rock.
        block_colour = 11
        poly(draw, [(0, 111), (38, 85), (82, 103), (125, 71), (169, 105),
                    (212, 79), (260, 106), (320, 74), (320, 180), (0, 180)], block_colour)
        draw_waterfall(draw, 198, 76, 151, 19, 8, 14)
        draw_crystals(draw, rng, 167, (4, 7, 8, 9))
        pine_line(draw, rng, 151, (22, 52), (11, 3), 4, (20, 33))
        wet_reflections(draw, rng, 153, (4, 7, 8), 38)
    elif mode == 3:  # Crystal grove, with clustered light rather than a flat gradient.
        rect(draw, (0, 120, WIDTH - 1, HEIGHT - 1), 12)
        pine_line(draw, rng, 133, (32, 74), (11, 3), 4, (15, 28))
        draw_crystals(draw, rng, 160, (4, 5, 7, 8))
        for x in (58, 150, 270):
            pixel_disc(draw, x, 91 + (x % 9), 8, 8)
            rect(draw, (x - 2, 100, x + 2, 157), 4)
        wet_reflections(draw, rng, 155, (4, 5, 8), 35)
    elif mode == 4:  # Aurora forest, leaving a broad calm strip for the light bands.
        for offset in range(5):
            points = [(0, 62 + offset * 7), (43, 55 + offset * 5), (87, 71 + offset * 4),
                      (143, 48 + offset * 7), (198, 67 + offset * 3), (257, 52 + offset * 5),
                      (320, 63 + offset * 6)]
            line(draw, points, 4 + (offset % 2), 2)
        pine_line(draw, rng, 143, (39, 80), (11, 3), 4, (14, 27))
        rect(draw, (0, 144, WIDTH - 1, HEIGHT - 1), 12)
        wet_reflections(draw, rng, 148, (4, 5, 8), 42)
    elif mode == 5:  # Bioluminescent canyon, tall forms framing a narrow opening.
        poly(draw, [(0, 72), (48, 91), (73, 65), (119, 106), (151, 82),
                    (176, 113), (213, 76), (264, 101), (291, 67), (320, 84),
                    (320, 180), (0, 180)], 11)
        rect(draw, (0, 128, WIDTH - 1, HEIGHT - 1), 12)
        for x in range(23, WIDTH, 38):
            h = 17 + (x % 15)
            poly(draw, [(x, 143), (x - 10, 143), (x - 4, 143 - h), (x + 3, 136 - h),
                        (x + 11, 143)], 4 if x % 2 else 7)
            rect(draw, (x - 2, 143 - h + 5, x + 2, 143 - h + 8), 9)
        draw_crystals(draw, rng, 168, (4, 5, 8))
    else:  # River bridge: a quiet horizontal anchor against layered hills.
        water_y = 116
        rect(draw, (0, water_y, WIDTH - 1, HEIGHT - 1), 12)
        draw_bridge(draw, 117, 43, 284, 11, 4, 7)
        pine_line(draw, rng, 136, (27, 61), (11, 3), 4, (19, 32))
        wet_reflections(draw, rng, water_y + 8, (3, 4, 7, 8), 63)
        line(draw, [(0, 135), (61, 130), (124, 137), (198, 131), (320, 138)], 4)


def scene_boss(draw: ImageDraw.ImageDraw, palette: list[str], rng: random.Random, variant: int) -> None:
    mode = variant % 7
    banded_sky(draw, [(39, 1), (81, 2), (115, 3), (HEIGHT - 1, 11)])
    starfield(draw, rng, (4, 5, 8), 25, 5, 105)
    dither_transition(draw, rng, 39, 91, 3, 0.24)
    floor = rng.randrange(128, 137)
    draw_arena_floor(draw, floor - 13, 160 + rng.randrange(-10, 11), floor, 12, 3, 4)

    if mode == 0:  # Monumental portal keep.
        draw_portal_gate(draw, 160, floor, 11, 4, 3, 8, variant)
        for x in (32, 286):
            draw_obelisk(draw, x, floor, rng.randrange(53, 75), 11, 4, 7)
    elif mode == 1:  # Arcade colossus as machinery, not a creature.
        rect(draw, (105, 59, 215, floor), 11)
        rect(draw, (116, 49, 204, 60), 4)
        rect(draw, (128, 39, 192, 49), 12)
        rect(draw, (137, 29, 183, 39), 5)
        for y in range(70, floor - 13, 14):
            rect(draw, (117, y, 203, y + 3), 3 if y % 2 else 4)
            rect(draw, (129, y + 1, 139, y + 2), 8)
            rect(draw, (177, y + 1, 192, y + 2), 7)
        for x in (87, 224):
            line(draw, [(x, floor), (x + (-19 if x < 160 else 19), 82)], 4, 3)
            rect(draw, (x - 7, 77, x + 7, 82), 8)
    elif mode == 2:  # Reactor chamber with localized core light.
        draw_reactor(draw, 160, floor, 11, 4, 3, 8)
        draw_obelisk(draw, 48, floor, 59, 11, 4, 7)
        draw_obelisk(draw, 272, floor, 64, 11, 4, 7)
        cable(draw, [(0, 83), (63, 99), (108, 81)], 5, 8)
        cable(draw, [(320, 72), (257, 96), (212, 79)], 4, 7)
    elif mode == 3:  # Final gate: a strong vertical frame and a beam into the floor.
        draw_portal_gate(draw, 160 + rng.randrange(-8, 9), floor, 11, 5, 4, 7, variant)
        line(draw, [(160, 18), (160, floor - 84)], 8, 2)
        for x in (22, 298):
            rect(draw, (x - 4, floor - 38, x + 4, floor), 11)
            rect(draw, (x - 10, floor - 42, x + 10, floor - 37), 4)
    elif mode == 4:  # Shield core shrine.
        draw_shrine(draw, 160, floor, 11, 4, 8, 7)
        for x in (54, 266):
            pixel_disc(draw, x, floor - 63, 19, 3)
            pixel_disc(draw, x, floor - 63, 13, 4)
            line(draw, [(x - 25, floor - 63), (x + 25, floor - 63)], 8)
            line(draw, [(x, floor - 88), (x, floor - 38)], 7)
    elif mode == 5:  # Phantom arena: empty platform, suspended rings, and fog bands.
        for radius, colour in ((61, 3), (48, 4), (35, 5)):
            line(draw, [(160 - radius, 79), (160 - radius + 10, 72),
                        (160 + radius - 10, 72), (160 + radius, 79)], colour, 2)
            line(draw, [(160 - radius, 79), (160 - radius + 10, 86),
                        (160 + radius - 10, 86), (160 + radius, 79)], colour)
        rect(draw, (74, floor - 10, 246, floor - 4), 11)
        rect(draw, (91, floor - 17, 229, floor - 11), 4)
        for x in range(101, 222, 20):
            rect(draw, (x, floor - 28 - (x % 5), x + 5, floor - 25 - (x % 5)), 8)
        cable(draw, [(0, 67), (81, 89), (160, 61), (242, 87), (320, 65)], 10, 7)
    else:  # Power shrine / victory architecture.
        draw_shrine(draw, 160 + rng.randrange(-12, 13), floor, 11, 4, 7, 8)
        for side in (-1, 1):
            draw_obelisk(draw, 160 + side * 87, floor, 73, 11, 4, 7)
        line(draw, [(74, floor - 76), (160, floor - 108), (246, floor - 76)], 5, 2)
        rect(draw, (151, floor - 115, 169, floor - 109), 8)

    # Keep the lower edge quiet so the arena architecture, not a repeated
    # foreground-bar pattern, remains the focal point.
    rect(draw, (0, HEIGHT - 3, WIDTH - 1, HEIGHT - 1), 0)


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
    # A tiny deterministic star-like signature keeps duplicate detection useful
    # if a future scene recipe accidentally collapses two seeds to one raster.
    x = 5 + (seed % 23)
    y = 5 + ((seed // 23) % 14)
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
