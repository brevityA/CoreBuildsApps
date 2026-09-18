package tv.corebuilds.iconpack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Draws a QR matrix from [QrCode].
 *
 * Three things here are about scanning rather than looks, and all three are
 * easy to undo by accident:
 *
 * * **It is always dark-on-light, whatever the app's theme.** The rest of the
 *   pack is night chrome, and a QR inverted on a dark card is unreadable to
 *   most phone cameras. The white here is the quiet zone and the paper.
 * * **The module size is a whole number of pixels.** Drawing at a fractional
 *   size leaves each module a slightly different width once rounded, which is
 *   exactly the sampling error a detector cannot recover from.
 * * **The quiet zone is four modules and is part of the symbol.** Without it a
 *   camera cannot find the finder patterns against the page.
 */
class QrView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    private var matrix: Array<BooleanArray>? = null

    /** Null hides the view; the caller shows the URL as text instead. */
    fun setContent(text: String?) {
        matrix = text?.let { QrCode.encode(it) }
        visibility = if (matrix == null) GONE else VISIBLE
        contentDescription = context.getString(R.string.request_qr_description)
        invalidate()
    }

    /** True when the last [setContent] produced a symbol. */
    fun hasSymbol(): Boolean = matrix != null

    override fun onDraw(canvas: Canvas) {
        val grid = matrix ?: return
        val modules = grid.size + QUIET * 2
        val size = minOf(width, height)
        val scale = size / modules
        if (scale < 1) {
            // Too small to draw a module per pixel. Anything drawn here would
            // be a decorative smudge that never scans, so draw nothing.
            return
        }
        val side = scale * modules
        val left = (width - side) / 2
        val top = (height - side) / 2
        canvas.drawRect(
            left.toFloat(), top.toFloat(),
            (left + side).toFloat(), (top + side).toFloat(), light
        )
        for (r in grid.indices) {
            val row = grid[r]
            for (c in row.indices) {
                if (!row[c]) continue
                val x = left + (c + QUIET) * scale
                val y = top + (r + QUIET) * scale
                canvas.drawRect(
                    x.toFloat(), y.toFloat(),
                    (x + scale).toFloat(), (y + scale).toFloat(), dark
                )
            }
        }
    }

    private companion object {
        /** Modules of clear margin the specification requires around a symbol. */
        const val QUIET = 4
    }
}
