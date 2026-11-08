package tv.corebuilds.eq.dsp

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Combines a baseline sweep with a same-position repeat using compact,
 * per-band evidence rather than raw audio.
 *
 * The average is memory-efficient: it consumes the 1/3-octave magnitude,
 * SNR, phase-gate and confidence arrays already produced by [SweepAnalysis].
 * It never retains PCM, an impulse response, or an extra full-length FFT
 * workspace.
 *
 * The repeat is aligned to the reference with a robust median offset over
 * 500 Hz-2 kHz. Each band is then averaged in the power domain, weighted by
 * measurement confidence. Agreement between the sweeps reduces confidence;
 * it never turns a poor band into a trusted one.
 */
object AdaptiveRepeatAverage {
    /** Bands used to remove a small capture-level difference between sweeps. */
    const val ALIGN_LOW_HZ = 500.0
    const val ALIGN_HIGH_HZ = 2_000.0
    const val MIN_ALIGNMENT_BANDS = 3

    /** RMS band difference above which the repeat is treated as weak evidence. */
    const val DISAGREEMENT_RMS_DB = 3.0
    /** Confidence floor applied when the two sweeps disagree materially. */
    const val MIN_AGREEMENT_FACTOR = 0.25

    data class Averaged(
        val result: SweepResult,
        val levelOffsetDb: Double,
        val agreementRmsDb: Double,
        val usableBands: Int,
        val note: String?
    )

    fun average(
        reference: SweepResult,
        repeat: SweepResult,
        target: String,
        volumeM3: Double?,
        nFilters: Int = DspConstants.PEAKING_FILTERS
    ): Averaged {
        requireValid(reference, "reference")
        requireValid(repeat, "repeat")

        val n = min(reference.centresHz.size, repeat.centresHz.size)
        require(n > 0) { "the sweeps have no shared bands" }
        for (k in 0 until n) {
            require(abs(reference.centresHz[k] - repeat.centresHz[k]) < 1e-6) {
                "the sweeps were analysed on different frequency grids"
            }
        }

        val referenceSnr = snrAt(reference, n)
        val repeatSnr = snrAt(repeat, n)
        val referenceConfidence = confidenceAt(reference, n)
        val repeatConfidence = confidenceAt(repeat, n)

        val offsetDb = levelOffsetDb(
            reference.centresHz,
            reference.measuredDb,
            repeat.measuredDb,
            referenceSnr,
            repeatSnr
        )

        val measured = DoubleArray(n)
        val bandSnr = DoubleArray(n)
        val confidence = DoubleArray(n)
        val gate = BooleanArray(n)
        var usable = 0
        var squaredDifference = 0.0

        for (k in 0 until n) {
            val referenceDb = reference.measuredDb[k]
            val repeatDb = repeat.measuredDb[k] + offsetDb
            val rSnr = referenceSnr[k]
            val pSnr = repeatSnr[k]
            val rConfidence = referenceConfidence[k]
            val pConfidence = repeatConfidence[k]

            val clean = referenceDb.isFinite() && repeatDb.isFinite() &&
                rSnr >= SweepAnalysis.BAND_MIN_SNR_DB && pSnr >= SweepAnalysis.BAND_MIN_SNR_DB
            gate[k] = reference.phaseGate.getOrElse(k) { reference.minPhaseOk[k] } &&
                repeat.phaseGate.getOrElse(k) { repeat.minPhaseOk[k] }

            if (!clean) {
                measured[k] = if (referenceDb.isFinite()) referenceDb else repeatDb
                bandSnr[k] = min(rSnr, pSnr)
                confidence[k] = 0.0
                continue
            }

            val rWeight = max(rConfidence * rConfidence, 1e-6)
            val pWeight = max(pConfidence * pConfidence, 1e-6)
            val referencePower = powerFromDb(referenceDb)
            val repeatPower = powerFromDb(repeatDb)
            measured[k] = dbFromPower(
                (rWeight * referencePower + pWeight * repeatPower) / (rWeight + pWeight)
            )
            bandSnr[k] = min(rSnr, pSnr)

            val difference = repeatDb - referenceDb
            squaredDifference += difference * difference
            usable++

            val agreement = agreementFactor(abs(difference))
            confidence[k] = min(rConfidence, pConfidence) * agreement
        }

        val agreementRmsDb = if (usable > 0) sqrt(squaredDifference / usable) else Double.NaN
        val agreementFactor = if (agreementRmsDb.isFinite()) {
            agreementFactor(agreementRmsDb)
        } else {
            MIN_AGREEMENT_FACTOR
        }

        val rt60s = listOfNotNull(reference.rt60Seconds, repeat.rt60Seconds)
        val rt60 = if (rt60s.isEmpty()) null else rt60s.average()
        val transition = Correction.transitionHz(volumeM3, rt60)
        val schroeder = if (volumeM3 != null && rt60 != null) {
            Correction.schroederHz(volumeM3, rt60)
        } else {
            null
        }

        val nulls = Correction.detectNulls(reference.centresHz.copyOf(n), measured)
        val rolloff = Correction.detectLowRolloff(reference.centresHz.copyOf(n), measured)
        val floorHz = max(DspConstants.F_MIN, rolloff)
        val fMax = min(reference.fMaxHz, repeat.fMaxHz)
        val minPhaseOk = BooleanArray(n) { gate[it] || reference.centresHz[it] >= transition }
        val correctable = BooleanArray(n) {
            minPhaseOk[it] && bandSnr[it] >= SweepAnalysis.BAND_MIN_SNR_DB
        }

        val targetDb = Targets.targetCurve(target, reference.centresHz.copyOf(n))
        val correction = Correction.calculateCorrectionCurve(
            reference.centresHz.copyOf(n),
            measured,
            targetDb,
            fMin = floorHz,
            fMax = fMax,
            transitionHz = transition,
            nullMask = nulls,
            minPhaseOk = correctable,
            maxBoostBelowTransition = Correction.boostBudgetDb(2),
            confidence = confidence
        )
        val filters = Peaking.fitPeakingFilters(
            reference.centresHz.copyOf(n),
            correction,
            nFilters
        )

        val note = when {
            usable < MIN_ALIGNMENT_BANDS ->
                "Too few clean shared bands to assess repeat agreement."
            agreementRmsDb > DISAGREEMENT_RMS_DB ->
                "The repeat differs from the first sweep; its influence is reduced."
            else -> null
        }

        return Averaged(
            result = SweepResult(
                centresHz = reference.centresHz.copyOf(n),
                measuredDb = measured,
                targetDb = targetDb,
                correctionDb = correction,
                nullMask = nulls,
                minPhaseOk = minPhaseOk,
                rt60Seconds = rt60,
                schroederHz = schroeder,
                transitionHz = transition,
                rolloffHz = rolloff,
                floorHz = floorHz,
                snrDb = min(reference.snrDb, repeat.snrDb),
                latencyMs = reference.latencyMs,
                filters = filters,
                preampDb = Peaking.preampDb(filters),
                bandSnrDb = bandSnr,
                rt60ByOctave = averageOctaves(reference, repeat),
                driftPpm = reference.driftPpm,
                fMaxHz = fMax,
                phaseGate = gate,
                confidence = confidence
            ),
            levelOffsetDb = offsetDb,
            agreementRmsDb = agreementRmsDb,
            usableBands = usable,
            note = note
        )
    }

    private fun requireValid(result: SweepResult, label: String) {
        require(result.centresHz.isNotEmpty()) { "$label has no measurement bands" }
        require(result.centresHz.size == result.measuredDb.size) {
            "$label has mismatched magnitude and frequency arrays"
        }
    }

    private fun snrAt(result: SweepResult, n: Int): DoubleArray =
        DoubleArray(n) { k ->
            result.bandSnrDb.getOrElse(k) { result.snrDb }
        }

    private fun confidenceAt(result: SweepResult, n: Int): DoubleArray =
        DoubleArray(n) { k ->
            result.confidence.getOrElse(k) {
                Correction.bandConfidence(
                    result.bandSnrDb.getOrElse(k) { result.snrDb }
                )
            }
        }

    private fun levelOffsetDb(
        centres: DoubleArray,
        referenceDb: DoubleArray,
        repeatDb: DoubleArray,
        referenceSnr: DoubleArray,
        repeatSnr: DoubleArray
    ): Double {
        val differences = centres.indices
            .filter {
                centres[it] in ALIGN_LOW_HZ..ALIGN_HIGH_HZ &&
                    referenceSnr[it] >= SweepAnalysis.BAND_MIN_SNR_DB &&
                    repeatSnr[it] >= SweepAnalysis.BAND_MIN_SNR_DB &&
                    referenceDb[it].isFinite() && repeatDb[it].isFinite()
            }
            .map { referenceDb[it] - repeatDb[it] }

        if (differences.size < MIN_ALIGNMENT_BANDS) return 0.0
        val sorted = differences.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private fun agreementFactor(differenceDb: Double): Double {
        if (!differenceDb.isFinite()) return MIN_AGREEMENT_FACTOR
        val excess = max(0.0, differenceDb - DISAGREEMENT_RMS_DB)
        return max(MIN_AGREEMENT_FACTOR, 1.0 - excess / 6.0)
    }

    private fun powerFromDb(db: Double): Double = 10.0.pow(db / 10.0)

    private fun dbFromPower(power: Double): Double =
        10.0 * log10(max(power, 1e-20))

    private fun averageOctaves(
        reference: SweepResult,
        repeat: SweepResult
    ): Map<Double, Double?> = SweepAnalysis.RT60_OCTAVES_HZ.associateWith { hz ->
        val values = listOfNotNull(
            reference.rt60ByOctave[hz],
            repeat.rt60ByOctave[hz]
        )
        if (values.isEmpty()) null else values.average()
    }
}
