package tv.corebuilds.eq

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.dsp.Correction
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.ImportResult
import tv.corebuilds.eq.dsp.MeasurementQuality
import tv.corebuilds.eq.dsp.MeasurementException
import tv.corebuilds.eq.dsp.RewImport
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
import tv.corebuilds.eq.ui.QualityText
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
    private data class RewMeasurement(val frequenciesHz: DoubleArray, val magnitudesDb: DoubleArray)

    private lateinit var profileStore: ProfileStore
    private lateinit var stimulusPlayer: StimulusPlayer
    private lateinit var captureEngine: CaptureEngine

    private lateinit var graphMeasure: CurveGraphView
    private lateinit var progressMeasure: ProgressBar
    private lateinit var textStatus: TextView
    private lateinit var textQuality: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSave: Button
    private lateinit var btnImport: Button
    private lateinit var btnRoom: Button
    private lateinit var btnTarget: Button

    private var roomIndex = 1
    private var targetIndex = 0
    private var result: SweepResult? = null
    /** A REW import replaces the sweep result for display and saving (plan M10). */
    private var importResult: ImportResult? = null
    private var importInput: RewMeasurement? = null
    private var importing = false
    private var importThread: Thread? = null
    private var micName: String? = null
    private var measuredOutput: OutputRoute.Output? = null
    /** Current media-route estimate captured when an external REW file is imported. */
    private var importedOutputEstimate: OutputRoute.Output? = null
    /** Bumped per measurement, so a late callback from an earlier one is ignored. */
    private var measurementGeneration = 0
    private var sweepReanalysisGeneration = 0
    private var sweepReanalysisInProgress = false
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
        textQuality = findViewById(R.id.text_measure_quality)
        btnStart = findViewById(R.id.btn_measure_start)
        btnStop = findViewById(R.id.btn_measure_stop)
        btnStop.isEnabled = false // only a running sweep can be stopped
        btnSave = findViewById(R.id.btn_measure_save)
        btnImport = findViewById(R.id.btn_measure_import)
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
        btnImport.setOnClickListener { pickRewImport() }

        refreshChoices()
        textStatus.text = getString(R.string.measure_ready)
        btnRoom.requestFocus()
    }

    private fun refreshChoices() {
        btnRoom.text = getString(R.string.measure_room_button, ROOMS[roomIndex].label)
        btnTarget.text = getString(R.string.measure_target_button, TARGETS[targetIndex].label)
        val capture = lastCapture
        val imported = importInput
        if (imported != null && importResult != null && !measuring && !importing) {
            // The same imported measurement is re-analysed for the new target
            // and room choice; the graph and saved profile cannot drift apart.
            reanalyzeImport(imported)
        } else if (capture != null && !measuring) {
            // Re-run the correction for the new choices from the same capture.
            // Lock the controls until the matching analysis completes so Save
            // cannot combine a new target label with the previous correction.
            val generation = ++sweepReanalysisGeneration
            val target = TARGETS[targetIndex].key
            val volume = ROOMS[roomIndex].volumeM3
            setSweepReanalysisBusy(true)
            textStatus.text = getString(R.string.measure_analysing)
            thread(name = "CoreEqReanalysis") {
                val updated = try {
                    SweepAnalysis.analyze(capture, lastRate, target, volume)
                } catch (e: Exception) {
                    Log.w(TAG, "Re-analysis failed", e)
                    null
                }
                runOnUiThread {
                    if (generation != sweepReanalysisGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    setSweepReanalysisBusy(false)
                    if (updated != null) {
                        result = updated
                        showResult(updated)
                    } else {
                        result = null
                        btnSave.visibility = View.GONE
                        showTargetOnly()
                        textStatus.text = getString(
                            R.string.measure_failed,
                            "The saved sweep could not be re-analysed for this room and target. Measure again."
                        )
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
        renderQuality()
    }

    /**
     * The quality line follows whatever result is on screen: a sweep shows its
     * recording score, then what it found about the room with no points
     * (1.3.2), a REW import says why it is not scored, and nothing shows
     * otherwise.
     */
    private fun renderQuality() {
        val sweep = result
        when {
            sweep != null -> {
                val score = MeasurementQuality.score(sweep)
                val room = MeasurementQuality.room(sweep)
                val line = SpannableStringBuilder(QualityText.text(this, score))
                line.setSpan(ForegroundColorSpan(QualityText.color(this, score)), 0, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                line.append("\n").append(
                    getString(
                        R.string.measure_room_line,
                        resources.getQuantityString(R.plurals.measure_room_dips, room.dips, room.dips),
                        resources.getQuantityString(R.plurals.measure_room_gated, room.phaseGatedBands, room.phaseGatedBands),
                        room.transitionHz.roundToInt()
                    )
                )
                textQuality.text = line
                textQuality.setTextColor(ContextCompat.getColor(this, R.color.cb_slate))
                textQuality.visibility = View.VISIBLE
            }
            importResult != null -> {
                textQuality.setText(R.string.quality_not_scored)
                textQuality.setTextColor(ContextCompat.getColor(this, R.color.cb_slate))
                textQuality.visibility = View.VISIBLE
            }
            else -> textQuality.visibility = View.GONE
        }
    }

    private fun startMeasurement() {
        if (measuring || importing || sweepReanalysisInProgress) return
        sweepReanalysisGeneration += 1
        if (!captureEngine.hasPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        measuring = true
        btnStop.isEnabled = true
        btnStop.requestFocus() // Start is disabled for the sweep; keep focus somewhere usable
        measurementGeneration += 1
        val generation = measurementGeneration
        result = null
        lastCapture = null
        importResult = null
        importInput = null
        importedOutputEstimate = null
        measuredOutput = null
        micName = null
        btnStart.isEnabled = false
        btnRoom.isEnabled = false
        btnTarget.isEnabled = false
        btnSave.setText(R.string.measure_save)
        btnSave.visibility = View.GONE
        btnImport.isEnabled = false
        progressMeasure.progress = 0
        textStatus.text = getString(R.string.measure_opening_mic)
        renderQuality()
        EqService.send(this, EqService.ACTION_SUSPEND) // our own correction must not colour the sweep

        val total = LEAD_SECONDS + Sweep.SECONDS + SweepAnalysis.TAIL_SECONDS
        captureEngine.start(total, object : CaptureListener {
            override fun onRecording(deviceName: String?) {
                micName = deviceName
                // Keep an output estimate as a fallback if Android does not
                // report which device receives the sweep's own AudioTrack.
                val ranked = OutputRoute.current(this@MeasureActivity)
                measuredOutput = ranked
                btnStart.postDelayed({
                    if (measuring && measurementGeneration == generation) stimulusPlayer.playSweep(
                        onError = { msg -> runOnUiThread { fail(msg) } },
                        onRouted = { device ->
                            val tagged = OutputRoute.tag(ranked, device?.type, device?.productName?.toString())
                            runOnUiThread {
                                if (measuring && measurementGeneration == generation) measuredOutput = tagged
                            }
                        }
                    )
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
                    importResult = null
                    importInput = null
                    showResult(it)
                    btnSave.visibility = View.VISIBLE
                    btnSave.requestFocus()
                }.onFailure { fail(it.message ?: it.javaClass.simpleName) }
            }
        }
    }

    private fun showResult(r: SweepResult) {
        btnSave.setText(R.string.measure_save)
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
        renderQuality()
    }

    /** REW measurement import (plan M10): File → Export → Measurement as text. */
    private fun pickRewImport() {
        if (measuring || importing) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        try {
            startActivityForResult(intent, REQ_IMPORT)
        } catch (e: ActivityNotFoundException) {
            importFailed("This TV's system file picker is unavailable. Enable its Files app or use a TV file-transfer app, then try again.")
        }
    }

    private fun importRewFile(uri: Uri) {
        if (measuring || importing) return
        val target = TARGETS[targetIndex].key
        val volume = ROOMS[roomIndex].volumeM3
        setImportBusy(true)
        textStatus.text = getString(R.string.measure_importing)

        val worker = thread(name = "CoreEqRewImport", start = false) {
            val outcome = try {
                val text = readRewText(uri)
                val (freqs, mags) = RewImport.parseMagnitudeText(text)
                val source = RewMeasurement(freqs, mags)
                val analyzed = RewImport.analyze(freqs, mags, target, volume)
                Result.success(source to analyzed)
            } catch (e: Exception) {
                Log.w(TAG, "REW import failed", e)
                Result.failure<Pair<RewMeasurement, ImportResult>>(e)
            }
            runOnUiThread {
                importThread = null
                if (isFinishing || isDestroyed) return@runOnUiThread
                setImportBusy(false)
                outcome.onSuccess { (source, analyzed) ->
                    result = null
                    importInput = source
                    importResult = analyzed
                    // REW's file does not identify the TV output it measured.
                    // Tag it to the route estimate now, never as an all-output profile.
                    importedOutputEstimate = OutputRoute.current(this@MeasureActivity)
                    showImportResult(analyzed)
                }.onFailure { error ->
                    importFailed(error.message ?: "the file could not be read")
                }
            }
        }
        importThread = worker
        worker.start()
    }

    /** Bounded read: reject an oversized provider stream before it can exhaust TV memory. */
    private fun readRewText(uri: Uri): String {
        val input = contentResolver.openInputStream(uri)
            ?: throw MeasurementException("That file could not be opened.")
        val output = java.io.ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > IMPORT_MAX_BYTES) {
                    throw MeasurementException("The file is larger than 1 MB. Export one REW measurement as text and try again.")
                }
                output.write(buffer, 0, count)
            }
        }
        return String(output.toByteArray(), Charsets.UTF_8)
    }

    private fun reanalyzeImport(source: RewMeasurement) {
        val target = TARGETS[targetIndex].key
        val volume = ROOMS[roomIndex].volumeM3
        setImportBusy(true)
        textStatus.text = getString(R.string.measure_importing)
        val worker = thread(name = "CoreEqRewReanalysis", start = false) {
            val analyzed = try {
                RewImport.analyze(source.frequenciesHz, source.magnitudesDb, target, volume)
            } catch (e: Exception) {
                Log.w(TAG, "REW re-analysis failed", e)
                null
            }
            runOnUiThread {
                importThread = null
                if (isFinishing || isDestroyed) return@runOnUiThread
                setImportBusy(false)
                if (analyzed != null) {
                    importResult = analyzed
                    showImportResult(analyzed)
                } else {
                    // Do not leave an old correction available under the new
                    // target/room label if the re-analysis ever fails.
                    importResult = null
                    importInput = null
                    importedOutputEstimate = null
                    btnSave.visibility = View.GONE
                    btnSave.setText(R.string.measure_save)
                    showTargetOnly()
                    importFailed("the imported data could not be analysed for this target")
                }
            }
        }
        importThread = worker
        worker.start()
    }

    private fun setSweepReanalysisBusy(busy: Boolean) {
        sweepReanalysisInProgress = busy
        val enabled = !busy && !measuring && !importing
        btnStart.isEnabled = enabled
        btnImport.isEnabled = enabled
        btnRoom.isEnabled = enabled
        btnTarget.isEnabled = enabled
        btnSave.isEnabled = !busy && !importing
    }

    private fun setImportBusy(busy: Boolean) {
        importing = busy
        val enabled = !busy && !measuring && !sweepReanalysisInProgress
        btnStart.isEnabled = enabled
        btnImport.isEnabled = enabled
        btnRoom.isEnabled = enabled
        btnTarget.isEnabled = enabled
        btnSave.isEnabled = !busy && !sweepReanalysisInProgress
    }

    private fun importFailed(reason: String) {
        // Preserve any previous unsaved result; a bad file must not destroy it.
        progressMeasure.progress = 0
        textStatus.text = getString(R.string.measure_import_failed, reason)
        btnStart.requestFocus()
    }

    private fun showImportResult(imp: ImportResult) {
        btnSave.setText(R.string.measure_import_save)
        graphMeasure.setData(
            imp.centresHz,
            listOf(
                Series("MEASURED", ContextCompat.getColor(this, R.color.cb_slate), imp.measuredDb),
                Series("TARGET", ContextCompat.getColor(this, R.color.cb_dusk_violet), imp.targetDb),
                Series("CORRECTION", ContextCompat.getColor(this, R.color.cb_signal_cyan), imp.correctionDb)
            ),
            String.format(Locale.US, "REW MAGNITUDE · PHASE UNVERIFIED · %.0f HZ", imp.transitionHz),
            20.0,
            imp.transitionHz
        )
        val nulls = imp.nullMask.count { it }
        val analysisStatus = String.format(
            Locale.US,
            "REW %d pts · %.0f Hz–%.0f kHz · %d null%s untouched · %.0f Hz fallback; room size unused; min-phase unverified",
            imp.pointsRead, imp.floorHz, DspConstants.F_MAX / 1000,
            nulls, if (nulls == 1) "" else "s", imp.transitionHz
        )
        val outputStatus = importedOutputEstimate?.let {
            getString(R.string.measure_import_output_estimate, it.name)
        } ?: getString(R.string.measure_import_output_unknown)
        textStatus.text = analysisStatus + outputStatus
        renderQuality()
        btnSave.visibility = View.VISIBLE
        btnSave.requestFocus()
    }

    private fun fail(message: String) {
        finishMeasuring()
        result = null
        importResult = null
        importInput = null
        importedOutputEstimate = null
        btnSave.visibility = View.GONE
        btnSave.setText(R.string.measure_save)
        progressMeasure.progress = 0
        textStatus.text = getString(R.string.measure_failed, message)
        renderQuality()
        btnStart.requestFocus()
    }

    private fun finishMeasuring() {
        measuring = false
        btnStop.isEnabled = false
        stimulusPlayer.stop()
        captureEngine.stop()
        btnStart.isEnabled = true
        btnRoom.isEnabled = true
        btnTarget.isEnabled = true
        btnImport.isEnabled = true
        EqService.send(this, EqService.ACTION_RESUME)
    }

    private fun cancelMeasurement(message: String) {
        if (!measuring) return
        finishMeasuring()
        progressMeasure.progress = 0
        textStatus.text = message
        btnStart.requestFocus()
    }

    private fun saveAndFinish() {
        val now = System.currentTimeMillis()
        val imp = importResult
        if (imp != null) {
            saveImportProfile(imp, now)
            return
        }
        val r = result ?: return
        val bands = Correction.collapseToBands(
            DISPLAY_BANDS_HZ, { hz -> interpolate(hz, r.centresHz, r.correctionDb) }, -1500, 1500
        ).map { PlatformBand(it.first, it.second) }
        val output = measuredOutput
        val baseName = getString(R.string.measure_profile_name, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(now)))
        val profile = Profile(
            id = "profile-$now",
            name = if (output != null) "$baseName · ${output.name}" else baseName,
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
            qualityScore = MeasurementQuality.score(r),
            nullsUntouchedHz = r.centresHz.indices.filter { r.nullMask[it] }.map { r.centresHz[it] },
            preampDb = r.preampDb,
            filters = r.filters,
            platformBands = bands,
            curve = r.centresHz.indices.map { CurvePoint(r.centresHz[it], r.measuredDb[it], r.correctionDb[it]) },
            outputKind = OutputRoute.keyFor(output),
            outputName = output?.name
        )
        profileStore.saveProfile(profile, setAsActive = true)
        EqService.send(this, EqService.ACTION_REAPPLY)
        Toast.makeText(this, getString(R.string.measure_saved, profile.name), Toast.LENGTH_SHORT).show()
        finish()
    }

    /**
     * The profile an import builds (plan M10): no invented decay, SNR or
     * stimulus data. Because REW exports do not name a TV output, the profile is
     * tagged to the current Android route estimate, not treated as all-output.
     */
    private fun saveImportProfile(imp: ImportResult, now: Long) {
        val bands = Correction.collapseToBands(
            DISPLAY_BANDS_HZ, { hz -> interpolate(hz, imp.centresHz, imp.correctionDb) }, -1500, 1500
        ).map { PlatformBand(it.first, it.second) }
        val output = importedOutputEstimate
        val baseName = getString(R.string.measure_profile_name, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(now)))
        val profile = Profile(
            id = "profile-$now",
            name = if (output != null) "$baseName · ${output.name}" else baseName,
            timestampMs = now,
            target = TARGETS[targetIndex].key,
            micType = "REW import",
            deviceName = "REW",
            stimulus = "rew_import",
            captureSeconds = 0.0,
            volumeM3 = ROOMS[roomIndex].volumeM3,
            rt60Seconds = null,
            schroederHz = null,
            transitionHz = imp.transitionHz,
            rolloffHz = imp.floorHz,
            snrDb = null,
            nullsUntouchedHz = imp.centresHz.indices.filter { imp.nullMask[it] }.map { imp.centresHz[it] },
            preampDb = imp.preampDb,
            filters = imp.filters,
            platformBands = bands,
            curve = imp.centresHz.indices.map { CurvePoint(imp.centresHz[it], imp.measuredDb[it], imp.correctionDb[it]) },
            outputKind = OutputRoute.keyFor(output),
            outputName = output?.name,
            measurementNotes = listOf(
                REW_IMPORT_MEASUREMENT_NOTE,
                if (output == null) REW_IMPORT_OUTPUT_UNKNOWN_NOTE else REW_IMPORT_OUTPUT_ESTIMATE_NOTE
            )
        )
        // An AVR or soundbar may already correct this response. Save the
        // imported profile for export only; choosing it in Profiles is the
        // explicit action that applies it. Its output tag is only the Android
        // route estimate captured at import time; the REW file itself names none.
        profileStore.saveProfile(profile, setAsActive = false)
        Toast.makeText(this, getString(R.string.measure_import_saved, profile.name), Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMPORT) return
        val uri = data?.data
        if (resultCode == RESULT_OK && uri != null) {
            importRewFile(uri)
        }
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

    override fun onDestroy() {
        importThread?.interrupt()
        importThread = null
        super.onDestroy()
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
        private const val REQ_IMPORT = 43
        private const val REW_IMPORT_MEASUREMENT_NOTE =
            "REW magnitude-only import: no decay analysis; optional phase column is not analysed; room size cannot inform transition; transition defaults to 300 Hz; minimum-phase gate unverified."
        private const val REW_IMPORT_OUTPUT_ESTIMATE_NOTE =
            "Output association is Android's current media-route estimate at import time; confirm it matches the chain measured in REW."
        private const val REW_IMPORT_OUTPUT_UNKNOWN_NOTE =
            "Android reported no recognized media route at import time; output association is unknown and does not identify the REW measurement chain."
        /** A normal REW response is far smaller; keep a hostile provider file bounded. */
        private const val IMPORT_MAX_BYTES = 1024 * 1024
        private const val PREFS = "core_eq_measure"
        private const val KEY_ROOM = "room_index"
        private const val KEY_TARGET = "target_index"
        /** Silence recorded before the sweep, so the noise estimate and latency have room. */
        private const val LEAD_SECONDS = 0.5
        private const val CLIP_DBFS = -0.5
        /** Saved common 5-band preview; Home uses the live engine's read-back bands while correction is active. */
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
