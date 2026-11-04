package tv.corebuilds.eq.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.ManualEq

class ManualEqReadoutTest {

    private fun band(hz: Double) = ManualEq.BAND_CENTRES_HZ.indexOf(hz)

    @Test
    fun aMeasuredProfileNamesTheCorrectionUnderTheBand() {
        assertEquals(
            "2 kHz · band +1.5 dB · room correction -3.0 dB · together -1.8 dB",
            ManualEqReadout.line(band(2_000.0), 1.5, -3.0, -1.84)
        )
    }

    @Test
    fun aManualOnlyProfileSaysThereIsNoMeasurement() {
        assertEquals("63 Hz · band -2.0 dB · no room measurement", ManualEqReadout.line(band(63.0), -2.0, null, null))
    }

    @Test
    fun theVisibleLineIsNotTheScreenReaderSentence() {
        // 1.11.x showed "Manual equaliser. Band 5 of 10, 500 hertz, …" here.
        val line = ManualEqReadout.line(band(500.0), 0.0, 0.0, 0.0)
        assertEquals("500 Hz · band +0.0 dB · room correction +0.0 dB · together +0.0 dB", line)
        assertFalse(line.contains("hertz"))
    }

    @Test
    fun aRoundingResidueNeverReadsMinusZero() {
        assertEquals("1 kHz · band +0.0 dB · room correction +0.0 dB · together +0.0 dB",
            ManualEqReadout.line(band(1_000.0), 0.0, -0.04, -0.01))
    }

    @Test
    fun anUnknownBandIsBlank() {
        assertEquals("", ManualEqReadout.line(42, 0.0, 0.0, 0.0))
    }

    @Test
    fun togetherIsTheCorrectionPlusTheWholeManualLayer() {
        // +3 dB at 1 kHz reaches its neighbour: at 2 kHz the layer is above 0 dB though
        // the 2 kHz slider is flat, so "together" is not correction + the band's slider.
        val filters = ManualEq.withBandGain(emptyList(), ManualEq.BAND_CENTRES_HZ.indexOf(1_000.0), 3.0)
        val together = ManualEqReadout.together(-2.0, filters, 2_000.0)!!
        assertEquals(-2.0 + ManualEq.responseDb(filters, 2_000.0), together, 1e-9)
        assertTrue(together > -2.0)
        assertEquals(1.0, ManualEqReadout.together(-2.0, filters, 1_000.0)!!, 0.05)
        assertNull(ManualEqReadout.together(null, filters, 1_000.0))
    }
}
