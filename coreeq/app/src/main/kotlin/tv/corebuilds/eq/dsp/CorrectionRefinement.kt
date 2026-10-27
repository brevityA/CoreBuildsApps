package tv.corebuilds.eq.dsp

import kotlin.math.abs
import kotlin.math.min

/**
 * A correction improved by the sweep that was played through it (1.8.0).
 *
 * [PostCorrectionCheck] gives three figures — the room, the residual the correction should
 * leave, and what a check sweep heard — and stops there. This is the other half: the
 * *difference* between the predicted and the heard residual says, band by band, which way the
 * correction was wrong. Where the room came out louder than predicted, the correction
 * under-cut; where it came out quieter, it under-lifted. Adding that difference back is a
 * closed loop: measure, correct, re-measure through the correction, adjust.
 *
 * It is deliberately a *small* loop, and every limit here exists because one check sweep is
 * one capture at one position:
 *
 * 1. **Only bands both sweeps can speak for.** A band the correction was not allowed to touch
 *    (a null, a non-minimum-phase band below the transition, past the bandwidth edge, under
 *    the noise) says nothing about whether the correction was right, and a band the check did
 *    not hear over its own noise says nothing at all. [PostCorrectionCheck.sharedSkip] is the
 *    same mask the figures on screen use.
 * 2. **Shape, not level.** A check sweep is taken at another moment and possibly another
 *    volume, and its own alignment to the target over 300 Hz-3 kHz leaves a constant behind
 *    that could equally be the correction being a fraction loud or the alignment being a
 *    fraction off. The weighted mean of the wanted change is removed, so a small global
 *    difference moves nothing; only a difference in *shape* does.
 * 3. **Trust is graded, again.** The step is scaled by the weaker of the two sweeps' per-band
 *    confidence ([Correction.bandConfidence]), so a band measured at 12 dB over the noise two
 *    sweeps running is refined by a fifth of what a clean band gets.
 * 4. **Never a new direction.** A band the correction left positive may only be reduced toward
 *    zero, and a band it cut may only be reduced toward zero: refinement cannot turn a cut into
 *    a boost on the strength of one more capture. A band that was left at 0 dB stays there.
 * 5. **A lift needs a deficit the check itself heard** at least
 *    [DspConstants.REFINE_MIN_DEFICIT_DB] deep, and must not sit on a cancellation the check
 *    found: the same "do not fill a null" rule that governs the first measurement.
 * 6. **The correction's own budget still applies**, through
 *    [Correction.budgetEnvelope] — the same function the curve itself is clamped by, so the
 *    refined curve can never ask for more bass below the transition, or more shaping above it,
 *    than the original was allowed.
 * 7. **One band moves at most [DspConstants.REFINE_MAX_STEP_DB]**, the slope limit is applied
 *    to the change as well, and a step under [DspConstants.REFINE_MIN_APPLIED_DB] is dropped
 *    rather than spending a filter on it.
 *
 * The result is a [SweepResult] that differs from its input only in the correction, the filters
 * fitted to it and the preamp that follows from them. The measurement, the target, the nulls,
 * the gates and the confidence are the original capture's: the room was not measured again.
 * Descriptive and reversible — the result replaces the correction only when the user accepts
 * it, and the saved profile is whatever the screen shows.
 */
object CorrectionRefinement {

    /**
     * [stepDb] is what was added to each band's correction (0 dB where a band was left alone);
     * [before] and [after] are the residual the correction was predicted to leave and the one
     * this refinement predicts. [heldBack] counts the bands whose evidence asked for a move the
     * rules above refused, which is what makes the difference between "the check agreed" and
     * "the check disagreed and was not acted on".
     */
    data class Refined(
        val result: SweepResult,
        val stepDb: DoubleArray,
        val heldBack: Int,
        val before: PostCorrectionCheck.Result,
        val after: PostCorrectionCheck.Result
    ) {
        /** Bands the refinement moved. */
        val changed: Int get() = stepDb.count { it != 0.0 }
        val raised: Int get() = stepDb.count { it > 0.0 }
        val lowered: Int get() = stepDb.count { it < 0.0 }
        val largestDb: Double get() = stepDb.maxOfOrNull { abs(it) } ?: 0.0
    }

    /**
     * [base] is the measurement the correction belongs to, [check] the sweep that was played
     * through that same correction. [boostBudgetDb] is the bass budget the correction itself
     * used ([Correction.boostBudgetDb] of the seats behind it). Null when the two sweeps cannot
     * be compared (different bands, too few of them, or nothing the correction may touch).
     */
    fun refine(
        base: SweepResult,
        check: SweepResult,
        boostBudgetDb: Double = Correction.boostBudgetDb(1),
        nFilters: Int = DspConstants.PEAKING_FILTERS
    ): Refined? {
        // The bands both sweeps have, as PostCorrectionCheck.compare does: a check analysed past the
        // average seat's bandwidth edge is still usable on the bands they share.
        val n = minOf(base.centresHz.size, check.centresHz.size)
        if (n < PostCorrectionCheck.MIN_BANDS) return null
        if (base.measuredDb.size < n || base.correctionDb.size < n || base.targetDb.size < n) return null
        if (check.measuredDb.size < n) return null
        for (k in 0 until n) {
            if (abs(base.centresHz[k] - check.centresHz[k]) > 1e-6) return null
        }
        val centres = base.centresHz.copyOf(n)
        val target = base.targetDb.copyOf(n)
        val skip = PostCorrectionCheck.sharedSkip(base, check, n)
        // The check on the target's level, aligned exactly as the comparison on screen aligns it.
        val heard = PostCorrectionCheck.align(centres, check.measuredDb.copyOf(n), target) ?: return null

        // 1. Evidence: how far the room came out from what the correction predicted, per band.
        //    Positive means the room is quieter than predicted — the correction needs more gain.
        val want = DoubleArray(n)
        val weight = DoubleArray(n)
        var weightedSum = 0.0
        var weightSum = 0.0
        for (k in 0 until n) {
            if (skip[k] || base.correctionDb[k] == 0.0) continue
            val predicted = base.measuredDb[k] + base.correctionDb[k] - target[k]
            val actual = heard[k] - target[k]
            if (!predicted.isFinite() || !actual.isFinite()) continue
            // The weaker sweep sets the band's trust, exactly as SeatAverage does across seats.
            val w = min(
                base.confidence.getOrElse(k) { 1.0 },
                check.confidence.getOrElse(k) { 1.0 }
            ).coerceIn(0.0, 1.0)
            if (w <= 0.0) continue
            want[k] = predicted - actual
            weight[k] = w
            weightedSum += want[k] * w
            weightSum += w
        }
        if (weightSum <= 0.0) return null

        // 2. Shape, not level: remove the weighted mean of what the check asked for.
        val mean = weightedSum / weightSum

        // 3. Per-band step, before the slope limit and the rules that cannot be arithmetic.
        val raw = DoubleArray(n)
        for (k in 0 until n) {
            if (weight[k] <= 0.0) continue
            val step = (want[k] - mean) * weight[k]
            if (!step.isFinite()) continue
            raw[k] = step.coerceIn(-DspConstants.REFINE_MAX_STEP_DB, DspConstants.REFINE_MAX_STEP_DB)
        }
        // No narrow feature becomes a ringing filter: the change is slope-limited like a curve.
        val limited = Correction.limitSlope(centres, raw)

        val envelope = Correction.budgetEnvelope(
            centres,
            base.transitionHz,
            maxBoostBelowTransition = boostBudgetDb
        )
        val curve = base.correctionDb.copyOf()
        val stepDb = DoubleArray(n)
        var heldBack = 0
        for (k in 0 until n) {
            val c = base.correctionDb[k]
            if (weight[k] <= 0.0 || c == 0.0) continue
            val hz = centres[k]
            if (hz < base.floorHz || hz > base.fMaxHz) continue
            // A band the correction itself already sits outside the budget for is left alone:
            // clamping it here would move it without evidence.
            if (c < envelope.floorDb[k] - 1e-9 || c > envelope.ceilingDb[k] + 1e-9) continue
            var step = limited[k]
            if (!step.isFinite()) continue
            // A lift needs a deficit the check heard itself, and no cancellation there.
            if (step > 0.0) {
                val checkNull = k < check.nullMask.size && check.nullMask[k]
                if (checkNull || heard[k] - target[k] > -DspConstants.REFINE_MIN_DEFICIT_DB) step = 0.0
            }
            if (abs(step) < DspConstants.REFINE_MIN_APPLIED_DB) {
                if (abs(want[k] - mean) * weight[k] >= DspConstants.REFINE_MIN_DEFICIT_DB) heldBack++
                continue
            }
            var refined = c + step
            // Never a new direction: a cut may relax to zero, never become a boost, and vice versa.
            if (c > 0.0 && refined < 0.0) refined = 0.0
            if (c < 0.0 && refined > 0.0) refined = 0.0
            refined = refined.coerceIn(envelope.floorDb[k], envelope.ceilingDb[k])
            // One step, whatever the envelope allows: the budget is a ceiling on the curve, not
            // a licence to move a band further than one check sweep can justify.
            refined = refined.coerceIn(c - DspConstants.REFINE_MAX_STEP_DB, c + DspConstants.REFINE_MAX_STEP_DB)
            if (c > 0.0 && refined < 0.0) refined = 0.0
            if (c < 0.0 && refined > 0.0) refined = 0.0
            stepDb[k] = refined - c
            if (stepDb[k] == 0.0) heldBack++
            curve[k] = refined
        }
        if (stepDb.all { it == 0.0 }) return null

        val filters = Peaking.fitPeakingFilters(centres, curve.copyOf(n), nFilters)
        val result = base.copy(
            correctionDb = curve,
            filters = filters,
            preampDb = Peaking.preampDb(filters)
        )
        // Measured from what the check *heard*, not from the original measurement: once the
        // two disagree, the older curve no longer describes the room, and `heard + step` is
        // the residual this refinement expects to leave. The "before" figure is therefore the
        // same check residual the result card already shows.
        val before = PostCorrectionCheck.residual(centres, heard, target, skip)
        val after = PostCorrectionCheck.residual(
            centres, DoubleArray(n) { heard[it] + curve[it] - base.correctionDb[it] }, target, skip
        )
        return Refined(result, stepDb, heldBack, before, after)
    }
}
