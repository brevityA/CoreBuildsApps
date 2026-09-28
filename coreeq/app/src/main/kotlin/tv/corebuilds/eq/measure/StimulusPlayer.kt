package tv.corebuilds.eq.measure

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import tv.corebuilds.eq.dsp.DspConstants
import kotlin.concurrent.thread
import kotlin.math.ln
import kotlin.math.sin

/**
 * Stimulus generator and player for room acoustics measurement.
 * Plays 10 s Farina exponential sine sweep or deterministic pink noise at 48 kHz.
 */
class StimulusPlayer {

    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false

    fun playSweep(
        durationSeconds: Double = 10.0,
        fStart: Double = 20.0,
        fEnd: Double = 20000.0,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        stop()
        isPlaying = true

        thread(name = "CoreEqStimulusThread") {
            val sampleRate = DspConstants.FS
            val totalSamples = (durationSeconds * sampleRate).toInt()
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val chunkSize = 4096
            val buffer = ShortArray(chunkSize)
            var sampleIdx = 0
            val k = totalSamples.toDouble() / ln(fEnd / fStart)
            val amplitude = 0.5 * Short.MAX_VALUE

            while (isPlaying && sampleIdx < totalSamples) {
                val samplesToWrite = Math.min(chunkSize, totalSamples - sampleIdx)
                for (i in 0 until samplesToWrite) {
                    val tSamples = sampleIdx + i
                    val t = tSamples.toDouble() / sampleRate
                    // Exponential sine sweep instantaneous phase
                    val phase = 2.0 * Math.PI * fStart * k * (Math.exp((tSamples.toDouble() / totalSamples) * ln(fEnd / fStart)) - 1.0) / sampleRate
                    // Taper envelope at ends (0.05 s) to prevent clicks
                    var env = 1.0
                    val taperSamples = (0.05 * sampleRate).toInt()
                    if (tSamples < taperSamples) {
                        env = 0.5 * (1.0 - Math.cos(Math.PI * tSamples / taperSamples))
                    } else if (tSamples > totalSamples - taperSamples) {
                        env = 0.5 * (1.0 - Math.cos(Math.PI * (totalSamples - tSamples) / taperSamples))
                    }
                    buffer[i] = (amplitude * env * sin(phase)).toInt().toShort()
                }

                track.write(buffer, 0, samplesToWrite)
                sampleIdx += samplesToWrite
                onProgress?.invoke(sampleIdx.toFloat() / totalSamples)
            }

            try {
                track.stop()
                track.release()
            } catch (ignored: Exception) {}

            isPlaying = false
            audioTrack = null
            onComplete?.invoke()
        }
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (ignored: Exception) {}
        audioTrack = null
    }

    fun isPlaying(): Boolean = isPlaying
}
