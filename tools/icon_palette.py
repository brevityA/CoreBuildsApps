#!/usr/bin/env python3
"""
Core Builds Classic — the fallback colour palette.

689 icons have no published brand colour (catalog `color_source` = the pack
palette). Until 1.9.4 they shared a palette that had grown by hand: five
colours carried 456 of them - one blue alone was 229 - so a row of long-tail
apps read as one blue smear, and two-letter tiles collided into
byte-identical twins.

This owns that palette and its assignment:

  * PALETTE is 18 vivid accents spread round the hue wheel, each clearing
    4.5:1 on the Night card (well past the pack's 3:1 floor, so
    display_accent never has to lift one). Signal Cyan, Build Blue and Light
    Ink are the brand guide's own tokens.
  * assign() walks the icons in catalogue order (name, case-folded - the
    grid's order) and hands each palette-sourced icon the next colour seven
    steps round the wheel, so neighbours differ by ~140 degrees of hue.
    A colour that would repeat either neighbour's, or render a picture some
    other app already has, is skipped for the next free one.
  * Brand groups share one accent (AGENTS.md); a group is coloured once.
  * Icons with a sourced brand colour are never touched.

    python tools/icon_palette.py           # rewrite palette colours in the catalog
    python tools/icon_palette.py --check   # fail if the catalog drifted (CI)
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from glyphs import family_glyph_for  # noqa: E402
from icon_style import contrast, display_accent  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"

PACK_PALETTE = "core-builds palette (no published brand colour found)"

# (name, hex), in hue order. Every entry clears MIN_READABLE on the card.
PALETTE = [
    ("Signal Red", "#FF4D4D"),
    ("Ember Orange", "#FF7A2E"),
    ("Amber", "#FFB020"),
    ("Volt Yellow", "#FFE14D"),
    ("Lime", "#B6F23A"),
    ("Signal Green", "#53FC18"),
    ("Jade", "#34EB7A"),
    ("Emerald", "#1FD19A"),
    ("Teal", "#19D3C5"),
    ("Signal Cyan", "#00D4FF"),
    ("Build Blue", "#4FACFE"),
    ("Azure", "#3D8BFF"),
    ("Indigo", "#7C74FF"),
    ("Violet", "#A366FF"),
    ("Orchid", "#C95CFF"),
    ("Magenta", "#F04DE0"),
    ("Hot Pink", "#FF5CA8"),
    ("Light Ink", "#E6EDF3"),
]
HEXES = [h for _, h in PALETTE]
STRIDE = 7            # coprime with 18: every colour is used, neighbours far apart
MIN_READABLE = 4.5


def shape(icon: dict) -> str:
    """What renders: a monogram's letter is replaced by its mark."""
    glyph = icon["glyph"]
    if icon.get("mark") and family_glyph_for(glyph) is not None:
        return glyph.rpartition("_")[0] + "_*"
    return glyph


def render_key(icon: dict, colour: str) -> tuple:
    return (shape(icon), icon.get("mark") or "", icon.get("mark_style") or "",
            display_accent(colour, monochrome=icon.get("color_note") == "monochrome"))


def is_palette(icon: dict) -> bool:
    return icon.get("color_source") == PACK_PALETTE


def assign(icons: list[dict]) -> dict[int, str]:
    """{id(icon): colour} for every palette-sourced icon, deterministically."""
    order = sorted(icons, key=lambda i: i["name"].lower())
    # Brand groups with a sourced colour anywhere keep that colour.
    sourced_brands = {i["brand"] for i in icons if i.get("brand") and not is_palette(i)}
    identity = lambda i: i.get("brand") or i["name"]  # noqa: E731

    owners: dict[tuple, str] = {}
    for i in order:
        if not is_palette(i) or i.get("brand") in sourced_brands:
            owners.setdefault(render_key(i, i["color"]), identity(i))

    fixed = lambda i: not is_palette(i) or i.get("brand") in sourced_brands  # noqa: E731
    # The tile after each one, when its colour is already decided (sourced).
    upcoming = {id(a): display_accent(b["color"]) for a, b in zip(order, order[1:])
                if fixed(b)}

    out: dict[int, str] = {}
    brand_colour: dict[str, str] = {}
    step = 0
    previous = None
    for i in order:
        if not is_palette(i) or i.get("brand") in sourced_brands:
            previous = display_accent(i["color"])
            continue
        brand = i.get("brand")
        if brand and brand in brand_colour:
            colour = brand_colour[brand]
        else:
            for attempt in range(len(HEXES)):
                colour = HEXES[(step + attempt) * STRIDE % len(HEXES)]
                key = render_key(i, colour)
                if colour in (previous, upcoming.get(id(i))):
                    continue
                if owners.get(key, identity(i)) != identity(i):
                    continue
                step += attempt + 1
                break
            else:
                raise SystemExit(f"{i['name']}: no palette colour leaves it distinct")
            if brand:
                brand_colour[brand] = colour
        owners[render_key(i, colour)] = identity(i)
        out[id(i)] = colour
        previous = colour
    return out


def check_palette() -> list[str]:
    errors = []
    for name, hexv in PALETTE:
        if contrast(hexv) < MIN_READABLE:
            errors.append(f"{name} {hexv}: {contrast(hexv):.2f}:1 on the card, "
                          f"under {MIN_READABLE}")
    if len(set(HEXES)) != len(HEXES):
        errors.append("PALETTE repeats a colour")
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument("--check", action="store_true", help="fail on drift")
    args = parser.parse_args(argv)

    errors = check_palette()
    if errors:
        for e in errors:
            print(f"::error::{e}")
        return 1
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = data["icons"]
    colours = assign(icons)
    drift = [i["name"] for i in icons if id(i) in colours and i["color"] != colours[id(i)]]
    counts = Counter(colours.values())
    receipt = (f"{len(colours)} palette icons on {len(counts)} colours, "
               f"largest share {max(counts.values())} "
               f"({max(counts.values()) / len(colours):.1%})")
    if args.check:
        if drift:
            print(f"::error::{len(drift)} palette colours differ from tools/icon_palette.py "
                  f"(first: {', '.join(drift[:5])}); run python tools/icon_palette.py")
            return 1
        print(f"icon palette in sync - {receipt}")
        return 0
    for i in icons:
        if id(i) in colours:
            i["color"] = colours[id(i)]
    CATALOG.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"icon palette written - {len(drift)} recoloured; {receipt}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
