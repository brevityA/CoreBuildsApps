# Core Builds — Motion (video wallpapers) v1.0

6 seamless looping video wallpapers, generated from the Core Builds still
collection. Silent H.264, 20 s loops, 1080p default (+ one 4K flagship).

These exist because Android TV launchers that support *video* wallpapers —
Monet (Premium) and Projectivy (Premium) — play looping MP4 files, and the
static collection had no video line. Motion is the missing format.

## What each file is

| # | Title | Source wallpaper | 1080p | 4K |
|---|---|---|---|---|
| 01 | Hex Glow | `42 core-mark-centered-glow` | ✔ | ✔ (flagship) |
| 02 | Signature | `41 core-mark-signature` | ✔ | — |
| 03 | Cyan Nebula | `47 core-mark-cyan-nebula` | ✔ | — |
| 04 | Aurora | `52 core-mark-aurora` | ✔ | — |
| 05 | Omni Diamond | `59 core-mark-omni-diamond` | ✔ | — |
| 06 | Zenith | `60 core-mark-zenith` | ✔ | — |

## How they're made

`tools/build_motion.py` turns a still into a **cosine-eased "breathing zoom"**
(the same technique video-wallpaper packs use): the zoom runs one full period
over the clip and returns to 1.0, so frame 0 and the last frame are identical
and the loop has no visible seam. A few entries add a subtle sine-based sway.

- **Format:** MP4 (H.264 High), `yuv420p`, **silent**, 30 fps, `+faststart`.
- **Motion:** deterministic — `zoom` amplitude 0.05–0.08, `sway` 0–22 px.
- **1080p is the default.** 4K is opt-in per entry (`make4k`), because weak TV
  boxes handle 1080p more reliably — use 1080p on low-RAM devices.

Two manifests are written from the same source of truth:

- `manifest-motion.json` — full metadata (source image, URLs, resolution).
- `motion-feed.json` — Overflight-compatible JSON (`url_img` + `url_1080p` /
  `url_4k`). Projectivy Premium users can point the Overflight plugin at this
  file's raw URL to get the set natively.

## Regenerate

```bash
pip install imageio-ffmpeg        # bundles a static ffmpeg binary
python tools/build_motion.py
```

Add an entry to the `MOTION` list in `tools/build_motion.py` (source wallpaper
path, slug, title, zoom/sway, `make4k`), then rebuild.

## Delivery to launchers

- **Monet (Premium):** download a `.mp4` to the device, then Monet → Wallpaper →
  your videos → pick it. Video is a per-wallpaper choice (Monet has no API to
  rotate videos automatically).
- **Projectivy (Premium):** point the Overflight plugin at
  `Motion/motion-feed.json` (raw GitHub URL) and it serves the whole set.
- Hosting is the repo's raw path today; move to GitHub Releases if the set
  grows beyond a few tens of MB.

## Note on the mark

Motion inherits the brand rules: original geometry (point-up hex + faceted
diamond), §03 palette, dark/OLED-friendly, no audio, no baked text. The slow
breathing motion keeps the mark moving gently to avoid OLED burn-in.
