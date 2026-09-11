"""Raster presence pass for Classic square icons.

The SVG masters stay style-AA monoline: one accent, no fill, no filter, no
container. That contract is load-bearing — identity tests and validate.py
reject glow in the vector. What the television actually shows is the 512px
PNG, and on Monet's transparent tiles those lines disappear against both
void and busy photography.

This pass sits *after* svg2png and adds two layers that the brand guide
already named, just not on third-party art:

- a night keyline so the mark holds against a bright wallpaper
- a short accent bloom so the mark reads as lit on a dark tile

Neither layer introduces a second hue, a container, or a vendor fill.
The original glyph composites on top, so stroke pixels stay the catalog
accent and interiors stay alpha.
"""
from __future__ import annotations

from PIL import Image, ImageChops, ImageFilter

from glyphs import GRID, SAFE

# Night ink, not the card colour — a keyline the same as #0D1117 would vanish
# on Projectivy's recommended dark card. Slightly deeper so it still reads
# on #0D1117 and on Monet's void.
KEYLINE = (7, 11, 18, 255)
# Sized for a ~100px Monet dock tile. 11px on the 512 grid is ~2px of
# holdout at sitting distance — enough to lift the mark off photography
# without becoming a container.
KEYLINE_RADIUS = 11
KEYLINE_OPACITY = 0.88
GLOW_RADIUS = 12
GLOW_OPACITY = 0.36


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
    """Return a new RGBA image with keyline + bloom under the source glyph.

    Both extras are *rings* around existing ink. A filled dilation or a
    blur of the whole glyph would flood open interiors (YouTube's play
    counter, MUBI's gaps) and trip the coverage ceiling.
    """
    src = image.convert("RGBA")
    alpha = src.getchannel("A")
    radius = keyline_radius(alpha)
    dilated = alpha.filter(ImageFilter.MaxFilter(radius * 2 + 1))
    ring = ImageChops.subtract(dilated, alpha)

    key_alpha = ring.point(lambda p: int(p * KEYLINE_OPACITY))
    keyline = Image.new("RGBA", src.size, KEYLINE)
    keyline.putalpha(key_alpha)

    # Blur the glyph for colour, then keep only the ring so interiors stay open.
    glow = src.filter(ImageFilter.GaussianBlur(GLOW_RADIUS))
    glow_alpha = ImageChops.multiply(glow.getchannel("A"), ring)
    glow_alpha = glow_alpha.point(lambda p: int(p * GLOW_OPACITY))
    glow.putalpha(glow_alpha)

    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    out = Image.alpha_composite(out, glow)
    out = Image.alpha_composite(out, keyline)
    out = Image.alpha_composite(out, src)
    return out


def apply_presence_file(path) -> None:
    image = Image.open(path)
    apply_presence(image).save(path)
