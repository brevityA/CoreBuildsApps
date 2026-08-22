# Core Shift — live-wallpaper browser (rebuilt)

Rebuilt as a **live-wallpaper app**: browse, preview (full-screen, looping),
and download the Core Builds Motion loops to `Movies/CoreBuilds` for Monet.

This replaces the earlier "static wallpaper rotation" build, which missed the
point. "Live wallpaper" means motion — so Core Shift now shows and delivers
motion, not a pan on a still.

## What it does

- **List** — the Core Motion loops from the bundled Overflight-compatible feed.
- **Preview** — full-screen `VideoView` playing the 1080p loop on repeat, so
  you see the motion before committing.
- **Download** — saves the MP4 into `Movies/CoreBuilds` (MediaStore on API 29+,
  file on ≤28) for Monet → Wallpaper → your videos.

## The two delivery routes

| Route | Launcher | Notes |
|---|---|---|
| **Core Shift** (this app) | Monet (Premium) | download → `Movies/CoreBuilds` → pick in Monet |
| **Core Motion** (`motion-plugin/`) | Projectivy (Premium) | native wallpaper-provider plugin |

## Build

```bash
cd shift && ./gradlew :app:assembleDebug    # JDK 17 + Android SDK
```

Standalone Gradle root, package `dev.corebuilds.shift`, minSdk 26, target/compile
34, AGP 8.5.2 / Kotlin 1.9.24. Dependencies: appcompat, core-ktx, recyclerview.
No WorkManager, no background service — preview and download are user-initiated.

## Not verified here

No Android SDK in the authoring sandbox — this has not compiled or run on a
device. The downloader reuses the redirect-following + surfaced-error fixes
from the prior build.
