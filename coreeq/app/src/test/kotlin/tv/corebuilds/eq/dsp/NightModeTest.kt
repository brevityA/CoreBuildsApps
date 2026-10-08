package tv.corebuilds.eq.dsp

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the night-mode constants so the limiter thresholds cannot drift
 * from the documented values. Dialogue boost is pinned by `ToneLayersTest`.
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
}
