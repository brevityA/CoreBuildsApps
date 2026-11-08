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

/**
 * A playback callback is a hint, not evidence that an output changed. Ignores
 * repeated hints.
 *
 * Until 1.13.2 every media start counted as a route check: correction paused,
 * a silent probe track opened beside the player's, and the effect was
 * re-applied. A streaming player "starts" again each time it resumes after
 * buffering, so a buffering stream got all of that on every resume, and the
 * extra track and the effect toggles can stall the player's audio clock: a
 * report of a stream that "lags very much" with Core EQ on. Now:
 *
 * - a changed hint is a route change ([Verdict.ROUTE_CHANGED]), as before;
 * - a start is nothing when the hint is precise (API 33+ reports the actual
 *   media route, so a moved route already changes the hint);
 * - on a coarse hint (older Android: connected devices only) a start earns a
 *   quiet verification ([Verdict.VERIFY_START]), at most once per
 *   [startRecheckMs] - the reason the start check existed, kept at a fraction
 *   of its cost.
 */
internal class PlaybackRouteHint<T>(initial: T, private val startRecheckMs: Long = START_RECHECK_MS) {
    enum class Verdict { NONE, ROUTE_CHANGED, VERIFY_START }

    private var previous = initial
    private var playing = false
    private var lastCheckAtMs = Long.MIN_VALUE / 2

    fun reset(value: T, active: Boolean = playing) {
        previous = value
        playing = active
    }

    fun observe(value: T, active: Boolean, preciseHint: Boolean, nowMs: Long): Verdict {
        val routeChanged = changed(value)
        val started = active && !playing
        playing = active
        return when {
            routeChanged -> {
                lastCheckAtMs = nowMs // a full route check is about to run
                Verdict.ROUTE_CHANGED
            }
            started && !preciseHint && nowMs - lastCheckAtMs >= startRecheckMs -> {
                lastCheckAtMs = nowMs
                Verdict.VERIFY_START
            }
            else -> Verdict.NONE
        }
    }

    fun changed(value: T): Boolean {
        if (value == previous) return false
        previous = value
        return true
    }

    companion object {
        /** A coarse hint's media start re-verifies the route at most this often. */
        const val START_RECHECK_MS = 5 * 60_000L
    }
}

/**
 * How long a route probe's answer stands (1.13.2). Pure, so the rule is
 * tested on the JVM.
 *
 * Every probe opens a real media AudioTrack. With a 2-second cache, anything
 * that read the route more than 2 seconds after the last probe ran another:
 * player discovery, a re-apply, a volume key with Low-volume bass on. While a
 * film played that meant probe tracks opening beside the player's.
 *
 * A route changes in ways Core EQ hears about: a device added or removed
 * (which invalidates the cache) or Android's route hint changing. So a usable
 * probe now stands while the hint still reads the same as when the probe ran,
 * up to [MAX_TRUST_MS]. What does not get the long trust: no result (no track
 * could open; the estimate answers instead) and the two hard-stop answers,
 * UNCONFIRMED and AMBIGUOUS, whose recovery is a fresh probe.
 */
internal object RouteTrust {
    /** One apply's burst of reads: the 1.10 cache, still the floor for every answer. */
    const val SHORT_MS = 2_000L
    /** The safety net for a route change that neither a device event nor the hint reported. */
    const val MAX_TRUST_MS = 10 * 60_000L

    fun trusted(ageMs: Long, usable: Boolean, hintAtProbe: Any?, hintNow: Any?): Boolean = when {
        ageMs < 0L -> false
        ageMs < SHORT_MS -> true
        !usable -> false
        ageMs >= MAX_TRUST_MS -> false
        hintAtProbe == null -> false
        else -> hintAtProbe == hintNow
    }
}

/**
 * At most one player-discovery dump per [minIntervalMs] (1.13.2). The dump
 * makes the audio server write out its state; it ran after every playback
 * change, which on a buffering stream is every few seconds. A throttled
 * request is not lost: the caller schedules one more look for when the
 * window opens, so a new player is still found.
 */
internal class DiscoveryDumpThrottle(private val minIntervalMs: Long = MIN_INTERVAL_MS) {
    private var lastAtMs = Long.MIN_VALUE / 2

    /** 0 when a dump may run now; otherwise how long until it may. */
    fun waitMs(nowMs: Long): Long = (lastAtMs + minIntervalMs - nowMs).coerceAtLeast(0L)

    fun ran(nowMs: Long) {
        lastAtMs = nowMs
    }

    companion object {
        const val MIN_INTERVAL_MS = 15_000L
    }
}
