package tv.corebuilds.eq.dsp

import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

/**
 * Why each band was corrected or left alone, and what the measurement says
 * about the room itself.
 *
 * `PostCorrectionCheck` already knows which bands a correction deliberately
 * skips; until now that knowledge only decided whether a band counted towards
 * the check's figures. This turns the same data into the list a user asks for:
 * "63 Hz · deep null · not boosted", "315 Hz · cut 4.2 dB", "5 kHz · above the
 * remote's bandwidth edge". It is derived from a [SweepResult] only, so it can
 * be unit-tested without a device and cannot disagree with what was applied.
 *
 * English is built here rather than in the activity for the same reason
 * [tv.corebuilds.eq.mode.ModeDecision.reason] is: the text is a property of
 * the measurement, not of the layout.
 */
object CorrectionReport {

    /** What happened to one measured band. */
    enum class Kind {
        /** A cut was applied; the room had a peak. */
        CUT,

        /** A boost was applied; the room had a dip the app trusts. */
        BOOST,

        /** Measured gain is inside the noise of the target: nothing to do. */
        CLOSE,

        /** A cancellation deeper than the null threshold: never boosted. */
        NULL,

        /** Excess group delay: correcting would fight the room's timing. */
        PHASE,

        /** Signal too close to the noise floor to trust. */
        NOISY,

        /** Under the speaker's measured roll-off. */
        BELOW_FLOOR,

        /** Above the correction ceiling (the remote's bandwidth edge or the user's choice). */
        ABOVE_CEILING
    }

    data class BandReason(val hz: Double, val kind: Kind, val correctionDb: Double) {
        /** One line for the result card, e.g. "315 Hz · cut 4.2 dB" or "63 Hz · null · not boosted". */
        fun text(): String = when (kind) {
            Kind.CUT -> "${hzText(hz)} · cut ${oneDecimal(abs(correctionDb))} dB"
            Kind.BOOST -> "${hzText(hz)} · boosted ${oneDecimal(correctionDb)} dB"
            Kind.CLOSE -> "${hzText(hz)} · already close to the target"
            Kind.NULL -> "${hzText(hz)} · deep null · not boosted"
            Kind.PHASE -> "${hzText(hz)} · room timing · not corrected"
            Kind.NOISY -> "${hzText(hz)} · too close to the noise floor"
            Kind.BELOW_FLOOR -> "${hzText(hz)} · below the speaker's limit"
            Kind.ABOVE_CEILING -> "${hzText(hz)} · above the correction ceiling"
        }
    }

    /** Bands this correction deliberately left alone, in frequency order. */
    fun skipped(result: SweepResult): List<BandReason> =
        classify(result).filter { !it.kind.appliesGain() }

    /** Bands this correction changed, largest change first. */
    fun corrected(result: SweepResult): List<BandReason> =
        classify(result).filter { it.kind.appliesGain() }
            .sortedByDescending { abs(it.correctionDb) }

    /** Every band with its reason, in frequency order. */
    fun classify(result: SweepResult): List<BandReason> = classifyBands(
        centresHz = result.centresHz,
        correctionDb = result.correctionDb,
        nullMask = result.nullMask,
        minPhaseOk = result.minPhaseOk,
        bandSnrDb = result.bandSnrDb,
        floorHz = result.floorHz,
        fMaxHz = result.fMaxHz
    )

    /**
     * The same report for a REW magnitude import. It carries no group delay and
     * no per-band SNR, so no band is ever called a phase or a noise problem:
     * "not analysed" and "measured and wrong" must not read the same.
     */
    fun classify(imp: ImportResult): List<BandReason> = classifyBands(
        centresHz = imp.centresHz,
        correctionDb = imp.correctionDb,
        nullMask = imp.nullMask,
        minPhaseOk = BooleanArray(0),
        bandSnrDb = DoubleArray(0),
        floorHz = imp.floorHz,
        fMaxHz = imp.fMaxHz
    )

    /**
     * One band's reason. Missing arrays mean "this measurement cannot say",
     * never "this band failed": [bandSnrDb] shorter than the band list used to
     * index out of bounds, and a null SNR is treated as untested.
     */
    private fun classifyBands(
        centresHz: DoubleArray,
        correctionDb: DoubleArray,
        nullMask: BooleanArray,
        minPhaseOk: BooleanArray,
        bandSnrDb: DoubleArray,
        floorHz: Double,
        fMaxHz: Double
    ): List<BandReason> {
        val n = centresHz.size
        val reasons = ArrayList<BandReason>(n)
        for (i in 0 until n) {
            val hz = centresHz[i]
            val gain = if (i < correctionDb.size) correctionDb[i] else 0.0
            val snr = if (i < bandSnrDb.size) bandSnrDb[i] else Double.NaN
            val nulled = i < nullMask.size && nullMask[i]
            val phaseOk = i >= minPhaseOk.size || minPhaseOk[i]
            // The reason describes the gate, not the gain: "why was this band
            // left alone" is answered by what the measurement failed, and a
            // null that is legitimately *cut* still reads as a null rather than
            // as an ordinary correction. The curve and the gate are kept in
            // agreement by the corrector itself (1.7.1), and [gateViolations]
            // is the audit that says so — a reason that disagrees with its gain
            // is a bug in the curve, not in this sentence.
            val kind = when {
                hz > fMaxHz -> Kind.ABOVE_CEILING
                hz < floorHz -> Kind.BELOW_FLOOR
                nulled -> Kind.NULL
                !phaseOk -> Kind.PHASE
                snr.isFinite() && snr < SweepAnalysis.BAND_MIN_SNR_DB -> Kind.NOISY
                abs(gain) < 0.25 -> Kind.CLOSE
                gain > 0.0 -> Kind.BOOST
                else -> Kind.CUT
            }
            reasons.add(BandReason(hz, kind, gain))
        }
        return reasons
    }

    /**
     * Bands where a refusal and the gain actually applied disagree: a null that
     * carries a boost, or a minimum-phase-gated band that carries anything.
     *
     * This is the check that would have caught the 1.7.0 defect, where
     * re-centring lifted zeroed bands back to +2 dB while the result card said
     * "not boosted". [classify] now reports such a band by its sign, so a
     * non-empty result here means the curve itself broke its contract rather
     * than the wording.
     */
    fun gateViolations(result: SweepResult): List<BandReason> =
        classify(result).filter { reason ->
            val i = result.centresHz.indexOfFirst { it == reason.hz }
            if (i < 0) return@filter false
            val nulled = i < result.nullMask.size && result.nullMask[i]
            val phaseOk = i >= result.minPhaseOk.size || result.minPhaseOk[i]
            (nulled && reason.correctionDb > 0.25) || (!phaseOk && abs(reason.correctionDb) > 0.25)
        }

    /** Bands a REW import deliberately left alone, in frequency order. */
    fun skipped(imp: ImportResult): List<BandReason> = classify(imp).filter { !it.kind.appliesGain() }

    /** Bands a REW import changed, largest change first. */
    fun corrected(imp: ImportResult): List<BandReason> =
        classify(imp).filter { it.kind.appliesGain() }.sortedByDescending { abs(it.correctionDb) }

    /** The short form for an import's result card; see the [SweepResult] overload. */
    fun summaryLines(imp: ImportResult, limit: Int = 5): List<String> {
        if (imp.centresHz.isEmpty()) return emptyList()
        return summaryOf(corrected(imp), skipped(imp), imp.fMaxHz, limit)
    }

    /**
     * The short form for the result card: the biggest corrections and the
     * skipped bands that put a limit on the correction, at most [limit] lines.
     *
     * A skipped band only earns a line when it is one of the two lowest
     * skipped frequencies, because a null or a phase problem at 60 Hz and at
     * 63 Hz says the same thing twice. The ceiling is the exception: it is
     * always stated once, because it is the user's own choice.
     */
    fun summaryLines(result: SweepResult, limit: Int = 5): List<String> =
        summaryOf(corrected(result), skipped(result), result.fMaxHz, limit)

    private fun summaryOf(
        corrected: List<BandReason>,
        skipped: List<BandReason>,
        fMaxHz: Double,
        limit: Int
    ): List<String> {
        val lines = corrected.take(limit).map { it.text() }.toMutableList<kotlin.String>()
        // The ceiling is the user's own choice and is stated once, whether or
        // not the low bands also have something to say: it used to be dropped
        // whenever two low-band skips filled the budget first.
        if (skipped.any { it.kind == Kind.ABOVE_CEILING }) {
            lines.add("above ${hzText(fMaxHz)} · not corrected")
        }
        val limits = linkedSetOf<String>()
        for (reason in skipped.sortedBy { it.hz }) {
            val line = when (reason.kind) {
                Kind.NULL, Kind.PHASE, Kind.NOISY, Kind.BELOW_FLOOR -> reason.text()
                else -> null
            } ?: continue
            if (line !in limits) limits.add(line)
            if (limits.size >= 2) break
        }
        return lines + limits
    }

    /**
     * What the measurement says about the room that EQ cannot fix.
     *
     * Only statements the numbers support: a bass-to-mid decay ratio, an
     * overall reverberation time, and a measurement noise floor. Nothing here
     * promises that a further sweep will improve anything.
     */
    fun roomAdvice(result: SweepResult): List<String> {
        val advice = mutableListOf<String>()
        val bass = midOf(result.rt60ByOctave, 125.0, 250.0)
        val mid = midOf(result.rt60ByOctave, 500.0, 1000.0)
        if (bass != null && mid != null && mid > 0.05 && bass / mid >= BASS_RING_RATIO) {
            advice.add(
                "Bass decays ${ratio(bass / mid)} longer than the midrange " +
                    "(${twoDecimals(bass)} s against ${twoDecimals(mid)} s). " +
                    "That is a room and furnishing finding, not something the next sweep fixes."
            )
        }
        val broadband = result.rt60Seconds
        if (broadband != null && broadband > LIVELY_SECONDS) {
            advice.add(
                "The room is lively: ${twoDecimals(broadband)} s reverberation. " +
                    "Soft furnishings and a rug will change it more than any EQ band."
            )
        }
        val snr = result.snrDb
        if (snr.isFinite() && snr < QUIET_SNR_DB) {
            advice.add(
                "The sweep was ${snr.roundToInt()} dB above the noise floor. " +
                    "A quieter room or a louder sweep tightens the bass bands."
            )
        }
        return advice
    }

    /** Median of the octave decays present at [a] or [b]; null when neither was measured. */
    private fun midOf(byOctave: Map<Double, Double?>, a: Double, b: Double): Double? {
        val values = listOfNotNull(byOctave[a], byOctave[b]).filter { it.isFinite() && it > 0.0 }
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[0] + sorted[1]) / 2.0
    }

    private fun Kind.appliesGain(): Boolean = this == Kind.CUT || this == Kind.BOOST

    private fun hzText(hz: Double): String = when {
        hz >= 1000.0 -> trimZero(hz / 1000.0) + " kHz"
        else -> hz.roundToInt().toString() + " Hz"
    }

    private fun trimZero(value: Double): String {
        val text = String.format(Locale.US, "%.1f", value)
        return if (text.endsWith(".0")) text.dropLast(2) else text
    }

    private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
    private fun twoDecimals(value: Double): String = String.format(Locale.US, "%.2f", value)
    private fun ratio(value: Double): String = String.format(Locale.US, "%.1f×", value)

    /** Bass must ring at least this much longer than the mids before it is called out. */
    const val BASS_RING_RATIO = 1.4

    /** Above this reverberation time a room is worth mentioning. */
    const val LIVELY_SECONDS = 0.6

    /** Below this sweep signal-to-noise ratio the bass bands are worth warning about. */
    const val QUIET_SNR_DB = 30.0
}
