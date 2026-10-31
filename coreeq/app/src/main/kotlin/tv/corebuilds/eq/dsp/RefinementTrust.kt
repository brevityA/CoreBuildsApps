package tv.corebuilds.eq.dsp

import kotlin.math.abs

/**
 * Whether a check sweep's refinement is worth offering (1.11.0).
 *
 * [CorrectionRefinement] computes a step whenever the check disagreed with the correction
 * in *shape* — but disagreement is not improvement. A one-sweep loop can propose a change
 * that lowers the overall residual while making the worst band worse, or that moves the
 * RMS by a hundredth of a decibel, which is one capture's worth of weather. Before
 * 1.11.0 every such step was offered on the same button as a proven one; this is the
 * gate between "the check asked" and "the check proved".
 *
 * Three thresholds, each with a reason:
 *
 * 1. **[MEANINGFUL_DB] on the RMS.** The residual must drop by at least this much for
 *    the offer to mean anything; below it, two captures of the same room disagree by
 *    more than the refinement would change.
 * 2. **[CLEAR_DB] is "clearly better"** — the label the UI can give without hedging.
 * 3. **[WORST_TOLERANCE_DB] on the worst band.** An average that improves while the
 *    worst band regresses is a trade, not a fix, and one capture cannot author trades.
 *
 * Withheld verdicts leave the correction exactly as it is; the check's figures still
 * show on screen as the evidence they are. Pure and Android-free: the whole rule is a
 * decision table over two [PostCorrectionCheck.Result]s ([RefinementTrustTest]).
 */
object RefinementTrust {

    /** The smallest RMS improvement worth a button. */
    const val MEANINGFUL_DB = 0.3

    /** From this RMS improvement the offer is "clearly better", not merely "better". */
    const val CLEAR_DB = 0.8

    /** The worst band may not grow by more than this (in magnitude) and still be offered. */
    const val WORST_TOLERANCE_DB = 0.5

    enum class Verdict {
        /** Proven better by the margin: offer it, and say so plainly. */
        CLEARLY_IMPROVED,
        /** Better beyond measurement weather: offer it. */
        IMPROVED,
        /** The residual did not meaningfully drop: nothing to offer. */
        NOT_IMPROVED,
        /** The average improved but the worst band got worse: one capture cannot author that trade. */
        WORST_REGRESSED;

        /** Whether the refinement behind this verdict earns the accept button. */
        val worthwhile: Boolean get() = this == CLEARLY_IMPROVED || this == IMPROVED
    }

    /**
     * The verdict on a refinement whose figures are [before] (what the check heard with
     * the correction as it is) and [after] (what it predicts to leave after the step).
     * Insufficient figures cannot prove improvement, so they withhold.
     */
    fun verdict(before: PostCorrectionCheck.Result, after: PostCorrectionCheck.Result): Verdict {
        val b = before.rmsDb
        val a = after.rmsDb
        if (!before.sufficient || !after.sufficient || b == null || a == null || !b.isFinite() || !a.isFinite()) {
            return Verdict.NOT_IMPROVED
        }
        val worstBefore = before.worstDb
        val worstAfter = after.worstDb
        val regressed = worstBefore != null && worstAfter != null &&
            worstBefore.isFinite() && worstAfter.isFinite() &&
            abs(worstAfter) > abs(worstBefore) + WORST_TOLERANCE_DB
        if (regressed) return Verdict.WORST_REGRESSED
        val gain = b - a
        return when {
            !gain.isFinite() || gain < MEANINGFUL_DB -> Verdict.NOT_IMPROVED
            gain >= CLEAR_DB -> Verdict.CLEARLY_IMPROVED
            else -> Verdict.IMPROVED
        }
    }

    /** The verdict on a refinement [refined] proposes, or null if it carries no figures. */
    fun verdict(refined: CorrectionRefinement.Refined): Verdict = verdict(refined.before, refined.after)
}
