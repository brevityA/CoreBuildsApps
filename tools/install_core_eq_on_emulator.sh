#!/usr/bin/env bash
# Install the Core EQ APK a CI job downloaded into dist/ on the running
# emulator, then prove the package is really there.
#
# Called as the one-line `script:` input of reactivecircus/android-emulator-runner.
# That input has to stay one line: the action splits it and runs every line as
# its own `sh -c`, so a variable set on one line is gone by the next. The first
# version of this job kept the whole check inline and failed on
# `test -n "$APK"` with an empty variable (the failure showed up as the
# unhelpful "The process '/usr/bin/sh' failed with exit code 1").
#
# The retries exist because preview platforms install differently from stable
# ones: the 37.0 image rejects a streaming install of an APK whose package
# verifier is still enabled, so after the first failure this disables the
# verifier and installs with --no-streaming before giving up. Every attempt
# reports its own error as an annotation, because the raw job log is not always
# downloadable.
set -euo pipefail

APK=$(find dist -maxdepth 1 -type f -name '*.apk' -print -quit)
if [[ -z "$APK" ]]; then
  echo "::error::No Core EQ APK artifact was downloaded into dist/"
  exit 1
fi

# The release package and the debug test package are different ids; installing
# one and probing the other has to be a failure, not a pass.
case "$APK" in
  *coreeq-release.apk) PACKAGE=tv.corebuilds.eq ;;
  *coreeq-debug.apk) PACKAGE=tv.corebuilds.eq.debug ;;
  *)
    echo "::error::Unexpected Core EQ APK asset: $APK"
    exit 1
    ;;
esac

escape() { # % , CR and LF are the workflow-command escapes
  local text="$1"
  text="${text//%/\%25}"
  text="${text//$'\r'/}"
  printf '%s' "${text//$'\n'/ | }"
}

# The raw job log cannot be downloaded from every run, so the numbers that
# decide whether a retry can work are attached as annotations instead: the
# guest's free space on /data, the host's free space, the AVD's own partition
# settings and the APK size. A missing or tiny data partition is the difference
# between "no space left on device" being a storage setting and a real bug.
storage_report() {
  local data host avd_size
  data=$(adb shell 'df -h /data | tail -n 1' 2>&1 | tr -s ' \n' ' ')
  host=$(df -h . 2>&1 | tail -n 1 | tr -s ' \n' ' ')
  avd_size=$(grep -h -iE 'disk|partition' "${ANDROID_AVD_HOME:-$HOME/.android/avd}"/*.avd/config.ini 2>/dev/null | tr '\n' ' ' || true)
  echo "::notice title=emulator storage::apk=$(stat -c%s "$APK")B | guest /data: ${data} | host: ${host} | avd: ${avd_size:-no disk/partition settings}"
}

# adb reports a streaming failure as a Java stack trace and a push failure as
# `adb: error: ...`; the informative line is the one naming INSTALL_*/Failure/
# Exception, and the first lines of output when there is no such line.
report_failure() { # <label> <output>
  local label="$1" output="$2" summary
  printf '%s\n' "$output"
  summary="$(printf '%s\n' "$output" | grep -m2 -E 'Failure|INSTALL_|Exception|error:|No space' || true)"
  if [[ -z "$summary" ]]; then
    summary="$(printf '%s\n' "$output" | head -n 3)"
  fi
  echo "::error title=adb install failed ($label)::$(escape "$summary")"
}

install_apk() { # <label> <adb args...>
  local label="$1"
  shift
  local out
  if out=$(adb install "$@" "$APK" 2>&1); then
    printf '%s\n' "$out"
    return 0
  fi
  report_failure "$label" "$out"
  return 1
}

storage_report
install_apk "streaming" -r || {
  # Preview images ship the package verifier on; a verifier that cannot reach
  # its service rejects the session with a createSession failure rather than a
  # readable error. --no-streaming then covers a session failure, and -t a
  # debug build that AGP marks test-only.
  adb shell settings put global verifier_verify_adb_installs 0 || true
  adb shell settings put global package_verifier_enable 0 || true
  install_apk "no-streaming" --no-streaming -r -t || {
    # A failed push reported `remote write failed: No space left on device` on
    # the 37.0 preview image, so clear the staging area, say how much room
    # /data has, and install straight from stdin instead of copying the APK
    # into /data/local/tmp first.
    storage_report
    adb shell 'rm -rf /data/local/tmp/*' || true
    size=$(stat -c%s "$APK")
    out=$(cat "$APK" | adb shell pm install -r -S "$size" 2>&1) || {
      report_failure "pm install -S" "$out"
      exit 1
    }
    printf '%s\n' "$out"
  }
}

if ! PATH_OUT=$(adb shell pm path "$PACKAGE" 2>&1); then
  echo "::error title=package not installed::${PACKAGE} is not on the device after installing ${APK}: $(escape "$PATH_OUT")"
  exit 1
fi
printf 'Installed %s as %s\n' "$APK" "$PACKAGE"
