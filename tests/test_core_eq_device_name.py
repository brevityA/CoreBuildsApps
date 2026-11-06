#!/usr/bin/env python3
"""Core EQ device names come from one helper, held statically.

Why this exists
---------------
Core EQ joined `Build.MANUFACTURER` and `Build.MODEL` in seven places. An onn
box reports "onn" and "onn 4K Pro Streaming Device", so the first supporter
test of 1.13.1 showed "Connected to onn onn 4K Pro Streaming Device" on the
phone. `DeviceName.of` drops a maker the model already starts with; its cases
are in `DeviceNameTest`. This file keeps new code from going back to joining
the two by hand.

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
        offenders = [
            f"{path.relative_to(ROOT)}:{number}"
            for path in sorted(SOURCES.rglob("*.kt"))
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1)
            if JOINED.search(line)
        ]
        self.assertEqual(offenders, [], "use DeviceName.current() so a maker is not named twice")

    def test_the_names_people_read_use_the_helper(self) -> None:
        expected = {
            "tv/corebuilds/eq/companion/CompanionServer.kt": '.put("device_name", DeviceName.current())',
            "tv/corebuilds/eq/companion/PhoneCompanionActivity.kt": "val clientName = DeviceName.current()",
            "tv/corebuilds/eq/companion/PhoneCaptureEngine.kt": '.put("capture_device_model", DeviceName.current()',
            "tv/corebuilds/eq/SetupActivity.kt": 'add("TV: ${DeviceName.current()}")',
        }
        for relative, needle in expected.items():
            with self.subTest(relative):
                self.assertIn(needle, (SOURCES / relative).read_text(encoding="utf-8"))

    def test_every_profile_writer_stores_the_helper_name(self) -> None:
        # The TV measurement, Manual EQ and phone-measurement profiles: a writer that built
        # the name some other way would pass the joined-pattern scan and still save "onn onn".
        writers = {
            "tv/corebuilds/eq/MeasureActivity.kt": 1,
            "tv/corebuilds/eq/ManualEqActivity.kt": 1,
            "tv/corebuilds/eq/companion/CompanionServer.kt": 1,
        }
        for relative, count in writers.items():
            with self.subTest(relative):
                source = (SOURCES / relative).read_text(encoding="utf-8")
                self.assertEqual(source.count("deviceName = DeviceName.current(),"), count)
                self.assertNotRegex(source, r"deviceName = (?!DeviceName\.current\(\)|\"REW\")")

    def test_brand_detection_still_reads_the_manufacturer_itself(self) -> None:
        setup = (SOURCES / "tv/corebuilds/eq/SetupActivity.kt").read_text(encoding="utf-8")
        self.assertIn("DeviceIdentity.detect(\n            Build.MANUFACTURER,", setup)

    def test_the_helper_drops_the_maker_only_as_a_whole_word(self) -> None:
        helper = HELPER.read_text(encoding="utf-8")
        self.assertIn("ignoreCase = true", helper)
        self.assertIn("!next.isLetterOrDigit()", helper)
        self.assertIn("Character.NON_SPACING_MARK", helper)


if __name__ == "__main__":
    sys.exit(0 if unittest.main(exit=False, verbosity=1).result.wasSuccessful() else 1)
