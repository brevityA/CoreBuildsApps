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

Imagery is a first-class decision, not a URL in a flag
------------------------------------------------------
Every basemap carries a licence verdict. `--for-publish` refuses to render from
a source that may not be redistributed, because the failure mode otherwise is
"it looked fine in the studio" followed by a takedown. The default provider is
OpenStreetMap raster: keyless, honest, and *not* redistributable — it is for
judging a move.

    osm        © OpenStreetMap contributors        judging only
    terrain    © Mapterhorn                     elevation only
    gibs       NASA GIBS true colour            public domain, continental only
    sentinel   Copernicus Sentinel-2            redistributable, needs a template
    local      your own tiles (titiler/pmtiles) redistributable, needs a server
    custom     any raster template              on your head, checked by hand

`--dry-run` needs nothing but Python: it prints the frame count, the tiles the
move touches, any zoom shortfall against the source, and the exact encode
command. Use it before installing 300 MB of browser, and use it on a plane.

Real renders need:

    pip install playwright imageio-ffmpeg
    python -m playwright install chromium

Usage:
    python tools/render_views.py --all --dry-run
    python tools/render_views.py --only coreviews-05-lord-howe --dry-run
    python tools/render_views.py --only coreviews-05-lord-howe --for-publish \
        --basemap sentinel --tile-template "$COREVIEWS_SENTINEL_TEMPLATE"
    python tools/render_views.py --tiles --all          # URLs to warm a cache
    python tools/render_views.py --from my-export.json  # studio camera path
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import views_geo  # noqa: E402
import build_views_feed as feeds  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = ROOT / "views" / "render"

# One entry per imagery choice. `redistributable` is the licence verdict: may a
# clip made from these tiles be published to users? `credit_token` is the word
# the view's catalog.json credits must contain when --for-publish is used.
BASEMAPS: dict[str, dict] = {
    "osm": {
        "label": "OpenStreetMap raster",
        "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
        "attribution": "© OpenStreetMap contributors · terrain © Mapterhorn",
        "redistributable": False,
        "credit_token": None,
        "max_zoom": 19,
        "note": "keyless and honest, but ODbL tiles are not a source for a "
                "shipped video. Judging a move only.",
    },
    "terrain": {
        "label": "Terrain only (hillshade)",
        "tiles": [],
        "attribution": "terrain © Mapterhorn",
        "redistributable": True,
        "credit_token": "Mapterhorn",
        "max_zoom": 12,
        "note": "no imagery, so nothing to licence but the elevation model. "
                "Reads as a relief map, not an aerial.",
    },
    "gibs": {
        "label": "NASA GIBS true colour",
        # WMTS RESTful order is z/y/x, and the layer needs a date. Templates
        # here are named substitutions, so both are expressible.
        "tiles": ["https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/"
                  "MODIS_Terra_CorrectedReflectance_TrueColor/default/{date}/"
                  "GoogleMapsCompatible_Level9/{z}/{y}/{x}.jpg"],
        "attribution": "Imagery courtesy NASA GIBS / MODIS",
        "redistributable": True,
        "credit_token": "NASA",
        "max_zoom": 9,
        "needs_date": True,
        "note": "public domain and keyless, but ~300 m per pixel: only an "
                "Earth-from-orbit view, not a 1 km aerial.",
    },
    "sentinel": {
        "label": "Copernicus Sentinel-2",
        "tiles": [],
        "attribution": "Contains modified Copernicus Sentinel data {year}",
        "redistributable": True,
        "credit_token": "Copernicus",
        "max_zoom": 15,
        "needs_template": True,
        "note": "free and open, commercial included, with attribution. Needs a "
                "tile template you control — see docs/research/"
                "core-views-aerial-2026-10.md §5a for the three routes.",
    },
    "local": {
        "label": "Your own tile server",
        "tiles": ["http://127.0.0.1:8080/tiles/{z}/{x}/{y}.png"],
        "attribution": "own tiles — record the source in the view's credits",
        "redistributable": True,
        "credit_token": None,
        "max_zoom": 18,
        "needs_template": True,
        "note": "titiler / pmtiles serve. Serve with CORS headers or the "
                "browser half of the render will fail on blank tiles.",
    },
    "custom": {
        "label": "Custom raster template",
        "tiles": [],
        "attribution": "custom — check the licence before you publish",
        "redistributable": False,
        "credit_token": None,
        "max_zoom": 19,
        "needs_template": True,
        "note": "anything you point it at. --for-publish will refuse it, which "
                "is the point: an allowlist you can see beats a promise.",
    },
}

DEFAULT_PITCH = {
    "crane-up": 52, "orbit": 58, "reveal": 55, "ridge-follow": 68,
    "descend": 64, "top-down-descend": 62, "glide": 50, "track": 58,
}
TILE_BYTES = 26_000       # a z14 raster tile / DEM tile, roughly
MAX_TILE_ZOOM = 15        # Sentinel-2 native resolution is around z14–15


def _env_template(name: str) -> str | None:
    value = os.environ.get(name, "").strip()
    return value or None


def resolve_basemap(args) -> dict:
    """Turn the flags into one provider, refusing the combinations that lie."""
    name = getattr(args, "basemap", "osm") or "osm"
    if name not in BASEMAPS:
        raise SystemExit(f"error: unknown basemap {name!r}; "
                         f"choose from {', '.join(sorted(BASEMAPS))}")
    provider = dict(BASEMAPS[name])
    provider["name"] = name

    template = getattr(args, "tile_template", None)
    if name == "sentinel":
        template = template or _env_template("COREVIEWS_SENTINEL_TEMPLATE")
        if not template:
            raise SystemExit(
                "error: --basemap sentinel needs a tile template you control.\n"
                "  Set COREVIEWS_SENTINEL_TEMPLATE or pass --tile-template, e.g.\n"
                "    https://your-tiler/{z}/{x}/{y}.png\n"
                "  No public keyless Sentinel endpoint is baked in on purpose:\n"
                "  see docs/research/core-views-aerial-2026-10.md §5a.")
    if name == "local":
        template = template or _env_template("COREVIEWS_TILE_TEMPLATE") \
            or (provider["tiles"][0] if provider["tiles"] else None)
        if not template:
            raise SystemExit("error: --basemap local needs --tile-template or "
                             "COREVIEWS_TILE_TEMPLATE")
    if name == "custom":
        if not template:
            raise SystemExit("error: --basemap custom needs --tile-template")
    if template and name in ("sentinel", "local", "custom"):
        provider["tiles"] = [template]

    if provider.get("needs_date") and not getattr(args, "date", None):
        raise SystemExit(
            "error: --basemap gibs needs --date YYYY-MM-DD.\n"
            "  GIBS serves a daily composite; picking one for you would make a "
            "render unreproducible.")

    year = str(getattr(args, "date", "") or "")[:4]
    if year:
        provider["attribution"] = provider["attribution"].replace("{year}", year)
    else:
        provider["attribution"] = provider["attribution"].replace(" {year}", "")

    if not provider.get("tiles") and name != "terrain":
        raise SystemExit(f"error: basemap {name!r} resolved to no tiles")
    return provider


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


def zoom_shortfall(frames: list[dict], source_max_zoom: int) -> int:
    """How many zoom levels the move wants beyond what the imagery has.

    Zero is sharp. Positive means the source is being stretched: the frame will
    be soft, and no amount of encoding fixes it. Raise the camera, or get finer
    imagery.
    """
    wanted = max((round(f["zoom"]) for f in frames), default=0)
    return max(0, wanted - source_max_zoom)


def plan_view(view: dict, args, out_dir: Path, provider: dict | None = None) -> dict:
    """Everything needed to render one view, computed without a browser."""
    provider = provider or resolve_basemap(args)
    params = render_params(view, args)
    frames = views_geo.camera_path(
        view, params["move"], params["duration"], params["fps"],
        params["altitude_start"], params["altitude_end"], params["pitch"],
        canvas_height=args.height,
    )
    source_max = min(MAX_TILE_ZOOM, provider.get("max_zoom", MAX_TILE_ZOOM))
    census = views_geo.tile_census(view, frames, max_zoom=source_max)
    tiles = sum(len(v) for v in census.values())

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
        "basemap": provider["name"],
        "basemapLabel": provider["label"],
        "attribution": provider["attribution"],
        "redistributable": provider["redistributable"],
        "sourceMaxZoom": source_max,
        "zoomShortfall": zoom_shortfall(frames, source_max),
        "tiles": tiles,
        "tilesByZoom": {str(z): len(v) for z, v in sorted(census.items())},
        "estimatedDownloadMB": round(views_geo.census_size_mb(census, TILE_BYTES), 1),
        "encode": encode_command(args, out_dir / f"{view['id']}.mp4", params["fps"]),
        "credits": view.get("credits", []),
    }


def publish_blockers(plan: dict, view: dict, provider: dict) -> list[str]:
    """Why this render may not be published. Empty means it may."""
    problems = []
    if not provider["redistributable"]:
        problems.append(
            f"basemap {provider['name']!r} ({provider['label']}) is not licensed "
            f"for redistribution — {provider.get('note', '')}".strip())
    token = provider.get("credit_token")
    if token:
        haystack = " ".join(
            f"{c.get('source', '')} {c.get('license', '')}" for c in view.get("credits", []))
        if token.lower() not in haystack.lower():
            problems.append(
                f"the view's credits do not mention {token!r}, which this source "
                "requires. Add it to views/catalog.json before publishing.")
    if plan["zoomShortfall"] > 0:
        problems.append(
            f"the move needs zoom {plan['sourceMaxZoom'] + plan['zoomShortfall']} "
            f"but the source stops at {plan['sourceMaxZoom']}: the frame will be "
            "soft. Raise the camera or get finer imagery.")
    return problems


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


def harness_html(plan: dict, args, view: dict, provider: dict | None = None) -> str:
    """A page that shows one keyframe. It contains no camera maths on purpose."""
    provider = provider or resolve_basemap(args)
    tiles = provider["tiles"]
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
            # Without this MapLibre asks the source for zoom past what it has,
            # and the frame is a grey checkerboard rather than an image.
            "maxzoom": provider.get("max_zoom", 19),
            "attribution": provider["attribution"],
        }
        style["layers"].append({
            "id": "base", "type": "raster", "source": "base",
            "paint": {"raster-saturation": -0.35, "raster-contrast": 0.12},
        })
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


def tile_urls(plan: dict, args, provider: dict | None = None) -> list[str]:
    provider = provider or resolve_basemap(args)
    available = provider.get("tiles") or []
    if not available:
        return []
    template = available[0]
    date = getattr(args, "date", None) or ""
    urls = set()
    for frame in plan["frames"]:
        zoom = int(min(plan["sourceMaxZoom"], round(frame["zoom"])))
        lat, lon = frame["center"][1], frame["center"][0]
        bbox = views_geo.ground_bbox(lat, lon, frame["altitudeM"], frame["pitch"])
        for x, y in views_geo.tiles_for_bbox(*bbox, zoom):
            urls.add(views_geo.tile_url(template, zoom, x, y, date=date))
    return sorted(urls)


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
        "\n".join(f"{c['source']} — {c['license']}" for c in plan["credits"]) + "\n"
        + f"\nBasemap: {plan['basemapLabel']} — {plan['attribution']}\n",
        encoding="utf-8")
    print(f"rendered {output.relative_to(ROOT) if output.is_relative_to(ROOT) else output}")


def print_plan(plan: dict) -> None:
    print(f"{plan['id']}")
    print(f"  {plan['title']} — {plan['place']}")
    print(f"  move {plan['move']} · {plan['durationSeconds']}s · {plan['fps']} fps "
          f"· {plan['frameCount']} frames")
    licence = "redistributable" if plan["redistributable"] else "NOT redistributable"
    print(f"  imagery {plan['basemapLabel']} · {licence} · z≤{plan['sourceMaxZoom']}")
    print(f"  tiles {plan['tiles']} ({plan['tilesByZoom']}) "
          f"≈ {plan['estimatedDownloadMB']} MB")
    if plan["zoomShortfall"]:
        print(f"  WARNING: needs z{plan['sourceMaxZoom'] + plan['zoomShortfall']}, "
              f"source stops at z{plan['sourceMaxZoom']} — this frame will be soft")
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
    parser.add_argument("--basemap", choices=sorted(BASEMAPS), default="osm")
    parser.add_argument("--tile-template", default=None,
                        help="raster template with {z}/{x}/{y} (and {date} if needed)")
    parser.add_argument("--date", default=None,
                        help="YYYY-MM-DD for providers that serve a daily composite")
    parser.add_argument("--for-publish", action="store_true",
                        help="refuse anything the licences say cannot ship")
    args = parser.parse_args(argv)

    try:
        provider = resolve_basemap(args)
    except SystemExit as exc:
        print(str(exc), file=sys.stderr)
        return 1

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
        source_max = min(MAX_TILE_ZOOM, provider.get("max_zoom", MAX_TILE_ZOOM))
        plan = {
            "id": view["id"], "title": view["title"], "place": view["place"],
            "lane": "render", "move": path.get("move", "custom"),
            "fps": args.fps, "durationSeconds": path["durationSeconds"],
            "frames": frames, "frameCount": len(frames),
            "basemap": provider["name"], "basemapLabel": provider["label"],
            "attribution": provider["attribution"],
            "redistributable": provider["redistributable"],
            "sourceMaxZoom": source_max,
            "zoomShortfall": zoom_shortfall(frames, source_max),
            "tiles": 0, "tilesByZoom": {}, "estimatedDownloadMB": 0.0,
            "encode": encode_command(args, args.out / f"{view['id']}.mp4", args.fps),
            "credits": view["credits"],
        }
        print_plan(plan)
        sys.stdout.flush()
        if args.for_publish:
            blockers = publish_blockers(plan, view, provider)
            for problem in blockers:
                print(f"  BLOCKED: {problem}", file=sys.stderr)
            if blockers:
                return 1
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

    blocked_total = 0
    for view in renderable:
        out_dir = args.out / view["id"]
        try:
            plan = plan_view(view, args, out_dir, provider)
        except SystemExit as exc:
            print(f"error: {exc}", file=sys.stderr)
            return 1

        print_plan(plan)
        sys.stdout.flush()      # keep BLOCKED lines next to the plan they block

        if args.for_publish:
            blockers = publish_blockers(plan, view, provider)
            for problem in blockers:
                print(f"  BLOCKED: {problem}", file=sys.stderr)
            if blockers:
                blocked_total += 1
                print()
                continue

        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / "plan.json").write_text(
            json.dumps({k: v for k, v in plan.items() if k != "frames"}, indent=2) + "\n",
            encoding="utf-8")

        if args.tiles:
            urls = tile_urls(plan, args, provider)
            (out_dir / "tiles.txt").write_text("\n".join(urls) + "\n", encoding="utf-8")
            print(f"  wrote {len(urls)} tile URLs to "
                  f"{out_dir / 'tiles.txt'}")
            print()
            continue

        if args.dry_run:
            print()
            continue

        render(plan, view, args, out_dir)
        print()

    return 1 if blocked_total else 0


if __name__ == "__main__":
    raise SystemExit(main())
