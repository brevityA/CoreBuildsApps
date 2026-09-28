package tv.corebuilds.eq.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A measurement that cannot be trusted, with the reason in words a user can act on. */
class MeasurementException(message: String) : Exception(message)

data class SweepResult(
    val centresHz: DoubleArray,
    val measuredDb: DoubleArray,
    val targetDb: DoubleArray,
    val correctionDb: DoubleArray,
    val nullMask: BooleanArray,
    /** False where the band is too far from minimum phase to correct; the corrector leaves it at 0. */
    val minPhaseOk: BooleanArray,
    /** Null when the decay could not be fitted: the transition then falls back to 300 Hz. */
    val rt60Seconds: Double?,
    val schroederHz: Double?,
    val transitionHz: Double,
    val rolloffHz: Double,
    val floorHz: Double,
    val snrDb: Double,
    val latencyMs: Double,
    val filters: List<PeakingFilter>,
    val preampDb: Double
)

/**
 * Turns a captured sweep into a room correction: the Kotlin twin of
 * tools/core_eq_dsp.py `analyze_sweep_recording`.
 *
 * 1. Deconvolve the capture with the Farina inverse ([Sweep.inverse]) to an
 *    impulse response; the linear response is its largest peak, and the
 *    harmonic distortion lands ahead of it where it is ignored.
 * 2. Refuse a capture whose impulse response does not stand [MIN_SNR_DB]
 *    above the noise just ahead of it: the sweep was not heard.
 * 3. RT60 by noise-compensated Schroeder backward integration, fitted over
 *    the -5 to -25 dB span (T20) because a remote mic rarely gives the 45 dB
 *    of range a T30 needs.
 * 4. Magnitude from a 170 ms window that starts 2 ms before the direct sound
 *    and fades out over its second half, reduced to 1/3-octave bands by
 *    energy mean, then level-aligned to the target over 300 Hz-3 kHz.
 * 5. The minimum-phase gate, below the transition: excess group delay over
 *    the same window, aligned to the direct arrival, against the cepstral
 *    minimum-phase reference; a band whose median exceeds
 *    [DspConstants.MIN_PHASE_TOLERANCE_MS] is not corrected.
 * 6. Nulls, roll-off, transition and the two-regime correction exactly as the
 *    reference chain computes them.
 */
object SweepAnalysis {

    const val MIN_SNR_DB = 20.0
    const val TAIL_SECONDS = 2.5
    private const val NOISE_FROM_S = -0.30
    private const val NOISE_TO_S = -0.02
    private const val RT60_TOP_DB = -5.0
    private const val RT60_BOTTOM_DB = -25.0
    private const val RT60_MAX_TAIL_S = 1.5
    private const val PRE_SECONDS = 0.002
    private const val ALIGN_LO_HZ = 300.0
    private const val ALIGN_HI_HZ = 3000.0

    fun impulseResponse(recording: DoubleArray, fs: Int, seconds: Double = Sweep.SECONDS): DoubleArray =
        Fft.convolve(recording, Sweep.inverse(fs, seconds))

    fun analyze(
        recording: DoubleArray,
        fs: Int,
        target: String,
        volumeM3: Double?,
        seconds: Double = Sweep.SECONDS,
        nFilters: Int = DspConstants.PEAKING_FILTERS
    ): SweepResult {
        val minSamples = ((seconds + 0.5) * fs).toInt()
        if (recording.size < minSamples) {
            throw MeasurementException(
                "The capture is ${"%.1f".format(recording.size.toDouble() / fs)} s long; " +
                    "a ${seconds.toInt()} s sweep needs at least ${"%.1f".format(minSamples.toDouble() / fs)} s."
            )
        }
        val inverseLen = Sweep.inverse(fs, seconds).size
        val ir = impulseResponse(recording, fs, seconds)

        var peak = 0
        var peakAbs = 0.0
        for (i in ir.indices) {
            val a = abs(ir[i])
            if (a > peakAbs) { peakAbs = a; peak = i }
        }
        if (peakAbs <= 0.0) throw MeasurementException("The microphone recorded silence: no sweep was heard.")
        val latencySamples = peak - (inverseLen - 1)

        val noisePower = meanSquare(ir, peak + (NOISE_FROM_S * fs).toInt(), peak + (NOISE_TO_S * fs).toInt())
        val snrDb = 10.0 * log10(peakAbs * peakAbs / max(noisePower, 1e-30))
        if (snrDb < MIN_SNR_DB) {
            throw MeasurementException(
                "The sweep was only ${snrDb.roundToInt()} dB above the room's noise (needs $MIN_SNR_DB dB). " +
                    "Turn the TV up, hold the remote still at your seat, and keep the room quiet."
            )
        }

        val rt60 = rt60Seconds(ir, peak, fs, noisePower)
        val transition = Correction.transitionHz(volumeM3, rt60)
        val schroeder = if (volumeM3 != null && rt60 != null) Correction.schroederHz(volumeM3, rt60) else null

        val (centres, rawDb) = bandMagnitudes(ir, peak, fs)
        val targetDb = Targets.targetCurve(target, centres)
        val measured = alignTo(centres, rawDb, targetDb)

        val nulls = Correction.detectNulls(centres, measured)
        val rolloff = Correction.detectLowRolloff(centres, measured)
        val floorHz = max(DspConstants.F_MIN, rolloff)
        val (gdFreqs, excess) = excessGroupDelayMs(ir, peak, fs)
        // Below the transition only, as in the reference: that is where the
        // corrector inverts; above it the field is diffuse and the correction
        // is one-octave shaping that a phase gate would only switch off.
        val gate = bandMinPhaseOk(centres, gdFreqs, excess)
        val minPhaseOk = BooleanArray(centres.size) { gate[it] || centres[it] >= transition }
        val correction = Correction.calculateCorrectionCurve(
            centres, measured, targetDb,
            fMin = floorHz,
            transitionHz = transition,
            nullMask = nulls,
            minPhaseOk = minPhaseOk
        )
        val filters = Peaking.fitPeakingFilters(centres, correction, nFilters)

        return SweepResult(
            centresHz = centres,
            measuredDb = measured,
            targetDb = targetDb,
            correctionDb = correction,
            nullMask = nulls,
            minPhaseOk = minPhaseOk,
            rt60Seconds = rt60,
            schroederHz = schroeder,
            transitionHz = transition,
            rolloffHz = rolloff,
            floorHz = floorHz,
            snrDb = snrDb,
            latencyMs = latencySamples * 1000.0 / fs,
            filters = filters,
            preampDb = Peaking.preampDb(filters)
        )
    }

    private fun meanSquare(x: DoubleArray, from: Int, to: Int): Double {
        val a = max(0, from)
        val b = min(x.size, to)
        if (b <= a) return 0.0
        var s = 0.0
        for (i in a until b) s += x[i] * x[i]
        return s / (b - a)
    }

    /**
     * Noise-compensated T20. The tail is cut where its 10 ms envelope comes
     * within 3 dB of the noise floor, the noise power is subtracted before
     * the backward integration (so it cannot flatten the decay), and a room
     * whose decay never reaches -25 dB above the noise returns null rather
     * than a guess.
     */
    fun rt60Seconds(ir: DoubleArray, peak: Int, fs: Int, noisePower: Double): Double? {
        val maxLen = min(ir.size - peak, (RT60_MAX_TAIL_S * fs).toInt())
        if (maxLen < fs / 20) return null
        val block = max(1, fs / 100)
        var cut = maxLen
        var i = block
        while (i + block <= maxLen) {
            if (meanSquare(ir, peak + i, peak + i + block) < 2.0 * noisePower) { cut = i; break }
            i += block
        }
        val energy = DoubleArray(cut)
        var acc = 0.0
        for (k in cut - 1 downTo 0) {
            val v = ir[peak + k]
            acc += max(0.0, v * v - noisePower)
            energy[k] = acc
        }
        if (energy[0] <= 0.0) return null
        var n = 0; var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        var reachedBottom = false
        for (k in 0 until cut) {
            if (energy[k] <= 0.0) break
            val db = 10.0 * log10(energy[k] / energy[0])
            if (db < RT60_BOTTOM_DB) { reachedBottom = true; break }
            if (db <= RT60_TOP_DB) {
                val t = k.toDouble() / fs
                n++; sx += t; sy += db; sxx += t * t; sxy += t * db
            }
        }
        if (!reachedBottom || n < 8) return null
        val denom = n * sxx - sx * sx
        if (denom <= 0.0) return null
        val slope = (n * sxy - sx * sy) / denom
        if (slope >= 0.0) return null
        val rt60 = -60.0 / slope
        return if (rt60 in 0.05..3.0) rt60 else null
    }

    /**
     * The analysis window both the magnitude and the excess group delay use
     * (tools/core_eq_dsp.py `_direct_window`): from 2 ms before the direct
     * arrival, a raised-cosine rise, flat through the first half, faded over
     * the second. Returns the chunk (power-of-two long) and the index of the
     * direct arrival inside it.
     */
    fun windowedChunk(ir: DoubleArray, peak: Int, fs: Int): Pair<DoubleArray, Int> {
        val winLen = (DspConstants.NFFT.toDouble() * fs / DspConstants.FS).roundToInt()
        val pre = max(1, (PRE_SECONDS * fs).roundToInt())
        val chunk = DoubleArray(Fft.nextPow2(winLen))
        val fadeStart = winLen / 2
        for (i in 0 until winLen) {
            val src = peak - pre + i
            if (src < 0 || src >= ir.size) continue
            val w = when {
                i < pre -> 0.5 * (1.0 - cos(PI * i / pre))
                i >= fadeStart -> 0.5 * (1.0 + cos(PI * (i - fadeStart) / (winLen - fadeStart)))
                else -> 1.0
            }
            chunk[i] = ir[src] * w
        }
        return Pair(chunk, pre)
    }

    /** 1/3-octave magnitudes (dB, relative) at the ISO centres the capture rate can hold. */
    fun bandMagnitudes(ir: DoubleArray, peak: Int, fs: Int): Pair<DoubleArray, DoubleArray> {
        val (chunk, _) = windowedChunk(ir, peak, fs)
        val nfft = chunk.size
        val re = chunk.copyOf()
        val im = DoubleArray(nfft)
        Fft.transform(re, im)
        val binHz = fs.toDouble() / nfft
        val power = DoubleArray(nfft / 2 + 1) { re[it] * re[it] + im[it] * im[it] }

        val top = min(Sweep.bandTopHz(fs), DspConstants.F_MAX * 2.0.pow(1.0 / 6.0))
        val centres = mutableListOf<Double>()
        val values = mutableListOf<Double>()
        for (c in DspConstants.ISO_CENTRES_HZ) {
            val exact = 1000.0 * 2.0.pow((3.0 * log2(c / 1000.0)).roundToInt() / 3.0)
            val lo = exact * 2.0.pow(-1.0 / 6.0)
            val hi = exact * 2.0.pow(1.0 / 6.0)
            if (lo >= top || c > DspConstants.F_MAX) continue
            val a = max(1, kotlin.math.ceil(lo / binHz).toInt())
            val b = min(power.size - 1, kotlin.math.floor(hi / binHz).toInt())
            val mean = if (b >= a) {
                var s = 0.0
                for (k in a..b) s += power[k]
                s / (b - a + 1)
            } else {
                power[(exact / binHz).roundToInt().coerceIn(1, power.size - 1)]
            }
            centres.add(c)
            values.add(10.0 * log10(max(mean, 1e-30)))
        }
        val db = values.toDoubleArray()
        val maxDb = db.maxOrNull() ?: 0.0
        for (k in db.indices) db[k] -= maxDb
        return Pair(centres.toDoubleArray(), db)
    }

    /**
     * Excess group delay in ms per FFT bin (tools/core_eq_dsp.py
     * `excess_group_delay_ms`). The window is rotated so the direct arrival
     * sits at t = 0 (latency is not excess), and the minimum-phase reference
     * comes from the folded real cepstrum of the full log-magnitude spectrum.
     * Returns (bin frequencies, excess ms) for bins 0..nfft/2.
     */
    fun excessGroupDelayMs(ir: DoubleArray, peak: Int, fs: Int): Pair<DoubleArray, DoubleArray> {
        val (chunk, pre) = windowedChunk(ir, peak, fs)
        val n = chunk.size
        val half = n / 2
        val re = DoubleArray(n) { chunk[(it + pre) % n] }
        val im = DoubleArray(n)
        Fft.transform(re, im)

        var maxMag = 0.0
        val mag = DoubleArray(n) { val m = sqrt(re[it] * re[it] + im[it] * im[it]); if (m > maxMag) maxMag = m; m }
        val floor = max(maxMag, 1e-30) * 1e-6
        val cRe = DoubleArray(n) { ln(max(mag[it], floor)) }
        val cIm = DoubleArray(n)
        Fft.transform(cRe, cIm, inverse = true) // real cepstrum
        val fRe = DoubleArray(n)
        val fIm = DoubleArray(n)
        fRe[0] = cRe[0]
        for (k in 1 until half) fRe[k] = 2.0 * cRe[k]
        fRe[half] = cRe[half]
        Fft.transform(fRe, fIm) // log of the minimum-phase spectrum; its imaginary part is the phase

        val phaseMeas = unwrap(DoubleArray(half + 1) { atan2(im[it], re[it]) })
        val phaseMp = unwrap(DoubleArray(half + 1) { fIm[it] })
        val omegaStep = 2.0 * PI * fs / n
        val excess = DoubleArray(half + 1)
        for (k in 1..half) {
            val gdMeas = -(phaseMeas[k] - phaseMeas[k - 1]) / omegaStep
            val gdMp = -(phaseMp[k] - phaseMp[k - 1]) / omegaStep
            excess[k] = (gdMeas - gdMp) * 1000.0
        }
        excess[0] = excess[1]
        return Pair(DoubleArray(half + 1) { it.toDouble() * fs / n }, excess)
    }

    /** Per band: the median excess group delay across its bins against [toleranceMs]. */
    fun bandMinPhaseOk(
        centres: DoubleArray,
        freqs: DoubleArray,
        excessMs: DoubleArray,
        toleranceMs: Double = DspConstants.MIN_PHASE_TOLERANCE_MS
    ): BooleanArray = BooleanArray(centres.size) { k ->
        val exact = 1000.0 * 2.0.pow((3.0 * log2(centres[k] / 1000.0)).roundToInt() / 3.0)
        val lo = exact * 2.0.pow(-1.0 / 6.0)
        val hi = exact * 2.0.pow(1.0 / 6.0)
        val inBand = freqs.indices.filter { freqs[it] >= lo && freqs[it] < hi }.map { excessMs[it] }
        val values = inBand.ifEmpty {
            listOf(excessMs[freqs.indices.minByOrNull { abs(freqs[it] - exact) } ?: 0])
        }.sorted()
        val median = if (values.size % 2 == 1) values[values.size / 2]
        else 0.5 * (values[values.size / 2 - 1] + values[values.size / 2])
        median <= toleranceMs
    }

    private fun unwrap(p: DoubleArray): DoubleArray {
        val out = p.copyOf()
        var offset = 0.0
        for (i in 1 until p.size) {
            val d = p[i] - p[i - 1]
            if (d > PI) offset -= 2.0 * PI else if (d < -PI) offset += 2.0 * PI
            out[i] = p[i] + offset
        }
        return out
    }

    /**
     * Shift [measuredDb] so its mean over 300 Hz-3 kHz equals the target's.
     * Without a calibrated mic there is no absolute level, and the corrector
     * compares shapes: aligning on the broadband span (research pass four)
     * keeps a level offset from turning into a blanket boost or cut.
     */
    fun alignTo(freqs: DoubleArray, measuredDb: DoubleArray, targetDb: DoubleArray): DoubleArray {
        var sum = 0.0
        var n = 0
        for (i in freqs.indices) {
            if (freqs[i] in ALIGN_LO_HZ..ALIGN_HI_HZ) {
                sum += targetDb[i] - measuredDb[i]
                n++
            }
        }
        val offset = if (n > 0) sum / n else 0.0
        return DoubleArray(measuredDb.size) { measuredDb[it] + offset }
    }

    /** Peak and RMS of a capture in dBFS, for the too-quiet and clipping checks. */
    fun levelsDbfs(x: DoubleArray): Pair<Double, Double> {
        var peak = 0.0
        var sq = 0.0
        for (v in x) {
            val a = abs(v)
            if (a > peak) peak = a
            sq += v * v
        }
        val rms = if (x.isEmpty()) 0.0 else sqrt(sq / x.size)
        return Pair(20.0 * log10(max(peak, 1e-9)), 20.0 * log10(max(rms, 1e-9)))
    }
}
