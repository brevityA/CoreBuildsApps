#!/usr/bin/env python3
"""Core Builds Glyphs: the companion pack behind the Banners/Glyphs toggle.

Banners are the icon pack's default (again, since 1.9.5): tv.corebuilds.iconpack
maps every app to its 16:9 banner. The square glyphs ship in Core Builds Glyphs
(tv.corebuilds.iconpack.glyphs), and the toggle works by telling a launcher to
apply one package or the other. That only works while four separately edited
things agree: the two packages' generated resources, the companion's manifest,
the icon pack's code that names it, and the release workflow that publishes it
under the filename the code downloads. Each test below pins one of those joins;
none of them needs an Android SDK.

Run: python tests/test_glyphs_pack.py
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
PACK_MANIFEST = ROOT / "glyphs/src/main/AndroidManifest.xml"
APP_GRADLE = ROOT / "app/build.gradle.kts"
PACK_GRADLE = ROOT / "glyphs/build.gradle.kts"
KT = ROOT / "app/src/main/java/tv/corebuilds/iconpack"
COMPANION_KT = KT / "GlyphsCompanion.kt"
ACTIVITY_JAVA = (ROOT / "glyphs/src/main/java/tv/corebuilds/iconpack/glyphs/"
                 "GlyphsActivity.java")
HOME_ACTIVITY_JAVA = (ROOT / "glyphs/src/main/java/tv/corebuilds/iconpack/glyphs/"
                      "GlyphsHomeActivity.java")
BUILD_YML = ROOT / ".github/workflows/build.yml"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def launcher_categories(manifest: Path) -> list[frozenset[str]]:
    """Category set of every MAIN filter, in document order."""
    out: list[frozenset[str]] = []
    for activity in ET.parse(manifest).getroot().iter("activity"):
        for f in activity.findall("intent-filter"):
            actions = [a.get(f"{ANDROID}name") for a in f.findall("action")]
            if "android.intent.action.MAIN" in actions:
                out.append(frozenset(c.get(f"{ANDROID}name")
                                     for c in f.findall("category")))
    return out


def mapping(module: str) -> list[tuple[str, str]]:
    return re.findall(r'<item component="([^"]+)" drawable="([^"]+)"',
                      read(ROOT / f"{module}/src/main/res/xml/appfilter.xml"))


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

    def test_same_components_in_both_packs(self):
        # The whole promise of the toggle: switching style never changes which
        # apps get an icon, only what the icon looks like.
        banners, glyphs = mapping("app"), mapping("glyphs")
        self.assertEqual([c for c, _ in banners], [c for c, _ in glyphs])
        self.assertEqual([d for _, d in banners], [f"{d}_banner" for _, d in glyphs])
        self.assertGreater(len(glyphs), 1000)

    def test_icon_pack_maps_banners_by_default(self):
        drawables = [d for _, d in mapping("app")]
        self.assertTrue(drawables)
        self.assertEqual([d for d in drawables if not d.endswith("_banner")], [])

    def test_companion_maps_glyphs_only(self):
        self.assertEqual([d for _, d in mapping("glyphs") if d.endswith("_banner")], [])

    def test_assets_copies_match(self):
        for module in ("app", "glyphs"):
            for name in ("appfilter.xml", "drawable.xml"):
                self.assertEqual(read(ROOT / f"{module}/src/main/res/xml/{name}"),
                                 read(ROOT / f"{module}/src/main/assets/{name}"),
                                 f"{module} {name}")

    def test_build_copies_every_drawable_it_names(self):
        # The glyphs are copied at build time, not committed twice; an include
        # that misses them would link but leave launchers resolving nothing.
        gradle = read(PACK_GRADLE)
        for needed in ('"*.webp"', 'exclude("*_banner.webp")', '"aliases.xml"',
                       '"cb_banner.png"', '"mipmap-*/**"'):
            self.assertIn(needed, gradle)


class OneStylePerPack(unittest.TestCase):
    """Banners only from the icon pack, glyphs only from the companion.

    The toggle picks a package, so each package has to hold one art style on
    every surface a launcher reads: the appfilter it auto-applies, the
    drawable.xml its icon browser lists, and the picker it opens.
    """
    MAIN_KT = KT / "MainActivity.kt"

    def browser(self, module: str) -> list[str]:
        return re.findall(r'<item drawable="([^"]+)" />',
                          read(ROOT / f"{module}/src/main/res/xml/drawable.xml"))

    def test_icon_pack_browser_lists_banners_only(self):
        banners = self.browser("app")
        self.assertGreater(len(banners), 900)
        self.assertEqual([d for d in banners if not d.endswith("_banner")], [])
        titles = re.findall(r'<category title="([^"]+)" />',
                            read(ROOT / "app/src/main/res/xml/drawable.xml"))
        self.assertTrue(all(t.startswith("Banners · ") for t in titles), titles)

    def test_glyphs_browser_lists_the_same_icons_as_glyphs(self):
        glyphs = self.browser("glyphs")
        self.assertEqual([f"{d}_banner" for d in glyphs], self.browser("app"))
        titles = re.findall(r'<category title="([^"]+)" />',
                            read(ROOT / "glyphs/src/main/res/xml/drawable.xml"))
        self.assertTrue(titles)
        self.assertTrue(all(t.startswith("Square · ") for t in titles), titles)

    def test_forwarded_picks_are_marked_glyphs(self):
        extra = re.search(r'const val EXTRA_PICK_GLYPHS = "([^"]+)"',
                          read(COMPANION_KT)).group(1)
        java = read(ACTIVITY_JAVA)
        self.assertIn(f'EXTRA_PICK_GLYPHS = "{extra}"', java)
        forward = java.split("if (isPickRequest(in))", 1)[1].split("} else {", 1)[0]
        self.assertIn("putExtra(EXTRA_PICK_GLYPHS, true)", forward)

    def test_picker_shape_comes_from_the_pack_not_the_toggle(self):
        src = read(self.MAIN_KT)
        self.assertIn("fun pickFixedByPack(): Boolean = pickMode && GlyphsCompanion.supported()",
                      src)
        # An unmarked pick came through the icon pack: banners.
        self.assertIn("!intent.getBooleanExtra(GlyphsCompanion.EXTRA_PICK_GLYPHS, false)", src)
        # With a companion the picker shows no shape chips, so no pick can
        # write the art style the whole launcher applies.
        chips = src.split("private fun bindPickShape()", 1)[1]
        self.assertLess(chips.index("if (pickFixedByPack())"),
                        chips.index("Prefs.set(this, Prefs.KEY_PICK_BANNERS"))
        sync = src.split("private fun syncArtStyle()", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("if (pickFixedByPack()) return", sync)

    def test_banners_are_the_default_style(self):
        prefs = read(KT / "Prefs.kt")
        self.assertIn("prefs(context).getBoolean(KEY_PICK_BANNERS, true)", prefs)
        # Glyphs, not banners, is what needs the companion.
        self.assertIn("supported() && !Prefs.pickerPrefersBanners(context)",
                      read(COMPANION_KT))

    def test_switch_never_claims_glyphs_the_launcher_lacks(self):
        src = read(COMPANION_KT)
        ensure = src.split("fun ensure(", 1)[1].split("\n    }\n", 1)[0]
        # Every way ensure() can fail to deliver the pack reverts the style.
        self.assertEqual(ensure.count("revertToBanners(activity)"), 3, ensure)
        self.assertEqual(ensure.count("onUnavailable()"), 3, ensure)
        revert = src.split("private fun revertToBanners(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("Prefs.KEY_PICK_BANNERS, true", revert)
        # A superseded download must not install or revert over a newer one.
        self.assertIn("if (generation != ensureGeneration) return@downloadCompanion", ensure)
        # Only the installer's own result can call an install declined; a
        # resume that arrives before the package lands just waits.
        result = src.split("fun onInstallResult(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("Pending.Declined", result)
        self.assertIn("revertToBanners(context)", result)
        take = src.split("fun takePendingApply(", 1)[1].split("\n    }\n", 1)[0]
        self.assertNotIn("revertToBanners", take)
        self.assertNotIn("Pending.Declined", take)
        installer = read(KT / "UpdateInstaller.kt")
        for_result = installer.split("fun installForResult(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("putExtra(Intent.EXTRA_RETURN_RESULT, true)", for_result)
        self.assertIn("startActivityForResult(intent, requestCode)", for_result)
        for activity in ("MainActivity.kt", "SettingsActivity.kt"):
            self.assertIn("GlyphsCompanion.onInstallResult(this)", read(KT / activity), activity)
        settings = read(KT / "SettingsActivity.kt")
        resume = settings.split("override fun onResume()", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("bannerSwitch.isChecked = Prefs.pickerPrefersBanners(this)", resume)


class Manifest(unittest.TestCase):
    def test_discovered_by_the_same_launchers(self):
        self.assertEqual(pack_filters(PACK_MANIFEST), pack_filters(APP_MANIFEST))

    def test_launcher_entry_is_solo_on_the_home_activity(self):
        # Reversed 2026-10-04, and the reversal is the point of these three
        # tests. This manifest used to assert no MAIN and no LAUNCHER at all
        # ("one app for the user"), which meant a package a user could install
        # - or that the Art style toggle installed for them - could never be
        # opened, and nothing in it said what it was or where the toggle
        # lived. It now has a front door: exactly one MAIN filter, on the
        # activity that shows the pack's own screen.
        #
        # Not on .GlyphsActivity. That window has to stay translucent because
        # it forwards every pick to the icon pack, and an opaque one would
        # flash on each forward.
        root = ET.parse(PACK_MANIFEST).getroot()
        with_main = [a.get(f"{ANDROID}name") for a in root.iter("activity")
                     if any(act.get(f"{ANDROID}name") == "android.intent.action.MAIN"
                            for f in a.findall("intent-filter")
                            for act in f.findall("action"))]
        self.assertEqual(with_main, [".GlyphsHomeActivity"])

    def test_it_lands_in_a_drawer_and_on_the_tv_row(self):
        # The pack is used mostly on Android TV, so LAUNCHER alone would put it
        # on a phone and leave it off the shelf. Both packages declare the
        # same launcher categories on one filter.
        self.assertEqual(launcher_categories(PACK_MANIFEST),
                         launcher_categories(APP_MANIFEST))
        self.assertEqual(launcher_categories(PACK_MANIFEST),
                         [frozenset({"android.intent.category.LAUNCHER",
                                     "android.intent.category.LEANBACK_LAUNCHER"})])

    def test_the_tv_row_is_declared_and_the_door_is_exported(self):
        # android.software.leanback is what puts the entry on the TV row, and
        # lint reads declaring it as a promise of a LEANBACK_LAUNCHER activity
        # - which the test above holds. required=false: a sideloaded install
        # does not filter on features, and the pack is useful on a phone
        # launcher too. exported is not optional on targetSdk 34.
        root = ET.parse(PACK_MANIFEST).getroot()
        home = next(a for a in root.iter("activity")
                    if a.get(f"{ANDROID}name") == ".GlyphsHomeActivity")
        self.assertEqual(home.get(f"{ANDROID}exported"), "true")
        leanback = [f for f in root.iter("uses-feature")
                    if f.get(f"{ANDROID}name") == "android.software.leanback"]
        self.assertEqual(len(leanback), 1)
        self.assertEqual(leanback[0].get(f"{ANDROID}required"), "false")

    def test_the_discovery_filters_stay_on_the_translucent_carrier(self):
        # A launcher finds the pack through .GlyphsActivity, so the front door
        # must not become a second discovery surface, and the carrier must not
        # pick up an opaque theme.
        root = ET.parse(PACK_MANIFEST).getroot()
        for activity in root.iter("activity"):
            name = activity.get(f"{ANDROID}name")
            filters = activity.findall("intent-filter")
            if name == ".GlyphsHomeActivity":
                self.assertEqual(len(filters), 1)
                continue
            self.assertEqual(name, ".GlyphsActivity")
            self.assertEqual(activity.get(f"{ANDROID}theme"),
                             "@android:style/Theme.Translucent.NoTitleBar")
            self.assertGreater(len(filters), 10)

    def test_the_screen_reads_its_counts_from_the_generated_pack(self):
        # The home screen quotes how much the pack covers. Reading the shipped
        # generated XML is what keeps that honest when the catalog grows; a
        # hard-coded number would go stale the first time build_icons.py ran.
        home = read(HOME_ACTIVITY_JAVA)
        self.assertIn('countItems(R.xml.drawable, "drawable")', home)
        self.assertIn('countItems(R.xml.appfilter, "component")', home)
        # A count it cannot read is hidden, never guessed.
        self.assertIn("counts.setVisibility(View.GONE)", home)

    def test_the_front_door_adds_no_dependency(self):
        # The companion stays a resource pack with a door on it: framework
        # widgets and a framework theme. The day a UI needs appcompat is the
        # day this module stops being cheap to build and impossible to drift.
        gradle = read(PACK_GRADLE)
        self.assertNotIn("implementation(", gradle)
        self.assertNotIn("api(", gradle)

    def test_each_side_can_see_the_other(self):
        self.assertIn('<package android:name="tv.corebuilds.iconpack" />',
                      read(PACK_MANIFEST))
        self.assertIn('<package android:name="tv.corebuilds.iconpack.glyphs" />',
                      read(APP_MANIFEST))


class Contracts(unittest.TestCase):
    def test_package_name_agrees_everywhere(self):
        pack_id = re.search(r'applicationId = "([^"]+)"', read(PACK_GRADLE)).group(1)
        self.assertEqual(pack_id, "tv.corebuilds.iconpack.glyphs")
        self.assertIn(f'"\\"{pack_id}\\""', read(APP_GRADLE))

    def test_forward_targets_the_icon_pack(self):
        app_id = re.search(r'applicationId = "([^"]+)"', read(APP_GRADLE)).group(1)
        self.assertIn(f'ICON_PACK = "{app_id}"', read(ACTIVITY_JAVA))

    def test_builds_without_a_companion_offer_none(self):
        # The debug-signed candidate build ships no companion APK, so an
        # empty package is what keeps its toggle in-app.
        candidate = read(APP_GRADLE).split('create("candidate")', 1)[1].split("}", 1)[0]
        self.assertIn('"GLYPHS_PACKAGE", "\\"\\""', candidate)

    def test_version_comes_from_the_icon_pack(self):
        gradle = read(PACK_GRADLE)
        self.assertIn('rootProject.file("app/build.gradle.kts")', gradle)
        self.assertIn("versionCode = packVersionCode", gradle)
        self.assertIn("versionName = packVersionName", gradle)

    def test_release_publishes_the_asset_the_app_downloads(self):
        asset = re.search(r'const val ASSET = "([^"]+)"', read(COMPANION_KT)).group(1)
        self.assertEqual(asset, "iconpack-glyphs-release.apk")
        self.assertIn('/v${BuildConfig.VERSION_NAME}/$ASSET', read(COMPANION_KT))
        yml = read(BUILD_YML)
        self.assertIn(f"dist/{asset}", yml)
        versioned = yml.split("Publish versioned release", 1)[1].split("- name:", 1)[0]
        self.assertIn(f"dist/{asset}", versioned)
        self.assertNotIn("iconpack-banners-release.apk", yml)

    def test_companion_is_held_to_the_updater_bar(self):
        src = read(KT / "UpdateInstaller.kt")
        body = src.split("fun downloadCompanion(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("verifyDownloadedApk(app, file, packageName", body)
        self.assertIn("it == versionCode", body)
        # The signature check compares against the *installed app's* certs.
        self.assertIn("pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)",
                      src)


FLAVOR_MAIN = ROOT / "app/src/glyphs"


class StandaloneGlyphApp(unittest.TestCase):
    """The :app module's `glyphs` flavor: the same app, square art.

    tv.corebuilds.glyphs is the icon pack's own code and catalog built with
    the square appfilter, for a user who wants glyphs as their only pack and
    no toggle. It is a flavor rather than a module because a second module
    cannot share :app's res/ and override just appfilter.xml - two source
    sets providing the same resource is a merge error, which is what the
    retired Pixel Neon forked its Kotlin to get around and then drifted on.

    These pin the joins a flavor introduces: one mapping in two trees, a
    package id and provider authority that cannot collide with the pack it
    installs beside, and a release asset name of its own.
    """

    def test_the_flavor_maps_exactly_what_the_companion_maps(self):
        # One generator, two committed trees, so the standalone app and the
        # resource pack can never apply different art to the same component.
        for name in ("res/xml/appfilter.xml", "assets/appfilter.xml",
                     "res/xml/drawable.xml", "assets/drawable.xml"):
            self.assertEqual(read(FLAVOR_MAIN / name),
                             read(ROOT / "glyphs/src/main" / name), name)

    def test_the_two_glyph_packages_map_glyphs_only(self):
        for tree in (FLAVOR_MAIN, ROOT / "glyphs/src/main"):
            drawables = re.findall(r'<item component="[^"]+" drawable="([^"]+)"',
                                   read(tree / "res/xml/appfilter.xml"))
            self.assertGreater(len(drawables), 1000)
            self.assertEqual([d for d in drawables if d.endswith("_banner")], [],
                             str(tree))

    def test_it_has_its_own_package_id(self):
        # Not the companion's: tv.corebuilds.iconpack.glyphs is the resource
        # pack the toggle installs, and two apps cannot hold one package name.
        gradle = read(APP_GRADLE)
        glyphs = gradle.split('create("glyphs")', 1)[1]
        self.assertIn('applicationId = "tv.corebuilds.glyphs"', glyphs)
        self.assertIn('applicationId = "tv.corebuilds.iconpack"', gradle)

    def test_its_provider_authority_cannot_collide(self):
        # Two installed apps declaring one FileProvider authority is
        # INSTALL_FAILED_CONFLICTING_PROVIDER, and these two are meant to be
        # installed side by side.
        gradle = read(APP_GRADLE)
        glyphs = gradle.split('create("glyphs")', 1)[1]
        self.assertIn('"tv.corebuilds.glyphs.update"', glyphs)
        self.assertIn('"tv.corebuilds.iconpack.update"', gradle)

    def test_it_offers_no_art_style_toggle(self):
        # The flavor *is* a glyph pack: there is nothing to switch to and no
        # companion for it to fetch, so it ships an empty package the way the
        # candidate build always has, which is what keeps the toggle hidden.
        glyphs = read(APP_GRADLE).split('create("glyphs")', 1)[1]
        self.assertIn(r'"GLYPHS_PACKAGE", "\"\""', glyphs)

    def test_it_does_not_poll_the_icon_packs_update_feed(self):
        # That feed names the banner APK, which this app could not install
        # over itself. A blank URL has to be a named gap, not a
        # MalformedURLException surfacing as an update error.
        glyphs = read(APP_GRADLE).split('create("glyphs")', 1)[1]
        self.assertIn(r'"UPDATE_MANIFEST_URL", "\"\""', glyphs)
        checker = read(KT / "UpdateChecker.kt")
        self.assertIn("if (MANIFEST_URLS.all { it.isBlank() })", checker)

    def test_the_release_publishes_it_under_its_own_name(self):
        # iconpack-glyphs-release.apk is the companion's, fetched by name
        # from GlyphsCompanion.ASSET; the app needs a name of its own.
        yml = read(BUILD_YML)
        self.assertIn("dist/corebuilds-glyphs-release.apk", yml)
        self.assertIn("app/build/outputs/apk/glyphs/release/", yml)
        self.assertIn("app/build/outputs/apk/banners/release/", yml)
        self.assertNotEqual("corebuilds-glyphs-release.apk",
                            re.search(r'const val ASSET = "([^"]+)"',
                                      read(COMPANION_KT)).group(1))

    def test_the_companion_label_is_the_one_the_icon_pack_says(self):
        # Both glyph packages appear in a launcher's icon-pack list, so the
        # resource pack carries "Pack" and the icon pack has to name it that
        # way when it offers to install it.
        label = re.search(r'const val LABEL = "([^"]+)"',
                          read(COMPANION_KT)).group(1)
        shipped = re.search(r'<string name="app_name">([^<]+)</string>',
                            read(ROOT / "glyphs/src/main/res/values/strings.xml")
                            ).group(1)
        self.assertEqual(label, shipped)
        self.assertEqual(label, "Core Builds Glyphs Pack")


if __name__ == "__main__":
    unittest.main()
