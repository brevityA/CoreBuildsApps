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

    /** Where a measurement is put on the target's level, as [SweepAnalysis] aligns a sweep. */
    const val ALIGN_LO_HZ = 300.0
    const val ALIGN_HI_HZ = 3000.0

    /**
     * [residual] after moving [measuredDb] onto the target's level over 300 Hz-3 kHz. A sweep
     * taken with the correction on is quieter by the preamp and wherever it cuts, and its
     * absolute level depends on the volume, so only its shape against the target means
     * anything. Null when no band falls in that window.
     */
    fun alignedResidual(
        centres: DoubleArray,
        measuredDb: DoubleArray,
        targetDb: DoubleArray,
        skip: BooleanArray? = null
    ): Result? = align(centres, measuredDb, targetDb)?.let { residual(centres, it, targetDb, skip) }

    /** [measuredDb] moved onto [targetDb]'s level over 300 Hz-3 kHz; null with no band there. */
    fun align(centres: DoubleArray, measuredDb: DoubleArray, targetDb: DoubleArray): DoubleArray? {
        val window = centres.indices.filter {
            centres[it] in ALIGN_LO_HZ..ALIGN_HI_HZ && measuredDb[it].isFinite() && targetDb[it].isFinite()
        }
        if (window.isEmpty()) return null
        val offset = window.sumOf { targetDb[it] - measuredDb[it] } / window.size
        return DoubleArray(measuredDb.size) { measuredDb[it] + offset }
    }

    /**
     * Bands a correction made from [r] was not allowed to touch: nulls, below the roll-off,
     * past the capture's bandwidth edge, non-minimum-phase bass and bands under the noise.
     * Their distance from the target is what the correction chose to leave, not what it missed.
     */
    fun leftAlone(r: SweepResult): BooleanArray = BooleanArray(r.centresHz.size) { k ->
        val hz = r.centresHz[k]
        r.nullMask[k] || hz < r.floorHz || hz > r.fMaxHz || !r.minPhaseOk[k] ||
            r.bandSnrDb.getOrElse(k) { Double.MAX_VALUE } < SweepAnalysis.BAND_MIN_SNR_DB
    }

    /**
     * One correction, three ways, over the same bands: [before] is the room it was made from,
     * [predicted] that room plus the correction curve, and [after] a sweep played through it.
     * [afterDb] is that sweep on the target's level, for drawing.
     */
    data class Comparison(
        val before: Result,
        val predicted: Result,
        val after: Result,
        val afterDb: DoubleArray
    )

    /**
     * [made] is the analysis a correction came from; [check] a sweep from the same position
     * chain with that correction playing, analysed for the same target. Only bands the
     * correction was allowed to touch, and that the check heard over its noise, count, so the
     * three figures describe the same bands. Null when the two were analysed on different
     * bands or there are too few to say anything.
     */
    fun compare(made: SweepResult, check: SweepResult): Comparison? {
        val n = minOf(made.centresHz.size, check.centresHz.size)
        if ((0 until n).any { abs(made.centresHz[it] - check.centresHz[it]) > 1e-6 }) return null
        val centres = made.centresHz.copyOf(n)
        val target = made.targetDb.copyOf(n)
        val untouched = leftAlone(made)
        val skip = BooleanArray(n) { k ->
            untouched[k] || check.bandSnrDb.getOrElse(k) { Double.MAX_VALUE } < SweepAnalysis.BAND_MIN_SNR_DB
        }
        val room = made.measuredDb.copyOf(n)
        val corrected = DoubleArray(n) { room[it] + made.correctionDb[it] }
        val afterDb = align(centres, check.measuredDb.copyOf(n), target) ?: return null
        val before = alignedResidual(centres, room, target, skip) ?: return null
        val predicted = alignedResidual(centres, corrected, target, skip) ?: return null
        val after = residual(centres, afterDb, target, skip)
        if (!before.sufficient || !after.sufficient) return null
        return Comparison(before, predicted, after, afterDb)
    }

    /**
     * [skip] leaves bands out of the figures: the dips the correction deliberately left alone
     * (room nulls) are not a residual it failed to remove.
     */
    fun residual(
        centres: DoubleArray,
        measuredDb: DoubleArray,
        targetDb: DoubleArray,
        skip: BooleanArray? = null
    ): Result {
        require(centres.size == measuredDb.size && centres.size == targetDb.size) {
            "centres, measured and target must be the same length"
        }
        require(skip == null || skip.size == centres.size) { "skip must match the centres" }
        var count = 0
        var sumSquares = 0.0
        var worstHz: Double? = null
        var worstDb = 0.0
        for (k in centres.indices) {
            val hz = centres[k]
            if (!CorrectionLimits.isCorrectable(hz)) continue
            if (skip != null && skip[k]) continue
            val diff = measuredDb[k] - targetDb[k]
            if (!diff.isFinite()) continue
            count++
            sumSquares += diff * diff
            if (worstHz == null || abs(diff) > abs(worstDb)) {
                worstHz = hz
                worstDb = diff
            }
        }
        // Below MIN_BANDS the figures would describe a handful of bands as if they were the
        // room, so only the count goes back.
        if (count < MIN_BANDS) return Result(count, null, null, null)
        return Result(count, sqrt(sumSquares / count), worstHz, worstDb)
    }
}
