#!/usr/bin/env python3
"""Draw a shot card for each Core View: the move, without rendering it.

A render needs a browser, a GPU and about half an hour. A decision about
whether the move is any good needs none of that. This writes an SVG per view
from the same camera path the renderer flies:

    plan view of the track      where the camera goes
    altitude / bearing / pitch  what the shot does over time
    caption ticks               when the words land

It is a storyboard, not a frame: no imagery, nothing invented about the place.
That matters here — the whole point of Core Views is that the place is real, so
a mock-up that looks like footage would be the one thing worse than no preview.

    python tools/views_storyboard.py --all
    python tools/views_storyboard.py --only coreviews-05-lord-howe --out /tmp
    python tools/views_storyboard.py --all --check     # CI: committed cards current
"""

from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import views_geo  # noqa: E402
import build_views_feed as feeds  # noqa: E402
import render_views as renderer  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = ROOT / "docs" / "views-storyboards"

VOID, PANEL, HAIR = "#04070f", "#0d1117", "#1d2735"
INK, MUTED, CYAN, VIOLET, EMBER = "#e8eef7", "#8b98a9", "#00e5ff", "#8a4890", "#c03a20"
W, H = 1200, 720


def esc(text: str) -> str:
    return (str(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def wrap(text: str, width: int) -> list[str]:
    words, lines, line = str(text).split(), [], ""
    for word in words:
        candidate = f"{line} {word}".strip()
        if len(candidate) <= width:
            line = candidate
            continue
        if line:
            lines.append(line)
        line = word
    if line:
        lines.append(line)
    return lines or [""]


def plan_panel(frames: list[dict], view: dict) -> str:
    """Top-down track, scaled so a degree of longitude is a degree of latitude."""
    lat0 = view["coordinates"][0]
    xs = [f["center"][0] * math.cos(math.radians(lat0)) for f in frames]
    ys = [f["center"][1] for f in frames]
    span_x = (max(xs) - min(xs)) or 1e-6
    span_y = (max(ys) - min(ys)) or 1e-6
    box = (24, 104, 648, 560)          # x, y, w, h
    pad = 46
    scale = min((box[2] - pad * 2) / span_x, (box[3] - pad * 2) / span_y)
    cx = box[0] + box[2] / 2
    cy = box[1] + box[3] / 2
    mid_x = (max(xs) + min(xs)) / 2
    mid_y = (max(ys) + min(ys)) / 2

    def px(x: float, y: float) -> tuple[float, float]:
        return (cx + (x - mid_x) * scale, cy - (y - mid_y) * scale)

    points = " ".join(f"{px(x, y)[0]:.1f},{px(x, y)[1]:.1f}" for x, y in zip(xs, ys))
    start = px(xs[0], ys[0])
    end = px(xs[-1], ys[-1])

    parts = [f'<rect x="{box[0]}" y="{box[1]}" width="{box[2]}" height="{box[3]}" '
             f'rx="12" fill="{PANEL}" stroke="{HAIR}"/>']
    # A faint cross-hair at the subject, so a static move still reads as a move.
    sx, sy = px(view["coordinates"][1] * math.cos(math.radians(lat0)),
                view["coordinates"][0])
    parts.append(f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="26" fill="none" '
                 f'stroke="{HAIR}" stroke-dasharray="3 5"/>')
    parts.append(f'<polyline points="{points}" fill="none" stroke="{CYAN}" '
                 f'stroke-width="2" stroke-linejoin="round" opacity="0.9"/>')
    parts.append(f'<circle cx="{start[0]:.1f}" cy="{start[1]:.1f}" r="6" fill="{VOID}" '
                 f'stroke="{CYAN}" stroke-width="2"/>')
    parts.append(f'<circle cx="{end[0]:.1f}" cy="{end[1]:.1f}" r="6" fill="{CYAN}"/>')
    parts.append(f'<text x="{start[0] + 12:.1f}" y="{start[1] - 10:.1f}" '
                 f'fill="{MUTED}" font-size="11">start</text>')
    parts.append(f'<text x="{end[0] + 12:.1f}" y="{end[1] + 18:.1f}" '
                 f'fill="{MUTED}" font-size="11">end</text>')
    parts.append(f'<text x="{box[0] + 16}" y="{box[1] + 26}" fill="{MUTED}" '
                 f'font-size="10" letter-spacing="2">TRACK · {esc(view["move"]).upper()}</text>')
    scale_note = f"{span_y * 110540 / 1000:.1f} km tall"
    parts.append(f'<text x="{box[0] + 16}" y="{box[1] + box[3] - 16}" '
                 f'fill="{MUTED}" font-size="10">{esc(scale_note)}</text>')
    return "\n".join(parts)


def series_panel(frames: list[dict], key: str, label: str, box: tuple,
                 colour: str, unit: str = "") -> str:
    x, y, w, h = box
    values = [f[key] for f in frames]
    lo, hi = min(values), max(values)
    if math.isclose(lo, hi):
        lo, hi = lo - 1, hi + 1
    pad = (hi - lo) * 0.12
    lo, hi = lo - pad, hi + pad

    def pt(i: int, value: float) -> tuple[float, float]:
        fx = x + 44 + (w - 60) * (i / max(1, len(frames) - 1))
        fy = y + h - 22 - (h - 44) * ((value - lo) / (hi - lo))
        return fx, fy

    points = " ".join(f"{a:.1f},{b:.1f}" for a, b in
                      (pt(i, v) for i, v in enumerate(values)))
    parts = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" '
             f'fill="{PANEL}" stroke="{HAIR}"/>',
             f'<text x="{x + 16}" y="{y + 20}" fill="{MUTED}" font-size="10" '
             f'letter-spacing="2">{esc(label.upper())}</text>',
             f'<text x="{x + 16}" y="{y + h - 8}" fill="{MUTED}" font-size="10">'
             f'{hi:.0f}{unit} → {lo:.0f}{unit}</text>',
             f'<polyline points="{points}" fill="none" stroke="{colour}" '
             f'stroke-width="2"/>']
    return "\n".join(parts)


def caption_panel(view: dict, box: tuple) -> str:
    x, y, w, h = box
    parts = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" '
             f'fill="{PANEL}" stroke="{HAIR}"/>',
             f'<text x="{x + 16}" y="{y + 20}" fill="{MUTED}" font-size="10" '
             f'letter-spacing="2">CAPTIONS · POINTS OF INTEREST</text>']
    duration = max(1, view["durationSeconds"])
    for index, caption in enumerate(view.get("captions", [])):
        frac = caption["at"] / duration
        tick_x = x + 16 + (w - 32) * frac
        ticks_y = y + 40
        parts.append(f'<line x1="{tick_x:.1f}" y1="{ticks_y}" x2="{tick_x:.1f}" '
                     f'y2="{ticks_y + 14}" stroke="{CYAN}" stroke-width="2"/>')
        parts.append(f'<text x="{tick_x:.1f}" y="{ticks_y - 4}" fill="{CYAN}" '
                     f'font-size="10">{caption["at"]}s</text>')
        for line_index, line in enumerate(wrap(caption["text"], 52)):
            parts.append(f'<text x="{x + 16}" y="{y + 84 + index * 34 + line_index * 14}" '
                         f'fill="{INK}" font-size="12">{esc(line)}</text>')
    return "\n".join(parts)


def storyboard(view: dict, frames: list[dict], args) -> str:
    try:
        provider = renderer.resolve_basemap(args)
    except SystemExit:
        provider = {"label": "—", "redistributable": False, "max_zoom": 15}
    source_max = min(renderer.MAX_TILE_ZOOM, provider.get("max_zoom", renderer.MAX_TILE_ZOOM))
    census = views_geo.tile_census(view, frames, max_zoom=source_max)
    tiles = sum(len(v) for v in census.values())
    shortfall = renderer.zoom_shortfall(frames, source_max)

    right_x = 692
    panels = [
        plan_panel(frames, view),
        series_panel(frames, "altitudeM", "altitude (m)",
                     (right_x, 104, 484, 150), CYAN),
        series_panel(frames, "bearing", "bearing (deg)",
                     (right_x, 268, 236, 130), VIOLET),
        series_panel(frames, "pitch", "pitch (deg)",
                     (right_x + 248, 268, 236, 130), EMBER),
        caption_panel(view, (right_x, 412, 484, 252)),
    ]

    warn = ""
    if shortfall:
        warn = (f'<text x="{right_x}" y="690" fill="{EMBER}" font-size="11">'
                f'imagery stops at z{source_max}; this move wants '
                f'z{source_max + shortfall} — render will be soft</text>')

    meta = (f'{view["durationSeconds"]}s · {len(frames)} frames · '
            f'{tiles} tiles · {esc(provider.get("label", "—"))} · '
            f'{"redistributable" if provider.get("redistributable") else "judging only"}')

    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}"
     viewBox="0 0 {W} {H}" font-family="Outfit, Inter, system-ui, sans-serif">
  <rect width="{W}" height="{H}" fill="{VOID}"/>
  <text x="24" y="40" fill="{CYAN}" font-size="11" letter-spacing="4">CORE VIEWS · CORE BUILDS</text>
  <text x="24" y="76" fill="{INK}" font-size="26" font-weight="600">{esc(view["title"])}</text>
  <text x="24" y="96" fill="{MUTED}" font-size="12">{esc(view["place"])}</text>
  <text x="{W - 24}" y="76" fill="{MUTED}" font-size="11" text-anchor="end">{esc(meta)}</text>
  <text x="{W - 24}" y="94" fill="{MUTED}" font-size="11" text-anchor="end">
    {esc(view["timeOfDay"])} · {esc(view["scene"])} · {esc(view["lane"])}
  </text>
{chr(10).join(panels)}
  {warn}
  <text x="24" y="690" fill="{MUTED}" font-size="10">
    A shot card, not a frame: camera and captions only, no imagery.
    Render with python tools/render_views.py --only {esc(view["id"])}
  </text>
</svg>
"""


def index_page(views: list[dict]) -> str:
    """The landing page for the shot cards. Generated, so it cannot drift."""
    rows = []
    for view in sorted(views, key=lambda v: v["number"]):
        camera = view.get("camera") or {}
        rows.append(
            f"| {view['number']:02d} | [{view['title']}]({view['id']}.svg) | "
            f"{view['place']} | {camera.get('move', '—')} | "
            f"{view['durationSeconds']}s | {view['timeOfDay']} | {view['scene']} |")
    return f"""# Core Views — shot cards

One card per view in [`views/catalog.json`](../../views/catalog.json): the
camera track from above, altitude, bearing and pitch over time, and where the
captions land. Generated by `tools/views_storyboard.py` from the same camera
paths the renderer flies, so a card cannot describe a move the render will not
make.

These are **not frames**. Nothing here invents imagery of a real place: that is
the one thing a preview of Core Views must not do. Judge the move here, then
render it.

| # | View | Where | Move | Length | Time | Scene |
|---|---|---|---|---|---|---|
{chr(10).join(rows)}

## Render one

```bash
python tools/render_views.py --only {views[0]['id']} --dry-run   # plan, no installs
python tools/render_views.py --only {views[0]['id']}             # needs Playwright
```

Regenerate every card:

```bash
python tools/views_storyboard.py --all
python tools/views_storyboard.py --all --check     # CI
```
"""


def build_for(view: dict, args) -> str:
    camera = view.get("camera") or {}
    params = {
        "move": camera.get("move", "orbit"),
        "duration": view["durationSeconds"],
        "fps": camera.get("fps") or 30,
        "altitude_start": float(camera.get("startHeightM") or 500),
        "altitude_end": float(camera.get("endHeightM") or camera.get("startHeightM") or 500),
        "pitch": float(args.pitch if args.pitch is not None
                       else camera.get("pitch") or renderer.DEFAULT_PITCH.get(
                           camera.get("move"), 55)),
    }
    frames = views_geo.camera_path(
        view, params["move"], params["duration"], params["fps"],
        params["altitude_start"], params["altitude_end"], params["pitch"])
    view = {**view, "move": params["move"]}
    return storyboard(view, frames, args)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", action="append", default=[], metavar="ID")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--pitch", type=float, default=None)
    parser.add_argument("--basemap", default="osm")
    parser.add_argument("--tile-template", default=None)
    parser.add_argument("--date", default=None)
    args = parser.parse_args(argv)

    if not args.all and not args.only:
        print("error: pick --all or --only <id>", file=sys.stderr)
        return 1

    views = feeds.validate(feeds.load_catalog())
    targets = renderer.selected(views, args.only, args.all)
    drawn = [v for v in targets if v["lane"] == "render"]
    skipped = [v for v in targets if v["lane"] != "render"]

    stale = 0
    for view in drawn:
        svg = build_for(view, args)
        path = args.out / f"{view['id']}.svg"
        if args.check:
            current = path.read_text(encoding="utf-8") if path.exists() else None
            if current != svg:
                print(f"error: {path} is out of date — run "
                      f"tools/views_storyboard.py --all", file=sys.stderr)
                stale += 1
            continue
        args.out.mkdir(parents=True, exist_ok=True)
        path.write_text(svg, encoding="utf-8")
        print(f"wrote {path}")

    if args.all or args.only:
        index = index_page(drawn)
        index_path = args.out / "README.md"
        if args.check:
            current = index_path.read_text(encoding="utf-8") if index_path.exists() else None
            if current != index:
                print(f"error: {index_path} is out of date — run "
                      f"tools/views_storyboard.py --all", file=sys.stderr)
                stale += 1
        elif not args.check:
            args.out.mkdir(parents=True, exist_ok=True)
            index_path.write_text(index, encoding="utf-8")
            print(f"wrote {index_path}")

    if skipped and not args.check:
        for view in skipped:
            print(f"skipped {view['id']}: lane {view['lane']} has no renderable move")

    if args.check:
        if stale:
            return 1
        print(f"ok: {len(drawn)} shot card(s) current")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
