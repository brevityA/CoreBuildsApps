#!/usr/bin/env python3
"""Core Views geometry and render planning, without a browser or a GPU.

The renderer cannot be integration-tested on a machine with no Chromium — but
everything that decides *what the shot is* can be: the camera path, the tiles
the move touches, the encode command. Those are the parts that fail quietly.
A wrong zoom or a bbox that is too tight does not raise; it produces a clip
with a grey band in it, hours later.

Run:
    python3 tests/test_views_geo.py
"""
from __future__ import annotations

import argparse
import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import views_geo as geo  # noqa: E402
import build_views_feed as feeds  # noqa: E402
import render_views as renderer  # noqa: E402

VIEW = {
    "id": "coreviews-99-test-view",
    "number": 99,
    "title": "Test View",
    "place": "Somewhere, New South Wales",
    "region": "New South Wales",
    "lane": "render",
    "coordinates": [-33.0, 151.0],
    "timeOfDay": "day",
    "scene": "sea",
    "durationSeconds": 20,
    "camera": {"move": "orbit", "startHeightM": 800, "endHeightM": 800, "fps": 30},
    "captions": [{"at": 2, "text": "first"}],
    "status": "planned",
    "renditions": {},
    "thumbnail": None,
    "credits": [{"source": "test", "license": "test"}],
}


def args(**overrides):
    base = dict(fps=None, width=1920, height=1080, crf=18, hdr=False, pitch=None,
                basemap="osm", tile_template=None, from_path=None)
    base.update(overrides)
    return argparse.Namespace(**base)


class Camera(unittest.TestCase):
    def test_ease_is_symmetric_and_bounded(self):
        self.assertAlmostEqual(geo.ease_in_out(0.5), 0.5)
        self.assertEqual(geo.ease_in_out(-1), 0.0)
        self.assertEqual(geo.ease_in_out(2), 1.0)
        self.assertAlmostEqual(geo.ease_in_out(0.25), 0.125)

    def test_path_has_one_frame_per_tick_plus_the_last(self):
        frames = geo.camera_path(VIEW, "orbit", 20, 30, 800, 800, 55)
        self.assertEqual(len(frames), 601)
        self.assertEqual(frames[0]["t"], 0.0)
        self.assertAlmostEqual(frames[-1]["t"], 20.0)

    def test_orbit_returns_to_its_start(self):
        frames = geo.camera_path(VIEW, "orbit", 20, 30, 800, 800, 55)
        self.assertAlmostEqual(frames[0]["bearing"], 0.0)
        self.assertAlmostEqual(frames[-1]["bearing"] % 360, 0.0, places=1)
        self.assertAlmostEqual(frames[300]["bearing"], 180.0, places=1)
        # An orbit must not wander off the place it is orbiting.
        self.assertEqual({tuple(f["center"]) for f in frames},
                         {(151.0, -33.0)})

    def test_reveal_pulls_back_and_flattens(self):
        frames = geo.camera_path(VIEW, "reveal", 20, 30, 320, 900, 70)
        self.assertAlmostEqual(frames[0]["altitudeM"], 320, places=1)
        self.assertAlmostEqual(frames[-1]["altitudeM"], 900, places=1)
        self.assertLess(frames[-1]["zoom"], frames[0]["zoom"])
        self.assertLess(frames[-1]["pitch"], frames[0]["pitch"])

    def test_glide_moves_east_and_stays_grounded(self):
        frames = geo.camera_path(VIEW, "glide", 20, 30, 200, 200, 50)
        self.assertGreater(frames[-1]["center"][0], frames[0]["center"][0])
        self.assertGreater(frames[-1]["center"][1], frames[0]["center"][1])

    def test_every_documented_move_flies(self):
        for move in geo.MOVES:
            frames = geo.camera_path(VIEW, move, 10, 30, 500, 300, 60)
            self.assertEqual(len(frames), 301, move)
            for frame in frames[:3]:
                self.assertGreaterEqual(frame["pitch"], 0, move)
                self.assertLessEqual(frame["pitch"], 85, move)

    def test_unknown_move_is_loud(self):
        with self.assertRaises(ValueError):
            geo.camera_at(VIEW, "barrel-roll", 1, 20, 500, 500, 60)

    def test_higher_altitude_means_a_wider_shot(self):
        low = geo.altitude_to_zoom(200, -33)
        high = geo.altitude_to_zoom(2000, -33)
        self.assertGreater(low, high)


class Tiles(unittest.TestCase):
    def test_known_tile_addresses(self):
        self.assertEqual(geo.lonlat_to_tile(0, 0, 0), (0, 0))
        self.assertEqual(geo.lonlat_to_tile(0, 0, 1), (1, 1))
        self.assertEqual(geo.lonlat_to_tile(-179, 85, 3), (0, 0))

    def test_bbox_contains_its_centre_and_pitch_expands_it(self):
        south, west, north, east = geo.ground_bbox(-33, 151, 500, 0)
        self.assertLess(south, -33)
        self.assertGreater(north, -33)
        self.assertLess(west, 151)
        self.assertGreater(east, 151)
        steep = geo.ground_bbox(-33, 151, 500, 70)
        self.assertGreater(steep[2] - steep[0], north - south)

    def test_tiles_for_bbox_is_non_empty_and_bounded(self):
        tiles = geo.tiles_for_bbox(-33.02, 150.98, -32.98, 151.02, 12)
        self.assertTrue(tiles)
        self.assertTrue(all(0 <= x < 4096 and 0 <= y < 4096 for x, y in tiles))

    def test_higher_up_covers_more_ground_at_the_same_zoom(self):
        """Tile count is not monotonic in altitude — zoom compensates — but the
        ground a frame touches at a fixed zoom is."""
        low = geo.tiles_for_bbox(*geo.ground_bbox(-33, 151, 300, 60), 13)
        high = geo.tiles_for_bbox(*geo.ground_bbox(-33, 151, 3000, 60), 13)
        self.assertGreater(len(high), len(low))

    def test_every_frame_of_a_move_needs_tiles(self):
        frames = geo.camera_path(VIEW, "track", 20, 15, 400, 400, 58)
        census = geo.tile_census(VIEW, frames)
        self.assertTrue(census)
        self.assertTrue(all(census.values()))

    def test_census_respects_the_zoom_cap(self):
        frames = geo.camera_path(VIEW, "orbit", 5, 5, 60, 60, 60)
        census = geo.tile_census(VIEW, frames, max_zoom=15)
        self.assertTrue(census)
        self.assertLessEqual(max(census), 15)

    def test_tile_url_substitution(self):
        self.assertEqual(geo.tile_url("https://x/{z}/{x}/{y}.png", 12, 3, 4),
                         "https://x/12/3/4.png")


class Planning(unittest.TestCase):
    def plan(self, view=VIEW, **overrides):
        return renderer.plan_view(view, args(**overrides), Path("/tmp/out"))

    def test_plan_reports_frames_tiles_and_an_encode_with_a_frame_rate(self):
        plan = self.plan()
        self.assertEqual(plan["frameCount"], 601)
        self.assertGreater(plan["tiles"], 0)
        self.assertGreater(plan["estimatedDownloadMB"], 0)
        self.assertIn("30", plan["encode"])
        self.assertNotIn("None", plan["encode"])
        self.assertIn("-crf", plan["encode"])

    def test_hdr_plan_is_ten_bit_hevc(self):
        plan = self.plan(hdr=True)
        self.assertIn("libx265", plan["encode"])
        self.assertIn("yuv420p10le", plan["encode"])
        self.assertIn("smpte2084", " ".join(plan["encode"]))

    def test_default_pitch_comes_from_the_move(self):
        plan = self.plan()
        self.assertEqual(plan["frames"][0]["pitch"], renderer.DEFAULT_PITCH["orbit"])

    def test_a_view_needs_a_start_height(self):
        view = {**VIEW, "camera": {"move": "orbit"}}
        with self.assertRaises(SystemExit):
            self.plan(view)

    def test_unknown_move_is_refused_before_rendering(self):
        view = {**VIEW, "camera": {"move": "archive-timelapse", "startHeightM": 100}}
        with self.assertRaises(SystemExit):
            self.plan(view)

    def test_harness_has_no_camera_maths_of_its_own(self):
        """The page must only display keyframes it is handed, never derive them."""
        view = dict(VIEW)
        plan = self.plan()
        html = renderer.harness_html(plan, args(), view)
        self.assertIn('window.renderFrame', html)
        self.assertIn('"zoom"', html)
        for leaked in ("altitude_to_zoom", "Math.log2", "ease("):
            self.assertNotIn(leaked, html)

    def test_tile_urls_are_https_and_keyed(self):
        plan = self.plan()
        urls = renderer.tile_urls(plan, args())
        self.assertTrue(urls)
        self.assertTrue(all(u.startswith("https://tile.openstreetmap.org/") for u in urls))
        self.assertTrue(all("{" not in u for u in urls))

    def test_terrain_only_basemap_has_no_raster_urls(self):
        plan = self.plan()
        self.assertEqual(renderer.tile_urls(plan, args(basemap="terrain")), [])


class StudioExport(unittest.TestCase):
    """The studio's export is the only bridge between the two halves."""

    def export(self) -> dict:
        frames = geo.camera_path(VIEW, "orbit", 6, 30, 800, 800, 55)
        return {
            "cameraPath": {
                "view": VIEW["id"], "move": "orbit", "durationSeconds": 6, "fps": 30,
                "terrain": "mapterhorn",
                "keyframes": [{"t": f["t"], "center": f["center"],
                               "altitudeM": f["altitudeM"], "zoom": f["zoom"],
                               "pitch": f["pitch"], "bearing": f["bearing"]}
                              for f in frames[::15]],
            },
            "catalogEntry": {
                "id": VIEW["id"], "title": VIEW["title"], "place": VIEW["place"],
                "camera": VIEW["camera"], "credits": VIEW["credits"],
            },
        }

    def test_export_plans_without_a_browser(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "export.json"
            path.write_text(json.dumps(self.export()), encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(
                    renderer.main(["--from", str(path), "--dry-run",
                                   "--out", str(Path(tmp) / "out")]), 0)

    def test_a_missing_export_fails_loudly(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(renderer.main(["--from", "/nope/none.json"]), 1)


class Season(unittest.TestCase):
    """Season 1 has to be producible without a camera."""

    def setUp(self):
        self.views = feeds.validate(feeds.load_catalog())

    def test_lanes_are_render_and_archive_only(self):
        self.assertEqual({v["lane"] for v in self.views}, {"render", "archive"})

    def test_no_view_needs_a_drone(self):
        self.assertEqual([v["id"] for v in self.views if v["lane"] == "drone"], [])

    def test_every_render_view_flies_a_known_move(self):
        for view in self.views:
            if view["lane"] != "render":
                continue
            self.assertIn(view["camera"]["move"], geo.MOVES, view["id"])
            self.assertGreater(view["camera"]["startHeightM"], 0, view["id"])

    def test_every_render_view_plans(self):
        with tempfile.TemporaryDirectory() as tmp:
            for view in self.views:
                if view["lane"] != "render":
                    continue
                plan = renderer.plan_view(view, args(), Path(tmp))
                self.assertGreater(plan["frameCount"], 0, view["id"])
                self.assertGreater(plan["tiles"], 0, view["id"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
