package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the ISO 226 equal-loudness compensation so it cannot drift.
 *
 * The contours come from the published ISO 226:2003 table; the
 * compensation is their difference normalised to 0 dB at 1 kHz.
 */
class EqualLoudnessTest {

    private fun indexOfFrequency(hz: Double): Int =
        EqualLoudness.FREQUENCIES_HZ.indices.first { EqualLoudness.FREQUENCIES_HZ[it] == hz }

    @Test
    fun `contour at 1 kHz equals the phon value`() {
        // At 1 kHz, SPL equals phon by definition.
        val contour = EqualLoudness.contourAtPhon(60.0)
        val kHz1 = indexOfFrequency(1000.0)
        // The 60-phon contour at 1 kHz should be 60 dB SPL
        assertEquals(60.0, contour[kHz1], 0.5)
    }

    @Test
    fun `contour at low frequencies is higher than at 1 kHz`() {
        // Bass needs more SPL to sound equally loud — the whole point of
        // the Fletcher-Munson effect.
        val contour = EqualLoudness.contourAtPhon(50.0)
        val kHz1 = indexOfFrequency(1000.0)
        val hz40 = indexOfFrequency(40.0)
        assertTrue("40 Hz should need more SPL than 1 kHz at 50 phon",
            contour[hz40] > contour[kHz1] + 10.0)
    }

    @Test
    fun `compensation is flat at reference level`() {
        // When reference and playback are the same phon, compensation is 0.
        val comp = EqualLoudness.compensationDb(70.0, 70.0, 1.0)
        for (db in comp) {
            assertEquals(0.0, db, 0.01)
        }
    }

    @Test
    fun `compensation boosts bass at lower volume`() {
        val comp = EqualLoudness.compensationDb(80.0, 40.0, 1.0)
        val hz40 = indexOfFrequency(40.0)
        val kHz1 = indexOfFrequency(1000.0)
        // 1 kHz is the normalisation point: 0 dB
        assertEquals(0.0, comp[kHz1], 0.01)
        // 40 Hz should get significant boost (typically 15+ dB at this delta)
        assertTrue("40 Hz should get bass boost at low volume, got ${comp[hz40]}",
            comp[hz40] > 8.0)
    }

    @Test
    fun `compensation is zero when strength is zero`() {
        val comp = EqualLoudness.compensationDb(80.0, 30.0, 0.0)
        for (db in comp) {
            assertEquals(0.0, db, 0.01)
        }
    }

    @Test
    fun `compensation scales linearly with strength`() {
        val full = EqualLoudness.compensationDb(80.0, 40.0, 1.0)
        val half = EqualLoudness.compensationDb(80.0, 40.0, 0.5)
        for (i in full.indices) {
            assertEquals(full[i] * 0.5, half[i], 0.01)
        }
    }

    @Test
    fun `phon estimate decreases with volume`() {
        val loud = EqualLoudness.estimatePhon(15, 15, 85.0)
        val quiet = EqualLoudness.estimatePhon(5, 15, 85.0)
        assertTrue("Quieter volume should give lower phon", quiet < loud)
        assertEquals("Max volume should be near reference", 85.0, loud, 0.1)
    }

    @Test
    fun `phon estimate never drops below 20`() {
        val silent = EqualLoudness.estimatePhon(0, 15, 85.0)
        assertEquals(20.0, silent, 0.01)
    }

    @Test
    fun `interpolation onto arbitrary frequencies works`() {
        val freqs = listOf(50.0, 500.0, 1000.0, 5000.0)
        val comp = EqualLoudness.compensationAtFreqs(80.0, 40.0, 1.0, freqs)
        assertEquals(4, comp.size)
        // 1 kHz should be 0
        assertEquals(0.0, comp[2], 0.5)
        // 50 Hz should be positive (bass boost)
        assertTrue(comp[0] > 0.0)
    }

    @Test
    fun `all contours are monotonic-ish at low frequencies`() {
        // At 40 Hz, higher phon levels should have lower excess SPL
        // (the ear is relatively more sensitive at higher absolute levels)
        val low = EqualLoudness.contourAtPhon(30.0)
        val high = EqualLoudness.contourAtPhon(80.0)
        val hz40 = indexOfFrequency(40.0)
        val kHz1 = indexOfFrequency(1000.0)
        val excess30 = low[hz40] - low[kHz1]
        val excess80 = high[hz40] - high[kHz1]
        assertTrue("Low frequencies should need relatively less boost at high phon",
            excess30 > excess80)
    }
}
