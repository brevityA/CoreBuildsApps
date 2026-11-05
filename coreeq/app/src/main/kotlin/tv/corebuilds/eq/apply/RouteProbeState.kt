package tv.corebuilds.eq.apply

/**
 * Atomic cache/generation and completion batches for the route probe. No Android
 * types: invalidation and callback/retry races can be reproduced on the JVM.
 */
internal class RouteProbeState<T> {
    data class Cached<T>(val atMs: Long, val result: T?)
    data class Publication(val accepted: Boolean, val changed: Boolean)
    data class Completion(val publication: Publication, val callbacks: List<() -> Unit>)

    private val lock = Any()
    private var generation = 0L
    private var cached: Cached<T>? = null
    private var held = false
    private var inFlight = false
    private val callbacks = mutableListOf<() -> Unit>()

    fun generation(): Long = synchronized(lock) { generation }
    fun snapshot(): Cached<T>? = synchronized(lock) { cached }
    fun isHeld(): Boolean = synchronized(lock) { held }

    fun invalidate() = synchronized(lock) {
        generation++
        cached = null
        held = false
    }

    fun hold(nowMs: Long, result: T) = synchronized(lock) {
        generation++
        cached = Cached(nowMs, result)
        held = true
    }

    /** True only for the caller that must launch the worker; the others join its batch. */
    fun enqueue(onDone: () -> Unit): Boolean = synchronized(lock) {
        callbacks += onDone
        if (inFlight) false else { inFlight = true; true }
    }

    fun publish(expectedGeneration: Long, nowMs: Long, result: T?, releasesHold: Boolean): Publication =
        synchronized(lock) { publishLocked(expectedGeneration, nowMs, result, releasesHold) }

    /**
     * Detach this batch before dispatching it. A callback may request a retry;
     * that new callback belongs to the next worker, not the batch being drained.
     */
    fun complete(expectedGeneration: Long, nowMs: Long, result: T?, releasesHold: Boolean): Completion = synchronized(lock) {
        val publication = publishLocked(expectedGeneration, nowMs, result, releasesHold)
        val completed = callbacks.toList()
        callbacks.clear()
        inFlight = false
        Completion(publication, completed)
    }

    private fun publishLocked(expectedGeneration: Long, nowMs: Long, result: T?, releasesHold: Boolean): Publication {
        if (generation != expectedGeneration) return Publication(accepted = false, changed = false)
        val changed = cached == null || cached?.result != result || (held && releasesHold)
        cached = Cached(nowMs, result)
        if (releasesHold) held = false
        return Publication(accepted = true, changed = changed)
    }
}
