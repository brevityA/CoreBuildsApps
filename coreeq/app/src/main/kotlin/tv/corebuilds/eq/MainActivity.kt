package tv.corebuilds.eq

import android.Manifest
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
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.ui.BandSlidersView
import tv.corebuilds.eq.ui.CurveGraphView
import tv.corebuilds.eq.ui.Series
import java.util.Locale

class MainActivity : TvActivity() {

    private lateinit var profileStore: ProfileStore
    private lateinit var textProfileName: TextView
    private lateinit var textProfileSub: TextView
    private lateinit var textStatus: TextView
    private lateinit var btnToggle: Button
    private lateinit var graphHome: CurveGraphView
    private lateinit var bandsHome: BandSlidersView

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        profileStore = ProfileStore(this)

        textProfileName = findViewById(R.id.text_active_profile_name)
        textProfileSub = findViewById(R.id.text_active_profile_sub)
        textStatus = findViewById(R.id.text_eq_status)
        btnToggle = findViewById(R.id.btn_toggle_correction)
        graphHome = findViewById(R.id.graph_home)
        bandsHome = findViewById(R.id.bands_home)

        findViewById<Button>(R.id.btn_remeasure).setOnClickListener {
            startActivity(Intent(this, MeasureActivity::class.java))
        }
        btnToggle.setOnClickListener { toggleCorrection() }
        findViewById<Button>(R.id.btn_nav_profiles).setOnClickListener {
            startActivity(Intent(this, ProfilesActivity::class.java))
        }
        findViewById<Button>(R.id.btn_nav_capability).setOnClickListener {
            startActivity(Intent(this, CapabilityActivity::class.java))
        }

        findViewById<Button>(R.id.btn_remeasure).requestFocus()
    }

    override fun onResume() {
        super.onResume()
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
        refreshStatus()
    }

    override fun onPause() {
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

    private fun refreshStatus() {
        val on = profileStore.correctionEnabled
        btnToggle.text = getString(if (on) R.string.correction_on else R.string.correction_off)
        val status = profileStore.status()
        textStatus.text = when {
            status == null -> getString(R.string.correction_off)
            else -> status.message
        }
        textStatus.setTextColor(
            ContextCompat.getColor(this, if (status?.isError == true) R.color.cb_ember else R.color.cb_slate)
        )
    }

    private fun loadActiveProfile() {
        val profile = profileStore.getActiveProfile()
        if (profile == null) {
            textProfileName.text = getString(R.string.no_profile)
            textProfileSub.text = getString(R.string.no_profile_sub)
            findViewById<Button>(R.id.btn_remeasure).text = getString(R.string.action_measure)
            graphHome.setData(DoubleArray(0), emptyList(), "NO MEASUREMENT YET")
            bandsHome.setBands(emptyList())
            bandsHome.visibility = View.INVISIBLE
            return
        }
        findViewById<Button>(R.id.btn_remeasure).text = getString(R.string.action_remeasure)
        textProfileName.text = profile.name
        val targetName = profile.target.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        val rt = profile.rt60Seconds?.let { String.format(Locale.US, "RT60 %.2f s", it) } ?: "RT60 unknown"
        textProfileSub.text = "$targetName · ${profile.filters.size} filters · $rt · ${profile.micType}"

        if (profile.curve.isNotEmpty()) {
            val freqs = DoubleArray(profile.curve.size) { profile.curve[it].hz }
            val measured = DoubleArray(profile.curve.size) { profile.curve[it].measuredDb }
            val corr = DoubleArray(profile.curve.size) { profile.curve[it].correctionDb }
            graphHome.setData(
                freqs = freqs,
                series = listOf(
                    Series("MEASURED", ContextCompat.getColor(this, R.color.cb_slate), measured),
                    Series("CORRECTION", ContextCompat.getColor(this, R.color.cb_signal_cyan), corr)
                ),
                title = "MEASURED vs CORRECTION",
                yRange = 20.0,
                tHz = profile.transitionHz
            )
        }
        bandsHome.visibility = View.VISIBLE
        bandsHome.setBands(profile.platformBands)
    }

    private companion object {
        const val REQ_NOTIFY = 7
    }
}
