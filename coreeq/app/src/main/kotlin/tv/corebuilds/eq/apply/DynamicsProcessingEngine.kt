package tv.corebuilds.eq.apply

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import tv.corebuilds.eq.export.Profile
import kotlin.math.abs

/** The result of creating/configuring a DP effect, including the platform read-back. */
data class DynamicsProcessingApplied(
    val effect: DynamicsProcessing,
    val bandCentresHz: List<Double>,
    val bandGainsDb: List<Double>
)

/**
 * The API-28+ DynamicsProcessing adapter. The pre-EQ is the only equalising
 * stage; MBC and post-EQ are disabled, and a linked protection limiter is
 * enabled on every channel. Construction/config refusal is allowed to escape
 * so the service can release this effect and fall back to Equalizer.
 *
 * Runtime API/read-back checks reduce the chance that a platform silently
 * accepts a no-op configuration. They do not replace the M6a audible limiter
 * and hardware validation.
 */
@RequiresApi(Build.VERSION_CODES.P)
object DynamicsProcessingEngine {
    private const val TAG = "CoreEqDynamics"
    private const val CONFIG_CHANNELS = 2
    private const val PRIORITY = 0
    private const val VERIFY_GAIN_TOLERANCE_DB = 0.15
    private const val VERIFY_CUTOFF_TOLERANCE_HZ = 0.2
    private const val VERIFY_LIMITER_TOLERANCE = 0.1

    fun create(sessionId: Int, profile: Profile): DynamicsProcessingApplied {
        val limiter = LimiterSettings()
        check(limiter.isProtectionOnly()) { "Limiter settings must only protect, never boost" }
        val layout = DpBandLayout.specs()
        val gains = BandMapping.gainsDb(profile, layout.map { it.centerHz })
        val config = buildConfig(layout, gains, limiter)
        val effect = DynamicsProcessing(PRIORITY, sessionId, config)
        try {
            check(effect.hasControl()) { "DynamicsProcessing did not obtain control" }
            val applied = configure(effect, profile, limiter)
            Log.i(
                TAG,
                "Configured session=$sessionId channels=${effect.channelCount} bands=${applied.bandCentresHz.size} " +
                    "centresHz=${applied.bandCentresHz.joinToString(",") { "%.1f".format(java.util.Locale.US, it) }} " +
                    "limiter=on thresholdDb=${limiter.thresholdDb} ratio=${limiter.ratio} " +
                    "attackMs=${limiter.attackMs} releaseMs=${limiter.releaseMs} postGainDb=${limiter.postGainDb}"
            )
            return applied
        } catch (e: Exception) {
            try {
                effect.setEnabled(false)
            } catch (_: Exception) {
                // Preserve the original construction/configuration failure.
            }
            effect.release()
            throw e
        }
    }

    /** Reapply a changed profile to an already-held DP effect and verify read-back. */
    fun configure(
        effect: DynamicsProcessing,
        profile: Profile,
        limiter: LimiterSettings = LimiterSettings()
    ): DynamicsProcessingApplied {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { "DynamicsProcessing requires API 28" }
        check(limiter.isProtectionOnly()) { "Limiter settings must only protect, never boost" }
        check(effect.hasControl()) { "DynamicsProcessing lost control" }

        val channelCount = effect.channelCount
        check(channelCount > 0) { "DynamicsProcessing reports no channels" }
        val cutoffs = (0 until effect.getPreEqByChannelIndex(0).bandCount).map { band ->
            effect.getPreEqBandByChannelIndex(0, band).cutoffFrequency.toDouble()
        }
        val expectedLayout = DpBandLayout.specs()
        check(cutoffs.size == expectedLayout.size) {
            "DynamicsProcessing returned ${cutoffs.size} PreEQ bands, expected ${expectedLayout.size}"
        }
        for (index in expectedLayout.indices) {
            check(abs(cutoffs[index] - expectedLayout[index].cutoffHz) <= VERIFY_CUTOFF_TOLERANCE_HZ) {
                "DynamicsProcessing band $index cutoff did not read back as configured (${cutoffs[index]} Hz)"
            }
        }
        val centres = DpBandLayout.centresFromCutoffs(cutoffs)
        val gains = BandMapping.gainsDb(profile, centres)
        check(gains.size == cutoffs.size) { "DynamicsProcessing band layout changed unexpectedly" }

        for (channel in 0 until channelCount) {
            val preEq = effect.getPreEqByChannelIndex(channel)
            check(preEq.isInUse && preEq.isEnabled) { "DynamicsProcessing PreEQ is not active" }
            check(preEq.bandCount == cutoffs.size) { "DynamicsProcessing PreEQ band count changed" }
            for (band in cutoffs.indices) {
                val read = effect.getPreEqBandByChannelIndex(channel, band)
                check(read.isEnabled) { "DynamicsProcessing PreEQ band $band is disabled" }
                val requested = gains[band].toFloat()
                read.gain = requested
                effect.setPreEqBandByChannelIndex(channel, band, read)
            }
        }

        verifyConfiguration(effect, channelCount, cutoffs, gains, limiter)
        check(effect.setEnabled(true) == AudioEffect.SUCCESS) { "DynamicsProcessing refused to enable" }
        check(effect.hasControl()) { "DynamicsProcessing lost control after enabling" }
        check(effect.getEnabled()) { "DynamicsProcessing reports disabled after enabling" }

        val readBackGains = (0 until cutoffs.size).map { band ->
            effect.getPreEqBandByChannelIndex(0, band).gain.toDouble()
        }
        return DynamicsProcessingApplied(effect, centres, readBackGains)
    }

    private fun buildConfig(
        layout: List<DpBandSpec>,
        gains: DoubleArray,
        limiterSettings: LimiterSettings
    ): DynamicsProcessing.Config {
        require(layout.size == gains.size)
        val eq = DynamicsProcessing.Eq(true, true, layout.size)
        for (index in layout.indices) {
            eq.setBand(
                index,
                DynamicsProcessing.EqBand(
                    true,
                    layout[index].cutoffHz.toFloat(),
                    gains[index].toFloat()
                )
            )
        }
        val limiter = DynamicsProcessing.Limiter(
            true,
            limiterSettings.enabled,
            if (limiterSettings.linked) 0 else 1,
            limiterSettings.attackMs,
            limiterSettings.releaseMs,
            limiterSettings.ratio,
            limiterSettings.thresholdDb,
            limiterSettings.postGainDb
        )
        return DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CONFIG_CHANNELS,
            true, layout.size,
            false, 0,
            false, 0,
            true
        )
            .setInputGainAllChannelsTo(0f)
            .setPreEqAllChannelsTo(eq)
            .setLimiterAllChannelsTo(limiter)
            .build()
    }

    private fun verifyConfiguration(
        effect: DynamicsProcessing,
        channelCount: Int,
        expectedCutoffs: List<Double>,
        expectedGains: DoubleArray,
        expectedLimiter: LimiterSettings
    ) {
        for (channel in 0 until channelCount) {
            val preEq = effect.getPreEqByChannelIndex(channel)
            check(preEq.isInUse && preEq.isEnabled) { "DynamicsProcessing PreEQ is not active on channel $channel" }
            check(preEq.bandCount == expectedCutoffs.size) { "DynamicsProcessing returned ${preEq.bandCount} bands, expected ${expectedCutoffs.size}" }
            for (band in expectedCutoffs.indices) {
                val actual = effect.getPreEqBandByChannelIndex(channel, band)
                val cutoffError = abs(actual.cutoffFrequency.toDouble() - expectedCutoffs[band])
                check(actual.isEnabled && cutoffError <= VERIFY_CUTOFF_TOLERANCE_HZ) {
                    "DynamicsProcessing band $band cutoff read-back differs (${actual.cutoffFrequency} Hz)"
                }
                val gainError = abs(actual.gain.toDouble() - expectedGains[band])
                check(gainError <= VERIFY_GAIN_TOLERANCE_DB) {
                    "DynamicsProcessing band $band gain read-back differs (${actual.gain} dB)"
                }
            }

            check(abs(effect.getInputGainByChannelIndex(channel).toDouble()) <= VERIFY_GAIN_TOLERANCE_DB) {
                "DynamicsProcessing input gain must remain at 0 dB"
            }
            val mbc = effect.getMbcByChannelIndex(channel)
            check(!mbc.isInUse && !mbc.isEnabled) { "DynamicsProcessing MBC must remain disabled" }
            val postEq = effect.getPostEqByChannelIndex(channel)
            check(!postEq.isInUse && !postEq.isEnabled) { "DynamicsProcessing PostEQ must remain disabled" }

            val limiter = effect.getLimiterByChannelIndex(channel)
            check(limiter.isInUse && limiter.isEnabled) { "DynamicsProcessing limiter is not active on channel $channel" }
            check(limiter.linkGroup == if (expectedLimiter.linked) 0 else 1) { "DynamicsProcessing limiter link group read-back differs" }
            check(abs(limiter.attackTime - expectedLimiter.attackMs) <= VERIFY_LIMITER_TOLERANCE) { "DynamicsProcessing limiter attack read-back differs" }
            check(abs(limiter.releaseTime - expectedLimiter.releaseMs) <= VERIFY_LIMITER_TOLERANCE) { "DynamicsProcessing limiter release read-back differs" }
            check(abs(limiter.ratio - expectedLimiter.ratio) <= VERIFY_LIMITER_TOLERANCE) { "DynamicsProcessing limiter ratio read-back differs" }
            check(abs(limiter.threshold - expectedLimiter.thresholdDb) <= VERIFY_LIMITER_TOLERANCE) { "DynamicsProcessing limiter threshold read-back differs" }
            check(abs(limiter.postGain - expectedLimiter.postGainDb) <= VERIFY_LIMITER_TOLERANCE) { "DynamicsProcessing limiter post-gain read-back differs" }
        }
    }
}
