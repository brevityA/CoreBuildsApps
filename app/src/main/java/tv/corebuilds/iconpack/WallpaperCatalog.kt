package tv.corebuilds.iconpack

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Metadata for one wallpaper in the Core Builds collection.
 *
 * The catalog is driven by `assets/manifest/wallpapers.json` (a copy of the
 * repo's `Wallpapers/manifest.json`). Thumbs are bundled so the browser grid
 * is instant offline; the full 4K image is downloaded on demand from [url]
 * (raw GitHub) and cached by [WallpaperDownloader].
 */
data class Wallpaper(
    /** Display name, e.g. "41 Core Mark · Signature". */
    val name: String,
    /** Series folder id, e.g. "series-4-core-mark". Drives the filter chips. */
    val series: String,
    /** Full-resolution image URL (raw GitHub, 4K PNG). */
    val url: String,
    /** Thumbnail URL (raw GitHub). Used only as a fallback if the bundled
     *  thumb is ever missing. */
    val thumbUrl: String,
    /** Declared resolution, e.g. "3840x2160". */
    val resolution: String,
    /** Bundled thumb asset path under assets/ (with .jpg). */
    val thumbAsset: String
) {
    /** Short title without the leading number/series prefix. */
    val title: String
        get() = name.substringAfter("· ", name).trim()

    /** Stable cache filename derived from the URL (always .png). */
    val cacheName: String
        get() = url.substringAfterLast('/')
}

object WallpaperCatalog {

    private const val MANIFEST = "manifest/wallpapers.json"
    const val THUMB_DIR = "wallpapers_thumbs"

    /**
     * Load the bundled manifest. Returns an empty list if the asset is missing
     * or malformed rather than crashing — the browser simply shows nothing.
     */
    fun load(context: Context): List<Wallpaper> {
        val json = try {
            context.assets.open(MANIFEST).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e("CoreBuilds/WP", "manifest missing", e)
            return emptyList()
        }
        val root = JSONObject(json)
        val arr = root.optJSONArray("wallpapers") ?: return emptyList()
        val out = ArrayList<Wallpaper>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val url = o.optString("url")
            if (url.isBlank()) continue
            out += Wallpaper(
                name = o.optString("name"),
                series = o.optString("series"),
                url = url,
                thumbUrl = o.optString("thumb"),
                resolution = o.optString("resolution"),
                thumbAsset = "$THUMB_DIR/${url.substringAfterLast('/').substringBeforeLast('.')}.jpg"
            )
        }
        return out
    }

    /** Human label for a series id, e.g. "series-4-core-mark" -> "Core Mark". */
    fun seriesLabel(series: String): String =
        series.removePrefix("series-")
            .substringAfter('-')
            .split('-')
            .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }
}
