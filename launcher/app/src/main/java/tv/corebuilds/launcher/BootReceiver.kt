package tv.corebuilds.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Optional boot-launch fallback. Android normally starts the selected HOME app
 * itself; this receiver only acts when the owner enabled Launch on boot AND the
 * system already resolves HOME to this package. It never changes the default
 * role. Android's background-activity restrictions may still block the start,
 * which is why the physical-device checklist records this separately.
 */
internal class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!LauncherPreferences.readSettings(context).launchOnBoot) return
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolved?.activityInfo?.packageName != context.packageName) return
        try {
            context.startActivity(
                Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            // The system may decline a background activity start; HOME remains user-controlled.
        }
    }
}
