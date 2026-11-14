package tv.corebuilds.iconpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When Home asks Google Play again ([PlusStore.sync]). A promo code redeemed
 * on a phone must reach a TV that never restarts the app, so the answer can
 * only depend on how long ago the last ask was, never on a process start.
 *
 * And which of Google Play's answers the Premium screen shows ([PlusTurns]),
 * now that it asks again on every return: only the newest, so a slow one
 * cannot undo a purchase.
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

    // Which answer the Premium screen shows ([PlusTurns]).

    @Test fun aLatePriceCannotUndoAPurchase() {
        val turns = PlusTurns()
        val first = turns.next()           // ForSale shown
        assertTrue(turns.isLatest(first))
        val recheck = turns.next()         // screen returns: price pending
        val purchase = turns.next()        // the purchase lands first: Owned
        assertTrue(turns.isLatest(purchase))
        assertFalse("the late price must not replace Owned", turns.isLatest(recheck))
    }

    @Test fun aRefundFoundLaterStillShowsThePrice() {
        val turns = PlusTurns()
        turns.next()                       // purchase: Owned
        val later = turns.next()           // a later check finds no purchase
        assertTrue("its price answer is the newest and shows", turns.isLatest(later))
    }

    @Test fun anOlderCheckGivesWayToANewerOne() {
        val turns = PlusTurns()
        val older = turns.next()
        val newer = turns.next()
        assertFalse(turns.isLatest(older))
        assertTrue(turns.isLatest(newer))
    }
}
