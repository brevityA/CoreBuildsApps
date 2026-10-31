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
}
