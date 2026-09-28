package tv.corebuilds.eq.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.R
import tv.corebuilds.eq.export.PlatformBand
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Platform equaliser bands D-pad row: vertical slider cards matching the mockups.
 */
class BandSlidersView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cb_card)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.cb_hairline)
    }

    private val focusRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = ContextCompat.getColor(context, R.color.cb_signal_cyan)
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cb_void)
    }

    private val barPositivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cb_signal_cyan)
    }

    private val barNegativePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cb_dusk_violet)
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cb_ink)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.cb_slate)
    }

    private val textActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = ContextCompat.getColor(context, R.color.cb_ink)
    }

    private val bands = mutableListOf<PlatformBand>()
    private var focusedBandIndex = 2

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setBands(newBands: List<PlatformBand>) {
        bands.clear()
        bands.addAll(newBands)
        if (focusedBandIndex >= bands.size) {
            focusedBandIndex = max(0, bands.size - 1)
        }
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!hasFocus()) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (focusedBandIndex > 0) {
                    focusedBandIndex--
                    invalidate()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (focusedBandIndex < bands.size - 1) {
                    focusedBandIndex++
                    invalidate()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0 || bands.isEmpty()) return

        val rect = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(rect, 20f, 20f, cardPaint)
        canvas.drawRoundRect(rect, 20f, 20f, borderPaint)

        val n = bands.size
        val slotWidth = w / n
        val midY = h / 2f
        val span = h * 0.28f

        // Center line
        canvas.drawLine(20f, midY, w - 20f, midY, borderPaint)

        for (i in bands.indices) {
            val b = bands[i]
            val cx = slotWidth * (i + 0.5f)
            val db = b.millibels / 100f
            val y = midY - (db / 15f) * span
            val barW = 18f
            val isFocused = (i == focusedBandIndex && hasFocus())

            if (isFocused) {
                val fRect = RectF(cx - slotWidth * 0.4f, 10f, cx + slotWidth * 0.4f, h - 10f)
                canvas.drawRoundRect(fRect, 16f, 16f, focusRingPaint)
            }

            // Track
            val trackRect = RectF(cx - barW / 2f, midY - span, cx + barW / 2f, midY + span)
            canvas.drawRoundRect(trackRect, barW / 2f, barW / 2f, trackPaint)

            // Fill bar
            val top = min(y, midY)
            val bot = max(y, midY)
            val fillRect = RectF(cx - barW / 2f, top, cx + barW / 2f, bot)
            val fillPaint = if (db >= 0) barPositivePaint else barNegativePaint
            canvas.drawRoundRect(fillRect, barW / 2f, barW / 2f, fillPaint)

            // Thumb
            canvas.drawCircle(cx, y, 10f, thumbPaint)

            // Frequency label below
            val freqLabel = if (b.centerHz >= 1000.0) {
                String.format(Locale.US, "%.1fk", b.centerHz / 1000.0)
            } else {
                String.format(Locale.US, "%.0fHz", b.centerHz)
            }
            canvas.drawText(freqLabel, cx, h - 14f, if (isFocused) textActivePaint else textPaint)

            // dB level label above
            val sign = if (db > 0) "+" else ""
            val dbLabel = String.format(Locale.US, "%s%.1fdB", sign, db)
            canvas.drawText(dbLabel, cx, 30f, if (isFocused) textActivePaint else textPaint)
        }
    }
}
