#!/usr/bin/env python3
"""
Fail when a wall's picture changed under a cache name a device may still hold.

The app caches each download under `Wallpaper.storeName`: the URL's file name,
prefixed with the manifest's `since` stamp from 2.4.1 on. A wall re-rendered
under the same file name must therefore move to a new `since`, or a device
that opened it before keeps opening, setting and saving the old picture
behind the new thumbnail. 2.3.3 redrew 52 Shockwave and Cinema 97, 99-102
without a stamp, so those six kept serving their old pictures through 2.4.1.

For every release tag `v*.*.*`, each wall whose file existed there with
different bytes must not share the cache name that release's app used:

  app <  2.4.1   <file name>
  app >= 2.4.1   <since>-<file name>, or <file name> when `since` is empty

It reads the release tags, so it needs them (`git fetch --tags`), and fails
rather than pass on no history. tools/prepare_release.py runs it as a gate.

Usage:
  check_wallpaper_cache_keys.py
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "Wallpapers" / "manifest.json"
STAMPED_FROM = (2, 4, 1)  # first app whose cache name carries `since`


def version(v: str) -> tuple[int, ...]:
    return tuple(int(p) for p in v.split("."))


def stamp(since: str) -> str:
    # WallpaperCatalog.storeName keeps letters, digits, '.' and '-'.
    return "".join(c for c in since if c.isalnum() or c in ".-")


def store_name(fname: str, since: str, app: tuple[int, ...]) -> str:
    """The cache file name app release `app` gives this wall."""
    if app < STAMPED_FROM:
        return fname
    s = stamp(since)
    return f"{s}-{fname}" if s else fname


def collisions(now: dict[str, tuple[str, str]],
               history: list[tuple[str, dict[str, tuple[str, str]]]]) -> list[str]:
    """Walls whose picture changed while their cache name did not.

    now:     file name -> (blob, since) for the walls in the working tree
    history: [(release, {file name -> (blob, since)})], any order
    """
    problems = []
    for fname, (blob, since) in sorted(now.items()):
        current = store_name(fname, since, (999,))
        # newest first, so the message names the last release with the old picture
        for release, walls in sorted(history, key=lambda h: version(h[0]), reverse=True):
            if fname not in walls:
                continue
            old_blob, old_since = walls[fname]
            if old_blob == blob:
                continue
            if store_name(fname, old_since, version(release)) == current:
                problems.append(
                    f"{fname}: the picture differs from v{release} but the cache name "
                    f"`{current}` is unchanged (since={since or 'none'}). Stamp `since` "
                    f"with the release that first ships this picture.")
                break
    return problems


def git(*args: str) -> str:
    return subprocess.run(["git", "-C", str(ROOT), *args], capture_output=True,
                          text=True, check=True).stdout


def walls_at(ref: str) -> dict[str, tuple[str, str]]:
    """file name -> (blob, since) for every wall file at a release tag."""
    blobs = {}
    for line in git("ls-tree", "-r", ref, "--", "Wallpapers").splitlines():
        meta, path = line.split("\t", 1)
        if "/thumbs/" in path or not re.search(r"\.(png|jpe?g)$", path):
            continue
        blobs[path.rsplit("/", 1)[-1]] = meta.split()[2]
    since = {}
    try:
        manifest = json.loads(git("show", f"{ref}:Wallpapers/manifest.json"))
        for w in manifest.get("wallpapers", []):
            since[w["url"].rsplit("/", 1)[-1]] = w.get("since", "")
    except subprocess.CalledProcessError:
        pass  # releases before the manifest: no stamps
    return {f: (b, since.get(f, "")) for f, b in blobs.items()}


def main() -> int:
    walls = json.loads(MANIFEST.read_text(encoding="utf-8"))["wallpapers"]
    now = {}
    for w in walls:
        fname = w["url"].rsplit("/", 1)[-1]
        path = ROOT / "Wallpapers" / w["series"] / fname
        now[fname] = (git("hash-object", str(path)).strip(), w.get("since", ""))

    tags = [t for t in git("tag", "-l", "v*").split() if re.fullmatch(r"v\d+\.\d+\.\d+", t)]
    if not tags:
        print("no release tags in this clone; run `git fetch --tags` and re-run",
              file=sys.stderr)
        return 2
    history = [(t[1:], walls_at(t)) for t in tags]

    problems = collisions(now, history)
    if problems:
        print("\n".join(f"  ✗ {p}" for p in problems), file=sys.stderr)
        return 1
    print(f"wallpaper cache names ok: {len(now)} walls against {len(tags)} releases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
