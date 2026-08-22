# Core Shift — a Core Builds wallpaper rotation app for Monet

Research to answer: **can we ship an Overflight-style experience for Monet
Launcher under the Core Builds brand, and what does Overflight actually do?**

Answer up front:

- **Overflight is a Projectivy plugin, not a launcher-agnostic thing.** It is a
  bound Android `Service` speaking Projectivy's private AIDL, discovered via a
  hard-coded intent action. It only works inside Projectivy, and only on the
  Premium tier. It cannot be "installed into" Monet.
- **But the Overflight *behavior* — wallpapers that rotate and re-theme the
  launcher — is exactly what Monet wants, and it is portable.** The portable
  surface is the **system wallpaper**: Monet re-themes its whole UI from it, and
  every launcher renders it. So the right product is **Core Shift, a Core
  Builds-branded universal wallpaper rotation app, Monet-first** — not a plugin.
- **The Overflight feed format is reusable data.** Overflight accepts a JSON
  list whose `url_img` field serves *static images*, so the same
  `Wallpapers/manifest.json` collection can be published as an Overflight
  feed and consumed by Projectivy Premium users for free — a bonus, not the core.

---

## 1. What Overflight actually is (deep dive)

Source: [spocky/projectivy-plugin-wallpaper-overflight](https://github.com/spocky/projectivy-plugin-wallpaper-overflight),
read from the repo (Apr 2026).

### Architecture

Overflight is tiny — one module plus a frozen AIDL API:

```
overflight/
  WallpaperProviderService.kt   ← the whole plugin
  Media.kt                      ← the feed schema
  NetClientManager.kt           ← HTTP + cache
  PreferencesManager.kt         ← SharedPreferences wrapper
  SettingsActivity/Fragment.kt  ← Leanback GuidedStep settings
api/                            ← AIDL contract, "don't change it"
```

`WallpaperProviderService` is a bound `Service` that extends
`IWallpaperProviderService.Stub()` (AIDL) and implements three methods:

- `getWallpapers(event: Event?): List<Wallpaper>` — Projectivy calls this on a
  timer (and on other events, if declared); it returns a list of wallpapers
  which Projectivy caches and rotates through.
- `getPreferences() / setPreferences(params)` — import/export the plugin's
  settings as a JSON string (Projectivy backup integration).

### Discovery (the Projectivy lock-in)

The service is found by one hard-coded intent action plus manifest meta-data:

```xml
<service android:name=".WallpaperProviderService" android:exported="true">
    <intent-filter>
        <action android:name="tv.projectivy.plugin.WALLPAPER_PROVIDER"/>  <!-- DO NOT CHANGE -->
    </intent-filter>
    <meta-data android:name="apiVersion" android:value="1"/>
    <meta-data android:name="uuid" android:value="@string/plugin_uuid"/>  <!-- UUID v4, generate your own -->
    <meta-data android:name="name" android:value="@string/plugin_short_name"/>
    <meta-data android:name="settingsActivity" android:value=".SettingsActivity"/>
    <meta-data android:name="itemsCacheDurationMillis" android:value="@integer/items_cache_duration_millis"/>
    <meta-data android:name="updateMode" android:value="1"/>  <!-- TIME_ELAPSED -->
</service>
```

`updateMode` is a bitmask of `WallpaperUpdateEventType`: `TIME_ELAPSED=1`,
`NOW_PLAYING_CHANGED=2`, `CARD_FOCUSED=4`, `PROGRAM_CARD_FOCUSED=8`,
`LAUNCHER_IDLE_MODE_CHANGED=16`. Overflight uses `1` only (timer). There is no
way to reach this service except through Projectivy — that is the lock-in.

### The feed — JSON or M3U, images *or* video

Overflight loads one URL (configurable) and sniffs it: a string starting with
`[` is JSON, starting with `#` is M3U. The JSON schema (`Media`) is:

```json
{
  "location": "source / series",
  "title":    "display title",
  "url_img":       "http://…/image.jpg",
  "url_1080p":     "http://…/video-1080p.mp4",
  "url_1080p_hdr": "http://…/video-1080p-hdr.mp4",
  "url_4k":        "http://…/video-4k.mp4",
  "url_4k_hdr":    "http://…/video-4k-hdr.mp4",
  "author":   "credit"
}
```

All fields optional; at least one `url_*` must resolve. Preferences
`video_4k` / `video_hdr` / `fallback` choose the URI priority. **Critically for
us: if only `url_img` is present, the entry is an `IMAGE` wallpaper.** So a
static Core Builds feed is a first-class citizen of the format — Overflight was
just aimed at video (Apple Aerials) because that was the gap.

Each entry maps to a `Wallpaper(uri, type, displayMode, title, source, author,
actionUri)` where:

- `type` ∈ `IMAGE, DRAWABLE, ANIMATED_DRAWABLE, LOTTIE, VIDEO, COLOR`
- `displayMode` ∈ `DEFAULT, CROP, STRETCH, BLUR, FIT_CENTER, FIT_CENTER_BLURRED_BG`
- `actionUri` — optional deep link (the Jellyfin/TVBGSuite plugins use it to
  open the media item from "about this wallpaper").

Other details worth copying: comma-separated **keyword filter** (matches title
or source), a **resulting-wallpaper-count hint** in settings, an HTTP cache
(default 24h), `MAX_WALLPAPERS_COUNT = 1000`, and a **refresh broadcast**
(`tv.projectivy.plugin.action.WALLPAPER_PROVIDER_UPDATED` carrying the plugin
UUID + reason `PREFS_CHANGED`/`DATA_CHANGED`) so a plugin can push a refresh
after its data changes.

### Settings UI

A Leanback `GuidedStepSupportFragment`: guidance header (name + version +
description + banner), then checkbox actions (4K, HDR, fallback) and editable
actions (source URL, cache hours, filter keywords), plus an info-only wallpaper
count that the fragment computes on load. Clean TV-first pattern — and the
pattern our own settings screen should follow.

### Takeaway

Overflight = **one bound service + one frozen AIDL + a JSON feed + a GuidedStep
settings screen**. The service is Projectivy-only. The feed and the *idea*
(rotate wallpapers that re-theme the home screen) are portable.

---

## 2. Monet — why the *system wallpaper* is the integration

Monet (`com.klevico.monet`, Klevico) is a Material You TV launcher. Its headline
feature is **dynamic colour**: it pulls an accent palette from the wallpaper and
recolours the entire UI in real time. Key facts for us:

- **No plugin SDK.** Monet's wallpaper sources are built-in and closed: own
  images/videos, ambient/weather scenes, Reddit, Aerial Views, movie-poster
  mode. There is no `tv.projectivy.*`-style extension point and no AIDL. A
  "plugin for Monet" cannot be built — this is settled by the absence of an API,
  not by a design choice on our side.
- **It re-themes from the system wallpaper.** The existing `Set` path in this
  repo already relies on it: `WallpaperManager.setBitmap(...)` → "Monet
  re-themes its tiles from it" (`WallpaperSetter.kt`, `strings.xml`). That is
  the contract we build on.
- **It also supports folder rotation** ("your own images… auto-rotation"),
  which is why the repo exports to `Pictures/CoreBuilds/`. This is likely
  Premium-gated (custom wallpapers and rotation controls sit in Monet's Premium
  tier), and it needs manual setup inside Monet.

So for Monet there are exactly two delivery channels, and they are both
launcher-agnostic:

| Channel | Mechanism | Monet Premium needed? | Setup |
| --- | --- | --- | --- |
| **System wallpaper** (primary) | `WallpaperManager` + timer | No | None — just run Core Shift |
| **Pictures folder** (complement) | export to `Pictures/CoreBuilds`, Monet's "own images" rotation | Likely yes | User picks the folder in Monet once |

Channel 1 is the whole product. Every time Core Shift rotates the system
wallpaper, Monet re-themes to match — cyan, ember, violet, void, in sequence.
That is the Overflight experience (a living, re-colouring home screen), done for
Monet and every other launcher at once, with no Premium and no per-launcher
setup.

---

## 3. What to build: Core Shift

A **standalone, Core Builds-branded wallpaper rotation app, Monet-first.** It is
not a plugin; it is an app that does at the OS level what Overflight does inside
Projectivy.

### The one new primitive — `WallpaperRotator`

Everything else (browse, preview, export, download cache, Fire TV fallback)
already exists in `app/`. The missing piece is rotation:

- Pick the next wallpaper (ordered or shuffle, series-filtered) from the same
  `Wallpapers/manifest.json` source of truth.
- Fetch the cached 4K file, decode to a bitmap, `WallpaperManager.setBitmap(
  bitmap, null, false, FLAG_SYSTEM)`.
- Schedule the next tick with `AlarmManager` and re-arm on `BOOT_COMPLETED`.
- Fire TV: skip direct set, fall back to `Pictures/CoreBuilds` export + crop
  intent (the path already in `WallpaperSetter`).

### Settings (branded, GuidedStep-style)

- **Rotation interval** — 15m / 30m / 1h / 6h / 24h / off.
- **Series filter** — the five series chips already in the browser.
- **Order** — sequential vs. shuffle.
- **Monet note** — one line: "Monet re-themes from the system wallpaper; each
  rotation recolours your home screen." Name the number, no hype.

### Branding (already load-bearing)

**"Core Shift"** is the third "Core ___" name after Core Builds and Core Line —
consistent without being another literal verb. "Shift" carries the double
meaning the product actually has: the *wallpaper* changes and the *accent
colour* shifts with it (Monet re-themes on every rotation). It sits in the
existing receipts voice — "next shift in HH:MM · series", "shift 07 / 70" — and
reuses the collection's own motifs (`66 Core Mark · Preflight Receipt`, "279
passed", "Mixed — cached first") without borrowing their words. Cyan→violet
rail, Outfit wordmark, mono for the status line. Nothing new is invented.

### The honest caveat

TV boxes with aggressive Doze/App-Standby can throttle exact alarms, so rotation
may drift or stall on some hardware. Mitigations: `setExactAndAllowWhileIdle`
for the tick, a battery-optimization-exemption prompt in settings, and a
documented "rotation may drift on aggressive power management" note. This is the
one step that needs a real device to verify — flag it exactly like the repo's
existing device-check precedent.

---

## 4. Bonus (near-free): publish a `preflight.json` feed

Because Overflight's JSON format supports `url_img`, the 70-wall
`Wallpapers/manifest.json` can be transformed into an Overflight-compatible
feed in ~30 lines:

```json
{ "location": "series-4-core-mark", "title": "66 Core Mark · Preflight Receipt",
  "url_img": "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Wallpapers/series-4-core-mark/corebuilds-66-core-mark-receipt.png",
  "author": "Core Builds" }
```

Publishing `preflight.json` at a stable release URL lets Projectivy Premium
users point Overflight at it *today* — same collection, same source of truth,
zero new APK. It is not the primary goal (Monet is), but it costs almost nothing
and widens reach. Later, a native Projectivy provider plugin can reuse the same
mapper if the Premium audience justifies it.

---

## 5. Recommendation

1. **Do not bulk-add static wallpapers now.** 70 across five series already
   exercises the full §03 palette; video and a `series-0` re-render are separate
   efforts, not prerequisites.
2. **Build Core Shift** — the Core Builds wallpaper rotation app, Monet-first:
   - Reuse `Wallpapers/manifest.json` as the single source of truth.
   - Add `WallpaperRotator` (the one new primitive) + interval/series/order
     settings + `BOOT_COMPLETED` re-arm + Fire TV fallback.
   - Brand it: Core Shift name, receipts voice, cyan→violet, Outfit, mono
     status line.
3. **Publish `preflight.json`** as a free bonus feed for Overflight users.
4. **Do not build a Projectivy plugin first.** It is Premium-only and
   Projectivy-only; revisit only after Core Shift ships and only if that audience
   asks for first-class in-picker integration.

### Build order

1. `WallpaperRotator` + `AlarmManager` scheduling + `BOOT_COMPLETED` receiver,
   inside the existing app (already `LAUNCHER` + `LEANBACK`, so it appears in
   every drawer, including Monet's).
2. Rotation settings screen (GuidedStep-style, branded) + a status line on the
   wallpapers screen.
3. `preflight.json` generator (`tools/build_preflight_feed.py`, mirroring the
   existing `build_*` tools) + host at a stable release URL.
4. Device verify the rotation tick on real hardware; document Doze behaviour.

Net: we ship Overflight's experience — rotating, re-theming wallpapers — for
Monet and every other launcher, under the Core Builds brand, with no Premium
gate and no dependence on any launcher's plugin system.
