# Core Shift — build plan (revised: motion-first)

A standalone, Core Builds-branded **motion wallpaper app for Monet Launcher**
(and, secondarily, Projectivy and any launcher that reads the system wallpaper).

**Revision note (after deeper research):** the original plan set the *system
wallpaper* on a timer and expected Monet to re-theme. Research shows that
premise is wrong — Monet draws its own wallpaper (image **or video**, Premium)
and re-themes from *that*, not from `WallpaperManager`. The one content format
the collection is missing is **video**, and that is what the launchers actually
play. So Core Shift pivots to **motion-first delivery**, with the static rotator
kept as a secondary, honest feature.

---

## 1. Why motion, and what "live wallpaper" actually means here

- **Android TV has no Live Wallpaper service.** A classic `WallpaperService`
  APK will not run — the live-wallpaper infrastructure exists on phones, not TV.
- **But launchers play looping video files as backgrounds.**
  - Monet (Premium): "Custom Wallpapers and **Video Backgrounds**."
  - Projectivy (Premium): video wallpaper, **MP4 and MOV** both work.
  - Sideload Channel Launcher 3: 1080p GIF "live wallpapers."
- This is exactly what Overflight and Aerial Views already ship (video), and it
  is the format the static Core Builds collection lacks.

So "an Overflight-like thing for Monet" resolves to: **deliver Core
Builds-branded video loops to the places Monet and Projectivy read them.**

## 2. The three delivery channels

| Channel | Launcher | Premium | Core Shift's job |
| --- | --- | --- | --- |
| **Video folder** | Monet | yes | download branded MP4 loops to `Movies/CoreBuilds`; user picks one in Monet's "your videos" once |
| **Image folder** | Monet | yes | stock `Pictures/CoreBuilds` (already done); Monet's "your images → auto-rotate" rotates + re-themes |
| **Overflight JSON feed** | Projectivy | yes | publish `motion-feed.json` (`url_img` + `url_1080p`/`url_4k`); user points Overflight at it |
| **System wallpaper** | any | free | the existing static rotator (secondary; does **not** re-theme Monet) |

## 3. Asset contract (you produce the MP4s — I can't render video here)

One tight spec so third-party-rendered loops stay on-brand:

- **Format:** MP4 (H.264), **silent**, seamless **10–30 s loop**.
- **Resolution:** 1920×1080 (default) **and** 3840×2160 (optional premium set).
- **Palette:** Core Builds §03 — cyan `#00e5ff` / signal / build-blue / ember /
  night `#0d1117` / void. Dark, OLED-friendly (70–95% dark coverage).
- **Motif:** the point-up hex + faceted core diamond, cyan→violet drift,
  particle field, aurora — original geometry, never a traced logo.
- **Naming:** `coremotion-NN-slug.mp4` (1080p) and `coremotion-NN-slug-4k.mp4`.
- **No baked UI text**, no audio track, no burn-in risk (keep the mark moving
  slowly or dimmed to avoid OLED retention).

## 4. Phase 1 build increment (defined; built after plan approval)

1. **`Wallpapers/manifest-motion.json`** — single source of truth for the motion
   line: `{ title, series, url_1080p, url_4k, thumb, resolution }`.
2. **`tools/build_motion_feed.py`** — emit an Overflight-schema
   `motion-feed.json` (`location`/`title`/`author`/`url_img`/`url_1080p`/`url_4k`)
   at a stable release URL, so Projectivy Premium users can point Overflight at
   it today.
3. **App motion path** — a `MotionDownloader` (MP4 → `Movies/CoreBuilds`, same
   https/allowlist discipline as `WallpaperDownloader`), a motion browse list,
   and a "set up in Monet" guide screen. Keep the existing static rotator.
4. **CI + release** — `core-shift-apk.yml` (path filter `shift/**`), a
   Downloader code, and video hosting on GitHub Releases (an already-allowlisted
   host).

**Deferred:** native Projectivy provider plugin (Premium, later); actual MP4
production (you); series-filter UI; Fire TV fallback.

## 5. Risk register (most → least risky)

Ranked by product risk, with the mitigation folded into the plan.

### R1 — Video wallpaper reliability on weak hardware — HIGH
Video backgrounds are the most common complaint across TV launchers: stutter,
revert-to-gray/black on low-RAM boxes (Shield Tube, ONN reported), especially
at 4K. A broken "motion" product is worse than none.
**Mitigation:** 1080p is the default recommendation (4K opt-in); short, silent,
low-motion loops; a static-image fallback path always available; honest "use
1080p on low-RAM boxes" guidance in the app.

### R2 — Premium gating (custom image + video wallpapers) — MED-HIGH
Both Monet channels that actually work are Premium. The free tier has no custom
wallpaper at all.
**Mitigation:** target Premium explicitly (confirmed); keep the free-tier
system-wallpaper rotator as best-effort, clearly labeled; do not market it as
Monet re-theming.

### R3 — Video hosting + downloader allowlist — MED
MP4s are large and must live on a host the downloader trusts
(`raw.githubusercontent.com`, `github.com`, `objects.githubusercontent.com`).
A CDN or third-party host would be silently refused.
**Mitigation:** host loops on GitHub Releases (already allowlisted); if a CDN is
ever needed, expand the allowlist deliberately rather than by accident.

### R4 — Overflight feed-schema drift — MED
Overflight sniffs JSON vs M3U and its `Media` schema can change between
versions.
**Mitigation:** pin to the documented JSON schema; test the generated feed
against Overflight 1.20 before shipping the URL; treat the feed as the
cheap-onramp, not the contract.

### R5 — System-wallpaper rotator doesn't move Monet — MED (already-known)
It is kept for other launchers and the free tier, but it will not re-theme
Monet.
**Mitigation:** label it in the UI ("works for launchers that read the system
wallpaper"); make motion the headline, not this.

### R6 — Third-party asset drift from the brand — LOW-MED
You produce the loops, so they can drift from the guide.
**Mitigation:** the §3 spec is tight and checkable; review each loop before it
enters the manifest.

### R7 — Naming/branding — LOW
"Core Shift" (app) + "Core Motion" (the video line, proposed) collide with
nothing existing; mono status voice, cyan→violet, Outfit, unchanged.

## 6. Out of scope (deferred)

Native Projectivy provider plugin; MP4 production; series-filter UI; Fire TV
crop/set fallback; motion auto-rotation inside Monet (Monet has no API — Monet
rotates images itself; videos are user-picked one at a time).

## 7. Acceptance for Phase 1

`shift/` builds (`:app:assembleDebug`); `motion-feed.json` validates against
Overflight's schema; motion download-to-`Movies/CoreBuilds` + Monet guide screen
exist alongside the static rotator; manifest declares only the permissions it
uses; no generated file hand-edited.
