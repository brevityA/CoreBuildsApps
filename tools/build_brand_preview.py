#!/usr/bin/env python3
"""
Renders docs/brand-preview.png — the pack's own branding shown at real sizes
and in context: the Leanback banner as it appears in an Android TV home row,
the launcher icon at true pixel sizes, and the adaptive-icon mask shapes.

Pure composition of assets that already exist; generates no new geometry.
"""
import base64
from pathlib import Path

import cairosvg

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
OUT = ROOT / "docs" / "brand-preview.png"


def data_uri(p: Path) -> str:
    return "data:image/png;base64," + base64.b64encode(p.read_bytes()).decode()


def main():
    banner = data_uri(RES / "drawable-nodpi" / "cb_banner.png")
    icon = data_uri(RES / "mipmap-xxhdpi" / "ic_launcher.png")
    fg = data_uri(RES / "mipmap-xxhdpi" / "ic_launcher_foreground.png")

    # a few pack icons to dress the mock home row
    row = [data_uri(RES / "drawable-nodpi" / f"{n}.png")
           for n in ["stremio", "kodi", "jellyfin", "plex", "youtube"]]

    W, H = 1200, 940
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
         f'viewBox="0 0 {W} {H}">',
         f'<rect width="{W}" height="{H}" fill="#0d1117"/>',
         '<defs>',
         '  <clipPath id="circle"><circle cx="60" cy="60" r="60"/></clipPath>',
         '  <clipPath id="squircle">'
         '<rect x="0" y="0" width="120" height="120" rx="34"/></clipPath>',
         '  <clipPath id="rounded">'
         '<rect x="0" y="0" width="120" height="120" rx="16"/></clipPath>',
         '</defs>']

    def title(x, y, text):
        s.append(f'<text x="{x}" y="{y}" fill="#e6edf3" '
                 f'font-family="Georgia,serif" font-size="21">{text}</text>')

    def sub(x, y, text):
        s.append(f'<text x="{x}" y="{y}" fill="#8b949e" '
                 f'font-family="ui-monospace,monospace" font-size="11.5">'
                 f'{text}</text>')

    def kicker(x, y, text):
        s.append(f'<text x="{x}" y="{y}" fill="#00d4ff" '
                 f'font-family="ui-monospace,monospace" font-size="10.5" '
                 f'letter-spacing="1.6">{text}</text>')

    # ---------------- header
    kicker(48, 52, "CORE BUILDS · ICON PACK")
    s.append('<text x="48" y="88" fill="#e6edf3" font-family="Georgia,serif" '
             'font-size="30">Branding assets</text>')
    sub(48, 112, "the pack's own identity — banner, launcher icon, adaptive masks")

    # ---------------- 1. the banner at 1x
    title(48, 168, "Leanback banner · 320×180")
    sub(48, 190, "drawable-nodpi/cb_banner.png — required for the ATV home row")
    s.append(f'<image href="{banner}" x="48" y="206" width="320" height="180"/>')
    s.append('<rect x="48" y="206" width="320" height="180" rx="4" fill="none" '
             'stroke="#ffffff" stroke-opacity=".10"/>')

    # ---------------- 2. launcher icon at true sizes
    title(430, 168, "Launcher icon · true pixel sizes")
    sub(430, 190, "mipmap ic_launcher.png — 144 / 96 / 48 / 24 px")
    x = 430
    for size in [144, 96, 48, 24]:
        y = 206 + (144 - size)
        s.append(f'<image href="{icon}" x="{x}" y="{y}" '
                 f'width="{size}" height="{size}"/>')
        s.append(f'<text x="{x + size / 2}" y="{372}" fill="#8b949e" '
                 f'font-family="ui-monospace,monospace" font-size="10" '
                 f'text-anchor="middle">{size}</text>')
        x += size + 26
    # §02 minimum-size note
    s.append(f'<text x="430" y="392" fill="#fbbf24" '
             f'font-family="ui-monospace,monospace" font-size="10.5">'
             f'24 px is the brand-guide floor — facet detail merges below it'
             f'</text>')

    # ---------------- 3. adaptive icon masks
    ty = 460
    title(48, ty, "Adaptive icon · mask shapes")
    sub(48, ty + 22, "mipmap-anydpi-v26 — foreground on #0d1117, OEM masks applied")
    labels = [("circle", "circle"), ("squircle", "squircle"),
              ("rounded", "rounded square")]
    x = 48
    for clip, label in labels:
        # A hairline ring shows where the mask actually cuts; without it the
        # #0d1117 background is invisible against the #0d1117 page.
        s.append(f'<g transform="translate({x},{ty + 42})">'
                 f'<g clip-path="url(#{clip})">'
                 f'<rect width="120" height="120" fill="#0d1117"/>'
                 f'<rect width="120" height="120" fill="#ffffff" '
                 f'fill-opacity=".05"/>'
                 f'<image href="{fg}" x="0" y="0" width="120" height="120"/>'
                 f'</g>'
                 f'<g clip-path="url(#{clip})">'
                 f'<rect width="120" height="120" fill="none" '
                 f'stroke="#ffffff" stroke-opacity=".22" stroke-width="2"/>'
                 f'</g></g>')
        s.append(f'<text x="{x + 60}" y="{ty + 182}" fill="#8b949e" '
                 f'font-family="ui-monospace,monospace" font-size="10" '
                 f'text-anchor="middle">{label}</text>')
        x += 156

    # ---------------- 4. home row in context
    hy = 700
    title(48, hy, "In context · Android TV home row")
    sub(48, hy + 22, "banner card beside pack icons on launcher cards")

    s.append(f'<rect x="48" y="{hy + 42}" width="1104" height="168" rx="14" '
             f'fill="#04070f"/>')
    # the banner as the focused card
    s.append(f'<g transform="translate(72,{hy + 62})">'
             f'<rect x="-4" y="-4" width="240" height="143" rx="12" '
             f'fill="none" stroke="#00d4ff" stroke-width="2.5"/>'
             f'<image href="{banner}" x="0" y="0" width="232" height="135"/>'
             f'</g>')
    s.append(f'<text x="72" y="{hy + 222}" fill="#e6edf3" '
             f'font-family="-apple-system,sans-serif" font-size="12">'
             f'Core Builds Icon Pack</text>')

    # pack icons on cards, as Projectivy would draw them
    x = 344
    for uri in row:
        s.append(f'<rect x="{x}" y="{hy + 62}" width="135" height="135" rx="14" '
                 f'fill="#151923" stroke="#ffffff" stroke-opacity=".06"/>')
        s.append(f'<image href="{uri}" x="{x + 33}" y="{hy + 95}" '
                 f'width="69" height="69"/>')
        x += 152

    s.append(f'<text x="1152" y="{hy + 240}" fill="#8b949e" text-anchor="end" '
             f'font-family="ui-monospace,monospace" font-size="10">'
             f'transparent icons · launcher supplies the card colour</text>')

    s.append('</svg>')

    OUT.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring="\n".join(s).encode(), write_to=str(OUT),
                     output_width=W, output_height=H,
                     background_color="#0d1117")
    print(f"\u2713 docs/brand-preview.png written ({W}\u00d7{H})")


if __name__ == "__main__":
    main()
