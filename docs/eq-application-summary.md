# EQ Application Methods - Summary & ADB Commands

## Quick Reference: ADB Commands for DUMP Permission

### Core EQ Production Build
```bash
adb shell pm grant tv.corebuilds.eq android.permission.DUMP
```

### Core EQ Debug/Test Build
```bash
adb shell pm grant tv.corebuilds.eq.debug android.permission.DUMP
```

### Alternative Methods (No PC Required)

**Using LADB App (Local ADB):**
1. Install LADB from Play Store
2. Enable Developer Options → Wireless Debugging
3. Pair using the code shown
4. Run: `pm grant tv.corebuilds.eq android.permission.DUMP`

**Using WebADB (Chrome Browser):**
1. Visit https://app.webadb.com/#/shell
2. Connect your device
3. Run: `pm grant tv.corebuilds.eq android.permission.DUMP`

### Verify Permission Granted
```bash
adb shell dumpsys package tv.corebuilds.eq | grep "android.permission.DUMP"
```
Expected output: `android.permission.DUMP: granted=true`

---

## Current Core EQ Methods (v1.1.1)

| Method | Reliability | Coverage | Requirements |
|--------|-------------|----------|--------------|
| **Global Mix (Session 0)** | Medium | Most apps | Works on many Android TV devices |
| **Broadcast Receiver** | High | Compliant apps | Apps must broadcast session ID |
| **DUMP Discovery** | High | Most apps | Requires DUMP permission via ADB |

---

## Alternative Methods Researched

### 1. AudioPolicyService Integration ⚠️
**System-level audio effects configuration**
- **Pros:** Automatic, works with all audio, low latency
- **Cons:** Requires root/system app, not available to third-party apps
- **Status:** ❌ Not feasible for Core EQ

### 2. ExoPlayer Custom AudioProcessor ✅
**DSP processing for ExoPlayer-based apps**
- **Pros:** Full DSP control, works with YouTube/Netflix/etc, no permissions needed
- **Cons:** Only works with ExoPlayer apps, requires app integration
- **Status:** ✅ Feasible as companion SDK

**Example Integration:**
```kotlin
val eqProcessor = CoreEQProcessor(roomCorrectionProfile)
val player = ExoPlayer.Builder(context)
    .setRenderersFactory(
        DefaultRenderersFactory(context)
            .setAudioProcessors(arrayOf(eqProcessor))
    )
    .build()
```

### 3. Oboe + Custom DSP ✅
**Low-latency audio processing for games/music apps**
- **Pros:** <10ms latency, full DSP control, advanced room correction
- **Cons:** Requires Oboe integration, complex implementation
- **Status:** ✅ Feasible for gaming/music apps

### 4. MediaSession + AudioFocus Monitoring ⚠️
**Detect playing apps via MediaSession API**
- **Pros:** Works with well-behaved media apps, detects content type
- **Cons:** Requires MediaBrowserService, not all apps use MediaSession
- **Status:** ⚠️ Partially feasible

### 5. Screen Recording + Audio Capture ⚠️
**Capture all device audio via MediaProjection API**
- **Pros:** Works with ANY app, captures all audio
- **Cons:** Requires Android 10+, blocks screen recording, higher latency
- **Status:** ⚠️ Feasible but invasive

### 6. AccessibilityService Audio Monitoring ⚠️
**Monitor app switches to detect audio playback**
- **Pros:** No DUMP permission needed, works on all Android versions
- **Cons:** Privacy concerns, requires user to enable accessibility
- **Status:** ⚠️ Feasible fallback option

### 7. NotificationListenerService ⚠️
**Monitor media notifications to detect playback**
- **Pros:** Works with music apps, no special permissions
- **Cons:** Only works with apps showing media notifications
- **Status:** ⚠️ Partially feasible

---

## Recommended Strategy for Core EQ

### Priority 1: Enhance Current Implementation
- **Keep Session 0** - Works on many Android TV devices
- **Keep Broadcast Receiver** - Standard Android API
- **Keep DUMP Discovery** - Most reliable method

**Improvement:** Add automatic retry logic and better error messages when DUMP is denied.

### Priority 2: ExoPlayer SDK (Companion Library)
Create a library that media player apps can integrate for native EQ support:
```kotlin
// Media player app adds this dependency
implementation("tv.corebuilds:eq-sdk:1.0.0")

// And applies room correction
val processor = CoreEQProcessor(profile)
exoPlayer.setAudioProcessors(arrayOf(processor))
```

**Target apps:** YouTube, Netflix, Disney+, Plex, Kodi (all use ExoPlayer/Media3)

### Priority 3: AccessibilityService Fallback
For devices where DUMP is denied:
```kotlin
if (!DumpsysDiscovery.hasGrant(context)) {
    // Show dialog explaining accessibility service option
    showAccessibilityServiceDialog()
}
```

**User flow:**
1. Detect DUMP permission denied
2. Show dialog: "Core EQ can also work via Accessibility Service. Enable in Settings?"
3. Guide user to Settings → Accessibility → Core EQ → Enable
4. Use AccessibilityService to monitor app switches

### Priority 4: OEM Partnership (Long-term)
Work with Android TV manufacturers to include Core EQ as a system app:
- Pre-installed with DUMP permission already granted
- AudioPolicyService integration for automatic application
- System-level room correction wizard

**Target OEMs:** Sony (Android TV), TCL, Hisense, Nvidia Shield

---

## Why DUMP Permission is Needed

Android's audio system doesn't provide a public API to:
1. List all active audio sessions
2. Get the audio session ID for a specific app
3. Attach effects to apps that don't broadcast their session ID

The DUMP permission allows Core EQ to read system dumps (`dumpsys media.audio_flinger`) to discover audio sessions that apps don't announce publicly.

**Without DUMP:**
- ✅ Works with apps that broadcast session ID (Spotify, Tidal, etc.)
- ❌ Doesn't work with apps that don't broadcast (some YouTube versions, games, etc.)
- ❌ Can't detect app switches automatically

**With DUMP:**
- ✅ Works with most apps (discovers sessions via dumpsys)
- ✅ Automatic app detection and switching
- ✅ Content-type-aware EQ optimization

---

## Troubleshooting DUMP Permission

### "Permission denial" Error
**Cause:** Some devices/ROMs block DUMP for third-party apps

**Solutions:**
1. Try disabling "Permission monitoring" in Developer Options
2. Use LADB instead of PC ADB
3. Some Samsung devices require additional steps (contact support)

### "Can't find service" Error
**Cause:** Audio service not running or not accessible

**Solutions:**
1. Play some audio first, then grant permission
2. Restart the device
3. Check if the device supports dumpsys (some custom ROMs disable it)

### Permission Doesn't Persist After Reboot
**Cause:** Some devices revoke DUMP permission on reboot

**Solutions:**
1. Re-run ADB command after each reboot
2. Use AccessibilityService as a fallback (doesn't require reboot)
3. Consider rooting the device (advanced users only)

---

## Comparison with Other EQ Apps

| App | Method | DUMP Required | Coverage |
|-----|--------|---------------|----------|
| **Core EQ** | Session 0 + Broadcast + DUMP | Optional (recommended) | High |
| **Wavelet** | Broadcast + DUMP | Required | High |
| **Poweramp EQ** | Broadcast + DUMP | Required | High |
| **Flat Equalizer** | Broadcast only | No | Medium |
| **Equalizer FX** | Session 0 only | No | Low |

---

## Future Research Directions

1. **Android 15 Audio Effects API** - Check if new APIs provide better session discovery
2. **Media3 AudioProcessor** - Native integration with Media3 (ExoPlayer successor)
3. **AIDL Audio Service** - Direct communication with AudioFlinger (requires system signature)
4. **OEM-Specific APIs** - Sony, Samsung, LG may have proprietary audio effect APIs

---

## References

1. [Android AudioEffect API](https://developer.android.com/reference/android/media/audiofx/AudioEffect)
2. [Wavelet DUMP Permission Guide](https://xdaforums.com/t/app-9-0-wavelet-headphone-specific-equalization.4097957/)
3. [ExoPlayer Custom AudioProcessor](https://stackoverflow.com/questions/58857797/exoplayer-custom-audioprocessor-equalizer)
4. [Why Many Android Equalizer Apps Don't Work](https://www.esper.io/blog/android-equalizer-apps-inconsistent)
5. [AudioPolicyService Architecture](http://strayinsights.blogspot.com/2018/05/android-audio-tutorial-part-six.html)

---

**Document Version:** 1.0  
**Last Updated:** 2026-10-06  
**Author:** Core EQ Development Team
