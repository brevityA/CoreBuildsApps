# UI Design & Enhanced Integration Guide

## Overview

This guide covers the modern UI components and integration patterns for Core EQ v1.2.0's enhanced audio processing features.

## New UI Components

### 1. EffectChainStatusCard

**Location:** `app/src/main/kotlin/tv/corebuilds/eq/ui/EffectChainStatusCard.kt`

**Purpose:** Modern status card displaying real-time effect chain information

**Features:**
- Visual effect chain status with active/inactive indicators
- Content type display with color coding
- Effect badges (EQ, Bass Boost, Loudness Enhancer, Dynamics Processing)
- Night mode indicator
- Device information with HDMI passthrough warnings
- Smooth canvas-based rendering

**Usage:**
```xml
<tv.corebuilds.eq.ui.EffectChainStatusCard
    android:id="@+id/effect_chain_status"
    android:layout_width="match_parent"
    android:layout_height="280dp"
    android:layout_marginTop="@dimen/cb_space_sm" />
```

```kotlin
val statusCard = findViewById<EffectChainStatusCard>(R.id.effect_chain_status)

// Update with effect chain status
val chainStatus = effectChain.getStatus()
statusCard.updateStatus(chainStatus)

// Update with device info
statusCard.updateDevice(
    name = "Sony Bravia XR",
    type = "hdmi",
    passthrough = true
)
```

### 2. EnhancedAudioPrefs

**Location:** `app/src/main/kotlin/tv/corebuilds/eq/ui/EnhancedAudioPrefs.kt`

**Purpose:** Preferences management for enhanced audio features

**Settings:**
- Effect chain toggles (Bass Boost, Loudness Enhancer, Dynamics Processing)
- Night mode configuration (manual/auto, time-based scheduling)
- Content type preferences (auto-detection, last used type)
- Device-specific settings (HDMI warnings, profile mappings)
- Advanced options (statistics display, effect chain priority)

**Usage:**
```kotlin
val prefs = EnhancedAudioPrefs(context)

// Read settings
val bassBoostEnabled = prefs.bassBoostEnabled
val nightModeActive = prefs.shouldEnableNightMode()

// Write settings
prefs.bassBoostEnabled = true
prefs.nightModeStartHour = 22
prefs.nightModeEndHour = 7

// Device-specific profiles
val deviceId = prefs.getDeviceProfileId("bluetooth", "AA:BB:CC:DD:EE:FF")
prefs.saveDeviceProfileMapping(deviceId, "living_room_headphones")
```

### 3. EnhancedAudioSettingsActivity

**Location:** `app/src/main/kotlin/tv/corebuilds/eq/EnhancedAudioSettingsActivity.kt`

**Purpose:** Full-featured settings screen for all enhanced audio options

**Sections:**
1. **Effect Chain** - Toggle individual effects
2. **Night Mode** - Enable/disable, configure auto-scheduling
3. **Content Type Detection** - Auto-switching preferences
4. **Device Settings** - HDMI warnings, device profiles
5. **Advanced** - Statistics display, priority settings

**Navigation:**
```kotlin
// From MainActivity
findViewById<Button>(R.id.btn_nav_enhanced_settings).setOnClickListener {
    startActivity(Intent(this, EnhancedAudioSettingsActivity::class.java))
}
```

## Integration Guide

### Step 1: Update EqService to Use EnhancedEffectChain

**File:** `app/src/main/kotlin/tv/corebuilds/eq/apply/EqService.kt`

**Changes:**

```kotlin
// Add imports
import tv.corebuilds.eq.ui.EnhancedAudioPrefs

// Add field
private var enhancedEffectChain: EnhancedEffectChain? = null
private var enhancedPrefs: EnhancedAudioPrefs? = null
private var deviceManager: ImprovedAudioDeviceManager? = null

// In onCreate()
override fun onCreate() {
    super.onCreate()
    
    // Existing code...
    
    // Initialize enhanced features
    enhancedPrefs = EnhancedAudioPrefs(this)
    deviceManager = ImprovedAudioDeviceManager(this)
    
    // Start device monitoring
    deviceManager?.startMonitoring { state ->
        handleDeviceChange(state)
    }
}

// Add device change handler
private fun handleDeviceChange(state: AudioDeviceState) {
    Log.d(TAG, "Device changed: ${state.deviceName} (${state.deviceType})")
    
    // Show HDMI passthrough warning if needed
    if (state.hdmiPassthrough && enhancedPrefs?.showHdmiWarnings == true) {
        showHdmiPassthroughWarning(state)
    }
    
    // Load device-specific profile
    val deviceId = state.device?.let { deviceManager?.getDeviceIdentifier(it) }
    val profileId = deviceId?.let { enhancedPrefs?.getDeviceProfileId(it) }
    if (profileId != null) {
        // Switch to device-specific profile
        store.chosenId = profileId
        applyAll()
    }
}

// Update createBestEffect to use EnhancedEffectChain
private fun createBestEffect(session: Int, profile: Profile): AppliedEffect {
    val prefs = enhancedPrefs ?: return createLegacyEffect(session, profile)
    
    // Create enhanced effect chain
    val chain = EnhancedEffectChain(session, packageName)
    
    // Detect content type
    val contentType = if (prefs.autoContentTypeEnabled) {
        detectContentType()
    } else {
        prefs.lastContentType
    }
    
    // Initialize with user preferences
    val success = chain.initialize(
        filters = profile.filters,
        preampGainDb = profile.preampGainDb,
        contentType = contentType,
        enableBassBoost = prefs.bassBoostEnabled,
        enableLoudnessEnhancer = prefs.loudnessEnhancerEnabled,
        enableDynamicsProcessing = prefs.dynamicsProcessingEnabled && Build.VERSION.SDK_INT >= 28,
        enableNightMode = prefs.shouldEnableNightMode()
    )
    
    if (!success) {
        Log.w(TAG, "Enhanced effect chain initialization failed; falling back to legacy")
        chain.release()
        return createLegacyEffect(session, profile)
    }
    
    enhancedEffectChain = chain
    
    // Convert to AppliedEffect format
    val status = chain.getStatus()
    return AppliedEffect(
        effect = chain.equalizer!!, // Primary effect
        engine = "EnhancedEffectChain",
        bands = status.bands
    )
}

// Add content type detection
private fun detectContentType(): ContentType {
    val activePackages = activeAppPackages()
    for (pkg in activePackages) {
        val detected = ContentTypeRegistry.detect(pkg)
        if (detected != ContentType.GENERAL) {
            return detected
        }
    }
    return ContentType.GENERAL
}

// Update applyAll to use enhanced features
private fun applyAll() {
    if (suspended) return
    
    val (profile, output, switched) = resolve()
    if (profile == null) {
        outputPaused = true
        setAllEnabled(false)
        report("No profile for current output", isError = false)
        return
    }
    
    outputPaused = false
    
    // Update enhanced effect chain if active
    enhancedEffectChain?.let { chain ->
        val prefs = enhancedPrefs ?: return@let
        
        // Update content type if auto-detection enabled
        if (prefs.autoContentTypeEnabled) {
            val contentType = detectContentType()
            chain.updateContentType(contentType)
        }
        
        // Update night mode
        chain.setNightMode(prefs.shouldEnableNightMode())
    }
    
    // Existing effect application logic...
}

// Add HDMI warning method
private fun showHdmiPassthroughWarning(state: AudioDeviceState) {
    // Send broadcast to show dialog in MainActivity
    sendBroadcast(
        Intent(ACTION_SHOW_HDMI_WARNING)
            .setPackage(packageName)
            .putExtra("device_name", state.deviceName)
            .putExtra("warnings", state.warnings.toTypedArray())
    )
}

// In onDestroy()
override fun onDestroy() {
    // Existing cleanup...
    
    // Cleanup enhanced features
    enhancedEffectChain?.release()
    enhancedEffectChain = null
    deviceManager?.stopMonitoring()
    deviceManager = null
    
    super.onDestroy()
}

// Add new action constant
companion object {
    const val ACTION_SHOW_HDMI_WARNING = "tv.corebuilds.eq.action.SHOW_HDMI_WARNING"
    // ... existing constants
}
```

### Step 2: Update MainActivity for Enhanced UI

**File:** `app/src/main/java/tv/corebuilds/eq/MainActivity.kt`

**Changes:**

```kotlin
// Add imports
import tv.corebuilds.eq.ui.EffectChainStatusCard
import tv.corebuilds.eq.apply.ImprovedAudioDeviceManager
import tv.corebuilds.eq.apply.AudioDeviceState

class MainActivity : TvActivity() {
    
    // Add fields
    private lateinit var effectChainStatusCard: EffectChainStatusCard
    private var deviceManager: ImprovedAudioDeviceManager? = null
    private var currentDeviceState: AudioDeviceState? = null
    
    // In onCreate()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Existing initialization...
        
        // Initialize enhanced UI
        effectChainStatusCard = findViewById(R.id.effect_chain_status)
        deviceManager = ImprovedAudioDeviceManager(this)
        
        // Add navigation to enhanced settings
        findViewById<Button>(R.id.btn_nav_enhanced_settings).setOnClickListener {
            startActivity(Intent(this, EnhancedAudioSettingsActivity::class.java))
        }
        
        // Start device monitoring
        deviceManager?.startMonitoring { state ->
            runOnUiThread {
                handleDeviceChange(state)
            }
        }
        
        // Register for HDMI warning broadcasts
        val filter = IntentFilter(EqService.ACTION_SHOW_HDMI_WARNING)
        ContextCompat.registerReceiver(this, hdmiWarningReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    
    // Add device change handler
    private fun handleDeviceChange(state: AudioDeviceState) {
        currentDeviceState = state
        
        // Update status card
        effectChainStatusCard.updateDevice(
            name = state.deviceName,
            type = state.deviceType,
            passthrough = state.hdmiPassthrough
        )
        
        // Show warning if needed
        if (state.hdmiPassthrough && !enhancedPrefs.hdmiPassthroughWarningShown) {
            showHdmiPassthroughDialog(state)
            enhancedPrefs.hdmiPassthroughWarningShown = true
        }
    }
    
    // Add HDMI warning dialog
    private fun showHdmiPassthroughDialog(state: AudioDeviceState) {
        AlertDialog.Builder(this)
            .setTitle("HDMI Passthrough Detected")
            .setMessage(
                "Your TV is using HDMI passthrough mode (bitstream). " +
                "Audio effects will be bypassed.\n\n" +
                "To enable EQ processing:\n" +
                "1. Go to TV Settings > Sound > Audio Output\n" +
                "2. Change Digital Audio Out from 'Bitstream' to 'PCM'\n" +
                "3. Restart Core EQ\n\n" +
                "You can disable this warning in Enhanced Audio Settings."
            )
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Don't Show Again") { dialog, _ ->
                enhancedPrefs.showHdmiWarnings = false
                dialog.dismiss()
            }
            .show()
    }
    
    // Add HDMI warning receiver
    private val hdmiWarningReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == EqService.ACTION_SHOW_HDMI_WARNING) {
                val deviceName = intent.getStringExtra("device_name") ?: "Unknown"
                val warnings = intent.getStringArrayExtra("warnings") ?: emptyArray()
                
                val state = AudioDeviceState(
                    device = null,
                    deviceType = "hdmi",
                    deviceName = deviceName,
                    supportsEq = false,
                    hdmiPassthrough = true,
                    warnings = warnings.toList()
                )
                
                showHdmiPassthroughDialog(state)
            }
        }
    }
    
    // Update refreshStatus to include effect chain info
    private fun refreshStatus() {
        // Existing status refresh...
        
        // Update effect chain status card
        if (EqService.running) {
            // Get effect chain status from service
            // Note: You'll need to add a method to EqService to expose this
            val chainStatus = EqService.getEffectChainStatus()
            effectChainStatusCard.updateStatus(chainStatus)
        } else {
            effectChainStatusCard.updateStatus(null)
        }
    }
    
    // In onResume()
    override fun onResume() {
        super.onResume()
        
        // Existing resume logic...
        
        // Refresh enhanced UI
        refreshStatus()
    }
    
    // In onDestroy()
    override fun onDestroy() {
        // Existing cleanup...
        
        // Cleanup enhanced features
        deviceManager?.stopMonitoring()
        deviceManager = null
        
        try {
            unregisterReceiver(hdmiWarningReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        
        super.onDestroy()
    }
}
```

### Step 3: Update Layout Files

**File:** `app/src/main/res/layout/activity_main.xml`

**Add to left rail (after existing navigation buttons):**

```xml
<Button
    android:id="@+id/btn_nav_enhanced_settings"
    android:layout_width="match_parent"
    android:layout_height="@dimen/cb_target_min"
    android:background="@drawable/bg_ghost"
    android:text="Enhanced Audio"
    android:textColor="@color/cb_ink"
    android:textSize="@dimen/cb_text_label"
    android:layout_marginTop="@dimen/cb_space_xs"
    android:focusable="true" />
```

**Add to main content area (after status section):**

```xml
<!-- Enhanced Effect Chain Status -->
<tv.corebuilds.eq.ui.EffectChainStatusCard
    android:id="@+id/effect_chain_status"
    android:layout_width="match_parent"
    android:layout_height="280dp"
    android:layout_marginTop="@dimen/cb_space_sm" />
```

### Step 4: Register New Activity

**File:** `app/src/main/AndroidManifest.xml`

**Add inside <application> tag:**

```xml
<activity
    android:name=".EnhancedAudioSettingsActivity"
    android:exported="false"
    android:label="Enhanced Audio Settings"
    android:theme="@style/Theme.CoreEq" />
```

### Step 5: Add String Resources

**File:** `app/src/main/res/values/strings.xml`

**Add:**

```xml
<!-- Enhanced Audio Settings -->
<string name="enhanced_audio_settings">Enhanced Audio Settings</string>
<string name="effect_chain">Effect Chain</string>
<string name="bass_boost">Bass Boost</string>
<string name="loudness_enhancer">Loudness Enhancer</string>
<string name="dynamics_processing">Dynamics Processing</string>
<string name="night_mode">Night Mode</string>
<string name="night_mode_auto">Automatic (time-based)</string>
<string name="content_type_detection">Content Type Detection</string>
<string name="auto_content_type">Automatic Content Type Switching</string>
<string name="device_settings">Device Settings</string>
<string name="show_hdmi_warnings">Show HDMI Passthrough Warnings</string>
<string name="advanced">Advanced</string>
<string name="show_advanced_stats">Show Advanced Statistics</string>
<string name="hdmi_passthrough_title">HDMI Passthrough Detected</string>
<string name="hdmi_passthrough_message">Your TV is using HDMI passthrough mode (bitstream). Audio effects will be bypassed.\n\nTo enable EQ processing:\n1. Go to TV Settings > Sound > Audio Output\n2. Change Digital Audio Out from \'Bitstream\' to \'PCM\'\n3. Restart Core EQ</string>
```

## UI/UX Design Principles

### 1. Progressive Disclosure
- Show essential info on main screen
- Advanced settings in dedicated screen
- Tooltips and descriptions for complex features

### 2. Visual Feedback
- Real-time status updates
- Color-coded effect badges (green = active, gray = inactive)
- Animated indicators for live status
- Toast notifications for user actions

### 3. Error Handling
- Clear error messages with actionable steps
- Graceful fallback when features unavailable
- Non-intrusive warnings (can be dismissed/disabled)

### 4. Accessibility
- Large touch targets (48dp minimum)
- High contrast text
- Clear focus indicators for TV remote navigation
- Descriptive labels and content descriptions

### 5. Performance
- Lazy loading of advanced features
- Debounced updates to prevent UI spam
- Efficient canvas rendering for status cards
- Background processing for device detection

## Testing Checklist

### UI Testing
- [ ] EffectChainStatusCard renders correctly with all effect combinations
- [ ] Settings screen saves and loads preferences correctly
- [ ] Night mode auto-scheduling works with time boundaries
- [ ] HDMI warning dialog appears and can be dismissed
- [ ] Device change updates status card in real-time
- [ ] Content type switching updates effect chain

### Integration Testing
- [ ] EnhancedEffectChain initializes with user preferences
- [ ] Effect chain falls back to legacy on failure
- [ ] Device-specific profiles load on device change
- [ ] Night mode activates/deactivates based on time
- [ ] Content type auto-detection works with streaming apps
- [ ] Settings changes trigger service reconfiguration

### Performance Testing
- [ ] Status card updates at 60 FPS
- [ ] Device detection doesn't block main thread
- [ ] Effect chain initialization completes in <500ms
- [ ] Memory usage stays under 50MB with all features enabled

## Future Enhancements

### Planned for v1.3.0
1. **Effect Chain Visualizer** - Real-time frequency response graph
2. **Audio Analysis Dashboard** - Spectrum analyzer, THD meter
3. **Profile Comparison** - Side-by-side A/B testing
4. **Custom Effect Presets** - Save/load user configurations
5. **Voice Control** - "Hey Google, enable night mode"

### Planned for v2.0.0
1. **Material Design 3** - Dynamic color, updated components
2. **Gesture Controls** - Swipe to adjust bass/treble
3. **Dark/Light Theme** - System theme following
4. **Widget Support** - Home screen quick controls
5. **Watch Companion** - Wear OS remote control

## Support

For UI/UX questions or integration issues:
- Review this guide's integration steps
- Check the enhanced features documentation
- Run the UI test suite
- Test on device with layout inspector

## Conclusion

The enhanced UI provides a modern, intuitive interface for Core EQ's advanced audio processing features. All components follow Material Design principles and integrate seamlessly with the existing codebase.
