#!/usr/bin/env python3
"""
Core Builds Wu Pack — monochrome disc variant built from the Classic pack.

Style, after the WuPlay mark (the pack's own yin-yang play logo):

  * one white disc, no accent hues — the Wu style drops the colour, never
    the mapping;
  * the glyph is negative space: transparent holes knocked out of the disc,
    the way WuPlay's dark play wedge sits inside its white lobe;
  * every glyph is scaled to one optical radius (TARGET_R) so the discs read
    as a single family, with a fixed rim gap so no hole breaks the edge;
  * rendered stroke weight is constant pack-wide (PRIMARY / 0.82 / 0.68),
    independent of the per-icon scale;
  * transparent ground — draw on dark chrome (#000000 / #0D1117). A night
    keyline (presence, same as Classic) keeps the disc defined on bright
    wallpapers.

Reads the one source of truth — tools/catalog.json — plus the glyph
companion's generated appfilter so both packs map exactly the same
components. Writes deterministically:

  wu-pack/svg/<drawable>.svg            master vector (mask knockout)
  wu-pack/webp/<drawable>.webp          512px transparent lossless + keyline
  wu-pack/values/aliases.xml            byte-identical renders ship once
  wu-pack/appfilter.xml                 component -> disc (from glyphs/)
  wu-pack/drawable.xml                  icon browser grid (from glyphs/)
  wu-pack/preview.svg / preview.png     contact sheet on pure black

Owner-supplied art (PROVIDED) ships as supplied instead: WuPlay's square is
its owner's mark and its banner their whole lockup, from
wu-pack/source/wuplay.png, resampled and nothing else.

Receipts voice: prints exactly what was written, counted.
"""
import argparse
import base64
import io
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from build_icons import esc, validate                     # noqa: E402
from glyphs import (GRID, GLYPHS, classic_fit, family_body,  # noqa: E402
                    monoline)
from presence import apply_presence                       # noqa: E402
from drawable_art import ART_EXT, alias_identical, write_aliases_file  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"
METRICS = ROOT / "tools" / "glyph_metrics.json"
FITS = ROOT / "tools" / "classic_glyph_fit.json"
# Component mappings are taken from the shipped glyph companion, not
# re-derived, so the Wu pack and the Classic pack cannot disagree.
GLYPH_APPFILTER = ROOT / "glyphs" / "src" / "main" / "res" / "xml" / "appfilter.xml"
GLYPH_DRAWABLE = ROOT / "glyphs" / "src" / "main" / "res" / "xml" / "drawable.xml"
OUT = ROOT / "wu-pack"
SVG_DIR = OUT / "svg"
WEBP_DIR = OUT / "webp"
VAL_DIR = OUT / "values"

CENTER = GRID / 2          # 256
DISC_R = 211               # rim; +5px presence keyline lands exactly on SAFE
INSET = 20                 # minimum gap from any hole to the rim
TARGET_R = DISC_R - INSET  # every glyph's optical ink radius
PRIMARY = 40               # rendered primary weight (Classic canonical: 32)
SCALE_LO = 0.55            # bounds for the per-glyph optical fit
SCALE_HI = 1.60
MASK_ID = "wuDisc"
WHITE = "#FFFFFF"
CUT = "#000000"            # mask luminance: black cuts, white keeps

# Owner-supplied art. WuPlay's owner provided its mark and banner, and both
# ship exactly as provided: never redrawn as a glyph, never cut from a disc,
# no keyline. The source is the 5120x2880 transparent lockup, mark left and
# wordmark right. "mark" is the mark's circle in source pixels (1266 square);
# the wordmark's W starts at x=1673, inside that box, so every column from
# "word_x" on is cleared before the crop. Only the black lobe sits past it.
PROVIDED = {
    "wuplay": {"source": OUT / "source" / "wuplay.png",
               "mark": (459, 752, 1725, 2018),
               "word_x": 1673},
}
ART_ID = "wuArt"

_STROKE_RE = re.compile(r'stroke="[^"]*"')
_FILL_HEX_RE = re.compile(r'fill="(#[0-9A-Fa-f]{6}|url\([^)]*\))"')
_FILL_NONE_RE = re.compile(r'fill="none"')


def load_json(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def ink_radius(glyph, mark, metrics, fits):
    """Radius of the glyph's rendered ink from centre, after Classic fit.

    Metrics are hairline ink boxes of the raw geometry; the box is pushed
    out by half the rendered primary weight so the *edge* of the hole is
    what stays inside the rim. Family monograms carry a mark instead of
    their lone letter — the shell's box is the budget the mark is fitted
    into, so it stands in when the drawn mark differs.
    """
    box = metrics.get(glyph)
    if box is None and mark:
        # <family>_<L> shells are measured under their letter glyph names.
        box = metrics.get(glyph)
    if box is None:
        # Conservative fallback: the committed safe area.
        box = [40.0, 40.0, GRID - 40.0, GRID - 40.0]
    x0, y0, x1, y1 = box
    fit = fits.get(glyph)
    if fit:
        scale, cx, cy = fit
        x0, x1 = (x0 - cx) * scale + CENTER, (x1 - cx) * scale + CENTER
        y0, y1 = (y0 - cy) * scale + CENTER, (y1 - cy) * scale + CENTER
    half = PRIMARY / 2
    x0, y0, x1, y1 = x0 - half, y0 - half, x1 + half, y1 + half
    corners = ((x0, y0), (x1, y0), (x0, y1), (x1, y1))
    return max(((x - CENTER) ** 2 + (y - CENTER) ** 2) ** 0.5
               for x, y in corners)


def disc_body(icon, metrics, fits, stats):
    """The glyph body, sized to the disc and neutralised for the mask.

    The weight is pre-divided by the optical scale so what renders is
    PRIMARY / 0.82·PRIMARY / 0.68·PRIMARY everywhere in the pack, and the
    transform scales geometry only. Paints collapse to the mask's cut
    colour: hue is not part of this style, and mask luminance would
    otherwise vary per accent (a dark blue hole would only half-open).
    """
    glyph = icon["glyph"]
    mark = icon.get("mark")
    radius = ink_radius(glyph, mark, metrics, fits)
    scale = TARGET_R / radius
    if not (SCALE_LO <= scale <= SCALE_HI):
        stats["clamped"] += 1
        scale = min(SCALE_HI, max(SCALE_LO, scale))
    if scale < 0.75:
        stats["tight"] += 1
    weight = PRIMARY / scale
    body = monoline(family_body(glyph, WHITE, mark, icon.get("mark_style")),
                    weight=weight)
    body = classic_fit(glyph, body)
    body = _STROKE_RE.sub(f'stroke="{CUT}"', body)
    body = _FILL_HEX_RE.sub(f'fill="{CUT}"', body)
    body = _FILL_NONE_RE.sub('fill="none"', body)
    return (f'<g transform="translate({CENTER:g} {CENTER:g}) '
            f'scale({scale:.4f}) translate({-CENTER:g} {-CENTER:g})">'
            f"{body}</g>")


def png_data_uri(image):
    buf = io.BytesIO()
    image.save(buf, "PNG", optimize=True)
    return ("data:image/png;base64,"
            + base64.b64encode(buf.getvalue()).decode("ascii"))


def provided_square(drawable):
    """The owner's mark on the 512 grid: its circle scaled onto the disc
    (r=DISC_R) and centred, so it sits at a coin's size. Resampled only."""
    from PIL import Image
    spec = PROVIDED[drawable]
    with Image.open(spec["source"]) as src:
        src = src.convert("RGBA")
        src.paste((0, 0, 0, 0), (spec["word_x"], 0, src.width, src.height))
        mark = src.crop(spec["mark"])
    side = 2 * DISC_R
    tile = Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))
    tile.paste(mark.resize((side, side), Image.LANCZOS),
               (int(CENTER) - DISC_R, int(CENTER) - DISC_R))
    return tile


_provided_uri = {}


def provided_image(drawable, uid):
    """The owner's mark as one <image> on the 512 grid, the footprint a
    coin takes, so the sheet and banners place it like any other."""
    if drawable not in _provided_uri:
        _provided_uri[drawable] = png_data_uri(provided_square(drawable))
    return (f'<image id="{ART_ID}-{uid}" x="0" y="0" width="{GRID}" '
            f'height="{GRID}" href="{_provided_uri[drawable]}"/>')


def wu_svg(icon, uid, metrics, fits, stats):
    """One master: white disc rect behind a mask that keeps the disc and
    cuts the glyph. Standalone files use the drawable as uid; the contact
    sheet inlines many icons, so ids must be unique per composition.
    Owner-supplied art takes the same 512 footprint as its own image."""
    if icon["drawable"] in PROVIDED:
        return provided_image(icon["drawable"], uid)
    mid = f"{MASK_ID}-{uid}"
    inner = disc_body(icon, metrics, fits, stats)
    return (
        f'<defs><mask id="{mid}" maskUnits="userSpaceOnUse" x="0" y="0" '
        f'width="{GRID}" height="{GRID}">'
        f'<rect width="{GRID}" height="{GRID}" fill="{CUT}"/>'
        f'<circle cx="{CENTER:g}" cy="{CENTER:g}" r="{DISC_R}" fill="{WHITE}"/>'
        f"{inner}</mask></defs>"
        f'<rect width="{GRID}" height="{GRID}" fill="{WHITE}" '
        f'mask="url(#{mid})"/>')


def svg_document(icon, uid, metrics, fits, stats):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {GRID} '
            f'{GRID}" width="{GRID}" height="{GRID}">\n'
            f"  {wu_svg(icon, uid, metrics, fits, stats)}\n</svg>\n")


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


# --- component mappings, taken from the shipped glyph companion ------------

FURNITURE = ("<iconback", "<iconmask", "<iconupon", "<scale")


def derive_appfilter():
    """Copy the companion's appfilter, dropping the fallback furniture
    (its cb_back_* drawables live in the Classic app, not this folder).
    The scale line stays: it is not a drawable reference and it keeps the
    discs at the same apparent size the shipped pack uses."""
    if not GLYPH_APPFILTER.is_file():
        raise SystemExit(
            "❌ missing glyphs/src/main/res/xml/appfilter.xml — run "
            "python tools/build_icons.py first (the Wu pack derives its "
            "mappings from the shipped glyph companion).")
    kept, dropped = [], 0
    for line in GLYPH_APPFILTER.read_text(encoding="utf-8").splitlines():
        if line.lstrip().startswith(FURNITURE[:3]):
            dropped += 1
            continue
        if line.strip() == "<!-- Generated by tools/build_icons.py. Do not edit by hand. -->":
            continue
        kept.append(line)
    text = "\n".join(kept) + "\n"
    items = text.count("<item ")
    return text, items, dropped


def derive_drawable():
    if not GLYPH_DRAWABLE.is_file():
        raise SystemExit(
            "❌ missing glyphs/src/main/res/xml/drawable.xml — run "
            "python tools/build_icons.py first.")
    text = GLYPH_DRAWABLE.read_text(encoding="utf-8")
    text = text.replace(
        "<!-- Generated by tools/build_icons.py. Do not edit by hand. -->",
        "<!-- Generated by tools/build_wu_pack.py from the glyph companion's "
        "drawable.xml. Do not edit by hand. -->")
    text = text.replace('title="Square · ', 'title="Wu · ')
    return text, text.count("<item ")


# --- lookalike bookkeeping -------------------------------------------------

def mono_key(icon):
    """Shape identity without the accent — what this style can draw."""
    from glyphs import family_glyph_for
    glyph = icon["glyph"]
    mark = icon.get("mark") or ""
    shape = (glyph.rpartition("_")[0] + "__*"
             if mark and family_glyph_for(glyph) else glyph)
    return shape, mark, icon.get("mark_style") or ""


def mono_collisions(icons):
    """Groups that render one picture now that hue is gone. Classic kept
    them apart with accents; the Wu style reports them instead of failing —
    mapping is unaffected, only the browser tile repeats."""
    groups = {}
    for icon in icons:
        groups.setdefault(mono_key(icon), []).append(icon["name"])
    return {k: v for k, v in groups.items() if len(v) > 1}


# --- contact sheet ---------------------------------------------------------

def preview(icons, meta, comp_count, metrics, fits, stats):
    cols, cell = 8, 150
    rows = (len(icons) + cols - 1) // cols
    head = 300
    w, h = cols * cell, rows * cell + head + 64
    esc_fn = esc
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
         f'viewBox="0 0 {w} {h}">',
         f'<rect width="{w}" height="{h}" fill="#000000"/>']

    def coin(icon, uid, px, tx, ty):
        return (f'<g transform="translate({tx:g} {ty:g}) scale({px / GRID:.5f})">'
                f"{wu_svg(icon, uid, metrics, fits, stats)}</g>")

    brand = next((i for i in icons if i["drawable"] == "wuplay"), icons[0])
    s.append(coin(brand, "brand", 176, 44, 62))
    # Wordmark in the WuPlay manner: thin, lowercase, open tracking.
    s.append(f'<text x="252" y="126" fill="{WHITE}" '
             f'font-family="DejaVu Sans,sans-serif" font-size="76" '
             f'letter-spacing="14">wu pack</text>')
    s.append('<text x="254" y="162" fill="#8B949E" '
             'font-family="DejaVu Sans,sans-serif" font-size="19" '
             'letter-spacing="1.5">Core Builds Icon Pack · Wu style</text>')
    s.append(f'<text x="254" y="194" fill="#6E7681" '
             f'font-family="DejaVu Sans Mono,monospace" font-size="13.5">'
             f'{len(icons)} icons · {comp_count} components · '
             f'{len({mono_key(i) for i in icons})} unique shapes · '
             f'catalog v{meta.get("version", "?")}</text>')
    # Size ladder — the same coin at the sizes a launcher actually shows.
    lx = w - 414
    s.append(f'<text x="{lx}" y="74" fill="#6E7681" '
             f'font-family="DejaVu Sans Mono,monospace" font-size="12.5">'
             f'tile sizes →</text>')
    for px, dx in ((96, 0), (64, 112), (48, 196), (32, 264)):
        s.append(coin(brand, f"lad{px}", px, lx + dx, 88))
        s.append(f'<text x="{lx + dx + px / 2:g}" y="200" fill="#6E7681" '
                 f'text-anchor="middle" font-family="DejaVu Sans Mono,monospace" '
                 f'font-size="11">{px}</text>')
    s.append('<text x="44" y="270" fill="#8B949E" '
             'font-family="DejaVu Sans,sans-serif" font-size="15.5" '
             'letter-spacing="1">one white disc · the glyph as negative '
             'space · draw on dark chrome</text>')

    for n, icon in enumerate(icons):
        cx, cy = (n % cols) * cell, head + (n // cols) * cell
        s.append(coin(icon, f"g{n}", 112, cx + (cell - 112) / 2, cy + 4))
        label = esc_fn(icon["name"][:17])
        s.append(f'<text x="{cx + cell / 2}" y="{cy + cell - 16}" '
                 f'fill="#8B949E" font-family="DejaVu Sans Mono,monospace" '
                 f'font-size="10.5" text-anchor="middle">{label}</text>')

    s.append(f'<text x="{w / 2:g}" y="{h - 24}" fill="#484F58" '
             f'text-anchor="middle" font-family="DejaVu Sans Mono,monospace" '
             f'font-size="12.5">Built from tools/catalog.json by '
             f'tools/build_wu_pack.py — draw on dark chrome; the Wu style '
             f'drops the accent, never the mapping.</text>')
    s.append("</svg>")
    return "\n".join(s) + "\n"


# --- build -----------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    ap.add_argument("--svg-only", action="store_true",
                    help="skip rasterisation (masters + XML + preview)")
    args = ap.parse_args()

    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())

    errs = validate(icons, data.get("artwork"))
    if errs:
        print("Catalog rejected — %d problem(s):" % len(errs))
        for e in errs:
            print("  ✗ " + e)
        return 1

    metrics = load_json(METRICS)["metrics"]
    fits = load_json(FITS)["fits"]
    collisions = mono_collisions(icons)

    # 1. master SVGs
    stats = {"clamped": 0, "tight": 0}
    for icon in icons:
        write(SVG_DIR / f"{icon['drawable']}.svg",
              svg_document(icon, icon["drawable"], metrics, fits, stats))
    print(f"✓ SVG masters written ({len(icons)}/{len(icons)}) → wu-pack/svg/ "
          f"(optical fit: {stats['clamped']} clamped, {stats['tight']} under "
          f"0.75×)")

    # 2. WebP art through the same presence keyline Classic ships
    art_written = 0
    if not args.svg_only:
        try:
            from PIL import Image
            from svg_renderer import svg2png
            for icon in icons:
                if icon["drawable"] in PROVIDED:
                    # As supplied: straight from the source, no keyline.
                    art = provided_square(icon["drawable"])
                else:
                    png = svg2png(url=str(SVG_DIR / f"{icon['drawable']}.svg"),
                                  output_width=GRID, output_height=GRID,
                                  background_color=None)
                    art = apply_presence(Image.open(io.BytesIO(png)))
                WEBP_DIR.mkdir(parents=True, exist_ok=True)
                art.save(WEBP_DIR / f"{icon['drawable']}{ART_EXT}", "WEBP",
                         lossless=True)
                art_written += 1
            print(f"✓ WebP {GRID}px transparent written "
                  f"({art_written}/{len(icons)}) → wu-pack/webp/ "
                  f"(night keyline; {len(PROVIDED)} owner-supplied as "
                  f"supplied: {', '.join(sorted(PROVIDED))})")
        except (ImportError, OSError) as exc:
            print(f"❌ rasterisation failed ({exc}) — SVG masters are on "
                  f"disk. Run: .venv/bin/pip install -r tools/requirements.txt")
            return 1
        files = [WEBP_DIR / f"{i['drawable']}{ART_EXT}" for i in icons]
        aliases = alias_identical(files)
        VAL_DIR.mkdir(parents=True, exist_ok=True)
        write_aliases_file(VAL_DIR / "aliases.xml", aliases,
                           "tools/build_wu_pack.py")
        print(f"✓ values/aliases.xml written ({len(aliases)} dup names "
              f"→ canonical art)")

    # 3. mappings from the shipped companion
    appfilter, items, dropped = derive_appfilter()
    write(OUT / "appfilter.xml",
          appfilter.replace(
              "<resources>",
              "<resources>\n    <!-- Generated by tools/build_wu_pack.py "
              "from glyphs/src/main/res/xml/appfilter.xml "
              "(fallback furniture dropped). Do not edit by hand. -->", 1))
    print(f"✓ wu-pack/appfilter.xml written — {items} entries "
          f"({dropped} furniture lines dropped)")
    drawable_text, d_items = derive_drawable()
    write(OUT / "drawable.xml", drawable_text)
    print(f"✓ wu-pack/drawable.xml written ({d_items} browser entries)")

    # 4. contact sheet
    sheet = preview(icons, data.get("meta", {}),
                    items, metrics, fits, stats)
    write(OUT / "preview.svg", sheet)
    print("✓ wu-pack/preview.svg written (contact sheet)")
    try:
        from svg_renderer import svg2png
        svg2png(url=str(OUT / "preview.svg"),
                write_to=str(OUT / "preview.png"),
                output_width=1200, background_color="#000000")
        print("✓ wu-pack/preview.png written (README sheet)")
        # First page for README embeds — GitHub has no patience for 19k pixels.
        from PIL import Image
        with Image.open(OUT / "preview.png") as sheet:
            head = sheet.crop((0, 0, sheet.width, min(1500, sheet.height)))
            head.save(OUT / "preview-hero.png")
        print("✓ wu-pack/preview-hero.png written (first page)")
    except (ImportError, OSError):
        print("⚠ preview.png skipped (rasteriser unavailable)")

    # 5. receipts for what monochrome can and cannot separate
    if collisions:
        dup_icons = sum(len(v) for v in collisions.values())
        print(f"· monochrome lookalikes: {len(collisions)} shape groups "
              f"({dup_icons} icons) share art — accents separated them in "
              f"Classic; the mapping is unaffected")

    print(f"\nBuild complete — {len(icons)} icons, {items} component "
          f"entries, {art_written} WebP. Style: white disc r={DISC_R}, "
          f"rim gap ≥{INSET}px, primary weight {PRIMARY}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
