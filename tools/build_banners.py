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
keep is the Core Builds identity: original rounded-line glyphs, one accent,
one Outfit label/category/rail lockup. Vendor artwork is reference material,
not a second icon style. Colour and provenance remain in tools/catalog.json.

No pack branding appears on any banner. Their DAZN icon is just DAZN; a
"CORE BUILDS" label on someone else's card is noise. The brand reads through
geometry and accent colour.

Writes:
  assets/banners/<drawable>.svg                    1280x720 master (4x)
  app/src/main/res/drawable-nodpi/<d>_banner.webp  320x180 RGBA lossless WebP
  app/src/main/res/values/banner_aliases.xml       dup-name -> canonical art
"""
import argparse
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glyphs import GLYPHS, apply_gradient, classic_fit, family_body, monoline  # noqa: E402
from icon_style import display_accent  # noqa: E402
from typeface import FONT_WORDMARK, measure as type_measure, wordmark_spans  # noqa: E402
from drawable_art import (ART_EXT, BRANDING_PNGS, alias_identical,
                          write_aliases_file)  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"
SVG_DIR = ROOT / "assets" / "banners"
PNG_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
VAL_DIR = ROOT / "app" / "src" / "main" / "res" / "values"

# Master grid is 4x the 320x180 target so the downscale stays crisp.
W, H = 1280, 720
PNG_W, PNG_H = 320, 180

# Their measured ink box, scaled to our master: 78% x 43%.
# Budget must also clear the rail and its gutter, or the lockup
# overflows the 90% safe limit the validator enforces.
INK_W = W * 0.78
INK_H = H * 0.43          # 310

GLYPH_H = 360             # glyph cap height inside the ink box
GAP = 80                  # space between the glyph's INK and the wordmark.
                          # Until 1.9.4 this was 46 from the glyph's 512 grid
                          # box, so the visible gap was 46 plus whatever
                          # empty grid the glyph left on its right: 71 to 193
                          # units across the pack (18-48px at 320). 80 is the
                          # old median visible gap, now the same on every card.
HALF_STROKE = 16          # grid units the primary monoline stroke overhangs
METRICS = ROOT / "tools" / "glyph_metrics.json"
_INK = None


def glyph_ink_x(glyph):
    """(left, right) of a glyph's drawn ink on its 512 grid, after the
    committed classic fit, so the lockup is spaced by what is drawn rather
    than by the grid box around it. Geometry comes from the committed
    metrics (tools/glyph_metrics.json), keeping this a pure function of
    committed files; a glyph with no metrics falls back to the full grid."""
    global _INK
    if _INK is None:
        _INK = json.loads(METRICS.read_text(encoding="utf-8"))["metrics"]
    box = _INK.get(glyph)
    if box is None:
        return 0.0, 512.0
    x0, _y0, x1, _y1 = box
    fit_body = classic_fit(glyph, "")
    if fit_body:
        # classic_fit maps the ink centre to 256 and scales about it.
        import re
        scale = float(re.search(r"scale\(([\d.]+)\)", fit_body).group(1))
        half = scale * (x1 - x0) / 2
        return 256 - half - HALF_STROKE, 256 + half + HALF_STROKE
    return x0 - HALF_STROKE, x1 + HALF_STROKE
INK = "#E6EDF3"           # Brand Guide §03
ACCENT = "#00d4ff"        # --th-accent, from the live configurator
CARD = "#151923"          # the grid card, also the fallback back fill
RAIL_W = 16               # brand-true rail, left edge (was a fixed
                          # cyan->violet stripe until 1.9.2: launchers that
                          # colour-sample the icon - Monet's navigation glow
                          # picks its tint this way - read the rail, not the
                          # thin glyph strokes, and bleached a cyan halo
                          # around a red SmartTube. The rail keeps its shape
                          # across the pack; its colour is now each icon's
                          # own accent, top stop, sinking 55% toward the card
                          # at the bottom so no rail ever goes full-bleed.)
RAIL_PAD = 168            # rail inset from top/bottom, keeps ink under 72%
KICKER = 46               # uppercase mono category size
KICK_TRACK = 7.0          # .08em at this size, matching the site


def _mix(hex_from: str, hex_to: str, t: float) -> str:
    """Linear RGB blend of two #RRGGBB stops. tiny, self-contained: the only
    rider it has is the rail gradient, which wants accent-at-top sinking
    into the card, and colour liberties the sample size doesn't justify."""
    f = tuple(int(hex_from[i:i + 2], 16) for i in (1, 3, 5))
    b = tuple(int(hex_to[i:i + 2], 16) for i in (1, 3, 5))
    return "#" + "".join(f"{round(x + (y - x) * t):02X}" for x, y in zip(f, b))

# Wordmarks are Outfit Bold, converted to paths.
#
# Brand Guide §04 scopes the serif to display copy — splash headlines, question
# cards, doc covers — and says "never bold". An app card is not display copy;
# it is a label read at distance, which §04 assigns to the system-ui stack at
# 600-800 weight. One bundled family keeps every card the same optical voice
# instead of whatever sans the host happens to have (DejaVu vs Liberation vs
# missing), which is what made earlier wordmarks look mixed in a row.


def _measure(text, size):
    """Width of `text` at `size`, measured from Outfit Bold."""
    try:
        return type_measure(text, size, FONT_WORDMARK)
    except Exception:
        return len(text) * size * 0.52


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


def render(name, glyph, accent, category=None, *, monochrome=False,
         gradient=None, mark=None, style=None):
    """
    Centred glyph + wordmark, with the Core Builds signature:

      * a rail on the left edge, brand-true since 1.9.2: the icon's own
        accent, dimming toward the card (concept H, hue from the brand)
      * an uppercase mono category kicker above the name (concept H)
      * the same single-accent rounded-line glyph as the square icon

    `mark` carries the catalog's adaptive wordmark token so the glyph bubble
    sets the same short type as the square icon beside the full name.

    Concepts E/G/I were rejected: their signal lives in the card BACKGROUND,
    which we do not own — these PNGs are transparent and Projectivy draws
    whatever colour the user picked behind them. Rail, kicker and glyph are
    all drawn ink, so they survive any card colour.
    """
    accent = display_accent(accent, monochrome=monochrome)
    lines = split_name(name)
    size = fit_type(lines)
    text_w = max(_measure(l, size) for l in lines)
    scale = GLYPH_H / 512
    # Lay the lockup out by the glyph's drawn ink, not its grid box: the same
    # GAP between mark and name on every card, whatever the mark's shape.
    ink_l, ink_r = glyph_ink_x(glyph)
    glyph_w = (ink_r - ink_l) * scale
    total = glyph_w + GAP + text_w

    # Shift the lockup right so the rail never crowds the glyph.
    ink_x = (W - total) / 2 + RAIL_W
    start_x = ink_x - ink_l * scale

    gy = (H - GLYPH_H) / 2
    tx = ink_x + glyph_w + GAP

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
        kick_paths, _ = wordmark_spans([category], KICKER, tx, [ky], ACCENT)
        spans += kick_paths + "\n  "

    name_paths, _ = wordmark_spans(lines, size, tx, baselines, INK)
    spans += name_paths

    # The rail used to be the same cyan->violet stripe on 960 cards: the
    # launcher that reads "the app's colour" (Monet's navigation glow, any
    # Palette-swatch theme engine) met exactly one big saturated mass in our
    # art - the rail - and answered with our cyan instead of the brand.
    # Its stops are the icon's own accent now; when the catalog declares a
    # two-stop gradient the rail rides the same ramp as the glyph.
    rail_from, rail_to = (gradient if gradient else
                          (accent, _mix(accent, CARD, 0.55)))
    rail = (f'<defs><linearGradient id="cbRail" x1="0" y1="0" x2="0" y2="1">'
            f'<stop offset="0%" stop-color="{rail_from}"/>'
            f'<stop offset="100%" stop-color="{rail_to}"/>'
            f'</linearGradient></defs>'
            f'<rect x="70" y="{RAIL_PAD}" width="{RAIL_W}" '
            f'height="{H - RAIL_PAD * 2}" rx="{RAIL_W / 2}" fill="url(#cbRail)"/>')

    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
        f'viewBox="0 0 {W} {H}">\n'
        f'  {rail}\n'
        f'  <g transform="translate({start_x:.0f},{gy:.0f}) '
        f'scale({scale:.5f})">{classic_fit(glyph, apply_gradient(monoline(family_body(glyph, accent, mark, style)), accent, gradient) if gradient and not monochrome else monoline(family_body(glyph, accent, mark, style)))}</g>\n'
        f'  {spans}</svg>\n'
    )


def render_glyph_only(glyph, accent, *, monochrome=False, gradient=None,
                      mark=None, style=None):
    """Mark-only variant — used when a name adds nothing (e.g. Core Builds)."""
    accent = display_accent(accent, monochrome=monochrome)
    box = 380
    scale = box / 512
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
        f'viewBox="0 0 {W} {H}">\n'
        f'  <g transform="translate({(W - box) / 2:.0f},{(H - box) / 2:.0f}) '
        f'scale({scale:.5f})">{apply_gradient(monoline(family_body(glyph, accent, mark, style)), accent, gradient) if gradient and not monochrome else monoline(family_body(glyph, accent, mark, style))}</g>\n'
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
        from svg_renderer import svg2png
        from PIL import Image
    except (ImportError, OSError):
        return svg

    png = svg2png(bytestring=svg.encode(),
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
        mono = i.get("color_note") == "monochrome"
        if i.get("banner_style") == "glyph":
            svg = render_glyph_only(i["glyph"], i["color"], monochrome=mono,
                                    gradient=i.get("gradient"),
                                    mark=i.get("mark"),
                                    style=i.get("mark_style"))
            # Wordmark marks are not ink-centred on the 512 grid either; the
            # banner centring audit holds them to the same 3px tolerance.
            svg = recentre(svg)
        else:
            # banner_glyph overrides the square icon's mark on the 16:9 card
            # (e.g. TizenTube: banner carries the emblem's tip dot).
            svg = render(i["name"], i.get("banner_glyph", i["glyph"]), i["color"],
                         i.get("category"), monochrome=mono,
                         gradient=i.get("gradient"), mark=i.get("mark"),
                         style=i.get("mark_style"))
            svg = recentre(svg)
        (SVG_DIR / f"{i['drawable']}.svg").write_text(svg, encoding="utf-8")
    print(f"\u2713 banner SVGs written ({len(targets)}/{len(targets)}) "
          f"\u2192 assets/banners/")

    try:
        import io
        from PIL import Image
        from svg_renderer import svg2png
    except (ImportError, OSError):
        print("\u26a0 no SVG rasterizer is available \u2014 art skipped. "
              "Run: pip install -r tools/requirements.txt")
        return 0

    PNG_DIR.mkdir(parents=True, exist_ok=True)
    for i in targets:
        raw = svg2png(
            url=str(SVG_DIR / f"{i['drawable']}.svg"),
            output_width=PNG_W, output_height=PNG_H,
            background_color=None)
        Image.open(io.BytesIO(raw)).save(
            PNG_DIR / f"{i['drawable']}_banner{ART_EXT}", "WEBP", lossless=True)
    print(f"\u2713 banner WebP {PNG_W}x{PNG_H} transparent written "
          f"({len(targets)}/{len(targets)}) \u2192 res/drawable-nodpi/")

    # Identical banners ship once (renamed-app twins); stale banner PNGs go.
    banner_files = [PNG_DIR / f"{i['drawable']}_banner{ART_EXT}" for i in targets]
    banner_files = [f for f in banner_files if f.exists()]
    aliases = alias_identical(banner_files)
    write_aliases_file(VAL_DIR / "banner_aliases.xml", aliases,
                       "tools/build_banners.py")
    for stale in PNG_DIR.glob("*_banner.png"):
        if stale.name not in BRANDING_PNGS:
            stale.unlink()
    print(f"\u2713 banner_aliases.xml written ({len(aliases)} dup names \u2192 "
          f"canonical art); stale banner PNGs removed")
    print(f"\nBanners complete \u2014 {len(targets)} at 16:9, centred lockups "
          f"on the reference pack's measured grid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
