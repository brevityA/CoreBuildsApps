package dev.corebuilds.shift

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object MotionDownloader {

    private const val TAG = "CoreShiftDownload"
    private const val TIMEOUT_MS = 30_000
    private const val SUBFOLDER = "CoreBuilds"

    private val ALLOWED_HOSTS = setOf(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com"
    )

    private val io = Executors.newSingleThreadExecutor()

    sealed class Result {
        data class Success(val uri: Uri) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun download(
        context: Context,
        url: String,
        filename: String,
        onResult: (Result) -> Unit
    ) {
        io.execute {
            val result = try {
                doDownload(context, url, filename)
            } catch (e: Exception) {
                Result.Failed(e.message ?: e.javaClass.simpleName)
            }
            android.os.Handler(context.mainLooper).post { onResult(result) }
        }
    }

    private fun doDownload(context: Context, url: String, filename: String): Result {
        val parsed = URL(url)
        if (parsed.protocol != "https") {
            return Result.Failed("Core Shift requires HTTPS, got ${parsed.protocol}")
        }
        if (parsed.host !in ALLOWED_HOSTS) {
            return Result.Failed("host not allowed: ${parsed.host}")
        }

        var conn: HttpURLConnection? = null
        try {
            conn = (parsed.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                // raw.githubusercontent.com can issue a 302 redirect (e.g. to
                // media.githubusercontent.com). Follow it — same as the
                // icon-pack app's downloader, which ships and works.
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CoreShift-Downloader")
            }
            if (conn.responseCode !in 200..299) {
                return Result.Failed("HTTP ${conn.responseCode}")
            }

            val inputStream = conn.inputStream

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/$SUBFOLDER"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return Result.Failed("Core Shift MediaStore insert failed for $filename")

                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        inputStream.copyTo(out)
                    } ?: run {
                        resolver.delete(uri, null, null)
                        return Result.Failed("Core Shift could not open output for $filename")
                    }
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                Log.i(TAG, "saved $filename via MediaStore")
                Result.Success(uri)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MOVIES
                    ),
                    SUBFOLDER
                )
                dir.mkdirs()
                val outFile = File(dir, filename)
                FileOutputStream(outFile).use { out ->
                    inputStream.copyTo(out)
                }
                Log.i(TAG, "saved $filename to ${outFile.absolutePath}")
                Result.Success(Uri.fromFile(outFile))
            }
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    fun isDownloaded(context: Context, filename: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val exactPath = "${Environment.DIRECTORY_MOVIES}/$SUBFOLDER/"
            val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Video.Media.RELATIVE_PATH} = ?"
            val args = arrayOf(filename, exactPath)
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args, null
            )
            val found = (cursor?.count ?: 0) > 0
            cursor?.close()
            found
        } else {
            @Suppress("DEPRECATION")
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "$SUBFOLDER/$filename"
            )
            file.exists()
        }
    }
}
