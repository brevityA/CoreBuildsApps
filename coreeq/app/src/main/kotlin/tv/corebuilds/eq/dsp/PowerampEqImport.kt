package tv.corebuilds.eq.dsp

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reads the single-preset JSON format exported by Poweramp / Poweramp Equalizer.
 *
 * The JSON does not carry an explicit format version, and public exports do
 * not establish a stable numeric type schema. This parser therefore uses a
 * narrow, best-effort subset for parametric files (3=peaking, 4/5=shelves),
 * skips types 0–2 and all unknown active types, and adds an explicit version-
 * uncertainty warning to every successful parametric import. The type mapping
 * is not a vendor guarantee. Graphic presets (parametric=false) use type 2
 * entries as fixed-frequency gain points; this interpretation is also checked
 * against dated public fixtures. Unsupported data is listed; a file with no
 * supported bands is rejected rather than silently becoming a flat preset.
 *
 * Core EQ applies a single, approximate 10-band curve (Q 1.0, ±6 dB, 0.5 dB
 * steps), not the original Poweramp filters. Stereo-only bands are averaged in
 * dB because Core EQ applies the same correction to both output channels.
 * Source preamp is reported but not carried over; Core EQ calculates its own
 * headroom after fitting. No input is uploaded or sent off-device.
 */
object PowerampEqImport {

    private const val MAX_BANDS = 64
    private const val MAX_PREAMP_DB = 100.0
    private const val MAX_FILTER_GAIN_DB = 60.0
    private const val MIN_FILTER_HZ = 1.0
    private const val MAX_FILTER_HZ = DspConstants.FS * 0.4999
    private const val MIN_Q = 0.05
    private const val MAX_Q = 20.0
    private const val ZERO_GAIN_DB = 1e-9

    // Best-effort numeric mapping for parametric exports; Poweramp publishes no versioned schema.
    private const val TYPE_PEAK = 3
    private const val TYPE_LOW_SHELF = 4
    private const val TYPE_HIGH_SHELF = 5
    private const val TYPE_GRAPHIC_POINT = 2

    private data class Filter(
        val type: Int,
        val frequencyHz: Double,
        val q: Double,
        val gainDb: Double,
        val channels: Int
    )

    /** Parse one native Poweramp preset JSON object or one-object export array. */
    fun parse(text: String): CorrectionImport.Result {
        require(text.length <= CorrectionImport.MAX_CHARS) {
            "The file is larger than 64 KB. Choose a smaller Poweramp JSON preset."
        }
        val json = text.removePrefix("\uFEFF").trim()
        require(json.isNotEmpty()) { "The Poweramp preset file is empty." }

        val preset = try {
            when (json.first()) {
                '[' -> {
                    val presets = JSONArray(json)
                    require(presets.length() == 1) {
                        "This Poweramp JSON contains ${presets.length()} presets. Export one preset at a time and try again."
                    }
                    presets.optJSONObject(0)
                        ?: throw IllegalArgumentException("The Poweramp JSON does not contain a preset object.")
                }
                '{' -> JSONObject(json)
                else -> throw IllegalArgumentException("Choose a Poweramp EQ preset JSON file.")
            }
        } catch (e: JSONException) {
            throw IllegalArgumentException("The Poweramp JSON preset could not be parsed.", e)
        }

        val name = (preset.opt("name") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val parametric = preset.opt("parametric") as? Boolean
            ?: throw IllegalArgumentException("The Poweramp preset is missing its parametric/graphic mode.")
        val bands = preset.optJSONArray("bands")
            ?: throw IllegalArgumentException("The Poweramp preset is missing its bands array.")
        require(bands.length() <= MAX_BANDS) {
            "The Poweramp preset has too many bands (${bands.length()}); at most $MAX_BANDS are accepted."
        }
        val preamp = optionalFiniteNumber(preset, "preamp")
        require(preamp == null || abs(preamp) <= MAX_PREAMP_DB) {
            "The Poweramp preset has an unreasonable preamp value."
        }

        val notes = mutableListOf<String>()
        var missingChannelTags = 0
        var usedSingleChannel = false
        var supportedBandCount = 0
        val grid = CorrectionImport.logGrid()
        val curve = if (parametric) {
            val left = mutableListOf<Filter>()
            val right = mutableListOf<Filter>()
            for (index in 0 until bands.length()) {
                val label = "Poweramp band #${index + 1}"
                val band = bands.optJSONObject(index)
                if (band == null) {
                    notes += "Skipped $label: it is not a JSON object."
                    continue
                }
                val type = integerField(band, "type")
                if (type == null) {
                    notes += "Skipped $label: its type is missing or invalid."
                    continue
                }
                val gain = finiteNumber(band, "gain")
                if (gain == null) {
                    notes += "Skipped $label (type $type): its gain is missing or invalid."
                    continue
                }
                if (abs(gain) < ZERO_GAIN_DB) continue

                // Codes 0–2 are deliberately not assigned a guessed shape. Public examples
                // disagree about their meaning, and Poweramp JSON does not identify a build.
                if (type in 0..2) {
                    notes += "Skipped active $label (type $type): Core EQ does not interpret codes 0–2 because their meaning varies across versionless exports."
                    continue
                }
                if (type !in TYPE_PEAK..TYPE_HIGH_SHELF) {
                    notes += "Skipped active $label (type $type): this numeric type is unknown to Core EQ."
                    continue
                }

                val frequency = finiteNumber(band, "frequency")
                val q = finiteNumber(band, "q")
                if (frequency == null || frequency !in MIN_FILTER_HZ..MAX_FILTER_HZ ||
                    q == null || q !in MIN_Q..MAX_Q || abs(gain) > MAX_FILTER_GAIN_DB
                ) {
                    notes += "Skipped active $label (type $type): frequency, Q or gain is outside the supported range."
                    continue
                }
                val (channels, missingTag) = channelField(band)
                if (missingTag) missingChannelTags++
                if (channels == null) {
                    notes += "Skipped active $label (type $type): its channels value is invalid."
                    continue
                }
                val filter = Filter(type, frequency, q, gain, channels)
                if (channels == 0 || channels == 1) left += filter
                if (channels == 0 || channels == 2) right += filter
                if (channels == 1 || channels == 2) usedSingleChannel = true
                supportedBandCount++
            }
            if (missingChannelTags > 0) {
                notes += "$missingChannelTags supported Poweramp band(s) had no channels field; they were treated as L+R."
            }
            if (usedSingleChannel) {
                notes += "Left-only and right-only filters were averaged into one shared Core EQ curve; Core EQ applies the same correction to both channels."
            }
            if (supportedBandCount > 0) {
                notes += "Poweramp JSON has no schema version. Core EQ assumes type 3=peaking, 4=low shelf and 5=high shelf; this mapping and the filter curves are best-effort. Confirm against your Poweramp build before relying on this result."
            }
            val leftDb = sumFilterResponses(left, grid)
            val rightDb = sumFilterResponses(right, grid)
            DoubleArray(grid.size) { (leftDb[it] + rightDb[it]) * 0.5 }
        } else {
            val leftPoints = mutableListOf<Pair<Double, Double>>()
            val rightPoints = mutableListOf<Pair<Double, Double>>()
            for (index in 0 until bands.length()) {
                val label = "Poweramp graphic band #${index + 1}"
                val band = bands.optJSONObject(index)
                if (band == null) {
                    notes += "Skipped $label: it is not a JSON object."
                    continue
                }
                val type = integerField(band, "type")
                if (type == null) {
                    notes += "Skipped $label: its type is missing or invalid."
                    continue
                }
                val gain = finiteNumber(band, "gain")
                if (gain == null) {
                    notes += "Skipped $label (type $type): its gain is missing or invalid."
                    continue
                }
                if (type != TYPE_GRAPHIC_POINT) {
                    if (abs(gain) >= ZERO_GAIN_DB) {
                        notes += "Skipped active $label (type $type): this code is not a recognized graphic gain point."
                    }
                    continue
                }
                val frequency = finiteNumber(band, "frequency")
                if (frequency == null || frequency !in MIN_FILTER_HZ..MAX_FILTER_HZ || abs(gain) > MAX_FILTER_GAIN_DB) {
                    notes += "Skipped active $label (type $type): frequency or gain is outside the supported range."
                    continue
                }
                val (channels, missingTag) = channelField(band)
                if (missingTag) missingChannelTags++
                if (channels == null) {
                    notes += "Skipped active $label (type $type): its channels value is invalid."
                    continue
                }
                if (channels == 0 || channels == 1) leftPoints += frequency to gain
                if (channels == 0 || channels == 2) rightPoints += frequency to gain
                if ((channels == 1 || channels == 2) && abs(gain) >= ZERO_GAIN_DB) usedSingleChannel = true
                supportedBandCount++
            }
            if (missingChannelTags > 0) {
                notes += "$missingChannelTags Poweramp graphic band(s) had no channels field; they were treated as L+R."
            }
            if (usedSingleChannel) {
                notes += "Left-only and right-only graphic gains were averaged into one shared Core EQ curve."
            }
            if (supportedBandCount > 0) {
                notes += "Graphic slider gains were interpolated and refit to Core EQ's ten fixed bands; this is not a filter-for-filter transfer."
            }
            val leftCurve = CorrectionImport.interpolateGraphic(collapsePoints(leftPoints, notes, "left"), grid)
            val rightCurve = CorrectionImport.interpolateGraphic(collapsePoints(rightPoints, notes, "right"), grid)
            DoubleArray(grid.size) { (leftCurve[it] + rightCurve[it]) * 0.5 }
        }

        if (supportedBandCount == 0) throw noSupportedFilters(notes)
        if (curve.all { abs(it) < ZERO_GAIN_DB }) {
            notes += "The supported Poweramp data produces a flat response over the import grid."
        }
        return CorrectionImport.fitResponseCurve(
            curve = curve,
            grid = grid,
            source = if (parametric) "Poweramp parametric JSON" else "Poweramp graphic JSON",
            preampDb = preamp,
            notes = notes,
            presetName = name
        )
    }

    private fun noSupportedFilters(notes: List<String>): IllegalArgumentException {
        val detail = notes.distinct().joinToString(" ")
        val prefix = "No supported active Poweramp EQ bands were found. Poweramp JSON has no build/schema version, so Core EQ will not guess unsupported type codes."
        val fallback = " Export as AutoEq or Equalizer APO text for a less ambiguous import."
        return IllegalArgumentException(
            if (detail.isBlank()) "$prefix$fallback"
            else "$prefix $detail$fallback"
        )
    }

    private fun sumFilterResponses(filters: List<Filter>, grid: DoubleArray): DoubleArray {
        val total = DoubleArray(grid.size)
        for (filter in filters) {
            val response = when (filter.type) {
                TYPE_PEAK -> Peaking.peakingMagnitudeDb(grid, filter.frequencyHz, filter.q, filter.gainDb)
                TYPE_LOW_SHELF -> shelfMagnitudeDb(grid, filter.frequencyHz, filter.q, filter.gainDb, highShelf = false)
                TYPE_HIGH_SHELF -> shelfMagnitudeDb(grid, filter.frequencyHz, filter.q, filter.gainDb, highShelf = true)
                else -> continue
            }
            for (index in total.indices) total[index] += response[index]
        }
        return total
    }

    /** RBJ cookbook shelf response; Poweramp's internal shelf curve may differ slightly. */
    private fun shelfMagnitudeDb(
        frequenciesHz: DoubleArray,
        frequencyHz: Double,
        q: Double,
        gainDb: Double,
        highShelf: Boolean
    ): DoubleArray {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequencyHz / DspConstants.FS
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        // RBJ shelf slope S corresponding to the exported Q (Q=1/sqrt(2) -> S=1).
        val slope = 1.0 / (2.0 * q * q)
        val alphaTerm = ((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0).coerceAtLeast(0.0)
        val alpha = sinW0 * 0.5 * sqrt(alphaTerm)
        val beta = 2.0 * sqrt(a) * alpha
        val aPlus = a + 1.0
        val aMinus = a - 1.0
        val b0: Double
        val b1: Double
        val b2: Double
        val a0: Double
        val a1: Double
        val a2: Double
        if (highShelf) {
            b0 = a * (aPlus + aMinus * cosW0 + beta)
            b1 = -2.0 * a * (aMinus + aPlus * cosW0)
            b2 = a * (aPlus + aMinus * cosW0 - beta)
            a0 = aPlus - aMinus * cosW0 + beta
            a1 = 2.0 * (aMinus - aPlus * cosW0)
            a2 = aPlus - aMinus * cosW0 - beta
        } else {
            b0 = a * (aPlus - aMinus * cosW0 + beta)
            b1 = 2.0 * a * (aMinus - aPlus * cosW0)
            b2 = a * (aPlus - aMinus * cosW0 - beta)
            a0 = aPlus + aMinus * cosW0 + beta
            a1 = -2.0 * (aMinus + aPlus * cosW0)
            a2 = aPlus + aMinus * cosW0 - beta
        }
        val normB0 = b0 / a0
        val normB1 = b1 / a0
        val normB2 = b2 / a0
        val normA1 = a1 / a0
        val normA2 = a2 / a0
        return DoubleArray(frequenciesHz.size) { index ->
            val w = 2.0 * PI * frequenciesHz[index] / DspConstants.FS
            val cosW = cos(w)
            val sinW = sin(w)
            val cos2W = cos(2.0 * w)
            val sin2W = sin(2.0 * w)
            val numReal = normB0 + normB1 * cosW + normB2 * cos2W
            val numImag = -normB1 * sinW - normB2 * sin2W
            val denReal = 1.0 + normA1 * cosW + normA2 * cos2W
            val denImag = -normA1 * sinW - normA2 * sin2W
            val numeratorPower = numReal * numReal + numImag * numImag
            val denominatorPower = max(denReal * denReal + denImag * denImag, 1e-30)
            10.0 * log10(max(numeratorPower / denominatorPower, 1e-30))
        }
    }

    private fun collapsePoints(
        points: List<Pair<Double, Double>>,
        notes: MutableList<String>,
        channel: String
    ): List<Pair<Double, Double>> {
        val grouped = points.groupBy { it.first }.toSortedMap()
        if (grouped.values.any { it.size > 1 }) {
            notes += "Duplicate Poweramp graphic frequencies on the $channel channel were averaged."
        }
        return grouped.map { (frequency, values) -> frequency to values.map { it.second }.average() }
    }

    private fun channelField(band: JSONObject): Pair<Int?, Boolean> {
        if (!band.has("channels") || band.isNull("channels")) return 0 to true
        val channel = integerField(band, "channels") ?: return null to false
        return (channel.takeIf { it in 0..2 }) to false
    }

    private fun integerField(obj: JSONObject, key: String): Int? {
        val value = obj.opt(key) as? Number ?: return null
        val number = value.toDouble()
        if (!number.isFinite() || number % 1.0 != 0.0 || number !in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) return null
        return number.toInt()
    }

    private fun finiteNumber(obj: JSONObject, key: String): Double? =
        (obj.opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }

    private fun optionalFiniteNumber(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = finiteNumber(obj, key)
            ?: throw IllegalArgumentException("The Poweramp preset's $key value must be a finite number.")
        return value
    }
}
