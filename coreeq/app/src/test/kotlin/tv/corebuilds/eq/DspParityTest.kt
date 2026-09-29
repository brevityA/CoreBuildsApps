package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.Correction
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.Peaking
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.dsp.SyntheticRoom
import tv.corebuilds.eq.dsp.Targets
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.Profile

class DspParityTest {

    @Test
    fun schroederFrequencyMatchesLiterature() {
        // 54 m3 room with 0.5s RT60 gives ~192.45 Hz
        val fS = Correction.schroederHz(54.0, 0.5)
        assertEquals(192.45, fS, 0.5)
    }

    @Test
    fun transitionFrequencyHonorsFallbacksAndCaps() {
        // Unknown room defaults to 300 Hz
        assertEquals(300.0, Correction.transitionHz(null, null), 0.0)
        // High Schroeder is capped at 400 Hz
        val capped = Correction.transitionHz(10.0, 1.0)
        assertEquals(400.0, capped, 0.0)
    }

    @Test
    fun targetCurveFlatIsZeroEverywhere() {
        val freqs = doubleArrayOf(50.0, 100.0, 1000.0, 5000.0)
        val flat = Targets.targetCurve("flat", freqs)
        for (v in flat) {
            assertEquals(0.0, v, 0.0)
        }
    }

    @Test
    fun targetCurveBkRollsOffInTreble() {
        val freqs = doubleArrayOf(100.0, 160.0, 1000.0, 10000.0)
        val bk = Targets.targetCurve("bk", freqs)
        // Pivots at 630 Hz -> 0 dB
        assertTrue(bk[0] > bk[bk.size - 1])
    }

    @Test
    fun nullDetectionFindsDeepSyntheticCancellation() {
        val freqs = DspConstants.ISO_CENTRES_HZ
        val measured = SyntheticRoom.generateDb(freqs, 11L)
        val nulls = Correction.detectNulls(freqs, measured)
        // 84 Hz has a synthetic -14 dB null
        var found84Null = false
        for (i in freqs.indices) {
            if (freqs[i] in 75.0..95.0 && nulls[i]) {
                found84Null = true
            }
        }
        assertTrue("Expected 84 Hz deep null to be detected", found84Null)
    }

    @Test
    fun peakingMagnitudeAtCenterFrequencyEqualsGain() {
        val freqs = doubleArrayOf(1000.0)
        val mag = Peaking.peakingMagnitudeDb(freqs, 1000.0, 2.0, 6.0)
        assertEquals(6.0, mag[0], 0.05)
    }

    @Test
    fun preampRoundsTowardMoreNegative() {
        val filters = listOf(
            PeakingFilter(100.0, 1.0, 3.24),
            PeakingFilter(500.0, 1.5, -4.00)
        )
        val preamp = Peaking.preampDb(filters)
        assertEquals(-3.24, preamp, 0.001)
    }

    @Test
    fun parametricExportIncludesPowerampCascadeComment() {
        val profile = Profile(
            id = "test-1",
            name = "Test",
            timestampMs = 1000L,
            target = "dialogue",
            micType = "remote mic",
            filters = listOf(PeakingFilter(100.0, 1.0, 2.0))
        )
        val txt = Formats.exportParametricTxt(profile)
        assertTrue(txt.contains("Preamp:"))
        assertTrue(txt.contains("Bands Overlap to Cascade"))
        assertTrue(txt.contains("Filter 1: ON PK Fc 100 Hz Gain +2.00 dB Q 1.00"))
    }

    @Test
    fun importedMeasurementLimitsTravelWithParametricExports() {
        val note = "REW magnitude-only import: minimum-phase gate unverified."
        val profile = Profile(
            id = "rew-test",
            name = "REW Test",
            timestampMs = 1000L,
            target = "flat",
            micType = "REW import",
            filters = listOf(PeakingFilter(100.0, 1.0, -2.0)),
            measurementNotes = listOf(note)
        )
        assertTrue(Formats.exportParametricTxt(profile).contains("# Core EQ measurement note: $note"))

        val noFilters = profile.copy(filters = emptyList())
        assertTrue(Formats.exportParametricTxt(noFilters).contains("# Core EQ measurement note: $note"))
    }
}
