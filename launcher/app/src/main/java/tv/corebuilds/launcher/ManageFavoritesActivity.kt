package tv.corebuilds.launcher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** Local pin editor. Press OK on a row to add/remove it from the home shelf. */
class ManageFavoritesActivity : Activity() {
    private val appLoader = Executors.newSingleThreadExecutor()
    private lateinit var rows: LinearLayout
    private lateinit var summary: TextView
    private var apps = emptyList<LaunchableApp>()
    private var requestedFirstFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        setContentView(createScreen())
        loadApps()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        appLoader.shutdownNow()
        super.onDestroy()
    }

    private fun createScreen(): View {
        val frame = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.cb_background)) }
        frame.addView(
            AmbientBackdropView(this),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            descendantFocusability = 0x40000 // after descendants
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(54), dp(30), dp(54), dp(46))
        }
        scroll.addView(content, ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT))
        frame.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(ImageView(this).apply {
            setImageResource(R.drawable.core_builds_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            marginEnd = dp(10)
        })
        top.addView(label("CORE BUILDS  /  PERSONALISE", 12f, getColor(R.color.cb_cyan), bold = true, mono = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(actionButton("DONE") { finish() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)))
        content.addView(top)

        content.addView(label("Choose your favourites.", 28f, getColor(R.color.cb_text), bold = true).apply {
            setPadding(0, dp(24), 0, dp(7))
        })
        content.addView(label(
            "Your favourites appear in the first row. Press OK to add or remove an app.",
            13f,
            getColor(R.color.cb_muted),
        ))
        summary = label("LOADING APPS…", 10f, getColor(R.color.cb_cyan), bold = true, mono = true).apply {
            setPadding(0, dp(18), 0, dp(12))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(summary)

        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(rows)
        content.addView(label("YOUR CHOICES STAY ON THIS DEVICE.", 10f, getColor(R.color.cb_muted), mono = true).apply {
            setPadding(0, dp(20), 0, 0)
        })
        return frame
    }

    private fun loadApps() {
        appLoader.execute {
            val found = LauncherAppCatalog.load(applicationContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                apps = found
                renderRows()
            }
        }
    }

    private fun renderRows() {
        rows.removeAllViews()
        val installed = apps.mapTo(linkedSetOf()) { it.packageName }
        val pins = LauncherPreferences.pinnedPackages(this, installed)
        summary.text = "${pins.size} FAVOURITES  ·  ${apps.size} APPS FOUND"

        if (apps.isEmpty()) {
            rows.addView(label("No apps found yet. Install an app, then come back.", 15f, getColor(R.color.cb_text)).apply {
                setPadding(0, dp(12), 0, dp(20))
            })
            return
        }

        apps.forEachIndexed { index, app ->
            val row = PinnedAppRowView(
                context = this,
                app = app,
                initiallyPinned = app.packageName in pins,
                onFocused = {},
                onToggle = {
                    val nowPinned = LauncherPreferences.togglePinned(this, app.packageName, installed)
                    rowUpdate(app.packageName, nowPinned)
                },
            )
            row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76)).apply {
                bottomMargin = dp(7)
            }
            row.tag = app.packageName
            row.contentDescription = if (app.packageName in pins) {
                "${app.label}. In favourites. Select to remove it."
            } else {
                "${app.label}. Not in favourites. Select to add it."
            }
            rows.addView(row)
            if (index == 0) {
                row.post {
                    if (!requestedFirstFocus && currentFocus == null) {
                        requestedFirstFocus = row.requestFocus()
                    }
                }
            }
        }
    }

    private fun rowUpdate(packageName: String, nowPinned: Boolean) {
        val row = (0 until rows.childCount)
            .map { rows.getChildAt(it) }
            .filterIsInstance<PinnedAppRowView>()
            .firstOrNull { it.app.packageName == packageName }
        row?.updatePinned(nowPinned)
        val installed = apps.mapTo(linkedSetOf()) { it.packageName }
        val pinCount = LauncherPreferences.pinnedPackages(this, installed).size
        summary.text = "$pinCount FAVOURITES  ·  ${apps.size} APPS FOUND"
        Toast.makeText(
            this,
            if (nowPinned) "Added to favourites" else "Removed from favourites",
            Toast.LENGTH_SHORT,
        ).show()
    }
}
