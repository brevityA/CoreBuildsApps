package tv.corebuilds.eq.display

import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import tv.corebuilds.eq.R
import tv.corebuilds.eq.TvActivity
import tv.corebuilds.eq.display.TestPatternView.Pattern

/**
 * Display calibration, as a guided walkthrough rather than a wall of text (1.9.0).
 *
 * The screen is still full-screen patterns with an instruction card, but the card now comes
 * from [CalibrationGuide]: one step at a time, and for each step the answers a person can
 * actually give — "bar 0 is still black", "everything glows", "the top row stutters" — as
 * buttons. Choosing one shows the advice for that fault, and where the advice is a repair the
 * Next button goes there instead of to the next happy-path step.
 *
 * Two things it deliberately does *not* do. It does not write to the TV's picture settings:
 * Android exposes no API for a television's picture menu, and the ones that exist need root the
 * app does not have. And it does not claim to have measured anything — [TestPatternView] draws
 * known references and the viewer judges them, which is the same instrument a calibration disc
 * relies on.
 *
 * What it reports is [CalibrationGuide.DisplayFacts], read from [Display]: the mode Android is
 * actually running (which is not always the panel's resolution), the refresh rate, and the HDR
 * formats the display advertises. Those notes sit above the steps so nobody spends an evening
 * trying to fix a 1080p interface on a 4K panel in a picture menu.
 *
 * Projectivy Launcher's display feature — pattern sets plus shortcuts into the manufacturer
 * engineering menus — was the model for the pattern coverage (judder, motion resolution and a
 * pixel grid join the old nine). Its display *profiles*, which push picture parameters on input
 * and HDR changes, are the part this cannot adopt without root; the honest equivalent here is
 * the walkthrough plus a written record in the last step.
 */
class DisplayCalibrationActivity : TvActivity() {

    private lateinit var patternView: TestPatternView
    private lateinit var textFacts: TextView
    private lateinit var textTitle: TextView
    private lateinit var textInstruction: TextView
    private lateinit var textAdvice: TextView
    private lateinit var textStep: TextView
    private lateinit var answerRow: LinearLayout
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var btnToggleOverlay: Button
    private lateinit var instructionPanel: View

    private var currentIndex = 0
    private var pendingStepId: String? = null

    private val facts: CalibrationGuide.DisplayFacts by lazy { readDisplayFacts() }

    private val step: CalibrationGuide.Step
        get() = CalibrationGuide.STEPS[currentIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_calibration)

        patternView = findViewById(R.id.pattern_view)
        patternView.hdrSupported = facts.hdrTypes.isNotEmpty()
        textFacts = findViewById(R.id.text_calibration_facts)
        textTitle = findViewById(R.id.text_calibration_title)
        textInstruction = findViewById(R.id.text_calibration_instruction)
        textAdvice = findViewById(R.id.text_calibration_advice)
        textStep = findViewById(R.id.text_calibration_step)
        answerRow = findViewById(R.id.calibration_answers)
        btnPrev = findViewById(R.id.btn_calibration_prev)
        btnNext = findViewById(R.id.btn_calibration_next)
        btnToggleOverlay = findViewById(R.id.btn_calibration_toggle)
        instructionPanel = findViewById(R.id.calibration_instruction_panel)

        textFacts.text = buildString {
            append(CalibrationGuide.factsLine(facts))
            for (note in CalibrationGuide.factsNotes(facts)) {
                append('\n').append(note)
            }
        }

        btnPrev.setOnClickListener { navigate(-1) }
        btnNext.setOnClickListener { goNext() }
        btnToggleOverlay.setOnClickListener { toggleOverlay() }

        showStep(0)
        btnNext.requestFocus()
    }

    /** What Android says this display is. Read once: a TV does not change mode mid-calibration. */
    private fun readDisplayFacts(): CalibrationGuide.DisplayFacts {
        val metrics = resources.displayMetrics
        val display: Display? = try {
            getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        } catch (e: Exception) {
            null
        }
        val mode = display?.mode
        val hdrTypes = try {
            display?.hdrCapabilities?.supportedHdrTypes?.map { hdrName(it) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return CalibrationGuide.DisplayFacts(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            refreshHz = (display?.refreshRate ?: 0f).toDouble(),
            hdrTypes = hdrTypes,
            densityDpi = metrics.densityDpi,
            panelWidthPx = mode?.physicalWidth ?: metrics.widthPixels,
            panelHeightPx = mode?.physicalHeight ?: metrics.heightPixels
        )
    }

    private fun hdrName(type: Int): String = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        else -> "HDR type $type"
    }

    /** Next on the ordinary path, to the repair step the chosen answer named, or out. */
    private fun goNext() {
        val target = pendingStepId
        if (target != null) {
            pendingStepId = null
            val index = CalibrationGuide.STEPS.indexOfFirst { it.id == target }
            if (index >= 0) {
                showStep(index)
                return
            }
        }
        if (currentIndex == CalibrationGuide.STEPS.lastIndex) {
            finish()
            return
        }
        navigate(1)
    }

    /**
     * The Next button's label is the whole navigation model: "Continue" after an answer that
     * named a repair step, "Finish" on the last step, "Next step" otherwise. It is never
     * disabled — a dead primary button on a TV, where there is no mouse to discover the reason,
     * reads as a broken screen.
     */
    private fun updateNextButton() {
        btnNext.isEnabled = true
        btnNext.text = when {
            pendingStepId != null -> getString(R.string.calibration_continue)
            currentIndex == CalibrationGuide.STEPS.lastIndex -> getString(R.string.calibration_finish)
            else -> getString(R.string.calibration_next)
        }
    }

    private fun navigate(delta: Int) {
        val next = (currentIndex + delta).coerceIn(0, CalibrationGuide.STEPS.lastIndex)
        if (next != currentIndex) showStep(next)
    }

    private fun showStep(index: Int) {
        currentIndex = index
        pendingStepId = null
        val current = step

        val pattern = current.pattern?.let { patternFor(it) }
        if (pattern != null) {
            patternView.pattern = pattern
            patternView.visibility = View.VISIBLE
        } else {
            // Instruction-only step (or a pattern this build's view does not know): a blank
            // screen is better than the wrong test.
            patternView.visibility = View.INVISIBLE
        }

        textTitle.text = current.title
        textInstruction.text = current.instruction
        textStep.text = getString(R.string.calibration_step_counter, index + 1, CalibrationGuide.STEPS.size)
        textAdvice.visibility = View.GONE
        textAdvice.text = ""

        buildAnswers(current)

        btnPrev.isEnabled = index > 0
        updateNextButton()

        instructionPanel.visibility = View.VISIBLE
    }

    /**
     * The answers for this step, as focusable buttons. They are rebuilt per step and are the
     * first thing the remote reaches, because the answer is the step's whole point; the Next
     * button is the shortcut for "the ordinary answer".
     */
    private fun buildAnswers(current: CalibrationGuide.Step) {
        answerRow.removeAllViews()
        for (branch in current.branches) {
            val button = Button(this).apply {
                text = branch.label
                isAllCaps = false
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                background = getDrawable(R.drawable.bg_ghost)
                setTextColor(getColor(R.color.ghost_text))
                minHeight = resources.getDimensionPixelSize(R.dimen.cb_target_min)
                isFocusable = true
                isFocusableInTouchMode = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.cb_space_xs) }
                setOnClickListener { choose(branch) }
            }
            answerRow.addView(button)
        }
    }

    private fun choose(branch: CalibrationGuide.Branch) {
        textAdvice.text = branch.advice
        textAdvice.visibility = View.VISIBLE
        pendingStepId = branch.nextStepId
        updateNextButton()
        btnNext.requestFocus()
    }

    /**
     * [CalibrationGuide.PatternId] → [Pattern], by name so the two lists cannot drift silently:
     * a guide pattern the view does not know leaves the screen blank rather than showing the
     * wrong test.
     */
    private fun patternFor(id: CalibrationGuide.PatternId): Pattern? =
        Pattern.entries.firstOrNull { it.name == id.name }

    private fun toggleOverlay() {
        val visible = instructionPanel.visibility == View.VISIBLE
        instructionPanel.visibility = if (visible) View.GONE else View.VISIBLE
    }
}
