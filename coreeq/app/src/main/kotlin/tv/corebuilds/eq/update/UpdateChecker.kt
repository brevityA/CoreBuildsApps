package tv.corebuilds.eq.update

import android.content.Context
import android.util.Log
import org.json.JSONObject
import tv.corebuilds.eq.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Update check.
 *
 * Polls `Latestrelease/coreeq-version.json` on the repository and compares its
 * `versionCode` with the installed one. This is the icon pack's updater, moved
 * to Core EQ: a sideloaded APK has no store to update it, so the release feed
 * is the only place a TV can learn that a newer build exists.
 *
 * Deliberately small: no library, no background service, no analytics. One
 * HTTPS GET on a worker thread when the Home screen opens, and only when the
 * user has not switched checks off. Download starts only after the update
 * button is pressed (see [UpdateInstaller]).
 */
object UpdateChecker {

    private const val TAG = "CoreEqUpdate"
    private const val TIMEOUT_MS = 8000
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    /** Long enough to say what changed, short enough to stay one line. */
    private const val MAX_HIGHLIGHTS = 8

    /**
     * The debug build (the public `coreeq-test` channel) is handed a blank
     * URL by Gradle: its package is `tv.corebuilds.eq.debug`, so the
     * production APK could never install over it, and a test channel that
     * offers an update it cannot install is worse than one that says nothing.
     * The blank value is reported as the named gap it is.
     */
    private val MANIFEST_URL: String = BuildConfig.UPDATE_MANIFEST_URL

    /** Outcome of a check. Never an unnamed error. */
    sealed class Result {
        /** A newer build exists. */
        data class Available(
            val versionName: String,
            val versionCode: Int,
            val apkUrl: String,
            val apkSha256: String?,
            val highlights: List<String> = emptyList()
        ) : Result()

        /** The installed build is current. */
        data class UpToDate(val versionName: String) : Result()

        /** The check could not complete. [reason] says why, in plain words. */
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
            android.os.Handler(context.mainLooper).post { onResult(result) }
        }
    }

    private fun fetch(installedCode: Int): Result {
        if (MANIFEST_URL.isBlank()) {
            return Result.Failed("this build has no update feed")
        }
        if (!UpdateRules.isApprovedManifestUrl(MANIFEST_URL)) {
            return Result.Failed("update feed URL is not the suite's manifest")
        }
        val conn = URL(MANIFEST_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "CoreEQ-Updater")
            val code = conn.responseCode
            if (code != 200) {
                return Result.Failed("update feed returned HTTP $code")
            }
            val json = JSONObject(readBounded(conn, MAX_MANIFEST_BYTES))

            val remoteCode = json.getInt("versionCode")
            val remoteName = json.optString("versionName", "?")
            val apk = json.optString("apkUrl", "")
            val sha256 = json.optString("apkSha256", "").takeIf { it.isNotBlank() }
            val highlights = json.optJSONArray("highlights")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i, "").trim().takeIf { it.isNotEmpty() }
                }.take(MAX_HIGHLIGHTS)
            } ?: emptyList()

            require(remoteCode > 0) { "update feed has an invalid versionCode" }
            require(UpdateRules.isApprovedApkUrl(apk)) {
                "update feed points at an APK outside the suite's releases"
            }
            if (sha256 != null) {
                require(UpdateRules.isSha256(sha256)) { "update feed has an invalid SHA-256" }
            }

            Log.i(TAG, "installed=$installedCode remote=$remoteCode")

            return if (UpdateRules.isNewer(remoteCode, installedCode)) {
                Result.Available(remoteName, remoteCode, apk, sha256, highlights)
            } else {
                Result.UpToDate(remoteName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "update feed fetch failed: ${e.message}")
            return Result.Failed(e.message ?: "could not fetch the update feed")
        } finally {
            try {
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /** A manifest that streams past its cap is refused, not buffered. */
    private fun readBounded(conn: HttpURLConnection, maxBytes: Int): String {
        val declared = conn.contentLength
        if (declared > maxBytes) {
            throw IllegalStateException("update feed exceeds ${maxBytes}B")
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
                    throw IllegalStateException("update feed exceeds ${maxBytes}B")
                }
                out.write(buf, 0, n)
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
