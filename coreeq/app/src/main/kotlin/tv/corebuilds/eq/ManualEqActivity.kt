package tv.corebuilds.eq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.DpBandLayout
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.HardwarePresets
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.ManualEqPreset
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.ui.CurveGraphView
import tv.corebuilds.eq.ui.ManualEqBandsView
import tv.corebuilds.eq.ui.Series
import java.util.Locale

/** TV-first manual graphic EQ editor layered on an optional room measurement. */
class ManualEqActivity : TvActivity() {

    private lateinit var profileStore: ProfileStore
    private lateinit var modeStore: ContentModeStore
    private lateinit var graph: CurveGraphView
    private lateinit var bands: ManualEqBandsView
    private lateinit var textProfile: TextView
    private lateinit var textProfileKicker: TextView
    private lateinit var textProfileSub: TextView
    private lateinit var textHeadroom: TextView
    private lateinit var textApplyHint: TextView
    private lateinit var textStatus: TextView
    private lateinit var btnPreset: Button
    private lateinit var btnEditMode: Button
    private var profile: Profile? = null
    private var editingOutput: OutputRoute.Output? = null
    private var editingMode: ContentMode = ContentMode.EVERYDAY
    private var statusReceiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            renderApplyHint()
        }
    }

    private val applyHandler = Handler(Looper.getMainLooper())
    private val applyRunnable = Runnable {
        saveCurrentModeFilters()
        EqService.send(this, EqService.ACTION_REAPPLY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_eq)

        profileStore = ProfileStore(this)
        modeStore = ContentModeStore(this)
        editingMode = modeStore.currentDecision().mode
        profile = resolveProfileForCurrentOutput(OutputRoute.current(this))

        graph = findViewById(R.id.graph_manual_eq)
        bands = findViewById(R.id.manual_eq_bands)
        textProfile = findViewById(R.id.text_manual_profile)
        textProfileKicker = findViewById(R.id.text_manual_profile_kicker)
        textProfileSub = findViewById(R.id.text_manual_profile_sub)
        textHeadroom = findViewById(R.id.text_manual_headroom)
        textApplyHint = findViewById(R.id.text_manual_eq_status)
        textStatus = findViewById(R.id.text_manual_selected_band)
        btnPreset = findViewById(R.id.btn_choose_eq_preset)
        btnEditMode = findViewById(R.id.btn_manual_eq_mode)

        btnEditMode.setOnClickListener { showEditingModePicker() }
        findViewById<Button>(R.id.btn_choose_eq_preset).setOnClickListener { showPresetPicker() }
        findViewById<Button>(R.id.btn_save_eq_preset).setOnClickListener { promptSavePreset() }
        findViewById<Button>(R.id.btn_reset_eq).setOnClickListener {
            applyPreset(ManualEq.BUILT_IN_PRESETS.first())
            textStatus.text = getString(R.string.manual_eq_reset_done)
        }
        findViewById<Button>(R.id.btn_manual_eq_done).setOnClickListener { finish() }

        bands.onBandDescriptionChanged = { textStatus.text = it }
        bands.onBandGainChanged = { index, gainDb ->
            if (profile != null) {
                modeStore.saveModeFilters(profile!!.id, editingMode, ManualEq.withBandGain(currentManualFilters(), index, gainDb))
                saveAndApplySoon()
                renderProfile()
                textStatus.text = bands.contentDescription
            }
        }
        bands.setManualFilters(currentManualFilters())
        updatePresetLabel()
        renderProfile()
        bands.requestFocus()

        if (EqService.running) EqService.send(this, EqService.ACTION_REAPPLY)
        if (profileStore.correctionEnabled && !EqService.running) {
            EqService.enable(this)?.let { message ->
                profileStore.setStatus(message, isError = true)
                textApplyHint.text = message
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(EqService.ACTION_STATUS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        statusReceiverRegistered = true
        val currentOutput = OutputRoute.current(this)
        if (currentOutput != editingOutput) {
            saveCurrentModeFilters()
            profile = resolveProfileForCurrentOutput(currentOutput)
            bands.setManualFilters(currentManualFilters())
            renderProfile()
            EqService.send(this, EqService.ACTION_REAPPLY)
        }
        renderApplyHint()
    }

    override fun onPause() {
        applyHandler.removeCallbacks(applyRunnable)
        saveCurrentModeFilters()
        EqService.send(this, EqService.ACTION_REAPPLY)
        if (statusReceiverRegistered) {
            unregisterReceiver(statusReceiver)
            statusReceiverRegistered = false
        }
        super.onPause()
    }

    private fun resolveProfileForCurrentOutput(output: OutputRoute.Output?): Profile {
        editingOutput = output
        val picked = OutputRoute.pick(
            profileStore.getAllProfiles(),
            profileStore.chosenId(),
            output?.kind,
            output?.name
        ).profile
        if (picked != null) return picked
        return createManualOnlyProfile(output)
    }

    private fun createManualOnlyProfile(output: OutputRoute.Output?): Profile {
        val now = System.currentTimeMillis()
        val displayOutput = output?.name ?: "Current output"
        return Profile(
            id = "manual-$now",
            name = "Manual EQ · $displayOutput",
            timestampMs = now,
            target = "flat",
            micType = "not measured",
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            stimulus = "manual_eq",
            captureSeconds = 0.0,
            outputKind = OutputRoute.keyFor(output),
            outputName = output?.name,
            manualOnly = true
        ).also { profileStore.saveProfile(it) }
    }

    private fun renderProfile() {
        val active = profile ?: return
        val effective = modeStore.effectiveProfile(active, editingMode)
        val outputLabel = active.outputName
            ?: active.outputKind?.let { OutputRoute.label(it) }
            ?: "current output"
        // The mode being edited is on the row below this card, so the card
        // names only the profile and what sits under the manual layer.
        textProfileKicker.setText(if (active.manualOnly) R.string.manual_profile_manual_only else R.string.manual_profile_room)
        textProfile.text = active.name
        textProfileSub.text = getString(
            if (active.manualOnly) R.string.manual_profile_manual_sub else R.string.manual_profile_room_sub,
            outputLabel
        )

        val frequencies: DoubleArray
        val series: List<Series>
        val title: String
        if (active.curve.isNotEmpty()) {
            frequencies = DoubleArray(active.curve.size) { active.curve[it].hz }
            val measured = DoubleArray(active.curve.size) { active.curve[it].measuredDb }
            val corrected = DoubleArray(frequencies.size) { index ->
                val hz = frequencies[index]
                effective.correctionAt(hz) + ManualEq.responseDb(effective.manualFilters, hz)
            }
            series = listOf(
                Series("MEASURED", ContextCompat.getColor(this, R.color.cb_slate), measured),
                Series("ROOM + ${editingMode.title.uppercase(Locale.US)}", ContextCompat.getColor(this, R.color.cb_signal_cyan), corrected)
            )
            title = "ROOM RESPONSE + ${editingMode.title.uppercase(Locale.US)} EQ"
        } else {
            frequencies = DspConstants.ISO_CENTRES_HZ
                .filter { it in DspConstants.F_MIN..DspConstants.F_MAX }
                .toDoubleArray()
            val response = DoubleArray(frequencies.size) { index ->
                effective.correctionAt(frequencies[index]) + ManualEq.responseDb(effective.manualFilters, frequencies[index])
            }
            series = listOf(Series("${editingMode.title.uppercase(Locale.US)} EQ", ContextCompat.getColor(this, R.color.cb_signal_cyan), response))
            title = "${editingMode.title.uppercase(Locale.US)} · NO ROOM MEASUREMENT"
        }
        graph.setData(
            frequencies,
            series,
            title = title,
            yRange = 15.0,
            tHz = active.transitionHz
        )

        val engineCentres = DpBandLayout.specs().map { it.centerHz }
        val reserveDb = BandMapping.headroomDb(effective, engineCentres)
        textHeadroom.text = getString(R.string.manual_eq_headroom, reserveDb)
        btnEditMode.text = getString(R.string.manual_eq_edit_mode, editingMode.title)
        bands.setManualFilters(currentManualFilters())
        renderApplyHint()
        textStatus.text = bands.contentDescription ?: getString(R.string.manual_eq_dpad_help)
        updatePresetLabel()
    }

    private fun renderApplyHint() {
        if (!::textApplyHint.isInitialized || !::profileStore.isInitialized) return
        val status = profileStore.status()
        textApplyHint.text = when {
            !profileStore.correctionEnabled -> getString(R.string.manual_eq_apply_hint)
            status?.isError == true -> status.message
            EqService.running -> getString(R.string.manual_eq_running_hint)
            else -> getString(R.string.manual_eq_resume_hint)
        }
    }

    private fun currentManualFilters(): List<PeakingFilter> =
        profile?.let { modeStore.modeFilters(it.id, editingMode) }.orEmpty()

    private fun saveCurrentModeFilters() {
        val active = profile ?: return
        modeStore.saveModeFilters(active.id, editingMode, currentManualFilters())
    }

    private fun updatePresetLabel() {
        val presetName = ManualEq.matchingPreset(currentManualFilters(), profileStore.manualEqPresets())?.name ?: "Custom"
        btnPreset.text = getString(R.string.manual_eq_choose_preset, presetName)
    }

    private fun showEditingModePicker() {
        val modes = ContentMode.entries.toTypedArray()
        val selected = modes.indexOf(editingMode)
        AlertDialog.Builder(this)
            .setTitle(R.string.manual_eq_mode_picker_title)
            .setSingleChoiceItems(modes.map { it.title }.toTypedArray(), selected) { dialog, which ->
                editingMode = modes[which]
                bands.setManualFilters(currentManualFilters())
                renderProfile()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** The hardware the current output is, when its name says so: see [HardwarePresets.detect]. */
    private fun detectedHardware(): HardwarePresets.Model? {
        val output = OutputRoute.current(this) ?: return null
        return HardwarePresets.detect(output.kind, output.name, OutputRoute.connectedNames(this, output.kind))
    }

    private fun showPresetPicker() {
        // A detected soundbar's presets lead the list; the rest stay below them.
        val detected = detectedHardware()
        val all = profileStore.manualEqPresets()
        val presets = if (detected == null) {
            all
        } else {
            val mine = all.filter { HardwarePresets.modelForPreset(it.id) == detected }
            mine + all.filterNot { it in mine }
        }
        val current = currentManualFilters()
        val selected = presets.indexOfFirst { ManualEq.sameFilters(it.filters, current) }
        val title = detected?.let { getString(R.string.manual_eq_presets_detected, it.name) }
            ?: getString(R.string.manual_eq_presets_title)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(presets.map { it.name }.toTypedArray(), selected) { dialog, which ->
                applyPreset(presets[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptSavePreset() {
        val input = EditText(this).apply {
            hint = getString(R.string.manual_eq_save_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            requestFocus()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.manual_eq_save_title)
            .setView(input)
            .setPositiveButton(R.string.manual_eq_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.manual_eq_preset_name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                try {
                    val saved = profileStore.saveManualEqPreset(name, currentManualFilters())
                    updatePresetLabel()
                    Toast.makeText(this, getString(R.string.manual_eq_preset_saved, saved.name), Toast.LENGTH_SHORT).show()
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(this, e.message ?: getString(R.string.manual_eq_builtin_name), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyPreset(preset: ManualEqPreset) {
        val active = profile ?: return
        modeStore.saveModeFilters(active.id, editingMode, ManualEq.sanitize(preset.filters))
        bands.setManualFilters(currentManualFilters())
        saveAndApplySoon()
        renderProfile()
        textStatus.text = getString(R.string.manual_eq_preset_applied, preset.name)
    }

    private fun saveAndApplySoon() {
        saveCurrentModeFilters()
        applyHandler.removeCallbacks(applyRunnable)
        applyHandler.postDelayed(applyRunnable, APPLY_DEBOUNCE_MS)
    }

    private companion object {
        const val APPLY_DEBOUNCE_MS = 160L
    }
}
