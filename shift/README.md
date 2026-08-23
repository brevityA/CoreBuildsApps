# Core Shift — Core Motion application

Core Shift is the user-facing Android TV application for Core Motion. It
browses, previews and downloads animated wallpaper loops to
`Movies/CoreBuilds` for Monet and other launchers that read local video
wallpapers.

## What it does

- **Instant startup** — the bundled Core Motion feed works offline.
- **Remote content updates** — on launch, Core Shift checks the stable Series 2/3
  prequel feed and appends newly published generations without requiring an APK
  update. The last good prequel feed is cached for offline use.
- **Preview** — full-screen `VideoView` playing a local cached copy of the loop.
- **Download** — saves the original MP4 into `Movies/CoreBuilds` using MediaStore
  on API 29+ and a scoped file path on API 26–28.
- **APK auto-update** — the existing `UpdateChecker` and `UpdateInstaller` use
  `Latestrelease/shift-version.json` and the Core Shift GitHub Release lane.

The app update channel and content channel are separate: new code ships as an
APK, while new wallpapers ship as an HTTPS/allowlisted feed plus on-demand
media.

## The two delivery routes

| Route | Launcher | Role |
|---|---|---|
| **Core Shift** (this app) | Monet (Premium) | primary catalog, preview, download and updater |
| **Core Motion** (`../motion-plugin/`) | Projectivy (Premium) | companion wallpaper-provider plugin |

The prequel renderer publishes its feed to:

```text
https://github.com/brevityA/CoreBuildsApps/releases/download/motion-prequels/prequel-feed.json
```

The feed is fetched natively by Core Shift, so browser CORS is not involved.
The CORS worker in the sibling `Core-Builds` repository remains scoped to its
AIOStreams paths and is not used as an arbitrary wallpaper relay.

## Build

```bash
cd shift && ./gradlew :app:assembleDebug    # JDK 17 + Android SDK
```

Standalone Gradle root, package `dev.corebuilds.shift`, version 2.3.2 (versionCode 8),
minSdk 26, target/compile 34, AGP 8.5.2 / Kotlin 1.9.24. Dependencies: appcompat,
core-ktx, recyclerview.
No WorkManager or background service is used; network refreshes happen only
when the user opens the app or presses Refresh.

## Content generation

The Series 2/3 prequel engine lives at the repository root:

```bash
python tools/build_prequel_motion.py --set series-2-3
```

The manual `render-prequel-motion.yml` workflow renders through Mesa software
OpenGL, validates the output and publishes the `motion-prequels` release. After
that release exists, Core Shift discovers the 16 prequel entries automatically.
