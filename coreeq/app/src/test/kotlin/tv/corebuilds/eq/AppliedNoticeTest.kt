package tv.corebuilds.eq

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.AppliedNotice

/**
 * The "profile applied" toast shows on a change of profile or mode, never on a
 * plain reapply (a volume step, an Extra effects reload, a player attaching).
 */
class AppliedNoticeTest {

    @Test
    fun `the first application is announced`() {
        assertTrue(AppliedNotice().onApplied("living-room", "Everyday"))
    }

    @Test
    fun `reapplying the same profile and mode is silent`() {
        val notice = AppliedNotice()
        notice.onApplied("living-room", "Everyday")
        repeat(5) { assertFalse(notice.onApplied("living-room", "Everyday")) }
    }

    @Test
    fun `a different profile or a different mode is announced`() {
        val notice = AppliedNotice()
        notice.onApplied("living-room", "Everyday")
        assertTrue("output switched to the soundbar's profile", notice.onApplied("soundbar", "Everyday"))
        assertTrue("mode changed", notice.onApplied("soundbar", "Movie / TV"))
        assertTrue("and back", notice.onApplied("living-room", "Movie / TV"))
    }

    @Test
    fun `after nothing was applied the same profile is news again`() {
        val notice = AppliedNotice()
        notice.onApplied("living-room", "Everyday")
        notice.reset() // an output with no profile of its own paused correction
        assertTrue(notice.onApplied("living-room", "Everyday"))
    }

    @Test
    fun `ids and modes cannot run together into a false match`() {
        val notice = AppliedNotice()
        notice.onApplied("a", "bc")
        assertTrue(notice.onApplied("ab", "c"))
    }
}
