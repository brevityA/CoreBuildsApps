# JBL Bar 800 and compact Bar 2.0: Core EQ research notes

**Research date:** 25 October 2026 (Australia/Sydney)
**Basis:** JBL Australia product pages and owner manuals, plus the existing Core EQ hardware-preset implementation. This is a documentation and route-interpretation review—not a listening test, frequency-response measurement, or hardware qualification.

## Model identification first

“JBL 800” and “JBL compact 2.0” are not sufficient identifiers for revision-specific processing. Check the label/model number on the bar (and, for Bar 800, the subwoofer) before choosing a model-specific tone overlay.

| Device | Official product identity | Useful distinctions |
|---|---|---|
| Original **JBL Bar 800** | Bar 800; model name does not include MK2 | 5.1.2-channel system, two detachable surrounds, 10-inch wireless subwoofer, 720 W maximum; HDMI eARC/ARC and HDMI input. The AU V9 manual documents two-stage surround calibration and separate subwoofer, surround and Atmos-effect level controls. |
| **JBL Bar 800MK2** | Bar 800MK2 | A distinct newer 7.1-channel system, 780 W maximum, still with detachable surrounds and a 10-inch subwoofer. It adds a different platform/features (including MultiBeam 3.0, DTS Virtual:X, PureVoice 2.0 and SmartDetails on the AU product listing). Do not reuse the original Bar 800 overlay merely because the name contains “Bar 800.” |
| Original **JBL Bar 2.0 All-in-One** | Bar 2.0 All-in-One, without MK2 | Compact 2-channel bar; 80 W maximum (2 × 40 W), two racetrack drivers, published 70 Hz–20 kHz response. HDMI ARC, optical and Bluetooth. |
| **JBL Bar 2.0 All-in-One MK2** | Bar 2.0 All-in-One (MK2), AU SKU JBLBAR20AIOM2BLKAS | Also 2-channel, 80 W (2 × 40 W), two racetrack drivers and 70 Hz–20 kHz stated response. HDMI ARC, optical and Bluetooth. Similar headline specs do not prove identical tuning or revision. |

The manuals for both Bar 2.0 revisions describe **Smart Mode** (EQ settings plus JBL Surround Sound) and **Standard Mode** (preset EQ). They do not document user-adjustable parametric EQ or a dedicated acoustic room-calibration procedure. Thus, “no EQ” is too broad; the defensible statement is **no documented user-parametric EQ**. The Bar 800 also has built-in processing: the original manual describes Smart Mode and a Standard Mode using preset EQ, and JBL's separate calibration is not Core EQ calibration.

## Core EQ route interpretation

The TV is the key boundary. Core EQ can only affect audio that actually passes through the Android TV software output/effect path. JBL's own soundbar DSP runs downstream of TV audio on a TV connection, and a soundbar feature or calibration does not prove that Core EQ is active on that route.

- **TV app/device → TV → HDMI ARC/eARC or optical → soundbar:** a possible Core EQ path, but verify the active playback-track route on the TV and test the exact TV audio format/output mode. A connected HDMI/ARC device name alone is not evidence that every app or passthrough format is processed.
- **Source plugged into the Bar 800's HDMI input:** audio can be decoded/processed at the bar without passing through the TV's Core EQ path.
- **Bluetooth from a phone/tablet directly to the bar, or Bar 800's own Wi-Fi/streaming services:** these are soundbar/device-originated paths, not TV audio; Core EQ on the TV does not sit in their signal chain.
- **Bluetooth from the TV to the soundbar:** it is still a TV-originated route, but treat it as a distinct route to verify rather than assuming its effect behavior matches ARC/eARC.

For repeatable measurements, keep the TV output format and soundbar processing fixed. First run JBL's own supported room/surround calibration in the final room layout; then measure/apply Core EQ on the intended TV-to-bar route. Record whether Smart or Standard mode is selected, the soundbar's bass/surround/Atmos controls, speaker placement, and the TV output format. On the original Bar 800 manual, Smart Mode is enabled by default again after power-on, so a prior Standard Mode selection should not be assumed to persist across a restart. Compare the resulting profile in the mode intended for everyday use.

Do not treat the published 70 Hz–20 kHz range or a wattage figure as a measured in-room response. No measurement from either of the user's soundbars is available here. The existing “Movie,” “Dialogue,” and similar hardware overlays are subjective listening starting points, not corrections derived from an acoustic measurement; level-match and evaluate them on the intended route. An 8 kHz global EQ lift does not selectively affect Atmos height channels.

## What changed in Core EQ

- The Bar 800 name matcher now rejects an explicit **MK2** suffix, preventing the newer Bar 800MK2 from receiving the original Bar 800's subjective overlay.
- The Bar 2.0 model description now acknowledges its documented Smart/Standard EQ modes rather than saying “no EQ.”
- JBL documents the Bar 2.0 MK2 Bluetooth pairing name as simply **“JBL BAR 2.0”**, the same bare model name expected for the family. That name cannot identify the revision, so Core EQ now declines to select the original Bar 2.0 model from that bare Bluetooth name. An explicit MK2 name is also excluded. The exact label/model number remains the reliable way to confirm which revision the owner has.
- The original Bar 800 preset's 8 kHz note now describes a global treble lift, not an Atmos-height-only adjustment. The Bar 2.0 125 Hz note no longer asserts that it restores sound below a hard 70 Hz cutoff.

## Official references

1. JBL Australia, [original Bar 800 product page](https://www.jbl.com.au/outlet/BAR-800-.html).
2. JBL Australia, [original Bar 800 owner's manual, V9](https://www.jbl.com.au/on/demandware.static/-/Sites-masterCatalog_Harman/default/dw2b4c8435/pdfs/JBL_HA_Bar%20800_OM_V9_EN.pdf).
3. JBL Australia, [Bar 800MK2 product page](https://www.jbl.com.au/BAR-800MK2.html) and [Bar 800MK2 owner's manual, V6](https://www.jbl.com.au/on/demandware.static/-/Sites-masterCatalog_Harman/default/dwd93b0d06/pdfs/HA_JBL_Bar%20800MK2_OM_Global_EN_V6.pdf).
4. JBL Support, [Bar 800 surround-speaker calibration guidance](https://support.jbl.com/us/en/howto/jbl-bar-700-800-1000-surround-speaker-calibration-us/000037071.html).
5. JBL Australia, [Bar 2.0 All-in-One MK2 product page](https://www.jbl.com.au/BAR-2-0-ALL-IN-ONE-MK2.html) and [MK2 owner's manual, V2](https://dk.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dw64c558de/pdfs/JBL_SB_Bar%202.0%20(MK2)_OM_EN_V2.pdf).
6. JBL, [original Bar 2.0 All-in-One owner's manual](https://www.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dwd611dc7f/pdfs/JBL%20_Bar%202.0_All%20in%20One%20Owner's%20Manual%20_EN.pdf).
7. Android Developers, [AudioTrack route reporting](https://developer.android.com/reference/android/media/AudioTrack) and [AudioManager](https://developer.android.com/reference/android/media/AudioManager).

**Validation status:** code tests were not run in this environment because Java/Gradle is unavailable. No phone, TV, soundbar, or calibrated microphone test was performed.
