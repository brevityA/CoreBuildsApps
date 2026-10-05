package tv.corebuilds.eq.apply

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build

enum class LadderRung(val rank: Int, val title: String, val description: String) {
    BROADCAST_SESSION(1, "Playback/session signals", "Request a DUMP rescan; never trusted as app or session identity."),
    DUMP_DISCOVERY(2, "DUMP-assisted discovery (v1.1)", "Optional UID/session scan; Android may refuse this third-party grant."),
    GLOBAL_MIX(3, "Global output mix (session 0)", "Deprecated in 2012, never removed. Works on some sets."),
    COMPANION_EXPORT(4, "Companion export", "Export for Poweramp Equalizer or TV sound settings when on-device application is unavailable.")
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
        var equalizerSession0Ok = false
        var eqSupported = false
        var bands = 5
        val centres = mutableListOf<Double>()
        var minMb = -1500
        var maxMb = 1500

        // Test platform Equalizer on session 0
        var eq: Equalizer? = null
        try {
            eq = Equalizer(0, 0)
            eqSupported = eq.hasControl()
            equalizerSession0Ok = eqSupported
            val count = if (eqSupported) eq.numberOfBands.toInt() else 0
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
            equalizerSession0Ok = false
        } finally {
            try {
                eq?.release()
            } catch (ignored: Exception) {}
        }

        if (centres.isEmpty()) {
            centres.addAll(listOf(60.0, 230.0, 910.0, 3600.0, 14000.0))
        }

        // A constructible, controlling DP instance also counts as a session-0 path.
        // The apply service still builds/verifies its real PreEQ+limiter config.
        var dpOk = false
        var dynamicsProcessingSession0Ok = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            var dp: DynamicsProcessing? = null
            try {
                dp = DynamicsProcessing(0)
                dpOk = dp.hasControl()
                dynamicsProcessingSession0Ok = dpOk
            } catch (e: Exception) {
                dpOk = false
                dynamicsProcessingSession0Ok = false
            } finally {
                try {
                    dp?.release()
                } catch (ignored: Exception) {}
            }
        }
        val session0Ok = equalizerSession0Ok || dynamicsProcessingSession0Ok

        // Recommendation
        val recommended = when {
            session0Ok -> LadderRung.GLOBAL_MIX
            DumpsysDiscovery.hasGrant(context) -> LadderRung.DUMP_DISCOVERY
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
