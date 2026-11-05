package tv.corebuilds.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * A connected wallpaper is fetched only from a URL the owner enters. There is
 * no built-in gallery, default endpoint, account, telemetry or automatic
 * refresh. The response is byte- and dimension-bounded before it is applied.
 */
internal object NetworkWallpaper {
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_WIDTH = 1920
    private const val MAX_HEIGHT = 1080
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    fun isAllowedUrl(value: String): Boolean {
        val uri = try {
            URI(value.trim())
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }

    /** ACCESS_NETWORK_STATE keeps a disconnected TV from waiting on a timeout. */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Downloads a bounded image off the UI thread; never follows non-HTTP schemes. */
    fun fetch(value: String): Bitmap? {
        if (!isAllowedUrl(value)) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(value.trim()).openConnection() as? HttpURLConnection) ?: return null
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/*")
            if (connection.responseCode !in 200..299) return null
            if (connection.contentLengthLong > MAX_BYTES) return null
            val bytes = readBounded(connection.inputStream)
            if (bytes == null || bytes.isEmpty()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** SET_WALLPAPER applies the decoded bitmap through Android's WallpaperManager. */
    fun apply(context: Context, bitmap: Bitmap): Boolean = try {
        WallpaperManager.getInstance(context).setBitmap(bitmap)
        true
    } catch (_: Exception) {
        false
    }

    private fun readBounded(input: java.io.InputStream): ByteArray? {
        return try {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var total = 0
            var tooLarge = false
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_BYTES) {
                    tooLarge = true
                    break
                }
                out.write(buffer, 0, count)
            }
            if (tooLarge) null else out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= MAX_WIDTH && h / 2 >= MAX_HEIGHT) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample.coerceAtLeast(1)
    }
}
