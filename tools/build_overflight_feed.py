#!/usr/bin/env python3
"""Build the combined Overflight feed.

Source of truth for the live set stays Motion/live-feed.json (validated
against Motion/live/). Series-5 loops live in Motion/motion-feed.json.
This script concatenates them, de-dupes on url_1080p, and writes
Motion/overflight-feed.json — the URL Overflight / Aerial users should paste.

    python tools/build_overflight_feed.py
    python tools/build_overflight_feed.py --check
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = (
    ROOT / "Motion" / "live-feed.json",
    ROOT / "Motion" / "motion-feed.json",
)
TARGET = ROOT / "Motion" / "overflight-feed.json"
ALLOWED_HOSTS = {
    "raw.githubusercontent.com",
    "github.com",
    "objects.githubusercontent.com",
}


def load_entries(path: Path) -> list[dict]:
    if not path.exists():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise SystemExit(f"error: {path.name} must be a JSON array")
    return data


def merge(sources: list[list[dict]]) -> list[dict]:
    seen: set[str] = set()
    out: list[dict] = []
    for group in sources:
        for entry in group:
            url = (entry.get("url_1080p") or entry.get("url_4k") or "").strip()
            if not url or url in seen:
                continue
            if not url.startswith("https://"):
                continue
            host = url.split("/")[2]
            if host not in ALLOWED_HOSTS:
                continue
            seen.add(url)
            row = {
                "location": entry.get("location") or "Core Motion",
                "title": entry.get("title") or "Core Motion",
                "author": entry.get("author") or "Core Builds",
            }
            if entry.get("url_img"):
                row["url_img"] = entry["url_img"]
            if entry.get("url_1080p"):
                row["url_1080p"] = entry["url_1080p"]
            if entry.get("url_4k"):
                row["url_4k"] = entry["url_4k"]
            out.append(row)
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    entries = merge([load_entries(p) for p in SOURCES])
    if not entries:
        print("error: combined Overflight feed is empty", file=sys.stderr)
        return 1
    rendered = json.dumps(entries, indent=2) + "\n"

    if args.check:
        if not TARGET.exists() or TARGET.read_text(encoding="utf-8") != rendered:
            print(
                f"error: {TARGET.relative_to(ROOT)} is out of sync — "
                "run tools/build_overflight_feed.py",
                file=sys.stderr,
            )
            return 1
        print(f"ok: {TARGET.relative_to(ROOT)} in sync ({len(entries)} entries)")
        return 0

    TARGET.write_text(rendered, encoding="utf-8")
    print(f"wrote {TARGET.relative_to(ROOT)} — {len(entries)} entries")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
