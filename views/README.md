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

**Picking imagery.** Imagery is a licence decision with a flag attached, not a
URL:

| `--basemap` | Source | May it ship? |
|---|---|---|
| `osm` (default) | OpenStreetMap raster | **No** — judging a move only |
| `terrain` | Mapterhorn hillshade | Yes, elevation only |
| `gibs` | NASA GIBS true colour | Yes, but ~300 m/px: orbit views only |
| `sentinel` | Copernicus Sentinel-2 | Yes — needs your tile template |
| `local` | your own titiler/pmtiles | Yes |
| `custom` | anything else | Never via `--for-publish` |

`--for-publish` refuses a source that may not be redistributed, refuses a view
whose `credits` do not name the source the licence requires, and refuses a move
that wants more zoom than the imagery has. Those are the three ways a good
preview becomes a bad surprise.

Get a Sentinel template one of three ways — bring-your-own tiler, Sentinel Hub
/ Copernicus Data Space, or Planetary Computer — in §5a of the research
document. `export COREVIEWS_SENTINEL_TEMPLATE=...` keeps it out of your shell
history:

```bash
export COREVIEWS_SENTINEL_TEMPLATE="http://127.0.0.1:8080/tiles/{z}/{x}/{y}.png"
python tools/render_views.py --all --basemap sentinel --for-publish
```

**Esri World Imagery, Mapbox, EOX cloudless and Google are not options** at any
price short of a contract.

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

## Examples

Three real artifacts, so the shapes are not a description.

### A shot card per view

[`docs/views-storyboards/`](../docs/views-storyboards/README.md) holds one
`<id>.svg` per view: the camera track from above, altitude, bearing and pitch
over time, and where the captions land. Generated by
`tools/views_storyboard.py` from the same camera paths the renderer flies, so a
card cannot describe a move the render will not make.

They are **not frames**. Nothing in them invents imagery of a real place —
that is the one thing a preview of Core Views must not do.

```bash
python tools/views_storyboard.py --all            # redraw
python tools/views_storyboard.py --all --check    # CI
```

### A worked example of a finished view

[`views/examples/ready-view.json`](examples/ready-view.json) is what one view
looks like after it has been rendered: the catalogue entry with renditions
filled and `status: ready`, plus the exact Aerial Views and Overflight rows the
builders derive from it. Season 1 is entirely `planned`, so this is the only
place the finished shape is visible.

```bash
python tools/build_views_feed.py --examples   # regenerate; --check holds it
```

### A plan, with no browser installed

```console
$ python tools/render_views.py --only coreviews-05-lord-howe --dry-run
coreviews-05-lord-howe
  Lord Howe — Lord Howe Island, New South Wales
  move orbit · 34s · 30 fps · 1021 frames
  imagery OpenStreetMap raster · NOT redistributable · z≤15
  tiles 40 ({'15': 40}) ≈ 1.0 MB
  encode: ffmpeg -y -framerate 30 -i frames/%05d.png -movflags +faststart -an \
          -c:v libx264 -profile:v high -preset slow -crf 18 -maxrate 16M \
          -bufsize 32M -pix_fmt yuv420p coreviews-05-lord-howe.mp4
```

Swap in a source you may ship, and the gate passes:

```console
$ export COREVIEWS_SENTINEL_TEMPLATE="http://127.0.0.1:8080/tiles/{z}/{x}/{y}.png"
$ python tools/render_views.py --only coreviews-05-lord-howe --dry-run \
    --basemap sentinel --for-publish
  imagery Copernicus Sentinel-2 · redistributable · z≤15
```

Ask for the same render from a source that is too coarse, and it refuses:

```console
$ python tools/render_views.py --only coreviews-08-uluru --basemap gibs \
    --date 2026-09-01 --for-publish --dry-run
  imagery NASA GIBS true colour · redistributable · z≤9
  WARNING: needs z15, source stops at z9 — this frame will be soft
  BLOCKED: the move needs zoom 15 but the source stops at 9: the frame will be
  soft. Raise the camera or get finer imagery.
```

---

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
