package tv.corebuilds.iconpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When Home asks Google Play again ([PlusStore.sync]). A promo code redeemed
 * on a phone must reach a TV that never restarts the app, so the answer can
 * only depend on how long ago the last ask was, never on a process start.
 */
class PlusRecheckTest {

    private val t = 5_000_000L

    @Test fun theFirstReturnAlwaysAsks() {
        assertTrue(Plus.recheckDue(null, 0))
        assertTrue(Plus.recheckDue(null, t))
    }

    @Test fun aQuickReturnDoesNotAskTwice() {
        assertFalse(Plus.recheckDue(t, t))
        assertFalse(Plus.recheckDue(t, t + Plus.RECHECK_MS - 1))
    }

    @Test fun aMinuteLaterItAsksAgain() {
        assertEquals(60_000L, Plus.RECHECK_MS)
        assertTrue(Plus.recheckDue(t, t + Plus.RECHECK_MS))
        assertTrue(Plus.recheckDue(t, t + 30L * 24 * 60 * 60 * 1000))
    }

    @Test fun aClockThatWentBackwardsAsks() {
        assertTrue(Plus.recheckDue(t, t - 1))
    }
}
