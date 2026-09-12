#!/usr/bin/env python3
"""
Contracts for the Monet Launcher wallpaper hand-off (v1.8.15).

Ground truth is the decompiled Monet v1.0.84 APK (see
docs/research/monet-probe/ and docs/MONET_LAUNCHER.md):

  * Monet never reads the system wallpaper. Its themes come from its own
    background library, so `WallpaperManager.setStream` changes nothing on a
    Monet home screen.
  * Monet's exported `com.klevico.monet.WallpaperShareActivity` accepts
    ACTION_SEND / ACTION_SEND_MULTIPLE with `image/*`, reads EXTRA_STREAM
    and clipData, and needs FLAG_GRANT_READ_URI_PERMISSION on content URIs.
  * Monet has no incoming icon-pack apply intent and its settings activity is
    not exported. Icons stay a Manual apply.

These tests pin the wiring that makes the hand-off real in all three packs
(app/, pixel-neon/ and the generated pop/ mirror). Plain unittest, no SDK.
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main"
NEON = ROOT / "pixel-neon/app/src/main"
POP = ROOT / "pop/src/main"

MONET_PKG = "com.klevico.monet"
MONET_SHARE = "com.klevico.monet.WallpaperShareActivity"

NEW_STRINGS = (
    "wp_send_to_monet",
    "wp_send_n_to_monet",
    "wp_monet_share_failed",
    "wp_saved_monet_hint",
    "wp_after_export_hint_monet",
)


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


class SetterContractTests(unittest.TestCase):
    """WallpaperSetter must target Monet's real share activity, correctly."""

    def setUp(self):
        self.app = read(APP / "java/tv/corebuilds/iconpack/WallpaperSetter.kt")
        self.neon = read(NEON / "java/tv/corebuilds/pixelneon/WallpaperSetter.kt")

    def test_targets_the_decompiled_share_activity(self):
        for src in (self.app, self.neon):
            self.assertIn(f'MONET_PACKAGE = "{MONET_PKG}"', src)
            self.assertIn(f'MONET_SHARE_ACTIVITY = "{MONET_SHARE}"', src)

    def test_share_intent_is_explicit_send_with_grant(self):
        # Explicit component (no chooser on a remote), EXTRA_STREAM + clipData
        # (Monet reads both), and a read grant (Monet opens the URI itself).
        for src in (self.app, self.neon):
            body = src[src.index("fun monetShareIntent(uri: Uri"):]
            body = body[:body.index("fun monetShareIntent(uris")]
            self.assertIn("Intent.ACTION_SEND)", body)
            self.assertIn("setClassName(MONET_PACKAGE, MONET_SHARE_ACTIVITY)", body)
            self.assertIn("Intent.EXTRA_STREAM", body)
            self.assertIn("clipData = ClipData.newRawUri", body)
            self.assertIn("FLAG_GRANT_READ_URI_PERMISSION", body)

    def test_multi_share_uses_send_multiple(self):
        for src in (self.app, self.neon):
            body = src[src.index("fun monetShareIntent(uris"):]
            body = body[:body.index("Projectivy Launcher specific")]
            self.assertIn("Intent.ACTION_SEND_MULTIPLE", body)
            self.assertIn("putParcelableArrayListExtra(Intent.EXTRA_STREAM", body)
            self.assertIn("FLAG_GRANT_READ_URI_PERMISSION", body)

    def test_share_is_probed_not_assumed(self):
        # v1.0.72 added the share target; older Monet must not get a crash.
        for src in (self.app, self.neon):
            self.assertIn("fun canShareToMonet", src)
            self.assertIn("resolveActivity(probe, 0) != null", src)

    def test_content_uri_uses_the_pack_authority(self):
        # Two installed packs cannot share a FileProvider authority, so the
        # URI must come from each pack's own UpdateInstaller.AUTHORITY.
        for src in (self.app, self.neon):
            self.assertIn("FileProvider.getUriForFile(context, UpdateInstaller.AUTHORITY, file)", src)

    def test_share_does_not_go_through_wallpaper_manager(self):
        for src in (self.app, self.neon):
            body = src[src.index("// ---- Monet Launcher"):]
            body = body[:body.index("Projectivy Launcher specific")]
            self.assertNotIn("WallpaperManager", body)


class FileProviderTests(unittest.TestCase):
    """cache/wallpapers/ must be exported alongside cache/updates/ in every pack."""

    def _paths(self, res: Path):
        root = ET.parse(res / "xml/file_paths.xml").getroot()
        return {(e.tag, e.get("name"), e.get("path")) for e in root}

    def test_all_three_packs_export_wallpaper_cache(self):
        for res in (APP / "res", NEON / "res", POP / "res"):
            paths = self._paths(res)
            self.assertIn(("cache-path", "updates", "updates/"), paths, res)
            self.assertIn(("cache-path", "wallpapers", "wallpapers/"), paths, res)

    def test_provider_path_matches_downloader_cache_dir(self):
        src = read(APP / "java/tv/corebuilds/iconpack/WallpaperDownloader.kt")
        self.assertIn('File(context.cacheDir, "wallpapers")', src)

    def test_pop_mirror_is_current(self):
        self.assertEqual(
            read(APP / "res/xml/file_paths.xml"),
            read(POP / "res/xml/file_paths.xml"),
            "run python tools/build_pop.py",
        )


class PreviewFlowTests(unittest.TestCase):
    def test_set_routes_to_monet_when_monet_is_home(self):
        for p in (APP / "java/tv/corebuilds/iconpack/WallpaperPreviewActivity.kt",
                  NEON / "java/tv/corebuilds/pixelneon/WallpaperPreviewActivity.kt"):
            src = read(p)
            self.assertIn("monetIsHome", src)
            self.assertIn("WallpaperSetter.canShareToMonet(this)", src)
            # The branch runs before any WallpaperManager / permission work.
            on_set = src[src.index("private fun onSetClicked()"):src.index("private fun applyNow()")]
            self.assertLess(on_set.index("sendToMonet()"), on_set.index("requiredPermission()"))
            self.assertIn("R.string.wp_send_to_monet", src)

    def test_no_hardcoded_monet_settings_path_in_kotlin(self):
        # 1.0.80 moved settings around; UI copy lives in strings.xml only.
        for p in (APP / "java/tv/corebuilds/iconpack/WallpaperPreviewActivity.kt",
                  NEON / "java/tv/corebuilds/pixelneon/WallpaperPreviewActivity.kt"):
            self.assertNotIn("Your own images", read(p))


class ExportFlowTests(unittest.TestCase):
    def test_export_result_offers_send_to_monet(self):
        for p in (APP / "java/tv/corebuilds/iconpack/ExportProgressActivity.kt",
                  NEON / "java/tv/corebuilds/pixelneon/ExportProgressActivity.kt"):
            src = read(p)
            self.assertIn("WallpaperSetter.canShareToMonet(this)", src)
            self.assertIn("WallpaperSetter.monetShareIntent(uris)", src)
            self.assertIn("R.string.wp_send_n_to_monet", src)
            self.assertIn("R.string.wp_after_export_hint_monet", src)
            # Existing launcher row still works.
            self.assertIn("ApplyIconPack.installed", src)
            self.assertIn("openLauncher", src)


class StringTests(unittest.TestCase):
    def test_strings_exist_in_all_three_packs(self):
        for res in (APP / "res", NEON / "res", POP / "res"):
            s = read(res / "values/strings.xml")
            for key in NEW_STRINGS:
                self.assertRegex(s, rf'<string name="{key}"', f"{key} missing in {res}")

    def test_settings_path_matches_monet_1080_layout(self):
        # "Background → Sources" is the 1.0.80+ category; the old "Wallpaper"
        # category no longer exists in Monet.
        s = read(APP / "res/values/strings.xml")
        self.assertRegex(s, r'wp_saved_monet_hint">[^<]*Background → Sources')
        self.assertNotRegex(s, r'Monet Settings → Wallpaper')


class IconApplyTests(unittest.TestCase):
    def test_monet_icon_apply_stays_manual_with_current_path(self):
        for p in (APP / "java/tv/corebuilds/iconpack/ApplyIconPack.kt",
                  NEON / "java/tv/corebuilds/pixelneon/ApplyIconPack.kt"):
            src = read(p)
            self.assertIn("no incoming apply extra", src)
            self.assertIn("Monet 1.0.84", src)
            self.assertRegex(src, r'manualPath = "Monet Settings → Apps → Icon pack → ')
            self.assertNotRegex(src, r'manualPath = "Monet Settings → Icons')

    def test_manifest_still_queries_monet(self):
        for m in (APP / "AndroidManifest.xml", NEON / "AndroidManifest.xml", POP / "AndroidManifest.xml"):
            self.assertIn(f'<package android:name="{MONET_PKG}"', read(m), m)


class ProbeCleanupTests(unittest.TestCase):
    def test_temporary_probe_workflow_is_gone(self):
        self.assertFalse((ROOT / ".github/workflows/probe-monet.yml").exists(),
                         "the Monet APK probe workflow was research scaffolding")


if __name__ == "__main__":
    unittest.main()
