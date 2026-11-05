package tv.corebuilds.launcher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Which shelf a card sits on; the info area and the options panel read it. */
internal enum class RowKind { FAVOURITES, RECENT, ALL_APPS }

/**
 * Every focusable card: a rounded 16:9 tile that lifts 1.1x on focus (the TV
 * focus system's card scale) and draws a 3dp ring inside its own bounds, so a
 * parent can never clip the ring off. Containers leave room for the lift.
 */
@SuppressLint("ViewConstructor")
internal open class FocusCard(context: Context, val kind: RowKind) : FrameLayout(context) {
    private val ringWidth = context.dp(3).toFloat()
    private val radius = context.dp(12).toFloat()
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth * 3f
    }
    private val chevron = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()
    private val path = Path()

    /** Move mode: a white ring and arrows on both sides, instead of the cyan focus ring. */
    var moving = false
        set(value) {
            field = value
            invalidate()
        }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        // The home scrolls rows itself so the focused row lands in one place;
        // the scroll views must not jump to reveal the card first.
        revealOnFocusHint = false
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        outlineSpotShadowColor = context.getColor(R.color.cb_cyan)
        outlineAmbientShadowColor = context.getColor(R.color.cb_cyan)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animateFocus(gainFocus, FOCUS_SCALE)
        translationZ = if (gainFocus) context.dp(10).toFloat() else 0f
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        if (!isFocused && !moving) return
        val active = context.getColor(R.color.cb_cyan)
        ring.color = if (moving) Color.WHITE else active
        glow.color = if (moving) Color.argb(42, 255, 255, 255) else Color.argb(45, 0, 212, 255)
        val glowInset = glow.strokeWidth / 2f
        rect.set(glowInset, glowInset, width - glowInset, height - glowInset)
        canvas.drawRoundRect(rect, radius - glowInset, radius - glowInset, glow)
        val inset = ringWidth / 2f
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(rect, radius - inset, radius - inset, ring)
        if (moving) {
            val size = height * 0.14f
            val cy = height / 2f
            val margin = ringWidth * 3
            path.reset()
            path.moveTo(margin, cy)
            path.lineTo(margin + size, cy - size)
            path.lineTo(margin + size, cy + size)
            path.close()
            path.moveTo(width - margin, cy)
            path.lineTo(width - margin - size, cy - size)
            path.lineTo(width - margin - size, cy + size)
            path.close()
            canvas.drawPath(path, chevron)
        }
    }

    protected fun roundedBackground(color: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }

    companion object {
        const val FOCUS_SCALE = 1.1f
    }
}

/** An app's card: its banner filling the card, or its icon and name on a plate tinted from the icon. */
@SuppressLint("ViewConstructor")
internal class AppCard(
    context: Context,
    val app: LaunchableApp,
    kind: RowKind,
) : FocusCard(context, kind) {
    init {
        tag = app.packageName
        val art = app.art
        if (art.banner != null) {
            background = roundedBackground(context.getColor(R.color.cb_night))
            addView(ImageView(context).apply {
                setImageBitmap(art.banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    CardArtFactory.mix(art.tint, CardArtFactory.NIGHT, 0.55f),
                    CardArtFactory.mix(art.tint, CardArtFactory.VOID, 0.8f),
                ),
            ).apply { cornerRadius = context.dp(12).toFloat() }
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
            }
            column.addView(ImageView(context).apply {
                setImageBitmap(art.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(art.icon?.width ?: 0, art.icon?.height ?: 0))
            column.addView(context.text(app.label, 12f, context.getColor(R.color.cb_text), weight = 500).apply {
                gravity = Gravity.CENTER
                tag = CARD_LABEL
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(6)
            })
            addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        describe()
    }

    fun describe() {
        contentDescription = context.getString(
            when {
                moving -> R.string.card_description_moving
                app.tvOptimized -> R.string.card_description_tv
                else -> R.string.card_description
            },
            app.label,
        )
    }

    companion object {
        /** Marks the name on an icon card; a long app name may end in an ellipsis there, never elsewhere. */
        const val CARD_LABEL = "card_label"
    }
}

/** The last card in Favourites: opens the favourites picker. */
@SuppressLint("ViewConstructor")
internal class AddCard(context: Context) : FocusCard(context, RowKind.FAVOURITES) {
    init {
        background = GradientDrawable().apply {
            cornerRadius = context.dp(12).toFloat()
            setColor(0x660D1117)
            setStroke(context.dp(2), context.getColor(R.color.cb_line), context.dp(8).toFloat(), context.dp(6).toFloat())
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        column.addView(context.text("+", 30f, context.getColor(R.color.cb_cyan), weight = 400).apply { gravity = Gravity.CENTER })
        column.addView(context.text(context.getString(R.string.add_card), 13f, context.getColor(R.color.cb_text), weight = 500).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(2)
        })
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        contentDescription = context.getString(R.string.add_card_description)
    }
}

/** The pill in the top bar: an icon and a word, a ring on focus. */
internal fun Context.pillButton(iconRes: Int, label: String, description: String): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(8), dp(18), dp(8))
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        contentDescription = description
        background = StateListDrawable().apply {
            fun pill(fill: Int, stroke: Int, width: Int) = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(fill)
                setStroke(dp(width), stroke)
            }
            addState(intArrayOf(android.R.attr.state_focused), pill(getColor(R.color.cb_night), getColor(R.color.cb_cyan), 3))
            addState(intArrayOf(), pill(0x990D1117.toInt(), getColor(R.color.cb_line), 1))
        }
        addView(ImageView(context).apply {
            setImageResource(iconRes)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
        addView(text(label, 15f, getColor(R.color.cb_text), weight = 500))
        setOnFocusChangeListener { view, focused -> view.animateFocus(focused, 1.06f) }
    }

/**
 * The full-screen background. In [Backdrop.FOCUSED_APP] it shows the focused
 * app's art (its softened banner, or a glow in its icon's colour) and
 * cross-fades when focus moves; the brand mode draws Void with a cyan and a
 * violet glow; Void is flat. Two scrims keep the info text and the shelves
 * readable on any art: one from the left, one from the bottom.
 */
internal class BackdropView(context: Context) : View(context) {
    /** One background: softened art, or a glow in the icon's colour built once at this view's size. */
    private class Layer(val bitmap: Bitmap?, val tint: Int) {
        var glow: Shader? = null
        var glowWidth = 0
    }

    private var current: Layer? = null
    private var previous: Layer? = null
    private var fade = 1f
    private var animator: ValueAnimator? = null
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()
    private val dst = RectF()

    // Built when the size is known, never while drawing.
    private var leftScrim: Shader? = null
    private var bottomScrim: Shader? = null
    private var cyanGlow: Shader? = null
    private var violetGlow: Shader? = null

    var mode: Backdrop = Backdrop.FOCUSED_APP
        set(value) {
            field = value
            invalidate()
        }

    var dim: Dim = Dim.MEDIUM
        set(value) {
            field = value
            invalidate()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val void = CardArtFactory.VOID
        leftScrim = LinearGradient(0f, 0f, w * 0.72f, 0f, withAlpha(void, 215), withAlpha(void, 0), Shader.TileMode.CLAMP)
        bottomScrim = LinearGradient(0f, h.toFloat(), 0f, h * 0.38f, withAlpha(void, 240), withAlpha(void, 0), Shader.TileMode.CLAMP)
        cyanGlow = radial(w * 0.82f, h * 0.12f, w * 0.62f, context.getColor(R.color.cb_cyan), 70)
        violetGlow = radial(w * 0.12f, h * 0.95f, w * 0.55f, context.getColor(R.color.cb_violet), 90)
    }

    /** Fades to [art]'s backdrop; the same art again does nothing. */
    fun show(art: CardArt?) {
        val now = current
        val bitmap = art?.backdrop
        val tint = art?.tint ?: CardArtFactory.NIGHT
        if (now != null && now.bitmap === bitmap && now.tint == tint) return
        val next = Layer(bitmap, tint)
        if (bitmap == null && width > 0) {
            next.glow = radial(width * 0.78f, height * 0.22f, width * 0.75f, tint, 150)
            next.glowWidth = width
        }
        val duration = Motion.backdropMs(context)
        previous = if (duration == 0L) null else now
        current = next
        animator?.cancel()
        if (duration == 0L) {
            fade = 1f
            animator = null
            invalidate()
            return
        }
        fade = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener {
                fade = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Ends a crossfade immediately; called when Home pauses or the screen is replaced. */
    fun finishFade() {
        animator?.cancel()
        animator = null
        previous = null
        fade = 1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(CardArtFactory.VOID)
        when (mode) {
            Backdrop.FOCUSED_APP -> {
                previous?.let { drawLayer(canvas, it, 255) }
                current?.let { drawLayer(canvas, it, (fade * 255).toInt()) }
                canvas.drawColor(withAlpha(Color.BLACK, dim.scrimAlpha))
            }
            Backdrop.CORE_BUILDS -> {
                fill(canvas, cyanGlow, 255)
                fill(canvas, violetGlow, 255)
                canvas.drawColor(withAlpha(Color.BLACK, dim.scrimAlpha / 2))
            }
            Backdrop.VOID -> Unit
        }
        // Left scrim for the info text, bottom scrim for the shelves.
        fill(canvas, leftScrim, 255)
        fill(canvas, bottomScrim, 255)
    }

    private fun drawLayer(canvas: Canvas, layer: Layer, alpha: Int) {
        val bitmap = layer.bitmap
        if (bitmap != null) {
            // Centre-crop the 16:9 art to the screen; filtering the upscale is the blur.
            val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            src.set(0, 0, bitmap.width, bitmap.height)
            dst.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
            bitmapPaint.alpha = alpha
            canvas.drawBitmap(bitmap, src, dst, bitmapPaint)
        } else if (layer.glowWidth == width) {
            fill(canvas, layer.glow, alpha)
        }
    }

    private fun fill(canvas: Canvas, shader: Shader?, alpha: Int) {
        shader ?: return
        shaderPaint.shader = shader
        shaderPaint.alpha = alpha
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shaderPaint)
        shaderPaint.shader = null
        shaderPaint.alpha = 255
    }

    private fun radial(cx: Float, cy: Float, radius: Float, color: Int, alpha: Int): Shader? =
        if (radius <= 0f) null else RadialGradient(cx, cy, radius, withAlpha(color, alpha), withAlpha(color, 0), Shader.TileMode.CLAMP)

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}

/** The title above a shelf: Outfit SemiBold, quiet, aligned with the cards. */
internal fun Context.shelfTitle(value: String): TextView =
    text(value, 16f, 0xFFC9D1D9.toInt(), weight = 600, tracking = 0.01f)

/** A one-line explanation under a shelf title; short enough for TV distance. */
internal fun Context.shelfNote(value: String): TextView =
    text(value, 11f, 0xFF8B949E.toInt(), weight = 400, tracking = 0.01f)
