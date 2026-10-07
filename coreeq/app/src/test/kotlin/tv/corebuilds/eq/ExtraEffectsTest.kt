package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.ExtraPlan
import tv.corebuilds.eq.apply.LimiterSettings
import tv.corebuilds.eq.mode.ContentType
import tv.corebuilds.eq.ui.EnhancedAudioPrefs

/**
 * 1.2.1: extras are opt-in, sized by content type, and night mode only ever
 * limits. 1.2.0 shipped BassBoost and LoudnessEnhancer on for everyone.
 */
class ExtraEffectsTest {

    @Test
    fun `switches off means nothing on top of correction, for every content type`() {
        for (type in ContentType.entries) {
            val plan = ExtraPlan.of(type, bassBoost = false, loudness = false)
            assertTrue("$type", plan.isEmpty)
            assertEquals(ExtraPlan.NONE, plan)
        }
    }

    @Test
    fun `every content type has a bass and a loudness strength`() {
        for (type in ContentType.entries) {
            // getValue throws if a type is missing, which would crash attach().
            ExtraPlan.of(type, bassBoost = true, loudness = true)
        }
    }

    @Test
    fun `strengths stay inside the platform ranges and the documented caps`() {
        for (type in ContentType.entries) {
            val plan = ExtraPlan.of(type, bassBoost = true, loudness = true)
            plan.bassStrength?.let { assertTrue("$type bass $it", it in 1..600) }
            plan.loudnessGainMb?.let { assertTrue("$type loudness $it", it in 1..400) }
        }
    }

    @Test
    fun `speech gets no bass boost and music no loudness lift`() {
        assertNull(ExtraPlan.of(ContentType.NEWS, bassBoost = true, loudness = false).bassStrength)
        assertNull(ExtraPlan.of(ContentType.PODCAST, bassBoost = true, loudness = false).bassStrength)
        assertNull(ExtraPlan.of(ContentType.MUSIC, bassBoost = false, loudness = true).loudnessGainMb)
        assertEquals(600, ExtraPlan.of(ContentType.MOVIE, bassBoost = true, loudness = false).bassStrength)
    }

    @Test
    fun `each switch only adds its own effect`() {
        val bassOnly = ExtraPlan.of(ContentType.MOVIE, bassBoost = true, loudness = false)
        assertNull(bassOnly.loudnessGainMb)
        val loudOnly = ExtraPlan.of(ContentType.MOVIE, bassBoost = false, loudness = true)
        assertNull(loudOnly.bassStrength)
        assertFalse(loudOnly.isEmpty)
    }

    @Test
    fun `night mode limiter compresses but never boosts`() {
        val night = EnhancedAudioPrefs.limiterFor(nightMode = true)
        assertTrue(night.isProtectionOnly())
        assertEquals(-12f, night.thresholdDb, 0f)
        assertEquals(4f, night.ratio, 0f)
        assertEquals(0f, night.postGainDb, 0f)
    }

    @Test
    fun `night mode off is exactly the 1_1_1 protection limiter`() {
        assertEquals(LimiterSettings(), EnhancedAudioPrefs.limiterFor(nightMode = false))
    }
}
