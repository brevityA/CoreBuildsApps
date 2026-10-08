package tv.corebuilds.eq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.apply.DumpsysDiscovery
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.ui.showSwitch

/** Remote-first controls for manual mode selection and best-effort app rules. */
class ModeActivity : TvActivity() {

    private lateinit var modeStore: ContentModeStore
    private lateinit var textCurrent: TextView
    private lateinit var textRules: TextView
    private lateinit var btnMovie: Button
    private lateinit var btnEveryday: Button
    private lateinit var btnGaming: Button
    private lateinit var btnAuto: Button
    private lateinit var btnOverridePolicy: Button
    private var statusReceiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            render()
        }
    }

    private data class AppChoice(val packageName: String, val label: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode)
        modeStore = ContentModeStore(this)

        textCurrent = findViewById(R.id.text_mode_current)
        textRules = findViewById(R.id.text_mode_rules)
        btnMovie = findViewById(R.id.btn_mode_movie)
        btnEveryday = findViewById(R.id.btn_mode_everyday)
        btnGaming = findViewById(R.id.btn_mode_gaming)
        btnAuto = findViewById(R.id.btn_auto_mode)
        btnOverridePolicy = findViewById(R.id.btn_mode_override_policy)

        btnMovie.setOnClickListener { selectMode(ContentMode.MOVIE_TV) }
        btnEveryday.setOnClickListener { selectMode(ContentMode.EVERYDAY) }
        btnGaming.setOnClickListener { selectMode(ContentMode.GAMING) }
        btnAuto.setOnClickListener {
            modeStore.setAutomaticSwitching(!modeStore.automaticSwitching())
            reapply()
            render()
        }
        btnOverridePolicy.setOnClickListener {
            modeStore.setStickyManualOverride(!modeStore.stickyManualOverride())
            render()
        }
        findViewById<Button>(R.id.btn_mode_manage_apps).setOnClickListener { showAppPicker() }
        findViewById<Button>(R.id.btn_mode_done).setOnClickListener { finish() }
        render()
        when (modeStore.currentDecision().mode) {
            ContentMode.MOVIE_TV -> btnMovie.requestFocus()
            ContentMode.EVERYDAY -> btnEveryday.requestFocus()
            ContentMode.GAMING -> btnGaming.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!statusReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                statusReceiver,
                IntentFilter(EqService.ACTION_STATUS_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            statusReceiverRegistered = true
        }
        render()
    }

    override fun onPause() {
        if (statusReceiverRegistered) {
            unregisterReceiver(statusReceiver)
            statusReceiverRegistered = false
        }
        super.onPause()
    }

    private fun selectMode(mode: ContentMode) {
        modeStore.selectMode(mode)
        reapply()
        render()
    }

    private fun reapply() {
        EqService.send(this, EqService.ACTION_REAPPLY)
    }

    private fun render() {
        if (!::modeStore.isInitialized) return
        val decision = modeStore.currentDecision()
        textCurrent.text = getString(R.string.mode_active_status, decision.mode.title, decision.reason)
        btnMovie.isActivated = decision.mode == ContentMode.MOVIE_TV
        btnEveryday.isActivated = decision.mode == ContentMode.EVERYDAY
        btnGaming.isActivated = decision.mode == ContentMode.GAMING
        btnAuto.showSwitch(modeStore.automaticSwitching(), R.string.mode_auto_on, R.string.mode_auto_off)
        btnOverridePolicy.setText(
            if (modeStore.stickyManualOverride()) R.string.mode_override_sticky else R.string.mode_override_temporary
        )
        textRules.text = buildString {
            append(getString(R.string.mode_app_rules_hint))
            val rules = modeStore.appRules()
            if (rules.isNotEmpty()) {
                append("\n\n").append(getString(R.string.mode_rules_app_rules)).append("\n")
                for ((pkg, mode) in rules.toSortedMap()) {
                    append(appLabel(pkg)).append(" → ").append(mode.title).append("\n")
                }
            }
            if (decision.conflictingPackages.isNotEmpty()) {
                append("\n").append(getString(R.string.mode_rules_conflict)).append("\n")
                decision.conflictingPackages.forEach { append(appLabel(it)).append("\n") }
            }
            val active = decision.activePackages
            if (active.isNotEmpty()) {
                append("\n").append(getString(R.string.mode_rules_playing)).append("\n")
                active.forEach { append(appLabel(it)).append("\n") }
            }
            if (decision.hasUnidentifiedPlayer) {
                append("\n").append(getString(R.string.mode_rules_unknown_player))
                if (modeStore.automaticSwitching() && decision.mode == ContentMode.EVERYDAY && decision.reason != "Manual override") {
                    append(getString(R.string.mode_rules_everyday_fallback))
                }
                append("\n")
            }
            if (!DumpsysDiscovery.hasGrant(this@ModeActivity)) {
                if (!endsWith("\n")) append("\n")
                append("\n").append(getString(R.string.mode_rules_no_dump))
            }
        }.trim()
    }

    private fun showAppPicker() {
        val apps = launchableApps()
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.mode_apps_empty, Toast.LENGTH_LONG).show()
            return
        }
        val rules = modeStore.appRules()
        val labels = apps.map { app ->
            val assigned = rules[app.packageName]?.let { " · ${it.title}" }.orEmpty()
            "${app.label}$assigned"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.mode_manage_apps)
            .setItems(labels) { dialog, index ->
                dialog.dismiss()
                showRuleModePicker(apps[index])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRuleModePicker(app: AppChoice) {
        val modes = ContentMode.entries.toMutableList()
        val existing = modeStore.appRules()[app.packageName]
        val labels = modes.map { it.title }.toMutableList()
        if (existing != null) labels += getString(R.string.mode_rule_remove)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.mode_rule_title, app.label))
            .setItems(labels.toTypedArray()) { _, index ->
                if (index < modes.size) modeStore.setAppRule(app.packageName, modes[index])
                else modeStore.setAppRule(app.packageName, null)
                reapply()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchableApps(): List<AppChoice> {
        val discovered = linkedMapOf<String, String>()
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (category in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            val resolved: List<ResolveInfo> = try {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(query, 0)
            } catch (_: Exception) {
                emptyList()
            }
            for (item in resolved) {
                val pkg = item.activityInfo?.packageName ?: continue
                if (pkg == packageName) continue
                val label = try {
                    item.loadLabel(packageManager).toString().trim().ifEmpty { pkg }
                } catch (_: Exception) {
                    pkg
                }
                discovered[pkg] = label
            }
        }
        // DUMP-discovered players may lack a TV launcher activity.
        val recentPlayers = if (EqService.running) modeStore.lastActivePackages() else emptySet()
        for (pkg in recentPlayers) {
            if (pkg == packageName || pkg in discovered) continue
            discovered[pkg] = appLabel(pkg)
        }
        for (pkg in modeStore.appRules().keys) {
            if (pkg !in discovered) discovered[pkg] = appLabel(pkg)
        }
        return discovered.map { (pkg, label) -> AppChoice(pkg, label) }
            .sortedWith(compareBy<AppChoice, String>(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.packageName })
    }

    private fun appLabel(pkg: String): String = try {
        @Suppress("DEPRECATION")
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        pkg
    }
}
