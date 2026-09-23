#!/usr/bin/env python3
"""
Core Builds Banners — coherence checks.

Mirrors tools/validate_pop.py for the Banners variant pack:
  * every catalog icon has a banner PNG under its base drawable name
  * every appfilter entry resolves to a drawable that exists
  * res/xml and assets/ copies are byte-identical
  * the app/ -> banners/ resource mirror is byte-identical where it should be
  * the Banners pack and classic pack agree about catalog components
  * manifest FileProvider authority does not collide with classic or Pop

Run:
    python tools/validate_banners.py
"""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_banner_pack  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
BANNERS_RES = ROOT / "banners" / "src" / "main" / "res"
BANNERS_PNG = BANNERS_RES / "drawable-nodpi"
BANNERS_XML = BANNERS_RES / "xml"
BANNERS_VAL = BANNERS_RES / "values"
BANNERS_ASSETS = ROOT / "banners" / "src" / "main" / "assets"
APP_RES = ROOT / "app" / "src" / "main" / "res"

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
    banners = suite["apps"]["banners"]
    icons = catalog["icons"]

    # ---- registry truth -------------------------------------------------
    component_count = sum(len(i.get("components", [])) for i in icons)
    check(banners["iconCount"] == len(icons),
          f"suite.json banners.iconCount {banners['iconCount']} != catalog {len(icons)}")
    check(banners["componentCount"] == component_count,
          f"suite.json banners.componentCount {banners['componentCount']} "
          f"!= catalog {component_count}")

    gradle = (ROOT / "banners" / "build.gradle.kts").read_text(encoding="utf-8")
    check(f'versionName = "{banners["versionName"]}"' in gradle,
          "banners/build.gradle.kts versionName disagrees with suite.json")
    check(f'applicationId = "{banners["applicationId"]}"' in gradle,
          "banners/build.gradle.kts applicationId disagrees with suite.json")
    check('applicationId = "tv.corebuilds.iconpack.banners"' in gradle,
          "Banners must have unique applicationId tv.corebuilds.iconpack.banners")
    check(banners["applicationId"] != suite["apps"]["iconpack"]["applicationId"],
          "Banners must not share classic pack applicationId")
    check(banners["applicationId"] != suite["apps"]["pop"]["applicationId"],
          "Banners must not share Pop applicationId")

    meta = json.loads((ROOT / banners["metadata"]).read_text(encoding="utf-8"))
    check(meta["versionName"] == banners["versionName"],
          f"{banners['metadata']} versionName disagrees with suite.json")
    check(meta["iconCount"] == len(icons),
          f"{banners['metadata']} iconCount disagrees with the catalog")

    # ---- assets ---------------------------------------------------------
    for i in icons:
        d = i["drawable"]
        check((BANNERS_PNG / f"{d}.png").exists(), f"{d}: missing banner PNG")
    check((BANNERS_PNG / "cb_banner.png").exists(), "missing Leanback banner cb_banner.png")

    # ---- mapping --------------------------------------------------------
    drawables = {i["drawable"] for i in icons}
    for xml_path in (BANNERS_XML / "appfilter.xml", BANNERS_ASSETS / "appfilter.xml"):
        root = ET.fromstring(xml_path.read_text(encoding="utf-8"))
        items = root.findall("item")
        check(len(items) > 0, f"{xml_path.name} has no entries")
        for item in items:
            drawable = item.get("drawable", "")
            check(drawable in drawables,
                  f"{xml_path.name}: '{drawable}' maps to no catalog icon")
            check((BANNERS_PNG / f"{drawable}.png").exists(),
                  f"{xml_path.name}: '{drawable}' has no PNG")

    a = (BANNERS_XML / "appfilter.xml").read_text(encoding="utf-8")
    b = (BANNERS_ASSETS / "appfilter.xml").read_text(encoding="utf-8")
    check(a == b, "res/xml/appfilter.xml and assets/appfilter.xml differ")

    classic = (ROOT / "app" / "src" / "main" / "res" / "xml"
               / "appfilter.xml").read_text(encoding="utf-8")

    def components(text: str) -> set[str]:
        return {m.group(1) for m in
                re.finditer(r'component="ComponentInfo\{([^}]+)\}"', text)}

    check(components(classic) == components(a),
          "the classic pack and banners pack map different components — both "
          "are generated from tools/catalog.json and must match")

    # drawable.xml
    d_res = (BANNERS_XML / "drawable.xml").read_text(encoding="utf-8")
    d_assets = (BANNERS_ASSETS / "drawable.xml").read_text(encoding="utf-8")
    check(d_res == d_assets, "drawable.xml in res/ and assets/ differ")

    # icon_pack.xml
    v_res = (BANNERS_VAL / "icon_pack.xml").read_text(encoding="utf-8")
    v_assets = (BANNERS_ASSETS / "icon_pack.xml").read_text(encoding="utf-8")
    check(v_res == v_assets, "icon_pack.xml in res/ and assets/ differ")

    # ---- mirror ---------------------------------------------------------
    for rel in build_banner_pack.MIRROR_FILES:
        src, dst = APP_RES / rel, BANNERS_RES / rel
        if not src.exists():
            continue
        check(dst.exists() and dst.read_bytes() == src.read_bytes(),
              f"mirrored resource {rel} is stale — run tools/build_banner_pack.py")
    for d in build_banner_pack.MIRROR_DIRS:
        src = APP_RES / d
        if not src.is_dir():
            continue
        for f in sorted(src.rglob("*")):
            if not f.is_file():
                continue
            dst = BANNERS_RES / d / f.relative_to(src)
            check(dst.exists() and dst.read_bytes() == f.read_bytes(),
                  f"mirrored resource {d}/{f.relative_to(src)} is stale — "
                  f"run tools/build_banner_pack.py")

    manifest = (ROOT / "banners" / "src" / "main" / "AndroidManifest.xml").read_text(
        encoding="utf-8")
    check('android:authorities="tv.corebuilds.iconpack.banners.update"' in manifest,
          "Banners' FileProvider authority must not collide with classic or Pop")

    # ---- report ---------------------------------------------------------
    if problems:
        print(f"Banners rejected — {len(problems)} problem(s) of {checks} checks:")
        for p in problems[:60]:
            print("  \u2717 " + p)
        return 1

    print(f"Validated {len(icons)} icons \u00b7 {component_count} components "
          f"\u00b7 {checks} checks run")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
