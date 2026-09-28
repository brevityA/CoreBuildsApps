package tv.corebuilds.eq

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.dsp.Correction
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.MeasurementException
import tv.corebuilds.eq.dsp.Sweep
import tv.corebuilds.eq.dsp.SweepAnalysis
import tv.corebuilds.eq.dsp.SweepResult
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
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Measure: play the sweep, record it through the remote, analyse it with
 * [SweepAnalysis], and save what was measured. Every number the saved
 * profile carries comes from this capture or from a choice made on screen.
 */
class MeasureActivity : TvActivity() {

    private data class RoomSize(val label: String, val volumeM3: Double?)
    private data class TargetChoice(val key: String, val label: String)

    private lateinit var profileStore: ProfileStore
    private lateinit var stimulusPlayer: StimulusPlayer
    private lateinit var captureEngine: CaptureEngine

    private lateinit var graphMeasure: CurveGraphView
    private lateinit var progressMeasure: ProgressBar
    private lateinit var textStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSave: Button
    private lateinit var btnRoom: Button
    private lateinit var btnTarget: Button

    private var roomIndex = 1
    private var targetIndex = 0
    private var result: SweepResult? = null
    private var micName: String? = null
    private var measuring = false

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
        btnRoom = findViewById(R.id.btn_room_size)
        btnTarget = findViewById(R.id.btn_target)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        roomIndex = prefs.getInt(KEY_ROOM, 1).coerceIn(ROOMS.indices)
        targetIndex = prefs.getInt(KEY_TARGET, 0).coerceIn(TARGETS.indices)

        btnRoom.setOnClickListener {
            roomIndex = (roomIndex + 1) % ROOMS.size
            prefs.edit().putInt(KEY_ROOM, roomIndex).apply()
            refreshChoices()
        }
        btnTarget.setOnClickListener {
            targetIndex = (targetIndex + 1) % TARGETS.size
            prefs.edit().putInt(KEY_TARGET, targetIndex).apply()
            refreshChoices()
        }
        btnStart.setOnClickListener { startMeasurement() }
        btnStop.setOnClickListener { cancelMeasurement("Stopped. Nothing was saved.") }
        btnSave.setOnClickListener { saveAndFinish() }

        refreshChoices()
        textStatus.text = getString(R.string.measure_ready)
        btnRoom.requestFocus()
    }

    private fun refreshChoices() {
        btnRoom.text = getString(R.string.measure_room_button, ROOMS[roomIndex].label)
        btnTarget.text = getString(R.string.measure_target_button, TARGETS[targetIndex].label)
        val r = result
        val capture = lastCapture
        if (r != null && capture != null && !measuring) {
            // Re-run the correction for the new choices from the same capture.
            val target = TARGETS[targetIndex].key
            val volume = ROOMS[roomIndex].volumeM3
            textStatus.text = getString(R.string.measure_analysing)
            thread(name = "CoreEqReanalysis") {
                val updated = try {
                    SweepAnalysis.analyze(capture, lastRate, target, volume)
                } catch (e: Exception) {
                    Log.w(TAG, "Re-analysis failed", e)
                    null
                }
                runOnUiThread {
                    if (updated != null) {
                        result = updated
                        showResult(updated)
                    } else {
                        showResult(r)
                    }
                }
            }
        } else {
            showTargetOnly()
        }
    }

    private fun showTargetOnly() {
        val centres = DspConstants.ISO_CENTRES_HZ.filter { it <= DspConstants.F_MAX }.toDoubleArray()
        graphMeasure.setData(
            centres,
            listOf(Series("TARGET", ContextCompat.getColor(this, R.color.cb_signal_cyan),
                Targets.targetCurve(TARGETS[targetIndex].key, centres))),
            "TARGET · ${TARGETS[targetIndex].label.uppercase(Locale.US)}",
            20.0,
            Correction.transitionHz(ROOMS[roomIndex].volumeM3, null)
        )
    }

    private fun startMeasurement() {
        if (measuring) return
        if (!captureEngine.hasPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        measuring = true
        result = null
        micName = null
        btnStart.isEnabled = false
        btnSave.visibility = View.GONE
        progressMeasure.progress = 0
        textStatus.text = getString(R.string.measure_opening_mic)
        EqService.send(this, EqService.ACTION_SUSPEND) // our own correction must not colour the sweep

        val total = LEAD_SECONDS + Sweep.SECONDS + SweepAnalysis.TAIL_SECONDS
        captureEngine.start(total, object : CaptureListener {
            override fun onRecording(deviceName: String?) {
                micName = deviceName
                btnStart.postDelayed({
                    if (measuring) stimulusPlayer.playSweep { msg -> runOnUiThread { fail(msg) } }
                }, (LEAD_SECONDS * 1000).toLong())
            }

            override fun onLevel(secondsElapsed: Float, totalSeconds: Float, rmsDbfs: Float, peakDbfs: Float) {
                runOnUiThread {
                    progressMeasure.progress = ((secondsElapsed / totalSeconds) * 100f).roundToInt().coerceIn(0, 100)
                    textStatus.text = String.format(
                        Locale.US, "%.1f s of %.0f s · mic level %.0f dBFS · peak %.0f dBFS%s",
                        secondsElapsed, totalSeconds, rmsDbfs, peakDbfs,
                        if (peakDbfs > CLIP_DBFS) " · TOO LOUD" else ""
                    )
                }
            }

            override fun onCaptured(samples: DoubleArray, sampleRate: Int, deviceName: String?) {
                runOnUiThread { textStatus.text = getString(R.string.measure_analysing) }
                analyse(samples, sampleRate)
            }

            override fun onError(message: String) {
                runOnUiThread { fail(message) }
            }
        })
    }

    private var lastCapture: DoubleArray? = null
    private var lastRate = CaptureEngine.ANALYSIS_RATE

    private fun analyse(samples: DoubleArray, rate: Int) {
        thread(name = "CoreEqAnalysis") {
            val (peakDbfs, _) = SweepAnalysis.levelsDbfs(samples)
            val outcome: Result<SweepResult> = if (peakDbfs > CLIP_DBFS) {
                Result.failure(MeasurementException(
                    String.format(Locale.US,
                        "The remote microphone clipped (peak %.1f dBFS), so the loud part of the sweep is distorted. " +
                            "Turn the TV down two or three steps and measure again.", peakDbfs)
                ))
            } else {
                try {
                    Result.success(SweepAnalysis.analyze(samples, rate, TARGETS[targetIndex].key, ROOMS[roomIndex].volumeM3))
                } catch (e: MeasurementException) {
                    Result.failure(e)
                } catch (e: Exception) {
                    Log.e(TAG, "Analysis crashed", e)
                    Result.failure(MeasurementException("Analysis failed: ${e.javaClass.simpleName}: ${e.message}"))
                }
            }
            runOnUiThread {
                outcome.onSuccess {
                    lastCapture = samples
                    lastRate = rate
                    finishMeasuring()
                    result = it
                    showResult(it)
                    btnSave.visibility = View.VISIBLE
                    btnSave.requestFocus()
                }.onFailure { fail(it.message ?: it.javaClass.simpleName) }
            }
        }
    }

    private fun showResult(r: SweepResult) {
        graphMeasure.setData(
            r.centresHz,
            listOf(
                Series("MEASURED", ContextCompat.getColor(this, R.color.cb_slate), r.measuredDb),
                Series("TARGET", ContextCompat.getColor(this, R.color.cb_dusk_violet), r.targetDb),
                Series("CORRECTION", ContextCompat.getColor(this, R.color.cb_signal_cyan), r.correctionDb)
            ),
            String.format(Locale.US, "MEASURED vs TARGET · FULL CORRECTION BELOW %.0f HZ", r.transitionHz),
            20.0,
            r.transitionHz
        )
        val rt = r.rt60Seconds?.let { String.format(Locale.US, "RT60 %.2f s", it) } ?: "RT60 not measurable (noisy decay)"
        val nulls = r.nullMask.count { it }
        val gated = r.centresHz.indices.count { !r.minPhaseOk[it] && r.centresHz[it] >= r.floorHz }
        textStatus.text = String.format(
            Locale.US, "%s · corrects %.0f Hz–%.0f kHz · %d null%s and %d non-minimum-phase band%s left alone · SNR %.0f dB",
            rt, r.floorHz, DspConstants.F_MAX / 1000, nulls, if (nulls == 1) "" else "s",
            gated, if (gated == 1) "" else "s", r.snrDb
        )
    }

    private fun fail(message: String) {
        finishMeasuring()
        result = null
        progressMeasure.progress = 0
        textStatus.text = getString(R.string.measure_failed, message)
        btnStart.requestFocus()
    }

    private fun finishMeasuring() {
        measuring = false
        stimulusPlayer.stop()
        captureEngine.stop()
        btnStart.isEnabled = true
        EqService.send(this, EqService.ACTION_RESUME)
    }

    private fun cancelMeasurement(message: String) {
        if (!measuring) return
        finishMeasuring()
        progressMeasure.progress = 0
        textStatus.text = message
    }

    private fun saveAndFinish() {
        val r = result ?: return
        val now = System.currentTimeMillis()
        val bands = Correction.collapseToBands(
            DISPLAY_BANDS_HZ, { hz -> interpolate(hz, r.centresHz, r.correctionDb) }, -1500, 1500
        ).map { PlatformBand(it.first, it.second) }
        val profile = Profile(
            id = "profile-$now",
            name = getString(R.string.measure_profile_name, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(now))),
            timestampMs = now,
            target = TARGETS[targetIndex].key,
            micType = micName ?: "microphone",
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            stimulus = "sweep_${Sweep.SECONDS.roundToInt()}s",
            captureSeconds = LEAD_SECONDS + Sweep.SECONDS + SweepAnalysis.TAIL_SECONDS,
            volumeM3 = ROOMS[roomIndex].volumeM3,
            rt60Seconds = r.rt60Seconds,
            schroederHz = r.schroederHz,
            transitionHz = r.transitionHz,
            rolloffHz = r.floorHz,
            snrDb = r.snrDb,
            nullsUntouchedHz = r.centresHz.indices.filter { r.nullMask[it] }.map { r.centresHz[it] },
            preampDb = r.preampDb,
            filters = r.filters,
            platformBands = bands,
            curve = r.centresHz.indices.map { CurvePoint(r.centresHz[it], r.measuredDb[it], r.correctionDb[it]) }
        )
        profileStore.saveProfile(profile, setAsActive = true)
        EqService.send(this, EqService.ACTION_REAPPLY)
        Toast.makeText(this, getString(R.string.measure_saved, profile.name), Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startMeasurement()
        } else {
            textStatus.text = getString(R.string.measure_failed,
                "Core EQ needs the microphone to hear the sweep. Allow it when asked, or in Settings → Apps → Core EQ → Permissions.")
        }
    }

    override fun onPause() {
        super.onPause()
        cancelMeasurement("Measurement stopped because Core EQ left the screen. Nothing was saved.")
    }

    private fun interpolate(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (xs.isEmpty()) return 0.0
        if (x <= xs[0]) return ys[0]
        if (x >= xs[xs.size - 1]) return 0.0
        for (i in 0 until xs.size - 1) {
            if (x in xs[i]..xs[i + 1]) {
                val frac = (x - xs[i]) / (xs[i + 1] - xs[i])
                return ys[i] + frac * (ys[i + 1] - ys[i])
            }
        }
        return 0.0
    }

    companion object {
        private const val TAG = "CoreEqMeasure"
        private const val REQ_MIC = 42
        private const val PREFS = "core_eq_measure"
        private const val KEY_ROOM = "room_index"
        private const val KEY_TARGET = "target_index"
        /** Silence recorded before the sweep, so the noise estimate and latency have room. */
        private const val LEAD_SECONDS = 0.5
        private const val CLIP_DBFS = -0.5
        /** The common 5-band layout, used for the Home preview only; the service reads the TV's own bands. */
        private val DISPLAY_BANDS_HZ = doubleArrayOf(60.0, 230.0, 910.0, 3600.0, 14000.0)

        private val ROOMS = listOf(
            RoomSize("Small (bedroom, ~30 m³)", 30.0),
            RoomSize("Medium (living room, ~55 m³)", 55.0),
            RoomSize("Large (lounge, ~90 m³)", 90.0),
            RoomSize("Open plan (~150 m³)", 150.0),
            RoomSize("Not sure", null)
        )
        private val TARGETS = listOf(
            TargetChoice("dialogue", "Dialogue"),
            TargetChoice("room", "Room"),
            TargetChoice("bk", "B&K 1974"),
            TargetChoice("flat", "Flat")
        )
    }
}
