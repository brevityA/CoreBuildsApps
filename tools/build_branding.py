#!/usr/bin/env python3
"""
Generates the pack's own branding assets from the Core Builds mark:
  mipmap ic_launcher (legacy + adaptive background/foreground + adaptive xml)
  drawable cb_banner  (320x180 Leanback TV banner, required for ATV home rows)
Geometry follows the Brand Guide mark: point-up hexagon, centred diamond,
glow as a local signal. The launcher icon gets a richer scene than the bare
mark (depth gradient disc, edge keyline, heavier glowing linework) so the app
reads as a finished object on dark living-room launchers instead of a thin
wireframe floating on flat black.
"""
from pathlib import Path
from svg_renderer import svg2png
from typeface import FONTS, measure as type_measure, wordmark_spans

FONT_MONO = FONTS / "DejaVuSansMono.ttf"

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

MARK_DEFS = '''
  <defs>
    <linearGradient id="hexGrad" x1="50%" y1="0%" x2="50%" y2="100%">
      <stop offset="0%" stop-color="#00e5ff"/>
      <stop offset="100%" stop-color="#4facfe"/>
    </linearGradient>
    <linearGradient id="diamGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#4facfe"/>
      <stop offset="50%" stop-color="#8a4890"/>
      <stop offset="100%" stop-color="#c03a20"/>
    </linearGradient>
    <filter id="hexGlow">
      <feGaussianBlur stdDeviation="10" result="blur"/>
      <feComposite in="SourceGraphic" in2="blur" operator="over"/>
    </filter>
    <filter id="softGlow"><feGaussianBlur stdDeviation="24"/></filter>
    <filter id="diaGlow"><feGaussianBlur stdDeviation="12"/></filter>
    <radialGradient id="discBg" cx="50%" cy="42%" r="70%">
      <stop offset="0%" stop-color="#1b2432"/>
      <stop offset="55%" stop-color="#0d1117"/>
      <stop offset="100%" stop-color="#04070f"/>
    </radialGradient>
    <radialGradient id="fieldBg" cx="50%" cy="45%" r="75%">
      <stop offset="0%" stop-color="#1b2432"/>
      <stop offset="60%" stop-color="#0d1117"/>
      <stop offset="100%" stop-color="#04070f"/>
    </radialGradient>
  </defs>'''

HEX = "256,41 442,149 442,363 256,471 70,363 70,149"
DIA = "256,166 346,256 256,346 166,256"


def mark(scale=1.0, dx=0, dy=0, disc=True):
    d = f'<circle cx="256" cy="256" r="248" fill="#0d1117"/>' if disc else ""
    return f'''{MARK_DEFS}
  <g transform="translate({dx},{dy}) scale({scale}) translate({(1 - 1) * 0},0)">
  {d}
  <polygon points="{HEX}" fill="#00e5ff" opacity="0.04" filter="url(#softGlow)"/>
  <polygon points="{DIA}" fill="#8a4890" opacity="0.08" filter="url(#softGlow)"/>
  <polygon points="{HEX}" fill="none" stroke="#00e5ff" stroke-width="30"
    stroke-linejoin="round" opacity="0.3" filter="url(#hexGlow)"/>
  <polygon points="{HEX}" fill="none" stroke="url(#hexGrad)" stroke-width="22"
    stroke-linejoin="round"/>
  <polygon points="{DIA}" fill="url(#diamGrad)" opacity="0.95"/>
  </g>'''


def launcher_mark(stroke=30):
    """The mark, tuned for launcher sizes: heavier glowing linework and a lit
    diamond. The banner keeps the lighter mark() so its lockup is unchanged."""
    return f'''
  <polygon points="{HEX}" fill="#00e5ff" opacity="0.05" filter="url(#softGlow)"/>
  <polygon points="{DIA}" fill="#8a4890" opacity="0.10" filter="url(#softGlow)"/>
  <polygon points="{HEX}" fill="none" stroke="#00e5ff" stroke-width="{stroke + 12}"
    stroke-linejoin="round" opacity="0.3" filter="url(#hexGlow)"/>
  <polygon points="{HEX}" fill="none" stroke="url(#hexGrad)" stroke-width="{stroke}"
    stroke-linejoin="round"/>
  <polygon points="{DIA}" fill="url(#diamGrad)" opacity="0.5" filter="url(#diaGlow)"/>
  <polygon points="{DIA}" fill="url(#diamGrad)"/>'''


def launcher_legacy_body():
    """Full badge: depth disc + edge keyline so the icon keeps a visible rim on
    near-black launchers, with the lit mark centred."""
    return f'''{MARK_DEFS}
  <circle cx="256" cy="256" r="252" fill="url(#discBg)"/>
  <circle cx="256" cy="256" r="242" fill="none" stroke="#4facfe"
    stroke-opacity="0.30" stroke-width="2.5"/>
  {launcher_mark()}'''


def launcher_background_body():
    """Adaptive background layer: the same depth field plus a whisper of the
    mark's glow, so masked icons never sit on flat black."""
    return f'''{MARK_DEFS}
  <rect width="512" height="512" fill="url(#fieldBg)"/>
  <polygon points="{HEX}" fill="#00e5ff" opacity="0.05" filter="url(#softGlow)"/>
  <polygon points="{DIA}" fill="#8a4890" opacity="0.08" filter="url(#softGlow)"/>'''


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
    written = []

    # launcher icon, legacy densities
    icon = svg(512, 512, launcher_legacy_body())
    for folder, size in [("mipmap-xhdpi", 160), ("mipmap-xxhdpi", 240)]:
        p = RES / folder / "ic_launcher.png"
        png(icon, p, size, size)
        written.append(f"{folder}/ic_launcher.png ({size}px)")

    # Adaptive foreground: the mark fills the 66/108 safe zone (0.611), the
    # largest it can be while surviving every OEM mask. 0.5 left it visibly
    # undersized inside circle masks.
    _S = 66.0 / 108.0
    _off = 512 * (1 - _S) / 2
    fg = svg(512, 512, MARK_DEFS +
             f'<g transform="translate({_off:.1f},{_off:.1f}) '
             f'scale({_S:.4f})">{launcher_mark()}</g>')
    bg = svg(512, 512, launcher_background_body())
    for folder, size in [("mipmap-xhdpi", 216), ("mipmap-xxhdpi", 324)]:
        p = RES / folder / "ic_launcher_foreground.png"
        png(fg, p, size, size)
        written.append(f"{folder}/ic_launcher_foreground.png ({size}px)")
        p = RES / folder / "ic_launcher_background.png"
        png(bg, p, size, size)
        written.append(f"{folder}/ic_launcher_background.png ({size}px)")

    # Adaptive wiring: gradient field behind, lit mark in front. Written by the
    # generator so the layer drawables and the xml can never disagree.
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        p = RES / "mipmap-anydpi-v26" / name
        p.write_text(ADAPTIVE_XML)
        written.append(f"mipmap-anydpi-v26/{name}")

    # Leanback banner 320x180 — night chrome, mark left, wordmark right.
    #
    # Text is outlined to paths rather than <text>. It used to name
    # "Georgia,serif" and "ui-monospace", so what shipped depended on whichever
    # fonts the build host happened to have, and Georgia is not licensed for
    # redistribution. All 926 app banners already outline Outfit through
    # typeface.py; the pack's own banner was the last asset that did not.
    #
    # The strapline also overran the canvas: 27 mono characters from x=164
    # ended at 326.6 on a 320 box, so "Android TV" shipped with the V clipped.
    # The assert keeps the lockup inside the 5% overscan margin (TV-OV) instead
    # of trusting the numbers to stay correct.
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
        f'<g transform="translate(14,22) scale(0.265)">{mark(disc=False)}</g>'
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
