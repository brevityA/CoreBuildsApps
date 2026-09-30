package tv.corebuilds.eq.apply

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Log-spaced DynamicsProcessing PreEQ layout over Core EQ's trusted band,
 * followed by a neutral high-frequency guard band.
 *
 * Android's EqBand cutoff is the upper edge of a band, not its centre. The
 * first 23 correction-band cutoffs are therefore half a log-step above their
 * intended centres. A 24th correction band ends exactly at 8 kHz; the 25th
 * band covers above 8 kHz and samples outside Core EQ's correction range, so
 * it receives only the shared headroom attenuation.
 */
data class DpBandSpec(val centerHz: Double, val cutoffHz: Double)

object DpBandLayout {
    const val CORRECTION_BAND_COUNT = 24
    const val BAND_COUNT = CORRECTION_BAND_COUNT + 1
    const val MIN_HZ = 40.0
    const val MAX_HZ = 8_000.0
    private const val LAST_REGULAR_CENTRE_HZ = 6_300.0
    private const val HIGH_GUARD_CUTOFF_HZ = 20_000.0
    private const val REGULAR_CENTRE_COUNT = CORRECTION_BAND_COUNT - 1

    fun specs(): List<DpBandSpec> {
        val ratio = (LAST_REGULAR_CENTRE_HZ / MIN_HZ)
            .pow(1.0 / (REGULAR_CENTRE_COUNT - 1))
        val halfStep = sqrt(ratio)
        val regular = List(REGULAR_CENTRE_COUNT) { index ->
            val center = MIN_HZ * ratio.pow(index.toDouble())
            DpBandSpec(center, center * halfStep)
        }
        val finalCorrectionCentre = sqrt(regular.last().cutoffHz * MAX_HZ)
        val highGuardCentre = sqrt(MAX_HZ * HIGH_GUARD_CUTOFF_HZ)
        return regular +
            DpBandSpec(finalCorrectionCentre, MAX_HZ) +
            DpBandSpec(highGuardCentre, HIGH_GUARD_CUTOFF_HZ)
    }

    /** Reconstruct band centres from the platform's returned upper cutoffs. */
    fun centresFromCutoffs(cutoffsHz: List<Double>): List<Double> {
        require(cutoffsHz.size >= 2) { "DynamicsProcessing needs at least two EQ bands" }
        require(cutoffsHz.all { it.isFinite() && it > 0.0 }) { "EQ cutoffs must be finite and positive" }
        require(cutoffsHz.zipWithNext().all { (a, b) -> b > a }) { "EQ cutoffs must be strictly ascending" }
        val firstStep = cutoffsHz[1] / cutoffsHz[0]
        require(firstStep.isFinite() && firstStep > 1.0) { "EQ cutoff spacing is invalid" }
        return cutoffsHz.indices.map { index ->
            if (index == 0) cutoffsHz[0] / sqrt(firstStep)
            else sqrt(cutoffsHz[index - 1] * cutoffsHz[index])
        }
    }
}
