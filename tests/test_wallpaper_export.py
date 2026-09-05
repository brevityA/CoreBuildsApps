#!/usr/bin/env python3
"""
Contracts for the wallpaper export feature (v1.7.2).

Plain unittest over the repo tree, no Android SDK (mirrors the other test_*.py
files). Guards the wiring that makes "Export to Pictures for Monet rotation"
actually work: permissions, activities, strings, no bitmap re-encode, the
file-copy path, and the idempotency/space checks.
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main"
RES = MAIN / "res"
KT = MAIN / "java/tv/corebuilds/iconpack"
MANIFEST = MAIN / "AndroidManifest.xml"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.root = ET.parse(MANIFEST).getroot()
        self.ns = "{http://schemas.android.com/apk/res/android}"
        self.xml = MANIFEST.read_text(encoding="utf-8")

    def test_write_storage_permission_scoped_to_api28(self):
        perms = {
            e.get(self.ns + "name"): e.get(self.ns + "maxSdkVersion")
            for e in self.root.findall("uses-permission")
        }
        self.assertIn("android.permission.WRITE_EXTERNAL_STORAGE", perms)
        self.assertEqual(perms["android.permission.WRITE_EXTERNAL_STORAGE"], "28")

    def test_set_wallpaper_permission_present(self):
        perms = {e.get(self.ns + "name") for e in self.root.findall("uses-permission")}
        self.assertIn("android.permission.SET_WALLPAPER", perms)

    def test_export_activity_declared_not_exported(self):
        acts = {
            a.get(self.ns + "name"): a.get(self.ns + "exported")
            for a in self.root.iter("activity")
        }
        self.assertIn(".ExportProgressActivity", acts)
        self.assertEqual(acts[".ExportProgressActivity"], "false")


class ResourceTests(unittest.TestCase):
    def test_required_strings_present(self):
        s = read("app/src/main/res/values/strings.xml")
        for key in (
            "wp_export", "wp_export_n_fmt", "wp_select_all", "wp_clear",
            "wp_save", "wp_save_done", "wp_saving",
            "wp_export_title", "wp_export_progress_fmt",
            "wp_export_done_fmt", "wp_export_failed_fmt", "wp_export_skipped_fmt",
            "wp_export_all_failed", "wp_after_export_hint", "wp_open_launcher",
            "wp_storage_permission", "wp_storage_permission_denied",
            "wp_selected_fmt", "wp_done",
        ):
            self.assertRegex(s, rf'<string name="{key}"', f"string/{key} missing")

    def test_selection_layouts_exist(self):
        for name in ("activity_export_progress.xml", "bg_wp_selected.xml"):
            self.assertTrue((RES / "layout" / name).exists() or
                            (RES / "drawable" / name).exists(), name)
        self.assertTrue((RES / "layout/activity_export_progress.xml").exists())
        self.assertTrue((RES / "drawable/bg_wp_selected.xml").exists())

    def test_item_wallpaper_has_selection_ring(self):
        xml = read("app/src/main/res/layout/item_wallpaper.xml")
        self.assertIn("wp_selected_ring", xml)


class KotlinWiringTests(unittest.TestCase):
    def setUp(self):
        self.files = {p.name: p.read_text(encoding="utf-8")
                      for p in KT.glob("Wallpaper*.kt")}
        self.files["ExportProgressActivity.kt"] = (
            KT / "ExportProgressActivity.kt").read_text(encoding="utf-8")
        self.files["WallpaperExporter.kt"] = (
            KT / "WallpaperExporter.kt").read_text(encoding="utf-8")

    def test_exporter_copies_files_not_bitmaps(self):
        # Must stream bytes via copyFileToPictures, never decode a Bitmap.
        src = self.files["WallpaperExporter.kt"]
        self.assertIn("copyFileToPictures", src)
        self.assertNotIn("BitmapFactory", src)
        self.assertNotIn("compress(", src)
        # The actual byte copy lives in WallpaperSetter.copyFileToPictures.
        setter = self.files["WallpaperSetter.kt"]
        self.assertIn("file.inputStream()", setter)
        self.assertRegex(setter, r"\.copyTo\(")

    def test_copy_file_helper_is_public_and_streaming(self):
        src = self.files["WallpaperSetter.kt"]
        self.assertRegex(src, r"fun\s+copyFileToPictures\s*\(")
        # Original-bytes copy uses inputStream().use { ... copyTo(...) }
        self.assertIn("file.inputStream()", src)
        self.assertIn("copyTo(out", src)

    def test_exporter_checks_free_space(self):
        self.assertIn("StatFs", self.files["WallpaperExporter.kt"])
        self.assertIn("availableBytes", self.files["WallpaperExporter.kt"])

    def test_exporter_reports_saved_skipped_failed_separately(self):
        src = self.files["WallpaperExporter.kt"]
        for field in ("saved", "skipped", "failed"):
            self.assertIn(field, src)
        # The result screen must surface a retry affordance for failures.
        strings = read("app/src/main/res/values/strings.xml")
        self.assertIn("wp_export_failed_fmt", strings)
        progress = self.files["ExportProgressActivity.kt"]
        self.assertIn("retry", progress)

    def test_exporter_uses_storage_permission_gate(self):
        src = self.files["WallpaperExporter.kt"]
        self.assertIn("hasStoragePermission", src)
        self.assertIn("NeedsStoragePermission", src)

    def test_already_exported_idempotency_check(self):
        self.assertIn("alreadyExported", self.files["WallpaperSetter.kt"])

    def test_preview_has_save_button_wiring(self):
        src = self.files["WallpaperPreviewActivity.kt"]
        self.assertIn("preview_save", src)
        self.assertIn("saveNow", src)
        self.assertIn("copyFileToPictures", src)

    def test_preview_destroys_safely(self):
        # Must detach the ImageView before recycling and guard callbacks.
        src = self.files["WallpaperPreviewActivity.kt"]
        self.assertIn("setImageDrawable(null)", src)
        self.assertIn("destroyed", src)
        self.assertIn("if (destroyed)", src)

    def test_downloader_coalesces_in_flight(self):
        src = self.files["WallpaperDownloader.kt"]
        self.assertIn("inFlight", src)
        self.assertIn("ConcurrentHashMap", src)
        self.assertIn(".part", src)  # atomic temp-file write

    def test_series_label_api21_safe(self):
        # CharSequence.titlecase() is API 24; it must not be CALLED.
        # Strip block (/* */) and line (//) comments before asserting.
        src = self.files["WallpaperCatalog.kt"]
        no_block = re.sub(r"/\*.*?\*/", "", src, flags=re.DOTALL)
        code = "\n".join(
            line.split("//", 1)[0] for line in no_block.splitlines()
        )
        self.assertNotIn(".titlecase(", code)
        self.assertIn("toUpperCase(Locale.ROOT)", code)

    def test_wallpaper_is_parcelable(self):
        src = self.files["WallpaperCatalog.kt"]
        self.assertIn("Parcelable", src)
        self.assertIn("writeToParcel", src)

    def test_export_progress_offers_installed_launchers(self):
        src = self.files["ExportProgressActivity.kt"]
        self.assertIn("ApplyIconPack.installed", src)
        self.assertIn("openLauncher", src)
        self.assertIn("getLaunchIntentForPackage", src)

    def test_browser_long_press_starts_selection(self):
        src = self.files["WallpapersActivity.kt"]
        self.assertIn("enterSelectionMode", src)
        self.assertIn("selectedItems", src)
        self.assertIn("ExportProgressActivity", src)

    def test_browser_back_exits_selection_first(self):
        src = self.files["WallpapersActivity.kt"]
        self.assertIn("selectionMode", src)
        # onBackPressed should exit selection instead of finishing when in mode.
        self.assertRegex(src, r"if\s*\(adapter\.selectionMode\)")


class VersionTests(unittest.TestCase):
    def test_version_bumped_to_182(self):
        gradle = read("app/build.gradle.kts")
        self.assertIn('versionCode = 15', gradle)
        self.assertIn('versionName = "1.8.2"', gradle)

    def test_version_json_matches_gradle(self):
        import json
        v = json.loads(read("Latestrelease/version.json"))
        gradle = read("app/build.gradle.kts")
        g_code = int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1))
        g_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
        self.assertEqual(v["versionCode"], g_code)
        self.assertEqual(v["versionName"], g_name)


if __name__ == "__main__":
    unittest.main(verbosity=2)
