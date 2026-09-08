#!/usr/bin/env python3
"""Suite truth gate.

One registry (`suite.json`) owns app names, versions, package IDs, release tags,
Downloader codes, and Gradle paths. This check keeps README/agent docs/catalog/
update metadata from drifting back to stale release numbers.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README_START = "<!-- suite-stamp:start -->"
README_END = "<!-- suite-stamp:end -->"


def fail(message: str) -> None:
    print(f"::error::{message}")
    raise AssertionError(message)


def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        fail(f"missing {path}")
    return p.read_text(encoding="utf-8")


def gradle_value(source: str, name: str) -> str:
    match = re.search(rf"{name}\s*=\s*(?:\"([^\"]+)\"|(\d+))", source)
    if not match:
        fail(f"missing {name} in Gradle file")
    return match.group(1) or match.group(2)


def readme_stamp(suite: dict) -> str:
    from build_readme_badge import block
    return block(suite)


def check_suite_json() -> None:
    suite = json.loads(read("suite.json"))
    apps = suite.get("apps", {})
    expected = ["iconpack", "pixelneon", "pop", "line", "shift", "motion", "doctor"]
    if list(apps.keys()) != expected:
        fail(f"suite.json apps must be in order {expected}")
    for key, app in apps.items():
        gradle = read(app["gradle"])
        version_name = gradle_value(gradle, "versionName")
        version_code = int(gradle_value(gradle, "versionCode"))
        if app["versionName"] != version_name:
            fail(f"suite.json {key}.versionName {app['versionName']} != Gradle {version_name}")
        if app["versionCode"] != version_code:
            fail(f"suite.json {key}.versionCode {app['versionCode']} != Gradle {version_code}")
        if f'applicationId = "{app["applicationId"]}"' not in gradle:
            fail(f"suite.json {key}.applicationId does not match {app['gradle']}")
    return suite


def check_pop_truth(suite: dict) -> None:
    """Pop renders the same catalog as the classic pack, so its counts are not
    an independent fact — they are the catalog's, and drift means one of the
    two packs is lying about its own coverage."""
    catalog = json.loads(read("tools/catalog.json"))
    icons = catalog.get("icons", [])
    icon_count = len(icons)
    component_count = sum(len(row.get("components", [])) for row in icons)
    pop = suite["apps"]["pop"]
    latest = json.loads(read(pop["metadata"]))
    if pop.get("iconCount") != icon_count or pop.get("componentCount") != component_count:
        fail("suite.json pop counts must match catalog")
    if latest.get("versionName") != pop["versionName"] or latest.get("versionCode") != pop["versionCode"]:
        fail(f"{pop['metadata']} must match Pop suite/Gradle version")
    if latest.get("iconCount") != icon_count or latest.get("componentCount") != component_count:
        fail(f"{pop['metadata']} counts must match catalog")
    if pop["applicationId"] == suite["apps"]["iconpack"]["applicationId"]:
        fail("Pop must not share the classic pack's applicationId")
    if pop["tagPrefix"] == suite["apps"]["iconpack"]["tagPrefix"]:
        fail("Pop must not share the classic pack's release tag prefix")
    pop_list = read("docs/PopIconList.md")
    if f"`{icon_count}` icons" not in pop_list or f"pack v{pop['versionName']}" not in pop_list:
        fail("docs/PopIconList.md header drifted from suite/catalog")


def check_iconpack_truth(suite: dict) -> None:
    icon = suite["apps"]["iconpack"]
    catalog = json.loads(read("tools/catalog.json"))
    latest = json.loads(read("Latestrelease/version.json"))
    icons = catalog.get("icons", [])
    icon_count = len(icons)
    component_count = sum(len(row.get("components", [])) for row in icons)
    if catalog.get("meta", {}).get("version") != icon["versionName"]:
        fail("tools/catalog.json meta.version must match Icon Pack Gradle/suite versionName")
    if catalog.get("meta", {}).get("count") != icon_count:
        fail("tools/catalog.json meta.count must equal len(icons)")
    if icon.get("iconCount") != icon_count or icon.get("componentCount") != component_count:
        fail("suite.json iconpack counts must match catalog")
    if latest.get("versionName") != icon["versionName"] or latest.get("versionCode") != icon["versionCode"]:
        fail("Latestrelease/version.json must match Icon Pack suite/Gradle version")
    if latest.get("iconCount") != icon_count or latest.get("componentCount") != component_count:
        fail("Latestrelease/version.json counts must match catalog")
    icon_list = read("docs/IconPackList.md")
    if f"`{icon_count}` icons" not in icon_list or f"pack v{icon['versionName']}" not in icon_list:
        fail("docs/IconPackList.md header drifted from suite/catalog")


def check_readme_stamp(suite: dict) -> None:
    readme = read("README.md")
    if README_START not in readme or README_END not in readme:
        fail("README suite table must be generated inside suite-stamp comments")
    current = readme[readme.index(README_START):readme.index(README_END) + len(README_END)]
    expected = readme_stamp(suite)
    if current != expected:
        fail("README suite-stamp block is stale; run python tools/build_readme_badge.py")


def check_stale_claims() -> None:
    files = ["README.md", "AGENTS.md", "docs/IconPackList.md", "tools/catalog.json"]
    stale = ["v1.7.1", "pack v1.8.1", "`921 icons`", "921 transparent icons", "40 icons", "78 components", "Four Android apps", "four apps"]
    for file in files:
        text = read(file)
        for needle in stale:
            if needle in text:
                fail(f"stale suite truth in {file}: {needle}")
    for old in ["CLAUDE.md", "START_HERE_CLAUDE.md", "patches/START_HERE_CLAUDE.md", "README-EXTRACT.txt"]:
        if (ROOT / old).exists():
            fail(f"stale agent/extract file must be removed or archived: {old}")


def check_line_v_trap() -> None:
    allowed = {"AGENTS.md", "docs/SUITE-POLISH-PLAN.md", "tools/check_suite_truth.py"}
    offenders: list[str] = []
    for path in [ROOT / ".github", ROOT / "docs", ROOT / "README.md"]:
        paths = [path] if path.is_file() else list(path.rglob("*"))
        for p in paths:
            if not p.is_file():
                continue
            rel = str(p.relative_to(ROOT))
            if rel in allowed or rel.startswith("docs/archive/"):
                continue
            try:
                text = p.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            if re.search(r"(?<!core)line-v", text):
                offenders.append(rel)
    if offenders:
        fail("line-v* release prefix trap present outside docs warning: " + ", ".join(sorted(set(offenders))))


def main() -> int:
    suite = check_suite_json()
    check_iconpack_truth(suite)
    check_pop_truth(suite)
    check_readme_stamp(suite)
    check_stale_claims()
    check_line_v_trap()
    print("suite truth checks passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError:
        raise SystemExit(1)
