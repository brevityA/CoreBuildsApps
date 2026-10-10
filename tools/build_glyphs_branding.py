#!/usr/bin/env python3
"""Branding for the glyphs-only app (`tv.corebuilds.glyphs`).

The `glyphs` product flavor of `:app` is the whole icon pack with the square
appfilter, installed as its own app. Without this script it inherits the pack's
launcher icon and TV banner, so in a launcher's drawer and on the TV row it
looks like a second copy of the pack, told apart only by a label.

This writes the flavor's own launcher and banner into `app/src/glyphs/res/`:

  mipmap ic_launcher            legacy PNG, 160 and 240 px
  mipmap ic_launcher_foreground adaptive foreground, 216 and 324 px
  mipmap ic_launcher_background adaptive background, 216 and 324 px
  drawable cb_banner            320x180 Leanback banner, 640x360 PNG

The mark is the pack's own 2x2 tile, but the four marks are different: a ring,
a square, a play triangle and a hexagon, all in Core monoline geometry (32 /
26.2 px strokes, rounded caps and joins, one accent). The accent is Violet
(`#A366FF`, a palette slot in docs/BRAND-GUIDE.md), so the two apps differ at
a glance on any row. The adaptive xml is not written here: the flavor reuses
the `mipmap-anydpi-v26` wiring generated for the pack, which already names
these three resources.

The mark passes the same `core_monoline_errors` contract as every catalog
icon before anything is written, and the legacy PNG gets the same raster
presence pass as the pack's own launcher.

Run: python tools/build_glyphs_branding.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from build_branding import png, svg  # noqa: E402  (shared renderer)
from icon_style import core_monoline_errors  # noqa: E402
from presence import apply_presence_file  # noqa: E402
from typeface import measure as type_measure, wordmark_spans, FONTS  # noqa: E402

FONT_MONO = FONTS / "DejaVuSansMono.ttf"
ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "glyphs" / "res"

ACCENT = "#A366FF"   # Violet — a palette slot; not the pack's Signal Cyan
NIGHT = "#0D1117"    # Brand Guide Night card

_STROKE = 'fill="none" stroke="{c}" stroke-width="26.2" stroke-linecap="round" stroke-linejoin="round"'


def glyph_mark(c: str) -> str:
    s = _STROKE.format(c=c)
    return (
        # outer rounded tile, the pack's frame grammar
        f'<rect x="72" y="72" width="368" height="368" rx="92" fill="none" '
        f'stroke="{c}" stroke-width="32" stroke-linecap="round" stroke-linejoin="round"/>'
        # top-left: ring
        f'<circle cx="190" cy="190" r="40" {s}/>'
        # top-right: square
        f'<rect x="282" y="150" width="80" height="80" rx="16" {s}/>'
        # bottom-left: play triangle
        f'<path d="M 172 300 L 208 322 L 172 344 Z" {s}/>'
        # bottom-right: hexagon
        f'<path d="M 322 282 L 354 300 L 354 344 L 322 362 L 290 344 L 290 300 Z" {s}/>'
    )


MARK = glyph_mark(ACCENT)


def main() -> None:
    errors = core_monoline_errors(MARK, ACCENT)
    assert not errors, f"glyphs-app mark violates Core monoline: {errors}"

    written = []

    # Legacy launcher: the mark on the night card, with the presence pass.
    legacy = svg(512, 512, MARK, bg=NIGHT)
    for folder, size in [("mipmap-xhdpi", 160), ("mipmap-xxhdpi", 240)]:
        p = RES / folder / "ic_launcher.png"
        png(legacy, p, size, size)
        apply_presence_file(p)
        written.append(f"{folder}/ic_launcher.png ({size}px)")

    # Adaptive: the mark inside the 66/108 safe zone, over a solid night card.
    _S = 66.0 / 108.0
    _off = 512 * (1 - _S) / 2
    fg = svg(512, 512, f'<g transform="translate({_off:.1f},{_off:.1f}) '
                       f'scale({_S:.4f})">{MARK}</g>')
    bg = svg(512, 512, f'<rect width="512" height="512" fill="{NIGHT}"/>')
    for folder, size in [("mipmap-xhdpi", 216), ("mipmap-xxhdpi", 324)]:
        png(fg, RES / folder / "ic_launcher_foreground.png", size, size)
        written.append(f"{folder}/ic_launcher_foreground.png ({size}px)")
        png(bg, RES / folder / "ic_launcher_background.png", size, size)
        written.append(f"{folder}/ic_launcher_background.png ({size}px)")

    # Leanback banner, same lockup grammar as the pack's, glyphs wording.
    TV_SAFE = 16
    TEXT_X = 164
    limit = 320 - TV_SAFE
    wm, _ = wordmark_spans(["Core Builds"], 23, TEXT_X, [82], "#e6edf3")
    sub, _ = wordmark_spans(["Glyphs"], 19, TEXT_X, [106], ACCENT)
    strap, strap_size = "Square icons \u00b7 Android TV", 9
    strap_w = type_measure(strap, strap_size, FONT_MONO)
    assert TEXT_X + strap_w <= limit, (
        f"banner strapline overruns the safe area: ends at {TEXT_X + strap_w:.1f}, "
        f"limit {limit}")
    tag, _ = wordmark_spans([strap], strap_size, TEXT_X, [130], "#8b949e",
                            font_path=FONT_MONO)
    banner = svg(320, 180,
                 f'<g transform="translate(14,22) scale(0.265)">{MARK}</g>{wm}{sub}{tag}',
                 bg=NIGHT)
    png(banner, RES / "drawable-nodpi" / "cb_banner.png", 640, 360)
    written.append("drawable-nodpi/cb_banner.png (640x360)")

    for w in written:
        print("\u2713 " + w)
    print(f"\nGlyphs-app branding complete \u2014 {len(written)} files written.")


if __name__ == "__main__":
    main()
