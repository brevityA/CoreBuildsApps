package dev.corebuilds.doctor.diagnostics

enum class Verdict { PASS, WARN, FAIL }

data class CheckResult(
    val name: String,
    val verdict: Verdict,
    val summary: String,
    val fix: String? = null
)

data class DoctorInput(
    val addonUrl: String = "",
    val rdKey: String = "",
    val torboxKey: String = ""
)

data class DoctorReport(
    val checks: List<CheckResult> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
