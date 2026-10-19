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

    def test_play_apps_are_tv_only(self):
        # Play holds an app that phones can install to the phone target API
        # (36 from 2026-08-31); an Android TV app may target 34, which is
        # what this one targets. Optional leanback would make it a phone app.
        for module in ("app", "glyphs"):
            root = ET.parse(ROOT / module / "src/play/AndroidManifest.xml").getroot()
            leanback = [f for f in root.iter("uses-feature")
                        if f.get(f"{ANDROID}name") == "android.software.leanback"]
            self.assertEqual(len(leanback), 1, module)
            self.assertEqual(leanback[0].get(f"{ANDROID}required"), "true", module)
            # The overlay may only silence lint's per-file view of src/main's
            # TV entry and touchscreen flag, which must therefore be there.
            main = ET.parse(ROOT / module / "src/main/AndroidManifest.xml").getroot()
            self.assertTrue(any(f.get(f"{ANDROID}name") == "android.hardware.touchscreen" and
                                f.get(f"{ANDROID}required") == "false"
                                for f in main.iter("uses-feature")), module)
            self.assertTrue(any(c.get(f"{ANDROID}name") == "android.intent.category.LEANBACK_LAUNCHER"
                                for c in main.iter("category")), module)
        gradle = read(APP / "build.gradle.kts")
        self.assertEqual(int(re.search(r"targetSdk = (\d+)", gradle).group(1)), 34,
                         "a new target API: re-read Play's target API rules, then this test")

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
                                 "about_network_request",
                                 "about_update_label", "about_update_sub",
                                 "about_support_label", "settings_support_title",
                                 "settings_support_sub", "support_label",
                                 "support_kicker", "support_title", "support_body",
                                 "support_mode", "support_footer"})
        self.assertIn("Google Play", play)
        # The Plus token is part of what the Play build sends, so it is on the list.
        self.assertIn("purchase token", play)
        # Every override is an override: a Play-only name would be missing on GitHub builds.
        main = set(re.findall(r'<string name="([^"]+)"', read(APP / "src/main/res/values/strings.xml")))
        self.assertLessEqual(names, main)

    def test_about_lists_the_request_only_when_a_broker_is_baked_in(self):
        about = read(KT / "AboutActivity.kt")
        self.assertIn("getString(R.string.audit_request_endpoint).isNotBlank()", about)
        self.assertIn("R.id.about_network_request", about)
        layout = read(APP / "src/main/res/layout/activity_about.xml")
        line = layout.split('android:id="@+id/about_network_request"', 1)[1].split("/>", 1)[0]
        self.assertIn('android:visibility="gone"', line)

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
        # (Its Play manifest only makes leanback required.)
        play = ET.parse(ROOT / "glyphs/src/play/AndroidManifest.xml").getroot()
        self.assertEqual(list(play.iter("activity")), [])
        self.assertEqual(list(play.iter("activity-alias")), [])
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


class Plus(unittest.TestCase):
    """Core Builds Plus: Play Billing on the Play build, nothing on GitHub builds."""

    GITHUB = APP / "src/github/java/tv/corebuilds/iconpack/PlusStore.kt"
    PLAY = APP / "src/play/java/tv/corebuilds/iconpack/PlusStore.kt"

    def test_each_build_has_one_plus_store(self):
        self.assertIn("const val AVAILABLE = false", read(self.GITHUB))
        self.assertIn("const val AVAILABLE = true", read(self.PLAY))
        self.assertFalse((KT / "PlusStore.kt").exists(), "PlusStore is per build type, never in main")
        gradle = read(APP / "build.gradle.kts")
        for build_type in ("debug", "release", "candidate"):
            self.assertIn(f'getByName("{build_type}").java.srcDir("src/github/java")', gradle)

    def test_billing_ships_in_the_play_build_only(self):
        gradle = read(APP / "build.gradle.kts")
        lines = [l.strip() for l in gradle.splitlines() if "billingclient" in l]
        self.assertEqual(len(lines), 1, lines)
        self.assertTrue(lines[0].startswith('"playImplementation"('), lines[0])
        self.assertNotIn("billingclient", read(self.GITHUB))
        # Support on a GitHub build says opening it sends nothing; its store must not either.
        for token in ("java.net", "HttpURLConnection", "openConnection", "Uri.parse"):
            self.assertNotIn(token, read(self.GITHUB))

    def test_plus_is_sold_only_with_a_broker_to_honour_it(self):
        support = read(KT / "SupportActivity.kt")
        blank = support.index("getString(R.string.audit_request_endpoint).isBlank()")
        self.assertLess(blank, support.index("PlusStore.session("))
        self.assertIn("render(null)", support)

    def test_a_button_that_stops_being_a_control_hands_focus_to_back_first(self):
        render = read(KT / "SupportActivity.kt").split("private fun render(", 1)[1]
        handoff = render.index("findViewById<View>(R.id.support_back).requestFocus()")
        self.assertLess(handoff, render.index("button.isFocusable = buyable"))

    def test_the_token_rides_with_requests_and_nowhere_else(self):
        sender = read(KT / "IconRequestSender.kt")
        self.assertIn('if (plusToken != null) put("plus_token", plusToken)', sender)
        self.assertIn("PlusStore.purchaseToken(this)", read(KT / "AuditorActivity.kt"))
        users = sorted(p.name for p in KT.glob("*.kt") if "purchaseToken(" in read(p))
        self.assertEqual(users, ["AuditorActivity.kt", "Plus.kt"])
        # Plus.kt only asks whether there is one; it never passes it on.
        plus = read(KT / "Plus.kt")
        self.assertEqual(re.findall(r"purchaseToken\([^)]*\)[^\n]*", plus),
                         ["purchaseToken(context) != null"])

    def test_purchases_are_acknowledged_and_refunds_clear_the_token(self):
        play = read(self.PLAY)
        self.assertIn("acknowledgePurchase", play)
        self.assertIn("storeToken(activity, null)", play)
        self.assertIn('PRODUCT_ID = "core_builds_plus"', play)
        self.assertIn("enableOneTimeProducts()", play)


class PlusExtras(unittest.TestCase):
    """What Plus unlocks beyond priority, and that none of it reaches a GitHub build."""

    def test_extras_appear_only_where_plus_is_sold(self):
        plus = read(KT / "Plus.kt")
        shown = plus.split("fun shown(", 1)[1].split("\n\n", 1)[0]
        self.assertIn("PlusStore.AVAILABLE", shown)
        self.assertIn("audit_request_endpoint", shown)
        owned = plus.split("fun owned(", 1)[1].split("\n\n", 1)[0]
        self.assertIn("PlusStore.AVAILABLE &&", owned)
        settings = read(APP / "src/main/res/layout/activity_settings.xml")
        group = settings.split('android:id="@+id/set_plus_group"', 1)[1].split(">", 1)[0]
        self.assertIn('android:visibility="gone"', group)
        self.assertIn("if (Plus.shown(this))", read(KT / "SettingsActivity.kt"))
        self.assertIn("if (Plus.shown(this))", read(KT / "AboutActivity.kt"))
        self.assertIn("if (Plus.shown(this)) WallpaperCatalog.loadPlus(this)",
                      read(KT / "WallpapersActivity.kt"))

    def test_locked_controls_open_the_plus_card(self):
        unlocked = read(KT / "Plus.kt").split("fun unlocked(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("SupportActivity::class.java", unlocked)
        settings = read(KT / "SettingsActivity.kt")
        self.assertEqual(settings.count("if (Plus.unlocked(this))"), 2)
        browser = read(KT / "WallpapersActivity.kt")
        self.assertIn("if (item.plus && !Plus.unlocked(this)) return@WallpaperAdapter", browser)
        # The preview pages through its list: locked walls never go in it.
        self.assertIn("adapter.currentItems().filter { !it.plus || owned }", browser)
        self.assertIn("picked.filter { !it.plus || owned }", browser)
        self.assertNotIn("startExport(adapter.selectedItems())", browser)
        # And My requests refuses to open without Plus, whatever started it.
        requests = read(KT / "RequestsActivity.kt")
        self.assertLess(requests.index("if (!Plus.owned(this))"), requests.index("setContentView"))

    def test_rotation_runs_in_the_wallpaper_engine_and_needs_plus(self):
        engine = read(KT / "LiveWallpaperService.kt")
        self.assertIn("if (shown) LoopRotation.advanceIfDue(applicationContext)", engine)
        rotation = read(KT / "LoopRotation.kt")
        due = rotation.split("fun advanceIfDue(", 1)[1]
        self.assertLess(due.index("Plus.owned(context)"), due.index("Prefs.setLiveLoopId"))
        # It only ever plays what is already on the TV; the engine never downloads.
        self.assertIn("LiveLoopDownloader.playable(context, it) != null", rotation)
        self.assertNotIn("fetch(", rotation)
        # A hand-picked loop restarts the interval.
        prefs = read(KT / "Prefs.kt")
        setter = prefs.split("fun setLiveLoopId(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("KEY_LIVE_LOOP_CHANGED_AT", setter)
        # Every interval the row cycles through has its own label.
        self.assertIn("val HOURS = intArrayOf(0, 1, 6, 24)", rotation)
        labels = read(KT / "SettingsActivity.kt").split("private fun showPlusRows()", 1)[1]
        for hours, name in ((1, "settings_rotate_hour"), (6, "settings_rotate_6h"),
                            (24, "settings_rotate_day")):
            self.assertIn(f"{hours} -> R.string.{name}", labels)

    def test_my_requests_records_only_on_play_and_reads_github_by_number(self):
        log = read(KT / "RequestLog.kt")
        self.assertIn("if (!PlusStore.AVAILABLE || issue <= 0) return", log)
        status = read(KT / "RequestStatus.kt")
        self.assertIn('"https://api.github.com/repos/brevityA/CoreBuildsApps/issues/"', status)
        self.assertIn('PRIORITY_LABEL = "priority"', status)
        broker = read(ROOT / "tools/icon_request_broker/constants.mjs")
        self.assertIn('PRIORITY_LABEL = "priority"', broker)
        screen = read(KT / "RequestsActivity.kt")
        self.assertIn("unchecked.drop(MAX_CHECKS)", screen)
        self.assertIn("R.string.requests_state_later", screen)
        auditor = read(KT / "AuditorActivity.kt")
        self.assertIn("RequestLog.record(this, issue, item.label, item.component, item.mapped)", auditor)
        # About and the policy list the reads.
        self.assertIn('android:id="@+id/about_network_tracker"',
                      read(APP / "src/main/res/layout/activity_about.xml"))
        self.assertIn("My requests", read(ROOT / "PRIVACY.md"))

    def test_plus_walls_ship_in_the_play_build_only(self):
        self.assertTrue((APP / "src/play/assets/manifest/plus-wallpapers.json").exists())
        self.assertFalse((APP / "src/main/assets/manifest/plus-wallpapers.json").exists())
        self.assertFalse(list((APP / "src/main/assets").rglob("*spectra*")))


if __name__ == "__main__":
    unittest.main()
