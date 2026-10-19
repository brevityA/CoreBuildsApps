package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.Resample
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class ResampleTest {

    @Test
    fun `44 point 1 kHz capture is converted to the right analysis duration`() {
        val inputRate = 44_100
        val input = DoubleArray(inputRate) { 0.6 * sin(2.0 * PI * 997.0 * it / inputRate) }

        val output = Resample.toRate(input, inputRate, 16_000)

        assertEquals(16_000, output.size)
    }

    @Test
    fun `fractional downsampling preserves a tone below the analysis passband`() {
        val inputRate = 44_100
        val frequency = 1_003.0
        val input = DoubleArray(inputRate / 2) { 0.6 * sin(2.0 * PI * frequency * it / inputRate) }
        val output = Resample.toRate(input, inputRate, 16_000)
        val start = 1_000
        val end = output.size - 1_000
        val rms = sqrt(output.sliceArray(start until end).sumOf { it * it } / (end - start))

        assertEquals("amplitude is preserved away from the filter edges", 0.6 / sqrt(2.0), rms, 0.01)
        assertEquals(
            "fractional resampling must not introduce a timing offset",
            0.6 * sin(2.0 * PI * frequency * start / 16_000.0),
            output[start],
            0.005
        )
    }

    @Test
    fun `fractional downsampling rejects energy above the analysis Nyquist`() {
        val inputRate = 44_100
        val input = DoubleArray(inputRate / 2) { 0.6 * sin(2.0 * PI * 12_000.0 * it / inputRate) }
        val output = Resample.toRate(input, inputRate, 16_000)
        val start = 1_000
        val end = output.size - 1_000
        val rms = sqrt(output.sliceArray(start until end).sumOf { it * it } / (end - start))

        assertTrue("12 kHz should be rejected before the 16 kHz sample rate", rms < 0.01)
    }

    @Test
    fun `integer ratio conversion keeps the optimized decimator`() {
        val input = DoubleArray(3_000) { it.toDouble() }

        val actual = Resample.toRate(input, 48_000, 16_000)
        val expected = Resample.decimate(input, 3)

        assertTrue(actual.contentEquals(expected))
    }
}
