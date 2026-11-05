package tv.corebuilds.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** One row in a panel. A [value] shows on the right; [selected] marks the current choice. */
internal data class PanelItem(
    val title: String,
    val meta: String? = null,
    val value: String? = null,
    val selected: Boolean = false,
    val art: CardArt? = null,
    val onSelect: () -> Unit,
)

internal data class PanelPage(
    val eyebrow: String,
    val title: String,
    val body: String? = null,
    val items: List<PanelItem>,
    val empty: String? = null,
    val footer: String? = null,
)

/**
 * The right-hand sheet every menu uses: app options, Customise and its lists.
 * Pages stack (Back returns to the previous page, then closes) and are kept as
 * builders, so a page re-reads the current state whenever it is refreshed.
 * While it is open the home behind it cannot take focus, and Up/Down walk its
 * rows and stop at the ends.
 */
internal class SidePanel(context: Context) : FrameLayout(context) {
    private val sheet = LinearLayout(context)
    private val eyebrow: TextView
    private val title: TextView
    private val body: TextView
    private val list = LinearLayout(context)
    private val scroller = ScrollView(context)
    private val footer: TextView
    private val stack = ArrayList<() -> PanelPage>()
    private val rows = ArrayList<View>()

    /** Where focus goes when the panel closes; asked at close time, so it survives a re-render. */
    var returnFocus: (() -> View?)? = null
    var onClosed: (() -> Unit)? = null

    val isOpen: Boolean get() = visibility == VISIBLE
    val focusedIndex: Int get() = rows.indexOfFirst { it.isFocused }
    val rowCount: Int get() = rows.size

    init {
        visibility = GONE
        isClickable = true // swallow touches meant for the home behind
        addView(View(context).apply { setBackgroundColor(context.getColor(R.color.cb_scrim)) },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        sheet.orientation = LinearLayout.VERTICAL
        sheet.setPadding(context.dp(32), context.dp(36), context.dp(32), context.dp(27))
        sheet.background = GradientDrawable().apply {
            setColor(context.getColor(R.color.cb_night))
            cornerRadii = floatArrayOf(
                context.dp(24).toFloat(), context.dp(24).toFloat(),
                0f, 0f, 0f, 0f,
                context.dp(24).toFloat(), context.dp(24).toFloat(),
            )
        }
        eyebrow = context.text("", 12f, context.getColor(R.color.cb_cyan), weight = 600, tracking = 0.12f)
        title = context.text("", 26f, context.getColor(R.color.cb_text), weight = 600, maxLines = 2)
        body = context.text("", 14f, context.getColor(R.color.cb_muted), maxLines = 5).apply {
            setLineSpacing(context.dp(3).toFloat(), 1f)
        }
        sheet.addView(eyebrow)
        sheet.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(8)
        })
        sheet.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(10)
        })

        list.orientation = LinearLayout.VERTICAL
        scroller.isVerticalScrollBarEnabled = false
        scroller.overScrollMode = OVER_SCROLL_NEVER
        scroller.isVerticalFadingEdgeEnabled = true
        scroller.setFadingEdgeLength(context.dp(24))
        scroller.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        sheet.addView(scroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = context.dp(18)
        })
        footer = context.text("", 12f, context.getColor(R.color.cb_muted), maxLines = 3)
        sheet.addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(14)
        })
        addView(sheet, LayoutParams(context.dp(SHEET_WIDTH_DP), LayoutParams.MATCH_PARENT, Gravity.END))
    }

    /** Opens on [page], replacing whatever was open. */
    fun open(page: () -> PanelPage) {
        stack.clear()
        stack += page
        val wasOpen = isOpen
        visibility = VISIBLE
        show(focus = 0)
        if (!wasOpen) {
            Motion.sheetIn(sheet, context.dp(SHEET_WIDTH_DP).toFloat())
        }
    }

    fun push(page: () -> PanelPage) {
        stack += page
        show(focus = 0)
    }

    /** Re-reads the current page, keeping focus on the same row (or the last one, if it shrank). */
    fun refresh() {
        if (!isOpen) return
        show(focus = focusedIndex.coerceAtLeast(0))
    }

    /** Back: the previous page, else close. Always handled while open. */
    fun back(): Boolean {
        if (!isOpen) return false
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            show(focus = 0)
        } else {
            close()
        }
        return true
    }

    fun close() {
        if (!isOpen) return
        stack.clear()
        rows.clear()
        list.removeAllViews()
        visibility = GONE
        // Let the home take focus again before handing it back.
        onClosed?.invoke()
        returnFocus?.invoke()?.requestFocus()
    }

    /** Up and Down walk the rows and stop at the ends; Left and Right do nothing in a panel. */
    fun move(direction: Int) {
        val index = focusedIndex
        val next = when (direction) {
            View.FOCUS_UP -> index - 1
            View.FOCUS_DOWN -> index + 1
            else -> return
        }
        if (index < 0) {
            rows.firstOrNull()?.requestFocus()
            return
        }
        rows.getOrNull(next)?.requestFocus()
    }

    private fun show(focus: Int) {
        val page: PanelPage = stack[stack.lastIndex].invoke()
        eyebrow.text = page.eyebrow
        title.text = page.title
        body.text = page.body.orEmpty()
        body.visibility = if (page.body.isNullOrEmpty()) GONE else VISIBLE
        footer.text = page.footer.orEmpty()
        footer.visibility = if (page.footer.isNullOrEmpty()) GONE else VISIBLE
        rows.clear()
        list.removeAllViews()
        if (page.items.isEmpty() && page.empty != null) {
            list.addView(context.text(page.empty, 15f, context.getColor(R.color.cb_text), maxLines = 4).apply {
                setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
            })
        }
        page.items.forEach { item ->
            val row = row(item)
            list.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(4)
            })
            rows += row
        }
        val target = rows.getOrNull(focus.coerceAtMost(rows.lastIndex)) ?: rows.firstOrNull()
        target?.requestFocus()
    }

    private fun row(item: PanelItem): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(56)
        setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                cornerRadius = context.dp(12).toFloat()
                setColor(context.getColor(R.color.cb_panel))
                setStroke(context.dp(2), context.getColor(R.color.cb_cyan))
            })
            addState(intArrayOf(), GradientDrawable().apply {
                cornerRadius = context.dp(12).toFloat()
                setColor(0x00000000)
            })
        }
        item.art?.let { art -> addView(thumbnail(art), LinearLayout.LayoutParams(context.dp(56), context.dp(32)).apply {
            marginEnd = context.dp(14)
        }) }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(context.text(item.title, 17f, context.getColor(R.color.cb_text), weight = 500, maxLines = 2))
        item.meta?.let {
            column.addView(context.text(it, 13f, context.getColor(R.color.cb_muted), maxLines = 3), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.dp(3) })
        }
        addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        when {
            item.selected -> addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(context.getColor(R.color.cb_cyan))
                }
            }, LinearLayout.LayoutParams(context.dp(10), context.dp(10)).apply { marginStart = context.dp(12) })
            item.value != null -> addView(context.text(item.value, 14f, context.getColor(R.color.cb_cyan), weight = 500), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.dp(12) })
        }
        contentDescription = listOfNotNull(
            item.title,
            item.meta,
            item.value,
            if (item.selected) context.getString(R.string.selected_state) else null,
        ).joinToString(". ")
        setOnFocusChangeListener { view, focused -> Motion.panelFocus(view, focused) }
        setOnClickListener { item.onSelect() }
    }

    private fun thumbnail(art: CardArt): View = FrameLayout(context).apply {
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        background = GradientDrawable().apply {
            cornerRadius = context.dp(6).toFloat()
            setColor(CardArtFactory.mix(art.tint, CardArtFactory.NIGHT, 0.55f))
        }
        val image = ImageView(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (art.banner != null) {
                setImageBitmap(art.banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                setImageBitmap(art.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
            }
        }
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    companion object {
        const val SHEET_WIDTH_DP = 400
    }
}
