#!/usr/bin/env python3
"""
Is the update we advertise actually downloadable at the URL we advertise?

The failure this exists for, in full:

  * `Latestrelease/version.json` is what every installed copy polls.
  * The floating `iconpack` release is what its `apkUrl` points at, because
    that URL has to stay stable for Downloader builds already in the wild.
  * So a feed that says 1.9.5 while the floating release still carries 1.9.4
    produces exactly the report users filed: "1.9.5 is available but fails to
    install". The app is behaving correctly - `UpdateInstaller` requires the
    downloaded APK's `versionCode` to be the one the feed named, and refuses
    1.9.4-over-1.9.4 with "APK version 36 is not the version expected".
    Nothing is broken except the loop between feed and release.

`tests/test_update_manifest_gate.py` holds the ordering half of that contract
(the feed is published by the tag build, after the releases carry the APK, and
never by a version-bump PR). This tool holds the half a test cannot reach from a
checkout: that the bytes at `apkUrl` really are the version the feed names.

Two modes:

  --offline   structural only, no network: the feed must not lead the build's
              own manifest, and the URL must name a tag shape we publish. Safe
              in a PR, where the release deliberately does not exist yet.
  (default)   fetch the feed's own APK from `apkUrl` and compare the version
              inside it with the version advertised. Run in the tag build,
              straight after "Publish the update manifest", which is the only
              moment the two can drift.

The version inside the APK is read from the APK's own `assets/version.json` -
the manifest the build stamps and ships (`tests/test_update_manifest_gate.py`
holds it equal to gradle's `versionCode`). That avoids a binary-XML parser and
parses the one file the app itself trusts. Read-only, bounded, stdlib only.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FEED = ROOT / "Latestrelease/version.json"
BUILD = ROOT / "app/src/main/assets/version.json"

# The APK is ~12 MB; the cap only stops a redirect to something enormous from
# filling a runner's disk before the version check can fail the build.
MAX_BYTES = 64 * 1024 * 1024

# /releases/download/<tag>/<asset> - the only shape the feed may use.
DOWNLOAD_URL = re.compile(
    r"^https://github\.com/[^/]+/[^/]+/releases/download/(?P<tag>[^/]+)/(?P<asset>[^/]+)$"
)


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def apk_manifest(apk: Path) -> dict:
    """The build manifest an APK carries, or a reason it is not a pack APK."""
    with zipfile.ZipFile(apk) as archive:
        try:
            raw = archive.read("assets/version.json")
        except KeyError:
            raise ValueError(f"{apk.name} carries no assets/version.json")
    return json.loads(raw.decode("utf-8"))


def url_problems(feed: dict, expected_tag: str | None = None) -> list[str]:
    """Shape of `apkUrl`: a URL we publish, naming an asset we ship."""
    url = str(feed.get("apkUrl", ""))
    if not url:
        return ["the feed has no apkUrl"]
    match = DOWNLOAD_URL.match(url)
    if not match:
        return [f"apkUrl is not a GitHub release download URL: {url}"]
    problems = []
    tag, asset = match.group("tag"), match.group("asset")
    # Two tags are legitimate: the floating `iconpack` (stable for Downloader
    # builds in the wild) and the versioned `v<versionName>` of this release.
    version = str(feed.get("versionName", ""))
    if tag not in {"iconpack", f"v{version}"}:
        problems.append(
            f"apkUrl names tag '{tag}', which is neither 'iconpack' nor 'v{version}'"
        )
    if expected_tag and tag != expected_tag:
        problems.append(f"apkUrl names tag '{tag}', expected '{expected_tag}'")
    if not asset.endswith(".apk"):
        problems.append(f"apkUrl names '{asset}', which is not an APK")
    return problems


def version_problems(feed: dict, manifest: dict, where: str) -> list[str]:
    """Every field the updater compares between the feed and the real APK."""
    problems = []
    for key in ("versionCode", "versionName"):
        if feed.get(key) != manifest.get(key):
            problems.append(
                f"the feed advertises {key}={feed.get(key)!r} but {where} carries "
                f"{key}={manifest.get(key)!r}"
            )
    return problems


def offline_problems(feed: dict, build: dict) -> list[str]:
    problems = url_problems(feed)
    if feed.get("versionCode", 0) > build.get("versionCode", 0):
        problems.append(
            "the published feed leads the build's own manifest "
            f"({feed.get('versionCode')} > {build.get('versionCode')}); the feed is "
            "stamped by the tag build, after the releases carry the APK"
        )
    if feed.get("versionName") not in {"", None} and not feed.get("versionCode"):
        problems.append("the feed names a version but carries no versionCode")
    return problems


def fetch(url: str, dest: Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "core-builds-gate"})
    with urllib.request.urlopen(request, timeout=120) as response:
        total = 0
        with dest.open("wb") as out:
            while True:
                chunk = response.read(256 * 1024)
                if not chunk:
                    break
                total += len(chunk)
                if total > MAX_BYTES:
                    raise ValueError(f"asset is larger than {MAX_BYTES} bytes")
                out.write(chunk)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--offline", action="store_true",
                        help="structural checks only; no network")
    parser.add_argument("--apk", type=Path,
                        help="check this APK against the feed instead of downloading")
    parser.add_argument("--feed", type=Path, default=FEED)
    args = parser.parse_args(argv)

    feed = read_json(args.feed)
    problems = offline_problems(feed, read_json(BUILD))
    if args.offline:
        for problem in problems:
            print(f"  x {problem}", file=sys.stderr)
        if problems:
            return 1
        print(f"feed is structurally sound (v{feed.get('versionName')} · "
              f"code {feed.get('versionCode')})")
        return 0

    apk = args.apk
    with tempfile.TemporaryDirectory() as scratch:
        if apk is None:
            url = str(feed.get("apkUrl", ""))
            apk = Path(scratch) / "feed.apk"
            try:
                fetch(url, apk)
            except Exception as e:  # network, 404, oversized: all "not downloadable"
                problems.append(f"could not download {url}: {e}")
            else:
                problems += url_problems(feed)
        if apk is not None and apk.exists():
            try:
                manifest = apk_manifest(apk)
            except Exception as e:
                problems.append(str(e))
            else:
                problems += version_problems(feed, manifest, "the APK at apkUrl")

    for problem in problems:
        print(f"  x {problem}", file=sys.stderr)
    if problems:
        print(
            "  The feed is written by the tag build after the releases carry the "
            "new APK. If the release is missing or older, publish (or rerun) the "
            "tag build rather than editing the feed by hand.",
            file=sys.stderr,
        )
        return 1
    print(f"apkUrl serves v{feed.get('versionName')} · code {feed.get('versionCode')}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
