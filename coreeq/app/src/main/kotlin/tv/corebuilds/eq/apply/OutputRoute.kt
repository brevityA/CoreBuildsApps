package tv.corebuilds.eq.apply

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
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
 * Android does not expose another app's actual media destination. On API 33+
 * [current] asks for the anticipated route for media attributes; earlier
 * versions use a connected-output estimate only when one external candidate
 * is present; competing external endpoints are treated as ambiguous. At
 * measurement time the route reported for the sweep's own `AudioTrack` is
 * stronger evidence, but does not prove the acoustic path or downstream signal
 * processing. If Android reports no route, the connected-output estimate is
 * retained and labeled as such.
 */
object OutputRoute {

    fun evidenceDescription(evidence: Evidence): String = when (evidence) {
        Evidence.ANTICIPATED -> "anticipated media route"
        Evidence.CONNECTED_ESTIMATE -> "connected-output estimate"
        Evidence.TRACK_REPORTED -> "route reported for the sweep track"
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

    enum class Evidence { ANTICIPATED, CONNECTED_ESTIMATE, TRACK_REPORTED, AMBIGUOUS }
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
     * Best available media route indication. API 33+ asks for the anticipated
     * route for media attributes; earlier releases use a connected-output
     * estimate only when one external candidate is present. Neither proves
     * where another app's current stream is routed.
     */
    fun current(context: Context): Output? {
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val mediaRoute = try {
                am.getAudioDevicesForAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                ).toList()
            } catch (_: Exception) {
                emptyList()
            }
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

    internal fun resolveConnectedCandidates(external: List<Output>, speaker: Output?): Output? = when {
        external.size > 1 -> Output(AMBIGUOUS, label(AMBIGUOUS), Evidence.AMBIGUOUS)
        external.size == 1 -> external.single()
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
        if (kind == AMBIGUOUS) return Pick(null, switched = chosen != null)
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

    private fun specificName(kind: String, name: String?): String? {
        val clean = name?.trim()?.takeIf { s -> s.isNotEmpty() } ?: return null
        return clean.takeUnless { it.equals(label(kind), ignoreCase = true) }
            ?.lowercase(java.util.Locale.ROOT)
    }

    /**
     * Dolby and DTS sent to a soundbar or receiver as a bitstream never pass
     * through Android's mixer, so no on-device equaliser reaches them. True
     * when this output may be carrying such audio (Android 12+ reports the
     * surround setting; before that the answer is unknown, so false).
     */
    fun mayPassThrough(context: Context, kind: String?): Boolean {
        if (kind != HDMI_ARC && kind != HDMI) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.encodedSurroundMode != AudioManager.ENCODED_SURROUND_OUTPUT_NEVER
    }

    // API 31 route constants are guarded by the SDK checks above; Android 11
    // devices stay on the same generic route handling as before.
    private const val TYPE_HDMI_EARC = 29 // AudioDeviceInfo.TYPE_HDMI_EARC, API 31
    private const val TYPE_BLE_HEADSET = 26 // API 31
    private const val TYPE_BLE_SPEAKER = 27 // API 31
}
