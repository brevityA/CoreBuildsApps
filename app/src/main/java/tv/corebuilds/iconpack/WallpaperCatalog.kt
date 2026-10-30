package tv.corebuilds.iconpack

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import org.json.JSONObject
import java.util.Locale

/**
 * Metadata for one wallpaper in the Core Builds collection.
 *
 * The catalog is driven by `assets/manifest/wallpapers.json` (a copy of the
 * repo's `Wallpapers/manifest.json`). Thumbs are bundled so the browser grid
 * is instant offline; the full 4K image is downloaded on demand from [url]
 * (raw GitHub) and cached by [WallpaperDownloader].
 *
 * A handful of entries are motion loops ([LiveLoop]) rather than stills: they
 * carry [isLive], which keeps them out of the stills exporter (video has no
 * place in the Pictures rotation folder) and routes their preview to the live
 * branch instead.
 *
 * Parcelable so the browser can hand a selection to [ExportProgressActivity].
 */
data class Wallpaper(
    /** Display name, e.g. "41 Core Mark · Signature". */
    val name: String,
    /** Series folder id, e.g. "series-6-circuit-core". Drives the filter chips. */
    val series: String,
    /** Full-resolution image URL (raw GitHub, 4K PNG). */
    val url: String,
    /** Thumbnail URL (raw GitHub). Used only as a fallback if the bundled
     *  thumb is ever missing. */
    val thumbUrl: String,
    /** Declared resolution, e.g. "3840x2160". */
    val resolution: String,
    /** Bundled thumb asset path under assets/ (with .jpg). */
    val thumbAsset: String,
    /** True for a [LiveLoop] motion loop: [url] is an MP4, not a still. */
    val isLive: Boolean = false,
    /**
     * Pack version this wall was added or last re-rendered in, e.g. "2.4.0",
     * or "" for walls that predate the field. The browser's New chip shows
     * the walls carrying the newest value in the manifest.
     */
    val since: String = "",
    /**
     * A Core Builds Plus wall (Wallpapers/plus-manifest.json, series 15 and 16):
     * listed only in the Google Play build, and opened or saved only with
     * Plus owned.
     */
    val plus: Boolean = false
) : Parcelable {

    private constructor(parcel: Parcel) : this(
        name = parcel.readString().orEmpty(),
        series = parcel.readString().orEmpty(),
        url = parcel.readString().orEmpty(),
        thumbUrl = parcel.readString().orEmpty(),
        resolution = parcel.readString().orEmpty(),
        thumbAsset = parcel.readString().orEmpty(),
        isLive = parcel.readInt() == 1,
        since = parcel.readString().orEmpty(),
        plus = parcel.readInt() == 1
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(series)
        parcel.writeString(url)
        parcel.writeString(thumbUrl)
        parcel.writeString(resolution)
        parcel.writeString(thumbAsset)
        parcel.writeInt(if (isLive) 1 else 0)
        parcel.writeString(since)
        parcel.writeInt(if (plus) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Wallpaper> {
        override fun createFromParcel(parcel: Parcel): Wallpaper = Wallpaper(parcel)
        override fun newArray(size: Int): Array<Wallpaper?> = arrayOfNulls(size)
    }
    /** Short title without the leading number/series prefix. */
    val title: String
        get() = name.substringAfter("· ", name).trim()

    /** The wall's file name from its URL: the name it is exported to Pictures under. */
    val cacheName: String
        get() = url.substringAfterLast('/')

    /**
     * The name the download is cached under. A re-rendered wall keeps its URL, so the
     * manifest's `since` goes into the name: keyed on [cacheName] alone, a wall opened
     * before its re-render kept serving the old picture. A re-render must therefore
     * move to a new `since`; `tools/check_wallpaper_cache_keys.py` fails a release
     * that does not. The old file ages out of the 12-file cache like any other.
     */
    val storeName: String
        get() {
            val stamp = since.filter { it.isLetterOrDigit() || it == '.' || it == '-' }
            return if (stamp.isEmpty()) cacheName else "$stamp-$cacheName"
        }
}

object WallpaperCatalog {

    private const val MANIFEST = "manifest/wallpapers.json"
    const val THUMB_DIR = "wallpapers_thumbs"

    /** Core Builds Plus walls: bundled by the play build type only (src/play/assets). */
    private const val PLUS_MANIFEST = "manifest/plus-wallpapers.json"
    const val PLUS_THUMB_DIR = "plus_thumbs"

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
        return parse(json, THUMB_DIR, plus = false)
    }

    /**
     * The Core Builds Plus walls, or none: a GitHub build has no such asset,
     * which is not an error, so nothing is logged.
     */
    fun loadPlus(context: Context): List<Wallpaper> {
        val json = try {
            context.assets.open(PLUS_MANIFEST).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return emptyList()
        }
        return parse(json, PLUS_THUMB_DIR, plus = true)
    }

    private fun parse(json: String, thumbDir: String, plus: Boolean): List<Wallpaper> {
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
                thumbAsset = "$thumbDir/${url.substringAfterLast('/').substringBeforeLast('.')}.jpg",
                since = o.optString("since"),
                plus = plus
            )
        }
        return out
    }

    /**
     * The newest `since` in the collection, or null if no wall carries one.
     * Compared numerically per dotted component, so "2.10.0" beats "2.4.0".
     */
    fun newestSince(walls: List<Wallpaper>): String? =
        walls.map { it.since }.filter { it.isNotBlank() }.maxWithOrNull(::compareVersions)

    /** Walls added or re-rendered in the newest release the manifest knows. */
    fun newWalls(walls: List<Wallpaper>): List<Wallpaper> {
        val newest = newestSince(walls) ?: return emptyList()
        return walls.filter { it.since == newest }
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrNull(i) ?: 0) - (pb.getOrNull(i) ?: 0)
            if (d != 0) return d
        }
        return 0
    }

    /**
     * Human label for a series id, e.g. "series-6-circuit-core" -> "Circuit Core".
     *
     * Strips the leading "series-N-" index prefix (the number is an ordering
     * aid, not a word) and title-cases the remainder. Uses `uppercase(Locale)`
     * rather than `CharSequence.titlecase()` because the latter is API 24+ and
     * minSdk is 21.
     */
    fun seriesLabel(series: String): String {
        val words = series
            .removePrefix("series-")
            .substringAfter('-')
        if (words.isBlank()) return series
        return words.split('-')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.substring(0, 1).uppercase(Locale.ROOT) + word.substring(1)
            }
    }
}
