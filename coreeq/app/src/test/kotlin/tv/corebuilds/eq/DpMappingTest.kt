package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.DpBandLayout
import tv.corebuilds.eq.apply.LimiterSettings
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile

/**
 * The band maths every engine shares (`BandMapping`) and the DynamicsProcessing
 * limiter's protection invariants (`LimiterSettings`) — plan M6's pure half.
 * The adapter is wired; M6a's physical engagement and clamp test remains open.
 */
class DpMappingTest {

    private fun profile(vararg points: Pair<Double, Double>) = Profile(
        id = "t", name = "t", timestampMs = 0L, target = "flat", micType = "test",
        curve = points.map { CurvePoint(it.first, 0.0, it.second) }
    )

    @Test
    fun gainsAreHeadroomShiftedSoNothingBoosts() {
        val p = profile(100.0 to 3.0, 400.0 to -2.0, 1000.0 to 0.0)
        val gains = BandMapping.gainsDb(p, listOf(100.0, 400.0, 1000.0))
        // The largest boost lands on 0: an engine with no preamp cannot clip.
        assertEquals(0.0, gains[0], 1e-9)
        assertEquals(-5.0, gains[1], 1e-9)
        assertEquals(-3.0, gains[2], 1e-9)
        assertTrue("nothing may boost: ${gains.toList()}", gains.all { it <= 0.0 })
    }

    @Test
    fun aCutOnlyProfileIsLeftExactlyAsMeasured() {
        val p = profile(100.0 to -6.0, 1000.0 to -1.0)
        val gains = BandMapping.gainsDb(p, listOf(100.0, 1000.0))
        assertEquals(-6.0, gains[0], 1e-9)
        assertEquals(-1.0, gains[1], 1e-9)
    }

    @Test
    fun bandsOutsideTheCurveReceiveOnlyHeadroomCut() {
        val p = profile(100.0 to 4.0)
        val gains = BandMapping.gainsDb(p, listOf(50.0, 100.0, 14000.0))
        assertEquals(-4.0, gains[0], 1e-9) // zero curve point, minus headroom
        assertEquals(0.0, gains[1], 1e-9)
        assertEquals(-4.0, gains[2], 1e-9)
    }

    @Test
    fun backendFitUsesReportedCentresRangeAndGainReadBack() {
        val p = profile(100.0 to 5.0, 500.0 to -1.0)
        val fit = BandMapping.fitToBackend(
            p,
            listOf(100.0, 500.0),
            minGainDb = -4.0,
            maxGainDb = 0.0
        )
        assertEquals(5.0, fit.headroomDb, 1e-9)
        assertEquals(0.0, fit.targetDb[0], 1e-9)
        assertEquals(-6.0, fit.targetDb[1], 1e-9)
        assertEquals(-4.0, fit.configuredDb[1], 1e-9)
        assertEquals(1, fit.rangeLimitedBands)

        // Equalizer read-back is compared to the headroom-shifted target, not
        // mistaken for an acoustic response between its reported band centres.
        val report = fit.report(doubleArrayOf(-0.1, -4.0))
        assertEquals(2, report.bandCount)
        assertEquals(1, report.rangeLimitedBands)
        assertEquals(2.0, report.maxCentreGainResidualDb, 1e-9)
        assertEquals(kotlin.math.sqrt(2.005), report.rmsCentreGainResidualDb, 1e-9)
        assertTrue(report.statusSummary().contains("backend centre-gain fit"))
    }

    @Test
    fun backendFitRejectsInvalidReportedLayoutOrRange() {
        val p = profile(100.0 to 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            BandMapping.fitToBackend(p, listOf(100.0, Double.NaN))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BandMapping.fitToBackend(p, listOf(200.0, 100.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BandMapping.fitToBackend(p, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            BandMapping.fitToBackend(p, listOf(100.0), minGainDb = -1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BandMapping.fitToBackend(p, listOf(100.0), minGainDb = 2.0, maxGainDb = 1.0)
        }
    }

    @Test
    fun millibelsClampToTheSuppliedRange() {
        val mbs = BandMapping.millibels(doubleArrayOf(0.0, -30.0, 1.5), -1500..1500)
        assertEquals(0.toShort(), mbs[0])
        assertEquals((-1500).toShort(), mbs[1]) // -30 dB clamps to the device floor
        assertEquals(150.toShort(), mbs[2])

        val narrow = BandMapping.millibels(doubleArrayOf(8.0, -2.0), -600..600)
        assertEquals(600.toShort(), narrow[0])
        assertEquals((-200).toShort(), narrow[1])
    }

    @Test
    fun theLimiterIsProtectionNeverLoudness() {
        val limiter = LimiterSettings()
        assertTrue(limiter.isProtectionOnly())
        assertFalse("a boost after the limiter is loudness, not protection",
            limiter.copy(postGainDb = 3f).isProtectionOnly())
        assertFalse("a threshold above 0 dBFS lets the signal clip",
            limiter.copy(thresholdDb = 1f).isProtectionOnly())
        assertFalse(limiter.copy(enabled = false).isProtectionOnly())
        assertFalse(limiter.copy(ratio = 0.5f).isProtectionOnly())
    }

    @Test
    fun dynamicsProcessingLayoutHasAscendingCutoffsAndTrustedBandCentres() {
        val specs = DpBandLayout.specs()
        assertEquals(DpBandLayout.BAND_COUNT, specs.size)
        assertEquals(DpBandLayout.CORRECTION_BAND_COUNT, specs.size - 1)
        assertTrue(specs.zipWithNext().all { (a, b) ->
            b.centerHz > a.centerHz && b.cutoffHz > a.cutoffHz
        })
        assertEquals(DpBandLayout.MIN_HZ, specs.first().centerHz, 1e-9)
        assertEquals(6300.0, specs[DpBandLayout.CORRECTION_BAND_COUNT - 2].centerHz, 1e-6)
        assertEquals(DpBandLayout.MAX_HZ, specs[DpBandLayout.CORRECTION_BAND_COUNT - 1].cutoffHz, 1e-6)
        assertTrue(specs.take(DpBandLayout.CORRECTION_BAND_COUNT).all {
            it.centerHz in DpBandLayout.MIN_HZ..DpBandLayout.MAX_HZ
        })
        assertTrue("high guard band must be outside the correction span", specs.last().centerHz > DpBandLayout.MAX_HZ)
    }

    @Test
    fun dynamicsProcessingReadBackCutoffsRecoverTheRequestedBandCentres() {
        val specs = DpBandLayout.specs()
        val centres = DpBandLayout.centresFromCutoffs(specs.map { it.cutoffHz })
        assertEquals(specs.size, centres.size)
        specs.indices.forEach { index ->
            assertEquals(specs[index].centerHz, centres[index], 1e-8)
        }
    }

    @Test
    fun highFrequencyGuardBandOnlyGetsTheSharedHeadroomCut() {
        val p = profile(40.0 to 0.0, 1000.0 to 0.0, 4000.0 to 3.0, 8000.0 to 0.0)
        val centres = DpBandLayout.specs().map { it.centerHz }
        val gains = BandMapping.gainsDb(p, centres)
        assertTrue(gains.all { it <= 0.0 })
        // The guard samples outside the curve, so it carries no correction of
        // its own: just the shared cut, which is the largest boost the band
        // centres actually sample. No centre lands exactly on the 4 kHz peak,
        // so that is just under 3 dB, not 3 dB.
        assertEquals(0.0, p.correctionAt(centres.last()), 1e-12)
        val headroom = centres.maxOf { p.correctionAt(it) }
        assertTrue(headroom > 2.9 && headroom <= 3.0)
        assertEquals(-headroom, gains.last(), 1e-12)
    }

    @Test
    fun dynamicsProcessingLayoutRejectsUnorderedOrNonFiniteCutoffs() {
        assertThrows(IllegalArgumentException::class.java) {
            DpBandLayout.centresFromCutoffs(listOf(100.0, 90.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DpBandLayout.centresFromCutoffs(listOf(100.0, Double.NaN))
        }
    }
}
