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
 * Renders full-screen, pixel-accurate test patterns for display calibration.
 *
 * Each pattern is drawn directly onto the [Canvas] at the view's full size,
 * so the TV displays exactly the pixels Core Display intends — no scaling,
 * no compression, no colour management by an intermediate layer.
 *
 * Patterns are selected by [Pattern] enum; the view redraws when the
 * pattern changes. The surrounding activity provides the instruction
 * overlay and the navigation between patterns.
 */
class TestPatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Pattern(val title: String, val subtitle: String) {
        BLACK_LEVEL(
            "Black Level / Brightness",
            "Adjust TV brightness until bar 2 is just visible above black"
        ),
        WHITE_LEVEL(
            "White Level / Contrast",
            "Adjust TV contrast until bar 248 is just distinguishable from 255"
        ),
        GREYSCALE(
            "Greyscale / Colour Temperature",
            "All bars should appear neutral grey — no colour tint"
        ),
        COLOUR_BARS(
            "SMPTE Colour Bars",
            "Use blue filter or TV colour-only mode to align colour and tint"
        ),
        SHARPNESS(
            "Sharpness",
            "Lines should be distinct without halos or ringing. Lower is usually better"
        ),
        OVERSCAN(
            "Overscan Check",
            "All border markers should be visible. If not, the TV is cropping the image"
        ),
        UNIFORMITY_WHITE(
            "Panel Uniformity — White",
            "Check for dark patches, clouding, or backlight bleed"
        ),
        UNIFORMITY_GREY(
            "Panel Uniformity — 50% Grey",
            "Check for uniformity issues at mid-grey"
        ),
        GAMMA(
            "Gamma Check",
            "The grey squares should blend into the background at the correct gamma"
        ),
        HDR_REFERENCE(
            "HDR Reference Levels",
            "Verifies ST.2084 PQ tracking and peak luminance"
        )
    }

    var pattern: Pattern = Pattern.BLACK_LEVEL
        set(value) {
            field = value
            invalidate()
        }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.MONOSPACE
    }

    private val darkLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 28f
        typeface = Typeface.MONOSPACE
    }

    private val fillPaint = Paint().apply { style = Paint.Style.FILL }

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
        }
    }

    /**
     * Black level: 16 near-black bars from IRE 0 (absolute black) to IRE 15,
     * each one digital step brighter. The user adjusts TV brightness until
     * the lowest bar above absolute black is just distinguishable.
     */
    private fun drawBlackLevel(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.BLACK
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val barCount = 16
        val barWidth = w / barCount
        val barHeight = (h * 0.6f).toInt()
        val barTop = (h * 0.2f).toInt()

        for (i in 0 until barCount) {
            val level = i // 0–15 digital levels above black
            val grey = (level * 255.0 / 255.0).roundToInt().coerceIn(0, 255)
            fillPaint.color = Color.rgb(grey, grey, grey)
            val left = i * barWidth
            canvas.drawRect(left.toFloat(), barTop.toFloat(), (left + barWidth).toFloat(), (barTop + barHeight).toFloat(), fillPaint)

            // Label
            val label = if (i == 0) "0" else "$i"
            canvas.drawText(label, left + barWidth / 2f - 8f, (barTop + barHeight + 40).toFloat(), labelPaint)
        }

        // Title
        canvas.drawText("BLACK LEVEL", 40f, 50f, labelPaint)
        canvas.drawText("Adjust brightness until bar 2 is just visible", 40f, 85f, labelPaint)
    }

    /**
     * White level: 16 near-white bars from IRE 240 to IRE 255.
     * The user adjusts contrast until the highest bars are distinguishable.
     */
    private fun drawWhiteLevel(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.WHITE
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val barCount = 16
        val barWidth = w / barCount
        val barHeight = (h * 0.6f).toInt()
        val barTop = (h * 0.2f).toInt()

        for (i in 0 until barCount) {
            val level = 240 + i
            fillPaint.color = Color.rgb(level, level, level)
            val left = i * barWidth
            canvas.drawRect(left.toFloat(), barTop.toFloat(), (left + barWidth).toFloat(), (barTop + barHeight).toFloat(), fillPaint)

            canvas.drawText("$level", left + barWidth / 2f - 16f, (barTop + barHeight + 40).toFloat(), darkLabelPaint)
        }

        canvas.drawText("WHITE LEVEL", 40f, 50f, darkLabelPaint)
        canvas.drawText("Adjust contrast until bar 253 is distinguishable from 255", 40f, 85f, darkLabelPaint)
    }

    /**
     * Greyscale: 11 steps from 0% to 100%. All should appear neutral grey
     * with no colour cast. A warm tint means too much red; a cool tint means
     * too much blue.
     */
    private fun drawGreyscale(canvas: Canvas, w: Int, h: Int) {
        val steps = 11
        val barWidth = w / steps
        for (i in 0 until steps) {
            val level = (i * 255.0 / (steps - 1)).roundToInt()
            fillPaint.color = Color.rgb(level, level, level)
            canvas.drawRect(
                (i * barWidth).toFloat(), 0f,
                ((i + 1) * barWidth).toFloat(), h.toFloat(), fillPaint
            )
            val label = "${(i * 10)}%"
            val paint = if (level > 128) darkLabelPaint else labelPaint
            canvas.drawText(label, i * barWidth + barWidth / 2f - 20f, h / 2f, paint)
        }
    }

    /**
     * SMPTE-style colour bars at 75% amplitude.
     * White, Yellow, Cyan, Green, Magenta, Red, Blue, Black.
     */
    private fun drawColourBars(canvas: Canvas, w: Int, h: Int) {
        val bars = listOf(
            intArrayOf(191, 191, 191),  // 75% white
            intArrayOf(191, 191, 0),    // 75% yellow
            intArrayOf(0, 191, 191),    // 75% cyan
            intArrayOf(0, 191, 0),      // 75% green
            intArrayOf(191, 0, 191),    // 75% magenta
            intArrayOf(191, 0, 0),      // 75% red
            intArrayOf(0, 0, 191),      // 75% blue
            intArrayOf(0, 0, 0)         // black
        )
        val barWidth = w / bars.size
        for (i in bars.indices) {
            fillPaint.color = Color.rgb(bars[i][0], bars[i][1], bars[i][2])
            canvas.drawRect(
                (i * barWidth).toFloat(), 0f,
                ((i + 1) * barWidth).toFloat(), h.toFloat(), fillPaint
            )
        }
        canvas.drawText("SMPTE COLOUR BARS · 75%", 40f, 50f, labelPaint)
    }

    /**
     * Sharpness: alternating single-pixel vertical lines at various
     * spacings. If the TV's sharpness is too high, lines will show
     * ringing (bright halos). Too low and they blur together.
     */
    private fun drawSharpness(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.rgb(64, 64, 64)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val linePaint = Paint().apply {
            color = Color.rgb(192, 192, 192)
            strokeWidth = 1f
        }

        // Multiple zones with different line spacings
        val zones = listOf(1, 2, 3, 4, 6, 8)
        val zoneWidth = w / zones.size

        for (z in zones.indices) {
            val spacing = zones[z]
            val xStart = z * zoneWidth
            var x = xStart
            while (x < xStart + zoneWidth) {
                canvas.drawLine(x.toFloat(), (h * 0.15f), x.toFloat(), (h * 0.85f), linePaint)
                x += spacing * 2
            }
            canvas.drawText(
                "${spacing}px",
                xStart + zoneWidth / 2f - 20f,
                (h * 0.92f),
                labelPaint
            )
        }
        canvas.drawText("SHARPNESS", 40f, 50f, labelPaint)
        canvas.drawText("Lines should be clean, no halos or blurring", 40f, 85f, labelPaint)
    }

    /**
     * Overscan: border markers at 2.5%, 5%, and 10% inset. If the TV is
     * overscanning, the outer borders will be cropped.
     */
    private fun drawOverscan(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.rgb(32, 32, 32)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.WHITE
        }

        val percents = listOf(0.025f, 0.05f, 0.10f)
        for (pct in percents) {
            val dx = w * pct
            val dy = h * pct
            canvas.drawRect(dx, dy, w - dx, h - dy, borderPaint)
            canvas.drawText(
                "${(pct * 100).roundToInt()}%",
                dx + 10f, dy + 30f, labelPaint
            )
        }

        // Centre crosshair
        canvas.drawLine(w / 2f - 40f, h / 2f, w / 2f + 40f, h / 2f, borderPaint)
        canvas.drawLine(w / 2f, h / 2f - 40f, w / 2f, h / 2f + 40f, borderPaint)

        canvas.drawText("OVERSCAN CHECK", 40f, 50f, labelPaint)
        canvas.drawText("All borders should be visible", 40f, 85f, labelPaint)
    }

    /**
     * Uniformity: a single flat colour filling the entire screen.
     * Check for dark patches, clouding, backlight bleed, or dead pixels.
     */
    private fun drawUniformity(canvas: Canvas, w: Int, h: Int, colour: Int) {
        fillPaint.color = colour
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)
        val paint = if (Color.red(colour) > 128) darkLabelPaint else labelPaint
        canvas.drawText("UNIFORMITY CHECK", 40f, 50f, paint)
        canvas.drawText("Look for uneven patches, clouding, or dead pixels", 40f, 85f, paint)
    }

    /**
     * Gamma: a series of mid-grey patches on a grey background. The patches
     * are at known luminance levels that correspond to specific gamma values.
     * When the patch blends into the background, the TV's gamma matches.
     */
    private fun drawGamma(canvas: Canvas, w: Int, h: Int) {
        // Background: 50% grey
        fillPaint.color = Color.rgb(128, 128, 128)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        // Patches at different gamma assumptions
        // For gamma 2.2: 50% signal = 128 digital
        // For gamma 2.4: 50% signal = 119 digital
        // For gamma 1.8: 50% signal = 145 digital
        val gammas = listOf(1.8, 2.0, 2.2, 2.4, 2.6)
        val patchSize = (w * 0.12f).toInt()
        val patchY = h / 2 - patchSize / 2
        val startX = w / 2 - (gammas.size * patchSize + (gammas.size - 1) * 20) / 2

        for (i in gammas.indices) {
            val gamma = gammas[i]
            val level = (255.0 * Math.pow(0.5, 1.0 / gamma)).roundToInt()
            fillPaint.color = Color.rgb(level, level, level)
            val x = startX + i * (patchSize + 20)
            canvas.drawRect(x.toFloat(), patchY.toFloat(), (x + patchSize).toFloat(), (patchY + patchSize).toFloat(), fillPaint)
            canvas.drawText("γ$gamma", x.toFloat(), (patchY + patchSize + 35).toFloat(), labelPaint)
        }

        canvas.drawText("GAMMA CHECK", 40f, 50f, labelPaint)
        canvas.drawText("The patch matching the background indicates your TV's gamma", 40f, 85f, labelPaint)
    }

    /**
     * HDR reference levels: patches at ST.2084 PQ reference luminance
     * points. On an SDR display this is informational; on an HDR display
     * it verifies PQ tracking.
     */
    private fun drawHdrReference(canvas: Canvas, w: Int, h: Int) {
        fillPaint.color = Color.BLACK
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fillPaint)

        // PQ reference levels and their approximate sRGB mappings for display
        val levels = listOf(
            Triple("0.01", 3, "Black floor"),
            Triple("1", 20, "1 nit"),
            Triple("10", 60, "10 nits"),
            Triple("100", 128, "100 nits (SDR peak)"),
            Triple("400", 180, "400 nits"),
            Triple("1000", 220, "1000 nits")
        )

        val patchW = w / (levels.size + 1)
        val patchH = (h * 0.4f).toInt()
        val patchY = h / 2 - patchH / 2

        for (i in levels.indices) {
            val (_, grey, label) = levels[i]
            fillPaint.color = Color.rgb(grey, grey, grey)
            val x = (i + 0.5f) * patchW
            canvas.drawRect(x, patchY.toFloat(), x + patchW * 0.8f, (patchY + patchH).toFloat(), fillPaint)
            canvas.drawText(label, x + 10f, (patchY + patchH + 35).toFloat(), labelPaint)
            canvas.drawText("${levels[i].first} nits", x + 10f, (patchY + patchH + 70).toFloat(), labelPaint)
        }

        canvas.drawText("HDR REFERENCE LEVELS", 40f, 50f, labelPaint)
        canvas.drawText("On SDR: verify grey ramp is smooth. On HDR: verify peak luminance", 40f, 85f, labelPaint)
    }


}
