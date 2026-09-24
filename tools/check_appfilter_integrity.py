#!/usr/bin/env python3
"""Appfilter integrity gate.

An icon pack that names a drawable its APK does not carry is worse than no
pack: the launcher resolves the mapping, fails to load the resource, and
falls back to a letter tile (observed in the wild: a missing tegrazone3.png
read as a "T" card on a Tegra Zone install, while every other icon looked
fine).

This gate closes that whole failure class. For every pack that ships an
appfilter, every drawable referenced by it must exist in that pack's res
tree — for both the res/xml and the assets copy, so the two cannot drift.

Run: python tools/check_appfilter_integrity.py
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

PACKS = [
    # (label, res root, appfilter copies)
    ("Icon Pack", ROOT / "app/src/main/res",
     [ROOT / "app/src/main/res/xml/appfilter.xml",
      ROOT / "app/src/main/assets/appfilter.xml"]),
]

DRAWABLE_RE = re.compile(r'drawable="([^"]+)"')


def fail(message: str) -> None:
    print(f"::error::{message}")
    raise AssertionError(message)


def referenced_drawables(path: Path) -> set[str]:
    text = path.read_text(encoding="utf-8")
    return set(DRAWABLE_RE.findall(text))


def available_drawables(res: Path) -> set[str]:
    names: set[str] = set()
    for folder in res.glob("drawable*"):
        if not folder.is_dir():
            continue
        for f in folder.iterdir():
            if f.suffix in (".png", ".webp", ".jpg", ".xml", ".9.png"):
                names.add(f.name[: -len(f.suffix)])
    return names


def main() -> None:
    total = 0
    for label, res, copies in PACKS:
        # The two appfilter copies must agree exactly (res/xml for modern
        # launchers, assets for older pickers).
        if len(set(c.read_text(encoding="utf-8") for c in copies)) != 1:
            fail(f"{label}: res/xml and assets appfilter copies differ")
        wanted = referenced_drawables(copies[0])
        # XML sanity: every item must carry a drawable attribute.
        root = ET.fromstring(copies[0].read_text(encoding="utf-8"))
        items = [el for el in root.iter() if el.tag.endswith("item")]
        bare = [el for el in items
                if el.get("component") and el.get("drawable") is None]
        if bare:
            fail(f"{label}: {len(bare)} appfilter items have a component "
                 "but no drawable")
        have = available_drawables(res)
        missing = sorted(wanted - have)
        if missing:
            fail(f"{label}: appfilter references {len(missing)} drawable(s) "
                 f"that are not in the res tree: {', '.join(missing[:8])}"
                 f"{' …' if len(missing) > 8 else ''}")
        total += len(wanted)
        print(f"\u2713 {label}: {len(wanted)} referenced drawables all present "
              f"({len(items)} component items, both appfilter copies in sync)")
    print(f"\u2713 appfilter integrity — {total} drawable references checked")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print(str(e))
        sys.exit(1)
