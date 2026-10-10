# Core Line

**TV-first sports & channel ticker (chyron).** Back to the [suite page](../../README.md).

A reader that crawls the listings your channel apps already publish as RSS — not a player, not streams:

```
LIVE  TOR 3-2 MTL  ·  TSN4  SN 3     ◆     LAL vs BOS  7:00 PM  ·  ESPN
```

One APK for phone, Shield, Google TV, Fire TV · messy listing lines parsed (`Team vs team epn, tsn4` → ESPN, TSN4) · ESPN/NHL/MLB scoreboards with a labeled demo fallback · same-Wi-Fi QR pairing so a Fire remote never types a URL · zero npm dependencies.

## Install

Downloader code **`7375676`**, or the permanent APK URL:

**https://github.com/brevityA/CoreBuildsApps/releases/download/coreline/coreline-release.apk**

Open it — a demo ticker loads immediately, then add feeds or pair from your phone. Already installed? **Settings → Updates**. Versioned builds are under `coreline-v*` tags.

## For developers

Build: `cd ticker/android && ./gradlew :app:assembleDebug` · tests: `cd ticker && npm test` · handover: [`ticker/HANDOVER.md`](../../ticker/HANDOVER.md) · releases: `tools/release_coreline.sh`

Changes: [`ticker/CHANGELOG.md`](../../ticker/CHANGELOG.md)
