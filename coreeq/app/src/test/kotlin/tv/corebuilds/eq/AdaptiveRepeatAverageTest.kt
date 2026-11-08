package tv.corebuilds.eq

import tv.corebuilds.eq.dsp.AdaptiveRepeatAverage
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.dsp.SweepResult
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdaptiveRepeatAverageTest {

    private val centres = doubleArrayOf(500.0, 1000.0, 2000.0, 4000.0)

    private fun result(
        measuredDb: DoubleArray,
        snrDb: DoubleArray,
        confidence: DoubleArray = DoubleArray(measuredDb.size) { 1.0 }
    ): SweepResult = SweepResult(
        centresHz = centres,
        measuredDb = measuredDb,
        targetDb = DoubleArray(measuredDb.size),
        correctionDb = DoubleArray(measuredDb.size),
        nullMask = BooleanArray(measuredDb.size),
        minPhaseOk = BooleanArray(measuredDb.size) { true },
        rt60Seconds = 0.3,
        schroederHz = null,
        transitionHz = 300.0,
        rolloffHz = 40.0,
        floorHz = 40.0,
        snrDb = snrDb.min(),
        latencyMs = 0.0,
        filters = emptyList<PeakingFilter>(),
        preampDb = 0.0,
        bandSnrDb = snrDb,
        rt60ByOctave = emptyMap(),
        driftPpm = 0.0,
        fMaxHz = 8000.0,
        phaseGate = BooleanArray(measuredDb.size) { true },
        confidence = confidence
    )

    @Test
    fun `identical clean sweeps preserve the reference curve`() {
        val measured = doubleArrayOf(-3.0, -1.0, 1.0, 3.0)
        val snr = doubleArrayOf(30.0, 30.0, 30.0, 30.0)
        val averaged = AdaptiveRepeatAverage.average(
            reference = result(measured, snr),
            repeat = result(measured, snr),
            target = "flat",
            volumeM3 = null
        )

        assertEquals(0.0, averaged.levelOffsetDb, 1e-9)
        assertEquals(0.0, averaged.agreementRmsDb, 1e-9)
        assertEquals(4, averaged.usableBands)
        for (k in measured.indices) {
            assertEquals(measured[k], averaged.result.measuredDb[k], 1e-9)
        }
    }

    @Test
    fun `a constant repeat offset is removed by median alignment`() {
        val reference = result(
            doubleArrayOf(-3.0, -1.0, 1.0, 3.0),
            doubleArrayOf(30.0, 30.0, 30.0, 30.0)
        )
        val repeat = result(
            doubleArrayOf(-1.0, 1.0, 3.0, 5.0),
            doubleArrayOf(30.0, 30.0, 30.0, 30.0)
        )
        val averaged = AdaptiveRepeatAverage.average(
            reference = reference,
            repeat = repeat,
            target = "flat",
            volumeM3 = null
        )

        assertEquals(2.0, averaged.levelOffsetDb, 1e-9)
        for (k in reference.measuredDb.indices) {
            assertEquals(reference.measuredDb[k], averaged.result.measuredDb[k], 1e-9)
        }
    }

    @Test
    fun `a noisy repeat receives less influence than a clean reference`() {
        val reference = result(
            doubleArrayOf(0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(40.0, 40.0, 40.0, 40.0),
            doubleArrayOf(1.0, 1.0, 1.0, 1.0)
        )
        val repeat = result(
            doubleArrayOf(-6.0, -6.0, -6.0, -6.0),
            doubleArrayOf(12.0, 12.0, 12.0, 12.0),
            doubleArrayOf(0.2, 0.2, 0.2, 0.2)
        )
        val averaged = AdaptiveRepeatAverage.average(
            reference = reference,
            repeat = repeat,
            target = "flat",
            volumeM3 = null
        )

        assertNotNull(averaged.result)
        assertTrue(averaged.result.measuredDb[0] > -6.0)
        assertTrue(averaged.result.measuredDb[0] < 0.0)
    }

    @Test
    fun `material disagreement lowers confidence and adds a note`() {
        val reference = result(
            doubleArrayOf(0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(40.0, 40.0, 40.0, 40.0)
        )
        val repeat = result(
            doubleArrayOf(-6.0, -6.0, -6.0, -6.0),
            doubleArrayOf(40.0, 40.0, 40.0, 40.0)
        )
        val averaged = AdaptiveRepeatAverage.average(
            reference = reference,
            repeat = repeat,
            target = "flat",
            volumeM3 = null
        )

        assertEquals(6.0, averaged.agreementRmsDb, 1e-9)
        assertEquals(AdaptiveRepeatAverage.MIN_AGREEMENT_FACTOR, averaged.result.confidence[0], 1e-9)
        assertEquals("The repeat differs from the first sweep; its influence is reduced.", averaged.note)
    }

    @Test
    fun `mismatched frequency grids are rejected`() {
        val reference = result(
            doubleArrayOf(0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(40.0, 40.0, 40.0, 40.0)
        )
        val repeat = result(
            doubleArrayOf(0.0, 0.0, 0.0, 0.0),
            doubleArrayOf(40.0, 40.0, 40.0, 40.0)
        ).copy(centresHz = doubleArrayOf(500.0, 1000.0, 2000.0, 5000.0))

        val error = assertFailsWith<IllegalArgumentException> {
            AdaptiveRepeatAverage.average(
                reference = reference,
                repeat = repeat,
                target = "flat",
                volumeM3 = null
            )
        }
        assertTrue(error.message!!.contains("different frequency grids"))
    }
}
