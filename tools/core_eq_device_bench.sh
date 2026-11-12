#!/usr/bin/env bash
# Core EQ device benchmark: run from a computer with one TV connected over adb.
#
#   tools/core_eq_device_bench.sh [report-file]
#   RUNS=5 tools/core_eq_device_bench.sh            # starts per kind (default 5)
#   SKIP_PROFILES=1 tools/core_eq_device_bench.sh    # no remote-control step
#
# Writes one plain-text report: the TV model, Android and app builds, cold and
# warm start times from `am start -W`, frame statistics for Home and Profiles
# from `dumpsys gfxinfo`, and Core EQ's own counters and timings from the
# service dump. It reads and launches; it changes no setting and installs
# nothing. Cold starts force-stop Core EQ, which stops correction until Home
# opens again (Home restarts it). The report holds no serial number, address,
# profile or output name.
#
# One run is one sample on one TV. It is not a performance claim, and runs
# are comparable only on the same TV, firmware and app build. Procedure and
# reading guide: docs/CORE_EQ_DEVICE_BENCHMARK.md.
set -euo pipefail

PKG=tv.corebuilds.eq
HOME_ACTIVITY="$PKG/.MainActivity"
SERVICE="$PKG/.apply.EqService"
RUNS="${RUNS:-5}"
OUT="${1:-core-eq-bench-$(date -u +%Y%m%dT%H%M%SZ).txt}"

die() { echo "core_eq_device_bench: $*" >&2; exit 1; }
sh_() { adb shell "$@" | tr -d '\r'; }

command -v adb >/dev/null || die "adb is not on PATH"
devices=$(adb devices | awk 'NR > 1 && $2 == "device"' | wc -l)
[ "$devices" -eq 1 ] || die "expected exactly one connected device, found $devices"
sh_ pm path "$PKG" | grep -q '^package:' || die "$PKG is not installed on this device"
[[ "$RUNS" =~ ^[1-9][0-9]*$ ]] || die "RUNS must be a positive whole number"

start_once() {
  # One `am start -W`, reported as one of:
  #   the figures Android printed: LaunchState (COLD, WARM or HOT, Android 10+),
  #     TotalTime and WaitTime in ms. LaunchState is Android's own label for the
  #     start that happened, kept as reported: a start after force-stop that
  #     says WARM did not start a new process, whatever the section is called;
  #   "launch failed: <line>" when am printed an error (nothing was started);
  #   "launch did not finish: <line>" for any Status other than ok (a timeout);
  #   "not started: <line>" when Android brought an existing task to the front;
  #   "launched, no start figures reported" when it started but printed none.
  local out figures problem
  out=$(sh_ am start -W -n "$HOME_ACTIVITY" 2>&1 || true)
  problem=$(printf '%s\n' "$out" | grep -m1 -E '^(Error|Exception|java\.)' || true)
  if [ -n "$problem" ]; then echo "launch failed: $problem"; return; fi
  problem=$(printf '%s\n' "$out" | grep -m1 -E '^Status: ' | grep -v '^Status: ok$' || true)
  if [ -n "$problem" ]; then echo "launch did not finish: $problem"; return; fi
  problem=$(printf '%s\n' "$out" | grep -m1 -E '^Warning: Activity not started' || true)
  if [ -n "$problem" ]; then echo "not started: $problem"; return; fi
  figures=$(printf '%s\n' "$out" | grep -E '^(LaunchState|TotalTime|WaitTime):' | tr '\n' ' ' | sed 's/ $//' || true)
  if [ -n "$figures" ]; then echo "$figures"; else echo "launched, no start figures reported"; fi
}

# Count what the lines of one section reported, by Android's LaunchState.
tally() {
  local lines="$1"
  printf 'reported: COLD %s, WARM %s, HOT %s, no LaunchState %s, failed or not started %s\n' \
    "$(grep -c 'LaunchState: COLD' <<<"$lines" || true)" \
    "$(grep -c 'LaunchState: WARM' <<<"$lines" || true)" \
    "$(grep -c 'LaunchState: HOT' <<<"$lines" || true)" \
    "$(grep -E -c ': (TotalTime|WaitTime|launched, no start figures)' <<<"$lines" || true)" \
    "$(grep -E -c ': (launch failed|launch did not finish|not started)' <<<"$lines" || true)"
}

frames() {
  sh_ dumpsys gfxinfo "$PKG" | grep -E '^(Total frames rendered|Janky frames|[0-9]+th percentile|Number Missed Vsync|Number Slow UI thread|Number Frame deadline missed):' || echo "(no frame statistics reported)"
}

{
  echo "# Core EQ device benchmark"
  echo "date_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "tv: $(sh_ getprop ro.product.manufacturer) $(sh_ getprop ro.product.model)"
  echo "android: $(sh_ getprop ro.build.version.release) (API $(sh_ getprop ro.build.version.sdk))"
  echo "firmware: $(sh_ getprop ro.build.fingerprint)"
  echo "app: $(sh_ dumpsys package "$PKG" | grep -m1 -oE 'versionName=[^ ]+' || true) $(sh_ dumpsys package "$PKG" | grep -m1 -oE 'versionCode=[0-9]+' || true)"
  echo "runs_per_kind: $RUNS"
  echo

  # Section names say what the script did; each line keeps what Android reported.
  echo "## Starts after force-stop (meant to be cold; am start -W)"
  section=""
  for i in $(seq 1 "$RUNS"); do
    sh_ am force-stop "$PKG"
    sleep 3
    line="after force-stop $i: $(start_once)"
    echo "$line"; section+="$line"$'\n'
    sleep 3
  done
  tally "$section"
  echo

  echo "## Starts after the Home key (meant to be warm; am start -W)"
  section=""
  for i in $(seq 1 "$RUNS"); do
    sh_ input keyevent KEYCODE_HOME
    sleep 2
    line="after Home key $i: $(start_once)"
    echo "$line"; section+="$line"$'\n'
    sleep 3
  done
  tally "$section"
  echo

  echo "## Home frames (gfxinfo reset, open Home, wait 5 s)"
  sh_ dumpsys gfxinfo "$PKG" reset >/dev/null
  home=$(start_once)
  case "$home" in
    "launch failed"*|"launch did not finish"*|"not started"*) echo "(Home did not open, so no frames: $home)";;
    *) sleep 5; frames;;
  esac
  echo

  echo "## Profiles frames"
  if [ "${SKIP_PROFILES:-0}" = "1" ]; then
    echo "skipped (SKIP_PROFILES=1)"
  else
    sh_ dumpsys gfxinfo "$PKG" reset >/dev/null
    # Profiles is not exported, so adb cannot open it: a person does, with the remote.
    # The prompt goes to the terminal; everything else in this block goes to the report.
    printf 'On the TV, open Profiles from the rail, move down the list and back up once, then press Enter here. ' >/dev/tty
    read -r _ </dev/tty
    frames
  fi
  echo

  echo "## Core EQ counters and timings (service dump)"
  dump=$(sh_ dumpsys activity service "$SERVICE" | sed -n '/Core EQ /,$p' || true)
  if [ -n "$dump" ]; then
    echo "$dump"
  else
    echo "(no dump: the service is not running; switch Correction on and run again)"
  fi
  echo

  echo "## Notes"
  echo "- One sample on one TV; not a performance claim."
  echo "- Compare runs only on the same TV, firmware and app build."
  echo "- LaunchState is Android's label for each start, as reported; only COLD lines time a new process."
  echo "- A failed or unfinished launch is not a sample: leave it out of medians."
  echo "- Home/Profiles 'first draw' and 'ready' count from the activity's construction, after process start."
  echo "- The service dump's counters and timings are in memory and restart with the process: force-stop"
  echo "  clears them, so they cover only what ran after the last start after force-stop above."
} >"$OUT" 2>&1

echo "Wrote $OUT"
