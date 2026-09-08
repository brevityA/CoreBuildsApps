"""Classic pack colour policy shared by squares, banners and previews.

The catalog retains the source brand accent (also used by Pop and Pixel Neon).
On Classic's recommended dark cards, very dark accents use a consistent light
ink instead of vanishing or being randomly brightened to a different hue.
This is a legibility adaptation, not a claim that a brand changed its colour.
"""
from __future__ import annotations

import colorsys

CARD = "#0D1117"
LIGHT_INK = "#E6EDF3"
MIN_CONTRAST = 3.0


def luminance(colour: str) -> float:
    channels = [int(colour[pos:pos + 2], 16) / 255 for pos in (1, 3, 5)]
    linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
              for v in channels]
    return sum(v * weight for v, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


def contrast(a: str, b: str = CARD) -> float:
    high, low = sorted((luminance(a), luminance(b)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def display_accent(accent: str, *, monochrome: bool = False) -> str:
    """Make a source accent legible on the dark card without losing its hue.

    Accents that already clear MIN_CONTRAST pass through untouched. Dark ones
    are lightened along their own hue to the *minimum* lightness that clears
    the threshold, so the icon still reads as its brand.

    The previous policy collapsed every sub-threshold accent to LIGHT_INK,
    which was legible but flattened 79 icons — Apple TV, MUBI, ABC News and
    BET+ all rendered as identical white linework. That is still the right
    answer for genuinely achromatic accents, which have no hue to preserve.

    Pass monochrome=True for brands whose mark is confirmed monochrome ink —
    sampled near-blacks may carry residual saturation from photography, but
    the brand has no hue to preserve, so they take the light-ink path.

    Idempotent by construction: the returned colour clears MIN_CONTRAST, so a
    second call takes the pass-through branch. tests/test_icon_identity.py
    asserts both properties.
    """
    colour = accent.upper()
    if contrast(colour) >= MIN_CONTRAST:
        return colour

    rgb = tuple(int(colour[pos:pos + 2], 16) / 255 for pos in (1, 3, 5))
    hue, light, sat = colorsys.rgb_to_hls(*rgb)
    if monochrome or sat < _MIN_SATURATION:
        # No hue to preserve — pure blacks and near-greys. Unchanged from the
        # original policy so sibling packs and existing marks stay identical.
        return LIGHT_INK

    # Binary-search the lowest lightness on this hue that is legible. hi is
    # always a valid candidate (lightness 1.0 is white, contrast ~21).
    lo, hi = light, 1.0
    for _ in range(24):
        mid = (lo + hi) / 2
        if contrast(_hls_hex(hue, mid, sat)) >= MIN_CONTRAST:
            hi = mid
        else:
            lo = mid
    return _hls_hex(hue, hi, sat)


# Below this an accent has no meaningful hue to carry, so it takes the light
# ink rather than being "lightened" into a different grey.
_MIN_SATURATION = 0.08


def _hls_hex(hue: float, light: float, sat: float) -> str:
    r, g, b = colorsys.hls_to_rgb(hue, light, sat)
    return "#{:02X}{:02X}{:02X}".format(
        *(max(0, min(255, round(v * 255))) for v in (r, g, b)))


# Existing Core Builds grammar, not a new theme. Keep the two detail weights
# subordinate rather than flattening all three levels to 32px.
CORE_MONOLINE = "core_monoline"
CORE_STROKES = frozenset({32.0, 26.2, 21.8})


def core_monoline_errors(body: str, accent: str) -> list[str]:
    """Check glyph ink before the common banner placement transform.

    A vendor silhouette or fixed-white wordmark must fail even if its brand
    proportions are correct. Original fallback letters are not opted into
    this contract; the catalog marks the reviewed brand constructions.
    """
    import xml.etree.ElementTree as ET

    errors = []
    try:
        root = ET.fromstring(f"<g>{body}</g>")
    except ET.ParseError:
        return ["invalid glyph XML"]
    primitives = {"path", "circle", "ellipse", "rect", "line", "polyline", "polygon"}
    if not len(root):
        return ["empty monoline glyph"]
    for node in root.iter():
        if node is root:
            continue
        tag = node.tag.split("}")[-1]
        if tag not in primitives:
            errors.append(f"{tag}: only original line primitives are allowed")
        if any(key in node.attrib for key in ("style", "transform", "filter", "opacity", "mask", "clip-path")):
            errors.append(f"{tag}: no private transforms/effects or CSS overrides")
        if node.get("fill") != "none":
            errors.append(f"{tag}: monoline glyphs cannot use solid fills")
        if node.get("stroke", "").upper() != accent.upper():
            errors.append(f"{tag}: glyph must use its one shared accent")
        try:
            weight = float(node.get("stroke-width", "nan"))
        except ValueError:
            weight = float("nan")
        if weight not in CORE_STROKES:
            errors.append(f"{tag}: stroke must be 32 / 26.2 / 21.8 after normalisation")
        if node.get("stroke-linecap") != "round" or node.get("stroke-linejoin") != "round":
            errors.append(f"{tag}: caps and joins must be round")
    return errors
