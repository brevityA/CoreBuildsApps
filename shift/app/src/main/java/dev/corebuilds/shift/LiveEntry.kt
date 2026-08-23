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
    /** Procedural scene id, or null for ordinary MP4 entries. */
    val scene: Int? = null,
    /** Accent used by the instant on-device procedural preview. */
    val accent: Int = 0xFF00E5FF.toInt(),
    /** False for the bundled procedural preview seed until an MP4 is published. */
    val mediaAvailable: Boolean = true,
) {
    /** Stable filename derived from the 1080p URL (always .mp4). */
    val cacheName: String get() = url1080p.substringAfterLast('/')

    val isPrequel: Boolean
        get() = scene != null || location.contains("Prequel", ignoreCase = true)

    /** Human label shown in the list. */
    val specLabel: String
        get() = when {
            isPrequel && !mediaAvailable -> "Procedural preview · MP4 publishing"
            isPrequel && url4k != null -> "4K · 20s loop · Prequel"
            isPrequel -> "1080p · 20s loop · Prequel"
            url4k != null -> "4K · 20s loop"
            else -> "1080p · 20s loop"
        }
}

object LiveCatalog {

    private const val MANIFEST = "manifest/live-feed.json"
    private const val PREQUEL_SEEDS = "manifest/prequels.json"
    private const val THUMB_DIR = "live_thumbs"

    /** Parse the bundled feed. Empty on any error rather than crashing. */
    fun load(context: Context): List<LiveEntry> {
        val json = try {
            context.assets.open(MANIFEST).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return emptyList()
        }
        return parse(json, bundledThumbs = true, mediaAvailable = true)
    }

    /**
     * Bundled metadata for all 16 prequels. These rows are useful immediately:
     * they provide the procedural preview even before the release workflow has
     * published the production MP4 files.
     */
    fun loadPrequelSeeds(context: Context): List<LiveEntry> {
        val json = try {
            context.assets.open(PREQUEL_SEEDS).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return emptyList()
        }
        return parse(json, bundledThumbs = false, mediaAvailable = false)
    }

    /** Parse a previously cached remote prequel feed. */
    fun loadCachedPrequels(context: Context): List<LiveEntry> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREFS_FEED, null) ?: return emptyList()
        return parse(json, bundledThumbs = false, mediaAvailable = true)
    }

    /** Parse an Overflight-compatible feed. */
    internal fun parse(
        json: String,
        bundledThumbs: Boolean,
        mediaAvailable: Boolean,
    ): List<LiveEntry> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val url1080p = o.optString("url_1080p").ifBlank { null }
                val url4k = o.optString("url_4k").ifBlank { null }
                val playback = url1080p ?: url4k
                if (playback == null) null else {
                    val thumbUrl = o.optString("url_img").ifBlank { null }
                    val scene = if (o.has("scene") && !o.isNull("scene")) {
                        o.optInt("scene", -1).takeIf { it >= 0 }
                    } else {
                        null
                    }
                    LiveEntry(
                        title = o.optString("title", "Live ${i + 1}"),
                        location = o.optString("location", "Core Motion"),
                        author = o.optString("author", "Core Builds"),
                        url1080p = playback,
                        url4k = url4k,
                        thumbAsset = if (bundledThumbs && thumbUrl != null) {
                            "$THUMB_DIR/${thumbUrl.substringAfterLast('/')}"
                        } else {
                            null
                        },
                        thumbUrl = if (bundledThumbs) null else thumbUrl,
                        scene = scene,
                        accent = parseAccent(o.optString("accent")),
                        mediaAvailable = mediaAvailable,
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
            byKey[key] = entry
        }
        return byKey.values.toList()
    }

    private fun parseAccent(value: String): Int {
        return runCatching {
            android.graphics.Color.parseColor(value)
        }.getOrDefault(0xFF00E5FF.toInt())
    }

    internal const val PREFS = "core_motion_content"
    internal const val PREFS_FEED = "prequel_feed_json"
}
