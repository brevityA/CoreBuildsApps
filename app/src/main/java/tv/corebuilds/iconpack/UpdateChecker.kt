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
 * Brand Guide §08 — the result names the version and what it will do. There
 * is no silent download and no self-install: Android would need
 * REQUEST_INSTALL_PACKAGES for that, which is a sensitive permission and a
 * poor trade for a pack the user sideloaded on purpose. We report, they
 * choose.
 */
object UpdateChecker {

    private const val TAG = "CoreBuildsUpdate"
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/brevityA/CoreBuildsIconPack/" +
            "main/Latestrelease/version.json"
    private const val TIMEOUT_MS = 8000

    /** Outcome of a check. Never an unnamed error (§08). */
    sealed class Result {
        /** A newer build exists. */
        data class Available(
            val versionName: String,
            val versionCode: Int,
            val iconCount: Int,
            val apkUrl: String
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
        val installed = installedVersionCode(context)
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
        val conn = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code != 200) {
                return Result.Failed("update manifest returned HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val remoteCode = json.getInt("versionCode")
            val remoteName = json.optString("versionName", "?")
            val icons = json.optInt("iconCount", 0)
            val apk = json.optString("apkUrl", "")

            Log.i(TAG, "installed=$installedCode remote=$remoteCode")

            return if (remoteCode > installedCode) {
                Result.Available(remoteName, remoteCode, icons, apk)
            } else {
                Result.UpToDate(remoteName)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun installedVersionCode(context: Context): Int = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pi.longVersionCode.toInt()
        } else {
            pi.versionCode
        }
    } catch (e: Exception) {
        Log.w(TAG, "could not read installed version: ${e.message}")
        0
    }
}
