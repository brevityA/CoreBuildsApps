package tv.corebuilds.eq.companion

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Where the TV listens for the phone.
 *
 * The listener used to take a fresh ephemeral port every time the phone link opened, so the
 * port a paired phone had saved was stale on every visit: each reconnect failed once
 * ("connection refused") and then depended on mDNS to find the new port, which never comes
 * back on a network that blocks multicast (guest Wi-Fi, AP isolation, some mesh systems).
 *
 * So the TV now asks for one fixed port first and keeps it across visits and reboots. It sits
 * below Linux's ephemeral range (32768-60999), so the system never hands it out to an
 * outgoing connection. When another app does hold it, the listener falls back to an ephemeral
 * port exactly as before, and the phone finds the TV over mDNS or the QR as it always has.
 */
internal object ListenerPort {
    const val PREFERRED = 29_517

    /** A listening socket on [preferred], or on an ephemeral port when that one is taken. */
    fun open(backlog: Int, preferred: Int = PREFERRED): ServerSocket {
        val any = InetAddress.getByName("0.0.0.0")
        val first = ServerSocket()
        try {
            first.reuseAddress = true
            first.bind(InetSocketAddress(any, preferred), backlog)
            return first
        } catch (_: IOException) {
            runCatching { first.close() }
        }
        return ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(any, 0), backlog)
        }
    }
}
