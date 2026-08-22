package dev.corebuilds.shift

import android.content.Context
import org.json.JSONArray

/**
 * One live wallpaper in the Core Builds Motion set, parsed from the bundled
 * Overflight-compatible feed.
 */
data class LiveEntry(
    /** Display title, e.g. "Spiral Cyan". */
    val title: String,
    /** Location/series label from the feed, e.g. "Core Motion". */
    val location: String,
    /** Author credit, e.g. "Core Builds". */
    val author: String,
    /** 1080p MP4 stream URL (the default playback/download tier). */
    val url1080p: String,
    /** Optional 4K MP4 URL, or null. */
    val url4k: String?,
    /** Bundled poster-thumb asset path under assets/, or null when the feed
     *  entry has no `url_img`. */
    val thumbAsset: String?,
) {
    /** Stable filename derived from the 1080p URL (always .mp4). */
    val cacheName: String get() = url1080p.substringAfterLast('/')

    /** Human label shown in the list, e.g. "1080p · 20s loop". */
    val specLabel: String get() = if (url4k != null) "4K · 20s loop" else "1080p · 20s loop"
}

object LiveCatalog {

    private const val MANIFEST = "manifest/live-feed.json"
    private const val THUMB_DIR = "live_thumbs"

    /** Parse the bundled feed. Empty on any error rather than crashing. */
    fun load(context: Context): List<LiveEntry> {
        val json = try {
            context.assets.open(MANIFEST).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return emptyList()
        }
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val url1080p = o.optString("url_1080p")
                if (url1080p.isBlank()) null else {
                    val thumbUrl = o.optString("url_img")
                    val thumbAsset = if (thumbUrl.isBlank()) {
                        null
                    } else {
                        "$THUMB_DIR/${thumbUrl.substringAfterLast('/')}"
                    }
                    LiveEntry(
                        title = o.optString("title", "Live ${i + 1}"),
                        location = o.optString("location"),
                        author = o.optString("author", "Core Builds"),
                        url1080p = url1080p,
                        url4k = o.optString("url_4k").ifBlank { null },
                        thumbAsset = thumbAsset,
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
