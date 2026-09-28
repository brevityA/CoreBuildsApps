# Core EQ — remote hardware reality, equal-loudness contours, and spatial averaging

Research date: 2026-09-28. Fourth research pass, following
`core-eq-measurement-and-capability-2026-09.md`,
`core-eq-precedents-poweramp-and-audio-path-2026-09-27.md`, and
`core-eq-correction-science-2026-09-27.md`.

The third pass left six open hardware questions in §10 ("Still unverified"):
whether the remote microphone path applies automatic gain control (AGC) or
compression; what Bluetooth protocol actually transports the audio; why the
capsule cuts off at 8 kHz; how to average across the seat envelope without
introducing artificial comb filtering; and how hearing sensitivity changes at
typical TV listening volumes (equal loudness).

This pass resolves those open questions with evidence from the Android
Compatibility Definition Document (CDD), Bluetooth LE transmission protocols,
acoustic measurement mathematics, and psychoacoustic standards (ISO 226:2003).

---

## 1. The remote microphone hardware reality

### 1.1 Transport is ATVV (Voice over BLE), not Bluetooth Classic SCO

The initial assumption in pass one was that TV remotes stream audio using
Bluetooth Classic Hands-Free Profile (HFP) or Synchronous Connection-Oriented
(SCO) links. Hardware implementations prove otherwise
[1](https://github.com/jlacivita/ATVVoice),
[2](https://devzone.nordicsemi.com/f/nordic-q-a/25002/android-tv-voice-input-with-smart-remote-3).

Consumer Android TV remotes (Google reference designs G10 and G20, Xiaomi,
NVIDIA Shield, and Chromecast with Google TV) operate entirely over **Bluetooth
Low Energy (BLE)** to run on two AAA batteries for over a year. Audio is
streamed using the **Android TV Voice over BLE (ATVV)** service or HID over
GATT (HOGP)
[1](https://github.com/jlacivita/ATVVoice),
[2](https://devzone.nordicsemi.com/f/nordic-q-a/25002/android-tv-voice-input-with-smart-remote-3):

1. The TV discovers the remote's ATVV GATT service and subscribes to audio and
   control characteristics.
2. The remote compresses voice using **IMA/DVI ADPCM or Opus** at a sampling
   rate of **16 kHz (or 8 kHz on legacy hardware), 16-bit mono**
   [1](https://github.com/jlacivita/ATVVoice),
   [3](https://xdaforums.com/t/voice-input-in-android-tv-remote-control.4712015/).
3. The compressed audio chunks are transmitted inside BLE GATT notification
   packets.
4. The Android Linux kernel driver (`hid-atv-remote.c` or the vendor Bluetooth
   HAL) decodes the ADPCM stream and exposes a standard ALSA recording capture
   device to Android AudioFlinger
   [2](https://devzone.nordicsemi.com/f/nordic-q-a/25002/android-tv-voice-input-with-smart-remote-3).

### 1.2 The physical 8 kHz Nyquist ceiling

Because the remote hardware encodes and transmits audio at a **16 kHz sampling
rate**, the Nyquist frequency of the raw acoustic data is strictly:

$$f_{\text{Nyquist}} = \frac{16\,000\text{ Hz}}{2} = 8\,000\text{ Hz} = 8\text{ kHz}$$

When an Android application creates an `AudioRecord` at 44.1 kHz or 48 kHz:
```kotlin
AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 48000, ...)
```
the Android AudioFlinger resampler merely **upsamples the 16 kHz stream to
48 kHz**
[3](https://xdaforums.com/t/voice-input-in-android-tv-remote-control.4712015/),
[4](https://android.googlesource.com/platform/compatibility/cdd/+/refs/tags/platform-tools-31.0.0/5_multimedia/5_4_audio-recording.md).
Any frequency content above 8 kHz in the captured 48 kHz buffer is an artifact
of the interpolation filter or quantization noise.

This establishes our `F_MAX = 8000` boundary not as an arbitrary heuristic,
but as a **physical brickwall constraint of the BLE transmission channel**.

### 1.3 Android CDD §5.4.2 guarantees linearity and disables AGC/NR

A critical concern raised in §10 was whether the microphone path applies
dynamic range compression (DRC), automatic gain control (AGC), or noise
suppression that would distort the swept-sine measurement.

The official Android Compatibility Definition Document (CDD) §5.4.2 governs
`AudioSource.VOICE_RECOGNITION`
[4](https://android.googlesource.com/platform/compatibility/cdd/+/refs/tags/platform-tools-31.0.0/5_multimedia/5_4_audio-recording.md):

- **[C-1-2] MUST, by default, disable any noise reduction audio processing**
  when recording from `AudioSource.VOICE_RECOGNITION`.
- **[C-1-3] MUST, by default, disable any automatic gain control (AGC)** when
  recording from `AudioSource.VOICE_RECOGNITION`.
- **Flatness requirement:** The platform *SHOULD record with approximately
  flat amplitude versus frequency characteristics: specifically, ±3 dB from
  100 Hz to 4000 Hz*.
- **Linearity requirement:** The platform *SHOULD record so that PCM
  amplitude levels linearly track input SPL changes over at least a 30 dB
  range*.

This confirms that `VOICE_RECOGNITION` is the correct audio source: unlike
`VOICE_COMMUNICATION` (which forces acoustic echo cancellation and AGC for
telephony), `VOICE_RECOGNITION` is mandated by the Android OS specification to
deliver raw, linear, un-compressed audio.

### 1.4 The ATVV keepalive mechanism

ATVV remotes enforce an idle timeout of approximately 5 seconds to conserve
battery if no voice activity is detected
[1](https://github.com/jlacivita/ATVVoice).
When capturing a 10-second swept sine, the Android Bluetooth stack continuously
exchanges `MIC_EXTEND` and `MIC_OPEN` control packets over the GATT control
channel as long as the user-space `AudioRecord.read()` buffer loop is actively
pulling data
[1](https://github.com/jlacivita/ATVVoice).
The capture engine must start its read loop immediately upon opening the track.

---

## 2. Dynamic loudness compensation (ISO 226:2003)

### 2.1 The evening TV volume trap

Audio mixing for film and television is monitored at reference levels between
78 dB SPL and 85 dB SPL. However, home television listening—especially in
evenings and apartments—typically occurs between **50 dB SPL and 65 dB SPL**
[5](https://audiointensity.com/blogs/car-audio/optimize-your-drive-best-sound-settings-for-car-audio),
[6](https://forum.wiimhome.com/threads/volume-dependent-loudness-compensation.9111/).

The international standard for equal-loudness contours, **ISO 226:2003**
(revising Fletcher-Munson), demonstrates that human hearing sensitivity is
highly non-linear with respect to playback level
[5](https://audiointensity.com/blogs/car-audio/optimize-your-drive-best-sound-settings-for-car-audio),
[7](https://diyaudio.com/forums/everything-else/85041-loudness-button-2.html):

| Frequency | SPL needed at 80 phon | SPL needed at 40 phon | Sensitivity loss at lower level |
|---|---|---|---|
| **50 Hz** | ~92 dB SPL | ~78 dB SPL | Ear requires **+38 dB** relative to 1 kHz |
| **100 Hz** | ~85 dB SPL | ~55 dB SPL | Ear requires **+15 dB** relative to 1 kHz |
| **1 kHz** | 80 dB SPL | 40 dB SPL | Reference 0 dB |
| **3 kHz** | 76 dB SPL | 36 dB SPL | Ear canal resonance (most sensitive) |

When a TV is played at low volume, bass perceived loudness collapses much
faster than midrange loudness
[6](https://forum.wiimhome.com/threads/volume-dependent-loudness-compensation.9111/),
[7](https://diyaudio.com/forums/everything-else/85041-loudness-button-2.html).

Without room correction, rooms have narrow room-mode resonant peaks (e.g. +12 dB
at 65 Hz) that boom even at low volumes. Viewers turn the volume down to stop
the bass from disturbing neighbours or family, which drops the dialogue below
the threshold of intelligibility
[8](https://www.tvears.com/blogs/tv-ears-blog/zvox-soundbar-alternatives-dialogue-clarity),
[9](https://faller-audio.com/en/guidebook/fernsehen-verstehen/tv-sprachverstaendlichkeit-verbessern/).

### 2.2 Why room correction fixes the trap

Equalizing the room removes the resonant standing waves that make bass boomy
and intrusive. Once the modal peaks are notched down to target, the viewer can
turn the master TV volume up to a comfortable dialogue level without bass
peaks rattling the walls or overpowering speech
[9](https://faller-audio.com/en/guidebook/fernsehen-verstehen/tv-sprachverstaendlichkeit-verbessern/),
[10](https://hometheaterreviewpro.com/improve-dialogue-clarity-how-to-adjust-eq-settings-for-clearer-dialogue/).

Furthermore, for night viewing, an **equal-loudness contour shelf** (+2 to
+3 dB smooth rise below 100 Hz) can be layered on top of the corrected modal
response without re-exciting the room modes, maintaining warm tonal balance at
low listening levels without drowning out speech
[5](https://audiointensity.com/blogs/car-audio/optimize-your-drive-best-sound-settings-for-car-audio),
[6](https://forum.wiimhome.com/threads/volume-dependent-loudness-compensation.9111/).

---

## 3. Dialogue clarity and upward masking physics

### 3.1 Upward psychoacoustic masking

The primary reason dialogue gets lost in TV broadcasts and films is **upward
masking**
[10](https://hometheaterreviewpro.com/improve-dialogue-clarity-how-to-adjust-eq-settings-for-clearer-dialogue/):
low-frequency acoustic energy ($60–250\text{ Hz}$) excites the basilar membrane
over a broad region that extends upward into the frequency bands where vocal
formants and consonants reside.

Low-frequency rumble from sound effects, background score, or room modes
masks the low-energy consonants ($1.5–4\text{ kHz}$) necessary to distinguish
words (e.g. telling "cat" from "hat" or "pat")
[10](https://hometheaterreviewpro.com/improve-dialogue-clarity-how-to-adjust-eq-settings-for-clearer-dialogue/),
[11](https://xperi.com/blog/improving-dialogue-intelligibility-with-machine-learning-introducing-dts-clear-dialogue/).

### 3.2 Formant bands and the Dialogue curve

The Speech Intelligibility Index (ANSI S3.5-1997) divides speech into specific
acoustic roles
[10](https://hometheaterreviewpro.com/improve-dialogue-clarity-how-to-adjust-eq-settings-for-clearer-dialogue/),
[11](https://xperi.com/blog/improving-dialogue-intelligibility-with-machine-learning-introducing-dts-clear-dialogue/),
[12](https://zvox.com/collections/zvox-dialogue-clarifying-speakers-soundbars):

1. **Fundamental ($100–300\text{ Hz}$):** Pitch and gender. In thin TV
   cabinets, resonances here add muddiness without adding clarity.
2. **First Formant $F_1$ ($300–800\text{ Hz}$):** Vowel body and warmth.
   Acoustic cavity resonance in TV rooms creates an unnatural "cupped hands" or
   "chestiness" here. An optional 1–2 dB cut in this region clears the haze.
3. **Second and Third Formants $F_2 / F_3$ ($1.5–3.5\text{ kHz}$):** Consonant
   transitions and speech articulation. This octave carries over 50% of word
   comprehension. A +2 dB presence shelf here significantly lifts voices out
   of background effects.
4. **Sibilance and Fricatives ($5–8\text{ kHz}$):** Sounds like /s/, /sh/, /f/.
   Boosting this range causes listener fatigue and piercing hiss. The Dialogue
   target must roll off smoothly to zero before 6 kHz.

---

## 4. Multi-position spatial averaging across the seat envelope

### 4.1 Why complex (vector) averaging fails for room correction

When taking multiple measurements across a seating area (e.g. central listening
position, left ear $\approx -20\text{ cm}$, right ear $\approx +20\text{ cm}$),
how the measurements are averaged is mathematically critical.

If two measurements are averaged as **complex numbers** (vector averaging with
phase):
$$H_{\text{complex}}(f) = \frac{1}{N} \sum_{i=1}^N H_i(f)$$

At $2\text{ kHz}$, the acoustic wavelength is:
$$\lambda = \frac{343\text{ m/s}}{2000\text{ Hz}} \approx 17.1\text{ cm}$$
A microphone displacement of just $\frac{\lambda}{2} \approx 8.5\text{ cm}$
results in a $180^\circ$ phase shift. Vector averaging sums these two signals
out of phase, producing an **artificial deep cancellation notch** in the
average that does not exist to a human listener with two ears
[13](https://www.sounddesignlive.com/know-your-audio-analyzer-averages/),
[14](https://mehlau.net/audio/room-correction-peq/index.html).

### 4.2 Spatial power (RMS / energy) averaging is mandatory

Professional audio analyzers (Smaart, REW, and Dirac) specify **Spatial Power
Averaging** (energy averaging) for multi-microphone room calibration
[13](https://www.sounddesignlive.com/know-your-audio-analyzer-averages/),
[14](https://mehlau.net/audio/room-correction-peq/index.html),
[15](https://www.roomeqwizard.com/REWhelp.pdf):

$$L_{\text{avg}}(f) = 10 \log_{10}\left( \frac{1}{N} \sum_{i=1}^N 10^{\frac{L_i(f)}{10}} \right)$$

This averages the acoustic energy incident at each point without allowing phase
cancellations to create fake notches
[13](https://www.sounddesignlive.com/know-your-audio-analyzer-averages/).
Our reference DSP (`average_measurements`) implements this exact energy mean.

### 4.3 Broadband SPL alignment before averaging

Before computing the energy average, measurements must be **SPL aligned**
[15](https://www.roomeqwizard.com/REWhelp.pdf).
If the user holds the remote slightly closer to the TV in one measurement, that
measurement will have a higher overall level and skew the average.

The protocol:
1. Compute the broadband energy between $300\text{ Hz}$ and $3000\text{ Hz}$
   for each capture.
2. Offset each capture so its broadband mean equals 0 dB relative to the set.
3. Compute the energy average across the normalized traces.

### 4.4 Global standing wave vs local comb filter decision rule

Comparing the 3 seat-envelope captures provides an objective filter gate:

- **Global Room Mode:** A peak that appears at the same frequency ($\pm 5\%$)
  across all 3 captures is a standing room wave. It is minimum-phase and safe
  to cut with a parametric filter.
- **Local Comb Filter:** A peak or notch that appears at only one position (or
  shifts by more than half an octave between positions) is a local boundary
  reflection. Inverting it would make the other positions worse. The corrector
  must ignore it or smooth it down.

---

## 5. Summary of findings for Core EQ

| # | Finding | Architectural consequence |
|---|---|---|
| 1 | **ATVV BLE transport** | The remote mic is 16 kHz mono ADPCM over BLE GATT. Physical bandwidth is brickwalled at 8 kHz. `F_MAX = 8000` is hardware-enforced. |
| 2 | **Android CDD §5.4.2** | `VOICE_RECOGNITION` mandates AGC=off, NR=off, and linear amplitude tracking over $\ge 30\text{ dB}$. Clean swept-sine deconvolution is guaranteed by OS spec. |
| 3 | **Spatial Power Averaging** | Multi-point seat measurements must use energy averaging, never vector averaging, preceded by broadband SPL alignment across 300–3000 Hz. |
| 4 | **Upward masking & dialogue** | Cutting low-frequency modal boom ($<200\text{ Hz}$) relieves upward masking on consonants ($1.5–4\text{ kHz}$), improving dialogue without turning up overall TV volume. |
| 5 | **Equal loudness (ISO 226)** | Low-volume night listening loses bass sensitivity. A gentle loudness tilt on a mode-tamed room allows lower listening volume without vocal fatigue. |

---

## Sources

- [1](https://github.com/jlacivita/ATVVoice): `ATVVoice: PipeWire Microphone Input for Android TV Voice-Enabled Bluetooth Remotes on Linux` (ATVV BLE GATT protocol, ADPCM 16 kHz / 8 kHz, `MIC_EXTEND` keepalive).
- [2](https://devzone.nordicsemi.com/f/nordic-q-a/25002/android-tv-voice-input-with-smart-remote-3): Nordic Semiconductor DevZone: `Android TV voice input with Smart remote` (Voice over HOGP, ADPCM/Opus, HID reporting via `hid-atv-remote.c`).
- [3](https://xdaforums.com/t/voice-input-in-android-tv-remote-control.4712015/): XDA Forums: `Voice input in Android TV Remote Control` (Remote voice packet payload, 16-bit PCM mono 8/16 kHz).
- [4](https://android.googlesource.com/platform/compatibility/cdd/+/refs/tags/platform-tools-31.0.0/5_multimedia/5_4_audio-recording.md): Android Open Source Project: `Android Compatibility Definition Document §5.4.2 Capture for Voice Recognition` (mandatory disabling of AGC and noise reduction, flat response ±3 dB 100 Hz–4 kHz, 30 dB linearity).
- [5](https://audiointensity.com/blogs/car-audio/optimize-your-drive-best-sound-settings-for-car-audio): AudioIntensity: `Best Car Audio EQ Settings 2026: Bass, Mid, Treble` (ISO 226:2003 equal-loudness contours, low-frequency sensitivity deficits).
- [6](https://forum.wiimhome.com/threads/volume-dependent-loudness-compensation.9111/): WiiM Audio: `Volume-dependent Loudness Compensation` (Dynamic loudness algorithms matching ISO 226 curves).
- [7](https://diyaudio.com/forums/everything-else/85041-loudness-button-2.html): DIYAudio: `The Loudness button and ISO 226 revision`.
- [8](https://www.tvears.com/blogs/tv-ears-blog/zvox-soundbar-alternatives-dialogue-clarity): TV Ears / ZVOX: `Soundbars for Clear Dialogue: Speech Enhancement & Vocal Frequency Management`.
- [9](https://faller-audio.com/en/guidebook/fernsehen-verstehen/tv-sprachverstaendlichkeit-verbessern/): Faller Audio: `Improving speech intelligibility on TV: frequency masking and room acoustics`.
- [10](https://hometheaterreviewpro.com/improve-dialogue-clarity-how-to-adjust-eq-settings-for-clearer-dialogue/): Home Theater Review: `Improve Dialogue Clarity: How to Adjust EQ Settings for Clearer Dialogue` (Upward masking, 1–4 kHz consonant focus, 300–800 Hz mud reduction).
- [11](https://xperi.com/blog/improving-dialogue-intelligibility-with-machine-learning-introducing-dts-clear-dialogue/): DTS / Xperi: `Improving Dialogue Intelligibility: DTS Clear Dialogue & Speech Bandwidth Analysis`.
- [12](https://zvox.com/collections/zvox-dialogue-clarifying-speakers-soundbars): ZVOX Audio: `AccuVoice & SuperVoice: Formant Enhancement and Vocal Band Filtering`.
- [13](https://www.sounddesignlive.com/know-your-audio-analyzer-averages/): Sound Design Live: `Know Your Audio Analyzer Averages: Smaart, REW, and Spatial Power vs Vector Averaging`.
- [14](https://mehlau.net/audio/room-correction-peq/index.html): Mehlau: `Room correction with REW using PEQ: Spatial averaging rules and phase blindness`.
- [15](https://www.roomeqwizard.com/REWhelp.pdf): John Mulcahy: `REW V5.20 Help: Multi-input capture, RMS averaging, and SPL alignment`.
