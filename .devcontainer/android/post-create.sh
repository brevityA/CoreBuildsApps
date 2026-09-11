#!/usr/bin/env bash
# Pin Gradle to the Java Feature's JDK. Stock Codespaces images have been
# known to leave Gradle on a system JDK 11 even when the shell is on 17.
set -euo pipefail

mkdir -p "${HOME}/.gradle"
if [ -n "${JAVA_HOME:-}" ]; then
  grep -q '^org.gradle.java.home=' "${HOME}/.gradle/gradle.properties" 2>/dev/null \
    || echo "org.gradle.java.home=${JAVA_HOME}" >> "${HOME}/.gradle/gradle.properties"
fi

pip3 install --user -r tools/requirements.txt
python3 tools/check_suite_truth.py

echo "Android SDK: ${ANDROID_HOME:-unset}"
echo "Java: ${JAVA_HOME:-unset} ($(java -version 2>&1 | head -1))"
command -v sdkmanager >/dev/null && sdkmanager --list | head -5 || true
