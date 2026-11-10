package tv.corebuilds.eq.dsp

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Same-position repeat averaging. Retains band summaries only, never PCM. */
object AdaptiveRepeatAverage {
    const val ALIGN_LOW_HZ = 500.0
    const val ALIGN_HIGH_HZ = 2_000.0
    const val MIN_ALIGNMENT_BANDS = 3
    const val DISAGREEMENT_RMS_DB = 3.0
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
        requireValid(reference)
        requireValid(repeat)
        require(target.isNotBlank() && nFilters > 0)
        require(volumeM3 == null || (volumeM3.isFinite() && volumeM3 > 0.0))
        val n = min(reference.centresHz.size, repeat.centresHz.size)
        val centres = reference.centresHz.copyOf(n)
        require((0 until n).all { abs(centres[it] - repeat.centresHz[it]) < 1e-6 }) {
            "the sweeps were analysed on different frequency grids"
        }
        val firstSnr = DoubleArray(n) { reference.bandSnrDb.getOrElse(it) { reference.snrDb } }
        val nextSnr = DoubleArray(n) { repeat.bandSnrDb.getOrElse(it) { repeat.snrDb } }
        val firstTrust = trust(reference, firstSnr)
        val nextTrust = trust(repeat, nextSnr)
        val commonFloor = max(reference.floorHz, repeat.floorHz)
        val commonCeiling = min(reference.fMaxHz, repeat.fMaxHz)
        require(commonCeiling > commonFloor) { "No shared trusted frequency range" }
        val anchors = (0 until n).filter {
            centres[it] in ALIGN_LOW_HZ..ALIGN_HIGH_HZ &&
                centres[it] in commonFloor..commonCeiling &&
                firstSnr[it] >= SweepAnalysis.BAND_MIN_SNR_DB &&
                nextSnr[it] >= SweepAnalysis.BAND_MIN_SNR_DB
        }.map { reference.measuredDb[it] - repeat.measuredDb[it] }.sorted()
        require(anchors.size >= MIN_ALIGNMENT_BANDS) { "Too few clean bands for level alignment" }
        val middle = anchors.size / 2
        val offset = if (anchors.size % 2 == 0) (anchors[middle - 1] + anchors[middle]) / 2.0 else anchors[middle]
        val measured = reference.measuredDb.copyOf(n)
        val snr = DoubleArray(n) { min(firstSnr[it], nextSnr[it]) }
        val confidence = DoubleArray(n)
        val gate = BooleanArray(n) { rawGate(reference, it) && rawGate(repeat, it) }
        var usable = 0
        var squaredDifference = 0.0
        for (k in 0 until n) {
            if (centres[k] !in commonFloor..commonCeiling ||
                snr[k] < SweepAnalysis.BAND_MIN_SNR_DB || firstTrust[k] <= 0.0 || nextTrust[k] <= 0.0) continue
            val first = reference.measuredDb[k]
            val next = repeat.measuredDb[k] + offset
            val firstWeight = firstTrust[k] * firstTrust[k]
            val nextWeight = nextTrust[k] * nextTrust[k]
            val peak = max(first, next)
            val power = (firstWeight * 10.0.pow((first - peak) / 10.0) +
                nextWeight * 10.0.pow((next - peak) / 10.0)) / (firstWeight + nextWeight)
            measured[k] = peak + 10.0 * log10(power)
            val difference = next - first
            squaredDifference += difference * difference
            usable++
            confidence[k] = min(firstTrust[k], nextTrust[k]) * agreementFactor(abs(difference))
        }
        require(usable >= MIN_ALIGNMENT_BANDS) { "Too few clean shared bands to average" }
        val rms = sqrt(squaredDifference / usable)
        val rt60s = listOfNotNull(reference.rt60Seconds, repeat.rt60Seconds)
        val rt60 = rt60s.takeIf { it.isNotEmpty() }?.average()
        val transition = Correction.transitionHz(volumeM3, rt60)
        val schroeder = if (volumeM3 != null && rt60 != null) Correction.schroederHz(volumeM3, rt60) else null
        val nulls = Correction.detectNulls(centres, measured)
        val rolloff = Correction.detectLowRolloff(centres, measured)
        val floor = max(commonFloor, max(DspConstants.F_MIN, rolloff))
        require(commonCeiling > floor) { "The averaged corrected band is empty" }
        val phase = BooleanArray(n) { gate[it] || centres[it] >= transition }
        val correctable = BooleanArray(n) { phase[it] && confidence[it] > 0.0 }
        val targetDb = Targets.targetCurve(target, centres)
        val correction = Correction.calculateCorrectionCurve(
            centres, measured, targetDb,
            fMin = floor, fMax = commonCeiling, transitionHz = transition,
            nullMask = nulls, minPhaseOk = correctable,
            maxBoostBelowTransition = Correction.boostBudgetDb(1), confidence = confidence
        )
        val filters = Peaking.fitPeakingFilters(centres, correction, nFilters)
        val octaves = SweepAnalysis.RT60_OCTAVES_HZ.associateWith { hz ->
            val values = listOfNotNull(reference.rt60ByOctave[hz], repeat.rt60ByOctave[hz])
            values.takeIf { it.isNotEmpty() }?.average()
        }
        val result = reference.copy(
            centresHz = centres, measuredDb = measured, targetDb = targetDb,
            correctionDb = correction, nullMask = nulls, minPhaseOk = phase,
            rt60Seconds = rt60, schroederHz = schroeder, transitionHz = transition,
            rolloffHz = rolloff, floorHz = floor, fMaxHz = commonCeiling,
            snrDb = min(reference.snrDb, repeat.snrDb), filters = filters,
            preampDb = Peaking.preampDb(filters), bandSnrDb = snr,
            rt60ByOctave = octaves, phaseGate = gate, confidence = confidence
        )
        return Averaged(result, offset, rms, usable,
            if (rms > DISAGREEMENT_RMS_DB) "The repeat differs from the first sweep; correction confidence is reduced." else null)
    }

    private fun trust(result: SweepResult, snr: DoubleArray): DoubleArray {
        val fallback = Correction.bandConfidence(snr)
        return DoubleArray(snr.size) { min(result.confidence.getOrElse(it) { fallback[it] }, fallback[it]) }
    }

    private fun rawGate(result: SweepResult, index: Int): Boolean =
        result.phaseGate.getOrElse(index) {
            result.centresHz[index] < result.transitionHz && result.minPhaseOk[index]
        }

    private fun agreementFactor(db: Double): Double =
        max(MIN_AGREEMENT_FACTOR, 1.0 - max(0.0, db - DISAGREEMENT_RMS_DB) / 6.0)

    private fun requireValid(result: SweepResult) {
        val n = result.centresHz.size
        require(n > 0 && result.measuredDb.size == n && result.minPhaseOk.size == n)
        require(result.centresHz.all { it.isFinite() && it > 0.0 })
        require((1 until n).all { result.centresHz[it] > result.centresHz[it - 1] })
        require(result.measuredDb.all { it.isFinite() })
        require(result.snrDb.isFinite() && result.transitionHz.isFinite())
        require(result.floorHz.isFinite() && result.fMaxHz.isFinite() && result.fMaxHz > result.floorHz)
        require(result.bandSnrDb.isEmpty() || (result.bandSnrDb.size == n && result.bandSnrDb.all { it.isFinite() }))
        require(result.confidence.isEmpty() || (result.confidence.size == n && result.confidence.all { it.isFinite() && it in 0.0..1.0 }))
        require(result.phaseGate.isEmpty() || result.phaseGate.size == n)
        require(result.rt60Seconds == null || (result.rt60Seconds.isFinite() && result.rt60Seconds > 0.0))
        require(result.rt60ByOctave.values.all { it == null || (it.isFinite() && it > 0.0) })
    }
}
