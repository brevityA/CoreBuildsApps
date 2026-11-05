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

/**
 * Core EQ's own silent route probe is a real AudioTrack, and Android reports its
 * start, stop and release to Core EQ's playback callback like any other player.
 * Those reports are not content. Treated as playback, each probe scheduled player
 * discovery, and discovery found the probe stale and ran the next one: a probe
 * and an effect reapply every few seconds with nothing playing. While a probe
 * plays, and for [echoMs] after it ends, playback reports are its echo.
 */
internal class OwnProbeEcho(private val echoMs: Long) {
    private var running = 0
    private var lastEndedAtMs = Long.MIN_VALUE / 2

    @Synchronized
    fun started() {
        running++
    }

    @Synchronized
    fun ended(nowMs: Long) {
        if (running > 0) running--
        lastEndedAtMs = nowMs
    }

    @Synchronized
    fun isEcho(nowMs: Long): Boolean = running > 0 || nowMs - lastEndedAtMs < echoMs
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
