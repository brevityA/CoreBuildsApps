#!/usr/bin/env bash
#
# Core Builds Icon Pack — device scanner.
#
# Lists every launchable app on a connected Android TV device with its real
# launcher component, and reports which ones the pack already covers.
#
# Usage:
#   ./tools/scan_device.sh                  # third-party apps (default)
#   ./tools/scan_device.sh --all            # include system apps
#   ./tools/scan_device.sh --serial ABC123  # pick a device when several are attached
#
# Output:
#   tools/device_scan.json   machine-readable, feed to import_scan.py
#   tools/device_scan.txt    human-readable table
#
# Requires: adb on PATH, device paired over USB or network ADB.

set -u

SERIAL=""
INCLUDE_SYSTEM=0
OUT_JSON="tools/device_scan.json"
OUT_TXT="tools/device_scan.txt"

while [ $# -gt 0 ]; do
  case "$1" in
    --all) INCLUDE_SYSTEM=1; shift ;;
    --serial) SERIAL="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

ADB="adb"
if [ -n "$SERIAL" ]; then ADB="adb -s $SERIAL"; fi

command -v adb >/dev/null 2>&1 || {
  echo "adb not found on PATH."
  echo "  macOS:   brew install --cask android-platform-tools"
  echo "  Linux:   sudo apt install adb"
  echo "  Windows: https://developer.android.com/tools/releases/platform-tools"
  exit 1
}

# --- device check -----------------------------------------------------------
DEVICES=$($ADB devices | sed '1d' | grep -c "device$" || true)
if [ "$DEVICES" -eq 0 ]; then
  echo "No device connected."
  echo
  echo "USB:     plug in, enable Developer options -> USB debugging, accept the prompt."
  echo "Network: on the TV enable Developer options -> Network debugging, then:"
  echo "           adb connect <TV-IP>:5555"
  echo "         (Find the IP under Settings -> Network -> About.)"
  exit 1
fi
if [ "$DEVICES" -gt 1 ] && [ -z "$SERIAL" ]; then
  echo "Several devices attached — pick one with --serial:"
  $ADB devices | sed '1d' | grep "device$"
  exit 1
fi

MODEL=$($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r')
RELEASE=$($ADB shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
SDK=$($ADB shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
echo "Device: ${MODEL:-unknown} · Android ${RELEASE:-?} (API ${SDK:-?})"
echo

# --- collect packages -------------------------------------------------------
if [ "$INCLUDE_SYSTEM" -eq 1 ]; then
  PKG_FLAG=""
  echo "Scanning all packages (including system)..."
else
  PKG_FLAG="-3"
  echo "Scanning third-party packages... (use --all to include system apps)"
fi

PKGS=$($ADB shell pm list packages $PKG_FLAG 2>/dev/null \
        | tr -d '\r' | sed 's/^package://' | sort -u)

TOTAL=$(printf '%s\n' "$PKGS" | grep -c . || true)
echo "Found $TOTAL packages. Resolving launcher activities..."
echo

# --- resolve each package's launcher component ------------------------------
# resolve-activity --brief returns the activity the launcher would start.
# Leanback (TV) activities are queried separately because some apps expose a
# different activity to TV launchers than to phone launchers — that difference
# is exactly what makes icons fail to auto-assign.
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

LEANBACK=$($ADB shell "cmd package query-activities --brief \
  -a android.intent.action.MAIN \
  -c android.intent.category.LEANBACK_LAUNCHER" 2>/dev/null | tr -d '\r' || true)

i=0
for PKG in $PKGS; do
  i=$((i + 1))
  printf "\r  [%d/%d] %-50.50s" "$i" "$TOTAL" "$PKG"

  COMP=$($ADB shell "cmd package resolve-activity --brief $PKG" 2>/dev/null \
          | tr -d '\r' | grep "/" | tail -1 || true)

  # Prefer a leanback activity when the app ships one.
  LB=$(printf '%s\n' "$LEANBACK" | grep "^${PKG}/" | head -1 || true)

  [ -z "$COMP" ] && [ -z "$LB" ] && continue

  VER=$($ADB shell "dumpsys package $PKG | grep -m1 versionName" 2>/dev/null \
          | tr -d '\r' | sed 's/.*versionName=//' || true)

  printf '%s\t%s\t%s\t%s\n' "$PKG" "${COMP:-}" "${LB:-}" "${VER:-}" >> "$TMP"
done
printf "\r%-70s\r" " "

# --- hand off to the reporter ----------------------------------------------
python3 tools/import_scan.py --raw "$TMP" --json "$OUT_JSON" --txt "$OUT_TXT" \
  --model "${MODEL:-unknown}" --api "${SDK:-?}"
