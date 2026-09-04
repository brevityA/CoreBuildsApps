package dev.corebuilds.line

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val pairServer = PairServer()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUi()

        webView = WebView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isFocusable = true
            isFocusableInTouchMode = true
            webViewClient = LineWebClient(this@MainActivity)
            webChromeClient = WebChromeClient()
        }
        configure(webView.settings)
        webView.addJavascriptInterface(LineBridge(this), "CoreLineNative")
        setContentView(webView)
        webView.requestFocus()
        setKeepAwake(true)

        val tv = isTelevision()
        val query = buildString {
            append("native=1")
            if (tv) append("&tv=1")
        }
        webView.loadUrl("https://${LineWebClient.HOST}/index.html?$query")

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            ) { handleBack() }
        }
    }

    fun startPair(): String = synchronized(pairServer) {
        return try {
            pairServer.start().toString()
        } catch (err: Exception) {
            JSONObject()
                .put("ok", false)
                .put("error", err.message ?: "could not start pair server")
                .toString()
        }
    }

    fun stopPair() {
        synchronized(pairServer) { pairServer.stop() }
    }

    fun takeInbox(): String {
        val feed = synchronized(pairServer) { pairServer.takeInbox() } ?: return ""
        return JSONObject()
            .put("url", feed.url)
            .put("label", feed.label)
            .toString()
    }

    fun setKeepAwake(on: Boolean) {
        runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Enumerate installed, launchable apps for the "Watch apps" picker.
     * Returns a JSON array [{pkg, label}] (LEANBACK_LAUNCHER first so TV apps
     * sort naturally; deduped; self excluded). Needs the <queries> MAIN/
     * LAUNCHER + LEANBACK_LAUNCHER block in the manifest on Android 11+.
     */
    @Suppress("DEPRECATION")
    fun listLaunchableApps(): String {
        return try {
            val pm = packageManager
            val seen = LinkedHashMap<String, String>()
            val intents = listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            )
            for (intent in intents) {
                val resolved = pm.queryIntentActivities(intent, 0)
                for (ri in resolved) {
                    val pkg = ri.activityInfo?.packageName ?: continue
                    if (pkg == packageName) continue
                    val label = ri.loadLabel(pm)?.toString()?.take(48) ?: pkg
                    seen.putIfAbsent(pkg, label)
                }
            }
            val arr = JSONArray()
            for ((pkg, label) in seen) {
                arr.put(JSONObject().put("pkg", pkg).put("label", label))
            }
            arr.toString()
        } catch (err: Exception) {
            "[]"
        }
    }

    /** Launch an installed app by package id (Leanback launch intent first). */
    fun openApp(packageName: String): Boolean {
        return try {
            var intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
                ?: packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(intent)
            true
        } catch (err: Exception) {
            false
        }
    }

    /** Current app version ("1.2.0") for the in-app Updates panel. */
    fun getVersion(): String = dev.corebuilds.line.BuildConfig.VERSION_NAME

    /** Download + hand off a newer APK to the system installer (async). */
    fun installUpdate(url: String): Boolean = UpdateManager.downloadAndInstall(this, url)

    /** Open a URL in whatever app handles it (usually the browser). */
    fun openUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (err: Exception) {
            false
        }
    }

    /** True when the OS has granted "display over other apps". */
    fun canDrawOverlays(): Boolean = android.provider.Settings.canDrawOverlays(this)

    /** Is the floating ticker window currently up? */
    fun overlayActive(): Boolean = OverlayService.running

    /**
     * Overlay platform status for the JS bridge. Returns one of:
     * - "supported"       — overlay works (phone, tablet, or Android TV with permission)
     * - "unsupported"     — Fire TV blocks SYSTEM_ALERT_WINDOW at the OS level
     * - "needs_permission" — Android TV, but the user hasn't granted overlay permission yet
     */
    fun overlayPlatform(): String {
        if (isFireTv()) return "unsupported"
        if (!android.provider.Settings.canDrawOverlays(this)) {
            if (isTelevision()) return "needs_permission"
            return "needs_permission" // phone/tablet also needs permission
        }
        return "supported"
    }

    /** Open the system overlay-permission screen for this app. */
    fun openOverlaySettings(): Boolean {
        return try {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (err: Exception) {
            false
        }
    }

    /**
     * Start the floating ticker. Works on phone, tablet, and Android TV
     * (some devices require the overlay permission to be granted first via
     * Settings or ADB). Returns false on Fire TV, which blocks
     * SYSTEM_ALERT_WINDOW at the OS level.
     */
    fun startOverlay(): Boolean {
        if (isFireTv()) return false
        if (!android.provider.Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return false
        }
        // Android 13+: ask for notifications so the "tap to stop" control shows.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        OverlayService.start(this)
        return true
    }

    /** Stop the floating ticker. */
    fun stopOverlay(): Boolean {
        OverlayService.stop(this)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBack()
    }

    /**
     * Back closes the settings drawer first, then backgrounds the app.
     * Uses the modern OnBackInvokedDispatcher on API 33+ (registered in
     * onCreate) and falls back to the deprecated callback below it.
     */
    private fun handleBack() {
        webView.evaluateJavascript(
            """
            (function(){
              var d = document.getElementById('drawer');
              if (d && !d.hidden) { d.hidden = true; return 'closed'; }
              return 'exit';
            })()
            """.trimIndent(),
        ) { result ->
            if (result.contains("exit")) {
                moveTaskToBack(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        stopPair()
        webView.removeJavascriptInterface("CoreLineNative")
        webView.destroy()
        super.onDestroy()
    }

    private fun configure(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = false
        }
    }

    private fun isTelevision(): Boolean {
        val pm = packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
            || isFireTv()
    }

    /**
     * Fire TV devices block SYSTEM_ALERT_WINDOW at the OS level — overlay
     * windows cannot be shown regardless of permission settings.
     */
    private fun isFireTv(): Boolean {
        return packageManager.hasSystemFeature("amazon.hardware.fire_tv")
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }
}
