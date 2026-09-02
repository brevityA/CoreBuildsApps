package dev.corebuilds.doctor.diagnostics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

object DebridChecks {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun checkRealDebrid(apiKey: String): CheckResult {
        val url = "https://api.real-debrid.com/rest/1.0/user"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                when {
                    response.code == 401 || response.code == 403 ->
                        CheckResult(
                            name = "Real-Debrid",
                            verdict = Verdict.FAIL,
                            summary = "API key rejected (HTTP ${response.code})",
                            fix = "Your Real-Debrid API key is invalid or expired. " +
                                "Generate a new one at real-debrid.com/apitoken"
                        )
                    response.code != 200 ->
                        CheckResult(
                            name = "Real-Debrid",
                            verdict = Verdict.FAIL,
                            summary = "Real-Debrid returned HTTP ${response.code}",
                            fix = "Unexpected response. The service may be temporarily down."
                        )
                    else -> parseRdUser(body)
                }
            }
        } catch (e: Exception) {
            CheckResult(
                name = "Real-Debrid",
                verdict = Verdict.FAIL,
                summary = "Could not reach Real-Debrid: ${e.javaClass.simpleName}",
                fix = "Check your internet connection. Error: ${e.message}"
            )
        }
    }

    private fun parseRdUser(body: String): CheckResult {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: "unknown"
            val expiration = obj["expiration"]?.jsonPrimitive?.content

            val isPremium = type == "premium"
            val daysLeft = try {
                if (expiration != null) {
                    ChronoUnit.DAYS.between(Instant.now(), Instant.parse(expiration))
                } else null
            } catch (_: Exception) { null }

            when {
                !isPremium -> CheckResult(
                    name = "Real-Debrid",
                    verdict = Verdict.WARN,
                    summary = "Account active but not premium (type: $type)",
                    fix = "A premium subscription is required for cached torrent streaming. " +
                        "Renew at real-debrid.com/premium"
                )
                daysLeft != null && daysLeft < 3 -> CheckResult(
                    name = "Real-Debrid",
                    verdict = Verdict.WARN,
                    summary = "Premium active, expires in $daysLeft day(s)",
                    fix = "Your subscription expires soon. Renew at real-debrid.com/premium"
                )
                else -> {
                    val extra = if (daysLeft != null) " ($daysLeft days remaining)" else ""
                    CheckResult(
                        name = "Real-Debrid",
                        verdict = Verdict.PASS,
                        summary = "Premium active$extra"
                    )
                }
            }
        } catch (_: Exception) {
            CheckResult(
                name = "Real-Debrid",
                verdict = Verdict.WARN,
                summary = "Key accepted but response could not be parsed",
                fix = "Real-Debrid accepted the key (good sign) but returned " +
                    "an unexpected format."
            )
        }
    }

    fun checkTorBox(apiKey: String): CheckResult {
        val url = "https://api.torbox.app/v1/api/user/me"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                when {
                    response.code == 401 || response.code == 403 ->
                        CheckResult(
                            name = "TorBox",
                            verdict = Verdict.FAIL,
                            summary = "API key rejected (HTTP ${response.code})",
                            fix = "Your TorBox API key is invalid or expired. " +
                                "Generate a new one at torbox.app/settings"
                        )
                    response.code != 200 ->
                        CheckResult(
                            name = "TorBox",
                            verdict = Verdict.FAIL,
                            summary = "TorBox returned HTTP ${response.code}",
                            fix = "Unexpected response. The service may be temporarily down."
                        )
                    else -> parseTorBoxUser(body)
                }
            }
        } catch (e: Exception) {
            CheckResult(
                name = "TorBox",
                verdict = Verdict.FAIL,
                summary = "Could not reach TorBox: ${e.javaClass.simpleName}",
                fix = "Check your internet connection. Error: ${e.message}"
            )
        }
    }

    private fun parseTorBoxUser(body: String): CheckResult {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val success = obj["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!success) {
                return CheckResult(
                    name = "TorBox",
                    verdict = Verdict.FAIL,
                    summary = "TorBox returned success=false",
                    fix = "The API key may be invalid. Generate a new one at torbox.app/settings"
                )
            }
            val data = obj["data"]?.jsonObject
            val plan = data?.get("plan")?.jsonPrimitive?.intOrNull ?: 0
            val premiumEnd = data?.get("premium_expires_at")?.jsonPrimitive?.content

            val isPremium = plan > 0
            val daysLeft = try {
                if (premiumEnd != null) {
                    ChronoUnit.DAYS.between(Instant.now(), Instant.parse(premiumEnd))
                } else null
            } catch (_: Exception) { null }

            when {
                !isPremium -> CheckResult(
                    name = "TorBox",
                    verdict = Verdict.WARN,
                    summary = "Account active but on free plan",
                    fix = "A paid plan is required for full streaming. " +
                        "Upgrade at torbox.app/subscription"
                )
                daysLeft != null && daysLeft < 3 -> CheckResult(
                    name = "TorBox",
                    verdict = Verdict.WARN,
                    summary = "Premium active, expires in $daysLeft day(s)",
                    fix = "Your subscription expires soon. Renew at torbox.app/subscription"
                )
                else -> {
                    val extra = if (daysLeft != null) " ($daysLeft days remaining)" else ""
                    CheckResult(
                        name = "TorBox",
                        verdict = Verdict.PASS,
                        summary = "Premium active$extra"
                    )
                }
            }
        } catch (_: Exception) {
            CheckResult(
                name = "TorBox",
                verdict = Verdict.WARN,
                summary = "Key accepted but response could not be parsed",
                fix = "TorBox accepted the key (good sign) but returned " +
                    "an unexpected format."
            )
        }
    }
}
