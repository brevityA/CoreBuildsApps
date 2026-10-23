#!/usr/bin/env python3
"""Pure geometry for Core Views: camera moves and the tiles a move needs.

No network, no dependencies, no Android. Everything here is a function of the
catalogue entry, so it can be tested without a GPU and reused by the renderer
and by any future prefetch tool.

The camera model deliberately mirrors the one in
`tools/views-studio/index.html` (same move names, same easing, same
altitude→zoom rule). The studio is where you *author* a move; this module is
where it is *reproduced frame for frame*, which is why the renderer always
takes its keyframes from here rather than from the browser.
"""

from __future__ import annotations

import math

EARTH_CIRCUMFERENCE_M = 40075016.686
TILE_SIZE = 512           # MapLibre's raster tile grid
DEFAULT_CANVAS_HEIGHT = 1080
MAX_ZOOM = 20.0
MIN_ZOOM = 1.0

MOVES = (
    "crane-up", "orbit", "reveal", "ridge-follow",
    "descend", "top-down-descend", "glide", "track",
)


def ease_in_out(u: float) -> float:
    """The same ease-in-out quad the studio uses. Frame parity depends on it."""
    u = min(1.0, max(0.0, u))
    return 2 * u * u if u < 0.5 else 1 - ((-2 * u + 2) ** 2) / 2


def metres_per_degree_lat() -> float:
    return 110540.0


def metres_per_degree_lon(lat: float) -> float:
    return 111320.0 * max(0.01, math.cos(math.radians(lat)))


def altitude_to_zoom(altitude_m: float, lat: float,
                     canvas_height: int = DEFAULT_CANVAS_HEIGHT) -> float:
    """Height above ground -> MapLibre zoom.

    Keeps a ground span of ~2.2x the altitude in frame. The `- 1` is because
    MapLibre renders 512 px tiles, which is twice the resolution of the 256 px
    grid the classic 156543 m/px constant assumes.
    """
    ground_span = max(altitude_m * 2.2, 50.0)
    metres_per_pixel = ground_span / max(1, canvas_height)
    zoom = math.log2((156543.03392 * math.cos(math.radians(lat))) / metres_per_pixel) - 1
    return min(MAX_ZOOM, max(MIN_ZOOM, zoom))


def camera_at(view: dict, move: str, t: float, duration: float,
              altitude_start: float, altitude_end: float,
              pitch: float, canvas_height: int = DEFAULT_CANVAS_HEIGHT) -> dict:
    """One camera state. Pure: same inputs, same frame, every time."""
    lat, lon = view["coordinates"]
    u = 0.0 if duration <= 0 else min(1.0, max(0.0, t / duration))
    ease = ease_in_out(u)

    altitude = altitude_start + (altitude_end - altitude_start) * ease
    cam_pitch = float(pitch)
    bearing = 0.0
    centre = [lon, lat]

    def move_east(metres: float) -> float:
        return lon + metres / metres_per_degree_lon(lat)

    def move_north(metres: float) -> float:
        return lat + metres / metres_per_degree_lat()

    if move == "crane-up":
        cam_pitch = pitch + 8 * ease
    elif move == "orbit":
        bearing = 360.0 * ease
    elif move == "reveal":
        altitude = altitude_start + (altitude_end - altitude_start) * (ease ** 1.6)
        cam_pitch = pitch - 18 * ease
    elif move == "ridge-follow":
        forward = altitude_start * 1.6 * ease
        centre = [move_east(forward * math.sin(0.6)),
                  move_north(forward * math.cos(0.6))]
        bearing = 25 * math.sin(ease * math.pi * 2)
    elif move == "descend":
        cam_pitch = pitch + 10 * ease
        bearing = 12 * math.sin(ease * math.pi)
    elif move == "top-down-descend":
        cam_pitch = 12 + (pitch - 12) * ease
        bearing = 20 * ease
    elif move == "glide":
        travel = altitude_start * 2.4 * ease
        centre = [move_east(travel), move_north(travel * 0.35)]
        bearing = 8.0
    elif move == "track":
        travel = altitude_start * 2.8 * (u - 0.5)
        centre = [move_east(travel), move_north(travel * 0.2)]
        bearing = 70.0
    else:
        raise ValueError(f"unknown camera move {move!r}; known: {', '.join(MOVES)}")

    return {
        "center": [round(centre[0], 6), round(centre[1], 6)],
        "altitudeM": round(altitude, 1),
        "zoom": round(altitude_to_zoom(altitude, lat, canvas_height), 3),
        "pitch": round(max(0.0, min(85.0, cam_pitch)), 2),
        "bearing": round(bearing % 360.0, 2),
    }


def camera_path(view: dict, move: str, duration: float, fps: int,
                altitude_start: float, altitude_end: float, pitch: float,
                canvas_height: int = DEFAULT_CANVAS_HEIGHT) -> list[dict]:
    """Every frame's camera, plus the time it belongs to."""
    total = max(1, int(round(duration * fps)))
    frames = []
    for index in range(total + 1):
        t = index / fps
        camera = camera_at(view, move, t, duration, altitude_start, altitude_end,
                           pitch, canvas_height)
        camera["t"] = round(t, 3)
        frames.append(camera)
    return frames


# --- tiles -----------------------------------------------------------------

def lonlat_to_tile(lon: float, lat: float, zoom: int) -> tuple[int, int]:
    """Slippy-map tile containing a point (XYZ scheme, as MapLibre fetches)."""
    n = 2 ** zoom
    x = int((lon + 180.0) / 360.0 * n)
    lat_rad = math.radians(max(-85.05112878, min(85.05112878, lat)))
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return max(0, min(n - 1, x)), max(0, min(n - 1, y))


def ground_bbox(lat: float, lon: float, altitude_m: float, pitch_deg: float,
                aspect: float = 16 / 9) -> tuple[float, float, float, float]:
    """The ground a pitched camera can see, as (south, west, north, east).

    Deliberately generous: pitch pushes the far edge away, and a prefetch that
    over-estimates costs a few kilobytes while one that under-estimates leaves
    a grey rectangle in the middle of a finished clip.
    """
    half_height = altitude_m * 1.1
    pitch_factor = 1.0 + (max(0.0, pitch_deg) / 85.0) * 1.5
    half_height *= pitch_factor
    half_width = half_height * aspect
    dlat = half_height / metres_per_degree_lat()
    dlon = half_width / metres_per_degree_lon(lat)
    return (max(-85.0, lat - dlat), lon - dlon,
            min(85.0, lat + dlat), lon + dlon)


def tiles_for_bbox(south: float, west: float, north: float, east: float,
                   zoom: int) -> set[tuple[int, int]]:
    """Every tile at `zoom` that a bbox touches."""
    x0, y0 = lonlat_to_tile(west, north, zoom)
    x1, y1 = lonlat_to_tile(east, south, zoom)
    if x1 < x0:
        x0, x1 = x1, x0
    if y1 < y0:
        y0, y1 = y1, y0
    n = 2 ** zoom
    return {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)
            if 0 <= x < n and 0 <= y < n}


def tile_census(view: dict, frames: list[dict], max_zoom: int = 15) -> dict[int, set]:
    """Tiles per zoom level that a whole move needs.

    Only the top of each frame's zoom range is counted: MapLibre over-zooms a
    capped source, so asking for z17 does not mean downloading z17.
    """
    census: dict[int, set] = {}
    for frame in frames:
        zoom = min(max_zoom, int(round(frame["zoom"])))
        lat, lon = frame["center"][1], frame["center"][0]
        bbox = ground_bbox(lat, lon, frame["altitudeM"], frame["pitch"])
        census.setdefault(zoom, set()).update(tiles_for_bbox(*bbox, zoom))
    return census


def census_size_mb(census: dict[int, set], bytes_per_tile: int) -> float:
    tiles = sum(len(v) for v in census.values())
    return tiles * bytes_per_tile / 1_000_000


def tile_url(template: str, zoom: int, x: int, y: int) -> str:
    return (template.replace("{z}", str(zoom))
                    .replace("{x}", str(x))
                    .replace("{y}", str(y)))
