#!/usr/bin/env python3
"""
Core Builds Icon Pack — series-2-motion, walls 25–32 (the 2.4.0 refresh).

The first eight Motion walls were hand-made exports with no generator in the
repo: thin single-tone lines on the night ground, flat at TV distance. This
tool re-authors the same eight ideas - Orbitals, Warp, Fogbanks, Spiral,
Slipstream, Embers, Tracer, Pendulum - keeping each wall's number, name and
subject, as long-exposure light: every trail has a hot core inside a wide
glow, a bright head where the light is now, depth from layers at different
blur, and a calm lower third for launcher cards. All procedural and seeded;
a re-run is byte-identical. Walls 51–54 of the same series come from
`tools/build_classic_wallpapers.py` and are unchanged.

Writes:
  Wallpapers/series-2-motion/corebuilds-NN-slug.png   3840x2160
  Wallpapers/thumbs/corebuilds-NN-slug.jpg            480x270 (+ app copy)
  docs/motion-wallpapers.png                          contact sheet
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_classic_wallpapers import (  # noqa: E402
    BUILD_BLUE,
    CYAN,
    DUSK,
    EMBER,
    GLOW_CYAN,
    H,
    VOID,
    W,
    dark_coverage,
    save_wall,
)
from build_horizons_wallpapers import _fbm2d  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-2-motion"
CONTACT_SHEET = ROOT / "docs" / "motion-wallpapers.png"

VIOLET = (140, 90, 230)
FLAME = (255, 150, 70)
WHITE = (235, 245, 255)
TWO_PI = 2 * math.pi


# ------------------------------------------------------------- toolkit ---

def _col(c):
    return np.array(c, dtype="float32").reshape(1, 1, 3)


class Trails:
    """Light drawn as long exposure: a mask per intensity tier, composited as
    core + near glow + far glow so lines read as emitted light, not ink."""

    def __init__(self):
        self.layers: list[tuple[Image.Image, tuple, float, float]] = []

    def mask(self) -> tuple[Image.Image, ImageDraw.ImageDraw]:
        m = Image.new("L", (W, H), 0)
        return m, ImageDraw.Draw(m)

    def add(self, mask: Image.Image, color, far: float = 0.6, core: float = 1.0):
        self.layers.append((mask, color, far, core))

    def render(self, base: np.ndarray | None = None) -> np.ndarray:
        arr = base if base is not None else np.zeros((H, W, 3), "float32") + _col(VOID)
        for mask, color, far, core in self.layers:
            m = np.asarray(mask, "float32") / 255
            near = np.asarray(mask.filter(ImageFilter.GaussianBlur(6)), "float32") / 255
            small = mask.resize((W // 8, H // 8), Image.BOX).filter(ImageFilter.GaussianBlur(14))
            wide = np.asarray(small.resize((W, H), Image.BILINEAR), "float32") / 255
            c = _col(color)
            arr = arr + wide[..., None] * c * far * 1.6 + near[..., None] * c * 0.9
            arr = arr + (m ** 1.5)[..., None] * (c * 0.35 + _col(WHITE) * 0.65) * core
        return arr


def _calm(arr: np.ndarray, start: float = 0.64, depth: float = 0.8) -> np.ndarray:
    y0 = int(H * start)
    t = (np.linspace(0, 1, H - y0, dtype="float32") ** 1.3 * depth)[:, None, None]
    arr[y0:] = arr[y0:] * (1 - t) + _col(VOID) * t
    return arr


def _ambient(seed: int, color, gain: float = 1.0, scale: float = 1800) -> np.ndarray:
    """A slow fBm wash of one colour over the night ground: the room the light is in."""
    f = _fbm2d(seed, scale, 4, w=W // 8, h=H // 8)
    f = np.asarray(Image.fromarray(f, mode="F").resize((W, H), Image.BICUBIC), "float32")
    return _col(VOID) + (f ** 2)[..., None] * _col(color) * 0.16 * gain


def _stars(seed: int, n: int, ymax: float = 0.7) -> Image.Image:
    rnd = np.random.RandomState(seed)
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    for _ in range(n):
        x, y = rnd.uniform(0, W), rnd.uniform(0, H * ymax)
        r = rnd.uniform(1.0, 2.6)
        d.ellipse((x - r, y - r, x + r, y + r), fill=int(rnd.uniform(60, 255)))
    return m


def _vignette(arr: np.ndarray, strength: float = 0.4) -> np.ndarray:
    yy, xx = np.mgrid[0:H, 0:W].astype("float32")
    d = ((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2
    return arr * (1 - strength * np.clip(d, 0, 1) ** 1.5)[..., None]


def _finish(arr: np.ndarray) -> Image.Image:
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))


def _head(d: ImageDraw.ImageDraw, x: float, y: float, r: float):
    d.ellipse((x - r, y - r, x + r, y + r), fill=255)


# --------------------------------------------------------------- walls ---

def w25_orbitals() -> Image.Image:
    """Bodies on tilted orbits around one bright core; each trail brightens to its head."""
    rnd = np.random.RandomState(2501)
    cx, cy = W * 0.5, H * 0.42
    tr = Trails()
    rings, heads = tr.mask()[0], tr.mask()[0]
    rd, hd = ImageDraw.Draw(rings), ImageDraw.Draw(heads)
    for k in range(9):
        a = 180 + k * 190 + rnd.uniform(-30, 30)
        b = a * rnd.uniform(0.28, 0.5)
        tilt = rnd.uniform(-0.5, 0.5)
        phase = rnd.uniform(0, TWO_PI)
        sweep = rnd.uniform(0.55, 0.95) * TWO_PI
        prev = None
        n = 700
        for i in range(n):
            t = i / (n - 1)
            th = phase + t * sweep
            x0, y0 = a * math.cos(th), b * math.sin(th)
            x = cx + x0 * math.cos(tilt) - y0 * math.sin(tilt)
            y = cy + x0 * math.sin(tilt) + y0 * math.cos(tilt)
            if prev is not None:
                rd.line([prev, (x, y)], fill=int(60 + 195 * t ** 1.8), width=int(3 + 5 * t))
            prev = (x, y)
        _head(hd, x, y, 7 + k % 3 * 2)
    tr.add(rings, CYAN if True else BUILD_BLUE, far=0.5, core=0.8)
    tr.add(heads, GLOW_CYAN, far=0.9, core=1.3)
    core = tr.mask()[0]
    _head(ImageDraw.Draw(core), cx, cy, 26)
    tr.add(core, WHITE, far=1.6, core=1.0)
    tr.add(_stars(2502, 500), (170, 200, 255), far=0.1, core=0.6)
    arr = tr.render(_ambient(2503, BUILD_BLUE, 1.2))
    return _finish(_calm(_vignette(arr), 0.66))


def w26_warp() -> Image.Image:
    """The jump: star streaks from an off-centre point, longer and hotter toward the rim."""
    rnd = np.random.RandomState(2601)
    cx, cy = W * 0.56, H * 0.40
    tr = Trails()
    far_m, mid_m, near_m = tr.mask()[0], tr.mask()[0], tr.mask()[0]
    fd, md, nd = (ImageDraw.Draw(m) for m in (far_m, mid_m, near_m))
    for i in range(750):
        th = rnd.uniform(0, TWO_PI)
        r0 = rnd.uniform(60, 1500) ** 1.0
        depth = rnd.rand()
        length = (40 + r0 * 0.55) * (0.4 + depth)
        x0, y0 = cx + r0 * math.cos(th), cy + r0 * math.sin(th)
        x1, y1 = x0 + length * math.cos(th), y0 + length * math.sin(th)
        d, wdt = (nd, 3) if depth > 0.8 else (md, 2) if depth > 0.4 else (fd, 1)
        d.line([(x0, y0), (x1, y1)], fill=int(40 + 215 * depth ** 2), width=wdt)
        if depth > 0.8:
            _head(d, x1, y1, 3.5)
    tr.add(far_m, BUILD_BLUE, far=0.15, core=0.3)
    tr.add(mid_m, CYAN, far=0.3, core=0.6)
    tr.add(near_m, GLOW_CYAN, far=0.7, core=1.2)
    bloom = tr.mask()[0]
    _head(ImageDraw.Draw(bloom), cx, cy, 90)
    tr.add(bloom, (120, 180, 255), far=2.4, core=0.0)
    arr = tr.render(_ambient(2602, VIOLET, 1.0))
    return _finish(_calm(_vignette(arr, 0.5), 0.68))


def w27_fogbanks() -> Image.Image:
    """Fog in layers, cyan ahead and violet behind, with light shafts coming down through it."""
    arr = np.zeros((H, W, 3), "float32") + _col(VOID)
    yy = np.linspace(0, 1, H, dtype="float32")[:, None]
    for seed, color, band_y, band_w, gain in (
        (2701, VIOLET, 0.22, 0.26, 0.55),
        (2702, (40, 90, 160), 0.40, 0.22, 0.7),
        (2703, CYAN, 0.52, 0.16, 0.6),
        (2704, GLOW_CYAN, 0.60, 0.08, 0.35),
    ):
        f = _fbm2d(seed, 1400, 5, w=W // 4, h=H // 4)
        f = np.asarray(Image.fromarray(f, mode="F").resize((W // 6, H // 4), Image.BILINEAR)
                       .resize((W, H), Image.BICUBIC), "float32")
        band = np.exp(-((yy - band_y) / band_w) ** 2)
        arr = arr + (np.clip(f - 0.2, 0, 1) ** 1.3 * band)[..., None] * _col(color) * gain * 0.95
    # light shafts: soft vertical columns from the top, brighter where the fog is
    tr = Trails()
    shafts = tr.mask()[0]
    sd = ImageDraw.Draw(shafts)
    rnd = np.random.RandomState(2705)
    for _ in range(9):
        x = rnd.uniform(W * 0.3, W * 0.8)
        wdt = rnd.uniform(4, 22)
        sd.polygon([(x - wdt, -10), (x + wdt, -10), (x + wdt * 2.5 + 260, H * 0.75), (x - wdt * 2.5 + 260, H * 0.75)],
                   fill=int(rnd.uniform(30, 80)))
    shafts = shafts.filter(ImageFilter.GaussianBlur(40))
    fogmask = np.clip((arr.sum(axis=2) - 26) / 80, 0.15, 1.0)
    arr = arr + (np.asarray(shafts, "float32") / 255 * (1 - yy) ** 1.2 * fogmask)[..., None] * _col(GLOW_CYAN) * 0.7
    tr.add(_stars(2706, 260, 0.35), (200, 220, 255), far=0.05, core=0.4)
    arr = tr.render(arr)
    return _finish(_calm(_vignette(arr, 0.35), 0.66, 0.75))


def w28_spiral() -> Image.Image:
    """A logarithmic spiral of light, four arms of points brightening toward a white core."""
    rnd = np.random.RandomState(2801)
    cx, cy = W * 0.5, H * 0.44
    tr = Trails()
    dots, arms = tr.mask()[0], tr.mask()[0]
    dd, ad = ImageDraw.Draw(dots), ImageDraw.Draw(arms)
    for arm in range(4):
        off = arm * TWO_PI / 4
        prev = None
        for i in range(900):
            t = i / 899
            th = off + t * 9.5
            r = 40 * math.exp(0.36 * (th - off)) + 30
            if r > 1600:
                break
            x, y = cx + r * math.cos(th), cy + r * math.sin(th) * 0.72
            if prev is not None:
                ad.line([prev, (x, y)], fill=int(200 * (1 - t) ** 1.2 + 40), width=int(9 - 6 * t))
            prev = (x, y)
            if i % 3 == 0:
                for _ in range(2):
                    sx = x + rnd.normal(0, 14 + 60 * t)
                    sy = y + rnd.normal(0, 10 + 40 * t)
                    _head(dd, sx, sy, rnd.uniform(1.2, 3.2) * (1.4 - t))
    tr.add(arms, CYAN, far=0.7, core=0.6)
    tr.add(dots, GLOW_CYAN, far=0.3, core=1.0)
    core = tr.mask()[0]
    _head(ImageDraw.Draw(core), cx, cy, 34)
    tr.add(core, WHITE, far=2.0, core=1.0)
    tr.add(_stars(2802, 400), (190, 210, 255), far=0.1, core=0.5)
    arr = tr.render(_ambient(2803, DUSK, 1.0))
    return _finish(_calm(_vignette(arr), 0.66))


def w29_slipstream() -> Image.Image:
    """Streamlines parting around a lit sphere, compressed and brightest at its shoulders."""
    cx, cy, R = W * 0.44, H * 0.40, 300.0
    tr = Trails()
    lines, hot = tr.mask()[0], tr.mask()[0]
    ld, hd = ImageDraw.Draw(lines), ImageDraw.Draw(hot)
    rnd = np.random.RandomState(2901)
    for k in range(64):
        y0 = H * 0.02 + k * (H * 0.78 / 64) + rnd.uniform(-4, 4)
        psi = (y0 - cy)                      # stream function value, far upstream
        if abs(psi) < 6:
            continue
        # integrate the streamline through the potential-flow velocity field
        x, y = -40.0, float(y0)
        prev = (x, y)
        for _ in range(900):
            dx, dy = x - cx, y - cy
            r2 = dx * dx + dy * dy
            u = 1 - R * R * (dx * dx - dy * dy) / (r2 * r2)
            v = -2 * R * R * dx * dy / (r2 * r2)
            speed = math.hypot(u, v)
            step = 6.0 / max(speed, 0.2)
            x, y = x + u * step, y + v * step
            if x > W + 40 or not (-50 < y < H + 50):
                break
            ld.line([prev, (x, y)], fill=int(min(255, 40 + 140 * speed ** 2)), width=2)
            if speed > 1.3:
                hd.line([prev, (x, y)], fill=int(min(255, 300 * (speed - 1.25))), width=3)
            prev = (x, y)
    tr.add(lines, BUILD_BLUE, far=0.35, core=0.55)
    tr.add(hot, GLOW_CYAN, far=0.6, core=1.0)
    body, rim = tr.mask()[0], tr.mask()[0]
    _head(ImageDraw.Draw(rim), cx, cy, R)
    rim = rim.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.GaussianBlur(1.5))
    tr.add(rim, CYAN, far=1.2, core=1.2)
    arr = tr.render(_ambient(2902, BUILD_BLUE, 1.1))
    disc = np.asarray(_disc_mask(cx, cy, R), "float32")[..., None] / 255
    arr = arr * (1 - disc * 0.92) + disc * _col((10, 16, 30))
    return _finish(_calm(_vignette(arr, 0.35), 0.66))


def _disc_mask(cx, cy, r):
    m = Image.new("L", (W, H), 0)
    _head(ImageDraw.Draw(m), cx, cy, r)
    return m


def w30_embers() -> Image.Image:
    """Embers rising from a fire below the frame: hot heads, blurred trails, warm air."""
    rnd = np.random.RandomState(3001)
    tr = Trails()
    far_m, near_m, heads = tr.mask()[0], tr.mask()[0], tr.mask()[0]
    fd, nd, hd = (ImageDraw.Draw(m) for m in (far_m, near_m, heads))
    for i in range(520):
        depth = rnd.rand() ** 1.5
        x = rnd.uniform(-100, W + 100)
        y = rnd.uniform(H * 0.05, H * 0.85) ** 1.0
        length = 20 + 160 * depth * rnd.uniform(0.4, 1.4)
        drift = rnd.uniform(-0.35, 0.35)
        pts = [(x + drift * s + 5 * math.sin(s / 60 + i), y + s) for s in np.linspace(0, length, 12)]
        d, wdt = (nd, 3) if depth > 0.6 else (fd, 1)
        for a, b in zip(pts, pts[1:]):
            f = 1 - (pts.index(a) / 11)
            d.line([a, b], fill=int(255 * (0.25 + 0.75 * f ** 2) * (0.5 + 0.5 * depth)), width=wdt)
        if depth > 0.6:
            _head(hd, x, y, 2.5 + 3 * depth)
    tr.add(far_m, EMBER, far=0.4, core=0.5)
    tr.add(near_m, FLAME, far=0.6, core=0.9)
    tr.add(heads, (255, 230, 170), far=0.5, core=1.3)
    arr = _ambient(3002, EMBER, 1.6, 1400)
    yy = np.linspace(0, 1, H, dtype="float32")[:, None, None]
    arr = arr + (yy ** 3.2) * _col(FLAME) * 0.42         # the fire below the frame
    arr = tr.render(arr)
    return _finish(_calm(_vignette(arr, 0.35), 0.70, 0.7))


def w31_tracer() -> Image.Image:
    """One light, one path: loops and a long run, with ghost passes fading behind it."""
    tr = Trails()
    rnd = np.random.RandomState(3101)

    def path(n=2400, ghost=0.0):
        pts = []
        for i in range(n):
            t = i / (n - 1)
            loop = 170 * (0.4 + 0.6 * math.sin(t * math.pi))          # loop radius swells mid-run
            x = W * 0.08 + t * W * 0.86 + loop * math.cos(t * 44.0) + ghost * 50 * math.sin(t * 5)
            y = (H * 0.40 + 230 * math.sin(t * 5.1 + 0.8) + loop * 0.7 * math.sin(t * 44.0)
                 + ghost * 80)
            pts.append((x, y))
        return pts

    for g, (color, far, core) in enumerate(((DUSK, 0.18, 0.15), (BUILD_BLUE, 0.25, 0.25))):
        m = tr.mask()[0]
        d = ImageDraw.Draw(m)
        p = path(ghost=(g + 1) * (1 if g % 2 else -1))
        d.line(p, fill=120, width=3)
        tr.add(m.filter(ImageFilter.GaussianBlur(4 + g * 3)), color, far=far, core=core)
    main_m = tr.mask()[0]
    md = ImageDraw.Draw(main_m)
    p = path()
    for i, (a, b) in enumerate(zip(p, p[1:])):
        t = i / (len(p) - 2)
        md.line([a, b], fill=int(90 + 165 * t ** 1.2), width=int(4 + 5 * t))
    tr.add(main_m, CYAN, far=1.3, core=1.2)
    head = tr.mask()[0]
    _head(ImageDraw.Draw(head), *p[-1], 16)
    tr.add(head, WHITE, far=1.8, core=1.0)
    spark = tr.mask()[0]
    sd = ImageDraw.Draw(spark)
    for _ in range(60):
        k = rnd.randint(len(p) // 2, len(p))
        x, y = p[k]
        _head(sd, x + rnd.normal(0, 30), y + rnd.normal(0, 30), rnd.uniform(1, 3))
    tr.add(spark, GLOW_CYAN, far=0.2, core=1.0)
    arr = tr.render(_ambient(3102, BUILD_BLUE, 1.0))
    return _finish(_calm(_vignette(arr), 0.66))


def w32_pendulum() -> Image.Image:
    """A harmonograph: two pendulums decaying, the pen's path shading cyan to violet as it slows."""
    tr = Trails()
    cx, cy = W * 0.5, H * 0.40
    n = 14000
    c_early, c_late = tr.mask()[0], tr.mask()[0]
    de, dl = ImageDraw.Draw(c_early), ImageDraw.Draw(c_late)
    prev = None
    for i in range(n):
        t = i / 60.0
        decay = math.exp(-t / 95.0)
        x = cx + decay * (980 * math.sin(2.01 * t + 1.2) + 380 * math.sin(3.0 * t))
        y = cy + decay * (560 * math.sin(3.0 * t + 0.4) + 260 * math.sin(2.0 * t + 2.1))
        if prev is not None:
            u = i / n
            if u < 0.5:
                de.line([prev, (x, y)], fill=int(60 + 120 * (1 - u)), width=3)
            else:
                dl.line([prev, (x, y)], fill=int(90 + 150 * u), width=3)
        prev = (x, y)
    tr.add(c_early, CYAN, far=0.35, core=0.45)
    tr.add(c_late, VIOLET, far=0.6, core=0.7)
    pen = tr.mask()[0]
    _head(ImageDraw.Draw(pen), *prev, 12)
    tr.add(pen, WHITE, far=1.4, core=1.0)
    arr = tr.render(_ambient(3201, DUSK, 1.0))
    return _finish(_calm(_vignette(arr), 0.68))


WALLS = [
    (25, "orbitals", "25 Orbitals", w25_orbitals),
    (26, "warp", "26 Warp", w26_warp),
    (27, "fogbanks", "27 Fogbanks", w27_fogbanks),
    (28, "spiral", "28 Spiral", w28_spiral),
    (29, "slipstream", "29 Slipstream", w29_slipstream),
    (30, "embers", "30 Embers", w30_embers),
    (31, "tracer", "31 Tracer", w31_tracer),
    (32, "pendulum", "32 Pendulum", w32_pendulum),
]
# The whole series, for the contact sheet (51–54 are rendered by build_classic_wallpapers.py).
SHEET_ORDER = WALLS + [(51, "vortex", "51 Vortex", None), (52, "shockwave", "52 Shockwave", None),
                       (53, "cascade", "53 Cascade", None), (54, "drift-field", "54 Drift Field", None)]


def _contact_sheet(rendered: dict):
    cols, tw, th, cap = 4, 430, 242, 20
    rows = (len(SHEET_ORDER) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * tw, rows * (th + cap)), VOID)
    dr = ImageDraw.Draw(sheet)
    for i, (num, slug, title, _) in enumerate(SHEET_ORDER):
        im = rendered.get(num) or Image.open(SERIES / f"corebuilds-{num}-{slug}.png").convert("RGB")
        x, y = (i % cols) * tw, (i // cols) * (th + cap)
        sheet.paste(im.resize((tw, th), Image.LANCZOS), (x, y))
        dr.text((x + 6, y + th + 4), title, fill=(200, 210, 225))
    sheet.save(CONTACT_SHEET, optimize=True)


def main() -> int:
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", type=int, action="append")
    args = parser.parse_args()
    SERIES.mkdir(parents=True, exist_ok=True)
    rendered, total = {}, 0
    for num, slug, title, fn in WALLS:
        if args.only and num not in args.only:
            continue
        im = fn()
        dc = dark_coverage(im)
        assert dc >= 0.45, f"{num}: dark coverage {dc:.0%} below the 45% floor"
        total += save_wall(im, SERIES, f"corebuilds-{num}-{slug}")
        rendered[num] = im
    _contact_sheet(rendered)
    print(f"  contact sheet: {CONTACT_SHEET.relative_to(ROOT)}")
    print(f"\nseries-2-motion 25–32 complete — {len(rendered)} walls at 4K, {total / 1024 / 1024:.1f} MB.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
