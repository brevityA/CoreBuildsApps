#!/usr/bin/env python3
"""
Core Builds Wu Pack — 16:9 banners in the Wu lockup.

The WuPlay banner grammar: mark left, wordmark right, on the card. Reuses
the Classic banner composition rules (tools/build_banners.py) so the two
packs sit on the same shelf — same canvas, same ink budget, same name fit
solver, same category kicker — with three Wu decisions:

  * the mark is the Wu coin (white disc, glyph as negative space);
  * the wordmark sets lighter and more tracked than Classic's stroke
    letters, after the WuPlay wordmark;
  * the kicker (VOD, STREAM, ...) keeps each app's brand accent — the one
    thread of brand colour on an otherwise monochrome card, and what a
    colour-sampling launcher reads off the icon.

Names (and banner_name / banner_glyph overrides) still carry the brand —
every banner is its app's own wordmark in the pack's letters. That also
means banners separate what monochrome squares cannot: the 13 IPTV players
share one coin but never one name.

Owner-supplied art (build_wu_pack.PROVIDED) is the exception: WuPlay's
banner is its owner's own lockup, resampled from the source and nothing
else — no coin, no stroke wordmark, no kicker, no recentring.

Writes, mirroring build_banners + build_banners_pack:
  wu-pack/banners/<drawable>.svg        1280x720 master (4x)
  wu-pack/webp/<d>_banner.webp          320x180 RGBA lossless WebP
  wu-pack/values/banner_aliases.xml     dup-name -> canonical art
  wu-pack/appfilter-banners.xml         component -> <d>_banner
  wu-pack/drawable-banners.xml          browser grid, Wu Banners sections
  wu-pack/preview-banners.svg/png       card mockups of a sample set

Receipts voice: prints exactly what was written, counted.
"""
import io
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import build_wu_pack as wu                                   # noqa: E402
from build_banners import (GAP, INK, INK_W, KICKER, KICK_CAP,   # noqa: E402
                           KICK_TRACK, KICK_WEIGHT, MAX_TYPE,
                           MIN_TYPE, recentre, split_name)
from drawable_art import ART_EXT, alias_identical, write_aliases_file  # noqa: E402
from glyphs import GRID, stroke_label, stroke_label_width      # noqa: E402
from icon_style import display_accent                         # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "wu-pack"
BANNER_DIR = OUT / "banners"
WEBP_DIR = OUT / "webp"
VAL_DIR = OUT / "values"
CATALOG = ROOT / "tools" / "catalog.json"
# The shipped pack's banner mapping — parity anchor, never written to.
SHIPPED_BANNER_APPFILTER = ROOT / "app" / "src" / "main" / "res" / "xml" / "appfilter.xml"

W, H = 1280, 720            # master grid: 4x the 320x180 target
PNG_W, PNG_H = 320, 180

COIN_BOX = 416              # grid box scale — Classic's GLYPH_H parity
COIN_SCALE = COIN_BOX / GRID
# The disc's own ink on the 512 grid (r=211; banners carry no presence
# keyline, exactly like Classic banners, which ship without one).
COIN_INK_L, COIN_INK_R = 256 - wu.DISC_R, 256 + wu.DISC_R
COIN_W = (COIN_INK_R - COIN_INK_L) * COIN_SCALE
BUDGET = INK_W - COIN_W - GAP

# WuPlay wordmark: a lighter skeleton than Classic's .19-of-cap name stroke,
# tracked wider than the .30 default so the letters breathe like the logo.
WU_NAME_WEIGHT = 0.15       # share of cap, floored at stroke_label's 8
WU_NAME_TRACK = 0.42        # share of cap between letters

BANNER_ITEM = re.compile(r'^(\s*<item component="[^"]+" drawable=")([^"]+)("/>)$')
GRID_ITEM = re.compile(r'^(\s*<item drawable=")([^"]+)(" />)$')


def name_weight(cap):
    return max(8.0, WU_NAME_WEIGHT * cap)


def name_gap(cap):
    return WU_NAME_TRACK * cap


def _measure(text, size):
    return stroke_label_width(text, size, gap=name_gap(.7 * size))


def fit_type(lines):
    """Largest size at which every line fits the Wu wordmark column."""
    size = MAX_TYPE
    while size > MIN_TYPE and max(_measure(l, size) for l in lines) > BUDGET:
        size -= 2
    return size


def coin_group(icon, uid, tx, ty, scale, stats):
    """The Wu coin — same mask knockout as the square icon — placed on the
    master grid. Ink is the disc itself, so placement is exact by math."""
    inner = wu.wu_svg(icon, uid, wu_metrics, wu_fits, stats)
    return (f'<g transform="translate({tx:.2f},{ty:.2f}) '
            f'scale({scale:.5f})">{inner}</g>')


def provided_banner(drawable, size):
    """The owner's whole lockup at `size`. The source is 16:9, so this is
    a straight resample."""
    from PIL import Image
    with Image.open(wu.PROVIDED[drawable]["source"]) as src:
        return src.convert("RGBA").resize(size, Image.LANCZOS)


_provided_uri = {}


def banner_inner(icon, uid, stats):
    """Lockup body (no <svg> wrapper): coin + kicker + name, for both the
    master files and the inline preview."""
    d = icon["drawable"]
    if d in wu.PROVIDED:
        if d not in _provided_uri:
            _provided_uri[d] = wu.png_data_uri(provided_banner(d, (W, H)))
        return (f'<image id="{wu.ART_ID}-{uid}" x="0" y="0" width="{W}" '
                f'height="{H}" href="{_provided_uri[d]}"/>')
    name = icon.get("banner_name") or icon["name"]
    glyph = icon.get("banner_glyph") or icon["glyph"]
    accent = display_accent(icon["color"],
                            monochrome=icon.get("color_note") == "monochrome")
    coin_icon = dict(icon, glyph=glyph)

    if icon.get("banner_style") == "glyph":
        # Mark-only — used when a name adds nothing. Coin centred, no type.
        tx = (W - GRID * COIN_SCALE) / 2
        ty = (H - GRID * COIN_SCALE) / 2
        return coin_group(coin_icon, uid, tx, ty, COIN_SCALE, stats)

    lines = split_name(name)
    size = fit_type(lines)
    text_w = max(_measure(l, size) for l in lines)
    # Lay out by drawn ink (the disc), so the visible gap is GAP on every
    # card — the same rule Classic switched to in 1.9.4.
    coin_w = COIN_W
    total = coin_w + GAP + text_w
    ink_x = (W - total) / 2
    tx = ink_x - COIN_INK_L * COIN_SCALE
    ty = (H - GRID * COIN_SCALE) / 2

    text_x = ink_x + coin_w + GAP
    lead = size * 1.08
    has_kick = bool(icon.get("category"))
    shift = (KICKER * 0.85) / 2 if has_kick else 0
    if len(lines) == 1:
        baselines = [H / 2 + size * 0.355 + shift]
    else:
        top = H / 2 - lead / 2 + size * 0.355 + shift
        baselines = [top, top + lead]

    spans = ""
    if has_kick:
        ky = baselines[0] - size * 0.92 - 10
        kick_paths, _ = stroke_label([icon["category"]], KICK_CAP / .7,
                                     text_x, [ky + 2], accent,
                                     weight=KICK_WEIGHT,
                                     gap=KICK_CAP * KICK_TRACK)
        spans += kick_paths + "\n  "
    name_paths, _ = stroke_label(lines, size, text_x, baselines, INK,
                                 weight=name_weight(.7 * size),
                                 gap=name_gap(.7 * size))
    spans += name_paths

    coin = coin_group(coin_icon, uid, tx, ty, COIN_SCALE, stats)
    return f"{coin}\n  {spans}"


def banner_svg(icon, uid, stats):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" '
            f'height="{H}" viewBox="0 0 {W} {H}">\n'
            f"  {banner_inner(icon, uid, stats)}\n</svg>\n")


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


# --- mappings, derived from this pack's square appfilter (which itself      #
# --- mirrors the shipped glyph companion) — the chain cannot disagree.      #

def derive_appfilter():
    if not (OUT / "appfilter.xml").is_file():
        raise SystemExit("❌ wu-pack/appfilter.xml missing — run "
                         "python tools/build_wu_pack.py first.")
    src = (OUT / "appfilter.xml").read_text(encoding="utf-8")
    out, items = [], 0
    for line in src.splitlines():
        m = BANNER_ITEM.match(line)
        if m:
            out.append(f"{m.group(1)}{m.group(2)}_banner{m.group(3)}")
            items += 1
        elif line.strip().startswith("<!--") and "build_wu_pack" in line:
            out.append('    <!-- Generated by tools/build_wu_banners.py from '
                       'wu-pack/appfilter.xml. Do not edit by hand. -->')
        else:
            out.append(line)
    return "\n".join(out) + "\n", items


def derive_drawable():
    if not (OUT / "drawable.xml").is_file():
        raise SystemExit("❌ wu-pack/drawable.xml missing — run "
                         "python tools/build_wu_pack.py first.")
    src = (OUT / "drawable.xml").read_text(encoding="utf-8")
    out, items = [], 0
    for line in src.splitlines():
        m = GRID_ITEM.match(line)
        if m:
            out.append(f"{m.group(1)}{m.group(2)}_banner{m.group(3)}")
            items += 1
        else:
            out.append(line.replace('title="Wu · ', 'title="Wu Banners · ')
                           .replace("from the glyph companion's "
                                    "drawable.xml",
                                    "from wu-pack/drawable.xml"))
    return "\n".join(out) + "\n", items


# --- preview ---------------------------------------------------------------

PREVIEW_PICKS = ["netflix", "kodi", "youtube", "wuplay", "bbc_iplayer", "kayo",
                 "disneyplus", "primevideo", "plex", "stremio", "stan", "vlc",
                 "spotify", "tubi", "amazon", "paramount"]


def preview(icons, stats):
    by_d = {i["drawable"]: i for i in icons}
    picks = [by_d[d] for d in PREVIEW_PICKS if d in by_d]
    cols, cw, ch, head = 4, 352, 236, 156
    rows = (len(picks) + cols - 1) // cols
    w, h = cols * cw, head + rows * ch + 44
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
         f'viewBox="0 0 {w} {h}">',
         f'<rect width="{w}" height="{h}" fill="#000000"/>',
         '<text x="44" y="74" fill="#FFFFFF" '
         'font-family="DejaVu Sans,sans-serif" font-size="46" '
         'letter-spacing="10">wu pack · banners</text>',
         '<text x="46" y="108" fill="#8B949E" '
         'font-family="DejaVu Sans,sans-serif" font-size="15.5">16:9 lockup '
         '— coin, light stroke wordmark, category in the brand\u2019s colour '
         f'· {len(icons)} banners</text>']
    for n, icon in enumerate(picks):
        cx, cy = (n % cols) * cw + 16, head + (n // cols) * ch
        # The card the banner really sits on, so holes show launcher chrome.
        s.append(f'<rect x="{cx}" y="{cy}" width="{PNG_W}" height="{PNG_H}" '
                 f'rx="18" fill="#151923" stroke="rgba(255,255,255,.06)"/>')
        s.append(f'<g transform="translate({cx},{cy}) '
                 f'scale({1 / 4:.5f})">{banner_inner(icon, f"p{n}", stats)}</g>')
    s.append(f'<text x="{w / 2:g}" y="{h - 18}" fill="#484F58" '
             f'text-anchor="middle" font-family="DejaVu Sans Mono,monospace" '
             f'font-size="12.5">Built from tools/catalog.json by '
             f'tools/build_wu_banners.py</text>')
    s.append("</svg>")
    return "\n".join(s) + "\n"


# --- build -----------------------------------------------------------------

def main():
    global wu_metrics, wu_fits
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())
    wu_metrics = wu.load_json(wu.METRICS)["metrics"]
    wu_fits = wu.load_json(wu.FITS)["fits"]
    stats = {"clamped": 0, "tight": 0}

    BANNER_DIR.mkdir(parents=True, exist_ok=True)
    for icon in icons:
        # Measure-and-shift the drawn ink to dead centre, the same audit
        # Classic banners pass: grids and letters carry side bearings.
        svg = banner_svg(icon, icon["drawable"], stats)
        if icon["drawable"] not in wu.PROVIDED:
            svg = recentre(svg)    # owner-supplied art keeps its own layout
        write(BANNER_DIR / f"{icon['drawable']}.svg", svg)
    print(f"✓ banner SVG masters written ({len(icons)}/{len(icons)}) "
          f"→ wu-pack/banners/ ({W}×{H})")

    # WebP at target size — banners ship without the presence keyline,
    # exactly like Classic's (the keyline is a square-icon/dock rule).
    art_written = 0
    try:
        from PIL import Image
        from svg_renderer import svg2png
        WEBP_DIR.mkdir(parents=True, exist_ok=True)
        for icon in icons:
            if icon["drawable"] in wu.PROVIDED:
                art = provided_banner(icon["drawable"], (PNG_W, PNG_H))
            else:
                raw = svg2png(url=str(BANNER_DIR / f"{icon['drawable']}.svg"),
                              output_width=PNG_W, output_height=PNG_H,
                              background_color=None)
                art = Image.open(io.BytesIO(raw))
            art.save(WEBP_DIR / f"{icon['drawable']}_banner{ART_EXT}", "WEBP",
                     lossless=True)
            art_written += 1
        print(f"✓ banner WebP {PNG_W}×{PNG_H} transparent written "
              f"({art_written}/{len(icons)}) → wu-pack/webp/")
    except (ImportError, OSError) as exc:
        print(f"❌ rasterisation failed ({exc}) — banner masters are on disk. "
              f"Run: .venv/bin/pip install -r tools/requirements.txt")
        return 1
    files = [WEBP_DIR / f"{i['drawable']}_banner{ART_EXT}" for i in icons]
    aliases = alias_identical(files)
    VAL_DIR.mkdir(parents=True, exist_ok=True)
    write_aliases_file(VAL_DIR / "banner_aliases.xml", aliases,
                       "tools/build_wu_banners.py")
    print(f"✓ values/banner_aliases.xml written ({len(aliases)} dup names "
          f"→ canonical art)")

    appfilter, items = derive_appfilter()
    write(OUT / "appfilter-banners.xml", appfilter)
    print(f"✓ wu-pack/appfilter-banners.xml written — {items} entries")
    # Parity with the shipped banner map: same components, same suffix rule.
    shipped = len(re.findall(r"<item component=", SHIPPED_BANNER_APPFILTER.read_text(encoding="utf-8")))
    if items != shipped:
        print(f"⚠ shipped banner appfilter has {shipped} entries — "
              f"wu-pack derives {items}; run tools/build_icons.py and "
              f"tools/build_banners_pack.py to refresh the source chain")
    else:
        print(f"· parity: shipped banner appfilter also carries {shipped} "
              f"entries")

    grid, grid_items = derive_drawable()
    write(OUT / "drawable-banners.xml", grid)
    print(f"✓ wu-pack/drawable-banners.xml written ({grid_items} browser "
          f"entries)")

    write(OUT / "preview-banners.svg", preview(icons, stats))
    print("✓ wu-pack/preview-banners.svg written")
    try:
        from svg_renderer import svg2png
        svg2png(url=str(OUT / "preview-banners.svg"),
                write_to=str(OUT / "preview-banners.png"),
                output_width=1408, background_color="#000000")
        print("✓ wu-pack/preview-banners.png written")
    except (ImportError, OSError):
        print("⚠ preview-banners.png skipped (rasteriser unavailable)")

    print(f"\nBanners complete — {len(icons)} at {PNG_W}×{PNG_H}, Wu "
          f"lockup, brand-accent kicker. Coin {COIN_W:.0f}u ink, gap {GAP}, "
          f"wordmark {WU_NAME_WEIGHT:.2f}×cap @ {WU_NAME_TRACK:.2f} track.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
