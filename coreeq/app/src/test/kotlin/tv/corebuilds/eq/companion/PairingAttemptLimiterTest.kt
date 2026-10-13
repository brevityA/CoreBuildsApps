package tv.corebuilds.eq.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingAttemptLimiterTest {

    @Test
    fun `defaults allow five attempts per minute then pause that peer for thirty seconds`() {
        val limiter = PairingAttemptLimiter()

        repeat(5) { attempt -> assertTrue(limiter.allow("192.168.1.9", 100L + attempt)) }
        assertFalse(limiter.allow("192.168.1.9", 105))
        assertFalse(limiter.allow("192.168.1.9", 30_104))
        assertTrue(limiter.allow("192.168.1.9", 30_105))
    }

    @Test
    fun `throttle applies to the noisy peer without locking out another phone`() {
        val limiter = PairingAttemptLimiter(maxAttempts = 2, windowMs = 1_000, blockMs = 500, maxPeers = 8)

        assertTrue(limiter.allow("192.168.1.20", 100))
        assertTrue(limiter.allow("192.168.1.20", 110))
        assertFalse(limiter.allow("192.168.1.20", 120))
        assertFalse(limiter.allow("192.168.1.20", 200))
        assertTrue(limiter.allow("192.168.1.21", 200))
    }

    @Test
    fun `blocked peer can retry after backoff and a fresh success clears state`() {
        val limiter = PairingAttemptLimiter(maxAttempts = 1, windowMs = 10_000, blockMs = 500, maxPeers = 8)

        assertTrue(limiter.allow("10.0.0.4", 1_000))
        assertFalse(limiter.allow("10.0.0.4", 1_001))
        assertFalse(limiter.allow("10.0.0.4", 1_499))
        assertTrue(limiter.allow("10.0.0.4", 1_501))
        limiter.recordSuccess("10.0.0.4")
        assertTrue(limiter.allow("10.0.0.4", 1_502))
    }

    @Test
    fun `attempt window expires and peer table remains bounded`() {
        val limiter = PairingAttemptLimiter(maxAttempts = 1, windowMs = 100, blockMs = 50, maxPeers = 2)

        assertTrue(limiter.allow("192.168.0.1", 10))
        assertFalse(limiter.allow("192.168.0.1", 11))
        assertTrue(limiter.allow("192.168.0.2", 11))
        assertTrue(limiter.allow("192.168.0.3", 11)) // evicts the least-recently-used entry
        assertTrue(limiter.allow("192.168.0.1", 12)) // an evicted peer starts with fresh, bounded state
        assertFalse(limiter.allow("192.168.0.1", 13))
        assertTrue(limiter.allow("192.168.0.1", 200)) // its attempt window has expired
    }
}
