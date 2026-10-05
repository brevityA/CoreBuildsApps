#!/usr/bin/env bash
# Install the Android platform (optionally a system image) a CI job needs,
# tolerating the several ids one API level can carry.
#
# Every workflow states its platform id itself — `platforms;android-37.0` — and
# tools/check_gradle_envelope.py holds that declaration to the compileSdk in
# the build file it names. That literal first attempt stays in the workflow;
# this script is the fallback for the ids the literal cannot cover: an API
# level ships as minor versions (Android 17 is 37.0 / 37.1 / 37.2, and there is
# no plain `platforms;android-37`), and a preview SDK ships under its codename
# (`CinnamonBun` for 37) on the canary channel.
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
# the log and emitted as workflow annotations, which is how a red job here gets
# diagnosed (the raw log is often not downloadable).
set -uo pipefail

CHANNEL="${ANDROID_SDK_CHANNEL:-3}" # canary; sdkmanager channels are inclusive
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
# How many minor versions an API level is probed for (Android 17: 37.0-37.2).
MINOR_LIMIT=2

major_of() { printf '%s' "${1%%.*}"; }

# A preview SDK is published under its release codename; when the level has no
# entry here only the numeric ids are tried.
codename_for() {
  case "$1" in
    37) printf '%s' "CinnamonBun" ;;
    *) printf '%s' "" ;;
  esac
}

# Ordered most-literal first, deduplicated: 37.0, 37, 37.0, 37.1, 37.2,
# CinnamonBun for an input of `37.0`.
platform_candidates() { # <level>
  local level="$1" major minor
  major="$(major_of "$level")"
  {
    printf 'platforms;android-%s\n' "$level" "$major"
    for ((minor = 0; minor <= MINOR_LIMIT; minor++)); do
      printf 'platforms;android-%s.%s\n' "$major" "$minor"
    done
    [[ -n "$(codename_for "$major")" ]] && printf 'platforms;android-%s\n' "$(codename_for "$major")"
  } | awk '!seen[$0]++'
}

system_image_candidates() { # <level> <target> <arch>
  local level="$1" target="$2" arch="$3" major minor
  major="$(major_of "$level")"
  {
    printf 'system-images;android-%s;%s;%s\n' "$level" "$target" "$arch"
    for ((minor = 0; minor <= MINOR_LIMIT; minor++)); do
      printf 'system-images;android-%s.%s;%s;%s\n' "$major" "$minor" "$target" "$arch"
    done
    [[ -n "$(codename_for "$major")" ]] &&
      printf 'system-images;android-%s;%s;%s\n' "$(codename_for "$major")" "$target" "$arch"
  } | awk '!seen[$0]++'
}

# Workflow commands need % , CR and LF escaped; a package id never contains one
# but sdkmanager's progress output and error text do.
annotate() { # <level> <title> <body>
  local level="$1" title="$2" body="$3"
  body="${body//%/\%25}"
  body="${body//$'\r'/\%0D}"
  body="${body//$'\n'/\%0A}"
  printf '::%s title=%s::%s\n' "$level" "$title" "$body"
}

emit_list() { # <title> <body>
  local title="$1" body="$2"
  [[ -n "$body" ]] || return 0
  printf '%s\n%s\n' "$title" "$body"
  annotate notice "$title" "$body"
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    printf '### %s\n\n```\n%s\n```\n' "$title" "$body" >>"$GITHUB_STEP_SUMMARY"
  fi
}

# Which platform, build-tools and system-image ids the repository offers for
# this API level family, and which of them are already installed. This is the
# payload that answers "which package should this job have asked for?"
# without the raw log.
report() { # <level>
  local level="$1" family listing
  family="$(major_of "$level")"
  listing="$(timeout 240 "$SDKMANAGER" --list --channel="$CHANNEL" 2>/dev/null | awk -v family="$family" '
    /^[[:space:]]*Installed packages:/ { section = "installed"; next }
    /^[[:space:]]*Available Packages:/ { section = "available"; next }
    /^[[:space:]]+[A-Za-z0-9._;-]+;/ {
      id = $1
      if (id ~ ("^platforms;android-" family "([^0-9]|$)") ||
          id ~ ("^build-tools;" family) || id ~ /^build-tools;3[0-9]/ ||
          id ~ ("^system-images;android-" family "([^0-9]|$)"))
        printf "[%s] %s\n", (section == "" ? "listed" : section), id
    }' | sort -u)"
  if [[ -z "$listing" ]]; then
    annotate notice "Android SDK packages for API $family (channel $CHANNEL)" \
      "sdkmanager listed no matching platform, build-tools or system-image ids. The SDK repository may be unreachable from this runner."
    return 0
  fi
  emit_list "Android SDK platforms and build-tools (channel $CHANNEL)" \
    "$(printf '%s\n' "$listing" | grep -E '^\[[a-z]+\] (platforms|build-tools);' | head -20)"
  emit_list "Android $family system images (channel $CHANNEL)" \
    "$(printf '%s\n' "$listing" | grep -E "^\[[a-z]+\] system-images;android-$family([^0-9]|$)" | head -20)"
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

install() { # <level> <build-tools> [--system-image <target> <arch>]
  local level="$1" build_tools="$2" target="${3:-}" arch="${4:-}"
  local id installed=1

  # Two passes per candidate: with the requested build-tools, then the platform
  # alone. Build tools are not the platform — AGP fetches the ones it needs on
  # its own — so a wrong build-tools id must not be what stops the compile.
  while read -r id; do
    if try_install "$id" "build-tools;$build_tools"; then installed=0; break; fi
    if try_install "$id"; then installed=0; break; fi
  done < <(platform_candidates "$level")

  if [[ -n "$target" ]]; then
    while read -r id; do
      if try_install "$id"; then installed=0; break; fi
    done < <(system_image_candidates "$level" "$target" "$arch")
  fi

  if ((installed != 0)); then
    annotate warning "No API $level SDK package installed" \
      "Tried $(platform_candidates "$level" | tr '\n' ' ')on channel $CHANNEL. Gradle resolves the platform itself and will name the hash string it cannot find; the package lists below say what this repository really offers."
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
      local level="$1" build_tools="${2:-}" target="" arch=""
      [[ -n "$build_tools" ]] || { echo "usage: $0 <api-level> <build-tools> [--system-image <target> <arch>]" >&2; return 2; }
      if [[ "${3:-}" == "--system-image" ]]; then
        target="${4:-}"
        arch="${5:-}"
        [[ -n "$target" && -n "$arch" ]] || { echo "--system-image needs <target> <arch>" >&2; return 2; }
      fi
      install "$level" "$build_tools" "$target" "$arch"
      ;;
  esac
  return 0
}

main "$@"
