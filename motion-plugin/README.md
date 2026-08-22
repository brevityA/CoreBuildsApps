# Core Motion — Projectivy wallpaper-provider plugin

The thing Core Shift should have been: a **real Projectivy Launcher plugin**
(Overflight-style) that serves **live wallpapers** — animated video loops — into
Projectivy's wallpaper picker.

This is the actual plugin type Projectivy supports. It implements Spocky's
official `IWallpaperProviderService` AIDL contract and is discovered by
Projectivy via the `tv.projectivy.plugin.WALLPAPER_PROVIDER` intent action.

## What it does

- On Projectivy's rotation timer, returns the **Core Builds live-wallpaper feed**
  as a list of `Wallpaper` (VIDEO) objects; Projectivy caches and cycles them.
- Default feed: [`Motion/live-feed.json`](../Motion/live-feed.json) — six
  procedurally animated loops in the §03 palette (spiral/radial gradients, a
  cellular-automaton "circuit", a deep fractal zoom).
- One editable setting: the **feed URL**, so you can point it at any
  Overflight-compatible JSON source — including real 4K footage you host.

## Install & use

1. Build the APK (`./gradlew :app:assembleDebug`), sideload it.
2. Projectivy → **Settings → Appearance → Wallpaper** → choose **Core Motion**.
3. (Optional) open Core Motion's settings to change the feed URL.

> Requires **Projectivy Launcher Premium** — the same as Overflight and every
> wallpaper provider, because custom wallpaper providers are a Premium feature.

## The contract (do not change)

The AIDL in `app/src/main/aidl/tv/projectivy/plugin/wallpaperprovider/api/` and
the parcelable classes in `app/src/main/java/tv/projectivy/plugin/wallpaperprovider/api/`
are Spocky's frozen API — copied verbatim from
[`spocky/projectivy-plugin-wallpaper-provider`](https://github.com/spocky/projectivy-plugin-wallpaper-provider).
Projectivy binds to this contract; altering it breaks discovery.

| Manifest key | Value | Meaning |
| --- | --- | --- |
| `apiVersion` | `1` | AIDL API level |
| `uuid` | `295b637c-…` | unique plugin id (see `strings.xml`) |
| `name` | `Core Motion` | shown in Projectivy's source list |
| `settingsActivity` | `.SettingsActivity` | config screen |
| `itemsCacheDurationMillis` | `3600000` (1 h) | batch re-fetch interval |
| `updateMode` | `1` | TIME_ELAPSED only |

## Package / build

`tv.corebuilds.motion`, standalone Gradle root (same toolchain as `shift/`:
AGP 8.5.2 / Kotlin 1.9.24 / compileSdk 34 / minSdk 26).

## Feed format (Overflight-compatible)

```json
[
  { "location": "Core Motion", "title": "Spiral Cyan", "author": "Core Builds",
    "url_img": "https://…/thumbs/spiral-cyan.jpg",
    "url_1080p": "https://…/spiral-cyan.mp4" }
]
```

`url_1080p`/`url_4k` → VIDEO wallpaper; `url_img` alone → IMAGE wallpaper.
Regenerate the procedural set with `python tools/build_motion_feed.py`.
