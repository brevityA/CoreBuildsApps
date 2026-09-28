package tv.corebuilds.eq.apply

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import tv.corebuilds.eq.export.ProfileStore

/**
 * Brings correction back after a reboot or an app update, if it was on.
 *
 * Session broadcasts are not received here: [EqService] registers for those
 * itself, because implicit broadcasts no longer reach manifest receivers.
 * Android 15 forbids starting a media-playback service from BOOT_COMPLETED;
 * when that happens the refusal is recorded so the Home screen can say why
 * correction is off and that opening Core EQ turns it back on.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val store = ProfileStore(context)
        if (!store.correctionEnabled) return
        val refused = EqService.enable(context)
        if (refused != null) {
            Log.w("CoreEqBoot", refused)
            store.setStatus("Correction did not restart after the TV started ($refused). It resumes as soon as you open Core EQ.", isError = true)
        }
    }
}
