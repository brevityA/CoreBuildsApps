#!/usr/bin/env python3
"""
Generates the pack's own branding assets from the Core Builds mark:
  mipmap ic_launcher (legacy + adaptive foreground)
  drawable cb_banner  (320x180 Leanback TV banner, required for ATV home rows)
Geometry follows Assets/core_icon.svg exactly (Brand Guide §02).
"""
from pathlib import Path
import cairosvg

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


def svg(w, h, body, bg=None):
    b = f'<rect width="{w}" height="{h}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}">{b}{body}</svg>')


def png(svg_text, out, w, h):
    out.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring=svg_text.encode(), write_to=str(out),
                     output_width=w, output_height=h, background_color=None)


def main():
    written = []

    # launcher icon, legacy densities
    icon = svg(512, 512, mark())
    for folder, size in [("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144)]:
        p = RES / folder / "ic_launcher.png"
        png(icon, p, size, size)
        written.append(f"{folder}/ic_launcher.png ({size}px)")

    # Adaptive foreground: the mark fills the 66/108 safe zone (0.611), the
    # largest it can be while surviving every OEM mask. 0.5 left it visibly
    # undersized inside circle masks.
    _S = 66.0 / 108.0
    _off = 512 * (1 - _S) / 2
    fg = svg(512, 512, f'<g transform="translate({_off:.1f},{_off:.1f}) '
                       f'scale({_S:.4f})">{mark(disc=False)}</g>')
    for folder, size in [("mipmap-xhdpi", 216), ("mipmap-xxhdpi", 324)]:
        p = RES / folder / "ic_launcher_foreground.png"
        png(fg, p, size, size)
        written.append(f"{folder}/ic_launcher_foreground.png ({size}px)")

    # Leanback banner 320x180 — night chrome, mark left, serif wordmark right
    banner = svg(
        320, 180,
        f'<g transform="translate(14,22) scale(0.265)">{mark(disc=False)}</g>'
        f'<text x="164" y="82" fill="#e6edf3" font-family="Georgia,serif" '
        f'font-size="23">Core Builds</text>'
        f'<text x="164" y="106" fill="#00d4ff" font-family="Georgia,serif" '
        f'font-size="19">Icon Pack</text>'
        f'<text x="164" y="130" fill="#8b949e" '
        f'font-family="ui-monospace,monospace" font-size="10">'
        f'for Projectivy \u00b7 Android TV</text>',
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
