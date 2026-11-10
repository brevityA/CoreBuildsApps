"""Static lifecycle gates for the Core EQ measurement proposal.

These pin Activity source wiring that the proposed JVM fakes do not exercise end
to end: generation guards around analysis, repeat-average path gating, clearing
the accepted average when its result is replaced, and the display/seat/save paths.
They are static gates, not a substitute for the Kotlin behavioural tests.
"""
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MEASURE = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/MeasureActivity.kt"


def source() -> str:
    return MEASURE.read_text(encoding="utf-8")


def between(text: str, start: str, end: str) -> str:
    i = text.index(start)
    j = text.index(end, i)
    return text[i:j]


class CoreEqMeasurementLifecycle(unittest.TestCase):
    def test_analysis_worker_checks_generation_before_dsp_and_refits(self) -> None:
        body = between(source(), "private fun analyse(", "private fun installPrimaryCapture")
        guard = "if (measurementGeneration != generation) return@thread"
        first = body.index(guard)
        self.assertLess(first, body.index("SweepAnalysis.levelsDbfs"))
        analyzed = body.index("val analyzed = outcome.getOrNull()")
        # The post-analysis guard is immediately before this read, not after it.
        second = body.rindex(guard, first, analyzed)
        for token in ("SeatAverage.average", "PostCorrectionCheck.compare", "CorrectionRefinement.refine"):
            self.assertLess(second, body.index(token))
        repeat_guard = body.index(guard, body.index("CorrectionRefinement.refine"))
        self.assertLess(repeat_guard, body.index("computeRepeatAnalysis"))

    def test_capture_status_and_player_error_are_generation_guarded(self) -> None:
        text = source()
        captured = between(text, "override fun onCaptured", "override fun onError")
        self.assertIn("if (measuring && measurementGeneration == generation)", captured)
        player = between(text, "stimulusPlayer.playSweep(", "override fun onLevel")
        self.assertIn("if (measuring && measurementGeneration == generation) fail(msg, kind)", player)

    def test_ceiling_change_clears_repeat_before_reanalysis(self) -> None:
        body = between(source(), "btnCeiling.setOnClickListener", "btnStart.setOnClickListener")
        self.assertLess(body.index("clearRepeatComparisonForSettingsChange()"), body.index("refreshChoices()"))

    def test_repeat_average_is_path_gated_on_the_worker(self) -> None:
        text = source()
        worker = between(text, "private fun analyse(", "private fun installPrimaryCapture")
        self.assertIn("computeRepeatAnalysis(baseline, repeatCapture, plan)", worker)
        body = between(text, "private fun computeRepeatAnalysis", "private fun completeRepeat")
        self.assertLess(body.index("RepeatAveragePath.refusal"), body.index("AdaptiveRepeatAverage.average"))
        self.assertIn("repeatAveragePathCapture(baseline)", body)
        self.assertIn("ProfileOutputIdentity.specificName", source())
        self.assertIn("val averageRefusal = pathMismatch ?: identityRefusal", body)
        self.assertIn("val averaged = if (averageRefusal == null)", body)
        ui = between(text, "private fun completeRepeat", "private fun repeatPathMismatch")
        self.assertNotIn("AdaptiveRepeatAverage.average", ui)
        self.assertNotIn("RepeatabilityAnalysis.compare", ui)

    def test_repeat_average_is_cleared_when_result_provenance_changes(self) -> None:
        text = source()
        for marker, end in (
            ("private fun installPrimaryCapture", "private fun maybeAutoApply"),
            ("private fun completeSeat", "private fun completeCheck"),
            ("private fun showImportResult", "private fun fail"),
            ("private fun fail", "private fun finishMeasuring"),
            ("private fun cancelMeasurement", "private fun saveAndFinish"),
            ("private fun clearRepeatComparisonForSettingsChange", "private fun showTargetOnly"),
        ):
            with self.subTest(marker=marker):
                self.assertIn("repeatAverage = null", between(text, marker, end))

    def test_saved_notes_do_not_claim_repeats_are_never_averaged(self) -> None:
        text = source()
        self.assertNotIn("captures are never averaged", text)
        self.assertIn("Same-position repeat average", text)
        profile = between(text, "private fun buildSweepProfile", "private fun saveImportProfile")
        self.assertIn("val repeatAvg = repeatAverage", profile)
        self.assertIn("repeatAvg.usableBands", profile)
        self.assertIn("captures were not averaged", profile)

    def test_average_display_and_additional_seat_use_the_first_capture(self) -> None:
        text = source()
        buttons = between(text, "private fun updateRefineButtons", "private fun evidenceLines")
        self.assertIn("repeatAverage != null", buttons)
        evidence = between(text, "private fun evidenceLines", "private fun renderQuality")
        self.assertIn("measure_repeat_average_summary", evidence)
        seat = between(text, "private fun startMeasurement", "private fun openCheckCorrection")
        self.assertIn("if (repeatAverage != null) repeatBaseline ?: currentCapture() else currentCapture()", seat)
        title = between(text, "private fun showResult", "private fun activeTargetKey")
        self.assertIn("SAME-POSITION REPEAT AVERAGE", title)

    def test_gate_is_wired_into_suite_ci(self) -> None:
        workflow = (ROOT / ".github/workflows/suite-ci.yml").read_text(encoding="utf-8")
        self.assertIn("python tests/test_core_eq_measurement_lifecycle.py", workflow)


if __name__ == "__main__":
    unittest.main()