package tv.corebuilds.eq.dsp

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.tanh

/**
 * Loudspeaker in-room target curves, matching tools/core_eq_dsp.py.
 */
object Targets {

    fun shelfDb(f: DoubleArray, cornerHz: Double, gainDb: Double): DoubleArray {
        val out = DoubleArray(f.size)
        for (i in f.indices) {
            val freq = max(f[i], 1e-9)
            val logRatio = log2(freq / cornerHz)
            out[i] = gainDb * 0.5 * (1.0 + tanh(logRatio / 0.35))
        }
        return out
    }

    fun boxDb(f: DoubleArray, loHz: Double, hiHz: Double): DoubleArray {
        val out = DoubleArray(f.size)
        val loEdge = loHz / Math.sqrt(2.0)
        val hiEdge = hiHz * Math.sqrt(2.0)
        val logLoRatio = log2(loHz / loEdge)
        val logHiRatio = log2(hiEdge / hiHz)

        for (i in f.indices) {
            val freq = f[i]
            if (freq > loEdge && freq < loHz) {
                val t = log2(freq / loEdge) / logLoRatio
                out[i] = 0.5 * (1.0 - cos(Math.PI * t))
            } else if (freq in loHz..hiHz) {
                out[i] = 1.0
            } else if (freq > hiHz && freq < hiEdge) {
                val t = log2(freq / hiHz) / logHiRatio
                out[i] = 0.5 * (1.0 + cos(Math.PI * t))
            } else {
                out[i] = 0.0
            }
        }
        return out
    }

    fun presenceDb(f: DoubleArray, gainDb: Double): DoubleArray {
        if (gainDb == 0.0) return DoubleArray(f.size)
        val box = boxDb(f, 2200.0, 4500.0)
        val out = DoubleArray(f.size)
        for (i in f.indices) {
            out[i] = gainDb * box[i]
        }
        return out
    }

    fun targetCurve(
        kind: String,
        freqs: DoubleArray,
        bassBoostDb: Double = 0.0,
        tiltDbPerOct: Double = -1.0,
        pivotHz: Double = 630.0,
        presenceDb: Double = 2.0,
        trimDb: Double = 0.0
    ): DoubleArray {
        val out = DoubleArray(freqs.size)
        val normalizedKind = kind.lowercase().trim()
        if (normalizedKind == "flat") return out

        val bassGain = when (normalizedKind) {
            "room", "harman" -> 3.5
            "olive" -> 6.6
            "dialogue" -> 3.5
            "house" -> bassBoostDb
            else -> 0.0
        }

        val shelf2500 = if (normalizedKind == "olive") shelfDb(freqs, 2500.0, -2.4) else null
        val presence = if (normalizedKind in listOf("dialogue", "house")) presenceDb(freqs, presenceDb) else null
        val trimBox = if (normalizedKind == "dialogue" && trimDb != 0.0) boxDb(freqs, 300.0, 800.0) else null

        val bkSlope = -6.0 / log2(20000.0 / 160.0)

        for (i in freqs.indices) {
            val f = freqs[i]
            val octavesFromPivot = log2(max(f, 1e-9) / pivotHz)
            val bass = bassGain * 0.5 * (1.0 - tanh((f - 105.0) / 55.0))

            when (normalizedKind) {
                "bk" -> {
                    out[i] = if (f <= 160.0) 0.0 else bkSlope * log2(max(f, 160.0) / 160.0)
                }
                "room", "harman" -> {
                    out[i] = bass + tiltDbPerOct * octavesFromPivot
                }
                "olive" -> {
                    out[i] = bass + tiltDbPerOct * octavesFromPivot + (shelf2500?.get(i) ?: 0.0)
                }
                "dialogue" -> {
                    var v = bass + tiltDbPerOct * octavesFromPivot + (presence?.get(i) ?: 0.0)
                    if (trimBox != null) {
                        v -= Math.abs(trimDb) * trimBox[i]
                    }
                    out[i] = v
                }
                "house" -> {
                    out[i] = bass + tiltDbPerOct * octavesFromPivot + (presence?.get(i) ?: 0.0)
                }
                else -> {
                    throw IllegalArgumentException("Unknown target curve: $kind")
                }
            }
        }

        // Normalise so the pivot frequency reads 0 dB
        val atPivot = interpolate(pivotHz, freqs, out)
        for (i in out.indices) {
            out[i] -= atPivot
        }
        return out
    }

    private fun interpolate(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (xs.isEmpty()) return 0.0
        if (x <= xs[0]) return ys[0]
        if (x >= xs[xs.size - 1]) return ys[ys.size - 1]
        for (i in 0 until xs.size - 1) {
            if (x in xs[i]..xs[i + 1]) {
                val fraction = (x - xs[i]) / (xs[i + 1] - xs[i])
                return ys[i] + fraction * (ys[i + 1] - ys[i])
            }
        }
        return ys[0]
    }
}
