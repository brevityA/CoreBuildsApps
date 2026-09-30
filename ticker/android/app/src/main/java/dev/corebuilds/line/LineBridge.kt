package dev.corebuilds.line

import android.webkit.JavascriptInterface

class LineBridge(private val activity: MainActivity) {
    @JavascriptInterface
    fun startPair(): String = activity.startPair()

    @JavascriptInterface
    fun stopPair() {
        activity.stopPair()
    }

    @JavascriptInterface
    fun takeInbox(): String = activity.takeInbox()

    @JavascriptInterface
    fun setKeepAwake(on: Boolean) {
        activity.setKeepAwake(on)
    }

    /** JSON array of installed launchable apps for the "Watch apps" picker. */
    @JavascriptInterface
    fun listLaunchableApps(): String = activity.listLaunchableApps()

    /** Launch an installed app by package id. Returns success. */
    @JavascriptInterface
    fun openApp(packageName: String): Boolean = activity.openApp(packageName)

    /** Hand a playlist stream URL to an external player. Returns success. */
    @JavascriptInterface
    fun openStream(url: String): Boolean = activity.openStream(url)

    /** Open a URL in the system browser. Returns success. */
    @JavascriptInterface
    fun openUrl(url: String): Boolean = activity.openUrl(url)

    /** Current app version, e.g. "1.2.0". */
    @JavascriptInterface
    fun getVersion(): String = activity.getVersion()

    /** Current app versionCode from BuildConfig, never a package-info fallback. */
    @JavascriptInterface
    fun getVersionCode(): Int = activity.getVersionCode()

    /** Download a newer APK and hand it to the system installer (async). */
    @JavascriptInterface
    fun installUpdate(url: String): Boolean = activity.installUpdate(url)

    /** Download + verify package/version/signature/hash before install. */
    @JavascriptInterface
    fun installUpdateVerified(url: String, sha256: String, versionCode: Int): Boolean =
        activity.installUpdateVerified(url, sha256, versionCode)

    /** "Display over other apps" granted? */
    @JavascriptInterface
    fun canDrawOverlays(): Boolean = activity.canDrawOverlays()

    /** Floating ticker window currently showing? */
    @JavascriptInterface
    fun overlayActive(): Boolean = activity.overlayActive()

    /**
     * Overlay platform status: "supported", "unsupported" (Fire TV), or
     * "needs_permission" (permission not yet granted).
     */
    @JavascriptInterface
    fun overlayPlatform(): String = activity.overlayPlatform()

    /** Start the floating ticker (opens the permission screen if needed). */
    @JavascriptInterface
    fun startOverlay(): Boolean = activity.startOverlay()

    /** Move a running overlay. The strip also calls this after reading localStorage. */
    @JavascriptInterface
    fun setOverlayEdge(edge: String) {
        activity.setOverlayEdge(edge)
    }

    /** Stop the floating ticker. */
    @JavascriptInterface
    fun stopOverlay(): Boolean = activity.stopOverlay()

    // ---- VPN status dot ---------------------------------------------------

    /** Live VPN state as JSON: {tunnelUp, covering, validated, transport, state}. */
    @JavascriptInterface
    fun vpnStatus(): String = activity.vpnStatus()

    /** Is the VPN dot on screen right now? */
    @JavascriptInterface
    fun vpnDotActive(): Boolean = activity.vpnDotActive()

    /** Same platform answer as overlayPlatform(): Fire TV cannot draw overlays. */
    @JavascriptInterface
    fun vpnDotPlatform(): String = activity.vpnDotPlatform()

    /** Turn the dot on; opens the overlay-permission screen when it is missing. */
    @JavascriptInterface
    fun startVpnDot(configJson: String): Boolean = activity.startVpnDot(configJson)

    /** Re-shape a running dot (corner, opacity, blink) without a restart. */
    @JavascriptInterface
    fun setVpnDotConfig(configJson: String): Boolean = activity.setVpnDotConfig(configJson)

    /** Turn the dot off. */
    @JavascriptInterface
    fun stopVpnDot(): Boolean = activity.stopVpnDot()

    // ---- Playlist + TV guide import (Importer.kt) ----------------------------

    /** Download and parse a playlist on the device. False when a job is running or the link is refused. */
    @JavascriptInterface
    fun startPlaylistImport(url: String): Boolean = activity.importer.startPlaylist(url)

    /** Download and parse the TV guide from a JSON array of up to four links. */
    @JavascriptInterface
    fun startGuideImport(urlsJson: String): Boolean {
        val urls = try {
            val arr = org.json.JSONArray(urlsJson)
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
        return activity.importer.startGuide(urls)
    }

    /** `{state, kind, message, count, at}` for the current or last import. */
    @JavascriptInterface
    fun importStatus(): String = activity.importer.status()

    /** The last imported playlist as JSON, or "" when there is none. */
    @JavascriptInterface
    fun readPlaylist(): String = activity.importer.read(Importer.Kind.PLAYLIST)

    /** The last imported guide as JSON, or "" when there is none. */
    @JavascriptInterface
    fun readGuide(): String = activity.importer.read(Importer.Kind.GUIDE)

    /** Delete both imports. */
    @JavascriptInterface
    fun clearImports() {
        activity.importer.clear()
    }
}
