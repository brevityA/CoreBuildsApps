package tv.corebuilds.eq.mode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the content type registry so app-to-content-type mappings
 * and the resolution rules cannot drift.
 */
class ContentTypeRegistryTest {

    @Test
    fun `Crunchyroll maps to anime`() {
        assertEquals(ContentType.ANIME, ContentTypeRegistry.lookup("com.crunchyroll.crunchyroid"))
        assertEquals(ContentType.ANIME, ContentTypeRegistry.lookup("com.crunchyroll.crunchyroid.tv"))
    }

    @Test
    fun `Funimation maps to anime`() {
        assertEquals(ContentType.ANIME, ContentTypeRegistry.lookup("com.funimation.funimationnow"))
    }

    @Test
    fun `Netflix maps to movies`() {
        assertEquals(ContentType.MOVIE, ContentTypeRegistry.lookup("com.netflix.ninja"))
        assertEquals(ContentType.MOVIE, ContentTypeRegistry.lookup("com.netflix.mediaclient"))
    }

    @Test
    fun `Disney+ maps to movies`() {
        assertEquals(ContentType.MOVIE, ContentTypeRegistry.lookup("com.disney.disneyplus"))
    }

    @Test
    fun `Spotify maps to music`() {
        assertEquals(ContentType.MUSIC, ContentTypeRegistry.lookup("com.spotify.tv.android"))
        assertEquals(ContentType.MUSIC, ContentTypeRegistry.lookup("com.spotify.music"))
    }

    @Test
    fun `Steam Link maps to gaming`() {
        assertEquals(ContentType.GAMING, ContentTypeRegistry.lookup("com.valvesoftware.steamlink"))
    }

    @Test
    fun `GeForce NOW maps to gaming`() {
        assertEquals(ContentType.GAMING, ContentTypeRegistry.lookup("com.nvidia.geforcenow"))
    }

    @Test
    fun `Hulu maps to TV shows`() {
        assertEquals(ContentType.TV_SHOW, ContentTypeRegistry.lookup("com.hulu.livingroomplus"))
    }

    @Test
    fun `YouTube maps to TV shows`() {
        assertEquals(ContentType.TV_SHOW, ContentTypeRegistry.lookup("com.google.android.youtube.tv"))
    }

    @Test
    fun `unknown package returns null`() {
        assertNull(ContentTypeRegistry.lookup("com.unknown.randomapp"))
        assertNull(ContentTypeRegistry.lookup("org.example.something"))
    }

    @Test
    fun `resolve with single recognized app returns its type`() {
        val packages = setOf("com.netflix.ninja")
        assertEquals(ContentType.MOVIE, ContentTypeRegistry.resolve(packages))
    }

    @Test
    fun `resolve with multiple same-type apps returns consensus`() {
        val packages = setOf("com.netflix.ninja", "com.disney.disneyplus")
        assertEquals(ContentType.MOVIE, ContentTypeRegistry.resolve(packages))
    }

    @Test
    fun `resolve with mixed content types returns GENERAL`() {
        val packages = setOf("com.netflix.ninja", "com.spotify.music")
        assertEquals(ContentType.GENERAL, ContentTypeRegistry.resolve(packages))
    }

    @Test
    fun `resolve with no recognized apps returns null`() {
        val packages = setOf("com.unknown.app1", "com.unknown.app2")
        assertNull(ContentTypeRegistry.resolve(packages))
    }

    @Test
    fun `resolve with mix of known and unknown uses known`() {
        val packages = setOf("com.crunchyroll.crunchyroid", "com.unknown.app")
        assertEquals(ContentType.ANIME, ContentTypeRegistry.resolve(packages))
    }

    @Test
    fun `packagesFor returns correct set`() {
        val animePackages = ContentTypeRegistry.packagesFor(ContentType.ANIME)
        assertTrue(animePackages.contains("com.crunchyroll.crunchyroid"))
        assertTrue(animePackages.contains("com.funimation.funimationnow"))
        assertTrue(animePackages.size >= 3)  // At least Crunchyroll, Funimation, HiDive
    }

    @Test
    fun `all content types have at least one package`() {
        for (type in ContentType.entries) {
            if (type == ContentType.GENERAL) continue  // GENERAL has no specific packages
            val count = ContentTypeRegistry.packagesFor(type).size
            assertTrue("${type.title} should have at least one package, got $count", count > 0)
        }
    }

    @Test
    fun `fromKey handles null gracefully`() {
        assertEquals(ContentType.GENERAL, ContentType.fromKey(null))
    }

    @Test
    fun `fromKey handles unknown key`() {
        assertEquals(ContentType.GENERAL, ContentType.fromKey("nonexistent"))
    }

    @Test
    fun `fromKey round-trips all types`() {
        for (type in ContentType.entries) {
            assertEquals(type, ContentType.fromKey(type.key))
        }
    }

    @Test
    fun `summary produces readable output`() {
        val summary = ContentTypeRegistry.summary()
        assertTrue(summary.contains("Content Type Registry"))
        assertTrue(summary.contains("Movies"))
        assertTrue(summary.contains("Anime"))
        assertTrue(summary.contains("Total"))
    }
}
