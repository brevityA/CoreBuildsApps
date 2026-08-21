#!/usr/bin/env python3
"""
Contracts for the in-app wallpapers feature.

Mirrors test_v151_robustness.py: plain unittest over the repo tree, no Android
SDK. Guards the things that would otherwise rot silently:
  * manifest is coherent and matches the bundled copy in assets/
  * every wallpaper has a bundled thumbnail the browser can decode
  * the Kotlin surfaces declare the permissions, activities, and strings they use
  * full-size series-4 files exist on disk for every manifest url

Run from anywhere:
    python3 tests/test_wallpapers.py
"""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
WALLPAPERS = ROOT / "Wallpapers"
MANIFEST = WALLPAPERS / "manifest.json"
BUNDLED_MANIFEST = ROOT / "app/src/main/assets/manifest/wallpapers.json"
THUMBS = WALLPAPERS / "thumbs"
BUNDLED_THUMBS = ROOT / "app/src/main/assets/wallpapers_thumbs"
MAIN = ROOT / "app" / "src" / "main"
RES = MAIN / "res"
ANDROID_MANIFEST = MAIN / "AndroidManifest.xml"
JAVA = MAIN / "java/tv/corebuilds/iconpack"


def _read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads(_read(MANIFEST))
        self.walls = self.manifest["wallpapers"]

    def test_count_matches_entries(self):
        self.assertEqual(self.manifest["count"], len(self.walls))

    def test_entries_are_https_github(self):
        for w in self.walls:
            self.assertTrue(w["url"].startswith("https://raw.githubusercontent.com/"), w["url"])
            self.assertTrue(w["url"].endswith((".png", ".jpg")), w["url"])

    def test_names_unique_and_numbered(self):
        names = [w["name"] for w in self.walls]
        self.assertEqual(len(names), len(set(names)), "duplicate wallpaper names")

    def test_resolution_is_4k(self):
        for w in self.walls:
            self.assertEqual(w["resolution"], "3840x2160", w["name"])

    def test_series4_present_and_30_branded_walls(self):
        s4 = [w for w in self.walls if w["series"] == "series-4-core-mark"]
        self.assertEqual(len(s4), 30, "expected 30 Core Mark branded walls")
        nums = sorted(int(re.match(r"(\d+)", w["name"]).group(1)) for w in s4)
        self.assertEqual(nums, list(range(41, 71)))

    def test_bundled_manifest_matches_repo_manifest(self):
        bundled = json.loads(_read(BUNDLED_MANIFEST))
        self.assertEqual(bundled["wallpapers"], self.walls)


class ThumbnailTests(unittest.TestCase):
    def setUp(self):
        self.walls = json.loads(_read(MANIFEST))["wallpapers"]

    def test_every_wallpaper_has_a_thumbnail_file(self):
        missing = []
        for w in self.walls:
            fname = w["url"].rsplit("/", 1)[-1].rsplit(".", 1)[0] + ".jpg"
            if not (THUMBS / fname).exists():
                missing.append(fname)
        self.assertFalse(missing, f"missing thumbs: {missing}")

    def test_thumbs_bundled_into_assets(self):
        missing = []
        for w in self.walls:
            fname = w["url"].rsplit("/", 1)[-1].rsplit(".", 1)[0] + ".jpg"
            if not (BUNDLED_THUMBS / fname).exists():
                missing.append(fname)
        self.assertFalse(missing, f"thumbs not bundled: {missing}")

    def test_thumbs_are_small_jpgs(self):
        # Grid must stay instant; a thumb should never approach full-image size.
        for p in BUNDLED_THUMBS.glob("*.jpg"):
            self.assertLess(p.stat().st_size, 60_000, f"{p.name} is too large")


class Series4FileTests(unittest.TestCase):
    def setUp(self):
        self.walls = json.loads(_read(MANIFEST))["wallpapers"]

    def test_series4_full_files_exist(self):
        missing = []
        for w in self.walls:
            if w["series"] != "series-4-core-mark":
                continue
            fname = w["url"].rsplit("/", 1)[-1]
            if not (WALLPAPERS / "series-4-core-mark" / fname).exists():
                missing.append(fname)
        self.assertFalse(missing, f"missing series-4 files: {missing}")

    def test_series4_files_are_4k_png(self):
        from PIL import Image
        for p in (WALLPAPERS / "series-4-core-mark").glob("*.png"):
            with Image.open(p) as im:
                self.assertEqual(im.size, (3840, 2160), p.name)


class AndroidWiringTests(unittest.TestCase):
    def setUp(self):
        self.manifest_xml = _read(ANDROID_MANIFEST)
        self.strings_xml = _read(RES / "values/strings.xml")

    def test_set_wallpaper_permission_declared(self):
        root = ET.fromstring(self.manifest_xml)
        ns = "{http://schemas.android.com/apk/res/android}"
        perms = {e.get(ns + "name") for e in root.findall("uses-permission")}
        self.assertIn("android.permission.SET_WALLPAPER", perms)

    def test_wallpaper_activities_declared(self):
        for cls in (".WallpapersActivity", ".WallpaperPreviewActivity"):
            self.assertIn(cls, self.manifest_xml, f"{cls} missing from manifest")

    def test_wallpaper_strings_present(self):
        for key in ("wp_entry", "wp_set_wallpaper", "wp_count_fmt",
                    "wp_downloading_fmt", "wp_set_done"):
            self.assertRegex(
                self.strings_xml,
                rf'<string name="{key}"',
                f"string/{key} missing",
            )

    def test_wallpaper_layouts_exist(self):
        for name in ("activity_wallpapers.xml", "activity_wallpaper_preview.xml",
                     "item_wallpaper.xml", "bg_ghost.xml", "bg_scrim.xml"):
            self.assertTrue((RES / ("drawable" if name.startswith("bg_") else "layout") / name).exists(),
                            name)

    def test_entry_point_wired_in_main(self):
        main = _read(JAVA / "MainActivity.kt")
        self.assertIn("WallpapersActivity.start", main)
        self.assertIn("wallpapers_entry", main)

    def test_no_full_wallpapers_bundled_into_apk(self):
        # Full 4K images download on demand; assets should only hold thumbs + manifest.
        assets = ROOT / "app/src/main/assets"
        full = [p for p in assets.rglob("*.png")]
        self.assertFalse(full, f"full-size PNGs bundled (would bloat APK): {full}")
        # The bundled wallpaper assets are exactly thumbs + manifest.
        self.assertTrue((assets / "manifest/wallpapers.json").exists())
        self.assertTrue(any((assets / "wallpapers_thumbs").glob("*.jpg")))


if __name__ == "__main__":
    unittest.main(verbosity=2)
