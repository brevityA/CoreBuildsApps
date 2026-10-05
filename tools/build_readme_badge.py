#!/usr/bin/env python3
from __future__ import annotations
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
START = "<!-- suite-stamp:start -->"
END = "<!-- suite-stamp:end -->"

WHAT = {
    "iconpack": "{iconCount} transparent icons + {wallpapers} wallpapers for Projectivy Launcher",
    "line": "Sports scores & channel RSS ticker (chyron)",
    "shift": "Android TV screensaver + motion wallpaper browser",
    "motion": "Projectivy wallpaper-provider plugin for Core Motion loops",
    "doctor": "Local-only streaming and suite diagnostics (phone)",
    "eq": "Room EQ measured per TV audio output, with manual/content-mode tone controls",
}
ANCHOR = {
    "iconpack": "core-builds-icon-pack",
    "line": "core-line",
    "shift": "core-shift",
    "motion": "core-motion",
    "doctor": "core-doctor",
    "eq": "core-eq",
}

def block(suite: dict) -> str:
    lines = [
        START,
        "> | App | Current | What it does | Downloader | Release tag |",
        "> |---|---:|---|---|---|",
    ]
    wallpapers = json.loads((ROOT / "Wallpapers/manifest.json").read_text())["count"]
    for key in ["iconpack", "line", "shift", "motion", "doctor", "eq"]:
        app = suite["apps"][key]
        releases = "../../releases"
        downloader = app["downloader"]
        tag = f"[`{app['tagPrefix']}*` / `{app['floatingTag']}`]({releases})"
        lines.append(
            f"> | **[{app['name']}](#-{ANCHOR[key]})** | `v{app['versionName']}` | "
            f"{WHAT[key].format(wallpapers=wallpapers, **app)} | `{downloader}` | {tag} |"
        )
    lines += [
        ">",
        "> Each app has its own CI workflow and release tag. Do not merge Gradle roots, split the repo, or repoint floating Downloader tags.",
        # Hand-maintained, and deliberately not a suite.json entry: the
        # glyphs-only pack is the icon pack's square companion
        # (tv.corebuilds.iconpack.glyphs), not a suite product - no registry
        # row, no tag of its own. It ships in the pack's v* release, so the
        # code lives here where the other Downloader codes are read.
        # 5804177 resolves to iconpack-glyphs-release.apk, the companion;
        # the standalone glyphs flavor (corebuilds-glyphs-release.apk) is
        # URL-only. Never repoint either at another asset.
        "> The Icon Pack release also carries a glyphs-only pack — square art, no banners (`tv.corebuilds.iconpack.glyphs`) — Downloader `5804177`.",
        END,
    ]
    return "\n".join(lines)

def main() -> int:
    suite = json.loads((ROOT / "suite.json").read_text())
    readme_path = ROOT / "README.md"
    readme = readme_path.read_text()
    stamped = block(suite)
    if START in readme and END in readme:
        before, rest = readme.split(START, 1)
        _, after = rest.split(END, 1)
        new = before + stamped + after
    else:
        marker = "> | App | Current | What it does | Downloader | Release tag |"
        if marker not in readme:
            raise SystemExit("README stamp table marker not found")
        before = readme[:readme.index(marker)]
        after = readme[readme.index("\n---", readme.index(marker)):]
        new = before + stamped + after
    readme_path.write_text(new)
    print("README suite stamp updated from suite.json")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
