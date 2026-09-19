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
    python tools/build_app_ui_mockups.py --check    # fail on drift from docs/

Determinism: Pillow is pinned in tools/requirements.txt and the only fonts
loaded are the committed ones, so --check compares bytes across machines.
"""
from __future__ import annotations

import hashlib
import io
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
    """Android's String.format, for the %1$s / %2$d this repo's strings use."""
    out = template
    for index, value in enumerate(args, start=1):
        out = out.replace(f"%{index}$s", str(value)).replace(f"%{index}$d", str(value))
    return out


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
        _FONT_CACHE[key] = ImageFont.truetype(str(path), px)
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


def draw_text(draw, xy, s, f, fill, anchor="la"):
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


def search_field(img: Image.Image, x: int, y: int, w_dp: float) -> None:
    draw = ImageDraw.Draw(img, "RGBA")
    w, h = dp(w_dp), dp(DIMENS["cb_target_min"])
    rrect(draw, [x, y, x + w, y + h], dp(DIMENS["cb_radius_field"]),
          fill=(0x14, 0x20, 0x2B), outline=(0x00, 0xD4, 0xFF, 0x33), width=dp(1))
    f = font("sans", DIMENS["cb_text_label"])
    draw_text(draw, (x + dp(DIMENS["cb_space_md"]), y + h / 2),
              STRINGS["search_hint"], f, colour("cb_slate"), anchor="lm")


def paste_art(img: Image.Image, path: Path, box, radius: int = 0) -> None:
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
    f = font("mono", 12)
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
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    g = dp(DIMENS["cb_gutter_side"])
    y = dp(DIMENS["cb_gutter_top"])

    # Header left: kicker, title, count.
    kicker_chip(img, g, y, STRINGS["kicker"])
    f_display = font("sans", DIMENS["cb_text_display"], bold=True)
    draw_text(draw, (g, y + dp(26)), STRINGS["app_name"], f_display, colour("cb_ink"))
    f_data = font("mono", DIMENS["cb_text_data"])
    draw_text(draw, (g, y + dp(72)),
              fmt(STRINGS["icon_count_fmt"], len(PACK), VERSION["versionName"]),
              f_data, colour("cb_slate"))

    # Header right column: apply, wallpapers, settings/about.
    rx = W - g - dp(260)
    bw, bh = button(img, rx, y, fmt(STRINGS["cta_apply_to_fmt"], "Projectivy"), "cta",
                    min_w_dp=260)
    f_sub = font("sans", DIMENS["cb_text_data"])
    sub = fmt(STRINGS["cta_sub_apply_fmt"], "Projectivy")
    for i, line in enumerate(wrap(draw, sub, f_sub, dp(260))[:2]):
        draw_text(draw, (W - g, y + bh + dp(6) + i * dp(18)), line, f_sub,
                  colour("cb_slate"), anchor="ra")
    wy = y + bh + dp(48)
    button(img, rx, wy, STRINGS["wp_entry"], "ghost", min_w_dp=260)
    draw_text(draw, (W - g, wy + bh + dp(6)),
              fmt(STRINGS["wp_entry_sub_fmt"], 84), f_sub, colour("cb_slate"),
              anchor="ra")
    sy = wy + bh + dp(40)
    button(img, W - g - dp(150) - dp(8) - dp(100), sy, STRINGS["settings_label"],
           "ghost", min_w_dp=100, size_sp=DIMENS["cb_text_label"])
    button(img, W - g - dp(100), sy, STRINGS["about_label"], "ghost", min_w_dp=100,
           size_sp=DIMENS["cb_text_label"])

    top = sy + bh + dp(DIMENS["cb_space_md"])

    # Update bar: only when asked for; the manifest's highlights are real.
    if with_update_bar:
        bar_h = dp(96)
        rrect(draw, [g, top, W - g, top + bar_h], dp(DIMENS["cb_radius_card"]),
              fill=(0x10, 0x1A, 0x26), outline=colour("cb_signal_cyan"), width=dp(1))
        f_label = font("sans", DIMENS["cb_text_label"], bold=True)
        draw_text(draw, (g + dp(DIMENS["cb_space_md"]), top + dp(14)),
                  fmt(STRINGS["update_available_fmt"], VERSION["versionName"],
                      VERSION["iconCount"]), f_label, colour("cb_ink"))
        f_bullet = font("mono", DIMENS["cb_text_data"])
        bullet_w = W - g - dp(220) - 3 * dp(DIMENS["cb_space_md"]) - g
        row = 0
        for highlight in VERSION["highlights"][:2]:
            for line in wrap(draw, f"•  {highlight}", f_bullet, bullet_w)[:2]:
                draw_text(draw, (g + dp(DIMENS["cb_space_md"]), top + dp(40) + row * dp(20)),
                          line, f_bullet, colour("cb_slate"))
                row += 1
        button(img, W - g - dp(220) - dp(DIMENS["cb_space_md"]),
               top + (bar_h - dp(48)) // 2,
               fmt(STRINGS["update_download"], VERSION["versionName"]), "cta",
               focused=True, min_w_dp=220)
        top += bar_h + dp(DIMENS["cb_space_md"])

    # Filter row: chips (weight 1) + search (400dp), one row.
    cy = top + dp(DIMENS["cb_space_lg"])
    search_left = W - g - 400 * SCALE
    x = g
    probe = ImageDraw.Draw(img, "RGBA")
    f_chip = font("sans", DIMENS["cb_text_label"], bold=True)
    for index, (_key, label, count) in enumerate(category_counts()):
        label_text = fmt(STRINGS["chip_count_fmt"], label, count)
        chip_w = text_width(probe, label_text, f_chip) + 2 * dp(DIMENS["cb_space_md"])
        if x + chip_w > search_left - dp(DIMENS["cb_space_md"]):
            break  # the row scrolls; a mockup shows what fits, like the device
        x += chip_pill(img, x, cy, label_text, active=index == 0,
                       focused=index == 0) + dp(DIMENS["cb_space_sm"])
    search_field(img, search_left, cy, 400)

    # Grid: six columns of labelled cards, real art, one focused tile.
    gy = cy + dp(DIMENS["cb_target_min"]) + dp(DIMENS["cb_space_md"])
    spans = 6
    gap = 0  # bg_card's focus inset is the gutter; tiles touch by design
    tile_w = (W - 2 * g) // spans
    tile_h = dp(168)
    f_tile = font("sans", DIMENS["cb_text_label"])
    focused_index = 2
    for index in range(spans * 3):
        if index >= len(PACK):
            break
        col, row = index % spans, index // spans
        box = [g + col * tile_w + gap, gy + row * tile_h,
               g + (col + 1) * tile_w - gap, gy + (row + 1) * tile_h - dp(2)]
        if index == focused_index:
            focus_ring(img, box)
        else:
            card(img, box)
        inset = dp(DIMENS["cb_focus_inset"]) + dp(DIMENS["cb_card_padding"])
        cx = (box[0] + box[2]) // 2
        icon_size = dp(DIMENS["cb_card_icon"])
        paste_art(img, ICONS_DIR / f"{PACK[index]}.png",
                  [cx - icon_size // 2, box[1] + inset,
                   cx + icon_size // 2, box[1] + inset + icon_size])
        name = NAMES[index]
        lines = wrap(draw, name, f_tile, tile_w - 2 * inset)[:2]
        for i, line in enumerate(lines):
            draw_text(draw, ((box[0] + box[2]) / 2, box[1] + inset + icon_size
                             + dp(DIMENS["cb_space_sm"]) + i * dp(20)),
                      line, f_tile, colour("cb_ink"), anchor="ma")
    extra = "update bar bullets from version.json; " if with_update_bar else ""
    note = ("MOCKUP - catalogue frame from activity_main.xml + strings/dimens/colors + "
            f"icon_pack.xml, real bundled art; {extra}example: launcher, focus, chips")
    return img, note


def wallpapers_frame() -> tuple[Image.Image, str]:
    import json as _json
    manifest = _json.load(open(ROOT / "app/src/main/assets/manifest/wallpapers.json"))
    walls = manifest["wallpapers"]
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    g = dp(DIMENS["cb_gutter_side"])
    y = dp(24)
    kw = kicker_chip(img, g, y, STRINGS["wp_kicker"])
    f_title = font("sans", DIMENS["cb_text_title"], bold=True)
    draw_text(draw, (g + kw + dp(DIMENS["cb_space_md"]), y - dp(4)),
              STRINGS["wp_title"], f_title, colour("cb_ink"))
    f_data = font("mono", DIMENS["cb_text_data"])
    draw_text(draw, (g, y + dp(40)), fmt(STRINGS["wp_count_fmt"], len(walls)), f_data,
              colour("cb_slate"))
    button(img, W - g - dp(240), y - dp(6), STRINGS["wp_export"], "ghost", min_w_dp=110)
    button(img, W - g - dp(120), y - dp(6), STRINGS["action_back"], "ghost", min_w_dp=110)

    series: list[str] = []
    for wall in walls:
        if wall["series"] not in series:
            series.append(wall["series"])
    cy = y + dp(76)
    x = g
    labels = [STRINGS["chip_all"]] + [
        " ".join(part.capitalize() for part in s.removeprefix("series-").split("-", 1)[1].split("-"))
        for s in series
    ]
    for index, label in enumerate(labels[:7]):
        x += chip_pill(img, x, cy, label, active=index == 0) + dp(DIMENS["cb_space_sm"])

    gy = cy + dp(DIMENS["cb_target_min"]) + dp(DIMENS["cb_space_md"])
    spans = 5
    tile_w = (W - 2 * g) // spans
    tile_h = dp(176)
    f_tile = font("sans", DIMENS["cb_text_label"])
    for index in range(spans * 3):
        if index >= len(walls):
            break
        col, row = index % spans, index // spans
        box = [g + col * tile_w, gy + row * tile_h, g + (col + 1) * tile_w,
               gy + (row + 1) * tile_h - dp(2)]
        if index == 0:
            focus_ring(img, box)
        else:
            card(img, box)
        inset = dp(DIMENS["cb_focus_inset"]) + dp(6)
        thumb = THUMBS_DIR / (walls[index]["url"].split("/")[-1].rsplit(".", 1)[0] + ".jpg")
        paste_art(img, thumb, [box[0] + inset, box[1] + inset, box[2] - inset,
                               box[1] + inset + dp(DIMENS["cb_wp_thumb"])],
                  radius=dp(6))
        title = walls[index]["name"].split("· ", 1)[-1]
        draw_text(draw, ((box[0] + box[2]) / 2, box[1] + inset + dp(DIMENS["cb_wp_thumb"])
                         + dp(10)), title, f_tile, colour("cb_ink"), anchor="ma")
    note = ("MOCKUP - wallpapers frame from activity_wallpapers.xml + strings.xml + "
            "manifest/wallpapers.json, bundled thumbs; example: focus, chip selection")
    return img, note


def settings_frame() -> tuple[Image.Image, str]:
    img = new_frame()
    draw = ImageDraw.Draw(img, "RGBA")
    order = [
        ("group", "settings_group_updates"),
        ("switch", "settings_updates_title", "settings_updates_sub", True),
        ("group", "settings_group_display"),
        ("switch", "settings_motion_title", "settings_motion_sub", False),
        ("switch", "settings_amoled_title", "settings_amoled_sub", False),
        ("group", "settings_group_storage"),
        ("row", "settings_cache_title", "settings_cache_sub", "settings_cache_clear"),
        ("group", "settings_group_launcher"),
        ("row", "settings_refresh_title", "settings_refresh_sub", None),
        ("row", "settings_appinfo_title", "settings_appinfo_sub", None),
        ("group", "settings_group_suite"),
        ("row", "settings_suite_title", "settings_suite_sub", None),
        ("group", "settings_group_help"),
        ("row", "settings_audit_title", "settings_audit_sub", None),
        ("row", "settings_faq_title", "settings_faq_sub", None),
    ]
    top = header(img, STRINGS["settings_kicker"], STRINGS["settings_title"])
    g = dp(DIMENS["cb_gutter_side"])
    y = top + dp(6)
    # One viewport of a scrolling list, scrolled to the LAUNCHER group: the row
    # this frame exists to show is the refresh row and its focus ring.
    while order and order[0][1] != "settings_group_launcher":
        order.pop(0)
    f_group = font("mono", DIMENS["cb_text_kicker"], bold=True)
    f_title = font("sans", DIMENS["cb_text_body"], bold=True)
    f_sub = font("sans", DIMENS["cb_text_data"])
    focused_row = "settings_refresh_title"
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
    note = ("MOCKUP - settings frame from activity_settings.xml + strings.xml, scrolled "
            "to LAUNCHER; example: focus on the refresh row, switch positions")
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
        state = (fmt(STRINGS["suite_installed_fmt"], "2.0.0") if states[index]
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
    ("app-ui-faq.png", faq_frame),
    ("app-ui-auditor.png", lambda: auditor_frames()[0]),
    ("app-ui-auditor-qr.png", lambda: auditor_frames()[1]),
    ("app-ui-inspector.png", inspector_frame),
    ("app-ui-suite.png", suite_frame),
]


def render(name: str) -> bytes:
    for target, build in FRAMES:
        if target == name:
            img, note = build()
            buf = io.BytesIO()
            caption(img, note).convert("RGB").save(buf, format="PNG", optimize=True)
            return buf.getvalue()
    raise KeyError(name)


def main(argv: list[str]) -> int:
    check = "--check" in argv
    drift = []
    for name, _ in FRAMES:
        blob = render(name)
        path = DOCS / name
        if check:
            if not path.exists():
                drift.append(f"{name} missing")
            elif hashlib.sha256(path.read_bytes()).digest() != hashlib.sha256(blob).digest():
                drift.append(name)
        else:
            path.write_bytes(blob)
            print(f"wrote {path.relative_to(ROOT)} ({len(blob) // 1024} KB)")
    if check and drift:
        print("mockup drift:", ", ".join(drift))
        print("Run: python tools/build_app_ui_mockups.py")
        return 1
    if check:
        print(f"mockups ok - {len(FRAMES)} frames match docs/")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
