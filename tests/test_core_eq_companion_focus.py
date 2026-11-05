#!/usr/bin/env python3
"""Phone companion focus, interaction and TV lifecycle wiring, held statically.

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
    start = re.search(rf"^    (?:(?:private|internal|override) )?fun {re.escape(name)}\b", source, re.M)
    if start is None:
        return None
    rest = source[start.start():]
    following = re.search(r"^    (?:(?:private|internal|override) )?fun ", rest[1:], re.M)
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


class PhoneInteractionContract(unittest.TestCase):
    def setUp(self) -> None:
        self.source = PHONE.read_text(encoding="utf-8")

    def body(self, name: str) -> str:
        result = _method_body(self.source, name)
        self.assertIsNotNone(result, name)
        return result or ""

    def test_discovery_is_passive_and_does_not_summon_a_tv_code(self) -> None:
        self.assertNotIn(".wake()", self.source)
        self.assertNotIn("onWake", TV.read_text(encoding="utf-8"))
        server = PHONE.parent / "CompanionServer.kt"
        self.assertNotIn("onWake", server.read_text(encoding="utf-8"))

    def test_phone_has_separate_control_measure_and_tv_pages(self) -> None:
        for page in ("CONTROL", "MEASURE", "TV"):
            self.assertIn(f"PhoneTab.{page}", self.source)
        tabs = self.body("selectTab")
        for page in ("controlPage", "measurePage", "tvPage"):
            self.assertIn(f"{page}.visibility", tabs)
        self.assertIn("KEY_TAB", self.body("onSaveInstanceState"))

    def test_every_mode_button_uses_the_same_busy_and_connection_policy(self) -> None:
        controls = self.body("updateControls")
        self.assertIn("modeButtons.values.forEach { it.isEnabled = state.canControl }", controls)
        self.assertIn("if (!uiState().canControl) return", self.body("runControlNow"))
        self.assertIn("if (!uiState().canControl) return", self.body("startPhoneMeasurement"))

    def test_read_only_refresh_never_toggles_eq_or_rebuilds_controls(self) -> None:
        for name in ("refreshDashboard", "showDashboard", "onResume"):
            body = self.body(name)
            self.assertNotIn("set_enabled", body)
            self.assertNotIn("EqService.enable", body)
            self.assertNotIn("EqService.disable", body)
        render = self.body("showDashboard")
        self.assertNotIn("removeAllViews", render)
        self.assertIn("lastSyncMs", render)
        refresh = self.body("refreshDashboard")
        self.assertIn("if (!quiet) setOperationBusy(true)", refresh)
        self.assertIn("revision != dashboardRevision", refresh)

    def test_polling_stops_when_phone_is_not_visible(self) -> None:
        self.assertIn("screenResumed = false", self.body("onPause"))
        self.assertIn("removeCallbacks(refreshTicker)", self.body("onPause"))
        self.assertIn("stopTvSearch()", self.body("onStop"))
        self.assertIn("removeCallbacksAndMessages(null)", self.body("onDestroy"))

    def test_forgetting_stops_discovery_before_clearing_credentials(self) -> None:
        body = self.body("forgetLocally")
        self.assertLess(body.index("stopTvSearch()"), body.index("credentialStore.clear()"))
        self.assertIn("pairedCredentials = null", body)
        self.assertIn("screenResumed", self.body("rediscoverSavedTv"))
        self.assertIn("samePairing(saved)", self.body("retryWithFoundTv"))
        self.assertIn("generation != searchGeneration", self.body("retryWithFoundTv"))
        self.assertIn("searchGeneration++", self.body("stopTvSearch"))

    def test_choices_and_destructive_actions_use_explicit_dialogs(self) -> None:
        for name in ("chooseTarget", "chooseRoom", "chooseProfile"):
            self.assertIn("setSingleChoiceItems", self.body(name))
        for name in ("confirmUnpair", "confirmForget"):
            body = self.body(name)
            self.assertIn("setNegativeButton", body)
            self.assertIn("setPositiveButton", body)

    def test_capture_keeps_screen_awake_and_is_cleaned_up(self) -> None:
        body = self.body("setMeasurementUi")
        self.assertIn("FLAG_KEEP_SCREEN_ON", body)
        self.assertIn("window.clearFlags", body)
        self.assertIn("SCREEN_ORIENTATION_LOCKED", body)
        self.assertIn("cancelCapture(sendRemote = true)", self.body("onStop"))

    def test_new_phone_resource_references_resolve_and_values_are_valid_xml(self) -> None:
        import xml.etree.ElementTree as ET
        res = ROOT / "coreeq/app/src/main/res"
        definitions: dict[str, set[str]] = {}
        for path in (res / "values").glob("*.xml"):
            for element in ET.parse(path).getroot():
                if "name" not in element.attrib:
                    continue
                kind = element.get("type") if element.tag == "item" else element.tag
                definitions.setdefault(kind or "", set()).add(element.attrib["name"])
        for path in (res / "color").glob("*.xml"):
            definitions.setdefault("color", set()).add(path.stem)
        for kind, name in re.findall(r"(?<!android\.)R\.(id|string|color)\.(phone_\w+)", self.source):
            self.assertIn(name, definitions.get(kind, set()), f"undefined R.{kind}.{name}")


class CorrectionLifecycleContract(unittest.TestCase):
    def setUp(self) -> None:
        self.source = (ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/apply/EqService.kt").read_text(encoding="utf-8")

    def test_disabled_correction_is_rejected_before_the_service_sets_up(self) -> None:
        body = _method_body(self.source, "onCreate") or ""
        self.assertIn("if (!store.correctionEnabled)", body)
        self.assertLess(body.index("if (!store.correctionEnabled)"), body.index("modeStore = ContentModeStore"))
        self.assertIn("stopAfterExplicitOff()", body)
        # enable() uses startForegroundService(); Android crashes an app whose
        # service stops before startForeground(), so go foreground, then stop.
        stop = _method_body(self.source, "stopAfterExplicitOff") or ""
        self.assertLess(stop.index("ServiceCompat.startForeground"), stop.index("stopSelf()"))
        self.assertIn("ServiceCompat.STOP_FOREGROUND_REMOVE", stop)

    def test_queued_stop_cannot_write_over_a_newer_user_choice(self) -> None:
        body = _method_body(self.source, "onStartCommand") or ""
        self.assertIn("CorrectionServicePolicy.decide(store.correctionEnabled", body)
        self.assertIn("Decision.IGNORE_STALE_STOP", body)
        self.assertNotIn("store.correctionEnabled = false", body)
        self.assertIn("stopSelf(startId)", body)

    def test_async_reapply_and_route_callbacks_cannot_revive_a_stopped_service(self) -> None:
        self.assertIn("instanceRunning = false", _method_body(self.source, "onDestroy") or "")
        self.assertIn("CorrectionServicePolicy.canApply(instanceRunning, store.correctionEnabled, suspended)", _method_body(self.source, "applyAll") or "")
        for name in ("onOutputsChanged", "onRouteConfirmed"):
            self.assertIn("if (!instanceRunning || !store.correctionEnabled) return", _method_body(self.source, name) or "")


if __name__ == "__main__":
    unittest.main(verbosity=2)
