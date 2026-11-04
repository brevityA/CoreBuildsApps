package tv.corebuilds.eq

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunPolicyTest {
    @Test
    fun freshTvInstallGetsTheWelcomeGuide() {
        assertTrue(FirstRunPolicy.shouldOffer(true, false, 10L, 10L))
    }

    @Test
    fun completedGuideAndUpgradesStayOutOfTheWay() {
        assertFalse(FirstRunPolicy.shouldOffer(true, true, 10L, 10L))
        assertFalse(FirstRunPolicy.shouldOffer(true, false, 10L, 20L))
        assertFalse(FirstRunPolicy.shouldOffer(false, false, 10L, 10L))
        assertFalse(FirstRunPolicy.shouldOffer(true, false, 0L, 0L))
    }
}
