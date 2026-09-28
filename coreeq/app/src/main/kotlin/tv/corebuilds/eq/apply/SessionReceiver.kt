package tv.corebuilds.eq.apply

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

/**
 * Listens for third-party audio player effect sessions on Android TV.
 */
class SessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: ""

        if (sessionId == AudioEffect.ERROR_BAD_VALUE) return

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                val serviceIntent = Intent(context, EqService::class.java).apply {
                    this.action = EqService.ACTION_APPLY_SESSION
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                }
                context.startService(serviceIntent)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                val serviceIntent = Intent(context, EqService::class.java).apply {
                    this.action = EqService.ACTION_CLOSE_SESSION
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                }
                context.startService(serviceIntent)
            }
        }
    }
}
