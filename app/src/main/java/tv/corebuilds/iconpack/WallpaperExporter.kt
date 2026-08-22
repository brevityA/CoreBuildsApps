package tv.corebuilds.iconpack

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

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

    private const val TAG = "CoreBuilds/Export"
    private const val HEADROOM_BYTES = 10L * 1024 * 1024   // keep 10 MB free
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
     * to use [WallpaperSetter.storagePermission] + a runtime request); if it
     * is missing, [Event.NeedsStoragePermission] is returned synchronously via
     * the listener.
     */
    fun export(context: Context, wallpapers: List<Wallpaper>, listener: Listener) {
        val app = context.applicationContext

        if (!WallpaperSetter.hasStoragePermission(app)) {
            val perm = WallpaperSetter.storagePermission()
            if (perm != null) {
                main.post { listener.onEvent(Event.NeedsStoragePermission) }
                return
            }
        }
        if (wallpapers.isEmpty()) {
            main.post { listener.onEvent(Event.Done(emptyList(), emptyList(), emptyList())) }
            return
        }

        io.execute {
            runCatching {
                ensureSpace(app, wallpapers)
                val saved = mutableListOf<String>()
                val skipped = mutableListOf<String>()
                val failed = mutableListOf<Pair<String, String>>()

                wallpapers.forEachIndexed { i, wp ->
                    val name = wp.title
                    main.post { listener.onEvent(Event.Progress(i, wallpapers.size, name)) }
                    try {
                        val file = ensureDownloaded(app, wp)
                        val cacheName = wp.cacheName
                        if (WallpaperSetter.alreadyExported(app, cacheName, file.length())) {
                            skipped += cacheName
                            return@forEachIndexed
                        }
                        when (val r = WallpaperSetter.copyFileToPictures(app, file, cacheName)) {
                            is WallpaperSetter.Result.SavedToGallery -> saved += cacheName
                            is WallpaperSetter.Result.NeedsPermission -> {
                                // Surface once and stop; caller re-requests.
                                main.post { listener.onEvent(Event.NeedsStoragePermission) }
                                return@execute
                            }
                            is WallpaperSetter.Result.Failed -> failed += (cacheName to r.reason)
                            else -> failed += (cacheName to "unexpected result")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "export failed for ${wp.cacheName}", e)
                        failed += (wp.cacheName to (e.message ?: e.javaClass.simpleName))
                    }
                }
                main.post { listener.onEvent(Event.Done(saved, skipped, failed)) }
            }.onFailure { e ->
                Log.e(TAG, "export aborted", e)
                main.post {
                    listener.onEvent(Event.Failed(e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    /** Download [wp] if it isn't cached; blocks until ready or throws. */
    private fun ensureDownloaded(context: Context, wp: Wallpaper): File {
        WallpaperDownloader.cached(context, wp)?.let { return it }
        val latch = java.util.concurrent.CountDownLatch(1)
        var result: File? = null
        var error: String? = null
        WallpaperDownloader.fetch(context, wp) { event ->
            when (event) {
                is WallpaperDownloader.Event.Ready -> { result = event.file; latch.countDown() }
                is WallpaperDownloader.Event.Failed -> { error = event.reason; latch.countDown() }
                else -> { /* progress: nothing to do */ }
            }
        }
        if (!latch.await(90, java.util.concurrent.TimeUnit.SECONDS)) {
            throw java.io.IOException("Download timed out")
        }
        result?.let { return it }
        throw java.io.IOException("Download failed: ${error ?: "unknown"}")
    }

    /**
     * Best-effort space check against the cache + export target. We don't know
     * exact on-wire sizes without a HEAD per file (costly), so we use a
     * conservative per-file ceiling of 5 MB — real 4K PNGs are 2–3 MB.
     */
    private fun ensureSpace(context: Context, wallpapers: List<Wallpaper>) {
        val bytesNeeded = wallpapers.size * 5L * 1024 * 1024 + HEADROOM_BYTES
        val stat = StatFs(context.cacheDir.absolutePath)
        val available = stat.availableBytes
        if (available < bytesNeeded) {
            throw java.io.IOException(
                "Not enough space — need ~${bytesNeeded / (1024 * 1024)} MB free"
            )
        }
    }
}
