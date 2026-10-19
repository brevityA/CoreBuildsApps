# Core Shift

**Android TV screensaver + motion wallpaper browser.** Back to the [suite page](../../README.md).

Motion wallpapers on Android TV, three ways: browse + preview + download MP4 loops to `Movies/CoreBuilds` for **Monet Premium**'s video picker · the **Core Motion** plugin serves the feed to **Projectivy Premium** as `VIDEO` wallpapers · the **Aerial Views bridge** (`Motion/aerial-entries.json`) gives Monet an auto-updating feed plus a matching screensaver.

Content: 13 ffmpeg-procedural MP4 loops (1080p H.264), 3 self-authored GLSL shaders, bundled Lottie vectors — all §03 palette, HTTPS-only, works offline after first sync.

## Install

Downloader code **`8829421`**, or the permanent APK URL:

**https://github.com/brevityA/CoreBuildsApps/releases/download/shift/coreshift-release.apk**

Versioned builds are under `shift-v*` tags.

## For developers

Build: `cd shift && ./gradlew :app:assembleDebug` · feed check: `python tools/validate_motion_feed.py` · [`shift/README.md`](../../shift/README.md)
