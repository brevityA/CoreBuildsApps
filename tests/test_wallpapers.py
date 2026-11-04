#!/usr/bin/env python3
"""
Contracts for the in-app wallpapers feature.

Mirrors test_v151_robustness.py: plain unittest over the repo tree, no Android
SDK. Guards the things that would otherwise rot silently:
  * manifest is coherent and matches the bundled copy in assets/
  * every wallpaper has a bundled thumbnail the browser can decode
  * the Kotlin surfaces declare the permissions, activities, and strings they use
  * full-size series-6 files exist on disk for every manifest url, and every
    entry's declared resolution is the size the file actually is
  * a retired series is really gone (no orphaned files, no orphaned entries)

Run from anywhere:
    python3 tests/test_wallpapers.py
"""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
WALLPAPERS = ROOT / "Wallpapers"
MANIFEST = WALLPAPERS / "manifest.json"
# Core Builds Plus walls: their own manifest, bundled by the Google Play build only.
PLUS_MANIFEST = WALLPAPERS / "plus-manifest.json"
PLUS_BUNDLED_MANIFEST = ROOT / "app/src/play/assets/manifest/plus-wallpapers.json"
PLUS_BUNDLED_THUMBS = ROOT / "app/src/play/assets/plus_thumbs"
BUNDLED_MANIFEST = ROOT / "app/src/main/assets/manifest/wallpapers.json"
SERIES6 = "series-6-circuit-core"
SERIES8 = "series-8-amoled"
# Series that intentionally live outside the classic manifest: series 0 is the
# photographic originals kept for history.
UNMANIFESTED = {"series-0-originals"}
THUMBS = WALLPAPERS / "thumbs"
BUNDLED_THUMBS = ROOT / "app/src/main/assets/wallpapers_thumbs"
MAIN = ROOT / "app" / "src" / "main"
RES = MAIN / "res"
ANDROID_MANIFEST = MAIN / "AndroidManifest.xml"
JAVA = MAIN / "java/tv/corebuilds/iconpack"


def _read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


class ManifestTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads(_read(MANIFEST))
        self.walls = self.manifest["wallpapers"]

    def test_count_matches_entries(self):
        self.assertEqual(self.manifest["count"], len(self.walls))

    def test_active_series_use_fixed_wall_units(self):
        from collections import Counter
        counts = Counter(w["series"] for w in self.walls)
        self.assertEqual(counts, {
            # Fieldwork (series 1, 24 walls) retired in 2.4.0: twenty-four
            # near-identical gradient washes, the feedback's "blurs". Numbers
            # 01-24 stay empty, as series 4 and 5 do.
            "series-2-motion": 12,
            "series-3-horizons": 12,
            "series-6-circuit-core": 12,
            "series-7-retrowave": 12,
            "series-8-amoled": 12,
            "series-9-deep-space": 12,
            # 2.4.0 equalises every active series at twelve. Cinema, Earth
            # and Wild are still being brought up (six Cinema scenes are
            # drawn with their loops; Earth and Wild are generated imagery
            # at 1376x768, Circuit Core's precedent) - update these as each
            # lands, and never leave a series at an odd count.
            "series-10-cinema": 12,
            "series-11-icon-echo": 12,
            "series-12-earth": 12,
            "series-13-wild": 12,
            "series-14-flux": 12,
        })

    def test_entries_are_https_github(self):
        for w in self.walls:
            self.assertTrue(w["url"].startswith("https://raw.githubusercontent.com/"), w["url"])
            self.assertTrue(w["url"].endswith((".png", ".jpg")), w["url"])

    def test_names_unique_and_numbered(self):
        names = [w["name"] for w in self.walls]
        self.assertEqual(len(names), len(set(names)), "duplicate wallpaper names")

    def test_since_is_a_version_and_the_new_chip_has_a_set(self):
        """`since` is optional, but where present it is dotted-numeric and the
        newest value marks a non-empty set - that is what WallpapersActivity's
        New chip and the home rail's "N new" hint count. A release that ships
        walls without stamping them makes the chip lie by omission."""
        import re
        stamped = [w for w in self.walls if "since" in w]
        for w in stamped:
            self.assertRegex(w["since"], r"^\d+\.\d+\.\d+$", w["name"])
        self.assertTrue(stamped, "no wall carries `since`; the New chip would never show")
        newest = max((w["since"] for w in stamped),
                     key=lambda v: tuple(int(p) for p in v.split(".")))
        new = [w for w in stamped if w["since"] == newest]
        self.assertGreaterEqual(len(new), 1)
        # A stamped wall's file is at least as new as an unstamped one's
        # cannot be checked from the manifest; what can is that the stamp
        # never names a release newer than the app's own version.
        import re as _re
        gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        app_v = _re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
        self.assertLessEqual(tuple(int(p) for p in newest.split(".")),
                             tuple(int(p) for p in app_v.split(".")), f"since={newest} is after app {app_v}")

    def test_resolution_matches_the_file_on_disk(self):
        """The manifest promises a size; the bytes must be that size.

        The collection is no longer uniformly 4K — series 5 ships at the
        resolution its sources were authored at, because upscaling a 1376x768
        JPEG into a 3840x2160 filename would be a lie the in-app browser then
        repeats to every TV. So the gate is honesty, not a fixed number.
        """
        from PIL import Image
        for w in self.walls:
            f = WALLPAPERS / w["series"] / w["url"].rsplit("/", 1)[-1]
            self.assertTrue(f.exists(), f"no file for {w['name']}: {f}")
            with Image.open(f) as im:
                self.assertEqual(w["resolution"], f"{im.width}x{im.height}",
                                 f"{f.name} is {im.width}x{im.height}, manifest says {w['resolution']}")

    def test_series6_present_and_12_branded_walls(self):
        s6 = [w for w in self.walls if w["series"] == SERIES6]
        self.assertEqual(len(s6), 12, "expected 12 Circuit Core walls")
        nums = sorted(int(re.match(r"(\d+)", w["name"]).group(1)) for w in s6)
        self.assertEqual(nums, list(range(41, 51)) + [79, 80])

    def test_retired_series4_is_not_in_the_manifest(self):
        # v1.8.6 replaced the 4K Core Mark series with series 5. The files and
        # the thumbs go with it — a manifest entry with no file is a 404 in the
        # app, and a file with no manifest entry is dead weight in the repo.
        self.assertFalse([w for w in self.walls if w["series"] == "series-4-core-mark"])

    def test_no_orphaned_series_on_disk(self):
        on_disk = {d.name for d in (ROOT / "Wallpapers").iterdir()
                   if d.is_dir() and d.name.startswith("series-")}
        plus = {w["series"] for w in json.loads(_read(PLUS_MANIFEST))["wallpapers"]}
        in_manifest = {w["series"] for w in self.walls} | plus
        self.assertEqual(on_disk - in_manifest, UNMANIFESTED,
                         "series directories the manifest does not index")
        self.assertEqual(in_manifest - on_disk, set(),
                         "manifest series with no directory on disk")

    def test_bundled_manifest_matches_repo_manifest(self):
        bundled = json.loads(_read(BUNDLED_MANIFEST))
        self.assertEqual(bundled["wallpapers"], self.walls)


class ThumbnailTests(unittest.TestCase):
    def setUp(self):
        self.walls = json.loads(_read(MANIFEST))["wallpapers"]

    def test_every_wallpaper_has_a_thumbnail_file(self):
        missing = []
        for w in self.walls:
            fname = w["url"].rsplit("/", 1)[-1].rsplit(".", 1)[0] + ".jpg"
            if not (THUMBS / fname).exists():
                missing.append(fname)
        self.assertFalse(missing, f"missing thumbs: {missing}")

    def test_thumbs_bundled_into_assets(self):
        missing = []
        for w in self.walls:
            fname = w["url"].rsplit("/", 1)[-1].rsplit(".", 1)[0] + ".jpg"
            if not (BUNDLED_THUMBS / fname).exists():
                missing.append(fname)
        self.assertFalse(missing, f"thumbs not bundled: {missing}")

    def test_thumbs_are_small_jpgs(self):
        # Grid must stay instant; a thumb should never approach full-image size.
        for p in BUNDLED_THUMBS.glob("*.jpg"):
            self.assertLess(p.stat().st_size, 60_000, f"{p.name} is too large")

    def test_bundled_thumbs_are_exactly_the_manifest_set(self):
        # 20 KB of APK per stray thumb; the count is the collection's size.
        want = {w["url"].rsplit("/", 1)[-1].rsplit(".", 1)[0] + ".jpg"
                for w in self.walls}
        have = {p.name for p in BUNDLED_THUMBS.glob("*.jpg")}
        self.assertEqual(have, want)

    def test_bundled_thumbs_match_the_repo_thumbs(self):
        # A re-render keeps its file names, so a name check passes on stale
        # art: 2.4.0 and 2.4.1 shipped Circuit Core's old thumbs this way.
        stale = []
        for w in self.walls:
            fname = w["url"].rsplit("/", 1)[-1].rsplit(".", 1)[0] + ".jpg"
            bundled = BUNDLED_THUMBS / fname
            if bundled.exists() and bundled.read_bytes() != (THUMBS / fname).read_bytes():
                stale.append(fname)
        self.assertFalse(stale, "bundled thumbs differ from Wallpapers/thumbs "
                                f"(run tools/sync_wallpaper_manifest.py): {stale}")


class PlusCollectionTests(unittest.TestCase):
    """Series 15 (Spectra) and 16 (Aurum), the Core Premium walls (Plus in
    code): indexed apart from the main collection and bundled only into the
    Google Play build."""

    # Each Premium series and the tool that draws it, in manifest order.
    GENERATORS = {
        "series-15-spectra": "tools/build_plus_wallpapers.py",
        "series-16-aurum": "tools/build_aurum_wallpapers.py",
    }

    def setUp(self):
        self.manifest = json.loads(_read(PLUS_MANIFEST))
        self.walls = self.manifest["wallpapers"]

    def test_two_series_of_twelve_counted(self):
        self.assertEqual(self.manifest["count"], len(self.walls))
        self.assertEqual(len(self.walls), 24)
        series = [w["series"] for w in self.walls]
        # One series after the other, so the Premium chip shows them as sets.
        self.assertEqual(series, sorted(series, key=list(self.GENERATORS).index))
        for name in self.GENERATORS:
            self.assertEqual(series.count(name), 12, name)

    def test_the_copy_names_the_count(self):
        # The purchase card and Settings say how many walls Premium adds.
        n = len(self.walls)
        main = _read(ROOT / "app/src/main/res/values/strings.xml")
        play = _read(ROOT / "app/src/play/res/values/strings.xml")
        self.assertIn(f"the {n} Spectra and Aurum wallpapers", main)
        self.assertIn(f"Spectra and Aurum, {n} wallpapers, are yours", play)
        self.assertIn("<string name=\"support_title\">Core Premium</string>", play)
        self.assertNotIn("Core Builds Plus</string>", play)

    def test_numbers_do_not_collide_with_the_main_collection(self):
        main = json.loads(_read(MANIFEST))["wallpapers"]
        def number(w):
            return int(w["name"].split()[0])
        self.assertFalse({number(w) for w in main} & {number(w) for w in self.walls})
        self.assertEqual(len({number(w) for w in self.walls}), len(self.walls))

    def test_files_exist_at_their_declared_resolution(self):
        from PIL import Image
        for w in self.walls:
            path = WALLPAPERS / w["series"] / w["url"].rsplit("/", 1)[-1]
            with Image.open(path) as im:
                self.assertEqual("x".join(map(str, im.size)), w["resolution"], path.name)
            self.assertTrue((THUMBS / w["thumb"].rsplit("/", 1)[-1]).exists(), w["name"])

    def test_bundled_only_into_the_play_build(self):
        self.assertEqual(json.loads(_read(PLUS_BUNDLED_MANIFEST)), self.manifest)
        want = {w["thumb"].rsplit("/", 1)[-1] for w in self.walls}
        self.assertEqual({p.name for p in PLUS_BUNDLED_THUMBS.glob("*.jpg")}, want)
        for name in want:
            self.assertEqual((PLUS_BUNDLED_THUMBS / name).read_bytes(), (THUMBS / name).read_bytes())
            # Not in the main assets every build ships.
            self.assertFalse((BUNDLED_THUMBS / name).exists(), name)
        self.assertFalse((ROOT / "app/src/main/assets/manifest/plus-wallpapers.json").exists())

    def test_the_series_has_its_generator(self):
        for w in self.walls:
            gen = _read(ROOT / self.GENERATORS[w["series"]])
            self.assertIn(w["url"].rsplit("/", 1)[-1].removesuffix(".png").split("-", 2)[2], gen)
            self.assertIn(w["series"], gen)


def _cache_keys():
    import importlib.util
    spec = importlib.util.spec_from_file_location(
        "check_wallpaper_cache_keys", ROOT / "tools" / "check_wallpaper_cache_keys.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class CacheNameTests(unittest.TestCase):
    """A re-render keeps its URL, so it must move to a new cache name.

    The full check reads the release tags (tools/check_wallpaper_cache_keys.py,
    a prepare_release gate); CI's shallow clone has none, so these pin the rule
    and the walls 2.3.3 redrew without a stamp."""

    def setUp(self):
        self.mod = _cache_keys()
        self.walls = json.loads(_read(MANIFEST))["wallpapers"]

    def test_redraw_under_the_same_cache_name_is_caught(self):
        history = [("2.3.2", {"w.png": ("old", "")}), ("2.4.1", {"w.png": ("new", "")})]
        problems = self.mod.collisions({"w.png": ("new", "")}, history)
        self.assertEqual(len(problems), 1)
        self.assertIn("v2.3.2", problems[0])

    def test_a_new_stamp_moves_the_cache_name(self):
        history = [("2.3.2", {"w.png": ("old", "")}), ("2.4.1", {"w.png": ("new", "")})]
        self.assertEqual(self.mod.collisions({"w.png": ("new", "2.3.3")}, history), [])

    def test_a_stamp_reused_across_a_redraw_is_caught(self):
        # 2.4.1+ apps cache under <since>-<file>; a redraw that keeps the stamp
        # hits the copy downloaded under it.
        history = [("2.4.1", {"w.png": ("old", "2.4.0")})]
        self.assertEqual(len(self.mod.collisions({"w.png": ("new", "2.4.0")}, history)), 1)

    def test_unchanged_pictures_need_no_stamp(self):
        history = [("1.2.0", {"w.png": ("same", "")})]
        self.assertEqual(self.mod.collisions({"w.png": ("same", "")}, history), [])

    def test_the_tool_models_the_apps_cache_name(self):
        src = _read(JAVA / "WallpaperCatalog.kt")
        self.assertIn("since.filter { it.isLetterOrDigit() || it == '.' || it == '-' }", src)
        self.assertIn('if (stamp.isEmpty()) cacheName else "$stamp-$cacheName"', src)
        self.assertEqual(self.mod.store_name("a.png", "2.3.3", (2, 4, 2)), "2.3.3-a.png")
        self.assertEqual(self.mod.store_name("a.png", "2.3.3", (2, 4, 0)), "a.png")

    def test_walls_redrawn_in_2_3_3_carry_their_stamp(self):
        # Redrawn in 2.3.3 (#299) with no stamp; devices kept the old pictures
        # through 2.4.1.
        redrawn = {"52 Shockwave", "97 Marquee Lights", "99 Projector Beam",
                   "100 Neon Lounge", "101 Late Rentals", "102 Box Office"}
        stamps = {w["name"]: w.get("since") for w in self.walls if w["name"] in redrawn}
        self.assertEqual(stamps, {n: "2.3.3" for n in redrawn})

    def test_release_runs_the_check(self):
        self.assertIn('"tools/check_wallpaper_cache_keys.py"',
                      _read(ROOT / "tools" / "prepare_release.py"))


class Series6FileTests(unittest.TestCase):
    def setUp(self):
        self.walls = json.loads(_read(MANIFEST))["wallpapers"]

    def test_series6_full_files_exist(self):
        missing = []
        for w in self.walls:
            if w["series"] != SERIES6:
                continue
            fname = w["url"].rsplit("/", 1)[-1]
            if not (WALLPAPERS / SERIES6 / fname).exists():
                missing.append(fname)
        self.assertFalse(missing, f"missing series-6 files: {missing}")

    def test_series6_files_are_tv_aspect(self):
        """16:9 (tolerant of a sub-1% rounding gap) so the system setter crops nothing meaningful."""
        from PIL import Image
        for p in sorted((WALLPAPERS / SERIES6).glob("*.jpg")):
            with Image.open(p) as im:
                self.assertAlmostEqual(
                    im.width / im.height, 16 / 9, delta=0.02,
                    msg=f"{p.name} is {im.width}x{im.height}, not a TV frame")

    def test_no_series4_files_left_behind(self):
        self.assertFalse((WALLPAPERS / "series-4-core-mark").exists(),
                         "retired series-4 files are still committed")


class Series8AmoledTests(unittest.TestCase):
    def setUp(self):
        self.walls = json.loads(_read(MANIFEST))["wallpapers"]
        self.amoled = [w for w in self.walls if w["series"] == SERIES8]

    def test_series8_has_twelve_numbered_4k_walls(self):
        self.assertEqual(len(self.amoled), 12)
        nums = sorted(int(re.match(r"(\d+)", w["name"]).group(1))
                      for w in self.amoled)
        self.assertEqual(nums, list(range(69, 79)) + [83, 84])
        self.assertEqual({w["resolution"] for w in self.amoled}, {"3840x2160"})

    def test_series8_is_at_least_half_exact_black(self):
        """Near-black is not off on OLED; only literal RGB zero counts."""
        from PIL import Image
        for wall in self.amoled:
            path = WALLPAPERS / SERIES8 / Path(wall["url"]).name
            with Image.open(path) as im:
                rgb = im.convert("RGB")
                colours = rgb.getcolors(maxcolors=rgb.width * rgb.height)
                self.assertIsNotNone(colours, f"too many colours to audit: {path.name}")
                black = next((count for count, colour in colours
                              if colour == (0, 0, 0)), 0)
                fraction = black / (rgb.width * rgb.height)
                self.assertGreaterEqual(
                    fraction, 0.50,
                    f"{path.name} is only {fraction:.1%} exact #000000",
                )


class AndroidWiringTests(unittest.TestCase):
    def setUp(self):
        self.manifest_xml = _read(ANDROID_MANIFEST)
        self.strings_xml = _read(RES / "values/strings.xml")

    def test_set_wallpaper_permission_declared(self):
        root = ET.fromstring(self.manifest_xml)
        ns = "{http://schemas.android.com/apk/res/android}"
        perms = {e.get(ns + "name") for e in root.findall("uses-permission")}
        self.assertIn("android.permission.SET_WALLPAPER", perms)

    def test_wallpaper_activities_declared(self):
        for cls in (".WallpapersActivity", ".WallpaperPreviewActivity"):
            self.assertIn(cls, self.manifest_xml, f"{cls} missing from manifest")

    def test_wallpaper_strings_present(self):
        for key in ("wp_entry", "wp_set_wallpaper", "wp_count_fmt",
                    "wp_downloading_fmt", "wp_set_done"):
            self.assertRegex(
                self.strings_xml,
                rf'<string name="{key}"',
                f"string/{key} missing",
            )

    def test_wallpaper_layouts_exist(self):
        for name in ("activity_wallpapers.xml", "activity_wallpaper_preview.xml",
                     "item_wallpaper.xml", "bg_ghost.xml", "bg_scrim.xml"):
            self.assertTrue((RES / ("drawable" if name.startswith("bg_") else "layout") / name).exists(),
                            name)

    def test_entry_point_wired_in_main(self):
        main = _read(JAVA / "MainActivity.kt")
        self.assertIn("WallpapersActivity", main)
        self.assertIn("wallpapers_entry", main)

    def test_card_floor_uses_the_generators_dark_threshold(self):
        # The preview's card-floor label and the generators' dark-coverage
        # floor are one measure: share of pixels at or under 14% luma. The
        # generators work on 0-1 (`L / 255 <= 0.14`); WallpaperSeed.luma is
        # 0-255, so its threshold must be scaled. Compared against a bare
        # 0.14f, only pure black counted and nearly every wall read bright.
        seed = _read(JAVA / "WallpaperSeed.kt")
        self.assertIn("DARK_LUMA = 0.14f * 255f", seed)
        self.assertRegex(seed, r"luma\(p\) <= DARK_LUMA")
        self.assertNotRegex(seed, r"luma\(\w+\) <= 0\.\d+f\)")
        classic = _read(ROOT / "tools/build_classic_wallpapers.py")
        self.assertIn("/ 255.0", classic)
        self.assertIn("(a <= 0.14)", classic)

    def test_cache_name_changes_when_a_wall_is_re_rendered(self):
        # 2.4.0 re-rendered walls under their old URLs. Cached under the bare
        # file name, a wall downloaded on 2.3.x kept showing its old picture
        # behind the new thumbnail. The cache key carries the manifest's
        # `since`; the export name (Pictures/CoreBuilds) stays the file name.
        catalog = _read(JAVA / "WallpaperCatalog.kt")
        self.assertIn("val storeName: String", catalog)
        self.assertIn('"$stamp-$cacheName"', catalog)
        downloader = _read(JAVA / "WallpaperDownloader.kt")
        self.assertIn("cachedFile(context, wallpaper.storeName)", downloader)
        self.assertIn("fetchUrl(context, wallpaper.url, wallpaper.storeName, callback)", downloader)
        self.assertNotIn("wallpaper.cacheName", downloader)
        preview = _read(JAVA / "WallpaperPreviewActivity.kt")
        self.assertIn("fetchUrl(this, wp.url, wp.storeName)", preview)
        self.assertNotIn("fetchUrl(this, wp.url, wp.cacheName)", preview)
        exporter = _read(JAVA / "WallpaperExporter.kt")
        self.assertIn("val cacheName = wp.cacheName", exporter)
        # The stamp goes into a file name the downloader validates against
        # [A-Za-z0-9._-]+, so the manifest may only use those characters.
        manifest = json.loads(_read(ROOT / "Wallpapers/manifest.json"))
        walls = manifest["wallpapers"] if isinstance(manifest, dict) else manifest
        for wall in walls:
            self.assertRegex(wall.get("since", ""), r"^[0-9A-Za-z.-]*$", wall["name"])

    def test_no_full_wallpapers_bundled_into_apk(self):
        # Full 4K images download on demand; assets should only hold thumbs + manifest.
        assets = ROOT / "app/src/main/assets"
        full = [p for p in assets.rglob("*.png")]
        self.assertFalse(full, f"full-size PNGs bundled (would bloat APK): {full}")
        # The bundled wallpaper assets are exactly thumbs + manifest.
        self.assertTrue((assets / "manifest/wallpapers.json").exists())
        self.assertTrue(any((assets / "wallpapers_thumbs").glob("*.jpg")))


class FavouritesTests(unittest.TestCase):
    """2.5.0 wallpaper favourites: starred in the preview, gathered under a
    Favourites chip in the browser, a star on the tile."""

    def setUp(self):
        self.prefs = _read(JAVA / "Prefs.kt")
        self.preview = _read(JAVA / "WallpaperPreviewActivity.kt")
        self.browser = _read(JAVA / "WallpapersActivity.kt")
        self.adapter = _read(JAVA / "WallpaperAdapter.kt")

    def test_a_favourite_is_keyed_by_the_name_a_re_render_keeps(self):
        # storeName carries the manifest's `since` and moves on every
        # re-render; a favourite stored under it would silently drop the
        # next time its wall was redrawn. cacheName is the file name.
        self.assertIn("Prefs.setWallpaperFavourite(this, wp.cacheName, !starred)", self.preview)
        self.assertIn("wp.cacheName in Prefs.wallpaperFavourites(this)", self.preview)
        self.assertIn("all.filter { it.cacheName in favourites }", self.browser)
        self.assertIn("item.cacheName in favourites", self.adapter)
        for source in (self.preview, self.browser, self.adapter):
            self.assertNotRegex(source, r"storeName\s+in\s+(favourites|Prefs\.wallpaperFavourites)")

    def test_the_stored_set_is_copied_before_it_is_changed(self):
        # SharedPreferences hands back its own set; editing it in place is
        # documented as unsafe and can lose the write.
        self.assertIn("getStringSet(KEY_WP_FAVOURITES, null)?.toSet()", self.prefs)
        self.assertIn("wallpaperFavourites(context).toMutableSet()", self.prefs)

    def test_the_preview_row_links_back_save_favourite_set(self):
        root = ET.parse(RES / "layout/activity_wallpaper_preview.xml").getroot()
        ns = "{http://schemas.android.com/apk/res/android}"
        ids = {el.get(ns + "id", "").split("/")[-1]: el for el in root.iter()}
        row = [i for i, el in ids.items()
               if i.startswith("preview_") and el.get(ns + "focusable") == "true"]
        self.assertEqual(row, ["preview_back", "preview_save", "preview_fav", "preview_set"])

        def edge(view, attr):
            return (ids[view].get(ns + attr) or "").split("/")[-1]

        self.assertEqual(edge("preview_save", "nextFocusRight"), "preview_fav")
        self.assertEqual(edge("preview_fav", "nextFocusRight"), "preview_set")
        self.assertEqual(edge("preview_fav", "nextFocusLeft"), "preview_save")
        self.assertEqual(edge("preview_set", "nextFocusLeft"), "preview_fav")

    def test_the_row_is_relinked_when_save_leaves_it(self):
        # Save is GONE for a loop. Before API 26, FocusFinder returns a GONE
        # nextFocus target instead of following past it, and the cursor
        # stops; the row must be relinked through the visible buttons.
        show = self.preview[self.preview.index("private fun showWallpaper()"):]
        show = show[:show.index("\n    }\n")]
        self.assertLess(show.index("saveButton.visibility ="), show.index("syncFocusChain()"))
        self.assertIn(".filter { it.visibility == View.VISIBLE }", self.preview)

    def test_the_chip_appears_only_with_a_favourite_to_show(self):
        self.assertIn("if (starred > 0)", self.browser)
        self.assertIn("FAV_KEY -> favouriteWalls()", self.browser)
        # Un-starring the last one while it is showing falls back to All.
        self.assertIn("if (showingFavourites && favouriteWalls().isEmpty()) series = null",
                      self.browser)

    def test_returning_from_the_preview_re_reads_the_stars(self):
        resume = self.browser[self.browser.index("override fun onResume()"):]
        resume = resume[:resume.index("\n    }\n")]
        self.assertIn("Prefs.wallpaperFavourites(this)", resume)
        self.assertIn("adapter.setFavourites(now)", resume)
        self.assertIn("bindChips()", resume)
        # The star is a partial bind, so the tile under the cursor stays put.
        self.assertIn("notifyItemChanged(i, FAVOURITE_PAYLOAD)", self.adapter)

    def test_strings_and_badge_exist(self):
        strings = _read(RES / "values/strings.xml")
        for key in ("chip_favourites_fmt", "wp_fav_add", "wp_fav_remove",
                    "wp_fav_add_desc", "wp_fav_remove_desc", "wp_fav_badge",
                    "wp_fav_spoken"):
            self.assertRegex(strings, rf'<string name="{key}"', key)
        tile = _read(RES / "layout/item_wallpaper.xml")
        self.assertIn('android:id="@+id/wp_fav_badge"', tile)
        self.assertIn('android:layout_gravity="top|end"', tile)


if __name__ == "__main__":
    unittest.main(verbosity=2)
