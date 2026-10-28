package tv.corebuilds.eq.display

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/**
 * Renders full-screen test patterns for display calibration.
 *
 * Every pattern is drawn straight onto the [Canvas] at the view's own size, so the pixels on
 * the panel are the pixels this file computed — no image scaling, no compression.
 *
 * 1.9.0 fixed four things that made earlier versions of this file untrustworthy:
 *
 * * **Black level** used sixteen one-code steps (0…15), which is a 6% range: bars 0 and 1 are
 *   the same colour on every panel and the instruction "make bar 2 visible" could not be
 *   followed. The ladder is now two codes apart (0…30) and each bar is labelled with the code
 *   it is drawn at.
 * * **White level** labelled codes 240…255 one step apart — 253 and 255 differ by 0.8% and were
 *   never separable. The ladder now spans 200…255 and keeps 252/253/254/255 as the top four,
 *   which is the range a clipping test actually needs.
 * * **Gamma** drew `255·0.5^(1/γ)`, which is not a gamma patch at all — it put the γ2.4 patch at
 *   code 191, a near-white square on a 50% field. Patches are now derived properly: a power-law
 *   row (`255·(0.5^γ)^(1/2.2)`, i.e. γ2.2 blends at 128) and a BT.1886 row that includes the
 *   standard's black offset, so the two models can be told apart on screen.
 * * **Sizes were raw pixels**, so a 4K panel drew a 28 px label the size of a pinhead and zones
 *   that were 1 mm wide. Everything is in dp now except the two places where a device pixel is
 *   the point (the sharpness 1-pixel zone and the pixel grid).
 *
 * It also gained the three patterns the guide needed and the old screen had no way to show:
 * [Pattern.MOTION_JUDDER], [Pattern.MOTION_RESOLUTION] and [Pattern.PIXEL_GRID].
 *
 * Nothing here measures a display. It draws known references; the person looking at them is the
 * instrument, and the guide is written that way.
 */
class TestPatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Pattern(val title: String, val subtitle: String) {
        BLACK_LEVEL(
            "Black Level / Brightness",
            "Codes 0 to 30, two apart. Bar 0 must stay black; the first bar you can see should be a low number"
        ),
        WHITE_LEVEL(
            "White Level / Contrast",
            "Codes 200 to 255. Adjust contrast until 253 is distinguishable from 255"
        ),
        GREYSCALE(
            "Greyscale / Colour Temperature",
            "All bars should appear neutral grey — no colour tint"
        ),
        COLOUR_BARS(
            "SMPTE Colour Bars",
            "Use a blue filter, or the TV's colour-only mode, to check colour and tint"
        ),
        SHARPNESS(
            "Sharpness",
            "Lines should be distinct without halos or ringing. Lower is usually better"
        ),
        OVERSCAN(
            "Overscan Check",
            "All border markers must be visible and the centre grid must be fine lines, not grey"
        ),
        UNIFORMITY_WHITE(
            "Panel Uniformity — White",
            "Check for dark patches, clouding, or backlight bleed"
        ),
        UNIFORMITY_GREY(
            "Panel Uniformity — 50% Grey",
            "Check for blotches and bands (dirty screen effect)"
        ),
        GAMMA(
            "Gamma Check",
            "The patch that blends into the grey is the gamma the display is at"
        ),
        HDR_REFERENCE(
            "HDR Reference Levels",
            "ST.2084 PQ reference points. On SDR this is the PQ code ramp, not a luminance test"
        ),
        MOTION_JUDDER(
            "24p Judder",
            "Top marker updates 24 times a second, bottom one 60. Uneven top motion is 3:2 judder"
        ),
        MOTION_RESOLUTION(
            "Motion Resolution",
            "Three gratings move at different speeds. Smearing at speed is the panel's response time"
        ),
        PIXEL_GRID(
            "Pixel Grid / 1:1",
            "One-device-pixel detail. Grey or shimmering means the image is being scaled"
        );

        /** Patterns that need a redraw loop rather than one frame. */
        val animated: Boolean get() = this == MOTION_JUDDER || this == MOTION_RESOLUTION
    }

    var pattern: Pattern = Pattern.BLACK_LEVEL
        set(value) {
            field = value
            startedNanos = 0L
            invalidate()
        }

    /**
     * Whether the display reports any HDR format. The HDR pattern draws the same PQ codes
     * either way — it is the *caption* that changes, because claiming a luminance test on an
     * SDR output would be a lie the pattern cannot back up.
     */
    var hdrSupported: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var startedNanos = 0L

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * scaledDensity

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(14f)
        typeface = Typeface.MONOSPACE
    }

    private val darkLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 24, 24)
        textSize = sp(14f)
        typeface = Typeface.MONOSPACE
    }

    private val dimLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(96, 96, 96)
        textSize = sp(12f)
        typeface = Typeface.MONOSPACE
    }

    private val fillPaint = Paint().apply { style = Paint.Style.FILL }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        when (pattern) {
            Pattern.BLACK_LEVEL -> drawBlackLevel(canvas, w, h)
            Pattern.WHITE_LEVEL -> drawWhiteLevel(canvas, w, h)
            Pattern.GREYSCALE -> drawGreyscale(canvas, w, h)
            Pattern.COLOUR_BARS -> drawColourBars(canvas, w, h)
            Pattern.SHARPNESS -> drawSharpness(canvas, w, h)
            Pattern.OVERSCAN -> drawOverscan(canvas, w, h)
            Pattern.UNIFORMITY_WHITE -> drawUniformity(canvas, w, h, Color.WHITE)
            Pattern.UNIFORMITY_GREY -> drawUniformity(canvas, w, h, Color.rgb(128, 128, 128))
            Pattern.GAMMA -> drawGamma(canvas, w, h)
            Pattern.HDR_REFERENCE -> drawHdrReference(canvas, w, h)
            Pattern.MOTION_JUDDER -> drawMotionJudder(canvas, w, h)
            Pattern.MOTION_RESOLUTION -> drawMotionResolution(canvas, w, h)
            Pattern.PIXEL_GRID -> drawPixelGrid(canvas, w, h)
        }

        if (pattern.animated) postInvalidateOnAnimation()
        else startedNanos = 0L
    }

    /** Seconds since this pattern started animating; 0 until the first frame. */
    private fun elapsedSeconds(): Double {
        if (startedNanos == 0L) {
            startedNanos = System.nanoTime()
            return 0.0
        }
        return (System.nanoTime() - startedNanos) / 1_000_000_000.0
    }

    private fun drawCentredText(canvas: Canvas, text: String, centreX: Float, baselineY: Float, paint: Paint) {
        canvas.drawText(text, centreX - paint.measureText(text) / 2f, baselineY, paint)
    }

    private fun drawHeader(canvas: Canvas, w: Int, h: Int, title: String, subtitle: String, paint: Paint) {
        canvas.drawText(title, dp(24f), dp(28f), paint)
        canvas.drawText(subtitle, dp(24f), dp(48f), paint)
    }

    /**
     * Black level: sixteen bars two digital codes apart, 0 to 30.
     *
     * Why two codes: a display maps 8-bit codes through a gamma curve, so code 0 and code 1
     * differ by 0.4% of white and no panel on the market separates them. A one-code ladder
     * (the old version) made the instruction "bar 2 should just be visible" untestable — the
     * bars it named were already invisible. Two-code steps are the finest ladder that can
     * still be counted from a sofa.
     *
     * The caption is deliberately brighter than the ladder (code 96) so it reads as an overlay
     * and not as one of the test bars.
     */
    private fun drawBlackLevel(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.BLACK
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val codes = DisplayMath.BLACK_LADDER
        val barWidth = w.toFloat() / codes.size
        val barTop = h * 0.28f
        val barHeight = h * 0.34f

        for (i in codes.indices) {
            val code = codes[i]
            fillPaint.color = Color.rgb(code, code, code)
            val left = i * barWidth
            canvas.drawRect(left, barTop, left + barWidth, barTop + barHeight, fillPaint)
            drawCentredText(
                canvas,
                "$code",
                left + barWidth / 2f,
                barTop + barHeight + dp(20f),
                dimLabelPaint
            )
        }

        drawHeader(
            canvas, w, h,
            "BLACK LEVEL",
            "Codes 0-30, two apart · bar 0 must stay black",
            dimLabelPaint
        )

        // A large true-black field under the ladder: the eye needs somewhere to rest before
        // judging whether the darkest bar is black or merely dark. fillPaint still holds bar
        // 15's grey at this point, so the field sets its own colour rather than inheriting it.
        fillPaint.color = Color.BLACK
        canvas.drawRect(0f, barTop + barHeight + dp(32f), w.toFloat(), h.toFloat(), fillPaint)
    }

    /**
     * White level: eleven bars from code 200 to 255 on a white field, ending 250 / 253 / 255 so
     * that "253 is distinguishable from 255" is a test a person can actually perform: those two
     * differ by two codes, which is the smallest step that survives a real panel. The old ladder
     * stepped by one code from 240 and its last sixteen bars were a flat white block.
     */
    private fun drawWhiteLevel(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.WHITE
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val codes = DisplayMath.WHITE_LADDER
        val barWidth = w.toFloat() / codes.size
        val barTop = h * 0.28f
        val barHeight = h * 0.34f

        for (i in codes.indices) {
            val code = codes[i]
            fillPaint.color = Color.rgb(code, code, code)
            val left = i * barWidth
            canvas.drawRect(left, barTop, left + barWidth, barTop + barHeight, fillPaint)
            drawCentredText(
                canvas,
                "$code",
                left + barWidth / 2f,
                barTop + barHeight + dp(20f),
                darkLabelPaint
            )
        }

        drawHeader(
            canvas, w, h,
            "WHITE LEVEL",
            "Codes 200-255 · adjust contrast until 253 separates from 255",
            darkLabelPaint
        )
    }

    /** Eleven neutral steps, 0% to 100%, for colour-temperature and tint checks. */
    private fun drawGreyscale(canvas: Canvas, w: Int, h: Int) {
        val steps = 11
        val barWidth = w.toFloat() / steps
        for (i in 0 until steps) {
            val level = (i * 255.0 / (steps - 1)).roundToInt()
            fillPaint.color = Color.rgb(level, level, level)
            canvas.drawRect(i * barWidth, 0f, (i + 1) * barWidth, h.toFloat(), fillPaint)
            val paint = if (level > 128) darkLabelPaint else labelPaint
            drawCentredText(canvas, "${i * 10}%", i * barWidth + barWidth / 2f, h / 2f, paint)
        }
    }

    /** SMPTE-style 75% colour bars: white, yellow, cyan, green, magenta, red, blue, black. */
    private fun drawColourBars(canvas: Canvas, w: Int, h: Int) {
        val bars = listOf(
            intArrayOf(191, 191, 191) to "WHITE",
            intArrayOf(191, 191, 0) to "YELLOW",
            intArrayOf(0, 191, 191) to "CYAN",
            intArrayOf(0, 191, 0) to "GREEN",
            intArrayOf(191, 0, 191) to "MAGENTA",
            intArrayOf(191, 0, 0) to "RED",
            intArrayOf(0, 0, 191) to "BLUE",
            intArrayOf(0, 0, 0) to "BLACK"
        )
        val barWidth = w.toFloat() / bars.size
        for (i in bars.indices) {
            val (rgb, name) = bars[i]
            fillPaint.color = Color.rgb(rgb[0], rgb[1], rgb[2])
            canvas.drawRect(i * barWidth, 0f, (i + 1) * barWidth, h.toFloat(), fillPaint)
            val paint = if (rgb[0] + rgb[1] + rgb[2] > 380) darkLabelPaint else labelPaint
            drawCentredText(canvas, name, i * barWidth + barWidth / 2f, h - dp(40f), paint)
        }
        drawHeader(canvas, w, h, "SMPTE COLOUR BARS · 75%", "Blue filter or colour-only mode", labelPaint)
    }

    /**
     * Sharpness: one native-pixel zone first, then zones whose line spacing is given in dp, so
     * the physical spacing is the same on a 1080p and a 4K panel. Edge enhancement shows as a
     * bright halo either side of each line; the 1-pixel zone is where it is least forgivable.
     */
    private fun drawSharpness(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.rgb(64, 64, 64)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val zones = listOf(1f to "1 device px", dp(2f) to "2 dp", dp(3f) to "3 dp", dp(4f) to "4 dp", dp(6f) to "6 dp", dp(9f) to "9 dp")
        val zoneWidth = w.toFloat() / zones.size
        linePaint.color = Color.rgb(200, 200, 200)
        linePaint.strokeWidth = 1f

        for (z in zones.indices) {
            val (spacing, caption) = zones[z]
            val xStart = z * zoneWidth
            var x = xStart + 1f
            while (x < xStart + zoneWidth) {
                canvas.drawLine(x, h * 0.18f, x, h * 0.78f, linePaint)
                x += spacing * 2f
            }
            drawCentredText(canvas, caption, xStart + zoneWidth / 2f, h * 0.84f, labelPaint)
        }
        drawHeader(
            canvas, w, h,
            "SHARPNESS",
            "Lines should be clean with no halo or ringing · lowest setting that stays sharp",
            labelPaint
        )
    }

    /**
     * Overscan: nested border markers at 2.5%, 5% and 10%, plus a one-device-pixel grid in the
     * middle. The grid is the second half of the test: with smoothing upscaling, the grid turns
     * into flat grey even when no marker is cropped.
     */
    private fun drawOverscan(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.rgb(32, 32, 32)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        linePaint.color = Color.WHITE
        linePaint.strokeWidth = dp(1.5f)
        for (pct in listOf(0.025f, 0.05f, 0.10f)) {
            val dx = w * pct
            val dy = h * pct
            canvas.drawRect(dx, dy, w - dx, h - dy, linePaint)
            canvas.drawText("${(pct * 100).roundToInt()}%", dx + dp(8f), dy + dp(20f), labelPaint)
        }

        // Centre: a device-pixel checkerboard, the thing scaling destroys first.
        val cell = 1
        val gridHalfW = (w * 0.12f).toInt()
        val gridHalfH = (h * 0.12f).toInt()
        fillPaint.color = Color.WHITE
        var y = h / 2 - gridHalfH
        var row = 0
        while (y < h / 2 + gridHalfH) {
            var x = w / 2 - gridHalfW
            var col = 0
            while (x < w / 2 + gridHalfW) {
                if ((row + col) % 2 == 0) {
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + cell).toFloat(), (y + cell).toFloat(), fillPaint)
                }
                x += cell
                col++
            }
            y += cell
            row++
        }

        drawHeader(
            canvas, w, h,
            "OVERSCAN CHECK",
            "All markers visible, and the centre grid must be fine lines — grey means scaling",
            labelPaint
        )
    }

    /** A flat field, for spotting clouding, banding, dead pixels and backlight bleed. */
    private fun drawUniformity(canvas: Canvas, w: Int, h: Int, colour: Int) {
        fillPaint.color = colour
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)
        val paint = if (Color.red(colour) > 128) darkLabelPaint else labelPaint
        canvas.drawText("UNIFORMITY CHECK", dp(24f), dp(28f), paint)
        canvas.drawText("Look for uneven patches, clouding, or dead pixels", dp(24f), dp(48f), paint)
    }

    /**
     * Gamma: the 50% signal rendered under five gamma values, twice.
     *
     * The code for a patch is `255·(L_rel)^(1/2.2)`, where `L_rel` is the luminance that model
     * gives for a 50% signal — the inverse of the display's own transfer, which is the only way
     * a patch can blend with a 50% field when the model is right. A pure power law gives
     * `L_rel = 0.5^γ`; BT.1886 adds the standard's black offset
     * (`L_rel = a·(0.5+b)^2.4` with `a, b` fixed by a 0.01 nit black and 100 nit white).
     *
     * The old code drew `255·0.5^(1/γ)`, which is neither of those: it put the γ2.4 patch at
     * code 191 — a bright square on a 128 field — so every patch looked wrong and the test
     * pointed at the wrong gamma. The BT.1886 row sits a few codes above the power-law row
     * because the standard's black offset lifts mid-grey; that is the standard, not a rounding
     * error.
     */
    private fun drawGamma(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.rgb(128, 128, 128)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val gammas = DisplayMath.GAMMAS
        val patchSize = dp(96f)
        val gap = dp(24f)
        val rowCentre = doubleArrayOf(h * 0.40, h * 0.70)

        for (row in 0..1) {
            val bt1886 = row == 1
            val totalWidth = gammas.size * patchSize + (gammas.size - 1) * gap
            val startX = w / 2f - totalWidth / 2f
            val top = rowCentre[row] - patchSize / 2f
            for (i in gammas.indices) {
                val gamma = gammas[i]
                val code = DisplayMath.gammaPatchCode(gamma, bt1886)
                fillPaint.color = Color.rgb(code, code, code)
                val x = startX + i * (patchSize + gap)
                canvas.drawRect(x, top, x + patchSize, top + patchSize, fillPaint)
                drawCentredText(
                    canvas,
                    "γ$gamma = $code",
                    x + patchSize / 2f,
                    top + patchSize + dp(18f),
                    labelPaint
                )
            }
            canvas.drawText(
                if (bt1886) "BT.1886 (0.01 nit black, 100 nit white)" else "POWER LAW",
                dp(24f),
                rowCentre[row] - patchSize / 2f - dp(10f),
                labelPaint
            )
        }

        drawHeader(
            canvas, w, h,
            "GAMMA CHECK",
            "On each row, the patch that disappears into the grey is the display's gamma",
            labelPaint
        )
    }

    /**
     * HDR reference levels: six ST.2084 (PQ) codes, computed from the standard rather than
     * guessed.
     *
     * PQ is a *signal* encoding, not a luminance: 100 nits sits at code 128 of 255, 1000 nits
     * at 192. On an HDR output the panel's PQ EOTF turns those codes into the stated
     * luminances; on an SDR output the same codes are a bottom-weighted grey ramp and nothing
     * more. The caption says which case this is, because the old pattern picked six arbitrary
     * greys and called them HDR levels whatever the display was doing.
     */
    private fun drawHdrReference(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.BLACK
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val levels = DisplayMath.HDR_NITS
        val patchWidth = w.toFloat() / (levels.size * 1.5f)
        val patchHeight = h * 0.34f
        val patchTop = h * 0.30f
        val gap = (w - levels.size * patchWidth) / (levels.size + 1)

        for (i in levels.indices) {
            val nits = levels[i]
            val code = DisplayMath.pqCode(nits)
            fillPaint.color = Color.rgb(code, code, code)
            val x = gap + i * (patchWidth + gap)
            canvas.drawRect(x, patchTop, x + patchWidth, patchTop + patchHeight, fillPaint)
            drawCentredText(
                canvas,
                "${DisplayMath.formatNits(nits)} nits",
                x + patchWidth / 2f,
                patchTop + patchHeight + dp(18f),
                labelPaint
            )
            drawCentredText(canvas, "PQ $code", x + patchWidth / 2f, patchTop + patchHeight + dp(36f), labelPaint)
        }

        canvas.drawText("HDR REFERENCE LEVELS", dp(24f), dp(28f), labelPaint)
        canvas.drawText(
            if (hdrSupported) {
                "This output reports HDR: codes are PQ, so the patches should rise steeply"
            } else {
                "SDR OUTPUT — the PQ code ramp only. Luminance claims do not apply here"
            },
            dp(24f),
            dp(48f),
            if (hdrSupported) labelPaint else dimLabelPaint
        )
    }

    /**
     * 24p judder: two markers sweeping the same path, the top one updated 24 times a second and
     * the bottom one every frame. On a 60 Hz panel the top marker advances by 2.5 frames each
     * update, which is exactly the uneven motion film sees — this shows it as a mechanism rather
     * than as a judgement about a film scene.
     */
    private fun drawMotionJudder(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.BLACK
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val seconds = elapsedSeconds()
        val margin = dp(40f)
        val trackWidth = w - margin * 2
        val markerWidth = dp(28f)
        val markerHeight = dp(48f)

        val rows = listOf(0.36f to 24.0, 0.62f to 60.0)
        for ((fraction, fps) in rows) {
            val y = h * fraction
            linePaint.color = Color.rgb(64, 64, 64)
            linePaint.strokeWidth = dp(1f)
            canvas.drawLine(margin, y, w - margin, y, linePaint)

            val updates = Math.floor(seconds * fps)
            val phase = (updates / fps) % 1.0 // whole-second loop, stepped at fps
            val x = margin + (trackWidth - markerWidth) * phase.toFloat()

            fillPaint.color = Color.WHITE
            canvas.drawRect(x, y - markerHeight / 2f, x + markerWidth, y + markerHeight / 2f, fillPaint)
            canvas.drawText(
                "${fps.roundToInt()} updates/s",
                margin,
                y - markerHeight,
                labelPaint
            )
        }

        drawHeader(
            canvas, w, h,
            "24p JUDDER",
            "Top moves 24 times a second on a 60 Hz panel; the uneven stepping is 3:2 judder",
            labelPaint
        )
    }

    /**
     * Motion resolution: the same grating dragged across the screen at three speeds. An LCD
     * loses detail as speed rises (sample-and-hold plus response time); this shows the loss
     * instead of describing it. The bars are one-dp lines, so what smears is the panel.
     */
    private fun drawMotionResolution(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.rgb(24, 24, 24)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val seconds = elapsedSeconds()
        val speeds = listOf(dp(60f) to "60 dp/s", dp(200f) to "200 dp/s", dp(600f) to "600 dp/s")
        val rowHeight = h * 0.22f

        for (i in speeds.indices) {
            val (speed, caption) = speeds[i]
            val top = h * (0.30f + 0.22f * i)
            val offset = (seconds * speed) % dp(64f)
            fillPaint.color = Color.WHITE
            var x = -dp(64f) + offset.toFloat()
            while (x < w) {
                canvas.drawRect(x, top, x + dp(3f), top + rowHeight, fillPaint)
                x += dp(8f)
            }
            canvas.drawText(caption, dp(24f), top + rowHeight + dp(18f), labelPaint)
        }

        drawHeader(
            canvas, w, h,
            "MOTION RESOLUTION",
            "Grating thins out as it speeds up on a sample-and-hold panel — that is response time",
            labelPaint
        )
    }

    /**
     * Pixel grid: one-device-pixel detail over the whole screen.
     *
     * If this looks like flat grey from the sofa it is normal at that distance; the test is what
     * happens up close and whether the fine structure is *stable*. Shimmering, coloured fringes
     * or a moiré that crawls means the image is being scaled or smoothed, not displayed 1:1.
     */
    private fun drawPixelGrid(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.BLACK
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val bandTop = h * 0.22f
        val bandBottom = h * 0.78f

        // Left half: alternating device-pixel columns.
        fillPaint.color = Color.WHITE
        var x = 0
        while (x < w / 2) {
            canvas.drawRect(x.toFloat(), bandTop, (x + 1).toFloat(), bandBottom, fillPaint)
            x += 2
        }
        // Right half: device-pixel rows, which fail differently when only one axis is scaled.
        var y = bandTop.toInt()
        while (y < bandBottom.toInt()) {
            canvas.drawRect(w / 2f, y.toFloat(), w.toFloat(), (y + 1).toFloat(), fillPaint)
            y += 2
        }

        // Centre: a real device-pixel checkerboard, the classic 1:1 test.
        val cell = 1
        val half = (Math.min(w, h) * 0.06f).toInt()
        var row = 0
        var cy = h / 2 - half
        while (cy < h / 2 + half) {
            var col = 0
            var cx = w / 2 - half
            while (cx < w / 2 + half) {
                fillPaint.color = if ((row + col) % 2 == 0) Color.WHITE else Color.BLACK
                canvas.drawRect(cx.toFloat(), cy.toFloat(), (cx + cell).toFloat(), (cy + cell).toFloat(), fillPaint)
                cx += cell
                col++
            }
            cy += cell
            row++
        }

        drawHeader(
            canvas, w, h,
            "PIXEL GRID · 1 DEVICE PIXEL",
            "Lines must be stable, not shimmering. Grey from a distance is normal; crawling is not",
            labelPaint
        )
    }

    /** True when the pattern currently on screen needs a redraw loop. Used by the activity. */
    val isAnimated: Boolean get() = pattern.animated
}
