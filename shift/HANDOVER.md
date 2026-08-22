# Core Shift — handover

Third Core Builds app. Motion wallpaper delivery + static wallpaper rotation
for Monet Launcher (and any launcher that reads the system wallpaper or video
files from a local folder).

## Locked decisions

These are done. Do not revisit without owner approval.

- **Name:** Core Shift.
- **Package:** `dev.corebuilds.shift`.
- **Standalone Gradle root** at `shift/` — not part of the icon pack build, not
  part of Core Line. Same pattern as `ticker/android/`.
- **Motion-first.** The headline feature is downloading branded MP4 loops to
  `Movies/CoreBuilds` for Monet Premium's "your videos." Static wallpaper
  rotation is kept as a secondary, honestly-labeled feature.
- **No Live Wallpaper service.** Android TV does not support `WallpaperService`.
  Launchers play video files from local storage.
- **System wallpaper does not re-theme Monet.** Monet draws its own wallpaper
  (image or video, Premium) and re-themes from that, not from
  `WallpaperManager`. The static rotator is labeled accordingly.
- **Monet and Android TV only.** No phone-specific layouts or features.
- **minSdk 26** (API 26 / Android 8.0). No Fire TV gen 1 support.
- **Same keystore** as icon pack and Core Line (shared signing secrets).
- **Tag prefix:** `shift-v*` — never collides with `v*` (icon pack) or
  `coreline-v*` (Core Line).
- **Host allowlist:** `raw.githubusercontent.com`, `github.com`,
  `objects.githubusercontent.com`. No CDN or third-party hosts.

## What exists

- `shift/` — complete Gradle project skeleton, configured for `:app:assembleDebug` (not yet compiled — first build will be CI or local machine)
- `Motion/` — 6 MP4 loops (1080p H.264, silent, 20s), 1 at 4K, 6 JPEG thumbs
- `Motion/manifest-motion.json` — source of truth (6 entries)
- `Motion/motion-feed.json` — Overflight-compatible feed
- `tools/validate_motion.py` — 112 coherence checks
- `.github/workflows/core-shift-apk.yml` — CI + release pipeline

### App components

| Class | Role |
| --- | --- |
| `MainActivity` | Home screen — motion browse button + rotation toggle |
| `MotionActivity` | Browse motion catalog, download loops |
| `MotionAdapter` | RecyclerView adapter for motion entries |
| `MotionCatalog` | Loads manifest-motion.json (remote with bundled fallback) |
| `MotionDownloader` | Downloads MP4 to Movies/CoreBuilds via MediaStore (API 29+) or file (≤28) |
| `RotationPreferences` | SharedPreferences wrapper for rotation state |
| `RotationScheduler` | Enqueues/cancels WorkManager periodic rotation |
| `RotationWorker` | Sets system wallpaper from Pictures/CoreBuilds on timer |

## What is NOT done

- **First build.** No Android SDK in the authoring environment — the project has
  never been compiled. First `assembleDebug` will be CI or the owner's machine.
- **Launcher icon.** Uses a vector drawable placeholder (hex + diamond). Replace
  with a proper branded icon when ready.
- **Leanback banner.** No `android:banner` on the application tag yet. Needs a
  320×180 PNG like the icon pack has.
- **Proguard rules.** `isMinifyEnabled = false` for now. Add rules if minification
  is enabled later.
- **4K download option.** The catalog has `url_4k` fields but the UI only
  downloads 1080p. Add a resolution picker when 4K loops are produced.
- **Monet setup guide screen.** The plan calls for a guide showing how to set a
  downloaded video as the Monet wallpaper. Not implemented yet.
- **Overflight feed generator script** (`tools/build_motion_feed.py`). The feed
  exists but was hand-authored. A generator from manifest-motion.json should
  replace it.
- **Tests.** No unit or integration tests yet.

## Commands

```bash
python tools/validate_motion.py                  # motion asset coherence (112 checks)
cd shift && ./gradlew :app:assembleDebug         # first build (needs JDK 17 + Android SDK)
```
