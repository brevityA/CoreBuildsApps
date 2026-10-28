package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2

/**
 * The closed loop: a check sweep played through the correction moves the correction.
 *
 * Every case builds a real correction from a synthetic room with [Correction.calculateCorrectionCurve]
 * rather than a hand-written curve, so what is being refined is the thing the app ships. The check
 * sweep is then synthesised as "the residual the correction predicted, plus an error" — which is
 * exactly the quantity [CorrectionRefinement] is meant to read back.
 */
class CorrectionRefinementTest {

    private val centres = DspConstants.ISO_CENTRES_HZ.filter { it in 40.0..DspConstants.F_MAX }.toDoubleArray()
    private val n = centres.size

    private fun idx(hz: Double) = centres.indices.minByOrNull { abs(centres[it] - hz) }!!

    /** A room: a broad bass lift, a mid dip, and a genuine cancellation at 84 Hz. */
    private val room: DoubleArray = DoubleArray(n) { k ->
        val f = centres[k]
        5.0 * exp(-0.5 * (log2(f / 90.0) / 0.6).pow2()) -
            2.5 * exp(-0.5 * (log2(f / 1200.0) / 0.5).pow2()) -
            9.0 * exp(-0.5 * (log2(f / 80.0) / 0.15).pow2())
    }

    private fun Double.pow2() = this * this

    private val target = DoubleArray(n)
    private val nulls = Correction.detectNulls(centres, room)
    private val snr = DoubleArray(n) { 40.0 }
    private val confidence = Correction.bandConfidence(snr)
    private val correction = Correction.calculateCorrectionCurve(
        centres, room, target,
        transitionHz = 300.0,
        nullMask = nulls,
        confidence = confidence,
        maxBoostBelowTransition = SweepAnalysis.SINGLE_SEAT_BOOST_DB
    )

    /** The residual the correction predicted: what the room should sound like with it on. */
    private val predicted = DoubleArray(n) { room[it] + correction[it] - target[it] }

    private fun sweep(
        measured: DoubleArray,
        correctionDb: DoubleArray = DoubleArray(n),
        nullMask: BooleanArray = BooleanArray(n),
        minPhaseOk: BooleanArray = BooleanArray(n) { true },
        bandSnr: DoubleArray = snr,
        confidenceDb: DoubleArray = Correction.bandConfidence(bandSnr)
    ) = SweepResult(
        centresHz = centres,
        measuredDb = measured,
        targetDb = target,
        correctionDb = correctionDb,
        nullMask = nullMask,
        minPhaseOk = minPhaseOk,
        rt60Seconds = 0.4,
        schroederHz = null,
        transitionHz = 300.0,
        rolloffHz = DspConstants.F_MIN,
        floorHz = DspConstants.F_MIN,
        snrDb = 40.0,
        latencyMs = 0.0,
        filters = emptyList(),
        preampDb = 0.0,
        bandSnrDb = bandSnr,
        fMaxHz = DspConstants.F_MAX,
        confidence = confidenceDb
    )

    /** The room with [correction] applied, plus [error], as the check sweep would have heard it. */
    private fun check(correctionDb: DoubleArray = correction, error: DoubleArray = DoubleArray(n), snrDb: DoubleArray = snr) =
        sweep(
            measured = DoubleArray(n) { target[it] + room[it] + correctionDb[it] - target[it] + error[it] },
            bandSnr = snrDb
        )

    private val base by lazy { sweep(room, correction, nulls) }

    @Test
    fun `a check that hears exactly the prediction changes nothing`() {
        // A pure level difference between the two sweeps: same shape, 2.5 dB louder, which is what
        // a check taken at another volume looks like. Alignment and re-centring must absorb it.
        val refined = CorrectionRefinement.refine(base, check(error = DoubleArray(n) { 2.5 }))
        assertNull("a level difference is not a correction error", refined)
    }

    @Test
    fun `a check that hears the room louder than predicted cuts more`() {
        // 2.5 dB more energy than predicted around 100 Hz, where the correction is already a cut.
        val error = DoubleArray(n) { k -> 2.5 * exp(-0.5 * (log2(centres[k] / 100.0) / 0.7).pow2()) }
        val refined = CorrectionRefinement.refine(base, check(error = error))
        assertNotNull(refined)
        val k = idx(100.0)
        val step = refined!!.stepDb[k]
        println("100 Hz: base ${correction[k]} step $step refined ${refined.result.correctionDb[k]}")
        assertTrue("expected a deeper cut at 100 Hz, got $step", step < -0.2)
        assertTrue("a cut must stay a cut", refined.result.correctionDb[k] < 0.0)
        assertTrue("one step only", abs(step) <= DspConstants.REFINE_MAX_STEP_DB + 1e-9)
        assertTrue("the predicted residual should fall", refined.after.rmsDb!! < refined.before.rmsDb!!)
    }

    @Test
    fun `a level error the check could not absorb is not turned into gain`() {
        // A band the correction may not touch was hit 8 dB harder in the check than predicted, so
        // the alignment constant over 300 Hz-3 kHz is wrong for every band that *is* trusted. That
        // constant is the check being taken at another level, not the correction being wrong: it
        // must be removed before anything is applied, or every trusted band gets a free 0.7 dB.
        val gated = BooleanArray(n) { true }.also { it[idx(1000.0)] = false }
        val gatedBase = sweep(room, correction, nulls, minPhaseOk = gated)
        val heard = DoubleArray(n) { k ->
            target[k] + predicted[k] + if (k == idx(1000.0)) -8.0 else 0.0
        }
        val refined = CorrectionRefinement.refine(gatedBase, sweep(measured = heard))
        assertNull("a level error is not a correction error", refined)
    }

    @Test
    fun `a band is never moved more than one step`() {
        // A check that disagrees by 9 dB — far more than one capture should ever be believed.
        val error = DoubleArray(n) { k -> 9.0 * exp(-0.5 * (log2(centres[k] / 100.0) / 0.5).pow2()) }
        val refined = CorrectionRefinement.refine(base, check(error = error))
        assertNotNull(refined)
        println("largest step ${refined!!.largestDb}")
        assertTrue(refined.largestDb <= DspConstants.REFINE_MAX_STEP_DB + 1e-9)
    }

    @Test
    fun `a band the correction did not touch is not refined`() {
        // 40 Hz sits on the taper and 6.3 kHz is above the correction's shaping: wherever the base
        // curve is exactly 0 dB the check has nothing to refine, and the step must stay 0.
        val error = DoubleArray(n) { 3.0 }
        val refined = CorrectionRefinement.refine(base, check(error = error))
        val zeroBands = centres.indices.filter { correction[it] == 0.0 }
        assertTrue("fixture needs untouched bands", zeroBands.isNotEmpty())
        if (refined != null) {
            for (k in zeroBands) assertEquals("band ${centres[k]} Hz", 0.0, refined.stepDb[k], 1e-12)
        }
    }

    @Test
    fun `a band the check could not hear is left where it was`() {
        val quiet = snr.copyOf().also { it[idx(125.0)] = 4.0 }
        val error = DoubleArray(n) { k -> -3.0 * exp(-0.5 * (log2(centres[k] / 125.0) / 0.5).pow2()) }
        val refined = CorrectionRefinement.refine(base, check(error = error, snrDb = quiet))
        if (refined != null) {
            assertEquals(0.0, refined.stepDb[idx(125.0)], 1e-12)
        }
        val below = snr.copyOf().also { it[idx(125.0)] = 15.0 }
        val partial = CorrectionRefinement.refine(base, check(error = error, snrDb = below))
        // 15 dB over the noise is half-trusted: the move is scaled, not switched on.
        val full = CorrectionRefinement.refine(base, check(error = error))
        if (partial != null && full != null) {
            println("125 Hz half-trusted ${partial.stepDb[idx(125.0)]} vs fully ${full.stepDb[idx(125.0)]}")
            assertTrue(
                "half trust must ask for less",
                abs(partial.stepDb[idx(125.0)]) < abs(full.stepDb[idx(125.0)])
            )
        }
    }

    @Test
    fun `a lift needs a deficit the check itself heard`() {
        // A check 4 dB hotter than the prediction everywhere, with one local dip: the global part is
        // a level difference and must not become gain, and the local dip may only be filled where
        // the aligned check residual is genuinely under the target.
        val error = DoubleArray(n) { k -> -2.0 * exp(-0.5 * (log2(centres[k] / 1250.0) / 0.6).pow2()) }
        val heard = DoubleArray(n) { k -> target[k] + predicted[k] + error[k] + 4.0 }
        val refined = CorrectionRefinement.refine(base, sweep(measured = heard))
        assertNotNull(refined)
        val aligned = PostCorrectionCheck.align(centres, heard, target)!!
        var lifts = 0
        for (k in centres.indices) {
            if (refined!!.stepDb[k] > 0.0) {
                lifts++
                val heardResidual = aligned[k] - target[k]
                println("lifted ${centres[k]} Hz by ${refined.stepDb[k]} with the check at $heardResidual dB")
                assertTrue(
                    "lifted ${centres[k]} Hz with the check at $heardResidual dB",
                    heardResidual < -DspConstants.REFINE_MIN_DEFICIT_DB
                )
            }
        }
        println("lifts: $lifts")
    }

    @Test
    fun `a cancellation the check found is not filled`() {
        // The check sees a deep narrow hole the first sweep did not: a moved microphone, not a
        // correction error. It must not be lifted into.
        val error = DoubleArray(n) { k -> -8.0 * exp(-0.5 * (log2(centres[k] / 1250.0) / 0.06).pow2()) }
        val heard = DoubleArray(n) { target[it] + predicted[it] + error[it] }
        val checkNulls = Correction.detectNulls(centres, heard)
        assertTrue("fixture needs the check to see a null", checkNulls.any { it })
        val refined = CorrectionRefinement.refine(
            base,
            sweep(measured = heard, nullMask = checkNulls)
        )
        if (refined != null) {
            for (k in centres.indices) {
                if (checkNulls[k]) assertTrue("lifted into a null at ${centres[k]} Hz", refined.stepDb[k] <= 0.0)
            }
        }
    }

    @Test
    fun `a cut is never turned into a boost, nor a boost into a cut`() {
        // One more capture cannot reverse what the first one measured. A band the correction cut
        // may relax toward 0 dB if the check says the cut overshot, but never past it.
        val cutBand = centres.indices.first { correction[it] < -0.1 && correction[it] > -1.0 }
        val quiet = DoubleArray(n) { k -> -6.0 * exp(-0.5 * (log2(centres[k] / centres[cutBand]) / 0.5).pow2()) }
        val fromCut = CorrectionRefinement.refine(base, check(error = quiet))
        assertNotNull(fromCut)
        val cutAfter = fromCut!!.result.correctionDb[cutBand]
        println("cut band ${centres[cutBand]}: ${correction[cutBand]} -> $cutAfter (step ${fromCut.stepDb[cutBand]})")
        assertTrue("a cut became a boost: $cutAfter", cutAfter <= 0.0)
        assertTrue("the cut should relax toward 0 dB", cutAfter >= correction[cutBand])

        val boostBand = centres.indices.first { correction[it] > 0.1 && correction[it] < 1.0 }
        val loud = DoubleArray(n) { k -> 6.0 * exp(-0.5 * (log2(centres[k] / centres[boostBand]) / 0.5).pow2()) }
        val fromBoost = CorrectionRefinement.refine(base, check(error = loud))
        assertNotNull(fromBoost)
        val boostAfter = fromBoost!!.result.correctionDb[boostBand]
        println("boost band ${centres[boostBand]}: ${correction[boostBand]} -> $boostAfter (step ${fromBoost.stepDb[boostBand]})")
        assertTrue("a boost became a cut: $boostAfter", boostAfter >= 0.0)
        assertTrue("the boost should relax toward 0 dB", boostAfter <= correction[boostBand])
    }

    @Test
    fun `the refined curve stays inside the budget and keeps every refusal`() {
        val error = DoubleArray(n) { k -> -3.5 * exp(-0.5 * (log2(centres[k] / 250.0) / 0.5).pow2()) }
        val refined = CorrectionRefinement.refine(base, check(error = error))
        assertNotNull(refined)
        val curve = refined!!.result.correctionDb
        val envelope = Correction.budgetEnvelope(centres, 300.0, maxBoostBelowTransition = SweepAnalysis.SINGLE_SEAT_BOOST_DB)
        for (k in centres.indices) {
            assertTrue("band ${centres[k]} Hz at ${curve[k]}", curve[k] <= envelope.ceilingDb[k] + 1e-9)
            assertTrue("band ${centres[k]} Hz at ${curve[k]}", curve[k] >= envelope.floorDb[k] - 1e-9)
        }
        // The 84 Hz cancellation is still not boosted, and the audit that caught the 1.7.0 defect
        // (re-centring lifting a refused band) has nothing to report about the refined curve.
        val nullBand = nulls.indexOfFirst { it }
        assertTrue("fixture needs a null", nullBand >= 0)
        println("null band ${centres[nullBand]} Hz, base ${correction[nullBand]} curve ${curve[nullBand]}")
        // A null may be cut but never lifted, and the refinement may not move it at all.
        assertTrue("null lifted to ${curve[nullBand]}", curve[nullBand] <= 0.0)
        assertEquals(0.0, refined.stepDb[nullBand], 1e-12)
        assertTrue(CorrectionReport.gateViolations(refined.result).isEmpty())
    }

    @Test
    fun `the refined result keeps the measurement and refits the filters`() {
        val error = DoubleArray(n) { k -> 2.0 * exp(-0.5 * (log2(centres[k] / 150.0) / 0.6).pow2()) }
        val refined = CorrectionRefinement.refine(base, check(error = error))
        assertNotNull(refined)
        val r = refined!!.result
        assertSame("the room is not measured again", room, r.measuredDb)
        assertSame(target, r.targetDb)
        assertSame(nulls, r.nullMask)
        // The fit drops filters it cannot use, so it is at most the budget and never empty.
        assertTrue("filters ${r.filters.size}", r.filters.isNotEmpty())
        assertTrue("filters ${r.filters.size}", r.filters.size <= DspConstants.PEAKING_FILTERS)
        assertTrue(r.preampDb.isFinite())
        assertTrue("a boost curve needs a negative preamp", r.preampDb <= 0.0)
        assertTrue(r.confidence.contentEquals(confidence))
    }

    @Test
    fun `a check analysed past the correction's own band still refines the shared bands`() {
        // A two-seat average stops at the narrowest seat's bandwidth edge; the check sweep that
        // follows is analysed at the full band. The shared bands are still usable.
        val m = n - 4
        val narrow = base.copy(
            centresHz = centres.copyOf(m), measuredDb = room.copyOf(m), targetDb = target.copyOf(m),
            correctionDb = correction.copyOf(m), nullMask = nulls.copyOf(m),
            minPhaseOk = BooleanArray(m) { true }, bandSnrDb = snr.copyOf(m),
            confidence = confidence.copyOf(m), fMaxHz = centres[m - 1]
        )
        val error = DoubleArray(n) { k -> 2.0 * exp(-0.5 * (log2(centres[k] / 100.0) / 0.7).pow2()) }
        val refined = CorrectionRefinement.refine(narrow, check(error = error))
        assertNotNull(refined)
        // Only the bands the correction has are described: the check's extra top bands are not
        // invented into a curve that stopped below them.
        assertEquals(m, refined!!.result.correctionDb.size)
        assertEquals(m, refined.stepDb.size)
        assertTrue("the shared bands must still be refined", refined.changed > 0)
    }

    @Test
    fun `a check on other bands or too few of them is refused`() {
        val shifted = SweepResult(
            centresHz = DoubleArray(n) { centres[it] * 1.01 },
            measuredDb = room, targetDb = target, correctionDb = correction,
            nullMask = nulls, minPhaseOk = BooleanArray(n) { true }, rt60Seconds = 0.4,
            schroederHz = null, transitionHz = 300.0, rolloffHz = DspConstants.F_MIN,
            floorHz = DspConstants.F_MIN, snrDb = 40.0, latencyMs = 0.0, filters = emptyList(),
            preampDb = 0.0, bandSnrDb = snr
        )
        assertNull(CorrectionRefinement.refine(base, shifted))

        val few = SweepResult(
            centresHz = doubleArrayOf(100.0, 200.0), measuredDb = doubleArrayOf(0.0, 0.0),
            targetDb = doubleArrayOf(0.0, 0.0), correctionDb = doubleArrayOf(-1.0, -1.0),
            nullMask = BooleanArray(2), minPhaseOk = BooleanArray(2) { true }, rt60Seconds = 0.4,
            schroederHz = null, transitionHz = 300.0, rolloffHz = 40.0, floorHz = 40.0,
            snrDb = 40.0, latencyMs = 0.0, filters = emptyList(), preampDb = 0.0,
            bandSnrDb = doubleArrayOf(40.0, 40.0)
        )
        assertNull(CorrectionRefinement.refine(base, few))
    }

    @Test
    fun `an agreeing check leaves the curve byte for byte`() {
        // Nothing moved at all is the common case, and it must not disturb the curve it agreed with.
        val refined = CorrectionRefinement.refine(base, check())
        assertNull(refined)
    }
}
