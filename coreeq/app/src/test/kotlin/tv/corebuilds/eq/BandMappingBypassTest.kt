package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.ToneLayers
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile

/**
 * The loudness-matched A/B bypass.
 *
 * Turning correction off by deleting the effect changes the level as well as
 * the shape, and the louder side nearly always wins a sighted comparison. The
 * bypass must hold the level where the correction put it.
 */
class BandMappingBypassTest {

    private val centres = listOf(60.0, 120.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0)

    private fun profile(curve: List<CurvePoint>, manual: List<PeakingFilter> = emptyList()) = Profile(
        id = "p",
        name = "Test",
        timestampMs = 0L,
        target = "dialogue",
        micType = "remote",
        curve = curve,
        manualFilters = manual
    )

    private val corrected = profile(
        listOf(
            CurvePoint(60.0, 0.0, -3.0),
            CurvePoint(120.0, 0.0, 2.0),
            CurvePoint(250.0, 0.0, -4.0),
            CurvePoint(500.0, 0.0, 0.5),
            CurvePoint(1000.0, 0.0, 0.0),
            CurvePoint(2000.0, 0.0, -1.0),
            CurvePoint(4000.0, 0.0, 1.5),
            CurvePoint(8000.0, 0.0, 0.0)
        )
    )

    @Test
    fun theBypassSitsAtTheCorrectionsOwnAverageLevel() {
        val requested = BandMapping.requestedGainsDb(corrected, centres)
        val bypass = BandMapping.bypassDb(corrected, centres)
        val mean = requested.average()
        assertEquals("bypass must track the correction's mean gain", mean, bypass, 1e-9)
    }

    @Test
    fun theBypassNeverAsksForPositiveGain() {
        // A profile of pure boosts would otherwise bypass *louder* than the
        // corrected setting and win its own comparison.
        val allBoost = profile(
            listOf(CurvePoint(60.0, 0.0, 3.0), CurvePoint(120.0, 0.0, 4.0), CurvePoint(250.0, 0.0, 2.0),
                CurvePoint(500.0, 0.0, 3.0), CurvePoint(1000.0, 0.0, 5.0), CurvePoint(2000.0, 0.0, 4.0),
                CurvePoint(4000.0, 0.0, 3.0), CurvePoint(8000.0, 0.0, 2.0))
        )
        assertTrue("bypass was ${BandMapping.bypassDb(allBoost, centres)} dB",
            BandMapping.bypassDb(allBoost, centres) <= 0.0)
    }

    @Test
    fun theBypassIsFlatAcrossEveryBand() {
        val fit = BandMapping.fitBypassed(corrected, centres)
        for (value in fit.targetDb) {
            assertEquals(fit.targetDb[0], value, 1e-12)
            assertTrue("bypass band $value dB is positive", value <= 0.0)
        }
    }

    @Test
    fun theBypassTakesNoHeadroomOfItsOwn() {
        val fit = BandMapping.fitBypassed(corrected, centres)
        assertEquals(0.0, fit.headroomDb, 0.0)
        assertEquals(0, fit.rangeLimitedBands)
        assertEquals(0.0, fit.report().rmsCentreGainResidualDb, 0.0)
    }

    @Test
    fun runtimeToneLayersKeepTheComparisonFair() {
        val withLayers = ToneLayers(dialogue = true, lowVolumeBassDb = 3.0)
        val plain = BandMapping.bypassDb(corrected, centres)
        val layered = BandMapping.bypassDb(corrected, centres, withLayers)
        // The dialogue boost lifts the average, so the bypass must lift with it.
        assertTrue("layered bypass $layered vs $plain", layered > plain)
    }

    @Test
    fun aManualOnlyProfileWithPureCutsStillMatchesLevel() {
        val cuts = profile(
            curve = emptyList(),
            manual = listOf(
                PeakingFilter(250.0, 1.0, -3.0),
                PeakingFilter(4000.0, 1.0, -2.0)
            )
        )
        val requested = BandMapping.requestedGainsDb(cuts, centres)
        assertEquals(requested.average(), BandMapping.bypassDb(cuts, centres), 1e-9)
        assertTrue(BandMapping.bypassDb(cuts, centres) < 0.0)
    }

    @Test
    fun anEmptyBandListIsRefusedRatherThanDividingByZero() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            BandMapping.bypassDb(corrected, emptyList())
        }
    }
}
