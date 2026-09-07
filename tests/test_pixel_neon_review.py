#!/usr/bin/env python3
"""Regression contracts for the Pixel Neon review fixes.

These source-level checks complement Android lint/build in CI. They cover the
cross-file contracts that are easy to compile successfully while still breaking
release reproducibility, legacy URI sharing, TV focus, or generated-asset drift.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PIXEL = ROOT / "pixel-neon/app/src/main"
JAVA = PIXEL / "java/tv/corebuilds/pixelneon"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def view_by_id(layout: str, name: str) -> ET.Element:
    root = ET.parse(PIXEL / "res/layout" / layout).getroot()
    wanted = f"@+id/{name}"
    for element in root.iter():
        if element.get(ANDROID + "id") == wanted:
            return element
    raise AssertionError(f"{name} missing from {layout}")


class AndroidIntegrationContracts(unittest.TestCase):
    def test_main_focus_routes_through_wallpapers(self) -> None:
        apply = view_by_id("activity_main.xml", "apply_button")
        chips = view_by_id("activity_main.xml", "chip_row")
        self.assertEqual(
            apply.get(ANDROID + "nextFocusDown"), "@id/wallpapers_entry"
        )
        self.assertEqual(
            chips.get(ANDROID + "nextFocusUp"), "@id/wallpapers_entry"
        )

    def test_wallpaper_header_does_not_skip_selection_bar(self) -> None:
        for view_id in ("wp_export", "wp_back"):
            view = view_by_id("activity_wallpapers.xml", view_id)
            self.assertIsNone(view.get(ANDROID + "nextFocusDown"))

    def test_legacy_wallpaper_uri_is_scanned_and_grantable(self) -> None:
        paths = ET.parse(PIXEL / "res/xml/file_paths.xml").getroot()
        external = paths.find("external-path")
        self.assertIsNotNone(external)
        self.assertEqual(external.get("path"), "Pictures/CoreBuilds/")

        setter = (JAVA / "WallpaperSetter.kt").read_text(encoding="utf-8")
        self.assertIn("MediaScannerConnection.scanFile", setter)
        self.assertIn("FileProvider.getUriForFile", setter)
        self.assertIn("ClipData.newRawUri", setter)
        self.assertNotIn("Uri.fromFile", setter)

    def test_picker_returns_only_one_bounded_bitmap_extra(self) -> None:
        picker = (JAVA / "IconPicker.kt").read_text(encoding="utf-8")
        self.assertEqual(
            picker.count("result.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)"), 1
        )
        self.assertNotIn('result.putExtra("icon", bitmap)', picker)
        self.assertIn("MAX_RESULT_PX", picker)

    def test_export_permission_path_is_terminal_and_cancellable(self) -> None:
        exporter = (JAVA / "WallpaperExporter.kt").read_text(encoding="utf-8")
        self.assertIn("cancelled: AtomicBoolean", exporter)
        self.assertIn("CANCEL_POLL_MILLIS", exporter)
        self.assertIn("WallpaperDownloader.MAX_CACHE_FILES", exporter)
        self.assertIn("getExternalFilesDir(Environment.DIRECTORY_PICTURES)", exporter)
        permission_post = exporter.index(
            "listener.onEvent(Event.NeedsStoragePermission)"
        )
        terminal_post = exporter.index("listener.onEvent(Event.Done", permission_post)
        self.assertGreater(terminal_post, permission_post)

    def test_single_save_is_idempotent_and_projectivy_has_no_bang_bang(self) -> None:
        preview = (JAVA / "WallpaperPreviewActivity.kt").read_text(encoding="utf-8")
        self.assertIn("WallpaperSetter.alreadyExported", preview)
        self.assertIn("shareWithProjectivy(file)", preview)
        self.assertNotIn("downloaded!!", preview)


class GenerationAndReleaseContracts(unittest.TestCase):
    def test_generated_asset_gate_includes_untracked_rasters(self) -> None:
        workflow = read(".github/workflows/pixel-neon-apk.yml")
        self.assertIn("--untracked-files=all", workflow)
        self.assertNotIn("grep -v '^?? '", workflow)
        for generated in (
            "pixel-neon/app/src/main/res/drawable-nodpi",
            "pixel-neon/app/src/main/res/mipmap-anydpi-v26",
            "pixel-neon/app/src/main/res/mipmap-xhdpi",
            "pixel-neon/app/src/main/res/mipmap-xxhdpi",
        ):
            self.assertIn(generated, workflow)

    def test_legacy_launcher_resize_is_nearest_neighbour(self) -> None:
        generator = read("tools/build_pixel_neon.py")
        self.assertIn(
            "cabinet.resize((size, size), Image.Resampling.NEAREST)", generator
        )

    def test_release_metadata_preserves_explicit_date(self) -> None:
        release_workflow = read(".github/workflows/suite-release.yml")
        self.assertIn('--release-date "$RELEASE_DATE"', release_workflow)

        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            apk = temp / "pixel-neon-release.apk"
            apk.write_bytes(b"deterministic test apk")
            outputs = [temp / "first.json", temp / "second.json"]
            for output in outputs:
                subprocess.run(
                    [
                        sys.executable,
                        "tools/generate_release_metadata.py",
                        "pixelneon",
                        "--apk",
                        str(apk),
                        "--release-date",
                        "2026-09-07",
                        "--out",
                        str(output),
                    ],
                    cwd=ROOT,
                    check=True,
                    capture_output=True,
                    text=True,
                )

            self.assertEqual(outputs[0].read_bytes(), outputs[1].read_bytes())
            metadata = json.loads(outputs[0].read_text(encoding="utf-8"))
            self.assertEqual(metadata["releaseDate"], "2026-09-07")
            self.assertEqual(
                metadata["apkSha256"], hashlib.sha256(apk.read_bytes()).hexdigest()
            )

    def test_release_metadata_rejects_implicit_or_invalid_date(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            apk.write_bytes(b"apk")
            base = [
                sys.executable,
                "tools/generate_release_metadata.py",
                "pixelneon",
                "--apk",
                str(apk),
            ]
            missing = subprocess.run(base, cwd=ROOT, capture_output=True, text=True)
            invalid = subprocess.run(
                base + ["--release-date", "2026-02-30"],
                cwd=ROOT,
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(missing.returncode, 0)
            self.assertNotEqual(invalid.returncode, 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
