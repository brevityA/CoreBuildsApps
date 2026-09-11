#!/usr/bin/env python3
"""Apply brand-colour remediation to tools/catalog.json with provenance.

Two things happen here, deliberately separate:

1. PROVENANCE for all 925 icons. Every colour records where it came from.
   Before this, `color` had no origin at all — which is how 692 icons came to
   carry a cycled decorative palette while looking brand-researched.

2. CORRECTIONS, only where a source is citable and the result stays legible on
   the pack's dark card. Nothing is bulk-applied from simple-icons: its hex is
   the maintainers' pick, frequently the wordmark colour rather than the brand
   accent, and 10 of the 40 divergences would collapse to the light-ink
   fallback and lose the brand signal entirely. Those are left alone and
   reported, not silently degraded.

Writes nothing unless --write. Prints the receipt either way.
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

ROOT = Path("/home/user/base")
CATALOG = ROOT / "tools" / "catalog.json"
TODAY = "2026-09-08"
sys.path.insert(0, str(ROOT / "tools"))
from icon_style import MIN_CONTRAST, contrast, display_accent, LIGHT_INK  # noqa: E402

PACK_PALETTE = "core-builds palette (no published brand colour found)"

# Corrections with a citable source, hand-reviewed one at a time.
# Rejected from this list, with reasons:
#   DAZN     #F8F8F5 is the wordmark white, not the brand yellow -> would erase it
#   Max      #525252 is the wordmark grey; the brand is blue/purple
#   Trakt    simple-icons #9F42C6 contradicts Trakt's own red; unresolved
#   Dailymotion / Maze / NOW / Peloton / RetroArch / Shadow / Mpv / Pandora /
#   FIFA+ / Tubi  -> land under MIN_CONTRAST and become light ink
#   Foxtel / Kick / Sky+ / Tailscale -> delta under 6, not worth a full rebuild
FIXES = {
    # catalog NAME (not drawable — drawable names are reused glyph ids: the
    # icon called "MLB" is drawable `bamnetworks`, "NBA" is `gametime`,
    # "Channel 4" is `ondemand`). Keying on drawable silently no-ops.
    # name: (hex, source, note)
    "10 Play": ("#0047F4", "https://brandfetch.com/10play.com.au",
                "10 Play brand primary 'Blue Ribbon'"),
    "Audiomack": ("#FFA200", "https://simpleicons.org/?q=audiomack", "simple-icons brand accent"),
    "Channel 4": ("#AAFF89", "https://simpleicons.org/?q=channel4", "Channel 4 block green"),
    "Fubo": ("#C83D1E", "https://simpleicons.org/?q=fubo", "simple-icons brand accent"),
    "Instagram": ("#FF0069", "https://simpleicons.org/?q=instagram", "simple-icons brand accent"),
    "Kinopoisk": ("#FF5500", "https://simpleicons.org/?q=kinopoisk", "simple-icons brand accent"),
    "LocalSend": ("#008080", "https://simpleicons.org/?q=localsend",
                  "simple-icons brand accent; catalog had the pack's own cyan"),
    "MLB": ("#041E42", "https://simpleicons.org/?q=mlb", "simple-icons brand accent"),
    "Nasa": ("#E03C31", "https://simpleicons.org/?q=nasa", "NASA insignia red"),
    "NBA": ("#253B73", "https://simpleicons.org/?q=nba", "simple-icons brand accent"),
    "Nebula": ("#2CADFE", "https://simpleicons.org/?q=nebula", "simple-icons brand accent"),
    "Neon": ("#34D59A", "https://simpleicons.org/?q=neon", "simple-icons brand accent"),
    "Newpipe": ("#CD201F", "https://simpleicons.org/?q=newpipe", "simple-icons brand accent"),
    "Obtainium": ("#D2BCFD", "https://simpleicons.org/?q=obtainium", "simple-icons brand accent"),
    "Podcast Addict": ("#F4842D", "https://simpleicons.org/?q=podcastaddict", "simple-icons brand accent"),
    "Private Internet Access": ("#1E811F", "https://simpleicons.org/?q=privateinternetaccess",
                                "simple-icons brand accent"),
    "RTL": ("#FA002E", "https://simpleicons.org/?q=rtl", "RTL brand red"),
    "Smugmug": ("#6DB944", "https://simpleicons.org/?q=smugmug", "simple-icons brand accent"),
    "Surfshark": ("#1EBFBF", "https://simpleicons.org/?q=surfshark", "simple-icons brand accent"),
    "Ted": ("#E62B1E", "https://simpleicons.org/?q=ted", "TED brand red"),
    "Tv4 Play": ("#E0001C", "https://simpleicons.org/?q=tv4play", "simple-icons brand accent"),
    "Viaplay": ("#FE365F", "https://simpleicons.org/?q=viaplay", "simple-icons brand accent"),
    "Zdf": ("#FA7D19", "https://simpleicons.org/?q=zdf", "ZDF brand orange"),
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true")
    args = ap.parse_args()

    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = data["icons"]
    cycled = {h for h, n in Counter(i["color"].upper() for i in icons).items() if n >= 20}

    applied, skipped, drawn = [], [], []
    for i in icons:
        d = i["name"]
        if d in FIXES:
            new, src, note = FIXES[d]
            if contrast(new) < MIN_CONTRAST:
                # Still record provenance. Bailing here left these three with
                # no origin at all, which is the exact hole this script closes.
                skipped.append((i["name"], new, f"contrast {contrast(new):.2f}"))
                i["color_source"] = PACK_PALETTE if i["color"].upper() in cycled \
                    else "brand reference (pre-provenance, unverified)"
                i["color_note"] = (f"true brand accent {new} rejected: contrast "
                                   f"{contrast(new):.2f} < {MIN_CONTRAST}")
                i["color_reviewed"] = TODAY
                continue
            if i["color"].upper() != new.upper():
                applied.append((i["name"], i["color"], new, note))
            i["color"] = new
            i["color_source"] = src
            i["color_note"] = note
            i["color_reviewed"] = TODAY
        elif i["color"].upper() in cycled:
            i["color_source"] = PACK_PALETTE
            i["color_reviewed"] = None
            drawn.append(i["name"])
        else:
            # Single-use colour: treated as already researched, but say so
            # rather than leaving the origin unknown.
            i.setdefault("color_source", "brand reference (pre-provenance, unverified)")
            i.setdefault("color_reviewed", None)

    # AGENTS.md: brand groups must share glyph AND accent.
    bad = []
    groups: dict[str, set] = {}
    for i in icons:
        if i.get("brand"):
            groups.setdefault(i["brand"], set()).add(i["color"].upper())
    for b, cols in groups.items():
        if len(cols) > 1:
            bad.append((b, sorted(cols)))

    print(f"corrections applied      : {len(applied)}")
    print(f"icons marked pack-palette: {len(drawn)}")
    print(f"rejected (illegible)     : {len(skipped)}")
    if bad:
        print(f"\n!! brand groups with divergent accents: {bad}")
        return 1
    print("brand-group accents      : consistent")

    print("\n=== applied ===")
    for n, o, new, note in sorted(applied):
        print(f"  {n[:26]:26} {o} -> {new}   {note}")
    if skipped:
        print("\n=== rejected ===")
        for n, new, why in skipped:
            print(f"  {n[:26]:26} {new}  {why}")

    if args.write:
        CATALOG.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n",
                           encoding="utf-8")
        print(f"\nwrote {CATALOG}")
    else:
        print("\n(dry run — pass --write to apply)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
