package tv.corebuilds.eq.dsp

/**
 * Tone presets for named playback hardware, and the detection that picks
 * them.
 *
 * A preset is a manual tone overlay in the same ten fixed bands as every
 * other preset (see [ManualEq]); it sits on top of the measured correction and
 * never replaces it. Each model's presets start from its published
 * specification and from what listening reviews describe. No model here has
 * a measured frequency response from Core EQ's own microphone, so these are
 * starting points for a room, not a calibration. The notes on each model say
 * which parts come from which sources.
 *
 * Detection is name-based and conservative. It only looks at outputs that can
 * carry a soundbar or receiver's name (HDMI, eARC, Bluetooth, USB, wired), so
 * the TV's own speakers never pick up a soundbar's preset, and a name must
 * match a whole model number so that "Bar 8000" is not read as a Bar 800.
 */
object HardwarePresets {

    /** What kind of device a model is. Stable strings, for the UI and tests. */
    enum class Kind(val label: String) {
        SOUNDBAR("Soundbar")
    }

    /**
     * One real product and its presets.
     *
     * [nameMatch] matches the product name Android reports for the output,
     * e.g. "JBL BAR 800" or "BAR800". It is anchored so a longer number such as
     * "Bar 8000" does not match.
     */
    data class Model(
        val id: String,
        val brand: String,
        val name: String,
        val kind: Kind,
        val nameMatch: Regex,
        /** A name that matches [nameMatch] but is a different product, or null. */
        val nameExclude: Regex?,
        val presets: List<ManualEqPreset>,
        val basis: String
    )

    // Q matches the editor's fixed bands (ManualEq.DEFAULT_Q), so a preset edits the same way.
    private fun bell(fc: Double, gain: Double, q: Double = ManualEq.DEFAULT_Q) = PeakingFilter(fc, q, gain)

    /** JBL Bar 800 (5.1.2 Dolby Atmos soundbar, 10-inch wireless subwoofer). */
    private val JBL_BAR_800 = Model(
        id = "jbl-bar-800",
        brand = "JBL",
        name = "JBL Bar 800",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:jbl[\s_-]*)?bar[\s_-]*800(?![0-9])""", RegexOption.IGNORE_CASE),
        nameExclude = null,
        presets = listOf(
            ManualEqPreset(
                id = "jbl-bar-800-movie",
                name = "JBL Bar 800 · Movie",
                filters = listOf(
                    bell(250.0, -1.0),   // the bar's bass sits under the mids on a small room
                    bell(2_000.0, 1.0),  // dialogue presence
                    bell(4_000.0, 1.5),  // articulation for speech and effects
                    bell(8_000.0, 1.0)   // air for the Atmos height channels
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-800-dialogue",
                name = "JBL Bar 800 · Dialogue",
                filters = listOf(
                    bell(250.0, -1.0),   // keeps the boxy part of the voice down
                    bell(1_000.0, 1.5),  // reviews: dialogue can sound slightly thin
                    bell(2_000.0, 2.0),
                    bell(4_000.0, 1.5),
                    bell(6_300.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-800-music",
                name = "JBL Bar 800 · Music",
                filters = listOf(
                    bell(63.0, -2.0),    // reviews: bass can read as one note on music
                    bell(125.0, -1.0),
                    bell(250.0, -1.0),   // reviews: muddy at high volume
                    bell(4_000.0, 1.0),  // detail back into the upper mids
                    bell(8_000.0, 1.0)
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-800-late-night",
                name = "JBL Bar 800 · Late night",
                filters = listOf(
                    bell(40.0, -3.0),    // less sub rumble for neighbours and sleep
                    bell(63.0, -2.0),
                    bell(125.0, -1.0),
                    bell(2_000.0, 1.5),  // keeps speech readable at low volume
                    bell(4_000.0, 1.0)
                )
            )
        ),
        basis = "Specification: JBL Bar 800 product page and owner's manual " +
            "(35 Hz to 20 kHz at -6 dB; 3 x 46x90 mm racetrack and 3 x 20 mm " +
            "tweeters plus 2 x 70 mm up-firing drivers in the bar; 10-inch subwoofer). " +
            "Character: listening reviews of the bar (strong bass that can " +
            "overpower the mids, slightly thin dialogue, muddy at high volume). " +
            "Not measured by Core EQ."
    )

    /**
     * JBL Bar 2.0 All-in-One, original (2 x 40 W, 70 Hz to 20 kHz, no EQ and no
     * bass settings on the bar itself, so Core EQ is the only tone control).
     *
     * Research for these presets (October 2026) found no measured frequency
     * response, only the claimed 70 Hz to 20 kHz range and reviews. Reviews
     * say: front soundstage wider and deeper than expected, dialogue distinct,
     * no centre channel, bright highs with energy, vocals sometimes harsh, bass
     * short on the lowest demanding material, virtual surround on its TV mode.
     * The 125 Hz lifts reach into 40 to 70 Hz through their skirts (up to about
     * 0.8 dB). The bar has no output there, so that part is headroom, not sound.
     *
     * The soundstage presets are tonal only. An EQ cannot widen a stereo image,
     * so they shape how the front stage sounds; the bar's own TV or virtual
     * surround mode is set on the bar and Core EQ does not change it.
     *
     * The MK2 (2022, JBLBAR20AIOM2BLKAM) is a different product and is not
     * matched, so its owners get no automatic preset.
     */
    private val JBL_BAR_2_0 = Model(
        id = "jbl-bar-2-0-all-in-one",
        brand = "JBL",
        name = "JBL Bar 2.0",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:jbl[\s_-]*)?bar[\s_-]*2[\s_.-]*0(?![0-9])""", RegexOption.IGNORE_CASE),
        nameExclude = Regex("""mk?[\s_-]*2(?![0-9])""", RegexOption.IGNORE_CASE),  // MK2, and the SKU's "AIOM2"
        presets = listOf(
            ManualEqPreset(
                id = "jbl-bar-2-0-movie",
                name = "JBL Bar 2.0 · Movie",
                filters = listOf(
                    bell(125.0, 1.5),    // the bass port adds depth the bar lacks at 70 Hz
                    bell(2_000.0, 1.0),
                    bell(4_000.0, 1.0)
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-2-0-dialogue",
                name = "JBL Bar 2.0 · Dialogue",
                filters = listOf(
                    bell(250.0, -1.0),   // less boxiness on speech
                    bell(1_000.0, 1.0),
                    bell(2_000.0, 2.0)   // dialogue is already distinct; this is a small lift
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-2-0-music",
                name = "JBL Bar 2.0 · Music",
                filters = listOf(
                    bell(125.0, 1.5),    // reviews: the bass is short on demanding music
                    bell(4_000.0, -1.0), // reviews: vocals sometimes harsh
                    bell(8_000.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-2-0-late-night",
                name = "JBL Bar 2.0 · Late night",
                filters = listOf(
                    bell(125.0, -1.5),   // less port thump at low volume
                    bell(250.0, -1.0),
                    bell(2_000.0, 1.5)   // keeps speech readable when quiet
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-2-0-open-stage",
                name = "JBL Bar 2.0 · Open stage",
                filters = listOf(
                    bell(250.0, -1.0),   // less centre-mid crowding, so the stage opens up
                    bell(4_000.0, 1.0),
                    bell(8_000.0, 1.5)   // air around the front image
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-2-0-centred-vocal",
                name = "JBL Bar 2.0 · Centred vocal",
                filters = listOf(
                    bell(250.0, -1.0),
                    bell(1_000.0, 1.0),
                    bell(2_000.0, 1.5)   // a voice that sits in the middle of the front stage
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-2-0-bass-forward",
                name = "JBL Bar 2.0 · Bass-forward",
                filters = listOf(
                    bell(125.0, 2.0),    // the most bass lift the bar can use above its 70 Hz floor
                    bell(250.0, -0.5)
                )
            )
        ),
        basis = "Specification: JBL Bar 2.0 All-in-One product page and reviews (70 Hz to 20 kHz; " +
            "2 x 40 W; two racetrack drivers; Dolby Digital; optical, USB, Bluetooth). " +
            "No measured response found in October 2026. Character: reviews only. Not measured by Core EQ."
    )

    /** Every shipped hardware model, in the order the picker lists them. */
    val MODELS: List<Model> = listOf(JBL_BAR_800, JBL_BAR_2_0)

    /** Every shipped hardware preset, flat across models. */
    val PRESETS: List<ManualEqPreset> = MODELS.flatMap { it.presets }

    /** The model a preset belongs to, or null for a preset that is not hardware. */
    fun modelForPreset(presetId: String): Model? = MODELS.firstOrNull { model ->
        model.presets.any { it.id == presetId }
    }

    fun matchAny(names: Collection<String?>): Model? {
        val cleaned = names.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
        return MODELS.firstOrNull { model ->
            cleaned.any { name ->
                model.nameMatch.containsMatchIn(name) && model.nameExclude?.containsMatchIn(name) != true
            }
        }
    }

    /**
     * The hardware the current output most likely is.
     *
     * - [kind] is the output kind [tv.corebuilds.eq.apply.OutputRoute] settled
     *   on. The TV's own speakers, and an output nothing recognised, never
     *   match: a soundbar's name is not evidence about the built-in speaker.
     * - [routeName] is the name Android gave the current route.
     * - [connectedNames] are the names of every connected output of that same
     *   kind, so a soundbar is still found when the route name is only the
     *   generic "HDMI" or the TV's own model.
     */
    fun detect(kind: String?, routeName: String?, connectedNames: Collection<String?>): Model? {
        if (kind == null || kind == SPEAKER_KIND || kind == UNKNOWN_KIND) return null
        return matchAny(listOf(routeName) + connectedNames)
    }

    // Duplicated from OutputRoute's stable strings, so this file stays pure Kotlin.
    private const val SPEAKER_KIND = "speaker"
    private const val UNKNOWN_KIND = "unknown"
}
