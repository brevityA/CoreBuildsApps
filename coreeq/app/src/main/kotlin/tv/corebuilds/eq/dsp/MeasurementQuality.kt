package tv.corebuilds.eq.dsp

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How much a sweep's recording can be trusted, from 0 to 100, kept apart from
 * what the recording says about the room (1.3.2).
 *
 * 1.3.0 scored one number from five factors, and two of them (null count, 20
 * points, and transition frequency, 15 points) described the room rather
 * than the recording. A clean capture of an ordinary living room with two
 * wall reflections scored 69: five 1/3-octave dips cost the whole null share.
 * Telling the app the room size lowered the score, because a known size moves
 * the transition from the 300 Hz fallback to the real, higher value. Neither
 * says anything about the microphone, and a better microphone would measure
 * the same dips.
 *
 * So the score is now the recording's only:
 *
 * | Factor             | Points | 0 at                               | Full at        |
 * |--------------------|--------|------------------------------------|----------------|
 * | SNR                | 60     | 20 dB (the analysis refuses below) | 45 dB          |
 * | Decay measurable   | 40     | RT60 not measurable                | RT60 0.1-1.5 s |
 *
 * and the room is reported as [Room], facts with no points: the dips and
 * phase-gated bands the correction leaves alone, and where full correction
 * stops. Shown on the Measure result, Home and Profiles. It describes the
 * measurement only and never changes the correction. REW imports carry no
 * SNR or decay data, so they are not scored rather than scored low.
 */
object MeasurementQuality {

    const val SNR_POINTS = 60
    const val DECAY_POINTS = 40
    private const val SNR_MIN_DB = 20.0
    private const val SNR_IDEAL_DB = 45.0
    private const val RT60_LOW_S = 0.1
    private const val RT60_HIGH_S = 1.5

    /** SNR share: 20 dB is the analysis's own floor (0 points), 45 dB or more is [SNR_POINTS]. */
    fun snrScore(snrDb: Double): Int {
        if (snrDb <= SNR_MIN_DB) return 0
        if (snrDb >= SNR_IDEAL_DB) return SNR_POINTS
        return (SNR_POINTS * (snrDb - SNR_MIN_DB) / (SNR_IDEAL_DB - SNR_MIN_DB)).toInt()
    }

    /**
     * Decay share: a decay the noise-compensated T20 could fit at all means
     * the recording's tail stood clear of the noise. Null (not measurable)
     * scores 0; an RT60 outside 0.1-1.5 s loses points with its distance from
     * that range, since a figure like that more often comes from a capture
     * problem than from a living room.
     */
    fun decayScore(rt60Seconds: Double?): Int {
        if (rt60Seconds == null) return 0
        if (rt60Seconds in RT60_LOW_S..RT60_HIGH_S) return DECAY_POINTS
        val deviation = if (rt60Seconds < RT60_LOW_S) RT60_LOW_S - rt60Seconds else rt60Seconds - RT60_HIGH_S
        // Rounded, not truncated: 1.7 - 1.5 is 0.19999... in binary.
        return max(0, DECAY_POINTS - (deviation * 2 * DECAY_POINTS).roundToInt())
    }

    /** The recording score from the two figures a sweep profile also saves (snrDb, rt60Seconds). */
    fun recordingScore(snrDb: Double, rt60Seconds: Double?): Int =
        (snrScore(snrDb) + decayScore(rt60Seconds)).coerceIn(0, 100)

    fun score(result: SweepResult): Int = recordingScore(result.snrDb, result.rt60Seconds)

    /**
     * What the sweep found about the room, with no points: [dips] are nulls
     * the correction leaves alone, [phaseGatedBands] are bands below
     * [transitionHz] that failed the minimum-phase gate and are also left
     * alone, and [transitionHz] is where full correction gives way to gentle
     * shaping.
     */
    data class Room(val dips: Int, val phaseGatedBands: Int, val transitionHz: Double)

    fun room(result: SweepResult): Room = Room(
        dips = result.nullMask.count { it },
        phaseGatedBands = result.centresHz.indices.count {
            !result.minPhaseOk[it] && result.centresHz[it] < result.transitionHz
        },
        transitionHz = result.transitionHz
    )

    /** The word the UI shows beside the score (a string resource per grade). */
    fun grade(score: Int): Grade = when {
        score >= 85 -> Grade.EXCELLENT
        score >= 70 -> Grade.GOOD
        score >= 50 -> Grade.FAIR
        score >= 30 -> Grade.POOR
        else -> Grade.REMEASURE
    }

    /** Colour hint for the UI: green, amber, or red. */
    fun tier(score: Int): Tier = when {
        score >= 70 -> Tier.GREEN
        score >= 45 -> Tier.AMBER
        else -> Tier.RED
    }

    enum class Tier { GREEN, AMBER, RED }

    enum class Grade { EXCELLENT, GOOD, FAIR, POOR, REMEASURE }
}
