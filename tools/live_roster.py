#!/usr/bin/env python3
"""The Core Builds Motion live-wallpaper roster — one list, every consumer.

Every loop in `Motion/live/` is the moving form of a committed still: this
is the only place that says which still, under which clip number, in which
set. `build_still_loops.py` renders from it, `build_cinema_wallpapers.py`
numbers its own loops from it, `build_motion_feed.py` writes the feed from
it, and `tests/test_live_wallpaper.py` holds `LiveLoop.kt` to it.

Clip numbers are URLs (raw GitHub) that shipped app builds hard-code, so a
number is never reused for a different scene and existing numbers are never
renamed. The original clips 01–10 were ffmpeg test patterns (gradients,
cellauto, mandelbrot, sierpinski) and were retired in 2.4.0; no app build
ever listed them, so their numbers simply stay empty.

Sets, in browser order (the Live chip shows them in this order):

  Motion      32–43  series-2-motion walls 25–32, 51–54      build_still_loops
  Deep Space  11–22  series-9-deep-space walls 85–96          build_still_loops
  Cinema      23–28  series-10-cinema walls 97–102            build_cinema_wallpapers
  Icon Echo   29–31, 44–52  series-11-icon-echo walls 104–106, 103, 107–108,
                            133–138                     build_still_loops
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIVE = ROOT / "Motion" / "live"
BASE_URL = "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/live"

FPS = 60
DURATION = 20
W, H = 1920, 1080
THUMB_W, THUMB_H = 480, 270


@dataclass(frozen=True)
class Clip:
    number: int          # clip number in the file name, stable forever
    set_name: str        # "Motion", "Deep Space", "Cinema", "Icon Echo"
    series: str          # still series directory name
    wall: int            # still wall number
    slug: str            # still slug (file stem after the number)
    title: str           # feed/app title
    prefix: str = ""     # file-name infix after the number, e.g. "deep-space-"
    file_slug: str = ""  # file-name slug when it differs from the still's
    # Still→loop tuning (build_still_loops): how much the points twinkle
    # and the soft light shimmers. Space wants stars; a gradient wants calm.
    twinkle: float = 0.55
    shimmer: float = 0.14

    @property
    def stem(self) -> str:
        return f"coremotion-live-{self.number:02d}-{self.prefix}{self.file_slug or self.slug}"

    @property
    def mp4(self) -> Path:
        return LIVE / f"{self.stem}.mp4"

    @property
    def thumb(self) -> Path:
        return LIVE / "thumbs" / f"{self.stem}.jpg"

    @property
    def still(self) -> Path:
        return ROOT / "Wallpapers" / self.series / f"corebuilds-{self.wall}-{self.slug}.png"

    @property
    def location(self) -> str:
        return f"Core Motion \u00b7 {self.set_name}"

    @property
    def id(self) -> str:
        """LiveLoop.kt id: the still slug, unique across the roster."""
        return self.slug


def _set(set_name, series, prefix, rows, **tuning) -> list[Clip]:
    return [Clip(number, set_name, series, wall, slug, title, prefix, *rest, **tuning)
            for number, wall, slug, title, *rest in rows]


MOTION = _set("Motion", "series-2-motion", "motion-", [
    (32, 25, "orbitals", "Orbitals"),
    (33, 26, "warp", "Warp"),
    (34, 27, "fogbanks", "Fogbanks"),
    (35, 28, "spiral", "Spiral"),
    (36, 29, "slipstream", "Slipstream"),
    (37, 30, "embers", "Embers"),
    (38, 31, "tracer", "Tracer"),
    (39, 32, "pendulum", "Pendulum"),
    (40, 51, "vortex", "Vortex"),
    (41, 52, "shockwave", "Shockwave"),
    (42, 53, "cascade", "Cascade"),
    (43, 54, "drift-field", "Drift Field"),
], twinkle=0.45, shimmer=0.12)

DEEP_SPACE = _set("Deep Space", "series-9-deep-space", "deep-space-", [
    (11, 85, "event-horizon", "Event Horizon"),
    (12, 86, "nebula-drift", "Nebula Drift"),
    (13, 87, "starfield", "Starfield"),
    (14, 88, "ringed-planet", "Ringed Planet"),
    (15, 89, "galaxy-spiral", "Galaxy Spiral"),
    (16, 90, "aurora-orbit", "Aurora Orbit"),
    (17, 91, "comet-lane", "Comet Lane"),
    (18, 92, "deep-field", "Deep Field"),
    (19, 93, "ember-nova", "Ember Nova"),
    (20, 94, "hex-station", "Hex Station"),
    (21, 95, "cyan-supernova", "Cyan Supernova"),
    (22, 96, "dark-side-moon", "Dark Side Moon"),
])

CINEMA = _set("Cinema", "series-10-cinema", "cinema-", [
    (23, 97, "marquee-lights", "Marquee Lights"),
    (24, 98, "velvet-curtain", "Velvet Curtain"),
    (25, 99, "projector-beam", "Projector Beam"),
    (26, 100, "neon-lounge", "Neon Lounge"),
    (27, 101, "late-rentals", "Late Rentals"),
    (28, 102, "box-office", "Box Office"),
])

# 29–31 keep the file names 2.3.x shipped (no "icon-echo-" infix, and 29
# stays "signal-sweep" on disk while it is the moving form of wall 104
# Signal Red); the three added in 2.4.0 take the infix like every other set.
# Icon Echo walls are soft light with no points, so no twinkle, more shimmer.
ICON_ECHO = (_set("Icon Echo", "series-11-icon-echo", "", [
    (29, 104, "signal-red", "Signal Red", "signal-sweep"),
    (30, 105, "green-current", "Green Current"),
    (31, 106, "molten-orbit", "Molten Orbit"),
], twinkle=0.0, shimmer=0.18) + _set("Icon Echo", "series-11-icon-echo", "icon-echo-", [
    (44, 103, "thermal-bloom", "Thermal Bloom"),
    (45, 107, "frost-line", "Frost Line"),
    (46, 108, "signal-azure", "Signal Azure"),
    (47, 133, "violet-stream", "Violet Stream"),
    (48, 134, "ember-reel", "Ember Reel"),
    (49, 135, "teal-tide", "Teal Tide"),
    (50, 136, "solar-band", "Solar Band"),
    (51, 137, "magenta-veil", "Magenta Veil"),
    (52, 138, "indigo-drift", "Indigo Drift"),
], twinkle=0.0, shimmer=0.18))

ROSTER: list[Clip] = MOTION + DEEP_SPACE + CINEMA + ICON_ECHO

# The sets build_still_loops renders; Cinema draws its own layers.
STILL_LOOPS: list[Clip] = MOTION + DEEP_SPACE + ICON_ECHO


def by_number(number: int) -> Clip:
    for c in ROSTER:
        if c.number == number:
            return c
    raise KeyError(number)


def _check() -> None:
    numbers = [c.number for c in ROSTER]
    assert len(numbers) == len(set(numbers)), "duplicate clip number"
    ids = [c.id for c in ROSTER]
    assert len(ids) == len(set(ids)), "duplicate loop id"
    assert all(n > 10 for n in numbers), "01–10 are retired test patterns"


_check()

if __name__ == "__main__":
    for c in ROSTER:
        print(f"{c.number:02d}  {c.set_name:<10} wall {c.wall:<3} {c.stem}")
    print(f"{len(ROSTER)} loops")
