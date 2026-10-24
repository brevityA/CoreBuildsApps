#!/usr/bin/env python3
"""
Contracts for Core Views as live wallpapers: the season the icon pack plays.

The catalogue is a content line (`views/`), not a product; what this guards is
the join between it and the app. Plain unittest over the repo tree, no Android
SDK, mirroring tests/test_live_wallpaper.py:

  * the roster, the Kotlin list and views/catalog.json are one list, entry for
    entry - a view added to the catalogue but never generated is a missing
    wallpaper, and a loop with no view is a wallpaper of nothing
  * every loop has a real MP4 in Motion/live/, a poster in Motion/live/thumbs/
    and a copy bundled in the APK for the engine's pre-download frame
  * the revision stamped in Kotlin is the committed MP4's hash, so a re-render
    without a regeneration fails here rather than shipping a stale cache key
  * the loops are the wallpaper engine's shape: 1080p, 30 fps, 20 s, silent,
    faststart
  * the engine composes MOTION_LOOPS + CoreViewsLoop.LOOPS, and the Motion set
    stays first so the shipped default does not move
  * **the catalogue still says `planned`**: a designed wallpaper is not a
    render, and no test here may start calling it one

Run from anywhere:
    python3 tests/test_coreviews_wallpapers.py
"""
from __future__ import annotations

import hashlib
import json
import re
import shutil
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

MAIN = ROOT / "app" / "src" / "main"
KT = MAIN / "java/tv/corebuilds/iconpack"
ASSETS = MAIN / "assets"
LIVE = ROOT / "Motion" / "live"
THUMBS = LIVE / "thumbs"
CATALOG = ROOT / "views" / "catalog.json"

import coreviews_roster as roster  # noqa: E402


def kt(name: str) -> str:
    return (KT / name).read_text(encoding="utf-8")


def body(src: str) -> str:
    m = re.search(r"val LOOPS: List<Loop> = listOf\((.*?)\n    \)", src, re.DOTALL)
    assert m, "CoreViewsLoop.kt has no LOOPS list"
    return m.group(1)


class CatalogueJoin(unittest.TestCase):
    """The catalogue, the roster and the Kotlin list are one list."""

    @classmethod
    def setUpClass(cls):
        cls.src = kt("CoreViewsLoop.kt")
        cls.body = body(cls.src)
        cls.catalog = json.loads(CATALOG.read_text(encoding="utf-8"))

    def test_one_loop_per_catalogue_view_in_catalogue_order(self):
        ids = re.findall(r'id = "([^"]+)"', self.body)
        self.assertEqual(ids, [c.id for c in roster.CLIPS])
        numbers = [int(v["number"]) for v in self.catalog["views"]]
        self.assertEqual([c.number for c in roster.CLIPS], sorted(numbers))

    def test_titles_are_the_catalogues(self):
        titles = re.findall(r'title = "([^"]+)"', self.body)
        self.assertEqual(titles, [v["title"] for v in
                                  sorted(self.catalog["views"], key=lambda v: v["number"])])

    def test_every_loop_has_its_mp4(self):
        for name in re.findall(r'fileName = "([^"]+)"', self.body):
            path = LIVE / name
            self.assertTrue(path.is_file(), f"no MP4 for loop: {path}")
            with path.open("rb") as fh:
                self.assertEqual(fh.read(12)[4:8], b"ftyp", f"{name} is not an MP4")

    def test_revisions_are_the_committed_mp4s(self):
        for clip in roster.CLIPS:
            digest = hashlib.sha256(clip.mp4.read_bytes()).hexdigest()[:12]
            self.assertIn(f'revision = "{digest}"', self.body,
                          f"{clip.stem} was re-rendered without "
                          "tools/build_coreviews_kt.py")

    def test_the_kotlin_list_is_current(self):
        done = subprocess.run(
            [sys.executable, str(ROOT / "tools" / "build_coreviews_kt.py"), "--check"],
            capture_output=True, text=True)
        self.assertEqual(done.returncode, 0,
                         (done.stdout + done.stderr).strip())

    def test_every_loop_bundles_its_poster_frame(self):
        # The grid draws offline and the engine draws this before the MP4
        # lands: a missing frame is a blank tile and a blank home screen.
        thumbs = sorted(p.name for p in ASSETS.joinpath("coreviews_thumbs").glob("*.jpg"))
        self.assertEqual(thumbs, sorted(f"{c.stem}.jpg" for c in roster.CLIPS))
        for clip in roster.CLIPS:
            self.assertTrue(clip.thumb.is_file(), f"no poster in Motion/live/thumbs: {clip.stem}")
            self.assertLess(clip.app_thumb.stat().st_size, 120_000,
                            f"{clip.app_thumb.name} is too heavy for a grid tile")

    def test_urls_are_https_raw_github_under_motion_live(self):
        for url in re.findall(r'url = RAW_BASE \+ "([^"]+)"', self.body):
            self.assertRegex(url, r"^coreviews-live-\d\d-[a-z0-9-]+\.mp4$")
        self.assertIn(
            'private const val RAW_BASE =\n'
            '        "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/live/"',
            self.src)

    def test_ids_are_stable_slugs(self):
        for clip in roster.CLIPS:
            self.assertRegex(clip.id, r"^coreviews-[a-z0-9-]+$")
            self.assertNotIn(clip.id, ("coreviews-",), "an id is never renamed once shipped")


class EngineWiring(unittest.TestCase):
    """One list, and the shipped default stays first."""

    def test_the_engine_plays_both_sets(self):
        src = kt("LiveLoop.kt")
        self.assertIn("val MOTION_LOOPS: List<Loop> = listOf(", src)
        self.assertIn("val LOOPS: List<Loop> = MOTION_LOOPS + CoreViewsLoop.LOOPS", src)

    def test_motion_stays_first_so_the_default_does_not_move(self):
        src = kt("LiveLoop.kt")
        self.assertLess(src.index("val MOTION_LOOPS"),
                        src.index("val LOOPS: List<Loop> = MOTION_LOOPS"))
        # byId falls back to LOOPS[0], which is still the Motion default.
        self.assertIn("fun byId(id: String?): Loop = LOOPS.firstOrNull { it.id == id } ?: LOOPS[0]",
                      src)

    def test_core_views_loops_carry_no_still_series(self):
        # They are not the moving form of a committed still, and saying they
        # were would put a caption in the preview that names nothing.
        src = kt("CoreViewsLoop.kt")
        self.assertEqual(src.count("stillSeries = \"\""),
                         len(roster.CLIPS))

    def test_the_chip_constant_is_not_a_stills_folder(self):
        src = kt("CoreViewsLoop.kt")
        self.assertIn('const val SERIES = "series-core-views"', src)
        manifest = json.loads((ROOT / "Wallpapers" / "manifest.json").read_text(encoding="utf-8"))
        series = {w["series"] for w in manifest["wallpapers"]}
        self.assertNotIn("series-core-views", series,
                         "Core Views loops are not stills and must not claim a stills folder")


class WallpaperFormat(unittest.TestCase):
    """The shape the engine and every other loop already plays."""

    def test_every_loop_is_1080p_30fps_20s(self):
        try:
            import imageio_ffmpeg
            ff = imageio_ffmpeg.get_ffmpeg_exe()
        except Exception:
            ff = shutil.which("ffmpeg")
        if not ff:
            self.skipTest("no ffmpeg available")
        for clip in roster.CLIPS:
            probe = subprocess.run([ff, "-i", str(clip.mp4)],
                                   capture_output=True, text=True).stderr
            self.assertIn("1920x1080", probe, clip.stem)
            self.assertRegex(probe, r"\b30 fps\b", f"{clip.stem} is not 30 fps")
            self.assertIn("Duration: 00:00:20.00", probe, clip.stem)
            self.assertNotIn("Audio:", probe, f"{clip.stem} has an audio track")

    def test_loops_are_faststart(self):
        # A wallpaper is fetched and played as it arrives; an MP4 with its
        # index at the end stalls on the first frame.
        for clip in roster.CLIPS:
            head = clip.mp4.read_bytes()[:8192]
            self.assertNotEqual(head.find(b"moov"), -1, clip.stem)
            mdat = head.find(b"mdat")
            self.assertTrue(mdat == -1 or head.find(b"moov") < mdat, clip.stem)


class HonestyTests(unittest.TestCase):
    """What these wallpapers are, and what they are not."""

    def test_the_catalogue_still_says_planned(self):
        # A designed wallpaper is not a render. The moment a rendition is
        # filled in, this test should be updated - deliberately, by whichever
        # change actually rendered and published the view.
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        for view in catalog["views"]:
            self.assertEqual(view["status"], "planned", view["id"])
            for key, value in view["renditions"].items():
                self.assertIsNone(value, f"{view['id']} {key} claims a rendition")

    def test_the_generator_says_it_is_not_footage(self):
        src = (ROOT / "tools" / "build_coreviews_loops.py").read_text(encoding="utf-8")
        self.assertIn("Not the view", src)
        self.assertIn("never traced from\n    real geography", src)
        kotlin = kt("CoreViewsLoop.kt")
        self.assertIn("drawn contour art", kotlin)

    def test_the_docs_exist_and_say_so(self):
        doc = ROOT / "docs" / "CORE_VIEWS_WALLPAPERS.md"
        self.assertTrue(doc.is_file(), "docs/CORE_VIEWS_WALLPAPERS.md is missing")
        text = doc.read_text(encoding="utf-8").lower()
        self.assertIn("not footage", text)
        self.assertIn("planned", text)


class GateTests(unittest.TestCase):
    """The gate is wired into CI, like every other generator's."""

    def test_the_kt_gate_runs_in_ci(self):
        ci = (ROOT / ".github" / "workflows" / "suite-ci.yml").read_text(encoding="utf-8")
        self.assertIn("python tools/build_coreviews_kt.py --check", ci)
        self.assertIn("python tests/test_coreviews_wallpapers.py", ci)


if __name__ == "__main__":
    unittest.main(verbosity=1)
