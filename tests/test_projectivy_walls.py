#!/usr/bin/env python3
"""The icon pack as a Projectivy wallpaper source.

Projectivy (4.51+) lists any installed service that answers its
`tv.projectivy.plugin.WALLPAPER_PROVIDER` action, binds it over an AIDL
interface and asks it for a list of wallpapers. Core Motion (motion-plugin/)
has implemented that contract for its video loops since 1.0; the icon pack now
does it for the stills (WallsProviderService, ProjectivyWalls).

A provider fails silently: a wrong interface, parcel or manifest entry is a
source that never appears or an empty rotation, and nothing on the TV says
why. So this file holds what can be checked off the device:

1. the interface is Core Motion's, byte for byte, and the hand-written parcel
   writes the fields in the order Core Motion's Parcelize declaration does;
2. the manifest declares what Projectivy reads, with a settings door that is
   exported while Settings itself is not;
3. each build installed on one TV gets its own provider id;
4. the free tier is the main collection, and every choice is Core Builds Plus;
5. the provider reads the bundled catalogue only: it never downloads.

Run: python3 tests/test_projectivy_walls.py
"""
from __future__ import annotations

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
KT = APP / "src/main/java/tv/corebuilds/iconpack"
API = "tv/projectivy/plugin/wallpaperprovider/api"
APP_API = APP / "src/main/java" / API
MOTION = ROOT / "motion-plugin/app/src/main"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


class InterfaceTests(unittest.TestCase):
    def test_the_aidl_is_core_motions(self):
        names = sorted(p.name for p in (MOTION / "aidl" / API).glob("*.aidl"))
        self.assertEqual(names, ["Event.aidl", "IWallpaperProviderService.aidl", "Wallpaper.aidl"])
        for name in names:
            self.assertEqual(read(APP / "src/main/aidl" / API / name),
                             read(MOTION / "aidl" / API / name), name)

    def test_the_contract_constants_are_core_motions(self):
        self.assertEqual(read(APP_API / "WallpaperProviderContract.kt"),
                         read(MOTION / "java" / API / "WallpaperProviderContract.kt"))

    def test_the_parcel_matches_core_motions_declaration(self):
        # Core Motion's Wallpaper is a Parcelize data class: the parcel is its
        # constructor's fields in order, Strings by writeString, Ints by
        # writeInt. The hand-written one must write and read the same.
        decl = read(MOTION / "java" / API / "Wallpaper.kt").split("data class Wallpaper(", 1)[1]
        decl = decl.split(") : Parcelable", 1)[0]
        fields = re.findall(r"val (\w+): (?:@\w+ )?(\w+)", decl)
        self.assertEqual([f for f, _ in fields],
                         ["uri", "type", "displayMode", "title", "source", "author", "actionUri"])
        ours = read(APP_API / "Wallpaper.kt")
        write = ours.split("override fun writeToParcel(", 1)[1].split("\n    }\n", 1)[0]
        expected = [f"parcel.write{'Int' if t == 'Int' else 'String'}({f})" for f, t in fields]
        self.assertEqual(re.findall(r"parcel\.write\w+\(\w+\)", write), expected)
        create = ours.split("override fun createFromParcel(", 1)[1].split("\n        )\n", 1)[0]
        reads = re.findall(r"(\w+) = parcel\.read(\w+)\(\)", create)
        self.assertEqual(reads, [(f, "Int" if t == "Int" else "String") for f, t in fields])

    def test_the_event_type_codes_are_core_motions(self):
        theirs = dict(re.findall(r"const val (\w+) = (\d+)",
                                 read(MOTION / "java" / API / "Event.kt")))
        ours = dict(re.findall(r"const val (\w+) = (\d+)", read(APP_API / "Event.kt")))
        self.assertEqual(ours, theirs)

    def test_aidl_is_switched_on(self):
        self.assertIn("aidl = true", read(APP / "build.gradle.kts"))


class ManifestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = ET.parse(APP / "src/main/AndroidManifest.xml").getroot()
        cls.app = cls.root.find("application")
        cls.service = next(s for s in cls.app.iter("service")
                           if s.get(f"{ANDROID}name") == ".WallsProviderService")
        cls.meta = {m.get(f"{ANDROID}name"): m.get(f"{ANDROID}value")
                    for m in cls.service.iter("meta-data")}

    def test_the_service_answers_projectivys_action(self):
        self.assertEqual(self.service.get(f"{ANDROID}exported"), "true")
        actions = [a.get(f"{ANDROID}name") for a in self.service.iter("action")]
        self.assertEqual(actions, ["tv.projectivy.plugin.WALLPAPER_PROVIDER"])

    def test_the_meta_data_projectivy_reads(self):
        self.assertEqual(self.meta["apiVersion"], "1")
        self.assertEqual(self.meta["uuid"], "${wallsProviderUuid}")
        self.assertEqual(self.meta["name"], "@string/app_name")
        self.assertEqual(self.meta["updateMode"], "1")  # TIME_ELAPSED only
        cache = read(APP / "src/main/res/values/integers.xml")
        self.assertIn('<integer name="walls_cache_duration_millis">3600000</integer>', cache)
        self.assertEqual(self.meta["itemsCacheDurationMillis"], "@integer/walls_cache_duration_millis")

    def test_the_settings_door_is_an_exported_alias_of_an_unexported_screen(self):
        door = self.meta["settingsActivity"]
        # Fully qualified: the Play and test packages are not the namespace.
        self.assertEqual(door, "tv.corebuilds.iconpack.WallsProviderSettings")
        alias = next(a for a in self.app.iter("activity-alias")
                     if f"tv.corebuilds.iconpack{a.get(f'{ANDROID}name')}" == door)
        self.assertEqual(alias.get(f"{ANDROID}exported"), "true")
        target = alias.get(f"{ANDROID}targetActivity")
        screen = next(a for a in self.app.iter("activity") if a.get(f"{ANDROID}name") == target)
        self.assertEqual(screen.get(f"{ANDROID}exported"), "false")
        self.assertEqual(target, ".SettingsActivity")
        # Arriving through the door lands on the Projectivy row.
        settings = read(KT / "SettingsActivity.kt")
        self.assertIn('endsWith(".WallsProviderSettings")', settings)

    def test_each_build_on_one_tv_is_its_own_source(self):
        gradle = read(APP / "build.gradle.kts")
        block = gradle.split("onVariants { variant ->", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn('"wallsProviderUuid"', block)
        self.assertIn("variant.applicationId.map", block)
        self.assertIn("UUID.nameUUIDFromBytes(", block)
        # The broadcast reads the id back from the service, so code and
        # manifest cannot disagree.
        walls = read(KT / "ProjectivyWalls.kt")
        self.assertIn('.metaData?.getString("uuid")', walls)


class TierTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.walls = read(KT / "ProjectivyWalls.kt")
        cls.settings = read(KT / "SettingsActivity.kt")

    def test_without_plus_projectivy_gets_the_main_collection(self):
        pick = self.walls.split("fun pick(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("if (!owned) return main", pick)
        self.assertLess(pick.index("if (!owned) return main"), pick.index("main + plus"))
        live = self.walls.split("fun walls(", 1)[1].split("\n    )\n", 1)[0]
        self.assertIn("owned = Plus.owned(context)", live)

    def test_both_rows_are_plus_and_tell_projectivy(self):
        for row, setter in (("set_walls_row", "Prefs.setWallsSource"),
                            ("set_walls_night_row", "Prefs.setWallsNight")):
            body = self.settings.split(f"row(R.id.{row})", 1)[1].split("\n            }\n", 1)[0]
            self.assertIn("if (Plus.unlocked(this))", body, row)
            self.assertLess(body.index("if (Plus.unlocked(this))"), body.index(setter), row)
            self.assertIn("wallsChanged()", body, row)
        changed = self.settings.split("private fun wallsChanged()", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("ProjectivyWalls.notifyProjectivy(", changed)
        # Both rows sit in the Plus group, which is hidden outside the Play build.
        layout = read(APP / "src/main/res/layout/activity_settings.xml")
        group = layout.split('android:id="@+id/set_plus_group"', 1)[1]
        self.assertIn('android:id="@+id/set_walls_row"', group)
        self.assertIn('android:id="@+id/set_walls_night_row"', group)

    def test_the_broadcast_goes_to_projectivy_only(self):
        notify = self.walls.split("fun notifyProjectivy(", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn(".setPackage(PROJECTIVY)", notify)
        self.assertIn('PROJECTIVY = "com.spocky.projengmenu"', self.walls)
        # A star only matters when Projectivy is rotating the favourites.
        preview = read(KT / "WallpaperPreviewActivity.kt")
        fav = preview.split("private fun onFavouriteClicked()", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("if (ProjectivyWalls.usesFavourites(this))", fav)


class CallerTests(unittest.TestCase):
    def test_only_projectivy_may_write_the_settings(self):
        # The service is exported with no permission (Projectivy binds it),
        # so setPreferences checks its caller before anything is stored.
        service = read(KT / "WallsProviderService.kt")
        body = service.split("override fun setPreferences(", 1)[1].split("\n        }\n", 1)[0]
        self.assertIn("getPackagesForUid(Binder.getCallingUid())", body)
        guard = body.split("if (ProjectivyWalls.PROJECTIVY !in callers) {", 1)
        self.assertEqual(len(guard), 2, "no caller guard")
        refused, after = guard[1].split("\n            }\n", 1)
        self.assertIn("return", refused)
        self.assertIn("ProjectivyWalls.importPrefs(", after)
        # Refused quietly: a thrown exception would land in the caller.
        self.assertNotIn("throw ", body)
        self.assertNotIn("check(", body)


class OfflineTests(unittest.TestCase):
    def test_the_provider_never_downloads(self):
        for name in ("WallsProviderService.kt", "ProjectivyWalls.kt"):
            src = read(KT / name)
            for word in ("HttpURLConnection", "openConnection", "WallpaperDownloader", "URL("):
                self.assertNotIn(word, src, f"{name} uses {word}")
        # Projectivy fetches each wall itself, from the manifest's URL.
        self.assertIn("uri = wall.url", read(KT / "ProjectivyWalls.kt"))


if __name__ == "__main__":
    unittest.main()
