package tv.corebuilds.eq.companion

/**
 * How often an unauthenticated wake request may open the TV's pairing screen. A LAN peer can
 * send wake requests, so a burst cannot keep the TV's pairing screen busy or flashing.
 */
internal object WakeLimiter {
    const val MIN_INTERVAL_MS = 5_000L

    /** True when a wake at [nowMs] is allowed, given the last accepted one at [lastWakeMs]. */
    fun shouldWake(nowMs: Long, lastWakeMs: Long?): Boolean =
        lastWakeMs == null || nowMs - lastWakeMs >= MIN_INTERVAL_MS
}
