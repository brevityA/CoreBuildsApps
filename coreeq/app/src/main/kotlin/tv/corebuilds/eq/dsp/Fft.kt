package tv.corebuilds.eq.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 FFT on split real/imaginary arrays.
 *
 * Kept deliberately small: Core EQ needs one long convolution per measurement
 * (sweep deconvolution) and one short transform per impulse response, both
 * power-of-two sized, and nothing that justifies a native dependency.
 */
object Fft {

    fun nextPow2(n: Int): Int {
        require(n in 1..(1 shl 30)) { "FFT size out of range: $n" }
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    /** Forward transform when [inverse] is false; scaled inverse otherwise. */
    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        val n = re.size
        require(n == im.size) { "re and im differ in length: $n vs ${im.size}" }
        require(n > 0 && n and (n - 1) == 0) { "FFT length must be a power of two, got $n" }

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        val sign = if (inverse) 1.0 else -1.0
        var len = 2
        while (len <= n) {
            val ang = sign * 2.0 * PI / len
            val wRe = cos(ang)
            val wIm = sin(ang)
            val half = len shr 1
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until half) {
                    val a = start + k
                    val b = a + half
                    val xr = re[b] * curRe - im[b] * curIm
                    val xi = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    val nr = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nr
                }
                start += len
            }
            len = len shl 1
        }

        if (inverse) {
            val scale = 1.0 / n
            for (i in 0 until n) {
                re[i] *= scale
                im[i] *= scale
            }
        }
    }

    /** Linear convolution of two real signals, length a.size + b.size - 1. */
    fun convolve(a: DoubleArray, b: DoubleArray): DoubleArray {
        require(a.isNotEmpty() && b.isNotEmpty()) { "convolution needs two non-empty signals" }
        val outLen = a.size + b.size - 1
        val n = nextPow2(outLen)
        val aRe = DoubleArray(n); val aIm = DoubleArray(n)
        val bRe = DoubleArray(n); val bIm = DoubleArray(n)
        a.copyInto(aRe)
        b.copyInto(bRe)
        transform(aRe, aIm)
        transform(bRe, bIm)
        for (i in 0 until n) {
            val r = aRe[i] * bRe[i] - aIm[i] * bIm[i]
            val m = aRe[i] * bIm[i] + aIm[i] * bRe[i]
            aRe[i] = r
            aIm[i] = m
        }
        transform(aRe, aIm, inverse = true)
        return aRe.copyOf(outLen)
    }
}
