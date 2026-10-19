# Core Builds Motion — live wallpapers

Forty-eight **seamless 1080p 60 fps loops**, every one the moving form of a
committed wall in [`Wallpapers/`](../../Wallpapers/README.md). None is a
stock pattern: until 2.4.0 the set opened with ten ffmpeg test sources
(`gradients`, `cellauto`, `mandelbrot`, `sierpinski`) with Core Builds names
on them; those were retired, their numbers 01–10 left empty, and every loop
since derives from a still you can also set.

The roster is one list, [`tools/live_roster.py`](../../tools/live_roster.py):
the renderers, the feed, the icon pack's `LiveLoop.kt`
(`tools/build_live_loop_kt.py`) and the tests all read it. Clip numbers are
raw-GitHub URLs that shipped app builds hard-code, so a number is never
renamed or reused.

| Set | Clips | Stills | Renderer | How it moves |
|---|---|---|---|---|
| Motion | 32–43 | 25–32, 51–54 | `tools/build_still_loops.py` | particles and ember heads twinkle, soft light shimmers, the camera drifts |
| Deep Space | 11–22 | 85–96 | `tools/build_still_loops.py` | stars twinkle on their own phase, nebulae shimmer, parallax drift |
| Cinema | 23–28, 53–58 | 97–102, 151–156 | `tools/build_cinema_wallpapers.py` | drawn as layers: marquee bulbs chase, neon stutters, dust in the beam |
| Icon Echo | 29–31, 44–52 | 104–106, 103, 107–108, 133–138 | `tools/build_still_loops.py` | no points to twinkle — the washes breathe, the camera drifts |

Clip 29 keeps its 2.3.x file name `signal-sweep` while being the moving form
of wall 104 Signal Red; 30 and 31 match their stills by name.

## The still→loop engine

`build_still_loops.py` splits a still into `detail` (points and hairlines,
`still − blur(still)`) and `glow` (everything soft), on a 1.1× canvas:

    frame = glow × shimmer(x, y, t) + detail + star × twinkle(t)

then resamples a moving crop (breathing zoom, Lissajous drift) to 1920×1080
and pipes it to ffmpeg (`libx264 -preset slow -crf 22`, `yuv420p`,
`+faststart`). Every motion completes a whole number of cycles over the 20 s,
so frame N equals frame 0. Only isolated points twinkle — a hairline stays
steady — and shimmer fades out below 60% of the height so launcher cards sit
on a calm floor. Per-set `twinkle`/`shimmer` amplitudes live on the roster.

Deterministic: same still, same seed, same frames. The committed still is
the composition's source of truth; the loop only adds time.

## Files

    Motion/live/<stem>.mp4            silent H.264 1080p 60 fps 20 s
    Motion/live/thumbs/<stem>.jpg     poster frame (1 s in), 480×270
    Motion/live/preview/live-preview.png   contact sheet, roster order
    Motion/live-feed.json             Overflight-compatible feed
    app/src/main/assets/live_thumbs/  the posters the icon pack bundles

## Rebuilding

    pip install -r tools/requirements.txt imageio-ffmpeg
    python tools/build_still_loops.py --stale      # clips older than their still
    python tools/build_cinema_wallpapers.py        # Cinema stills + loops
    python tools/build_motion_feed.py              # feed, sheet, bundled posters
    python tools/build_live_loop_kt.py             # LiveLoop.kt from the roster
    python tools/validate_motion_feed.py

A refreshed still makes its loop stale; `--stale` renders only those. About
three to five minutes per clip on two cores.
