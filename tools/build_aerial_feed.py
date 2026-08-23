#!/usr/bin/env python3
"""Emit the Core Motion loops as an Aerial Views "community" feed.

Why this exists
---------------
Monet Launcher (com.klevico.monet) is closed source and has no wallpaper
provider plugin API — nothing like Projectivy's IWallpaperProviderService. Its
wallpaper sources are fixed: your own images/videos, built-in, Reddit, and
**Aerial Views**.

Aerial Views (com.neilturner.aerialviews) *does* take arbitrary remote feeds, in
the "community" entries.json format. So the supported route onto Monet is:

    Motion/aerial-entries.json  ->  Aerial Views (custom feed)  ->  Monet

That gives Monet users the same auto-updating remote catalogue Projectivy users
get from the Core Motion plugin, without Monet needing to know we exist.

Source of truth stays Motion/live-feed.json (Overflight format). This script is
a pure projection of it — never hand-edit the output.

Usage:
    python tools/build_aerial_feed.py           # write Motion/aerial-entries.json
    python tools/build_aerial_feed.py --check   # verify it is in sync (CI)
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "Motion" / "live-feed.json"
TARGET = ROOT / "Motion" / "aerial-entries.json"

# Aerial Views buckets entries by time of day and uses it for playlist filtering.
# The Core Builds palette is dark-first (Night #0D1117 / Void #04070F), so every
# loop belongs in the night bucket — putting them in "day" makes Aerial Views
# skip them for users who filter, which reads as "the feed is broken".
TIME_OF_DAY = "night"


def slugify(url: str, fallback: str) -> str:
    """Stable id from the filename. Aerial Views keys favourites off `id`, so it
    must not change when titles are reworded."""
    name = url.rsplit("/", 1)[-1]
    name = re.sub(r"\.(mp4|mov|webm)$", "", name, flags=re.IGNORECASE)
    name = re.sub(r"[^a-zA-Z0-9]+", "_", name).strip("_").lower()
    return name or re.sub(r"[^a-z0-9]+", "_", fallback.lower()).strip("_")


def convert(entries: list[dict]) -> dict:
    assets = []
    for e in entries:
        url_1080 = (e.get("url_1080p") or "").strip()
        url_4k = (e.get("url_4k") or "").strip()
        if not url_1080 and not url_4k:
            continue

        title = (e.get("title") or "Core Motion").strip()
        location = (e.get("location") or "Core Motion").strip()
        author = (e.get("author") or "Core Builds").strip()

        asset: dict = {
            "id": slugify(url_1080 or url_4k, title),
            # Aerial Views shows this as the on-screen description, so it is the
            # only place our attribution can surface.
            "accessibilityLabel": f"{title} — {location} by {author}",
            "type": "aerial",
            "timeOfDay": TIME_OF_DAY,
        }
        # Only emit the keys we actually have. Aerial Views falls back across
        # qualities, but an empty-string URL is treated as present and fails.
        if url_1080:
            asset["url-1080-SDR"] = url_1080
        if url_4k:
            asset["url-4K-SDR"] = url_4k

        assets.append(asset)

    return {"assets": assets}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true",
                    help="fail if the generated feed differs from the committed one")
    args = ap.parse_args()

    if not SOURCE.exists():
        print(f"error: {SOURCE.relative_to(ROOT)} not found", file=sys.stderr)
        return 1

    entries = json.loads(SOURCE.read_text())
    if not isinstance(entries, list):
        print("error: live-feed.json must be a JSON array", file=sys.stderr)
        return 1

    feed = convert(entries)
    rendered = json.dumps(feed, indent=2) + "\n"

    if not feed["assets"]:
        print("error: produced an empty feed", file=sys.stderr)
        return 1

    if args.check:
        if not TARGET.exists():
            print(f"error: {TARGET.relative_to(ROOT)} is missing — run "
                  "tools/build_aerial_feed.py", file=sys.stderr)
            return 1
        if TARGET.read_text() != rendered:
            print(f"error: {TARGET.relative_to(ROOT)} is out of sync with "
                  "live-feed.json — run tools/build_aerial_feed.py", file=sys.stderr)
            return 1
        print(f"ok: {TARGET.relative_to(ROOT)} in sync "
              f"({len(feed['assets'])} assets)")
        return 0

    TARGET.write_text(rendered)
    print(f"wrote {TARGET.relative_to(ROOT)} — {len(feed['assets'])} assets")
    for a in feed["assets"]:
        tiers = [k.replace("url-", "") for k in a if k.startswith("url-")]
        print(f"  {a['id']:<38} {'/'.join(tiers)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
