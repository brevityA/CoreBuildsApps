#!/usr/bin/env python3
"""
Core Builds Icon Pack — 16:9 banner generator.

Built on the composition grammar measured from Projectivy Icon Pack 1.1.9
(1002 icons at 320x180 RGBA), then re-expressed in Core Builds' visual
language. What was measured from their pack, over a 150-icon sample:

    canvas          320 x 180 RGBA (16:9)
    ink bounding    median 78% of width, 43% of height
    padding         median 35px left/right, ~51px top/bottom (on 320x180)
    centring        dead centre, both axes (median offset 0.0px)
    ink coverage    ~12% of the canvas — these read as marks, not blocks
    composition     glyph + wordmark side by side, or wordmark alone

Those are their structural rules and they are sound for a 10-foot UI. What we
do NOT copy is the art: their icons are official third-party logos placed
as-is. Ours are original geometry in the Core Builds icon language — simple
shapes, rounded ends, one accent colour per app (Brand Guide §07).

No pack branding appears on any banner. Their DAZN icon is just DAZN; a
"CORE BUILDS" label on someone else's card is noise. The brand reads through
geometry and accent colour.

Writes:
  assets/banners/<drawable>.svg                    1280x720 master (4x)
  app/src/main/res/drawable-nodpi/<d>_banner.png   320x180 RGBA
"""
import argparse
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glyphs import GLYPHS, monoline  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"
SVG_DIR = ROOT / "assets" / "banners"
PNG_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"

# Master grid is 4x the 320x180 target so the downscale stays crisp.
W, H = 1280, 720
PNG_W, PNG_H = 320, 180

# Their measured ink box, scaled to our master: 78% x 43%.
# Budget must also clear the rail and its gutter, or the lockup
# overflows the 90% safe limit the validator enforces.
INK_W = W * 0.78
INK_H = H * 0.43          # 310

GLYPH_H = 360             # glyph cap height inside the ink box
GAP = 46                  # space between glyph and wordmark
INK = "#E6EDF3"           # Brand Guide §03
ACCENT = "#00d4ff"        # --th-accent, from the live configurator
VIOLET = "#a78bfa"        # --th-purple
RAIL_W = 16               # cyan->violet rail, left edge
RAIL_PAD = 168            # rail inset from top/bottom, keeps ink under 72%
KICKER = 46               # uppercase mono category size
KICK_TRACK = 7.0          # .08em at this size, matching the site

# Wordmarks are BOLD SANS, not serif.
#
# Brand Guide §04 scopes the serif to display copy — splash headlines, question
# cards, doc covers — and says "never bold". An app card is not display copy;
# it is a label read at distance, which §04 assigns to the system-ui stack at
# 600-800 weight. The reference pack's icons read hard because their wordmarks
# are heavy sans; a light serif at card size looks tentative next to them.
#
# Metrics come from DejaVu Sans Bold, which is close to the system-ui stack the
# guide specifies. The SVG still requests the real stack first.
FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
]
FONT_STACK = ("-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,"
              "'DejaVu Sans',sans-serif")


def _measure(text, size):
    """Width of `text` at `size`, measured — falls back to an estimate."""
    try:
        from PIL import ImageFont
        for p in FONT_CANDIDATES:
            if os.path.exists(p):
                return ImageFont.truetype(p, size).getlength(text)
    except Exception:
        pass
    return len(text) * size * 0.55


def esc(s):
    return (s.replace("&", "&amp;").replace("<", "&lt;")
             .replace(">", "&gt;").replace('"', "&quot;"))


MAX_TYPE = 124
MIN_TYPE = 62


def split_name(name):
    """
    Break a long name across two lines at its most balanced space.

    Shrinking "Projectivy Launcher" to 54px to fit one line made it a third
    the size of "VLC" on the neighbouring card — a 3.5x spread across the set,
    which reads as inconsistent in a row. Two lines keeps every wordmark in
    the same optical range.
    """
    if len(name) <= 11:
        return [name]
    if " " not in name:
        # No space to break on (e.g. "Xtreamplayeranddownloader"). Long
        # single tokens blew past the 90% safe-area limit because fit_type
        # can only shrink to MIN_TYPE. Split near the middle instead.
        if len(name) > 16:
            k = len(name) // 2
            return [name[:k], name[k:]]
        return [name]
    words = name.split()
    best, gap = None, 10 ** 9
    for k in range(1, len(words)):
        a, b = " ".join(words[:k]), " ".join(words[k:])
        if abs(len(a) - len(b)) < gap:
            best, gap = (a, b), abs(len(a) - len(b))
    return list(best)


def fit_type(lines):
    """Largest size at which every line fits the wordmark column."""
    budget = INK_W - GLYPH_H - GAP
    size = MAX_TYPE
    while size > MIN_TYPE and max(_measure(l, size) for l in lines) > budget:
        size -= 2
    return size


def hex_host(cx, cy, r, color):  # retired in style AA
    """
    UNUSED. Kept for reference only.

    Google's TV icon guidance: "Avoid adding any border around the logo as
    they get cropped and create unpolished visuals." Style AA drops the host.

    Original note:
    Concept F's container: the brand's point-up hexagon (§02 — the stance is
    load-bearing, never rotated), tinted in the app's own accent.

    Deliberately a light wash plus a hairline. At full strength across 81
    icons a row reads as "hexagons" before it reads as apps, which is the
    generic trap. At this weight it hosts the mark without becoming it.
    """
    import math
    pts = " ".join(
        f"{cx + r * math.cos(math.radians(60 * i - 90)):.1f},"
        f"{cy + r * math.sin(math.radians(60 * i - 90)):.1f}"
        for i in range(6))
    return (f'<polygon points="{pts}" fill="{color}" fill-opacity="0.10" '
            f'stroke="{color}" stroke-opacity="0.42" stroke-width="7" '
            f'stroke-linejoin="round"/>')


def render(name, glyph, accent, category=None):
    """
    Centred glyph + wordmark, with the Core Builds signature:

      * a cyan->violet rail on the left edge (concept H)
      * an uppercase mono category kicker above the name (concept H)
      * the glyph's own halo, already applied by lit() (concept D)

    Concepts E/G/I were rejected: their signal lives in the card BACKGROUND,
    which we do not own — these PNGs are transparent and Projectivy draws
    whatever colour the user picked behind them. Rail, kicker and halo are
    all drawn ink, so they survive any card colour.
    """
    lines = split_name(name)
    size = fit_type(lines)
    text_w = max(_measure(l, size) for l in lines)
    total = GLYPH_H + GAP + text_w

    # Shift the lockup right so the rail never crowds the glyph.
    start_x = (W - total) / 2 + RAIL_W

    gy = (H - GLYPH_H) / 2
    scale = GLYPH_H / 512
    tx = start_x + GLYPH_H + GAP

    lead = size * 1.08
    has_kick = bool(category)
    # Kicker sits above the name; drop the block so the pair stays centred.
    shift = (KICKER * 0.85) / 2 if has_kick else 0

    if len(lines) == 1:
        baselines = [H / 2 + size * 0.355 + shift]
    else:
        top = H / 2 - lead / 2 + size * 0.355 + shift
        baselines = [top, top + lead]

    spans = ""
    if has_kick:
        ky = baselines[0] - size * 0.92 - 10
        spans += (f'<text x="{tx:.0f}" y="{ky:.0f}" fill="{ACCENT}" '
                  f'font-family="ui-monospace,\'DejaVu Sans Mono\',monospace" '
                  f'font-size="{KICKER}" font-weight="700" '
                  f'letter-spacing="{KICK_TRACK}">{esc(category)}</text>\n  ')

    spans += "".join(
        f'<text x="{tx:.0f}" y="{b:.0f}" fill="{INK}" '
        f'font-family="{FONT_STACK}" font-weight="700" '
        f'font-size="{size}" letter-spacing="-1.5">{esc(l)}</text>\n  '
        for l, b in zip(lines, baselines)
    )

    rail = (f'<defs><linearGradient id="cbRail" x1="0" y1="0" x2="0" y2="1">'
            f'<stop offset="0%" stop-color="{ACCENT}"/>'
            f'<stop offset="100%" stop-color="{VIOLET}"/>'
            f'</linearGradient></defs>'
            f'<rect x="70" y="{RAIL_PAD}" width="{RAIL_W}" '
            f'height="{H - RAIL_PAD * 2}" rx="{RAIL_W / 2}" fill="url(#cbRail)"/>')

    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
        f'viewBox="0 0 {W} {H}">\n'
        f'  {rail}\n'
        f'  <g transform="translate({start_x:.0f},{gy:.0f}) '
        f'scale({scale:.5f})">{monoline(GLYPHS[glyph](accent))}</g>\n'
        f'  {spans}</svg>\n'
    )


def render_glyph_only(glyph, accent):
    """Mark-only variant — used when a name adds nothing (e.g. Core Builds)."""
    box = 380
    scale = box / 512
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
        f'viewBox="0 0 {W} {H}">\n'
        f'  <g transform="translate({(W - box) / 2:.0f},{(H - box) / 2:.0f}) '
        f'scale({scale:.5f})">{monoline(GLYPHS[glyph](accent))}</g>\n'
        f'</svg>\n'
    )


def recentre(svg):
    """
    Rasterise once, measure the real ink box, and shift the lockup so the
    drawn art is exactly centred.

    Glyph grids are not tight to their ink and serif text carries side
    bearings, so geometric centring alone leaves a visible few-px drift —
    obvious when cards sit in a row. Measuring beats estimating.
    """
    try:
        import io
        import re
        import cairosvg
        from PIL import Image
    except ImportError:
        return svg

    png = cairosvg.svg2png(bytestring=svg.encode(),
                           output_width=W, output_height=H,
                           background_color=None)
    bbox = Image.open(io.BytesIO(png)).convert("RGBA").getchannel("A").getbbox()
    if not bbox:
        return svg
    l, t, r, b = bbox
    dx = (W - (l + r)) / 2
    dy = (H - (t + b)) / 2
    if abs(dx) < 0.5 and abs(dy) < 0.5:
        return svg
    body = svg.split(">", 1)[1].rsplit("</svg>", 1)[0]
    head = svg.split(">", 1)[0] + ">"
    return (f'{head}\n  <g transform="translate({dx:.1f},{dy:.1f})">'
            f'{body}</g>\n</svg>\n')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--all", action="store_true",
                    help="accepted for compatibility; every icon always builds")
    ap.parse_args()

    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())

    # Every icon gets a banner, unconditionally. Banners are what appfilter
    # maps to, so a partial run would point the mapping at drawables that do
    # not exist and those apps would fall back to their stock icon.
    targets = icons

    SVG_DIR.mkdir(parents=True, exist_ok=True)
    for i in targets:
        if i.get("banner_style") == "glyph":
            svg = render_glyph_only(i["glyph"], i["color"])
        else:
            svg = render(i["name"], i["glyph"], i["color"],
                         i.get("category"))
            svg = recentre(svg)
        (SVG_DIR / f"{i['drawable']}.svg").write_text(svg, encoding="utf-8")
    print(f"\u2713 banner SVGs written ({len(targets)}/{len(targets)}) "
          f"\u2192 assets/banners/")

    try:
        import cairosvg
    except ImportError:
        print("\u26a0 cairosvg not installed \u2014 PNGs skipped. "
              "Run: pip install -r tools/requirements.txt")
        return 0

    PNG_DIR.mkdir(parents=True, exist_ok=True)
    for i in targets:
        cairosvg.svg2png(
            url=str(SVG_DIR / f"{i['drawable']}.svg"),
            write_to=str(PNG_DIR / f"{i['drawable']}_banner.png"),
            output_width=PNG_W, output_height=PNG_H,
            background_color=None)
    print(f"\u2713 banner PNGs {PNG_W}x{PNG_H} transparent written "
          f"({len(targets)}/{len(targets)}) \u2192 res/drawable-nodpi/")
    print(f"\nBanners complete \u2014 {len(targets)} at 16:9, centred lockups "
          f"on the reference pack's measured grid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
