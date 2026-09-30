package tv.corebuilds.eq.measure

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.Sweep
import kotlin.concurrent.thread

/**
 * Plays the measurement sweep ([Sweep.generate]) at [DspConstants.FS].
 *
 * The whole stimulus is rendered before playback starts, so a slow device
 * cannot starve the track mid-sweep and put a gap in the measurement.
 */
class StimulusPlayer {

    private var audioTrack: AudioTrack? = null
    @Volatile
    private var playing = false

    /**
     * @param onRouted the device the sweep is actually playing through, once
     *   Android has routed it (null when it never says) — the ground truth for
     *   which chain this measurement is of.
     */
    fun playSweep(onError: (String) -> Unit, onRouted: (AudioDeviceInfo?) -> Unit = {}) {
        stop()
        playing = true
        thread(name = "CoreEqStimulus") {
            val pcm = Sweep.generate(DspConstants.FS).let { s ->
                ShortArray(s.size) { (s[it] * Short.MAX_VALUE).toInt().toShort() }
            }
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(DspConstants.FS)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack creation failed", e)
                playing = false
                onError("The TV refused to open a ${DspConstants.FS} Hz audio track: ${e.message ?: e.javaClass.simpleName}")
                return@thread
            }
            val written = track.write(pcm, 0, pcm.size)
            if (written != pcm.size) {
                track.release()
                playing = false
                onError("Could not load the sweep into the audio track (AudioTrack.write returned $written).")
                return@thread
            }
            audioTrack = track
            try {
                track.play()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "AudioTrack.play failed", e)
                stop()
                onError("The TV would not start playback: ${e.message ?: "IllegalStateException"}")
                return@thread
            }
            // Routing settles just after play(); ask for up to half a second.
            var routed: AudioDeviceInfo? = null
            for (i in 0 until ROUTE_POLLS) {
                routed = try { track.routedDevice } catch (e: IllegalStateException) { null }
                if (routed != null || !playing) break
                Thread.sleep(ROUTE_POLL_MS)
            }
            onRouted(routed)
        }
    }

    fun stop() {
        playing = false
        val track = audioTrack ?: return
        audioTrack = null
        try {
            track.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioTrack.stop on a track that was not playing", e)
        }
        track.release()
    }

    fun isPlaying(): Boolean = playing

    private companion object {
        const val TAG = "CoreEqStimulus"
        const val ROUTE_POLLS = 10
        const val ROUTE_POLL_MS = 50L
    }
}
