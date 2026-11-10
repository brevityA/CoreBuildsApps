package tv.corebuilds.eq.dsp

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import tv.corebuilds.eq.export.CaptureInfo

class RepeatAveragePathTest {
    private fun capture(
        kind: String? = "speaker",
        name: String? = "TV speakers",
        trackReported: Boolean = true,
        nameSpecific: Boolean = true,
        info: CaptureInfo? = CaptureInfo(inputDeviceType = "BUILTIN_MIC", inputDeviceName = "Remote mic")
    ) = RepeatAveragePath.Capture(kind, name, trackReported, nameSpecific, info)

    @Test fun identifiedSamePathIsAllowed() {
        assertNull(RepeatAveragePath.refusal(capture(), capture()))
    }

    @Test fun outputMustBeTrackReportedForBothSweeps() {
        assertNotNull(RepeatAveragePath.refusal(capture(trackReported = false), capture()))
        assertNotNull(RepeatAveragePath.refusal(capture(), capture(trackReported = false)))
    }

    @Test fun unknownAmbiguousOrUnconfirmedOutputIsRefused() {
        for (kind in listOf(null, "", "unknown", "ambiguous", "unconfirmed")) {
            assertNotNull(RepeatAveragePath.refusal(capture(kind = kind), capture()))
        }
    }

    @Test fun unnamedOrDifferentOutputIsRefused() {
        assertNotNull(RepeatAveragePath.refusal(capture(name = null), capture()))
        assertNotNull(RepeatAveragePath.refusal(capture(name = " "), capture()))
        assertNotNull(RepeatAveragePath.refusal(capture(kind = "speaker"), capture(kind = "hdmi_arc")))
        assertNotNull(RepeatAveragePath.refusal(capture(name = "TV speakers"), capture(name = "Soundbar")))
    }

    @Test fun genericExternalOutputNameIsRefused() {
        assertNotNull(RepeatAveragePath.refusal(
            capture(kind = "hdmi_arc", name = "HDMI ARC (soundbar or receiver)", nameSpecific = false),
            capture(kind = "hdmi_arc", name = "HDMI ARC (soundbar or receiver)", nameSpecific = false)
        ))
    }

    @Test fun missingInputIdentityIsRefused() {
        assertNotNull(RepeatAveragePath.refusal(capture(info = null), capture()))
        assertNotNull(RepeatAveragePath.refusal(capture(info = CaptureInfo()), capture()))
        assertNotNull(RepeatAveragePath.refusal(capture(info = CaptureInfo(inputDeviceType = "BUILTIN_MIC")), capture()))
        assertNotNull(RepeatAveragePath.refusal(capture(info = CaptureInfo(inputDeviceName = "Remote mic")), capture()))
    }

    @Test fun differentInputIsRefused() {
        assertNotNull(RepeatAveragePath.refusal(
            capture(info = CaptureInfo(inputDeviceType = "BUILTIN_MIC", inputDeviceName = "Remote mic")),
            capture(info = CaptureInfo(inputDeviceType = "USB_DEVICE", inputDeviceName = "Remote mic"))
        ))
        assertNotNull(RepeatAveragePath.refusal(
            capture(info = CaptureInfo(inputDeviceType = "BUILTIN_MIC", inputDeviceName = "Remote mic")),
            capture(info = CaptureInfo(inputDeviceType = "BUILTIN_MIC", inputDeviceName = "Other mic"))
        ))
    }

    @Test fun changedRecorderSourceOrRatesAreRefused() {
        val base = CaptureInfo(inputDeviceType = "BUILTIN_MIC", inputDeviceName = "Remote mic")
        assertNotNull(RepeatAveragePath.refusal(
            capture(info = base.copy(captureDeviceModel = "TV A")),
            capture(info = base.copy(captureDeviceModel = "TV B"))
        ))
        assertNotNull(RepeatAveragePath.refusal(
            capture(info = base.copy(audioSource = "VOICE_RECOGNITION")),
            capture(info = base.copy(audioSource = "MIC"))
        ))
        assertNotNull(RepeatAveragePath.refusal(
            capture(info = base.copy(inputSampleRateHz = 48000)),
            capture(info = base.copy(inputSampleRateHz = 44100))
        ))
        assertNotNull(RepeatAveragePath.refusal(
            capture(info = base.copy(analysisSampleRateHz = 16000)),
            capture(info = base.copy(analysisSampleRateHz = 48000))
        ))
    }
}