package tv.corebuilds.eq

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.AudioEffect
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
import tv.corebuilds.eq.apply.DynamicsProcessingEngine
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.dsp.Correction
import tv.corebuilds.eq.dsp.CorrectionReport
import tv.corebuilds.eq.dsp.CustomTarget
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.ImportResult
import tv.corebuilds.eq.dsp.AutoApplyPolicy
import tv.corebuilds.eq.dsp.MeasurementQuality
import tv.corebuilds.eq.dsp.MeasurementException
import tv.corebuilds.eq.dsp.PostCorrectionCheck
import tv.corebuilds.eq.dsp.RepeatabilityAnalysis
import tv.corebuilds.eq.dsp.RepeatabilityComparison
import tv.corebuilds.eq.dsp.RewImport
import tv.corebuilds.eq.dsp.SeatAverage
import tv.corebuilds.eq.dsp.Sweep
import tv.corebuilds.eq.dsp.SweepAnalysis
import tv.corebuilds.eq.dsp.SweepResult
import tv.corebuilds.eq.dsp.TargetCurveImport
import tv.corebuilds.eq.ui.FilePicker
import tv.corebuilds.eq.dsp.Targets
import tv.corebuilds.eq.export.CaptureInfo
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.PlatformBand
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.RepeatabilityCheck
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
    private data class SweepCapture(
        val result: SweepResult,
        val samples: DoubleArray,
        val rate: Int,
        val captureInfo: CaptureInfo?,
        val micName: String?,
        val output: OutputRoute.Output?,
        val targetIndex: Int,
        val roomIndex: Int
    )

    /** What a sweep is for: a new measurement, or evidence about the one on screen. */
    private enum class SweepKind { PRIMARY, REPEAT, SEAT, CHECK }

    /** Everything a sweep is analysed with, fixed when it starts. */
    private data class SweepPlan(
        val kind: SweepKind,
        val target: String,
        val volumeM3: Double?,
        /** Top of the corrected band, as chosen when the sweep started. */
        val ceilingHz: Double,
        val targetIndex: Int,
        val roomIndex: Int,
        /** SEAT: the seats so far, first seat first. */
        val seatsBefore: List<SweepCapture> = emptyList(),
        /** CHECK: the result whose correction the sweep plays through. */
        val checked: SweepResult? = null
    )

    private lateinit var profileStore: ProfileStore
    private lateinit var stimulusPlayer: StimulusPlayer
    private lateinit var captureEngine: CaptureEngine

    private lateinit var graphMeasure: CurveGraphView
    private lateinit var progressMeasure: ProgressBar
    private lateinit var textStatus: TextView
    private lateinit var textQuality: TextView
    private lateinit var textRepeatability: TextView
    private lateinit var btnStart: Button
    private lateinit var btnRepeat: Button
    private lateinit var btnStop: Button
    private lateinit var btnSave: Button
    private lateinit var btnImport: Button
    private lateinit var btnRoom: Button
    private lateinit var btnTarget: Button
    private lateinit var btnCeiling: Button
    private lateinit var cardRefine: View
    private lateinit var btnAddSeat: Button
    private lateinit var btnCheck: Button

    private var roomIndex = 1
    private var targetIndex = 0
    private var ceilingIndex = CorrectionLimits.CeilingChoice.DEFAULT.index
    private var useCustomTarget = false
    private lateinit var btnTargetImport: Button
    private var result: SweepResult? = null
    /** A REW import replaces the sweep result for display and saving (plan M10). */
    private var importResult: ImportResult? = null
    private var importInput: RewMeasurement? = null
    private var importing = false
    private var importThread: Thread? = null
    private var micName: String? = null
    private var captureInfo: CaptureInfo? = null
    private var measuredOutput: OutputRoute.Output? = null
    /** The first sweep remains the saved profile; a repeated sweep is kept separately for comparison. */
    private var repeatBaseline: SweepCapture? = null
    private var repeatCapture: SweepCapture? = null
    private var repeatComparison: RepeatabilityComparison? = null
    private var repeatabilityCheck: RepeatabilityCheck? = null
    private var repeatComparisonClearedDueToSettings = false
    private var activeMicName: String? = null
    private var activeOutput: OutputRoute.Output? = null
    /** The sweep to start once the microphone permission is granted. */
    private var pendingKind: SweepKind? = null
    private var measurementKind = SweepKind.PRIMARY
    /** Seats averaged into [result], first seat first; empty while [result] is one seat. */
    private var seats: List<SweepCapture> = emptyList()
    private var seatAverage: SeatAverage.Averaged? = null
    /** The last check sweep of [result]'s correction; cleared whenever [result] changes. */
    private var correctionCheck: PostCorrectionCheck.Comparison? = null
    /** Why the check's chain differed from the measurement's, when it did. */
    private var correctionCheckNote: String? = null
    /** The correction a check sweep plays through; released when the sweep ends. */
    private var checkEffect: AudioEffect? = null
    /** Current media-route estimate captured when an external REW file is imported. */
    private var importedOutputEstimate: OutputRoute.Output? = null
    /** Bumped per measurement, so a late callback from an earlier one is ignored. */
    @Volatile private var measurementGeneration = 0
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
        textRepeatability = findViewById(R.id.text_measure_repeatability)
        btnStart = findViewById(R.id.btn_measure_start)
        btnRepeat = findViewById(R.id.btn_measure_repeat)
        btnStop = findViewById(R.id.btn_measure_stop)
        btnStop.isEnabled = false // only a running sweep can be stopped
        btnSave = findViewById(R.id.btn_measure_save)
        btnImport = findViewById(R.id.btn_measure_import)
        btnRoom = findViewById(R.id.btn_room_size)
        btnTarget = findViewById(R.id.btn_target)
        btnCeiling = findViewById(R.id.btn_ceiling)
        btnTargetImport = findViewById(R.id.btn_target_import)
        cardRefine = findViewById(R.id.card_refine)
        btnAddSeat = findViewById(R.id.btn_measure_add_seat)
        btnCheck = findViewById(R.id.btn_measure_check)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        roomIndex = prefs.getInt(KEY_ROOM, 1).coerceIn(ROOMS.indices)
        targetIndex = prefs.getInt(KEY_TARGET, 0).coerceIn(TARGETS.indices)
        // A stored index from a build with a different list is clamped by
        // CeilingChoice rather than trusted.
        ceilingIndex = prefs.getInt(KEY_CEILING, CorrectionLimits.CeilingChoice.DEFAULT.index)
        restoreCustomTarget(prefs)

        btnRoom.setOnClickListener {
            roomIndex = (roomIndex + 1) % ROOMS.size
            prefs.edit().putInt(KEY_ROOM, roomIndex).apply()
            clearRepeatComparisonForSettingsChange()
            refreshChoices()
        }
        btnTarget.setOnClickListener {
            // The first press leaves an imported target and returns to the built-in choice.
            if (useCustomTarget) {
                useCustomTarget = false
            } else {
                targetIndex = (targetIndex + 1) % TARGETS.size
            }
            prefs.edit().putInt(KEY_TARGET, targetIndex).putBoolean(KEY_USE_CUSTOM, useCustomTarget).apply()
            clearRepeatComparisonForSettingsChange()
            refreshChoices()
        }
        btnTargetImport.setOnClickListener { pickTargetFile() }
        btnCeiling.setOnClickListener {
            ceilingIndex = CorrectionLimits.CeilingChoice(ceilingIndex).next().index
            prefs.edit().putInt(KEY_CEILING, ceilingIndex).apply()
            renderCeiling()
            refreshChoices()
        }
        btnStart.setOnClickListener { startMeasurement() }
        btnRepeat.setOnClickListener { startRepeatMeasurement() }
        btnAddSeat.setOnClickListener { startMeasurement(SweepKind.SEAT) }
        btnCheck.setOnClickListener { startMeasurement(SweepKind.CHECK) }
        btnStop.setOnClickListener { cancelMeasurement("Stopped. Nothing was saved.") }
        btnSave.setOnClickListener { saveAndFinish() }
        btnImport.setOnClickListener { pickRewImport() }

        refreshChoices()
        textStatus.text = getString(R.string.measure_ready)
        btnRoom.requestFocus()
    }

    /**
     * The ceiling the next analysis will use. [CorrectionLimits.ceilingHz]
     * clamps it, so a corrupt pref cannot widen the band.
     */
    private fun chosenCeilingHz(): Double = CorrectionLimits.CeilingChoice(ceilingIndex).hz

    private fun renderCeiling() {
        val choice = CorrectionLimits.CeilingChoice(ceilingIndex)
        btnCeiling.text = if (choice.isFullBand) {
            getString(R.string.measure_ceiling_full, choice.hz.toInt())
        } else {
            getString(R.string.measure_ceiling_button, choice.hz.toInt())
        }
    }

    private fun refreshChoices() {
        btnRoom.text = getString(R.string.measure_room_button, ROOMS[roomIndex].label)
        btnTarget.text = if (useCustomTarget) {
            getString(R.string.measure_target_custom)
        } else {
            getString(R.string.measure_target_button, TARGETS[targetIndex].label)
        }
        renderCeiling()
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
            val target = activeTargetKey()
            val volume = ROOMS[roomIndex].volumeM3
            val ceiling = chosenCeilingHz()
            val selectedTargetIndex = targetIndex
            val selectedRoomIndex = roomIndex
            val seatCaptures = seats
            setSweepReanalysisBusy(true)
            textStatus.text = getString(R.string.measure_analysing)
            thread(name = "CoreEqReanalysis") {
                // Every seat is analysed again and re-averaged: one seat on the old target
                // would put a different correction under the new label.
                val updated: Triple<SweepResult, List<SweepCapture>, SeatAverage.Averaged?>? = try {
                    if (seatCaptures.size >= 2) {
                        val again = seatCaptures.map {
                            it.copy(
                                result = SweepAnalysis.analyze(it.samples, it.rate, target, volume, ceilingHz = ceiling),
                                targetIndex = selectedTargetIndex,
                                roomIndex = selectedRoomIndex
                            )
                        }
                        val averaged = SeatAverage.average(again.map { it.result }, target, volume)
                        Triple(averaged.result, again, averaged)
                    } else {
                        Triple(
                            SweepAnalysis.analyze(capture, lastRate, target, volume, ceilingHz = ceiling),
                            emptyList(), null
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Re-analysis failed", e)
                    null
                }
                runOnUiThread {
                    if (generation != sweepReanalysisGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    correctionCheck = null
                    correctionCheckNote = null
                    if (updated != null) {
                        result = updated.first
                        seats = updated.second
                        seatAverage = updated.third
                        setSweepReanalysisBusy(false)
                        showResult(updated.first)
                    } else {
                        result = null
                        seats = emptyList()
                        seatAverage = null
                        setSweepReanalysisBusy(false)
                        btnSave.visibility = View.GONE
                        btnRepeat.visibility = View.GONE
                        btnStart.setText(R.string.measure_start)
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

    private fun clearRepeatComparisonForSettingsChange() {
        if (repeatCapture == null && repeatabilityCheck == null) return
        repeatBaseline = null
        repeatCapture = null
        repeatComparison = null
        repeatabilityCheck = null
        repeatComparisonClearedDueToSettings = true
        renderRepeatabilityText()
    }

    private fun showTargetOnly() {
        val centres = DspConstants.ISO_CENTRES_HZ.filter { it <= DspConstants.F_MAX }.toDoubleArray()
        graphMeasure.setData(
            centres,
            listOf(Series("TARGET", ContextCompat.getColor(this, R.color.cb_signal_cyan),
                Targets.targetCurve(activeTargetKey(), centres))),
            "TARGET · ${activeTargetTitle().uppercase(Locale.US)}",
            20.0,
            Correction.transitionHz(ROOMS[roomIndex].volumeM3, null)
        )
        renderQuality()
        updateRefineButtons()
    }

    /** Add a seat and Check belong to a sweep on screen; an import or no result hides them. */
    private fun updateRefineButtons() {
        val sweep = result != null && importResult == null
        val seatCount = maxOf(1, seats.size)
        cardRefine.visibility = if (sweep) View.VISIBLE else View.GONE
        btnAddSeat.visibility = if (sweep && seatCount < SeatAverage.MAX_SEATS) View.VISIBLE else View.GONE
        btnAddSeat.text = getString(R.string.measure_add_seat_button, seatCount + 1, SeatAverage.MAX_SEATS)
        // A same-spot repeat describes one seat; once seats are averaged it has nothing to compare.
        if (seats.size >= 2) btnRepeat.visibility = View.GONE
        val idle = !measuring && !importing && !sweepReanalysisInProgress
        btnAddSeat.isEnabled = idle
        btnCheck.isEnabled = idle
    }

    /** The seats and check lines, under whatever the repeat line says. */
    private fun evidenceLines(): List<String> {
        if (result == null || importResult != null) return emptyList()
        val lines = mutableListOf<String>()
        val averaged = seatAverage
        if (averaged != null && seats.size >= 2) {
            lines += String.format(
                Locale.US, getString(R.string.measure_seats_summary),
                averaged.seatsUsed, averaged.boostBudgetDb
            )
            val out = averaged.leftOut
            val offDb = averaged.leftOutDb
            if (out != null && offDb != null) {
                lines += String.format(Locale.US, getString(R.string.measure_seats_left_out), out + 1, offDb)
            }
        }
        val check = correctionCheck
        if (check != null) {
            val after = check.after
            val summary = String.format(
                Locale.US, getString(R.string.measure_check_summary),
                after.bands, after.rmsDb ?: 0.0, check.before.rmsDb ?: 0.0, check.predicted.rmsDb ?: 0.0,
                after.worstDb ?: 0.0, hzLabel(after.worstHz ?: 0.0)
            )
            lines += correctionCheckNote?.let { getString(R.string.measure_check_provisional, "$summary $it") } ?: summary
        }
        return lines
    }

    private fun hzLabel(hz: Double): String =
        if (hz >= 1000.0) String.format(Locale.US, "%.1f kHz", hz / 1000.0) else String.format(Locale.US, "%.0f Hz", hz)

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
                ).append("\n").append(getString(R.string.quality_accuracy_caveat))
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
        renderRepeatabilityText()
    }

    private fun renderRepeatabilityText() {
        val check = repeatabilityCheck
        val sweepAvailable = result != null && importResult == null
        when {
            repeatComparisonClearedDueToSettings && sweepAvailable -> {
                textRepeatability.setText(R.string.measure_repeat_changed)
                textRepeatability.visibility = View.VISIBLE
            }
            check?.metrics != null -> {
                val metrics = check.metrics
                val summary = String.format(
                    Locale.US,
                    getString(R.string.measure_repeat_summary),
                    metrics.comparedBands,
                    metrics.lowFrequencyHz,
                    metrics.highFrequencyHz / 1000.0,
                    metrics.medianAbsDifferenceDb,
                    metrics.rmsDifferenceDb,
                    metrics.p95AbsDifferenceDb,
                    metrics.levelOffsetDb
                )
                textRepeatability.text = listOfNotNull(summary, check.note).joinToString("\n")
                textRepeatability.visibility = View.VISIBLE
            }
            check != null -> {
                textRepeatability.text = getString(
                    R.string.measure_repeat_uncompared,
                    check.note ?: "too few clean shared bands"
                )
                textRepeatability.visibility = View.VISIBLE
            }
            sweepAvailable && seats.size < 2 -> {
                textRepeatability.setText(R.string.measure_repeat_intro)
                textRepeatability.visibility = View.VISIBLE
            }
            else -> {
                textRepeatability.text = ""
                textRepeatability.visibility = View.GONE
            }
        }
        val extra = evidenceLines()
        if (extra.isNotEmpty()) {
            textRepeatability.text = (listOf(textRepeatability.text.toString()).filter { it.isNotBlank() } + extra)
                .joinToString("\n")
            textRepeatability.visibility = View.VISIBLE
        }
    }

    /** The single sweep on screen (the first seat, once seats are averaged) as a capture. */
    private fun currentCapture(): SweepCapture? {
        seats.firstOrNull()?.let { return it }
        val current = result ?: return null
        val samples = lastCapture ?: return null
        if (importResult != null) return null
        return SweepCapture(
            result = current,
            samples = samples,
            rate = lastRate,
            captureInfo = captureInfo,
            micName = micName,
            output = measuredOutput,
            targetIndex = targetIndex,
            roomIndex = roomIndex
        )
    }

    private fun startRepeatMeasurement() {
        if (seats.size >= 2) return
        val baseline = currentCapture() ?: return
        startMeasurement(SweepKind.REPEAT, baseline)
    }

    private fun startMeasurement(kind: SweepKind = SweepKind.PRIMARY, repeatAgainst: SweepCapture? = null) {
        if (measuring || importing || sweepReanalysisInProgress) return
        // Seats and checks add to a sweep on screen; without one there is nothing to add to.
        val seatsBefore = if (kind == SweepKind.SEAT) seats.ifEmpty { listOfNotNull(currentCapture()) } else emptyList()
        if (kind == SweepKind.SEAT && (seatsBefore.isEmpty() || seatsBefore.size >= SeatAverage.MAX_SEATS)) return
        val checked = if (kind == SweepKind.CHECK) (result?.takeIf { importResult == null } ?: return) else null
        if (kind == SweepKind.REPEAT && repeatAgainst == null) return
        sweepReanalysisGeneration += 1
        if (!captureEngine.hasPermission()) {
            pendingKind = kind
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        pendingKind = null
        // The check plays through the correction itself, on a session of its own: the
        // service stays suspended below, so the sweep is corrected once, by exactly this
        // profile, whether the service would have reached it through the output mix or not.
        val playbackSession = if (checked != null) {
            try {
                openCheckCorrection(buildSweepProfile(checked, System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.w(TAG, "Check correction refused", e)
                releaseCheckCorrection()
                textStatus.text = "${textStatus.text}\nCheck not run: this TV refused to run the correction on the " +
                    "check sweep (${e.message ?: e.javaClass.simpleName}). The correction is unchanged."
                return
            }
        } else {
            null
        }
        measurementKind = kind
        repeatComparisonClearedDueToSettings = false
        // A seat or a check leaves the same-spot repeat as it is until it succeeds.
        if (kind == SweepKind.PRIMARY || kind == SweepKind.REPEAT) {
            repeatBaseline = repeatAgainst
            repeatCapture = null
            repeatComparison = null
            repeatabilityCheck = null
        }
        if (kind == SweepKind.PRIMARY) {
            result = null
            lastCapture = null
            importResult = null
            importInput = null
            importedOutputEstimate = null
            measuredOutput = null
            micName = null
            captureInfo = null
            seats = emptyList()
            seatAverage = null
            correctionCheck = null
            correctionCheckNote = null
            btnStart.setText(R.string.measure_start)
            btnRepeat.visibility = View.GONE
        }
        activeMicName = null
        activeOutput = null
        measuring = true
        btnStop.visibility = View.VISIBLE
        btnStop.isEnabled = true
        btnStop.requestFocus() // Start is disabled for the sweep; keep focus somewhere usable
        measurementGeneration += 1
        val generation = measurementGeneration
        val selectedTargetIndex = targetIndex
        val selectedRoomIndex = roomIndex
        val plan = SweepPlan(
            kind = kind,
            target = activeTargetKey(),
            volumeM3 = ROOMS[selectedRoomIndex].volumeM3,
            ceilingHz = chosenCeilingHz(),
            targetIndex = selectedTargetIndex,
            roomIndex = selectedRoomIndex,
            seatsBefore = seatsBefore,
            checked = checked
        )
        btnStart.isEnabled = false
        btnRepeat.isEnabled = false
        btnRoom.isEnabled = false
        btnTarget.isEnabled = false
        btnCeiling.isEnabled = false
        btnSave.setText(R.string.measure_save)
        btnSave.visibility = View.GONE
        btnImport.isEnabled = false
        updateRefineButtons()
        progressMeasure.progress = 0
        textStatus.text = getString(R.string.measure_opening_mic)
        renderQuality()
        EqService.send(this, EqService.ACTION_SUSPEND) // the service's correction must not colour the sweep

        val total = LEAD_SECONDS + Sweep.STIMULUS_SECONDS + SweepAnalysis.TAIL_SECONDS
        captureEngine.start(total, object : CaptureListener {
            override fun onRecording(info: CaptureInfo) {
                if (measurementGeneration != generation) return
                activeMicName = info.inputDeviceName?.takeIf { it.isNotBlank() }
                    ?: info.inputDeviceType?.takeIf { it.isNotBlank() }
                    ?: if (info.activeMicrophoneCount != null) "Android microphone input" else "remote microphone"
                // Keep an output estimate as a fallback if Android does not
                // report which device receives the sweep's own AudioTrack.
                val ranked = OutputRoute.current(this@MeasureActivity)
                activeOutput = ranked
                btnStart.postDelayed({
                    if (measuring && measurementGeneration == generation) stimulusPlayer.playSweep(
                        onError = { msg -> runOnUiThread { fail(msg, kind) } },
                        onRouted = { devices ->
                            val tagged = OutputRoute.tag(ranked, devices)
                            runOnUiThread {
                                if (measuring && measurementGeneration == generation) activeOutput = tagged
                            }
                        },
                        sessionId = playbackSession
                    )
                }, (LEAD_SECONDS * 1000).toLong())
            }

            override fun onLevel(secondsElapsed: Float, totalSeconds: Float, rmsDbfs: Float, peakDbfs: Float) {
                runOnUiThread {
                    if (!measuring || measurementGeneration != generation) return@runOnUiThread
                    progressMeasure.progress = ((secondsElapsed / totalSeconds) * 100f).roundToInt().coerceIn(0, 100)
                    textStatus.text = String.format(
                        Locale.US, "%.1f s of %.0f s · mic level %.0f dBFS · peak %.0f dBFS%s",
                        secondsElapsed, totalSeconds, rmsDbfs, peakDbfs,
                        if (peakDbfs > CLIP_DBFS) " · TOO LOUD" else ""
                    )
                }
            }

            override fun onCaptured(samples: DoubleArray, sampleRate: Int, info: CaptureInfo) {
                if (measurementGeneration != generation) return
                val outputAtCapture = activeOutput
                val micAtCapture = activeMicName
                runOnUiThread { textStatus.text = getString(R.string.measure_analysing) }
                analyse(samples, sampleRate, info, plan, micAtCapture, outputAtCapture, generation)
            }

            override fun onError(message: String) {
                runOnUiThread {
                    if (measuring && measurementGeneration == generation) fail(message, kind)
                }
            }
        })
    }

    /**
     * A fresh audio session with [profile]'s correction on it, for a check sweep to play
     * into. The effect goes on before the track exists, so Android does not make the
     * sweep a fast track, which cannot carry one. The limiter is the default protection-only
     * one, and the tone layers are left off: they are not the correction being checked.
     */
    private fun openCheckCorrection(profile: Profile): Int {
        releaseCheckCorrection()
        val audio = getSystemService(AudioManager::class.java)
        val session = audio.generateAudioSessionId()
        check(session > 0) { "Android gave no audio session (generateAudioSessionId returned $session)" }
        checkEffect = DynamicsProcessingEngine.create(session, profile).effect
        return session
    }

    private fun releaseCheckCorrection() {
        val effect = checkEffect ?: return
        checkEffect = null
        runCatching { effect.enabled = false }
        runCatching { effect.release() }
    }

    private var lastCapture: DoubleArray? = null
    private var lastRate = CaptureEngine.ANALYSIS_RATE

    private fun analyse(
        samples: DoubleArray,
        rate: Int,
        info: CaptureInfo,
        plan: SweepPlan,
        capturedMicName: String?,
        capturedOutput: OutputRoute.Output?,
        generation: Int
    ) {
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
                    Result.success(
                        SweepAnalysis.analyze(samples, rate, plan.target, plan.volumeM3, ceilingHz = plan.ceilingHz)
                    )
                } catch (e: MeasurementException) {
                    Result.failure(e)
                } catch (e: Exception) {
                    Log.e(TAG, "Analysis crashed", e)
                    Result.failure(MeasurementException("Analysis failed: ${e.javaClass.simpleName}: ${e.message}"))
                }
            }
            // The average and the comparison refit and re-read whole curves: worker-thread work.
            val analyzed = outcome.getOrNull()
            val averaged = if (plan.kind == SweepKind.SEAT && analyzed != null) {
                try {
                    SeatAverage.average(plan.seatsBefore.map { it.result } + analyzed, plan.target, plan.volumeM3)
                } catch (e: Exception) {
                    Log.w(TAG, "Seat average failed", e)
                    null
                }
            } else null
            val comparison = if (plan.kind == SweepKind.CHECK && analyzed != null) {
                plan.checked?.let { PostCorrectionCheck.compare(it, analyzed) }
            } else null
            runOnUiThread {
                if (!measuring || measurementGeneration != generation || isFinishing || isDestroyed) return@runOnUiThread
                outcome.onSuccess { sweep ->
                    finishMeasuring()
                    val capture = SweepCapture(
                        result = sweep,
                        samples = samples,
                        rate = rate,
                        captureInfo = info,
                        micName = capturedMicName,
                        output = capturedOutput,
                        targetIndex = plan.targetIndex,
                        roomIndex = plan.roomIndex
                    )
                    when (plan.kind) {
                        SweepKind.PRIMARY -> installPrimaryCapture(capture)
                        SweepKind.REPEAT -> completeRepeat(capture)
                        SweepKind.SEAT -> completeSeat(plan.seatsBefore, capture, averaged)
                        SweepKind.CHECK -> completeCheck(capture, comparison)
                    }
                }.onFailure { fail(it.message ?: it.javaClass.simpleName, plan.kind) }
            }
        }
    }

    private fun installPrimaryCapture(capture: SweepCapture) {
        result = capture.result
        lastCapture = capture.samples
        lastRate = capture.rate
        captureInfo = capture.captureInfo
        micName = capture.micName
        measuredOutput = capture.output
        importResult = null
        importInput = null
        importedOutputEstimate = null
        repeatBaseline = null
        repeatCapture = null
        repeatComparison = null
        repeatabilityCheck = null
        seats = emptyList()
        seatAverage = null
        correctionCheck = null
        correctionCheckNote = null
        btnStart.setText(R.string.measure_start_new)
        btnRepeat.visibility = View.VISIBLE
        btnRepeat.isEnabled = true
        showResult(capture.result)
        btnSave.visibility = View.VISIBLE
        btnSave.requestFocus()
        maybeAutoApply(capture)
    }

    /**
     * Once per output kind, a clean (GREEN) first measurement is saved and applied without a
     * tap. Later measurements, weaker recordings, and outputs Android did not name are only
     * shown, as before. The applied kinds are kept on the TV.
     */
    private fun maybeAutoApply(capture: SweepCapture) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val device = OutputRoute.keyFor(capture.output).takeIf { it != OutputRoute.UNKNOWN }
        val applied = prefs.getStringSet(KEY_AUTO_APPLIED, emptySet()) ?: emptySet()
        if (AutoApplyPolicy.decide(device, applied) != AutoApplyPolicy.Action.AUTO_APPLY) return
        val tier = MeasurementQuality.tier(MeasurementQuality.score(capture.result))
        if (tier != MeasurementQuality.Tier.GREEN) return
        val profile = buildSweepProfile(capture.result, System.currentTimeMillis())
        // A failed save must not count as applied: the next clean measurement may still auto-apply.
        try {
            profileStore.saveProfile(profile, setAsActive = true)
        } catch (e: Exception) {
            Log.w(TAG, "Auto-apply could not save the profile", e)
            textStatus.text = getString(R.string.measure_auto_apply_failed)
            return
        }
        EqService.send(this, EqService.ACTION_REAPPLY)
        prefs.edit()
            .putStringSet(KEY_AUTO_APPLIED, AutoApplyPolicy.recordApplied(device!!, applied))
            .apply()
        textStatus.text = getString(R.string.measure_auto_applied, profile.name)
    }

    /**
     * A new seat joins the average only on the chain the first seat was measured on: another
     * output or microphone would average two different systems.
     */
    private fun completeSeat(before: List<SweepCapture>, capture: SweepCapture, averaged: SeatAverage.Averaged?) {
        val first = before.firstOrNull()
        val refused = when {
            first == null -> "The first seat is no longer available."
            else -> repeatPathMismatch(first, capture)?.let { "$it Keep the same output and remote for every seat." }
                ?: if (averaged == null) "The seats could not be averaged." else null
        }
        if (refused != null || averaged == null) {
            keepResultAfter("Seat not added: $refused The result on screen is unchanged.")
            return
        }
        // A same-spot repeat described the first seat alone; the average replaces it.
        repeatBaseline = null
        repeatCapture = null
        repeatComparison = null
        repeatabilityCheck = null
        repeatComparisonClearedDueToSettings = false
        seats = before + capture
        seatAverage = averaged
        correctionCheck = null
        correctionCheckNote = null
        result = averaged.result
        btnStart.setText(R.string.measure_start_new)
        showResult(averaged.result)
        btnSave.visibility = View.VISIBLE
        btnSave.requestFocus()
    }

    /** A check changes nothing but the evidence shown: the correction and what Save keeps stay put. */
    private fun completeCheck(capture: SweepCapture, comparison: PostCorrectionCheck.Comparison?) {
        if (result == null) {
            fail("The measurement being checked is no longer on screen.", SweepKind.CHECK)
            return
        }
        if (comparison == null) {
            keepResultAfter(getString(R.string.measure_check_unscored))
            return
        }
        correctionCheck = comparison
        correctionCheckNote = currentCapture()?.let { repeatPathMismatch(it, capture) }
        keepResultAfter(null)
    }

    /** Back to the result on screen after a seat or check sweep, with [note] under the summary. */
    private fun keepResultAfter(note: String?) {
        val r = result ?: return
        btnStart.setText(R.string.measure_start_new)
        btnRepeat.visibility = View.VISIBLE
        btnRepeat.isEnabled = true
        btnSave.setText(R.string.measure_save)
        btnSave.visibility = View.VISIBLE
        showResult(r, repeatComparison)
        if (note != null) textStatus.text = "${textStatus.text}\n$note"
        btnSave.requestFocus()
    }

    private fun completeRepeat(capture: SweepCapture) {
        val baseline = repeatBaseline
        if (baseline == null) {
            fail("The first sweep is no longer available. Measure again.", SweepKind.REPEAT)
            return
        }
        val pathMismatch = repeatPathMismatch(baseline, capture)
        val comparison = RepeatabilityAnalysis.compare(baseline.result, capture.result)
        val canCompare = pathMismatch == null
        val inputUnknown = listOf(baseline.captureInfo, capture.captureInfo).any { info ->
            info == null || (info.inputDeviceType.isNullOrBlank() && info.inputDeviceName.isNullOrBlank())
        }
        val metadataUnknown = inputUnknown || baseline.output == null || capture.output == null ||
            OutputRoute.keyFor(baseline.output) == OutputRoute.UNKNOWN ||
            OutputRoute.keyFor(capture.output) == OutputRoute.UNKNOWN
        val metrics = if (canCompare) comparison.metrics else null
        val note = when {
            pathMismatch != null -> "$pathMismatch No same-path repeatability summary was calculated."
            metrics == null -> comparison.reason ?: "There were not enough comparable bands for a repeatability summary."
            metadataUnknown -> getString(R.string.measure_repeat_unknown_output)
            else -> null
        }
        repeatCapture = capture
        repeatComparison = comparison.takeIf { canCompare }
        repeatabilityCheck = RepeatabilityCheck(
            metrics = metrics,
            comparisonCurve = capture.result.centresHz.indices.map { index ->
                CurvePoint(capture.result.centresHz[index], capture.result.measuredDb[index], capture.result.correctionDb[index])
            },
            captureInfo = capture.captureInfo,
            outputKind = OutputRoute.keyFor(capture.output),
            outputName = capture.output?.name,
            snrDb = capture.result.snrDb,
            driftPpm = capture.result.driftPpm,
            note = note
        )
        // Keep the first sweep as the profile curve. A repeat is evidence, not
        // an implicit average or an automatic replacement of the correction.
        result = baseline.result
        lastCapture = baseline.samples
        lastRate = baseline.rate
        captureInfo = baseline.captureInfo
        micName = baseline.micName
        measuredOutput = baseline.output
        btnStart.setText(R.string.measure_start_new)
        btnRepeat.visibility = View.VISIBLE
        btnRepeat.isEnabled = true
        showResult(baseline.result, repeatComparison)
        btnSave.visibility = View.VISIBLE
        btnSave.requestFocus()
    }

    private fun repeatPathMismatch(first: SweepCapture, second: SweepCapture): String? {
        if (first.targetIndex != second.targetIndex || first.roomIndex != second.roomIndex) {
            return "Target or room-size settings changed between sweeps."
        }
        val outputMismatch = RepeatabilityAnalysis.reportedOutputMismatch(
            OutputRoute.keyFor(first.output), first.output?.name,
            OutputRoute.keyFor(second.output), second.output?.name
        )
        return outputMismatch ?: RepeatabilityAnalysis.reportedInputMismatch(first.captureInfo, second.captureInfo)
    }

    private fun showResult(r: SweepResult, comparison: RepeatabilityComparison? = null) {
        btnSave.setText(R.string.measure_save)
        val graphSeries = mutableListOf(
            Series("MEASURED", ContextCompat.getColor(this, R.color.cb_slate), r.measuredDb)
        )
        val alignedRepeat = comparison?.alignedRepeatDb?.takeIf { values -> values.any { it.isFinite() } }
        if (alignedRepeat != null) {
            graphSeries += Series("REPEAT · LEVEL-ALIGNED", ContextCompat.getColor(this, R.color.cb_warning), alignedRepeat)
        } else {
            repeatCapture?.let { repeat ->
                graphSeries += Series("REPEAT · RAW, NOT COMPARED", ContextCompat.getColor(this, R.color.cb_warning), repeat.result.measuredDb)
            }
        }
        graphSeries += Series("TARGET", ContextCompat.getColor(this, R.color.cb_dusk_violet), r.targetDb)
        graphSeries += Series("CORRECTION", ContextCompat.getColor(this, R.color.cb_signal_cyan), r.correctionDb)
        correctionCheck?.let { check ->
            // Drawn over the bands both sweeps share; past those the line simply ends.
            val values = DoubleArray(r.centresHz.size) { k -> check.afterDb.getOrElse(k) { Double.NaN } }
            graphSeries += Series("CHECK · CORRECTION ON", ContextCompat.getColor(this, R.color.cb_success), values)
        }
        val averagedSeats = if (seats.size >= 2) seats.size - (if (seatAverage?.leftOut != null) 1 else 0) else 0
        val title = when {
            repeatCapture != null ->
                String.format(Locale.US, "SAME-POSITION REPEAT · FULL CORRECTION BELOW %.0f HZ", r.transitionHz)
            averagedSeats >= 2 ->
                String.format(Locale.US, "%d-SEAT AVERAGE vs TARGET · FULL CORRECTION BELOW %.0f HZ", averagedSeats, r.transitionHz)
            else ->
                String.format(Locale.US, "MEASURED vs TARGET · FULL CORRECTION BELOW %.0f HZ", r.transitionHz)
        }
        graphMeasure.setData(r.centresHz, graphSeries, title, 20.0, r.transitionHz)
        val rt = r.rt60Seconds?.let { String.format(Locale.US, "RT60 %.2f s", it) } ?: "RT60 not measurable (noisy decay)"
        val nulls = r.nullMask.count { it }
        val gated = r.centresHz.indices.count { !r.minPhaseOk[it] && r.centresHz[it] >= r.floorHz }
        val noisy = r.centresHz.indices.count {
            r.centresHz[it] >= r.floorHz && r.bandSnrDb.getOrElse(it) { Double.MAX_VALUE } < SweepAnalysis.BAND_MIN_SNR_DB
        }
        // The graph's last band is where the *measurement* stopped; the
        // correction may have stopped earlier at the user's ceiling. Reporting
        // the band edge as the corrected range was the old, wrong claim.
        val top = r.fMaxHz.coerceAtMost(r.centresHz.lastOrNull() ?: DspConstants.F_MAX)
        val clock = r.driftPpm?.takeIf { kotlin.math.abs(it) > SweepAnalysis.MIN_DRIFT_PPM }
            ?.let { String.format(Locale.US, " · clock drift %+.0f ppm corrected", it) } ?: ""
        val resultSummary = String.format(
            Locale.US,
            "%s · corrects %.0f Hz–%.1f kHz · %d null%s, %d non-minimum-phase band%s and %d noisy band%s left alone · SNR %.0f dB%s",
            rt, r.floorHz, top / 1000, nulls, if (nulls == 1) "" else "s",
            gated, if (gated == 1) "" else "s", noisy, if (noisy == 1) "" else "s", r.snrDb, clock
        )
        val output = measuredOutput
        val outputSummary = when {
            output?.kind == OutputRoute.AMBIGUOUS -> getString(R.string.measure_output_ambiguous)
            output != null -> getString(
                R.string.measure_output_evidence,
                output.name,
                OutputRoute.evidenceDescription(output.evidence)
            )
            else -> getString(R.string.measure_output_unreported)
        }
        // Why bands were left alone, and what the room does that EQ cannot fix.
        val reportLines = CorrectionReport.summaryLines(r, limit = 2)
        val adviceLines = CorrectionReport.roomAdvice(r)
        textStatus.text = (
            listOfNotNull(resultSummary, outputSummary, captureInfo?.summary()) +
                reportLines + adviceLines
            ).joinToString("\n")
        renderQuality()
        updateRefineButtons()
    }

    /** REW measurement import (plan M10): File → Export → Measurement as text. */
    /** The key the analysis and the saved profile use: "custom" while an imported target is active. */
    private fun activeTargetKey(): String =
        if (useCustomTarget && CustomTarget.points != null) CustomTarget.KEY else TARGETS[targetIndex].key

    /** The imported curve a profile saved now must keep, so it still draws its own target after the next import. */
    private fun activeTargetPoints(): List<Pair<Double, Double>> =
        if (activeTargetKey() == CustomTarget.KEY) CustomTarget.points.orEmpty() else emptyList()

    private fun activeTargetTitle(): String =
        if (useCustomTarget && CustomTarget.points != null) getString(R.string.measure_target_custom_title)
        else TARGETS[targetIndex].label

    private fun restoreCustomTarget(prefs: android.content.SharedPreferences) {
        useCustomTarget = prefs.getBoolean(KEY_USE_CUSTOM, false)
        val saved = prefs.getString(KEY_CUSTOM_TARGET, null)
        val parsed = saved?.let { TargetCurveImport.parse(it) }
        CustomTarget.points = (parsed as? TargetCurveImport.Result.Ok)?.points
        if (CustomTarget.points == null) useCustomTarget = false
    }

    private fun pickTargetFile() {
        if (measuring || importing) return
        if (!FilePicker.open(this, REQ_TARGET_IMPORT, FilePicker.TEXT_TYPES)) {
            textStatus.text = getString(R.string.measure_target_import_failed,
                getString(R.string.measure_target_picker_missing))
        }
    }

    /** Reads a GraphicEQ file as the target. Nothing changes unless the whole file parses. */
    private fun loadCustomTarget(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                // Read at most MAX_BYTES + 1 bytes; readNBytes needs API 33, and minSdk is 30.
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var total = 0
                while (total <= TargetCurveImport.MAX_BYTES) {
                    val n = stream.read(buf, 0, minOf(buf.size, TargetCurveImport.MAX_BYTES + 1 - total))
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                if (total > TargetCurveImport.MAX_BYTES) null else out.toString(Charsets.UTF_8.name())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Target file could not be read", e)
            null
        }
        if (text == null) {
            textStatus.text = getString(R.string.measure_target_import_failed,
                getString(R.string.measure_target_unreadable))
            return
        }
        when (val parsed = TargetCurveImport.parse(text)) {
            is TargetCurveImport.Result.Error ->
                textStatus.text = getString(R.string.measure_target_import_failed, parsed.message)
            is TargetCurveImport.Result.Ok -> {
                CustomTarget.points = parsed.points
                useCustomTarget = true
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_CUSTOM_TARGET, text)
                    .putBoolean(KEY_USE_CUSTOM, true)
                    .apply()
                clearRepeatComparisonForSettingsChange()
                refreshChoices()
                if (parsed.notes.isNotEmpty()) textStatus.text = parsed.notes.joinToString(" ")
            }
        }
    }

    private fun pickRewImport() {
        if (measuring || importing) return
        if (!FilePicker.open(this, REQ_IMPORT)) {
            importFailed("This TV has no file picker. Install a file manager or use a TV file-transfer app, then try again.")
        }
    }

    private fun importRewFile(uri: Uri) {
        if (measuring || importing) return
        val target = activeTargetKey()
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
        val target = activeTargetKey()
        val volume = ROOMS[roomIndex].volumeM3
        setImportBusy(true)
        textStatus.text = getString(R.string.measure_importing)
        val worker = thread(name = "CoreEqRewReanalysis", start = false) {
            val analyzed = try {
                RewImport.analyze(source.frequenciesHz, source.magnitudesDb, target, volume, ceilingHz = chosenCeilingHz())
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
        btnRepeat.isEnabled = enabled && result != null && importResult == null
        btnImport.isEnabled = enabled
        btnRoom.isEnabled = enabled
        btnTarget.isEnabled = enabled
        btnSave.isEnabled = !busy && !importing
        updateRefineButtons()
    }

    private fun setImportBusy(busy: Boolean) {
        importing = busy
        val enabled = !busy && !measuring && !sweepReanalysisInProgress
        btnStart.isEnabled = enabled
        btnRepeat.isEnabled = enabled && result != null && importResult == null
        btnImport.isEnabled = enabled
        btnRoom.isEnabled = enabled
        btnTarget.isEnabled = enabled
        btnSave.isEnabled = !busy && !sweepReanalysisInProgress
        updateRefineButtons()
    }

    private fun importFailed(reason: String) {
        // Preserve any previous unsaved result; a bad file must not destroy it.
        progressMeasure.progress = 0
        textStatus.text = getString(R.string.measure_import_failed, reason)
        btnStart.requestFocus()
    }

    private fun showImportResult(imp: ImportResult) {
        repeatBaseline = null
        repeatCapture = null
        repeatComparison = null
        repeatabilityCheck = null
        repeatComparisonClearedDueToSettings = false
        seats = emptyList()
        seatAverage = null
        correctionCheck = null
        correctionCheckNote = null
        btnRepeat.visibility = View.GONE
        btnStart.setText(R.string.measure_start_new)
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
            imp.pointsRead, imp.floorHz, imp.fMaxHz / 1000,
            nulls, if (nulls == 1) "" else "s", imp.transitionHz
        )
        val outputStatus = importedOutputEstimate?.let {
            getString(R.string.measure_import_output_estimate, it.name)
        } ?: getString(R.string.measure_import_output_unknown)
        textStatus.text = listOf(analysisStatus, outputStatus)
            .plus(CorrectionReport.summaryLines(imp, limit = 2))
            .joinToString("\n")
        renderQuality()
        updateRefineButtons()
        btnSave.visibility = View.VISIBLE
        btnSave.requestFocus()
    }

    private fun fail(message: String, kind: SweepKind = measurementKind) {
        finishMeasuring()
        progressMeasure.progress = 0
        val kept = result
        if (kind != SweepKind.PRIMARY && kept != null) {
            if (kind == SweepKind.REPEAT) {
                repeatBaseline = null
                repeatCapture = null
                repeatComparison = null
                repeatabilityCheck = null
            }
            btnStart.setText(R.string.measure_start_new)
            btnRepeat.visibility = View.VISIBLE
            btnRepeat.isEnabled = true
            btnSave.setText(R.string.measure_save)
            btnSave.visibility = View.VISIBLE
            showResult(kept, repeatComparison)
            // Most failure messages end in a full stop already; the templates add their own.
            val reason = message.trimEnd().trimEnd('.')
            val note = when (kind) {
                SweepKind.SEAT -> "Seat not added: $reason. The result on screen is unchanged."
                SweepKind.CHECK -> "Check not run: $reason. The correction is unchanged."
                else -> "Repeat not compared: $reason. The first sweep remains available; nothing was averaged."
            }
            textStatus.text = "${textStatus.text}\n$note"
            btnStart.requestFocus()
            return
        }
        result = null
        importResult = null
        importInput = null
        importedOutputEstimate = null
        repeatBaseline = null
        repeatCapture = null
        repeatComparison = null
        repeatabilityCheck = null
        repeatComparisonClearedDueToSettings = false
        seats = emptyList()
        seatAverage = null
        correctionCheck = null
        correctionCheckNote = null
        btnRepeat.visibility = View.GONE
        btnStart.setText(R.string.measure_start)
        btnSave.visibility = View.GONE
        btnSave.setText(R.string.measure_save)
        textStatus.text = getString(R.string.measure_failed, message)
        renderQuality()
        btnStart.requestFocus()
    }

    private fun finishMeasuring() {
        measuring = false
        measurementGeneration += 1 // invalidate late callbacks from the capture just ended
        measurementKind = SweepKind.PRIMARY
        btnStop.isEnabled = false
        btnStop.visibility = View.GONE
        stimulusPlayer.stop()
        releaseCheckCorrection()
        captureEngine.stop()
        btnStart.isEnabled = true
        btnRepeat.isEnabled = result != null && importResult == null
        btnRoom.isEnabled = true
        btnTarget.isEnabled = true
        btnCeiling.isEnabled = true
        btnImport.isEnabled = true
        updateRefineButtons()
        EqService.send(this, EqService.ACTION_RESUME)
    }

    private fun cancelMeasurement(message: String) {
        if (!measuring) return
        val kind = measurementKind
        finishMeasuring()
        progressMeasure.progress = 0
        if (kind != SweepKind.PRIMARY && result != null) {
            if (kind == SweepKind.REPEAT) {
                repeatBaseline = null
                repeatCapture = null
                repeatComparison = null
                repeatabilityCheck = null
            }
            btnStart.setText(R.string.measure_start_new)
            btnRepeat.visibility = View.VISIBLE
            btnRepeat.isEnabled = true
            btnSave.setText(R.string.measure_save)
            btnSave.visibility = View.VISIBLE
            updateRefineButtons()
            textStatus.text = when (kind) {
                SweepKind.SEAT -> "$message The seat was not added; the result on screen is unchanged."
                SweepKind.CHECK -> "$message The check did not finish; the correction is unchanged."
                else -> "$message The first sweep remains available; nothing was averaged."
            }
        } else {
            textStatus.text = message
        }
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
        val profile = buildSweepProfile(r, now)
        profileStore.saveProfile(profile, setAsActive = true)
        EqService.send(this, EqService.ACTION_REAPPLY)
        Toast.makeText(this, getString(R.string.measure_saved, profile.name), Toast.LENGTH_SHORT).show()
        finish()
    }

    /** The profile Save keeps for [r]; a check sweep plays this same profile's correction. */
    private fun buildSweepProfile(r: SweepResult, now: Long): Profile {
        val bands = Correction.collapseToBands(
            DISPLAY_BANDS_HZ, { hz -> interpolate(hz, r.centresHz, r.correctionDb) }, -1500, 1500
        ).map { PlatformBand(it.first, it.second) }
        val output = measuredOutput
        val savedRepeatability = repeatabilityCheck
        val averaged = seatAverage?.takeIf { seats.size >= 2 }
        val check = correctionCheck
        val baseName = getString(R.string.measure_profile_name, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(now)))
        return Profile(
            id = "profile-$now",
            name = if (output != null) "$baseName · ${output.name}" else baseName,
            timestampMs = now,
            target = activeTargetKey(),
            targetPoints = activeTargetPoints(),
            micType = micName ?: "microphone",
            captureInfo = captureInfo,
            repeatabilityCheck = savedRepeatability,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            stimulus = "sweep_${Sweep.SECONDS.roundToInt()}s",
            captureSeconds = LEAD_SECONDS + Sweep.STIMULUS_SECONDS + SweepAnalysis.TAIL_SECONDS,
            volumeM3 = ROOMS[roomIndex].volumeM3,
            rt60Seconds = r.rt60Seconds,
            schroederHz = r.schroederHz,
            transitionHz = r.transitionHz,
            rolloffHz = r.floorHz,
            correctionCeilingHz = r.fMaxHz,
            snrDb = r.snrDb,
            qualityScore = MeasurementQuality.score(r),
            nullsUntouchedHz = r.centresHz.indices.filter { r.nullMask[it] }.map { r.centresHz[it] },
            preampDb = r.preampDb,
            filters = r.filters,
            platformBands = bands,
            curve = r.centresHz.indices.map { CurvePoint(r.centresHz[it], r.measuredDb[it], r.correctionDb[it]) },
            outputKind = OutputRoute.keyFor(output),
            outputName = output?.name,
            measurementNotes = buildList {
                if (savedRepeatability != null) {
                    add("Optional same-position repeat retained as a separate trace; captures are never averaged. The comparison is descriptive only, with no qualified pass/fail threshold, and does not generalize to other seats or rooms.")
                    savedRepeatability.note?.let { add(it) }
                }
                if (averaged != null) {
                    add(String.format(Locale.US,
                        "Energy average of %d seats (%d measured): each band is the mean power across seats, then corrected with the limits unchanged apart from the bass budget, which rises to %.0f dB below the transition because more than one seat agreed on the dip. A band is corrected only where every seat measured it cleanly, and only as far as the weakest seat is trusted. The microphone, output and capture details are the first seat's.",
                        averaged.seatsUsed, seats.size, averaged.boostBudgetDb))
                    val out = averaged.leftOut
                    val offDb = averaged.leftOutDb
                    if (out != null && offDb != null) {
                        add(String.format(Locale.US,
                            "Seat %d left out of the average: its 300 Hz-3 kHz response sat %.1f dB RMS from the other seats.",
                            out + 1, offDb))
                    }
                }
                if (check != null) {
                    add(String.format(Locale.US,
                        "Check sweep with this correction on, one position: %.1f dB RMS from the target over %d bands (room alone %.1f dB, predicted %.1f dB). Bands the correction leaves alone are not counted. Descriptive only.",
                        check.after.rmsDb ?: 0.0, check.after.bands, check.before.rmsDb ?: 0.0, check.predicted.rmsDb ?: 0.0))
                    correctionCheckNote?.let { add("Check chain: $it") }
                }
            }
        )
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
            target = activeTargetKey(),
            targetPoints = activeTargetPoints(),
            micType = "REW import",
            deviceName = "REW",
            stimulus = "rew_import",
            captureSeconds = 0.0,
            volumeM3 = ROOMS[roomIndex].volumeM3,
            rt60Seconds = null,
            schroederHz = null,
            transitionHz = imp.transitionHz,
            rolloffHz = imp.floorHz,
            correctionCeilingHz = imp.fMaxHz,
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
        if (requestCode == REQ_TARGET_IMPORT) {
            val targetUri = data?.data
            if (resultCode == RESULT_OK && targetUri != null) loadCustomTarget(targetUri)
            return
        }
        if (requestCode != REQ_IMPORT) return
        val uri = data?.data
        if (resultCode == RESULT_OK && uri != null) {
            importRewFile(uri)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        val kind = pendingKind ?: SweepKind.PRIMARY
        pendingKind = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (kind == SweepKind.REPEAT) startRepeatMeasurement() else startMeasurement(kind)
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
        releaseCheckCorrection()
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
        private const val REQ_TARGET_IMPORT = 44
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
        private const val KEY_USE_CUSTOM = "use_custom_target"
        private const val KEY_AUTO_APPLIED = "auto_applied_outputs"
        private const val KEY_CUSTOM_TARGET = "custom_target_text"
        private const val KEY_CEILING = "ceiling_index"
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
