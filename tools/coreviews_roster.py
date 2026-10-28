#!/usr/bin/env python3
"""The Core Views wallpaper roster — `views/catalog.json`, read as live wallpapers.

Core Views is an aerial wallpaper line for the icon pack's live wallpaper
engine. Its twelve season-1 views are planned renders: the catalogue is the
source of truth for the place, the camera move, the caption track and the
credits, and this file turns each of those into one moving wallpaper.

What plays today is **the moving form of the shot card, not a render of the
place**. `views/catalog.json` still says `planned` for every view's
`renditions`, and it stays that way: these loops are a designed stand-in —
contour art in the view's own palette with the camera's real track travelling
across it — so the app has something to browse, preview and set while the
open-data renderer waits for tile access. When a view's 1080p rendition lands
it replaces this file's entry for that number and nothing else moves; see
`docs/CORE_VIEWS_WALLPAPERS.md`.

Naming is the Motion line's, one set apart:

  file    Motion/live/…mp4                 Core Views loops live in Motion/live/ too
          coreviews-live-01-three-sisters.mp4
  id      coreviews-three-sisters          the persisted preference value
  chip    series-core-views                "Core Views" in the browser

Screen format is the wallpaper engine's, not the renderer's: a wallpaper is
played on a TV whose launcher owns the bottom of the screen, so the calm
bottom quarter, the muted loop and the 20 s length are all the same rules the
Motion loops follow.

    python tools/coreviews_roster.py          # list the season
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "views" / "catalog.json"
LIVE = ROOT / "Motion" / "live"
THUMBS = LIVE / "thumbs"
APP_THUMBS = ROOT / "app" / "src" / "main" / "assets" / "coreviews_thumbs"
BASE_URL = "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/Motion/live"

# The wallpaper engine's shape: 20 s, 30 fps, 1080p. Not the renderer's 20–40 s
# at 30 fps — a wallpaper loop that spends its first second fading in reads as
# a glitch on a home screen that comes back every few minutes.
FPS = 30
DURATION = 20
W, H = 1920, 1080
THUMB_W, THUMB_H = 480, 270

# The browser chip. `series-core-views` label-ises to "Core Views" through
# WallpaperCatalog.seriesLabel's explicit case.
SERIES = "series-core-views"


@dataclass(frozen=True)
class Clip:
    """One view, playable: the catalogue entry plus the wallpaper it becomes."""

    number: int
    view_id: str
    title: str
    place: str
    region: str
    scene: str
    move: str
    captions: tuple[tuple[int, str], ...]
    credits: tuple[str, ...]

    @property
    def slug(self) -> str:
        """File-name slug: the catalogue id without its `coreviews-NN-` prefix."""
        return self.view_id.split("-", 2)[2]

    @property
    def stem(self) -> str:
        return f"coreviews-live-{self.number:02d}-{self.slug}"

    @property
    def mp4(self) -> Path:
        return LIVE / f"{self.stem}.mp4"

    @property
    def thumb(self) -> Path:
        return THUMBS / f"{self.stem}.jpg"

    @property
    def app_thumb(self) -> Path:
        return APP_THUMBS / f"{self.stem}.jpg"

    @property
    def id(self) -> str:
        """The persisted preference value. Never renamed once shipped."""
        return f"coreviews-{self.slug}"

    @property
    def url(self) -> str:
        return f"{BASE_URL}/{self.stem}.mp4"

    @property
    def thumb_url(self) -> str:
        return f"{BASE_URL}/thumbs/{self.stem}.jpg"

    @property
    def thumb_asset(self) -> str:
        return f"coreviews_thumbs/{self.stem}.jpg"

    @property
    def location(self) -> str:
        return f"Core Views · {self.region}"


def _load() -> list[Clip]:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    clips = []
    for view in sorted(catalog["views"], key=lambda v: v["number"]):
        clips.append(Clip(
            number=int(view["number"]),
            view_id=view["id"],
            title=view["title"],
            place=view["place"],
            region=view["region"],
            scene=view["scene"],
            move=(view.get("camera") or {}).get("move", "glide"),
            captions=tuple((int(c["at"]), c["text"]) for c in view.get("captions", ())),
            credits=tuple(c["source"] for c in view.get("credits", ())),
        ))
    return clips


CLIPS: list[Clip] = _load()


def by_number(number: int) -> Clip:
    for clip in CLIPS:
        if clip.number == number:
            return clip
    raise KeyError(number)


def _check() -> None:
    numbers = [c.number for c in CLIPS]
    assert len(numbers) == len(set(numbers)), "duplicate view number"
    ids = [c.id for c in CLIPS]
    assert len(ids) == len(set(ids)), "duplicate clip id"
    for clip in CLIPS:
        # A renamed slug silently re-points an installed wallpaper at the
        # default, exactly like the Motion loops.
        assert clip.id.startswith("coreviews-") and clip.slug, clip.id
    assert len(CLIPS) == 12, f"season 1 is twelve views, found {len(CLIPS)}"


_check()

if __name__ == "__main__":
    for c in CLIPS:
        print(f"{c.number:02d}  {c.id:<34} {c.title:<16} {c.move:<14} {c.stem}.mp4")
    print(f"{len(CLIPS)} views")
