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
    private var pinnedPackages = emptySet<String>()
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
        // Finish any queued local pin update even if the user exits immediately.
        appLoader.shutdown()
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
        top.addView(label(getString(R.string.manage_header), 12f, getColor(R.color.cb_cyan), bold = true, mono = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(actionButton(getString(R.string.done)) { finish() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)))
        content.addView(top)

        content.addView(label(getString(R.string.manage_title), 28f, getColor(R.color.cb_text), bold = true).apply {
            setPadding(0, dp(24), 0, dp(7))
        })
        content.addView(label(
            getString(R.string.manage_description),
            13f,
            getColor(R.color.cb_muted),
        ))
        summary = label(getString(R.string.loading_apps), 10f, getColor(R.color.cb_cyan), bold = true, mono = true).apply {
            setPadding(0, dp(18), 0, dp(12))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(summary)

        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(rows)
        content.addView(label(getString(R.string.local_choices_disclosure), 10f, getColor(R.color.cb_muted), mono = true).apply {
            setPadding(0, dp(20), 0, 0)
        })
        return frame
    }

    private fun loadApps() {
        appLoader.execute {
            val found = LauncherAppCatalog.load(applicationContext)
            val installed = found.mapTo(linkedSetOf()) { it.packageName }
            val foundPins = LauncherPreferences.pinnedPackages(applicationContext, installed)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                apps = found
                pinnedPackages = foundPins
                renderRows()
            }
        }
    }

    private fun renderRows() {
        rows.removeAllViews()
        val installed = apps.mapTo(linkedSetOf()) { it.packageName }
        summary.text = getString(R.string.manage_summary, pinnedPackages.size, apps.size)

        if (apps.isEmpty()) {
            rows.addView(label(getString(R.string.manage_empty), 15f, getColor(R.color.cb_text)).apply {
                setPadding(0, dp(12), 0, dp(20))
            })
            return
        }

        apps.forEachIndexed { index, app ->
            val row = PinnedAppRowView(
                context = this,
                app = app,
                initiallyPinned = app.packageName in pinnedPackages,
                onFocused = {},
                onToggle = { togglePin(app.packageName, installed) },
            )
            row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76)).apply {
                bottomMargin = dp(7)
            }
            row.tag = app.packageName
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

    private fun togglePin(packageName: String, installed: Set<String>) {
        appLoader.execute {
            val nowPinned = LauncherPreferences.togglePinned(applicationContext, packageName, installed)
            val updatedPins = LauncherPreferences.pinnedPackages(applicationContext, installed)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                pinnedPackages = updatedPins
                rowUpdate(packageName, nowPinned)
            }
        }
    }

    private fun rowUpdate(packageName: String, nowPinned: Boolean) {
        val row = (0 until rows.childCount)
            .map { rows.getChildAt(it) }
            .filterIsInstance<PinnedAppRowView>()
            .firstOrNull { it.app.packageName == packageName }
        row?.updatePinned(nowPinned)
        summary.text = getString(R.string.manage_summary, pinnedPackages.size, apps.size)
        Toast.makeText(
            this,
            getString(if (nowPinned) R.string.favourite_added else R.string.favourite_removed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
