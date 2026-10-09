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
 *    above the noise just ahead of it: the sweep was not heard. The peak is
 *    read between samples ([interpolatedPeak]), so where the arrival happens
 *    to fall in the 16 kHz grid cannot cost up to 3 dB of SNR.
 * 3. RT60 as a T20 ([decay]): Lundeby-style crosspoint from the decay's own
 *    regression line, noise subtracted, the energy past the crosspoint added
 *    back, and no RT60 at all unless the decay stands [MIN_DECAY_RANGE_DB]
 *    clear of the noise (ISO 3382-2's requirement for a T20).
 * 4. Magnitude from a 170 ms window that starts 2 ms before the direct sound
 *    and fades out over its second half, reduced to 1/3-octave bands by
 *    energy mean, then level-aligned to the target over 300 Hz-3 kHz. Only
 *    bands that end below 0.45 x the capture rate are kept: at 16 kHz the
 *    8 kHz band straddles the anti-alias edge and read 4-12 dB low.
 * 5. The minimum-phase gate, below the transition: excess group delay over
 *    the same window, aligned to the direct arrival, against the cepstral
 *    minimum-phase reference; a band whose median exceeds
 *    [DspConstants.MIN_PHASE_TOLERANCE_MS] is not corrected.
 * 6. Nulls, roll-off, transition and the two-regime correction exactly as the
 *    reference chain computes them.
 */
object SweepAnalysis {

    const val MIN_SNR_DB = 20.0
    /** Decay range a T20 needs (ISO 3382-2:2008 via Hak & Vertegaal; Larson Davis rates under 35 dB "poor"). */
    const val MIN_DECAY_RANGE_DB = 35.0
    /** The highest band edge a capture holds cleanly, as a fraction of its rate. */
    const val BAND_TOP_FRACTION = 0.45
    const val TAIL_SECONDS = 2.5
    private const val NOISE_FROM_S = -0.30
    private const val NOISE_TO_S = -0.02
    private const val RT60_TOP_DB = -5.0
    private const val RT60_BOTTOM_DB = -25.0
    private const val RT60_MAX_TAIL_S = 1.5
    private const val PRE_SECONDS = 0.002
    private const val ALIGN_LO_HZ = 300.0
    private const val ALIGN_HI_HZ = 3000.0
    private const val STEPS_PER_SAMPLE = 8
    private const val INTERP_HALF = 16

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
        var sampleAbs = 0.0
        for (i in ir.indices) {
            val a = abs(ir[i])
            if (a > sampleAbs) { sampleAbs = a; peak = i }
        }
        if (sampleAbs <= 0.0) throw MeasurementException("The microphone recorded silence: no sweep was heard.")
        val latencySamples = peak - (inverseLen - 1)
        val peakAbs = interpolatedPeak(ir, peak)

        val noisePower = meanSquare(ir, peak + (NOISE_FROM_S * fs).toInt(), peak + (NOISE_TO_S * fs).toInt())
        val snrDb = 10.0 * log10(peakAbs * peakAbs / max(noisePower, 1e-30))
        if (snrDb < MIN_SNR_DB) {
            throw MeasurementException(
                "The sweep was only ${snrDb.roundToInt()} dB above the room's noise (needs $MIN_SNR_DB dB). " +
                    "Turn the TV up, hold the remote still at your seat, and keep the room quiet."
            )
        }

        val rt60 = decay(ir, peak, fs, noisePower).rt60Seconds
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
        checkTimeline(centres, gate, transition)
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

    /** A T20 and the decay range it was fitted over; [rt60Seconds] is null when the range is too small. */
    data class Decay(val rt60Seconds: Double?, val decayRangeDb: Double?)

    fun rt60Seconds(ir: DoubleArray, peak: Int, fs: Int, noisePower: Double): Double? =
        decay(ir, peak, fs, noisePower).rt60Seconds

    /**
     * T20 with Lundeby-style noise handling (1.3.2).
     *
     * Through 1.3.1 the tail was cut at the first 10 ms block within 3 dB of
     * the noise, each sample's noise was clamped away (`max(0, v^2 - N)`,
     * which removes only about half of it) and nothing was added back past
     * the cut. The truncated curve always plunged to -25 dB at the cut, so
     * the "decay reached -25 dB" check always passed, and the fit read short:
     * 22-39 % at 35 dB SNR and 63-82 % at 25 dB in synthetic rooms, still
     * accepted. Now:
     *
     * 1. The 10 ms envelope is fitted by regression from the direct sound down
     *    to 10 dB above the noise. Where that line meets the noise is the
     *    crosspoint, and the line's level at t = 0 over the noise is the decay
     *    range (INR).
     * 2. Up to the crosspoint, v^2 - N is integrated signed, and the energy an
     *    exponential decay holds past it (N x the decay's time constant) is
     *    added back, so the Schroeder curve is not truncated.
     * 3. The T20 is fitted over -5 to -25 dB of that curve, across at least
     *    30 ms, and only kept if the decay range is [MIN_DECAY_RANGE_DB] or
     *    more. In simulation this read within 0.2 % on every run it kept.
     */
    fun decay(ir: DoubleArray, peak: Int, fs: Int, noisePower: Double): Decay {
        val block = max(1, fs / 100)
        val blockS = block.toDouble() / fs
        val len = min(ir.size - peak, (RT60_MAX_TAIL_S * fs).toInt())
        val nBlocks = len / block
        if (nBlocks < 5) return Decay(null, null)
        val noise = max(noisePower, 1e-30)
        val noiseDb = 10.0 * log10(noise)
        val envDb = DoubleArray(nBlocks) {
            10.0 * log10(max(meanSquare(ir, peak + it * block, peak + (it + 1) * block), 1e-300))
        }
        var k = 1
        while (k < nBlocks && envDb[k] > noiseDb + 10.0) k++
        if (k - 1 < 3) return Decay(null, null)
        val (s, c) = fitLine(DoubleArray(k - 1) { (it + 1.5) * blockS }, DoubleArray(k - 1) { envDb[it + 1] })
        if (s >= 0.0) return Decay(null, null)
        val rangeDb = c - noiseDb
        val crossSamples = ((noiseDb - c) / s * fs).toInt()
        val end = min(len, max(crossSamples, block))
        val tailEnergy = noise * (10.0 / (-s * ln(10.0))) * fs
        val energy = DoubleArray(end)
        var acc = 0.0
        for (i in end - 1 downTo 0) {
            val v = ir[peak + i]
            acc += v * v - noise
            energy[i] = max(acc + tailEnergy, 1e-300)
        }
        var i5 = -1
        var i25 = -1
        for (i in 0 until end) {
            val db = 10.0 * log10(energy[i] / energy[0])
            if (i5 < 0 && db <= RT60_TOP_DB) i5 = i
            if (db <= RT60_BOTTOM_DB) { i25 = i; break }
        }
        if (i5 < 0 || i25 < 0 || i25 - i5 < 3 * block) return Decay(null, rangeDb)
        val n = i25 - i5
        val (slope, _) = fitLine(
            DoubleArray(n) { (i5 + it).toDouble() / fs },
            DoubleArray(n) { 10.0 * log10(energy[i5 + it] / energy[0]) }
        )
        if (slope >= 0.0) return Decay(null, rangeDb)
        val rt60 = -60.0 / slope
        val kept = rangeDb >= MIN_DECAY_RANGE_DB && rt60 in 0.05..3.0
        return Decay(if (kept) rt60 else null, rangeDb)
    }

    /** Least-squares line through (x, y): (slope, intercept). */
    private fun fitLine(x: DoubleArray, y: DoubleArray): Pair<Double, Double> {
        val n = x.size
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (i in 0 until n) { sx += x[i]; sy += y[i]; sxx += x[i] * x[i]; sxy += x[i] * y[i] }
        val denom = n * sxx - sx * sx
        if (n < 2 || denom <= 0.0) return Pair(0.0, 0.0)
        val slope = (n * sxy - sx * sy) / denom
        return Pair(slope, (sy - slope * sx) / n)
    }

    /**
     * The impulse response's peak magnitude read between samples: a
     * band-limited (Hann-windowed sinc) interpolation at 1/8-sample steps
     * within a sample either side of the largest sample. A direct sound that
     * lands half-way between two 16 kHz samples otherwise reads up to 3.2 dB
     * low, and the SNR with it.
     */
    fun interpolatedPeak(ir: DoubleArray, peak: Int): Double {
        var best = abs(ir[peak])
        for (step in -STEPS_PER_SAMPLE..STEPS_PER_SAMPLE) {
            if (step == 0) continue
            val t = step.toDouble() / STEPS_PER_SAMPLE
            var v = 0.0
            for (m in -INTERP_HALF..INTERP_HALF) {
                val idx = peak + m
                if (idx < 0 || idx >= ir.size) continue
                val x = t - m
                val sinc = if (abs(x) < 1e-12) 1.0 else kotlin.math.sin(PI * x) / (PI * x)
                val w = 0.5 * (1.0 + cos(PI * x / (INTERP_HALF + 1)))
                v += ir[idx] * sinc * w
            }
            best = max(best, abs(v))
        }
        return best
    }

    /**
     * Refuse a recording that lost part of itself. When a stretch of the
     * capture is dropped (a lost Bluetooth audio packet that is skipped
     * rather than filled), everything after it arrives early: the impulse
     * response lines up on the late, high-frequency part of the sweep, and
     * the bass swept before the gap looks delayed by the gap. In simulation,
     * losing 8 ms anywhere from 2 s to 7.5 s into the sweep failed the
     * minimum-phase gate on every band from 20 Hz to the transition, which
     * silently zeroed the bass correction while the SNR stayed above 40 dB.
     * A real room's modal bass is minimum phase, so every bass band failing
     * together is the recording, not the room.
     */
    private fun checkTimeline(centres: DoubleArray, gate: BooleanArray, transitionHz: Double) {
        val bass = centres.indices.filter { centres[it] >= DspConstants.F_MIN && centres[it] < transitionHz }
        if (bass.size >= 4 && bass.none { gate[it] }) {
            throw MeasurementException(
                "Part of the recording went missing: every bass band arrived late against the rest of the " +
                    "sweep, which a room cannot do. A remote that drops a stretch of audio causes this. " +
                    "Measure again with the remote still and nearer the TV."
            )
        }
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

        val top = minOf(Sweep.bandTopHz(fs), BAND_TOP_FRACTION * fs, DspConstants.F_MAX * 2.0.pow(1.0 / 6.0))
        val centres = mutableListOf<Double>()
        val values = mutableListOf<Double>()
        for (c in DspConstants.ISO_CENTRES_HZ) {
            val exact = 1000.0 * 2.0.pow((3.0 * log2(c / 1000.0)).roundToInt() / 3.0)
            val lo = exact * 2.0.pow(-1.0 / 6.0)
            val hi = exact * 2.0.pow(1.0 / 6.0)
            if (hi > top || c > DspConstants.F_MAX) continue
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
