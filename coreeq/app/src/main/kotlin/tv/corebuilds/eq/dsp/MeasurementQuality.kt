package tv.corebuilds.eq.dsp

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
 * So the score is now the recording's only, and it is one figure: the
 * impulse response's peak-to-noise ratio (the `snrDb` the analysis already
 * refuses below 20 dB), mapped linearly from 20 dB (0) to 50 dB (100).
 *
 * Why SNR alone. The #270 review and a synthetic-room audit (pink noise,
 * modes, a strong reflection, 12 seeds per cell) showed what the band levels
 * the correction is built from actually track: the RMS error of the bass
 * bands against the true response was about 0.5 dB at 55 dB SNR, under 1 dB
 * at 45, 1-2.8 dB at 35 and 2-8 dB at 25. So 45 dB scores 83 (Good), 35 dB
 * 50 (Fair) and 25 dB 17 (Remeasure), which is what those errors mean for
 * the EQ. 50 dB is about what a voice remote's codec allows at best (the
 * audit measured 50.5 dB through 8 kHz ADPCM and 53.7 dB at 16 kHz), so
 * Excellent is reachable in a quiet room.
 *
 * The decay share is gone. Through 1.3.2 drafts it gave 40 points to any
 * RT60 the T20 fit returned, and the fit returned one in 36 of 36 runs at
 * 35 dB SNR, reading 22-39 % short: points for nothing. RT60 is a room fact
 * (it sets where full correction stops) and is reported with the room.
 *
 * The room is reported as [Room], facts with no points: the dips and
 * phase-gated bands the correction leaves alone, and where full correction
 * stops. Shown on the Measure result, Home and Profiles. It describes the
 * measurement only and never changes the correction. REW imports carry no
 * SNR data, so they are not scored rather than scored low.
 */
object MeasurementQuality {

    private const val SNR_MIN_DB = 20.0
    private const val SNR_FULL_DB = 50.0

    /** The recording score: 20 dB SNR (the analysis's own floor) is 0, 50 dB or more is 100. */
    fun recordingScore(snrDb: Double): Int {
        if (snrDb <= SNR_MIN_DB) return 0
        if (snrDb >= SNR_FULL_DB) return 100
        return (100.0 * (snrDb - SNR_MIN_DB) / (SNR_FULL_DB - SNR_MIN_DB)).toInt()
    }

    fun score(result: SweepResult): Int = recordingScore(result.snrDb)

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
