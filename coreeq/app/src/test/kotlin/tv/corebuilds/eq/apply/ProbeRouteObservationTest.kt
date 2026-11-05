package tv.corebuilds.eq.apply

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `a real media start verifies once even when an older Android hint is coarse`() {
        val hint = PlaybackRouteHint("hdmi_arc|Soundbar")
        assertFalse(hint.observe("hdmi_arc|Soundbar", false))
        assertTrue(hint.observe("hdmi_arc|Soundbar", true))
        repeat(10) { assertFalse(hint.observe("hdmi_arc|Soundbar", true)) }
        assertFalse(hint.observe("hdmi_arc|Soundbar", false))
        assertTrue(hint.observe("hdmi_arc|Soundbar", true))
    }

    @Test
    fun `a probe that has ended by the coalesced read does not look like a media start`() {
        val idle = PlaybackRouteHint("speaker|TV")
        repeat(100) { assertFalse(idle.observe("speaker|TV", false)) }
        val playing = PlaybackRouteHint("speaker|TV")
        playing.reset("speaker|TV", true)
        repeat(100) { assertFalse(playing.observe("speaker|TV", true)) }
    }

}
