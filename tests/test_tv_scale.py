#!/usr/bin/env python3
"""Pin the dp-box normaliser that makes one design canvas fit every TV panel.

Why this exists
---------------
Android TV panels disagree about the dp box they report: 1080p sets say
960x540dp, 4K sets say 1280x720dp or 1920x1080dp depending on the density
bucket their OEM chose. A dp-written layout then renders at a different
*physical* size on each panel - on the doubled box every dp is half the
millimetres, so a 16sp label is an 8sp label at the same three metres.

The app's answer is not resource qualifiers (steps, wrong between steps: a
1280x720dp panel matches sw720dp and would wear the 1920dp panel's metrics,
1.5x too big) but tv.corebuilds.iconpack.TvActivity, which overrides
configuration.densityDpi in attachBaseContext to the density that makes the
panel's pixel width come out as exactly DESIGN_WIDTH_DP dp. Every screen
inherits it.

What is pinned here
-------------------
* the arithmetic: densityDpi = widthPixels * 160 / 960, clamped, and the box it
  produces is the design box for every panel report this suite knows about;
* the near-match guard, so a reference set is never overridden;
* inheritance: every Activity in app extends TvActivity, and
  TvActivity overrides attachBaseContext - a screen that forgot the base class
  would silently render unnormalised on 4K;
* the retired mechanism stays retired: no values-sw720dp directory and no
  generator for one, because two scaling mechanisms drifting apart is worse
  than either alone.
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_KT = ROOT / "app/src/main/java/tv/corebuilds/iconpack"
DESIGN_WIDTH_DP = 960


def target_density_dpi(width_pixels: int) -> int:
    """The Python twin of TvActivity.targetDensityDpi."""
    return max(160, min(640, width_pixels * 160 // DESIGN_WIDTH_DP))


def dp_box(width_pixels: int, density_dpi: int) -> float:
    return width_pixels / (density_dpi / 160.0)


# (pixel width, densityDpi the panel reports) for the reports this suite knows:
# 1080p at tvdpi and xhdpi, 720p, and 4K at the two buckets OEMs actually ship.
PANELS = [
    ("1080p @ 160dpi", 1920, 160),
    ("1080p @ 240dpi", 1920, 240),
    ("1080p @ 320dpi (reference)", 1920, 320),
    ("720p @ 213dpi", 1280, 213),
    ("4K @ 320dpi", 3840, 320),
    ("4K @ 480dpi", 3840, 480),
    ("4K @ 640dpi", 3840, 640),
]


class NormaliserMath(unittest.TestCase):
    def test_every_known_panel_report_normalises_to_the_design_box(self):
        for name, pixels, reported in PANELS:
            target = target_density_dpi(pixels)
            box = dp_box(pixels, target)
            # densityDpi is an integer, so the box lands within a dp or two of
            # the canvas - 0.2%, invisible at 3m and far inside any bucket's
            # step error.
            self.assertAlmostEqual(box, DESIGN_WIDTH_DP, delta=2.0,
                                   msg=f"{name}: normalised box is {box:.0f}dp")

    def test_the_reference_panel_is_left_alone(self):
        """Within 5% the guard returns the context untouched; the reference
        report is exactly on target, so it must be inside the guard."""
        for name, pixels, reported in PANELS:
            target = target_density_dpi(pixels)
            if name.endswith("(reference)"):
                self.assertEqual(target, reported)
            near = abs(target - reported) * 20 <= reported
            if name.endswith("(reference)"):
                self.assertTrue(near, f"{name}: reference must trip the guard")

    def test_the_kotlin_implements_the_same_arithmetic(self):
        for kotlin in (APP_KT / "TvActivity.kt",):
            text = kotlin.read_text(encoding="utf-8")
            self.assertIn("widthPixels * 160 / DESIGN_WIDTH_DP", text,
                          f"{kotlin.name}: the density formula drifted")
            self.assertIn("coerceIn(160, 640)", text,
                          f"{kotlin.name}: the clamp drifted")
            self.assertIn("override fun attachBaseContext", text,
                          f"{kotlin.name}: the override is the whole mechanism")
            self.assertIn("createConfigurationContext", text,
                          f"{kotlin.name}: the override must be per-context, "
                          f"not a global metrics mutation")

    def test_buckets_are_not_come_back(self):
        """Two scaling mechanisms drifting apart is worse than either alone."""
        for res in ("app/src/main/res",):
            self.assertFalse((ROOT / res / "values-sw720dp").exists(),
                             f"{res}: values-sw720dp is retired; TvActivity "
                             f"normalises continuously instead")
        self.assertFalse((ROOT / "tools/build_scale_variants.py").exists())


class EveryScreenInherits(unittest.TestCase):
    def activity_files(self, directory: Path):
        return sorted(p for p in directory.glob("*Activity.kt")
                      if p.name != "TvActivity.kt")

    def test_every_activity_extends_tvactivity(self):
        checked = 0
        for directory in (APP_KT,):
            for path in self.activity_files(directory):
                text = path.read_text(encoding="utf-8")
                self.assertRegex(
                    text, r"class \w+Activity\s*:\s*TvActivity\(\)",
                    f"{path.name}: a screen that forgets TvActivity renders "
                    f"unnormalised on a 4K panel - half-size type at 3m")
                self.assertNotIn("AppCompatActivity()", text,
                                 f"{path.name}: still extends AppCompatActivity")
                checked += 1
        self.assertGreaterEqual(checked, 7,
                                f"only {checked} activities checked; the scan "
                                f"must cover every screen in app")


if __name__ == "__main__":
    unittest.main()
