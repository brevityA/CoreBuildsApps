package tv.corebuilds.eq.apply

/** Initial routing is not a route *change*. Only two different reported endpoint sets are. */
internal class ProbeRouteObservation<T> {
    private var first: Set<T>? = null
    private var moved = false

    @Synchronized
    fun observe(endpoints: Collection<T>) {
        val reported = endpoints.toSet()
        if (reported.isEmpty()) return // settling/no report is handled by the final route read
        val previous = first
        if (previous == null) first = reported
        else if (previous != reported) moved = true
    }

    @Synchronized
    fun changed(): Boolean = moved
}

/** A playback callback is a hint, not evidence that an output changed. Ignores repeated hints. */
internal class PlaybackRouteHint<T>(initial: T) {
    private var previous = initial
    private var playing = false

    fun reset(value: T, active: Boolean = playing) {
        previous = value
        playing = active
    }

    /** Recheck one real media start too: older Android may have only a coarse connected hint. */
    fun observe(value: T, active: Boolean): Boolean {
        val routeChanged = changed(value)
        val started = active && !playing
        playing = active
        return routeChanged || started
    }

    fun changed(value: T): Boolean {
        if (value == previous) return false
        previous = value
        return true
    }
}
