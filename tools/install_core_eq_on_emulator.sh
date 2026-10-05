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

# adb prints something like `Failure [INSTALL_FAILED_...: message]` before a
# stack trace; that line is the one worth annotating, plus a couple of
# surrounding lines when it is absent.
report_failure() { # <label> <output>
  local label="$1" output="$2" line
  printf '%s\n' "$output"
  line="$(printf '%s\n' "$output" | grep -m1 -E '^Failure \[|INSTALL_FAILED|INSTALL_PARSE_FAILED|Error:' || true)"
  if [[ -z "$line" ]]; then
    line="$(printf '%s\n' "$output" | tail -n 2 | tr '\n' ' ')"
  fi
  echo "::error title=adb install failed ($label)::$(escape "$line")"
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

if ! install_apk "streaming" -r; then
  # Preview images ship the package verifier on; a verifier that cannot reach
  # its service rejects the session with a createSession failure rather than a
  # readable error.
  adb shell settings put global verifier_verify_adb_installs 0 || true
  adb shell settings put global package_verifier_enable 0 || true
  # --no-streaming works around a session failure on preview images; -t covers
  # a debug build that AGP marks test-only, which a stable image accepts.
  install_apk "no-streaming" --no-streaming -r -t || exit 1
fi

if ! PATH_OUT=$(adb shell pm path "$PACKAGE" 2>&1); then
  echo "::error title=package not installed::${PACKAGE} is not on the device after installing ${APK}: $(escape "$PATH_OUT")"
  exit 1
fi
printf 'Installed %s as %s\n' "$APK" "$PACKAGE"
