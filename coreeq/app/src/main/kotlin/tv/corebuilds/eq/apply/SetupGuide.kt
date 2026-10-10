package tv.corebuilds.eq.apply

import tv.corebuilds.eq.dsp.DeviceIdentity
import tv.corebuilds.eq.dsp.HardwarePresets

/**
 * The one-tap setup check (1.4.0): what Core EQ can see about itself, and the
 * exact ADB line for anything it cannot grant itself.
 *
 * Android does not let an app grant itself DUMP or run `pm grant`; only a
 * shell with ADB can. So this does not run ADB. It reads the state Core EQ can
 * see, says which step is still needed, and gives the command to copy. The
 * commands name this package and no other, and [connectTemplate] keeps its
 * `<TV-IP>` placeholder so nobody runs it unedited.
 *
 * Pure Kotlin on purpose: the decisions are unit-tested without Android.
 */
object SetupGuide {

    const val PACKAGE = "tv.corebuilds.eq"

    /** Network ADB port on Android TV, and the one the connect line uses. */
    const val ADB_PORT = 5555

    /** Connect from a computer on the same network; the TV IP is under Settings › Network. */
    val connectTemplate: String = "adb connect <TV-IP>:$ADB_PORT"

    val dumpCommand: String = "adb shell pm grant $PACKAGE android.permission.DUMP"

    val overlayCommand: String = "adb shell appops set $PACKAGE SYSTEM_ALERT_WINDOW allow"

    enum class State { DONE, NEEDED, INFO }

    /**
     * One row on the screen. [command] is what the Copy button puts on the
     * clipboard, or null when there is nothing to copy. [detail] is plain text.
     */
    data class Step(
        val id: String,
        val title: String,
        val state: State,
        val detail: String,
        val command: String?
    )

    /**
     * The checklist for the current state.
     *
     * - [dumpGranted]: `android.permission.DUMP` is granted (see DumpsysDiscovery.hasGrant).
     * - [overlayAllowed]: "Display over other apps" is allowed (see OverlayPermission.allowed).
     * - [outputKind] and [outputName]: the output the sound is routed to, from OutputRoute.current.
     * - [hardware]: the model that output matches, or null (see HardwarePresets.detect).
     * - [detection]: the TV and output brands (see [DeviceIdentity]), or null to omit the row.
     * - [measured]: whether this output has a measurement, or null to omit the row.
     */
    fun steps(
        dumpGranted: Boolean,
        overlayAllowed: Boolean,
        outputKind: String?,
        outputName: String?,
        hardware: HardwarePresets.Model?,
        detection: DeviceIdentity.Detection? = null,
        measured: Boolean? = null
    ): List<Step> = buildList {
        add(
            Step(
                id = "connect",
                title = "Connect ADB from a computer",
                state = State.INFO,
                detail = "Turn on Network debugging in Developer options, then run this from a computer on the same network. Replace <TV-IP> with the TV's address.",
                command = connectTemplate
            )
        )
        add(
            if (dumpGranted) {
                Step("dump", "Read the playing app (DUMP)", State.DONE, "Granted. Core EQ can tell which app is playing.", null)
            } else {
                Step(
                    "dump",
                    "Read the playing app (DUMP)",
                    State.NEEDED,
                    "Not granted. Android documents DUMP as not for third-party apps, so some TVs refuse it. Run this once over ADB.",
                    dumpCommand
                )
            }
        )
        add(
            if (overlayAllowed) {
                Step("overlay", "Show the on-screen card", State.DONE, "Allowed. The card can show over other apps.", null)
            } else {
                Step(
                    "overlay",
                    "Show the on-screen card",
                    State.NEEDED,
                    "Not allowed. Turn on Display over other apps, or run this once over ADB on a TV with no such screen.",
                    overlayCommand
                )
            }
        )
        add(
            Step(
                id = "output",
                title = "Sound output",
                state = State.INFO,
                detail = outputDetail(outputKind, outputName),
                command = null
            )
        )
        detection?.let { add(deviceRow(it)) }
        if (measured == false) {
            add(
                Step(
                    id = "measure",
                    title = "Measure this output",
                    state = State.INFO,
                    detail = "No measurement for this output yet. Run Measure: it is the real calibration. Brand and model presets are only starting points.",
                    command = null
                )
            )
        }
        if (hardware != null) {
            add(
                Step(
                    id = "hardware",
                    title = "Detected: ${hardware.name}",
                    state = State.INFO,
                    detail = "Its presets are listed first in Manual EQ. They are a starting point, not measured; the room correction still applies.",
                    command = null
                )
            )
        }
    }

    /** Plain text for the Copy report button: the same rows, readable pasted anywhere. */
    fun report(steps: List<Step>): String = buildString {
        append("Core EQ setup check\n")
        for (step in steps) {
            val tag = when (step.state) {
                State.DONE -> "[done]"
                State.NEEDED -> "[needed]"
                State.INFO -> "[info]"
            }
            append(tag).append(' ').append(step.title).append('\n')
            append("    ").append(step.detail).append('\n')
            step.command?.let { append("    ").append(it).append('\n') }
        }
    }

    private fun deviceRow(detection: DeviceIdentity.Detection): Step {
        val lines = mutableListOf<String>()
        val tv = detection.tvBrand
        lines += if (tv != null) "TV: ${tv.name}." else "TV: brand not recognised."
        when {
            detection.ambiguous -> lines += "The output name matches the TV's own brand, so it may be the TV itself. Core EQ is not guessing a soundbar."
            detection.outputBrand != null -> lines += "Output: ${detection.outputBrand.name}."
            else -> lines += "Output: no brand in its name."
        }
        return Step("device", "Detected devices", State.INFO, lines.joinToString(" "), null)
    }

    private fun outputDetail(kind: String?, name: String?): String {
        if (kind == null) return "Android named no output Core EQ recognises."
        val label = OutputRoute.label(kind)
        val named = name?.trim()?.takeIf { it.isNotEmpty() && !it.equals(label, ignoreCase = true) }
        return if (named == null) "Routed to $label." else "Routed to $label: $named."
    }
}
