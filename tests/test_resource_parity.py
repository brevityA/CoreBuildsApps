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


PIXEL_KOTLIN = ROOT / "pixel-neon/app/src/main/java/tv/corebuilds/pixelneon"


def pixel_inflates() -> set[str]:
    """Layouts Pixel Neon's own Kotlin actually inflates.

    Pixel Neon is not a mirror of the classic pack: it keeps a fork of the
    Kotlin under `tv.corebuilds.pixelneon` with a smaller set of screens — it
    has no SettingsActivity, AboutActivity or RequestIconActivity.
    """
    wanted: set[str] = set()
    for path in PIXEL_KOTLIN.rglob("*.kt"):
        wanted |= set(re.findall(r"R\.layout\.([a-zA-Z0-9_]+)",
                                 path.read_text(encoding="utf-8")))
    for path in (PIXEL / "layout").glob("*.xml"):
        wanted |= set(re.findall(r'layout="@layout/([a-zA-Z0-9_]+)"',
                                 path.read_text(encoding="utf-8")))
    return {f"{name}.xml" for name in wanted}


def test_layouts_pixel_neon_inflates_exist_there():
    """Every layout Pixel Neon inflates has to be present in Pixel Neon.

    This used to demand that *every* layout under app/ be copied across, which
    is why `activity_about.xml` and `activity_settings.xml` sit in Pixel Neon's
    tree for screens it does not have. Copying is the wrong remedy for a layout
    the fork never inflates: `activity_request.xml` names
    `tv.corebuilds.iconpack.QrView`, a class outside Pixel Neon's package, so a
    copy there would be a file that throws if anything ever did inflate it.

    Stated against what the fork inflates, the rule still catches the failure it
    was written for — Pixel Neon reaching for a layout that is not there — and it
    starts requiring a screen's layout by itself on the day Pixel Neon grows that
    screen.
    """
    pixel_layouts = {p.name for p in (PIXEL / "layout").glob("*.xml")}
    missing = sorted(pixel_inflates() - pixel_layouts)
    assert not missing, f"Pixel Neon inflates layouts it does not have: {missing}"


# The three reference checks read Pixel Neon's own layouts, not app/'s.
#
# They used to resolve app/'s layout references against Pixel Neon's values,
# on the assumption that the two trees hold the same layouts. They do not:
# Pixel Neon hand-maintains its screens and legitimately differs — its home
# screen carries no Request tile, so app/'s `@string/request_label` is a string
# it has no use for. Checking app/'s references against Pixel Neon's values
# therefore fails on divergence that is the design, while missing nothing:
# what actually has to resolve is what Pixel Neon itself links.


def test_strings_used_by_pixel_neon_layouts_exist_there():
    missing = sorted(layout_refs(PIXEL, "string") - names(PIXEL / "values", "string"))
    assert not missing, f"Pixel Neon layouts use strings it does not declare: {missing}"


def test_dimensions_used_by_pixel_neon_layouts_exist_there():
    missing = sorted(layout_refs(PIXEL, "dimen") - names(PIXEL / "values", "dimen"))
    assert not missing, f"Pixel Neon layouts use dimensions it does not declare: {missing}"


def test_drawables_used_by_pixel_neon_layouts_exist_there():
    pixel_drawables = {p.stem for p in (PIXEL / "drawable").glob("*")}
    missing = sorted(layout_refs(PIXEL, "drawable") - pixel_drawables)
    assert not missing, f"Pixel Neon layouts use drawables it does not have: {missing}"


if __name__ == "__main__":
    for check in (
        test_layouts_pixel_neon_inflates_exist_there,
        test_strings_used_by_pixel_neon_layouts_exist_there,
        test_dimensions_used_by_pixel_neon_layouts_exist_there,
        test_drawables_used_by_pixel_neon_layouts_exist_there,
    ):
        check()
    print("resource parity: OK")
