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
    /**
     * The room's RT60: the mean of the 500 Hz and 1 kHz octave T20s (ISO
     * 3382-2's mid-frequency value), else the broadband T20. Null when no
     * decay could be fitted: the transition then falls back to 300 Hz.
     */
    val rt60Seconds: Double?,
    val schroederHz: Double?,
    val transitionHz: Double,
    val rolloffHz: Double,
    val floorHz: Double,
    val snrDb: Double,
    val latencyMs: Double,
    val filters: List<PeakingFilter>,
    val preampDb: Double,
    /** Signal over noise per band, dB; a band under [SweepAnalysis.BAND_MIN_SNR_DB] is shown but not corrected. */
    val bandSnrDb: DoubleArray = DoubleArray(0),
    /** T20 per octave, 125 Hz-2 kHz; null where that octave's decay did not stand clear of its noise. */
    val rt60ByOctave: Map<Double, Double?> = emptyMap(),
    /** The capture clock against the player's, from the timing markers; null when they were not found. */
    val driftPpm: Double? = null
)

/**
 * Turns a captured sweep into a room correction: the Kotlin twin of
 * tools/core_eq_dsp.py `analyze_sweep_recording`. Steps 1, 3 (per octave) and
 * 6 run here only: they read the capture itself, which the reference never
 * sees.
 *
 * 1. Time the capture against the player: the two markers around the sweep
 *    ([Sweep.stimulus]) are found by cross-correlating one with the other,
 *    and their spacing gives the drift in ppm. Over [MIN_DRIFT_PPM] the
 *    capture is stretched back onto the player's clock ([Resample.stretch]);
 *    over [MAX_DRIFT_PPM] it lost samples, which no clock does, and it is
 *    refused.
 * 2. Deconvolve the capture with the Farina inverse ([Sweep.inverse]) to an
 *    impulse response; the linear response is its largest peak, and the
 *    harmonic distortion lands ahead of it where it is ignored. Refuse a
 *    capture whose impulse response does not stand [MIN_SNR_DB] above the
 *    noise just ahead of it: the sweep was not heard. The peak is read
 *    between samples ([interpolatedPeak]), so where the arrival happens to
 *    fall in the 16 kHz grid cannot cost up to 3 dB of SNR.
 * 3. RT60 as a T20 ([decay]): Lundeby-style crosspoint from the decay's own
 *    regression line, noise subtracted, the energy past the crosspoint added
 *    back, and no RT60 at all unless the decay stands [MIN_DECAY_RANGE_DB]
 *    clear of the noise (ISO 3382-2's requirement for a T20). It is taken per
 *    octave ([octaveDecays]), and the 500 Hz-1 kHz mean sets the transition.
 * 4. Magnitude from a 170 ms window that starts 2 ms before the direct sound
 *    and fades out over its second half, reduced to 1/3-octave bands by
 *    energy mean from [MIN_BAND_HZ] up, then level-aligned to the target over
 *    300 Hz-3 kHz. Only bands that end below 0.45 x the capture rate are
 *    kept: at 16 kHz the 8 kHz band straddles the anti-alias edge and read
 *    4-12 dB low.
 * 5. Each band's SNR ([bandSnrDb]) against the same window laid over the
 *    noise ahead of the arrival. From 2 kHz up the first band under
 *    [BAND_MIN_SNR_DB], or 20 dB under the mid-band level, is where the
 *    remote's bandwidth ends ([bandwidthEdge]; an 8 kHz codec stops near
 *    4 kHz): it and the band below it, which straddles that edge, end the
 *    measurement. Lower down an untrusted band is shown but left at 0.
 * 6. The minimum-phase gate, below the transition: excess group delay over
 *    the same window, aligned to the direct arrival, against the cepstral
 *    minimum-phase reference; a band whose median exceeds
 *    [DspConstants.MIN_PHASE_TOLERANCE_MS] is not corrected.
 * 7. Nulls, roll-off, transition and the two-regime correction as the
 *    reference chain computes them, with boosts below the transition held to
 *    [SINGLE_SEAT_BOOST_DB].
 */
object SweepAnalysis {

    const val MIN_SNR_DB = 20.0
    /** Decay range a T20 needs (ISO 3382-2:2008 via Hak & Vertegaal; Larson Davis rates under 35 dB "poor"). */
    const val MIN_DECAY_RANGE_DB = 35.0
    /** The highest band edge a capture holds cleanly, as a fraction of its rate. */
    const val BAND_TOP_FRACTION = 0.45
    const val TAIL_SECONDS = 2.5
    /** A band needs this much signal over noise to be corrected (1.3.2). */
    const val BAND_MIN_SNR_DB = 10.0
    /** From here up, the first band under [BAND_MIN_SNR_DB] is where the capture's bandwidth ends. */
    const val BANDWIDTH_CHECK_FROM_HZ = 2000.0
    /** ... or the first band this far under the mid-band level ([bandwidthEdge]). */
    const val BANDWIDTH_DROP_DB = 20.0
    /**
     * The lowest band reported. The 170 ms window's main lobe is 6.7 Hz wide,
     * wider than the 20 and 25 Hz bands, and the sweep starts at 20 Hz: those
     * two bands read 0.5-1.8 dB off in a loopback and fed only the roll-off
     * finder and the null trend.
     */
    const val MIN_BAND_HZ = 31.5
    /**
     * The most a correction may lift below the transition (1.3.2). One seat
     * and an uncalibrated microphone cannot tell a dip that belongs to the
     * room from one that belongs to that seat, or to the remote's own bass
     * roll-off (a first-order 100 Hz high-pass reads 8.6 dB low at 40 Hz), so
     * bass dips get at most this much; peaks are still cut in full.
     */
    const val SINGLE_SEAT_BOOST_DB = 2.0
    /** Drift under this is left alone: 5 ppm smears the 10 s sweep by 0.05 ms. */
    const val MIN_DRIFT_PPM = 5.0
    /** Over this the markers moved by more than a clock can (REW reads hundreds of ppm as dropouts). */
    const val MAX_DRIFT_PPM = 300.0
    /** Octave centres the decay is timed in. */
    val RT60_OCTAVES_HZ = doubleArrayOf(125.0, 250.0, 500.0, 1000.0, 2000.0)
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
    /** Earliest noise the band SNR and octave decays read: clear of the 2nd harmonic's response at -1 s. */
    private const val BAND_NOISE_FROM_S = -0.48
    /** The octave filters ring for a few ms either side of the arrival; their noise stops short of it. */
    private const val OCTAVE_NOISE_TO_S = -0.10
    /** 1/Q of an octave band: (f2 - f1) / fc = sqrt(2) - 1/sqrt(2). */
    private const val OCTAVE_BANDWIDTH = 0.7071067811865476
    /** Reported for a band whose noise could not be read: as if clean, the 1.3.1 behaviour. */
    private const val SNR_UNMEASURED_DB = 120.0
    private const val MARKER_SEARCH_S = 0.02
    private const val MARKER_MIN_SIMILARITY = 0.6

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
        var capture = recording
        var ir = impulseResponse(capture, fs, seconds)
        var peak = loudest(ir)
        if (ir[peak] == 0.0) throw MeasurementException("The microphone recorded silence: no sweep was heard.")

        val drift = markerDriftPpm(capture, peak - (inverseLen - 1), fs, seconds)
        if (drift != null && abs(drift) > MAX_DRIFT_PPM) {
            val ms = drift * 1e-6 * Sweep.markerSpacingSeconds(seconds) * 1000.0
            throw MeasurementException(
                "Part of the recording went missing: the two timing beeps arrived " +
                    "${"%.1f".format(abs(ms))} ms ${if (ms < 0) "closer together" else "further apart"} " +
                    "than they were played, more than a clock can drift. A remote that drops a stretch of audio " +
                    "causes this. Measure again with the remote still and nearer the TV."
            )
        }
        if (drift != null && abs(drift) > MIN_DRIFT_PPM) {
            capture = Resample.stretch(capture, 1.0 + drift * 1e-6)
            ir = impulseResponse(capture, fs, seconds)
            peak = loudest(ir)
        }
        val latencySamples = peak - (inverseLen - 1) - (Sweep.SWEEP_OFFSET_SECONDS * fs).roundToInt()
        val peakAbs = interpolatedPeak(ir, peak)

        val noisePower = meanSquare(ir, peak + (NOISE_FROM_S * fs).toInt(), peak + (NOISE_TO_S * fs).toInt())
        val snrDb = 10.0 * log10(peakAbs * peakAbs / max(noisePower, 1e-30))
        if (snrDb < MIN_SNR_DB) {
            throw MeasurementException(
                "The sweep was only ${snrDb.roundToInt()} dB above the room's noise (needs $MIN_SNR_DB dB). " +
                    "Turn the TV up, hold the remote still at your seat, and keep the room quiet."
            )
        }

        val broadband = decay(ir, peak, fs, noisePower).rt60Seconds
        val octaves = octaveDecays(ir, peak, fs)
        val mid = listOfNotNull(octaves[500.0], octaves[1000.0])
        val rt60 = if (mid.isNotEmpty()) mid.average() else broadband
        val transition = Correction.transitionHz(volumeM3, rt60)
        val schroeder = if (volumeM3 != null && rt60 != null) Correction.schroederHz(volumeM3, rt60) else null

        val (allCentres, allDb) = bandMagnitudes(ir, peak, fs)
        val allSnr = bandSnrDb(ir, peak, fs)
        val edge = bandwidthEdge(allCentres, allDb, allSnr)
        val keep = if (edge != null) max(1, edge - 1) else allCentres.size
        val centres = allCentres.copyOf(keep)
        val bandSnr = allSnr.copyOf(keep)
        val fMax = if (keep < allCentres.size) min(DspConstants.F_MAX, centres.last() * 2.0.pow(1.0 / 6.0))
        else DspConstants.F_MAX

        val targetDb = Targets.targetCurve(target, centres)
        val measured = alignTo(centres, allDb.copyOf(keep), targetDb)

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
        val correctable = BooleanArray(centres.size) { minPhaseOk[it] && bandSnr[it] >= BAND_MIN_SNR_DB }
        val correction = Correction.calculateCorrectionCurve(
            centres, measured, targetDb,
            fMin = floorHz,
            fMax = fMax,
            transitionHz = transition,
            nullMask = nulls,
            minPhaseOk = correctable,
            maxBoostBelowTransition = SINGLE_SEAT_BOOST_DB
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
            preampDb = Peaking.preampDb(filters),
            bandSnrDb = bandSnr,
            rt60ByOctave = octaves,
            driftPpm = drift
        )
    }

    /**
     * The first band, from [BANDWIDTH_CHECK_FROM_HZ] up, where the capture has
     * stopped: under [BAND_MIN_SNR_DB], or more than [BANDWIDTH_DROP_DB] under
     * the 400 Hz-1.6 kHz level. The second test matters when the remote's
     * own noise went through its codec too, so above the codec's edge the
     * signal and the noise are both gone and their ratio says nothing. No
     * loudspeaker falls 20 dB in a third of an octave and stays there; an
     * 8 kHz codec reads 62-92 dB down from 5 kHz up. Null when the capture
     * holds every band.
     */
    fun bandwidthEdge(centres: DoubleArray, db: DoubleArray, snrDb: DoubleArray): Int? {
        val ref = centres.indices.filter { centres[it] in 400.0..1600.0 }.map { db[it] }
        val refDb = if (ref.isEmpty()) db.maxOrNull() ?: 0.0 else ref.average()
        return centres.indices.firstOrNull {
            centres[it] >= BANDWIDTH_CHECK_FROM_HZ && (snrDb[it] < BAND_MIN_SNR_DB || db[it] < refDb - BANDWIDTH_DROP_DB)
        }
    }

    private fun loudest(x: DoubleArray): Int {
        var best = 0
        var bestAbs = -1.0
        for (i in x.indices) {
            val a = abs(x[i])
            if (a > bestAbs) { bestAbs = a; best = i }
        }
        return best
    }

    /**
     * The capture clock's drift against the player's, in ppm, from the two
     * timing markers; null when they cannot both be found.
     *
     * Each marker region is matched-filtered with the marker, which keeps
     * 800 Hz-3 kHz and compresses it to the room's response there; the two
     * compressed responses are then cross-correlated with each other. The
     * room, the level and the path are the same for both, so that
     * correlation is an autocorrelation and peaks at the true spacing
     * whatever the reflections: picking each marker's own peak could pick a
     * different reflection in each. The peak is read between samples by a
     * parabola. A region that holds no marker correlates with the other at
     * about 0.2 at best, under [MARKER_MIN_SIMILARITY].
     *
     * [sweepStart] is where the sweep begins in the capture, from the impulse
     * response; the markers are searched for ±20 ms around where that puts them.
     */
    fun markerDriftPpm(capture: DoubleArray, sweepStart: Int, fs: Int, seconds: Double = Sweep.SECONDS): Double? {
        val template = Sweep.marker(fs)
        val search = (MARKER_SEARCH_S * fs).roundToInt()
        val first = sweepStart - (Sweep.SWEEP_OFFSET_SECONDS * fs).roundToInt()
        val spacing = Sweep.markerSpacingSeconds(seconds) * fs
        val second = first + spacing.roundToInt()
        if (first - search < 0 || second + 2 * search + template.size > capture.size) return null
        val c1 = matched(capture, template, first - search, 2 * search + 1)
        val c2 = matched(capture, template, second - 2 * search, 4 * search + 1)
        val e1 = c1.sumOf { it * it }
        if (e1 <= 0.0) return null
        val score = DoubleArray(2 * search + 1)
        var best = 0
        for (l in score.indices) {
            var s = 0.0
            for (k in c1.indices) s += c1[k] * c2[k + l]
            score[l] = s
            if (s > score[best]) best = l
        }
        if (best == 0 || best == score.size - 1 || score[best] <= 0.0) return null
        var e2 = 0.0
        for (k in c1.indices) e2 += c2[k + best] * c2[k + best]
        val similarity = score[best] / sqrt(e1 * e2)
        if (similarity < MARKER_MIN_SIMILARITY || e2 / e1 !in 0.25..4.0) return null
        val y0 = score[best - 1]; val y1 = score[best]; val y2 = score[best + 1]
        val curve = y0 - 2.0 * y1 + y2
        val frac = if (curve < 0.0) (0.5 * (y0 - y2) / curve).coerceIn(-0.5, 0.5) else 0.0
        val measured = (second - first) + (best - search) + frac
        return (measured / spacing - 1.0) * 1e6
    }

    /** [x] correlated with [template] at [count] lags from [from]. */
    private fun matched(x: DoubleArray, template: DoubleArray, from: Int, count: Int): DoubleArray =
        DoubleArray(count) { lag ->
            var s = 0.0
            val at = from + lag
            for (k in template.indices) s += x[at + k] * template[k]
            s
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

    /**
     * T20 per octave, [RT60_OCTAVES_HZ] (1.3.2). A broadband T20 follows
     * whichever octaves hold the most energy, the treble in practice: in a
     * room whose 125 Hz octave rang for 0.70 s it read 0.457 s. ISO 3382-2
     * times each octave, and the mid-frequency (500 Hz-1 kHz) value is the
     * one the Schroeder frequency is defined on.
     *
     * The response from [BAND_NOISE_FROM_S] to 1.5 s after the arrival is
     * transformed once; each octave is a third-order Butterworth band-pass
     * magnitude applied with no phase, so the filter neither delays the
     * decay nor smears it forward. Its noise is read from the same filtered
     * response ahead of the arrival, stopping [OCTAVE_NOISE_TO_S] short of it
     * where the zero-phase filter's own ringing is.
     */
    fun octaveDecays(ir: DoubleArray, peak: Int, fs: Int): Map<Double, Double?> {
        val out = LinkedHashMap<Double, Double?>()
        val from = max(0, peak + (BAND_NOISE_FROM_S * fs).toInt())
        val to = min(ir.size, peak + (RT60_MAX_TAIL_S * fs).toInt())
        val len = to - from
        if (len <= 0) return RT60_OCTAVES_HZ.associateWithTo(out) { null }
        val n = Fft.nextPow2(2 * len)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val fade = max(1, fs / 50) // 20 ms, so the cut at 1.5 s does not ring through the filters
        for (i in 0 until len) {
            val w = if (i >= len - fade) 0.5 * (1.0 + cos(PI * (i - (len - fade)) / fade)) else 1.0
            re[i] = ir[from + i] * w
        }
        Fft.transform(re, im)
        val arrival = peak - from
        val reach = max(1, fs / 100)
        for (fc in RT60_OCTAVES_HZ) {
            if (fc * sqrt(2.0) >= BAND_TOP_FRACTION * fs) { out[fc] = null; continue }
            val bRe = DoubleArray(n)
            val bIm = DoubleArray(n)
            for (k in 1..n / 2) {
                val f = k.toDouble() * fs / n
                val x = (f / fc - fc / f) / OCTAVE_BANDWIDTH
                val g = 1.0 / sqrt(1.0 + x * x * x * x * x * x)
                bRe[k] = re[k] * g; bIm[k] = im[k] * g
                if (k < n / 2) { bRe[n - k] = re[n - k] * g; bIm[n - k] = im[n - k] * g }
            }
            Fft.transform(bRe, bIm, inverse = true)
            var at = arrival
            for (i in max(0, arrival - reach)..min(len - 1, arrival + reach)) if (abs(bRe[i]) > abs(bRe[at])) at = i
            val noise = meanSquare(bRe, at + (BAND_NOISE_FROM_S * fs).toInt(), at + (OCTAVE_NOISE_TO_S * fs).toInt())
            out[fc] = decay(bRe.copyOf(len), at, fs, noise).rt60Seconds
        }
        return out
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

    /** The ISO 1/3-octave bands an [nfft]-point transform at [fs] can hold: centres and the bins each spans. */
    private class BandGrid(val centres: DoubleArray, val first: IntArray, val last: IntArray, val nearest: IntArray)

    private fun bandGrid(fs: Int, nfft: Int): BandGrid {
        val binHz = fs.toDouble() / nfft
        val top = minOf(Sweep.bandTopHz(fs), BAND_TOP_FRACTION * fs, DspConstants.F_MAX * 2.0.pow(1.0 / 6.0))
        val centres = mutableListOf<Double>()
        val first = mutableListOf<Int>()
        val last = mutableListOf<Int>()
        val nearest = mutableListOf<Int>()
        for (c in DspConstants.ISO_CENTRES_HZ) {
            val exact = 1000.0 * 2.0.pow((3.0 * log2(c / 1000.0)).roundToInt() / 3.0)
            val lo = exact * 2.0.pow(-1.0 / 6.0)
            val hi = exact * 2.0.pow(1.0 / 6.0)
            if (c < MIN_BAND_HZ || hi > top || c > DspConstants.F_MAX) continue
            centres += c
            first += max(1, kotlin.math.ceil(lo / binHz).toInt())
            last += min(nfft / 2, kotlin.math.floor(hi / binHz).toInt())
            nearest += (exact / binHz).roundToInt().coerceIn(1, nfft / 2)
        }
        return BandGrid(centres.toDoubleArray(), first.toIntArray(), last.toIntArray(), nearest.toIntArray())
    }

    /** Mean power per band of [chunk]'s spectrum: a band too narrow for a whole bin reads its nearest bin. */
    private fun bandPowers(chunk: DoubleArray, grid: BandGrid): DoubleArray {
        val re = chunk.copyOf()
        val im = DoubleArray(chunk.size)
        Fft.transform(re, im)
        return DoubleArray(grid.centres.size) { b ->
            val a = grid.first[b]
            val z = grid.last[b]
            if (z >= a) {
                var s = 0.0
                for (k in a..z) s += re[k] * re[k] + im[k] * im[k]
                s / (z - a + 1)
            } else {
                val k = grid.nearest[b]
                re[k] * re[k] + im[k] * im[k]
            }
        }
    }

    /** 1/3-octave magnitudes (dB, relative) at the ISO centres from [MIN_BAND_HZ] that the capture rate can hold. */
    fun bandMagnitudes(ir: DoubleArray, peak: Int, fs: Int): Pair<DoubleArray, DoubleArray> {
        val (chunk, _) = windowedChunk(ir, peak, fs)
        val grid = bandGrid(fs, chunk.size)
        val power = bandPowers(chunk, grid)
        val db = DoubleArray(power.size) { 10.0 * log10(max(power[it], 1e-30)) }
        val maxDb = db.maxOrNull() ?: 0.0
        for (k in db.indices) db[k] -= maxDb
        return Pair(grid.centres, db)
    }

    /**
     * Each band's signal over noise in dB, on [bandMagnitudes]' grid (1.3.2).
     * The noise is the same window laid over the response ahead of the
     * arrival, from [BAND_NOISE_FROM_S] to 20 ms before it at half-window
     * steps (four windows at 16 kHz), so it is measured through exactly the
     * filter the band was. The broadband SNR cannot see this: in
     * simulation a rumble that buried the 63 Hz band left it at 46 dB, an
     * excellent score, and white noise 45 dB under the peak is only about
     * 12 dB under a 1/3-octave band of a direct sound.
     */
    fun bandSnrDb(ir: DoubleArray, peak: Int, fs: Int): DoubleArray {
        val (chunk, pre) = windowedChunk(ir, peak, fs)
        val grid = bandGrid(fs, chunk.size)
        val signal = bandPowers(chunk, grid)
        val winLen = (DspConstants.NFFT.toDouble() * fs / DspConstants.FS).roundToInt()
        val end = peak + (NOISE_TO_S * fs).toInt()
        val earliest = max(0, peak + (BAND_NOISE_FROM_S * fs).toInt())
        val noise = DoubleArray(signal.size)
        var windows = 0
        var start = end - winLen
        while (start >= earliest) {
            val (n, _) = windowedChunk(ir, start + pre, fs)
            val p = bandPowers(n, grid)
            for (b in noise.indices) noise[b] += p[b]
            windows++
            start -= winLen / 2
        }
        if (windows == 0) return DoubleArray(signal.size) { SNR_UNMEASURED_DB }
        return DoubleArray(signal.size) { b ->
            val nb = noise[b] / windows
            val excess = signal[b] - nb
            if (nb <= 0.0) SNR_UNMEASURED_DB
            else (10.0 * log10(max(excess, signal[b] * 1e-12) / nb)).coerceAtMost(SNR_UNMEASURED_DB)
        }
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
