# Core Motion

**Projectivy wallpaper-provider plugin for Core Motion loops.** Back to the [suite page](../../README.md).

The Projectivy-side delivery app: implements Spocky's `IWallpaperProviderService`, serves the GitHub-hosted feed plus bundled vector loops. Separate from Core Shift so the Monet/Aerial and Projectivy paths evolve independently.

## Install

No Downloader code yet. Install from the permanent APK URL:

**https://github.com/brevityA/CoreBuildsApps/releases/download/motion/coremotion-release.apk**

Then in Projectivy: **Settings → Appearance → Wallpaper → Launcher wallpaper → Core Motion** (requires Projectivy Premium). Versioned builds are under `motion-v*` tags.

## For developers

Build: `cd motion-plugin && ./gradlew :app:assembleDebug` · [`motion-plugin/README.md`](../../motion-plugin/README.md)
