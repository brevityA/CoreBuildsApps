package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.CorrectionLimits

/**
 * A target response read from a file, not a correction.
 *
 * Two formats:
 *  - **GraphicEQ** (`GraphicEQ: f g; f g; ...`, Equalizer APO / AutoEq), as before.
 *  - **Columns**: one point per line, frequency then gain, separated by spaces, tabs, commas
 *    or semicolons. That is how REW house curves, AutoEq target CSVs and most hand-made
 *    target files are written. Extra columns are ignored (AutoEq's `frequency,raw,...` takes
 *    `raw`), and lines that start with text (a header, a `*` or `#` comment) are skipped.
 *    When a line is split by `;` or tabs, a comma inside a number is a decimal comma.
 *
 * The curve is normalised to 0 dB at 1 kHz, so a target only shapes the balance and
 * never changes the overall level. Each gain is held to the same budget as a correction
 * (+6 dB / −12 dB), so a file cannot ask for more than the corrector is allowed to do.
 * Points outside 20 Hz to 20 kHz are left out with a note, and a curve with more than
 * [MAX_POINTS] points is resampled to that many on a log-frequency grid, because target
 * files are routinely denser than the correction can use. A number that does not parse
 * still rejects the whole file: a half-read curve is worse than none.
 */
object TargetCurveImport {
    const val MAX_BYTES = 64 * 1024
    const val MAX_POINTS = 256
    /** Bounds the work on a hostile file; 64 KB of the shortest valid lines is fewer than this. */
    private const val MAX_INPUT_POINTS = 16_384
    private const val MIN_HZ = 20.0
    private const val MAX_HZ = 20_000.0
    private const val REF_HZ = 1_000.0

    sealed interface Result {
        /** [points] are sorted by frequency, normalised to 0 dB at 1 kHz, and within the budget. */
        data class Ok(val points: List<Pair<Double, Double>>, val notes: List<String>) : Result
        data class Error(val message: String) : Result
    }

    private sealed interface Read {
        data class Points(val points: List<Pair<Double, Double>>) : Read
        data class Failed(val message: String) : Read
    }

    fun parse(text: String): Result {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            return Result.Error("The file is larger than 64 KB. Export the target with fewer points and try again.")
        }
        val graphicLine = text.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("GraphicEQ:", ignoreCase = true) }
        val read = if (graphicLine != null) readGraphicEq(graphicLine) else readColumns(text)
        val raw = when (read) {
            is Read.Failed -> return Result.Error(read.message)
            is Read.Points -> read.points
        }
        val notes = mutableListOf<String>()

        val inRange = raw.filter { (f, _) -> f in MIN_HZ..MAX_HZ }
        val outside = raw.size - inRange.size
        if (outside > 0) {
            notes += "$outside point${if (outside == 1) " was" else "s were"} outside 20 Hz to 20 kHz and left out."
        }
        if (inRange.size < 2) return Result.Error("A target needs at least two points between 20 Hz and 20 kHz.")
        var sorted = inRange.sortedBy { it.first }.distinctBy { it.first }
        if (sorted.size < 2) return Result.Error("A target needs at least two different frequencies.")
        if (sorted.size > MAX_POINTS) {
            notes += "The file's ${sorted.size} points were resampled to $MAX_POINTS."
            sorted = resample(sorted, MAX_POINTS)
        }

        val reference = CorrectionImport.interpolateGraphic(sorted, doubleArrayOf(REF_HZ))[0]
        // Pin 1 kHz before limiting. Without a point there, clamping the two neighbours by
        // different amounts moves the interpolated 1 kHz value off 0 dB (100 Hz −20 dB and
        // 10 kHz +20 dB limit to −12 / +6 and read −3 dB at 1 kHz). The pinned point lies on
        // the normalised curve, so it changes nothing else.
        val anchored = if (sorted.any { it.first == REF_HZ }) sorted
            else (sorted + (REF_HZ to reference)).sortedBy { it.first }
        val normalised = anchored.map { (f, g) -> f to (g - reference) }
        val limited = normalised.map { (f, g) ->
            f to g.coerceIn(-CorrectionLimits.MAX_CUT_DB, CorrectionLimits.MAX_BOOST_DB)
        }
        if (limited.zip(normalised).any { (a, b) -> a.second != b.second }) {
            notes += "Some gains were limited to +6 dB / −12 dB, as for corrections."
        }
        return Result.Ok(limited, notes)
    }

    private fun readGraphicEq(line: String): Read {
        val raw = ArrayList<Pair<Double, Double>>()
        for (entry in line.substringAfter(':').split(';')) {
            if (entry.isBlank()) continue
            val parts = entry.trim().split(Regex("\\s+"))
            if (parts.size != 2) return Read.Failed("Each point must be a frequency and a gain.")
            val point = point(parts[0], parts[1]) ?: return Read.Failed(numberError(parts[0], parts[1]))
            raw += point
            if (raw.size > MAX_INPUT_POINTS) return Read.Failed("The file has too many points to be a target.")
        }
        return Read.Points(raw)
    }

    private fun readColumns(text: String): Read {
        val raw = ArrayList<Pair<Double, Double>>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("#") || line.startsWith("//")) continue
            // ';' or a tab between columns means a comma is the decimal mark (20,5;-3,2).
            // So does exactly two space-separated fields whose first holds a comma ("20,5 -3,2").
            // Without this, "20,5 -3,2" split on commas into four numbers and read as 20 Hz at 5 dB.
            val spaced = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val decimalComma = line.contains(';') || line.contains('\t') ||
                (spaced.size == 2 && spaced[0].contains(','))
            val tokens = line.split(if (decimalComma) Regex("[;\\t ]+") else Regex("[,\\s]+"))
                .filter { it.isNotEmpty() }
                .map { if (decimalComma) it.replace(',', '.') else it }
            // A line that does not start with a number is a header or a comment.
            if (tokens.isEmpty() || tokens[0].toDoubleOrNull() == null) continue
            if (tokens.size < 2) return Read.Failed("Each line must hold a frequency and a gain.")
            val point = point(tokens[0], tokens[1]) ?: return Read.Failed(numberError(tokens[0], tokens[1]))
            raw += point
            if (raw.size > MAX_INPUT_POINTS) return Read.Failed("The file has too many points to be a target.")
        }
        if (raw.isEmpty()) {
            return Read.Failed(
                "No target points found. Choose a GraphicEQ file, or a text or CSV file with frequency and gain columns."
            )
        }
        return Read.Points(raw)
    }

    private fun point(f: String, g: String): Pair<Double, Double>? {
        val hz = f.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val db = g.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return hz to db
    }

    private fun numberError(f: String, g: String): String =
        if (f.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } == null) "A frequency is not a number."
        else "A gain is not a number."

    /** [count] points on a log-frequency grid across the curve's own span, read off the curve. */
    private fun resample(points: List<Pair<Double, Double>>, count: Int): List<Pair<Double, Double>> {
        val lo = points.first().first
        val hi = points.last().first
        val grid = DoubleArray(count) { k -> lo * Math.pow(hi / lo, k.toDouble() / (count - 1)) }
        val db = CorrectionImport.interpolateGraphic(points, grid)
        return grid.indices.map { grid[it] to db[it] }
    }

    /**
     * A curve read back from a saved profile or a backup file, held to what [parse] can
     * produce: finite points within 20 Hz to 20 kHz, gains within the budget, sorted, no
     * repeated frequency, at most [MAX_POINTS] + 1 (the pinned 1 kHz). Unlike [parse] it
     * drops bad points instead of rejecting the curve, so one damaged value in a backup
     * costs that point, not the profile. Returns an empty list when fewer than two remain.
     */
    fun fromStored(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val kept = points
            .filter { (f, g) -> f.isFinite() && g.isFinite() && f in MIN_HZ..MAX_HZ }
            .map { (f, g) -> f to g.coerceIn(-CorrectionLimits.MAX_CUT_DB, CorrectionLimits.MAX_BOOST_DB) }
            .sortedBy { it.first }
            .distinctBy { it.first }
            .take(MAX_POINTS + 1)
        return if (kept.size >= 2) kept else emptyList()
    }

    /** The target in dB at each frequency in [centres]: log-frequency interpolation, flat beyond the ends. */
    fun targetDb(points: List<Pair<Double, Double>>, centres: DoubleArray): DoubleArray =
        CorrectionImport.interpolateGraphic(points, centres)
}
