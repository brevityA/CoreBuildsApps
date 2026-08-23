# Core Motion prequel engine

The original Series 2 and Series 3 artwork is static. The prequel engine gives
those 16 pieces a proper motion generation pass: a single self-authored GLSL
scene engine renders real fields, orbiting emitters, volumetric-looking fog,
particle trails, tracers, pendulums, aurora ribbons and horizon atmospheres.
It is not a still image with a zoom or pan.

## What is included

- `motion-engine/prequels.json` — the 16-preset source of truth, linked back to
  wallpaper 25–40.
- `motion-shaders/prequel_engine.frag` — the renderer. All trajectories are
  periodic over 20 seconds, so frame 0 and frame 600 close without a seam.
- `tools/build_prequel_motion.py` — headless ModernGL → raw RGB → H.264 MP4
  renderer. It can render 1080p for TV or 4K for a flagship export.
- `tools/motion-studio/index.html` — a zero-dependency WebGL2 art-director
  preview. It uses the same preset IDs and shader logic, lets you scrub the
  loop, change intensity and export a browser preview as WebM.

The engine deliberately keeps the lower part of the frame dark and calm so
launcher cards remain readable. It uses the Core Builds palette and no third-
party shader snippets or footage.

## Render the complete set

The production render needs a GPU or Mesa software OpenGL context:

```bash
pip install moderngl numpy imageio-ffmpeg
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a \
  python tools/build_prequel_motion.py --set series-2-3
```

Outputs go to `Motion/prequel/` and include a generated
`Motion/prequel-feed.json`. The generated URLs target the stable GitHub Release
asset lane `motion-prequels`, so the workflow can publish the binaries without
committing large MP4 files. The feed is Overflight-compatible; point Projectivy
at the release feed after the render is reviewed, or copy selected entries into
`Motion/live-feed.json` when you are ready to promote them into the main live
catalog.

Useful commands:

```bash
# Fast local smoke render: one preset, 640x360, one loop
python tools/build_prequel_motion.py --only orbitals --width 640 --height 360 --duration 2

# Production Series 2 at 1080p
python tools/build_prequel_motion.py --set series-2-motion

# 4K Series 3 output
python tools/build_prequel_motion.py --set series-3-horizons --width 3840 --height 2160
```

The render script fails loudly if the shader cannot compile, ffmpeg exits with
an error, or the first/last frame cannot be read. A `--dry-run` is available to
inspect the exact render plan on machines without OpenGL.

## Studio preview

Serve the repo root and open `/tools/motion-studio/`:

```bash
python -m http.server 8000 --bind 0.0.0.0
```

The studio loads the preset catalog locally. It does not need the CORS proxy to
render because the artwork is generated on the user's GPU. Source stills and
remote manifests are optional metadata only; if you add a remote manifest, use
an allowlisted read endpoint rather than turning the existing AIOStreams proxy
into an arbitrary URL relay.

The existing proxy in `Core-Builds/cloudflare-worker/` is intentionally scoped
to allowlisted AIOStreams API hosts and paths. That is the safe design: this
engine never sends wallpaper bytes or user credentials through it. GitHub raw
assets are already the canonical public host for this repo. If a future source
needs proxying, add a narrowly allowlisted asset route and tests to the worker;
do not accept arbitrary `?url=` forwarding.

## Delivery

Core Shift is the primary application surface. It already has the updater,
D-pad browser, local MP4 preview and Monet export path. Version 2.3.1 checks the
prequel feed on launch, caches the last good feed, downloads remote thumbnails
on demand, and appends new Series 2/3 entries to the existing Core Motion list.
No browser CORS proxy is involved in the Android path.

The engine does not silently replace the existing ten live clips. Render and
review the prequel set first, then either:

1. run `render-prequel-motion.yml`, which publishes `Motion/prequel/` to the
   stable `motion-prequels` release; Core Shift discovers the feed on its next
   launch, and Projectivy can be pointed at its `prequel-feed.json`, or
2. promote selected entries into the shared `Motion/live-feed.json` after
   checking playback on the target Fire TV/Android TV hardware.

For low-memory TV hardware, use 1920×1080, 30 fps, H.264 High, `yuv420p`,
`+faststart`, and the default 20-second loop. 4K is an explicit opt-in.
