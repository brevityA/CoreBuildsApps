package tv.corebuilds.eq

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.update.UpdateRules

/**
 * The updater's two load-bearing decisions, pinned without a device: a feed
 * from anywhere but the suite's own repository is refused before any bytes are
 * read, and only a strictly newer versionCode counts as an update. The cases
 * that matter are the near-misses — a look-alike host, an http URL, a feed
 * that lags behind the installed build.
 */
class UpdateRulesTest {

    @Test
    fun onlyTheSuiteRepositoryServesTheFeed() {
        assertTrue(
            UpdateRules.isApprovedManifestUrl(
                "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Latestrelease/coreeq-version.json"
            )
        )
        val refused = listOf(
            // Look-alike owner: the first path segment is the owner, not a substring.
            "https://raw.githubusercontent.com/brevityA/CoreBuildsApps-evil/main/Latestrelease/coreeq-version.json",
            "https://raw.githubusercontent.com/evil/CoreBuildsApps/main/Latestrelease/coreeq-version.json",
            "https://evil.example.com/brevityA/CoreBuildsApps/main/Latestrelease/coreeq-version.json",
            "http://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Latestrelease/coreeq-version.json",
            "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Latestrelease/coreeq-version.json.php",
            "",
        )
        for (url in refused) {
            assertFalse(url, UpdateRules.isApprovedManifestUrl(url))
        }
    }

    @Test
    fun onlyTheSuitesReleaseAssetsServeTheApk() {
        assertTrue(
            UpdateRules.isApprovedApkUrl(
                "https://github.com/brevityA/CoreBuildsApps/releases/download/coreeq/coreeq-release.apk"
            )
        )
        assertFalse(
            UpdateRules.isApprovedApkUrl(
                "https://github.com/brevityA/CoreBuildsApps.evil.example/releases/download/coreeq/coreeq-release.apk"
            )
        )
        assertFalse(
            UpdateRules.isApprovedApkUrl(
                "https://github.com/someone-else/CoreBuildsApps/releases/download/coreeq/coreeq-release.apk"
            )
        )
        assertFalse(UpdateRules.isApprovedApkUrl("https://example.com/coreeq-release.apk"))
    }

    @Test
    fun onlyAStrictlyNewerBuildIsOffered() {
        assertTrue(UpdateRules.isNewer(remoteCode = 5, installedCode = 4))
        assertFalse(UpdateRules.isNewer(remoteCode = 4, installedCode = 4))
        // A feed that lags (or an installed build ahead of the channel) must
        // not produce an update offer: there is nothing to install.
        assertFalse(UpdateRules.isNewer(remoteCode = 3, installedCode = 4))
    }

    @Test
    fun aManifestHashIsCheckedShapedBeforeItIsTrusted() {
        assertTrue(UpdateRules.isSha256("66718f558b486f0b5e8da33832a286c81ab8abd3702b2a41ead61b6adf6f215d"))
        assertTrue(UpdateRules.isSha256("66718F558B486F0B5E8DA33832A286C81AB8ABD3702B2A41EAD61B6ADF6F215D"))
        assertFalse(UpdateRules.isSha256(""))
        assertFalse(UpdateRules.isSha256("66718f55"))
        assertFalse(UpdateRules.isSha256("g6718f558b486f0b5e8da33832a286c81ab8abd3702b2a41ead61b6adf6f215d"))
    }
}
