package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.export.CaptureInfo
import java.util.Locale

/**
 * Identity gate for same-position repeat averaging. Missing identity is a
 * refusal, not a pass: the two captures must both report the sweep's own
 * output route and the same microphone input before their bands are combined.
 */
object RepeatAveragePath {

    data class Capture(
        val outputKind: String?,
        val outputName: String?,
        val outputTrackReported: Boolean,
        val outputNameSpecific: Boolean,
        val info: CaptureInfo?
    )

    fun refusal(first: Capture, second: Capture): String? {
        if (!first.outputTrackReported || !second.outputTrackReported) {
            return "The sweep output route was not reported for both captures."
        }
        val firstKind = usableKind(first.outputKind)
        val secondKind = usableKind(second.outputKind)
        if (firstKind == null || secondKind == null) {
            return "The audio output was not identified for both sweeps."
        }
        if (!firstKind.equals(secondKind, ignoreCase = true)) {
            return "Android reported a different TV audio output for the repeat."
        }
        val firstName = cleaned(first.outputName)
        val secondName = cleaned(second.outputName)
        if (firstName == null || secondName == null) {
            return "The audio output was not named for both sweeps."
        }
        if (!firstName.equals(secondName, ignoreCase = true)) {
            return "Android reported a different named TV audio output for the repeat."
        }
        if (!first.outputNameSpecific || !second.outputNameSpecific) {
            return "The audio output was not specifically named for both sweeps."
        }

        val firstInfo = first.info
        val secondInfo = second.info
        if (firstInfo == null || secondInfo == null) {
            return "The microphone input was not identified for both sweeps."
        }
        val firstType = cleaned(firstInfo.inputDeviceType)
        val secondType = cleaned(secondInfo.inputDeviceType)
        val firstInputName = cleaned(firstInfo.inputDeviceName)
        val secondInputName = cleaned(secondInfo.inputDeviceName)
        if (firstType == null || secondType == null || firstInputName == null || secondInputName == null) {
            return "The microphone input was not identified for both sweeps."
        }
        if (!firstType.equals(secondType, ignoreCase = true)) {
            return "Android reported a different microphone input route for the repeat."
        }
        if (!firstInputName.equals(secondInputName, ignoreCase = true)) {
            return "Android reported a different microphone input device for the repeat."
        }
        return RepeatabilityAnalysis.reportedInputMismatch(firstInfo, secondInfo)
    }

    private fun cleaned(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun usableKind(value: String?): String? {
        val kind = cleaned(value)?.lowercase(Locale.ROOT) ?: return null
        return kind.takeIf { it != "unknown" && it != "ambiguous" && it != "unconfirmed" }
    }
}