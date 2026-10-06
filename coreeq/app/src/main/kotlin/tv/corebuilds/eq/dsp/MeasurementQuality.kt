package tv.corebuilds.eq.dsp

import kotlin.math.max
import kotlin.math.min

/**
 * A composite measurement quality score from 0 to 100, so the user can
 * tell at a glance whether their sweep was good enough.
 *
 * The score weights five factors that the analysis already computes:
 *
 * | Factor                  | Weight | Rationale                                    |
 * |-------------------------|--------|----------------------------------------------|
 * | SNR                     | 30     | A quiet room and a loud sweep are the single |
 * |                         |        | biggest predictor of a trustworthy result.   |
 * | RT60 plausibility       | 20     | A measurable decay means the room was not    |
 * |                         |        | too noisy for the analysis to work.          |
 * | Null count              | 20     | Fewer nulls means a simpler room with more   |
 * |                         |        | correctable response.                        |
 * | Min-phase gates passed  | 15     | More bands passing means more of the low     |
 * |                         |        | end is safe to correct.                      |
 * | Transition frequency    | 15     | A lower transition means more of the bass    |
 * |                         |        | range gets full inversion rather than        |
 * |                         |        | shaping-only.                                |
 *
 * The weights sum to 100 and the result is a percentage.
 */
object MeasurementQuality {

    /** SNR score: 20 dB is the minimum (0 points), 45+ dB is perfect (30 points). */
    fun snrScore(snrDb: Double): Int {
        val min = 20.0
        val ideal = 45.0
        if (snrDb <= min) return 0
        if (snrDb >= ideal) return 30
        return (30.0 * (snrDb - min) / (ideal - min)).toInt()
    }

    /**
     * RT60 plausibility score: a measured RT60 between 0.1 and 1.5 s
     * is ideal (20 points). A null RT60 (too noisy to measure) scores 0.
     * Values outside the plausible range lose points proportionally.
     */
    fun rt60Score(rt60Seconds: Double?): Int {
        if (rt60Seconds == null) return 0
        if (rt60Seconds in 0.1..1.5) return 20
        // Penalise but don't zero out for slightly unusual rooms
        val deviation = if (rt60Seconds < 0.1) 0.1 - rt60Seconds else rt60Seconds - 1.5
        return max(0, 20 - (deviation * 40).toInt())
    }

    /**
     * Null count score: 0 nulls is perfect (20 points), 5+ nulls is 0.
     * Nulls are room cancellations that cannot be corrected; fewer means
     * more of the response is addressable.
     */
    fun nullScore(nullCount: Int): Int {
        if (nullCount <= 0) return 20
        if (nullCount >= 5) return 0
        return 20 - (nullCount * 4)
    }

    /**
     * Minimum-phase gate score: the fraction of bands below the transition
     * that passed the gate. All passing = 15 points.
     */
    fun minPhaseScore(bandsBelowTransition: Int, bandsGated: Int): Int {
        if (bandsBelowTransition <= 0) return 15
        val passed = bandsBelowTransition - bandsGated
        return (15.0 * passed / bandsBelowTransition).toInt()
    }

    /**
     * Transition frequency score: 300 Hz (the fallback) scores 5 points;
     * 150 Hz or lower scores 15 points (more of the bass gets full correction).
     */
    fun transitionScore(transitionHz: Double): Int {
        if (transitionHz <= 150.0) return 15
        if (transitionHz >= 400.0) return 5
        // Linear between 150 and 400
        return 15 - ((transitionHz - 150.0) / 250.0 * 10.0).toInt()
    }

    /**
     * Composite score from a SweepResult.
     */
    fun score(result: SweepResult): Int {
        val nullCount = result.nullMask.count { it }
        val bandsBelowTransition = result.centresHz.indices.count {
            result.centresHz[it] < result.transitionHz
        }
        val bandsGated = result.centresHz.indices.count {
            !result.minPhaseOk[it] && result.centresHz[it] < result.transitionHz
        }
        val total = snrScore(result.snrDb) +
            rt60Score(result.rt60Seconds) +
            nullScore(nullCount) +
            minPhaseScore(bandsBelowTransition, bandsGated) +
            transitionScore(result.transitionHz)
        return total.coerceIn(0, 100)
    }

    /** Human-readable label for the score. */
    fun label(score: Int): String = when {
        score >= 85 -> "Excellent"
        score >= 70 -> "Good"
        score >= 50 -> "Fair"
        score >= 30 -> "Poor"
        else -> "Re-measure"
    }

    /** Colour hint for the UI: green, amber, or red. */
    fun tier(score: Int): Tier = when {
        score >= 70 -> Tier.GREEN
        score >= 45 -> Tier.AMBER
        else -> Tier.RED
    }

    enum class Tier { GREEN, AMBER, RED }
}
