#!/usr/bin/env python3
"""Compact, reproducible visual receipt for the catalog's sourced logo pass.

Run after all three pack generators. Reads committed catalog/resources only;
no screenshots, stock photos, network requests, or manually edited previews.
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from icon_style import CARD, LIGHT_INK, display_accent

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/icon-fidelity-preview.png"
FONTS = ROOT / "tools/fonts"


def main() -> int:
    data = json.loads((ROOT / "tools/catalog.json").read_text())
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())
    by_id = {i["drawable"]: i for i in icons}
    groups = {name: next(i for i in icons if i["glyph"] == name)
              for name in data["artwork"] if name != "nobuffr_mark"}
    reviewed = sorted(list(groups.values()) + [by_id["iplayer"]], key=lambda i: i["name"].lower())
    fallback = sum(display_accent(i["color"]) != i["color"].upper() for i in icons)
    columns = 6
    rows = (len(reviewed) + 1 + columns - 1) // columns
    width, grid_y, cell_h = 1280, 500, 188
    height = grid_y + rows * cell_h + 100
    canvas = Image.new("RGB", (width, height), CARD)
    draw = ImageDraw.Draw(canvas)

    def font(size, mono=False):
        return ImageFont.truetype(str(FONTS / ("DejaVuSansMono.ttf" if mono else "Outfit-Bold.ttf")), size)

    def text(x, y, value, size=18, colour=LIGHT_INK, mono=False):
        draw.text((x, y), value, fill=colour, font=font(size, mono))

    def image(path, x, y, size):
        icon = Image.open(path).convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
        canvas.paste(icon, (x, y), icon)

    def tile(x, y, w, h):
        draw.rounded_rectangle((x, y, x+w, y+h), radius=16, fill="#10151D", outline="#27303D")

    text(48, 32, "CORE BUILDS / ICON FIDELITY", 14, "#56C8F0", True)
    text(48, 64, "True marks. One colour policy.", 46)
    count = len(icons)
    components = sum(len(i["components"]) for i in icons)
    text(48, 127, f"v{data['meta']['version']} candidate  /  {count} icons  /  {components:,} catalog components", 19, "#9AAABD")
    draw.line((48, 172, 1232, 172), fill="#27303D", width=1)

    text(48, 216, "NoBuffr added", 36)
    text(48, 268, "Artwork and launcher read from your APK.", 19)
    text(48, 299, "Phone + Android TV / static manifest verified", 16, "#9AAABD")
    text(48, 336, "com.nobuffr.app/", 15, "#56C8F0", True)
    text(48, 360, "tv.tivitime.compose.app.AppActivity", 15, "#56C8F0", True)
    packs = [("Classic", "app"), ("Pop", "pop"), ("Pixel Neon", "pixel-neon/app")]
    for n, (label, module) in enumerate(packs):
        x = 616 + n * 204
        tile(x, 212, 188, 192)
        image(ROOT / module / "src/main/res/drawable-nodpi/nobuffr.png", x+20, 222, 148)
        text(x+20, 375, label, 17, "#9AAABD")

    text(48, 440, "Source-checked silhouettes", 26)
    text(762, 449, "16 existing brands + iPlayer reconstruction", 17, "#9AAABD")
    for n, icon in enumerate(reviewed):
        x, y = 48 + n % columns * 200, grid_y + n // columns * cell_h
        tile(x, y, 184, 174)
        image(ROOT / "app/src/main/res/drawable-nodpi" / f"{icon['drawable']}.png", x+38, y+12, 108)
        text(x+14, y+127, icon["name"], 18)
        colour = display_accent(icon["color"])
        label = f"{icon['color']} > light" if colour != icon["color"] else colour
        text(x+14, y+151, label, 11, "#9AAABD", True)

    n = len(reviewed)
    x, y = 48 + n % columns * 200, grid_y + n // columns * cell_h
    tile(x, y, 184, 174)
    text(x+18, y+20, str(fallback), 50, "#56C8F0")
    text(x+18, y+81, "dark accents", 19)
    text(x+18, y+109, "now readable", 19)
    text(x+18, y+145, "3:1 on #0D1117", 11, "#9AAABD", True)
    text(48, height-65, "Transparent Classic assets. Sourced marks use flat-colour adaptations; other packs retain their own style.", 17, "#9AAABD")
    text(48, height-37, "Targeted pass, not 925 verified vendor logos. Source URLs, hashes and remaining requests are documented.", 15, "#9AAABD")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUT, optimize=True)
    print(f"Wrote {OUT.relative_to(ROOT)} ({width}×{height}) from the catalog and generated assets")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
