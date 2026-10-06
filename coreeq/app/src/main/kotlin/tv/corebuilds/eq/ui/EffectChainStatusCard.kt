package tv.corebuilds.eq.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.R
import tv.corebuilds.eq.apply.EffectChainStatus
import tv.corebuilds.eq.mode.ContentType

/**
 * A modern status card that displays the current effect chain configuration,
 * including active effects, content type, and device information.
 * 
 * Features:
 * - Real-time effect chain status visualization
 * - Content type indicator with color coding
 * - Active effects badges (EQ, Bass, Loudness, Dynamics)
 * - Night mode indicator
 * - Smooth animations for state changes
 */
class EffectChainStatusCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var status: EffectChainStatus? = null
    private var deviceName: String = "Unknown Device"
    private var deviceType: String = "unknown"
    private var hdmiPassthrough: Boolean = false
    
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cb_card_bg)
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.cb_border)
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = ContextCompat.getColor(context, R.color.cb_ink)
    }
    
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = ContextCompat.getColor(context, R.color.cb_slate)
    }
    
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = ContextCompat.getColor(context, R.color.cb_ink)
        textAlign = Paint.Align.CENTER
    }
    
    private val activeColor = ContextCompat.getColor(context, R.color.cb_success)
    private val inactiveColor = ContextCompat.getColor(context, R.color.cb_slate)
    private val warningColor = ContextCompat.getColor(context, R.color.cb_ember)
    
    init {
        // Minimum size for the card
        minimumWidth = 400
        minimumHeight = 200
    }
    
    /**
     * Update the card with new effect chain status
     */
    fun updateStatus(newStatus: EffectChainStatus?) {
        status = newStatus
        invalidate()
    }
    
    /**
     * Update device information
     */
    fun updateDevice(name: String, type: String, passthrough: Boolean = false) {
        deviceName = name
        deviceType = type
        hdmiPassthrough = passthrough
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        
        // Draw background
        val bgRect = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(bgRect, 16f, 16f, backgroundPaint)
        canvas.drawRoundRect(bgRect, 16f, 16f, borderPaint)
        
        val padding = 24f
        var y = padding
        
        // Title
        textPaint.textSize = 32f
        canvas.drawText("EFFECT CHAIN STATUS", padding, y + textPaint.textSize, textPaint)
        y += textPaint.textSize + 20f
        
        // Device info
        labelPaint.textSize = 22f
        val deviceLabel = if (hdmiPassthrough) {
            "$deviceName ($deviceType) ⚠ HDMI Passthrough"
        } else {
            "$deviceName ($deviceType)"
        }
        canvas.drawText(deviceLabel, padding, y + labelPaint.textSize, labelPaint)
        y += labelPaint.textSize + 24f
        
        // Content type
        val currentStatus = status
        if (currentStatus != null) {
            textPaint.textSize = 28f
            canvas.drawText("Content: ${currentStatus.contentType.title}", padding, y + textPaint.textSize, textPaint)
            y += textPaint.textSize + 20f
            
            // Effect badges
            y = drawEffectBadges(canvas, padding, y, currentStatus)
            y += 16f
            
            // Night mode indicator
            if (currentStatus.nightMode) {
                badgePaint.color = activeColor
                canvas.drawRoundRect(RectF(padding, y, padding + 120f, y + 32f), 8f, 8f, badgePaint)
                badgeTextPaint.textSize = 18f
                canvas.drawText("NIGHT MODE", padding + 60f, y + 22f, badgeTextPaint)
                y += 40f
            }
        } else {
            textPaint.textSize = 28f
            canvas.drawText("No active effect chain", padding, y + textPaint.textSize, textPaint)
        }
    }
    
    private fun drawEffectBadges(canvas: Canvas, x: Float, y: Float, status: EffectChainStatus): Float {
        var currentX = x
        val badgeHeight = 36f
        val badgePadding = 12f
        val spacing = 8f
        
        badgeTextPaint.textSize = 20f
        
        // EQ Badge
        val eqText = "EQ"
        val eqWidth = badgeTextPaint.measureText(eqText) + badgePadding * 2
        badgePaint.color = if (status.equalizerActive) activeColor else inactiveColor
        canvas.drawRoundRect(RectF(currentX, y, currentX + eqWidth, y + badgeHeight), 8f, 8f, badgePaint)
        canvas.drawText(eqText, currentX + eqWidth / 2, y + badgeHeight - 10f, badgeTextPaint)
        currentX += eqWidth + spacing
        
        // Bass Boost Badge
        if (status.bassBoostActive) {
            val bassText = "BASS ${status.bassBoostStrength}%"
            val bassWidth = badgeTextPaint.measureText(bassText) + badgePadding * 2
            badgePaint.color = activeColor
            canvas.drawRoundRect(RectF(currentX, y, currentX + bassWidth, y + badgeHeight), 8f, 8f, badgePaint)
            canvas.drawText(bassText, currentX + bassWidth / 2, y + badgeHeight - 10f, badgeTextPaint)
            currentX += bassWidth + spacing
        }
        
        // Loudness Enhancer Badge
        if (status.loudnessEnhancerActive) {
            val loudText = "LOUD +${status.loudnessGainMb}mB"
            val loudWidth = badgeTextPaint.measureText(loudText) + badgePadding * 2
            badgePaint.color = activeColor
            canvas.drawRoundRect(RectF(currentX, y, currentX + loudWidth, y + badgeHeight), 8f, 8f, badgePaint)
            canvas.drawText(loudText, currentX + loudWidth / 2, y + badgeHeight - 10f, badgeTextPaint)
            currentX += loudWidth + spacing
        }
        
        // Dynamics Processing Badge
        if (status.dynamicsProcessingActive) {
            val dynText = "DYN"
            val dynWidth = badgeTextPaint.measureText(dynText) + badgePadding * 2
            badgePaint.color = activeColor
            canvas.drawRoundRect(RectF(currentX, y, currentX + dynWidth, y + badgeHeight), 8f, 8f, badgePaint)
            canvas.drawText(dynText, currentX + dynWidth / 2, y + badgeHeight - 10f, badgeTextPaint)
            currentX += dynWidth + spacing
        }
        
        return y + badgeHeight
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 600
        val desiredHeight = 280
        
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        
        setMeasuredDimension(width, height)
    }
}
