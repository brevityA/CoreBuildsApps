#!/usr/bin/env python3
"""Core Views — the season as live wallpapers the icon pack's engine can play.

Twelve loops, one per catalogue view, in the shape the wallpaper engine
already plays: silent, 1920x1080, 30 fps, 20 s, seamless, `+faststart`, a
bundled poster frame in the APK and the MP4 fetched from raw GitHub.

**What the picture is.** Not the view. Every catalogue view is `planned`: the
open-data renderer that turns Copernicus Sentinel imagery and Copernicus DEM
elevation into a real flyover needs tile access, and until a view's 1080p
rendition exists there is no footage of that place to play. So each loop is a
*wallpaper*, drawn from the view's own design:

  * the scene's accent from the suite palette, washing a dark field;
  * a cartographic hairline texture — contours for a land view, a shoreline
    for a coastal one — generated from the view's own seed, never traced from
    real geography;
  * the camera's **real track**, from `views/catalog.json` through
    `tools/views_geo.camera_at`, with a comet travelling it at the renderer's
    own speed, so the move on the wallpaper is the move the render will make;
  * the calm bottom quarter and the vignette that keep a launcher's cards
    readable, which is the one thing a TV wallpaper has to get right.

Nothing here claims to be a photograph. `docs/CORE_VIEWS_WALLPAPERS.md` says
so, the browser tile says so, and `views/catalog.json` keeps every rendition
`null` so nothing downstream can publish one as a render.

Every motion completes a whole number of cycles in the 20 s, so frame N is
frame 0 and the loop has no seam - and the travelling light, which cannot be
cyclic on a track that is not closed, fades out at the end of its run and back
in at the start. Deterministic: same view, same clip.

    pip install numpy pillow imageio-ffmpeg
    python tools/build_coreviews_loops.py                  # all twelve
    python tools/build_coreviews_loops.py --only 3         # one view
    python tools/build_coreviews_loops.py --palette        # one PNG still, no ffmpeg
    python tools/build_coreviews_loops.py --list
"""
from __future__ import annotations

import argparse
import hashlib
import math
import os
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import views_geo  # noqa: E402
import render_views  # noqa: E402
from coreviews_roster import (  # noqa: E402
    APP_THUMBS, CLIPS, DURATION, FPS, H, LIVE, ROOT, THUMB_H, THUMB_W, THUMBS, W,
    Clip)

# Room for the drift: the picture is 15% wider and taller than the frame, so a
# two-cycle parallax crop never reaches an edge.
CANVAS = 1.15
CW, CH = int(W * CANVAS), int(H * CANVAS)
# The contour field is computed small and lifted: hairlines want to be smooth
# curves, and a full-resolution noise field makes dotted ones.
FIELD_W = 1104
FIELD_H = int(FIELD_W * CH / CW)

# The suite palette, one accent per scene (the mockup's table, unchanged).
ACCENTS = {
    "sea": (0, 229, 255),
    "beach": (62, 201, 214),
    "countryside": (138, 72, 144),
    "waterfall": (79, 179, 200),
    "patterns": (192, 58, 32),
    "city": (0, 229, 255),
    "space": (138, 72, 144),
}
# Scenes whose low ground is water: the tint below the shoreline is the sea,
# not a shadow, and the contours only cross the land.
COASTAL = {"sea", "beach", "waterfall"}

# Time of day is the one thing that changes a place more than its outline, and
# a season of twelve wallpapers in one temperature reads as one wallpaper. The
# palette stays the suite's; the light colour is the hour's.
HOURS = {
    "sunrise": ((255, 176, 118), 0.34),
    "day":     ((210, 232, 242), 0.18),
    "sunset":  ((255, 122, 74), 0.38),
    "night":   ((122, 158, 255), 0.26),
}

# Filled by terrain(): each coastal view's water, as (mask, signed distance to
# the shore). The signed field is what the shoreline stroke is drawn from -
# a mask alone has no gradient, so contouring it draws a line everywhere or
# nowhere, depending on whether the mask happens to land on an integer.
CLIP_SHORE: dict[str, tuple[np.ndarray, np.ndarray]] = {}

DRIFT = 0.80        # of the available margin, at the peak of the pan
# One pass per loop, and the light fades in at the start of its track and out
# at the end, so the wrap happens while it is invisible. A camera track is not
# a closed curve: running the traveller round it and letting it snap back is a
# jump cut, and a jump cut is the one thing a wallpaper must not do.
LAPS = 1
TRAIL = 46          # trail segments
VIG = 0.34          # how far the frame corners fall off


def seed_of(clip: Clip) -> int:
    return int.from_bytes(hashlib.sha1(clip.view_id.encode()).digest()[:4], "big")


def accent_of(clip: Clip) -> tuple[int, int, int]:
    return ACCENTS.get(clip.scene, (0, 229, 255))


def _blur(field: np.ndarray, radius: int, passes: int = 3) -> np.ndarray:
    """Separable box blur, [passes] times: a Gaussian to the eye, exact to nobody.

    Three passes is the standard approximation and it costs two cumulative
    sums per axis, which at this size is far cheaper than a real convolution
    and avoids pulling in scipy for one filter.
    """
    out = field.astype(np.float32, copy=True)
    for _ in range(passes):
        out = _box(out, radius, axis=0)
        out = _box(out, radius, axis=1)
    return out


def _box(field: np.ndarray, radius: int, axis: int) -> np.ndarray:
    r = max(1, radius)
    pad = [(0, 0), (0, 0)]
    pad[axis] = (r, r)
    padded = np.pad(field, pad, mode="edge")
    cum = np.cumsum(padded, axis=axis, dtype=np.float32)
    n = field.shape[axis]
    zero_shape = list(cum.shape)
    zero_shape[axis] = 1
    cum = np.concatenate([np.zeros(zero_shape, np.float32), cum], axis=axis)
    hi = np.take(cum, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis)
    lo = np.take(cum, np.arange(0, n), axis=axis)
    return (hi - lo) / float(2 * r + 1)


def _octaves(seed: int, shape: tuple[int, int], radii) -> np.ndarray:
    """Fractal noise in 0..1: white noise blurred at a spread of scales.

    Blurring at increasing radii rather than blowing up a handful of random
    pixels - a resize of a small grid leaves diagonal stair-steps that no
    amount of contouring hides, and on a 4K TV they read as banding.
    """
    rng = np.random.default_rng(seed)
    # Float throughout, and blurred in numpy: an 8-bit field quantises the
    # height into 256 steps, and a contour drawn on those steps comes out as
    # terraces and vertical combs instead of curves.
    white = rng.random(shape).astype(np.float32)
    h = np.zeros(shape, np.float32)
    amp, total = 1.0, 0.0
    for radius in radii:
        layer = _blur(white, int(radius))
        layer -= layer.min()
        layer /= max(float(layer.max()), 1e-6)
        h += amp * layer
        total += amp
        amp *= 0.55
    h /= total
    # Normalise after the mix, or the octaves that blur noisily set the range.
    h -= h.min()
    h /= max(float(h.max()), 1e-6)
    return h


def terrain(clip: Clip) -> tuple[np.ndarray, np.ndarray]:
    """The landform and the grain that shades it.

    Contours belong to the landform only. Drawing them on the grain as well is
    what makes a wallpaper look like a marbled countertop: a dozen broad lines
    read as ground, several hundred small closed loops read as texture and
    then, at TV distance, as noise.
    """
    seed = seed_of(clip)
    # Broad shapes: landmass, bays, the fall of a valley.
    shape = _octaves(seed, (FIELD_H, FIELD_W), [30, 72, 160])
    # Fine grain: cliffs, spurs, the stipple of a dry basin. Shading only.
    grain = _octaves(seed + 5_003, (FIELD_H, FIELD_W), [3, 7, 15])
    yy = np.linspace(0, 1, FIELD_H)[:, None]
    xx = np.linspace(0, 1, FIELD_W)[None, :]
    # Every view gets a different grain of ridge, but all of them tilt: a flat
    # field reads as wallpaper noise, a tilted one reads as ground.
    tilt = 0.14 + 0.06 * ((seed >> 5) % 5)
    shape = shape * 0.80 + tilt * yy + 0.07 * np.sin(xx * (1.4 + (seed >> 9) % 3) + seed % 7)
    shape = (shape - shape.min()) / max(float(shape.max() - shape.min()), 1e-6)
    if clip.scene in COASTAL:
        # A coast is a curve, not a level set. The water line is drawn as a
        # curve in x across a low band of the field rather than taken at a
        # fixed height: the noise's own range differs per seed, and a fixed
        # number gives one view a puddle and the next a continent.
        shore = (np.quantile(shape, 0.30)
                 + 0.10 * np.sin(xx * 2.2 + seed % 11)
                 + 0.05 * np.sin(xx * 4.7 + 1.3))
        signed = (shape - shore).astype(np.float32)
        # Carried explicitly from here on. Re-deriving the water later from a
        # height needs that height to survive every rescale, and the one time
        # that was tried the shoreline moved inland off the water.
        CLIP_SHORE[clip.id] = ((signed < 0).astype(np.float32), signed)
    height = np.clip(shape * 0.86 + (grain - 0.5) * 0.16, 0, 1)
    return shape, height


def _upscale(field: np.ndarray) -> np.ndarray:
    """Lift the low-resolution field to the canvas, in float."""
    img = Image.fromarray(np.asarray(field, np.float32), mode="F")
    return np.asarray(img.resize((CW, CH), Image.BICUBIC), np.float32)


def _hairline(field: np.ndarray, width_px: float) -> np.ndarray:
    """Antialiased lines of constant [width_px] where [field] crosses integers.

    Dividing the distance to the nearest integer by the local gradient turns a
    height difference into a pixel distance, which is the only way one stroke
    width survives both a plateau and a cliff.
    """
    dist = np.abs(field - np.round(field))
    gy, gx = np.gradient(field)
    slope = np.maximum(np.hypot(gx, gy), 1e-5)
    return np.clip(1 - (dist / slope) / width_px, 0, 1) ** 1.2


def base_texture(clip: Clip) -> Image.Image:
    """The still the loop drifts over: light, contours, shoreline, calm bottom.

    The field is built at full canvas resolution from a lifted low-resolution
    height field. Doing it the other way round - binarise the edges small and
    blow them up - makes dotted contour lines, which at TV distance read as
    compression artefacts rather than as a map.
    """
    seed = seed_of(clip)
    accent = np.array(accent_of(clip), np.float32)
    hour, hour_amount = HOURS.get(_catalog_view(clip).get("timeOfDay", "day"),
                                 HOURS["day"])
    hour = np.array(hour, np.float32)
    landform, height = terrain(clip)
    h = _upscale(height)

    yy = np.linspace(0, 1, CH)[:, None]
    xx = np.linspace(0, 1, CW)[None, :]

    # The field: void at the edges, a lifted navy where the light is.
    tex = np.empty((CH, CW, 3), np.float32)
    tex[:] = np.array([5, 9, 16], np.float32)
    cx = 0.34 + 0.12 * math.sin(seed % 13)
    horizon = 0.34 + 0.08 * math.cos(seed % 7)
    d = np.sqrt(((xx - cx) * 1.26) ** 2 + ((yy - horizon) * 1.04) ** 2) / 0.84
    pool = (np.clip(1 - d, 0, 1) ** 1.9)[..., None]
    tex += pool * (accent * 0.24 * (1 - hour_amount) + hour * 0.30 * hour_amount)
    # A second, cooler pool opposite it: two lights read as depth, one as a
    # spotlight.
    d2 = np.sqrt(((xx - (cx + 0.58)) * 1.1) ** 2 + ((yy - 0.70) * 1.3) ** 2) / 0.92
    tex += (np.clip(1 - d2, 0, 1) ** 2.4)[..., None] * accent * 0.09

    # Hypsometric lift: high ground is warmer and lighter, water is deeper.
    land = np.clip((h - 0.26) / 0.74, 0, 1)
    lift = (land ** (1.6 if clip.scene in COASTAL else 1.1))[..., None]
    tex += lift * (accent * 0.16 + hour * 0.10 * hour_amount
                   + np.array([18, 28, 40], np.float32))

    # Contours: where the landform crosses each band, a hairline 1.5 px wide.
    # The width has to be measured in pixels, not in height units: a height
    # band is wide where the ground is flat and narrow where it is steep, so a
    # fixed-distance band draws thick lines across a plateau and none down a
    # cliff. Dividing by the local gradient gives the same stroke everywhere -
    # and the same stroke for the shoreline, which is just the contour at the
    # water's height with a mask over it.
    land = _upscale(landform)
    # A coast already has one strong line - the shoreline - so its land needs
    # fewer contours to read; an inland view has nothing but its contours, so
    # it gets more.
    bands = (11 + (seed >> 3) % 4) if clip.scene in COASTAL else (16 + seed % 5)
    band = 1.0 / bands
    f = land / band
    lines = _hairline(f, 1.5)
    if clip.scene in COASTAL:
        # The sea keeps a hint of its own survey - shallows, a channel - at a
        # third of the strength, over a darker field: water reads as water at
        # TV distance from its value, not from its line work. Zeroing the
        # lines instead leaves half a frame empty, which is not calm, it is
        # unfinished.
        mask_small, signed_small = CLIP_SHORE[clip.id]
        water = np.clip(_upscale(mask_small), 0, 1)
        signed = _upscale(signed_small)
        lines = np.where(water > 0.5, lines * 0.34, lines)
        # Sea: a touch deeper, and a touch bluer than the land it meets, so
        # the shoreline is legible at TV distance without a colour key.
        tex = tex * (1 - 0.22 * water)[..., None]
        tex += (water * 0.10)[..., None] * np.array([10, 40, 64], np.float32)
        # One shoreline: the single zero crossing of the signed field, at
        # 2 px. Contouring the height instead would draw every band below the
        # water as well, and contouring the mask itself has no gradient to
        # measure - it is 0 or 1 everywhere but three pixels wide.
        # `signed` is the height difference to the water line, so it crosses
        # zero once: no scaling before contouring it, or the stroke multiplies
        # into every integer crossing in the field.
        lines = np.maximum(lines, _hairline(signed, 2.0))
    if clip.scene == "space":
        # Nothing to survey: a contour over a star field is a contour over
        # nothing.
        lines[:] = 0.0
    line_rgb = np.array([232, 238, 247], np.float32) * 0.34 + accent * 0.50 + hour * 0.16
    tex = tex * (1 - lines[..., None] * 0.58) + line_rgb * lines[..., None] * 0.58

    if clip.scene == "space":
        tex = _stars(tex, seed)

    # Calm: the bottom quarter stays dark and quiet for a launcher's cards.
    calm = np.broadcast_to(np.clip((yy - 0.68) / 0.32, 0, 1), (CH, CW))
    tex *= (1 - 0.62 * calm)[..., None]

    # Vignette, so the TV's own bezel does not close on a bright edge.
    vig = np.clip(1 - (((xx - 0.5) / 0.66) ** 2 + ((yy - 0.5) / 0.80) ** 2) * (1 + VIG),
                  0.18, 1)
    tex *= vig[..., None]

    return Image.fromarray(np.clip(tex, 0, 255).astype(np.uint8))


def _stars(tex: np.ndarray, seed: int) -> np.ndarray:
    """A deterministic star field: the archive view's subject is light itself."""
    rng = np.random.default_rng(seed + 991)
    count = 420
    ys = rng.integers(0, CH, count)
    xs = rng.integers(0, CW, count)
    mag = rng.random(count) ** 2.4
    colour = np.array([226, 236, 255], np.float32)
    # A 3x3 tent, so a star is a point of light at TV distance rather than a
    # pixel: a hard square reads as a dead pixel once the clip is scaled up.
    tent = np.array([[1, 2, 1], [2, 4, 2], [1, 2, 1]], np.float32) / 16.0
    for y, x, m in zip(ys, xs, mag):
        y0, x0 = max(0, y - 1), max(0, x - 1)
        patch = tex[y0:y0 + 3, x0:x0 + 3]
        if patch.shape[:2] != (3, 3):
            continue
        patch += tent[..., None] * colour * (30 + 500 * m)
    # A faint galactic band, so the field is not evenly sprinkled.
    yy = np.linspace(0, 1, CH)[:, None]
    xx = np.linspace(0, 1, CW)[None, :]
    band = np.exp(-(((yy - 0.42) - 0.18 * (xx - 0.5)) ** 2) / 0.012)
    return tex + (band * 10)[..., None]


def track_pixels(clip: Clip, points: int = 64) -> list[tuple[float, float]]:
    """The camera's real track, in canvas pixels, inset from the drift margin."""
    view = {
        "coordinates": _coordinates(clip),
        "durationSeconds": DURATION,
        "camera": _camera(clip),
    }
    move = clip.move
    cam = _camera(clip)
    start = float(cam.get("startHeightM") or 500)
    end = float(cam.get("endHeightM") or start)
    pitch = float(cam.get("pitch") or render_views.DEFAULT_PITCH.get(move, 55))
    centres = [views_geo.camera_at(view, move, DURATION * i / (points - 1),
                                   DURATION, start, end, pitch)["center"]
               for i in range(points)]
    lat0 = view["coordinates"][0]
    cos = abs(math.cos(math.radians(lat0))) or 1.0
    xs = np.array([lon * cos for lon, _ in centres])
    ys = np.array([lat for _, lat in centres])
    span = max(float(xs.max() - xs.min()), float(ys.max() - ys.min()), 1e-9) * 1.55
    mx, my = (xs.max() + xs.min()) / 2, (ys.max() + ys.min()) / 2
    # 0.74 of the canvas keeps the whole track inside the drift-safe area.
    return [(((x - mx) / span * 0.74 + 0.5) * CW, (0.5 - (y - my) / span * 0.74) * CH)
            for x, y in zip(xs, ys)]


_VIEWS: dict[str, dict] = {}


def _catalog_view(clip: Clip) -> dict:
    """The catalogue entry, so the track is the renderer's own arithmetic."""
    if not _VIEWS:
        import json
        catalog = json.loads((ROOT / "views" / "catalog.json").read_text(encoding="utf-8"))
        for view in catalog["views"]:
            _VIEWS[view["id"]] = view
    return _VIEWS[clip.view_id]


def _coordinates(clip: Clip) -> list[float]:
    return _catalog_view(clip)["coordinates"]


def _camera(clip: Clip) -> dict:
    return _catalog_view(clip).get("camera") or {}


def frame(base: Image.Image, clip: Clip, track, t: float) -> Image.Image:
    """Frame at loop position [t] in 0..1. Frame 0 and frame 1 are identical."""
    cx, cy = (CW - W) / 2, (CH - H) / 2
    # Two whole cycles across the loop: the parallax is back where it started.
    x0 = int(round(cx + cx * DRIFT * math.sin(2 * math.pi * t)))
    y0 = int(round(cy + cy * DRIFT * math.cos(2 * math.pi * t)))
    x0 = max(0, min(CW - W, x0))
    y0 = max(0, min(CH - H, y0))
    img = base.crop((x0, y0, x0 + W, y0 + H))
    draw = ImageDraw.Draw(img, "RGBA")

    if not track:
        # The archive view has no camera, so it gets no traveller: a line
        # across a field would imply a move nobody planned.
        return _term_glow(img, t)

    pts = [(x - x0, y - y0) for x, y in track]

    def at(u: float) -> tuple[float, float]:
        f = (u % 1.0) * (len(pts) - 1)
        i = min(int(f), len(pts) - 2)
        fr = f - i
        return (pts[i][0] + (pts[i + 1][0] - pts[i][0]) * fr,
                pts[i][1] + (pts[i + 1][1] - pts[i][1]) * fr)

    accent = accent_of(clip)
    head = (t * LAPS) % 1.0

    def window(u: float) -> float:
        """0 at each end of the track, 1 in the middle.

        A camera track is not a closed curve: running the traveller the whole
        way along it and letting it snap back to the start is a jump cut, and
        a jump cut is the one thing a wallpaper must not do. Fading the light
        out at the end of the track and in at the start hides the wrap -
        and because the window is 0 at both ends, frame N still equals frame 0.
        """
        return math.sin(math.pi * min(max(u, 0.0), 1.0)) ** 0.7

    # A tapered comet: the trail is drawn as segments that thin and fade, so
    # the light reads as travelling along the track rather than a painted line.
    for k in range(TRAIL, 0, -1):
        u0 = head - (k / TRAIL) * 0.24
        u1 = head - ((k - 1) / TRAIL) * 0.24
        fade = (1 - k / TRAIL) ** 2.4 * window(u1)
        width = max(2, int(11 * (1 - k / TRAIL) ** 1.6))
        draw.line([at(u0), at(u1)], fill=accent + (int(190 * fade),), width=width,
                  joint="curve")
    hx, hy = at(head)
    lit = window(head)
    for radius, alpha in ((58, 10), (40, 20), (26, 40), (15, 95), (7, 200), (3, 255)):
        draw.ellipse([hx - radius, hy - radius, hx + radius, hy + radius],
                     fill=accent + (int(alpha * lit),))
    # The start of the track stays faintly lit while the comet is away, so the
    # shape of the move is legible in every frame - faint enough that it reads
    # as a pin on a map, not as a second comet.
    sx, sy = pts[0]
    for radius, alpha in ((18, 8), (9, 14), (4, 34)):
        draw.ellipse([sx - radius, sy - radius, sx + radius, sy + radius],
                     fill=accent + (alpha,))
    return img


def _term_glow(img: Image.Image, t: float) -> Image.Image:
    """The archive view's loop: the terminator sweeping the frame, one cycle.

    A day/night boundary crossing the disc - warm at the line, blue behind it,
    a soft penumbra rather than a hard edge - because this is the one wallpaper
    whose subject is light, and the one view with no coordinates to fly.
    """
    w, h = img.size
    phase = 2 * math.pi * t
    # The line runs a whole cycle and its tilt breathes once over it, so the
    # loop closes exactly on frame N == frame 0.
    centre = 0.5 + 0.42 * math.sin(phase)
    tilt = 0.10 * math.sin(phase + math.pi / 3)

    xs = np.linspace(0, 1, w)[None, :]              # (1, w)
    ys = np.linspace(0, 1, h)[:, None]              # (h, 1)
    edge = centre + tilt * (ys - 0.5) * 2           # (h, w)

    day = np.clip((xs - edge) / 0.30 + 0.5, 0, 1)   # (h, w): 1 is daylit
    penumbra = np.clip(1 - np.abs(xs - edge) / 0.13, 0, 1) ** 2

    warm = np.array([255, 168, 96], np.float32)
    night = np.array([24, 32, 62], np.float32)

    tex = np.asarray(img, np.float32)
    tex = tex * (1 + day[..., None] * 0.50)
    tex += penumbra[..., None] * warm * 0.60
    tex += day[..., None] * warm * 0.10
    tex = tex * (1 - 0.20 * (1 - day))[..., None]
    tex += ((1 - day) * 0.16)[..., None] * night
    return Image.fromarray(np.clip(tex, 0, 255).astype(np.uint8))


def loop_frames(clip: Clip):
    """Every frame of the loop, in order, as RGB bytes for ffmpeg."""
    base = base_texture(clip)
    track = track_pixels(clip) if clip.move != "archive-timelapse" else []
    total = FPS * DURATION
    for i in range(total):
        yield frame(base, clip, track, i / total).tobytes()


def ffmpeg() -> str:
    try:
        import imageio_ffmpeg
    except ImportError:  # pragma: no cover - the CI path has it installed
        raise SystemExit("pip install imageio-ffmpeg (or pass --ffmpeg /path/to/ffmpeg)")
    return imageio_ffmpeg.get_ffmpeg_exe()


def write_loop(clip: Clip, ff: str, crf: int = 23) -> None:
    LIVE.mkdir(parents=True, exist_ok=True)
    THUMBS.mkdir(parents=True, exist_ok=True)
    APP_THUMBS.mkdir(parents=True, exist_ok=True)
    target = clip.mp4
    command = [
        ff, "-y", "-hide_banner", "-loglevel", "error",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
        "-r", str(FPS), "-i", "-",
        "-an", "-c:v", "libx264", "-profile:v", "high", "-pix_fmt", "yuv420p",
        "-crf", str(crf), "-preset", "medium", "-g", str(FPS * 2),
        "-movflags", "+faststart", str(target),
    ]
    process = subprocess.Popen(command, stdin=subprocess.PIPE)
    assert process.stdin is not None
    try:
        for raw in loop_frames(clip):
            process.stdin.write(raw)
        process.stdin.close()
    except BrokenPipeError:  # pragma: no cover
        pass
    code = process.wait()
    if code != 0:
        raise SystemExit(f"ffmpeg failed for {clip.stem} ({code})")

    # Poster frame at 1 s: the grid tile, and what the engine draws before the
    # MP4 has downloaded. Written to both the stills folder and the APK.
    base = base_texture(clip)
    track = track_pixels(clip) if clip.move != "archive-timelapse" else []
    thumb = frame(base, clip, track, 1.0 / DURATION).resize((THUMB_W, THUMB_H), Image.LANCZOS)
    thumb.save(clip.thumb, quality=88, optimize=True)
    thumb.save(clip.app_thumb, quality=88, optimize=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--all", action="store_true", help="every view (the default)")
    parser.add_argument("--only", type=int, help="one catalogue number")
    parser.add_argument("--list", action="store_true", help="print the season")
    parser.add_argument("--palette", action="store_true",
                        help="write one PNG still per view, no ffmpeg — for looking")
    parser.add_argument("--out", type=Path, default=ROOT / "build" / "coreviews-palette")
    parser.add_argument("--crf", type=int, default=23)
    parser.add_argument("--ffmpeg", help="path to ffmpeg (default: imageio-ffmpeg's)")
    args = parser.parse_args(argv)

    if args.list:
        for c in CLIPS:
            print(f"{c.number:02d}  {c.id:<32} {c.title:<16} {c.scene:<12} {c.stem}.mp4")
        print(f"{len(CLIPS)} views")
        return 0

    clips = [c for c in CLIPS if args.only in (None, c.number)]
    if not clips:
        print(f"no view {args.only}", file=sys.stderr)
        return 1

    if args.palette:
        args.out.mkdir(parents=True, exist_ok=True)
        for clip in clips:
            base = base_texture(clip)
            track = track_pixels(clip) if clip.move != "archive-timelapse" else []
            shot = frame(base, clip, track, 0.18)
            path = args.out / f"{clip.stem}.png"
            shot.save(path)
            print(f"wrote {path}")
        return 0

    ff = args.ffmpeg or ffmpeg()
    for clip in clips:
        write_loop(clip, ff, crf=args.crf)
        print(f"wrote {clip.mp4.relative_to(ROOT)} "
              f"({clip.mp4.stat().st_size / 1048576:.1f} MB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
