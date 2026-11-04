package tv.corebuilds.eq.mode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `app-detectable content types have at least one package`() {
        // The package registry classifies apps, not individual titles. Genre-
        // specific Sitcom and Documentary modes stay manual until Android can
        // identify the program being played; GENERAL is the mixed-content fallback.
        val typesWithoutRequiredMapping = setOf(ContentType.SITCOM, ContentType.DOCUMENTARY, ContentType.GENERAL)
        for (type in ContentType.entries) {
            if (type in typesWithoutRequiredMapping) continue
            val count = ContentTypeRegistry.packagesFor(type).size
            assertTrue("${type.title} should have an app mapping, got $count", count > 0)
        }
    }

    @Test
    fun `genre-specific content types are not inferred from app identity`() {
        assertTrue(ContentTypeRegistry.packagesFor(ContentType.SITCOM).isEmpty())
        assertTrue(ContentTypeRegistry.packagesFor(ContentType.DOCUMENTARY).isEmpty())
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

    /**
     * The screen text is the only place a user sees what a content type does.
     * Before 1.7.0 it described target curves that were never applied, so the
     * description could say anything at all. These two tests tie every
     * published number to the table the effect actually reads.
     */
    private fun bassPercent(description: String): Int? =
        if (description.contains("No bass boost")) 0
        else Regex("""Bass boost (\d+)%""").find(description)?.groupValues?.get(1)?.toInt()

    private fun loudnessDb(description: String): Double? =
        if (description.contains("no loudness gain")) 0.0
        else Regex("""loudness \+(\d+(?:\.\d+)?) dB""").find(description)
            ?.groupValues?.get(1)?.toDouble()

    @Test
    fun `every description states the bass strength that is applied`() {
        for (type in ContentType.entries) {
            val applied = tv.corebuilds.eq.apply.ExtraPlan.BASS_BOOST_STRENGTH.getValue(type) / 10
            assertEquals(
                "${type.title} description and BASS_BOOST_STRENGTH disagree",
                applied.toDouble(),
                bassPercent(type.description)?.toDouble()
            )
        }
    }

    @Test
    fun `every description states the loudness gain that is applied`() {
        for (type in ContentType.entries) {
            val applied = tv.corebuilds.eq.apply.ExtraPlan.LOUDNESS_GAIN_MB.getValue(type) / 100.0
            assertEquals("${type.title} loudness", applied, loudnessDb(type.description))
        }
    }

    @Test
    fun `no description claims a tonal or spatial effect`() {
        // The words the old text used to promise things an equaliser cannot do.
        val forbidden = listOf("optimisation", "optimization", "clarity", "presence", "stage", "spatial", "image")
        for (type in ContentType.entries) {
            for (word in forbidden) {
                assertFalse(
                    "${type.title} still says '$word'",
                    type.description.contains(word, ignoreCase = true)
                )
            }
        }
    }
}
