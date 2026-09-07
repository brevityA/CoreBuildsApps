#!/usr/bin/env python3
"""
Core Builds Pop — coherence checks.

Mirrors tools/validate.py for the classic pack and adds the checks that only
matter once a pack claims to be *uniform*. "Uniform" is a measurable property,
so it gets measured rather than asserted:

  * every catalog icon has a square PNG, a banner PNG and two SVG masters
  * every appfilter entry resolves to a drawable that exists
  * every accent snaps to one of the 16 locked swatches, and no other colour
    appears in any master
  * every swatch clears the contrast floor against both INK and CREAM
  * every mark lands within tolerance of the same optical size
  * the app/ -> pop/ resource mirror is byte-identical where it should be
  * the Pop pack and the classic pack agree about every component mapping

Run:
    python tools/validate_pop.py
"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import popart  # noqa: E402
from popart import CREAM, INK, SWATCHES, contrast, fit, snap  # noqa: E402
import build_pop  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
POP_RES = ROOT / "pop" / "src" / "main" / "res"
POP_PNG = POP_RES / "drawable-nodpi"
POP_XML = POP_RES / "xml"
POP_ASSETS = ROOT / "pop" / "src" / "main" / "assets"
SVG_DIR = ROOT / "assets" / "pop" / "svg"
BANNER_DIR = ROOT / "assets" / "pop" / "banners"
APP_RES = ROOT / "app" / "src" / "main" / "res"

# Legibility of a two-tone mark is not a single ratio.
#
# Every mark is cream filled with an ink keyline, and cream is the lightest
# thing in the pack while ink is the darkest. So a field colour is always
# *bracketed* by the mark: whichever side has low contrast, the other side
# carries the edge. That is the actual invariant, and it is what gets checked:
#
#   1. the mark's own two tones separate hard (cream vs ink)
#   2. every field is strictly between ink and cream in luminance, so the
#      mark has an edge on both sides and can never melt into the field
#   3. the dominant side clears 4:1, so the shape reads at 10 feet
#
# A naive "cream must clear 3:1 on every field" would ban yellow, which is
# exactly the colour comics rely on an ink keyline to carry.
MIN_PAIR_CONTRAST = 7.0
MIN_DOMINANT_CONTRAST = 4.0

# Optical size tolerance. fit() solves exactly, so this only catches a mark
# whose measured box is stale or degenerate.
SIZE_TOLERANCE = 0.02

checks = 0
problems: list[str] = []


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        problems.append(message)


def main() -> int:
    catalog = json.loads((ROOT / "tools" / "catalog.json").read_text(
        encoding="utf-8"))
    suite = json.loads((ROOT / "suite.json").read_text(encoding="utf-8"))
    pop = suite["apps"]["pop"]
    icons = catalog["icons"]

    # ---- registry truth -------------------------------------------------
    component_count = sum(len(i.get("components", [])) for i in icons)
    check(pop["iconCount"] == len(icons),
          f"suite.json pop.iconCount {pop['iconCount']} != catalog {len(icons)}")
    check(pop["componentCount"] == component_count,
          f"suite.json pop.componentCount {pop['componentCount']} "
          f"!= catalog {component_count}")

    gradle = (ROOT / "pop" / "build.gradle.kts").read_text(encoding="utf-8")
    check(f'versionName = "{pop["versionName"]}"' in gradle,
          "pop/build.gradle.kts versionName disagrees with suite.json")
    check(f'applicationId = "{pop["applicationId"]}"' in gradle,
          "pop/build.gradle.kts applicationId disagrees with suite.json")
    check('applicationId = "tv.corebuilds.iconpack.pop"' in gradle,
          "Pop must not share the classic pack's applicationId")

    meta = json.loads((ROOT / pop["metadata"]).read_text(encoding="utf-8"))
    check(meta["versionName"] == pop["versionName"],
          f"{pop['metadata']} versionName disagrees with suite.json")
    check(meta["iconCount"] == len(icons),
          f"{pop['metadata']} iconCount disagrees with the catalog")

    # ---- assets ---------------------------------------------------------
    for i in icons:
        d = i["drawable"]
        check((SVG_DIR / f"{d}.svg").exists(), f"{d}: missing square master")
        check((BANNER_DIR / f"{d}.svg").exists(), f"{d}: missing banner master")
        check((POP_PNG / f"{d}.png").exists(), f"{d}: missing square PNG")
        check((POP_PNG / f"{d}_banner.png").exists(), f"{d}: missing banner PNG")

    # ---- palette --------------------------------------------------------
    allowed = {v.upper() for v in SWATCHES.values()}
    allowed |= {INK.upper(), CREAM.upper()}
    allowed |= {popart.shade(v, 0.70).upper() for v in SWATCHES.values()}
    # the halftone mask is greyscale and carries no hue
    allowed |= {"#000000", "#8A8A8A", "#FFFFFF"}

    hex_re = re.compile(r"#[0-9A-Fa-f]{6}")
    for i in icons:
        d = i["drawable"]
        swatch, field = snap(i["color"])
        check(swatch in SWATCHES, f"{d}: accent {i['color']} snapped to "
                                  f"unknown swatch '{swatch}'")
        for path in (SVG_DIR / f"{d}.svg", BANNER_DIR / f"{d}.svg"):
            if not path.exists():
                continue
            found = {c.upper() for c in hex_re.findall(
                path.read_text(encoding="utf-8"))}
            stray = found - allowed
            check(not stray,
                  f"{d} ({path.parent.name}): colour outside the locked "
                  f"palette: {', '.join(sorted(stray))}")

    check(contrast(CREAM, INK) >= MIN_PAIR_CONTRAST,
          f"the mark's own two tones only separate "
          f"{contrast(CREAM, INK):.2f}:1 — below {MIN_PAIR_CONTRAST}")

    lum_ink = popart.relative_luminance(INK)
    lum_cream = popart.relative_luminance(CREAM)
    seen_hex: dict[str, str] = {}
    for name, hexv in SWATCHES.items():
        lum = popart.relative_luminance(hexv)
        check(lum_ink < lum < lum_cream,
              f"swatch {name} {hexv}: luminance {lum:.3f} is not bracketed by "
              f"ink ({lum_ink:.3f}) and cream ({lum_cream:.3f}) — the mark "
              f"would lose an edge on this field")
        dominant = max(contrast(hexv, CREAM), contrast(hexv, INK))
        check(dominant >= MIN_DOMINANT_CONTRAST,
              f"swatch {name} {hexv}: dominant contrast {dominant:.2f} "
              f"< {MIN_DOMINANT_CONTRAST}")
        check(hexv.upper() not in seen_hex,
              f"swatch {name} duplicates {seen_hex.get(hexv.upper())} "
              f"({hexv}) — 16 swatches must be 16 colours")
        seen_hex[hexv.upper()] = name

    # ---- optical uniformity ---------------------------------------------
    metrics = json.loads((ROOT / "tools" / "pop_glyph_metrics.json").read_text(
        encoding="utf-8"))["metrics"]
    for glyph in sorted({i["glyph"] for i in icons}):
        check(glyph in metrics, f"glyph '{glyph}' has no measured ink box")
        if glyph not in metrics:
            continue
        scale, _, _ = fit(glyph, target=popart.TARGET_INK,
                          outline=popart.GLYPH_OUTLINE)
        x0, y0, x1, y1 = metrics[glyph]
        span = max(max(x1 - x0, popart.MIN_GEOM), max(y1 - y0, popart.MIN_GEOM))
        drawn = span * scale + popart.POP_STROKE + popart.GLYPH_OUTLINE
        off = abs(drawn - popart.TARGET_INK) / popart.TARGET_INK
        check(off <= SIZE_TOLERANCE,
              f"glyph '{glyph}': optical size {drawn:.1f} is "
              f"{off:.1%} off the {popart.TARGET_INK} target")

    # ---- mapping --------------------------------------------------------
    drawables = {i["drawable"] for i in icons}
    for xml_path in (POP_XML / "appfilter.xml", POP_ASSETS / "appfilter.xml"):
        root = ET.fromstring(xml_path.read_text(encoding="utf-8"))
        items = root.findall("item")
        check(len(items) > 0, f"{xml_path.name} has no entries")
        for item in items:
            drawable = item.get("drawable", "")
            base = drawable[:-len("_banner")] if drawable.endswith("_banner") \
                else drawable
            check(base in drawables,
                  f"{xml_path.name}: '{drawable}' maps to no catalog icon")
            check((POP_PNG / f"{drawable}.png").exists(),
                  f"{xml_path.name}: '{drawable}' has no PNG")

    a = (POP_XML / "appfilter.xml").read_text(encoding="utf-8")
    b = (POP_ASSETS / "appfilter.xml").read_text(encoding="utf-8")
    check(a == b, "res/xml/appfilter.xml and assets/appfilter.xml differ")

    classic = (ROOT / "app" / "src" / "main" / "res" / "xml"
               / "appfilter.xml").read_text(encoding="utf-8")

    def components(text: str) -> set[str]:
        return {m.group(1) for m in
                re.finditer(r'component="ComponentInfo\{([^}]+)\}"', text)}

    check(components(a) == components(classic),
          "Pop and the classic pack disagree about which components are "
          "mapped — both are generated from tools/catalog.json and must not")

    # ---- mirror ---------------------------------------------------------
    for rel in build_pop.MIRROR_FILES:
        src, dst = APP_RES / rel, POP_RES / rel
        if not src.exists():
            continue
        check(dst.exists() and dst.read_bytes() == src.read_bytes(),
              f"mirrored resource {rel} is stale — run tools/build_pop.py")
    for d in build_pop.MIRROR_DIRS:
        src = APP_RES / d
        if not src.is_dir():
            continue
        for f in sorted(src.rglob("*")):
            if not f.is_file():
                continue
            dst = POP_RES / d / f.relative_to(src)
            check(dst.exists() and dst.read_bytes() == f.read_bytes(),
                  f"mirrored resource {d}/{f.relative_to(src)} is stale — "
                  f"run tools/build_pop.py")

    manifest = (ROOT / "pop" / "src" / "main" / "AndroidManifest.xml").read_text(
        encoding="utf-8")
    check('android:authorities="tv.corebuilds.iconpack.pop.update"' in manifest,
          "Pop's FileProvider authority must not collide with the classic pack")

    # ---- report ---------------------------------------------------------
    if problems:
        print(f"Pop rejected — {len(problems)} problem(s) of {checks} checks:")
        for p in problems[:60]:
            print("  \u2717 " + p)
        if len(problems) > 60:
            print(f"  … and {len(problems) - 60} more")
        return 1

    print(f"Validated {len(icons)} icons \u00b7 {component_count} components "
          f"\u00b7 {len(SWATCHES)} swatches \u00b7 {checks} checks run")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
