package tv.corebuilds.eq.apply

/**
 * When the on-screen card shows (1.3.0): once each time the applied profile
 * or mode changes, and once when a route pause settles (uncertain routing
 * or no matching profile); never on a plain reapply.
 *
 * The service reapplies often without anything a viewer would call a change:
 * a volume step under Low-volume bass, an Extra effects reload, a discovered
 * player attaching. Only a different profile (an output switch, a new
 * measurement, a choice in Profiles) or a different mode is announced. When
 * nothing is applied (an output with no profile of its own), the next
 * application is announced again, even if it is the same profile. A deferred
 * pause that recovers before its settling timer fires is not announced; effects
 * and status still pause immediately.
 *
 * Kept free of Android types so the rule is unit-tested (`AppliedNoticeTest`).
 */
class AppliedNotice {
    // Typed keys, not joined strings: no profile id, mode or route can be
    // spelled to match another kind of event.
    private data class Applied(val profileId: String, val mode: String)
    private data class Paused(val route: String)

    private var lastShown: Any? = null
    private var pendingPause: Paused? = null

    /** [profileId] in [mode] is now applied; true when the notice should show. */
    fun onApplied(profileId: String, mode: String): Boolean {
        pendingPause = null
        val key = Applied(profileId, mode)
        if (key == lastShown) return false
        lastShown = key
        return true
    }

    /**
     * Correction paused on [route] (uncertain or without a matching profile);
     * true when the card should say so. A second report for the same route is
     * silent, and whatever applies next is news again.
     */
    fun onPaused(route: String): Boolean {
        pendingPause = null
        val key = Paused(route)
        if (key == lastShown) return false
        lastShown = key
        return true
    }

    /** Start one settling timer per pause, without resetting it on repeated reports. */
    internal fun deferPause(route: String): Boolean {
        val key = Paused(route)
        if (key == lastShown) {
            pendingPause = null
            return false
        }
        if (key == pendingPause) return false
        pendingPause = key
        return true
    }

    /** A still-current pause survived settling. A cancelled/superseded timer cannot announce it. */
    internal fun commitPause(route: String): Boolean {
        if (pendingPause != Paused(route)) return false
        return onPaused(route)
    }

    internal fun cancelDeferredPause() { pendingPause = null }

    /** Nothing is applied any more, so whatever applies next is news. */
    fun reset() {
        pendingPause = null
        lastShown = null
    }
}
