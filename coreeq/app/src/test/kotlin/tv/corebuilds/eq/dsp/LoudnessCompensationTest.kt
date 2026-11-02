package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full ISO 226 loudness compensation mapped to the ten manual EQ bands. */
class LoudnessCompensationTest {

    private val centres = ManualEq.BAND_CENTRES_HZ

    @Test
    fun `at or above the reference level there is no compensation`() {
        assertAllZeros(LoudnessCompensation.bandGains(0.0))
        assertAllZeros(LoudnessCompensation.bandGains(-5.0))
        assertAllZeros(LoudnessCompensation.bandGains(Double.NaN))
    }

    @Test
    fun `strength zero returns all zeros even far below reference`() {
        assertAllZeros(LoudnessCompensation.bandGains(30.0, strength = 0.0))
        assertAllZeros(LoudnessCompensation.bandGains(60.0, strength = 0.0))
    }

    @Test
    fun `below the reference, bass and treble gain while 1 kHz stays flat`() {
        val gains = LoudnessCompensation.bandGains(30.0, strength = 1.0)
        // 1 kHz is normalised to 0 dB in the ISO 226 difference
        val khzIdx = centres.indexOfFirst { it == 1000.0 }
        assertEquals(0.0, gains[khzIdx], 1e-9)
        // Bass bands should be positive (boost needed at low volume)
        assertTrue("63 Hz should need boost at low volume, was ${gains[centres.indexOfFirst { it == 63.0 }]}",
            gains[centres.indexOfFirst { it == 63.0 }] > 0.0)
        // Treble bands should also need boost
        assertTrue("8 kHz should need boost at low volume",
            gains[centres.indexOfFirst { it == 8000.0 }] > 0.0)
    }

    @Test
    fun `strength scales the compensation`() {
        val full = LoudnessCompensation.bandGains(30.0, strength = 1.0)
        val half = LoudnessCompensation.bandGains(30.0, strength = 0.5)
        val zero = LoudnessCompensation.bandGains(30.0, strength = 0.0)
        for (i in centres.indices) {
            assertEquals(half[i], full[i] * 0.5, 1e-9)
            assertEquals(0.0, zero[i], 1e-9)
        }
    }

    @Test
    fun `gains never exceed the maximum magnitude`() {
        val gains = LoudnessCompensation.bandGains(100.0, strength = 1.0)
        for (g in gains) {
            assertTrue("$g is within ${
                LoudnessCompensation::class.simpleName}.MAX_COMPENSATION_DB",
                g <= 6.0 && g >= -6.0)
        }
    }

    @Test
    fun `band gains are aligned with ManualEq band centres`() {
        val gains = LoudnessCompensation.bandGains(30.0)
        assertEquals(centres.size, gains.size)
    }

    private fun assertAllZeros(gains: DoubleArray) {
        for (g in gains) assertEquals(0.0, g, 1e-9)
    }
}
