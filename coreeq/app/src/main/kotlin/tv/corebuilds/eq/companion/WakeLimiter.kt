package tv.corebuilds.eq.companion

/**
 * Bounds the legacy unauthenticated discovery probe. It is acknowledged for
 * older phone clients, but can no longer open a pairing code or any TV UI.
 */
internal object WakeLimiter {
    const val MIN_INTERVAL_MS = 5_000L

    /** True when a wake at [nowMs] is allowed, given the last accepted one at [lastWakeMs]. */
    fun shouldWake(nowMs: Long, lastWakeMs: Long?): Boolean =
        lastWakeMs == null || nowMs - lastWakeMs >= MIN_INTERVAL_MS
}
