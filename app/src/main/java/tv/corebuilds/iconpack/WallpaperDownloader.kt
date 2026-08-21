package tv.corebuilds.iconpack

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Downloads a full-resolution wallpaper from the repo's raw GitHub URL and
 * caches it in internal storage.
 *
 * Same shape and discipline as [UpdateInstaller]: no image/HTTP library,
 * https-only, GitHub host allowlist, one worker thread, nothing runs in the
 * background without a user action. The 4K PNGs are 2–3 MB each and are kept
 * in a small LRU cache so repeat previews are instant.
 */
object WallpaperDownloader {

    private const val TAG = "CoreBuilds/WP"
    private const val TIMEOUT_MS = 30_000
    private const val MAX_CACHE_FILES = 12       // ~30 MB ceiling at ~2.5 MB/file
    private const val MIN_BYTES = 20_000L        // a real wallpaper is far larger

    private val ALLOWED_HOSTS = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
    )

    private val io = Executors.newFixedThreadPool(2)

    sealed class Event {
        data class Progress(val received: Long, val total: Long) : Event()
        data class Ready(val file: File) : Event()
        data class Failed(val reason: String) : Event()
    }

    fun interface Callback {
        fun onEvent(event: Event)
    }

    /** Return the cached file if present, else null. */
    fun cached(context: Context, wallpaper: Wallpaper): File? {
        val f = File(cacheDir(context), wallpaper.cacheName)
        return if (f.exists() && f.length() >= MIN_BYTES) f else null
    }

    fun cacheDir(context: Context): File =
        File(context.cacheDir, "wallpapers").apply { mkdirs() }

    fun fetch(context: Context, wallpaper: Wallpaper, callback: Callback) {
        val app = context.applicationContext
        val main = android.os.Handler(app.mainLooper)
        cached(app, wallpaper)?.let {
            main.post { callback.onEvent(Event.Ready(it)) }
            return
        }
        io.execute {
            try {
                val file = download(app, wallpaper.url) { rec, tot ->
                    main.post { callback.onEvent(Event.Progress(rec, tot)) }
                }
                trimCache(app)
                main.post { callback.onEvent(Event.Ready(file)) }
            } catch (e: Exception) {
                Log.w(TAG, "download failed", e)
                main.post {
                    callback.onEvent(Event.Failed(e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    private fun download(
        context: Context,
        url: String,
        onProgress: (Long, Long) -> Unit
    ): File {
        val parsed = URL(url)
        if (parsed.protocol != "https" || parsed.host !in ALLOWED_HOSTS) {
            throw IllegalStateException("Refusing non-GitHub URL: $url")
        }
        val dest = File(cacheDir(context), parsed.file.substringAfterLast('/'))
        if (dest.exists()) dest.delete()

        var conn: HttpURLConnection? = null
        (parsed.openConnection() as HttpURLConnection).let { c ->
            c.instanceFollowRedirects = true
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.requestMethod = "GET"
            c.setRequestProperty("Accept", "image/png,image/jpeg,*/*")
            conn = c
        }
        val code = conn!!.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code fetching wallpaper")
        }
        val total = conn!!.contentLengthLong.coerceAtLeast(0L)
        conn!!.inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var received = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    out.write(buf, 0, n)
                    received += n
                    onProgress(received, total)
                }
                out.flush()
            }
        }
        if (dest.length() < MIN_BYTES) {
            dest.delete()
            throw IllegalStateException("Downloaded wallpaper was too small")
        }
        return dest
    }

    /** Keep the most recently modified files up to [MAX_CACHE_FILES]. */
    private fun trimCache(context: Context) {
        val dir = cacheDir(context)
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_CACHE_FILES).forEach { runCatching { it.delete() } }
    }
}
