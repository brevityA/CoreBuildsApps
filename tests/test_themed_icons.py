#!/usr/bin/env python3
"""Themed icons: every adaptive launcher icon carries a monochrome layer.

Android 13 and later can theme launcher icons from the wallpaper. A launcher
that does reads only the adaptive icon's `<monochrome>` layer, tinting its
alpha; an app without one keeps its full-colour icon in a row of themed ones,
or on some launchers is drawn as a generic shape. Until 2.5.0 none of the
three apps here had the layer: the icon pack, its glyphs-only flavor and Core
Builds Glyphs.

What is held here:

1. every adaptive xml the three apps ship names a monochrome layer;
2. that layer exists at every density its foreground does, in the same
   resource set, so a flavor never themes with the pack's mark;
3. it is the foreground's silhouette exactly (same alpha) in plain white ink,
   because theming keeps only alpha and any colour in it is wasted.

Run: python3 tests/test_themed_icons.py
"""
from __future__ import annotations

import unittest
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ADAPTIVE = [
    ROOT / "app/src/main/res/mipmap-anydpi-v26",
    ROOT / "glyphs/src/main/res/mipmap-anydpi-v26",
]
# Resource sets with their own launcher art. The glyphs flavor of :app reuses
# the pack's adaptive xml, so its layers must override every one the xml names.
LAYER_SETS = [
    ROOT / "app/src/main/res",
    ROOT / "app/src/glyphs/res",
    ROOT / "glyphs/src/main/res",
]
DENSITIES = ("mipmap-xhdpi", "mipmap-xxhdpi")


class ThemedIconTests(unittest.TestCase):
    def test_every_adaptive_icon_names_a_monochrome_layer(self):
        for folder in ADAPTIVE:
            for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
                xml = (folder / name).read_text(encoding="utf-8")
                self.assertIn('<monochrome android:drawable="@mipmap/ic_launcher_monochrome" />',
                              xml, f"{folder.relative_to(ROOT)}/{name}")

    def test_each_resource_set_draws_its_own_monochrome_layer(self):
        for res in LAYER_SETS:
            for density in DENSITIES:
                fg = res / density / "ic_launcher_foreground.png"
                mono = res / density / "ic_launcher_monochrome.png"
                self.assertTrue(fg.exists(), fg.relative_to(ROOT))
                self.assertTrue(mono.exists(), f"{mono.relative_to(ROOT)} missing beside "
                                               "its foreground")

    def test_the_layer_is_the_foreground_silhouette_in_white(self):
        for res in LAYER_SETS:
            for density in DENSITIES:
                with Image.open(res / density / "ic_launcher_foreground.png") as f:
                    fg = f.convert("RGBA")
                with Image.open(res / density / "ic_launcher_monochrome.png") as m:
                    mono = m.convert("RGBA")
                where = f"{res.relative_to(ROOT)}/{density}"
                self.assertEqual(fg.size, mono.size, where)
                self.assertEqual(fg.getchannel("A").tobytes(), mono.getchannel("A").tobytes(),
                                 f"{where}: the monochrome mark is not the foreground's")
                inked = [px[:3] for px in mono.getdata() if px[3] > 128]
                self.assertTrue(inked, f"{where}: the monochrome layer is empty")
                self.assertTrue(all(rgb == (255, 255, 255) for rgb in inked),
                                f"{where}: the monochrome layer carries colour")

    def test_the_generators_write_the_layer(self):
        # The layers are generated, not hand-placed: a regenerated launcher
        # must not silently drop them.
        for tool in ("build_branding.py", "build_glyphs_branding.py"):
            source = (ROOT / "tools" / tool).read_text(encoding="utf-8")
            self.assertIn('"ic_launcher_monochrome.png"', source, tool)
            self.assertIn("MONO_INK", source, tool)


if __name__ == "__main__":
    unittest.main(verbosity=2)
