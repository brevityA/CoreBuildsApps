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
  # TotalTime/WaitTime in ms, and LaunchState (COLD, WARM or HOT) on Android 10+.
  { sh_ am start -W -n "$HOME_ACTIVITY" | grep -E '^(LaunchState|TotalTime|WaitTime):' | tr '\n' ' '; } || printf '(no start figures reported)'
  echo
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

  echo "## Cold starts (force-stop, then am start -W)"
  for i in $(seq 1 "$RUNS"); do
    sh_ am force-stop "$PKG"
    sleep 3
    printf 'cold %s: ' "$i"; start_once
    sleep 3
  done
  echo

  echo "## Warm starts (Home key, then am start -W)"
  for i in $(seq 1 "$RUNS"); do
    sh_ input keyevent KEYCODE_HOME
    sleep 2
    printf 'warm %s: ' "$i"; start_once
    sleep 3
  done
  echo

  echo "## Home frames (gfxinfo reset, open Home, wait 5 s)"
  sh_ dumpsys gfxinfo "$PKG" reset >/dev/null
  sh_ am start -W -n "$HOME_ACTIVITY" >/dev/null
  sleep 5
  frames
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
  echo "- Home/Profiles 'first draw' and 'ready' count from the activity's construction, after process start."
} >"$OUT" 2>&1

echo "Wrote $OUT"
