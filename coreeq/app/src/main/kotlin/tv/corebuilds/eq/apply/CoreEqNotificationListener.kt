package tv.corebuilds.eq.apply

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * The manifest-registered listener that keeps [MediaSessionMonitor]'s view of
 * the active sessions current.
 *
 * It lives in its own file so the policy it triggers — [MediaSessionDiscovery]
 * and [MediaSessionControllerTracker] — can be compiled and unit-tested without
 * the Android framework, which is why those two have tests and this does not.
 * It still only looks at whether a notification carries a media-session extra;
 * it never reads a title, text or artwork. Declaring it in the manifest is what
 * puts Core EQ in Android's Notification-access settings at all.
 */
class CoreEqNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        requestRefresh()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        requestRefresh()
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) {
        if (isMediaSessionNotification(sbn)) requestRefresh()
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification) {
        if (isMediaSessionNotification(sbn)) requestRefresh()
    }

    /** Inspect only the extras key, never the notification's title/text/content. */
    private fun isMediaSessionNotification(sbn: android.service.notification.StatusBarNotification): Boolean =
        sbn.notification.extras?.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) == true

    private fun requestRefresh() {
        EqService.send(this, EqService.ACTION_REFRESH_DISCOVERY)
    }
}
