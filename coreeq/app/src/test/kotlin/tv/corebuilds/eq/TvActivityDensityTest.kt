package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 960 x 540dp design box fits the window it is drawn in (1.3.2): the side
 * that runs out first sets the density, and a density that would make the
 * box bigger than the window is never kept.
 */
class TvActivityDensityTest {

    @Test
    fun `a 16 by 9 window keeps the width-derived density`() {
        assertEquals(320, TvActivity.targetDensityDpi(1920, 1080))
        assertEquals(640, TvActivity.targetDensityDpi(3840, 2160))
        assertEquals(213, TvActivity.targetDensityDpi(1280, 720))
        assertEquals(320, TvActivity.targetDensityDpi(1920))
    }

    @Test
    fun `a window shorter than 16 by 9 is fitted by its height`() {
        // A phone-style build on a 1080p projector with a 48 px navigation bar.
        assertEquals(305, TvActivity.targetDensityDpi(1920, 1032))
        assertEquals(296, TvActivity.targetDensityDpi(1920, 1000))
    }

    @Test
    fun `a window taller than 16 by 9 is still fitted by its width`() {
        assertEquals(213, TvActivity.targetDensityDpi(1280, 800))
    }

    @Test
    fun `a density over the target is never kept, one slightly under it is`() {
        // The reported case: 320dpi against a 305dpi target was within the old
        // symmetric 5% and stayed, so the bottom 24dp were cut off.
        assertFalse(TvActivity.keepsCurrentDensity(currentDpi = 320, targetDpi = 305))
        assertTrue(TvActivity.keepsCurrentDensity(currentDpi = 320, targetDpi = 320))
        assertTrue(TvActivity.keepsCurrentDensity(currentDpi = 310, targetDpi = 320))
        assertFalse(TvActivity.keepsCurrentDensity(currentDpi = 240, targetDpi = 320))
    }
}
