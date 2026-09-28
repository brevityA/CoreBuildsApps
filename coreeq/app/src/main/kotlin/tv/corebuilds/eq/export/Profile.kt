package tv.corebuilds.eq.export

import tv.corebuilds.eq.dsp.PeakingFilter

data class PlatformBand(
    val centerHz: Double,
    val millibels: Int
)

data class CurvePoint(
    val hz: Double,
    val measuredDb: Double,
    val correctionDb: Double
)

data class Profile(
    val id: String,
    val name: String,
    val timestampMs: Long,
    val target: String,
    val micType: String,
    val deviceName: String = "Android TV",
    val stimulus: String = "sweep_10s",
    val captureSeconds: Double = 10.0,
    val volumeM3: Double? = 54.0,
    val rt60Seconds: Double? = 0.5,
    val schroederHz: Double = 192.0,
    val transitionHz: Double = 384.0,
    val rolloffHz: Double = 40.0,
    val nullsUntouchedHz: List<Double> = emptyList(),
    val preampDb: Double = 0.0,
    val filters: List<PeakingFilter> = emptyList(),
    val platformBands: List<PlatformBand> = emptyList(),
    val curve: List<CurvePoint> = emptyList(),
    val capabilityVerdict: Map<String, String> = emptyMap()
)
