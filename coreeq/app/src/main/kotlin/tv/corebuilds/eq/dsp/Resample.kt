package tv.corebuilds.eq.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Integer-factor decimation for captures that arrive faster than the analysis rate. */
object Resample {

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
}
