package tv.corebuilds.iconpack

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Download the release APK and hand it to the system installer.
 *
 * The user presses a button — nothing downloads in the background, nothing
 * installs itself. REQUEST_INSTALL_PACKAGES is declared so we can open the
 * installer; Android still shows the confirm screen.
 *
 * GitHub's /releases/latest/download URL 302s. Follow redirects, write to
 * cache/updates/, then FileProvider so the installer can read the file.
 */
object UpdateInstaller {

    const val AUTHORITY = "tv.corebuilds.iconpack.update"
    private const val TIMEOUT_MS = 30_000
    private const val MIN_APK_BYTES = 200_000L
    private val ALLOWED_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )
    private val io = Executors.newSingleThreadExecutor()

    sealed class Event {
        data class Progress(val received: Long, val total: Long) : Event()
        data class Ready(val file: File) : Event()
        data class Failed(val reason: String) : Event()
    }

    fun download(context: Context, apkUrl: String, onEvent: (Event) -> Unit) {
        val app = context.applicationContext
        val main = android.os.Handler(app.mainLooper)
        io.execute {
            try {
                val file = fetchToCache(app, apkUrl) { rec, tot ->
                    main.post { onEvent(Event.Progress(rec, tot)) }
                }
                main.post { onEvent(Event.Ready(file)) }
            } catch (e: Exception) {
                main.post {
                    onEvent(Event.Failed(e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun requestInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun fetchToCache(
        context: Context,
        apkUrl: String,
        onProgress: (Long, Long) -> Unit
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dest = File(dir, "core-builds-update.apk")
        if (dest.exists()) dest.delete()

        val start = URL(apkUrl)
        if (start.protocol != "https") {
            throw IllegalStateException("APK URL must be https")
        }
        if (start.host !in ALLOWED_HOSTS) {
            throw IllegalStateException("APK host ${start.host} is not GitHub")
        }
        var url = apkUrl
        var conn: HttpURLConnection? = null
        repeat(6) {
            val nextUrl = URL(url)
            if (nextUrl.protocol != "https" || nextUrl.host !in ALLOWED_HOSTS) {
                throw IllegalStateException("redirect left GitHub ($url)")
            }
            conn = (nextUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.android.package-archive,*/*")
                setRequestProperty("User-Agent", "CoreBuildsIconPack-Updater")
            }
            val code = conn!!.responseCode
            if (code in 300..399) {
                val next = conn!!.getHeaderField("Location")
                    ?: throw IllegalStateException("redirect $code with no Location")
                conn!!.disconnect()
                url = if (next.startsWith("http")) next else URL(URL(url), next).toString()
            } else if (code == 200) {
                return@repeat
            } else {
                throw IllegalStateException("APK download returned HTTP $code")
            }
        }
        val c = conn ?: throw IllegalStateException("could not open $apkUrl")
        try {
            if (c.responseCode != 200) {
                throw IllegalStateException("APK download returned HTTP ${c.responseCode}")
            }
            val total = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                c.contentLengthLong
            } else {
                @Suppress("DEPRECATION")
                c.contentLength.toLong()
            }.let { if (it > 0) it else -1L }
            var received = 0L
            c.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        received += n
                        onProgress(received, total)
                    }
                }
            }
            if (received < MIN_APK_BYTES) {
                dest.delete()
                throw IllegalStateException(
                    "downloaded ${received}B — too small to be an APK"
                )
            }
            val header = dest.inputStream().use { s ->
                val b = ByteArray(2); s.read(b); b
            }
            if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                dest.delete()
                throw IllegalStateException("not a ZIP/APK (bad magic)")
            }
            return dest
        } finally {
            c.disconnect()
        }
    }
}
