#!/usr/bin/env python3
"""Render the Series 2/3 prequel set with the Core Prequel Engine.

This is the production/offline half of the engine. It uses one self-authored
GLSL fragment shader with scene presets and streams frames directly into
ffmpeg, so no thousands-of-PNG temporary files are created.

Requirements:
    pip install moderngl numpy imageio-ffmpeg
    # CI/no-GPU: sudo apt-get install libgl1-mesa-dri libegl1 xvfb

Examples:
    python tools/build_prequel_motion.py --dry-run
    python tools/build_prequel_motion.py --only orbitals --width 640 --height 360 --duration 2
    LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a python tools/build_prequel_motion.py --set series-2-3
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
CATALOG_PATH = ROOT / "motion-engine" / "prequels.json"
SHADER_PATH = ROOT / "motion-shaders" / "prequel_engine.frag"
DEFAULT_OUTPUT = ROOT / "Motion" / "prequel"
# Generated binaries are published by render-prequel-motion.yml to a stable
# release instead of being committed to the source tree. GitHub redirects are
# supported by both Core Motion and Core Shift downloaders.
BASE_URL = "https://github.com/brevityA/CoreBuildsApps/releases/download/motion-prequels"
DEFAULT_FPS = 30
DEFAULT_DURATION = 20.0
DEFAULT_WIDTH = 1920
DEFAULT_HEIGHT = 1080
DEFAULT_CRF = 18
THUMB_W, THUMB_H = 480, 270

VERTEX = """#version 330 core
in vec2 in_pos;
void main() { gl_Position = vec4(in_pos, 0.0, 1.0); }
"""


def ffmpeg_exe() -> str:
    try:
        import imageio_ffmpeg  # type: ignore

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"


def load_catalog() -> dict[str, Any]:
    with CATALOG_PATH.open(encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict) or not isinstance(data.get("series"), list):
        raise ValueError(f"invalid catalog: {CATALOG_PATH}")
    return data


def all_presets(catalog: dict[str, Any]) -> list[dict[str, Any]]:
    return [
        preset
        for group in catalog["series"]
        for preset in group.get("presets", [])
    ]


def select_presets(catalog: dict[str, Any], set_name: str, only: list[str]) -> list[dict[str, Any]]:
    groups = {
        "series-2-motion": {"series-2-motion"},
        "series-3-horizons": {"series-3-horizons"},
        "series-2-3": {"series-2-motion", "series-3-horizons"},
    }
    allowed = groups[set_name]
    selected = [g for g in catalog["series"] if g["id"] in allowed]
    presets = [p for g in selected for p in g.get("presets", [])]
    if only:
        wanted = {value.lower().removeprefix("coremotion-prequel-") for value in only}
        def key(preset: dict[str, Any]) -> str:
            return f"{int(preset['number']):02d}-{preset['slug'].lower()}"
        presets = [
            p for p in presets
            if p["slug"].lower() in wanted
            or str(p["number"]) in wanted
            or key(p) in wanted
        ]
        available = {p["slug"].lower() for p in presets} | {str(p["number"]) for p in presets} | {key(p) for p in presets}
        missing = wanted - available
        if missing:
            raise ValueError(f"unknown preset(s): {', '.join(sorted(missing))}")
    return presets


def hex_rgb(value: str) -> tuple[float, float, float]:
    value = value.lstrip("#")
    if len(value) != 6:
        raise ValueError(f"invalid accent colour: {value!r}")
    return tuple(int(value[i : i + 2], 16) / 255.0 for i in (0, 2, 4))  # type: ignore[return-value]


def output_name(preset: dict[str, Any], width: int = DEFAULT_WIDTH, height: int = DEFAULT_HEIGHT) -> str:
    tier = (
        "" if (width, height) == (DEFAULT_WIDTH, DEFAULT_HEIGHT)
        else "-4k" if (width, height) == (3840, 2160)
        else "-preview"
    )
    return f"coremotion-prequel-{int(preset['number']):02d}-{preset['slug']}{tier}.mp4"


def thumbnail_name(preset: dict[str, Any], width: int = DEFAULT_WIDTH, height: int = DEFAULT_HEIGHT) -> str:
    return output_name(preset, width, height).removesuffix(".mp4") + ".jpg"


def render_one(
    ctx: Any,
    program: Any,
    vao: Any,
    fbo: Any,
    preset: dict[str, Any],
    out_path: Path,
    width: int,
    height: int,
    duration: float,
    fps: int,
    crf: int,
    speed: float,
) -> None:
    import moderngl  # type: ignore

    program["u_resolution"] = (float(width), float(height))
    program["u_loop"] = float(duration)
    program["u_speed"] = float(speed)
    program["u_scene"] = int(preset["scene"])
    program["u_seed"] = float(preset["seed"])
    program["u_intensity"] = float(preset["intensity"])
    program["u_accent"] = hex_rgb(preset["accent"])
    frames = max(1, round(duration * fps))

    cmd = [
        ffmpeg_exe(),
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-f",
        "rawvideo",
        "-pix_fmt",
        "rgb24",
        "-s",
        f"{width}x{height}",
        "-r",
        str(fps),
        "-i",
        "-",
        "-c:v",
        "libx264",
        "-preset",
        "slow",
        "-crf",
        str(crf),
        "-pix_fmt",
        "yuv420p",
        "-movflags",
        "+faststart",
        "-an",
        str(out_path),
    ]
    process = subprocess.Popen(cmd, stdin=subprocess.PIPE, stderr=subprocess.PIPE)
    first_frame: bytes | None = None
    try:
        assert process.stdin is not None
        for index in range(frames):
            program["u_time"] = index / fps
            ctx.clear(0.0, 0.0, 0.0, 1.0)
            vao.render(moderngl.TRIANGLES)
            frame = fbo.read(components=3, alignment=1)
            if first_frame is None:
                first_frame = frame
            process.stdin.write(frame)

        # The encoded clip intentionally excludes the duplicate frame at t=T,
        # while this check renders it to prove the mathematical loop closes.
        program["u_time"] = float(duration)
        ctx.clear(0.0, 0.0, 0.0, 1.0)
        vao.render(moderngl.TRIANGLES)
        closing_frame = fbo.read(components=3, alignment=1)
        if first_frame != closing_frame:
            raise RuntimeError(f"non-seamless loop detected for {preset['slug']}")
        process.stdin.close()
        process.stdin = None
    except (BrokenPipeError, OSError) as exc:
        if process.stdin is not None:
            process.stdin.close()
            process.stdin = None
        process.kill()
        stderr = process.stderr.read().decode("utf-8", errors="replace") if process.stderr else ""
        raise RuntimeError(f"ffmpeg stopped while rendering {out_path.name}: {stderr}") from exc

    stderr = process.stderr.read().decode("utf-8", errors="replace") if process.stderr else ""
    code = process.wait()
    if code != 0:
        raise RuntimeError(f"ffmpeg failed for {out_path.name}: {stderr}")


def build_thumbnail(clip: Path, thumb: Path, duration: float, fps: int) -> None:
    # A very short smoke render may contain only a frame at t=0; seeking to
    # duration/2 would make ffmpeg emit an empty JPEG. Production renders still
    # get the attractive one-second-in poster.
    seek = min(1.0, max(0.0, duration - 1.0 / max(fps, 1)))
    cmd = [
        ffmpeg_exe(),
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-ss",
        str(seek),
        "-i",
        str(clip),
        "-vf",
        f"scale={THUMB_W}:{THUMB_H}:force_original_aspect_ratio=decrease,"
        f"pad={THUMB_W}:{THUMB_H}:(ow-iw)/2:(oh-ih)/2:color=black",
        "-frames:v",
        "1",
        "-q:v",
        "3",
        str(thumb),
    ]
    subprocess.run(cmd, check=True)


def render_context(width: int, height: int) -> tuple[Any, Any, Any, Any]:
    import moderngl  # type: ignore
    import numpy as np  # type: ignore

    # ModernGL's default backend prefers X11. CI and headless Linux machines
    # often have EGL but no DISPLAY, so retry explicitly before asking callers
    # to install xvfb.
    try:
        ctx = moderngl.create_standalone_context(require=330)
    except Exception as x11_error:
        try:
            ctx = moderngl.create_standalone_context(require=330, backend="egl")
        except Exception as egl_error:
            raise RuntimeError(f"X11 context: {x11_error}; EGL context: {egl_error}") from egl_error
    shader_source = SHADER_PATH.read_text(encoding="utf-8")
    program = ctx.program(vertex_shader=VERTEX, fragment_shader=shader_source)
    vertices = np.array([-1.0, -1.0, 3.0, -1.0, -1.0, 3.0], dtype="f4")
    vertex_buffer = ctx.buffer(vertices)
    vao = ctx.vertex_array(program, [(vertex_buffer, "2f", "in_pos")])
    texture = ctx.texture((width, height), 4)
    fbo = ctx.framebuffer(color_attachments=[texture])
    fbo.use()
    return ctx, program, vao, fbo


def build_manifest(presets: list[dict[str, Any]], output_dir: Path, width: int, height: int, duration: float, fps: int, speed: float) -> None:
    is_4k = (width, height) == (3840, 2160)
    videos = []
    for preset in presets:
        video_url = f"{BASE_URL}/{output_name(preset, width, height)}"
        videos.append(
            {
                "name": f"{int(preset['number']):02d} {preset['title']} · Prequel",
                "series": "series-2-motion" if int(preset["scene"]) < 8 else "series-3-horizons",
                "source": preset["source"],
                "engine": "core-prequel-engine@1.0.0",
                "url_1080p": None if is_4k else video_url,
                "url_4k": video_url if is_4k else None,
                "thumb": f"{BASE_URL}/{thumbnail_name(preset, width, height)}",
                "resolution": f"{width}x{height}",
                "duration": round(duration),
                "fps": fps,
                "motion_speed": speed,
                "scene": int(preset["scene"]),
            }
        )
    manifest = {
        "collection": "Core Builds Motion · Series 2/3 Prequels",
        "version": "1.0",
        "author": "brevityA",
        "engine": "core-prequel-engine@1.0.0",
        "count": len(videos),
        "videos": videos,
    }
    with (output_dir / "manifest.json").open("w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")


def build_feed(presets: list[dict[str, Any]], output_dir: Path, width: int, height: int, speed: float) -> None:
    is_4k = (width, height) == (3840, 2160)
    feed = []
    for preset in presets:
        video_url = f"{BASE_URL}/{output_name(preset, width, height)}"
        feed.append(
            {
                "location": "Core Motion · Prequels",
                "title": f"{int(preset['number']):02d} {preset['title']}",
                "author": "Core Builds",
                "url_img": f"{BASE_URL}/{thumbnail_name(preset, width, height)}",
                "url_1080p": None if is_4k else video_url,
                "url_4k": video_url if is_4k else None,
                "series": "series-2-motion" if int(preset["scene"]) < 8 else "series-3-horizons",
                "scene": int(preset["scene"]),
                "accent": preset["accent"],
                "motion_speed": speed,
                "engine": "core-prequel-engine@1.0.0",
            }
        )
    with (output_dir / "prequel-feed.json").open("w", encoding="utf-8") as f:
        json.dump(feed, f, indent=2)
        f.write("\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--set", choices=("series-2-motion", "series-3-horizons", "series-2-3"), default="series-2-3")
    parser.add_argument("--only", action="append", default=[], help="slug or number; repeat to render selected presets")
    parser.add_argument("--width", type=int, default=DEFAULT_WIDTH)
    parser.add_argument("--height", type=int, default=DEFAULT_HEIGHT)
    parser.add_argument("--duration", type=float, default=DEFAULT_DURATION)
    parser.add_argument("--fps", type=int, default=DEFAULT_FPS)
    parser.add_argument("--crf", type=int, default=DEFAULT_CRF)
    parser.add_argument("--speed", type=float, default=1.0, help="motion speed profile, 0.5 to 2.0")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--dry-run", action="store_true", help="print the plan without requiring OpenGL or ffmpeg")
    args = parser.parse_args(argv)

    if args.width <= 0 or args.height <= 0 or args.fps <= 0 or args.duration <= 0:
        parser.error("width, height, fps and duration must be positive")
    if not 0.5 <= args.speed <= 2.0:
        parser.error("speed must be between 0.5 and 2.0")

    catalog = load_catalog()
    presets = select_presets(catalog, args.set, args.only)
    if not presets:
        parser.error("no presets selected")

    print(f"Core Prequel Engine · {len(presets)} preset(s) · {args.width}x{args.height} · {args.duration:g}s · {args.fps}fps")
    for preset in presets:
        print(f"  {int(preset['number']):02d} {preset['title']:<18} scene={preset['scene']} -> {output_name(preset, args.width, args.height)}")
    if args.dry_run:
        return 0

    args.output_dir.mkdir(parents=True, exist_ok=True)
    try:
        ctx, program, vao, fbo = render_context(args.width, args.height)
    except Exception as exc:
        print("Unable to create OpenGL 3.3 context. Use --dry-run locally or run under Mesa/xvfb in CI.", file=sys.stderr)
        print(f"detail: {exc}", file=sys.stderr)
        return 2

    try:
        for preset in presets:
            clip = args.output_dir / output_name(preset, args.width, args.height)
            thumb = args.output_dir / thumbnail_name(preset, args.width, args.height)
            print(f"rendering {clip.name} ...", flush=True)
            render_one(ctx, program, vao, fbo, preset, clip, args.width, args.height, args.duration, args.fps, args.crf, args.speed)
            build_thumbnail(clip, thumb, args.duration, args.fps)
        build_manifest(presets, args.output_dir, args.width, args.height, args.duration, args.fps, args.speed)
        build_feed(presets, args.output_dir, args.width, args.height, args.speed)
    finally:
        try:
            fbo.release()
            vao.release()
            program.release()
            ctx.release()
        except Exception:
            pass

    print(f"done: {len(presets)} prequel loops + {args.output_dir / 'prequel-feed.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
