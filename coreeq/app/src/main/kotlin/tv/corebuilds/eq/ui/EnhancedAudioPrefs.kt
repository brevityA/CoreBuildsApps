package tv.corebuilds.eq.ui

import android.content.Context
import android.content.SharedPreferences
import tv.corebuilds.eq.apply.LimiterSettings
import tv.corebuilds.eq.apply.ToneLayers
import tv.corebuilds.eq.dsp.NightMode

/**
 * The Extra effects screen's switches. Every one is off by default.
 *
 * 1.2.0 shipped BassBoost, LoudnessEnhancer and a second DynamicsProcessing
 * stage switched on for everyone, with no screen to turn them off, so the
 * update changed the sound of every measured room. 1.2.1 reads new keys
 * (`*_v121`): nothing 1.2.0 stored can switch an extra back on.
 *
 * 1.3.0 adds Dialogue boost and Low-volume bass ([tv.corebuilds.eq.apply.ToneLayers]),
 * also off by default, and the reference volume Low-volume bass measures from.
 */
class EnhancedAudioPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var bassBoostEnabled: Boolean
        get() = prefs.getBoolean(KEY_BASS_BOOST, false)
        set(value) = prefs.edit().putBoolean(KEY_BASS_BOOST, value).apply()

    var loudnessEnhancerEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOUDNESS, false)
        set(value) = prefs.edit().putBoolean(KEY_LOUDNESS, value).apply()

    /**
     * Night mode level; it retunes the DynamicsProcessing limiter, so it
     * needs the DP engine. Through 1.13.2 it was a switch: a stored "on" reads as
     * [NightMode.Level.STRONG], which has that switch's exact settings.
     */
    var nightLevel: NightMode.Level
        get() = NightMode.Level.fromKey(prefs.getString(KEY_NIGHT_LEVEL, null))
            ?: if (prefs.getBoolean(KEY_NIGHT_MODE, false)) NightMode.Level.STRONG else NightMode.Level.OFF
        set(value) = prefs.edit().putString(KEY_NIGHT_LEVEL, value.key).apply()

    /** Any night level but Off. */
    val nightModeEnabled: Boolean get() = nightLevel != NightMode.Level.OFF

    /**
     * Clarity: the speech-band lift in dB, 0 to [CLARITY_MAX_DB] in
     * [CLARITY_STEP_DB] steps. It replaces the Dialogue boost switch: a stored
     * "on" reads as +2 dB, which is what the switch applied.
     */
    var clarityDb: Double
        get() {
            val stored = prefs.getFloat(KEY_CLARITY, Float.NaN)
            if (stored.isFinite()) return clampClarity(stored.toDouble())
            return if (prefs.getBoolean(KEY_DIALOGUE, false)) ToneLayers.DIALOGUE_DB else 0.0
        }
        set(value) = prefs.edit().putFloat(KEY_CLARITY, clampClarity(value).toFloat()).apply()

    /** Night mode and Clarity back to their defaults; correction and profiles are untouched. */
    fun resetListening() {
        nightLevel = NightMode.Level.OFF
        clarityDb = 0.0
    }

    var lowVolumeBassEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOW_VOLUME_BASS, false)
        set(value) = prefs.edit().putBoolean(KEY_LOW_VOLUME_BASS, value).apply()

    /**
     * Full ISO 226 loudness compensation at low volume (1.11.0): restores the
     * bass and treble the ear loses when the output is turned down, mapped to
     * the ten manual EQ bands. Off until switched on; low-volume bass and
     * auto loudness are mutually exclusive — auto loudness wins.
     */
    var autoLoudnessEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOUDNESS, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LOUDNESS, value).apply()

    /**
     * The everyday volume of one output, in dB from that output's own volume
     * curve, that Low-volume bass measures from: nothing is added at or above
     * it. Null until recorded ([tv.corebuilds.eq.apply.LowVolumeBass]).
     */
    fun lowVolumeReferenceDb(route: String): Double? =
        prefs.getFloat(KEY_LOW_VOLUME_REFERENCE + route, Float.NaN).toDouble().takeIf { it.isFinite() }

    fun setLowVolumeReferenceDb(route: String, db: Double?) {
        prefs.edit().putFloat(KEY_LOW_VOLUME_REFERENCE + route, db?.toFloat() ?: Float.NaN).apply()
    }

    /** Switching Low-volume bass on starts afresh: every output records a new reference. */
    fun clearLowVolumeReferences() {
        val editor = prefs.edit()
        for (key in prefs.all.keys) if (key.startsWith(KEY_LOW_VOLUME_REFERENCE)) editor.remove(key)
        editor.apply()
    }

    /** Stereo speakers (1.10.0): 5.1 folded to two channels ([tv.corebuilds.eq.apply.ToneLayers.stereoDownmix]). */
    var stereoSpeakersEnabled: Boolean
        get() = prefs.getBoolean(KEY_STEREO_SPEAKERS, false)
        set(value) = prefs.edit().putBoolean(KEY_STEREO_SPEAKERS, value).apply()

    /**
     * The Surround sound mode Stereo speakers replaced with Never, to put back
     * when it is switched off; null when Core EQ changed nothing.
     */
    var surroundRestoreMode: Int?
        get() = prefs.getInt(KEY_SURROUND_RESTORE, -1).takeIf { it >= 0 }
        set(value) = prefs.edit().putInt(KEY_SURROUND_RESTORE, value ?: -1).apply()

    /**
     * Stereo speakers is on but Surround sound could not be changed yet (the
     * permission was still to be given). Cleared once it is changed, or the
     * switch goes off, so Core EQ asks once and never fights a later choice.
     */
    var surroundPending: Boolean
        get() = prefs.getBoolean(KEY_SURROUND_PENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_SURROUND_PENDING, value).apply()

    /** The limiter correction should run with: protection-only, or the night level's. */
    fun limiter(): LimiterSettings = NightMode.limiter(nightLevel)

    companion object {
        const val PREFS_NAME = "enhanced_audio_prefs"
        private const val KEY_BASS_BOOST = "bass_boost_v121"
        private const val KEY_LOUDNESS = "loudness_v121"
        private const val KEY_NIGHT_MODE = "night_mode_v121"
        // Storage identifiers, not release numbers. These keys were introduced
        // together on this development line and are named with a "v1140" suffix,
        // the same convention as the older keys (night_mode_v121,
        // dialogue_boost_v130). The suffix does not fix the next release's
        // number, but once any build that writes these keys is released the names
        // must never change, or upgrading users lose the stored values. The old
        // keys are only ever read as a fallback, so a downgrade still finds them.
        private const val KEY_NIGHT_LEVEL = "night_level_v1140"
        private const val KEY_DIALOGUE = "dialogue_boost_v130"
        private const val KEY_CLARITY = "clarity_db_v1140"

        const val CLARITY_MAX_DB = 3.0
        const val CLARITY_STEP_DB = 1.0

        /** Whole steps from 0 to [CLARITY_MAX_DB]; anything else stored snaps into range. */
        fun clampClarity(db: Double): Double =
            if (!db.isFinite()) 0.0
            else (Math.round(db / CLARITY_STEP_DB) * CLARITY_STEP_DB).coerceIn(0.0, CLARITY_MAX_DB)
        private const val KEY_LOW_VOLUME_BASS = "low_volume_bass_v130"
        private const val KEY_AUTO_LOUDNESS = "auto_loudness_v1110"
        private const val KEY_STEREO_SPEAKERS = "stereo_speakers_v1100"
        private const val KEY_SURROUND_RESTORE = "surround_restore_mode_v1100"
        private const val KEY_SURROUND_PENDING = "surround_pending_v1100"
        /** Followed by the output's route key: one reference per output. */
        private const val KEY_LOW_VOLUME_REFERENCE = "low_volume_reference_db_v130:"

        /** The switch of 1.13.2 and earlier: on is [NightMode.Level.STRONG]. */
        fun limiterFor(nightMode: Boolean): LimiterSettings =
            NightMode.limiter(if (nightMode) NightMode.Level.STRONG else NightMode.Level.OFF)
    }
}
