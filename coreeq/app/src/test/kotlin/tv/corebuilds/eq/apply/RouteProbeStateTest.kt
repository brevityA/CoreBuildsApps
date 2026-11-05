package tv.corebuilds.eq.apply

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProbeStateTest {
    @Test
    fun `concurrent requests join one worker but all receive completion`() {
        val state = RouteProbeState<String>()
        var called = 0
        assertTrue(state.enqueue { called++ })
        repeat(12) { assertFalse(state.enqueue { called++ }) }
        val done = state.complete(state.generation(), 10L, "speaker", true)
        assertTrue(done.publication.accepted)
        assertEquals(0, called)
        assertEquals(13, done.callbacks.size)
        done.callbacks.forEach { it() }
        assertEquals(13, called)
    }

    @Test
    fun `a callback requesting a retry cannot run it in the old completion batch`() {
        val state = RouteProbeState<String>()
        var retriesCalled = 0
        var retryStarted = false
        state.enqueue { retryStarted = state.enqueue { retriesCalled++ } }
        val old = state.generation()
        state.invalidate() // the first probe is now stale
        val done = state.complete(old, 10L, "obsolete speaker", true)
        assertFalse(done.publication.accepted)
        done.callbacks.forEach { it() }
        assertTrue(retryStarted)
        assertEquals(0, retriesCalled)
        assertNull(state.snapshot())
        val retry = state.complete(state.generation(), 20L, "hdmi_arc", true)
        retry.callbacks.forEach { it() }
        assertEquals(1, retriesCalled)
        assertEquals("hdmi_arc", state.snapshot()?.result)
    }

    @Test
    fun `invalidation and a racing publication cannot resurrect an obsolete route`() {
        val state = RouteProbeState<String>()
        val old = state.generation()
        val release = CountDownLatch(1)
        val result = AtomicReference<RouteProbeState.Publication>()
        val worker = Thread {
            release.await()
            result.set(state.publish(old, 10L, "wrong output", true))
        }.apply { isDaemon = true; start() }
        state.invalidate()
        state.hold(20L, "unconfirmed")
        release.countDown()
        worker.join(5_000L)
        assertFalse(worker.isAlive)
        assertFalse(result.get().accepted)
        assertTrue(state.isHeld())
        assertEquals("unconfirmed", state.snapshot()?.result)
    }

    @Test
    fun `give-up hold survives a failed track open but a confirmed new probe releases it`() {
        val state = RouteProbeState<String>()
        state.hold(1L, "unconfirmed")
        val failed = state.publish(state.generation(), 2L, null, false)
        assertTrue(failed.accepted)
        assertTrue(state.isHeld())
        val confirmed = state.publish(state.generation(), 3L, "speaker", true)
        assertTrue(confirmed.accepted)
        assertTrue(confirmed.changed)
        assertFalse(state.isHeld())
        assertEquals("speaker", state.snapshot()?.result)
    }

    @Test
    fun `identical refreshes update freshness without announcing a route switch`() {
        val state = RouteProbeState<String>()
        assertTrue(state.publish(state.generation(), 1L, "speaker", true).changed)
        assertFalse(state.publish(state.generation(), 2L, "speaker", true).changed)
        assertEquals(2L, state.snapshot()?.atMs)
        assertTrue(state.publish(state.generation(), 3L, "unconfirmed", false).changed)
        assertTrue(state.publish(state.generation(), 4L, "speaker", true).changed)
    }

    @Test
    fun `device invalidation removes both cached evidence and a held pause`() {
        val state = RouteProbeState<String>()
        state.hold(1L, "unconfirmed")
        val previous = state.generation()
        state.invalidate()
        assertTrue(state.generation() > previous)
        assertFalse(state.isHeld())
        assertNull(state.snapshot())
        assertFalse(state.publish(previous, 2L, "obsolete speaker", true).accepted)
    }
}
