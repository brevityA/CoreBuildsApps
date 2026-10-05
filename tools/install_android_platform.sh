#!/usr/bin/env bash
# Install the Android platform (optionally the system image) a CI job needs,
# tolerating the two ids an SDK level can carry while it moves from preview to
# stable.
#
# Every workflow states its platform id itself — `platforms;android-37` — and
# tools/check_gradle_envelope.py holds that declaration to the compileSdk in
# the build file it names. That literal first attempt stays in the workflow;
# this script is the fallback for when the numeric package is not in the SDK
# repository yet: Google publishes a preview SDK under its codename (API 37
# shipped as "CinnamonBun") on the canary channel, so a job that only asks for
# the numeric id fails before it ever reaches Gradle.
#
# Usage:
#   install_android_platform.sh --report <api-level>
#   install_android_platform.sh <api-level> <build-tools> [--system-image <target> <arch>]
#
# The fallback deliberately exits 0 even when no package could be installed.
# This script cannot name the platform Gradle will fail on, so it does not
# pretend to be the gate: Gradle resolves the platform itself and reports the
# hash string it could not find. What this script owns is the evidence — every
# attempt, its error text, and every id the repository offers are printed to
# the log and emitted as workflow annotations, which is the only way a red job
# here can be diagnosed (the raw log is often not downloadable).
set -uo pipefail

CHANNEL="${ANDROID_SDK_CHANNEL:-3}" # canary; sdkmanager channels are inclusive
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

# A preview SDK is published under its release codename; when the level is not
# listed here only the numeric id is tried.
codename_for() {
  case "$1" in
    37) printf '%s' "CinnamonBun" ;;
    *) printf '%s' "" ;;
  esac
}

# Workflow commands need % , CR and LF escaped; a package id never contains one
# but sdkmanager's error text does.
annotate() { # <level> <title> <body>
  local level="$1" title="$2" body="$3"
  body="${body//%/\%25}"
  body="${body//$'\r'/\%0D}"
  body="${body//$'\n'/\%0A}"
  printf '::%s title=%s::%s\n' "$level" "$title" "$body"
}

# Every platform id on this channel from API 30 up, plus the system images for
# the requested level (numeric and codename). This is the payload that answers
# "which package should this job have asked for?" without the raw log.
report() { # <api-level>
  local level="$1" listing
  listing="$(timeout 240 "$SDKMANAGER" --list --channel="$CHANNEL" 2>/dev/null |
    awk -v level="$level" '
      /^[[:space:]]+[a-z-]+;/ {
        id = $1
        if (id ~ /^platforms;android-3[0-9]/ || id ~ /^platforms;android-[A-Za-z]/ \
            || id ~ ("^system-images;android-" level "([^0-9]|$)") \
            || id ~ /^system-images;android-[A-Za-z]/) print id
      }' | sort -u | head -30)"
  if [[ -z "$listing" ]]; then
    annotate notice "Android SDK packages on channel $CHANNEL" \
      "sdkmanager listed no platform or system-image ids (API $level and up). The SDK repository may be unreachable from this runner."
    return 0
  fi
  printf 'SDK packages this runner offers (channel %s):\n%s\n' "$CHANNEL" "$listing"
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
      printf '### SDK packages this runner offers (channel %s)\n\n```\n%s\n```\n' "$CHANNEL" "$listing"
    } >>"$GITHUB_STEP_SUMMARY"
  fi
  annotate notice "Android SDK packages on channel $CHANNEL" "$listing"
}

try_install() { # <package-id> [<package-id> ...]
  local out
  if out="$(timeout 1800 "$SDKMANAGER" --install "$@" --channel="$CHANNEL" 2>&1)"; then
    printf 'installed: %s\n' "$*"
    return 0
  fi
  printf '%s\n' "$out" | tail -n 5
  annotate warning "sdkmanager could not install $1" "$(printf '%s' "$out" | tail -n 3)"
  return 1
}

install() { # <api-level> <build-tools> [--system-image <target> <arch>]
  local level="$1" tools="build-tools;$2" target="${3:-}" arch="${4:-}"
  local code candidates id installed=1
  code="$(codename_for "$level")"

  candidates=("platforms;android-$level")
  [[ -n "$code" ]] && candidates+=("platforms;android-$code")
  for id in "${candidates[@]}"; do
    if try_install "$id" "$tools"; then
      installed=0
      break
    fi
  done

  if [[ -n "$target" ]]; then
    candidates=("system-images;android-$level;$target;$arch")
    [[ -n "$code" ]] && candidates+=("system-images;android-$code;$target;$arch")
    for id in "${candidates[@]}"; do
      if try_install "$id"; then
        installed=0
        break
      fi
    done
  fi

  if ((installed != 0)); then
    annotate warning "No API $level SDK package installed" \
      "Tried the numeric id${code:+ and the $code codename} on channel $CHANNEL. Gradle resolves the platform itself and will name the hash string it cannot find; the package list below says what this repository really offers."
  fi

  # Always report, so the ids land in the run's annotations whether or not the
  # install worked.
  report "$level"
  return 0
}

main() {
  if [[ ! -x "$SDKMANAGER" ]]; then
    annotate warning "Android SDK not found" "no sdkmanager at $SDKMANAGER"
    return 0
  fi
  case "${1:-}" in
    --report)
      [[ $# -ge 2 ]] || { echo "usage: $0 --report <api-level>" >&2; return 2; }
      report "$2"
      ;;
    "")
      echo "usage: $0 --report <api-level> | $0 <api-level> <build-tools> [--system-image <target> <arch>]" >&2
      return 2
      ;;
    *)
      local level="$1" tools="${2:-}" target="" arch=""
      [[ -n "$tools" ]] || { echo "usage: $0 <api-level> <build-tools> [--system-image <target> <arch>]" >&2; return 2; }
      if [[ "${3:-}" == "--system-image" ]]; then
        target="${4:-}"
        arch="${5:-}"
        [[ -n "$target" && -n "$arch" ]] || { echo "--system-image needs <target> <arch>" >&2; return 2; }
      fi
      install "$level" "$tools" "$target" "$arch"
      ;;
  esac
  return 0
}

main "$@"
