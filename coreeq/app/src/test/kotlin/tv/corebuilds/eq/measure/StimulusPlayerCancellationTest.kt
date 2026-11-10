package tv.corebuilds.eq.measure

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Deterministic preparation-cancellation tests against the internal backend seam. */
class StimulusPlayerCancellationTest {

    private class FakeFocus : StimulusPlayer.FocusLease {
        val abandoned = CountDownLatch(1)
        val abandons = AtomicInteger()
        override fun abandon() {
            abandons.incrementAndGet()
            abandoned.countDown()
        }
    }

    private class FakeTrack(initialPending: Boolean = false) : StimulusPlayer.Track {
        @Volatile var pending = initialPending
        val played = CountDownLatch(1)
        val released = CountDownLatch(1)
        val writes = AtomicInteger()
        val plays = AtomicInteger()
        val stops = AtomicInteger()
        val releases = AtomicInteger()
        var writeHook: () -> Unit = {}
        override fun write(pcm: ShortArray): Int {
            writes.incrementAndGet()
            writeHook()
            return pcm.size
        }
        override fun play() {
            plays.incrementAndGet()
            played.countDown()
        }
        override fun stop() {
            stops.incrementAndGet()
        }
        override fun release() {
            releases.incrementAndGet()
            released.countDown()
        }
        override fun routedDevices(): List<AudioDeviceInfo> = emptyList()
        override fun hasPendingPlayback(frameCount: Int): Boolean = pending
    }

    private class FakeBackend : StimulusPlayer.Backend {
        val listeners = CopyOnWriteArrayList<() -> Unit>()
        val focuses = CopyOnWriteArrayList<FakeFocus>()
        private val queueLock = Any()
        var focusSequence = mutableListOf<StimulusPlayer.FocusLease?>()
        @Volatile var requestHook: (Int) -> Unit = {}
        val focusRequests = AtomicInteger()
        var trackSequence = mutableListOf<FakeTrack>()
        @Volatile var defaultTrack = FakeTrack()
        @Volatile var openHook: (Int) -> Unit = {}
        val openCalls = AtomicInteger()
        override fun requestFocus(onLoss: () -> Unit): StimulusPlayer.FocusLease? {
            val index = focusRequests.getAndIncrement()
            listeners.add(onLoss)
            // Select this request's lease before blocking in the hook; otherwise a
            // superseding request could consume the old lease from the queue.
            val lease = synchronized(queueLock) {
                if (focusSequence.isNotEmpty()) focusSequence.removeAt(0) else FakeFocus()
            }
            if (lease is FakeFocus) focuses.add(lease)
            requestHook(index)
            return lease
        }
        override fun renderStimulus(): ShortArray = shortArrayOf(0, 1, 2, 3)
        override fun openTrack(pcm: ShortArray, sessionId: Int?): StimulusPlayer.Track {
            val index = openCalls.getAndIncrement()
            val track = synchronized(queueLock) {
                if (trackSequence.isNotEmpty()) trackSequence.removeAt(0) else defaultTrack
            }
            openHook(index)
            return track
        }
    }

    private fun await(latch: CountDownLatch) {
        assertTrue("timed out waiting for playback race", latch.await(5, TimeUnit.SECONDS))
    }

    @Test fun stopDuringPreparationDoesNotOpenTrackOrLeakFocus() {
        val focus = FakeFocus()
        val backend = FakeBackend().apply { focusSequence = mutableListOf(focus) }
        lateinit var player: StimulusPlayer
        val errors = AtomicInteger()
        player = StimulusPlayer(backend, { it() }, { player.stop() })
        player.playSweep(onError = { errors.incrementAndGet() })
        assertFalse(player.isPlaying())
        assertEquals(0, backend.openCalls.get())
        assertEquals(1, focus.abandons.get())
        assertEquals(0, errors.get())
    }

    @Test fun stopDuringWriteReleasesOnlyTheLocalTrack() {
        val focus = FakeFocus()
        val track = FakeTrack()
        val backend = FakeBackend().apply {
            focusSequence = mutableListOf(focus)
            defaultTrack = track
        }
        lateinit var player: StimulusPlayer
        val errors = AtomicInteger()
        track.writeHook = { player.stop() }
        player = StimulusPlayer(backend, { it() }, {})
        player.playSweep(onError = { errors.incrementAndGet() })
        assertFalse(player.isPlaying())
        assertEquals(1, track.writes.get())
        assertEquals(0, track.plays.get())
        assertEquals(1, track.releases.get())
        assertEquals(1, focus.abandons.get())
        assertEquals(0, errors.get())
    }

    @Test fun focusLossBeforeTheLeaseIsAttachedAbandonsItAndDoesNotOpenATrack() {
        val focus = FakeFocus()
        val backend = FakeBackend().apply {
            focusSequence = mutableListOf(focus)
            requestHook = { index -> listeners[index].invoke() }
        }
        val player = StimulusPlayer(backend, { it() }, {})
        val errors = AtomicInteger()
        player.playSweep(onError = { errors.incrementAndGet() })
        assertFalse(player.isPlaying())
        assertEquals(0, backend.openCalls.get())
        assertEquals(1, focus.abandons.get())
        assertEquals(1, errors.get())
    }

    @Test fun stopDuringTrackOpenDoesNotWriteTheLateTrack() {
        val focus = FakeFocus()
        val track = FakeTrack()
        val backend = FakeBackend().apply {
            focusSequence = mutableListOf(focus)
            defaultTrack = track
        }
        lateinit var player: StimulusPlayer
        val errors = AtomicInteger()
        backend.openHook = { player.stop() }
        player = StimulusPlayer(backend, { it() }, {})
        player.playSweep(onError = { errors.incrementAndGet() })
        assertFalse(player.isPlaying())
        assertEquals(1, backend.openCalls.get())
        assertEquals(0, track.writes.get())
        assertEquals(0, track.plays.get())
        assertEquals(1, track.releases.get())
        assertEquals(1, focus.abandons.get())
        assertEquals(0, errors.get())
    }

    @Test fun stopWhileFocusRequestIsPendingAbandonsTheLateLease() {
        val requested = CountDownLatch(1)
        val release = CountDownLatch(1)
        val focus = FakeFocus()
        val backend = FakeBackend().apply {
            focusSequence = mutableListOf(focus)
            requestHook = {
                requested.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
            }
        }
        val player = StimulusPlayer(backend, { work -> Thread { work() }.start() }, {})
        val errors = AtomicInteger()
        player.playSweep(onError = { errors.incrementAndGet() })
        await(requested)
        player.stop()
        release.countDown()
        await(focus.abandoned)
        assertFalse(player.isPlaying())
        assertEquals(0, backend.openCalls.get())
        assertEquals(1, focus.abandons.get())
        assertEquals(0, errors.get())
    }

    @Test fun supersededPreparationCannotReleaseNewerFocusOrTrack() {
        val oldRequested = CountDownLatch(1)
        val allowOld = CountDownLatch(1)
        val oldFocus = FakeFocus()
        val newFocus = FakeFocus()
        val newTrack = FakeTrack(pending = true)
        val backend = FakeBackend().apply {
            focusSequence = mutableListOf(oldFocus, newFocus)
            trackSequence = mutableListOf(newTrack)
            requestHook = { index ->
                if (index == 0) {
                    oldRequested.countDown()
                    assertTrue(allowOld.await(5, TimeUnit.SECONDS))
                }
            }
        }
        val player = StimulusPlayer(backend, { work -> Thread { work() }.start() }, {})
        val oldErrors = AtomicInteger()
        val newErrors = AtomicInteger()
        player.playSweep(onError = { oldErrors.incrementAndGet() })
        await(oldRequested)
        player.playSweep(onError = { newErrors.incrementAndGet() })
        await(newTrack.played)
        allowOld.countDown()
        await(oldFocus.abandoned)
        assertTrue(player.isPlaying())
        assertEquals(1, backend.openCalls.get())
        assertEquals(0, newFocus.abandons.get())
        assertEquals(0, newTrack.stops.get())
        assertEquals(0, newTrack.releases.get())
        assertEquals(0, oldErrors.get())
        assertEquals(0, newErrors.get())
        newTrack.pending = false
        await(newFocus.abandoned)
        assertEquals(1, newFocus.abandons.get())
        assertEquals(1, newTrack.releases.get())
    }

    @Test fun supersededWriteReleasesOnlyTheOldTrack() {
        val oldWritten = CountDownLatch(1)
        val allowOld = CountDownLatch(1)
        val oldFocus = FakeFocus()
        val newFocus = FakeFocus()
        val oldTrack = FakeTrack()
        val newTrack = FakeTrack(pending = true)
        val backend = FakeBackend().apply {
            focusSequence = mutableListOf(oldFocus, newFocus)
            trackSequence = mutableListOf(oldTrack, newTrack)
        }
        oldTrack.writeHook = {
            oldWritten.countDown()
            assertTrue(allowOld.await(5, TimeUnit.SECONDS))
        }
        val player = StimulusPlayer(backend, { work -> Thread { work() }.start() }, {})
        val oldErrors = AtomicInteger()
        val newErrors = AtomicInteger()
        player.playSweep(onError = { oldErrors.incrementAndGet() })
        await(oldWritten)
        player.playSweep(onError = { newErrors.incrementAndGet() })
        await(newTrack.played)
        allowOld.countDown()
        await(oldTrack.released)
        await(oldFocus.abandoned)
        assertTrue(player.isPlaying())
        assertEquals(1, oldTrack.writes.get())
        assertEquals(0, oldTrack.plays.get())
        assertEquals(1, oldTrack.releases.get())
        assertEquals(1, oldFocus.abandons.get())
        assertEquals(0, newFocus.abandons.get())
        assertEquals(0, newTrack.stops.get())
        assertEquals(0, newTrack.releases.get())
        assertEquals(0, oldErrors.get())
        assertEquals(0, newErrors.get())
        newTrack.pending = false
        await(newFocus.abandoned)
        assertEquals(1, newFocus.abandons.get())
        assertEquals(1, newTrack.releases.get())
    }

    @Test fun supersededFocusDenialDoesNotReportTheOldError() {
        val oldRequested = CountDownLatch(1)
        val allowOld = CountDownLatch(1)
        val newFocus = FakeFocus()
        val backend = FakeBackend().apply {
            focusSequence = mutableListOf(null, newFocus)
            requestHook = { index ->
                if (index == 0) {
                    oldRequested.countDown()
                    assertTrue(allowOld.await(5, TimeUnit.SECONDS))
                }
            }
        }
        val player = StimulusPlayer(backend, { work -> Thread { work() }.start() }, {})
        val oldErrors = AtomicInteger()
        val newErrors = AtomicInteger()
        player.playSweep(onError = { oldErrors.incrementAndGet() })
        await(oldRequested)
        player.playSweep(onError = { newErrors.incrementAndGet() })
        allowOld.countDown()
        await(newFocus.abandoned)
        assertFalse(player.isPlaying())
        assertEquals(0, oldErrors.get())
        assertEquals(0, newErrors.get())
    }

    @Test fun staleFocusLossDoesNotStopTheNewerPlayback() {
        val oldFocus = FakeFocus()
        val newFocus = FakeFocus()
        val oldTrack = FakeTrack()
        val newTrack = FakeTrack(pending = true)
        val backend = FakeBackend().apply {
            focusSequence = mutableListOf(oldFocus, newFocus)
            trackSequence = mutableListOf(oldTrack, newTrack)
        }
        val player = StimulusPlayer(backend, { work -> Thread { work() }.start() }, {})
        player.playSweep(onError = {})
        await(oldFocus.abandoned)
        player.playSweep(onError = {})
        await(newTrack.played)
        backend.listeners[0].invoke()
        assertTrue(player.isPlaying())
        assertEquals(0, newFocus.abandons.get())
        assertEquals(0, newTrack.stops.get())
        newTrack.pending = false
        await(newFocus.abandoned)
        assertEquals(1, newTrack.releases.get())
    }
}