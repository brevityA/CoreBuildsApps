package dev.corebuilds.shift

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object UpdateChecker {

    private const val TAG = "CoreShiftUpdate"
    private val MANIFEST_URLS = listOf(
        "https://raw.githubusercontent.com/brevityA/CoreBuildsIconPack/" +
            "main/Latestrelease/shift-version.json"
    )
    private const val TIMEOUT_MS = 8000

    sealed class Result {
        data class Available(
            val versionName: String,
            val versionCode: Int,
            val apkUrl: String
        ) : Result()

        data class UpToDate(val versionName: String) : Result()

        data class Failed(val reason: String) : Result()
    }

    private val io = Executors.newSingleThreadExecutor()

    fun check(context: Context, onResult: (Result) -> Unit) {
        val installedCode = BuildConfig.VERSION_CODE
        val installedName = BuildConfig.VERSION_NAME
        io.execute {
            val result = try {
                fetch(installedCode, installedName)
            } catch (e: Exception) {
                Result.Failed(e.message ?: e.javaClass.simpleName)
            }
            android.os.Handler(context.mainLooper).post { onResult(result) }
        }
    }

    private fun fetch(installedCode: Int, installedName: String): Result {
        var lastError: String? = null
        for (url in MANIFEST_URLS) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "CoreShift-Updater")
                }
                val code = conn.responseCode
                if (code != 200) {
                    lastError = "update manifest returned HTTP $code from $url"
                    Log.w(TAG, lastError!!)
                    continue
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                val remoteCode = json.getInt("versionCode")
                val remoteName = json.optString("versionName", "?")
                val apk = json.optString("apkUrl", "")

                Log.i(TAG, "installed=$installedCode ($installedName) " +
                    "remote=$remoteCode ($remoteName) from $url")

                return if (remoteCode > installedCode && remoteName != installedName) {
                    Result.Available(remoteName, remoteCode, apk)
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
}
