#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import date
from pathlib import Path

APPS = {
    "iconpack": {
        "gradle": "app/build.gradle.kts",
        "metadata": "Latestrelease/version.json",
        "apk": "iconpack-release.apk",
        "tag": "iconpack",
        "minSdk": 21,
    },
    "pixelneon": {
        "gradle": "pixel-neon/app/build.gradle.kts",
        "metadata": "Latestrelease/pixel-neon-version.json",
        "apk": "pixel-neon-release.apk",
        "tag": "pixel-neon",
        "minSdk": 21,
    },
    "coreline": {
        "gradle": "ticker/android/app/build.gradle.kts",
        "metadata": "Latestrelease/coreline-version.json",
        "apk": "coreline-release.apk",
        "tag": "coreline",
        "minSdk": 24,
    },
    "coreshift": {
        "gradle": "shift/app/build.gradle.kts",
        "metadata": "Latestrelease/shift-version.json",
        "apk": "coreshift-release.apk",
        "tag": "shift",
        "minSdk": 26,
    },
}


def gradle_value(text: str, name: str) -> str:
    match = re.search(rf'{name}\s*=\s*(?:"([^"]+)"|(\d+))', text)
    if not match:
        raise SystemExit(f"missing {name}")
    return match.group(1) or match.group(2)


def sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as apk:
        for chunk in iter(lambda: apk.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def iso_date(value: str) -> str:
    """Argparse validator for an explicit, reproducible YYYY-MM-DD date."""
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        raise argparse.ArgumentTypeError("release date must use YYYY-MM-DD")
    try:
        date.fromisoformat(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError(str(error)) from error
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("app", choices=APPS)
    parser.add_argument("--apk", required=True)
    parser.add_argument(
        "--release-date",
        required=True,
        type=iso_date,
        help="release date in YYYY-MM-DD form (never inferred from wall-clock time)",
    )
    parser.add_argument("--out")
    args = parser.parse_args()

    config = APPS[args.app]
    gradle = Path(config["gradle"]).read_text(encoding="utf-8")
    metadata_path = Path(config["metadata"])
    current = (
        json.loads(metadata_path.read_text(encoding="utf-8"))
        if metadata_path.exists()
        else {}
    )
    data = {
        **current,
        "versionCode": int(gradle_value(gradle, "versionCode")),
        "versionName": gradle_value(gradle, "versionName"),
        "releaseDate": args.release_date,
        "apkUrl": (
            "https://github.com/brevityA/CoreBuildsApps/releases/download/"
            f"{config['tag']}/{config['apk']}"
        ),
        "apkSha256": sha256(args.apk),
        "releaseNotesUrl": "https://github.com/brevityA/CoreBuildsApps/releases",
        "minSdk": config["minSdk"],
    }
    output = Path(args.out or config["metadata"])
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(
        f"wrote {output}: {data['versionName']} code {data['versionCode']} "
        f"date {data['releaseDate']} sha {data['apkSha256']}"
    )


if __name__ == "__main__":
    main()
