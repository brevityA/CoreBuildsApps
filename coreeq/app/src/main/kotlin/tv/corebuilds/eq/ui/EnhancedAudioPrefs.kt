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

    /** The limiter correction should run with: protection-only, or night mode's. */
    fun limiter(): LimiterSettings = limiterFor(nightModeEnabled)

    companion object {
        const val PREFS_NAME = "enhanced_audio_prefs"
        private const val KEY_BASS_BOOST = "bass_boost_v121"
        private const val KEY_LOUDNESS = "loudness_v121"
        private const val KEY_NIGHT_MODE = "night_mode_v121"

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
