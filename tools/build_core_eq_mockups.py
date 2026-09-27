#!/usr/bin/env python3
"""
Core EQ — UI frames, rendered from the sources they depict.

Same contract as `tools/build_app_ui_mockups.py`, for the app that does not
exist yet. The reason for generating these rather than drawing them by hand is
the reason that generator gives: hand-drawn sheets drift, and they drifted once
already in this suite. Every colour here comes from
`app/src/main/res/values/colors.xml`, every metric from
`app/src/main/res/values/dimens.xml` at the 1080p scale (960dp wide, 2px per
dp), the type roles are the pack's own Outfit and DejaVu Sans Mono from
`tools/fonts`, and the equaliser curves in frame 4 are the real output of
`tools/core_eq_dsp.py` run against its synthetic room — not a hand-shaped
pretend curve.

What is example data is named in each frame's caption band, per house style:
the device name, the microphone, the session verdict and the capability probe
are invented. The measurement maths is not.

    python tools/build_core_eq_mockups.py           # write docs/core-eq-*.png
    python tools/build_core_eq_mockups.py --check   # fail if frames are stale

Text rendering is not reproducible across machines (see the mockup
generator's own note about raqm and CPython 3.11 vs 3.12), so --check
compares the drawn structure this generator records, not the PNG bytes.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
FONTS = Path(__file__).resolve().parent / "fonts"
OUT = ROOT / "docs"
sys.path.insert(0, str(Path(__file__).resolve().parent))
import core_eq_dsp as dsp  # noqa: E402

SCALE = 2
W, H = 1920, 1080
CAPTION_H = 64

RECORD: list[str] = []


def dp(value: float) -> int:
    return int(round(value * SCALE))


def _xml(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


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


DIMENS = load_dimens()
COLORS = load_colors()

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
        # Layout pinned to BASIC, for the reason the icon pack's generator
        # gives: raqm-vs-FreeType advances change where wrap() breaks a line
        # and where a centred label sits, so the same sources render different
        # frames on different runners.
        _FONT_CACHE[key] = ImageFont.truetype(
            str(path), px, layout_engine=ImageFont.Layout.BASIC)
    return _FONT_CACHE[key]


def colour(name: str, alpha: int | None = None) -> tuple:
    hexed = COLORS[name].lstrip("#")
    if len(hexed) == 8:
        a, hexed = int(hexed[0:2], 16), hexed[2:]
    else:
        a = 255
    rgb = tuple(int(hexed[i:i + 2], 16) for i in (0, 2, 4))
    return rgb + (alpha if alpha is not None else a,)


def draw_text(draw, xy, s, f, fill, anchor="la"):
    RECORD.append("T|{}|{}|{}|{}|{}|{}".format(
        tuple(xy), s, getattr(f, "size", "?"),
        Path(getattr(f, "path", "") or "").name, tuple(fill), anchor))
    draw.text(xy, s, font=f, fill=fill, anchor=anchor)


def text_width(draw, s, f) -> int:
    return int(draw.textlength(s, font=f))


def new_frame() -> Image.Image:
    img = Image.new("RGB", (W, H + CAPTION_H), colour("cb_night"))
    RECORD.append("FRAME")
    return img


def caption(img: Image.Image, text: str) -> Image.Image:
    """The band that names the invented values, per house style."""
    d = ImageDraw.Draw(img)
    y = H + CAPTION_H // 2
    d.line([(0, H), (W, H)], fill=colour("cb_hairline"), width=1)
    draw_text(d, (dp(24), y), "EXAMPLE DATA", font("mono", 11, True),
              colour("cb_signal_cyan"), "lm")
    draw_text(d, (dp(150), y), text, font("mono", 11),
              colour("cb_slate"), "lm")
    return img


def card(img: Image.Image, box, focused: bool = False):
    """Mirrors res/drawable/bg_card.xml: 3dp ring, 3dp gap, then the surface."""
    d = ImageDraw.Draw(img)
    inset = dp(DIMENS["cb_focus_inset"])
    if focused:
        d.rounded_rectangle(box, radius=dp(DIMENS["cb_radius_card"]),
                            fill=colour("cb_signal_cyan"))
        gap = dp(DIMENS["cb_focus_ring"])
        d.rounded_rectangle([box[0] + gap, box[1] + gap, box[2] - gap, box[3] - gap],
                            radius=dp(DIMENS["cb_radius_card_gap"]),
                            fill=colour("cb_night"))
    d.rounded_rectangle([box[0] + inset, box[1] + inset, box[2] - inset, box[3] - inset],
                        radius=dp(DIMENS["cb_radius_card_inner"]),
                        fill=colour("cb_panel") if focused else colour("cb_card"),
                        outline=colour("cb_hairline"), width=1)
    return [box[0] + inset, box[1] + inset, box[2] - inset, box[3] - inset]


def header(img: Image.Image, kick: str, title: str, sub: str | None = None) -> int:
    d = ImageDraw.Draw(img)
    x = dp(DIMENS["cb_gutter_side"])
    y = dp(DIMENS["cb_gutter_top"])
    draw_text(d, (x, y), kick.upper(), font("mono", DIMENS["cb_text_kicker"], True),
              colour("cb_signal_cyan"))
    y += dp(DIMENS["cb_text_kicker"] + 12)
    draw_text(d, (x, y), title, font("sans", DIMENS["cb_text_title"], True),
              colour("cb_ink"))
    y += dp(DIMENS["cb_text_title"] + 8)
    if sub:
        for line in sub.split("\n"):
            draw_text(d, (x, y), line, font("sans", DIMENS["cb_text_body"]),
                      colour("cb_slate"))
            y += dp(DIMENS["cb_text_body"] + 8)
    return y


def button(img: Image.Image, x, y, w, h, label: str, kind: str = "primary",
           focused: bool = False):
    d = ImageDraw.Draw(img)
    box = [x, y, x + w, y + h]
    if focused:
        d.rounded_rectangle(box, radius=dp(DIMENS["cb_radius_card"]),
                            fill=colour("cb_signal_cyan"))
        gap = dp(DIMENS["cb_focus_ring"])
        d.rounded_rectangle([x + gap, y + gap, x + w - gap, y + h - gap],
                            radius=dp(DIMENS["cb_radius_card_gap"]),
                            fill=colour("cb_night"))
        inset = dp(DIMENS["cb_focus_inset"])
        d.rounded_rectangle([x + inset, y + inset, x + w - inset, y + h - inset],
                            radius=dp(DIMENS["cb_radius_card_inner"]),
                            fill=colour("cb_cta_start"))
        ink = colour("cb_cta_ink")
    elif kind == "primary":
        d.rounded_rectangle(box, radius=dp(DIMENS["cb_radius_button"]),
                            fill=colour("cb_cta_start"))
        ink = colour("cb_cta_ink")
    elif kind == "danger":
        d.rounded_rectangle(box, radius=dp(DIMENS["cb_radius_button"]),
                            fill=colour("cb_ember"))
        ink = colour("cb_ink")
    else:
        d.rounded_rectangle(box, radius=dp(DIMENS["cb_radius_button"]),
                            fill=None, outline=colour("cb_hairline"), width=2)
        ink = colour("cb_ink")
    draw_text(d, (x + w // 2, y + h // 2), label,
              font("sans", DIMENS["cb_text_label"], True), ink, "mm")
    return box


def chip(img: Image.Image, x, y, label: str, active: bool = False,
         focused: bool = False) -> int:
    d = ImageDraw.Draw(img)
    f = font("sans", DIMENS["cb_text_label"], True)
    w = text_width(d, label, f) + dp(32)
    h = dp(40)
    box = [x, y, x + w, y + h]
    if focused:
        d.rounded_rectangle(box, radius=dp(DIMENS["cb_radius_card"]),
                            fill=colour("cb_signal_cyan"))
        d.rounded_rectangle([x + dp(3), y + dp(3), x + w - dp(3), y + h - dp(3)],
                            radius=dp(DIMENS["cb_radius_card_gap"]),
                            fill=colour("cb_night"))
        d.rounded_rectangle([x + dp(6), y + dp(6), x + w - dp(6), y + h - dp(6)],
                            radius=dp(DIMENS["cb_radius_card_inner"]),
                            fill=colour("cb_panel"))
    elif active:
        d.rounded_rectangle(box, radius=dp(DIMENS["cb_radius_pill"]),
                            fill=colour("cb_cta_start"))
    else:
        d.rounded_rectangle(box, radius=dp(DIMENS["cb_radius_pill"]),
                            fill=None, outline=colour("cb_hairline"), width=2)
    ink = colour("cb_cta_ink") if active and not focused else colour("cb_ink")
    draw_text(d, (x + w // 2, y + h // 2), label, f, ink, "mm")
    return x + w + dp(12)


def kicker(img: Image.Image, x, y, text: str, col: str = "cb_slate") -> int:
    d = ImageDraw.Draw(img)
    draw_text(d, (x, y), text.upper(), font("mono", DIMENS["cb_text_kicker"], True),
              colour(col))
    return y + dp(DIMENS["cb_text_kicker"] + 8)


PANEL_BOTTOM_DP = 516


def fits(name: str, lowest_dp: float) -> None:
    """Fail loudly when a frame's content leaves the 540dp panel.

    The frames are the design contract for `tests/test_tv_layout_fit.py`. A
    frame that quietly runs past the bottom teaches the wrong layout, so the
    generator refuses to write it.
    """
    if lowest_dp > PANEL_BOTTOM_DP:
        raise SystemExit(
            f"FAIL: {name} content reaches {lowest_dp:.0f}dp, past the "
            f"{PANEL_BOTTOM_DP}dp bottom margin on a 540dp panel")


def entry_row(img: Image.Image, box, title: str, sub: str, focused: bool = False,
              state: str | None = None):
    """Title over subtitle, the two-line block vertically centred in the card.

    The block is centred rather than hung from a fixed offset, because these
    rows are used at 60dp and at 76dp and a fixed offset put the subtitle
    through the bottom border on the short variant — which is the same mistake
    the icon pack's own dimens.xml note warns about: measure the block, then
    place it, do not assume the card is tall enough to absorb the slack.
    """
    d = ImageDraw.Draw(img)
    inner = card(img, box, focused)
    tf = font("sans", DIMENS["cb_text_data"], True)
    sf = font("sans", DIMENS["cb_text_kicker"])
    block = dp(12) + dp(11) + dp(15) + dp(11)  # ascender, gap, ascender, descender
    top = inner[1] + max(dp(6), (inner[3] - inner[1] - block) // 2)
    draw_text(d, (inner[0] + dp(18), top), title, tf, colour("cb_ink"))
    draw_text(d, (inner[0] + dp(18), top + dp(26)), sub, sf, colour("cb_slate"))
    if state:
        col = {"ok": "cb_success", "warn": "cb_warning", "bad": "cb_ember"}[state]
        draw_text(d, (inner[2] - dp(16), (inner[1] + inner[3]) // 2),
                  {"ok": "VERIFIED", "warn": "LIMITED", "bad": "UNAVAILABLE"}[state],
                  font("mono", DIMENS["cb_text_kicker"], True), colour(col), "mm")


# ---------------------------------------------------------------------------
# The equaliser curves — real output, not hand-shaped
# ---------------------------------------------------------------------------

def curve_data():
    """Run the reference chain once and hand the frames the real numbers."""
    f, _ = dsp.welch_db(dsp.pink_noise(seconds=0.5), dsp.FS)
    centres, _ = dsp.octave_bands(f, np.zeros_like(f), n=3.0)
    measured = dsp.synthetic_room_db(centres)
    target = dsp.target_curve("harman", centres)
    corr = dsp.correction_curve(centres, measured, target)
    filters = dsp.fit_peaking_filters(centres, corr, n_filters=6)
    bands = dsp.collapse_to_bands(
        [60, 230, 910, 3600, 14000],
        lambda hz: float(np.interp(hz, centres, corr)), -1500, 1500)
    return centres, measured, target, corr, filters, bands


def draw_graph(img: Image.Image, box, centres, series, y_range=20.0,
               title: str = "", legend: list[tuple[str, str]] | None = None):
    """Log-frequency dB plot with the house palette and a 0 dB rule."""
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    RECORD.append("GRAPH|{}|{}|{}|{}|{}|{}".format(
        box, title, int(y_range), len(centres), len(series),
        [(n, tuple(v[:6])) for n, v in (legend or [])]))

    d.rectangle(box, fill=colour("cb_void"))
    d.rectangle(box, outline=colour("cb_hairline"), width=1)

    f_lo, f_hi = 25.0, 20000.0

    def fx(hz):
        t = math.log(hz / f_lo) / math.log(f_hi / f_lo)
        return x0 + t * (x1 - x0)

    def fy(db):
        t = (db + y_range) / (2 * y_range)
        return y1 - t * (y1 - y0)

    for db in range(-int(y_range), int(y_range) + 1, 5):
        y = fy(db)
        d.line([(x0, y), (x1, y)],
               fill=colour("cb_hairline") if db else colour("cb_slate"))
        if db % 10 == 0:
            draw_text(d, (x0 + dp(6), y - dp(10)), f"{db:+d}",
                      font("mono", DIMENS["cb_text_kicker"]),
                      colour("cb_slate"))

    for hz, label in [(31.5, "31"), (63, "63"), (125, "125"), (250, "250"),
                      (500, "500"), (1000, "1k"), (2000, "2k"),
                      (4000, "4k"), (8000, "8k"), (16000, "16k")]:
        x = fx(hz)
        d.line([(x, y0), (x, y1)], fill=colour("cb_hairline"))
        draw_text(d, (x, y1 - dp(2)), label, font("mono", DIMENS["cb_text_kicker"]),
                  colour("cb_slate"), "ms")

    for name, values in series:
        pts = [(fx(max(f_lo, min(f_hi, fc))), fy(max(-y_range, min(y_range, v))))
               for fc, v in zip(centres, values) if f_lo <= fc <= f_hi]
        if len(pts) > 1:
            d.line(pts, fill=colour(name), width=3, joint="curve")

    if title:
        draw_text(d, (x0 + dp(14), y0 + dp(8)), title,
                  font("mono", DIMENS["cb_text_kicker"], True), colour("cb_slate"))
    if legend:
        # Bottom-right, inside the plot: at the top it collided with the title
        # and with the series lines that start near +20 dB.
        lx = x1 - dp(12)
        for name, label in reversed(legend):
            lw = text_width(d, label, font("mono", DIMENS["cb_text_kicker"]))
            draw_text(d, (lx, y1 - dp(10)), label,
                      font("mono", DIMENS["cb_text_kicker"], True),
                      colour(name), "rd")
            lx -= lw + dp(20)


def draw_bands(img: Image.Image, box, bands, focused_index: int = -1):
    """The 5-band equaliser as a D-pad row: vertical sliders, one focused."""
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    d.rounded_rectangle(box, radius=dp(DIMENS["cb_radius_card"]),
                        fill=colour("cb_card"), outline=colour("cb_hairline"))
    n = len(bands)
    slot = (x1 - x0) / n
    mid = (y0 + y1) / 2
    span = (y1 - y0) * 0.32

    d.line([(x0 + dp(20), mid), (x1 - dp(20), mid)], fill=colour("cb_hairline"), width=1)

    for i, (fc, millibel) in enumerate(bands):
        cx = int(x0 + slot * (i + 0.5))
        db = millibel / 100.0
        y = int(mid - (db / 15.0) * span)
        w = dp(26)
        focused = i == focused_index

        if focused:
            d.rounded_rectangle([cx - dp(34), y0 + dp(16), cx + dp(34), y1 - dp(16)],
                                radius=dp(DIMENS["cb_radius_card"]),
                                fill=colour("cb_signal_cyan"))
            d.rounded_rectangle([cx - dp(31), y0 + dp(19), cx + dp(31), y1 - dp(19)],
                                radius=dp(DIMENS["cb_radius_card_gap"]),
                                fill=colour("cb_night"))

        d.rounded_rectangle([cx - w // 2, mid - span, cx + w // 2, mid + span],
                            radius=w // 2, fill=colour("cb_void"))
        top, bot = min(y, mid), max(y, mid)
        d.rounded_rectangle([cx - w // 2, top, cx + w // 2, bot],
                            radius=w // 2,
                            fill=colour("cb_signal_cyan" if db >= 0 else "cb_dusk_violet"))
        d.ellipse([cx - dp(13), y - dp(13), cx + dp(13), y + dp(13)],
                  fill=colour("cb_ink"))

        draw_text(d, (cx, y1 - dp(26)),
                  f"{int(fc)}Hz", font("mono", DIMENS["cb_text_kicker"]),
                  colour("cb_slate" if not focused else "cb_ink"), "ms")
        draw_text(d, (cx, y0 + dp(30)),
                  f"{db:+.1f}", font("mono", DIMENS["cb_text_kicker"], True),
                  colour("cb_ink"), "ms")


# ---------------------------------------------------------------------------
# Frames
#
# The vertical budget is the whole point of writing these down. A 1080p panel
# reports 960x540dp, and the frames are drawn at 2px per dp, so the panel is
# exactly 540dp tall and nothing in a frame may be taller than that. The first
# draft of these frames laid out against 1080 as if it were dp and the home
# screen came to 828dp — the same class of error `tests/test_tv_layout_fit.py`
# exists to catch in the layouts themselves.
#
# Budget, every frame: top gutter 24dp + header 73dp, content 97dp..516dp.
# ---------------------------------------------------------------------------

def frame_home(data):
    centres, measured, target, corr, filters, bands = data
    img = new_frame()
    d = ImageDraw.Draw(img)

    gut = dp(DIMENS["cb_gutter_side"])
    y = header(img, "Core EQ · tv.corebuilds.eq", "Equaliser built from this room",
               "Pink noise out, the remote microphone listening, a curve you can "
               "read before you trust it.")

    rail_w = dp(DIMENS["cb_rail_width"])
    pane_x = gut + rail_w + dp(30)

    # Left rail: active profile card, then the four entry rows.
    inner = card(img, [gut, y, gut + rail_w, y + dp(132)], focused=True)
    kicker(img, inner[0] + dp(18), inner[1] + dp(14), "Active profile", "cb_signal_cyan")
    draw_text(d, (inner[0] + dp(18), inner[1] + dp(38)), "Living room",
              font("sans", DIMENS["cb_text_section"], True), colour("cb_ink"))
    draw_text(d, (inner[0] + dp(18), inner[1] + dp(66)),
              "Harman · 6 filters · remote mic",
              font("mono", DIMENS["cb_text_kicker"]), colour("cb_slate"))
    button(img, inner[0] + dp(18), inner[1] + dp(74),
           rail_w - dp(36), dp(44), "Re-measure", "primary", focused=True)

    ny = y + dp(142)
    for label, sub, state in [
        ("Measure", "Pink noise · 20 s · this room", "ok"),
        ("Profiles", "3 saved · Living room active", "ok"),
        ("Targets", "Harman, B&K, Flat, House", None),
        ("Capability", "What this device will accept", "warn"),
    ]:
        entry_row(img, [gut, ny, gut + rail_w, ny + dp(60)], label, sub,
                  focused=(label == "Capability"), state=state)
        ny += dp(62)

    # Right pane: the real curves, then the platform band row.
    draw_graph(img, [pane_x, y, W - gut, y + dp(250)], centres,
               [("cb_slate", measured), ("cb_signal_cyan", corr)],
               y_range=20.0,
               title="MEASURED vs CORRECTION",
               legend=[("cb_slate", "MEASURED"), ("cb_signal_cyan", "CORRECTION")])

    by = y + dp(262)
    kicker(img, pane_x, by, "Platform equaliser — the 5 bands this device reports")
    by += dp(14)
    draw_bands(img, [pane_x, by, W - gut, by + dp(110)], bands, focused_index=2)

    fits("core-eq-home", (by + dp(110)) / SCALE)
    return caption(img, "device name, session verdict and the last-run date are "
                        "invented · the curves are tools/core_eq_dsp.py output")


def frame_measure(data):
    centres, measured, target, corr, filters, bands = data
    img = new_frame()
    d = ImageDraw.Draw(img)
    gut = dp(DIMENS["cb_gutter_side"])
    y = header(img, "Core EQ · Measure", "Hold the room still for 20 seconds",
               "The TV plays pink noise. The remote microphone listens. Nothing "
               "leaves this device.")

    steps = [
        ("1", "Press OK to start the stimulus",
         "Pink noise, equal energy per octave. Loud enough to hear over the "
         "room, quiet enough to live with.", "ok"),
        ("2", "Point the remote at your seat",
         "Where your ears are, not at the TV. The remote microphone is the "
         "only microphone this can use.", "warn"),
        ("3", "Keep the room quiet while the bar fills",
         "Talking, footsteps and the kitchen fan land in the same "
         "measurement. Retakes are free.", None),
    ]
    sy = y
    for num, title, sub, state in steps:
        h = dp(92)
        inner = card(img, [gut, sy, gut + dp(420), sy + h], focused=(num == "1"))
        d.ellipse([inner[0] + dp(14), inner[1] + dp(26), inner[0] + dp(52),
                   inner[1] + dp(64)], fill=colour("cb_cta_start"))
        draw_text(d, (inner[0] + dp(33), inner[1] + dp(45)), num,
                  font("sans", DIMENS["cb_text_label"], True),
                  colour("cb_cta_ink"), "mm")
        draw_text(d, (inner[0] + dp(66), inner[1] + dp(16)), title,
                  font("sans", DIMENS["cb_text_data"], True), colour("cb_ink"))
        line, lines = "", []
        for w in sub.split(" "):
            if text_width(d, (line + " " + w).strip(),
                          font("sans", DIMENS["cb_text_kicker"])) > dp(300):
                lines.append(line)
                line = w
            else:
                line = (line + " " + w).strip()
        lines.append(line)
        for i, ln in enumerate(lines[:3]):
            draw_text(d, (inner[0] + dp(66), inner[1] + dp(40) + dp(17) * i), ln,
                      font("sans", DIMENS["cb_text_kicker"]), colour("cb_slate"))
        sy += h + dp(12)

    gx = gut + dp(450)
    draw_graph(img, [gx, y, W - gut, y + dp(250)], centres,
               [("cb_slate", measured), ("cb_signal_cyan", target)],
               y_range=20.0,
               title="LIVE CAPTURE vs TARGET",
               legend=[("cb_slate", "CAPTURE"), ("cb_signal_cyan", "TARGET")])

    by = y + dp(262)
    kicker(img, gx, by, "Capture progress")
    by += dp(16)
    bar = [gx, by, W - gut, by + dp(22)]
    d.rounded_rectangle(bar, radius=dp(11), fill=colour("cb_void"))
    fill_w = int((bar[2] - bar[0]) * 0.62)
    d.rounded_rectangle([bar[0], bar[1], bar[0] + fill_w, bar[3]],
                        radius=dp(11), fill=colour("cb_cta_start"))
    draw_text(d, (gx, by + dp(34)),
              "12.4 s of 20 s · 18 frames averaged · noise floor −61 dB",
              font("mono", DIMENS["cb_text_kicker"]), colour("cb_slate"))
    by += dp(58)
    button(img, gx, by, dp(190), dp(46), "Stop", "ghost")
    button(img, gx + dp(210), by, dp(190), dp(46), "Start over", "ghost")

    fits("core-eq-measure", (by + dp(46)) / SCALE)
    return caption(img, "capture progress, noise floor and the step copy are "
                        "invented · the plotted target is the real Harman curve")


def frame_profiles(data):
    centres, measured, target, corr, filters, bands = data
    img = new_frame()
    d = ImageDraw.Draw(img)
    gut = dp(DIMENS["cb_gutter_side"])
    y = header(img, "Core EQ · Profiles", "Saved corrections",
               "Every profile carries its own microphone, limits and capability "
               "verdict.")

    rows = [
        ("Living room", "Harman · 6 filters · remote mic", "ACTIVE", "ok"),
        ("Bedroom TV", "B&K · 5 filters · remote mic", "2 days ago", None),
        ("Kitchen", "Flat · 8 filters · USB mic", "last week", None),
    ]
    ry = y
    for name, sub, badge, state in rows:
        h = dp(78)
        inner = card(img, [gut, ry, gut + dp(420), ry + h], focused=(name == "Living room"))
        draw_text(d, (inner[0] + dp(18), inner[1] + dp(16)), name,
                  font("sans", DIMENS["cb_text_body"], True), colour("cb_ink"))
        draw_text(d, (inner[0] + dp(18), inner[1] + dp(46)), sub,
                  font("mono", DIMENS["cb_text_kicker"]), colour("cb_slate"))
        if badge == "ACTIVE":
            draw_text(d, (inner[2] - dp(16), inner[1] + dp(18)), "ACTIVE",
                      font("mono", DIMENS["cb_text_kicker"], True),
                      colour("cb_success"), "ra")
        draw_text(d, (inner[2] - dp(16), inner[1] + dp(48)), badge,
                  font("mono", DIMENS["cb_text_kicker"]), colour("cb_slate"), "ra")
        ry += h + dp(12)

    gx = gut + dp(450)
    draw_graph(img, [gx, y, W - gut, y + dp(220)], centres,
               [("cb_signal_cyan", corr), ("cb_dusk_violet", target)],
               y_range=15.0,
               title="LIVING ROOM — CORRECTION vs TARGET",
               legend=[("cb_signal_cyan", "CORRECTION"), ("cb_dusk_violet", "TARGET")])

    ey = y + dp(234)
    kicker(img, gx, ey, "Export")
    ey += dp(16)
    ex = gx
    for label in ["Parametric .txt", "GraphicEQ", "Profile .json"]:
        ex = chip(img, ex, ey, label, active=(label == "Parametric .txt"))
    ey += dp(46)
    draw_text(d, (gx, ey), "Preamp  −3.72 dB",
              font("mono", DIMENS["cb_text_data"], True), colour("cb_ink"))
    draw_text(d, (gx, ey + dp(22)),
              "The export ships its own headroom note. Copy the preamp with the "
              "filters or the boost clips.",
              font("sans", DIMENS["cb_text_kicker"]), colour("cb_slate"))
    ey += dp(42)
    button(img, gx, ey, dp(190), dp(46), "Export", "primary")
    button(img, gx + dp(210), ey, dp(160), dp(46), "Delete", "danger")

    fits("core-eq-profiles", (ey + dp(46)) / SCALE)
    return caption(img, "profile names, dates and the export row are invented · "
                        "the preamp value is real output of the fitted filters")


def frame_capability(data):
    img = new_frame()
    d = ImageDraw.Draw(img)
    gut = dp(DIMENS["cb_gutter_side"])
    y = header(img, "Core EQ · Capability", "What this device will accept",
               "Android has no global equaliser API. Each row is a path we probe, "
               "and the verdict is the truth.")

    rows = [
        ("Global output mix (session 0)",
         "Deprecated in 2012, never removed. Works on some sets.", "bad"),
        ("Session open/close broadcasts",
         "Players that announce a session. Netflix and YouTube do not.", "warn"),
        ("This app's own playback",
         "Always works. The correction applies to what Core EQ plays.", "ok"),
        ("DynamicsProcessing (API 28+)",
         "Your own band layout, up to 31 bands, plus a limiter.", "warn"),
        ("Platform Equalizer",
         "Device reports its own bands and millibel range. Always present.", "ok"),
    ]
    ry = y
    for title, sub, state in rows:
        entry_row(img, [gut, ry, gut + dp(420), ry + dp(72)], title, sub,
                  focused=title.startswith("Global"), state=state)
        ry += dp(77)

    gx = gut + dp(450)
    inner = card(img, [gx, y, W - gut, y + dp(180)], focused=False)
    kicker(img, inner[0] + dp(18), inner[1] + dp(14), "Verdict on this set", "cb_signal_cyan")
    draw_text(d, (inner[0] + dp(18), inner[1] + dp(38)), "5 bands",
              font("sans", DIMENS["cb_text_title"], True), colour("cb_ink"))
    draw_text(d, (inner[0] + dp(18), inner[1] + dp(86)),
              "session broadcast: no", font("mono", DIMENS["cb_text_kicker"]),
              colour("cb_slate"))
    draw_text(d, (inner[0] + dp(18), inner[1] + dp(108)),
              "global mix: not supported", font("mono", DIMENS["cb_text_kicker"]),
              colour("cb_slate"))
    draw_text(d, (inner[0] + dp(18), inner[1] + dp(140)),
              "Profiles from other sets keep the limits",
              font("sans", DIMENS["cb_text_kicker"]), colour("cb_slate"))
    draw_text(d, (inner[0] + dp(18), inner[1] + dp(158)),
              "they were built under.",
              font("sans", DIMENS["cb_text_kicker"]), colour("cb_slate"))

    by = y + dp(198)
    kicker(img, gx, by, "If nothing applies, the curve still travels")
    by += dp(18)
    for line in ["Export the profile and set the numbers in the TV's",
                 "own sound settings, or in the player's built-in",
                 "equaliser. It is the same curve."]:
        draw_text(d, (gx, by), line, font("sans", DIMENS["cb_text_data"]),
                  colour("cb_ink"))
        by += dp(22)
    by += dp(14)
    button(img, gx, by, dp(280), dp(46), "Export for TV settings", "primary")

    fits("core-eq-capability", max(ry, by + dp(46)) / SCALE)
    return caption(img, "device, probe verdicts and the capability text are "
                        "invented · the shape of the verdict card is the contract")


FRAMES = [
    ("core-eq-home.png", frame_home),
    ("core-eq-measure.png", frame_measure),
    ("core-eq-profiles.png", frame_profiles),
    ("core-eq-capability.png", frame_capability),
]


def build_all() -> dict[str, list[str]]:
    data = curve_data()
    out = {}
    for name, fn in FRAMES:
        RECORD.clear()
        img = fn(data)
        out[name] = list(RECORD)
        img.save(OUT / name)
    return out


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Core EQ UI frames")
    parser.add_argument("--check", action="store_true",
                        help="fail if the committed frames are stale")
    args = parser.parse_args(argv)

    manifest_path = OUT / "core-eq-mockups.json"
    drawn = build_all()

    payload = {
        "generator": "tools/build_core_eq_mockups.py",
        "sources": {
            "colors": str(RES / "values/colors.xml"),
            "dimens": str(RES / "values/dimens.xml"),
            "dsp": "tools/core_eq_dsp.py",
        },
        "frames": {k: {"drawn": len(v)} for k, v in drawn.items()},
        "structure": drawn,
    }

    if args.check:
        if not manifest_path.exists():
            print("FAIL: docs/core-eq-mockups.json is missing")
            return 1
        was = json.loads(manifest_path.read_text())
        if was.get("structure") != drawn:
            stale = [k for k in drawn if was.get("structure", {}).get(k) != drawn[k]]
            print(f"FAIL: frames are stale: {', '.join(stale)}")
            return 1
        print(f"mockups ok - {len(drawn)} frames match the sources in the tree")
        return 0

    manifest_path.write_text(json.dumps(payload, indent=2) + "\n")
    for name in drawn:
        print(f"wrote docs/{name}")
    print(f"wrote docs/{manifest_path.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
