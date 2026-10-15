package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCorrectionCheckTest {

    @Test
    fun `ignores bands outside the trusted 40 Hz to 8 kHz window`() {
        // Eight trusted bands from 100 Hz to 8 kHz, between 20 Hz and 16 kHz bands whose
        // large errors must not count.
        val trusted = doubleArrayOf(100.0, 200.0, 400.0, 800.0, 1000.0, 2000.0, 4000.0, 8000.0)
        val centres = doubleArrayOf(20.0) + trusted + doubleArrayOf(16000.0)
        val measured = doubleArrayOf(30.0, 1.0) + DoubleArray(7) + doubleArrayOf(-30.0)
        val target = DoubleArray(centres.size)
        val result = PostCorrectionCheck.residual(centres, measured, target)
        assertEquals(8, result.bands)
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
        val result = PostCorrectionCheck.residual(centres, doubleArrayOf(4.0, 0.0), DoubleArray(2))
        assertFalse(result.sufficient)
        assertEquals(2, result.bands)
        assertNull(result.rmsDb)
        assertNull(result.worstHz)
        assertNull(result.worstDb)
    }

    @Test
    fun `returns no summary when nothing is trusted`() {
        val centres = doubleArrayOf(20.0, 16000.0)
        val result = PostCorrectionCheck.residual(centres, DoubleArray(2), DoubleArray(2))
        assertEquals(0, result.bands)
        assertNull(result.rmsDb)
    }

    @Test
    fun `skipped bands are left out of the figures`() {
        // A room null the correction leaves alone is not a residual it failed to remove.
        val centres = DoubleArray(9) { 100.0 * (it + 1) }
        val measured = DoubleArray(9) { if (it == 2) -15.0 else 0.0 }
        val skip = BooleanArray(9) { it == 2 }
        val result = PostCorrectionCheck.residual(centres, measured, DoubleArray(9), skip)
        assertEquals(8, result.bands)
        assertEquals(0.0, result.rmsDb!!, 1e-9)
    }

    @Test
    fun `aligned residual ignores the level and reads the shape`() {
        // A sweep with the correction on is quieter by the preamp: 6 dB down everywhere is
        // no residual at all, and a 3 dB bump on top of it still is.
        val centres = doubleArrayOf(100.0, 200.0, 315.0, 500.0, 800.0, 1250.0, 2000.0, 3150.0, 5000.0)
        val quieter = DoubleArray(centres.size) { -6.0 }
        val flat = PostCorrectionCheck.alignedResidual(centres, quieter, DoubleArray(centres.size))!!
        assertEquals(0.0, flat.rmsDb!!, 1e-9)
        val bump = DoubleArray(centres.size) { if (it == 0) -3.0 else -6.0 }
        val shaped = PostCorrectionCheck.alignedResidual(centres, bump, DoubleArray(centres.size))!!
        assertEquals(100.0, shaped.worstHz!!, 1e-9)
        assertEquals(3.0, shaped.worstDb!!, 1e-9)
    }

    @Test
    fun `aligned residual needs a band between 300 Hz and 3 kHz`() {
        val centres = doubleArrayOf(100.0, 200.0, 5000.0)
        assertNull(PostCorrectionCheck.alignedResidual(centres, DoubleArray(3), DoubleArray(3)))
    }

    private val centres = DspConstants.ISO_CENTRES_HZ.filter { it in 31.5..DspConstants.F_MAX }.toDoubleArray()

    /** A sweep result with [measured] against a flat target, every band trusted unless marked. */
    private fun sweep(
        measured: DoubleArray,
        correction: DoubleArray = DoubleArray(centres.size),
        nulls: BooleanArray = BooleanArray(centres.size),
        minPhaseOk: BooleanArray = BooleanArray(centres.size) { true },
        bandSnr: DoubleArray = DoubleArray(centres.size) { 40.0 },
        floorHz: Double = DspConstants.F_MIN,
        fMaxHz: Double = DspConstants.F_MAX
    ) = SweepResult(
        centresHz = centres, measuredDb = measured, targetDb = DoubleArray(centres.size), correctionDb = correction,
        nullMask = nulls, minPhaseOk = minPhaseOk, rt60Seconds = 0.4, schroederHz = null, transitionHz = 300.0,
        rolloffHz = floorHz, floorHz = floorHz, snrDb = 40.0, latencyMs = 0.0, filters = emptyList(), preampDb = 0.0,
        bandSnrDb = bandSnr, fMaxHz = fMaxHz
    )

    private fun idx(hz: Double) = centres.indices.minByOrNull { kotlin.math.abs(centres[it] - hz) }!!

    @Test
    fun `left alone is every band the correction may not touch`() {
        val nulls = BooleanArray(centres.size).also { it[idx(125.0)] = true }
        val gated = BooleanArray(centres.size) { true }.also { it[idx(80.0)] = false }
        val noisy = DoubleArray(centres.size) { 40.0 }.also { it[idx(5000.0)] = 6.0 }
        val r = sweep(DoubleArray(centres.size), nulls = nulls, minPhaseOk = gated, bandSnr = noisy,
            floorHz = 50.0, fMaxHz = 6300.0)
        val skip = PostCorrectionCheck.leftAlone(r)
        for (k in centres.indices) {
            val expected = centres[k] < 50.0 || centres[k] > 6300.0 ||
                k == idx(125.0) || k == idx(80.0) || k == idx(5000.0)
            assertEquals("band ${centres[k]} Hz", expected, skip[k])
        }
    }

    @Test
    fun `a correction that lands reads as a smaller residual than the room`() {
        val room = DoubleArray(centres.size).also { it[idx(100.0)] = 6.0; it[idx(160.0)] = -4.0 }
        val made = sweep(room, correction = DoubleArray(centres.size) { -room[it] })
        // Played back through the correction, 2 dB quieter, with 1 dB of the peak left.
        val heard = DoubleArray(centres.size) { -2.0 }.also { it[idx(100.0)] = -1.0 }
        val c = PostCorrectionCheck.compare(made, sweep(heard))!!
        assertEquals(0.0, c.predicted.rmsDb!!, 1e-9)
        assertTrue("after ${c.after.rmsDb} before ${c.before.rmsDb}", c.after.rmsDb!! < c.before.rmsDb!!)
        assertEquals(100.0, c.after.worstHz!!, 1e-9)
        assertEquals(1.0, c.after.worstDb!!, 1e-9)
        assertEquals(c.before.bands, c.after.bands)
    }

    @Test
    fun `a null the correction left alone is not counted against it`() {
        val room = DoubleArray(centres.size).also { it[idx(125.0)] = -15.0 }
        val nulls = BooleanArray(centres.size).also { it[idx(125.0)] = true }
        val c = PostCorrectionCheck.compare(sweep(room, nulls = nulls), sweep(room))!!
        assertEquals(0.0, c.before.rmsDb!!, 1e-9)
        assertEquals(0.0, c.after.rmsDb!!, 1e-9)
    }

    @Test
    fun `a band the check could not hear is left out of all three figures`() {
        val made = sweep(DoubleArray(centres.size))
        val noisy = DoubleArray(centres.size) { 40.0 }.also { it[idx(4000.0)] = 3.0 }
        val heard = DoubleArray(centres.size).also { it[idx(4000.0)] = -20.0 }
        val c = PostCorrectionCheck.compare(made, sweep(heard, bandSnr = noisy))!!
        assertEquals(0.0, c.after.rmsDb!!, 1e-9)
        assertEquals(c.before.bands, c.after.bands)
    }

    @Test
    fun `sweeps on different bands are not compared`() {
        val shifted = centres.map { it * 1.05 }.toDoubleArray()
        val other = sweep(DoubleArray(centres.size)).copy(centresHz = shifted)
        assertNull(PostCorrectionCheck.compare(sweep(DoubleArray(centres.size)), other))
    }
}
