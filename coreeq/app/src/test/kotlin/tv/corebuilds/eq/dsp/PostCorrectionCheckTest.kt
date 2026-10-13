package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCorrectionCheckTest {

    @Test
    fun `ignores bands outside the trusted 40 Hz to 8 kHz window`() {
        val centres = doubleArrayOf(20.0, 100.0, 1000.0, 8000.0, 16000.0)
        val measured = doubleArrayOf(30.0, 1.0, 0.0, 0.0, -30.0)
        val target = DoubleArray(centres.size)
        val result = PostCorrectionCheck.residual(centres, measured, target)
        assertEquals(3, result.bands)
        assertEquals(100.0, result.worstHz!!, 1e-9)
    }

    @Test
    fun `reports rms and the worst band`() {
        val centres = DoubleArray(8) { 100.0 * (it + 1) }
        val measured = DoubleArray(8) { if (it == 3) 3.0 else 0.0 }
        val target = DoubleArray(8)
        val result = PostCorrectionCheck.residual(centres, measured, target)
        assertEquals(8, result.bands)
        assertEquals(Math.sqrt(9.0 / 8.0), result.rmsDb!!, 1e-9)
        assertEquals(400.0, result.worstHz!!, 1e-9)
        assertEquals(3.0, result.worstDb!!, 1e-9)
        assertTrue(result.sufficient)
    }

    @Test
    fun `withholds the summary when there are too few bands`() {
        val centres = doubleArrayOf(100.0, 200.0)
        val result = PostCorrectionCheck.residual(centres, DoubleArray(2), DoubleArray(2))
        assertFalse(result.sufficient)
    }

    @Test
    fun `returns no summary when nothing is trusted`() {
        val centres = doubleArrayOf(20.0, 16000.0)
        val result = PostCorrectionCheck.residual(centres, DoubleArray(2), DoubleArray(2))
        assertEquals(0, result.bands)
        assertNull(result.rmsDb)
    }
}
