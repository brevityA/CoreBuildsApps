package tv.corebuilds.iconpack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import java.io.File

/**
 * The system live wallpaper: plays the chosen motion loop behind the home
 * screen.
 *
 * Android has no direct-apply for live wallpapers — the picker *is* the apply
 * — so this service is what the system picker points at once the user has
 * chosen a loop in the preview screen. It is exported with
 * `android.permission.BIND_WALLPAPER`, so only the system can bind it; nothing
 * in the app starts it.
 *
 * Playback rules, all of them deliberate:
 *  - **Muted.** A wallpaper that made noise would be a defect, not a feature.
 *  - **Looping, and paused off-screen.** The engine stops drawing the moment
 *    the home screen is not the visible surface, which is what keeps a video
 *    wallpaper from costing battery behind a game or the settings UI.
 *  - **Still fallback.** Before the MP4 has been downloaded (or if it can no
 *    longer be read) the bundled thumb frame is drawn instead, so the home
 *    screen is never blank and never an error.
 *  - **Re-readable choice.** The chosen loop id lives in [Prefs] rather than in
 *    this engine, so re-picking a loop re-points wallpaper that is already
 *    active instead of requiring a re-apply.
 */
class LiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = LiveEngine()

    private inner class LiveEngine : Engine() {

        private var holder: SurfaceHolder? = null
        private var player: MediaPlayer? = null
        private var loop: Loop = LiveLoop.LOOPS[0]
        private var file: File? = null
        private var visible = false

        override fun onSurfaceCreated(incoming: SurfaceHolder) {
            holder = incoming
            loadChoice()
            attachFile(LiveLoopDownloader.cached(applicationContext, loop))
        }

        override fun onSurfaceChanged(
            incoming: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            holder = incoming
        }

        override fun onVisibilityChanged(shown: Boolean) {
            visible = shown
            // Re-read the choice on every visibility change: the preview's
            // "Set live" writes the preference and hands the user to the
            // picker, so the loop can change while this engine is alive.
            val previous = loop
            loadChoice()
            if (loop.id != previous.id) {
                stopPlayback()
                attachFile(LiveLoopDownloader.cached(applicationContext, loop))
                return
            }
            if (shown) startPlayback() else pausePlayback()
        }

        override fun onSurfaceRedrawNeeded(incoming: SurfaceHolder) {
            holder = incoming
            if (player?.isPlaying != true) drawStill()
        }

        override fun onSurfaceDestroyed(incoming: SurfaceHolder) {
            if (holder === incoming) holder = null
            stopPlayback()
        }

        override fun onDestroy() {
            stopPlayback()
            super.onDestroy()
        }

        // ---- choice / file ----------------------------------------------------

        private fun loadChoice() {
            loop = LiveLoop.byId(Prefs.liveLoopId(applicationContext))
        }

        /** Point the engine at [next], or fall back to the still frame. */
        private fun attachFile(next: File?) {
            file = next
            if (next == null) drawStill() else preparePlayback(next)
        }

        // ---- playback ---------------------------------------------------------

        private fun preparePlayback(target: File) {
            val surface = holder?.surface ?: return
            val mp = MediaPlayer()
            try {
                mp.setSurface(surface)
                mp.setDataSource(target.absolutePath)
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "loop playback error $what/$extra; showing the still frame")
                    stopPlayback()
                    drawStill()
                    true
                }
                mp.prepare()
                // Fill the screen and crop the overflow, as the still frame
                // does: a 16:9 loop stretched onto a portrait phone read as
                // warped rather than moving.
                runCatching {
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                }
                player = mp
                if (visible) mp.start()
            } catch (e: Exception) {
                Log.w(TAG, "could not start loop playback", e)
                runCatching { mp.release() }
                player = null
                drawStill()
            }
        }

        private fun startPlayback() {
            val existing = player
            if (existing == null) {
                file?.let { preparePlayback(it) } ?: drawStill()
                return
            }
            if (!existing.isPlaying) {
                runCatching { existing.setVolume(0f, 0f) }
                runCatching { existing.start() }
            }
        }

        private fun pausePlayback() {
            val existing = player ?: return
            if (existing.isPlaying) runCatching { existing.pause() }
        }

        private fun stopPlayback() {
            val existing = player ?: return
            player = null
            runCatching { existing.stop() }
            runCatching { existing.release() }
        }

        // ---- still frame ------------------------------------------------------

        /**
         * Draw the loop's bundled thumb frame, centre-cropped over the surface.
         *
         * The fallback path: the MP4 is not on the device yet, could not be
         * read, or the engine is being redrawn while playback is stopped. A
         * dark fill goes down first so a frame that fails to decode leaves a
         * dark home screen rather than whatever was last on the surface.
         */
        private fun drawStill() {
            val surfaceHolder = holder ?: return
            val frame = thumbFrame() ?: return
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas() ?: return
                canvas.drawColor(Color.BLACK)
                val scale = maxOf(
                    canvas.width.toFloat() / frame.width.coerceAtLeast(1),
                    canvas.height.toFloat() / frame.height.coerceAtLeast(1)
                )
                val w = frame.width * scale
                val h = frame.height * scale
                val left = (canvas.width - w) / 2f
                val top = (canvas.height - h) / 2f
                canvas.drawBitmap(frame, null, RectF(left, top, left + w, top + h), null)
            } catch (e: Exception) {
                Log.w(TAG, "could not draw the still frame", e)
            } finally {
                canvas?.let { runCatching { surfaceHolder.unlockCanvasAndPost(it) } }
            }
        }

        /** The loop's bundled thumb, or null if the asset is unreadable. */
        private fun thumbFrame(): Bitmap? = try {
            applicationContext.assets.open(loop.thumbAsset)
                .use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.w(TAG, "no bundled frame for ${loop.id}", e)
            null
        }
    }

    private companion object {
        const val TAG = "CoreBuilds/LiveWP"
    }
}
