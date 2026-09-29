package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.corebuilds.eq.ui.CorrectionIndicator
import tv.corebuilds.eq.ui.correctionIndicator

/** The Home screen indicator is a truth table; all eight rows live here. */
class CorrectionIndicatorTest {

    @Test
    fun everyInputCombinationMapsToExactlyOneState() {
        val rows = listOf(
            Triple(false, false, false) to CorrectionIndicator.OFF,
            Triple(false, false, true) to CorrectionIndicator.OFF,
            Triple(false, true, false) to CorrectionIndicator.OFF,
            Triple(false, true, true) to CorrectionIndicator.OFF,
            Triple(true, false, false) to CorrectionIndicator.STANDBY,
            Triple(true, false, true) to CorrectionIndicator.FAULT,
            Triple(true, true, false) to CorrectionIndicator.LIVE,
            Triple(true, true, true) to CorrectionIndicator.FAULT
        )
        for ((inputs, expected) in rows) {
            val (enabled, playing, fault) = inputs
            assertEquals(
                "enabled=$enabled playing=$playing fault=$fault",
                expected,
                correctionIndicator(enabled = enabled, playing = playing, fault = fault)
            )
        }
    }

    @Test
    fun offWinsOverEverythingElse() {
        assertEquals(
            CorrectionIndicator.OFF,
            correctionIndicator(enabled = false, playing = true, fault = true)
        )
    }

    @Test
    fun aFaultIsNeverHiddenByPlayingAudio() {
        // A fault means nothing reached the audio: the badge must not claim
        // LIVE just because something is playing.
        assertEquals(
            CorrectionIndicator.FAULT,
            correctionIndicator(enabled = true, playing = true, fault = true)
        )
    }

    @Test
    fun playingWithoutAFaultIsTheOnlyLiveRow() {
        // The pulse means "audio is flowing through the correction right now"
        // and lives in MainActivity: this table decides LIVE, the screen
        // animates only in it. One row may claim it, and it is this one.
        assertEquals(
            CorrectionIndicator.LIVE,
            correctionIndicator(enabled = true, playing = true, fault = false)
        )
    }
}
