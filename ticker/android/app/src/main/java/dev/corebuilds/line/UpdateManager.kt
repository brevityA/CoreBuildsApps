package dev.corebuilds.line

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Sideload updater — Core Line ships outside the Play Store, so updates are
 * the classic off-Play path: download the signed APK from the GitHub release,
 * then hand it to the system package installer (FileProvider + ACTION_VIEW).
 *
 * No credentials, no logging of URLs beyond the host, and only GitHub release
 * hosts are accepted (SSRF-safe). The version CHECK itself is done in JS (via
 * the /api/proxy fetch of the releases JSON); this object only downloads and
 * installs.
 */
object UpdateManager {
    private const val TAG = "CoreLineUpdate"
    private const val MAX_APK_BYTES = 80L * 1024 * 1024
    private const val MAX_REDIRECTS = 5

    /** Only the GitHub release CDN is a legitimate download source. */
    fun isAllowed(rawUrl: String?): Boolean {
        val uri = try {
            URI(rawUrl?.trim().orEmpty())
        } catch (_: Exception) {
            return false
        }
        if (uri.scheme?.lowercase() != "https") return false
        val host = try {
            uri.host?.lowercase()?.trim('[', ']')
        } catch (_: Exception) {
            null
        } ?: return false
        return host == "github.com" ||
            host == "objects.githubusercontent.com" ||
            host == "release-assets.githubusercontent.com" ||
            host.endsWith(".github.com")
    }

    /**
     * Download the APK on a worker thread, then launch the system installer on
     * the main thread. @return true when the download started (install happens
     * asynchronously after it completes).
     */
    fun downloadAndInstall(context: Context, rawUrl: String): Boolean {
        if (!isAllowed(rawUrl)) return false
        val cacheDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(cacheDir, "coreline-update-${System.currentTimeMillis()}.apk")
        val main = ContextCompat.getMainExecutor(context)
        Thread {
            val ok = try {
                download(rawUrl, apk)
            } catch (err: Exception) {
                Log.w(TAG, "update download failed", err)
                false
            }
            main.execute {
                if (ok) launchInstaller(context, apk)
                else apk.delete()
            }
        }.start()
        return true
    }

    private fun download(rawUrl: String, apk: File): Boolean {
        var currentUrl = rawUrl
        var redirects = 0
        while (redirects <= MAX_REDIRECTS) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 20_000
                conn.readTimeout = 180_000
                conn.setRequestProperty("User-Agent", "CoreLineUpdater/1.0")
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: return false
                    if (!isAllowed(location)) {
                        Log.w(TAG, "redirect to disallowed host rejected")
                        return false
                    }
                    currentUrl = location
                    redirects++
                    continue
                }
                if (code !in 200..299) return false
                conn.inputStream.use { input ->
                    FileOutputStream(apk).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_APK_BYTES) {
                                throw IllegalStateException("update larger than ${MAX_APK_BYTES / (1024 * 1024)} MB")
                            }
                            out.write(buf, 0, n)
                        }
                    }
                }
                return apk.length() > 0
            } finally {
                conn.disconnect()
            }
        }
        Log.w(TAG, "too many redirects")
        return false
    }

    private fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (err: Exception) {
            Log.w(TAG, "no package installer available", err)
        }
    }
}
