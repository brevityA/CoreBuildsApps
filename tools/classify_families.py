"""Work out what the 549 tile-backed apps actually are.

The catalog puts 455 of them in a generic APP bucket, which is why they all
ended up as the same letter in the same box: nothing in the data separated a
Dutch catch-up service from a USB tool. Package IDs carry that signal.

The families here are chosen to produce distinguishable SILHOUETTES, not to be
a taxonomy. "Live TV" and "catch-up" collapse into one broadcaster family
because no regional network app is usefully one and not the other, and a
viewer scanning a home screen cannot see the difference anyway.

Specific rules run before general ones; the first hit wins. Anything that
matches nothing keeps a neutral container rather than being forced into a
family it does not belong to.
"""
import json, re, collections, pathlib, sys

ROOT = pathlib.Path("/home/user/corebuildsiconpack")
cat = json.loads((ROOT / "tools/catalog.json").read_text())["icons"]

RULES = [
    # --- unambiguous function words, checked before anything TV-shaped ---
    ("vpn",     r"\bvpn\b|wireguard|openvpn|shadowsock|byedpi|psiphon|\bwarp\b|tunnel|"
                r"clash|surfshark|nord|mullvad|proton(?!.*mail)|torguard|getflix"),
    ("gaming",  r"\bgame|gaming|emulat|retro|arcade|playstation|xbox|steam|geforce|"
                r"boosteroid|xcloud|betterxc|puzzle|nvidia|luna\b|daijishou|"
                r"ppsspp|uae4arm|dolphin|mame|snes|nes\b|psx"),
    ("browser", r"browser|chrome|firefox|opera|vewd|webview|\bweb\b"),
    ("store",   r"store|market|bazaar|aptoide|applinked|apk|installer|updater|downloader|"
                r"\bi4\b|filesynced"),
    ("files",   r"explorer|folder|\bftp\b|\bsmb\b|\bnas\b|drive|sync(?!ler)|commander|"
                r"x-?plore|solid ?explorer|file ?manager|anexplorer|package"),
    ("tool",    r"tool|setedit|notifier|screensaver|remap|keyboard|\badb\b|debug|aida|"
                r"anydesk|monitor|analiti|network|wifi|setting|cleaner|backup|widget|"
                r"remote|mouse|speed|helper|aerial|background|process|scrcpy|virtualhere|"
                r"sd ?maid|twilight|lux\b|protect|unifi|weather|scholastic|texttv|"
                r"screenscape|tvoverlay|overlay|buttons|remap|coreelec|dangbei"),
    ("music",   r"music|radio|audio|podcast|spotify|deezer|tidal|tuner|audials|soundcloud|"
                r"\bfm\b|jukebox|lyric|scrobbl|speaker|volume|boost|wiim|linkplay"),
    ("photos",  r"photo|gallery|collage|smugmug|snapwood|kailash|picture|image|slideshow"),
    ("debrid",  r"debrid|put\.?io|pikpak|premiumize|torbox|alldebrid|offcloud|seedr|"
                r"torrserve|torrent|magnet|nzb|usenet|stash"),
    ("sport",   r"sport|football|soccer|\bnba\b|\bnfl\b|\bnhl\b|\bmlb\b|\bufc\b|\bf1\b|"
                r"cricket|tennis|golf|dazn|league|match|race|wrestl|espn|peloton|fitness"),
    ("anime",   r"anime|manga|crunchy|bstation|bilibili|otaku|kitsu|seadex"),
    ("kids",    r"\bkids\b|cartoon|junior|\bbaby\b|child|toon"),
    ("film",    r"\bfilm|movie|cinema|\bkino\b|\bcine|documentar|\bvod\b"),
    # --- broadcaster: anything that is a channel, network or streaming service ---
    ("broadcast", r"\btv\b|television|canal|kanal|channel|zender|cha[iî]ne|t[ée]l[ée]|sender|"
                  r"broadcast|iptv|xtream|m3u|stalker|smarters|\bott\b|playlist|"
                  r"\bplay\b|player|\bnow\b|\bgo\b|replay|gemist|mediathek|demand|"
                  r"stream|\bplus\b|\+|flix|watch|view|media|video|cast|live"),
]
COMPILED = [(fam, re.compile(rx, re.I)) for fam, rx in RULES]

CATEGORY_MAP = {
    "VPN": "vpn", "GAMING": "gaming", "MUSIC": "music", "SPORT": "sport",
    "STORE": "store", "FILES": "files", "TOOL": "tool", "BROWSER": "browser",
    "LIVE": "broadcast", "VOD": "broadcast", "VIDEO": "broadcast",
    "PLAYER": "broadcast", "STREAM": "broadcast", "DEBRID": "tool",
    "LAUNCHER": "tool",
}


def classify(icon):
    c = icon.get("category")
    if c in CATEGORY_MAP:
        return CATEGORY_MAP[c]
    hay = icon["name"] + "|" + "|".join(icon.get("components") or [])
    for fam, rx in COMPILED:
        if rx.search(hay):
            return fam
    return "app"


if __name__ == "__main__":
    tiles = [i for i in cat if str(i["glyph"]).startswith("tile_")]
    fams = collections.Counter(classify(i) for i in tiles)
    print(f"tile-backed apps: {len(tiles)}\n")
    for k, v in fams.most_common():
        print(f"  {v:4d}  {k}")
    if "--show" in sys.argv:
        want = sys.argv[sys.argv.index("--show") + 1]
        print(f"\n--- {want} ---")
        for i in tiles:
            if classify(i) == want:
                comp = (i.get("components") or [""])[0].split("/")[0]
                print(f"   {i['name'][:30]:<30} {comp[:44]}")
