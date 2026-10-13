package tv.corebuilds.eq.companion

import org.junit.Assert.assertEquals
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
}
