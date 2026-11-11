# Core EQ

**Measure the room you sit in, with the remote you already hold.** Back to the [suite page](../../README.md).

Core EQ combines TV-room measurement with a manual 10-band tone EQ. Each measured base belongs to its output; Movie / TV, Everyday and Gaming add independent tone overlays. Choose a mode with the remote or optionally assign app rules for best-effort automatic switching. Player identity can come from optional DUMP discovery or a new, user-granted Android media-session source; neither proves the route or EQ coverage. Unknown/conflicting players use Everyday. Manual choices can temporarily override the current player set or stay sticky. Profiles and the current mode can be exported for Poweramp Equalizer or TV sound settings.

### Data and override flow

- **Room base:** the sweep/capture or imported REW response is tagged to the output evidence available during that measurement. It is stored per output/profile; a route change does not silently reuse another output's curve.
- **Current route:** Android supplies an anticipated media route (API 33+) or a connected-device estimate; the sweep's own `AudioTrack` route is stronger evidence for that measurement only. Multiple/unrecognized routes remain ambiguous/unknown rather than selecting a convenient profile.
- **Player identity:** DUMP may identify session IDs and packages for per-session effects and app rules. If DUMP is unavailable, optional Notification access can provide packages and playing/buffering state for apps that publish MediaSessions. Core EQ does not read notification text, and MediaSession data does not identify the route or authorize effects on that app's session. DUMP discovery runs at most once every 15 seconds (since 1.13.2), so a player started within 15 seconds of the last scan can take that long to be corrected.
- **Mode arbitration:** a manual override takes precedence; otherwise automatic app rules apply. Unmapped apps, unidentified playback or conflicting rules choose Everyday. Temporary overrides are tied to a complete detected player-set snapshot; sticky overrides remain until changed. Incomplete scans cannot expire a temporary override.
- **Tone composition:** the selected output's room base remains the foundation. The chosen Movie / TV, Everyday or Gaming overlay adds its mode tone; per-profile manual EQ and optional extra effects are separate layers, not new room measurements.
- **Hardware hints:** Android's reported output product name can suggest a soundbar preset, but it does not reveal JBL One settings, its own calibration or source processing. Those are external device controls and should remain fixed across a measurement/playback comparison.

A detailed source, permission and precedence map is in [Playback identity and mode overrides](../research/core-eq-playback-identity-and-mode-overrides-2026-10-25.md).

## Getting around

Since 1.12.0 every screen except First run, Measure and Display calibration has a rail down the left: Core EQ (Home), then Manual EQ, Extra effects and Content type under **Sound**, and Profiles, Capability, Setup and ADB, Phone companion and Display calibration under **Set up**. The screen you are on is marked. Up and Down walk the rail and stop at its ends; Right goes into the screen, to the control that last had focus; Left from a screen's edge returns to its rail item. Going from one screen to another closes the one you leave, so Back always returns Home. Every control shows a 3dp cyan ring when it has focus. An information panel whose text runs longer than the panel (Extra effects, Mode's rules) takes focus on Right and scrolls with Up and Down.

## Home

Home answers two questions in two lines. The first says what is **applied**, apart from what is switched on: applied by DynamicsProcessing or Equalizer (and whether media is playing), pending, paused while a room is measured, nothing measured for the output in use, output not identified by Android, the correction service not running, or not applied with the reason the TV gave. Where Android cannot tell, it says unknown; it never claims a playing stream passes through the effect, because Android does not report that. The second line lists the listening effects switched on, and says when Night mode cannot run on the engine the TV accepted. The technical status line below is hidden while correction is off or there is nothing to apply, so Off is not said twice. Every state, the evidence behind it and which one wins when several apply are in [Home status](../CORE_EQ_HOME_STATUS.md).

## Profiles

Choosing a profile in the list shows its curve and, under the list, whether it plays on the output in use (through 1.13.2 this was a toast). **Details** lists what the profile records: kind, date, output, target, microphone, capture, room volume, RT60, signal-to-noise, how high it corrects, the repeat's agreement (agreement only, not a quality grade), manual EQ and notes; a field an older profile never recorded says "not recorded". **Rename** takes up to 48 characters. **Duplicate** makes an independent copy, with its mode overlays, named "(copy)"; the copy is not made active. **Delete** asks first, with Cancel focused, so a second press of OK cannot remove a profile. Back on Measure with an unsaved result asks Save, Discard or Keep it, with Keep it focused.

## First install and setup

On a fresh Android TV install, Core EQ opens a short welcome guide. It shows Android's route indication with its evidence type (anticipated route or connected-output estimate), explains the remote-microphone measurement, and warns that the first GREEN capture-quality measurement for an output type may be applied automatically. Green indicates signal-to-noise only, not calibrated microphone response or acoustic accuracy. You can choose **Start room measurement**, open setup help, or skip the guide; upgrades are not interrupted.

**ADB is optional.** Room measurement, correction and manually selected modes do not need ADB. The Content modes screen offers an optional **Enable media-session access** path in Android settings; it lets Core EQ use active session app package names and playing/buffering state, but does not read notification content or establish audio route/effect coverage. Some players do not publish a usable media session. Optional DUMP discovery may reveal more players and is also required for per-session effect attachment; Android documents DUMP as unavailable to third-party apps, so some TVs refuse it. The **Setup check and ADB** screen has copyable commands, a link to Google's official [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools), and remedies for common connection errors. Core EQ never runs ADB commands itself. If neither identity source is available, use manual mode selection. The floating card is optional; the Home status and text toast remain available when overlay access is unavailable.

## Measuring

A measurement plays a 10-second sweep between two short timing beeps and records it through the remote's microphone; keep the room quiet for 14 seconds. Choose the output, sound mode and volume you will use; keep processing unchanged, quiet the room and place the mic at the main seat. Measure materially different routes or modes separately. Each sweep gets a 0–100 capture-quality score from signal-to-noise only; it does not grade microphone calibration or acoustic accuracy. At the 16 kHz analysis rate, useful response analysis reaches about 7.8 kHz, not the sweep generator's nominal 20 kHz. What it found about the room is listed beside the score, not scored. Bands the remote could not hear clearly are shown and left uncorrected, and bass dips are lifted by 2 dB at most, because one seat and an uncalibrated microphone cannot tell a room dip from a seat dip.

## Manual EQ

Ten bands from 40 Hz to 8 kHz, ±6 dB in 0.5 dB steps, layered over the room correction for the mode you are editing. The line under the bands names the selected band, what the room correction already does there, and the two together. **Room hints** reads the measured correction: it offers to undo a band of yours that piles onto the correction, to ease a correction working near its limit above the room's transition, and names the bass bands to leave to the correction because they are room modes that move from seat to seat. While the correction is running, one preset may be suggested for what is playing and how loud it is; it is never offered over bands you set or saved yourself, and the same row undoes it. Presets, your saved presets, imports (Poweramp JSON, AutoEq or Equalizer APO text) and the same curve as TV sound-menu values are here too.

## Backend fit and repeatability

The service status can report the requested correction versus the equalizer's configured/read-back gains at the backend's own band centres. It also reports shared headroom and any gain-range-limited bands. This is a configuration-level fit diagnostic only: it does not measure the speakers, establish the response between EQ bands, or prove that a particular player's audio passed through the effect.

On the TV, **Repeat at same spot** makes a second sweep against the first. On the Android phone companion, repeat checking is opt-in and records two passes; keep the phone still and leave the TV output and volume unchanged. Both traces are retained separately. On the TV, when Android identifies the same output and microphone for both sweeps, the repeat is averaged into the correction and the saved profile says so; otherwise the first sweep remains the correction. Phone-companion repeats are never averaged. If the optional phone repeat fails, is cancelled or times out after the first sweep has reached the TV, Core EQ saves that first sweep alone when the TV is still available. A descriptive comparison removes a robust level offset using clean shared mid-band measurements. It needs at least three clean alignment bands and eight clean common bands before it reports summary metrics; these are data-sufficiency gates, not quality cutoffs. If Android reports a changed capture/output path, the summary is withheld; if route metadata is missing, the result is marked provisional. This checks one position only and has no qualified pass/fail threshold.

The Android phone companion can also select a profile/mode and toggle correction over a TV-initiated, encrypted local-network pairing. Since 1.13.0 it has three tabs: **Control** (correction, listening mode and room profile), **Measure** (the phone-mic measurement) and **TV** (pairing: scan the TV's QR code, or enter its address and code). A saved pairing opens Control, and status refreshes every 10 seconds while the screen is open, without changing anything on the TV. It uses the TV screens' look, with a ripple for touch. Pairing needs 1.13.1 or later on the phone: 1.13.0 could not save the pairing on any phone, while the TV still recorded it, so pair again after updating. Phone measurement is foreground-only. Android microphone response and processing vary by handset; route metadata is stored, not treated as proof of an acoustic path or applied as calibration. TV-to-phone capture timing and audio-path behaviour still require qualification on each physical setup.

## Extras and the on-screen card

Optional Extra effects, all off by default: **stereo speakers** (a speech lift and a bass guard for 5.1 on two speakers), **Clarity** (a presence lift from 2.2 to 4.5 kHz: Off, +1, +2 or +3 dB; the Dialogue boost switch before it was +2 dB and carries over as +2), low-volume bass that follows the TV volume, auto loudness (see the known issue below), bass boost, loudness enhancer and **Night mode** (Off; Light, −6 dB at 3:1, which brings down only the loudest moments; Strong, −12 dB at 4:1, the night switch from 1.13.2 and earlier, carried over unchanged). OK steps a level row through its levels; Left and Right step it down and up. Night mode retunes Core EQ's one limiter and adds no gain; on the Equalizer fallback there is no limiter, and Home says Night is not active. **Reset** at the bottom turns every effect on that screen off and leaves room correction, profiles and manual EQ alone. Clarity, stereo speakers, low-volume bass and auto loudness are paid for out of Core EQ's headroom, so switching one on makes the rest of the sound a little quieter instead of raising any band above 0 dB; bass boost and loudness enhancer are Android's own effects and sit outside it. **Known issue: Auto loudness has no effect in this version** (and has had none since 1.10.2). Its band lookup gives every band the same gain, and the headroom removes that, so the sound is identical with it on or off. Extra effects and Home say so. A fix is planned, and it will ask before it changes the sound for anyone who has the switch on. The numbers are in the [headroom and limiter audit](../CORE_EQ_HEADROOM_AUDIT.md). **Stereo speakers can also change a system setting.** On Android 12 and later, once you allow Core EQ to "Modify system settings", switching it on sets the TV's *Surround sound* to Never so Android decodes Dolby and DTS and folds 5.1 to stereo where Core EQ can hear it; switching it off puts the old mode back, but only while the setting is still Never. That matters on the outputs that can carry an undecoded bitstream — HDMI, HDMI ARC and optical, judged by the output actually in use rather than any port merely plugged in — because a bitstream is folded by the soundbar, where Core EQ never hears it. Bluetooth, USB and analogue outputs only take PCM, so the setting is left alone there. When a different profile or mode takes effect, a card shows for 4 seconds over whatever is playing: profile, mode, output, engine and extras.

## TV and phone

| | On the TV | On the phone companion |
|---|---|---|
| Measure | Remote microphone; a same-position repeat can be averaged when output and microphone match | Phone microphone; a repeat is compared, never averaged |
| Correction on/off, profile, listening mode | Yes | Yes, over the paired local connection |
| Clarity, Night mode, other extra effects | Yes (Extra effects) | No |
| Rename, duplicate, delete, details | Yes (Profiles) | No |
| Manual EQ | Yes | No |
| Diagnostics summary | Yes (Capability) | No |

## Reporting a problem

**Capability → Diagnostics summary** builds a short block of text when pressed and shows all of it before anything else can happen: app version and build, Android version, the TV's maker and model, the output in use and how Android reported it, the kind of profile that applies (not its name), Home's two lines, the last service message, how the last measurement ended, and the background-work counters. **Copy** puts exactly that text on the TV's clipboard; **Show as QR code** puts it in a code for a phone camera. Core EQ sends nothing anywhere. Never included: recordings, curves and the phone pairing keys. In the free-text lines (Home's applied line, the last service message and the last measurement message), the names of saved profiles and of outputs Android has reported, network and hardware addresses and long ids are replaced with `[name]`, `[address]` and `[id]`. A name Core EQ has never been told cannot be recognised, so read the preview before you share it.

## Compatibility

**Android 11 (API 30) and later; built against Android 17 (API 37).** Android does not guarantee a global equaliser, reveal every player's identity, or prove another app's actual output route, so Core EQ reports its apply path instead of promising every app will be affected. HDMI and optical bitstream (passthrough) skips all on-device EQ; the Extra effects screen and Home say when the current setting allows one. Optional DUMP discovery may require an ADB grant that stock/release builds can refuse; Android Notification access is a separate, user-granted option for media-session-based app rules. Permissions: RECORD_AUDIO, MODIFY_AUDIO_SETTINGS; optional **"Modify system settings" (WRITE_SETTINGS)**, requested only when you switch Stereo speakers on, used only to set and restore the TV's Surround sound mode, and refused by the app with an explanation and the manual menu path when not granted; optional Notification access for media-session identity; optional "Display over other apps" (SYSTEM_ALERT_WINDOW) for the on-screen card, which falls back to a text toast without it and is refused on Fire TV.

## Install

Downloader code **`7946159`**, or the permanent APK URL:

**https://github.com/brevityA/CoreBuildsApps/releases/download/coreeq/coreeq-release.apk**

Release builds check the suite's release feed when Home opens and offer the update in-app after verifying its package, version, SHA-256 and signing certificate. Versioned builds are under `coreeq-v*` tags. Test builds are on the [`coreeq-test`](https://github.com/brevityA/CoreBuildsApps/releases/tag/coreeq-test) prerelease; the test package carries no feed and is installed by hand.

## For developers

Build: `cd coreeq && ./gradlew :app:assembleDebug` · DSP reference: `tools/core_eq_dsp.py` · plan: [`docs/CORE_EQ_PLAN.md`](../CORE_EQ_PLAN.md)

Changes: [`coreeq/CHANGELOG.md`](../../coreeq/CHANGELOG.md)
