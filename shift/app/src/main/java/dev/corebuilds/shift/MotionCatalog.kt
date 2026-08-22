package dev.corebuilds.shift

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors

data class MotionEntry(
    val name: String,
    val series: String,
    val url1080p: String,
    val url4k: String?,
    val thumb: String,
    val resolution: String,
    val duration: Int
)

object MotionCatalog {

    private const val TAG = "CoreShiftCatalog"
    private const val REMOTE_URL =
        "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/" +
            "main/Motion/manifest-motion.json"
    private const val ASSET_FILE = "manifest-motion.json"
    private const val TIMEOUT_MS = 8000

    private val io = Executors.newSingleThreadExecutor()

    fun load(context: Context, onResult: (List<MotionEntry>) -> Unit) {
        io.execute {
            val entries = try {
                loadRemote() ?: loadBundled(context)
            } catch (e: Exception) {
                Log.w(TAG, "remote load failed, falling back to bundled: ${e.message}")
                loadBundled(context)
            }
            android.os.Handler(context.mainLooper).post { onResult(entries) }
        }
    }

    private fun loadRemote(): List<MotionEntry>? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(REMOTE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "CoreShift-Catalog")
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "manifest returned HTTP ${conn.responseCode}")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseManifest(body)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun loadBundled(context: Context): List<MotionEntry> {
        return try {
            val body = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            parseManifest(body)
        } catch (e: Exception) {
            Log.w(TAG, "bundled manifest failed: ${e.message}")
            emptyList()
        }
    }

    private val ALLOWED_HOSTS = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com"
    )

    private fun requireAllowedUrl(url: String, field: String) {
        val host = URI(url).host
        require(host in ALLOWED_HOSTS) { "$field host not allowed: $host" }
        require(URI(url).scheme == "https") { "$field must use HTTPS" }
    }

    private fun parseManifest(json: String): List<MotionEntry> {
        val root = JSONObject(json)
        val videos = root.getJSONArray("videos")
        return (0 until videos.length()).mapNotNull { i ->
            val v = videos.getJSONObject(i)
            try {
                val url1080p = v.getString("url_1080p")
                val thumb = v.optString("thumb", "")
                val url4k = v.optString("url_4k", null)
                requireAllowedUrl(url1080p, "url_1080p")
                if (thumb.isNotBlank()) requireAllowedUrl(thumb, "thumb")
                if (!url4k.isNullOrBlank()) requireAllowedUrl(url4k, "url_4k")
                MotionEntry(
                    name = v.getString("name"),
                    series = v.optString("series", ""),
                    url1080p = url1080p,
                    url4k = url4k,
                    thumb = thumb,
                    resolution = v.optString("resolution", "1920x1080"),
                    duration = v.optInt("duration", 20)
                )
            } catch (e: Exception) {
                Log.w(TAG, "skipping entry ${v.optString("name", "index $i")}: ${e.message}")
                null
            }
        }
    }
}
