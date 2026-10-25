package tv.corebuilds.eq

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.CorrectionReport
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.ImportResult
import tv.corebuilds.eq.dsp.RewImport
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.ln
import kotlin.math.pow

/**
 * The correction ceiling, end to end: the choice the user makes on the measure
 * screen must survive the import path, the saved profile and the exported file,
 * and must never be silently widened by any of them.
 *
 * The ceiling exists because a remote microphone stops hearing before the room
 * does; correcting past that point is guesswork dressed as a measurement.
 */
class CorrectionCeilingTest {

    /** A room with two real modes, sampled the way a text export would be. */
    private fun room(): Pair<DoubleArray, DoubleArray> {
        val freqs = mutableListOf<Double>()
        val steps = (24 * log2(20000.0 / 20.0)).toInt()
        for (i in 0..steps) freqs.add(20.0 * 2.0.pow(i.toDouble() / 24.0))
        val mags = freqs.map { f ->
            val mode = 6.0 * exp(-(ln(f / 70.0) / 0.12).let { it * it }) -
                5.0 * exp(-(ln(f / 220.0) / 0.10).let { it * it })
            mode - 2.0 * ln(f / 1000.0) / ln(2.0) * 0.05
        }
        return freqs.toDoubleArray() to mags.toDoubleArray()
    }

    private fun analyze(ceilingHz: Double?): ImportResult {
        val (f, m) = room()
        return RewImport.analyze(f, m, "flat", 55.0, ceilingHz = ceilingHz)
    }

    @Test
    fun anImportStopsWhereTheUserStoppedIt() {
        val full = analyze(null)
        val capped = analyze(1000.0)
        assertEquals(DspConstants.F_MAX, full.fMaxHz, 1e-9)
        assertEquals(1000.0, capped.fMaxHz, 1e-9)
        assertEquals("the ceiling must not change the analysed bands", full.centresHz.size, capped.centresHz.size)
    }

    @Test
    fun nothingAboveTheCeilingIsCorrected() {
        val capped = analyze(500.0)
        for (i in capped.centresHz.indices) {
            if (capped.centresHz[i] > 500.0) {
                assertEquals("${capped.centresHz[i]} Hz was corrected", 0.0, capped.correctionDb[i], 1e-12)
            }
        }
    }

    @Test
    fun theCeilingKeepsTheBassShapeAndOnlyShiftsItsLevel() {
        val full = analyze(null)
        val capped = analyze(2000.0)
        // The curve is recentred to zero mean over the corrected band, so a
        // narrower band means a different mean and therefore a constant shift.
        // The shape below the ceiling — what the correction does to the room —
        // must survive that shift exactly.
        // Outside both edge tapers: the taper multiplies, so a constant offset
        // is not preserved through it.
        val low = capped.centresHz.indices.filter {
            capped.centresHz[it] >= capped.floorHz * 2.1 && capped.centresHz[it] <= 1000.0
        }
        assertTrue("no bands to compare", low.size >= 3)
        val offset = capped.correctionDb[low.first()] - full.correctionDb[low.first()]
        assertTrue("recentring moved the band by $offset dB", abs(offset) < 1.0)
        for (i in low) {
            assertEquals(
                "shape changed at ${capped.centresHz[i]} Hz",
                full.correctionDb[i] + offset, capped.correctionDb[i], 1e-9
            )
        }
    }

    @Test
    fun aNonsenseCeilingFallsBackToTheFullBand() {
        // Unusable numbers mean "no ceiling", not "ceiling of zero".
        for (requested in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e9)) {
            assertEquals("ceiling $requested", 8000.0, analyze(requested).fMaxHz, 1e-9)
        }
        // A negative number is clamped to the bottom of the band, and the
        // bottom of the band refuses (see the empty-band test above) rather
        // than being quietly read as "full band".
        assertTrue(runCatching { analyze(-5.0) }.isFailure)
    }

    @Test
    fun everyFilterTheFitReturnsIsInsideTheBandsItFitted() {
        val capped = analyze(1000.0)
        val top = capped.centresHz.last { it <= 1000.0 }
        assertTrue("no filters were fitted at all", capped.filters.isNotEmpty())
        for (filter in capped.filters) {
            assertTrue("filter at ${filter.fc} Hz", filter.fc <= top * 1.5)
        }
    }

    @Test
    fun theReportSaysWhyTheTrebleWasLeftAlone() {
        val lines = CorrectionReport.summaryLines(analyze(500.0))
        assertTrue("report was $lines", lines.any { it.contains("above 500 Hz") })
    }

    @Test
    fun anImportNeverClaimsAPhaseOrNoiseFailureItCannotMeasure() {
        // A magnitude-only file has no group delay and no per-band SNR. Labelling
        // a band "room timing" here would invent a measurement.
        val kinds = CorrectionReport.classify(analyze(2000.0)).map { it.kind }.toSet()
        assertFalse("phase was claimed from a magnitude file", CorrectionReport.Kind.PHASE in kinds)
        assertFalse("noise was claimed from a magnitude file", CorrectionReport.Kind.NOISY in kinds)
        assertTrue("there must still be bands to report", kinds.isNotEmpty())
    }

    @Test
    fun anImportReportClassifiesTheSameWayASweepWould() {
        val capped = analyze(1000.0)
        val reasons = CorrectionReport.classify(capped)
        assertEquals(capped.centresHz.size, reasons.size)
        assertTrue("nothing was corrected", CorrectionReport.corrected(capped).isNotEmpty())
        assertTrue(
            "nothing was skipped",
            CorrectionReport.skipped(capped).any { it.kind == CorrectionReport.Kind.ABOVE_CEILING }
        )
    }

    // ---- the saved profile and the exported file ----

    private fun profile(ceiling: Double?) = Profile(
        id = "p", name = "Living room", timestampMs = 1_700_000_000_000L,
        target = "flat", micType = "remote microphone", rolloffHz = 45.0,
        correctionCeilingHz = ceiling,
        curve = listOf(CurvePoint(20.0, -3.0, 2.0), CurvePoint(20_000.0, 1.0, -1.0))
    )

    private fun roundTrip(p: Profile): Profile =
        ProfileStore.parseProfile(JSONObject(Formats.exportProfileJson(p)))

    @Test
    fun theCeilingSurvivesSaveAndReload() {
        assertEquals(1000.0, roundTrip(profile(1000.0)).correctionCeilingHz!!, 1e-9)
        assertEquals(500.0, roundTrip(profile(500.0)).correctionCeilingHz!!, 1e-9)
    }

    @Test
    fun aProfileWithNoRecordedCeilingReloadsAsTheFullBandItWasExportedWith() {
        // The exporter resolves null to 8 kHz rather than omitting the range,
        // so the reloaded value is 8 kHz: true for every profile written
        // before a ceiling existed, since those did correct the full band.
        assertEquals(DspConstants.F_MAX, roundTrip(profile(null)).correctionCeilingHz!!, 1e-9)
    }

    @Test
    fun aStoredProfileWithoutARangeStillReadsAsUnknown() {
        // A file from before correction_range_hz existed carries no range at
        // all: unknown, not a claim of 8 kHz.
        val json = JSONObject(Formats.exportProfileJson(profile(1000.0)))
        json.remove("correction_range_hz")
        assertNull(ProfileStore.parseProfile(json).correctionCeilingHz)
    }

    @Test
    fun theExportedRangeIsTheBandThatWasActuallyCorrected() {
        val json = Formats.exportProfileJson(profile(1000.0))
        val range = JSONObject(json).getJSONArray("correction_range_hz")
        assertEquals("the floor is the measured roll-off", 45.0, range.getDouble(0), 1e-9)
        assertEquals("the top is the ceiling, not 8 kHz", 1000.0, range.getDouble(1), 1e-9)
    }

    @Test
    fun aLegacyProfileWithNoCeilingStillExportsTheFullBand() {
        val range = JSONObject(Formats.exportProfileJson(profile(null))).getJSONArray("correction_range_hz")
        assertEquals(DspConstants.F_MAX, range.getDouble(1), 1e-9)
    }

    @Test
    fun theParametricExportNamesTheNarrowedBand() {
        val text = Formats.exportParametricTxt(profile(1000.0))
        assertTrue("header was:\n${text.lines().take(6).joinToString("\n")}",
            text.contains("correction band 45-1000 Hz"))
    }

    @Test
    fun aFullBandProfileStillExportsAFullBandHeader() {
        val text = Formats.exportParametricTxt(profile(null))
        assertTrue(text.contains("correction band 45-8000 Hz"))
    }

    @Test
    fun aCorruptStoredCeilingIsIgnoredRatherThanApplied() {
        val json = JSONObject(Formats.exportProfileJson(profile(1000.0)))
        json.getJSONArray("correction_range_hz").put(1, -12.0)
        assertNull(ProfileStore.parseProfile(json).correctionCeilingHz)
    }

    @Test
    fun theCeilingChoiceListIsTheOneTheAnalysisClamps() {
        // The measure screen offers exactly these; every one of them must be a
        // value the analysis accepts unchanged, or the label would lie.
        for (choice in CorrectionLimits.CEILING_CHOICES_HZ) {
            assertEquals(choice, CorrectionLimits.ceilingHz(choice), 0.0)
        }
        assertTrue("the widest choice leaves the full band correctable", CorrectionLimits.isFullBand(DspConstants.F_MAX))
        for (choice in CorrectionLimits.CEILING_CHOICES_HZ.dropLast(1)) {
            assertFalse("$choice Hz was labelled full band", CorrectionLimits.isFullBand(choice))
        }
    }

    @Test
    fun aCeilingThatLeavesNoBandIsRefusedWithAReason() {
        // 40 Hz cannot be reached from the measure screen (its narrowest choice
        // is 500 Hz), but the API takes any number. An empty band used to
        // surface as "IllegalArgumentException: The corrected band must not be
        // empty"; it has to read as a measurement the user can act on.
        val failure = runCatching { analyze(40.0) }.exceptionOrNull()
        assertTrue("expected a MeasurementException, got $failure",
            failure is tv.corebuilds.eq.dsp.MeasurementException)
        assertTrue("message was ${failure?.message}",
            failure!!.message!!.contains("nothing to correct"))
    }

    @Test
    fun theCurveObjectAgreesWithTheAnalysisThatWillRun() {
        val capped = analyze(2000.0)
        assertEquals(CorrectionLimits.effectiveCeilingHz(capped.centresHz.last(), 2000.0), capped.fMaxHz, 1e-9)
    }

    @Test
    fun theLimitsStillDescribeTheDspBand() {
        // If these ever diverge, every ceiling in this file silently means
        // something other than what the analysis does.
        assertEquals(DspConstants.F_MIN, CorrectionLimits.MIN_HZ, 0.0)
        assertEquals(DspConstants.F_MAX, CorrectionLimits.MAX_HZ, 0.0)
    }
}
