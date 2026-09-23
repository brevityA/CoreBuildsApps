#!/usr/bin/env python3
"""
Contracts for Core Builds Banners.

Plain unittest over the repo tree, no Android SDK — same shape as tests/test_pop.py:
  * the packs never disagree about catalog coverage
  * Banners module cannot collide with classic or Pop at install time
  * the app/ -> banners/ mirror is current
  * Banners ships 16:9 banner PNGs under base names

Run from anywhere:
    python3 tests/test_banners.py
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

BANNERS = ROOT / "banners"
BANNERS_RES = BANNERS / "src" / "main" / "res"
BANNERS_PNG = BANNERS_RES / "drawable-nodpi"
BANNERS_ASSETS = BANNERS / "src" / "main" / "assets"
APP = ROOT / "app" / "src" / "main"
CATALOG = json.loads((ROOT / "tools" / "catalog.json").read_text(encoding="utf-8"))
SUITE = json.loads((ROOT / "suite.json").read_text(encoding="utf-8"))
ICONS = CATALOG["icons"]


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


class ModuleTests(unittest.TestCase):
    def setUp(self):
        self.gradle = read(BANNERS / "build.gradle.kts")
        self.manifest = read(BANNERS / "src" / "main" / "AndroidManifest.xml")

    def test_module_is_registered_with_the_root_build(self):
        self.assertIn('include(":banners")', read(ROOT / "settings.gradle.kts"))

    def test_application_id_is_distinct(self):
        classic = SUITE["apps"]["iconpack"]["applicationId"]
        pop = SUITE["apps"]["pop"]["applicationId"]
        banners = SUITE["apps"]["banners"]["applicationId"]
        self.assertNotEqual(classic, banners)
        self.assertNotEqual(pop, banners)
        self.assertIn(f'applicationId = "{banners}"', self.gradle)

    def test_fileprovider_authority_cannot_collide(self):
        self.assertIn('android:authorities="tv.corebuilds.iconpack.banners.update"',
                      self.manifest)
        self.assertNotIn('android:authorities="tv.corebuilds.iconpack.update"',
                         self.manifest)
        self.assertNotIn('android:authorities="tv.corebuilds.iconpack.pop.update"',
                         self.manifest)

    def test_updater_endpoints_come_from_buildconfig(self):
        src = read(BANNERS / "build.gradle.kts")
        self.assertIn("UPDATE_AUTHORITY", src)
        self.assertIn("UPDATE_MANIFEST_URL", src)

    def test_banners_manifest_url_points_at_banners_metadata(self):
        self.assertIn(SUITE["apps"]["banners"]["metadata"].split("/")[-1],
                      self.gradle)

    def test_kotlin_is_not_duplicated(self):
        self.assertFalse((BANNERS / "src" / "main" / "java").exists(),
                         "banners/ must compile app/'s Kotlin, not a second copy")
        self.assertIn('java.srcDirs("../app/src/main/java")', self.gradle)

    def test_resources_are_not_shrunk(self):
        self.assertIn("isShrinkResources = false", self.gradle)

    def test_release_tag_prefix_is_unique(self):
        prefixes = [a["tagPrefix"] for a in SUITE["apps"].values()]
        self.assertEqual(len(prefixes), len(set(prefixes)))


class CoverageTests(unittest.TestCase):
    def test_every_catalog_icon_has_banner_raster_under_base_name(self):
        missing = [i["drawable"] for i in ICONS
                   if not (BANNERS_PNG / f"{i['drawable']}.png").exists()]
        self.assertEqual(missing, [], f"{len(missing)} icons without banner rasters")

    def test_packs_map_the_same_components(self):
        def comps(p: Path) -> set[str]:
            return {m.group(1) for m in re.finditer(
                r'component="ComponentInfo\{([^}]+)\}"', read(p))}
        classic = comps(APP / "res" / "xml" / "appfilter.xml")
        banners = comps(BANNERS_RES / "xml" / "appfilter.xml")
        self.assertEqual(classic, banners,
                         "both packs generate from tools/catalog.json and must "
                         "map the exact same components")

    def test_bundled_appfilter_matches_res(self):
        self.assertEqual(read(BANNERS_RES / "xml" / "appfilter.xml"),
                         read(BANNERS_ASSETS / "appfilter.xml"))

    def test_icon_arrays_are_parallel(self):
        root = ET.fromstring(read(BANNERS_RES / "values" / "icon_pack.xml"))
        arrays = {a.get("name"): a.findall("item") for a in
                  root.findall("string-array")}
        lengths = {k: len(v) for k, v in arrays.items()}
        self.assertEqual(set(lengths.values()), {len(ICONS)}, lengths)


class MirrorTests(unittest.TestCase):
    def test_mirrored_resources_are_current(self):
        import build_banner_pack
        stale = []
        for rel in build_banner_pack.MIRROR_FILES:
            src, dst = APP / "res" / rel, BANNERS_RES / rel
            if src.exists() and (not dst.exists()
                                 or dst.read_bytes() != src.read_bytes()):
                stale.append(rel)
        for d in build_banner_pack.MIRROR_DIRS:
            src = APP / "res" / d
            if not src.is_dir():
                continue
            for f in src.rglob("*"):
                if not f.is_file():
                    continue
                dst = BANNERS_RES / d / f.relative_to(src)
                if not dst.exists() or dst.read_bytes() != f.read_bytes():
                    stale.append(f"{d}/{f.relative_to(src)}")
        self.assertEqual(stale, [], "run python tools/build_banner_pack.py")

    def test_banners_names_itself(self):
        strings = read(BANNERS_RES / "values" / "strings.xml")
        self.assertIn("<string name=\"app_name\">Core Builds Banners</string>",
                      strings)

    def test_shared_ui_strings_are_not_forked(self):
        def keys(p: Path) -> set[str]:
            return set(re.findall(r'<string name="([^"]+)">', read(p)))
        self.assertEqual(keys(APP / "res" / "values" / "strings.xml")
                         - keys(BANNERS_RES / "values" / "strings.xml"), set())


if __name__ == "__main__":
    unittest.main(verbosity=2)
