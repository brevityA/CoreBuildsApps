package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.DpBandLayout
import tv.corebuilds.eq.apply.ExtraPlan
import tv.corebuilds.eq.apply.LimiterSettings
import tv.corebuilds.eq.apply.ToneLayers
import tv.corebuilds.eq.dsp.NightMode
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.mode.ContentType
import tv.corebuilds.eq.ui.EnhancedAudioPrefs

/**
 * The numbers `docs/CORE_EQ_HEADROOM_AUDIT.md` states, held here so
 * the document cannot drift from the code. Configured values only: nothing
 * here plays audio, reads a TV back, or knows where Android places BassBoost
 * and LoudnessEnhancer relative to DynamicsProcessing.
 */
class HeadroomAuditTest {

    private val centres = DpBandLayout.specs().map { it.centerHz }

    private fun flat() = Profile(
        id = "flat", name = "Flat", timestampMs = 1L, target = "flat", micType = "remote mic",
        curve = listOf(CurvePoint(40.0, 0.0, 0.0), CurvePoint(8_000.0, 0.0, 0.0))
    )

    @Test fun `the protection limiter is the one the audit documents`() {
        val l = LimiterSettings()
        assertEquals(-1f, l.thresholdDb, 0f)
        assertEquals(10f, l.ratio, 0f)
        assertEquals(1f, l.attackMs, 0f)
        assertEquals(50f, l.releaseMs, 0f)
        assertEquals(0f, l.kneeWidthDb, 0f)
        assertEquals(0f, l.postGainDb, 0f)
        assertTrue(l.linked && l.isProtectionOnly())
        // Night levels retune this one limiter; none adds gain or a second stage.
        for (level in NightMode.Level.entries) {
            val n = NightMode.limiter(level)
            assertTrue("$level", n.isProtectionOnly() && n.postGainDb == 0f && n.linked)
        }
        assertEquals(l, NightMode.limiter(NightMode.Level.OFF))
    }

    @Test fun `the Android effects are the only gain outside the headroom, and both ship off`() {
        for (type in ContentType.entries) {
            assertEquals("$type with both switches off", ExtraPlan.NONE, ExtraPlan.of(type, bassBoost = false, loudness = false))
        }
        // Upper bounds the audit quotes: LoudnessEnhancer +4 dB, BassBoost 600 of 1000.
        assertEquals(400, ExtraPlan.LOUDNESS_GAIN_MB.values.max())
        assertEquals(600, ExtraPlan.BASS_BOOST_STRENGTH.values.max())
        assertEquals(null, ExtraPlan.of(ContentType.MUSIC, bassBoost = false, loudness = true).loudnessGainMb)
        assertEquals(null, ExtraPlan.of(ContentType.NEWS, bassBoost = true, loudness = false).bassStrength)
    }

    @Test fun `a layer's boost is paid for by lowering everything else`() {
        // On a flat room, Clarity +3 is the peak, so every band outside the
        // presence region drops 3 dB: the price of keeping the peak at 0 dBFS.
        val p = flat()
        val clarity = ToneLayers(clarityDb = EnhancedAudioPrefs.CLARITY_MAX_DB)
        assertEquals(3.0, BandMapping.headroomDb(p, centres, clarity), 1e-9)
        val gains = BandMapping.gainsDb(p, centres, clarity)
        val at1k = gains[centres.indexOfFirst { it >= 1_000.0 }]
        assertEquals(-3.0, at1k, 1e-6)
        assertEquals(0.0, gains.max(), 1e-9)
    }

    @Test fun `stacking layers costs at most the sum of their peaks`() {
        val p = flat()
        val clarity = ToneLayers(clarityDb = EnhancedAudioPrefs.CLARITY_MAX_DB)
        val bass = ToneLayers(lowVolumeBassDb = ToneLayers.MAX_LOW_VOLUME_BASS_DB)
        val both = ToneLayers(clarityDb = EnhancedAudioPrefs.CLARITY_MAX_DB, lowVolumeBassDb = ToneLayers.MAX_LOW_VOLUME_BASS_DB)
        val cost = BandMapping.headroomDb(p, centres, both)
        assertTrue(cost <= BandMapping.headroomDb(p, centres, clarity) + BandMapping.headroomDb(p, centres, bass) + 1e-9)
        // They live in different bands, so together they cost no more than the larger one.
        assertEquals(maxOf(BandMapping.headroomDb(p, centres, clarity), BandMapping.headroomDb(p, centres, bass)), cost, 1e-9)
    }
}
