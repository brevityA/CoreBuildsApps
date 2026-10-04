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
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.MainActivity
import tv.corebuilds.eq.export.PlatformBand
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore
import java.util.concurrent.Executors
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
 * Two correction scopes, never both at once (that would correct twice):
 * - **Whole TV** – an effect on the output mix (session 0), where firmware allows it.
 * - **Per player** – an effect on each session a player announces, or that DUMP
 *   discovery finds (rung 2, only where the user granted the one-time
 *   [DumpsysDiscovery.DUMP_PERMISSION]). Announced sessions win over discovered
 *   ones for the same session id; a discovered session is released the moment
 *   its player disappears from the dump.
 *
 * On API 28+ the service tries a DynamicsProcessing PreEQ with its limiter
 *   enabled, then falls back to the platform Equalizer on any refusal. Both
 *   paths lower gains by the largest boost; neither applies positive EQ gain.
 * Every outcome, good or bad, is written to [ProfileStore.setStatus] and the
 * notification, so a failure is always named somewhere the user can see.
 *
 * While audio is playing through the correction the status gains a
 * "▶ Playing ·" marker — the Home screen indicator and the notification both
 * light from that one fact. The platform reports *that* something is playing
 * ([AudioManager] playback callbacks) but never *which* app is playing, so the
 * marker is only shown on the whole-TV path, where anything playing is
 * corrected. On the per-player path the TV playing proves nothing — YouTube
 * can be audible while only Kodi's paused session is held — so the badge stays
 * armed and the status line names the players being corrected.
 */
class EqService : Service() {

    private data class AppliedEffect(
        val effect: AudioEffect,
        val engine: String,
        val bands: List<PlatformBand>
    )

    /** An effect held on one session, and where that session came from. */
    private data class Held(val applied: AppliedEffect, val pkg: String, val discovered: Boolean)

    private lateinit var store: ProfileStore
    private lateinit var modeStore: ContentModeStore
    private val sessionEqs = mutableMapOf<Int, Held>()
    private val announcedSessions = mutableMapOf<Int, String>()
    private var discoveredPackages: Set<String> = emptySet()
    private var globalEffect: AppliedEffect? = null
    private var globalError: String? = null
    private var suspended = false
    /** True while the current output has no profile of its own: effects are held, disabled. */
    private var outputPaused = false

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
            mainHandler.post { onOutputsChanged() }
            scheduleDiscovery()
        }
    }

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: ""
            if (session == AudioEffect.ERROR_BAD_VALUE || session == 0) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    val previousMode = modeStore.currentDecision().mode
                    if (pkg.isNotBlank()) announcedSessions[session] = pkg
                    store.noteSessionPackage(pkg)
                    if (globalEffect != null) refreshModeAndReapply(previousMode) else openSession(session, pkg)
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    val previousMode = modeStore.currentDecision().mode
                    announcedSessions.remove(session)
                    closeSession(session)
                    refreshModeAndReapply(previousMode)
                }
            }
        }
    }

    /** Plugging in a soundbar or headphones changes which profile is right. */
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onOutputsChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onOutputsChanged()
    }
    private var lastOutput: OutputRoute.Output? = null

    private fun onOutputsChanged() {
        val output = OutputRoute.current(this)
        if (output == lastOutput) return
        lastOutput = output
        applyAll()
    }

    override fun onCreate() {
        super.onCreate()
        store = ProfileStore(this)
        modeStore = ContentModeStore(this)
        store.clearRuntimeBands()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Starting audio correction…"), type)
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
        lastOutput = OutputRoute.current(this)
        // Registering delivers the current devices once; the full route identity makes that a no-op.
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

    /** Session-visible app identities are best-effort; DUMP adds active sessions when granted. */
    private fun activeAppPackages(): Set<String> =
        (announcedSessions.values + discoveredPackages)
            .filter { it.isNotBlank() && it != packageName }
            .toSet()

    private fun refreshModeAndReapply(previousMode: ContentMode) {
        val decision = modeStore.resolve(activeAppPackages())
        if (decision.mode != previousMode && running && store.correctionEnabled && !suspended) {
            applyAll()
        } else if (running) {
            publish()
        }
    }

    /** The route-matched room base plus its current content-mode overlay. */
    private fun resolve(): Triple<Profile?, OutputRoute.Output?, Boolean> {
        val output = OutputRoute.current(this)
        val pick = OutputRoute.pick(
            store.getAllProfiles(),
            store.chosenId(),
            output?.kind,
            output?.name
        )
        val mode = modeStore.resolve(activeAppPackages()).mode
        val effective = pick.profile?.let { modeStore.effectiveProfile(it, mode) }
        return Triple(effective, output, pick.switched)
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
            outputPaused = true
            setAllEnabled(false)
            report(
                "Nothing measured on ${OutputRoute.label(output?.kind)} yet, so correction is paused there. " +
                    "Measure with it playing, or switch back to an output you measured.",
                isError = false
            )
            return
        }
        val via = if (switched) " (this output's own profile)" else ""
        val modeName = modeStore.currentDecision().mode.title
        outputPaused = false

        var createdGlobalNow = false
        if (globalEffect == null && globalError == null) {
            try {
                globalEffect = createBestEffect(0, profile)
                createdGlobalNow = true
            } catch (e: Exception) {
                Log.w(TAG, "Output-mix correction refused", e)
                globalError = e.message ?: e.javaClass.simpleName
            }
        }

        val global = globalEffect
        if (global != null) {
            try {
                val configured = if (createdGlobalNow) global else configureOrReplace(global, 0, profile)
                globalEffect = configured
                syncRuntimeBands()
                report(
                    "Output-mix effect configured · $modeName · ${configured.engine} · ${profile.name}$via · ${configured.bands.size} bands · coverage depends on TV routing${where(output)}",
                    isError = false
                )
                releaseSessions() // avoid applying the same curve twice where the mix effect is accepted
                if (DumpsysDiscovery.hasGrant(this)) scheduleDiscovery(0L)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Output-mix correction could not be configured", e)
                releaseApplied(global)
                globalEffect = null
                globalError = e.message ?: e.javaClass.simpleName
            }
        }

        var failed: String? = null
        for ((session, held) in sessionEqs.toMap()) {
            try {
                val configured = configureOrReplace(held.applied, session, profile)
                sessionEqs[session] = held.copy(applied = configured)
            } catch (e: Exception) {
                failed = "Could not correct ${label(held.pkg)}: ${e.message ?: e.javaClass.simpleName}"
                closeSession(session)
            }
        }
        syncRuntimeBands()
        when {
            failed != null -> report(failed, isError = true)
            sessionEqs.isNotEmpty() -> report(sessionReport(profile, via, output), isError = false)
            else -> {
                if (DumpsysDiscovery.hasGrant(this)) scheduleDiscovery(0L)
                report(waitingStatus(), isError = false)
            }
        }
    }

    /** What to say when nothing is attached: the way forward, named per grant. */
    private fun waitingStatus(): String =
        if (DumpsysDiscovery.hasGrant(this)) {
            "DUMP discovery is looking for active players. The output-mix effect path was unavailable " +
                "($globalError), so Core EQ attaches to supported player sessions it finds and names them here."
        } else {
            "Waiting for a player that shares its audio (Kodi, VLC, Poweramp). " +
                "The output-mix effect path was unavailable ($globalError), so apps that do not " +
                "share their audio, such as Netflix and YouTube, are not covered by the session fallback. " +
                "Granting DUMP discovery on the Capability screen reaches some of them " +
                "(not YouTube), or export " +
                "the profile for the TV's own sound settings."
        }

    private fun openSession(session: Int, pkg: String) {
        if (globalEffect != null) return // already corrected by the output-mix path
        if (store.getAllProfiles().isEmpty()) return
        val (picked, output, _) = resolve()
        // An output nothing was measured on still gets the session held, just
        // disabled, so an output change or a resume can apply it later. The
        // effect needs some curve to be built with; it is never enabled with it.
        val profile = picked ?: store.getActiveProfile() ?: return
        val previous = sessionEqs[session]
        try {
            // A discovered session a player just announced is upgraded, not
            // duplicated: the announcement carries the player's own name.
            val applied = previous?.let { configureOrReplace(it.applied, session, profile) }
                ?: createBestEffect(session, profile)
            val name = pkg.ifBlank { previous?.pkg ?: "" }
            sessionEqs[session] = Held(applied, name, discovered = false)
            syncRuntimeBands()
            if (suspended || picked == null) {
                applied.effect.setEnabled(false)
            } else {
                report(
                    "Effect attached to ${label(name)} · ${modeStore.currentDecision().mode.title} · ${applied.engine} · ${profile.name} · ${applied.bands.size} bands${where(output)}",
                    isError = false
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Correction effect on session $session ($pkg) failed", e)
            closeSession(session)
            report("Could not correct ${label(pkg)}: ${e.message ?: e.javaClass.simpleName}", isError = true)
        }
    }

    private fun closeSession(session: Int) {
        val applied = sessionEqs.remove(session)?.applied ?: return
        releaseApplied(applied)
        syncRuntimeBands()
        publish() // what is being corrected may have just changed
    }

    private fun releaseSessions() {
        for (s in sessionEqs.keys.toList()) closeSession(s)
    }

    /** Prefer DP on API 28+, and fall back to Equalizer on any construction/configuration refusal. */
    private fun createBestEffect(session: Int, profile: Profile): AppliedEffect {
        var dpFailure: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val dp = DynamicsProcessingEngine.create(session, profile)
                return AppliedEffect(
                    effect = dp.effect,
                    engine = ENGINE_DYNAMICS_PROCESSING,
                    bands = dp.bandCentresHz.zip(dp.bandGainsDb).map { (hz, db) ->
                        PlatformBand(hz, (db * 100.0).roundToInt())
                    }
                )
            } catch (e: Exception) {
                dpFailure = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "DynamicsProcessing refused on session $session; trying Equalizer", e)
            }
        } else {
            dpFailure = "DynamicsProcessing requires API 28"
        }

        var eq: Equalizer? = null
        try {
            eq = Equalizer(PRIORITY, session)
            return configureEqualizer(eq, profile)
        } catch (e: Exception) {
            try {
                eq?.release()
            } catch (_: Exception) {
                // Preserve the Equalizer refusal below.
            }
            throw IllegalStateException(
                "DynamicsProcessing refused ($dpFailure); Equalizer refused (${e.message ?: e.javaClass.simpleName})",
                e
            )
        }
    }

    /** Reconfigure a held effect; replace it with the preferred path if it no longer accepts control. */
    private fun configureOrReplace(current: AppliedEffect, session: Int, profile: Profile): AppliedEffect =
        try {
            configure(current, profile)
        } catch (e: Exception) {
            Log.w(TAG, "${current.engine} reconfiguration failed on session $session; replacing the effect", e)
            releaseApplied(current)
            createBestEffect(session, profile)
        }

    // Equalizer is checked first, and DynamicsProcessing only behind the SDK
    // check: on API 26–27 the class does not exist, and even an `is` test
    // against it throws NoClassDefFoundError on every re-apply.
    private fun configure(current: AppliedEffect, profile: Profile): AppliedEffect {
        val effect = current.effect
        return when {
            effect is Equalizer -> configureEqualizer(effect, profile)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> configureDynamicsProcessing(effect, profile)
            else -> throw IllegalArgumentException("Unknown audio effect ${effect.javaClass.simpleName}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun configureDynamicsProcessing(effect: AudioEffect, profile: Profile): AppliedEffect {
        require(effect is DynamicsProcessing) { "Unknown audio effect ${effect.javaClass.simpleName}" }
        val dp = DynamicsProcessingEngine.configure(effect, profile)
        return AppliedEffect(
            effect = effect,
            engine = ENGINE_DYNAMICS_PROCESSING,
            bands = dp.bandCentresHz.zip(dp.bandGainsDb).map { (hz, db) ->
                PlatformBand(hz, (db * 100.0).roundToInt())
            }
        )
    }

    /** Samples the profile at the effect's actual bands and verifies applied EQ levels. */
    private fun configureEqualizer(eq: Equalizer, profile: Profile): AppliedEffect {
        val n = eq.numberOfBands.toInt()
        check(n > 0) { "Equalizer reports no bands" }
        val range = eq.bandLevelRange
        check(range != null && range.size >= 2) { "Equalizer reports no gain range" }
        val centres = List(n) { eq.getCenterFreq(it.toShort()) / 1000.0 }
        val mbs = BandMapping.millibels(
            BandMapping.gainsDb(profile, centres),
            range[0].toInt()..range[1].toInt()
        )
        for (i in 0 until n) eq.setBandLevel(i.toShort(), mbs[i])
        eq.enabled = true
        check(eq.hasControl()) { "another equaliser app has priority over this audio" }
        val actual = List(n) { index ->
            val millibels = eq.getBandLevel(index.toShort()).toInt()
            PlatformBand(centres[index], millibels)
        }
        return AppliedEffect(eq, ENGINE_PLATFORM_EQUALIZER, actual)
    }

    private fun releaseApplied(applied: AppliedEffect) {
        try {
            applied.effect.setEnabled(false)
        } catch (e: Exception) {
            Log.w(TAG, "Could not disable ${applied.engine} during teardown", e)
        }
        try {
            applied.effect.release()
        } catch (e: Exception) {
            Log.w(TAG, "Could not release ${applied.engine} during teardown", e)
        }
    }

    /** The Home band rail follows the layout of the effect that is actually live. */
    private fun syncRuntimeBands() {
        val representative = globalEffect ?: sessionEqs.values.firstOrNull()?.applied
        if (representative == null) store.clearRuntimeBands()
        else store.setRuntimeBands(representative.bands)
    }

    private fun setAllEnabled(on: Boolean) {
        val all = listOfNotNull(globalEffect) + sessionEqs.values.map { it.applied }
        for (applied in all) {
            try {
                check(applied.effect.setEnabled(on) == AudioEffect.SUCCESS) {
                    "${applied.engine} refused enable=$on"
                }
            } catch (e: Exception) {
                Log.w(TAG, "${applied.engine} no longer valid", e)
            }
        }
    }

    /**
     * Debounced re-discovery (plan §4: `dumpsys` is a heavy IPC). Playback
     * changes coalesce into one run; measurement never competes with it.
     */
    private fun scheduleDiscovery(delayMs: Long = DISCOVERY_DEBOUNCE_MS) {
        if (suspended) return
        if (!DumpsysDiscovery.hasGrant(this)) return
        mainHandler.removeCallbacks(discoveryRunnable)
        mainHandler.postDelayed(discoveryRunnable, delayMs)
    }

    /**
     * Uses DUMP both as an optional active-app signal for mode selection and,
     * when the output-mix path is unavailable, as a per-session attach fallback.
     * Announced sessions remain owned by their open/close broadcasts.
     */
    private fun applyDiscovered(found: List<DiscoveredSession>) {
        if (!running || suspended) return
        val previousMode = modeStore.currentDecision().mode
        val foundPackages = linkedSetOf<String>()
        for (session in found) {
            val uid = session.uid ?: continue
            packageManager.getPackagesForUid(uid)
                ?.filterNot { it == packageName }
                ?.forEach { foundPackages += it }
        }
        discoveredPackages = foundPackages
        val decision = modeStore.resolve(activeAppPackages())

        if (globalEffect != null) {
            if (decision.mode != previousMode) applyAll()
            return
        }
        if (decision.mode != previousMode && sessionEqs.isNotEmpty()) applyAll()

        // The same output-aware pick as applyAll: an output nothing was
        // measured on gets no discovered sessions attached, not another
        // output's curve. An output change re-runs applyAll.
        val (profile, output, switched) = resolve()
        if (profile == null) return
        val via = if (switched) " (this output's own profile)" else ""
        var changed = false
        var failed: String? = null
        for (session in found) {
            if (sessionEqs.containsKey(session.sessionId)) continue
            val uid = session.uid ?: continue
            val packages = packageManager.getPackagesForUid(uid)?.filterNot { it == packageName }.orEmpty()
            val pkg = packages.firstOrNull() ?: continue
            try {
                val applied = createBestEffect(session.sessionId, profile)
                sessionEqs[session.sessionId] = Held(applied, pkg, discovered = true)
                changed = true
            } catch (e: Exception) {
                Log.w(TAG, "Correction effect on discovered session ${session.sessionId} failed", e)
                failed = "Could not attach to ${label(pkg)} (DUMP discovery): ${e.message ?: e.javaClass.simpleName}"
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
        if (changed) syncRuntimeBands()
        if (failed != null) {
            report(failed, isError = true)
        } else if (changed) {
            if (sessionEqs.isEmpty()) report(waitingStatus(), isError = false)
            else report(sessionReport(profile, via, output), isError = false)
        }
    }

    /** The status line for whatever is attached right now, with its provenance. */
    private fun sessionReport(profile: Profile?, via: String = "", output: OutputRoute.Output? = null): String {
        val held = sessionEqs.values.sortedBy { it.discovered }
        if (held.isEmpty()) return waitingStatus()
        val names = held.joinToString { label(it.pkg) }
        val engines = held.map { it.applied.engine }.distinct().joinToString(" + ")
        val marker = if (held.any { it.discovered }) " · found by DUMP discovery" else ""
        return "Effect attached to $names · ${modeStore.currentDecision().mode.title} · $engines · ${profile?.name ?: "the active profile"}$via$marker${where(output)}"
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
     * "▶ Playing ·" marker is added while the whole-TV correction is on and
     * something is audible right now (see the class note for why only then).
     * Unchanged reports publish nothing, so a playback
     * callback on every focus change cannot spam the notification.
     */
    private fun publish(configs: List<AudioPlaybackConfiguration>? = null) {
        if (!running || messageBody.isEmpty()) return
        val playingNow = !suspended && !outputPaused && !messageError && globalEffect != null && audioIsPlaying(configs)
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
        getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(deviceCallback)
        releaseSessions()
        globalEffect?.let(::releaseApplied)
        globalEffect = null
        store.clearRuntimeBands()
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
        val channel = NotificationChannel(CHANNEL_ID, "Audio correction", NotificationManager.IMPORTANCE_LOW)
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
        private const val ENGINE_DYNAMICS_PROCESSING = "DynamicsProcessing"
        private const val ENGINE_PLATFORM_EQUALIZER = "Equalizer"

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
            if (store.getActiveProfile() == null) return "Measure this room or open Manual EQ to create a profile before applying correction."
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
