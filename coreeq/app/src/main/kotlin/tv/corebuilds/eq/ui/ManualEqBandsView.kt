package tv.corebuilds.eq.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.R
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.PeakingFilter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** A real 10-band graphic control: D-pad up/down adjusts, touch can drag a band. */
class ManualEqBandsView @JvmOverloads constructor(
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
        strokeWidth = dp(1.5f)
        color = ContextCompat.getColor(context, R.color.cb_hairline)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.parseColor("#253044")
    }
    private val zeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.parseColor("#64748B")
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cb_void)
    }
    private val positivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cb_signal_cyan)
    }
    private val negativePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cb_dusk_violet)
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cb_ink)
    }
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = ContextCompat.getColor(context, R.color.cb_signal_cyan)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.cb_slate)
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = ContextCompat.getColor(context, R.color.cb_ink)
    }

    private val gains = DoubleArray(ManualEq.BAND_CENTRES_HZ.size)
    private var focusedBand = 4
    private var dragging = false

    var onBandGainChanged: ((index: Int, gainDb: Double) -> Unit)? = null
    var onBandDescriptionChanged: ((String) -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        updateAccessibilityDescription()
    }

    fun setManualFilters(filters: List<PeakingFilter>) {
        for (index in gains.indices) gains[index] = ManualEq.gainAtBand(filters, index)
        focusedBand = focusedBand.coerceIn(0, gains.lastIndex)
        updateAccessibilityDescription()
        invalidate()
    }

    fun gainAt(index: Int): Double = gains.getOrElse(index) { 0.0 }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!hasFocus()) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (focusedBand > 0) {
                    focusedBand--
                    updateAccessibilityDescription()
                    invalidate()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (focusedBand < gains.lastIndex) {
                    focusedBand++
                    updateAccessibilityDescription()
                    invalidate()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                adjustFocusedBand(ManualEq.STEP_DB)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                adjustFocusedBand(-ManualEq.STEP_DB)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                announceSelectedBand()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                focusedBand = bandAt(event.x)
                dragging = true
                updateAccessibilityDescription()
                invalidate()
                setGainFromY(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                val band = bandAt(event.x)
                if (band != focusedBand) {
                    focusedBand = band
                    updateAccessibilityDescription()
                    invalidate()
                }
                setGainFromY(event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) return false
                dragging = false
                focusedBand = bandAt(event.x)
                updateAccessibilityDescription()
                invalidate()
                setGainFromY(event.y)
                performClick()
                announceSelectedBand()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val card = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(card, dp(18f), dp(18f), cardPaint)
        canvas.drawRoundRect(card, dp(18f), dp(18f), borderPaint)

        val top = dp(40f)
        val bottom = h - dp(34f)
        if (bottom <= top) return
        val centreY = (top + bottom) / 2f
        val span = (bottom - top) / 2f
        val slotWidth = w / gains.size
        val denseText = w < dp(700f)
        val normalSize = sp(if (denseText) 10f else 12f)
        val selectedSize = sp(if (denseText) 11f else 13f)
        labelPaint.textSize = normalSize
        activePaint.textSize = selectedSize

        // +6, +3, 0, -3 and -6 dB guides make the editable range explicit.
        for (db in listOf(6.0, 3.0, 0.0, -3.0, -6.0)) {
            val y = centreY - (db / ManualEq.MAX_GAIN_DB).toFloat() * span
            canvas.drawLine(dp(8f), y, w - dp(8f), y, if (db == 0.0) zeroPaint else gridPaint)
        }

        for (index in gains.indices) {
            val cx = slotWidth * (index + 0.5f)
            val gain = gains[index]
            val y = centreY - (gain / ManualEq.MAX_GAIN_DB).toFloat() * span
            val barWidth = min(dp(14f), slotWidth * 0.32f)
            val active = index == focusedBand && hasFocus()

            if (active) {
                val focusRect = RectF(cx - slotWidth * 0.43f, dp(6f), cx + slotWidth * 0.43f, h - dp(4f))
                canvas.drawRoundRect(focusRect, dp(10f), dp(10f), focusPaint)
            }

            val track = RectF(cx - barWidth / 2f, top, cx + barWidth / 2f, bottom)
            canvas.drawRoundRect(track, barWidth / 2f, barWidth / 2f, trackPaint)
            val fill = RectF(cx - barWidth / 2f, min(y, centreY), cx + barWidth / 2f, max(y, centreY))
            canvas.drawRoundRect(fill, barWidth / 2f, barWidth / 2f, if (gain >= 0.0) positivePaint else negativePaint)
            canvas.drawCircle(cx, y, dp(if (active) 6f else 4.5f), thumbPaint)

            val dbPrefix = if (gain > 0.0) "+" else ""
            val dbText = String.format(Locale.US, "%s%.1f", dbPrefix, gain)
            canvas.drawText(dbText, cx, dp(25f), if (active) activePaint else labelPaint)

            val frequency = ManualEq.BAND_CENTRES_HZ[index]
            val frequencyText = if (frequency >= 1_000.0) {
                val khz = frequency / 1_000.0
                if (khz == kotlin.math.floor(khz)) String.format(Locale.US, "%.0fk", khz)
                else String.format(Locale.US, "%.1fk", khz)
            } else {
                String.format(Locale.US, "%.0f", frequency)
            }
            canvas.drawText(frequencyText, cx, h - dp(10f), if (active) activePaint else labelPaint)
        }
    }

    private fun adjustFocusedBand(deltaDb: Double) {
        setGain(focusedBand, gains[focusedBand] + deltaDb)
    }

    private fun setGain(index: Int, requestedDb: Double) {
        val snapped = round(requestedDb.coerceIn(ManualEq.MIN_GAIN_DB, ManualEq.MAX_GAIN_DB) / ManualEq.STEP_DB) * ManualEq.STEP_DB
        if (kotlin.math.abs(snapped - gains[index]) < 0.001) return
        gains[index] = snapped
        updateAccessibilityDescription()
        invalidate()
        onBandGainChanged?.invoke(index, snapped)
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
    }

    private fun setGainFromY(y: Float) {
        val top = dp(40f)
        val bottom = height.toFloat() - dp(34f)
        if (bottom <= top) return
        val amount = ((y.coerceIn(top, bottom) - top) / (bottom - top)).toDouble()
        setGain(focusedBand, ManualEq.MAX_GAIN_DB - amount * (ManualEq.MAX_GAIN_DB - ManualEq.MIN_GAIN_DB))
    }

    private fun bandAt(x: Float): Int =
        (x / (width.toFloat() / gains.size)).toInt().coerceIn(0, gains.lastIndex)

    private fun updateAccessibilityDescription() {
        val frequency = ManualEq.BAND_CENTRES_HZ[focusedBand]
        val gain = gains[focusedBand]
        val description = String.format(
            Locale.US,
            "Manual equaliser. Band %d of %d, %.0f hertz, %+.1f decibels. Use left and right to choose a band, up and down to adjust.",
            focusedBand + 1,
            gains.size,
            frequency,
            gain
        )
        contentDescription = description
        onBandDescriptionChanged?.invoke(description)
    }

    private fun announceSelectedBand() {
        announceForAccessibility(contentDescription)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
