package tv.corebuilds.iconpack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.doOnLayout
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import java.text.NumberFormat

/**
 * Front door. Apply targets the Home launcher. An update bar appears
 * when Latestrelease/version.json is newer; Download pulls the APK and
 * hands it to the system installer.
 *
 * Focus contract while searching, because a D-pad has no touch position to
 * recover from: the search field owns the cursor for as long as the user is
 * typing and nothing else on this screen may take it — not the grid when a
 * result set changes, not the update bar when its network check happens to
 * resolve mid-word. The keyboard follows the cursor: it opens when the field is
 * focused, closes when the cursor leaves, and the Search key closes it and
 * moves the cursor into the results. Where focus goes after a filter is decided
 * in [settleFilterFocus] and nowhere else; the one other move this screen makes
 * is the deliberate one the Search key makes in [onSearchAction].
 */
class MainActivity : TvActivity() {

    private var target: ApplyIconPack.Launcher? = null
    private var updateChecked = false
    private var whatsNewShown = false
    /** Set by the bar's Later button: the bar stays hidden for this session. */
    private var updateDismissed = false
    private var pickMode = false
    private lateinit var all: List<IconAdapter.IconItem>
    private lateinit var adapter: IconAdapter
    private lateinit var chipAdapter: ChipAdapter

    /**
     * Catalogue position of every icon, by drawable name.
     *
     * Both the list before a filter and the list after it are order-preserving
     * subsets of [all], so an icon's index here is the only distance between
     * them that survives a filter. That is what makes "nearest surviving icon"
     * in [nearestSurvivor] mean something.
     */
    private var catalogOrder: Map<String, Int> = emptyMap()
    /** Apps in the grid: every tile but the alternates. */
    private var iconCount = 0

    /** The first entry of the rail's last row: Support, except on Play. */
    private val railHead: Int =
        if (Distribution.PLAY) R.id.settings_entry else R.id.support_entry

    private var category = ALL
    private var query = ""
    private var pickBanners = false
    private var pendingUpdate: UpdateChecker.Result.Available? = null
    private var downloadedApk: File? = null
    private var installOffered = false
    /** The bar's download is running: presses wait, and the button keeps the remote. */
    private var updateDownloading = false

    /**
     * Keystrokes are filtered as a burst, not one at a time. See [scheduleFilter].
     */
    private val filterHandler = Handler(Looper.getMainLooper())
    private val filterRunnable = Runnable {
        filterPending = false
        applyFilter()
    }
    private var filterPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Google Play only (a no-op elsewhere): settle Core Builds Plus with
        // Play once per process, so its extras follow purchases and refunds.
        PlusStore.sync(this)

        pickMode = IconPicker.isPickRequest(intent)
        if (pickMode) {
            IconPicker.cancel(this)
        }

        val drawables = resources.getStringArray(R.array.icon_pack)
        val names = resources.getStringArray(R.array.icon_names)
        val cats = resources.getStringArray(R.array.icon_categories)
        val bespoke = resources.getIntArray(R.array.icon_bespoke)
        all = drawables.indices.map { i ->
            IconAdapter.IconItem(
                drawable = drawables[i],
                name = names.getOrElse(i) { drawables[i] },
                category = cats.getOrElse(i) { "APP" },
                // getOrElse rather than indexing: the four arrays are written
                // by the same generator pass and cannot drift, but a truncated
                // resource should dim a badge, not crash the grid.
                bespoke = bespoke.getOrElse(i) { 0 } == 1
            )
        }
        catalogOrder = all.withIndex().associate { (index, item) -> item.drawable to index }
        // Alternates are extra tiles for apps already in the grid, so every
        // number on this screen counts apps and leaves them out.
        iconCount = all.count { !it.alternate }

        findViewById<TextView>(R.id.count).text =
            getString(R.string.pack_stats_fmt, iconCount, mappedComponents())

        pickBanners = if (pickFixedByPack() &&
            intent.getBooleanExtra(GlyphsCompanion.EXTRA_PICK_GLYPHS, false)) {
            // Core Builds Glyphs forwards its picks here marked: squares only.
            false
        } else {
            // A pick through this pack follows the Art style switch (2.0.1),
            // so one app can hand out either shape card by card. Picking
            // never writes the switch, so it cannot flip what the launcher
            // auto-applies.
            Prefs.pickerPrefersBanners(this)
        }
        adapter = IconAdapter(all, showBanners = pickBanners) { item -> onIconChosen(item) }
        findViewById<RecyclerView>(R.id.grid).apply {
            layoutManager = GridLayoutManager(this@MainActivity, spanForScreen())
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
            // Filtering should not animate every one of the 900+ tiles. The
            // tile itself owns the small focus animation, so D-pad focus stays
            // stable while DiffUtil updates the data set.
            itemAnimator = null
        }

        if (pickMode) {
            // With a companion, each pack picks its own art (see
            // pickFixedByPack). Without one (the candidate build), the shape
            // a user last delivered simply stays: the picker opens on the
            // stored chip, and the chip row keeps it. The shipped default is
            // the banner - the same art the appfilter maps for launcher-side
            // apply.
            val pickerHint = findViewById<TextView>(R.id.picker_hint)
            pickerHint.visibility = View.VISIBLE
            pickerHint.text = pickHint()
            findViewById<TextView>(R.id.apply_button).visibility = View.GONE
            findViewById<TextView>(R.id.apply_sub).visibility = View.GONE
            findViewById<LinearLayout>(R.id.update_bar).visibility = View.GONE
        }

        bindChips()
        bindSearch()
        bindEmptyActions()
        syncFocusChain()
        bindRailScroll()
        matchRowTextSizes()
        // Play's Payments policy: an app distributed on Play may not lead users
        // to a payment method other than Play Billing, and Support is two QR
        // codes for GitHub Sponsors and Ko-fi - tips to one person are not a
        // tax-exempt donation, and Google has rejected real apps for exactly
        // that link. The Play build has no Support entry at all (this rail,
        // Settings and About each hide theirs), so the last row closes up to
        // Settings | About. The ids the focus chain names are rewritten rather
        // than left pointing at the hidden entry: a GONE view that is
        // focusable still takes the cursor.
        if (Distribution.PLAY) {
            findViewById<View>(R.id.support_entry).visibility = View.GONE
            // Nothing is left of Settings, so LEFT falls back to geometry
            // (stays put) instead of walking to a view nobody can see.
            findViewById<View>(R.id.settings_entry).setNextFocusLeftId(View.NO_ID)
            // The wallpaper card steps DOWN onto the row's first entry.
            findViewById<View>(R.id.wallpapers_entry).setNextFocusDownId(railHead)
        }

        if (pickMode) {
            // Icon-picker mode has no use for the wallpapers entry or apply.
            findViewById<View>(R.id.wallpapers_group).visibility = View.GONE
            bindPickShape()
        } else {
            bindApplyButton()
            // The row keeps the one-word title; what it contains moves to the
            // subtitle under it. Overwriting the label with the long sentence
            // made the affordance read as prose rather than as something you
            // press, which is why the sheet draws three rows of title + sub.
            val wpEntry = findViewById<View>(R.id.wallpapers_entry)
            bindWallpapersSub()
            wpEntry.setOnClickListener { startActivity(Intent(this, WallpapersActivity::class.java)) }
            bindStyleRow()
            findViewById<View>(R.id.missing_entry).setOnClickListener {
                startActivity(Intent(this, AuditorActivity::class.java))
            }
            findViewById<View>(R.id.support_entry).setOnClickListener {
                startActivity(Intent(this, SupportActivity::class.java))
            }
            findViewById<View>(R.id.settings_entry).setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            findViewById<View>(R.id.about_entry).setOnClickListener {
                startActivity(Intent(this, AboutActivity::class.java))
            }
        }

        // Give a physical remote a deterministic starting point. Without an
        // initial target Android may leave focus on the decor view, requiring
        // an extra key press before the first D-pad move does anything.
        window.decorView.post {
            if (pickMode) {
                // Unconditional: the picker is launched by another app, nobody
                // is mid-typing, and the platform's own default here is the
                // banner/square shape row rather than the category chips.
                val chips = findViewById<RecyclerView>(R.id.chip_row)
                chips.post {
                    val firstChip = chips.layoutManager?.findViewByPosition(0)
                    (firstChip ?: chips).requestFocus()
                }
            } else if (currentFocus == null || currentFocus === window.decorView) {
                // Guarded the way WallpapersActivity guards its own: this runs
                // on a post, so it lands after the first traversal, and it must
                // not take the cursor away from a control already reached.
                findViewById<View>(R.id.apply_button).requestFocus()
            }
        }
    }

    /**
     * The rail scrolls when it is taller than the screen: large system text, the
     * update bar and the launcher note under Apply can each push its last row
     * past the bottom edge. D-pad focus scrolls it down to the row it lands on;
     * when focus leaves the rail it goes back to the top, so the wordmark and the
     * icon count are on screen again instead of half a line clipped under the
     * update bar (2.4.0 at the TV's largest text size).
     */
    private fun bindRailScroll() {
        val rail = findViewById<ScrollView>(R.id.rail)
        rail.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus == null || rail.scrollY == 0 || isInside(newFocus, rail)) {
                return@addOnGlobalFocusChangeListener
            }
            if (Prefs.reduceMotion(this)) rail.scrollTo(0, 0) else rail.smoothScrollTo(0, 0)
        }
    }

    /**
     * Support | Settings | About each shrink to fit a third of the rail under
     * large system text (autoSize in activity_main.xml), but each on its own, so
     * "About" would stay larger than "Settings". After the first layout all three
     * take the smallest size and keep it, so the row reads as one control. At the
     * default text size all three already fit at full size and nothing changes.
     */
    private fun matchRowTextSizes() {
        val labels = intArrayOf(R.id.support_entry, R.id.settings_entry, R.id.about_entry)
            .map { findViewById<TextView>(it) }
        (labels.first().parent as View).doOnLayout {
            val smallest = labels.minOf { it.textSize }
            for (label in labels) {
                TextViewCompat.setAutoSizeTextTypeWithDefaults(label, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallest)
            }
        }
    }

    private fun isInside(view: View, ancestor: View): Boolean {
        var node: Any? = view
        while (node is View) {
            if (node === ancestor) return true
            node = node.parent
        }
        return false
    }

    /**
     * The Wallpapers row's subtitle: the count, and - until the browser has
     * been opened once since the manifest's newest release - how many walls
     * that release added or re-rendered, so an update taken for the
     * wallpapers says where they are. Re-bound in onResume so returning from
     * the browser clears the "new" the same visit.
     */
    private fun bindWallpapersSub() {
        val wpSub = findViewById<TextView>(R.id.wallpapers_entry_sub)
        val walls = WallpaperCatalog.load(this)
        if (walls.isEmpty()) {
            // No subtitle rather than repeating the row's own label.
            wpSub.text = ""
            return
        }
        val newest = WallpaperCatalog.newestSince(walls)
        val fresh = WallpaperCatalog.newWalls(walls).size
        wpSub.text = if (newest != null && fresh > 0 && Prefs.wallpapersNewSeen(this) != newest) {
            getString(R.string.wp_entry_sub_new_fmt, walls.size, fresh)
        } else {
            getString(R.string.wp_entry_sub_fmt, walls.size)
        }
    }

    /**
     * Rewrite the vertical D-pad chain around the containers that come and go,
     * at the moment their visibility changes.
     *
     * The sheet's screen order is apply_button -> update_bar -> style_entry ->
     * missing_entry -> wallpapers_entry -> support_entry | settings_entry |
     * about_entry (one row) -> apply_targets ->
     * search -> chip_row ->
     * grid, and three of those stops are conditional: the update bar only when a
     * newer manifest exists, the ALSO APPLIES TO row only with a second launcher
     * installed, the grid only while a filter matches anything. A GONE view that
     * is focusable still takes the cursor - `isFocusable()` ignores visibility -
     * so UP from the search field used to park the ring on the invisible bar and
     * the next UP on the invisible target row, after which UP/DOWN ping-ponged
     * between two views nobody can see and the chips and the grid read as dead:
     * "the lists disappeared". The conditional containers are focusable="false"
     * in the layout so a hidden one can never hold the cursor; this does the
     * other half, naming the nearest VISIBLE stop for every edge that routes
     * through them. `update_button` is named rather than its bar on purpose: a
     * child of a GONE parent still reports itself VISIBLE, so the bar's own flag
     * is what decides, and it is read here.
     */
    private fun syncFocusChain() {
        val bar = findViewById<View>(R.id.update_bar)
        val band = findViewById<View>(R.id.apply_targets_band)
        val targets = findViewById<RecyclerView>(R.id.apply_targets)
        val group = findViewById<View>(R.id.wallpapers_group)
        val grid = findViewById<RecyclerView>(R.id.grid)
        val barShown = bar.visibility == View.VISIBLE
        // The band is what comes and goes; the list inside it is the stop.
        val targetsShown = band.visibility == View.VISIBLE
        val groupShown = group.visibility == View.VISIBLE
        // A shown target row can be a named stop: focusable only while shown
        // (set below), and afterDescendants hands the cursor to its first chip.
        targets.isFocusable = targetsShown
        // In icon-picker mode the CTA and the entry rows are hidden, so there is
        // nothing focusable above the band at all: name the picker's hint, which
        // is not focusable, and let the cursor stay where it is rather than
        // naming a GONE button.
        val applyShown = findViewById<View>(R.id.apply_button).visibility == View.VISIBLE
        val aboveRows = when {
            barShown -> R.id.update_button
            applyShown -> R.id.apply_button
            else -> R.id.picker_hint
        }
        val belowRows = if (targetsShown) R.id.apply_targets else R.id.search
        findViewById<View>(R.id.style_entry).nextFocusUpId = aboveRows
        val aboveBar = if (applyShown) R.id.apply_button else R.id.picker_hint
        for (id in intArrayOf(R.id.update_button, R.id.update_later)) {
            findViewById<View>(id).nextFocusUpId = aboveBar
            findViewById<View>(id).nextFocusDownId =
                if (groupShown) R.id.style_entry else R.id.search
        }
        // Support, Settings and About share the rail's last row: all step down.
        // On Play there is no Support entry, so [railHead] is Settings.
        findViewById<View>(R.id.support_entry).nextFocusDownId = belowRows
        findViewById<View>(R.id.settings_entry).nextFocusDownId = belowRows
        findViewById<View>(R.id.about_entry).nextFocusDownId = belowRows
        findViewById<View>(R.id.apply_targets).nextFocusUpId =
            if (groupShown) railHead else aboveRows
        findViewById<View>(R.id.apply_targets).nextFocusDownId = R.id.search
        findViewById<View>(R.id.search).nextFocusUpId = belowRows.takeIf { targetsShown }
            ?: if (groupShown) railHead else aboveRows
        findViewById<View>(R.id.chip_row).nextFocusUpId = R.id.search
        // Down from the category row: the grid, or the empty state's undo when
        // the grid is the container that just went GONE. The search field always
        // names the chips now - on the sheet they have their own row between the
        // field and the grid, so DOWN from the field lands on the filter row
        // instead of leaping into the results.
        findViewById<View>(R.id.chip_row).nextFocusDownId = when {
            grid.visibility == View.VISIBLE -> R.id.grid
            findViewById<View>(R.id.empty_clear_search).visibility == View.VISIBLE ->
                R.id.empty_clear_search
            else -> R.id.empty_clear_filter
        }
    }

    private fun bindEmptyActions() {
        findViewById<TextView>(R.id.empty_clear_search).setOnClickListener {
            val search = findViewById<EditText>(R.id.search)
            search.setText("")
            query = ""
            // setText("") already queued a debounced pass; this cancels it and
            // filters now, so the grid the user is handed back to is the full
            // one rather than last keystroke's.
            applyFilterNow()
            search.requestFocus()
        }
        findViewById<TextView>(R.id.empty_clear_filter).setOnClickListener {
            // select() drives the chip highlight and calls back into
            // applyFilter through the same path a D-pad press would take.
            chipAdapter.select(ALL)
        }
    }

    /**
     * Remote bumper skips: CHANNEL_DOWN / CHANNEL_UP jump a letter group
     * through the filtered list, because paging 943 tiles six rows at a time
     * is the single most common complaint about icon grids on a D-pad.
     *
     * Only while the grid itself holds focus. Anywhere else - search field,
     * chips, header - the keys keep their default meaning, and a launcher
     * that reserves them for its own paging still gets them on every other
     * screen of this app. The anchor is the first visible row, so the jump
     * reads like a scrollbar: where you are looking is where you jump from.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val forward = when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_DOWN -> true
            KeyEvent.KEYCODE_CHANNEL_UP -> false
            else -> return super.onKeyDown(keyCode, event)
        }
        val grid = findViewById<RecyclerView>(R.id.grid)
        if (!grid.hasFocus()) return super.onKeyDown(keyCode, event)
        return if (jumpByLetter(grid, forward)) true
        else super.onKeyDown(keyCode, event)
    }

    /** Move the grid to the next / previous letter group. False = no group
     *  left in that direction, and the key falls through unconsumed. */
    private fun jumpByLetter(grid: RecyclerView, forward: Boolean): Boolean {
        val items = adapter?.current() ?: return false
        if (items.isEmpty()) return false
        val manager = grid.layoutManager as? LinearLayoutManager ?: return false
        val anchor = manager.findFirstVisibleItemPosition()
        if (anchor == RecyclerView.NO_POSITION) return false
        val anchorInitial = initialOf(items[anchor].name)
        val target = if (forward) {
            items.indices.firstOrNull {
                it > anchor && initialOf(items[it].name) > anchorInitial
            }
        } else {
            // Land on the first row of the previous letter group, not on the
            // last row of the one before the jump - a skip should arrive
            // where the group starts, the same place a forward skip leaves.
            val last = items.indices.lastOrNull {
                it < anchor && initialOf(items[it].name) < anchorInitial
            }
            if (last == null) null
            else {
                var start = last
                val letter = initialOf(items[last].name)
                while (start > 0 && initialOf(items[start - 1].name) == letter) start--
                start
            }
        }
        if (target == null || target == anchor) return false
        manager.scrollToPositionWithOffset(target, 0)
        grid.post {
            grid.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()
        }
        return true
    }

    /**
     * Component items in the bundled appfilter: the right-hand number of the
     * sheet's header ("943 icons - 1765 components"). The asset ships with the
     * APK and never changes at runtime, so it is read once and remembered -
     * this runs again on every filter update, where the header line switches to
     * the matched/total form and back.
     */
    private var componentCount = -1

    private fun mappedComponents(): Int {
        if (componentCount < 0) {
            componentCount = try {
                assets.open("appfilter.xml").bufferedReader().use { it.readText() }
                    .split("component=").size - 1
            } catch (e: Exception) {
                0
            }
        }
        return componentCount
    }

    private fun initialOf(name: String): Char =
        name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '#'

    private fun onIconChosen(item: IconAdapter.IconItem) {
        val deliver = if (pickBanners) "${item.drawable}_banner" else item.drawable
        if (!pickMode) {
            // The toast this replaces named the icon for two seconds, exactly
            // when someone wanted to read it. The inspector is the same
            // information with a place to put it: full-size mark, mapped
            // components, export and launch.
            startActivity(
                Intent(this, InspectorActivity::class.java)
                    .putExtra(InspectorActivity.EXTRA_DRAWABLE, deliver)
                    .putExtra(InspectorActivity.EXTRA_NAME, item.name)
                    .putExtra(InspectorActivity.EXTRA_CATEGORY, item.category)
            )
            return
        }
        if (!IconPicker.deliver(this, deliver)) {
            toast(getString(R.string.picker_failed_fmt, item.name))
        }
    }

    override fun onResume() {
        super.onResume()
        // A companion install that finished without its result reaching us
        // (the activity was recreated): apply once the package is really
        // there. A declined install is only ever reported by the installer's
        // result, in onActivityResult - a resume is not an answer.
        if (!pickMode) onCompanionOutcome(GlyphsCompanion.takePendingApply(this))
        syncArtStyle()
        if (!pickMode) {
            bindApplyButton()
            refreshMissingCount()
            // What's New fires once per upgrade, independently of the update
            // checker's switch: it reads the APK's own asset, so it works
            // with the network check off. Fresh installs seed the gate
            // instead of narrating — nothing changed *for them*, and the
            // first-run flow has the focus. lastUpdateTime >
            // firstInstallTime is what separates "just installed 1.9.2" from
            // "updated into it", a difference SharedPreferences alone
            // cannot see.
            if (!whatsNewShown) {
                val seen = Prefs.whatsNewSeen(this)
                if (seen == 0) {
                    val info = packageManager.getPackageInfo(packageName, 0)
                    if (info.lastUpdateTime > info.firstInstallTime) {
                        whatsNewShown = true
                        startActivity(Intent(this, WhatsNewActivity::class.java))
                    } else {
                        Prefs.setWhatsNewSeen(this, BuildConfig.VERSION_CODE)
                    }
                } else if (BuildConfig.VERSION_CODE > seen) {
                    whatsNewShown = true
                    startActivity(Intent(this, WhatsNewActivity::class.java))
                }
            }
            bindWallpapersSub()
            // Gated by Settings. Off means this startup check never runs, so
            // the only update request left is the one the user asks for in
            // About - which is what the About screen's network list says, and
            // the claim has to stay checkable.
            // Play builds update through Play only (Device and Network Abuse
            // policy), so they never check GitHub or offer an APK.
            if (!Distribution.PLAY && !updateChecked && Prefs.updateChecks(this)) {
                updateChecked = true
                checkForUpdate()
            }
            downloadedApk?.let { file ->
                if (!file.exists()) {
                    downloadedApk = null
                    return@let
                }
                // The update flow is mid-round-trip: the APK is downloaded
                // and verified, and this resume is either back from the
                // permission screen or the installer's task returning.
                val bar = findViewById<View>(R.id.update_bar)
                if (bar.visibility == View.VISIBLE) {
                    val button = findViewById<TextView>(R.id.update_button)
                    if (UpdateInstaller.canInstall(this) && !installOffered) {
                        // The permission was granted: open the installer
                        // without a second press and park the remote on the
                        // button underneath.
                        installOffered = true
                        button.requestFocus()
                        startInstall(file)
                    } else if (!UpdateInstaller.canInstall(this)) {
                        // Back without granting: the sub-line says what is
                        // owed; the remote lands on the button, not the grid.
                        button.requestFocus()
                    }
                }
            }
        }
    }

    private fun bindChips() {
        val present = all.map { it.category }.toSet()
        // Counts are tallied from the same generated arrays that feed the
        // grid, so a chip can never advertise a number the grid cannot back
        // up. A hand-typed "(184)" goes stale on the next tranche; this does
        // not. They count apps: an alternate is a second tile for one.
        val apps = all.filterNot { it.alternate }
        val counts = apps.groupingBy { it.category }.eachCount()
        val keys = mutableListOf(ALL)
        val labels = mutableListOf(
            getString(R.string.chip_count_fmt, getString(R.string.chip_all), iconCount)
        )
        // Brandmarks is not a category — it cuts across all of them — so it is
        // added by hand next to All rather than through CHIP_ORDER, and
        // applyFilter branches on it. Second position because it is the one
        // filter that answers "show me the drawn art", which is what a browsing
        // user is usually here for.
        if (all.any { it.bespoke }) {
            keys += BESPOKE
            labels += getString(
                R.string.chip_count_fmt,
                getString(R.string.chip_brandmarks),
                apps.count { it.bespoke }
            )
        }
        for ((key, label) in CHIP_ORDER) {
            if (key in present) {
                keys += key
                labels += getString(R.string.chip_count_fmt, label, counts[key] ?: 0)
            }
        }
        chipAdapter = ChipAdapter(labels, keys, ALL) { picked ->
            category = picked
            // A chip press is a decision, not a keystroke: it filters at once
            // rather than waiting out the typing debounce, and cancels anything
            // the search field had queued.
            applyFilterNow()
        }
        findViewById<RecyclerView>(R.id.chip_row).apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity, LinearLayoutManager.HORIZONTAL, false
            )
            // Same guard the grid and the wallpaper chip row carry, and the one
            // WallpapersActivity's comment already claimed this row had.
            // ChipAdapter.select() answers a press with two notifyItemChanged
            // calls; the default change animation swaps the pressed chip for a
            // fresh ViewHolder and cross-fades it, which drops the D-pad
            // highlight on the press that was supposed to move it.
            itemAnimator = null
            adapter = chipAdapter
        }
    }

    /**
     * The search field, and the keyboard that comes with it.
     *
     * Three things a TV needs and none of which the platform does on its own
     * here:
     *
     *  1. A field focused by remote does not reliably open the IME. The
     *     platform shows soft input for touch-mode focus; a D-pad focus can
     *     leave a caret blinking in an empty field with no keyboard on screen,
     *     so focus in asks for it explicitly and focus out puts it away —
     *     where it would otherwise hang over the results the user just moved
     *     the cursor into.
     *  2. The keyboard's Search key, and Enter on a hardware keyboard, both
     *     arrive as [EditorInfo.IME_ACTION_SEARCH] because the field declares
     *     `imeOptions="actionSearch"`. Nothing consumed them, so the one key
     *     that means "I have finished typing" did nothing at all.
     *  3. Filtering runs once per burst rather than once per character.
     */
    private fun bindSearch() {
        val search = findViewById<EditText>(R.id.search)
        search.hint = getString(R.string.search_hint_fmt, iconCount)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                scheduleFilter()
            }
        })
        search.setOnFocusChangeListener { view, focused ->
            if (focused) {
                val service = getSystemService(Context.INPUT_METHOD_SERVICE)
                val imm = service as? InputMethodManager ?: return@setOnFocusChangeListener
                // Posted, then re-checked. showSoftInput from inside the focus
                // change is too early on some boxes: the field is focused but is
                // not the window's input target yet, and the request is dropped
                // without a word. IMPLICIT rather than EXPLICIT because with a
                // hardware keyboard paired there is no soft input to show, and
                // that is the system's call, not ours.
                view.post {
                    if (view.hasFocus()) {
                        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            } else {
                // The cursor left for the grid or a chip. A keyboard hanging
                // over the results it just moved into covers what the user went
                // to look at.
                hideIme(view)
            }
        }
        search.setOnEditorActionListener { view, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_GO -> onSearchAction(view)
                else -> false
            }
        }
    }

    /**
     * Search/Enter: close the keyboard and put the cursor on the first result.
     *
     * Returning true consumes the action, which is also what stops TextView's
     * default handling from hiding the IME and leaving focus where it was —
     * the state that read as "the keyboard went away and nothing happened".
     */
    private fun onSearchAction(view: View): Boolean {
        hideIme(view)
        // A keystroke's debounce must not land after this move: it would filter
        // again with the cursor already inside the grid and re-decide focus
        // from there.
        applyFilterNow()
        val grid = findViewById<RecyclerView>(R.id.grid)
        if (adapter.itemCount == 0) {
            // Nothing to open. The empty state's own undo is the next stop, and
            // it is the one place on the screen that can fix the query.
            focusEmptyAction()
            return true
        }
        grid.scrollToPosition(0)
        grid.post {
            (grid.layoutManager?.findViewByPosition(0) ?: grid.getChildAt(0))?.requestFocus()
        }
        return true
    }

    private fun hideIme(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Queue a filter for the end of the current burst of typing.
     *
     * One character used to mean one filter pass over 943 icons, one DiffUtil
     * calculation and one full grid layout, all on the main thread inside
     * [TextWatcher.afterTextChanged]. On a TV stick that is enough work for the
     * caret to lag behind a remote's key repeat, and it re-lays out the window
     * underneath an open IME once per character. 120ms is longer than a repeat
     * interval and shorter than the pause between words, so a burst produces
     * one diff and one layout instead of five.
     */
    private fun scheduleFilter() {
        filterHandler.removeCallbacks(filterRunnable)
        filterPending = true
        filterHandler.postDelayed(filterRunnable, FILTER_DEBOUNCE_MS)
    }

    /** Filter immediately, dropping anything the search field had queued. */
    private fun applyFilterNow() {
        filterHandler.removeCallbacks(filterRunnable)
        filterPending = false
        applyFilter()
    }

    override fun onPause() {
        // Flush a queued pass rather than let it land behind another window. The
        // filter has to run either way — the grid and the count label must agree
        // with the text in the field when the user comes back — but its focus
        // half reads what is on screen, and from here what is on screen is
        // somebody else's window.
        if (filterPending) applyFilterNow()
        super.onPause()
    }

    override fun onDestroy() {
        // The debounce outlives a fast exit otherwise, and a Runnable holding
        // the activity is a leak plus a filter pass on a dead view tree.
        filterHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /**
     * Filter the catalogue into the grid, then settle focus after the layout
     * the filter scheduled.
     *
     * Focus is decided in [settleFilterFocus] and nowhere else. The rule set:
     *
     *  1. Typing never moves the cursor. While the search field owns focus,
     *     filtering only changes what is on screen.
     *  2. A grid that owns focus keeps it on the same icon, or on the nearest
     *     icon that survived. Left to the platform, RecyclerView's
     *     preserve-focus-after-layout pass finds the remembered item id gone
     *     and hands focus to its first focusable child, which on screen is the
     *     ring teleporting to the top-left tile.
     *  3. Hiding a container the cursor is inside leaves the cursor nowhere to
     *     live, and this screen hides one on every filter: the grid when the
     *     last result goes, the empty state when results come back — which is
     *     what pressing its own **Clear filter** does. Android either drops
     *     focus outright, in which case the window restores its own default on
     *     the next traversal (the Apply button at the top of this screen), or
     *     leaves it on a view that is no longer shown. Both are caught in
     *     [adoptDroppedFocus] and the cursor is put somewhere that can help.
     *  4. When the cursor is elsewhere, the grid scrolls to the top: the result
     *     set just changed, and the tail of the previous scroll position is not
     *     where anyone wants to look.
     */
    private fun applyFilter() {
        val q = query.trim().lowercase()
        val filtered = all.filter { item ->
            val catOk = inCategory(item)
            val qOk = q.isEmpty()
                || item.name.lowercase().contains(q)
                || item.drawable.contains(q)
            catOk && qOk
        }
        val grid = findViewById<RecyclerView>(R.id.grid)

        // Read from the grid rather than scanned for. getFocusedChild() is the
        // tile itself; the old loop asked every one of 943 view holders whether
        // it had focus, on every keystroke, to find the one that did.
        val gridOwnsFocus = grid.hasFocus()
        val focusedDrawable = if (gridOwnsFocus) {
            grid.focusedChild
                ?.let { grid.getChildAdapterPosition(it) }
                ?.takeIf { it != RecyclerView.NO_POSITION }
                ?.let(adapter::itemAt)
                ?.drawable
        } else {
            null
        }

        adapter.submit(filtered)
        findViewById<TextView>(R.id.count).text =
            if (filtered.size == all.size) {
                getString(R.string.pack_stats_fmt, iconCount, mappedComponents())
            } else {
                getString(R.string.icon_filter_fmt,
                    filtered.count { !it.alternate }, iconCount)
            }
        bindEmptyState(filtered.size, q)
        // The grid and the empty state just swapped places; down from the
        // filter row has to name whichever of them is actually on screen.
        syncFocusChain()

        // submit() only schedules the layout pass. A filtered-out tile is
        // detached — and its focus dropped — during that pass, so every focus
        // decision is taken after it rather than here.
        grid.post { settleFilterFocus(grid, filtered, focusedDrawable) }
    }

    /**
     * Post-layout half of [applyFilter]; see there for the rule set.
     *
     * The two [adoptDroppedFocus] calls look redundant and are not: hiding
     * either container can strand the cursor, and the call is a no-op unless it
     * was — which is what keeps rule 1 true.
     */
    private fun settleFilterFocus(
        grid: RecyclerView,
        filtered: List<IconAdapter.IconItem>,
        focusedDrawable: String?
    ) {
        when {
            focusedDrawable != null ->
                keepGridCursor(grid, filtered, focusedDrawable)

            filtered.isEmpty() ->
                adoptDroppedFocus(grid)

            else -> {
                grid.scrollToPosition(0)
                adoptDroppedFocus(grid)
            }
        }
    }

    /**
     * Keep the ring on the icon the user was on, or on the closest survivor.
     *
     * A tile that is already laid out takes focus directly, and RecyclerView
     * brings it fully on screen the way it does for any focus move. Scrolling to
     * it first — which is what this did before — pins the tile to the top of the
     * viewport, so every keystroke jumped the whole grid even when the icon
     * under the cursor had not moved at all.
     */
    private fun keepGridCursor(
        grid: RecyclerView,
        filtered: List<IconAdapter.IconItem>,
        drawable: String
    ) {
        val exact = filtered.indexOfFirst { it.drawable == drawable }
        val target = if (exact >= 0) exact else nearestSurvivor(filtered, drawable)
        if (target < 0) {
            adoptDroppedFocus(grid)
            return
        }
        val laid = grid.findViewHolderForAdapterPosition(target)
        if (laid != null) {
            laid.itemView.requestFocus()
            return
        }
        // Off screen: the tile does not exist yet, so it has to be laid out
        // before it can take focus. scrollToPosition only schedules that.
        grid.layoutManager?.scrollToPosition(target)
        grid.post {
            grid.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()
        }
    }

    /**
     * The filtered item closest to [drawable] in catalogue order, or -1.
     *
     * Reached when a keystroke drops the icon the cursor was on. "Nearest"
     * beats "first", which is what the platform's own recovery picks: an icon
     * two tiles away from where the user was reading is where they expect the
     * cursor to land, not the top-left of the grid.
     */
    private fun nearestSurvivor(
        filtered: List<IconAdapter.IconItem>,
        drawable: String
    ): Int {
        val anchor = catalogOrder[drawable] ?: return -1
        var best = -1
        var bestDistance = Int.MAX_VALUE
        filtered.forEachIndexed { index, item ->
            val at = catalogOrder[item.drawable] ?: return@forEachIndexed
            val distance = abs(at - anchor)
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    /**
     * Give a cursor with nowhere to live somewhere to live.
     *
     * Runs after every filter and does nothing unless focus was actually lost,
     * so it cannot interrupt typing or a D-pad move. Two ways it is lost here:
     * the window drops it outright, and then restores its own default on the
     * next traversal — the Apply button, two screens away from the results being
     * read — or [bindEmptyState] hides the container the cursor was inside,
     * which leaves focus on a view that is not shown: no highlight, key events
     * still landing on it, and the next D-pad move computed from a rectangle
     * that no longer exists.
     */
    private fun adoptDroppedFocus(grid: RecyclerView) {
        val current = currentFocus
        val lost = current == null || current === window.decorView || isStranded(current)
        if (!lost) return
        val target: View? = when {
            grid.visibility == View.VISIBLE ->
                grid.layoutManager?.findViewByPosition(0) ?: grid.getChildAt(0)
            else -> emptyActionView()
        }
        target?.requestFocus()
    }

    /**
     * Whether a focused view was hidden out from under the cursor.
     *
     * Asked only of the views [bindEmptyState] swaps — the grid, its tiles, and
     * the empty state's two undo buttons. `isShown` is false for a perfectly
     * healthy search field too while the activity sits behind another window,
     * and adopting focus then would move a cursor the user left exactly where
     * they left it.
     */
    private fun isStranded(focused: View): Boolean {
        if (focused.isShown) return false
        val grid = findViewById<RecyclerView>(R.id.grid)
        return focused === grid ||
            focused.id == R.id.empty_clear_search ||
            focused.id == R.id.empty_clear_filter ||
            grid.findContainingViewHolder(focused) != null
    }

    /** The empty state's undo, whichever of the two applies, or null. */
    private fun emptyActionView(): View? {
        val clearSearch = findViewById<View>(R.id.empty_clear_search)
        if (clearSearch.visibility == View.VISIBLE) return clearSearch
        val clearFilter = findViewById<View>(R.id.empty_clear_filter)
        if (clearFilter.visibility == View.VISIBLE) return clearFilter
        return null
    }

    /** Move the cursor to the empty state's undo, whether or not it was dropped. */
    private fun focusEmptyAction() {
        emptyActionView()?.requestFocus()
    }

    /**
     * A filter with no match used to render a blank grid with no message and no
     * way back except backspacing. Say what happened and offer the undo.
     */
    private fun bindEmptyState(shown: Int, q: String) {
        val empty = findViewById<View>(R.id.empty_state)
        val grid = findViewById<View>(R.id.grid)
        if (shown > 0) {
            empty.visibility = View.GONE
            grid.visibility = View.VISIBLE
            return
        }
        grid.visibility = View.GONE
        empty.visibility = View.VISIBLE

        findViewById<TextView>(R.id.empty_title).text =
            if (q.isEmpty()) {
                getString(R.string.empty_title_category_fmt, categoryLabel(category))
            } else {
                getString(R.string.empty_title_fmt, query.trim())
            }

        findViewById<TextView>(R.id.empty_body).text =
            if (q.isEmpty()) {
                getString(R.string.empty_body_plain)
            } else {
                getString(
                    R.string.empty_body_query_fmt,
                    categoryLabel(category),
                    all.count(::inCategory)
                )
            }

        // Offer whichever undo actually applies: the search, the filter, or both.
        val clearSearch = findViewById<TextView>(R.id.empty_clear_search)
        val clearFilter = findViewById<TextView>(R.id.empty_clear_filter)
        clearSearch.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
        clearFilter.visibility = if (category == ALL) View.GONE else View.VISIBLE
        clearFilter.text =
            getString(R.string.empty_clear_filter, all.count(::inCategory))
    }

    /**
     * Whether an item belongs to the selected chip.
     *
     * BESPOKE is not a category — it cuts across all of them — so every place
     * that asks "is this item in the current filter" has to go through here.
     * The empty state previously compared the synthetic key against
     * `item.category` directly, which is never equal, so a fruitless search
     * under Brandmarks offered to clear a filter holding 0 icons and labelled
     * it "All".
     */
    private fun inCategory(item: IconAdapter.IconItem): Boolean = when (category) {
        ALL -> true
        BESPOKE -> item.bespoke
        else -> item.category == category
    }

    private fun categoryLabel(key: String): String = when (key) {
        BESPOKE -> getString(R.string.chip_brandmarks)
        else -> CHIP_ORDER.firstOrNull { it.first == key }?.second
            ?: getString(R.string.chip_all)
    }

    private fun checkForUpdate() {
        UpdateChecker.check(this) { result ->
            when (result) {
                is UpdateChecker.Result.Available -> showUpdateAvailable(result)
                is UpdateChecker.Result.UpToDate -> { }
                is UpdateChecker.Result.Failed -> {
                    android.util.Log.w(
                        "CoreBuilds",
                        getString(R.string.update_failed_fmt, result.reason)
                    )
                }
            }
        }
    }

    private fun showUpdateAvailable(update: UpdateChecker.Result.Available) {
        // Later means later: the check can fire again on a resume, and a bar the
        // user dismissed must not come back and take the grid's row with it.
        if (updateDismissed) return
        pendingUpdate = update
        downloadedApk = null
        val bar = findViewById<LinearLayout>(R.id.update_bar)
        val label = findViewById<TextView>(R.id.update_label)
        val sub = findViewById<TextView>(R.id.update_sub)
        val button = findViewById<TextView>(R.id.update_button)
        bar.visibility = View.VISIBLE
        // The bar just joined the vertical chain; the edges that route around
        // it when it is hidden have to point at it now that it is not.
        syncFocusChain()
        label.text = getString(
            R.string.update_available_fmt, update.versionName, update.iconCount
        )
        // One line under the label, not two: the release's first highlight when
        // the manifest carries any, otherwise what Download does. Both is ~40dp
        // this screen does not have - the grid is the weighted remainder of a
        // 540dp panel and the tiles are the point of the screen.
        val highlights = findViewById<TextView>(R.id.update_highlights)
        if (update.highlights.isEmpty()) {
            highlights.visibility = View.GONE
            sub.visibility = View.VISIBLE
            sub.text = getString(R.string.update_sub_download)
        } else {
            sub.visibility = View.GONE
            highlights.visibility = View.VISIBLE
            highlights.text = "•  " + update.highlights.first()
        }
        // Give the bar the rows it outranks, so the grid keeps a full row of
        // tiles: the header's launcher list and the ALSO APPLIES TO band are
        // both secondary chrome, and Later puts them back. The icon count goes
        // too: the bar states the new release's count, and without that line
        // the rail's last row (Support | Settings | About) fits under the bar
        // at the default text size instead of being cut in half.
        findViewById<View>(R.id.apply_sub).visibility = View.GONE
        findViewById<View>(R.id.count).visibility = View.GONE
        setTargetsBand(shown = false)
        syncFocusChain()
        findViewById<TextView>(R.id.update_later).setOnClickListener { dismissUpdate() }
        button.isEnabled = true
        button.text = getString(R.string.update_download, update.versionName)
        button.setOnClickListener { startDownload(update) }
        // Hand the remote to the button. This used to be "reveal, do not
        // grab": an unconditional focus stole the cursor out of the search
        // field mid-word and closed the keyboard, because the check resolves
        // a second after launch - exactly when someone has reached the field
        // and started typing. The counter-case is a D-pad user who does not
        // know the bar is there: the bar sits between the apply button and
        // the chips, and "I keep pressing but nothing updates" is what that
        // looks like from the sofa (issue from a Shield, 2026-10). So the bar
        // now takes focus whenever it appears, with the one carve-out the
        // regression was about - never while the user is typing.
        val current = currentFocus
        val typingInSearch = current is EditText && current.id == R.id.search
            && current.text.isNotEmpty()
        if (!typingInSearch) {
            button.requestFocus()
        }
    }

    /**
     * Later: hide the bar for this session and give back the two rows it took,
     * so the grid returns to a full row of tiles. The manifest is still fetched
     * and [pendingUpdate] still set - About and Settings keep reporting the
     * update, and Download is one screen away.
     */
    private fun dismissUpdate() {
        updateDismissed = true
        findViewById<LinearLayout>(R.id.update_bar).visibility = View.GONE
        findViewById<View>(R.id.count).visibility = View.VISIBLE
        if (!pickMode) {
            // Recomputes the launcher list under the CTA and the ALSO APPLIES TO
            // band from what is installed, then resyncs the chain.
            bindApplyButton()
        }
        findViewById<View>(R.id.apply_button).requestFocus()
    }

    private fun startDownload(update: UpdateChecker.Result.Available) {
        if (updateDownloading) return
        if (update.apkUrl.isBlank()) {
            toast(getString(R.string.update_failed_fmt, "no apkUrl in version.json"))
            return
        }
        val label = findViewById<TextView>(R.id.update_label)
        val button = findViewById<TextView>(R.id.update_button)
        // A busy flag, not isEnabled = false. Disabling the focused view makes
        // Android clear its focus, and a TV is never in touch mode, so the
        // framework hands the cursor to the next focusable view: Later, one OK
        // press from dismissing the update. Every D-pad Download press ended
        // there, and nothing moved it back when the button came back as
        // Install (Shield report, updating 2.4.1 to 2.4.2). About's row has
        // always used a flag and never had the problem.
        updateDownloading = true
        button.text = getString(R.string.update_checking)
        UpdateInstaller.download(this, update.apkUrl, update.versionCode, update.apkSha256) { event ->
            when (event) {
                is UpdateInstaller.Event.Progress -> {
                    label.text = progressLabel(event.received, event.total)
                }
                is UpdateInstaller.Event.Ready -> {
                    updateDownloading = false
                    downloadedApk = event.file
                    installOffered = false
                    button.text = getString(R.string.update_install, update.versionName)
                    // The highlight line and this one share the row; the
                    // instruction wins once there is something to press.
                    findViewById<TextView>(R.id.update_highlights).visibility = View.GONE
                    findViewById<TextView>(R.id.update_sub).apply {
                        visibility = View.VISIBLE
                        text = getString(R.string.update_sub_install)
                    }
                    button.setOnClickListener { startInstall(event.file) }
                    startInstall(event.file)
                }
                is UpdateInstaller.Event.Failed -> {
                    updateDownloading = false
                    button.text = getString(R.string.update_download, update.versionName)
                    label.text = getString(R.string.update_failed_fmt, event.reason)
                    toast(getString(R.string.update_failed_fmt, event.reason))
                    button.setOnClickListener { startDownload(update) }
                }
            }
        }
    }

    private fun startInstall(file: File) {
        if (!UpdateInstaller.canInstall(this)) {
            installOffered = false
            // A toast is gone before the permission toggle has been found
            // and flipped, so the bar's sub-line carries the instruction
            // persistently: it is still there when the user is back. onResume
            // re-offers the install the moment the permission is granted, so
            // there is no second press to remember.
            findViewById<TextView>(R.id.update_highlights).visibility = View.GONE
            val sub = findViewById<TextView>(R.id.update_sub)
            sub.visibility = View.VISIBLE
            sub.text = getString(R.string.update_need_permission)
            toast(getString(R.string.update_need_permission))
            if (!UpdateInstaller.requestInstallPermission(this)) {
                toast(getString(R.string.update_permission_missing))
            }
            return
        }
        // Mark it offered before the installer opens: coming back from a
        // cancelled install fires onResume, which would otherwise launch it
        // again. The button stays live for a deliberate retry.
        installOffered = true
        try {
            UpdateInstaller.install(this, file)
        } catch (e: android.content.ActivityNotFoundException) {
            // No APK receiver on this build at all: the download is still
            // verified and on disk, but this intent will never resolve. Keep
            // the offer live and name the door that does work.
            installOffered = false
            toast(getString(R.string.update_no_installer))
        } catch (e: Exception) {
            // A refused start is not a verdict: keep the offer live so the
            // next press or resume tries again.
            installOffered = false
            toast(getString(R.string.update_failed_fmt, e.message ?: "installer refused"))
        }
    }

    private fun progressLabel(received: Long, total: Long): String {
        val rec = formatBytes(received)
        return if (total > 0) {
            getString(R.string.update_progress_of_fmt, rec, formatBytes(total))
        } else {
            getString(R.string.update_progress_fmt, rec)
        }
    }

    private fun formatBytes(n: Long): String = when {
        n >= 1_000_000 -> "%.1f MB".format(n / 1_000_000.0)
        n >= 1_000 -> "%.0f KB".format(n / 1_000.0)
        else -> "${n}B"
    }

    /**
     * Show or hide the ALSO APPLIES TO band as a unit.
     *
     * The band is the row's wrapper; the list inside it is the focus stop. The
     * wrapper carries the visibility so a hidden band takes its kicker with it,
     * and the list keeps `isFocusable` in sync in [syncFocusChain] so a hidden
     * one can never hold the cursor. [withLabel] is false in icon-picker mode,
     * where the same row carries the banner/square shape chips and picker_hint
     * is their label.
     */
    private fun setTargetsBand(shown: Boolean, withLabel: Boolean = true) {
        findViewById<View>(R.id.apply_targets_band).visibility =
            if (shown) View.VISIBLE else View.GONE
        findViewById<View>(R.id.apply_targets_label).visibility =
            if (shown && withLabel) View.VISIBLE else View.GONE
    }

    @Deprecated("Deprecated in AndroidX; the installer result still arrives here")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GlyphsCompanion.INSTALL_REQUEST) {
            onCompanionOutcome(GlyphsCompanion.onInstallResult(this))
            syncArtStyle()
        }
    }

    /** Finish the apply an Apply press asked for, or say it was declined. */
    private fun onCompanionOutcome(pending: GlyphsCompanion.Pending?) {
        when (pending) {
            is GlyphsCompanion.Pending.Ready ->
                (ApplyIconPack.installed(this).firstOrNull { it.key == pending.launcherKey }
                    ?: target)?.let { applyTo(it) }
            GlyphsCompanion.Pending.Declined -> toast(getString(R.string.glyphs_declined))
            null -> Unit
        }
    }

    /**
     * True when a pick's shape comes from the pack the launcher opened, not
     * from the art-style toggle: builds with a Glyphs companion. There the
     * icon pack answers with banners only and Core Builds Glyphs with
     * glyphs only, so choosing one app's icon can never flip the style the
     * whole launcher applies. The candidate build has no companion and keeps
     * its chips.
     */
    private fun pickFixedByPack(): Boolean = pickMode && GlyphsCompanion.supported()

    private fun pickHint(): String = getString(
        when {
            pickFixedByPack() && intent.getBooleanExtra(GlyphsCompanion.EXTRA_PICK_GLYPHS, false) ->
                R.string.picker_hint_glyphs_only
            pickFixedByPack() && pickBanners -> R.string.picker_hint_banners_only
            // No shape chips here, so point at the switch that sets the shape.
            pickFixedByPack() -> R.string.picker_hint_square_by_style
            pickBanners -> R.string.picker_hint_banner
            else -> R.string.picker_hint_square
        }
    )

    /**
     * The Art style row: the Banners/Glyphs switch, on the home screen next
     * to Apply because it decides what every app on the launcher looks like.
     * On (the default) is Banners. A press flips the style and re-applies to
     * the detected launcher, the same as Settings' row - fetching Core Builds
     * Glyphs first when it is not installed. Builds without a companion
     * switch the catalogue only.
     */
    /**
     * The Missing icons row's count, from the same [AppAudit] scan the
     * auditor lists, so the number on the home screen and the rows behind it
     * cannot disagree. Off the main thread on every resume: an app installed
     * since the last visit shows up without a restart, and the parsed
     * appfilter is cached for the process, so a repeat costs only the
     * PackageManager query. A failed scan leaves the row as a plain way in.
     * Two quick resumes start two scans; only the latest one may write, so
     * a slower, older scan cannot put a stale count back on the badge.
     */
    private var missingScanGeneration = 0

    private fun refreshMissingCount() {
        val sub = findViewById<TextView>(R.id.missing_entry_sub)
        val badge = findViewById<TextView>(R.id.missing_entry_count)
        val generation = ++missingScanGeneration
        Thread {
            val result = runCatching { AppAudit.scan(applicationContext) }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != missingScanGeneration) return@runOnUiThread
                val missing = result?.items?.size
                when {
                    missing == null -> {
                        sub.setText(R.string.missing_entry_failed)
                        badge.visibility = View.GONE
                    }
                    missing == 0 -> {
                        sub.setText(R.string.missing_entry_none)
                        badge.visibility = View.GONE
                    }
                    else -> {
                        sub.setText(R.string.missing_entry_sub)
                        badge.text = NumberFormat.getIntegerInstance().format(missing)
                        badge.contentDescription =
                            getString(R.string.missing_entry_count_cd_fmt, missing)
                        badge.visibility = View.VISIBLE
                    }
                }
            }
        }.start()
    }

    private fun bindStyleRow() {
        findViewById<View>(R.id.style_entry).setOnClickListener {
            Prefs.set(this, Prefs.KEY_PICK_BANNERS, !Prefs.pickerPrefersBanners(this))
            syncArtStyle()
            if (GlyphsCompanion.supported()) {
                val launcher = target
                if (launcher != null) applyTo(launcher)
                else toast(getString(R.string.projectivy_missing))
            }
        }
        showStyle()
    }

    private fun showStyle() {
        val banners = Prefs.pickerPrefersBanners(this)
        findViewById<SwitchCompat>(R.id.style_switch).isChecked = banners
        findViewById<TextView>(R.id.style_entry_sub).text = getString(
            if (banners) R.string.art_style_banners else R.string.art_style_glyphs
        )
    }

    /**
     * The catalogue and the Art style row follow the toggle, which Settings
     * can change and a failed Glyphs install can revert.
     */
    private fun syncArtStyle() {
        if (pickFixedByPack()) return
        if (!pickMode) showStyle()
        val preferBanners = Prefs.pickerPrefersBanners(this)
        if (pickBanners == preferBanners) return
        pickBanners = preferBanners
        adapter.setShowBanners(pickBanners)
        if (pickMode) findViewById<TextView>(R.id.picker_hint)?.text = pickHint()
    }

    private fun bindPickShape() {
        val hint = findViewById<TextView>(R.id.picker_hint)
        val targets = findViewById<RecyclerView>(R.id.apply_targets)
        if (pickFixedByPack()) {
            // One shape per pack: no chips to switch it.
            setTargetsBand(shown = false)
            syncFocusChain()
            return
        }
        val labels = listOf(
            getString(R.string.picker_chip_banner),
            getString(R.string.picker_chip_square)
        )
        val keys = listOf(PICK_BANNER, PICK_SQUARE)
        setTargetsBand(shown = true, withLabel = false)
        targets.layoutManager = LinearLayoutManager(
            this, LinearLayoutManager.HORIZONTAL, false
        )
        // See bindChips: a chip press that costs the chip its highlight reads as
        // the press not having landed.
        targets.itemAnimator = null
        targets.adapter = ChipAdapter(
            labels, keys, if (pickBanners) PICK_BANNER else PICK_SQUARE
        ) { key ->
            pickBanners = key == PICK_BANNER
            Prefs.set(this, Prefs.KEY_PICK_BANNERS, pickBanners)
            adapter.setShowBanners(pickBanners)
            hint.text = pickHint()
        }
        syncFocusChain()
    }

    private fun bindApplyButton() {
        val button = findViewById<TextView>(R.id.apply_button)
        val sub = findViewById<TextView>(R.id.apply_sub)
        val extras = findViewById<RecyclerView>(R.id.apply_targets)

        if (pickMode) {
            bindPickShape()
            return
        }
        // The update bar hides this line while it is up (see
        // showUpdateAvailable); binding re-establishes it, which is what makes
        // the bar's Later button able to give the row back.
        button.visibility = View.VISIBLE
        sub.visibility = View.VISIBLE

        // HOME first unless HOME cannot take an apply and another launcher can:
        // see ApplyIconPack.applyOrder.
        val installed = ApplyIconPack.applyOrder(this)
        val detected = installed.firstOrNull()
        target = detected

        if (detected == null) {
            button.text = getString(R.string.cta_no_launcher)
            sub.text = getString(R.string.cta_sub_no_launcher)
            extras.visibility = View.GONE
            button.setOnClickListener {
                toast(getString(R.string.projectivy_missing))
            }
            setTargetsBand(shown = false)
            syncFocusChain()
            return
        }

        // The sheet puts the word on the button and the launchers under it:
        // "Apply" over "Projectivy - Launchchair - Nova". The detected launcher
        // is still the one a press applies to; the list is the row of chips
        // below for the others.
        //
        // A launcher with no inbound apply says so on the button instead. Brand
        // Guide §05/§08 - the button names what the press will do - and on
        // Monet "Apply" has never done anything but show a toast, which reads
        // as a broken button rather than as a launcher that cannot be called.
        button.text = if (detected.inboundApply) {
            getString(R.string.cta_apply)
        } else {
            getString(R.string.cta_set_up_fmt, detected.displayName)
        }
        sub.text = installed.joinToString(" - ") { it.displayName }
        button.setOnClickListener { applyTo(detected) }

        val others = installed.drop(1)
        if (others.isEmpty()) {
            setTargetsBand(shown = false)
        } else {
            setTargetsBand(shown = true)
            extras.layoutManager = LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false
            )
            // See bindChips. This row's chips fire an apply rather than moving a
            // filter, but select() still runs and still rebinds two chips.
            extras.itemAnimator = null
            extras.adapter = ChipAdapter(
                others.map { it.displayName },
                others.map { it.key },
                selected = ""
            ) { key ->
                others.firstOrNull { it.key == key }?.let { applyTo(it) }
            }
        }
        syncFocusChain()
    }

    private fun applyTo(launcher: ApplyIconPack.Launcher) {
        when (val result = ApplyIconPack.apply(this, launcher)) {
            is ApplyIconPack.Result.Applied ->
                toast(getString(R.string.apply_handed_off_fmt, result.launcherName))

            is ApplyIconPack.Result.NotInstalled ->
                toast(getString(R.string.apply_not_installed_fmt, result.launcherName))

            is ApplyIconPack.Result.Manual -> {
                toast(
                    getString(
                        R.string.apply_manual_fmt,
                        result.launcherName,
                        result.instructions
                    )
                )
                ApplyIconPack.openLauncher(this, launcher)
            }

            is ApplyIconPack.Result.Handoff -> {
                // No inbound apply: nothing can be pressed for the user, so the
                // walk gets a screen of its own instead of a toast that expires
                // on the way to following it. The key travels, not the steps:
                // the screen rebuilds them from ApplyIconPack, so the two can
                // never disagree.
                startActivity(
                    Intent(this, LauncherSetupActivity::class.java)
                        .putExtra(LauncherSetupActivity.EXTRA_LAUNCHER, launcher.key)
                )
            }

            ApplyIconPack.Result.NeedsCompanion -> {
                // Glyphs is selected: fetch Core Builds Glyphs, and apply to
                // this launcher when the install lands (see onResume). If it
                // cannot be had the style is Banners again; show that.
                GlyphsCompanion.ensure(this, launcher.key) { syncArtStyle() }
            }
        }
    }

    /**
     * Columns for the glyph grid: the right pane's width over one tile's pitch.
     *
     * The pane is what the panel leaves after the gutters, the rail and the gap
     * between them; the pitch is cb_tile_icon plus the tile's vertical padding
     * twice - the tile's real height, and the width its near-square card wants.
     * Both come from dimens, and TvActivity normalises every panel to the
     * 960x540dp box before this runs, so the column count is the same on a
     * 1080p set and a 4K one: the proportions travel, the arithmetic does not
     * care which panel it runs on.
     */
    private fun spanForScreen(): Int {
        val density = resources.displayMetrics.density
        fun dpOf(id: Int) = resources.getDimensionPixelSize(id) / density
        val pane = resources.configuration.screenWidthDp -
            2 * dpOf(R.dimen.cb_gutter_side) -
            dpOf(R.dimen.cb_rail_width) - dpOf(R.dimen.cb_space_md)
        val pitch = dpOf(R.dimen.cb_tile_icon) + 2 * dpOf(R.dimen.cb_card_padding)
        return max(2, floor(pane / pitch).toInt())
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private const val ALL = "ALL"
        private const val BESPOKE = "BESPOKE"
        private const val PICK_BANNER = "PICK_BANNER"
        private const val PICK_SQUARE = "PICK_SQUARE"

        /**
         * How long a keystroke waits for the next one before the grid filters.
         *
         * Longer than a remote's key-repeat interval (~50ms) so a held key or a
         * fast burst produces one pass, shorter than the pause between words so
         * the results never look like they are behind the caret.
         */
        private const val FILTER_DEBOUNCE_MS = 120L
        private val CHIP_ORDER = listOf(
            "FILES" to "Files",
            "LIVE" to "Live TV",
            "STREAM" to "Streaming",
            "MEDIA" to "Media",
            "VOD" to "On demand",
            "PLAYER" to "Players",
            "MUSIC" to "Music",
            "SPORT" to "Sport",
            "TOOL" to "Tools",
            "STORE" to "Stores",
            "LAUNCHER" to "Launchers",
            "VPN" to "VPN",
            "GAMING" to "Gaming",
            "DEBRID" to "Debrid",
            "BROWSER" to "Browsers",
            "REMOTE" to "Remotes",
            "VIDEO" to "Video",
            "SYSTEM" to "System",
            "TRACK" to "Tracking",
            "CORE" to "Core Builds",
            "APP" to "Apps"
        )
    }
}
