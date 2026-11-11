package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.DpBandLayout
import tv.corebuilds.eq.apply.ToneLayers
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.ui.EnhancedAudioPrefs
import kotlin.math.abs

/**
 * Clarity and the headroom it shares with correction (1.14.0), on the
 * DynamicsProcessing band layout. Configured gains only: nothing here plays
 * audio or reads a device back.
 */
class ListeningLayersTest {

    private val centres = DpBandLayout.specs().map { it.centerHz }

    private fun profile(target: String = "flat", boostDb: Double = 0.0) = Profile(
        id = "p", name = "P", timestampMs = 1L, target = target, micType = "remote mic",
        curve = listOf(
            CurvePoint(40.0, 0.0, boostDb), CurvePoint(1_000.0, 0.0, -2.0), CurvePoint(8_000.0, 0.0, boostDb)
        )
    )

    @Test fun `the old dialogue switch and clarity plus two are the same sound`() {
        val p = profile()
        assertTrue(
            BandMapping.gainsDb(p, centres, ToneLayers(dialogue = true))
                .contentEquals(BandMapping.gainsDb(p, centres, ToneLayers(clarityDb = 2.0)))
        )
        assertEquals(ToneLayers(dialogue = true), ToneLayers(clarityDb = 2.0, dialogue = true))
    }

    @Test fun `clarity never stacks with a presence target's own lift`() {
        assertEquals(3.0, ToneLayers(clarityDb = 3.0).presenceGainDb(profile("flat")), 0.0)
        assertEquals(1.0, ToneLayers(clarityDb = 3.0).presenceGainDb(profile("dialogue")), 0.0)
        assertEquals(0.0, ToneLayers(clarityDb = 2.0).presenceGainDb(profile("house")), 0.0)
        assertEquals("clarity +3 dB", ToneLayers(clarityDb = 3.0).label(profile()))
    }

    @Test fun `one clarity step moves any applied band by at most one step`() {
        for (boost in listOf(0.0, 2.0, 6.0)) {
            val p = profile(boostDb = boost)
            for (from in 0..2) {
                val before = BandMapping.gainsDb(p, centres, ToneLayers(clarityDb = from.toDouble()))
                val after = BandMapping.gainsDb(p, centres, ToneLayers(clarityDb = from + 1.0))
                val worst = before.indices.maxOf { abs(after[it] - before[it]) }
                assertTrue("step $from→${from + 1} on a +$boost dB curve moved a band $worst dB", worst <= EnhancedAudioPrefs.CLARITY_STEP_DB + 1e-9)
            }
        }
    }

    @Test fun `worst-case layers still never boost a band`() {
        val p = profile(boostDb = 6.0).copy(
            manualFilters = List(ManualEq.BAND_CENTRES_HZ.size) { PeakingFilter(ManualEq.BAND_CENTRES_HZ[it], 1.0, ManualEq.MAX_GAIN_DB) }
        )
        val worst = ToneLayers(
            clarityDb = EnhancedAudioPrefs.CLARITY_MAX_DB,
            lowVolumeBassDb = ToneLayers.MAX_LOW_VOLUME_BASS_DB,
            stereoDownmix = true,
            loudnessBandGains = DoubleArray(10) { 6.0 }
        )
        for (side in BandMapping.AbSide.entries) {
            val gains = BandMapping.gainsDb(p, centres, worst, side)
            assertTrue("$side: every band finite", gains.all { it.isFinite() })
            assertTrue("$side: a band boosts (${gains.maxOrNull()} dB)", gains.all { it <= 1e-9 })
        }
    }

    @Test fun `stored clarity snaps into whole steps from 0 to 3 dB`() {
        assertEquals(0.0, EnhancedAudioPrefs.clampClarity(-4.0), 0.0)
        assertEquals(2.0, EnhancedAudioPrefs.clampClarity(1.6), 0.0)
        assertEquals(3.0, EnhancedAudioPrefs.clampClarity(9.0), 0.0)
        assertEquals(0.0, EnhancedAudioPrefs.clampClarity(Double.NaN), 0.0)
    }
}
