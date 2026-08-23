package tv.corebuilds.motion

import android.util.Log
import org.json.JSONArray
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads the live-wallpaper feed (Overflight-compatible JSON) and maps it to
 * [Wallpaper] objects Projectivy can render.
 *
 * Synchronous — Projectivy calls `getWallpapers()` off the main thread, so a
 * blocking fetch here is correct (the stock providers do the same). HTTPS-only
 * and GitHub-host allowlisted, same discipline as the rest of the repo.
 */
object MotionFeed {

    private const val TAG = "CoreMotion"

    /** Projectivy calls getWallpapers() on a binder thread and waits. Spocky's
     *  own guidance is to be frugal here, so keep the worst case well under
     *  ~15s rather than the 40s a 20s connect + 20s read allows. A provider
     *  that stalls looks broken and Projectivy falls back to its cache. */
    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS = 8_000

    /** In-memory feed cache. Projectivy already caches results for
     *  itemsCacheDurationMillis, but it re-binds the service freely and other
     *  events can trigger extra calls; this makes those free. */
    private const val CACHE_TTL_MS = 15 * 60 * 1000L

    private var cachedUrl: String? = null
    private var cachedAt: Long = 0L
    private var cachedWallpapers: List<Wallpaper> = emptyList()

    const val DEFAULT_FEED_URL =
        "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/live-feed.json"

    private val ALLOWED_HOSTS = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
    )

    /** Fetch and parse [url]. Returns an empty list on any failure (Projectivy
     *  keeps the current wallpaper rather than crashing). Results are cached
     *  for [CACHE_TTL_MS] so repeat binds don't re-hit the network. */
    @Synchronized
    fun load(url: String): List<Wallpaper> {
        val resolved = url.ifBlank { DEFAULT_FEED_URL }

        val now = System.currentTimeMillis()
        if (resolved == cachedUrl &&
            cachedWallpapers.isNotEmpty() &&
            now - cachedAt < CACHE_TTL_MS
        ) {
            return cachedWallpapers
        }

        val host = try {
            URL(resolved).host
        } catch (_: Exception) {
            return emptyList()
        }
        if (!resolved.startsWith("https://") || host !in ALLOWED_HOSTS) {
            Log.w(TAG, "refusing non-https or non-allowlisted feed url: $resolved")
            return emptyList()
        }

        val fetched = try {
            val conn = URL(resolved).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode !in 200..299) {
                    Log.w(TAG, "feed HTTP ${conn.responseCode}")
                    return staleOrEmpty(resolved)
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "feed load failed", e)
            return staleOrEmpty(resolved)
        }

        if (fetched.isNotEmpty()) {
            cachedUrl = resolved
            cachedAt = now
            cachedWallpapers = fetched
        }
        Log.i(TAG, "feed loaded: ${fetched.size} wallpapers from $resolved")
        return fetched
    }

    /** On a transient failure, serving the last good batch beats serving
     *  nothing — an empty list makes the plugin look dead in Projectivy. */
    private fun staleOrEmpty(url: String): List<Wallpaper> =
        if (url == cachedUrl) cachedWallpapers else emptyList()

    private fun parse(json: String): List<Wallpaper> {
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val title = o.optString("title")
                    val author = o.optString("author", "Core Builds")
                    val location = o.optString("location")
                    val url4k = o.optString("url_4k")
                    val url1080p = o.optString("url_1080p")
                    val urlImg = o.optString("url_img")

                    val uri = when {
                        url4k.isNotBlank() -> url4k
                        url1080p.isNotBlank() -> url1080p
                        else -> urlImg
                    }
                    if (uri.isBlank()) continue

                    val type = if (url1080p.isNotBlank() || url4k.isNotBlank()) {
                        WallpaperType.VIDEO
                    } else {
                        WallpaperType.IMAGE
                    }

                    add(Wallpaper(uri, type, title = title, source = location, author = author))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "feed parse failed", e)
            emptyList()
        }
    }
}
