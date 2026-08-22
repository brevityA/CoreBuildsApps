# Procedural live-wallpaper generation — research & recommendation

How to actually *produce* the Core Motion content. Fan-out research across the
real-world ways people make procedural live wallpapers, with a verdict on what
is feasible **in this environment** versus **in CI / on a dev machine**.

## The five approaches, ranked by quality

| # | Approach | Tooling | Quality | Loops | License |
|---|---|---|---|---|---|
| 1 | **GLSL fragment shaders** (Shadertoy-style) | `glsl-to-mp4`, `shadertoy-render`, `shadertoy-to-video-FBO`, `shadertoy-exporter` | Highest — ray-marched scenes, plasma, SDF, infinitely crisp at 4K/8K | mathematically seamless | ⚠ Shadertoy default is CC-BY-NC-SA (non-commercial). Use only self-authored or permissive shaders. |
| 2 | **Blender geometry nodes** | Blender 3.x | Highest — particles, 3D, noise fields | 4D-noise circles, modulo tricks | owned (you author) |
| 3 | **Creative coding → frames** (p5.js / Processing) | `p5.createLoop`, `CCapture.js` + ffmpeg, Golan Levin's `LoopTemplates` | High — organic Perlin-noise flow, aurora, bokeh | noise loops | owned |
| 4 | **Lottie / AnimatedVectorDrawable** (vector) | After Effects + Bodymovin, LottieFiles | Crisp at any size, tiny (KB), no banding | keyframe loops | owned / templates |
| 5 | **ffmpeg built-in filters** (current) | `mandelbrot`, `gradients`, `cellauto`, `sierpinski`, `life` | Lowest — geometric only | yes | owned |

### Key facts behind the ranking

- **Shaders are the reference.** Wallpaper Engine, Lively, `neowall`, and
  `shadow` all center on GLSL shaders: every pixel computed from math, zero
  texture/video assets, ~KB of source, GPU-rendered, "less than 5% GPU load."
  Lively even pastes a Shadertoy URL directly. Shadertoy has ~80k shaders.
- **Offline shader→MP4 is a solved problem.** `nabeel-oz/glsl-to-mp4` (ModernGL
  + ffmpeg, headless GL 3.3, seed-driven deterministic, seamless loops) and
  `alexjc/shadertoy-render` (vispy + ffmpeg) both do exactly the pipeline we
  need: a `.glsl` fragment shader → MP4 at arbitrary resolution/duration/seed.
- **Creative coding is the organic-motion route.** `p5.createLoop` gives
  one-line seamless noise loops; `CCapture.js` + ffmpeg exports them; Golan
  Levin's `LoopTemplates` are the canonical seamless-loop patterns. This is
  where "flowing aurora / particle field" comes from — not ffmpeg's geometric
  filters.
- **Lottie is a plugin-native lightweight path.** Projectivy's
  `WallpaperType.LOTTIE` and `ANIMATED_DRAWABLE` mean the *plugin* (not the
  Overflight feed) can serve vector animations that Projectivy renders itself —
  tiny, crisp, no video banding. Authoring is the constraint (AE/Bodymovin or
  LottieFiles templates).

## Feasibility here vs. CI

| Environment | GPU / GL | Xvfb / Mesa | Node | Verdict |
|---|---|---|---|---|
| **This sandbox** | none (`/dev/dri` absent, `libGL.so` missing) | none (no apt/sudo) | v22 ✓ | GLSL route **cannot** run; ffmpeg filters only |
| **GitHub Actions** | software GL via `libgl1-mesa-dri` | `xvfb` installable | ✓ | GLSL + creative-coding routes **can** run |

I verified the sandbox limitation directly: `moderngl.create_standalone_context()`
fails with `libGL.so not found`, there's no `/dev/dri`, and no `xvfb-run`.
PyCairo/CairoSVG aren't present in this session either (they'd need libcairo2).

## Recommendation

1. **Move procedural generation to CI.** Add `tools/build_shaders.py`
   (ModernGL headless + ffmpeg) with 3–6 **self-authored** GLSL shaders adapted
   in the §03 palette (a raymarched point-up hex + faceted diamond, a plasma
   field, a starfield, flowing noise). A CI job installs `xvfb` + `libgl1-mesa`
   + `moderngl`, renders the loops, and attaches them to the release. This is
   the real quality leap over the current ffmpeg filters — Shadertoy-grade,
   seamless, seed-reproducible — while keeping everything licensed (we author
   the shaders, so no Shadertoy CC-BY-NC-SA trap).

2. **Keep the ffmpeg filter set as the zero-dependency fallback** (works
   anywhere, including a CI-lite run) — already shipped.

3. **Spike Lottie/AnimatedVectorDrawable** as a lightweight plugin-native
   second channel later: serve `WallpaperType.LOTTIE` from the Core Motion
   plugin for vector loops that stay crisp and avoid banding.

4. **Blender geometry nodes only if you take over rendering** — it's the
   ceiling but an external, heavy authoring tool; not something to automate in
   CI cheaply.

## What I need from you to proceed

- Confirmation to add the **shader pipeline** (author shaders + CI job that
  renders them via software GL). This is the single highest-leverage upgrade.
- Whether the **Lottie** vector channel is worth spiking now or later.

## Sources

- `nabeel-oz/glsl-to-mp4` — ModernGL + ffmpeg GLSL→MP4, seed-driven
- `alexjc/shadertoy-render`, `danilw/shadertoy-to-video-with-FBO`,
  `NodotProject/shadertoy-exporter` — Shadertoy→video pipelines
- `mrchantey/p5.createLoop`, Golan Levin `LoopTemplates`, Peter Beshai
  (CCapture.js + ffmpeg) — creative-coding seamless loops
- Blender StackExchange / Blender Artists — geometry-node seamless loops
  (4D noise, modulo)
- Lively / Wallpaper Engine / `neowall` / `shadow` — real-time shader engines,
  confirming shaders as the reference approach
- Projectivy plugin `WallpaperType` (LOTTIE, ANIMATED_DRAWABLE) — from the
  official AIDL contract already vendored in `motion-plugin/`
