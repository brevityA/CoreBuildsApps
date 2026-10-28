package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileExchange
import tv.corebuilds.eq.mode.ContentMode

class ProfileExchangeTest {
    @Test fun versionedProfileRoundTripsWithFreshIdentityAndModeOverlays() {
        val profile = Profile(
            id = "source-id", name = "Room", timestampMs = 1L,
            target = "flat", micType = "imported",
            filters = listOf(PeakingFilter(100.0, 1.0, -2.0))
        )
        val doc = Formats.exportProfileJson(
            profile,
            mapOf(ContentMode.GAMING to listOf(PeakingFilter(1000.0, 1.0, 1.5))),
            ContentMode.GAMING
        )
        val imported = ProfileExchange.parse(doc)
        assertNotEquals("source-id", imported.profile.id)
        assertEquals("Imported: Room", imported.profile.name)
        assertEquals(1, imported.profile.filters.size)
        assertEquals(1, imported.overlays[ContentMode.GAMING]?.size)
    }

    @Test fun rejectsUnknownOrMissingExchangeVersion() {
        assertThrows(IllegalArgumentException::class.java) { ProfileExchange.parse("{}") }
        val old = "{\"format\":\"corebuilds.core-eq/99\"}"
        assertThrows(IllegalArgumentException::class.java) { ProfileExchange.parse(old) }
    }
}
