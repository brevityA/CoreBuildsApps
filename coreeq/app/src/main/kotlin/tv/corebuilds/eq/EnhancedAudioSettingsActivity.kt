package tv.corebuilds.eq

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.ToneLayers
import tv.corebuilds.eq.apply.VolumeLevel
import tv.corebuilds.eq.ui.EnhancedAudioPrefs

/**
 * Extra effects: the opt-in layers on top of room correction.
 *
 * Dialogue boost and Low-volume bass (1.3.0) are tone layers summed into the
 * correction itself ([ToneLayers]); bass boost and the loudness enhancer are
 * separate platform effects on each corrected session; night mode tightens
 * the DynamicsProcessing limiter. All are off until switched on here, and
 * each change applies at once. Room correction itself never changes on this
 * screen.
 *
 * Unreachable in 1.2.0 (not in the manifest, no way in), while every one
 * of these effects except night mode ran by default.
 */
class EnhancedAudioSettingsActivity : TvActivity() {

    private lateinit var prefs: EnhancedAudioPrefs
    private lateinit var btnDialogue: Button
    private lateinit var btnLowBass: Button
    private lateinit var textLowBassNote: TextView
    private lateinit var btnBass: Button
    private lateinit var btnLoudness: Button
    private lateinit var btnNight: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_audio_settings)
        prefs = EnhancedAudioPrefs(this)

        btnDialogue = findViewById(R.id.btn_extras_dialogue)
        btnLowBass = findViewById(R.id.btn_extras_low_bass)
        textLowBassNote = findViewById(R.id.text_extras_low_bass_note)
        btnBass = findViewById(R.id.btn_extras_bass)
        btnLoudness = findViewById(R.id.btn_extras_loudness)
        btnNight = findViewById(R.id.btn_extras_night)

        btnDialogue.setOnClickListener {
            prefs.dialogueBoostEnabled = !prefs.dialogueBoostEnabled
            changed()
        }
        btnLowBass.setOnClickListener {
            val on = !prefs.lowVolumeBassEnabled
            // Turning it on records the volume of this moment as the reference:
            // the viewer's everyday level, below which bass is restored.
            if (on) prefs.lowVolumeReferenceDb = VolumeLevel.mediaDb(this)
            prefs.lowVolumeBassEnabled = on
            changed()
        }
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
        btnDialogue.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refresh() // the volume may have moved while this screen was away
    }

    private fun changed() {
        refresh()
        if (EqService.running) EqService.send(this, EqService.ACTION_RELOAD_PREFS)
    }

    private fun refresh() {
        btnDialogue.text = getString(
            if (prefs.dialogueBoostEnabled) R.string.extras_dialogue_on else R.string.extras_dialogue_off
        )
        btnLowBass.text = getString(
            if (prefs.lowVolumeBassEnabled) R.string.extras_low_bass_on else R.string.extras_low_bass_off
        )
        textLowBassNote.text = lowBassNote()
        btnBass.text = getString(if (prefs.bassBoostEnabled) R.string.extras_bass_on else R.string.extras_bass_off)
        btnLoudness.text = getString(
            if (prefs.loudnessEnhancerEnabled) R.string.extras_loudness_on else R.string.extras_loudness_off
        )
        btnNight.text = getString(if (prefs.nightModeEnabled) R.string.extras_night_on else R.string.extras_night_off)
    }

    /** What the switch does, or, while on, where the reference sits and what it adds right now. */
    private fun lowBassNote(): String {
        if (!prefs.lowVolumeBassEnabled) return getString(R.string.extras_low_bass_note)
        val now = VolumeLevel.mediaDb(this)
        // Switched on while Android reported no volume: the first volume it
        // does report becomes the reference, rather than leaving it unset.
        if (prefs.lowVolumeReferenceDb == null && now != null) {
            prefs.lowVolumeReferenceDb = now
            if (EqService.running) EqService.send(this, EqService.ACTION_RELOAD_PREFS)
        }
        val reference = prefs.lowVolumeReferenceDb
        if (reference == null || now == null) return getString(R.string.extras_low_bass_unknown)
        val lift = ToneLayers.lowVolumeBassDb(reference - now)
        val effect = if (lift > 0.0) getString(R.string.extras_low_bass_lift, lift) else getString(R.string.extras_low_bass_flat)
        return getString(R.string.extras_low_bass_status, reference, now, effect)
    }
}
