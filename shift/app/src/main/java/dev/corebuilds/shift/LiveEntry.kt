package dev.corebuilds.shift

import android.content.Context
import org.json.JSONArray

/**
 * One live wallpaper in the Core Motion catalog.
 *
 * The bundled feed is the offline fallback. Remote prequel entries use the
 * same shape, but their poster is loaded into the app cache on demand.
 */
data class LiveEntry(
    /** Display title, e.g. "25 Orbitals". */
    val title: String,
    /** Location/series label from the feed, e.g. "Core Motion · Prequels". */
    val location: String,
    /** Author credit, e.g. "Core Builds". */
    val author: String,
    /** 1080p MP4 stream URL (the default playback/download tier). */
    val url1080p: String,
    /** Optional 4K MP4 URL, or null. */
    val url4k: String?,
    /** Bundled poster-thumb asset path under assets/, or null for remote media. */
    val thumbAsset: String?,
    /** Remote poster URL used when [thumbAsset] is unavailable. */
    val thumbUrl: String? = null,
) {
    /** Stable filename derived from the 1080p URL (always .mp4). */
    val cacheName: String get() = url1080p.substringAfterLast('/')

    /** Human label shown in the list. */
    val specLabel: String
        get() = if (location.contains("Prequel", ignoreCase = true)) {
            if (url4k != null) "4K · 20s loop · Prequel" else "1080p · 20s loop · Prequel"
        } else if (url4k != null) {
            "4K · 20s loop"
        } else {
            "1080p · 20s loop"
        }
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
        return parse(json, bundledThumbs = true)
    }

    /** Parse a previously cached remote prequel feed. */
    fun loadCachedPrequels(context: Context): List<LiveEntry> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREFS_FEED, null) ?: return emptyList()
        return parse(json, bundledThumbs = false)
    }

    /** Parse an Overflight-compatible feed. */
    internal fun parse(json: String, bundledThumbs: Boolean): List<LiveEntry> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val url1080p = o.optString("url_1080p")
                // Core Shift needs a downloadable default tier. The Projectivy
                // plugin can consume url_4k-only feeds, but this app is a TV
                // browser/downloader and must not create a dead row.
                if (url1080p.isBlank()) null else {
                    val thumbUrl = o.optString("url_img").ifBlank { null }
                    LiveEntry(
                        title = o.optString("title", "Live ${i + 1}"),
                        location = o.optString("location", "Core Motion"),
                        author = o.optString("author", "Core Builds"),
                        url1080p = url1080p,
                        url4k = o.optString("url_4k").ifBlank { null },
                        thumbAsset = if (bundledThumbs && thumbUrl != null) {
                            "$THUMB_DIR/${thumbUrl.substringAfterLast('/')}"
                        } else {
                            null
                        },
                        thumbUrl = if (bundledThumbs) null else thumbUrl,
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Append remote entries while keeping the bundled catalog first. */
    fun merge(base: List<LiveEntry>, additions: List<LiveEntry>): List<LiveEntry> {
        val byKey = LinkedHashMap<String, LiveEntry>(base.size + additions.size)
        for (entry in base + additions) {
            val key = entry.url1080p.ifBlank { entry.title }
            // Remote metadata wins over an older cached copy while the
            // original catalog order remains stable.
            byKey[key] = entry
        }
        return byKey.values.toList()
    }

    internal const val PREFS = "core_motion_content"
    internal const val PREFS_FEED = "prequel_feed_json"
}
