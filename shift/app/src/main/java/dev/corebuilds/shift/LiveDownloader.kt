package dev.corebuilds.shift

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a live-wallpaper MP4 into `Movies/CoreBuilds`, the shared location
 * Monet's "your videos" source reads.
 *
 * https-only + GitHub host allowlist, follows redirects (raw.githubusercontent
 * can 302), streams bytes to MediaStore (API 29+) or a file (API ≤28) with no
 * re-encode and no half-written rows on failure.
 */
object LiveDownloader {

    private const val TAG = "CoreShift/Download"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val RELATIVE_PATH = "${Environment.DIRECTORY_MOVIES}/CoreBuilds"

    private val ALLOWED_HOSTS = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
    )

    sealed class Result {
        data class Saved(val uri: Uri) : Result()
        data class NeedsPermission(val permission: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun storagePermission(): String? =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) Manifest.permission.WRITE_EXTERNAL_STORAGE
        else null

    fun hasStoragePermission(context: Context): Boolean {
        val p = storagePermission() ?: return true
        return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }

    /** Download [entry] and save into Movies/CoreBuilds. Never throws. */
    fun download(context: Context, entry: LiveEntry): Result {
        val app = context.applicationContext
        if (!hasStoragePermission(app)) {
            return Result.NeedsPermission(storagePermission()!!)
        }
        return try {
            val file = fetch(app, entry.url1080p, entry.cacheName)
                ?: return Result.Failed("download failed")
            copyToMovies(app, file, entry.cacheName)
        } catch (e: Exception) {
            Log.w(TAG, "download failed: ${entry.cacheName}", e)
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Fetch [url] into the internal live cache, returning the local file.
     * Public so [PreviewActivity] can play a cached copy instead of streaming
     * from the remote host (which may not serve a video content-type or ranges).
     */
    fun fetch(context: Context, url: String, cacheName: String): File? {
        val host = try {
            URL(url).host
        } catch (_: Exception) {
            return null
        }
        if (!url.startsWith("https://") || host !in ALLOWED_HOSTS) {
            Log.w(TAG, "refusing non-https or non-allowlisted url: $url")
            return null
        }
        val dir = File(context.cacheDir, "live").apply { mkdirs() }
        val dest = File(dir, cacheName)
        if (dest.exists() && dest.length() > 20_000) return dest

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CoreShift")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${conn.responseCode} for $cacheName")
                return null
            }
            val tmp = File(dir, "$cacheName.part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            if (tmp.length() < 20_000) {
                tmp.delete()
                return null
            }
            if (!tmp.renameTo(dest)) tmp.delete()
            dest
        } finally {
            conn?.disconnect()
        }
    }

    private fun copyToMovies(context: Context, file: File, displayName: String): Result {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values,
                ) ?: return Result.Failed("MediaStore insert failed")
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out, 64 * 1024) }
                    } ?: return Result.Failed("could not open output stream")
                } catch (e: Exception) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    throw e
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                Result.Saved(uri)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "CoreBuilds",
                )
                if (!dir.exists() && !dir.mkdirs()) {
                    return Result.Failed("could not create Movies/CoreBuilds")
                }
                val dest = File(dir, displayName)
                file.inputStream().use { it.copyTo(dest.outputStream(), 64 * 1024) }
                Result.Saved(Uri.fromFile(dest))
            }
        } catch (e: Exception) {
            Log.w(TAG, "copy to Movies failed", e)
            Result.Failed(e.message ?: "could not save video")
        }
    }
}
