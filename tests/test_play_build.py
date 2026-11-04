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
import json
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
        # The listing is TV only (docs/play/listing.json form_factors, and
        # PLAY-CONSOLE.md says to pick it): there are no phone screenshots and
        # no phone UI has been tested. Console cannot make a phone-compatible
        # bundle TV-only; only the manifest can. With leanback optional, Play
        # would offer the app to phones and want phone screenshots.
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
        listing = json.loads(read(ROOT / "docs/play/listing.json"))
        self.assertEqual(listing["form_factors"], ["tv"])
        # The suite-wide baseline is API 37, above both TV and phone Play floors.
        gradle = read(APP / "build.gradle.kts")
        self.assertEqual(int(re.search(r"targetSdk = (\d+)", gradle).group(1)), 37,
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
                                 "about_update_label", "about_update_sub",
                                 "support_label", "support_kicker", "support_title",
                                 "support_body", "support_mode", "support_footer"})
        self.assertIn("Google Play", play)
        # The Plus token is part of what the Play build sends, so it is on the
        # list: About's item 4, shown wherever Plus is.
        tracker = re.search(r'<string name="about_network_tracker">([^<]*)</string>',
                            read(APP / "src/main/res/values/strings.xml")).group(1)
        self.assertRegex(tracker, r"^4\.")
        self.assertIn("purchase token", tracker)
        self.assertIn("My requests", tracker)
        # Every override is an override: a Play-only name would be missing on GitHub builds.
        main = set(re.findall(r'<string name="([^"]+)"', read(APP / "src/main/res/values/strings.xml")))
        self.assertLessEqual(names, main)

    def test_about_lists_plus_traffic_only_where_plus_is(self):
        # Item 3 (the request) is always listed, as main has it; item 4 is the
        # token and My requests' reads, which exist only where Plus is.
        about = read(KT / "AboutActivity.kt")
        self.assertIn("if (Plus.shown(this)) {\n            findViewById<View>(R.id.about_network_tracker)", about)
        layout = read(APP / "src/main/res/layout/activity_about.xml")
        line = layout.split('android:id="@+id/about_network_tracker"', 1)[1].split("/>", 1)[0]
        self.assertIn('android:visibility="gone"', line)
        self.assertLess(layout.index("@string/about_network_three"),
                        layout.index("@string/about_network_tracker"))

    def test_plus_door_is_its_own_row_not_a_support_door(self):
        # The tip doors stay shut on Play (test above). Plus has one door of its
        # own, in Settings' Plus group, which only exists where Plus is sold;
        # on Play, SupportActivity is the Plus card and shows no tip code.
        settings = read(KT / "SettingsActivity.kt")
        group = settings.split("if (Plus.shown(this)) {", 1)[1].split("\n        }\n", 1)[0]
        self.assertIn("row(R.id.set_plus_row) {\n                startActivity(Intent(this, SupportActivity::class.java))", group)
        self.assertNotIn("set_plus_row", settings.split("if (Plus.shown(this)) {", 1)[0])
        layout = read(APP / "src/main/res/layout/activity_settings.xml")
        plus_group = layout.split('android:id="@+id/set_plus_group"', 1)[1]
        self.assertIn('android:id="@+id/set_plus_row"', plus_group)

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

    def test_suite_screen_names_no_way_off_play(self):
        # The Suite screen was the last place the Play build still told users
        # how to get an app Play did not install it: "Downloader code 5270601"
        # and "grab the APK from the GitHub release". Play reads steering users
        # to another install route as circumvention, so on Play the screen is
        # only "what of ours is on this TV".
        src = read(KT / "SuiteActivity.kt")
        self.assertIn("if (onPlay && !installed) continue", src,
                      "Play build must not show a row for an absent app")
        self.assertIn("if (onPlay) {\n                codeLine.visibility = View.GONE", src)
        self.assertIn("if (onPlay && rows == 0)", src,
                      "an emptied column must say why, not look broken")
        # The two off-Play strings must sit in the branch Play cannot reach.
        branch = src.split("if (onPlay) {", 1)[1].split("} else {", 1)[0]
        self.assertNotIn("R.string.suite_code_fmt", branch)
        self.assertIn("R.string.suite_none_play", src)

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

    def test_play_bundles_avoid_the_pre_launch_warnings(self):
        # Play's pre-launch report on 57 (2.4.4) flagged two things. Neither
        # blocked the release; both are cheap to keep away.
        # 1. Deprecated edge-to-edge APIs: no code calls the bar-colour
        #    setters and no theme sets the bar colours, in either module.
        # Case-insensitive, so the Java-style setters (setStatusBarColor(...))
        # count as well as the Kotlin properties; comments are stripped first,
        # since Prefs.applyChrome's doc names the setters it dropped.
        comment = re.compile(r"/\*.*?\*/|//[^\n]*|<!--.*?-->", re.S)
        for root in (APP / "src", ROOT / "glyphs/src"):
            for path in list(root.rglob("*.kt")) + list(root.rglob("*.xml")):
                code = comment.sub("", read(path)).lower()
                for word in ("statusBarColor", "navigationBarColor", "navigationBarDividerColor",
                             "LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES"):
                    self.assertNotIn(word.lower(), code, f"{path.relative_to(ROOT)} uses {word}")
        # 2. Orientation restriction: the Play overlay removes MainActivity's
        #    landscape lock; the sideload manifest keeps it (validate.py).
        play = ET.parse(APP / "src/play/AndroidManifest.xml").getroot()
        removed = [a for a in play.iter("activity")
                   if a.get(f"{ANDROID}name") == ".MainActivity"
                   and a.get("{http://schemas.android.com/tools}remove") == "android:screenOrientation"]
        self.assertEqual(len(removed), 1)
        self.assertIn('android:screenOrientation="landscape"', read(APP / "src/main/AndroidManifest.xml"))

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
            self.assertIn(f'getByName("{build_type}").kotlin.srcDir("src/github/java")', gradle)

    def test_billing_ships_in_the_play_build_only(self):
        gradle = read(APP / "build.gradle.kts")
        lines = [ln.strip() for ln in gradle.splitlines() if "billingclient" in ln]
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
        handoff = render.index("if (!buyable && button.hasFocus()) back.requestFocus()")
        self.assertLess(handoff, render.index("button.isFocusable = buyable"))
        # The trial button too: Back takes the cursor before it is hidden.
        trial = render.index("if (!offer && tryIt.hasFocus()) back.requestFocus()")
        self.assertLess(trial, render.index("tryIt.visibility ="))

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
        # Rotation, night loop, night hours, Projectivy walls by day and at
        # night, My requests.
        self.assertEqual(settings.count("if (Plus.unlocked(this))"), 6)
        browser = read(KT / "WallpapersActivity.kt")
        self.assertIn("if (item.plus && !Plus.unlocked(this)) return@WallpaperAdapter", browser)
        # The preview pages through its list: locked walls never go in it.
        self.assertIn("val on = Plus.active(this)", browser)
        self.assertIn("adapter.currentItems().filter { !it.plus || on }", browser)
        # Export is the purchase's, not the trial's: a copy outlasts it.
        self.assertIn("val owned = Plus.owned(this)\n        val wallpapers = picked.filter { !it.plus || owned }",
                      browser)
        self.assertNotIn("startExport(adapter.selectedItems())", browser)
        # And My requests refuses to open without Plus, whatever started it.
        requests = read(KT / "RequestsActivity.kt")
        self.assertLess(requests.index("if (!Plus.active(this))"), requests.index("setContentView"))

    def test_rotation_runs_in_the_wallpaper_engine_and_needs_plus(self):
        engine = read(KT / "LiveWallpaperService.kt")
        self.assertIn("if (shown) LoopRotation.advanceIfDue(applicationContext)", engine)
        rotation = read(KT / "LoopRotation.kt")
        due = rotation.split("fun advanceIfDue(", 1)[1]
        self.assertLess(due.index("if (!Plus.active(context, now)) return false"),
                        due.index("Prefs.setLiveLoopId"))
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

    def test_night_loop_is_read_by_the_engine_needs_plus_and_never_downloads(self):
        engine = read(KT / "LiveWallpaperService.kt")
        self.assertIn("loop = LiveLoop.byId(DayNight.loopId(applicationContext))", engine)
        # A home screen left on still changes over: the engine re-reads at the
        # boundary while visible, and never leaves the callback behind.
        self.assertIn("handler.postDelayed(switchCheck, wait + SWITCH_SLACK_MS)", engine)
        self.assertEqual(engine.count("handler.removeCallbacks(switchCheck)"), 3)
        night = read(KT / "DayNight.kt")
        active = night.split("fun active(", 1)[1].split("\n\n", 1)[0]
        self.assertIn("Plus.active(context) &&", active)
        # An unplayable night loop falls back to the day choice, never a still.
        self.assertIn("LiveLoopDownloader.playable(context, night) != null) night.id else day", night)
        self.assertNotIn("fetch(", night)
        # Settings offers only loops already on the TV.
        settings = read(KT / "SettingsActivity.kt")
        row = settings.split("row(R.id.set_night_row)", 1)[1].split("row(R.id.set_hours_row)", 1)[0]
        self.assertIn("LiveLoopDownloader.playable(this, it) != null", row)
        self.assertIn("R.string.settings_night_none", row)

    def test_my_requests_records_only_on_play_and_reads_github_by_number(self):
        log = read(KT / "RequestLog.kt")
        self.assertIn("if (!PlusStore.AVAILABLE || issue <= 0) return", log)
        status = read(KT / "RequestStatus.kt")
        self.assertIn('"https://api.github.com/repos/brevityA/CoreBuildsApps/issues/"', status)
        self.assertIn('PRIORITY_LABEL = "priority"', status)
        broker = read(ROOT / "tools/icon_request_broker/constants.mjs")
        self.assertIn('PRIORITY_LABEL = "priority"', broker)
        screen = read(KT / "RequestsActivity.kt")
        # One hourly budget per process, not a per-visit cap: three visits
        # with 30 open rows each used to send 90 reads past GitHub's 60.
        self.assertIn("const val HOURLY_READS = 30", screen)
        self.assertIn("const val HOUR_MS = 60 * 60_000L", screen)
        self.assertIn("val granted = reserveReads(unchecked.size)", screen)
        self.assertIn("unchecked.drop(granted)", screen)
        self.assertNotIn("MAX_CHECKS", screen)
        self.assertIn("R.string.requests_state_later", screen)
        auditor = read(KT / "AuditorActivity.kt")
        self.assertIn("RequestLog.record(this, issue, item.label, item.component, item.mapped)", auditor)
        # About and the policy list the reads.
        self.assertIn('android:id="@+id/about_network_tracker"',
                      read(APP / "src/main/res/layout/activity_about.xml"))
        policy = read(ROOT / "docs/privacy.html")
        self.assertIn("My requests", policy)
        self.assertIn("api.github.com", policy)

    def test_plus_walls_ship_in_the_play_build_only(self):
        self.assertTrue((APP / "src/play/assets/manifest/plus-wallpapers.json").exists())
        self.assertFalse((APP / "src/main/assets/manifest/plus-wallpapers.json").exists())
        self.assertFalse(list((APP / "src/main/assets").rglob("*spectra*")))
        self.assertFalse(list((APP / "src/main/assets").rglob("*aurum*")))
        # The thumbs are named by number, not series, so check them by name.
        plus = json.loads(read(APP / "src/play/assets/manifest/plus-wallpapers.json"))
        shipped = {p.name for p in (APP / "src/main/assets").rglob("*.jpg")}
        for w in plus["wallpapers"]:
            self.assertNotIn(w["thumb"].rsplit("/", 1)[-1], shipped, w["name"])


class PlusTrial(unittest.TestCase):
    """Core Premium's one 24-hour trial: on-device extras only, once per TV."""

    @classmethod
    def setUpClass(cls):
        cls.plus = read(KT / "Plus.kt")
        cls.strings = read(APP / "src/main/res/values/strings.xml")

    def string(self, name):
        found = re.search(rf'<string name="{name}">([^<]*)</string>', self.strings)
        self.assertIsNotNone(found, name)
        return found.group(1)

    def test_it_lasts_what_the_button_says_and_runs_once(self):
        self.assertIn("const val TRIAL_MS = 24L * 60 * 60 * 1000", self.plus)
        self.assertIn("24 hours", self.string("plus_trial_button"))
        start = self.plus.split("fun startTrial(", 1)[1].split("\n    }\n", 1)[0]
        # Once: a second press finds the stored start and stores nothing.
        self.assertLess(start.index("if (trialStart(context) != null) return false"),
                        start.index("putLong(KEY_STARTED, now)"))
        # A clock set back before the start ends it rather than restarting it.
        trial = self.plus.split("fun trial(", 1)[1].split("\n\n", 1)[0]
        self.assertIn("now < started || now >= started + TRIAL_MS -> Trial.Ended", trial)

    def test_active_is_the_purchase_or_a_running_trial_and_only_on_play(self):
        active = self.plus.split("fun active(", 1)[1].split("\n\n", 1)[0]
        self.assertIn("owned(context) ||", active)
        self.assertIn("PlusStore.AVAILABLE && trial(trialStart(context), now) is Trial.Running", active)
        unlocked = self.plus.split("fun unlocked(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("if (if (purchase) owned(activity) else active(activity)) return true", unlocked)

    def test_the_trial_unlocks_what_the_tv_decides_and_nothing_else(self):
        # On-device extras read active().
        for name, call in (("LoopRotation.kt", "Plus.active(context, now)"),
                           ("DayNight.kt", "Plus.active(context)"),
                           ("ProjectivyWalls.kt", "Plus.active(context, now)"),
                           ("RequestsActivity.kt", "Plus.active(this)")):
            self.assertIn(call, read(KT / name), name)
        # What Google must confirm, or what outlasts the trial, reads owned().
        auditor = read(KT / "AuditorActivity.kt")
        self.assertIn("Plus.unlocked(this, purchase = true)", auditor)
        self.assertIn("!Plus.owned(this) -> getString(R.string.audit_request_all_locked)", auditor)
        preview = read(KT / "WallpaperPreviewActivity.kt")
        save = preview.split("private fun onSaveClicked()", 1)[1].split("\n    }\n", 1)[0]
        self.assertLess(save.index("?.plus == true && !Plus.owned(this)"),
                        save.index("saveNow()"))
        # The token still rides only with a purchase: the trial has none.
        self.assertEqual(re.findall(r"purchaseToken\([^)]*\)[^\n]*", self.plus),
                         ["purchaseToken(context) != null"])

    def test_the_card_offers_it_once_and_only_beside_a_price(self):
        support = read(KT / "SupportActivity.kt")
        self.assertIn("val offer = buyable && trial is Plus.Trial.Unused", support)
        self.assertIn("tryIt.visibility = if (offer) View.VISIBLE else View.GONE", support)
        layout = read(APP / "src/main/res/layout/activity_support.xml")
        button = layout.split('android:id="@+id/support_plus_trial"', 1)[1].split("/>", 1)[0]
        self.assertIn('android:visibility="gone"', button)
        # Settings shows the time left on the Core Premium row.
        self.assertIn("R.string.settings_plus_trial_fmt", read(KT / "SettingsActivity.kt"))
        # The policy says the trial is kept on the TV and sends nothing.
        policy = " ".join(read(ROOT / "docs/privacy.html").split())
        self.assertIn("try it free for 24 hours, once", policy)
        self.assertIn("starting it sends nothing", policy)


if __name__ == "__main__":
    unittest.main()
