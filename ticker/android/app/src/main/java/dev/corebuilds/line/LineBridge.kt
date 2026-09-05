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

    /** Download a newer APK and hand it to the system installer (async). */
    @JavascriptInterface
    fun installUpdate(url: String): Boolean = activity.installUpdate(url)

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

    /** Stop the floating ticker. */
    @JavascriptInterface
    fun stopOverlay(): Boolean = activity.stopOverlay()
}
