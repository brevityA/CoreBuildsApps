package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.PostCorrectionCheck
import tv.corebuilds.eq.dsp.RefinementTrust
import tv.corebuilds.eq.dsp.RefinementTrust.Verdict

class RefinementTrustTest {

    private fun result(
        rmsDb: Double?,
        worstDb: Double? = -2.0,
        minBands: Int = PostCorrectionCheck.MIN_BANDS
    ) = PostCorrectionCheck.Result(
        bands = minBands,
        rmsDb = rmsDb,
        worstHz = 1_000.0,
        worstDb = worstDb
    )

    @Test
    fun clearMarginEarnsTheClearVerdict() {
        // 1.00 → 0.10: a 0.9 dB drop, past the clear threshold.
        assertEquals(Verdict.CLEARLY_IMPROVED, RefinementTrust.verdict(result(1.00), result(0.10)))
    }

    @Test
    fun modestMarginIsStillOffered() {
        // 1.00 → 0.55: 0.45 dB — real, past the meaningful floor, below clear.
        assertEquals(Verdict.IMPROVED, RefinementTrust.verdict(result(1.00), result(0.55)))
    }

    @Test
    fun weatherBelowTheFloorIsWithheld() {
        // 1.00 → 0.85: 0.15 dB, less than two captures of the same room disagree by.
        assertEquals(Verdict.NOT_IMPROVED, RefinementTrust.verdict(result(1.00), result(0.85)))
    }

    @Test
    fun aWorseRmsIsWithheld() {
        assertEquals(Verdict.NOT_IMPROVED, RefinementTrust.verdict(result(1.00), result(1.40)))
    }

    @Test
    fun worstBandRegressionBeatsAnAverageGain() {
        // RMS drops 0.5 dB but the worst band grows from -2 to -4: a trade, not a fix.
        assertEquals(
            Verdict.WORST_REGRESSED,
            RefinementTrust.verdict(result(1.00, worstDb = -2.0), result(0.50, worstDb = -4.0))
        )
    }

    @Test
    fun worstBandMovementInsideTheToleranceStillCounts() {
        // -2.0 → -2.3 is noise; the RMS gain stands.
        assertEquals(
            Verdict.IMPROVED,
            RefinementTrust.verdict(result(1.00, worstDb = -2.0), result(0.55, worstDb = -2.3))
        )
    }

    @Test
    fun insufficientCheckFiguresWithhold() {
        // No bands at all: MIN_BANDS would say so at runtime; either way, nothing to offer.
        assertEquals(Verdict.NOT_IMPROVED, RefinementTrust.verdict(result(1.00), result(0.10, minBands = 0)))
        assertEquals(Verdict.NOT_IMPROVED, RefinementTrust.verdict(result(null), result(0.10)))
        assertEquals(Verdict.NOT_IMPROVED, RefinementTrust.verdict(result(1.00), result(null)))
    }

    @Test
    fun onlyProvenVerdictsAreWorthwhile() {
        assertTrue(Verdict.CLEARLY_IMPROVED.worthwhile)
        assertTrue(Verdict.IMPROVED.worthwhile)
        assertFalse(Verdict.NOT_IMPROVED.worthwhile)
        assertFalse(Verdict.WORST_REGRESSED.worthwhile)
    }

    @Test
    fun thresholdsAreOrdered() {
        assertTrue(RefinementTrust.MEANINGFUL_DB < RefinementTrust.CLEAR_DB)
        assertTrue(RefinementTrust.WORST_TOLERANCE_DB > 0.0)
    }
}
