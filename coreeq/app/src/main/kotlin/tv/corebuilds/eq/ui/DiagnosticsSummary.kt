package tv.corebuilds.eq.ui

/**
 * The text a viewer can preview, then copy or show as a QR code, from
 * Capability, for a bug report or a beta question. It is built only
 * when asked for, shown in full before anything leaves the screen, and never
 * sent anywhere by Core EQ.
 *
 * Never read into it: recordings, curves and filter values, the phone pairing
 * token and keys, account details and serial numbers. The structured lines
 * carry kinds ("room measurement", "TV speakers"), never a profile or output
 * name. The free-text lines (Home's applied line, the last service message,
 * the last measurement message) are written by the service and can carry
 * names, so [clean] masks every name Core EQ knows ([Facts.knownNames]: a
 * profile is often named after a Bluetooth device, and a Bluetooth name is
 * often a person's), network and hardware addresses and long hex ids. A name
 * Core EQ was never told cannot be recognised, which is why the header says
 * to read the preview. The device line is the maker and model Android
 * reports, which name a kind of TV, not a unit.
 *
 * Android-free so its contents are unit-tested (`DiagnosticsSummaryTest`).
 */
object DiagnosticsSummary {

    data class Measurement(val outcome: String, val stage: String, val message: String, val ageSeconds: Long?)

    data class Facts(
        val appVersion: String,
        val versionCode: Int,
        val buildType: String,
        val android: String,
        val sdkInt: Int,
        val device: String,
        /** "room measurement", "manual profile …"; null when no profile applies or is chosen. */
        val profileKind: String?,
        /** The output the profile was saved for, as a kind label ("TV speakers"). */
        val profileOutput: String?,
        val profileAgeSeconds: Long?,
        /** "measured with Core EQ 1.14.0 (30)", or null when the profile's origin was not recorded. */
        val profileOrigin: String? = null,
        /** The output in use as a kind label, and how Android told us. */
        val outputInUse: String?,
        val outputEvidence: String?,
        val home: HomeSummary.Summary,
        val lastStatus: String?,
        val lastStatusAgeSeconds: Long?,
        val lastStatusIsError: Boolean,
        val measurement: Measurement?,
        val backgroundWork: String,
        /**
         * Names Core EQ knows and that service and measurement messages can carry:
         * saved profile names and the output names Android reported. Masked as
         * "[name]" wherever they appear in a free-text line.
         */
        val knownNames: List<String> = emptyList()
    )

    /**
     * What the text leaves out, and no more than that. Recordings, curves and pairing
     * keys are never read. Names are masked only where Core EQ knows them (saved
     * profiles and reported outputs); a name it has never seen cannot be recognised,
     * which is why the preview is shown before anything is copied.
     */
    const val HEADER = "Core EQ diagnostics · no recordings, curves or pairing keys · known profile and output names, addresses and long ids masked · read before sharing"
    private const val MAX_FREE_TEXT = 240
    /** Shorter names ("TV", "A") would mask ordinary words. */
    private const val MIN_NAME_CHARS = 3

    fun text(f: Facts): String = buildString {
        appendLine(HEADER)
        appendLine("App: ${f.appVersion} (${f.versionCode}, ${f.buildType})")
        appendLine("Android: ${f.android} (API ${f.sdkInt}) · ${clean(f.device)}")
        appendLine("Output in use: ${f.outputInUse?.let { o -> f.outputEvidence?.let { "$o ($it)" } ?: o } ?: "not identified by Android"}")
        appendLine(
            "Profile: " + if (f.profileKind == null) {
                "none applies or is chosen"
            } else {
                "${f.profileKind} · for ${f.profileOutput ?: "any output"}" +
                    (f.profileAgeSeconds?.let { " · saved ${ago(it)}" } ?: "") +
                    " · " + (f.profileOrigin ?: "origin not recorded")
            }
        )
        appendLine("State: ${f.home.state.name.lowercase().replace('_', ' ')}")
        appendLine(clean(f.home.applied, f.knownNames))
        appendLine(f.home.listening)
        val status = f.lastStatus?.takeIf { it.isNotBlank() }
        appendLine(
            if (status == null) {
                "Last service message: none recorded"
            } else {
                "Last service message" + (if (f.lastStatusIsError) " (error)" else "") +
                    (f.lastStatusAgeSeconds?.let { ", ${ago(it)}" } ?: "") + ": " + clean(status, f.knownNames)
            }
        )
        val m = f.measurement
        appendLine(
            if (m == null) {
                "Last measurement: none recorded"
            } else {
                "Last measurement: ${m.outcome}" + (if (m.stage.isNotBlank()) " (${m.stage})" else "") +
                    (m.ageSeconds?.let { ", ${ago(it)}" } ?: "") +
                    (m.message.takeIf { it.isNotBlank() }?.let { ": ${clean(it, f.knownNames)}" } ?: "")
            }
        )
        append("Background work since start: ").append(f.backgroundWork)
    }

    /** "45 s ago", "12 min ago", "3 h ago", "2 d ago". */
    fun ago(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return when {
            s < 90 -> "$s s ago"
            s < 90 * 60 -> "${(s + 30) / 60} min ago"
            s < 36 * 3600 -> "${(s + 1800) / 3600} h ago"
            else -> "${(s + 43200) / 86400} d ago"
        }
    }

    private val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b""")
    private val IPV6 = Regex("""\b(?:[0-9A-Fa-f]{1,4}:){3,7}[0-9A-Fa-f]{1,4}\b""")
    private val MAC = Regex("""\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b""")
    private val HEX_ID = Regex("""\b[0-9A-Fa-f]{16,}\b""")
    private val SPACE = Regex("""\s+""")

    /** One line, bounded, with [names], addresses and long hex ids masked. */
    fun clean(text: String, names: List<String> = emptyList()): String {
        var t = text
        // Longest first, so "Living room 2" is masked whole rather than leaving " 2".
        for (name in names.map { it.trim() }.filter { it.length >= MIN_NAME_CHARS }.distinct().sortedByDescending { it.length }) {
            t = Regex(Regex.escape(name), RegexOption.IGNORE_CASE).replace(t, "[name]")
        }
        t = MAC.replace(t, "[address]")
        t = IPV6.replace(t, "[address]")
        t = IPV4.replace(t, "[address]")
        t = HEX_ID.replace(t, "[id]")
        t = SPACE.replace(t, " ").trim()
        return if (t.length > MAX_FREE_TEXT) t.take(MAX_FREE_TEXT - 1).trimEnd() + "…" else t
    }
}
