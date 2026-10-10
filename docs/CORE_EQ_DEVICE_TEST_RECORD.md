# Core EQ device test record (template)

Use one record per TV and output combination. Fill it in on the hardware, and
do not copy a claim from one device to another. Source: the routing and
capability notes in `docs/research/` (2026-09-27 to 2026-10-05). Those notes
say Android does not show which output another app's stream uses, so every
route is an estimate until a measurement backs it.

Do not paste private dump text. Record only the fields below.

## 1. Identity

| Field | Value |
| --- | --- |
| TV model (from Settings › Device Preferences › About) | |
| TV manufacturer string (`Build.MANUFACTURER`, from the setup check) | |
| TV model string (`Build.MODEL`, from the setup check) | |
| Firmware / build | |
| Android / API level | |
| Date and tester | |

## 2. Output

| Field | Value |
| --- | --- |
| Output route (TV speakers, HDMI ARC, HDMI eARC, Bluetooth, other) | |
| Output name Android reports (setup check › Sound output) | |
| Output brand and model, if any (soundbar or speaker) | |
| TV surround / audio format setting (PCM, Auto, Manual, Bitstream) | |
| Source app and content (name the app, not the title) | |
| Stream believed PCM or direct/passthrough? | |

## 3. Setup check result

For each row, write the state the setup check showed (done / needed / info):

| Row | State | Notes |
| --- | --- | --- |
| Connect ADB from a computer | | |
| Read the playing app (DUMP) | | |
| Show the on-screen card | | |
| Sound output | | |
| Check the TV's surround setting (info) | | |
| Detected devices (TV and output brand) | | |
| Measure this output | | |
| Detected hardware | | |

Record whether the brand detection was right. Note any ambiguity row shown
over HDMI or ARC.

## 4. Verification

| Check | Result (pass / fail / not run) | Notes |
| --- | --- | --- |
| `OutputRoute.current()` matches the output on screen | | |
| `getAudioDevicesForAttributes(USAGE_MEDIA)` before and during playback | | |
| Session-0 effect construct / control / read-back | | |
| Equalizer or DynamicsProcessing applies (clamped to device range) | | |
| Acoustic before and after measurement, TV speakers | | |
| Acoustic before and after measurement, soundbar (ARC or eARC) | | |
| Passthrough: PCM versus bitstream compared | | |

## 5. Presets

| Preset | Applied? | Listener verdict (better / same / worse, with reason) |
| --- | --- | --- |
| | | |

Preset labels stay "reviewer-informed, not measured" unless this record
includes a measurement for that device.

## 6. Limits

State anything not run, and why. Example: "no soundbar connected; not run".
