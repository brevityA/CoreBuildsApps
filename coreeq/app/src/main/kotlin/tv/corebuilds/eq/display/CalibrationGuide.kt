package tv.corebuilds.eq.display

/**
 * What a display calibration should ask, in what order, and what each answer means (1.9.0).
 *
 * The first version of this screen was a fixed list of ten patterns with one paragraph each:
 * it told everybody the same thing and could not answer "so what do I do if it looks wrong?".
 * This is the model behind the screen now — pure Kotlin, no Android, so it can be tested in
 * the repository harness — and it does three things the list did not:
 *
 * 1. **It asks before it tells.** Every step has branches, each with the advice for that
 *    answer and the step it leads to. "Bar 1 is invisible" and "the whole screen glows" are
 *    different faults with opposite fixes, and a wall of text makes the user pick the wrong
 *    one.
 * 2. **It starts from what the screen actually is.** [DisplayFacts] is built from what
 *    Android reports (resolution, refresh rate, HDR types) and turns it into notes: a UI
 *    running at 1080p on a 4K panel, a 59.94 Hz mode that will show 24p judder as 3:2, a
 *    panel that reports no HDR at all. Those are things no picture-menu step can fix and
 *    every viewer should know before adjusting anything.
 * 3. **It says which faults are not settings.** Motion blur is the panel's response time,
 *    dirty-screen effect is the panel's uniformity, and a 1080p UI on a 4K panel is Android's
 *    resolution choice. Sending somebody to the picture menu for those is worse than telling
 *    them the truth.
 *
 * The order is the ISF one, with the two additions this screen needs: overscan before
 * anything else that is judged against the screen edge, and motion last because judging it
 * before the panel is settled wastes the effort. Nothing here measures a display: it is a
 * guide for a person's eyes, and it says so.
 */
object CalibrationGuide {

    /** The patterns this guide can ask for. The view decides how to draw them. */
    enum class PatternId {
        BLACK_LEVEL,
        WHITE_LEVEL,
        GREYSCALE,
        COLOUR_BARS,
        SHARPNESS,
        OVERSCAN,
        UNIFORMITY_WHITE,
        UNIFORMITY_GREY,
        GAMMA,
        HDR_REFERENCE,
        MOTION_JUDDER,
        MOTION_RESOLUTION,
        PIXEL_GRID
    }

    /**
     * One answer to a step: what it means and what to do about it. [nextStepId] is where that
     * answer leads — the next step when the answer was "this is right", a repair step when it
     * was not. Null keeps the user on the current step, which is what the "keep adjusting"
     * answers do.
     */
    data class Branch(
        val label: String,
        val advice: String,
        val nextStepId: String? = null,
        /** True when this answer means the step is done. */
        val resolved: Boolean = false
    )

    /**
     * One step: what to show, what to do, and the answers to it. [pattern] null means an
     * instruction-only step; [factsNote] is extra text derived from [DisplayFacts], when the
     * step reads differently on this screen.
     */
    data class Step(
        val id: String,
        val title: String,
        val pattern: PatternId?,
        val instruction: String,
        val branches: List<Branch>,
        /** The branch that continues the ordinary path, for the Next button's label. */
        val happyPath: String? = null
    )

    val STEPS: List<Step> = listOf(
        Step(
            id = "mode",
            title = "Picture mode",
            pattern = null,
            instruction = "Set the TV to Movie, Cinema, or Filmmaker mode. These are the presets " +
                "closest to the D65 white point and BT.1886 gamma that films are mastered for. " +
                "Vivid, Dynamic and Standard lift saturation, contrast and edge enhancement, and " +
                "no later step can undo that — sharpening and dynamic contrast destroy detail " +
                "before it reaches the panel.",
            branches = listOf(
                Branch(
                    "My TV has Movie or Filmmaker mode",
                    "Good. Turn off any 'dynamic contrast', 'dynamic colour', 'auto motion' or " +
                        "'noise reduction' inside it — these are the usual culprits when a Movie " +
                        "mode still looks wrong.",
                    nextStepId = "overscan", resolved = true
                ),
                Branch(
                    "Only Standard, Vivid or Dynamic exist",
                    "Use Standard and do the rest of the steps on it; the biggest wins are still " +
                        "available. Set sharpness low, turn dynamic contrast off, and expect the " +
                        "colour temperature to be cooler than D65. Every later step still applies.",
                    nextStepId = "overscan", resolved = true
                )
            ),
            happyPath = "My TV has Movie or Filmmaker mode"
        ),
        Step(
            id = "overscan",
            title = "Overscan and 1:1 pixels",
            pattern = PatternId.OVERSCAN,
            instruction = "Markers sit at 2.5%, 5% and 10% from the edge. All three must be " +
                "visible, and the 1-pixel grid in the middle must be a clean pattern rather than " +
                "a grey blur — a blur means the TV is scaling 1080p onto a 4K panel with a " +
                "smoothing filter.",
            branches = listOf(
                Branch(
                    "All markers are visible",
                    "No overscan. If the grid in the middle looks like flat grey rather than " +
                        "fine lines, the panel is upscaling with smoothing; look for a '1:1 pixel', " +
                        "'Just Scan' or 'PC' mode.",
                    nextStepId = "black", resolved = true
                ),
                Branch(
                    "The outer (2.5%) marker is cut off",
                    "The TV is overscanning: the image is cropped and effectively lower " +
                        "resolution. Find 'Just Scan' (Samsung/LG), 'Screen Fit', 'Full Pixel' or " +
                        "'1:1' in the picture options and enable it. If the setting exists but " +
                        "does nothing, it is usually the input's own picture mode that has to be " +
                        "changed, not the HDMI-level one."
                )
            ),
            happyPath = "All markers are visible"
        ),
        Step(
            id = "black",
            title = "Black level (brightness)",
            pattern = PatternId.BLACK_LEVEL,
            instruction = "Sixteen bars, each two digital codes brighter than the last, from code " +
                "0 to code 30. Adjust the TV's BRIGHTNESS control. The target: bar 0 is still " +
                "black and the first bar you can see is a low number — 2 to 6 is normal in a dim " +
                "room. Each bar is labelled with the code it is drawn at.",
            branches = listOf(
                Branch(
                    "The first visible bar is a low number (2 to 6)",
                    "Black level is right. Every bar above it should now be distinguishable.",
                    nextStepId = "white", resolved = true
                ),
                Branch(
                    "Nothing appears until code 8 or higher",
                    "Brightness is too low and near-black detail is being thrown away (black " +
                        "crush). Raise BRIGHTNESS until a bar in the low single digits appears. If " +
                        "raising it turns the whole screen grey before any bar appears, the panel " +
                        "has a limited black floor — stop there.",
                    nextStepId = "white"
                ),
                Branch(
                    "The whole screen glows grey and bar 0 is not black",
                    "Brightness is too high and black is lifted. Lower it until the left edge " +
                        "goes truly black and bar 1 is still just visible."
                )
            ),
            happyPath = "The first visible bar is a low number (2 to 6)"
        ),
        Step(
            id = "white",
            title = "White level (contrast)",
            pattern = PatternId.WHITE_LEVEL,
            instruction = "Eleven near-white bars from code 200 to 255, ending 250 / 253 / 255. " +
                "Adjust CONTRAST. The target: 253 is just distinguishable from 255 — the " +
                "brightest bars should not merge into one flat white.",
            branches = listOf(
                Branch(
                    "253 is distinguishable from 255",
                    "White level is right.",
                    nextStepId = "gamma", resolved = true
                ),
                Branch(
                    "The brightest bars are all the same white",
                    "Contrast is too high and highlights are clipping. Lower CONTRAST until the " +
                        "last bars separate. On an LCD this also lowers peak brightness, which is " +
                        "the correct trade: clipped highlights are gone for good.",
                    nextStepId = "gamma"
                ),
                Branch(
                    "Even the brightest bar looks grey",
                    "Contrast is too low. Raise it until 253 separates from 255, then stop."
                )
            ),
            happyPath = "253 is distinguishable from 255"
        ),
        Step(
            id = "gamma",
            title = "Gamma",
            pattern = PatternId.GAMMA,
            instruction = "Two rows of patches, each labelled with the code it is drawn at, on a " +
                "50% grey field: the top row assumes a plain power law, the bottom row BT.1886 " +
                "with a 0.01 nit black. On each row, the patch that disappears into the grey is " +
                "the gamma the display is at under that model.",
            branches = listOf(
                Branch(
                    "The γ2.2 patch blends in",
                    "Correct for a bright room. For a dark room BT.1886 (≈γ2.4) tracks better; " +
                        "use it only if the room is genuinely dark.",
                    nextStepId = "colour", resolved = true
                ),
                Branch(
                    "The γ2.4 patch blends in",
                    "Correct for a dark room. In daylight it will look slightly flat.",
                    nextStepId = "colour", resolved = true
                ),
                Branch(
                    "None of the patches blend in, or shadows look crushed",
                    "Gamma is too steep. Raise the TV's gamma setting (a lower number on most " +
                        "menus, e.g. 2.2 instead of 2.4) and check again."
                ),
                Branch(
                    "Shadows look washed out and mid-grey looks bright",
                    "Gamma is too shallow. Lower the gamma setting and check again."
                )
            ),
            happyPath = "The γ2.2 patch blends in"
        ),
        Step(
            id = "colour",
            title = "Colour temperature and tint",
            pattern = PatternId.GREYSCALE,
            instruction = "Eleven bars from black to white. Every one should be neutral grey. A " +
                "yellowish cast is too warm (too much red/green), a bluish cast is too cool.",
            branches = listOf(
                Branch(
                    "The greys look neutral",
                    "Leave colour temperature alone. D65 is the standard; 'Warm 2' on most sets " +
                        "is the usual approximation of it.",
                    nextStepId = "sharpness", resolved = true
                ),
                Branch(
                    "Greys look warm or yellowish",
                    "The set is already warm — try a cooler preset ('Warm 1' or 'Normal'). If it " +
                        "is on its warmest setting already, this is the panel, not a setting.",
                    nextStepId = "sharpness"
                ),
                Branch(
                    "Greys look cool or bluish",
                    "Move the colour temperature warmer (Warm 2 / Warm) and leave the individual " +
                        "RGB gains alone unless the TV offers a two- or ten-point white balance.",
                    nextStepId = "sharpness"
                ),
                Branch(
                    "There is a strong green or magenta tint",
                    "That is a white-balance error, not taste. Only a TV with 2-point or 10-point " +
                        "white balance can correct it; a tint/tint control (hue) is the wrong tool " +
                        "for greys.",
                    nextStepId = "sharpness"
                )
            ),
            happyPath = "The greys look neutral"
        ),
        Step(
            id = "sharpness",
            title = "Sharpness",
            pattern = PatternId.SHARPNESS,
            instruction = "Vertical lines at 1, 2, 3, 4, 6 and 8 pixel spacing. At the TV's best " +
                "sharpness setting the 1-pixel zone is clean: distinct lines, no bright halo " +
                "beside them.",
            branches = listOf(
                Branch(
                    "Edges are clean, no halos",
                    "Sharpness is right, which is usually 0 or very near it.",
                    nextStepId = "motion", resolved = true
                ),
                Branch(
                    "There is a bright halo or ringing beside the lines",
                    "Sharpness is too high — edge enhancement is adding detail that is not " +
                        "there. Lower it until the halos disappear. On most TVs that is 0.",
                    nextStepId = "motion"
                ),
                Branch(
                    "The lines merge into flat grey",
                    "Sharpness is too low or the panel is softening the image. If the TV has a " +
                        "separate 'edge enhancement' control, raise sharpness one step at a time " +
                        "and stop before halos appear."
                )
            ),
            happyPath = "Edges are clean, no halos"
        ),
        Step(
            id = "motion",
            title = "Motion and judder",
            pattern = PatternId.MOTION_JUDDER,
            instruction = "A marker steps across the screen at 24 updates a second on the top " +
                "row and 60 on the bottom. Film is 24 fps; a 60 Hz panel has to repeat frames " +
                "unevenly (3:2), which is the judder you see on slow pans. This is a visual aid, " +
                "not a measurement.",
            branches = listOf(
                Branch(
                    "Both rows look smooth enough",
                    "Leave motion processing off. It adds latency and the 'soap opera' look.",
                    nextStepId = "uniformity", resolved = true
                ),
                Branch(
                    "The top row stutters while the bottom is smooth",
                    "That is 24p judder, and it is correct behaviour. Try 'film mode' / " +
                        "'24p true cinema' if the TV has it; a mild motion interpolation setting " +
                        "trades the stutter for some smoothing and input lag.",
                    nextStepId = "uniformity"
                ),
                Branch(
                    "The picture looks like a home video — too smooth, edges crawl",
                    "Motion interpolation is on. Turn 'Auto Motion Plus', 'TruMotion', " +
                        "'Motionflow' or 'Smoothness' down or off; that look is not what the " +
                        "director shot.",
                    nextStepId = "uniformity"
                ),
                Branch(
                    "Fast-moving lines smear or trail",
                    "That is the panel's response time, not a setting. Lowering motion " +
                        "interpolation will not fix it; a faster panel would.",
                    nextStepId = "uniformity", resolved = true
                )
            ),
            happyPath = "Both rows look smooth enough"
        ),
        Step(
            id = "uniformity",
            title = "Panel uniformity",
            pattern = PatternId.UNIFORMITY_GREY,
            instruction = "A flat 50% grey, then a flat white. Look for blotches, dark corners, " +
                "clouding or a vertical band. These are panel characteristics and no picture " +
                "setting removes them.",
            branches = listOf(
                Branch(
                    "It looks even",
                    "Nothing to adjust. Check the white field as well — backlight bleed only " +
                        "shows on a bright field.",
                    nextStepId = "hdr", resolved = true
                ),
                Branch(
                    "There are blotches or bands",
                    "Dirty screen effect. It is the panel; a return or exchange is the only fix, " +
                        "and only within the return window. Reducing brightness slightly can make " +
                        "it less visible but does not fix it.",
                    nextStepId = "hdr", resolved = true
                ),
                Branch(
                    "Bright patches at the edges on the white field",
                    "Backlight bleed from the panel's edge lighting. Loosening the wall mount or " +
                        "reducing brightness can reduce it; it is not a calibration fault.",
                    nextStepId = "hdr", resolved = true
                )
            ),
            happyPath = "It looks even"
        ),
        Step(
            id = "hdr",
            title = "HDR reference levels",
            pattern = PatternId.HDR_REFERENCE,
            instruction = "Patches at ST.2084 PQ reference points. In HDR the brightest patch " +
                "should be visibly brighter than the 100-nit one, and the near-black patches " +
                "should stay distinguishable. In SDR this pattern is a grey ramp, not an HDR test.",
            branches = listOf(
                Branch(
                    "On HDR, the ramp is smooth and the top end is bright",
                    "PQ tracking looks sane. If the whole picture looks flat in HDR, check that " +
                        "the input's 'HDMI Ultra HD Deep Colour' / 'Enhanced' mode is on — HDR " +
                        "cannot pass without it on many sets.",
                    nextStepId = "record", resolved = true
                ),
                Branch(
                    "On HDR, the near-black patches all look black",
                    "HDR is clipping the shadows. This is usually the TV's HDR tone mapping or a " +
                        "too-high brightness; some sets also need 'HDR10+' / 'dynamic tone mapping' " +
                        "off to track PQ predictably.",
                    nextStepId = "record"
                ),
                Branch(
                    "The picture looks the same in HDR and SDR",
                    "The display may not be receiving HDR at all — see the note above the " +
                        "instructions for what Android reports this screen supports.",
                    nextStepId = "record", resolved = true
                )
            ),
            happyPath = "On HDR, the ramp is smooth and the top end is bright"
        ),
        Step(
            id = "record",
            title = "Write it down",
            pattern = null,
            instruction = "Note what you changed: picture mode, brightness, contrast, gamma, " +
                "colour temperature, sharpness and motion. Most TVs keep a separate set of these " +
                "per input (and some per dynamic range, so HDR has its own brightness and " +
                "contrast) — check each input you actually use, and each of SDR and HDR.",
            branches = listOf(
                Branch(
                    "Done",
                    "Come back to this screen after a firmware update or a panel replacement: " +
                        "the settings usually survive, and a reset is the usual reason they do not.",
                    nextStepId = null, resolved = true
                )
            ),
            happyPath = "Done"
        )
    )

    val FIRST_STEP_ID: String = STEPS.first().id

    fun step(id: String): Step? = STEPS.firstOrNull { it.id == id }

    /** The step after [id] on the ordinary path, or null at the end. */
    fun next(id: String): Step? {
        val index = STEPS.indexOfFirst { it.id == id }
        return if (index in 0 until STEPS.lastIndex) STEPS[index + 1] else null
    }

    fun previous(id: String): Step? {
        val index = STEPS.indexOfFirst { it.id == id }
        return if (index > 0) STEPS[index - 1] else null
    }

    /**
     * What Android says this screen is, as plain numbers. Built by the activity from
     * [android.view.Display]; kept here so the notes below can be tested without an SDK.
     */
    data class DisplayFacts(
        val widthPx: Int,
        val heightPx: Int,
        val refreshHz: Double,
        val hdrTypes: List<String> = emptyList(),
        val densityDpi: Int = 0,
        val panelWidthPx: Int = 0,
        val panelHeightPx: Int = 0
    ) {
        val isHd: Boolean get() = widthPx >= 1280
        val isFullHd: Boolean get() = widthPx >= 1920
        val isUhd: Boolean get() = widthPx >= 3840
        val panelIsUhd: Boolean get() = panelWidthPx >= 3840
        val refreshIsFilmMultiple: Boolean
            get() = refreshHz > 0.0 && (kotlin.math.abs(refreshHz / 24.0 - Math.round(refreshHz / 24.0)) < 0.02)
    }

    /** The line shown above the steps: mode, resolution, refresh, HDR. */
    fun factsLine(facts: DisplayFacts): String {
        val hdr = if (facts.hdrTypes.isEmpty()) "no HDR reported" else facts.hdrTypes.joinToString(" / ")
        val refresh = if (facts.refreshHz > 0.0) String.format(java.util.Locale.US, "%.2f Hz", facts.refreshHz) else "refresh unknown"
        val mode = if (facts.widthPx > 0 && facts.heightPx > 0) "${facts.widthPx}×${facts.heightPx}" else "resolution unknown"
        return "$mode · $refresh · $hdr"
    }

    /**
     * What the reported mode means for this calibration, and only what it means. Each note is
     * something the user cannot fix in a picture menu, said before they start adjusting, so
     * they do not chase it there. Empty when nothing stands out.
     */
    fun factsNotes(facts: DisplayFacts): List<String> {
        val notes = mutableListOf<String>()
        if (facts.panelIsUhd && facts.isFullHd && !facts.isUhd) {
            notes += "Android is running the interface at ${facts.widthPx}×${facts.heightPx} on a " +
                "4K panel (${facts.panelWidthPx}×${facts.panelHeightPx}). Patterns are drawn at " +
                "that resolution and the TV upscales them, so the 1-pixel details below test the " +
                "scaler, not the panel. That is Android's default UI resolution, not a picture setting."
        } else if (!facts.isHd && facts.widthPx > 0) {
            notes += "The interface is only ${facts.widthPx}×${facts.heightPx}: fine detail patterns " +
                "will not be native resolution."
        }
        if (facts.refreshHz > 0.0 && kotlin.math.abs(facts.refreshHz - 60.0) > 0.5 &&
            kotlin.math.abs(facts.refreshHz - 59.94) > 0.5 && !facts.refreshIsFilmMultiple
        ) {
            notes += String.format(
                java.util.Locale.US,
                "This mode refreshes at %.2f Hz, which is not a multiple of film's 24 fps: 24p " +
                    "material will judder unevenly. The motion pattern shows it more honestly " +
                    "than a film scene.",
                facts.refreshHz
            )
        }
        if (facts.hdrTypes.isEmpty()) {
            notes += "This display reports no HDR support, so the HDR pattern is a grey ramp here. " +
                "If the TV does support HDR, the input's enhanced/deep-colour mode is the usual " +
                "reason Android cannot see it."
        }
        return notes
    }
}
