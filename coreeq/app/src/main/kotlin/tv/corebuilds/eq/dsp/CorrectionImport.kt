package tv.corebuilds.eq.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.round

/**
 * Imports a correction text file (AutoEq ParametricEQ, Equalizer APO or
 * GraphicEQ) and fits it to the 10 fixed bands of the manual EQ.
 *
 * The fit is a best effort. The editor has 10 bands at Q 1.0, ±6 dB, in 0.5 dB
 * steps, so a file with more bands, other filter types or a larger gain is
 * approximated, and [Result.maxErrorDb] says by how much. Filters other than
 * peaking (PK), and the preamp, are not applied; they are listed in
 * [Result.notes]. The file is never sent anywhere; it is parsed on the device.
 *
 * Pure Kotlin on purpose: the parser and the fit are unit-tested without Android.
 */
object CorrectionImport {

    /** Files larger than this are refused; a correction file is a few kilobytes. */
    const val MAX_CHARS = 64_000

    private const val GRID_POINTS = 96
    private const val LOW_HZ = 40.0
    private const val HIGH_HZ = 8_000.0
    private const val MAX_ITERATIONS = 60
    private const val POLISH_PASSES = 4

    data class Result(
        /** Fitted filters on the 10 fixed bands, sanitised, sorted by frequency. */
        val filters: List<PeakingFilter>,
        /** Largest difference, in dB, between the file's curve and the fitted curve, over 40 Hz to 8 kHz. */
        val maxErrorDb: Double,
        /** "ParametricEQ" or "GraphicEQ". */
        val source: String,
        /** The file's preamp, if it had one. It is reported, never applied. */
        val preampDb: Double?,
        /** Plain-English notes about anything skipped, clamped or approximated. */
        val notes: List<String>
    )

    private val preampLine = Regex("""^preamp\s*:\s*(-?\d+(?:\.\d+)?)\s*db""", RegexOption.IGNORE_CASE)
    private val filterLine = Regex("""^filter\s*\d*\s*:\s*(on|off)\s+(\S+)(.*)$""", RegexOption.IGNORE_CASE)
    private val fcToken = Regex("""\bfc\s+(\d+(?:\.\d+)?)\s*hz""", RegexOption.IGNORE_CASE)
    private val gainToken = Regex("""\bgain\s+(-?\d+(?:\.\d+)?)\s*db""", RegexOption.IGNORE_CASE)
    private val qToken = Regex("""\bq\s+(\d+(?:\.\d+)?)\b""", RegexOption.IGNORE_CASE)

    /**
     * Parse the text and fit it. Throws [IllegalArgumentException] with a message
     * the user can read when the file is too large or holds no correction.
     */
    fun parse(text: String): Result {
        require(text.length <= MAX_CHARS) {
            "The file is larger than 64 KB. Export a ParametricEQ or GraphicEQ text file and try again."
        }
        val notes = mutableListOf<String>()
        val parametric = mutableListOf<PeakingFilter>()
        var graphic: List<Pair<Double, Double>>? = null
        var preamp: Double? = null
        var recognised = false
        var ignored = 0

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val preampMatch = preampLine.find(line)
            val filterMatch = filterLine.find(line)
            when {
                preampMatch != null -> {
                    recognised = true
                    preamp = preampMatch.groupValues[1].toDouble()
                }
                line.startsWith("GraphicEQ:", ignoreCase = true) -> {
                    recognised = true
                    graphic = parseGraphic(line.substringAfter(':'))
                    if (graphic == null) ignored++
                }
                filterMatch != null -> {
                    recognised = true
                    val (state, type, rest) = filterMatch.destructured
                    if (state.equals("off", ignoreCase = true)) continue
                    val kind = type.uppercase()
                    if (kind != "PK" && kind != "PEQ") {
                        notes += "Skipped a $kind filter: only peaking (PK) filters are fitted."
                        continue
                    }
                    val fc = fcToken.find(rest)?.groupValues?.get(1)?.toDoubleOrNull()
                    val gain = gainToken.find(rest)?.groupValues?.get(1)?.toDoubleOrNull()
                    val q = qToken.find(rest)?.groupValues?.get(1)?.toDoubleOrNull()
                    if (fc == null || gain == null || q == null || fc <= 0.0 || q <= 0.0) {
                        notes += "Skipped a PK filter with a missing or invalid Fc, Gain or Q."
                        continue
                    }
                    parametric += PeakingFilter(fc, q, gain)
                }
                else -> ignored++
            }
        }

        require(recognised) {
            "No correction found. Choose a ParametricEQ or GraphicEQ text file (AutoEq or Equalizer APO)."
        }
        if (ignored > 0) notes += "$ignored line(s) were not understood and were ignored."

        val useGraphic = parametric.isEmpty() && graphic != null
        if (!useGraphic && graphic != null && parametric.isNotEmpty()) {
            notes += "The file has both a ParametricEQ and a GraphicEQ curve; the ParametricEQ filters were used."
        }
        if (parametric.isEmpty() && graphic == null) {
            notes += "The file has no active peaking (PK) filters to fit."
        }

        val grid = logGrid()
        val curve = if (useGraphic) {
            interpolateGraphic(graphic!!)
        } else {
            DoubleArray(grid.size) { k ->
                var sum = 0.0
                for (filter in parametric) {
                    sum += Peaking.peakingMagnitudeDb(doubleArrayOf(grid[k]), filter.fc, filter.q, filter.gain)[0]
                }
                sum
            }
        }
        val peak = curve.maxOfOrNull { abs(it) } ?: 0.0
        if (peak > ManualEq.MAX_GAIN_DB + 1e-9) {
            notes += String.format(
                java.util.Locale.US,
                "The file asks for up to %.1f dB; the editor stops at ±6 dB, so the peaks are limited.",
                peak
            )
        }

        val gains = fitBands(curve, grid)
        val filters = ManualEq.BAND_CENTRES_HZ.mapIndexedNotNull { i, centre ->
            if (abs(gains[i]) < 1e-9) null else PeakingFilter(centre, ManualEq.DEFAULT_Q, gains[i])
        }
        val maxError = grid.indices.maxOf { k ->
            abs(curve[k] - ManualEq.responseDb(filters, grid[k]))
        }
        if (preamp != null) {
            notes += "The file's preamp (${String.format(java.util.Locale.US, "%.1f", preamp)} dB) was not applied. " +
                "If the result clips, lower the volume."
        }
        return Result(
            filters = ManualEq.sanitize(filters),
            maxErrorDb = maxError,
            source = if (useGraphic) "GraphicEQ" else "ParametricEQ",
            preampDb = preamp,
            notes = notes
        )
    }

    private fun parseGraphic(body: String): List<Pair<Double, Double>>? {
        val points = body.split(';').mapNotNull { part ->
            val tokens = part.trim().split(Regex("\\s+"))
            if (tokens.size != 2) return@mapNotNull null
            val f = tokens[0].toDoubleOrNull() ?: return@mapNotNull null
            val g = tokens[1].toDoubleOrNull() ?: return@mapNotNull null
            if (!f.isFinite() || !g.isFinite() || f <= 0.0) null else f to g
        }.sortedBy { it.first }
        return if (points.size >= 2) points else null
    }

    /** Linear in dB, log in frequency, clamped to the end points outside the file's range. */
    private fun interpolateGraphic(points: List<Pair<Double, Double>>): DoubleArray {
        val grid = logGrid()
        return DoubleArray(grid.size) { k ->
            val f = grid[k]
            when {
                f <= points.first().first -> points.first().second
                f >= points.last().first -> points.last().second
                else -> {
                    val upper = points.indexOfFirst { it.first >= f }
                    val (f0, g0) = points[upper - 1]
                    val (f1, g1) = points[upper]
                    val t = (ln(f) - ln(f0)) / (ln(f1) - ln(f0))
                    g0 + t * (g1 - g0)
                }
            }
        }
    }

    private fun logGrid(): DoubleArray = DoubleArray(GRID_POINTS) { k ->
        LOW_HZ * Math.pow(HIGH_HZ / LOW_HZ, k.toDouble() / (GRID_POINTS - 1))
    }

    /**
     * Fit the 10 fixed bands to [target] over [grid]. A damped Gauss-Newton pass
     * finds continuous gains (clamped to ±6 dB), then the gains snap to 0.5 dB and a
     * coordinate pass polishes them. Plain coordinate descent from zero stalls at
     * local minima, which is why the continuous pass comes first.
     */
    internal fun fitBands(target: DoubleArray, grid: DoubleArray): DoubleArray {
        val n = ManualEq.BAND_CENTRES_HZ.size
        val q = ManualEq.DEFAULT_Q
        val centres = ManualEq.BAND_CENTRES_HZ

        fun model(gains: DoubleArray): DoubleArray = DoubleArray(grid.size) { k ->
            var sum = 0.0
            for (i in 0 until n) {
                if (gains[i] != 0.0) {
                    sum += Peaking.peakingMagnitudeDb(doubleArrayOf(grid[k]), centres[i], q, gains[i])[0]
                }
            }
            sum
        }
        fun sse(gains: DoubleArray): Double {
            val m = model(gains)
            var total = 0.0
            for (k in grid.indices) {
                val d = target[k] - m[k]
                total += d * d
            }
            return total
        }
        fun clamp(x: Double) = x.coerceIn(ManualEq.MIN_GAIN_DB, ManualEq.MAX_GAIN_DB)

        var gains = DoubleArray(n)
        var current = sse(gains)
        var lambda = 1e-2
        for (iteration in 0 until MAX_ITERATIONS) {
            val m = model(gains)
            val residual = DoubleArray(grid.size) { target[it] - m[it] }
            val jacobian = Array(grid.size) { DoubleArray(n) }
            for (i in 0 until n) {
                val bumped = gains.copyOf()
                bumped[i] += 1e-4
                val mb = model(bumped)
                for (k in grid.indices) jacobian[k][i] = (mb[k] - m[k]) / 1e-4
            }
            val a = Array(n) { i -> DoubleArray(n) { j -> grid.indices.sumOf { k -> jacobian[k][i] * jacobian[k][j] } } }
            val b = DoubleArray(n) { i -> grid.indices.sumOf { k -> jacobian[k][i] * residual[k] } }
            var improved = false
            while (lambda < 1e6) {
                val damped = Array(n) { i ->
                    DoubleArray(n) { j -> a[i][j] + if (i == j) lambda * (1.0 + a[i][i]) else 0.0 }
                }
                val step = solve(damped, b)
                val candidate = DoubleArray(n) { i -> clamp(gains[i] + step[i]) }
                val e = sse(candidate)
                if (e < current) {
                    gains = candidate
                    current = e
                    lambda = max(lambda / 3.0, 1e-7)
                    improved = true
                    break
                }
                lambda *= 4.0
            }
            if (!improved) break
        }

        gains = DoubleArray(n) { i -> clamp(round(gains[i] / ManualEq.STEP_DB) * ManualEq.STEP_DB) }
        repeat(POLISH_PASSES) {
            for (i in 0 until n) {
                var bestGain = gains[i]
                var bestError = sse(gains)
                for (step in -12..12) {
                    val trial = gains.copyOf()
                    trial[i] = step * ManualEq.STEP_DB
                    val e = sse(trial)
                    if (e < bestError - 1e-9) {
                        bestError = e
                        bestGain = trial[i]
                    }
                }
                gains[i] = bestGain
            }
        }
        return gains
    }

    /** Gaussian elimination with partial pivoting. A singular row contributes zero. */
    private fun solve(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val n = rhs.size
        val m = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) matrix[r][c] else rhs[r] } }
        for (c in 0 until n) {
            var pivot = c
            for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[pivot][c])) pivot = r
            val tmp = m[c]; m[c] = m[pivot]; m[pivot] = tmp
            if (abs(m[c][c]) < 1e-14) continue
            for (r in 0 until n) {
                if (r == c) continue
                val f = m[r][c] / m[c][c]
                for (k in c..n) m[r][k] -= f * m[c][k]
            }
        }
        return DoubleArray(n) { i -> if (abs(m[i][i]) < 1e-14) 0.0 else m[i][n] / m[i][i] }
    }
}
