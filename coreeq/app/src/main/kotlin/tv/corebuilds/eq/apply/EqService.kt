package tv.corebuilds.eq.apply

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.MainActivity
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Applies the active profile and keeps it applied.
 *
 * Started only from Core EQ's own screens (Android 8+ forbids starting it from
 * the background), it registers for the players' audio-session broadcasts
 * itself: implicit broadcasts no longer reach manifest receivers, so a
 * receiver that lives in the manifest would never hear them.
 *
 * Correction follows the output ([OutputRoute]): each profile belongs to the
 * chain it was measured on, the device callback re-picks when a soundbar or
 * headphones come and go, and an output nothing was measured on gets no
 * correction rather than another chain's.
 *
 * Two paths, never both at once (that would correct twice):
 * - **Whole TV** – an [Equalizer] on the output mix (session 0). Deprecated,
 *   never removed, and accepted by some TVs.
 * - **Per player** – an [Equalizer] on each session a player announces.
 *
 * The platform [Equalizer] has no preamp, so every band is lowered by the
 * largest boost: the correction never pushes the signal into clipping.
 * Every outcome, good or bad, is written to [ProfileStore.setStatus] and the
 * notification, so a failure is always named somewhere the user can see.
 */
class EqService : Service() {

    private lateinit var store: ProfileStore
    private val sessionEqs = mutableMapOf<Int, Pair<Equalizer, String>>()
    private var globalEq: Equalizer? = null
    private var globalError: String? = null
    private var suspended = false

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: ""
            if (session == AudioEffect.ERROR_BAD_VALUE || session == 0) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> openSession(session, pkg)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> closeSession(session)
            }
        }
    }

    /** Plugging in a soundbar or headphones changes which profile is right. */
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onOutputsChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onOutputsChanged()
    }
    private var lastKind: String? = null

    private fun onOutputsChanged() {
        val kind = OutputRoute.current(this)?.kind
        if (kind == lastKind) return // an input, or a second device of the same kind
        lastKind = kind
        applyAll()
    }

    override fun onCreate() {
        super.onCreate()
        store = ProfileStore(this)
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Starting room correction…"), type)
        } catch (e: Exception) {
            // Android 12+ refuses foreground starts from the background, and
            // Android 15 refuses media-playback ones after a reboot.
            Log.e(TAG, "startForeground refused", e)
            store.setStatus(
                "Android would not let correction run (${e.javaClass.simpleName}). It resumes as soon as you open Core EQ.",
                isError = true
            )
            stopSelf()
            return
        }
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        // Exported: the broadcasts come from the players, which are other apps.
        ContextCompat.registerReceiver(this, sessionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        lastKind = OutputRoute.current(this)?.kind
        // Registering delivers the current devices once; lastKind makes that a no-op.
        getSystemService(AudioManager::class.java)?.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) return START_NOT_STICKY // onCreate could not go foreground
        when (intent?.action) {
            ACTION_STOP -> {
                store.correctionEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SUSPEND -> {
                suspended = true
                setAllEnabled(false)
                report("Correction paused while this room is measured.", isError = false)
            }
            ACTION_RESUME -> {
                suspended = false
                applyAll()
            }
            else -> applyAll() // ACTION_START, ACTION_REAPPLY, or a sticky restart
        }
        return START_STICKY
    }

    /** The profile for the output playing now, and that output. */
    private fun resolve(): Triple<Profile?, OutputRoute.Output?, Boolean> {
        val output = OutputRoute.current(this)
        val pick = OutputRoute.pick(store.getAllProfiles(), store.chosenId(), output?.kind)
        return Triple(pick.profile, output, pick.switched)
    }

    /** " · on Sonos Beam", plus the passthrough caveat where it applies. */
    private fun where(output: OutputRoute.Output?): String {
        if (output == null) return ""
        val passthrough = if (OutputRoute.mayPassThrough(this, output.kind)) {
            ". Dolby and DTS sent to it as a bitstream bypass any on-device EQ; set Surround sound to PCM to correct them"
        } else {
            ""
        }
        return " · on ${output.name}$passthrough"
    }

    private fun applyAll() {
        if (suspended) return
        if (store.getAllProfiles().isEmpty()) {
            report("No measurement yet. Measure this room to create a correction.", isError = true)
            return
        }
        val (profile, output, switched) = resolve()
        if (profile == null) {
            // Another chain's curve would be wrong here: step aside, say why.
            setAllEnabled(false)
            report(
                "Nothing measured on ${OutputRoute.label(output?.kind)} yet, so correction is paused there. " +
                    "Measure with it playing, or switch back to an output you measured.",
                isError = false
            )
            return
        }
        val via = if (switched) " (this output's own profile)" else ""

        if (globalEq == null && globalError == null) {
            try {
                globalEq = Equalizer(PRIORITY, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Output-mix Equalizer (session 0) refused", e)
                globalError = e.message ?: e.javaClass.simpleName
            }
        }

        val global = globalEq
        if (global != null) {
            try {
                val bands = configure(global, profile)
                releaseSessions() // the whole-TV path already covers them
                report("Correcting the whole TV · ${profile.name}$via · $bands bands${where(output)}", isError = false)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Output-mix Equalizer could not be configured", e)
                global.release()
                globalEq = null
                globalError = e.message ?: e.javaClass.simpleName
            }
        }

        var failed: String? = null
        for ((session, pair) in sessionEqs.toMap()) {
            try {
                configure(pair.first, profile)
            } catch (e: Exception) {
                failed = "Could not correct ${label(pair.second)}: ${e.message ?: e.javaClass.simpleName}"
                closeSession(session)
            }
        }
        when {
            failed != null -> report(failed, isError = true)
            sessionEqs.isNotEmpty() -> report(
                "Correcting ${sessionEqs.values.joinToString { label(it.second) }} · ${profile.name}$via${where(output)}",
                isError = false
            )
            else -> report(
                "Waiting for a player that shares its audio (Kodi, VLC, Poweramp). " +
                    "This TV refused whole-TV correction ($globalError), so apps that do not " +
                    "share their audio, such as Netflix and YouTube, are not corrected. " +
                    "Export the profile for the TV's own sound settings instead.",
                isError = false
            )
        }
    }

    private fun openSession(session: Int, pkg: String) {
        store.noteSessionPackage(pkg)
        if (globalEq != null) return // already corrected by the whole-TV path
        if (store.getAllProfiles().isEmpty()) return
        val (profile, output, _) = resolve()
        try {
            val eq = sessionEqs[session]?.first ?: Equalizer(PRIORITY, session)
            sessionEqs[session] = Pair(eq, pkg)
            if (suspended || profile == null) {
                // Held, not dropped: an output change or a resume re-applies it.
                eq.enabled = false
            } else {
                val bands = configure(eq, profile)
                report("Correcting ${label(pkg)} · ${profile.name} · $bands bands${where(output)}", isError = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer on session $session ($pkg) failed", e)
            closeSession(session)
            report("Could not correct ${label(pkg)}: ${e.message ?: e.javaClass.simpleName}", isError = true)
        }
    }

    private fun closeSession(session: Int) {
        val eq = sessionEqs.remove(session)?.first ?: return
        try {
            eq.enabled = false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Session $session already gone", e)
        }
        eq.release()
    }

    private fun releaseSessions() {
        for (s in sessionEqs.keys.toList()) closeSession(s)
    }

    /** Sets every band from the profile's curve; returns the band count. Throws when control is lost. */
    private fun configure(eq: Equalizer, profile: Profile): Int {
        val n = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        val gains = DoubleArray(n) { profile.correctionAt(eq.getCenterFreq(it.toShort()) / 1000.0) }
        val headroom = max(0.0, gains.maxOrNull() ?: 0.0)
        for (i in 0 until n) {
            val mb = ((gains[i] - headroom) * 100.0).roundToInt().coerceIn(range[0].toInt(), range[1].toInt())
            eq.setBandLevel(i.toShort(), mb.toShort())
        }
        eq.enabled = true
        check(eq.hasControl()) { "another equaliser app has priority over this audio" }
        return n
    }

    private fun setAllEnabled(on: Boolean) {
        val all = listOfNotNull(globalEq) + sessionEqs.values.map { it.first }
        for (eq in all) {
            try {
                eq.enabled = on
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Equalizer no longer valid", e)
            }
        }
    }

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg.ifBlank { "a player" }
    }

    private fun report(message: String, isError: Boolean) {
        store.setStatus(message, isError)
        // Without POST_NOTIFICATIONS (Android 13+) the update is not shown; the
        // Home screen still shows the same status from ProfileStore.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(message))
        }
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
    }

    override fun onDestroy() {
        if (!running) {
            super.onDestroy()
            return
        }
        try {
            unregisterReceiver(sessionReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Session receiver was not registered", e)
        }
        getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(deviceCallback)
        releaseSessions()
        globalEq?.release()
        globalEq = null
        running = false
        store.setStatus("Correction is off.", isError = false)
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Room correction", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Shows what Core EQ is correcting" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Core EQ")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START = "tv.corebuilds.eq.action.START"
        const val ACTION_STOP = "tv.corebuilds.eq.action.STOP"
        const val ACTION_REAPPLY = "tv.corebuilds.eq.action.REAPPLY"
        const val ACTION_SUSPEND = "tv.corebuilds.eq.action.SUSPEND"
        const val ACTION_RESUME = "tv.corebuilds.eq.action.RESUME"
        const val ACTION_STATUS_CHANGED = "tv.corebuilds.eq.action.STATUS_CHANGED"
        private const val TAG = "CoreEqService"
        private const val CHANNEL_ID = "core_eq_service_channel"
        private const val NOTIFICATION_ID = 1010
        private const val PRIORITY = 0

        @Volatile
        var running = false
            private set

        /**
         * Turn correction on. Call from a visible screen only. Returns null on
         * success, or the reason Android refused, for the screen to show.
         */
        fun enable(context: Context): String? {
            val store = ProfileStore(context)
            if (store.getActiveProfile() == null) return "Measure this room first: there is no correction to apply yet."
            store.correctionEnabled = true
            return try {
                ContextCompat.startForegroundService(context, Intent(context, EqService::class.java).setAction(ACTION_START))
                null
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService refused", e)
                "Android would not start the correction service: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        fun disable(context: Context) {
            ProfileStore(context).correctionEnabled = false
            if (running) send(context, ACTION_STOP)
        }

        /** Deliver [action] to a running service; a no-op when correction is off. */
        fun send(context: Context, action: String) {
            if (!running) return
            try {
                context.startService(Intent(context, EqService::class.java).setAction(action))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not deliver $action", e)
            }
        }
    }
}
