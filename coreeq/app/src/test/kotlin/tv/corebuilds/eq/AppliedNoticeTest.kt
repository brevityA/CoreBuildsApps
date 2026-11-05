package tv.corebuilds.eq

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.AppliedNotice

/**
 * The on-screen card shows on a change of profile or mode, and once when
 * correction pauses on an output with no profile, never on a plain reapply
 * (a volume step, an Extra effects reload, a player attaching).
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

    @Test
    fun `a pause is announced once per output, and what applies after it is news`() {
        val notice = AppliedNotice()
        notice.onApplied("living-room", "Everyday")
        assertTrue("switched to an output with no profile", notice.onPaused("HDMI|Soundbar"))
        repeat(3) { assertFalse("same paused output, reapplied", notice.onPaused("HDMI|Soundbar")) }
        assertTrue("a different unmeasured output", notice.onPaused("BLUETOOTH|Headphones"))
        assertTrue("back on the measured output", notice.onApplied("living-room", "Everyday"))
    }

    @Test
    fun `a pause cannot be mistaken for a profile named like it`() {
        val notice = AppliedNotice()
        notice.onPaused("x")
        assertTrue(notice.onApplied("paused", "x"))
    }

    @Test
    fun `a brief route pause recovering to the same profile produces neither card`() {
        val notice = AppliedNotice()
        assertTrue(notice.onApplied("room", "Everyday"))
        assertTrue(notice.deferPause("unconfirmed"))
        assertFalse("same pause must not restart its settling timer", notice.deferPause("unconfirmed"))
        assertFalse("restoring the same profile is not news before the pause was announced", notice.onApplied("room", "Everyday"))
        assertFalse("old timer must not announce after recovery", notice.commitPause("unconfirmed"))
    }

    @Test
    fun `a settled pause and its eventual recovery are both announced once`() {
        val notice = AppliedNotice()
        notice.onApplied("room", "Everyday")
        assertTrue(notice.deferPause("bluetooth|unmeasured"))
        assertTrue(notice.commitPause("bluetooth|unmeasured"))
        assertFalse(notice.commitPause("bluetooth|unmeasured"))
        assertFalse(notice.deferPause("bluetooth|unmeasured"))
        assertTrue(notice.onApplied("room", "Everyday"))
    }

    @Test
    fun `a superseded pause cannot announce the wrong route`() {
        val notice = AppliedNotice()
        assertTrue(notice.deferPause("unconfirmed"))
        assertTrue(notice.deferPause("ambiguous"))
        assertFalse(notice.commitPause("unconfirmed"))
        assertTrue(notice.commitPause("ambiguous"))
    }

    @Test
    fun `returning to an already announced paused route cancels a pending other warning`() {
        val notice = AppliedNotice()
        notice.onPaused("bluetooth|unmeasured")
        notice.deferPause("unconfirmed")
        assertFalse(notice.deferPause("bluetooth|unmeasured"))
        assertFalse(notice.commitPause("unconfirmed"))
    }

    @Test
    fun `a genuine profile change is announced even if an unconfirmed warning was pending`() {
        val notice = AppliedNotice()
        notice.onApplied("speaker-room", "Everyday")
        notice.deferPause("unconfirmed")
        assertTrue(notice.onApplied("soundbar-room", "Everyday"))
        assertFalse(notice.commitPause("unconfirmed"))
    }

    @Test
    fun `reset or measurement suspension cancels a pending pause`() {
        val notice = AppliedNotice()
        notice.deferPause("unconfirmed")
        notice.cancelDeferredPause()
        assertFalse(notice.commitPause("unconfirmed"))
        notice.deferPause("ambiguous")
        notice.reset()
        assertFalse(notice.commitPause("ambiguous"))
        assertTrue(notice.onApplied("room", "Everyday"))
    }

}
