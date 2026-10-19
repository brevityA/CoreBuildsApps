package tv.corebuilds.eq.apply

import tv.corebuilds.eq.dsp.DeviceIdentity
import tv.corebuilds.eq.dsp.HardwarePresets

/**
 * The setup check: what Core EQ can see and the exact, optional ADB steps for
 * anything Android does not let it grant itself. This screen never runs ADB.
 *
 * Android TV's ADB workflow depends on the TV OS: legacy Network debugging
 * uses the TCP port the TV exposes (often 5555); Wireless debugging with a
 * pairing code is supported on Android TV 13+ and uses separate, dynamic pair
 * and connect ports. The screen keeps those paths separate rather than
 * promising that 5555 works on every TV.
 *
 * Pure Kotlin on purpose: the decisions are unit-tested without Android.
 */
object SetupGuide {

    const val PACKAGE = "tv.corebuilds.eq"

    /** Classic ADB-over-TCP default only; use the port displayed by the TV if different. */
    const val CLASSIC_ADB_PORT = 5555

    val connectTemplate: String = "adb connect <TV-IP>:$CLASSIC_ADB_PORT"
    val wirelessPairTemplate: String = "adb pair <TV-IP>:<PAIRING-PORT>"
    val wirelessConnectTemplate: String = "adb connect <TV-IP>:<CONNECT-PORT>"
    val deviceListCommand: String = "adb devices -l"
    val dumpCommand: String = "adb shell pm grant $PACKAGE android.permission.DUMP"
    val overlayCommand: String = "adb shell appops set $PACKAGE SYSTEM_ALERT_WINDOW allow"
    val logcatCommand: String = "adb logcat -d -t 500 -s CoreEqCapture CoreEqStimulus CoreEqService CoreEqProfiles"

    enum class State { DONE, NEEDED, INFO }

    /** One row on the screen. [command] is what its Copy button puts on the clipboard. */
    data class Step(
        val id: String,
        val title: String,
        val state: State,
        val detail: String,
        val command: String?
    )

    /**
     * Checklist for the current state.
     *
     * - [dumpGranted]: `android.permission.DUMP` is granted (see DumpsysDiscovery.hasGrant).
     * - [overlayAllowed]: "Display over other apps" is allowed (see OverlayPermission.allowed).
     * - [outputKind] and [outputName]: current Android route (see OutputRoute.current).
     * - [hardware]: model matched from that route, or null (see HardwarePresets.detect).
     * - [detection]: TV/output brand detection, or null to omit that row.
     * - [measured]: whether this output has a profile, or null to omit that row.
     * - [passthroughRisk]: HDMI may bypass Android's mixer; it is not proof of bitstream playback.
     * - [spatialDetail]: read-only Android spatializer status, or null to omit the row.
     */
    fun steps(
        dumpGranted: Boolean,
        overlayAllowed: Boolean,
        outputKind: String?,
        outputName: String?,
        hardware: HardwarePresets.Model?,
        detection: DeviceIdentity.Detection? = null,
        measured: Boolean? = null,
        passthroughRisk: Boolean = false,
        spatialDetail: String? = null
    ): List<Step> = buildList {
        add(
            Step(
                id = "connect",
                title = "Classic Network debugging",
                state = State.INFO,
                detail = "On the TV, enable Network debugging in Developer options. On the same network, replace <TV-IP> and use the TCP port shown by the TV (often 5555). This is not the Wireless debugging pairing port.",
                command = connectTemplate
            )
        )
        add(
            Step(
                id = "wireless-pair",
                title = "Wireless debugging · pair",
                state = State.INFO,
                detail = "On Android TV 13+, open Developer options → Wireless debugging → Pair device with pairing code. Copy the pairing IP and pairing port into this command; enter the six-digit code only when ADB prompts. Never include that code in a report.",
                command = wirelessPairTemplate
            )
        )
        add(
            Step(
                id = "wireless-connect",
                title = "Wireless debugging · connect",
                state = State.INFO,
                detail = "After pairing, use the separate connect port shown on the main Wireless debugging screen (not the pairing-dialog port). Some ADB tools discover the paired TV automatically; otherwise run this command.",
                command = wirelessConnectTemplate
            )
        )
        add(
            Step(
                id = "adb-check",
                title = "Verify the computer is connected",
                state = State.INFO,
                detail = "Run after connecting. The TV should appear as a device, not unauthorized or offline. If several TVs are connected, use the serial shown here with adb -s for later commands.",
                command = deviceListCommand
            )
        )
        add(
            if (dumpGranted) {
                Step("dump", "Read the playing app (DUMP)", State.DONE, "Granted. Core EQ can try to identify the playing app; Android may still limit what the system reveals.", null)
            } else {
                Step(
                    "dump",
                    "Read the playing app (DUMP)",
                    State.NEEDED,
                    "Not granted. DUMP is a protected permission and Android documents it as unavailable to third-party apps; some TVs refuse this command. Optional app switching works only if the TV accepts it.",
                    dumpCommand
                )
            }
        )
        add(
            if (overlayAllowed) {
                Step("overlay", "Show the on-screen card", State.DONE, "Allowed. The applied-status card can show over other apps.", null)
            } else {
                Step(
                    "overlay",
                    "Show the on-screen card",
                    State.NEEDED,
                    "Not allowed. Turn on Display over other apps, or use this command on a TV with no permission screen.",
                    overlayCommand
                )
            }
        )
        add(
            Step(
                id = "adb-security",
                title = "Turn debugging off when finished",
                state = State.INFO,
                detail = "ADB gives a trusted computer powerful access to the TV. When setup is done, turn off Wireless debugging or Network debugging in Developer options. Pairing codes are temporary secrets; do not share them.",
                command = null
            )
        )
        add(
            Step(
                id = "logs",
                title = "Collect recent Core EQ logs (optional)",
                state = State.INFO,
                detail = "After reproducing a problem, run the copied command from the connected computer. It filters to Core EQ audio, service and profile tags; review the output before sharing it.",
                command = logcatCommand
            )
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
        if (passthroughRisk) {
            add(
                Step(
                    id = "passthrough",
                    title = "Check the TV's surround setting",
                    state = State.INFO,
                    detail = "HDMI may carry Dolby or DTS as a bitstream, and Core EQ cannot process that. On the TV, set Surround sound (or Audio format) to PCM. Core EQ cannot see the current stream format, so this is a check, not a missing step.",
                    command = null
                )
            )
        }
        detection?.let { add(deviceRow(it)) }
        spatialDetail?.let {
            add(Step("spatial", "Spatial audio (read-only)", State.INFO, it, null))
        }
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

    /** Plain-text support report. [deviceDetails] is supplied only on explicit Copy report. */
    fun report(steps: List<Step>, deviceDetails: List<String> = emptyList()): String = buildString {
        append("Core EQ setup check\n")
        append("Privacy: no IP address, serial number, pairing code or microphone audio is included. Review before sharing.\n")
        if (deviceDetails.isNotEmpty()) {
            append("Device and app\n")
            deviceDetails.forEach { detail -> append("  ").append(detail).append('\n') }
        }
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
