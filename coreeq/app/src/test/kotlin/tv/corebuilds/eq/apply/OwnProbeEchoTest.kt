package tv.corebuilds.eq.apply

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Core EQ's own route probe must not read as playback, or each probe schedules the next. */
class OwnProbeEchoTest {
    @Test
    fun `reports while a probe plays and just after it ends are its echo`() {
        val echo = OwnProbeEcho(echoMs = 500)
        assertFalse(echo.isEcho(nowMs = 10_000))
        echo.started()
        assertTrue(echo.isEcho(nowMs = 10_000))
        assertTrue(echo.isEcho(nowMs = 60_000)) // a long probe is still its own playback
        echo.ended(nowMs = 60_120)
        assertTrue(echo.isEcho(nowMs = 60_120)) // its release report arrives after release
        assertTrue(echo.isEcho(nowMs = 60_619))
        assertFalse(echo.isEcho(nowMs = 60_620)) // after the window, playback is content again
    }

    @Test
    fun `overlapping probes stay an echo until the last one ends`() {
        val echo = OwnProbeEcho(echoMs = 500)
        echo.started()
        echo.started()
        echo.ended(nowMs = 1_000)
        assertTrue(echo.isEcho(nowMs = 5_000))
        echo.ended(nowMs = 5_000)
        assertFalse(echo.isEcho(nowMs = 5_500))
    }

    @Test
    fun `an unmatched end never leaves a permanent echo`() {
        val echo = OwnProbeEcho(echoMs = 500)
        echo.ended(nowMs = 1_000)
        echo.ended(nowMs = 1_100)
        assertFalse(echo.isEcho(nowMs = 2_000))
        echo.started()
        echo.ended(nowMs = 3_000)
        assertFalse(echo.isEcho(nowMs = 3_500))
    }
}
