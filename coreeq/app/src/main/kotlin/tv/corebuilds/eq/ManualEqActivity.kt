package tv.corebuilds.eq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.OpenableColumns
import android.os.Bundle
import android.os.Handler
import android.net.Uri
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.apply.BandMapping
import tv.corebuilds.eq.apply.DpBandLayout
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.apply.VolumeLevel
import tv.corebuilds.eq.dsp.CorrectionImport
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.HardwarePresets
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.ManualEqPreset
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.dsp.RoomAwareGuidance
import tv.corebuilds.eq.dsp.SmartSuggestion
import tv.corebuilds.eq.dsp.TvSoundEq
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.mode.ContentTypePrefs
import tv.corebuilds.eq.ui.CurveGraphView
import tv.corebuilds.eq.ui.EnhancedAudioPrefs
import tv.corebuilds.eq.ui.ManualEqBandsView
import tv.corebuilds.eq.ui.ManualEqReadout
import tv.corebuilds.eq.ui.Series
import java.util.Locale
import kotlin.concurrent.thread

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
    private var importing = false
    private lateinit var btnEditMode: Button
    private var profile: Profile? = null
    private var editingOutput: OutputRoute.Output? = null
    private var editingMode: ContentMode = ContentMode.EVERYDAY
    private var statusReceiverRegistered = false

    private lateinit var btnSuggestion: Button
    private lateinit var btnHints: Button
    private var hints: List<RoomAwareGuidance.Guidance> = emptyList()
    private var suggestion: SmartSuggestion.Suggestion? = null
    /** What the suggestion row replaced, while the same row can still put it back. */
    private var suggestionUndo: SuggestionUndo? = null
    /** Suggestions undone on this visit: not offered again until the screen reopens. */
    private val declinedSuggestions = mutableSetOf<String>()
    /** Set when Room hints sent a manual-only profile to Measure. */
    private var reloadProfileOnResume = false

    /** The undo belongs to one profile and one mode: it is never put back anywhere else. */
    private data class SuggestionUndo(
        val profileId: String,
        val mode: ContentMode,
        val presetId: String,
        val previousName: String,
        val previousFilters: List<PeakingFilter>
    )

    /**
     * The undo, if it belongs to the profile and mode on screen now. A restored undo
     * can outlive its context (playback may switch the mode, or the output the profile,
     * before the screen comes back); writing its curve into another mode would replace
     * a curve nobody asked to change, so it is dropped instead.
     */
    private fun currentUndo(): SuggestionUndo? {
        val undo = suggestionUndo ?: return null
        if (undo.profileId == profile?.id && undo.mode == editingMode) return undo
        suggestionUndo = null
        return null
    }

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
        restoreSuggestionState(savedInstanceState)
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
        btnSuggestion = findViewById(R.id.btn_manual_eq_suggestion)
        btnHints = findViewById(R.id.btn_manual_eq_hints)

        btnSuggestion.setOnClickListener { onSuggestionPressed() }
        btnSuggestion.setOnFocusChangeListener { _, focused -> if (focused) describeSuggestionRow() }
        btnHints.setOnClickListener { showRoomHints() }
        btnHints.setOnFocusChangeListener { _, focused -> if (focused) describeHintsRow() }
        btnEditMode.setOnClickListener { showEditingModePicker() }
        findViewById<Button>(R.id.btn_choose_eq_preset).setOnClickListener { showPresetPicker() }
        findViewById<Button>(R.id.btn_save_eq_preset).setOnClickListener { promptSavePreset() }
        findViewById<Button>(R.id.btn_import_eq_file).setOnClickListener { pickCorrectionFile() }
        findViewById<Button>(R.id.btn_manual_eq_tv_values).setOnClickListener { showTvValues() }
        findViewById<Button>(R.id.btn_reset_eq).setOnClickListener {
            applyPreset(ManualEq.BUILT_IN_PRESETS.first())
            textStatus.text = getString(R.string.manual_eq_reset_done)
        }
        findViewById<Button>(R.id.btn_manual_eq_done).setOnClickListener { finish() }

        bands.onBandSelected = { _, _ -> showReadout() }
        // Rows on the left borrow the readout line to explain themselves; coming
        // back to the bands puts the selected band's readout back.
        bands.setOnFocusChangeListener { _, focused -> if (focused) showReadout() }
        bands.onBandGainChanged = { index, gainDb ->
            if (profile != null) {
                modeStore.saveModeFilters(profile!!.id, editingMode, ManualEq.withBandGain(currentManualFilters(), index, gainDb))
                // A hand-moved band is a new decision; the suggestion's undo no longer describes it.
                suggestionUndo = null
                saveAndApplySoon()
                renderProfile()
            }
        }
        bands.setManualFilters(currentManualFilters())
        updatePresetLabel()
        renderProfile()
        focusOnOpen(bands)

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
        if (currentOutput != editingOutput || reloadProfileOnResume) {
            reloadProfileOnResume = false
            saveCurrentModeFilters()
            suggestionUndo = null
            profile = resolveProfileForCurrentOutput(currentOutput)
            bands.setManualFilters(currentManualFilters())
            renderProfile()
            EqService.send(this, EqService.ACTION_REAPPLY)
        }
        renderApplyHint()
        // What is playing and how loud may have changed while Core EQ was away.
        renderSmartRows()
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
            deviceName = DeviceName.current(),
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
        showReadout()
        updatePresetLabel()
        renderSmartRows()
    }

    /** The selected band in one line: its own gain, and what the room correction does there. */
    private fun showReadout() {
        val active = profile ?: return
        val index = bands.selectedBand
        val fc = ManualEq.BAND_CENTRES_HZ.getOrNull(index) ?: return
        val correction = if (active.manualOnly || active.curve.isEmpty()) null else active.correctionAt(fc)
        val together = ManualEqReadout.together(correction, currentManualFilters(), fc)
        textStatus.text = ManualEqReadout.line(index, bands.gainAt(index), correction, together)
    }

    /** The Room hints row and the suggestion row, re-read whenever the bands or the mode change. */
    private fun renderSmartRows() {
        if (!::btnHints.isInitialized) return
        val active = profile ?: return
        val measured = !active.manualOnly && active.curve.isNotEmpty()
        hints = if (measured) RoomAwareGuidance.guide(active, currentManualFilters()) else emptyList()
        btnHints.text = when {
            !measured -> getString(R.string.manual_eq_hints_measure)
            hints.isEmpty() -> getString(R.string.manual_eq_hints_none)
            else -> getString(R.string.manual_eq_hints_count, hints.size)
        }

        val undo = currentUndo()
        suggestion = if (undo == null) currentSuggestion() else null
        val pick = suggestion
        when {
            undo != null -> {
                btnSuggestion.text = getString(R.string.manual_eq_suggestion_undo, undo.previousName)
                btnSuggestion.visibility = View.VISIBLE
            }
            pick != null -> {
                btnSuggestion.text = getString(R.string.manual_eq_suggestion, pick.name)
                btnSuggestion.visibility = View.VISIBLE
            }
            else -> {
                // A row vanishing under the D-pad would leave focus nowhere.
                if (btnSuggestion.hasFocus()) btnHints.requestFocus()
                btnSuggestion.visibility = View.GONE
            }
        }
    }

    /**
     * The preset [SmartSuggestion] picks for what is playing now, or null. Only for the
     * mode in effect now (a suggestion about what is playing means nothing for a mode
     * that is not playing), only while the correction service is running (it is what
     * knows which app plays), and only over a shipped preset: a curve someone set or
     * saved by hand is theirs.
     */
    private fun currentSuggestion(): SmartSuggestion.Suggestion? {
        if (!EqService.running) return null
        // The A/B bypass plays the correction flat: a preset applied now could not be heard.
        if (profileStore.bypassEnabled) return null
        if (editingMode != modeStore.currentDecision().mode) return null
        val presets = profileStore.manualEqPresets()
        // A preset saved with a shipped preset's exact filters is still the person's own:
        // shipped presets come first in the list, so look at every match, not the first.
        val matches = presets.filter { ManualEq.sameFilters(currentManualFilters(), it.filters) }
        val shippedId = matches
            .takeIf { found -> found.none { it.id.startsWith(ManualEq.USER_PRESET_ID_PREFIX) } }
            ?.firstOrNull()?.id
            // A custom curve is never offered a preset, so skip the volume and content
            // reads below: this runs on every band step.
            ?: return null
        val typePrefs = getSharedPreferences(ContentTypePrefs.PREFS_NAME, MODE_PRIVATE)
        val contentType = ContentTypePrefs.pick(
            typePrefs.getBoolean(ContentTypePrefs.KEY_AUTO_SWITCH, true),
            typePrefs.getString(ContentTypePrefs.KEY_MANUAL_TYPE, null),
            modeStore.lastActivePackages()
        )
        val enhanced = EnhancedAudioPrefs(this)
        val layers = SmartSuggestion.Layers(
            autoLoudness = enhanced.autoLoudnessEnabled,
            lowVolumeBass = enhanced.lowVolumeBassEnabled,
            dialogueBoost = enhanced.dialogueBoostEnabled
        )
        val pick = SmartSuggestion.suggest(contentType, VolumeLevel.mediaDb(this), shippedId, layers) ?: return null
        if (pick.presetId in declinedSuggestions) return null
        return pick.takeIf { candidate -> presets.any { it.id == candidate.presetId } }
    }

    /**
     * The suggestion row's state survives the screen being recreated (a configuration
     * change, or Android reclaiming the process while another app is in front): the
     * applied preset is saved either way, so losing the undo would lose the way back
     * to the curve it replaced, and a declined suggestion would come back the same visit.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_DECLINED, ArrayList(declinedSuggestions))
        suggestionUndo?.let { undo ->
            outState.putString(STATE_UNDO_PROFILE, undo.profileId)
            outState.putString(STATE_UNDO_MODE, undo.mode.name)
            outState.putString(STATE_UNDO_PRESET, undo.presetId)
            outState.putString(STATE_UNDO_NAME, undo.previousName)
            outState.putDoubleArray(
                STATE_UNDO_FILTERS,
                undo.previousFilters.flatMap { listOf(it.fc, it.q, it.gain) }.toDoubleArray()
            )
        }
    }

    private fun restoreSuggestionState(state: Bundle?) {
        state ?: return
        state.getStringArrayList(STATE_DECLINED)?.let { declinedSuggestions += it }
        val presetId = state.getString(STATE_UNDO_PRESET) ?: return
        val values = state.getDoubleArray(STATE_UNDO_FILTERS) ?: return
        val profileId = state.getString(STATE_UNDO_PROFILE) ?: return
        val mode = state.getString(STATE_UNDO_MODE)?.let { name -> ContentMode.entries.firstOrNull { it.name == name } } ?: return
        // Kept as saved; currentUndo() drops it if the profile or mode on screen differs.
        suggestionUndo = SuggestionUndo(
            profileId = profileId,
            mode = mode,
            presetId = presetId,
            previousName = state.getString(STATE_UNDO_NAME).orEmpty(),
            previousFilters = values.toList().chunked(3).filter { it.size == 3 }.map { PeakingFilter(it[0], it[1], it[2]) }
        )
    }

    /** Apply the suggested preset, or put back what it replaced when the row reads Undo. */
    private fun onSuggestionPressed() {
        val active = profile ?: return
        val undo = currentUndo()
        if (undo != null) {
            modeStore.saveModeFilters(active.id, editingMode, undo.previousFilters)
            declinedSuggestions += undo.presetId
            suggestionUndo = null
            bands.setManualFilters(currentManualFilters())
            saveAndApplySoon()
            renderProfile()
            textStatus.text = getString(R.string.manual_eq_suggestion_undone, undo.previousName)
            return
        }
        val pick = suggestion ?: return
        // Playback, volume or mode may have moved since the row was drawn: apply only the
        // suggestion that is current, otherwise show the current one and wait for a press.
        if (currentSuggestion()?.presetId != pick.presetId) {
            renderSmartRows()
            return
        }
        val presets = profileStore.manualEqPresets()
        val preset = presets.firstOrNull { it.id == pick.presetId } ?: return
        val previous = currentManualFilters()
        val previousName = ManualEq.matchingPreset(previous, presets)?.name ?: "Custom"
        suggestionUndo = SuggestionUndo(active.id, editingMode, pick.presetId, previousName, previous)
        applyPreset(preset, fromSuggestion = true)
        textStatus.text = getString(R.string.manual_eq_suggestion_applied, preset.name, editingMode.title)
    }

    private fun describeSuggestionRow() {
        val undo = currentUndo()
        val pick = suggestion
        textStatus.text = when {
            undo != null -> getString(R.string.manual_eq_suggestion_undo_focus, undo.previousName)
            pick != null -> getString(R.string.manual_eq_suggestion_focus, pick.reason, pick.name, editingMode.title)
            else -> return
        }
    }

    private fun describeHintsRow() {
        val active = profile ?: return
        val measured = !active.manualOnly && active.curve.isNotEmpty()
        textStatus.text = getString(if (measured) R.string.manual_eq_hints_focus else R.string.manual_eq_hints_measure_focus)
    }

    /**
     * The hints, one row each with what it means underneath; pressing an actionable one
     * sets its band. A profile with no room measurement goes to Measure instead.
     */
    private fun showRoomHints() {
        val active = profile ?: return
        if (active.manualOnly || active.curve.isEmpty()) {
            // Coming back, the output may have a measured profile to edit instead.
            reloadProfileOnResume = true
            startActivity(Intent(this, MeasureActivity::class.java))
            return
        }
        val list = hints
        if (list.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.manual_eq_hints_none_title)
                .setMessage(
                    getString(
                        R.string.manual_eq_hints_none_message,
                        RoomAwareGuidance.hz(active.transitionHz),
                        RoomAwareGuidance.EASE_THRESHOLD_DB,
                        RoomAwareGuidance.MODAL_THRESHOLD_DB,
                        RoomAwareGuidance.STACKED_MIN_MANUAL_DB,
                        RoomAwareGuidance.STACKED_TOTAL_DB
                    )
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.manual_eq_hints_title, active.name))
            .setAdapter(twoLineRows(list.map { it.title }, list.map { it.detail })) { _, which ->
                applyHint(list[which])
            }
            .setNegativeButton(R.string.manual_eq_tv_values_close, null)
            .show()
    }

    private fun applyHint(hint: RoomAwareGuidance.Guidance) {
        val active = profile ?: return
        val index = hint.bandIndex
        val gain = hint.applyGainDb
        if (index == null || gain == null) {
            textStatus.text = getString(R.string.manual_eq_hint_advice)
            return
        }
        modeStore.saveModeFilters(active.id, editingMode, ManualEq.withBandGain(currentManualFilters(), index, gain))
        suggestionUndo = null
        bands.setManualFilters(currentManualFilters())
        // Land on the band that moved, so the bars and the readout show what changed.
        bands.selectBand(index)
        bands.requestFocus()
        saveAndApplySoon()
        renderProfile()
        textStatus.text = getString(
            R.string.manual_eq_hint_applied,
            RoomAwareGuidance.hz(ManualEq.BAND_CENTRES_HZ[index]),
            String.format(Locale.US, "%+.1f dB", ManualEq.gainAtBand(currentManualFilters(), index))
        )
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
                suggestionUndo = null
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
            .setSingleChoiceItems(presetRows(presets), selected) { dialog, which ->
                applyPreset(presets[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * One row per preset: the name in the list's own style and, underneath it, what the preset
     * actually does — a name like "Warm" is a mood, not a description, and a person choosing
     * blind on a TV ten feet away needs the second line.
     */
    private fun presetRows(presets: List<ManualEqPreset>): ArrayAdapter<String> =
        twoLineRows(presets.map { it.name }, presets.map { it.description })

    /** A list row with a title and, when there is one, a second line saying what it means. */
    private fun twoLineRows(titles: List<String>, details: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_2,
            android.R.id.text1,
            titles
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent)
                val second = row.findViewById<TextView>(android.R.id.text2)
                val detail = details.getOrElse(position) { "" }
                second.text = detail
                second.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE
                return row
            }
        }

    /**
     * The same curve as numbers a person can type into the TV's own sound menu.
     *
     * Core EQ cannot write a television's picture or sound settings without root, so the
     * honest hand-off is text: the values, which band the menu will not take, and which band
     * Core EQ has no opinion about at all. [TvSoundEq] does the maths; this only shows it.
     */
    private fun showTvValues() {
        val filters = currentManualFilters()
        val preset = ManualEq.matchingPreset(filters, profileStore.manualEqPresets())
        val settings = TvSoundEq.GRIDS.map { TvSoundEq.setting(filters, it) }
        if (settings.none { it.movesSomething }) {
            Toast.makeText(this, R.string.manual_eq_tv_values_empty, Toast.LENGTH_LONG).show()
            return
        }
        val title = preset?.name ?: getString(R.string.manual_eq_tv_values_title)
        val report = TvSoundEq.report(title, settings)
        val body = TextView(this).apply {
            text = report
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val scroller = ScrollView(this).apply { addView(body) }
        AlertDialog.Builder(this)
            .setTitle(R.string.manual_eq_tv_values_title)
            .setView(scroller)
            .setPositiveButton(R.string.manual_eq_tv_values_export) { _, _ ->
                val name = "tv_sound_values_${(preset?.id ?: "custom")}.txt"
                val result = Formats.exportToDevice(this, report, name)
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.manual_eq_tv_values_close, null)
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

    /** Import a Poweramp JSON preset or an AutoEq/Equalizer APO correction text file. */
    private fun pickCorrectionFile() {
        if (importing) return
        if (!tv.corebuilds.eq.ui.FilePicker.open(this, REQ_IMPORT_EQ)) {
            showImportError(getString(R.string.manual_eq_import_no_picker))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMPORT_EQ) return
        val uri = data?.data
        if (resultCode == RESULT_OK && uri != null) importCorrection(uri)
    }

    private fun importCorrection(uri: Uri) {
        if (importing) return
        importing = true
        textStatus.text = getString(R.string.manual_eq_import_busy)
        thread(name = "CoreEqCorrectionImport", start = true) {
            val outcome = try {
                val text = readCorrectionText(uri)
                Result.success(displayName(uri) to CorrectionImport.parseAny(text))
            } catch (e: Exception) {
                Result.failure<Pair<String, CorrectionImport.Result>>(e)
            }
            runOnUiThread {
                importing = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                outcome.onSuccess { (name, result) -> finishImport(name, result) }
                    .onFailure { error -> showImportError(error.message ?: getString(R.string.manual_eq_import_unreadable)) }
            }
        }
    }

    private fun readCorrectionText(uri: Uri): String {
        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException(getString(R.string.manual_eq_import_unreadable))
        // UTF-8 uses at most four bytes per character, so this bounds the read before parsing.
        val limit = CorrectionImport.MAX_CHARS * 4
        val bytes = input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
                if (out.size() > limit) {
                    throw IllegalArgumentException(getString(R.string.manual_eq_import_too_large))
                }
            }
            out.toByteArray()
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun displayName(uri: Uri): String {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return name ?: uri.lastPathSegment ?: "correction"
    }

    private fun finishImport(fileName: String, result: CorrectionImport.Result) {
        val base = result.presetName?.takeIf { it.isNotBlank() }
            ?: fileName.substringBeforeLast('.').trim().ifEmpty { "correction" }
        val name = "Import: $base".take(32).trim()
        val fit = String.format(Locale.US, "%.1f", result.maxErrorDb)
        val summary = getString(R.string.manual_eq_import_summary, result.source, fit, result.filters.size)
        val notes = result.notes.joinToString("\n")
        val message = if (notes.isEmpty()) "$summary\n\n${getString(R.string.manual_eq_import_no_notes)}" else
            "$summary\n\n${getString(R.string.manual_eq_import_notes_heading)}\n$notes"
        textStatus.text = getString(R.string.manual_eq_import_review_status)
        AlertDialog.Builder(this)
            .setTitle(R.string.manual_eq_import_preview_title)
            .setMessage(message)
            .setPositiveButton(R.string.manual_eq_import_apply) { _, _ ->
                val saved = try {
                    profileStore.saveManualEqPreset(name, result.filters)
                } catch (e: IllegalArgumentException) {
                    showImportError(e.message ?: getString(R.string.manual_eq_builtin_name))
                    return@setPositiveButton
                }
                applyPreset(saved)
                Toast.makeText(
                    this,
                    getString(R.string.manual_eq_import_applied, saved.name),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                textStatus.text = getString(R.string.manual_eq_import_cancelled)
            }
            .setOnCancelListener {
                textStatus.text = getString(R.string.manual_eq_import_cancelled)
            }
            .show()
    }

    private fun showImportError(reason: String) {
        textStatus.text = getString(R.string.manual_eq_import_failed, reason)
        AlertDialog.Builder(this)
            .setTitle(R.string.manual_eq_import_failed_title)
            .setMessage(reason)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun applyPreset(preset: ManualEqPreset, fromSuggestion: Boolean = false) {
        val active = profile ?: return
        // Any other preset is a new choice; only the suggestion row's own apply keeps its undo.
        if (!fromSuggestion) suggestionUndo = null
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
        const val REQ_IMPORT_EQ = 4101
        const val STATE_DECLINED = "manual_eq.declined_suggestions"
        const val STATE_UNDO_PROFILE = "manual_eq.undo_profile"
        const val STATE_UNDO_MODE = "manual_eq.undo_mode"
        const val STATE_UNDO_PRESET = "manual_eq.undo_preset"
        const val STATE_UNDO_NAME = "manual_eq.undo_name"
        const val STATE_UNDO_FILTERS = "manual_eq.undo_filters"
    }
}
