package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentType

class SmartSuggestionTest {

    private fun suggest(
        contentType: ContentType,
        volumeDb: Double?,
        currentMode: ContentMode = ContentMode.EVERYDAY,
        activePreset: String? = null
    ) = SmartSuggestion.suggest(contentType, volumeDb, currentMode, activePreset)

    @Test
    fun gamingSuggestsFlatUnlessFlatIsAlreadyActive() {
        val s = suggest(ContentType.GAMING, -25.0, activePreset = "bass-lift")
        assertNotNull(s)
        assertEquals("flat", s!!.presetId)
        assertEquals("🎮", s.icon)

        // Flat already active: nothing to suggest.
        assertNull(suggest(ContentType.GAMING, -25.0, activePreset = "flat"))
    }

    @Test
    fun musicSuggestsFlatUnlessFlatIsAlreadyActive() {
        val s = suggest(ContentType.MUSIC, -25.0, activePreset = "warm")
        assertNotNull(s)
        assertEquals("flat", s!!.presetId)
        assertEquals("🎵", s.icon)

        assertNull(suggest(ContentType.MUSIC, -25.0, activePreset = "flat"))
    }

    @Test
    fun quietDialogueContentSuggestsSpeechClarity() {
        val s = suggest(ContentType.MOVIE, -35.0, activePreset = "bass-lift")
        assertNotNull(s)
        assertEquals("speech-clarity", s!!.presetId)
        assertEquals("🗣️", s.icon)
        assertTrue(s.reason.contains("Quiet"))

        // Already active: nothing to suggest.
        assertNull(suggest(ContentType.MOVIE, -35.0, activePreset = "speech-clarity"))
    }

    @Test
    fun quietNonDialogueContentSuggestsLowVolumeLoudness() {
        val s = suggest(ContentType.ANIME, -35.0, activePreset = "flat")
        assertNotNull(s)
        assertEquals("low-volume-loudness", s!!.presetId)
        assertEquals("🔉", s.icon)
        assertTrue(s.reason.contains("Quiet listening"))

        assertNull(suggest(ContentType.ANIME, -35.0, activePreset = "low-volume-loudness"))
    }

    @Test
    fun normalVolumeDialogueContentSuggestsNothing() {
        // Normal-volume movie: the measured correction + target already carry
        // presence, no preset suggestion needed.
        assertNull(suggest(ContentType.MOVIE, -15.0))
        assertNull(suggest(ContentType.TV_SHOW, -20.0))
        assertNull(suggest(ContentType.NEWS, -25.0))
        assertNull(suggest(ContentType.PODCAST, -25.0))
        assertNull(suggest(ContentType.DOCUMENTARY, -25.0))
    }

    @Test
    fun normalVolumeNonDialogueContentSuggestsNothing() {
        assertNull(suggest(ContentType.ANIME, -25.0))
        assertNull(suggest(ContentType.GENERAL, -25.0))
        assertNull(suggest(ContentType.SITCOM, -25.0))
    }

    @Test
    fun fixedVolumeOutputsSuggestNothing() {
        // volumeDb is null for fixed-volume routes (HDMI-CEC, most soundbars):
        // no low-volume intelligence is possible. Gaming still suggests Flat
        // (content-based, not volume-based), so it is excluded here.
        assertNull(suggest(ContentType.MOVIE, null, activePreset = "bass-lift"))
        assertNull(suggest(ContentType.ANIME, null, activePreset = "flat"))
    }

    @Test
    fun thresholdBoundaryIsInclusiveAtQuietDb() {
        // Exactly QUIET_DB (-30) counts as low volume.
        assertNotNull(suggest(ContentType.ANIME, -30.0, activePreset = "flat"))
        // Just above is not low.
        assertNull(suggest(ContentType.ANIME, -29.9, activePreset = "flat"))
    }

}
