#!/usr/bin/env python3
"""Keep the brandmark flag honest in every pack that ships it.

The grid draws a dot on tiles whose glyph is a drawn brandmark rather than a
letter in a container, and a "Brandmarks" chip filters to them. Both read
R.array.icon_bespoke, which three separate generators write:

    tools/build_icons.py       -> app/src/main/res/values/icon_pack.xml
    tools/build_pop.py         -> pop/src/main/res/values/icon_pack.xml
    tools/build_pixel_neon.py  -> pixel-neon/app/src/main/res/values/icon_pack.xml

Three ways that goes wrong, all checked here:

* the array falls out of step with icon_pack, so every tile past the short
  point is badged by its neighbour's glyph;
* a generator is edited and the packs start disagreeing about which icons are
  bespoke, which is invisible until someone compares two TVs;
* the classification drifts from tools/catalog.json, which is the only real
  source — the flag is derived from the catalog's `glyph` field through
  glyphs.MONOGRAM_GLYPHS, and a regex over glyph names would quietly
  misclassify anything starting with a family word.

The counts are asserted against the catalog rather than pinned to a number, so
adding icons does not fail this suite; replacing a monogram with a drawn mark
moves the totals and is meant to.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import json

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from glyphs import MONOGRAM_GLYPHS, is_monogram  # noqa: E402

PACKS = {
    "app": ROOT / "app/src/main/res/values/icon_pack.xml",
    "pop": ROOT / "pop/src/main/res/values/icon_pack.xml",
    "banners": ROOT / "banners/src/main/res/values/icon_pack.xml",
    "pixel-neon": ROOT / "pixel-neon/app/src/main/res/values/icon_pack.xml",
}


def arrays(path: Path) -> tuple[list[str], list[int]]:
    root = ET.parse(path).getroot()
    names: list[str] = []
    flags: list[int] = []
    for node in root.iter("string-array"):
        if node.get("name") == "icon_pack":
            names = [(item.text or "") for item in node]
    for node in root.iter("integer-array"):
        if node.get("name") == "icon_bespoke":
            flags = [int((item.text or "0").strip()) for item in node]
    return names, flags


def catalog_flags() -> dict[str, bool]:
    icons = json.loads((ROOT / "tools/catalog.json").read_text())["icons"]
    return {i["drawable"]: not is_monogram(i["glyph"]) for i in icons}


def test_monogram_registry_is_not_empty():
    """A registry that silently emptied would mark every icon bespoke."""
    assert len(MONOGRAM_GLYPHS) > 100, (
        f"only {len(MONOGRAM_GLYPHS)} monogram glyph names registered; "
        "FAMILY_SHELLS or the tile set has probably been renamed"
    )


def test_every_pack_has_a_flag_per_icon():
    for pack, path in PACKS.items():
        names, flags = arrays(path)
        assert names, f"{pack}: icon_pack array missing"
        assert flags, f"{pack}: icon_bespoke array missing — regenerate it"
        assert len(flags) == len(names), (
            f"{pack}: icon_bespoke has {len(flags)} entries for "
            f"{len(names)} icons; the badge would be off by "
            f"{abs(len(flags) - len(names))} tiles"
        )
        assert set(flags) <= {0, 1}, f"{pack}: icon_bespoke must be 0 or 1"


def test_flags_match_the_catalog():
    """Every pack lists exactly the catalog's icons, each flagged correctly.

    The name-set assertion is not decoration. Comparing only the names that
    appear in both sets means a pack could carry a drawable the catalog has
    never heard of and still pass: the length check and the cross-pack check
    both survive it if every pack does the same thing. On device that tile
    reaches getIdentifier(), gets resource id 0, and draws nothing.
    """
    expected = catalog_flags()
    expected_names = set(expected)
    for pack, path in PACKS.items():
        names, flags = arrays(path)
        missing = sorted(expected_names - set(names))
        extra = sorted(set(names) - expected_names)
        assert not missing and not extra and len(names) == len(expected), (
            f"{pack}: icon_pack does not match the catalog — "
            f"missing {missing[:5]}, extra {extra[:5]}, "
            f"{len(names)} entries for {len(expected)} catalog icons"
        )
        wrong = [
            name for name, flag in zip(names, flags, strict=True)
            if bool(flag) != expected[name]
        ]
        assert not wrong, (
            f"{pack}: {len(wrong)} icons flagged against the catalog, "
            f"first few: {wrong[:5]}"
        )


def test_packs_agree_with_each_other():
    baseline = None
    for pack, path in PACKS.items():
        names, flags = arrays(path)
        current = dict(zip(names, flags))
        if baseline is None:
            baseline, baseline_pack = current, pack
            continue
        shared = set(baseline) & set(current)
        disagree = sorted(n for n in shared if baseline[n] != current[n])
        assert not disagree, (
            f"{pack} and {baseline_pack} disagree on {len(disagree)} icons: "
            f"{disagree[:5]}"
        )


if __name__ == "__main__":
    expected = catalog_flags()
    total = len(expected)
    bespoke = sum(expected.values())
    print(f"catalog: {bespoke} brandmarks, {total - bespoke} monograms, {total} icons")
    for pack, path in PACKS.items():
        names, flags = arrays(path)
        print(f"{pack:12s} {sum(flags):4d} brandmarks of {len(names)}")
    test_monogram_registry_is_not_empty()
    test_every_pack_has_a_flag_per_icon()
    test_flags_match_the_catalog()
    test_packs_agree_with_each_other()
    print("ok")
