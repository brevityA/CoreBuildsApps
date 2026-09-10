#!/usr/bin/env bash
# Bootstrap Google cmdline-tools, accept licences, install the packages
# needed to assemble this suite. No emulator / system-images on purpose.
#
# Feature options arrive as uppercase env (camelCase id → UPPERCASE, no
# extra underscores): cmdlineToolsVersion → CMDLINETOOLSVERSION.
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y --no-install-recommends curl unzip ca-certificates
rm -rf /var/lib/apt/lists/*

SDK_ROOT=/usr/local/lib/android/sdk
CMDLINE="${CMDLINETOOLSVERSION:-11076708}"
PACKAGES="${PACKAGES:-platform-tools,platforms;android-34,platforms;android-35,build-tools;34.0.0,build-tools;35.0.0}"

mkdir -p "$SDK_ROOT/cmdline-tools"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE}_latest.zip"
echo "Fetching $ZIP_URL"
curl -fsSL "$ZIP_URL" -o "$TMP/cmdtools.zip"
unzip -q "$TMP/cmdtools.zip" -d "$TMP"
mkdir -p "$SDK_ROOT/cmdline-tools/latest"
mv "$TMP/cmdline-tools/"* "$SDK_ROOT/cmdline-tools/latest/"

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses >/dev/null

IFS=',' read -ra PKGS <<< "$PACKAGES"
sdkmanager --sdk_root="$SDK_ROOT" "${PKGS[@]}"

chmod -R a+rX "$SDK_ROOT"

cat > /etc/profile.d/android-sdk.sh <<EOF
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
EOF
chmod 644 /etc/profile.d/android-sdk.sh
if ! grep -q android-sdk.sh /etc/bash.bashrc 2>/dev/null; then
  echo '. /etc/profile.d/android-sdk.sh' >> /etc/bash.bashrc
fi

echo "Android SDK ready at $SDK_ROOT"
sdkmanager --sdk_root="$SDK_ROOT" --list_installed
