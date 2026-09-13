#!/usr/bin/env python3
"""
Generates the pack's own branding assets from the pack-icon mark:
  mipmap ic_launcher (legacy + adaptive background/foreground + adaptive xml)
  drawable cb_banner  (320x180 Leanback TV banner, required for ATV home rows)

v1.8.17 redraws the launcher icon AS A PACK ICON. The old lit hexagon scene
(gradients, glow filters, depth disc) belonged to the brand-mark era; on a
home row full of rounded monoline tiles it read as a different product. The
mark is now the universal icon-pack symbol — a rounded app tile holding a
2x2 grid of marks — drawn to the catalog's own Core monoline grammar:
32px primary / 26.2px detail, rounded caps and joins, one accent (#00D4FF),
no fills, no effects, transparent ground.

Because the mark obeys the same contract as the 931 catalog icons, this
script asserts it against icon_style.core_monoline_errors before writing a
single asset, and the legacy PNG gets the same raster presence pass
(tools/presence.py) as every square icon. The app's own icon is held to the
standard the pack promises everyone else. The adaptive background keeps the
night card so masked icons never sit on flat black.
"""
from pathlib import Path
from svg_renderer import svg2png
from icon_style import core_monoline_errors
from presence import apply_presence_file
from typeface import FONTS, measure as type_measure, wordmark_spans

FONT_MONO = FONTS / "DejaVuSansMono.ttf"

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

ACCENT = "#00D4FF"


def _s(w):
    return (f'fill="none" stroke="{ACCENT}" stroke-width="{w}" '
            f'stroke-linecap="round" stroke-linejoin="round"')


# The pack icon on the 512 grid. Frame carries the primary weight; the four
# marks inside carry the subordinate detail weight — the same hierarchy the
# catalog uses for containers and their contents. One of the four is a circle
# so the grid reads as "icons" (a set of different marks) rather than
# "windows" (four identical panes).
PACK_ICON = (
    f'<rect x="64" y="64" width="384" height="384" rx="96" {_s("32.0")}/>'
    f'<circle cx="168" cy="168" r="44" {_s("26.2")}/>'
    f'<rect x="300" y="124" width="88" height="88" rx="24" {_s("26.2")}/>'
    f'<rect x="124" y="300" width="88" height="88" rx="24" {_s("26.2")}/>'
    f'<rect x="300" y="300" width="88" height="88" rx="24" {_s("26.2")}/>'
)

# Adaptive background: the night card. Kept deliberately quiet — a radial
# field, no mark echo — so OEM masks crop the field, never the foreground.
FIELD_DEFS = '''
  <defs>
    <radialGradient id="fieldBg" cx="50%" cy="45%" r="75%">
      <stop offset="0%" stop-color="#1b2432"/>
      <stop offset="60%" stop-color="#0d1117"/>
      <stop offset="100%" stop-color="#04070f"/>
    </radialGradient>
  </defs>'''


def launcher_background_body():
    return f'''{FIELD_DEFS}
  <rect width="512" height="512" fill="url(#fieldBg)"/>'''


ADAPTIVE_XML = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
'''


def svg(w, h, body, bg=None):
    b = f'<rect width="{w}" height="{h}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}">{b}{body}</svg>')


def png(svg_text, out, w, h):
    out.parent.mkdir(parents=True, exist_ok=True)
    svg2png(bytestring=svg_text.encode(), write_to=str(out),
                     output_width=w, output_height=h, background_color=None)


def main():
    # The mark must pass the same contract as every catalog icon before it is
    # allowed to represent the pack. Fail loudly, by name, before any asset
    # is written (Brand Guide §08).
    errors = core_monoline_errors(PACK_ICON, ACCENT)
    assert not errors, f"launcher mark violates Core monoline: {errors}"

    written = []

    # launcher icon, legacy densities — transparent ground, presence pass.
    icon = svg(512, 512, PACK_ICON)
    for folder, size in [("mipmap-xhdpi", 160), ("mipmap-xxhdpi", 240)]:
        p = RES / folder / "ic_launcher.png"
        png(icon, p, size, size)
        apply_presence_file(p)
        written.append(f"{folder}/ic_launcher.png ({size}px)")

    # Adaptive foreground: the mark fills the 66/108 safe zone (0.611), the
    # largest it can be while surviving every OEM mask. 0.5 left it visibly
    # undersized inside circle masks.
    _S = 66.0 / 108.0
    _off = 512 * (1 - _S) / 2
    fg = svg(512, 512,
             f'<g transform="translate({_off:.1f},{_off:.1f}) '
             f'scale({_S:.4f})">{PACK_ICON}</g>')
    bg = svg(512, 512, launcher_background_body())
    for folder, size in [("mipmap-xhdpi", 216), ("mipmap-xxhdpi", 324)]:
        p = RES / folder / "ic_launcher_foreground.png"
        png(fg, p, size, size)
        written.append(f"{folder}/ic_launcher_foreground.png ({size}px)")
        p = RES / folder / "ic_launcher_background.png"
        png(bg, p, size, size)
        written.append(f"{folder}/ic_launcher_background.png ({size}px)")

    # Adaptive wiring: night card behind, pack icon in front. Written by the
    # generator so the layer drawables and the xml can never disagree.
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        p = RES / "mipmap-anydpi-v26" / name
        p.write_text(ADAPTIVE_XML)
        written.append(f"mipmap-anydpi-v26/{name}")

    # Leanback banner 320x180 — night chrome, mark left, wordmark right.
    #
    # Text is outlined to paths rather than <text>. All 931 app banners
    # outline Outfit through typeface.py; the pack's own banner does too.
    #
    # The assert keeps the lockup inside the 5% overscan margin (TV-OV)
    # instead of trusting the numbers to stay correct.
    TV_SAFE = 16                      # 5% of 320
    TEXT_X = 164                      # clear of the mark, which ends near 150
    limit = 320 - TV_SAFE

    wm, _ = wordmark_spans(["Core Builds"], 23, TEXT_X, [82], "#e6edf3")
    sub, _ = wordmark_spans(["Icon Pack"], 19, TEXT_X, [106], "#00d4ff")

    strap, strap_size = "Projectivy \u00b7 Android TV", 9
    strap_w = type_measure(strap, strap_size, FONT_MONO)
    assert TEXT_X + strap_w <= limit, (
        f"banner strapline overruns the safe area: ends at "
        f"{TEXT_X + strap_w:.1f}, limit {limit}")
    tag, _ = wordmark_spans([strap], strap_size, TEXT_X, [130], "#8b949e",
                            font_path=FONT_MONO)

    banner = svg(
        320, 180,
        f'<g transform="translate(14,22) scale(0.265)">{PACK_ICON}</g>'
        f'{wm}{sub}{tag}',
        bg="#0d1117")
    p = RES / "drawable-nodpi" / "cb_banner.png"
    png(banner, p, 640, 360)
    written.append("drawable-nodpi/cb_banner.png (640x360)")

    # same banner for the README header
    docs = ROOT / "docs" / "banner.png"
    png(banner, docs, 640, 360)
    written.append("docs/banner.png (640x360)")

    for w in written:
        print("\u2713 " + w)
    print(f"\nBranding complete \u2014 {len(written)} files written.")


if __name__ == "__main__":
    main()
