package tv.corebuilds.iconpack

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Downloads a motion loop MP4 and caches it in `filesDir/live`.
 *
 * Deliberately not [WallpaperDownloader]: that path validates every byte as a
 * decodable 16:9 bitmap, which would reject an MP4 as corrupt. This one keeps
 * the same discipline instead — https-only, the GitHub host allowlist, one
 * worker for the heavy path, nothing running without a user action, in-flight
 * coalescing — and swaps the bitmap check for the one thing that can be said
 * about a video without decoding it: it is big enough and it starts with an
 * MP4 `ftyp` box.
 *
 * The cache lives in `filesDir`, not `cacheDir`, because the wallpaper engine
 * plays from it long after the download: the system may reclaim the cache
 * directory at any time, and a live wallpaper that silently stopped playing
 * would be worse than one that never started.
 */
object LiveLoopDownloader {

    private const val TAG = "CoreBuilds/Live"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val DIR = "live"
    private const val MIN_BYTES = 64L * 1024L          // a real loop is 1-8 MB
    private const val MAX_BYTES = 32L * 1024L * 1024L
    private const val MAX_CACHE_FILES = 6              // three loops plus churn

    private val ALLOWED_HOSTS = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
    )

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** cacheName -> callbacks waiting on the same in-flight fetch. */
    private val inFlight = ConcurrentHashMap<String, MutableList<Callback>>()

    sealed class Event {
        data class Progress(val received: Long, val total: Long) : Event()
        data class Ready(val file: File) : Event()
        data class Failed(val reason: String) : Event()
    }

    fun interface Callback {
        fun onEvent(event: Event)
    }

    /** The cached MP4 for [loop], or null if it has not been downloaded yet. */
    fun cached(context: Context, loop: Loop): File? =
        cachedFile(context.applicationContext, loop.fileName)

    /** Where [loop]'s MP4 is (or would be) cached. */
    fun fileFor(context: Context, loop: Loop): File =
        File(cacheDir(context.applicationContext), loop.fileName)

    fun cacheDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR).apply { mkdirs() }

    /** Fetch [loop]'s MP4. See [fetchUrl]. */
    fun fetch(context: Context, loop: Loop, callback: Callback) {
        fetchUrl(context, loop.url, loop.fileName, callback)
    }

    /**
     * Fetch the MP4 at [url], caching it as [cacheName]. Delivers [Event]s on
     * the main thread. A second caller for the same [cacheName] joins the
     * first's fetch rather than writing the same file from two threads.
     */
    fun fetchUrl(
        context: Context,
        url: String,
        cacheName: String,
        callback: Callback
    ) {
        val app = context.applicationContext
        require(cacheName.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid loop cache name" }
        cachedFile(app, cacheName)?.let {
            main.post { callback.onEvent(Event.Ready(it)) }
            return
        }

        val requestKey = "$url\u0000$cacheName"
        synchronized(inFlight) {
            val waiters = inFlight[requestKey]
            if (waiters != null) {
                waiters += callback
                return
            }
            inFlight[requestKey] = mutableListOf(callback)
        }

        io.execute {
            val result = runCatching { download(app, url, cacheName, requestKey) }
            runCatching { trimCache(app) }

            val waiters = synchronized(inFlight) { inFlight.remove(requestKey) } ?: emptyList()
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
        return if (f.exists() && f.length() >= MIN_BYTES) {
            f.setLastModified(System.currentTimeMillis())
            f
        } else null
    }

    private fun download(
        context: Context,
        url: String,
        cacheName: String,
        requestKey: String
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
            setRequestProperty("Accept", "video/mp4,*/*")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code fetching motion loop")
            }
            @Suppress("DEPRECATION")
            val total = conn.contentLength.toLong().coerceAtLeast(0L)
            if (total > MAX_BYTES) {
                throw IllegalStateException("Loop exceeds ${MAX_BYTES / 1024 / 1024}MB")
            }
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        received += n
                        if (received > MAX_BYTES) {
                            throw IllegalStateException("Loop exceeds ${MAX_BYTES / 1024 / 1024}MB")
                        }
                        out.write(buf, 0, n)
                        val snapshot = synchronized(inFlight) {
                            inFlight[requestKey]?.toList()
                        } ?: continue
                        snapshot.forEach { w ->
                            main.post { w.onEvent(Event.Progress(received, total)) }
                        }
                    }
                    out.flush()
                }
            }
            if (tmp.length() < MIN_BYTES) {
                tmp.delete()
                throw IllegalStateException("Downloaded loop was too small")
            }
            if (!looksLikeMp4(tmp)) {
                tmp.delete()
                throw IllegalStateException("Downloaded file is not an MP4")
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            return dest
        } finally {
            conn.disconnect()
        }
    }

    /**
     * True when [file] opens with an MP4 `ftyp` box — the cheapest possible
     * "this is a video" check. It catches the failure that matters here (a
     * proxy or a 404 page served with a 200, which the engine would then try
     * to play and fail on) without pulling in a media parser.
     */
    private fun looksLikeMp4(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(12)
            if (raf.length() < 12) return false
            raf.readFully(head)
            String(head, 4, 4, Charsets.US_ASCII) == "ftyp"
        }
    } catch (e: Exception) {
        Log.w(TAG, "could not read loop header", e)
        false
    }

    /** Keep the most recently fetched files up to [MAX_CACHE_FILES]. */
    private fun trimCache(context: Context) {
        val files = cacheDir(context).listFiles()
            ?.filter { !it.name.endsWith(".part") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_CACHE_FILES).forEach { runCatching { it.delete() } }
    }
}
