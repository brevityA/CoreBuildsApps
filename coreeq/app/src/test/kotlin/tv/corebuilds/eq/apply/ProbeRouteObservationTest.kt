package tv.corebuilds.eq.apply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.PlaybackRouteHint.Verdict.NONE
import tv.corebuilds.eq.apply.PlaybackRouteHint.Verdict.ROUTE_CHANGED
import tv.corebuilds.eq.apply.PlaybackRouteHint.Verdict.VERIFY_START

class ProbeRouteObservationTest {
    @Test
    fun `initial routing and duplicate start callbacks are not a route change`() {
        val observation = ProbeRouteObservation<Int>()
        observation.observe(emptyList())
        observation.observe(listOf(17))
        repeat(6) { observation.observe(listOf(17)) }
        assertFalse(observation.changed())
    }

    @Test
    fun `actually moving between endpoints is a route change even when the probe returns`() {
        val observation = ProbeRouteObservation<Int>()
        observation.observe(listOf(17))
        observation.observe(listOf(21))
        observation.observe(listOf(17))
        assertTrue(observation.changed())
    }

    @Test
    fun `multi-route ordering and duplicate identities do not invent an extra change`() {
        val observation = ProbeRouteObservation<Int>()
        observation.observe(listOf(17, 21))
        observation.observe(listOf(21, 17, 17))
        assertFalse(observation.changed()) // multi-route itself is blocked by OutputRoute.resolveCandidates
        observation.observe(listOf(21))
        assertTrue(observation.changed())
    }

    @Test
    fun `no reported endpoint is not evidence of a changed or confirmed route`() {
        val observation = ProbeRouteObservation<Int>()
        repeat(5) { observation.observe(emptyList()) }
        assertFalse(observation.changed()) // the final empty read must remain UNCONFIRMED
    }

    @Test
    fun `repeated playback and probe callbacks with an unchanged hint request no more probes`() {
        val hint = PlaybackRouteHint("speaker|TV")
        repeat(100) { assertFalse(hint.changed("speaker|TV")) }
        assertTrue(hint.changed("hdmi_arc|Soundbar"))
        repeat(100) { assertFalse(hint.changed("hdmi_arc|Soundbar")) }
        assertTrue(hint.changed("speaker|TV"))
    }

    @Test
    fun `a device callback seeds the hint so its playback callback cannot duplicate the probe`() {
        val hint = PlaybackRouteHint<String?>("speaker|TV")
        hint.reset("bluetooth|Headphones")
        assertFalse(hint.changed("bluetooth|Headphones"))
        assertTrue(hint.changed(null))
        assertFalse(hint.changed(null))
        assertTrue(hint.changed("speaker|TV"))
    }

    @Test
    fun `a coarse hint's media start verifies quietly, at most once per window`() {
        // Older Android: the hint is connected devices only, so a start is the
        // one moment a silent route move could be caught. 1.13.2 keeps that
        // check but runs it once per START_RECHECK_MS, not on every start.
        val window = PlaybackRouteHint.START_RECHECK_MS
        val hint = PlaybackRouteHint("hdmi_arc|Soundbar")
        assertEquals(NONE, hint.observe("hdmi_arc|Soundbar", false, preciseHint = false, nowMs = 0L))
        assertEquals(VERIFY_START, hint.observe("hdmi_arc|Soundbar", true, preciseHint = false, nowMs = 1_000L))
        repeat(10) { assertEquals(NONE, hint.observe("hdmi_arc|Soundbar", true, preciseHint = false, nowMs = 2_000L)) }
        // A buffering stream: stop, start, stop, start ... inside the window.
        var t = 3_000L
        repeat(20) {
            assertEquals(NONE, hint.observe("hdmi_arc|Soundbar", false, preciseHint = false, nowMs = t))
            assertEquals(NONE, hint.observe("hdmi_arc|Soundbar", true, preciseHint = false, nowMs = t + 500L))
            t += 4_000L
        }
        assertEquals(NONE, hint.observe("hdmi_arc|Soundbar", false, preciseHint = false, nowMs = 1_000L + window))
        assertEquals(VERIFY_START, hint.observe("hdmi_arc|Soundbar", true, preciseHint = false, nowMs = 1_000L + window))
    }

    @Test
    fun `a precise hint's media start is no route check at all`() {
        // API 33+ reports the actual media route: a moved route changes the hint.
        val hint = PlaybackRouteHint("hdmi_arc|Soundbar")
        var t = 0L
        repeat(50) {
            assertEquals(NONE, hint.observe("hdmi_arc|Soundbar", false, preciseHint = true, nowMs = t))
            assertEquals(NONE, hint.observe("hdmi_arc|Soundbar", true, preciseHint = true, nowMs = t + 500L))
            t += PlaybackRouteHint.START_RECHECK_MS
        }
    }

    @Test
    fun `a changed hint is a route change whenever it happens, and resets the start window`() {
        val hint = PlaybackRouteHint("speaker|TV")
        assertEquals(ROUTE_CHANGED, hint.observe("hdmi_arc|Soundbar", true, preciseHint = true, nowMs = 0L))
        assertEquals(ROUTE_CHANGED, hint.observe("speaker|TV", true, preciseHint = false, nowMs = 10L))
        // The full check just ran: a coarse start right after it adds nothing.
        assertEquals(NONE, hint.observe("speaker|TV", false, preciseHint = false, nowMs = 20L))
        assertEquals(NONE, hint.observe("speaker|TV", true, preciseHint = false, nowMs = 30L))
    }

    @Test
    fun `a probe that has ended by the coalesced read does not look like a media start`() {
        val idle = PlaybackRouteHint("speaker|TV")
        repeat(100) { assertEquals(NONE, idle.observe("speaker|TV", false, preciseHint = false, nowMs = it * 1_000_000L)) }
        val playing = PlaybackRouteHint("speaker|TV")
        playing.reset("speaker|TV", true)
        repeat(100) { assertEquals(NONE, playing.observe("speaker|TV", true, preciseHint = false, nowMs = it * 1_000_000L)) }
    }

}
