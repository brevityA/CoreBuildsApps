# Core Views

Aerial views of real places, built by Core Builds. Each one is a silent,
captioned loop of 20–40 seconds that drops into the same places Core Motion
already reaches: Aerial Views (screensaver and, through its Projectivy plugin,
a launcher wallpaper), Projectivy's Overflight feed, Monet's Aerial Views
bridge, and Core Shift's bundled fallback.

The research behind the format — what is legal to film, render or reuse, and
why — is [`docs/research/core-views-aerial-2026-10.md`](../docs/research/core-views-aerial-2026-10.md).

## What is here

| File | What it is |
|---|---|
| `catalog.json` | the source of truth: every view's place, time of day, scene, camera note, captions and credits |
| `aerial-entries.json` | generated — the Aerial Views "community" feed |
| `overflight-feed.json` | generated — the Overflight / Projectivy feed |

Never hand-edit the two feed files. They are projections of the catalogue:

```bash
python tools/build_views_feed.py           # write both feeds
python tools/build_views_feed.py --check   # verify them (CI)
python tools/build_views_feed.py --plan    # print the season, write nothing
```

A view with `"status": "planned"` publishes nothing. That is deliberate: a feed
entry whose MP4 was never rendered is a black screen in someone's living room,
not a placeholder.

## Make one

### 1. Art-direct it

Open `tools/views-studio/index.html` from a local server:

```bash
python -m http.server 8000 --bind 0.0.0.0
# then http://localhost:8000/tools/views-studio/
```

The studio flies a MapLibre camera over open terrain, shows the caption track
against the timeline, and exports a camera path plus a browser-recorded WebM.
It is a preview, not the master: the WebM is for judging the move, the camera
path is what you render.

### 2. Shoot it, or render it

Three lanes are in scope; the licence table in the research document decides
which one a place is allowed to use.

**Shoot it (drone).** 4K or better, 24 or 30 fps, cinema profile, low
contrast, no sharpening, ND filter for a 180° shutter. Fly the move twice —
once to find it, once to keep. For a place you can only visit once, shoot 360
and reproject: one flight, five camera moves.

**Render it (open geodata).** Imagery from a source that permits commercial
redistribution (Copernicus Sentinel-2, Landsat, NAIP), terrain from
Copernicus DEM or Mapterhorn, camera flown in MapLibre, frames piped to ffmpeg.
Keep the credits the licence asks for in `catalog.json` as you go — they are
required to publish.

**Archive it (public domain).** NASA/ESA/NOAA material, graded and stabilised.
Credit the mission.

**Do not use Google Earth Studio.** Its terms forbid exactly this: a new
product based on Google Earth, redistributed to users, with a baked-in
attribution watermark. The research document explains it in full.

### 3. Deliver it

| Property | Value |
|---|---|
| Duration | 20–40 s |
| Frame rate | 30 fps (24 for cinematic drone moves) |
| 1080p | H.264 High, `yuv420p`, 12–16 Mbps, `+faststart` |
| 4K (optional) | HEVC Main10, 35–50 Mbps |
| HDR (optional) | HDR10; Dolby Vision is not worth the tooling here |
| Audio | none |
| Loop | close the move, or bake a 1 s head-over-tail crossfade |
| Text | none baked in — captions come from `pointsOfInterest` |
| Safe area | keep the bottom ~25 % calm for launcher cards and overlays |
| Naming | `coreviews-NN-slug.mp4`, `-4k`, `-hdr` |
| Thumbnail | 480×270 JPEG, 1 s in |

A worked encode, 20 seconds, 1080p:

```bash
ffmpeg -framerate 30 -i frames/%05d.png \
  -c:v libx264 -profile:v high -preset slow -crf 18 \
  -maxrate 16M -bufsize 32M -pix_fmt yuv420p \
  -movflags +faststart -an coreviews-01-harbour-dawn.mp4
```

4K HDR10, from a 10-bit source:

```bash
ffmpeg -framerate 30 -i frames/%05d.png \
  -c:v libx265 -preset slow -crf 20 -tag:v hvc1 \
  -x265-params "hdr-opt=1:repeat-headers=1:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc" \
  -pix_fmt yuv420p10le -movflags +faststart -an coreviews-01-harbour-dawn-4k-hdr.mp4
```

**Size.** 20 s at 14 Mbps is about 35 MB; 20 s of 4K at 50 Mbps is about
125 MB. A twelve-view 1080p season is roughly 0.4 GB. `Motion/` is already
167 MB, so Core Views media does not go in git. It goes on the `views`
release lane, one asset per rendition, and `baseUrl` in `catalog.json` points
there.

### 4. Publish it

```bash
# upload the renditions to the `views` release, then:
# 1. set the filenames and "status": "ready" in views/catalog.json
# 2. regenerate
python tools/build_views_feed.py
python tools/build_views_feed.py --check
python tests/test_views_feed.py

# Paste into Aerial Views → Settings → Custom feeds:
#   https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/views/aerial-entries.json
# Paste into Projectivy's Overflight plugin:
#   https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/views/overflight-feed.json
```

## Why the metadata matters as much as the picture

Aerial Views reads more than a URL list. It filters by `timeOfDay` and `scene`,
it shows `accessibilityLabel` on screen, and it renders `pointsOfInterest` as
timed captions. An entry that omits them is still playable — it just loses the
part that makes it a *view* rather than a video.

Two traps this repo has already set for itself:

- `Motion/aerial-entries.json` marks all 19 entries `timeOfDay: "night"`
  because the content is dark-first. That is correct there and wrong here:
  a Core View of a beach at noon filed as night disappears from a user's day
  playlist.
- Aerial Views parses the feed with `ignoreUnknownKeys = true`. A misspelled
  key is not an error, it is a missing rendition. The builder rejects unknown
  keys for exactly that reason.

## Credits

Every view must name its sources in `catalog.json`; the builder refuses to
publish one that does not. The strings the open-data licences require:

| Source | Credit |
|---|---|
| Copernicus Sentinel | `Contains modified Copernicus Sentinel data <year>` |
| Copernicus DEM | `© DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018, provided under COPERNICUS by the EU and ESA` |
| Mapterhorn | `© Mapterhorn` linking to <https://mapterhorn.com/attribution> |
| OpenStreetMap | `© OpenStreetMap contributors` |
| NASA | credit the mission; NASA media is generally public domain, check the item |
| Own footage | `© Core Builds <year>` |

Where a licence requires the credit to be *visible*, it goes in the frame, not
only in the feed — Overflight and Aerial Views only show the
`accessibilityLabel`, which is why the series name and place ride with it.

Provenance lands in [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md) when
a season ships.
