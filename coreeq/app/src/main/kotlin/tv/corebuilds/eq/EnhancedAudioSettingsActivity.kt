package tv.corebuilds.eq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.LowVolumeBass
import tv.corebuilds.eq.ui.EnhancedAudioPrefs
import tv.corebuilds.eq.ui.showSwitch

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

    // While this screen is up, the Low-volume bass line follows the volume
    // keys the same way the service does (Settings.System plus the
    // system's volume broadcast), instead of showing the volume at open.
    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

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
            // Turning it on starts afresh: this output's volume right now
            // becomes its reference (recorded by the refresh below), and every
            // other output records its own the first time it is used.
            if (on) prefs.clearLowVolumeReferences()
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

    override fun onStart() {
        super.onStart()
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
        ContextCompat.registerReceiver(
            this, volumeReceiver, IntentFilter(VOLUME_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        refresh() // the volume may have moved while this screen was away
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(volumeObserver)
        unregisterReceiver(volumeReceiver)
        super.onStop()
    }

    private fun changed() {
        refresh()
        if (EqService.running) EqService.send(this, EqService.ACTION_RELOAD_PREFS)
    }

    private fun refresh() {
        btnDialogue.showSwitch(prefs.dialogueBoostEnabled, R.string.extras_dialogue_on, R.string.extras_dialogue_off)
        btnLowBass.showSwitch(prefs.lowVolumeBassEnabled, R.string.extras_low_bass_on, R.string.extras_low_bass_off)
        textLowBassNote.text = lowBassNote()
        btnBass.showSwitch(prefs.bassBoostEnabled, R.string.extras_bass_on, R.string.extras_bass_off)
        btnLoudness.showSwitch(prefs.loudnessEnhancerEnabled, R.string.extras_loudness_on, R.string.extras_loudness_off)
        btnNight.showSwitch(prefs.nightModeEnabled, R.string.extras_night_on, R.string.extras_night_off)
    }

    /** What the switch does, or, while on, where this output's reference sits and what it adds right now. */
    private fun lowBassNote(): String {
        if (!prefs.lowVolumeBassEnabled) return getString(R.string.extras_low_bass_note)
        val reading = LowVolumeBass.read(this, prefs)
        val reference = reading.referenceDb
        val now = reading.nowDb
        if (reference == null || now == null) return getString(R.string.extras_low_bass_unknown)
        val effect = if (reading.liftDb > 0.0) {
            getString(R.string.extras_low_bass_lift, reading.liftDb)
        } else {
            getString(R.string.extras_low_bass_flat)
        }
        return getString(R.string.extras_low_bass_status, reference, now, effect)
    }

    private companion object {
        /** AudioManager.VOLUME_CHANGED_ACTION: sent by the system on most builds, but hidden API. */
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}
