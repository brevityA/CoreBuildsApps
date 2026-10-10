#!/usr/bin/env python3
"""Contracts for the morph-icon test set (docs/morph-test/, candidate build).

The morph's whole claim is "frame 1 is the glyph at rest, and the last frame
IS the shipped banner, pixel for pixel". That claim rots silently: a banner
re-render, a catalog rename, or a render() argument the generator forgets
(e.g. a secondary ink) all leave committed files that no longer match the
pack. These tests pin the claim so a drift fails in CI instead of on a TV.

Plain unittest; needs Pillow (tools/requirements.txt), no SVG rendering.
"""
from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

from PIL import Image, ImageSequence  # noqa: E402

import build_morph_icons as morph  # noqa: E402

CATALOG = json.loads((ROOT / "tools" / "catalog.json").read_text(encoding="utf-8"))
ICONS = {i["name"]: i for i in CATALOG["icons"]}
DOCS = ROOT / "docs" / "morph-test"
CAND = ROOT / "app" / "src" / "candidate"
MAIN_RES = ROOT / "app" / "src" / "main" / "res"


def webp_frames(path: Path) -> list[Image.Image]:
    return [f.convert("RGBA") for f in ImageSequence.Iterator(Image.open(path))]


class MorphTestSet(unittest.TestCase):
    def test_test_set_names_exist_in_the_catalog(self):
        missing = [n for n in morph.TEST_SET if n not in ICONS]
        self.assertEqual(
            missing, [],
            f"TEST_SET names not in tools/catalog.json (renamed?): {missing}")

    def test_last_frame_is_the_shipped_banner(self):
        for name in morph.TEST_SET:
            d = ICONS[name]["drawable"]
            with self.subTest(app=name):
                last = webp_frames(DOCS / f"{d}_morph.webp")[-1]
                banner = webp_frames(
                    MAIN_RES / "drawable-nodpi" / f"{d}_banner.webp")[0]
                self.assertEqual(
                    last.tobytes(), banner.tobytes(),
                    f"{d}_morph.webp's last frame drifted from the shipped "
                    "banner — regenerate with tools/build_morph_icons.py")

    def test_play_once_files_play_once(self):
        for name in morph.TEST_SET:
            d = ICONS[name]["drawable"]
            with self.subTest(app=name):
                im = Image.open(DOCS / f"{d}_morph.webp")
                self.assertEqual(im.n_frames, morph.FRAMES)
                self.assertEqual(im.info.get("loop"), 1,
                                 "play-once files must carry loop count 1")

    def test_loop_files_pingpong_and_loop_forever(self):
        for name in morph.TEST_SET:
            d = ICONS[name]["drawable"]
            with self.subTest(app=name):
                once = webp_frames(DOCS / f"{d}_morph.webp")
                loop = webp_frames(DOCS / f"{d}_morph_loop.webp")
                im = Image.open(DOCS / f"{d}_morph_loop.webp")
                self.assertEqual(im.n_frames, 2 * morph.FRAMES - 2)
                self.assertEqual(im.info.get("loop"), 0,
                                 "ping-pong files must loop forever (loop 0)")
                # Same endpoints and turn: glyph first, banner at the turn,
                # and the return trip retraces the forward frames, so the
                # cycle closes seamlessly on the glyph.
                self.assertEqual(loop[0].tobytes(), once[0].tobytes())
                self.assertEqual(loop[morph.FRAMES - 1].tobytes(), once[-1].tobytes())
                self.assertEqual(loop[morph.FRAMES].tobytes(), once[-2].tobytes())
                self.assertEqual(loop[-1].tobytes(), once[1].tobytes())

    def test_every_flavour_and_format_is_committed(self):
        for name in morph.TEST_SET:
            d = ICONS[name]["drawable"]
            with self.subTest(app=name):
                for rel in (f"{d}_morph.webp",
                            f"apng/{d}_morph.png",
                            f"gif/{d}_morph.gif",
                            f"{d}_morph_loop.webp",
                            f"apng/{d}_morph_loop.png",
                            f"gif/{d}_morph_loop.gif"):
                    self.assertTrue((DOCS / rel).exists(),
                                    f"docs/morph-test/{rel} is missing")
                self.assertTrue(
                    (CAND / "res" / "drawable-nodpi" / f"{d}_morph.webp").exists(),
                    f"candidate drawable {d}_morph.webp is missing")

    def test_candidate_appfilter_maps_the_test_apps(self):
        af = (CAND / "res" / "xml" / "appfilter.xml").read_text(encoding="utf-8")
        af_assets = (CAND / "assets" / "appfilter.xml").read_text(encoding="utf-8")
        for name in morph.TEST_SET:
            d = ICONS[name]["drawable"]
            with self.subTest(app=name):
                self.assertIn(f'drawable="{d}_morph"', af)
                self.assertNotIn(f'drawable="{d}_banner"', af,
                                 f"{d}_banner still mapped in the candidate appfilter")
        self.assertEqual(af, af_assets,
                         "the res/xml and assets appfilter copies drifted")

    def test_candidate_drawable_lists_the_morph_section(self):
        dx = (CAND / "res" / "xml" / "drawable.xml").read_text(encoding="utf-8")
        dx_assets = (CAND / "assets" / "drawable.xml").read_text(encoding="utf-8")
        self.assertEqual(dx, dx_assets,
                         "the res/xml and assets drawable.xml copies drifted")
        self.assertIn('<category title="Morph test" />', dx)
        for name in morph.TEST_SET:
            d = ICONS[name]["drawable"]
            self.assertIn(f'<item drawable="{d}_morph" />', dx)

    def test_pingpong_and_durations(self):
        # Pure construction, no rendering: runs in milliseconds.
        frames = [Image.new("RGBA", (2, 2), (i, 0, 0, 255))
                  for i in range(morph.FRAMES)]
        pp = morph.pingpong(frames)
        self.assertEqual(len(pp), 2 * morph.FRAMES - 2)
        self.assertEqual([f.getpixel((0, 0))[0] for f in pp],
                         list(range(morph.FRAMES))
                         + list(range(morph.FRAMES - 2, 0, -1)))
        self.assertEqual(len(morph.once_durations(morph.FRAMES)), morph.FRAMES)
        self.assertEqual(len(morph.loop_durations(morph.FRAMES)), len(pp))
        self.assertEqual(
            sum(morph.loop_durations(morph.FRAMES)),
            120 + (morph.FRAMES - 2) * morph.FRAME_MS + 600
            + (morph.FRAMES - 3) * morph.FRAME_MS + 120)


if __name__ == "__main__":
    unittest.main()
