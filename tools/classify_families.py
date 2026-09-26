"""Classify generic app-letter glyphs by the job their app actually does.

The catalogue deliberately keeps a neutral ``app_*`` fallback when evidence is
not strong enough to draw or assign a functional cue.  For the rows where the
package/name evidence is clear, the family shell replaces a bare app letter
with a Core-authored browser, broadcaster, tool, sport, music, file, or other
functional container.  The app's short ``mark`` remains inside the shell, so
this is not vendor artwork and does not invent a logo.

This is a source-of-truth helper, not a runtime classifier.  It only changes
entries whose current glyph is ``app_<letter-or-digit>`` and is deterministic:
run it with ``--write`` before running the normal icon and banner builders.
Unknown or ambiguous apps remain on their neutral app shell and are reported
so a later evidence pass can revisit them without silently guessing.
"""
from __future__ import annotations

import argparse
import collections
import json
import pathlib
import re
from typing import Iterable

ROOT = pathlib.Path(__file__).resolve().parents[1]
CATALOG_PATH = ROOT / "tools" / "catalog.json"

# Specific product/function evidence wins over broad words below.  These are
# intentionally small corrections for names whose package or title contains a
# word with a misleading generic meaning (for example Better XC is IPTV, not
# gaming; Oilers+ is sport, not a game).
OVERRIDES = {
    "ace stream": "broadcast",
    "all saves social": "tool",
    "allsaves social": "tool",
    "amnis": "broadcast",
    "ant1": "broadcast",
    "airplay receiver": "tool",
    "aniken tv": "broadcast",
    "anikentv": "broadcast",
    "blip": "broadcast",
    "bp box": "broadcast",
    "brollie": "film",
    "byutv": "broadcast",
    "ctv": "broadcast",
    "cue new": "broadcast",
    "d-smart": "broadcast",
    "dig": "gaming",
    "dramox": "film",
    "echogram": "music",
    "elefin": "broadcast",
    "es de frontend": "broadcast",
    "etube": "broadcast",
    "findlink": "tool",
    "firesend": "tool",
    "fladder": "broadcast",
    "fp basquetebol": "sport",
    "francetv": "broadcast",
    "full episodes": "broadcast",
    "get icon": "tool",
    "goplay": "broadcast",
    "guideplus": "tool",
    "hrti": "broadcast",
    "hubitat": "tool",
    "hue shortcuts": "tool",
    "hueessentials": "tool",
    "kennytv": "broadcast",
    "kijk": "broadcast",
    "lampa": "broadcast",
    "loco": "broadcast",
    "scb next": "broadcast",
    "u": "broadcast",
    "better xc": "broadcast",
    "bell fibe": "broadcast",
    "bravia core": "broadcast",
    "earthcam": "broadcast",
    "eternal tv (nath)": "broadcast",
    "fox nation": "broadcast",
    "fox one": "broadcast",
    "f-droid": "store",
    "f droid": "store",
    "flicky": "store",
    "fluffy": "files",
    "gallery 3d": "photos",
    "gain": "film",
    "genplay": "gaming",
    "gymondo": "sport",
    "hidive": "anime",
    "hei network": "broadcast",
    "joyn": "broadcast",
    "karaoke": "music",
    "kpn": "broadcast",
    "launchbox": "gaming",
    "laugh after dark": "film",
    "lemino": "broadcast",
    "liga portugal": "sport",
    "localnow": "broadcast",
    "m64plus fz": "gaming",
    "maze": "gaming",
    "meo": "broadcast",
    "mi gallery": "photos",
    "moonfin": "broadcast",
    "mutv": "sport",
    "netmirror": "broadcast",
    "netmirror tv": "broadcast",
    "nettv": "broadcast",
    "netzkino": "film",
    "nfb": "film",
    "njpw world": "sport",
    "notubetv": "broadcast",
    "nxsha": "anime",
    "oilers+": "sport",
    "oblivion": "vpn",
    "avoid": "broadcast",
    "ontv": "broadcast",
    "oqee by free": "broadcast",
    "orf on": "broadcast",
    "ottplay": "broadcast",
    "pathethuis": "film",
    "playkids": "kids",
    "playnet": "broadcast",
    "quicksupport": "tool",
    "raiplay": "broadcast",
    "rezka": "film",
    "seerrtv": "broadcast",
    "shark tv": "broadcast",
    "sledovani": "broadcast",
    "strim": "broadcast",
    "sunnxt": "broadcast",
    "tablo": "broadcast",
    "talk tv": "broadcast",
    "tcl home": "tool",
    "the weather network": "broadcast",
    "tod": "sport",
    "tfc": "broadcast",
    "tf1": "broadcast",
    "unifi tv": "broadcast",
    "veezie": "broadcast",
    "viaplay": "broadcast",
    "vidangel": "broadcast",
    "viki": "broadcast",
    "vmx": "broadcast",
    "voyo sk": "broadcast",
    "vrt max": "broadcast",
    "wako": "broadcast",
    "weatherbug": "tool",
    "wiim": "music",
    "wow2": "broadcast",
    "yle areena": "broadcast",
    "zona": "broadcast",
}

# Rules use both the human-facing name and the mapped package/component.  The
# order is deliberate: a narrow utility or network family gets first refusal;
# the final broadcast rule catches TV/video/live services.
RULES = [
    (
        "vpn",
        r"\bvpn\b|wireguard|openvpn|shadowsock|byedpi|psiphon|\bwarp\b|tunnel|"
        r"clash|surfshark|nord|mullvad|proton(?!.*mail)|torguard|getflix|oblivion",
    ),
    (
        "gaming",
        r"\bgame|gaming|emulat|retro|arcade|playstation|xbox|steam|geforce|"
        r"boosteroid|xcloud|betterxc|puzzle|nvidia|luna\b|daijishou|ppsspp|"
        r"uae4arm|dolphin|mame|snes|nes\b|psx|launchbox|m64plus",
    ),
    ("browser", r"browser|chrome|firefox|opera|vewd|webview|\bweb\b"),
    (
        "store",
        r"store|market|bazaar|aptoide|applinked|apk|installer|updater|downloader|"
        r"\bi4\b|filesynced|fdroid|f-droid",
    ),
    (
        "files",
        r"explorer|folder|\bftp\b|\bsmb\b|\bnas\b|drive|sync(?!ler)|commander|"
        r"x-?plore|solid ?explorer|file ?manager|anexplorer|package",
    ),
    (
        "tool",
        r"tool|setedit|notifier|screensaver|remap|keyboard|\badb\b|debug|aida|"
        r"anydesk|monitor|analiti|network|wifi|setting|cleaner|backup|widget|"
        r"remote|mouse|speed|helper|aerial|background|process|scrcpy|virtualhere|"
        r"sd ?maid|twilight|lux\b|protect|unifi|weather|scholastic|texttv|"
        r"screenscape|tvoverlay|overlay|buttons|coreelec|dangbei|dns|drm",
    ),
    (
        "music",
        r"music|radio|audio|podcast|spotify|deezer|tidal|tuner|audials|soundcloud|"
        r"\bfm\b|jukebox|lyric|scrobbl|speaker|volume|boost|wiim|linkplay|karaoke",
    ),
    (
        "photos",
        r"photo|gallery|collage|smugmug|snapwood|kailash|picture|image|slideshow|"
        r"fotoo|flickfolio",
    ),
    (
        "debrid",
        r"debrid|put\.?io|pikpak|premiumize|torbox|alldebrid|offcloud|seedr|"
        r"torrserve|torrent|magnet|nzb|usenet|stash",
    ),
    (
        "sport",
        r"sport|football|soccer|\bnba\b|\bnfl\b|\bnhl\b|\bmlb\b|\bufc\b|\bf1\b|"
        r"cricket|tennis|golf|dazn|league|match|race|wrestl|espn|peloton|fitness|"
        r"oilers|gymondo|mutv|tod",
    ),
    ("anime", r"anime|manga|crunchy|bstation|bilibili|otaku|kitsu|seadex|hidive|nxsha"),
    ("kids", r"\bkids\b|cartoon|junior|\bbaby\b|child|toon|playkids"),
    ("film", r"\bfilm|movie|cinema|\bkino\b|\bcine|documentar|\bvod\b|netzkino"),
    (
        "broadcast",
        r"\btv\b|television|canal|kanal|channel|zender|cha[iî]ne|t[ée]l[ée]|sender|"
        r"broadcast|iptv|xtream|m3u|stalker|smarters|\bott\b|playlist|\bplay\b|"
        r"player|\bnow\b|\bgo\b|replay|gemist|mediathek|demand|stream|\bplus\b|"
        r"\+|flix|watch|view|media|video|cast|live|earthcam|moonfin|seerr",
    ),
]
COMPILED_RULES = [(family, re.compile(pattern, re.IGNORECASE)) for family, pattern in RULES]

CATEGORY_MAP = {
    "VPN": "vpn",
    "GAMING": "gaming",
    "MUSIC": "music",
    "SPORT": "sport",
    "STORE": "store",
    "FILES": "files",
    "TOOL": "tool",
    "BROWSER": "browser",
    "LIVE": "broadcast",
    "VOD": "broadcast",
    "VIDEO": "broadcast",
    "PLAYER": "broadcast",
    "STREAM": "broadcast",
    "DEBRID": "tool",
    "LAUNCHER": "tool",
}


def evidence_text(icon: dict) -> str:
    return "|".join(
        [icon.get("name", ""), *[str(c) for c in icon.get("components", [])]]
    )


def classify(icon: dict) -> str:
    """Return a functional family, or ``app`` when evidence is ambiguous."""
    override = OVERRIDES.get(icon.get("name", "").casefold())
    if override:
        return override
    category = icon.get("category")
    if category in CATEGORY_MAP:
        return CATEGORY_MAP[category]
    haystack = evidence_text(icon)
    for family, pattern in COMPILED_RULES:
        if pattern.search(haystack):
            return family
    return "app"


def family_glyph(icon: dict, family: str) -> str:
    current = str(icon["glyph"])
    suffix = current.rsplit("_", 1)[-1]
    if family == "app":
        return current
    return f"{family}_{suffix}"


def candidates(icons: Iterable[dict]) -> list[tuple[dict, str, str]]:
    rows = []
    for icon in icons:
        if not str(icon.get("glyph", "")).startswith("app_"):
            continue
        family = classify(icon)
        updated = family_glyph(icon, family)
        rows.append((icon, family, updated))
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="write classified glyphs to catalog.json")
    parser.add_argument("--show", metavar="FAMILY", help="list candidates assigned to one family")
    args = parser.parse_args()

    data = json.loads(CATALOG_PATH.read_text())
    rows = candidates(data["icons"])
    counts = collections.Counter(family for _, family, _ in rows)
    print(f"generic app-letter rows: {len(rows)}")
    print("  " + "  ".join(f"{family}={counts[family]}" for family, _ in RULES) + f"  app={counts['app']}")

    if args.show:
        for icon, family, updated in rows:
            if family == args.show:
                print(f"{icon['name']}\t{icon['glyph']} -> {updated}")

    if args.write:
        changed = 0
        for icon, _, updated in rows:
            if icon["glyph"] != updated:
                icon["glyph"] = updated
                changed += 1
        CATALOG_PATH.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
        print(f"wrote {changed} functional family assignments; {counts['app']} neutral rows retained")


if __name__ == "__main__":
    main()
