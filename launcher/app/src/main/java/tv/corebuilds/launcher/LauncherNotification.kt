package tv.corebuilds.launcher

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * A single, dismissible status notification. POST_NOTIFICATIONS is requested
 * at runtime on Android 13+; the launcher never posts a notification until the
 * owner presses the Customise action.
 */
internal object LauncherNotification {
    const val CHANNEL_ID = "core_builds_launcher_status"
    const val NOTIFICATION_ID = 7

    fun canPost(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun showStatus(context: Context, title: String, text: String): Boolean {
        if (!canPost(context)) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notification_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            val notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: Exception) {
            false
        }
    }
}
