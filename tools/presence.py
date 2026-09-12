"""Raster presence pass for Classic square icons.

The SVG masters stay style-AA monoline: one accent, no fill, no filter, no
container. That contract is load-bearing — identity tests and validate.py
reject glow in the vector. What the television actually shows is the 512px
PNG, and on Monet's transparent tiles those lines disappear against both
void and busy photography.

This pass sits *after* svg2png and gives the stroke a direction:

- a night drop, offset down-right, so the mark holds against a bright
  wallpaper without being wrapped in an outline
- a lit catch on the opposite edge, in a lighter tint of the icon's own
  accent, so the stroke reads as raised rather than printed

Neither layer introduces a second hue, a container, or a vendor fill.
The original glyph composites between them, so stroke pixels stay the
catalog accent and interiors stay alpha.
"""
from __future__ import annotations

from PIL import Image, ImageChops, ImageFilter

from glyphs import GRID, SAFE


def _lighten(src: Image.Image, t: float) -> tuple[int, int, int, int]:
    """The icon's own accent, mixed toward white — read off its own pixels so
    the catch never needs the catalog colour passed in."""
    px = src.convert("RGBA").getcolors(src.width * src.height) or []
    opaque = [(n, c) for n, c in px if c[3] > 200]
    if not opaque:
        return (255, 255, 255, 255)
    _, (r, g, b, _) = max(opaque)
    return (round(r + (255 - r) * t), round(g + (255 - g) * t),
            round(b + (255 - b) * t), 255)

# Night ink, not the card colour — a keyline the same as #0D1117 would vanish
# on Projectivy's recommended dark card. Slightly deeper so it still reads
# on #0D1117 and on Monet's void.
KEYLINE = (7, 11, 18, 255)
# Sized for a ~100px Monet dock tile. 11px on the 512 grid is ~2px of
# holdout at sitting distance — enough to lift the mark off photography
# without becoming a container.
KEYLINE_RADIUS = 11
KEYLINE_OPACITY = 0.82
# The lit edge opposite the drop. Two pixels of a lighter tint of the icon's
# own accent — never a second hue, so the one-accent rule still holds.
CATCH = 3
CATCH_OPACITY = 0.70


def _coverage(alpha: Image.Image, threshold: int = 128) -> float:
    hist = alpha.point(lambda p: 255 if p >= threshold else 0).histogram()
    return hist[255] / (alpha.size[0] * alpha.size[1])


def _safe_headroom(alpha: Image.Image) -> int:
    """Pixels the ring may grow outward before ink leaves the safe area.

    The ring dilates in every direction, so a mark already flush against
    SAFE has nowhere to put a keyline. `glyphs.SAFE` promises 40px of margin
    on the 512 grid and #110 left four marks sitting at exactly that, which
    an 11px ring would spend — the vector would still pass the safe-area
    test while the shipped PNG did not. Cap the ring at the margin actually
    available instead of assuming there is room.
    """
    box = alpha.getbbox()
    if not box:
        return 0
    w, h = alpha.size
    margin = min(box[0], box[1], w - box[2], h - box[3])
    pad = round((GRID - SAFE) / 2 * (w / GRID))
    return max(0, margin - pad)


def keyline_radius(alpha: Image.Image) -> int:
    """Dense marks get a shorter ring so they do not turn into slabs.

    Bounded by the safe-area headroom, so the pass never pushes ink outside
    SAFE to buy contrast.
    """
    covered = _coverage(alpha)
    if covered > 0.22:
        want = 5
    elif covered > 0.16:
        want = 8
    else:
        want = KEYLINE_RADIUS
    return min(want, _safe_headroom(alpha))


def apply_presence(image: Image.Image) -> Image.Image:
    """Return a new RGBA image with the mark lifted off whatever sits behind it.

    The keyline is *directional*, not a ring. An even outline reads as an
    outline — a container by another name, which is the thing style AA threw
    the hex host away to avoid. Offsetting it down-right and catching the
    opposite edge in a lighter tint of the same accent reads as a raised
    stroke instead, and it costs less safe-area margin because it only grows
    on two sides.

    The drop is blurred, so it is not a ring subtraction and cannot be used
    to flood an interior: the light catch is an edge difference, one to three
    pixels wide, and the glyph itself composites last. YouTube's play counter
    and MUBI's gaps stay open.
    """
    src = image.convert("RGBA")
    alpha = src.getchannel("A")
    radius = keyline_radius(alpha)
    if radius <= 0:
        return src

    dilated = alpha.filter(ImageFilter.MaxFilter(radius * 2 + 1))
    ring = ImageChops.subtract(dilated, alpha)

    keyline = Image.new("RGBA", src.size, KEYLINE)
    keyline.putalpha(ring.point(lambda p: int(p * KEYLINE_OPACITY)))

    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    out = Image.alpha_composite(out, keyline)
    out = Image.alpha_composite(out, src)

    # The lit edge is the only directional part, and it is decoration, not
    # holdout. A *directional* keyline was tried and reverted: offsetting the
    # drop down-right leaves the opposite edge unprotected, and a white mark
    # on a white wallpaper is exactly where that edge is load-bearing.
    # RetroArch and MUBI vanished. Contrast has to come from every side.
    catch_px = max(1, min(CATCH, radius // 2))
    catch = Image.new("RGBA", src.size, _lighten(src, 0.60))
    edge = ImageChops.subtract(
        ImageChops.offset(alpha, -catch_px, -catch_px), alpha)
    catch.putalpha(ImageChops.multiply(edge, ring)
                   .point(lambda p: int(p * CATCH_OPACITY)))
    return Image.alpha_composite(out, catch)


def apply_presence_file(path) -> None:
    image = Image.open(path)
    apply_presence(image).save(path)
