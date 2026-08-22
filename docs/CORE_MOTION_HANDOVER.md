# Core Motion — setup, wiring & potential fixes (handover)

Paste this file as the first message to the next agent, or keep it beside the
code. It is enough to continue without the original chat.

---

## 1. 30-second brief

**What this is:** "Core Motion" — the Core Builds **live-wallpaper** product.
Two delivery surfaces for the same animated content:

| Surface | Launcher | Package | What it does |
|---|---|---|---|
| **Core Motion plugin** | Projectivy (Premium) | `tv.corebuilds.motion` (`motion-plugin/`) | a real Projectivy **wallpaper-provider plugin** (Overflight-style) |
| **Core Shift** | Monet (Premium) | `dev.corebuilds.shift` (`shift/`) | a live-wallpaper **browser**: preview + download to `Movies/CoreBuilds` |

The content is **procedural** (no third-party footage), in three tiers:

1. **GLSL shaders** — `motion-shaders/` → `tools/build_shaders.py` (highest quality)
2. **ffmpeg built-in filters** — `tools/build_motion_feed.py` (zero-dependency, shipped)
3. **Lottie vectors** — bundled in the plugin (`WallpaperType.LOTTIE`)

The one shared source of truth is **`Motion/live-feed.json`** (Overflight-compatible).

**Current home:**

| | |
|---|---|
| Repo | `brevityA/CoreBuildsApps` |
| Branch | `arena/01a027f2-corebuildsapps` |
| PR | #33 (open) |
| Head commit | `6f3df5d` |

---

## 2. What exists (file map)

### Plugin — `motion-plugin/` (standalone Gradle root)

| File | Role |
|---|---|
| `app/src/main/aidl/tv/projectivy/plugin/wallpaperprovider/api/*.aidl` | Spocky's **frozen AIDL contract** — do not change |
| `app/src/main/java/tv/projectivy/plugin/wallpaperprovider/api/*.kt` | parcelable `Wallpaper`, `Event`, `WallpaperProviderContract` — do not change |
| `app/src/main/java/tv/corebuilds/motion/WallpaperProviderService.kt` | the provider; returns `BundledAnimations` + `MotionFeed` |
| `app/src/main/java/tv/corebuilds/motion/MotionFeed.kt` | fetch + parse `live-feed.json` → `List<Wallpaper>` (VIDEO/IMAGE) |
| `app/src/main/java/tv/corebuilds/motion/BundledAnimations.kt` | serves bundled Lottie `res/raw/*.json` as `WallpaperType.LOTTIE` |
| `app/src/main/java/tv/corebuilds/motion/Preferences.kt` | feed-URL setting + AIDL get/setPreferences round-trip |
| `app/src/main/java/tv/corebuilds/motion/SettingsActivity.kt` / `SettingsFragment.kt` | Leanback `GuidedStepSupportFragment` settings |
| `app/src/main/AndroidManifest.xml` | the `tv.projectivy.plugin.WALLPAPER_PROVIDER` action + metadata |
| `app/src/main/res/raw/core_hex.json`, `core_diamond.json` | hand-authored Lottie vectors |

Plugin identity (in `AndroidManifest.xml` + `strings.xml`): `uuid =
295b637c-aa92-4d09-8f54-74c4806e1e80`, `apiVersion = 1`, `updateMode = 1`
(TIME_ELAPSED), `itemsCacheDurationMillis = 3600000`.

### App — `shift/` (standalone Gradle root, v2.0.0)

| File | Role |
|---|---|
| `MainActivity.kt` | list the loops (`LiveCatalog`), download to Movies |
| `PreviewActivity.kt` | full-screen looping `VideoView` preview |
| `LiveAdapter.kt` | RecyclerView rows (thumb/title/preview/download) |
| `LiveEntry.kt` / `LiveCatalog.kt` | parse bundled `assets/manifest/live-feed.json` |
| `LiveDownloader.kt` | https-only + GitHub allowlist → `Movies/CoreBuilds` |
| `app/src/main/assets/manifest/live-feed.json` | bundled copy of `Motion/live-feed.json` |
| `app/src/main/assets/live_thumbs/*.jpg` | 10 bundled poster thumbs |

### Content + tools

| Path | What |
|---|---|
| `Motion/live/*.mp4` | 10 ffmpeg-filter loops (1080p, silent, 20s) |
| `Motion/live/thumbs/*.jpg`, `preview/live-preview.png` | posters + contact sheet |
| `Motion/live-feed.json` | Overflight feed (source of truth) |
| `motion-shaders/*.frag` | 3 self-authored GLSL shaders (untested) |
| `tools/build_motion_feed.py` | ffmpeg-filter generator + feed writer |
| `tools/build_shaders.py` | ModernGL + ffmpeg shader renderer |
| `tools/validate_motion_feed.py` | feed↔files coherence |

### Workflows

| File | Status |
|---|---|
| `.github/workflows/core-shift-apk.yml` | **tracked** (committed to `main` earlier) |
| `.github/workflows/core-motion-plugin.yml` | **untracked on disk** — blocked (see §4.1) |

---

## 3. Setup & build

### 3.1 Prerequisites

- JDK 17 + Android SDK (platform 34). Not present in the authoring sandbox.
- Python 3 + `imageio-ffmpeg` (static ffmpeg) for the filter generator.
- For shaders: `moderngl numpy` + a GL context (GPU, or Mesa software GL in CI).

### 3.2 Build the plugin

```bash
cd motion-plugin && ./gradlew :app:assembleDebug
```

### 3.3 Build Core Shift

```bash
cd shift && ./gradlew :app:assembleDebug
```

### 3.4 Regenerate ffmpeg-filter content

```bash
pip install imageio-ffmpeg
python tools/build_motion_feed.py
python tools/validate_motion_feed.py     # expect "OK: 10 live wallpapers"
```

Then re-copy the feed into the app bundle:

```bash
cp Motion/live-feed.json shift/app/src/main/assets/manifest/live-feed.json
cp Motion/live/thumbs/*.jpg shift/app/src/main/assets/live_thumbs/
```

### 3.5 Render the shaders (needs GL — CI or a dev machine)

```bash
pip install moderngl numpy imageio-ffmpeg
python tools/build_shaders.py            # all three
python tools/build_shaders.py --only flow
```

In CI (no GPU): `sudo apt-get install libgl1-mesa-dri libegl1 xvfb` then
`LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a python tools/build_shaders.py`.
Outputs → `Motion/live/coremotion-shader-*.mp4` and appended to `live-feed.json`.

---

## 4. Known issues & potential fixes (most → least urgent)

### 4.1 CI workflow can't be pushed — `workflows` permission  ← do first

**Symptom:** `git push` fails with *"refusing to allow a GitHub App to create or
update workflow .github/workflows/core-motion-plugin.yml without workflows
permission"*.

**Fix (you, the human, not Claude):** grant the GitHub App the
**`workflows: write`** scope, or push with a token that has it. Then:

```bash
git add .github/workflows/core-motion-plugin.yml
git commit -m "ci: Core Motion plugin APK + shader render"
git push
```

The file is complete and YAML-validated (jobs: `validate`, `shaders`, `apk`).
Do **not** rewrite it; it mirrors `core-shift-apk.yml` and adds a software-GL
`shaders` job.

### 4.2 Shaders have never compiled/rendered — verify first run

**Symptom:** unknown — the three `.frag` shaders and `build_shaders.py` have
never executed (no GL in the authoring sandbox; `moderngl` failed with
`libGL.so not found`).

**Fix / verification:** run §3.5. Likely issues to watch:
- GLSL version/`gl_Position` vs `gl_FragCoord` — the renderer binds a
  fullscreen triangle with `in_pos`; shaders use `gl_FragCoord`. That's correct
  for fragment shaders, but confirm `moderngl` compiles them (it reports
  compile errors verbatim).
- `#version 330 core` vs the context's `require=330` — they match.
- ffmpeg `-pix_fmt rgb24` input vs `fbo.read(components=3)` — should match; if
  colours look swapped (RGB/BGR), flip the `components` read or the ffmpeg
  `-pix_fmt`.

### 4.3 Lottie files unverified on device

**Symptom:** unknown — `core_hex.json` / `core_diamond.json` haven't rendered.
They're valid JSON and follow the official sample's `v5.4.4` shape (shape layer,
`sr` star path for hex/diamond, animated rotation, `gf` gradient fill).

**Fix if they don't render:** the `sy`/`pt`/`or`/`os` star-path keys map to the
After Effects "star" shape (used for a 4-point diamond and 6-point hex). If
Projectivy's Lottie engine rejects `ty:"sr"`, fall back to an explicit `ty:"sh"`
Bezier path for the hexagon. Simplest safe first move: ship only
`core_diamond.json` (a 4-point star = square = plain shape) until a device
confirms `ty:"sr"` support.

### 4.4 Plugin not appearing in Projectivy's wallpaper list

**Symptom:** installed, but not in Settings → Appearance → Wallpaper.

**Likely causes, in order:**
1. **Premium** — custom wallpaper providers require Projectivy Premium.
2. **Contract mismatch** — if any AIDL/api file was edited, discovery breaks.
   `git diff` them against the official template; they must be byte-identical.
3. **Manifest metadata** — confirm `tv.projectivy.plugin.WALLPAPER_PROVIDER`
   intent-filter and all six `<meta-data>` are intact.
4. **Projectivy version** — `apiVersion=1` needs a recent Projectivy.

### 4.5 Downloads fail in Core Shift (already fixed once)

**Symptom (fixed in `9d68c19`):** button showed generic "download failed";
root cause was `MotionDownloader` rejecting non-200 + not following redirects.

**If it regresses:** the fix is `instanceFollowRedirects = true` + accept 2xx +
surface the reason in a Toast. The rebuilt `LiveDownloader.kt` already does all
three. Debug via `adb logcat -s CoreShift/Download`.

### 4.6 "life" generator dropped (do not re-add)

`life=` (Conway) produced **93 Mb/s** — 1-bit crisp edges are incompressible and
look bad. If you want a cellular look, `cellauto` (already used, "Circuit") is
the correct choice.

---

## 5. Wiring diagram (text)

```
Motion/live-feed.json  ──(single source of truth)──┐
        │                                           │
        ├─► motion-plugin (Projectivy)              │
        │     WallpaperProviderService              │
        │       ├─ BundledAnimations (Lottie)       │
        │       └─ MotionFeed (VIDEO from feed)     │
        │                                           │
        └─► shift (Monet)                           │
              LiveCatalog (bundled copy of feed)    │
                ├─ PreviewActivity (stream 1080p)   │
                └─ LiveDownloader → Movies/CoreBuilds
```

- `tools/build_motion_feed.py` writes `Motion/live/*.mp4` + `live-feed.json`.
- `tools/build_shaders.py` appends shader renders to `live-feed.json`.
- The plugin reads the feed **live** (remote URL); Core Shift reads a
  **bundled copy** — keep them in sync (§3.4).
- Overflight users can also point the official plugin at `live-feed.json`.

---

## 6. Constraints (do not violate)

- **AIDL/api files are frozen** — byte-identical to `spocky/projectivy-plugin-wallpaper-provider`.
- **Original, licensed content only.** Shaders are self-authored specifically
  because Shadertoy's default license is CC-BY-NC-SA (non-commercial).
- **§03 palette only** (cyan `#00e5ff`, signal `#00d4ff`, build-blue `#4facfe`,
  violet `#8a4890`, ember `#c03a20`, night `#0d1117`, void `#04070f`).
- **Video spec:** MP4 H.264, silent, seamless loop, 1080p default, 4K opt-in,
  OLED-friendly (slow motion, 70–95% dark).
- **Honest copy:** Projectivy Premium required; real footage is the only path to
  Apple-Aerials-grade quality (drops in via `url_4k`, no plugin change).
- **No analytics, no QUERY_ALL_PACKAGES, no background service** beyond what
  Projectivy itself drives.

---

## 7. When you are done (report back)

1. `assembleDebug` result for **both** `motion-plugin/` and `shift/` (with any fixes).
2. `tools/validate_motion_feed.py` final line.
3. First `tools/build_shaders.py` run result (compile + render + feed append).
4. Device facts, marked **verified or unverified**: plugin appears in
   Projectivy; Lottie renders; shader MP4s play; Core Shift downloads play in
   Monet.
5. Whether `core-motion-plugin.yml` is now committed (or still blocked on
   `workflows` permission).
