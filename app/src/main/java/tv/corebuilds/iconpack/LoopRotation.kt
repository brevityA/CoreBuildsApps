package tv.corebuilds.iconpack

import android.content.Context

/**
 * Live loop rotation, a Core Builds Plus extra: the live wallpaper moves to
 * the next downloaded loop when the home screen comes back after the chosen
 * interval.
 *
 * No alarm, job or background work: [LiveWallpaperService] asks
 * [advanceIfDue] each time its surface becomes visible, which is the only
 * moment a new loop could be seen anyway. It never downloads (the engine
 * never does), so rotation walks the loops already on this TV, in catalogue
 * order, and stays put when there is only one.
 */
object LoopRotation {

    /** The intervals the Settings row cycles through, in hours; 0 is off. */
    val HOURS = intArrayOf(0, 1, 6, 24)

    fun nextHours(current: Int): Int =
        HOURS[(HOURS.indexOf(current).coerceAtLeast(0) + 1) % HOURS.size]

    /**
     * The loop after [currentId] in catalogue order that is in [available],
     * or null when no other loop is. An unknown [currentId] starts from the
     * top of the catalogue.
     */
    fun next(currentId: String, available: Set<String>): String? {
        val order = LiveLoop.LOOPS.map { it.id }
        val start = order.indexOf(currentId)
        for (step in 1..order.size) {
            val id = order[(start + step).mod(order.size)]
            if (id != currentId && id in available) return id
        }
        return null
    }

    /**
     * Called by the engine as the home screen comes back. Moves the live loop
     * choice when Plus is owned, rotation is on and the interval has passed
     * since the loop last changed (by hand or by rotation); true when it did.
     */
    fun advanceIfDue(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (!Plus.owned(context)) return false
        val hours = Prefs.liveRotateHours(context)
        if (hours <= 0) return false
        val since = Prefs.liveLoopChangedAt(context)
        // No stamp yet, or a clock that went backwards: start the interval now.
        if (since <= 0L || now < since) {
            Prefs.setLiveLoopChangedAt(context, now)
            return false
        }
        if (now - since < hours * HOUR_MS) return false
        val available = LiveLoop.LOOPS
            .filter { LiveLoopDownloader.playable(context, it) != null }
            .map { it.id }
            .toSet()
        val next = next(Prefs.liveLoopId(context), available)
        if (next == null) {
            Prefs.setLiveLoopChangedAt(context, now)
            return false
        }
        Prefs.setLiveLoopId(context, next, now)
        return true
    }

    private const val HOUR_MS = 3_600_000L
}
