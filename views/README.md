# Core Views

Aerial views of real places, built by Core Builds. Each one is a silent,
captioned loop of 20–40 seconds that drops into the same places Core Motion
already reaches: Aerial Views (screensaver and, through its Projectivy plugin,
a launcher wallpaper), Projectivy's Overflight feed, Monet's Aerial Views
bridge, and Core Shift's bundled fallback.

**No camera is required.** Season 1 is eleven views rendered from open imagery
and terrain, plus one from the NASA archive. You need a laptop, a browser
engine and ffmpeg — not a drone, not a flight, not a permit.

The research behind the format — what is legal to render or reuse, and why
Google Earth Studio is not — is
[`docs/research/core-views-aerial-2026-10.md`](../docs/research/core-views-aerial-2026-10.md).

## What is here

| File | What it is |
|---|---|
| `catalog.json` | the source of truth: every view's place, time of day, scene, camera move, captions and credits |
| `aerial-entries.json` | generated — the Aerial Views "community" feed |
| `overflight-feed.json` | generated — the Overflight / Projectivy feed |

Never hand-edit the two feed files:

```bash
python tools/build_views_feed.py           # write both feeds
python tools/build_views_feed.py --check   # verify them (CI)
python tools/build_views_feed.py --plan    # print the season, write nothing
```

A view with `"status": "planned"` publishes nothing. That is deliberate: a feed
entry whose MP4 was never rendered is a black screen in someone's living room,
not a placeholder.

## Make one

### 1. Plan it — no installs

```bash
python tools/render_views.py --all --dry-run
```

That prints, per view, the camera move, frame count, the tiles the move touches
(the download budget), and the exact ffmpeg command. It needs only Python: no
Playwright, no GPU, no network. Use it to decide whether a move is worth
rendering before you render it.

### 2. Art-direct it

```bash
python -m http.server 8000 --bind 0.0.0.0
# then http://localhost:8000/tools/views-studio/
```

The studio flies a MapLibre camera over open terrain, shows the caption track
against the timeline, and exports:

- **Export path** — the camera keyframes, plus a ready-to-paste `catalog.json`
  entry. Feed that file to the renderer with `--from`.
- **Record WebM** — a real-time preview for judging the move, not for shipping.

### 3. Render it

```bash
pip install playwright imageio-ffmpeg
python -m playwright install chromium

python tools/render_views.py --only coreviews-05-lord-howe
python tools/render_views.py --all                      # the whole season
python tools/render_views.py --from my-export.json      # a studio export
python tools/render_views.py --only coreviews-08-uluru --hdr   # HEVC 10-bit
```

Output lands in `views/render/<id>/` (git-ignored): `frames/`, `plan.json`,
`credits.txt` and `<id>.mp4`. A 30-second 1080p view is about 900-1000 frames;
expect several minutes per view on a laptop, most of it waiting on tiles.

**Picking imagery.** The renderer's default base map is OpenStreetMap raster
because it needs no key, and it is honest about being a map. That is for
judging a move. For a clip you intend to ship, point `--basemap custom
--tile-template` at imagery whose licence permits redistribution — Copernicus
Sentinel-2 and Landsat are the two that are free for commercial use with
attribution. **Esri World Imagery, Mapbox, EOX cloudless and Google are not.**
The licence table is §5 of the research document; `catalog.json` credits are
enforced by the feed builder because a season that cannot name its sources
cannot ship.

### 4. Publish it

```bash
# upload the MP4 to the `views` release lane, then in views/catalog.json:
#   "renditions": {"1080-SDR": "coreviews-05-lord-howe.mp4", ...}
#   "status": "ready"
python tools/build_views_feed.py
python tools/build_views_feed.py --check
python tests/test_views_feed.py && python tests/test_views_geo.py

# Paste into Aerial Views → Settings → Custom feeds:
#   https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/views/aerial-entries.json
# Paste into Projectivy's Overflight plugin:
#   https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/views/overflight-feed.json
```

## The asset contract

| Property | Value |
|---|---|
| Duration | 20–40 s |
| Frame rate | 30 fps |
| 1080p | H.264 High, `yuv420p`, 12–16 Mbps, `+faststart` |
| 4K (optional) | HEVC Main10, 35–50 Mbps |
| HDR (optional) | HDR10; Dolby Vision is not worth the tooling here |
| Audio | none |
| Loop | close the move, or bake a 1 s head-over-tail crossfade |
| Text | none baked in — captions come from `pointsOfInterest` |
| Safe area | keep the bottom ~25 % calm for launcher cards and overlays |
| Naming | `coreviews-NN-slug.mp4`, `-4k`, `-hdr` |
| Thumbnail | 480×270 JPEG, 1 s in |

**Size.** 20 s at 14 Mbps is about 35 MB; 20 s of 4K at 50 Mbps is about
125 MB. A twelve-view 1080p season is roughly 0.4 GB. `Motion/` is already
167 MB, so Core Views media does not go in git: it goes on the `views` release
lane, one asset per rendition, and `baseUrl` in `catalog.json` points there.

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

The renderer has the same failure mode in a different place: a camera move that
drifts a degree off the catalogue, or a tile bounding box that is 10 % too
tight, produces a clip with a grey band in it and no error anywhere. That is
why `tools/views_geo.py` is pure and `tests/test_views_geo.py` pins it.

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

## If you ever get a camera

Lane A is still the best lane, and the door is open: a drone view is a normal
catalogue entry with `"lane": "drone"`, the same contract, and no pipeline
change. The research document keeps the shooting spec (24/30 fps, 180° shutter,
two takes, 360 reprojection for one-flight-many-moves) for exactly that day.
