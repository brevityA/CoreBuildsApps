# Core EQ route and measurement-accuracy audit

**Audit basis:** source at PR #375 head `0d27653` plus Android API/AOSP and JBL public documentation reviewed 24 October 2026. This is a code/API audit, not a device qualification. CI and emulator results do not establish acoustic performance on a TV, phone, remote microphone, soundbar, or receiver.

**Implementation follow-up:** this audit's software-addressable safeguards have now been implemented in the working tree: ambiguous multi-output routes fail closed; pre-33 competing external endpoints are not priority-ranked; sweep route reports accumulate across playback so route changes become ambiguous; route evidence is labeled; the UI explains capture-score and frequency-range limits; and synthetic-score documentation no longer implies field accuracy. These changes have not yet passed a local Gradle run (Java is unavailable in this workspace). Physical validation remains outstanding and cannot be completed from this environment.

## Executive summary

Core EQ has a fail-safe for profiles tagged to different output kinds, records useful capture/backend provenance, and avoids presenting backend gain read-back as an acoustic measurement. The software follow-up now handles reported multi-output and competing pre-33 endpoint cases conservatively. The main unresolved limitation is route truth: Android generally does not expose another app's actual media sink to a third-party app. On API 33+, Core EQ asks for an *anticipated* media route; below API 33 it can only use a connected-output estimate when there is one external candidate. At measurement time the sweep's own `AudioTrack` route is better evidence, but it only describes that sweep's track, not whether another app's stream or the eventual listener's sound took the same acoustic path.

Android 33+ `getAudioDevicesForAttributes()` can return multiple devices for duplicated output. The current code treats multiple reported candidates as ambiguous instead of picking the first. On Android 36+, `AudioTrack.getRoutedDevices()` is used for the sweep; on earlier versions the single `getRoutedDevice()` is polled. Distinct endpoints observed during the sweep are accumulated so a detected route change also becomes ambiguous. This is a software safeguard, not proof that every brief route change is detected or that one profile represents a mixed acoustic field.

Measurement is a chain, not just DSP: loudspeaker response and DSP → Android route/encoding → room and placement → remote/TV microphone, its firmware/codec and any residual processing → sample-rate conversion and analysis → limited-band EQ backend → vendor audio policy/HAL and downstream soundbar DSP. Software tests cover selected algorithmic properties; they cannot certify this end-to-end transfer function. In particular, a high SNR score means a clean capture under the score's definition, not a calibrated microphone or accurate absolute frequency response.

## Route-by-route findings

The kinds below are the kinds currently recognized by `OutputRoute.kindOf()`. Android's reported device type identifies a framework endpoint category; it does not fully identify the signal format, downstream processing, or acoustic transducer.

| Route | Current Core EQ handling | What can be said safely | Main uncertainty / validation needed |
|---|---|---|---|
| Built-in TV speaker | `TYPE_BUILTIN_SPEAKER` → `speaker`; generic TV-speaker label | Sweep route may identify the TV speaker endpoint; profile matching keeps this distinct from HDMI/BT/USB profiles. | TV sound modes, dynamic EQ, loudness, speaker protection, and vendor DSP can change the response between measurement and playback. Confirm same sound mode and processing state. |
| HDMI ARC / eARC | `TYPE_HDMI_ARC` and API-31 eARC constant → `hdmi_arc` | The route category is classified as a soundbar/receiver-type HDMI return path. The service warns that encoded Dolby/DTS may bypass Android EQ when the system surround setting permits it. | ARC/eARC does not itself mean PCM or bitstream. Actual format, TV policy, CEC route changes, Atmos/DTS passthrough, and whether an Android effect lies in that path are device/content dependent. Verify PCM and bitstream separately on each TV. JBL Bar 800 supports eARC and multichannel features, but that product capability does not prove Core EQ coverage. |
| Other HDMI | `TYPE_HDMI` → `hdmi` | It is kept separate from ARC/eARC profiles. | It may mean an HDMI output topology unlike a soundbar return; the framework kind alone cannot distinguish transport format or processing. Verify actual wiring and content path. |
| Bluetooth A2DP / BLE headset / BLE speaker | Recognized types → `bluetooth` | Bluetooth output can be distinguished as an endpoint category. | Codec, sample-rate conversion, latency, DSP, mono/stereo behavior, and reconnection/routing can vary. A connected Bluetooth device is not necessarily the active sink on pre-33 fallback. Test the actual connected product and playback format. |
| USB device / headset / accessory | → `usb` | USB audio has a distinct output kind. | USB DAC format negotiation, device DSP, volume stage, channel layout and route policy are vendor/device dependent. Presence does not establish it is the active route on API <33. Test the exact adapter/DAC and output format. |
| Wired headphones / wired headset / analog or digital line | → `wired` | Wired output category is separately tagged. | A line output may feed an external amp/DSP; device type does not tell line-level calibration, gain, or downstream EQ. Confirm physical path and volume/gain. |
| Earpiece, telephony, bus, remote submix, unfamiliar device | Not mapped to a listening kind | This avoids assigning a known profile to an unrecognized category. | The route may be a valid custom/vendor output. It should remain “unknown” until device-specific mapping and testing; do not infer speaker equivalence. |
| Simultaneous / duplicated outputs | API 33+ anticipated lists and API 36+ sweep routed-device lists with multiple entries resolve to `ambiguous`; pre-33 competing external candidates also pause | Route evidence may describe multiple endpoints; no single profile safely represents a combined field. | Polling may miss brief route changes, and only physical testing can verify the acoustic mixture. |

### Route selection by API level

- **API 33+:** `OutputRoute.current()` requests `getAudioDevicesForAttributes()` for media/movie attributes. Android describes this as an anticipated route, not a guarantee of the active route of another app's track. It can change as devices/policy change and can return multiple devices for duplicated routing. The current implementation now marks a multi-entry result ambiguous rather than selecting the first.
- **API 30–32:** `OutputRoute.current()` uses a connected-output estimate, not another app's active route. If exactly one external candidate is known, it uses that estimate; if multiple distinct external candidates are connected, it marks the route ambiguous and pauses correction. This may pause correction even if one candidate is inactive, but avoids silently selecting an arbitrary one.
- **During measurement:** `StimulusPlayer` asks its own playing `AudioTrack` for its routed device(s) and `OutputRoute.tag()` prefers that report over the estimate. This is stronger evidence for the sweep track, not “ground truth” for sound arriving at the mic: an effect/track route can still be muted, duplicated, encoded, redirected downstream, or processed by vendor DSP. The route is polled at 100 ms intervals; short changes can be missed.
- **API 36+:** `AudioTrack.getRoutedDevices()` is used to detect multi-device sweep routing. On earlier APIs, the single `getRoutedDevice()` is monitored; distinct endpoints observed during playback accumulate into an ambiguous result.

### Correction backend and content paths

`EqService` first tries an output-mix effect on session 0; if that path is refused, the optional per-session path depends on DUMP-discovered sessions. It verifies important configured/read-back properties and enabled/control state, which is good configuration evidence. But Android deprecates session-0 insert effects, third-party apps cannot assume global mix coverage, and vendor policy/HAL determines the signal paths and effects actually honored. A successful effect object, `hasControl()`, or reported gain read-back is not proof that every player, output, encoded bitstream, or downstream device was processed.

The HDMI passthrough warning is appropriately conditional and useful. Its presence is a warning based on system surround settings, not an observation of every stream's actual encoding. A TV may output PCM for one title and a bitstream for another; validate both. The export-to-TV-EQ route is a separate manual path and must be checked against the TV's actual band centres, gain limits, preamp/headroom and selected sound mode.

## Measurement and accuracy chain

### Stimulus and capture bandwidth

- `Sweep` defines a 20 Hz–20 kHz logarithmic sweep, but its usable upper bound is limited by sample rate (`bandTopHz` is `min(fEnd, 0.49 * fs)`). `CaptureEngine` requests 16 kHz first, then 48 kHz, then 44.1 kHz, and resamples accepted capture to 16 kHz. At 16 kHz analysis, Nyquist is 8 kHz and the sweep guard is approximately 7.84 kHz. Therefore the end-to-end measured/corrected response is not 20 kHz, regardless of the original sweep's nominal end frequency. Resampling cannot restore information above the capture bandwidth.
- The sweep is mono. It measures the acoustic field at the microphone position, not independently calibrated left/right channels. A mono measurement cannot establish stereo matching or spatial uniformity.
- `VOICE_RECOGNITION` is selected to avoid speech AGC/noise suppression and capture effects are queried/disabled where possible. This does not certify a flat response: source behavior, routing, remote-microphone firmware, microphone orientation, codec, filtering, automatic processing outside exposed effects, and input hardware can still alter frequency response.
- Android's `UNPROCESSED` support property and `MicrophoneInfo.frequencyResponse` are recorded where available. The code retains microphone response as provenance; it does not automatically deconvolve it. A reported metadata curve is not necessarily a calibrated end-to-end correction for a remote mic in its actual orientation/firmware path.
- SNR, clipping, route identity and sample-rate provenance are useful checks, but SNR cannot expose a smooth mic coloration: a clean, quiet capture can still be systematically wrong in frequency response.

### DSP and room inference

`MeasurementQuality` intentionally separates capture quality from room properties. Its score maps 20–50 dB SNR to 0–100 and uses synthetic-room results as rationale. That is a *recording quality heuristic*, not an accuracy certificate. The synthetic tests do not include the full distribution of TV/remote microphones, speaker nonlinearities, codec changes, background sound, room geometry, or vendor DSP. Avoid presenting the score as “measurement accuracy” or promising a dB error bound for an actual household.

The correction protects against some unreliable conditions (capture quality gates, null handling, phase gating, transition behavior, headroom). These safeguards reduce known failure modes; they do not resolve microphone calibration or route ambiguity. Deep narrow nulls are generally not recoverable with EQ and boosting them risks wasted headroom; a single microphone location cannot correct the whole listening area. Changing listener/mic position or soundbar mode after measurement invalidates at least part of the result.

### Mapping to a playback EQ

`BandMapping` samples the requested curve at backend-reported/selected band centres, shifts down by the largest requested boost for digital headroom, and clamps to a reported Equalizer range. The read-back residual compares target centre gains with gains the backend reports. It is explicitly not an acoustic response measurement. It does not reveal the filter shape between centres, frequency precision, Q, vendor implementation, channel differences, dynamic processing, or whether the effect is on the listener's signal path. Headroom can also lower overall loudness; users should level-match when judging before/after.

The Android `DynamicsProcessing` path verifies configured PreEQ layout, gain, enable/control state and limiter fields. This gives stronger evidence that the framework object accepted the configuration than a write-only configuration would. It still does not quantify acoustic response or establish effect reach on every vendor path.

## Evidence classes (keep these distinct in UI/release notes)

1. **Code verified:** how the app classifies known `AudioDeviceInfo` types, selects a route estimate, tags a measurement, maps the curve to backend bands, and records capture metadata.
2. **Android API evidence:** documented API behavior such as anticipated media routing, per-track routed-device reporting, audio effect/session semantics, and capture metadata availability.
3. **Vendor-dependent:** actual routing and effect reach through TV audio policy/HAL, HDMI formats, passthrough, remote-mic behavior, codec/processing, and output DSP.
4. **Acoustic evidence:** only a controlled physical measurement of the complete signal chain can support claims about response error, correction accuracy, route coverage, or repeatability. No such device test is evidenced for this branch.

## Recommendations, prioritized

### P0 — route safety before claiming “all routes”

1. **Implemented:** API 33+ anticipated route lists with more than one endpoint (including recognized plus unknown) resolve to `ambiguous`; profile selection refuses even legacy cross-route profiles. Unit tests cover ambiguous route and legacy-profile refusal.
2. **Implemented:** API 36+ sweep routing reads `AudioTrack.getRoutedDevices()`; earlier APIs use the single routed device. The sweep accumulates every distinct endpoint observed while it plays. Multiple endpoints or a mid-sweep route change becomes ambiguous rather than being tagged to only the final destination.
3. **Implemented:** route wording now distinguishes anticipated, connected-output estimate, track-reported, and ambiguous evidence; the “ground truth” wording is removed.
4. **Implemented:** API <33 keeps a connected-output estimate only when a single external candidate is present. Competing external endpoints pause correction rather than selecting the priority winner; the built-in speaker is excluded from competition because it is commonly listed alongside an external sink.
5. **Implemented with polling limitation:** sweep route is monitored during playback at 100 ms intervals. A shorter transient or route change to a no-route state may be missed; validate event-based route monitoring and changes during playback on hardware before treating this as complete.

### P1 — make measurement claims honest and useful

6. **Implemented:** user-facing score is called capture quality and explicitly says signal-to-noise only, not microphone calibration or acoustic accuracy. Synthetic-score rationale no longer presents a remote codec result as general.
7. **Implemented:** measurement result shows the present ~7.8 kHz analysis limit; onboarding explains the remote mic is not necessarily calibrated. The sweep generator's nominal 20 kHz limit is not presented as measured coverage.
8. **Partially implemented:** first-run and measurement-ready text tell users to choose their intended output/mode/volume, keep processing unchanged, quiet the room, place the mic at the main seat, and measure other routes/modes separately. The more detailed workflow (disable adaptive processing where possible, verify PCM for mixer-EQ, exact placement/orientation) remains an enhancement.
9. **Existing capability, bounded:** Core EQ already has a correction-check sweep and same-position repeat comparison. The UI/docs explicitly say repeatability is descriptive, not a physical accuracy threshold. It does not establish acoustic accuracy without calibrated hardware and level-matched physical validation; no new verification claim is made here.
10. **Implemented:** route-specific profile matching is retained; ambiguous routes select no profile, including legacy untagged profiles, and service status explains why correction is paused.

### P2 — physical validation matrix

Use a calibrated measurement microphone/interface and a trusted reference workflow (for example, REW with a loopback or independently verified playback chain). For each supported TV model/OS build and output, capture: route-reported vs physically active endpoint; PCM stereo and multichannel PCM; Dolby/DTS bitstream where relevant; each TV/soundbar processing mode; sample format/rate; effect status; uncorrected and corrected response; repeatability across three measurements; level-matched distortion/clipping; and listening-position variance. Test single and duplicated routes, hotplug/reconnect, route changes during measurement, and competing active audio. Include a JBL Bar 800 eARC path only as a specific test case, not as a proxy for all soundbars.

A valid claim should be bounded, e.g. “on [TV firmware, output path, sound mode, format, mic, position], the measured residual/repeatability was [result].” No generic “accurate on all TVs/soundbars” claim is supported by the present evidence.

## Sources

### Android API and AOSP

- [AudioManager reference](https://developer.android.com/reference/android/media/AudioManager) — anticipated route for audio attributes (`getAudioDevicesForAttributes`); it is not another app's currently playing track route.
- [AudioTrack reference](https://developer.android.com/reference/android/media/AudioTrack) — route reporting for a particular track while playing; API 36 multi-device route API.
- [AudioEffect reference](https://developer.android.com/reference/android/media/audiofx/AudioEffect) — effect/session behavior and deprecated output-mix session-0 insert effects.
- [AOSP audio effects](https://source.android.com/docs/core/audio/audio-effects) — effect implementation/configuration depends on platform/vendor integration.
- [AOSP Android TV audio](https://source.android.com/docs/core/audio/tv) — TV output device/path handling is platform-specific.
- [AudioRecord reference](https://developer.android.com/reference/android/media/AudioRecord) — capture route and active-microphone APIs do not certify response calibration.
- [AOSP audio pre-processing](https://source.android.com/docs/core/audio/implement-pre-processing) — capture source/pre-processing contracts, not a guarantee for a particular remote microphone.
- [Android TV audio capabilities](https://developer.android.com/training/tv/playback/audio-capabilities) — recommends inspecting playback-track routing rather than inferring solely from connected devices.

### Product-specific context

- [JBL Bar 800 product page](https://global.jbl.com/soundbars/BAR-800-.html)
- [JBL Bar 800 owner manual](https://no.jbl.com/on/demandware.static/-/Sites-masterCatalog_Harman/default/dwfc9ba8c3/pdfs/JBL_HA_Bar%20800_OM_V6_EN.pdf)

These JBL sources establish product features and its own calibration workflow only; they do not establish Core EQ route coverage or acoustic accuracy.

## Audit conclusion

The code supports route-aware profiles and now fails closed for reported multi-output routes, but route identification is still not universally authoritative: anticipated/connected routes cannot prove another app's active sink, route polling can miss short events, and API 36 behavior is not physically verified here. The UI calls the score capture quality, states the analysis range and calibration caveat, and explains route evidence. For acoustic accuracy or full TV/soundbar route coverage, physical measurements—not CI, emulator installs, API documentation, or backend read-back—remain mandatory. Software changes reduce known misclassification and overclaim risks; they do not qualify hardware performance.
