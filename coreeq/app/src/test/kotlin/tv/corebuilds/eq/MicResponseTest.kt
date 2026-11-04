package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.MicResponse
import tv.corebuilds.eq.export.MicrophoneResponsePoint

class MicResponseTest {

    /** A plausible four-point report: 2 dB high at 1 kHz, -2 dB low at 10 kHz. */
    private val report = listOf(
        MicrophoneResponsePoint("remote", 100.0, 1.0),
        MicrophoneResponsePoint("remote", 1_000.0, 2.0),
        MicrophoneResponsePoint("remote", 4_000.0, -1.0),
        MicrophoneResponsePoint("remote", 10_000.0, 0.0)
    )

    private val centres = doubleArrayOf(100.0, 1_000.0, 2_000.0, 4_000.0, 10_000.0, 16_000.0)

    // --- applicability -------------------------------------------------------------

    @Test
    fun fourValidPointsAreApplicable() {
        assertTrue(MicResponse.applicable(report))
    }

    @Test
    fun fewerThanFourPointsAreNotApplicable() {
        assertFalse(MicResponse.applicable(report.take(3)))
        assertFalse(MicResponse.applicable(emptyList()))
    }

    @Test
    fun blendingTwoMicrophoneReportsRefusesTheWholeComposite() {
        val tv = report.map { MicrophoneResponsePoint("tv-array", it.frequencyHz, it.responseDb) }
        val blend = report + tv
        assertTrue(MicResponse.applicable(blend)) // sufficiency alone still passes...
        assertEquals(2, MicResponse.microphoneCount(blend))
        val measured = DoubleArray(centres.size) { 5.0 }
        // ...but flatten refuses: a composite of two unknown capsules matches
        // neither, so nothing is applied rather than something fictional.
        assertEquals(measured.toList(), MicResponse.flatten(centres, measured, blend).toList())
        // The single capsule does apply: the reported +1 dB at 100 Hz comes off.
        assertEquals(4.0, MicResponse.flatten(centres, measured, report)[0], 1e-9)
        assertEquals(1, MicResponse.microphoneCount(report))
        assertEquals(0, MicResponse.microphoneCount(emptyList()))
        // Same-description mics would collide at this layer - the uniqueness is
        // CaptureEngine's contract ("Built-in microphone #1"/" #2"); a genuinely
        // single capsule must never be miscounted:
        assertEquals(1, MicResponse.microphoneCount(report + report[0]))
    }

    @Test
    fun invalidAndOutOfRangePointsDoNotCount() {
        val junk = listOf(
            MicrophoneResponsePoint("remote", Double.NaN, 1.0),
            MicrophoneResponsePoint("remote", 500.0, Double.NaN),
            MicrophoneResponsePoint("remote", 0.5, 1.0),          // below the audible band
            MicrophoneResponsePoint("remote", 40_000.0, 1.0),     // above it
            MicrophoneResponsePoint("remote", 250.0, 1.0)
        )
        // One good point (250 Hz): metadata with a single usable sample is not a calibration.
        assertFalse(MicResponse.applicable(junk))
    }

    @Test
    fun duplicateFrequenciesCountOnce() {
        val duplicates = (1..6).map { MicrophoneResponsePoint("remote", 1_000.0, 2.0) }
        assertFalse(MicResponse.applicable(duplicates))
    }

    // --- interpolation -------------------------------------------------------------

    @Test
    fun exactPointsComeBackVerbatim() {
        assertEquals(2.0, MicResponse.responseDbAt(report, 1_000.0)!!, 1e-9)
        assertEquals(-1.0, MicResponse.responseDbAt(report, 4_000.0)!!, 1e-9)
    }

    @Test
    fun betweenPointsIsLinearInLogHz() {
        // Halfway (in log10) between 1 kHz (+2) and 4 kHz (-1): 0.5.
        val at2k = MicResponse.responseDbAt(report, 2_000.0)!!
        assertEquals(0.5, at2k, 1e-6)
    }

    @Test
    fun outsideTheReportedSpanSaysNothing() {
        assertNull(MicResponse.responseDbAt(report, 50.0))
        assertNull(MicResponse.responseDbAt(report, 16_000.0))
        assertNull(MicResponse.responseDbAt(report, Double.NaN))
        assertNull(MicResponse.responseDbAt(report.take(2), 2_000.0)) // not applicable
    }

    // --- flatten -------------------------------------------------------------------

    @Test
    fun flattenSubtractsTheResponsePerBand() {
        val measured = doubleArrayOf(10.0, 10.0, 10.0, 10.0, 10.0, 10.0)
        val flat = MicResponse.flatten(centres, measured, report)
        assertEquals(10.0 - 1.0, flat[0], 1e-9)   // 100 Hz, exact point
        assertEquals(10.0 - 2.0, flat[1], 1e-9)   // 1 kHz, exact point
        assertEquals(10.0 - 0.5, flat[2], 1e-9)   // 2 kHz, interpolated
        assertEquals(10.0 + 1.0, flat[3], 1e-9)   // 4 kHz, exact point (report is -1)
        assertEquals(10.0, flat[5], 1e-9)         // 16 kHz: outside the span, untouched
    }

    @Test
    fun flattenClampsToTheCap() {
        val wild = listOf(
            MicrophoneResponsePoint("remote", 100.0, 40.0),
            MicrophoneResponsePoint("remote", 1_000.0, 40.0),
            MicrophoneResponsePoint("remote", 4_000.0, 40.0),
            MicrophoneResponsePoint("remote", 10_000.0, 40.0)
        )
        val measured = doubleArrayOf(10.0, 10.0, 10.0, 10.0)
        val flat = MicResponse.flatten(doubleArrayOf(100.0, 1_000.0, 4_000.0, 10_000.0), measured, wild)
        val expected = 10.0 - MicResponse.MAX_APPLIED_DB
        for (band in flat) assertEquals(expected, band, 1e-9)
    }

    @Test
    fun flatMetadataIsAnExactNoOp() {
        val flatReport = listOf(
            MicrophoneResponsePoint("remote", 100.0, 0.0),
            MicrophoneResponsePoint("remote", 1_000.0, 0.0),
            MicrophoneResponsePoint("remote", 4_000.0, 0.0),
            MicrophoneResponsePoint("remote", 10_000.0, 0.0)
        )
        val measured = doubleArrayOf(1.5, -3.0, 0.25, 12.0, -0.5, 4.0)
        val flat = MicResponse.flatten(centres, measured, flatReport)
        for (k in measured.indices) assertEquals(measured[k], flat[k], 0.0)
    }

    @Test
    fun inapplicableMetadataLeavesTheCurveAlone() {
        val measured = doubleArrayOf(1.5, -3.0, 0.25, 12.0, -0.5, 4.0)
        val flat = MicResponse.flatten(centres, measured, report.take(2))
        for (k in measured.indices) assertEquals(measured[k], flat[k], 0.0)
    }

    @Test
    fun nonFiniteBandsPassThroughUntouched() {
        val measured = doubleArrayOf(10.0, Double.NaN, Double.POSITIVE_INFINITY, 10.0, 10.0, Double.NaN)
        val flat = MicResponse.flatten(centres, measured, report)
        assertTrue(flat[1].isNaN())
        assertEquals(Double.POSITIVE_INFINITY, flat[2], 0.0)
        assertTrue(flat[5].isNaN())
        assertEquals(10.0 - 1.0, flat[0], 1e-9)
    }

    @Test
    fun flattenNeverWritesBackIntoItsInput() {
        val measured = doubleArrayOf(10.0, 10.0, 10.0, 10.0, 10.0, 10.0)
        val original = measured.copyOf()
        MicResponse.flatten(centres, measured, report)
        for (k in original.indices) assertEquals(original[k], measured[k], 0.0)
    }

    @Test
    fun spanReportsTheCoveredRange() {
        assertEquals(100.0, MicResponse.spanHz(report)!!.first, 0.0)
        assertEquals(10_000.0, MicResponse.spanHz(report)!!.second, 0.0)
        assertNull(MicResponse.spanHz(report.take(1)))
    }

    @Test
    fun spanTextNeverReadsZeroKilohertz() {
        assertEquals("100 Hz-10.0 kHz", MicResponse.spanText(report))
        val low = listOf(20.0, 25.0, 31.5, 40.0).map { MicrophoneResponsePoint("remote", it, 0.0) }
        assertEquals("20 Hz-40 Hz", MicResponse.spanText(low))
        assertNull(MicResponse.spanText(report.take(1)))
    }

    // --- collecting a capture's reports ---------------------------------------------

    private fun curve(n: Int): List<Pair<Double, Double>> =
        (0 until n).map { 20.0 * Math.pow(1_000.0, it / n.toDouble()) to 1.0 }

    @Test
    fun aLongFirstListCannotCrowdTheNextMicOutOfTheGuard() {
        // 300 points from the first mic used to fill the whole 256-point budget,
        // leave the second mic nothing, and make the capture read as one capsule.
        val points = MicResponse.collect(
            listOf(MicResponse.Report("Front mic", curve(300)), MicResponse.Report("Rear mic", curve(10))),
            fallbackName = "active mic", maxMicrophones = 4, maxPoints = 256
        )
        assertTrue(points.size <= 256)
        assertEquals(2, MicResponse.microphoneCount(points))
        val measured = doubleArrayOf(10.0, 10.0, 10.0, 10.0, 10.0, 10.0)
        val flat = MicResponse.flatten(centres, measured, points)
        for (k in measured.indices) assertEquals("band $k must be left alone", measured[k], flat[k], 0.0)
    }

    @Test
    fun micsSharingADescriptionStayTwoCapsules() {
        val points = MicResponse.collect(
            listOf(MicResponse.Report("Built-in microphone", curve(8)), MicResponse.Report("Built-in microphone", curve(8))),
            fallbackName = "phone mic", maxMicrophones = 4, maxPoints = 256
        )
        assertEquals(2, MicResponse.microphoneCount(points))
        assertEquals(setOf("Built-in microphone #1", "Built-in microphone #2"), points.map { it.microphone }.toSet())
    }

    @Test
    fun aLongSharedDescriptionKeepsItsSuffixInsideTheExportField() {
        val long = "x".repeat(200)
        val labels = MicResponse.collect(
            listOf(MicResponse.Report(long, curve(4)), MicResponse.Report(long, curve(4))),
            fallbackName = "phone mic", maxMicrophones = 4, maxPoints = 256
        ).map { it.microphone }.toSet()
        assertEquals(2, labels.size)
        assertTrue(labels.all { it.length <= 80 })
        assertEquals(2, labels.map { it.take(80) }.toSet().size)
    }

    @Test
    fun oneMicKeepsTheWholeBudgetAndAPlainLabel() {
        val points = MicResponse.collect(
            listOf(MicResponse.Report(null, curve(300))),
            fallbackName = "active mic", maxMicrophones = 4, maxPoints = 256
        )
        assertEquals(256, points.size)
        assertEquals(setOf("active mic 1"), points.map { it.microphone }.toSet())
    }

    @Test
    fun invalidPointsDoNotSpendTheBudget() {
        val noisy = listOf(Double.NaN to 1.0, -5.0 to 1.0, 100.0 to Double.POSITIVE_INFINITY) + curve(6)
        val points = MicResponse.collect(
            listOf(MicResponse.Report("remote", noisy)),
            fallbackName = "active mic", maxMicrophones = 4, maxPoints = 256
        )
        assertEquals(6, points.size)
    }

    @Test
    fun outOfBandPointsCannotSpendASecondMicsShare() {
        // Two mics, so each gets 128 of 256. The second mic's list opens with 200
        // points below 20 Hz, which valid() later drops; before the fix they used
        // its whole share, it kept no usable point, and the capture read as one
        // capsule, so flatten applied the first mic's curve.
        val subsonic = (0 until 200).map { 1.0 + it * 0.05 to 3.0 }
        val points = MicResponse.collect(
            listOf(MicResponse.Report("Front mic", curve(8)), MicResponse.Report("Rear mic", subsonic + curve(8))),
            fallbackName = "active mic", maxMicrophones = 4, maxPoints = 256
        )
        assertEquals(2, MicResponse.microphoneCount(points))
        assertTrue(points.all { it.frequencyHz in 20.0..20_000.0 })
        val measured = doubleArrayOf(10.0, 10.0, 10.0, 10.0, 10.0, 10.0)
        val flat = MicResponse.flatten(centres, measured, points)
        for (k in measured.indices) assertEquals("band $k must be left alone", measured[k], flat[k], 0.0)
    }

    // --- active microphones -------------------------------------------------------------

    @Test
    fun oneReportingMicOfSeveralActiveIsStillABlend() {
        // Two mics recorded and only the first gave Android a curve. The labels
        // show one capsule, so before the active count was read, flatten applied
        // that capsule's curve to a recording two mics made.
        val points = MicResponse.collect(
            listOf(MicResponse.Report("Front mic", curve(8)), MicResponse.Report("Rear mic", emptyList())),
            fallbackName = "active mic", maxMicrophones = 4, maxPoints = 256
        )
        assertEquals(1, MicResponse.microphoneCount(points))
        assertEquals(MicResponse.Verdict.SEVERAL_MICS, MicResponse.verdict(points, activeMicrophones = 2))
        val measured = doubleArrayOf(10.0, 10.0, 10.0, 10.0, 10.0, 10.0)
        val flat = MicResponse.flatten(centres, measured, points, activeMicrophones = 2)
        for (k in measured.indices) assertEquals("band $k must be left alone", measured[k], flat[k], 0.0)
        // The same list from a one-mic capture is applied: 1 dB comes off in span.
        assertEquals(9.0, MicResponse.flatten(centres, measured, points, activeMicrophones = 1)[0], 1e-9)
    }

    @Test
    fun theActiveCountOnlyRefusesAboveOne() {
        val measured = DoubleArray(centres.size) { 5.0 }
        // One active mic, or a count Android did not report: the labels decide.
        for (active in listOf(null, 0, 1)) {
            assertEquals("active=$active", MicResponse.Verdict.APPLIED, MicResponse.verdict(report, active))
            assertEquals("active=$active", 4.0, MicResponse.flatten(centres, measured, report, active)[0], 1e-9)
        }
        // Sufficiency stays the first gate at any count.
        assertEquals(MicResponse.Verdict.TOO_FEW, MicResponse.verdict(report.take(3), activeMicrophones = 3))
        assertEquals(MicResponse.Verdict.NONE, MicResponse.verdict(emptyList(), activeMicrophones = 3))
    }

    // --- what the capture line says ----------------------------------------------------

    @Test
    fun theLineNamesTheMicsThatReportedNothing() {
        val two = tv.corebuilds.eq.export.CaptureInfo(activeMicrophoneCount = 2, microphoneResponse = report)
        assertTrue(two.summary(), two.summary().contains("1 of 2 active mics reported mic response; this version applies none"))
        assertFalse(two.summary(), two.summary().contains("applied over"))
        assertTrue(two.exportNotes().any { it.contains("one of 2 active microphones") && it.contains("1.11.2 and earlier did") })
        val one = tv.corebuilds.eq.export.CaptureInfo(activeMicrophoneCount = 1, microphoneResponse = report)
        assertTrue(one.summary(), one.summary().contains("applied over"))
        assertTrue(one.exportNotes().any { it.contains("from one capsule; applied") })
    }

    @Test
    fun theSummaryNamesTheFirstGateTheDataFellAt() {
        // Two mics, but too few points to apply even from one capsule: both the
        // summary and the export notes say "too few", not "composite".
        val thin = listOf(MicrophoneResponsePoint("a #1", 100.0, 1.0), MicrophoneResponsePoint("b #2", 1_000.0, 1.0))
        val info = tv.corebuilds.eq.export.CaptureInfo(microphoneResponse = thin)
        assertTrue(info.summary(), info.summary().contains("too few to apply"))
        assertFalse(info.summary(), info.summary().contains("composite"))
        assertTrue(info.exportNotes().any { it.contains("too few") })
    }

    @Test
    fun aMultiMicLineDescribesTheRuleNotTheSavedProfilesHistory() {
        // 1.11.2 and earlier subtracted the composite, and a saved profile does not
        // say which version measured it, so the line must not claim "not applied".
        val blend = report + report.map { MicrophoneResponsePoint("tv-array", it.frequencyHz, it.responseDb) }
        val info = tv.corebuilds.eq.export.CaptureInfo(microphoneResponse = blend)
        assertTrue(info.summary(), info.summary().contains("this version applies no composite"))
        assertFalse(info.summary(), info.summary().contains("composite not applied"))
        assertTrue(info.exportNotes().any { it.contains("1.11.2 and earlier did") })
    }
}
