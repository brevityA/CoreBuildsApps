#!/usr/bin/env python3
"""
Outfit typeface helpers.

Wordmarks and monograms share one family so a row of cards reads as one pack
instead of a mix of hand-drawn letters and whatever sans the host happens to
have installed. Outlines are converted to SVG paths here — the shipped SVGs
stay font-independent and identical in CI.

Outfit is SIL OFL 1.1 (tools/fonts/OFL.txt).
"""
from functools import lru_cache
from pathlib import Path

from fontTools.misc.transform import Transform
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

FONTS = Path(__file__).resolve().parent / "fonts"
FONT_WORDMARK = FONTS / "Outfit-Bold.ttf"
FONT_MONOGRAM = FONTS / "Outfit-ExtraBold.ttf"

GRID = 512
SAFE = 432


@lru_cache(maxsize=4)
def _font(path: str):
    return TTFont(path)


def _cmap(font):
    return font.getBestCmap()


def measure(text, size, font_path=None):
    """Advance width of `text` at `size` (em-square px)."""
    font = _font(str(font_path or FONT_WORDMARK))
    glyphset = font.getGlyphSet()
    cmap = _cmap(font)
    upem = font["head"].unitsPerEm
    scale = size / upem
    w = 0.0
    for ch in text:
        name = cmap.get(ord(ch), ".notdef")
        w += glyphset[name].width * scale
    return w


def _line_paths(text, size, x, baseline, font_path):
    """SVG path `d` strings for one line. `baseline` is the typographic baseline."""
    font = _font(str(font_path))
    glyphset = font.getGlyphSet()
    cmap = _cmap(font)
    upem = font["head"].unitsPerEm
    scale = size / upem
    cursor = x
    ds = []
    for ch in text:
        name = cmap.get(ord(ch), ".notdef")
        glyph = glyphset[name]
        pen = SVGPathPen(glyphset)
        # font y-up → SVG y-down; place on baseline
        xf = Transform(scale, 0, 0, -scale, cursor, baseline)
        glyph.draw(TransformPen(pen, xf))
        d = pen.getCommands()
        if d:
            ds.append(d)
        cursor += glyph.width * scale
    return ds, cursor - x


def wordmark_spans(lines, size, tx, baselines, fill, font_path=None):
    """
    Path-based wordmark, same layout the old <text> block used.

    Returns (svg_markup, ink_width) so the banner lockup can be measured
    without relying on a host font.

    `font_path` defaults to Outfit Bold, which is what every app banner uses.
    The pack's own Leanback banner passes the mono face for its strapline.
    """
    parts = []
    max_w = 0.0
    for line, baseline in zip(lines, baselines):
        ds, w = _line_paths(line, size, tx, baseline, font_path or FONT_WORDMARK)
        max_w = max(max_w, w)
        for d in ds:
            parts.append(f'<path d="{d}" fill="{fill}" stroke="none"/>')
    return "\n  ".join(parts), max_w


# --------------------------------------------------------------------------
# Adaptive wordmark lockups.
#
# Letter-first fallbacks (the <family>_<L> category shells) used to carry one
# Outfit letter; every app whose name began with the same letter rendered the
# same type. The Projectivy Icon Pack's one measurable win over this pack
# (docs/research/iconpack-design-upgrade-2026-09.md) is that its fallbacks set
# per-app wordmarks, so no two fallbacks read the same.
#
# This is the Core Builds answer: the same Outfit ExtraBold voice, set as a
# short per-app token ("AI Cam View" -> "ACV", "Weyd" -> "WE"), with the
# TYPE adapting instead of the letter: a cap-height tier per character count,
# a per-shell interior width, and the shell's optical centre. Same face as
# every banner wordmark, so a row still reads as one pack — the silhouette
# and the hue answer "which app", the token just stops the type lying.
#
# The derivation rule (multi-word -> up to three initials, one word -> first
# two letters) is the same one Pixel Neon's brand_initials uses, so the suite
# derives one token per app instead of three divergent systems.
# --------------------------------------------------------------------------

# Fewer characters are set larger — the classic optical-size ladder. Four is
# the hard ceiling: longer marks would sit under the counter floor in every
# shell and read as texture, not type. A mark that cannot hold
# MIN_LOCKUP_CAP on the third tier trades back to two characters — the floor
# is the boss of the ladder, never the other way round.
LOCKUP_TIERS = {1: 1.00, 2: 0.82, 3: 0.68, 4: 0.58}

# Below this cap a filled Outfit counter closes at a 48dp Projectivy tile.
# v1.8.14 drew the same lesson for glyph counters (the 32px minimum); this is
# its typographic twin.
MIN_LOCKUP_CAP = 96


def _lockup_ink(text):
    """Em-scale ink box + per-glyph origins for a lockup candidate."""
    font = _font(str(FONT_MONOGRAM))
    glyphset = font.getGlyphSet()
    cmap = _cmap(font)
    cursor = 0.0
    pieces = []
    xmin = ymin = 1e9
    xmax = ymax = -1e9
    for ch in text:
        name = cmap.get(ord(ch), ".notdef")
        glyph = glyphset[name]
        bounds = BoundsPen(glyphset)
        glyph.draw(bounds)
        if bounds.bounds:
            bx0, by0, bx1, by1 = bounds.bounds
            xmin = min(xmin, cursor + bx0)
            ymin = min(ymin, by0)
            xmax = max(xmax, cursor + bx1)
            ymax = max(ymax, by1)
        pieces.append((glyph, cursor))
        cursor += glyph.width
    return pieces, (xmin, ymin, xmax, ymax), glyphset


def lockup_cap(text, cap_h, max_w):
    """The cap height `text` actually reaches inside a shell's type budget.

    `cap_h` is the shell's single-letter cap; `max_w` its interior width. The
    renderer and the catalog validator share this number so a mark that would
    shrink under MIN_LOCKUP_CAP fails validation by name, never silently.
    """
    if not text or len(text) > max(LOCKUP_TIERS):
        return 0.0
    _, (xmin, ymin, xmax, ymax), _gs = _lockup_ink(text)
    gw, gh = xmax - xmin, ymax - ymin
    if gw <= 0 or gh <= 0:
        return 0.0
    scale = min(cap_h * LOCKUP_TIERS[len(text)] / gh, max_w / gw)
    return gh * scale


def adaptive_lockup(text, color, cap_h, max_w, cy=GRID / 2):
    """One filled Outfit ExtraBold lockup, sized to a shell's type budget.

    Same optical box as the single-letter monograms: ink centred on the
    grid's vertical axis at `cy`, never stretched — the size gives, the face
    doesn't.
    """
    pieces, (xmin, ymin, xmax, ymax), glyphset = _lockup_ink(text)
    gw, gh = xmax - xmin, ymax - ymin
    if gw <= 0 or gh <= 0:
        return ""
    scale = min(cap_h * LOCKUP_TIERS[len(text)] / gh, max_w / gw)
    cx = GRID / 2
    ink_cx, ink_cy = (xmin + xmax) / 2, (ymin + ymax) / 2
    out = []
    for glyph, origin in pieces:
        xf = Transform(scale, 0, 0, -scale,
                       cx - (ink_cx - origin) * scale,
                       cy + ink_cy * scale)
        pen = SVGPathPen(glyphset)
        glyph.draw(TransformPen(pen, xf))
        d = pen.getCommands()
        if d:
            out.append(f'<path d="{d}" fill="{color}" stroke="none"/>')
    return "".join(out)


def monogram_body(letter, color):
    """
    One filled Outfit ExtraBold letter, optically centred on the 512 grid.

    Cap height is locked so A and M and I share the same visual weight in a
    row — that is the whole point of driving monograms from a typeface.
    """
    font = _font(str(FONT_MONOGRAM))
    glyphset = font.getGlyphSet()
    cmap = _cmap(font)
    upem = font["head"].unitsPerEm
    name = cmap.get(ord(letter), ".notdef")
    glyph = glyphset[name]

    bounds = BoundsPen(glyphset)
    glyph.draw(bounds)
    if not bounds.bounds:
        return ""
    xmin, ymin, xmax, ymax = bounds.bounds
    gw, gh = xmax - xmin, ymax - ymin
    if gw <= 0 or gh <= 0:
        return ""

    # Target a shared cap-box. Narrow letters (I, 1) keep their width; they
    # are not stretched. Tall box ~300 so the letter survives 10-foot downscale.
    target_h = 300
    scale = target_h / gh
    # Don't let wide letters (W, M) blow the 432 safe area.
    if gw * scale > 360:
        scale = 360 / gw
    cx = GRID / 2
    cy = GRID / 2
    # Centre the ink box, not the em square — side bearings differ per letter.
    ink_cx = (xmin + xmax) / 2
    ink_cy = (ymin + ymax) / 2
    xf = Transform(scale, 0, 0, -scale,
                   cx - ink_cx * scale,
                   cy + ink_cy * scale)
    pen = SVGPathPen(glyphset)
    glyph.draw(TransformPen(pen, xf))
    d = pen.getCommands()
    return f'<path d="{d}" fill="{color}" stroke="none"/>'


def monogram_text(text, color):
    """
    One or two characters as a filled lockup (e.g. '10' for 10 Play).
    Same optical box as a single-letter monogram.
    """
    font = _font(str(FONT_MONOGRAM))
    glyphset = font.getGlyphSet()
    cmap = _cmap(font)
    upem = font["head"].unitsPerEm

    # Measure combined ink
    cursor = 0.0
    pieces = []
    xmin = ymin = 1e9
    xmax = ymax = -1e9
    for ch in text:
        name = cmap.get(ord(ch), ".notdef")
        glyph = glyphset[name]
        bounds = BoundsPen(glyphset)
        glyph.draw(bounds)
        if bounds.bounds:
            bx0, by0, bx1, by1 = bounds.bounds
            xmin = min(xmin, cursor + bx0)
            ymin = min(ymin, by0)
            xmax = max(xmax, cursor + bx1)
            ymax = max(ymax, by1)
        pieces.append((glyph, cursor))
        cursor += glyph.width

    gw, gh = xmax - xmin, ymax - ymin
    if gw <= 0 or gh <= 0:
        return ""
    target_h = 280 if len(text) > 1 else 300
    scale = target_h / gh
    if gw * scale > 360:
        scale = 360 / gw
    cx, cy = GRID / 2, GRID / 2
    ink_cx, ink_cy = (xmin + xmax) / 2, (ymin + ymax) / 2
    out = []
    for glyph, origin in pieces:
        xf = Transform(scale, 0, 0, -scale,
                       cx - (ink_cx - origin) * scale,
                       cy + ink_cy * scale)
        pen = SVGPathPen(glyphset)
        glyph.draw(TransformPen(pen, xf))
        d = pen.getCommands()
        if d:
            out.append(f'<path d="{d}" fill="{color}" stroke="none"/>')
    return "".join(out)


def monogram_outline(text, color, cap_h=210, weight=20, max_width=296):
    """Stroked (open-ink) version of `monogram_text` for the monoline pack.

    Same optical placement as the filled monograms, but each contour is
    emitted as an outline stroke instead of a fill, so it satisfies the
    core_monoline contract (no solid fills, one accent, round caps). Used by
    marks whose brand cue is a numeral lockup (e.g. France 24's '24').
    """
    font = _font(str(FONT_MONOGRAM))
    glyphset = font.getGlyphSet()
    cmap = _cmap(font)
    upem = font["head"].unitsPerEm

    cursor = 0.0
    pieces = []
    xmin = ymin = 1e9
    xmax = ymax = -1e9
    for ch in text:
        name = cmap.get(ord(ch), ".notdef")
        glyph = glyphset[name]
        bounds = BoundsPen(glyphset)
        glyph.draw(bounds)
        if bounds.bounds:
            bx0, by0, bx1, by1 = bounds.bounds
            xmin = min(xmin, cursor + bx0)
            ymin = min(ymin, by0)
            xmax = max(xmax, cursor + bx1)
            ymax = max(ymax, by1)
        pieces.append((glyph, cursor))
        cursor += glyph.width

    gw, gh = xmax - xmin, ymax - ymin
    if gw <= 0 or gh <= 0:
        return ""
    scale = cap_h / gh
    if gw * scale > max_width:
        scale = max_width / gw
    cx, cy = GRID / 2, GRID / 2
    ink_cx, ink_cy = (xmin + xmax) / 2, (ymin + ymax) / 2
    out = []
    for glyph, origin in pieces:
        xf = Transform(scale, 0, 0, -scale,
                       cx - (ink_cx - origin) * scale,
                       cy + ink_cy * scale)
        pen = SVGPathPen(glyphset)
        glyph.draw(TransformPen(pen, xf))
        d = pen.getCommands()
        if d:
            out.append(f'<path d="{d}" fill="none" stroke="{color}" '
                       f'stroke-width="{weight}" stroke-linecap="round" '
                       f'stroke-linejoin="round"/>')
    return "".join(out)


def monogram_scaled(letter, color, cap_h=250, cx=GRID / 2, cy=GRID / 2):
    """One filled letter, rendered to a requested cap height and centre.

    Used to place a monogram inside a contained mark (a tile/badge) rather
    than full-bleed. `cap_h` scales the shared cap-box; `cx`/`cy` reposition
    the ink box. Same typeface and optical treatment as monogram_body.
    """
    font = _font(str(FONT_MONOGRAM))
    glyphset = font.getGlyphSet()
    cmap = _cmap(font)
    upem = font["head"].unitsPerEm
    name = cmap.get(ord(letter), ".notdef")
    glyph = glyphset[name]

    bounds = BoundsPen(glyphset)
    glyph.draw(bounds)
    if not bounds.bounds:
        return ""
    xmin, ymin, xmax, ymax = bounds.bounds
    gw, gh = xmax - xmin, ymax - ymin
    if gw <= 0 or gh <= 0:
        return ""

    scale = cap_h / gh
    if gw * scale > 380:
        scale = 380 / gw
    ink_cx, ink_cy = (xmin + xmax) / 2, (ymin + ymax) / 2
    xf = Transform(scale, 0, 0, -scale,
                   cx - ink_cx * scale,
                   cy + ink_cy * scale)
    pen = SVGPathPen(glyphset)
    glyph.draw(TransformPen(pen, xf))
    d = pen.getCommands()
    return f'<path d="{d}" fill="{color}" stroke="none"/>'
