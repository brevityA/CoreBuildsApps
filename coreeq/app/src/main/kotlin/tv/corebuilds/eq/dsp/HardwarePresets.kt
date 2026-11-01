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
        // JBL sells a distinct Bar 800MK2. Its name also contains "Bar 800",
        // so exclude the explicit revision rather than assigning the original's
        // subjective tone overlays to the newer 7.1 product.
        nameExclude = Regex("""mk?[\s_-]*2(?![0-9])""", RegexOption.IGNORE_CASE),
        presets = listOf(
            ManualEqPreset(
                id = "jbl-bar-800-movie",
                name = "JBL Bar 800 · Movie",
                filters = listOf(
                    bell(250.0, -1.0),   // the bar's bass sits under the mids on a small room
                    bell(2_000.0, 1.0),  // dialogue presence
                    bell(4_000.0, 1.5),  // articulation for speech and effects
                    bell(8_000.0, 1.0)   // a global treble lift, not height-channel-specific
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
     * JBL Bar 2.0 All-in-One, original (2 x 40 W, 70 Hz to 20 kHz).
     * JBL documents Smart Mode (EQ settings plus JBL Surround Sound) and
     * Standard Mode (preset EQ), but no user-adjustable parametric EQ or
     * dedicated room-calibration process. The JBL mode remains separate from
     * Core EQ; it is not accurate to say the bar has "no EQ".
     *
     * Research for these presets (October 2026) found no measured frequency
     * response, only the claimed 70 Hz to 20 kHz range and reviews. Reviews
     * say: front soundstage wider and deeper than expected, dialogue distinct,
     * no centre channel, bright highs with energy, vocals sometimes harsh, bass
     * short on the lowest demanding material, virtual surround on its TV mode.
     * The 125 Hz lifts reach into 40 to 70 Hz through their skirts (up to about
     * 0.8 dB). The published 70 Hz lower response limit is not a hard cutoff;
     * do not claim that this skirt is inaudible.
     *
     * The soundstage presets are tonal only. An EQ cannot widen a stereo image,
     * so they shape how the front stage sounds; the bar's own TV or virtual
     * surround mode is set on the bar and Core EQ does not change it.
     *
     * The MK2 (AU SKU JBLBAR20AIOM2BLKAS) is a different revision. Explicit
     * MK2 labels and the bare Bluetooth name shared by both revisions are
     * excluded; only an explicit non-MK2 All-in-One name is eligible.
     */
    private val JBL_BAR_2_0 = Model(
        id = "jbl-bar-2-0-all-in-one",
        brand = "JBL",
        name = "JBL Bar 2.0",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:jbl[\s_-]*)?bar[\s_-]*2[\s_.-]*0(?![0-9])""", RegexOption.IGNORE_CASE),
        // Exclude explicit MK2 names and the bare Bluetooth name shared by
        // both revisions ("JBL BAR 2.0"). That name cannot identify the version.
        // An explicit original All-in-One name without MK2 remains eligible.
        nameExclude = Regex(
            """mk?[\s_-]*2(?![0-9])|^\s*(?:jbl[\s_-]*)?bar[\s_.-]*2[\s_.-]*0\s*$""",
            RegexOption.IGNORE_CASE
        ),
        presets = listOf(
            /**
             * The everyday curve argued for in
             * docs/research/core-eq-bar-2-0-everyday-curve-and-correction-review-2026-10-25.md.
             *
             * It is a **starting point from the spec sheet and reviews**, not a
             * measured correction: no measured response of this bar exists. What
             * it is built on is the one hard number JBL publishes, 70 Hz at
             * -6 dB, so nothing below 70 Hz is asked for. The 125 Hz bell lifts
             * the body band the bar has, 250 Hz keeps that lift from reading as
             * cupped, 2 and 4 kHz add consonant presence for TV dialogue, and
             * 6.3/8 kHz take back the "crisp, sometimes harsh" top end the
             * reviews describe. Peak boost +1.7 dB, peak cut -2.0 dB.
             */
            ManualEqPreset(
                id = "jbl-bar-2-0-everyday",
                name = "JBL Bar 2.0 · Everyday",
                filters = listOf(
                    bell(125.0, 2.0),
                    bell(250.0, -1.0),
                    bell(2_000.0, 1.0),
                    bell(4_000.0, 1.0),
                    bell(6_300.0, -1.0),
                    bell(8_000.0, -1.5)
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-2-0-movie",
                name = "JBL Bar 2.0 · Movie",
                filters = listOf(
                    bell(125.0, 1.5),    // subjective low-bass lift, not measured correction below 70 Hz
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
                    bell(125.0, 1.5),    // review-derived preference, not a measured response correction
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
                // Renamed from "Open stage" in 1.7.0: an equaliser cannot widen a
                // stereo image, and a name that says it can is a promise the DSP
                // cannot keep. The filters are unchanged; only the claim is.
                id = "jbl-bar-2-0-open-stage",
                name = "JBL Bar 2.0 · Air",
                filters = listOf(
                    bell(250.0, -1.0),   // less centre-mid crowding, which reads as a cleaner front
                    bell(4_000.0, 1.0),
                    bell(8_000.0, 1.5)   // treble lift; a tone change, not a wider image
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

    /** Samsung HW-Q800H (5.1.2 Dolby Atmos, wireless subwoofer; the F version is the same tuning). */
    private val SAMSUNG_Q800H = Model(
        id = "samsung-hw-q800h",
        brand = "Samsung",
        name = "Samsung HW-Q800H",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:samsung[\s_-]*)?(?:hw[\s_-]*)?q800[hf](?![a-z0-9])""", RegexOption.IGNORE_CASE),
        nameExclude = null,
        presets = listOf(
            ManualEqPreset(
                id = "samsung-q800h-movie",
                name = "Samsung Q800H · Movie",
                filters = listOf(
                    bell(63.0, -1.0),    // the subwoofer's bass is generous on this bar
                    bell(2_000.0, 1.0),  // dialogue presence
                    bell(8_000.0, 1.0)   // the warm profile rolls off the top treble
                )
            ),
            ManualEqPreset(
                id = "samsung-q800h-dialogue",
                name = "Samsung Q800H · Dialogue",
                filters = listOf(
                    bell(1_000.0, 1.0),
                    bell(2_000.0, 1.5),
                    bell(4_000.0, 1.0)
                )
            ),
            ManualEqPreset(
                id = "samsung-q800h-music",
                name = "Samsung Q800H · Music",
                filters = listOf(
                    bell(63.0, -1.0),
                    bell(8_000.0, 1.5)   // restores the air the warm profile takes away
                )
            )
        ),
        basis = "Specification: Samsung HW-Q800H (5.1.2 channel, 6.5-inch woofer plus passive radiator, " +
            "wireless subwoofer). Character: Crutchfield describes a warm profile that emphasises bass and " +
            "rolls off the top treble; Les Numériques finds balanced mids and detailed highs, with Standard " +
            "mode the most natural. No measured response from Core EQ."
    )

    /** Sony Bravia Theatre Bar 6 (bar with a wireless subwoofer, centre channel, Dolby Atmos). */
    private val SONY_BAR_6 = Model(
        id = "sony-bravia-theatre-bar-6",
        brand = "Sony",
        name = "Sony Bravia Theatre Bar 6",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:bravia[\s_-]*)?theat(?:re|er)[\s_-]*bar[\s_-]*6(?![0-9])""", RegexOption.IGNORE_CASE),
        nameExclude = null,
        presets = listOf(
            ManualEqPreset(
                id = "sony-bar-6-movie",
                name = "Sony Bar 6 · Movie",
                filters = listOf(
                    bell(63.0, -2.0),    // reviews: the default sub level drowns the mids
                    bell(250.0, -1.0),
                    bell(2_000.0, 1.0)
                )
            ),
            ManualEqPreset(
                id = "sony-bar-6-dialogue",
                name = "Sony Bar 6 · Dialogue",
                filters = listOf(
                    bell(63.0, -2.5),
                    bell(250.0, -1.5),
                    bell(1_000.0, 1.5),
                    bell(2_000.0, 1.5)
                )
            ),
            ManualEqPreset(
                id = "sony-bar-6-music",
                name = "Sony Bar 6 · Music",
                filters = listOf(
                    bell(63.0, -3.0),    // reviews: bass needs turning down for music
                    bell(250.0, -1.0),
                    bell(4_000.0, 1.0),
                    bell(8_000.0, 1.0)
                )
            )
        ),
        basis = "Specification: Sony Bravia Theatre Bar 6 (Dolby Atmos, wireless subwoofer, dedicated centre " +
            "channel). Character: What Hi-Fi finds the default bass drowns the midrange; RTINGS finds the mids " +
            "well balanced and dialogue crisp; Yahoo Tech says bass needs reducing for music. No measured " +
            "response from Core EQ."
    )

    /** LG S80TR (5.1.3 Dolby Atmos, 8-inch wireless subwoofer, up-firing centre channel, rear speakers). */
    private val LG_S80TR = Model(
        id = "lg-s80tr",
        brand = "LG",
        name = "LG S80TR",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:lg[\s_-]*)?s80tr(?![a-z0-9])""", RegexOption.IGNORE_CASE),
        nameExclude = null,
        presets = listOf(
            ManualEqPreset(
                id = "lg-s80tr-movie",
                name = "LG S80TR · Movie",
                filters = listOf(
                    bell(63.0, 2.0),     // HomeTheaterReview: bass rolls off below about 70 Hz
                    bell(125.0, 1.0),
                    bell(4_000.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "lg-s80tr-dialogue",
                name = "LG S80TR · Dialogue",
                filters = listOf(
                    bell(250.0, -0.5),
                    bell(2_000.0, 1.0),
                    bell(4_000.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "lg-s80tr-music",
                name = "LG S80TR · Music",
                filters = listOf(
                    bell(63.0, 1.5),
                    bell(125.0, 1.0),
                    bell(8_000.0, 0.5)
                )
            )
        ),
        basis = "Specification: LG S80TR (5.1.3 channel, 8-inch wireless subwoofer, up-firing centre channel, " +
            "rear speakers). Character: HomeTheaterReview finds bass rolls off below about 70 Hz and the " +
            "centre channel keeps dialogue clear; owner reviews describe the bass as modest. No measured " +
            "response from Core EQ."
    )

    /** JBL Bar 1000 MK2 (7.1.4 Dolby Atmos, 10-inch wireless subwoofer, PureVoice 2.0). */
    private val JBL_BAR_1000_MK2 = Model(
        id = "jbl-bar-1000-mk2",
        brand = "JBL",
        name = "JBL Bar 1000 MK2",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:jbl[\s_-]*)?bar[\s_-]*1000(?![0-9])""", RegexOption.IGNORE_CASE),
        nameExclude = null,
        presets = listOf(
            ManualEqPreset(
                id = "jbl-bar-1000-movie",
                name = "JBL Bar 1000 MK2 · Movie",
                filters = listOf(
                    bell(63.0, -2.0),    // What Hi-Fi: the bass distracts from the other frequencies
                    bell(250.0, -1.0),
                    bell(2_000.0, 1.0),
                    bell(4_000.0, 0.5)   // keeps this preset distinct from the Sony Bar 6 movie tone
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-1000-dialogue",
                name = "JBL Bar 1000 MK2 · Dialogue",
                filters = listOf(
                    bell(63.0, -2.5),
                    bell(1_000.0, 1.0),  // TechRadar: dialogue is less crisp than rivals
                    bell(2_000.0, 1.5),
                    bell(4_000.0, 1.0)
                )
            ),
            ManualEqPreset(
                id = "jbl-bar-1000-music",
                name = "JBL Bar 1000 MK2 · Music",
                filters = listOf(
                    bell(63.0, -3.0),
                    bell(250.0, -1.0),
                    bell(8_000.0, 0.5)
                )
            )
        ),
        basis = "Specification: JBL Bar 1000 MK2 (7.1.4 channel, 10-inch wireless subwoofer, PureVoice 2.0). " +
            "Character: What Hi-Fi finds powerful bass that distracts from the other frequencies; TechRadar finds " +
            "dialogue less crisp than rivals; HomeTheaterReview says PureVoice can sound heavy-handed. " +
            "No measured response from Core EQ."
    )


    /** Sonos Arc (5.0.2 Dolby Atmos, 11 drivers: eight elliptical woofers, three silk-dome tweeters). */
    private val SONOS_ARC = Model(
        id = "sonos-arc",
        brand = "Sonos",
        name = "Sonos Arc",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:sonos[\s_-]*)?arc(?![a-z0-9])""", RegexOption.IGNORE_CASE),
        // The Arc Ultra is a different revision with more drivers and more bass;
        // its name still contains "Arc", so exclude it explicitly.
        nameExclude = Regex("""ultra""", RegexOption.IGNORE_CASE),
        presets = listOf(
            ManualEqPreset(
                id = "sonos-arc-movie",
                name = "Sonos Arc · Movie",
                filters = listOf(
                    bell(63.0, -1.5),    // reviews: the rich bass can dominate effects
                    bell(250.0, -0.5),
                    bell(2_000.0, 1.0),  // dialogue presence
                    bell(4_000.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "sonos-arc-dialogue",
                name = "Sonos Arc · Dialogue",
                filters = listOf(
                    bell(250.0, -1.0),
                    bell(1_000.0, 1.0),
                    bell(2_000.0, 1.5),
                    bell(4_000.0, 1.0)
                )
            ),
            ManualEqPreset(
                id = "sonos-arc-music",
                name = "Sonos Arc · Music",
                filters = listOf(
                    bell(63.0, -1.0),
                    bell(250.0, -0.5),
                    bell(8_000.0, 1.0)   // restores the air a warm-leaning tilt takes away
                )
            ),
            ManualEqPreset(
                id = "sonos-arc-late-night",
                name = "Sonos Arc · Late night",
                filters = listOf(
                    bell(40.0, -2.5),    // less sub rumble for neighbours and sleep
                    bell(63.0, -1.5),
                    bell(125.0, -1.0),
                    bell(2_000.0, 1.0)   // keeps speech readable at low volume
                )
            )
        ),
        basis = "Specification: Sonos Arc (5.0.2 channel Dolby Atmos, 11 drivers – 8 elliptical woofers " +
            "and 3 silk-dome tweeters, 11 Class-D amplifiers, Trueplay room tuning). " +
            "Character: reviews find a balanced midrange with clear dialogue that can read bright, " +
            "powerful rich bass, and some lack of very low bass without a sub. " +
            "Not measured by Core EQ."
    )

    /** Sonos Beam (Gen 2) (Dolby Atmos via virtual height, 4 elliptical mid-woofers, centre tweeter, 3 passive radiators). */
    private val SONOS_BEAM_GEN_2 = Model(
        id = "sonos-beam-gen-2",
        brand = "Sonos",
        name = "Sonos Beam Gen 2",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:sonos[\s_-]*)?beam[\s_-]*(?:\(?gen[\s_-]*2\)?|g2)(?![a-z0-9])""", RegexOption.IGNORE_CASE),
        // The original Beam is a different product; its name lacks the generation.
        nameExclude = null,
        presets = listOf(
            ManualEqPreset(
                id = "sonos-beam-gen-2-movie",
                name = "Sonos Beam Gen 2 · Movie",
                filters = listOf(
                    bell(63.0, -1.0),    // modest bass for a compact bar; keep effects clear
                    bell(2_000.0, 1.0),  // dialogue presence
                    bell(4_000.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "sonos-beam-gen-2-dialogue",
                name = "Sonos Beam Gen 2 · Dialogue",
                filters = listOf(
                    bell(250.0, -0.5),
                    bell(1_000.0, 1.0),
                    bell(2_000.0, 1.5)   // reviews: the centre tweeter targets clear dialogue
                )
            ),
            ManualEqPreset(
                id = "sonos-beam-gen-2-music",
                name = "Sonos Beam Gen 2 · Music",
                filters = listOf(
                    bell(63.0, -1.0),
                    bell(8_000.0, 1.0)   // a little air for a small driver set
                )
            ),
            ManualEqPreset(
                id = "sonos-beam-gen-2-late-night",
                name = "Sonos Beam Gen 2 · Late night",
                filters = listOf(
                    bell(40.0, -2.0),    // less low-end at low volume
                    bell(125.0, -1.0),
                    bell(2_000.0, 1.0)   // keeps speech readable when quiet
                )
            )
        ),
        basis = "Specification: Sonos Beam (Gen 2) (Dolby Atmos, 4 elliptical mid-woofers, 1 centre tweeter, " +
            "3 passive radiators, HDMI eARC, Trueplay). Character: reviews find focused dialogue " +
            "clarity and modest bass output for a compact bar, with Trueplay making an audible difference. " +
            "Not measured by Core EQ."
    )

    /** Sonos Ray (2.0 stereo, optical, 2 woofers, 2 tweeters; bass rolls off near 65 Hz). */
    private val SONOS_RAY = Model(
        id = "sonos-ray",
        brand = "Sonos",
        name = "Sonos Ray",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:sonos[\s_-]*)?ray(?![a-z0-9])""", RegexOption.IGNORE_CASE),
        nameExclude = null,
        presets = listOf(
            ManualEqPreset(
                id = "sonos-ray-movie",
                name = "Sonos Ray · Movie",
                filters = listOf(
                    bell(250.0, -0.5),
                    bell(2_000.0, 1.0),  // dialogue presence
                    bell(4_000.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "sonos-ray-dialogue",
                name = "Sonos Ray · Dialogue",
                filters = listOf(
                    bell(1_000.0, 1.0),
                    bell(2_000.0, 1.5),  // mirrors the Ray's Speech Enhancement 1–4 kHz lift
                    bell(4_000.0, 1.0)
                )
            ),
            ManualEqPreset(
                id = "sonos-ray-music",
                name = "Sonos Ray · Music",
                filters = listOf(
                    bell(125.0, 1.0),    // the body band the small woofers can still produce
                    bell(250.0, 0.5),
                    bell(8_000.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "sonos-ray-late-night",
                name = "Sonos Ray · Late night",
                filters = listOf(
                    bell(63.0, -1.5),    // tight, not booming bass down low
                    bell(125.0, -1.0),
                    bell(2_000.0, 1.0)   // keeps speech readable when quiet
                )
            )
        ),
        basis = "Specification: Sonos Ray (2.0 stereo, 2 woofers, 2 tweeters, optical input, " +
            "bass+treble EQ and Speech Enhancement in the Sonos app). Character: reviews find " +
            "best-in-class dialogue clarity, tight rather than booming bass, and output rolling " +
            "off near 65 Hz. Not measured by Core EQ."
    )


    /** Sonos Arc Ultra (9.1.4 Dolby Atmos, 14 drivers incl. a Sound Motion woofer; double the Arc's bass at 50 Hz). */
    private val SONOS_ARC_ULTRA = Model(
        id = "sonos-arc-ultra",
        brand = "Sonos",
        name = "Sonos Arc Ultra",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:sonos[\s_-]*)?arc[\s_-]*ultra(?![a-z0-9])""", RegexOption.IGNORE_CASE),
        nameExclude = null,
        presets = listOf(
            ManualEqPreset(
                id = "sonos-arc-ultra-movie",
                name = "Sonos Arc Ultra · Movie",
                filters = listOf(
                    bell(63.0, -1.0),
                    bell(2_000.0, 1.0),
                    bell(4_000.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "sonos-arc-ultra-dialogue",
                name = "Sonos Arc Ultra · Dialogue",
                filters = listOf(
                    bell(250.0, -0.5),
                    bell(1_000.0, 1.0),
                    bell(2_000.0, 1.5),
                    bell(4_000.0, 1.0)
                )
            ),
            ManualEqPreset(
                id = "sonos-arc-ultra-music",
                name = "Sonos Arc Ultra · Music",
                filters = listOf(
                    bell(63.0, -1.0),
                    bell(8_000.0, 1.0)
                )
            ),
            ManualEqPreset(
                id = "sonos-arc-ultra-late-night",
                name = "Sonos Arc Ultra · Late night",
                filters = listOf(
                    bell(40.0, -2.5),
                    bell(63.0, -1.5),
                    bell(125.0, -1.0),
                    bell(2_000.0, 1.0)
                )
            )
        ),
        basis = "Specification: Sonos Arc Ultra (9.1.4 channel Dolby Atmos, 14 drivers including a Sound Motion " +
            "woofer, 15 Class-D amplifiers, Bluetooth 5.3, Trueplay). Character: reviews find deeper, more " +
            "controlled bass than the original Arc and exceptional dialogue clarity, with Speech Enhancement " +
            "adjustable in three levels. Not measured by Core EQ."
    )

    /** Bose Smart Soundbar 900 (5.0.2 Dolby Atmos, 9 transducers, ADAPTiQ room calibration). */
    private val BOSE_SMART_SOUNDBAR_900 = Model(
        id = "bose-smart-soundbar-900",
        brand = "Bose",
        name = "Bose Smart Soundbar 900",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:bose[\s_-]*)?smart[\s_-]*soundbar[\s_-]*900(?![0-9])""", RegexOption.IGNORE_CASE),
        nameExclude = null,
        presets = listOf(
            ManualEqPreset(
                id = "bose-smart-soundbar-900-movie",
                name = "Bose Smart Soundbar 900 · Movie",
                filters = listOf(
                    bell(63.0, -0.5),
                    bell(2_000.0, 1.0),
                    bell(4_000.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "bose-smart-soundbar-900-dialogue",
                name = "Bose Smart Soundbar 900 · Dialogue",
                filters = listOf(
                    bell(250.0, -0.5),
                    bell(1_000.0, 1.0),
                    bell(2_000.0, 1.5)
                )
            ),
            ManualEqPreset(
                id = "bose-smart-soundbar-900-music",
                name = "Bose Smart Soundbar 900 · Music",
                filters = listOf(
                    bell(63.0, -0.5),
                    bell(8_000.0, -0.5)
                )
            ),
            ManualEqPreset(
                id = "bose-smart-soundbar-900-late-night",
                name = "Bose Smart Soundbar 900 · Late night",
                filters = listOf(
                    bell(40.0, -2.0),
                    bell(63.0, -1.0),
                    bell(125.0, -0.5),
                    bell(2_000.0, 1.0)
                )
            )
        ),
        basis = "Specification: Bose Smart Soundbar 900 (5.0.2 channel Dolby Atmos, 9 transducers, " +
            "ADAPTiQ room calibration, HDMI eARC/optical, subwoofer output). Character: reviews find a " +
            "balanced signature with clear dialogue and powerful bass that lacks deep low-bass without a " +
            "sub, with treble that can read bright at high volume. Not measured by Core EQ."
    )

    /** Samsung HW-Q990C (11.1.4 Dolby Atmos/DTS:X, 22 speakers, 8-inch wireless subwoofer, SpaceFit Sound Pro). */
    private val SAMSUNG_HW_Q990C = Model(
        id = "samsung-hw-q990c",
        brand = "Samsung",
        name = "Samsung HW-Q990C",
        kind = Kind.SOUNDBAR,
        nameMatch = Regex("""(?<![a-z0-9])(?:samsung[\s_-]*)?(?:hw[\s_-]*)?q990c(?![a-z0-9])""", RegexOption.IGNORE_CASE),
        nameExclude = null,
        presets = listOf(
            ManualEqPreset(
                id = "samsung-hw-q990c-movie",
                name = "Samsung HW-Q990C · Movie",
                filters = listOf(
                    bell(63.0, -1.0),
                    bell(2_000.0, 1.0),
                    bell(4_000.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "samsung-hw-q990c-dialogue",
                name = "Samsung HW-Q990C · Dialogue",
                filters = listOf(
                    bell(1_000.0, 1.0),
                    bell(2_000.0, 1.5),
                    bell(4_000.0, 0.5)
                )
            ),
            ManualEqPreset(
                id = "samsung-hw-q990c-music",
                name = "Samsung HW-Q990C · Music",
                filters = listOf(
                    bell(63.0, -0.5),
                    bell(8_000.0, -0.5)
                )
            ),
            ManualEqPreset(
                id = "samsung-hw-q990c-late-night",
                name = "Samsung HW-Q990C · Late night",
                filters = listOf(
                    bell(40.0, -2.0),
                    bell(63.0, -1.0),
                    bell(125.0, -0.5),
                    bell(2_000.0, 1.0)
                )
            )
        ),
        basis = "Specification: Samsung HW-Q990C (11.1.4 channel Dolby Atmos/DTS:X, 22 speakers, " +
            "8-inch wireless subwoofer, SpaceFit Sound Pro room calibration, HDMI eARC). Character: " +
            "reviews find deep but controlled bass, a clean midrange and clear dialogue, with treble " +
            "that can read slightly bright. Not measured by Core EQ."
    )

    /** Every shipped hardware model, in the order the picker lists them. */
    val MODELS: List<Model> = listOf(JBL_BAR_800, JBL_BAR_2_0, SAMSUNG_Q800H, SONY_BAR_6, LG_S80TR, JBL_BAR_1000_MK2, SONOS_ARC, SONOS_ARC_ULTRA, BOSE_SMART_SOUNDBAR_900, SAMSUNG_HW_Q990C, SONOS_BEAM_GEN_2, SONOS_RAY)

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
