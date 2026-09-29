package tv.corebuilds.eq.dsp

import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * What one REW text export produced: the same shape of answer a sweep gives,
 * minus what a magnitude file cannot carry.
 *
 * This M10 path intentionally uses magnitude only, even when REW's export
 * includes an optional phase column. It cannot measure decay or verify the
 * minimum-phase gate: RT60 and Schroeder stay null and
 * [minPhaseGateVerified] is false. The limitation travels with the profile,
 * named and never silently trusted.
 */
data class ImportResult(
    val centresHz: DoubleArray,
    val measuredDb: DoubleArray,
    val targetDb: DoubleArray,
    val correctionDb: DoubleArray,
    val nullMask: BooleanArray,
    val minPhaseGateVerified: Boolean,
    val transitionHz: Double,
    val rolloffHz: Double,
    val floorHz: Double,
    val filters: List<PeakingFilter>,
    val preampDb: Double,
    val pointsRead: Int
)

/**
 * REW measurement import (plan M10, decided from community input
 * `docs/research/core-eq-community-input-2026-09-30.md`): a measurement taken
 * with a real microphone and REW enters the same correction chain as a sweep.
 *
 * [parseMagnitudeText] reads REW's "export measurement as text" and FRD/CSV
 * (comment lines starting `*`, `#` or `//`, then `freq magnitude [phase]`
 * rows separated by whitespace, commas or semicolons). An optional phase
 * column is ignored in this milestone; a file it cannot read is refused with
 * the reason, and its shape is never guessed.
 *
 * [analyze] reduces the imported response onto the sweep chain's own 1/3-octave
 * band grid and then runs the identical steps — level alignment, null
 * detection, roll-off floor, the two-regime correction, the peaking fit — so
 * an imported profile and a swept one are the same maths on different
 * measurements. Pure JVM; pinned by `RewImportTest`.
 */
object RewImport {

    const val MIN_POINTS = 32

    /** (freqs Hz, magnitudes dB), strictly increasing in frequency. */
    fun parseMagnitudeText(text: String): Pair<DoubleArray, DoubleArray> {
        val freqs = mutableListOf<Double>()
        val mags = mutableListOf<Double>()
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("#") || line.startsWith("//")) continue
            val decimalCommaSpace = Regex("""^[+-]?\d+,\d+\s+[+-]?\d+,\d+(?:\s+.*)?$""").matches(line)
            val tokens = when {
                '\t' in line -> line.split('\t')
                ';' in line -> line.split(';')
                decimalCommaSpace -> line.split(Regex("\\s+"))
                ',' in line -> line.split(Regex(",\\s*"))
                else -> line.split(Regex("\\s+"))
            }.map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.size < 2) continue
            // REW uses decimal points normally; some locales use decimal
            // commas with TAB/space/semicolon delimiters.
            val f = tokens[0].replace(',', '.').toDoubleOrNull() ?: continue
            val m = tokens[1].replace(',', '.').toDoubleOrNull() ?: continue
            if (!f.isFinite() || !m.isFinite() || f <= 0.0) continue
            freqs.add(f)
            mags.add(m)
        }
        val frequencies = freqs.toDoubleArray()
        val magnitudes = mags.toDoubleArray()
        validateMeasurement(frequencies, magnitudes)
        return Pair(frequencies, magnitudes)
    }

    /** Reject invalid direct callers too; no assumptions are left to the parser. */
    private fun validateMeasurement(freqs: DoubleArray, magnitudesDb: DoubleArray) {
        if (freqs.size != magnitudesDb.size) {
            throw MeasurementException("The frequency and magnitude columns have different lengths.")
        }
        if (freqs.size < MIN_POINTS) {
            throw MeasurementException(
                "Only ${freqs.size} frequency points could be read (need $MIN_POINTS). " +
                    "REW exports a measurement as text: File → Export → Measurement as text."
            )
        }
        for (i in freqs.indices) {
            if (!freqs[i].isFinite() || !magnitudesDb[i].isFinite() || freqs[i] <= 0.0) {
                throw MeasurementException("The measurement contains a non-finite value or non-positive frequency.")
            }
            if (magnitudesDb[i] !in -300.0..300.0) {
                throw MeasurementException("A magnitude is outside the supported −300 to +300 dB range.")
            }
            if (i > 0 && freqs[i] <= freqs[i - 1]) {
                throw MeasurementException(
                    "Frequencies must run lowest to highest; ${freqs[i - 1]} Hz is followed by ${freqs[i]} Hz."
                )
            }
        }
        if (freqs.first() > DspConstants.F_MIN) {
            throw MeasurementException(
                "This measurement starts at ${"%.0f".format(freqs.first())} Hz; " +
                    "Core EQ corrects from ${DspConstants.F_MIN.toInt()} Hz and will not guess below the data."
            )
        }
        if (freqs.last() < DspConstants.F_MAX) {
            throw MeasurementException(
                "This measurement stops at ${"%.0f".format(freqs.last())} Hz; " +
                    "Core EQ corrects to ${DspConstants.F_MAX.toInt()} Hz and will not guess above the data."
            )
        }
    }

    /**
     * The imported response becomes a correction through the same chain as a
     * sweep. [volumeM3] is the room size the user chose (provenance; with no
     * RT60 the transition is the 300 Hz unknown-room default either way).
     */
    fun analyze(
        freqs: DoubleArray,
        magnitudesDb: DoubleArray,
        target: String,
        volumeM3: Double?,
        nFilters: Int = DspConstants.PEAKING_FILTERS
    ): ImportResult {
        validateMeasurement(freqs, magnitudesDb)
        val centres = bandCentres(freqs)
        if (centres.size < 8) {
            throw MeasurementException("Too few analysis bands fall inside this measurement.")
        }
        val bandDb = bandValues(freqs, magnitudesDb, centres)
        val targetDb = Targets.targetCurve(target, centres)
        val measured = SweepAnalysis.alignTo(centres, bandDb, targetDb)

        val nulls = Correction.detectNulls(centres, measured)
        val rolloff = Correction.detectLowRolloff(centres, measured)
        val floorHz = max(DspConstants.F_MIN, rolloff)
        val transition = Correction.transitionHz(volumeM3, null)
        // No group-delay data: there is no basis for opening or closing the
        // minimum-phase gate. Leave it unverified and carry that warning in
        // ImportResult/profile metadata instead of claiming an all-clear.
        val correction = Correction.calculateCorrectionCurve(
            centres, measured, targetDb,
            fMin = floorHz,
            transitionHz = transition,
            nullMask = nulls
        )
        val filters = Peaking.fitPeakingFilters(centres, correction, nFilters)

        return ImportResult(
            centresHz = centres,
            measuredDb = measured,
            targetDb = targetDb,
            correctionDb = correction,
            nullMask = nulls,
            minPhaseGateVerified = false,
            transitionHz = transition,
            rolloffHz = rolloff,
            floorHz = floorHz,
            filters = filters,
            preampDb = Peaking.preampDb(filters),
            pointsRead = freqs.size
        )
    }

    /**
     * The sweep chain's 1/3-octave grid (SweepAnalysis.bandMagnitudes without
     * the FFT's top rule): ISO centres up to F_MAX whose own band the
     * measurement actually covers. Bands below F_MIN stay — the correction
     * floors at [Correction.detectLowRolloff]'s answer exactly as the sweep
     * chain does, and the graph shows the measured curve below it.
     */
    private fun bandCentres(freqs: DoubleArray): DoubleArray {
        val out = mutableListOf<Double>()
        for (c in DspConstants.ISO_CENTRES_HZ) {
            if (c > DspConstants.F_MAX) continue
            val exact = 1000.0 * 2.0.pow((3.0 * log2(c / 1000.0)).roundToInt() / 3.0)
            if (exact < freqs.first() || exact > freqs.last()) continue
            out.add(c)
        }
        return out.toDoubleArray()
    }

    private fun bandValues(freqs: DoubleArray, magsDb: DoubleArray, centres: DoubleArray): DoubleArray =
        DoubleArray(centres.size) { i ->
            val exact = centres[i]
            val lo = exact * 2.0.pow(-1.0 / 6.0)
            val hi = exact * 2.0.pow(1.0 / 6.0)
            var peakDb = Double.NEGATIVE_INFINITY
            var n = 0
            for (j in freqs.indices) {
                if (freqs[j] in lo..hi) {
                    peakDb = max(peakDb, magsDb[j])
                    n++
                }
            }
            if (n > 0) {
                var relativePower = 0.0
                for (j in freqs.indices) {
                    if (freqs[j] in lo..hi) relativePower += 10.0.pow((magsDb[j] - peakDb) / 10.0)
                }
                peakDb + 10.0 * log10(max(relativePower / n, 1e-30))
            } else {
                interpolateDb(freqs, magsDb, exact)
            }
        }

    /** Log-frequency interpolation for a sparse export with no sample in this band. */
    private fun interpolateDb(freqs: DoubleArray, magsDb: DoubleArray, at: Double): Double {
        if (at <= freqs.first()) return magsDb.first()
        if (at >= freqs.last()) return magsDb.last()
        for (i in 0 until freqs.lastIndex) {
            if (at in freqs[i]..freqs[i + 1]) {
                val fraction = log2(at / freqs[i]) / log2(freqs[i + 1] / freqs[i])
                return magsDb[i] + fraction * (magsDb[i + 1] - magsDb[i])
            }
        }
        return magsDb.last()
    }
}
