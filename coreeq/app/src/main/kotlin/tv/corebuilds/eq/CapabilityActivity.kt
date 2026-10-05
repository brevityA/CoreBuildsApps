package tv.corebuilds.eq

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.DumpsysDiscovery
import tv.corebuilds.eq.apply.EffectLadder
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.update.UpdateChecker
import tv.corebuilds.eq.update.UpdateInstaller
import tv.corebuilds.eq.update.UpdatePrefs
import java.util.Locale
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
    private var lastBandCentres: List<Double>? = null

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

        setUpUpdateCard()
        showDiscoveryGrant()
        runCapabilityProbe()
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
        btnUpdateAuto.text = getString(
            if (updatePrefs.checksEnabled) R.string.update_auto_on else R.string.update_auto_off
        )
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
                lastBandCentres = if (verdict.platformEqualizerSupported) verdict.bandCentresHz else null
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
        val centres = lastBandCentres ?: listOf(60.0, 230.0, 910.0, 3600.0, 14000.0)
        // One home for the band maths (BandMapping): the export cannot drift
        // from what the service applies.
        val gains = BandMapping.gainsDb(active, centres)
        val sb = StringBuilder()
        sb.append("CORE EQ · TV SOUND SETTINGS REFERENCE\n")
        sb.append("Profile: ").append(active.name).append(" · ").append(mode.title).append("\n")
        sb.append("Target: ").append(active.target).append("\n")
        sb.append(String.format(Locale.US, "Bands: %s\n", if (lastBandCentres != null) "this TV's own equaliser" else "the common 5-band layout"))
        sb.append("------------------------------------\n")
        for (i in centres.indices) {
            val db = gains[i]
            val sign = if (db > 0) "+" else ""
            sb.append(String.format(Locale.US, "  %6.0f Hz : %s%.1f dB\n", centres[i], sign, db))
        }
        sb.append("------------------------------------\n")
        sb.append("Enter these values in the TV's own sound equaliser, at the nearest band it offers.\n")
        sb.append("Every band is lowered by the largest boost, so the correction cannot clip.\n")

        val result = Formats.exportToDevice(this, sb.toString(), "tv_settings_bands_${mode.key}.txt")
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val REQ_INSTALL = 9
    }
}
