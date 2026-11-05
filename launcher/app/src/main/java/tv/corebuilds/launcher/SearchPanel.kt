package tv.corebuilds.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Ranking is local: exact, prefix, word, initials, package and subsequence matches. */
internal object AppSearch {
    const val MAX_RESULTS = 8

    fun rank(apps: List<LaunchableApp>, query: String, preferred: Set<String> = emptySet()): List<LaunchableApp> {
        val q = normalize(query)
        if (q.isEmpty()) {
            return apps.sortedWith(
                compareByDescending<LaunchableApp> { it.packageName in preferred }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            ).take(MAX_RESULTS)
        }
        return apps.mapNotNull { app ->
            val label = normalize(app.label)
            val packageName = normalize(app.packageName)
            val words = label.split(' ').filter { it.isNotEmpty() }
            val initials = words.map { it.first() }.joinToString("")
            val base = when {
                label == q -> 120
                label.startsWith(q) -> 100
                words.any { it.startsWith(q) } -> 85
                label.contains(q) -> 65
                initials.startsWith(q) -> 55
                packageName.contains(q) -> 30
                isSubsequence(q, label) -> 20
                else -> return@mapNotNull null
            }
            val boost = (if (app.packageName in preferred) 8 else 0) + if (app.tvOptimized) 2 else 0
            app to (base + boost)
        }
            .sortedWith(compareByDescending<Pair<LaunchableApp, Int>> { it.second }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.label })
            .map { it.first }
            .take(MAX_RESULTS)
    }

    private fun normalize(value: String): String = buildString {
        var pendingSpace = false
        for (char in value.lowercase(Locale.ROOT)) {
            if (char.isLetterOrDigit()) {
                if (pendingSpace && isNotEmpty()) append(' ')
                append(char)
                pendingSpace = false
            } else {
                pendingSpace = true
            }
        }
    }.trim()

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var index = 0
        for (char in haystack) {
            if (index < needle.length && char == needle[index]) index++
            if (index == needle.length) return true
        }
        return false
    }
}

/**
 * A local, TV-first app finder. The input accepts a remote IME or hardware
 * keyboard; D-pad Up/Down walks results, Left/Right never escapes the sheet,
 * and Back is owned by the activity.
 */
@SuppressLint("ViewConstructor")
internal class SearchPanel(context: Context) : FrameLayout(context) {
    private val sheet = LinearLayout(context)
    private val input: EditText
    private val status: TextView
    private val results = LinearLayout(context)
    private val rows = ArrayList<View>()
    private var apps = emptyList<LaunchableApp>()
    private var preferred = emptySet<String>()

    var onLaunch: ((LaunchableApp) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    val isOpen: Boolean get() = visibility == VISIBLE
    val focusedIndex: Int get() = rows.indexOfFirst { it.isFocused }

    init {
        visibility = GONE
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(View(context).apply {
            setBackgroundColor(0xCC000000.toInt())
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        sheet.orientation = LinearLayout.VERTICAL
        sheet.setPadding(context.dp(30), context.dp(34), context.dp(30), context.dp(27))
        sheet.background = GradientDrawable().apply {
            setColor(context.getColor(R.color.cb_night))
            cornerRadii = floatArrayOf(
                context.dp(24).toFloat(), context.dp(24).toFloat(),
                0f, 0f, 0f, 0f,
                context.dp(24).toFloat(), context.dp(24).toFloat(),
            )
        }
        sheet.addView(context.text(context.getString(R.string.search_eyebrow), 12f,
            context.getColor(R.color.cb_cyan), weight = 600, tracking = 0.12f))
        sheet.addView(context.text(context.getString(R.string.search_title), 26f,
            context.getColor(R.color.cb_text), weight = 600),
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(8) })
        sheet.addView(context.text(context.getString(R.string.search_body), 14f,
            context.getColor(R.color.cb_muted), maxLines = 3),
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(9) })

        input = EditText(context).apply {
            hint = context.getString(R.string.search_hint)
            setHintTextColor(context.getColor(R.color.cb_muted))
            setTextColor(context.getColor(R.color.cb_text))
            typeface = Type.outfit(context, 500)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
            includeFontPadding = false
            isSingleLine = true
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
            background = GradientDrawable().apply {
                cornerRadius = context.dp(12).toFloat()
                setColor(context.getColor(R.color.cb_panel))
                setStroke(context.dp(2), context.getColor(R.color.cb_line))
            }
            contentDescription = context.getString(R.string.search_input_description)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        sheet.addView(input, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(20)
        })
        status = context.text("", 12f, context.getColor(R.color.cb_cyan), weight = 600, tracking = 0.07f)
        sheet.addView(status, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(14)
        })

        results.orientation = LinearLayout.VERTICAL
        val scroller = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(context.dp(20))
            addView(results, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        sheet.addView(scroller, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = context.dp(12)
        })
        sheet.addView(context.text(context.getString(R.string.search_footer), 12f,
            context.getColor(R.color.cb_muted), maxLines = 3),
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(14) })

        addView(sheet, LayoutParams(context.dp(SHEET_WIDTH_DP), LayoutParams.MATCH_PARENT, Gravity.END))

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isOpen) render()
            }
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && rows.isNotEmpty()) {
                rows.first().performClick()
                true
            } else false
        }
    }

    fun open(apps: List<LaunchableApp>, preferred: Set<String>) {
        this.apps = apps
        this.preferred = preferred
        visibility = VISIBLE
        input.setText("")
        render()
        Motion.sheetIn(sheet, context.dp(SHEET_WIDTH_DP).toFloat())
        input.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    fun close() {
        if (!isOpen) return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(input.windowToken, 0)
        visibility = GONE
        rows.clear()
        results.removeAllViews()
        onClosed?.invoke()
    }

    /** Up enters the input from the first result; Down enters results. Ends hold. */
    fun move(direction: Int): Boolean {
        when (direction) {
            View.FOCUS_DOWN -> {
                if (input.isFocused) rows.firstOrNull()?.requestFocus()
                else rows.getOrNull(focusedIndex + 1)?.requestFocus()
            }
            View.FOCUS_UP -> {
                val index = focusedIndex
                if (index <= 0) input.requestFocus()
                else rows.getOrNull(index - 1)?.requestFocus()
            }
        }
        return true
    }

    private fun render() {
        val hadResultFocus = rows.any { it.isFocused }
        val query = input.text?.toString().orEmpty()
        val matches = AppSearch.rank(apps, query, preferred)
        rows.clear()
        results.removeAllViews()
        status.text = context.getString(R.string.search_results, matches.size, context.getString(R.string.search_status_local))
        if (matches.isEmpty()) {
            results.addView(context.text(context.getString(R.string.search_empty), 15f,
                context.getColor(R.color.cb_text), maxLines = 3).apply {
                setPadding(context.dp(12), context.dp(16), context.dp(12), context.dp(16))
            })
        }
        matches.forEach { app ->
            val row = resultRow(app)
            rows += row
            results.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = context.dp(5)
            })
        }
        if (hadResultFocus) rows.firstOrNull()?.requestFocus() else input.requestFocus()
    }

    private fun resultRow(app: LaunchableApp): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(58)
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        background = android.graphics.drawable.StateListDrawable().apply {
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
        addView(thumbnail(app.art), LinearLayout.LayoutParams(context.dp(58), context.dp(33)).apply {
            marginEnd = context.dp(13)
        })
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(context.text(app.label, 17f, context.getColor(R.color.cb_text), weight = 500))
        column.addView(context.text(
            context.getString(if (app.tvOptimized) R.string.info_tv_app else R.string.info_app),
            12f, context.getColor(R.color.cb_muted),
        ), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(3) })
        addView(column, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        contentDescription = context.getString(R.string.search_result_description, app.label)
        setOnFocusChangeListener { view, focused -> Motion.panelFocus(view, focused) }
        setOnClickListener { onLaunch?.invoke(app) }
    }

    private fun thumbnail(art: CardArt): View = FrameLayout(context).apply {
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        background = GradientDrawable().apply {
            cornerRadius = context.dp(7).toFloat()
            setColor(CardArtFactory.mix(art.tint, CardArtFactory.NIGHT, 0.55f))
        }
        addView(ImageView(context).apply {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            if (art.banner != null) {
                setImageBitmap(art.banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                setImageBitmap(art.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private companion object {
        const val SHEET_WIDTH_DP = 460
    }
}
