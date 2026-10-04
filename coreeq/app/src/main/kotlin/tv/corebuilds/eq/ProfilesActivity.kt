package tv.corebuilds.eq

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.Targets
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.ui.CurveGraphView
import tv.corebuilds.eq.ui.Series
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
    private lateinit var btnExport: Button
    private lateinit var btnDelete: Button

    private var profilesList = mutableListOf<Profile>()
    private var selectedProfile: Profile? = null
    private var selectedExportFormat = ExportFormat.PARAMETRIC

    private enum class ExportFormat {
        PARAMETRIC, GRAPHIC_EQ, JSON
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
        btnExport = findViewById(R.id.btn_export_profile)
        btnDelete = findViewById(R.id.btn_delete_profile)

        recyclerProfiles.layoutManager = LinearLayoutManager(this)

        setupExportChips()
        loadProfiles()

        btnExport.setOnClickListener { exportCurrentProfile() }
        btnDelete.setOnClickListener { deleteCurrentProfile() }
    }

    private fun setupExportChips() {
        btnChipParametric.setOnClickListener {
            selectedExportFormat = ExportFormat.PARAMETRIC
            updateChipStates()
        }
        btnChipGraphicEq.setOnClickListener {
            selectedExportFormat = ExportFormat.GRAPHIC_EQ
            updateChipStates()
        }
        btnChipJson.setOnClickListener {
            selectedExportFormat = ExportFormat.JSON
            updateChipStates()
        }
        updateChipStates()
    }

    private fun updateChipStates() {
        btnChipParametric.isActivated = (selectedExportFormat == ExportFormat.PARAMETRIC)
        btnChipGraphicEq.isActivated = (selectedExportFormat == ExportFormat.GRAPHIC_EQ)
        btnChipJson.isActivated = (selectedExportFormat == ExportFormat.JSON)
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
        val active = routeMatch ?: if (output?.kind == null) profileStore.getActiveProfile() else null
        selectedProfile = active

        recyclerProfiles.adapter = ProfileAdapter(
            items = profilesList,
            activeId = { profileStore.getActiveProfile()?.id },
            modeStore = modeStore,
            onProfileSelected = { profile ->
                selectedProfile = profile
                btnExport.isEnabled = true
                btnDelete.isEnabled = true
                profileStore.setActiveProfile(profile.id)
                EqService.send(this, EqService.ACTION_REAPPLY)
                displayProfile(profile)
                recyclerProfiles.adapter?.notifyDataSetChanged()
            }
        )
        btnExport.isEnabled = active != null
        btnDelete.isEnabled = active != null
        if (active != null) {
            displayProfile(active)
        } else if (output?.kind != null && profilesList.isNotEmpty()) {
            textPreamp.text = getString(R.string.output_profile_missing_sub, OutputRoute.label(output.kind))
            graphProfile.setData(DoubleArray(0), emptyList(), "NO PROFILE FOR THIS OUTPUT")
        } else {
            textPreamp.text = getString(R.string.no_profile_sub)
            graphProfile.setData(DoubleArray(0), emptyList(), "NO SAVED PROFILES")
        }
    }

    private fun displayProfile(profile: Profile) {
        val mode = modeStore.resolve(modeStore.lastActivePackages()).mode
        val effective = modeStore.effectiveProfile(profile, mode)
        val pDb = Formats.recommendedPreampDb(effective)
        // Which output this corrects: correction only applies there (OutputRoute).
        val on = profile.outputKind?.let { profile.outputName ?: OutputRoute.label(it) } ?: "any output (output not specified)"
        textPreamp.text = String.format(Locale.US, "Preamp  %.2f dB  ·  %s ·  For %s", pDb, mode.title, on)

        if (effective.curve.isNotEmpty()) {
            val freqs = DoubleArray(effective.curve.size) { effective.curve[it].hz }
            val corr = DoubleArray(effective.curve.size) { index ->
                val hz = effective.curve[index].hz
                effective.correctionAt(hz) + ManualEq.responseDb(effective.manualFilters, hz)
            }
            val target = Targets.targetCurve(profile.target, freqs)

            val series = listOf(
                Series("TOTAL EQ", ContextCompat.getColor(this, R.color.cb_signal_cyan), corr),
                Series("TARGET", ContextCompat.getColor(this, R.color.cb_dusk_violet), target)
            )
            graphProfile.setData(
                freqs = freqs,
                series = series,
                title = "${profile.name.uppercase(Locale.US)} · ${mode.title.uppercase(Locale.US)} — EQ vs TARGET",
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

    private fun exportCurrentProfile() {
        val p = selectedProfile ?: return
        val mode = modeStore.resolve(modeStore.lastActivePackages()).mode
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
                "$targetTitle · ${item.filters.size} filters$manual$overlays · $rt · ${item.micType}$measurementLimit"
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
}
