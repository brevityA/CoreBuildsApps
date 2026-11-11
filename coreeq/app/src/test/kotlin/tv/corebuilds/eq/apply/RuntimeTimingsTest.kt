package tv.corebuilds.eq.apply

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.corebuilds.eq.apply.RuntimeTimings.Op
import tv.corebuilds.eq.apply.RuntimeTimings.Outcome
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The timing records on a fake monotonic clock: bounded, thread-safe, outcome-aware. JVM only. */
class RuntimeTimingsTest {

    private var now = 0L
    private val ms = 1_000_000L

    @Before fun fakeClock() {
        RuntimeTimings.reset()
        RuntimeTimings.clock = { now }
    }

    @After fun realClock() = RuntimeTimings.reset()

    private fun stats(op: Op) = RuntimeTimings.snapshot().single { it.op == op }

    @Test fun `a duration is the monotonic clock's difference, recorded with its outcome`() {
        now = 5 * ms
        val started = RuntimeTimings.start()
        now = 17 * ms
        RuntimeTimings.finish(Op.APPLY, started, Outcome.NOT_APPLIED)
        val s = stats(Op.APPLY)
        assertEquals(1, s.count)
        assertEquals(12 * ms, s.lastNs)
        assertEquals(12 * ms, s.medianNs)
        assertEquals(mapOf(Outcome.NOT_APPLIED to 1L), s.outcomes)
    }

    @Test fun `a clock that goes backwards records zero, never a negative duration`() {
        now = 50 * ms
        val started = RuntimeTimings.start()
        now = 10 * ms
        RuntimeTimings.finish(Op.ROUTE_PROBE, started)
        assertEquals(0L, stats(Op.ROUTE_PROBE).lastNs)
    }

    @Test fun `time records ok on return and failed on a throw, which still propagates`() {
        val value = RuntimeTimings.time(Op.ENGINE_CONFIGURE) { now += 3 * ms; "configured" }
        assertEquals("configured", value)
        try {
            RuntimeTimings.time(Op.ENGINE_CONFIGURE) { now += 7 * ms; throw IllegalStateException("refused") }
            fail("the throw must propagate")
        } catch (e: IllegalStateException) {
            assertEquals("refused", e.message)
        }
        val s = stats(Op.ENGINE_CONFIGURE)
        assertEquals(2, s.count)
        assertEquals(mapOf(Outcome.OK to 1L, Outcome.FAILED to 1L), s.outcomes)
        assertEquals(3 * ms, s.minNs)
        assertEquals(7 * ms, s.maxNs)
        assertEquals(5 * ms, s.medianNs)
    }

    @Test fun `memory is bounded and the median covers only the most recent window`() {
        // 100 slow samples, then exactly one window of fast ones.
        repeat(100) { RuntimeTimings.record(Op.DISCOVERY, 900 * ms, Outcome.OK) }
        repeat(RuntimeTimings.WINDOW) { RuntimeTimings.record(Op.DISCOVERY, (it + 1) * ms, Outcome.OK) }
        val s = stats(Op.DISCOVERY)
        assertEquals(100L + RuntimeTimings.WINDOW, s.count)
        assertEquals("median of 1..32 ms", (16 * ms + 17 * ms) / 2, s.medianNs)
        assertEquals("min and max cover the whole run", 1 * ms, s.minNs)
        assertEquals(900 * ms, s.maxNs)
    }

    @Test fun `concurrent records from many threads are all counted`() {
        val threads = 8
        val each = 2_000
        val pool = Executors.newFixedThreadPool(threads)
        val go = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) { t ->
            pool.execute {
                go.await()
                repeat(each) { i ->
                    RuntimeTimings.record(Op.MEASUREMENT_ANALYSIS, (t * each + i).toLong(), if (i % 4 == 0) Outcome.FAILED else Outcome.OK)
                }
                done.countDown()
            }
        }
        go.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()
        val s = stats(Op.MEASUREMENT_ANALYSIS)
        assertEquals((threads * each).toLong(), s.count)
        assertEquals((threads * each / 4).toLong(), s.outcomes[Outcome.FAILED])
        assertEquals((threads * each * 3 / 4).toLong(), s.outcomes[Outcome.OK])
        assertEquals(0L, s.minNs)
        assertEquals((threads * each - 1).toLong(), s.maxNs)
    }

    @Test fun `only ops that ran are reported, in a fixed order, with non-ok outcomes named`() {
        assertEquals("none recorded", RuntimeTimings.summary())
        RuntimeTimings.record(Op.PROFILES_READY, 240 * ms, Outcome.OK)
        RuntimeTimings.record(Op.APPLY, 4_200_000L, Outcome.OK)
        RuntimeTimings.record(Op.APPLY, 18 * ms, Outcome.FAILED)
        val line = RuntimeTimings.summary()
        assertEquals(
            "apply 2× median 11 ms, 4.2 ms–18 ms (1 failed) · Profiles ready 1× median 240 ms, 240 ms–240 ms",
            line
        )
        assertFalse(line.contains("route probe"))
        val detail = RuntimeTimings.detail()
        assertTrue(detail, detail.contains("apply: count=2 last=18 ms median=11 ms min=4.2 ms max=18 ms ok=1 not_applied=0 deferred=0 no_answer=0 failed=1"))
    }

    @Test fun `milliseconds keep a tenth below ten`() {
        assertEquals("0.0 ms", RuntimeTimings.ms(0))
        assertEquals("0.4 ms", RuntimeTimings.ms(400_000))
        assertEquals("9.9 ms", RuntimeTimings.ms(9_940_000))
        assertEquals("10 ms", RuntimeTimings.ms(10_000_000))
        assertEquals("1234 ms", RuntimeTimings.ms(1_234_000_000))
    }
}
