# Core EQ phone companion: product and technical readiness

**Research date:** 2026-10-11
**Scope:** Record the product decision, implementation boundary, and measurement qualification work for a Core EQ phone companion. This is a research and readiness brief, not evidence of device-level calibration or validation.

## Executive summary

**User decision (2026-10-11):** v1 should do both—control Core EQ on the TV and capture measurements with the phone microphone—and should target Android phones. The two jobs share a TV-pairing/session layer, but measurement remains a separate, explicitly started and cancellable audio session. iOS is not in v1 scope.

The TV app owns playback, captures through the Android-selected TV/remote input, analyses a single position, and stores room profiles locally. The companion implementation adds an opt-in TV-side LAN endpoint and an Android phone UI; it is separate from ADB, which remains a developer/setup tool rather than the runtime transport. The endpoint and phone client have not yet been built or exercised on physical TV/phone hardware.

A sensor companion is technically possible, but it changes a calibrated measurement from a same-device sweep/capture into a distributed audio session. The phone hears the TV speaker or downstream audio system in the room; start delay, playback route, phone mic processing, clock drift, distance, orientation, and the phone model all matter. A microphone's frequency-response metadata, when the OS exposes it, is not by itself a complete room-measurement calibration.

## Two distinct products often conflated as “companion”

| Product role | Phone does | TV must do | Main risk |
|---|---|---|---|
| **EQ remote** | Select output/profile/mode, enable correction, inspect status, perhaps trigger a sweep | Expose a supported, authenticated control API and report state | Android TV does not give an ordinary app unrestricted control of every other app or the system audio path. ADB is a developer tool, not the consumer transport. |
| **Measurement sensor** | Place at the listening position, record the TV's sweep, send samples or analysis data | Play a known sweep, signal start/stop, accept and validate the result | Phone input processing and the playback/capture clocks/routes can make the result model- and session-dependent. |
| **Both** | Remote plus sensor | Provide the control API and a measurement-session protocol | Combines both risks; pairing, reconnect, state consistency, privacy, and a clear fallback are all required. |
| **Profile viewer/importer** | View/export/import files | No live service needed | Safer first step, but does not control or measure the TV. |

## Measurement and OS constraints

### Android phones

- Android provides `AudioRecord` for capture and `MicrophoneInfo` for characteristics of microphones that the platform exposes. The latter can include a frequency-response list, sensitivity, location, and orientation; the list can be absent, incomplete, or for a particular active input rather than a lab calibration. Store it as provenance until a per-model validation establishes how it should be used.
- Android's audio guidance warns developers not to assume mobile microphone/speaker acoustics are good and notes that platform processing and input/output latency vary. Nominally matching sample rates do not prove that independently-clocked TV playback and phone recording are synchronized.
- `UNPROCESSED` capture is an optional capability, not a guaranteed route. Query support and record the route/effect state; do not label arbitrary phone recordings “raw” or “calibrated.”
- A second app that discovers or connects to the TV over the LAN will need a deliberate permission story. Android's current Local Network Protection guidance says Android 17 (API 37) requires `ACCESS_LOCAL_NETWORK` for low-level LAN operations when an app targets API 37 or later; this repository's Core EQ TV app targets 37. Discovery APIs and lower-target behavior have transition details, so verify against the final target and phone matrix rather than copying a permission recipe blindly.

### iPhone

- Apple's `AVAudioSession` measurement mode is intended for apps performing audio measurement. It does not make every iPhone model a calibrated measurement microphone; placement, model differences, route changes, and acoustic validation still matter.
- Local-network discovery/communication needs a user-facing usage description and, when Bonjour is used, the appropriate service declaration.
- An iPhone version is a separate Swift/iOS implementation and release pipeline. It should share a versioned wire format and DSP test vectors with the TV, not be treated as a drop-in Android build.

### Distributed sweep requirements

If phone capture is chosen, a credible first measurement flow must:

1. Pair explicitly with the TV and show which TV/output is in the session.
2. Ask for microphone permission only when the user starts a measurement; show a clear live recording indicator and stop control.
3. Record the entire sweep and decay tail locally. Do not send microphone audio to a cloud service.
4. Timestamp the session and include phone model/OS, requested and actual sample rate, audio source/mode, active route, supported processing state, clipping/SNR, and any reported mic-response metadata.
5. Use a known sync marker/correlation procedure and test jitter/clock drift rather than assuming a phone command timestamp equals acoustic start time.
6. Reject or warn on clipped, interrupted, silent, or unsupported captures; preserve a truthful “unknown” result when route or calibration data is unavailable.
7. Keep the existing single-position workflow unless multi-position capture is separately designed and validated. Spatial averaging remains research, not an assumed feature.

## TV control, pairing, and security boundary

- At research kickoff there was no Core EQ LAN server or mobile control endpoint. The current implementation instead uses an app-owned, on-demand TCP endpoint on the TV; it does not mutate TV files or call phone `localhost`.
- The TV listener runs only while the user keeps the companion screen open. It requires TV-initiated, expiring one-time pairing; limits bad pairing attempts; replaces/revokes a phone token; and exposes only status, saved profiles, correction enable/disable, listening mode, and a bounded measurement session. The phone stores its token encrypted with an AndroidKeyStore AES key. TV and phone pairing preferences are excluded from cloud backup and device transfer.
- Pairing and RPC payloads use AES-GCM with endpoint-bound additional data, unique nonces, replay checks, and request-age checks. Phone and TV inputs are restricted to private IPv4 addresses and private-LAN clients. This is an app-layer authenticated-encryption design, not a claim that a home network or either endpoint is invulnerable; keep pairing limited to a trusted LAN and do not expose/port-forward the listener.
- Android's documented Wireless debugging pairing is for workstation development and uses separate, dynamic pairing and connection ports on Android TV 13+; it is not a runtime companion API. The setup screen labels this boundary.
- The phone flow requests local-network access on Android 17 / API 37 when needed. Audio permission is requested only when a user starts a measurement. Capture is foreground-only, cancellable, buffered on the phone, and uploaded only over the paired TV link; it is not an always-on mic or a cloud service.

## Selected v1 scope and next qualification work

The user selected **both TV control and phone-microphone measurement**, with **Android phones** first. The iOS client, general remote-control keys, app launching, volume control, and arbitrary system settings remain out of scope.

1. Build and test the pairing/control protocol on Android TV and Android phones: reconnects, TV sleep/reboot, changed IP, multiple TVs, guest-network isolation, permission denial/revocation, and listener teardown when the TV screen closes.
2. Prototype a **single Android phone model + TV/output pair** in one room. Compare repeated phone captures to a calibrated USB measurement microphone at the same position and playback level.
3. Repeat across phone orientations, distances, Android processing modes, input routes, and at least one other phone model before widening support or describing the phone microphone as a measurement instrument.
4. Store a device/input calibration only when it is measured and traceable. Android-reported microphone response remains provenance only; it is not applied to the room curve.
5. Keep the TV-remote microphone path as the existing fallback. Multi-position averaging remains a research candidate, not a v1 measurement feature.

## Acceptance checklist for any phone companion

- User can identify/pair the intended TV without ADB and revoke the pairing from either side.
- No control endpoint accepts unauthenticated state-changing commands; wrong-code attempts expire or rate-limit.
- Core EQ continues to work from the TV remote if the phone is absent or disconnected.
- Any measurement names the exact phone, input route, sample rate, processing state, and playback output; unsupported details say “not reported.”
- Repeat measurements are stable within a predeclared error budget against a calibrated reference; the device/OS matrix is published.
- Capture stays local unless the user separately exports it; no ambient capture runs outside the explicit measurement session.
- Android phone and TV D-pad flows, permission denial/revocation, interruptions, route changes, disconnects, and TV reboot are tested. If iOS is selected, the equivalent Local Network and microphone permission paths are tested too.

## Sources

- Android `AudioRecord` and active microphones: <https://developer.android.com/reference/android/media/AudioRecord>
- Android `MicrophoneInfo`: <https://developer.android.com/reference/android/media/MicrophoneInfo>
- Android audio latency and the warning not to assume mobile audio performance: <https://developer.android.com/ndk/guides/audio/audio-latency>
- Android TV hardware and the distinction between built-in TV microphones and controller microphones: <https://developer.android.com/training/tv/get-started/hardware>
- Android microphone permission, privacy indicators, and the system microphone toggle: <https://developer.android.com/training/permissions/explaining-access>
- Android local-network permission changes: <https://developer.android.com/privacy-and-security/local-network-permission>
- Android ADB wireless debugging, TV OS requirement, and distinct pair/connect ports: <https://developer.android.com/tools/adb>
- Apple `AVAudioSession.Mode.measurement`: <https://developer.apple.com/documentation/avfaudio/avaudiosession/mode-swift.struct/measurement>
- Apple local-network usage description and Bonjour service discovery: <https://developer.apple.com/documentation/bundleresources/information-property-list/nslocalnetworkusagedescription>, <https://developer.apple.com/documentation/network/nwbrowser/descriptor-swift.enum/bonjour(type:domain:)>
- Existing Core EQ measurement and capture limitations: [`core-eq-measurement-and-capability-2026-09.md`](core-eq-measurement-and-capability-2026-09.md), [`core-eq-precedents-poweramp-and-audio-path-2026-09-27.md`](core-eq-precedents-poweramp-and-audio-path-2026-09-27.md)

## Resolved user decision

**Resolved 2026-10-11:** v1 does both—TV control and phone-microphone capture—and targets Android phones. The code is an unverified prototype until an Android build and physical-device test matrix pass. In particular, it makes no claim that phone microphones are calibrated, Android preserves a selected input route or processing state, or measurements are consistent across phone models.
