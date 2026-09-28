package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionLimitsTest {

    @Test
    fun bandEdgesAreInclusive() {
        assertTrue(CorrectionLimits.isCorrectable(40.0))
        assertTrue(CorrectionLimits.isCorrectable(8000.0))
        assertFalse(CorrectionLimits.isCorrectable(39.9))
        assertFalse(CorrectionLimits.isCorrectable(8000.1))
    }

    @Test
    fun outsideTheBandTheGainIsExactlyZero() {
        assertEquals(0.0, CorrectionLimits.limit(20.0, 5.0), 0.0)
        assertEquals(0.0, CorrectionLimits.limit(12000.0, -9.0), 0.0)
    }

    @Test
    fun boostsAndCutsAreClamped() {
        assertEquals(6.0, CorrectionLimits.limit(100.0, 15.0), 0.0)
        assertEquals(-12.0, CorrectionLimits.limit(100.0, -30.0), 0.0)
        assertEquals(-3.5, CorrectionLimits.limit(1000.0, -3.5), 0.0)
    }

    @Test
    fun notANumberNeverBecomesAGain() {
        assertEquals(0.0, CorrectionLimits.limit(1000.0, Double.NaN), 0.0)
    }
}
