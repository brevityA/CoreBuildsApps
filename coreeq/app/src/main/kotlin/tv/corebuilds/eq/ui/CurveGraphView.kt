package tv.corebuilds.eq.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.R
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

data class Series(
    val label: String,
    val color: Int,
    val values: DoubleArray
)

/**
 * Log-frequency dB response graph matching the mockups and Core Builds visual style.
 */
class CurveGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cb_void)
    }

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.cb_hairline)
    }

    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#1C2333")
    }

    private val zeroPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#30363D")
    }

    private val transitionPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#44D29922")
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = ContextCompat.getColor(context, R.color.cb_slate)
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        isFakeBoldText = true
        color = ContextCompat.getColor(context, R.color.cb_slate)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private var frequencies: DoubleArray = doubleArrayOf()
    private val seriesList = mutableListOf<Series>()
    private var yRangeDb = 20.0
    private var graphTitle = "MEASURED vs CORRECTION"
    private var transitionHz = 384.0

    fun setData(
        freqs: DoubleArray,
        series: List<Series>,
        title: String = "MEASURED vs CORRECTION",
        yRange: Double = 20.0,
        tHz: Double = 384.0
    ) {
        frequencies = freqs
        seriesList.clear()
        seriesList.addAll(series)
        graphTitle = title
        yRangeDb = yRange
        transitionHz = tHz
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padL = 30f
        val padR = 30f
        val padT = 50f
        val padB = 40f

        val plotL = padL
        val plotR = w - padR
        val plotT = padT
        val plotB = h - padB

        // Background and outline
        canvas.drawRect(plotL, plotT, plotR, plotB, bgPaint)
        canvas.drawRect(plotL, plotT, plotR, plotB, borderPaint)

        val fLo = 20.0
        val fHi = 20000.0
        val logLo = log10(fLo)
        val logHi = log10(fHi)

        fun fx(freq: Double): Float {
            val clamped = max(fLo, min(fHi, freq))
            val frac = (log10(clamped) - logLo) / (logHi - logLo)
            return (plotL + frac * (plotR - plotL)).toFloat()
        }

        fun fy(db: Double): Float {
            val clamped = max(-yRangeDb, min(yRangeDb, db))
            val frac = (yRangeDb - clamped) / (2.0 * yRangeDb)
            return (plotT + frac * (plotB - plotT)).toFloat()
        }

        // Draw dB horizontal grid lines
        for (db in listOf(-12.0, -6.0, 0.0, 6.0, 12.0)) {
            val y = fy(db)
            if (db == 0.0) {
                canvas.drawLine(plotL, y, plotR, y, zeroPaint)
            } else {
                canvas.drawLine(plotL, y, plotR, y, gridPaint)
            }
        }

        // Draw frequency vertical grid lines
        val gridFreqs = listOf(
            50.0 to "50",
            100.0 to "100",
            200.0 to "200",
            500.0 to "500",
            1000.0 to "1k",
            2000.0 to "2k",
            5000.0 to "5k",
            10000.0 to "10k"
        )
        for ((gf, label) in gridFreqs) {
            val x = fx(gf)
            canvas.drawLine(x, plotT, x, plotB, gridPaint)
            canvas.drawText(label, x - 15f, plotB + 28f, textPaint)
        }

        // Draw Schroeder transition vertical rule
        if (transitionHz in fLo..fHi) {
            val tx = fx(transitionHz)
            canvas.drawLine(tx, plotT, tx, plotB, transitionPaint)
        }

        // Title
        canvas.drawText(graphTitle, plotL + 20f, plotT - 18f, titlePaint)

        // Draw Series Curves
        if (frequencies.isNotEmpty()) {
            val path = Path()
            for (s in seriesList) {
                linePaint.color = s.color
                path.reset()
                var started = false
                for (i in frequencies.indices) {
                    val f = frequencies[i]
                    if (f in fLo..fHi && i < s.values.size) {
                        val x = fx(f)
                        val y = fy(s.values[i])
                        if (!started) {
                            path.moveTo(x, y)
                            started = true
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                }
                if (started) {
                    canvas.drawPath(path, linePaint)
                }
            }
        }

        // Legend at bottom right
        var lx = plotR - 20f
        val ly = plotB - 16f
        for (s in seriesList.reversed()) {
            val textLen = textPaint.measureText(s.label)
            lx -= textLen
            textPaint.color = s.color
            canvas.drawText(s.label, lx, ly, textPaint)
            lx -= 20f
            linePaint.color = s.color
            canvas.drawLine(lx - 25f, ly - 6f, lx, ly - 6f, linePaint)
            lx -= 40f
        }
        textPaint.color = ContextCompat.getColor(context, R.color.cb_slate)
    }
}
