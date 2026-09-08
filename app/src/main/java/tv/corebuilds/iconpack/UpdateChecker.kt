package tv.corebuilds.iconpack

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Update check.
 *
 * Polls `Latestrelease/version.json` on the repo and compares its
 * `versionCode` against the installed one. This is the same pattern the
 * reference Projectivy pack uses, and it works for a sideloaded APK where
 * the Play Store cannot help.
 *
 * Deliberately small: no library, no background service, no analytics. One
 * HTTPS GET on a worker thread when the app is opened.
 *
 * Brand Guide §08 — the result names the version. Download only starts
 * when the user presses the update button (see UpdateInstaller).
 */
object UpdateChecker {

    private const val TAG = "CoreBuildsUpdate"
    // Each pack polls its own manifest. Supplied by the module that is
    // building, so :pop can never tell a user they are out of date because
    // the classic pack shipped a release.
    private val MANIFEST_URLS = listOf(BuildConfig.UPDATE_MANIFEST_URL)
    private const val TIMEOUT_MS = 8000
    private const val MAX_MANIFEST_BYTES = 64 * 1024

    /** Outcome of a check. Never an unnamed error (§08). */
    sealed class Result {
        /** A newer build exists. */
        data class Available(
            val versionName: String,
            val versionCode: Int,
            val iconCount: Int,
            val apkUrl: String,
            val apkSha256: String?
        ) : Result()

        /** Installed build is current. */
        data class UpToDate(val versionName: String) : Result()

        /** Check could not complete. [reason] says why, in plain words. */
        data class Failed(val reason: String) : Result()
    }

    private val io = Executors.newSingleThreadExecutor()

    /**
     * Run the check off the main thread and deliver the result back on it.
     *
     * @param onResult always called exactly once.
     */
    fun check(context: Context, onResult: (Result) -> Unit) {
        val installed = BuildConfig.VERSION_CODE
        io.execute {
            val result = try {
                fetch(installed)
            } catch (e: Exception) {
                Result.Failed(e.message ?: e.javaClass.simpleName)
            }
            // Post back to the caller's thread.
            android.os.Handler(context.mainLooper).post { onResult(result) }
        }
    }

    private fun fetch(installedCode: Int): Result {
        var lastError: String? = null
        for (url in MANIFEST_URLS) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "CoreBuildsIconPack-Updater")
                }
                val code = conn.responseCode
                if (code != 200) {
                    lastError = "update manifest returned HTTP $code from $url"
                    Log.w(TAG, lastError!!)
                    continue
                }
                val body = readBounded(conn, MAX_MANIFEST_BYTES)
                val json = JSONObject(body)

                val remoteCode = json.getInt("versionCode")
                val remoteName = json.optString("versionName", "?")
                val icons = json.optInt("iconCount", 0)
                val apk = json.optString("apkUrl", "")
                val sha256 = json.optString("apkSha256", "").takeIf { it.isNotBlank() }

                Log.i(TAG, "installed=$installedCode remote=$remoteCode from $url")

                return if (remoteCode > installedCode) {
                    Result.Available(remoteName, remoteCode, icons, apk, sha256)
                } else {
                    Result.UpToDate(remoteName)
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "manifest fetch failed $url: $lastError")
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
        return Result.Failed(lastError ?: "could not fetch update manifest")
    }

    private fun readBounded(conn: HttpURLConnection, maxBytes: Int): String {
        val declared = conn.contentLength
        if (declared > maxBytes) {
            throw IllegalStateException("update manifest exceeds ${maxBytes}B")
        }
        val out = java.io.ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(4096)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > maxBytes) {
                    throw IllegalStateException("update manifest exceeds ${maxBytes}B")
                }
                out.write(buf, 0, n)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
