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
 * versions rank connected outputs as a fallback: Bluetooth or USB, then wired,
 * HDMI (eARC/ARC before plain HDMI), then the built-in speaker. At measurement
 * time the sweep's own `AudioTrack` route is more direct evidence: when Android
 * reports a recognized device, [tag] stores that actual output kind. If it does
 * not, the connected-output estimate is used. A mismatch later means the
 * profile is not applied to a different chain.
 */
object OutputRoute {

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

    data class Output(val kind: String, val name: String)

    /** The label a person reads: "TV speakers", "HDMI ARC (soundbar)". */
    fun label(kind: String?): String = when (kind) {
        SPEAKER -> "TV speakers"
        HDMI_ARC -> "HDMI ARC (soundbar or receiver)"
        HDMI -> "HDMI"
        BLUETOOTH -> "Bluetooth"
        USB -> "USB audio"
        WIRED -> "Wired headphones or line out"
        UNKNOWN -> "an output Android did not name"
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
     * Best available media output estimate. API 33+ predicts the route for
     * media attributes; older releases fall back to ranking connected outputs.
     * This still cannot prove where another app's current stream is routed.
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
                // Android returned an anticipated route but Core EQ does not
                // recognize its device kind: do not replace that evidence with
                // a lower-confidence connected-device ranking.
                return mediaRoute.firstNotNullOfOrNull { device ->
                    kindOf(device.type)?.let { kind ->
                        Output(kind, displayName(kind, device.productName?.toString()))
                    }
                }
            }
        }

        val connected = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val kind = pickKind(connected.map { it.type }) ?: return null
        val device = connected.firstOrNull { kindOf(it.type) == kind }
        return Output(kind, displayName(kind, device?.productName?.toString()))
    }

    /** The name to show for an output: the device's own, unless it only repeats the TV's model. */
    fun displayName(kind: String, product: String?, model: String = Build.MODEL): String {
        val p = product?.trim().orEmpty()
        return if (kind == SPEAKER || p.isEmpty() || p == model) label(kind) else p
    }

    /**
     * Tag a measurement with the sweep's actual routed output when recognized.
     * The predicted/connected-output estimate is only the fallback when Android
     * does not report a useful routed device. This prevents a sweep measured on
     * TV speakers from being mislabeled as an attached HDMI soundbar merely
     * because that endpoint had higher connection priority.
     */
    fun tag(ranked: Output?, routedType: Int?, routedProduct: String?, model: String = Build.MODEL): Output? {
        val routedKind = routedType?.let(::kindOf)
        return when {
            routedKind != null -> Output(routedKind, displayName(routedKind, routedProduct, model))
            else -> ranked
        }
    }

    /** The key a new measurement is saved under: never null, which is reserved for old profiles. */
    fun keyFor(output: Output?): String = output?.kind ?: UNKNOWN

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
        val clean = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
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

    // Types added after minSdk 26: the constants are inlined, so naming them
    // here keeps older devices working (they simply never report them).
    private const val TYPE_HDMI_EARC = 29 // AudioDeviceInfo.TYPE_HDMI_EARC, API 31
    private const val TYPE_BLE_HEADSET = 26 // API 31
    private const val TYPE_BLE_SPEAKER = 27 // API 31
}
