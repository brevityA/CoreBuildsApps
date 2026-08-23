package dev.corebuilds.shift

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Remote content updater for Core Motion prequels.
 *
 * APK updates and wallpaper updates are intentionally separate: the app's
 * existing UpdateChecker installs a new APK, while this class refreshes a
 * small Overflight-compatible feed and lets the normal downloader fetch MP4s
 * on demand. The last good feed is cached so the app remains useful offline.
 */
object RemoteLiveCatalog {

    private const val TAG = "CoreShiftContent"
    const val PREQUEL_FEED_URL =
        "https://github.com/brevityA/CoreBuildsApps/releases/download/" +
            "motion-prequels/prequel-feed.json"
    private const val CONNECT_TIMEOUT_MS = 7_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_FEED_BYTES = 512 * 1024

    private val ALLOWED_FEED_HOSTS = setOf(
        "github.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )
    private val io = Executors.newSingleThreadExecutor()

    data class RefreshResult(
        val entries: List<LiveEntry>,
        val prequelCount: Int,
        val newEntries: Int,
        val networkSucceeded: Boolean,
        val fromCache: Boolean,
    )

    /** Last good prequel feed, if this device has seen one before. */
    fun cached(context: Context): List<LiveEntry> =
        LiveCatalog.loadCachedPrequels(context)

    /** Refresh the feed off the main thread and return the merged catalog. */
    fun refresh(
        context: Context,
        current: List<LiveEntry>,
        onResult: (RefreshResult) -> Unit,
    ) {
        val app = context.applicationContext
        io.execute {
            val cached = LiveCatalog.loadCachedPrequels(app)
            val fetched = runCatching { fetch() }.getOrNull()
            val remoteEntries = fetched?.first ?: cached
            if (fetched != null && remoteEntries.isNotEmpty()) {
                app.getSharedPreferences(LiveCatalog.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(LiveCatalog.PREFS_FEED, fetched.second)
                    .apply()
            }
            val merged = LiveCatalog.merge(current, remoteEntries)
            val result = RefreshResult(
                entries = merged,
                prequelCount = remoteEntries.size,
                newEntries = (merged.size - current.size).coerceAtLeast(0),
                networkSucceeded = fetched != null,
                fromCache = fetched == null && remoteEntries.isNotEmpty(),
            )
            android.os.Handler(app.mainLooper).post { onResult(result) }
        }
    }

    /** Returns parsed entries and the original JSON for the local cache. */
    private fun fetch(): Pair<List<LiveEntry>, String> {
        val target = URL(PREQUEL_FEED_URL)
        require(target.protocol == "https") { "feed must use HTTPS" }
        require(target.host in ALLOWED_FEED_HOSTS) { "feed host is not allowlisted" }

        val connection = (target.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "CoreShift-Content")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("feed HTTP ${connection.responseCode}")
            }
            val declared = connection.contentLengthLong
            require(declared <= MAX_FEED_BYTES) { "feed is too large" }
            val body = connection.inputStream.use { input ->
                val bytes = input.readBounded(MAX_FEED_BYTES)
                bytes.toString(Charsets.UTF_8)
            }
            val parsed = LiveCatalog.parse(body, bundledThumbs = false, mediaAvailable = true)
            require(parsed.isNotEmpty()) { "feed is empty or invalid" }
            parsed to body
        } finally {
            try {
                connection.disconnect()
            } catch (_: Exception) {
                Log.w(TAG, "feed disconnect failed")
            }
        }
    }

    private fun java.io.InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "feed is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
