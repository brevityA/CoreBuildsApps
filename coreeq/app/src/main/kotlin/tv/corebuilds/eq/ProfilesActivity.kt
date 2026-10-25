package tv.corebuilds.eq

import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.export.ProfileExchange
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.ui.CurveGraphView
import tv.corebuilds.eq.ui.OverlayPermission
import tv.corebuilds.eq.ui.QualityText
import tv.corebuilds.eq.ui.Series
import tv.corebuilds.eq.ui.showSwitch
import java.util.Locale

class ProfilesActivity : TvActivity() {

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

        recyclerProfiles.layoutManager = LinearLayoutManager(this)

        setupExportChips()
        loadProfiles()

        btnExport.setOnClickListener { exportCurrentProfile() }
        findViewById<Button>(R.id.btn_import_profile).setOnClickListener {
            if (!tv.corebuilds.eq.ui.FilePicker.open(this, REQ_IMPORT_PROFILE)) {
                Toast.makeText(this, "No file picker is available.", Toast.LENGTH_LONG).show()
            }
        }
        btnDelete.setOnClickListener { deleteCurrentProfile() }
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

        recyclerProfiles.adapter = ProfileAdapter(
            items = profilesList,
            activeId = { activeProfileId },
            modeStore = modeStore,
            onProfileSelected = { profile ->
                selectedProfile = profile
                btnExport.isEnabled = true
                btnDelete.isEnabled = true
                profileStore.setActiveProfile(profile.id)
                val currentOutput = OutputRoute.current(this)
                val routeMatch = OutputRoute.pick(
                    profilesList,
                    profileStore.chosenId(),
                    currentOutput?.kind,
                    currentOutput?.name
                ).profile
                activeProfileId = routeMatch?.id
                EqService.send(this, EqService.ACTION_REAPPLY)
                displayProfile(profile)
                if (routeMatch?.id != profile.id) {
                    Toast.makeText(this, R.string.profile_not_active_on_output, Toast.LENGTH_LONG).show()
                }
                recyclerProfiles.adapter?.notifyDataSetChanged()
            }
        )
        btnExport.isEnabled = active != null
        btnDelete.isEnabled = active != null
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

    private fun deleteCurrentProfile() {
        val p = selectedProfile ?: return
        profileStore.deleteProfile(p.id)
        if (profileStore.getActiveProfile() == null) {
            EqService.disable(this)
        } else {
            EqService.send(this, EqService.ACTION_REAPPLY)
        }
        Toast.makeText(this, "Deleted ${p.name}", Toast.LENGTH_SHORT).show()
        loadProfiles()
    }

    private class ProfileAdapter(
        private val items: List<Profile>,
        private val activeId: () -> String?,
        private val modeStore: ContentModeStore,
        private val onProfileSelected: (Profile) -> Unit
    ) : RecyclerView.Adapter<ProfileViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_profile_row, parent, false)
            return ProfileViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
            val item = items[position]
            holder.textName.text = item.name
            val targetTitle = item.target.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
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
