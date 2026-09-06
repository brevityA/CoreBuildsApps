package dev.corebuilds.doctor.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SuiteHealthChecks {
    private data class AppProbe(
        val label: String,
        val packageName: String,
        val metadataUrl: String?,
    )

    private const val MAX_METADATA_BYTES = 64L * 1024L

    private val apps = listOf(
        AppProbe("Icon Pack", "tv.corebuilds.iconpack", "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Latestrelease/version.json"),
        AppProbe("Core Line", "dev.corebuilds.line", "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Latestrelease/coreline-version.json"),
        AppProbe("Core Shift", "dev.corebuilds.shift", "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Latestrelease/shift-version.json"),
        AppProbe("Core Motion", "tv.corebuilds.motion", null),
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun check(context: Context): CheckResult {
        val installed = apps.map { probe ->
            val local = packageVersion(context, probe.packageName)
            val remote = probe.metadataUrl?.let { metadataVersion(it) }
            when {
                local == null -> "${probe.label}: not installed"
                remote == null -> "${probe.label}: installed $local"
                remote.startsWith("metadata error") -> "${probe.label}: installed $local; $remote"
                else -> "${probe.label}: installed $local; latest $remote"
            }
        }
        val errors = installed.count { it.contains("metadata error") }
        return CheckResult(
            name = "Core Builds suite health",
            verdict = if (errors == 0) Verdict.PASS else Verdict.WARN,
            summary = installed.joinToString(" | "),
            fix = if (errors == 0) null else "One or more update metadata feeds could not be reached; installed apps continue to work offline."
        )
    }

    private fun packageVersion(context: Context, packageName: String): String? = try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        "${info.versionName ?: "?"} ($code)"
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun metadataVersion(url: String): String {
        return try {
            val req = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(req).execute().use { response ->
                if (response.code !in 200..299) return "metadata error HTTP ${response.code}"
                val source = response.body?.source() ?: return "metadata error empty"
                source.request(MAX_METADATA_BYTES + 1)
                if (source.buffer.size > MAX_METADATA_BYTES) return "metadata error too large"
                val text = source.buffer.readUtf8()
                val code = Regex("\"versionCode\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)
                    ?: return "metadata error missing versionCode"
                val name = Regex("\"versionName\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                    ?: "?"
                "$name ($code)"
            }
        } catch (e: Exception) {
            "metadata error ${e.javaClass.simpleName}"
        }
    }
}
