#!/usr/bin/env python3
"""The phone companion's D-pad contract, held statically.

Why this exists
---------------
`PhoneCompanionActivity` builds every control in code. A `<Button>` inflated from
XML is focusable by default; a `Button(context)` is not. So when this screen was
written it had no focus targets at all, and nothing requested initial focus —
which is invisible on a phone and fatal on a D-pad remote, where the cursor has
nowhere to land and the first press is swallowed.

It is not a phone-only screen: `MainActivity` sends **any** device without
`FEATURE_LEANBACK` straight here, so a TV box or a projector running a
phone-style Android build lands on it with a remote in hand.

That defect cannot be caught by `tools/check_ui_resources.py` or
`tests/test_navigation_graph.py`, because both read layout XML and this screen
has none. It also cannot be caught by a unit test: the class needs the Android
SDK, which the off-device harness does not have. So the properties that make it
navigable are asserted against the source, the same way this repository already
holds UI wiring it cannot compile.

Run: `python3 tests/test_core_eq_companion_focus.py`
"""
from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PHONE = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/companion/PhoneCompanionActivity.kt"
TV = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/companion/CompanionActivity.kt"
MAIN = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/MainActivity.kt"


def _method_body(source: str, name: str) -> str | None:
    """The body of `private fun <name>(...)` as text, up to the next top-level `private fun`."""
    start = re.search(rf"^    (?:private )?fun {re.escape(name)}\b", source, re.M)
    if start is None:
        return None
    rest = source[start.start():]
    following = re.search(r"^    (?:private )?fun ", rest[1:], re.M)
    return rest if following is None else rest[: following.start() + 1]


class PhoneCompanionDpadContract(unittest.TestCase):
    def setUp(self) -> None:
        self.source = PHONE.read_text(encoding="utf-8")

    def test_code_built_buttons_are_focusable(self) -> None:
        body = _method_body(self.source, "actionButton")
        self.assertIsNotNone(body, "PhoneCompanionActivity has no actionButton()")
        assert body is not None
        self.assertIn(
            "isFocusable = true", body,
            "a Button built in code is not focusable by default: without this the "
            "screen has no D-pad targets at all",
        )
        self.assertIn(
            "isClickable = true", body,
            "a focusable but non-clickable button takes the cursor and ignores the "
            "select key",
        )

    def test_the_screen_puts_the_cursor_somewhere(self) -> None:
        self.assertIn(
            "requestFocus()", self.source,
            "nothing requests focus, so a D-pad starts with no cursor on screen",
        )
        self.assertRegex(
            self.source, r"private fun focusFirstControl\(",
            "the initial-focus policy should be one named function, not scattered calls",
        )
        self.assertIn(
            "focusFirstControl()", self.source,
            "the initial-focus helper exists but is never called",
        )

    def test_focus_is_moved_when_the_remote_panel_appears(self) -> None:
        body = _method_body(self.source, "setRemoteEnabled")
        self.assertIsNotNone(body, "PhoneCompanionActivity has no setRemoteEnabled()")
        assert body is not None
        self.assertIn(
            "focusFirstControl()", body,
            "revealing the remote panel adds new focus stops; leaving the cursor on a "
            "hidden view strands the D-pad",
        )

    def test_the_scroll_container_does_not_swallow_focus(self) -> None:
        self.assertIn(
            "FOCUS_AFTER_DESCENDANTS", self.source,
            "the ScrollView must hand focus to its children rather than keep it",
        )

    def test_every_control_goes_through_the_focusable_builder(self) -> None:
        # A raw Button(this) or CheckBox(this) added to the panel would silently
        # skip the focusability the builder sets.
        raw = re.findall(r"\bButton\(this\)", self.source)
        self.assertLessEqual(
            len(raw), 1,
            f"found {len(raw)} raw Button(this) constructions; all but the builder "
            "must use actionButton() so they stay focusable",
        )

    def test_focus_targets_it_names_are_real_fields(self) -> None:
        body = _method_body(self.source, "focusFirstControl")
        self.assertIsNotNone(body)
        assert body is not None
        for field in re.findall(r"this\.(\w+)", body):
            self.assertIn(
                f"lateinit var {field}", self.source,
                f"focusFirstControl names {field}, which is not a field on the screen",
            )


class TvCompanionDpadContract(unittest.TestCase):
    def test_the_tv_side_pairing_controls_are_focusable(self) -> None:
        source = TV.read_text(encoding="utf-8")
        body = _method_body(source, "actionButton")
        self.assertIsNotNone(body, "CompanionActivity has no actionButton()")
        assert body is not None
        self.assertIn("isFocusable = true", body)


class EntryPointContract(unittest.TestCase):
    def test_a_device_without_leanback_is_routed_to_the_companion(self) -> None:
        # This is *why* the phone screen's focusability matters on a TV remote:
        # the router is the reason a D-pad user can land there at all.
        source = MAIN.read_text(encoding="utf-8")
        self.assertIn("FEATURE_LEANBACK", source)
        self.assertIn("PhoneCompanionActivity::class.java", source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
