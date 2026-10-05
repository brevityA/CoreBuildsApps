# Core EQ v1.1 — M6a Android TV acceptance runbook

**Status: pending. Release stays held until the DP path and limiter pass on real hardware.**
This is a test record template, not evidence that a device has passed.

## 1. Test build

Use the current v1.1 worktree or a reviewed debug APK. Core EQ now supports
Android 11/API 30 and later, and targets Android 17/API 37. A local build
requires JDK 17, Android SDK platform 37, and ADB:

```sh
cd coreeq
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The public `coreeq-test` APK uses package `tv.corebuilds.eq.debug` and is now
signed with the same stable certificate as production. That certificate comes
from the suite's existing repository secrets (`KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) — the same four every other
app's workflow reads, so there is nothing new to configure in GitHub. The first install after
this signing change cannot update an older runner-debug-signed test build: if
one is present, uninstall only the test package once, then install the new APK.
Production `tv.corebuilds.eq` is a separate package and is not removed.

Record the APK SHA-256 so the logs and audio captures can be tied to the exact
build:

```sh
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

## 2. Device and route record

Fill this in before testing:

| Item | Result |
|---|---|
| TV make/model | |
| Android version / API level / build fingerprint | |
| Core EQ APK SHA-256 | |
| Audio source app and playback content | |
| Output route (TV speakers, HDMI ARC/eARC, optical, other) | |
| AVR/soundbar processing or room correction enabled | |
| Active Core EQ profile | |
| DUMP permission granted (if needed for this player) | |
| Capture method for output audio | |

For DUMP discovery, the PC ADB grant is:

```sh
adb shell pm grant tv.corebuilds.eq android.permission.DUMP
```

## 3. Capture logs

Start this before enabling correction; stop it after the playback checks with
Ctrl+C. Keep the complete log, including warnings and stack traces.

```sh
adb logcat -c
adb logcat -v time -s CoreEqService:V CoreEqDynamics:V AudioEffect:W
```

## 4. Prove DP engagement and read-back

1. Open Core EQ, select the test profile, enable correction, and start the
   target player. Exercise the same output route on which the TV will be used.
2. Record the Home status. It must name `DynamicsProcessing` as the active
   engine; a capability probe alone is not proof of engagement.
3. Find the `CoreEqDynamics` `Configured session=...` line. It must report
   `bands=25`, strictly ascending read-back centres, and limiter values of
   `on`, threshold `-1 dB`, ratio `10:1`, attack `1 ms`, release `50 ms`, and
   post-gain `0 dB`. The nominal layout spans a 40 Hz first centre to a
   roughly 12.65 kHz high-frequency guard centre; the last cutoff is 20 kHz.
4. Verify an actual EQ change with a known non-flat profile and an audio
   capture on the same route. Compare correction on/off at unchanged playback
   and device volume. Logs and UI status alone do not prove audible processing.
5. Record any config refusal, altered cutoff/gain read-back, or fallback. In
   particular, note whether the device accepts the requested low-frequency
   cutoffs below the default AOSP descriptor's 220 Hz minimum.

## 5. Prove limiter action

Use a flat/zero-correction profile so the EQ does not attenuate the test tone
before it reaches the limiter. Play a known near-full-scale signal, for example
a steady 1 kHz sine at `-0.1 dBFS`, and capture the output after the limiter
has settled. Disable downstream room correction, loudness normalization, and
dynamic-range processing for this measurement where possible. Keep the player,
route, TV volume, and remaining signal-chain settings unchanged between the
correction-off baseline and correction-on run. The configured limiter is
10:1 above a `-1 dBFS` threshold, with zero post-gain. The captured output
should show the expected peak reduction and no over-threshold clipping; record
the input/output peak measurements and capture method. If downstream processing
cannot be isolated, record that limitation and do not attribute the result to
Core EQ alone.

A configuration/read-back log, listening test, TV volume display, or phone
sound-meter reading is **not** proof that the digital limiter clamps. Use a
digital/line-level capture or audio analyser capable of measuring the signal
on the tested output route. If no suitable capture is available, mark this
check **not tested** rather than passed.

## 6. Prove safe fallback

On a real route/device where DP construction, control, or configuration is
refused while Equalizer remains available:

1. Confirm the log names the DP refusal and the Equalizer attempt.
2. Confirm Home status names `Equalizer`, not `DynamicsProcessing`.
3. Confirm audio remains present and corrected once, with no overlapping DP
   and Equalizer correction on that route.
4. Save the relevant log and device/route details.

If no refusal can be induced on the available TV, record this check as
**not tested**. Do not disable system effects or modify production firmware
just to force the result; a second test device or a dedicated fault-injection
build will be needed.

## 7. Evidence to return

Send the completed device/route table, filtered log (including any warnings),
Home status screenshots, and the output capture/measurement for EQ engagement
and limiter action. Keep the APK hash with the evidence. M6a passes only when
DP is shown to affect the tested route, the requested bands read back, the
limiter measurably clamps the known signal, and refusal falls back without
stacking a second correction.
