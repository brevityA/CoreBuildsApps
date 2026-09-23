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
Latestrelease/version.json, the inspector's component list from the bundled
appfilter.xml asset, and the artwork is the real bundled PNG - icon tiles paste
drawable-nodpi, wallpaper tiles paste wallpapers_thumbs. The auditor frame's QR
is a real scannable code of the same generated deep link AuditorActivity builds
(issue_prefill.xml fmt + URL-encoded args), so scanning the mockup opens the
same prefilled issue form scanning the TV would.

What is example data is said out loud: each frame carries a caption band under
the 1080p panel naming which values are invented (focused tile, detected
launchers, install states, unmapped-app rows), because a mockup that hides its
fictions ends up quoting them in a release note.

Type: the pack's own Outfit (tools/fonts, SIL OFL) for the sans roles and
DejaVu Sans Mono for the mono roles, matching what the layouts set
(fontFamily="monospace" on kickers, counts and components). The device renders
body copy in Roboto; Outfit Bold is the stand-in here, and the only one - the
frames are illustrative, not a font-fidelity claim.

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

from PIL import Image, ImageDraw, ImageFont

import qrcode
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
VERSION = __import__("json").load(open(ROOT / "Latestrelease/version.json"))

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

ICONS_DIR = RES / "drawable-nodpi"
THUMBS_DIR = ROOT / "app/src/main/assets/wallpapers_thumbs"


# The header's second number and the wallpapers row's count are read, not typed:
# 1765 component items in the bundled appfilter, and the wallpaper manifest the
# browser actually loads.
APPFILTER_TEXT = (ROOT / "app/src/main/assets/appfilter.xml").read_text(encoding="utf-8")
COMPONENT_COUNT = len(re.findall(r'component="ComponentInfo', APPFILTER_TEXT))
WALLPAPERS = __import__("json").load(
    open(ROOT / "app/src/main/assets/manifest/wallpapers.json"))["wallpapers"]

# Example data, named as such in every caption: the launchers a TV with several
# installed would be detected as. The list under Apply and the ALSO APPLIES TO
# chips are drawn from it.
EXAMPLE_LAUNCHERS = ["Projectivy", "Launchchair", "Nova"]


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


def button(img: Image.Image, x: int, y: int, label: str, kind: str,
           focused: bool = False, min_w_dp: float = 0, size_sp: float = 17,
           bold: bool = True) -> tuple[int, int]:
    """kind: 'cta' | 'ghost'. Returns (width, height) in px."""
    draw = ImageDraw.Draw(img, "RGBA")
    f = font("sans", size_sp, bold=bold)
    pad_x, pad_y = dp(DIMENS["cb_space_lg"]), dp(DIMENS["cb_space_sm"])
    w = text_width(draw, label, f) + 2 * pad_x
    w = max(w, dp(min_w_dp))
    h = max(dp(DIMENS["cb_target_min"]), dp(size_sp) + 2 * pad_y)
    box = [x, y, x + w, y + h]
    radius = dp(DIMENS["cb_radius_button"])
    if kind == "cta":
        img.paste(masked(cta_gradient((w, h)), radius), (x, y),
                  masked(cta_gradient((w, h)), radius))
        ink = colour("cb_cta_ink")
    else:
        rrect(draw, box, radius, fill=colour("cb_panel") if focused else None,
              outline=colour("cb_signal_cyan") if focused else (0, 212, 255, 0x59),
              width=dp(DIMENS["cb_focus_ring"]) if focused else dp(1))
        ink = colour("cb_ink")
    if focused and kind == "cta":
        rrect(draw, box, radius, outline=colour("cb_glow_cyan"),
              width=dp(DIMENS["cb_focus_ring"]))
    draw_text(draw, (x + w / 2, y + h / 2), label, f, ink, anchor="mm")
    return w, h


def focus_ring(img: Image.Image, box, glow: bool = True):
    """bg_card's focused state: 3dp cyan ring, 3dp gap of night, card inside."""
    draw = ImageDraw.Draw(img, "RGBA")
    x0, y0, x1, y1 = box
    ring = dp(DIMENS["cb_focus_ring"])
    gap = dp(DIMENS["cb_focus_gap"])
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


def kicker_chip(img: Image.Image, x: int, y: int, label: str) -> int:
    draw = ImageDraw.Draw(img, "RGBA")
    f = font("mono", DIMENS["cb_text_kicker"], bold=True)
    pad = dp(DIMENS["cb_space_sm"])
    w = text_width(draw, label, f) + 2 * pad + dp(7)
    h = dp(DIMENS["cb_text_kicker"]) + 2 * dp(7)
    rrect(draw, [x, y, x + w, y + h], dp(DIMENS["cb_radius_field"]),
          fill=(0x14, 0x20, 0x2B), outline=(0x00, 0xD4, 0xFF, 0x33), width=dp(1))
    draw_text(draw, (x + pad, y + h / 2 - dp(1)), label, f, colour("cb_signal_cyan"))
    return w


def header(img: Image.Image, kick: str, title: str, sub: str | None = None) -> int:
    """The pinned header grammar: kicker chip + title, Back on the right."""
    gutter = dp(DIMENS["cb_gutter_side"])
    y = dp(24)
    kw = kicker_chip(img, gutter, y, kick)
    draw = ImageDraw.Draw(img, "RGBA")
    f = font("sans", DIMENS["cb_text_title"], bold=True)
    draw_text(draw, (gutter + kw + dp(DIMENS["cb_space_md"]), y - dp(4)), title, f,
              colour("cb_ink"))
    bw, bh = button(img, W - gutter - dp(150), y - dp(6), STRINGS["action_back"], "ghost",
                    min_w_dp=150)
    if sub:
        fs = font("sans", DIMENS["cb_text_data"])
        draw_text(draw, (gutter, y + dp(34)), sub, fs, colour("cb_slate"))
    return y + dp(64)


def chip_pill(img: Image.Image, x: int, y: int, label: str, active: bool,
              focused: bool = False) -> int:
    draw = ImageDraw.Draw(img, "RGBA")
    f = font("sans", DIMENS["cb_text_label"], bold=True)
    pad = dp(DIMENS["cb_space_md"])
    w = text_width(draw, label, f) + 2 * pad
    h = max(dp(DIMENS["cb_target_min"]), dp(DIMENS["cb_text_label"]) + 2 * pad)
    box = [x, y, x + w, y + h]
    radius = dp(DIMENS["cb_radius_pill"]) if DIMENS["cb_radius_pill"] < 100 else h // 2
    if active:
        grad = masked(cta_gradient((w, h)), radius)
        img.paste(grad, (x, y), grad)
        ink = colour("cb_cta_ink")
    else:
        rrect(draw, box, radius, fill=(0x14, 0x20, 0x2B),
              outline=(0x00, 0xD4, 0xFF, 0x33), width=dp(1))
        ink = colour("cb_ink")
    if focused:
        rrect(draw, [x - dp(3), y - dp(3), x + w + dp(3), y + h + dp(3)],
              radius + dp(3), outline=colour("cb_signal_cyan"),
              width=dp(DIMENS["cb_focus_ring"]))
    draw_text(draw, (x + w / 2, y + h / 2), label, f, ink, anchor="mm")
    return w


def search_field(img: Image.Image, x: int, y: int, w_dp: float,
                 hint: str | None = None) -> None:
    """The field. `hint` is what MainActivity sets at runtime (search_hint_fmt);
    the layout's static hint is only what a screenshot before binding shows."""
    draw = ImageDraw.Draw(img, "RGBA")
    w, h = dp(w_dp), dp(DIMENS["cb_target_min"])
    rrect(draw, [x, y, x + w, y + h], dp(DIMENS["cb_radius_field"]),
          fill=(0x14, 0x20, 0x2B), outline=(0x00, 0xD4, 0xFF, 0x33), width=dp(1))
    f = font("sans", DIMENS["cb_text_label"])
    draw_text(draw, (x + dp(DIMENS["cb_space_md"]), y + h / 2),
              hint or STRINGS["search_hint"], f, colour("cb_slate"), anchor="lm")


def entry_row_lines(sub: str) -> list[str]:
    """The rail card's subtitle, wrapped to the rail's text width - the same
    wrap the match_parent TextView does on the device."""
    inset = dp(DIMENS["cb_card_padding"])
    probe = ImageDraw.Draw(Image.new("RGBA", (8, 8)))
    return wrap(probe, sub, font("mono", DIMENS["cb_text_data"]),
                dp(DIMENS["cb_rail_width"]) - 2 * inset)


def entry_row_height(sub: str) -> int:
    lines = entry_row_lines(sub)
    inset = dp(DIMENS["cb_card_padding"])
    return 2 * inset + dp(20) + dp(3) + len(lines) * dp(17)


def entry_row(img: Image.Image, box, title: str, sub: str, focused: bool = False) -> None:
    """One of the rail's three entry cards: title over subtitle, as the sheet
    draws them - the rail is the one place with the height for both lines."""
    draw = ImageDraw.Draw(img, "RGBA")
    if focused:
        focus_ring(img, box)
    else:
        card(img, box)
    inset = dp(DIMENS["cb_card_padding"])
    draw_text(draw, (box[0] + inset, box[1] + inset), title,
              font("sans", DIMENS["cb_text_body"], bold=True), colour("cb_ink"))
    for i, line in enumerate(entry_row_lines(sub)):
        draw_text(draw, (box[0] + inset, box[1] + inset + dp(23) + i * dp(17)),
                  line, font("mono", DIMENS["cb_text_data"]), colour("cb_slate"))


def rail_box() -> tuple[int, int, int]:
    """(rail left, rail right, pane left) for the two-pane screens."""
    g = dp(DIMENS["cb_gutter_side"])
    rail = dp(DIMENS["cb_rail_width"])
    gap = dp(DIMENS["cb_space_md"])
    return g, g + rail, g + rail + gap


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
    return Image.new("RGBA", (W, H), colour("cb_night"))


# ---------------------------------------------------------------------------
# Frames
# ---------------------------------------------------------------------------

def category_counts() -> list[tuple[str, str, int]]:
    counts: dict[str, int] = {}
    for cat in CATS:
        counts[cat] = counts.get(cat, 0) + 1
    out = [("ALL", STRINGS["chip_all"], len(PACK))]
    if any(BESPOKE):
        out.append(("BESPOKE", STRINGS["chip_brandmarks"], sum(BESPOKE)))
    for key, label in CHIP_ORDER:
        if key in counts:
            out.append((key, label, counts[key]))
    return out


def catalogue_frame(with_update_bar: bool) -> tuple[Image.Image, str]:
    """The home screen as built: docs/design/app-ui-apply-wallpapers.png as a
    landscape two-pane TV screen - the sheet's vertical stack in a fixed left
    rail, the glyph grid owning the right pane - drawn at 960x540dp with the
    real strings, dimens and bundled art.
    """
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    g, rail_r, pane_l = rail_box()
    right = W - g
    gap = dp(DIMENS["cb_space_xs"])
    row_h = dp(DIMENS["cb_target_min"])
    y = dp(DIMENS["cb_gutter_top"])
    f_data = font("mono", DIMENS["cb_text_data"])

    # The update bar spans both panes: wider than the rail, more urgent than
    # the grid.
    top = y
    if with_update_bar:
        bar_h = dp(58)
        pad = dp(DIMENS["cb_space_md"])
        rrect(draw, [g, top, right, top + bar_h], dp(DIMENS["cb_radius_card"]),
              fill=(0x10, 0x1A, 0x26), outline=colour("cb_signal_cyan"), width=dp(1))
        draw_text(draw, (g + pad, top + dp(9)),
                  fmt(STRINGS["update_available_fmt"], VERSION["versionName"],
                      VERSION["iconCount"]),
                  font("sans", DIMENS["cb_text_label"], bold=True), colour("cb_ink"))
        f_btn = font("sans", DIMENS["cb_text_body"], bold=True)
        pad_x = dp(DIMENS["cb_space_lg"])
        dl = fmt(STRINGS["update_download"], VERSION["versionName"])
        dl_w = text_width(draw, dl, f_btn) + 2 * pad_x
        later_w = text_width(draw, STRINGS["update_later"], f_btn) + 2 * pad_x
        bx = right - pad - later_w - dp(DIMENS["cb_space_sm"]) - dl_w
        by = top + (bar_h - row_h) // 2
        line = "\u2022  " + VERSION["highlights"][0]
        limit = bx - dp(DIMENS["cb_space_md"])
        while text_width(draw, line + "\u2026", f_data) > limit - (g + pad):
            line = line[:-1]
        draw_text(draw, (g + pad, top + dp(31)), line + "\u2026", f_data,
                  colour("cb_ink"))
        top += bar_h + gap
        button(img, bx, by, dl, "cta", focused=True)
        button(img, bx + dl_w + dp(DIMENS["cb_space_sm"]), by,
               STRINGS["update_later"], "ghost")

    body_top = top

    # ---- LEFT RAIL -------------------------------------------------------
    ry = body_top
    draw_text(draw, (g, ry + dp(2)), STRINGS["kicker"],
              font("mono", DIMENS["cb_text_label"], bold=True),
              colour("cb_signal_cyan"))
    draw_text(draw, (g, ry + dp(26)),
              fmt(STRINGS["pack_stats_fmt"], len(PACK), COMPONENT_COUNT),
              f_data, colour("cb_slate"))
    ry += dp(48)
    button(img, g, ry, STRINGS["cta_apply"], "cta", min_w_dp=DIMENS["cb_rail_width"])
    ry += row_h + gap
    if not with_update_bar:
        for i, line in enumerate(wrap(draw, " - ".join(EXAMPLE_LAUNCHERS), f_data,
                                      dp(DIMENS["cb_rail_width"]))[:2]):
            draw_text(draw, (g, ry + i * dp(18)), line, f_data, colour("cb_slate"))
        ry += dp(20)
    ry += gap
    tile_h = dp(DIMENS["cb_tile_icon"]) + 2 * dp(DIMENS["cb_card_padding"])
    rows = [
        (STRINGS["wp_entry"], fmt(STRINGS["wp_entry_sub_fmt"], len(WALLPAPERS))),
        (STRINGS["settings_label"], STRINGS["settings_entry_sub"]),
        (STRINGS["about_label"],
         fmt(STRINGS["about_entry_sub_fmt"], VERSION["versionName"])),
    ]
    for index, (title, sub_text) in enumerate(rows):
        h = entry_row_height(sub_text)
        entry_row(img, [g, ry, rail_r, ry + h], title, sub_text,
                  focused=(index == 0 and not with_update_bar))
        ry += h + gap
    if not with_update_bar:
        draw_text(draw, (g, ry + dp(4)), STRINGS["cta_also_applies"],
                  font("mono", DIMENS["cb_text_kicker"], bold=True),
                  colour("cb_signal_cyan"))
        ry += dp(24)
        cx = g
        for name in EXAMPLE_LAUNCHERS[1:]:
            w = chip_pill(img, cx, ry, name, active=False)
            cx += w + gap
            if cx > rail_r:
                break
        ry += row_h

    # ---- RIGHT PANE ------------------------------------------------------
    search_field(img, pane_l, body_top, (right - pane_l) / SCALE,
                 hint=fmt(STRINGS["search_hint_fmt"], len(PACK)))
    py = body_top + row_h + gap
    x = pane_l
    f_chip = font("sans", DIMENS["cb_text_label"], bold=True)
    for index, (_key, label, count) in enumerate(category_counts()):
        label_text = fmt(STRINGS["chip_count_fmt"], label, count)
        chip_w = text_width(draw, label_text, f_chip) + 2 * dp(DIMENS["cb_space_md"])
        if x + chip_w > right:
            break
        x += chip_pill(img, x, py, label_text, active=index == 0) + dp(DIMENS["cb_space_sm"])
    py += row_h + gap

    # Grid: columns are pane width over tile pitch, exactly as MainActivity
    # derives them; rows run past the panel edge and clip, as the RecyclerView
    # does.
    pane_w = right - pane_l
    spans = max(2, int(pane_w // tile_h))
    tile_w = pane_w // spans
    focused_index = -1 if with_update_bar else 2
    row = 0
    while py < H:
        for col in range(spans):
            index = row * spans + col
            if index >= len(PACK):
                break
            box = [pane_l + col * tile_w, py, pane_l + (col + 1) * tile_w,
                   py + tile_h]
            if index == focused_index:
                focus_ring(img, box)
            else:
                card(img, box)
            icon = dp(DIMENS["cb_tile_icon"])
            cx, cy = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
            paste_art(img, ICONS_DIR / f"{PACK[index]}.png",
                      [cx - icon // 2, cy - icon // 2, cx + icon // 2, cy + icon // 2])
        py += tile_h
        row += 1

    extra = ("update bar: first highlight from version.json, launcher list and "
             "ALSO APPLIES TO reclaimed while it is up; " if with_update_bar
             else "")
    note = ("MOCKUP - catalogue from activity_main.xml + strings/dimens/colors + "
            f"appfilter ({COMPONENT_COUNT} components); {extra}"
            "example: launchers, focus")
    return img, note


def wallpapers_frame() -> tuple[Image.Image, str]:
    """The wallpapers browser as built: landscape two-pane. Header across the
    top, series chips and the export foot in the left rail, the named-thumb
    grid owning the right pane."""
    walls = WALLPAPERS
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    g, rail_r, pane_l = rail_box()
    right = W - g
    gap = dp(DIMENS["cb_space_xs"])
    y = dp(DIMENS["cb_gutter_top"])
    f_data = font("mono", DIMENS["cb_text_data"])

    draw_text(draw, (g, y + dp(2)), STRINGS["wp_kicker"],
              font("mono", DIMENS["cb_text_label"], bold=True),
              colour("cb_signal_cyan"))
    draw_text(draw, (g, y + dp(26)), fmt(STRINGS["wp_sub_fmt"], len(walls)),
              f_data, colour("cb_slate"))
    button(img, right - dp(150), y, STRINGS["wp_back"], "ghost", min_w_dp=150)
    body_top = y + dp(DIMENS["cb_target_min"]) + dp(DIMENS["cb_space_md"])

    # ---- LEFT RAIL: series chips, then the export foot.
    series: list[str] = []
    for wall in walls:
        if wall["series"] not in series:
            series.append(wall["series"])
    labels = [STRINGS["chip_all"]] + [
        " ".join(part.capitalize()
                 for part in s.removeprefix("series-").split("-", 1)[1].split("-"))
        for s in series
    ]
    foot_h = dp(DIMENS["cb_target_min"]) + gap
    f_para = font("sans", DIMENS["cb_text_data"])
    para = wrap(draw, STRINGS["wp_export_sub"], f_para, dp(DIMENS["cb_rail_width"]))
    foot_h += len(para) * dp(19)
    fy = H - dp(DIMENS["cb_gutter_bottom"]) - foot_h
    # The weighted chip list scrolls in the rail, so chips run to the foot's top
    # edge and clip there, exactly as the RecyclerView does.
    chips_bottom = fy - dp(DIMENS["cb_space_md"])
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ry = body_top
    for index, label in enumerate(labels):
        chip_pill(layer, g, ry, label, active=index == 0, focused=index == 0)
        ry += dp(DIMENS["cb_target_min"]) + gap
        if ry > chips_bottom + dp(DIMENS["cb_target_min"]):
            break
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).rectangle([0, body_top, rail_r, chips_bottom], fill=255)
    img.paste(layer, (0, 0), Image.composite(
        layer.split()[3], Image.new("L", (W, H), 0), mask))
    f_pill = font("sans", DIMENS["cb_text_label"], bold=True)
    pill_label = STRINGS["wp_export"].upper()
    rrect(draw, [g, fy, rail_r, fy + dp(DIMENS["cb_target_min"])],
          dp(DIMENS["cb_radius_button"]), outline=(0, 212, 255, 0x59), width=dp(1))
    draw_text(draw, ((g + rail_r) / 2, fy + dp(DIMENS["cb_target_min"]) / 2),
              pill_label, f_pill, colour("cb_signal_cyan"), anchor="mm")
    for i, line in enumerate(para):
        draw_text(draw, (g, fy + dp(DIMENS["cb_target_min"]) + gap + i * dp(19)),
                  line, f_para, colour("cb_slate"))

    # ---- RIGHT PANE: the thumb grid, full height.
    pane_w = right - pane_l
    thumb = dp(DIMENS["cb_wp_thumb"])
    tile_h = thumb + dp(52)
    spans = max(2, int(pane_w // tile_h))
    tile_w = pane_w // spans
    f_tile = font("sans", DIMENS["cb_text_label"])
    row = 0
    py = body_top
    while py < H:
        for col in range(spans):
            index = row * spans + col
            if index >= len(walls):
                break
            box = [pane_l + col * tile_w, py, pane_l + (col + 1) * tile_w,
                   py + tile_h - dp(2)]
            if index == 0:
                focus_ring(img, box)
            else:
                card(img, box)
            inset = dp(DIMENS["cb_focus_inset"]) + dp(6)
            thumb_path = THUMBS_DIR / (
                walls[index]["url"].split("/")[-1].rsplit(".", 1)[0] + ".jpg")
            paste_art(img, thumb_path,
                      [box[0] + inset, box[1] + inset, box[2] - inset,
                       box[1] + inset + thumb], radius=dp(6))
            title = walls[index]["name"].split("\u00b7 ", 1)[-1]
            draw_text(draw, ((box[0] + box[2]) / 2, box[1] + inset + thumb + dp(10)),
                      title, f_tile, colour("cb_ink"), anchor="ma")
        py += tile_h
        row += 1

    note = ("MOCKUP - wallpapers from activity_wallpapers.xml + strings.xml + "
            f"manifest/wallpapers.json ({len(walls)} walls), bundled thumbs; "
            "example: focus, chip")
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
        ("switch", "settings_banner_title", "settings_banner_sub", False),
        ("switch", "settings_apply_art_title", "settings_apply_art_sub", False),
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
    ]


def paint_settings_viewport(img, order, focused_row):
    """One viewportful of the settings list, already scrolled: rows above the
    scroll point were popped by the caller, the first visible entry leads,
    and the footer lands only if the list ends inside the viewport."""
    draw = ImageDraw.Draw(img, "RGBA")
    top = header(img, STRINGS["settings_kicker"], STRINGS["settings_title"])
    g = dp(DIMENS["cb_gutter_side"])
    y = top + dp(6)
    f_group = font("mono", DIMENS["cb_text_kicker"], bold=True)
    f_title = font("sans", DIMENS["cb_text_body"], bold=True)
    f_sub = font("sans", DIMENS["cb_text_data"])
    def row_height(entry) -> int:
        sub_lines = wrap(draw, STRINGS[entry[2]], f_sub, W - 2 * g - dp(160))[:2]
        return dp(16) + dp(24) + len(sub_lines) * dp(19) + dp(10)

    for position, entry in enumerate(order):
        # A group kicker whose first row would not fit is a dangling label at
        # the bottom of the viewport; stop before it, the way a real scroll
        # position would never park half a row on screen.
        if entry[0] == "group":
            nxt = order[position + 1] if position + 1 < len(order) else None
            if nxt is None or y + dp(26) + row_height(nxt) > H - dp(44):
                break
        elif y + row_height(entry) > H - dp(44):
            break
        if entry[0] == "group":
            draw_text(draw, (g, y), STRINGS[entry[1]], f_group, colour("cb_signal_cyan"))
            y += dp(26)
            continue
        title, sub = STRINGS[entry[1]], STRINGS[entry[2]]
        sub_lines = wrap(draw, sub, f_sub, W - 2 * g - dp(160))[:2]
        row_h = row_height(entry)
        focused = entry[1] == focused_row
        if focused:
            focus_ring(img, [g, y, W - g, y + row_h])
            inner = dp(DIMENS["cb_focus_inset"])
        else:
            card(img, [g, y, W - g, y + row_h])
            inner = dp(DIMENS["cb_focus_inset"])
        draw_text(draw, (g + inner + dp(8), y + inner + dp(2)), title, f_title,
                  colour("cb_ink"))
        for i, line in enumerate(sub_lines):
            draw_text(draw, (g + inner + dp(8), y + inner + dp(26) + i * dp(19)), line,
                      f_sub, colour("cb_slate"))
        if entry[0] == "switch":
            on = entry[3]
            sx, sy = W - g - dp(56), y + row_h // 2 - dp(11)
            rrect(draw, [sx, sy, sx + dp(46), sy + dp(22)], dp(11),
                  fill=colour("cb_signal_cyan") if on else colour("cb_panel"),
                  outline=(0x00, 0xD4, 0xFF, 0x33), width=dp(1))
            knob = sx + dp(24) if on else sx + dp(4)
            draw.ellipse([knob, sy + dp(3), knob + dp(16), sy + dp(19)],
                         fill=colour("cb_ink") if on else colour("cb_slate"))
        elif entry[3]:
            label = STRINGS[entry[3]]
            f_btn = font("sans", DIMENS["cb_text_label"], bold=True)
            bw = text_width(draw, label, f_btn) + 2 * dp(DIMENS["cb_space_lg"])
            button(img, W - g - dp(DIMENS["cb_focus_inset"]) - dp(8) - bw,
                   y + row_h // 2 - dp(24), label, "ghost",
                   size_sp=DIMENS["cb_text_label"])
        y += row_h + dp(8)
    if y < H - dp(40):
        draw_text(draw, (g, H - dp(28)), STRINGS["settings_footer"], f_sub,
                  colour("cb_slate"))


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
    # see is not a toggle. Focus ring on the new row, in its shipped state:
    # off, square glyphs.
    while order and order[0][1] != "settings_group_display":
        order.pop(0)
    paint_settings_viewport(img, order, "settings_banner_title")
    note = ("MOCKUP - settings frame scrolled to DISPLAY; example: focus on the "
            "banner previews switch (off - square glyphs, the 1.9.2 default)")
    return img, note


def faq_frame() -> tuple[Image.Image, str]:
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    top = header(img, STRINGS["faq_kicker"], STRINGS["faq_title"])
    g = dp(DIMENS["cb_gutter_side"])
    y = top + dp(6)
    f_title = font("sans", DIMENS["cb_text_body"], bold=True)
    f_body = font("sans", DIMENS["cb_text_data"])
    for key in ("cache", "override", "monet", "unmapped"):
        title = STRINGS[f"faq_{key}_title"]
        body = STRINGS[f"faq_{key}_body"]
        lines = wrap(draw, body, f_body, W - 2 * g - dp(40))
        row_h = dp(24) + len(lines) * dp(19) + dp(16)
        card(img, [g, y, W - g, y + row_h])
        inner = dp(DIMENS["cb_focus_inset"])
        draw_text(draw, (g + inner + dp(8), y + inner), title, f_title, colour("cb_ink"))
        for i, line in enumerate(lines):
            draw_text(draw, (g + inner + dp(8), y + inner + dp(24) + i * dp(19)), line,
                      f_body, colour("cb_slate"))
        y += row_h + dp(10)
    note = ("MOCKUP - FAQ frame from activity_faq.xml + strings.xml; bodies are the "
            "shipped strings, which paraphrase the repo docs")
    return img, note


def auditor_frames() -> list[tuple[Image.Image, str]]:
    examples = [
        ("TiviMate IPTV Player", "ar.tvplayer/ar.tvplayer.tv.ui.MainActivity"),
        ("IPTV Smarters Pro", "com.whatsapp.iptv.smarters.activity.MainActivity"),
        ("XCIPTV Player", "com.otg.xciptv.activity.SplashActivity"),
    ]
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    top = header(img, STRINGS["audit_kicker"], STRINGS["audit_title"],
                 fmt(STRINGS["audit_count_fmt"], len(examples)))
    g = dp(DIMENS["cb_gutter_side"])
    y = top + dp(10)
    f_label = font("sans", DIMENS["cb_text_body"], bold=True)
    f_pkg = font("mono", DIMENS["cb_text_data"])
    for index, (label, component) in enumerate(examples):
        row_h = dp(64)
        if index == 0:
            focus_ring(img, [g, y, W - g, y + row_h])
            inner = dp(DIMENS["cb_focus_inset"])
        else:
            card(img, [g, y, W - g, y + row_h])
            inner = dp(DIMENS["cb_focus_inset"])
        draw_text(draw, (g + inner + dp(8), y + inner + dp(2)), label, f_label,
                  colour("cb_ink"))
        draw_text(draw, (g + inner + dp(8), y + inner + dp(30)), component, f_pkg,
                  colour("cb_slate"))
        y += row_h + dp(8)
    list_note = ("MOCKUP - auditor frame from activity_auditor.xml + strings.xml; "
                 "example: the three unmapped rows (a real scan lists this TV's apps)")
    frames = [(img, list_note)]

    # QR panel: the same generated deep link the screen builds, really scannable.
    label, component = examples[0]
    note_text = f"Scanned on Example TV, Android 14, pack {VERSION['versionName']}"
    url = fmt(
        STRINGS["audit_issue_url_fmt"],
        urllib.parse.quote(label), urllib.parse.quote(component),
        urllib.parse.quote(note_text),
    )
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    top = header(img, STRINGS["audit_kicker"], STRINGS["audit_title"])
    qr = qrcode.QRCode(error_correction=ERROR_CORRECT_M,
                       box_size=8, border=4)
    qr.add_data(url)
    qr.make(fit=True)
    qr_img = qr.make_image(fill_color="black", back_color="white").convert("RGB")
    size = dp(330)
    qr_img = qr_img.resize((size, size), Image.NEAREST)
    px, py = (W - size) // 2, top + dp(20)
    img.paste(qr_img, (px, py))
    f_url = font("mono", DIMENS["cb_text_data"])
    url_lines = wrap(draw, url, f_url, W - 2 * g)[:2]
    if len(wrap(draw, url, f_url, W - 2 * g)) > 2:
        url_lines[-1] = url_lines[-1][: -1] + "…"
    for i, line in enumerate(url_lines):
        draw_text(draw, (W // 2, py + size + dp(14) + i * dp(18)), line, f_url,
                  colour("cb_slate"), anchor="ma")
    f_hint = font("sans", DIMENS["cb_text_data"])
    hint_top = py + size + dp(14) + len(url_lines) * dp(18) + dp(10)
    for i, line in enumerate(wrap(draw, STRINGS["audit_qr_hint"], f_hint, dp(620))):
        draw_text(draw, (W // 2, hint_top + i * dp(19)), line, f_hint,
                  colour("cb_slate"), anchor="ma")
    qr_note = ("MOCKUP panel with a REAL scannable QR of an EXAMPLE prefilled issue URL "
               "(issue_prefill.xml fmt + URL-encoded args, as AuditorActivity builds it)")
    frames.append((img, qr_note))
    return frames


def inspector_frame() -> tuple[Image.Image, str]:
    index = PACK.index("spotify") if "spotify" in PACK else 0
    drawable = PACK[index]
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    top = header(img, STRINGS["inspector_kicker"], NAMES[index])
    g = dp(DIMENS["cb_gutter_side"])
    y = top + dp(10)
    comps = components_of(drawable)[:4]
    row_h = dp(40) + dp(160) + dp(20)
    card(img, [g, y, W - g, y + row_h])
    inner = dp(DIMENS["cb_focus_inset"]) + dp(DIMENS["cb_card_padding"])
    paste_art(img, ICONS_DIR / f"{drawable}.png",
              [g + inner, y + inner, g + inner + dp(160), y + inner + dp(160)])
    f_meta = font("sans", DIMENS["cb_text_data"])
    f_mono = font("mono", DIMENS["cb_text_data"])
    tx = g + inner + dp(160) + dp(DIMENS["cb_space_md"])
    draw_text(draw, (tx, y + inner + dp(4)),
              fmt(STRINGS["inspector_meta_fmt"], CATS[index], drawable), f_meta,
              colour("cb_slate"))
    for i, comp in enumerate(comps):
        draw_text(draw, (tx, y + inner + dp(34) + i * dp(20)), comp, f_mono,
                  colour("cb_ink"))
    # Actions sit under the card, right-aligned, as in activity_inspector.xml.
    by = y + row_h + dp(DIMENS["cb_space_md"])
    f_launch = font("sans", DIMENS["cb_text_body"], bold=True)
    launch_w = max(dp(150), text_width(draw, STRINGS["inspector_launch"], f_launch)
                   + 2 * dp(DIMENS["cb_space_lg"]))
    f_export = font("sans", DIMENS["cb_text_body"], bold=True)
    export_w = text_width(draw, STRINGS["inspector_export"], f_export) + 2 * dp(DIMENS["cb_space_lg"])
    button(img, W - g - launch_w, by, STRINGS["inspector_launch"], "cta",
           min_w_dp=150)
    button(img, W - g - launch_w - dp(DIMENS["cb_space_md"]) - export_w, by,
           STRINGS["inspector_export"], "ghost")
    note = ("MOCKUP - inspector frame from activity_inspector.xml + strings.xml + "
            "appfilter.xml; mark, category and components are the shipped ones")
    return img, note


def suite_frame() -> tuple[Image.Image, str]:
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    top = header(img, STRINGS["suite_kicker"], STRINGS["suite_title"])
    g = dp(DIMENS["cb_gutter_side"])
    y = top + dp(10)
    f_name = font("sans", DIMENS["cb_text_body"], bold=True)
    f_state = font("sans", DIMENS["cb_text_data"])
    f_code = font("sans", DIMENS["cb_text_data"])
    states = [False, True, False, True, False, True]  # example install states
    names = SUITE["suite_hub_names"]
    codes = SUITE["suite_hub_codes"]
    for index, name in enumerate(names):
        row_h = dp(70)
        if index == len(names) - 1:
            focus_ring(img, [g, y, W - g, y + row_h])
            inner = dp(DIMENS["cb_focus_inset"])
        else:
            card(img, [g, y, W - g, y + row_h])
            inner = dp(DIMENS["cb_focus_inset"])
        draw_text(draw, (g + inner + dp(8), y + inner), name, f_name, colour("cb_ink"))
        state = (fmt(STRINGS["suite_installed_fmt"], "2.0.0")
                 if states[index % len(states)]
                 else STRINGS["suite_not_installed"])
        draw_text(draw, (g + inner + dp(8), y + inner + dp(22)), state, f_state,
                  colour("cb_slate"))
        code = codes[index] if index < len(codes) else ""
        draw_text(draw, (g + inner + dp(8), y + inner + dp(38)),
                  fmt(STRINGS["suite_code_fmt"], code) if code else STRINGS["suite_no_code"],
                  f_code, colour("cb_signal_cyan"))
        y += row_h + dp(6)
    note = ("MOCKUP - suite frame from activity_suite.xml + strings.xml + generated "
            "suite_hub.xml; example: install states; codes are the registry's")
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
    "Latestrelease/version.json",
    "app/src/main/assets/appfilter.xml",
    "app/src/main/assets/manifest/wallpapers.json",
    "app/src/main/res/layout/*.xml",
    "app/src/main/res/values/*.xml",
    "tools/fonts/*.ttf",
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
