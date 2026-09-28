# Core EQ — Research Pass 6: MEMS Capsule Acoustics, Body Shadowing, Dialogue SII, and Crossover Dynamics

*Date: 2026-09-29*  
*Author: Core EQ Architecture Team*  
*Status: Approved Design Foundation*

---

## Executive Summary

Previous research passes established the capabilities and constraints of Android TV room equalization:
* **Pass 1 & 2:** Capability ladder, HDMI passthrough vs PCM routing, and Poweramp Equalizer companion role.
* **Pass 3:** Schroeder frequency ceilings ($f_s = 2000 \cdot \sqrt{\text{RT60}/V}$), minimum-phase room modes, anti-null guardrails, and Farina swept-sine stimulus.
* **Pass 4:** Android TV Voice over BLE (ATVV) 16 kHz ADPCM protocol, 8 kHz Nyquist ceiling, Android CDD §5.4.2 `VOICE_RECOGNITION` linear tracking, ISO 226:2003 loudness curves, and energy-domain spatial averaging.
* **Pass 5:** Competitive landscape across TV-native Play Store equalizers, sideloaded engines, and OEM auto-calibration lockouts.

This sixth pass addresses the **physical and psychoacoustic interface**: how the physical conditions of measuring with a handheld TV remote interact with acoustic sound fields, human anatomy, speech intelligibility standards, and home theater speaker configurations.

### Key Findings
1. **MEMS Capsule Transfer Functions & Gasket Acoustics:** TV remotes use bottom-port or top-port silicon MEMS capsules (e.g., Knowles SPU/SPV, ST MP34/IMP23). Acoustic modeling reveals that the mechanical inlet tube and elastomeric gasket form a Helmholtz resonator with a resonance peak between 15 kHz and 21 kHz. Because the ATVV BLE link decimates to 16 kHz audio (8 kHz Nyquist limit), the anti-aliasing decimation filter in the Bluetooth controller rejects this resonance completely. In the operating band (40 Hz–8 kHz), the raw MEMS capsule is remarkably flat ($\pm 0.75\text{ dB}$).
2. **Handheld Acoustic Interface & Body Baffle Diffraction:** A remote held in hand creates two acoustic phenomena:
   * **Baffle Step:** The remote chassis (width $\approx 4\text{ cm}$) transitions from free-field ($4\pi$ steradians) to half-space ($2\pi$) above $4.3\text{ kHz}$.
   * **Torso Reflection & Shadowing:** The human torso ($r \approx 25\text{ cm}$) creates comb-filtering ripples ($\pm 3\text{ dB}$) between 700 Hz and 1.8 kHz if the microphone is held within 15 cm of the chest. Holding the remote with arm extended forward ($\ge 35\text{ cm}$) at ear level places torso reflections beyond the direct impulse response gating window.
3. **ANSI S3.5 Speech Intelligibility Index (SII) Frequency Weighting:** Modern streaming audio features wide dynamic range where background music and explosions overpower whispered dialogue. ANSI S3.5-1997 Table 3 proves that 71.6% of speech intelligibility is concentrated in three octave bands: 1000 Hz (23.7%), 2000 Hz (26.5%), and 4000 Hz (21.4%). In contrast, room modes in the 150–350 Hz band account for only 6.2% of speech information but generate severe **upward auditory masking** over higher-frequency consonants. Dialogue optimization requires cutting low-frequency modal overhang and applying targeted presence boost across 2.2–4.5 kHz.
4. **Subwoofer Crossover Dynamics vs Room Boundary Cancellations:** In 2.1 soundbars and AVR setups, phase cancellation at the crossover frequency (typically 80 Hz or 100 Hz, wavelength $\lambda \approx 3.4\text{–}4.3\text{ m}$) frequently mimics a room mode null. Attempting to boost an acoustic phase cancellation wastes headroom and induces distortion; Core EQ must identify phase-alignment artifacts and guide users to subwoofer phase toggling ($0^\circ / 180^\circ$) rather than applying destructive positive boost filters.
5. **Spatial Seat Variance & Modal Classification:** Single-point room correction runs the risk of overfitting to one seat at the expense of adjacent listeners. By tracking spatial variance across multiple seat captures (Main Listening Position vs Left/Right couch seats), Core EQ distinguishes **global axial modes** (uniform across seats, safe to cut up to $-12\text{ dB}$) from **localized interference nulls** (which shift with position and must not be inverted).

---

## 1. MEMS Microphone Package Acoustics & Gasket Modeling

### 1.1 The Anatomy of TV Remote Microphones
Modern Android TV voice remotes (Google G9N9N, Nvidia Shield, Sony RMF-TX series, TCL RC902V, Amazon Alexa voice remotes) do not use traditional electret condenser capsules (ECMs). They use surface-mount silicon MEMS (Micro-Electro-Mechanical Systems) microphones paired with an integrated ASIC.

```
+-------------------------------------------------------------+
|                     Remote Plastic Shell                    |
|                        [ Sound Port ]                       |
|                             |                               |
|                     [ Rubber Gasket ]                       |
|                             |                               |
|        +--------------------+---------------------+         |
|        |       MEMS Metal Lid / Front Chamber     |         |
|        |     +-----------+   +---------------+    |         |
|        |     | Membrane  |---|  Silicon ASIC |    |         |
|        |     +-----------+   +---------------+    |         |
|        |             [ Back Chamber ]             |         |
|        +--------------------+---------------------+         |
|                        FR4 PCB                              |
+-------------------------------------------------------------+
```

### 1.2 Acoustic Transfer Function
The acoustic behavior of a MEMS microphone is partitioned into two distinct physical regimes:

1. **Low-Frequency Roll-Off (LFRO):**
   The back chamber and the microscopic pressure-equalization ventilation hole in the silicon membrane form an acoustic high-pass RC filter:
   $$f_{\text{LFRO}} = \frac{1}{2\pi R_{\text{acoustic}} C_{\text{acoustic}}}$$
   For consumer voice-grade MEMS capsules (e.g., Knowles SPU0410HR5H, ST MP34DT05), $f_{\text{LFRO}}$ sits between **25 Hz and 45 Hz**, exhibiting a classical first-order $+6\text{ dB/octave}$ ($20\text{ dB/decade}$) roll-off below this corner. Above 40 Hz, the mechanical response is flat within $\pm 0.5\text{ dB}$.

2. **High-Frequency Helmholtz Resonance:**
   The combination of the remote housing sound port, the elastomeric rubber gasket channel, and the microphone lid forms a Helmholtz acoustic resonator. The resonant frequency is modeled as:
   $$f_{\text{resonance}} = \frac{c}{2\pi} \sqrt{\frac{A}{V \cdot (L + 0.8 \cdot r)}}$$
   where:
   * $c = 343\text{ m/s}$ (speed of sound in air),
   * $A = \pi r^2$ is the cross-sectional area of the sound port ($r \approx 0.4\text{ mm}$),
   * $L \approx 1.5\text{ mm}$ is the channel length through the gasket and plastic housing,
   * $V \approx 0.6\text{ mm}^3$ is the front chamber volume.

   COMSOL acoustic simulations by STMicroelectronics (Application Note AN4427 / AN5522) verify that this front-cavity resonance peak occurs between **15 kHz and 21.6 kHz**, with a Q-factor of 1.5 to 2.5 (a $+4\text{ to }+7\text{ dB}$ peak).

### 1.3 The Nyquist Filtering Benefit
In full-range studio recording, the 15–21 kHz Helmholtz peak introduces noticeable high-frequency color unless equalized. However, in an Android TV voice remote:
* The audio is digitized and transmitted via the ATVV (Android TV Voice over BLE) profile at **$16\text{ kHz}$ sampling rate**.
* The hardware sigma-delta ADC and decimation filter in the remote's BLE SoC (e.g., Telink, Realtek, or Nordic Semiconductor) apply a linear-phase low-pass FIR filter with brickwall rejection starting at **$7.5\text{ kHz}$** and $>60\text{ dB}$ attenuation at the $8\text{ kHz}$ Nyquist limit.
* **Consequence:** The Helmholtz resonance peak is completely rejected before transmission. Between **40 Hz and 7.5 kHz**, the transmitted frequency response is remarkably linear, with less than $\pm 1.0\text{ dB}$ of intrinsic capsule deviation.

This confirms our earlier architectural decision: **an uncalibrated TV remote microphone is an exceptionally reliable measurement tool between 40 Hz and 8000 Hz, provided the software refuses to extrapolate outside this band.**

---

## 2. The Handheld Acoustic Interface: Baffle Step and Body Shadowing

When using a professional measurement microphone (such as a miniDSP UMIK-1 or Earthworks M30), the capsule sits on a thin $1/4\text{"}$ wand mounted on a tripod, isolated from reflecting structures. A TV remote, however, is held in the user's hand while seated on a couch.

### 2.1 Remote Enclosure Baffle Step
The plastic remote body acts as a small acoustic baffle. 
* At low frequencies where the wavelength $\lambda \gg w$ (where $w \approx 4\text{ cm}$ is the remote width, corresponding to $f < 1000\text{ Hz}$, $\lambda > 34\text{ cm}$), sound waves diffract entirely around the remote ($4\pi$ steradian spherical radiation).
* At high frequencies where $\lambda \ll w$ ($f > 4300\text{ Hz}$, $\lambda < 8\text{ cm}$), the remote face acts as a half-space reflector ($2\pi$ hemispherical boundary), creating a baffle step boost of $+3\text{ to }+6\text{ dB}$ on-axis.
* In Core EQ, this transition is smooth and monotonic. Because Core EQ's target curves are relative shape pivots and high-frequency shaping above the Schroeder transition is constrained to broad $\pm 3\text{ dB}$ adjustments, baffle step does not corrupt modal peak detection.

### 2.2 Human Torso and Head Diffraction (The Body Baffle)
The presence of the user's body in the sound field introduces acoustic scattering and reflection:

```
        TV Loudspeaker (Direct Wave Arrival)
                     =====================> 
                                             \
                                              \ (Direct arrival: t = 0)
                                               \
                                                v
             [ TV Remote Mic ] <---------------+ 
                    |                           \
                    | ~35 cm                     \ (Torso reflection: t = +2.1 ms)
                    v                             \
             [ Human Torso ] <=====================+
```

1. **Torso Reflection Delay:**
   If a user holds the remote against their chest ($d \approx 10\text{ cm}$), the reflection off the sternum arrives with a delay of:
   $$\Delta t = \frac{2 \cdot d}{c} = \frac{0.20\text{ m}}{343\text{ m/s}} \approx 0.58\text{ ms}$$
   This creates destructive comb-filtering notches at:
   $$f_{\text{null}} = \frac{1}{2 \cdot \Delta t} \approx 860\text{ Hz}, \quad 3 \cdot f_{\text{null}} \approx 2580\text{ Hz}$$
2. **Extended Arm Isolation:**
   When the user extends their arm forward by $35\text{–}45\text{ cm}$ at ear level:
   $$\Delta t_{\text{extended}} = \frac{2 \cdot 0.40\text{ m}}{343\text{ m/s}} \approx 2.33\text{ ms}$$
   * The reflection path length is doubled, reducing the reflected sound pressure level by at least $6\text{–}8\text{ dB}$ relative to the direct arrival due to inverse-square geometric spreading and clothing absorption ($1\text{ kHz}$ absorption coefficient of fabric/cotton $\alpha \approx 0.35$).
   * In swept-sine deconvolution, an arrival at $+2.33\text{ ms}$ falls well past the direct sound impulse response peak, enabling the software window to isolate the primary wavefront.

### 2.3 Handling Noise and Mechanical Microphonics
Human hands suffer from low-frequency physiological tremor (tremor frequency band $4\text{–}12\text{ Hz}$). When holding a plastic remote, skin friction and mechanical flexing generate microphonic impulse clicks.
* **Physical Guardrail:** Core EQ incorporates a sharp 4th-order Butterworth digital high-pass filter at **35 Hz** in the capture chain to eliminate all hand tremor rumble and sub-audible HVAC vibrations before Welch PSD estimation and deconvolution.
* **UI Guidance:** The on-screen Step 2 card explicitly instructs: *"Hold the remote steady with an extended arm at ear height, or rest it on a soft cushion at your ear position."*

---

## 3. Psychoacoustics of TV Audio: ANSI S3.5 Dialogue Intelligibility

The most common complaint from TV viewers is not lack of bass extension; it is **poor dialogue intelligibility**. Viewers frequently raise the master volume during quiet dialogue scenes, only to be blasted by explosions, sound effects, or commercial breaks seconds later.

### 3.1 Upward Auditory Masking
Psychoacoustic masking occurs when a louder sound reduces the auditory sensitivity to a concurrent or subsequent quieter sound. In human hearing, **upward masking** is significantly stronger than downward masking: low-frequency sounds easily mask high-frequency sounds, because low-frequency basilar membrane vibrations travel past the high-frequency basal region of the cochlea.

* In a typical untreated living room, the room's lowest axial modes (typically between $50\text{ Hz}$ and $180\text{ Hz}$) ring with long decay times ($\text{RT60} > 0.8\text{ s}$).
* English speech consists of high-energy low-frequency vowel formants ($150\text{–}800\text{ Hz}$) and low-energy high-frequency consonants ($1.5\text{–}5\text{ kHz}$, e.g., /t/, /s/, /k/, /f/, /th/). Consonants carry over 90% of semantic information but less than 10% of speech energy.
* When resonant room modes ring, lingering low-frequency energy physically masks the subsequent delicate consonant sounds. Taming room modes below the Schroeder frequency immediately clears the auditory channel, allowing consonants to be heard clearly without boosting master volume.

### 3.2 ANSI S3.5-1997 Speech Intelligibility Index (SII)
ANSI S3.5 defines the standardized method for quantifying speech intelligibility. The Speech Intelligibility Index ($S$) is calculated as:
$$S = \sum_{i=1}^{N} I_i \cdot A_i$$
where $I_i$ is the Band Importance Function (FIF) and $A_i$ is the band audibility factor ($0 \le A_i \le 1$).

From ANSI S3.5-1997 Table 3 (One-Third Octave Band Importance for Continuous Discourse) and octave-band equivalents:

| Frequency Band | Center Frequency (Hz) | Importance Weight ($I_i$) | Cumulative Contribution |
| :--- | :---: | :---: | :---: |
| Sub-bass / Room Modes | 125–250 Hz | 0.0617 | 6.2% |
| Lower Midrange / Chest | 500 Hz | 0.1671 | 22.9% |
| **Core Intelligibility** | **1000 Hz** | **0.2373** | **46.6%** |
| **Consonant Articulation** | **2000 Hz** | **0.2648** | **73.1%** |
| **High Sibilance / Clarity** | **4000 Hz** | **0.2142** | **94.5%** |
| Air / Noise Floor | 8000 Hz | 0.0549 | 100.0% |

```
ANSI S3.5 Importance Function Distribution:
===================================================================
125-250 Hz [==] 6.2%
    500 Hz [=====] 16.7%
   1000 Hz [=======] 23.7%          <--- 71.6% of all speech
   2000 Hz [========] 26.5% (PEAK)  <--- intelligibility lives
   4000 Hz [======] 21.4%           <--- in this band!
   8000 Hz [=] 5.5%
===================================================================
```

### 3.3 Core EQ Dialogue Target Formulation
Armed with ANSI S3.5 data, Core EQ's `dialogue` target implements a psychoacoustically validated curve:
1. **Modal Damping (<300 Hz):** Standard in-room bass target (+3.5 dB shelf) but with strict modal inversion. Resonance peaks that ring at 60–160 Hz are cut aggressively to prevent upward masking of speech.
2. **Mud Trim (300–800 Hz):** An optional $-1.5\text{ to }-2.5\text{ dB}$ gentle dip across 300–800 Hz. In compact soundbars and flat-panel TV speakers, plastic enclosure resonance creates a hollow "boxy" coloration in this octave that obscures vocal clarity.
3. **Consonant Articulation Plateau (2.2–4.5 kHz):** A $+2.0\text{ dB}$ raised-cosine presence boost centered over the peak of the ANSI S3.5 importance curve ($2\text{–}4\text{ kHz}$).
4. **Sibilance Guard (>6 kHz):** A sharp return to neutral by 6.5 kHz. Human sibilance harshness (/s/ and /sh/ splash) occurs at 5.5–8 kHz. Boosting this region produces fatigue and accentuates lossy streaming audio compression artifacts; Core EQ deliberately caps the presence boost below this threshold.

---

## 4. Subwoofer Crossover Dynamics vs Room Boundary Nulls

Many Android TV setups utilize a soundbar with a separate wireless subwoofer or a 2.1/3.1 PCM sound system. The crossover between the main soundbar channels and the subwoofer introduces critical acoustic challenges.

### 4.1 The Crossover Phase Cancellation Trap
Standard soundbar subwoofers employ an active 4th-order Linkwitz-Riley or 2nd-order Butterworth crossover at **80 Hz or 100 Hz**.
* At 80 Hz, the acoustic wavelength is:
  $$\lambda = \frac{343\text{ m/s}}{80\text{ Hz}} \approx 4.29\text{ m}$$
  A half-wavelength ($\lambda / 2$) corresponds to **$2.14\text{ meters}$**, or a time delay of **$\Delta t = 6.25\text{ ms}$**.
* If the subwoofer is placed in a corner 3 meters from the couch, while the soundbar under the TV is 2.2 meters away, the path length difference is $\Delta d = 0.8\text{ m}$ ($\Delta t \approx 2.3\text{ ms}$).
* Wireless subwoofers transmit audio over a 2.4 GHz or 5.8 GHz proprietary RF link, which introduces an additional **$10\text{ to }25\text{ ms}$ of digital buffer latency** in the subwoofer's internal DSP.
* This latency differential causes the subwoofer arrival to be $180^\circ$ out of phase with the soundbar at the crossover frequency. Direct and subwoofer acoustic waves destructively cancel, creating a steep **$-12\text{ to }-20\text{ dB}$ notch at 80–100 Hz**.

```
Soundbar Wave:   ----+       +-------+       +-------+
                     \     /         \     /         \
                      +---+           +---+           +---
Subwoofer Wave:  ---+           +---+           +---+
(Delayed 180°)       \         /     \         /     \
                      +-------+       +-------+       +---
                                  |
                                  v
Resulting Sum:   =========================================  <--- TOTAL CANCELLATION
```

### 4.2 Why Equalization Cannot Fix Phase Cancellation
If an equalizer detects a 15 dB dip at 80 Hz and attempts to boost it by $+6\text{ dB}$:
* It increases the electrical drive to both the soundbar woofer and the subwoofer.
* Because the two sources are out of phase, increasing the power to both merely increases the cancellation energy.
* The cancellation dip remains completely uncorrected, but the subwoofer amplifier hits its clipping ceiling and driver excursion increases fourfold ($+6\text{ dB} = 4\times$ power), producing severe acoustic distortion and driver bottoming.

### 4.3 Core EQ Diagnostic Recognition
Core EQ distinguishes between a room modal null and a crossover phase cancellation:
1. **Impulse Response Time-Domain Analysis:**
   In Farina deconvolution, an exponential sine sweep produces an impulse response. If the impulse response contains two distinct arrival peaks separated by $10\text{–}25\text{ ms}$ with opposite initial polarity, a subwoofer phase delay is present.
2. **Crossover Notch Identification:**
   If a deep notch ($>8\text{ dB}$) is localized precisely at common crossover frequencies ($75\text{–}110\text{ Hz}$) and does not shift when moving between spatial measurement positions, Core EQ flags it as a *Crossover Integration Warning*.
3. **User Remedy Guidance:**
   Instead of applying an ineffective boost filter, Core EQ's UI informs the user:
   > *"A phase cancellation was detected around 80 Hz near your subwoofer crossover. Toggle the 0° / 180° phase switch on the back of your subwoofer or check soundbar subwoofer distance settings before applying EQ."*

---

## 5. Multi-Seat Spatial Averaging & Modal Variance

A classic failure mode of automated room correction is **over-equalizing for a single microphone point**. 

### 5.1 The Spatial Variance Problem
Low-frequency room modes have spatial nodes (pressure minima) and antinodes (pressure maxima):
* An antinode that causes a $+8\text{ dB}$ boom at the center seat may shift to a node ($-10\text{ dB}$ null) just $40\text{ cm}$ to the left.
* If a single-point measurement applies a severe $+6\text{ dB}$ boost to fill the null at the left seat, a listener sitting in the center or right seat experiences an unbearable $+14\text{ dB}$ modal resonance.

### 5.2 Global vs Local Room Modes
According to acoustician Floyd Toole (AES Fellow) and Earl Geddes:
* **Global Modes:** Low-order axial standing waves between rigid front and back room boundaries. These exhibit similar peak frequencies across the width of a standard seating couch. They can be safely attenuated using minimum-phase parametric notch filters.
* **Local Interference:** High-order tangential/oblique modes, quarter-wavelength boundary bounces, and furniture reflections. These change drastically with position. They must **never** be inverted with narrow high-Q filters.

### 5.3 The Core EQ 3-Point Spatial Measurement Protocol
Core EQ structures its multi-seat measurement into a 3-point couch envelope:

```
                      TV / Speaker Front
                              |
                              v
        [ Seat 1: Left ]   [ Seat 2: Center ]   [ Seat 3: Right ]
           (-40 cm)             (MLP)               (+40 cm)
```

1. **Energy-Domain Spatial Power Averaging:**
   Averaging across the 3 positions must be performed in linear energy ($p^2$), never in decibels or complex vectors:
   $$\bar{L}_{\text{energy}}(f) = 10 \log_{10} \left( \frac{1}{M} \sum_{m=1}^{M} 10^{L_m(f) / 10} \right)$$
   As proven in Pass 4, complex vector averaging ($p_1 e^{j\theta_1} + p_2 e^{j\theta_2}$) artificially creates destructive comb nulls at frequencies where acoustic phase rotates by $180^\circ$ over a few centimeters. Energy averaging computes the true sound power envelope across the seating area.
2. **Spatial Variance Metric ($\sigma_{\text{spatial}}^2$):**
   For each 1/3-octave band $k$, Core EQ calculates the inter-seat variance:
   $$\sigma^2(k) = \frac{1}{M} \sum_{m=1}^{M} \left( L_m(k) - \bar{L}(k) \right)^2$$
   * **Low Variance ($\sigma < 2.0\text{ dB}$):** The acoustic feature is globally consistent across all seats. Core EQ permits full modal correction (up to $-12\text{ dB}$ cut).
   * **High Variance ($\sigma > 4.5\text{ dB}$):** The feature is seat-localized interference. Core EQ limits correction to a conservative $\pm 2\text{ dB}$ broad adjustment, preventing degradation of adjacent seats.

---

## 6. Implementation Architecture & Algorithm Summary

Integrating Pass 6 findings into Core EQ updates the processing pipeline with the following formal parameters:

```
+-----------------------------------------------------------------------------+
|                      Core EQ Enhanced Processing Chain                       |
+-----------------------------------------------------------------------------+
  1. Capture Stimulus:
     - 48 kHz 16-bit Farina Logarithmic Sine Sweep (10 s)
     - Android AudioRecord (AudioSource.VOICE_RECOGNITION)
     - 35 Hz 4th-order Butterworth High-Pass Filter (handling noise rejection)

  2. Spatial Protocol (Optional 3-Point Mode):
     - Center (MLP) -> Left (-40 cm) -> Right (+40 cm)
     - Energy-Domain Averaging: 10 * log10( mean( 10^(L/10) ) )
     - Spatial Variance Gate: Inhibit deep notch cuts if variance > 4.5 dB

  3. Crossover & Phase Check:
     - Detect 75-110 Hz anomalies
     - If notch depth > 8 dB and delay delta > 5 ms: flag Crossover Warning

  4. Schroeder & Regime Partitioning:
     - Compute Schroeder f_s = 2000 * sqrt(RT60 / V)
     - Below Transition (f < min(2*f_s, 400 Hz)): Full modal inversion
     - Above Transition (f >= min(2*f_s, 400 Hz)): Gentle shaping (+/- 3 dB)

  5. Target Application:
     - Dialogue Target: ANSI S3.5 FIF (+2.0 dB across 2.2-4.5 kHz, sibilance roll-off)
     - Room / Harman Target: +3.5 dB bass shelf, -1 dB/octave tilt

  6. Regularization:
     - Zero out detected nulls (trend - measured > 6 dB)
     - AutoEq Bidirectional Slope Limiter (6 dB/octave)
     - Recenter 100 Hz-10 kHz (unless cut_only)
     - Edge Tapering: 1-octave raised cosine to 0 outside [40 Hz, 8000 Hz]

  7. Export:
     - Parametric .txt: Headroom Preamp + "Bands Overlap = Cascade" for Poweramp
     - GraphicEQ: 31-band ISO string
     - Profile .json: Full acoustic provenance (RT60, Schroeder, nulls, capability)
+-----------------------------------------------------------------------------+
```

---

## 7. Conclusion

Pass 6 completes the scientific foundation of Core EQ:
1. It validates that **uncalibrated remote MEMS microphones are acoustically linear ($\pm 1\text{ dB}$) within the 40 Hz–8 kHz band** because their Helmholtz package resonance is physically eliminated by the BLE ATVV decimation filter.
2. It solves **body shadowing and handling microphonics** via an extended-arm protocol and a 35 Hz high-pass filter.
3. It anchors the **Dialogue preset in ANSI S3.5 Speech Intelligibility Index standards**, eliminating low-frequency upward masking while accentuating core 2–4 kHz consonant intelligibility without high-frequency sibilance.
4. It safeguards against the **subwoofer crossover phase cancellation trap**, preventing dangerous amplifier boosts into acoustic cancellations.
5. It formalizes **multi-seat spatial power averaging** to guarantee that equalizing for one viewer improves—rather than destroys—the listening experience for the entire family.
