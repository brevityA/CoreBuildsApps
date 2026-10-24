package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

class SeatAverageTest {

    private val centres = DspConstants.ISO_CENTRES_HZ.filter { it in 31.5..DspConstants.F_MAX }.toDoubleArray()
    private fun idx(hz: Double) = centres.indices.minByOrNull { abs(centres[it] - hz) }!!

    /** A seat as SweepAnalysis would return it: flat apart from [bumps] (Hz to dB). */
    private fun seat(
        bumps: Map<Double, Double> = emptyMap(),
        minPhaseOk: BooleanArray = BooleanArray(centres.size) { true },
        fMaxHz: Double = DspConstants.F_MAX,
        rt60: Double? = 0.4
    ): SweepResult {
        val measured = DoubleArray(centres.size)
        for ((hz, db) in bumps) measured[idx(hz)] += db
        val target = Targets.targetCurve("flat", centres)
        val correction = Correction.calculateCorrectionCurve(
            centres, measured, target,
            transitionHz = Correction.transitionHz(null, rt60),
            minPhaseOk = minPhaseOk,
            maxBoostBelowTransition = SweepAnalysis.SINGLE_SEAT_BOOST_DB
        )
        return SweepResult(
            centresHz = centres, measuredDb = measured, targetDb = target, correctionDb = correction,
            nullMask = BooleanArray(centres.size), minPhaseOk = minPhaseOk, rt60Seconds = rt60,
            schroederHz = null, transitionHz = Correction.transitionHz(null, rt60), rolloffHz = DspConstants.F_MIN,
            floorHz = DspConstants.F_MIN, snrDb = 40.0, latencyMs = 0.0, filters = emptyList(), preampDb = 0.0,
            bandSnrDb = DoubleArray(centres.size) { 40.0 }, fMaxHz = fMaxHz
        )
    }

    @Test
    fun `energy mean matches the Python reference`() {
        // tools/core_eq_dsp.py average_measurements([10, 0], [0, 10]) - tests/test_core_eq_dsp.py.
        val avg = SeatAverage.energyMeanDb(listOf(doubleArrayOf(10.0, 0.0), doubleArrayOf(0.0, 10.0)))
        val expected = 10.0 * log10((10.0 + 1.0) / 2.0)
        assertEquals(expected, avg[0], 1e-12)
        assertEquals(expected, avg[1], 1e-12)
    }

    @Test
    fun `energy mean is not a decibel mean`() {
        // +12 dB and -12 dB average to +9 dB of energy, not 0 dB: a peak at one seat is not
        // cancelled by a dip at another.
        val avg = SeatAverage.energyMeanDb(listOf(doubleArrayOf(12.0), doubleArrayOf(-12.0)))
        assertTrue(avg[0] > 8.9 && avg[0] < 9.1)
    }

    @Test
    fun `one seat is returned unchanged`() {
        val only = seat(mapOf(63.0 to 6.0))
        assertSame(only, SeatAverage.combine(listOf(only), "flat", null))
    }

    // Room modes span neighbouring third-octave bands; a one-band spike is mostly smoothed
    // away by the corrector before it is ever cut, so the shapes here are three bands wide,
    // and sit above the taper at the 40 Hz floor and below the 300 Hz transition.
    private val roomMode = mapOf(80.0 to 4.0, 100.0 to 6.0, 125.0 to 4.0)
    private val chairPeak = mapOf(160.0 to 5.0, 200.0 to 8.0, 250.0 to 5.0)

    @Test
    fun `a peak at one seat is cut less, a peak at every seat is cut in full`() {
        val a = seat(roomMode + chairPeak)
        val b = seat(roomMode)
        val c = seat(roomMode)
        val combined = SeatAverage.combine(listOf(a, b, c), "flat", null)
        val at100 = idx(100.0)
        val at200 = idx(200.0)
        // The room's mode is cut as deeply as one seat would cut it.
        assertTrue("room mode should still be cut: ${combined.correctionDb[at100]}", combined.correctionDb[at100] < -3.0)
        assertEquals(a.correctionDb[at100], combined.correctionDb[at100], 1.0)
        // The one-chair peak is cut much less than seat A alone would cut it.
        assertTrue(
            "one-seat peak: ${combined.correctionDb[at200]} vs seat A ${a.correctionDb[at200]}",
            abs(combined.correctionDb[at200]) < abs(a.correctionDb[at200]) - 1.5
        )
    }

    @Test
    fun `a band one seat could not trust stays uncorrected`() {
        val bad = BooleanArray(centres.size) { true }.also { it[idx(100.0)] = false }
        val a = seat(roomMode)
        val b = seat(roomMode, minPhaseOk = bad)
        val combined = SeatAverage.combine(listOf(a, b), "flat", null)
        assertTrue(a.correctionDb[idx(100.0)] < -3.0)
        // Gated to 0; the slope limiter's ramp from the neighbours leaves a fraction of a dB.
        assertTrue("gated band: ${combined.correctionDb[idx(100.0)]}", abs(combined.correctionDb[idx(100.0)]) < 0.5)
    }

    /** A seat as SweepAnalysis returns it for a known room: raw [gate], waived above its own transition. */
    private fun seatInRoom(volumeM3: Double, rt60: Double, gate: BooleanArray): SweepResult {
        val transition = Correction.transitionHz(volumeM3, rt60)
        return seat(minPhaseOk = BooleanArray(centres.size) { gate[it] || centres[it] >= transition }, rt60 = rt60)
            .copy(transitionHz = transition, phaseGate = gate)
    }

    @Test
    fun `a seat's failed gate stays failed below the combined transition`() {
        // 60 m3: RT60 0.2 s puts seat A's transition at 231 Hz, 0.6 s puts B's at 400 Hz, and
        // their mean, 0.4 s, puts the combined one at 327 Hz. A's own result waives its failed
        // 250 Hz test (above 231 Hz); the combined result must not, since 250 < 327.
        val failsAt250 = BooleanArray(centres.size) { true }.also { it[idx(250.0)] = false }
        val a = seatInRoom(60.0, 0.2, failsAt250)
        val b = seatInRoom(60.0, 0.6, BooleanArray(centres.size) { true })
        assertTrue("seat A waives its own 250 Hz band", a.minPhaseOk[idx(250.0)])
        val combined = SeatAverage.combine(listOf(a, b), "flat", 60.0)
        assertTrue("combined transition ${combined.transitionHz}", combined.transitionHz in 300.0..350.0)
        assertTrue("250 Hz must stay gated", !combined.minPhaseOk[idx(250.0)])
        // Above the combined transition the diffuse-field waiver still applies.
        assertTrue(combined.minPhaseOk[idx(400.0)])
    }

    @Test
    fun `bass is never lifted past the single-seat limit`() {
        val dip = mapOf(50.0 to -9.0, 63.0 to -9.0)
        val combined = SeatAverage.combine(listOf(seat(dip), seat(dip), seat(dip)), "flat", null)
        for (k in centres.indices) {
            if (centres[k] < combined.transitionHz) {
                assertTrue("boost at ${centres[k]} Hz", combined.correctionDb[k] <= SweepAnalysis.SINGLE_SEAT_BOOST_DB + 1e-9)
            }
        }
    }

    @Test
    fun `stops at the narrowest seat's bandwidth edge`() {
        val combined = SeatAverage.combine(listOf(seat(fMaxHz = 8000.0), seat(fMaxHz = 4000.0)), "flat", null)
        assertEquals(4000.0, combined.fMaxHz, 1e-9)
    }

    @Test
    fun `leaves out the one seat that does not match the others`() {
        val mid = mapOf(500.0 to 7.0, 800.0 to -7.0, 1250.0 to 7.0, 2000.0 to -7.0)
        val curves = listOf(seat().measuredDb, seat().measuredDb, seat(mid).measuredDb)
        assertEquals(2, SeatAverage.outlier(centres, curves))
    }

    @Test
    fun `the seat left out does not shape the correction`() {
        val mid = mapOf(500.0 to 7.0, 800.0 to -7.0, 1250.0 to 7.0, 2000.0 to -7.0)
        val turnedAway = seat(roomMode + mid)
        val averaged = SeatAverage.average(listOf(seat(roomMode), seat(roomMode), turnedAway), "flat", null)
        assertEquals(2, averaged.leftOut)
        assertTrue("deviation ${averaged.leftOutDb}", averaged.leftOutDb!! > SeatAverage.OUTLIER_RMS_DB)
        // Two identical kept seats average to themselves: nothing of the third is in it.
        val keptOnly = SeatAverage.combine(listOf(seat(roomMode), seat(roomMode)), "flat", null)
        for (k in centres.indices) assertEquals(keptOnly.correctionDb[k], averaged.result.correctionDb[k], 1e-9)
    }

    @Test
    fun `average keeps every seat when none stands out`() {
        val averaged = SeatAverage.average(listOf(seat(roomMode), seat(roomMode + chairPeak), seat(roomMode)), "flat", null)
        assertNull(averaged.leftOut)
        assertNull(averaged.leftOutDb)
    }

    @Test
    fun `keeps every seat when they agree, and cannot judge two`() {
        val small = mapOf(500.0 to 1.0, 1250.0 to -1.0)
        assertNull(SeatAverage.outlier(centres, listOf(seat().measuredDb, seat(small).measuredDb, seat().measuredDb)))
        val mid = mapOf(500.0 to 7.0, 800.0 to -7.0, 1250.0 to 7.0, 2000.0 to -7.0)
        assertNull(SeatAverage.outlier(centres, listOf(seat().measuredDb, seat(mid).measuredDb)))
    }
}
