package tv.corebuilds.eq.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import tv.corebuilds.eq.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipFile

/**
 * Downloads a release APK and offers it to the system installer only after the
 * artifact is proven to be the same package, newer than this build, and signed
 * by the certificate that signed the installed app.
 *
 * This is the icon pack's installer, kept whole: the checks are what make an
 * in-app updater safe on a sideloaded TV app, where a typo in a feed URL would
 * otherwise be an installer prompt for a stranger's APK. `apkSha256` in the
 * feed is optional for older manifests but enforced whenever present.
 */
object UpdateInstaller {

    /** Must match the FileProvider authority Gradle writes into the manifest. */
    val AUTHORITY: String = BuildConfig.UPDATE_AUTHORITY

    private const val TIMEOUT_MS = 30_000
    private const val MIN_APK_BYTES = 200_000L
    private const val MAX_APK_BYTES = 120L * 1024L * 1024L
    private const val UPDATE_FILE = "coreeq-update.apk"

    /**
     * Release assets are served from github.com and redirect to one of these
     * CDN hosts. A redirect anywhere else ends the download: the installer
     * verifies the bytes, but it will not fetch them from an unknown origin.
     */
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

    fun download(
        context: Context,
        apkUrl: String,
        expectedVersionCode: Int,
        expectedSha256: String?,
        onEvent: (Event) -> Unit,
    ) {
        val app = context.applicationContext
        val main = android.os.Handler(app.mainLooper)
        io.execute {
            try {
                val file = fetchToCache(app, apkUrl) { received, total ->
                    main.post { onEvent(Event.Progress(received, total)) }
                }
                verifyDownloadedApk(app, file, app.packageName, expectedSha256) { code ->
                    code == expectedVersionCode && code > BuildConfig.VERSION_CODE
                }
                main.post { onEvent(Event.Ready(file)) }
            } catch (e: Exception) {
                main.post { onEvent(Event.Failed(e.message ?: e.javaClass.simpleName)) }
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

    /** Opens the "install unknown apps" page for this package; true if shown. */
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

    /**
     * Hand [file] to the system installer and have it answer: the result comes
     * back to [activity]'s onActivityResult under [requestCode] as RESULT_OK
     * (installed), RESULT_CANCELED (backed out) or RESULT_FIRST_USER (failed).
     * The fire-and-forget [install] cannot say which, and on a TV "did it
     * take?" is the question the user is about to ask.
     */
    @Suppress("DEPRECATION")
    fun installForResult(activity: Activity, file: File, requestCode: Int) {
        val uri = FileProvider.getUriForFile(activity, AUTHORITY, file)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        activity.startActivityForResult(intent, requestCode)
    }

    private fun fetchToCache(
        context: Context,
        apkUrl: String,
        onProgress: (Long, Long) -> Unit,
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dest = File(dir, UPDATE_FILE)
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
        for (hop in 0 until 6) {
            val next = URL(url)
            if (next.protocol != "https" || next.host !in ALLOWED_HOSTS) {
                throw IllegalStateException("redirect left GitHub ($url)")
            }
            val nextConn = (next.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.android.package-archive,*/*")
                setRequestProperty("User-Agent", "CoreEQ-Updater")
            }
            conn = nextConn
            val code = nextConn.responseCode
            if (code in 300..399) {
                val location = nextConn.getHeaderField("Location")
                    ?: throw IllegalStateException("redirect $code with no Location")
                nextConn.disconnect()
                url = if (location.startsWith("http")) location else URL(URL(url), location).toString()
            } else if (code == 200) {
                break
            } else {
                throw IllegalStateException("APK download returned HTTP $code")
            }
        }

        val c = conn ?: throw IllegalStateException("could not open $apkUrl")
        try {
            if (c.responseCode != 200) {
                throw IllegalStateException("APK download returned HTTP ${c.responseCode}")
            }
            @Suppress("DEPRECATION")
            val total = c.contentLength.toLong().let { if (it > 0) it else -1L }
            if (total > MAX_APK_BYTES) {
                throw IllegalStateException("APK exceeds ${MAX_APK_BYTES / (1024 * 1024)} MB")
            }
            var received = 0L
            c.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        received += n
                        if (received > MAX_APK_BYTES) {
                            dest.delete()
                            throw IllegalStateException("APK exceeds ${MAX_APK_BYTES / (1024 * 1024)} MB")
                        }
                        output.write(buf, 0, n)
                        onProgress(received, total)
                    }
                }
            }
            if (received < MIN_APK_BYTES) {
                dest.delete()
                throw IllegalStateException("downloaded ${received}B — too small to be an APK")
            }
            val header = dest.inputStream().use { stream ->
                val b = ByteArray(2)
                stream.read(b)
                b
            }
            if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                dest.delete()
                throw IllegalStateException("not a ZIP/APK (bad magic)")
            }
            ZipFile(dest).use { zip ->
                zip.getEntry("AndroidManifest.xml")
                    ?: throw IllegalStateException("APK missing AndroidManifest.xml")
            }
            return dest
        } finally {
            c.disconnect()
        }
    }

    private fun verifyDownloadedApk(
        context: Context,
        file: File,
        expectedPackage: String,
        expectedSha256: String?,
        versionAccepted: (Int) -> Boolean,
    ) {
        val normalized = expectedSha256?.trim()?.lowercase(Locale.US).orEmpty()
        if (normalized.isNotEmpty()) {
            require(UpdateRules.isSha256(normalized)) { "invalid apkSha256 in update feed" }
            if (sha256(file) != normalized) {
                file.delete()
                throw IllegalStateException("APK checksum mismatch")
            }
        }

        val pm = context.packageManager
        val archive = archivePackageInfo(pm, file)
            ?: throw IllegalStateException("downloaded file is not an installable APK")
        if (archive.packageName != expectedPackage) {
            file.delete()
            throw IllegalStateException("APK package mismatch: ${archive.packageName}")
        }
        val archiveCode = versionCodeOf(archive)
        if (!versionAccepted(archiveCode)) {
            file.delete()
            throw IllegalStateException("APK version $archiveCode is not the version expected")
        }

        // The signature check is the one that matters: Android itself refuses
        // an update signed by a different key, so a mismatch here means the
        // feed is pointing at an APK that cannot install over this app at all.
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val installedCerts = signingCertDigests(installed)
        val archiveCerts = signingCertDigests(archive)
        if (installedCerts.isEmpty() || archiveCerts.isEmpty() ||
            installedCerts.intersect(archiveCerts).isEmpty()
        ) {
            file.delete()
            throw IllegalStateException("APK signature does not match installed app")
        }
    }

    private fun archivePackageInfo(pm: PackageManager, file: File): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
        }
    }

    private fun versionCodeOf(info: PackageInfo): Int {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else info.versionCode
    }

    private fun signingCertDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()
        return signatures.map { sig -> sha256(sig.toByteArray()) }.toSet()
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
