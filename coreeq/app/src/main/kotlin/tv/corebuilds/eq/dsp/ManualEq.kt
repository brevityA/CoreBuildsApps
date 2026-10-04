package tv.corebuilds.eq.dsp

import kotlin.math.abs

/** A reusable manual graphic-EQ preset. Filters use the same RBJ peaking model as exports. */
data class ManualEqPreset(
    val id: String,
    val name: String,
    val filters: List<PeakingFilter>
)

/**
 * Manual tone shaping layered after the measured room correction.
 *
 * The editor exposes ten fixed frequency controls over Core EQ's trusted
 * 40 Hz–8 kHz span. Each control is a peaking filter; the applied engine
 * samples the combined response at its own band centres. The response is
 * deliberately zero outside the trusted span, so a manual preset cannot
 * extend Core EQ's measurement claim into frequencies it does not correct.
 */
object ManualEq {
    const val MIN_GAIN_DB = -6.0
    const val MAX_GAIN_DB = 6.0
    const val STEP_DB = 0.5
    const val DEFAULT_Q = 1.0

    val BAND_CENTRES_HZ = listOf(
        40.0, 63.0, 125.0, 250.0, 500.0,
        1_000.0, 2_000.0, 4_000.0, 6_300.0, 8_000.0
    )

    val BUILT_IN_PRESETS = listOf(
        ManualEqPreset("flat", "Flat", emptyList()),
        ManualEqPreset(
            "bass-lift",
            "Bass lift",
            listOf(
                PeakingFilter(63.0, 0.8, 3.0),
                PeakingFilter(125.0, 0.9, 2.0),
                PeakingFilter(250.0, 0.9, 1.0)
            )
        ),
        ManualEqPreset(
            "speech-clarity",
            "Speech clarity",
            listOf(
                PeakingFilter(1_000.0, 0.8, 1.0),
                PeakingFilter(2_000.0, 0.9, 2.0),
                PeakingFilter(4_000.0, 0.9, 1.5),
                PeakingFilter(6_300.0, 0.8, 0.5)
            )
        ),
        ManualEqPreset(
            "less-bass",
            "Less bass",
            listOf(
                PeakingFilter(40.0, 0.8, -3.0),
                PeakingFilter(63.0, 0.9, -2.0),
                PeakingFilter(125.0, 0.9, -1.0)
            )
        )
    )

    /** The gain shown by a fixed control, or 0 dB if it has not been changed. */
    fun gainAtBand(filters: List<PeakingFilter>, bandIndex: Int): Double {
        val centre = BAND_CENTRES_HZ.getOrNull(bandIndex) ?: return 0.0
        return filters.firstOrNull { abs(it.fc - centre) < 0.01 }?.gain ?: 0.0
    }

    /** Replace one fixed graphic band, snapping to 0.5 dB and the safe ±6 dB UI range. */
    fun withBandGain(filters: List<PeakingFilter>, bandIndex: Int, requestedDb: Double): List<PeakingFilter> {
        val centre = BAND_CENTRES_HZ.getOrNull(bandIndex)
            ?: throw IndexOutOfBoundsException("Manual EQ band $bandIndex does not exist")
        require(requestedDb.isFinite()) { "Manual EQ gain must be finite" }
        val gain = (kotlin.math.round(requestedDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) / STEP_DB) * STEP_DB)
        val kept = filters.filterNot { abs(it.fc - centre) < 0.01 }
        if (abs(gain) < STEP_DB / 2.0) return kept.sortedBy { it.fc }
        return (kept + PeakingFilter(centre, DEFAULT_Q, gain)).sortedBy { it.fc }
    }

    /** Keep stored/custom presets finite, in range, unique by centre, and in the app's correction span. */
    fun sanitize(filters: List<PeakingFilter>): List<PeakingFilter> {
        val byCentre = linkedMapOf<Double, PeakingFilter>()
        for (filter in filters) {
            if (!filter.fc.isFinite() || filter.fc !in 40.0..8_000.0) continue
            if (!filter.q.isFinite() || filter.q !in 0.4..4.0) continue
            if (!filter.gain.isFinite()) continue
            val gain = filter.gain.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            if (abs(gain) < 0.05) {
                byCentre.remove(filter.fc)
            } else {
                byCentre[filter.fc] = filter.copy(gain = gain)
            }
        }
        return byCentre.values.sortedBy { it.fc }
    }

    /** Combined magnitude of the manual peaking filters, in dB. */
    fun responseDb(filters: List<PeakingFilter>, hz: Double): Double {
        if (!hz.isFinite() || hz !in 40.0..8_000.0 || filters.isEmpty()) return 0.0
        var response = 0.0
        for (filter in filters) {
            response += Peaking.peakingMagnitudeDb(doubleArrayOf(hz), filter.fc, filter.q, filter.gain)[0]
        }
        return response
    }

    fun matchingPreset(filters: List<PeakingFilter>, presets: List<ManualEqPreset>): ManualEqPreset? =
        presets.firstOrNull { sameFilters(filters, it.filters) }

    fun sameFilters(first: List<PeakingFilter>, second: List<PeakingFilter>): Boolean {
        val a = sanitize(first)
        val b = sanitize(second)
        if (a.size != b.size) return false
        return a.indices.all { index ->
            abs(a[index].fc - b[index].fc) < 0.01 &&
                abs(a[index].q - b[index].q) < 0.01 &&
                abs(a[index].gain - b[index].gain) < 0.01
        }
    }
}
