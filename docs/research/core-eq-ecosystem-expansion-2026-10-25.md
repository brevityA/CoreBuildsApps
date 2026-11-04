# Core EQ ecosystem expansion research

**Research date:** 25 October 2026
**Scope:** Android playback detection and content-mode overrides, comparable equalizer apps/APIs, and JBL ecosystem integration. The selected Core EQ scope remains playback detection and mode overrides; third-party control and file import/export were not implemented. The P0 event-driven observer recommendation below has now been implemented in the working tree; runtime/device validation remains outstanding.

## Executive summary

The most useful near-term direction is to make the existing optional MediaSession signal event-driven and clearer about its evidence, rather than adding broader permission-heavy monitoring. Android's public `MediaSessionManager` API can notify a listener when active sessions change, and each `MediaController` can report state changes. This complements Core EQ's existing NotificationListener-triggered refresh and avoids relying exclusively on notification token events. It still only identifies a media-session owner and its declared state; it does not prove audible output, effect attachment, or the soundbar/TV route.

Wavelet is the closest architecture comparator: its own documentation calls DUMP-based enhanced session detection experimental, requires both DUMP and Notification access, and says compatibility varies by player and device. Poweramp exposes an official app-specific API, but it is not a system-wide EQ API; its separate Poweramp Equalizer API does have documented preset-import/management operations, so it should not be described as having no API. Neither product establishes universal Android effect coverage.

A current Home Assistant project advertises local JBL One soundbar status and controls, including EQ, across several JBL Bar models. It is community-developed, based on inspecting the JBL One app, and its README says the local endpoint requires a client certificate sourced from another third-party project. This is evidence of a possible enthusiast integration—not an official JBL SDK, a stable public contract, or a suitable dependency for Core EQ. There is no basis here to expand Core EQ into soundbar control.

## What the ecosystem evidence supports

### Android playback/app signals

| Signal | Useful for | Important boundary | Core EQ implication |
|---|---|---|---|
| `MediaSessionManager.getActiveSessions()` with the enabled NotificationListener component | Listing app-owned media controllers; `MediaController` exposes owner package and current `PlaybackState`. | Requires the user-enabled listener component. Session publishers can be absent, stale, or inaccurate; state is a report by the app, not a meter of audible sound. | Keep it as an optional, best-effort app/mode identity source. Read only package and state; do not infer route or audio session ID. |
| `addOnActiveSessionsChangedListener(...)` plus `MediaController.Callback` | Reacting to session-list and playback-state changes rather than waiting for notifications or polling. | Same access and session-publication limits. Controllers can be replaced; callbacks must be registered/unregistered and reconciled as the active set changes. | Strong candidate to improve freshness and lifecycle correctness in `MediaSessionDiscovery`; maintain a snapshot reconciliation path on service connect/resume. |
| Android 13+ `getMediaKeyEventSessionPackageName()` | A compact “media key target” / likely current transport-control target. | It identifies the session Android would target with media keys (or a last media-button receiver), not every concurrently playing app. Requires `MEDIA_CONTENT_CONTROL` or an enabled notification listener. | Optional fallback/hint only; never let it override a richer active-session set or imply that it is the sole audible player. |
| `AudioManager.AudioPlaybackCallback` / `getActivePlaybackConfigurations()` | Coarse system playback activity and audio attributes. | Non-system access is deliberately anonymized for identity; `getClientUid()` is hidden/System API. Public `getAudioDeviceInfo()` is deprecated at API 36 because it was never populated. | Retain as an “audio is active / unidentified player” safety signal, not app identification or exact sink routing. |
| `MediaController.PlaybackInfo` | Declared local-vs-remote playback type and session volume-handling information. | It does not expose a resolved Android `AudioDeviceInfo` route for that session. “Local” is not proof of the output endpoint. | Could inform a diagnostic label only; do not use it to select a measured output profile. |
| `UsageStatsManager` | Foreground/usage-history context with user special access. | Foreground is not equivalent to playing audio and can misidentify background playback or foreground video with no sound. | Do not use for automatic audio-mode switching. |
| Audio playback capture | Capturing eligible other-app audio after MediaProjection consent and subject to source policy/usages. | Not a passive playback detector; requires conspicuous screen-capture consent and does not capture all sources. | Not a fallback for playback identity or soundbar control. |
| `AudioManager.getAudioDevicesForAttributes()` | Expected routing for a supplied `AudioAttributes`; current connected devices. | Expected route is not proof of a particular third-party player’s actual route. `MediaPlayer`/`AudioTrack` routed-device APIs are player-instance APIs, unavailable for another app's track. | Preserve the distinction already documented in Core EQ between anticipated/connected route and a route observed for Core EQ's own sweep. |

Android's `AudioEffect` documentation says insert effects should attach to a specific player's session; attaching an insert effect to output-mix session 0 is deprecated. This is the platform-level reason neither media-session identity nor mode selection can guarantee DSP coverage. Some firmware may accept output-mix effects, and other media paths such as passthrough can bypass Android processing. These are device/path facts to report as conditional, not promises.

### Comparable apps and APIs

**Wavelet.** Its documentation says legacy mode is off by default and may work depending on the manufacturer's audio framework. It documents player-by-player compatibility and describes enhanced session detection as experimental (Android 10+), using DUMP to inspect audio-service information and Notification access to attach/release effects to sessions. This validates the general architecture trade-off: better detection may require exceptional user setup, but the app still cannot guarantee every player/path/device. For Core EQ, separate the permission choices and their jobs: Notification access supports app identity for modes; DUMP supports the existing session-discovery/effect workflow. The existing UI copy should not imply that either one guarantees correction.

**Poweramp.** The public `powerampapi` repository documents Intent commands and status broadcasts for the Poweramp player itself, including playback control/status. It separately publishes `PeqAPI`, which defines a command receiver, service start/stop/toggle, `SET_EQU_PRESET`, and an `ACTION_IMPORT_PRESETS` activity action with preset names/data arrays. This is a real app-specific interoperability opportunity if a future task explicitly targets Poweramp EQ. It does not provide a generic Android equalizer-control contract or a route into other apps' processing. The project also supports import/export through its own UI; forum documentation notes AutoEQ text and `.pa-eq-preset` import, and settings backups are app backups rather than a stable cross-app interchange schema.

**File formats and portability.** Poweramp's preset JSON/AutoEQ text import is more tractable as a separately scoped import feature than a proprietary backup archive. A backup archive is intended for Poweramp restore and can include broad app data; reverse-engineering it is not justified for this playback/mode task. Core EQ should accept only clearly documented, versioned formats and disclose conversion/approximation (especially when parametric filters are converted to a graphic EQ). No implementation of `.peq-settings` import is recommended by this research.

### JBL ecosystem

The `SH1FT-W/jbl-bar` Home Assistant repository (active as of the research date) advertises local control/status for JBL One Bar models including Bar 800, with EQ, volume, power and other entities. The repository says commands were read from JBL One and tested on a Bar 1000, and documents device/firmware limitations. It further states the local API expects a JBL One client certificate and identifies the certificate as third-party material from `MrBearPresident/JBL_Soundbar`, with separate MIT notice/license. These disclosures indicate reverse engineering and credential/material provenance that require careful security and license review before reuse.

This is not evidence of an official public JBL developer API or a supported contract. There is no basis to embed that certificate, copy its protocol, or send user-device commands from Core EQ. If JBL device control is ever requested, treat it as a separate research/design decision: confirm model/firmware coverage, inspect current upstream code and licenses, assess certificate/private-key implications, obtain explicit user consent, and prefer an official JBL integration/API if one becomes available. For current Core EQ behavior, continue to keep JBL One settings outside the app and ask the user to hold those settings constant during a measurement.

## Prioritized roadmap (research recommendation)

### P0 — Event-driven session observation (implemented; validation pending)

The apply service now registers `MediaSessionManager.addOnActiveSessionsChangedListener` while Notification access is available, immediately reconciles the current session list, and maintains `MediaController.Callback`s for active controllers. Callbacks run on the main handler and only trigger the existing debounced query. Controllers removed from the active list and service teardown unregister callbacks. Queries still read only package/state and preserve the existing incomplete-snapshot/temporary-override rules. The original notification-token refresh remains as an additional trigger. A controller-tracker unit test now covers idempotent registration, state-change notification, removal, token reuse/replacement, cleanup, and retry after failed registration.

**Validation still needed:** manager-listener registration/unregistration; initial reconciliation; state transitions with Android framework controllers; multiple simultaneous apps; access revoke/disconnect; callback/query exceptions; and unknown audio fallback. The existing and new unit tests could not be run here: the Gradle test/build attempt is blocked because Java/JAVA_HOME is absent. Validate the callback lifecycle on an Android TV as a separate hardware check; no TV/device claim is established.

### P1 — Evidence and user controls (small, aligned)

- Keep manual mode selection first-class and state its precedence plainly.
- Distinguish “app/session identity found,” “audio activity observed,” “route estimate,” “DUMP session found,” and “effect attach reported” in diagnostics; do not collapse them into a single “working” badge.
- Give the user an opt-out for automatic mode switching that leaves manual selection intact. Explain Notification access separately from DUMP, and offer a concise local-only data explanation.
- Add an in-app playback-detection test view that lists package labels only if the user explicitly opens it, with a clear/forget action if any history is ever stored. Prefer no history by default.

### P2 — Optional app-specific bridges (only if users request)

- Poweramp Equalizer: prototype an explicit “Connect to Poweramp Equalizer” bridge using its documented API/import flow. Keep it isolated behind package/API detection and manual confirmation; never claim it affects arbitrary players.
- Portable EQ files: define Core EQ's own versioned text/JSON exchange format, validate bounds/NaNs/filter counts, and make preview/confirm the import flow. Evaluate AutoEQ text and Poweramp's public preset JSON as additional sources only when each schema is documented and tests can cover version drift.
- Do not parse `.poweramp-backup`/`.peq-settings` containers without format-owner permission/documentation and a separate security review.

### P3 — JBL direct integration (out of current scope; do not build now)

No Core EQ roadmap slot is recommended until a supported public API or user-authorized, carefully reviewed community bridge exists. Keep soundbar EQ/device configuration separate from Core EQ's room correction and content modes. In particular, do not tune a profile while soundbar processing changes silently underneath it.

## Privacy, capability, and policy guardrails

- Notification access is broad system access, even though the current implementation reads only the media-session token to refresh and then package/state. Keep the disclosure, code path, and actual access aligned; do not inspect unrelated notification content.
- DUMP is an exceptional shell-granted permission, not an ordinary runtime permission or an API contract. Require deliberate opt-in, explain ADB and revocation, and degrade to manual/best-effort behavior when unavailable.
- Do not add `QUERY_ALL_PACKAGES` or `PACKAGE_USAGE_STATS` just to guess what is playing. Installed-app inventory/usage data are privacy-sensitive and are not required for this feature when session APIs can identify publishers.
- Keep every detection signal separate from proof of audibility, route, effect attachment, or user-perceived improvement. No statement should claim “works with all apps/devices.”
- The research does not qualify the user's JBL Bar 800 or any Android TV. No soundbar or physical TV test is evidenced by CI/emulators.

## Evidence and references

Official platform/API sources:

- [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager) — active-session access, session listener, notification-listener authorization, and media-key target APIs.
- [MediaController](https://developer.android.com/reference/android/media/session/MediaController) and [MediaController.Callback](https://developer.android.com/reference/android/media/session/MediaController.Callback) — package/state access and callbacks.
- [MediaController.PlaybackInfo](https://developer.android.com/reference/android/media/session/MediaController.PlaybackInfo) — declared local/remote playback type and volume handling, not actual audio route.
- [AudioManager](https://developer.android.com/reference/android/media/AudioManager) and [AudioPlaybackConfiguration](https://developer.android.com/reference/android/media/AudioPlaybackConfiguration) — playback-activity callback, active configurations, public attributes and route-info limitations.
- [AudioEffect](https://developer.android.com/reference/android/media/audiofx/AudioEffect) and [Equalizer](https://developer.android.com/reference/android/media/audiofx/Equalizer) — session-based effect association; output-mix session 0 is deprecated for insert effects.
- [Audio playback capture](https://developer.android.com/media/platform/av-capture) — capture eligibility/policies and user-consent context.
- [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager) — special access to usage history, which is not a playback signal.

Ecosystem/product sources:

- [Wavelet Settings](https://pittvandewitt.github.io/Wavelet/Settings/) — player compatibility list, legacy-mode variability, and experimental DUMP + Notification access setup.
- [Wavelet Features](https://pittvandewitt.github.io/Wavelet/Features/) — product feature set and EQ/AutoEq capabilities.
- [Poweramp API README](https://github.com/maxmpz/powerampapi/blob/master/poweramp_api_lib/readme.md) — player-specific Intent command/status API.
- [Poweramp Equalizer PeqAPI source](https://github.com/maxmpz/powerampapi/blob/master/poweramp_api_lib/src/main/java/com/maxmpz/poweramp/equalizer/PeqAPI.java) — separate EQ API surface, preset import action, and preset command.
- [Poweramp API issue #39](https://github.com/maxmpz/powerampapi/issues/39) — an older user report questioning EQ API scope; the current source demonstrates that the API exists, so the issue is not conclusive documentation.
- [Poweramp AutoEQ import discussion](https://forum.powerampapp.com/topic/20397-parametric-equalizer-and-autoeq-integration/) and [backup-format discussion](https://forum.powerampapp.com/topic/28706-export-file-format/) — app-supported preset import vs app backup format.
- [Community JBL Bar integration](https://github.com/SH1FT-W/jbl-bar) — project claims, model/firmware caveats, local client-certificate requirement, and third-party certificate notice; community reverse engineering, not official JBL documentation.
- [JBL Bar 800/1000 software update notes](https://support.jbl.com/us/en/howto/bar-800-bar-1000-software-update-release-notes-us/000036861.html) — official firmware/security update history; does not publish a developer API.
- [Google Play sensitive data/permissions guidance](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en) — purpose limitation and incremental permission principles.
