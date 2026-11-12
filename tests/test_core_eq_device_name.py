#!/usr/bin/env python3
"""Core EQ device names come from one helper, held statically.

Why this exists
---------------
Core EQ joined `Build.MANUFACTURER` and `Build.MODEL` in seven places. An onn
box reports "onn" and "onn 4K Pro Streaming Device", so the first supporter
test of 1.13.1 showed "Connected to onn onn 4K Pro Streaming Device" on the
phone. `DeviceName.of` drops a maker the model already starts with; its cases
are in `DeviceNameTest`. This file keeps new code from going back to joining
the two by hand. Formatting and profile round trips are exercised by Kotlin
tests; these source checks hold the production call sites to that helper.

Run: `python3 tests/test_core_eq_device_name.py`
"""
from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "coreeq/app/src/main/kotlin"
HELPER = SOURCES / "tv/corebuilds/eq/DeviceName.kt"
JOINED = re.compile(r"\$\{?Build\.MANUFACTURER\}?\s*\$\{?Build\.MODEL\b")


class DeviceNames(unittest.TestCase):
    def test_no_source_joins_maker_and_model_by_hand(self) -> None:
        """Reject direct interpolation, including an expression wrapped over lines."""
        offenders = []
        for path in sorted(SOURCES.rglob("*.kt")):
            source = path.read_text(encoding="utf-8")
            for match in JOINED.finditer(source):
                number = source.count("\n", 0, match.start()) + 1
                offenders.append(f"{path.relative_to(ROOT)}:{number}")
        self.assertEqual(offenders, [], "use DeviceName.current() so a maker is not named twice")

    def test_the_names_people_read_use_the_helper(self) -> None:
        """Hold pairing, status, capture and setup labels to the shared formatter."""
        expected = {
            "tv/corebuilds/eq/companion/CompanionServer.kt": '.put("device_name", DeviceName.current())',
            # Format first, then apply the wire/metadata limit. Truncating the model
            # before joining can hide its maker prefix and put the duplicate back.
            "tv/corebuilds/eq/companion/PhoneCompanionActivity.kt":
                'val clientName = DeviceName.current().take(60).ifBlank { "Android phone" }',
            "tv/corebuilds/eq/companion/PhoneCaptureEngine.kt":
                '.put("capture_device_model", DeviceName.current().take(80))',
            "tv/corebuilds/eq/SetupActivity.kt": 'add("TV: ${DeviceName.current()}")',
        }
        for relative, needle in expected.items():
            with self.subTest(relative=relative):
                self.assertIn(needle, (SOURCES / relative).read_text(encoding="utf-8"))

    def test_every_profile_writer_stores_the_helper_name(self) -> None:
        """Cover all three writers; a different join would evade the interpolation scan."""
        # The TV measurement, Manual EQ and phone-measurement profiles. REW imports
        # deliberately say "REW": an imported file did not measure this TV.
        writers = {
            "tv/corebuilds/eq/MeasureActivity.kt": 1,
            "tv/corebuilds/eq/ManualEqActivity.kt": 1,
            "tv/corebuilds/eq/companion/CompanionServer.kt": 1,
        }
        for relative, count in writers.items():
            with self.subTest(relative=relative):
                source = (SOURCES / relative).read_text(encoding="utf-8")
                self.assertEqual(source.count("deviceName = DeviceName.current(),"), count)
                self.assertNotRegex(source, r"deviceName = (?!DeviceName\.current\(\)|\"REW\")")

    def test_brand_detection_still_reads_the_manufacturer_itself(self) -> None:
        """Keep acoustic hardware detection separate from the human-readable label."""
        setup = (SOURCES / "tv/corebuilds/eq/SetupActivity.kt").read_text(encoding="utf-8")
        self.assertIn("DeviceIdentity.detect(\n            Build.MANUFACTURER,", setup)

    def test_the_helper_drops_the_maker_only_as_a_whole_word(self) -> None:
        """Keep the case, word-boundary and combining-mark guards present."""
        helper = HELPER.read_text(encoding="utf-8")
        self.assertIn("ignoreCase = true", helper)
        self.assertIn("!next.isLetterOrDigit()", helper)
        for category in ("NON_SPACING_MARK", "COMBINING_SPACING_MARK", "ENCLOSING_MARK"):
            with self.subTest(category=category):
                self.assertIn(f"Character.{category}", helper)


if __name__ == "__main__":
    sys.exit(0 if unittest.main(exit=False, verbosity=1).result.wasSuccessful() else 1)
