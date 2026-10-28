#!/usr/bin/env python3
"""Contracts for Core Views: the catalogue and the feeds it publishes.

The consumer here is Aerial Views, and it parses its feed with
`ignoreUnknownKeys = true`. That means every way this can break is silent:
a misspelled key, a time-of-day value outside the enum, a caption pointing past
the end of the clip, an entry with no licence. None of them raise anywhere in
the app — they just degrade, on a TV, weeks later.

So the assertions are about the *shape* of what we publish, pinned to the same
literals the app uses in `AbstractVideo.kt`, `TimeOfDay.kt` and `SceneType.kt`.

Run:
    python3 tests/test_views_feed.py
"""
from __future__ import annotations

import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import build_views_feed as feeds  # noqa: E402


def minimal_view(**overrides) -> dict:
    """One valid, ready view; overrides are the thing under test."""
    view = {
        "id": "coreviews-99-test-view",
        "number": 99,
        "title": "Test View",
        "place": "Somewhere, New South Wales",
        "region": "New South Wales",
        "country": "AU",
        "coordinates": [-33.0, 151.0],
        "timeOfDay": "day",
        "scene": "sea",
        "lane": "render",
        "durationSeconds": 24,
        "camera": {"move": "orbit", "startHeightM": 100, "endHeightM": 100, "fps": 30},
        "captions": [{"at": 3, "text": "first"}, {"at": 15, "text": "second"}],
        "status": "ready",
        "renditions": {"1080-SDR": "coreviews-99-test-view.mp4"},
        "thumbnail": "coreviews-99-test-view.jpg",
        "credits": [{"source": "Contains modified Copernicus Sentinel data 2026",
                     "license": "Copernicus Sentinel Data Licence", "url": None}],
    }
    view.update(overrides)
    return view


def catalog_with(*views: dict) -> dict:
    return {
        "schema": 1,
        "series": "Core Views",
        "season": 1,
        "baseUrl": "https://example.com/views/",
        "views": list(views),
    }


class AppContract(unittest.TestCase):
    """The literals the Android client actually understands."""

    def test_feed_keys_are_the_keys_the_app_reads(self):
        self.assertEqual(
            set(feeds.RENDITIONS.values()),
            {"url-1080-H264", "url-1080-SDR", "url-1080-HDR", "url-4K-SDR", "url-4K-HDR"},
        )

    def test_time_of_day_matches_the_app_enum(self):
        # TimeOfDay.fromString maps exactly these; everything else is UNKNOWN.
        self.assertEqual(feeds.TIME_OF_DAY, {"day", "night", "sunset", "sunrise"})

    def test_scene_matches_the_app_enum(self):
        self.assertEqual(
            feeds.SCENES,
            {"nature", "beach", "countryside", "waterfall", "city", "sea", "space",
             "patterns", "fire"},
        )

    def test_overflight_keys_are_the_overflight_schema(self):
        self.assertEqual(
            feeds.OVERFLIGHT_KEYS,
            {"1080-SDR": "url_1080p", "4K-SDR": "url_4k",
             "1080-HDR": "url_1080p_hdr", "4K-HDR": "url_4k_hdr"},
        )


class Projection(unittest.TestCase):
    def test_one_ready_view_becomes_one_captioned_entry(self):
        aerial, overflight, _ = feeds.build(catalog_with(minimal_view()))
        assets = json.loads(aerial)["assets"]
        self.assertEqual(len(assets), 1)
        entry = assets[0]
        self.assertEqual(entry["id"], "coreviews-99-test-view")
        self.assertEqual(entry["type"], "aerial")
        self.assertEqual(entry["timeOfDay"], "day")
        self.assertEqual(entry["scene"], "sea")
        self.assertEqual(entry["url-1080-SDR"],
                         "https://example.com/views/coreviews-99-test-view.mp4")
        self.assertEqual(entry["pointsOfInterest"], {"3": "first", "15": "second"})
        self.assertIn("Core Views by Core Builds", entry["accessibilityLabel"])

        rows = json.loads(overflight)
        self.assertEqual(rows[0]["url_1080p"], entry["url-1080-SDR"])
        self.assertEqual(rows[0]["url_img"],
                         "https://example.com/views/coreviews-99-test-view.jpg")
        self.assertEqual(rows[0]["location"], "Core Views · New South Wales")

    def test_absent_renditions_are_omitted_not_emptied(self):
        """A blank URL is worse than a missing one: the app treats it as present."""
        aerial, _, _ = feeds.build(catalog_with(minimal_view()))
        entry = json.loads(aerial)["assets"][0]
        self.assertNotIn("url-4K-SDR", entry)
        self.assertNotIn("url-4K-HDR", entry)
        for value in entry.values():
            self.assertNotEqual(value, "")

    def test_planned_views_never_publish(self):
        planned = minimal_view(id="coreviews-98-planned", number=98,
                               status="planned", renditions={"1080-SDR": None},
                               thumbnail=None)
        aerial, overflight, views = feeds.build(catalog_with(minimal_view(), planned))
        self.assertEqual(len(feeds.ready_views(views)), 1)
        self.assertEqual([a["id"] for a in json.loads(aerial)["assets"]],
                         ["coreviews-99-test-view"])
        self.assertEqual(len(json.loads(overflight)), 1)


class Rejections(unittest.TestCase):
    """Every one of these would otherwise ship as a silent gap."""

    def assert_rejected(self, catalog: dict, needle: str):
        with self.assertRaises(feeds.CatalogError) as caught:
            feeds.validate(catalog)
        self.assertIn(needle, str(caught.exception))

    def test_misspelled_rendition_key_is_rejected(self):
        self.assert_rejected(
            catalog_with(minimal_view(renditions={"1080-SDR": "coreviews-99-test-view.mp4",
                                                  "url-4k-sdr": "x.mp4"})),
            "unknown rendition keys")

    def test_time_of_day_outside_the_enum_is_rejected(self):
        self.assert_rejected(catalog_with(minimal_view(timeOfDay="dusk")), "timeOfDay")

    def test_scene_outside_the_enum_is_rejected(self):
        self.assert_rejected(catalog_with(minimal_view(scene="mountain")), "scene")

    def test_view_without_credits_is_rejected(self):
        self.assert_rejected(catalog_with(minimal_view(credits=[])), "credits are required")

    def test_caption_past_the_end_of_the_clip_is_rejected(self):
        self.assert_rejected(
            catalog_with(minimal_view(captions=[{"at": 30, "text": "too late"}])),
            "inside the clip")

    def test_captions_out_of_order_are_rejected(self):
        self.assert_rejected(
            catalog_with(minimal_view(captions=[{"at": 9, "text": "b"},
                                                {"at": 4, "text": "a"}])),
            "ascend")

    def test_duration_outside_the_loop_range_is_rejected(self):
        self.assert_rejected(catalog_with(minimal_view(durationSeconds=120)), "durationSeconds")

    def test_ready_view_with_no_rendition_is_rejected(self):
        self.assert_rejected(
            catalog_with(minimal_view(renditions={"1080-SDR": None})),
            "status is ready but no rendition")

    def test_non_https_base_url_is_rejected(self):
        catalog = catalog_with(minimal_view())
        catalog["baseUrl"] = "http://example.com/views/"
        self.assert_rejected(catalog, "https://")

    def test_duplicate_id_is_rejected(self):
        self.assert_rejected(
            catalog_with(minimal_view(), minimal_view(number=100)), "duplicate id")

    def test_a_render_view_without_coordinates_is_rejected(self):
        self.assert_rejected(catalog_with(minimal_view(coordinates=None)), "coordinates")


class Season(unittest.TestCase):
    """The committed catalogue, held to the same rules as the fixtures."""

    def setUp(self) -> None:
        self.catalog = feeds.load_catalog()
        self.views = feeds.validate(self.catalog)

    def test_season_one_is_twelve_numbered_views(self):
        self.assertEqual(len(self.views), 12)
        self.assertEqual(sorted(v["number"] for v in self.views), list(range(1, 13)))
        self.assertEqual(len({v["id"] for v in self.views}), 12)

    def test_season_one_needs_no_camera_hardware(self):
        """Season 1 is producible from a laptop: open-data renders plus archive.
        A drone view here would be a plan nobody can shoot."""
        lanes = {v["lane"] for v in self.views}
        self.assertEqual(lanes, {"render", "archive"})

    def test_every_view_carries_a_caption_and_a_place(self):
        for view in self.views:
            self.assertTrue(view["captions"], f"{view['id']} has no captions")
            self.assertIn(view["region"], {"New South Wales", "Tasmania", "Victoria",
                                           "Northern Territory", "Queensland",
                                           "South Australia", "Western Australia",
                                           "Earth"})

    def test_published_feeds_agree_with_the_catalog(self):
        """If a season has been rendered, its feeds must be committed and current."""
        aerial, overflight, views = feeds.build(self.catalog)
        if not feeds.ready_views(views):
            self.assertFalse(feeds.AERIAL_TARGET.exists(),
                             "no view is ready, but views/aerial-entries.json is committed")
            return
        self.assertTrue(feeds.AERIAL_TARGET.exists(), "run tools/build_views_feed.py")
        self.assertEqual(feeds.AERIAL_TARGET.read_text(encoding="utf-8"), aerial)
        self.assertEqual(feeds.OVERFLIGHT_TARGET.read_text(encoding="utf-8"), overflight)


class WorkedExample(unittest.TestCase):
    """Season 1 is all planned, so the file shapes exist only as an example."""

    def setUp(self) -> None:
        self.catalog = feeds.load_catalog()
        self.views = feeds.validate(self.catalog)
        self.doc = json.loads(feeds.example_document(self.catalog, self.views))

    def test_example_shows_a_finished_view_end_to_end(self):
        entry = self.doc["catalogEntry"]
        self.assertEqual(entry["status"], "ready")
        self.assertTrue(entry["renditions"]["1080-SDR"].startswith(entry["id"]))
        aerial = self.doc["aerialViewsEntry"]
        self.assertEqual(aerial["id"], entry["id"])
        self.assertIn("url-1080-SDR", aerial)
        self.assertIn("pointsOfInterest", aerial)
        self.assertIn("url_1080p", self.doc["overflightEntry"])

    def test_example_omits_renditions_it_does_not_have(self):
        aerial = self.doc["aerialViewsEntry"]
        self.assertNotIn("url-4K-HDR", aerial)
        for value in aerial.values():
            self.assertNotEqual(value, "")

    def test_example_is_generated_not_hand_edited(self):
        current = feeds.EXAMPLE_TARGET.read_text(encoding="utf-8")
        self.assertEqual(current, feeds.example_document(self.catalog, self.views),
                         "views/examples/ready-view.json drifted — run "
                         "tools/build_views_feed.py --examples")


class CheckMode(unittest.TestCase):
    def test_check_passes_when_nothing_is_ready_and_nothing_is_committed(self):
        with tempfile.TemporaryDirectory() as tmp:
            aerial = Path(tmp) / "aerial.json"
            overflight = Path(tmp) / "overflight.json"
            original = (feeds.AERIAL_TARGET, feeds.OVERFLIGHT_TARGET)
            feeds.AERIAL_TARGET, feeds.OVERFLIGHT_TARGET = aerial, overflight
            try:
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(feeds.main(["--check"]), 0)
                    # A committed feed with an empty season is a stale feed.
                    aerial.write_text("[]", encoding="utf-8")
                    self.assertEqual(feeds.main(["--check"]), 1)
            finally:
                feeds.AERIAL_TARGET, feeds.OVERFLIGHT_TARGET = original

    def test_plan_mode_writes_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            aerial = Path(tmp) / "aerial.json"
            original = feeds.AERIAL_TARGET
            feeds.AERIAL_TARGET = aerial
            try:
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(feeds.main(["--plan"]), 0)
                self.assertFalse(aerial.exists())
            finally:
                feeds.AERIAL_TARGET = original


if __name__ == "__main__":
    unittest.main(verbosity=2)
