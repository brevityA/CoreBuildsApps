# Core Builds Motion — live wallpapers

Twenty-two **genuinely animated** live-wallpaper loops — actual moving
content, not a still with a pan — all original and licenseable (no
third-party footage):

- **Clips 01–10** are generated procedurally by ffmpeg in the Core Builds §03
  palette (`tools/build_motion_feed.py`).
- **Clips 11–22** are the Deep Space walls moving: rendered from the committed
  `series-9-deep-space` stills by `tools/build_deep_space_loops.py`, with each
  scene's own stars twinkling and its glow shimmering.

| # | File | Title | Technique | Bitrate |
|---|---|---|---|---|
| 01 | `coremotion-live-01-spiral-cyan.mp4` | Spiral Cyan | flowing spiral gradient, cyan→violet | ~1.0 Mb/s |
| 02 | `coremotion-live-02-spiral-ember.mp4` | Spiral Ember | flowing spiral gradient, ember→violet | ~1.0 Mb/s |
| 03 | `coremotion-live-03-radial-cyan.mp4` | Radial Cyan | radial gradient, cyan→build-blue | ~0.7 Mb/s |
| 04 | `coremotion-live-04-circular-void.mp4` | Circular Void | circular gradient, cyan on night | ~0.8 Mb/s |
| 05 | `coremotion-live-05-circuit.mp4` | Circuit | cellular automaton (rule 110), cyan-tinted | ~2.2 Mb/s |
| 06 | `coremotion-live-06-deep-zoom.mp4` | Deep Zoom | Mandelbrot deep zoom, blurred | ~3.0 Mb/s |
| 07 | `coremotion-live-07-linear-blue.mp4` | Linear Build Blue | flowing linear gradient, build-blue→cyan | ~0.9 Mb/s |
| 08 | `coremotion-live-08-square-cyan.mp4` | Square Cyan | square gradient field, cyan on night | ~0.2 Mb/s |
| 09 | `coremotion-live-09-carpet.mp4` | Carpet | Sierpinski carpet, violet-tinted | ~1.2 Mb/s |
| 10 | `coremotion-live-10-spiral-blue.mp4` | Spiral Build Blue | flowing spiral gradient, build-blue→cyan | ~1.2 Mb/s |
| 11 | `coremotion-live-11-deep-space-event-horizon.mp4` | Event Horizon | wall 85, moving: its stars twinkle, its glow shimmers | ~0.9 Mb/s |
| 12 | `coremotion-live-12-deep-space-nebula-drift.mp4` | Nebula Drift | wall 86, moving: its stars twinkle, its glow shimmers | ~0.9 Mb/s |
| 13 | `coremotion-live-13-deep-space-starfield.mp4` | Starfield | wall 87, moving: its stars twinkle, its glow shimmers | ~1.3 Mb/s |
| 14 | `coremotion-live-14-deep-space-ringed-planet.mp4` | Ringed Planet | wall 88, moving: its stars twinkle, its glow shimmers | ~1.1 Mb/s |
| 15 | `coremotion-live-15-deep-space-galaxy-spiral.mp4` | Galaxy Spiral | wall 89, moving: its stars twinkle, its glow shimmers | ~0.7 Mb/s |
| 16 | `coremotion-live-16-deep-space-aurora-orbit.mp4` | Aurora Orbit | wall 90, moving: its stars twinkle, its glow shimmers | ~0.5 Mb/s |
| 17 | `coremotion-live-17-deep-space-comet-lane.mp4` | Comet Lane | wall 91, moving: its stars twinkle, its glow shimmers | ~0.8 Mb/s |
| 18 | `coremotion-live-18-deep-space-deep-field.mp4` | Deep Field | wall 92, moving: its stars twinkle, its glow shimmers | ~0.6 Mb/s |
| 19 | `coremotion-live-19-deep-space-ember-nova.mp4` | Ember Nova | wall 93, moving: its stars twinkle, its glow shimmers | ~1.0 Mb/s |
| 20 | `coremotion-live-20-deep-space-hex-station.mp4` | Hex Station | wall 94, moving: its stars twinkle, its glow shimmers | ~0.6 Mb/s |
| 21 | `coremotion-live-21-deep-space-cyan-supernova.mp4` | Cyan Supernova | wall 95, moving: its stars twinkle, its glow shimmers | ~0.9 Mb/s |
| 22 | `coremotion-live-22-deep-space-dark-side-moon.mp4` | Dark Side Moon | wall 96, moving: its stars twinkle, its glow shimmers | ~1.0 Mb/s |

Clips 11–22 are the icon pack's `series-9-deep-space` walls themselves, in
wall order, rendered from the committed 4K stills by
`tools/build_deep_space_loops.py`: each scene's own stars twinkle and its
nebulae shimmer, under a slow camera drift, and every motion completes whole
cycles so the loop has no seam.

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

`build_motion_feed.py` renders the ten procedural clips and writes the feed
(it lists the Deep Space loops but does not render them). Re-render those
with `python tools/build_deep_space_loops.py` (`--only 85` for one wall).

Edit the `CLIPS` list in `tools/build_motion_feed.py` to change palettes,
speeds, seeds, or add generators. To serve **real 4K footage** instead, drop
your own MP4s alongside and add them to `live-feed.json` — the plugin and the
feed don't care whether the bytes are procedural or filmed.

## Note on real footage

This is the lower-ceiling "now" content. For Apple-Aerials-grade quality, real
4K drone/aerial footage (which you source and license) drops into the same
feed with `url_4k` entries — no plugin change needed.
