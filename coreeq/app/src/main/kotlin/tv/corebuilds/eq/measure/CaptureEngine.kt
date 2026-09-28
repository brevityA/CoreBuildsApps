package tv.corebuilds.eq.measure

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.dsp.Resample
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

interface CaptureListener {
    /** Called once the microphone is actually recording; start the stimulus here. */
    fun onRecording(deviceName: String?)
    fun onLevel(secondsElapsed: Float, totalSeconds: Float, rmsDbfs: Float, peakDbfs: Float)
    /** The full capture at [sampleRate], normalised to ±1. */
    fun onCaptured(samples: DoubleArray, sampleRate: Int, deviceName: String?)
    fun onError(message: String)
}

/**
 * Records the room through the remote microphone.
 *
 * `VOICE_RECOGNITION` is the source the CDD (§5.4.2) requires to be flat and
 * free of AGC and noise suppression, which is what a measurement needs. The
 * remote streams 16 kHz, so that rate is asked for first; a device that only
 * offers 48 kHz is decimated to 16 kHz so the analysis always runs at one rate.
 *
 * There is no simulation fallback: a capture that cannot happen is an error
 * with its cause, never a made-up spectrum.
 */
class CaptureEngine(private val context: Context) {

    @Volatile
    private var recording = false
    private var audioRecord: AudioRecord? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(totalSeconds: Double, listener: CaptureListener) {
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
        var rate = 0
        val tried = mutableListOf<String>()
        for (candidate in RATES) {
            val minBuf = AudioRecord.getMinBufferSize(
                candidate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                tried += "$candidate Hz (getMinBufferSize $minBuf)"
                continue
            }
            try {
                val r = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, candidate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf * 4, candidate)
                )
                if (r.state == AudioRecord.STATE_INITIALIZED) {
                    record = r
                    rate = candidate
                    break
                }
                r.release()
                tried += "$candidate Hz (not initialised)"
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord at $candidate Hz failed", e)
                tried += "$candidate Hz (${e.javaClass.simpleName})"
            }
        }
        if (record == null) {
            recording = false
            listener.onError("No microphone could be opened for measurement. Tried ${tried.joinToString(", ")}.")
            return
        }

        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            record.release()
            recording = false
            listener.onError("The microphone would not start recording: ${e.message ?: "IllegalStateException"}")
            return
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            recording = false
            listener.onError(
                "The microphone is in use by another app. Close any voice search or assistant and try again."
            )
            return
        }
        audioRecord = record
        val deviceName = record.routedDevice?.productName?.toString()?.takeIf { it.isNotBlank() }
        listener.onRecording(deviceName)

        val total = (totalSeconds * rate).toInt()
        val pcm = ShortArray(total)
        var filled = 0
        val chunk = ShortArray(rate / 20)
        var error: String? = null
        while (recording && filled < total) {
            val want = minOf(chunk.size, total - filled)
            val n = record.read(chunk, 0, want)
            if (n < 0) {
                error = "Reading the microphone failed (${readError(n)})."
                break
            }
            chunk.copyInto(pcm, filled, 0, n)
            var sq = 0.0
            var pk = 0
            for (i in 0 until n) {
                val v = chunk[i].toInt()
                sq += v.toDouble() * v
                pk = max(pk, abs(v))
            }
            filled += n
            if (n > 0) {
                val rms = sqrt(sq / n) / Short.MAX_VALUE
                listener.onLevel(
                    filled.toFloat() / rate, totalSeconds.toFloat(),
                    (20.0 * log10(max(rms, 1e-9))).toFloat(),
                    (20.0 * log10(max(pk.toDouble() / Short.MAX_VALUE, 1e-9))).toFloat()
                )
            }
        }

        val cancelled = !recording
        recording = false
        release(record)
        when {
            cancelled -> Unit
            error != null -> listener.onError(error)
            else -> {
                val samples = DoubleArray(filled) { pcm[it].toDouble() / Short.MAX_VALUE }
                val out = if (rate == ANALYSIS_RATE) samples else Resample.decimate(samples, rate / ANALYSIS_RATE)
                listener.onCaptured(out, ANALYSIS_RATE, deviceName)
            }
        }
    }

    /** Ask the capture to end. The capture thread releases the recorder itself. */
    fun stop() {
        recording = false
    }

    private fun release(r: AudioRecord) {
        if (audioRecord === r) audioRecord = null
        try {
            r.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioRecord.stop on a recorder that was not recording", e)
        }
        r.release()
    }

    private fun readError(code: Int): String = when (code) {
        AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
        AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
        AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT: the microphone went away"
        else -> "error $code"
    }

    companion object {
        const val ANALYSIS_RATE = 16000
        private val RATES = intArrayOf(16000, 48000)
        private const val TAG = "CoreEqCapture"
    }
}
