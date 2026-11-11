package tv.corebuilds.eq.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.NightMode
import tv.corebuilds.eq.ui.HomeSummary.State

class HomeSummaryTest {

    private val applied = HomeSummary.Facts(
        correctionOn = true, serviceRunning = true, measurementPaused = false,
        anyProfile = true, profileForOutput = true, outputKnown = true, outputLabel = "TV speakers",
        appliedEngine = "DynamicsProcessing", playing = true, faultMessage = null, bypass = false,
        night = NightMode.Level.OFF, clarityDb = 0.0, stereoSpeakers = false, lowVolumeBass = false,
        autoLoudness = false, bassBoost = false, loudnessEnhancer = false
    )

    @Test fun `every state is reached by the fact that defines it`() {
        assertEquals(State.APPLIED, HomeSummary.stateOf(applied))
        assertEquals(State.OFF, HomeSummary.stateOf(applied.copy(correctionOn = false)))
        assertEquals(State.MEASURING, HomeSummary.stateOf(applied.copy(measurementPaused = true)))
        assertEquals(State.NO_PROFILE, HomeSummary.stateOf(applied.copy(anyProfile = false, profileForOutput = false)))
        assertEquals(State.NOT_RUNNING, HomeSummary.stateOf(applied.copy(serviceRunning = false)))
        assertEquals(State.FAULT, HomeSummary.stateOf(applied.copy(faultMessage = "Effect refused")))
        assertEquals(State.OUTPUT_UNKNOWN, HomeSummary.stateOf(applied.copy(profileForOutput = false, outputKnown = false)))
        assertEquals(State.NO_PROFILE_FOR_OUTPUT, HomeSummary.stateOf(applied.copy(profileForOutput = false)))
        assertEquals(State.PENDING, HomeSummary.stateOf(applied.copy(appliedEngine = null)))
    }

    @Test fun `a stale measuring flag cannot claim a pause once the service is gone`() {
        assertEquals(State.NOT_RUNNING, HomeSummary.stateOf(applied.copy(measurementPaused = true, serviceRunning = false)))
    }

    @Test fun `applied never claims the stream passes through it`() {
        val line = HomeSummary.of(applied).applied
        assertTrue(line.startsWith("Applied by DynamicsProcessing · media is playing"))
        assertTrue(line.contains("does not confirm"))
        assertTrue(HomeSummary.of(applied.copy(playing = false)).applied.contains("nothing is playing"))
        assertTrue(HomeSummary.of(applied.copy(bypass = true)).applied.contains("bypass side"))
    }

    @Test fun `unknowns are said as unknown, faults keep their reason`() {
        assertTrue(HomeSummary.of(applied.copy(profileForOutput = false, outputKnown = false)).applied.startsWith("Unknown:"))
        assertTrue(HomeSummary.of(applied.copy(serviceRunning = false)).applied.startsWith("Unknown:"))
        assertEquals("Not applied: Effect refused", HomeSummary.of(applied.copy(faultMessage = "Effect refused")).applied)
        assertTrue(HomeSummary.of(applied.copy(profileForOutput = false)).applied.contains("TV speakers"))
    }

    @Test fun `listening line says requests, and when night cannot run`() {
        assertEquals("Listening: no extras (Extra effects to add some).", HomeSummary.listeningLine(applied))
        val busy = applied.copy(
            night = NightMode.Level.LIGHT, clarityDb = 2.0, stereoSpeakers = true,
            lowVolumeBass = true, autoLoudness = true, bassBoost = true, loudnessEnhancer = true
        )
        assertEquals(
            "Listening: night light · clarity +2 dB · stereo speakers · auto loudness · bass boost (Android effect) · loudness (Android effect).",
            HomeSummary.listeningLine(busy)
        )
        val onEqualizer = applied.copy(night = NightMode.Level.STRONG, appliedEngine = "Equalizer")
        assertEquals("Listening: night strong (not active on Equalizer).", HomeSummary.listeningLine(onEqualizer))
        // With no engine held yet, nothing is claimed either way.
        assertFalse(HomeSummary.listeningLine(applied.copy(night = NightMode.Level.STRONG, appliedEngine = null)).contains("not active"))
    }
}
