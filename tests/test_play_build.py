#!/usr/bin/env python3
"""The Google Play build stays inside Play policy.

Play's Device and Network Abuse policy forbids an app distributed on Play from
installing APKs from anywhere but Play. The GitHub builds do exactly that
twice - the self-updater and the Core Builds Glyphs download - so the `play`
build type (app/build.gradle.kts) has to switch both off, and these tests pin
every part of that so a later change cannot quietly put one back.

The Play apps are also separate apps from the sideload ones: their own ids,
upload key and FileProvider authority, so neither can update the other.

Run: `python3 tests/test_play_build.py`
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
KT = APP / "src/main/java/tv/corebuilds/iconpack"


def read(p):
    return Path(p).read_text(encoding="utf-8")


def play_type(module):
    """The body of a module's `play` build type (not its signing config)."""
    gradle = read(ROOT / module / "build.gradle.kts")
    body = gradle.split('create("play") {\n            initWith', 1)[1]
    return body.split("\n        }\n", 1)[0]


class PlayBuild(unittest.TestCase):
    def test_play_build_type_exists_and_reports_play(self):
        gradle = read(APP / "build.gradle.kts")
        self.assertIn('buildConfigField("String", "DISTRIBUTION", "\\"github\\"")', gradle)
        play = play_type("app")
        self.assertIn('(getByName("release"))', play)
        self.assertIn('"DISTRIBUTION", "\\"play\\""', play)
        self.assertIn('"UPDATE_MANIFEST_URL", "\\"\\""', play)

    def test_play_manifest_removes_the_install_permission(self):
        manifest = read(APP / "src/play/AndroidManifest.xml")
        self.assertRegex(manifest, r'android:name="android\.permission\.REQUEST_INSTALL_PACKAGES"\s+'
                                   r'tools:node="remove"')
        # The GitHub build still needs it for its updater.
        self.assertIn("android.permission.REQUEST_INSTALL_PACKAGES",
                      read(APP / "src/main/AndroidManifest.xml"))

    def test_play_build_never_checks_github_for_updates(self):
        main = read(KT / "MainActivity.kt")
        self.assertIn("if (!Distribution.PLAY && !updateChecked && Prefs.updateChecks(this))", main)
        self.assertIn("if (Distribution.PLAY)", read(KT / "SettingsActivity.kt"))

    def test_play_build_sends_glyphs_to_its_play_listing(self):
        src = read(KT / "GlyphsCompanion.kt")
        ensure = src.split("fun ensure(", 1)[1]
        play = ensure.split("if (Distribution.PLAY) {", 1)[1].split("\n        }\n", 1)[0]
        self.assertIn("openPlayListing(activity, launcherKey)", play)
        self.assertIn("return", play)
        # ...before any download path is reached.
        self.assertLess(ensure.index("if (Distribution.PLAY)"),
                        ensure.index("UpdateInstaller.downloadCompanion"))
        self.assertIn('"market://details?id=$pkg"', read(KT / "Distribution.kt"))

    def test_play_companion_trust(self):
        src = read(KT / "GlyphsCompanion.kt")
        ready = src.split("fun ready(", 1)[1].split("\n    }\n", 1)[0]
        # Same certificate first; the Play-installer fallback only on Play.
        self.assertIn("SIGNATURE_MATCH", ready)
        self.assertIn("Distribution.PLAY && installedByPlay(context)", ready)
        self.assertIn('PLAY_STORE = "com.android.vending"', read(KT / "Distribution.kt"))

    def test_play_build_says_what_it_sends(self):
        play = read(APP / "src/play/res/values/strings.xml")
        names = set(re.findall(r'<string name="([^"]+)"', play))
        self.assertEqual(names, {"about_network_one", "about_network_two"})
        self.assertIn("Google Play", play)


class SeparateApps(unittest.TestCase):
    def test_both_play_apps_have_their_own_ids(self):
        for module in ("app", "glyphs"):
            self.assertIn('applicationIdSuffix = ".play"', play_type(module), module)
        app = play_type("app")
        self.assertIn('"GLYPHS_PACKAGE", "\\"tv.corebuilds.iconpack.glyphs.play\\""', app)
        self.assertIn('manifestPlaceholders["glyphsPackage"] = "tv.corebuilds.iconpack.glyphs.play"', app)
        self.assertIn('manifestPlaceholders["iconPackId"] = "tv.corebuilds.iconpack.play"',
                      play_type("glyphs"))

    def test_play_app_has_its_own_file_provider(self):
        # Two apps cannot install side by side with one provider authority.
        app = play_type("app")
        self.assertIn('"UPDATE_AUTHORITY", "\\"tv.corebuilds.iconpack.play.update\\""', app)
        self.assertIn('manifestPlaceholders["fileProviderAuthority"] = "tv.corebuilds.iconpack.play.update"', app)

    def test_play_apps_sign_with_the_play_upload_key_only(self):
        for module in ("app", "glyphs"):
            play = play_type(module)
            self.assertIn('System.getenv("PLAY_KEYSTORE_PATH")', play, module)
            self.assertIn('signingConfigs.getByName("play")', play, module)
            self.assertNotIn('signingConfigs.getByName("release")', play, module)
            gradle = read(ROOT / module / "build.gradle.kts")
            signing = gradle.split("signingConfigs {", 1)[1].split('create("release")', 1)[0]
            self.assertIn('System.getenv("PLAY_KEY_ALIAS")', signing, module)

    def test_play_glyphs_has_a_tv_launcher_entry(self):
        manifest = read(ROOT / "glyphs/src/play/AndroidManifest.xml")
        self.assertIn("android.software.leanback", manifest)
        self.assertIn("android.intent.category.LEANBACK_LAUNCHER", manifest)
        # The sideload companion stays hidden from the launcher.
        self.assertNotIn("android.intent.category.LEANBACK_LAUNCHER",
                         read(ROOT / "glyphs/src/main/AndroidManifest.xml"))

    def test_play_workflow_is_its_own(self):
        play = read(ROOT / ".github/workflows/play.yml")
        self.assertIn(":app:bundlePlay", play)
        self.assertIn(":glyphs:bundlePlay", play)
        self.assertIn("PLAY_KEYSTORE_BASE64", play)
        # Hand-run only: a tag prefix is a suite.json release contract
        # (tools/check_suite_truth.py), and a Play build cuts no release.
        self.assertNotIn("tags:", play)
        build = read(ROOT / ".github/workflows/build.yml")
        self.assertNotIn("bundlePlay", build)
        self.assertNotIn("PLAY_KEYSTORE", build)


if __name__ == "__main__":
    unittest.main()
