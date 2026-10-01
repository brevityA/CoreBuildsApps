#!/usr/bin/env python3
"""
Core Builds — series-10-cinema: the lounge-room set, still and moving.

Six night-time cinema scenes in neon and marquee light: a marquee frontage,
a velvet stage, a projector beam, a neon lounge, a late-night rental shop
and a box office. Each is drawn once as a stack of layers (facade, bulbs,
tubes, beams) and animated by weighting those layers over time, so the 4K
still and the 1080p loop are the same drawing and cannot disagree.

Everything moves on whole-number cycles of the loop (bulbs chase, tubes
flicker, dust drifts, rain falls), so frame N equals frame 0 and the loop
has no seam. Deterministic: same code, same frames. No external art.

House rules (Wallpapers/README.md): dark ground, the bottom third kept calm
for launcher cards, the palette's cyan / violet / ember plus one warm bulb
white that a marquee cannot do without.

Writes:
  Wallpapers/series-10-cinema/corebuilds-NN-slug.png           3840x2160
  Wallpapers/thumbs/corebuilds-NN-slug.jpg                      480x270 (+ app copy)
  Motion/live/coremotion-live-NN-cinema-slug.mp4                1080p 60 fps 20 s
  Motion/live/thumbs/coremotion-live-NN-cinema-slug.jpg         480x270
  docs/cinema-wallpapers.png                                    contact sheet

    python tools/build_cinema_wallpapers.py                 # stills + loops
    python tools/build_cinema_wallpapers.py --stills        # stills only
    python tools/build_cinema_wallpapers.py --only 99       # one scene
"""
from __future__ import annotations

import argparse
import math
import random
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_classic_wallpapers import save_wall  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-10-cinema"
LIVE = ROOT / "Motion" / "live"
CONTACT_SHEET = ROOT / "docs" / "cinema-wallpapers.png"
FONT = ROOT / "tools" / "fonts" / "Outfit-Bold.ttf"

# Scenes are authored on a 1920x1080 grid and drawn at any scale.
BW, BH = 1920, 1080
FPS = 60
DURATION = 20
FRAMES = FPS * DURATION
CRF = 20
THUMB_W, THUMB_H = 480, 270
FIRST_CLIP = 23          # after the twelve Deep Space loops (11-22)

VOID = (4, 7, 15)
NIGHT = (13, 17, 23)
CYAN = (0, 212, 255)
GLOW_CYAN = (126, 238, 255)
VIOLET = (138, 72, 144)
ORCHID = (196, 110, 214)     # violet lifted for a lit tube
EMBER = (192, 58, 32)
FLAME = (255, 96, 60)        # ember lifted for a lit tube
BULB = (255, 204, 140)       # marquee bulb white - the one warm addition
BUILD_BLUE = (79, 172, 254)

# Wall number -> (slug, title). Clip number = FIRST_CLIP + index.
WALLS = [
    (97, "marquee-lights", "Marquee Lights"),
    (98, "velvet-curtain", "Velvet Curtain"),
    (99, "projector-beam", "Projector Beam"),
    (100, "neon-lounge", "Neon Lounge"),
    (101, "late-rentals", "Late Rentals"),
    (102, "box-office", "Box Office"),
]

TWO_PI = 2 * math.pi


def clip_name(index: int, slug: str) -> str:
    return f"coremotion-live-{FIRST_CLIP + index:02d}-cinema-{slug}"


# --------------------------------------------------------------------------
# Drawing primitives. Every layer is float32 HxWx3 in 0..255 (additive light)
# --------------------------------------------------------------------------

class Canvas:
    """Coordinates in the 1920x1080 authoring grid, pixels at scale [s]."""

    def __init__(self, s: float):
        self.s = s
        self.w, self.h = round(BW * s), round(BH * s)

    def p(self, v: float) -> int:
        return round(v * self.s)

    def box(self, x0, y0, x1, y1):
        return (self.p(x0), self.p(y0), self.p(x1), self.p(y1))

    def blank(self) -> np.ndarray:
        return np.zeros((self.h, self.w, 3), dtype="float32")

    def mask(self) -> tuple[Image.Image, ImageDraw.ImageDraw]:
        m = Image.new("L", (self.w, self.h), 0)
        return m, ImageDraw.Draw(m)

    def font(self, size: float) -> ImageFont.FreeTypeFont:
        return ImageFont.truetype(str(FONT), max(8, self.p(size)))


def tint(mask: Image.Image, colour, gain: float = 1.0) -> np.ndarray:
    a = np.asarray(mask, dtype="float32")[..., None] / 255.0
    return a * (np.array(colour, dtype="float32") * gain)


def blur(mask: Image.Image, radius: float) -> Image.Image:
    return mask.filter(ImageFilter.GaussianBlur(radius))


def glow(c: Canvas, mask: Image.Image, colour, core=0.9, near=1.0, far=0.55,
         white=0.55) -> np.ndarray:
    """A lit tube: a hot near-white core, a tight halo and a wide bloom."""
    core_col = tuple(min(255, v + (255 - v) * white) for v in colour)
    out = tint(mask, core_col, core)
    out += tint(blur(mask, c.p(6)), colour, near * 1.6)
    out += tint(blur(mask, c.p(26)), colour, far * 1.4)
    return out


def tube_text(c: Canvas, text, x, y, size, stroke=5.0, anchor="mm"):
    """Neon lettering: the outline of the glyphs, drawn as one tube."""
    m, d = c.mask()
    f = c.font(size)
    d.text((c.p(x), c.p(y)), text, font=f, anchor=anchor, fill=255,
           stroke_width=c.p(stroke), stroke_fill=255)
    inner, di = c.mask()
    di.text((c.p(x), c.p(y)), text, font=f, anchor=anchor, fill=255)
    inner = inner.filter(ImageFilter.MinFilter(max(3, (c.p(stroke) // 2) * 2 + 1)))
    return Image.fromarray(
        np.clip(np.asarray(m, "int16") - np.asarray(inner, "int16"), 0, 255).astype("uint8"))


def solid_text(c: Canvas, text, x, y, size, anchor="mm"):
    m, d = c.mask()
    d.text((c.p(x), c.p(y)), text, font=c.font(size), anchor=anchor, fill=255)
    return m


def gradient(c: Canvas, top, bottom, power=1.0) -> np.ndarray:
    ys = np.linspace(0, 1, c.h, dtype="float32")[:, None, None] ** power
    t = np.array(top, "float32").reshape(1, 1, 3)
    b = np.array(bottom, "float32").reshape(1, 1, 3)
    return np.broadcast_to(t + (b - t) * ys, (c.h, c.w, 3)).copy()


def vignette(c: Canvas, strength=0.55) -> np.ndarray:
    ys = np.linspace(-1, 1, c.h, dtype="float32")[:, None]
    xs = np.linspace(-1, 1, c.w, dtype="float32")[None, :]
    r = np.sqrt((xs * 0.85) ** 2 + ys ** 2)
    return (1 - strength * np.clip(r - 0.35, 0, 1) ** 1.4)[..., None]


def calm_floor(c: Canvas, arr: np.ndarray, start=0.66, keep=0.35) -> np.ndarray:
    """Dim the launcher-card third so cards read over it."""
    ys = np.linspace(0, 1, c.h, dtype="float32")
    f = np.clip((ys - start) / (1 - start), 0, 1)
    return arr * (1 - (1 - keep) * f ** 0.8)[:, None, None]


def reflect(c: Canvas, layer: np.ndarray, horizon: float, gain=0.18, smear=10):
    """A wet-pavement reflection of [layer] below [horizon] (grid y)."""
    hy = c.p(horizon)
    src = layer[max(0, 2 * hy - c.h):hy][::-1]
    out = np.zeros_like(layer)
    n = min(len(src), c.h - hy)
    if n <= 0:
        return out
    img = Image.fromarray(np.clip(src[:n], 0, 255).astype("uint8"))
    img = img.filter(ImageFilter.GaussianBlur(c.p(smear)))
    fade = np.linspace(1, 0, n, dtype="float32")[:, None, None] ** 1.5
    out[hy:hy + n] = np.asarray(img, "float32") * fade * gain
    return out


def pulse(x: float, sharp: float = 3.0) -> float:
    """0..1, peaking once per unit of [x]; [sharp] narrows the peak."""
    return ((1 + math.cos(TWO_PI * x)) / 2) ** sharp


def flicker(t: float, seed: int, dips: int = 2, depth: float = 0.8) -> float:
    """A tube that mostly holds, with [dips] brief stutters per loop.

    Stutter positions are fixed per seed, so the loop is seamless.
    """
    rnd = random.Random(seed)
    level = 1.0
    for _ in range(dips):
        at = rnd.random()
        width = 0.006 + rnd.random() * 0.01
        d = min(abs(t - at), 1 - abs(t - at))
        if d < width:
            # two quick blinks inside the stutter
            level = min(level, 1 - depth * (0.5 + 0.5 * math.cos(TWO_PI * d / width * 2)))
    # a faint mains hum, whole cycles per loop
    return level * (0.96 + 0.04 * math.sin(TWO_PI * 300 * t))


class Scene:
    """base + sum(weight_i(t) * layer_i) + dynamic(t)."""

    def __init__(self, c: Canvas):
        self.c = c
        self.base = c.blank()
        self.layers: list[tuple[np.ndarray, object]] = []
        self.dynamic = None       # callable(t) -> additive array or None
        self.still_t = 0.0        # the moment the 4K still shows

    def add(self, arr, weight):
        self.layers.append((arr, weight))

    def frame(self, t: float) -> np.ndarray:
        out = self.base.copy()
        for arr, weight in self.layers:
            w = weight(t) if callable(weight) else weight
            if w:
                out += arr * w
        if self.dynamic is not None:
            extra = self.dynamic(t)
            if extra is not None:
                out += extra
        return out


def particles(c: Canvas, n, seed, region, colour, size=2.2, gain=1.0,
              drift=(40, 18), rise=0.0, blur_r=1.2):
    """Dust motes that wander on whole-number cycles. Returns fn(t)->array."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = region
    motes = []
    for _ in range(n):
        motes.append((
            rnd.uniform(x0, x1), rnd.uniform(y0, y1),
            rnd.randint(1, 2), rnd.random(), rnd.randint(1, 3), rnd.random(),
            rnd.uniform(0.3, 1.0), rnd.randint(2, 5), rnd.random(),
            rnd.uniform(0.6, 1.4),
        ))
    col = np.array(colour, "float32")

    def draw(t: float, weight_at=None):
        m, d = c.mask()
        for (bx, by, fx, px, fy, py, bright, ft, pt, sz) in motes:
            x = bx + drift[0] * math.sin(TWO_PI * (fx * t + px))
            y = by + drift[1] * math.sin(TWO_PI * (fy * t + py))
            if rise:
                y = y0 + ((by - y0 - rise * (y1 - y0) * t * fy) % (y1 - y0))
            tw = 0.55 + 0.45 * math.sin(TWO_PI * (ft * t + pt))
            if weight_at is not None:
                tw *= weight_at(x, y)
            v = int(255 * bright * tw)
            if v <= 4:
                continue
            r = c.s * size * sz
            d.ellipse((c.p(x) - r, c.p(y) - r, c.p(x) + r, c.p(y) + r), fill=v)
        m = blur(m, max(0.6, c.s * blur_r))
        return np.asarray(m, "float32")[..., None] / 255.0 * col * gain

    return draw


# --------------------------------------------------------------------------
# Shared set pieces
# --------------------------------------------------------------------------

def brick_wall(c: Canvas, x0, y0, x1, y1, colour, seed, row=34, col=78, gain=1.0):
    """A dim brick facade: mortar lines darker than the brick faces."""
    rnd = random.Random(seed)
    m, d = c.mask()
    y = y0
    r = 0
    while y < y1:
        off = (col / 2) * (r % 2)
        x = x0 - off
        while x < x1:
            v = int(255 * (0.55 + 0.45 * rnd.random()))
            d.rectangle(c.box(max(x0, x + 3), y + 3, min(x1, x + col - 3), min(y1, y + row - 3)),
                        fill=v)
            x += col
        y += row
        r += 1
    return tint(blur(m, c.p(1.2)), colour, gain)


def bulb_ring(c: Canvas, box, spacing, groups=3, radius=6.5):
    """Bulbs around a rectangle, split into [groups] chase phases.

    Returns (list of per-group glow layers, list of per-group bulb-body layers).
    """
    x0, y0, x1, y1 = box
    pts = []
    per = 2 * ((x1 - x0) + (y1 - y0))
    n = int(per // spacing)
    for i in range(n):
        dist = i * per / n
        if dist < x1 - x0:
            pts.append((x0 + dist, y0))
        elif dist < (x1 - x0) + (y1 - y0):
            pts.append((x1, y0 + dist - (x1 - x0)))
        elif dist < 2 * (x1 - x0) + (y1 - y0):
            pts.append((x1 - (dist - (x1 - x0) - (y1 - y0)), y1))
        else:
            pts.append((x0, y1 - (dist - 2 * (x1 - x0) - (y1 - y0))))
    return bulb_layers(c, pts, groups, radius)


def bulb_layers(c: Canvas, pts, groups, radius):
    lit, sockets = [], c.mask()
    for g in range(groups):
        m, d = c.mask()
        for i, (x, y) in enumerate(pts):
            if i % groups == g:
                r = c.p(radius)
                d.ellipse((c.p(x) - r, c.p(y) - r, c.p(x) + r, c.p(y) + r), fill=255)
        lit.append(glow(c, m, BULB, core=1.0, near=0.9, far=0.45, white=0.7))
    sm, sd = sockets
    for (x, y) in pts:
        r = c.p(radius * 1.25)
        sd.ellipse((c.p(x) - r, c.p(y) - r, c.p(x) + r, c.p(y) + r), fill=255)
    return lit, tint(sm, (60, 48, 38), 0.6)


def chase(g: int, groups: int, cycles: int, floor=0.18):
    """Weight fn for chase group [g]: one lit group runs round the ring."""
    return lambda t: floor + (1 - floor) * pulse(cycles * t - g / groups, 2.2)


# --------------------------------------------------------------------------
# The six scenes
# --------------------------------------------------------------------------

def scene_marquee(c: Canvas) -> Scene:
    sc = Scene(c)
    sc.base = gradient(c, VOID, (10, 12, 20))
    sc.base += brick_wall(c, 0, 0, BW, 720, (40, 28, 36), 97, gain=0.55)
    # facade block
    m, d = c.mask()
    d.rectangle(c.box(200, 170, 1720, 720), fill=255)
    fa = np.asarray(m, "float32")[..., None] / 255
    sc.base = sc.base * (1 - fa * 0.65) + tint(m, (14, 16, 26))
    # the canopy the board hangs on
    m, d = c.mask()
    d.polygon([(c.p(150), c.p(300)), (c.p(1770), c.p(300)), (c.p(1700), c.p(560)),
               (c.p(220), c.p(560))], fill=255)
    sc.base += tint(m, (22, 20, 28))
    # the letter board: warm backlit glass, black programme letters
    panel, pd = c.mask()
    pd.rectangle(c.box(300, 330, 1620, 530), fill=255)
    lit_panel = tint(blur(panel, c.p(2)), (238, 222, 186), 0.62)
    lit_panel += tint(blur(panel, c.p(40)), (255, 200, 140), 0.25)
    ink = np.maximum(np.asarray(solid_text(c, "NOW SHOWING", 960, 392, 74), "float32"),
                     np.asarray(solid_text(c, "THE LATE SHOW  \u00b7  DOUBLE FEATURE", 960, 478, 50),
                                "float32"))[..., None] / 255
    pa = np.asarray(blur(panel, c.p(2)), "float32")[..., None] / 255
    sc.base = sc.base * (1 - pa) + lit_panel * (1 - ink * 0.92)
    # bulbs around the board, three-phase chase, 30 laps a loop
    lit, sockets = bulb_ring(c, (262, 300, 1658, 560), 30, 3, 8)
    sc.base += sockets
    for g, layer in enumerate(lit):
        sc.add(layer, chase(g, 3, 30))
    # the neon name above the canopy
    name = tube_text(c, "CINEMA", 960, 175, 170, stroke=6)
    sc.add(glow(c, name, CYAN, far=0.7), lambda t: flicker(t, 971, dips=2))
    under, ud = c.mask()
    ud.rounded_rectangle(c.box(600, 272, 1320, 283), radius=c.p(5), fill=255)
    sc.add(glow(c, under, FLAME, far=0.5), lambda t: flicker(t, 972, dips=1, depth=0.6))
    # glass doors with warm foyer light behind them
    m, d = c.mask()
    for x in (700, 880, 1060):
        d.rectangle(c.box(x + 6, 596, x + 154, 718), fill=255)
    dy = np.clip((np.linspace(0, BH, c.h, dtype="float32")[:, None, None] - 596) / 122, 0, 1)
    glass = np.asarray(blur(m, c.p(2)), "float32")[..., None] / 255
    sc.base = sc.base * (1 - glass) + glass * np.array((70, 48, 34), "float32") * (1.1 - 0.7 * dy)
    sc.base += tint(blur(m, c.p(50)), (255, 170, 110), 0.12)
    frames, fd = c.mask()
    for x in (700, 880, 1060):
        fd.rectangle(c.box(x, 590, x + 160, 720), outline=255, width=c.p(6))
    sc.base = sc.base * (1 - np.asarray(frames, "float32")[..., None] / 255 * 0.85)
    # wet pavement: reflect the lights
    lights = sum(layer for layer, _ in sc.layers)
    sc.add(reflect(c, lights + lit_panel, 722, gain=0.16), 1.0)
    sc.base = calm_floor(c, sc.base * vignette(c, 0.5))
    sc.still_t = 0.0
    return sc


def scene_velvet(c: Canvas) -> Scene:
    sc = Scene(c)
    xs = np.linspace(0, 1, c.w, dtype="float32")[None, :]
    ys = np.linspace(0, 1, c.h, dtype="float32")[:, None]
    folds = (0.55 + 0.25 * np.sin(xs * TWO_PI * 11.0 + 0.6)
             + 0.12 * np.sin(xs * TWO_PI * 27.0 + 1.9)
             + 0.08 * np.sin(xs * TWO_PI * 5.0))
    folds = np.clip(folds, 0, 1) ** 1.6
    velvet = np.array((118, 18, 34), "float32")
    shade = folds * (0.35 + 0.65 * (1 - ys) ** 0.4)
    sc.base = shade[..., None] * velvet
    # the valance: a row of swags across the top
    m, d = c.mask()
    for i in range(6):
        x0 = i * 320
        d.chord(c.box(x0 - 20, -120, x0 + 340, 190), 0, 180, fill=255)
    swag = np.asarray(blur(m, c.p(2)), "float32")[..., None] / 255
    sc.base = sc.base * (1 - swag) + swag * np.array((70, 10, 22), "float32")
    fr, fd = c.mask()
    for i in range(6):
        x0 = i * 320
        fd.arc(c.box(x0 - 20, -120, x0 + 340, 190), 0, 180, fill=255, width=c.p(5))
    sc.base += tint(fr, (190, 140, 70), 0.6)          # gold fringe
    # stage lip and footlight row (kept dim: this is the card zone)
    m, d = c.mask()
    d.rectangle(c.box(0, 820, BW, BH), fill=255)
    sc.base = sc.base * (1 - np.asarray(m, "float32")[..., None] / 255 * 0.85)
    pts = [(80 + i * 118, 830) for i in range(16)]
    lit, _ = bulb_layers(c, pts, 2, 5)
    for g, layer in enumerate(lit):
        sc.add(layer * 0.5, chase(g, 2, 6, floor=0.55))
    # two follow-spots breathing in counterpoint, drifting on the curtain
    gx = np.linspace(0, BW, c.w, dtype="float32")[None, :]
    gy = np.linspace(0, BH, c.h, dtype="float32")[:, None]
    warm = np.array((255, 190, 150), "float32")
    motes = particles(c, 140, 981, (300, 200, 1620, 800), (255, 214, 170),
                      size=1.3, gain=0.9, drift=(60, 40))

    def spots(t):
        out = np.zeros((c.h, c.w, 3), "float32")
        field = np.zeros((c.h, c.w), "float32")
        for k, (cx, ph) in enumerate(((680, 0.0), (1240, 0.5))):
            x = cx + 70 * math.sin(TWO_PI * (t + ph))
            y = 470 + 30 * math.sin(TWO_PI * (2 * t + ph))
            a = 0.55 + 0.45 * pulse(t + ph, 1.0)
            field += a * np.exp(-(((gx - x) / 260) ** 2 + ((gy - y) / 320) ** 2))
        out += field[..., None] * warm * 0.42
        out += field[..., None] * sc.base * 1.6          # the velvet lights up
        out += motes(t) * 0.55
        return out

    sc.dynamic = spots
    sc.base = calm_floor(c, sc.base * vignette(c, 0.4), start=0.72, keep=0.45)
    return sc


def scene_projector(c: Canvas) -> Scene:
    sc = Scene(c)
    sc.base = gradient(c, (6, 8, 16), (3, 4, 9))
    # the screen, far right, lit by the film
    m, d = c.mask()
    d.polygon([(c.p(1180), c.p(170)), (c.p(1820), c.p(110)), (c.p(1820), c.p(600)),
               (c.p(1180), c.p(540))], fill=255)
    screen_mask = np.asarray(blur(m, c.p(1.5)), "float32")[..., None] / 255
    sx = np.linspace(0, 1, c.w, dtype="float32")[None, :]
    sy = np.linspace(0, 1, c.h, dtype="float32")[:, None]
    sc.base += tint(blur(m, c.p(40)), (40, 60, 90), 0.35)
    # booth window, top left
    port, pd = c.mask()
    pd.rounded_rectangle(c.box(150, 150, 230, 205), radius=c.p(8), fill=255)
    sc.add(glow(c, port, (255, 240, 220), core=1.0, near=1.0, far=0.6, white=0.8), 1.0)
    # the beam: a cone from the port to the screen, soft-edged
    beam, bd = c.mask()
    bd.polygon([(c.p(215), c.p(165)), (c.p(215), c.p(195)),
                (c.p(1180), c.p(540)), (c.p(1180), c.p(170))], fill=255)
    beam = blur(beam, c.p(14))
    gx = np.linspace(0, 1, c.w, dtype="float32")[None, :]
    along = np.clip((gx * BW - 215) / (1180 - 215), 0, 1)
    beam_a = np.asarray(beam, "float32") / 255 * (1 - 0.55 * along)
    beam_layer = beam_a[..., None] * np.array((150, 175, 200), "float32") * 0.42
    motes = particles(c, 520, 991, (230, 160, 1180, 545), (230, 240, 255),
                      size=1.2, gain=1.3, drift=(90, 26))
    beam_img = beam_a

    def beam_weight(x, y):
        px = min(c.w - 1, max(0, c.p(x)))
        py = min(c.h - 1, max(0, c.p(y)))
        return float(beam_img[py, px]) * 1.4

    # seat rows: silhouettes rim-lit by the screen (kept low and dark)
    m, d = c.mask()
    for row, y in enumerate((760, 850, 950)):
        for i in range(-1, 26):
            x = i * 84 + (42 if row % 2 else 0)
            d.rounded_rectangle(c.box(x, y, x + 70, y + 140), radius=c.p(26), fill=255)
    seats = np.asarray(m, "float32")[..., None] / 255
    sc.base = sc.base * (1 - seats * 0.8)
    rim = m.filter(ImageFilter.FIND_EDGES)
    sc.base += tint(blur(rim, c.p(1.5)), (60, 90, 130), 0.35)
    film = [np.array(v, "float32") for v in (CYAN, VIOLET, (220, 190, 150), BUILD_BLUE, FLAME)]

    def show(t):
        # the film: slow colour changes on whole cycles, 24 fps shutter hum
        k = t * len(film)
        i = int(k) % len(film)
        f = k - int(k)
        e = (1 - math.cos(math.pi * f)) / 2
        col = film[i] * (1 - e) + film[(i + 1) % len(film)] * e
        shutter = 0.94 + 0.06 * math.sin(TWO_PI * 24 * DURATION * t)
        # soft moving light on the screen: two blobs orbiting on whole cycles
        bx = 0.78 + 0.08 * math.sin(TWO_PI * t)
        by = 0.3 + 0.1 * math.sin(TWO_PI * 2 * t + 1)
        blob = np.exp(-(((sx - bx) / 0.12) ** 2 + ((sy - by) / 0.2) ** 2))[..., None]
        out = screen_mask * (col * (0.16 + 0.34 * blob) + 10 + 30 * blob) * shutter
        out = out + beam_layer * shutter
        out += motes(t, beam_weight)
        return out

    sc.dynamic = show
    sc.base = calm_floor(c, sc.base * vignette(c, 0.45), start=0.7, keep=0.5)
    sc.still_t = 0.1
    return sc


def scene_lounge(c: Canvas) -> Scene:
    sc = Scene(c)
    sc.base = gradient(c, (10, 8, 18), (5, 4, 10))
    sc.base += brick_wall(c, 0, 0, BW, 820, (46, 30, 58), 1001, row=30, col=70, gain=0.5)
    word = tube_text(c, "LOUNGE", 800, 300, 190, stroke=6)
    sc.add(glow(c, word, ORCHID, far=0.75), lambda t: flicker(t, 1002, dips=2))
    late = tube_text(c, "OPEN LATE", 800, 470, 64, stroke=3.5)
    sc.add(glow(c, late, FLAME, far=0.5), lambda t: flicker(t, 1003, dips=3, depth=0.9))
    # a cocktail glass in cyan tubing, the olive blinking
    gm, gd = c.mask()
    w = c.p(7)
    gd.line([(c.p(1370), c.p(190)), (c.p(1590), c.p(190)), (c.p(1480), c.p(330)),
             (c.p(1370), c.p(190))], fill=255, width=w, joint="curve")
    gd.line([(c.p(1480), c.p(330)), (c.p(1480), c.p(450))], fill=255, width=w)
    gd.line([(c.p(1420), c.p(455)), (c.p(1540), c.p(455))], fill=255, width=w)
    sc.add(glow(c, gm, CYAN, far=0.65), lambda t: flicker(t, 1004, dips=1))
    om, od = c.mask()
    od.ellipse(c.box(1500, 212, 1530, 242), fill=255)
    sc.add(glow(c, om, (140, 230, 90), far=0.4), lambda t: 0.25 + 0.75 * (math.sin(TWO_PI * 10 * t) > 0))
    # a low sofa silhouette with a lamp glow beside it
    m, d = c.mask()
    d.rounded_rectangle(c.box(420, 760, 1260, 900), radius=c.p(40), fill=255)
    d.rounded_rectangle(c.box(460, 690, 1220, 800), radius=c.p(40), fill=255)
    sofa = np.asarray(m, "float32")[..., None] / 255
    sc.base = sc.base * (1 - sofa * 0.85)
    lamp, ld = c.mask()
    ld.ellipse(c.box(1340, 560, 1560, 780), fill=255)
    sc.add(tint(blur(lamp, c.p(90)), (255, 170, 100), 0.3), lambda t: 0.9 + 0.1 * pulse(t, 1))
    bokeh = particles(c, 26, 1005, (0, 60, BW, 700), (200, 140, 230), size=10,
                      gain=0.22, drift=(120, 30), blur_r=6)
    sc.dynamic = lambda t: bokeh(t)
    sc.base = calm_floor(c, sc.base * vignette(c, 0.45))
    return sc


def scene_rentals(c: Canvas) -> Scene:
    sc = Scene(c)
    rnd = random.Random(1011)
    sc.base = gradient(c, (8, 10, 18), (4, 5, 10))
    sc.base += brick_wall(c, 0, 0, BW, 760, (36, 30, 40), 1011, gain=0.5)
    # two shop windows with shelves of tape spines behind the glass
    spines = [np.array(v, "float32") for v in (CYAN, VIOLET, EMBER, BUILD_BLUE,
                                                 (200, 170, 90), (90, 160, 120))]
    shelves = Image.new("RGB", (c.w, c.h), (0, 0, 0))
    sd = ImageDraw.Draw(shelves)
    for wx0, wx1 in ((180, 860), (1060, 1740)):
        m, d = c.mask()
        d.rectangle(c.box(wx0, 230, wx1, 700), fill=255)
        win = np.asarray(m, "float32")[..., None] / 255
        sc.base = sc.base * (1 - win) + win * np.array((16, 18, 30), "float32")
        for shelf_y in (390, 480, 570):
            x = wx0 + 20
            while x < wx1 - 20:
                wdt = rnd.randint(12, 20)
                col = spines[rnd.randrange(len(spines))] * rnd.uniform(0.18, 0.34)
                sd.rectangle(c.box(x, shelf_y, x + wdt - 3, shelf_y + 74),
                             fill=tuple(int(v) for v in col))
                x += wdt
        frame, fd = c.mask()
        fd.rectangle(c.box(wx0, 230, wx1, 700), outline=255, width=c.p(6))
        sc.base += tint(frame, (30, 34, 48))
    sc.base += np.asarray(shelves, "float32")
    # window neons
    open_ = tube_text(c, "OPEN", 520, 305, 110, stroke=5)
    sc.add(glow(c, open_, FLAME, far=0.7), lambda t: flicker(t, 1012, dips=3, depth=0.95))
    rent = tube_text(c, "RENTALS", 1400, 280, 88, stroke=4.5)
    sc.add(glow(c, rent, CYAN, far=0.7), lambda t: flicker(t, 1013, dips=2))
    tape = tube_text(c, "VHS \u00b7 2 NIGHTS", 1400, 352, 34, stroke=2.2)
    sc.add(glow(c, tape, ORCHID, far=0.5), lambda t: flicker(t, 1014, dips=1))
    # rain across the glass: streaks on whole-number falls per loop
    drops = [(rnd.uniform(0, BW), rnd.random(), rnd.randint(3, 6), rnd.uniform(0.3, 1.0))
             for _ in range(240)]
    lights = sum(layer for layer, _ in sc.layers)
    sc.add(reflect(c, lights, 740, gain=0.14), 1.0)

    def rain(t):
        m, d = c.mask()
        for x, ph, falls, b in drops:
            y = ((ph + falls * t) % 1.0) * (BH + 120) - 60
            d.line([(c.p(x), c.p(y)), (c.p(x - 6), c.p(y + 46))],
                   fill=int(120 * b), width=max(1, c.p(1.6)))
        return tint(blur(m, c.s * 0.8), (170, 200, 230), 0.55)

    sc.dynamic = rain
    sc.base = calm_floor(c, sc.base * vignette(c, 0.45))
    return sc


def scene_box_office(c: Canvas) -> Scene:
    sc = Scene(c)
    sc.base = gradient(c, VOID, (9, 10, 18))
    sc.base += brick_wall(c, 0, 0, BW, 740, (36, 26, 30), 1021, gain=0.45)
    # the booth
    m, d = c.mask()
    d.rounded_rectangle(c.box(640, 330, 1280, 740), radius=c.p(18), fill=255)
    sc.base = sc.base * (1 - np.asarray(m, "float32")[..., None] / 255 * 0.7) + tint(m, (20, 22, 34))
    win, wd = c.mask()
    wd.rounded_rectangle(c.box(720, 420, 1200, 600), radius=c.p(90), fill=255)
    sc.base += tint(blur(win, c.p(3)), (230, 180, 120), 0.42)
    sc.base += tint(blur(win, c.p(60)), (255, 170, 100), 0.16)
    clerk, cd = c.mask()
    cd.ellipse(c.box(915, 455, 1005, 545), fill=255)
    cd.rounded_rectangle(c.box(860, 530, 1060, 640), radius=c.p(50), fill=255)
    wa = np.asarray(win, "float32")[..., None] / 255
    ca = np.asarray(blur(clerk, c.p(2)), "float32")[..., None] / 255 * wa
    sc.base = sc.base * (1 - ca * 0.85)
    grille, gd = c.mask()
    for gx in range(760, 1200, 40):
        gd.line([(c.p(gx), c.p(420)), (c.p(gx), c.p(600))], fill=255, width=c.p(3))
    sc.base = sc.base * (1 - np.asarray(grille, "float32")[..., None] / 255 * wa * 0.5)
    tickets = tube_text(c, "TICKETS", 960, 250, 130, stroke=5)
    sc.add(glow(c, tickets, CYAN, far=0.75), lambda t: flicker(t, 1022, dips=2))
    # a bulb arrow pointing at the booth, chasing toward it
    pts = [(160 + i * 34, 560) for i in range(12)]
    pts += [(530 - i * 30, 560 - i * 30) for i in range(1, 5)]
    pts += [(530 - i * 30, 560 + i * 30) for i in range(1, 5)]
    lit, sockets = bulb_layers(c, pts[:12], 4, 7)
    sc.base += sockets
    for g, layer in enumerate(lit):
        sc.add(layer, chase(g, 4, 24, floor=0.12))
    head, _ = bulb_layers(c, pts[12:], 1, 7)
    sc.add(head[0], lambda t: 0.3 + 0.7 * pulse(24 * t / 4, 1.5))
    # popcorn box in ember tubing on the right
    pm, pd = c.mask()
    w = c.p(6)
    pd.line([(c.p(1470), c.p(420)), (c.p(1500), c.p(640)), (c.p(1640), c.p(640)),
             (c.p(1670), c.p(420)), (c.p(1470), c.p(420))], fill=255, width=w, joint="curve")
    for sx in (1520, 1570, 1620):
        pd.line([(c.p(sx), c.p(430)), (c.p(sx + 4), c.p(630))], fill=255, width=c.p(4))
    sc.add(glow(c, pm, FLAME, far=0.55), lambda t: flicker(t, 1023, dips=1))
    km, kd = c.mask()
    for i, (kx, ky) in enumerate(((1490, 395), (1535, 375), (1585, 385), (1630, 372), (1660, 398))):
        kd.ellipse(c.box(kx - 26, ky - 26, kx + 26, ky + 26), outline=255, width=c.p(4))
    sc.add(glow(c, km, BULB, far=0.35, white=0.8), lambda t: flicker(t, 1024, dips=1))
    stars = particles(c, 60, 1025, (0, 0, BW, 180), (220, 230, 255), size=1.4,
                      gain=0.6, drift=(4, 2))
    lights = sum(layer for layer, _ in sc.layers)
    sc.add(reflect(c, lights, 745, gain=0.15), 1.0)
    sc.dynamic = lambda t: stars(t)
    sc.base = calm_floor(c, sc.base * vignette(c, 0.45))
    return sc


SCENES = {
    97: scene_marquee,
    98: scene_velvet,
    99: scene_projector,
    100: scene_lounge,
    101: scene_rentals,
    102: scene_box_office,
}


# --------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------

def to_image(arr: np.ndarray) -> Image.Image:
    # soft shoulder instead of a hard clip, so bright neon cores roll off
    a = arr / 255.0
    a = np.where(a > 0.8, 0.8 + 0.2 * np.tanh((a - 0.8) / 0.2), a)
    return Image.fromarray(np.clip(a * 255 + 0.5, 0, 255).astype("uint8"))


def ffmpeg() -> str:
    try:
        import imageio_ffmpeg  # type: ignore
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"


def build_still(number: int, slug: str) -> Path:
    sc = SCENES[number](Canvas(2.0))
    img = to_image(sc.frame(sc.still_t))
    stem = f"corebuilds-{number}-{slug}"
    save_wall(img, SERIES, stem)
    return SERIES / f"{stem}.png"


def build_loop(index: int, number: int, slug: str) -> Path:
    name = clip_name(index, slug)
    out = LIVE / f"{name}.mp4"
    thumb = LIVE / "thumbs" / f"{name}.jpg"
    thumb.parent.mkdir(parents=True, exist_ok=True)
    sc = SCENES[number](Canvas(1.0))
    cmd = [ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
           "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{BW}x{BH}",
           "-r", str(FPS), "-i", "-",
           "-c:v", "libx264", "-preset", "slow", "-crf", str(CRF),
           "-g", str(FPS * 2), "-pix_fmt", "yuv420p", "-movflags", "+faststart",
           "-an", str(out)]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    assert proc.stdin is not None
    poster = None
    # Reap ffmpeg on every path, and never leave a half-written MP4 behind:
    # a failed rerun must not sit in Motion/live/ beside its old thumbnail.
    ok = False
    try:
        for f in range(FRAMES):
            img = to_image(sc.frame(f / FRAMES))
            if f == FPS:
                poster = img
            proc.stdin.write(img.tobytes())
        ok = True
    finally:
        try:
            proc.stdin.close()
        except OSError:
            pass
        rc = proc.wait()
        if not ok or rc != 0:
            out.unlink(missing_ok=True)
    if rc != 0:
        raise SystemExit(f"ffmpeg failed on {name}")
    assert poster is not None
    poster.resize((THUMB_W, THUMB_H), Image.LANCZOS).save(thumb, quality=88)
    print(f"  ✓ {out.relative_to(ROOT)}  {out.stat().st_size / 1e6:.1f} MB")
    return out


def contact_sheet() -> None:
    tiles = [Image.open(ROOT / "Wallpapers" / "thumbs" / f"corebuilds-{n}-{s}.jpg")
             for n, s, _ in WALLS]
    cols = 3
    sheet = Image.new("RGB", (cols * THUMB_W, 2 * THUMB_H), VOID)
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * THUMB_W, (i // cols) * THUMB_H))
    sheet.save(CONTACT_SHEET, optimize=True)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    ap.add_argument("--only", type=int, action="append")
    ap.add_argument("--stills", action="store_true", help="stills only")
    ap.add_argument("--loops", action="store_true", help="loops only")
    args = ap.parse_args(argv)
    for i, (number, slug, _title) in enumerate(WALLS):
        if args.only and number not in args.only:
            continue
        if not args.loops:
            build_still(number, slug)
        if not args.stills:
            build_loop(i, number, slug)
    if not args.loops and not args.only:
        contact_sheet()
    return 0


if __name__ == "__main__":
    sys.exit(main())
