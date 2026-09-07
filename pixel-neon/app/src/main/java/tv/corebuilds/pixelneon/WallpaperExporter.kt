package tv.corebuilds.pixelneon

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Copies a set of wallpapers from the download cache into shared
 * `Pictures/CoreBuilds/` so launcher wallpaper rotation (Monet, etc.) can see
 * them. The engine is deliberately boring:
 *
 *  - Original bytes are copied — no bitmap decode, no re-encode (keeps heap
 *    tiny on 1–2 GB TV boxes and preserves the 4K PNGs losslessly).
 *  - Sequential: one wallpaper at a time, each downloaded (if missing) then
 *    copied. A single-thread executor means predictable disk/network use.
 *  - Idempotent: a same-named, same-size file already in MediaStore is skipped.
 *  - Honest accounting: [Event.Done] reports saved and failed separately so
 *    the UI can offer "Retry failed".
 *
 * No foreground service — this only runs while the user is watching the
 * progress screen and explicitly started it. Matches the app's "nothing runs
 * in the background without a user action" stance.
 */
object WallpaperExporter {

    private const val TAG = "PixelNeon/Export"
    private const val HEADROOM_BYTES = 10L * 1024 * 1024
    private const val ESTIMATED_WALLPAPER_BYTES = 5L * 1024 * 1024
    private const val DOWNLOAD_TIMEOUT_SECONDS = 90L
    private const val CANCEL_POLL_MILLIS = 250L
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    sealed class Event {
        /** [index] is 0-based among [total]; [currentName] is the wallpaper being saved. */
        data class Progress(val index: Int, val total: Int, val currentName: String) : Event()
        data class Done(
            val saved: List<String>,
            val skipped: List<String>,
            val failed: List<Pair<String, String>>
        ) : Event()
        object NeedsStoragePermission : Event()
        data class Failed(val reason: String) : Event()
    }

    fun interface Listener {
        fun onEvent(event: Event)
    }

    /**
     * Export [wallpapers]. Delivers events on the main thread. Must be called
     * after storage permission is granted on API ≤ 28 (the caller is expected
     * to use [WallpaperSetter.storagePermission] + a runtime request). If
     * access is lost before or during the run, the listener receives
     * [Event.NeedsStoragePermission] followed by a terminal [Event.Done] with
     * every unprocessed item reported as failed.
     *
     * Returns an [AtomicBoolean] that the caller can set to true to cancel the
     * background operation (for example, when its activity is destroyed).
     */
    fun export(
        context: Context,
        wallpapers: List<Wallpaper>,
        listener: Listener
    ): AtomicBoolean {
        val cancelled = AtomicBoolean(false)
        val app = context.applicationContext

        if (wallpapers.isEmpty()) {
            main.post { listener.onEvent(Event.Done(emptyList(), emptyList(), emptyList())) }
            return cancelled
        }
        if (!WallpaperSetter.hasStoragePermission(app)) {
            val permission = WallpaperSetter.storagePermission()
            if (permission != null) {
                val failed = wallpapers.map { it.cacheName to PERMISSION_FAILURE }
                main.post {
                    if (!cancelled.get()) {
                        listener.onEvent(Event.NeedsStoragePermission)
                        listener.onEvent(Event.Done(emptyList(), emptyList(), failed))
                    }
                }
                return cancelled
            }
        }

        io.execute task@{
            try {
                ensureSpace(app, wallpapers)
                val saved = mutableListOf<String>()
                val skipped = mutableListOf<String>()
                val failed = mutableListOf<Pair<String, String>>()

                wallpapers.forEachIndexed { index, wallpaper ->
                    if (cancelled.get()) return@task
                    main.post {
                        if (!cancelled.get()) {
                            listener.onEvent(
                                Event.Progress(index, wallpapers.size, wallpaper.title)
                            )
                        }
                    }
                    try {
                        val file = ensureDownloaded(app, wallpaper, cancelled)
                        if (cancelled.get()) return@task
                        val cacheName = wallpaper.cacheName
                        if (WallpaperSetter.alreadyExported(app, cacheName, file.length())) {
                            skipped += cacheName
                            return@forEachIndexed
                        }
                        when (val result = WallpaperSetter.copyFileToPictures(app, file, cacheName)) {
                            is WallpaperSetter.Result.SavedToGallery -> saved += cacheName
                            is WallpaperSetter.Result.NeedsPermission -> {
                                val remaining = wallpapers.drop(index)
                                    .map { it.cacheName to PERMISSION_FAILURE }
                                val done = Event.Done(
                                    saved.toList(),
                                    skipped.toList(),
                                    failed.toList() + remaining
                                )
                                main.post {
                                    if (!cancelled.get()) {
                                        listener.onEvent(Event.NeedsStoragePermission)
                                        listener.onEvent(done)
                                    }
                                }
                                return@task
                            }
                            is WallpaperSetter.Result.Failed ->
                                failed += cacheName to result.reason
                            else -> failed += cacheName to "unexpected result"
                        }
                    } catch (e: Exception) {
                        if (cancelled.get()) return@task
                        Log.w(TAG, "export failed for ${wallpaper.cacheName}", e)
                        failed += wallpaper.cacheName to
                            (e.message ?: e.javaClass.simpleName)
                    }
                }

                val done = Event.Done(saved.toList(), skipped.toList(), failed.toList())
                main.post { if (!cancelled.get()) listener.onEvent(done) }
            } catch (e: Exception) {
                if (cancelled.get()) return@task
                Log.e(TAG, "export aborted", e)
                main.post {
                    if (!cancelled.get()) {
                        listener.onEvent(Event.Failed(e.message ?: e.javaClass.simpleName))
                    }
                }
            }
        }
        return cancelled
    }

    /** Download [wallpaper] if it is not cached; blocks until ready or throws. */
    private fun ensureDownloaded(
        context: Context,
        wallpaper: Wallpaper,
        cancelled: AtomicBoolean
    ): File {
        if (cancelled.get()) throw InterruptedIOException("Export cancelled")
        WallpaperDownloader.cached(context, wallpaper)?.let { return it }

        val latch = CountDownLatch(1)
        var result: File? = null
        var error: String? = null
        WallpaperDownloader.fetch(context, wallpaper) { event ->
            when (event) {
                is WallpaperDownloader.Event.Ready -> {
                    result = event.file
                    latch.countDown()
                }
                is WallpaperDownloader.Event.Failed -> {
                    error = event.reason
                    latch.countDown()
                }
                else -> Unit
            }
        }

        val deadline = System.nanoTime() +
            TimeUnit.SECONDS.toNanos(DOWNLOAD_TIMEOUT_SECONDS)
        while (true) {
            if (cancelled.get()) throw InterruptedIOException("Export cancelled")
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0L) throw java.io.IOException("Download timed out")
            val slice = minOf(
                remaining,
                TimeUnit.MILLISECONDS.toNanos(CANCEL_POLL_MILLIS)
            )
            if (latch.await(slice, TimeUnit.NANOSECONDS)) break
        }

        if (cancelled.get()) throw InterruptedIOException("Export cancelled")
        result?.let { return it }
        throw java.io.IOException("Download failed: ${error ?: "unknown"}")
    }

    /**
     * Best-effort space checks for both filesystems involved in an export.
     * The entire batch accumulates in shared Pictures, while the internal
     * download cache is bounded by [WallpaperDownloader.MAX_CACHE_FILES].
     */
    private fun ensureSpace(context: Context, wallpapers: List<Wallpaper>) {
        val destinationBytes =
            wallpapers.size * ESTIMATED_WALLPAPER_BYTES + HEADROOM_BYTES
        val sharedVolume = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: throw java.io.IOException("Shared Pictures storage is unavailable")
        ensureAvailable(sharedVolume, destinationBytes, "shared Pictures")

        val cacheFiles = minOf(wallpapers.size, WallpaperDownloader.MAX_CACHE_FILES)
        val cacheBytes = cacheFiles * ESTIMATED_WALLPAPER_BYTES + HEADROOM_BYTES
        ensureAvailable(WallpaperDownloader.cacheDir(context), cacheBytes, "download cache")
    }

    private fun ensureAvailable(path: File, bytesNeeded: Long, destination: String) {
        val available = StatFs(path.absolutePath).availableBytes
        if (available < bytesNeeded) {
            throw java.io.IOException(
                "Not enough $destination space — need ~${bytesNeeded / (1024 * 1024)} MB free"
            )
        }
    }

    private const val PERMISSION_FAILURE = "storage permission required"
}
