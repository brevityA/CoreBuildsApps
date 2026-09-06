package dev.corebuilds.doctor.diagnostics

/** Privacy boundary for shareable Doctor output. */
object Redactor {
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{8,}")
    private val longToken = Regex("(?i)(api[_-]?key|token|authorization|password|secret)(=|:|%3D)\\s*[^\\s&]+")
    private val querySecret = Regex("(?i)([?&](?:api[_-]?key|token|auth|apikey)=)[^&\\s]+")
    private val credentialUrl = Regex("(?i)(https?://)[^/@\\s:]+:[^/@\\s]+@")

    fun clean(value: String?): String = value.orEmpty()
        .replace(bearer, "Bearer [REDACTED]")
        .replace(longToken) { match -> match.groupValues[1] + match.groupValues[2] + "[REDACTED]" }
        .replace(querySecret) { match -> match.groupValues[1] + "[REDACTED]" }
        .replace(credentialUrl) { match -> match.groupValues[1] + "[REDACTED]@" }
        .take(500)
}
