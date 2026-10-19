package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.corebuilds.eq.apply.DiscoveredPackageResolver

class DiscoveredPackageResolverTest {

    @Test
    fun aSingleVisiblePackageCanIdentifyTheSessionUid() {
        assertEquals(
            "com.example.player",
            DiscoveredPackageResolver.uniquePackage(arrayOf("com.example.player"), "tv.corebuilds.eq")
        )
    }

    @Test
    fun ownPackageAndBlankEntriesAreNotReportedAsPlayers() {
        assertNull(
            DiscoveredPackageResolver.uniquePackage(
                arrayOf("tv.corebuilds.eq", "", "  "),
                "tv.corebuilds.eq"
            )
        )
    }

    @Test
    fun sharedUidRemainsUnknownRatherThanChoosingTheFirstPackage() {
        assertNull(
            DiscoveredPackageResolver.uniquePackage(
                arrayOf("com.example.player", "com.example.companion"),
                "tv.corebuilds.eq"
            )
        )
    }

    @Test
    fun filteredOrMissingPackageListsRemainUnknown() {
        assertNull(DiscoveredPackageResolver.uniquePackage(null, "tv.corebuilds.eq"))
        assertNull(DiscoveredPackageResolver.uniquePackage(emptyArray(), "tv.corebuilds.eq"))
    }

    @Test
    fun duplicateNamesDoNotMakeAnOtherwiseUniqueUidAmbiguous() {
        assertEquals(
            "com.example.player",
            DiscoveredPackageResolver.uniquePackage(
                arrayOf("com.example.player", "com.example.player"),
                "tv.corebuilds.eq"
            )
        )
    }
}
