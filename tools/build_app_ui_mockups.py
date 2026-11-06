#!/usr/bin/env python3
"""
Render the app's UI mockups as 1920x1080 TV frames, from the source they depict.

Why this exists
---------------
The four sheets this replaces (app-ui-apply-wallpapers, app-ui-sideload-round,
app-ui-auditor, app-ui-inspector-suite) were drawn by hand next to the code
they illustrated, and hand-drawn things drift: they showed a stacked-rows home
screen activity_main.xml never had, unlabeled grid tiles, "943 icons - 1765
components" (1765 is appfilter component *items*; the pack counts 943 icons and
the manifest counts components differently again), a v1.8.20 header and chip
counts from an older tranche. From the owner's chair that read as "the UI
mockups look nothing like what was published", and it was unfalsifiable: no
gate, no generator, nothing that would ever notice.

So the mockups are now output, like the icons and banners: every label comes
from res/values/strings.xml through the same fmt substitution the Kotlin does,
every metric from res/values/dimens.xml at the 1080p scale (960dp wide, 2px per
dp), every colour from res/values/colors.xml, chip labels and counts from the
generated icon_pack.xml arrays and MainActivity's CHIP_ORDER, suite rows from
the generated suite_hub.xml, the update bar's bullets from
app/src/main/assets/version.json, the inspector's component list from the bundled
appfilter.xml asset, and the artwork is the real bundled PNG - icon tiles paste
drawable-nodpi, wallpaper tiles paste wallpapers_thumbs. The auditor frame's QR
is a real scannable code of the same generated deep link AuditorActivity builds
(issue_prefill.xml fmt + URL-encoded args), so scanning the mockup opens the
same prefilled issue form scanning the TV would.

What is example data is said out loud: each frame carries a caption band under
the 1080p panel naming which values are invented (focused tile, detected
launchers, install states, unmapped-app rows), because a mockup that hides its
fictions ends up quoting them in a release note.

Type: since 2.6.0 the app is set in Outfit, and the frames use the same four
cuts the APK ships (app/src/main/res/font, SIL OFL) for the role each layout
names: regular, medium on pills and rail rows, semibold on titles and
headings, bold on the call to action. The mono roles (counts, components,
fontFamily="monospace") are DejaVu Sans Mono, standing in for the device's
monospace. The Glyphs Pack's screen keeps tools/fonts' Outfit Bold as the
stand-in for its system sans. Icons are the res/drawable vectors themselves,
tinted as their views tint them and rasterised through resvg.

The 2.6.0 look in short (rail and stage on Home and Wallpapers, one header on
every other screen, pills, Core EQ's rows and sections) is drawn from the
drawables' own values: each primitive below names the drawable it paints.

Usage
-----
    python tools/build_app_ui_mockups.py            # write docs/app-ui-*.png
                                                    #   and docs/app-ui-mockups.json
    python tools/build_app_ui_mockups.py --check    # fail if the committed frames
                                                    #   were not generated from the
                                                    #   sources now in the tree
    python tools/build_app_ui_mockups.py --report   # renderer/pixel diagnostics

What --check proves, and what it deliberately does not
------------------------------------------------------
It proves the frames in docs/ were generated from the sources now in the tree:
every layout, values file, the catalog, the update manifest, the bundled
appfilter and wallpaper manifest, the fonts, the generator's own source, and
each raster a frame pastes are hashed into `docs/app-ui-mockups.json`, along
with a per-frame digest of what the generator drew - every string, its
position, its size, its font and its colour, every pasted artwork and its box.
Edit any of that without regenerating and the check names the files that moved.

It does not compare the PNG bytes, and that is not laziness. Rendering text is
not reproducible across machines: the nine frames committed from CPython 3.11
differed from the same generator's output on a runner's 3.12 in every frame,
2-6% of pixels, mean channel delta under 5 - antialiasing, with identical pins
(Pillow 10.4.0, fontTools 4.59.0, resvg-py 0.4.0) and byte-identical input
rasters on both sides. build.yml already scopes its asset drift gate to text
for the same reason ("a stale PNG is cosmetic, a stale appfilter is a broken
pack"), and the same ranking holds here: a frame whose pixels moved with the
runner's rasteriser is cosmetic, a frame depicting a layout nobody ships is
the defect. The structural digest is also the stricter half - it fails on a
one-dp move that changes a handful of pixels, which a byte comparison would
only catch by accident of encoding.

The committed PNGs are still the artefact people look at, and `--report`
still prints both sides' hashes and a pixel-level difference, because when two
machines disagree that is the only way to see how far apart they are.
"""
from __future__ import annotations

import hashlib
import io
import json
import os
import re
import sys
import urllib.parse
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from drawable_art import art_path, read_aliases  # noqa: E402
from alternates import is_alternate  # noqa: E402

from PIL import Image, ImageDraw, ImageFont

import qrcode
import resvg_py
from qrcode.constants import ERROR_CORRECT_M

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
RES = ROOT / "app/src/main/res"
FONTS = Path(__file__).resolve().parent / "fonts"

ANDROID = "{http://schemas.android.com/apk/res/android}"

# A 1080p TV is 960dp wide, so one dp is two pixels. sp follows: the pack ships
# no font-scale overrides and a mockup of a 10-foot UI should be measured at
# the density the UI is designed for.
SCALE = 2
W, H = 1920, 1080
CAPTION_H = 64


def dp(value: float) -> int:
    return int(round(value * SCALE))


# ---------------------------------------------------------------------------
# Source of truth
# ---------------------------------------------------------------------------

def _xml(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


def load_strings() -> dict[str, str]:
    out = {}
    for element in _xml(RES / "values/strings.xml"):
        if element.tag == "string":
            # Android collapses the XML indentation of multi-line bodies into
            # the single spaces the TextView shows.
            raw = re.sub(r"\s+", " ", "".join(element.itertext())).strip()
            # strings.xml escapes apostrophes; the TextView shows them bare.
            out[element.get("name")] = raw.replace("\\'", "'")
        elif element.tag == "plurals":
            # One entry per quantity, as `name#one` / `name#other`.
            for item in element:
                raw = re.sub(r"\s+", " ", "".join(item.itertext())).strip()
                out[f"{element.get('name')}#{item.get('quantity')}"] = raw.replace("\\'", "'")
    return out


def load_dimens() -> dict[str, float]:
    out = {}
    for element in _xml(RES / "values/dimens.xml"):
        if element.tag == "dimen":
            out[element.get("name")] = float((element.text or "0").strip()[:-2] or 0)
    return out


def load_colors() -> dict[str, str]:
    out = {}
    for element in _xml(RES / "values/colors.xml"):
        if element.tag == "color":
            out[element.get("name")] = element.text.strip()
    return out


def load_arrays(path: Path, names: list[str]) -> dict[str, list[str]]:
    root = _xml(path)
    out = {}
    for name in names:
        for element in root:
            if element.tag in ("string-array", "integer-array") and element.get("name") == name:
                out[name] = [
                    re.sub(r"\s+", " ", "".join(item.itertext())).strip()
                    for item in element.findall("item")
                ]
    return out


def fmt(template: str, *args) -> str:
    """Android's String.format, for the %1$s / %2$d this repo's strings use.

    A literal percent is written %% in the resource — the Formatter's only
    escape — and comes out single here after the positional substitution,
    which is the order the runtime itself formats in (the auditor's deep
    link rides on this: its URL-encoded title prefix is %%5B in the resource
    and %5B in the URL). With no args, Android serves getString(id) raw and
    never runs the format pass at all, so the %% is left alone.
    """
    out = template
    for index, value in enumerate(args, start=1):
        out = out.replace(f"%{index}$s", str(value)).replace(f"%{index}$d", str(value))
    return out.replace("%%", "%") if args else out


STRINGS = load_strings()
DIMENS = load_dimens()
COLORS = load_colors()

ICON_PACK = load_arrays(
    RES / "values/icon_pack.xml",
    ["icon_pack", "icon_names", "icon_categories", "icon_bespoke"],
)
SUITE = load_arrays(
    RES / "values/suite_hub.xml",
    ["suite_hub_names", "suite_hub_pkgs", "suite_hub_codes"],
)
VERSION = __import__("json").load(open(ROOT / "app/src/main/assets/version.json"))

# The auditor's deep link is generated, not typed: build_issue_prefills.py writes
# it from the issue forms, and AuditorActivity only URL-encodes its three args.
STRINGS.update(load_strings.__wrapped__() if hasattr(load_strings, "__wrapped__") else {})
for _el in _xml(RES / "values/issue_prefill.xml"):
    if _el.tag == "string":
        STRINGS[_el.get("name")] = re.sub(r"\s+", " ", "".join(_el.itertext())).strip()

# MainActivity's chip order is the catalogue's own; read it rather than retyping
# it, so a reordered chip row shows up in the mockup instead of drifting.
CHIP_ORDER = re.findall(r'"([A-Z]+)" to "([^"]+)"',
                        (ROOT / "app/src/main/java/tv/corebuilds/iconpack/MainActivity.kt").read_text())

PACK = ICON_PACK["icon_pack"]
NAMES = ICON_PACK["icon_names"]
CATS = ICON_PACK["icon_categories"]
BESPOKE = [b == "1" for b in ICON_PACK["icon_bespoke"]]
# MainActivity.iconCount: apps, so an alternate's extra tile is not counted.
APPS = [not is_alternate(d) for d in PACK]
ICON_COUNT = sum(APPS)

ICONS_DIR = RES / "drawable-nodpi"
ALIASES = read_aliases(RES / "values")
THUMBS_DIR = ROOT / "app/src/main/assets/wallpapers_thumbs"


# The header's second number and the wallpapers row's count are read, not typed:
# 1765 component items in the bundled appfilter, and the wallpaper manifest the
# browser actually loads.
APPFILTER_TEXT = (ROOT / "app/src/main/assets/appfilter.xml").read_text(encoding="utf-8")
COMPONENT_COUNT = len(re.findall(r'component="ComponentInfo', APPFILTER_TEXT))
WALLPAPERS = __import__("json").load(
    open(ROOT / "app/src/main/assets/manifest/wallpapers.json"))["wallpapers"]

# The motion loops the browser appends to the catalog (WallpapersActivity:
# `WallpaperCatalog.load(this) + LiveLoop.asWallpapers()`), so the grid carries
# them behind a LIVE badge. Parsed from the Kotlin rather than copied here: a
# loop added there and missing from this frame is exactly the drift the mockup
# check exists to catch. Their still frames live with the motion art, not in
# the bundled wallpaper thumbs.
LIVE_THUMBS = ROOT / "Motion/live/thumbs"


def live_loops() -> list[dict]:
    """Every loop the wallpaper browser lists, from the Kotlin that lists them.

    Two generated lists since Core Views shipped: the Motion line's in
    `LiveLoop.kt` (`MOTION_LOOPS`) and the aerial season's in
    `CoreViewsLoop.kt` (`LOOPS`). `LiveLoop.LOOPS` is the two concatenated, so
    reading only the first file would draw a grid eleven tiles short of the
    app's - the drift this check exists to catch. Both live under the one Live
    chip: `Loop.series` defaults to `LiveLoop.SERIES`, and neither set
    overrides it.
    """
    source = ROOT / "app/src/main/java/tv/corebuilds/iconpack"
    series = re.search(r'const val SERIES = "([^"]+)"',
                       (source / "LiveLoop.kt").read_text(encoding="utf-8")).group(1)
    loops = []
    for name, list_name in (
        ("LiveLoop.kt", "MOTION_LOOPS"),      # the Motion line's forty-eight
        ("CoreViewsLoop.kt", "LOOPS"),        # the Core Views season's twelve
    ):
        src = (source / name).read_text(encoding="utf-8")
        body = re.search(rf"val {list_name}: List<Loop> = listOf\((.*?)\n    \)",
                         src, re.DOTALL).group(1)
        base = re.search(r'RAW_BASE\s*=\s*\n?\s*"([^"]+)"', src).group(1)
        titles = re.findall(r'title = "([^"]+)"', body)
        files = re.findall(r'fileName = "([^"]+)"', body)
        loops += [
            {"name": title, "series": series, "url": base + file_name, "live": True}
            for title, file_name in zip(titles, files)
        ]
    return loops


LIVE_LOOPS = live_loops()

# Example data, named as such in every caption: the launchers this TV has, by
# their ApplyIconPack display names, HOME first. The line under Apply lists
# them and every one after the first is an ALSO APPLIES TO chip. One, the
# common case and the one the screen tests open on: since 2.6.0 the rail is a
# ScrollView, and with two more launchers its foot rows (Support, Settings,
# About) scroll below the panel - true on a TV, and no screenshot of the rail.
EXAMPLE_LAUNCHERS = ["Projectivy Launcher"]


def components_of(drawable: str) -> list[str]:
    xml = (ROOT / "app/src/main/assets/appfilter.xml").read_text(encoding="utf-8")
    # Same rule as InspectorActivity.componentsFor: appfilter maps components to
    # the banner drawable, so the square name alone matches nothing.
    return re.findall(
        rf'component="ComponentInfo\{{([^}}]+)\}}"\s+drawable="{drawable}(?:_banner)?"',
        xml
    )


# ---------------------------------------------------------------------------
# Drawing kit
# ---------------------------------------------------------------------------

_FONT_CACHE: dict[tuple[str, int], ImageFont.FreeTypeFont] = {}


def font(role: str, size_sp: float, bold: bool = False) -> ImageFont.FreeTypeFont:
    """role: 'sans' (Outfit, the pack's family) or 'mono' (DejaVu Sans Mono)."""
    px = max(8, dp(size_sp))
    key = (role + ("-bold" if bold else ""), px)
    if key not in _FONT_CACHE:
        path = {
            ("sans", False): FONTS / "Outfit-Bold.ttf",
            ("sans", True): FONTS / "Outfit-ExtraBold.ttf",
            ("mono", False): FONTS / "DejaVuSansMono.ttf",
            ("mono", True): FONTS / "DejaVuSansMono-Bold.ttf",
        }[(role, bold)]
        # Layout engine pinned to BASIC on purpose, and this is the pin that
        # matters. Pillow uses raqm/harfbuzz for shaping and measurement when
        # the wheel can find it, and falls back to FreeType's own advances when
        # it cannot - so whether a runner has libraqm installed changes how wide
        # a string measures, which changes where wrap() breaks a line and where
        # a centred label sits. Same Pillow version, same fonts, same sources,
        # different frames: nine of nine differed between a workspace on CPython
        # 3.11 without raqm and a runner on 3.12, by 2-6% of pixels. BASIC is
        # available everywhere, so the frames are laid out by the same rules on
        # every machine that renders them.
        _FONT_CACHE[key] = ImageFont.truetype(
            str(path), px, layout_engine=ImageFont.Layout.BASIC)
    return _FONT_CACHE[key]


def colour(name: str, alpha: int | None = None) -> tuple:
    """Android resource colors are AARRGGBB; #RRGGBB means opaque."""
    hexed = COLORS[name].lstrip("#")
    if len(hexed) == 8:
        a, hexed = int(hexed[0:2], 16), hexed[2:]
    else:
        a = 255
    rgb = tuple(int(hexed[i:i + 2], 16) for i in (0, 2, 4))
    return rgb + (alpha if alpha is not None else a,)


def rrect(draw: ImageDraw.ImageDraw, box, radius: int, fill=None, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def text_width(draw: ImageDraw.ImageDraw, s: str, f) -> int:
    return int(draw.textlength(s, font=f))


# What the generator drew, per frame: the machine-independent half of the
# drift check. Text and pasted art are the content; geometry and colour come
# from dimens/colors, which the input hashes already cover.
RECORD: list[str] = []


def draw_text(draw, xy, s, f, fill, anchor="la"):
    RECORD.append("T|{}|{}|{}|{}|{}|{}".format(
        tuple(xy), s, getattr(f, "size", "?"),
        Path(getattr(f, "path", "") or "").name, tuple(fill), anchor))
    draw.text(xy, s, font=f, fill=fill, anchor=anchor)


def wrap(draw, s: str, f, max_width: int) -> list[str]:
    lines, current = [], ""
    for word in s.split(" "):
        trial = f"{current} {word}".strip()
        if text_width(draw, trial, f) <= max_width or not current:
            current = trial
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def cta_gradient(size: tuple[int, int]) -> Image.Image:
    """bg_cta: linear-gradient(135deg, cb_cta_start, cb_cta_end)."""
    w, h = size
    start, end = colour("cb_cta_start"), colour("cb_cta_end")
    img = Image.new("RGB", (w, h))
    pixels = img.load()
    span = max(1, (w + h) - 2)
    for y in range(h):
        for x in range(w):
            t = (x + y) / span
            pixels[x, y] = tuple(int(a + (b - a) * t) for a, b in zip(start, end))
    return img


def masked(img: Image.Image, radius: int) -> Image.Image:
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1],
                                           radius=radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def focus_ring(img: Image.Image, box, glow: bool = True):
    """bg_card's focused state: 3dp cyan ring, 3dp gap of night, card inside.
    The icon and wallpaper tiles keep bg_card in 2.6.0: its 6dp inset is the
    grid's gutter."""
    draw = ImageDraw.Draw(img, "RGBA")
    x0, y0, x1, y1 = box
    ring = dp(DIMENS["cb_focus_ring"])
    radius = dp(DIMENS["cb_radius_card"])
    if glow:
        rrect(draw, [x0 - dp(4), y0 - dp(4), x1 + dp(4), y1 + dp(4)],
              radius + dp(4), outline=colour("cb_focus_glow"), width=dp(5))
    rrect(draw, [x0, y0, x1, y1], radius, fill=colour("cb_signal_cyan"))
    rrect(draw, [x0 + ring, y0 + ring, x1 - ring, y1 - ring],
          dp(DIMENS["cb_radius_card_gap"]), fill=colour("cb_night"))
    inset = dp(DIMENS["cb_focus_inset"])
    rrect(draw, [x0 + inset, y0 + inset, x1 - inset, y1 - inset],
          dp(DIMENS["cb_radius_card_inner"]), fill=colour("cb_panel"))


def card(img: Image.Image, box):
    draw = ImageDraw.Draw(img, "RGBA")
    x0, y0, x1, y1 = box
    inset = dp(DIMENS["cb_focus_inset"])
    rrect(draw, [x0 + inset, y0 + inset, x1 - inset, y1 - inset],
          dp(DIMENS["cb_radius_card_inner"]), fill=colour("cb_card"),
          outline=(255, 255, 255, 0x0F), width=1)


# ---------------------------------------------------------------------------
# The 2.6.0 kit: Core EQ 1.12's rail-and-stage grammar, as the app now draws it
# ---------------------------------------------------------------------------

# The app's own Outfit cuts, one file per weight, because the layouts name a
# cut per role (textFontWeight needs API 28; the pack's floor is 21): regular
# body copy, medium on pills and rail rows, semibold on titles and headings,
# bold on the call to action.
APP_FONTS = RES / "font"
DRAWABLES = RES / "drawable"


def ofont(size_sp: float, weight: str = "regular") -> ImageFont.FreeTypeFont:
    px = max(8, dp(size_sp))
    key = ("outfit-" + weight, px)
    if key not in _FONT_CACHE:
        # BASIC for the reason font() gives.
        _FONT_CACHE[key] = ImageFont.truetype(
            str(APP_FONTS / f"outfit_{weight}.ttf"), px,
            layout_engine=ImageFont.Layout.BASIC)
    return _FONT_CACHE[key]


def line_h(f) -> int:
    """One line of a TextView: ascent plus descent, as includeFontPadding
    lays it out."""
    ascent, descent = f.getmetrics()
    return ascent + descent


def fit(draw, s: str, f, width: int) -> str:
    """maxLines="1" ellipsize="end"."""
    if text_width(draw, s, f) <= width:
        return s
    while s and text_width(draw, s + "…", f) > width:
        s = s[:-1]
    return s.rstrip() + "…"


def tracked(draw, xy, s: str, f, fill, tracking: float) -> int:
    """A label with Android's letterSpacing (in em), drawn glyph by glyph.
    Returns its width."""
    RECORD.append("S|{}|{}|{}|{}|{}|{}".format(
        tuple(xy), s, f.size, Path(f.path).name, tuple(fill), tracking))
    x, y = xy
    for ch in s:
        draw.text((x, y), ch, font=f, fill=fill)
        x += draw.textlength(ch, font=f) + tracking * f.size
    return int(x - xy[0])


def eyebrow(draw, x: int, y: int, s: str, size_sp: float | None = None) -> int:
    """CoreBuilds.Eyebrow: cyan caps, semibold, 0.14em. Returns its bottom."""
    f = ofont(size_sp or DIMENS["cb_text_kicker"], "semibold")
    tracked(draw, (x, y), s.upper(), f, colour("cb_signal_cyan"), 0.14)
    return y + line_h(f)


def menu_heading(draw, x: int, y: int, s: str) -> int:
    """CoreBuilds.MenuHeading: slate caps, semibold, 0.12em. Returns its bottom."""
    f = ofont(DIMENS["cb_text_kicker"], "semibold")
    tracked(draw, (x, y), s.upper(), f, colour("cb_slate"), 0.12)
    return y + line_h(f)


def _android_alpha(value: str | None) -> int:
    hexed = (value or "").lstrip("#")
    if not hexed:
        return 0
    return int(hexed[:2], 16) if len(hexed) == 8 else 255


def vector(img: Image.Image, name: str, box, tint) -> None:
    """A res/drawable vector icon in `box`, tinted as its view tints it.

    Every icon the screens draw is a monoline path set in white that the view
    recolours (drawableTint, tint), so the tint replaces each painted path's
    colour and an unpainted one stays unpainted. resvg rasterises it, the
    renderer tools/requirements.txt already pins; the record carries the
    drawable, its box and its tint, which is the machine-independent part."""
    root = _xml(DRAWABLES / f"{name}.xml")
    r, g, b = tint[:3]
    paths = []
    for p in root.iter("path"):
        def paint(attr: str) -> str:
            return f"rgb({r},{g},{b})" if _android_alpha(p.get(ANDROID + attr)) else "none"
        paths.append(
            '<path d="{}" fill="{}" stroke="{}" stroke-width="{}" '
            'stroke-linecap="{}" stroke-linejoin="{}"/>'.format(
                p.get(ANDROID + "pathData"), paint("fillColor"), paint("strokeColor"),
                p.get(ANDROID + "strokeWidth", "0"),
                p.get(ANDROID + "strokeLineCap", "butt"),
                p.get(ANDROID + "strokeLineJoin", "miter")))
    x0, y0, x1, y1 = (int(v) for v in box)
    w, h = x1 - x0, y1 - y0
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {} {}" '
           'width="{}" height="{}">{}</svg>').format(
        root.get(ANDROID + "viewportWidth"), root.get(ANDROID + "viewportHeight"),
        w, h, "".join(paths))
    png = resvg_py.svg_to_bytes(svg_string=svg, width=w, height=h, skip_system_fonts=True)
    art = Image.open(io.BytesIO(bytes(png))).convert("RGBA")
    img.paste(art, (x0, y0), art)
    RECORD.append(f"V|{name}|{(x0, y0, x1, y1)}|{tuple(tint)}")


def vector_dp(name: str) -> float:
    """A vector's intrinsic width: the size a drawableStart draws it at."""
    return float(_xml(DRAWABLES / f"{name}.xml").get(ANDROID + "width")[:-2])


# Label colour per pill kind: color/cta_text, color/pill_ghost_text,
# color/ghost_text (enabled).
PILL_INK = {"cta": "cb_cta_ink", "ghost": "cb_signal_cyan", "plain": "cb_ink"}


def _pill_font(kind: str):
    return ofont(DIMENS["cb_text_button"], "bold" if kind == "cta" else "medium")


def pill_width(draw, label: str, kind: str = "plain", icon=None,
               pad_start: float | None = None) -> int:
    start = DIMENS["cb_pill_padding"] if pad_start is None else pad_start
    extra = dp(vector_dp(icon[0])) + dp(icon[1]) if icon else 0
    return (dp(start) + extra + text_width(draw, label, _pill_font(kind))
            + dp(DIMENS["cb_pill_padding"]))


def pill(img: Image.Image, box, label: str, kind: str = "plain", focused: bool = False,
         activated: bool = False, icon=None, pad_start: float | None = None) -> None:
    """Widget.CoreBuilds.Pill in the state a frame shows it: kind 'cta'
    (bg_pill_cta), 'ghost' (bg_pill_ghost) or 'plain' (bg_pill). `icon` is
    (drawable, drawablePadding dp): a TextView's drawableStart sits at the
    start padding at its own size, and the label centres in what is left."""
    draw = ImageDraw.Draw(img, "RGBA")
    x0, y0, x1, y1 = box
    r = (y1 - y0) // 2
    ring = dp(DIMENS["cb_focus_ring"])
    if kind == "cta":
        grad = masked(cta_gradient((x1 - x0, y1 - y0)), r)
        img.paste(grad, (x0, y0), grad)
        if focused:
            rrect(draw, box, r, outline=colour("cb_glow_cyan"), width=ring)
    elif focused:
        rrect(draw, box, r, fill=colour("cb_selected_fill" if activated else "cb_panel"),
              outline=colour("cb_focus_ring"), width=ring)
    elif kind == "ghost":
        rrect(draw, box, r, outline=colour("cb_ghost_edge"), width=dp(1))
    elif activated:
        rrect(draw, box, r, fill=colour("cb_selected_fill"),
              outline=colour("cb_selected_edge"), width=dp(1))
    else:
        rrect(draw, box, r, fill=colour("cb_card_surface"),
              outline=colour("cb_divider"), width=dp(1))
    ink = colour(PILL_INK[kind])
    left = x0 + dp(DIMENS["cb_pill_padding"] if pad_start is None else pad_start)
    cy = (y0 + y1) // 2
    if icon:
        s = dp(vector_dp(icon[0]))
        vector(img, icon[0], [left, cy - s // 2, left + s, cy - s // 2 + s], ink)
        left += s + dp(icon[1])
    right = x1 - dp(DIMENS["cb_pill_padding"])
    draw_text(draw, ((left + right) / 2, cy), label, _pill_font(kind), ink, anchor="mm")


def chip_width(draw, label: str) -> int:
    return (text_width(draw, label, ofont(DIMENS["cb_text_button"], "medium"))
            + 2 * dp(DIMENS["cb_space_md"]))


def chip(img: Image.Image, x: int, y: int, label: str, active: bool = False,
         focused: bool = False) -> int:
    """item_chip on bg_chip_toggle: the chosen filter is the CTA gradient,
    every other chip the card colour with a hairline, focus the 3dp ring.
    Returns its width."""
    draw = ImageDraw.Draw(img, "RGBA")
    w, h = chip_width(draw, label), dp(DIMENS["cb_target_min"])
    box = [x, y, x + w, y + h]
    r, ring = h // 2, dp(DIMENS["cb_focus_ring"])
    if active:
        grad = masked(cta_gradient((w, h)), r)
        img.paste(grad, (x, y), grad)
        if focused:
            rrect(draw, box, r, outline=colour("cb_glow_cyan"), width=ring)
    elif focused:
        rrect(draw, box, r, fill=colour("cb_panel"), outline=colour("cb_signal_cyan"),
              width=ring)
    else:
        rrect(draw, box, r, fill=colour("cb_card"), outline=colour("cb_hairline"),
              width=dp(1))
    draw_text(draw, (x + w / 2, y + h / 2), label, ofont(DIMENS["cb_text_button"], "medium"),
              colour("cb_cta_ink" if active else "cb_ink"), anchor="mm")
    return w


def tag(img: Image.Image, right: int, cy: int, label: str, pad_x: float = 10,
        pad_y: float = 3) -> int:
    """A bg_chip status label (the auditor's action, a launcher's apply
    mode): cyan semibold on a cyan-tinted pill, right-aligned at `right`.
    It only labels, so it has no focus state. Returns its width."""
    draw = ImageDraw.Draw(img, "RGBA")
    f = ofont(DIMENS["cb_text_kicker"], "semibold")
    w = text_width(draw, label, f) + 2 * dp(pad_x)
    h = line_h(f) + 2 * dp(pad_y)
    box = [right - w, cy - h // 2, right, cy - h // 2 + h]
    rrect(draw, box, h // 2, fill=colour("cb_selected_fill"),
          outline=colour("cb_selected_edge"), width=dp(1))
    draw_text(draw, ((box[0] + box[2]) / 2, cy), label, f, colour("cb_signal_cyan"),
              anchor="mm")
    return w


def row(img: Image.Image, box, focused: bool = False) -> None:
    """bg_row: the surface with a hairline at rest; focused, the panel inside
    the 3dp ring. It draws to its bounds (no inset, unlike bg_card)."""
    draw = ImageDraw.Draw(img, "RGBA")
    r = dp(DIMENS["cb_radius_card"])
    if focused:
        rrect(draw, box, r, fill=colour("cb_panel"), outline=colour("cb_focus_ring"),
              width=dp(DIMENS["cb_focus_ring"]))
    else:
        rrect(draw, box, r, fill=colour("cb_card_surface"), outline=colour("cb_hairline"),
              width=dp(1))


def section(img: Image.Image, box) -> None:
    """bg_section: the surface inside a divider-coloured edge."""
    rrect(ImageDraw.Draw(img, "RGBA"), box, dp(DIMENS["cb_radius_section"]),
          fill=colour("cb_card_surface"), outline=colour("cb_divider"), width=dp(1))


def rail_body(img: Image.Image, box, focused: bool = False, activated: bool = False,
              choice: bool = False) -> None:
    """bg_rail_item (choice: bg_rail_choice). Nothing at rest; the ring under
    focus; the current screen a panel body, a chosen series cyan on a tint."""
    draw = ImageDraw.Draw(img, "RGBA")
    r, ring = dp(DIMENS["cb_radius_rail_item"]), dp(DIMENS["cb_focus_ring"])
    if focused:
        fill = colour("cb_selected_fill" if choice and activated else "cb_panel")
        rrect(draw, box, r, fill=fill, outline=colour("cb_focus_ring"), width=ring)
    elif activated and choice:
        rrect(draw, box, r, fill=colour("cb_selected_fill"),
              outline=colour("cb_selected_edge"), width=dp(1))
    elif activated:
        rrect(draw, box, r, fill=colour("cb_panel"))


def draw_switch(draw, sx: int, sy: int, on: bool) -> None:
    """ic_switch / sw_track + sw_thumb: a 40x22dp track, cyan with a dark
    knob at the end when on, the card-focus grey with a slate knob at the
    start when off."""
    track = [sx, sy, sx + dp(40), sy + dp(22)]
    if on:
        rrect(draw, track, dp(11), fill=colour("cb_signal_cyan"))
    else:
        rrect(draw, track, dp(11), fill=colour("cb_card_focus"), outline=colour("cb_divider"),
              width=dp(1))
    knob = sx + (dp(21) if on else dp(3))
    draw.ellipse([knob, sy + dp(3), knob + dp(16), sy + dp(19)],
                 fill=colour("cb_cta_ink" if on else "cb_slate"))
    RECORD.append(f"W|{(sx, sy)}|{on}")


def count_badge(img: Image.Image, right: int, cy: int, label: str) -> int:
    """missing_entry_count on bg_count_badge: 24dp tall, at least 32dp wide,
    a cyan mono count. Right-aligned at `right`; returns its width."""
    draw = ImageDraw.Draw(img, "RGBA")
    f = font("mono", DIMENS["cb_text_data"], bold=True)
    w = max(dp(32), text_width(draw, label, f) + 2 * dp(DIMENS["cb_space_xs"]))
    h = dp(24)
    box = [right - w, cy - h // 2, right, cy - h // 2 + h]
    rrect(draw, box, h // 2, fill=colour("cb_selected_fill"),
          outline=colour("cb_selected_edge"), width=dp(1))
    draw_text(draw, ((box[0] + box[2]) / 2, cy), label, f, colour("cb_signal_cyan"),
              anchor="mm")
    return w


BACK_ICON = ("ic_chevron_start", 6)


def header(img: Image.Image, kick: str, title: str, x0: int | None = None,
           x1: int | None = None, top: int | None = None,
           back: str | None = None) -> int:
    """The 2.6.0 header on every screen: the eyebrow over the title, and a
    compact ghost Back pill with its chevron at the right. Returns the y
    under it."""
    draw = ImageDraw.Draw(img, "RGBA")
    x0 = dp(DIMENS["cb_gutter_side"]) if x0 is None else x0
    x1 = W - dp(DIMENS["cb_gutter_side"]) if x1 is None else x1
    y = dp(24) if top is None else top
    label = back or STRINGS["action_back"]
    bw = pill_width(draw, label, "ghost", icon=BACK_ICON, pad_start=12)
    bh = dp(DIMENS["cb_pill_compact"])
    pill(img, [x1 - bw, y, x1, y + bh], label, "ghost", icon=BACK_ICON, pad_start=12)
    title_top = eyebrow(draw, x0, y, kick) + dp(4)
    f = ofont(DIMENS["cb_text_stage_title"], "semibold")
    draw_text(draw, (x0, title_top),
              fit(draw, title, f, x1 - bw - dp(DIMENS["cb_space_md"]) - x0), f,
              colour("cb_ink"))
    return max(title_top + line_h(f), y + bh)


def search_pill(img: Image.Image, box, hint: str) -> None:
    """bg_search at rest: a pill with the magnifier (slate) and the hint
    MainActivity sets (search_hint_fmt)."""
    draw = ImageDraw.Draw(img, "RGBA")
    x0, y0, x1, y1 = box
    rrect(draw, box, (y1 - y0) // 2, fill=colour("cb_card_surface"),
          outline=colour("cb_divider"), width=dp(1))
    s = dp(vector_dp("ic_search"))
    left = x0 + dp(DIMENS["cb_pill_padding"])
    cy = (y0 + y1) // 2
    vector(img, "ic_search", [left, cy - s // 2, left + s, cy - s // 2 + s], colour("cb_slate"))
    draw_text(draw, (left + s + dp(DIMENS["cb_space_sm"]), cy), hint,
              ofont(DIMENS["cb_text_label"]), colour("cb_slate"), anchor="lm")


def rail_and_stage(img: Image.Image) -> tuple[int, int, int, int]:
    """The full-height void rail and the hairline beside it. Returns (rail
    content left, rail content right, stage left, stage right)."""
    draw = ImageDraw.Draw(img, "RGBA")
    rw = dp(DIMENS["cb_nav_rail_width"])
    draw.rectangle([0, 0, rw - 1, H - 1], fill=colour("cb_void"))
    draw.rectangle([rw, 0, rw + dp(1) - 1, H - 1], fill=colour("cb_divider"))
    return (dp(DIMENS["cb_rail_pad_start"]), rw - dp(DIMENS["cb_rail_pad_end"]),
            rw + dp(1) + dp(DIMENS["cb_stage_start"]), W - dp(DIMENS["cb_stage_end"]))


def clip(img: Image.Image, box, paint) -> None:
    """Draws paint(scratch) on a copy of the frame and keeps only `box`: a
    scrolled list or grid cut at its viewport's edges, the way the
    ScrollView or RecyclerView cuts it. A copy rather than a transparent
    layer, so translucent fills blend over the real background."""
    scratch = img.copy()
    paint(scratch)
    box = [int(v) for v in box]
    img.paste(scratch.crop(box), (box[0], box[1]))


def screen_box() -> tuple[int, int, int]:
    """(left, right, bottom) of the screens with the house gutters (48dp
    sides, 24dp top, 16dp bottom)."""
    g = dp(DIMENS["cb_gutter_side"])
    return g, W - g, H - dp(16)


def live_badge(img: Image.Image, draw: ImageDraw.ImageDraw, box, inset: int) -> None:
    """The LIVE chip from item_wallpaper.xml over a motion-loop tile: a
    near-opaque night pill with a cyan hairline and the label in signal cyan,
    over the thumb's top-left corner. A label, never a control, so it carries
    no focus state."""
    f = ofont(DIMENS["cb_text_kicker"], "semibold")
    label = STRINGS["wp_live_badge"]
    pad = dp(DIMENS["cb_space_sm"])
    h = line_h(f) + 2 * dp(2)
    w = text_width(draw, label, f) + 2 * pad
    x = box[0] + inset + dp(DIMENS["cb_space_sm"])
    y = box[1] + inset + dp(DIMENS["cb_space_sm"])
    rrect(draw, [x, y, x + w, y + h], dp(DIMENS["cb_radius_field"]),
          fill=colour("cb_night", alpha=0xE6),
          outline=colour("cb_signal_cyan"), width=dp(1))
    draw_text(draw, (x + w / 2, y + h / 2), label, f, colour("cb_signal_cyan"),
              anchor="mm")


# Every raster a frame pastes, recorded so --check can prove whether the inputs
# it rendered from are the inputs that were committed. Byte-compared output is
# only meaningful if both sides started from the same art.
PASTED: set[Path] = set()


def pasted_digest() -> str:
    """sha256 over the sorted (path, sha256) pairs of every raster pasted."""
    h = hashlib.sha256()
    for path in sorted(PASTED):
        rel = path.relative_to(ROOT) if path.is_relative_to(ROOT) else path
        h.update(str(rel).encode())
        h.update(hashlib.sha256(path.read_bytes()).digest() if path.exists() else b"MISSING")
    return h.hexdigest()


def paste_art(img: Image.Image, path: Path, box, radius: int = 0) -> None:
    PASTED.add(path)
    RECORD.append("A|{}|{}|{}".format(
        path.relative_to(ROOT) if path.is_relative_to(ROOT) else path,
        tuple(box), radius))
    art = Image.open(path).convert("RGBA")
    x0, y0, x1, y1 = box
    bw, bh = x1 - x0, y1 - y0
    art.thumbnail((bw, bh), Image.LANCZOS)
    px, py = x0 + (bw - art.size[0]) // 2, y0 + (bh - art.size[1]) // 2
    if radius:
        art = masked(art, radius)
    img.paste(art, (px, py), art)


def caption(img: Image.Image, text: str) -> Image.Image:
    out = Image.new("RGBA", (W, H + CAPTION_H), colour("cb_void"))
    out.paste(img, (0, 0))
    draw = ImageDraw.Draw(out, "RGBA")
    f = font("mono", 10)
    limit = W - dp(DIMENS["cb_gutter_side"])
    while text_width(draw, text, f) > limit:
        text = text[:-2] + "\u2026"
    draw_text(draw, (dp(DIMENS["cb_gutter_side"]) // 2, H + CAPTION_H // 2),
              text, f, colour("cb_slate"), anchor="lm")
    return out


def new_frame() -> Image.Image:
    # RGB, not RGBA: Pillow blends a translucent fill into the image only when
    # an RGBA-mode Draw paints on an RGB image. On an RGBA canvas it writes the
    # colour as given, so the 6% hairlines, 8% dividers and 16% selection
    # tints the drawables use came out at full strength.
    return Image.new("RGB", (W, H), colour("cb_night")[:3])


# ---------------------------------------------------------------------------
# Frames
# ---------------------------------------------------------------------------

def wallpapers_entry_sub() -> str:
    """MainActivity.bindWallpapersSub: the "N new in this release" line until
    the New chip has been seen; the mockup is a first run, so it shows."""
    newest = max((w.get("since", "") for w in WALLPAPERS), key=version_key, default="")
    new = sum(1 for w in WALLPAPERS if w.get("since", "") == newest) if newest else 0
    if new:
        return fmt(STRINGS["wp_entry_sub_new_fmt"], len(WALLPAPERS), new)
    return fmt(STRINGS["wp_entry_sub_fmt"], len(WALLPAPERS))


def category_counts() -> list[tuple[str, str, int]]:
    counts: dict[str, int] = {}
    for cat, app in zip(CATS, APPS):
        if app:
            counts[cat] = counts.get(cat, 0) + 1
    out = [("ALL", STRINGS["chip_all"], ICON_COUNT)]
    if any(BESPOKE):
        out.append(("BESPOKE", STRINGS["chip_brandmarks"],
                    sum(b and a for b, a in zip(BESPOKE, APPS))))
    for key, label in CHIP_ORDER:
        if key in counts:
            out.append((key, label, counts[key]))
    return out


def catalogue_frame(with_update_bar: bool) -> tuple[Image.Image, str]:
    """Home as built since 2.6.0: activity_main.xml's rail and stage at
    960x540dp with the real strings, dimens, colours, icons and bundled art.
    The rail holds the wordmark, Apply and its launchers, the three rows and
    the foot rows; the stage holds the update bar when there is one, the
    search pill, the category chips and the grid."""
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    rx0, rx1, sx0, sx1 = rail_and_stage(img)
    top = dp(DIMENS["cb_stage_top"])
    bottom = H - dp(DIMENS["cb_stage_bottom"])
    # A rail row's paddingStart: the wordmark, count, launcher line and
    # headings carry the same 12dp, so every rail label starts on one line.
    inner = dp(12)

    # ---- RAIL -------------------------------------------------------------
    y = eyebrow(draw, rx0 + inner, top, STRINGS["kicker"], DIMENS["cb_text_rail_brand"])
    if not with_update_bar:
        # MainActivity.showUpdateAvailable hides the count, the launcher line
        # and ALSO APPLIES TO while the bar is up: the bar states the new
        # release's count, and Later gives them back.
        # autoSize, 13sp down to the 12sp floor in 1sp steps, as the layout
        # shrinks it: DejaVu's mono is wider than the device's.
        count = fmt(STRINGS["pack_stats_fmt"], ICON_COUNT, COMPONENT_COUNT)
        size = DIMENS["cb_text_count"]
        while (size > DIMENS["cb_text_fit_min"]
               and text_width(draw, count, font("mono", size)) > rx1 - rx0 - inner):
            size -= 1
        f_count = font("mono", size)
        y += dp(6)
        draw_text(draw, (rx0 + inner, y), fit(draw, count, f_count, rx1 - rx0 - inner),
                  f_count, colour("cb_slate"))
        y += line_h(f_count)
    y += dp(DIMENS["cb_space_md"])
    # Home opens on Apply (FocusChainTest holds it); with the bar up the
    # cursor is on Download instead.
    pill(img, [rx0, y, rx1, y + dp(DIMENS["cb_pill"])], STRINGS["cta_apply"], "cta",
         focused=not with_update_bar)
    y += dp(DIMENS["cb_pill"])
    if not with_update_bar:
        f_lede = ofont(DIMENS["cb_text_data"])
        y += dp(DIMENS["cb_space_xs"])
        for line in wrap(draw, " - ".join(EXAMPLE_LAUNCHERS), f_lede,
                         rx1 - rx0 - inner - dp(4)):
            draw_text(draw, (rx0 + inner, y), line, f_lede, colour("cb_slate"))
            y += line_h(f_lede) + dp(3)
        y -= dp(3)
        if EXAMPLE_LAUNCHERS[1:]:
            # The ALSO APPLIES TO band: GONE with one launcher.
            y += dp(DIMENS["cb_space_md"])
            y = menu_heading(draw, rx0 + inner, y, STRINGS["cta_also_applies"])
            y += dp(DIMENS["cb_space_xs"])
            cx = rx0
            for name in EXAMPLE_LAUNCHERS[1:]:
                if cx + chip_width(draw, name) > rx1:
                    break
                cx += chip(img, cx, y, name) + dp(DIMENS["cb_space_sm"])
            y += dp(DIMENS["cb_target_min"])
    y += dp(DIMENS["cb_space_md"])

    # The three rows with a state line, in activity_main.xml's order. Art
    # style shows the shipped default, Banners (switch on); Missing icons an
    # example count (a real scan counts this TV's apps).
    f_title = ofont(DIMENS["cb_text_label"], "medium")
    f_sub = ofont(DIMENS["cb_text_kicker"])
    text_h = line_h(f_title) + dp(2) + line_h(f_sub)
    tall = max(dp(DIMENS["cb_rail_item_tall"]), dp(16) + text_h)
    icon = dp(DIMENS["cb_rail_icon"])
    for drawable, title, sub, trailing in (
        ("ic_nav_style", STRINGS["art_style_label"], STRINGS["art_style_banners"], "switch"),
        ("ic_nav_missing", STRINGS["missing_entry"], STRINGS["missing_entry_sub"],
         str(len(AUDIT_EXAMPLES))),
        ("ic_nav_wallpapers", STRINGS["wp_entry"], wallpapers_entry_sub(), None),
    ):
        y += dp(2)
        rail_body(img, [rx0, y, rx1, y + tall])
        cy = y + tall // 2
        ix = rx0 + inner
        vector(img, drawable, [ix, cy - icon // 2, ix + icon, cy - icon // 2 + icon],
               colour("cb_slate"))
        tx = ix + icon + dp(12)
        end = rx1 - dp(8)
        if trailing == "switch":
            draw_switch(draw, end - dp(40), cy - dp(11), True)
            end -= dp(40) + dp(DIMENS["cb_space_xs"])
        elif trailing:
            end -= count_badge(img, end, cy, trailing) + dp(DIMENS["cb_space_xs"])
        ty = cy - text_h // 2
        draw_text(draw, (tx, ty), fit(draw, title, f_title, end - tx), f_title,
                  colour("cb_ink"))
        draw_text(draw, (tx, ty + line_h(f_title) + dp(2)), fit(draw, sub, f_sub, end - tx),
                  f_sub, colour("cb_slate"))
        y += tall

    # Support, Settings and About at the rail's foot (the GitHub build; the
    # Play build has no Support row). A spacer takes what is left above them.
    foot = [("ic_nav_support", "support_label"), ("ic_nav_settings", "settings_label"),
            ("ic_nav_about", "about_label")]
    step = dp(2) + dp(DIMENS["cb_rail_item"])
    fy = max(y + dp(DIMENS["cb_space_md"]), bottom - len(foot) * step)
    f_foot = ofont(DIMENS["cb_text_label"], "medium")
    for drawable, key in foot:
        fy += dp(2)
        rail_body(img, [rx0, fy, rx1, fy + dp(DIMENS["cb_rail_item"])])
        cy = fy + dp(DIMENS["cb_rail_item"]) // 2
        s = dp(vector_dp(drawable))
        vector(img, drawable, [rx0 + inner, cy - s // 2, rx0 + inner + s, cy - s // 2 + s],
               colour("cb_slate"))
        draw_text(draw, (rx0 + inner + s + dp(12), cy), STRINGS[key], f_foot,
                  colour("cb_ink"), anchor="lm")
        fy += dp(DIMENS["cb_rail_item"])

    # ---- STAGE ------------------------------------------------------------
    sy = top
    if with_update_bar:
        f_label = ofont(DIMENS["cb_text_label"], "semibold")
        f_line = ofont(DIMENS["cb_text_data"])
        pad_v = dp(DIMENS["cb_space_xs"])
        text_h = line_h(f_label) + dp(2) + line_h(f_line)
        bar_h = max(dp(DIMENS["cb_pill_compact"]), text_h) + 2 * pad_v
        section(img, [sx0, sy, sx1, sy + bar_h])
        bh = dp(DIMENS["cb_pill_compact"])
        by = sy + (bar_h - bh) // 2
        later = STRINGS["update_later"]
        download = fmt(STRINGS["update_download"], VERSION["versionName"])
        later_w = pill_width(draw, later, "ghost")
        dl_w = pill_width(draw, download, "cta")
        lx = sx1 - dp(DIMENS["cb_space_xs"]) - later_w
        dx = lx - dp(DIMENS["cb_space_xs"]) - dl_w
        pill(img, [dx, by, dx + dl_w, by + bh], download, "cta", focused=True)
        pill(img, [lx, by, lx + later_w, by + bh], later, "ghost")
        tx = sx0 + dp(DIMENS["cb_space_md"])
        limit = dx - dp(DIMENS["cb_space_md"]) - tx
        ty = sy + (bar_h - text_h) // 2
        draw_text(draw, (tx, ty),
                  fit(draw, fmt(STRINGS["update_available_fmt"], VERSION["versionName"],
                                VERSION["iconCount"]), f_label, limit),
                  f_label, colour("cb_ink"))
        # The first release highlight, one line, ellipsised.
        draw_text(draw, (tx, ty + line_h(f_label) + dp(2)),
                  fit(draw, "•  " + VERSION["highlights"][0], f_line, limit),
                  f_line, colour("cb_ink"))
        sy += bar_h + dp(DIMENS["cb_space_sm"])

    search_h = dp(DIMENS["cb_target_min"])
    search_pill(img, [sx0, sy, sx1, sy + search_h], fmt(STRINGS["search_hint_fmt"], ICON_COUNT))
    sy += search_h + dp(DIMENS["cb_space_sm"])

    # The category chips run off the stage's edge and are cut there, as the
    # RecyclerView cuts them.
    def chips(canvas):
        x = sx0
        for index, (_key, label, count) in enumerate(category_counts()):
            if x > sx1:
                break
            x += chip(canvas, x, sy, fmt(STRINGS["chip_count_fmt"], label, count),
                      active=index == 0) + dp(DIMENS["cb_space_sm"])
    clip(img, [sx0 - dp(4), sy - dp(4), sx1, sy + dp(DIMENS["cb_target_min"]) + dp(4)], chips)
    sy += dp(DIMENS["cb_target_min"]) + dp(DIMENS["cb_space_sm"])

    # The grid: columns from the stage width over the tile pitch, exactly as
    # MainActivity.spanForScreen derives them; rows run past the grid's foot
    # and are cut there.
    pane_w = sx1 - sx0
    pitch = max(dp(DIMENS["cb_tile_icon"]) + 2 * dp(DIMENS["cb_card_padding"]),
                dp(DIMENS["cb_tile_min_width"]))
    spans = max(2, pane_w // pitch)
    tile_w = pane_w // spans
    tile_h = dp(DIMENS["cb_tile_icon"]) + 2 * dp(DIMENS["cb_card_padding"])
    pad = dp(DIMENS["cb_card_padding"])
    art_h = dp(DIMENS["cb_tile_icon"])
    dot = dp(DIMENS["cb_brandmark_dot"])
    grid_top = sy

    def grid(canvas):
        d = ImageDraw.Draw(canvas, "RGBA")
        gy, index = grid_top, 0
        while gy < bottom and index < len(PACK):
            for col in range(spans):
                if index >= len(PACK):
                    break
                box = [sx0 + col * tile_w, gy, sx0 + (col + 1) * tile_w, gy + tile_h]
                card(canvas, box)
                # The catalogue opens on the shipped art style, Banners: the
                # ImageView is the tile's width by cb_tile_icon, fitCenter.
                paste_art(canvas, art_path(ICONS_DIR, f"{PACK[index]}_banner", ALIASES),
                          [box[0] + pad, box[1] + pad, box[2] - pad, box[1] + pad + art_h])
                if BESPOKE[index]:
                    # icon_brandmark: the dot in the artwork's top-end corner.
                    d.ellipse([box[2] - pad - dot, box[1] + pad, box[2] - pad,
                               box[1] + pad + dot], fill=colour("cb_ink"))
                index += 1
            gy += tile_h
    clip(img, [sx0, grid_top, sx1, bottom], grid)

    # The caption band holds ~150 characters before it ellipsizes, and the
    # example clause at the end is the one that must survive.
    if with_update_bar:
        note = ("MOCKUP - Home, update bar up: activity_main.xml + version.json + "
                f"appfilter ({COMPONENT_COUNT} components); example: missing count, focus")
    else:
        note = ("MOCKUP - Home from activity_main.xml + strings/dimens/colors + "
                f"appfilter ({COMPONENT_COUNT} components); example: launcher, missing "
                "count, focus")
    return img, note


def version_key(v: str) -> tuple:
    return tuple(int(p) if p.isdigit() else 0 for p in v.split("."))


def wallpapers_frame() -> tuple[Image.Image, str]:
    """The wallpaper browser as built since 2.6.0: the series as rail rows
    under SERIES with Export at the rail's foot, and the stage's header over
    the named-thumb grid. The motion loops ride the same grid, badged."""
    walls = WALLPAPERS + LIVE_LOOPS
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    rx0, rx1, sx0, sx1 = rail_and_stage(img)
    top = dp(DIMENS["cb_stage_top"])
    bottom = H - dp(DIMENS["cb_stage_bottom"])
    inner = dp(12)

    # ---- RAIL: SERIES, the series, then the export foot.
    # WallpapersActivity: All, New (N), Live, then the still series in
    # catalogue order - the loops' own key is not a series.
    series: list[str] = []
    for wall in walls:
        if not wall.get("live") and wall["series"] not in series:
            series.append(wall["series"])
    newest = max((w.get("since", "") for w in walls), key=version_key, default="")
    new_count = sum(1 for w in walls if w.get("since", "") == newest) if newest else 0
    labels = [STRINGS["chip_all"]] + (
        [fmt(STRINGS["chip_new_fmt"], new_count)] if new_count else []) + (
        [STRINGS["chip_live"]] if any(w.get("live") for w in walls) else []) + [
        " ".join(part.capitalize()
                 for part in s.removeprefix("series-").split("-", 1)[1].split("-"))
        for s in series
    ]
    y = menu_heading(draw, rx0 + inner, top, STRINGS["wp_series_heading"])
    list_top = y + dp(DIMENS["cb_space_xs"])

    # The foot is laid out from the bottom: the Export pill, then its note.
    f_note = ofont(DIMENS["cb_text_kicker"])
    note_lines = wrap(draw, STRINGS["wp_export_sub"], f_note, rx1 - rx0 - dp(8))
    note_h = len(note_lines) * (line_h(f_note) + dp(3))
    pill_h = dp(DIMENS["cb_pill"])
    ny = bottom - note_h
    py = ny - dp(DIMENS["cb_space_xs"]) - pill_h
    pill(img, [rx0, py, rx1, py + pill_h], STRINGS["wp_export_rail"], "ghost",
         icon=("ic_export", 10))
    for i, line in enumerate(note_lines):
        draw_text(draw, (rx0 + dp(4), ny + i * (line_h(f_note) + dp(3))), line, f_note,
                  colour("cb_slate"))

    # The list takes the height between the heading and the pill, and scrolls:
    # rows past its foot are cut there. Wallpapers opens on the series, on All.
    list_bottom = py - dp(DIMENS["cb_space_md"])
    f_row = ofont(DIMENS["cb_text_label"], "medium")

    def rows(canvas):
        ry = list_top + dp(4)
        for index, label in enumerate(labels):
            ry += dp(2)
            box = [rx0, ry, rx1, ry + dp(DIMENS["cb_rail_item"])]
            rail_body(canvas, box, focused=index == 0, activated=index == 0, choice=True)
            draw_text(ImageDraw.Draw(canvas, "RGBA"),
                      (rx0 + inner, ry + dp(DIMENS["cb_rail_item"]) // 2), label, f_row,
                      colour("cb_signal_cyan" if index == 0 else "cb_ink"), anchor="lm")
            ry += dp(DIMENS["cb_rail_item"])
            if ry > list_bottom:
                break
    clip(img, [rx0 - dp(6), list_top, rx1 + dp(6), list_bottom], rows)

    # ---- STAGE: the header, then the grid.
    sy = header(img, STRINGS["wp_kicker"], STRINGS["wp_entry"], x0=sx0, x1=sx1, top=top,
                back=STRINGS["wp_back"])
    # wp_count sits under the title, in the header's left column.
    f_count = font("mono", DIMENS["cb_text_kicker"])
    title_bottom = (top + line_h(ofont(DIMENS["cb_text_kicker"], "semibold")) + dp(4)
                    + line_h(ofont(DIMENS["cb_text_stage_title"], "semibold")))
    draw_text(draw, (sx0, title_bottom + dp(2)), fmt(STRINGS["wp_sub_fmt"], len(walls)),
              f_count, colour("cb_slate"))
    grid_top = max(sy, title_bottom + dp(2) + line_h(f_count)) + dp(DIMENS["cb_space_md"])

    # Columns as WallpapersActivity.spanForScreen derives them: the stage over
    # the thumb, its padding, the gap and the title line.
    pane_w = sx1 - sx0
    thumb = dp(DIMENS["cb_wp_thumb"])
    pad = dp(DIMENS["cb_card_padding"])
    pitch = (thumb + 2 * pad + dp(DIMENS["cb_space_sm"])
             + dp(DIMENS["cb_text_label"] * 1.2))
    spans = max(2, int(pane_w // pitch))
    tile_w = pane_w // spans
    f_tile = ofont(DIMENS["cb_text_label"], "semibold")
    tile_h = pad + thumb + dp(DIMENS["cb_space_sm"]) + line_h(f_tile) + pad

    def grid(canvas):
        d = ImageDraw.Draw(canvas, "RGBA")
        gy, index = grid_top, 0
        while gy < bottom and index < len(walls):
            for col in range(spans):
                if index >= len(walls):
                    break
                box = [sx0 + col * tile_w, gy, sx0 + (col + 1) * tile_w, gy + tile_h]
                card(canvas, box)
                wall = walls[index]
                stem = wall["url"].split("/")[-1].rsplit(".", 1)[0] + ".jpg"
                # Motion loops keep their still frame with the motion art; the
                # stills' thumbs are bundled under assets/.
                thumb_path = (LIVE_THUMBS if wall.get("live") else THUMBS_DIR) / stem
                paste_art(canvas, thumb_path,
                          [box[0] + pad, box[1] + pad, box[2] - pad, box[1] + pad + thumb],
                          radius=dp(6))
                if wall.get("live"):
                    live_badge(canvas, d, box, 0)
                title = wall["name"].split("· ", 1)[-1]
                draw_text(d, ((box[0] + box[2]) / 2,
                              box[1] + pad + thumb + dp(DIMENS["cb_space_sm"])),
                          fit(d, title, f_tile, box[2] - box[0] - 2 * pad), f_tile,
                          colour("cb_ink"), anchor="ma")
                index += 1
            gy += tile_h
    clip(img, [sx0, grid_top, sx1, bottom], grid)

    # The caption band ellipsizes rather than overflow the panel, so this line
    # has to stay short enough to survive whole: the LIVE badge and the counts
    # are the honesty clauses, and a "MOCKUP - ..." that ends in "..." names
    # no fiction at all. Counts are interpolated, never typed.
    note = ("MOCKUP - wallpapers: activity_wallpapers.xml + strings.xml + "
            f"{len(WALLPAPERS)} stills + {len(LIVE_LOOPS)} live loops "
            "(LiveLoop.kt, CoreViewsLoop.kt); example: focus")
    return img, note


def settings_order() -> list:
    # One source of truth for the rows, shared by both settings frames: the
    # existing frame scrolls to LAUNCHER, settings_display_frame scrolls to
    # DISPLAY, and a hand-copied row list would drift between the two exactly
    # the way production copy would. Order and grouping mirror
    # activity_settings.xml — if a row lands in the layout and not here the
    # frames stop being examples of the build.
    return [
        ("group", "settings_group_updates"),
        ("switch", "settings_updates_title", "settings_updates_sub", True),
        ("group", "settings_group_display"),
        ("switch", "settings_motion_title", "settings_motion_sub", False),
        ("switch", "settings_amoled_title", "settings_amoled_sub", False),
        ("switch", "settings_banner_title", "settings_banner_sub", True),
        ("group", "settings_group_storage"),
        ("row", "settings_cache_title", "settings_cache_sub", "settings_cache_clear"),
        ("group", "settings_group_launcher"),
        ("row", "settings_refresh_title", "settings_refresh_sub", None),
        ("row", "settings_appinfo_title", "settings_appinfo_sub", None),
        ("group", "settings_group_suite"),
        ("row", "settings_suite_title", "settings_suite_sub", None),
        ("row", "settings_whatsnew_title", "settings_whatsnew_sub", None),
        ("group", "settings_group_help"),
        ("row", "settings_audit_title", "settings_audit_sub", None),
        ("row", "settings_faq_title", "settings_faq_sub", None),
        ("row", "settings_about_title", "settings_about_sub", None),
        ("row", "settings_support_title", "settings_support_sub", None),
    ]


def paint_settings_viewport(img, order, focused_row):
    """One viewportful of the settings list, already scrolled: rows above the
    scroll point were popped by the caller, the first visible entry leads,
    and rows past the ScrollView's foot are cut there. The header above and
    the footer under the list stay put, as activity_settings.xml pins them."""
    draw = ImageDraw.Draw(img, "RGBA")
    g, right, bottom = screen_box()
    top = header(img, STRINGS["settings_kicker"], STRINGS["settings_title"])
    f_title = ofont(DIMENS["cb_text_body"], "semibold")
    f_sub = ofont(DIMENS["cb_text_data"])
    f_foot = ofont(DIMENS["cb_text_data"])
    foot_y = bottom - line_h(f_foot)
    draw_text(draw, (g, foot_y), STRINGS["settings_footer"], f_foot, colour("cb_slate"))
    view_bottom = foot_y - dp(DIMENS["cb_space_xs"])
    pad = dp(DIMENS["cb_card_padding"])
    text_w = right - g - 2 * pad - dp(160)

    def rows(canvas):
        d = ImageDraw.Draw(canvas, "RGBA")
        y = top
        for entry in order:
            if y > view_bottom:
                break
            if entry[0] == "group":
                y = menu_heading(d, g, y + dp(DIMENS["cb_space_md"]), STRINGS[entry[1]])
                continue
            sub_lines = wrap(d, STRINGS[entry[2]], f_sub, text_w)
            text_h = line_h(f_title) + dp(4) + len(sub_lines) * line_h(f_sub)
            y += dp(6)
            row_h = 2 * pad + max(text_h, dp(DIMENS["cb_pill"]) if entry[0] == "row" and entry[3] else 0)
            row(canvas, [g, y, right, y + row_h], focused=entry[1] == focused_row)
            ty = y + (row_h - text_h) // 2
            draw_text(d, (g + pad, ty), STRINGS[entry[1]], f_title, colour("cb_ink"))
            for i, line in enumerate(sub_lines):
                draw_text(d, (g + pad, ty + line_h(f_title) + dp(4) + i * line_h(f_sub)),
                          line, f_sub, colour("cb_slate"))
            cy = y + row_h // 2
            if entry[0] == "switch":
                draw_switch(d, right - pad - dp(40), cy - dp(11), entry[3])
            elif entry[3]:
                label = STRINGS[entry[3]]
                bw = pill_width(d, label, "ghost")
                bh = dp(DIMENS["cb_pill"])
                pill(canvas, [right - pad - bw, cy - bh // 2, right - pad, cy - bh // 2 + bh],
                     label, "ghost")
            y += row_h
    clip(img, [g - dp(4), top, right + dp(4), view_bottom], rows)


def settings_frame() -> tuple[Image.Image, str]:
    img = new_frame()
    order = settings_order()
    # One viewport of a scrolling list, scrolled to the LAUNCHER group: the row
    # this frame exists to show is the refresh row and its focus ring.
    while order and order[0][1] != "settings_group_launcher":
        order.pop(0)
    paint_settings_viewport(img, order, "settings_refresh_title")
    note = ("MOCKUP - settings frame from activity_settings.xml + strings.xml, scrolled "
            "to LAUNCHER; example: focus on the refresh row, switch positions")
    return img, note


def settings_display_frame() -> tuple[Image.Image, str]:
    img = new_frame()
    order = settings_order()
    # The same list parked at the DISPLAY group: this frame exists since the
    # banner-previews switch landed (1.9.2), because a new toggle nobody can
    # see is not a toggle. Focus ring on the row, in its shipped state: on,
    # banners (the default again since 1.9.5).
    while order and order[0][1] != "settings_group_display":
        order.pop(0)
    paint_settings_viewport(img, order, "settings_banner_title")
    note = ("MOCKUP - settings frame scrolled to DISPLAY; example: focus on the "
            "art style switch (on - banners, the 1.9.5 default)")
    return img, note


def faq_frame() -> tuple[Image.Image, str]:
    img = new_frame()
    g, right, bottom = screen_box()
    top = header(img, STRINGS["faq_kicker"], STRINGS["faq_title"])
    f_title = ofont(DIMENS["cb_text_body"], "semibold")
    f_body = ofont(DIMENS["cb_text_data"])
    pad = dp(DIMENS["cb_card_padding"])

    def rows(canvas):
        d = ImageDraw.Draw(canvas, "RGBA")
        y = top + dp(DIMENS["cb_space_md"])
        for key in ("cache", "override", "monet", "projectivy", "unmapped"):
            lines = wrap(d, STRINGS[f"faq_{key}_body"], f_body, right - g - 2 * pad)
            row_h = 2 * pad + line_h(f_title) + dp(6) + len(lines) * (line_h(f_body) + dp(3))
            y += dp(6)
            row(canvas, [g, y, right, y + row_h])
            draw_text(d, (g + pad, y + pad), STRINGS[f"faq_{key}_title"], f_title,
                      colour("cb_ink"))
            for i, line in enumerate(lines):
                draw_text(d, (g + pad, y + pad + line_h(f_title) + dp(6)
                              + i * (line_h(f_body) + dp(3))), line, f_body, colour("cb_slate"))
            y += row_h
    clip(img, [g - dp(4), top, right + dp(4), bottom], rows)
    note = ("MOCKUP - FAQ frame from activity_faq.xml + strings.xml; bodies are the "
            "shipped strings, which paraphrase the repo docs")
    return img, note


# Example rows for the auditor frames and the home screen's Missing icons
# count: (label, component, kind, tv app, preinstalled, what the pack maps
# instead). A real scan lists this TV's apps.
AUDIT_EXAMPLES = [
    ("Speedtest", "com.rma.speedtesttv/com.rma.speedtesttv.ui.SplashActivity",
     "not_applying", True, False, ".MainActivity"),
    ("TiviMate IPTV Player", "ar.tvplayer/ar.tvplayer.tv.ui.MainActivity",
     "no_icon", True, False, None),
    ("XCIPTV Player", "com.otg.xciptv/com.otg.xciptv.activity.SplashActivity",
     "no_icon", True, False, None),
    ("File Manager", "com.sdmc.filemanager/com.sdmc.filemanager.MainActivity",
     "no_icon", False, True, None),
]


def app_icon_placeholder(img: Image.Image, box, label: str) -> None:
    """Where the row shows the app's own launcher icon (PackageManager's, so
    not reproducible here): a neutral tile with the label's initial."""
    draw = ImageDraw.Draw(img, "RGBA")
    rrect(draw, box, dp(8), fill=(0x22, 0x2C, 0x3A))
    draw_text(draw, ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), label[:1],
              ofont(DIMENS["cb_text_body"], "semibold"), colour("cb_slate"), anchor="mm")


def audit_row(img: Image.Image, y: int, example, focused: bool) -> int:
    """item_audit: the app's icon, its name and tags, the component, and
    what the pack maps instead when the pack's icon is not applying; the
    action as a bg_chip label at the end."""
    label, component, kind, tv, system, mapped_as = example
    draw = ImageDraw.Draw(img, "RGBA")
    g, right, _ = screen_box()
    pad = dp(DIMENS["cb_card_padding"])
    f_label = ofont(DIMENS["cb_text_body"], "semibold")
    f_tag = font("mono", DIMENS["cb_text_kicker"])
    f_pkg = font("mono", DIMENS["cb_text_data"])
    lines = 2 if kind == "not_applying" else 1
    text_h = line_h(f_label) + lines * (dp(2) + line_h(f_pkg))
    row_h = dp(10) * 2 + max(dp(40), text_h)
    row(img, [g, y, right, y + row_h], focused=focused)
    icon = dp(40)
    iy = y + (row_h - icon) // 2
    app_icon_placeholder(img, [g + pad, iy, g + pad + icon, iy + icon], label)
    tx = g + pad + icon + dp(DIMENS["cb_space_sm"])
    ty = y + (row_h - text_h) // 2
    draw_text(draw, (tx, ty), label, f_label, colour("cb_ink"))
    tags = [STRINGS["audit_tag_tv"] if tv else STRINGS["audit_tag_phone"]]
    if system:
        tags.append(STRINGS["audit_tag_system"])
    draw_text(draw, (tx + text_width(draw, label, f_label) + dp(DIMENS["cb_space_sm"]),
                     ty + line_h(f_label) // 2), " · ".join(tags), f_tag,
              colour("cb_slate"), anchor="lm")
    py = ty + line_h(f_label) + dp(2)
    draw_text(draw, (tx, py), component, f_pkg, colour("cb_slate"))
    if kind == "not_applying":
        draw_text(draw, (tx, py + line_h(f_pkg) + dp(2)),
                  fmt(STRINGS["audit_mapped_as_fmt"], mapped_as), f_pkg, colour("cb_warning"))
    action = STRINGS["audit_action_report" if kind == "not_applying" else "audit_action_request"]
    tag(img, right - pad, y + row_h // 2, action, pad_x=10, pad_y=4)
    return row_h


def audit_section(draw, y: int, title: str, sub: str) -> int:
    """item_audit_header: a menu heading over its slate line. Returns its
    height."""
    g, _, _ = screen_box()
    top = y + dp(DIMENS["cb_space_md"])
    hy = menu_heading(draw, g, top, title)
    f = ofont(DIMENS["cb_text_data"])
    draw_text(draw, (g, hy + dp(3)), sub, f, colour("cb_slate"))
    return hy + dp(3) + line_h(f) - y


def audit_launcher_line(img, y: int, draw) -> int:
    """audit_launcher_note: a wide ghost pill, one line, naming the launcher
    this TV is on and the art it applies. The mockup shows the detected-home
    case, Projectivy - the one the launcher-compat frame focuses."""
    g, right, _ = screen_box()
    text = fmt(STRINGS["launcher_compat_here_fmt"], "Projectivy Launcher",
               art_sentence("BANNERS"))
    h = dp(DIMENS["cb_pill"])
    label = fit(draw, text, _pill_font("ghost"), right - g - 2 * dp(DIMENS["cb_pill_padding"]))
    pill(img, [g, y, right, y + h], label, "ghost")
    return y + h


def auditor_frames() -> list[tuple[Image.Image, str]]:
    checked = 41
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    g, right, bottom = screen_box()
    top = header(img, STRINGS["audit_kicker"], STRINGS["audit_title"])
    f_count = ofont(DIMENS["cb_text_data"])
    draw_text(draw, (g, top + dp(DIMENS["cb_space_xs"])), fmt(STRINGS["audit_count_fmt"], checked),
              f_count, colour("cb_slate"))
    line_top = top + dp(DIMENS["cb_space_xs"]) + line_h(f_count) + dp(DIMENS["cb_space_sm"])
    list_top = audit_launcher_line(img, line_top, draw)

    def rows(canvas):
        d = ImageDraw.Draw(canvas, "RGBA")
        y, first = list_top + dp(DIMENS["cb_space_sm"]), True
        for kind, title_key, sub_key in (
            ("not_applying", "audit_section_not_applying_fmt", "audit_section_not_applying_sub"),
            ("no_icon", "audit_section_no_icon_fmt", "audit_section_no_icon_sub"),
        ):
            examples = [e for e in AUDIT_EXAMPLES if e[2] == kind]
            y += audit_section(d, y, fmt(STRINGS[title_key], len(examples)), STRINGS[sub_key])
            for example in examples:
                y += dp(6)
                y += audit_row(canvas, y, example, focused=first)
                first = False
    clip(img, [g - dp(4), list_top, right + dp(4), bottom], rows)
    list_note = ("MOCKUP - auditor: activity_auditor.xml + item_audit*.xml + "
                 "strings.xml; example: rows, count, and tiles for each app's own icon")
    frames = [(img, list_note)]

    # QR panel: the same generated deep link the screen builds, really
    # scannable, beside the request's identity - two columns, as the layout.
    # The device line carries the detected launcher, as AuditorActivity's
    # deviceNote() appends it.
    label, component = AUDIT_EXAMPLES[1][0], AUDIT_EXAMPLES[1][1]
    note_text = (f"Scanned on Example TV, Android 14, "
                 f"pack {VERSION['versionName']}, Projectivy Launcher")
    url = fmt(
        STRINGS["audit_issue_url_fmt"],
        urllib.parse.quote(label), urllib.parse.quote(component),
        urllib.parse.quote(note_text),
    )
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    top = header(img, STRINGS["audit_kicker"], STRINGS["audit_title"])
    size = dp(DIMENS["cb_qr_size"])
    qx = right - size
    # The launcher line stays up in the QR state (only the count goes away),
    # so the panel clears it.
    panel_top = audit_launcher_line(img, top + dp(DIMENS["cb_space_sm"]), draw) + dp(DIMENS["cb_space_md"])
    qy = panel_top + (bottom - panel_top - size) // 2
    qr = qrcode.QRCode(error_correction=ERROR_CORRECT_M, box_size=8, border=4)
    qr.add_data(url)
    qr.make(fit=True)
    qr_img = qr.make_image(fill_color="black", back_color="white").convert("RGB")
    img.paste(qr_img.resize((size, size), Image.NEAREST), (qx, qy))
    col_w = qx - dp(DIMENS["cb_space_lg"]) - g
    ly = eyebrow(draw, g, qy + dp(10), STRINGS["audit_qr_kind_request"]) + dp(DIMENS["cb_space_sm"])
    pad = dp(DIMENS["cb_card_padding"])
    f_name = ofont(DIMENS["cb_text_section"], "semibold")
    f_data = font("mono", DIMENS["cb_text_data"])
    tx = g + pad + dp(64) + dp(DIMENS["cb_space_sm"])
    comp_lines = wrap(draw, component, f_data, g + col_w - pad - tx)[:2]
    text_h = line_h(f_name) + len(comp_lines) * (dp(3) + line_h(f_data))
    card_h = 2 * pad + max(dp(64), text_h)
    row(img, [g, ly, g + col_w, ly + card_h])
    app_icon_placeholder(img, [g + pad, ly + (card_h - dp(64)) // 2, g + pad + dp(64),
                               ly + (card_h - dp(64)) // 2 + dp(64)], label)
    ty = ly + (card_h - text_h) // 2
    draw_text(draw, (tx, ty), label, f_name, colour("cb_ink"))
    for i, line in enumerate(comp_lines):
        draw_text(draw, (tx, ty + line_h(f_name) + dp(3) + i * (dp(3) + line_h(f_data))), line,
                  f_data, colour("cb_slate"))
    ly += card_h + dp(DIMENS["cb_space_md"])
    f_hint = ofont(DIMENS["cb_text_data"])
    for line in wrap(draw, STRINGS["audit_qr_hint"], f_hint, col_w):
        draw_text(draw, (g, ly), line, f_hint, colour("cb_ink"))
        ly += line_h(f_hint) + dp(3)
    ly += dp(DIMENS["cb_space_sm"])
    f_url = font("mono", DIMENS["cb_text_kicker"])
    # One "word" longer than the column: a TextView breaks it at the character.
    url_lines, current = [], ""
    for ch in url:
        if current and text_width(draw, current + ch, f_url) > col_w:
            url_lines.append(current)
            current = ""
        current += ch
    url_lines.append(current)
    shown = url_lines[:2]
    if len(url_lines) > 2:
        shown[-1] = shown[-1][:-1] + "…"
    for i, line in enumerate(shown):
        draw_text(draw, (g, ly + i * dp(16)), line, f_url, colour("cb_slate"))
    qr_note = ("MOCKUP panel with a REAL scannable QR of an EXAMPLE prefilled issue URL "
               "(issue_prefill.xml fmt + URL-encoded args, as AuditorActivity builds it)")
    frames.append((img, qr_note))
    return frames


def inspector_frame() -> tuple[Image.Image, str]:
    index = PACK.index("spotify") if "spotify" in PACK else 0
    drawable = PACK[index]
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    g, right, _ = screen_box()
    top = header(img, STRINGS["inspector_kicker"], NAMES[index])
    y = top + dp(DIMENS["cb_space_md"])
    comps = components_of(drawable)[:4]
    pad = dp(DIMENS["cb_card_padding"])
    art = dp(160)
    row_h = 2 * pad + art
    row(img, [g, y, right, y + row_h])
    paste_art(img, art_path(ICONS_DIR, drawable, ALIASES),
              [g + pad, y + pad, g + pad + art, y + pad + art])
    f_meta = ofont(DIMENS["cb_text_data"])
    f_mono = font("mono", DIMENS["cb_text_data"])
    tx = g + pad + art + dp(DIMENS["cb_space_md"])
    draw_text(draw, (tx, y + pad), fmt(STRINGS["inspector_meta_fmt"], CATS[index], drawable),
              f_meta, colour("cb_slate"))
    cy = y + pad + line_h(f_meta) + dp(DIMENS["cb_space_sm"])
    for i, comp in enumerate(comps):
        draw_text(draw, (tx, cy + i * line_h(f_mono)), comp, f_mono, colour("cb_ink"))
    # The actions sit under the card, right-aligned: Export, then Launch.
    by = y + row_h + dp(DIMENS["cb_space_md"])
    bh = dp(DIMENS["cb_pill"])
    launch_w = pill_width(draw, STRINGS["inspector_launch"], "cta")
    export_w = pill_width(draw, STRINGS["inspector_export"], "ghost")
    pill(img, [right - launch_w, by, right, by + bh], STRINGS["inspector_launch"], "cta")
    ex = right - launch_w - dp(DIMENS["cb_space_sm"]) - export_w
    pill(img, [ex, by, ex + export_w, by + bh], STRINGS["inspector_export"], "ghost")
    note = ("MOCKUP - inspector frame from activity_inspector.xml + strings.xml + "
            "appfilter.xml; mark, category and components are the shipped ones")
    return img, note


def suite_frame() -> tuple[Image.Image, str]:
    img = new_frame()
    g, right, bottom = screen_box()
    top = header(img, STRINGS["suite_kicker"], STRINGS["suite_title"])
    f_name = ofont(DIMENS["cb_text_body"], "semibold")
    f_state = ofont(DIMENS["cb_text_data"])
    f_code = font("mono", DIMENS["cb_text_data"])
    pad = dp(DIMENS["cb_card_padding"])
    states = [False, True, False, True, False, True]  # example install states
    names = SUITE["suite_hub_names"]
    codes = SUITE["suite_hub_codes"]

    def rows(canvas):
        d = ImageDraw.Draw(canvas, "RGBA")
        y = top + dp(DIMENS["cb_space_md"])
        for index, name in enumerate(names):
            row_h = 2 * pad + line_h(f_name) + dp(4) + line_h(f_state) + dp(2) + line_h(f_code)
            y += dp(6)
            row(canvas, [g, y, right, y + row_h], focused=index == len(names) - 1)
            draw_text(d, (g + pad, y + pad), name, f_name, colour("cb_ink"))
            sy = y + pad + line_h(f_name) + dp(4)
            state = (fmt(STRINGS["suite_installed_fmt"], "2.0.0") if states[index % len(states)]
                     else STRINGS["suite_not_installed"])
            draw_text(d, (g + pad, sy), state, f_state, colour("cb_slate"))
            code = codes[index] if index < len(codes) else ""
            draw_text(d, (g + pad, sy + line_h(f_state) + dp(2)),
                      fmt(STRINGS["suite_code_fmt"], code) if code else STRINGS["suite_no_code"],
                      f_code, colour("cb_signal_cyan"))
            y += row_h
    clip(img, [g - dp(4), top, right + dp(4), bottom], rows)
    note = ("MOCKUP - suite frame from activity_suite.xml + item_suite.xml + strings.xml + "
            "generated suite_hub.xml; example: install states; codes are the registry's")
    return img, note


# ---------------------------------------------------------------------------
# Launcher compatibility (activity_launcher_compat.xml)
# ---------------------------------------------------------------------------

def launcher_entries() -> list[dict]:
    """ApplyIconPack's compatibility list in COMPATIBILITY order: display name,
    art, apply mode, first note. The frame renders the data the screen
    renders, so it parses the same Kotlin (art/applyMode defaults BANNERS/AUTO
    mirror the data class)."""
    text = (ROOT / "app/src/main/java/tv/corebuilds/iconpack/ApplyIconPack.kt"
            ).read_text(encoding="utf-8")
    consts = {m.group(1): m.group(2) for m in re.finditer(
        r"private val (\w+)\s*=\s*(?:\n\s*)?\"((?:[^\"\\]|\\.)*)\"", text, re.S)}
    entry_re = re.compile(r"^\s{4}val (\w+) = Launcher\(", re.M)
    matches = list(entry_re.finditer(text))
    entries = {}
    for i, m in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        body = text[m.end():end]
        notes_m = re.search(r"notes = listOf\((.*?)\)\s*\n", body, re.S)
        note = ""
        if notes_m:
            # The entry's comment lines sit inside the listOf(...) and carry
            # capitalised words, so a name only counts as a constant when it
            # is actually defined in consts.
            const = re.search(r"\b[A-Z][A-Z_0-9]+\b", notes_m.group(1))
            inline = re.search(r"\"([^\"]*)\"", notes_m.group(1))
            note = (consts[const.group(0)] if const and const.group(0) in consts
                    else inline.group(1) if inline else "")
        entries[m.group(1)] = {
            "display": (re.search(r'displayName = "([^"]*)"', body) or [None, ""])[1],
            "art": (re.search(r"art = Art\.(\w+)", body) or [None, "BANNERS"])[1],
            "mode": (re.search(r"applyMode = ApplyMode\.(\w+)", body) or [None, "AUTO"])[1],
            "note": note,
        }

    def members(list_name: str) -> list[str]:
        m = re.search(rf"val {list_name} = listOf\(([^)]*)\)", text, re.S)
        return [t for t in re.findall(r"\b([A-Z]\w*)\b", m.group(1))] if m else []
    ordered = members("ALL") + members("NOT_SUPPORTED")
    return [entries[n] for n in ordered if n in entries]


def launcher_compat_frame() -> tuple[Image.Image, str]:
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    g, right, bottom = screen_box()
    top = header(img, STRINGS["launcher_compat_kicker"], STRINGS["launcher_compat_title"])

    # The detected-launcher line and the count, as the screen builds them.
    entries = launcher_entries()
    home = next(e for e in entries if e["display"].startswith("Projectivy"))
    fs = ofont(DIMENS["cb_text_data"])
    y = top + dp(DIMENS["cb_space_xs"])
    draw_text(draw, (g, y), fmt(STRINGS["launcher_compat_here_fmt"], home["display"],
                                art_sentence(home["art"])), fs, colour("cb_signal_cyan"))
    y += line_h(fs) + dp(3)
    draw_text(draw, (g, y), fmt(STRINGS["launcher_compat_sub_fmt"], len(entries)), fs,
              colour("cb_slate"))
    list_top = y + line_h(fs)

    f_name = ofont(DIMENS["cb_text_body"], "semibold")
    f_status = ofont(DIMENS["cb_text_kicker"], "semibold")
    pad = dp(DIMENS["cb_card_padding"])
    mode_strings = {"AUTO": STRINGS["launcher_compat_mode_auto"],
                    "WALK": STRINGS["launcher_compat_mode_walk"],
                    "PER_APP": STRINGS["launcher_compat_mode_per_app"],
                    "NONE": STRINGS["launcher_compat_mode_none"]}
    shown = 0

    def rows(canvas):
        nonlocal shown
        d = ImageDraw.Draw(canvas, "RGBA")
        y = list_top + dp(DIMENS["cb_space_sm"])
        for entry in entries:
            if y > bottom:
                break
            note_lines = wrap(d, entry["note"], fs, right - g - 2 * pad) if entry["note"] else []
            row_h = (2 * pad + line_h(f_name) + dp(4) + line_h(fs)
                     + (dp(3) + len(note_lines) * line_h(fs) if note_lines else 0))
            y += dp(6)
            active = entry is home
            row(canvas, [g, y, right, y + row_h], focused=active)
            draw_text(d, (g + pad, y + pad), entry["display"], f_name, colour("cb_ink"))
            cy = y + pad + line_h(f_name) // 2
            tag(canvas, right - pad, cy, mode_strings[entry["mode"]], pad_x=10, pad_y=3)
            if active:
                # launcher_compat_item_status: caps at 0.12em, 12dp after the name.
                sx = g + pad + text_width(d, entry["display"], f_name) + dp(DIMENS["cb_space_sm"])
                tracked(d, (sx, cy - line_h(f_status) // 2),
                        STRINGS["launcher_compat_active"].upper(), f_status,
                        colour("cb_slate"), 0.12)
            ay = y + pad + line_h(f_name) + dp(4)
            draw_text(d, (g + pad, ay), art_sentence(entry["art"]), fs, colour("cb_slate"))
            for i, line in enumerate(note_lines):
                draw_text(d, (g + pad, ay + line_h(fs) + dp(3) + i * line_h(fs)), line, fs,
                          colour("cb_slate"))
            y += row_h
            if y <= bottom:
                shown += 1
    clip(img, [g - dp(4), list_top, right + dp(4), bottom], rows)
    note = (f"MOCKUP - launcher compat: activity_launcher_compat.xml + ApplyIconPack.kt; "
            f"{shown} of {len(entries)} rows whole; example: focus on Projectivy, the home")
    return img, note


def art_sentence(art: str) -> str:
    return {"BANNERS": STRINGS["launcher_compat_art_banners"],
            "GLYPHS": STRINGS["launcher_compat_art_glyphs"],
            "NONE": STRINGS["launcher_compat_art_none"]}[art]


# ---------------------------------------------------------------------------
# Core Builds Glyphs Pack (the glyphs/ module): its one screen
# ---------------------------------------------------------------------------

GLYPHS_RES = ROOT / "glyphs/src/main/res"


def _glyphs_values(file: str, tag: str) -> dict[str, str]:
    out = {}
    for element in _xml(GLYPHS_RES / "values" / file):
        if element.tag == tag:
            out[element.get("name")] = re.sub(
                r"\s+", " ", "".join(element.itertext())).strip().replace("\\'", "'")
    return out


def _glyphs_count(xml_file: str, attribute: str) -> int:
    """GlyphsHomeActivity.countItems: <item> rows carrying the attribute,
    alternates left out."""
    return sum(1 for el in _xml(GLYPHS_RES / "xml" / xml_file).iter("item")
               if el.get(attribute) is not None
               and not is_alternate(el.get(attribute)))


def glyphs_home_frame() -> tuple[Image.Image, str]:
    """GlyphsHomeActivity on glyphs_home.xml, the package's only screen, with
    the button focused as it is on entry (<requestFocus/>). Every size, colour
    and string is read from the glyphs/ module, and the counts are counted the
    way the activity counts them."""
    text = _glyphs_values("strings_home.xml", "string")
    dimen = {k: float(v[:-2]) for k, v in _glyphs_values("dimens.xml", "dimen").items()}
    hexes = _glyphs_values("colors.xml", "color")

    def rgb(name: str) -> tuple:
        h = hexes[name].lstrip("#")
        return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)

    img = Image.new("RGBA", (W, H), rgb("glyphs_night"))
    draw = ImageDraw.Draw(img, "RGBA")
    x, y = dp(dimen["glyphs_home_pad_h"]), dp(dimen["glyphs_home_pad_v"])
    width = W - 2 * x
    gap, lead = dp(dimen["glyphs_home_gap"]), dp(dimen["glyphs_home_leading"])

    def block(s: str, size: float, colour_name: str, bold: bool = False) -> None:
        nonlocal y
        f = font("sans", size, bold=bold)
        for line in wrap(draw, s, f, width):
            draw_text(draw, (x, y), line, f, rgb(colour_name))
            y += dp(size) + int(dp(size) * 0.25) + lead

    block(text["glyphs_home_title"], dimen["glyphs_home_title"], "glyphs_ink", bold=True)
    y += gap
    icons = _glyphs_count("drawable.xml", "drawable")
    components = _glyphs_count("appfilter.xml", "component")
    counts = text["glyphs_home_counts"].replace("%1$d", str(icons)).replace("%2$d", str(components))
    block(counts, dimen["glyphs_home_counts"], "glyphs_cyan")
    y += gap
    block(text["glyphs_home_body"], dimen["glyphs_home_body"], "glyphs_slate")
    y += dp(dimen["glyphs_home_button_gap"])

    # The button: wrap_content with a 320dp minimum, cyan fill while focused,
    # CTA ink text (drawable/glyphs_button_bg.xml, color/glyphs_button_text.xml).
    f_btn = font("sans", dimen["glyphs_home_button_text"])
    label = text["glyphs_home_open"]
    bw = max(dp(dimen["glyphs_home_button_w"]),
             text_width(draw, label, f_btn) + 2 * dp(dimen["glyphs_home_button_pad"]))
    bh = dp(dimen["glyphs_home_button_h"])
    rrect(draw, [x, y, x + bw, y + bh], dp(dimen["glyphs_home_radius"]), fill=rgb("glyphs_cyan"))
    draw_text(draw, (x + bw // 2, y + bh // 2), label, f_btn, rgb("glyphs_cta_ink"), anchor="mm")
    y += bh + gap

    block(text["glyphs_home_hint"], dimen["glyphs_home_hint"], "glyphs_slate")
    note = (f"Glyphs Pack home: counts from its drawable.xml ({icons}) and "
            f"appfilter.xml ({components}), button focused; Outfit stands in for "
            f"the system sans")
    return img, note


FRAMES = [
    ("app-ui-catalogue.png", lambda: catalogue_frame(False)),
    ("app-ui-catalogue-update.png", lambda: catalogue_frame(True)),
    ("app-ui-wallpapers.png", wallpapers_frame),
    ("app-ui-settings.png", settings_frame),
    ("app-ui-settings-display.png", settings_display_frame),
    ("app-ui-faq.png", faq_frame),
    ("app-ui-auditor.png", lambda: auditor_frames()[0]),
    ("app-ui-auditor-qr.png", lambda: auditor_frames()[1]),
    ("app-ui-inspector.png", inspector_frame),
    ("app-ui-suite.png", suite_frame),
    ("app-ui-launcher-compat.png", launcher_compat_frame),
    ("app-ui-glyphs-home.png", glyphs_home_frame),
]


def render(name: str) -> tuple[bytes, str]:
    """(png bytes, digest of what was drawn) for one frame."""
    for target, build in FRAMES:
        if target == name:
            RECORD.clear()
            img, note = build()
            buf = io.BytesIO()
            caption(img, note).convert("RGB").save(buf, format="PNG", optimize=True)
            drawn = hashlib.sha256(
                ("\n".join(RECORD) + "\n" + note).encode("utf-8")).hexdigest()
            return buf.getvalue(), drawn
    raise KeyError(name)


# Everything a frame depends on, as globs relative to the repo root. The
# generator's own source is in the list: changing how a frame is drawn without
# regenerating it is the same drift as changing what it depicts.
INPUT_GLOBS = (
    "tools/build_app_ui_mockups.py",
    "tools/catalog.json",
    "app/src/main/assets/version.json",
    "app/src/main/assets/appfilter.xml",
    "app/src/main/assets/manifest/wallpapers.json",
    "app/src/main/res/layout/*.xml",
    "app/src/main/res/values/*.xml",
    # The primitives paint these drawables' shapes, colours and radii by hand
    # (bg_pill, bg_row, bg_rail_item, sw_track, ...) and the vectors directly,
    # so a change to any of them is a change to what the frames depict.
    "app/src/main/res/drawable/*.xml",
    "app/src/main/res/color/*.xml",
    "app/src/main/res/font/*",
    "tools/fonts/*.ttf",
    "glyphs/src/main/res/layout/*.xml",
    "glyphs/src/main/res/values/*.xml",
    "glyphs/src/main/res/xml/*.xml",
)
MANIFEST = DOCS / "app-ui-mockups.json"


def input_hashes() -> dict[str, str]:
    """{relative path: sha256} for every source the frames are built from."""
    out: dict[str, str] = {}
    for pattern in INPUT_GLOBS:
        for path in sorted(ROOT.glob(pattern)):
            if path.is_file():
                out[str(path.relative_to(ROOT))] = hashlib.sha256(
                    path.read_bytes()).hexdigest()
    return out


# A fixed string, measured in every font the frames used. Neither a source nor
# a drawn label: the rasteriser's opinion of its own metrics. When two machines
# disagree about a frame this is what separates "a source moved" from "the
# layout engine moved", which are different failures with different fixes.
METRIC_PROBE = "Handgloves 943 \u00b7 components \u2014 Wi"


def metrics_probe() -> dict[str, float]:
    """{font@px: width of METRIC_PROBE}. Call after rendering, so every font
    the frames actually used is in the cache."""
    scratch = ImageDraw.Draw(Image.new("RGB", (8, 8)))
    return {
        f"{role}@{px}": round(scratch.textlength(METRIC_PROBE, font=f), 3)
        for (role, px), f in sorted(_FONT_CACHE.items())
    }


def environment() -> str:
    """The renderer's identity: what a byte comparison is actually between."""
    import platform
    parts = [f"python {platform.python_version()}"]
    try:
        from PIL import features
        parts.append(f"freetype {features.version('freetype2')}")
        parts.append(f"layout {'raqm' if features.check('raqm') else 'basic'}")
    except Exception:
        pass
    for mod, attr in (("PIL", "__version__"), ("qrcode", "__version__"),
                      ("fontTools", "version"), ("resvg_py", "__version__")):
        try:
            parts.append(f"{mod} {getattr(__import__(mod), attr, 'installed')}")
        except Exception:
            parts.append(f"{mod} absent")
    return ", ".join(parts)


def pixel_difference(committed: bytes, fresh: bytes) -> str:
    """How far apart two renders are, which separates a rasteriser's
    antialiasing (many pixels off by one) from different content (few pixels,
    far apart) - the two call for completely different fixes."""
    # A diagnostic that can crash is worse than no diagnostic: the committed
    # side is exactly the thing under suspicion, so it may not decode.
    try:
        a = Image.open(io.BytesIO(committed)).convert("RGB")
    except Exception as e:
        return f"committed bytes do not decode as an image ({e})"
    try:
        b = Image.open(io.BytesIO(fresh)).convert("RGB")
    except Exception as e:
        return f"fresh render does not decode as an image ({e})"
    if a.size != b.size:
        return f"dimensions {a.size} vs {b.size}"
    deltas = [
        max(abs(x[0] - y[0]), abs(x[1] - y[1]), abs(x[2] - y[2]))
        for x, y in zip(a.getdata(), b.getdata())
    ]
    changed = sum(1 for d in deltas if d)
    return (f"{changed}/{len(deltas)} pixels differ ({100 * changed / len(deltas):.2f}%), "
            f"mean channel delta {sum(deltas) / len(deltas):.3f}, max {max(deltas)}")


def main(argv: list[str]) -> int:
    check = "--check" in argv
    report = "--report" in argv

    blobs: dict[str, bytes] = {}
    frames: dict[str, dict] = {}
    for name, _ in FRAMES:
        blob, drawn = render(name)
        blobs[name] = blob
        frames[name] = {"bytes": len(blob), "structure": drawn}

    inputs = input_hashes()
    pasted = pasted_digest()
    metrics = metrics_probe()
    inputs_digest = hashlib.sha256(
        "".join(f"{k}:{v}\n" for k, v in sorted(inputs.items())).encode()
    ).hexdigest()

    if report:
        print(f"renderer: {environment()}")
        print(f"sources: {len(inputs)} files, inputs digest {inputs_digest}")
        print(f"pasted inputs: {len(PASTED)} rasters, aggregate sha256 {pasted}")
        print(f"metrics probe {METRIC_PROBE!r}: "
              + ", ".join(f"{k}={v}" for k, v in sorted(metrics.items())))
        for name, _ in FRAMES:
            path = DOCS / name
            if not path.exists():
                print(f"  {name:34s} absent from docs/")
                continue
            committed = path.read_bytes()
            same = hashlib.sha256(committed).digest() == hashlib.sha256(blobs[name]).digest()
            print(f"  {name:34s} structure {frames[name]['structure'][:16]}"
                  f"  bytes {'match' if same else pixel_difference(committed, blobs[name])}")
        return 0

    if not check:
        for name, _ in FRAMES:
            (DOCS / name).write_bytes(blobs[name])
            print(f"wrote {(DOCS / name).relative_to(ROOT)} ({len(blobs[name]) // 1024} KB)")
        MANIFEST.write_text(json.dumps({
            "note": "Which sources the frames in docs/ were generated from, and "
                    "what each one depicts. Written by build_app_ui_mockups.py; "
                    "never hand-edit. The PNG bytes are not recorded on purpose: "
                    "text rendering is not reproducible across machines, so "
                    "--check compares the sources and the drawn structure "
                    "instead. See the module docstring.",
            "generator": "tools/build_app_ui_mockups.py",
            "inputs_digest": inputs_digest,
            "inputs": inputs,
            "pasted": {"count": len(PASTED), "digest": pasted},
            "metrics": metrics,
            "renderer": environment(),
            "frames": frames,
        }, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"wrote {MANIFEST.relative_to(ROOT)} - {len(inputs)} sources, "
              f"{len(PASTED)} pasted rasters, {len(FRAMES)} frames")
        return 0

    problems: list[str] = []
    if not MANIFEST.exists():
        problems.append(
            f"{MANIFEST.relative_to(ROOT)} is missing - the committed frames "
            "carry no record of the sources they were generated from")
        manifest: dict = {}
    else:
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

    was = manifest.get("inputs", {})
    changed = sorted(k for k in set(was) & set(inputs) if was[k] != inputs[k])
    added = sorted(set(inputs) - set(was))
    removed = sorted(set(was) - set(inputs))
    if changed:
        problems.append(f"sources changed since the frames were generated: {changed}")
    if added:
        problems.append(f"sources the committed frames never read: {added}")
    if removed:
        problems.append(f"sources that no longer exist: {removed}")

    was_metrics = manifest.get("metrics", {})
    if was_metrics != metrics:
        differing = [
            f"{k}: recorded {was_metrics.get(k, 'absent')}, here {metrics[k]}"
            for k in sorted(set(was_metrics) | set(metrics))
            if was_metrics.get(k) != metrics.get(k)
        ]
        problems.append(
            "text metrics differ, so the frames would be laid out differently "
            f"here than where they were committed ({'; '.join(differing[:6])}). "
            "Pillow measures through raqm/harfbuzz when it can find it and "
            "through FreeType when it cannot; font() pins BASIC, so if this "
            "fires the pin has been lost or the fonts have moved")
    was_pasted = manifest.get("pasted", {})
    if was_pasted.get("digest") != pasted:
        problems.append(
            f"the rasters the frames paste changed (recorded "
            f"{str(was_pasted.get('digest', '-'))[:16]}, now {pasted[:16]})")

    was_frames = manifest.get("frames", {})
    for name, _ in FRAMES:
        path = DOCS / name
        if not path.exists():
            problems.append(f"{name} is missing from docs/")
            continue
        if was_frames.get(name, {}).get("structure") != frames[name]["structure"]:
            problems.append(
                f"{name} does not depict what the sources now say (structure "
                f"{str(was_frames.get(name, {}).get('structure', '-'))[:16]} "
                f"recorded, {frames[name]['structure'][:16]} now)")
        # Corruption and panel width are machine-independent, so they are worth
        # checking even though pixel bytes are not.
        try:
            with Image.open(path) as img:
                img.load()
                if img.size[0] != W:
                    problems.append(
                        f"{name} is {img.size[0]}px wide, not the {W}px panel")
        except Exception as e:
            problems.append(f"{name} does not decode as an image ({e})")
    stale = sorted(set(was_frames) - {n for n, _ in FRAMES})
    if stale:
        problems.append(f"the manifest lists frames the generator no longer builds: {stale}")

    if problems:
        print(f"mockup drift: {len(problems)} problem(s)")
        for problem in problems:
            print("  \u2717 " + problem)
        print("Run: python tools/build_app_ui_mockups.py")
        if os.environ.get("GITHUB_ACTIONS"):
            # A runner's log storage is not always reachable from the workspace
            # that has to fix the failure, so the diagnosis travels as
            # annotations. Capped: GitHub keeps ten per level per run.
            print(f"::error::mockup drift, {len(problems)} problem(s) | "
                  f"{environment()} | inputs digest {inputs_digest[:16]}")
            for problem in problems[:6]:
                print(f"::error::{problem[:400]}")
        return 1

    print(f"mockups ok - {len(FRAMES)} frames depict the {len(inputs)} sources "
          f"in the tree ({len(PASTED)} pasted rasters, inputs digest "
          f"{inputs_digest[:16]})")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
