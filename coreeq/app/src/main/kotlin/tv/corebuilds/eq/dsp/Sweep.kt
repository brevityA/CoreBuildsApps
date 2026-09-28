package tv.corebuilds.eq.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The exponential sine sweep Core EQ plays, and the Farina inverse filter the
 * capture is deconvolved with (tools/core_eq_dsp.py `sine_sweep`).
 *
 * One definition serves both sides so the played stimulus and the inverse can
 * never drift apart. The sweep is played at [DspConstants.FS]; the remote
 * microphone delivers 16 kHz, so the inverse is built at the capture rate and
 * only over the part of the sweep below that rate's Nyquist limit. The
 * deconvolved response is therefore band-limited to [bandTopHz], which is
 * above the 8 kHz correction ceiling the remote mic imposes anyway.
 */
object Sweep {

    const val F_START = 20.0
    const val F_END = 20000.0
    const val SECONDS = DspConstants.ESS_SECONDS
    const val AMPLITUDE = 0.5
    const val TAPER_SECONDS = 0.05

    private fun rate(seconds: Double, fStart: Double, fEnd: Double) = seconds / ln(fEnd / fStart)

    /** Instantaneous frequency of the sweep [t] seconds in. */
    fun frequencyAt(t: Double, seconds: Double = SECONDS, fStart: Double = F_START, fEnd: Double = F_END): Double =
        fStart * exp(t / rate(seconds, fStart, fEnd))

    /** When the sweep passes [hz], in seconds from its start. */
    fun timeAt(hz: Double, seconds: Double = SECONDS, fStart: Double = F_START, fEnd: Double = F_END): Double =
        rate(seconds, fStart, fEnd) * ln(hz / fStart)

    private fun sample(t: Double, seconds: Double, fStart: Double, fEnd: Double): Double {
        val k = rate(seconds, fStart, fEnd)
        return sin(2.0 * PI * fStart * k * (exp(t / k) - 1.0))
    }

    private fun taper(t: Double, seconds: Double): Double = when {
        t < TAPER_SECONDS -> 0.5 * (1.0 - cos(PI * t / TAPER_SECONDS))
        t > seconds - TAPER_SECONDS -> 0.5 * (1.0 - cos(PI * max(0.0, seconds - t) / TAPER_SECONDS))
        else -> 1.0
    }

    /** The played stimulus: [AMPLITUDE] peak, 50 ms raised-cosine ends so it never clicks. */
    fun generate(
        fs: Int = DspConstants.FS,
        seconds: Double = SECONDS,
        fStart: Double = F_START,
        fEnd: Double = F_END
    ): DoubleArray {
        val n = (seconds * fs).toInt()
        return DoubleArray(n) { i ->
            val t = i.toDouble() / fs
            AMPLITUDE * taper(t, seconds) * sample(t, seconds, fStart, fEnd)
        }
    }

    /** Highest frequency the inverse covers at capture rate [fs]. */
    fun bandTopHz(fs: Int, fEnd: Double = F_END): Double = min(fEnd, 0.49 * fs)

    /**
     * Farina inverse filter at capture rate [fs].
     *
     * The sweep is evaluated analytically at [fs] up to [bandTopHz], weighted
     * by its instantaneous frequency (the +3 dB/octave correction for the
     * sweep's pink spectrum), faded out over its last 100 ms so the truncation
     * does not ring, then time-reversed and normalised to unit peak.
     */
    fun inverse(
        fs: Int,
        seconds: Double = SECONDS,
        fStart: Double = F_START,
        fEnd: Double = F_END
    ): DoubleArray {
        val top = bandTopHz(fs, fEnd)
        val tTop = min(seconds, timeAt(top, seconds, fStart, fEnd))
        val n = (tTop * fs).toInt()
        require(n > 0) { "capture rate $fs Hz cannot hold any of the sweep" }
        val fade = 0.1
        val forward = DoubleArray(n) { i ->
            val t = i.toDouble() / fs
            val endFade = if (t > tTop - fade) 0.5 * (1.0 - cos(PI * max(0.0, tTop - t) / fade)) else 1.0
            taper(t, seconds) * endFade * sample(t, seconds, fStart, fEnd) *
                (frequencyAt(t, seconds, fStart, fEnd) / top)
        }
        forward.reverse()
        var peak = 0.0
        for (v in forward) peak = max(peak, kotlin.math.abs(v))
        if (peak > 0) for (i in forward.indices) forward[i] /= peak
        return forward
    }
}
