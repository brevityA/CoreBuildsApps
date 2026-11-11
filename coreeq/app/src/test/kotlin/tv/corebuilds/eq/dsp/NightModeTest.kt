package tv.corebuilds.eq.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.LimiterSettings

/**
 * Night mode's levels against the protection limiter they replace.
 *
 * These check the configured static curve (hard knee, steady input), which is
 * what Core EQ controls. They do not measure a device's limiter, its attack
 * behaviour or true peak. Clarity is pinned by `ToneLayersTest` and
 * `ListeningLayersTest`.
 */
class NightModeTest {

    private val protection = LimiterSettings()

    @Test
    fun `every level only reduces gain, never adds it`() {
        for (level in NightMode.Level.entries) {
            val settings = NightMode.limiter(level)
            assertTrue("$level must be protection-only", settings.isProtectionOnly())
            assertEquals("$level adds makeup gain", 0f, settings.postGainDb, 0f)
        }
    }

    @Test
    fun `every level sits at or below protection up to +12 dBFS`() {
        for (level in NightMode.Level.entries) {
            val settings = NightMode.limiter(level)
            var input = -60.0
            while (input <= NightMode.GUARANTEED_UP_TO_DBFS + 1e-9) {
                val night = NightMode.staticOutputDb(settings, input)
                val guard = NightMode.staticOutputDb(protection, input)
                assertTrue("$level at ${input} dBFS gives $night dB, protection gives $guard dB", night <= guard + 1e-9)
                input += 0.25
            }
        }
    }

    @Test
    fun `strong reduces at least as much as light at every level`() {
        var input = -60.0
        while (input <= 12.0) {
            val light = NightMode.staticOutputDb(NightMode.limiter(NightMode.Level.LIGHT), input)
            val strong = NightMode.staticOutputDb(NightMode.limiter(NightMode.Level.STRONG), input)
            assertTrue("at $input dBFS strong ($strong) is above light ($light)", strong <= light + 1e-9)
            input += 0.5
        }
    }

    @Test
    fun `quiet material passes untouched at every level`() {
        for (level in NightMode.Level.entries) {
            assertEquals(-30.0, NightMode.staticOutputDb(NightMode.limiter(level), -30.0), 1e-9)
        }
    }

    @Test
    fun `strong is exactly the night switch that shipped through 1_13_2`() {
        val strong = NightMode.limiter(NightMode.Level.STRONG)
        assertEquals(-12f, strong.thresholdDb, 0f)
        assertEquals(4f, strong.ratio, 0f)
        assertEquals(5f, strong.attackMs, 0f)
        assertEquals(50f, strong.releaseMs, 0f)
        assertEquals(LimiterSettings(), NightMode.limiter(NightMode.Level.OFF))
    }

    @Test
    fun `stepping wraps for OK and the labels say what each level does`() {
        assertEquals(NightMode.Level.LIGHT, NightMode.Level.OFF.next())
        assertEquals(NightMode.Level.OFF, NightMode.Level.STRONG.next())
        assertEquals(NightMode.Level.STRONG, NightMode.Level.OFF.previous())
        assertEquals("night: light (−6 dB, 3:1)", NightMode.describe(NightMode.Level.LIGHT))
        for (level in NightMode.Level.entries) {
            val words = NightMode.describe(level)
            assertTrue(!words.contains("normali", ignoreCase = true) && !words.contains("isolat", ignoreCase = true))
        }
    }
}
