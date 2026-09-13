# Core Builds on Monet Launcher

How the Core Builds apps hand **icons** and **wallpapers** to Monet Launcher
(`com.klevico.monet`, Klevico), what Monet actually accepts, and what it does
not. Ground truth is the decompiled **Monet v1.0.84** APK (versionCode 118,
released 2026-09-10, decompiled 2026-09-12); raw probe output lives in
`docs/research/monet-probe/`.

---

## The short version

| What | Can we push it into Monet? | How |
|---|---|---|
| **A wallpaper** | **Yes** (Monet 1.0.72+, Monet Premium) | `ACTION_SEND image/*` → `com.klevico.monet.WallpaperShareActivity`. Wired into the app as **Send to Monet**. |
| **Several wallpapers** | **Yes** (same) | `ACTION_SEND_MULTIPLE image/*` → same activity. Wired into Export as **Send N to Monet**. |
| **A folder to rotate** | Manual | Export to `Pictures/CoreBuilds`, then Monet → Settings → Background → Sources → Choose folder. |
| **The icon pack** | **No** — Manual | Monet has no apply intent and no exported settings activity. It *discovers* packs through the standard actions we already declare; the user picks it under Settings → Apps → Icon pack. |
| **Live wallpapers (Core Motion)** | Indirectly | Monet is a *client* of the Projectivy wallpaper-provider API (it binds Aerial Views through it). A built and released `motion-plugin/` would be a candidate source. Not verified end to end. |

Two things that were assumed in this repo and are **false**, both settled by
the dex:

1. *"Monet re-themes from the system wallpaper."* It does not. There is no
   `android.app.WallpaperManager` reference anywhere in Monet. Setting the
   system wallpaper changes nothing on a Monet home screen.
2. *"There is no way to hand Monet a file."* There is, since v1.0.72 ("set any
   image as wallpaper by sharing it from a file manager").

---

## What the APK says

### Exported surface

Only two activities are exported:

- `com.klevico.monet.HomeActivity` — HOME / LAUNCHER / LEANBACK_LAUNCHER.
- `com.klevico.monet.WallpaperShareActivity` — `exported=true`,
  `taskAffinity=com.klevico.monet.share`, `excludeFromRecents=true`, filters:
  - `ACTION_SEND` — `image/*`, `video/*`
  - `ACTION_SEND_MULTIPLE` — `image/*`, `video/*`
  - `ACTION_ATTACH_DATA` — `image/*`, `video/*`
  - category `DEFAULT`; share-sheet label "Set as background".

The settings activity (`MainActivity`) is `exported=false`, so **no deep link
into Monet settings exists**. No exported receiver or service takes an icon
pack. Monet's `<queries>` block lists TV settings packages, Aerial Views, a
dozen cast receivers, and Leanback settings intents — nothing about icon-pack
apply actions.

### `WallpaperShareActivity.onCreate`, in order

1. Collect URIs from `intent.data`, `EXTRA_STREAM` (single or list) and
   `clipData`. `file://` URIs pointing inside Monet's own `filesDir` are
   rejected.
2. MIME per URI: `ContentResolver.getType` → `MimeTypeMap` by extension →
   `intent.type`. Must start with `image/` or `video/`. Nothing valid →
   toast *"This file can't be used as a background."* and
   `finishAndRemoveTask()`.
3. Premium flag false → toast *"Custom backgrounds are part of Monet
   Premium."* and finish. **Custom backgrounds are Premium; the share target
   does not bypass that.**
4. Otherwise each file is copied into `files/backgrounds/bg_<…>.<ext>`; a
   single image toasts *"Set as background"*, several toast *"%d wallpapers
   added"* then *"Open Monet to change the wallpaper"*. Copied images appear
   under Settings → Background → **Gallery**; videos keep their original
   location when possible.

Consequences for a sender: use an explicit component (no chooser on a
remote), put the URI in both `EXTRA_STREAM` and `clipData`, set
`FLAG_GRANT_READ_URI_PERMISSION`, and let Monet do the talking — it toasts
success, Premium, and failure itself.

### Icon-pack support is outbound only

Classes `a/wj1` / `a/tj1` query the package manager for the usual discovery
actions — `org.adw.launcher.THEMES`, `com.novalauncher.THEME`,
`com.anddoes.launcher.THEME`, `com.gau.go.launcherex.theme`,
`com.fede.launcher.THEME_ICONPACK`, `ch.deletescape.lawnchair.ICONPACK`,
`net.oneplus.launcher.icons.ACTION_PICK_ICON`,
`com.teslacoilsw.launcher.THEME` — all of which our manifests already declare,
so all three packs show up in Monet's picker. Monet then loads `appfilter.xml`
via `res.getIdentifier("appfilter","xml",pkg)`, falling back to `icon_pack`
xml and `assets/appfilter.xml`, and looks up `ComponentInfo{pkg/cls}` →
`pkg/cls` → `pkg`. `assets/drawable.xml` feeds its icon browser for per-app
overrides.

There is **no incoming apply action or extra**. The selected pack is written
to Monet's own preferences (`icon_pack_package`, plus `iconPackVariant`,
`iconPackDynamicBackground`, `noBannerIconFallback`, `app_tile_presentation`
= `AUTO | ICON | BANNER`). `iconPackVariant` is an enum inside Monet's per-app
`AppAppearanceOverride` — it selects how Monet renders the pack drawable in a
tile, not a second drawable set from the pack, so **packs do not need to ship
`_banner` variants for Monet**. Icon packs are a Premium feature.

Settings were reorganised in 1.0.80: the icon-pack picker now lives under
**Settings → Apps → Icon pack** (old copy in this repo said "Icons").

### Other facts worth keeping

- `minSdk 26`, `targetSdk 35`, R8-obfuscated, Play Billing 8 with an offline
  activation key. Distributed on Play and as sideload APKs from
  `Klevico/Monet-Launcher` (releases only, no source).
- Monet binds Aerial Views' `WallpaperProviderService` over
  `tv.projectivy.plugin.WALLPAPER_PROVIDER` — the same AIDL contract
  `motion-plugin/` implements. Help text credits "Projectivy Wallpaper
  Provider API (Apache 2.0)".
- Monet's wallpaper library keys: `custom_background_uris`,
  `selected_custom_background_uri`, `wallpaper_folder_uris`,
  `wallpaper_rotation_sources`, `background_style=CUSTOM`. The folder picker
  enumerates MediaStore buckets and `OPEN_DOCUMENT_TREE`, so an exported
  `Pictures/CoreBuilds` is a valid rotation source.
- Uses an accessibility service for HOME-key override and `WRITE_SECURE_SETTINGS`
  via ADB for setting itself as default on locked-down boxes.

---

## What the app does now

### Wallpapers

- **Preview → Set.** When Monet is the HOME launcher *and* resolves the share
  target, the primary button reads **Send to Monet** and fires
  `WallpaperSetter.monetShareIntent(uri)` with a `content://` URI from our
  FileProvider (`cache/wallpapers/` is now exported alongside `cache/updates/`).
  No `SET_WALLPAPER` or storage permission is touched. Any other HOME keeps the
  old `WallpaperManager` path.
- **Export → result.** If Monet is installed, the launcher row leads with
  **Send N to Monet** (`ACTION_SEND_MULTIPLE` over the cached files) and the
  hint names the 1.0.80 path for folder rotation. Existing "Open <launcher>"
  chips remain.
- **Fallback copy** that used to say "Wallpaper → Your own images" now names
  Settings → Background → Sources → Choose folder.

Monet's own toasts carry the outcome. On the free tier the user sees
*"Custom backgrounds are part of Monet Premium."* — from Monet, which is the
honest place for it.

### Icons

Unchanged mechanics; corrected copy. `ApplyIconPack.MONET` still tries the
generic apply contracts and falls to Manual, with `manualPath` now
"Monet Settings → Apps → Icon pack → Core Builds Icon Pack".

---

## Routes for Core Motion (video)

### Route A — Aerial Views bridge (works today)

Monet consumes Aerial Views as a background source over the Projectivy
provider API. Aerial Views accepts custom remote feeds, and
`tools/build_aerial_feed.py` publishes ours:

```
https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/aerial-entries.json
```

1. Install Aerial Views; Settings → Custom feeds → paste the URL.
2. Monet → Settings → Background → Sources → **Aerial Views** → "Use as
   background".

Format mapping (`Motion/live-feed.json` is the source of truth):

| Overflight | Aerial Views |
|---|---|
| `url_1080p` | `url-1080-SDR` |
| `url_4k` | `url-4K-SDR` |
| `title` + `location` + `author` | `accessibilityLabel` |
| filename stem | `id` |
| — | `type: "aerial"`, `timeOfDay: "night"` |

### Route B — local files (works today)

Core Shift downloads loops to `Movies/CoreBuilds`; Monet → Settings →
Background → Sources → Choose image or video. Manual and one at a time. Since
Monet's share target also accepts `video/*`, Core Shift could `ACTION_SEND` the
downloaded `.mp4` the same way the icon pack sends images — an obvious follow-up.

### Route C — ask Klevico (long game)

Two small requests, both cheap given what the APK already contains:

1. An **apply intent for icon packs** (e.g. accept
   `com.novalauncher.THEME` with `ICON_THEME_PACKAGE`, or its own extra). Monet
   already discovers packs through those actions; accepting the inbound form
   is the missing half.
2. Bind **any** `tv.projectivy.plugin.WALLPAPER_PROVIDER` service, not just
   Aerial Views (or expose a "custom feed URL" field). The AIDL client exists.

Klevico ships fixes within days via the internal test channel; file on
`Klevico/Monet-Launcher` issues.

### Route D — release `motion-plugin/`

Monet's provider client may be pinned to Aerial Views' component
(`com.neilturner.aerialviews.services.projectivy.WallpaperProviderService`
is a literal in the dex) or may enumerate the action. The dump does not settle
which. Building and installing Core Motion on a Monet box is the only way to
know; if it enumerates, Core Motion appears in Background → Sources for free.

---

## What we are NOT going to do

- **Set the system wallpaper for Monet.** Proven no-op.
- **Ship a Monet "plugin".** There is no plugin surface beyond the Projectivy
  provider API Monet already speaks.
- **Reverse-engineer Monet's preference store** to inject `icon_pack_package`.
  Closed source, private storage, breaks every update.
- **Invent a Monet apply action.** `tests/test_v151_robustness.py` guards
  against `com.klevico.monet.APPLY_ICONPACK` for that reason.

---

## Summary

| Launcher | Asset | Route | Status |
|---|---|---|---|
| Monet | wallpaper | `ACTION_SEND` → `WallpaperShareActivity` | **shipping** (Send to Monet) |
| Monet | wallpaper set | `ACTION_SEND_MULTIPLE` → same | **shipping** (Send N to Monet) |
| Monet | folder rotation | Export → Background → Sources → Choose folder | works, manual |
| Monet | icon pack | discovery + Settings → Apps → Icon pack | Manual (no inbound API) |
| Monet | Core Motion video | Aerial Views + `aerial-entries.json` | works today |
| Monet | Core Motion video | `motion-plugin/` as provider | unverified (Route D) |
| Projectivy | icon pack / wallpaper | own apply intents | shipping — `docs/PROJECTIVY_DETECTION.md` |
