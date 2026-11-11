#!/usr/bin/env python3
"""SDK-free adapter contracts for applied/no-profile overlay regressions.

These inspect the Android wiring, not its behavior on hardware. Executable JVM
regressions live in AppliedNoticeTest, ProbeRouteObservationTest and
RouteProbeStateTest; real AudioTrack/routing/overlay checks still need a TV.
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APPLY = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/apply"


def method(source: str, name: str) -> str:
    start = re.search(rf"^    (?:(?:private|internal|override) )?fun {name}\b", source, re.M)
    assert start, f"method missing: {name}"
    rest = source[start.start():]
    end = re.search(r"^    (?:(?:private|internal|override) )?fun ", rest[1:], re.M)
    return rest if end is None else rest[:end.start() + 1]


class RouteStabilityContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.route = (APPLY / "OutputRoute.kt").read_text()
        cls.service = (APPLY / "EqService.kt").read_text()
        cls.state = (APPLY / "RouteProbeState.kt").read_text()
        cls.notice = (APPLY / "AppliedNotice.kt").read_text()

    def test_finite_silent_pcm_is_queued_before_playback_with_media_attributes(self) -> None:
        body = method(self.route, "probeOnce")
        self.assertIn(".setAudioAttributes(mediaAttributes())", body)
        self.assertIn("AudioTrack.MODE_STATIC", body)
        self.assertIn("ByteArray(PROBE_BUFFER_BYTES)", body)
        self.assertLess(body.index("track.write("), body.index("track.play()"))
        self.assertNotIn("requestAudioFocus", body)
        self.assertNotIn("setStreamVolume", body)
        duration = int(re.search(r"PROBE_TRACK_MS = (\d+)", self.route)[1])
        settle = int(re.search(r"PROBE_SETTLE_MS = (\d+)L", self.route)[1])
        self.assertGreater(duration, settle)
        self.assertGreater(settle, 0)

    def test_initial_route_callback_is_not_unconditionally_classified_as_a_change(self) -> None:
        body = method(self.route, "probeOnce")
        self.assertIn("ProbeRouteObservation<Int>()", body)
        self.assertIn("observed.observe(devices.map { it.id })", body)
        self.assertIn("observed.changed()", body)
        self.assertNotIn("routeChanged = true", body)
        self.assertIn("devices.isEmpty()", body)
        self.assertIn("unconfirmedOutput()", body)

    def test_a_post_playback_error_cannot_decay_to_an_estimate(self) -> None:
        body = method(self.route, "probeOnce")
        self.assertIn("playbackStarted = true", body)
        self.assertEqual(2, body.count("if (playbackStarted) ProbeResult(unconfirmedOutput(), null) else null"))
        self.assertIn("track.release()", body)

    def test_playback_callback_only_schedules_a_coalesced_check(self) -> None:
        body = self.service.split("private val playbackCallback =", 1)[1].split("private val sessionReceiver", 1)[0]
        self.assertIn("playbackCheckQueued", body)
        self.assertIn("postDelayed(playbackCheck, PLAYBACK_ROUTE_CHECK_MS)", body)
        for side_effect in ("onOutputsChanged()", "invalidateProbeCache", "refreshProbeAsync"):
            self.assertNotIn(side_effect, body)
        check = self.service.split("private val playbackCheck =", 1)[1].split("private val playbackCallback", 1)[0]
        # 1.13.2: only a changed hint is a route change. A media start on a
        # coarse hint gets a quiet verification; on a precise one, nothing.
        self.assertIn("PlaybackRouteHint.Verdict.ROUTE_CHANGED -> onOutputsChanged()", check)
        self.assertIn("PlaybackRouteHint.Verdict.VERIFY_START -> verifyRouteQuietly()", check)
        self.assertIn("preciseHint = indication?.evidence == OutputRoute.Evidence.ANTICIPATED", check)
        self.assertEqual(1, check.count("onOutputsChanged()"))
        quiet = method(self.service, "verifyRouteQuietly")
        self.assertIn("OutputRoute.refreshProbeAsync(this) { onRouteConfirmed() }", quiet)
        for side_effect in ("setAllEnabled(false)", "outputPaused = true", "invalidateProbeCache", "applyAll()"):
            self.assertNotIn(side_effect, quiet)
        wait = int(re.search(r"PLAYBACK_ROUTE_CHECK_MS = (\d+)L", self.service)[1])
        duration = int(re.search(r"PROBE_TRACK_MS = (\d+)", self.route)[1])
        self.assertGreater(wait, duration)

    def test_the_probes_own_playback_reports_never_schedule_discovery(self) -> None:
        # The probe's AudioTrack is reported to Core EQ's own playback callback.
        # Read as playback, it scheduled discovery, whose apply found the probe
        # stale and ran the next one: a probe and a reapply every few seconds.
        body = method(self.route, "probeOnce")
        self.assertLess(body.index("ownProbe.started()"), body.index("track.play()"))
        cleanup = body.split("} finally {", 1)[1]
        self.assertLess(cleanup.index("track.release()"), cleanup.index("ownProbe.ended("))
        callback = self.service.split("private val playbackCallback =", 1)[1].split("private var ownProbeRecheckQueued", 1)[0]
        self.assertLess(callback.index("OutputRoute.isOwnProbeEcho()"), callback.index("mainHandler.post"))
        self.assertIn("if (ownProbeEcho) queueOwnProbeRecheck() else queuePlaybackCheck()", callback)
        recheck = self.service.split("private val ownProbeRecheck =", 1)[1].split("private fun queuePlaybackCheck", 1)[0]
        self.assertIn("audioIsPlaying(configs) != playbackActiveHint", recheck)
        self.assertNotIn("scheduleDiscovery", recheck)
        self.assertGreater(int(re.search(r"OWN_PROBE_ECHO_MS = (\d+)L", self.route)[1]), 0)

    def test_the_timed_entry_points_delegate_to_the_guarded_bodies(self) -> None:
        # Milestone C wraps the probe and the apply in timings; the guarded logic the
        # tests above read moved into probeOnce/applyAllOnce, and every caller still
        # goes through the public entry points, which must reach it unchanged.
        probe = method(self.route, "probeWithSilentTrack")
        self.assertIn("probeOnce(context)", probe)
        self.assertIn("RuntimeTimings.Op.ROUTE_PROBE", probe)
        self.assertNotIn("AudioTrack.Builder", probe)
        self.assertEqual(1, len(re.findall(r"\bprobeOnce\(", self.route.replace("private fun probeOnce(", ""))))
        apply = method(self.service, "applyAll")
        self.assertLess(
            apply.index("CorrectionServicePolicy.canApply(instanceRunning, store.correctionEnabled, suspended)"),
            apply.index("RuntimeTimings.start()"),
        )
        self.assertIn("outcome = applyAllOnce()", apply)
        self.assertIn("RuntimeTimings.finish(RuntimeTimings.Op.APPLY, started, outcome)", apply.split("} finally {", 1)[1])
        self.assertEqual(1, len(re.findall(r"\bapplyAllOnce\(", self.service.replace("private fun applyAllOnce(", ""))))

    def test_route_indications_and_identity_do_not_open_playback_tracks(self) -> None:
        body = method(self.route, "indication")
        self.assertIn("estimateOutput(context)", body)
        for side_effect in ("probeWithSilentTrack", "refreshProbeAsync", "AudioTrack.Builder", "current(context)"):
            self.assertNotIn(side_effect, body)
        identity = method(self.route, "identity")
        self.assertIn("it.kind", identity)
        self.assertIn("it.name", identity)
        self.assertNotIn("it.evidence", identity)

    def test_only_sink_device_events_trigger_output_changes(self) -> None:
        body = self.service.split("private val deviceCallback =", 1)[1].split("private var lastOutput", 1)[0]
        self.assertIn("addedDevices.any { it.isSink }", body)
        self.assertIn("removedDevices.any { it.isSink }", body)

    def test_effect_configuration_waits_for_a_fresh_probe_cache(self) -> None:
        body = method(self.service, "applyAllOnce")
        self.assertIn("!OutputRoute.hasFreshProbe(this) && !OutputRoute.isHeldUnconfirmed()", body)
        self.assertLess(body.index("reapplyWithConfirmedRoute()"), body.index("resolve()"))
        self.assertIn("CorrectionServicePolicy.canApply(instanceRunning, store.correctionEnabled, suspended)", method(self.service, "applyAll"))
        self.assertIn("reapplyWithConfirmedRoute()", method(self.service, "onStartCommand"))

    def test_already_enabled_correction_recovers_on_its_own_measured_output(self) -> None:
        body = method(self.service, "onRouteConfirmed")
        self.assertIn("if (!instanceRunning || !store.correctionEnabled) return", body)
        self.assertIn("if (suspended) return", body)
        self.assertNotIn("AutoApplyPolicy", body)
        self.assertNotIn("SUGGEST_ONLY", body)
        self.assertIn("&& !outputPaused", body)
        self.assertIn("applyAll()", body)
        self.assertIn("OutputRoute.addRouteListener(routeProbeListener)", method(self.service, "onCreate"))
        self.assertIn("OutputRoute.removeRouteListener(routeProbeListener)", method(self.service, "onDestroy"))

    def test_learned_preferences_and_discovery_cannot_bypass_route_safety(self) -> None:
        resolve = method(self.service, "resolve")
        self.assertIn("OutputRoute.pick(profiles, preferred.id, output?.kind, output?.name)", resolve)
        self.assertNotIn("OutputRoute.Pick(preferred,", resolve)
        discovery = method(self.service, "applyDiscovered")
        self.assertIn("outputPaused || !OutputRoute.hasFreshProbe(this)", discovery)
        self.assertIn("!store.correctionEnabled", discovery)
        self.assertLess(discovery.index("outputPaused || !OutputRoute.hasFreshProbe(this)"), discovery.index("createBestEffect("))

    def test_probe_publication_is_atomic_with_invalidation(self) -> None:
        self.assertIn("private val probeState = RouteProbeState<ProbeResult>()", self.route)
        self.assertNotIn("probeGeneration", self.route)
        self.assertIn("fun invalidate() = synchronized(lock)", self.state)
        self.assertIn("synchronized(lock) { publishLocked(", self.state)
        self.assertLess(self.state.index("if (generation != expectedGeneration)"), self.state.index("cached = Cached(nowMs, result)", self.state.index("private fun publishLocked")))
        self.assertIn("if (!publication.accepted)", method(self.route, "current"))

    def test_completion_callbacks_are_detached_before_the_worker_is_released(self) -> None:
        self.assertIn("val completed = callbacks.toList()", self.state)
        self.assertLess(self.state.index("callbacks.clear()"), self.state.index("inFlight = false", self.state.index("fun complete(")))
        body = method(self.route, "refreshProbeAsync")
        self.assertIn("completion.callbacks.forEach", body)
        self.assertIn("completion.publication.changed", body)
        self.assertNotIn("while (true)", body)

    def test_status_and_effects_pause_immediately_but_warning_cards_settle(self) -> None:
        body = method(self.service, "onOutputsChanged")
        self.assertLess(body.index("outputPaused = true"), body.index("refreshProbeAsync"))
        self.assertLess(body.index("setAllEnabled(false)"), body.index("refreshProbeAsync"))
        pause = method(self.service, "announcePaused")
        self.assertIn("appliedNotice.deferPause(key)", pause)
        self.assertIn("appliedNotice.commitPause(key)", pause)
        self.assertIn("postDelayed(notice, PAUSED_NOTICE_SETTLE_MS)", pause)
        self.assertIn("appliedCard.dismiss()", pause)
        self.assertIn("pendingPausedCard?.let(mainHandler::removeCallbacks)", method(self.service, "announceApplied"))
        self.assertIn("pendingPause = null", method(self.notice, "onApplied"))

    def test_missing_profile_and_route_uncertainty_have_distinct_strings(self) -> None:
        body = method(self.service, "announcePaused")
        self.assertIn("OutputRoute.UNCONFIRMED ->", body)
        self.assertIn("OutputRoute.AMBIGUOUS ->", body)
        self.assertIn("R.string.card_paused_title", body)
        values = ET.parse(ROOT / "coreeq/app/src/main/res/values/strings_route_status.xml").getroot()
        strings = {e.attrib["name"]: e.text or "" for e in values if e.tag == "string"}
        for name in ("card_route_unconfirmed_title", "card_route_ambiguous_title", "notice_route_unconfirmed", "notice_route_ambiguous"):
            self.assertIn(f"R.string.{name}", body)
            self.assertNotIn("no profile applied", strings[name].lower())
        self.assertIn("still saved", strings["card_route_unconfirmed_detail"])

    def test_unnamed_bluetooth_device_is_not_reported_as_a_deleted_profile(self) -> None:
        resolver = method(self.service, "resolve")
        self.assertIn("pick.profile == null && OutputRoute.needsDeviceName(profiles, output)", resolver)
        apply = method(self.service, "applyAllOnce")
        self.assertIn("announcePaused(output, resolved.needsDeviceName)", apply)
        self.assertIn("R.string.status_device_name_unconfirmed", apply)
        pause = method(self.service, "announcePaused")
        self.assertIn("if (needsDeviceName)", pause)
        self.assertIn("R.string.card_device_name_unconfirmed_title", pause)
        self.assertIn("needsDeviceName=$needsDeviceName", pause)
        values = ET.parse(ROOT / "coreeq/app/src/main/res/values/strings_route_status.xml").getroot()
        strings = {e.attrib["name"]: e.text or "" for e in values if e.tag == "string"}
        self.assertIn("still saved", strings["card_device_name_unconfirmed_detail"])
        self.assertNotIn("no profile", strings["card_device_name_unconfirmed_title"].lower())

    def test_device_name_explanation_uses_the_same_name_rules_as_matching(self) -> None:
        self.assertIn("ProfileOutputIdentity.specificName(label(kind), name)", method(self.route, "specificName"))
        body = method(self.route, "needsDeviceName")
        self.assertIn("UNKNOWN, AMBIGUOUS, UNCONFIRMED", body)
        self.assertIn("it.outputKind == output.kind", body)
        self.assertIn("ProfileOutputIdentity.needsDeviceName", body)
        policy = (APPLY / "ProfileOutputIdentity.kt").read_text()
        self.assertIn("profileNames.isNotEmpty() && profileNames.all", policy)
        self.assertNotIn("import android.", policy)

    def test_uncertain_outputs_still_block_even_legacy_profiles(self) -> None:
        body = method(self.route, "pick")
        guard = "if (kind == AMBIGUOUS || kind == UNCONFIRMED) return Pick(null, switched = chosen != null)"
        self.assertIn(guard, body)
        self.assertLess(body.index(guard), body.index("if (kind == null)"))


if __name__ == "__main__":
    unittest.main()
