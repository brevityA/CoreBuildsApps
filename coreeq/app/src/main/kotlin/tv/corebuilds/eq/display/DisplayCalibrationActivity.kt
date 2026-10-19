package tv.corebuilds.eq.display

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import tv.corebuilds.eq.R
import tv.corebuilds.eq.TvActivity
import tv.corebuilds.eq.display.TestPatternView.Pattern

/**
 * Display calibration companion: full-screen test patterns with guided
 * step-by-step instructions for adjusting TV picture settings.
 *
 * The user who cares about audio correction also cares about picture
 * accuracy — they are setting up the same home theatre. This screen
 * lives alongside the EQ screens, sharing the same design language.
 *
 * Patterns are rendered pixel-accurately by [TestPatternView]. The
 * instruction panel at the bottom tells the user what to adjust and
 * what to look for. Left/right on the remote moves between patterns;
 * up toggles the instruction overlay.
 *
 * Steps follow the ISF/THX recommended order:
 * 1. Picture mode selection (instruction only, no pattern)
 * 2. Black level (brightness)
 * 3. White level (contrast)
 * 4. Colour / tint
 * 5. Sharpness
 * 6. Overscan
 * 7. Gamma
 * 8. Uniformity check
 */
class DisplayCalibrationActivity : TvActivity() {

    private lateinit var patternView: TestPatternView
    private lateinit var textTitle: TextView
    private lateinit var textInstruction: TextView
    private lateinit var textStep: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var btnToggleOverlay: Button
    private lateinit var instructionPanel: View

    private var currentStep = 0

    /**
     * Calibration steps: each pairs a pattern (or null for instruction-only)
     * with a title and instruction text.
     */
    private data class Step(
        val pattern: Pattern?,
        val title: String,
        val instruction: String
    )

    private val steps = listOf(
        Step(
            pattern = null,
            title = "Step 1: Picture Mode",
            instruction = "Set your TV to Movie, Cinema, or Filmmaker mode. " +
                "These presets are closest to the industry-standard D65 white point " +
                "and BT.1886 gamma that filmmakers intend. Avoid Vivid, Dynamic, " +
                "or Standard modes — they boost saturation and sharpening."
        ),
        Step(
            pattern = Pattern.BLACK_LEVEL,
            title = "Step 2: Black Level (Brightness)",
            instruction = "Adjust the TV's BRIGHTNESS control. Raise it until you can " +
                "just see bar 2 (a very dark grey) above the absolute black of bar 0. " +
                "If all bars look the same black, brightness is too low. If bar 0 looks " +
                "grey instead of black, brightness is too high. The goal is the lowest " +
                "brightness where near-black detail is still visible."
        ),
        Step(
            pattern = Pattern.WHITE_LEVEL,
            title = "Step 3: White Level (Contrast)",
            instruction = "Adjust the TV's CONTRAST control. Lower it from maximum until " +
                "you can just distinguish bar 253 from the 255 white background. If all " +
                "bars near the right look the same white, contrast is too high. If the " +
                "brightest bars look grey, contrast is too low."
        ),
        Step(
            pattern = Pattern.COLOUR_BARS,
            title = "Step 4: Colour & Tint",
            instruction = "If your TV has a blue-only mode, enable it and adjust COLOUR " +
                "and TINT until the blue bars appear uniform. Otherwise, use a blue " +
                "filter over your eye and adjust until the blue bars match. Most TVs " +
                "ship with colour close enough — only adjust if you see a clear cast."
        ),
        Step(
            pattern = Pattern.SHARPNESS,
            title = "Step 5: Sharpness",
            instruction = "Adjust the TV's SHARPNESS control. Lower it until the " +
                "single-pixel lines (1px zone) are clean — no bright halos around them " +
                "and no blurring. Most TVs are best at 0 or near-0. High sharpness adds " +
                "artificial edge enhancement that degrades the image."
        ),
        Step(
            pattern = Pattern.OVERSCAN,
            title = "Step 6: Overscan",
            instruction = "Check that all border markers are visible. If the outer " +
                "borders are cut off, the TV is overscanning. Look for a 'Just Scan', " +
                "'1:1 pixel', 'Screen Fit', or 'Full Pixel' setting in the TV's picture " +
                "options and enable it. Overscan crops the image and reduces resolution."
        ),
        Step(
            pattern = Pattern.GAMMA,
            title = "Step 7: Gamma",
            instruction = "The patch that blends into the grey background indicates your " +
                "TV's current gamma. Target: γ2.2 for bright rooms, BT.1886 (≈γ2.4) for " +
                "dark rooms. If your TV has a gamma control, adjust until the γ2.2 or " +
                "γ2.4 patch matches the background."
        ),
        Step(
            pattern = Pattern.GREYSCALE,
            title = "Step 8: Colour Temperature",
            instruction = "All greyscale bars should appear neutral — no red, blue, or " +
                "green tint. If greys look warm (yellowish), set colour temperature to " +
                "Warm 2 or Warm. If they look cool (bluish), use a less warm setting. " +
                "D65 (6500K) is the standard — Warm 2 on most TVs."
        ),
        Step(
            pattern = Pattern.UNIFORMITY_WHITE,
            title = "Step 9: Panel Uniformity",
            instruction = "Look for dark patches, clouding, or bright spots around the " +
                "edges (backlight bleed on LCD). Minor uniformity issues are normal and " +
                "usually invisible during content. Severe issues may warrant a panel " +
                "exchange within the return window."
        ),
        Step(
            pattern = Pattern.UNIFORMITY_GREY,
            title = "Step 10: Mid-Grey Uniformity",
            instruction = "At 50% grey, look for banding (visible stripes instead of a " +
                "smooth gradient), dirty screen effect (DSE — blotchy patches during " +
                "camera pans), or colour shifts. These are panel characteristics, not " +
                "adjustable settings."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_calibration)

        patternView = findViewById(R.id.pattern_view)
        textTitle = findViewById(R.id.text_calibration_title)
        textInstruction = findViewById(R.id.text_calibration_instruction)
        textStep = findViewById(R.id.text_calibration_step)
        btnPrev = findViewById(R.id.btn_calibration_prev)
        btnNext = findViewById(R.id.btn_calibration_next)
        btnToggleOverlay = findViewById(R.id.btn_calibration_toggle)
        instructionPanel = findViewById(R.id.calibration_instruction_panel)

        btnPrev.setOnClickListener { navigateStep(-1) }
        btnNext.setOnClickListener { navigateStep(1) }
        btnToggleOverlay.setOnClickListener { toggleOverlay() }

        showStep(0)
        btnNext.requestFocus()
    }

    private fun navigateStep(delta: Int) {
        val next = (currentStep + delta).coerceIn(0, steps.lastIndex)
        if (next != currentStep) showStep(next)
    }

    private fun showStep(index: Int) {
        currentStep = index
        val step = steps[index]

        if (step.pattern != null) {
            patternView.pattern = step.pattern
            patternView.visibility = View.VISIBLE
        } else {
            patternView.visibility = View.INVISIBLE
        }

        textTitle.text = step.title
        textInstruction.text = step.instruction
        textStep.text = getString(R.string.calibration_step_counter, index + 1, steps.size)

        btnPrev.isEnabled = index > 0
        btnNext.isEnabled = index < steps.lastIndex

        // Show the overlay for instruction steps, allow hiding for pattern viewing
        instructionPanel.visibility = View.VISIBLE
    }

    private fun toggleOverlay() {
        val visible = instructionPanel.visibility == View.VISIBLE
        instructionPanel.visibility = if (visible) View.GONE else View.VISIBLE
    }
}
