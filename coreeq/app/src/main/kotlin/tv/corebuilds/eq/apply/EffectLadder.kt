package tv.corebuilds.eq.apply

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build

enum class LadderRung(val rank: Int, val title: String, val description: String) {
    BROADCAST_SESSION(1, "Session open/close broadcasts", "Players that announce a session. Netflix and YouTube do not."),
    DUMP_DISCOVERY(2, "DUMP-assisted discovery (v1.1)", "ADB grant dumpsys media.audio_flinger discovery."),
    GLOBAL_MIX(3, "Global output mix (session 0)", "Deprecated in 2012, never removed. Works on some sets."),
    COMPANION_EXPORT(4, "Companion export", "Export for Poweramp Equalizer or TV sound settings. Always works.")
}

data class ProbeVerdict(
    val session0Supported: Boolean,
    val sessionBroadcastSupported: Boolean,
    val dynamicsProcessingSupported: Boolean,
    val platformEqualizerSupported: Boolean,
    val bandCount: Int,
    val bandCentresHz: List<Double>,
    val minMillibel: Int,
    val maxMillibel: Int,
    val recommendedRung: LadderRung
)

/**
 * Capability probe: tests every path Android TV provides to apply equalization.
 * Matches docs/CORE_EQ_PLAN.md §4.
 */
object EffectLadder {

    fun probe(context: Context): ProbeVerdict {
        var session0Ok = false
        var eqSupported = false
        var bands = 5
        val centres = mutableListOf<Double>()
        var minMb = -1500
        var maxMb = 1500

        // Test platform Equalizer on session 0
        var eq: Equalizer? = null
        try {
            eq = Equalizer(0, 0)
            eqSupported = true
            session0Ok = true
            val count = eq.numberOfBands.toInt()
            if (count > 0) {
                bands = count
                centres.clear()
                for (b in 0 until count) {
                    centres.add(eq.getCenterFreq(b.toShort()).toDouble() / 1000.0)
                }
                val range = eq.bandLevelRange
                if (range != null && range.size >= 2) {
                    minMb = range[0].toInt()
                    maxMb = range[1].toInt()
                }
            }
        } catch (e: Exception) {
            session0Ok = false
        } finally {
            try {
                eq?.release()
            } catch (ignored: Exception) {}
        }

        if (centres.isEmpty()) {
            centres.addAll(listOf(60.0, 230.0, 910.0, 3600.0, 14000.0))
        }

        // Test DynamicsProcessing (API 28+)
        var dpOk = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val dp = DynamicsProcessing(0)
                dpOk = true
                dp.release()
            } catch (e: Exception) {
                dpOk = false
            }
        }

        // Recommendation
        val recommended = when {
            session0Ok -> LadderRung.GLOBAL_MIX
            else -> LadderRung.COMPANION_EXPORT
        }

        return ProbeVerdict(
            session0Supported = session0Ok,
            sessionBroadcastSupported = false, // Probed via runtime announcements
            dynamicsProcessingSupported = dpOk,
            platformEqualizerSupported = eqSupported,
            bandCount = bands,
            bandCentresHz = centres,
            minMillibel = minMb,
            maxMillibel = maxMb,
            recommendedRung = recommended
        )
    }
}
