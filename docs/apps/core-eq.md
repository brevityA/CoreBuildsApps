# Core EQ

**Measure the room you sit in, with the remote you already hold.** Back to the [suite page](../../README.md).

Core EQ combines TV-room measurement with a manual 10-band tone EQ. Each measured base belongs to its output; Movie / TV, Everyday and Gaming add independent tone overlays. Choose a mode with the remote or optionally assign app rules for best-effort automatic switching (unmapped, unidentified or conflicting players use Everyday; a temporary or sticky manual override is configurable). Profiles and the current mode can be exported for Poweramp Equalizer or TV sound settings.

## Measuring

A measurement plays a 10-second sweep between two short timing beeps and records it through the remote's microphone; keep the room quiet for 14 seconds. Each sweep gets a 0–100 recording score from its signal to noise; what it found about the room is listed beside it, not scored. Bands the remote could not hear clearly are shown and left uncorrected, and bass dips are lifted by 2 dB at most, because one seat and an uncalibrated microphone cannot tell a room dip from a seat dip.

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
