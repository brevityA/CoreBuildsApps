package tv.corebuilds.iconpack

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * One-press anonymous icon requests: the auditor row POSTs the three fields
 * the GitHub issue form would have been prefilled with (app name, component,
 * device note) to the Core Builds request broker, which files the issue as a
 * bot — no GitHub account on the reporter's side, ever. `mapped` rows (the
 * pack already maps the app under another activity) are sent as mapping
 * reports, which the broker files with the "not auto-assigning" form's shape. The broker and the
 * trust split (the GitHub credential lives on the broker, never in the APK)
 * are documented in tools/icon_request_broker/.
 *
 * The payload carries no identifiers: no IP-derived store on our side beyond
 * a rate-limit window, no device IDs — a model name, an Android version and a
 * pack version, the same values the QR flow already asked the phone to paste.
 *
 * Contract with the caller: [send] always answers on the main thread, either
 * quickly (offline, invalid endpoint) or when the broker replies. A result of
 * >0 is the filed issue number (the broker returns the existing issue for a
 * duplicate — a second press is a "+1", not a second issue); <=0 means the
 * fallback path should run (in the auditor: the QR panel).
 */
object IconRequestSender {

    private val IO = Executors.newSingleThreadExecutor()
    private val MAIN = Handler(Looper.getMainLooper())

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000

    fun send(
        endpoint: String,
        appName: String,
        component: String,
        deviceNote: String,
        mapped: Boolean,
        onResult: (Int) -> Unit,
    ) {
        IO.execute {
            val issue = post(endpoint, appName, component, deviceNote, mapped)
            MAIN.post { onResult(issue) }
        }
    }

    /** Worker thread only. Returns the issue number, or <=0 on any failure. */
    private fun post(
        endpoint: String,
        appName: String,
        component: String,
        deviceNote: String,
        mapped: Boolean,
    ): Int {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val body = JSONObject()
                .put("app_name", appName)
                .put("component", component)
                .put("device", deviceNote)
                .apply { if (mapped) put("mapped", true) }
                .toString()
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                it.write(body)
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val text = stream?.let { input ->
                BufferedReader(InputStreamReader(input)).use { it.readText() }
            }.orEmpty()
            if (code in 200..299 || code == 409) {
                runCatching { JSONObject(text).optInt("issue", 0) }.getOrDefault(0)
            } else {
                0
            }
        } catch (_: Exception) {
            0
        } finally {
            conn?.disconnect()
        }
    }
}
