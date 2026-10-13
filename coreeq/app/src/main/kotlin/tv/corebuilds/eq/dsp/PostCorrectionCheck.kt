package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.CorrectionLimits
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Descriptive residual after a correction: how far a re-measurement sits from the target.
 *
 * Only bands inside the trusted 40 Hz to 8 kHz window count, because nothing outside it is
 * corrected or trusted. The result is a description, not a pass/fail verdict, and it is
 * withheld when there are too few bands to say anything. Both arrays must already be
 * aligned to the same centres.
 */
object PostCorrectionCheck {
    const val MIN_BANDS = 8

    data class Result(
        val bands: Int,
        val rmsDb: Double?,
        val worstHz: Double?,
        val worstDb: Double?
    ) {
        /** True when enough trusted bands were measured to report a residual. */
        val sufficient: Boolean get() = bands >= MIN_BANDS
    }

    fun residual(centres: DoubleArray, measuredDb: DoubleArray, targetDb: DoubleArray): Result {
        require(centres.size == measuredDb.size && centres.size == targetDb.size) {
            "centres, measured and target must be the same length"
        }
        var count = 0
        var sumSquares = 0.0
        var worstHz: Double? = null
        var worstDb = 0.0
        for (k in centres.indices) {
            val hz = centres[k]
            if (!CorrectionLimits.isCorrectable(hz)) continue
            val diff = measuredDb[k] - targetDb[k]
            if (!diff.isFinite()) continue
            count++
            sumSquares += diff * diff
            if (worstHz == null || abs(diff) > abs(worstDb)) {
                worstHz = hz
                worstDb = diff
            }
        }
        if (count == 0) return Result(0, null, null, null)
        return Result(count, sqrt(sumSquares / count), worstHz, worstDb)
    }
}
