#!/usr/bin/env python3
"""Build docs/wallpapers-collection.jpg: every shipped wall, in manifest
order, grouped by series, from the thumbs the manifest points at.

The per-series contact sheets are written by each generator; the AI series
(Earth, Wild) have no generator, so this sheet is the one place the whole
collection is visible at once. Run after any wallpaper change, before
`tools/prepare_release.py`. Deterministic: same manifest + thumbs, same JPEG.

    python3 tools/build_wallpaper_collection_sheet.py
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "Wallpapers" / "manifest.json"
THUMBS = ROOT / "Wallpapers" / "thumbs"
OUT = ROOT / "docs" / "wallpapers-collection.jpg"

TILE_W, TILE_H, GAP, COLS = 240, 135, 10, 6
BG, INK, DIM, CYAN = (11, 13, 18), (236, 240, 245), (120, 130, 145), (0, 212, 255)


def font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    for name in (["DejaVuSansMono-Bold.ttf"] if bold else ["DejaVuSansMono.ttf"]):
        for base in ("/usr/share/fonts/truetype/dejavu", str(ROOT / "tools" / "fonts")):
            p = Path(base) / name
            if p.exists():
                return ImageFont.truetype(str(p), size)
    return ImageFont.load_default()


def main() -> None:
    manifest = json.loads(MANIFEST.read_text())
    walls = manifest["wallpapers"]
    groups: dict[str, list[dict]] = {}
    for w in walls:
        groups.setdefault(w["series"], []).append(w)

    f_head, f_cap, f_title = font(15, True), font(12), font(22, True)
    pad = 24
    width = pad * 2 + COLS * TILE_W + (COLS - 1) * GAP
    height = pad + 44
    for items in groups.values():
        rows = -(-len(items) // COLS)
        height += 30 + rows * (TILE_H + 22 + GAP)
    height += pad

    img = Image.new("RGB", (width, height), BG)
    d = ImageDraw.Draw(img)
    d.text((pad, pad), f"CORE BUILDS · WALLPAPERS · {len(walls)} walls · manifest v{manifest.get('version', '?')}",
           font=f_title, fill=CYAN)
    y = pad + 44
    # "New" is the newest stamp, as in the app's New chip: an older `since`
    # marks a re-render's cache name (52 Shockwave, 2.3.3), not a new wall.
    newest = max((w["since"] for w in walls if w.get("since")),
                 key=lambda v: tuple(int(p) for p in v.split(".")), default="")
    for series, items in groups.items():
        new = sum(1 for w in items if newest and w.get("since") == newest)
        label = series.upper() + f"  ({len(items)})" + (f"  · NEW IN {newest}" if new == len(items) else
                                                       (f"  · {new} NEW" if new else ""))
        d.text((pad, y), label, font=f_head, fill=INK)
        y += 30
        for i, w in enumerate(items):
            x = pad + (i % COLS) * (TILE_W + GAP)
            ty = y + (i // COLS) * (TILE_H + 22 + GAP)
            thumb = THUMBS / Path(w["thumb"]).name
            if thumb.exists():
                t = Image.open(thumb).convert("RGB").resize((TILE_W, TILE_H), Image.LANCZOS)
                img.paste(t, (x, ty))
            else:
                d.rectangle([x, ty, x + TILE_W, ty + TILE_H], outline=DIM)
            d.text((x, ty + TILE_H + 4), w['name'][:34], font=f_cap, fill=DIM)
        y += (-(-len(items) // COLS)) * (TILE_H + 22 + GAP)

    OUT.parent.mkdir(exist_ok=True)
    img.save(OUT, quality=82, optimize=True, progressive=True)
    print(f"wrote {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB, {len(walls)} walls)")


if __name__ == "__main__":
    main()
