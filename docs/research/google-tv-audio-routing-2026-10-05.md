# Google TV audio routing: what an ordinary app can know

**Research snapshot:** 2026-10-05
**Target:** generic, stock Android/Google TV; no root, privileged install, system-image changes, or OEM-specific API. A one-time ADB grant is acceptable only if the device permits it.

## Executive finding

A normal app can inspect **connected output devices**, ask Android for the **anticipated route for a chosen `AudioAttributes` set**, observe coarse system playback activity, and query the route of **its own** currently playing `AudioTrack`. It cannot use the public SDK to ask “which device is this other app's stream actually using?” or to prove that a global audio effect processed that stream.

That distinction is central to Core EQ. Its `OutputRoute.current()` result is an estimate; the playback callback is an activity hint; and successful creation/configuration of a session-0 effect is not proof of end-to-end coverage. A particular TV can still route a public output-mix effect across most apps—as the maintainer has observed with side-loaded Poweramp EQ on an unspecified setup—but that observation cannot be generalized to a generic stock TV without the model, Android build, output, format, and effect settings.

## Public probes and what they establish

| Public API / signal | What an ordinary app can observe | What it cannot establish |
|---|---|---|
| `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` and `AudioDeviceInfo` | The output devices Android currently reports as available/connected, their public type/name, and some device format capabilities. | Which output is carrying a particular other app's current stream. A connected HDMI device or Bluetooth sink is not proof that it is the active media route. |
| `AudioManager.getAudioDevicesForAttributes(attributes)` (API 33+) | The devices Android anticipates for a newly created `AudioTrack` with those attributes. Android explicitly describes this as an anticipated route that can change. | The actual route of another app's already-running track, its codec path, or whether its data was mixed/processed. |
| `AudioManager.AudioPlaybackCallback` / `getActivePlaybackConfigurations()` (API 26+) | A system playback-activity change and the public playback configurations, including `AudioAttributes`; useful as a coarse “media-like activity” hint. | A public UID/package/session identity or a dependable per-player route. `AudioPlaybackConfiguration.getAudioDeviceInfo()` is documented as never populated and deprecated in API 36. The callback does not prove audible output or effect coverage. |
| `AudioTrack.getRoutedDevice()` / `AudioRouting` | The current device and route-change callbacks for an `AudioTrack` that this app owns; the query is valid while that track is playing. Core EQ can use this for its own measurement stimulus. | The route of another app's `AudioTrack` or `MediaPlayer`. The API is object-scoped, not a system-wide route query. |
| `AudioDeviceCallback` | Output-device additions/removals. | Whether the active stream moved, whether it is direct/bitstream, or the route used by a particular player. |
| `MediaSessionManager.getActiveSessions()` | Published media sessions and their controllers if the app holds the system-only `MEDIA_CONTENT_CONTROL` permission or the user enables the app as a notification listener. | All audio players, current audibility, output route, or audio-effect coverage. Session metadata is not the audio pipeline. This adds a broad user-granted notification-listener surface and is not used by Core EQ. |
| `AudioManager.getDirectPlaybackSupport()` / device audio profiles / encoded-surround settings | Whether a format/attribute combination may use direct playback on the currently routed path (API-dependent), plus device capabilities and the system's surround-output preference. This helps explain whether PCM mixing is plausible. | Which format another app actually selected, whether it is currently bitstreaming, or whether a specific effect is bypassed at that moment. Capability and preference are not active-stream telemetry. |

Android's TV playback guidance describes multiple possible output devices and encodings (including PCM and Dolby/DTS formats), and tells a **player app** to inspect the route of its own `AudioTrack`. It also documents the API-33 anticipatory route. That is evidence that route and format decisions vary with the app's attributes, connected devices, and playback configuration—not a public API for inspecting every other app's live path. [1][2][3][4]

## Effects, global routing, and capture boundaries

- **Own session:** an insert effect is associated with the audio session ID supplied when the app creates it. An ordinary app can reliably know the IDs of tracks it creates, not arbitrary tracks owned by other processes.
- **Session 0:** Android's `AudioEffect` reference now marks insert effects on session 0 (the output mix) as deprecated. A successful constructor, control ownership, parameter write, and read-back show that the framework accepted/configured an effect object; they do not prove every app, direct path, codec, DSP, or physical output traverses it. OEM/HAL behavior can differ.
- **System routing policy:** `AudioPolicy` APIs that directly control or override routing require `MODIFY_AUDIO_ROUTING` (or a narrowly scoped `MediaProjection` route for capture). That is not an ordinary runtime grant available to a generic third-party app. Do not design a stock-TV product around obtaining it.
- **Playback capture:** Android 10+ can let an app capture some other app's playback only after the user approves a `MediaProjection` prompt, and only when the player usage and capture policy allow it. Capture does not insert an EQ into the original output path; rebuilding a capture/process/playback loop would have latency, duplication, and opt-out problems. It is not a transparent whole-TV equalizer API.
- **DUMP:** the Android permission reference says `android.permission.DUMP` is “Not for use by third-party applications.” `pm grant` may be refused for an ordinary/release APK even when the permission is declared. If a particular device grants it, DUMP parsing remains a firmware-dependent best effort, not a platform contract. Core EQ therefore treats broadcasts only as rescan triggers and uses DUMP-discovered session UIDs as its optional source of app identity; missing or ambiguous package mappings stay unidentified.

Sources: [`AudioEffect`](https://developer.android.com/reference/android/media/audiofx/AudioEffect), [`AudioPolicy` AOSP API annotations](https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/audiopolicy/AudioPolicy.java), [`AudioPlaybackCapture`](https://developer.android.com/media/platform/av-capture), [`Manifest.permission.DUMP`](https://developer.android.com/reference/android/Manifest.permission#DUMP), and [`PackageManager.getPackagesForUid`](https://developer.android.com/reference/android/content/pm/PackageManager#getPackagesForUid(int)). [5][6][7][8][9]

## Passthrough: useful clues, not a yes/no answer for another app

Android TV devices can expose different encodings on HDMI, Bluetooth, and other outputs. A source app may decode to PCM, request direct playback, or choose an encoded format based on device support. If the signal remains encoded/direct instead of going through the PCM mixer, an ordinary session-0 software effect cannot be assumed to process it; if decoded and mixed, it may traverse a different path. The public direct-playback and surround-setting APIs report supported/preferred capability, not what a streaming app actually selected for its current title.

For Core EQ, `OutputRoute.mayPassThrough()` only supports a **may-bypass** warning for HDMI when Android's encoded-surround setting is not `NEVER`. It must not be interpreted as proof that the current stream is bitstreamed. A device run should compare TV surround settings (especially PCM versus Auto/Manual), source app/content, output endpoint, and an independent acoustic measurement. [1][2][10]

## What this means for Core EQ today

1. `OutputRoute.current()` uses `getAudioDevicesForAttributes(USAGE_MEDIA)` on API 33+ and falls back to ranking connected output types on older versions. The code and UI should keep calling this an **estimate**, not another app's active route.
2. `EqService` can test whether a platform effect can be created, controlled, configured, and read back. Its `coverage depends on TV routing` status language is warranted; the `PLAYING` indicator is only a playback hint.
3. Automatic app rules are conditional: they need a usable DUMP grant and a parser-visible active session UID that maps unambiguously to a package. Stock/release Android may refuse the grant. Manual remote selection remains the dependable mode control; unknown/unmapped/conflicting active players use Everyday when automatic mode is enabled.
4. The user's report that Poweramp EQ affects most/all apps on their current setup is useful device evidence, not a generic Android contract. The missing device model, Android build, output, Poweramp settings, and passthrough state prevent attributing that behavior to one route/effect mechanism.
5. Poweramp's own build 995 release notes describe an experimental **Global equalization** mode: when enabled it applies to the output mix instead of tracking individual players, is based on deprecated Android behavior, and may fail on some devices. That is consistent with a device-dependent output-mix path—not evidence of a universal app-level routing API. [11]
6. No Poweramp EQ or other third-party APK has been decompiled in this research pass. The official Poweramp forum download page and OpenEQ's GitHub APK asset both failed to transfer here (TLS/SSL EOF; no usable binary was saved); OpenEQ and TuneX source code were reviewed, but that is not binary inspection. I also tried 32steps 2.2.12, an Android TV-capable equalizer whose official GitHub release declares a 1,959,802-byte APK. The IzzyOnDroid index lists SHA-256 `e4b0c25ecd1cc73a0703fca37c11a2efb4ba0d73b4d55c1938603eda237dbd92`, but the GitHub asset and IzzyOnDroid/Codeberg mirrors all failed TLS/SSL transfer in this sandbox; the hash is not locally verified and no binary was decompiled. Keep these retrieval failures separate from the API findings here.

## Recommended generic-TV test record

For each target device, record the model, firmware/build, Android/API level, connected output names/types, selected TV surround mode, source app, content/audio format, and whether the stream is believed to be PCM or direct/passthrough. Then capture, without retaining private dump text unnecessarily:

- `getAudioDevicesForAttributes(USAGE_MEDIA)` before and during playback;
- connected `AudioDeviceInfo` types/names and the encoded-surround preference;
- public playback callback state and what Core EQ's DUMP check actually returns;
- session-0 effect construction/control/configuration results;
- an external acoustic before/after measurement on TV speakers, HDMI ARC/eARC, and any relevant Bluetooth output, with PCM and direct/passthrough settings tested separately.

A successful effect probe is one data point. Only the independent output measurement can establish that the target stream and route changed acoustically.

## References

1. [Android TV audio capabilities](https://developer.android.com/training/tv/playback/audio-capabilities) — output formats, device capabilities, per-track routed-device guidance, and API-33 anticipatory routing.
2. [`AudioManager`](https://developer.android.com/reference/android/media/AudioManager) — `getAudioDevicesForAttributes`, `getActivePlaybackConfigurations`, direct playback, and encoded-surround APIs.
3. [`AudioRouting`](https://developer.android.com/reference/android/media/AudioRouting) and [`AudioTrack`](https://developer.android.com/reference/android/media/AudioTrack) — own-track routing and currently-playing constraint.
4. [`AudioPlaybackConfiguration`](https://developer.android.com/reference/android/media/AudioPlaybackConfiguration) — public attributes; `getAudioDeviceInfo()` deprecation note (“this information was never populated”).
5. [`MediaSessionManager`](https://developer.android.com/reference/android/media/session/MediaSessionManager) — active-session access requires `MEDIA_CONTENT_CONTROL` or an enabled notification listener.
6. [`AudioEffect`](https://developer.android.com/reference/android/media/audiofx/AudioEffect) — session IDs and deprecated session-0 insert effects.
7. [AOSP `AudioPolicy`](https://android.googlesource.com/platform/frameworks/base/+/master/media/java/android/media/audiopolicy/AudioPolicy.java) — `MODIFY_AUDIO_ROUTING` requirements for system routing-policy operations.
8. [Android playback capture](https://developer.android.com/media/platform/av-capture) — `MediaProjection` consent, usage/capture-policy restrictions, and opt-out behavior.
9. [`Manifest.permission.DUMP`](https://developer.android.com/reference/android/Manifest.permission#DUMP) and [`PackageManager.getPackagesForUid`](https://developer.android.com/reference/android/content/pm/PackageManager#getPackagesForUid(int)) — DUMP's third-party restriction and UID package mapping semantics.
10. [Android audio playback improvements](https://developer.android.com/media/platform/improve-audio-playback) — bit-perfect mixer behavior and when the mixer applies no processing.
11. [Poweramp Equalizer build 995 release notes](https://forum.powerampapp.com/files/file/95-powerampequalizer-build-995-uniapk/) — Global equalization mode is described as experimental, based on deprecated behavior, output-mix rather than per-player, and device-dependent.
