package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeResolver
import tv.corebuilds.eq.mode.ModeOverride

class ContentModeResolverTest {

    @Test
    fun automaticRuleSelectsTheModeForAnActiveApp() {
        val decision = ContentModeResolver.resolve(
            activePackages = setOf("com.example.stream"),
            rules = mapOf("com.example.stream" to ContentMode.MOVIE_TV),
            automatic = true,
            selectedMode = ContentMode.EVERYDAY
        )
        assertEquals(ContentMode.MOVIE_TV, decision.mode)
        assertEquals("Automatic app rule", decision.reason)
    }

    @Test
    fun conflictingSimultaneousRulesUseEverydayAndNameTheConflicts() {
        val decision = ContentModeResolver.resolve(
            activePackages = setOf("com.example.game", "com.example.stream"),
            rules = mapOf(
                "com.example.game" to ContentMode.GAMING,
                "com.example.stream" to ContentMode.MOVIE_TV
            ),
            automatic = true,
            selectedMode = ContentMode.GAMING
        )
        assertEquals(ContentMode.EVERYDAY, decision.mode)
        assertEquals("Conflicting app rules; using Everyday", decision.reason)
        assertEquals(setOf("com.example.game", "com.example.stream"), decision.conflictingPackages)
    }

    @Test
    fun anUnmatchedAppUsesEverydayWhenAutomaticSwitchingIsOn() {
        val decision = ContentModeResolver.resolve(
            activePackages = setOf("com.example.unknown"),
            rules = emptyMap(),
            automatic = true,
            selectedMode = ContentMode.GAMING
        )
        assertEquals(ContentMode.EVERYDAY, decision.mode)
        assertEquals("No matching app rule; using Everyday", decision.reason)
    }

    @Test
    fun manualSelectionWinsWhenAutomaticSwitchingIsOff() {
        val decision = ContentModeResolver.resolve(
            activePackages = setOf("com.example.stream"),
            rules = mapOf("com.example.stream" to ContentMode.MOVIE_TV),
            automatic = false,
            selectedMode = ContentMode.GAMING
        )
        assertEquals(ContentMode.GAMING, decision.mode)
        assertEquals("Manual selection", decision.reason)
    }

    @Test
    fun manualOverrideTakesPrecedenceOverAutomaticRules() {
        val decision = ContentModeResolver.resolve(
            activePackages = setOf("com.example.stream"),
            rules = mapOf("com.example.stream" to ContentMode.MOVIE_TV),
            automatic = true,
            selectedMode = ContentMode.EVERYDAY,
            override = ModeOverride(ContentMode.GAMING, sticky = true)
        )
        assertEquals(ContentMode.GAMING, decision.mode)
        assertEquals("Manual override", decision.reason)
    }

    @Test
    fun temporaryOverrideWaitsForPlaybackThenExpiresWhenTheAppSetChanges() {
        val unstarted = ModeOverride(ContentMode.MOVIE_TV, sticky = false)
        assertEquals(unstarted, ContentModeResolver.advanceTemporaryOverride(unstarted, emptySet()))

        val started = ContentModeResolver.advanceTemporaryOverride(unstarted, setOf("com.example.stream"))
        assertEquals(setOf("com.example.stream"), started?.anchorPackages)
        assertEquals(true, started?.playbackStarted)
        assertEquals(started, ContentModeResolver.advanceTemporaryOverride(started, setOf("com.example.stream")))
        assertNull(ContentModeResolver.advanceTemporaryOverride(started, setOf("com.example.game")))
    }

    @Test
    fun incompletePlayerSnapshotsCannotStartOrExpireTemporaryOverrides() {
        val pending = ModeOverride(ContentMode.GAMING, sticky = false)
        assertEquals(
            pending,
            ContentModeResolver.reconcileTemporaryOverride(
                override = pending,
                activePackages = setOf("com.example.stream"),
                snapshotComplete = false
            )
        )

        val started = pending.copy(
            anchorPackages = setOf("com.example.stream"),
            playbackStarted = true
        )
        assertEquals(
            started,
            ContentModeResolver.reconcileTemporaryOverride(
                override = started,
                activePackages = setOf("com.example.game"),
                snapshotComplete = false
            )
        )
        assertNull(
            ContentModeResolver.reconcileTemporaryOverride(
                override = started,
                activePackages = setOf("com.example.game"),
                snapshotComplete = true
            )
        )
    }

    @Test
    fun unknownActivePlayerForcesEverydayAlongsideAKnownRule() {
        val decision = ContentModeResolver.resolve(
            activePackages = setOf("com.example.stream"),
            rules = mapOf("com.example.stream" to ContentMode.MOVIE_TV),
            automatic = true,
            selectedMode = ContentMode.GAMING,
            hasUnidentifiedPlayer = true
        )
        assertEquals(ContentMode.EVERYDAY, decision.mode)
        assertEquals("Unknown player alongside an app rule; using Everyday", decision.reason)
        assertEquals(setOf("com.example.stream"), decision.conflictingPackages)
        assertTrue(decision.hasUnidentifiedPlayer)
    }

    @Test
    fun anUnmappedActiveAppDefaultsToEverydayInsteadOfInheritingAnotherAppsMode() {
        val decision = ContentModeResolver.resolve(
            activePackages = setOf("com.example.stream", "com.example.other"),
            rules = mapOf("com.example.stream" to ContentMode.GAMING),
            automatic = true,
            selectedMode = ContentMode.MOVIE_TV
        )
        assertEquals(ContentMode.EVERYDAY, decision.mode)
        assertEquals("Unmapped player alongside an app rule; using Everyday", decision.reason)
        assertEquals(setOf("com.example.stream", "com.example.other"), decision.conflictingPackages)
    }

    @Test
    fun temporaryOverrideTracksAnUnidentifiedPlayerAsPartOfThePlayerSet() {
        val unstarted = ModeOverride(ContentMode.MOVIE_TV, sticky = false)
        assertEquals(
            unstarted,
            ContentModeResolver.advanceTemporaryOverride(unstarted, emptySet(), hasUnidentifiedPlayer = false)
        )

        val started = ContentModeResolver.advanceTemporaryOverride(
            unstarted,
            emptySet(),
            hasUnidentifiedPlayer = true
        )
        assertEquals(true, started?.playbackStarted)
        assertEquals(true, started?.anchorHasUnidentifiedPlayer)
        assertEquals(
            started,
            ContentModeResolver.advanceTemporaryOverride(started, emptySet(), hasUnidentifiedPlayer = true)
        )
        assertNull(ContentModeResolver.advanceTemporaryOverride(started, emptySet(), hasUnidentifiedPlayer = false))
    }
}
