#!/usr/bin/env python3
"""
New tests for Core Builds Icon Pack v1.5.1.

These are the contracts added in the robustness pass. They do not replace
tools/validate.py (12k+ catalog/PNG/appfilter checks). Run from anywhere:

    python3 tests/test_v151_robustness.py

Walks up from this file until it finds tools/catalog.json.
"""
from __future__ import annotations

import json
import re
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


def find_root() -> Path:
    here = Path(__file__).resolve().parent
    for p in [here, *here.parents]:
        if (p / "tools" / "catalog.json").exists():
            return p
    raise SystemExit("could not find repo root (tools/catalog.json)")


ROOT = find_root()
RES = ROOT / "app" / "src" / "main" / "res"
KT = ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds" / "iconpack"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


class ManifestUpdaterTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mf = read("app/src/main/AndroidManifest.xml")

    def test_request_install_packages_declared(self):
        self.assertIn(
            "android.permission.REQUEST_INSTALL_PACKAGES",
            self.mf,
            "without this the system installer cannot be opened",
        )

    def test_file_provider_class(self):
        self.assertIn(
            "androidx.core.content.FileProvider",
            self.mf,
            "UpdateInstaller.install() crashes if the provider is missing",
        )

    def test_file_provider_authority(self):
        self.assertIn(
            'android:authorities="tv.corebuilds.iconpack.update"',
            self.mf,
        )

    def test_file_provider_not_exported(self):
        block = self.mf.split("FileProvider", 1)[1][:800]
        self.assertIn('android:exported="false"', block)
        self.assertIn('android:grantUriPermissions="true"', block)

    def test_file_paths_exposes_cache_updates(self):
        p = RES / "xml" / "file_paths.xml"
        self.assertTrue(p.exists(), "xml/file_paths.xml missing")
        body = p.read_text(encoding="utf-8")
        self.assertIn('name="updates"', body)
        self.assertIn('path="updates/"', body)

    def test_monet_and_at4k_are_queryable(self):
        for pkg in ("com.klevico.monet", "com.overdevs.at4k"):
            self.assertIn(
                f'<package android:name="{pkg}"',
                self.mf,
                f"API 30+ cannot see {pkg} without <queries>",
            )


class UpdateInstallerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.src = read(
            "app/src/main/java/tv/corebuilds/iconpack/UpdateInstaller.kt"
        )

    def test_authority_matches_manifest(self):
        self.assertIn('AUTHORITY = "tv.corebuilds.iconpack.update"', self.src)

    def test_github_hosts_allowlisted(self):
        for host in (
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        ):
            self.assertIn(host, self.src, f"missing allowlisted host {host}")

    def test_https_only(self):
        self.assertIn('protocol != "https"', self.src)

    def test_rejects_non_apk_magic(self):
        self.assertIn("0x50.toByte()", self.src)
        self.assertIn("0x4B.toByte()", self.src)
        self.assertIn("not a ZIP/APK", self.src)

    def test_min_apk_size_is_not_tiny(self):
        m = re.search(r"MIN_APK_BYTES\s*=\s*([\d_]+)L?", self.src)
        self.assertIsNotNone(m, "MIN_APK_BYTES missing")
        self.assertGreaterEqual(int(m.group(1).replace("_", "")), 200_000)


class PickerTests(unittest.TestCase):
    def test_always_returns_icon_resource(self):
        src = read("app/src/main/java/tv/corebuilds/iconpack/IconPicker.kt")
        self.assertIn("EXTRA_SHORTCUT_ICON_RESOURCE", src)
        self.assertIn("ShortcutIconResource.fromContext", src)
        self.assertIn("EXTRA_SHORTCUT_ICON", src)

    def test_pick_default_is_banner(self):
        src = read("app/src/main/java/tv/corebuilds/iconpack/MainActivity.kt")
        self.assertIn("pickBanners = true", src)
        self.assertIn("${item.drawable}_banner", src)
        self.assertIn("PICK_BANNER", src)
        self.assertIn("PICK_SQUARE", src)

    def test_install_retries_once_after_permission(self):
        src = read("app/src/main/java/tv/corebuilds/iconpack/MainActivity.kt")
        self.assertIn("installOffered", src)
        self.assertIn("UpdateInstaller.canInstall", src)
        self.assertIn("startInstall(file)", src)


class ApplyTests(unittest.TestCase):
    def test_projectivy_contract_unchanged(self):
        src = read("app/src/main/java/tv/corebuilds/iconpack/ApplyIconPack.kt")
        self.assertIn("com.spocky.projengmenu.APPLY_ICONPACK", src)
        self.assertIn("com.spocky.projengmenu.extra.ICONPACK_PACKAGENAME", src)

    def test_monet_has_no_invented_apply_extra(self):
        src = read("app/src/main/java/tv/corebuilds/iconpack/ApplyIconPack.kt")
        self.assertIn("com.klevico.monet", src)
        self.assertIn("no incoming apply extra", src)
        self.assertNotRegex(
            src,
            r"com\.klevico\.monet\.APPLY_ICONPACK",
            "do not invent a Monet apply action — 1.0.76 has none",
        )


class MatchingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        data = json.loads((ROOT / "tools" / "catalog.json").read_text())
        cls.twitch = next(i for i in data["icons"] if i["drawable"] == "twitch")
        cls.af = (RES / "xml" / "appfilter.xml").read_text(encoding="utf-8")

    def test_twitch_maps_starshot(self):
        self.assertIn(
            "tv.twitch.android.app/tv.twitch.starshot64.app.StarshotActivity",
            self.twitch["components"],
        )

    def test_twitch_maps_tv_landing(self):
        self.assertIn(
            "tv.twitch.android.app/tv.twitch.android.apps.TVLandingActivity",
            self.twitch["components"],
        )

    def test_twitch_appfilter_points_at_banner(self):
        for comp in (
            "tv.twitch.android.app/tv.twitch.starshot64.app.StarshotActivity",
            "tv.twitch.android.app/tv.twitch.android.apps.TVLandingActivity",
        ):
            self.assertIn(
                f'ComponentInfo{{{comp}}}" drawable="twitch_banner"',
                self.af,
            )

    def test_appfilter_default_is_still_banners(self):
        root = ET.parse(RES / "xml" / "appfilter.xml").getroot()
        for item in root.findall("item"):
            d = item.get("drawable") or ""
            self.assertTrue(
                d.endswith("_banner"),
                f"{item.get('component')} maps to {d}, not a banner",
            )


class VersionAndCiTests(unittest.TestCase):
    def test_gradle_and_version_json_agree(self):
        gradle = read("app/build.gradle.kts")
        ver = json.loads(read("Latestrelease/version.json"))
        g_code = int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1))
        g_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
        # The two sources must agree (the updater would otherwise advertise a
        # build that doesn't exist). Compare against each other, not a pinned
        # number, so a version bump doesn't break this test — only a mismatch does.
        self.assertEqual(ver["versionCode"], g_code)
        self.assertEqual(ver["versionName"], g_name)
        self.assertEqual(ver["iconCount"], 920)
        self.assertEqual(
            ver["apkUrl"],
            "https://github.com/brevityA/CoreBuildsApps/releases/download/iconpack/iconpack-release.apk",
        )

    def test_ci_runs_build_banners(self):
        wf = read(".github/workflows/build.yml")
        self.assertIn("python tools/build_banners.py", wf)

    def test_iconpack_release_keeps_both_download_names(self):
        wf = read(".github/workflows/build.yml")
        self.assertIn("dist/iconpack-release.apk", wf)
        self.assertIn("dist/app-release.apk", wf)
        self.assertIn("git tag -f iconpack", wf)
        self.assertIn("tag_name: iconpack", wf)
        versioned = wf.split("- name: Publish versioned release", 1)[1]
        self.assertIn("make_latest: true", versioned.split("- name:", 1)[0])
        stable = wf.split("- name: Publish stable Downloader release", 1)[1]
        self.assertIn("make_latest: false", stable)

    def test_core_line_never_becomes_repository_latest(self):
        wf = read(".github/workflows/core-line-apk.yml")
        self.assertEqual(
            wf.count("make_latest: false"),
            2,
            "both Core Line versioned and floating releases must opt out of Latest",
        )
        self.assertNotIn("make_latest: true", wf)


if __name__ == "__main__":
    print(f"repo {ROOT}", file=sys.stderr)
    suite = unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__])
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
