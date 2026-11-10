package tv.corebuilds.eq.measure

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
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
 *
 * Preparation is cancellable. Each playback is an attempt with its own focus
 * lease and track; a stop or a newer [playSweep] cancels the old attempt. An
 * old attempt may not start a track, abandon a newer focus lease, or release a
 * newer track. The internal backend seam lets JVM tests exercise those races
 * without an AudioTrack device.
 */
class StimulusPlayer internal constructor(
    private val backend: Backend,
    private val runPreparation: (() -> Unit) -> Unit,
    private val preparationHook: () -> Unit
) {
    constructor(context: Context? = null) : this(
        AndroidBackend(context),
        { work -> thread(name = "CoreEqStimulus", block = work) },
        {}
    )

    internal interface FocusLease {
        fun abandon()
    }

    internal interface Track {
        fun write(pcm: ShortArray): Int
        fun play()
        fun stop()
        fun release()
        fun routedDevices(): List<AudioDeviceInfo>
        fun hasPendingPlayback(frameCount: Int): Boolean
    }

    internal interface Backend {
        /** Null means focus was denied. A no-op lease means the platform has no focus manager. */
        fun requestFocus(onLoss: () -> Unit): FocusLease?
        fun renderStimulus(): ShortArray
        fun openTrack(pcm: ShortArray, sessionId: Int?): Track
    }

    private val lock = Any()
    private var current: Attempt? = null
    private var generation = 0L

    private class Attempt(
        val generation: Long,
        val onError: (String) -> Unit,
        val onRouted: (List<AudioDeviceInfo>) -> Unit,
        val sessionId: Int?
    ) {
        var focus: FocusLease? = null
        var track: Track? = null
        var cancelled = false
    }

    private class Resources(val focus: FocusLease?, val track: Track?)

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
        val (attempt, previous) = synchronized(lock) {
            val old = current
            val next = Attempt(++generation, onError, onRouted, sessionId)
            current = next
            next to old
        }
        if (previous != null) finish(previous, null)
        try {
            runPreparation { prepare(attempt) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Stimulus preparation could not be scheduled", e)
            fail(attempt, "Could not start the measurement sweep: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun stop() {
        val attempt = synchronized(lock) {
            generation += 1
            val old = current
            current = null
            old
        }
        if (attempt != null) finish(attempt, null)
    }

    fun isPlaying(): Boolean = synchronized(lock) { current != null }

    private fun prepare(attempt: Attempt) {
        if (!isCurrent(attempt)) return
        val lease = try {
            backend.requestFocus {
                if (isCurrent(attempt)) {
                    finish(attempt, "Audio focus was lost during the sweep. Close the other app and measure again.")
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Audio focus request failed", e)
            fail(attempt, "Another app is using audio. Close it or wait a moment, then measure again.")
            return
        }
        if (lease == null) {
            fail(attempt, "Another app is using audio. Close it or wait a moment, then measure again.")
            return
        }
        if (!attachFocus(attempt, lease)) {
            abandon(lease)
            return
        }
        if (!isCurrent(attempt)) return

        val pcm = try {
            backend.renderStimulus()
        } catch (e: Exception) {
            Log.e(TAG, "Stimulus render failed", e)
            fail(attempt, "Could not render the measurement sweep: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        if (!isCurrent(attempt)) return
        try {
            preparationHook()
        } catch (e: Exception) {
            Log.w(TAG, "Stimulus preparation hook failed", e)
            fail(attempt, "Stimulus preparation failed: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        if (!isCurrent(attempt)) return

        val track = try {
            backend.openTrack(pcm, attempt.sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack creation failed", e)
            fail(attempt, "The TV refused to open a ${DspConstants.FS} Hz audio track: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        if (!isCurrent(attempt)) {
            releaseTrack(track)
            return
        }
        val written = try {
            track.write(pcm)
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack.write failed", e)
            releaseTrack(track)
            fail(attempt, "Could not load the sweep into the audio track: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        if (!isCurrent(attempt)) {
            releaseTrack(track)
            return
        }
        if (written != pcm.size) {
            releaseTrack(track)
            fail(attempt, "Could not load the sweep into the audio track (AudioTrack.write returned $written).")
            return
        }
        if (!attachTrack(attempt, track)) {
            releaseTrack(track)
            return
        }

        val playError = synchronized(lock) {
            if (!activeLocked(attempt)) null
            else try {
                track.play()
                null
            } catch (e: RuntimeException) {
                e
            }
        }
        if (playError != null) {
            Log.e(TAG, "AudioTrack.play failed", playError)
            fail(attempt, "The TV would not start playback: ${playError.message ?: "IllegalStateException"}")
            return
        }
        if (!isCurrent(attempt)) return

        try {
            monitor(attempt, track, pcm.size)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            fail(attempt, "Stimulus playback was interrupted.")
            return
        } catch (e: RuntimeException) {
            Log.e(TAG, "Stimulus playback monitor failed", e)
            fail(attempt, "Stimulus playback failed: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        finish(attempt, null)
    }

    private fun monitor(attempt: Attempt, track: Track, frameCount: Int) {
        // Routing settles just after play(); then keep watching for route
        // changes until this static sweep finishes. API 36 can report a
        // multi-device route; earlier APIs expose only one routed device.
        var lastRoute: List<AudioDeviceInfo>? = null
        val observedRoutes = mutableListOf<AudioDeviceInfo>()
        fun reportIfChanged() {
            if (!ownsTrack(attempt, track)) return
            val route = try {
                track.routedDevices()
            } catch (e: RuntimeException) {
                Log.w(TAG, "AudioTrack route could not be read", e)
                emptyList()
            }
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
                if (ownsTrack(attempt, track)) attempt.onRouted(observedRoutes.toList())
            }
        }
        for (i in 0 until ROUTE_POLLS) {
            reportIfChanged()
            if (lastRoute != null || !isCurrent(attempt)) break
            Thread.sleep(ROUTE_POLL_MS)
        }
        while (hasPendingPlayback(attempt, track, frameCount)) {
            Thread.sleep(ROUTE_MONITOR_MS)
            reportIfChanged()
        }
        reportIfChanged()
    }

    private fun isCurrent(attempt: Attempt): Boolean = synchronized(lock) {
        activeLocked(attempt)
    }

    private fun activeLocked(attempt: Attempt): Boolean =
        !attempt.cancelled && current === attempt

    private fun ownsTrack(attempt: Attempt, track: Track): Boolean = synchronized(lock) {
        activeLocked(attempt) && attempt.track === track
    }

    private fun attachFocus(attempt: Attempt, lease: FocusLease): Boolean = synchronized(lock) {
        if (!activeLocked(attempt)) false
        else {
            attempt.focus = lease
            true
        }
    }

    private fun attachTrack(attempt: Attempt, track: Track): Boolean = synchronized(lock) {
        if (!activeLocked(attempt)) false
        else {
            attempt.track = track
            true
        }
    }

    private fun hasPendingPlayback(attempt: Attempt, track: Track, frameCount: Int): Boolean = synchronized(lock) {
        if (!activeLocked(attempt) || attempt.track !== track) false
        else try {
            track.hasPendingPlayback(frameCount)
        } catch (e: RuntimeException) {
            false // stop() may release the track while the route monitor is waking
        }
    }

    private fun fail(attempt: Attempt, message: String) {
        finish(attempt, message)
    }

    /** Detach exactly once and release outside the lock; a superseded attempt reports no failure. */
    private fun finish(attempt: Attempt, error: String?): Boolean {
        val resources = synchronized(lock) {
            if (attempt.cancelled) return@synchronized null
            attempt.cancelled = true
            if (current === attempt) current = null
            val detached = Resources(attempt.focus, attempt.track)
            attempt.focus = null
            attempt.track = null
            detached
        } ?: return false
        release(resources)
        val reportError = synchronized(lock) { error != null && attempt.generation == generation }
        if (reportError) attempt.onError(error)
        return true
    }

    private fun release(resources: Resources) {
        resources.track?.let { track ->
            try {
                track.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioTrack.stop on a track that was not playing", e)
            } catch (e: RuntimeException) {
                Log.w(TAG, "AudioTrack.stop failed", e)
            }
            releaseTrack(track)
        }
        resources.focus?.let(::abandon)
    }

    private fun releaseTrack(track: Track) {
        try {
            track.release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "AudioTrack.release failed", e)
        }
    }

    private fun abandon(lease: FocusLease) {
        try {
            lease.abandon()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not abandon audio focus", e)
        }
    }

    private object NoFocusLease : FocusLease {
        override fun abandon() = Unit
    }

    private class AndroidBackend(private val context: Context?) : Backend {
        override fun requestFocus(onLoss: () -> Unit): FocusLease? {
            val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return NoFocusLease
            val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onLoss()
                }
            }
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            val result = audioManager.requestAudioFocus(request)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return null
            return object : FocusLease {
                override fun abandon() {
                    audioManager.abandonAudioFocusRequest(request)
                }
            }
        }

        override fun renderStimulus(): ShortArray =
            Sweep.stimulus(DspConstants.FS).let { s ->
                ShortArray(s.size) { (s[it] * Short.MAX_VALUE).toInt().toShort() }
            }

        override fun openTrack(pcm: ShortArray, sessionId: Int?): Track {
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
            return AndroidTrack(builder.build())
        }
    }

    private class AndroidTrack(private val track: AudioTrack) : Track {
        override fun write(pcm: ShortArray): Int = track.write(pcm, 0, pcm.size)
        override fun play() = track.play()
        override fun stop() = track.stop()
        override fun release() = track.release()
        override fun routedDevices(): List<AudioDeviceInfo> =
            if (Build.VERSION.SDK_INT >= 36) track.routedDevices.toList()
            else listOfNotNull(track.routedDevice)

        override fun hasPendingPlayback(frameCount: Int): Boolean =
            track.playState == AudioTrack.PLAYSTATE_PLAYING && track.playbackHeadPosition < frameCount
    }

    private companion object {
        const val TAG = "CoreEqStimulus"
        const val ROUTE_POLLS = 10
        const val ROUTE_POLL_MS = 50L
        const val ROUTE_MONITOR_MS = 100L
    }
}