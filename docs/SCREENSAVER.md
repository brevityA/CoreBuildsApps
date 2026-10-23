# Core Shift screensaver + Overflight feed

## Screensaver

Core Shift 2.3.5 registers an Android `DreamService`.

1. Open Core Shift → **Screensaver** (quality bar).
2. Or: device Settings → Display / Device Preferences → **Screensaver** → **Core Shift**.
3. Idle timeout is a system setting. Shift does not change it.

Playlist, in order:

1. MP4s already in `Movies/CoreBuilds` (Monet downloads)
2. Internal live cache
3. Bundled Overflight catalog over https

Silent. Shuffled when more than one item. No MediaSession (must not steal now-playing).

Fire TV often has no `ACTION_DREAM_SETTINGS` activity. The toast names the path.

This is **not** a live wallpaper. TV has no `WallpaperService`. Projectivy still uses the Core Motion plugin / Overflight. Monet still uses Aerial Views or the Movies folder.

## Overflight / Aerial paste URL

```
https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/overflight-feed.json
```

54 entries: the 48 live loops plus the 6 series-5 loops (hex glow … zenith),
de-duped, https + GitHub hosts only.

Regenerate:

```bash
python tools/build_overflight_feed.py
python tools/build_aerial_feed.py
python tools/build_overflight_feed.py --check
```

`live-feed.json` stays the validator source of truth for `Motion/live/`. Do not merge series-5 into it unless those files move under `Motion/live/`.

## One wallpaper feed (Motion + Views)

The Core Motion plugin and Aerial Views each read exactly one feed URL, so a
second catalogue used to mean a second setting. `Motion/wallpaper-feed.json` is
the union:

```
https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/wallpaper-feed.json
```

Paste it once into Projectivy → Core Motion → feed, or into Aerial Views →
Custom feeds. Core Views entries appear in the rotation when a season is
rendered and marked ready; nothing has to be reconfigured, and the plugin does
not need an update. Until then the file is the motion set, which is a correct
feed on its own.

```bash
python tools/build_wallpaper_feed.py           # regenerate
python tools/build_wallpaper_feed.py --check   # CI
```

It validates what the consumers cannot: https only, the Core Motion plugin's
host allowlist (`github.com`, `raw.githubusercontent.com`,
`objects.githubusercontent.com`), a playable URL on every entry, and no
duplicate of something already in the rotation.

## Core Views (aerial catalogues)

Core Views is the aerial line: real places, captioned, 20–40 s loops. It rides
the same rails as Core Motion, from one catalogue:

```
views/catalog.json              source of truth (place, time of day, scene,
                                captions, credits)
views/aerial-entries.json       → Aerial Views → screensaver / Monet
views/overflight-feed.json      → Projectivy Premium (Overflight)
python tools/build_views_feed.py --check
```

Unlike `Motion/aerial-entries.json`, which is night for the whole abstract set
on purpose, a Core View carries its own `timeOfDay` and `scene` — Aerial Views
filters on both, so a beach at noon filed as night disappears from a day
playlist. Captions ride in `pointsOfInterest`; nothing is baked into the frame.

A view that has not been rendered publishes nothing. Add it to the catalogue
with `"status": "planned"` and it stays out of both feeds until it is ready.
Season 1 needs no camera: `python tools/render_views.py --all --dry-run` shows
the plan, `--all` renders it from open imagery and terrain.

Format, licences and the authoring studio: [`views/README.md`](../views/README.md),
[`docs/research/core-views-aerial-2026-10.md`](research/core-views-aerial-2026-10.md).
