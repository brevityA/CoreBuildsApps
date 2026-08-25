package dev.corebuilds.shift

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Lightweight on-device procedural preview for the Core Motion prequels.
 *
 * This is intentionally a preview surface, not the production exporter. It
 * keeps Core Shift visually alive even before the rendered MP4 release is
 * available, and it gives a user an immediate animated result when opening a
 * Series 2/3 seed. Production delivery still uses the higher-fidelity GLSL
 * renderer and H.264 files from the repository root.
 */
class CoreMotionPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var scene = 0
    private var accent = Color.rgb(0, 229, 255)
    private var playbackSpeed = 1f
    private var loopSeconds = 20f
    private var startedNanos = System.nanoTime()
    private var attached = false
    private var tickerRunning = false
    private val frameDelayMs = if (
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
            ?.isLowRamDevice == true
    ) 66L else 33L

    private val ticker = object : Runnable {
        override fun run() {
            if (!attached || !tickerRunning) return
            invalidate()
            postDelayed(this, frameDelayMs)
        }
    }

    fun setScene(scene: Int, accent: Int = Color.rgb(0, 229, 255)) {
        this.scene = scene.coerceIn(0, 15)
        this.accent = accent
        startedNanos = System.nanoTime()
        invalidate()
    }

    /** Change preview playback without recreating the view or activity. */
    fun setPlayback(speed: Float = playbackSpeed, seconds: Float = loopSeconds) {
        playbackSpeed = speed.coerceIn(0.25f, 2.5f)
        loopSeconds = seconds.coerceIn(5f, 60f)
        startedNanos = System.nanoTime()
        invalidate()
    }

    fun playbackSpeed(): Float = playbackSpeed
    fun loopSeconds(): Float = loopSeconds

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        startedNanos = System.nanoTime()
        startTicker()
    }

    override fun onDetachedFromWindow() {
        attached = false
        stopTicker()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) startTicker() else stopTicker()
    }

    private fun startTicker() {
        if (!attached || tickerRunning) return
        tickerRunning = true
        post(ticker)
    }

    private fun stopTicker() {
        tickerRunning = false
        removeCallbacks(ticker)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        // Fill/crop instead of letterboxing. The home-stage is intentionally
        // much wider than 16:9; using min() created the narrow black pillarbox
        // visible in the screenshot. max() gives a clean full-bleed surface.
        val scale = max(width / DESIGN_W, height / DESIGN_H)
        val offsetX = (width - DESIGN_W * scale) / 2f
        val offsetY = (height - DESIGN_H * scale) / 2f
        val elapsed = (System.nanoTime() - startedNanos) / 1_000_000_000f
        val basePhase = (elapsed % loopSeconds) / loopSeconds * TAU
        // Alter the velocity profile without breaking the phase at the loop
        // boundary. This keeps 0.5× and 2× previews smooth and repeatable.
        val phase = basePhase + (playbackSpeed - 1f) * .65f *
            (1f - cos(basePhase.toDouble()).toFloat())

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(4, 7, 15)
        canvas.drawRect(0f, 0f, DESIGN_W, DESIGN_H, paint)

        if (scene < 8) {
            drawKinetic(canvas, phase)
        } else {
            drawHorizon(canvas, phase, scene - 8)
        }
        drawVignette(canvas)
        canvas.restore()
    }

    private fun drawKinetic(canvas: Canvas, phase: Float) {
        val bg = LinearGradient(
            0f, 0f, DESIGN_W, DESIGN_H,
            Color.rgb(4, 7, 15), Color.rgb(13, 17, 23), Shader.TileMode.CLAMP,
        )
        paint.shader = bg
        canvas.drawRect(0f, 0f, DESIGN_W, DESIGN_H, paint)
        paint.shader = null

        when (scene) {
            0 -> drawOrbitals(canvas, phase)
            1 -> drawWarp(canvas, phase)
            2 -> drawFogbanks(canvas, phase)
            3 -> drawSpiral(canvas, phase)
            4 -> drawSlipstream(canvas, phase)
            5 -> drawEmbers(canvas, phase)
            6 -> drawTracer(canvas, phase)
            else -> drawPendulum(canvas, phase)
        }
    }

    private fun drawOrbitals(canvas: Canvas, phase: Float) {
        val cx = DESIGN_W / 2f
        val cy = DESIGN_H / 2f
        glow(canvas, cx, cy, 210f, blend(accent, Color.rgb(79, 172, 254), .35f), .18f)
        for (i in 0 until 7) {
            val radius = 135f + i * 72f
            val tilt = .38f + i * .035f
            rect.set(cx - radius, cy - radius * tilt, cx + radius, cy + radius * tilt)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f + i * .18f
            paint.color = alpha(blend(accent, Color.rgb(138, 72, 144), i / 8f), 45 + i * 10)
            canvas.drawOval(rect, paint)
            val angle = phase * (.34f + i * .065f) + i * 1.91f
            val x = cx + cos(angle.toDouble()).toFloat() * radius
            val y = cy + sin(angle.toDouble()).toFloat() * radius * tilt
            glow(canvas, x, y, 34f, blend(accent, Color.rgb(79, 172, 254), i / 8f), .28f)
            paint.style = Paint.Style.FILL
            paint.color = alpha(blend(accent, Color.WHITE, .18f), 215)
            canvas.drawCircle(x, y, 5f + i * .6f, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = alpha(Color.WHITE, 180)
        canvas.drawCircle(cx, cy, 4f + 2f * (0.5f + 0.5f * sin(phase.toDouble()).toFloat()), paint)
    }

    private fun drawWarp(canvas: Canvas, phase: Float) {
        val cx = DESIGN_W / 2f
        val cy = DESIGN_H / 2f
        glow(canvas, cx, cy, 270f, accent, .16f)
        paint.style = Paint.Style.STROKE
        for (i in 0 until 44) {
            val a = i * TAU / 44f + .18f * sin((phase + i).toDouble()).toFloat()
            val travel = (phase / TAU * (.20f + (i % 6) * .035f) + i / 44f) % 1f
            val near = 18f + travel * 850f
            val far = near + 60f + (i % 5) * 48f
            paint.strokeWidth = 2f + (i % 3)
            paint.color = alpha(blend(accent, Color.rgb(138, 72, 144), i / 44f), 35 + (travel * 150).toInt())
            canvas.drawLine(cx + cos(a.toDouble()).toFloat() * near, cy + sin(a.toDouble()).toFloat() * near,
                cx + cos(a.toDouble()).toFloat() * far, cy + sin(a.toDouble()).toFloat() * far, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawFogbanks(canvas: Canvas, phase: Float) {
        for (i in 0 until 7) {
            val center = 455f + i * 86f + 24f * sin((phase * (.7f + i * .1f) + i).toDouble()).toFloat()
            path.reset()
            path.moveTo(-40f, center)
            for (x in 0..20) {
                val px = x * 100f
                val py = center + 32f * sin((x * .55f + phase * .22f + i).toDouble()).toFloat()
                if (x == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 38f + i * 7f
            paint.color = alpha(blend(accent, Color.rgb(138, 72, 144), i / 8f), 18 + i * 4)
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
        glow(canvas, 1420f, 380f, 270f, accent, .07f)
    }

    private fun drawSpiral(canvas: Canvas, phase: Float) {
        val cx = DESIGN_W / 2f
        val cy = DESIGN_H / 2f
        for (arm in 0 until 3) {
            path.reset()
            for (i in 0..110) {
                val radius = i * 10.5f
                val angle = phase * .42f + arm * TAU / 3f + i * .105f
                val x = cx + cos(angle.toDouble()).toFloat() * radius
                val y = cy + sin(angle.toDouble()).toFloat() * radius * .62f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 7f
            paint.color = alpha(blend(accent, Color.rgb(138, 72, 144), arm / 3f), 75)
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
        glow(canvas, cx, cy, 220f, accent, .15f)
    }

    private fun drawSlipstream(canvas: Canvas, phase: Float) {
        val vanX = DESIGN_W * .52f
        val vanY = DESIGN_H * .49f
        paint.style = Paint.Style.STROKE
        for (i in 0 until 26) {
            val x = i * 82f
            paint.strokeWidth = 2f + i % 3
            paint.color = alpha(blend(accent, Color.rgb(79, 172, 254), i / 26f), 38 + i * 3)
            canvas.drawLine(vanX, vanY, x, DESIGN_H + 60f, paint)
        }
        for (i in 0 until 9) {
            val y = 210f + i * 112f + ((phase / TAU * 190f + i * 70f) % 190f)
            val left = vanX - (y - vanY) * 1.3f
            val right = vanX + (y - vanY) * 1.3f
            paint.strokeWidth = 3f
            paint.color = alpha(accent, 70 + i * 10)
            canvas.drawLine(left, y, right, y, paint)
        }
        paint.style = Paint.Style.FILL
        glow(canvas, vanX, vanY, 150f, accent, .15f)
    }

    private fun drawEmbers(canvas: Canvas, phase: Float) {
        for (i in 0 until 44) {
            val fi = i.toFloat()
            val seed = (i * 37 % 101) / 101f
            val speed = .45f + (i % 7) * .08f
            val y = ((seed + phase / TAU * speed) % 1f) * 1200f - 120f
            val x = (i * 173f % 2100f) - 90f + 65f * sin((phase * (1f + i % 4 * .14f) + fi).toDouble()).toFloat()
            val size = 3f + (i % 5) * 1.7f
            glow(canvas, x, y, 26f + size * 2f, blend(Color.rgb(192, 58, 32), accent, .18f), .22f)
            paint.color = alpha(blend(Color.rgb(192, 58, 32), accent, .2f), 90 + i % 4 * 35)
            canvas.drawCircle(x, y, size, paint)
        }
        paint.color = alpha(Color.rgb(192, 58, 32), 34)
        canvas.drawRect(0f, 820f, DESIGN_W, DESIGN_H, paint)
    }

    private fun drawTracer(canvas: Canvas, phase: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = alpha(accent, 22)
        for (x in 0..12) canvas.drawLine(x * 170f, 250f, x * 170f, DESIGN_H, paint)
        for (y in 2..7) canvas.drawLine(0f, y * 150f, DESIGN_W, y * 150f, paint)
        for (i in 0 until 7) {
            val y = 300f + i * 86f
            val x = -80f + ((phase / TAU * (450f + i * 65f) + i * 220f) % 2100f)
            paint.strokeWidth = 5f
            paint.color = alpha(blend(accent, Color.rgb(79, 172, 254), i / 7f), 120)
            canvas.drawLine(x - 100f, y, x, y, paint)
            canvas.drawLine(x, y, x, y + 54f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawPendulum(canvas: Canvas, phase: Float) {
        for (i in 0 until 5) {
            val pivotX = 330f + i * 330f
            val length = 230f + 35f * sin((i * 2f + .6f).toDouble()).toFloat()
            val angle = .44f * sin((phase * (.55f + i * .05f) + i * 1.2f).toDouble()).toFloat()
            val bobX = pivotX + sin(angle.toDouble()).toFloat() * length
            val bobY = 180f + cos(angle.toDouble()).toFloat() * length
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = alpha(blend(accent, Color.rgb(138, 72, 144), i / 5f), 90)
            canvas.drawLine(pivotX, 180f, bobX, bobY, paint)
            paint.style = Paint.Style.FILL
            glow(canvas, bobX, bobY, 42f, accent, .18f)
            paint.color = alpha(accent, 210)
            canvas.drawCircle(bobX, bobY, 12f, paint)
        }
    }

    private fun drawHorizon(canvas: Canvas, phase: Float, variant: Int) {
        val skyA = when (variant) {
            1 -> Color.rgb(36, 8, 20)
            2 -> Color.rgb(21, 10, 34)
            3 -> Color.rgb(5, 24, 43)
            4 -> Color.rgb(4, 22, 30)
            5 -> Color.rgb(38, 21, 7)
            6 -> Color.rgb(18, 26, 38)
            else -> Color.rgb(3, 8, 19)
        }
        val skyB = Color.rgb(13, 17, 23)
        paint.shader = LinearGradient(0f, 0f, 0f, 760f, skyA, skyB, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, DESIGN_W, DESIGN_H, paint)
        paint.shader = null

        val horizon = 700f + 16f * sin(phase.toDouble()).toFloat()
        paint.color = Color.rgb(4, 7, 15)
        canvas.drawRect(0f, horizon + 8f, DESIGN_W, DESIGN_H, paint)
        glow(canvas, DESIGN_W * .5f, horizon, 130f, accent, .17f)
        paint.color = alpha(accent, 150)
        canvas.drawRect(0f, horizon, DESIGN_W, horizon + 3f, paint)

        when (variant) {
            0 -> drawAurora(canvas, phase)
            1, 5 -> drawSun(canvas, phase, horizon, variant == 1)
            2 -> drawCloudShelf(canvas, phase)
            3 -> drawScanPlane(canvas, phase)
            4 -> drawSignalArcs(canvas, phase)
            6 -> drawMonolith(canvas, phase, horizon)
            else -> drawVoidLine(canvas, phase, horizon)
        }
        drawHorizonBands(canvas, phase, horizon)
    }

    private fun drawAurora(canvas: Canvas, phase: Float) {
        for (i in 0 until 3) {
            path.reset()
            val base = 300f + i * 125f
            for (x in 0..20) {
                val px = x * 100f
                val py = base + 55f * sin((x * .55f + phase * .7f + i).toDouble()).toFloat()
                if (x == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 48f - i * 8f
            paint.color = alpha(blend(accent, Color.rgb(79, 172, 254), i / 3f), 32)
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawSun(canvas: Canvas, phase: Float, horizon: Float, ember: Boolean) {
        val x = if (ember) 1450f else 720f
        val y = horizon - 130f + 22f * sin(phase.toDouble()).toFloat()
        glow(canvas, x, y, 180f, if (ember) Color.rgb(192, 58, 32) else accent, .16f)
        paint.color = alpha(if (ember) Color.rgb(192, 58, 32) else accent, 190)
        canvas.drawCircle(x, y, 64f, paint)
    }

    private fun drawCloudShelf(canvas: Canvas, phase: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 32f
        for (i in 0 until 4) {
            path.reset()
            val base = 265f + i * 80f
            for (x in 0..20) {
                val px = x * 100f
                val py = base + 22f * sin((x * .8f + phase * .45f + i).toDouble()).toFloat()
                if (x == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            paint.color = alpha(Color.rgb(210, 106, 137), 25 + i * 5)
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawScanPlane(canvas: Canvas, phase: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = alpha(accent, 30)
        for (y in 160..650 step 54) canvas.drawLine(0f, y.toFloat(), DESIGN_W, y.toFloat(), paint)
        for (x in 0..1920 step 130) canvas.drawLine(x.toFloat(), 120f, x.toFloat(), 700f, paint)
        val y = 210f + ((phase / TAU * 520f) % 520f)
        paint.strokeWidth = 6f
        paint.color = alpha(accent, 125)
        canvas.drawLine(0f, y, DESIGN_W, y, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawSignalArcs(canvas: Canvas, phase: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        rect.set(280f, 140f, 980f, 840f)
        paint.color = alpha(accent, 120)
        canvas.drawArc(rect, -70f + phase * 8f, 115f, false, paint)
        rect.set(940f, 110f, 1770f, 950f)
        paint.color = alpha(Color.rgb(79, 172, 254), 75)
        canvas.drawArc(rect, 110f - phase * 6f, 130f, false, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawMonolith(canvas: Canvas, phase: Float, horizon: Float) {
        val x = 750f + 24f * sin(phase.toDouble()).toFloat()
        paint.color = alpha(Color.rgb(45, 58, 73), 210)
        path.reset()
        path.moveTo(x, horizon)
        path.lineTo(x + 80f, horizon)
        path.lineTo(x + 68f, 280f)
        path.lineTo(x + 22f, 230f)
        path.lineTo(x - 5f, 280f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawVoidLine(canvas: Canvas, phase: Float, horizon: Float) {
        paint.color = alpha(accent, 215)
        canvas.drawRect(0f, horizon, DESIGN_W, horizon + 4f, paint)
        glow(canvas, 1150f + 120f * sin(phase.toDouble()).toFloat(), horizon, 160f, accent, .1f)
    }

    private fun drawHorizonBands(canvas: Canvas, phase: Float, horizon: Float) {
        paint.style = Paint.Style.STROKE
        for (i in 0 until 4) {
            val y = horizon - 52f - i * 62f + 16f * sin((phase * (.7f + i * .12f) + i).toDouble()).toFloat()
            paint.strokeWidth = 8f - i
            paint.color = alpha(blend(accent, Color.rgb(79, 172, 254), i / 4f), 28 + i * 6)
            canvas.drawLine(0f, y, DESIGN_W, y + 10f * sin(phase.toDouble()).toFloat(), paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, strength: Float) {
        paint.shader = RadialGradient(
            x, y, radius,
            alpha(color, (255f * strength).toInt()),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
    }

    private fun drawVignette(canvas: Canvas) {
        paint.shader = RadialGradient(
            DESIGN_W / 2f, DESIGN_H / 2f, DESIGN_W * .68f,
            Color.TRANSPARENT, Color.argb(145, 0, 0, 0), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, DESIGN_W, DESIGN_H, paint)
        paint.shader = null
    }

    private fun alpha(color: Int, value: Int): Int =
        Color.argb(value.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun blend(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
        )
    }

    private companion object {
        const val DESIGN_W = 1920f
        const val DESIGN_H = 1080f
        const val TAU = 6.2831855f
        const val FRAME_MS = 33L
    }
}
