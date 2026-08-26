package dev.corebuilds.shift

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

        val exo = ExoPlayer.Builder(this).build().also { player = it }
        exo.volume = 0f
        exo.repeatMode = Player.REPEAT_MODE_ALL
        exo.shuffleModeEnabled = items.size > 1
        exo.setMediaItems(items)
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
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
        val view = findViewById<PlayerView>(R.id.dream_player)
        view.player = null
        player?.release()
        player = null
        super.onDetachedFromWindow()
    }
}
