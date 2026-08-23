#!/usr/bin/env python3
"""Render one or both production quality tiers and emit one combined feed.

The Core Shift selector expects each feed item to carry both `url_1080p` and
`url_4k`. Calling the single-tier renderer twice would overwrite the feed, so
this orchestrator renders into temporary directories, combines both manifests,
and publishes one coherent Motion/prequel bundle.
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RENDERER = ROOT / "tools" / "build_prequel_motion.py"
OUTPUT = ROOT / "Motion" / "prequel"

TIERS = {
    "1080p": (1920, 1080),
    "4k": (3840, 2160),
}


def render_tier(
    set_name: str,
    only: list[str],
    width: int,
    height: int,
    duration: float,
    fps: int,
    crf: int,
    speed: float,
    output: Path,
) -> None:
    cmd = [
        sys.executable,
        str(RENDERER),
        "--set",
        set_name,
        "--width",
        str(width),
        "--height",
        str(height),
        "--duration",
        str(duration),
        "--fps",
        str(fps),
        "--crf",
        str(crf),
        "--speed",
        str(speed),
        "--output-dir",
        str(output),
    ]
    for item in only:
        cmd += ["--only", item]
    subprocess.run(cmd, check=True, cwd=ROOT)


def key(entry: dict) -> str:
    title = str(entry.get("title", ""))
    return title.split(" ", 1)[0] if title else ""


def copy_media(source: Path, destination: Path) -> None:
    for item in source.iterdir():
        if item.name in {"manifest.json", "prequel-feed.json"}:
            continue
        target = destination / item.name
        if item.is_file():
            shutil.copy2(item, target)


def merge_feeds(hd_feed: list[dict], uhd_feed: list[dict]) -> list[dict]:
    by_number: dict[str, dict] = {}
    for entry in hd_feed + uhd_feed:
        by_number.setdefault(key(entry), {}).update(entry)
    combined = []
    for number in sorted(by_number, key=lambda value: int(value or 0)):
        entry = by_number[number]
        entry["url_1080p"] = next(
            (e.get("url_1080p") for e in hd_feed if key(e) == number),
            None,
        )
        entry["url_4k"] = next(
            (e.get("url_4k") for e in uhd_feed if key(e) == number),
            None,
        )
        entry["url_img"] = next(
            (e.get("url_img") for e in hd_feed if key(e) == number),
            entry.get("url_img"),
        )
        entry["tiers"] = [
            tier for tier, present in (
                ("1080p", bool(entry.get("url_1080p"))),
                ("4k", bool(entry.get("url_4k"))),
            ) if present
        ]
        combined.append(entry)
    return combined


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--set", choices=("series-2-motion", "series-3-horizons", "series-2-3"), default="series-2-3")
    parser.add_argument("--tier", choices=("1080p", "4k", "both"), default="both")
    parser.add_argument("--only", action="append", default=[])
    parser.add_argument("--duration", type=float, default=20.0)
    parser.add_argument("--fps", type=int, default=30)
    parser.add_argument("--crf", type=int, default=18)
    parser.add_argument("--speed", type=float, default=1.0)
    parser.add_argument("--output-dir", type=Path, default=OUTPUT)
    args = parser.parse_args()
    if args.duration <= 0 or args.fps <= 0:
        parser.error("duration and fps must be positive")
    if not 0.5 <= args.speed <= 2.0:
        parser.error("speed must be between 0.5 and 2.0")
    tiers = ("1080p", "4k") if args.tier == "both" else (args.tier,)

    with tempfile.TemporaryDirectory(prefix="core-prequel-") as temp:
        temp_root = Path(temp)
        feeds: dict[str, list[dict]] = {}
        for tier in tiers:
            tier_dir = temp_root / tier
            width, height = TIERS[tier]
            print(f"rendering tier {tier} ({width}x{height})", flush=True)
            render_tier(args.set, args.only, width, height, args.duration, args.fps, args.crf, args.speed, tier_dir)
            feeds[tier] = json.loads((tier_dir / "prequel-feed.json").read_text(encoding="utf-8"))

        args.output_dir.mkdir(parents=True, exist_ok=True)
        # Never leave stale tier files from a previous partial render in the
        # release directory; the feed↔files validator must describe exactly
        # what this run produced.
        for stale in args.output_dir.iterdir():
            if stale.is_file():
                stale.unlink()
        for tier in tiers:
            copy_media(temp_root / tier, args.output_dir)

        if len(tiers) == 2:
            feed = merge_feeds(feeds["1080p"], feeds["4k"])
        else:
            feed = feeds[tiers[0]]
            missing = "url_1080p" if tiers[0] == "4k" else "url_4k"
            for entry in feed:
                entry["tiers"] = [tiers[0]]
                entry.setdefault(missing, None)
            print(f"warning: single-tier render ({tiers[0]}); "
                  f"feed entries lack {missing}", flush=True)

        videos = []
        for entry in feed:
            videos.append({
                "name": f"{entry['title']} · Prequel",
                "series": entry.get("series", "series-2-motion"),
                "engine": entry.get("engine", "core-prequel-engine@1.0.0"),
                "url_1080p": entry.get("url_1080p"),
                "url_4k": entry.get("url_4k"),
                "thumb": entry.get("url_img"),
                "resolution": "1920x1080 + 3840x2160" if len(tiers) == 2 else ("3840x2160" if tiers[0] == "4k" else "1920x1080"),
                "duration": round(args.duration),
                "fps": args.fps,
                "motion_speed": args.speed,
                "scene": entry.get("scene"),
                "accent": entry.get("accent"),
                "tiers": entry.get("tiers", list(tiers)),
            })
        write_json(args.output_dir / "prequel-feed.json", feed)
        write_json(args.output_dir / "manifest.json", {
            "collection": "Core Builds Motion · Series 2/3 Prequels",
            "version": "1.1",
            "author": "brevityA",
            "engine": "core-prequel-engine@1.0.0",
            "count": len(videos),
            "tiers": list(tiers),
            "videos": videos,
        })
    print(f"done: {len(feed)} prequel(s), tiers={','.join(tiers)} -> {args.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
