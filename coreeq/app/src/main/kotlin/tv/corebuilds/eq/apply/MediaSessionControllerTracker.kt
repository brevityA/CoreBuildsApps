package tv.corebuilds.eq.apply

import android.util.Log

/**
 * Keeps one state callback per active controller token. Isolated from Android
 * services so membership/replacement/cleanup behaviour is unit-testable — it is
 * generic over the controller and key types for exactly that reason, and lives
 * in its own file so the harness can compile it without the media-session
 * framework classes its caller needs.
 */
internal class MediaSessionControllerTracker<C : Any, K : Any>(
    private val keyOf: (C) -> K,
    /** Return a cleanup action, or null when callback registration failed. */
    private val attach: (C, onChanged: () -> Unit) -> (() -> Unit)?,
    private val onChanged: () -> Unit
) {
    private data class Registration<C>(val controller: C, val unregister: () -> Unit)
    private val registrations = LinkedHashMap<K, Registration<C>>()

    fun update(active: List<C>) {
        val current = LinkedHashMap<K, C>()
        active.forEach { current[keyOf(it)] = it }

        registrations.keys.toList().forEach { key ->
            val previous = registrations[key] ?: return@forEach
            if (current[key] !== previous.controller) {
                registrations.remove(key)
                unregisterSafely(previous.unregister)
            }
        }
        current.forEach { (key, controller) ->
            if (key !in registrations) {
                attach(controller, onChanged)?.let { unregister ->
                    registrations[key] = Registration(controller, unregister)
                }
            }
        }
    }

    fun clear() {
        registrations.values.forEach { unregisterSafely(it.unregister) }
        registrations.clear()
    }

    private fun unregisterSafely(unregister: () -> Unit) {
        try {
            unregister()
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove media-controller callback", e)
        }
    }

    private companion object {
        const val TAG = "CoreEqMediaSessions"
    }
}
