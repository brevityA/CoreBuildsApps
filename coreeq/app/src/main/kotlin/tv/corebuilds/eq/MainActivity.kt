package tv.corebuilds.eq

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.ui.BandSlidersView
import tv.corebuilds.eq.ui.CurveGraphView
import tv.corebuilds.eq.ui.Series
import java.util.Locale

class MainActivity : TvActivity() {

    private lateinit var profileStore: ProfileStore
    private lateinit var textProfileName: TextView
    private lateinit var textProfileSub: TextView
    private lateinit var graphHome: CurveGraphView
    private lateinit var bandsHome: BandSlidersView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        profileStore = ProfileStore(this)

        textProfileName = findViewById(R.id.text_active_profile_name)
        textProfileSub = findViewById(R.id.text_active_profile_sub)
        graphHome = findViewById(R.id.graph_home)
        bandsHome = findViewById(R.id.bands_home)

        findViewById<Button>(R.id.btn_remeasure)?.setOnClickListener {
            startActivity(Intent(this, MeasureActivity::class.java))
        }
        findViewById<Button>(R.id.btn_nav_measure)?.setOnClickListener {
            startActivity(Intent(this, MeasureActivity::class.java))
        }
        findViewById<Button>(R.id.btn_nav_profiles)?.setOnClickListener {
            startActivity(Intent(this, ProfilesActivity::class.java))
        }
        findViewById<Button>(R.id.btn_nav_capability)?.setOnClickListener {
            startActivity(Intent(this, CapabilityActivity::class.java))
        }

        // Default focus on Re-measure button
        findViewById<Button>(R.id.btn_remeasure)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        loadActiveProfile()
    }

    private fun loadActiveProfile() {
        val profile = profileStore.getActiveProfile()
        textProfileName.text = profile.name

        val targetName = profile.target.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        val filterCount = profile.filters.size
        textProfileSub.text = "$targetName · $filterCount filters · ${profile.micType}"

        // Update CurveGraphView
        if (profile.curve.isNotEmpty()) {
            val freqs = DoubleArray(profile.curve.size) { profile.curve[it].hz }
            val measured = DoubleArray(profile.curve.size) { profile.curve[it].measuredDb }
            val corr = DoubleArray(profile.curve.size) { profile.curve[it].correctionDb }

            val series = listOf(
                Series("MEASURED", ContextCompat.getColor(this, R.color.cb_slate), measured),
                Series("CORRECTION", ContextCompat.getColor(this, R.color.cb_signal_cyan), corr)
            )
            graphHome.setData(
                freqs = freqs,
                series = series,
                title = "MEASURED vs CORRECTION",
                yRange = 20.0,
                tHz = profile.transitionHz
            )
        }

        // Update BandSlidersView
        if (profile.platformBands.isNotEmpty()) {
            bandsHome.setBands(profile.platformBands)
        }
    }
}
