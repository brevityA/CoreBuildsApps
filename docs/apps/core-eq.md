# Core EQ

**Measure the room you sit in, with the remote you already hold.** Back to the [suite page](../../README.md).

Core EQ combines TV-room measurement with a manual 10-band tone EQ. Each measured base belongs to its output; Movie / TV, Everyday and Gaming add independent tone overlays. Choose a mode with the remote or optionally assign app rules for best-effort automatic switching. Player identity can come from optional DUMP discovery or a new, user-granted Android media-session source; neither proves the route or EQ coverage. Unknown/conflicting players use Everyday. Manual choices can temporarily override the current player set or stay sticky. Profiles and the current mode can be exported for Poweramp Equalizer or TV sound settings.

### Data and override flow

- **Room base:** the sweep/capture or imported REW response is tagged to the output evidence available during that measurement. It is stored per output/profile; a route change does not silently reuse another output's curve.
- **Current route:** Android supplies an anticipated media route (API 33+) or a connected-device estimate; the sweep's own `AudioTrack` route is stronger evidence for that measurement only. Multiple/unrecognized routes remain ambiguous/unknown rather than selecting a convenient profile.
- **Player identity:** DUMP may identify session IDs and packages for per-session effects and app rules. If DUMP is unavailable, optional Notification access can provide packages and playing/buffering state for apps that publish MediaSessions. Core EQ does not read notification text, and MediaSession data does not identify the route or authorize effects on that app's session.
- **Mode arbitration:** a manual override takes precedence; otherwise automatic app rules apply. Unmapped apps, unidentified playback or conflicting rules choose Everyday. Temporary overrides are tied to a complete detected player-set snapshot; sticky overrides remain until changed. Incomplete scans cannot expire a temporary override.
- **Tone composition:** the selected output's room base remains the foundation. The chosen Movie / TV, Everyday or Gaming overlay adds its mode tone; per-profile manual EQ and optional extra effects are separate layers, not new room measurements.
- **Hardware hints:** Android's reported output product name can suggest a soundbar preset, but it does not reveal JBL One settings, its own calibration or source processing. Those are external device controls and should remain fixed across a measurement/playback comparison.

A detailed source, permission and precedence map is in [Playback identity and mode overrides](../research/core-eq-playback-identity-and-mode-overrides-2026-10-25.md).

## First install and setup

On a fresh Android TV install, Core EQ opens a short welcome guide. It shows Android's route indication with its evidence type (anticipated route or connected-output estimate), explains the remote-microphone measurement, and warns that the first GREEN capture-quality measurement for an output type may be applied automatically. Green indicates signal-to-noise only, not calibrated microphone response or acoustic accuracy. You can choose **Start room measurement**, open setup help, or skip the guide; upgrades are not interrupted.

**ADB is optional.** Room measurement, correction and manually selected modes do not need ADB. The Content modes screen offers an optional **Enable media-session access** path in Android settings; it lets Core EQ use active session app package names and playing/buffering state, but does not read notification content or establish audio route/effect coverage. Some players do not publish a usable media session. Optional DUMP discovery may reveal more players and is also required for per-session effect attachment; Android documents DUMP as unavailable to third-party apps, so some TVs refuse it. The **Setup check and ADB** screen has copyable commands, a link to Google's official [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools), and remedies for common connection errors. Core EQ never runs ADB commands itself. If neither identity source is available, use manual mode selection. The floating card is optional; the Home status and text toast remain available when overlay access is unavailable.

## Measuring

A measurement plays a 10-second sweep between two short timing beeps and records it through the remote's microphone; keep the room quiet for 14 seconds. Choose the output, sound mode and volume you will use; keep processing unchanged, quiet the room and place the mic at the main seat. Measure materially different routes or modes separately. Each sweep gets a 0–100 capture-quality score from signal-to-noise only; it does not grade microphone calibration or acoustic accuracy. At the 16 kHz analysis rate, useful response analysis reaches about 7.8 kHz, not the sweep generator's nominal 20 kHz. What it found about the room is listed beside the score, not scored. Bands the remote could not hear clearly are shown and left uncorrected, and bass dips are lifted by 2 dB at most, because one seat and an uncalibrated microphone cannot tell a room dip from a seat dip.

## Backend fit and repeatability

The service status can report the requested correction versus the equalizer's configured/read-back gains at the backend's own band centres. It also reports shared headroom and any gain-range-limited bands. This is a configuration-level fit diagnostic only: it does not measure the speakers, establish the response between EQ bands, or prove that a particular player's audio passed through the effect.

On the TV, **Repeat at same spot** makes a second sweep against the first. On the Android phone companion, repeat checking is opt-in and records two passes; keep the phone still and leave the TV output and volume unchanged. Both traces are retained separately and the first sweep remains the correction; they are never averaged. If the optional phone repeat fails, is cancelled or times out after the first sweep has reached the TV, Core EQ saves that first sweep alone when the TV is still available. A descriptive comparison removes a robust level offset using clean shared mid-band measurements. It needs at least three clean alignment bands and eight clean common bands before it reports summary metrics; these are data-sufficiency gates, not quality cutoffs. If Android reports a changed capture/output path, the summary is withheld; if route metadata is missing, the result is marked provisional. This checks one position only and has no qualified pass/fail threshold.

The Android phone companion can also select a profile/mode and toggle correction over a TV-initiated, encrypted local-network pairing. Phone measurement is foreground-only. Android microphone response and processing vary by handset; route metadata is stored, not treated as proof of an acoustic path or applied as calibration. TV-to-phone capture timing and audio-path behaviour still require qualification on each physical setup.

## Extras and the on-screen card

Optional Extra effects, all off by default: **stereo speakers** (a speech lift and a bass guard for 5.1 on two speakers), dialogue boost, low-volume bass that follows the TV volume, bass boost, loudness enhancer and night mode. **Stereo speakers can also change a system setting.** On Android 12 and later, once you allow Core EQ to "Modify system settings", switching it on sets the TV's *Surround sound* to Never so Android decodes Dolby and DTS and folds 5.1 to stereo where Core EQ can hear it; switching it off puts the old mode back, but only while the setting is still Never. That matters on the outputs that can carry an undecoded bitstream — HDMI, HDMI ARC and optical — because a bitstream is folded by the soundbar, where Core EQ never hears it. Bluetooth, USB and analogue outputs only take PCM, so the setting is left alone there. When a different profile or mode takes effect, a card shows for 4 seconds over whatever is playing: profile, mode, output, engine and extras.

## Compatibility

**Android 11 (API 30) and later; built against Android 17 (API 37).** Android does not guarantee a global equaliser, reveal every player's identity, or prove another app's actual output route, so Core EQ reports its apply path instead of promising every app will be affected. HDMI and optical bitstream (passthrough) skips all on-device EQ; the Extra effects screen and Home say when the current setting allows one. Optional DUMP discovery may require an ADB grant that stock/release builds can refuse; Android Notification access is a separate, user-granted option for media-session-based app rules. Permissions: RECORD_AUDIO, MODIFY_AUDIO_SETTINGS; optional **"Modify system settings" (WRITE_SETTINGS)**, requested only when you switch Stereo speakers on, used only to set and restore the TV's Surround sound mode, and refused by the app with an explanation and the manual menu path when not granted; optional Notification access for media-session identity; optional "Display over other apps" (SYSTEM_ALERT_WINDOW) for the on-screen card, which falls back to a text toast without it and is refused on Fire TV.

## Install

Downloader code **`7946159`**, or the permanent APK URL:

**https://github.com/brevityA/CoreBuildsApps/releases/download/coreeq/coreeq-release.apk**

Release builds check the suite's release feed when Home opens and offer the update in-app after verifying its package, version, SHA-256 and signing certificate. Versioned builds are under `coreeq-v*` tags. Test builds are on the [`coreeq-test`](https://github.com/brevityA/CoreBuildsApps/releases/tag/coreeq-test) prerelease; the test package carries no feed and is installed by hand.

## For developers

Build: `cd coreeq && ./gradlew :app:assembleDebug` · DSP reference: `tools/core_eq_dsp.py` · plan: [`docs/CORE_EQ_PLAN.md`](../CORE_EQ_PLAN.md)

Changes: [`coreeq/CHANGELOG.md`](../../coreeq/CHANGELOG.md)
