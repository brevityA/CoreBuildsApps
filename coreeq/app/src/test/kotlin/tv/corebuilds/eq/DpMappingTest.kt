package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.LimiterSettings
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile

/**
 * The band maths every engine shares (`BandMapping`) and the DynamicsProcessing
 * limiter's protection invariants (`LimiterSettings`) — plan M6's pure half.
 * The platform constructors wait for the M6a hardware spike; the maths does not.
 */
class DpMappingTest {

    private fun profile(vararg points: Pair<Double, Double>) = Profile(
        id = "t", name = "t", timestampMs = 0L, target = "flat", micType = "test",
        curve = points.map { CurvePoint(it.first, 0.0, it.second) }
    )

    @Test
    fun gainsAreHeadroomShiftedSoNothingBoosts() {
        val p = profile(100.0 to 3.0, 400.0 to -2.0, 1000.0 to 0.0)
        val gains = BandMapping.gainsDb(p, listOf(100.0, 400.0, 1000.0))
        // The largest boost lands on 0: an engine with no preamp cannot clip.
        assertEquals(0.0, gains[0], 1e-9)
        assertEquals(-5.0, gains[1], 1e-9)
        assertEquals(-3.0, gains[2], 1e-9)
        assertTrue("nothing may boost: ${gains.toList()}", gains.all { it <= 0.0 })
    }

    @Test
    fun aCutOnlyProfileIsLeftExactlyAsMeasured() {
        val p = profile(100.0 to -6.0, 1000.0 to -1.0)
        val gains = BandMapping.gainsDb(p, listOf(100.0, 1000.0))
        assertEquals(-6.0, gains[0], 1e-9)
        assertEquals(-1.0, gains[1], 1e-9)
    }

    @Test
    fun bandsOutsideTheCurveGetNothing() {
        val p = profile(100.0 to 4.0)
        val gains = BandMapping.gainsDb(p, listOf(50.0, 100.0, 14000.0))
        assertEquals(-4.0, gains[0], 1e-9) // zero curve point, minus headroom
        assertEquals(0.0, gains[1], 1e-9)
        assertEquals(-4.0, gains[2], 1e-9)
    }

    @Test
    fun millibelsClampToTheEngineRange() {
        val mbs = BandMapping.millibels(doubleArrayOf(0.0, -30.0, 1.5), -1500..1500)
        assertEquals(0.toShort(), mbs[0])
        assertEquals((-1500).toShort(), mbs[1]) // -30 dB clamps to the device floor
        assertEquals(150.toShort(), mbs[2])

        val narrow = BandMapping.millibels(doubleArrayOf(2.0, -2.0), -600..600)
        assertEquals(600.toShort(), narrow[0])
        assertEquals((-200).toShort(), narrow[1])
    }

    @Test
    fun theLimiterIsProtectionNeverLoudness() {
        val limiter = LimiterSettings()
        assertTrue(limiter.isProtectionOnly())
        assertFalse("a boost after the limiter is loudness, not protection",
            limiter.copy(postGainDb = 3f).isProtectionOnly())
        assertFalse("a threshold above 0 dBFS lets the signal clip",
            limiter.copy(thresholdDb = 1f).isProtectionOnly())
        assertFalse(limiter.copy(enabled = false).isProtectionOnly())
        assertFalse(limiter.copy(ratio = 0.5f).isProtectionOnly())
    }
}
