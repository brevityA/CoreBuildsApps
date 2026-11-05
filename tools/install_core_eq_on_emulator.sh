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

# A tag run downloads the release APK; a pull request the debug APK plus a
# release-shape APK (the production package, signed with a throwaway key).
# Every one of them is installed and checked, release-shape first, so a pull
# request meets the emulator the way a tag run does before it reaches the
# debug package.
mapfile -t APKS < <(find dist -maxdepth 1 -type f -name '*.apk' | LC_ALL=C sort -r)
if (( ${#APKS[@]} == 0 )); then
  echo "::error::No Core EQ APK artifact was downloaded into dist/"
  exit 1
fi

# The release package and the debug test package are different ids; installing
# one and probing the other has to be a failure, not a pass.
package_for() { # <apk>
  case "$1" in
    *coreeq-release.apk | *coreeq-release-shape.apk) echo tv.corebuilds.eq ;;
    *coreeq-debug.apk) echo tv.corebuilds.eq.debug ;;
    *) return 1 ;;
  esac
}
for APK in "${APKS[@]}"; do
  if ! package_for "$APK" >/dev/null; then
    echo "::error::Unexpected Core EQ APK asset: $APK"
    exit 1
  fi
done

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

# What took the system server down, when one dies under an install. logd
# outlives the system server, so the crash buffer still holds the stack after
# it restarts; without it the job log says "Broken pipe" and nothing about why.
crash_report() { # <label>
  local label="$1" crash first
  crash=$(timeout 30 adb logcat -d -b crash 2>&1 || true)
  echo "::group::crash buffer (${label})"
  printf '%s\n' "$crash"
  echo "::endgroup::"
  echo "::group::package manager and runtime log (${label})"
  timeout 30 adb logcat -d -b main,system 2>&1 \
    | grep -E 'AndroidRuntime|PackageManager|PackageInstaller|system_server|SystemServer|Watchdog|FATAL|ArtService|Permission' \
    | tail -n 200 || true
  echo "::endgroup::"
  first="$(printf '%s\n' "$crash" | grep -m4 -E 'FATAL|Exception|Error:|Caused by' || true)"
  if [[ -n "$first" ]]; then
    echo "::error title=crash buffer (${label})::$(escape "$first")"
  else
    echo "::notice title=crash buffer (${label})::empty"
  fi
}

# A booted emulator is not yet a ready one. The 37.0 preview image restarts its
# whole framework shortly after reporting sys.boot_completed=1, on some boots
# and not others: SystemUI dies with "Couldn't removeRegionSamplingListener",
# surfaceflinger then aborts (SIGABRT in its RegionSampling thread), and init
# brings up a new system server. An install that lands in that window fails
# with "Failure calling service package: Broken pipe (32)" and "Can't find
# service: package" (both coreeq-v1.3.0 tag attempts) or with a
# NullPointerException on StorageManager.getVolumes() from the new system
# server's PackageInstallerService (PR run 37780272513, where the crash buffer
# below caught the whole chain, all of it before the install was sent). That
# run's retry then installed the 1.3.0 release-shape APK and the debug APK.
# So before each attempt, wait until the package service answers twice in a
# row a few seconds apart, retry once it is back, and dump the crash buffer
# whenever an attempt fails.
wait_for_package_service() { # <label>
  local label="$1" deadline=$((SECONDS + 300)) answered=0 out
  while (( SECONDS < deadline )); do
    timeout 60 adb wait-for-device || true
    # Each probe is bounded too: an `adb shell` that hangs on a half-up device
    # would otherwise hold the loop past its deadline.
    out=$(timeout 60 adb shell pm path android 2>/dev/null || true)
    if [[ "$(timeout 60 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" && "$out" == *package:* ]]; then
      answered=$((answered + 1))
      if (( answered >= 2 )); then
        return 0
      fi
    else
      answered=0
    fi
    sleep 5
  done
  echo "::error title=package service not ready (${label})::the emulator's package service did not answer twice in a row within 300 s"
  return 1
}

install_one() { # installs $APK and proves $PACKAGE is on the device
  PACKAGE=$(package_for "$APK")
  storage_report
  wait_for_package_service "before installing" || return 1
  if ! install_apk "streaming" -r; then
    crash_report "$(basename "$APK"), first attempt"
    # A system server that restarted under the install leaves the same streaming
    # install valid once the package service is back, so that is retried first.
    wait_for_package_service "after the first attempt" || return 1
    if ! install_apk "streaming, package service back" -r; then
      # Preview images ship the package verifier on; a verifier that cannot reach
      # its service rejects the session with a createSession failure rather than a
      # readable error. --no-streaming then covers a session failure, and -t a
      # debug build that AGP marks test-only.
      adb shell settings put global verifier_verify_adb_installs 0 || true
      adb shell settings put global package_verifier_enable 0 || true
      if ! install_apk "no-streaming" --no-streaming -r -t; then
        # A failed push reported `remote write failed: No space left on device` on
        # the 37.0 preview image, so clear the staging area, say how much room
        # /data has, and install straight from stdin instead of copying the APK
        # into /data/local/tmp first.
        storage_report
        adb shell 'rm -rf /data/local/tmp/*' || true
        wait_for_package_service "before pm install -S" || return 1
        size=$(stat -c%s "$APK")
        out=$(cat "$APK" | adb shell pm install -r -S "$size" 2>&1) || {
          report_failure "pm install -S" "$out"
          crash_report "$(basename "$APK"), last attempt"
          return 1
        }
        printf '%s\n' "$out"
      fi
    fi
  fi

  wait_for_package_service "before checking the package" || return 1
  if ! PATH_OUT=$(adb shell pm path "$PACKAGE" 2>&1); then
    echo "::error title=package not installed::${PACKAGE} is not on the device after installing ${APK}: $(escape "$PATH_OUT")"
    return 1
  fi
  printf 'Installed %s as %s\n' "$APK" "$PACKAGE"
}

# On-device tests (coreeq/app/src/androidTest), on the runs that built them:
# pull requests and main pushes, never a tag. They cover what only a real
# Android Keystore enforces; 1.13.0's phone pairing passed every JVM test and
# failed on every phone with "Caller-provided IV not permitted".
TEST_APK=androidtest/coreeq-debug-androidTest.apk
TEST_RUNNER=tv.corebuilds.eq.debug.test/androidx.test.runner.AndroidJUnitRunner
run_device_tests() {
  local out passed
  APK="$TEST_APK"
  wait_for_package_service "before installing the on-device tests" || return 1
  if ! install_apk "on-device tests, streaming" -r -t; then
    # The same preview-image fallback the app installs use, in one step.
    adb shell settings put global verifier_verify_adb_installs 0 || true
    adb shell settings put global package_verifier_enable 0 || true
    wait_for_package_service "before the on-device tests' second install" || return 1
    install_apk "on-device tests, no-streaming" --no-streaming -r -t || return 1
  fi
  out=$(timeout 600 adb shell am instrument -w "$TEST_RUNNER" 2>&1 || true)
  printf '%s\n' "$out"
  # am instrument exits 0 whether or not a test failed; its summary line says
  # which, and "OK (0 tests)" would mean the runner found nothing to run.
  passed=$(printf '%s\n' "$out" | grep -oE 'OK \([1-9][0-9]* tests?\)' | tail -n 1 || true)
  if [[ -n "$passed" ]] && ! printf '%s\n' "$out" | grep -qE 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'; then
    echo "::notice title=Core EQ on-device tests::${passed}"
    return 0
  fi
  echo "::error title=Core EQ on-device tests::$(escape "$(printf '%s\n' "$out" | grep -m6 -E 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|Exception|Error|^[0-9]+\) ' || printf 'no OK line in the am instrument output')")"
  return 1
}

failed=()
for APK in "${APKS[@]}"; do
  # A plain header, not a ::group::, because the crash dump below opens its
  # own groups and Actions does not nest them.
  echo "=== install $(basename "$APK") ==="
  if ! install_one; then
    failed+=("$(basename "$APK")")
  fi
done
if (( ${#failed[@]} )); then
  echo "::error title=Core EQ install smoke::did not install: ${failed[*]}"
  exit 1
fi

if [[ -f "$TEST_APK" ]]; then
  echo "=== on-device tests ==="
  if ! run_device_tests; then
    echo "::error title=Core EQ on-device tests::the tests in coreeq/app/src/androidTest did not pass on this emulator"
    exit 1
  fi
fi
