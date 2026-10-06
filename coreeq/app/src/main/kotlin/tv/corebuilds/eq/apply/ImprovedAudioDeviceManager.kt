package tv.corebuilds.eq.apply

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import tv.corebuilds.eq.dsp.RoomProfile

/**
 * Improved audio device manager with comprehensive device detection,
 * automatic profile switching, and HDMI passthrough detection.
 * 
 * Key improvements over basic implementation:
 * 1. AudioDeviceCallback for real-time device change detection
 * 2. HDMI passthrough detection (warns when EQ won't work)
 * 3. Device-specific profile matching
 * 4. Bluetooth device identification and profile persistence
 * 5. Automatic profile switching based on connected device
 */
class ImprovedAudioDeviceManager(private val context: Context) {
    
    private val audioManager: AudioManager = 
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private var deviceCallback: AudioDeviceCallback? = null
    private var onDeviceChangedListener: ((AudioDeviceState) -> Unit)? = null
    
    private var currentDevice: AudioDeviceInfo? = null
    private var deviceProfiles: MutableMap<String, RoomProfile> = mutableMapOf()
    
    companion object {
        private const val TAG = "ImprovedAudioDeviceMgr"
        
        // Device types that support EQ
        private val EQ_SUPPORTED_TYPES = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY
        )
        
        // Device types that may have HDMI passthrough (EQ won't work)
        private val HDMI_PASSTHROUGH_TYPES = setOf(
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC
        )
    }
    
    /**
     * Start monitoring audio device changes
     */
    fun startMonitoring(listener: (AudioDeviceState) -> Unit) {
        onDeviceChangedListener = listener
        
        // Register AudioDeviceCallback for real-time device change detection
        deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                Log.d(TAG, "Audio devices added: ${addedDevices.size}")
                handleDeviceChange()
            }
            
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                Log.d(TAG, "Audio devices removed: ${removedDevices.size}")
                handleDeviceChange()
            }
        }
        
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        
        // Initial device detection
        handleDeviceChange()
        
        Log.i(TAG, "Audio device monitoring started")
    }
    
    /**
     * Stop monitoring audio device changes
     */
    fun stopMonitoring() {
        deviceCallback?.let {
            audioManager.unregisterAudioDeviceCallback(it)
            deviceCallback = null
        }
        onDeviceChangedListener = null
        Log.i(TAG, "Audio device monitoring stopped")
    }
    
    /**
     * Handle device change events
     */
    private fun handleDeviceChange() {
        val newState = detectCurrentDevice()
        
        if (newState.device != currentDevice) {
            currentDevice = newState.device
            Log.i(TAG, "Audio device changed: ${newState.deviceName} (${newState.deviceType})")
            onDeviceChangedListener?.invoke(newState)
        }
    }
    
    /**
     * Detect the current active audio output device
     */
    fun detectCurrentDevice(): AudioDeviceState {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        
        // Priority order for device selection:
        // 1. Wired headphones/headset (highest priority)
        // 2. Bluetooth A2DP
        // 3. USB audio
        // 4. HDMI (may have passthrough issues)
        // 5. Built-in speaker (lowest priority)
        
        val device = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_HDMI }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_HDMI_ARC }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        
        return if (device != null) {
            createDeviceState(device)
        } else {
            AudioDeviceState(
                device = null,
                deviceType = "unknown",
                deviceName = "Unknown",
                supportsEq = false,
                hdmiPassthrough = false,
                warnings = listOf("No audio output device detected")
            )
        }
    }
    
    /**
     * Create AudioDeviceState from AudioDeviceInfo
     */
    private fun createDeviceState(device: AudioDeviceInfo): AudioDeviceState {
        val deviceType = getDeviceTypeName(device.type)
        val deviceName = getDeviceDisplayName(device)
        val supportsEq = device.type in EQ_SUPPORTED_TYPES
        val hdmiPassthrough = device.type in HDMI_PASSTHROUGH_TYPES
        
        val warnings = mutableListOf<String>()
        
        // HDMI passthrough warning
        if (hdmiPassthrough) {
            warnings.add("HDMI audio output detected. If using HDMI passthrough (bitstream), " +
                    "audio effects will be bypassed. Set audio output to PCM/LPCM in your TV settings " +
                    "to enable EQ processing.")
        }
        
        // Bluetooth codec warning
        if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && Build.VERSION.SDK_INT >= 28) {
            val codec = device.codecs
            if (codec != null) {
                Log.d(TAG, "Bluetooth codec: ${codec.codecs?.firstOrNull()?.name ?: "unknown"}")
            }
        }
        
        // USB audio info
        if (device.type == AudioDeviceInfo.TYPE_USB_DEVICE || 
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET) {
            Log.d(TAG, "USB audio device detected: ${device.productName}")
        }
        
        return AudioDeviceState(
            device = device,
            deviceType = deviceType,
            deviceName = deviceName,
            supportsEq = supportsEq,
            hdmiPassthrough = hdmiPassthrough,
            warnings = warnings
        )
    }
    
    /**
     * Get human-readable device type name
     */
    private fun getDeviceTypeName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth_a2dp"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth_sco"
            AudioDeviceInfo.TYPE_HDMI -> "hdmi"
            AudioDeviceInfo.TYPE_HDMI_ARC -> "hdmi_arc"
            AudioDeviceInfo.TYPE_HDMI_EARC -> "hdmi_earc"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "usb_accessory"
            AudioDeviceInfo.TYPE_DOCK -> "dock"
            else -> "unknown"
        }
    }
    
    /**
     * Get display name for audio device
     */
    private fun getDeviceDisplayName(device: AudioDeviceInfo): String {
        val productName = device.productName?.toString()
        val typeName = getDeviceTypeName(device.type)
        
        return when {
            productName != null && productName.isNotBlank() -> productName
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                // Try to get Bluetooth device name
                "Bluetooth ${device.address ?: "Device"}"
            }
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE || 
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET -> {
                "USB ${device.productName ?: "Audio"}"
            }
            else -> typeName.replaceFirstChar { it.uppercase() }
        }
    }
    
    /**
     * Get unique identifier for device (for profile persistence)
     */
    fun getDeviceIdentifier(device: AudioDeviceInfo): String {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                // Use Bluetooth MAC address
                "bt_${device.address}"
            }
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET -> {
                // Use USB vendor:product ID
                "usb_${device.address}"
            }
            else -> {
                // Use device type
                "type_${device.type}"
            }
        }
    }
    
    /**
     * Save room profile for specific device
     */
    fun saveProfileForDevice(deviceId: String, profile: RoomProfile) {
        deviceProfiles[deviceId] = profile
        Log.i(TAG, "Profile saved for device: $deviceId")
    }
    
    /**
     * Get room profile for specific device
     */
    fun getProfileForDevice(deviceId: String): RoomProfile? {
        return deviceProfiles[deviceId]
    }
    
    /**
     * Get all saved device profiles
     */
    fun getAllDeviceProfiles(): Map<String, RoomProfile> {
        return deviceProfiles.toMap()
    }
    
    /**
     * Get all available audio output devices
     */
    fun getAvailableDevices(): List<AudioDeviceState> {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { createDeviceState(it) }
    }
    
    /**
     * Check if current device supports EQ
     */
    fun isCurrentDeviceEqSupported(): Boolean {
        return currentDevice?.let { it.type in EQ_SUPPORTED_TYPES } ?: false
    }
    
    /**
     * Check if HDMI passthrough is active
     */
    fun isHdmiPassthroughActive(): Boolean {
        return currentDevice?.type in HDMI_PASSTHROUGH_TYPES
    }
    
    /**
     * Get recommended audio settings for current device
     */
    fun getRecommendedSettings(): AudioSettingsRecommendation {
        val device = currentDevice ?: return AudioSettingsRecommendation()
        
        return when (device.type) {
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> {
                AudioSettingsRecommendation(
                    audioFormat = "PCM/LPCM",
                    sampleRate = 48000,
                    channelConfig = "Stereo or 5.1/7.1",
                    notes = listOf(
                        "Set TV audio output to PCM/LPCM (not bitstream/passthrough)",
                        "This allows EQ processing before sending to receiver/soundbar",
                        "Bitstream mode bypasses all audio effects"
                    )
                )
            }
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                AudioSettingsRecommendation(
                    audioFormat = "SBC/AAC/aptX/LDAC",
                    sampleRate = 44100,
                    channelConfig = "Stereo",
                    notes = listOf(
                        "Bluetooth audio has limited bandwidth",
                        "High-quality codecs (LDAC, aptX HD) recommended",
                        "EQ effects may be limited by codec capabilities"
                    )
                )
            }
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET -> {
                AudioSettingsRecommendation(
                    audioFormat = "PCM",
                    sampleRate = 48000,
                    channelConfig = "Stereo or Multi-channel",
                    notes = listOf(
                        "USB audio provides high-quality digital output",
                        "Supports high sample rates (96kHz, 192kHz)",
                        "Best option for audiophile listening"
                    )
                )
            }
            else -> {
                AudioSettingsRecommendation(
                    audioFormat = "PCM",
                    sampleRate = 48000,
                    channelConfig = "Stereo",
                    notes = listOf("Standard audio output")
                )
            }
        }
    }
}

/**
 * Current state of audio output device
 */
data class AudioDeviceState(
    val device: AudioDeviceInfo?,
    val deviceType: String,
    val deviceName: String,
    val supportsEq: Boolean,
    val hdmiPassthrough: Boolean,
    val warnings: List<String>
) {
    fun toDisplayString(): String {
        return buildString {
            append("$deviceName ($deviceType)")
            if (!supportsEq) append(" - EQ not supported")
            if (hdmiPassthrough) append(" - HDMI passthrough")
        }
    }
}

/**
 * Recommended audio settings for device
 */
data class AudioSettingsRecommendation(
    val audioFormat: String = "PCM",
    val sampleRate: Int = 48000,
    val channelConfig: String = "Stereo",
    val notes: List<String> = emptyList()
) {
    fun toDisplayString(): String {
        return buildString {
            appendLine("Audio Format: $audioFormat")
            appendLine("Sample Rate: ${sampleRate}Hz")
            appendLine("Channels: $channelConfig")
            if (notes.isNotEmpty()) {
                appendLine("\nNotes:")
                notes.forEach { appendLine("• $it") }
            }
        }
    }
}
