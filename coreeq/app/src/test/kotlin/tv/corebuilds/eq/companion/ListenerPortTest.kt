package tv.corebuilds.eq.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class ListenerPortTest {

    /** A port nothing holds right now: the OS hands one out, and it is let go at once. */
    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `listens on the preferred port when it is free`() {
        val preferred = freePort()
        ListenerPort.open(backlog = 1, preferred = preferred).use { socket ->
            assertTrue(socket.isBound)
            assertEquals(preferred, socket.localPort)
        }
    }

    @Test
    fun `falls back to an ephemeral port when another app holds the preferred one`() {
        ServerSocket(0).use { holder ->
            ListenerPort.open(backlog = 1, preferred = holder.localPort).use { socket ->
                assertTrue(socket.isBound)
                assertNotEquals(holder.localPort, socket.localPort)
            }
        }
    }

    @Test
    fun `the preferred port is below the Linux ephemeral range`() {
        // 32768-60999 is what Linux hands out to outgoing connections; a port in it
        // could be taken by any app's socket at the moment the TV opens the link.
        assertTrue(ListenerPort.PREFERRED in 1024 until 32_768)
    }
}
