package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Translating a Core EQ curve onto a television's own equaliser (1.9.0).
 *
 * The numbers that matter are the ones a person types in: sample at the TV's own centres,
 * round to its own step, refuse to invent a value outside Core EQ's trusted band, and never
 * exceed the range the menu accepts.
 */
class TvSoundEqTest {

    private val curve: (Double) -> Double = { hz ->
        when {
            hz < 200.0 -> -4.0
            hz < 800.0 -> -1.5
            hz < 2_500.0 -> 1.0
            hz < 6_000.0 -> 3.0
            else -> 0.5
        }
    }

    @Test
    fun `the grids are the ones the manufacturers publish`() {
        assertEquals(listOf(100.0, 300.0, 1_000.0, 3_000.0, 10_000.0), TvSoundEq.SAMSUNG_LG_5.centresHz)
        assertEquals(listOf(125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0), TvSoundEq.SONY_GOOGLE_7.centresHz)
        for (grid in TvSoundEq.GRIDS) {
            assertTrue("${grid.id} has no source", grid.source.isNotBlank())
            assertTrue("${grid.id} is empty", grid.centresHz.size >= 5)
            assertEquals("${grid.id} is not ascending", grid.centresHz.sorted(), grid.centresHz)
            assertEquals("${grid.id} centre duplicates", grid.centresHz.size, grid.centresHz.toSet().size)
            assertTrue(grid.minDb < 0.0 && grid.maxDb > 0.0 && grid.stepDb > 0.0)
        }
    }

    @Test
    fun `a band outside the trusted range gets no number, not a zero`() {
        // A Samsung set's 10 kHz slider is above Core EQ's 8 kHz ceiling: it has nothing to say
        // about it, and "0 dB" would read as advice to leave the treble alone.
        val setting = TvSoundEq.setting(curve, TvSoundEq.SAMSUNG_LG_5)
        assertEquals(5, setting.values.size)
        assertEquals(4, setting.covered)
        val top = setting.values.last()
        assertEquals(10_000.0, top.hz, 0.0)
        assertNull(top.db)
        // -1.5 dB rounds to -1 at Kotlin's half-up rounding, and the 10 kHz band is refused.
        assertEquals("100 Hz -4 · 300 Hz -1 · 1 kHz +1 · 3 kHz +3 · 10 kHz —", TvSoundEq.describe(setting))
    }

    @Test
    fun `a grid entirely inside the trusted range is fully covered`() {
        val setting = TvSoundEq.setting(curve, TvSoundEq.SONY_GOOGLE_7)
        assertEquals(7, setting.covered)
        assertEquals(7, setting.values.size)
        assertTrue(setting.values.all { it.db != null })
    }

    @Test
    fun `values are rounded to the menu's own step`() {
        val setting = TvSoundEq.setting({ 1.4 }, TvSoundEq.SONY_GOOGLE_7)
        assertTrue("1.4 dB should be +1", setting.values.all { it.db == 1 })
        val down = TvSoundEq.setting({ -1.6 }, TvSoundEq.SONY_GOOGLE_7)
        assertTrue("-1.6 dB should be -2", down.values.all { it.db == -2 })
    }

    @Test
    fun `the menu's range wins and the clamp is reported`() {
        val setting = TvSoundEq.setting({ 9.0 }, TvSoundEq.SAMSUNG_LG_5)
        val covered = setting.values.filter { it.db != null }
        assertTrue(covered.isNotEmpty())
        assertTrue("nothing may exceed +6", covered.all { it.db == 6 })
        assertTrue(covered.all { it.clamped })
        assertTrue(covered.all { abs(it.wantedDb - 9.0) < 1e-9 })
        assertEquals(covered.size, setting.clampedCount)

        val wide = TvSoundEq.setting({ 9.0 }, TvSoundEq.SONY_GOOGLE_7)
        assertTrue("a ±12 menu accepts +9", wide.values.all { it.db == 9 && !it.clamped })
    }

    @Test
    fun `a flat curve writes nothing at all`() {
        val setting = TvSoundEq.setting({ 0.0 }, TvSoundEq.SAMSUNG_LG_5)
        assertFalse(setting.movesSomething)
        assertEquals(0, setting.largestDb)
    }

    @Test
    fun `a curve on its own centres is interpolated in log frequency and held flat at the ends`() {
        val centres = doubleArrayOf(100.0, 1_000.0, 8_000.0)
        val gains = doubleArrayOf(-6.0, 0.0, 3.0)
        val setting = TvSoundEq.setting(centres, gains, TvSoundEq.SONY_GOOGLE_7)
        val byHz = setting.values.associateBy { it.hz }
        // 125 Hz is a little under a tenth of the way from 100 Hz to 1 kHz in octaves (log2(1.25)
        // over log2(10) = 0.097), so about -5.4 dB: nearer the -6 end than the 0 end.
        val at125 = byHz.getValue(125.0).db!!
        println("125 Hz between -6 and 0: $at125")
        assertTrue("125 Hz should be between the endpoints, got $at125", at125 in -6..-4)
        assertEquals("1 kHz is the curve's own point", 0, byHz.getValue(1_000.0).db)
        // 4 kHz is two of the three octaves from 1 kHz to 8 kHz: 0 + 3 * 2/3 = +2 dB.
        assertEquals(2, byHz.getValue(4_000.0).db)
        // The 8 kHz end is exact; there is no band above it to extrapolate into.
        assertEquals(3, byHz.getValue(8_000.0).db)
    }

    @Test
    fun `a preset maps to values, and a curve with no points writes zeros`() {
        val preset = ManualEq.BUILT_IN_PRESETS.first { it.id == "night-quiet" }
        val setting = TvSoundEq.setting(preset, TvSoundEq.SAMSUNG_LG_5)
        println(TvSoundEq.describe(setting))
        assertEquals("the 100 Hz slider should be pulled down", true, setting.values.first().db!! < 0)

        val empty = TvSoundEq.fromProfileCurve(emptyList(), TvSoundEq.SAMSUNG_LG_5)
        assertFalse(empty.movesSomething)
    }

    @Test
    fun `the report names the source, the clamp and the band it cannot speak for`() {
        val text = TvSoundEq.report(
            "Night · less boom",
            listOf(
                TvSoundEq.setting({ 9.0 }, TvSoundEq.SAMSUNG_LG_5),
                TvSoundEq.setting({ -2.0 }, TvSoundEq.SONY_GOOGLE_7)
            )
        )
        println(text)
        assertTrue(text.contains("Night · less boom"))
        assertTrue("the source is named", text.contains("Samsung and LG"))
        assertTrue("the clamp is explained", text.contains("stops at"))
        assertTrue("the refused band is explained", text.contains("outside Core EQ"))
        assertTrue("the limits are stated", text.contains("not an"))
        // Nothing in the report may claim to measure the TV.
        assertFalse(text.contains("measured the TV"))
    }

    @Test
    fun `mismatched curve arrays are refused rather than half-read`() {
        val thrown = try {
            TvSoundEq.setting(doubleArrayOf(100.0, 1_000.0), doubleArrayOf(1.0), TvSoundEq.SAMSUNG_LG_5)
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertTrue("expected an IllegalArgumentException", thrown != null)
    }

    @Test
    fun `every built-in preset can be written on a five-band tv menu`() {
        // A preset whose whole shape sits above 3 kHz would map to nothing on a Samsung set.
        // That is allowed — it is not an error — but the mapping must still be honest about it
        // rather than inventing a number at 10 kHz.
        for (preset in ManualEq.BUILT_IN_PRESETS) {
            val setting = TvSoundEq.setting(preset, TvSoundEq.SAMSUNG_LG_5)
            assertEquals(5, setting.values.size)
            assertNull("10 kHz is never covered", setting.values.last().db)
            for (value in setting.values) {
                if (value.db != null) {
                    assertTrue("${preset.id} wrote ${value.db}", value.db in -6..6)
                }
            }
        }
    }
}
