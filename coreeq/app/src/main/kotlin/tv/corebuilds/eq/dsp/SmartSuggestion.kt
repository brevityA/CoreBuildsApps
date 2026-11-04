package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.mode.ContentType

/**
 * Volume-aware preset suggestions (1.11.0; layer-aware and first shown on screen in 1.12.0).
 *
 * Core EQ ships a dozen manual-EQ presets plus Flat, but nothing tells a viewer which
 * one fits what is playing and how loud it is. This recommends one from the content
 * type, the Android-reported volume and the preset already in place. It never applies
 * anything: Manual EQ shows it as one row, and the person decides.
 *
 * It stays quiet rather than repeat or undo something:
 * - **A custom curve is never offered a preset.** Bands someone set by hand match no
 *   preset; replacing them on a guess would throw their work away.
 * - **Enhanced audio already doing the job wins.** Auto loudness and Low-volume bass
 *   compensate quiet listening on their own, so the loudness preset would add the same
 *   lift twice; Dialogue boost already lifts the speech band Speech clarity lifts.
 *
 * Pure Kotlin, unit-tested without Android.
 */
object SmartSuggestion {

    /** One suggestion: the preset to try, its name, and why, in a line. */
    data class Suggestion(
        val presetId: String,
        val name: String,
        val reason: String
    )

    /** The Enhanced audio layers that already do what a preset would. */
    data class Layers(
        val autoLoudness: Boolean = false,
        val lowVolumeBass: Boolean = false,
        val dialogueBoost: Boolean = false
    ) {
        val compensatesQuietListening: Boolean get() = autoLoudness || lowVolumeBass
    }

    /** At or below this media volume, dB from the output's own curve, listening is quiet. */
    const val QUIET_DB = -30.0

    /**
     * Suggest a manual-EQ preset for the current situation, or null when none is
     * clearly better than what is in place.
     *
     * @param contentType what the Content type screen resolved for the playing app
     * @param volumeDb Android's media volume in dB, or null for fixed-volume outputs
     *   (HDMI-CEC, most soundbars) where it cannot say
     * @param activePreset the id of the preset the edited bands match, or null when
     *   they match none (a custom curve)
     * @param layers the Enhanced audio layers switched on
     */
    fun suggest(
        contentType: ContentType,
        volumeDb: Double?,
        activePreset: String?,
        layers: Layers = Layers()
    ): Suggestion? {
        if (activePreset == null) return null
        val quiet = volumeDb != null && volumeDb <= QUIET_DB
        val pick = when {
            // Gaming: leave the game's own mix alone.
            contentType == ContentType.GAMING ->
                Suggestion("flat", "Flat", "Gaming: leave the game's own mix alone")
            // Music: the track's own mix is the reference.
            contentType == ContentType.MUSIC ->
                Suggestion("flat", "Flat", "Music: play the mix as it was mixed")
            // Quiet dialogue-first content: lift the 1–6 kHz consonant band, not the bass.
            quiet && contentType in DIALOGUE_FIRST && !layers.dialogueBoost ->
                Suggestion(
                    "speech-clarity", "Speech clarity",
                    "Quiet ${contentType.title.lowercase()}: lift the consonant band"
                )
            // Quiet anything else: give back the bass and treble the ear loses.
            quiet && contentType !in DIALOGUE_FIRST && !layers.compensatesQuietListening ->
                Suggestion(
                    "low-volume-loudness", "Low-volume loudness",
                    "Quiet listening: restore the bass and treble the ear loses"
                )
            else -> null
        }
        return pick?.takeIf { it.presetId != activePreset }
    }

    /** Content types where speech intelligibility is the main complaint. */
    private val DIALOGUE_FIRST = setOf(
        ContentType.MOVIE, ContentType.TV_SHOW, ContentType.NEWS,
        ContentType.PODCAST, ContentType.DOCUMENTARY
    )
}
