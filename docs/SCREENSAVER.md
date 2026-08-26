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

16 entries: the 10 live loops plus the 6 series-5 loops (hex glow … zenith), de-duped, https + GitHub hosts only.

Regenerate:

```bash
python tools/build_overflight_feed.py
python tools/build_aerial_feed.py
python tools/build_overflight_feed.py --check
```

`live-feed.json` stays the validator source of truth for `Motion/live/`. Do not merge series-5 into it unless those files move under `Motion/live/`.
