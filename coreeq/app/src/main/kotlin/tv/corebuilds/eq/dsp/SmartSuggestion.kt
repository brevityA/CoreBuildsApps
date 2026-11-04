package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentType

/**
 * Volume-aware smart preset suggestions (1.11.0).
 *
 * Core EQ ships ten manual-EQ presets plus Flat, but nothing tells a viewer
 * which one fits what is playing and how loud it is. This object recommends
 * one from the current content type, the Android-reported volume, and the
 * preset already active. It never applies anything: it returns a
 * [Suggestion] the UI can show as a dismissible chip, and the user decides.
 *
 * Pure Kotlin, unit-tested without Android.
 */
object SmartSuggestion {

    /** One suggestion: the preset to try, why, and an icon for the chip. */
    data class Suggestion(
        val presetId: String,
        val name: String,
        val reason: String,
        val icon: String
    )

    /** Volume bands, in dB below full scale from [tv.corebuilds.eq.apply.VolumeLevel]. */
    private const val LOUD_DB = -20.0   // at or above: normal listening
    private const val QUIET_DB = -30.0  // at or below: low volume

    /**
     * Suggest a manual-EQ preset for the current situation, or null when none
     * is clearly better than what is already active.
     *
     * @param contentType what [tv.corebuilds.eq.mode.ContentTypeRegistry] resolved
     * @param volumeDb Android's media volume in dB FS, or null for fixed-volume
     *   outputs (HDMI-CEC, most soundbars) where volume is not controllable here
     * @param currentMode the active [ContentMode]
     * @param activePreset the id of the manual preset already applied, or null
     */
    fun suggest(
        contentType: ContentType,
        volumeDb: Double?,
        currentMode: ContentMode,
        activePreset: String?
    ): Suggestion? {
        val low = volumeDb != null && volumeDb <= QUIET_DB

        when {
            // Gaming: do not process. Flat keeps the game's own mix untouched
            // and adds no latency. Only suggest if something else is active.
            contentType == ContentType.GAMING -> {
                if (activePreset != "flat") {
                    return Suggestion(
                        "flat", "Flat",
                        "Gaming: leave the mix alone, no added processing",
                        "🎮"
                    )
                }
                return null
            }

            // Music: the track's own mix is the reference. Flat unless the
            // viewer has already chosen something.
            contentType == ContentType.MUSIC -> {
                if (activePreset != "flat") {
                    return Suggestion(
                        "flat", "Flat",
                        "Music: play the mix as the artist mixed it",
                        "🎵"
                    )
                }
                return null
            }

            // Dialogue-first content at low volume: speech clarity lifts the
            // 1–6 kHz consonant band without touching the bass.
            low && contentType in dialogueFirst -> {
                if (activePreset != "speech-clarity") {
                    return Suggestion(
                        "speech-clarity", "Speech clarity",
                        "Quiet ${contentType.title.lowercase()}: lift the consonant band",
                        "🗣️"
                    )
                }
                return null
            }

            // Dialogue-first content at normal volume: no suggestion; the
            // measured correction and target already carry presence.
            contentType in dialogueFirst -> return null

            // Low volume + anything else: restore the bass and treble the ear
            // loses at quiet listening, without changing the level.
            low -> {
                if (activePreset != "low-volume-loudness") {
                    return Suggestion(
                        "low-volume-loudness", "Low-volume loudness",
                        "Quiet listening: restore the bass and treble the ear loses",
                        "🔉"
                    )
                }
                return null
            }

            else -> null
        }

        // Unreachable: every branch returns or produces null, but the
        // compiler needs an explicit return after the when expression.
        return null
    }

    /** Content types where speech intelligibility is the main complaint. */
    private val dialogueFirst = setOf(
        ContentType.MOVIE, ContentType.TV_SHOW, ContentType.NEWS,
        ContentType.PODCAST, ContentType.DOCUMENTARY
    )
}
