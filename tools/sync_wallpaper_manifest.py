#!/usr/bin/env python3
"""
Sync the bundled wallpaper manifests (and their thumb sets) with the repo.

The repo manifest is the source of truth; the APK ships a copy that CI and
tests/test_wallpapers.py hold byte-identical, and the bundled thumb set must
be exactly the manifest's set (an extra thumb is dead weight, a missing one
is a blank cell in the in-app grid).

One collection, one APK:

  classic   Wallpapers/manifest.json        -> app/src/main/assets/manifest/wallpapers.json

Thumbs live in Wallpapers/thumbs/ and are copied to
app/src/main/assets/wallpapers_thumbs/.

Usage:
  sync_wallpaper_manifest.py           # verify + copy, fail on drift
  sync_wallpaper_manifest.py --check   # report only, change nothing
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WALLPAPERS = ROOT / "Wallpapers"
THUMBS = WALLPAPERS / "thumbs"

COLLECTIONS = {
    "classic": {
        "manifest": WALLPAPERS / "manifest.json",
        "bundled": ROOT / "app/src/main/assets/manifest/wallpapers.json",
        "thumbs": ROOT / "app/src/main/assets/wallpapers_thumbs",
    },
}


def thumb_file(entry: dict) -> Path:
    return THUMBS / Path(entry["thumb"]).name


def check_collection(name: str, cfg: dict, write: bool) -> list[str]:
    problems: list[str] = []
    manifest = cfg["manifest"]
    if not manifest.exists():
        return [f"{name}: missing {manifest.relative_to(ROOT)}"]
    data = json.loads(manifest.read_text(encoding="utf-8"))
    walls = data.get("wallpapers", [])

    # every entry needs its file and its thumb on disk
    for w in walls:
        url = w.get("url", "")
        fname = url.rsplit("/", 1)[-1] if url else None
        series_dir = WALLPAPERS / w.get("series", "")
        if not fname or not (series_dir / fname).exists():
            problems.append(f"{name}: no file on disk for {w.get('name')!r}: "
                            f"{series_dir / (fname or '?')}")
        t = thumb_file(w)
        if not t.exists():
            problems.append(f"{name}: missing thumb {t.name} for {w.get('name')!r}")

    if problems:
        return problems

    # manifest copy
    bundled = cfg["bundled"]
    if bundled.exists() and bundled.read_bytes() == manifest.read_bytes():
        manifest_state = "in sync"
    else:
        if write:
            bundled.parent.mkdir(parents=True, exist_ok=True)
            bundled.write_bytes(manifest.read_bytes())
            manifest_state = f"copied -> {bundled.relative_to(ROOT)}"
        else:
            manifest_state = "DRIFT (would copy)"
            problems.append(f"{name}: bundled manifest drifts from the repo manifest")

    # thumb set: exactly the manifest's set
    want = {Path(w["thumb"]).name for w in walls}
    dst = cfg["thumbs"]
    dst.mkdir(parents=True, exist_ok=True)
    have = {p.name for p in dst.iterdir() if p.is_file()}
    copied = [n for n in sorted(want - have)]
    stale = [n for n in sorted(have - want)]
    for n in copied:
        if write:
            (dst / n).write_bytes((THUMBS / n).read_bytes())
    for n in stale:
        if write:
            (dst / n).unlink()
        else:
            problems.append(f"{name}: stale bundled thumb {n}")

    state = ", ".join([manifest_state,
                       f"{len(copied)} thumb copied" if copied else "thumbs in sync",
                       f"{len(stale)} stale thumb removed" if stale else ""])
    print(f"{name}: {state}")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="report drift without writing")
    args = parser.parse_args()
    problems: list[str] = []
    for name, cfg in COLLECTIONS.items():
        problems += check_collection(name, cfg, write=not args.check)
    if problems:
        print("\n" + "\n".join(f"  ✗ {p}" for p in problems), file=sys.stderr)
        return 1
    print("wallpaper manifests and thumbs in sync")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
