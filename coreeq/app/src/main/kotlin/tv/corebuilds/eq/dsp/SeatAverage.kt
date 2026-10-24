package tv.corebuilds.eq.dsp

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Several seats around the listening position combined into one correction.
 *
 * One seat cannot tell a dip or a peak that belongs to the room from one that belongs to
 * that chair: move the remote 50 cm and a 60 Hz peak can halve, or a dip can fill in. A
 * correction cut to one seat's peaks is partly wrong everywhere else on the sofa. Averaging
 * the seats (roadmap item 1, docs/research/core-eq-competitor-research-2026-10-13.md) leaves
 * what the room does at all of them, which is what an EQ can fix.
 *
 * The average is an energy mean per band, the reference's `average_measurements` in
 * tools/core_eq_dsp.py: averaging decibels would quietly flatten the peaks the corrector has
 * to see. Each seat's curve is already level-aligned to the target over 300 Hz-3 kHz by
 * [SweepAnalysis], so the seats are on one scale before they are averaged.
 *
 * The averaged curve then goes through the same correction as one seat, with every limit
 * unchanged, including [SweepAnalysis.SINGLE_SEAT_BOOST_DB] below the transition: averaging
 * makes the cuts truer, and lifting the bass further is a separate decision. A band is
 * corrected only where every seat measured it cleanly (minimum phase below the transition,
 * and signal over noise), so one bad seat can switch a band off but never on.
 */
object SeatAverage {
    /** The reference's seat envelope: "the three measurements taken within the seat envelope". */
    const val MAX_SEATS = 3

    /** Where seats in one room should agree: above the modes, below the remote's beaming. */
    const val AGREE_LO_HZ = 300.0
    const val AGREE_HI_HZ = 3000.0

    /**
     * A seat whose 300 Hz-3 kHz response sits this far (RMS) from the others' energy mean is
     * not measuring the same thing: the remote was covered, turned away or behind something.
     * Seat-to-seat differences there are normally 1-2 dB; the bass is allowed to differ, as
     * that is the point of measuring several seats.
     */
    const val OUTLIER_RMS_DB = 4.0

    /** Energy mean per band, over the bands every curve has (the reference's `average_measurements`). */
    fun energyMeanDb(curves: List<DoubleArray>): DoubleArray {
        require(curves.isNotEmpty()) { "nothing to average" }
        val n = curves.minOf { it.size }
        return DoubleArray(n) { k ->
            val meanPower = curves.sumOf { 10.0.pow(it[k] / 10.0) } / curves.size
            10.0 * log10(max(meanPower, 1e-20))
        }
    }

    /**
     * Each seat's RMS departure, 300 Hz-3 kHz, from the energy mean of the other seats; null
     * for a seat when fewer than three bands fall in that window or there is no other seat.
     */
    fun midbandDeviationDb(centres: DoubleArray, curves: List<DoubleArray>): List<Double?> =
        curves.indices.map { i ->
            val others = curves.filterIndexed { j, _ -> j != i }
            if (others.isEmpty()) return@map null
            val reference = energyMeanDb(others)
            val n = minOf(centres.size, reference.size, curves[i].size)
            val diffs = (0 until n)
                .filter { centres[it] in AGREE_LO_HZ..AGREE_HI_HZ }
                .map { curves[i][it] - reference[it] }
                .filter { it.isFinite() }
            if (diffs.size < 3) null else sqrt(diffs.sumOf { it * it } / diffs.size)
        }

    /**
     * The seat to leave out of the average, if any: only with three or more seats (with two
     * there is no telling which one is wrong), and only the single worst one, when it is over
     * [OUTLIER_RMS_DB].
     */
    fun outlier(centres: DoubleArray, curves: List<DoubleArray>): Int? {
        if (curves.size < 3) return null
        val deviations = midbandDeviationDb(centres, curves)
        val worst = deviations.indices.maxByOrNull { deviations[it] ?: -1.0 } ?: return null
        val worstDb = deviations[worst] ?: return null
        return if (worstDb > OUTLIER_RMS_DB) worst else null
    }

    /** [result] is the correction; [leftOut] the seat it does not include, and how far off that seat was. */
    data class Averaged(val result: SweepResult, val leftOut: Int?, val leftOutDb: Double?)

    /** [combine], after leaving out the one seat [outlier] picks, if any. */
    fun average(
        seats: List<SweepResult>,
        target: String,
        volumeM3: Double?,
        nFilters: Int = DspConstants.PEAKING_FILTERS
    ): Averaged {
        require(seats.isNotEmpty()) { "no seats to average" }
        val centres = seats.minByOrNull { it.centresHz.size }!!.centresHz
        val curves = seats.map { it.measuredDb }
        val out = outlier(centres, curves)
        val kept = seats.filterIndexed { i, _ -> i != out }
        return Averaged(
            result = combine(kept, target, volumeM3, nFilters),
            leftOut = out,
            leftOutDb = out?.let { midbandDeviationDb(centres, curves)[it] }
        )
    }

    /**
     * One correction from [seats], all analysed for the same [target] and room ([volumeM3]).
     * A single seat is returned as it is. Bands are the ones every seat kept; the correction
     * stops at the narrowest seat's bandwidth edge.
     */
    fun combine(
        seats: List<SweepResult>,
        target: String,
        volumeM3: Double?,
        nFilters: Int = DspConstants.PEAKING_FILTERS
    ): SweepResult {
        require(seats.isNotEmpty()) { "no seats to combine" }
        if (seats.size == 1) return seats[0]
        val n = seats.minOf { it.centresHz.size }
        val centres = seats[0].centresHz.copyOf(n)
        for (seat in seats) {
            for (k in 0 until n) {
                require(abs(seat.centresHz[k] - centres[k]) < 1e-6) { "seats were analysed on different bands" }
            }
        }
        val measured = energyMeanDb(seats.map { it.measuredDb })
        val targetDb = Targets.targetCurve(target, centres)

        // The room's decay does not depend on the seat; the mean of the seats that could time it.
        val rt60s = seats.mapNotNull { it.rt60Seconds }
        val rt60 = if (rt60s.isEmpty()) null else rt60s.average()
        val transition = Correction.transitionHz(volumeM3, rt60)
        val schroeder = if (volumeM3 != null && rt60 != null) Correction.schroederHz(volumeM3, rt60) else null

        // Nulls and roll-off are read again from the average: a dip at one seat that the
        // others fill in is no longer a null, which is most of what averaging is for.
        val nulls = Correction.detectNulls(centres, measured)
        val rolloff = Correction.detectLowRolloff(centres, measured)
        val floorHz = max(DspConstants.F_MIN, rolloff)
        // Each seat's raw gate, not its minPhaseOk: that one is already waived above the seat's
        // own transition, which a shorter decay puts below the combined one.
        val gate = BooleanArray(n) { k -> seats.all { it.phaseGate.getOrElse(k) { _ -> it.minPhaseOk[k] } } }
        val minPhaseOk = BooleanArray(n) { k -> gate[k] || centres[k] >= transition }
        val bandSnr = DoubleArray(n) { k -> seats.minOf { it.bandSnrDb.getOrElse(k) { Double.MAX_VALUE } } }
        val correctable = BooleanArray(n) { minPhaseOk[it] && bandSnr[it] >= SweepAnalysis.BAND_MIN_SNR_DB }
        val fMax = seats.minOf { it.fMaxHz }
        val correction = Correction.calculateCorrectionCurve(
            centres, measured, targetDb,
            fMin = floorHz,
            fMax = fMax,
            transitionHz = transition,
            nullMask = nulls,
            minPhaseOk = correctable,
            maxBoostBelowTransition = SweepAnalysis.SINGLE_SEAT_BOOST_DB
        )
        val filters = Peaking.fitPeakingFilters(centres, correction, nFilters)
        val octaves = SweepAnalysis.RT60_OCTAVES_HZ.associateWith { hz ->
            val values = seats.mapNotNull { it.rt60ByOctave[hz] }
            if (values.isEmpty()) null else values.average()
        }
        return SweepResult(
            centresHz = centres,
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
            // The weakest seat is the one the result is only as good as.
            snrDb = seats.minOf { it.snrDb },
            latencyMs = seats[0].latencyMs,
            filters = filters,
            preampDb = Peaking.preampDb(filters),
            bandSnrDb = bandSnr,
            rt60ByOctave = octaves,
            driftPpm = seats[0].driftPpm,
            fMaxHz = fMax,
            phaseGate = gate
        )
    }
}
