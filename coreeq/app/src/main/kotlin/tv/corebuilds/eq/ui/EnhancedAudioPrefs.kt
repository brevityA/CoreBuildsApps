package tv.corebuilds.eq.ui

import android.content.Context
import android.content.SharedPreferences
import tv.corebuilds.eq.apply.LimiterSettings
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

    /** Night mode tightens the DynamicsProcessing limiter; it needs the DP engine. */
    var nightModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT_MODE, value).apply()

    var dialogueBoostEnabled: Boolean
        get() = prefs.getBoolean(KEY_DIALOGUE, false)
        set(value) = prefs.edit().putBoolean(KEY_DIALOGUE, value).apply()

    var lowVolumeBassEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOW_VOLUME_BASS, false)
        set(value) = prefs.edit().putBoolean(KEY_LOW_VOLUME_BASS, value).apply()

    /**
     * The everyday volume, in dB from Android's volume curve, that Low-volume
     * bass measures from: nothing is added at or above it. Null until the
     * switch is first turned on, which records the volume of that moment.
     */
    var lowVolumeReferenceDb: Double?
        get() = prefs.getFloat(KEY_LOW_VOLUME_REFERENCE, Float.NaN).toDouble().takeIf { it.isFinite() }
        set(value) = prefs.edit().putFloat(KEY_LOW_VOLUME_REFERENCE, value?.toFloat() ?: Float.NaN).apply()

    /** The limiter correction should run with: protection-only, or night mode's. */
    fun limiter(): LimiterSettings = limiterFor(nightModeEnabled)

    companion object {
        const val PREFS_NAME = "enhanced_audio_prefs"
        private const val KEY_BASS_BOOST = "bass_boost_v121"
        private const val KEY_LOUDNESS = "loudness_v121"
        private const val KEY_NIGHT_MODE = "night_mode_v121"
        private const val KEY_DIALOGUE = "dialogue_boost_v130"
        private const val KEY_LOW_VOLUME_BASS = "low_volume_bass_v130"
        private const val KEY_LOW_VOLUME_REFERENCE = "low_volume_reference_db_v130"

        fun limiterFor(nightMode: Boolean): LimiterSettings =
            if (nightMode) {
                LimiterSettings(
                    attackMs = NightMode.NIGHT_LIMITER_ATTACK_MS,
                    releaseMs = NightMode.NIGHT_LIMITER_RELEASE_MS,
                    ratio = NightMode.NIGHT_LIMITER_RATIO,
                    thresholdDb = NightMode.NIGHT_LIMITER_THRESHOLD_DB
                )
            } else {
                LimiterSettings()
            }
    }
}
