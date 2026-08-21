package tv.corebuilds.iconpack

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Downloads a full-resolution wallpaper from the repo's raw GitHub URL and
 * caches it in internal storage.
 *
 * Same discipline as [UpdateInstaller]: no image/HTTP library, https-only,
 * GitHub host allowlist, one worker for the heavy path, nothing runs in the
 * background without a user action. The 4K PNGs are 2–3 MB each and are kept
 * in a small LRU cache so repeat previews are instant.
 *
 * A concurrent map coalesces in-flight requests for the same URL: a second
 * caller joins the first's fetch rather than writing the same cache file from
 * two threads (which previously risked a truncated or corrupt file).
 */
object WallpaperDownloader {

    private const val TAG = "CoreBuilds/WP"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_CACHE_FILES = 12       // ~30 MB ceiling at ~2.5 MB/file
    private const val MIN_BYTES = 20_000L        // a real wallpaper is far larger

    private val ALLOWED_HOSTS = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
    )

    // Single worker for the export path (sequential, predictable disk use).
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** url -> list of callbacks waiting on the same in-flight fetch. */
    private val inFlight = ConcurrentHashMap<String, MutableList<Callback>>()

    sealed class Event {
        data class Progress(val received: Long, val total: Long) : Event()
        data class Ready(val file: File) : Event()
        data class Failed(val reason: String) : Event()
    }

    fun interface Callback {
        fun onEvent(event: Event)
    }

    /** Return the cached file if present, else null. */
    fun cached(context: Context, wallpaper: Wallpaper): File? =
        cachedFile(context, wallpaper.cacheName)

    fun cacheDir(context: Context): File =
        File(context.cacheDir, "wallpapers").apply { mkdirs() }

    /** Fetch by [Wallpaper]. See [fetchUrl]. */
    fun fetch(context: Context, wallpaper: Wallpaper, callback: Callback) {
        fetchUrl(context, wallpaper.url, wallpaper.cacheName, callback)
    }

    /**
     * Fetch the image at [url], caching it as [cacheName]. Delivers [Event]s on
     * the main thread. If a fetch for the same [cacheName] is already running,
     * [callback] is added to its waiter list instead of starting a second
     * download.
     */
    fun fetchUrl(
        context: Context,
        url: String,
        cacheName: String,
        callback: Callback
    ) {
        val app = context.applicationContext
        cachedFile(app, cacheName)?.let {
            main.post { callback.onEvent(Event.Ready(it)) }
            return
        }

        // Coalesce: join an existing in-flight fetch for this cache entry.
        synchronized(inFlight) {
            val waiters = inFlight[cacheName]
            if (waiters != null) {
                waiters += callback
                return
            }
            inFlight[cacheName] = mutableListOf(callback)
        }

        io.execute {
            val result = runCatching { download(app, url, cacheName) }
            // Trim after the write, regardless of outcome.
            runCatching { trimCache(app) }

            val waiters = synchronized(inFlight) { inFlight.remove(cacheName) } ?: emptyList()
            result.onSuccess { file ->
                waiters.forEach { w -> main.post { w.onEvent(Event.Ready(file)) } }
            }.onFailure { e ->
                Log.w(TAG, "download failed", e)
                val reason = e.message ?: e.javaClass.simpleName
                waiters.forEach { w -> main.post { w.onEvent(Event.Failed(reason)) } }
            }
        }
    }

    private fun cachedFile(context: Context, cacheName: String): File? {
        val f = File(cacheDir(context), cacheName)
        return if (f.exists() && f.length() >= MIN_BYTES) f else null
    }

    private fun download(
        context: Context,
        url: String,
        cacheName: String
    ): File {
        val parsed = URL(url)
        if (parsed.protocol != "https" || parsed.host !in ALLOWED_HOSTS) {
            throw IllegalStateException("Refusing non-GitHub URL: $url")
        }
        val dest = File(cacheDir(context), cacheName)
        val tmp = File(dest.parentFile, "$cacheName.part")
        if (tmp.exists()) tmp.delete()

        val conn = (parsed.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "image/png,image/jpeg,*/*")
        }
        conn.use { c ->
            val code = c.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code fetching wallpaper")
            }
            val total = c.contentLengthLong.coerceAtLeast(0L)
            c.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        received += n
                        // Progress is broadcast to all waiters; coalescing means
                        // there is only one fetch per URL, so this is cheap.
                        val waiters = inFlight[cacheName] ?: continue
                        waiters.forEach { w ->
                            main.post { w.onEvent(Event.Progress(received, total)) }
                        }
                    }
                    out.flush()
                }
            }
            if (tmp.length() < MIN_BYTES) {
                tmp.delete()
                throw IllegalStateException("Downloaded wallpaper was too small")
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                // Fall back to copy if rename across filesystems fails (shouldn't
                // happen within cacheDir, but be defensive).
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            return dest
        }
    }

    /** Keep the most recently modified files up to [MAX_CACHE_FILES]. */
    private fun trimCache(context: Context) {
        val dir = cacheDir(context)
        val files = dir.listFiles()
            ?.filter { !it.name.endsWith(".part") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_CACHE_FILES).forEach { runCatching { it.delete() } }
    }
}
