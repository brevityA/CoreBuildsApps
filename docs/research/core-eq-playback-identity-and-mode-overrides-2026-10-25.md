# Core EQ playback identity, data sources, and overrides

**Scope:** Android playback/app identity and content-mode arbitration in Core EQ. This documents the existing signals and the optional MediaSession source added 25 October 2026. It does not claim that a reported app is audible, routed to a particular output, or processed by Core EQ.

## What Core EQ reads—and what it does not

| Input | Source | How Core EQ uses it | Limitation |
|---|---|---|---|
| Candidate media output | `AudioManager.getAudioDevicesForAttributes()` on API 33+, otherwise connected output devices | Chooses the output-specific measured profile, labels evidence as anticipated/connected estimate, and pauses on ambiguity. | An anticipated route or connected device list is not another app's active track route. |
| Sweep's output route | The measurement's own `AudioTrack.getRoutedDevice(s)` | Tags the new measurement to the strongest route evidence available for that sweep. | It describes Core EQ's sweep track, not necessarily another player's route or the acoustic chain heard by the microphone. |
| Playing/not-playing hint | `AudioManager.AudioPlaybackCallback` and active playback configurations | Shows the playback hint and, when app identity is unavailable, prevents a false claim about which app is active. | This is coarse; it does not reliably name the player, sink, audibility or effect coverage. |
| Player session candidates and UID | Optional `dumpsys media.audio_flinger` and `dumpsys audio`, only when the TV grants the protected DUMP permission | Can discover candidate session IDs for per-session effects, map a unique UID to a package for app-mode rules, and identify unknown/conflicting players. | DUMP is not intended for third-party apps; stock TVs may refuse it, and output formats/visibility vary. Untrusted session broadcasts only trigger a rescan. |
| Active media-session package/state | **New optional** Android `MediaSessionManager.getActiveSessions()` path, after the user grants Notification access in Android Settings | Supplies app package name and `PLAYING`/`BUFFERING` state for app-mode rules if the app publishes a session. | Does not supply a trusted effect-session ID or actual route. A player without a media session stays unidentified. |
| TV/soundbar brand/model string | Android `AudioDeviceInfo.productName` when provided | Shows recognized hardware hints and orders manual tone presets. | Not read from JBL One, not a full model identifier in every route, and not evidence of the active audio source or processing mode. |
| Volume | Android media stream volume/index and available volume dB curve | Optional low-volume bass layer follows the TV's media-volume setting. | OEM volume policy may differ; it is not a sound-pressure-level meter. |
| Spatializer state | Android `Spatializer` on supported Android versions | Read-only setup detail. | Not a Dolby/Atmos bitstream detector, not a signal-path trace and not an EQ control. |
| Measurement samples | Core EQ's sweep played and captured through the selected TV/remote microphone, or explicitly imported REW data | Creates/updates a room correction profile with route and capture provenance. | A clean capture is not a calibrated measurement microphone or proof of physical hardware qualification. |
| User profile/settings | Core EQ app-private persistence | Stores measured bases, mode overlays, rules, manual/automatic selection, override policy and effect preferences. | These are local settings, not JBL device settings or a cloud account. |
| Imported/exported files | Android file picker / share or MediaStore; supported text/JSON formats | Imports supported curves and backs up/exports profile data for other tools. | A file import is not proof that another app/device has applied the data. The Poweramp `.peq-settings` backup format remains unsupported pending a versioned, understood schema. |

Core EQ does **not** connect to JBL One, fetch a JBL soundbar's internal calibration, read its Smart/Standard setting, or change its bass/surround/Atmos controls. No public, documented JBL API has been established for those operations. Those remain separate device-side settings that the user should keep fixed during measurement and playback.

## New optional media-session identity source

On Android TVs that expose **Notification access**, the user can enable **Core EQ playback detection (media session state only)** from the Content modes screen. This is a system-managed special access, not an ordinary runtime permission.

When enabled:

1. Android's `NotificationListenerService` binds Core EQ. The implementation only asks for a refresh on connection/disconnection and on notifications whose extras contain Android's media-session token key; it checks that key without reading notification title, body, app label, artwork or content intent. Each refresh is debounced.
2. While the apply service is running, Core EQ also registers `MediaSessionManager.addOnActiveSessionsChangedListener()` using the declared notification-listener component and immediately reconciles the current session list. It tracks each active controller by session token, registers a `MediaController.Callback` for playback-state changes/session destruction, and unregisters callbacks when controllers disappear or the service stops. These callbacks only trigger the existing debounced snapshot query; they do not read metadata or issue transport controls.
3. Core EQ calls `MediaSessionManager.getActiveSessions()` using its declared notification-listener component.
4. It reads only each active controller's package name and `PlaybackState.state`. Only `PLAYING` and `BUFFERING` are app-rule candidates; paused/stopped sessions do not select an app mode. The app's own package and blank package names are discarded.
5. The resulting package set may choose a mode. It is never converted into a session ID, never used to attach an `AudioEffect`, and never treated as route evidence.
6. Android playback activity with no identified session is treated as an unidentified player, which selects the safe Everyday mode while automatic switching is enabled.

Notification access is powerful: Android grants the listener access to posted notifications in general. Core EQ's implementation deliberately does not inspect notification title, text, artwork, artist, album or content intent. The user can revoke access in the same Android settings screen at any time. A TV may omit that settings page, refuse the grant, or have players that do not publish a media session; manual mode selection still works.

DUMP remains the only current route to **per-session effect attachment**. MediaSession identity improves automatic content-mode rules only. The output-mix effect remains a separate first choice where firmware accepts it; session identity does not prove that the effect owns or covers that player's audio.

## Mode and manual-override precedence

The mode arbitration is intentionally conservative:

1. **Manual override**, if active, wins over app rules. With automatic switching on, a manual mode becomes a temporary or sticky override according to the user's selected policy.
2. A **temporary** override is anchored to the currently detected player set; it clears when a complete trusted player snapshot changes. Incomplete/failed scans cannot expire it. A **sticky** override stays until the user selects again or changes the relevant policy.
3. With no active override and automatic switching on, each active package's app rule contributes a mode. An active app without a rule demands Everyday.
4. If rules conflict, or a player is active but cannot be identified, Core EQ uses Everyday rather than guessing.
5. With automatic switching off, the selected manual mode is used. The selected output-specific measured base remains underneath every mode.
6. Manual tone is a per-profile/per-mode overlay. It does not replace or rewrite the measured room base. Extra effects (dialogue, low-volume bass, etc.) remain a separate user-controlled layer.

When several discovery sources are enabled, Core EQ unions identified package sets; it does not assume that two sources naming different packages are a single app. Unknown evidence remains unknown even if another source names a player, so uncertain multi-player situations resolve safely. A complete snapshot is required before a temporary override can expire. With DUMP enabled, the DUMP snapshot is required; if Notification access is also enabled, that query must also complete. Without DUMP, Notification access plus a successful playback-state probe is the available complete snapshot.

## User-facing controls added

- **Content modes → Enable media-session access…** opens Android Notification access settings; after returning, the UI shows whether access is on and refreshes a running service.
- The on-screen explanation states what data is read, what it cannot establish and that players without a visible session fall back safely.
- **Setup check** reports this as optional information, separate from DUMP (needed for per-session correction) and overlay access.
- No access is requested automatically on install. The user must open the screen and grant it in Android's settings.

## Verification and remaining limits

Pure unit tests cover session-state filtering (playing/buffering only, own/blank package rejection, duplicate-package collapse) and the setup guide's optional-access wording. `git diff --check` is the available static validation. A local Gradle run is not available in this workspace because Java is missing. The feature is not physically validated on the user's Android TV. In particular, verify that the TV offers Notification access, that its players publish sessions, and that session changes arrive as expected before relying on automatic mode rules. If not, use manual selection or the existing optional DUMP path.

## Android references

- [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager) — active media-controller sessions and the listener component parameter.
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) — system-bound listener service and user-granted access.
- [PlaybackState](https://developer.android.com/reference/android/media/session/PlaybackState) — playing, buffering, paused and stopped states.
- [AudioManager](https://developer.android.com/reference/android/media/AudioManager) and [AudioTrack](https://developer.android.com/reference/android/media/AudioTrack) — coarse playback/configuration and track-specific route evidence.
- [AudioEffect](https://developer.android.com/reference/android/media/audiofx/AudioEffect) — audio-effect/session APIs and their limits.
