#!/usr/bin/env python3
"""Static release/readiness contracts for CoreBuildsApps.

These are intentionally repo-level checks, not Android runtime tests: they catch
missing CI, stale updater metadata, unbounded updater reads, unsigned release
workflow regressions, and accidental keystore commits before an APK can ship.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

APPS = {
    "iconpack": {
        "gradle": "app/build.gradle.kts",
        "metadata": "Latestrelease/version.json",
        "workflow": ".github/workflows/build.yml",
        "apk": "iconpack-release.apk",
        "checker": "app/src/main/java/tv/corebuilds/iconpack/UpdateChecker.kt",
        "installer": "app/src/main/java/tv/corebuilds/iconpack/UpdateInstaller.kt",
    },
    "pixelneon": {
        "gradle": "pixel-neon/app/build.gradle.kts",
        "metadata": "Latestrelease/pixel-neon-version.json",
        "workflow": ".github/workflows/pixel-neon-apk.yml",
        "apk": "pixel-neon-release.apk",
        "checker": "pixel-neon/app/src/main/java/tv/corebuilds/pixelneon/UpdateChecker.kt",
        "installer": "pixel-neon/app/src/main/java/tv/corebuilds/pixelneon/UpdateInstaller.kt",
    },
    "pop": {
        "gradle": "pop/build.gradle.kts",
        "metadata": "Latestrelease/pop-version.json",
        "workflow": ".github/workflows/pop-apk.yml",
        "apk": "corepop-release.apk",
        # Pop compiles the icon pack's Kotlin; the updater hardening it must
        # satisfy is therefore the same file, checked once per app so a
        # regression cannot slip in behind "but the other pack passes".
        "checker": "app/src/main/java/tv/corebuilds/iconpack/UpdateChecker.kt",
        "installer": "app/src/main/java/tv/corebuilds/iconpack/UpdateInstaller.kt",
    },
    "coreline": {
        "gradle": "ticker/android/app/build.gradle.kts",
        "metadata": "Latestrelease/coreline-version.json",
        "workflow": ".github/workflows/core-line-apk.yml",
        "apk": "coreline-release.apk",
        "installer": "ticker/android/app/src/main/java/dev/corebuilds/line/UpdateManager.kt",
    },
    "coreshift": {
        "gradle": "shift/app/build.gradle.kts",
        "metadata": "Latestrelease/shift-version.json",
        "workflow": ".github/workflows/core-shift-apk.yml",
        "apk": "coreshift-release.apk",
        "checker": "shift/app/src/main/java/dev/corebuilds/shift/UpdateChecker.kt",
        "installer": "shift/app/src/main/java/dev/corebuilds/shift/UpdateInstaller.kt",
    },
    "coredoctor": {
        "gradle": "doctor/app/build.gradle.kts",
        "workflow": ".github/workflows/core-doctor-apk.yml",
        "apk": "coredoctor-release.apk",
    },
    "coremotion": {
        "gradle": "motion-plugin/app/build.gradle.kts",
        "workflow": ".github/workflows/core-motion-apk.yml",
        "apk": "coremotion-release.apk",
    },
}


def fail(msg: str) -> None:
    print(f"::error::{msg}")
    raise AssertionError(msg)


def text(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        fail(f"missing {path}")
    return p.read_text(encoding="utf-8")


def gradle_value(source: str, name: str) -> str:
    m = re.search(rf"{name}\s*=\s*(?:\"([^\"]+)\"|(\d+))", source)
    if not m:
        fail(f"missing {name}")
    return m.group(1) or m.group(2)


def check_version_truth() -> None:
    import importlib.util
    spec = importlib.util.spec_from_file_location("check_suite_truth", ROOT / "tools" / "check_suite_truth.py")
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    module.main()


def check_workflows() -> None:
    suite = text(".github/workflows/suite-ci.yml")
    for app, cfg in APPS.items():
        wf = text(cfg["workflow"])
        if "pull_request:" not in wf:
            fail(f"{app} workflow has no PR trigger")
        if "actions/upload-artifact" not in wf:
            fail(f"{app} workflow does not upload an APK artifact")
        if app != "iconpack" and cfg["apk"] not in wf:
            fail(f"{app} workflow does not name {cfg['apk']}")
        if cfg["gradle"] not in suite or cfg["workflow"] not in suite:
            fail(f"suite CI gate does not track {app}")


def check_metadata() -> None:
    for app, cfg in APPS.items():
        gradle = text(cfg["gradle"])
        code = int(gradle_value(gradle, "versionCode"))
        name = gradle_value(gradle, "versionName")
        meta_path = cfg.get("metadata")
        if not meta_path:
            continue
        meta = json.loads(text(meta_path))
        if meta.get("versionCode") != code:
            fail(f"{meta_path} versionCode {meta.get('versionCode')} != Gradle {code}")
        if meta.get("versionName") != name:
            fail(f"{meta_path} versionName {meta.get('versionName')} != Gradle {name}")
        apk_url = str(meta.get("apkUrl", ""))
        if not apk_url.startswith("https://github.com/brevityA/CoreBuildsApps/releases/download/"):
            fail(f"{meta_path} apkUrl is not the CoreBuildsApps GitHub release channel")
        if cfg["apk"] not in apk_url:
            fail(f"{meta_path} apkUrl does not reference {cfg['apk']}")


def check_updaters() -> None:
    for app, cfg in APPS.items():
        checker = cfg.get("checker")
        if checker:
            src = text(checker)
            if "raw.githubusercontent.com/brevityA/CoreBuildsIconPack" in src:
                fail(f"{checker} still points at the stale CoreBuildsIconPack repo")
            if "BuildConfig.VERSION_CODE" not in src:
                fail(f"{checker} does not compare against BuildConfig.VERSION_CODE")
            if "readText()" in src:
                fail(f"{checker} uses unbounded readText()")
            if "MAX_MANIFEST_BYTES" not in src:
                fail(f"{checker} does not bound manifest reads")
        installer = cfg.get("installer")
        if installer:
            src = text(installer)
            for needle in [
                "GET_SIGNING_CERTIFICATES",
                "APK signature does not match installed app",
                "APK checksum mismatch",
                "MAX_APK_BYTES",
                "package mismatch",
            ]:
                if needle not in src:
                    fail(f"{installer} missing updater hardening: {needle}")


def check_secrets_absent() -> None:
    forbidden = {".jks", ".keystore", ".p12", ".pfx", "keystore.properties"}
    tracked = [p for p in ROOT.rglob("*") if p.is_file() and any(p.name.endswith(s) or p.name == s for s in forbidden)]
    if tracked:
        fail("keystore-like files present in repository: " + ", ".join(str(p.relative_to(ROOT)) for p in tracked))


def main() -> int:
    checks = [check_version_truth, check_workflows, check_metadata, check_updaters, check_secrets_absent]
    for check in checks:
        check()
        print(f"ok {check.__name__}")
    print("release/update contract checks passed")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError:
        raise SystemExit(1)
