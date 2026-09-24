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

These tests pin the wiring that makes the hand-off real in the icon pack.
Plain unittest, no SDK.
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main"

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
        self.src = read(APP / "java/tv/corebuilds/iconpack/WallpaperSetter.kt")

    def test_targets_the_decompiled_share_activity(self):
        self.assertIn(f'MONET_PACKAGE = "{MONET_PKG}"', self.src)
        self.assertIn(f'MONET_SHARE_ACTIVITY = "{MONET_SHARE}"', self.src)

    def test_share_intent_is_explicit_send_with_grant(self):
        # Explicit component (no chooser on a remote), EXTRA_STREAM + clipData
        # (Monet reads both), and a read grant (Monet opens the URI itself).
        src = self.src
        body = src[src.index("fun monetShareIntent(uri: Uri"):]
        body = body[:body.index("fun monetShareIntent(uris")]
        self.assertIn("Intent.ACTION_SEND)", body)
        self.assertIn("setClassName(MONET_PACKAGE, MONET_SHARE_ACTIVITY)", body)
        self.assertIn("Intent.EXTRA_STREAM", body)
        self.assertIn("clipData = ClipData.newRawUri", body)
        self.assertIn("FLAG_GRANT_READ_URI_PERMISSION", body)

    def test_multi_share_uses_send_multiple(self):
        src = self.src
        body = src[src.index("fun monetShareIntent(uris: List<Uri>)"):]
        body = body[:body.index("Projectivy Launcher specific")]
        self.assertIn("Intent.ACTION_SEND_MULTIPLE", body)
        self.assertIn("putParcelableArrayListExtra(Intent.EXTRA_STREAM", body)
        self.assertIn("FLAG_GRANT_READ_URI_PERMISSION", body)

    def test_share_is_probed_not_assumed(self):
        # v1.0.72 added the share target; older Monet must not get a crash.
        self.assertIn("fun canShareToMonet", self.src)
        self.assertIn("resolveActivity(probe, 0) != null", self.src)

    def test_content_uri_uses_the_pack_authority(self):
        self.assertIn("FileProvider.getUriForFile(context, UpdateInstaller.AUTHORITY, file)", self.src)

    def test_share_does_not_go_through_wallpaper_manager(self):
        body = self.src[self.src.index("// ---- Monet Launcher"):]
        body = body[:body.index("Projectivy Launcher specific")]
        self.assertNotIn("WallpaperManager", body)


class FileProviderTests(unittest.TestCase):
    """cache/wallpapers/ must be exported alongside cache/updates/."""

    def _paths(self, res: Path):
        root = ET.parse(res / "xml/file_paths.xml").getroot()
        return {(e.tag, e.get("name"), e.get("path")) for e in root}

    def test_pack_exports_wallpaper_cache(self):
        paths = self._paths(APP / "res")
        self.assertIn(("cache-path", "updates", "updates/"), paths)
        self.assertIn(("cache-path", "wallpapers", "wallpapers/"), paths)

    def test_provider_path_matches_downloader_cache_dir(self):
        src = read(APP / "java/tv/corebuilds/iconpack/WallpaperDownloader.kt")
        self.assertIn('File(context.cacheDir, "wallpapers")', src)


class PreviewFlowTests(unittest.TestCase):
    def test_set_routes_to_monet_when_monet_is_home(self):
        src = read(APP / "java/tv/corebuilds/iconpack/WallpaperPreviewActivity.kt")
        self.assertIn("monetIsHome", src)
        self.assertIn("WallpaperSetter.canShareToMonet(this)", src)
        # The branch runs before any WallpaperManager / permission work.
        on_set = src[src.index("private fun onSetClicked()"):src.index("private fun applyNow()")]
        self.assertLess(on_set.index("sendToMonet()"), on_set.index("requiredPermission()"))
        self.assertIn("R.string.wp_send_to_monet", src)

    def test_no_hardcoded_monet_settings_path_in_kotlin(self):
        # 1.0.80 moved settings around; UI copy lives in strings.xml only.
        src = read(APP / "java/tv/corebuilds/iconpack/WallpaperPreviewActivity.kt")
        self.assertNotIn("Your own images", src)


class ExportFlowTests(unittest.TestCase):
    def test_export_result_offers_send_to_monet(self):
        src = read(APP / "java/tv/corebuilds/iconpack/ExportProgressActivity.kt")
        self.assertIn("WallpaperSetter.canShareToMonet(this)", src)
        self.assertIn("WallpaperSetter.monetShareIntent(uris)", src)
        self.assertIn("R.string.wp_send_n_to_monet", src)
        self.assertIn("R.string.wp_after_export_hint_monet", src)
        # Existing launcher row still works.
        self.assertIn("ApplyIconPack.installed", src)
        self.assertIn("openLauncher", src)


class StringTests(unittest.TestCase):
    def test_strings_exist(self):
        s = read(APP / "res/values/strings.xml")
        for key in NEW_STRINGS:
            self.assertRegex(s, rf'<string name="{key}"', f"{key} missing")

    def test_settings_path_matches_monet_1080_layout(self):
        # "Background → Sources" is the 1.0.80+ category; the old "Wallpaper"
        # category no longer exists in Monet.
        s = read(APP / "res/values/strings.xml")
        self.assertRegex(s, r'wp_saved_monet_hint">[^<]*Background → Sources')
        self.assertNotRegex(s, r'Monet Settings → Wallpaper')


class IconApplyTests(unittest.TestCase):
    def test_monet_icon_apply_stays_manual_with_current_path(self):
        src = read(APP / "java/tv/corebuilds/iconpack/ApplyIconPack.kt")
        self.assertIn("no incoming apply extra", src)
        self.assertIn("Monet 1.0.84", src)
        self.assertRegex(src, r'manualPath = "Monet Settings → Apps → Icon pack → ')
        self.assertNotRegex(src, r'manualPath = "Monet Settings → Icons')

    def test_manifest_still_queries_monet(self):
        self.assertIn(f'<package android:name="{MONET_PKG}"', read(APP / "AndroidManifest.xml"))


class KotlinCommentTests(unittest.TestCase):
    """Kotlin block comments nest. A literal `image/*` inside a KDoc opens a
    second comment that swallows the rest of the file — WallpaperSetter.kt
    failed to compile exactly this way in CI (PR #120). No SDK here, so
    guard the shared Kotlin by hand."""

    @staticmethod
    def _nested_openers(text: str) -> list[int]:
        """Line numbers where `/*` appears inside an open block comment.
        Skips string literals and `//` line comments; good enough for the
        shared Kotlin, which has no raw strings containing comment tokens."""
        hits, in_block = [], False
        for n, line in enumerate(text.splitlines(), 1):
            i, in_str = 0, False
            while i < len(line):
                two = line[i:i + 2]
                if in_block:
                    if two == "*/":
                        in_block = False
                        i += 2
                        continue
                    if two == "/*":
                        hits.append(n)
                elif in_str:
                    if line[i] == "\\":
                        i += 2
                        continue
                    if line[i] == '"':
                        in_str = False
                elif line[i] == '"':
                    in_str = True
                elif two == "//":
                    break
                elif two == "/*":
                    in_block = True
                    i += 2
                    continue
                i += 1
        return hits

    def test_scanner_catches_the_pr120_shape(self):
        bad = "/**\n * accepts `image/*` shares\n */\nobject X"
        self.assertEqual(self._nested_openers(bad), [2])
        ok = 'fun f() = Intent().setType("image/*") /* trailing */'
        self.assertEqual(self._nested_openers(ok), [])

    def test_no_nested_block_comment_openers(self):
        offenders = []
        for kt in sorted((APP / "java/tv/corebuilds/iconpack").glob("*.kt")):
            for n in self._nested_openers(read(kt)):
                offenders.append(f"{kt.relative_to(ROOT)}:{n}")
        self.assertEqual(offenders, [], "nested /* inside a Kotlin block comment")


class ProbeCleanupTests(unittest.TestCase):
    def test_temporary_probe_workflow_is_gone(self):
        self.assertFalse((ROOT / ".github/workflows/probe-monet.yml").exists(),
                         "the Monet APK probe workflow was research scaffolding")


if __name__ == "__main__":
    unittest.main()
