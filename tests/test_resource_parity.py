#!/usr/bin/env python3
"""Catch shared Android resource drift before Gradle resource linking."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/res"
PIXEL = ROOT / "pixel-neon/app/src/main/res"


def names(path: Path, tag: str, attr: str = "name") -> set[str]:
    out = set()
    for p in path.rglob("*.xml"):
        try:
            root = ET.parse(p).getroot()
        except ET.ParseError:
            continue
        for node in root.iter(tag):
            value = node.get(attr)
            if value:
                out.add(value)
    return out


def layout_refs(path: Path, kind: str) -> set[str]:
    pattern = re.compile(rf"@{kind}/([a-zA-Z0-9_]+)")
    return {
        match.group(1)
        for p in path.rglob("layout/*.xml")
        for match in pattern.finditer(p.read_text(encoding="utf-8"))
    }


def test_shared_layouts_exist_in_pixel_neon():
    app_layouts = {p.name for p in (APP / "layout").glob("*.xml")}
    pixel_layouts = {p.name for p in (PIXEL / "layout").glob("*.xml")}
    missing = sorted(app_layouts - pixel_layouts)
    assert not missing, f"Pixel Neon is missing shared layouts: {missing}"


def test_shared_strings_used_by_app_layouts_exist_in_pixel_neon():
    pixel_strings = names(PIXEL / "values", "string")
    refs = layout_refs(APP, "string")
    missing = sorted(refs - pixel_strings)
    assert not missing, f"Pixel Neon is missing strings used by shared layouts: {missing}"


def test_shared_dimensions_used_by_app_layouts_exist_in_pixel_neon():
    pixel_dims = names(PIXEL / "values", "dimen")
    refs = layout_refs(APP, "dimen")
    missing = sorted(refs - pixel_dims)
    assert not missing, f"Pixel Neon is missing dimensions used by shared layouts: {missing}"


def test_shared_drawables_used_by_app_layouts_exist_in_pixel_neon():
    pixel_drawables = {p.stem for p in (PIXEL / "drawable").glob("*")}
    refs = layout_refs(APP, "drawable")
    missing = sorted(refs - pixel_drawables)
    assert not missing, f"Pixel Neon is missing drawables used by shared layouts: {missing}"


if __name__ == "__main__":
    for check in (
        test_shared_layouts_exist_in_pixel_neon,
        test_shared_strings_used_by_app_layouts_exist_in_pixel_neon,
        test_shared_dimensions_used_by_app_layouts_exist_in_pixel_neon,
        test_shared_drawables_used_by_app_layouts_exist_in_pixel_neon,
    ):
        check()
    print("resource parity: OK")
