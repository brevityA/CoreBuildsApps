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
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
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
import java.util.concurrent.Executors

/**
 * Applies the active profile and keeps it applied.
 *
 * Started only from Core EQ's own screens (Android 8+ forbids starting it from
 * the background), it registers for the players' audio-session broadcasts
 * itself: implicit broadcasts no longer reach manifest receivers, so a
 * receiver that lives in the manifest would never hear them.
 *
 * Two paths, never both at once (that would correct twice):
 * - **Whole TV** – an [Equalizer] on the output mix (session 0). Deprecated,
 *   never removed, and accepted by some TVs.
 * - **Per player** – an [Equalizer] on each session a player announces, or
 *   that DUMP discovery finds (rung 2, only where the user granted the
 *   one-time [DumpsysDiscovery.DUMP_PERMISSION]). Announced sessions win over
 *   discovered ones for the same session id; a discovered session is released
 *   the moment its player disappears from the dump.
 *
 * The platform [Equalizer] has no preamp, so every band is lowered by the
 * largest boost: the correction never pushes the signal into clipping.
 * Every outcome, good or bad, is written to [ProfileStore.setStatus] and the
 * notification, so a failure is always named somewhere the user can see.
 *
 * While audio is playing through the correction the status gains a
 * "▶ Playing ·" marker — the Home screen indicator and the notification both
 * light from that one fact. The platform reports *that* something is playing
 * ([AudioManager] playback callbacks) but never *which* app is playing, so the
 * marker means "the TV is playing and the correction is attached", and the
 * status line is what names the players being corrected.
 */
class EqService : Service() {

    /** An equaliser held on one session, and where that session came from. */
    private data class Held(val eq: Equalizer, val pkg: String, val discovered: Boolean)

    private lateinit var store: ProfileStore
    private val sessionEqs = mutableMapOf<Int, Held>()
    private var globalEq: Equalizer? = null
    private var globalError: String? = null
    private var suspended = false

    // The last words report() chose, and what publish() last put on screen, so
    // a playback change can re-publish the same status with (or without) the
    // playing marker without inventing new text.
    private var messageBody = ""
    private var messageError = false
    private var lastShown: String? = null
    private var lastPlaying: Boolean? = null

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    // DUMP discovery runs off the main thread and applies its results back on
    // it; a playback change coalesces into one re-discovery after the debounce.
    private val mainHandler = Handler(Looper.getMainLooper())
    private val discoveryExecutor = Executors.newSingleThreadExecutor()
    private val discoveryRunnable = Runnable {
        discoveryExecutor.execute {
            val found = try {
                DumpsysDiscovery.discover(android.os.Process.myUid())
            } catch (e: Exception) {
                Log.w(TAG, "DUMP discovery failed", e)
                emptyList()
            }
            mainHandler.post { applyDiscovered(found) }
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            // The delivered list is the change that fired this: use it instead
            // of re-querying, which costs a binder call and can race the event.
            publish(configs)
            scheduleDiscovery()
        }
    }

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
        // The indicator is not a guess: the platform says when a player is
        // audible, and the badge follows that. A refusal only costs the pulse;
        // the status line still works.
        try {
            audioManager.registerAudioPlaybackCallback(playbackCallback, null)
        } catch (e: Exception) {
            Log.w(TAG, "Playback watcher refused; the playing marker is off", e)
        }
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

    private fun applyAll() {
        if (suspended) return
        val profile = store.getActiveProfile()
        if (profile == null) {
            report("No measurement yet. Measure this room to create a correction.", isError = true)
            return
        }

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
                report("Correcting the whole TV · ${profile.name} · $bands bands", isError = false)
                releaseSessions() // the whole-TV path already covers them
                return
            } catch (e: Exception) {
                Log.w(TAG, "Output-mix Equalizer could not be configured", e)
                global.release()
                globalEq = null
                globalError = e.message ?: e.javaClass.simpleName
            }
        }

        var failed: String? = null
        for ((session, held) in sessionEqs.toMap()) {
            try {
                configure(held.eq, profile)
            } catch (e: Exception) {
                failed = "Could not correct ${label(held.pkg)}: ${e.message ?: e.javaClass.simpleName}"
                closeSession(session)
            }
        }
        when {
            failed != null -> report(failed, isError = true)
            sessionEqs.isNotEmpty() -> report(sessionReport(profile), isError = false)
            else -> {
                if (DumpsysDiscovery.hasGrant(this)) scheduleDiscovery(0L)
                report(waitingStatus(), isError = false)
            }
        }
    }

    /** What to say when nothing is attached: the way forward, named per grant. */
    private fun waitingStatus(): String =
        if (DumpsysDiscovery.hasGrant(this)) {
            "DUMP discovery is looking for players. This TV refused whole-TV correction " +
                "($globalError), so Core EQ attaches to each player it finds and names them here."
        } else {
            "Waiting for a player that shares its audio (Kodi, VLC, Poweramp). " +
                "This TV refused whole-TV correction ($globalError), so apps that do not " +
                "share their audio, such as Netflix and YouTube, are not corrected. " +
                "Grant DUMP discovery on the Capability screen to reach them, or export " +
                "the profile for the TV's own sound settings."
        }

    private fun openSession(session: Int, pkg: String) {
        store.noteSessionPackage(pkg)
        if (globalEq != null) return // already corrected by the whole-TV path
        val profile = store.getActiveProfile() ?: return
        try {
            // A discovered session a player just announced is upgraded, not
            // duplicated: the announcement carries the player's own name.
            val eq = sessionEqs[session]?.eq ?: Equalizer(PRIORITY, session)
            val name = pkg.ifBlank { sessionEqs[session]?.pkg ?: "" }
            sessionEqs[session] = Held(eq, name, discovered = false)
            if (suspended) {
                eq.enabled = false
            } else {
                val bands = configure(eq, profile)
                report("Correcting ${label(name)} · ${profile.name} · $bands bands", isError = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer on session $session ($pkg) failed", e)
            closeSession(session)
            report("Could not correct ${label(pkg)}: ${e.message ?: e.javaClass.simpleName}", isError = true)
        }
    }

    private fun closeSession(session: Int) {
        val eq = sessionEqs.remove(session)?.eq ?: return
        try {
            eq.enabled = false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Session $session already gone", e)
        }
        eq.release()
        publish() // what is being corrected may have just changed
    }

    private fun releaseSessions() {
        for (s in sessionEqs.keys.toList()) closeSession(s)
    }

    /** Sets every band from the profile's curve; returns the band count. Throws when control is lost. */
    private fun configure(eq: Equalizer, profile: Profile): Int {
        val n = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        val centres = DoubleArray(n) { eq.getCenterFreq(it.toShort()) / 1000.0 }
        val mbs = BandMapping.millibels(
            BandMapping.gainsDb(profile, centres.toList()),
            range[0].toInt()..range[1].toInt()
        )
        for (i in 0 until n) eq.setBandLevel(i.toShort(), mbs[i])
        eq.enabled = true
        check(eq.hasControl()) { "another equaliser app has priority over this audio" }
        return n
    }

    private fun setAllEnabled(on: Boolean) {
        val all = listOfNotNull(globalEq) + sessionEqs.values.map { it.eq }
        for (eq in all) {
            try {
                eq.enabled = on
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Equalizer no longer valid", e)
            }
        }
    }

    /**
     * Debounced re-discovery (plan §4: `dumpsys` is a heavy IPC). Playback
     * changes coalesce into one run; measurement never competes with it.
     */
    private fun scheduleDiscovery(delayMs: Long = DISCOVERY_DEBOUNCE_MS) {
        if (suspended || globalEq != null) return
        if (!DumpsysDiscovery.hasGrant(this)) return
        mainHandler.removeCallbacks(discoveryRunnable)
        mainHandler.postDelayed(discoveryRunnable, delayMs)
    }

    /**
     * Applies one discovery result on the main thread: attaches to new
     * sessions, releases discovered sessions whose player left the dump, and
     * names exactly what is being corrected. Announced sessions are never
     * released here — their CLOSE broadcast is their owner.
     */
    private fun applyDiscovered(found: List<DiscoveredSession>) {
        if (!running || suspended || globalEq != null) return
        val profile = store.getActiveProfile() ?: return
        var changed = false
        for (s in found) {
            if (sessionEqs.containsKey(s.sessionId)) continue
            val pkg = packageManager.getPackagesForUid(s.uid ?: continue)?.firstOrNull() ?: continue
            try {
                val eq = Equalizer(PRIORITY, s.sessionId)
                sessionEqs[s.sessionId] = Held(eq, pkg, discovered = true)
                configure(eq, profile)
                changed = true
            } catch (e: Exception) {
                Log.w(TAG, "Equalizer on discovered session ${s.sessionId} failed", e)
                closeSession(s.sessionId)
                report(
                    "Could not correct ${label(pkg)} (found by DUMP discovery): ${e.message ?: e.javaClass.simpleName}",
                    isError = true
                )
                return
            }
        }
        val present = found.mapTo(mutableSetOf()) { it.sessionId }
        for (session in sessionEqs.keys.toList()) {
            val held = sessionEqs[session] ?: continue
            if (held.discovered && session !in present) {
                closeSession(session)
                changed = true
            }
        }
        if (changed) {
            // The last player leaving is not "Correcting nobody": it is back
            // to waiting, with the same words applyAll would use.
            if (sessionEqs.isEmpty()) {
                report(waitingStatus(), isError = false)
            } else {
                report(sessionReport(profile), isError = false)
            }
        }
    }

    /** The status line for whatever is attached right now, with its provenance. */
    private fun sessionReport(profile: Profile?): String {
        val held = sessionEqs.values.sortedBy { it.discovered }
        if (held.isEmpty()) return waitingStatus()
        val names = held.joinToString { label(it.pkg) }
        val marker = if (held.any { it.discovered }) " · found by DUMP discovery" else ""
        return "Correcting $names · ${profile?.name ?: "the active profile"}$marker"
    }

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg.ifBlank { "a player" }
    }

    /**
     * Publishes the status to every visible surface — the Home screen status
     * line, its correction indicator, and the notification — from one place,
     * so they cannot disagree. The words are whatever [report] last chose; the
     * "▶ Playing ·" marker is added while audio the correction is attached to
     * is audible right now. Unchanged reports publish nothing, so a playback
     * callback on every focus change cannot spam the notification.
     */
    private fun publish(configs: List<AudioPlaybackConfiguration>? = null) {
        if (!running || messageBody.isEmpty()) return
        val playingNow = !suspended && !messageError && correcting() && audioIsPlaying(configs)
        val shown = if (playingNow) "▶ Playing · $messageBody" else messageBody
        if (shown == lastShown && playingNow == lastPlaying) return
        lastShown = shown
        lastPlaying = playingNow
        store.setStatus(shown, messageError, playing = playingNow)
        // Without POST_NOTIFICATIONS (Android 13+) the update is not shown; the
        // Home screen still shows the same status from ProfileStore.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(shown))
        }
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
    }

    private fun report(message: String, isError: Boolean) {
        messageBody = message
        messageError = isError
        publish()
    }

    /** True while an equaliser is configured and enabled on something. */
    private fun correcting(): Boolean = globalEq != null || sessionEqs.isNotEmpty()

    /**
     * True when something on this TV is playing audio right now.
     *
     * [AudioManager.getActivePlaybackConfigurations] lists only streams that
     * are actually playing (the platform sanitizes the list down to active
     * ones) — or [configs], the list [playbackCallback] just delivered — and
     * the callback re-runs this on every change. Only media traffic counts:
     * notification beeps and the like must not light the badge. It cannot say
     * *which* app is playing — player identity is hidden from third-party
     * apps — and our own measurement sweep is silenced by [suspended], never
     * by guessing at the caller.
     */
    private fun audioIsPlaying(configs: List<AudioPlaybackConfiguration>? = null): Boolean {
        val active = try {
            configs ?: audioManager.activePlaybackConfigurations
        } catch (e: Exception) {
            Log.w(TAG, "Playback probe failed", e)
            return false
        }
        return active.any { config ->
            when (config.audioAttributes?.usage) {
                null, AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_GAME, AudioAttributes.USAGE_UNKNOWN -> true
                else -> false
            }
        }
    }

    override fun onDestroy() {
        if (!running) {
            super.onDestroy()
            return
        }
        // Down first: teardown below closes sessions, and publish() must not
        // write a transient "Correcting …" status (or light the indicator)
        // mid-teardown only to have it overwritten a moment later.
        running = false
        try {
            unregisterReceiver(sessionReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Session receiver was not registered", e)
        }
        try {
            audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Playback watcher was not registered", e)
        }
        mainHandler.removeCallbacksAndMessages(null)
        discoveryExecutor.shutdownNow()
        releaseSessions()
        globalEq?.release()
        globalEq = null
        // "Off" is the user's switch, not the service's fate: a service the
        // system stopped is not a correction the user turned off, and the
        // screen must not say so.
        store.setStatus(
            if (store.correctionEnabled) {
                "Correction stopped in the background. It resumes as soon as you open Core EQ."
            } else {
                "Correction is off."
            },
            isError = false
        )
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

        /** Playback changes coalesce into one re-discovery after this pause. */
        private const val DISCOVERY_DEBOUNCE_MS = 2000L

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
