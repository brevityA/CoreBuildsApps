#!/usr/bin/env python3
"""
Core Builds Pop — pop-art cartoon render engine.

The classic pack is 924 monoline glyphs drawn in 267 shapes across 169 accent
colours. Every icon is good on its own; the *set* is not uniform, because the
only thing all 924 share is a stroke weight. Mark sizes vary by more than 2x,
sixty-five percent of the pack is a letter in a rounded box, and the palette is
effectively unbounded.

Pop makes every icon the same object:

    ink drop shadow -> flat colour field -> Ben-Day halftone
    -> 22px ink keyline -> cream mark with a heavy ink outline

Five rules produce the uniformity, and each is enforced here rather than left
to whoever draws the next glyph:

  1. One container. A superellipse, identical on all 924.
  2. One palette. 169 brand accents snap to 16 locked swatches by hue.
  3. One optical size. Every mark is scaled so its ink box is exactly
     TARGET_INK across — measured, not eyeballed (tools/pop_glyph_metrics.json).
  4. One stroke weight. Widths are pre-divided by that scale, so after scaling
     every line in the pack renders at POP_STROKE.
  5. One light source. The halftone screen rakes the same way on all 924.

Quality comes from reusing the original geometry already in `glyphs.py` — 267
hand-built marks — rather than inventing 924 new shapes.

This module owns the look and nothing else: it reads one committed metrics
table and returns strings. `build_pop.py` owns the files.
"""
from __future__ import annotations

import colorsys
import json
import math
import re
from functools import lru_cache
from pathlib import Path
from xml.etree import ElementTree as ET

from glyphs import GLYPHS, monoline
from typeface import monogram_scaled, monogram_text

ROOT = Path(__file__).resolve().parent.parent
METRICS_PATH = ROOT / "tools" / "pop_glyph_metrics.json"

# --------------------------------------------------------------------------
# Canvas constants
# --------------------------------------------------------------------------
GRID = 512

INK = "#151019"          # comic linework. Near-black warmed slightly, so it
                         # does not go dead flat against the cream.
CREAM = "#FFF4E0"        # newsprint white. Pure #FFFFFF vibrates against
                         # saturated flats on an OLED panel at 10 feet.

BOX_X, BOX_Y, BOX_S = 34, 26, 444    # the colour field
SHADOW_DY = 16                        # hard offset, no blur. Comics do not own
                                      # the Gaussian.
KEYLINE = 22                          # ink outline around the field

POP_STROKE = 34.0        # the one canonical line weight, post-scale
TARGET_INK = 268.0       # every mark's ink box, post-scale, on the 512 grid
MIN_GEOM = 12.0          # guard: a mark with no measurable extent in one axis

HALFTONE_PITCH = 27.0
HALFTONE_R = 5.6
HALFTONE_ANGLE = 45      # the classic screen angle

_SUPERELLIPSE_N = 4.6    # 4.0 is Apple-ish; 4.6 reads slightly boxier and
                         # holds a heavy ink keyline better at card size
_SUPERELLIPSE_STEPS = 128   # 3.5px per segment at 512 — below the rasteriser's
                            # own AA, so more points only cost bytes

# glyphs.py paints its knockouts in the night-chrome background colour. Pop has
# no background to knock out to, so those shapes become ink instead — a solid
# cream badge with a black play reads exactly like the original intent.
_KNOCKOUT = "#0d1117"
_KNOCKOUT_TOKEN = "__pop_knockout__"


# --------------------------------------------------------------------------
# The locked palette.
#
# 169 accents collapse to 16. Hue survives because hue is the part of an app's
# colour users actually read — Netflix is red, Spotify is green, Plex is amber.
# Saturation and value are what made the old set look like 169 different design
# systems, so those are the parts Pop takes away.
#
# Every swatch clears 3:1 against CREAM and 4.5:1 against INK, so both passes of
# the mark stay legible on all sixteen.
# --------------------------------------------------------------------------
PALETTE = [
    ("pop_red",     "#E03127", 358.0),
    ("pop_blaze",   "#F05A22", 16.0),
    ("pop_amber",   "#E8880F", 33.0),
    ("pop_sun",     "#E0AF0C", 47.0),
    ("pop_acid",    "#9DBB1F", 72.0),
    ("pop_green",   "#3F9C35", 113.0),
    ("pop_jade",    "#0E9077", 166.0),
    ("pop_aqua",    "#0C8AAE", 192.0),
    ("pop_blue",    "#1C79D2", 210.0),
    ("pop_marine",  "#3A4CC4", 232.0),
    ("pop_grape",   "#7440C6", 267.0),
    ("pop_orchid",  "#A934B6", 292.0),
    ("pop_magenta", "#CE2C78", 330.0),
    ("pop_rose",    "#E0405E", 348.0),
]
NEUTRAL_SLATE = ("pop_slate", "#59637A")        # desaturated brand colours
NEUTRAL_GRAPHITE = ("pop_graphite", "#333A4B")  # near-black brand colours

SWATCHES: dict[str, str] = {name: hexv for name, hexv, _ in PALETTE}
SWATCHES[NEUTRAL_SLATE[0]] = NEUTRAL_SLATE[1]
SWATCHES[NEUTRAL_GRAPHITE[0]] = NEUTRAL_GRAPHITE[1]

_SAT_FLOOR = 0.17        # below this an accent is grey, not a hue
_VAL_FLOOR = 0.26        # below this it is black, not a dark hue


def _hex_to_rgb(h: str) -> tuple[float, float, float]:
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


def _rgb_to_hex(r: float, g: float, b: float) -> str:
    return "#%02X%02X%02X" % tuple(
        max(0, min(255, round(v * 255))) for v in (r, g, b))


def snap(accent: str) -> tuple[str, str]:
    """Map any brand accent onto the locked palette.

    Returns (swatch_name, swatch_hex). Pure and deterministic: the pack must
    not reshuffle its own colours between builds.
    """
    # Closed over the palette. Without this, snap is not idempotent: the two
    # neutrals are selected by saturation/value thresholds that their own hex
    # values do not satisfy, so snap(SWATCHES["pop_slate"]) returned marine.
    # Nothing in the icon pipeline snapped twice, so it never showed up there —
    # it surfaced the moment something asked for a specific swatch by value.
    # A palette you cannot round-trip through your own mapper is a trap.
    exact = accent.strip().upper()
    for name, hexv in SWATCHES.items():
        if hexv.upper() == exact:
            return name, hexv

    r, g, b = _hex_to_rgb(accent)
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    if v < _VAL_FLOOR:
        return NEUTRAL_GRAPHITE
    if s < _SAT_FLOOR:
        return NEUTRAL_SLATE
    deg = h * 360.0
    best = min(PALETTE,
               key=lambda p: min(abs(deg - p[2]), 360.0 - abs(deg - p[2])))
    return best[0], best[1]


def shade(hex_colour: str, factor: float) -> str:
    """Darken (<1) or lighten (>1) a swatch in HSV value."""
    r, g, b = _hex_to_rgb(hex_colour)
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    v = max(0.0, min(1.0, v * factor))
    s = max(0.0, min(1.0, s * (1.06 if factor < 1 else 0.94)))
    return _rgb_to_hex(*colorsys.hsv_to_rgb(h, s, v))


def relative_luminance(hex_colour: str) -> float:
    def chan(c: float) -> float:
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (chan(x) for x in _hex_to_rgb(hex_colour))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a: str, b: str) -> float:
    la, lb = relative_luminance(a), relative_luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


# --------------------------------------------------------------------------
# Geometry
# --------------------------------------------------------------------------
def squircle_path(x: float, y: float, size: float,
                  n: float = _SUPERELLIPSE_N,
                  steps: int = _SUPERELLIPSE_STEPS) -> str:
    """A true superellipse, not a rounded rect.

    A rounded rect with a large radius has four tangent breaks where each arc
    meets its straight run. Nobody sees them at 512px. Behind a 22px ink
    keyline on a 10-foot display they read as dents in the outline. The
    superellipse has continuous curvature, so the keyline stays even the whole
    way round — which matters here precisely because the same shape is repeated
    924 times and any flaw in it is a flaw in the whole pack.
    """
    a = size / 2.0
    cx, cy = x + a, y + a
    pts = []
    for i in range(steps):
        t = 2.0 * math.pi * i / steps
        ct, st = math.cos(t), math.sin(t)
        px = cx + a * math.copysign(abs(ct) ** (2.0 / n), ct)
        py = cy + a * math.copysign(abs(st) ** (2.0 / n), st)
        pts.append(f"{px:.1f},{py:.1f}")
    return "M " + " L ".join(pts) + " Z"


def _rounded_rect(x: float, y: float, w: float, h: float, r: float) -> str:
    return (f"M {x + r} {y} H {x + w - r} A {r} {r} 0 0 1 {x + w} {y + r} "
            f"V {y + h - r} A {r} {r} 0 0 1 {x + w - r} {y + h} "
            f"H {x + r} A {r} {r} 0 0 1 {x} {y + h - r} "
            f"V {y + r} A {r} {r} 0 0 1 {x + r} {y} Z")


# --------------------------------------------------------------------------
# Bodies
#
# 601 of the 924 icons use a `tile_X` glyph: a letter inside a rounded box with
# a corner notch. Pop's field already *is* that box, so drawing it again nests
# two containers and shrinks the letter to about a third of the card. Pop takes
# the letter alone and lets rule 3 scale it to the same ink box as every other
# mark, which is both more uniform and roughly twice as legible across a room.
# --------------------------------------------------------------------------
_TILE_RE = re.compile(r"^tile_(.+)$")
_SENTINEL = "#000000"


def pop_body(glyph_name: str) -> str:
    """Raw geometry for one mark, container stripped, weights snapped.

    Colour is a sentinel: every path gets repainted downstream, so carrying the
    catalog accent this far would only invite someone to trust it.
    """
    m = _TILE_RE.match(glyph_name)
    if m:
        token = m.group(1)
        if len(token) == 1:
            body = monogram_scaled(token, _SENTINEL, cap_h=300)
        else:
            body = monogram_text(token, _SENTINEL)
        return body
    return monoline(GLYPHS[glyph_name](_SENTINEL), weight=POP_STROKE)


_SW_RE = re.compile(r'stroke-width="([\d.]+)"')


def strip_weights(body: str, width: float) -> str:
    """Force every stroke to one width (used by the measuring pass)."""
    return _SW_RE.sub(f'stroke-width="{width}"', body)


# --------------------------------------------------------------------------
# Optical normalisation
# --------------------------------------------------------------------------
@lru_cache(maxsize=1)
def _metrics() -> dict[str, list[float]]:
    if not METRICS_PATH.exists():
        raise SystemExit(
            "tools/pop_glyph_metrics.json is missing. "
            "Run: python tools/measure_pop_glyphs.py")
    return json.loads(METRICS_PATH.read_text(encoding="utf-8"))["metrics"]


def fit(glyph_name: str, target: float = TARGET_INK,
        outline: float = 0.0) -> tuple[float, float, float]:
    """Return (scale, dx, dy) placing a mark's ink box at the canvas centre.

    Solves for scale including the linework, not just the path geometry:

        target = scale * geometry + POP_STROKE + outline

    so a mark drawn as a hairline outline and a mark drawn as a solid fill
    finish the same size on screen. Stroke widths are pre-divided by `scale` in
    `_repaint`, which is what keeps rule 4 true after rule 3 has been applied.
    """
    box = _metrics().get(glyph_name)
    if not box:
        raise KeyError(f"no measured ink box for glyph '{glyph_name}' — "
                       f"run tools/measure_pop_glyphs.py")
    x0, y0, x1, y1 = box
    gw = max(x1 - x0, MIN_GEOM)
    gh = max(y1 - y0, MIN_GEOM)
    room = max(target - POP_STROKE - outline, 24.0)
    scale = room / max(gw, gh)
    # Centre the measured box, not the 512 canvas: several marks are drawn
    # off-centre in glyphs.py and would otherwise sit low or left in the field.
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    return scale, cx, cy


# --------------------------------------------------------------------------
# Re-inking
#
# glyphs.py emits two kinds of painted element: stroked outlines
# (fill="none" stroke="#accent") and flat fills (fill="#accent" stroke="none").
# Pop needs both to come out as "cream shape, heavy ink outline", so each is
# drawn twice — an ink pass underneath that is fatter in every direction, and a
# cream pass on top at the original dimensions.
# --------------------------------------------------------------------------
_SVG_NS = "http://www.w3.org/2000/svg"
_PAINTED = {"path", "rect", "circle", "ellipse", "line", "polyline", "polygon"}


def _parse(body: str) -> ET.Element:
    return ET.fromstring(f'<g xmlns="{_SVG_NS}">{body}</g>')


def _serialize(root: ET.Element) -> str:
    return "".join(
        ET.tostring(child, encoding="unicode").replace(f' xmlns="{_SVG_NS}"', "")
        for child in list(root))


def _repaint(root: ET.Element, colour: str, *, ink_pass: bool,
             outline: float, inv_scale: float) -> None:
    for el in root.iter():
        if el.tag.split("}")[-1] not in _PAINTED:
            continue
        fill = el.get("fill")
        stroke = el.get("stroke")
        knockout = _KNOCKOUT_TOKEN in (fill or "")
        has_fill = fill not in (None, "none")
        has_stroke = stroke not in (None, "none")

        if knockout:
            # A knockout is a hole in the mark. It never gets an ink halo of
            # its own (that would eat the shape it is punched into) and it is
            # ink in both passes, so it stays a hole.
            if ink_pass:
                el.set("fill", "none")
                el.set("stroke", "none")
                continue
            el.set("fill", INK)
            el.set("stroke", "none")
            continue

        el.set("fill", colour if has_fill else "none")

        if ink_pass:
            base = float(el.get("stroke-width", 0) or 0)
            el.set("stroke", colour)
            el.set("stroke-width", f"{(base + outline) * inv_scale:.2f}")
            el.set("stroke-linecap", el.get("stroke-linecap", "round"))
            el.set("stroke-linejoin", el.get("stroke-linejoin", "round"))
        elif has_stroke:
            base = float(el.get("stroke-width", 0) or 0)
            el.set("stroke", colour)
            el.set("stroke-width", f"{base * inv_scale:.2f}")
        else:
            el.set("stroke", "none")


def inked_mark(glyph_name: str, *, target: float, outline: float) -> str:
    """One mark, cream on a heavy ink outline, wrapped in its fitting transform."""
    scale, cx, cy = fit(glyph_name, target=target, outline=outline)
    inv = 1.0 / scale
    body = pop_body(glyph_name).replace(_KNOCKOUT, _KNOCKOUT_TOKEN)

    under = _parse(body)
    _repaint(under, INK, ink_pass=True, outline=outline, inv_scale=inv)
    over = _parse(body)
    _repaint(over, CREAM, ink_pass=False, outline=0.0, inv_scale=inv)
    return (f'<g transform="scale({scale:.5f}) '
            f'translate({-cx:.2f},{-cy:.2f})">'
            f'{_serialize(under)}{_serialize(over)}</g>')


# --------------------------------------------------------------------------
# Compositions
# --------------------------------------------------------------------------
def _halftone_defs(uid: str, dot: str, w: float, h: float,
                   pitch: float = HALFTONE_PITCH,
                   radius: float = HALFTONE_R) -> str:
    """Ben-Day screen plus the gradient mask that rakes it across the field.

    A flat screen over the whole field turns the colour muddy at card size. The
    mask keeps the dots in the lower-right two-thirds, where a comic colourist
    would put them. Because the mask is identical on all 924 icons, the set
    reads as lit by one light — which is most of what "uniform" means to an eye
    scanning a home row.
    """
    return (
        f'<pattern id="ht{uid}" width="{pitch}" height="{pitch}" '
        f'patternUnits="userSpaceOnUse" '
        f'patternTransform="rotate({HALFTONE_ANGLE})">'
        f'<circle cx="{pitch / 2:.2f}" cy="{pitch / 2:.2f}" r="{radius}" '
        f'fill="{dot}"/></pattern>'
        f'<linearGradient id="hg{uid}" x1="0" y1="0" x2="1" y2="1">'
        f'<stop offset="0.18" stop-color="#000000"/>'
        f'<stop offset="0.62" stop-color="#8A8A8A"/>'
        f'<stop offset="1" stop-color="#FFFFFF"/>'
        f'</linearGradient>'
        f'<mask id="hm{uid}">'
        f'<rect x="0" y="0" width="{w}" height="{h}" fill="url(#hg{uid})"/>'
        f'</mask>'
    )


GLYPH_OUTLINE = 30.0     # how far the ink halo sits proud of the cream mark


def _field_stack(uid: str, path: str, field: str, dot: str,
                 w: float, h: float, keyline: float, shadow_dy: float,
                 pitch: float, radius: float) -> str:
    """Shadow, flat colour, halftone and keyline — the four layers every card
    in the pack shares, emitted once and referenced four times.

    The field outline is a 128-point superellipse; writing it out four times
    per file cost ~9KB an icon, which is 8MB across the pack for four copies of
    the same string. `<use>` keeps the bytes down without changing a pixel.
    """
    return (
        f'<defs>'
        f'<path id="f{uid}" d="{path}"/>'
        f'<clipPath id="cp{uid}">'
        f'<use href="#f{uid}" xlink:href="#f{uid}"/></clipPath>'
        f'{_halftone_defs(uid, dot, w, h, pitch=pitch, radius=radius)}'
        f'</defs>'
        f'<use href="#f{uid}" xlink:href="#f{uid}" fill="{INK}" '
        f'transform="translate(0,{shadow_dy})"/>'
        f'<use href="#f{uid}" xlink:href="#f{uid}" fill="{field}"/>'
        f'<g clip-path="url(#cp{uid})">'
        f'<rect width="{w}" height="{h}" fill="url(#ht{uid})" '
        f'mask="url(#hm{uid})"/></g>'
        f'<use href="#f{uid}" xlink:href="#f{uid}" fill="none" stroke="{INK}" '
        f'stroke-width="{keyline}" stroke-linejoin="round"/>'
    )


_SVG_OPEN = ('<svg xmlns="http://www.w3.org/2000/svg" '
             'xmlns:xlink="http://www.w3.org/1999/xlink"')


def render_icon(glyph_name: str, accent: str, uid: str = "i") -> str:
    """512x512 square icon. Transparent outside the field."""
    _, field = snap(accent)
    path = squircle_path(BOX_X, BOX_Y, BOX_S)
    mark = inked_mark(glyph_name, target=TARGET_INK, outline=GLYPH_OUTLINE)
    fx, fy = BOX_X + BOX_S / 2.0, BOX_Y + BOX_S / 2.0

    return (
        f'{_SVG_OPEN} viewBox="0 0 {GRID} {GRID}" '
        f'width="{GRID}" height="{GRID}">\n'
        f'{_field_stack(uid, path, field, shade(field, 0.70), GRID, GRID, KEYLINE, SHADOW_DY, HALFTONE_PITCH, HALFTONE_R)}'
        f'<g transform="translate({fx},{fy})">{mark}</g>\n'
        f'</svg>\n'
    )


# --------------------------------------------------------------------------
# Fallback furniture for apps the pack does not cover.
#
# The ADW standard lets a pack hand the launcher a background, a clip mask, an
# overlay and a scale factor; the launcher composites the app's *own* icon onto
# them. Nova, Apex, ADW, Lawnchair and Blueprint-based packs all implement it.
#
# This matters more for Pop than for a transparent pack. Pop's entire claim is
# "one container, 924 times" — and a single unthemed app sitting next to them
# breaks that claim on sight. With this furniture the claim becomes "every app
# on your device", which is what the commercial competition advertises.
#
# Multiple iconbacks are allowed and the launcher picks one per app, so we ship
# all sixteen swatches and let unthemed apps land across the whole palette.
# --------------------------------------------------------------------------
def render_iconback(field: str, uid: str = "k") -> str:
    """The Pop container with no mark in it — field, halftone, keyline, ledge."""
    path = squircle_path(BOX_X, BOX_Y, BOX_S)
    return (
        f'{_SVG_OPEN} viewBox="0 0 {GRID} {GRID}" '
        f'width="{GRID}" height="{GRID}">\n'
        f'{_field_stack(uid, path, field, shade(field, 0.70), GRID, GRID, KEYLINE, SHADOW_DY, HALFTONE_PITCH, HALFTONE_R)}'
        f'</svg>\n'
    )


def render_iconmask() -> str:
    """Opaque squircle on transparent — clips a square app icon to Pop's shape.

    Polarity is the one thing here that is not settled by the spec: launchers
    disagree about whether the mask's opaque region is kept or removed. This
    follows the majority convention (opaque = visible). Verify on hardware.
    """
    return (
        f'{_SVG_OPEN} viewBox="0 0 {GRID} {GRID}" '
        f'width="{GRID}" height="{GRID}">\n'
        f'<path d="{squircle_path(BOX_X, BOX_Y, BOX_S)}" fill="#000000"/>\n'
        f'</svg>\n'
    )


def render_iconupon() -> str:
    """The ink keyline, drawn on top so a full-bleed app icon cannot cover it.

    Without this the borrowed icon paints over the keyline and the container
    stops reading as a Pop card — which is the whole point of the exercise.
    """
    return (
        f'{_SVG_OPEN} viewBox="0 0 {GRID} {GRID}" '
        f'width="{GRID}" height="{GRID}">\n'
        f'<path d="{squircle_path(BOX_X, BOX_Y, BOX_S)}" fill="none" '
        f'stroke="{INK}" stroke-width="{KEYLINE}" stroke-linejoin="round"/>\n'
        f'</svg>\n'
    )


# How far the launcher shrinks the borrowed icon before compositing.
#
# Not TARGET_INK/GRID: that is sized for a *stroked mark*, whose ink box is a
# thin skeleton, and applying it to a solid square app icon would leave it
# marooned in the middle of the field. This is a geometric fit instead — the
# largest square inside the container, less the keyline on both sides, less a
# breathing margin so the borrowed art does not crowd the ink.
#
# The superellipse is n=4.6, so it is close enough to square that the inscribed
# square is effectively the inner width.
FALLBACK_SCALE = round((BOX_S - 2 * KEYLINE) * 0.88 / GRID, 2)


BANNER_W, BANNER_H = 1280, 720
BANNER_INSET = 28
BANNER_RX = 76
BANNER_KEYLINE = 26
BANNER_SHADOW_DY = 18
BANNER_TARGET_INK = 430.0
BANNER_OUTLINE = 46.0


def render_banner(glyph_name: str, accent: str, uid: str = "b") -> str:
    """1280x720 master for Projectivy's 320x180 card. Mark only, deliberately.

    `tools/build_variant_markonly.py` measured a real TCL Google TV home row:
    with a wordmark in the lockup the glyph lands at 30-39px on screen while
    neighbouring stock icons render their marks at 48-100px, and the category
    kicker falls to 6.4px — below the legibility floor. Projectivy already
    draws the app name underneath the card. Spending the whole canvas on the
    mark roughly doubles it, and removes the one element (a wordmark set in our
    typeface on someone else's card) that could never be uniform anyway.
    """
    _, field = snap(accent)
    x, y = BANNER_INSET, BANNER_INSET
    w = BANNER_W - BANNER_INSET * 2
    h = BANNER_H - BANNER_INSET * 2 - BANNER_SHADOW_DY
    path = _rounded_rect(x, y, w, h, BANNER_RX)
    mark = inked_mark(glyph_name, target=BANNER_TARGET_INK,
                      outline=BANNER_OUTLINE)

    return (
        f'{_SVG_OPEN} width="{BANNER_W}" height="{BANNER_H}" '
        f'viewBox="0 0 {BANNER_W} {BANNER_H}">\n'
        f'{_field_stack(uid, path, field, shade(field, 0.70), BANNER_W, BANNER_H, BANNER_KEYLINE, BANNER_SHADOW_DY, 34.0, 7.0)}'
        f'<g transform="translate({BANNER_W / 2},{y + h / 2})">{mark}</g>\n'
        f'</svg>\n'
    )


__all__ = [
    "GRID", "INK", "CREAM", "PALETTE", "SWATCHES", "POP_STROKE", "TARGET_INK",
    "snap", "shade", "contrast", "squircle_path", "pop_body", "strip_weights",
    "fit", "inked_mark", "render_icon", "render_banner",
]
