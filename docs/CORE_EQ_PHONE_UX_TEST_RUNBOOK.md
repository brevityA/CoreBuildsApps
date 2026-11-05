# Core EQ phone UX, profile-status and correction lifecycle regression

Development changes, not a published release or a physical-device receipt.

## What changed

- Phone navigation is **Control / Measure / TV**. A new install starts at TV;
  a saved pairing starts at Control. Returning from another app reads status;
  it does not send an enable, disable, profile or mode command.
- QR pairing leads; manual address/port/code entry is an explicit expansion.
  Find TV is passive. Even an older client's unauthenticated `/v1/wake` probe
  cannot open a TV pairing window. Press **Start pairing** on the TV.
- Target, room volume and profile are named choice lists. Current listening
  mode is marked with both a check and selection styling.
- The phone checks status every 10 seconds **while resumed**, without
  rebuilding controls, disabling the focused control or retrying writes.
  A local command invalidates an older in-flight read. Failed reads preserve
  last-known values, label them as such and disable remote changes.
- Correction intent, service-running state and A/B bypass are distinct.
  Older TVs that do not return `service_running` keep their existing service
  status wording; absence is not interpreted as proof the service stopped.
- Unpair, Forget and replacing a saved TV require confirmation. Forget is
  local only; it never disables TV correction or revokes the TV's token.
- Capture keeps the phone awake, asks Android to lock orientation and cancels
  when the app leaves the foreground. Repeat fallback still saves the first
  sweep separately; it is never averaged.
- A queued STOP no longer writes over a newer enable. A saved off rejects
  sticky/late starts and RESUME/REAPPLY. Async callbacks are guarded by the
  **service instance's** lifetime, not just the public process-wide flag.

The TV listener remains deliberately screen-scoped. Closing **Phone companion**
on the TV disconnects phone control; it must not change the correction switch.
This work does not add an always-on LAN service or TV power control.

## Reported profile-status symptom (clarified)

The user clarified that the **on-screen audio-profile notification appears,
then appears again saying no profile is applied**, while in Core EQ or when
starting audio. This is not a report that the TV powers off or the app closes.
The user has confirmed the current audio output is **Bluetooth** and identified
the host as a **Hotack L007CM Android projector**, with a reported MStar/SigmaStar
m7332 SoC. This identification is user-provided, not hardware-verified here.
The Android release/API, firmware and remote Bluetooth audio device are still
unknown; the SoC and HDMI-CEC label must not be used to infer them or bind an
audio profile. No physical reproduction or device log has been captured for
this branch.

Source-level defects found in the relevant path:

- Every playback callback invalidated the route and opened a silent probe,
  whose own playback could generate another callback. The stream was played
  without queued PCM and with no explicit media attributes.
- An initial routing callback marked the probe ambiguous, just like an actual
  endpoint change. A callback that requested another probe could be consumed
  by the old completion's live callback-drain loop.
- Generation check/cache publication was not atomic. A known, previously
  applied output could not recover through a first-measurement policy gate.
- All route-related pauses used the same "No profile" card. Learned-profile
  selection and session-discovery attachment also needed the same route guards
  as normal application, not a bypass or stale estimate.

The patch coalesces playback reads **400 ms** later (after the finite **250 ms**
probe), verifies a changed read-only route hint or a real media start, and
compares reported endpoint IDs rather than counting initial callbacks. The
probe queues PCM zeros before playing, observes routing for **120 ms**, and
releases its track in `finally`. Cache/generation updates are atomic; callback
batches are detached before dispatch. The running service follows accepted
route changes and recovers only its matching profile while the saved correction
choice remains on. It waits for a fresh cache before configuring effects.

Effects, status and the correction indicator pause immediately for an output
change; an old active overlay is dismissed. A pause warning settles for
**750 ms**. Recovery to the same profile before that timer fires cancels both
the warning and a redundant applied card. This does **not** delay disabling
unsafe correction or suppress a settled pause. Unknown, unconfirmed, ambiguous
and genuinely unmeasured outputs have distinct wording. Profiles are not deleted.

Route evidence still concerns Core EQ's probe, **not another app's route or
end-to-end EQ coverage**. A no-track/pre-playback failure keeps the existing
explicitly labelled estimate fallback; a started probe with no route or an
error is unconfirmed. A retry-cap hold cannot decay into that fallback. On
older Android, a connected hint is coarse; rechecking actual media starts does
not guarantee detecting every settings-only/format-specific routing change.
Physical PCM/bitstream, ARC/eARC and Bluetooth checks remain required.

### Bluetooth device-name follow-up

A same-kind Bluetooth profile can be saved for a specific headset/speaker while
Android later reports only the generic `Bluetooth` label. Matching correctly
refuses to guess that device, but the old pause message incorrectly said nothing
was measured there. The resolver now carries this **missing device identity**
reason through to both service status and the card. It explicitly says profiles
are still saved. The notice key includes the reason, so a corrected explanation
is not hidden by deduplication of the same generic output label.

The shared, Android-free name rules preserve the existing trim/case behavior.
A named but different Bluetooth device still gets no matching profile; even a
single named saved profile is not permission to guess an unnamed route. Generic
profiles retain their existing documented behavior. A2DP and BLE media kinds
remain Bluetooth; SCO/hands-free is not silently equated with that media chain.
No Bluetooth permissions, automatic profile retagging or new fallback were added.
This is a code/messaging fix, not proof that this user's TV reports a generic
name or that its Bluetooth handshake caused the original card transition.

## Automated checks

Android CI, with the module's supported Android SDK and JDK:

```sh
cd coreeq
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

Added JVM/Android unit coverage (the Gradle suite is not run locally):

- `companion/PhoneUiStateTest.kt`: online/offline, busy/capture and retained-first-sweep policies.
- `apply/CorrectionServicePolicyTest.kt`: latest user intent wins; stale STOP;
  off rejects late starts; dead/suspended instances cannot reapply.
- `apply/RouteProbeStateTest.kt`: stale publications, give-up holds, coalescing
  and detached retry batches, including a deterministic cross-thread race.
- `apply/ProbeRouteObservationTest.kt`: initial vs changed endpoint sets,
  A → B → A, repeated playback/probe hints and genuine media starts.
- `AppliedNoticeTest.kt` / `OutputRouteTest.kt`: transient vs settled pauses,
  superseded/cancelled warnings, evidence-independent route identity, Bluetooth
  media kinds, named vs generic device matching and reconnect profile recovery.
- `apply/ProfileOutputIdentityTest.kt`: generic/blank Bluetooth labels vs named
  saved profiles, a different named device and retained generic-profile semantics.
- `apply/EqServiceLifecycleTest.kt`: an off service does not go foreground,
  and ignores START, REAPPLY, RESUME, STOP and a sticky restart.
- `companion/CompanionServerWakeTest.kt`: a legacy probe cannot open pairing,
  start a screen/service or change correction.
- `companion/PhoneCompanionScreenTest.kt`: real phone views, hidden manual
  fields, tabs, named choices, stable focus, blocked controls, stale data,
  capture UI, Forget confirmation and 320/360dp portrait layouts. The test
  writes real-view renders to `coreeq/app/build/screens/phone-*.png` when run.

Off-device/source gates:

```sh
python tests/test_core_eq_companion_focus.py
python tests/test_core_eq_route_stability.py
python tools/core_eq_dsp.py --selftest
python -m pytest tests/test_core_eq_dsp.py tests/test_core_eq_parity.py \
  tests/test_core_eq_companion_focus.py tests/test_core_eq_release_contract.py \
  tests/test_core_eq_update_contract.py tests/test_core_eq_route_stability.py
python tools/check_suite_truth.py
python tools/check_gradle_envelope.py
python tests/test_ci_coverage.py
```

Local receipt after the final profile-status source changes:

- **170 Python tests and 45 subtests passed** across DSP, parity, companion
  focus, route-stability, release and update contracts; DSP self-test passed.
- The actual Android-free production policies were recompiled with a temporary
  Kotlin/JRE toolchain: **60** lifecycle/phone assertions and **1,069** route/
  notice assertions passed, including 100 stale-publication races and 100
  detached-retry cases. **49** further Bluetooth/device-name and notice-policy
  assertions passed. These are standalone smokes, not a JUnit runner.
- **24** changed/new Kotlin files parsed; **76** resource XML files parsed and
  changed Kotlin resource references resolved. Suite truth, Gradle envelope,
  CI coverage, changelog and whitespace gates passed.

**Android build, Android/JUnit/Robolectric tests, lint and physical-device flows
have not run in this workspace**: it has no Android SDK or cached Gradle/Android
dependencies. No APK, device install or screenshots were produced. A syntax
parse and compiled policy smoke are not an APK compilation or a UI render.

## Physical phone / TV matrix (required before release)

Record phone and TV model, OS, Core EQ version, output and time for each row.
Run on Android 11 and a current supported phone/TV OS, including one phone
without working Google Play services. Include a TV box with a D-pad and a
phone-style Android build.

| Flow | Expected |
|---|---|
| Fresh phone, TV pairing screen open | QR action is easy to reach; Find TV only fills address/port. No TV screen or pairing code appears by itself. |
| Start pairing on TV; scan QR | Phone pairs once and opens Control. TV code is spent; phone form is not left over the controls. |
| No Play services / module install denied | Clear QR failure and usable manual entry; no CAMERA permission requested. |
| Local-network or microphone permission denied | Explanation names the permission. No recording or correction change starts. Manual retry works after granting access. |
| Saved TV; background/resume phone | Reconnect/read only. Correction off stays off, on stays on, A/B bypass stays as selected. |
| Wait on a focused mode/profile control | Ten-second reads neither steal focus nor flip settings. Touch and D-pad both work. |
| Physical TV mode/profile/correction change | Phone catches up on refresh. Switch state and service status are not conflated. |
| Disable, immediately enable; then reverse | Final switch wins. No delayed STOP flips it back; no late reapply starts effects after off. |
| Stop/recreate TV service with a pending route probe | Old instance does not reattach effects or overwrite the replacement's status. |
| Close the TV companion screen | Phone reports disconnected/last-known and blocks commands. TV correction does not turn off. Reopen and reconnect. |
| Wi-Fi loss, DHCP change, TV restart, two discoverable TVs | Offline values cannot be used to toggle. Reconnect reuses the token only at a TV that authenticates it. No automatic write retry. |
| Forget while discovery is active; leave/reopen phone | Search stops; late callbacks cannot restore a forgotten token. Forget/unpair can be cancelled. |
| Capture, rotate phone, lock screen, leave app, cancel repeat | Screen stays awake during capture; conflicting controls disabled. Leaving cancels. First sweep fallback stays separate and any failure is named. |
| Narrow phone, landscape, large font, gesture navigation and keyboard | Tabs remain reachable, text wraps, fields stay clear of system bars/IME, and the active page scrolls. |

## Physical profile-card regression (required before calling the symptom fixed)

### Reported target: Hotack L007CM + Bluetooth (unverified)

Do not add a Hotack/m7332 timing workaround or treat the HDMI-CEC room name as
Bluetooth identity without a device trace. The profile matcher uses Android's
audio output kind/name, not the projector's regulatory ID or CEC label.

The installed app already has **Setup → Check again → Copy report**. With
Bluetooth connected and audio playing, collect that report as the baseline. It
includes the manufacturer/model, Android release/API, Core EQ version, current
route indication/evidence and any matched profile. Review before sharing; no
pairing codes or token are needed. If clipboard transfer from the projector is
awkward, a photo of Setup's **Sound output** row is a useful first step, with
the Bluetooth speaker/headphone model supplied separately.

Compare the route and matching state while the applied card is shown against
the state after the no-profile card. Check whether kind, device name or evidence
changes, whether only a generic `Bluetooth` label remains, or whether Android
reports no route/multiple routes. This is evidence gathering on the existing
build, not validation of the uninstalled patch. Confirm the actual Android API
before deciding whether anticipated routing or the connected-output fallback
is in use. No physical/firmware-specific fix is claimed from the model alone.

### Cross-output checks

Use the reported output/player once supplied, then repeat across TV speakers,
HDMI/ARC/eARC PCM, Bluetooth and a known-unmeasured output. Record TV/firmware,
player, output name, selected profile, route evidence and each card's exact
words/time. A configured effect or passing JVM policy test is not proof the
listener's stream traverses the effect.

| Flow | Expected |
|---|---|
| Open Core EQ with correction on; leave Home/Profiles visible | No repeated applied/no-profile cards from UI polling or silent-probe callbacks. |
| Start, pause, resume, skip tracks and play UI sounds on one output | A genuine media start may verify the route; short checks do not flash warnings or reannounce the same profile. The correction switch never changes by itself. |
| Switch between two measured outputs, then return to the first | Each uses only its own matching curve; the known first output recovers without toggling correction again. A real profile change is announced once. |
| Switch to an unmeasured output and leave it there | Effects pause immediately; one settled no-matching-profile card, with the switch's saved intent unchanged. No borrowed TV/soundbar curve. |
| Route unreported or multiple outputs reported | Effects pause; one reason-specific unconfirmed/ambiguous message, not "profile missing". Saved profiles remain listed. Legacy and learned preferences cannot override this pause. |
| Bluetooth route reports only a generic name while all Bluetooth profiles name specific devices | Correction stays paused. Card/status say device name unconfirmed, not that the saved profile disappeared; no guessing even if only one named profile exists. |
| Reconnect the same Bluetooth headset/speaker, then a different named one | Same named device regains its own profile; another device cannot borrow it. Changing only display case/whitespace does not lose a match. |
| Bluetooth media path changes to SCO/hands-free | Do not silently apply the A2DP/LE media curve as if the chain were unchanged. Record reported route/evidence and actual device behavior. |
| Brief route loss then recovery to the same profile | Effects/status reflect the real pause; the old active card is dismissed. No transient warning or duplicate applied card if recovery precedes 750 ms. |
| Rapid ARC/Bluetooth connect/disconnect; then let it settle | Bounded retries and cooldown, no callback-drain loop. Stable matching output recovers; otherwise one honest held-unconfirmed warning and explicit reapply path. |
| Disable during a probe, or recreate the service | No late apply/notice from the old or disabled instance. |
| Measure while device/playback callbacks arrive | No device-callback probe is started into the sweep; effects stay suspended. Resume confirms the route before applying. |
| DUMP/per-session path with a probe pending | Discovery does not attach an estimate's curve; only the confirmed matching profile is configured. |

If the symptom remains, collect `adb logcat -s CoreEqService OutputRoute` around
the card transition and the Capability/status route evidence. The new service
route log names kind/evidence and prior paused state; probe failures are logged.
Keep pairing tokens out of any shared log. UI closure, an actual switch change
and hardware/HDMI-CEC power transitions are separate issues, not evidence of
this profile-card bug.
