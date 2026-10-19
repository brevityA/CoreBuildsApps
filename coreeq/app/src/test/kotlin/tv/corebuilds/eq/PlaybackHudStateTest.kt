package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.corebuilds.eq.apply.PlaybackHudState
import tv.corebuilds.eq.apply.playbackHudState

class PlaybackHudStateTest {
    @Test
    fun `output mix reports playback with the coverage caveat`() {
        assertEquals(
            PlaybackHudState.OUTPUT_MIX_REPORTED,
            playbackHudState(
                enabled = true,
                correctionEnabled = true,
                healthy = true,
                playbackReported = true,
                outputMixConfigured = true,
                sessionEffectConfigured = false
            )
        )
    }

    @Test
    fun `session effects never imply that the reported player matches`() {
        assertEquals(
            PlaybackHudState.SESSION_MATCH_UNKNOWN,
            playbackHudState(
                enabled = true,
                correctionEnabled = true,
                healthy = true,
                playbackReported = true,
                outputMixConfigured = false,
                sessionEffectConfigured = true
            )
        )
    }

    @Test
    fun `effect configuration without reported playback is idle`() {
        assertEquals(
            PlaybackHudState.IDLE,
            playbackHudState(
                enabled = true,
                correctionEnabled = true,
                healthy = true,
                playbackReported = false,
                outputMixConfigured = true,
                sessionEffectConfigured = false
            )
        )
    }

    @Test
    fun `off unhealthy or unconfigured states are hidden`() {
        val cases = listOf(
            Triple(false, true, true),
            Triple(true, false, true),
            Triple(true, true, false)
        )
        for ((enabled, correctionEnabled, healthy) in cases) {
            assertEquals(
                PlaybackHudState.HIDDEN,
                playbackHudState(
                    enabled = enabled,
                    correctionEnabled = correctionEnabled,
                    healthy = healthy,
                    playbackReported = true,
                    outputMixConfigured = true,
                    sessionEffectConfigured = false
                )
            )
        }
        assertEquals(
            PlaybackHudState.HIDDEN,
            playbackHudState(
                enabled = true,
                correctionEnabled = true,
                healthy = true,
                playbackReported = true,
                outputMixConfigured = false,
                sessionEffectConfigured = false
            )
        )
    }

    @Test
    fun `output mix status takes precedence if both effect paths exist`() {
        assertEquals(
            PlaybackHudState.OUTPUT_MIX_REPORTED,
            playbackHudState(
                enabled = true,
                correctionEnabled = true,
                healthy = true,
                playbackReported = true,
                outputMixConfigured = true,
                sessionEffectConfigured = true
            )
        )
    }
}
