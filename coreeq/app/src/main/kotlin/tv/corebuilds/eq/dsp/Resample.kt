package tv.corebuilds.eq.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Integer-factor decimation for captures that arrive faster than the analysis
 * rate, and the fractional [stretch] that takes clock drift back out.
 */
object Resample {

    /** Half-width, in input samples, of the [stretch] kernel. */
    const val STRETCH_HALF = 24

    /**
     * Low-pass at 0.45 of the output rate (Blackman-windowed sinc, 127 taps),
     * then keep every [factor]th sample. The filter delay is removed so the
     * output lines up with the input in time.
     */
    fun decimate(x: DoubleArray, factor: Int, taps: Int = 127): DoubleArray {
        require(factor >= 1) { "decimation factor must be at least 1, got $factor" }
        if (factor == 1) return x.copyOf()
        val m = taps - 1
        val fc = 0.45 / factor
        val h = DoubleArray(taps) { i ->
            val k = i - m / 2.0
            val sinc = if (abs(k) < 1e-12) 2 * fc else sin(2 * PI * fc * k) / (PI * k)
            sinc * (0.42 - 0.5 * cos(2 * PI * i / m) + 0.08 * cos(4 * PI * i / m))
        }
        val sum = h.sum()
        for (i in h.indices) h[i] /= sum
        val delay = m / 2
        val out = DoubleArray(x.size / factor)
        for (o in out.indices) {
            val centre = o * factor
            var acc = 0.0
            for (t in 0 until taps) {
                val idx = centre + delay - t
                if (idx in x.indices) acc += h[t] * x[idx]
            }
            out[o] = acc
        }
        return out
    }

    /**
     * [x] read at positions n x [ratio] for n = 0, 1, ...: a capture whose
     * clock ran [ratio] times as fast as the player's, put back on the
     * player's time base (1.3.2). Blackman-windowed sinc over
     * ±[STRETCH_HALF] samples, normalised to unit DC gain. The sinc and window
     * terms are rebuilt by angle addition, so each output sample costs five
     * trig calls whatever the kernel width.
     */
    fun stretch(x: DoubleArray, ratio: Double): DoubleArray {
        require(ratio > 0.5 && ratio < 2.0) { "stretch ratio out of range: $ratio" }
        if (x.isEmpty()) return x.copyOf()
        val h = STRETCH_HALF
        val width = (h + 1).toDouble()
        val taps = 2 * h + 2 // m = -h .. h + 1
        val cosM = DoubleArray(taps) { cos(PI * (it - h) / width) }
        val sinM = DoubleArray(taps) { sin(PI * (it - h) / width) }
        val cos2M = DoubleArray(taps) { cos(2.0 * PI * (it - h) / width) }
        val sin2M = DoubleArray(taps) { sin(2.0 * PI * (it - h) / width) }
        val n = ((x.size - 1) / ratio).toInt() + 1
        return DoubleArray(n) { i ->
            val pos = i * ratio
            val base = floor(pos).toInt()
            val frac = pos - base
            if (frac < 1e-9) return@DoubleArray x.getOrElse(base) { 0.0 }
            val sinF = sin(PI * frac)
            val c1 = cos(PI * frac / width); val s1 = sin(PI * frac / width)
            val c2 = cos(2.0 * PI * frac / width); val s2 = sin(2.0 * PI * frac / width)
            var acc = 0.0
            var norm = 0.0
            for (t in 0 until taps) {
                val m = t - h
                // x = frac - m: sin(pi x) = (-1)^m sin(pi frac), and the window's
                // cos(k pi x / width) by angle addition from the tables.
                val sinc = (if (m and 1 == 0) sinF else -sinF) / (PI * (frac - m))
                val w = 0.42 + 0.5 * (c1 * cosM[t] + s1 * sinM[t]) + 0.08 * (c2 * cos2M[t] + s2 * sin2M[t])
                val k = sinc * w
                norm += k
                val idx = base + m
                if (idx >= 0 && idx < x.size) acc += k * x[idx]
            }
            acc / norm
        }
    }
}
