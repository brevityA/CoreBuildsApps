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
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Phone floating ticker — the chyron drawn as a translucent, non-focusable,
 * touch-through overlay window above every other app.
 *
 * It reuses the SAME Core Line web app as the full screen (loaded from
 * https://coreline.local with ?native=1&overlay=1), so it shares the parser,
 * the /api/proxy data path, and — because both WebViews are on the same origin
 * — the same localStorage settings as the main app. The overlay page strips
 * everything but the chyron via the [data-overlay] CSS.
 *
 * Android does NOT allow this on TV (no SYSTEM_ALERT_WINDOW on Android TV);
 * the service is phone/tablet only and the main app refuses to start it on a
 * leanback/fire_tv device.
 */
class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var webView: WebView? = null

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
        if (windowManager != null && webView != null) return // already up

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val height = (56 * resources.displayMetrics.density).toInt().coerceAtLeast(96)
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
            gravity = Gravity.TOP or Gravity.FILL_HORIZONTAL
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
        }
        configure(wv.settings)
        wv.loadUrl("https://${LineWebClient.HOST}/index.html?native=1&overlay=1")

        try {
            wm.addView(wv, params)
        } catch (err: Exception) {
            stopSelf() // permission was revoked mid-flight
            return
        }
        webView = wv
        windowManager = wm
        running = true
    }

    override fun onDestroy() {
        running = false
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = false
        }
    }

    companion object {
        const val ACTION_STOP = "dev.corebuilds.line.OVERLAY_STOP"
        const val OVERLAY_NOTIFICATION_ID = 42

        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
