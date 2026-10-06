package tv.corebuilds.eq.dsp

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the measurement quality scoring so the weights and thresholds
 * cannot drift.
 */
class MeasurementQualityTest {

    @Test
    fun `perfect measurement scores near 100`() {
        val result = makeResult(
            snrDb = 50.0,
            rt60Seconds = 0.4,
            nullCount = 0,
            bandsBelowTransition = 8,
            bandsGated = 0,
            transitionHz = 200.0
        )
        val score = MeasurementQuality.score(result)
        assertTrue("Perfect measurement should score 85+, got $score", score >= 85)
        assertEquals(MeasurementQuality.Tier.GREEN, MeasurementQuality.tier(score))
    }

    @Test
    fun `noisy measurement scores poorly`() {
        val result = makeResult(
            snrDb = 22.0,   // barely above minimum
            rt60Seconds = null,  // too noisy to measure
            nullCount = 6,
            bandsBelowTransition = 8,
            bandsGated = 5,
            transitionHz = 300.0
        )
        val score = MeasurementQuality.score(result)
        assertTrue("Noisy measurement should score below 40, got $score", score < 40)
        assertEquals(MeasurementQuality.Tier.RED, MeasurementQuality.tier(score))
    }

    @Test
    fun `SNR score is 0 at minimum threshold`() {
        assertEquals(0, MeasurementQuality.snrScore(20.0))
        assertEquals(0, MeasurementQuality.snrScore(15.0))
    }

    @Test
    fun `SNR score is max at ideal level`() {
        assertEquals(30, MeasurementQuality.snrScore(45.0))
        assertEquals(30, MeasurementQuality.snrScore(60.0))
    }

    @Test
    fun `SNR score interpolates linearly`() {
        val mid = MeasurementQuality.snrScore(32.5)
        assertTrue("Mid-range SNR should be between 0 and 30", mid in 1..29)
    }

    @Test
    fun `RT60 score is 0 for null`() {
        assertEquals(0, MeasurementQuality.rt60Score(null))
    }

    @Test
    fun `RT60 score is max for typical room`() {
        assertEquals(20, MeasurementQuality.rt60Score(0.4))
        assertEquals(20, MeasurementQuality.rt60Score(0.8))
    }

    @Test
    fun `null score decreases with count`() {
        assertEquals(20, MeasurementQuality.nullScore(0))
        assertEquals(16, MeasurementQuality.nullScore(1))
        assertEquals(0, MeasurementQuality.nullScore(5))
        assertEquals(0, MeasurementQuality.nullScore(10))
    }

    @Test
    fun `min phase score is max when all bands pass`() {
        assertEquals(15, MeasurementQuality.minPhaseScore(8, 0))
    }

    @Test
    fun `min phase score is 0 when all bands fail`() {
        assertEquals(0, MeasurementQuality.minPhaseScore(8, 8))
    }

    @Test
    fun `transition score is max at low frequency`() {
        assertEquals(15, MeasurementQuality.transitionScore(150.0))
        assertEquals(15, MeasurementQuality.transitionScore(100.0))
    }

    @Test
    fun `transition score is min at default 400 Hz`() {
        assertEquals(5, MeasurementQuality.transitionScore(400.0))
    }

    @Test
    fun `labels match tiers`() {
        assertEquals("Excellent", MeasurementQuality.label(90))
        assertEquals("Good", MeasurementQuality.label(75))
        assertEquals("Fair", MeasurementQuality.label(55))
        assertEquals("Poor", MeasurementQuality.label(35))
        assertEquals("Re-measure", MeasurementQuality.label(20))
    }

    /** Helper: build a minimal SweepResult with the given quality factors. */
    private fun makeResult(
        snrDb: Double,
        rt60Seconds: Double?,
        nullCount: Int,
        bandsBelowTransition: Int,
        bandsGated: Int,
        transitionHz: Double
    ): SweepResult {
        val nBands = 30
        val centres = DoubleArray(nBands) { 20.0 * Math.pow(2.0, it / 3.0) }
        val measured = DoubleArray(nBands) { 0.0 }
        val target = DoubleArray(nBands) { 0.0 }
        val correction = DoubleArray(nBands) { 0.0 }
        val nullMask = BooleanArray(nBands) { it < nullCount }
        val minPhaseOk = BooleanArray(nBands) { i ->
            if (centres[i] < transitionHz) i >= bandsGated else true
        }
        return SweepResult(
            centresHz = centres,
            measuredDb = measured,
            targetDb = target,
            correctionDb = correction,
            nullMask = nullMask,
            minPhaseOk = minPhaseOk,
            rt60Seconds = rt60Seconds,
            schroederHz = null,
            transitionHz = transitionHz,
            rolloffHz = 40.0,
            floorHz = 40.0,
            snrDb = snrDb,
            latencyMs = 50.0,
            filters = emptyList(),
            preampDb = 0.0
        )
    }
}
