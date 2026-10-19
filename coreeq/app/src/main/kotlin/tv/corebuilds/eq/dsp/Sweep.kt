package tv.corebuilds.eq.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
 *
 * What is played is [stimulus]: a short timing [marker], the sweep, and the
 * marker again. The remote's clock and the TV's are separate crystals, so
 * the capture can run tens of ppm fast or slow against the sweep; the
 * markers' spacing in the capture measures that, the way REW times its
 * sweeps (1.3.2).
 */
object Sweep {

    const val F_START = 20.0
    const val F_END = 20000.0
    const val SECONDS = DspConstants.ESS_SECONDS
    const val AMPLITUDE = 0.5
    const val TAPER_SECONDS = 0.05

    /** The timing marker: a linear chirp, Hann-shaped, inside even an 8 kHz-codec remote's band. */
    const val MARKER_SECONDS = 0.1
    const val MARKER_F_START = 800.0
    const val MARKER_F_END = 3000.0
    /** Silence between each marker and the sweep. */
    const val MARKER_GAP_SECONDS = 0.25
    /** Where the sweep starts inside [stimulus]. */
    const val SWEEP_OFFSET_SECONDS = MARKER_SECONDS + MARKER_GAP_SECONDS
    /** Length of [stimulus]: both markers, both gaps and the sweep. */
    const val STIMULUS_SECONDS = 2 * SWEEP_OFFSET_SECONDS + SECONDS

    /** Start-to-start distance between the two markers for a [seconds]-long sweep. */
    fun markerSpacingSeconds(seconds: Double = SECONDS): Double = SWEEP_OFFSET_SECONDS + seconds + MARKER_GAP_SECONDS

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

    /** One timing marker at rate [fs]: [MARKER_F_START] to [MARKER_F_END] Hz over [MARKER_SECONDS], [AMPLITUDE] peak. */
    fun marker(fs: Int): DoubleArray {
        val n = (MARKER_SECONDS * fs).roundToInt()
        val chirp = (MARKER_F_END - MARKER_F_START) / (2.0 * MARKER_SECONDS)
        return DoubleArray(n) { i ->
            val t = i.toDouble() / fs
            val w = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            AMPLITUDE * w * sin(2.0 * PI * (MARKER_F_START * t + chirp * t * t))
        }
    }

    /** What the TV plays: marker, [MARKER_GAP_SECONDS] of silence, the sweep, silence, marker. */
    fun stimulus(fs: Int = DspConstants.FS, seconds: Double = SECONDS): DoubleArray {
        val m = marker(fs)
        val sweep = generate(fs, seconds)
        val second = (markerSpacingSeconds(seconds) * fs).roundToInt()
        val out = DoubleArray(second + m.size)
        m.copyInto(out, 0)
        sweep.copyInto(out, (SWEEP_OFFSET_SECONDS * fs).roundToInt())
        m.copyInto(out, second)
        return out
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
