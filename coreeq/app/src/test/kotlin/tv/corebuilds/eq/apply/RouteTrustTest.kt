package tv.corebuilds.eq.apply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.13.2: a usable route probe stands while Android's route hint reads the
 * same, so reads during someone else's playback open no probe track, and the
 * player-discovery dump runs at most once per window.
 */
class RouteTrustTest {
    private val soundbar = "hdmi_arc" to "soundbar"
    private val speakers = "speaker" to "tv"

    @Test
    fun `every answer stands inside the short window, as before`() {
        assertTrue(RouteTrust.trusted(0L, usable = false, hintAtProbe = null, hintNow = speakers))
        assertTrue(RouteTrust.trusted(RouteTrust.SHORT_MS - 1, usable = false, hintAtProbe = null, hintNow = speakers))
    }

    @Test
    fun `a usable probe stands past the short window while the hint is unchanged`() {
        for (age in listOf(RouteTrust.SHORT_MS, 60_000L, RouteTrust.MAX_TRUST_MS - 1)) {
            assertTrue("age $age", RouteTrust.trusted(age, usable = true, hintAtProbe = soundbar, hintNow = soundbar))
        }
    }

    @Test
    fun `a changed hint, the maximum age, or an unusable answer ends the trust`() {
        assertFalse(RouteTrust.trusted(60_000L, usable = true, hintAtProbe = soundbar, hintNow = speakers))
        assertFalse(RouteTrust.trusted(60_000L, usable = true, hintAtProbe = soundbar, hintNow = null))
        assertFalse(RouteTrust.trusted(RouteTrust.MAX_TRUST_MS, usable = true, hintAtProbe = soundbar, hintNow = soundbar))
        // No track could open, or UNCONFIRMED / AMBIGUOUS: recovery is a fresh probe.
        assertFalse(RouteTrust.trusted(RouteTrust.SHORT_MS, usable = false, hintAtProbe = soundbar, hintNow = soundbar))
        // An answer recorded without its hint cannot prove the hint is unchanged.
        assertFalse(RouteTrust.trusted(RouteTrust.SHORT_MS, usable = true, hintAtProbe = null, hintNow = null))
        // A clock that ran backwards proves nothing.
        assertFalse(RouteTrust.trusted(-1L, usable = true, hintAtProbe = soundbar, hintNow = soundbar))
    }

    @Test
    fun `ten minutes of a film reading the route every few seconds needs no probe after the first`() {
        // The 1.13.1 shape: discovery, re-applies and volume keys each read the
        // route; anything past 2 s opened a new probe track.
        var probes = 1
        var probedAt = 0L
        var t = 0L
        while (t < RouteTrust.MAX_TRUST_MS - 1) {
            if (!RouteTrust.trusted(t - probedAt, usable = true, hintAtProbe = soundbar, hintNow = soundbar)) {
                probes++
                probedAt = t
            }
            t += 2_500L
        }
        assertEquals(1, probes)
    }

    @Test
    fun `discovery dumps at most once per window, and says how long to wait`() {
        val throttle = DiscoveryDumpThrottle(15_000L)
        assertEquals(0L, throttle.waitMs(0L))
        throttle.ran(0L)
        assertEquals(15_000L, throttle.waitMs(0L))
        assertEquals(5_000L, throttle.waitMs(10_000L))
        assertEquals(0L, throttle.waitMs(15_000L))
        // A buffering stream: a playback change every 3 s for a minute.
        var dumps = 1
        var t = 3_000L
        while (t <= 60_000L) {
            if (throttle.waitMs(t) == 0L) {
                throttle.ran(t)
                dumps++
            }
            t += 3_000L
        }
        assertEquals(5, dumps) // 0, 15, 30, 45, 60 s: was one per change, 21
    }

    @Test
    fun `the counters add up and summarise in one line`() {
        RuntimeCounters.reset()
        RuntimeCounters.routeProbe()
        RuntimeCounters.discoveryDump()
        RuntimeCounters.discoveryDump()
        RuntimeCounters.effectWrite()
        RuntimeCounters.effectWriteSkipped()
        RuntimeCounters.effectWriteSkipped()
        RuntimeCounters.effectWriteSkipped()
        RuntimeCounters.correctionPause()
        val snap = RuntimeCounters.snapshot()
        assertEquals(RuntimeCounters.Snapshot(1, 2, 1, 3, 1), snap)
        assertEquals(
            "1 route probes · 2 discovery dumps · 1 effect writes (3 skipped, unchanged) · 1 correction pauses",
            snap.summary()
        )
        RuntimeCounters.reset()
    }
}
