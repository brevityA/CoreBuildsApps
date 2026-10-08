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
import android.database.ContentObserver
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
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import tv.corebuilds.eq.MainActivity
import tv.corebuilds.eq.R
import tv.corebuilds.eq.export.PlatformBand
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.mode.ContentModeStore
import tv.corebuilds.eq.mode.ContentType
import tv.corebuilds.eq.mode.ContentTypePrefs
import tv.corebuilds.eq.mode.ContentTypeRegistry
import tv.corebuilds.eq.ui.EnhancedAudioPrefs
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Applies the active profile and keeps it applied.
 *
 * Started only from Core EQ's own screens (Android 8+ forbids starting it from
 * the background), it registers for player session broadcasts itself. These
 * and public playback callbacks are rescan triggers only: broadcast-provided
 * session IDs and package names never establish player identity or ownership.
 *
 * Correction follows the output ([OutputRoute]): each profile belongs to the
 * chain it was measured on, the device callback re-picks when a soundbar or
 * headphones come and go, and an output nothing was measured on gets no
 * correction rather than another chain's.
 *
 * Two correction scopes, never both at once (that would correct twice):
 * - **Output mix** – an effect on session 0, where firmware accepts it. The
 *   effect's state does not prove every app or route traverses that mix.
 * - **Per session** – DUMP-discovered media/game sessions, only where the user
 *   granted the one-time [DumpsysDiscovery.DUMP_PERMISSION]. Raw broadcast IDs
 *   are not used as proof of session ownership; open/close events trigger a
 *   fresh scan instead. A discovered session is released when it disappears
 *   from a later dump.
 *
 * On API 28+ the service tries a DynamicsProcessing PreEQ with its limiter
 *   enabled, then falls back to the platform Equalizer on any refusal. Both
 *   paths lower gains by the largest boost; neither applies positive EQ gain.
 * Every outcome, good or bad, is written to [ProfileStore.setStatus] and the
 * notification, so a failure is always named somewhere the user can see.
 *
 * When the platform reports media playback while the output-mix effect is
 * configured, the Home indicator and notification show a playback hint. The
 * callback does not identify the app or prove its route passes through the
 * effect, so the wording explicitly says coverage is unknown. On the
 * per-session path the callback cannot be correlated with a held session, so
 * there is no playback badge there.
 */
class EqService : Service() {

    private data class AppliedEffect(
        val effect: AudioEffect,
        val engine: String,
        val bands: List<PlatformBand>,
        /** Opt-in BassBoost/LoudnessEnhancer on the same session; created and released with [effect]. */
        val extras: ExtraEffects? = null
    )

    /** An effect held on one session, and where that session came from. */
    private data class Held(val applied: AppliedEffect, val pkg: String, val discovered: Boolean)

    private lateinit var store: ProfileStore
    private lateinit var modeStore: ContentModeStore
    
    // The Extra effects screen's switches (all off by default since 1.2.1).
    private var enhancedPrefs: EnhancedAudioPrefs? = null
    /** Dialogue boost and Low-volume bass as last applied; recomputed by [applyAll]. */
    private var activeLayers = ToneLayers.NONE
    /** Decides when the "profile applied" toast shows: on a change, not on every reapply. */
    private val appliedNotice = AppliedNotice()
    private val appliedCard by lazy { AppliedCard(this) }
    
    private val sessionEqs = mutableMapOf<Int, Held>()
    /** Only unambiguous DUMP UID-to-package matches may influence app-mode rules. */
    private var discoveredPackages: Set<String> = emptySet()
    private var discoveredHasUnknownPlayer = false
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
    // it. Playback callbacks and session broadcasts are coalesced; untrusted
    // broadcasts cannot queue an unbounded stream of dump processes.
    private val mainHandler = Handler(Looper.getMainLooper())

    // Low-volume bass follows the media volume. Settings.System carries the
    // persisted volumes; VOLUME_CHANGED_ACTION arrives sooner on most TVs but
    // is not public API, so both only prompt a re-check of the lift.
    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = onVolumeChanged()
    }
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onVolumeChanged()
    }
    private val discoveryExecutor = Executors.newSingleThreadExecutor()
    private var discoveryQueued = false
    private var discoveryInFlight = false
    private var discoveryAgain = false
    /** Do not expire a temporary override against an empty pre-scan snapshot. */
    private var discoveryInitialized = false
    /** Whether the last discovery result was complete enough to prove absences. */
    private var discoverySnapshotComplete = false
    private val discoveryRunnable = Runnable {
        discoveryQueued = false
        if (discoveryInFlight) {
            discoveryAgain = true
        } else {
            discoveryInFlight = true
            discoveryExecutor.execute {
                val snapshot = try {
                    DumpsysDiscovery.discover(android.os.Process.myUid())
                } catch (e: Exception) {
                    Log.w(TAG, "DUMP discovery failed; retaining the last known player set", e)
                    DiscoverySnapshot(emptyList(), hasUnidentifiedPlayer = false, complete = false)
                }
                mainHandler.post {
                    applyDiscovered(snapshot)
                    discoveryInFlight = false
                    if (discoveryAgain) {
                        discoveryAgain = false
                        scheduleDiscovery()
                    }
                }
            }
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
            if (intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION ||
                intent.action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
            ) {
                // Extras are deliberately ignored. The event only asks the
                // optional DUMP path to refresh its independently discovered snapshot.
                scheduleDiscovery()
            }
        }
    }

    /** Plugging in a soundbar or headphones changes which profile is right. */
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onOutputsChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onOutputsChanged()
    }
    private var lastOutput: OutputRoute.Output? = null

    /** Reprogram only when the lift actually moves a step; holding the volume key costs a few reapplies. */
    private fun onVolumeChanged() {
        if (!running || suspended || enhancedPrefs?.lowVolumeBassEnabled != true) return
        if (toneLayers() != activeLayers) applyAll()
    }

    private fun onOutputsChanged() {
        val output = OutputRoute.current(this)
        if (output == lastOutput) return
        lastOutput = output
        applyAll()
        if (DumpsysDiscovery.hasGrant(this)) scheduleDiscovery(0L)
    }

    override fun onCreate() {
        super.onCreate()
        store = ProfileStore(this)
        modeStore = ContentModeStore(this)
        
        enhancedPrefs = EnhancedAudioPrefs(this)
        
        // Keep the persisted last-known set until a complete dump proves it
        // changed; service recreation is not evidence that playback stopped.
        discoveredPackages = modeStore.lastActivePackages()
        discoveredHasUnknownPlayer = modeStore.lastActivePlayerUnknown()
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
        // Playback callbacks provide a coarse activity hint, not app identity,
        // audibility, route or effect coverage. A refusal only hides the pulse;
        // the correction status itself remains available.
        try {
            audioManager.registerAudioPlaybackCallback(playbackCallback, null)
        } catch (e: Exception) {
            Log.w(TAG, "Playback watcher refused; the playing marker is off", e)
        }
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
        ContextCompat.registerReceiver(
            this, volumeReceiver, IntentFilter(VOLUME_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED
        )
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
                if (DumpsysDiscovery.hasGrant(this)) scheduleDiscovery(0L)
            }
            ACTION_RELOAD_PREFS -> {
                reloadEnhancedPreferences()
            }
            else -> applyAll() // ACTION_START, ACTION_REAPPLY, or a sticky restart
        }
        return START_STICKY
    }

    /** DUMP-discovered UID mappings are the only package identities used for automatic modes. */
    private fun activeAppPackages(): Set<String> = discoveredPackages
        .filter { it.isNotBlank() && it != packageName }
        .toSet()

    /** The route-matched room base plus its current content-mode overlay. */
    private fun resolve(): Triple<Profile?, OutputRoute.Output?, Boolean> {
        val output = OutputRoute.current(this)
        val pick = OutputRoute.pick(
            store.getAllProfiles(),
            store.chosenId(),
            output?.kind,
            output?.name
        )
        val mode = when {
            !DumpsysDiscovery.hasGrant(this) ->
                modeStore.resolve(emptySet(), advanceTemporaryOverride = false).mode
            !discoveryInitialized -> modeStore.currentDecision().mode
            else -> modeStore.resolve(
                activeAppPackages(),
                discoveredHasUnknownPlayer,
                advanceTemporaryOverride = discoverySnapshotComplete
            ).mode
        }
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
        activeLayers = toneLayers()
        if (store.getAllProfiles().isEmpty()) {
            report("No measurement yet. Measure this room to create a correction.", isError = true)
            return
        }
        val (profile, output, switched) = resolve()
        if (profile == null) {
            // Another chain's curve would be wrong here: step aside, say why.
            outputPaused = true
            setAllEnabled(false)
            announcePaused(output)
            val routeName = output?.name ?: "the output Android could not identify"
            report(
                "Nothing measured on $routeName yet, so correction is paused there. " +
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
                    "Output-mix effect configured · $modeName · ${configured.engine} · ${profile.name}$via · ${configured.bands.size} bands${extrasNote(listOf(configured), profile)} · coverage depends on TV routing${where(output)}",
                    isError = false
                )
                announceApplied(profile, modeName, listOf(configured), output)
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
            sessionEqs.isNotEmpty() -> {
                report(sessionReport(profile, via, output), isError = false)
                announceApplied(profile, modeName, sessionEqs.values.map { it.applied }, output)
            }
            else -> {
                appliedNotice.reset()
                if (DumpsysDiscovery.hasGrant(this)) scheduleDiscovery(0L)
                report(waitingStatus(), isError = false)
            }
        }
    }

    /** What to say when nothing is attached: the way forward, named per grant. */
    private fun waitingStatus(): String =
        if (DumpsysDiscovery.hasGrant(this)) {
            "No active media/game session with a usable UID is visible yet. DUMP discovery is checking. " +
                "The output-mix effect path was unavailable ($globalError); dump visibility and effect support vary by player and TV."
        } else {
            "No output-mix effect could be configured ($globalError). Per-session correction and automatic app switching need optional DUMP discovery. " +
                "Android documents DUMP as unavailable to third-party apps, so this grant may be refused on your TV. " +
                "Try the Capability-screen command if supported, or export the profile for the TV's own sound settings."
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

    /** Correction on [session], plus that session's opt-in extras. */
    private fun createBestEffect(session: Int, profile: Profile): AppliedEffect =
        createCorrection(session, profile).let { it.copy(extras = attachExtras(session)) }

    /** Prefer DP on API 28+, and fall back to Equalizer on any construction/configuration refusal. */
    private fun createCorrection(session: Int, profile: Profile): AppliedEffect {
        var dpFailure: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val dp = DynamicsProcessingEngine.create(session, profile, limiter(), activeLayers)
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
                "DynamicsProcessing ($dpFailure) and Equalizer (${e.message ?: e.javaClass.simpleName}) both refused",
                e
            )
        }
    }

    /** Reconfigure a held effect; replace it with the preferred path if it no longer accepts control. */
    private fun configureOrReplace(current: AppliedEffect, session: Int, profile: Profile): AppliedEffect =
        try {
            configure(current, profile).copy(extras = current.extras?.also { it.setEnabled(true) })
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
        val dp = DynamicsProcessingEngine.configure(effect, profile, limiter(), activeLayers)
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
            BandMapping.gainsDb(profile, centres, activeLayers),
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
        applied.extras?.release()
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
            applied.extras?.setEnabled(on)
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
     * Debounced re-discovery (`dumpsys` is a heavy IPC). Playback changes
     * coalesce into one run; during a scan, at most one follow-up is retained.
     */
    private fun scheduleDiscovery(delayMs: Long = DISCOVERY_DEBOUNCE_MS) {
        if (!running || suspended || !DumpsysDiscovery.hasGrant(this)) return
        if (discoveryInFlight) {
            discoveryAgain = true
            return
        }
        if (discoveryQueued) return
        discoveryQueued = true
        mainHandler.postDelayed(discoveryRunnable, delayMs)
    }

    /**
     * Uses DUMP as the only source for session IDs and app identity. Package
     * visibility gaps and shared UIDs stay unidentified; their sessions may
     * still be corrected, but they cannot select a content-mode rule.
     */
    private fun applyDiscovered(snapshot: DiscoverySnapshot) {
        if (!running || suspended) return
        val previousDecision = modeStore.currentDecision()
        val foundPackages = linkedSetOf<String>()
        val packageBySession = mutableMapOf<Int, String?>()
        var hasUnknownPlayer = snapshot.hasUnidentifiedPlayer
        for (session in snapshot.sessions) {
            val uid = session.uid ?: continue
            val packages = try {
                packageManager.getPackagesForUid(uid)
            } catch (e: Exception) {
                Log.w(TAG, "Could not map discovered UID $uid to a package", e)
                null
            }
            val pkg = DiscoveredPackageResolver.uniquePackage(packages, packageName)
            packageBySession[session.sessionId] = pkg
            if (pkg == null) hasUnknownPlayer = true else foundPackages += pkg
        }

        // A complete pair of recognized dumps is the only trusted snapshot:
        // it may remove players and expire a temporary override. An incomplete
        // scan may add newly seen players, but cannot erase state or expire one.
        val positiveEvidence = snapshot.sessions.isNotEmpty() || hasUnknownPlayer
        if (snapshot.complete) {
            discoveredPackages = foundPackages
            discoveredHasUnknownPlayer = hasUnknownPlayer
            discoveryInitialized = true
            discoverySnapshotComplete = true
        } else {
            discoveredPackages = discoveredPackages + foundPackages
            discoveredHasUnknownPlayer = discoveredHasUnknownPlayer || hasUnknownPlayer
            discoverySnapshotComplete = false
            if (positiveEvidence) discoveryInitialized = true
        }
        val decision = if (!snapshot.complete && !positiveEvidence && !discoveryInitialized) {
            // Before the first usable scan, an incomplete/empty result must not
            // replace the persisted mode with the automatic empty-set default.
            // Keep that mode/player state but mark it untrusted for override expiry.
            modeStore.resolve(
                previousDecision.activePackages,
                previousDecision.hasUnidentifiedPlayer,
                advanceTemporaryOverride = false
            )
            previousDecision
        } else {
            modeStore.resolve(
                activeAppPackages(),
                discoveredHasUnknownPlayer,
                advanceTemporaryOverride = snapshot.complete
            )
        }
        val playerSnapshotChanged = decision.activePackages != previousDecision.activePackages ||
            decision.hasUnidentifiedPlayer != previousDecision.hasUnidentifiedPlayer

        if (globalEffect != null) {
            if (decision.mode != previousDecision.mode) applyAll()
            else if (playerSnapshotChanged) notifyModeSnapshotChanged()
            return
        }
        if (decision.mode != previousDecision.mode && sessionEqs.isNotEmpty()) applyAll()

        // Prune only against a complete snapshot. A failed command, permission
        // refusal, or unrecognized firmware format is not proof a player ended.
        var changed = false
        if (snapshot.complete) {
            val present = snapshot.sessions.mapTo(mutableSetOf()) { it.sessionId }
            for (session in sessionEqs.keys.toList()) {
                val held = sessionEqs[session] ?: continue
                if (held.discovered && session !in present) {
                    closeSession(session)
                    changed = true
                }
            }
        }

        // The same output-aware pick as applyAll: an output nothing was
        // measured on gets no discovered sessions attached, not another
        // output's curve. An output change re-runs applyAll.
        val (profile, output, switched) = resolve()
        if (profile == null) return
        val via = if (switched) " (this output's own profile)" else ""
        var failed: String? = null
        for (session in snapshot.sessions) {
            if (sessionEqs.containsKey(session.sessionId)) continue
            if (session.uid == null) continue // attribution is required to hold an effect
            val pkg = packageBySession[session.sessionId].orEmpty()
            try {
                val applied = createBestEffect(session.sessionId, profile)
                sessionEqs[session.sessionId] = Held(applied, pkg, discovered = true)
                changed = true
            } catch (e: Exception) {
                Log.w(TAG, "Correction effect on discovered session ${session.sessionId} failed", e)
                failed = "Could not attach to ${label(pkg)} (DUMP discovery): ${e.message ?: e.javaClass.simpleName}"
            }
        }
        if (changed) syncRuntimeBands()
        if (failed != null) {
            report(failed, isError = true)
        } else if (changed) {
            if (sessionEqs.isEmpty()) {
                appliedNotice.reset()
                report(waitingStatus(), isError = false)
            } else {
                report(sessionReport(profile, via, output), isError = false)
                announceApplied(profile, modeStore.currentDecision().mode.title, sessionEqs.values.map { it.applied }, output)
            }
        } else if (playerSnapshotChanged) {
            notifyModeSnapshotChanged()
        }
    }

    private fun notifyModeSnapshotChanged() {
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
    }

    /** The status line for whatever is attached right now, with its provenance. */
    private fun sessionReport(profile: Profile?, via: String = "", output: OutputRoute.Output? = null): String {
        val held = sessionEqs.values.sortedBy { it.discovered }
        if (held.isEmpty()) return waitingStatus()
        val names = held.joinToString { label(it.pkg) }
        val engines = held.map { it.applied.engine }.distinct().joinToString(" + ")
        val marker = if (held.any { it.discovered }) " · found by DUMP discovery" else ""
        return "Effect attached to $names · ${modeStore.currentDecision().mode.title} · $engines · ${profile?.name ?: "the active profile"}$via${extrasNote(held.map { it.applied }, profile)}$marker${where(output)}"
    }

    /** What the Extra effects screen added on [applied], one entry per effect or layer. */
    private fun extrasWords(applied: List<AppliedEffect>, profile: Profile?): List<String> =
        (applied.mapNotNull { it.extras?.label?.takeIf(String::isNotEmpty) } +
            listOfNotNull(profile?.let { activeLayers.label(it) }?.takeIf(String::isNotEmpty)))
            .flatMap { it.split(" + ") }
            .distinct()

    /** Say what the Extra effects screen added, and when night mode cannot run. */
    private fun extrasNote(applied: List<AppliedEffect>, profile: Profile?): String {
        val extras = extrasWords(applied, profile)
        val night = enhancedPrefs?.nightModeEnabled == true
        val nightOnDp = applied.any { it.engine == ENGINE_DYNAMICS_PROCESSING }
        return buildString {
            if (extras.isNotEmpty()) append(" · extras: ").append(extras.joinToString(" / "))
            if (night) append(if (nightOnDp) " · night mode" else " · night mode needs DynamicsProcessing, not active")
        }
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
     * "▶ Playing ·" marker is added while the output-mix effect is configured
     * and Android reports media playback, not as proof of end-to-end coverage.
     * Unchanged reports publish nothing, so playback callbacks cannot spam
     * the notification on every focus change.
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
     * True when Android reports an active media-like playback configuration.
     *
     * [AudioManager.getActivePlaybackConfigurations] returns the platform's
     * sanitized active-player list — or [configs], the list
     * [playbackCallback] just delivered — and the callback re-runs this on
     * every change. Only media traffic counts; this does not prove the stream
     * is audible, identify its app, reveal its route, or show that it traverses
     * the configured effect. The measurement sweep is excluded by [suspended].
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

    /**
     * The Extra effects screen saved: swap every held effect's extras for the
     * new plan, then reapply so the DP limiter picks up night mode. Correction
     * effects themselves stay attached; nothing is torn down and rediscovered.
     */
    private fun reloadEnhancedPreferences() {
        globalEffect = globalEffect?.let { refreshExtras(0, it) }
        for ((session, held) in sessionEqs.toMap()) {
            sessionEqs[session] = held.copy(applied = refreshExtras(session, held.applied))
        }
        applyAll()
    }

    private fun refreshExtras(session: Int, applied: AppliedEffect): AppliedEffect {
        applied.extras?.release()
        return applied.copy(extras = attachExtras(session))
    }

    private fun attachExtras(session: Int): ExtraEffects? {
        val prefs = enhancedPrefs ?: return null
        val plan = ExtraPlan.of(extrasContentType(), prefs.bassBoostEnabled, prefs.loudnessEnhancerEnabled)
        return ExtraEffects.attach(session, plan)
    }

    /** The Content type screen's choice: its manual pick, or the app detected playing. */
    private fun extrasContentType(): ContentType {
        val prefs = getSharedPreferences(ContentTypePrefs.PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.getBoolean(ContentTypePrefs.KEY_AUTO_SWITCH, true)) {
            ContentTypeRegistry.resolve(activeAppPackages()) ?: ContentType.GENERAL
        } else {
            ContentType.fromKey(prefs.getString(ContentTypePrefs.KEY_MANUAL_TYPE, null))
        }
    }

    /**
     * The on-screen card when a different profile or mode takes effect
     * (1.3.0): correction on, the profile, and how it is applied, for a few
     * seconds over whatever is playing ([AppliedCard]). Without "Display over
     * other apps" it falls back to a text toast, the one on-screen surface a
     * background app keeps on Android 11+ (Android TV shows no heads-up
     * notifications). Plain reapplies stay silent ([AppliedNotice]). Switch it
     * off on Profiles.
     */
    private fun announceApplied(profile: Profile, modeName: String, applied: List<AppliedEffect>, output: OutputRoute.Output?) {
        if (!appliedNotice.onApplied(profile.id, modeName)) return
        if (!store.announceApplied) return
        val detail = listOfNotNull(
            modeName,
            output?.let { OutputRoute.label(it.kind) },
            engineWords(applied)
        ).joinToString(" · ")
        val night = enhancedPrefs?.nightModeEnabled == true && applied.any { it.engine == ENGINE_DYNAMICS_PROCESSING }
        val extras = (extrasWords(applied, profile) + listOfNotNull("night mode".takeIf { night }))
            .joinToString(" · ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
            .ifEmpty { null }
        val shown = appliedCard.show(AppliedCard.Content(active = true, title = profile.name, detail = detail, extras = extras))
        if (!shown) {
            Toast.makeText(this, getString(R.string.notice_profile_applied, profile.name, modeName), Toast.LENGTH_SHORT).show()
        }
    }

    /** Once per output without a profile: say on screen that correction stepped aside there. */
    private fun announcePaused(output: OutputRoute.Output?) {
        if (!appliedNotice.onPaused(output?.let { "${it.kind}|${it.name}" } ?: OutputRoute.UNKNOWN)) return
        if (!store.announceApplied) return
        val label = OutputRoute.label(output?.kind ?: OutputRoute.UNKNOWN)
        val shown = appliedCard.show(
            AppliedCard.Content(
                active = false,
                title = getString(R.string.card_paused_title),
                detail = getString(R.string.card_paused_detail, label),
                extras = null
            )
        )
        if (!shown) Toast.makeText(this, getString(R.string.notice_paused, label), Toast.LENGTH_SHORT).show()
    }

    /** "32 bands" on DynamicsProcessing, "5-band fallback" when any session fell back to the Equalizer. */
    private fun engineWords(applied: List<AppliedEffect>): String? {
        val fallback = applied.firstOrNull { it.engine == ENGINE_PLATFORM_EQUALIZER }
        if (fallback != null) return getString(R.string.card_engine_eq, fallback.bands.size)
        val dp = applied.firstOrNull() ?: return null
        return resources.getQuantityString(R.plurals.card_engine_dp, dp.bands.size, dp.bands.size)
    }

    /** Protection-only, or night mode's tighter limiter. DP correction only. */
    private fun limiter(): LimiterSettings = enhancedPrefs?.limiter() ?: LimiterSettings()

    /**
     * Dialogue boost and Low-volume bass for this moment. The lift is measured
     * from the current output's own reference volume, recorded the first time
     * that output reports one ([LowVolumeBass]); a volume Android does not
     * report (fixed-volume outputs) adds nothing.
     */
    private fun toneLayers(): ToneLayers {
        val prefs = enhancedPrefs ?: return ToneLayers.NONE
        val bass = if (prefs.lowVolumeBassEnabled) LowVolumeBass.read(this, prefs).liftDb else 0.0
        return ToneLayers(dialogue = prefs.dialogueBoostEnabled, lowVolumeBassDb = bass)
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
        appliedCard.dismiss() // a card about correction must not outlive it
        discoveredPackages = emptySet()
        discoveredHasUnknownPlayer = false
        discoveryInitialized = false
        // Service teardown is not evidence that the detected player set
        // changed. Clear the visible snapshot without expiring a temporary
        // manual override; the next DUMP scan will compare against its anchor.
        modeStore.resolve(emptySet(), advanceTemporaryOverride = false)
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
        contentResolver.unregisterContentObserver(volumeObserver)
        try {
            unregisterReceiver(volumeReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Volume receiver was not registered", e)
        }
        mainHandler.removeCallbacksAndMessages(null)
        discoveryQueued = false
        discoveryAgain = false
        discoveryExecutor.shutdownNow()
        getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(deviceCallback)
        releaseSessions()
        globalEffect?.let(::releaseApplied)
        globalEffect = null
        
        enhancedPrefs = null
        
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
        const val ACTION_RELOAD_PREFS = "tv.corebuilds.eq.action.RELOAD_PREFS"
        /** AudioManager.VOLUME_CHANGED_ACTION: sent by the system on most builds, but hidden API. */
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
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
