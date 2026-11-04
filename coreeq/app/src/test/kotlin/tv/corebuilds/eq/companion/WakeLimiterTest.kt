package tv.corebuilds.eq.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeLimiterTest {
    @Test
    fun `the first wake is always allowed`() {
        assertTrue(WakeLimiter.shouldWake(nowMs = 1_000L, lastWakeMs = null))
    }

    @Test
    fun `a second wake inside the interval is refused`() {
        assertFalse(WakeLimiter.shouldWake(nowMs = 1_000L + WakeLimiter.MIN_INTERVAL_MS - 1, lastWakeMs = 1_000L))
    }

    @Test
    fun `a wake at or after the interval is allowed`() {
        assertTrue(WakeLimiter.shouldWake(nowMs = 1_000L + WakeLimiter.MIN_INTERVAL_MS, lastWakeMs = 1_000L))
    }
}
