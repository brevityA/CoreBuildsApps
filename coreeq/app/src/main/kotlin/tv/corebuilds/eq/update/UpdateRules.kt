package tv.corebuilds.eq.update

/**
 * The rules an update offer has to satisfy, as pure functions.
 *
 * They live apart from [UpdateChecker] and [UpdateInstaller] so a JVM unit
 * test can hold them: the checker reads the manifests with org.json and the
 * installer touches PackageManager, so neither is testable without a device.
 * These are the two decisions that turn a JSON document on the internet into
 * "install this": is the URL the one the suite publishes from, and is the
 * advertised build actually newer than the installed one. Everything else -
 * hash, package, signature - is verified against bytes that exist.
 */
object UpdateRules {

    /** The only host family a Core EQ feed may live on. */
    const val MANIFEST_PREFIX =
        "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/"

    /** The only host family an APK may be downloaded from. */
    const val APK_PREFIX =
        "https://github.com/brevityA/CoreBuildsApps/releases/download/"

    /**
     * A feed that is not served from the suite's own repository is refused, not
     * fetched: a redirected manifest is how a sideloaded app is pointed at
     * somebody else's APK.
     */
    fun isApprovedManifestUrl(url: String): Boolean =
        url.startsWith(MANIFEST_PREFIX) && url.endsWith(".json")

    /**
     * Release assets are served from github.com/.../releases/download/<tag>/<file>
     * (which redirects to a githubusercontent CDN host the installer allows,
     * but the URL it accepts is this one).
     */
    fun isApprovedApkUrl(url: String): Boolean = url.startsWith(APK_PREFIX)

    /** A manifest hash is optional; when present it must be a real SHA-256. */
    fun isSha256(value: String): Boolean = value.matches(Regex("^[0-9a-fA-F]{64}$"))

    /**
     * An update is offered only when the feed names a strictly newer code.
     * Equal means up to date; lower means the feed is stale or the installed
     * build is ahead of the channel - either way there is nothing to install,
     * and saying "update available" would be a bug report waiting to happen.
     */
    fun isNewer(remoteCode: Int, installedCode: Int): Boolean = remoteCode > installedCode
}
