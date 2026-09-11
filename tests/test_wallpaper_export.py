#!/usr/bin/env python3
"""
Contracts for the wallpaper export feature (v1.7.2).

Plain unittest over the repo tree, no Android SDK (mirrors the other test_*.py
files). Guards the wiring that makes "Export to Pictures for Monet rotation"
actually work: permissions, activities, strings, no bitmap re-encode, the
file-copy path, and the idempotency/space checks.
"""
from __future__ import annotations

import json
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

    def test_item_wallpaper_root_is_the_focus_target(self):
        """D-pad highlight and OK must land on the same view as the click
        listener (itemView). Nested focusable cards split those two, so the
        cyan ring never tracked the focused tile and centre-press did nothing.
        """
        ns = "{http://schemas.android.com/apk/res/android}"
        root = ET.fromstring(read("app/src/main/res/layout/item_wallpaper.xml"))
        self.assertEqual(root.get(ns + "focusable"), "true", "root must be focusable")
        self.assertEqual(root.get(ns + "clickable"), "true", "root must be clickable")
        self.assertEqual(root.get(ns + "background"), "@drawable/bg_card")
        self.assertEqual(root.get(ns + "descendantFocusability"), "blocksDescendants")
        nested = [
            el for el in root.iter()
            if el is not root and el.get(ns + "focusable") == "true"
        ]
        self.assertEqual(nested, [], "nested focusable views steal D-pad highlight")


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

    def test_wallpaper_chips_keep_focus_on_pick(self):
        """notifyDataSetChanged on a chip press drops D-pad highlight — the
        exact bug ChipAdapter already documents. Targeted notifyItemChanged
        keeps the focused chip attached.
        """
        src = self.files["WallpaperChipAdapter.kt"]
        no_block = re.sub(r"/\*.*?\*/", "", src, flags=re.DOTALL)
        code = "\n".join(line.split("//", 1)[0] for line in no_block.splitlines())
        self.assertNotIn("notifyDataSetChanged()", code)
        self.assertIn("notifyItemChanged", code)
        self.assertIn("isSelected", code)
        self.assertIn("KEYCODE_DPAD_LEFT", code)
        self.assertIn("wp_grid", code)

    def test_wallpaper_chip_rebind_is_payload_only(self):
        """A payload-less notifyItemChanged still triggers the default
        RecyclerView change animation: the pressed chip is detached and
        cross-faded with a fresh ViewHolder, dropping D-pad focus and its
        highlight on the press that moved it. The selection rebind must carry
        a payload so the focused ViewHolder is reused in place.
        """
        src = self.files["WallpaperChipAdapter.kt"]
        self.assertRegex(src, r"notifyItemChanged\([^,]+,\s*\w*SELECTION\w*\)")
        self.assertIn("onBindViewHolder(holder: VH, position: Int, payloads", src)

    def test_wallpaper_lists_disable_change_animations(self):
        """The main screen sets itemAnimator = null on its chip/grid lists for
        exactly this reason. The wallpaper screen must do the same for both
        the chip strip and the tile grid.
        """
        src = self.files["WallpapersActivity.kt"]
        self.assertGreaterEqual(src.count("itemAnimator = null"), 2)

    def test_wallpaper_screen_sets_initial_focus(self):
        """The main screen's own rule: without a deterministic initial target
        Android can leave focus on the decor view, so the wallpaper menu opens
        with no highlighted control and the first D-pad press does nothing.
        The first chip ("All") must take focus once it is laid out.
        """
        src = self.files["WallpapersActivity.kt"]
        self.assertIn("findViewByPosition(0)", src)
        self.assertRegex(src, r"\(firstChip \?[:] chips\)\.requestFocus\(\)")

    def test_wallpaper_tile_requests_item_focus(self):
        src = self.files["WallpaperAdapter.kt"]
        self.assertIn("itemView.isFocusable = true", src)
        self.assertIn("setOnFocusChangeListener", src)

    def test_preview_extracts_seed_palette(self):
        src = self.files["WallpaperPreviewActivity.kt"]
        self.assertIn("WallpaperSeed.from", src)
        self.assertIn("paintSeed", src)
        self.assertIn("seed_row", src)
        seed = (KT / "WallpaperSeed.kt").read_text(encoding="utf-8")
        self.assertIn("WallpaperColors.fromBitmap", seed)
        self.assertNotIn("com.google.android.material", seed)
        self.assertNotIn("DynamicColors", seed)

    def test_seed_chips_are_not_focusable(self):
        """Seed chips are a readout, not a control. A focusable chip would
        steal D-pad from Back/Save/Set the same way nested wallpaper tiles
        used to steal OK from the card root.
        """
        ns = "{http://schemas.android.com/apk/res/android}"
        xml = read("app/src/main/res/layout/activity_wallpaper_preview.xml")
        root = ET.fromstring(xml)
        self.assertIn("seed_row", xml)
        self.assertIn("wp_seed_label", xml)
        chips = []
        row = None
        for el in root.iter():
            vid = (el.get(ns + "id") or "").split("/")[-1]
            if vid == "seed_row":
                row = el
                self.assertEqual(el.get(ns + "descendantFocusability"),
                                 "blocksDescendants")
                self.assertEqual(el.get(ns + "visibility"), "gone")
            if vid.startswith("seed_") and vid[-1].isdigit():
                chips.append(el)
                self.assertNotEqual(el.get(ns + "focusable"), "true", vid)
                self.assertNotEqual(el.get(ns + "clickable"), "true", vid)
        self.assertEqual(len(chips), 5, "five seed swatches")
        self.assertIsNotNone(row)
        for key in (
            "wp_seed_label", "wp_seed_desc",
        ):
            self.assertRegex(
                read("app/src/main/res/values/strings.xml"),
                rf'<string name="{key}"',
            )
            self.assertRegex(
                read("pop/src/main/res/values/strings.xml"),
                rf'<string name="{key}"',
            )
            self.assertRegex(
                read("pixel-neon/app/src/main/res/values/strings.xml"),
                rf'<string name="{key}"',
            )

    def test_pixel_neon_preview_mirrors_seed_wiring(self):
        neon = ROOT / "pixel-neon/app/src/main"
        preview = (neon / "java/tv/corebuilds/pixelneon/WallpaperPreviewActivity.kt"
                   ).read_text(encoding="utf-8")
        self.assertIn("package tv.corebuilds.pixelneon", preview)
        self.assertIn("WallpaperSeed.from", preview)
        self.assertIn("seed_row", preview)
        seed = (neon / "java/tv/corebuilds/pixelneon/WallpaperSeed.kt"
                ).read_text(encoding="utf-8")
        self.assertIn("package tv.corebuilds.pixelneon", seed)
        self.assertIn("WallpaperColors.fromBitmap", seed)
        layout = (neon / "res/layout/activity_wallpaper_preview.xml").read_text()
        self.assertIn("seed_row", layout)
        self.assertIn("cb_seed_swatch", layout)
        dimens = (neon / "res/values/dimens.xml").read_text()
        self.assertIn("cb_seed_swatch", dimens)

    def test_wallpaper_tile_selection_keeps_focus(self):
        """Long-press selection toggles the focused tile. A payload-less
        notifyItemChanged there detaches the tile under the D-pad, so the
        focus ring and the selection frame can never show together.
        """
        src = self.files["WallpaperAdapter.kt"]
        self.assertRegex(
            src, re.compile(r"notifyItemChanged\(.*?SELECTION_PAYLOAD\s*\)", re.DOTALL)
        )
        self.assertIn("onBindViewHolder(holder: VH, position: Int, payloads", src)
        # Selection-mode sweeps reuse holders in place via payload too.
        self.assertIn("notifyItemRangeChanged(0, itemCount, SELECTION_PAYLOAD)", src)


class VersionTests(unittest.TestCase):
    """The pack still ships what this feature needs, and the version moves up.

    This used to pin `versionCode = 15` / `versionName = "1.8.2"` verbatim, which
    turned every release into a test edit and made the suite fail for the wrong
    reason — shipping a newer version is not a regression. What actually matters
    here is that the release carrying export is still the floor, so an APK built
    from this tree can do the thing under test. Gradle, suite.json, the catalog,
    and `Latestrelease/version.json` are kept in lockstep by
    `tools/check_suite_truth.py`, which is the right place for exact equality.
    """

    # v1.7.2 introduced multi-select export (CHANGELOG), versionCode 12.
    EXPORT_CODE = 12
    EXPORT_NAME = (1, 7, 2)

    def _version(self):
        gradle = read("app/build.gradle.kts")
        code = int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1))
        name = tuple(int(x) for x in
                     re.search(r'versionName\s*=\s*"([0-9.]+)"', gradle).group(1).split("."))
        return code, name

    def test_version_is_at_or_above_the_export_release(self):
        code, name = self._version()
        self.assertGreaterEqual(code, self.EXPORT_CODE,
                                "versionCode went below the release that shipped export")
        self.assertGreaterEqual(name, self.EXPORT_NAME,
                                "versionName went below the release that shipped export")

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
