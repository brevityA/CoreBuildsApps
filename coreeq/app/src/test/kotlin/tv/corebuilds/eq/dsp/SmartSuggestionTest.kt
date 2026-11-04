package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.SmartSuggestion.Layers
import tv.corebuilds.eq.mode.ContentType

class SmartSuggestionTest {

    private fun suggest(
        contentType: ContentType,
        volumeDb: Double?,
        activePreset: String? = "flat",
        layers: Layers = Layers()
    ) = SmartSuggestion.suggest(contentType, volumeDb, activePreset, layers)

    @Test
    fun gamingAndMusicSuggestFlatUnlessFlatIsInPlace() {
        assertEquals("flat", suggest(ContentType.GAMING, -25.0, activePreset = "bass-lift")?.presetId)
        assertEquals("flat", suggest(ContentType.MUSIC, -25.0, activePreset = "warm")?.presetId)
        assertNull(suggest(ContentType.GAMING, -25.0, activePreset = "flat"))
        assertNull(suggest(ContentType.MUSIC, -25.0, activePreset = "flat"))
    }

    @Test
    fun quietDialogueContentSuggestsSpeechClarity() {
        val s = suggest(ContentType.MOVIE, -35.0, activePreset = "bass-lift")
        assertNotNull(s)
        assertEquals("speech-clarity", s!!.presetId)
        assertEquals("Quiet movies: lift the consonant band", s.reason)
        assertNull(suggest(ContentType.MOVIE, -35.0, activePreset = "speech-clarity"))
    }

    @Test
    fun quietNonDialogueContentSuggestsLowVolumeLoudness() {
        val s = suggest(ContentType.ANIME, -35.0)
        assertEquals("low-volume-loudness", s?.presetId)
        assertTrue(s!!.reason.startsWith("Quiet listening"))
        assertNull(suggest(ContentType.ANIME, -35.0, activePreset = "low-volume-loudness"))
    }

    @Test
    fun aCustomCurveIsNeverOfferedAPreset() {
        // Bands set by hand match no shipped preset (null): replacing them on a guess
        // would throw the work away, whatever is playing.
        for (type in ContentType.entries) {
            assertNull(type.key, suggest(type, -40.0, activePreset = null))
        }
    }

    @Test
    fun enhancedAudioAlreadyCompensatingQuietListeningWins() {
        // Auto loudness and Low-volume bass already lift quiet listening; the preset
        // would add the same lift a second time.
        assertNull(suggest(ContentType.ANIME, -35.0, layers = Layers(autoLoudness = true)))
        assertNull(suggest(ContentType.GENERAL, -35.0, layers = Layers(lowVolumeBass = true)))
        // Dialogue boost already lifts the speech band, and quiet dialogue content
        // does not fall through to the loudness preset instead.
        assertNull(suggest(ContentType.MOVIE, -35.0, activePreset = "bass-lift", layers = Layers(dialogueBoost = true)))
        // A layer that does a different job does not silence the other suggestion.
        assertEquals(
            "speech-clarity",
            suggest(ContentType.NEWS, -35.0, activePreset = "flat", layers = Layers(autoLoudness = true))?.presetId
        )
    }

    @Test
    fun normalVolumeSuggestsNothingButTheContentRules() {
        for (type in listOf(ContentType.MOVIE, ContentType.TV_SHOW, ContentType.NEWS, ContentType.PODCAST,
            ContentType.DOCUMENTARY, ContentType.ANIME, ContentType.GENERAL, ContentType.SITCOM)) {
            assertNull(type.key, suggest(type, -20.0, activePreset = "bass-lift"))
        }
    }

    @Test
    fun anUnknownVolumeWithdrawsOnlyTheVolumeSuggestions() {
        // Fixed-volume outputs (HDMI-CEC, most soundbars) report no dB.
        assertNull(suggest(ContentType.MOVIE, null, activePreset = "bass-lift"))
        assertNull(suggest(ContentType.ANIME, null))
        assertEquals("flat", suggest(ContentType.GAMING, null, activePreset = "bass-lift")?.presetId)
    }

    @Test
    fun exactlyQuietDbCountsAsQuiet() {
        assertNotNull(suggest(ContentType.ANIME, SmartSuggestion.QUIET_DB))
        assertNull(suggest(ContentType.ANIME, SmartSuggestion.QUIET_DB + 0.1))
    }

    @Test
    fun everySuggestedPresetShips() {
        val shipped = ManualEq.BUILT_IN_PRESETS.map { it.id }.toSet()
        val suggested = ContentType.entries.flatMap { type ->
            listOf(-40.0, -20.0, null).mapNotNull { suggest(type, it, activePreset = "bass-lift")?.presetId }
        }.toSet()
        assertTrue(suggested.toString(), suggested.isNotEmpty() && shipped.containsAll(suggested))
    }
}
