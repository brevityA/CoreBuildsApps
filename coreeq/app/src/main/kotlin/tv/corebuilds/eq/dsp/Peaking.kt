package tv.corebuilds.eq.dsp

import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

data class PeakingFilter(val fc: Double, val q: Double, val gain: Double)

/**
 * RBJ-cookbook peaking filter response, coordinate descent fit and preamp calculation.
 * Matches tools/core_eq_dsp.py.
 */
object Peaking {

    fun peakingMagnitudeDb(freqs: DoubleArray, fc: Double, q: Double, gainDb: Double): DoubleArray {
        val out = DoubleArray(freqs.size)
        if (abs(gainDb) < 1e-9) return out

        val amp = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * Math.PI * min(fc, DspConstants.FS * 0.4999) / DspConstants.FS
        val alpha = sin(w0) / (2.0 * q)
        val cosW0 = cos(w0)

        for (i in freqs.indices) {
            val f = max(freqs[i], 1e-9)
            val w = 2.0 * Math.PI * f / DspConstants.FS
            val sinW = sin(w)
            val common = (cos(w) - cosW0).pow(2)
            val num = common + (alpha * amp * sinW).pow(2)
            val den = common + (alpha / amp * sinW).pow(2)
            val ratio = max(num, 1e-20) / max(den, 1e-20)
            out[i] = 10.0 * log10(max(ratio, 1e-20))
        }
        return out
    }

    fun filterSumDb(freqs: DoubleArray, filters: List<PeakingFilter>): DoubleArray {
        val total = DoubleArray(freqs.size)
        for (filt in filters) {
            val mag = peakingMagnitudeDb(freqs, filt.fc, filt.q, filt.gain)
            for (i in freqs.indices) {
                total[i] += mag[i]
            }
        }
        return total
    }

    fun preampDb(filters: List<PeakingFilter>): Double {
        if (filters.isEmpty()) return 0.0
        var biggest = 0.0
        for (f in filters) {
            if (f.gain > biggest) biggest = f.gain
        }
        return floor(-biggest * 100.0) / 100.0
    }

    fun fitPeakingFilters(
        freqs: DoubleArray,
        targetDb: DoubleArray,
        nFilters: Int = DspConstants.PEAKING_FILTERS,
        minQ: Double = DspConstants.PEAKING_MIN_Q,
        maxQ: Double = DspConstants.PEAKING_MAX_Q,
        maxGain: Double = DspConstants.MAX_BOOST_DB,
        seed: Long = 7L,
        iterations: Int = 400
    ): List<PeakingFilter> {
        val bandIndices = mutableListOf<Int>()
        for (i in freqs.indices) {
            if (freqs[i] in DspConstants.F_MIN..DspConstants.F_MAX) {
                bandIndices.add(i)
            }
        }
        if (bandIndices.size < 8) return emptyList()

        val bf = DoubleArray(bandIndices.size) { freqs[bandIndices[it]] }
        val bt = DoubleArray(bandIndices.size) { targetDb[bandIndices[it]] }

        val seeds = mutableListOf<PeakingFilter>()
        for (i in 1 until bt.size - 1) {
            if (bt[i] == bt[i - 1] && bt[i] == bt[i + 1]) continue
            val isPeak = bt[i] > bt[i - 1] && bt[i] >= bt[i + 1]
            val isValley = bt[i] < bt[i - 1] && bt[i] <= bt[i + 1]
            if ((isPeak || isValley) && abs(bt[i]) >= 0.5) {
                val q = min(maxQ, max(minQ, 1.0 / max(abs(bt[i]), 0.5)))
                seeds.add(PeakingFilter(bf[i], q, bt[i].coerceIn(-maxGain, maxGain)))
            }
        }

        if (seeds.none { it.fc < 120.0 }) {
            seeds.add(PeakingFilter(35.0, 0.8, interpolate(35.0, bf, bt).coerceIn(-maxGain, maxGain)))
            seeds.add(PeakingFilter(80.0, 0.8, interpolate(80.0, bf, bt).coerceIn(-maxGain, maxGain)))
        }

        if (seeds.size > nFilters) {
            seeds.sortByDescending { abs(it.gain) }
            while (seeds.size > nFilters) seeds.removeAt(seeds.size - 1)
        }
        while (seeds.size < nFilters) {
            val frac = seeds.size.toDouble() / max(1.0, (nFilters - 1).toDouble())
            val fc = bf[0] + frac * (bf[bf.size - 1] - bf[0])
            seeds.add(PeakingFilter(fc, 1.0, 0.0))
        }

        seeds.sortBy { it.fc }
        val filt = seeds.toMutableList()

        val rng = Random(seed)
        var step = 0.35

        for (iter in 0 until iterations) {
            var improved = false
            for (k in 0 until filt.size) {
                var baseSum = filterSumDb(bf, filt)
                var err = meanSquaredError(baseSum, bt)
                var best = Quad(filt[k].fc, filt[k].q, filt[k].gain, err)

                for (cand in 0 until 24) {
                    val candFc = (best.fc * (1.0 + rng.nextGaussian() * step * 0.4)).coerceIn(bf[0], bf[bf.size - 1])
                    val candQ = (best.q * (1.0 + rng.nextGaussian() * step * 0.25)).coerceIn(minQ, maxQ)
                    val candG = (best.g + rng.nextGaussian() * step * 1.5).coerceIn(-maxGain, maxGain)

                    val trial = filt.toMutableList()
                    trial[k] = PeakingFilter(candFc, candQ, candG)
                    var e = meanSquaredError(filterSumDb(bf, trial), bt)
                    e += 0.02 * max(0.0, candQ - 2.0) * abs(candG)

                    for (j in trial.indices) {
                        if (j != k && trial[j].fc > 0.0) {
                            val spread = abs(log2(candFc / trial[j].fc))
                            if (spread < 0.25) {
                                e += 0.5 * (0.25 - spread)
                            }
                        }
                    }

                    if (e < best.err - 1e-9) {
                        best = Quad(candFc, candQ, candG, e)
                        improved = true
                    }
                }
                filt[k] = PeakingFilter(best.fc, best.q, best.g)
            }
            step *= 0.94
            if (!improved && step < 0.02) break
        }

        return filt.filter { abs(it.gain) >= 0.05 }
            .sortedBy { it.fc }
            .map { PeakingFilter(roundTo1Dec(it.fc), roundTo2Dec(it.q), roundTo2Dec(it.gain)) }
    }

    private data class Quad(val fc: Double, val q: Double, val g: Double, val err: Double)

    private fun meanSquaredError(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return if (a.isNotEmpty()) sum / a.size else 0.0
    }

    private fun interpolate(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (xs.isEmpty()) return 0.0
        if (x <= xs[0]) return ys[0]
        if (x >= xs[xs.size - 1]) return ys[ys.size - 1]
        for (i in 0 until xs.size - 1) {
            if (x in xs[i]..xs[i + 1]) {
                val frac = (x - xs[i]) / (xs[i + 1] - xs[i])
                return ys[i] + frac * (ys[i + 1] - ys[i])
            }
        }
        return ys[0]
    }

    private fun roundTo1Dec(v: Double): Double = round(v * 10.0) / 10.0
    private fun roundTo2Dec(v: Double): Double = round(v * 100.0) / 100.0
}
