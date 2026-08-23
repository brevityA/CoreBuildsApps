package dev.corebuilds.shift

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Small, bounded poster cache for remotely published prequel thumbnails.
 * Bundled thumbnails are still preferred; this only runs for the remote
 * catalog and never blocks RecyclerView binding or the UI thread.
 */
object RemoteThumbLoader {

    private const val MAX_BYTES = 512 * 1024
    private const val CONNECT_TIMEOUT_MS = 7_000
    private const val READ_TIMEOUT_MS = 10_000
    private val ALLOWED_HOSTS = setOf(
        "github.com",
        "raw.githubusercontent.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )
    private val io = Executors.newFixedThreadPool(2)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Callback receives the URL it requested so a recycled tile can guard it. */
    fun load(context: Context, url: String, onReady: (String, Bitmap?) -> Unit) {
        val app = context.applicationContext
        io.execute {
            val bitmap = runCatching { read(app, url) }.getOrNull()
            main.post { onReady(url, bitmap) }
        }
    }

    private fun read(context: Context, rawUrl: String): Bitmap? {
        val parsed = URL(rawUrl)
        require(parsed.protocol == "https") { "thumbnail must use HTTPS" }
        require(parsed.host in ALLOWED_HOSTS) { "thumbnail host is not allowlisted" }

        val dir = File(context.cacheDir, "prequel-thumbs").apply { mkdirs() }
        val dest = File(dir, sha256(rawUrl) + ".jpg")
        if (!dest.exists() || dest.length() == 0L) {
            val tmp = File(dir, dest.name + ".part")
            val connection = (parsed.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "image/jpeg,image/*;q=0.8")
                setRequestProperty("User-Agent", "CoreShift-Content")
            }
            try {
                require(connection.responseCode in 200..299) {
                    "thumbnail HTTP ${connection.responseCode}"
                }
                require(connection.contentLengthLong <= MAX_BYTES) { "thumbnail is too large" }
                connection.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BYTES) { "thumbnail is too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                require(tmp.length() > 0L) { "thumbnail is empty" }
                if (!tmp.renameTo(dest)) {
                    tmp.delete()
                    return null
                }
            } finally {
                connection.disconnect()
                if (tmp.exists()) tmp.delete()
            }
        }
        return BitmapFactory.decodeFile(dest.absolutePath)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
