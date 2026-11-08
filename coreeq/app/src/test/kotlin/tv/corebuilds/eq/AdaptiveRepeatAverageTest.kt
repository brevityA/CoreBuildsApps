package tv.corebuilds.eq

import org.junit.Assert.*
import org.junit.Test
import tv.corebuilds.eq.dsp.AdaptiveRepeatAverage
import tv.corebuilds.eq.dsp.SweepResult
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

class AdaptiveRepeatAverageTest {
    private fun result(
        db: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0),
        snr: Double = 40.0,
        confidence: Double = 1.0
    ): SweepResult = SweepResult(
        centresHz = doubleArrayOf(500.0, 1000.0, 2000.0, 4000.0),
        measuredDb = db, targetDb = DoubleArray(4), correctionDb = DoubleArray(4),
        nullMask = BooleanArray(4), minPhaseOk = BooleanArray(4) { true },
        rt60Seconds = 0.3, schroederHz = null, transitionHz = 300.0,
        rolloffHz = 40.0, floorHz = 40.0, snrDb = snr, latencyMs = 0.0,
        filters = emptyList(), preampDb = 0.0, bandSnrDb = DoubleArray(4) { snr },
        phaseGate = BooleanArray(4) { true }, confidence = DoubleArray(4) { confidence },
        fMaxHz = 8000.0
    )

    private fun average(first: SweepResult, next: SweepResult) =
        AdaptiveRepeatAverage.average(first, next, "flat", null)

    @Test fun identicalSweepsPreserveCurve() {
        val first = result(doubleArrayOf(-3.0, -1.0, 1.0, 3.0))
        val actual = average(first, first)
        assertArrayEquals(first.measuredDb, actual.result.measuredDb, 1e-9)
        assertEquals(0.0, actual.agreementRmsDb, 1e-9)
        assertEquals(4, actual.usableBands)
    }

    @Test fun louderRepeatNeedsNegativeOffset() {
        val first = result(doubleArrayOf(-3.0, -1.0, 1.0, 3.0))
        val next = result(doubleArrayOf(-1.0, 1.0, 3.0, 5.0))
        val actual = average(first, next)
        assertEquals(-2.0, actual.levelOffsetDb, 1e-9)
        assertArrayEquals(first.measuredDb, actual.result.measuredDb, 1e-9)
    }

    @Test fun lowerConfidenceRepeatHasLessInfluence() {
        val first = result()
        val next = result(doubleArrayOf(-6.0, -4.0, -2.0, 0.0), confidence = 0.2)
        val actual = average(first, next)
        val expected = 10.0 * log10((1.0 + 0.04 * 10.0.pow(-2.0 / 10.0)) / 1.04)
        assertEquals(4.0, actual.levelOffsetDb, 1e-9)
        assertEquals(expected, actual.result.measuredDb[0], 1e-9)
    }

    @Test fun shapeDisagreementReducesConfidence() {
        val actual = average(result(), result(doubleArrayOf(-10.0, -6.0, -2.0, 2.0)))
        assertEquals(6.0, actual.levelOffsetDb, 1e-9)
        assertEquals(sqrt(24.0), actual.agreementRmsDb, 1e-9)
        assertEquals(5.0 / 6.0, actual.result.confidence[0], 1e-9)
        assertEquals("The repeat differs from the first sweep; correction confidence is reduced.", actual.note)
    }

    @Test fun mismatchedGridsAreRejected() {
        val next = result().copy(centresHz = doubleArrayOf(500.0, 1000.0, 2000.0, 5000.0))
        assertThrows(IllegalArgumentException::class.java) { average(result(), next) }
    }

    @Test fun unequalBandwidthUsesOnlySharedBands() {
        val first = result()
        val next = result().copy(
            centresHz = doubleArrayOf(500.0, 1000.0, 2000.0),
            measuredDb = DoubleArray(3), targetDb = DoubleArray(3), correctionDb = DoubleArray(3),
            nullMask = BooleanArray(3), minPhaseOk = BooleanArray(3) { true },
            bandSnrDb = DoubleArray(3) { 40.0 }, phaseGate = BooleanArray(3) { true },
            confidence = DoubleArray(3) { 1.0 }, fMaxHz = 2200.0
        )
        val actual = average(first, next)
        assertEquals(3, actual.usableBands)
        assertEquals(3, actual.result.centresHz.size)
        assertEquals(2200.0, actual.result.fMaxHz, 1e-9)
    }

    @Test fun missingRawPhaseEvidenceDoesNotInventAPass() {
        val first = result().copy(phaseGate = BooleanArray(0), transitionHz = 300.0)
        val actual = average(first, result())
        assertFalse(actual.result.phaseGate[0])
    }

    @Test fun nonFiniteInputIsRejected() {
        val first = result(doubleArrayOf(Double.NaN, 0.0, 0.0, 0.0))
        assertThrows(IllegalArgumentException::class.java) { average(first, result()) }
    }
}
