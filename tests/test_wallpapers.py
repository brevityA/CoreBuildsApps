#!/usr/bin/env python3
"""
Contracts for the in-app wallpapers feature.

Mirrors test_v151_robustness.py: plain unittest over the repo tree, no Android
SDK. Guards the things that would otherwise rot silently:
  * manifest is coherent and matches the bundled copy in assets/
  * every wallpaper has a bundled thumbnail the browser can decode
  * the Kotlin surfaces declare the permissions, activities, and strings they use
  * full-size series-6 files exist on disk for every manifest url, and every
    entry's declared resolution is the size the file actually is
  * a retired series is really gone (no orphaned files, no orphaned entries)

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
SERIES6 = "series-6-circuit-core"
# Series that intentionally live outside the classic manifest: series 0 is the
# photographic originals kept for history, series 5-pop is Core Builds Pop's
# own collection, indexed by Wallpapers/pop-manifest.json.
UNMANIFESTED = {"series-0-originals", "series-5-pop"}
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

    def test_resolution_matches_the_file_on_disk(self):
        """The manifest promises a size; the bytes must be that size.

        The collection is no longer uniformly 4K — series 5 ships at the
        resolution its sources were authored at, because upscaling a 1376x768
        JPEG into a 3840x2160 filename would be a lie the in-app browser then
        repeats to every TV. So the gate is honesty, not a fixed number.
        """
        from PIL import Image
        for w in self.walls:
            f = WALLPAPERS / w["series"] / w["url"].rsplit("/", 1)[-1]
            self.assertTrue(f.exists(), f"no file for {w['name']}: {f}")
            with Image.open(f) as im:
                self.assertEqual(w["resolution"], f"{im.width}x{im.height}",
                                 f"{f.name} is {im.width}x{im.height}, manifest says {w['resolution']}")

    def test_series6_present_and_10_branded_walls(self):
        s5 = [w for w in self.walls if w["series"] == SERIES6]
        self.assertEqual(len(s5), 10, "expected 10 Circuit Core walls")
        nums = sorted(int(re.match(r"(\d+)", w["name"]).group(1)) for w in s5)
        self.assertEqual(nums, list(range(41, 51)))

    def test_retired_series4_is_not_in_the_manifest(self):
        # v1.8.6 replaced the 4K Core Mark series with series 5. The files and
        # the thumbs go with it — a manifest entry with no file is a 404 in the
        # app, and a file with no manifest entry is dead weight in the repo.
        self.assertFalse([w for w in self.walls if w["series"] == "series-4-core-mark"])

    def test_no_orphaned_series_on_disk(self):
        on_disk = {d.name for d in (ROOT / "Wallpapers").iterdir()
                   if d.is_dir() and d.name.startswith("series-")}
        in_manifest = {w["series"] for w in self.walls}
        self.assertEqual(on_disk - in_manifest, UNMANIFESTED,
                         "series directories the manifest does not index")
        self.assertEqual(in_manifest - on_disk, set(),
                         "manifest series with no directory on disk")

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

    def test_bundled_thumbs_are_exactly_the_manifest_set(self):
        # 20 KB of APK per stray thumb; the count is the collection's size.
        want = {w["url"].rsplit("/", 1)[-1].rsplit(".", 1)[0] + ".jpg"
                for w in self.walls}
        have = {p.name for p in BUNDLED_THUMBS.glob("*.jpg")}
        self.assertEqual(have, want)


class Series6FileTests(unittest.TestCase):
    def setUp(self):
        self.walls = json.loads(_read(MANIFEST))["wallpapers"]

    def test_series6_full_files_exist(self):
        missing = []
        for w in self.walls:
            if w["series"] != SERIES6:
                continue
            fname = w["url"].rsplit("/", 1)[-1]
            if not (WALLPAPERS / SERIES6 / fname).exists():
                missing.append(fname)
        self.assertFalse(missing, f"missing series-6 files: {missing}")

    def test_series6_files_are_tv_aspect(self):
        """16:9 (tolerant of a sub-1% rounding gap) so the system setter crops nothing meaningful."""
        from PIL import Image
        for p in sorted((WALLPAPERS / SERIES6).glob("*.jpg")):
            with Image.open(p) as im:
                self.assertAlmostEqual(
                    im.width / im.height, 16 / 9, delta=0.02,
                    msg=f"{p.name} is {im.width}x{im.height}, not a TV frame")

    def test_no_series4_files_left_behind(self):
        self.assertFalse((WALLPAPERS / "series-4-core-mark").exists(),
                         "retired series-4 files are still committed")


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
        self.assertIn("WallpapersActivity", main)
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
