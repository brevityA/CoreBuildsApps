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
     * [measuredDb] with the microphone's reported response subtracted, per band:
     * the deconvolution. Every band outside the reported span, every non-finite band,
     * and the whole array when [points] are not [applicable], come back unchanged.
     * The correction, the nulls and the roll-off that follow read this curve.
     */
    fun flatten(
        centresHz: DoubleArray,
        measuredDb: DoubleArray,
        points: List<MicrophoneResponsePoint>
    ): DoubleArray {
        require(centresHz.size == measuredDb.size) { "centres and measured must be the same length" }
        if (!applicable(points)) return measuredDb.copyOf()
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
}
