package tv.corebuilds.eq.apply

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build

/**
 * The media volume as Android's own volume curve states it, in dB below full
 * scale, for Low-volume bass (1.3.0).
 *
 * A volume step is not a fixed number of dB: each device maps its index
 * through its own curve, which [AudioManager.getStreamVolumeDb] reports. The
 * 1.2.0 estimate treated the index as linear amplitude and is gone.
 *
 * Null whenever Android cannot say: a fixed-volume output (HDMI-CEC or a
 * soundbar that keeps its own volume), a muted stream, or a device that has
 * no dB curve for the route. Low-volume bass then adds nothing, and the
 * Extra effects screen says why.
 */
object VolumeLevel {

    fun mediaDb(context: Context): Double? {
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        if (am.isVolumeFixed) return null
        val index = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (index <= 0) return null
        val type = mediaDeviceType(am) ?: return null
        return try {
            am.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, type).toDouble().takeIf { it.isFinite() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** The output media is expected to play on, ranked as [OutputRoute.current] ranks it. */
    private fun mediaDeviceType(am: AudioManager): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val routed = try {
                am.getAudioDevicesForAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
            } catch (_: Exception) {
                emptyList()
            }
            routed.firstOrNull()?.let { return it.type }
        }
        val connected = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val kind = OutputRoute.pickKind(connected.map { it.type }) ?: return null
        return connected.firstOrNull { OutputRoute.kindOf(it.type) == kind }?.type
    }
}
