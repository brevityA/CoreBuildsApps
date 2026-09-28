package tv.corebuilds.eq

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.dsp.Correction
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.Peaking
import tv.corebuilds.eq.dsp.Targets
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.PlatformBand
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.measure.CaptureEngine
import tv.corebuilds.eq.measure.CaptureListener
import tv.corebuilds.eq.measure.StimulusPlayer
import tv.corebuilds.eq.ui.CurveGraphView
import tv.corebuilds.eq.ui.Series
import java.util.Locale

class MeasureActivity : TvActivity() {

    private lateinit var profileStore: ProfileStore
    private lateinit var stimulusPlayer: StimulusPlayer
    private lateinit var captureEngine: CaptureEngine

    private lateinit var graphMeasure: CurveGraphView
    private lateinit var progressMeasure: ProgressBar
    private lateinit var textStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSave: Button

    private var measuredResult: DoubleArray? = null
    private var lastRt60 = 0.50

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measure)

        profileStore = ProfileStore(this)
        stimulusPlayer = StimulusPlayer()
        captureEngine = CaptureEngine(this)

        graphMeasure = findViewById(R.id.graph_measure)
        progressMeasure = findViewById(R.id.progress_measure)
        textStatus = findViewById(R.id.text_measure_status)
        btnStart = findViewById(R.id.btn_measure_start)
        btnStop = findViewById(R.id.btn_measure_stop)
        btnSave = findViewById(R.id.btn_measure_save)

        val centres = DspConstants.ISO_CENTRES_HZ
        val target = Targets.targetCurve("dialogue", centres)
        val initialSeries = listOf(
            Series("TARGET", ContextCompat.getColor(this, R.color.cb_signal_cyan), target)
        )
        graphMeasure.setData(centres, initialSeries, "LIVE CAPTURE vs TARGET · CORRECTS BELOW 384 HZ", 20.0, 384.0)

        btnStart.setOnClickListener { startMeasurement() }
        btnStop.setOnClickListener { stopMeasurement() }
        btnSave.setOnClickListener { saveAndFinish() }

        // Default focus on Step 1 card
        findViewById<View>(R.id.card_step_1)?.requestFocus()
    }

    private fun startMeasurement() {
        btnStart.isEnabled = false
        btnSave.visibility = View.GONE
        progressMeasure.progress = 0

        stimulusPlayer.playSweep(
            durationSeconds = 10.0,
            onProgress = null,
            onComplete = null
        )

        captureEngine.startCapture(
            durationSeconds = 10.0,
            listener = object : CaptureListener {
                override fun onProgress(secondsElapsed: Float, totalSeconds: Float, noiseFloorDb: Float) {
                    runOnUiThread {
                        val pct = ((secondsElapsed / totalSeconds) * 100f).toInt().coerceIn(0, 100)
                        progressMeasure.progress = pct
                        textStatus.text = String.format(
                            Locale.US,
                            "%.1f s of %.0f s · Noise floor %.0f dB",
                            secondsElapsed, totalSeconds, noiseFloorDb
                        )
                    }
                }

                override fun onSpectrumUpdate(frequencies: DoubleArray, magnitudesDb: DoubleArray) {
                    runOnUiThread {
                        val target = Targets.targetCurve("dialogue", frequencies)
                        val series = listOf(
                            Series("CAPTURE", ContextCompat.getColor(this@MeasureActivity, R.color.cb_slate), magnitudesDb),
                            Series("TARGET", ContextCompat.getColor(this@MeasureActivity, R.color.cb_signal_cyan), target)
                        )
                        graphMeasure.setData(
                            frequencies,
                            series,
                            "LIVE CAPTURE vs TARGET · CORRECTS BELOW 384 HZ",
                            20.0,
                            384.0
                        )
                    }
                }

                override fun onComplete(measuredDb: DoubleArray, rt60Seconds: Double) {
                    measuredResult = measuredDb
                    lastRt60 = rt60Seconds
                    runOnUiThread {
                        btnStart.isEnabled = true
                        btnSave.visibility = View.VISIBLE
                        btnSave.requestFocus()
                        progressMeasure.progress = 100
                        textStatus.text = String.format(
                            Locale.US,
                            "Measurement complete · RT60 %.2f s · Ready to save",
                            rt60Seconds
                        )
                        Toast.makeText(this@MeasureActivity, "Measurement complete", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        btnStart.isEnabled = true
                        textStatus.text = "Error: $message"
                    }
                }
            }
        )
    }

    private fun stopMeasurement() {
        stimulusPlayer.stop()
        captureEngine.stop()
        btnStart.isEnabled = true
        textStatus.text = "Stimulus stopped"
    }

    private fun saveAndFinish() {
        val measured = measuredResult ?: return
        val centres = DspConstants.ISO_CENTRES_HZ
        val nulls = Correction.detectNulls(centres, measured)
        val tHz = Correction.transitionHz(54.0, lastRt60)
        val schroeder = Correction.schroederHz(54.0, lastRt60)
        val target = Targets.targetCurve("dialogue", centres)
        val corr = Correction.calculateCorrectionCurve(centres, measured, target, transitionHz = tHz, nullMask = nulls)
        val filters = Peaking.fitPeakingFilters(centres, corr, 6)
        val preamp = Peaking.preampDb(filters)

        val platformBands5 = Correction.collapseToBands(
            doubleArrayOf(60.0, 230.0, 910.0, 3600.0, 14000.0),
            { hz -> interpolate(hz, centres, corr) },
            -1500, 1500
        ).map { PlatformBand(it.first, it.second) }

        val curvePoints = centres.indices.map {
            CurvePoint(centres[it], measured[it], corr[it])
        }

        val untouchedNullsList = mutableListOf<Double>()
        for (i in centres.indices) {
            if (nulls[i]) untouchedNullsList.add(centres[i])
        }

        val newProfile = Profile(
            id = "profile-${System.currentTimeMillis()}",
            name = "Living room (Calibrated)",
            timestampMs = System.currentTimeMillis(),
            target = "dialogue",
            micType = "remote mic",
            deviceName = "Android TV",
            stimulus = "sweep_10s",
            captureSeconds = 10.0,
            volumeM3 = 54.0,
            rt60Seconds = lastRt60,
            schroederHz = schroeder,
            transitionHz = tHz,
            rolloffHz = 40.0,
            nullsUntouchedHz = untouchedNullsList,
            preampDb = preamp,
            filters = filters,
            platformBands = platformBands5,
            curve = curvePoints
        )

        profileStore.saveProfile(newProfile, setAsActive = true)
        Toast.makeText(this, "Profile saved and applied as active", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun interpolate(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (xs.isEmpty()) return 0.0
        if (x <= xs[0]) return ys[0]
        if (x >= xs[xs.size - 1]) return ys[ys.size - 1]
        for (i in 0 until xs.size - 1) {
            if (x in xs[i]..xs[i + 1]) {
                val frac = (x - xs[i]) / (xs[i + 1] - xs[i])
                return ys[i] + frac * (ys[i + 1] - ys[i])
            }
        }
        return ys[0]
    }

    override fun onDestroy() {
        stimulusPlayer.stop()
        captureEngine.stop()
        super.onDestroy()
    }
}
