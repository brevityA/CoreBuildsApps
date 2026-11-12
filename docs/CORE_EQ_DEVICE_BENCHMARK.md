# Core EQ device benchmark

Milestone C of `docs/CORE_EQ_SOUND_EDITOR_ROADMAP.md`. This page says how to
measure Core EQ on a TV and how to read the result. It records no result:
**no run has been made on any device.** Until one is, nothing in the changelog,
the release notes or a pull request may say Core EQ got faster, smoother or
lighter.

## What is measured

| Figure | Source | Counts from | Counts to |
|---|---|---|---|
| Start after force-stop (meant to be cold) | `am start -W` after `am force-stop` | the launch intent | Home's first frame, as Android reports it (`TotalTime`) |
| Start after the Home key (meant to be warm) | `am start -W` after the Home key | the launch intent | Home's first frame |
| Home / Profiles frames | `dumpsys gfxinfo` after a reset | the reset | the read |
| Apply | `RuntimeTimings`, in the app | `EqService.applyAll` starts deciding | its last decision; outcome ok, not applied, deferred or failed |
| Engine configure | `RuntimeTimings` | one effect's bands and limiter handed to Android | read back, or refused |
| Player discovery | `RuntimeTimings` | one DUMP discovery request | its result parsed |
| Route probe | `RuntimeTimings` | the silent probe track opened | Android's answer; "no answer" when the track named no device |
| Measurement analysis | `RuntimeTimings` | one captured sweep handed to analysis | its result, or its refusal |
| Home first draw / Profiles first draw | `RuntimeTimings` | the activity constructed | its first draw pass |
| Home ready | `RuntimeTimings` | the activity constructed | its summary rendered from loaded profiles and status |
| Profiles ready | `RuntimeTimings` | the activity constructed | its list laid out with rows, or found empty |

"First draw" and "ready" are kept apart because they can come in either
order. Home fills its content before its first frame in the current code, so
Home ready is expected to be shorter than Home first draw; a screen that drew
an empty frame first would show the opposite. Both start at the activity's
construction, after the process exists, so a cold start's process launch is
only in the `am start -W` figure.

### Start lines

Each start is one line, and says one of:

| Line | Meaning | A sample? |
|---|---|---|
| `LaunchState: COLD TotalTime: … WaitTime: …` | Android's own label for the start that happened, kept as reported (Android 10 and later) | Yes, of that LaunchState |
| `LaunchState: WARM …` or `HOT …` in the force-stop section | The process survived the force-stop, or Android reused something: not a cold start, whatever the section is called | Yes, but as WARM or HOT, never as cold |
| `TotalTime: …` with no LaunchState | Android before 10: the kind of start is not reported | Only with that caveat |
| `launched, no start figures reported` | `am` started Home but printed no figures | No |
| `launch failed: Error …` | `am` printed an error; nothing started | No |
| `launch did not finish: Status: timeout` | `am` gave up waiting | No |
| `not started: Warning: Activity not started …` | Android brought an existing task to the front instead | No |

Each section ends with a `reported:` line counting its starts by LaunchState
and by failure. Medians are taken per LaunchState, from sample lines only. A
failed launch in the Home-frames step is reported in place of the frame
figures and does not stop the run.

### When figures reset

- **In-app timings and counters** live in the process. Force-stop, a crash,
  Android reclaiming memory or an app update clears them. The script
  force-stops Core EQ for every start in the first section, so the service
  dump at the end covers only what ran after the last of those: the
  Home-key starts, Home and Profiles. A long-running TV that has not
  restarted Core EQ carries everything since its last start, bounded by the
  window below.
- **Frame statistics** are reset by the script with `dumpsys gfxinfo reset`
  before Home and before Profiles, and also restart with the process.
- **`am start -W` figures** are per start and never accumulate.

The in-app timings keep, per kind: a count, the last, shortest and longest
duration, a count per outcome, and the median of the most recent 32. They are
in memory only, gone when the process ends, never written to the log per
sample, and never sent anywhere. Read them in **Capability → Diagnostics
summary** ("Timings since start") or with
`adb shell dumpsys activity service tv.corebuilds.eq/.apply.EqService`, which
answers only while correction is on.

## Running it

From a computer with `adb` and exactly one TV connected:

```bash
tools/core_eq_device_bench.sh                       # writes core-eq-bench-<UTC time>.txt
RUNS=10 tools/core_eq_device_bench.sh bench.txt     # more starts per kind
SKIP_PROFILES=1 tools/core_eq_device_bench.sh       # no step with the remote
```

Before a run: Core EQ installed, at least one measured profile, Correction on,
nothing else playing, the TV on mains power and awake. The script force-stops
Core EQ for each cold start, which stops correction until Home opens again;
Home restarts it. Profiles is not exported, so the script cannot open it: it
asks you to open Profiles with the remote, move down the list and back up
once, and press Enter.

The report names the TV maker and model, the Android version, the firmware
fingerprint and the app version. It holds no serial number, network address,
profile name or output name.

## Reading it

- One run is one sample on one TV. Compare runs only on the same TV,
  firmware and app build, and only medians over several runs.
- Robolectric runs the same timing code in `TimingWiringTest`; those numbers
  are not a TV's and are never reported.
- A difference smaller than the spread between repeated runs on the same
  build is not a difference.

## Record

| Date | TV | Firmware | App | Runs | Result |
|---|---|---|---|---|---|
| — | — | — | — | — | NOT RUN |
