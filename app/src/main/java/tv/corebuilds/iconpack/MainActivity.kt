package tv.corebuilds.iconpack

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
            getString(R.string.icon_count_fmt, all.size)

        adapter = IconAdapter(all) { item -> onIconChosen(item) }
        findViewById<RecyclerView>(R.id.grid).apply {
            layoutManager = GridLayoutManager(this@MainActivity, spanForScreen())
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }

        if (pickMode) {
            findViewById<TextView>(R.id.picker_hint).visibility = View.VISIBLE
            findViewById<TextView>(R.id.picker_hint).text =
                getString(R.string.picker_hint_banner)
            findViewById<TextView>(R.id.apply_button).visibility = View.GONE
            findViewById<TextView>(R.id.apply_sub).visibility = View.GONE
            findViewById<LinearLayout>(R.id.update_bar).visibility = View.GONE
        }

        bindChips()
        bindSearch()
        if (pickMode) bindPickShape() else bindApplyButton()
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
        findViewById<RecyclerView>(R.id.chip_row).apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity, LinearLayoutManager.HORIZONTAL, false
            )
            adapter = ChipAdapter(labels, keys, ALL) { picked ->
                category = picked
                applyFilter()
            }
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
        adapter.submit(filtered)
        findViewById<TextView>(R.id.count).text =
            if (filtered.size == all.size) {
                getString(R.string.icon_count_fmt, all.size)
            } else {
                getString(R.string.icon_filter_fmt, filtered.size, all.size)
            }
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
        UpdateInstaller.download(this, update.apkUrl) { event ->
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
            "VIDEO" to "Video",
            "SYSTEM" to "System",
            "APP" to "Apps"
        )
    }
}
