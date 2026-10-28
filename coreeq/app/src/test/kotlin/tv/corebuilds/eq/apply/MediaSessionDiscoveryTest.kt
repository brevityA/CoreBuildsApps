package tv.corebuilds.eq.apply

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeResolver

class MediaSessionDiscoveryTest {
    @Test
    fun onlyPlayingOrBufferingSessionsContributeAnAppModeIdentity() {
        val actual = MediaSessionDiscovery.activePackages(
            listOf(
                MediaSessionCandidate("video.app", PlaybackState.STATE_PLAYING),
                MediaSessionCandidate("music.app", PlaybackState.STATE_BUFFERING),
                MediaSessionCandidate("paused.app", PlaybackState.STATE_PAUSED),
                MediaSessionCandidate("stopped.app", PlaybackState.STATE_STOPPED),
                MediaSessionCandidate("none.app", PlaybackState.STATE_NONE)
            ),
            ownPackage = "core.eq"
        )

        assertEquals(setOf("music.app", "video.app"), actual)
    }

    @Test
    fun aPublishedPlayingMediaSessionCanDriveAnAppModeRule() {
        val packages = MediaSessionDiscovery.activePackages(
            listOf(MediaSessionCandidate("stream.app", PlaybackState.STATE_PLAYING)),
            ownPackage = "core.eq"
        )
        val decision = ContentModeResolver.resolve(
            activePackages = packages,
            rules = mapOf("stream.app" to ContentMode.MOVIE_TV),
            automatic = true,
            selectedMode = ContentMode.EVERYDAY
        )

        assertEquals(ContentMode.MOVIE_TV, decision.mode)
        assertEquals("Automatic app rule", decision.reason)
    }

    @Test
    fun missingPackagesAndCoreEqAreNotTreatedAsPlayers() {
        val actual = MediaSessionDiscovery.activePackages(
            listOf(
                MediaSessionCandidate(null, PlaybackState.STATE_PLAYING),
                MediaSessionCandidate("  ", PlaybackState.STATE_PLAYING),
                MediaSessionCandidate("core.eq", PlaybackState.STATE_PLAYING)
            ),
            ownPackage = "core.eq"
        )

        assertEquals(emptySet<String>(), actual)
    }

    @Test
    fun duplicateSessionsFromOnePackageCollapseToOneRuleDemand() {
        val actual = MediaSessionDiscovery.activePackages(
            listOf(
                MediaSessionCandidate("video.app", PlaybackState.STATE_PLAYING),
                MediaSessionCandidate("video.app", PlaybackState.STATE_BUFFERING)
            ),
            ownPackage = "core.eq"
        )

        assertEquals(setOf("video.app"), actual)
    }
}
