package tv.corebuilds.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Main screen and HOME entry point for the private Core Builds launcher prototype. */
class LauncherActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appLoader = Executors.newSingleThreadExecutor()
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            mainHandler.postDelayed(this, 30_000L)
        }
    }

    private var apps = emptyList<LaunchableApp>()
    private var renderedAppKeys: List<String>? = null
    private var renderedPins: Set<String>? = null
    private var renderedRecentPackages: List<String>? = null
    private var scanning = false
    private var selectedApp: LaunchableApp? = null
    private var lastFocusedPackage: String? = null
    private var hasRenderedApps = false
    private var userInteracted = false

    private lateinit var pageContent: LinearLayout
    private lateinit var favoritesScroller: HorizontalScrollView
    private lateinit var favoritesRow: LinearLayout
    private lateinit var emptyFavorites: TextView
    private lateinit var recentSection: LinearLayout
    private lateinit var recentScroller: HorizontalScrollView
    private lateinit var recentRow: LinearLayout
    private lateinit var appGrid: GridLayout
    private lateinit var appCount: TextView
    private lateinit var scanStatus: TextView
    private lateinit var heroIcon: ImageView
    private lateinit var heroEyebrow: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSubtitle: TextView
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var manageButton: TextView
    private val appTiles = linkedMapOf<String, AppTileView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        setContentView(createHomeScreen())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // A HOME surface should remain on the launcher instead of exposing a
        // previous task. Back still works normally in child screens/apps.
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) userInteracted = true
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        updateClock()
        mainHandler.removeCallbacks(clockTick)
        mainHandler.postDelayed(clockTick, 30_000L)
        refreshApps()
    }

    override fun onPause() {
        mainHandler.removeCallbacks(clockTick)
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        appLoader.shutdownNow()
        super.onDestroy()
    }

    private fun createHomeScreen(): View {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.cb_background))
        }
        frame.addView(
            AmbientBackdropView(this),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        pageContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(54), dp(28), dp(54), dp(44))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        scroll.addView(pageContent, ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT))
        frame.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        pageContent.addView(createHeader())
        pageContent.addView(createHero(), marginLayout(height = dp(196), top = dp(20), bottom = dp(18)))

        pageContent.addView(sectionHeader("FAVOURITES", "Quick access to the apps you choose."))
        favoritesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(8), dp(5), dp(12), dp(5))
        }
        favoritesScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), 0, dp(4), 0)
            addView(favoritesRow, HorizontalScrollView.LayoutParams(HorizontalScrollView.LayoutParams.WRAP_CONTENT, dp(122)))
        }
        pageContent.addView(favoritesScroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(130)))
        emptyFavorites = actionButton("CHOOSE YOUR FAVOURITES") { openManageFavorites() }.apply {
            contentDescription = "Choose which apps appear in favourites"
        }
        pageContent.addView(emptyFavorites, marginLayout(height = dp(48), bottom = dp(10)))

        recentSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        recentSection.addView(sectionHeader("RECENTLY OPENED", "Fast access · saved on this TV only."))
        recentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(8), dp(5), dp(12), dp(5))
        }
        recentScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), 0, dp(4), 0)
            addView(recentRow, HorizontalScrollView.LayoutParams(HorizontalScrollView.LayoutParams.WRAP_CONTENT, dp(122)))
        }
        recentSection.addView(recentScroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(130)))
        pageContent.addView(recentSection, marginLayout(bottom = dp(8)))

        val allAppsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val allAppsTitleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        allAppsTitleColumn.addView(label("ALL APPS", 17f, getColor(R.color.cb_text), bold = true))
        allAppsTitleColumn.addView(label("Select a tile to launch it.", 11f, getColor(R.color.cb_muted)).apply {
            setPadding(0, dp(4), 0, 0)
        })
        allAppsHeader.addView(allAppsTitleColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        appCount = label("", 11f, getColor(R.color.cb_cyan), bold = true, mono = true)
        allAppsHeader.addView(appCount)
        pageContent.addView(allAppsHeader, marginLayout(top = dp(10), bottom = dp(10)))

        scanStatus = label("SCANNING THIS TV…", 12f, getColor(R.color.cb_muted), mono = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(52)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_LIVE_REGION_POLITE
        }
        pageContent.addView(scanStatus)

        appGrid = GridLayout(this).apply {
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(8))
        }
        pageContent.addView(appGrid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        pageContent.addView(label(
            "PRIVATE  ·  No ads or account; app history stays on this TV.",
            10f,
            getColor(R.color.cb_muted),
            mono = true,
        ).apply {
            setPadding(0, dp(18), 0, 0)
        })

        return frame
    }

    private fun createHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.core_builds_mark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            marginEnd = dp(12)
        })
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("CORE BUILDS", 18f, getColor(R.color.cb_text), bold = true))
        brand.addView(label("YOUR TV  ·  YOUR APPS", 9f, getColor(R.color.cb_cyan), bold = true, mono = true).apply {
            setPadding(0, dp(5), 0, 0)
        })
        row.addView(brand)
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val dateTime = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(20), 0)
        }
        clockText = label("--:--", 17f, getColor(R.color.cb_text), bold = true, mono = true).apply { gravity = Gravity.END }
        dateText = label("", 10f, getColor(R.color.cb_muted), mono = true).apply {
            gravity = Gravity.END
            setPadding(0, dp(4), 0, 0)
        }
        dateTime.addView(clockText)
        dateTime.addView(dateText)
        row.addView(dateTime)

        manageButton = actionButton("EDIT FAVOURITES") { openManageFavorites() }
        row.addView(manageButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)).apply {
            marginStart = dp(8)
        })
        val homeButton = actionButton("SET AS HOME") { openHomeSettings() }
        row.addView(homeButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)).apply {
            marginStart = dp(8)
        })
        return row
    }

    private fun createHero(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = heroBackground()
            setPadding(dp(26), dp(15), dp(30), dp(15))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        // Match the pack's banner lockup: the installed mark/art leads, then
        // the category and readable app name. Never redraw vendor artwork.
        val artwork = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(getColor(R.color.cb_night))
                setStroke(dp(1), getColor(R.color.cb_line))
            }
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        heroIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        artwork.addView(heroIcon)
        panel.addView(artwork, LinearLayout.LayoutParams(dp(256), dp(144)))

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(30)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        heroEyebrow = label("CORE BUILDS HOME", 10f, getColor(R.color.cb_cyan), bold = true, mono = true)
        copy.addView(heroEyebrow)
        heroTitle = label("Your apps.\nYour space.", 30f, getColor(R.color.cb_text), bold = true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, dp(7))
        }
        copy.addView(heroTitle)
        heroSubtitle = label("Move with the remote to explore.", 13f, getColor(R.color.cb_muted)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        copy.addView(heroSubtitle)
        panel.addView(copy)
        return panel
    }

    private fun sectionHeader(title: String, subtitle: String): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        column.addView(label(title, 15f, getColor(R.color.cb_text), bold = true))
        column.addView(label(subtitle, 10f, getColor(R.color.cb_muted)).apply {
            setPadding(0, dp(4), 0, 0)
        })
        return column
    }

    private fun marginLayout(
        height: Int = LinearLayout.LayoutParams.WRAP_CONTENT,
        top: Int = 0,
        bottom: Int = 0,
    ): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        height,
    ).apply {
        topMargin = top
        bottomMargin = bottom
    }

    private fun refreshApps() {
        if (scanning || isFinishing) return
        scanning = true
        if (renderedAppKeys == null) scanStatus.text = "SCANNING THIS TV…"
        val focusToRestore = focusedPackage() ?: lastFocusedPackage

        appLoader.execute {
            val loaded = LauncherAppCatalog.load(applicationContext)
            runOnUiThread {
                scanning = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                val keys = loaded.map {
                    "${it.packageName}/${it.activityName}:${it.label}:${it.tvOptimized}:${it.lastUpdateTime}"
                }
                val installed = loaded.mapTo(linkedSetOf()) { it.packageName }
                val pins = LauncherPreferences.pinnedPackages(this, installed)
                val recentPackages = LauncherPreferences.recentPackages(this, installed)
                    .filterNot { it in pins }
                val appListChanged = keys != renderedAppKeys
                val pinsChanged = pins != renderedPins
                val recentsChanged = recentPackages != renderedRecentPackages
                apps = loaded
                if (appListChanged || pinsChanged || recentsChanged || renderedAppKeys == null) {
                    val firstRender = !hasRenderedApps
                    renderedAppKeys = keys
                    renderedPins = pins
                    renderedRecentPackages = recentPackages
                    renderApps(focusToRestore, installed, recentPackages, firstRender)
                    hasRenderedApps = true
                }
            }
        }
    }

    private fun renderApps(
        focusToRestore: String?,
        installed: Set<String>,
        recentPackages: List<String>,
        firstRender: Boolean,
    ) {
        val currentFocusPackage = focusedPackage()
        val focusPackage = currentFocusPackage ?: focusToRestore
        appTiles.clear()
        favoritesRow.removeAllViews()
        recentRow.removeAllViews()
        recentSection.visibility = View.GONE
        appGrid.removeAllViews()

        val pins = LauncherPreferences.pinnedPackages(this, installed)
        val favorites = apps.filter { it.packageName in pins }
        if (favorites.isEmpty()) {
            favoritesScroller.visibility = View.GONE
            emptyFavorites.visibility = View.VISIBLE
        } else {
            favoritesScroller.visibility = View.VISIBLE
            emptyFavorites.visibility = View.GONE
            favorites.forEach { app ->
                val tile = AppTileView(this, app, ::onAppFocused, wideCard = true)
                tile.setOnClickListener { launchApp(app) }
                favoritesRow.addView(tile, LinearLayout.LayoutParams(dp(220), dp(112)).apply {
                    marginEnd = dp(12)
                })
                appTiles.putIfAbsent(app.packageName, tile)
            }
        }

        val recentApps = recentPackages.mapNotNull { packageName ->
            apps.firstOrNull { it.packageName == packageName }
        }
        if (recentApps.isNotEmpty()) {
            recentSection.visibility = View.VISIBLE
            recentApps.forEach { app ->
                val tile = AppTileView(this, app, ::onAppFocused, wideCard = true)
                tile.setOnClickListener { launchApp(app) }
                recentRow.addView(tile, LinearLayout.LayoutParams(dp(220), dp(112)).apply {
                    marginEnd = dp(12)
                })
                appTiles.putIfAbsent(app.packageName, tile)
            }
        }

        appCount.text = if (apps.isEmpty()) "0 APPS" else "${apps.size} APPS"
        scanStatus.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        scanStatus.text = if (apps.isEmpty()) {
            "No apps found. Install an app or check your TV's app visibility settings."
        } else {
            ""
        }

        if (apps.isNotEmpty()) {
            val usableWidthDp = (resources.configuration.screenWidthDp - 108).coerceAtLeast(420)
            val columns = (usableWidthDp / 152).coerceIn(3, 6)
            appGrid.columnCount = columns
            val cellWidth = dp(usableWidthDp / columns) - dp(8)
            apps.forEachIndexed { index, app ->
                val tile = AppTileView(this, app, ::onAppFocused)
                tile.setOnClickListener { launchApp(app) }
                val row = index / columns
                val column = index % columns
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(row),
                    GridLayout.spec(column),
                ).apply {
                    width = cellWidth
                    height = dp(118)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                appGrid.addView(tile, params)
                appTiles.putIfAbsent(app.packageName, tile)
            }
        }

        val focusedApp = focusPackage?.let { packageName -> apps.firstOrNull { it.packageName == packageName } }
        if (focusedApp != null) updateHero(focusedApp)
        else if (selectedApp == null || apps.none { it.packageName == selectedApp?.packageName }) {
            updateHero(apps.firstOrNull())
        }

        pageContent.post {
            val target = focusPackage?.let(appTiles::get)
                ?: appTiles.values.firstOrNull()
                ?: manageButton
            if (firstRender && !userInteracted) {
                target.requestFocus()
                return@post
            }
            if (currentFocus?.isFocused == true && currentFocus?.visibility == View.VISIBLE) return@post
            target.requestFocus()
        }
    }

    private fun onAppFocused(app: LaunchableApp) {
        lastFocusedPackage = app.packageName
        updateHero(app)
    }

    private fun updateHero(app: LaunchableApp?) {
        val previous = selectedApp
        if (app != null && previous != null &&
            app.packageName == previous.packageName &&
            app.label == previous.label &&
            app.tvOptimized == previous.tvOptimized &&
            app.lastUpdateTime == previous.lastUpdateTime
        ) return

        selectedApp = app
        if (app == null) {
            heroIcon.setImageResource(R.drawable.core_builds_mark)
            heroIcon.scaleType = ImageView.ScaleType.FIT_CENTER
            heroIcon.setPadding(0, 0, 0, 0)
            heroEyebrow.text = if (apps.isEmpty()) "NO APPS FOUND" else "CORE BUILDS HOME"
            heroTitle.text = if (apps.isEmpty()) "Start with an app." else "Your apps.\nYour space."
            heroSubtitle.text = if (apps.isEmpty()) "Install an app to get started." else "Move with the remote to explore."
            return
        }
        heroIcon.setImageDrawable((app.banner ?: app.icon).copyForDisplay())
        if (app.banner != null) {
            heroIcon.scaleType = ImageView.ScaleType.CENTER_CROP
            heroIcon.setPadding(0, 0, 0, 0)
        } else {
            heroIcon.scaleType = ImageView.ScaleType.FIT_CENTER
            heroIcon.setPadding(dp(56), 0, dp(56), 0)
        }
        heroEyebrow.text = if (app.tvOptimized) "TV APP · READY TO OPEN" else "APP · READY TO OPEN"
        heroTitle.text = app.label
        heroSubtitle.text = "Press OK to open."
    }

    private fun focusedPackage(): String? = currentFocus?.tag as? String

    private fun launchApp(app: LaunchableApp) {
        val category = if (app.tvOptimized) {
            Intent.CATEGORY_LEANBACK_LAUNCHER
        } else {
            Intent.CATEGORY_LAUNCHER
        }
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            LauncherPreferences.recordLaunch(this, app.packageName)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "${app.label} is no longer available. Refreshing apps…", Toast.LENGTH_SHORT).show()
            refreshApps()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Android did not allow ${app.label} to open.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openManageFavorites() {
        startActivity(Intent(this, ManageFavoritesActivity::class.java))
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Open Settings and choose the default Home app.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateClock() {
        if (!::clockText.isInitialized || !::dateText.isInitialized) return
        val now = Date()
        clockText.text = DateFormat.getTimeInstance(DateFormat.SHORT).format(now)
        dateText.text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(now).uppercase()
    }

}
