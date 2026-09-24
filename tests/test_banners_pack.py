#!/usr/bin/env python3
"""Core Builds Banners: the companion pack behind the Glyphs/Banners toggle.

The toggle works by telling a launcher to apply a different *package*, so it
only works while four separately edited things agree: the companion's
generated resources, its manifest, the icon pack's code that names it, and the
release workflow that publishes it under the filename the code downloads. Each
test below pins one of those joins; none of them needs an Android SDK.

Run: python tests/test_banners_pack.py
"""
from __future__ import annotations

import re
import subprocess
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"

APP_MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
PACK_MANIFEST = ROOT / "banners/src/main/AndroidManifest.xml"
APP_GRADLE = ROOT / "app/build.gradle.kts"
PACK_GRADLE = ROOT / "banners/build.gradle.kts"
COMPANION_KT = ROOT / "app/src/main/java/tv/corebuilds/iconpack/BannersCompanion.kt"
ACTIVITY_JAVA = (ROOT / "banners/src/main/java/tv/corebuilds/iconpack/banners/"
                 "BannersActivity.java")
BUILD_YML = ROOT / ".github/workflows/build.yml"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def components(appfilter: Path) -> list[str]:
    return re.findall(r'<item component="([^"]+)"', read(appfilter))


def pack_filters(manifest: Path) -> set[tuple[str, frozenset[str]]]:
    """(action, categories) of every intent filter except the launcher entry."""
    out = set()
    for activity in ET.parse(manifest).getroot().iter("activity"):
        for f in activity.findall("intent-filter"):
            actions = [a.get(f"{ANDROID}name") for a in f.findall("action")]
            cats = frozenset(c.get(f"{ANDROID}name") for c in f.findall("category"))
            for action in actions:
                if action == "android.intent.action.MAIN":
                    continue
                out.add((action, cats))
    return out


class GeneratedResources(unittest.TestCase):
    def test_generator_is_in_sync(self):
        result = subprocess.run(
            [sys.executable, str(ROOT / "tools/build_banners_pack.py"), "--check"],
            capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_same_components_as_the_glyph_pack(self):
        # The whole promise of the toggle: switching style never changes which
        # apps get an icon, only what the icon looks like.
        glyphs = components(ROOT / "app/src/main/res/xml/appfilter.xml")
        banners = components(ROOT / "banners/src/main/res/xml/appfilter.xml")
        self.assertEqual(glyphs, banners)
        self.assertGreater(len(banners), 1000)

    def test_every_item_is_a_banner(self):
        text = read(ROOT / "banners/src/main/res/xml/appfilter.xml")
        drawables = re.findall(r'<item component="[^"]+" drawable="([^"]+)"', text)
        self.assertTrue(drawables)
        self.assertEqual([d for d in drawables if not d.endswith("_banner")], [])

    def test_assets_copy_matches(self):
        self.assertEqual(read(ROOT / "banners/src/main/res/xml/appfilter.xml"),
                         read(ROOT / "banners/src/main/assets/appfilter.xml"))

    def test_build_copies_every_drawable_it_names(self):
        # The art is copied at build time, not committed; an include glob
        # that misses a family would link but leave launchers resolving
        # nothing.
        gradle = read(PACK_GRADLE)
        for needed in ('"*_banner.webp"', '"cb_back_*.webp"', '"cb_mask.webp"',
                       '"cb_upon.webp"', '"cb_banner.png"', '"mipmap-*/**"',
                       '"banner_aliases.xml"'):
            self.assertIn(needed, gradle)


class OneStylePerPack(unittest.TestCase):
    """Glyphs only from the glyph pack, banners only from the companion.

    The toggle picks a package, so each package has to hold one art style on
    every surface a launcher reads: the appfilter it auto-applies, the
    drawable.xml its icon browser lists, and the picker it opens.
    """
    MAIN_KT = ROOT / "app/src/main/java/tv/corebuilds/iconpack/MainActivity.kt"

    def browser(self, module: str) -> list[str]:
        return re.findall(r'<item drawable="([^"]+)" />',
                          read(ROOT / f"{module}/src/main/res/xml/drawable.xml"))

    def test_glyph_pack_browser_lists_glyphs_only(self):
        glyphs = self.browser("app")
        self.assertGreater(len(glyphs), 900)
        self.assertEqual([d for d in glyphs if d.endswith("_banner")], [])
        self.assertEqual(read(ROOT / "app/src/main/res/xml/drawable.xml"),
                         read(ROOT / "app/src/main/assets/drawable.xml"))

    def test_banners_browser_lists_the_same_icons_as_banners(self):
        glyphs = self.browser("app")
        self.assertEqual(self.browser("banners"), [f"{d}_banner" for d in glyphs])
        titles = re.findall(r'<category title="([^"]+)" />',
                            read(ROOT / "banners/src/main/res/xml/drawable.xml"))
        self.assertTrue(titles)
        self.assertTrue(all(t.startswith("Banners \u00b7 ") for t in titles), titles)

    def test_glyph_appfilter_maps_glyphs_only(self):
        text = read(ROOT / "app/src/main/res/xml/appfilter.xml")
        drawables = re.findall(r'<item component="[^"]+" drawable="([^"]+)"', text)
        self.assertEqual([d for d in drawables if d.endswith("_banner")], [])

    def test_forwarded_picks_are_marked_banners(self):
        extra = re.search(r'const val EXTRA_PICK_BANNERS = "([^"]+)"',
                          read(COMPANION_KT)).group(1)
        java = read(ACTIVITY_JAVA)
        self.assertIn(f'EXTRA_PICK_BANNERS = "{extra}"', java)
        forward = java.split("if (isPickRequest(in))", 1)[1].split("} else {", 1)[0]
        self.assertIn("putExtra(EXTRA_PICK_BANNERS, true)", forward)

    def test_picker_shape_comes_from_the_pack_not_the_toggle(self):
        src = read(self.MAIN_KT)
        self.assertIn("fun pickFixedByPack(): Boolean = pickMode && BannersCompanion.supported()",
                      src)
        self.assertIn("intent.getBooleanExtra(BannersCompanion.EXTRA_PICK_BANNERS, false)", src)
        # With a companion the picker shows no shape chips, so no pick can
        # write the art style the whole launcher applies.
        chips = src.split("private fun bindPickShape()", 1)[1]
        self.assertLess(chips.index("if (pickFixedByPack())"),
                        chips.index("Prefs.set(this, Prefs.KEY_PICK_BANNERS"))
        sync = src.split("private fun syncArtStyle()", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("if (pickFixedByPack()) return", sync)

    def test_switch_never_claims_banners_the_launcher_lacks(self):
        src = read(COMPANION_KT)
        ensure = src.split("fun ensure(", 1)[1].split("\n    }\n", 1)[0]
        # Every way ensure() can fail to deliver the pack reverts the style.
        self.assertEqual(ensure.count("revertToGlyphs(activity)"), 3, ensure)
        self.assertEqual(ensure.count("onUnavailable()"), 3, ensure)
        # A superseded download must not install or revert over a newer one.
        self.assertIn("if (generation != ensureGeneration) return@downloadCompanion", ensure)
        # Only the installer's own result can call an install declined; a
        # resume that arrives before the package lands just waits.
        result = src.split("fun onInstallResult(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("Pending.Declined", result)
        self.assertIn("revertToGlyphs(context)", result)
        take = src.split("fun takePendingApply(", 1)[1].split("\n    }\n", 1)[0]
        self.assertNotIn("revertToGlyphs", take)
        self.assertNotIn("Pending.Declined", take)
        installer = read(ROOT / "app/src/main/java/tv/corebuilds/iconpack/UpdateInstaller.kt")
        for_result = installer.split("fun installForResult(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("putExtra(Intent.EXTRA_RETURN_RESULT, true)", for_result)
        self.assertIn("startActivityForResult(intent, requestCode)", for_result)
        for activity in ("MainActivity.kt", "SettingsActivity.kt"):
            src_a = read(ROOT / f"app/src/main/java/tv/corebuilds/iconpack/{activity}")
            self.assertIn("BannersCompanion.onInstallResult(this)", src_a, activity)
        settings = read(ROOT / "app/src/main/java/tv/corebuilds/iconpack/SettingsActivity.kt")
        resume = settings.split("override fun onResume()", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("bannerSwitch.isChecked = Prefs.pickerPrefersBanners(this)", resume)


class Manifest(unittest.TestCase):
    def test_discovered_by_the_same_launchers(self):
        self.assertEqual(pack_filters(PACK_MANIFEST), pack_filters(APP_MANIFEST))

    def test_no_launcher_entry(self):
        # One app for the user: the companion must not appear in the drawer.
        text = read(PACK_MANIFEST)
        self.assertNotIn("android.intent.category.LAUNCHER", text)
        self.assertNotIn("android.intent.category.LEANBACK_LAUNCHER", text)
        self.assertNotIn("android.intent.action.MAIN", text)

    def test_each_side_can_see_the_other(self):
        self.assertIn('<package android:name="tv.corebuilds.iconpack" />',
                      read(PACK_MANIFEST))
        self.assertIn('<package android:name="tv.corebuilds.iconpack.banners" />',
                      read(APP_MANIFEST))


class Contracts(unittest.TestCase):
    def test_package_name_agrees_everywhere(self):
        pack_id = re.search(r'applicationId = "([^"]+)"', read(PACK_GRADLE)).group(1)
        self.assertEqual(pack_id, "tv.corebuilds.iconpack.banners")
        self.assertIn(f'"\\"{pack_id}\\""', read(APP_GRADLE))

    def test_forward_targets_the_glyph_pack(self):
        app_id = re.search(r'applicationId = "([^"]+)"', read(APP_GRADLE)).group(1)
        self.assertIn(f'GLYPH_PACK = "{app_id}"', read(ACTIVITY_JAVA))

    def test_builds_without_a_companion_offer_none(self):
        # The debug-signed candidate build ships no companion APK, so an
        # empty package is what keeps its toggle in-app.
        candidate = read(APP_GRADLE).split('create("candidate")', 1)[1].split("}", 1)[0]
        self.assertIn('"BANNERS_PACKAGE", "\\"\\""', candidate)

    def test_version_comes_from_the_icon_pack(self):
        gradle = read(PACK_GRADLE)
        self.assertIn('rootProject.file("app/build.gradle.kts")', gradle)
        self.assertIn("versionCode = packVersionCode", gradle)
        self.assertIn("versionName = packVersionName", gradle)

    def test_release_publishes_the_asset_the_app_downloads(self):
        asset = re.search(r'const val ASSET = "([^"]+)"', read(COMPANION_KT)).group(1)
        self.assertIn('/v${BuildConfig.VERSION_NAME}/$ASSET', read(COMPANION_KT))
        yml = read(BUILD_YML)
        self.assertIn(f"dist/{asset}", yml)
        versioned = yml.split("Publish versioned release", 1)[1].split("- name:", 1)[0]
        self.assertIn(f"dist/{asset}", versioned)

    def test_companion_is_held_to_the_updater_bar(self):
        src = read(ROOT / "app/src/main/java/tv/corebuilds/iconpack/UpdateInstaller.kt")
        body = src.split("fun downloadCompanion(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("verifyDownloadedApk(app, file, packageName", body)
        self.assertIn("it == versionCode", body)
        # The signature check compares against the *installed app's* certs.
        self.assertIn("pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)",
                      src)


if __name__ == "__main__":
    unittest.main()
