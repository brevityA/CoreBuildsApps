#!/usr/bin/env python3
"""
Core Builds Icon Pack — series-3-horizons, the 2.4.0 refresh.

Horizons was "one horizon, twelve meanings": the §03 semantic accent slots
as landscapes. The first cut (33-40 hand-made, 55-58 from
build_classic_wallpapers.py) drew each as a gradient, a glow line and a
dot - which at TV distance is a line and a dot. This renderer keeps every
wall's number, slug, name and accent, and gives each one a place to be:
a sky graded from the accent down to the night ground, a sun or moon on
the line with its own atmosphere, drifting cloud bands, a star field that
thins toward the glow, and three to five ridgelines of fractional-Brownian
terrain stepping forward into haze. All procedural, all seeded; a re-run
is byte-identical.

House rules (Wallpapers/README.md): dark-coverage floor, a calm lower
third for launcher cards (the nearest ridge is a flat silhouette fading to
void), no grain.

Writes:
  Wallpapers/series-3-horizons/corebuilds-NN-slug.png   3840x2160
  Wallpapers/thumbs/corebuilds-NN-slug.jpg              480x270 (+ app copy)
  docs/horizons-wallpapers.png                          contact sheet

Does NOT rewrite the manifest - the entries already exist; only the bytes
change.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_classic_wallpapers import (  # noqa: E402
    H,
    NIGHT,
    VOID,
    W,
    dark_coverage,
    save_wall,
)

ROOT = Path(__file__).resolve().parent.parent
SERIES = ROOT / "Wallpapers" / "series-3-horizons"
CONTACT_SHEET = ROOT / "docs" / "horizons-wallpapers.png"


# ------------------------------------------------------------------ noise ---

def _value_noise_1d(seed: int, n: int, scale: float, octaves: int = 5,
                    gain: float = 0.5) -> np.ndarray:
    """Seeded 1-D fBm in [0, 1] with n samples: a ridgeline profile."""
    rnd = np.random.RandomState(seed)
    acc = np.zeros(n, dtype="float32")
    amp, total, cell = 1.0, 0.0, scale
    for _ in range(octaves):
        k = max(2, int(n / cell) + 2)
        pts = rnd.rand(k).astype("float32")
        xs = np.linspace(0, k - 1, n, dtype="float32")
        i0 = np.floor(xs).astype(int)
        i1 = np.minimum(i0 + 1, k - 1)
        t = xs - i0
        t = t * t * (3 - 2 * t)  # smoothstep
        acc += (pts[i0] * (1 - t) + pts[i1] * t) * amp
        total += amp
        amp *= gain
        cell = max(2.0, cell * 0.5)
    acc /= total
    lo, hi = float(acc.min()), float(acc.max())
    return (acc - lo) / max(1e-6, hi - lo)


def _fbm2d(seed: int, scale: float, octaves: int = 5, gain: float = 0.5,
           w: int = W, h: int = H) -> np.ndarray:
    """Seeded 2-D fBm in [0, 1] (coarse random grids, bicubic upsampled)."""
    rnd = np.random.RandomState(seed)
    acc = np.zeros((h, w), dtype="float32")
    amp, total, cell = 1.0, 0.0, scale
    for _ in range(octaves):
        gw = max(2, int(math.ceil(w / cell)) + 1)
        gh = max(2, int(math.ceil(h / cell)) + 1)
        grid = rnd.rand(gh, gw).astype("float32")
        acc += np.asarray(Image.fromarray(grid, mode="F").resize((w, h), Image.BICUBIC),
                          dtype="float32") * amp
        total += amp
        amp *= gain
        cell = max(2.0, cell * 0.5)
    acc /= total
    lo, hi = float(acc.min()), float(acc.max())
    return (acc - lo) / max(1e-6, hi - lo)


# ---------------------------------------------------------------- scene ---

def _mix(a, b, t):
    return tuple(a[i] * (1 - t) + b[i] * t for i in range(3))


def horizon(seed: int, accent: tuple, *, horizon_frac: float = 0.56,
            body: str = "sun", body_x: float = 0.5, body_r: float = 150.0,
            body_color: tuple | None = None, sky_strength: float = 0.55,
            ridges: int = 4, roughness: float = 0.5, stars: int = 1400,
            clouds: float = 0.5, haze: float = 0.6) -> Image.Image:
    """One horizon scene. Every number here is data the wall table owns."""
    acc = np.array(accent, dtype="float32")
    void = np.array(VOID, dtype="float32")
    night = np.array(NIGHT, dtype="float32")
    hy = H * horizon_frac
    yy, xx = np.mgrid[0:H, 0:W].astype("float32")

    # Sky: void at the zenith, through night, into the accent at the line.
    t = np.clip(yy / hy, 0, 1)
    sky = void[None, None, :] * (1 - t)[..., None] ** 1.0
    sky = sky + night[None, None, :] * (t * (1 - t) * 1.6)[..., None]
    glow = np.power(t, 2.6)[..., None] * sky_strength
    arr = sky * (1 - glow) + acc[None, None, :] * glow
    # Horizontal falloff away from the body: the glow is a sunset, not a strip.
    dxn = np.abs(xx / W - body_x)
    arr = arr * (1 - 0.35 * np.clip(dxn * 1.6, 0, 1))[..., None]

    # Stars thin out toward the glow.
    rnd = np.random.RandomState(seed)
    sx = rnd.randint(0, W, stars)
    sy = (np.abs(rnd.normal(0.0, 0.42, stars)) % 1.0 * hy * 0.92).astype(int)
    sb = rnd.uniform(0.15, 1.0, stars).astype("float32") * (1 - sy / hy) ** 1.4
    star_col = np.array((220, 232, 255), dtype="float32")
    arr[sy, sx] = np.minimum(255, arr[sy, sx] + sb[:, None] * star_col)
    big = sb > 0.75
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        bx = np.clip(sx[big] + dx, 0, W - 1)
        by = np.clip(sy[big] + dy, 0, H - 1)
        arr[by, bx] = np.minimum(255, arr[by, bx] + sb[big][:, None] * star_col * 0.45)

    # Clouds: stretched fBm bands lit from the body's side, fading up.
    if clouds > 0:
        cl = _fbm2d(seed + 11, 700.0, 5, w=W // 4, h=H // 4)
        cl = np.asarray(Image.fromarray(cl, mode="F").resize((W, H), Image.BICUBIC),
                        dtype="float32")
        band = np.exp(-((yy - hy * 0.72) / (hy * 0.26)) ** 2)
        lit = np.clip(cl - 0.45, 0, 1) * band * clouds
        cloud_col = _mix(accent, (255, 236, 220), 0.35)
        arr = arr + lit[..., None] * np.array(cloud_col, dtype="float32") * 0.9
        shade = np.clip(0.55 - cl, 0, 1) * band * clouds * 0.5
        arr = arr * (1 - shade[..., None] * 0.6)

    # The body: sun (a lit disc with corona) or moon (a pale disc, soft).
    bx, by = W * body_x, hy - H * 0.095 - body_r * 0.15  # clears the far ridge
    d = np.sqrt((xx - bx) ** 2 + (yy - by) ** 2)
    bcol = np.array(body_color or _mix(accent, (255, 250, 240), 0.55), dtype="float32")
    if body == "sun":
        corona = np.exp(-(d / (body_r * 3.2)) ** 1.4) * 0.75
        arr = arr + corona[..., None] * acc[None, None, :]
        disc = np.clip((body_r - d) / 3.0 + 0.5, 0, 1)
        arr = arr * (1 - disc[..., None]) + bcol[None, None, :] * disc[..., None]
    elif body == "moon":
        halo = np.exp(-(d / (body_r * 2.4)) ** 1.6) * 0.35
        arr = arr + halo[..., None] * np.array((190, 210, 240), dtype="float32")
        disc = np.clip((body_r - d) / 3.0 + 0.5, 0, 1)
        shade = np.clip(0.75 + 0.5 * (xx - bx) / body_r, 0.25, 1.0)
        mcol = np.array((214, 224, 240), dtype="float32")
        arr = arr * (1 - disc[..., None]) + (mcol * shade[..., None]) * disc[..., None]
    # Below the line the sky mirrors into a dark ground plane briefly, so a
    # ridge gap never shows raw void. (The ridges cover almost all of it.)
    below = yy > hy
    arr[below] = (night * 0.6 + acc * 0.08)[None, :]

    # Ridges: far to near, each a 1-D fBm profile; the farther, the paler
    # (atmospheric perspective toward the accent) and the lower on the frame.
    ridge_col_far = np.array(_mix(_mix(accent, NIGHT, 0.55), VOID, 0.2), dtype="float32")
    ridge_col_near = void
    for k in range(ridges):
        f = k / max(1, ridges - 1)          # 0 far, 1 near
        prof = _value_noise_1d(seed + 100 + k, W, 900.0 - 500.0 * f, 6, 0.42 + 0.18 * roughness)
        amp = H * (0.07 + 0.11 * f) * (0.6 + 0.8 * roughness)
        base = hy - H * 0.035 + H * 0.085 * f
        top = base - prof * amp
        col = ridge_col_far * (1 - f * haze) + ridge_col_near * f * haze
        mask = np.clip((yy - top[None, :]) / 2.0 + 0.5, 0, 1)
        # Edge light: the rim nearest the sky picks up the glow.
        rim = np.exp(-np.clip(yy - top[None, :], 0, None) / 14.0) * (1 - f) * 0.5
        arr = arr * (1 - mask[..., None]) + col[None, None, :] * mask[..., None]
        arr = arr + (rim * mask)[..., None] * acc[None, None, :] * 0.45

    # Calm lower third: fade to void under the nearest ridge.
    y0 = int(H * 0.70)
    tt = np.linspace(0, 0.9, H - y0, dtype="float32")[:, None, None]
    arr[y0:] = arr[y0:] * (1 - tt) + void[None, None, :] * tt
    return Image.fromarray(np.clip(arr, 0, 255).astype("uint8"))


# ---------------------------------------------------------------- walls ---
# (number, slug, title, kwargs) - accents sampled from the 1.x files so each
# wall keeps the colour it has carried since the series shipped.

WALLS = [
    (33, "horizon-cyan", "33 Horizon Cyan",
     dict(seed=3301, accent=(1, 202, 230), body="sun", body_x=0.46, body_r=170, ridges=4)),
    (34, "horizon-ember", "34 Horizon Ember",
     dict(seed=3401, accent=(217, 91, 53), body="sun", body_x=0.62, body_r=210,
          sky_strength=0.6, clouds=0.7, roughness=0.65)),
    (35, "horizon-rose", "35 Horizon Rose",
     dict(seed=3501, accent=(217, 102, 163), body="sun", body_x=0.38, body_r=150,
          clouds=0.8, ridges=3, roughness=0.35)),
    (36, "horizon-labs", "36 Horizon Labs",
     dict(seed=3601, accent=(102, 88, 156), body="moon", body_x=0.70, body_r=120,
          sky_strength=0.45, stars=2400, clouds=0.3, ridges=5, roughness=0.7)),
    (37, "horizon-verified", "37 Horizon Verified",
     dict(seed=3701, accent=(46, 184, 135), body="sun", body_x=0.54, body_r=130,
          sky_strength=0.5, clouds=0.4, ridges=4)),
    (38, "horizon-amber", "38 Horizon Amber",
     dict(seed=3801, accent=(190, 140, 36), body="sun", body_x=0.30, body_r=240,
          sky_strength=0.55, clouds=0.6, ridges=3, roughness=0.3)),
    (39, "horizon-slate", "39 Horizon Slate",
     dict(seed=3901, accent=(143, 152, 161), body="moon", body_x=0.50, body_r=110,
          sky_strength=0.4, stars=1800, clouds=0.5, ridges=5, roughness=0.55)),
    (40, "horizon-void", "40 Horizon Void",
     dict(seed=4001, accent=(64, 126, 156), body="none", body_x=0.5,
          sky_strength=0.45, stars=3200, clouds=0.2, ridges=5, roughness=0.8)),
    (55, "horizon-dusk", "55 Horizon Dusk",
     dict(seed=5501, accent=(146, 83, 166), body="sun", body_x=0.58, body_r=180,
          sky_strength=0.55, clouds=0.6, ridges=4, roughness=0.5)),
    (56, "horizon-frost", "56 Horizon Frost",
     dict(seed=5601, accent=(103, 140, 168), body="moon", body_x=0.36, body_r=140,
          sky_strength=0.5, stars=2000, clouds=0.35, ridges=4, roughness=0.75)),
    (57, "horizon-signal", "57 Horizon Signal",
     dict(seed=5701, accent=(0, 212, 255), body="sun", body_x=0.50, body_r=120,
          sky_strength=0.45, clouds=0.3, ridges=5, roughness=0.6)),
    (58, "horizon-ember-deep", "58 Horizon Ember Deep",
     dict(seed=5801, accent=(150, 56, 24), body="sun", body_x=0.66, body_r=260,
          sky_strength=0.65, clouds=0.75, ridges=4, roughness=0.45)),
]


def _contact_sheet(rendered: list[tuple[int, str, Image.Image]]) -> None:
    cols, tw, th, cap = 2, 430, 242, 20
    rows = (len(rendered) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * tw, rows * (th + cap)), VOID)
    dr = ImageDraw.Draw(sheet)
    for i, (num, title, im) in enumerate(rendered):
        x, y = (i % cols) * tw, (i // cols) * (th + cap)
        sheet.paste(im.resize((tw, th), Image.LANCZOS), (x, y))
        dr.text((x + 6, y + th + 4), title, fill=(200, 210, 225))
    CONTACT_SHEET.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(CONTACT_SHEET, optimize=True)


def main() -> int:
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", type=int, action="append")
    args = parser.parse_args()
    rendered, total = [], 0
    for num, slug, title, kw in WALLS:
        stem = f"corebuilds-{num}-{slug}"
        if args.only and num not in args.only:
            rendered.append((num, title, Image.open(SERIES / f"{stem}.png").convert("RGB")))
            continue
        im = horizon(**kw)
        dc = dark_coverage(im)
        assert dc >= 0.45, f"{stem}: dark coverage {dc:.0%} below the 45% floor"
        total += save_wall(im, SERIES, stem)
        rendered.append((num, title, im))
    _contact_sheet(rendered)
    print(f"  contact sheet: {CONTACT_SHEET.relative_to(ROOT)}")
    print(f"\nseries-3-horizons complete — {len(WALLS)} walls at 4K, "
          f"{total / 1024 / 1024:.1f} MB.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
