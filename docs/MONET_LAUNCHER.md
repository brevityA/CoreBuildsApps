# Core Motion on Monet Launcher

How to get the Core Builds live wallpapers onto **Monet Launcher**
(`com.klevico.monet`), and why it can't be done the same way as Projectivy.

---

## The short version

**The Core Motion plugin cannot work with Monet, and no amount of work on our
side will change that.** Monet has no wallpaper-provider plugin API. There is no
intent action to answer, no AIDL to implement, no service to export. Projectivy
is the outlier here — Spocky publishes a documented plugin contract; Klevico
does not.

Monet's wallpaper sources are a fixed list, baked into the closed-source APK:

| Source | Third-party extensible? |
|---|---|
| Your own images | no — user picks files |
| Your own videos | **yes, indirectly** — user picks files |
| Built-in wallpapers | no |
| Reddit | no — hardcoded subreddits |
| **Aerial Views** | **yes** — Aerial Views itself takes custom feeds |
| Cinematic movie posters | no |

So there are exactly two routes, and one of them is good.

---

## Route A — the Aerial Views bridge (recommended)

Monet added *"Aerial Views as a live wallpaper and screensaver, plus your own
videos"* in v1.0.45. Aerial Views (`com.neilturner.aerialviews`, GPLv3) supports
**custom remote feeds** in the "community" `entries.json` format.

Chain the two and Monet gets our catalogue, auto-updating, without Klevico
having to know we exist:

```
Motion/aerial-entries.json  ──▶  Aerial Views (custom feed)  ──▶  Monet Launcher
        (we publish)                  (user pastes URL once)        (picks Aerial as source)
```

This is the closest thing to feature parity with the Projectivy plugin: one URL,
set once, and new loops appear as we ship them. It also gets the user a matching
*screensaver* for free, which the Projectivy plugin doesn't do.

### The feed

`tools/build_aerial_feed.py` projects `Motion/live-feed.json` (Overflight
format, the source of truth) into Aerial Views' format. Never hand-edit the
output; CI checks the two stay in sync.

```
https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/aerial-entries.json
```

Format mapping:

| Overflight (`live-feed.json`) | Aerial Views (`aerial-entries.json`) |
|---|---|
| `url_1080p` | `url-1080-SDR` |
| `url_4k` | `url-4K-SDR` |
| `title` + `location` + `author` | `accessibilityLabel` (only place attribution surfaces) |
| filename stem | `id` — stable, so favourites survive a retitle |
| — | `type: "aerial"` |
| — | `timeOfDay: "night"` |

`timeOfDay` matters: the Core Builds palette is dark-first (Night `#0D1117`,
Void `#04070F`). Tagging these `day` makes Aerial Views skip them for anyone
using time-of-day filtering, which reads to the user as a broken feed.

### User steps

1. Install **Aerial Views** (Play Store, Amazon Appstore, or GitHub APK).
2. Aerial Views → `Settings → Custom feeds` (or `Custom Media URLs`) → add:
   `https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/aerial-entries.json`
3. Optionally disable the Apple/Amazon/Jetson sources so only Core Motion plays.
4. Monet → `Settings → Wallpaper → Source → Aerial`.
5. Monet extracts its Material You palette from the video frames — Core Cyan
   `#00E5FF` carries into the tiles and accents.

Same feed also works in Projectivy via Overflight, and Aerial Views is *itself*
a Projectivy wallpaper provider, so this one file covers three launchers.

---

## Route B — local files (already works today)

Monet plays videos from local storage. Core Shift already downloads loops to
`Movies/CoreBuilds`, which is exactly the right shape for this:

1. Core Shift → pick a loop → **Download**.
2. Monet → `Settings → Wallpaper → Your own videos` → browse to
   `Movies/CoreBuilds` → pick the `.mp4`.

Offline and reliable, but manual, one-at-a-time, and doesn't auto-update. Worth
keeping as the fallback for anyone who won't install a second app — and it's the
only route on Fire TV if Aerial Views' storage permissions get awkward.

**Improvement worth making:** Core Shift's success message currently says
*"Saved to Movies/CoreBuilds — set it in Monet"*, which leaves the user to find
the folder themselves. Firing an `ACTION_VIEW` on the saved file, or at minimum
naming the exact path in the toast, would close that gap. Low effort, real payoff.

---

## Route C — ask Klevico for a provider API (long game)

Worth opening as a feature request on `Klevico/Monet-Launcher`, because Monet
already has the two pieces that make it cheap:

- it consumes remote video lists (the Aerial integration proves the plumbing);
- it already supports third-party **icon packs**, so it has a precedent for
  reading from other installed apps.

The concrete ask is small: let a user paste an arbitrary
`entries.json`/Overflight URL directly into Monet's wallpaper settings, skipping
Aerial Views. That's a text field and an HTTP fetch, and it would serve every
wallpaper project on Android TV, not just ours.

A full Projectivy-style AIDL plugin API is the bigger ask and much less likely
to land.

---

## What we are NOT going to do

- **Ship a Monet "plugin".** There is no plugin surface. Anything claiming to be
  one would be a lie.
- **Set the system wallpaper via `WallpaperManager`.** Monet themes from *its
  own* wallpaper setting, not the system one, so this changes nothing on the
  home screen. (It also fails outright on Fire TV, which blocks third-party
  wallpaper writes — the icon pack already works around this by saving to
  `Pictures/CoreBuilds`.)
- **Reverse-engineer Monet's preference store** to inject a wallpaper path.
  It's closed source, unsigned-writable only via root, and would break on every
  update.

---

## Summary

| Launcher | Route | Auto-updating | Status |
|---|---|---|---|
| Projectivy | Core Motion plugin (AIDL) | yes | shipping — see `docs/PROJECTIVY_DETECTION.md` |
| Projectivy | Overflight + our feed URL | yes | works today, no install needed |
| Monet | Aerial Views + `aerial-entries.json` | yes | **feed shipping now** |
| Monet | Core Shift → `Movies/CoreBuilds` | no | works today |
| Any | Aerial Views as screensaver | yes | free side-effect of Route A |
