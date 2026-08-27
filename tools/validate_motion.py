#!/usr/bin/env python3
"""
validate_motion.py — coherence checks for the Core Builds Motion asset set.

Run before every commit that touches Motion/ or manifest-motion.json.
CI runs this in the core-shift-apk.yml validate job.

Checks:
  1. manifest-motion.json parses as valid JSON with required structure.
  2. Every video entry has required fields (name, url_1080p, thumb, resolution, duration).
  3. Every url_1080p file exists on disk (relative to repo root).
  4. Every thumb file exists on disk.
  5. motion-feed.json parses and has matching entry count.
  6. No duplicate names or filenames.
  7. All URLs use the expected GitHub raw host.
  8. 4K entries (url_4k not null) have matching files on disk.
  9. Duration is positive and ≤ 60s.
  10. Resolution matches known formats.
"""

import json
import os
import sys
from urllib.parse import urlparse

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(REPO_ROOT, "Motion", "manifest-motion.json")
FEED = os.path.join(REPO_ROOT, "Motion", "motion-feed.json")
MOTION_DIR = os.path.join(REPO_ROOT, "Motion")

ALLOWED_HOSTS = {"raw.githubusercontent.com", "github.com", "objects.githubusercontent.com"}
VALID_RESOLUTIONS = {"1920x1080", "3840x2160"}
REQUIRED_FIELDS = {"name", "url_1080p", "thumb", "resolution", "duration"}

errors = []
warnings = []
checks = 0


def check(condition, msg):
    global checks
    checks += 1
    if not condition:
        errors.append(msg)
    return condition


def warn(condition, msg):
    global checks
    checks += 1
    if not condition:
        warnings.append(msg)


def url_to_local(url):
    """Convert a raw.githubusercontent URL to a local file path."""
    prefix = "https://raw.githubusercontent.com/brevityA/CoreBuildsApps/main/"
    if url and url.startswith(prefix):
        return os.path.join(REPO_ROOT, url[len(prefix):])
    return None


def validate_manifest():
    check(os.path.isfile(MANIFEST), f"manifest not found: {MANIFEST}")
    if errors:
        return []

    with open(MANIFEST) as f:
        data = json.load(f)

    check(isinstance(data, dict), "manifest root must be a JSON object")
    check("videos" in data, "manifest missing 'videos' array")
    check("count" in data, "manifest missing 'count' field")

    videos = data.get("videos", [])
    check(isinstance(videos, list), "'videos' must be an array")
    check(
        data.get("count") == len(videos),
        f"count={data.get('count')} but {len(videos)} videos found"
    )

    names = set()
    filenames = set()

    for i, v in enumerate(videos):
        if not isinstance(v, dict):
            check(False, f"video at index {i} is not a JSON object")
            continue
        label = v.get("name", f"index {i}")

        for field in REQUIRED_FIELDS:
            check(field in v, f"video '{label}' missing field '{field}'")

        if "name" in v:
            check(v["name"] not in names, f"duplicate name: {v['name']}")
            names.add(v["name"])

        url_1080 = v.get("url_1080p", "")
        if url_1080:
            fname = url_1080.rsplit("/", 1)[-1]
            check(fname not in filenames, f"duplicate filename: {fname}")
            filenames.add(fname)

            host = urlparse(url_1080).hostname
            check(host in ALLOWED_HOSTS, f"'{label}' url_1080p host not allowed: {host}")

            local = url_to_local(url_1080)
            if local:
                check(os.path.isfile(local), f"'{label}' 1080p file missing: {local}")

        url_4k = v.get("url_4k")
        if url_4k:
            host_4k = urlparse(url_4k).hostname
            check(host_4k in ALLOWED_HOSTS, f"'{label}' url_4k host not allowed: {host_4k}")
            local_4k = url_to_local(url_4k)
            if local_4k:
                check(os.path.isfile(local_4k), f"'{label}' 4K file missing: {local_4k}")

        thumb = v.get("thumb", "")
        if thumb:
            host_thumb = urlparse(thumb).hostname
            check(host_thumb in ALLOWED_HOSTS, f"'{label}' thumb host not allowed: {host_thumb}")
            local_thumb = url_to_local(thumb)
            if local_thumb:
                check(os.path.isfile(local_thumb), f"'{label}' thumb missing: {local_thumb}")

        res = v.get("resolution", "")
        check(res in VALID_RESOLUTIONS, f"'{label}' invalid resolution: {res}")

        dur = v.get("duration", 0)
        check(isinstance(dur, int) and 0 < dur <= 60, f"'{label}' duration out of range: {dur}")

    return videos


def validate_feed(video_count):
    check(os.path.isfile(FEED), f"motion feed not found: {FEED}")
    if not os.path.isfile(FEED):
        return

    with open(FEED) as f:
        feed = json.load(f)

    check(isinstance(feed, list), "feed root must be a JSON array")
    check(
        len(feed) == video_count,
        f"feed has {len(feed)} entries but manifest has {video_count} videos"
    )

    for i, entry in enumerate(feed):
        label = entry.get("title", f"feed index {i}")
        for field in ("location", "title", "author", "url_img", "url_1080p"):
            check(field in entry, f"feed '{label}' missing field '{field}'")


BUNDLED_MANIFEST = os.path.join(
    REPO_ROOT, "shift", "app", "src", "main", "assets", "manifest-motion.json"
)


def validate_bundled():
    if not os.path.isfile(BUNDLED_MANIFEST):
        warn(False, "bundled manifest not found (expected at shift/app/src/main/assets/)")
        return

    with open(MANIFEST) as f:
        release = f.read()
    with open(BUNDLED_MANIFEST) as f:
        bundled = f.read()

    check(
        release == bundled,
        f"bundled manifest differs from Motion/manifest-motion.json — "
        f"copy the release manifest to {BUNDLED_MANIFEST}"
    )


def main():
    print("Core Builds Motion — validation")
    print("=" * 40)

    videos = validate_manifest()
    if videos:
        validate_feed(len(videos))
    validate_bundled()

    print()
    if warnings:
        for w in warnings:
            print(f"  WARN: {w}")
        print()

    if errors:
        for e in errors:
            print(f"  FAIL: {e}")
        print()
        print(f"Validated {checks} checks — {len(errors)} failed, {len(warnings)} warnings")
        sys.exit(1)
    else:
        count = len(videos) if videos else 0
        print(f"Validated {count} videos · {checks} checks passed · {len(warnings)} warnings")
        sys.exit(0)


if __name__ == "__main__":
    main()
