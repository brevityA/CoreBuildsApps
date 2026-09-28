package tv.corebuilds.eq

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
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
                textVerdictSession.text = "session broadcast: ${if (verdict.sessionBroadcastSupported) "yes" else "no"}"
                textVerdictGlobal.text = "global mix: ${if (verdict.session0Supported) "supported" else "not supported"}"
            }
        }
    }

    private fun exportForTvSettings() {
        val active = profileStore.getActiveProfile()
        val sb = StringBuilder()
        sb.append("CORE EQ · TV SOUND SETTINGS REFERENCE\n")
        sb.append("Profile: ").append(active.name).append("\n")
        sb.append("Target: ").append(active.target).append("\n")
        sb.append("------------------------------------\n")
        for (b in active.platformBands) {
            val db = b.millibels / 100.0
            val sign = if (db > 0) "+" else ""
            sb.append(String.format(Locale.US, "  %6.0f Hz : %s%.1f dB\n", b.centerHz, sign, db))
        }
        sb.append("------------------------------------\n")
        sb.append("Enter these band values directly into your TV's built-in sound equaliser.\n")

        Formats.exportToDevice(
            this,
            sb.toString(),
            "tv_settings_bands.txt",
            "TV Settings Band Levels"
        )
    }
}
