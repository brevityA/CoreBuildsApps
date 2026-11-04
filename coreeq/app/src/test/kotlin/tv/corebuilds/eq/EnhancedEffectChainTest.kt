package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun a_capability_read_that_throws_is_caught_and_the_write_never_happens() {
        // A corrupted effect instance can throw from numberOfBands before any
        // write: the guard must catch that read too, and set() must not run.
        var wrote = false
        val ok = EnhancedEffectChain.safeSetBandLevel(
            band = 0,
            levelMb = 0,
            readBands = { throw RuntimeException("corrupted effect state") },
            readRange = { range },
            set = { _, _ -> wrote = true }
        )
        assertFalse(ok)
        assertFalse(wrote)
    }

    @Test
    fun a_range_read_that_throws_is_caught_too() {
        var wrote = false
        val ok = EnhancedEffectChain.safeSetBandLevel(
            band = 0,
            levelMb = 0,
            readBands = { 5 },
            readRange = { throw RuntimeException("corrupted effect state") },
            set = { _, _ -> wrote = true }
        )
        assertFalse(ok)
        assertFalse(wrote)
    }

    @Test
    fun the_set_call_itself_throwing_unsupported_operation_is_caught() {
        val ok = EnhancedEffectChain.safeSetBandLevel(
            band = 0,
            levelMb = 0,
            readBands = { 5 },
            readRange = { range },
            set = { _, _ -> throw UnsupportedOperationException("engine unavailable") }
        )
        assertFalse(ok)
    }

    @Test
    fun the_set_call_itself_throwing_runtime_is_caught_and_not_retried() {
        var calls = 0
        val ok = EnhancedEffectChain.safeSetBandLevel(
            band = 0,
            levelMb = 0,
            readBands = { 5 },
            readRange = { range },
            set = { _, _ -> calls++; throw RuntimeException("error state") }
        )
        assertFalse(ok)
        assertEquals(1, calls) // exactly one attempt: a retry would hit the same state
    }

    @Test
    fun a_good_write_reports_the_clamped_level() {
        var written: Short? = null
        val ok = EnhancedEffectChain.safeSetBandLevel(
            band = 2,
            levelMb = Short.MAX_VALUE,
            readBands = { 5 },
            readRange = { range },
            set = { _, level -> written = level }
        )
        assertTrue(ok)
        assertEquals(1500.toShort(), written)
    }
}
