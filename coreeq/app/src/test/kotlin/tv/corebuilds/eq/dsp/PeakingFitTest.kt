package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.pow

/**
 * The peaking-filter fit: the last step between a correction curve and the
 * bands a TV actually receives.
 *
 * Nothing here compares against `tools/core_eq_dsp.py`, and that is deliberate.
 * The fit is a seeded coordinate descent: the reference draws its steps from
 * `numpy.random.default_rng`, the app from `java.util.Random`, so the two cannot
 * produce the same filter list. What can be held to account — and is, below —
 * is that the fit is deterministic within one process, stays inside the filter
 * budget, and reproduces the curve closely enough that the difference is below
 * audibility.
 */
class PeakingFitTest {

    private val freqs = DoubleArray(61) { 40.0 * 2.0.pow(it / 8.0) } // 1/8 octave, 40 Hz - 8 kHz

    private fun bump(fc: Double, gain: Double, width: Double): DoubleArray =
        DoubleArray(freqs.size) { exp(-(log2(freqs[it] / fc) * log2(freqs[it] / fc)) / (2.0 * width * width)) * gain }

    private fun fit(target: DoubleArray, n: Int = DspConstants.PEAKING_FILTERS) =
        Peaking.fitPeakingFilters(freqs, target, n)

    private fun residual(filters: List<PeakingFilter>, target: DoubleArray): DoubleArray {
        val sum = Peaking.filterSumDb(freqs, filters)
        return DoubleArray(freqs.size) { sum[it] - target[it] }
    }

    @Test
    fun refitsItsOwnFilterSumAlmostExactly() {
        // The strongest property available: a curve that *is* a sum of peaking
        // filters must come back as that shape, not an approximation of it.
        val known = listOf(
            PeakingFilter(120.0, 1.2, -5.0),
            PeakingFilter(900.0, 1.4, 2.5),
            PeakingFilter(4000.0, 0.9, -3.0)
        )
        val target = Peaking.filterSumDb(freqs, known)
        val fitted = fit(target)
        val error = residual(fitted, target)
        val rms = kotlin.math.sqrt(error.sumOf { it * it } / error.size)
        assertTrue("RMS fit error $rms dB", rms < 1.0)
        assertTrue("worst band ${error.maxOf { abs(it) }} dB", error.maxOf { abs(it) } < 2.5)
    }

    @Test
    fun aSmoothBassCutIsReproduced() {
        val target = bump(90.0, -6.0, 0.5)
        val error = residual(fit(target), target)
        val rms = kotlin.math.sqrt(error.sumOf { it * it } / error.size)
        assertTrue("RMS fit error $rms dB for a single smooth cut", rms < 1.0)
    }

    @Test
    fun theFitIsDeterministicWithinAProcess() {
        val target = bump(250.0, -4.0, 0.4)
        val first = fit(target)
        val second = fit(target)
        assertEquals(first.size, second.size)
        for (i in first.indices) {
            assertEquals(first[i].fc, second[i].fc, 0.0)
            assertEquals(first[i].q, second[i].q, 0.0)
            assertEquals(first[i].gain, second[i].gain, 0.0)
        }
    }

    @Test
    fun filtersStayInsideTheirDocumentedBounds() {
        val target = DoubleArray(freqs.size) { i -> 6.0 * kotlin.math.sin(i.toDouble()) }
        for (filter in fit(target)) {
            assertTrue("fc ${filter.fc} Hz", filter.fc > 0.0 && filter.fc.isFinite())
            assertTrue("q ${filter.q}", filter.q in DspConstants.PEAKING_MIN_Q..DspConstants.PEAKING_MAX_Q)
            assertTrue("gain ${filter.gain} dB", abs(filter.gain) <= DspConstants.MAX_BOOST_DB + 1e-9)
        }
    }

    @Test
    fun neverReturnsMoreFiltersThanTheBudget() {
        for (n in 1..DspConstants.PEAKING_FILTERS) {
            val target = DoubleArray(freqs.size) { i -> 5.0 * kotlin.math.cos(i.toDouble() / 2.0) }
            assertTrue("asked for $n filters, got ${fit(target, n).size}", fit(target, n).size <= n)
        }
    }

    @Test
    fun filtersComeBackSortedAndFreeOfVanishingGains() {
        val target = bump(1000.0, -5.0, 0.3)
        val fitted = fit(target)
        assertEquals(fitted.sortedBy { it.fc }, fitted)
        for (filter in fitted) {
            assertTrue("gain ${filter.gain} dB is below the reported threshold", abs(filter.gain) >= 0.05)
        }
    }

    @Test
    fun tooFewBandsMeansNoFiltersRatherThanAGuess() {
        val sparse = DoubleArray(4) { 100.0 + it * 100.0 }
        assertEquals(emptyList<PeakingFilter>(), Peaking.fitPeakingFilters(sparse, DoubleArray(4)))
    }

    @Test
    fun aZeroTargetProducesNothingToUndo() {
        val fitted = fit(DoubleArray(freqs.size))
        assertTrue("a flat target fitted ${fitted.size} filters", fitted.isEmpty())
    }

    @Test
    fun preampKeepsTheCascadeBelowUnity() {
        val filters = listOf(PeakingFilter(80.0, 1.0, 4.0), PeakingFilter(3000.0, 1.0, 2.0))
        assertEquals(-4.0, Peaking.preampDb(filters), 0.01)
        val sum = Peaking.filterSumDb(freqs, filters)
        val peak = sum.max() + Peaking.preampDb(filters)
        // preampDb is -max(single gain), the AutoEq convention. Two filters
        // whose skirts overlap can sum a few thousandths of a dB above that,
        // so the guard is a small bound rather than exactly zero; the applied
        // path takes its headroom from the summed curve (BandMapping), which
        // is exact.
        assertTrue("preamp + peak gain is $peak dB", peak <= 0.05)
    }

    @Test
    fun preampOfAnEmptyCascadeIsZero() {
        assertEquals(0.0, Peaking.preampDb(emptyList()), 0.0)
    }

    @Test
    fun peakingMagnitudeIsSymmetricInGain() {
        val boost = Peaking.peakingMagnitudeDb(freqs, 500.0, 1.2, 4.0)
        val cut = Peaking.peakingMagnitudeDb(freqs, 500.0, 1.2, -4.0)
        for (i in freqs.indices) assertEquals(boost[i], -cut[i], 1e-9)
    }

    @Test
    fun aFilterAboveNyquistIsClampedRatherThanExploding() {
        val value = Peaking.peakingMagnitudeDb(doubleArrayOf(DspConstants.FS / 2.0), 40_000.0, 1.0, 3.0)[0]
        assertTrue("value $value is not finite", value.isFinite())
    }
}
