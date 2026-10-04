package tv.corebuilds.iconpack

/**
 * Where this build is distributed, from BuildConfig.DISTRIBUTION.
 *
 * The `github` builds (release, debug, candidate) update themselves from
 * GitHub releases and fetch Core Builds Glyphs as a signed release asset.
 * The `play` build type is the Google Play upload: Play's Device and Network
 * Abuse policy forbids a Play app from installing APKs from anywhere else, so
 * it drops REQUEST_INSTALL_PACKAGES (src/play/AndroidManifest.xml), never
 * checks GitHub for updates, and sends the Glyphs switch to the companion's
 * own Play listing instead of downloading it.
 */
object Distribution {
    val PLAY: Boolean = BuildConfig.DISTRIBUTION == "play"

    const val PLAY_STORE = "com.android.vending"

    fun playListing(pkg: String) = "market://details?id=$pkg"
    fun playListingWeb(pkg: String) = "https://play.google.com/store/apps/details?id=$pkg"
}
