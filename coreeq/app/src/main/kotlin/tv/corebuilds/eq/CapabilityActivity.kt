package tv.corebuilds.eq

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.dsp.TvSoundEq
import tv.corebuilds.eq.apply.DumpsysDiscovery
import tv.corebuilds.eq.apply.EffectLadder
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.apply.RuntimeCounters
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.export.ProfileDetails
import tv.corebuilds.eq.ui.DiagnosticsSummary
import tv.corebuilds.eq.ui.HomeSummary
import tv.corebuilds.eq.ui.QrBitmap
import tv.corebuilds.eq.ui.StatusFacts
import tv.corebuilds.eq.ui.showSwitch
import tv.corebuilds.eq.update.UpdateChecker
import tv.corebuilds.eq.update.UpdateInstaller
import tv.corebuilds.eq.update.UpdatePrefs
import kotlin.concurrent.thread

class CapabilityActivity : TvActivity() {

    private lateinit var profileStore: ProfileStore
    private lateinit var modeStore: ContentModeStore
    private lateinit var statusSession0: TextView
    private lateinit var statusDp: TextView
    private lateinit var statusPlatformEq: TextView
    private lateinit var statusDiscovery: TextView
    private lateinit var textDiscoveryCommand: TextView
    private lateinit var textVerdictBands: TextView
    private lateinit var textVerdictSession: TextView
    private lateinit var textVerdictGlobal: TextView
    private lateinit var btnExportTv: Button
    private lateinit var updatePrefs: UpdatePrefs
    private lateinit var textUpdateInstalled: TextView
    private lateinit var textUpdateStatus: TextView
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnInstallUpdate: Button
    private lateinit var btnUpdateAuto: Button
    private var pendingUpdate: UpdateChecker.Result.Available? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capability)

        profileStore = ProfileStore(this)
        modeStore = ContentModeStore(this)

        statusSession0 = findViewById(R.id.status_session0)
        statusDp = findViewById(R.id.status_dp)
        statusPlatformEq = findViewById(R.id.status_platform_eq)
        statusDiscovery = findViewById(R.id.status_discovery)
        textDiscoveryCommand = findViewById(R.id.text_discovery_command)
        textVerdictBands = findViewById(R.id.text_verdict_bands)
        textVerdictSession = findViewById(R.id.text_verdict_session)
        textVerdictGlobal = findViewById(R.id.text_verdict_global)
        btnExportTv = findViewById(R.id.btn_export_tv_settings)

        btnExportTv.setOnClickListener { exportForTvSettings() }
        findViewById<Button>(R.id.btn_open_support).setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }
        findViewById<Button>(R.id.btn_diagnostics).setOnClickListener { showDiagnostics(it) }

        setUpUpdateCard()
        showDiscoveryGrant()
        runCapabilityProbe()
    }

    private var diagnosticsOpen = false

    /** The diagnostics text for what this TV reports right now; built only on request. */
    internal fun diagnosticsText(now: Long = System.currentTimeMillis()): String {
        val store = ProfileStore(this)
        val snap = StatusFacts.read(this, store)
        val secondsSince = { ms: Long -> if (ms > 0L) (now - ms) / 1000 else null }
        val measured = store.lastMeasurement()
        return DiagnosticsSummary.text(
            DiagnosticsSummary.Facts(
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                buildType = BuildConfig.BUILD_TYPE,
                android = Build.VERSION.RELEASE ?: "unknown",
                sdkInt = Build.VERSION.SDK_INT,
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                profileKind = snap.profile?.let { ProfileDetails.kindOf(it) },
                profileOutput = snap.profile?.outputKind?.let { OutputRoute.label(it) },
                profileAgeSeconds = snap.profile?.timestampMs?.let(secondsSince),
                profileOrigin = snap.profile?.let { p ->
                    p.createdBy?.let { "${ProfileDetails.originLabel(p).lowercase()} ${it.label()}" }
                },
                outputInUse = snap.output?.kind?.let { OutputRoute.label(it) },
                outputEvidence = snap.output?.evidence?.name?.lowercase()?.replace('_', ' '),
                home = HomeSummary.of(snap.facts),
                lastStatus = snap.status?.message,
                lastStatusAgeSeconds = snap.status?.updatedMs?.let(secondsSince),
                lastStatusIsError = snap.status?.isError == true,
                measurement = measured?.let {
                    DiagnosticsSummary.Measurement(it.outcome, it.stage, it.message, secondsSince(it.atMs))
                },
                backgroundWork = RuntimeCounters.snapshot().summary()
            )
        )
    }

    /**
     * Preview first: the whole text is on screen before Copy or the QR code can
     * take it anywhere, and Core EQ itself sends nothing.
     */
    private fun showDiagnostics(returnFocus: View) {
        if (diagnosticsOpen) return
        diagnosticsOpen = true
        val body = diagnosticsText()
        val text = TextView(this).apply {
            this.text = body
            setTextColor(ContextCompat.getColor(this@CapabilityActivity, R.color.cb_ink))
            textSize = 14f
            setLineSpacing(0f, 1.15f)
        }
        val note = TextView(this).apply {
            setText(R.string.diagnostics_note)
            setTextColor(ContextCompat.getColor(this@CapabilityActivity, R.color.cb_slate))
            textSize = 13f
            setPadding(0, 0, 0, 12)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(note)
            addView(text)
        }
        // Focusable, so the remote scrolls the text line by line before choosing.
        val scroll = ScrollView(this).apply {
            isFocusable = true
            setPadding(48, 16, 48, 8)
            addView(column)
            contentDescription = body
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.diagnostics_title)
            .setView(scroll)
            .setPositiveButton(R.string.diagnostics_qr, null)
            .setNeutralButton(R.string.diagnostics_copy, null)
            .setNegativeButton(R.string.diagnostics_close, null)
            .setOnDismissListener {
                diagnosticsOpen = false
                if (returnFocus.isShown) returnFocus.requestFocus()
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val clipboard = getSystemService(ClipboardManager::class.java)
            if (clipboard == null) {
                note.setText(R.string.diagnostics_no_clipboard)
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("Core EQ diagnostics", body))
                note.setText(R.string.diagnostics_copied)
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { button ->
            val side = (resources.displayMetrics.heightPixels * 0.6f).toInt()
            val qr = runCatching { QrBitmap.render(body, side) }.getOrNull()
            if (qr == null) {
                note.setText(R.string.diagnostics_qr_too_long)
                return@setOnClickListener
            }
            column.removeView(text)
            if (column.findViewWithTag<View>(QR_TAG) == null) {
                column.addView(ImageView(this).apply {
                    tag = QR_TAG
                    setImageBitmap(qr)
                    adjustViewBounds = true
                    contentDescription = getString(R.string.diagnostics_qr_note)
                    layoutParams = LinearLayout.LayoutParams(side, side)
                })
            }
            note.setText(R.string.diagnostics_qr_note)
            button.isEnabled = false
            scroll.scrollTo(0, 0)
        }
        scroll.requestFocus()
    }

    /**
     * The Capability screen is where a user asks the device questions, so the
     * updater lives here too: it names the installed build, runs a check on
     * demand, and shows the exact refusal when the feed cannot be read. The
     * test package carries no feed at all, and says so instead of pretending a
     * check happened.
     */
    private fun setUpUpdateCard() {
        updatePrefs = UpdatePrefs(this)
        textUpdateInstalled = findViewById(R.id.text_update_installed)
        textUpdateStatus = findViewById(R.id.text_update_status)
        btnCheckUpdate = findViewById(R.id.btn_check_update)
        btnInstallUpdate = findViewById(R.id.btn_install_update)
        btnUpdateAuto = findViewById(R.id.btn_update_auto)

        textUpdateInstalled.text = getString(R.string.update_installed, BuildConfig.VERSION_NAME)
        val hasFeed = BuildConfig.UPDATE_MANIFEST_URL.isNotBlank()
        if (!hasFeed) {
            textUpdateStatus.text = getString(R.string.update_no_feed)
            btnCheckUpdate.isEnabled = false
            btnUpdateAuto.visibility = View.GONE
        } else {
            textUpdateStatus.text = ""
            refreshAutoButton()
            btnUpdateAuto.setOnClickListener {
                updatePrefs.checksEnabled = !updatePrefs.checksEnabled
                refreshAutoButton()
            }
            btnCheckUpdate.setOnClickListener { runUpdateCheck() }
        }
        btnInstallUpdate.setOnClickListener { startPendingUpdate() }
    }

    private fun refreshAutoButton() {
        btnUpdateAuto.showSwitch(updatePrefs.checksEnabled, R.string.update_auto_on, R.string.update_auto_off)
    }

    private fun runUpdateCheck() {
        textUpdateStatus.text = getString(R.string.update_checking)
        btnCheckUpdate.isEnabled = false
        UpdateChecker.check(this) { result ->
            if (isFinishing || isDestroyed) return@check
            btnCheckUpdate.isEnabled = true
            when (result) {
                is UpdateChecker.Result.Available -> {
                    pendingUpdate = result
                    textUpdateStatus.text = getString(R.string.update_available, result.versionName)
                    btnInstallUpdate.visibility = View.VISIBLE
                    btnInstallUpdate.requestFocus()
                }
                is UpdateChecker.Result.UpToDate -> {
                    pendingUpdate = null
                    btnInstallUpdate.visibility = View.GONE
                    textUpdateStatus.text = getString(R.string.update_up_to_date, result.versionName)
                }
                is UpdateChecker.Result.Failed -> {
                    pendingUpdate = null
                    btnInstallUpdate.visibility = View.GONE
                    textUpdateStatus.text = getString(R.string.update_failed, result.reason)
                }
            }
        }
    }

    private fun startPendingUpdate() {
        val update = pendingUpdate ?: return
        if (!UpdateInstaller.canInstall(this)) {
            UpdateInstaller.requestInstallPermission(this)
            textUpdateStatus.text = getString(R.string.update_permission_needed)
            return
        }
        btnInstallUpdate.isEnabled = false
        textUpdateStatus.text = getString(R.string.update_downloading, 0)
        UpdateInstaller.download(this, update.apkUrl, update.versionCode, update.apkSha256) { event ->
            if (isFinishing || isDestroyed) return@download
            when (event) {
                is UpdateInstaller.Event.Progress -> {
                    val percent = if (event.total > 0) {
                        ((event.received * 100) / event.total).toInt()
                    } else {
                        0
                    }
                    textUpdateStatus.text = getString(R.string.update_downloading, percent)
                }
                is UpdateInstaller.Event.Ready -> {
                    textUpdateStatus.text = getString(R.string.update_verifying)
                    btnInstallUpdate.isEnabled = true
                    updatePrefs.dismiss(update.versionCode)
                    UpdateInstaller.installForResult(this, event.file, REQ_INSTALL)
                }
                is UpdateInstaller.Event.Failed -> {
                    btnInstallUpdate.isEnabled = true
                    textUpdateStatus.text = getString(R.string.update_install_failed, event.reason)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_INSTALL || resultCode != RESULT_OK) return
        textUpdateStatus.text = getString(R.string.update_installed_done)
    }

    /**
     * Ladder rung 2 is optional and clearly labelled (plan §6): the row names
     * the grant state, and without the grant it shows the exact one-time
     * command rather than a dead end.
     */
    private fun showDiscoveryGrant() {
        val granted = DumpsysDiscovery.hasGrant(this)
        statusDiscovery.text = getString(
            if (granted) R.string.capability_discovery_granted else R.string.capability_discovery_denied
        )
        textDiscoveryCommand.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun runCapabilityProbe() {
        thread(name = "CapabilityProbeThread") {
            val verdict = EffectLadder.probe(this)
            runOnUiThread {
                statusSession0.text = if (verdict.session0Supported) {
                    "Supported on this hardware HAL"
                } else {
                    "Not supported (HAL rejects session 0)"
                }

                statusDp.text = if (verdict.dynamicsProcessingSupported) {
                    "API 28+ effect available; configuration is verified on apply, with Equalizer fallback."
                } else {
                    "Unavailable on this Android version; Equalizer fallback."
                }

                statusPlatformEq.text = if (verdict.platformEqualizerSupported) {
                    "${verdict.bandCount} bands · range ${verdict.minMillibel / 100} to +${verdict.maxMillibel / 100} dB"
                } else {
                    "Default 5-band layout"
                }

                textVerdictBands.text = "${verdict.bandCount} Equalizer fallback bands"
                val activePackages = if (EqService.running) modeStore.lastActivePackages() else emptySet()
                val unidentifiedPlayer = EqService.running && modeStore.lastActivePlayerUnknown()
                textVerdictSession.text = when {
                    !DumpsysDiscovery.hasGrant(this@CapabilityActivity) ->
                        getString(R.string.capability_app_identity_not_granted)
                    unidentifiedPlayer && activePackages.isNotEmpty() ->
                        getString(R.string.capability_app_identity_partial, activePackages.size)
                    activePackages.isNotEmpty() ->
                        getString(R.string.capability_app_identity_detected, activePackages.size)
                    unidentifiedPlayer ->
                        getString(R.string.capability_app_identity_unknown)
                    else -> getString(R.string.capability_app_identity_none)
                }
                textVerdictGlobal.text = "global mix: ${if (verdict.session0Supported) "supported" else "not supported"}"
            }
        }
    }

    private fun exportForTvSettings() {
        val output = OutputRoute.current(this)
        val profiles = profileStore.getAllProfiles()
        val base = OutputRoute.pick(
            profiles,
            profileStore.chosenId(),
            output?.kind,
            output?.name
        ).profile
        if (base == null) {
            val message = when {
                profiles.isEmpty() -> getString(R.string.no_profile_sub)
                output?.kind == null -> getString(R.string.output_profile_unknown_sub)
                else -> getString(R.string.output_profile_missing_sub, OutputRoute.label(output.kind))
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
        val mode = modeStore.currentDecision().mode
        val active = modeStore.effectiveProfile(base, mode)
        // 1.9.0: the values are sampled at each television equaliser's *own* centres, from the
        // curve the correction asks for (BandMapping.requestedGainsDb, which includes the room
        // correction, the manual layer and the mode's tone layer). A TV's equaliser has no
        // preamp, so the headroom shift the service applies would be wrong here: it would
        // slide every band down and leave the shape on a menu that cannot take negative gain.
        val settings = TvSoundEq.GRIDS.map { grid ->
            TvSoundEq.setting({ hz -> BandMapping.requestedGainsDb(active, listOf(hz))[0] }, grid)
        }
        val report = TvSoundEq.report("${active.name} · ${mode.title}", settings)
        val result = Formats.exportToDevice(this, report, "tv_sound_values_${mode.key}.txt")
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val REQ_INSTALL = 9
        const val QR_TAG = "diagnostics_qr"
    }
}
