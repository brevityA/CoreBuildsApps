package tv.corebuilds.eq

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import tv.corebuilds.eq.apply.EffectLadder
import tv.corebuilds.eq.export.Formats
import tv.corebuilds.eq.export.ProfileStore
import java.util.Locale
import kotlin.concurrent.thread

class CapabilityActivity : TvActivity() {

    private lateinit var profileStore: ProfileStore
    private lateinit var statusSession0: TextView
    private lateinit var statusDp: TextView
    private lateinit var statusPlatformEq: TextView
    private lateinit var textVerdictBands: TextView
    private lateinit var textVerdictSession: TextView
    private lateinit var textVerdictGlobal: TextView
    private lateinit var btnExportTv: Button
    private var lastBandCentres: List<Double>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capability)

        profileStore = ProfileStore(this)

        statusSession0 = findViewById(R.id.status_session0)
        statusDp = findViewById(R.id.status_dp)
        statusPlatformEq = findViewById(R.id.status_platform_eq)
        textVerdictBands = findViewById(R.id.text_verdict_bands)
        textVerdictSession = findViewById(R.id.text_verdict_session)
        textVerdictGlobal = findViewById(R.id.text_verdict_global)
        btnExportTv = findViewById(R.id.btn_export_tv_settings)

        btnExportTv.setOnClickListener { exportForTvSettings() }

        runCapabilityProbe()
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
                    "Supported (API 28+ DynamicsProcessing engine available)"
                } else {
                    "Not supported on this Android API version"
                }

                statusPlatformEq.text = if (verdict.platformEqualizerSupported) {
                    "${verdict.bandCount} bands · range ${verdict.minMillibel / 100} to +${verdict.maxMillibel / 100} dB"
                } else {
                    "Default 5-band layout"
                }

                textVerdictBands.text = "${verdict.bandCount} bands"
                val announced = profileStore.sessionPackages()
                textVerdictSession.text = if (announced.isEmpty()) {
                    "session broadcast: none seen yet (turn correction on, then play something)"
                } else {
                    "session broadcast: ${announced.size} player(s) seen, latest ${announced.first()}"
                }
                textVerdictGlobal.text = "global mix: ${if (verdict.session0Supported) "supported" else "not supported"}"
            }
        }
    }

    private fun exportForTvSettings() {
        val active = profileStore.getActiveProfile()
        if (active == null) {
            Toast.makeText(this, getString(R.string.no_profile_sub), Toast.LENGTH_LONG).show()
            return
        }
        val centres = lastBandCentres ?: listOf(60.0, 230.0, 910.0, 3600.0, 14000.0)
        val gains = centres.map { active.correctionAt(it) }
        val headroom = maxOf(0.0, gains.maxOrNull() ?: 0.0)
        val sb = StringBuilder()
        sb.append("CORE EQ · TV SOUND SETTINGS REFERENCE\n")
        sb.append("Profile: ").append(active.name).append("\n")
        sb.append("Target: ").append(active.target).append("\n")
        sb.append(String.format(Locale.US, "Bands: %s\n", if (lastBandCentres != null) "this TV's own equaliser" else "the common 5-band layout"))
        sb.append("------------------------------------\n")
        for (i in centres.indices) {
            val db = gains[i] - headroom
            val sign = if (db > 0) "+" else ""
            sb.append(String.format(Locale.US, "  %6.0f Hz : %s%.1f dB\n", centres[i], sign, db))
        }
        sb.append("------------------------------------\n")
        sb.append("Enter these values in the TV's own sound equaliser, at the nearest band it offers.\n")
        sb.append("Every band is lowered by the largest boost, so the correction cannot clip.\n")

        val result = Formats.exportToDevice(this, sb.toString(), "tv_settings_bands.txt")
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
    }
}
