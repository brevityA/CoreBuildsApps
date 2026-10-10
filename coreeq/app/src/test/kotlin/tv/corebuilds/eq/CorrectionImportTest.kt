package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.corebuilds.eq.dsp.CorrectionImport
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.PeakingFilter

class CorrectionImportTest {

    @Test
    fun aSinglePeakingFilterOnABandCentreIsFittedExactly() {
        val result = CorrectionImport.parse(
            "Preamp: -3.0 dB\nFilter 1: ON PK Fc 1000 Hz Gain 3.0 dB Q 1.0\n"
        )
        assertEquals("ParametricEQ", result.source)
        assertEquals(listOf(PeakingFilter(1000.0, 1.0, 3.0)), result.filters)
        assertTrue(result.maxErrorDb < 0.05)
        assertEquals(-3.0, result.preampDb!!, 1e-9)
        assertTrue(result.notes.any { it.contains("preamp") })
    }

    @Test
    fun aTwoFilterCurveIsApproximatedWithinHalfADecibel() {
        val result = CorrectionImport.parse(
            "Filter 1: ON PK Fc 63 Hz Gain -4.0 dB Q 0.7\nFilter 2: ON PK Fc 4000 Hz Gain 2.5 dB Q 1.4\n"
        )
        assertTrue(result.filters.isNotEmpty())
        assertTrue("error was ${result.maxErrorDb}", result.maxErrorDb < 0.5)
        result.filters.forEach { assertTrue(it.gain in -6.0..6.0) }
        assertTrue(result.filters.all { f -> ManualEq.BAND_CENTRES_HZ.any { kotlin.math.abs(it - f.fc) < 0.01 } })
    }

    @Test
    fun aFlatGraphicEqFitsToNoFilters() {
        val result = CorrectionImport.parse("GraphicEQ: 40 0; 1000 0; 8000 0")
        assertEquals("GraphicEQ", result.source)
        assertTrue(result.filters.isEmpty())
        assertEquals(0.0, result.maxErrorDb, 1e-9)
        assertNull(result.preampDb)
    }

    @Test
    fun aGraphicEqBumpAt1kHzBoostsThatBand() {
        val result = CorrectionImport.parse("GraphicEQ: 40 0; 1000 3; 8000 0")
        assertTrue(result.filters.any { it.fc == 1000.0 && it.gain > 0.0 })
    }

    @Test
    fun aLowShelfIsSkippedAndSaidSo() {
        val result = CorrectionImport.parse("Filter 1: ON LS 80 Hz Gain 4.0 dB Q 0.7\n")
        assertTrue(result.filters.isEmpty())
        assertTrue(result.notes.any { it.contains("LS") })
    }

    @Test
    fun anOffFilterIsIgnoredWithoutANote() {
        val result = CorrectionImport.parse("Filter 1: OFF PK Fc 1000 Hz Gain 3.0 dB Q 1.0\n")
        assertTrue(result.filters.isEmpty())
        assertTrue(result.notes.none { it.startsWith("Skipped") })
        assertTrue(result.notes.any { it.contains("no active") })
    }

    @Test
    fun aGainBeyondTheEditorRangeIsClampedAndSaid() {
        val result = CorrectionImport.parse("Filter 1: ON PK Fc 1000 Hz Gain 9.0 dB Q 1.0\n")
        result.filters.forEach { assertTrue(it.gain in -6.0..6.0) }
        assertTrue(result.notes.any { it.contains("9.0 dB") })
    }

    @Test
    fun textWithNoCorrectionIsRefused() {
        try {
            CorrectionImport.parse("hello world\nthis is not a correction file")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("No correction found"))
        }
    }

    @Test
    fun anOversizeFileIsRefusedBeforeParsing() {
        val big = "Preamp: -1.0 dB\n" + "# padding\n".repeat(CorrectionImport.MAX_CHARS / 5)
        try {
            CorrectionImport.parse(big)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("64 KB"))
        }
    }

    @Test
    fun importedFiltersSurviveSanitizeUnchanged() {
        val result = CorrectionImport.parse("Filter 1: ON PK Fc 1000 Hz Gain 3.0 dB Q 1.0\n")
        assertEquals(result.filters, ManualEq.sanitize(result.filters))
    }
}
