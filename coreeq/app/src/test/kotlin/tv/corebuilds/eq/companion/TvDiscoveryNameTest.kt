package tv.corebuilds.eq.companion

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDiscoveryNameTest {

    @Test
    fun `display name keeps the model and drops punctuation`() {
        assertEquals("Core EQ - SHIELD TV", TvDiscovery.displayName("SHIELD TV"))
        assertEquals("Core EQ - Chromecast-HD", TvDiscovery.displayName("Chromecast-HD"))
    }

    @Test
    fun `blank or missing model falls back to a generic name`() {
        assertEquals("Core EQ TV", TvDiscovery.displayName(null))
        assertEquals("Core EQ TV", TvDiscovery.displayName("   "))
        assertEquals("Core EQ TV", TvDiscovery.displayName("!!!"))
        assertEquals("Core EQ TV", TvDiscovery.displayName("\u00e9\u00e8"))
    }

    @Test
    fun `display name stays inside the mDNS label limit`() {
        val name = TvDiscovery.displayName("X".repeat(200))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 63)
    }

    @Test
    fun `advertised name never carries pairing material`() {
        // The advertised name comes only from the model string, so it can never include a code or token.
        val name = TvDiscovery.displayName("Model")
        assertEquals("Core EQ - Model", name)
    }

    @Test
    fun `an IPv6 address listed first does not hide the TV's IPv4 address`() {
        // Literals only: InetAddress.getByName does no lookup for them.
        val v6 = InetAddress.getByName("fe80::1")
        val v4 = InetAddress.getByName("192.168.1.20")
        assertEquals("192.168.1.20", TvDiscovery.firstIpv4(listOf(v6, v4))?.hostAddress)
        assertEquals("192.168.1.20", TvDiscovery.firstIpv4(listOf(v4))?.hostAddress)
        assertNull(TvDiscovery.firstIpv4(listOf(v6)))
        assertNull(TvDiscovery.firstIpv4(emptyList()))
    }
}
