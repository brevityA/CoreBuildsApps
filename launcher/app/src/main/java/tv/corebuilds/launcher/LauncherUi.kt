package tv.corebuilds.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal fun Activity.enterImmersiveMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.apply {
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsets.Type.systemBars())
        }
    } else {
        window.decorView.enterLegacyImmersiveMode()
    }
}

@Suppress("DEPRECATION")
private fun View.enterLegacyImmersiveMode() {
    systemUiVisibility = (
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
}

internal fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
internal fun Context.dp(value: Int): Int = dp(value.toFloat())

internal fun Drawable.copyForDisplay(): Drawable = constantState?.newDrawable()?.mutate() ?: this

internal fun Context.label(
    text: CharSequence = "",
    sizeSp: Float = 14f,
    color: Int = getColor(R.color.cb_text),
    bold: Boolean = false,
    mono: Boolean = false,
): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    setTextColor(color)
    typeface = Typeface.create(
        if (mono) "monospace" else "sans-serif",
        if (bold) Typeface.BOLD else Typeface.NORMAL,
    )
    includeFontPadding = false
}

internal fun Context.actionButton(
    title: String,
    onClick: () -> Unit,
): TextView = label(
    text = title,
    sizeSp = 11f,
    color = getColor(R.color.cb_text),
    bold = true,
    mono = true,
).apply {
    gravity = Gravity.CENTER
    minHeight = dp(46)
    setPadding(dp(16), dp(4), dp(16), dp(4))
    isFocusable = true
    isFocusableInTouchMode = true
    isClickable = true
    background = focusBackground(13f)
    setOnFocusChangeListener { view, focused ->
        view.animate().scaleX(if (focused) 1.045f else 1f)
            .scaleY(if (focused) 1.045f else 1f)
            .setDuration(120)
            .start()
    }
    setOnClickListener { onClick() }
}

internal fun Context.focusBackground(radiusDp: Float = 16f): StateListDrawable {
    fun shape(fill: Int, border: Int, strokeDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(getColor(fill))
        setStroke(dp(strokeDp), getColor(border))
    }

    return StateListDrawable().apply {
        // Focus is an outline, not a flood of colour: match the icon pack's
        // transparent monoline marks on Night cards.
        addState(
            intArrayOf(android.R.attr.state_focused),
            shape(R.color.cb_night, R.color.cb_cyan, 2),
        )
        addState(
            intArrayOf(android.R.attr.state_pressed),
            shape(R.color.cb_panel, R.color.cb_cyan_soft, 1),
        )
        addState(
            intArrayOf(),
            shape(R.color.cb_night, R.color.cb_line, 1),
        )
    }
}

internal fun Context.heroBackground(): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = dp(18).toFloat()
    setColor(getColor(R.color.cb_night))
    setStroke(dp(1), getColor(R.color.cb_line))
}

/** Flat Void canvas keeps Core Builds linework and package artwork in focus. */
internal class AmbientBackdropView(context: Context) : View(context) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setBackgroundColor(context.getColor(R.color.cb_background))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(context.getColor(R.color.cb_background))
    }
}

internal class AppTileView(
    context: Context,
    val app: LaunchableApp,
    onFocused: (LaunchableApp) -> Unit,
    private val wideCard: Boolean = false,
) : LinearLayout(context) {
    init {
        // Favourites use the icon-pack banner grammar: mark, category, name.
        // The mark itself always comes from the installed package unchanged.
        orientation = if (wideCard) HORIZONTAL else VERTICAL
        gravity = if (wideCard) Gravity.CENTER_VERTICAL else Gravity.CENTER
        if (wideCard) {
            setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        } else {
            setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        }
        background = context.focusBackground(if (wideCard) 13f else 15f)
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        minimumHeight = context.dp(if (wideCard) 112 else 118)
        tag = app.packageName
        contentDescription = context.getString(
            if (app.tvOptimized) R.string.open_tv_app_description else R.string.open_app_description,
            app.label,
        )
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES

        val iconPlate = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(if (wideCard) 10 else 12).toFloat()
                setColor(context.getColor(R.color.cb_night))
                setStroke(context.dp(1), context.getColor(R.color.cb_line))
            }
            layoutParams = LayoutParams(context.dp(56), context.dp(56)).apply {
                if (wideCard) marginEnd = context.dp(12) else bottomMargin = context.dp(8)
            }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val icon = ImageView(context).apply {
            setImageDrawable(app.icon.copyForDisplay())
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(context.dp(42), context.dp(42), Gravity.CENTER)
        }
        iconPlate.addView(icon)
        addView(iconPlate)

        if (wideCard) {
            val textColumn = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            textColumn.addView(context.label(
                context.getString(if (app.tvOptimized) R.string.tv_app_label else R.string.app_label),
                sizeSp = 9f,
                color = context.getColor(R.color.cb_cyan),
                bold = true,
                mono = true,
            ))
            textColumn.addView(context.label(
                app.label,
                sizeSp = 14f,
                color = context.getColor(R.color.cb_text),
                bold = true,
            ).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = context.dp(4)
                }
            })
            addView(textColumn)
        } else {
            addView(context.label(
                app.label,
                sizeSp = 12.5f,
                color = context.getColor(R.color.cb_text),
                bold = true,
            ).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            })
        }

        setOnFocusChangeListener { view, focused ->
            view.animate().scaleX(if (focused) 1.055f else 1f)
                .scaleY(if (focused) 1.055f else 1f)
                .setDuration(125)
                .start()
            if (focused) onFocused(app)
        }
    }
}

internal class PinnedAppRowView(
    context: Context,
    val app: LaunchableApp,
    initiallyPinned: Boolean,
    onFocused: (LaunchableApp) -> Unit,
    onToggle: () -> Unit,
) : LinearLayout(context) {
    private val action = context.label(
        sizeSp = 10f,
        color = context.getColor(R.color.cb_cyan),
        bold = true,
        mono = true,
    )
    private var pinned = initiallyPinned

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
        background = context.focusBackground(15f)
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        minimumHeight = context.dp(78)
        tag = app.packageName

        val icon = ImageView(context).apply {
            setImageDrawable(app.icon.copyForDisplay())
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LayoutParams(context.dp(48), context.dp(48)).apply {
                marginEnd = context.dp(14)
            }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(icon)

        val textColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        textColumn.addView(context.label(
            app.label,
            sizeSp = 16f,
            color = context.getColor(R.color.cb_text),
            bold = true,
        ))
        textColumn.addView(context.label(
            context.getString(if (app.tvOptimized) R.string.tv_app_label else R.string.app_label),
            sizeSp = 10f,
            color = context.getColor(R.color.cb_muted),
            mono = true,
        ).apply {
            setPadding(0, context.dp(4), 0, 0)
        })
        addView(textColumn)
        addView(action.apply {
            gravity = Gravity.CENTER
            setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(10).toFloat()
                setColor(context.getColor(R.color.cb_panel))
            }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        updatePinned(initiallyPinned)

        setOnClickListener { onToggle() }
        setOnFocusChangeListener { view, focused ->
            view.animate().scaleX(if (focused) 1.025f else 1f)
                .scaleY(if (focused) 1.025f else 1f)
                .setDuration(120)
                .start()
            if (focused) onFocused(app)
        }
    }

    fun updatePinned(value: Boolean) {
        pinned = value
        action.text = context.getString(
            if (pinned) R.string.pin_action_remove else R.string.pin_action_add,
        )
        contentDescription = context.getString(
            if (pinned) R.string.pin_accessibility_pinned else R.string.pin_accessibility_unpinned,
            app.label,
        )
    }
}
