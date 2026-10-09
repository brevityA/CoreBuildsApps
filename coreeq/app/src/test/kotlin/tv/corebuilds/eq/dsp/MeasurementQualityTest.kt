package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the recording score (1.3.2) so its weights and thresholds cannot
 * drift, and pins that the room facts carry no points.
 */
class MeasurementQualityTest {

    @Test
    fun `a clean recording scores 100 whatever the room is like`() {
        val plain = makeResult(snrDb = 50.0, rt60Seconds = 0.4, nullCount = 0, bandsGated = 0, transitionHz = 200.0)
        val awkward = makeResult(snrDb = 50.0, rt60Seconds = 0.4, nullCount = 6, bandsGated = 5, transitionHz = 400.0)
        assertEquals(100, MeasurementQuality.score(plain))
        assertEquals(100, MeasurementQuality.score(awkward))
        assertEquals(MeasurementQuality.Tier.GREEN, MeasurementQuality.tier(100))
    }

    @Test
    fun `a noisy recording scores poorly`() {
        val result = makeResult(snrDb = 22.0, rt60Seconds = null, nullCount = 0, bandsGated = 0, transitionHz = 300.0)
        val score = MeasurementQuality.score(result)
        assertTrue("Noisy recording should score below 30, got $score", score < 30)
        assertEquals(MeasurementQuality.Tier.RED, MeasurementQuality.tier(score))
    }

    @Test
    fun `SNR share is 0 at the analysis floor and 60 at 45 dB`() {
        assertEquals(0, MeasurementQuality.snrScore(20.0))
        assertEquals(0, MeasurementQuality.snrScore(15.0))
        assertEquals(60, MeasurementQuality.snrScore(45.0))
        assertEquals(60, MeasurementQuality.snrScore(80.0))
        assertEquals(30, MeasurementQuality.snrScore(32.5))
    }

    @Test
    fun `decay share is 0 when not measurable and 40 for a living room`() {
        assertEquals(0, MeasurementQuality.decayScore(null))
        assertEquals(40, MeasurementQuality.decayScore(0.1))
        assertEquals(40, MeasurementQuality.decayScore(0.5))
        assertEquals(40, MeasurementQuality.decayScore(1.5))
        assertEquals(24, MeasurementQuality.decayScore(1.7))
        assertEquals(0, MeasurementQuality.decayScore(2.5))
    }

    @Test
    fun `the two shares sum to 100`() {
        assertEquals(100, MeasurementQuality.SNR_POINTS + MeasurementQuality.DECAY_POINTS)
        assertEquals(60, MeasurementQuality.recordingScore(45.0, null))
        assertEquals(40, MeasurementQuality.recordingScore(20.0, 0.5))
    }

    @Test
    fun `room facts are reported, not scored`() {
        val result = makeResult(snrDb = 50.0, rt60Seconds = 0.4, nullCount = 3, bandsGated = 2, transitionHz = 300.0)
        val room = MeasurementQuality.room(result)
        assertEquals(3, room.dips)
        assertEquals(2, room.phaseGatedBands)
        assertEquals(300.0, room.transitionHz, 0.0)
        assertEquals(100, MeasurementQuality.score(result))
    }

    @Test
    fun `labels match tiers`() {
        assertEquals(MeasurementQuality.Grade.EXCELLENT, MeasurementQuality.grade(90))
        assertEquals(MeasurementQuality.Grade.GOOD, MeasurementQuality.grade(75))
        assertEquals(MeasurementQuality.Grade.FAIR, MeasurementQuality.grade(55))
        assertEquals(MeasurementQuality.Grade.POOR, MeasurementQuality.grade(35))
        assertEquals(MeasurementQuality.Grade.REMEASURE, MeasurementQuality.grade(20))
    }

    /** A minimal SweepResult: [nullCount] dips, [bandsGated] gated bands below [transitionHz]. */
    private fun makeResult(
        snrDb: Double,
        rt60Seconds: Double?,
        nullCount: Int,
        bandsGated: Int,
        transitionHz: Double
    ): SweepResult {
        val nBands = 30
        val centres = DoubleArray(nBands) { 20.0 * Math.pow(2.0, it / 3.0) }
        val zeros = DoubleArray(nBands)
        return SweepResult(
            centresHz = centres,
            measuredDb = zeros,
            targetDb = zeros,
            correctionDb = zeros,
            nullMask = BooleanArray(nBands) { it < nullCount },
            minPhaseOk = BooleanArray(nBands) { i -> if (centres[i] < transitionHz) i >= bandsGated else true },
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
