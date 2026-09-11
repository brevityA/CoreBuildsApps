package tv.corebuilds.iconpack

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * Front door. Apply targets the Home launcher. An update bar appears
 * when Latestrelease/version.json is newer; Download pulls the APK and
 * hands it to the system installer.
 */
class MainActivity : AppCompatActivity() {

    private var target: ApplyIconPack.Launcher? = null
    private var updateChecked = false
    private var pickMode = false
    private lateinit var all: List<IconAdapter.IconItem>
    private lateinit var adapter: IconAdapter
    private lateinit var chipAdapter: ChipAdapter
    private var category = ALL
    private var query = ""
    private var pickBanners = true
    private var pendingUpdate: UpdateChecker.Result.Available? = null
    private var downloadedApk: File? = null
    private var installOffered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pickMode = IconPicker.isPickRequest(intent)
        if (pickMode) {
            IconPicker.cancel(this)
        }

        val drawables = resources.getStringArray(R.array.icon_pack)
        val names = resources.getStringArray(R.array.icon_names)
        val cats = resources.getStringArray(R.array.icon_categories)
        all = drawables.indices.map { i ->
            IconAdapter.IconItem(
                drawable = drawables[i],
                name = names.getOrElse(i) { drawables[i] },
                category = cats.getOrElse(i) { "APP" }
            )
        }

        findViewById<TextView>(R.id.count).text =
            getString(R.string.icon_count_fmt, all.size, BuildConfig.VERSION_NAME)

        adapter = IconAdapter(all) { item -> onIconChosen(item) }
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
            val pickerHint = findViewById<TextView>(R.id.picker_hint)
            pickerHint.visibility = View.VISIBLE
            pickerHint.text = getString(R.string.picker_hint_banner)
            findViewById<TextView>(R.id.apply_button).visibility = View.GONE
            findViewById<TextView>(R.id.apply_sub).visibility = View.GONE
            findViewById<LinearLayout>(R.id.update_bar).visibility = View.GONE
        }

        bindChips()
        bindSearch()
        bindEmptyActions()
        if (pickMode) {
            // Icon-picker mode has no use for the wallpapers entry or apply.
            findViewById<View>(R.id.wallpapers_group).visibility = View.GONE
            bindPickShape()
        } else {
            bindApplyButton()
            // The button keeps the short title; the count moves to a subtitle.
            // Overwriting the label with the long sentence made the affordance
            // read as prose rather than as something you press.
            val wpEntry = findViewById<TextView>(R.id.wallpapers_entry)
            val wpSub = findViewById<TextView>(R.id.wallpapers_entry_sub)
            val wpCount = WallpaperCatalog.load(this).size
            // No subtitle rather than repeating the button's own label.
            wpSub.text = if (wpCount > 0) {
                getString(R.string.wp_entry_sub_fmt, wpCount)
            } else {
                ""
            }
            wpEntry.setOnClickListener { startActivity(Intent(this, WallpapersActivity::class.java)) }
        }

        // Give a physical remote a deterministic starting point. Without an
        // initial target Android may leave focus on the decor view, requiring
        // an extra key press before the first D-pad move does anything.
        window.decorView.post {
            if (pickMode) {
                val chips = findViewById<RecyclerView>(R.id.chip_row)
                chips.post {
                    val firstChip = chips.layoutManager?.findViewByPosition(0)
                    (firstChip ?: chips).requestFocus()
                }
            } else {
                findViewById<View>(R.id.apply_button).requestFocus()
            }
        }
    }

    private fun bindEmptyActions() {
        findViewById<TextView>(R.id.empty_clear_search).setOnClickListener {
            val search = findViewById<EditText>(R.id.search)
            search.setText("")
            query = ""
            applyFilter()
            search.requestFocus()
        }
        findViewById<TextView>(R.id.empty_clear_filter).setOnClickListener {
            // select() drives the chip highlight and calls back into
            // applyFilter through the same path a D-pad press would take.
            chipAdapter.select(ALL)
        }
    }

    private fun onIconChosen(item: IconAdapter.IconItem) {
        if (!pickMode) {
            toast(getString(R.string.icon_selected_fmt, item.name, item.drawable))
            return
        }
        val deliver = if (pickBanners) "${item.drawable}_banner" else item.drawable
        if (!IconPicker.deliver(this, deliver)) {
            toast(getString(R.string.picker_failed_fmt, item.name))
        }
    }

    override fun onResume() {
        super.onResume()
        if (!pickMode) {
            bindApplyButton()
            if (!updateChecked) {
                updateChecked = true
                checkForUpdate()
            }
            downloadedApk?.let { file ->
                if (file.exists() && UpdateInstaller.canInstall(this) && !installOffered) {
                    installOffered = true
                    startInstall(file)
                }
            }
        }
    }

    private fun bindChips() {
        val present = all.map { it.category }.toSet()
        val keys = mutableListOf(ALL)
        val labels = mutableListOf(getString(R.string.chip_all))
        for ((key, label) in CHIP_ORDER) {
            if (key in present) {
                keys += key
                labels += label
            }
        }
        chipAdapter = ChipAdapter(labels, keys, ALL) { picked ->
            category = picked
            applyFilter()
        }
        findViewById<RecyclerView>(R.id.chip_row).apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity, LinearLayoutManager.HORIZONTAL, false
            )
            adapter = chipAdapter
        }
    }

    private fun bindSearch() {
        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                applyFilter()
            }
        })
    }

    private fun applyFilter() {
        val q = query.trim().lowercase()
        val filtered = all.filter { item ->
            val catOk = category == ALL || item.category == category
            val qOk = q.isEmpty()
                || item.name.lowercase().contains(q)
                || item.drawable.contains(q)
            catOk && qOk
        }
        val grid = findViewById<RecyclerView>(R.id.grid)
        // Keep the remote cursor attached to the same icon when a query or
        // category changes. Losing focus after every keystroke is especially
        // disorienting on TV because there is no touch position to recover.
        val focusedDrawable = (0 until adapter.itemCount)
            .firstOrNull { position ->
                grid.findViewHolderForAdapterPosition(position)?.itemView?.hasFocus() == true
            }
            ?.let(adapter::itemAt)
            ?.drawable

        adapter.submit(filtered)
        if (focusedDrawable != null) {
            grid.post {
                val nextPosition = filtered.indexOfFirst { it.drawable == focusedDrawable }
                if (nextPosition >= 0) {
                    grid.layoutManager?.scrollToPosition(nextPosition)
                    grid.post {
                        grid.findViewHolderForAdapterPosition(nextPosition)
                            ?.itemView?.requestFocus()
                    }
                }
            }
        }
        findViewById<TextView>(R.id.count).text =
            if (filtered.size == all.size) {
                getString(R.string.icon_count_fmt, all.size, BuildConfig.VERSION_NAME)
            } else {
                getString(R.string.icon_filter_fmt, filtered.size, all.size)
            }
        bindEmptyState(filtered.size, q)
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
                    all.count { it.category == category }
                )
            }

        // Offer whichever undo actually applies: the search, the filter, or both.
        val clearSearch = findViewById<TextView>(R.id.empty_clear_search)
        val clearFilter = findViewById<TextView>(R.id.empty_clear_filter)
        clearSearch.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
        clearFilter.visibility = if (category == ALL) View.GONE else View.VISIBLE
        clearFilter.text =
            getString(R.string.empty_clear_filter, all.count { it.category == category })
    }

    private fun categoryLabel(key: String): String =
        CHIP_ORDER.firstOrNull { it.first == key }?.second ?: getString(R.string.chip_all)

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
        pendingUpdate = update
        downloadedApk = null
        val bar = findViewById<LinearLayout>(R.id.update_bar)
        val label = findViewById<TextView>(R.id.update_label)
        val sub = findViewById<TextView>(R.id.update_sub)
        val button = findViewById<TextView>(R.id.update_button)
        bar.visibility = View.VISIBLE
        label.text = getString(
            R.string.update_available_fmt, update.versionName, update.iconCount
        )
        sub.text = getString(R.string.update_sub_download)
        button.isEnabled = true
        button.text = getString(R.string.update_download, update.versionName)
        button.setOnClickListener { startDownload(update) }
        button.requestFocus()
    }

    private fun startDownload(update: UpdateChecker.Result.Available) {
        if (update.apkUrl.isBlank()) {
            toast(getString(R.string.update_failed_fmt, "no apkUrl in version.json"))
            return
        }
        val label = findViewById<TextView>(R.id.update_label)
        val button = findViewById<TextView>(R.id.update_button)
        button.isEnabled = false
        button.text = getString(R.string.update_checking)
        UpdateInstaller.download(this, update.apkUrl, update.versionCode, update.apkSha256) { event ->
            when (event) {
                is UpdateInstaller.Event.Progress -> {
                    label.text = progressLabel(event.received, event.total)
                }
                is UpdateInstaller.Event.Ready -> {
                    downloadedApk = event.file
                    installOffered = false
                    button.isEnabled = true
                    button.text = getString(R.string.update_install, update.versionName)
                    findViewById<TextView>(R.id.update_sub).text =
                        getString(R.string.update_sub_install)
                    button.setOnClickListener { startInstall(event.file) }
                    startInstall(event.file)
                }
                is UpdateInstaller.Event.Failed -> {
                    button.isEnabled = true
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
            toast(getString(R.string.update_need_permission))
            if (!UpdateInstaller.requestInstallPermission(this)) {
                toast(getString(R.string.update_permission_missing))
            }
            return
        }
        try {
            UpdateInstaller.install(this, file)
        } catch (e: Exception) {
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

    private fun bindPickShape() {
        val hint = findViewById<TextView>(R.id.picker_hint)
        val targets = findViewById<RecyclerView>(R.id.apply_targets)
        val labels = listOf(
            getString(R.string.picker_chip_banner),
            getString(R.string.picker_chip_square)
        )
        val keys = listOf(PICK_BANNER, PICK_SQUARE)
        targets.visibility = View.VISIBLE
        targets.layoutManager = LinearLayoutManager(
            this, LinearLayoutManager.HORIZONTAL, false
        )
        targets.adapter = ChipAdapter(labels, keys, PICK_BANNER) { key ->
            pickBanners = key == PICK_BANNER
            hint.text = if (pickBanners) {
                getString(R.string.picker_hint_banner)
            } else {
                getString(R.string.picker_hint_square)
            }
        }
    }

    private fun bindApplyButton() {
        val button = findViewById<TextView>(R.id.apply_button)
        val sub = findViewById<TextView>(R.id.apply_sub)
        val extras = findViewById<RecyclerView>(R.id.apply_targets)

        if (pickMode) {
            bindPickShape()
            return
        }

        val installed = ApplyIconPack.installed(this)
        val detected = installed.firstOrNull()
        target = detected

        if (detected == null) {
            button.text = getString(R.string.cta_no_launcher)
            sub.text = getString(R.string.cta_sub_no_launcher)
            extras.visibility = View.GONE
            button.setOnClickListener {
                toast(getString(R.string.projectivy_missing))
            }
            return
        }

        val home = ApplyIconPack.detectDefault(this)
        button.text = getString(R.string.cta_apply_to_fmt, detected.displayName)
        sub.text = if (home != null && installed.size > 1) {
            getString(R.string.cta_sub_home_fmt, detected.displayName)
        } else {
            getString(R.string.cta_sub_apply_fmt, detected.displayName)
        }
        button.setOnClickListener { applyTo(detected) }

        val others = installed.drop(1)
        if (others.isEmpty()) {
            extras.visibility = View.GONE
        } else {
            extras.visibility = View.VISIBLE
            extras.layoutManager = LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false
            )
            extras.adapter = ChipAdapter(
                others.map { getString(R.string.cta_apply_also_fmt, it.displayName) },
                others.map { it.key },
                selected = ""
            ) { key ->
                others.firstOrNull { it.key == key }?.let { applyTo(it) }
            }
        }
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
        }
    }

    private fun spanForScreen(): Int {
        val dp = resources.configuration.screenWidthDp
        return (dp / 148).coerceIn(3, 8)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private const val ALL = "ALL"
        private const val PICK_BANNER = "PICK_BANNER"
        private const val PICK_SQUARE = "PICK_SQUARE"
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
