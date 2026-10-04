package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
