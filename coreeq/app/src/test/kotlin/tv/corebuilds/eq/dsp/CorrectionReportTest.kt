package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The human-readable half of a measurement: why a band was left alone, and
 * what the room itself is doing that EQ cannot fix.
 *
 * The figures come from [SweepResult] only, so the list cannot disagree with
 * the correction that was applied.
 */
class CorrectionReportTest {

    private fun result(
        hz: DoubleArray = doubleArrayOf(50.0, 63.0, 80.0, 100.0, 315.0, 1000.0, 4000.0, 8000.0),
        correction: DoubleArray = doubleArrayOf(0.0, 0.0, 1.2, -4.2, -0.1, 0.2, 0.0, 0.0),
        nulls: BooleanArray = BooleanArray(8),
        phaseOk: BooleanArray = BooleanArray(8) { true },
        bandSnr: DoubleArray = DoubleArray(8) { 30.0 },
        floorHz: Double = 63.0,
        fMaxHz: Double = 4000.0,
        rt60: Double? = 0.4,
        rt60ByOctave: Map<Double, Double?> = mapOf(
            125.0 to 0.5, 250.0 to 0.5, 500.0 to 0.4, 1000.0 to 0.4, 2000.0 to 0.35
        ),
        snrDb: Double = 45.0
    ) = SweepResult(
        centresHz = hz,
        measuredDb = DoubleArray(hz.size),
        targetDb = DoubleArray(hz.size),
        correctionDb = correction,
        nullMask = nulls,
        minPhaseOk = phaseOk,
        rt60Seconds = rt60,
        schroederHz = 180.0,
        transitionHz = 360.0,
        rolloffHz = floorHz,
        floorHz = floorHz,
        snrDb = snrDb,
        latencyMs = 120.0,
        filters = emptyList(),
        preampDb = 0.0,
        bandSnrDb = bandSnr,
        rt60ByOctave = rt60ByOctave,
        fMaxHz = fMaxHz
    )

    @Test
    fun aBandBelowTheSpeakerLimitIsNotBlamedOnTheRoom() {
        val kinds = CorrectionReport.classify(result()).associate { it.hz to it.kind }
        assertEquals(CorrectionReport.Kind.BELOW_FLOOR, kinds[50.0])
        assertEquals(CorrectionReport.Kind.CLOSE, kinds[63.0])
    }

    @Test
    fun aBandAboveTheCeilingSaysSo() {
        val kinds = CorrectionReport.classify(result()).associate { it.hz to it.kind }
        assertEquals(CorrectionReport.Kind.ABOVE_CEILING, kinds[8000.0])
    }

    @Test
    fun nullsPhaseAndNoiseEachGetTheirOwnReason() {
        val nulls = BooleanArray(8).also { it[1] = true }
        val phase = BooleanArray(8) { true }.also { it[2] = false }
        val snr = DoubleArray(8) { 30.0 }.also { it[3] = 3.0 }
        val kinds = CorrectionReport.classify(result(nulls = nulls, phaseOk = phase, bandSnr = snr))
            .associate { it.hz to it.kind }
        assertEquals(CorrectionReport.Kind.NULL, kinds[63.0])
        assertEquals(CorrectionReport.Kind.PHASE, kinds[80.0])
        assertEquals(CorrectionReport.Kind.NOISY, kinds[100.0])
    }

    @Test
    fun correctionsAreListedBiggestFirst() {
        val corrected = CorrectionReport.corrected(result())
        assertEquals("the deepest correction in the fixture is at 100 Hz", 100.0, corrected.first().hz, 0.0)
        assertTrue(corrected.first().text().contains("cut"))
        assertTrue("a boost must sort below a deeper cut", corrected.last().correctionDb > 0.0)
    }

    @Test
    fun theSummaryIsShortAndMentionsTheCeilingOnce() {
        val lines = CorrectionReport.summaryLines(result(), limit = 3)
        assertTrue("summary was $lines", lines.size <= 5)
        assertEquals("the ceiling must be mentioned once", 1, lines.count { it.contains("not corrected") })
    }

    @Test
    fun anEmptyResultHasNoLinesRatherThanBlankOnes() {
        val empty = result(hz = DoubleArray(0), correction = DoubleArray(0), nulls = BooleanArray(0),
            phaseOk = BooleanArray(0), bandSnr = DoubleArray(0))
        assertEquals(emptyList<String>(), CorrectionReport.summaryLines(empty))
        assertEquals(emptyList<String>(), CorrectionReport.classify(empty))
    }

    @Test
    fun shortArraysFromAnOlderProfileCannotCrashTheList() {
        // bandSnrDb is empty on results rebuilt from stored profiles; the
        // classifier must not read past the end of it.
        val sparse = result(bandSnr = DoubleArray(0), nulls = BooleanArray(0), phaseOk = BooleanArray(0))
        assertEquals(8, CorrectionReport.classify(sparse).size)
    }

    @Test
    fun ringingBassIsCalledOutWithItsRatio() {
        val advice = CorrectionReport.roomAdvice(
            result(rt60ByOctave = mapOf(125.0 to 0.8, 250.0 to 0.8, 500.0 to 0.4, 1000.0 to 0.4))
        )
        assertTrue("advice was $advice", advice.any { it.contains("longer than the midrange") })
    }

    @Test
    fun balancedDecayProducesNoDrama() {
        val advice = CorrectionReport.roomAdvice(
            result(rt60ByOctave = mapOf(125.0 to 0.42, 250.0 to 0.41, 500.0 to 0.4, 1000.0 to 0.4))
        )
        assertTrue("advice was $advice", advice.none { it.contains("longer than the midrange") })
    }

    @Test
    fun aLivelyRoomIsMentionedButNotAQuietOne() {
        assertTrue(CorrectionReport.roomAdvice(result(rt60 = 0.9)).any { it.contains("lively") })
        assertTrue(CorrectionReport.roomAdvice(result(rt60 = 0.3)).none { it.contains("lively") })
    }

    @Test
    fun aNoisyMeasurementIsReportedWithItsFigure() {
        val advice = CorrectionReport.roomAdvice(result(snrDb = 24.0))
        assertTrue("advice was $advice", advice.any { it.contains("24 dB above the noise floor") })
        assertTrue(CorrectionReport.roomAdvice(result(snrDb = 50.0)).none { it.contains("noise floor") })
    }

    @Test
    fun missingDecayDataIsSimplySilent() {
        val advice = CorrectionReport.roomAdvice(result(rt60 = null, rt60ByOctave = emptyMap()))
        assertEquals(emptyList<String>(), advice)
    }
}
