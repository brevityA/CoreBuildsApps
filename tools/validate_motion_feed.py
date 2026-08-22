#!/usr/bin/env python3
"""Validate the Core Builds Motion live-wallpaper set.

Checks that Motion/live-feed.json is a well-formed Overflight-compatible feed,
that every referenced MP4/thumb exists on disk under Motion/live/, and that no
stray media files are missing from the feed (catches edits to
build_motion_feed.py that forgot to regenerate).

Exit 0 iff all checks pass.  Run:  python tools/validate_motion_feed.py
"""

import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIVE = os.path.join(ROOT, "Motion", "live")
FEED = os.path.join(ROOT, "Motion", "live-feed.json")

ALLOWED_HOSTS = {"raw.githubusercontent.com", "github.com", "objects.githubusercontent.com"}


def fail(msg):
    print(f"  FAIL: {msg}")
    return False


def main():
    if not os.path.exists(FEED):
        return fail("Motion/live-feed.json missing")

    with open(FEED, encoding="utf-8") as f:
        feed = json.load(f)

    ok = True
    if not isinstance(feed, list):
        return fail("feed root must be a JSON array")
    if not feed:
        ok = fail("feed is empty")

    referenced = set()
    for i, entry in enumerate(feed):
        title = entry.get("title", f"entry {i}")
        url1080 = entry.get("url_1080p", "")
        url4k = entry.get("url_4k", "")
        url_img = entry.get("url_img", "")

        # At least one media URL must be present (Overflight rule).
        if not (url1080 or url4k or url_img):
            ok = fail(f"{title}: no media url (url_1080p/url_4k/url_img)")
            continue

        # URLs must be https + allowlisted, and resolve to real files.
        for field, url in (("url_1080p", url1080), ("url_4k", url4k), ("url_img", url_img)):
            if not url:
                continue
            if not url.startswith("https://"):
                ok = fail(f"{title} {field}: not https")
                continue
            host = url.split("/")[2]
            if host not in ALLOWED_HOSTS:
                ok = fail(f"{title} {field}: host {host} not allowlisted")
            name = url.rsplit("/", 1)[-1]
            subdir = "thumbs" if field == "url_img" else ""
            path = os.path.join(LIVE, subdir, name)
            if not os.path.exists(path):
                ok = fail(f"{title} {field}: {name} missing on disk")
            else:
                referenced.add(name)

    # No stray media files (leftovers from an edited CLIPS list).
    for dirname, _subdirs, files in os.walk(LIVE):
        for name in files:
            if name.endswith((".mp4", ".jpg")) and name not in referenced:
                ok = fail(f"unreferenced media file: {name}")

    if ok:
        print(f"  OK: {len(feed)} live wallpapers · feed↔files consistent")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
