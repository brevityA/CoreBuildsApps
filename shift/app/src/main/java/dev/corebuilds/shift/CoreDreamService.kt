package dev.corebuilds.shift

import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Android TV screensaver. Silent, shuffled Core Motion loops.
 *
 * Not a live-wallpaper service (TV has none). This is the system Dream that
 * Settings → Screensaver can pick. Projectivy / Monet wallpaper is unchanged.
 * No MediaSession — a dream must not steal the TV's now-playing slot.
 */
class CoreDreamService : DreamService() {

    private var player: ExoPlayer? = null
    private var consecutiveErrors = 0
    private val handler = Handler(Looper.getMainLooper())
    private val bufferTimeout = Runnable {
        val exo = player ?: return@Runnable
        if (exo.playbackState == Player.STATE_BUFFERING) {
            if (exo.hasNextMediaItem()) {
                consecutiveErrors++
                exo.seekToNextMediaItem()
                exo.prepare()
                exo.play()
            } else {
                exo.stop()
                findViewById<TextView>(R.id.dream_status)?.let {
                    it.visibility = View.VISIBLE
                    it.text = getString(R.string.dream_empty)
                }
            }
        }
    }
    private companion object {
        const val BUFFER_TIMEOUT_MS = 30_000L
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        setContentView(R.layout.dream_motion)

        val status = findViewById<TextView>(R.id.dream_status)
        val view = findViewById<PlayerView>(R.id.dream_player)
        view.useController = false

        val items = DreamPlaylist.mediaItems(this)
        if (items.isEmpty()) {
            status.visibility = View.VISIBLE
            status.text = getString(R.string.dream_empty)
            return
        }

        consecutiveErrors = 0
        val exo = ExoPlayer.Builder(this).build().also { player = it }
        exo.volume = 0f
        exo.repeatMode = Player.REPEAT_MODE_ALL
        exo.shuffleModeEnabled = items.size > 1
        exo.setMediaItems(items)
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    consecutiveErrors = 0
                    handler.removeCallbacks(bufferTimeout)
                } else if (playbackState == Player.STATE_BUFFERING) {
                    handler.removeCallbacks(bufferTimeout)
                    handler.postDelayed(bufferTimeout, BUFFER_TIMEOUT_MS)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                consecutiveErrors++
                if (consecutiveErrors >= exo.mediaItemCount) {
                    exo.stop()
                    status.visibility = View.VISIBLE
                    status.text = getString(R.string.dream_empty)
                    return
                }
                if (exo.hasNextMediaItem()) {
                    exo.seekToNextMediaItem()
                    exo.prepare()
                    exo.play()
                } else {
                    status.visibility = View.VISIBLE
                    status.text = getString(R.string.dream_empty)
                }
            }
        })
        view.player = exo
        exo.prepare()
        exo.playWhenReady = true
        status.visibility = View.GONE
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(bufferTimeout)
        val view = findViewById<PlayerView>(R.id.dream_player)
        view.player = null
        player?.release()
        player = null
        super.onDetachedFromWindow()
    }
}
