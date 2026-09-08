#!/usr/bin/env python3
"""
Contracts for Core Builds Pop.

Plain unittest over the repo tree, no Android SDK — same shape as
tests/test_wallpapers.py and tests/test_v151_robustness.py. Guards the things
that rot silently once two packs share one catalog and one source tree:

  * the two packs never disagree about coverage
  * Pop's module cannot collide with the classic pack at install time
  * the app/ -> pop/ mirror is current
  * the Pop wallpaper set is coherent, bundled, and actually dark

Run from anywhere:
    python3 tests/test_pop.py
"""
from __future__ import annotations

import json
import re
import sys
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

POP = ROOT / "pop"
POP_RES = POP / "src" / "main" / "res"
POP_PNG = POP_RES / "drawable-nodpi"
POP_ASSETS = POP / "src" / "main" / "assets"
APP = ROOT / "app" / "src" / "main"
CATALOG = json.loads((ROOT / "tools" / "catalog.json").read_text(encoding="utf-8"))
SUITE = json.loads((ROOT / "suite.json").read_text(encoding="utf-8"))
ICONS = CATALOG["icons"]


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


class ModuleTests(unittest.TestCase):
    def setUp(self):
        self.gradle = read(POP / "build.gradle.kts")
        self.manifest = read(POP / "src" / "main" / "AndroidManifest.xml")

    def test_module_is_registered_with_the_root_build(self):
        self.assertIn('include(":pop")', read(ROOT / "settings.gradle.kts"))

    def test_application_id_is_distinct(self):
        classic = SUITE["apps"]["iconpack"]["applicationId"]
        pop = SUITE["apps"]["pop"]["applicationId"]
        self.assertNotEqual(classic, pop)
        self.assertIn(f'applicationId = "{pop}"', self.gradle)

    def test_fileprovider_authority_cannot_collide(self):
        # Two installed packages sharing a FileProvider authority makes the
        # second install fail outright, and the error names nothing useful.
        self.assertIn('android:authorities="tv.corebuilds.iconpack.pop.update"',
                      self.manifest)
        self.assertNotIn('android:authorities="tv.corebuilds.iconpack.update"',
                         self.manifest)

    def test_updater_endpoints_come_from_buildconfig(self):
        # Shared Kotlin, two packs: a hardcoded URL here would have Pop polling
        # the classic pack's release manifest.
        for module in ("app", "pop"):
            src = read(ROOT / module / "build.gradle.kts")
            self.assertIn("UPDATE_AUTHORITY", src, module)
            self.assertIn("UPDATE_MANIFEST_URL", src, module)
        checker = read(APP / "java/tv/corebuilds/iconpack/UpdateChecker.kt")
        self.assertIn("BuildConfig.UPDATE_MANIFEST_URL", checker)
        installer = read(APP / "java/tv/corebuilds/iconpack/UpdateInstaller.kt")
        self.assertIn("BuildConfig.UPDATE_AUTHORITY", installer)

    def test_pop_manifest_url_points_at_pop_metadata(self):
        self.assertIn(SUITE["apps"]["pop"]["metadata"].split("/")[-1],
                      self.gradle)

    def test_kotlin_is_not_duplicated(self):
        self.assertFalse((POP / "src" / "main" / "java").exists(),
                         "pop/ must compile app/'s Kotlin, not a second copy")
        self.assertIn('java.srcDirs("../app/src/main/java")', self.gradle)

    def test_resources_are_not_shrunk(self):
        # Drawables resolve by name at runtime; shrinking strips every icon.
        self.assertIn("isShrinkResources = false", self.gradle)

    def test_release_tag_prefix_is_unique(self):
        prefixes = [a["tagPrefix"] for a in SUITE["apps"].values()]
        self.assertEqual(len(prefixes), len(set(prefixes)))


class CoverageTests(unittest.TestCase):
    def test_every_catalog_icon_has_both_rasters(self):
        missing = [i["drawable"] for i in ICONS
                   if not (POP_PNG / f"{i['drawable']}.png").exists()
                   or not (POP_PNG / f"{i['drawable']}_banner.png").exists()]
        self.assertEqual(missing, [], f"{len(missing)} icons without rasters")

    def test_packs_map_the_same_components(self):
        def comps(p: Path) -> set[str]:
            return {m.group(1) for m in re.finditer(
                r'component="ComponentInfo\{([^}]+)\}"', read(p))}
        import build_pop
        classic = comps(APP / "res" / "xml" / "appfilter.xml")
        pop = comps(POP_RES / "xml" / "appfilter.xml")
        self.assertEqual(classic - pop, set(),
                         "both packs generate from tools/catalog.json and must "
                         "not disagree about coverage")
        # Pop maps Projectivy's internal activities on top (4.70+).
        self.assertTrue(all(build_pop.PROJECTIVY_PKG in c for c in pop - classic),
                        f"unexpected extra Pop components: {sorted(pop - classic)[:5]}")

    def test_unthemed_apps_still_get_the_container(self):
        # Pop's whole claim is "one container". An app the pack does not cover
        # must still land in one, or the claim dies on the user's first screen.
        import build_pop
        from popart import SWATCHES
        xml = read(POP_RES / "xml" / "appfilter.xml")
        self.assertIn("<iconback ", xml)
        self.assertIn("<iconmask ", xml)
        self.assertIn("<iconupon ", xml)
        self.assertIn("<scale ", xml)
        for name in SWATCHES:
            stem = f"pop_back_{name.replace('pop_', '')}"
            self.assertTrue((POP_PNG / f"{stem}.png").exists(), stem)
        for stem in ("pop_mask", "pop_upon"):
            self.assertTrue((POP_PNG / f"{stem}.png").exists(), stem)

    def test_projectivy_internal_cards_exist(self):
        import build_pop
        for drawable, acts in build_pop.PROJECTIVY_INTERNALS:
            self.assertTrue((POP_PNG / f"pl_{drawable}_banner.png").exists(),
                            drawable)
            self.assertIn(drawable, build_pop.INTERNAL_GLYPH)
            self.assertIn(drawable, build_pop.INTERNAL_ACCENT)
            self.assertTrue(acts)

    def test_furniture_is_not_offered_as_a_pickable_icon(self):
        # iconback/mask/upon are compositing inputs. Listing them in the
        # picker grid invites users to assign a blank card to an app.
        grid = read(POP_RES / "xml" / "drawable.xml")
        for bad in ("pop_mask", "pop_upon", "pop_back_"):
            self.assertNotIn(f'drawable="{bad}', grid)

    def test_bundled_appfilter_matches_res(self):
        self.assertEqual(read(POP_RES / "xml" / "appfilter.xml"),
                         read(POP_ASSETS / "appfilter.xml"))

    def test_icon_arrays_are_parallel(self):
        root = ET.fromstring(read(POP_RES / "values" / "icon_pack.xml"))
        arrays = {a.get("name"): a.findall("item") for a in
                  root.findall("string-array")}
        lengths = {k: len(v) for k, v in arrays.items()}
        self.assertEqual(set(lengths.values()), {len(ICONS)}, lengths)

    def test_palette_is_locked_to_sixteen(self):
        from popart import PALETTE, SWATCHES, snap
        self.assertEqual(len(SWATCHES), len(PALETTE) + 2)
        landed = {snap(i["color"])[0] for i in ICONS}
        self.assertTrue(landed <= set(SWATCHES),
                        f"accents snapped outside the palette: "
                        f"{landed - set(SWATCHES)}")

    def test_snap_is_deterministic(self):
        from popart import snap
        for i in ICONS[:120]:
            self.assertEqual(snap(i["color"]), snap(i["color"]))

    def test_snap_is_closed_over_the_palette(self):
        # snap(swatch) must be that swatch. Otherwise any code that asks for a
        # specific colour by value gets a different one, silently.
        from popart import SWATCHES, snap
        for name, hexv in SWATCHES.items():
            self.assertEqual(snap(hexv), (name, hexv), name)

    def test_every_used_glyph_is_measured(self):
        metrics = json.loads(
            read(ROOT / "tools" / "pop_glyph_metrics.json"))["metrics"]
        used = {i["glyph"] for i in ICONS}
        self.assertEqual(used - set(metrics), set())


class MirrorTests(unittest.TestCase):
    def test_mirrored_resources_are_current(self):
        import build_pop
        stale = []
        for rel in build_pop.MIRROR_FILES:
            src, dst = APP / "res" / rel, POP_RES / rel
            if src.exists() and (not dst.exists()
                                 or dst.read_bytes() != src.read_bytes()):
                stale.append(rel)
        for d in build_pop.MIRROR_DIRS:
            src = APP / "res" / d
            if not src.is_dir():
                continue
            for f in src.rglob("*"):
                if not f.is_file():
                    continue
                dst = POP_RES / d / f.relative_to(src)
                if not dst.exists() or dst.read_bytes() != f.read_bytes():
                    stale.append(f"{d}/{f.relative_to(src)}")
        self.assertEqual(stale, [], "run python tools/build_pop.py")

    def test_pop_names_itself(self):
        strings = read(POP_RES / "values" / "strings.xml")
        self.assertIn("<string name=\"app_name\">Core Builds Pop</string>",
                      strings)

    def test_shared_ui_strings_are_not_forked(self):
        # Every string key in app/ must exist in pop/, or the shared Kotlin
        # will crash on a missing resource in one build and not the other.
        def keys(p: Path) -> set[str]:
            return set(re.findall(r'<string name="([^"]+)">', read(p)))
        self.assertEqual(keys(APP / "res" / "values" / "strings.xml")
                         - keys(POP_RES / "values" / "strings.xml"), set())


class WallpaperTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads(read(ROOT / "Wallpapers" / "pop-manifest.json"))
        self.walls = self.manifest["wallpapers"]

    def test_count_matches_entries(self):
        self.assertEqual(self.manifest["count"], len(self.walls))

    def test_bundled_copy_is_identical(self):
        self.assertEqual(
            read(ROOT / "Wallpapers" / "pop-manifest.json"),
            read(POP_ASSETS / "manifest" / "wallpapers.json"))

    def test_pop_walls_are_separate_from_the_classic_collection(self):
        classic = json.loads(read(ROOT / "Wallpapers" / "manifest.json"))
        classic_names = {w["name"] for w in classic["wallpapers"]}
        self.assertTrue(classic_names.isdisjoint({w["name"] for w in self.walls}))
        # The classic pack ships 50 and says so in the README; changing it from
        # a Pop script would make that claim false. Pop owns none of it.
        self.assertEqual(classic["count"], 50)

    def test_entries_are_https_github_and_4k(self):
        for w in self.walls:
            self.assertTrue(w["url"].startswith("https://raw.githubusercontent.com/"),
                            w["url"])
            self.assertTrue(w["url"].endswith((".png", ".jpg")), w["url"])
            self.assertEqual(w["resolution"], "3840x2160", w["name"])

    def test_full_size_and_thumb_exist_for_every_entry(self):
        for w in self.walls:
            full = ROOT / "Wallpapers" / w["series"] / w["url"].rsplit("/", 1)[1]
            thumb_name = w["thumb"].rsplit("/", 1)[1]
            self.assertTrue(full.exists(), full)
            self.assertTrue((ROOT / "Wallpapers" / "thumbs" / thumb_name).exists())
            self.assertTrue((POP_ASSETS / "wallpapers_thumbs" / thumb_name).exists(),
                            f"{thumb_name} is not bundled — the in-app grid "
                            f"would render a hole")

    def test_names_are_unique(self):
        names = [w["name"] for w in self.walls]
        self.assertEqual(len(names), len(set(names)))


if __name__ == "__main__":
    unittest.main(verbosity=2)
