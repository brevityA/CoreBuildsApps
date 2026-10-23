#!/usr/bin/env python3
"""Render a Core View from open data — no camera, no drone, no GPU.

The pipeline is deliberately boring:

    views/catalog.json          one view's camera move
        ↓ tools/views_geo.py    every frame's camera (pure Python)
    harness.html + keyframes    MapLibre in headless Chromium
        ↓ screenshots           one PNG per frame
    ffmpeg                      H.264 / HEVC MP4

Everything that decides what the shot *is* happens in Python. The harness only
displays a keyframe it was handed, so there is no second copy of the camera
maths to drift, and a render is reproducible from the catalogue alone.

`--dry-run` needs nothing but Python: it prints the frame count, the tiles the
move touches and the exact encode command. Use it before installing 300 MB of
browser, and use it on a plane.

Real renders need:

    pip install playwright imageio-ffmpeg
    python -m playwright install chromium

Image sources must be ones whose licence allows redistribution — Copernicus
Sentinel-2, Landsat, NAIP, or your own tiles. The default base map here is
OpenStreetMap raster because it needs no key and is honest about being a map;
it is for judging the move, not for shipping. See
docs/research/core-views-aerial-2026-10.md §5 before pointing this at imagery.

Usage:
    python tools/render_views.py --all --dry-run
    python tools/render_views.py --only coreviews-05-lord-howe --dry-run
    python tools/render_views.py --only coreviews-05-lord-howe
    python tools/render_views.py --tiles --all          # URLs to warm a cache
    python tools/render_views.py --from my-export.json  # studio camera path
"""

from __future__ import annotations

import argparse
import json
import math
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import views_geo  # noqa: E402
import build_views_feed as feeds  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = ROOT / "views" / "render"

BASEMAPS = {
    # Attribution is baked into the harness, not bolted on later.
    "osm": {
        "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
        "attribution": "© OpenStreetMap contributors · terrain © Mapterhorn",
    },
    "terrain": {
        "tiles": [],
        "attribution": "terrain © Mapterhorn",
    },
}
DEFAULT_PITCH = {
    "crane-up": 52, "orbit": 58, "reveal": 55, "ridge-follow": 68,
    "descend": 64, "top-down-descend": 62, "glide": 50, "track": 58,
}
TILE_BYTES = 26_000       # a z14 raster tile / DEM tile, roughly
MAX_TILE_ZOOM = 15        # Sentinel-2 native resolution is around z14–15


def selected(views: list[dict], only: list[str], every: bool) -> list[dict]:
    if every:
        return sorted(views, key=lambda v: v["number"])
    by_id = {v["id"]: v for v in views}
    missing = [i for i in only if i not in by_id]
    if missing:
        raise SystemExit(f"error: no such view: {', '.join(missing)}")
    return [by_id[i] for i in only]


def render_params(view: dict, args) -> dict:
    camera = view.get("camera") or {}
    move = camera.get("move", "orbit")
    if move not in views_geo.MOVES:
        raise SystemExit(
            f"error: {view['id']} uses camera move {move!r}, which the renderer "
            f"cannot fly. Known: {', '.join(views_geo.MOVES)}")
    start = camera.get("startHeightM")
    end = camera.get("endHeightM")
    if not start:
        raise SystemExit(f"error: {view['id']} has no camera.startHeightM")
    return {
        "move": move,
        "duration": view["durationSeconds"],
        "fps": args.fps or camera.get("fps") or 30,
        "altitude_start": float(start),
        "altitude_end": float(end or start),
        "pitch": float(args.pitch if args.pitch is not None
                       else camera.get("pitch") or DEFAULT_PITCH.get(move, 55)),
    }


def plan_view(view: dict, args, out_dir: Path) -> dict:
    """Everything needed to render one view, computed without a browser."""
    params = render_params(view, args)
    frames = views_geo.camera_path(
        view, params["move"], params["duration"], params["fps"],
        params["altitude_start"], params["altitude_end"], params["pitch"],
        canvas_height=args.height,
    )
    census = views_geo.tile_census(view, frames, max_zoom=MAX_TILE_ZOOM)
    tiles = sum(len(v) for v in census.values())
    encode = encode_command(args, out_dir / f"{view['id']}.mp4", params["fps"])

    return {
        "id": view["id"],
        "title": view["title"],
        "place": view["place"],
        "lane": view["lane"],
        "move": params["move"],
        "fps": params["fps"],
        "durationSeconds": view["durationSeconds"],
        "frames": frames,
        "frameCount": len(frames),
        "tiles": tiles,
        "tilesByZoom": {str(z): len(v) for z, v in sorted(census.items())},
        "estimatedDownloadMB": round(views_geo.census_size_mb(census, TILE_BYTES), 1),
        "encode": encode,
        "credits": view.get("credits", []),
    }


def encode_command(args, output: Path, fps: int) -> list[str]:
    """The exact ffmpeg invocation, so the plan can be read and copied."""
    source = "frames/%05d.png"
    common = ["-framerate", str(fps), "-i", source,
              "-movflags", "+faststart", "-an"]
    if args.hdr:
        return ["ffmpeg", "-y", *common,
                "-c:v", "libx265", "-preset", "slow", "-crf", str(args.crf),
                "-tag:v", "hvc1",
                "-x265-params",
                "hdr-opt=1:repeat-headers=1:colorprim=bt2020:"
                "transfer=smpte2084:colormatrix=bt2020nc",
                "-pix_fmt", "yuv420p10le", str(output)]
    profile = ["-profile:v", "high", "-preset", "slow", "-crf", str(args.crf),
               "-maxrate", "16M", "-bufsize", "32M", "-pix_fmt", "yuv420p"]
    return ["ffmpeg", "-y", *common, "-c:v", "libx264", *profile, str(output)]


def harness_html(plan: dict, args, view: dict) -> str:
    """A page that shows one keyframe. It contains no camera maths on purpose."""
    basemap = BASEMAPS.get(args.basemap, {"tiles": [], "attribution": ""})
    tiles = ([args.tile_template] if args.basemap == "custom" and args.tile_template
             else basemap["tiles"])
    style = {
        "version": 8,
        "sources": {
            "dem": {"type": "raster-dem",
                    "url": "https://tiles.mapterhorn.com/tilejson.json"},
        },
        "layers": [],
        "terrain": {"source": "dem", "exaggeration": 1},
        "sky": {},
    }
    if tiles:
        style["sources"]["base"] = {
            "type": "raster", "tiles": tiles, "tileSize": 256,
            "maxzoom": 19, "attribution": basemap["attribution"],
        }
        style["layers"].append({"id": "base", "type": "raster", "source": "base"})
    style["layers"].append({
        "id": "hills", "type": "hillshade", "source": "dem",
        "paint": {"hillshade-shadow-color": "#0a1018",
                  "hillshade-highlight-color": "#1c2a3a"},
    })

    keyframes = [{k: f[k] for k in ("center", "zoom", "pitch", "bearing")}
                 for f in plan["frames"]]
    return f"""<!doctype html>
<meta charset="utf-8">
<title>{view['id']}</title>
<link href="https://unpkg.com/maplibre-gl@6.13.0/dist/maplibre-gl.css" rel="stylesheet">
<style>html,body,#map{{margin:0;height:100%;background:#04070f}}</style>
<div id="map"></div>
<script type="module">
import * as maplibregl from 'https://unpkg.com/maplibre-gl@6.13.0/dist/maplibre-gl.mjs';
const frames = {json.dumps(keyframes)};
const map = new maplibregl.Map({{
  container: 'map',
  style: {json.dumps(style)},
  center: frames[0].center,
  zoom: frames[0].zoom,
  pitch: frames[0].pitch,
  bearing: frames[0].bearing,
  maxPitch: 85,
  preserveDrawingBuffer: true,
  attributionControl: false,
  fadeDuration: 0,
  interactive: false,
}});
window.__ready = false;
map.on('load', () => {{ window.__ready = true; }});
window.renderFrame = (i) => new Promise((resolve) => {{
  const f = frames[i];
  map.jumpTo({{ center: f.center, zoom: f.zoom, pitch: f.pitch, bearing: f.bearing }});
  let done = false;
  const finish = () => {{ if (!done) {{ done = true; resolve(true); }} }};
  map.once('idle', finish);
  map.triggerRepaint();
  setTimeout(finish, 2500);   // a missing tile must not stall the render
}});
</script>
"""


def write_harness(plan: dict, view: dict, args, out_dir: Path) -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / "harness.html"
    path.write_text(harness_html(plan, args, view), encoding="utf-8")
    return path


def tile_urls(plan: dict, args) -> list[str]:
    if args.basemap == "custom" and args.tile_template:
        template = args.tile_template
    else:
        available = BASEMAPS.get(args.basemap, {}).get("tiles", [])
        template = available[0] if available else None
    if not template:
        return []
    urls = []
    for frame in plan["frames"]:
        zoom = int(min(MAX_TILE_ZOOM, round(frame["zoom"])))
        lat, lon = frame["center"][1], frame["center"][0]
        bbox = views_geo.ground_bbox(lat, lon, frame["altitudeM"], frame["pitch"])
        for x, y in sorted(views_geo.tiles_for_bbox(*bbox, zoom)):
            urls.append(views_geo.tile_url(template, zoom, x, y))
    return sorted(set(urls))


def ffmpeg_exe() -> str:
    try:
        import imageio_ffmpeg  # type: ignore
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        found = shutil.which("ffmpeg")
        if not found:
            raise SystemExit(
                "error: no ffmpeg. Install it, or `pip install imageio-ffmpeg`.")
        return found


def render(plan: dict, view: dict, args, out_dir: Path) -> None:
    try:
        from playwright.sync_api import sync_playwright  # type: ignore
    except ImportError:
        raise SystemExit(
            "error: playwright is not installed. Run:\n"
            "  pip install playwright imageio-ffmpeg\n"
            "  python -m playwright install chromium\n"
            "Everything except the capture works today: try --dry-run.")

    harness = write_harness(plan, view, args, out_dir)
    frames_dir = out_dir / "frames"
    if frames_dir.exists():
        shutil.rmtree(frames_dir)
    frames_dir.mkdir(parents=True)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            args=["--use-gl=swiftshader", "--disable-gpu-sandbox",
                  "--force-device-scale-factor=1"])
        page = browser.new_page(viewport={"width": args.width, "height": args.height})
        page.goto(harness.as_uri())
        page.wait_for_function("window.__ready === true", timeout=60_000)
        for index in range(plan["frameCount"]):
            page.evaluate("async (i) => { await window.renderFrame(i); }", index)
            page.screenshot(path=str(frames_dir / f"{index:05d}.png"))
            if index % 30 == 0:
                print(f"  frame {index}/{plan['frameCount']}", flush=True)
        browser.close()

    output = out_dir / f"{view['id']}.mp4"
    command = [ffmpeg_exe(), *plan["encode"][1:-1], str(output)]
    subprocess.run(command, cwd=out_dir, check=True)
    (out_dir / "credits.txt").write_text(
        "\n".join(f"{c['source']} — {c['license']}" for c in plan["credits"]) + "\n",
        encoding="utf-8")
    print(f"rendered {output.relative_to(ROOT)}")


def print_plan(plan: dict) -> None:
    print(f"{plan['id']}")
    print(f"  {plan['title']} — {plan['place']}")
    print(f"  move {plan['move']} · {plan['durationSeconds']}s · {plan['fps']} fps "
          f"· {plan['frameCount']} frames")
    print(f"  tiles {plan['tiles']} ({plan['tilesByZoom']}) "
          f"≈ {plan['estimatedDownloadMB']} MB")
    print(f"  encode: {' '.join(plan['encode'])}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", action="append", default=[], metavar="ID")
    parser.add_argument("--all", action="store_true", help="every renderable view")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--tiles", action="store_true",
                        help="write the tile URL list and render nothing")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--from", dest="from_path", type=Path,
                        help="a studio export (camera path) instead of the catalogue")
    parser.add_argument("--width", type=int, default=1920)
    parser.add_argument("--height", type=int, default=1080)
    parser.add_argument("--fps", type=int, default=None)
    parser.add_argument("--crf", type=int, default=18)
    parser.add_argument("--pitch", type=float, default=None)
    parser.add_argument("--hdr", action="store_true", help="HEVC 10-bit HDR10 encode")
    parser.add_argument("--basemap", choices=["osm", "terrain", "custom"], default="osm")
    parser.add_argument("--tile-template", default=None,
                        help="raster template with {z}/{x}/{y}, for --basemap custom")
    args = parser.parse_args(argv)

    if args.from_path:
        if not args.from_path.exists():
            print(f"error: {args.from_path} not found", file=sys.stderr)
            return 1
        export = json.loads(args.from_path.read_text(encoding="utf-8"))
        path = export.get("cameraPath") or export
        entry = export.get("catalogEntry") or {}
        view = {"id": entry.get("id", args.from_path.stem), "title": entry.get("title", ""),
                "place": entry.get("place", ""), "lane": "render",
                "coordinates": [path["keyframes"][0]["center"][1],
                                path["keyframes"][0]["center"][0]],
                "durationSeconds": path["durationSeconds"], "camera": entry.get("camera", {}),
                "credits": entry.get("credits", [])}
        args.fps = args.fps or path.get("fps", 30)
        frames = [{"center": k["center"], "zoom": k["zoom"], "pitch": k["pitch"],
                   "bearing": k["bearing"], "altitudeM": k.get("altitudeM", 0),
                   "t": k["t"]} for k in path["keyframes"]]
        plan = {
            "id": view["id"], "title": view["title"], "place": view["place"],
            "lane": "render", "move": path.get("move", "custom"),
            "fps": args.fps, "durationSeconds": path["durationSeconds"],
            "frames": frames, "frameCount": len(frames), "tiles": 0,
            "tilesByZoom": {}, "estimatedDownloadMB": 0.0,
            "encode": encode_command(args, args.out / f"{view['id']}.mp4", args.fps),
            "credits": view["credits"],
        }
        print_plan(plan)
        if args.dry_run:
            return 0
        render(plan, view, args, args.out)
        return 0

    catalog = feeds.load_catalog()
    views = feeds.validate(catalog)

    if not args.all and not args.only:
        print("error: pick --all or --only <id> (or --dry-run --all to see the plan)",
              file=sys.stderr)
        return 1

    targets = selected(views, args.only, args.all)
    renderable, skipped = [], []
    for view in targets:
        if view["lane"] == "render":
            renderable.append(view)
        else:
            skipped.append((view, "archive footage" if view["lane"] == "archive"
                            else "no capture device"))

    if skipped:
        print("not rendered here:")
        for view, reason in skipped:
            print(f"  {view['id']}: {reason} — supply a clip and encode it by hand")
        print()

    if not renderable:
        return 0

    failures = 0
    for view in renderable:
        out_dir = args.out / view["id"]
        try:
            plan = plan_view(view, args, out_dir)
        except SystemExit as exc:
            print(f"error: {exc}", file=sys.stderr)
            failures += 1
            continue

        print_plan(plan)
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / "plan.json").write_text(
            json.dumps({k: v for k, v in plan.items() if k != "frames"}, indent=2) + "\n",
            encoding="utf-8")

        if args.tiles:
            urls = tile_urls(plan, args)
            (out_dir / "tiles.txt").write_text("\n".join(urls) + "\n", encoding="utf-8")
            print(f"  wrote {len(urls)} tile URLs to "
                  f"{(out_dir / 'tiles.txt').relative_to(ROOT)}")

        if args.dry_run or args.tiles:
            print()
            continue

        try:
            render(plan, view, args, out_dir)
        except SystemExit as exc:
            print(f"error: {exc}", file=sys.stderr)
            return 1
        print()

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
