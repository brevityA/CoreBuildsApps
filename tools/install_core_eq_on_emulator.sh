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

if ! OUT=$(adb install -r "$APK" 2>&1); then
  printf '%s\n' "$OUT" | tail -n 5
  printf '%s\n' "$OUT" | tail -n 2 | while IFS= read -r line; do
    echo "::error title=adb install failed::${line//%/\%25}"
  done
  exit 1
fi
printf '%s\n' "$OUT"

if ! PATH_OUT=$(adb shell pm path "$PACKAGE" 2>&1); then
  echo "::error title=package not installed::${PACKAGE} is not on the device after installing ${APK}: ${PATH_OUT//%/\%25}"
  exit 1
fi
printf 'Installed %s as %s\n' "$APK" "$PACKAGE"
