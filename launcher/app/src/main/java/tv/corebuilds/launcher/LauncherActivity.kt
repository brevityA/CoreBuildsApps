package tv.corebuilds.launcher

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * The HOME screen (0.2): a background that follows the focused app, a top bar
 * with the clock and Customise, an info area naming the focused app, and
 * shelves below it: Favourites (ordered, with an Add card), Recently opened,
 * and All apps as a grid. Every arrow is decided here, not by Android's
 * geometric focus search: Left/Right walk a row and stop at its ends, Up/Down
 * change row to the nearest card, Up from the first row reaches Customise.
 */
class LauncherActivity : Activity() {

    /** A shelf as the remote sees it: its cards in order, and the view the scroll lines up. */
    private class NavRow(val kind: RowKind, val anchor: View, val cards: MutableList<FocusCard>, val scroller: HorizontalScrollView?)

    private val appLoader = Executors.newSingleThreadExecutor()

    private var apps = emptyList<LaunchableApp>()
    private var state = HomeState(emptyList(), emptySet(), emptyList(), HomeSettings())
    private var packs = emptyList<IconPackInfo>()
    private var activePack: IconPackInfo? = null
    private var renderedKey: String? = null
    private var loaded = false
    private var scanning = false
    private var rescanRequested = false
    private var userInteracted = false
    private var resetArmed = false
    /** The app whose artwork a pack browser or the image picker is choosing, across the round trip. */
    private var pendingArtApp: String? = null

    private lateinit var backdrop: BackdropView
    private lateinit var topBar: LinearLayout
    private lateinit var clockColumn: LinearLayout
    private lateinit var customiseButton: View
    private lateinit var infoEyebrow: TextView
    private lateinit var infoTitle: TextView
    private lateinit var infoMeta: TextView
    private lateinit var shelves: ScrollView
    private lateinit var shelvesContent: LinearLayout
    private lateinit var panel: SidePanel

    private val navRows = ArrayList<NavRow>()
    private var lastCard: Pair<RowKind, String?>? = null
    private var moving: AppCard? = null
    private var movingOrder: MutableList<String>? = null
    private var receiverRegistered = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // An app was installed, removed or updated while Home was showing.
            refreshApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingArtApp = savedInstanceState?.getString(STATE_PENDING_ART_APP)
        setContentView(buildHome())
        enterImmersiveMode()
        registerHomeBackHandler()
        showInfoText(getString(R.string.info_scanning_title), getString(R.string.info_scanning_meta), eyebrow = "")
        customiseButton.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            // Package broadcasts come from the system, so the receiver need not be exported.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(packageReceiver, filter)
            }
            receiverRegistered = true
        }
        refreshApps()
    }

    override fun onPause() {
        // Leaving mid-move keeps the order as it stands, like pressing OK.
        finishMove()
        if (receiverRegistered) {
            unregisterReceiver(packageReceiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingArtApp?.let { outState.putString(STATE_PENDING_ART_APP, it) }
    }

    override fun onDestroy() {
        rescanRequested = false
        // Let any queued preference write finish; never start new work.
        appLoader.shutdown()
        super.onDestroy()
    }

    /** Home pressed while Home is showing: close what is open and go back to the first card. */
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) goToTop()
    }

    // ---------------------------------------------------------------- back

    private fun registerHomeBackHandler() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            OnBackInvokedCallback { handleBack() },
        )
    }

    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Android 11 and 12 still deliver Back here; newer versions use the callback above.
        handleBack()
    }

    /** Back never leaves Home: it closes a panel, ends a move, or returns to the first card. */
    internal fun handleBack() {
        when {
            panel.isOpen -> panel.back()
            moving != null -> finishMove()
            else -> goToTop()
        }
    }

    private fun goToTop() {
        if (panel.isOpen) panel.close()
        finishMove()
        val first = navRows.firstOrNull()?.cards?.firstOrNull()
        animateScroll(shelves, "scrollY", 0)
        navRows.firstOrNull()?.scroller?.let { animateScroll(it, "scrollX", 0) }
        (first ?: customiseButton).requestFocus()
    }

    // ---------------------------------------------------------------- layout

    private fun buildHome(): View {
        val root = FrameLayout(this)
        backdrop = BackdropView(this)
        root.addView(backdrop, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(ImageView(this).apply {
            setImageResource(R.drawable.core_builds_mark)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
        topBar.addView(text(getString(R.string.brand_name), 17f, getColor(R.color.cb_text), weight = 600))
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        clockColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        clockColumn.addView(TextClock(this).apply {
            isSingleLine = true
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(getColor(R.color.cb_text))
            typeface = Type.outfit(context, 600)
            includeFontPadding = false
            gravity = Gravity.END
        })
        clockColumn.addView(TextClock(this).apply {
            isSingleLine = true
            val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM")
            format12Hour = pattern
            format24Hour = pattern
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(getColor(R.color.cb_muted))
            typeface = Type.outfit(context, 500)
            includeFontPadding = false
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(3)
        })
        topBar.addView(clockColumn)
        customiseButton = pillButton(R.drawable.ic_customise, getString(R.string.customise), getString(R.string.customise_description)).apply {
            setOnClickListener { openCustomise() }
            setOnFocusChangeListener { view, focused ->
                view.animateFocus(focused, 1.06f)
                if (focused) showInfoText(getString(R.string.info_customise_title), getString(R.string.info_customise_meta), eyebrow = "")
            }
        }
        topBar.addView(customiseButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply {
            marginStart = dp(24)
        })
        root.addView(topBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
            leftMargin = dp(SAFE_X); rightMargin = dp(SAFE_X); topMargin = dp(SAFE_Y)
        })

        // The info area sits on the shelves' top edge and grows upward, so a
        // two-line name never pushes the shelves around.
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        infoEyebrow = text("", 12f, getColor(R.color.cb_cyan), weight = 600, tracking = 0.12f)
        infoTitle = text("", TITLE_MAX_SP, getColor(R.color.cb_text), weight = 700, maxLines = 2).apply {
            setLineSpacing(0f, 0.95f)
        }
        infoMeta = text("", 15f, 0xFFB8C2CC.toInt(), weight = 400)
        info.addView(infoEyebrow)
        info.addView(infoTitle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
        info.addView(infoMeta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        root.addView(info, FrameLayout.LayoutParams(dp(INFO_WIDTH_DP), dp(SHELVES_TOP_DP - INFO_TOP_DP - 8)).apply {
            leftMargin = dp(SAFE_X); topMargin = dp(INFO_TOP_DP)
        })

        shelvesContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            // The fade band sits above the first row, so a row's title is never faded.
            setPadding(0, dp(FADE_DP), 0, dp(40))
        }
        shelves = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            // A row scrolled half under the info area fades out instead of ending in a hard cut.
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(FADE_DP))
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(shelvesContent, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        root.addView(shelves, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(SHELVES_TOP_DP)
        })

        panel = SidePanel(this).apply {
            returnFocus = { cardFor(lastCard) ?: navRows.firstOrNull()?.cards?.firstOrNull() ?: customiseButton }
            onClosed = {
                resetArmed = false
                shelves.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                topBar.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
        }
        root.addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return root
    }

    // ---------------------------------------------------------------- catalog

    /** Loads one catalog snapshot and coalesces refreshes requested while it is running. */
    private fun refreshApps() {
        if (isFinishing || isDestroyed) {
            rescanRequested = false
            return
        }
        if (scanning) {
            rescanRequested = true
            return
        }
        scanning = true
        try {
            appLoader.execute {
                val settings = LauncherPreferences.readSettings(applicationContext)
                val spec = artSpec(settings)
                val installedPacks = IconPacks.installed(applicationContext)
                val chosenPack = IconPacks.resolve(settings.artwork, installedPacks)
                val pack = chosenPack?.let { IconPacks.load(applicationContext, it.packageName) }
                val customArt = LauncherPreferences.readCustomArt(applicationContext)
                val loadedApps = LauncherAppCatalog.load(applicationContext, spec.copy(source = pack?.packageName ?: "app"), pack, customArt)
                val snapshot = LauncherPreferences.snapshot(applicationContext, loadedApps.map { it.packageName })
                runOnUiThread {
                    scanning = false
                    if (isFinishing || isDestroyed) {
                        rescanRequested = false
                        return@runOnUiThread
                    }
                    apps = loadedApps
                    packs = installedPacks
                    activePack = chosenPack.takeIf { pack != null }
                    state = snapshot
                    loaded = true
                    applySettings(snapshot.settings)
                    val key = renderKey()
                    if (key != renderedKey) {
                        renderedKey = key
                        renderShelves()
                    }
                    if (panel.isOpen) panel.refresh()
                    // Render this snapshot before starting the one coalesced follow-up.
                    if (rescanRequested) {
                        rescanRequested = false
                        if (!isFinishing && !isDestroyed) refreshApps()
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            // onDestroy shuts down the executor; never leave a pending refresh behind.
            scanning = false
            rescanRequested = false
        }
    }

    private fun renderKey(): String = buildString {
        apps.forEach { append(it.renderKey).append('|') }
        append(state.favourites).append(state.recents).append(state.hidden.sorted())
        append(state.settings.cardSize).append(state.settings.showRecents)
    }

    /** Card and icon pixel sizes for the chosen card size, from this screen's width. */
    private fun artSpec(settings: HomeSettings): ArtSpec {
        val (w, h) = cardSizeDp(settings.cardSize)
        return ArtSpec(dp(w), dp(h), dp(h * 0.42f), source = "", cornerRadius = dp(12).toFloat())
    }

    private fun cardSizeDp(size: CardSize): Pair<Float, Float> {
        val usable = resources.configuration.screenWidthDp - SAFE_X * 2
        val gap = gapDp(size)
        val width = (usable - gap * (size.perRow - 1)).toFloat() / size.perRow
        return width to width * 9f / 16f
    }

    private fun gapDp(size: CardSize): Int = if (size == CardSize.COMPACT) 14 else 16

    private fun applySettings(settings: HomeSettings) {
        backdrop.mode = settings.backdrop
        backdrop.dim = settings.dim
        clockColumn.visibility = if (settings.showClock) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- shelves

    private fun renderShelves() {
        val restore = if (panel.isOpen) null else focusedIdentity() ?: lastCard
        val firstRender = !userInteracted && navRows.isEmpty()
        navRows.clear()
        shelvesContent.removeAllViews()

        val (cardW, cardH) = cardSizeDp(state.settings.cardSize).let { dp(it.first) to dp(it.second) }
        val gap = dp(gapDp(state.settings.cardSize))
        val byPackage = apps.associateBy { it.packageName }

        // Favourites, then the Add card.
        val favouriteCards = state.favourites.mapNotNull { byPackage[it] }.map { appCard(it, RowKind.FAVOURITES) }
        val addCard = AddCard(this).apply {
            setOnClickListener { openFavouritesPicker(asRoot = true) }
            setOnFocusChangeListener { view, focused -> onCardFocus(view as FocusCard, focused) }
        }
        addShelf(getString(R.string.row_favourites), RowKind.FAVOURITES, favouriteCards + addCard, cardW, cardH, gap)

        if (state.settings.showRecents && state.recents.isNotEmpty()) {
            val recentCards = state.recents.mapNotNull { byPackage[it] }.map { appCard(it, RowKind.RECENT) }
            if (recentCards.isNotEmpty()) addShelf(getString(R.string.row_recent), RowKind.RECENT, recentCards, cardW, cardH, gap)
        }

        val visible = apps.filterNot { it.packageName in state.hidden }
        if (visible.isNotEmpty()) {
            val title = shelfTitle(getString(R.string.row_all_apps_count, visible.size))
            shelvesContent.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(SAFE_X); topMargin = dp(ROW_GAP_DP)
            })
            visible.chunked(state.settings.cardSize.perRow).forEachIndexed { index, chunk ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                    setPadding(dp(SAFE_X), dp(CARD_PAD_DP), dp(SAFE_X), dp(CARD_PAD_DP))
                }
                val cards = chunk.map { appCard(it, RowKind.ALL_APPS) }
                cards.forEachIndexed { i, card ->
                    row.addView(card, LinearLayout.LayoutParams(cardW, cardH).apply { if (i > 0) marginStart = gap })
                }
                shelvesContent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                navRows += NavRow(RowKind.ALL_APPS, if (index == 0) title else row, cards.toMutableList(), scroller = null)
            }
        }

        if (apps.isEmpty()) {
            showInfoText(getString(R.string.info_empty_title), getString(R.string.info_empty_meta), eyebrow = "")
        }

        if (panel.isOpen) return
        val target = when {
            firstRender -> navRows.firstOrNull()?.cards?.firstOrNull()
            else -> cardFor(restore)
        } ?: navRows.firstOrNull()?.cards?.firstOrNull() ?: customiseButton
        val current = window.currentFocus
        if (firstRender || current == null || !current.isAttachedToWindow || current is FocusCard) {
            target.requestFocus()
        }
    }

    private fun addShelf(title: String, kind: RowKind, cards: List<FocusCard>, cardW: Int, cardH: Int, gap: Int) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        container.addView(shelfTitle(title), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(SAFE_X)
        })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(SAFE_X), dp(CARD_PAD_DP), dp(SAFE_X), dp(CARD_PAD_DP))
        }
        cards.forEachIndexed { i, card ->
            row.addView(card, LinearLayout.LayoutParams(cardW, cardH).apply { if (i > 0) marginStart = gap })
        }
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        container.addView(scroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        shelvesContent.addView(container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            if (navRows.isNotEmpty()) topMargin = dp(ROW_GAP_DP)
        })
        navRows += NavRow(kind, container, cards.toMutableList(), scroller)
    }

    private fun appCard(app: LaunchableApp, kind: RowKind): AppCard = AppCard(this, app, kind).apply {
        setOnClickListener { launchApp(app) }
        setOnLongClickListener {
            openAppOptions(this)
            true
        }
        setOnFocusChangeListener { view, focused -> onCardFocus(view as FocusCard, focused) }
    }

    private fun onCardFocus(card: FocusCard, focused: Boolean) {
        if (!focused) return
        lastCard = card.kind to (card as? AppCard)?.app?.packageName
        updateInfo(card)
        revealCard(card)
    }

    private fun focusedIdentity(): Pair<RowKind, String?>? {
        val card = window.currentFocus as? FocusCard ?: return null
        return card.kind to (card as? AppCard)?.app?.packageName
    }

    /** The card for (row, package) after a re-render: the same row first, then the same app anywhere. */
    private fun cardFor(identity: Pair<RowKind, String?>?): FocusCard? {
        identity ?: return null
        val (kind, packageName) = identity
        val all = navRows.flatMap { it.cards }
        return all.firstOrNull { it.kind == kind && (it as? AppCard)?.app?.packageName == packageName && (packageName != null || it is AddCard) }
            ?: packageName?.let { pkg -> all.firstOrNull { (it as? AppCard)?.app?.packageName == pkg } }
    }

    // ---------------------------------------------------------------- info + backdrop

    private fun updateInfo(card: FocusCard) {
        val current = moving
        when {
            current != null -> {
                showInfoText(getString(R.string.info_moving_title, current.app.label), getString(R.string.info_moving_meta), eyebrow = getString(R.string.info_eyebrow_favourite))
                backdrop.show(current.app.art)
            }
            card is AppCard -> {
                val eyebrow = getString(when (card.kind) {
                    RowKind.FAVOURITES -> R.string.info_eyebrow_favourite
                    RowKind.RECENT -> R.string.info_eyebrow_recent
                    RowKind.ALL_APPS -> R.string.info_eyebrow_all_apps
                })
                val type = getString(if (card.app.tvOptimized) R.string.info_tv_app else R.string.info_app)
                showInfoText(card.app.label, getString(R.string.info_meta, type, getString(R.string.info_hint)), eyebrow)
                backdrop.show(card.app.art)
            }
            card is AddCard -> showInfoText(getString(R.string.info_add_title), getString(R.string.info_add_meta), eyebrow = getString(R.string.info_eyebrow_favourite))
        }
    }

    private fun showInfoText(title: String, meta: String, eyebrow: String) {
        infoEyebrow.text = eyebrow
        infoEyebrow.visibility = if (eyebrow.isEmpty()) View.INVISIBLE else View.VISIBLE
        fitTitle(title)
        infoMeta.text = meta
    }

    /**
     * The focused app's name as large as fits: 44sp on one line, else the
     * largest size down to 30sp on one line, else two lines at 30sp. Measured,
     * not auto-sized, so the size is chosen once and never animates.
     */
    private fun fitTitle(title: String) {
        val width = dp(INFO_WIDTH_DP).toFloat()
        val paint = Paint().apply { typeface = Type.outfit(this@LauncherActivity, 700) }
        var size = TITLE_MAX_SP
        while (size > TITLE_MIN_SP) {
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, resources.displayMetrics)
            if (paint.measureText(title) <= width) break
            size -= 2f
        }
        infoTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.coerceAtLeast(TITLE_MIN_SP))
        infoTitle.text = title
    }

    // ---------------------------------------------------------------- remote

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) userInteracted = true
        val direction = direction(event.keyCode)
        if (panel.isOpen) {
            if (direction != null) {
                if (event.action == KeyEvent.ACTION_DOWN) panel.move(direction)
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        moving?.let {
            if (direction != null) {
                if (event.action == KeyEvent.ACTION_DOWN) moveFavourite(direction)
                return true
            }
            if (isSelectKey(event.keyCode)) {
                if (event.action == KeyEvent.ACTION_UP) finishMove()
                return true
            }
        }
        if (direction != null) {
            if (event.action == KeyEvent.ACTION_DOWN) navigate(window.currentFocus, direction)
            // The home always decides; nothing falls through to Android's geometric search.
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) (window.currentFocus as? AppCard)?.let { openAppOptions(it) }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun direction(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        else -> null
    }

    private fun isSelectKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    /**
     * One arrow on the home. Left/Right stay in the row and stop at its ends;
     * Up/Down go to the neighbouring row's card nearest on screen and stop at
     * the last row; Up from the first row goes to Customise, Down from there
     * comes back to the card you left.
     */
    internal fun navigate(focused: View?, direction: Int) {
        if (focused === customiseButton) {
            if (direction == View.FOCUS_DOWN) (cardFor(lastCard) ?: navRows.firstOrNull()?.cards?.firstOrNull())?.requestFocus()
            return
        }
        val card = focused as? FocusCard
        val rowIndex = navRows.indexOfFirst { row -> row.cards.any { it === card } }
        if (card == null || rowIndex < 0) {
            (navRows.firstOrNull()?.cards?.firstOrNull() ?: customiseButton).requestFocus()
            return
        }
        val row = navRows[rowIndex]
        val col = row.cards.indexOf(card)
        when (direction) {
            View.FOCUS_LEFT -> row.cards.getOrNull(col - 1)?.requestFocus()
            View.FOCUS_RIGHT -> row.cards.getOrNull(col + 1)?.requestFocus()
            View.FOCUS_UP -> if (rowIndex == 0) customiseButton.requestFocus() else nearest(navRows[rowIndex - 1], card)?.requestFocus()
            View.FOCUS_DOWN -> navRows.getOrNull(rowIndex + 1)?.let { nearest(it, card)?.requestFocus() }
        }
    }

    private fun nearest(row: NavRow, from: View): FocusCard? {
        val x = centreX(from)
        return row.cards.minByOrNull { kotlin.math.abs(centreX(it) - x) }
    }

    private fun centreX(view: View): Int {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return location[0] + view.width / 2
    }

    /** Lines the focused card's row up under the info area, and scrolls a shelf so the card shows whole. */
    private fun revealCard(card: FocusCard) {
        val row = navRows.firstOrNull { r -> r.cards.any { it === card } } ?: return
        val rect = Rect()
        row.anchor.getDrawingRect(rect)
        shelvesContent.offsetDescendantRectToMyCoords(row.anchor, rect)
        val maxScroll = (shelvesContent.height - shelves.height).coerceAtLeast(0)
        // The row's top lands just under the fade band, where the first row sits unscrolled.
        animateScroll(shelves, "scrollY", (rect.top - dp(FADE_DP)).coerceIn(0, maxScroll))

        val scroller = row.scroller ?: return
        val margin = dp(SAFE_X)
        val left = card.left
        val right = card.right
        val view = scroller.scrollX
        val visibleWidth = scroller.width
        val target = when {
            left - margin < view -> left - margin
            right + margin > view + visibleWidth -> right + margin - visibleWidth
            else -> return
        }
        val maxX = ((scroller.getChildAt(0)?.width ?: 0) - visibleWidth).coerceAtLeast(0)
        animateScroll(scroller, "scrollX", target.coerceIn(0, maxX))
    }

    private fun animateScroll(view: View, property: String, target: Int) {
        val current = if (property == "scrollY") view.scrollY else view.scrollX
        if (current == target) return
        ObjectAnimator.ofInt(view, property, current, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    // ---------------------------------------------------------------- launch + actions

    private fun launchApp(app: LaunchableApp) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.tvOptimized) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            LauncherPreferences.recordLaunch(applicationContext, app.packageName)
        } catch (_: ActivityNotFoundException) {
            toast(getString(R.string.app_no_longer_available, app.label))
            refreshApps()
        } catch (_: SecurityException) {
            toast(getString(R.string.app_launch_blocked, app.label))
        }
    }

    private fun openAppInfo(app: LaunchableApp) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", app.packageName, null)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(getString(R.string.app_info_unavailable, app.label))
        } catch (_: SecurityException) {
            toast(getString(R.string.app_info_unavailable, app.label))
        }
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                toast(getString(R.string.open_home_settings_hint))
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun installedInOrder(): List<String> = apps.map { it.packageName }

    private fun setFavourite(app: LaunchableApp, favourite: Boolean, announce: Boolean) {
        LauncherPreferences.setFavourite(applicationContext, app.packageName, favourite, installedInOrder())
        state = state.copy(favourites = if (favourite) state.favourites - app.packageName + app.packageName else state.favourites - app.packageName)
        if (announce) toast(getString(if (favourite) R.string.toast_favourite_added else R.string.toast_favourite_removed, app.label))
        refreshApps()
    }

    private fun setHidden(app: LaunchableApp, hidden: Boolean, announce: Boolean) {
        LauncherPreferences.setHidden(applicationContext, app.packageName, hidden)
        state = state.copy(hidden = if (hidden) state.hidden + app.packageName else state.hidden - app.packageName)
        if (announce) toast(getString(if (hidden) R.string.toast_hidden else R.string.toast_shown, app.label))
        refreshApps()
    }

    private fun updateSettings(change: (HomeSettings) -> HomeSettings) {
        val next = change(state.settings)
        if (next == state.settings) return
        LauncherPreferences.saveSettings(applicationContext, next)
        state = state.copy(settings = next)
        applySettings(next)
        panel.refresh()
        refreshApps()
    }

    // ---------------------------------------------------------------- move mode

    private fun startMove(card: AppCard) {
        panel.close()
        moving = card
        movingOrder = state.favourites.toMutableList()
        card.moving = true
        card.describe()
        card.requestFocus()
        updateInfo(card)
    }

    /** Left/Right swap the moving favourite with its neighbour; the Add card stays last. */
    private fun moveFavourite(direction: Int) {
        val card = moving ?: return
        val order = movingOrder ?: return
        val row = navRows.firstOrNull { it.kind == RowKind.FAVOURITES } ?: return
        val index = row.cards.indexOf(card)
        val target = when (direction) {
            View.FOCUS_LEFT -> index - 1
            View.FOCUS_RIGHT -> index + 1
            else -> return
        }
        val neighbour = row.cards.getOrNull(target) as? AppCard ?: return
        // Move the neighbour, never the focused card, so focus never drops.
        val parent = card.parent as ViewGroup
        val gap = dp(gapDp(state.settings.cardSize))
        parent.removeView(neighbour)
        parent.addView(neighbour, index)
        row.cards[index] = neighbour
        row.cards[target] = card
        for (i in 0 until parent.childCount) {
            (parent.getChildAt(i).layoutParams as LinearLayout.LayoutParams).marginStart = if (i == 0) 0 else gap
        }
        parent.requestLayout()
        val a = order.indexOf(card.app.packageName)
        val b = order.indexOf(neighbour.app.packageName)
        if (a >= 0 && b >= 0) {
            order[a] = neighbour.app.packageName
            order[b] = card.app.packageName
        }
        card.post { revealCard(card) }
    }

    private fun finishMove() {
        val card = moving ?: return
        val order = movingOrder
        moving = null
        movingOrder = null
        card.moving = false
        card.describe()
        if (order != null && order != state.favourites) {
            LauncherPreferences.reorderFavourites(applicationContext, order, installedInOrder())
            state = state.copy(favourites = order.toList())
            renderedKey = renderKey()
        }
        if (card.isFocused) updateInfo(card)
    }

    // ---------------------------------------------------------------- panels

    private fun openPanel(page: () -> PanelPage) {
        // Nothing behind the panel can take focus while it is open.
        shelves.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        topBar.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        panel.open(page)
    }

    private fun openAppOptions(card: AppCard) {
        lastCard = card.kind to card.app.packageName
        val app = card.app
        val kind = card.kind
        openPanel {
            val favourite = app.packageName in state.favourites
            val hidden = app.packageName in state.hidden
            val items = buildList {
                add(PanelItem(getString(R.string.option_open)) { panel.close(); launchApp(app) })
                if (favourite) {
                    add(PanelItem(getString(R.string.option_remove_favourite)) { panel.close(); setFavourite(app, false, announce = true) })
                } else {
                    add(PanelItem(getString(R.string.option_add_favourite)) { panel.close(); setFavourite(app, true, announce = true) })
                }
                if (kind == RowKind.FAVOURITES && state.favourites.size > 1) {
                    add(PanelItem(getString(R.string.option_move), meta = getString(R.string.option_move_meta)) {
                        cardFor(RowKind.FAVOURITES to app.packageName)?.let { startMove(it as AppCard) }
                    })
                }
                if (hidden) {
                    add(PanelItem(getString(R.string.option_unhide)) { panel.close(); setHidden(app, false, announce = true) })
                } else {
                    add(PanelItem(getString(R.string.option_hide), meta = getString(R.string.option_hide_meta)) {
                        panel.close(); setHidden(app, true, announce = true)
                    })
                }
                add(PanelItem(
                    getString(R.string.option_change_artwork),
                    meta = getString(if (app.packageName in state.customArt) R.string.option_artwork_custom else R.string.option_artwork_default),
                ) { openArtworkPicker(app) })
                add(PanelItem(getString(R.string.option_app_info), meta = getString(R.string.option_app_info_meta)) {
                    panel.close(); openAppInfo(app)
                })
            }
            PanelPage(
                eyebrow = getString(R.string.options_eyebrow),
                title = app.label,
                body = getString(if (app.tvOptimized) R.string.info_tv_app else R.string.info_app),
                items = items,
            )
        }
    }

    private fun openCustomise() {
        openPanel { customisePage() }
    }

    private fun customisePage(): PanelPage {
        val s = state.settings
        val hiddenCount = state.hidden.count { pkg -> apps.any { it.packageName == pkg } }
        return PanelPage(
            eyebrow = getString(R.string.customise_eyebrow),
            title = getString(R.string.customise_title),
            items = listOf(
                PanelItem(getString(R.string.setting_artwork), value = artworkLabel()) { panel.push { artworkPage() } },
                PanelItem(getString(R.string.setting_background), value = backgroundLabel(s.backdrop)) { panel.push { backgroundPage() } },
                PanelItem(getString(R.string.setting_dim), value = dimLabel(s.dim)) { panel.push { dimPage() } },
                PanelItem(getString(R.string.setting_card_size), value = cardSizeLabel(s.cardSize)) { panel.push { cardSizePage() } },
                PanelItem(getString(R.string.setting_recent), value = onOff(s.showRecents)) { updateSettings { it.copy(showRecents = !it.showRecents) } },
                PanelItem(getString(R.string.setting_clock), value = onOff(s.showClock)) { updateSettings { it.copy(showClock = !it.showClock) } },
                PanelItem(getString(R.string.setting_favourites), value = resources.getQuantityString(R.plurals.setting_favourites_value, state.favourites.size, state.favourites.size)) {
                    openFavouritesPicker(asRoot = false)
                },
                PanelItem(
                    getString(R.string.setting_hidden),
                    value = if (hiddenCount == 0) getString(R.string.setting_hidden_value_none) else resources.getQuantityString(R.plurals.setting_hidden_value, hiddenCount, hiddenCount),
                ) { panel.push { hiddenPage() } },
                PanelItem(
                    getString(R.string.setting_custom_art),
                    value = customArtCount().let { n ->
                        if (n == 0) getString(R.string.setting_hidden_value_none) else resources.getQuantityString(R.plurals.setting_favourites_value, n, n)
                    },
                ) { panel.push { customArtPage() } },
                PanelItem(getString(R.string.setting_set_home), meta = getString(R.string.setting_set_home_meta)) {
                    panel.close(); openHomeSettings()
                },
                PanelItem(
                    getString(R.string.setting_reset),
                    meta = getString(if (resetArmed) R.string.setting_reset_confirm else R.string.setting_reset_meta),
                ) {
                    if (!resetArmed) {
                        resetArmed = true
                        panel.refresh()
                    } else {
                        LauncherPreferences.reset(applicationContext)
                        CustomArtStore.clearAll(applicationContext)
                        state = HomeState(emptyList(), emptySet(), emptyList(), HomeSettings())
                        renderedKey = null
                        panel.close()
                        toast(getString(R.string.toast_reset))
                        refreshApps()
                    }
                },
            ),
            footer = getString(R.string.customise_footer),
        )
    }

    private fun artworkLabel(): String = activePack?.let { packLabel(it) } ?: getString(R.string.artwork_app)

    private fun packLabel(pack: IconPackInfo): String = when (pack.packageName) {
        IconPacks.CORE_BUILDS_BANNERS -> getString(R.string.artwork_core_banners)
        in IconPacks.CORE_BUILDS_GLYPHS -> getString(R.string.artwork_core_glyphs)
        else -> pack.label
    }

    private fun artworkPage(): PanelPage {
        val chosen = IconPacks.resolve(state.settings.artwork, packs)
        val items = packs.map { pack ->
            PanelItem(packLabel(pack), meta = getString(R.string.artwork_pack_meta), selected = chosen?.packageName == pack.packageName) {
                updateSettings { it.copy(artwork = ArtworkChoice.IconPack(pack.packageName)) }
            }
        } + PanelItem(getString(R.string.artwork_app), meta = getString(R.string.artwork_app_meta), selected = chosen == null) {
            updateSettings { it.copy(artwork = ArtworkChoice.AppArtwork) }
        }
        val missing = (state.settings.artwork as? ArtworkChoice.IconPack)?.takeIf { choice -> packs.none { it.packageName == choice.packageName } }
        return PanelPage(
            eyebrow = getString(R.string.customise_eyebrow),
            title = getString(R.string.artwork_title),
            body = missing?.let { getString(R.string.artwork_missing, it.packageName) } ?: getString(R.string.artwork_body),
            items = items,
        )
    }

    private fun backgroundPage(): PanelPage = choicePage(
        getString(R.string.background_title),
        Backdrop.entries.map { mode ->
            PanelItem(backgroundLabel(mode), meta = backgroundMeta(mode), selected = state.settings.backdrop == mode) {
                updateSettings { it.copy(backdrop = mode) }
            }
        },
    )

    private fun dimPage(): PanelPage = choicePage(
        getString(R.string.dim_title),
        Dim.entries.map { level ->
            PanelItem(dimLabel(level), selected = state.settings.dim == level) { updateSettings { it.copy(dim = level) } }
        },
    )

    private fun cardSizePage(): PanelPage = choicePage(
        getString(R.string.card_size_title),
        CardSize.entries.map { size ->
            PanelItem(cardSizeLabel(size), meta = cardSizeMeta(size), selected = state.settings.cardSize == size) {
                updateSettings { it.copy(cardSize = size) }
            }
        },
    )

    private fun choicePage(title: String, items: List<PanelItem>) =
        PanelPage(eyebrow = getString(R.string.customise_eyebrow), title = title, items = items)

    private fun openFavouritesPicker(asRoot: Boolean) {
        val page = {
            PanelPage(
                eyebrow = getString(R.string.customise_eyebrow),
                title = getString(R.string.favourites_title),
                body = getString(R.string.favourites_body),
                items = apps.map { app ->
                    val favourite = app.packageName in state.favourites
                    PanelItem(
                        app.label,
                        value = if (favourite) getString(R.string.favourite_state_on) else null,
                        art = app.art,
                    ) {
                        setFavourite(app, !favourite, announce = false)
                        panel.refresh()
                    }
                },
            )
        }
        if (asRoot) openPanel(page) else panel.push(page)
    }

    // ---------------------------------------------------------------- custom artwork

    private fun customArtCount(): Int = state.customArt.keys.count { pkg -> apps.any { it.packageName == pkg } }

    /**
     * Artwork for one app. The packs' own suggestions for it (the Core Builds
     * banner and glyph, say) apply in one press; Browse opens a pack's icon
     * browser; an image comes from the TV's storage; Default undoes the pick.
     * Packs are read off the main thread before the page opens.
     */
    private fun openArtworkPicker(app: LaunchableApp) {
        val spec = artSpec(state.settings)
        try {
            appLoader.execute {
                val choices = packs.mapNotNull { info ->
                    val pack = IconPacks.load(applicationContext, info.packageName) ?: return@mapNotNull null
                    val suggestions = pack.namesFor(app.packageName).take(MAX_SUGGESTIONS).mapNotNull { name ->
                        pack.drawable(name)?.let { drawable ->
                            Triple(info, name, CardArtFactory.create(spec, drawable, { null }, { drawable }))
                        }
                    }
                    val browsable = packageManager.queryActivities(Intent(CustomArtStore.PICK_ICON_ACTION).setPackage(info.packageName)).isNotEmpty()
                    Triple(info, suggestions, browsable)
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed || !panel.isOpen) return@runOnUiThread
                    panel.push { artworkPickerPage(app, choices) }
                }
            }
        } catch (_: RejectedExecutionException) {
            // Finishing; nothing to open.
        }
    }

    private fun artworkPickerPage(
        app: LaunchableApp,
        choices: List<Triple<IconPackInfo, List<Triple<IconPackInfo, String, CardArt>>, Boolean>>,
    ): PanelPage {
        val current = state.customArt[app.packageName]
        val items = ArrayList<PanelItem>()
        items += PanelItem(getString(R.string.art_default), meta = getString(R.string.art_default_meta), selected = current == null) {
            setCustomArt(app, null)
        }
        for ((info, suggestions, _) in choices) {
            for ((pack, name, art) in suggestions) {
                val choice = CustomArt.PackResource(pack.packageName, name)
                items += PanelItem(packLabel(info), meta = getString(R.string.art_suggestion_meta), art = art, selected = current == choice) {
                    setCustomArt(app, choice)
                }
            }
        }
        for ((info, _, browsable) in choices) {
            if (!browsable) continue
            items += PanelItem(getString(R.string.art_browse, packLabel(info)), meta = getString(R.string.art_browse_meta)) {
                pendingArtApp = app.packageName
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(CustomArtStore.PICK_ICON_ACTION).setPackage(info.packageName), REQUEST_PICK_ICON)
                } catch (_: ActivityNotFoundException) {
                    pendingArtApp = null
                    toast(getString(R.string.art_browse_failed, packLabel(info)))
                }
            }
        }
        items += PanelItem(
            getString(R.string.art_image),
            meta = getString(R.string.art_image_meta),
            selected = current is CustomArt.Image,
        ) {
            pendingArtApp = app.packageName
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),
                    REQUEST_PICK_IMAGE,
                )
            } catch (_: ActivityNotFoundException) {
                pendingArtApp = null
                toast(getString(R.string.art_no_image_picker))
            }
        }
        return PanelPage(
            eyebrow = getString(R.string.options_eyebrow),
            title = getString(R.string.art_title, app.label),
            body = getString(R.string.art_body),
            items = items,
        )
    }

    @Deprecated("Activity results without AndroidX; this launcher has no AndroidX dependency.")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_ICON && requestCode != REQUEST_PICK_IMAGE) return
        val packageName = pendingArtApp ?: return
        pendingArtApp = null
        if (resultCode != RESULT_OK) return
        val app = apps.firstOrNull { it.packageName == packageName } ?: return
        val uri = data?.data
        try {
            appLoader.execute {
                // Decoding and saving an image is disk work; keep it off the main thread.
                val art = when (requestCode) {
                    REQUEST_PICK_IMAGE -> uri?.let { CustomArtStore.importImage(applicationContext, it, packageName) }
                    else -> CustomArtStore.fromPickResult(applicationContext, data, packageName)
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (art == null) {
                        toast(getString(if (requestCode == REQUEST_PICK_IMAGE) R.string.art_image_failed else R.string.art_nothing_returned))
                    } else {
                        setCustomArt(app, art)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            // Finishing; the choice is dropped.
        }
    }

    /** Saves (or with null, clears) [app]'s artwork, deletes an image it replaced, and redraws. */
    private fun setCustomArt(app: LaunchableApp, art: CustomArt?) {
        val previous = LauncherPreferences.setCustomArt(applicationContext, app.packageName, art)
        if (previous == art) {
            if (panel.isOpen) panel.close()
            return
        }
        CustomArtStore.discard(applicationContext, previous)
        state = state.copy(customArt = if (art == null) state.customArt - app.packageName else state.customArt + (app.packageName to art))
        if (panel.isOpen) panel.close()
        toast(getString(if (art == null) R.string.toast_artwork_default else R.string.toast_artwork_custom, app.label))
        refreshApps()
    }

    private fun customArtPage(): PanelPage {
        val custom = apps.filter { it.packageName in state.customArt }
        return PanelPage(
            eyebrow = getString(R.string.customise_eyebrow),
            title = getString(R.string.custom_art_title),
            body = getString(R.string.custom_art_body),
            items = custom.map { app ->
                PanelItem(app.label, value = getString(R.string.custom_art_state), art = app.art) {
                    LauncherPreferences.setCustomArt(applicationContext, app.packageName, null)
                        ?.let { CustomArtStore.discard(applicationContext, it) }
                    state = state.copy(customArt = state.customArt - app.packageName)
                    panel.refresh()
                    refreshApps()
                }
            },
            empty = getString(R.string.custom_art_empty),
        )
    }

    private fun hiddenPage(): PanelPage {
        val hidden = apps.filter { it.packageName in state.hidden }
        return PanelPage(
            eyebrow = getString(R.string.customise_eyebrow),
            title = getString(R.string.hidden_title),
            body = getString(R.string.hidden_body),
            items = hidden.map { app ->
                PanelItem(app.label, value = getString(R.string.hidden_state), art = app.art) {
                    setHidden(app, false, announce = false)
                    panel.refresh()
                }
            },
            empty = getString(R.string.hidden_empty),
        )
    }

    private fun onOff(value: Boolean) = getString(if (value) R.string.value_on else R.string.value_off)

    private fun backgroundLabel(mode: Backdrop) = getString(when (mode) {
        Backdrop.FOCUSED_APP -> R.string.background_focused
        Backdrop.CORE_BUILDS -> R.string.background_core
        Backdrop.VOID -> R.string.background_void
    })

    private fun backgroundMeta(mode: Backdrop) = getString(when (mode) {
        Backdrop.FOCUSED_APP -> R.string.background_focused_meta
        Backdrop.CORE_BUILDS -> R.string.background_core_meta
        Backdrop.VOID -> R.string.background_void_meta
    })

    private fun dimLabel(level: Dim) = getString(when (level) {
        Dim.LIGHT -> R.string.dim_light
        Dim.MEDIUM -> R.string.dim_medium
        Dim.STRONG -> R.string.dim_strong
    })

    private fun cardSizeLabel(size: CardSize) = getString(when (size) {
        CardSize.COMPACT -> R.string.card_size_compact
        CardSize.STANDARD -> R.string.card_size_standard
        CardSize.LARGE -> R.string.card_size_large
    })

    private fun cardSizeMeta(size: CardSize) = getString(when (size) {
        CardSize.COMPACT -> R.string.card_size_compact_meta
        CardSize.STANDARD -> R.string.card_size_standard_meta
        CardSize.LARGE -> R.string.card_size_large_meta
    })

    internal companion object {
        /** The TV overscan-safe margins: 48dp at the sides, 27dp top and bottom. */
        const val SAFE_X = 48
        const val SAFE_Y = 27
        const val INFO_TOP_DP = 76
        const val INFO_WIDTH_DP = 600
        const val SHELVES_TOP_DP = 212
        /** The band at the shelves' top where a row scrolled up fades out. */
        const val FADE_DP = 24
        const val ROW_GAP_DP = 6
        /** Room around each row of cards for the 1.1x focus lift and its ring. */
        const val CARD_PAD_DP = 12
        const val REQUEST_PICK_ICON = 41
        const val REQUEST_PICK_IMAGE = 42
        const val STATE_PENDING_ART_APP = "pending_art_app"
        /** At most this many suggestions per pack for one app: banner, glyph and an alternate. */
        const val MAX_SUGGESTIONS = 3
        const val TITLE_MAX_SP = 44f
        const val TITLE_MIN_SP = 30f
    }
}
