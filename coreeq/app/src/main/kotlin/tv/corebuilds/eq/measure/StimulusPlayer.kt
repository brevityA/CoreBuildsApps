package tv.corebuilds.eq.measure

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.Sweep
import kotlin.concurrent.thread

/**
 * Plays the measurement stimulus ([Sweep.stimulus]: timing marker, sweep,
 * timing marker) at [DspConstants.FS].
 *
 * The whole stimulus is rendered before playback starts, so a slow device
 * cannot starve the track mid-sweep and put a gap in the measurement.
 */
class StimulusPlayer {

    private var audioTrack: AudioTrack? = null
    @Volatile
    private var playing = false

    /**
     * @param onRouted the route Android reports for this AudioTrack while it
     *   plays (empty when it never reports one). This is stronger evidence than
     *   a connected-device estimate, but does not prove the acoustic path or
     *   downstream processing. Changes during playback are reported as well.
     * @param sessionId an audio session that already carries an effect (the correction,
     *   for a check sweep); null gives the track a fresh session of its own.
     */
    fun playSweep(
        onError: (String) -> Unit,
        onRouted: (List<AudioDeviceInfo>) -> Unit = {},
        sessionId: Int? = null
    ) {
        stop()
        playing = true
        thread(name = "CoreEqStimulus") {
            val pcm = Sweep.stimulus(DspConstants.FS).let { s ->
                ShortArray(s.size) { (s[it] * Short.MAX_VALUE).toInt().toShort() }
            }
            val track = try {
                val builder = AudioTrack.Builder()
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
                if (sessionId != null) builder.setSessionId(sessionId)
                builder.build()
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
            // Routing settles just after play(); then keep watching for route
            // changes until this static sweep finishes. API 36 can report a
            // multi-device route; earlier APIs expose only one routed device.
            var lastRoute: List<AudioDeviceInfo>? = null
            val observedRoutes = mutableListOf<AudioDeviceInfo>()
            fun reportIfChanged() {
                val route = readRoutedDevices(track)
                val key = route.map { Triple(it.id, it.type, it.productName?.toString()) }
                val previousKey = lastRoute?.map { Triple(it.id, it.type, it.productName?.toString()) }
                if (route.isNotEmpty() && key != previousKey) {
                    lastRoute = route
                    for (device in route) {
                        val deviceKey = Triple(device.id, device.type, device.productName?.toString())
                        if (observedRoutes.none {
                                Triple(it.id, it.type, it.productName?.toString()) == deviceKey
                            }) observedRoutes += device
                    }
                    // Keep all endpoints observed during this sweep. If the
                    // route changes mid-stimulus, the consumer sees a mixture
                    // and marks the measurement ambiguous rather than tagging
                    // only the final destination.
                    if (playing && audioTrack === track) onRouted(observedRoutes.toList())
                }
            }
            for (i in 0 until ROUTE_POLLS) {
                reportIfChanged()
                if (lastRoute != null || !playing) break
                Thread.sleep(ROUTE_POLL_MS)
            }
            while (hasPendingPlayback(track, pcm.size)) {
                Thread.sleep(ROUTE_MONITOR_MS)
                reportIfChanged()
            }
            reportIfChanged()
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

    private fun hasPendingPlayback(track: AudioTrack, frameCount: Int): Boolean = try {
        playing && audioTrack === track && track.playState == AudioTrack.PLAYSTATE_PLAYING &&
            track.playbackHeadPosition < frameCount
    } catch (e: RuntimeException) {
        false // stop() may release the track while the route monitor is waking
    }

    private fun readRoutedDevices(track: AudioTrack): List<AudioDeviceInfo> = try {
        if (Build.VERSION.SDK_INT >= 36) {
            track.routedDevices.toList()
        } else {
            listOfNotNull(track.routedDevice)
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "AudioTrack route could not be read", e)
        emptyList()
    }

    private companion object {
        const val TAG = "CoreEqStimulus"
        const val ROUTE_POLLS = 10
        const val ROUTE_POLL_MS = 50L
        const val ROUTE_MONITOR_MS = 100L
    }
}
