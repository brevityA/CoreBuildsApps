package tv.corebuilds.eq.apply

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import tv.corebuilds.eq.export.ProfileStore

/**
 * Foreground service that maintains audio effect instances, prevents Android TV
 * from killing DSP sessions during sleep/wake or app switches, and re-applies gains.
 */
class EqService : Service() {

    private val activeEqualizers = mutableMapOf<Int, Equalizer>()
    private lateinit var profileStore: ProfileStore

    override fun onCreate() {
        super.onCreate()
        profileStore = ProfileStore(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)

        when (action) {
            ACTION_APPLY_SESSION -> {
                if (sessionId != AudioEffect.ERROR_BAD_VALUE) {
                    applyToSession(sessionId)
                }
            }
            ACTION_CLOSE_SESSION -> {
                if (sessionId != AudioEffect.ERROR_BAD_VALUE) {
                    releaseSession(sessionId)
                }
            }
            ACTION_REAPPLY_ALL -> {
                reapplyAll()
            }
        }
        return START_STICKY
    }

    private fun applyToSession(sessionId: Int) {
        try {
            var eq = activeEqualizers[sessionId]
            if (eq == null) {
                eq = Equalizer(0, sessionId)
                activeEqualizers[sessionId] = eq
            }
            val activeProfile = profileStore.getActiveProfile()
            val bands = activeProfile.platformBands
            val numBands = eq.numberOfBands.toInt()

            for (i in 0 until numBands) {
                val centerHz = eq.getCenterFreq(i.toShort()).toDouble() / 1000.0
                val matchingBand = bands.minByOrNull { Math.abs(it.centerHz - centerHz) }
                val millibels = matchingBand?.millibels ?: 0
                val range = eq.bandLevelRange
                val clamped = if (range != null && range.size >= 2) {
                    millibels.coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                } else {
                    millibels.toShort()
                }
                eq.setBandLevel(i.toShort(), clamped)
            }
            eq.enabled = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseSession(sessionId: Int) {
        val eq = activeEqualizers.remove(sessionId)
        try {
            eq?.enabled = false
            eq?.release()
        } catch (ignored: Exception) {}
    }

    private fun reapplyAll() {
        for (sessionId in activeEqualizers.keys) {
            applyToSession(sessionId)
        }
    }

    override fun onDestroy() {
        for (eq in activeEqualizers.values) {
            try {
                eq.enabled = false
                eq.release()
            } catch (ignored: Exception) {}
        }
        activeEqualizers.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Core EQ Effect Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Core EQ correction active across apps"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Core EQ")
            .setContentText("Room correction active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_APPLY_SESSION = "tv.corebuilds.eq.action.APPLY_SESSION"
        const val ACTION_CLOSE_SESSION = "tv.corebuilds.eq.action.CLOSE_SESSION"
        const val ACTION_REAPPLY_ALL = "tv.corebuilds.eq.action.REAPPLY_ALL"
        private const val CHANNEL_ID = "core_eq_service_channel"
        private const val NOTIFICATION_ID = 1010
    }
}
