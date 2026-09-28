# Core EQ — the Android TV audio app market: competitors, precedents, and the product gap

Research date: 2026-09-28. Fifth research pass, surveying the existing audio,
equalizer, volume-boosting, and calibration apps available for Android TV,
Google TV, Fire TV, and adjacent platforms.

---

## 1. Executive summary: the empty market quadrant

The Android TV audio market is split between three incomplete categories:

1. **OEM-locked auto-calibration (Sony, LG, Samsung):** Uses the remote control
   microphone to measure room acoustics, but is hard-coded into proprietary TV
   firmware, disabled the moment external audio (HDMI ARC / soundbar / optical)
   is connected, offers zero transparency or curve editing, and cannot export.
2. **Play Store TV-native equalisers (SoundWave TV, TV DSP Center):** Built
   with 10-foot D-pad remote navigation, but offer only crude 5-to-10 band
   manual graphic sliders and generic genre presets ("Rock", "Pop", "Cinema").
   They have **zero acoustic measurement capabilities**, no microphone support,
   and leave the user to manually guess which frequencies are causing room boom
   or dialogue mud.
3. **Sideloaded smartphone power-tools (Poweramp Equalizer, Wavelet):**
   Sophisticated DSP engines (parametric EQ, compressor, limiter, AutoEQ
   import), but **crippled on television by touch-screen phone interfaces**.
   They require plugging in a physical USB mouse or using virtual mouse apps,
   and they do not measure rooms or microphones.

**The Product Gap:** There is **not a single application in the Android TV /
Google TV ecosystem** that uses the remote microphone to measure room
acoustics, detects standing room modes, computes the Schroeder transition, and
builds a calibrated parametric filter set.

Core EQ occupies this unoccupied quadrant: **10-foot remote measurement +
rigorous acoustic science + companion export to Poweramp Equalizer**.

---

## 2. Detailed landscape of existing Android TV audio apps

### Category A: TV-Native Play Store Equalisers

#### 1. SoundWave TV: Equalizer (`com.soundwave.tv`)
- **Developer:** Nexech
- **Form factor:** Android TV & Google TV native (Google Play Store).
- **Architecture:** 5-band or 10-band graphic equalizer (60 Hz to 14 kHz)
  targeting the standard Android `android.media.audiofx.Equalizer` API.
- **Controls:** Bass boost, virtualizer, reverb, genre presets (Normal, Rock,
  Jazz, Hip-Hop, Flat). Full D-pad remote navigation.
- **User reception & limitations (Play Store rating: 3.8/5):**
  - Completely manual slider adjustment; users have no idea which frequencies
    are resonant in their room.
  - Frequent audio loss bugs: users report that after TV sleep/wake, the audio
    engine loses session routing and audio cuts out completely until toggled off.
  - Fails silently when surround sound / passthrough is enabled on Xiaomi/TCL
    sets.

#### 2. TV DSP Center - Equalizer (`com.tvdsp.center`)
- **Developer:** TV DSP
- **Form factor:** Android TV, Google TV, and Android boxes (Formuler, Shield).
- **Architecture:** 10-band graphic equalizer (32 Hz to 16 kHz), real-time
  visual curve, dynamic loudness, anti-clipping limiter, dialogue enhancer.
- **Monetization:** Free tier limited to 3 bands (32 Hz, 1 kHz, 16 kHz);
  one-time Pro upgrade for 10 bands and Smart DSP.
- **User reception & strengths:**
  - One of the few apps that openly documents the HDMI passthrough limitation
    in its UI: warns users to switch TV audio output from "Auto / Bitstream" to
    "PCM / Stereo" if audio effects have no audible impact.
  - Still strictly manual: does not use the remote mic or measure acoustic
    reflections.

---

### Category B: Sideloaded Phone Equaliser Engines

#### 1. Poweramp Equalizer (`com.maxmpz.equalizer`)
- **Status on TV:** Sideloaded via APK or third-party stores.
- **Audio capabilities:** The gold standard of Android mobile equalization.
  Up to 32 graphic bands, fully configurable parametric peaking/shelf filters,
  AutoEQ preset import, variable Q, compressor, limiter, and preamp.
- **The Fatal TV Flaw:** **Phone touch UI**. The sliders, knobs, and menus
  cannot be focused or navigated using a standard TV remote D-pad. Users must
  plug in a USB mouse or air mouse to configure it.
- **Core EQ Relationship:** **Companion target, not a rival.** Core EQ's
  entire export format is designed to feed Poweramp Equalizer via standard
  AutoEQ `.txt` (with "Bands Overlap = Cascade"). Core EQ does the 10-foot
  measurement; Poweramp applies the audio engine.

#### 2. Wavelet (`com.pittvandewitt.wavelet`)
- **Status on TV:** Sideloaded.
- **Audio capabilities:** AutoEQ database integration, graphic EQ, limiter.
- **The TV Flaw:** Designed exclusively for headphones. Requires
  `android.permission.DUMP` over ADB to capture global audio sessions, lacks
  TV remote navigation, and does not support room calibration or speaker targets.

#### 3. Volume Booster Goodev (`com.goodev.volume.booster`)
- **Status on TV:** Widely sideloaded by users struggling with quiet dialogue.
- **Mechanism:** Applies brute-force digital gain (up to +60%) to the audio
  stream without equal-loudness shaping or peak limiting.
- **Acoustic hazard:** Frequently blows or permanently distorts small,
  thin TV speakers. User reviews routinely cite speaker rattle and damaged
  transducers caused by overdriving small drivers past their linear excursion.

---

### Category C: OEM Built-in TV Calibration

#### 1. Sony Bravia "Acoustic Auto Calibration"
- **Platform:** Sony Bravia XR Android TV / Google TV models.
- **Hardware mechanism:** The TV plays swept chirps through its internal
  speakers and records them using the **microphone on the Sony TV remote**.
- **Acoustic processing:** Adjusts internal DSP delay and frequency response
  for the primary seating position.
- **The Crippling Constraint:** **Internal TV speakers only.** The instant an
  external soundbar, AV receiver, or HDMI eARC device is connected, the Sony
  firmware **greys out and disables Acoustic Auto Calibration**. It cannot be
  used with third-party audio systems, soundbars, or headphones.

#### 2. LG webOS "AI Acoustic Tuning"
- **Platform:** LG webOS OLED/QNED smart TVs (proprietary OS, not Android).
- **Hardware mechanism:** Plays test tones and captures them with the LG Magic
  Remote microphone.
- **User feedback:** Users report the calibration difference is subtle, often
  over-boosting subwoofer levels or producing a scooped midrange, with no way
  to view the measured curve or edit the resulting EQ filters.

#### 3. Samsung Tizen "SpaceFit Sound"
- **Platform:** Samsung QLED / Neo QLED smart TVs (Tizen OS).
- **Hardware mechanism:** Uses microphones built into the TV chassis or select
  Samsung Q-Series soundbars.
- **Constraint:** Proprietary closed garden; requires purchasing matching
  high-end Samsung hardware.

---

### Category D: Professional Calibration Systems

#### 1. Dirac Live
- **Platform:** High-end AVRs (Denon, Marantz, Onkyo, Pioneer) and PC/Mac.
- **Mechanism:** Multi-point swept sine, impulse response deconvolution,
  mixed-phase FIR filters.
- **Constraint:** Extremely expensive ($349–$799 license), requires a calibrated
  USB measurement microphone (miniDSP UMIK-1) and a laptop. Completely out of
  reach for a mainstream television user on a couch.

#### 2. Audyssey MultEQ / MultEQ-X
- **Platform:** Denon / Marantz AV receivers.
- **Mechanism:** Proprietary calibration microphone plugged into the receiver;
  PC software for filter target customization.
- **Constraint:** Tied to AVR hardware; cannot run on standalone TV sets or
  soundbars.

---

## 3. Comprehensive feature comparison matrix

| Feature | Core EQ | SoundWave TV | TV DSP Center | Poweramp EQ | Sony Acoustic Auto | Volume Booster Goodev |
|---|---|---|---|---|---|---|
| **Platform** | Android TV / Google TV | Android TV | Android TV / Boxes | Android (Sideload) | Sony Bravia OS | Android (Sideload) |
| **D-pad 10-foot UI** | **Yes (TvActivity)** | Yes | Yes | **No (Mouse only)** | Yes (Settings) | **No** |
| **Remote Mic Measurement** | **Yes (ATVV BLE)** | No | No | No | **Yes** | No |
| **Acoustic Room Mode Detection** | **Yes (Schroeder $f_s$)** | No | No | No | Proprietary black box | No |
| **Anti-Null Protection** | **Yes (Refuses to boost)**| No | No | No | Unknown | No |
| **Speech Intelligibility Target**| **Yes (SII Formants)** | Generic "Voice" | "Dialogue" preset | Manual | No | No (Distorts) |
| **Parametric Filter Export** | **Yes (AutoEQ / Poweramp)**| No | No | Export/Import | **None** | No |
| **Works with External Speakers**| **Yes (via Poweramp/PCM)**| Yes (PCM) | Yes (PCM) | Yes | **No (Disabled on ARC)** | Yes |
| **Speaker Excursion Protection** | **Yes (Roll-off floor)** | No | Limited | Preamp limiter | Proprietary | **None (Blows drivers)**|
| **Cost** | Open / Free | Free / IAP | Freemium / Paid Pro | Paid ($5) | Built-in | Free |

---

## 4. Key market lessons for Core EQ

### 1. The "Passthrough" trap is the #1 cause of user 1-star reviews
Both SoundWave TV and TV DSP Center suffer negative reviews from users saying
"Does not work, no effect on sound". In every case, the TV is set to Dolby
Digital / DTS Bitstream passthrough over HDMI ARC, bypassing Android's
software audio stack.
**Core EQ Action:** Core EQ must detect the active audio output format during
onboarding and explicitly warn the user: *"Set TV Sound Output to PCM / Stereo
so the equaliser can reach your audio."*

### 2. Sideloaded phone apps fail on the remote control
Poweramp Equalizer is universally praised for its DSP quality, but users on
Reddit repeatedly lament having to keep a wireless mouse on their coffee table
just to tweak an EQ band.
**Core EQ Action:** By building a 100% remote-navigable 10-foot UI (`TvActivity`
normalised to $960\times 540\text{ dp}$), Core EQ gives users the calibration
wizard on their TV remote, exporting directly to Poweramp without requiring
mouse interaction during listening.

### 3. OEM TV auto-calibration abandons external speakers
Sony's Acoustic Auto Calibration is the closest concept to Core EQ, but its
fatal limitation is that it shuts off when a soundbar or receiver is plugged
in.
**Core EQ Action:** Core EQ calibrates the acoustic reality of the room
regardless of whether the sound comes from internal drivers, a soundbar, or
powered stereo monitors.
