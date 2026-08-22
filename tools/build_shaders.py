#!/usr/bin/env python3
"""Render the Core Motion GLSL shaders to seamless MP4 loops.

Uses a headless OpenGL 3.3 context (ModernGL) to run each fragment shader and
pipes raw frames straight to ffmpeg for H.264 encoding — no intermediate files,
constant memory. This is the same pipeline as `glsl-to-mp4` /
`shadertoy-render`.

Requires a GL context: on a dev machine any GPU works; in CI, install
`libgl1-mesa-dri` + `libegl1` and set `LIBGL_ALWAYS_SOFTWARE=1` (software GL),
optionally under `xvfb-run`.

Run:
    python tools/build_shaders.py            # render all, write Motion/live/
    python tools/build_shaders.py --only hex_plasma

Outputs: Motion/live/coremotion-shader-*.mp4, and appends to Motion/live-feed.json.
"""

import argparse
import json
import os
import subprocess
import sys

FPS = 30
DURATION = 20
W, H = 1920, 1080

BASE_URL = "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/live"

# name -> (shader file, crf)
SHADERS = {
    "hex-plasma": ("motion-shaders/hex_plasma.frag", 20),
    "starfield": ("motion-shaders/starfield.frag", 20),
    "flow": ("motion-shaders/flow.frag", 20),
}

VERTEX = """#version 330 core
in vec2 in_pos;
void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }
"""


def ffmpeg():
    try:
        import imageio_ffmpeg  # type: ignore
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"


def render(shader_path, out_mp4, width, height, duration, fps, crf):
    import moderngl  # type: ignore
    import numpy as np  # type: ignore

    ctx = moderngl.create_standalone_context(require=330)
    prog = ctx.program(vertex_shader=VERTEX, fragment_shader=open(shader_path).read())

    # Fullscreen triangle.
    vertices = np.array([-1.0, -1.0, 3.0, -1.0, -1.0, 3.0], dtype="f4")
    vao = ctx.vertex_array(prog, [(ctx.buffer(vertices), "2f", "in_pos")])

    prog["u_resolution"] = (float(width), float(height))

    fbo = ctx.framebuffer(color_attachments=[ctx.texture((width, height), 4)])
    fbo.use()

    frames = int(duration * fps)
    cmd = [
        ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{width}x{height}",
        "-r", str(fps), "-i", "-",
        "-c:v", "libx264", "-preset", "slow", "-crf", str(crf),
        "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an", out_mp4,
    ]
    enc = subprocess.Popen(cmd, stdin=subprocess.PIPE)

    for i in range(frames):
        prog["u_time"] = i / fps
        ctx.clear(0.0, 0.0, 0.0, 1.0)
        vao.render(moderngl.TRIANGLES)
        enc.stdin.write(fbo.read(components=3))

    enc.stdin.close()
    enc.wait()
    if enc.returncode != 0:
        raise RuntimeError(f"ffmpeg failed for {out_mp4}")


def update_feed(names):
    """Append the shader renders to Motion/live-feed.json (idempotent)."""
    feed_path = "Motion/live-feed.json"
    feed = json.load(open(feed_path, encoding="utf-8"))
    existing = {e["url_1080p"].rsplit("/", 1)[-1] for e in feed if e.get("url_1080p")}
    for name in names:
        mp4 = f"coremotion-shader-{name}.mp4"
        if mp4 in existing:
            continue
        title = name.replace("-", " ").title()
        feed.append({
            "location": "Core Motion (shader)",
            "title": title,
            "author": "Core Builds",
            "url_img": f"{BASE_URL}/thumbs/coremotion-shader-{name}.jpg",
            "url_1080p": f"{BASE_URL}/{mp4}",
        })
    json.dump(feed, open(feed_path, "w", encoding="utf-8"), indent=2)
    open(feed_path, "a", encoding="utf-8").write("\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="render a single shader by name")
    args = ap.parse_args()

    names = [args.only] if args.only else list(SHADERS)
    os.makedirs("Motion/live", exist_ok=True)

    for name in names:
        shader_file, crf = SHADERS[name]
        out = f"Motion/live/coremotion-shader-{name}.mp4"
        print(f"rendering {name} -> {out}")
        render(shader_file, out, W, H, DURATION, FPS, crf)

    update_feed(names)
    print(f"done: {len(names)} shader renders + feed updated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
