package dev.corebuilds.shift

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
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
 * The downloader is deliberately a bounded, observable pipeline: it reports
 * progress, refuses oversized/non-MP4 responses, checks cache free space and
 * writes through a temporary file before exposing the completed asset.
 */
object LiveDownloader {

    private const val TAG = "CoreShift/Download"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MIN_VIDEO_BYTES = 20_000L
    private const val MAX_VIDEO_BYTES = 256L * 1024L * 1024L
    private const val FREE_SPACE_MARGIN = 8L * 1024L * 1024L
    private val RELATIVE_PATH = "${Environment.DIRECTORY_MOVIES}/CoreBuilds"

    private val ALLOWED_HOSTS = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
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

    /** Download [entry] at [quality] and save into Movies/CoreBuilds. */
    fun download(
        context: Context,
        entry: LiveEntry,
        quality: QualityTier = QualityTier.HD_1080,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): Result {
        val app = context.applicationContext
        if (!hasStoragePermission(app)) {
            return Result.NeedsPermission(storagePermission()!!)
        }
        val sourceUrl = entry.urlFor(quality)
            ?: return Result.Failed("${quality.label} version is unavailable")
        val cacheName = sourceUrl.substringAfterLast('/')
        return try {
            val file = fetch(app, sourceUrl, cacheName, onProgress)
                ?: return Result.Failed("download failed")
            copyToMovies(app, file, cacheName)
        } catch (e: Exception) {
            Log.w(TAG, "download failed: ${entry.cacheName} (${quality.label})", e)
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Fetch [url] into the internal live cache, returning the local file.
     * [onProgress] runs on the caller's worker thread and never on the UI.
     */
    fun fetch(
        context: Context,
        url: String,
        cacheName: String,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): File? {
        val parsed = try {
            URL(url)
        } catch (_: Exception) {
            return null
        }
        if (parsed.protocol != "https" || parsed.host !in ALLOWED_HOSTS) {
            Log.w(TAG, "refusing non-https or non-allowlisted url: $url")
            return null
        }
        val dir = File(context.cacheDir, "live").apply { mkdirs() }
        val dest = File(dir, cacheName)
        if (dest.exists() && dest.length() > MIN_VIDEO_BYTES && isMp4(dest)) {
            onProgress(dest.length(), dest.length())
            return dest
        }
        dest.delete()

        var conn: HttpURLConnection? = null
        val tmp = File(dir, "$cacheName.part")
        return try {
            conn = (parsed.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "video/mp4,video/*;q=0.8")
                setRequestProperty("User-Agent", "CoreShift")
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${conn.responseCode} for $cacheName")
                return null
            }
            val declared = conn.contentLengthLong
            if (declared > MAX_VIDEO_BYTES) {
                Log.w(TAG, "refusing oversized video ($declared bytes) for $cacheName")
                return null
            }
            val available = StatFs(context.cacheDir.absolutePath).availableBytes
            if (declared > 0 && available < declared + FREE_SPACE_MARGIN) {
                Log.w(TAG, "not enough cache space for $cacheName")
                return null
            }

            var received = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > MAX_VIDEO_BYTES) {
                            throw IllegalStateException("video exceeds ${MAX_VIDEO_BYTES} bytes")
                        }
                        output.write(buffer, 0, count)
                        onProgress(received, if (declared > 0) declared else -1L)
                    }
                }
            }
            if (received < MIN_VIDEO_BYTES) {
                throw IllegalStateException("downloaded ${received}B — too small to be an MP4")
            }
            if (!isMp4(tmp)) {
                throw IllegalStateException("downloaded file is not an MP4")
            }
            if (!tmp.renameTo(dest)) {
                throw IllegalStateException("could not commit cached video")
            }
            onProgress(dest.length(), dest.length())
            dest
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed for $cacheName: ${e.message}")
            null
        } finally {
            conn?.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun isMp4(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                read >= 8 && header.copyOfRange(4, 8).contentEquals("ftyp".toByteArray())
            }
        } catch (_: Exception) {
            false
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
                    } ?: throw IllegalStateException("could not open output stream")
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    val rows = context.contentResolver.update(uri, values, null, null)
                    if (rows != 1) throw IllegalStateException("MediaStore publish failed ($rows rows)")
                    Result.Saved(uri)
                } catch (e: Exception) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    Result.Failed(e.message ?: "could not save video")
                }
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
                val tmp = File(dir, "$displayName.part")
                file.inputStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                }
                if (!tmp.renameTo(dest)) {
                    tmp.delete()
                    return Result.Failed("could not commit Movies/CoreBuilds file")
                }
                Result.Saved(Uri.fromFile(dest))
            }
        } catch (e: Exception) {
            Log.w(TAG, "copy to Movies failed", e)
            Result.Failed(e.message ?: "could not save video")
        }
    }
}
