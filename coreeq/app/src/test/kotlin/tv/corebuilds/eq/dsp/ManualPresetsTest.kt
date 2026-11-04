package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The built-in manual presets (1.9.0): more of them, each one saying what it does.
 *
 * These are tone shapes, not measurements, so the properties worth pinning are the ones a
 * listener is promised: the set is distinct, every preset stays inside the manual gain range
 * *after* the filters overlap, nothing claims to correct a room, and the frequency at which a
 * preset says it acts is the frequency at which it acts.
 */
class ManualPresetsTest {

    private val presets = ManualEq.BUILT_IN_PRESETS

    /** A third-octave grid across Core EQ's trusted band. */
    private val grid = (0..23).map { 40.0 * Math.pow(2.0, it / 3.0) }.filter { it <= 8_000.0 }

    @Test
    fun `the built-in set is bigger than it was and every entry explains itself`() {
        println("built-in presets: " + presets.joinToString { it.name })
        assertTrue("expected the generic set, got ${presets.size}", presets.size >= 12)
        assertEquals("Flat", presets.first().name)
        for (preset in presets) {
            assertTrue("${preset.id} has no description", preset.description.isNotBlank())
            assertTrue("${preset.id} has a blank name", preset.name.isNotBlank())
            // A description is what the picker shows; it must not be the name repeated back.
            assertNotEquals(preset.name, preset.description)
        }
    }

    @Test
    fun `ids and names are unique`() {
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        assertEquals(presets.size, presets.map { it.name.lowercase() }.toSet().size)
    }

    @Test
    fun `no preset duplicates another`() {
        for (i in presets.indices) {
            for (j in i + 1 until presets.size) {
                assertFalse(
                    "${presets[i].name} is ${presets[j].name}",
                    ManualEq.sameFilters(presets[i].filters, presets[j].filters)
                )
            }
        }
    }

    @Test
    fun `every filter is inside the editor's own limits`() {
        for (preset in presets) {
            for (filter in preset.filters) {
                assertTrue("${preset.id}: ${filter.fc} Hz", filter.fc in 40.0..8_000.0)
                assertTrue("${preset.id}: Q ${filter.q}", filter.q in 0.4..4.0)
                assertTrue("${preset.id}: ${filter.gain} dB", filter.gain in ManualEq.MIN_GAIN_DB..ManualEq.MAX_GAIN_DB)
            }
        }
    }

    @Test
    fun `the combined response stays inside the manual gain range`() {
        // Overlapping bells sum: three +3 dB filters 40 Hz apart are not +3 dB anywhere. The
        // promise the editor makes is about the *curve*, so it is the curve that is checked.
        for (preset in presets) {
            if (preset.filters.isEmpty()) continue
            var peak = 0.0
            var trough = 0.0
            for (hz in grid) {
                val db = ManualEq.responseDb(preset.filters, hz)
                peak = maxOf(peak, db)
                trough = minOf(trough, db)
            }
            println(String.format(java.util.Locale.US, "%-24s peak %+.2f  trough %+.2f", preset.name, peak, trough))
            assertTrue("${preset.name} peaks at $peak", peak <= ManualEq.MAX_GAIN_DB + 1e-9)
            assertTrue("${preset.name} troughs at $trough", trough >= ManualEq.MIN_GAIN_DB - 1e-9)
        }
    }

    @Test
    fun `a preset that says it lifts a band lifts that band`() {
        fun at(id: String, hz: Double) = ManualEq.responseDb(presets.first { it.id == id }.filters, hz)
        assertTrue("speech clarity at 2 kHz", at("speech-clarity", 2_000.0) > 1.5)
        assertTrue("podcast at 2 kHz", at("podcast", 2_000.0) > 1.5)
        assertTrue("bright at 6.3 kHz", at("bright", 6_300.0) > 1.0)
        assertTrue("warm at 125 Hz", at("warm", 125.0) > 1.5)
    }

    @Test
    fun `a preset that says it takes the bass away takes it away`() {
        fun at(id: String, hz: Double) = ManualEq.responseDb(presets.first { it.id == id }.filters, hz)
        assertTrue("night at 63 Hz", at("night-quiet", 63.0) < -1.5)
        assertTrue("podcast at 63 Hz", at("podcast", 63.0) < -1.0)
        assertTrue("small speaker at 50 Hz", at("small-speaker-body", 50.0) < -2.0)
        assertTrue("thin speaker gets body at 125 Hz", at("small-speaker-body", 125.0) > 1.0)
    }

    @Test
    fun `the two bass presets are different shapes, not two names for one`() {
        val lessBass = presets.first { it.id == "less-bass" }.filters
        val removeBoom = presets.first { it.id == "remove-boom" }.filters
        // Less bass reaches deepest and has given up by 250 Hz; Remove boom is the mirror: soft
        // at 50 Hz, deepest at 200 Hz. Measured, not assumed — the bells overlap.
        println(
            String.format(
                java.util.Locale.US,
                "50 Hz: %.2f / %.2f · 125 Hz: %.2f / %.2f · 250 Hz: %.2f / %.2f",
                ManualEq.responseDb(lessBass, 50.0), ManualEq.responseDb(removeBoom, 50.0),
                ManualEq.responseDb(lessBass, 125.0), ManualEq.responseDb(removeBoom, 125.0),
                ManualEq.responseDb(lessBass, 250.0), ManualEq.responseDb(removeBoom, 250.0)
            )
        )
        assertTrue("Less bass digs into 50 Hz", ManualEq.responseDb(lessBass, 50.0) < -3.0)
        assertTrue("Remove boom barely touches 50 Hz", ManualEq.responseDb(removeBoom, 50.0) > -1.5)
        assertTrue("Less bass still cuts 63 Hz", ManualEq.responseDb(lessBass, 63.0) < -3.0)
        assertTrue("Remove boom is deeper at 250 Hz", ManualEq.responseDb(removeBoom, 250.0) < -3.0)
        assertTrue("Less bass has let go by 250 Hz", ManualEq.responseDb(lessBass, 250.0) > -1.0)
    }

    /**
     * The peaks and troughs printed in
     * `docs/research/core-eq-manual-presets-and-tv-sound-eq-2026-10-28.md`, and the numbers the
     * preset descriptions speak in terms of. Measured with the same RBJ arithmetic the app uses,
     * over a 4001-point log sweep from 40 Hz to 8 kHz; the tolerance is a third of a dB, which is
     * far tighter than any ear and loose enough for a rounding difference in the sweep.
     */
    @Test
    fun `each preset does what its description claims, at the frequency it names`() {
        data class Expected(val id: String, val hz: Double, val db: Double)

        val expected = listOf(
            Expected("bass-lift", 75.0, 3.94),
            Expected("speech-clarity", 2_102.0, 3.00),
            Expected("less-bass", 50.0, -4.57),
            Expected("night-quiet", 57.0, -5.79),
            Expected("night-quiet", 1_896.0, 1.15),
            Expected("warm", 143.0, 2.65),
            Expected("warm", 6_806.0, -2.09),
            Expected("bright", 5_768.0, 3.26),
            Expected("bright", 125.0, -0.99),
            Expected("podcast", 1_921.0, 2.87),
            Expected("podcast", 71.0, -2.59),
            Expected("sibilance-tame", 6_970.0, -4.64),
            Expected("small-speaker-body", 234.0, 2.07),
            Expected("small-speaker-body", 46.0, -4.29),
            Expected("remove-boom", 147.0, -4.11),
            Expected("low-volume-loudness", 61.0, 2.95)
        )
        for (row in expected) {
            val preset = presets.firstOrNull { it.id == row.id }
            assertTrue("no preset ${row.id}", preset != null)
            val actual = ManualEq.responseDb(preset!!.filters, row.hz)
            println(String.format(java.util.Locale.US, "%-20s %7.0f Hz  %+.2f (doc %+.2f)", row.id, row.hz, actual, row.db))
            assertTrue(
                "${row.id} at ${row.hz} Hz: expected ~${row.db}, got $actual",
                abs(actual - row.db) < 0.34
            )
        }
    }

    @Test
    fun `no preset reaches outside the trusted band`() {
        for (preset in presets) {
            assertEquals(0.0, ManualEq.responseDb(preset.filters, 30.0), 0.0)
            assertEquals(0.0, ManualEq.responseDb(preset.filters, 9_000.0), 0.0)
        }
    }

    @Test
    fun `every preset round-trips through the saved-preset sanitiser unchanged`() {
        for (preset in presets) {
            val cleaned = ManualEq.sanitize(preset.filters)
            assertTrue("${preset.id} changed under sanitize", ManualEq.sameFilters(cleaned, preset.filters))
        }
    }

    @Test
    fun `a stored preset without a description still loads`() {
        // The field is defaulted so backups and custom presets written before 1.9.0 keep working,
        // and it takes no part in matching.
        val stored = ManualEqPreset("custom-1", "Mine", listOf(PeakingFilter(1_000.0, 1.0, 2.0)))
        assertEquals("", stored.description)
        assertTrue(ManualEq.sameFilters(stored.filters, ManualEq.sanitize(stored.filters)))
        val described = stored.copy(description = "Something")
        assertTrue("a description must not change identity", ManualEq.sameFilters(described.filters, stored.filters))
    }

    @Test
    fun `flat is exactly nothing`() {
        val flat = presets.first { it.id == "flat" }
        assertTrue(flat.filters.isEmpty())
        assertNull(ManualEq.matchingPreset(listOf(PeakingFilter(1_000.0, 1.0, 2.0)), listOf(flat)))
    }
}
