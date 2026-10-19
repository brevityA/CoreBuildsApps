package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.corebuilds.eq.dsp.Correction
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.Fft
import tv.corebuilds.eq.dsp.MeasurementException
import tv.corebuilds.eq.dsp.MeasurementQuality
import tv.corebuilds.eq.dsp.Resample
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
        // The synthetic room has no diffuse field under 300 Hz, so its bass
        // sits ~8 dB under the target; it is lifted to the target first, or
        // the mode only shrinks a capped boost instead of drawing a cut.
        val base = bassAtTarget(reverbRoom(0.4))
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
            if (f < result.transitionHz) {
                assertTrue("boost $g at $f from one seat", g <= SweepAnalysis.SINGLE_SEAT_BOOST_DB + 1e-9)
            }
        }
    }

    // --- accuracy (1.3.2 audit) ---------------------------------------------

    /** An exponentially decaying noise tail with a known RT60, plus a noise floor [rangeDb] under its start. */
    private fun decayTail(rt60: Double, rangeDb: Double, seed: Long = 3L): Pair<DoubleArray, Double> {
        val n = 2 * capFs
        val rnd = Random(seed)
        val k = ln(10.0.pow(3.0)) / rt60
        val noisePower = 10.0.pow(-rangeDb / 10.0)
        val ir = DoubleArray(n) { i ->
            rnd.nextGaussian() * exp(-k * i / capFs) + rnd.nextGaussian() * sqrt(noisePower)
        }
        return Pair(ir, noisePower)
    }

    @Test
    fun t20ReadsTrueWhenTheDecayStandsClearOfTheNoise() {
        for (range in doubleArrayOf(50.0, 40.0)) {
            val (ir, noise) = decayTail(0.6, range)
            val d = SweepAnalysis.decay(ir, 0, capFs, noise)
            assertNotNull("RT60 at $range dB of decay range", d.rt60Seconds)
            // 1.3.1 read 3.5-10 % short here; the tail compensation keeps it true.
            assertEquals("RT60 at $range dB", 0.6, d.rt60Seconds!!, 0.03)
            assertEquals("decay range", range, d.decayRangeDb!!, 3.0)
        }
    }

    @Test
    fun t20IsRefusedRatherThanReadShortWhenTheDecayIsShallow() {
        val (ir, noise) = decayTail(0.6, 28.0)
        val d = SweepAnalysis.decay(ir, 0, capFs, noise)
        assertEquals("no RT60 under ${SweepAnalysis.MIN_DECAY_RANGE_DB} dB of range", null, d.rt60Seconds)
        assertEquals(28.0, d.decayRangeDb!!, 3.0)
    }

    /**
     * The sweep is lowered 10 dB at a time against the same noise until the
     * analysis refuses it. 1.3.1 kept returning an RT60 all the way down,
     * reading ever shorter. Now every RT60 it returns is the room's, and the
     * RT60 goes (the transition falling back to its default) before the
     * capture is refused.
     */
    @Test
    fun asTheSweepSinksIntoTheNoiseRt60GoesRatherThanReadingShort() {
        val readings = mutableListOf<Pair<Double, Double?>>()
        var gain = 0.5
        while (gain > 1e-5) {
            val result = try {
                SweepAnalysis.analyze(capture(room = reverbRoom(0.5), gain = gain, noiseDbfs = -40.0), capFs, "flat", 54.0)
            } catch (e: MeasurementException) {
                break
            }
            readings += Pair(result.snrDb, result.rt60Seconds)
            result.rt60Seconds?.let { assertEquals("RT60 at ${result.snrDb} dB SNR", 0.5, it, 0.06) }
                ?: assertEquals(DspConstants.UNKNOWN_ROOM_TRANSITION_HZ, result.transitionHz, 1e-9)
            gain /= sqrt(10.0)
        }
        assertTrue("the sweep should be refused eventually: $readings", gain > 1e-5)
        assertEquals("the quietest accepted sweep should have no RT60: $readings", null, readings.last().second)
        assertTrue("a loud sweep should time the room: $readings", readings.first().second != null)
    }

    @Test
    fun thePeakIsReadBetweenSamples() {
        // An arrival half-way between two samples, band-limited to 0.45 x the
        // rate as the capture's anti-alias filter leaves it: the largest
        // sample is 0.70 of the true peak, 3.1 dB short.
        val ir = DoubleArray(96) { n ->
            val x = 0.9 * (n - 48.5)
            sin(PI * x) / (PI * x)
        }
        var peak = 0
        for (i in ir.indices) if (abs(ir[i]) > abs(ir[peak])) peak = i
        assertEquals(0.70, abs(ir[peak]), 0.01)
        assertEquals(1.0, SweepAnalysis.interpolatedPeak(ir, peak), 0.01)
    }

    @Test
    fun noBandStraddlesTheCapturesAntiAliasEdge() {
        val result = SweepAnalysis.analyze(capture(room = doubleArrayOf(1.0)), capFs, "flat", 54.0)
        val top = result.centresHz.maxOrNull()!!
        assertEquals("16 kHz capture keeps bands up to 6.3 kHz", 6300.0, top, 1e-9)
        assertEquals("the window cannot resolve 20 and 25 Hz", SweepAnalysis.MIN_BAND_HZ, result.centresHz.first(), 1e-9)
        assertTrue("the loopback must not report a dip at the top: ${result.nullMask.toList()}", result.nullMask.none { it })
    }

    @Test
    fun aRecordingThatLostAStretchIsRefused() {
        val lost = 128 // 8 ms: one dropped Bluetooth packet's worth
        fun drop(x: DoubleArray, atS: Double) =
            DoubleArray(x.size) { i -> if (i < (atS * capFs).toInt()) x[i] else x.getOrElse(i + lost) { 0.0 } }
        val room = reverbRoom(0.5)
        // The markers catch it: 8 ms over 10.6 s is -755 ppm, more than a
        // clock drifts. 4.35 s into the sweep (about 400 Hz) the bass timing
        // below does not: the 250 Hz band stays in phase and 1.3.1 accepted it.
        try {
            SweepAnalysis.analyze(drop(capture(room = room), 0.12 + Sweep.SWEEP_OFFSET_SECONDS + 4.35), capFs, "flat", 54.0)
            fail("a recording with 8 ms missing must not produce a profile")
        } catch (e: MeasurementException) {
            assertTrue(e.message!!, e.message!!.contains("went missing") && e.message!!.contains("timing beeps"))
        }
        // Without markers the bass bands' timing still gives a loss at 4 s (about 320 Hz) away.
        try {
            SweepAnalysis.analyze(drop(capture(room = room, stimulus = Sweep.generate(playFs)), 0.12 + 4.0), capFs, "flat", 54.0)
            fail("a recording with 8 ms missing must not produce a profile")
        } catch (e: MeasurementException) {
            assertTrue(e.message!!, e.message!!.contains("went missing") && e.message!!.contains("every bass band"))
        }
        // The same capture intact still measures.
        SweepAnalysis.analyze(capture(room = room), capFs, "flat", 54.0)
    }

    /**
     * The remote's clock runs 80 ppm fast. Through 1.3.1 nothing measured it:
     * the 10 s sweep smeared and the SNR, which is the score, fell 2.7 dB in
     * this room. The markers read the drift, the capture is stretched back,
     * and the SNR is the drift-free one again.
     */
    @Test
    fun clockDriftIsMeasuredAndTakenOut() {
        val room = reverbRoom(0.5)
        val clean = SweepAnalysis.analyze(capture(room = room), capFs, "flat", 54.0)
        assertEquals("no drift reads as none", 0.0, clean.driftPpm!!, 0.5)
        for (ppm in doubleArrayOf(80.0, -150.0)) {
            val drifted = SweepAnalysis.analyze(capture(room = room, driftPpm = ppm), capFs, "flat", 54.0)
            assertEquals("drift read", ppm, drifted.driftPpm!!, 1.0)
            assertEquals("SNR after taking $ppm ppm out", clean.snrDb, drifted.snrDb, 0.3)
            for (i in clean.centresHz.indices) {
                assertEquals("band ${clean.centresHz[i]} at $ppm ppm", clean.measuredDb[i], drifted.measuredDb[i], 0.3)
            }
        }
        val unmarked = SweepAnalysis.analyze(capture(room = room, driftPpm = 80.0, stimulus = Sweep.generate(playFs)), capFs, "flat", 54.0)
        assertEquals("no markers, no drift reading", null, unmarked.driftPpm)
        assertTrue("80 ppm left in costs SNR: ${unmarked.snrDb} vs ${clean.snrDb}", unmarked.snrDb < clean.snrDb - 1.5)
    }

    /**
     * A remote whose codec runs at 8 kHz hears nothing above about 3.6 kHz.
     * Through 1.3.1 the 5 and 6.3 kHz bands read as deep dips and the 4 kHz
     * band, half inside the codec's edge, as a smaller one the corrector
     * boosted. The band SNR finds where the capture stops: the measurement
     * ends below that edge and nothing above it is corrected.
     */
    @Test
    fun aRemoteThatStopsAt4kHzEndsTheMeasurementThere() {
        val result = SweepAnalysis.analyze(capture(room = reverbRoom(0.4), codecHz = 3600.0), capFs, "dialogue", 54.0)
        val top = result.centresHz.last()
        assertTrue("an 8 kHz codec's capture should end at 2.5-3.15 kHz, ended at $top", top in 2500.0..3150.0)
        val full = SweepAnalysis.analyze(capture(room = reverbRoom(0.4)), capFs, "dialogue", 54.0)
        assertEquals("a full-band remote keeps 6.3 kHz", 6300.0, full.centresHz.last(), 1e-9)
        for (i in result.centresHz.indices) {
            if (result.centresHz[i] >= 1000.0) {
                assertEquals("the codec must not bend ${result.centresHz[i]} Hz", full.measuredDb[i], result.measuredDb[i], 1.0)
            }
        }
    }

    /**
     * An air conditioner's rumble, strong under 80 Hz, under a sweep from a
     * far seat. It buries the 63 Hz band while the broadband SNR stays over
     * 44 dB, an excellent score, because the rumble is a sliver of the band
     * the broadband figure averages; through 1.3.1 the corrector then cut
     * the rumble's level out of the 63 Hz band. Now that band is shown and
     * left alone.
     */
    @Test
    fun rumbleThatBuriesTheBassLeavesItUncorrected() {
        val room = peaking(bassAtTarget(reverbRoom(0.4)), 63.0, 4.0, 8.0)
        // The TV 26 dB quieter than the default capture, as at a far seat.
        val quiet = SweepAnalysis.analyze(capture(room = room, gain = 0.025), capFs, "flat", 54.0)
        val i63 = quiet.centresHz.toList().indexOf(63.0)
        assertTrue("the 63 Hz mode is cut when the room is quiet: ${quiet.correctionDb[i63]}", quiet.correctionDb[i63] < -1.0)
        val noisy = SweepAnalysis.analyze(capture(room = room, gain = 0.025, extraNoise = rumble(0.13)), capFs, "flat", 54.0)
        assertTrue("the broadband SNR should still score the recording excellent: ${noisy.snrDb}", noisy.snrDb > 44.0)
        assertTrue("the 63 Hz band should read as noisy: ${noisy.bandSnrDb[i63]}", noisy.bandSnrDb[i63] < SweepAnalysis.BAND_MIN_SNR_DB)
        // Left alone: zero before the curve is smoothed and re-levelled, so
        // only the whole curve's level offset is left there, not the cut.
        assertEquals("a noisy band is not cut", 0.0, noisy.correctionDb[i63], 0.5)
        val i1k = noisy.centresHz.toList().indexOf(1000.0)
        assertTrue("1 kHz is untouched by rumble: ${noisy.bandSnrDb[i1k]}", noisy.bandSnrDb[i1k] > 30.0)
    }

    /**
     * Mids that ring for 0.7 s under a louder treble that dies in 0.3 s. A
     * broadband T20 follows the treble, which holds most of the energy, and
     * read 0.41 s here; the octave T20s keep them apart, and the 500 Hz-1 kHz
     * mean is the RT60 the Schroeder frequency is defined on. In a 150 m3
     * room, where the 400 Hz cap does not bind, that moves the transition
     * from about 209 Hz to about 276 Hz.
     */
    @Test
    fun eachOctaveIsTimedOnItsOwn() {
        val n = (1.5 * playFs).toInt()
        val rnd = Random(21L)
        val start = (0.004 * playFs).toInt()
        fun tail(rt60: Double, amp: Double) = DoubleArray(n) { i ->
            if (i < start) 0.0 else amp * rnd.nextGaussian() * exp(-ln(1000.0) / rt60 * i / playFs)
        }
        val mids = lowpassBiquad(lowpassBiquad(highpass(highpass(tail(0.7, 0.08), 300.0), 300.0), 2000.0), 2000.0)
        val treble = highpass(highpass(tail(0.3, 0.24), 2000.0), 2000.0)
        val room = DoubleArray(n) { mids[it] + treble[it] }.also { it[0] += 1.0 }
        val cap = capture(room = room)
        val result = SweepAnalysis.analyze(cap, capFs, "flat", 150.0)
        assertEquals("500 Hz octave", 0.7, result.rt60ByOctave[500.0]!!, 0.07)
        assertEquals("1 kHz octave", 0.7, result.rt60ByOctave[1000.0]!!, 0.07)
        assertEquals("RT60 is the mid-frequency mean", 0.7, result.rt60Seconds!!, 0.07)
        assertEquals(4000.0 * sqrt(result.rt60Seconds!! / 150.0), result.transitionHz, 1e-6)
        val ir = SweepAnalysis.impulseResponse(cap, capFs)
        var peak = 0
        for (i in ir.indices) if (abs(ir[i]) > abs(ir[peak])) peak = i
        val noise = ir.copyOfRange(peak - (0.3 * capFs).toInt(), peak - (0.02 * capFs).toInt()).let { w -> w.sumOf { it * it } / w.size }
        val broadband = SweepAnalysis.rt60Seconds(ir, peak, capFs, noise)
        assertTrue("the broadband T20 follows the treble: $broadband", broadband != null && broadband < 0.5)
        assertTrue("the transition from it would be lower", result.transitionHz - 4000.0 * sqrt(broadband!! / 150.0) > 40.0)
    }

    @Test
    fun theRolloffIsWhereTheSpeakerStopsNotWhereNoiseClimbsBack() {
        val freqs = doubleArrayOf(31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0)
        // A soundbar high-passed at 70 Hz at 35 dB SNR (the audit's case):
        // noise lifts 31.5-50 Hz back within 6 dB of the plateau.
        val measured = doubleArrayOf(-5.5, -4.0, -5.0, -9.0, -3.0, -2.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        assertEquals(80.0, Correction.detectLowRolloff(freqs, measured), 1e-9)
        // A speaker that holds to the bottom still gets the full floor.
        val flat = DoubleArray(freqs.size)
        assertEquals(DspConstants.F_MIN, Correction.detectLowRolloff(freqs, flat), 1e-9)
    }

    @Test
    fun boostsBelowTheTransitionAreCappedForOneSeat() {
        val freqs = DspConstants.ISO_CENTRES_HZ.filter { it in 31.5..8000.0 }.toDoubleArray()
        // A broad 6 dB dip from 63 to 125 Hz: not a null, so 1.3.1 lifted it up to the full 6 dB.
        val measured = DoubleArray(freqs.size) { if (freqs[it] in 63.0..125.0) -6.0 else 0.0 }
        val target = DoubleArray(freqs.size)
        val open = Correction.calculateCorrectionCurve(freqs, measured, target, transitionHz = 300.0, nullMask = BooleanArray(freqs.size))
        val capped = Correction.calculateCorrectionCurve(
            freqs, measured, target, transitionHz = 300.0, nullMask = BooleanArray(freqs.size),
            maxBoostBelowTransition = SweepAnalysis.SINGLE_SEAT_BOOST_DB
        )
        assertTrue("uncapped lifts the dip by more than 2 dB: ${open.max()}", open.max() > 3.0)
        for (i in freqs.indices) {
            if (freqs[i] < 300.0) assertTrue("${capped[i]} at ${freqs[i]}", capped[i] <= SweepAnalysis.SINGLE_SEAT_BOOST_DB + 1e-9)
        }
        // Cuts are not capped: a 6 dB peak is still cut.
        val peaky = DoubleArray(freqs.size) { if (freqs[it] in 63.0..125.0) 6.0 else 0.0 }
        val cut = Correction.calculateCorrectionCurve(
            freqs, peaky, target, transitionHz = 300.0, nullMask = BooleanArray(freqs.size),
            maxBoostBelowTransition = SweepAnalysis.SINGLE_SEAT_BOOST_DB
        )
        assertTrue("the peak is still cut: ${cut.min()}", cut.min() < -4.0)
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

    /**
     * 1.3.0's score lowered when the room size was entered: a known size moved
     * the transition from the 300 Hz fallback to the room's real value, and
     * the transition carried points. The recording score must not depend on
     * what the user says about the room.
     */
    @Test
    fun recordingScoreDoesNotDependOnTheRoomSize() {
        val cap = capture(room = reverbRoom(0.5))
        val unknown = SweepAnalysis.analyze(cap, capFs, "flat", null)
        val known = SweepAnalysis.analyze(cap, capFs, "flat", 54.0)
        assertTrue("the transition should move with a known size", known.transitionHz != unknown.transitionHz)
        assertEquals(MeasurementQuality.score(unknown), MeasurementQuality.score(known))
    }

    /**
     * A clean recording of a room with two ordinary reflections (a wall
     * behind the sofa, a side wall) has 1/3-octave dips the correction leaves
     * alone. 1.3.0 scored that recording 69 because the dips cost the whole
     * null share; they belong to the room, so the recording still scores
     * excellent and the dips are reported.
     */
    @Test
    fun roomReflectionsDoNotLowerTheRecordingScore() {
        val room = reflection(reflection(reverbRoom(0.5), 1.7, -0.85), 4.3, -0.7)
        val result = SweepAnalysis.analyze(capture(room = room), capFs, "flat", 54.0)
        val facts = MeasurementQuality.room(result)
        assertTrue("the reflections should leave dips, found ${facts.dips}", facts.dips >= 3)
        val score = MeasurementQuality.score(result)
        assertTrue("a clean recording of this room scored $score", score >= 85)
    }

    /** A reflection: x[n] + [gain] * x[n - delay], the comb filter a wall makes, at the playback rate. */
    private fun reflection(x: DoubleArray, delayMs: Double, gain: Double): DoubleArray {
        val d = (delayMs * playFs / 1000).toInt()
        return DoubleArray(x.size + d) { i ->
            (if (i < x.size) x[i] else 0.0) + (if (i - d in x.indices) gain * x[i - d] else 0.0)
        }
    }

    /**
     * What the remote hears: the app's [Sweep.stimulus] through [room],
     * band-limited and decimated to 16 kHz, delayed by [latencyS], then noise.
     * [driftPpm] runs the capture clock that much fast: capture sample i is
     * read at i / (1 + drift) on the player's time base.
     */
    private fun capture(
        room: DoubleArray,
        latencyS: Double = 0.12,
        gain: Double = 0.5,
        noiseDbfs: Double = -70.0,
        seed: Long = 5L,
        driftPpm: Double = 0.0,
        stimulus: DoubleArray = Sweep.stimulus(playFs),
        extraNoise: DoubleArray? = null,
        codecHz: Double? = null
    ): DoubleArray {
        val wet = Fft.convolve(stimulus, room)
        val lp = lowpass(7400.0, playFs, 255)
        val filtered = Fft.convolve(wet, lp)
        val delay = lp.size / 2
        val full = DoubleArray((filtered.size - delay) / 3) { filtered[it * 3 + delay] }
        // An 8 kHz codec, upsampled back to 16 kHz: nothing above [codecHz].
        val decimated = if (codecHz == null) full else {
            val c = lowpass(codecHz, capFs, 255)
            Fft.convolve(full, c).copyOfRange(c.size / 2, c.size / 2 + full.size)
        }
        val total = ((Sweep.STIMULUS_SECONDS + SweepAnalysis.TAIL_SECONDS) * capFs).toInt()
        val lat = (latencyS * capFs).toInt()
        val timed = if (driftPpm == 0.0) decimated else Resample.stretch(decimated, 1.0 / (1.0 + driftPpm * 1e-6))
        val rnd = Random(seed)
        val noiseAmp = 10.0.pow(noiseDbfs / 20.0)
        return DoubleArray(total) { i ->
            val s = i - lat
            val sig = if (s in timed.indices) timed[s] * gain else 0.0
            sig + rnd.nextGaussian() * noiseAmp + (extraNoise?.getOrElse(i) { 0.0 } ?: 0.0)
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

    /** [room] with its bass lifted by 8 dB under 250 Hz, which puts [reverbRoom]'s bass back at the flat target. */
    private fun bassAtTarget(room: DoubleArray): DoubleArray {
        val low = lowpassBiquad(lowpassBiquad(room, 250.0), 250.0)
        val lift = 10.0.pow(8.0 / 20.0) - 1.0
        return DoubleArray(room.size) { room[it] + lift * low[it] }
    }

    /** Capture-rate rumble: white noise through four poles at 60 Hz, [rms] after filtering. */
    private fun rumble(rms: Double, seed: Long = 17L): DoubleArray {
        val n = ((Sweep.STIMULUS_SECONDS + SweepAnalysis.TAIL_SECONDS) * capFs).toInt()
        val rnd = Random(seed)
        val white = DoubleArray(n) { rnd.nextGaussian() }
        val low = lowpassBiquad(lowpassBiquad(white, 60.0, capFs), 60.0, capFs)
        val scale = rms / sqrt(low.sumOf { it * it } / n)
        for (i in low.indices) low[i] *= scale
        return low
    }

    /** RBJ second-order low-pass (Q 0.707) at [fs], the playback rate unless given. */
    private fun lowpassBiquad(x: DoubleArray, fc: Double, fs: Int = playFs): DoubleArray {
        val w0 = 2.0 * PI * fc / fs
        val alpha = sin(w0) / (2.0 * 0.7071)
        val c = cos(w0)
        val b0 = (1 - c) / 2; val b1 = 1 - c; val b2 = (1 - c) / 2
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
