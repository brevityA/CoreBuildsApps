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
| `prequel_engine.frag` | 16-scene Series 2/3 engine: orbitals, warp, fog, trails, embers and horizon atmosphere | scene-specific Core Builds accents |

`prequel_engine.frag` is the production scene engine for the Series 2 and 3
prequel generations. Its scene ID and art-direction values live in
`motion-engine/prequels.json`; the same IDs are used by the WebGL2 studio and
the offline MP4 renderer.

## Seamless loops

Every motion term is **periodic in the requested loop duration** (20 s in the
production presets): positions drift via `sin/cos` and wrap via `fract`, colour
cycles via `sin`, so frame 0 == frame N and the loop closes with no seam.

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

## Status

The authoring sandbox does not expose a usable standalone GL context, so the
first production run is still a CI/dev-machine verification step. Use
`python tools/build_prequel_motion.py --dry-run` for a local plan, or run the
manual `render-prequel-motion.yml` workflow for Mesa software-GL compilation
and output validation. The browser studio can preview the same engine on a
WebGL2-capable machine without waiting for an MP4 render.
