# Alternative EQ Application Methods

## Current Implementation (Core EQ v1.1.1)

Core EQ currently uses three primary methods to apply equalization:

1. **Global Output Mix (Session 0)** - Deprecated but functional on some devices
2. **Per-App Audio Sessions** - Via `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcasts
3. **DUMP Discovery** - Reading `dumpsys media.audio_flinger` and `dumpsys audio` to find active sessions

## Alternative Application Methods

### 1. AudioPolicyService Integration (System-Level)

**Description:** AudioPolicyService is the policy maker for all audio routing and effects in Android. It works with AudioFlinger to manage audio streams.

**How it works:**
- AudioPolicyService loads default AudioEffects from configuration files
- Effects are applied at the policy level before reaching AudioFlinger
- Can apply effects to specific audio streams, devices, or use cases

**Implementation approach:**
```xml
<!-- audio_effects.xml configuration -->
<libraries>
  <library name="coreeq" path="libcoreeq.so"/>
</libraries>
<effects>
  <effect name="coreeq_equalizer" library="coreeq" uuid="..."/>
</effects>
<preprocess>
  <stream type="music">
    <apply effect="coreeq_equalizer"/>
  </stream>
</preprocess>
```

**Pros:**
- System-wide application without session hunting
- Works with all audio sources automatically
- Lower latency than app-level effects

**Cons:**
- Requires system-level access or ROM modification
- Not available to third-party apps
- Device-specific configuration

**Status:** Not feasible for Core EQ (requires root/system app)

---

### 2. ExoPlayer Custom AudioProcessor

**Description:** For apps using ExoPlayer (Media3), implement a custom `AudioProcessor` that applies EQ directly to audio buffers.

**Implementation:**
```kotlin
class EqualizerAudioProcessor : BaseAudioProcessor() {
    private lateinit var eqFilters: List<PeakingFilter>
    
    override fun configure(sampleRateHz: Int, channelCount: Int, encoding: Int): Boolean {
        setInputFormat(sampleRateHz, channelCount, encoding)
        return true
    }
    
    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val outputBuffer = replaceOutputBuffer(limit - position)
        
        // Apply peaking filters to each sample
        for (i in position until limit step (2 * channelCount)) {
            for (ch in 0 until channelCount) {
                var sample = inputBuffer.getShort(i + 2 * ch).toDouble()
                
                // Apply each peaking filter
                for (filter in eqFilters) {
                    sample = applyPeakingFilter(sample, filter, sampleRateHz)
                }
                
                outputBuffer.putShort(sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
        }
        
        inputBuffer.position(limit)
    }
}
```

**Integration with ExoPlayer:**
```kotlin
val renderersFactory = object : DefaultRenderersFactory(context) {
    override fun buildAudioProcessors(): Array<AudioProcessor> {
        return arrayOf(EqualizerAudioProcessor())
    }
}
val player = ExoPlayer.Builder(context)
    .setRenderersFactory(renderersFactory)
    .build()
```

**Pros:**
- Works with any ExoPlayer-based app (YouTube, Netflix, etc.)
- Full control over DSP processing
- Can apply room correction curves directly
- No permission issues

**Cons:**
- Only works with ExoPlayer apps (not all media players use it)
- Requires app integration (can't be system-wide)
- Higher CPU usage than hardware effects

**Status:** Feasible as a companion SDK/library for media player apps

---

### 3. Oboe + Custom DSP (Low-Latency Path)

**Description:** Use Oboe (Google's high-performance audio library) with custom DSP for low-latency equalization.

**Implementation:**
```cpp
class EqualizerStream : public oboe::AudioStreamCallback {
public:
    oboe::Result openStream() {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setSharingMode(oboe::SharingMode::Exclusive)
               ->setFormat(oboe::AudioFormat::Float)
               ->setChannelCount(oboe::ChannelCount::Stereo)
               ->setSampleRate(48000)
               ->setCallback(this);
        return builder.openStream(mStream);
    }
    
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) override {
        
        float *outputBuffer = static_cast<float*>(audioData);
        
        // Apply peaking filters to output buffer
        for (int i = 0; i < numFrames; i++) {
            for (int ch = 0; ch < 2; ch++) {
                float sample = outputBuffer[i * 2 + ch];
                sample = applyRoomCorrection(sample, ch);
                outputBuffer[i * 2 + ch] = sample;
            }
        }
        
        return oboe::DataCallbackResult::Continue;
    }
};
```

**Pros:**
- Lowest possible latency (<10ms)
- Full DSP control
- Can implement advanced room correction (FIR filters, convolution)

**Cons:**
- Requires Oboe integration (not system-wide)
- Complex implementation
- Only works for apps that use Oboe

**Status:** Feasible for gaming/music apps that need low-latency audio

---

### 4. MediaSession + AudioFocus Monitoring

**Description:** Monitor active MediaSessions and AudioFocus to detect which app is playing, then attach effects to its audio session.

**Implementation:**
```kotlin
class MediaSessionMonitor(context: Context) {
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    fun startMonitoring() {
        // Monitor active media sessions
        val sessions = mediaSessionManager.getActiveSessions(
            ComponentName(context, MediaBrowserService::class.java)
        )
        
        for (session in sessions) {
            val controller = session.controller
            if (controller.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
                // Get audio session ID from the playing app
                val audioSessionId = getAudioSessionFromPackage(session.packageName)
                if (audioSessionId != null) {
                    attachEqualizer(audioSessionId)
                }
            }
        }
    }
    
    private fun getAudioSessionFromPackage(packageName: String): Int? {
        // Use AudioManager to find audio sessions for this package
        val playbackConfigs = audioManager.activePlaybackConfigurations
        for (config in playbackConfigs) {
            if (config.clientAudioSessionId != AudioEffect.ERROR && 
                config.clientPackageName == packageName) {
                return config.clientAudioSessionId
            }
        }
        return null
    }
}
```

**Pros:**
- Works with apps that use MediaSession API
- Can detect playback state and content type
- No DUMP permission required

**Cons:**
- Requires MediaBrowserService (special permission)
- Not all apps use MediaSession
- Audio session ID may not be exposed

**Status:** Partially feasible (works with well-behaved media apps)

---

### 5. Screen Recording + Audio Capture (PhantomAmp Method)

**Description:** Use Android's screen recording API to capture device audio, apply effects, and play it back through a virtual audio device.

**Implementation:**
```kotlin
class AudioCaptureService : Service() {
    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var audioRecord: AudioRecord
    private lateinit var audioTrack: AudioTrack
    
    override fun onCreate() {
        super.onCreate()
        
        // Request screen recording permission (includes audio)
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE)
    }
    
    private fun startAudioCapture() {
        // Capture device audio
        audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.REMOTE_SUBMIX)
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(48000)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .build())
            .setBufferSizeInBytes(BUFFER_SIZE)
            .build()
        
        // Apply EQ and play back
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(audioRecord.format)
            .setBufferSizeInBytes(BUFFER_SIZE)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        // Process audio in real-time
        thread {
            val buffer = FloatArray(BUFFER_SIZE / 4)
            while (isRunning) {
                audioRecord.read(buffer, 0, buffer.size)
                applyRoomCorrection(buffer)
                audioTrack.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
        }
    }
}
```

**Pros:**
- Works with ANY app (captures all device audio)
- No permission issues (uses standard APIs)
- Can apply any DSP processing

**Cons:**
- Requires Android 10+ (API 29)
- Requires screen recording permission (user must grant)
- Blocks actual screen recording while active
- Higher latency (audio capture + processing + playback)
- Higher CPU/battery usage

**Status:** Feasible but invasive (requires user to grant screen recording permission)

---

### 6. AccessibilityService Audio Monitoring

**Description:** Use AccessibilityService to monitor audio events and detect which app is playing audio.

**Implementation:**
```kotlin
class AudioAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName.toString()
            // Check if this package is playing audio
            checkAudioPlayback(packageName)
        }
    }
    
    private fun checkAudioPlayback(packageName: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val playbackConfigs = audioManager.activePlaybackConfigurations
        
        for (config in playbackConfigs) {
            if (config.clientPackageName == packageName && 
                config.clientAudioSessionId != AudioEffect.ERROR) {
                attachEqualizer(config.clientAudioSessionId)
            }
        }
    }
}
```

**Pros:**
- No DUMP permission required
- Can detect app switches
- Works on all Android versions

**Cons:**
- Requires AccessibilityService permission (user must enable in Settings)
- Privacy concerns (accessibility can see all UI events)
- Not all devices allow third-party accessibility services
- May be killed by battery optimization

**Status:** Feasible but requires user to enable accessibility service

---

### 7. NotificationListenerService + Audio Detection

**Description:** Monitor media notifications to detect which app is playing, then use AudioManager to find its audio session.

**Implementation:**
```kotlin
class MediaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        
        // Check if this is a media notification
        if (notification.category == Notification.CATEGORY_TRANSPORT) {
            val packageName = sbn.packageName
            val isPlaying = extras.getBoolean(Notification.EXTRA_MEDIA_SESSION_PLAYING, false)
            
            if (isPlaying) {
                // Find audio session for this package
                val audioSessionId = findAudioSession(packageName)
                if (audioSessionId != null) {
                    attachEqualizer(audioSessionId)
                }
            }
        }
    }
    
    private fun findAudioSession(packageName: String): Int? {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val playbackConfigs = audioManager.activePlaybackConfigurations
        
        for (config in playbackConfigs) {
            if (config.clientPackageName == packageName) {
                return config.clientAudioSessionId
            }
        }
        return null
    }
}
```

**Pros:**
- Works with apps that show media notifications
- No special permissions beyond notification access
- Can detect playback state

**Cons:**
- Only works with apps that show media notifications
- User must grant notification access
- May miss apps that don't use MediaSession

**Status:** Partially feasible (good for music apps, not for all media)

---

## Recommended Approach for Core EQ

### Primary: Enhance Current Implementation
1. **Keep Session 0 (Global Mix)** - Works on many Android TV devices
2. **Keep Broadcast Receiver** - Standard Android API for session detection
3. **Keep DUMP Discovery** - Most reliable for finding hidden sessions

### Secondary: Add ExoPlayer SDK
Create a companion library that media player apps can integrate:
```kotlin
// In the media player app
val eqProcessor = CoreEQProcessor(roomCorrectionProfile)
val renderersFactory = DefaultRenderersFactory(context)
    .setAudioProcessors(arrayOf(eqProcessor))
```

### Tertiary: AccessibilityService Fallback
For devices where DUMP is denied and broadcasts don't work:
```kotlin
if (!DumpsysDiscovery.hasGrant(context) && !hasBroadcastSupport()) {
    // Prompt user to enable accessibility service
    showAccessibilityServiceDialog()
}
```

## DUMP Permission ADB Commands

### For Core EQ (Production)
```bash
adb shell pm grant tv.corebuilds.eq android.permission.DUMP
```

### For Core EQ (Debug/Test Build)
```bash
adb shell pm grant tv.corebuilds.eq.debug android.permission.DUMP
```

### For Wavelet (Reference)
```bash
adb shell pm grant com.pittvandewitt.wavelet android.permission.DUMP
```

### For Poweramp Equalizer (Reference)
```bash
adb shell pm grant com.maxmpz.equalizer android.permission.DUMP
```

### Alternative: Using LADB (Local ADB)
If you don't have a PC, use the LADB app from Play Store:
1. Install LADB
2. Enable Developer Options → Wireless Debugging
3. Pair LADB using the pairing code
4. Run the command in LADB:
```bash
pm grant tv.corebuilds.eq android.permission.DUMP
```

### Alternative: Using WebADB (Chrome Browser)
1. Open https://app.webadb.com/#/shell in Chrome
2. Connect your Android TV device
3. Choose "Interactive Shell"
4. Run:
```bash
pm grant tv.corebuilds.eq android.permission.DUMP
```

## Verification

After granting DUMP permission, verify it worked:
```bash
adb shell dumpsys package tv.corebuilds.eq | grep "android.permission.DUMP"
```

You should see:
```
android.permission.DUMP: granted=true
```

## Troubleshooting

### "Permission denial" error
- Some devices/ROMs block DUMP permission for third-party apps
- Try disabling "Permission monitoring" in Developer Options
- Some Samsung devices require additional steps

### "Can't find service" error
- The audio service may not be running
- Try playing audio first, then grant permission
- Restart the device and try again

### Permission doesn't persist after reboot
- Some devices revoke DUMP permission on reboot
- Re-run the ADB command after each reboot
- Consider using AccessibilityService as a fallback

## References

1. [Android AudioEffect API](https://developer.android.com/reference/android/media/audiofx/AudioEffect)
2. [Wavelet DUMP Permission Guide](https://xdaforums.com/t/app-9-0-wavelet-headphone-specific-equalization.4097957/)
3. [ExoPlayer Custom AudioProcessor](https://stackoverflow.com/questions/58857797/exoplayer-custom-audioprocessor-equalizer)
4. [Oboe Audio Effects](https://github.com/google/oboe/wiki/TechNote_Effects)
5. [AudioPolicyService Documentation](http://strayinsights.blogspot.com/2018/05/android-audio-tutorial-part-six.html)
6. [PhantomAmp Screen Recording Method](https://www.esper.io/blog/android-equalizer-apps-inconsistent)
