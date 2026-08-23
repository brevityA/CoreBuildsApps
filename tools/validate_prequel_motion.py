#!/usr/bin/env python3
"""Validate generated Series 2/3 prequel outputs.

Unlike validate_motion.py this validator accepts a partial render from
--only, which makes it useful for fast shader smoke tests as well as the full
16-preset production job.
"""

from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "motion-engine" / "prequels.json"
ALLOWED_HOSTS = {"raw.githubusercontent.com", "github.com", "objects.githubusercontent.com"}


def catalog_presets() -> dict[str, dict]:
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    return {
        f"{int(p['number']):02d}-{p['slug']}": p
        for group in data["series"]
        for p in group["presets"]
    }


def probe(path: Path) -> tuple[int, int, float]:
    cmd = [
        "ffprobe", "-v", "error", "-select_streams", "v:0",
        "-show_entries", "stream=width,height,r_frame_rate",
        "-show_entries", "format=duration", "-of", "json", str(path),
    ]
    data = json.loads(subprocess.check_output(cmd, text=True))
    stream = data["streams"][0]
    num, den = (int(x) for x in stream["r_frame_rate"].split("/"))
    return int(stream["width"]), int(stream["height"]), float(data["format"]["duration"])


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--generated", type=Path, default=ROOT / "Motion" / "prequel")
    ap.add_argument("--skip-media-probe", action="store_true")
    args = ap.parse_args()
    out = args.generated if args.generated.is_absolute() else ROOT / args.generated
    feed_path = out / "prequel-feed.json"
    manifest_path = out / "manifest.json"
    errors: list[str] = []

    if not feed_path.is_file():
        errors.append(f"missing {feed_path}")
    if not manifest_path.is_file():
        errors.append(f"missing {manifest_path}")
    if errors:
        for error in errors:
            print(f"FAIL: {error}")
        return 1

    feed = json.loads(feed_path.read_text(encoding="utf-8"))
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    known = catalog_presets()
    if not isinstance(feed, list) or not feed:
        errors.append("prequel-feed.json must be a non-empty array")
    if manifest.get("count") != len(feed) or len(manifest.get("videos", [])) != len(feed):
        errors.append("feed and manifest counts differ")

    seen: set[str] = set()
    for entry in feed if isinstance(feed, list) else []:
        title = entry.get("title", "untitled")
        for field in ("location", "title", "author", "url_img"):
            if not entry.get(field):
                errors.append(f"{title}: missing {field}")
        if not entry.get("url_1080p") and not entry.get("url_4k"):
            errors.append(f"{title}: missing url_1080p or url_4k")
        for field in ("url_img", "url_1080p", "url_4k"):
            url = entry.get(field, "")
            if not url:
                continue
            parsed = urlparse(url)
            if parsed.scheme != "https" or parsed.hostname not in ALLOWED_HOSTS:
                errors.append(f"{title}: {field} host is not allowlisted")
            name = Path(parsed.path).name
            local = out / name
            if not local.is_file():
                errors.append(f"{title}: {name} missing from {out}")
            if field in ("url_1080p", "url_4k") and name.endswith(".mp4"):
                key = name.removeprefix("coremotion-prequel-").removesuffix("-4k.mp4").removesuffix("-preview.mp4").removesuffix(".mp4")
                seen.add(key)
                if not args.skip_media_probe and local.is_file():
                    try:
                        width, height, duration = probe(local)
                        if width < 2 or height < 2 or duration < 1:
                            errors.append(f"{title}: invalid media dimensions/duration")
                    except (OSError, subprocess.CalledProcessError, json.JSONDecodeError, KeyError, ValueError) as exc:
                        errors.append(f"{title}: ffprobe failed: {exc}")

    unknown = seen - set(known)
    if unknown:
        errors.append(f"generated file(s) do not map to catalog: {', '.join(sorted(unknown))}")

    if errors:
        for error in errors:
            print(f"FAIL: {error}")
        print(f"Validated {len(feed) if isinstance(feed, list) else 0} prequel entries — {len(errors)} failure(s)")
        return 1

    print(f"OK: {len(feed)} generated prequel wallpaper(s) · feed↔manifest↔files consistent")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
