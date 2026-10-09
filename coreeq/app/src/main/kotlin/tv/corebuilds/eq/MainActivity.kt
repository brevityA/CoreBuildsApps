package tv.corebuilds.eq

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.export.EqStatus
import tv.corebuilds.eq.export.PlatformBand
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.ui.BandSlidersView
import tv.corebuilds.eq.ui.CorrectionIndicator
import tv.corebuilds.eq.ui.CurveGraphView
import tv.corebuilds.eq.ui.OverlayPermission
import tv.corebuilds.eq.ui.QualityText
import tv.corebuilds.eq.ui.Series
import tv.corebuilds.eq.ui.correctionIndicator
import tv.corebuilds.eq.ui.showSwitch
import tv.corebuilds.eq.update.UpdateChecker
import tv.corebuilds.eq.update.UpdateInstaller
import tv.corebuilds.eq.update.UpdatePrefs
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : TvActivity() {

    private lateinit var profileStore: ProfileStore
    private lateinit var modeStore: ContentModeStore
    private lateinit var textProfileName: TextView
    private lateinit var textProfileSub: TextView
    private lateinit var textStatus: TextView
    private lateinit var btnToggle: Button
    private lateinit var graphHome: CurveGraphView
    private lateinit var bandsHome: BandSlidersView
    private lateinit var btnModes: Button
    private lateinit var badgeIndicator: View
    private lateinit var badgeDot: View
    private lateinit var badgeLabel: TextView
    private lateinit var updateBar: View
    private lateinit var updateBarText: TextView
    private lateinit var updateBarInstall: Button
    private lateinit var updatePrefs: UpdatePrefs
    private lateinit var cardPermissionBar: View
    private var pendingUpdate: UpdateChecker.Result.Available? = null
    private var updateChecked = false
    private var pulse: ObjectAnimator? = null
    private var lastIndicatorState: CorrectionIndicator? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadActiveProfile() // runtime effect bands are published with status changes
            refreshModeButton()
            refreshStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        profileStore = ProfileStore(this)
        modeStore = ContentModeStore(this)
        updatePrefs = UpdatePrefs(this)

        textProfileName = findViewById(R.id.text_active_profile_name)
        textProfileSub = findViewById(R.id.text_active_profile_sub)
        textStatus = findViewById(R.id.text_eq_status)
        btnToggle = findViewById(R.id.btn_toggle_correction)
        graphHome = findViewById(R.id.graph_home)
        bandsHome = findViewById(R.id.bands_home)
        btnModes = findViewById(R.id.btn_nav_modes)
        badgeIndicator = findViewById(R.id.badge_correction)
        badgeDot = findViewById(R.id.badge_dot)
        badgeLabel = findViewById(R.id.badge_label)
        updateBar = findViewById(R.id.update_bar)
        updateBarText = findViewById(R.id.update_bar_text)
        updateBarInstall = findViewById(R.id.update_bar_install)
        cardPermissionBar = findViewById(R.id.card_permission_bar)

        findViewById<Button>(R.id.btn_remeasure).setOnClickListener {
            startActivity(Intent(this, MeasureActivity::class.java))
        }
        btnToggle.setOnClickListener { toggleCorrection() }
        findViewById<Button>(R.id.btn_nav_manual_eq).setOnClickListener {
            startActivity(Intent(this, ManualEqActivity::class.java))
        }
        btnModes.setOnClickListener { startActivity(Intent(this, ModeActivity::class.java)) }
        findViewById<Button>(R.id.btn_nav_profiles).setOnClickListener {
            startActivity(Intent(this, ProfilesActivity::class.java))
        }
        findViewById<Button>(R.id.btn_nav_capability).setOnClickListener {
            startActivity(Intent(this, CapabilityActivity::class.java))
        }
        findViewById<Button>(R.id.btn_nav_display_calibration).setOnClickListener {
            startActivity(Intent(this, tv.corebuilds.eq.display.DisplayCalibrationActivity::class.java))
        }
        findViewById<Button>(R.id.btn_nav_content_type).setOnClickListener {
            startActivity(Intent(this, ContentTypeActivity::class.java))
        }
        findViewById<Button>(R.id.btn_nav_extras).setOnClickListener {
            startActivity(Intent(this, EnhancedAudioSettingsActivity::class.java))
        }
        updateBarInstall.setOnClickListener { startPendingUpdate() }
        findViewById<Button>(R.id.update_bar_later).setOnClickListener {
            pendingUpdate?.let { updatePrefs.dismiss(it.versionCode) }
            pendingUpdate = null
            updateBar.visibility = View.GONE
        }

        findViewById<Button>(R.id.card_permission_allow).setOnClickListener { allowCard(it) }
        findViewById<Button>(R.id.card_permission_later).setOnClickListener {
            profileStore.cardPromptDismissed = true
            cardPermissionBar.visibility = View.GONE
        }

        findViewById<Button>(R.id.btn_remeasure).requestFocus()
    }

    /**
     * Home's bar for the on-screen card (1.3.2): shown while the notice is on
     * and Android will not let Core EQ draw over other apps. Re-read on every
     * resume, so coming back from the permission screen hides it.
     */
    private fun refreshCardPermissionBar() {
        val visible = OverlayPermission.homePromptVisible(
            noticeOn = profileStore.announceApplied,
            allowed = OverlayPermission.allowed(this),
            dismissed = profileStore.cardPromptDismissed
        )
        cardPermissionBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Android's screen for the permission, or the ADB line where this TV has none. */
    private fun allowCard(allow: View) {
        if (OverlayPermission.open(this)) return
        findViewById<TextView>(R.id.card_permission_text).text =
            getString(R.string.profiles_card_no_settings, packageName)
        allow.visibility = View.GONE
        findViewById<Button>(R.id.card_permission_later).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refreshCardPermissionBar()
        ContextCompat.registerReceiver(
            this, statusReceiver, IntentFilter(EqService.ACTION_STATUS_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Correction that was on but is not running (a reboot Android would not
        // restart it after, or the system stopping it) resumes now that Core EQ
        // is on screen, which is the one moment Android allows it.
        if (profileStore.correctionEnabled && !EqService.running) {
            EqService.enable(this)?.let { profileStore.setStatus(it, isError = true) }
        }
        loadActiveProfile()
        refreshModeButton()
        refreshStatus()
        maybeCheckForUpdate()
    }

    /**
     * One check per app process, and only when the user has left checks on.
     * The result is a bar, never a download: nothing is fetched until Update is
     * pressed, and a version the user already pushed away does not come back.
     */
    private fun maybeCheckForUpdate() {
        if (updateChecked || !updatePrefs.checksEnabled) return
        if (pendingUpdate != null) return
        updateChecked = true
        UpdateChecker.check(this) { result ->
            if (isFinishing || isDestroyed) return@check
            when (result) {
                is UpdateChecker.Result.Available -> {
                    if (result.versionCode == updatePrefs.dismissedVersionCode()) return@check
                    pendingUpdate = result
                    val lead = result.highlights.firstOrNull()
                    updateBarText.text = if (lead.isNullOrBlank()) {
                        getString(R.string.update_available, result.versionName)
                    } else {
                        getString(R.string.update_available, result.versionName) + " · " + lead
                    }
                    updateBar.visibility = View.VISIBLE
                    updateBarInstall.requestFocus()
                }
                // Up to date and failures are both "nothing to say" on Home.
                // Capability names the reason when the user asks for a check.
                else -> Unit
            }
        }
    }

    /**
     * Download and verify the offered release, then hand it to the installer.
     * The bar's own line carries progress and every refusal in words; the
     * screen behind it stays usable, but this is deliberately the only thing
     * the update bar does.
     */
    private fun startPendingUpdate() {
        val update = pendingUpdate ?: return
        if (!UpdateInstaller.canInstall(this)) {
            UpdateInstaller.requestInstallPermission(this)
            updateBarText.text = getString(R.string.update_permission_needed)
            return
        }
        updateBarInstall.isEnabled = false
        updateBarText.text = getString(R.string.update_downloading, 0)
        UpdateInstaller.download(this, update.apkUrl, update.versionCode, update.apkSha256) { event ->
            if (isFinishing || isDestroyed) return@download
            when (event) {
                is UpdateInstaller.Event.Progress -> {
                    val percent = if (event.total > 0) {
                        ((event.received * 100) / event.total).toInt()
                    } else {
                        0
                    }
                    updateBarText.text = getString(R.string.update_downloading, percent)
                }
                is UpdateInstaller.Event.Ready -> {
                    updateBarText.text = getString(R.string.update_verifying)
                    updateBarInstall.isEnabled = true
                    updatePrefs.dismiss(update.versionCode)
                    UpdateInstaller.installForResult(this, event.file, REQ_INSTALL)
                }
                is UpdateInstaller.Event.Failed -> {
                    updateBarInstall.isEnabled = true
                    updateBarText.text = getString(R.string.update_install_failed, event.reason)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_INSTALL) return
        // RESULT_CANCELED also arrives when Android tears the activity down to
        // replace it, so silence here is correct in both readings: an installed
        // update restarts Core EQ by itself, and a declined one needs no note.
        if (resultCode == RESULT_OK) {
            updateBarText.text = getString(R.string.update_installed_done)
        }
    }

    override fun onPause() {
        stopPulse()
        unregisterReceiver(statusReceiver)
        super.onPause()
    }

    private fun toggleCorrection() {
        if (profileStore.correctionEnabled) {
            EqService.disable(this)
        } else {
            val refused = EqService.enable(this)
            if (refused != null) profileStore.setStatus(refused, isError = true)
            if (refused == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                // Optional: correction runs either way; the notification only reports it.
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
            }
        }
        refreshStatus()
    }

    private fun refreshModeButton() {
        val decision = modeStore.currentDecision()
        btnModes.text = getString(R.string.home_mode_button, decision.mode.title)
    }

    private fun refreshStatus() {
        val on = profileStore.correctionEnabled
        btnToggle.showSwitch(on, R.string.correction_on, R.string.correction_off)
        val status = profileStore.status()
        // The switch above already says Off; this line says what Off means
        // rather than repeating it. Errors always show, whatever the switch.
        textStatus.text = when {
            status?.isError == true -> status.message
            !on -> getString(R.string.status_correction_off)
            status == null -> getString(R.string.status_correction_starting)
            else -> status.message
        }
        textStatus.setTextColor(
            ContextCompat.getColor(this, if (status?.isError == true) R.color.cb_ember else R.color.cb_slate)
        )
        renderIndicator(status)
        // The status line has carried this caveat since 1.1.1; 1.3.0 also shows
        // it on its own line, because a bitstream output silently undoes everything.
        val passthrough = OutputRoute.mayPassThrough(this, OutputRoute.current(this)?.kind)
        findViewById<TextView>(R.id.text_passthrough_warning).visibility = if (passthrough) View.VISIBLE else View.GONE
    }

    /**
     * The playback-hint badge under the Correction switch. [LIVE][CorrectionIndicator.LIVE]
     * pulses when the output-mix effect is configured and Android reports
     * media playback. It does not prove that the stream traverses the effect.
     * A stale playing flag cannot light it: the badge trusts it only while
     * [EqService] is actually running.
     */
    private fun renderIndicator(status: EqStatus?) {
        val state = correctionIndicator(
            enabled = profileStore.correctionEnabled,
            playing = EqService.running && status?.playing == true,
            fault = status?.isError == true
        )
        if (state != lastIndicatorState) {
            lastIndicatorState = state
            if (state == CorrectionIndicator.OFF) {
                badgeIndicator.visibility = View.GONE
            } else {
                badgeIndicator.visibility = View.VISIBLE
                val (bgRes, tintRes, labelRes) = when (state) {
                    CorrectionIndicator.FAULT ->
                        Triple(R.drawable.bg_fault_badge, R.color.cb_ember, R.string.indicator_not_applied)
                    CorrectionIndicator.STANDBY ->
                        Triple(R.drawable.bg_idle_badge, R.color.cb_slate, R.string.indicator_standby)
                    CorrectionIndicator.LIVE ->
                        Triple(R.drawable.bg_live_badge, R.color.cb_success, R.string.indicator_live)
                    CorrectionIndicator.OFF -> return // handled above
                }
                badgeIndicator.setBackgroundResource(bgRes)
                val tint = ContextCompat.getColor(this, tintRes)
                badgeDot.setBackgroundColor(tint)
                badgeLabel.setTextColor(tint)
                badgeLabel.setText(labelRes)
            }
        }
        // The pulse is animation state, not style state: restore it on every
        // render, because returning to the screen stops it while the state
        // itself did not change.
        if (state == CorrectionIndicator.LIVE) startPulse() else stopPulse()
    }

    private fun startPulse() {
        if (pulse?.isRunning == true) return
        pulse = ObjectAnimator.ofFloat(badgeDot, View.ALPHA, 1f, 0.25f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        badgeDot.alpha = 1f
    }

    private fun loadActiveProfile() {
        val output = OutputRoute.current(this)
        val profiles = profileStore.getAllProfiles()
        val routed = OutputRoute.pick(
            profiles,
            profileStore.chosenId(),
            output?.kind,
            output?.name
        ).profile
        val profile = routed?.let { modeStore.effectiveProfile(it, modeStore.currentDecision().mode) }
        if (profile == null) {
            val anotherOutputHasProfile = profiles.isNotEmpty()
            textProfileName.text = getString(
                if (anotherOutputHasProfile) R.string.output_profile_missing_name else R.string.no_profile
            )
            textProfileSub.text = if (!anotherOutputHasProfile) {
                getString(R.string.no_profile_sub)
            } else if (output?.kind == null) {
                getString(R.string.output_profile_unknown_sub)
            } else {
                getString(R.string.output_profile_missing_sub, OutputRoute.label(output.kind))
            }
            findViewById<Button>(R.id.btn_remeasure).text = getString(R.string.action_measure)
            graphHome.setData(DoubleArray(0), emptyList(), if (anotherOutputHasProfile) "NO PROFILE FOR THIS OUTPUT" else "NO MEASUREMENT YET")
            bandsHome.setBands(emptyList())
            bandsHome.visibility = View.INVISIBLE
            return
        }
        findViewById<Button>(R.id.btn_remeasure).text = getString(
            if (profile.manualOnly) R.string.action_measure else R.string.action_remeasure
        )
        textProfileName.text = profile.name
        val outputLabel = profile.outputName
            ?: profile.outputKind?.let { OutputRoute.label(it) }
            ?: "any output"
        if (profile.manualOnly) {
            textProfileSub.text = "Manual EQ · no room measurement · ${profile.manualFilters.size} tone bands · $outputLabel"
        } else {
            val targetName = profile.target.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            val rt = profile.rt60Seconds?.let { String.format(Locale.US, "RT60 %.2f s", it) } ?: "RT60 unknown"
            val measurementLimit = if (profile.measurementNotes.isNotEmpty()) " · magnitude-only; phase unverified" else ""
            val manual = if (profile.manualFilters.isNotEmpty()) " · ${profile.manualFilters.size} tone bands" else ""
            // Quality leads on Home: the line can run to a third line and
            // ellipsize, and the score is the part worth keeping.
            textProfileSub.text = QualityText.prependTo(
                this,
                "$targetName · ${profile.filters.size} filters$manual · $rt · ${profile.micType} · $outputLabel$measurementLimit",
                profile.qualityScore
            )
        }

        if (profile.curve.isNotEmpty()) {
            val freqs = DoubleArray(profile.curve.size) { profile.curve[it].hz }
            val measured = DoubleArray(profile.curve.size) { profile.curve[it].measuredDb }
            val corr = DoubleArray(profile.curve.size) {
                val hz = profile.curve[it].hz
                profile.correctionAt(hz) + ManualEq.responseDb(profile.manualFilters, hz)
            }
            graphHome.setData(
                freqs = freqs,
                series = listOf(
                    Series("MEASURED", ContextCompat.getColor(this, R.color.cb_slate), measured),
                    Series("TOTAL EQ", ContextCompat.getColor(this, R.color.cb_signal_cyan), corr)
                ),
                title = "MEASURED vs TOTAL EQ",
                yRange = 20.0,
                tHz = profile.transitionHz
            )
        } else if (profile.manualOnly) {
            val freqs = DspConstants.ISO_CENTRES_HZ
                .filter { it in DspConstants.F_MIN..DspConstants.F_MAX }
                .toDoubleArray()
            val response = DoubleArray(freqs.size) { ManualEq.responseDb(profile.manualFilters, freqs[it]) }
            graphHome.setData(
                freqs = freqs,
                series = listOf(Series("MANUAL EQ", ContextCompat.getColor(this, R.color.cb_signal_cyan), response)),
                title = "MANUAL EQ · NOT ROOM-MEASURED",
                yRange = 12.0,
                tHz = profile.transitionHz
            )
        } else {
            graphHome.setData(DoubleArray(0), emptyList(), "NO MEASUREMENT DATA")
        }
        val runtimeBands = if (EqService.running) profileStore.runtimeBands() else emptyList()
        val profilePreview = if (profile.platformBands.isEmpty()) {
            emptyList()
        } else {
            val centres = profile.platformBands.map { it.centerHz }
            val gains = BandMapping.gainsDb(profile, centres)
            profile.platformBands.mapIndexed { index, band ->
                PlatformBand(band.centerHz, (gains[index] * 100.0).roundToInt())
            }
        }
        val shownBands = runtimeBands.ifEmpty { profilePreview }
        bandsHome.visibility = if (shownBands.isEmpty()) View.INVISIBLE else View.VISIBLE
        bandsHome.setBands(shownBands)
    }

    private companion object {
        const val REQ_NOTIFY = 7
        const val REQ_INSTALL = 8
    }
}
