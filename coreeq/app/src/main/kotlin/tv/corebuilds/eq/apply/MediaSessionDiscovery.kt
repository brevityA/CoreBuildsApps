package tv.corebuilds.eq.apply

import android.service.notification.NotificationListenerService
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.util.Log
import java.util.LinkedHashMap

/** Package identities from active framework media sessions; never used as audio-session IDs. */
internal data class MediaSessionSnapshot(
    val packages: Set<String>,
    /** The active-session query completed and its absence can be considered. */
    val complete: Boolean,
    /** False when the user has not granted Notification access. */
    val available: Boolean
)

internal data class MediaSessionCandidate(val packageName: String?, val playbackState: Int?)

/**
 * Optional second source for app-mode rules. The user explicitly grants Android
 * Notification access, which authorizes MediaSessionManager.getActiveSessions.
 * Core EQ reads only controller package names and playback states: it does not
 * inspect notification text, titles, artists, artwork, or audio content.
 *
 * A media session identifies an app, not the output route and not an effect
 * session. A player without a published MediaSession remains unidentified;
 * AudioManager's coarse playback callback then forces the safe Everyday mode.
 */
internal object MediaSessionDiscovery {
    private const val TAG = "CoreEqMediaSessions"

    fun listenerComponent(context: Context): ComponentName =
        ComponentName(context, CoreEqNotificationListener::class.java)

    fun hasAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            context.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(listenerComponent(context)) == true
        } catch (e: Exception) {
            Log.w(TAG, "Could not check Notification access", e)
            false
        }
    }

    fun discover(context: Context): MediaSessionSnapshot {
        if (!hasAccess(context)) return MediaSessionSnapshot(emptySet(), complete = false, available = false)
        return try {
            val manager = context.getSystemService(MediaSessionManager::class.java)
                ?: return MediaSessionSnapshot(emptySet(), complete = false, available = true)
            val candidates = manager.getActiveSessions(listenerComponent(context)).map { controller ->
                MediaSessionCandidate(controller.packageName, controller.playbackState?.state)
            }
            MediaSessionSnapshot(
                activePackages(candidates, context.packageName),
                complete = true,
                available = true
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Media-session access was revoked or refused", e)
            MediaSessionSnapshot(emptySet(), complete = false, available = true)
        } catch (e: Exception) {
            Log.w(TAG, "Could not query active media sessions", e)
            MediaSessionSnapshot(emptySet(), complete = false, available = true)
        }
    }

    /** Pure policy: only current playing/buffering sessions identify a mode. */
    fun activePackages(candidates: List<MediaSessionCandidate>, ownPackage: String): Set<String> =
        candidates.asSequence()
            .filter { it.playbackState == PlaybackState.STATE_PLAYING || it.playbackState == PlaybackState.STATE_BUFFERING }
            .mapNotNull { it.packageName?.trim()?.takeIf(String::isNotEmpty) }
            .filter { it != ownPackage }
            .toSortedSet()
}

/**
 * Event-driven session-list/state observer. It retains only controller tokens
 * and callbacks; the callback path never reads metadata or notification text.
 * All methods and callbacks are serialized on [handler].
 */
internal class MediaSessionMonitor(
    private val context: Context,
    private val handler: Handler,
    private val onChanged: () -> Unit
) {
    private val manager: MediaSessionManager? = context.getSystemService(MediaSessionManager::class.java)
    private var registered = false
    private val controllerTracker = MediaSessionControllerTracker<MediaController, MediaSession.Token>(
        keyOf = { it.sessionToken },
        attach = { controller, changed ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = changed()
                override fun onSessionDestroyed() = changed()
            }
            try {
                controller.registerCallback(callback, handler)
                val unregister: () -> Unit = { controller.unregisterCallback(callback) }
                unregister
            } catch (e: Exception) {
                Log.w(TAG, "Could not observe media-controller state", e)
                null
            }
        },
        onChanged = onChanged
    )

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { active ->
        reconcileControllers(active.orEmpty())
        onChanged()
    }

    /** Start while access is available; failures are logged and leave queries as best effort. */
    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            stop()
            return
        }
        val service = manager ?: return
        if (registered) return
        try {
            service.addOnActiveSessionsChangedListener(
                sessionsChanged,
                MediaSessionDiscovery.listenerComponent(context),
                handler
            )
            registered = true
            // Reconcile immediately rather than waiting for the next session-list event.
            reconcileControllers(service.getActiveSessions(MediaSessionDiscovery.listenerComponent(context)))
        } catch (e: Exception) {
            Log.w(TAG, "Could not observe active media sessions", e)
            stop()
        }
    }

    fun stop() {
        if (registered) {
            try {
                manager?.removeOnActiveSessionsChangedListener(sessionsChanged)
            } catch (e: Exception) {
                Log.w(TAG, "Could not remove active-session listener", e)
            }
            registered = false
        }
        controllerTracker.clear()
    }

    private fun reconcileControllers(active: List<MediaController>) {
        controllerTracker.update(active)
    }

    private companion object {
        const val TAG = "CoreEqMediaSessions"
    }
}
