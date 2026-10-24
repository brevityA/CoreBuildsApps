# Core EQ

**Measure the room you sit in, with the remote you already hold.** Back to the [suite page](../../README.md).

Core EQ combines TV-room measurement with a manual 10-band tone EQ. Each measured base belongs to its output; Movie / TV, Everyday and Gaming add independent tone overlays. Choose a mode with the remote or optionally assign app rules for best-effort automatic switching (unmapped, unidentified or conflicting players use Everyday; a temporary or sticky manual override is configurable). Profiles and the current mode can be exported for Poweramp Equalizer or TV sound settings.

## First install and setup

On a fresh Android TV install, Core EQ opens a short welcome guide. It shows the output Android currently reports, explains the remote-microphone measurement, and warns that the first GREEN-quality measurement for an output type may be applied automatically. You can choose **Start room measurement**, open setup help, or skip the guide; upgrades are not interrupted.

**ADB is optional.** Room measurement, correction and manually selected modes do not need ADB. ADB can enable best-effort automatic app-based mode switching and may grant the optional on-screen card on TVs that permit it. The **Setup check and ADB** screen has copyable commands, a link to Google's official [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools), and remedies for common connection errors. Core EQ never runs ADB commands itself. If your TV refuses the protected `DUMP` permission, use manual mode selection; this is a firmware restriction, not a broken measurement setup. The floating card is optional; the Home status and text toast remain available when overlay access is unavailable.

## Measuring

A measurement plays a 10-second sweep between two short timing beeps and records it through the remote's microphone; keep the room quiet for 14 seconds. Each sweep gets a 0–100 recording score from its signal to noise; what it found about the room is listed beside it, not scored. Bands the remote could not hear clearly are shown and left uncorrected, and bass dips are lifted by 2 dB at most, because one seat and an uncalibrated microphone cannot tell a room dip from a seat dip.

## Backend fit and repeatability

The service status can report the requested correction versus the equalizer's configured/read-back gains at the backend's own band centres. It also reports shared headroom and any gain-range-limited bands. This is a configuration-level fit diagnostic only: it does not measure the speakers, establish the response between EQ bands, or prove that a particular player's audio passed through the effect.

On the TV, **Repeat at same spot** makes a second sweep against the first. On the Android phone companion, repeat checking is opt-in and records two passes; keep the phone still and leave the TV output and volume unchanged. Both traces are retained separately and the first sweep remains the correction; they are never averaged. If the optional phone repeat fails, is cancelled or times out after the first sweep has reached the TV, Core EQ saves that first sweep alone when the TV is still available. A descriptive comparison removes a robust level offset using clean shared mid-band measurements. It needs at least three clean alignment bands and eight clean common bands before it reports summary metrics; these are data-sufficiency gates, not quality cutoffs. If Android reports a changed capture/output path, the summary is withheld; if route metadata is missing, the result is marked provisional. This checks one position only and has no qualified pass/fail threshold.

The Android phone companion can also select a profile/mode and toggle correction over a TV-initiated, encrypted local-network pairing. Phone measurement is foreground-only. Android microphone response and processing vary by handset; route metadata is stored, not treated as proof of an acoustic path or applied as calibration. TV-to-phone capture timing and audio-path behaviour still require qualification on each physical setup.

## Extras and the on-screen card

Optional Extra effects, all off by default: dialogue boost, low-volume bass that follows the TV volume, bass boost, loudness enhancer and night mode. When a different profile or mode takes effect, a card shows for 4 seconds over whatever is playing: profile, mode, output, engine and extras.

## Compatibility

**Android 11 (API 30) and later; built against Android 17 (API 37).** Android does not guarantee a global equaliser, reveal every player's identity, or prove another app's actual output route, so Core EQ reports its apply path instead of promising every app will be affected. HDMI bitstream (passthrough) skips all on-device EQ. Optional DUMP discovery may require an ADB grant that stock/release builds can refuse. Permissions: RECORD_AUDIO, MODIFY_AUDIO_SETTINGS; optional "Display over other apps" (SYSTEM_ALERT_WINDOW) for the on-screen card, which falls back to a text toast without it and is refused on Fire TV.

## Install

Downloader code **`7946159`**, or the permanent APK URL:

**https://github.com/brevityA/CoreBuildsApps/releases/download/coreeq/coreeq-release.apk**

Release builds check the suite's release feed when Home opens and offer the update in-app after verifying its package, version, SHA-256 and signing certificate. Versioned builds are under `coreeq-v*` tags. Test builds are on the [`coreeq-test`](https://github.com/brevityA/CoreBuildsApps/releases/tag/coreeq-test) prerelease; the test package carries no feed and is installed by hand.

## For developers

Build: `cd coreeq && ./gradlew :app:assembleDebug` · DSP reference: `tools/core_eq_dsp.py` · plan: [`docs/CORE_EQ_PLAN.md`](../CORE_EQ_PLAN.md)

Changes: [`coreeq/CHANGELOG.md`](../../coreeq/CHANGELOG.md)
