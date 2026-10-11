package tv.corebuilds.eq.companion

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
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import tv.corebuilds.eq.audioDeviceTypeLabel
import org.json.JSONObject
import kotlin.math.max

internal data class PhoneCaptureStart(
    val sessionId: String,
    val captureSeconds: Double
)

internal data class PhoneRecording(
    val sessionId: String,
    val sampleRateHz: Int,
    val pcm: ShortArray,
    val captureInfo: JSONObject,
    val captureSeconds: Double
)

/** Explicit, finite phone-microphone session. It never records in the background. */
internal class PhoneCaptureEngine(private val context: Context) {

    @Volatile private var cancelled = false
    @Volatile private var activeRecord: AudioRecord? = null

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission") // checked before opening AudioRecord
    fun capture(
        onRecordingReady: (captureInfo: JSONObject) -> PhoneCaptureStart,
        onProgress: (Float) -> Unit = {}
    ): PhoneRecording {
        check(hasPermission()) { "Microphone permission is not granted" }
        cancelled = false
        var record: AudioRecord? = null
        var effects = emptyList<AudioEffect>()
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val supportsUnprocessed = try {
                audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
                    ?.trim()?.equals("true", ignoreCase = true) == true
            } catch (_: RuntimeException) {
                false
            }
            val unprocessedSource = supportsUnprocessed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            val source = if (unprocessedSource) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }
            val opened = openRecorder(source)
                ?: (if (unprocessedSource) openRecorder(MediaRecorder.AudioSource.VOICE_RECOGNITION) else null)
                ?: throw IllegalStateException("Android could not open a phone microphone at 48, 44.1 or 16 kHz.")
            record = opened.record
            activeRecord = record
            val actualRate = record.sampleRate.takeIf { it > 0 } ?: opened.requestedRate
            if (actualRate < MIN_ANALYSIS_RATE) {
                throw IllegalStateException("Android opened a $actualRate Hz phone input, below the measurement analysis rate.")
            }
            val effectSetup = disableCaptureEffects(record.audioSessionId)
            effects = effectSetup.first
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("The phone microphone did not start recording.")
            }
            val captureInfo = readCaptureInfo(
                record = record,
                requestedRate = opened.requestedRate,
                actualRate = actualRate,
                audioSource = if (opened.source == MediaRecorder.AudioSource.UNPROCESSED) "UNPROCESSED" else "VOICE_RECOGNITION",
                unprocessedSupported = supportsUnprocessed,
                processing = effectSetup.second
            )
            // The phone starts buffering first; only then does the TV play the
            // sweep. Timing markers, not network latency, align the capture.
            val start = onRecordingReady(captureInfo)
            require(start.sessionId.isNotBlank()) { "TV returned no measurement session id." }
            require(start.captureSeconds.isFinite() && start.captureSeconds in MIN_CAPTURE_SECONDS..MAX_CAPTURE_SECONDS) {
                "TV returned an invalid measurement duration."
            }
            val totalSamples = (start.captureSeconds * actualRate).toInt()
            require(totalSamples > 0) { "TV returned an empty measurement duration." }
            val pcm = ShortArray(totalSamples)
            val chunk = ShortArray(max(1, actualRate / 20))
            var filled = 0
            var zeroReads = 0
            while (!cancelled && filled < totalSamples) {
                val count = try {
                    record.read(chunk, 0, minOf(chunk.size, totalSamples - filled))
                } catch (e: Exception) {
                    if (cancelled) break
                    throw IllegalStateException("Phone microphone read failed: ${e.message ?: e.javaClass.simpleName}", e)
                }
                if (count < 0) {
                    if (cancelled) break
                    throw IllegalStateException("Phone microphone read failed ($count).")
                }
                if (count == 0) {
                    zeroReads++
                    if (zeroReads > MAX_ZERO_READS) throw IllegalStateException("Phone microphone stopped returning samples.")
                    Thread.sleep(ZERO_READ_PAUSE_MS)
                    continue
                }
                zeroReads = 0
                chunk.copyInto(pcm, filled, 0, count)
                filled += count
                onProgress(filled.toFloat() / totalSamples)
            }
            if (cancelled) throw InterruptedException("Phone measurement cancelled.")
            if (filled != totalSamples) throw IllegalStateException("Phone recording ended before the sweep and decay tail were captured.")
            return PhoneRecording(start.sessionId, actualRate, pcm, captureInfo, filled.toDouble() / actualRate)
        } finally {
            cancelled = true
            activeRecord = null
            effects.forEach { effect ->
                try { effect.release() } catch (e: RuntimeException) { Log.w(TAG, "Could not release a phone capture effect", e) }
            }
            record?.let(::release)
        }
    }

    fun cancel() {
        cancelled = true
        try {
            activeRecord?.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Phone capture stop raced with recorder startup", e)
        }
    }

    private data class Opened(val record: AudioRecord, val requestedRate: Int, val source: Int)

    private fun openRecorder(source: Int): Opened? {
        for (rate in RATES) {
            val minimum = try {
                AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            } catch (_: Exception) {
                continue
            }
            if (minimum <= 0) continue
            try {
                val record = AudioRecord(
                    source,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minimum * 4, rate)
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) return Opened(record, rate, source)
                record.release()
            } catch (e: Exception) {
                Log.w(TAG, "Phone AudioRecord at $rate Hz failed", e)
            }
        }
        return null
    }

    private fun disableCaptureEffects(sessionId: Int): Pair<List<AudioEffect>, List<String>> {
        val effects = mutableListOf<AudioEffect>()
        val report = mutableListOf<String>()

        fun disable(name: String, available: () -> Boolean, create: () -> AudioEffect?) {
            val exists = try { available() } catch (_: RuntimeException) { false }
            if (!exists) {
                report += "$name not exposed"
                return
            }
            val effect = try { create() } catch (_: RuntimeException) { null }
            if (effect == null) {
                report += "$name could not be inspected"
                return
            }
            try {
                val wasEnabled = effect.enabled
                val result = effect.setEnabled(false)
                val off = result == AudioEffect.SUCCESS && !effect.enabled
                report += "$name ${if (wasEnabled) "was on" else "was off"}, " +
                    if (off) "now off" else "could not be turned off ($result)"
                effects += effect
            } catch (e: RuntimeException) {
                report += "$name state not readable"
                try { effect.release() } catch (_: RuntimeException) { }
            }
        }

        disable("AGC", { AutomaticGainControl.isAvailable() }) { AutomaticGainControl.create(sessionId) }
        disable("NoiseSuppressor", { NoiseSuppressor.isAvailable() }) { NoiseSuppressor.create(sessionId) }
        disable("EchoCanceler", { AcousticEchoCanceler.isAvailable() }) { AcousticEchoCanceler.create(sessionId) }
        return effects to report
    }

    private fun readCaptureInfo(
        record: AudioRecord,
        requestedRate: Int,
        actualRate: Int,
        audioSource: String,
        unprocessedSupported: Boolean,
        processing: List<String>
    ): JSONObject {
        val route = try { record.routedDevice } catch (_: RuntimeException) { null }
        val microphones = try { record.activeMicrophones.toList() } catch (_: Exception) { emptyList() }
        val descriptions = microphones.mapNotNull { it.description?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        val response = JSONArray()
        var responseCount = 0
        microphones.take(MAX_RESPONSE_MICROPHONES).forEachIndexed { index, microphone ->
            val label = microphone.description?.trim()?.takeIf(String::isNotEmpty) ?: "phone mic ${index + 1}"
            val points = try { microphone.frequencyResponse } catch (_: RuntimeException) { emptyList() }
            for (point in points.take(MAX_RESPONSE_POINTS - responseCount)) {
                val hz = point.first?.toDouble() ?: continue
                val db = point.second?.toDouble() ?: continue
                if (hz > 0.0 && hz.isFinite() && db.isFinite()) {
                    response.put(JSONObject().put("microphone", label.take(80)).put("frequency_hz", hz).put("response_db", db))
                    responseCount++
                }
            }
        }
        return JSONObject()
            .put("requested_sample_rate_hz", requestedRate)
            .put("audio_source", audioSource)
            .put("input_device_name", route?.productName?.toString()?.take(80) ?: JSONObject.NULL)
            .put("input_device_type", route?.let { audioDeviceTypeLabel(it.type) } ?: JSONObject.NULL)
            .put("active_microphone_count", microphones.size)
            .put("capture_device_model", "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80))
            .put("capture_os_version", Build.VERSION.RELEASE.take(40))
            .put("unprocessed_audio_source_supported", unprocessedSupported)
            .put("microphone_descriptions", JSONArray(descriptions.take(MAX_METADATA_STRINGS)))
            .put("processing", JSONArray(processing.take(MAX_METADATA_STRINGS)))
            .put("microphone_frequency_response", response)
            .put("capture_sample_rate_hz", actualRate)
    }

    private fun release(record: AudioRecord) {
        try { record.stop() } catch (_: IllegalStateException) { }
        try { record.release() } catch (e: RuntimeException) { Log.w(TAG, "Could not release the phone AudioRecord", e) }
    }

    companion object {
        private val RATES = intArrayOf(48_000, 44_100, 16_000)
        private const val MIN_ANALYSIS_RATE = 16_000
        private const val MIN_CAPTURE_SECONDS = 8.0
        private const val MAX_CAPTURE_SECONDS = 30.0
        private const val MAX_ZERO_READS = 100
        private const val ZERO_READ_PAUSE_MS = 5L
        private const val MAX_RESPONSE_MICROPHONES = 4
        private const val MAX_RESPONSE_POINTS = 256
        private const val MAX_METADATA_STRINGS = 16
        private const val TAG = "CoreEqPhoneCapture"
    }
}
