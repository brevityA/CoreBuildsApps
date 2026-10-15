package tv.corebuilds.eq.dsp

import tv.corebuilds.eq.CorrectionLimits

/**
 * A GraphicEQ text file read as a target response, not as a correction.
 *
 * The curve is normalised to 0 dB at 1 kHz, so a target only shapes the balance and
 * never changes the overall level. Each gain is held to the same budget as a correction
 * (+6 dB / −12 dB), so a file cannot ask for more than the corrector is allowed to do.
 * Parsing is strict; any malformed line rejects the whole file.
 */
object TargetCurveImport {
    const val MAX_BYTES = 64 * 1024
    const val MAX_POINTS = 256
    private const val MIN_HZ = 20.0
    private const val MAX_HZ = 20_000.0
    private const val REF_HZ = 1_000.0

    sealed interface Result {
        /** [points] are sorted by frequency, normalised to 0 dB at 1 kHz, and within the budget. */
        data class Ok(val points: List<Pair<Double, Double>>, val notes: List<String>) : Result
        data class Error(val message: String) : Result
    }

    fun parse(text: String): Result {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            return Result.Error("The file is larger than 64 KB. Export a GraphicEQ text file and try again.")
        }
        val line = text.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("GraphicEQ:", ignoreCase = true) }
            ?: return Result.Error("No GraphicEQ line found. Choose a GraphicEQ text file.")
        val raw = ArrayList<Pair<Double, Double>>()
        for (entry in line.substringAfter(':').split(';')) {
            if (entry.isBlank()) continue
            val parts = entry.trim().split(Regex("\\s+"))
            if (parts.size != 2) return Result.Error("Each point must be a frequency and a gain.")
            val f = parts[0].toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: return Result.Error("A frequency is not a number.")
            val g = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: return Result.Error("A gain is not a number.")
            if (f < MIN_HZ || f > MAX_HZ) return Result.Error("A frequency is outside 20 Hz to 20 kHz.")
            raw += f to g
            if (raw.size > MAX_POINTS) return Result.Error("The file has more than $MAX_POINTS points.")
        }
        if (raw.size < 2) return Result.Error("A target needs at least two points.")
        val sorted = raw.sortedBy { it.first }.distinctBy { it.first }
        if (sorted.size < 2) return Result.Error("A target needs at least two different frequencies.")

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
        val notes = mutableListOf<String>()
        if (limited.zip(normalised).any { (a, b) -> a.second != b.second }) {
            notes += "Some gains were limited to +6 dB / −12 dB, as for corrections."
        }
        return Result.Ok(limited, notes)
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
