# Related Android TV / live-wallpaper repositories

Research date: 2026-08-24.

## Directly relevant references

| Repository | What it offers | What Core Builds should borrow |
|---|---|---|
| [Projectivy wallpaper-provider template](https://github.com/spocky/projectivy-plugin-wallpaper-provider) | Official Projectivy provider template, frozen AIDL API, service/settings wiring, provider metadata and caching hints. | Keep the current vendored contract byte-identical; follow its cache discipline and do not request dynamic update events unnecessarily. |
| [Projectivy Overflight](https://github.com/spocky/projectivy-plugin-wallpaper-overflight) | JSON/M3U-driven Projectivy wallpaper provider with `url_1080p`, `url_4k`, HDR variants and configurable source URL. | Continue using the compatible feed shape; add explicit HDR/SDR fields only when real encodes exist. |
| [Aerial Views](https://github.com/theothernt/AerialViews) | Production Android TV screensaver for Google TV, Nvidia Shield and Fire TV. Supports 4K/HDR, local/network/custom feeds, overlays, playlist caching, burn-in avoidance, D-pad skip, speed, seek, pause, loop and refresh-rate switching. | Best reference for playback/cache/device fallback. Borrow behaviours, not UI/source wholesale. |
| [AlynxZhou video live wallpaper](https://github.com/AlynxZhou/alynx-live-wallpaper) | Video wallpaper app using ExoPlayer plus a custom OpenGL renderer to center-crop video and disable audio. | Strong reason to replace `VideoView` for production preview/download playback with Media3/ExoPlayer and explicit center-crop/audio-off handling. |
| [ShaderEditor](https://github.com/markusfisch/ShaderEditor) | Android GLSL live-wallpaper editor with live preview, arbitrary render resolutions, textures/sensors and low-battery rendering disable. | Reference for a true GPU shader preview and lifecycle/power controls. Do not copy phone-only sensor assumptions into TV. |
| [PureVideoLiveWallpaper](https://github.com/SureshkumarKV/PureVideoLiveWallpaper) | Lightweight OpenGL/GLSL/MediaPlayer video wallpaper pipeline. | Useful comparison for surface lifecycle and shader/video composition, but validate age/device assumptions. |
| [LiveSlider](https://github.com/rahulshah456/LiveSlider) | OpenGL live wallpaper with playlists, configurable slideshow interval, parallax strength and persisted settings. | Playlist/settings model is useful; its sensor/parallax focus is phone-oriented rather than TV-oriented. |
| [TV Background Suite Projectivy plugin](https://github.com/z9m/projectivy-tvbgsuite-plugin) + [web GUI](https://github.com/z9m/androidtvbackgroundWebGui) | Dynamic Projectivy wallpaper provider backed by a configurable canvas/editor/backend, with server-side filtering, layouts and metadata. | Inspiration for a future Core Motion remote art-director/feed editor, not a dependency for the current procedural pipeline. |

## Important findings

### 1. Core Motion is already on the correct Projectivy path

The current plugin should not be replaced. The official template says the API
is version 1, the AIDL contract should not be changed, providers should use
`itemsCacheDurationMillis`, avoid unnecessary dynamic update events and avoid
sending more wallpapers than the launcher needs. The current Core Motion plugin
already follows this general model.

### 2. Overflight is the feed-compatibility benchmark

Overflight's public format supports separate 1080p, 4K, HDR and image fields.
The Core Motion feed already has separate `url_1080p` and `url_4k` values. Do not
claim HDR until actual HDR encodes and device testing exist.

### 3. Aerial Views is the strongest operational reference

Aerial Views covers the problems that are easy to miss after the renderer works:
large-feed caching, Fire TV/Google TV differences, local/network fallback,
playlist state, pause/seek/speed controls, burn-in avoidance and display refresh
rate. Core Motion should copy the product behaviours, especially lifecycle and
cache handling, not fork the project.

### 4. Core Shift should move beyond `VideoView`

The current `PreviewActivity` is a simple `VideoView`. AlynxZhou's reference
shows the more robust pattern: ExoPlayer/Media3 for playback plus OpenGL for
center-crop and audio suppression. This should be a follow-up P1 task if 4K
playback, seek, pause, repeat and device compatibility matter.

### 5. The custom Canvas preview needs a lifecycle/power plan

ShaderEditor and Aerial Views both point toward a better production direction:
GPU rendering, lifecycle-aware pause, low-battery/low-RAM throttling and explicit
resolution control. Core Motion's Android preview should pause when hidden and
use a GPU path or a lower-FPS fallback on low-RAM devices.

## Recommended adoption plan

### Keep

- Core Shift as the user-facing app.
- Core Motion as the Projectivy provider.
- Core Prequel Engine as the self-authored procedural generator.
- Overflight-compatible JSON feed.
- Separate GitHub Release assets for 1080p and 4K.

### Borrow next

1. **Aerial Views behaviours**: cache/index, resume, pause, seek, speed, loop,
   burn-in-safe rotation and device fallback.
2. **AlynxZhou playback architecture**: Media3/ExoPlayer + audio off + explicit
   center-crop.
3. **Official Projectivy template discipline**: cache duration, small batches,
   AIDL stability and source/author metadata.
4. **TV Background Suite compositing ideas**: optional overlays/scrims and a
   future remote feed/art-director surface.

### Do not borrow

- Phone sensor/parallax assumptions from LiveSlider for the TV app.
- Arbitrary third-party shader code.
- Unbounded remote feeds or generic CORS relays.
- HDR claims without real HDR encodes and hardware tests.
- A second provider protocol that would split the existing Core Motion feed.

## Final gap list for Core Builds

| Priority | Task |
|---|---|
| P0 | Test the full D-pad matrix on a physical TV/Fire TV with TalkBack off and on. |
| P0 | Stop refresh callbacks from stealing focus while the user browses. |
| P0 | Verify 1080p and 4K downloads from the actual APK, not just HTTP URLs. |
| P1 | Add download progress, retry, free-space and integrity checks. |
| P1 | Replace `VideoView` with Media3/ExoPlayer for standard TV playback controls. |
| P1 | Pause/throttle the procedural preview based on lifecycle, battery and low-RAM status. |
| P1 | Add accessible labelling or an accessibility delegate for the custom preview view. |
| P2 | Convert the transitional vertical list into horizontal shelves with selected-detail actions. |
| P2 | Add ETag/Last-Modified, stable content IDs and cache eviction. |
