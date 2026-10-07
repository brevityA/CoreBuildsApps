package tv.corebuilds.eq.apply

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

/**
 * The opt-in extras on one audio session: BassBoost and LoudnessEnhancer.
 *
 * Owned by that session's correction effect in [EqService] and created and
 * released with it, so every session carries its own. 1.2.0 kept one chain
 * for the whole service and released it whenever another session attached,
 * leaving the first session's effects released or orphaned.
 *
 * Room correction never runs here. It stays on DynamicsProcessing, or the
 * platform Equalizer where DP is refused, exactly as in 1.1.1.
 */
internal class ExtraEffects private constructor(
    private val bassBoost: BassBoost?,
    private val loudness: LoudnessEnhancer?
) {
    /** Short status words, e.g. "bass 60% + loudness +2.0 dB". */
    val label: String = listOfNotNull(
        bassBoost?.let { "bass ${it.roundedStrength / 10}%" },
        loudness?.let { "loudness +%.1f dB".format(java.util.Locale.US, it.targetGain / 100f) }
    ).joinToString(" + ")

    fun setEnabled(on: Boolean) {
        for (effect in listOfNotNull<AudioEffect>(bassBoost, loudness)) {
            try {
                effect.setEnabled(on)
            } catch (e: Exception) {
                Log.w(TAG, "${effect.javaClass.simpleName} refused enable=$on", e)
            }
        }
    }

    fun release() {
        for (effect in listOfNotNull<AudioEffect>(bassBoost, loudness)) {
            try {
                effect.setEnabled(false)
            } catch (_: Exception) {
                // Releasing below is what matters.
            }
            try {
                effect.release()
            } catch (e: Exception) {
                Log.w(TAG, "Could not release ${effect.javaClass.simpleName}", e)
            }
        }
    }

    companion object {
        private const val TAG = "CoreEqExtras"
        private const val PRIORITY = 0

        /**
         * Create the planned extras on [session]. An extra the TV refuses is
         * skipped and logged; correction is never held back by an extra.
         * Returns null when nothing was planned or nothing was accepted.
         */
        fun attach(session: Int, plan: ExtraPlan): ExtraEffects? {
            if (plan.isEmpty) return null
            val bass = plan.bassStrength?.let { strength ->
                create("BassBoost", session) {
                    BassBoost(PRIORITY, session).apply {
                        setStrength(strength.toShort())
                        enabled = true
                    }
                }
            }
            val loud = plan.loudnessGainMb?.let { gain ->
                create("LoudnessEnhancer", session) {
                    LoudnessEnhancer(session).apply {
                        setTargetGain(gain)
                        enabled = true
                    }
                }
            }
            return if (bass == null && loud == null) null else ExtraEffects(bass, loud)
        }

        private fun <T : AudioEffect> create(name: String, session: Int, block: () -> T): T? =
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "$name refused on session $session", e)
                null
            }
    }
}
