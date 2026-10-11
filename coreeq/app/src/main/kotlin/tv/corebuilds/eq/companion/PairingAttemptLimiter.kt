package tv.corebuilds.eq.companion

/**
 * Small, in-memory per-peer limiter for the one-time pairing endpoint.
 *
 * A peer that sends malformed/authentication-failing requests must not be
 * able to disable pairing for every device on the LAN. The map is bounded so
 * unauthenticated source addresses cannot allocate unbounded state. [nowMs]
 * is monotonic elapsed time supplied by the caller.
 */
internal class PairingAttemptLimiter(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val blockMs: Long = DEFAULT_BLOCK_MS,
    private val maxPeers: Int = DEFAULT_MAX_PEERS
) {

    private data class State(
        var windowStartedMs: Long,
        var attempts: Int = 0,
        var blockedUntilMs: Long = 0L
    )

    private val peers = LinkedHashMap<String, State>(16, 0.75f, true)

    init {
        require(maxAttempts > 0)
        require(windowMs > 0L)
        require(blockMs > 0L)
        require(maxPeers > 0)
    }

    /** Allow at most [maxAttempts] pairing requests per peer in each window. */
    @Synchronized
    fun allow(peerAddress: String, nowMs: Long): Boolean {
        val peer = peerAddress.trim()
        if (peer.isEmpty() || nowMs < 0L) return false

        var state = peers[peer]
        if (state == null) {
            if (peers.size >= maxPeers) {
                val eldest = peers.entries.iterator()
                if (eldest.hasNext()) {
                    eldest.next()
                    eldest.remove()
                }
            }
            state = State(windowStartedMs = nowMs)
            peers[peer] = state
        }

        if (state.blockedUntilMs > nowMs) return false
        if (state.blockedUntilMs != 0L || nowMs - state.windowStartedMs >= windowMs) {
            state.windowStartedMs = nowMs
            state.attempts = 0
            state.blockedUntilMs = 0L
        }

        if (state.attempts >= maxAttempts) {
            state.blockedUntilMs = nowMs + blockMs
            return false
        }
        state.attempts++
        return true
    }

    /** Remove the short-lived rate-limit state after a successful pairing. */
    @Synchronized
    fun recordSuccess(peerAddress: String) {
        peers.remove(peerAddress.trim())
    }

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
        const val DEFAULT_WINDOW_MS = 60_000L
        const val DEFAULT_BLOCK_MS = 30_000L
        const val DEFAULT_MAX_PEERS = 128
    }
}
