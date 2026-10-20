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
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
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
        self.assertEqual(names, {"about_network_one", "about_network_two",
                                 "about_update_label", "about_update_sub"})
        self.assertIn("Google Play", play)

    def test_play_build_has_no_route_to_a_tip(self):
        # Play's Payments policy: an app on Play may not lead users to a
        # payment method other than Play Billing, and 100% of a tip to one
        # developer is not a tax-exempt donation. Support is two QR codes for
        # GitHub Sponsors and Ko-fi, so every door to it is shut on Play -
        # the home rail, Settings, and About - or a hidden one is a rejection.
        main = read(KT / "MainActivity.kt")
        self.assertIn("findViewById<View>(R.id.support_entry).visibility = View.GONE", main)
        self.assertIn("if (Distribution.PLAY) {\n            findViewById<View>(R.id.support_entry)", main)
        settings = read(KT / "SettingsActivity.kt")
        self.assertIn("findViewById<View>(R.id.set_support_row).visibility = View.GONE", settings)
        self.assertIn("if (Distribution.PLAY) {\n            findViewById<View>(R.id.set_support_row)", settings)
        about = read(KT / "AboutActivity.kt")
        self.assertIn("findViewById<View>(R.id.about_support).visibility = View.GONE", about)
        self.assertIn("if (Distribution.PLAY) {\n            findViewById<View>(R.id.about_support)", about)
        # Every door to SupportActivity is wired (a press handler exists in all
        # three screens) and every one of them is shut on Play. A new door
        # added without its own Distribution.PLAY test shows up here as a
        # handler with no gate beside it.
        for name, anchor in (
            ("MainActivity.kt", "R.id.support_entry"),
            ("SettingsActivity.kt", "R.id.set_support_row"),
            ("AboutActivity.kt", "R.id.about_support"),
        ):
            src = read(KT / name)
            handler = src.split(f"startActivity(Intent(this, SupportActivity::class.java))", 1)[0]
            self.assertIn(anchor, handler, f"{name}: no press handler for {anchor}")
            self.assertIn(f"findViewById<View>({anchor}).visibility = View.GONE", src, name)

    def test_play_rail_does_not_focus_a_hidden_support_entry(self):
        # A GONE view that is focusable still takes the cursor (MainActivity
        # says so above syncFocusChain), so hiding Support is only half the
        # job: no D-pad edge may keep naming it. railHead is the first entry
        # that is still there.
        main = read(KT / "MainActivity.kt")
        self.assertIn(
            "if (Distribution.PLAY) R.id.settings_entry else R.id.support_entry", main)
        self.assertIn("findViewById<View>(R.id.settings_entry).setNextFocusLeftId(View.NO_ID)", main)
        self.assertIn("findViewById<View>(R.id.wallpapers_entry).setNextFocusDownId(railHead)", main)
        chain = main.split("private fun syncFocusChain()", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("if (groupShown) railHead else aboveRows", chain)
        self.assertNotIn("if (groupShown) R.id.support_entry else aboveRows", chain)

    def test_the_sends_list_names_the_icon_request(self):
        # About's list is a complete enumeration, not a summary: the missing-
        # icon report POSTs a device note (model, Android version, pack
        # version, launcher), and the list used to say "no device model"
        # while that was true. Play inherits item 3 unchanged, so the Data
        # safety form does not get a false declaration to copy.
        main = read(APP / "src/main/res/values/strings.xml")
        for item in ("about_network_one", "about_network_two", "about_network_three"):
            self.assertRegex(main, rf'<string name="{item}">[1-3]\.')
        self.assertIn("about_network_three", read(APP / "src/main/res/layout/activity_about.xml"))
        three = re.search(r'<string name="about_network_three">([^<]*)</string>', main).group(1)
        for fact in ("model", "Android version", "pack version", "launcher"):
            self.assertIn(fact, three)
        none = re.search(r'<string name="about_network_none">([^<]*)</string>', main).group(1)
        self.assertNotIn("device model", none)
        # The Play overlay replaces items 1 and 2 only; item 3 is the same
        # POST in both builds, so it is inherited from main by design.
        play = read(APP / "src/play/res/values/strings.xml")
        self.assertIn('name="about_network_one"', play)
        self.assertNotIn('name="about_network_three"', play)
        # ...and deviceNote() still sends exactly what item 3 lists.
        auditor = read(KT / "AuditorActivity.kt")
        note = auditor.split("private fun deviceNote(): String =", 1)[1].split("\n\n", 1)[0]
        for field in ("Build.MODEL", "Build.VERSION.RELEASE", "BuildConfig.VERSION_NAME",
                      "ApplyIconPack.detectDefault"):
            self.assertIn(field, note)

    def test_about_update_row_never_fetches_an_apk_on_play(self):
        # About's row is the second update path (the first is the home check
        # above). On Play it opens the listing instead: a Play app may not
        # download or install its own APK.
        about = read(KT / "AboutActivity.kt")
        self.assertIn("if (Distribution.PLAY) openPlayListing() else onUpdatePressed()", about)
        listing = about.split("private fun openPlayListing()", 1)[1].split("\n    }\n", 1)[0]
        self.assertNotIn("UpdateInstaller", listing)
        self.assertNotIn("UpdateChecker", listing)


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

    def test_glyphs_flavor_has_no_play_variant(self):
        # As a play variant the glyphs flavor would take the play build type's
        # FileProvider authority and collide with the Play icon pack on one TV.
        gradle = read(APP / "build.gradle.kts")
        self.assertIn('beforeVariants(selector().withBuildType("play").withFlavor("art" to "glyphs"))',
                      gradle)
        self.assertIn("it.enable = false", gradle.split("androidComponents {", 1)[1])

    def test_play_glyphs_has_one_tv_launcher_entry(self):
        # Play lists an app for Android TV only when it declares leanback and
        # has a LEANBACK_LAUNCHER entry. Since 2.4.4 the companion's own home
        # screen (.GlyphsHomeActivity) is that entry in every build, so the
        # Play build adds none: a second would put two tiles on the TV row.
        self.assertFalse((ROOT / "glyphs/src/play/AndroidManifest.xml").exists())
        root = ET.parse(ROOT / "glyphs/src/main/AndroidManifest.xml").getroot()
        tv_entries = [a.get(f"{ANDROID}name") for a in root.iter("activity")
                      for f in a.findall("intent-filter")
                      for c in f.findall("category")
                      if c.get(f"{ANDROID}name") == "android.intent.category.LEANBACK_LAUNCHER"]
        self.assertEqual(tv_entries, [".GlyphsHomeActivity"])
        self.assertTrue(any(f.get(f"{ANDROID}name") == "android.software.leanback"
                            for f in root.iter("uses-feature")))

    def test_play_workflow_is_its_own(self):
        play = read(ROOT / ".github/workflows/play.yml")
        self.assertIn(":app:bundleBannersPlay", play)
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
