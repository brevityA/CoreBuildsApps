package tv.corebuilds.eq.dsp

import java.util.Random
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow

/**
 * Deterministic synthetic room response generator matching tools/core_eq_dsp.py.
 * Enables zero-hardware self-testing, demoing, and offline verification.
 */
object SyntheticRoom {

    fun generateDb(freqs: DoubleArray, seed: Long = 11L): DoubleArray {
        val out = DoubleArray(freqs.size)
        val rng = Random(seed)

        for (i in freqs.indices) {
            val f = max(freqs[i], 1e-9)
            var c = 0.0

            // 62 Hz modal peak (+4.5 dB)
            val d62 = log2(f / 62.0)
            c += 4.5 * exp(-(d62 * d62) / (2.0 * 0.25 * 0.25))

            // 118 Hz modal peak (+6.0 dB)
            val d118 = log2(f / 118.0)
            c += 6.0 * exp(-(d118 * d118) / (2.0 * 0.12 * 0.12))

            // 84 Hz deep room cancellation null (-14.0 dB)
            val d84 = log2(f / 84.0)
            c += -14.0 * exp(-(d84 * d84) / (2.0 * 0.10 * 0.10))

            // In-room boundary tilt
            c += -5.0 * log2(max(f, 1.0) / 1000.0) * 0.6

            // Minor room measurement jitter
            c += rng.nextGaussian() * 0.35

            out[i] = c
        }
        return out
    }
}
