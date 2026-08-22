# Core Motion — GLSL shaders (procedural live wallpapers)

Self-authored GLSL fragment shaders, rendered to seamless MP4 loops. This is
the quality tier *above* the ffmpeg built-in filters: every pixel computed from
math, resolution-independent, Shadertoy-style — but **authored here** so the
result is fully licensed (Shadertoy's default license is CC-BY-NC-SA, so we
don't adapt community shaders).

| Shader | Motif | Palette |
|---|---|---|
| `hex_plasma.frag` | plasma field + breathing point-up hex mark | cyan → violet |
| `starfield.frag` | twinkling, drifting layered starfields | cyan / build-blue on night |
| `flow.frag` | flowing noise bands (aurora) | cyan → build-blue → violet |

## Seamless loops

Every motion term is **periodic in T = 20 s**: positions drift via `sin/cos` and
wrap via `fract`, colour cycles via `sin`, so frame 0 == frame N and the loop
closes with no seam.

## Rendering

```bash
pip install moderngl numpy imageio-ffmpeg
python tools/build_shaders.py                 # all
python tools/build_shaders.py --only flow     # one
```

Runs a headless OpenGL 3.3 context (ModernGL) and pipes raw RGB frames straight
to ffmpeg. On a GPU dev machine it "just works"; in CI (no GPU) it uses Mesa
software GL:

```bash
sudo apt-get install -y libgl1-mesa-dri libegl1 xvfb
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a python tools/build_shaders.py
```

Outputs land in `Motion/live/coremotion-shader-*.mp4` and are appended to
`Motion/live-feed.json`.

## Status — not yet verified

These shaders have **never been compiled or run** (the authoring sandbox has no
GL: `libGL.so` absent, no `/dev/dri`). The first `build_shaders.py` run — in CI
or on a dev machine — is the real test of the GLSL and the renderer. Treat them
as untested until that run succeeds.
