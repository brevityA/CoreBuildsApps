package tv.corebuilds.eq.apply

import tv.corebuilds.eq.mode.ContentType

/**
 * The optional extras layered on top of room correction, as numbers.
 *
 * Kept free of Android types so the strengths can be unit-tested. A null
 * field means that effect is not created at all. Both extras are off by
 * default since 1.2.1 ([tv.corebuilds.eq.ui.EnhancedAudioPrefs]): 1.2.0
 * switched them on for everyone and changed the sound of every measured
 * room on update.
 */
data class ExtraPlan(
    /** BassBoost strength, 0..1000, or null for no BassBoost. */
    val bassStrength: Int?,
    /** LoudnessEnhancer target gain in millibels, or null for none. */
    val loudnessGainMb: Int?
) {
    val isEmpty: Boolean get() = bassStrength == null && loudnessGainMb == null

    companion object {
        /** Nothing on top of correction: the 1.1.1 sound. */
        val NONE = ExtraPlan(null, null)

        /**
         * Strengths per content type. A zero strength creates no effect,
         * so speech content never carries a BassBoost that does nothing
         * but cost a slot in the effect chain.
         */
        fun of(contentType: ContentType, bassBoost: Boolean, loudness: Boolean): ExtraPlan {
            val bass = if (bassBoost) BASS_BOOST_STRENGTH.getValue(contentType).takeIf { it > 0 } else null
            val gain = if (loudness) LOUDNESS_GAIN_MB.getValue(contentType).takeIf { it > 0 } else null
            return ExtraPlan(bass, gain)
        }

        // BassBoost strength, 0..1000 (1000 = the platform's maximum).
        internal val BASS_BOOST_STRENGTH = mapOf(
            ContentType.MOVIE to 600,
            ContentType.GAMING to 500,
            ContentType.MUSIC to 400,
            ContentType.ANIME to 300,
            ContentType.TV_SHOW to 200,
            ContentType.SITCOM to 150,
            ContentType.DOCUMENTARY to 100,
            ContentType.PODCAST to 0,
            ContentType.NEWS to 0,
            ContentType.GENERAL to 300
        )

        // LoudnessEnhancer target gain in millibels (100 mB = 1 dB).
        internal val LOUDNESS_GAIN_MB = mapOf(
            ContentType.PODCAST to 400,
            ContentType.NEWS to 400,
            ContentType.TV_SHOW to 300,
            ContentType.DOCUMENTARY to 300,
            ContentType.SITCOM to 250,
            ContentType.MOVIE to 200,
            ContentType.GENERAL to 200,
            ContentType.GAMING to 150,
            ContentType.ANIME to 100,
            ContentType.MUSIC to 0
        )
    }
}
