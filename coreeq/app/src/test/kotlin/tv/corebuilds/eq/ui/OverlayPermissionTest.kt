package tv.corebuilds.eq.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Home's "allow the on-screen card" bar (1.3.2) shows in exactly one case:
 * the notice is on, Android will not let Core EQ draw the card, and Later was
 * never pressed. Every other combination keeps Home as it was.
 */
class OverlayPermissionTest {

    @Test
    fun `bar shows only when the notice is on, the card is refused and Later was not pressed`() {
        for (noticeOn in listOf(false, true)) for (allowed in listOf(false, true)) for (dismissed in listOf(false, true)) {
            assertEquals(
                "noticeOn=$noticeOn allowed=$allowed dismissed=$dismissed",
                noticeOn && !allowed && !dismissed,
                OverlayPermission.homePromptVisible(noticeOn, allowed, dismissed)
            )
        }
    }

    @Test
    fun `the reported case shows the bar`() {
        // Notice on by default, no permission, Home never answered: the TV
        // that only ever saw the toast.
        assertEquals(true, OverlayPermission.homePromptVisible(noticeOn = true, allowed = false, dismissed = false))
    }
}
