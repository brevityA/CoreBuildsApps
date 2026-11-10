package tv.corebuilds.eq.apply

import java.util.concurrent.atomic.AtomicLong

/**
 * What Core EQ has done to the TV's audio in the background since its process
 * started (1.13.2): route probes, player-discovery dumps, effect writes, writes
 * skipped because nothing changed, and correction pauses.
 *
 * Each of these is real work on someone else's playback. A route probe opens
 * an AudioTrack next to the player's, a discovery dump makes the audio server
 * write out its state, and an effect write programs every band. A report of
 * stuttering video (1.13.1) traced to these running on every playback start,
 * so they are counted, logged and shown in Mode's diagnostics, and a regression
 * shows up as a number rather than a feeling.
 */
object RuntimeCounters {
    private val routeProbes = AtomicLong()
    private val discoveryDumps = AtomicLong()
    private val effectWrites = AtomicLong()
    private val effectWritesSkipped = AtomicLong()
    private val correctionPauses = AtomicLong()

    fun routeProbe() { routeProbes.incrementAndGet() }
    fun discoveryDump() { discoveryDumps.incrementAndGet() }
    fun effectWrite() { effectWrites.incrementAndGet() }
    fun effectWriteSkipped() { effectWritesSkipped.incrementAndGet() }
    fun correctionPause() { correctionPauses.incrementAndGet() }

    data class Snapshot(
        val routeProbes: Long,
        val discoveryDumps: Long,
        val effectWrites: Long,
        val effectWritesSkipped: Long,
        val correctionPauses: Long
    ) {
        /** One line for Mode's diagnostics and the service log. */
        fun summary(): String =
            "$routeProbes route probes · $discoveryDumps discovery dumps · " +
                "$effectWrites effect writes ($effectWritesSkipped skipped, unchanged) · " +
                "$correctionPauses correction pauses"
    }

    fun snapshot(): Snapshot = Snapshot(
        routeProbes.get(),
        discoveryDumps.get(),
        effectWrites.get(),
        effectWritesSkipped.get(),
        correctionPauses.get()
    )

    /** Tests only: the counters are process-wide. */
    internal fun reset() {
        routeProbes.set(0)
        discoveryDumps.set(0)
        effectWrites.set(0)
        effectWritesSkipped.set(0)
        correctionPauses.set(0)
    }
}
