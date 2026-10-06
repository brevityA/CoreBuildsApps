# Core EQ Advanced Research & Future Roadmap

**Research Date:** October 6, 2026  
**Purpose:** Identify advanced features, optimizations, and future-proofing opportunities for Core EQ

---

## Executive Summary

This research document identifies **critical improvements** and **future features** for Core EQ based on:
- Modern Android audio optimization techniques
- AI/ML room correction advancements
- Competitive analysis of leading EQ apps
- Emerging audio technologies (Auracast, spatial audio)

**Key Findings:**
1. **Performance:** Current implementation can achieve 5-10ms latency with NEON SIMD optimization (vs. 20-35ms currently)
2. **AI Room Correction:** Machine learning can achieve 0.3dB accuracy vs. 2-5dB with traditional DSP
3. **Competitive Gap:** Missing parametric EQ, AutoEQ profiles, and per-output memory
4. **Future-Proofing:** Auracast support will be essential by 2027

---

## 1. Performance Optimizations

### 1.1 Low-Latency Audio Processing

**Current State:**
- Using Android AudioEffect API (Equalizer, BassBoost, etc.)
- Latency: 20-35ms with full effect chain
- CPU usage: ~5% on modern devices

**Research Findings:**

#### ARM NEON SIMD Optimization
- **Impact:** 4-6x performance improvement
- **Latency reduction:** From 20-35ms to 5-10ms
- **Implementation:** NDK native code with vectorized FFT

**Architecture:**
```
[Oboe/AAudio Stream] → [Lock-Free Ring Buffer] → [NEON DSP Kernel] → [Output Buffer]
     ↑ callback thread        ↑ wait-free SPSC         ↑ vectorized
     (real-time priority)      (no mutex, no alloc)      (4x throughput)
```

**Benchmark Results:**
| Pipeline | Pixel 8 | Galaxy S24 | Pixel 7a |
|----------|---------|------------|----------|
| AudioTrack (Java) | 32ms | 28ms | 41ms |
| Oboe + scalar C++ | 11ms | 9ms | 14ms |
| Oboe + NEON FFT | 7ms | 6ms | 9ms |
| **Oboe + NEON + Exclusive** | **5ms** | **4ms** | **8ms** |

**Recommendation:** ⚠️ **HIGH PRIORITY**
- Implement Oboe with SharingMode::Exclusive for direct HAL access
- Add NEON-vectorized FFT for room correction filters
- Use lock-free ring buffers for audio callback thread
- **Expected impact:** 70% latency reduction, better battery life

#### In-Process Audio Decoders
- **Impact:** 40% latency reduction for media playback
- **Benefit:** Eliminates IPC overhead and scheduling contention
- **Implementation:** Use `c2.android.inproc.aac.decoder` for AAC, `c2.android.inproc.opus.decoder` for Opus

**Code Example:**
```kotlin
val INPROC_AAC_CODEC = "c2.android.inproc.aac.decoder"

fun createAudioDecoder(): MediaCodec {
    return try {
        MediaCodec.createByCodecName(INPROC_AAC_CODEC)
    } catch (e: IllegalArgumentException) {
        MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    }
}
```

**Recommendation:** ✅ **MEDIUM PRIORITY**
- Opt-in to in-process decoders for media playback scenarios
- Reduces CPU usage and extends battery life
- Will become default in future Android versions

### 1.2 Oboe Best Practices Checklist

**Current Implementation:** Using AudioEffect API (system-level effects)

**Recommended Configuration for Low Latency:**
```cpp
oboe::AudioStreamBuilder builder;
builder.setDirection(oboe::Direction::Output)
       ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
       ->setSharingMode(oboe::SharingMode::Exclusive)
       ->setFormat(oboe::AudioFormat::Float)
       ->setChannelCount(oboe::ChannelCount::Stereo)
       ->setFramesPerBurst(48)  // minimize buffer depth
       ->setCallback(this);
```

**Critical Rules:**
1. ✅ Use `PerformanceMode::LowLatency`
2. ✅ Request `SharingMode::Exclusive` for direct HAL access
3. ✅ Use 48000 Hz or Oboe's sample rate converter
4. ✅ Set usage to `AAUDIO_USAGE_GAME` or `AAUDIO_USAGE_MEDIA`
5. ✅ Use data callbacks (not blocking writes)
6. ✅ Avoid blocking operations in callback (no allocation, I/O, mutex)
7. ✅ Tune buffer size to 2x burst size (double buffering)

**Latency Impact:**
| Configuration | Latency |
|---------------|---------|
| Follow all recommendations | 20ms |
| Performance mode not low latency | 205ms |
| Not EXCLUSIVE (SHARED) | 26ms |
| 44100 Hz (AAudio) | 160ms |
| Buffer size set to maximum | 53ms |

**Recommendation:** ⚠️ **HIGH PRIORITY for v2.0**
- Migrate to Oboe for custom DSP processing
- Keep AudioEffect API as fallback for compatibility
- Implement hybrid approach: Oboe for advanced features, AudioEffect for basic EQ

---

## 2. AI/ML Room Correction

### 2.1 Current State of AI Room Correction

**Traditional DSP (Current Core EQ):**
- Static FIR/IIR filters
- One-time calibration
- 2-5dB accuracy
- Manual tuning required

**AI-Driven Systems (Industry Leaders):**
- Adaptive neural networks
- Continuous optimization
- 0.3dB accuracy
- Automatic tuning

**Market Leaders:**
1. **Dirac Live** - Time-domain correction, high-end studios
2. **Sonarworks SoundID Reference** - Personalized calibration
3. **IK Multimedia ARC 4** - Multi-point measurement
4. **Genelec GLM AutoCal 2** - Smart speaker integration
5. **Neumann MA 1** - Neural classification of room acoustics

### 2.2 AI Room Correction Architecture

**How AI "Knows" Your Room:**
```
1. Multi-point Audio Capture
   ↓
2. Neural Classification of Room Acoustics
   ↓
3. Predictive EQ Curve Generation
   ↓
4. A/B Validation for Real-World Accuracy
   ↓
5. Continuous Optimization (adaptive learning)
```

**Key Technologies:**

#### Deep Neural Networks
- Trained on 1M+ room profiles
- Identify standing waves, reflections, cancellations
- Generate inverse filters automatically
- Adapt to furniture movement, temperature changes

#### Multi-Mic Sensor Fusion
- Combine multiple microphones with environmental sensors
- Track vibration, temperature, humidity
- Use Kalman filtering for data fusion
- Create 3D acoustic map of room

#### Real-Time Adaptation
- Monitor and refine corrections over time
- Adjust to real-world changes
- Maintain accuracy across listening positions

### 2.3 Implementation Roadmap for Core EQ

**Phase 1: Enhanced Measurement (v1.3)**
- Multi-point measurement (5-9 positions)
- Environmental sensor integration (temperature, humidity)
- Improved RT60 calculation
- **Accuracy target:** 1.5dB

**Phase 2: ML-Assisted Correction (v1.5)**
- Train neural network on existing measurements
- Predict optimal filters from limited data
- Reduce calibration time from 5min to 30sec
- **Accuracy target:** 0.8dB

**Phase 3: Adaptive AI (v2.0)**
- Continuous learning during playback
- Adapt to room changes automatically
- Personalized sound profiles per user
- **Accuracy target:** 0.3dB

**Technical Requirements:**
- TensorFlow Lite for on-device ML
- 50MB model size (acceptable for TV apps)
- Inference time <10ms per frame
- Training data: 10,000+ room profiles (can start with 1,000)

**Recommendation:** ⚠️ **HIGH PRIORITY for v1.5+**
- Start with multi-point measurement (Phase 1)
- Collect anonymized room data for ML training
- Partner with audio research labs for dataset access
- Implement TensorFlow Lite for on-device inference

---

## 3. Competitive Analysis

### 3.1 Feature Comparison

| Feature | Core EQ v1.2 | Wavelet | Poweramp EQ | XEQ |
|---------|--------------|---------|-------------|-----|
| **Bands** | 5 (fixed) | 9 (graphic) | 5-32 (parametric) | 10 (graphic) |
| **Parametric EQ** | ❌ | ❌ | ✅ | ❌ |
| **AutoEQ Profiles** | ❌ | ✅ (5000+) | ❌ | ❌ |
| **Per-Output Memory** | ❌ | ❌ | ✅ | ✅ |
| **Multiband Compressor** | ❌ | ❌ | ❌ | ✅ |
| **Limiter** | ✅ | ✅ | ✅ | ✅ |
| **Bass Boost** | ✅ | ✅ | ✅ | ✅ |
| **Loudness Enhancer** | ✅ | ❌ | ❌ | ❌ |
| **Dynamics Processing** | ✅ | ❌ | ❌ | ❌ |
| **Night Mode** | ✅ | ❌ | ❌ | ❌ |
| **Content Type Detection** | ✅ | ❌ | ❌ | ❌ |
| **Room Correction** | ✅ | ❌ | ❌ | ❌ |
| **Price** | Free | Free + $5 unlock | $7 unlock | Free |
| **Rating** | N/A (new) | 3.8★ | 4.3★ | 4.5★ |

### 3.2 Competitive Advantages

**Core EQ Strengths:**
- ✅ Room correction (unique feature)
- ✅ Multi-effect chain (Bass + Loudness + Dynamics)
- ✅ Night mode (unique feature)
- ✅ Content type detection (unique feature)
- ✅ TV-optimized (Android TV focus)

**Core EQ Weaknesses:**
- ❌ Only 5 bands (competitors have 9-32)
- ❌ No parametric EQ
- ❌ No AutoEQ headphone profiles
- ❌ No per-output memory
- ❌ Limited preset library

### 3.3 Critical Missing Features

**Priority 1: Parametric EQ**
- **Why:** Professional users need precise control
- **Implementation:** Add 10-band parametric mode
- **Effort:** Medium (2-3 weeks)
- **Impact:** High (attracts audiophiles)

**Priority 2: AutoEQ Integration**
- **Why:** 5000+ headphone correction profiles
- **Implementation:** Import AutoEQ database
- **Effort:** Low (1 week)
- **Impact:** Very High (mass adoption)

**Priority 3: Per-Output Memory**
- **Why:** Different EQ for headphones vs. speakers
- **Implementation:** Save profiles per device type
- **Effort:** Low (1 week)
- **Impact:** High (user convenience)

**Priority 4: Expanded Preset Library**
- **Why:** Users want ready-made curves
- **Implementation:** Add 50+ presets (genres, headphones, speakers)
- **Effort:** Low (1 week)
- **Impact:** Medium (onboarding)

**Recommendation:** ⚠️ **IMMEDIATE ACTION**
- Implement AutoEQ integration (v1.3)
- Add parametric EQ mode (v1.4)
- Add per-output memory (v1.4)
- Expand preset library (v1.3)

---

## 4. Emerging Technologies

### 4.1 Auracast (Bluetooth LE Audio)

**What is Auracast?**
- One-to-many audio broadcasting
- Built on Bluetooth LE Audio (5.2+)
- Unlimited receivers per transmitter
- Public or private (encrypted) streams

**Current Adoption (2026):**
- Samsung Neo QLED TVs: ✅ Native support
- Galaxy S23+, Pixel 8+: ✅ Broadcast capable
- Most other TVs: ❌ No native support
- Aftermarket transmitters: ✅ Available

**Use Cases for Core EQ:**
1. **Multi-Listener TV** - Family watches with individual EQ
2. **Public Venues** - Gym, airport, museum audio
3. **Hearing Assistance** - Accessibility feature
4. **Party Mode** - Share music with friends

**Technical Requirements:**
- Bluetooth 5.2+ hardware
- Android 14+ for full API support
- LC3 codec implementation
- Auracast Assistant integration

**Implementation Roadmap:**
- **v1.5:** Detect Auracast-capable devices
- **v2.0:** Implement Auracast transmitter mode
- **v2.5:** Multi-listener EQ profiles

**Recommendation:** ✅ **MEDIUM PRIORITY for v2.0+**
- Monitor adoption (expected mainstream by 2027)
- Implement detection first, then transmission
- Partner with Auracast transmitter manufacturers

### 4.2 Spatial Audio / 3D Audio

**Technologies:**
- **Dolby Atmos** - Object-based spatial audio
- **Sony 360 Reality Audio** - Immersive music
- **Apple Spatial Audio** - Head tracking
- **Android Spatializer API** (API 33+)

**Current State:**
- Requires compatible content (movies, music)
- Head tracking for headphones
- Virtualization for stereo speakers

**Implementation for Core EQ:**
- Use `Spatializer` API for virtual surround
- Integrate with Dolby Atmos passthrough
- Add "Cinema Mode" for spatial enhancement
- Preserve spatial cues in room correction

**Recommendation:** ✅ **MEDIUM PRIORITY for v2.0**
- Implement Spatializer API for headphones
- Add "Cinema Mode" preset
- Preserve spatial information in EQ processing

### 4.3 HDMI eARC Advanced Features

**Current Implementation:**
- Basic HDMI passthrough detection
- PCM/LPCM mode warning

**Advanced Features:**
- **eARC (Enhanced Audio Return Channel)**
  - High-bitrate audio (Dolby TrueHD, DTS-HD MA)
  - Object-based audio (Dolby Atmos, DTS:X)
  - Lip sync correction
  - Audio format detection

**Implementation:**
- Detect eARC capability
- Show format information (Dolby Atmos, DTS:X)
- Optimize EQ for object-based audio
- Automatic lip sync compensation

**Recommendation:** ✅ **LOW PRIORITY for v2.5**
- Requires HDMI CEC integration
- Limited to high-end TVs
- Nice-to-have feature

---

## 5. User Experience Improvements

### 5.1 Accessibility Features

**Current State:** Basic TV remote navigation

**Recommended Additions:**
1. **Voice Control**
   - "Hey Google, enable night mode"
   - "Switch to movie EQ"
   - Integration with Google Assistant

2. **High Contrast Mode**
   - For visually impaired users
   - Larger text, higher contrast colors

3. **Audio Descriptions**
   - Describe EQ changes verbally
   - "Bass boosted by 3dB"

4. **Hearing Aid Compatibility**
   - Auracast integration
   - T-coil support
   - Accessibility presets

**Recommendation:** ✅ **MEDIUM PRIORITY for v1.5**
- Start with voice control (Google Assistant)
- Add high contrast mode
- Implement audio descriptions

### 5.2 Gesture Controls

**For TV Remotes:**
- Swipe left/right: Adjust bass/treble
- Swipe up/down: Volume
- Long press: Quick presets

**For Touch Devices:**
- Pinch to zoom EQ curve
- Drag to adjust bands
- Two-finger swipe: Switch presets

**Recommendation:** ✅ **LOW PRIORITY for v2.0**
- Nice-to-have for touch devices
- Limited value for TV remote

### 5.3 Multi-Room Audio Sync

**Use Case:**
- Synchronize EQ across multiple rooms
- Whole-home audio system
- Consistent sound signature

**Technical Requirements:**
- Network synchronization
- Latency compensation
- Multi-device profile management

**Recommendation:** ❌ **LOW PRIORITY**
- Out of scope for TV-focused app
- Better suited for dedicated multi-room systems

---

## 6. Testing & Quality Assurance

### 6.1 Audio Quality Measurement

**Current Testing:**
- Manual listening tests
- Basic frequency response verification

**Professional Tools:**

#### PEAQ (Perceptual Evaluation of Audio Quality)
- ITU-R BS.1387 standard
- Objective audio quality measurement
- Compares original vs. processed audio
- Score: 0 (bad) to 5 (excellent)

#### MUSHRA (MUltiple Stimulus with Hidden Reference and Anchor)
- ITU-R BS.1534 standard
- Subjective listening tests
- Double-blind methodology
- Statistical analysis

#### THD+N (Total Harmonic Distortion + Noise)
- Measure distortion introduced by EQ
- Target: <0.1% THD+N
- Verify no clipping or artifacts

**Recommendation:** ⚠️ **HIGH PRIORITY**
- Implement PEAQ measurement in CI/CD
- Add automated THD+N testing
- Conduct MUSHRA tests with users
- Target: PEAQ score >4.5 (excellent)

### 6.2 Automated Testing

**Current Coverage:**
- 30+ unit tests for DSP modules
- Manual integration testing

**Recommended Additions:**

#### Audio Processing Tests
```kotlin
@Test
fun testEqualizerFrequencyResponse() {
    val eq = Equalizer(0, 0)
    eq.setBandLevel(0, 600) // +6dB at 60Hz
    
    val response = measureFrequencyResponse(eq, 60.0)
    assertEquals(6.0, response, 0.5) // ±0.5dB tolerance
}

@Test
fun testLatencyUnder10ms() {
    val latency = measureProcessingLatency()
    assertTrue(latency < 10.0) // Must be under 10ms
}

@Test
fun testNoClipping() {
    val output = processAudio(testSignal)
    assertTrue(output.max() <= 1.0) // No clipping
    assertTrue(output.min() >= -1.0)
}
```

#### UI Automation Tests
```kotlin
@Test
fun testSettingsScreenNavigation() {
    onView(withId(R.id.btn_nav_enhanced_settings))
        .perform(click())
    
    onView(withText("Bass Boost"))
        .check(matches(isDisplayed()))
}
```

**Recommendation:** ⚠️ **HIGH PRIORITY**
- Add audio quality tests to CI/CD
- Implement UI automation with Espresso
- Target: 80% code coverage
- Run tests on every commit

---

## 7. Implementation Roadmap

### Phase 1: Critical Features (v1.3 - Q1 2027)

**Priority 1: AutoEQ Integration**
- Import 5000+ headphone profiles
- Auto-detect connected headphones
- Apply correction automatically
- **Effort:** 1 week
- **Impact:** Very High

**Priority 2: Expanded Preset Library**
- Add 50+ presets (genres, headphones, speakers)
- User-importable presets
- Preset sharing
- **Effort:** 1 week
- **Impact:** Medium

**Priority 3: Multi-Point Measurement**
- 5-9 measurement positions
- Improved accuracy (1.5dB)
- Environmental sensors
- **Effort:** 3 weeks
- **Impact:** High

**Priority 4: Audio Quality Testing**
- PEAQ measurement
- THD+N testing
- Automated CI/CD tests
- **Effort:** 2 weeks
- **Impact:** High

### Phase 2: Advanced Features (v1.4 - Q2 2027)

**Priority 1: Parametric EQ**
- 10-band parametric mode
- Q factor control
- Filter type selection (peak, shelf, notch)
- **Effort:** 3 weeks
- **Impact:** High

**Priority 2: Per-Output Memory**
- Save profiles per device type
- Automatic profile switching
- Profile management UI
- **Effort:** 1 week
- **Impact:** High

**Priority 3: Voice Control**
- Google Assistant integration
- Voice commands for presets
- Audio descriptions
- **Effort:** 2 weeks
- **Impact:** Medium

### Phase 3: Performance & AI (v1.5 - Q3 2027)

**Priority 1: Oboe Migration**
- Low-latency audio processing
- NEON SIMD optimization
- 5-10ms latency target
- **Effort:** 4 weeks
- **Impact:** Very High

**Priority 2: ML-Assisted Correction**
- TensorFlow Lite integration
- Predictive EQ from limited data
- 30-second calibration
- **Effort:** 6 weeks
- **Impact:** Very High

**Priority 3: Spatial Audio**
- Spatializer API integration
- Cinema Mode preset
- Dolby Atmos optimization
- **Effort:** 3 weeks
- **Impact:** Medium

### Phase 4: Future-Proofing (v2.0 - Q4 2027)

**Priority 1: Auracast Support**
- Device detection
- Transmitter mode
- Multi-listener profiles
- **Effort:** 4 weeks
- **Impact:** High (future)

**Priority 2: Adaptive AI**
- Continuous learning
- Real-time optimization
- Personalized profiles
- **Effort:** 8 weeks
- **Impact:** Very High

**Priority 3: Gesture Controls**
- Touch gestures
- Remote shortcuts
- Quick adjustments
- **Effort:** 2 weeks
- **Impact:** Low

---

## 8. Recommendations Summary

### Immediate Actions (Next 30 Days)

1. ⚠️ **Implement AutoEQ Integration**
   - Import headphone correction database
   - Auto-detect and apply profiles
   - **Impact:** Mass adoption, competitive parity

2. ⚠️ **Add Audio Quality Testing**
   - PEAQ measurement in CI/CD
   - Automated THD+N tests
   - **Impact:** Quality assurance, professional credibility

3. ⚠️ **Expand Preset Library**
   - 50+ ready-made presets
   - Genre-specific curves
   - **Impact:** User onboarding, satisfaction

### Short-Term (3-6 Months)

1. ⚠️ **Implement Parametric EQ**
   - 10-band parametric mode
   - Q factor control
   - **Impact:** Attract audiophiles, professionals

2. ⚠️ **Multi-Point Measurement**
   - 5-9 positions
   - Improved accuracy
   - **Impact:** Better room correction

3. ✅ **Per-Output Memory**
   - Device-specific profiles
   - Automatic switching
   - **Impact:** User convenience

### Medium-Term (6-12 Months)

1. ⚠️ **Oboe Migration**
   - Low-latency processing
   - NEON SIMD optimization
   - **Impact:** 70% latency reduction

2. ⚠️ **ML-Assisted Correction**
   - TensorFlow Lite
   - Predictive EQ
   - **Impact:** 30-second calibration

3. ✅ **Spatial Audio**
   - Spatializer API
   - Cinema Mode
   - **Impact:** Immersive experience

### Long-Term (12+ Months)

1. ✅ **Auracast Support**
   - Multi-listener broadcasting
   - **Impact:** Future-proofing

2. ✅ **Adaptive AI**
   - Continuous learning
   - **Impact:** Industry-leading accuracy

3. ✅ **Voice Control**
   - Google Assistant
   - **Impact:** Accessibility

---

## 9. Technical Debt & Risks

### Technical Debt

1. **AudioEffect API Limitations**
   - Fixed 5-band EQ
   - No parametric control
   - Higher latency
   - **Solution:** Migrate to Oboe (v1.5)

2. **Single-Point Measurement**
   - Limited accuracy
   - Sweet-spot dependency
   - **Solution:** Multi-point (v1.3)

3. **No Headphone Correction**
   - Missing AutoEQ
   - Competitive disadvantage
   - **Solution:** AutoEQ integration (v1.3)

### Risks

1. **Auracast Adoption Uncertainty**
   - May not reach mainstream
   - **Mitigation:** Implement detection first, transmission later

2. **ML Model Size**
   - May exceed TV app limits
   - **Mitigation:** Use quantization, pruning
   - **Fallback:** Cloud-based inference

3. **Oboe Compatibility**
   - Not all devices support Exclusive mode
   - **Mitigation:** Hybrid approach (Oboe + AudioEffect fallback)

---

## 10. Conclusion

Core EQ v1.2 has a strong foundation with unique features (room correction, night mode, content detection). However, **critical gaps** exist in competitive features (AutoEQ, parametric EQ) and performance (latency).

**Top 3 Priorities:**
1. **AutoEQ Integration** (v1.3) - Competitive parity
2. **Audio Quality Testing** (v1.3) - Professional credibility
3. **Oboe Migration** (v1.5) - Performance leadership

**Long-Term Vision:**
- AI-driven adaptive room correction (0.3dB accuracy)
- Sub-10ms latency with NEON optimization
- Auracast multi-listener support
- Industry-leading audio quality

**Success Metrics:**
- PEAQ score >4.5 (excellent)
- Latency <10ms
- User rating >4.5★
- 100K+ downloads by end of 2027

---

## References

1. ARM NEON SIMD for Real-Time Audio - MVP Factory (2026)
2. Android Low Latency Audio - Developer Documentation (2026)
3. In-Process Audio Decoders - Android Developer Guide (2026)
4. AI-Driven DSP Revolution - Audio Intensity (2026)
5. Intelligent Room Simulation - Making a Scene (2025)
6. Auracast Bluetooth LE Audio - Why So Geek (2026)
7. Best Equalizer Apps for Android - iTechGuides (2025)
8. Wavelet vs Poweramp Comparison - Reddit (2025)
9. Dirac Live Room Correction - Official Documentation
10. Sonarworks SoundID Reference - Product Specifications

---

**Document Version:** 1.0  
**Last Updated:** October 6, 2026  
**Next Review:** January 6, 2027 (after v1.3 release)
