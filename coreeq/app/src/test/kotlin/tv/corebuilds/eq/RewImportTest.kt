package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.MeasurementException
import tv.corebuilds.eq.dsp.RewImport
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import java.util.Locale

/**
 * REW import (plan M10): the parser refuses what it cannot read, and the
 * analysis runs the sweep chain's maths on imported magnitudes. Includes the
 * community fixture from docs/research/core-eq-community-input-2026-09-30.md:
 * a narrow room mode must never be mistaken for the loudspeaker's roll-off.
 */
class RewImportTest {

    /** A text export shaped like REW's: `*` comment rows, then f/Mag/Phase. */
    private fun rewText(points: List<Pair<Double, Double>>): String = buildString {
        append("* Room EQ Wizard Measurement\n")
        append("* Source: RewImportTest\n")
        append("Freq(Hz)\tSPL(dB)\tPhase(deg)\n")
        for ((f, m) in points) append("$f\t$m\t0.0\n")
    }

    /** Sampled 20 Hz–20 kHz, 32 points per octave — plenty for every band. */
    private fun sweep(
        at: (Double) -> Double,
        fLo: Double = 20.0,
        fHi: Double = 20000.0
    ): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        val steps = (32 * kotlin.math.log2(fHi / fLo)).toInt()
        for (i in 0..steps) {
            val f = fLo * exp(ln(fHi / fLo) * i / steps)
            out.add(f to at(f))
        }
        return out
    }

    @Test
    fun rewTextAndCsvBothParse() {
        val points = sweep({ 0.0 })
        val (f1, m1) = RewImport.parseMagnitudeText(rewText(points))
        assertEquals(points.size, f1.size)
        assertEquals(20.0, f1.first(), 1e-9)
        assertEquals(0.0, m1[0], 1e-9)

        val csv = "Freq(Hz),SPL(dB)\n" + points.joinToString("\n") { "${it.first},${it.second}" }
        val (f2, m2) = RewImport.parseMagnitudeText(csv)
        assertEquals(f1.size, f2.size)
        assertEquals(m1.size, m2.size)

        // REW's documented locale-safe format: decimal commas with a
        // semicolon delimiter (comma splitting would silently corrupt it).
        val decimalComma = points.joinToString("\n") {
            String.format(Locale.GERMANY, "%.3f; %.2f", it.first, it.second)
        }
        val (f3, _) = RewImport.parseMagnitudeText(decimalComma)
        assertEquals(points.size, f3.size)
        assertEquals(20.0, f3.first(), 0.001)

        val decimalCommaSpace = points.joinToString("\n") {
            String.format(Locale.GERMANY, "%.3f %.2f", it.first, it.second)
        }
        val (f4, _) = RewImport.parseMagnitudeText(decimalCommaSpace)
        assertEquals(points.size, f4.size)
        assertEquals(20.0, f4.first(), 0.001)
    }

    @Test
    fun unparseableFilesAreRefusedWithTheReason() {
        // Too few points.
        try {
            RewImport.parseMagnitudeText(rewText(listOf(10.0 to 0.0, 20.0 to 0.0)))
            fail("short file must be refused")
        } catch (e: MeasurementException) {
            assertTrue(e.message!!.contains("frequency points"))
        }
        // Not ascending.
        val backwards = sweep({ 0.0 }).reversed()
        try {
            RewImport.parseMagnitudeText(rewText(backwards))
            fail("unsorted file must be refused")
        } catch (e: MeasurementException) {
            assertTrue(e.message!!.contains("lowest to highest"))
        }
        // Correctable band not covered.
        try {
            RewImport.parseMagnitudeText(rewText(sweep({ 0.0 }, fLo = 100.0)))
            fail("a file starting at 100 Hz must be refused")
        } catch (e: MeasurementException) {
            assertTrue(e.message!!.contains("starts at"))
        }
        try {
            RewImport.parseMagnitudeText(rewText(sweep({ 0.0 }, fHi = 5000.0)))
            fail("a file stopping at 5 kHz must be refused")
        } catch (e: MeasurementException) {
            assertTrue(e.message!!.contains("stops at"))
        }
    }

    @Test
    fun aFlatImportNeedsAlmostNoCorrection() {
        val points = sweep({ 0.0 })
        val (f, m) = RewImport.parseMagnitudeText(rewText(points))
        val r = RewImport.analyze(f, m, target = "flat", volumeM3 = null)
        assertEquals(DspConstants.UNKNOWN_ROOM_TRANSITION_HZ, r.transitionHz, 1e-9)
        assertTrue("flat must not be corrected hard: ${r.correctionDb.toList()}",
            r.correctionDb.all { abs(it) < 2.0 })
        assertTrue("the export preamp can never be positive", r.preampDb <= 0.0)
    }

    @Test
    fun aModalPeakGetsCutAndANullIsNeverBoosted() {
        val points = sweep({ f ->
            val mode = 9.0 * exp(-0.5 * (ln(f / 80.0) / 0.09) * (ln(f / 80.0) / 0.09))
            val dip = -18.0 * exp(-0.5 * (ln(f / 125.0) / 0.06) * (ln(f / 125.0) / 0.06))
            mode + dip
        })
        val (f, m) = RewImport.parseMagnitudeText(rewText(points))
        val r = RewImport.analyze(f, m, target = "flat", volumeM3 = 55.0)

        val at80 = r.centresHz.indexOfFirst { abs(it - 80.0) < 1.0 }
        assertTrue("the 80 Hz mode must be in the grid", at80 >= 0)
        assertTrue("a +9 dB mode must be cut, got ${r.correctionDb[at80]}",
            r.correctionDb[at80] < -2.0)

        val at125 = r.centresHz.indexOfFirst { abs(it - 125.0) < 1.0 }
        assertTrue("the 125 Hz dip must be in the grid", at125 >= 0)
        assertTrue("never boost into a null, got ${r.correctionDb[at125]}",
            r.correctionDb[at125] <= 0.5)
    }

    @Test
    fun aRoomModeIsNeverMistakenForTheRollOff() {
        // The community fixture (A1 Evo thread): a narrow ~45 Hz room mode on
        // a speaker that plays flat well below it. A detector that reads the
        // mode as the loudspeaker's roll-off loses half an octave of bass.
        val points = sweep({ f ->
            6.0 * exp(-0.5 * (ln(f / 45.0) / 0.07) * (ln(f / 45.0) / 0.07))
        }, fLo = 15.0)
        val (f, m) = RewImport.parseMagnitudeText(rewText(points))
        val r = RewImport.analyze(f, m, target = "flat", volumeM3 = null)

        assertEquals("a flat low end keeps the detector at its minimum despite the 45 Hz mode",
            DspConstants.F_MIN, r.rolloffHz, 1e-9)
        assertEquals("the correction floor must stay at its 40 Hz promise",
            DspConstants.F_MIN, r.floorHz, 1e-9)
    }

    @Test
    fun importsCarryTheirLimits() {
        val points = sweep({ 0.0 })
        val (f, m) = RewImport.parseMagnitudeText(rewText(points))
        val r = RewImport.analyze(f, m, target = "flat", volumeM3 = null)
        // This path does no decay or phase analysis: the transition defaults
        // honestly, and the unverified gate is recorded rather than passed.
        assertFalse(r.minPhaseGateVerified)
        assertEquals(DspConstants.UNKNOWN_ROOM_TRANSITION_HZ, r.transitionHz, 1e-9)
        assertEquals(points.size, r.pointsRead)
    }
}
