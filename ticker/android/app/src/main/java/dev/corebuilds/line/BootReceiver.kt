package dev.corebuilds.line

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Puts the VPN dot back after a reboot or an in-app update.
 *
 * A status dot is only useful if it is there when the box comes back on: the
 * whole point is that nobody has to go and check. Google TV boxes reboot on
 * their own schedule, so "remembered until the next reboot" would be a
 * feature that quietly stops working.
 *
 * Two deliberate limits:
 *
 * - Only the dot is restored. The ticker is a WebView and is not resurrected
 *   from a boot broadcast (see [OverlayPrefs]).
 * - The start is wrapped, because Android 15 narrowed the
 *   SYSTEM_ALERT_WINDOW-based exemption from background service starts, and a
 *   BOOT_COMPLETED receiver is not allowed to launch every foreground service
 *   type. `specialUse` is not on the restricted list, and Core Line targets
 *   API 34 today, but a boot path that throws is the worst possible place to
 *   be clever. Note *where* a refusal lands: on Android 12+ the platform
 *   throws ForegroundServiceStartNotAllowedException from the service's own
 *   startForeground(), so OverlayService handles that and stops itself; the
 *   catch here covers the other refusals a vendor ROM can raise at the call
 *   site.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = OverlayPrefs(context)
        val config = prefs.readDotConfig() ?: return
        if (!android.provider.Settings.canDrawOverlays(context)) return

        try {
            OverlayService.startDot(context, config)
        } catch (err: Exception) {
            // A vendor ROM can throw SecurityException for overlay services on
            // booted devices without a user present. A refused *foreground*
            // start surfaces inside the service instead (see above), where it
            // is handled.
        }
    }
}
