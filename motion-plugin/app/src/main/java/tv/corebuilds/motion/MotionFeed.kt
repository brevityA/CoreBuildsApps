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
    private const val TIMEOUT_MS = 20_000

    const val DEFAULT_FEED_URL =
        "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/live-feed.json"

    private val ALLOWED_HOSTS = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
    )

    /** Fetch and parse [url]. Returns an empty list on any failure (Projectivy
     *  keeps the current wallpaper rather than crashing). */
    fun load(url: String): List<Wallpaper> {
        val resolved = url.ifBlank { DEFAULT_FEED_URL }
        val host = try {
            URL(resolved).host
        } catch (_: Exception) {
            return emptyList()
        }
        if (!resolved.startsWith("https://") || host !in ALLOWED_HOSTS) {
            Log.w(TAG, "refusing non-https or non-allowlisted feed url: $resolved")
            return emptyList()
        }

        return try {
            val conn = URL(resolved).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode !in 200..299) {
                    Log.w(TAG, "feed HTTP ${conn.responseCode}")
                    return emptyList()
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "feed load failed", e)
            emptyList()
        }
    }

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
