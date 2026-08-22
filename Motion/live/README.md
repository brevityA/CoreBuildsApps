# Core Builds Motion — live wallpapers (procedural)

Six **genuinely animated** live-wallpaper loops — actual moving content, not a
still with a pan. Generated procedurally by ffmpeg in the Core Builds §03
palette, so they're original and licenseable (no third-party footage).

| # | File | Title | Technique | Bitrate |
|---|---|---|---|---|
| 01 | `coremotion-live-01-spiral-cyan.mp4` | Spiral Cyan | flowing spiral gradient, cyan→violet | ~1.0 Mb/s |
| 02 | `coremotion-live-02-spiral-ember.mp4` | Spiral Ember | flowing spiral gradient, ember→violet | ~1.0 Mb/s |
| 03 | `coremotion-live-03-radial-cyan.mp4` | Radial Cyan | radial gradient, cyan→build-blue | ~0.7 Mb/s |
| 04 | `coremotion-live-04-circular-void.mp4` | Circular Void | circular gradient, cyan on night | ~0.8 Mb/s |
| 05 | `coremotion-live-05-circuit.mp4` | Circuit | cellular automaton (rule 110), cyan-tinted | ~2.1 Mb/s |
| 06 | `coremotion-live-06-deep-zoom.mp4` | Deep Zoom | Mandelbrot deep zoom, blurred | ~3.0 Mb/s |

All: 1920×1080, 30 fps, 20 s, H.264, **silent**, `+faststart`.

## Why these

"Live wallpaper" means *motion*, not a photograph. These are real animated
content — flowing gradients, a running automaton, an infinite fractal zoom —
which is what Overflight / Aerial Views do with footage, done here with
procedural generators so the brand stays fully owned.

## Feeds

- [`../live-feed.json`](../live-feed.json) — Overflight-compatible feed
  (`location`/`title`/`author`/`url_img`/`url_1080p`). Point the Overflight
  plugin *or* the Core Motion plugin at its raw URL.

## Regenerate

```bash
pip install imageio-ffmpeg
python tools/build_motion_feed.py
```

Edit the `CLIPS` list in `tools/build_motion_feed.py` to change palettes,
speeds, seeds, or add generators. To serve **real 4K footage** instead, drop
your own MP4s alongside and add them to `live-feed.json` — the plugin and the
feed don't care whether the bytes are procedural or filmed.

## Note on real footage

This is the lower-ceiling "now" content. For Apple-Aerials-grade quality, real
4K drone/aerial footage (which you source and license) drops into the same
feed with `url_4k` entries — no plugin change needed.
