package dev.corebuilds.shift

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import java.util.concurrent.Executors

/**
 * Full-screen TV preview using Media3/ExoPlayer.
 *
 * The local cached copy gives reliable range/format behaviour, while Media3
 * supplies familiar TV transport controls, MediaSession integration, looping,
 * audio-off playback and a clean error callback.
 */
class PreviewActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var prepared = false
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val quality = intent.getStringExtra(EXTRA_QUALITY).orEmpty()

        playerView = findViewById(R.id.preview_player)
        status = findViewById(R.id.preview_status)
        findViewById<TextView>(R.id.preview_title).text = title
        findViewById<TextView>(R.id.preview_quality).apply {
            text = quality
            visibility = if (quality.isBlank()) View.GONE else View.VISIBLE
        }
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
                val exo = ExoPlayer.Builder(this).build()
                player = exo
                mediaSession = MediaSession.Builder(this, exo).build()
                exo.repeatMode = Player.REPEAT_MODE_ALL
                exo.volume = 0f
                exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            prepared = true
                            status.visibility = View.GONE
                            exo.playWhenReady = true
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        prepared = false
                        status.visibility = View.VISIBLE
                        status.text = getString(
                            R.string.preview_error_fmt,
                            error.errorCodeName,
                        )
                    }
                })
                playerView.player = exo
                exo.prepare()
            }
        }
    }

    override fun onPause() {
        player?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (prepared) player?.play()
    }

    override fun onDestroy() {
        io.shutdownNow()
        mediaSession?.release()
        mediaSession = null
        if (::playerView.isInitialized) playerView.player = null
        player?.release()
        player = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "preview_url"
        const val EXTRA_TITLE = "preview_title"
        const val EXTRA_QUALITY = "preview_quality"
    }
}
