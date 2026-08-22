package dev.corebuilds.shift

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen looping preview of one live wallpaper. Streams the 1080p MP4 and
 * loops it; this is where you actually *see* the motion before committing to a
 * download or the plugin.
 */
class PreviewActivity : AppCompatActivity() {

    private lateinit var video: VideoView
    private var title: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        video = findViewById(R.id.preview_video)
        findViewById<TextView>(R.id.preview_title).text = title

        video.setOnPreparedListener { mp: MediaPlayer -> mp.isLooping = true }
        video.setOnErrorListener { _, _, _ -> finish(); true }
        video.setVideoURI(Uri.parse(url))
        video.setOnCompletionListener { video.start() }
        video.start()
    }

    override fun onPause() {
        super.onPause()
        if (::video.isInitialized) video.pause()
    }

    override fun onDestroy() {
        if (::video.isInitialized) video.stopPlayback()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "preview_url"
        const val EXTRA_TITLE = "preview_title"
    }
}
