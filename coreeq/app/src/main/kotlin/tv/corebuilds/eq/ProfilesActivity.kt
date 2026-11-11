package tv.corebuilds.eq

import android.os.Bundle
import android.content.Intent
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileDetails
import tv.corebuilds.eq.export.ProfileRouteStore
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.export.ProfileExchange
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.ui.ProfileRouteLine
import tv.corebuilds.eq.ui.CurveGraphView
import tv.corebuilds.eq.ui.OverlayPermission
import tv.corebuilds.eq.ui.QualityText
import tv.corebuilds.eq.apply.RuntimeTimings
import tv.corebuilds.eq.ui.ScreenTiming
import tv.corebuilds.eq.ui.Series
import tv.corebuilds.eq.ui.showSwitch
import java.util.Locale

class ProfilesActivity : TvActivity() {

    /** Profiles' first draw and readiness, kept apart ([ScreenTiming]). */
    private val screenTiming = ScreenTiming(RuntimeTimings.Op.PROFILES_FIRST_DRAW, RuntimeTimings.Op.PROFILES_READY)

    private lateinit var profileStore: ProfileStore
    private lateinit var modeStore: ContentModeStore
    private lateinit var recyclerProfiles: RecyclerView
    private lateinit var graphProfile: CurveGraphView
    private lateinit var textPreamp: TextView
    private lateinit var btnChipParametric: Button
    private lateinit var btnChipGraphicEq: Button
    private lateinit var btnChipJson: Button
    private lateinit var btnChipPoweramp: Button
    private lateinit var btnExport: Button
    private lateinit var btnDelete: Button
    private lateinit var btnAnnounce: Button
    private lateinit var textAnnounceCard: TextView
    private lateinit var btnCardSeconds: Button
    private lateinit var btnPlaybackHud: Button
    private lateinit var textPlaybackHud: TextView
    private lateinit var btnAllowOverlay: Button
    private lateinit var btnLearnedPrefs: Button
    private lateinit var btnDetails: Button
    private lateinit var btnRename: Button
    private lateinit var btnDuplicate: Button
    private lateinit var btnImport: Button
    private lateinit var textRoute: TextView
    /** One adapter for the screen's life, so a refresh keeps the list's scroll and focus. */
    private var adapter: ProfileAdapter? = null
    /** A dialog is up: a second press on the button behind it must not open another. */
    private var dialogOpen = false

    private var profilesList = mutableListOf<Profile>()
    private var selectedProfile: Profile? = null
    private var activeProfileId: String? = null
    private var selectedExportFormat = ExportFormat.PARAMETRIC

    private enum class ExportFormat {
        PARAMETRIC, GRAPHIC_EQ, JSON, POWERAMP_JSON
    }

    override fun onResume() {
        super.onResume()
        if (::profileStore.isInitialized) {
            loadProfiles()
            refreshOverlaySettings() // back from the overlay permission screen
            EqService.send(this, EqService.ACTION_REFRESH_PLAYBACK_HUD)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profiles)

        profileStore = ProfileStore(this)
        modeStore = ContentModeStore(this)

        recyclerProfiles = findViewById(R.id.recycler_profiles)
        graphProfile = findViewById(R.id.graph_profile)
        textPreamp = findViewById(R.id.text_preamp_display)
        btnChipParametric = findViewById(R.id.chip_parametric)
        btnChipGraphicEq = findViewById(R.id.chip_graphiceq)
        btnChipJson = findViewById(R.id.chip_json)
        btnChipPoweramp = findViewById(R.id.chip_poweramp_json)
        btnExport = findViewById(R.id.btn_export_profile)
        btnDelete = findViewById(R.id.btn_delete_profile)
        btnAnnounce = findViewById(R.id.btn_announce_applied)
        textAnnounceCard = findViewById(R.id.text_announce_card)
        btnCardSeconds = findViewById(R.id.btn_card_seconds)
        btnPlaybackHud = findViewById(R.id.btn_playback_hud)
        textPlaybackHud = findViewById(R.id.text_playback_hud)
        btnAllowOverlay = findViewById(R.id.btn_allow_overlay)
        btnLearnedPrefs = findViewById(R.id.btn_learned_prefs)
        btnDetails = findViewById(R.id.btn_profile_details)
        btnRename = findViewById(R.id.btn_profile_rename)
        btnDuplicate = findViewById(R.id.btn_profile_duplicate)
        btnImport = findViewById(R.id.btn_import_profile)
        textRoute = findViewById(R.id.text_profile_route)

        recyclerProfiles.layoutManager = LinearLayoutManager(this)
        screenTiming.watchFirstDraw(window.decorView)
        // Ready: the list has been laid out with its rows, or found empty.
        screenTiming.readyAfterLayout(recyclerProfiles) {
            val count = recyclerProfiles.adapter?.itemCount ?: 0
            recyclerProfiles.adapter != null && (count == 0 || recyclerProfiles.childCount > 0)
        }

        setupExportChips()
        loadProfiles()

        btnExport.setOnClickListener { exportCurrentProfile() }
        btnImport.setOnClickListener {
            if (!tv.corebuilds.eq.ui.FilePicker.open(this, REQ_IMPORT_PROFILE)) {
                Toast.makeText(this, "No file picker is available.", Toast.LENGTH_LONG).show()
            }
        }
        btnDelete.setOnClickListener { confirmDelete() }
        btnDetails.setOnClickListener { showDetails() }
        btnRename.setOnClickListener { showRename() }
        btnDuplicate.setOnClickListener { duplicateSelected() }
        btnAnnounce.setOnClickListener {
            profileStore.announceApplied = !profileStore.announceApplied
            refreshOverlaySettings()
        }
        btnCardSeconds.setOnClickListener {
            // 1 to 10 s, then back to 1: one press per second, no keyboard needed.
            val next = profileStore.cardSeconds % ProfileStore.CARD_SECONDS_RANGE.last + 1
            profileStore.cardSeconds = next
            refreshOverlaySettings()
        }
        btnPlaybackHud.setOnClickListener {
            profileStore.playbackHudEnabled = !profileStore.playbackHudEnabled
            refreshOverlaySettings()
            EqService.send(this, EqService.ACTION_REFRESH_PLAYBACK_HUD)
        }
        btnAllowOverlay.setOnClickListener { openOverlaySettings() }
        btnLearnedPrefs.setOnClickListener { showLearnedPrefsDialog() }
        findViewById<Button>(R.id.btn_test_card).setOnClickListener { showTestCard() }
        refreshOverlaySettings()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMPORT_PROFILE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val stream = contentResolver.openInputStream(uri) ?: error("Cannot open this file.")
            val bytes = stream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    out.write(buffer, 0, count)
                    require(out.size() <= ProfileExchange.MAX_CHARS * 4) { "Profile file is too large." }
                }
                out.toByteArray()
            }
            val imported = ProfileExchange.parse(String(bytes, Charsets.UTF_8))
            val profile = imported.profile
            AlertDialog.Builder(this)
                .setTitle("Import Core EQ profile?")
                .setMessage("${profile.name}\n${profile.filters.size} room filters · ${profile.manualFilters.size} manual filters · ${imported.overlays.size} mode overlays\n\nThis imports settings and measurement provenance. It does not validate that the profile matches this device or output.")
                .setPositiveButton("Import") { _, _ ->
                    profileStore.saveProfile(profile, setAsActive = true)
                    imported.overlays.forEach { (mode, filters) -> modeStore.saveModeFilters(profile.id, mode, filters) }
                    EqService.send(this, EqService.ACTION_REAPPLY)
                    Toast.makeText(this, "Imported ${profile.name}", Toast.LENGTH_LONG).show()
                    loadProfiles()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Could not import profile.", Toast.LENGTH_LONG).show()
        }
    }

    /** Describe both independent overlay features and share Android's overlay permission. */
    private fun refreshOverlaySettings() {
        val announce = profileStore.announceApplied
        val playbackHud = profileStore.playbackHudEnabled
        btnAnnounce.showSwitch(announce, R.string.profiles_announce_on, R.string.profiles_announce_off)
        btnPlaybackHud.showSwitch(playbackHud, R.string.profiles_playback_hud_on, R.string.profiles_playback_hud_off)
        val allowed = OverlayPermission.allowed(this)
        val seconds = profileStore.cardSeconds
        btnCardSeconds.text = getString(R.string.profiles_card_seconds, seconds)
        btnCardSeconds.visibility = if (announce) View.VISIBLE else View.GONE
        textAnnounceCard.text = when {
            !announce -> getString(R.string.profiles_card_off)
            allowed -> getString(R.string.profiles_card_allowed, seconds)
            else -> getString(R.string.profiles_card_needs_permission)
        }
        textPlaybackHud.setText(
            when {
                !playbackHud -> R.string.profiles_playback_hud_off_note
                allowed -> R.string.profiles_playback_hud_allowed
                else -> R.string.profiles_playback_hud_needs_permission
            }
        )
        btnAllowOverlay.visibility = if ((announce || playbackHud) && !allowed) View.VISIBLE else View.GONE
    }

    /**
     * Ask the running service for the card it would show now, so its place and
     * size can be checked on this TV. Without the overlay permission the same
     * words arrive as a toast, which is what this TV would show as well.
     */
    private fun showTestCard() {
        if (!EqService.running) {
            textAnnounceCard.setText(R.string.profiles_card_test_off)
            return
        }
        refreshOverlaySettings()
        EqService.send(this, EqService.ACTION_SHOW_CARD)
    }

    /** Show what Core EQ has learned: the profile it prefers for each output, with an option to clear all. */
    private fun showLearnedPrefsDialog() {
        val store = ProfileRouteStore.of(this)
        val all = store.allPreferences()
        val profiles = profileStore.getAllProfiles().associateBy { it.id }
        val lines = all.map { (outputKey, profileId) ->
            val label = when {
                outputKey.contains("|") -> {
                    val parts = outputKey.split("|")
                    OutputRoute.label(parts.firstOrNull())
                }
                else -> outputKey
            }
            val profileName = profiles[profileId]?.name ?: profileId
            "$label: $profileName"
        }
        val message = if (lines.isEmpty()) {
            getString(R.string.profiles_learned_prefs_none)
        } else {
            lines.joinToString("\n")
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.profiles_learned_prefs_title)
            .setMessage(message)
            .setPositiveButton(R.string.profiles_learned_prefs_clear_all) { _, _ ->
                store.clear() // ProfileRouteStore.clear() removes all keys
                Toast.makeText(this, "Learned preferences cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Android's own "Display over other apps" screen for Core EQ, or the ADB line where a TV has none. */
    private fun openOverlaySettings() {
        if (!OverlayPermission.open(this)) {
            val command = getString(R.string.profiles_card_no_settings, packageName)
            textAnnounceCard.text = command
            if (profileStore.playbackHudEnabled) textPlaybackHud.text = command
        }
    }

    private fun setupExportChips() {
        btnChipParametric.setOnClickListener {
            selectedExportFormat = ExportFormat.PARAMETRIC
            updateChipStates()
            selectedProfile?.let { refreshPreampDisplay(it) }
        }
        btnChipGraphicEq.setOnClickListener {
            selectedExportFormat = ExportFormat.GRAPHIC_EQ
            updateChipStates()
            selectedProfile?.let { refreshPreampDisplay(it) }
        }
        btnChipJson.setOnClickListener {
            selectedExportFormat = ExportFormat.JSON
            updateChipStates()
            selectedProfile?.let { refreshPreampDisplay(it) }
        }
        btnChipPoweramp.setOnClickListener {
            selectedExportFormat = ExportFormat.POWERAMP_JSON
            updateChipStates()
            selectedProfile?.let { refreshPreampDisplay(it) }
        }
        updateChipStates()
    }

    private fun updateChipStates() {
        btnChipParametric.isActivated = (selectedExportFormat == ExportFormat.PARAMETRIC)
        btnChipGraphicEq.isActivated = (selectedExportFormat == ExportFormat.GRAPHIC_EQ)
        btnChipJson.isActivated = (selectedExportFormat == ExportFormat.JSON)
        btnChipPoweramp.isActivated = (selectedExportFormat == ExportFormat.POWERAMP_JSON)
    }

    private fun loadProfiles() {
        profilesList.clear()
        profilesList.addAll(profileStore.getAllProfiles())
        val output = OutputRoute.current(this)
        val routeMatch = OutputRoute.pick(
            profilesList,
            profileStore.chosenId(),
            output?.kind,
            output?.name
        ).profile
        val active = routeMatch
        selectedProfile = active
        activeProfileId = active?.id

        val list = adapter ?: ProfileAdapter(
            activeId = { activeProfileId },
            modeStore = modeStore,
            onProfileSelected = { profile ->
                selectedProfile = profile
                setProfileActionsEnabled(true)
                profileStore.setActiveProfile(profile.id)
                // Remember this profile as the user's preference for the current output.
                val currentOutput = OutputRoute.current(this)
                currentOutput?.let {
                    ProfileRouteStore.of(this@ProfilesActivity)
                        .rememberPreference("${it.kind}|${it.name}", profile.id)
                }
                val routeMatch = OutputRoute.pick(
                    profilesList,
                    profileStore.chosenId(),
                    currentOutput?.kind,
                    currentOutput?.name
                ).profile
                activeProfileId = routeMatch?.id
                EqService.send(this, EqService.ACTION_REAPPLY)
                displayProfile(profile)
                renderRoute(profile, routeMatch, currentOutput)
                // Only the active badge moved: rebind the rows in place.
                adapter?.refreshRows()
            }
        ).also {
            it.setHasStableIds(true)
            recyclerProfiles.adapter = it
            adapter = it
        }
        // The rows that were added, removed or kept, by profile id, so the list keeps
        // its scroll position and the focused row across a rename, duplicate or delete.
        list.submit(profilesList)
        setProfileActionsEnabled(active != null)
        renderRoute(active, routeMatch, output)
        if (active != null) {
            displayProfile(active)
        } else if (profilesList.isNotEmpty() && output?.kind == null) {
            textPreamp.text = getString(R.string.output_profile_unknown_sub)
            graphProfile.setData(DoubleArray(0), emptyList(), "OUTPUT NOT IDENTIFIED")
        } else if (output?.kind != null && profilesList.isNotEmpty()) {
            textPreamp.text = getString(R.string.output_profile_missing_sub, OutputRoute.label(output.kind))
            graphProfile.setData(DoubleArray(0), emptyList(), "NO PROFILE FOR THIS OUTPUT")
        } else {
            textPreamp.text = getString(R.string.no_profile_sub)
            graphProfile.setData(DoubleArray(0), emptyList(), "NO SAVED PROFILES")
        }
    }

    private fun displayProfile(profile: Profile) {
        val mode = modeStore.currentDecision().mode
        val effective = modeStore.effectiveProfile(profile, mode)
        refreshPreampDisplay(profile, mode, effective)

        if (effective.curve.isNotEmpty()) {
            val freqs = DoubleArray(effective.curve.size) { effective.curve[it].hz }
            val corr = DoubleArray(effective.curve.size) { index ->
                val hz = effective.curve[index].hz
                effective.correctionAt(hz) + ManualEq.responseDb(effective.manualFilters, hz)
            }
            // The profile's own target: an imported one is read from the profile, never from
            // whatever curve is imported now. Null (imported, curve not saved) draws no target.
            val target = profile.targetDb(freqs)

            val series = listOfNotNull(
                Series("TOTAL EQ", ContextCompat.getColor(this, R.color.cb_signal_cyan), corr),
                target?.let { Series("TARGET", ContextCompat.getColor(this, R.color.cb_dusk_violet), it) }
            )
            graphProfile.setData(
                freqs = freqs,
                series = series,
                title = "${profile.name.uppercase(Locale.US)} · ${mode.title.uppercase(Locale.US)} — " +
                    if (target != null) "EQ vs TARGET" else "EQ",
                yRange = 15.0,
                tHz = profile.transitionHz
            )
        } else if (effective.manualOnly) {
            val freqs = DspConstants.ISO_CENTRES_HZ.filter { it in DspConstants.F_MIN..DspConstants.F_MAX }.toDoubleArray()
            val response = DoubleArray(freqs.size) { ManualEq.responseDb(effective.manualFilters, freqs[it]) }
            graphProfile.setData(
                freqs = freqs,
                series = listOf(Series("${mode.title.uppercase(Locale.US)} EQ", ContextCompat.getColor(this, R.color.cb_signal_cyan), response)),
                title = "${mode.title.uppercase(Locale.US)} · NO ROOM MEASUREMENT",
                yRange = 15.0,
                tHz = profile.transitionHz
            )
        } else {
            graphProfile.setData(DoubleArray(0), emptyList(), "NO MEASUREMENT DATA")
        }
    }

    private fun refreshPreampDisplay(profile: Profile) {
        val mode = modeStore.currentDecision().mode
        refreshPreampDisplay(profile, mode, modeStore.effectiveProfile(profile, mode))
    }

    private fun refreshPreampDisplay(profile: Profile, mode: ContentMode, effective: Profile) {
        // Which output this corrects: correction only applies there (OutputRoute).
        val output = profile.outputKind?.let { profile.outputName ?: OutputRoute.label(it) }
            ?: "any output (output not specified)"
        textPreamp.text = when (selectedExportFormat) {
            ExportFormat.PARAMETRIC -> {
                val filters = profile.filters + ManualEq.sanitize(effective.manualFilters)
                if (filters.isEmpty()) {
                    getString(R.string.profile_preamp_no_filters, mode.title, output)
                } else {
                    getString(R.string.profile_preamp_parametric, Formats.recommendedPreampDb(effective), mode.title, output)
                }
            }
            ExportFormat.GRAPHIC_EQ -> {
                val preamp = Formats.graphicEqPreampDb(effective)
                if (preamp == null) {
                    getString(R.string.profile_preamp_graphic_no_line, mode.title, output)
                } else {
                    getString(R.string.profile_preamp_graphic, preamp, mode.title, output)
                }
            }
            ExportFormat.POWERAMP_JSON -> getString(R.string.profile_preamp_poweramp_json, Formats.recommendedPreampDb(effective), mode.title, output)
            ExportFormat.JSON -> getString(
                R.string.profile_preamp_json,
                Formats.recommendedPreampDb(effective), mode.title, output
            )
        }
    }

    private fun exportCurrentProfile() {
        val p = selectedProfile ?: return
        val mode = modeStore.currentDecision().mode
        val effective = modeStore.effectiveProfile(p, mode)
        val stem = "${p.name.replace(" ", "_").lowercase(Locale.US)}_${mode.key}"
        val filename: String
        val content: String
        val label: String

        when (selectedExportFormat) {
            ExportFormat.PARAMETRIC -> {
                filename = "${stem}_parametric.txt"
                content = Formats.exportParametricTxt(effective)
                label = "Poweramp Parametric Filters · ${mode.title}"
            }
            ExportFormat.GRAPHIC_EQ -> {
                filename = "${stem}_graphiceq.txt"
                content = Formats.exportGraphicEq(effective)
                label = "GraphicEQ Curve · ${mode.title}"
            }
            ExportFormat.POWERAMP_JSON -> {
                filename = "${stem}_poweramp.json"
                content = Formats.exportPowerampJson(effective)
                label = "Poweramp Equalizer preset JSON"
            }
            ExportFormat.JSON -> {
                filename = "${stem}_profile.json"
                content = Formats.exportProfileJson(p, modeStore.allModeFilters(p.id), mode)
                label = "Core EQ Profile JSON · all mode overlays"
            }
        }

        val result = Formats.exportToDevice(this, content, filename)
        Toast.makeText(this, if (result.ok) "${result.message} ($label)" else result.message, Toast.LENGTH_LONG).show()
    }

    private fun setProfileActionsEnabled(on: Boolean) {
        for (button in listOf(btnExport, btnDelete, btnDetails, btnRename, btnDuplicate)) button.isEnabled = on
    }

    /**
     * Whether the chosen profile plays on the output in use, on screen for as
     * long as it is true. It was a toast, gone before it could be read.
     */
    private fun renderRoute(chosen: Profile?, matched: Profile?, output: OutputRoute.Output?) {
        val kind = ProfileRouteLine.kindOf(chosen, matched, output?.kind)
        val res = ProfileRouteLine.stringRes(kind)
        textRoute.text = when (kind) {
            ProfileRouteLine.Kind.MATCHES_OUTPUT -> getString(res, outputWords(output?.kind, output?.name))
            ProfileRouteLine.Kind.OTHER_OUTPUT -> getString(
                res,
                outputWords(output?.kind, output?.name),
                outputWords(chosen?.outputKind, chosen?.outputName)
            )
            else -> getString(res)
        }
    }

    private fun outputWords(kind: String?, name: String?): String {
        val label = OutputRoute.label(kind)
        val named = name?.takeIf { it.isNotBlank() && !it.equals(label, ignoreCase = true) }
        return if (named != null) "$label · $named" else label
    }

    /** A dialog that cannot be stacked by a second press, with focus put back when it closes. */
    private fun showGuarded(builder: AlertDialog.Builder, returnFocus: View, onShown: (AlertDialog) -> Unit = {}) {
        if (dialogOpen) return
        dialogOpen = true
        val dialog = builder
            .setOnDismissListener {
                dialogOpen = false
                if (returnFocus.isShown && returnFocus.isEnabled) returnFocus.requestFocus()
            }
            .create()
        dialog.show()
        onShown(dialog)
    }

    /** Delete asks first: one press used to remove a measured profile with no way back. */
    private fun confirmDelete() {
        val p = selectedProfile ?: return
        val tiedTo = p.outputKind?.let { outputWords(it, p.outputName) }
            ?: getString(R.string.profile_route_legacy)
        showGuarded(
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_delete_title, p.name))
                .setMessage(getString(R.string.profile_delete_message, tiedTo))
                .setPositiveButton(R.string.profile_delete_confirm) { _, _ -> deleteProfile(p) }
                .setNegativeButton(android.R.string.cancel, null),
            returnFocus = btnDelete
        ) { dialog ->
            // Cancel takes focus: a second OK press must not delete. Posted, because the
            // dialog's own first layout otherwise moves focus to its button panel.
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { cancel -> cancel.post { cancel.requestFocus() } }
        }
    }

    private fun deleteProfile(p: Profile) {
        val deletedAt = profilesList.indexOfFirst { it.id == p.id }
        if (!profileStore.deleteProfile(p.id)) return
        if (profileStore.getActiveProfile() == null) {
            EqService.disable(this)
        } else {
            EqService.send(this, EqService.ACTION_REAPPLY)
        }
        Toast.makeText(this, getString(R.string.profile_deleted, p.name), Toast.LENGTH_SHORT).show()
        loadProfiles()
        // Land on the profile that took the deleted row's place (the next one), or on
        // the one before it when the last row went, or on Import when none are left.
        val target = if (profilesList.isEmpty()) -1 else deletedAt.coerceIn(0, profilesList.size - 1)
        if (target >= 0) recyclerProfiles.scrollToPosition(target)
        recyclerProfiles.post {
            val row = if (target >= 0) recyclerProfiles.layoutManager?.findViewByPosition(target) else null
            if (row != null) row.requestFocus() else btnImport.requestFocus()
        }
    }

    private fun showDetails() {
        val p = selectedProfile ?: return
        val body = ProfileDetails.text(ProfileDetails.lines(p, { OutputRoute.label(it) }))
        val text = TextView(this).apply {
            this.text = body
            setTextColor(ContextCompat.getColor(this@ProfilesActivity, R.color.cb_ink))
            textSize = 15f
            setLineSpacing(0f, 1.15f)
        }
        // Focusable, so the remote scrolls a long list of notes line by line.
        val scroll = ScrollView(this).apply {
            isFocusable = true
            setPadding(48, 16, 48, 8)
            addView(text)
            contentDescription = body
        }
        showGuarded(
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_details_title, p.name))
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null),
            returnFocus = btnDetails
        ) { scroll.requestFocus() }
    }

    private fun showRename() {
        val p = selectedProfile ?: return
        val input = EditText(this).apply {
            setText(p.name)
            setSelection(text.length)
            hint = getString(R.string.profile_rename_hint)
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(ProfileStore.MAX_NAME_LENGTH))
        }
        showGuarded(
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_rename_title, p.name))
                .setView(input)
                .setPositiveButton(R.string.profile_rename_save) { _, _ ->
                    val renamed = profileStore.renameProfile(p.id, input.text.toString())
                    if (renamed == null) {
                        Toast.makeText(this, R.string.profile_rename_blank, Toast.LENGTH_LONG).show()
                    } else {
                        selectedProfile = renamed
                        loadProfiles()
                        displayProfile(renamed)
                        EqService.send(this, EqService.ACTION_REAPPLY) // the applied card names it
                        Toast.makeText(this, getString(R.string.profile_renamed, renamed.name), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null),
            returnFocus = btnRename
        ) { input.requestFocus() }
    }

    private fun duplicateSelected() {
        val p = selectedProfile ?: return
        val copy = profileStore.duplicateProfile(p.id) ?: return
        loadProfiles()
        Toast.makeText(this, getString(R.string.profile_duplicated, copy.name), Toast.LENGTH_LONG).show()
    }

    private class ProfileAdapter(
        private val activeId: () -> String?,
        private val modeStore: ContentModeStore,
        private val onProfileSelected: (Profile) -> Unit
    ) : RecyclerView.Adapter<ProfileViewHolder>() {

        private var items: List<Profile> = emptyList()

        /**
         * Show [newItems], telling the list exactly which rows were inserted,
         * removed or kept. Kept rows are always rebound: a row also shows its mode
         * overlays and the active badge, which live outside [Profile] and may have
         * changed while another screen was open.
         */
        fun submit(newItems: List<Profile>) {
            val old = items
            val next = newItems.toList()
            items = next
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = old.size
                override fun getNewListSize(): Int = next.size
                override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean = old[oldPosition].id == next[newPosition].id
                override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean = false
            }, false).dispatchUpdatesTo(this)
        }

        /** Rebind every row where it is; nothing is inserted or removed. */
        fun refreshRows() = notifyItemRangeChanged(0, itemCount)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_profile_row, parent, false)
            return ProfileViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        /**
         * Stable ids keep focus on the same profile across a refresh. Each profile id
         * gets its own number for the adapter's life; String.hashCode() collided
         * ("Aa" and "BB" are both 2112), and a collision moves focus to the wrong row.
         */
        private val stableIds = HashMap<String, Long>()
        private var nextStableId = 1L
        override fun getItemId(position: Int): Long = stableIds.getOrPut(items[position].id) { nextStableId++ }

        override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
            val item = items[position]
            holder.textName.text = item.name
            val targetTitle = "${tv.corebuilds.eq.dsp.Targets.label(item.target)} target"
            val rt = item.rt60Seconds?.let { String.format(Locale.US, "RT60 %.2f s", it) } ?: "RT60 unknown"
            val measurementLimit = if (item.measurementNotes.isNotEmpty()) " · magnitude-only; phase unverified" else ""
            val overlays = ContentMode.entries
                .filter { modeStore.modeFilters(item.id, it).isNotEmpty() }
                .joinToString { it.title }
                .takeIf { it.isNotEmpty() }
                ?.let { " · mode EQ: $it" }
                .orEmpty()
            holder.textSub.text = if (item.manualOnly) {
                "Manual EQ only · not measured$overlays"
            } else {
                val manual = if (item.manualFilters.isNotEmpty()) " · ${item.manualFilters.size} legacy manual bands" else ""
                // Score first: the row is one line with an end ellipsis, and the
                // details before it vary in length (review on #264).
                QualityText.prependTo(
                    holder.itemView.context,
                    "$targetTitle · ${item.filters.size} filters$manual$overlays · $rt · ${item.micType}$measurementLimit",
                    QualityText.scoreOf(item)
                )
            }

            val isActive = item.id == activeId()
            holder.textBadge.visibility = if (isActive) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                onProfileSelected(item)
            }
        }
    }

    private class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.item_profile_name)
        val textBadge: TextView = view.findViewById(R.id.item_profile_badge)
        val textSub: TextView = view.findViewById(R.id.item_profile_sub)
    }

    companion object { private const val REQ_IMPORT_PROFILE = 4201 }
}
