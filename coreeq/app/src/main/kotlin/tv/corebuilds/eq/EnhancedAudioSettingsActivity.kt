package tv.corebuilds.eq

import android.os.Bundle
import android.widget.Button
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.ui.EnhancedAudioPrefs

/**
 * Extra effects: the opt-in layers on top of room correction.
 *
 * Bass boost and loudness are separate platform effects on each corrected
 * session; night mode tightens the DynamicsProcessing limiter. All three
 * are off until switched on here, and each change applies at once. Room
 * correction itself never changes on this screen.
 *
 * Unreachable in 1.2.0 (not in the manifest, no way in), while every one
 * of these effects except night mode ran by default.
 */
class EnhancedAudioSettingsActivity : TvActivity() {

    private lateinit var prefs: EnhancedAudioPrefs
    private lateinit var btnBass: Button
    private lateinit var btnLoudness: Button
    private lateinit var btnNight: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_audio_settings)
        prefs = EnhancedAudioPrefs(this)

        btnBass = findViewById(R.id.btn_extras_bass)
        btnLoudness = findViewById(R.id.btn_extras_loudness)
        btnNight = findViewById(R.id.btn_extras_night)

        btnBass.setOnClickListener {
            prefs.bassBoostEnabled = !prefs.bassBoostEnabled
            changed()
        }
        btnLoudness.setOnClickListener {
            prefs.loudnessEnhancerEnabled = !prefs.loudnessEnhancerEnabled
            changed()
        }
        btnNight.setOnClickListener {
            prefs.nightModeEnabled = !prefs.nightModeEnabled
            changed()
        }
        refresh()
        btnBass.requestFocus()
    }

    private fun changed() {
        refresh()
        if (EqService.running) EqService.send(this, EqService.ACTION_RELOAD_PREFS)
    }

    private fun refresh() {
        btnBass.text = getString(if (prefs.bassBoostEnabled) R.string.extras_bass_on else R.string.extras_bass_off)
        btnLoudness.text = getString(
            if (prefs.loudnessEnhancerEnabled) R.string.extras_loudness_on else R.string.extras_loudness_off
        )
        btnNight.text = getString(if (prefs.nightModeEnabled) R.string.extras_night_on else R.string.extras_night_off)
    }
}
