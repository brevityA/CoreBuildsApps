package tv.corebuilds.iconpack

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Core Builds Icon Pack — front door.
 *
 * Brand Guide §05: every interactive element states what it will do BEFORE
 * it does it, and the receipts voice reports exactly what happened.
 */
class MainActivity : AppCompatActivity() {

    private var target: ApplyIconPack.Launcher? = null
    private var updateChecked = false
    private var pickMode = false
    private lateinit var all: List<IconAdapter.IconItem>
    private lateinit var adapter: IconAdapter
    private var category = ALL
    private var query = ""

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
            findViewById<TextView>(R.id.apply_button).visibility = View.GONE
            findViewById<TextView>(R.id.apply_sub).visibility = View.GONE
        }

        bindChips()
        bindSearch()
        bindApplyButton()
    }

    private fun onIconChosen(item: IconAdapter.IconItem) {
        if (!pickMode) {
            toast(getString(R.string.icon_selected_fmt, item.name, item.drawable))
            return
        }
        if (!IconPicker.deliver(this, item.drawable)) {
            toast(getString(R.string.picker_failed_fmt, item.name))
        }
    }

    override fun onResume() {
        super.onResume()
        bindApplyButton()
        if (!updateChecked) {
            updateChecked = true
            checkForUpdate()
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
                is UpdateChecker.Result.Available -> {
                    toast(getString(R.string.update_available_fmt,
                        result.versionName, result.iconCount))
                    findViewById<TextView>(R.id.count).apply {
                        text = getString(R.string.update_available_fmt,
                            result.versionName, result.iconCount)
                        setOnClickListener { openReleases(result.apkUrl) }
                    }
                }
                is UpdateChecker.Result.UpToDate -> { }
                is UpdateChecker.Result.Failed -> {
                    android.util.Log.w("CoreBuilds",
                        getString(R.string.update_failed_fmt, result.reason))
                }
            }
        }
    }

    private fun openReleases(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast(getString(R.string.update_failed_fmt, "no browser installed"))
        }
    }

    private fun bindApplyButton() {
        val button = findViewById<TextView>(R.id.apply_button)
        val sub = findViewById<TextView>(R.id.apply_sub)

        val detected = ApplyIconPack.detectInstalled(this)
        target = detected

        if (detected == null) {
            button.text = getString(R.string.cta_no_launcher)
            sub.text = getString(R.string.cta_sub_no_launcher)
            button.setOnClickListener {
                toast(getString(R.string.projectivy_missing))
            }
            return
        }

        button.text = getString(R.string.cta_apply_to_fmt, detected.displayName)
        sub.text = getString(R.string.cta_sub_apply_fmt, detected.displayName)
        button.setOnClickListener { applyTo(detected) }
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
