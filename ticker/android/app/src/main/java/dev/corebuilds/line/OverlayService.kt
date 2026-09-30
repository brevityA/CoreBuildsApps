package dev.corebuilds.line

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.webkit.JavascriptInterface
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * The always-on-top surfaces: the floating ticker (a WebView crawl), the
 * scoreboard bug (a WebView score box at the top, see [ScoreBugWindow]) and
 * the VPN status dot (a native view). One foreground service owns all three,
 * because Android wants one ongoing notification per app's overlay work.
 *
 * The ticker loads overlay.html, a crawl strip, not the full board. The strip
 * is on the same origin, so it can read the main app's localStorage (position,
 * feeds, cached slate) without building the grid. Default edge is the bottom.
 *
 * The dot is independent: it can run with the ticker off (that is the normal
 * case — you want to know your tunnel is up while someone else's app is on
 * screen) and the ticker can run with the dot off. Each surface is rebuilt
 * from [OverlayPrefs] when the service is restarted with a null intent, which
 * is also what [BootReceiver] restores after a reboot.
 *
 * Supported on phone, tablet, and Android TV (Google TV, NVIDIA Shield, etc.)
 * where SYSTEM_ALERT_WINDOW is granted. Fire TV blocks overlay windows at the
 * OS level — the main app refuses to start either surface on Fire TV devices.
 */
class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var webView: WebView? = null
    private var edge: String = "bottom"

    private var scoreWindow: ScoreBugWindow? = null
    private var scoreConfig: ScoreBugConfig = ScoreBugConfig.DEFAULT

    private var dotWindow: VpnDotWindow? = null
    private var dotConfig: DotConfig = DotConfig.DEFAULT
    private var watcher: VpnState.Watcher? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * The dot also re-reads its state on a timer. Network callbacks are the
     * fast path; this is the floor under them, so a vendor ROM that drops a
     * callback cannot leave a dot frozen in the wrong colour — the failure
     * mode every review of the competing dots complains about.
     */
    private val dotRefresh = object : Runnable {
        override fun run() {
            val dot = dotWindow ?: return
            dot.apply(VpnState.read(this@OverlayService).state)
            handler.postDelayed(this, DOT_REFRESH_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService gives us seconds, not minutes, so the
        // notification goes up before any work — even when the command that
        // follows turns out to be "stop". A refused start is refused here, not
        // at the call site: on Android 12+ the platform throws
        // ForegroundServiceStartNotAllowedException from startForeground(), so
        // a caller's try/catch cannot see it (boot restore is the case that
        // matters). Stopping immediately is the documented remedy and beats
        // crashing at boot.
        if (!startAsForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent?.action
        val dotPayload = intent?.getStringExtra(EXTRA_DOT_CONFIG)
        val scorePayload = intent?.getStringExtra(EXTRA_SCORE_CONFIG)
        var position = intent?.getStringExtra(EXTRA_POSITION)
        if (position == null) position = OverlayPrefs(this).tickerPosition
        if (position == "top" || position == "bottom") edge = position

        when (action) {
            ACTION_STOP -> {
                stopTicker()
                stopScores()
                stopDot()
            }
            ACTION_STOP_TICKER -> stopTicker()
            ACTION_START_SCORES -> {
                ScoreBugConfig.fromJson(scorePayload)?.let { scoreConfig = it }
                showScores()
            }
            ACTION_STOP_SCORES -> stopScores()
            ACTION_STOP_DOT -> stopDot()
            ACTION_START_DOT -> {
                DotConfig.fromJson(dotPayload)?.let { dotConfig = it }
                showDot()
            }
            ACTION_START_TICKER -> showTicker()
            null -> {
                // A null action is the system restarting us (START_STICKY).
                // Rebuild only what survives a restart — the native dot, see
                // [OverlayPrefs] — and never put the crawl up unasked: with
                // three surfaces, "something else is already running" is the
                // common case, and it used to fall through to showTicker().
                if (intent == null && !dotRunning) {
                    OverlayPrefs(this).readDotConfig()?.let {
                        dotConfig = it
                        showDot()
                    }
                } else if (intent != null) {
                    showTicker() // an explicit start from an older caller
                }
            }
            else -> showTicker()
        }

        if (!tickerRunning && !scoresRunning && !dotRunning) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateNotification()
        return START_STICKY
    }

    private fun asPendingIntent(action: String): PendingIntent = PendingIntent.getService(
        this, 1,
        Intent(this, OverlayService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The surfaces on screen, in the order the notification names them. */
    private fun runningNames(): List<String> = listOfNotNull(
        getString(R.string.overlay_name_ticker).takeIf { tickerRunning },
        getString(R.string.overlay_name_scores).takeIf { scoresRunning },
        getString(R.string.overlay_name_dot).takeIf { dotRunning },
    )

    private fun notificationText(): String {
        val names = runningNames()
        return when {
            names.size > 1 -> getString(R.string.overlay_text_many, names.joinToString(", "))
            dotRunning -> getString(R.string.overlay_text_dot)
            scoresRunning -> getString(R.string.overlay_text_scores)
            else -> getString(R.string.overlay_text_ticker)
        }
    }

    private fun notificationTitle(): String = when {
        runningNames().size > 1 -> getString(R.string.overlay_title_both)
        dotRunning -> getString(R.string.overlay_title_dot)
        scoresRunning -> getString(R.string.overlay_title_scores)
        else -> getString(R.string.overlay_title_ticker)
    }

    private fun buildNotification(): Notification {
        val stopIntent = asPendingIntent(ACTION_STOP)
        val channelId = "coreline.overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.overlay_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val note = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setSmallIcon(dev.corebuilds.line.R.drawable.ic_notification)
            .setContentTitle(notificationTitle())
            .setContentText(notificationText())
            .setOngoing(true)
            .setContentIntent(stopIntent)
            .addAction(0, getString(R.string.overlay_stop), stopIntent)
            .build()
        return note
    }

    /** False when the platform refused a background start; the caller stops. */
    private fun startAsForeground(): Boolean {
        return try {
            startForeground(OVERLAY_NOTIFICATION_ID, buildNotification())
            true
        } catch (err: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException extends
            // IllegalStateException, and is an API 31 class this minSdk 24
            // build must not name.
            false
        } catch (err: Exception) {
            // Notification permission revoked, or a vendor ROM's own refusal.
            false
        }
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(OVERLAY_NOTIFICATION_ID, buildNotification())
        } catch (err: Exception) {
            // Notification permission revoked mid-flight; the surfaces still work.
        }
    }

    // ---- Ticker -----------------------------------------------------------

    private fun showTicker() {
        if (webView != null) {
            applyEdge(edge)
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        // Tall enough for the 2rem crawl used at 10-foot. The window is only
        // the bar — not a clear WebView over the rest of the screen.
        val isTv = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
            || packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
        val baseDp = if (isTv) 112 else 64
        val height = (baseDp * resources.displayMetrics.density).toInt().coerceAtLeast(96)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = gravityFor(edge)
            x = 0
            y = 0
        }

        val wv = WebView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isFocusable = false
            isFocusableInTouchMode = false
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            webViewClient = LineWebClient(this@OverlayService)
            webChromeClient = WebChromeClient()
            addJavascriptInterface(OverlayEdgeBridge(this@OverlayService), "CoreLineNative")
        }
        configure(wv.settings)
        val tvParam = if (isTv) "&tv=1" else ""
        wv.loadUrl("https://${LineWebClient.HOST}/overlay.html?native=1$tvParam")

        try {
            wm.addView(wv, params)
        } catch (err: Exception) {
            wv.destroy() // clean up before stopping so no WebView leaks
            return // permission was revoked mid-flight
        }
        webView = wv
        windowManager = wm
        tickerRunning = true
        instance = this
        OverlayPrefs(this).let {
            it.ticker = true
            it.tickerPosition = edge
        }
    }

    private fun stopTicker() {
        val wv = webView ?: return
        webView = null
        tickerRunning = false
        try {
            windowManager?.removeView(wv)
        } catch (err: Exception) {
            /* already detached */
        }
        wv.removeJavascriptInterface("CoreLineNative")
        wv.destroy()
        windowManager = null
        OverlayPrefs(this).ticker = false
    }

    // ---- Scoreboard bug ---------------------------------------------------

    private fun showScores() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            // Permission revoked since the drawer checked it: drop the surface.
            scoreWindow?.hide()
            scoreWindow = null
            scoresRunning = false
            return
        }
        val window = scoreWindow ?: ScoreBugWindow(this).also { scoreWindow = it }
        window.configure(scoreConfig)
        if (!window.show()) {
            stopScores()
            return
        }
        scoresRunning = true
        instance = this
    }

    private fun stopScores() {
        scoreWindow?.hide()
        scoreWindow = null
        scoresRunning = false
    }

    /** Live move or dim from the settings drawer, without reloading the page. */
    fun applyScoreConfig(next: ScoreBugConfig) {
        val run = Runnable {
            scoreConfig = next.sanitized()
            scoreWindow?.configure(scoreConfig)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else handler.post(run)
    }

    // ---- VPN dot ----------------------------------------------------------

    private fun showDot() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            // Permission was revoked (or the boot restore ran on a device where
            // it never existed). Drop the surface but keep the stored setting:
            // forgetting it here would silently lose the viewer's choice, and
            // re-granting the permission should bring the dot back.
            handler.removeCallbacks(dotRefresh)
            watcher?.stop()
            watcher = null
            dotWindow?.hide()
            dotWindow = null
            dotRunning = false
            return
        }
        val window = dotWindow ?: VpnDotWindow(this).also { dotWindow = it }
        window.configure(dotConfig)
        if (!window.show()) {
            stopDot()
            return
        }
        window.apply(VpnState.read(this).state)
        dotRunning = true
        instance = this
        OverlayPrefs(this).writeDotConfig(dotConfig)

        watcher?.stop()
        // Connectivity callbacks arrive on a binder thread, and a View may only
        // be touched from the UI thread (View.invalidate() is explicitly
        // UI-thread-only), so the update hops through the main handler.
        val watch = VpnState.Watcher(this) { snapshot ->
            handler.post { dotWindow?.apply(snapshot.state) }
        }
        watcher = if (watch.start()) watch else null

        handler.removeCallbacks(dotRefresh)
        handler.postDelayed(dotRefresh, DOT_REFRESH_MS)
    }

    private fun stopDot() {
        handler.removeCallbacks(dotRefresh)
        watcher?.stop()
        watcher = null
        dotWindow?.hide()
        dotWindow = null
        dotRunning = false
        OverlayPrefs(this).clearDot()
    }

    /** Live re-shape from the settings drawer, without dropping the window. */
    fun applyDotConfig(next: DotConfig) {
        dotConfig = next
        val window = dotWindow ?: return
        window.configure(next)
        OverlayPrefs(this).writeDotConfig(next)
    }

    // ---- Shared -----------------------------------------------------------

    fun applyEdge(next: String) {
        val edgeName = if (next == "top") "top" else "bottom"
        val run = Runnable {
            edge = edgeName
            val view = webView ?: return@Runnable
            val wm = windowManager ?: return@Runnable
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return@Runnable
            val gravity = gravityFor(edgeName)
            if (params.gravity == gravity) return@Runnable
            params.gravity = gravity
            params.y = 0
            try {
                wm.updateViewLayout(view, params)
            } catch (_: Exception) {
                /* window already gone */
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else handler.post(run)
    }

    private fun gravityFor(edgeName: String): Int {
        val vertical = if (edgeName == "top") Gravity.TOP else Gravity.BOTTOM
        return vertical or Gravity.FILL_HORIZONTAL
    }

    override fun onDestroy() {
        tickerRunning = false
        scoresRunning = false
        dotRunning = false
        scoreWindow?.hide()
        scoreWindow = null
        if (instance === this) instance = null
        handler.removeCallbacks(dotRefresh)
        watcher?.stop()
        watcher = null
        dotWindow?.hide()
        dotWindow = null
        try {
            webView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
            /* already detached */
        }
        webView?.removeJavascriptInterface("CoreLineNative")
        webView?.destroy()
        webView = null
        windowManager = null
        super.onDestroy()
    }

    private fun configure(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
    }

    companion object {
        const val ACTION_START_TICKER = "dev.corebuilds.line.OVERLAY_START"
        const val ACTION_STOP_TICKER = "dev.corebuilds.line.OVERLAY_STOP_TICKER"
        const val ACTION_START_DOT = "dev.corebuilds.line.VPN_DOT_START"
        const val ACTION_STOP_DOT = "dev.corebuilds.line.VPN_DOT_STOP"
        const val ACTION_START_SCORES = "dev.corebuilds.line.SCOREBUG_START"
        const val ACTION_STOP_SCORES = "dev.corebuilds.line.SCOREBUG_STOP"
        const val EXTRA_SCORE_CONFIG = "dev.corebuilds.line.SCOREBUG_CONFIG"

        /** Stops every surface. Kept for the notification's action and older callers. */
        const val ACTION_STOP = "dev.corebuilds.line.OVERLAY_STOP"
        const val EXTRA_POSITION = "dev.corebuilds.line.OVERLAY_POSITION"
        const val EXTRA_DOT_CONFIG = "dev.corebuilds.line.VPN_DOT_CONFIG"
        const val OVERLAY_NOTIFICATION_ID = 42

        private const val DOT_REFRESH_MS = 15_000L

        /** The ticker strip is up. What `overlayActive()` on the bridge reports. */
        @Volatile
        var tickerRunning = false
            private set

        /** The scoreboard bug is up. */
        @Volatile
        var scoresRunning = false
            private set

        /** The VPN status dot is up. */
        @Volatile
        var dotRunning = false
            private set

        @Volatile
        private var instance: OverlayService? = null

        fun startTicker(context: Context, position: String? = null) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_START_TICKER)
            if (position == "top" || position == "bottom") {
                intent.putExtra(EXTRA_POSITION, position)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTicker(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_STOP_TICKER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Brings the dot up. The caller has already checked
         * Settings.canDrawOverlays(); this is the one path that also runs
         * from a boot broadcast, where no activity exists to ask.
         */
        fun startDot(context: Context, config: DotConfig) {
            val intent = Intent(context, OverlayService::class.java)
                .setAction(ACTION_START_DOT)
                .putExtra(EXTRA_DOT_CONFIG, config.toJson())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopDot(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_STOP_DOT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Bring the scoreboard bug up. The caller has already checked canDrawOverlays(). */
        fun startScores(context: Context, config: ScoreBugConfig) {
            val intent = Intent(context, OverlayService::class.java)
                .setAction(ACTION_START_SCORES)
                .putExtra(EXTRA_SCORE_CONFIG, config.toJson())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopScores(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_STOP_SCORES)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Move or dim a running bug without reloading it. */
        fun applyScoreConfig(config: ScoreBugConfig) {
            instance?.applyScoreConfig(config)
        }

        /** Re-shape a running dot (corner, opacity, blink) without a restart. */
        fun applyDotConfig(config: DotConfig) {
            instance?.applyDotConfig(config)
        }

        fun setEdge(edge: String) {
            instance?.applyEdge(edge)
        }
    }
}

/** Overlay WebView bridge. Reads nothing itself — the strip passes the saved edge. */
class OverlayEdgeBridge(private val service: OverlayService) {
    @JavascriptInterface
    fun setOverlayEdge(edge: String) {
        service.applyEdge(edge)
    }
}
