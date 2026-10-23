#!/usr/bin/env python3
"""One wallpaper feed for everything Core Builds plays.

The Core Motion plugin (`tv.corebuilds.motion`) and Aerial Views both read a
single Overflight-format JSON list. A projector user can paste one URL, and
"one URL" is the whole point: a second source should not mean a second setting.

Today that is two catalogues:

    Motion/overflight-feed.json    the 19 motion loops (always present)
    views/overflight-feed.json     Core Views aerials (present once a season is
                                   rendered and marked ready)

so this merges them into:

    Motion/wallpaper-feed.json

Paste that URL once and Core Views appear in the rotation when they publish —
no re-configuration, no plugin update, no app build. Until then the file is the
motion set, which is a correct feed in its own right.

The output is a projection, never hand-edited:

    python tools/build_wallpaper_feed.py
    python tools/build_wallpaper_feed.py --check     # CI
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOTION = ROOT / "Motion" / "overflight-feed.json"
VIEWS = ROOT / "views" / "overflight-feed.json"
TARGET = ROOT / "Motion" / "wallpaper-feed.json"

# The Core Motion plugin refuses any other host (MotionFeed.ALLOWED_HOSTS), and
# Aerial Views is happiest on the same set. A feed that points somewhere else is
# a feed that silently plays nothing.
ALLOWED_HOSTS = {
    "raw.githubusercontent.com",
    "github.com",
    "objects.githubusercontent.com",
}
REQUIRED_KEYS = ("location", "title", "author")


class FeedError(Exception):
    """A source feed that would publish something unplayable."""


def label(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def load(path: Path) -> list[dict]:
    if not path.exists():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise FeedError(f"{label(path)} is not valid JSON: {exc}") from exc
    if not isinstance(data, list):
        raise FeedError(f"{label(path)} must be a JSON array")
    return data


def check_entry(entry: dict, source: Path, index: int) -> None:
    where = f"{label(source)}[{index}]"
    if not isinstance(entry, dict):
        raise FeedError(f"{where} is not an object")
    for key in REQUIRED_KEYS:
        value = entry.get(key)
        if not isinstance(value, str) or not value.strip():
            raise FeedError(f"{where} has no {key}")
    url = (entry.get("url_1080p") or entry.get("url_4k") or "").strip()
    if not url:
        raise FeedError(f"{where} ({entry.get('title')!r}) has no url_1080p or url_4k")
    if not url.startswith("https://"):
        raise FeedError(f"{where} ({entry.get('title')!r}) is not https: {url}")
    try:
        host = url.split("/")[2]
    except IndexError as exc:
        raise FeedError(f"{where} has an unparseable url: {url}") from exc
    if host not in ALLOWED_HOSTS:
        raise FeedError(
            f"{where} ({entry.get('title')!r}) points at {host}, which the Core "
            f"Motion plugin refuses. Allowed: {', '.join(sorted(ALLOWED_HOSTS))}")


def merge(groups: list[tuple[Path, list[dict]]]) -> list[dict]:
    """Concatenate in source order, de-duplicated on the URL it would play."""
    seen: set[str] = set()
    merged: list[dict] = []
    for source, entries in groups:
        for index, entry in enumerate(entries):
            check_entry(entry, source, index)
            url = (entry.get("url_1080p") or entry.get("url_4k") or "").strip()
            if url in seen:
                continue
            seen.add(url)
            # Only the keys the consumers read. An unknown key is not an error
            # in either client, but it is also not a promise we can keep.
            row = {key: entry[key] for key in REQUIRED_KEYS}
            for key in ("url_img", "url_1080p", "url_4k",
                        "url_1080p_hdr", "url_4k_hdr"):
                if entry.get(key):
                    row[key] = entry[key]
            merged.append(row)
    return merged


def build() -> tuple[str, int, int]:
    """Return (rendered JSON, motion count, views count)."""
    motion = load(MOTION)
    if not motion:
        raise FeedError(f"{label(MOTION)} is missing or empty — run "
                        "tools/build_overflight_feed.py first")
    views = load(VIEWS)
    merged = merge([(MOTION, motion), (VIEWS, views)])
    if not merged:
        raise FeedError("the merged wallpaper feed is empty")
    return json.dumps(merged, indent=2) + "\n", len(motion), len(views)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true",
                        help="fail if the committed feed differs from its sources")
    args = parser.parse_args(argv)

    try:
        rendered, motion_count, views_count = build()
    except FeedError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    if args.check:
        if not TARGET.exists():
            print(f"error: {label(TARGET)} is missing — run "
                  "tools/build_wallpaper_feed.py", file=sys.stderr)
            return 1
        if TARGET.read_text(encoding="utf-8") != rendered:
            print(f"error: {label(TARGET)} is out of sync with its sources — run "
                  "tools/build_wallpaper_feed.py", file=sys.stderr)
            return 1
        print(f"ok: {label(TARGET)} in sync ({motion_count} motion, "
              f"{views_count} view(s))")
        return 0

    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(rendered, encoding="utf-8")
    note = "" if views_count else " — no Core Views yet, the motion set only"
    print(f"wrote {label(TARGET)} ({motion_count} motion, "
          f"{views_count} view(s)){note}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
