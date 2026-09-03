package dev.corebuilds.doctor.diagnostics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

object AddonChecks {

    private const val MAX_BODY_BYTES = 2L * 1024 * 1024 // 2 MB

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun Response.boundedBody(): String? {
        val source = body?.source() ?: return null
        source.request(MAX_BODY_BYTES + 1)
        if (source.buffer.size > MAX_BODY_BYTES) return null
        val charset = body?.contentType()?.charset() ?: Charsets.UTF_8
        return source.buffer.readString(charset)
    }

    fun checkManifest(addonUrl: String): CheckResult {
        val url = addonUrl.trimEnd('/') + "/manifest.json"
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    return@use CheckResult(
                        name = "Addon manifest",
                        verdict = Verdict.FAIL,
                        summary = "Manifest returned HTTP ${response.code}",
                        fix = "Check the addon URL is correct and the host is online."
                    )
                }
                val body = response.boundedBody()
                if (body == null) {
                    return@use CheckResult(
                        name = "Addon manifest",
                        verdict = Verdict.FAIL,
                        summary = "Manifest response too large (>2 MB)",
                        fix = "The addon returned an unexpectedly large response. " +
                            "Check the addon URL is correct."
                    )
                }
                if (body.isBlank()) {
                    CheckResult(
                        name = "Addon manifest",
                        verdict = Verdict.FAIL,
                        summary = "Manifest returned empty body",
                        fix = "The addon host responded but returned no data. " +
                            "The host may be misconfigured."
                    )
                } else {
                    val parsed = try { json.parseToJsonElement(body).jsonObject } catch (_: Exception) { null }
                    if (parsed == null || !parsed.containsKey("id")) {
                        CheckResult(
                            name = "Addon manifest",
                            verdict = Verdict.FAIL,
                            summary = "Manifest is not valid JSON or missing required fields",
                            fix = "The host returned a response but it is not a valid " +
                                "Stremio addon manifest. Check the addon URL."
                        )
                    } else if (isConfiguredAddon(addonUrl)) {
                        CheckResult(
                            name = "Addon manifest",
                            verdict = Verdict.PASS,
                            summary = "Manifest alive, addon is configured"
                        )
                    } else {
                        CheckResult(
                            name = "Addon manifest",
                            verdict = Verdict.WARN,
                            summary = "Manifest alive but URL looks like a base install, " +
                                "not a configured addon",
                            fix = "Open AIOStreams/Stremio and configure the addon with " +
                                "your credentials before testing."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            CheckResult(
                name = "Addon manifest",
                verdict = Verdict.FAIL,
                summary = "Could not reach manifest: ${e.javaClass.simpleName}",
                fix = "Check your internet connection and verify the addon URL. " +
                    "Error: ${e.message}"
            )
        }
    }

    fun probeStreams(addonUrl: String): CheckResult {
        val url = addonUrl.trimEnd('/') + "/stream/movie/tt0133093.json"
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    return@use CheckResult(
                        name = "Stream probe",
                        verdict = Verdict.FAIL,
                        summary = "Stream endpoint returned HTTP ${response.code}",
                        fix = "The addon is alive but could not return streams. " +
                            "Check that the addon is properly configured."
                    )
                }
                val body = response.boundedBody()
                if (body == null) {
                    return@use CheckResult(
                        name = "Stream probe",
                        verdict = Verdict.FAIL,
                        summary = "Stream response too large (>2 MB)",
                        fix = "The addon returned an unexpectedly large response. " +
                            "Check the addon URL is correct."
                    )
                }
                if (body.isBlank()) {
                    CheckResult(
                        name = "Stream probe",
                        verdict = Verdict.WARN,
                        summary = "Addon responded but returned empty body",
                        fix = "The addon is reachable but returned no data. " +
                            "Your debrid/scraper configuration may need attention."
                    )
                } else {
                    val streams = try {
                        json.parseToJsonElement(body).jsonObject["streams"]?.jsonArray
                    } catch (_: Exception) { null }
                    if (streams != null && streams.isNotEmpty()) {
                        CheckResult(
                            name = "Stream probe",
                            verdict = Verdict.PASS,
                            summary = "Streams returned for test title (The Matrix)"
                        )
                    } else {
                        CheckResult(
                            name = "Stream probe",
                            verdict = Verdict.WARN,
                            summary = "Addon responded but returned no streams for The Matrix",
                            fix = "The addon is reachable but found no results. " +
                                "Your debrid/scraper configuration may need attention."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            CheckResult(
                name = "Stream probe",
                verdict = Verdict.FAIL,
                summary = "Stream probe failed: ${e.javaClass.simpleName}",
                fix = "Could not reach the stream endpoint. Error: ${e.message}"
            )
        }
    }

    internal fun isConfiguredAddon(url: String): Boolean {
        val path = try {
            java.net.URL(url).path
        } catch (_: Exception) {
            return false
        }
        val segments = path.split("/").filter { it.isNotBlank() }
        return segments.any { seg -> seg.length > 20 && !seg.contains(".") }
    }
}
