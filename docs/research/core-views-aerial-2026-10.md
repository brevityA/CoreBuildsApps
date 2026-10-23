# Core Views — aerial views you build yourself

**Status:** research + first build increment · **Date:** 2026-10-23 · **Owner:** brevityA

You said you like building your own aerial views and want to call them **Core
Views**. This document answers three questions: what the aerial-screensaver
market actually is, what you can legally put in a Core View, and which of the
six or seven things "we could do" is worth building first.

Everything here is either read out of this repo or from the primary source
named next to it. Where a claim is a judgement call it says so.

---

## 1. The short version

- **The rails already exist.** Core Builds already publishes two feed formats
  that Aerial Views, Projectivy (via Overflight) and Monet consume. Core Views
  does not need a new player to reach a TV on day one.
- **The current feed is an abstract-motion feed wearing an aerial hat.** Every
  entry in `Motion/aerial-entries.json` is `type: "aerial"`,
  `timeOfDay: "night"`, with no scene, no place and no captions. Aerial Views
  will happily show an aerial *catalogue* only if the entries carry the fields
  it reads — `scene`, `timeOfDay`, `pointsOfInterest` — and ours carry none.
- **The legal wall is real and it is not where people expect.** Google Earth
  Studio is the obvious tool and it is the one you cannot ship. Open geodata
  (Copernicus, Landsat, USGS, public-domain NASA) is the lane that is both
  free and redistributable, with attribution.
- **Best first move:** make "Core Views" a *content contract plus a publishing
  pipeline*, not an app. Write the spec, render a first season, publish on the
  existing feeds. Add the app only when the catalogue is big enough that
  browsing it inside Aerial Views' settings list is the pain.
- **Decided 2026-10-23: there is no drone.** Core Views is built from lanes B
  (open-data render) and F (archive), which means the renderer is not a
  nice-to-have — it is the product, and season 1 is a laptop job.

---

## 2. What the market actually is

### 2.1 Apple set the format, and the format is metadata-rich

Apple's tvOS 26 resource bundle ships roughly 140 aerial clips. Each entry in
`entries.json` carries `timeOfDay`, `scene`, `categories`, `pointsOfInterest`,
`previewImage` and per-quality tiers including `url-4K-SDR-240FPS`
([BirknerAlex/plasma6-wallpapers-aerial PR #11](https://github.com/BirknerAlex/plasma6-wallpapers-aerial/pull/11)).
Apple's own UI exposes four categories — Cityscape, Earth, Landscape,
Underwater — and since tvOS 26 lets you deselect individual clips
([Apple Support](https://support.apple.com/guide/tv/set-up-screen-savers-atvbfa799b87/tvos),
[9to5Mac](https://9to5mac.com/2025/09/19/apple-tv-4k-gets-the-screen-saver-feature-ive-always-wanted-in-tvos-26/)).

Two things follow. First, an aerial *product* is a catalogue with place,
time-of-day, scene and captions — not a folder of files. Second, Apple's clips
go up to 4K and, in the newest bundles, carry a 240 fps tier: high-frame-rate
capture played back slowly, which is why they feel calm rather than busy.

### 2.2 The Android TV rail is Aerial Views, and it is generous

[Aerial Views](https://github.com/theothernt/AerialViews) (Neil Turner, GPL-3.0)
is the de-facto Android TV aerial screensaver: 250+ videos, 4K Dolby Vision,
burn-in-aware overlay alternation, refresh-rate switching for 24/30/50/60 fps,
overlays for location/date/clock/now-playing, a local message API, and — the
part that matters here — **custom remote feeds**.

Its custom-feed JSON is read by a `@Serializable` class with
`ignoreUnknownKeys = true`. The exact contract, from
[`AbstractVideo.kt`](https://github.com/theothernt/AerialViews/blob/master/app/src/main/java/com/neilturner/aerialviews/models/videos/AbstractVideo.kt):

| Key | Type | Notes |
|---|---|---|
| `url-1080-H264` | string | baseline compatibility |
| `url-1080-SDR` | string | default quality |
| `url-1080-HDR` | string | falls back to 1080 SDR if blank/absent |
| `url-4K-SDR` | string | |
| `url-4K-HDR` | string | falls back to 4K SDR if blank/absent |
| `url-audio` | string | DASH-style separate audio; merged at playback |
| `accessibilityLabel` | string | the on-screen description |
| `pointsOfInterest` | `{seconds: text}` | timed captions |
| `timeOfDay` | `day` / `night` / `sunset` / `sunrise` | anything else is `UNKNOWN` |
| `scene` | `nature` / `beach` / `countryside` / `waterfall` / `city` / `sea` / `space` / `patterns` / `fire` | anything else is `UNKNOWN` |

Two more facts worth building on:

- **Photos are a separate feed shape** (`url-1080`, `url-4K`, `title`) — so a
  stills companion can ride the same rail, but with no captions or time bucket.
- **Aerial Views is also a Projectivy wallpaper provider** ("all streaming
  sources can be used as wallpaper videos", its release notes). One custom
  feed can therefore reach both the screensaver slot *and* a launcher
  wallpaper, without us shipping a provider of our own.

### 2.3 The macOS side is a content market, not just an app

[Aerial 4](https://aerialscreensaver.github.io/) has community "Expansions"
with per-pack licences, playlists exportable as JSON, and a "My Videos" folder.
[JohnCoates/Aerial](https://github.com/JohnCoates/Aerial) documents the custom
video JSON shape, including **points of interest as `{time: description}`** and
a day/night flag. The [AerialCommunity](https://github.com/AerialScreensaver/AerialCommunity)
submission rules are effectively a content brief: no logos, descriptive
captions (not marketing), a travelling shot from a drone at a not-too-rapid
pace, 45 s–3 min, at least 1080p H.264 plus one 4K format, and footage that was
captured legally.

That brief is a good starting point for the Core Views spec, adjusted for a
screensaver loop rather than a 2-minute film.

### 2.4 Where the gap is

Everything above is built around *somebody else's footage*. The Core Builds
suite has no aerial line at all: Core Motion is procedural, the Deep Space and
Cinema sets are abstract, and the icon pack's wallpapers are stills. "Core
Views" is the missing genre — real places, captioned, on the same rails.

---

## 3. What the suite already publishes

| Rail | File | Consumer | Entry count | What it carries today |
|---|---|---|---:|---|
| Overflight | `Motion/overflight-feed.json` | Projectivy Premium | 19 | `location`, `title`, `author`, `url_img`, `url_1080p`, `url_4k` |
| Aerial Views | `Motion/aerial-entries.json` | Aerial Views → screensaver / Monet via Projectivy | 19 | `id`, `accessibilityLabel`, `type: aerial`, `timeOfDay: night`, `url-1080-SDR` |
| Core Motion plugin | `Motion/live-feed.json` | Projectivy Premium (AIDL provider) | 48 | same shape as Overflight |
| Core Shift | bundled `live-feed.json` + live cache + `Movies/CoreBuilds` | system DreamService | fallback | loops, silent, shuffled |

Two consequences that are already visible in the code:

1. `tools/build_aerial_feed.py` hard-codes `TIME_OF_DAY = "night"` for the whole
   catalogue, with the correct reasoning that a dark-first set gets skipped by
   Aerial Views' day filter. A Core View is the opposite case: it has a real
   time of day, and forcing it to `night` would be a lie the playlist filter
   then acts on.
2. The Overflight schema can only carry `location` / `title` / `author`. It has
   nowhere to put a caption track, a scene or a place. The Aerial Views feed
   does. So **the Aerial Views feed is the richer target**, and Overflight is a
   lossy projection of it — the reverse of today's direction of travel, where
   `live-feed.json` is the source of truth.

---

## 4. What "aerial view" can actually mean — six production lanes

Scored on quality, legality, cost and whether it can be repeated for episode 20.

| Lane | What it is | Quality | Legal | Cost | Automatable | Verdict |
|---|---|---|---|---|---|---|
| **A. Shoot it** | Drone (or 360 + reprojection) over a real place | Highest | Clear if flown legally; site permissions matter | Gear + time | No | **Out of scope — no hardware.** Keep the door open |
| **B. Open-geodata render** | Sentinel-2 / Landsat / NAIP imagery draped on Copernicus/Mapterhorn terrain, camera flown in MapLibre, frames to ffmpeg | Good for landscape, poor for skylines | Free incl. commercial, with attribution | Compute only | **Yes** | The repeatable engine |
| **C. Procedural** | Shaders (what Core Motion already does) | High, but not a place | None | Compute | Yes | Filler, not a view |
| **D. Licence or partner** | Buy packs (Jetson Creative et al.) or commission | Highest | Per-licence | Money | No | Fast quality, no moat |
| **E. Google Earth Studio** | Keyframed Google Earth fly-throughs | Very high | **Barred for this use** | Free | No | Do not build on it |
| **F. Public-domain archive** | NASA/ESA/NOAA/USGS footage, ISS timelapses, aerial surveys | Varies, some excellent | Public domain / open | Free | Partly | Cheap Earth category |
| **G. Generative video** | Text/image-to-video | Pretty, geographically fake | Output terms vary by vendor | Credits | Yes | Only if labelled "inspired" |

### A. Shoot it — out of scope for now

Not available: there is no drone. This section stays because it is the one lane
that produces something nobody can copy, and the spec above (24/30 fps, 180°
shutter, two takes) is what matters if a clip is ever bought, commissioned or
borrowed. The 360-reprojection trick is still the cheapest route in if the
situation changes.

The rest of this document assumes lane A does not exist.

One 20-second move needs a recce, a flight window, good light and a second
take. This is the only lane that produces something no one else can copy.

Practical rules for Australia (CASA, the regulator that applies to you in
Sydney):

- Recreational flight needs **no registration and no licence**; fly for fun.
  Any paid work — including a sponsorship or an ad — needs drone registration
  and **RPA operator accreditation at every weight, sub-250 g included**
  ([CASA](https://www.casa.gov.au/drones/drone-rules/drone-safety-rules)).
- Standard operating conditions apply regardless: **120 m AGL ceiling, visual
  line of sight, 30 m from uninvolved people**, and 5.5 km from a controlled
  aerodrome for anything over 250 g. Check a CASA-verified app before each
  flight.
- Many of the best NSW views are inside national parks. NSW NPWS treats drone
  use as requiring permission, so a "Royal National Park" or "Blue Mountains"
  view needs a permit, not just a battery.
- Council land and harbour foreshore rules vary; the Opera House and Harbour
  Bridge precinct is heavily restricted.

**The cheap trick worth knowing:** shoot **360** (Insta360 X4/X5, 8K), then
reproject to flat 16:9 with `ffmpeg -vf v360=...` or Insta360 Studio. One
flight yields five or six different camera moves, each a separate Core View,
all perfectly stabilised and all from the same 20 minutes of airtime. This is
the single biggest productivity multiplier in the whole document.

### B. Open-geodata render (the engine)

This is a solved pipeline, not a research project:

```
open imagery tiles  +  open DEM tiles  +  camera keyframes
        ↓ MapLibre GL JS in headless Chrome/Chromium
    PNG/JPEG frame sequence  →  ffmpeg (x264/x265)  →  MP4
```

- MapLibre does 3D terrain (`raster-dem` + `setTerrain`) and `sky`
  ([MapLibre 3D terrain example](https://maplibre.org/maplibre-gl-js/docs/examples/3d-terrain/)).
- Headless capture is a known pattern: two independent implementations drive
  MapLibre through Puppeteer and pipe frames into ffmpeg
  ([rawko-worker-flyover](https://github.com/bvdaniel/rawko-worker-flyover),
  [route flyover write-up](https://blog.partyonbici.com/posts/route-video-generation)).
- A ready-made control exists for camera presets — orbit, figure-8, spiral,
  waypoints, terrain-aware
  ([maplibre-gl-video-export](https://github.com/bjperson/maplibre-gl-video-export)).

The honest limitation: **Sentinel-2 is 10 m**, Landsat is 30 m, NAIP is 0.6 m
but US-only. At 10 m you get convincing *landscape* — ridges, rivers, coasts,
deserts, islands — and mush at city scale. So lane B owns the Blue Mountains,
Lord Howe, Uluru, the Kimberley and the Bungle Bungles; lane A owns the Opera
House.

### C. Procedural

Already shipped: 48 seamless loops in `Motion/live/`, 16 prequel scenes from a
GLSL engine. A procedural view is not a *place*, so it belongs in Core Motion,
not Core Views. The one crossover worth considering is a stylised
"terrain-from-noise" flyover in the brand palette — a Core Motion series, not a
Core View.

### D. Licence or partner

Jetson Creative and Robin Fourcade already supply Aerial Views and Aerial.
Buying or commission-sharing is the fastest path to Apple-grade footage, and
the slowest path to anything distinct. If you do it, get **screensaver/app
redistribution** rights in writing — a stock "video project" licence usually is
not that.

### E. Google Earth Studio — why it is out

Google is unusually explicit, and the restriction is on *redistribution*, not
just monetisation:

- "Google Earth content **may not** be used for any commercial or promotional
  purposes" ([Google Brand Resource Center — Geo guidelines](https://about.google/brand-resource-center/products-and-services/geo-guidelines/)).
- The Earth Additional Terms forbid you to "redistribute or sell any part of
  Google Earth or **create a new product or service based on Google Earth**"
  ([Earth Additional Terms](https://www.google.com/help/terms_maps-earth/)).
- Earth Studio renders bake a **mandatory attribution watermark into every
  frame** ([Earth Studio attribution](https://earth.google.com/studio/docs/attribution/)).

A screensaver catalogue is a product based on Google Earth, distributed to
users. That is the exact thing the terms name. Do not build the pipeline on it,
and do not "just render it and see".

### F. Public-domain archive

NASA's ISS timelapses, ESA open imagery and USGS aerial surveys are public
domain or open-licensed, and they fill Apple's "Earth" category authentically.
They need grading, stabilising and captioning to feel like Core Builds, but the
raw material is free and legitimate.

### G. Generative video

A generated coastline looks like a coastline and is not one. If Core Views is
*somewhere*, generative output breaks the promise on the first clip. It could
work as a separate, clearly-labelled line ("Core Drift — imagined places"), and
vendor terms for commercial redistribution of output need reading per vendor.
Not a first move.

---

## 5. The licensing wall, in one table

What you may bake into a video that ships to users:

| Source | Commercial redistribution | Obligation | Use it? |
|---|---|---|---|
| **Copernicus Sentinel-2** | Yes, free and open | "Contains modified Copernicus Sentinel data 2026" | **Yes** |
| **Landsat (USGS)** | Yes, public domain | Courtesy credit | **Yes** |
| **NAIP (US)** | Yes, public domain | None required | **Yes** (US places only) |
| **Copernicus DEM GLO-30/90** | Yes | "© DLR e.V. / Airbus DS, provided under COPERNICUS" | **Yes** |
| **Mapterhorn terrain** | Yes (per-source CC BY / PD / Etalab) | "© Mapterhorn" linked to its attribution page | **Yes** |
| **NASA / ESA / NOAA archive** | Mostly yes | Per-item; credit the mission | **Yes**, check per clip |
| **OpenStreetMap (vector/road context)** | Yes | "© OpenStreetMap contributors" (ODbL) | Yes, with credit |
| **OpenTopoMap** | Share-alike on the map image | CC BY-SA | Avoid for shipped video |
| **EOX Sentinel-2 cloudless** | No — CC BY-NC-SA without a paid licence | — | No |
| **Esri World Imagery** | No — Esri Master License Agreement; no tile redistribution | — | No |
| **Google Earth / Earth Studio** | No | — | **No** |
| **Mapbox tiles** | Per contract; not a free render source | — | No, unless contracted |
| **Apple aerials** | No — Apple's footage | — | No (do not rehost) |
| **Stock (Artgrid, Pexels, etc.)** | Usually yes for video projects | Check "screensaver/app redistribution" explicitly | Maybe |

The repo already has the place to record this: `THIRD_PARTY_NOTICES.md`.
Every Core View should carry a machine-readable `credits` array, and the
publisher should refuse an entry with no credits. That is enforced in the
generator added with this document.

---

## 6. Format spec for a Core View

This is the contract the pipeline enforces. It deliberately reuses the numbers
the suite already uses for Core Motion so a TV that plays one plays the other.

| Property | Value | Why |
|---|---|---|
| Duration | 20–40 s | long enough to settle, short enough to loop in a screensaver rotation |
| Frame rate | 30 fps (24 for cinematic drone moves) | Aerial Views switches refresh rate per clip; 30 is the safe default |
| 1080p delivery | H.264 High, `yuv420p`, 12–16 Mbps, `+faststart` | plays on every Fire TV / Android TV generation |
| 4K delivery | HEVC Main10, 35–50 Mbps | HDR10 is the practical HDR; Dolby Vision needs `dovi_tool` and buys little here |
| Audio | none | a dream must not steal now-playing; `url-audio` exists but we do not need it |
| Loop | seamless if the move closes; otherwise a 1 s head-over-tail crossfade | Core Shift's DreamService uses `REPEAT_MODE_ALL`, so an unclosed loop is a visible jump |
| Text | none baked in | captions come from `pointsOfInterest`; the bottom ~25 % stays calm for launcher cards and overlays |
| Palette | Core Builds §03, dark-friendly | `docs/BRAND-GUIDE.md` |
| Naming | `coreviews-NN-slug[-4k][-hdr].mp4` | stable, sortable, and the filename stem becomes the feed `id` |
| Thumbnail | 480×270 JPEG, 1 s in | matches `Motion/live/thumbs/` |

**Size arithmetic.** 20 s at 14 Mbps ≈ 35 MB; 40 s ≈ 70 MB; 4K 20 s at 50 Mbps
≈ 125 MB. A twelve-view 1080p first season is around 0.4 GB. That is too much
for git — `Motion/` is already 167 MB — so Core Views media belongs in a
GitHub **Release asset lane** (`views`), exactly like the prequel render lane.
GitHub's per-asset ceiling is 2 GB, so a 4K HDR clip fits with room to spare.

**The silent-typo trap.** Aerial Views parses the feed with
`ignoreUnknownKeys = true`. A misspelled `url-4K-SDR` does not error; the entry
just silently loses its 4K rendition. The generator is therefore strict about
key names and the test suite pins them, because the consumer cannot.

---

## 7. Product shapes, costed

| Shape | What ships | Effort | Reaches users via | Main risk |
|---|---|---|---|---|
| **1. Content line + feeds** | `views/` catalogue, renderer, first season of MP4s, Aerial Views + Overflight feeds | one evening to render season 1, once Playwright is installed | Aerial Views, Projectivy, Monet, Core Shift | imagery licences, not hardware |
| **2. Studio** | browser art-director that flies a camera, previews, exports camera path + WebM | built | you | none to users |
| **3. Extend Core Shift** | second feed, Views tab, caption overlay, offline download | ~1 week | Core Shift's own users (8.8 k Downloader code) | more surface in an app that already has an updater, dream and downloader |
| **4. New app** | `tv.corebuilds.views`: DreamService + Projectivy provider + browse-by-place UI + offline packs | 2–4 weeks + release lane, updater, Play/Amazon listing | new install | duplicates Aerial Views unless it adds place-browsing and offline packs |

**Recommendation: 1 → 2 → 3, and only then consider 4.**

The reason is the suite's own history. Core Motion exists as a plugin because
Projectivy needed a provider; Core Shift exists because Monet had no provider
API and a DreamService was the honest route. Neither shipped an app where a
format would do. Core Views has a format that three players already read. An
app is justified only when its *interface* is the value — browsing 60 places by
region and time of day, downloading a season for a road trip, captions designed
for the suite — not because we want an icon in the launcher.

---

## 8. First season — revised for a camera-free build

The first proposal leaned on drone work for its best shots. Without a drone
those are gone, and the season is rebuilt around what lane B can actually
deliver. `R` = open-data render (lane B), `A` = archive (lane F).

| # | View | Where | Lane | Time | Scene | Move |
|---|---|---|---|---|---|---|
| 01 | Three Sisters | Blue Mountains, NSW | R | sunrise | countryside | ridge-follow through morning cloud |
| 02 | Hawkesbury | Brooklyn, NSW | R | day | sea | glide up the river mouth |
| 03 | Wattamolla | Royal National Park, NSW | R | day | waterfall | descend the cliff line |
| 04 | Hyams Beach | Jervis Bay, NSW | R | day | beach | glide the white sand |
| 05 | Lord Howe | Lord Howe Island, NSW | R | day | sea | orbit the lagoon and the twin peaks |
| 06 | Wineglass Bay | Freycinet, TAS | R | sunrise | beach | reveal over the saddle |
| 07 | Twelve Apostles | Port Campbell, VIC | R | sunset | sea | track the limestone stacks |
| 08 | Uluru | NT | R | sunset | countryside | slow orbit at last light |
| 09 | Whitehaven | Whitsundays, QLD | R | day | beach | top-down descend onto Hill Inlet |
| 10 | Kati Thanda | Lake Eyre, SA | R | day | patterns | glide over the salt pan |
| 11 | Ningaloo | Coral Bay, WA | R | day | sea | track the reef edge |
| 12 | Terminator | ISS archive | A | night | space | archive timelapse |

What changed and why:

- **Sydney Harbour, Barrenjoey and the Opera House are out.** At Sentinel's
  10 m, a city skyline is mush; a render of the harbour would have looked like
  a mistake. Losing them is the honest cost of not having a camera, and they
  are the first three views to shoot if that ever changes.
- **The set moved to landforms and coasts**, which is exactly where 10 m
  imagery and 30 m terrain are strongest: ridgelines, river mouths, reef
  edges, salt pans, sea stacks.
- **Kati Thanda and Ningaloo were added.** Both are places a drone would
  struggle with anyway (permit country, remote, marine park), and both are
  spectacular as imagery rather than as camera work.
- **Australia-first stays.** It is no longer about travel: it is about
  licensing. Sentinel and the DEMs are global, but knowing a place means you
  can tell a bad render from a bad place.

Every view has a real coordinate, a time bucket, a scene and at least two
captions, which is enough to exercise the whole feed contract on the first
render.

## 9. What is built

| File | What it does |
|---|---|
| `docs/research/core-views-aerial-2026-10.md` | this document |
| `views/catalog.json` | season 1: 12 views with coordinates, lane, time of day, scene, camera move, captions and per-source credits |
| `tools/views_geo.py` | camera moves, altitude→zoom, tile census — pure Python, no browser |
| `tools/render_views.py` | **the renderer**: catalogue → keyframes → headless MapLibre → PNG frames → ffmpeg. `--dry-run` plans a whole season with no browser installed |
| `tools/views-studio/index.html` | zero-install art director: fly a move, see the captions land, export keyframes and a WebM preview |
| `tools/build_views_feed.py` | validates the catalogue, projects it to the Aerial Views and Overflight feeds, `--check` for CI |
| `tests/test_views_feed.py` | 24 assertions on the keys the Android client silently accepts |
| `tests/test_views_geo.py` | 27 assertions on camera paths, tile coverage and encode plans |

Two design decisions worth keeping:

1. **The camera maths lives in Python, once.** The browser harness is handed
   finished keyframes and only displays them, so the studio preview and the
   render cannot disagree, and a render is reproducible from the catalogue.
2. **`--dry-run` is a first-class mode.** It prints the frame count, the tiles
   the move touches and the exact encode command, with no Playwright, no GPU
   and no network. It is the check CI runs, and it is what makes a render
   reviewable before it costs an hour.

Nothing publishes until a view is rendered and flipped to `ready`. A feed of
dead URLs is worse than no feed.

---

## 10. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| **Licence drift** — a source's terms change, or a contributor ships an unlicensed clip | High | machine-readable `credits` per view, publisher refuses entries without them, `THIRD_PARTY_NOTICES.md` per season |
| **Rendering the unauthorised thing** — someone reaches for Earth Studio because it looks best | High | this document, plus a note in `views/README.md` next to the render instructions |
| **Silent feed typos** | Medium | generator validates key names; `ignoreUnknownKeys` means nothing else will |
| **All-night bucket** carried over from `build_aerial_feed.py` | Medium | Core Views computes `timeOfDay` per view; never inherits the constant |
| **TV decode limits** — 4K HDR on a Fire TV Stick reverts to black | Medium | 1080p SDR is the default rendition; 4K/HDR opt-in per view; HDR keys fall back to SDR in the player |
| **Bandwidth** — raw.githubusercontent is not a CDN | Medium | release-asset lane, 1080p default, thumbnails for every view |
| **Git bloat** | Medium | media in releases, never in git |
| **Name collision** — "Core Views" vs "Aerial Views" in search and in support threads | Low | always "Core Views by Core Builds", never "Core Aerial"; the feed's `accessibilityLabel` carries the full attribution |
| **A render that looks like a map** — using OSM raster as the base because it needs no key | High for a shipped clip | the default basemap is for judging a move only; shipping imagery must be imagery the licence allows, and the credits array makes that explicit |
| **Over-zoomed imagery** — asking for z17 from a z14 source gives a soft, blocky frame | Medium | tile census caps at z15; pick altitude so the frame sits at the source's native zoom |
| **The season looks like screenshots** — terrain renders are static unless the camera does the work | Medium | every move in `views_geo.MOVES` is a continuous camera path; captions carry the story; short loops |
| **Attribution lost in playback** | Medium | Overflight and Aerial Views show `accessibilityLabel` on screen; where a licence requires visible credit, the credit goes in the frame, not just the feed |

---

## 11. Open questions for you

1. ~~Lane mix.~~ Answered 2026-10-23: no hardware, so lane B + archive. The
   renderer is now the critical path, not the studio.
2. **Geography.** Australia-first (as built), or global? Lane B has no travel
   cost, so "global" is a licensing and naming question rather than a
   logistics one. A global season 2 is the natural expansion.
3. **App or format.** Content line first (my recommendation), or do you want
   the `tv.corebuilds.views` app scaffolded now?
4. **Captions.** Place names only, or a one-line story per stop ("Fort Denison
   — the tidal island that became a prison")? The second is more work and is
   the thing that makes a Core View feel authored.

---

## 12. Sources

Primary:

- Aerial Views contract — [`AbstractVideo.kt`](https://github.com/theothernt/AerialViews/blob/master/app/src/main/java/com/neilturner/aerialviews/models/videos/AbstractVideo.kt), [`Comm1Video.kt`](https://github.com/theothernt/AerialViews/blob/master/app/src/main/java/com/neilturner/aerialviews/models/videos/Comm1Video.kt), [`TimeOfDay.kt`](https://github.com/theothernt/AerialViews/blob/master/app/src/main/java/com/neilturner/aerialviews/models/enums/TimeOfDay.kt), [`SceneType.kt`](https://github.com/theothernt/AerialViews/blob/master/app/src/main/java/com/neilturner/aerialviews/models/enums/SceneType.kt), README "Custom feeds".
- Aerial Views releases and PRs — HDR quality keys ([#393](https://github.com/theothernt/AerialViews/pull/393)), `url-audio` ([#392](https://github.com/theothernt/AerialViews/pull/392)).
- Apple catalogue structure — [plasma6 PR #11](https://github.com/BirknerAlex/plasma6-wallpapers-aerial/pull/11); [Apple Support: screen savers](https://support.apple.com/guide/tv/set-up-screen-savers-atvbfa799b87/tvos).
- Aerial (macOS) — [custom videos](https://github.com/JohnCoates/Aerial/blob/master/Documentation/CustomVideos.md), [community submission rules](https://github.com/AerialScreensaver/AerialCommunity).
- Google restrictions — [Geo guidelines](https://about.google/brand-resource-center/products-and-services/geo-guidelines/), [Earth Additional Terms](https://www.google.com/help/terms_maps-earth/), [Earth Studio attribution](https://earth.google.com/studio/docs/attribution/).
- Open data licences — [Copernicus Sentinel licence](https://cds.climate.copernicus.eu/licences/ec-sentinel), [Copernicus DEM](https://dataspace.copernicus.eu/explore-data/data-collections/copernicus-contributing-missions/collections-description/COP-DEM), [Mapterhorn attribution](https://mapterhorn.com/attribution), [MapLibre open data list](https://makewithmaplibre.com/data/).
- Drone rules — [CASA safety rules](https://www.casa.gov.au/drones/drone-rules/drone-safety-rules), [weight categories](https://www.casa.gov.au/drones/operator-accreditation-certificate/drone-weight-categories-and-requirements).

Repo:

- `docs/PLUGIN_RESEARCH.md` — Overflight AIDL and the `Media` schema.
- `docs/MONET_LAUNCHER.md` — Monet's Aerial Views bridge.
- `docs/SPACE_LIVE_WALLPAPERS.md` — the delivery matrix.
- `docs/CORE_SHIFT_PLAN.md` — why TV has no `WallpaperService`.
- `docs/CORE_MOTION_PREQUELS.md` — the existing render-to-release-lane pattern.
- `tools/build_aerial_feed.py` — the projection this document generalises.
