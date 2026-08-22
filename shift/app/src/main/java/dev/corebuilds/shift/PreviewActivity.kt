package dev.corebuilds.shift

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/**
 * Full-screen looping preview of one live wallpaper.
 *
 * Downloads the 1080p MP4 to the internal cache, then plays the *local* file
 * with [VideoView]. Playing the cached copy avoids two failure modes of
 * streaming straight from `raw.githubusercontent.com`: an unexpected
 * content-type and missing range-request support, both of which can make
 * MediaPlayer silently fail.
 */
class PreviewActivity : AppCompatActivity() {

    private lateinit var video: VideoView
    private lateinit var status: TextView

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        video = findViewById(R.id.preview_video)
        status = findViewById(R.id.preview_status)
        findViewById<TextView>(R.id.preview_title).text = title

        status.visibility = View.VISIBLE
        status.text = getString(R.string.preview_loading)

        val cacheName = url.substringAfterLast('/')
        io.execute {
            val file = LiveDownloader.fetch(this, url, cacheName)
            main.post {
                if (isFinishing || isDestroyed) return@post
                if (file == null) {
                    status.text = getString(R.string.preview_failed)
                    return@post
                }
                status.visibility = View.GONE
                video.setVideoURI(Uri.fromFile(file))
                video.setOnPreparedListener { mp -> mp.isLooping = true }
                video.setOnErrorListener { _, _, _ -> status.text = getString(R.string.preview_failed); true }
                video.start()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::video.isInitialized) video.pause()
    }

    override fun onDestroy() {
        io.shutdown()
        if (::video.isInitialized) video.stopPlayback()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "preview_url"
        const val EXTRA_TITLE = "preview_title"
    }
}
