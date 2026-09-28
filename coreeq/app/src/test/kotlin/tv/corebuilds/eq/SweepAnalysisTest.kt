package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.Fft
import tv.corebuilds.eq.dsp.MeasurementException
import tv.corebuilds.eq.dsp.Sweep
import tv.corebuilds.eq.dsp.SweepAnalysis
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The measurement chain against physical ground truth.
 *
 * Each case plays the real 48 kHz stimulus through a synthetic room, low-passes
 * and decimates to the remote mic's 16 kHz, adds latency and noise, and checks
 * that the analysis recovers what the room was built with.
 */
class SweepAnalysisTest {

    private val playFs = DspConstants.FS
    private val capFs = 16000

    @Test
    fun fftRoundTripsAndConvolvesLikeTheDefinition() {
        val a = doubleArrayOf(1.0, 2.0, 3.0)
        val b = doubleArrayOf(0.0, 1.0, 0.5)
        val c = Fft.convolve(a, b)
        val expected = doubleArrayOf(0.0, 1.0, 2.5, 4.0, 1.5)
        for (i in expected.indices) assertEquals(expected[i], c[i], 1e-9)
    }

    @Test
    fun loopbackMeasuresFlat() {
        val result = SweepAnalysis.analyze(capture(room = doubleArrayOf(1.0)), capFs, "flat", 54.0)
        val inBand = result.centresHz.indices.filter { result.centresHz[it] in 63.0..6300.0 }
        val mean = inBand.map { result.measuredDb[it] }.average()
        for (i in inBand) {
            assertEquals("loopback at ${result.centresHz[i]} Hz", mean, result.measuredDb[i], 1.5)
        }
        assertTrue("SNR ${result.snrDb}", result.snrDb > 40.0)
    }

    @Test
    fun latencyIsRecovered() {
        val result = SweepAnalysis.analyze(capture(room = doubleArrayOf(1.0), latencyS = 0.35), capFs, "flat", 54.0)
        assertEquals(350.0, result.latencyMs, 3.0)
    }

    @Test
    fun rt60IsRecoveredFromADecayingRoom() {
        val result = SweepAnalysis.analyze(capture(room = reverbRoom(0.5)), capFs, "flat", 54.0)
        val rt60 = result.rt60Seconds
        assertNotNull("RT60 should be fitted", rt60)
        assertEquals(0.5, rt60!!, 0.1)
        // 54 m3 at 0.5 s: Schroeder ~192 Hz, transition = min(2 * fs, 400)
        assertEquals(385.0, result.transitionHz, 40.0)
    }

    @Test
    fun aRoomModeShowsUpAndIsCut() {
        // Differential: the same reverberant room with and without an 80 Hz
        // mode, so the room's own colouring cancels out of the comparison.
        val base = reverbRoom(0.4)
        val without = SweepAnalysis.analyze(capture(room = base), capFs, "flat", 54.0)
        val with = SweepAnalysis.analyze(capture(room = peaking(base, 80.0, 4.0, 9.0)), capFs, "flat", 54.0)
        val i80 = with.centresHz.toList().indexOf(80.0)
        val rise = with.measuredDb[i80] - without.measuredDb[i80]
        assertTrue("a +9 dB mode raised 80 Hz by only $rise dB", rise > 5.0)
        assertTrue("a modal room must pass the gate at 80 Hz", with.minPhaseOk[i80])
        val deeper = without.correctionDb[i80] - with.correctionDb[i80]
        assertTrue("the mode deepened the 80 Hz cut by only $deeper dB", deeper > 2.0)
    }

    @Test
    fun aSweepBuriedInNoiseIsRefusedWithAReason() {
        try {
            SweepAnalysis.analyze(capture(room = doubleArrayOf(1.0), gain = 0.0005, noiseDbfs = -30.0), capFs, "flat", 54.0)
            fail("a sweep under the noise must not produce a profile")
        } catch (e: MeasurementException) {
            assertTrue(e.message!!, e.message!!.contains("above the room's noise"))
        }
    }

    @Test
    fun correctionStaysInsideTheTrustedBand() {
        val result = SweepAnalysis.analyze(capture(room = peaking(reverbRoom(0.4), 60.0, 3.0, 8.0)), capFs, "dialogue", 40.0)
        for (i in result.centresHz.indices) {
            val f = result.centresHz[i]
            val g = result.correctionDb[i]
            if (f < result.floorHz || f > DspConstants.F_MAX) assertEquals("outside band at $f", 0.0, g, 1e-9)
            assertTrue("gain $g at $f", g <= DspConstants.MAX_BOOST_DB + 1e-9 && g >= -DspConstants.MAX_CUT_DB - 1e-9)
            if (f >= result.transitionHz) assertTrue("gate closed above the transition at $f", result.minPhaseOk[i])
        }
    }

    // --- minimum-phase gate ---------------------------------------------------

    private fun gateAt(ir: DoubleArray, centres: DoubleArray): BooleanArray {
        var peak = 0
        for (i in ir.indices) if (abs(ir[i]) > abs(ir[peak])) peak = i
        val (f, ex) = SweepAnalysis.excessGroupDelayMs(ir, peak, capFs)
        return SweepAnalysis.bandMinPhaseOk(centres, f, ex)
    }

    private val gateBands = doubleArrayOf(50.0, 63.0, 80.0, 250.0, 1000.0, 4000.0)

    @Test
    fun theIdentityIsMinimumPhaseEverywhere() {
        val ir = DoubleArray(8192).also { it[0] = 1.0 }
        assertTrue(gateAt(ir, gateBands).all { it })
    }

    @Test
    fun latencyIsNotExcessGroupDelay() {
        // 400 samples at 16 kHz is 25 ms, five times the tolerance.
        val ir = DoubleArray(8192).also { it[400] = 1.0 }
        assertTrue(gateAt(ir, gateBands).all { it })
    }

    @Test
    fun anAllpassIsGatedAtItsCentreOnly() {
        val gate = gateAt(biquad(impulse(16384), 63.0, 2.0, capFs, allpass = true), gateBands)
        assertTrue("63 Hz should be gated: ${gate.toList()}", !gate[1])
        assertTrue("1 kHz should pass: ${gate.toList()}", gate[4])
    }

    @Test
    fun aRoomModeIsMinimumPhase() {
        val gate = gateAt(biquad(impulse(16384), 63.0, 4.0, capFs, gainDb = 9.0), gateBands)
        assertTrue("a resonance must pass the gate: ${gate.toList()}", gate[0] && gate[1] && gate[2])
    }

    // --- synthetic room -----------------------------------------------------

    private fun capture(
        room: DoubleArray,
        latencyS: Double = 0.12,
        gain: Double = 0.5,
        noiseDbfs: Double = -70.0,
        seed: Long = 5L
    ): DoubleArray {
        val sweep = Sweep.generate(playFs)
        val wet = Fft.convolve(sweep, room)
        val lp = lowpass(7400.0, playFs, 255)
        val filtered = Fft.convolve(wet, lp)
        val delay = lp.size / 2
        val decimated = DoubleArray((filtered.size - delay) / 3) { filtered[it * 3 + delay] }
        val total = ((Sweep.SECONDS + SweepAnalysis.TAIL_SECONDS) * capFs).toInt()
        val lat = (latencyS * capFs).toInt()
        val rnd = Random(seed)
        val noiseAmp = 10.0.pow(noiseDbfs / 20.0)
        return DoubleArray(total) { i ->
            val s = i - lat
            val sig = if (s in decimated.indices) decimated[s] * gain else 0.0
            sig + rnd.nextGaussian() * noiseAmp
        }
    }

    /**
     * A physically shaped room: the direct sound, a diffuse tail that decays
     * over [rt60] but only above ~300 Hz (below the Schroeder frequency a room
     * has no diffuse field), and two low-frequency modes, which are resonances
     * and therefore minimum phase.
     */
    private fun reverbRoom(rt60: Double, seed: Long = 9L): DoubleArray {
        val n = (1.2 * playFs).toInt()
        val rnd = Random(seed)
        val decay = ln(10.0.pow(3.0)) / rt60 // amplitude e-folding for -60 dB over rt60
        val tail = DoubleArray(n)
        val start = (0.004 * playFs).toInt()
        for (i in start until n) {
            tail[i] = 0.08 * rnd.nextGaussian() * exp(-decay * i / playFs)
        }
        val diffuse = highpass(highpass(tail, 300.0), 300.0)
        val out = DoubleArray(n) { diffuse[it] }
        out[0] += 1.0
        return biquad(biquad(out, 45.0, 5.0, playFs, gainDb = 5.0), 115.0, 5.0, playFs, gainDb = 4.0)
    }

    private fun highpass(x: DoubleArray, fc: Double): DoubleArray {
        val w0 = 2.0 * PI * fc / playFs
        val alpha = sin(w0) / (2.0 * 0.7071)
        val c = cos(w0)
        val b0 = (1 + c) / 2; val b1 = -(1 + c); val b2 = (1 + c) / 2
        val a0 = 1 + alpha; val a1 = -2 * c; val a2 = 1 - alpha
        val y = DoubleArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val v = (b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x[i]; y2 = y1; y1 = v
            y[i] = v
        }
        return y
    }

    private fun impulse(n: Int) = DoubleArray(n).also { it[0] = 1.0 }

    /** RBJ peaking biquad run over [x] at the playback rate. */
    private fun peaking(x: DoubleArray, fc: Double, q: Double, gainDb: Double): DoubleArray =
        biquad(x, fc, q, playFs, gainDb = gainDb)

    /** RBJ peaking (or, with [allpass], second-order allpass) biquad run over [x]. */
    private fun biquad(
        x: DoubleArray, fc: Double, q: Double, fs: Int,
        gainDb: Double = 0.0, allpass: Boolean = false
    ): DoubleArray {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / fs
        val alpha = sin(w0) / (2.0 * q)
        val b0: Double; val b1: Double; val b2: Double; val a0: Double; val a1: Double; val a2: Double
        if (allpass) {
            b0 = 1 - alpha; b1 = -2 * cos(w0); b2 = 1 + alpha
            a0 = 1 + alpha; a1 = -2 * cos(w0); a2 = 1 - alpha
        } else {
            b0 = 1 + alpha * a; b1 = -2 * cos(w0); b2 = 1 - alpha * a
            a0 = 1 + alpha / a; a1 = -2 * cos(w0); a2 = 1 - alpha / a
        }
        val y = DoubleArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val v = (b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x[i]; y2 = y1; y1 = v
            y[i] = v
        }
        return y
    }

    private fun lowpass(cutHz: Double, fs: Int, taps: Int): DoubleArray {
        val m = taps - 1
        val fc = cutHz / fs
        val h = DoubleArray(taps) { i ->
            val k = i - m / 2.0
            val sinc = if (abs(k) < 1e-12) 2 * fc else sin(2 * PI * fc * k) / (PI * k)
            val w = 0.42 - 0.5 * cos(2 * PI * i / m) + 0.08 * cos(4 * PI * i / m)
            sinc * w
        }
        val s = h.sum()
        for (i in h.indices) h[i] /= s
        return h
    }

    @Suppress("unused")
    private fun rms(x: DoubleArray) = sqrt(x.sumOf { it * it } / x.size)
}
