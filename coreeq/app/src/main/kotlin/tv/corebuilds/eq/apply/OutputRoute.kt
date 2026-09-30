package tv.corebuilds.eq.apply

import android.content.Context
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
 * on, and correction follows the output: switch to the soundbar and the
 * soundbar's profile applies; switch to an output nothing was measured on and
 * correction steps aside instead of applying another chain's curve.
 *
 * Android does not say which output media is routed to (the public API lists
 * what is connected), so [pickKind] ranks the connected outputs the way
 * Android's own media routing does: a Bluetooth or USB device that was
 * plugged in wins, then a wired one, then HDMI (eARC/ARC before plain
 * HDMI), then the built-in speaker. The same function tags the measurement
 * and picks at play time, so a setup always maps to the same kind.
 */
object OutputRoute {

    /** Output kinds, stored in profiles. Stable strings: they are saved data. */
    const val SPEAKER = "speaker"
    const val HDMI_ARC = "hdmi_arc"
    const val HDMI = "hdmi"
    const val BLUETOOTH = "bluetooth"
    const val USB = "usb"
    const val WIRED = "wired"

    data class Output(val kind: String, val name: String)

    /** The label a person reads: "TV speakers", "HDMI ARC (soundbar)". */
    fun label(kind: String?): String = when (kind) {
        SPEAKER -> "TV speakers"
        HDMI_ARC -> "HDMI ARC (soundbar or receiver)"
        HDMI -> "HDMI"
        BLUETOOTH -> "Bluetooth"
        USB -> "USB audio"
        WIRED -> "Wired headphones or line out"
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

    /** The current output, or null when Android lists nothing recognisable. */
    fun current(context: Context): Output? {
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val kind = pickKind(devices.map { it.type }) ?: return null
        val device = devices.firstOrNull { kindOf(it.type) == kind }
        return Output(kind, displayName(kind, device?.productName?.toString()))
    }

    /** The name to show for an output: the device's own, unless it only repeats the TV's model. */
    fun displayName(kind: String, product: String?, model: String = Build.MODEL): String {
        val p = product?.trim().orEmpty()
        return if (kind == SPEAKER || p.isEmpty() || p == model) label(kind) else p
    }

    /**
     * What a measurement is tagged with.
     *
     * The key ([Output.kind]) is always [pickKind]'s answer: at play time
     * Core EQ cannot see where another app's audio goes, only what is
     * connected, so selection must use the same rule on both sides — then a
     * setup always finds the profile measured in that setup, and that curve
     * was measured on whatever that setup really plays through.
     *
     * The name is the truth where Android gives it: the device the sweep's own
     * AudioTrack was routed to. When that differs from the ranked guess, the
     * profile says what was actually measured ("TV speakers") rather than
     * what was merely connected ("Sonos Beam").
     */
    fun tag(ranked: Output?, routedType: Int?, routedProduct: String?, model: String = Build.MODEL): Output? {
        val routedKind = routedType?.let(::kindOf)
        return when {
            ranked == null && routedKind == null -> null
            ranked == null -> Output(routedKind!!, displayName(routedKind, routedProduct, model))
            routedKind == null -> ranked
            else -> Output(ranked.kind, displayName(routedKind, routedProduct, model))
        }
    }

    /** Why a profile was, or was not, picked for the current output. */
    data class Pick(val profile: Profile?, val switched: Boolean)

    /**
     * The profile to apply on [kind]:
     * - the chosen profile, when it was measured on this output (or is from
     *   before outputs were recorded, and so applies anywhere, as it always did);
     * - otherwise the newest profile measured on this output;
     * - otherwise none — correcting a soundbar with the TV speakers' curve is
     *   worse than not correcting it.
     * When the output is unknown, the chosen profile applies, as before.
     */
    fun pick(profiles: List<Profile>, chosenId: String?, kind: String?): Pick {
        val chosen = profiles.firstOrNull { it.id == chosenId } ?: profiles.maxByOrNull { it.timestampMs }
        if (kind == null) return Pick(chosen, switched = false)
        if (chosen != null && (chosen.outputKind == null || chosen.outputKind == kind)) return Pick(chosen, switched = false)
        val match = profiles.filter { it.outputKind == kind }.maxByOrNull { it.timestampMs }
        return Pick(match, switched = match != null)
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
