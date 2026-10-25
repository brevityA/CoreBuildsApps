package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.DspConstants

class CorrectionLimitsTest {

    @Test
    fun bandEdgesAreInclusive() {
        assertTrue(CorrectionLimits.isCorrectable(40.0))
        assertTrue(CorrectionLimits.isCorrectable(8000.0))
        assertFalse(CorrectionLimits.isCorrectable(39.9))
        assertFalse(CorrectionLimits.isCorrectable(8000.1))
    }

    @Test
    fun outsideTheBandTheGainIsExactlyZero() {
        assertEquals(0.0, CorrectionLimits.limit(20.0, 5.0), 0.0)
        assertEquals(0.0, CorrectionLimits.limit(12000.0, -9.0), 0.0)
    }

    @Test
    fun boostsAndCutsAreClamped() {
        assertEquals(6.0, CorrectionLimits.limit(100.0, 15.0), 0.0)
        assertEquals(-12.0, CorrectionLimits.limit(100.0, -30.0), 0.0)
        assertEquals(-3.5, CorrectionLimits.limit(1000.0, -3.5), 0.0)
    }

    @Test
    fun notANumberNeverBecomesAGain() {
        assertEquals(0.0, CorrectionLimits.limit(1000.0, Double.NaN), 0.0)
    }

    @Test
    fun aChosenCeilingCanOnlyNarrowTheBand() {
        assertEquals(500.0, CorrectionLimits.ceilingHz(500.0), 0.0)
        assertEquals(8000.0, CorrectionLimits.ceilingHz(20000.0), 0.0)
        assertEquals(8000.0, CorrectionLimits.ceilingHz(null), 0.0)
        assertEquals(8000.0, CorrectionLimits.ceilingHz(Double.NaN), 0.0)
        assertEquals(40.0, CorrectionLimits.ceilingHz(10.0), 0.0)
    }

    @Test
    fun theCeilingOfferedToUsersNeverExceedsTheTrustedBand() {
        for (value in CorrectionLimits.CEILING_CHOICES_HZ) {
            assertTrue("$value Hz is outside the trusted band", value in CorrectionLimits.MIN_HZ..CorrectionLimits.MAX_HZ)
        }
        assertEquals("the widest choice must be the default", CorrectionLimits.MAX_HZ,
            CorrectionLimits.CEILING_CHOICES_HZ.last(), 0.0)
        assertTrue("choices must ascend", CorrectionLimits.CEILING_CHOICES_HZ.toList() ==
            CorrectionLimits.CEILING_CHOICES_HZ.sorted())
    }

    @Test
    fun theEffectiveCeilingTakesTheNarrowerOfMeasurementAndChoice() {
        assertEquals(5600.0, CorrectionLimits.effectiveCeilingHz(5600.0, null), 0.0)
        assertEquals(1000.0, CorrectionLimits.effectiveCeilingHz(5600.0, 1000.0), 0.0)
        assertEquals(1000.0, CorrectionLimits.effectiveCeilingHz(1000.0, 4000.0), 0.0)
        assertEquals(500.0, CorrectionLimits.effectiveCeilingHz(Double.NaN, 500.0), 0.0)
        assertEquals(CorrectionLimits.MAX_HZ, CorrectionLimits.effectiveCeilingHz(0.0, null), 0.0)
    }

    @Test
    fun aBandAboveTheCeilingIsNotCorrectable() {
        assertFalse(CorrectionLimits.isCorrectableAt(4000.0, 1000.0))
        assertTrue(CorrectionLimits.isCorrectableAt(500.0, 1000.0))
        assertFalse(CorrectionLimits.isCorrectableAt(30.0, 1000.0))
    }

    @Test
    fun theCeilingButtonWrapsThroughEveryChoiceAndBack() {
        var choice = CorrectionLimits.CeilingChoice.DEFAULT
        assertTrue("a fresh install must start at full band", choice.isFullBand)
        val seen = mutableSetOf(choice.hz)
        repeat(CorrectionLimits.CEILING_CHOICES_HZ.size - 1) {
            choice = choice.next()
            assertFalse("an intermediate choice must narrow the band", choice.isFullBand)
            seen.add(choice.hz)
        }
        assertEquals("every choice must be reachable", CorrectionLimits.CEILING_CHOICES_HZ.size, seen.size)
        assertEquals("the cycle must come back to full band", true, choice.next().isFullBand)
    }

    @Test
    fun anIndexFromAnotherBuildCannotBreakTheButton() {
        // A pref written by a build with a longer or shorter list, or a
        // negative value: the choice falls back to full band rather than
        // throwing or silently selecting 500 Hz.
        for (index in listOf(-1, -99, 5, 99, Int.MIN_VALUE, Int.MAX_VALUE)) {
            val choice = CorrectionLimits.CeilingChoice(index)
            assertEquals("index $index", CorrectionLimits.CeilingChoice.DEFAULT.hz, choice.hz, 0.0)
            assertTrue(choice.index in CorrectionLimits.CEILING_CHOICES_HZ.indices)
        }
    }

    @Test
    fun theNarrowestChoiceIsStillUsableAndTheWidestIsTheDefault() {
        val narrowest = CorrectionLimits.CeilingChoice(0)
        assertEquals(500.0, narrowest.hz, 0.0)
        assertFalse(narrowest.isFullBand)
        assertEquals(DspConstants.F_MAX, CorrectionLimits.CeilingChoice.DEFAULT.hz, 0.0)
    }

    @Test
    fun aCeilingChoiceNeverWidensWhatTheAnalysisWillUse() {
        var choice = CorrectionLimits.CeilingChoice.DEFAULT
        repeat(CorrectionLimits.CEILING_CHOICES_HZ.size + 2) {
            val applied = CorrectionLimits.effectiveCeilingHz(3000.0, choice.hz)
            assertTrue("${choice.hz} Hz widened a 3000 Hz measurement", applied <= 3000.0)
            choice = choice.next()
        }
    }
}
