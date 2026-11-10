package tv.corebuilds.eq.apply

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import tv.corebuilds.eq.export.Profile

/**
 * Which sound output the TV is playing through, and which profile belongs to it.
 *
 * A measurement is a measurement of one chain — the TV's speakers, or the
 * soundbar on HDMI ARC, or Bluetooth headphones — so its correction is only
 * right for that chain. Each profile is tagged with the output it was measured
 * on, and correction follows the measured chain: switch to the soundbar and
 * the soundbar's profile applies; switch to an output nothing was measured on
 * and correction steps aside instead of applying another chain's curve.
 *
 * Android does not expose another app's actual media destination. [current]
 * prefers the route reported for Core EQ's finite silent PCM probe. If a probe
 * cannot start, API 33+ anticipated media routing or a connected-output estimate
 * is retained and labelled; competing endpoints remain ambiguous. A probe that
 * starts but reports no route is UNCONFIRMED, not an estimate fallback.
 * Measurement-time sweep routing is evidence about that sweep track. Neither
 * track proves another app's path, acoustic coverage or downstream processing.
 */
object OutputRoute {

    fun evidenceDescription(evidence: Evidence): String = when (evidence) {
        Evidence.ANTICIPATED -> "anticipated media route"
        Evidence.CONNECTED_ESTIMATE -> "connected-output estimate"
        Evidence.TRACK_REPORTED -> "route reported for the sweep track"
        Evidence.TRACK_PROBE -> "route reported for the silent probe track"
        Evidence.AMBIGUOUS -> "ambiguous route"
    }

    /** Output kinds, stored in profiles. Stable strings: they are saved data. */
    const val SPEAKER = "speaker"
    const val HDMI_ARC = "hdmi_arc"
    const val HDMI = "hdmi"
    const val BLUETOOTH = "bluetooth"
    const val USB = "usb"
    const val WIRED = "wired"
    /**
     * A measurement made where Android named no output Core EQ recognises.
     * Unlike a profile from before outputs were recorded (null, applies
     * everywhere), it applies only while the output is still unknown, and
     * steps aside once Android reports a real one.
     */
    const val UNKNOWN = "unknown"
    /** More than one plausible output was reported; no single-output profile is safe. */
    const val AMBIGUOUS = "ambiguous"
    /**
     * A silent probe track played and Android still named no routed device.
     * Unlike UNKNOWN (a real, unrecognized device), this is not a route at
     * all — correction pauses on it, and no profile (legacy included) may be
     * applied through it.
     */
    const val UNCONFIRMED = "unconfirmed"

    enum class Evidence { ANTICIPATED, CONNECTED_ESTIMATE, TRACK_REPORTED, TRACK_PROBE, AMBIGUOUS }
    data class Output(
        val kind: String,
        val name: String,
        val evidence: Evidence = Evidence.CONNECTED_ESTIMATE
    )

    /** The label a person reads: "TV speakers", "HDMI ARC (soundbar)". */
    fun label(kind: String?): String = when (kind) {
        SPEAKER -> "TV speakers"
        HDMI_ARC -> "HDMI ARC (soundbar or receiver)"
        HDMI -> "HDMI"
        BLUETOOTH -> "Bluetooth"
        USB -> "USB audio"
        WIRED -> "Wired headphones or line out"
        UNKNOWN -> "an output Android did not name"
        AMBIGUOUS -> "multiple or ambiguous audio outputs"
        UNCONFIRMED -> "an output route Android could not confirm"
        null -> "any output"
        else -> kind
    }

    /** One AudioDeviceInfo type to a kind, or null for outputs that are not a listening chain. */
    fun kindOf(type: Int): String? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> SPEAKER
        AudioDeviceInfo.TYPE_HDMI_ARC, TYPE_HDMI_EARC -> HDMI_ARC
        AudioDeviceInfo.TYPE_HDMI -> HDMI
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER -> BLUETOOTH
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> USB
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> WIRED
        else -> null // earpiece, telephony, bus, remote submix, …
    }

    private val PRIORITY = listOf(BLUETOOTH, USB, WIRED, HDMI_ARC, HDMI, SPEAKER)

    /** The kind media most likely plays through, given the connected output types. */
    fun pickKind(types: List<Int>): String? {
        val kinds = types.mapNotNull(::kindOf).toSet()
        return PRIORITY.firstOrNull { it in kinds }
    }

    /**
     * The result of probing the route with a silent playback track: the route
     * Android actually reported for a playing [AudioTrack], plus — on API 33+
     * — whether that route supports direct playback of the format correction
     * would be applied to (a passthrough bitstream path strips any effect, so
     * correction must not be assumed to reach it).
     */
    data class ProbeResult(
        val output: Output,
        /** API 33+: true when this PCM format can be direct-played, i.e. the route may bypass the mixer and strip an on-device effect. Null below API 33. */
        val directPlaybackSupported: Boolean?,
        /**
         * The route hint ([identity] of [indication]) read as the probe started
         * (1.13.2). The answer stands while the hint still reads the same; see
         * [RouteTrust].
         */
        val hint: Pair<String, String>? = null
    )

    /**
     * Google's documented pattern for asking where audio actually goes: start
     * a silent PCM [AudioTrack] and read [AudioTrack.getRoutedDevice] while it
     * is playing. This reports this probe track's route, not another app's
     * destination, effect coverage or downstream processing. The track is
     * released in `finally` no matter what.
     *
     * On API 24+ an [AudioTrack.OnRoutingChangedListener] watches for the route
     * changing mid-probe (HDMI hot-plug, Bluetooth connect); on API 33+
     * [AudioManager.getDirectPlaybackSupport] reports whether the probed route
     * takes the PCM format directly or is a passthrough path.
     *
     * Returns null when no track could be opened at all (then the caller falls
     * back to the connected-output estimate). A track that played but reported
     * no routed device yields [UNCONFIRMED] — a hard stop, never a silent
     * fallback to "Everyday".
     */
    fun probeWithSilentTrack(context: Context): ProbeResult? {
        context.getSystemService(AudioManager::class.java) ?: return null
        // Read before the track opens: the hint this answer will stand on.
        val hint = identity(indication(context))
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(PROBE_SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(mediaAttributes())
                .setAudioFormat(format)
                .setBufferSizeInBytes(PROBE_BUFFER_BYTES)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (_: Exception) {
            return null
        }

        val observed = ProbeRouteObservation<Int>()
        val listener = AudioTrack.OnRoutingChangedListener {
            observed.observe(readRoutedDevices(track).map { it.id })
        }
        try {
            track.addOnRoutingChangedListener(listener, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
            // The bounded polls below still observe reported endpoints.
        }

        var playbackStarted = false
        ownProbe.started() // ended in `finally`, after the track is released
        return try {
            // play() on an empty streaming track does not establish a usable
            // playback route. Queue real PCM zeros, without focus or volume
            // changes. A finite static buffer cannot run away or underrun
            // halfway through the settling window.
            val silence = ByteArray(PROBE_BUFFER_BYTES)
            if (track.write(silence, 0, silence.size) != silence.size) return null
            track.play()
            playbackStarted = true
            RuntimeCounters.routeProbe()
            val deadline = SystemClock.elapsedRealtime() + PROBE_SETTLE_MS
            var devices: List<AudioDeviceInfo>
            while (true) {
                devices = readRoutedDevices(track)
                observed.observe(devices.map { it.id })
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) break
                Thread.sleep(minOf(PROBE_POLL_MS, remaining))
            }
            val output = if (devices.isEmpty()) {
                unconfirmedOutput()
            } else {
                resolveCandidates(devices.map { device ->
                    val kind = kindOf(device.type) ?: UNKNOWN
                    Output(
                        kind,
                        if (kind == UNKNOWN) label(UNKNOWN)
                        else displayName(kind, device.productName?.toString()),
                        Evidence.TRACK_PROBE
                    )
                }) ?: unconfirmedOutput()
            }
            // Android sends an initial "routed" callback when playback starts.
            // That is normal. Only observing different endpoint identities
            // makes this probe ambiguous, including A -> B -> A.
            val confirmed = if (observed.changed() && output.kind != UNCONFIRMED) {
                Output(AMBIGUOUS, label(AMBIGUOUS), Evidence.AMBIGUOUS)
            } else output
            ProbeResult(confirmed, directPlaybackSupport(format), hint)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            if (playbackStarted) ProbeResult(unconfirmedOutput(), null) else null
        } catch (e: RuntimeException) {
            Log.w("OutputRoute", "Silent route probe failed", e)
            if (playbackStarted) ProbeResult(unconfirmedOutput(), null) else null
        } finally {
            try { track.removeOnRoutingChangedListener(listener) } catch (_: Exception) { }
            try { track.stop() } catch (_: Exception) { }
            try { track.release() } catch (_: Exception) { }
            ownProbe.ended(SystemClock.elapsedRealtime())
        }
    }

    private fun unconfirmedOutput() = Output(UNCONFIRMED, label(UNCONFIRMED), Evidence.TRACK_PROBE)

    /**
     * API 33+: can this PCM format be direct-played at all — i.e. is the
     * route a path that may bypass the mixer and strip an on-device effect?
     * [AudioManager.getDirectPlaybackSupport] is *static* and takes the
     * format plus the intended attributes; its result is a bitfield of
     * DIRECT_PLAYBACK_OFFLOAD/BITSTREAM/GAPLESS support, not a single value,
     * so test the bits rather than compare equality. Below API 33 there is
     * no answer (null), not "no".
     */
    private fun directPlaybackSupport(format: AudioFormat): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return try {
            val support = AudioManager.getDirectPlaybackSupport(format, mediaAttributes())
            val mask = AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED or
                AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED
            (support and mask) != 0
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun readRoutedDevices(track: AudioTrack): List<AudioDeviceInfo> = try {
        if (Build.VERSION.SDK_INT >= 36) {
            track.routedDevices.toList()
        } else {
            listOfNotNull(track.routedDevice)
        }
    } catch (e: RuntimeException) {
        emptyList()
    }

    /**
     * Tag a route from a silent-track probe. Pure, so the rule is unit-tested:
     * a null device is [UNCONFIRMED] (hard stop), an unrecognized device stays
     * [UNKNOWN], and the estimate is never silently substituted.
     */
    fun tagProbed(ranked: Output?, routedType: Int?, routedProduct: String?, model: String = Build.MODEL): Output? {
        if (routedType == null) {
            return Output(UNCONFIRMED, label(UNCONFIRMED), Evidence.TRACK_PROBE)
        }
        val kind = kindOf(routedType) ?: UNKNOWN
        return Output(
            kind,
            if (kind == UNKNOWN) label(UNKNOWN) else displayName(kind, routedProduct, model),
            Evidence.TRACK_PROBE
        )
    }

    /**
     * Best available media route indication. Prefer the reported route
     * from the probe: a silent playback track started and
     * [AudioTrack.getRoutedDevice] read while it plays. Only when a usable probe
     * cannot start does this fall back to the API 33+ anticipated route and then
     * the connected-output estimate. A started probe that reported no route
     * is [UNCONFIRMED], which pauses correction rather than guessing.
     *
     * The probe opens a real AudioTrack and waits for it to settle, so this
     * never blocks the main thread: on main, a cold cache answers from the
     * last (even if expired) probe result while [refreshProbeAsync] re-probes
     * in the background, or from the estimate when no probe has run yet. UI
     * reads are display-only and self-correct on the next read; the
     * auto-apply path ([refreshProbeAsync] with a callback) acts only on the
     * confirmed cache answer. A track-open failure retains the explicitly
     * labelled estimate fallback below; an empty reported route never does.
     * After a retry-cap give-up the hold pins this answer to [UNCONFIRMED] outright —
     * no probe, no estimate decay — until a device event or a confirmed
     * background probe says something new.
     */
    fun current(context: Context): Output? {
        // The probe starts and stops a real AudioTrack, and current() is called
        // several times per apply and on every volume key: a usable answer
        // stands while the route hint reads the same ([isTrusted], 1.13.2), so
        // reads during someone else's film open no track. Device hot-plug forces
        // a fresh read: onOutputsChanged -> refreshProbeAsync -> onRouteConfirmed
        // -> applyAll, all confirming off-thread before anything acts on the route.
        if (isHeldUnconfirmed()) {
            // Held after a give-up: answer the hard stop directly. No probe is
            // scheduled, so a failed track open can never decay to the
            // estimate, and a flapping sink earns no more track opens from UI
            // reads — only from device events and explicit re-applies.
            return Output(UNCONFIRMED, label(UNCONFIRMED), Evidence.TRACK_PROBE)
        }
        val cached = probeState.snapshot()
        if (cached != null && isTrusted(cached, context)) {
            // A probe that still stands: trust it. A null result (no track
            // could be opened at all) falls through to the estimate, and that
            // is a decision, not an oversight: if this ROM cannot open the
            // probe track, seeding UNCONFIRMED would make the hard stop
            // permanent — the "play a test tone" recovery is itself a track
            // open — and estimateOutput is already fail-closed against the
            // dangerous shape (several candidates -> AMBIGUOUS -> pause). The
            // residual risk knowingly kept: one external candidate that is
            // wrong gets its profile applied on a device where nothing can
            // confirm it. Reachable only with no hold set; a give-up hold
            // short-circuits above this branch.
            return cached.result?.output ?: estimateOutput(context)
        }
        if (isOnMainThread()) {
            // Never block the UI on a track open + settle. Refresh in the
            // background and answer now from the stalest-usable thing we
            // have: a prior probe result beats the estimate. UI reads
            // self-correct on the next read once the background probe lands.
            refreshProbeAsync(context)
            return cached?.result?.output ?: estimateOutput(context)
        }
        val generation = probeState.generation()
        val probed = probeWithSilentTrack(context)
        val publication = probeState.publish(
            generation, SystemClock.elapsedRealtime(), probed, releasesHold(probed)
        )
        if (!publication.accepted) {
            // A device event overtook this read: never return the obsolete
            // result even though it was correctly refused by the cache.
            return if (probeState.isHeld()) unconfirmedOutput()
                else probeState.snapshot()?.result?.output ?: unconfirmedOutput()
        }
        if (publication.changed) notifyRouteListeners()
        return probed?.output ?: estimateOutput(context)
    }

    /** Read-only indication. Playback callbacks use it without opening another AudioTrack. */
    internal fun indication(context: Context): Output? = try {
        estimateOutput(context)
    } catch (e: RuntimeException) {
        Log.w("OutputRoute", "Media route indication unavailable", e)
        null
    }

    /** Evidence upgrades are not device switches. */
    internal fun identity(output: Output?): Pair<String, String>? = output?.let {
        it.kind to it.name.trim().lowercase(java.util.Locale.ROOT)
    }

    /** The non-authoritative route reads: anticipated (API 33+) then connected estimate. */
    private fun estimateOutput(context: Context): Output? {
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        val mediaRoute = activeMediaRoute(am)
        if (mediaRoute.isNotEmpty()) {
            // A duplicated route is a mixture of endpoints, not permission
            // to select the first one. Preserve that uncertainty so no
            // single-output profile (including a legacy profile) is applied.
            val candidates = mediaRoute.map { device ->
                val kind = kindOf(device.type) ?: UNKNOWN
                Output(
                    kind,
                    if (kind == UNKNOWN) label(UNKNOWN)
                    else displayName(kind, device.productName?.toString()),
                    Evidence.ANTICIPATED
                )
            }
            return resolveCandidates(candidates)
        }

        val connected = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        // Built-in speakers are commonly listed even while an external route
        // is connected, so do not count them as a competing candidate. More
        // than one distinct external endpoint cannot be resolved reliably on
        // these API levels; fail closed instead of ranking a possibly inactive
        // Bluetooth/USB/HDMI device as the live media route.
        val external = connected.mapNotNull { device ->
            val kind = kindOf(device.type) ?: return@mapNotNull null
            if (kind == SPEAKER) return@mapNotNull null
            Output(kind, displayName(kind, device.productName?.toString()), Evidence.CONNECTED_ESTIMATE)
        }.distinct()
        val speaker = connected.firstOrNull { kindOf(it.type) == SPEAKER }?.let {
            Output(SPEAKER, displayName(SPEAKER, it.productName?.toString()), Evidence.CONNECTED_ESTIMATE)
        }
        return resolveConnectedCandidates(external, speaker)
    }

    internal fun resolveConnectedCandidates(externalCandidates: List<Output>, speaker: Output?): Output? = when {
        externalCandidates.size > 1 -> Output(AMBIGUOUS, label(AMBIGUOUS), Evidence.AMBIGUOUS)
        externalCandidates.size == 1 -> externalCandidates.single()
        else -> speaker
    }

    internal fun resolveCandidates(candidates: List<Output>): Output? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.single()
        return Output(AMBIGUOUS, label(AMBIGUOUS), Evidence.AMBIGUOUS)
    }

    /**
     * The product names of every connected output of [kind]. A soundbar can
     * be connected alongside another device of the same kind, and Android's
     * single "current" route may name only the generic port, so detection
     * looks at all of them. Empty for an unknown kind or a failed lookup.
     */
    fun connectedNames(context: Context, kind: String?): List<String> {
        if (kind == null) return emptyList()
        val am = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        return try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { kindOf(it.type) == kind }
                .mapNotNull { it.productName?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /** The name to show for an output: the device's own, unless it only repeats the TV's model. */
    fun displayName(kind: String, product: String?, model: String = Build.MODEL): String {
        val p = product?.trim().orEmpty()
        return if (kind == SPEAKER || p.isEmpty() || p == model) label(kind) else p
    }

    /**
     * Tag a measurement from the route reported for its own AudioTrack. A null
     * or empty list means Android supplied no route and leaves the estimate as
     * fallback. A reported but unrecognized route remains unknown; it must not
     * be replaced by a more convenient connected-device guess.
     */
    fun tag(
        ranked: Output?,
        routedDevices: List<AudioDeviceInfo>?,
        model: String = Build.MODEL
    ): Output? {
        if (routedDevices.isNullOrEmpty()) return ranked
        val candidates = routedDevices.map { device ->
            val kind = kindOf(device.type) ?: UNKNOWN
            Output(
                kind,
                if (kind == UNKNOWN) label(UNKNOWN)
                else displayName(kind, device.productName?.toString(), model),
                Evidence.TRACK_REPORTED
            )
        }
        return resolveCandidates(candidates)
    }

    /** Compatibility overload for callers/tests with one reported route. */
    fun tag(ranked: Output?, routedType: Int?, routedProduct: String?, model: String = Build.MODEL): Output? {
        val type = routedType ?: return ranked
        val kind = kindOf(type) ?: UNKNOWN
        return Output(
            kind,
            if (kind == UNKNOWN) label(UNKNOWN) else displayName(kind, routedProduct, model),
            Evidence.TRACK_REPORTED
        )
    }

    /** The key a new measurement is saved under: never null, which is reserved for old profiles. */
    fun keyFor(output: Output?): String =
        output?.kind?.takeUnless { it == AMBIGUOUS } ?: UNKNOWN

    /** Why a profile was, or was not, picked for the current output. */
    data class Pick(val profile: Profile?, val switched: Boolean)

    /**
     * The profile to apply on [kind]:
     * - the chosen profile, when it was measured on this output (or is from
     *   before outputs were recorded, and so applies anywhere, as it always did);
     * - otherwise the newest profile measured on this output;
     * - otherwise none — correcting a soundbar with the TV speakers' curve is
     *   worse than not correcting it.
     * With an unknown current output, only an explicitly unknown-output or
     * legacy untagged profile is safe; a known-output profile is not guessed.
     */
    fun pick(profiles: List<Profile>, chosenId: String?, kind: String?, outputName: String? = null): Pick {
        val chosen = profiles.firstOrNull { it.id == chosenId } ?: profiles.maxByOrNull { it.timestampMs }
        // UNCONFIRMED blocks like AMBIGUOUS — and just as absolutely: the
        // legacy "untagged applies everywhere" allowance must not become a
        // side door past the hard stop. If Android will not say where audio
        // goes, nothing is safe to apply there, even a pre-output profile.
        if (kind == AMBIGUOUS || kind == UNCONFIRMED) return Pick(null, switched = chosen != null)
        if (kind == null) {
            if (chosen == null || chosen.outputKind == null || chosen.outputKind == UNKNOWN) {
                return Pick(chosen, switched = false)
            }
            val fallback = profiles.filter { it.outputKind == UNKNOWN }.maxByOrNull { it.timestampMs }
                ?: profiles.filter { it.outputKind == null }.maxByOrNull { it.timestampMs }
            return Pick(fallback, switched = fallback?.id != chosen.id)
        }

        val sameKind = profiles.filter { it.outputKind == kind }
        val routeName = specificName(kind, outputName)
        if (routeName != null) {
            val exact = sameKind
                .filter { specificName(kind, it.outputName) == routeName }
                .maxByOrNull { it.timestampMs }
            if (exact != null) return Pick(exact, switched = exact.id != chosen?.id)
        }

        if (chosen != null && chosen.outputKind == null) return Pick(chosen, switched = false)
        if (chosen != null && chosen.outputKind == kind && specificName(kind, chosen.outputName) == null) {
            return Pick(chosen, switched = false)
        }

        val generic = sameKind.filter { specificName(kind, it.outputName) == null }
        if (generic.isNotEmpty()) {
            val match = generic.maxByOrNull { it.timestampMs }
            return Pick(match, switched = match?.id != chosen?.id)
        }
        if (sameKind.any { specificName(kind, it.outputName) != null }) {
            // Android did not name this output, or named a different one; don't
            // apply a profile tied to another specific device of the same kind.
            return Pick(null, switched = false)
        }
        return Pick(null, switched = chosen != null)
    }

    private fun specificName(kind: String, name: String?): String? =
        ProfileOutputIdentity.specificName(label(kind), name)

    /** A same-kind profile exists, but Android did not identify which device is playing. */
    internal fun needsDeviceName(profiles: List<Profile>, output: Output?): Boolean {
        if (output == null || output.kind in setOf(UNKNOWN, AMBIGUOUS, UNCONFIRMED)) return false
        val names = profiles.filter { it.outputKind == output.kind }.map { it.outputName }
        return ProfileOutputIdentity.needsDeviceName(label(output.kind), output.name, names)
    }

    /**
     * Dolby and DTS sent to a soundbar or receiver as a bitstream never pass
     * through Android's mixer, so no on-device equaliser reaches them. True
     * when this output may be carrying such audio: it can carry a bitstream
     * ([bitstreamCapable]) and the Surround sound setting lets one through
     * ([SurroundOutput]). Until 1.10.0 this was HDMI on Android 12+ only, so an
     * optical soundbar, and every Android 11 box, never saw the warning.
     */
    fun mayPassThrough(context: Context, kind: String?): Boolean =
        mayCarryBitstream(context, kind) && SurroundOutput.mayBypass(SurroundOutput.read(context))

    /**
     * True when an output of [kind] here could carry Dolby or DTS undecoded, whatever the setting says.
     *
     * A wired route is judged from the route the media is on ([capabilityTypes]),
     * not from every port the TV has ever seen: an optical output stays connected
     * beside active headphones, and counting it there is what let Core EQ set
     * Surround sound to Never for an output that only ever takes PCM (#413).
     */
    fun mayCarryBitstream(context: Context, kind: String?): Boolean {
        if (kind != WIRED) return bitstreamCapable(kind, emptyList())
        val am = context.getSystemService(AudioManager::class.java)
            ?: return bitstreamCapable(kind, emptyList())
        return bitstreamCapable(kind, capabilityTypes(activeMediaTypes(am), connectedTypes(am)))
    }

    /**
     * The types [bitstreamCapable] judges a wired route from: the active media
     * route when Android named one (API 33+), otherwise every connected output —
     * the only estimate there is below API 33, and what 1.10.0 judged everywhere.
     * Pure, so the rule is unit-tested without Android.
     */
    internal fun capabilityTypes(activeMediaRoute: List<Int>, connectedTypes: List<Int>): List<Int> =
        activeMediaRoute.ifEmpty { connectedTypes }

    /**
     * HDMI and HDMI ARC can carry Dolby and DTS undecoded; so can optical
     * (S/PDIF), which Android reports as TYPE_LINE_DIGITAL and Core EQ files
     * under [WIRED] with analogue line outs, so a wired route counts when
     * optical is on the route being judged ([capabilityTypes]) — a port merely
     * connected beside the active output does not. Bluetooth, USB and analogue
     * only take PCM.
     */
    internal fun bitstreamCapable(kind: String?, routeTypes: List<Int>): Boolean = when (kind) {
        HDMI_ARC, HDMI -> true
        WIRED -> AudioDeviceInfo.TYPE_LINE_DIGITAL in routeTypes
        else -> false
    }

    /**
     * API 33+'s anticipated route for [mediaAttributes]: empty below API 33, on
     * failure, or where Android names no route — where only the connected-output
     * estimate is left to speak.
     */
    private fun activeMediaRoute(am: AudioManager): List<AudioDeviceInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptyList()
        return try {
            am.getAudioDevicesForAttributes(mediaAttributes()).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun activeMediaTypes(am: AudioManager): List<Int> = activeMediaRoute(am).map { it.type }

    /** The attributes the route label and bitstream capability are both judged by. */
    private fun mediaAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()

    private fun connectedTypes(am: AudioManager): List<Int> = try {
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
    } catch (_: SecurityException) {
        emptyList()
    }

    // API 31 route constants are guarded by the SDK checks above; Android 11
    // devices stay on the same generic route handling as before.
    private const val TYPE_HDMI_EARC = 29 // AudioDeviceInfo.TYPE_HDMI_EARC, API 31
    private const val TYPE_BLE_HEADSET = 26 // API 31
    private const val TYPE_BLE_SPEAKER = 27 // API 31

    /** Finite silent PCM, longer than the bounded settling/polling window. */
    private const val PROBE_SAMPLE_RATE_HZ = 48_000
    private const val PROBE_TRACK_MS = 250
    private const val PROBE_BUFFER_BYTES = PROBE_SAMPLE_RATE_HZ * PROBE_TRACK_MS / 1000 * 2 * 2
    private const val PROBE_SETTLE_MS = 120L
    private const val PROBE_POLL_MS = 10L

    /** How long after a probe's track is released its stop/release reports may still arrive. */
    internal const val OWN_PROBE_ECHO_MS = 500L
    private val ownProbe = OwnProbeEcho(OWN_PROBE_ECHO_MS)

    /** True while Core EQ's own probe plays and briefly after: a playback report now is its echo. */
    internal fun isOwnProbeEcho(): Boolean = ownProbe.isEcho(SystemClock.elapsedRealtime())

    private val probeState = RouteProbeState<ProbeResult>()
    private val routeListeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    internal fun addRouteListener(listener: () -> Unit) { routeListeners.add(listener) }
    internal fun removeRouteListener(listener: () -> Unit) { routeListeners.remove(listener) }

    internal fun isHeldUnconfirmed(): Boolean = probeState.isHeld()

    /** Invalidation and publication use the same lock; stale probes cannot republish over it. */
    fun invalidateProbeCache() = probeState.invalidate()

    internal fun hasFreshProbe(context: Context): Boolean =
        probeState.snapshot()?.let { isTrusted(it, context) } ?: false

    /**
     * Whether a cached probe still answers for the route (1.13.2): inside the
     * short window always; after it, only a usable answer whose route hint
     * still reads the same, up to [RouteTrust.MAX_TRUST_MS]. Reading the hint
     * costs a couple of calls into the audio system, nothing like opening a
     * track, and only after the short window.
     */
    private fun isTrusted(cached: RouteProbeState.Cached<ProbeResult>, context: Context): Boolean {
        val ageMs = SystemClock.elapsedRealtime() - cached.atMs
        if (ageMs in 0 until RouteTrust.SHORT_MS) return true
        val result = cached.result
        val usable = result != null && result.output.kind != UNCONFIRMED && result.output.kind != AMBIGUOUS
        if (!usable) return false
        return RouteTrust.trusted(ageMs, usable = true, hintAtProbe = result?.hint, hintNow = identity(indication(context)))
    }

    internal fun publishUnconfirmedProbe() = probeState.hold(
        SystemClock.elapsedRealtime(), ProbeResult(unconfirmedOutput(), null)
    )

    private fun releasesHold(result: ProbeResult?): Boolean = result != null && result.output.kind != UNCONFIRMED

    private val probeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "coreeq-route-probe").apply { isDaemon = true }
    }

    /**
     * Coalesced, generation-checked probe. Completion callbacks are detached
     * before dispatch: a retry requested by a callback must await its own
     * worker, not run immediately in the old completion's drain loop.
     */
    fun refreshProbeAsync(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        if (!probeState.enqueue(onDone)) return
        probeExecutor.execute {
            var generation = probeState.generation()
            var result: ProbeResult? = null
            try {
                result = probeWithSilentTrack(app)
                if (probeState.generation() != generation) {
                    generation = probeState.generation()
                    result = probeWithSilentTrack(app)
                }
            } catch (e: RuntimeException) {
                Log.w("OutputRoute", "Background route probe failed", e)
            }
            val completion = probeState.complete(
                generation, SystemClock.elapsedRealtime(), result, releasesHold(result)
            )
            Handler(Looper.getMainLooper()).post {
                completion.callbacks.forEach { callback ->
                    try { callback() } catch (e: RuntimeException) {
                        Log.w("OutputRoute", "Route completion callback failed", e)
                    }
                }
                if (completion.publication.changed) dispatchRouteListeners()
            }
        }
    }

    private fun notifyRouteListeners() {
        Handler(Looper.getMainLooper()).post { dispatchRouteListeners() }
    }

    private fun dispatchRouteListeners() {
        routeListeners.forEach { listener ->
            try { listener() } catch (e: RuntimeException) {
                Log.w("OutputRoute", "Route listener failed", e)
            }
        }
    }

    private fun isOnMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()
}
