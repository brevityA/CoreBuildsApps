package tv.corebuilds.eq.dsp

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Core EQ's curve, translated onto a television's **own** sound-menu equaliser (1.9.0).
 *
 * Most TVs will not let Core EQ touch their built-in speakers, and the ones that do are the
 * exception rather than the rule. Every one of them, though, has a sound menu with a graphic
 * equaliser in it — and the band layout is far narrower than Core EQ's: five bands on most
 * Samsung and LG sets, seven on a Sony running Google TV. That menu is the only way to shape
 * a built-in speaker on most hardware, so this file turns a preset or a measured correction
 * into the numbers a person can type in.
 *
 * Three things are deliberately honest here, because a mapping that hides them would be a
 * lie told in ±1 dB steps:
 *
 * 1. **The bands are not the same shape.** Core EQ shapes with Q≈1 peaking filters on
 *    third-octave centres; a TV's five sliders are much wider and their Q is not published.
 *    Sampling the curve at the slider's centre frequency is the right first-order answer and
 *    is what this does — but the result is a starting point to refine by ear, not an
 *    equivalence.
 * 2. **A band outside 40 Hz–8 kHz is not a correction.** Core EQ refuses to correct there,
 *    so it has nothing to say: a Samsung set's 10 kHz slider is reported as *outside the
 *    trusted band* rather than as "0 dB", which would read as a recommendation to leave the
 *    treble alone.
 * 3. **The TV's own range wins.** A slider that stops at ±6 gets ±6 and the clamped value is
 *    reported, so nobody types in a number their menu cannot accept.
 *
 * The grids are read from the manufacturers' own documentation, not guessed:
 * Samsung and LG publish 100 Hz / 300 Hz / 1 kHz / 3 kHz / 10 kHz; Sony's Google TV
 * equaliser is 125 Hz / 250 Hz / 500 Hz / 1 kHz / 2 kHz / 4 kHz / 8 kHz at ±12 dB. See
 * `docs/research/core-eq-manual-presets-and-tv-sound-eq-2026-10-28.md`.
 */
object TvSoundEq {

    /** One television equaliser: its bands, its range and where the numbers came from. */
    data class Grid(
        val id: String,
        val label: String,
        val centresHz: List<Double>,
        val minDb: Double,
        val maxDb: Double,
        val stepDb: Double = 1.0,
        val source: String = ""
    )

    /**
     * Samsung and LG's built-in equaliser: five bands, ±6 dB. LG's webOS help library and
     * Samsung's sound-settings support pages both name these five frequencies.
     */
    val SAMSUNG_LG_5 = Grid(
        id = "samsung-lg-5",
        label = "Samsung / LG · 5-band",
        centresHz = listOf(100.0, 300.0, 1_000.0, 3_000.0, 10_000.0),
        minDb = -6.0,
        maxDb = 6.0,
        source = "Samsung and LG sound-menu equalisers (100 Hz / 300 Hz / 1 kHz / 3 kHz / 10 kHz)"
    )

    /**
     * Sony's Google TV equaliser: seven bands, ±12 dB (Sony support article 00302857, which
     * also gives 125 Hz–8 kHz as the band set).
     */
    val SONY_GOOGLE_7 = Grid(
        id = "sony-google-7",
        label = "Sony Google TV · 7-band",
        centresHz = listOf(125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0),
        minDb = -12.0,
        maxDb = 12.0,
        source = "Sony Google TV equaliser (125 Hz / 250 Hz / 500 Hz / 1 kHz / 2 kHz / 4 kHz / 8 kHz), ±12 dB"
    )

    val GRIDS = listOf(SAMSUNG_LG_5, SONY_GOOGLE_7)

    /** One slider's value: [db] rounded to the set's step, or null where Core EQ has no claim. */
    data class Value(val hz: Double, val db: Int?, val wantedDb: Double, val clamped: Boolean)

    data class Setting(val grid: Grid, val values: List<Value>) {
        /** Sliders that get a number. */
        val covered: Int get() = values.count { it.db != null }
        /** Sliders whose wanted value the set could not accept. */
        val clampedCount: Int get() = values.count { it.clamped }
        /** The largest magnitude actually written, 0 when nothing is. */
        val largestDb: Int get() = values.mapNotNull { it.db?.let(::abs) }.maxOrNull() ?: 0
        val movesSomething: Boolean get() = values.any { it.db != null && it.db != 0 }
    }

    /**
     * [curve] at [grid]'s centres. A centre outside 40 Hz–8 kHz is reported without a number:
     * Core EQ does not correct there, and printing 0 dB would be a recommendation it has not
     * earned.
     */
    fun setting(curve: (Double) -> Double, grid: Grid): Setting = Setting(
        grid,
        grid.centresHz.map { hz ->
            if (hz < DspConstants.F_MIN || hz > DspConstants.F_MAX) {
                Value(hz, null, 0.0, clamped = false)
            } else {
                val wanted = curve(hz)
                if (!wanted.isFinite()) {
                    Value(hz, 0, 0.0, clamped = false)
                } else {
                    val stepped = (wanted / grid.stepDb).roundToInt() * grid.stepDb
                    val limited = stepped.coerceIn(grid.minDb, grid.maxDb)
                    Value(hz, limited.roundToInt(), wanted, clamped = abs(limited - stepped) > 1e-9)
                }
            }
        }
    )

    fun setting(filters: List<PeakingFilter>, grid: Grid): Setting =
        setting({ hz -> ManualEq.responseDb(filters, hz) }, grid)

    fun setting(preset: ManualEqPreset, grid: Grid): Setting = setting(preset.filters, grid)

    /**
     * A curve given on its own band centres, interpolated in log-frequency between them.
     * Outside the given centres the curve is held flat at its end value: a correction that
     * stopped at 8 kHz has nothing to say about a 10 kHz slider, which [setting] then refuses
     * anyway.
     */
    fun setting(centresHz: DoubleArray, gainsDb: DoubleArray, grid: Grid): Setting {
        require(centresHz.size == gainsDb.size) {
            "Gains (${gainsDb.size}) do not match centres (${centresHz.size})"
        }
        if (centresHz.isEmpty()) return setting({ 0.0 }, grid)
        return setting({ hz -> interpolateAt(centresHz, gainsDb, hz) }, grid)
    }

    /**
     * A saved profile's curve, as `(Hz, dB)` points. Named apart from [setting] because a
     * `List<Pair<…>>` and a `List<PeakingFilter>` erase to the same JVM signature.
     */
    fun fromProfileCurve(profileCurve: List<Pair<Double, Double>>, grid: Grid): Setting {
        val points = profileCurve.filter { it.first.isFinite() && it.second.isFinite() }.sortedBy { it.first }
        if (points.isEmpty()) return setting({ 0.0 }, grid)
        return setting(
            points.map { it.first }.toDoubleArray(),
            points.map { it.second }.toDoubleArray(),
            grid
        )
    }

    /** Log-frequency interpolation, flat at both ends — as a curve is read everywhere else. */
    private fun interpolateAt(xs: DoubleArray, ys: DoubleArray, hz: Double): Double {
        if (hz <= xs.first()) return ys.first()
        if (hz >= xs.last()) return ys.last()
        var i = 0
        while (i < xs.size - 1 && xs[i + 1] < hz) i++
        val lo = xs[i]
        val hi = xs[i + 1]
        val span = kotlin.math.log2(hi / max(lo, 1e-9))
        if (span <= 0.0) return ys[i]
        val t = kotlin.math.log2(hz / max(lo, 1e-9)) / span
        return ys[i] + (ys[i + 1] - ys[i]) * min(max(t, 0.0), 1.0)
    }

    private fun formatHz(hz: Double): String =
        if (hz >= 1_000.0) "${(hz / 1_000.0).let { if (it == it.toInt().toDouble()) it.toInt().toString() else "%.1f".format(it) }} kHz"
        else "${hz.toInt()} Hz"

    /** One line, for the dialog: `100 Hz −2 · 300 Hz −1 · 1 kHz 0 · 10 kHz —`. */
    fun describe(setting: Setting): String = setting.values.joinToString(" · ") { value ->
        val shown = value.db?.let { if (it > 0) "+$it" else "$it" } ?: "—"
        "${formatHz(value.hz)} $shown"
    }

    /**
     * The text a person copies into a TV: the values, what was clamped, what was refused, and
     * the limits of what the numbers mean. [title] names what was mapped (a preset, a saved
     * profile).
     */
    fun report(title: String, settings: List<Setting>): String = buildString {
        append("CORE EQ · TV SOUND MENU VALUES\n")
        append("Mapped from: ").append(title).append('\n')
        append("These are starting points for the TV's own equaliser, not a measurement of it.\n")
        for (setting in settings) {
            append('\n')
            append(setting.grid.label).append('\n')
            append("  ").append(setting.grid.source).append('\n')
            for (value in setting.values) {
                val shown = when {
                    value.db == null -> "    —      outside Core EQ's 40 Hz–8 kHz band"
                    value.db > 0 -> "    +${value.db} dB"
                    value.db < 0 -> "    ${value.db} dB"
                    else -> "     0 dB"
                }
                append(String.format(java.util.Locale.US, "  %6s  %s", formatHz(value.hz), shown.trimStart()))
                if (value.db != null && value.clamped) {
                    append(String.format(
                        java.util.Locale.US,
                        "   (wanted %+.1f dB; this menu stops at %+.0f)",
                        value.wantedDb,
                        if (value.wantedDb > 0) setting.grid.maxDb else setting.grid.minDb
                    ))
                }
                append('\n')
            }
        }
        append("\nThe TV's sliders are wider than Core EQ's filters, and their shape is not\n")
        append("published: sample values are a starting point to adjust by ear, not an\n")
        append("equivalence. Nothing here corrects a room — that is what Measure does.\n")
    }
}
