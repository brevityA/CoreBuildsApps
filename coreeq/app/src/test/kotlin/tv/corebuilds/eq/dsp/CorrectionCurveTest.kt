package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/**
 * The correction curve's contract, independent of any microphone: what it may
 * never exceed, never touch, and never invent.
 *
 * These are the properties the app promises in `CorrectionLimits` and in the
 * research notes. They are asserted here on synthetic inputs so a change to
 * the curve maths fails a test instead of a user's room.
 */
class CorrectionCurveTest {

    private val freqs = DoubleArray(121) { 31.5 * 2.0.pow(it / 12.0) } // 1/12 octave, 31.5 Hz - 8 kHz

    private fun measured(vararg bumps: Triple<Double, Double, Double>): DoubleArray {
        val out = DoubleArray(freqs.size)
        for ((fc, gain, width) in bumps) {
            for (i in freqs.indices) {
                val d = log2(freqs[i] / fc)
                out[i] += gain * kotlin.math.exp(-(d * d) / (2.0 * width * width))
            }
        }
        return out
    }

    private fun correction(
        measuredDb: DoubleArray,
        target: DoubleArray = DoubleArray(freqs.size),
        fMin: Double = DspConstants.F_MIN,
        fMax: Double = DspConstants.F_MAX,
        transitionHz: Double = DspConstants.DEFAULT_TRANSITION_HZ,
        cutOnly: Boolean = false,
        nullMask: BooleanArray? = null
    ) = Correction.calculateCorrectionCurve(
        freqs, measuredDb, target,
        fMin = fMin, fMax = fMax, transitionHz = transitionHz,
        cutOnly = cutOnly, nullMask = nullMask
    )

    @Test
    fun nothingOutsideTheTrustedBandIsEverAskedFor() {
        val result = correction(measured(Triple(50.0, -12.0, 0.5), Triple(10000.0, 9.0, 0.5)),
            fMin = 100.0, fMax = 4000.0)
        for (i in freqs.indices) {
            if (freqs[i] < 100.0 || freqs[i] > 4000.0) {
                assertEquals("${freqs[i]} Hz must be exactly zero", 0.0, result[i], 0.0)
            }
        }
    }

    @Test
    fun aDeepNullIsNeverBoosted() {
        val room = measured(Triple(80.0, -18.0, 0.12))
        val nulls = Correction.detectNulls(freqs, room)
        assertTrue("the synthetic 80 Hz cancellation must be detected", nulls.any { it })
        val result = correction(room, nullMask = nulls)
        for (i in freqs.indices) {
            if (nulls[i]) {
                assertTrue(
                    "null band ${freqs[i]} Hz was boosted by ${result[i]} dB",
                    result[i] <= 1e-9
                )
            }
        }
    }

    @Test
    fun boostsRespectTheSingleSeatCapBelowTheTransition() {
        val result = correction(measured(Triple(70.0, -20.0, 0.4)))
        val below = freqs.indices.filter { freqs[it] < 400.0 }
        for (i in below) {
            assertTrue(
                "bass boost ${result[i]} dB at ${freqs[i]} Hz exceeds the single-seat cap",
                result[i] <= SweepAnalysis.SINGLE_SEAT_BOOST_DB + 1e-9
            )
        }
    }

    @Test
    fun cutsAreAllowedDeeperThanBoostsAnywhere() {
        val result = correction(measured(Triple(120.0, 12.0, 0.4), Triple(3000.0, 12.0, 0.4)))
        val deepestCut = result.min()
        val highestBoost = result.max()
        assertTrue("a modal peak should be cut", deepestCut < -3.0)
        assertTrue("a boost may never exceed the cap", highestBoost <= DspConstants.MAX_BOOST_DB + 1e-9)
        assertTrue("a cut may not exceed the cut budget", deepestCut >= -DspConstants.MAX_CUT_DB - 1e-9)
    }

    @Test
    fun cutOnlyNeverProducesAPositiveGain() {
        val result = correction(measured(Triple(70.0, -15.0, 0.3), Triple(120.0, 9.0, 0.3)), cutOnly = true)
        for (value in result) assertTrue("cutOnly produced $value dB", value <= 1e-9)
    }

    @Test
    fun theInteriorNeverRisesFasterThanTheSlopeLimitPerOctave() {
        val result = correction(measured(Triple(63.0, -15.0, 0.25), Triple(100.0, 12.0, 0.25)))
        // The one-octave raised-cosine fade at each end is *meant* to fall away
        // faster than the slope limit, so the check starts where the fade ends.
        for (i in 0 until freqs.size - 1) {
            if (freqs[i] < 80.0 || freqs[i + 1] > 6000.0) continue
            val octaves = log2(freqs[i + 1] / freqs[i])
            val slope = abs(result[i + 1] - result[i]) / octaves
            assertTrue(
                "slope of $slope dB/oct between ${freqs[i]} and ${freqs[i + 1]} Hz",
                slope <= DspConstants.MAX_SLOPE_DB_PER_OCT + 0.35
            )
        }
    }

    @Test
    fun theEdgeFadeReachesZeroWithoutAStep() {
        val result = correction(measured(Triple(50.0, 8.0, 0.4)))
        assertEquals("the fade must reach exactly zero at the floor", 0.0, result.first(), 1e-12)
        // A fade, not a cliff: no single 1/12-octave step may exceed half the
        // whole band's correction over the one-octave taper.
        for (i in 0 until 12) {
            assertTrue(
                "the fade jumped ${abs(result[i + 1] - result[i])} dB at ${freqs[i]} Hz",
                abs(result[i + 1] - result[i]) < 3.0
            )
        }
    }

    @Test
    fun theCorrectionIsRecentredSoItChangesShapeNotLoudness() {
        val result = correction(measured(Triple(100.0, 9.0, 0.4)))
        val inBand = freqs.indices.filter { freqs[it] in 100.0..10000.0 }
        val mean = inBand.map { result[it] }.average()
        assertEquals("mean correction over 100 Hz-10 kHz", 0.0, mean, 0.6)
    }

    // ---- the 1.7.1 gate leak ----
    //
    // Every refusal used to be applied to the raw curve and then undone by the
    // two steps after it: slope limiting and, decisively, the re-centring pass,
    // which adds one constant to *every* band. A room with a broad peak above
    // target makes the correction mean negative, so re-centring lifts the whole
    // curve — and a null that had been zeroed came back as +2.2 dB, a gated band
    // as +3.0 dB. The app then boosted a cancellation while the result card said
    // "not boosted". These two tests are the shape that reproduces it.

    /** A flat 8 dB peak across the band: every band wants a cut, so re-centring lifts. */
    private fun broadPeak(): DoubleArray = DoubleArray(freqs.size) { 8.0 }

    @Test
    fun aNullIsNotBoostedEvenWhenRecentringWouldLiftEveryBand() {
        for (nullHz in doubleArrayOf(250.0, 500.0, 1000.0)) {
            val room = broadPeak().also { it[freqs.indexOfFirst { f -> f >= nullHz }] = -20.0 }
            val nulls = Correction.detectNulls(freqs, room)
            assertTrue("$nullHz Hz must be detected as a null", nulls.any { it })
            val result = correction(room, nullMask = nulls)
            val i = freqs.indexOfFirst { f -> f >= nullHz }
            assertTrue(
                "the null at ${freqs[i]} Hz was boosted to ${result[i]} dB by re-centring",
                result[i] <= 1e-9
            )
        }
    }

    @Test
    fun aPhaseGatedBandIsNotCorrectedEvenWhenRecentringWouldLiftEveryBand() {
        val room = broadPeak()
        val gated = freqs.indexOfFirst { it >= 200.0 }
        val phaseOk = BooleanArray(freqs.size) { true }.also { it[gated] = false }
        val result = Correction.calculateCorrectionCurve(
            freqs, room, DoubleArray(freqs.size),
            transitionHz = 1e6, minPhaseOk = phaseOk
        )
        assertEquals(
            "a band that failed the gate must stay at exactly 0 dB",
            0.0, result[gated], 0.0
        )
    }

    @Test
    fun noRefusalIsEverContradictedByTheCurveItLeaves() {
        // The general form: build a room whose refusals and whose re-centring
        // pull in opposite directions, then check the finished curve agrees
        // with every refusal it was given.
        val room = broadPeak()
        room[freqs.indexOfFirst { it >= 315.0 }] = -22.0
        val nulls = Correction.detectNulls(freqs, room)
        val phaseOk = BooleanArray(freqs.size) { true }.also {
            it[freqs.indexOfFirst { f -> f >= 2000.0 }] = false
        }
        val result = Correction.calculateCorrectionCurve(
            freqs, room, DoubleArray(freqs.size),
            nullMask = nulls, minPhaseOk = phaseOk
        )
        for (i in freqs.indices) {
            if (nulls[i]) {
                assertTrue("null at ${freqs[i]} Hz carries ${result[i]} dB", result[i] <= 1e-9)
            }
            if (!phaseOk[i]) {
                assertEquals("gated band ${freqs[i]} Hz moved", 0.0, result[i], 0.0)
            }
        }
    }

    // ---- graded trust (1.7.1) ----

    @Test
    fun confidenceIsZeroAtTheGateAndFullTwentyDecibelsClear() {
        assertEquals("on the gate", 0.0, Correction.bandConfidence(doubleArrayOf(10.0))[0], 0.0)
        assertEquals("below the gate", 0.0, Correction.bandConfidence(doubleArrayOf(4.0))[0], 0.0)
        assertEquals("twenty dB clear", 1.0, Correction.bandConfidence(doubleArrayOf(20.0))[0], 0.0)
        assertEquals("well clear", 1.0, Correction.bandConfidence(doubleArrayOf(45.0))[0], 0.0)
    }

    @Test
    fun confidenceRampsBetweenInsteadOfJumping() {
        val ramp = Correction.bandConfidence(doubleArrayOf(11.0, 15.0, 19.0))
        assertTrue("11 dB should be barely trusted, was ${ramp[0]}", ramp[0] in 0.0..0.2)
        assertTrue("15 dB should be about half, was ${ramp[1]}", ramp[1] in 0.4..0.6)
        assertTrue("19 dB should be nearly all, was ${ramp[2]}", ramp[2] > 0.85)
        assertTrue("the ramp must increase", ramp[0] <= ramp[1] && ramp[1] <= ramp[2])
    }

    @Test
    fun anUnreadableBandIsNeverGivenAPositiveWeight() {
        val conf = Correction.bandConfidence(doubleArrayOf(Double.NaN, Double.NEGATIVE_INFINITY))
        assertEquals("NaN SNR", 0.0, conf[0], 0.0)
        assertEquals("-inf SNR", 0.0, conf[1], 0.0)
    }

    @Test
    fun halfTrustedBandsGetHalfTheCorrection() {
        val room = measured(Triple(500.0, 6.0, 0.7))
        val full = correction(room, transitionHz = 1e6)
        val half = Correction.calculateCorrectionCurve(
            freqs, room, DoubleArray(freqs.size),
            transitionHz = 1e6, confidence = DoubleArray(freqs.size) { 0.5 }
        )
        val i = freqs.indexOfFirst { it >= 500.0 }
        assertTrue("the fixture must ask for a cut at 500 Hz", full[i] < -1.0)
        assertEquals("half trust must halve the gain", full[i] / 2.0, half[i], 0.05)
    }

    // ---- the seat-count boost budget (1.7.1) ----

    @Test
    fun theBassBudgetRisesWithASecondSeatAndStopsThere() {
        assertEquals(SweepAnalysis.SINGLE_SEAT_BOOST_DB, Correction.boostBudgetDb(1), 0.0)
        assertEquals(DspConstants.MULTI_SEAT_BOOST_DB, Correction.boostBudgetDb(2), 0.0)
        assertEquals(DspConstants.MULTI_SEAT_BOOST_DB, Correction.boostBudgetDb(3), 0.0)
        assertTrue("the multi-seat budget must still be conservative", DspConstants.MULTI_SEAT_BOOST_DB < DspConstants.MAX_BOOST_DB)
        assertTrue("and above one seat's", DspConstants.MULTI_SEAT_BOOST_DB > SweepAnalysis.SINGLE_SEAT_BOOST_DB)
    }

    @Test
    fun aFlatRoomAgainstAFlatTargetAsksForAlmostNothing() {
        val result = correction(DoubleArray(freqs.size))
        assertTrue("a flat measurement must not invent a curve", result.all { abs(it) < 0.5 })
    }

    @Test
    fun theTransitionSplitsInversionBelowFromShapingAbove() {
        // A narrow treble peak may only be shaped, so its correction is gentler
        // than the same peak in the bass, which is inverted directly.
        val bass = correction(measured(Triple(120.0, 6.0, 0.2)))
        val treble = correction(measured(Triple(4000.0, 6.0, 0.2)))
        val iBass = freqs.indexOfFirst { it >= 120.0 }
        val iTreble = freqs.indexOfFirst { it >= 4000.0 }
        assertTrue(
            "bass ${bass[iBass]} dB vs treble ${treble[iTreble]} dB",
            abs(bass[iBass]) > abs(treble[iTreble])
        )
        assertTrue("treble shaping may not exceed the shaping budget",
            abs(treble[iTreble]) <= DspConstants.MAX_SHAPING_DB + 0.5)
    }

    @Test
    fun anEmptyGridProducesAnEmptyCurveRatherThanCrashing() {
        val result = Correction.calculateCorrectionCurve(
            DoubleArray(0), DoubleArray(0), DoubleArray(0)
        )
        assertEquals(0, result.size)
    }

    @Test
    fun aMismatchedGridIsRefused() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            Correction.calculateCorrectionCurve(
                DoubleArray(8) { 100.0 }, DoubleArray(4), DoubleArray(8)
            )
        }
    }

    @Test
    fun smoothingIsEnergyPreservingForAFlatInput() {
        val flat = DoubleArray(freqs.size)
        val smoothed = Correction.smoothVariable(freqs, flat, 300.0)
        for (value in smoothed) assertEquals(0.0, value, 1e-9)
    }

    @Test
    fun theNullDetectorNeedsDepthNotJustASmallDip() {
        val shallow = measured(Triple(80.0, -3.0, 0.15))
        assertTrue("a 3 dB dip is not a null", Correction.detectNulls(freqs, shallow).none { it })
        val deep = measured(Triple(80.0, -15.0, 0.15))
        assertTrue("a 15 dB cancellation is a null", Correction.detectNulls(freqs, deep).any { it })
    }

    @Test
    fun rolloffStopsWhereTheSpeakerStops() {
        // A speaker flat to 60 Hz, 6 dB down at 40 Hz and falling steeply: the
        // floor must land in the roll-off, and nothing under it may be lifted.
        val room = DoubleArray(freqs.size) { i ->
            if (freqs[i] >= 60.0) 0.0 else -12.0 * log2(60.0 / freqs[i])
        }
        val floor = Correction.detectLowRolloff(freqs, room)
        assertTrue("floor $floor Hz must stay inside the roll-off", floor in 40.0..63.0)
        val result = correction(room, fMin = floor)
        assertEquals("nothing at 31.5 Hz may be corrected", 0.0, result.first(), 1e-9)
    }

    @Test
    fun smoothingCannotSmoothAwayASingleBandDifferenceAtTheEdges() {
        // A step at the very first sample must not be extrapolated into a wild
        // value: the fine-regime smoothing window is clamped at the edges.
        val step = DoubleArray(freqs.size) { if (it == 0) 12.0 else 0.0 }
        val smoothed = Correction.smoothVariable(freqs, step, 300.0)
        for (value in smoothed) assertTrue("smoothed $value dB is outside the input range", abs(value) <= 12.0)
    }
}
