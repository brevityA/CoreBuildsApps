package tv.corebuilds.eq.dsp

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Two-regime acoustic correction and room modal analysis, matching tools/core_eq_dsp.py.
 */
object Correction {

    fun schroederHz(volumeM3: Double, rt60Seconds: Double): Double {
        require(volumeM3 > 0.0) { "Room volume must be positive, got $volumeM3" }
        require(rt60Seconds > 0.0) { "RT60 must be positive, got $rt60Seconds" }
        return 2000.0 * sqrt(rt60Seconds / volumeM3)
    }

    fun transitionHz(volumeM3: Double?, rt60Seconds: Double?): Double {
        if (volumeM3 == null || rt60Seconds == null || volumeM3 <= 0.0 || rt60Seconds <= 0.0) {
            return DspConstants.UNKNOWN_ROOM_TRANSITION_HZ
        }
        return min(2.0 * schroederHz(volumeM3, rt60Seconds), DspConstants.DEFAULT_TRANSITION_HZ)
    }

    fun smoothOctave(freqs: DoubleArray, valuesDb: DoubleArray, octaves: Double): DoubleArray {
        val n = freqs.size
        val out = DoubleArray(n)
        if (n == 0) return out
        if (n < 3) return valuesDb.clone()

        val logF = DoubleArray(n) { log2(max(freqs[it], 1e-9)) }
        val linear = DoubleArray(n) { 10.0.pow(valuesDb[it] / 10.0) }
        val half = octaves / 2.0

        for (i in 0 until n) {
            val targetLo = logF[i] - half
            val targetHi = logF[i] + half
            var lo = searchSorted(logF, targetLo)
            var hi = searchSorted(logF, targetHi)
            val span = max(hi - lo, 3)
            val w = span / 2
            lo = max(0, i - w)
            hi = min(n, i + w + 1)

            var sum = 0.0
            val count = hi - lo
            for (j in lo until hi) {
                sum += linear[j]
            }
            val meanLinear = if (count > 0) sum / count else 1e-20
            out[i] = 10.0 * log10(max(meanLinear, 1e-20))
        }
        return out
    }

    fun smoothVariable(
        freqs: DoubleArray,
        valuesDb: DoubleArray,
        transitionHz: Double = DspConstants.DEFAULT_TRANSITION_HZ
    ): DoubleArray {
        val n = freqs.size
        val out = DoubleArray(n)
        if (n == 0) return out
        if (n < 3) return valuesDb.clone()

        val loT = max(transitionHz, 1.0)
        val hiT = loT * 2.0
        val logLoT = log2(loT)
        val logF = DoubleArray(n) { log2(max(freqs[it], 1e-9)) }
        val linear = DoubleArray(n) { 10.0.pow(valuesDb[it] / 10.0) }

        val widths = DoubleArray(n)
        for (i in 0 until n) {
            val f = freqs[i]
            widths[i] = when {
                f <= loT -> DspConstants.SMOOTH_FINE_OCTAVES
                f >= hiT -> DspConstants.SMOOTH_COARSE_OCTAVES
                else -> {
                    val frac = (logF[i] - logLoT)
                    DspConstants.SMOOTH_FINE_OCTAVES +
                        (DspConstants.SMOOTH_COARSE_OCTAVES - DspConstants.SMOOTH_FINE_OCTAVES) * frac
                }
            }
        }

        for (i in 0 until n) {
            val half = widths[i] / 2.0
            val a = searchSorted(logF, logF[i] - half)
            var b = searchSorted(logF, logF[i] + half)
            b = max(b, a + 1)
            b = min(b, n)

            var sum = 0.0
            val count = b - a
            for (j in a until b) {
                sum += linear[j]
            }
            val meanLinear = if (count > 0) sum / count else 1e-20
            out[i] = 10.0 * log10(max(meanLinear, 1e-20))
        }
        return out
    }

    fun detectNulls(
        freqs: DoubleArray,
        measuredDb: DoubleArray,
        depthDb: Double = DspConstants.NULL_DEPTH_DB,
        trendOctaves: Double = 2.0
    ): BooleanArray {
        val trend = smoothOctave(freqs, measuredDb, trendOctaves)
        val mask = BooleanArray(freqs.size)
        for (i in freqs.indices) {
            mask[i] = (trend[i] - measuredDb[i]) > depthDb
        }
        return mask
    }

    fun detectLowRolloff(
        freqs: DoubleArray,
        measuredDb: DoubleArray,
        plateauHz: Double = 250.0,
        dropDb: Double = DspConstants.ROLLOFF_DROP_DB
    ): Double {
        if (freqs.isEmpty()) return DspConstants.F_MIN
        val loRef = plateauHz / 1.5
        val hiRef = plateauHz * 1.5

        var refSum = 0.0
        var refCount = 0
        var maxDb = Double.NEGATIVE_INFINITY
        for (i in freqs.indices) {
            if (measuredDb[i] > maxDb) maxDb = measuredDb[i]
            if (freqs[i] in loRef..hiRef) {
                refSum += measuredDb[i]
                refCount++
            }
        }
        val refLevel = if (refCount > 0) refSum / refCount else maxDb

        var lowestUsable = Double.POSITIVE_INFINITY
        for (i in freqs.indices) {
            if (measuredDb[i] >= refLevel - dropDb) {
                if (freqs[i] < lowestUsable) lowestUsable = freqs[i]
            }
        }
        return if (lowestUsable.isInfinite()) DspConstants.F_MIN else max(DspConstants.F_MIN, lowestUsable)
    }

    fun limitSlope(
        freqs: DoubleArray,
        curve: DoubleArray,
        maxDbPerOct: Double = DspConstants.MAX_SLOPE_DB_PER_OCT
    ): DoubleArray {
        val n = curve.size
        if (n <= 1) return curve.clone()
        val logF = DoubleArray(n) { log2(max(freqs[it], 1e-9)) }

        val fwd = curve.clone()
        for (i in 0 until n - 1) {
            val j = i + 1
            val df = abs(logF[j] - logF[i])
            if (df > 0.0) {
                val limit = maxDbPerOct * df
                val delta = (fwd[j] - fwd[i]).coerceIn(-limit, limit)
                fwd[j] = fwd[i] + delta
            }
        }

        val bwd = curve.clone()
        for (i in n - 1 downTo 1) {
            val j = i - 1
            val df = abs(logF[j] - logF[i])
            if (df > 0.0) {
                val limit = maxDbPerOct * df
                val delta = (bwd[j] - bwd[i]).coerceIn(-limit, limit)
                bwd[j] = bwd[i] + delta
            }
        }

        val out = DoubleArray(n)
        for (i in 0 until n) {
            out[i] = min(fwd[i], bwd[i])
        }
        return out
    }

    fun taperEdges(
        freqs: DoubleArray,
        curve: DoubleArray,
        fMin: Double,
        fMax: Double,
        octaves: Double = 1.0
    ): DoubleArray {
        val out = curve.clone()
        val loEdge = fMin * 2.0.pow(octaves)
        val hiEdge = fMax / 2.0.pow(octaves)

        for (i in freqs.indices) {
            val f = freqs[i]
            if (f in fMin until loEdge) {
                val t = log2(f / fMin) / octaves
                out[i] *= 0.5 * (1.0 - cos(Math.PI * t))
            } else if (f in hiEdge..fMax) {
                val t = log2(fMax / f) / octaves
                out[i] *= 0.5 * (1.0 - cos(Math.PI * t))
            }
        }
        return out
    }

    fun calculateCorrectionCurve(
        freqs: DoubleArray,
        measuredDb: DoubleArray,
        targetDb: DoubleArray,
        fMin: Double = DspConstants.F_MIN,
        fMax: Double = DspConstants.F_MAX,
        transitionHz: Double = DspConstants.DEFAULT_TRANSITION_HZ,
        maxBoost: Double = DspConstants.MAX_BOOST_DB,
        maxCut: Double = DspConstants.MAX_CUT_DB,
        cutOnly: Boolean = false,
        maxSlope: Double = DspConstants.MAX_SLOPE_DB_PER_OCT,
        nullMask: BooleanArray? = null,
        minPhaseOk: BooleanArray? = null
    ): DoubleArray {
        val n = freqs.size
        val smoothed = smoothVariable(freqs, measuredDb, transitionHz)
        val raw = DoubleArray(n) { -(smoothed[it] - targetDb[it]) }

        val actualNullMask = nullMask ?: detectNulls(freqs, measuredDb)
        for (i in 0 until n) {
            if (actualNullMask[i] && raw[i] > 0.0) {
                raw[i] = 0.0
            }
            if (minPhaseOk != null && !minPhaseOk[i]) {
                raw[i] = 0.0
            }
            if (cutOnly) {
                raw[i] = min(raw[i], 0.0)
            }
        }

        val loT = max(transitionHz, 1.0)
        val hiT = loT * 2.0
        val ceiling = DoubleArray(n)
        val floor = DoubleArray(n)

        for (i in 0 until n) {
            val f = freqs[i]
            val mix = when {
                f <= loT -> 0.0
                f >= hiT -> 1.0
                else -> 0.5 * (1.0 - cos(Math.PI * log2(f / loT)))
            }
            ceiling[i] = if (cutOnly) 0.0 else maxBoost + (DspConstants.MAX_SHAPING_DB - maxBoost) * mix
            floor[i] = -maxCut + (maxCut - DspConstants.MAX_SHAPING_DB) * mix
            raw[i] = raw[i].coerceIn(floor[i], ceiling[i])
        }

        var limited = limitSlope(freqs, raw, maxSlope)

        if (!cutOnly) {
            var recentreSum = 0.0
            var recentreCount = 0
            val loRecentre = max(100.0, fMin)
            val hiRecentre = min(10000.0, fMax)
            for (i in 0 until n) {
                if (freqs[i] in loRecentre..hiRecentre) {
                    recentreSum += limited[i]
                    recentreCount++
                }
            }
            if (recentreCount > 0) {
                val mean = recentreSum / recentreCount
                for (i in 0 until n) {
                    limited[i] -= mean
                }
            }
        }

        for (i in 0 until n) {
            limited[i] = limited[i].coerceIn(floor[i], ceiling[i])
            if (freqs[i] < fMin || freqs[i] > fMax) {
                limited[i] = 0.0
            }
        }

        return taperEdges(freqs, limited, fMin, fMax)
    }

    fun collapseToBands(
        bandCentresHz: DoubleArray,
        correctionFn: (Double) -> Double,
        minMillibel: Int,
        maxMillibel: Int
    ): List<Pair<Double, Int>> {
        val out = mutableListOf<Pair<Double, Int>>()
        for (fc in bandCentresHz) {
            val rawDb = correctionFn(fc)
            val clampedDb = rawDb.coerceIn(-DspConstants.MAX_CUT_DB, DspConstants.MAX_BOOST_DB)
            val mb = (clampedDb * 100.0).roundToInt().coerceIn(minMillibel, maxMillibel)
            out.add(Pair(fc, mb))
        }
        return out
    }

    private fun searchSorted(arr: DoubleArray, target: Double): Int {
        var low = 0
        var high = arr.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (arr[mid] < target) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }
}
