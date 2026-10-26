#!/usr/bin/env python3
"""Review the corrected icons in the Classic pack, not as an isolated logo set.

Run after the icon generators. Reads catalog + generated Classic PNGs only.
The unmodified Core Builds, Emby, TiviMate and Syncler neighbours are intentional:
a logo can be recognisable and still be wrong for this pack's visual identity.

Use --glyphs for docs/glyphs-identity-preview.png: the two glyph apps beside
unchanged main-pack art and neighbours, including actual-size TV banners.
Use --requests for docs/request-icons-preview.png: SPORT FM TV and TVLok at
128/64px and their actual-size banners, beside unchanged pack neighbours.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from drawable_art import art_path, read_aliases  # noqa: E402

from PIL import Image, ImageDraw, ImageFont
from icon_style import CARD, LIGHT_INK

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/icon-fidelity-preview.png"
FONTS = ROOT / "tools/fonts"
RES = ROOT / "app/src/main/res/drawable-nodpi"
ALIASES = read_aliases(ROOT / "app/src/main/res/values")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--glyphs", action="store_true",
                       help="review the glyphs apps' self-icons beside the unchanged pack")
    modes.add_argument("--requests", action="store_true",
                       help="review SPORT FM TV and TVLok with unchanged pack neighbours")
    args = parser.parse_args()
    data = json.loads((ROOT / "tools/catalog.json").read_text())
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())
    by_id = {i["drawable"]: i for i in icons}
    tivimate = next(i["drawable"] for i in icons if i["name"].casefold() == "tivimate")
    row = ["corebuilds", "emby", "nobuffr", tivimate, "syncler", "netflix"]
    revised = ["spotify", "kodi", "jellyfin", "stremio", "plex", "crunchyroid",
               "twitch", "nordvpn", "mubi", "protonvpn", "deezer", "iplayer"]
    width, height = 1280, 1110 if args.requests else 1020 if args.glyphs else 1510
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
            image(art_path(RES, key, ALIASES), x+30, y+16, (124, 124))
            text(x+14, y+146, icon["name"], 18)

    if args.requests:
        text(48, 30, "CORE BUILDS / REQUESTS #382 + #352", 14, "#56C8F0", True)
        text(48, 65, "Two requests. One pack.", 46)
        text(48, 130, "Brand cues, redrawn in the same rounded monoline as the rest of the pack.", 23, "#9AAABD")
        text(48, 174, f"v{data['meta']['version']} candidate  /  {len(icons)} icons  /  squares at 128 + 64 px  /  banners at 320 x 180 px",
             15, "#9AAABD", True)
        draw.line((48, 212, 1232, 212), fill="#27303D", width=1)
        for n, key in enumerate(("sport_fm_tv", "tvlok")):
            x, y = 48 + n * 600, 242
            tile(x, y, 584, 488)
            text(x+24, y+22, by_id[key]["name"], 27)
            image(art_path(RES, key, ALIASES), x+136, y+76, (128, 128))
            image(art_path(RES, key, ALIASES), x+352, y+108, (64, 64))
            text(x+169, y+215, "128 px", 14, "#9AAABD", True)
            text(x+357, y+215, "64 px", 14, "#9AAABD", True)
            image(art_path(RES, key + "_banner", ALIASES), x+132, y+252, (320, 180))
            note = ("New icon / exact scanner component" if n == 0
                    else "Corrected art / TV alias verified from the official APK")
            text(x+24, y+450, note, 17, "#9AAABD")
        text(48, 773, "Alongside unchanged icons", 28)
        square_row(["corebuilds", "emby", tivimate, "syncler", "netflix", "coreeq"], 825)
        text(48, 1052, "Original Core geometry. Source colours documented. Both names use the standard banner renderer.",
             18, "#9AAABD")
        out = ROOT / "docs/request-icons-preview.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        canvas.save(out, optimize=True)
        print(f"Wrote {out.relative_to(ROOT)} ({width}x{height}) — requests, tile sizes and actual-size banners")
        return 0

    if args.glyphs:
        text(48, 30, "CORE BUILDS / GLYPHS IDENTITY FIX", 14, "#A366FF", True)
        text(48, 65, "Glyphs, not the main pack.", 46)
        text(48, 130, "The existing violet mark, now preserved when the icon pack is applied.", 23, "#9AAABD")
        text(48, 174, "Square glyphs at 64 px  /  TV banners at their shipped 320 x 180 px", 15, "#9AAABD", True)
        draw.line((48, 212, 1232, 212), fill="#27303D", width=1)
        for n, key in enumerate(("corebuilds", "corebuilds_glyphs", "corebuilds_glyphs_pack")):
            x, y = 48 + n * 400, 242
            tile(x, y, 384, 400)
            image(art_path(RES, key, ALIASES), x+160, y+24, (64, 64))
            text(x+22, y+108, by_id[key]["name"], 23)
            image(art_path(RES, f"{key}_banner", ALIASES), x+32, y+149, (320, 180))
            note = "Unchanged main-pack art" if n == 0 else "Violet mark / own banner label"
            text(x+22, y+359, note, 15, "#9AAABD")
        text(48, 682, "Alongside unchanged icons", 28)
        square_row(["corebuilds", "emby", tivimate, "syncler", "netflix", "coreeq"], 731)
        text(48, 955, "Catalog mappings fixed for the standalone Glyphs app and the Glyphs Pack companion, including Play.",
             17, "#9AAABD")
        out = ROOT / "docs/glyphs-identity-preview.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        canvas.save(out, optimize=True)
        print(f"Wrote {out.relative_to(ROOT)} ({width}x{height}) — self-icons and actual-size banners")
        return 0

    text(48, 30, "CORE BUILDS / CLASSIC IDENTITY", 14, "#56C8F0", True)
    text(48, 65, "Core Builds first.", 48)
    text(48, 130, "Rounded monoline. One accent. One banner system.", 23, "#9AAABD")
    text(48, 171, f"v{data['meta']['version']} candidate  /  {len(icons)} icons  /  verified NoBuffr mapping retained", 15, "#9AAABD", True)
    draw.line((48, 212, 1232, 212), fill="#27303D", width=1)

    text(48, 241, "NoBuffr, beside the existing icons", 28)
    text(48, 280, "Shown beside the established Core Builds, Emby, TiviMate and Syncler artwork.", 18, "#9AAABD")
    square_row(row, 325)

    text(48, 552, "The same layout at TV-card size", 28)
    text(48, 591, "Monoline glyph + stroke-letter name + category, no rail. No private wordmark-only exception.", 17, "#9AAABD")
    for n, key in enumerate(("emby", "nobuffr", tivimate)):
        x, y = 48 + n * 400, 634
        tile(x, y, 384, 266, key == "nobuffr")
        image(art_path(RES, f"{key}_banner", ALIASES), x+32, y+14, (320, 180))  # actual shipping size
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
