package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.corebuilds.eq.apply.EnhancedEffectChain

/**
 * The bounds-check half of the AudioEffect guard rail (the CVE-2019-9382
 * class: an unvalidated band index or gain reaching libeffects). The clamp
 * core is pure Kotlin, so the rule is pinned here without a device; the
 * instrumented behaviour — band = -1, band = numberOfBands, level =
 * Short.MIN_VALUE against a real Equalizer — is covered on the emulator by
 * the wrapper's try/catch paths.
 */
class EnhancedEffectChainTest {

    private val range = shortArrayOf(-1500, 1500)

    @Test
    fun a_valid_band_and_level_passes_through() {
        assertEquals(300.toShort(), EnhancedEffectChain.clampBandLevel(2, 300, 5, range))
        assertEquals((-1500).toShort(), EnhancedEffectChain.clampBandLevel(0, (-1500).toShort(), 5, range))
    }

    @Test
    fun a_negative_band_index_is_refused() {
        assertNull(EnhancedEffectChain.clampBandLevel(-1, 0, 5, range))
    }

    @Test
    fun a_band_index_at_numberOfBands_is_refused() {
        // The classic out-of-bounds write: index == count is one past the end.
        assertNull(EnhancedEffectChain.clampBandLevel(5, 0, 5, range))
        assertNull(EnhancedEffectChain.clampBandLevel(100, 0, 5, range))
    }

    @Test
    fun an_extreme_level_is_clamped_to_the_devices_own_range() {
        assertEquals((-1500).toShort(), EnhancedEffectChain.clampBandLevel(0, Short.MIN_VALUE, 5, range))
        assertEquals(1500.toShort(), EnhancedEffectChain.clampBandLevel(0, Short.MAX_VALUE, 5, range))
    }

    @Test
    fun a_narrow_device_range_clamps_harder() {
        val tight = shortArrayOf(-300, 300)
        assertEquals(300.toShort(), EnhancedEffectChain.clampBandLevel(0, 2000, 5, tight))
        assertEquals((-300).toShort(), EnhancedEffectChain.clampBandLevel(0, (-2000).toShort(), 5, tight))
    }

    @Test
    fun a_device_that_reports_no_range_or_no_bands_is_refused_not_guessed() {
        assertNull(EnhancedEffectChain.clampBandLevel(0, 0, 5, null))
        assertNull(EnhancedEffectChain.clampBandLevel(0, 0, 5, shortArrayOf(-1500)))
        assertNull(EnhancedEffectChain.clampBandLevel(0, 0, 0, range))
        assertNull(EnhancedEffectChain.clampBandLevel(0, 0, 5, shortArrayOf(1500, -1500)))
    }
}
