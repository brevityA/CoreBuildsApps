package tv.corebuilds.eq.dsp

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the night-mode constants so the limiter thresholds and dialogue
 * filter cannot drift from the documented values.
 */
class NightModeTest {

    @Test
    fun `night limiter threshold is negative (compression, not protection)`() {
        assertTrue("Night limiter must compress, threshold < 0",
            NightMode.NIGHT_LIMITER_THRESHOLD_DB < 0f)
    }

    @Test
    fun `night limiter ratio is moderate`() {
        // 4:1 is gentle enough not to pump but firm enough to tame peaks
        assertTrue("Ratio should be between 2 and 8",
            NightMode.NIGHT_LIMITER_RATIO in 2f..8f)
    }

    @Test
    fun `dialogue filter is in the SII band`() {
        val filter = NightMode.dialogueFilter()
        assertTrue("Centre should be in 1–5 kHz SII band",
            filter.fc in 1000.0..5000.0)
        assertTrue("Gain should be modest (1–4 dB)",
            filter.gain in 1.0..4.0)
    }

    @Test
    fun `dialogue response is positive at the centre frequency`() {
        val response = NightMode.dialogueResponseDb(doubleArrayOf(NightMode.DIALOGUE_LIFT_CENTRE_HZ))
        assertTrue("Response at centre should be positive", response[0] > 0.0)
        assertEquals(NightMode.DIALOGUE_LIFT_DB, response[0], 0.1)
    }

    @Test
    fun `dialogue response is near zero far from the centre`() {
        val response = NightMode.dialogueResponseDb(doubleArrayOf(100.0, 20000.0))
        assertTrue("Response at 100 Hz should be near 0", abs(response[0]) < 0.5)
        assertTrue("Response at 20 kHz should be near 0", abs(response[1]) < 0.5)
    }
}
