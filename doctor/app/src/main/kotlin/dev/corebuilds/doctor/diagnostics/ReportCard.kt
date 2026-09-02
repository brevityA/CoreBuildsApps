package dev.corebuilds.doctor.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportCard {

    fun render(report: DoctorReport): String {
        val counts = report.checks.groupingBy { it.verdict }.eachCount()
        val pass = counts[Verdict.PASS] ?: 0
        val warn = counts[Verdict.WARN] ?: 0
        val fail = counts[Verdict.FAIL] ?: 0
        val total = report.checks.size

        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
            .format(Date(report.timestamp))

        return buildString {
            appendLine("Core Doctor Report")
            appendLine("==================")
            appendLine()
            appendLine("$total checks: $pass passed, $warn warnings, $fail failed")
            appendLine()

            for (result in report.checks) {
                val icon = when (result.verdict) {
                    Verdict.PASS -> "[PASS]"
                    Verdict.WARN -> "[WARN]"
                    Verdict.FAIL -> "[FAIL]"
                }
                appendLine("$icon ${result.name}")
                appendLine("      ${result.summary}")
                if (result.fix != null) {
                    appendLine("      Fix: ${result.fix}")
                }
                appendLine()
            }

            appendLine("--")
            appendLine("Generated: $ts")
            appendLine("No keys or URLs included in this report.")
        }
    }
}
