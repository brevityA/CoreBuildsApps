#!/usr/bin/env python3
"""Review the corrected icons in the Classic pack, not as an isolated logo set.

Run after the icon generators. Reads catalog + generated Classic PNGs only.
The unmodified Core Builds, Emby, TiviMate and Syncler neighbours are intentional:
a logo can be recognisable and still be wrong for this pack's visual identity.
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from icon_style import CARD, LIGHT_INK

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/icon-fidelity-preview.png"
FONTS = ROOT / "tools/fonts"
RES = ROOT / "app/src/main/res/drawable-nodpi"


def main() -> int:
    data = json.loads((ROOT / "tools/catalog.json").read_text())
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())
    by_id = {i["drawable"]: i for i in icons}
    tivimate = next(i["drawable"] for i in icons if i["name"].casefold() == "tivimate")
    row = ["corebuilds", "emby", "nobuffr", tivimate, "syncler", "netflix"]
    revised = ["spotify", "kodi", "jellyfin", "stremio", "plex", "crunchyroid",
               "twitch", "nordvpn", "mubi", "protonvpn", "deezer", "iplayer"]
    width, height = 1280, 1510
    canvas = Image.new("RGB", (width, height), CARD)
    draw = ImageDraw.Draw(canvas)

    def font(size, mono=False):
        return ImageFont.truetype(str(FONTS / ("DejaVuSansMono.ttf" if mono else "Outfit-Bold.ttf")), size)

    def text(x, y, value, size=18, colour=LIGHT_INK, mono=False):
        draw.text((x, y), value, fill=colour, font=font(size, mono))

    def image(path, x, y, size):
        icon = Image.open(path).convert("RGBA").resize(size, Image.Resampling.LANCZOS)
        canvas.paste(icon, (x, y), icon)

    def tile(x, y, w, h, focus=False):
        draw.rounded_rectangle((x, y, x+w, y+h), radius=16, fill="#10151D",
                               outline="#56C8F0" if focus else "#27303D", width=2 if focus else 1)

    def square_row(keys, y):
        for n, key in enumerate(keys):
            x = 48 + n * 200
            icon = by_id[key]
            tile(x, y, 184, 188, key == "nobuffr")
            image(RES / f"{key}.png", x+30, y+16, (124, 124))
            text(x+14, y+146, icon["name"], 18)

    text(48, 30, "CORE BUILDS / CLASSIC IDENTITY", 14, "#56C8F0", True)
    text(48, 65, "Core Builds first.", 48)
    text(48, 130, "Rounded monoline. One accent. One banner system.", 23, "#9AAABD")
    text(48, 171, f"v{data['meta']['version']} candidate  /  {len(icons)} icons  /  verified NoBuffr mapping retained", 15, "#9AAABD", True)
    draw.line((48, 212, 1232, 212), fill="#27303D", width=1)

    text(48, 241, "NoBuffr, beside the existing icons", 28)
    text(48, 280, "Shown beside the established Core Builds, Emby, TiviMate and Syncler artwork.", 18, "#9AAABD")
    square_row(row, 325)

    text(48, 552, "The same layout at TV-card size", 28)
    text(48, 591, "Monoline glyph + Outfit name + category + cyan/violet rail. No private wordmark-only exception.", 17, "#9AAABD")
    for n, key in enumerate(("emby", "nobuffr", tivimate)):
        x, y = 48 + n * 400, 634
        tile(x, y, 384, 266, key == "nobuffr")
        image(RES / f"{key}_banner.png", x+32, y+14, (320, 180))  # actual shipping size
        text(x+22, y+209, by_id[key]["name"], 19)
        text(x+22, y+237, "320 x 180 / same renderer", 12, "#9AAABD", True)

    text(48, 943, "Brand cues, re-drawn in the pack's linework", 28)
    square_row(revised[:6], 990)
    square_row(revised[6:], 1192)

    text(48, 1424, "No pasted vendor silhouettes, fixed-white logotypes, extra containers or changed launcher mappings.", 17, "#9AAABD")
    text(48, 1457, "32px primary stroke / 26.2px + 21.8px detail / transparent canvas / original Core Builds constructions", 14, "#9AAABD")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUT, optimize=True)
    print(f"Wrote {OUT.relative_to(ROOT)} ({width}x{height}) — mixed Classic row and actual-size banners")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
