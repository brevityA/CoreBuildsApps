package tv.corebuilds.eq.export

import tv.corebuilds.eq.dsp.Targets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * What Profiles' Details shows about one saved profile, as labelled
 * lines: where it came from, what it is tied to, what the room measured, and
 * the notes that travel with the curve.
 *
 * Until now this was only readable in a JSON export. Old profiles miss fields
 * that later versions record; a missing field reads "not recorded", never a
 * plausible default and never "invalid". Repeat agreement is shown apart from
 * recording quality: two captures that agree say nothing about whether either
 * was a good measurement.
 *
 * Android-free so it is unit-tested (`ProfileDetailsTest`). [outputLabel]
 * turns an output kind into words ("TV speakers"); the screen passes
 * OutputRoute.label.
 */
object ProfileDetails {

    data class Line(val label: String, val value: String)

    const val NOT_RECORDED = "not recorded"

    fun lines(
        profile: Profile,
        outputLabel: (String?) -> String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<Line> = buildList {
        add(Line("Kind", kindOf(profile)))
        add(Line(if (profile.manualOnly) "Created" else "Measured", date(profile.timestampMs, timeZone)))
        add(Line("Output", output(profile, outputLabel)))
        add(Line("Target", if (profile.manualOnly) "none (manual EQ only)" else Targets.label(profile.target)))
        if (!profile.manualOnly) {
            add(Line("Measured on", profile.deviceName.ifBlank { NOT_RECORDED }))
            add(Line("Microphone", profile.micType.ifBlank { NOT_RECORDED }))
            add(Line("Capture", profile.captureInfo?.summary() ?: NOT_RECORDED))
            add(Line("Room volume", profile.volumeM3?.let { fmt("%.0f m³", it) } ?: "not entered"))
            add(Line("Reverberation (RT60)", profile.rt60Seconds?.let { fmt("%.2f s", it) } ?: NOT_RECORDED))
            add(Line("Schroeder frequency", profile.schroederHz?.let { fmt("%.0f Hz", it) } ?: NOT_RECORDED))
            add(Line("Signal-to-noise", profile.snrDb?.let { fmt("%.0f dB", it) } ?: NOT_RECORDED))
            add(
                Line(
                    "Corrected up to",
                    profile.correctionCeilingHz?.let { fmt("%.0f Hz", it) } ?: "the full band (older profile: ceiling not recorded)"
                )
            )
            add(Line("Repeat at same spot", repeat(profile)))
        }
        add(Line("Manual EQ", if (profile.manualFilters.isEmpty()) "none" else "${profile.manualFilters.size} filter(s)"))
        // Provenance: what each build did, recorded when the profile was created;
        // older profiles say "not recorded" rather than being guessed.
        addAll(originLines(profile.origin))
        add(Line("Last saved with", profile.lastSavedBy?.label() ?: NOT_RECORDED))
        for (note in profile.measurementNotes) add(Line("Note", note))
        profile.repeatabilityCheck?.note?.takeIf { it.isNotBlank() }?.let { add(Line("Repeat note", it)) }
    }

    /** The lines as plain text, one "Label: value" per line, for the dialog and diagnostics. */
    fun text(lines: List<Line>): String = lines.joinToString("\n") { "${it.label}: ${it.value}" }

    /**
     * The origin as Details lines. Each line says which build did one thing;
     * nothing a build did not do is attributed to it, and what is not known
     * says so.
     */
    fun originLines(origin: ProfileOrigin?): List<Line> {
        if (origin == null) return listOf(Line("Origin", NOT_RECORDED))
        val built = origin.createdBy.label()
        return originBody(origin, built) + Line(
            "Origin record",
            "as saved with the profile, not verified; an imported file's record is what the file says"
        )
    }

    private fun originBody(origin: ProfileOrigin, built: String): List<Line> {
        return when (origin.kind) {
            ProfileOrigin.TV_MEASUREMENT -> listOf(
                Line("Origin", "TV measurement: the TV played and recorded the sweep"),
                Line("Sweep recorded with", origin.capturedBy?.label() ?: NOT_RECORDED),
                Line("Analysed with", origin.analysedBy?.label() ?: NOT_RECORDED),
                Line("Profile created with", built)
            )
            ProfileOrigin.PHONE_MEASUREMENT -> listOf(
                Line("Origin", "phone measurement: the phone recorded the sweep, the TV analysed it"),
                Line("Sweep recorded with", origin.capturedBy?.label() ?: "phone's Core EQ build not reported"),
                Line("Analysed with", origin.analysedBy?.label() ?: NOT_RECORDED),
                Line("Profile created with", built)
            )
            ProfileOrigin.REW_IMPORT -> listOf(
                Line("Origin", "imported REW measurement file"),
                Line("Measured with", "outside Core EQ; the software and its version are not recorded"),
                Line("Imported with", built)
            )
            ProfileOrigin.MANUAL -> listOf(
                Line("Origin", "manual EQ, nothing measured"),
                Line("Created with", built)
            )
            else -> listOf(
                Line("Origin", "\"${origin.kind}\", recorded by a build newer than this one"),
                Line("Profile created with", built)
            )
        }
    }

    /** What kind of profile this is, in words; diagnostics reuse it without the name. */
    fun kindOf(profile: Profile): String = when {
        profile.manualOnly -> "manual profile (no room measurement)"
        profile.stimulus == "rew_import" -> "imported measurement (REW)"
        else -> "room measurement"
    }

    private fun output(profile: Profile, outputLabel: (String?) -> String): String {
        val kind = profile.outputKind
            ?: return "$NOT_RECORDED (saved before outputs were recorded; offered on any output)"
        val name = profile.outputName?.takeIf { it.isNotBlank() }
        val label = outputLabel(kind)
        return if (name != null && !name.equals(label, ignoreCase = true)) "$label · $name" else label
    }

    /**
     * The repeat's agreement, never a grade. An accepted same-position average
     * is recorded in the measurement notes, which follow as their own lines.
     */
    private fun repeat(profile: Profile): String {
        val check = profile.repeatabilityCheck ?: return "none"
        val m = check.metrics ?: return "captured, not compared (see the repeat note)"
        return fmt(
            "%d bands compared · RMS difference %.1f dB · level offset %+.1f dB (agreement only, not a quality grade)",
            m.comparedBands, m.rmsDifferenceDb, m.levelOffsetDb
        )
    }

    private fun date(ms: Long, zone: TimeZone): String =
        if (ms <= 0L) NOT_RECORDED
        else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }.format(Date(ms))

    private fun fmt(pattern: String, vararg args: Any): String = String.format(Locale.US, pattern, *args)
}
