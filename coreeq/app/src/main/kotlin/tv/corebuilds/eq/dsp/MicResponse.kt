package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.export.MicrophoneResponsePoint
import kotlin.math.ln

/**
 * The microphone's own frequency response, taken out of the measurement (1.11.0).
 *
 * Android reports each active microphone's response through
 * `AudioRecord.activeMicrophones[].frequencyResponse` — the deviation from flat, in dB at
 * a handful of frequencies. Core EQ has always stored those points on the profile as
 * provenance ([tv.corebuilds.eq.export.Profile.microphoneResponse]) but never used them,
 * so a remote that reads 4 dB low at 3 kHz made the room look 4 dB down there and the
 * correction followed it. This is the deconvolution: subtract what the microphone itself
 * added before anything — null detection, roll-off, gates, the correction — reads the
 * curve.
 *
 * Deliberately conservative, because metadata is not a calibration certificate:
 *
 * 1. **Capped at [MAX_APPLIED_DB] per band.** A wrong or stale report can move the
 *    correction by at most this much; beyond it the clamp wins and the measurement
 *    stands. Flat metadata (every point at 0 dB) is an exact no-op.
 * 2. **No extrapolation.** Between reported points the curve is linear in log10(Hz)
 *    (how the standard presents it); outside the reported span the band is left
 *    exactly as measured — a microphone that says nothing about 40 Hz is not asked.
 * 3. **[MIN_POINTS] valid points or nothing.** One or two stray points describe a
 *    slope nobody measured; fewer than that, and the list is ignored whole.
 * 4. **Signal-to-noise and bandwidth are read from the raw capture still** — the
 *    response scales signal and noise alike, so SNR is already response-free, and the
 *    capture's bandwidth edge stays the capture's, not the metadata's idea of it.
 * 5. **One capsule, or nothing.** Points labelled to more than one microphone are a
 *    blend, and blending two unknown responses yields a third curve that matches
 *    neither; spatial averaging is legitimate for one measurement mic at several
 *    positions, not for different capsules in fixed positions. A multi-label list
 *    is refused whole — [microphoneCount] — in favour of leaving the measurement
 *    alone.
 *
 * Pure Kotlin so the whole rule is unit-tested without Android ([MicResponseTest]).
 */
object MicResponse {

    /** The most one band may move on the strength of metadata alone. */
    const val MAX_APPLIED_DB = 6.0

    /** Below this many valid points the list describes a slope nobody measured. */
    const val MIN_POINTS = 4

    private val SUPPORTED_HZ = 20.0..20_000.0

    /** Points that are usable: finite, positive, inside the audible band. */
    private fun List<MicrophoneResponsePoint>.valid(): List<MicrophoneResponsePoint> =
        filter { p -> p.frequencyHz.isFinite() && p.responseDb.isFinite() && p.frequencyHz in SUPPORTED_HZ }
            .sortedBy { it.frequencyHz }

    /**
     * True when [points] may be applied: at least [MIN_POINTS] distinct, in-band,
     * finite points. Distinct on frequency — a repeated report of the same point adds
     * no information.
     */
    fun applicable(points: List<MicrophoneResponsePoint>): Boolean =
        points.valid().distinctBy { it.frequencyHz }.size >= MIN_POINTS

    /**
     * The reported response at [hz], in dB: linear in log10(Hz) between the two points
     * that bracket it, exact on a point, and null outside the reported span or when
     * [points] are not [applicable] — null means "the metadata says nothing here",
     * which [flatten] reads as "leave the band alone".
     */
    fun responseDbAt(points: List<MicrophoneResponsePoint>, hz: Double): Double? {
        if (!hz.isFinite() || hz <= 0.0) return null
        if (!applicable(points)) return null
        val ps = points.valid().distinctBy { it.frequencyHz }
        if (ps.size < 2) return null
        if (hz < ps.first().frequencyHz || hz > ps.last().frequencyHz) return null
        val exact = ps.firstOrNull { it.frequencyHz == hz }?.responseDb
            ?: return interpolate(ps, hz)
        return exact
    }

    private fun interpolate(ps: List<MicrophoneResponsePoint>, hz: Double): Double? {
        val hi = ps.indexOfFirst { it.frequencyHz >= hz }
        if (hi <= 0) return null
        val a = ps[hi - 1]
        val b = ps[hi]
        val span = ln(b.frequencyHz / a.frequencyHz)
        if (span <= 0.0) return b.responseDb
        val t = (ln(hz / a.frequencyHz) / span).coerceIn(0.0, 1.0)
        return a.responseDb + t * (b.responseDb - a.responseDb)
    }

    /**
     * How many distinct microphones the valid points come from. Zero or one means
     * the list is one capsule's curve and can be read as such; two or more means
     * Android reported several capsules and the concatenation describes none of them.
     * The count is only as good as the labels, so CaptureEngine makes them unique
     * per mic at capture time (description, " #index"-suffixed when several mics
     * report). Profiles saved before that suffix exist, but nothing re-flattens a
     * saved list, so the collision can no longer reach a live measurement.
     */
    fun microphoneCount(points: List<MicrophoneResponsePoint>): Int =
        points.valid().map { it.microphone }.distinct().size

    /**
     * [measuredDb] with the microphone's reported response subtracted, per band:
     * the deconvolution. Every band outside the reported span, every non-finite band,
     * and the whole array when [points] are not [applicable] or span more than one
     * microphone, come back unchanged: a blended two-capsule curve is not a third,
     * averaging-friendly curve — it matches neither capsule, so it is refused whole,
     * exactly like thin data is. The correction, the nulls and the roll-off that
     * follow read this curve.
     */
    fun flatten(
        centresHz: DoubleArray,
        measuredDb: DoubleArray,
        points: List<MicrophoneResponsePoint>
    ): DoubleArray {
        require(centresHz.size == measuredDb.size) { "centres and measured must be the same length" }
        if (!applicable(points) || microphoneCount(points) > 1) return measuredDb.copyOf()
        return DoubleArray(measuredDb.size) { k ->
            val m = measuredDb[k]
            if (!m.isFinite()) return@DoubleArray m
            val reported = responseDbAt(points, centresHz[k]) ?: return@DoubleArray m
            m - reported.coerceIn(-MAX_APPLIED_DB, MAX_APPLIED_DB)
        }
    }

    /** For notes and tests: the span the metadata actually covers, or null. */
    fun spanHz(points: List<MicrophoneResponsePoint>): Pair<Double, Double>? {
        val ps = points.valid().distinctBy { it.frequencyHz }
        if (ps.size < 2) return null
        return ps.first().frequencyHz to ps.last().frequencyHz
    }

    /** [spanHz] as text: whole Hz below 1 kHz, tenths of a kHz above, so a low span never reads "0.0 kHz". */
    fun spanText(points: List<MicrophoneResponsePoint>): String? =
        spanHz(points)?.let { (low, high) -> "${hzText(low)}-${hzText(high)}" }

    private fun hzText(hz: Double): String =
        if (hz < 1_000.0) String.format(java.util.Locale.US, "%.0f Hz", hz)
        else String.format(java.util.Locale.US, "%.1f kHz", hz / 1_000.0)

    /** One active microphone's report as Android gave it: its description and (Hz, dB) points. */
    class Report(val description: String?, val points: List<Pair<Double, Double>>)

    /** Longest description kept, so a label plus its " #n" suffix fits the 80-character export field. */
    private const val MAX_DESCRIPTION = 72

    /**
     * One capture's reports as labelled points, at most [maxPoints] in all, from the
     * first [maxMicrophones] mics. Both capture paths (the TV's own, and the phone
     * companion's) build their list here, so the single-capsule guard reads the same
     * thing from each.
     *
     * - **Each mic gets an equal share of [maxPoints].** A first mic with a long list
     *   could otherwise take the whole budget, leave the next mic no points, and make a
     *   multi-mic capture look like one capsule to [microphoneCount], so [flatten] would
     *   apply the first mic's curve to a capture several mics made.
     * - **With more than one mic, every label carries its position** ("#1", "#2"), so two
     *   mics sharing a description ("Built-in microphone") stay two capsules.
     */
    fun collect(
        reports: List<Report>,
        fallbackName: String,
        maxMicrophones: Int,
        maxPoints: Int
    ): List<MicrophoneResponsePoint> {
        val used = reports.take(maxMicrophones)
        if (used.isEmpty() || maxPoints <= 0) return emptyList()
        val share = maxPoints / used.size
        val out = mutableListOf<MicrophoneResponsePoint>()
        used.forEachIndexed { index, report ->
            val description = report.description?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_DESCRIPTION)
            val label = if (reports.size > 1) "${description ?: fallbackName} #${index + 1}"
            else description ?: "$fallbackName 1"
            report.points.asSequence()
                .filter { (hz, db) -> hz > 0.0 && hz.isFinite() && db.isFinite() }
                .take(share)
                .forEach { (hz, db) -> out += MicrophoneResponsePoint(label, hz, db) }
        }
        return out
    }
}
