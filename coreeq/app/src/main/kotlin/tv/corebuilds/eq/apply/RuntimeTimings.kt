package tv.corebuilds.eq.apply

import java.util.EnumMap
import java.util.Locale

/**
 * How long Core EQ's own work takes on this TV, since its process started
 * (roadmap milestone C). The counters in [RuntimeCounters] say how often
 * background work runs; these say how long each kind takes and how it ended.
 *
 * Bounded by construction: one fixed record per [Op], each holding a count,
 * the last, shortest and longest duration, a count per [Outcome] and the most
 * recent [WINDOW] durations for a median. Nothing grows with use, nothing is
 * written to disk or the log per sample, nothing leaves the TV, and it is all
 * gone when the process ends. It is read only by the diagnostics summary and
 * `adb shell dumpsys activity service tv.corebuilds.eq/.apply.EqService`.
 *
 * Durations come from a monotonic clock ([System.nanoTime] by default; tests
 * pass their own), so a wall-clock change cannot make one negative. Each
 * record is guarded by its own lock, because the operations run on different
 * threads: apply on the main thread, discovery on its executor, probes and
 * analysis on workers.
 *
 * These are measurements of this code on this device and nothing more. No
 * figure here is a performance claim until a device run says so
 * (`docs/CORE_EQ_DEVICE_BENCHMARK.md`).
 */
object RuntimeTimings {

    /** What was timed. [label] is what diagnostics print. */
    enum class Op(val label: String) {
        /** The service applying correction: [EqService] `applyAll`, start to its last decision. */
        APPLY("apply"),
        /** Creating or reconfiguring one effect: bands and limiter handed to Android and read back. */
        ENGINE_CONFIGURE("engine configure"),
        /** One DUMP player-discovery request, issued to parsed. */
        DISCOVERY("player discovery"),
        /** One silent-track route probe, opened to Android's answer or giving up. */
        ROUTE_PROBE("route probe"),
        /** One captured sweep analysed (TV or phone capture, first pass or re-analysis). */
        MEASUREMENT_ANALYSIS("measurement analysis"),
        /** Home created to its first draw pass. */
        HOME_FIRST_DRAW("Home first draw"),
        /** Home created to its summary rendered from loaded profile and status facts. */
        HOME_READY("Home ready"),
        /** Profiles created to its first draw pass. */
        PROFILES_FIRST_DRAW("Profiles first draw"),
        /** Profiles created to its list laid out with rows (or found empty). */
        PROFILES_READY("Profiles ready")
    }

    /** How a timed operation ended. */
    enum class Outcome(val label: String) {
        OK("ok"),
        /** Ran, and decided nothing should be applied (no profile for this output, paused). */
        NOT_APPLIED("not applied"),
        /** Handed off to later work (a route probe first). */
        DEFERRED("deferred"),
        /** Ran to its end without the answer it was for (a probe whose track Android routed to no named device). */
        NO_ANSWER("no answer"),
        /** Refused or threw. */
        FAILED("failed")
    }

    const val WINDOW = 32

    private class Record {
        var count = 0L
        var lastNs = 0L
        var minNs = Long.MAX_VALUE
        var maxNs = 0L
        val outcomes = EnumMap<Outcome, Long>(Outcome::class.java)
        val recent = LongArray(WINDOW)
        var next = 0
    }

    private val records = EnumMap<Op, Record>(Op::class.java).apply { for (op in Op.entries) put(op, Record()) }

    /** The monotonic clock, in nanoseconds. Tests replace it; nothing else should. */
    @Volatile internal var clock: () -> Long = System::nanoTime

    /** A start mark for [finish]. */
    fun start(): Long = clock()

    /** Record [op] as having run since [startNs] and ended with [outcome]. */
    fun finish(op: Op, startNs: Long, outcome: Outcome = Outcome.OK) {
        record(op, (clock() - startNs).coerceAtLeast(0L), outcome)
    }

    /** Time [block]: [Outcome.OK] when it returns, [Outcome.FAILED] when it throws (and the throw continues). */
    inline fun <T> time(op: Op, block: () -> T): T {
        val started = start()
        var outcome = Outcome.FAILED
        try {
            return block().also { outcome = Outcome.OK }
        } finally {
            finish(op, started, outcome)
        }
    }

    internal fun record(op: Op, durationNs: Long, outcome: Outcome) {
        val r = records.getValue(op)
        synchronized(r) {
            r.count++
            r.lastNs = durationNs
            if (durationNs < r.minNs) r.minNs = durationNs
            if (durationNs > r.maxNs) r.maxNs = durationNs
            r.outcomes[outcome] = (r.outcomes[outcome] ?: 0L) + 1
            r.recent[r.next] = durationNs
            r.next = (r.next + 1) % WINDOW
        }
    }

    data class Stats(
        val op: Op,
        val count: Long,
        val lastNs: Long,
        val minNs: Long,
        val maxNs: Long,
        /** The median of the most recent [WINDOW] durations (all of them, when fewer). */
        val medianNs: Long,
        val outcomes: Map<Outcome, Long>
    )

    /** A copy of every op that has run at least once, in [Op] order. */
    fun snapshot(): List<Stats> = Op.entries.mapNotNull { op ->
        val r = records.getValue(op)
        synchronized(r) {
            if (r.count == 0L) return@synchronized null
            val n = minOf(r.count, WINDOW.toLong()).toInt()
            val window = r.recent.copyOf(n).sorted()
            val median = if (n % 2 == 1) window[n / 2] else (window[n / 2 - 1] + window[n / 2]) / 2
            Stats(op, r.count, r.lastNs, r.minNs, r.maxNs, median, r.outcomes.toMap())
        }
    }

    /** One line for the diagnostics summary: count, median and range per op; outcomes other than ok named. */
    fun summary(stats: List<Stats> = snapshot()): String =
        if (stats.isEmpty()) "none recorded" else stats.joinToString(" · ") { s ->
            val others = s.outcomes.filterKeys { it != Outcome.OK }.entries
                .joinToString(", ") { (k, v) -> "$v ${k.label}" }
            "${s.op.label} ${s.count}× median ${ms(s.medianNs)}, ${ms(s.minNs)}–${ms(s.maxNs)}" +
                if (others.isEmpty()) "" else " ($others)"
        }

    /** Every figure, one op per line, for `dumpsys`. */
    fun detail(stats: List<Stats> = snapshot()): String =
        if (stats.isEmpty()) "  none recorded" else stats.joinToString("\n") { s ->
            "  ${s.op.name.lowercase()}: count=${s.count} last=${ms(s.lastNs)} median=${ms(s.medianNs)} " +
                "min=${ms(s.minNs)} max=${ms(s.maxNs)} " +
                Outcome.entries.joinToString(" ") { "${it.name.lowercase()}=${s.outcomes[it] ?: 0}" }
        }

    /** "0.4 ms", "12 ms": a tenth below 10 ms, whole milliseconds above. */
    fun ms(ns: Long): String {
        val millis = ns / 1_000_000.0
        return if (millis < 10.0) String.format(Locale.US, "%.1f ms", millis) else String.format(Locale.US, "%.0f ms", millis)
    }

    /** Tests only: the records are process-wide. */
    internal fun reset() {
        for (r in records.values) synchronized(r) {
            r.count = 0; r.lastNs = 0; r.minNs = Long.MAX_VALUE; r.maxNs = 0
            r.outcomes.clear(); r.recent.fill(0); r.next = 0
        }
        clock = System::nanoTime
    }
}
