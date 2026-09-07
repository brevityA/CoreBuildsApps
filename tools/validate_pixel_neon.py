#!/usr/bin/env python3
"""Validate the generated Core Builds Pixel Neon companion pack."""
from __future__ import annotations

import hashlib
import json
import re
import struct
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"
PACK = ROOT / "pixel-neon"
RES = PACK / "app" / "src" / "main" / "res"
failures: list[str] = []
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        failures.append(message)


def png_size(path: Path) -> tuple[int, int, int] | None:
    try:
        raw = path.read_bytes()
        width, height = struct.unpack(">II", raw[16:24])
        return width, height, raw[25]
    except Exception as exc:
        failures.append(f"{path.relative_to(ROOT)}: unreadable PNG header ({exc})")
        return None


def canonical(value: str) -> str:
    match = re.match(r"^ComponentInfo\{([^/]+)/([^}]+)\}$", value)
    if not match:
        return value
    package, activity = match.groups()
    if activity.startswith("."):
        activity = package + activity
    return f"{package}/{activity}"


def expand(component: str) -> set[str]:
    package, _, activity = component.partition("/")
    if not activity:
        return {component}
    if activity.startswith("."):
        return {component, f"{package}/{package}{activity}"}
    if activity.startswith(package + "."):
        return {component, f"{package}/{activity[len(package):]}"}
    return {component}


def main() -> int:
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())
    names = {icon["drawable"] for icon in icons}
    check(len(icons) == 924, f"catalog has {len(icons)} icons, expected 924")
    check(data["meta"]["count"] == len(icons), "catalog meta.count drifted")

    square_dir = RES / "drawable-nodpi"
    sprite_hashes: set[str] = set()
    for icon in icons:
        square = square_dir / f"{icon['drawable']}.png"
        banner = square_dir / f"{icon['drawable']}_banner.png"
        check(square.exists(), f"{icon['name']}: missing pixel square")
        check(banner.exists(), f"{icon['name']}: missing pixel banner")
        if square.exists():
            header = png_size(square)
            check(header == (512, 512, 6),
                  f"{icon['name']}: square header {header}, expected 512x512 RGBA")
            sprite_hashes.add(hashlib.sha256(square.read_bytes()).hexdigest())
        if banner.exists():
            header = png_size(banner)
            check(header == (320, 180, 6),
                  f"{icon['name']}: banner header {header}, expected 320x180 RGBA")
    check(len(sprite_hashes) == len(icons),
          f"only {len(sprite_hashes)} unique sprite files for {len(icons)} catalog rows")

    appfilter_path = RES / "xml" / "appfilter.xml"
    appfilter = ET.parse(appfilter_path).getroot()
    emitted: set[str] = set()
    drawables: set[str] = set()
    for item in appfilter.findall("item"):
        component = item.get("component", "")
        drawable = item.get("drawable", "")
        emitted.add(component)
        drawables.add(drawable)
        check(drawable.endswith("_banner"),
              f"appfilter: {drawable} is not a banner drawable")
        check(drawable.removesuffix("_banner") in names,
              f"appfilter: {drawable} has no catalog square")
    check('ComponentInfo{tv.corebuilds.pixelneon/tv.corebuilds.pixelneon.MainActivity}' in emitted,
          "appfilter: Pixel Neon launcher component is not mapped")
    check(len(emitted) == 1661,
          f"appfilter emits {len(emitted)} entries, expected 1661 including own launcher")

    canonical_emitted = {canonical(item) for item in emitted}
    for icon in icons:
        for component in icon["components"]:
            for spelling in expand(component):
                check(canonical(f"ComponentInfo{{{spelling}}}") in canonical_emitted,
                      f"{icon['name']}: missing component {spelling}")

    drawable_path = RES / "xml" / "drawable.xml"
    drawable_root = ET.parse(drawable_path).getroot()
    listed = {item.get("drawable") for item in drawable_root.findall("item")}
    check({name for name in names} <= listed, "drawable.xml misses a catalog icon")
    check({f"{name}_banner" for name in names} <= listed,
          "drawable.xml misses a catalog banner")
    assets = PACK / "app" / "src" / "main" / "assets"
    for filename in ("appfilter.xml", "drawable.xml"):
        res_file = RES / "xml" / filename
        asset_file = assets / filename
        check(asset_file.exists(), f"assets/{filename} is missing")
        if asset_file.exists():
            check(asset_file.read_bytes() == res_file.read_bytes(),
                  f"assets/{filename} differs from res/xml/{filename}")

    arrays = (RES / "values" / "icon_pack.xml").read_text(encoding="utf-8")
    check(arrays.count("<item>") == 3 * len(icons),
          "icon_pack.xml does not contain three complete generated arrays")
    check((RES / "drawable-nodpi" / "cb_banner.png").exists(),
          "Pixel Neon Leanback banner is missing")
    check((RES / "mipmap-xhdpi" / "ic_launcher_foreground.png").exists(),
          "Pixel Neon adaptive foreground is missing")

    receipt = json.loads((PACK / "docs" / "build-receipt.json").read_text())
    check(receipt.get("pixelGrid") == 32, "build receipt does not record the 32px sprite grid")
    check(receipt.get("uniqueSprites") == len(icons), "build receipt does not prove unique sprites")
    check(receipt.get("icons") == len(icons), "build receipt icon count drifted")
    check(receipt.get("catalogComponents") == sum(len(i["components"]) for i in icons),
          "build receipt component count drifted")
    source = (ROOT / "tools" / "build_pixel_neon.py").read_text(encoding="utf-8")
    check("BASE_SVG" not in source and "BASE_BANNERS" not in source,
          "Pixel Neon renderer still depends on monoline source assets")

    if failures:
        print(f"Pixel Neon validation failed — {len(failures)} problem(s)")
        for failure in failures:
            print(f"  ✗ {failure}")
        return 1
    print(f"Validated Pixel Neon · {len(icons)} icons · "
          f"{sum(len(i['components']) for i in icons)} catalog components · "
          f"{checks} checks run")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
