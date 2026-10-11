package tv.corebuilds.eq

import android.media.AudioDeviceInfo

/** Stable diagnostic labels for Android's public audio-device type constants. */
internal fun audioDeviceTypeLabel(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_UNKNOWN -> "TYPE_UNKNOWN"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "TYPE_BUILTIN_EARPIECE"
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "TYPE_BUILTIN_SPEAKER"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "TYPE_WIRED_HEADSET"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "TYPE_WIRED_HEADPHONES"
    AudioDeviceInfo.TYPE_LINE_ANALOG -> "TYPE_LINE_ANALOG"
    AudioDeviceInfo.TYPE_LINE_DIGITAL -> "TYPE_LINE_DIGITAL"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "TYPE_BLUETOOTH_SCO"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "TYPE_BLUETOOTH_A2DP"
    AudioDeviceInfo.TYPE_HDMI -> "TYPE_HDMI"
    AudioDeviceInfo.TYPE_HDMI_ARC -> "TYPE_HDMI_ARC"
    AudioDeviceInfo.TYPE_USB_DEVICE -> "TYPE_USB_DEVICE"
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> "TYPE_USB_ACCESSORY"
    AudioDeviceInfo.TYPE_DOCK -> "TYPE_DOCK"
    AudioDeviceInfo.TYPE_FM -> "TYPE_FM"
    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "TYPE_BUILTIN_MIC"
    AudioDeviceInfo.TYPE_FM_TUNER -> "TYPE_FM_TUNER"
    AudioDeviceInfo.TYPE_TV_TUNER -> "TYPE_TV_TUNER"
    AudioDeviceInfo.TYPE_TELEPHONY -> "TYPE_TELEPHONY"
    AudioDeviceInfo.TYPE_AUX_LINE -> "TYPE_AUX_LINE"
    AudioDeviceInfo.TYPE_IP -> "TYPE_IP"
    AudioDeviceInfo.TYPE_BUS -> "TYPE_BUS"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "TYPE_USB_HEADSET"
    AudioDeviceInfo.TYPE_HEARING_AID -> "TYPE_HEARING_AID"
    AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "TYPE_REMOTE_SUBMIX"
    else -> "TYPE_$type"
}
