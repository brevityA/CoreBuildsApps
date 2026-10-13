package tv.corebuilds.eq.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvPairingQrTest {
    private val key = "00112233445566778899aabbccddeeff"

    @Test
    fun `round trips the address, port and key the TV encodes`() {
        val text = TvPairingQr.build(listOf("192.168.1.20"), 45123, key)
        assertEquals(TvPairingQr.Payload(listOf("192.168.1.20"), 45123, key), TvPairingQr.parse(text))
    }

    @Test
    fun `carries several addresses in order and caps them`() {
        val hosts = listOf("192.168.1.20", "10.0.0.5", "172.16.4.9", "192.168.50.2")
        val text = TvPairingQr.build(hosts, 45123, key)
        assertEquals(TvPairingQr.Payload(hosts.take(3), 45123, key), TvPairingQr.parse(text))
    }

    @Test
    fun `rejects other schemes and other QR content`() {
        assertNull(TvPairingQr.parse(null))
        assertNull(TvPairingQr.parse("https://example.com/?h=192.168.1.20&p=1&k=$key"))
        assertNull(TvPairingQr.parse("WIFI:T:WPA;S:home;P:secret;;"))
    }

    @Test
    fun `rejects malformed addresses and ports`() {
        assertNull(TvPairingQr.parse("coreeq://pair?h=999.1.1.1&p=1000&k=$key"))
        assertNull(TvPairingQr.parse("coreeq://pair?h=tv.local&p=1000&k=$key"))
        assertNull(TvPairingQr.parse("coreeq://pair?h=192.168.1.20&p=0&k=$key"))
        assertNull(TvPairingQr.parse("coreeq://pair?h=192.168.1.20&p=65536&k=$key"))
        assertNull(TvPairingQr.parse("coreeq://pair?h=192.168.1.20&p=port&k=$key"))
    }

    @Test
    fun `rejects an empty or bad address in a list`() {
        assertNull(TvPairingQr.parse("coreeq://pair?h=192.168.1.20,&p=1000&k=$key"))
        assertNull(TvPairingQr.parse("coreeq://pair?h=192.168.1.20,tv.local&p=1000&k=$key"))
        assertNull(TvPairingQr.parse("coreeq://pair?h=1.1.1.1,2.2.2.2,3.3.3.3,4.4.4.4&p=1000&k=$key"))
    }

    @Test
    fun `rejects keys that are not 128-bit hex`() {
        assertNull(TvPairingQr.parse("coreeq://pair?h=192.168.1.20&p=1000&k=abc"))
        assertNull(TvPairingQr.parse("coreeq://pair?h=192.168.1.20&p=1000&k=${"z".repeat(32)}"))
    }

    @Test
    fun `rejects missing and duplicated fields`() {
        assertNull(TvPairingQr.parse("coreeq://pair?h=192.168.1.20&p=1000"))
        assertNull(TvPairingQr.parse("coreeq://pair?h=192.168.1.20&h=10.0.0.5&p=1000&k=$key"))
        assertNull(TvPairingQr.parse("coreeq://pair?h192.168.1.20&p=1000&k=$key"))
    }
}
