package tv.corebuilds.eq.measure

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.audioDeviceTypeLabel
import tv.corebuilds.eq.dsp.Resample
import tv.corebuilds.eq.export.CaptureInfo
import tv.corebuilds.eq.export.MicrophoneResponsePoint
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

interface CaptureListener {
    /** Called once the microphone is actually recording; start the stimulus here. */
    fun onRecording(info: CaptureInfo)
    fun onLevel(secondsElapsed: Float, totalSeconds: Float, rmsDbfs: Float, peakDbfs: Float)
    /** Samples are normalized to ±1 and resampled to [sampleRate] for analysis. */
    fun onCaptured(samples: DoubleArray, sampleRate: Int, info: CaptureInfo)
    fun onError(message: String)
}

/**
 * Records the room through the TV/remote input selected by Android.
 *
 * `VOICE_RECOGNITION` is requested to avoid speech AGC and noise suppression;
 * capture effects are still queried, switched off where possible, and reported.
 * Android guarantees 44.1 kHz on AudioRecord but does not guarantee that 16 or
 * 48 kHz is supported, so 44.1 kHz is a real fallback. Every accepted input is
 * converted to the analysis rate with a correctly band-limited resampler.
 *
 * The actual input route, sample rate, active-microphone metadata and effect
 * state travel with the saved profile. Any platform microphone-response data
 * is retained as provenance only; it is not silently used to alter the EQ.
 * There is no simulation fallback: a capture that cannot happen is an error,
 * never a made-up spectrum.
 */
class CaptureEngine(private val context: Context) {

    @Volatile
    private var recording = false
    private var audioRecord: AudioRecord? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun start(totalSeconds: Double, listener: CaptureListener) {
        if (!totalSeconds.isFinite() || totalSeconds <= 0.0 || totalSeconds > MAX_CAPTURE_SECONDS) {
            listener.onError("The requested measurement duration is not valid.")
            return
        }
        if (recording) {
            listener.onError("A measurement is already running.")
            return
        }
        if (!hasPermission()) {
            listener.onError(
                "Core EQ does not have microphone permission. Allow it when asked, " +
                    "or in Settings → Apps → Core EQ → Permissions."
            )
            return
        }
        recording = true
        thread(name = "CoreEqCapture") { run(totalSeconds, listener) }
    }

    @Suppress("MissingPermission") // checked in start()
    private fun run(totalSeconds: Double, listener: CaptureListener) {
        var record: AudioRecord? = null
        var requestedRate = 0
        var effects = emptyList<AudioEffect>()
        var error: String? = null
        var captured: DoubleArray? = null
        var info: CaptureInfo? = null
        var cancelled = false

        try {
            val tried = mutableListOf<String>()
            for (candidate in RATES) {
                val minBuffer = try {
                    AudioRecord.getMinBufferSize(
                        candidate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                } catch (e: Exception) {
                    tried += "$candidate Hz (${e.javaClass.simpleName})"
                    continue
                }
                if (minBuffer <= 0) {
                    tried += "$candidate Hz (getMinBufferSize $minBuffer)"
                    continue
                }
                try {
                    val candidateRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        candidate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        max(minBuffer * 4, candidate)
                    )
                    if (candidateRecord.state == AudioRecord.STATE_INITIALIZED) {
                        record = candidateRecord
                        requestedRate = candidate
                        break
                    }
                    candidateRecord.release()
                    tried += "$candidate Hz (not initialized)"
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord at $candidate Hz failed", e)
                    tried += "$candidate Hz (${e.javaClass.simpleName})"
                }
            }

            val opened = record
            if (opened == null) {
                error = "No microphone could be opened for measurement. Tried ${tried.joinToString(", ")}."
            } else if (!recording) {
                cancelled = true
            } else {
                audioRecord = opened
                val actualRate = opened.sampleRate.takeIf { it > 0 } ?: requestedRate
                if (actualRate < ANALYSIS_RATE) {
                    error = "Android opened a $actualRate Hz input, below Core EQ's $ANALYSIS_RATE Hz analysis rate."
                } else {
                    val effectSetup = silenceCaptureEffects(opened.audioSessionId)
                    effects = effectSetup.effects
                    opened.startRecording()
                    if (opened.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        error = "The microphone is in use by another app. Close voice search or the assistant and try again."
                    } else {
                        val captureInfo = readCaptureInfo(
                            opened = opened,
                            requestedRate = requestedRate,
                            actualRate = actualRate,
                            processing = effectSetup.processing,
                            unprocessedSupported = effectSetup.unprocessedSupported
                        )
                        info = captureInfo
                        listener.onRecording(captureInfo)

                        val totalSamples = (totalSeconds * actualRate).toInt()
                        val pcm = ShortArray(totalSamples)
                        val chunk = ShortArray(max(1, actualRate / 20))
                        var filled = 0
                        var zeroReads = 0
                        while (recording && filled < totalSamples && error == null) {
                            val want = minOf(chunk.size, totalSamples - filled)
                            val count = try {
                                opened.read(chunk, 0, want)
                            } catch (e: Exception) {
                                Log.e(TAG, "AudioRecord.read threw while capturing", e)
                                error = "The microphone stopped while recording: ${e.message ?: e.javaClass.simpleName}."
                                break
                            }
                            if (count < 0) {
                                error = "Reading the microphone failed (${readError(count)})."
                                break
                            }
                            if (count == 0) {
                                zeroReads++
                                if (zeroReads > MAX_ZERO_READS) {
                                    error = "The microphone stopped returning samples. Close voice search and try again."
                                } else {
                                    Thread.sleep(ZERO_READ_PAUSE_MS)
                                }
                                continue
                            }
                            zeroReads = 0
                            chunk.copyInto(pcm, filled, 0, count)
                            var squareSum = 0.0
                            var peak = 0
                            for (i in 0 until count) {
                                val value = chunk[i].toInt()
                                squareSum += value.toDouble() * value
                                peak = max(peak, abs(value))
                            }
                            filled += count
                            val rms = sqrt(squareSum / count) / Short.MAX_VALUE
                            listener.onLevel(
                                filled.toFloat() / actualRate,
                                totalSeconds.toFloat(),
                                (20.0 * log10(max(rms, 1e-9))).toFloat(),
                                (20.0 * log10(max(peak.toDouble() / Short.MAX_VALUE, 1e-9))).toFloat()
                            )
                        }

                        cancelled = !recording
                        if (!cancelled && error == null) {
                            val input = DoubleArray(filled) { pcm[it].toDouble() / Short.MAX_VALUE }
                            captured = Resample.toRate(input, actualRate, ANALYSIS_RATE)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Microphone capture failed", e)
            if (recording) {
                error = "Microphone capture failed: ${e.message ?: e.javaClass.simpleName}."
            } else {
                cancelled = true
            }
        } finally {
            recording = false
            effects.forEach { effect ->
                try {
                    effect.release()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Could not release a capture effect", e)
                }
            }
            record?.let(::release)
        }

        when {
            cancelled -> Unit
            error != null -> reportError(listener, error!!)
            captured != null && info != null -> {
                try {
                    listener.onCaptured(captured!!, ANALYSIS_RATE, info!!)
                } catch (e: Exception) {
                    Log.e(TAG, "Capture result callback failed", e)
                    reportError(listener, "Core EQ could not hand the recording to analysis: ${e.javaClass.simpleName}.")
                }
            }
            else -> reportError(listener, "The microphone capture ended without a complete recording.")
        }
    }

    /** Ask the capture to end. The capture thread releases the recorder itself. */
    fun stop() {
        recording = false
    }

    private data class EffectSetup(
        val effects: List<AudioEffect>,
        val processing: List<String>,
        val unprocessedSupported: Boolean?
    )

    /** Switch capture processing off where Android exposes a control, and record what happened. */
    private fun silenceCaptureEffects(session: Int): EffectSetup {
        val found = mutableListOf<AudioEffect>()
        val report = mutableListOf<String>()

        fun take(name: String, available: () -> Boolean, create: () -> AudioEffect?) {
            val exposed = try {
                available()
            } catch (e: RuntimeException) {
                Log.w(TAG, "$name availability probe failed", e)
                report += "$name availability not reported"
                return
            }
            if (!exposed) {
                report += "$name not exposed"
                return
            }
            val effect = try {
                create()
            } catch (e: RuntimeException) {
                Log.w(TAG, "$name on session $session could not be created", e)
                report += "$name could not be inspected"
                null
            }
            if (effect == null) {
                if (report.lastOrNull() != "$name could not be inspected") report += "$name could not be created"
                return
            }
            try {
                val wasOn = effect.enabled
                val result = effect.setEnabled(false)
                val isOff = result == AudioEffect.SUCCESS && !effect.enabled
                report += "$name ${if (wasOn) "was on" else "was off"}, " +
                    if (isOff) "now off" else "could not be turned off ($result)"
                found += effect
            } catch (e: RuntimeException) {
                Log.w(TAG, "$name state could not be read", e)
                report += "$name state not readable"
                try {
                    effect.release()
                } catch (_: RuntimeException) {
                    // The capture's final cleanup still owns every effect it successfully kept.
                }
            }
        }

        take("AGC", { AutomaticGainControl.isAvailable() }) { AutomaticGainControl.create(session) }
        take("NoiseSuppressor", { NoiseSuppressor.isAvailable() }) { NoiseSuppressor.create(session) }
        take("EchoCanceler", { AcousticEchoCanceler.isAvailable() }) { AcousticEchoCanceler.create(session) }

        val unprocessedProperty = try {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unprocessed audio-source support was not reported", e)
            null
        }
        val unprocessedSupported = when (unprocessedProperty?.trim()?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
        Log.i(
            TAG,
            "Capture effects on session $session: ${report.joinToString("; ")}. " +
                "UNPROCESSED source supported: ${unprocessedProperty ?: "not reported"}."
        )
        return EffectSetup(found, report, unprocessedSupported)
    }

    private fun readCaptureInfo(
        opened: AudioRecord,
        requestedRate: Int,
        actualRate: Int,
        processing: List<String>,
        unprocessedSupported: Boolean?
    ): CaptureInfo {
        val route = try {
            opened.routedDevice
        } catch (e: RuntimeException) {
            Log.w(TAG, "Capture input route was not reported", e)
            null
        }
        val activeMicrophones = try {
            opened.activeMicrophones.toList()
        } catch (e: Exception) {
            Log.w(TAG, "Active microphone details were not reported", e)
            emptyList()
        }
        val descriptions = activeMicrophones.mapNotNull { it.description?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        val response = mutableListOf<MicrophoneResponsePoint>()
        activeMicrophones.take(MAX_RESPONSE_MICROPHONES).forEachIndexed { index, microphone ->
            val label = microphone.description?.trim()?.takeIf(String::isNotEmpty) ?: "active mic ${index + 1}"
            val points = try {
                microphone.frequencyResponse
            } catch (e: RuntimeException) {
                Log.w(TAG, "Frequency response for $label was not reported", e)
                emptyList()
            }
            for (point in points.take(MAX_RESPONSE_POINTS - response.size)) {
                val hz = point.first?.toDouble() ?: continue
                val db = point.second?.toDouble() ?: continue
                if (hz > 0.0 && hz.isFinite() && db.isFinite()) {
                    response += MicrophoneResponsePoint(label, hz, db)
                }
            }
            if (response.size >= MAX_RESPONSE_POINTS) return@forEachIndexed
        }
        val inputType = route?.let { audioDeviceTypeLabel(it.type) }
        val inputName = route?.productName?.toString()?.trim()?.takeIf(String::isNotEmpty)
        val info = CaptureInfo(
            requestedSampleRateHz = requestedRate,
            inputSampleRateHz = actualRate,
            analysisSampleRateHz = ANALYSIS_RATE,
            audioSource = "VOICE_RECOGNITION",
            inputDeviceName = inputName,
            inputDeviceType = inputType,
            activeMicrophoneCount = activeMicrophones.size.takeIf { it > 0 },
            microphoneDescriptions = descriptions,
            processing = processing,
            unprocessedAudioSourceSupported = unprocessedSupported,
            microphoneResponse = response
        )
        Log.i(TAG, info.summary())
        return info
    }

    private fun release(record: AudioRecord) {
        if (audioRecord === record) audioRecord = null
        try {
            record.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioRecord.stop on a recorder that was not recording", e)
        }
        try {
            record.release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "AudioRecord.release failed", e)
        }
    }

    private fun readError(code: Int): String = when (code) {
        AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
        AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
        AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT: the microphone went away"
        else -> "error $code"
    }

    private fun reportError(listener: CaptureListener, message: String) {
        try {
            listener.onError(message)
        } catch (e: Exception) {
            Log.e(TAG, "Capture error callback failed", e)
        }
    }

    companion object {
        const val ANALYSIS_RATE = 16000
        /** 16 kHz works for voice remotes; 48 kHz is preferred over the guaranteed 44.1 kHz fallback. */
        private val RATES = intArrayOf(16000, 48000, 44100)
        private const val MAX_ZERO_READS = 100
        private const val MAX_CAPTURE_SECONDS = 30.0
        private const val ZERO_READ_PAUSE_MS = 5L
        private const val MAX_RESPONSE_MICROPHONES = 4
        private const val MAX_RESPONSE_POINTS = 256
        private const val TAG = "CoreEqCapture"
    }
}
