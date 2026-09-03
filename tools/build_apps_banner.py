#!/usr/bin/env python3
"""
Generates docs/apps-banner.png — a repo-level banner showing all four
Core Builds apps side by side.

Layout: dark ground, four columns, each with a glyph + app name + one-liner.
Uses the same hex/diamond mark defs as build_branding.py.
"""
from pathlib import Path
import cairosvg

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "apps-banner.png"

BG = "#0d1117"
TEXT_PRIMARY = "#e6edf3"
TEXT_ACCENT = "#00d4ff"
TEXT_MUTED = "#8b949e"
DIVIDER = "#21262d"

APPS = [
    {
        "name": "Icon Pack",
        "tagline": "921 icons + 70 wallpapers",
        "code": "5270601",
        "color": "#00d4ff",
        "glyph": "iconpack",
    },
    {
        "name": "Core Line",
        "tagline": "Sports and channel ticker",
        "code": "7375676",
        "color": "#f0883e",
        "glyph": "coreline",
    },
    {
        "name": "Core Shift",
        "tagline": "Live wallpaper browser",
        "code": "8829421",
        "color": "#a371f7",
        "glyph": "coreshift",
    },
    {
        "name": "Core Doctor",
        "tagline": "Streaming diagnostics",
        "code": "8664938",
        "color": "#3fb950",
        "glyph": "coredoctor",
    },
]

W, H = 1280, 400
COL_W = W // 4
GLYPH_Y = 100
GLYPH_R = 36
NAME_Y = 190
TAG_Y = 218
CODE_Y = 252


def _hex_points(cx, cy, r):
    """Point-up hexagon vertices."""
    import math
    pts = []
    for i in range(6):
        angle = math.radians(60 * i - 90)
        pts.append(f"{cx + r * math.cos(angle):.1f},{cy + r * math.sin(angle):.1f}")
    return " ".join(pts)


def _glyph_iconpack(cx, cy, color):
    r = 32
    hex_pts = _hex_points(cx, cy, r)
    return (
        f'<polygon points="{hex_pts}" fill="none" stroke="{color}" '
        f'stroke-width="3" stroke-linejoin="round"/>'
        f'<rect x="{cx-8}" y="{cy-8}" width="16" height="16" rx="2" '
        f'fill="{color}" opacity="0.8" transform="rotate(45 {cx} {cy})"/>'
    )


def _glyph_coreline(cx, cy, color):
    y_top = cy - 18
    bars = ""
    for i, w in enumerate([40, 52, 34, 46]):
        yy = y_top + i * 12
        bars += (
            f'<rect x="{cx - w//2}" y="{yy}" width="{w}" height="6" '
            f'rx="3" fill="{color}" opacity="{0.9 - i*0.15}"/>'
        )
    return bars


def _glyph_coreshift(cx, cy, color):
    r = 28
    return (
        f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{color}" '
        f'stroke-width="3"/>'
        f'<path d="M{cx-10} {cy-6} L{cx+10} {cy-6} L{cx+10} {cy+10} '
        f'L{cx} {cy+4} L{cx-10} {cy+10} Z" fill="{color}" opacity="0.7"/>'
        f'<circle cx="{cx}" cy="{cy-2}" r="6" fill="{color}" opacity="0.9"/>'
    )


def _glyph_coredoctor(cx, cy, color):
    return (
        f'<circle cx="{cx}" cy="{cy}" r="28" fill="none" stroke="{color}" '
        f'stroke-width="3"/>'
        f'<rect x="{cx-3}" y="{cy-16}" width="6" height="32" rx="3" '
        f'fill="{color}"/>'
        f'<rect x="{cx-16}" y="{cy-3}" width="32" height="6" rx="3" '
        f'fill="{color}"/>'
    )


GLYPH_FNS = {
    "iconpack": _glyph_iconpack,
    "coreline": _glyph_coreline,
    "coreshift": _glyph_coreshift,
    "coredoctor": _glyph_coredoctor,
}


def build_svg():
    parts = [f'<rect width="{W}" height="{H}" fill="{BG}"/>']

    # top accent gradient bar
    top_stops = " ".join(
        f'<stop offset="{i * 33}%" stop-color="{app["color"]}"/>'
        for i, app in enumerate(APPS)
    )
    parts.append(
        f'<defs><linearGradient id="topGrad" x1="0" y1="0" x2="1" y2="0">'
        f'{top_stops}</linearGradient></defs>'
    )
    parts.append(f'<rect x="0" y="0" width="{W}" height="3" fill="url(#topGrad)"/>')

    # header
    parts.append(
        f'<text x="{W//2}" y="46" text-anchor="middle" '
        f'fill="{TEXT_PRIMARY}" font-family="Georgia,serif" font-size="26" '
        f'font-weight="bold">Core Builds Apps</text>'
    )
    parts.append(
        f'<text x="{W//2}" y="68" text-anchor="middle" '
        f'fill="{TEXT_MUTED}" font-family="ui-monospace,monospace" '
        f'font-size="11">four apps · same brand · same living-room bar</text>'
    )

    for i, app in enumerate(APPS):
        cx = COL_W * i + COL_W // 2

        # vertical divider between columns
        if i > 0:
            dx = COL_W * i
            parts.append(
                f'<line x1="{dx}" y1="82" x2="{dx}" y2="{H-30}" '
                f'stroke="{DIVIDER}" stroke-width="1"/>'
            )

        # glyph background disc
        parts.append(
            f'<circle cx="{cx}" cy="{GLYPH_Y}" r="42" '
            f'fill="{app["color"]}" opacity="0.08"/>'
        )

        # glyph
        gfn = GLYPH_FNS[app["glyph"]]
        parts.append(gfn(cx, GLYPH_Y, app["color"]))

        # app name
        parts.append(
            f'<text x="{cx}" y="{NAME_Y}" text-anchor="middle" '
            f'fill="{TEXT_PRIMARY}" font-family="Georgia,serif" '
            f'font-size="17" font-weight="bold">{app["name"]}</text>'
        )

        # tagline
        parts.append(
            f'<text x="{cx}" y="{TAG_Y}" text-anchor="middle" '
            f'fill="{TEXT_MUTED}" font-family="ui-monospace,monospace" '
            f'font-size="10">{app["tagline"]}</text>'
        )

        # downloader code chip
        chip_w = 80
        chip_h = 22
        chip_x = cx - chip_w // 2
        chip_y = CODE_Y - chip_h // 2 - 2
        parts.append(
            f'<rect x="{chip_x}" y="{chip_y}" width="{chip_w}" '
            f'height="{chip_h}" rx="11" fill="{app["color"]}" opacity="0.12"/>'
        )
        parts.append(
            f'<text x="{cx}" y="{CODE_Y + 2}" text-anchor="middle" '
            f'fill="{app["color"]}" font-family="ui-monospace,monospace" '
            f'font-size="11">{app["code"]}</text>'
        )

        # platform badge
        parts.append(
            f'<text x="{cx}" y="{H - 40}" text-anchor="middle" '
            f'fill="{TEXT_MUTED}" font-family="ui-monospace,monospace" '
            f'font-size="9" opacity="0.6">Android</text>'
        )

    # bottom line (reuses topGrad)
    parts.append(
        f'<rect x="0" y="{H-3}" width="{W}" height="3" fill="url(#topGrad)"/>'
    )

    body = "\n".join(parts)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
        f'viewBox="0 0 {W} {H}">{body}</svg>'
    )


def main():
    svg_text = build_svg()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(
        bytestring=svg_text.encode(),
        write_to=str(OUT),
        output_width=W,
        output_height=H,
        background_color=None,
    )
    print(f"✓ docs/apps-banner.png ({W}x{H})")


if __name__ == "__main__":
    main()
