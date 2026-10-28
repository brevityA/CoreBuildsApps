package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.dsp.HardwarePresets
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.ManualEqPreset
import tv.corebuilds.eq.dsp.Peaking
import tv.corebuilds.eq.dsp.PeakingFilter
import kotlin.math.abs
import kotlin.math.pow

/**
 * The shipped soundbar presets as curves, not as lists of numbers.
 *
 * `HardwarePresetsTest` covers name matching; this covers what the presets
 * actually do to the response, which is what a user hears. The JBL Bar 2.0
 * case pins the curve argued for in
 * `docs/research/core-eq-bar-2-0-everyday-curve-and-correction-review-2026-10-25.md`:
 * nothing asked for below the bar's published 70 Hz limit, and a peak boost
 * small enough to stack on a measured correction.
 */
class HardwarePresetCurvesTest {

    // 1/24 octave from 20 Hz to ~21 kHz: 220 steps. (160 steps stops at 2 kHz,
    // which would have made the treble assertions vacuous rather than failing.)
    private val grid = DoubleArray(220) { 20.0 * 2.0.pow(it / 24.0) }

    private fun preset(id: String): ManualEqPreset {
        val found = HardwarePresets.PRESETS.firstOrNull { it.id == id }
        assertNotNull("preset $id is missing", found)
        return found!!
    }

    private fun response(filters: List<PeakingFilter>): DoubleArray =
        Peaking.filterSumDb(grid, filters)

    private fun at(curve: DoubleArray, hz: Double): Double =
        curve[grid.indices.minByOrNull { abs(grid[it] - hz) }!!]

    @Test
    fun everyPresetStaysInsideTheEditorsRange() {
        for (preset in HardwarePresets.PRESETS) {
            for (filter in preset.filters) {
                assertTrue(
                    "${preset.name}: ${filter.fc} Hz is outside the editor",
                    filter.fc in 40.0..8000.0
                )
                assertTrue(
                    "${preset.name}: ${filter.gain} dB is outside the editor",
                    abs(filter.gain) <= ManualEq.MAX_GAIN_DB
                )
            }
        }
    }

    @Test
    fun theJblEverydayPresetAsksForNothingBelowTheBarsLimit() {
        val curve = response(preset("jbl-bar-2-0-everyday").filters)
        // 70 Hz at -6 dB is the only response figure JBL publishes for this bar.
        assertTrue("40 Hz is asked for ${at(curve, 40.0)} dB", at(curve, 40.0) <= 0.5)
        assertTrue("63 Hz is asked for ${at(curve, 63.0)} dB", at(curve, 63.0) <= 1.0)
        assertTrue("the body band must be lifted", at(curve, 125.0) > 1.0)
    }

    @Test
    fun theJblEverydayPresetLiftsPresenceAndTamesTheTop() {
        val curve = response(preset("jbl-bar-2-0-everyday").filters)
        assertTrue("2 kHz must be lifted", at(curve, 2000.0) > 0.5)
        assertTrue("the top end must come down", at(curve, 8000.0) < -1.0)
    }

    @Test
    fun theJblEverydayPresetIsSmallEnoughToStackOnACorrection() {
        val curve = response(preset("jbl-bar-2-0-everyday").filters)
        assertTrue("peak boost ${curve.max()} dB takes too much headroom", curve.max() <= 2.0)
        assertTrue("peak cut ${curve.min()} dB is deeper than the research supports", curve.min() >= -3.0)
    }

    @Test
    fun theJblEverydayPresetUsesSixBandsAndNoMore() {
        assertEquals(6, preset("jbl-bar-2-0-everyday").filters.size)
    }

    @Test
    fun theAirPresetNoLongerPromisesAWiderImage() {
        // The filters are unchanged; the name is the fix. An EQ cannot widen a
        // stereo image, so no preset may claim to.
        val air = preset("jbl-bar-2-0-open-stage")
        assertEquals("JBL Bar 2.0 · Air", air.name)
        assertFalse("a preset name must not promise an image change", air.name.contains("stage", ignoreCase = true))
    }

    @Test
    fun presetIdsAreUniqueAcrossEveryModel() {
        val ids = HardwarePresets.PRESETS.map { it.id }
        assertEquals("a duplicate id would silently shadow a preset", ids.size, ids.distinct().size)
        val names = HardwarePresets.PRESETS.map { it.name }
        assertEquals("a duplicate name would confuse the picker", names.size, names.distinct().size)
    }

    @Test
    fun everyModelPresetBelongsToExactlyOneModel() {
        for (preset in HardwarePresets.PRESETS) {
            assertNotNull("${preset.name} belongs to no model", HardwarePresets.modelForPreset(preset.id))
        }
    }

    @Test
    fun theJblEverydayCurveIsReachableFromTheBarName() {
        val model = HardwarePresets.matchAny(listOf("JBL BAR 2.0 ALL-IN-ONE"))
        assertNotNull("the bar must still match", model)
        assertEquals("jbl-bar-2-0-all-in-one", model!!.id)
        assertTrue(
            "the model must offer its everyday preset",
            model.presets.any { it.id == "jbl-bar-2-0-everyday" }
        )
    }
}
