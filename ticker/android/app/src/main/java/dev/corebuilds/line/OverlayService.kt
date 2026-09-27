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
 * Floating ticker — the chyron drawn as a translucent, non-focusable,
 * touch-through overlay window above every other app.
 *
 * It loads overlay.html, a crawl strip, not the full board. The strip is on
 * the same origin, so it can read the main app's localStorage (position,
 * feeds, cached slate) without building the grid. Default edge is the bottom.
 *
 * Supported on phone, tablet, and Android TV (Google TV, NVIDIA Shield, etc.)
 * where SYSTEM_ALERT_WINDOW is granted. Fire TV blocks overlay windows at the
 * OS level — the main app refuses to start the service on Fire TV devices.
 */
class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var webView: WebView? = null
    private var edge: String = "bottom"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val requested = intent?.getStringExtra(EXTRA_POSITION)
        if (requested == "top" || requested == "bottom") edge = requested
        showOverlay()
        return START_STICKY
    }

    private fun startAsForeground() {
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channelId = "coreline.overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(channelId, "Floating ticker", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val note = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setSmallIcon(dev.corebuilds.line.R.drawable.ic_notification)
            .setContentTitle("Core Line ticker")
            .setContentText("Running over your apps — tap to stop")
            .setOngoing(true)
            .setContentIntent(stopIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
        startForeground(OVERLAY_NOTIFICATION_ID, note)
    }

    private fun showOverlay() {
        if (windowManager != null && webView != null) {
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
            stopSelf() // permission was revoked mid-flight
            return
        }
        webView = wv
        windowManager = wm
        running = true
        instance = this
    }

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
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else Handler(Looper.getMainLooper()).post(run)
    }

    private fun gravityFor(edgeName: String): Int {
        val vertical = if (edgeName == "top") Gravity.TOP else Gravity.BOTTOM
        return vertical or Gravity.FILL_HORIZONTAL
    }

    override fun onDestroy() {
        running = false
        if (instance === this) instance = null
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
        const val ACTION_STOP = "dev.corebuilds.line.OVERLAY_STOP"
        const val EXTRA_POSITION = "dev.corebuilds.line.OVERLAY_POSITION"
        const val OVERLAY_NOTIFICATION_ID = 42

        @Volatile
        var running = false
            private set

        @Volatile
        private var instance: OverlayService? = null

        fun start(context: Context, position: String? = null) {
            val intent = Intent(context, OverlayService::class.java)
            if (position == "top" || position == "bottom") {
                intent.putExtra(EXTRA_POSITION, position)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun setEdge(edge: String) {
            instance?.applyEdge(edge)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
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
