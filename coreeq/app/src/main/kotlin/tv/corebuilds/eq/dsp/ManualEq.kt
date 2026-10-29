package tv.corebuilds.eq.dsp

import kotlin.math.abs

/**
 * A reusable manual graphic-EQ preset. Filters use the same RBJ peaking model as exports.
 *
 * [description] says what the preset does to the sound and, where it matters, what it
 * cannot do — the picker shows it when the preset is applied. It is defaulted so presets
 * saved before it existed still load, and it never takes part in matching: two presets are
 * the same preset when their filters are ([ManualEq.sameFilters]).
 */
data class ManualEqPreset(
    val id: String,
    val name: String,
    val filters: List<PeakingFilter>,
    val description: String = ""
)

/**
 * Manual tone shaping layered after the measured room correction.
 *
 * The editor exposes ten fixed frequency controls over Core EQ's trusted
 * 40 Hz–8 kHz span. Each control is a peaking filter; the applied engine
 * samples the combined response at its own band centres. The response is
 * deliberately zero outside the trusted span, so a manual preset cannot
 * extend Core EQ's measurement claim into frequencies it does not correct.
 */
object ManualEq {
    const val MIN_GAIN_DB = -6.0
    const val MAX_GAIN_DB = 6.0
    const val STEP_DB = 0.5
    const val DEFAULT_Q = 1.0

    val BAND_CENTRES_HZ = listOf(
        40.0, 63.0, 125.0, 250.0, 500.0,
        1_000.0, 2_000.0, 4_000.0, 6_300.0, 8_000.0
    )

    /**
     * The built-in tone presets: the four that shipped first, then the generic set (1.9.0).
     *
     * Every one is a *tone*, not a measurement. Nothing here claims to fix a room — that is
     * what the corrective path does — and none of them asks for bass a speaker cannot make
     * or for width a stereo pair cannot give. What each is for, and what it costs, is in its
     * [ManualEqPreset.description]; the picker shows that text when one is applied.
     *
     * The gains are deliberately modest. A TV's built-in speakers have 2–3 dB of usable
     * headroom before the amplifier or the driver gives up, and the loudspeaker research is
     * blunt about it: stay inside ±3 dB of tonal shaping and fix the rest with the room
     * correction (see `docs/research/core-eq-manual-presets-and-tv-sound-eq-2026-10-28.md`).
     * The two exceptions are the cuts at the bottom of *Small speaker · body* and *Night ·
     * less boom*, which remove energy a small driver cannot reproduce.
     */
    val BUILT_IN_PRESETS = listOf(
        ManualEqPreset(
            "flat", "Flat", emptyList(),
            "No tone shaping. The measured correction, if any, is what you hear."
        ),
        ManualEqPreset(
            "bass-lift",
            "Bass lift",
            listOf(
                PeakingFilter(63.0, 0.8, 3.0),
                PeakingFilter(125.0, 0.9, 2.0),
                PeakingFilter(250.0, 0.9, 1.0)
            ),
            "More weight below 250 Hz. Costs headroom and, on small drivers, clarity in the " +
                "lower midrange — it is a taste, not a correction."
        ),
        ManualEqPreset(
            "speech-clarity",
            "Speech clarity",
            listOf(
                PeakingFilter(1_000.0, 0.8, 1.0),
                PeakingFilter(2_000.0, 0.9, 2.0),
                PeakingFilter(4_000.0, 0.9, 1.5),
                PeakingFilter(6_300.0, 0.8, 0.5)
            ),
            "Lifts the 1–6 kHz octave where consonants live. The gentlest of the dialogue " +
                "presets; it leaves the bass as it is."
        ),
        ManualEqPreset(
            "less-bass",
            "Less bass",
            listOf(
                PeakingFilter(40.0, 0.8, -3.0),
                PeakingFilter(63.0, 0.9, -2.0),
                PeakingFilter(125.0, 0.9, -1.0)
            ),
            "Takes weight out from under 125 Hz without touching the midrange, so speech " +
                "stops competing with the low end. Frees excursion and headroom."
        ),
        // ---- The generic set (1.9.0) --------------------------------------
        ManualEqPreset(
            "night-quiet",
            "Night · less boom",
            listOf(
                PeakingFilter(40.0, 0.8, -3.0),
                PeakingFilter(63.0, 0.9, -3.0),
                PeakingFilter(125.0, 0.9, -2.0),
                PeakingFilter(250.0, 0.9, -1.0),
                PeakingFilter(1_000.0, 0.9, 0.5),
                PeakingFilter(2_000.0, 0.9, 1.0)
            ),
            "For late-night listening at low volume: the boom comes down, the intelligibility " +
                "band comes up a touch. It does not compress anything — if the gap between " +
                "whispers and explosions is the problem, that is the TV's night mode, not EQ."
        ),
        ManualEqPreset(
            "warm",
            "Warm",
            listOf(
                PeakingFilter(125.0, 0.9, 2.0),
                PeakingFilter(250.0, 0.9, 1.5),
                PeakingFilter(500.0, 0.9, 0.5),
                PeakingFilter(4_000.0, 0.9, -0.5),
                PeakingFilter(6_300.0, 0.9, -1.0),
                PeakingFilter(8_000.0, 0.9, -1.0)
            ),
            "A downward tilt from the low mids up: fuller, softer, easier on a bright TV or a " +
                "hard reflective room. Speech loses a little crispness."
        ),
        ManualEqPreset(
            "bright",
            "Bright · detail",
            listOf(
                PeakingFilter(125.0, 0.9, -1.0),
                PeakingFilter(2_000.0, 0.9, 1.0),
                PeakingFilter(4_000.0, 0.9, 1.5),
                PeakingFilter(6_300.0, 0.9, 1.5),
                PeakingFilter(8_000.0, 0.9, 1.0)
            ),
            "The mirror of Warm: a tilt back toward the top. Helps a dull or curtained room; " +
                "overdone it turns sibilance into a hiss, so it gives a little back at 125 Hz."
        ),
        ManualEqPreset(
            "podcast",
            "Podcast · voice forward",
            listOf(
                PeakingFilter(63.0, 0.9, -2.0),
                PeakingFilter(125.0, 0.9, -1.5),
                PeakingFilter(1_000.0, 0.9, 1.5),
                PeakingFilter(2_000.0, 0.9, 2.0),
                PeakingFilter(4_000.0, 0.9, 1.0)
            ),
            "Speech clarity with the low end pulled out of the way — the shape most TV and " +
                "soundbar \"clear voice\" modes use. Voices sit forward; music will sound thin."
        ),
        ManualEqPreset(
            "sibilance-tame",
            "Tame sibilance",
            listOf(
                PeakingFilter(4_000.0, 0.9, -1.0),
                PeakingFilter(6_300.0, 0.9, -2.0),
                PeakingFilter(8_000.0, 0.9, -2.5)
            ),
            "Cuts the 4–8 kHz band where \"s\" and \"t\" live, for a presenter who sounds " +
                "sharp or a TV that emphasises the top. Also takes some air out of music."
        ),
        ManualEqPreset(
            "small-speaker-body",
            "Small speaker · body",
            listOf(
                PeakingFilter(40.0, 0.8, -3.0),
                PeakingFilter(63.0, 0.9, -2.5),
                PeakingFilter(125.0, 0.9, 2.0),
                PeakingFilter(250.0, 0.9, 1.5),
                PeakingFilter(500.0, 0.9, 0.5)
            ),
            "For TV speakers and small bars that cannot move air below 100 Hz: stop asking " +
                "them to (the 40 and 63 Hz cuts are where the distortion and the excursion " +
                "go), then add body where they can still work. It cannot create bass."
        ),
        ManualEqPreset(
            "remove-boom",
            "Remove boom",
            listOf(
                PeakingFilter(125.0, 0.9, -3.0),
                PeakingFilter(250.0, 0.9, -2.5),
                PeakingFilter(500.0, 0.9, -1.0),
                PeakingFilter(1_000.0, 0.9, 0.5)
            ),
            "For a chesty, boxy 100–500 Hz region — a TV against a wall, or a bar in a " +
                "corner. Different from Less bass, which works below 125 Hz."
        ),
        ManualEqPreset(
            "low-volume-loudness",
            "Low-volume loudness",
            listOf(
                PeakingFilter(40.0, 0.9, 1.0),
                PeakingFilter(63.0, 0.9, 2.0),
                PeakingFilter(125.0, 0.9, 1.0),
                PeakingFilter(4_000.0, 0.9, 1.0),
                PeakingFilter(8_000.0, 0.9, 1.5)
            ),
            "The classic equal-loudness contour: at low volume the ear loses both ends first, " +
                "so this lifts them. Sounds right quietly and too bright loud — that is the " +
                "point, so use it only while the volume is down."
        )
    )

    /** The gain shown by a fixed control, or 0 dB if it has not been changed. */
    fun gainAtBand(filters: List<PeakingFilter>, bandIndex: Int): Double {
        val centre = BAND_CENTRES_HZ.getOrNull(bandIndex) ?: return 0.0
        return filters.firstOrNull { abs(it.fc - centre) < 0.01 }?.gain ?: 0.0
    }

    /** Replace one fixed graphic band, snapping to 0.5 dB and the safe ±6 dB UI range. */
    fun withBandGain(filters: List<PeakingFilter>, bandIndex: Int, requestedDb: Double): List<PeakingFilter> {
        val centre = BAND_CENTRES_HZ.getOrNull(bandIndex)
            ?: throw IndexOutOfBoundsException("Manual EQ band $bandIndex does not exist")
        require(requestedDb.isFinite()) { "Manual EQ gain must be finite" }
        val gain = (kotlin.math.round(requestedDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) / STEP_DB) * STEP_DB)
        val kept = filters.filterNot { abs(it.fc - centre) < 0.01 }
        if (abs(gain) < STEP_DB / 2.0) return kept.sortedBy { it.fc }
        return (kept + PeakingFilter(centre, DEFAULT_Q, gain)).sortedBy { it.fc }
    }

    /**
     * Merge a pre-mode manual EQ into an existing overlay: preserve untouched
     * legacy bands, but let a newer overlay value win at a duplicated centre.
     */
    fun mergeLegacyWithOverlay(legacy: List<PeakingFilter>, overlay: List<PeakingFilter>): List<PeakingFilter> =
        sanitize(legacy + overlay)

    /** Keep stored/custom presets finite, in range, unique by centre, and in the app's correction span. */
    fun sanitize(filters: List<PeakingFilter>): List<PeakingFilter> {
        val byCentre = linkedMapOf<Double, PeakingFilter>()
        for (filter in filters) {
            if (!filter.fc.isFinite() || filter.fc !in 40.0..8_000.0) continue
            if (!filter.q.isFinite() || filter.q !in 0.4..4.0) continue
            if (!filter.gain.isFinite()) continue
            val gain = filter.gain.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
            if (abs(gain) < 0.05) {
                byCentre.remove(filter.fc)
            } else {
                byCentre[filter.fc] = filter.copy(gain = gain)
            }
        }
        return byCentre.values.sortedBy { it.fc }
    }

    /** Combined magnitude of the manual peaking filters, in dB. */
    fun responseDb(filters: List<PeakingFilter>, hz: Double): Double {
        if (!hz.isFinite() || hz !in 40.0..8_000.0 || filters.isEmpty()) return 0.0
        var response = 0.0
        for (filter in filters) {
            response += Peaking.peakingMagnitudeDb(doubleArrayOf(hz), filter.fc, filter.q, filter.gain)[0]
        }
        return response
    }

    fun matchingPreset(filters: List<PeakingFilter>, presets: List<ManualEqPreset>): ManualEqPreset? =
        presets.firstOrNull { sameFilters(filters, it.filters) }

    fun sameFilters(first: List<PeakingFilter>, second: List<PeakingFilter>): Boolean {
        val a = sanitize(first)
        val b = sanitize(second)
        if (a.size != b.size) return false
        return a.indices.all { index ->
            abs(a[index].fc - b[index].fc) < 0.01 &&
                abs(a[index].q - b[index].q) < 0.01 &&
                abs(a[index].gain - b[index].gain) < 0.01
        }
    }
}
