# Core Doctor

**Streaming infrastructure diagnostics for your phone.** Back to the [suite page](../../README.md).

Six checks on your setup's health — DNS, VPN detection, addon manifest + stream probe, Real-Debrid and TorBox account status. No backend, no persistence, no analytics; keys go only to their own provider; share reports are redacted by construction. Permissions: INTERNET, ACCESS_NETWORK_STATE, nothing else.

## Install

Downloader code **`8664938`**, or the permanent APK URL:

**https://github.com/brevityA/CoreBuildsApps/releases/download/doctor/coredoctor-release.apk**

Versioned builds are under `doctor-v*` tags.

## For developers

Build: `cd doctor && ./gradlew :app:assembleDebug` · [`doctor/SPEC.md`](../../doctor/SPEC.md)
