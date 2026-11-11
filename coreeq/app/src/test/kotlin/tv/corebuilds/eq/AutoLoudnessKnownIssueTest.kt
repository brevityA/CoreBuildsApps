package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.DpBandLayout
import tv.corebuilds.eq.apply.ToneLayers
import tv.corebuilds.eq.dsp.LoudnessCompensation
import tv.corebuilds.eq.dsp.NightMode
import tv.corebuilds.eq.ui.HomeSummary
import kotlin.math.abs

/**
 * The known Auto loudness defect, held as it is (review of #474).
 *
 * `ToneLayers.responseDb` looks up the nearest manual band by comparing a band
 * index with a frequency, so every frequency receives the 8 kHz band's gain.
 * The headroom shift removes a uniform positive gain, so the configured bands
 * are identical with Auto loudness on or off. This batch discloses that and
 * does not change the sound; the fix, with explicit activation for anyone who
 * has it on, is roadmap milestone AL. When that lands, this test must change
 * with it, together with the disclosure.
 */
class AutoLoudnessKnownIssueTest {

    private val dp = DpBandLayout.specs().map { it.centerHz }

    @Test fun `auto loudness currently configures exactly the bands it would with it off`() {
        val gains = LoudnessCompensation.bandGains(20.0, 1.0)
        assertTrue("the intended bass lift is real: ${gains[0]}", gains[0] > 5.0)
        val p = LegacyGoldenFixtures.roomWithManual.copy(manualFilters = emptyList())
        for (side in BandMapping.AbSide.entries) {
            val off = BandMapping.gainsDb(p, dp, ToneLayers(), side)
            val on = BandMapping.gainsDb(p, dp, ToneLayers(loudnessBandGains = gains), side)
            val worst = dp.indices.maxOf { abs(on[it] - off[it]) }
            assertTrue("$side differs by $worst dB", worst <= 1e-9)
        }
        // Every frequency gets the last band's gain: the defect itself.
        val layers = ToneLayers(loudnessBandGains = gains)
        for (hz in listOf(40.0, 250.0, 1_000.0, 8_000.0)) assertEquals(gains.last(), layers.responseDb(p, hz), 1e-12)
    }

    @Test fun `Home says auto loudness is requested but has no effect yet`() {
        val f = HomeSummary.Facts(
            correctionOn = true, serviceRunning = true, measurementPaused = false,
            anyProfile = true, profileForOutput = true, outputKnown = true, outputLabel = "TV speakers",
            appliedEngine = "DynamicsProcessing", playing = false, faultMessage = null, bypass = false,
            night = NightMode.Level.OFF, clarityDb = 0.0, stereoSpeakers = false, lowVolumeBass = false,
            autoLoudness = true, bassBoost = false, loudnessEnhancer = false
        )
        assertEquals("Listening: auto loudness (no effect in this version).", HomeSummary.listeningLine(f))
    }
}
