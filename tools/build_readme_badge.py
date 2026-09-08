#!/usr/bin/env python3
from __future__ import annotations
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
START = "<!-- suite-stamp:start -->"
END = "<!-- suite-stamp:end -->"

WHAT = {
    "iconpack": "{iconCount} transparent icons + 50 wallpapers for Projectivy Launcher",
    "pixelneon": "{iconCount} transparent 8-bit neon icons + 70 wallpapers for Projectivy Launcher",
    "pop": "The same {iconCount} icons, pop-art cartoon: 16 swatches, one container",
    "line": "Sports scores & channel RSS ticker (chyron)",
    "shift": "Android TV screensaver + motion wallpaper browser",
    "motion": "Projectivy wallpaper-provider plugin for Core Motion loops",
    "doctor": "Local-only streaming and suite diagnostics (phone)",
}
ANCHOR = {
    "iconpack": "icon-pack",
    "pixelneon": "pixel-neon-icon-pack",
    "pop": "core-builds-pop",
    "line": "core-line",
    "shift": "core-shift",
    "motion": "core-motion",
    "doctor": "core-doctor",
}

def block(suite: dict) -> str:
    lines = [
        START,
        "> | App | Current | What it does | Downloader | Release tag |",
        "> |---|---:|---|---|---|",
    ]
    for key in ["iconpack", "pixelneon", "pop", "line", "shift", "motion", "doctor"]:
        app = suite["apps"][key]
        releases = "../../releases"
        downloader = app["downloader"]
        tag = f"[`{app['tagPrefix']}*` / `{app['floatingTag']}`]({releases})"
        lines.append(
            f"> | **[{app['name']}](#-{ANCHOR[key]})** | `v{app['versionName']}` | "
            f"{WHAT[key].format(**app)} | `{downloader}` | {tag} |"
        )
    lines += [
        ">",
        "> Each app has its own CI workflow and release tag. Do not merge Gradle roots, split the repo, or repoint floating Downloader tags.",
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
