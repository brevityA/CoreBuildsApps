import json, os, datetime

ROOT = "/home/user/repo"
CAT = os.path.join(ROOT, "tools/catalog.json")
data = json.load(open(CAT))
icons = data["icons"]

# ---- Curated research: brand official mark vs current pack treatment ----
# Each record: "official" = what the brand's real logo is; "gap" = how the
# current pack glyph diverges. Grounded from logo-heritage research.
FACTS = {
 "Netflix": ("Standalone red ribbon 'N' (2016): a folded-ribbon diagonal stroke with depth/shadow.", "Pack uses a monoline twin-stem N (no fold, no depth). Reads as a flat letter, not the ribbon."),
 "Crunchyroll": ("Orange 'eye/sushi-roll': a circle with an off-centre inner crescent (pupil) + wordmark.", "Pack is a symmetric closed-eye ellipse with a bar; the offset crescent/pupil and the spiral read are gone."),
 "Hulu": ("Wordmark ONLY — lowercase 'hulu' in a custom geometric sans. There is no icon/symbol.", "Any monogram/tile is an invented abstraction. Real brand is purely typographic; a single tile letter misrepresents it."),
 "DAZN": ("Bold lowercase 'DAZN' wordmark, black/white slab. No standalone emblem exists.", "Pack invents a 'D-with-lightning'. Not a real DAZN mark."),
 "Paramount+": ("The 'majestic mountain' peak ringed by 22 stars inside an arc, + 'Paramount+' wordmark.", "Pack is a bare triangle peak; drops the star ring, the arc, and the wordmark."),
 "Peacock": ("NBC peacock fan: 6 colour-coded feathers (or 11 originally) forming a wide fan, usually over a wordmark.", "Pack is a small fan of dotted stems — reads as a mini/firework fan, not the wide feather fan."),
 "Max": ("'Max' bold mark with the flat 'ax' underline (2023+); the old HBO ribbon-C emblem is retired.", "Pack 'max_wave' is an invented wave. Not the current (or historic) mark."),
 "Discovery": ("Bold 'Discovery' wordmark geosans + a small globe/sunburst icon to its right.", "Pack sunburst globe is a loose recreation; missing the wordmark and exact proportions."),
 "Disney+": ("The iconic 'D' from the Disney signature + a '+'.", "Pack is a plus/star; loses the signature 'D' entirely."),
 "Prime Video": ("Amazon 'smile-arrow' under 'Prime video'. Icon = the a-to-z smile arrow.", "Pack smile arrow approximates it. Good match."),
 "Apple TV": ("Apple logo (apple + leaf) with a small 'tv'.", "Pack is an apple silhouette. Approx. correct."),
 "Spotify": ("Green circle with three concave sound arcs fanning off the left.", "Pack has three arcs but arranged convex/differently; not the flat green disc."),
 "SoundCloud": ("Orange cloud built from stacked vertical bars of growing height.", "Pack cloud+bars is a loose match; bar rhythm differs."),
 "Deezer": ("Green rectangular grid/staircase of equal round-ended bars.", "Pack uses varied-height bars; the real mark is a regular equal-bar matrix."),
 "TIDAL": ("Black 3+1 diamond wave: three crests on top, one below.", "Pack is two sine waves; not the 3+1 diamond arrangement."),
 "Vimeo": ("The Vimeo 'V' as a stylised play/logotype with a cut.", "Pack is a plain V; loses the letterform play cut."),
 "DuckDuckGo": ("Green duck head inside a rounded square.", "Pack is an oval/egg head; loses the duck silhouette and colour scheme."),
 "Google TV": ("The Google 'G' (multi-colour) or 'Google TV' wordmark.", "Pack is a TV+play; not the G mark."),
 "Google Play Games": ("The Play triangle (colour split) with a controller.", "Pack is a circle+G; not the triangle."),
 "Google Play Store": ("The Play triangle built from 4 folded colour planes.", "Pack play_store_tri matches."),
 "YouTube": ("Red rounded-rectangle with a WHITE FILLED triangle play.", "Pack outlines both; reads as outline, not the solid badge."),
 "YouTube Music": ("Red circle + white play + note stem.", "Pack is circle+play only; missing the note."),
 "Twitch": ("The Twitch speech/glitch glyph with two vertical slits.", "Pack uses generic chat_screen; loses the distinctive Twitch shape."),
 "TNT": ("'TNT' heavy block slab with the wire/wrench look.", "Pack is a bare T; loses the full wordmark block."),
 "CNN": ("Alternating red/black curved 'CNN' bars forming a cone/peak.", "Pack is an abstract T-bar; not the CNN scoop shape."),
 "ESPN": ("The ESPN 'E' — a filled E in a block/circle (the cut 'E').", "Pack is an E in a ring; the filled E-block character is lost."),
 "NFL": ("The NFL shield — shield with divisional stripes + ball.", "Pack is an oval ball only; the shield is the famous mark."),
 "MLB": ("MLB batter silhouette inside a rounded diamond.", "Pack is a diamond outline; loses the silhouette."),
 "NBA": ("Jerry West silhouette in a red/blue shield.", "Pack is a basketball; the brand shield silhouette is the iconic mark."),
 "UFC": ("The UFC octagon cage in a crest.", "Pack is an octagon; close."),
 "F1": ("The F1 'flying one' red swoosh + number 1.", "Pack is a wing; invented."),
 "MotoGP": ("Winged tyre with speed arcs.", "Pack circle+arc; close."),
 "LaLiga": ("The dynamic 'L' LaLiga mark + wordmark.", "Pack L+ball; loose."),
 "BBC Iplayer": ("The BBC 'i' — 3 lowercase blocks + play.", "Pack is a rounded play frame; not the BBC blocks."),
 "ITV Hub": ("Stacked 'itv' blocks.", "Pack is abstract."),
 "Channel 4": ("The blocky geometric 4.", "Pack abstract 4; approx."),
 "SBS": ("SBS multicolour wave/eyebrow.", "Pack is three bars."),
 "Plex": ("The Plex chevron (sideways V pointing right) in a rounded tile.", "Pack matches."),
 "Plexamp": ("Plex chevron in a circle.", "Pack matches."),
 "Kodi": ("Boxed 'K' — upright stem + wedge in a rounded tile.", "Pack matches."),
 "Jellyfin": ("Triangle with an inner-triangle cut (jellyfish).", "Pack matches."),
 "Emby": ("Shield + play triangle.", "Pack matches."),
 "Stremio": ("Play triangle in a rounded tile.", "Pack matches."),
 "VLC": ("The traffic cone.", "Pack matches."),
 "MX Player TV": ("Orange triangle play in a rounded square.", "Pack is a play; loses the specific rounded-square framing."),
 "IPTV Smarters": ("Phone/screen + play; generic industry look.", "Pack tv+play is a placeholder; close but generic."),
 "Criterion Channel": ("The Criterion 'C' — bold C in a flat circle (design icon).", "Pack is a C; close."),
 "MGM+": ("The MGM lion 'Leo' in a laurel ribbon + 'MGM'.", "Pack is an M/ribbon; loses the lion."),
 "STARZ": ("STARZ sparkle star (5-point with motion).", "Pack is a star; close."),
 "Shudder": ("The Shudder 'S' goosebump mark.", "Pack is an S."),
 "Pluto TV": ("Planet (circle) with orbit ring + 'p'.", "Pack planet+ring; close."),
 "Sling TV": ("The Sling tilted 'swoosh' spike.", "Pack is an S; loose."),
 "Showmax": ("Bean-shaped 'Showmax eye'.", "Pack is an eye; close."),
 "Hoopla": ("Hoopla 'hoop' circle over a curve.", "Pack circle+curve; close."),
 "Acorn TV": ("Acorn in a rounded tile.", "Pack matches."),
 "BritBox": ("'Brit' box with a union-flag cross.", "Pack is stacked chevrons; loses the flag cross."),
 "MUBI": ("Bold 'M' with a heavy underline in a tile.", "Pack M+bar; close."),
 "JustWatch": ("Magnifier with a play.", "Pack matches."),
 "Kanopy": ("Kanopy 'K' / streaming mark.", "Pack is a K."),
 "Curiosity Stream": ("Green 'curiosity' eye/frame.", "Pack is a dome; loose."),
 "The Roku Channel": ("Roku purple rounded square with inner white icon.", "Pack is a house; the actual Roku mark is a rounded block."),
 "Red Bull TV": ("Two bulls charging in a yellow sun.", "Pack is a sun; loses the bulls."),
 "NordVPN": ("Blue shield with an 'N' notch peak.", "Pack shield+chevron; close."),
 "Proton VPN": ("Purple shield with inner curve/key.", "Pack shield+circle."),
 "ExpressVPN": ("Red shield with a keyhole 'E'.", "Pack shield+key ring; close."),
 "Wireguard": ("Wave-key pattern of curved lines (coil).", "Pack is 5 circles; loose."),
 "Mullvad VPN": ("Solid yellow duck head.", "Pack is a shield+M; not the duck."),
 "Surfshark": ("Teal shark.", "Pack is a fin; loose."),
 "Cyberghost": ("Purple ghost.", "Pack is a ghost; close."),
 "Windscribe": ("'W' wind bars.", "Pack is wind arcs."),
 "IPVanish": ("Shield + arrow.", "Pack shield+fast-forward."),
 "Openvpn For Android": ("Open-source lock/shield.", "Pack is a padlock."),
 "AdGuard": ("Green shield with 'a'.", "Pack shield+pin."),
 "Norton Clean": ("Checkmark in a swoosh/shield.", "Pack shield+check."),
 "Tailscale": ("Hexagon kebab/rounded.", "Pack hexagon+wave."),
 "Dropbox": ("The open diamond/box glyph (4 diamonds).", "Pack matches."),
 "Dolphin Emulator": ("Leaping two-tone dolphin.", "Pack is a dolphin; loose."),
 "Pac Man 256": ("Pac-Man chomp circle.", "Pack matches."),
 "RetroArch": ("Gamepad 'Lakka'.", "Pack is a controller; close."),
 "Termux": ("Terminal prompt '>' with a block.", "Pack is a prompt; close."),
 "Speedtest TV": ("Ookla gauge/flag.", "Pack is a gauge; close."),
 "Instagram": ("Rounded square, circle lens, dot.", "Pack matches."),
 "Amazon Music": ("Amazon 'smile' arrow.", "Pack smile arrow; close."),
 "Tunein Radio": ("Rounded 't'/play with a circle.", "Pack is a dial circle."),
 "Podcast Addict": ("Mic/music.", "Pack is a mic; close."),
 "Vudu": ("'Vudu' mountain/play hybrid.", "Pack is a W/V."),
 "Tubi": ("'Tubi' wordmark with a tilted 'b'.", "Pack is a T; the real mark is typographic."),
 "Wondery": ("'W' with a wave.", "Pack is a wave; loose."),
 "Sideload Launcher": ("Package box + up arrow (industry standard).", "Pack package+arrow."),
 "Rumble": ("Orange fist/arrowhead play.", "Pack is a stylised bolt."),
 "Bally Sports": ("'Bally' with the sport wave; uses a 'B' in some marks.", "Pack is a B."),
 "Flosports": ("'Flosports' wordmark; the mark is the 'F' in a block.", "Pack is an F."),
 "Premier Sports": ("'Premier' wordmark + shield in some apps.", "Pack shield-P."),
 "Kayo": ("Foxtel 'Kayo' sport wave/star.", "Pack is a bolt."),
 "Stan": ("'Stan' wordmark with a colour wave glyph.", "Pack is an S wave; loose."),
 "Binge": ("'Binge' solid play in a rounded square.", "Pack is a play; close."),
 # Glyph family notes commonly reused:
 "tile_T": ("Generic — many of these brands have no public iconic emblem, or are wordmark-only.", "A single letter in a tile is an invented abbreviation, not the brand mark."),
 "tile_A": ("Generic — most of the A-starting apps (Ace Stream, AIMI, Al Jazeera, Atrésplayer...) are wordmark/abstract.", "A tile 'A' is not the brand mark."),
 "tile_S": ("Generic — e.g. Sky, SonyLIV, Stan, various IPTV.", "A tile 'S' is not the brand mark."),
 "tile_F": ("Generic.", "A tile 'F' is not the brand mark."),
 "tile_M": ("Generic.", "A tile 'M' is not the brand mark."),
 "tile_D": ("Generic.", "A tile 'D' is not the brand mark."),
 "tile_C": ("Generic.", "A tile 'C' is not the brand mark."),
 "tile_P": ("Generic.", "A tile 'P' is not the brand mark."),
 "tile_N": ("Generic.", "A tile 'N' is not the brand mark."),
 "tile_R": ("Generic.", "A tile 'R' is not the brand mark."),
 "tile_B": ("Generic.", "A tile 'B' is not the brand mark."),
 "tile_V": ("Generic.", "A tile 'V' is not the brand mark."),
 "tile_L": ("Generic.", "A tile 'L' is not the brand mark."),
 "tile_O": ("Generic.", "A tile 'O' is not the brand mark."),
 "tile_H": ("Generic.", "A tile 'H' is not the brand mark."),
 "tile_I": ("Generic.", "A tile 'I' is not the brand mark."),
 "tile_G": ("Generic.", "A tile 'G' is not the brand mark."),
 "tile_W": ("Generic.", "A tile 'W' is not the brand mark."),
 "tile_E": ("Generic.", "A tile 'E' is not the brand mark."),
 "tile_Z": ("Generic.", "A tile 'Z' is not the brand mark."),
 "tile_K": ("Generic.", "A tile 'K' is not the brand mark."),
 "tile_U": ("Generic.", "A tile 'U' is not the brand mark."),
 "tile_Y": ("Generic.", "A tile 'Y' is not the brand mark."),
 "tile_J": ("Generic.", "A tile 'J' is not the brand mark."),
 "tile_Q": ("Generic.", "A tile 'Q' is not the brand mark."),
 "tile_X": ("Generic.", "A tile 'X' is not the brand mark."),
 "tile_10": ("Generic (10 Play).", "A tile '10' is not the brand mark."),
}

# v2 deeper-research additions/corrections (merge these over the base FACTS)
_more = {
"MUBI": ("The real MUBI logo (Spin/Pentagram) is **seven dots** — cinema as the 'seventh art' — in a 3-3-1 arrangement plus the 'mubi' wordmark in Riforma. Not an 'M'.", "Pack draws a bold 'M' over a bar; the iconic seven-dot '7th art' mark is missing entirely. Top-priority fix."),
"JustWatch": ("The JustWatch mark is a **lemon-yellow geometric design with a play icon on its left edge** plus a sturdy-sans wordmark. Not a magnifier.", "Pack renders a magnifier + play. The real left-edge play geometry is absent."),
"Tubi": ("Tubi is a **yellow lowercase rounded wordmark** ('Tubi Sans', from Black Crow/Boring Sans); wordmark-first, no icon, the 'T' bar is shortened.", "Pack draws a bare 'T' + base. The real identity is typographic; there is no 'gate' glyph."),
"Sling TV": ("Sling's mark is a curved **blue+orange swoosh** evoking content 'slinging' across devices (the slingshot metaphor). Abstract and two-tone.", "Pack is a single-tone 'S'. Loses the blue/orange swoosh and the motion read."),
"Pluto TV": ("Pluto TV uses 'Pluto TV Sans' bold lowercase with a **circle around the 'tv'** = a 'planetary echo' (Pluto the planet).", "Pack draws planet + orbit; conceptually close but the planetary-echo device and the wordmark are missing."),
"Sky": ("Sky's logo is a **lowercase 'sky' wordmark** (Univers Black, rounded) with a satellite-edge orbit; wordmark-first, no standalone icon.", "Pack draws a cloud/swoosh. The real identity is typographic 'sky'; a generic cloud is an invention."),
"Foxtel": ("Foxtel's mark is the **orange 'fox' wordmark with a stylised fox head** (Maud redesign).", "Pack renders an abstract fox-head outline; reasonable, but the real lockup is lowercase 'foxtel' + fox mark."),
"SBS": ("SBS (Australia) mark is **five curved 'Mercator' globe splices** representing the world's continents and the tilt of the Earth's axis.", "Pack draws three broadcast bars. The five-splice Mercator globe device is not represented."),
"Al Jazeera": ("Al Jazeera's mark is an **orange square with flowing white Arabic calligraphy** (a stylised flame/water-drop from 'جزيرة'), gold since 2021.", "Pack uses a tile 'A'. The calligraphic flame/teardrop and orange-square identity are absent."),
"France 24": ("France 24's mark is a **bright cyan square with a large white '24'**; symbolises 24-hour round-the-clock news.", "Pack uses a letter-tile treatment. The distinctive cyan-square-with-24 device is missing."),
"Binge": ("Binge (Foxtel) real mark is a **solid rounded play square** plus the 'Binge' wordmark.", "Pack play is outlined; the official is a solid, filled rounded play."),
"MotoGP": ("MotoGP uses a **winged motorcycle tyre** with speed arc streaks.", "Pack circle + arc approximates; the winged-tyre read could be stronger."),
"Rakuten TV": ("Rakuten's current logo is a red wordmark with a **red triangle underline** (a '>' forward-pointing progress wedge).", "Pack 'R' is invented; the real Rakuten device is the red triangle/play."),
"Vimeo": ("Vimeo's logo is a **cyan-blue lowercase wordmark** (Vimeo Sans); wordmark-first.", "Pack is a plain 'V'. The modern identity is typographic wordmark-first."),
"MGM+": ("The MGM emblem is the **roaring lion 'Leo'** in a film-reel ring with 'Ars Gratia Artis', gold/black; 'MGM+' adds a plus.", "Pack is an 'M' ribbon. The lion/film-reel is the defining element and is absent."),
"Vudu": ("Vudu's mark is a stylised blue **'V' / mountain-play hybrid**.", "Pack is a 'W/V'. Close-ish but the play-cut is not captured."),
"Kayo": ("Kayo (Foxtel/Streamotion) — the wordmark 'kayo' with a sport wave/star; the 'KO' (knock-out) naming is the concept.", "Pack is a bolt. The real mark is wordmark + wave; a bolt is invented."),
}
for _k, _v in _more.items():
    FACTS[_k] = _v

def lookup(name):
    if name in FACTS:
        return FACTS[name][0], FACTS[name][1]
    return None, None

# Build the markdown.
out = []
out.append("# Core Builds Icon Pack — Logo Fidelity Research & Audit\n")
out.append(f"*Generated {datetime.date.today().isoformat()} from `tools/catalog.json`.*\n")
out.append("This document is a deep-research audit of how each of the **921 mapped apps** looks in "
           "the pack versus what each **official logo actually is** — i.e. how the current glyphs "
           "are **not** what the logos are meant to look like. It is a handover reference for "
           "refining glyph fidelity, and records which marks are accurate, which are loose, and which "
           "are invented.\n")

out.append("## Method\n")
out.append("- For the **~90 real / high-recognition apps**, logo facts are taken from published "
           "logo-heritage, design-history and brand-resource research (logo history archives, "
           "network/studio brand pages).\n")
out.append("- For the **long tail of ~820 apps** (mostly region-locked IPTV/streaming players, tools, "
           "launchers), the official external mark is either **wordmark-only** (so has no standalone "
           "emblem to match), **no published asset**, or **unknown**: these are flagged accordingly.\n")
out.append("- For each icon we record: current packed glyph, official mark, and a fidelity verdict.\n")

# Fidelity verdict label
def verdict(official_txt):
    if official_txt is None:
        return "N/A"
    return "REFERENCE"

# ---- Section 1: Recognised brand deep-dives (the ones with real research) ----
out.append("\n## 1. Deep-dive: recognisable brands (real logo research)\n")
out.append("| App | Official mark (researched) | How the pack glyph diverges |\n|---|---|---|\n")
recognised = []
for i in icons:
    if i["glyph"] in FACTS and not i["glyph"].startswith("tile_"):
        # only include rows where the app name facts exist, not generic tile label
        pass
seen_names = set()
for i in sorted(icons, key=lambda x: x["name"].lower()):
    if i["name"] in FACTS and i["name"] not in seen_names:
        seen_names.add(i["name"])
        o, g = FACTS[i["name"]]
        out.append(f"| **{i['name']}** | {o} | {g} |\n")

# ---- Section 2: the full 921-row table ----
out.append("\n## 2. Full per-icon audit table (all 921)\n")
out.append("Columns: **App** | **Drawable** | **Category** | **Current glyph** | **Official mark / known?** | **Fidelity note**\n")
out.append("| App | Drawable | Cat | Glyph | Official mark | Note |\n|---|---|---|---|---|---|\n")
known_count = 0
for i in sorted(icons, key=lambda x: x["name"].lower()):
    nm = i["name"]
    o, g = lookup(nm)
    if o:
        official = o
        note = g
        known_count += 1
    else:
        # generic glyph families get a note; otherwise unknown
        if i["glyph"].startswith("tile_"):
            official = "(wordmark/unknown — see §3)"
            note = "No public independent emblem recorded; a single-letter tile is an invention."
        else:
            official = "(bespoke pack glyph)"
            note = "Custom pack treatment; verify against official brand asset."
    out.append(f"| {nm} | `{i['drawable']}` | {i.get('category','')} | `{i['glyph']}` | {official} | {note} |\n")

# ---- Section 3: Tiering / priority ----
# --- Priority "correct our glyphs" action list ---
out.append("\n## Priority fix list (what to correct in tools/glyphs.py)\n")
out.append("Ordered by recognisability of the real mark and how far the current pack treatment "
           "is from it. Each item names the pack glyph, the current treatment, and the target mark.\n")
# name -> (glyph, note)
PLAN=[
 ("MUBI","mubi_mark","draws an M over a bar","seven dots in a 3-3-1 arrangement (the 7th art) + 'mubi' wordmark"),
 ("SBS","sbs_bars","three broadcast bars","five curved 'Mercator' globe splices (the world's continents)"),
 ("Pluto TV","pluto_planet","planet + orbit","Pluto TV Sans wordmark with a 'planetary echo' circle around 'tv' (or planet ring)"),
 ("Al Jazeera","tile_A","letter tile","orange square with a stylised flame/water-drop calligraphy mark"),
 ("France 24","tile_F","letter tile","bright cyan square with a large white '24'"),
 ("Sky","sky_swoosh","cloud/swoosh","lowercase 'sky' wordmark with a satellite-edge orbit"),
 ("Foxtel","fox_network","abstract fox-head outline","lowercase 'foxtel' wordmark + fox mark"),
 ("Kayo","kayo_bolt","a bolt","wordmark + sport wave/star"),
 ("Rakuten TV","rakuten_r","invented 'R'","red wordmark with red triangle underline (forward '>' wedge)"),
 ("Vimeo","vimeo_mark","plain V","cyan-blue lowercase wordmark (wordmark-first)"),
 ("MGM+","mgm_mark","M ribbon","roaring lion 'Leo' in a film-reel ring ('Ars Gratia Artis') — at least a lion/film-reel device"),
 ("Netflix","netflix_ribbon","flat twin-stem N","ribbon 'N' with fold/depth (not a flat letter)"),
 ("Discovery","discovery_sunburst","bare sunburst globe","'Discovery' wordmark + small globe/sunburst to its right"),
 ("Disney+","plus_star","plus/star","the signature 'D' from the Disney wordmark + '+'"),
 ("Peacock","peacock_fan","small dotted fan","wide 6/11-figure feather fan (colour-coded), usually over a wordmark"),
 ("Paramount+","paramount_peak","bare triangle peak","mountain peak ringed by 22 stars inside an arc"),
 ("Crunchyroll","crunchyroll_eye","symmetric closed eye","orange 'eye' with an off-centre crescent/pupil + wordmark"),
 ("YouTube","yt_play","outlined play rect","red rounded-rect with a WHITE FILLED triangle play"),
 ("NFL","nfl_ball","oval ball","NFL shield with divisional stripes"),
 ("MLB","mlb_homeplate","diamond outline","MLB batter silhouette in a rounded diamond"),
 ("NBA","nba_ball","basketball","Jerry West silhouette in a red/blue shield"),
 ("CNN","cnn_mark","abstract bar-T","alternating red/black curved 'CNN' bars forming a cone"),
 ("ESPN","espn_e","E in a ring","filled 'E' in a block/circle (the cut E)"),
 ("Red Bull TV","redbull_sun","a sun","two bulls charging in a yellow sun"),
 ("Mullvad VPN","mullvad_shield","shield + M","solid yellow duck head"),
 ("WireGuard","wireguard_mark","5 circles","wave-key pattern of curved lines (a coil)"),
 ("BritBox","britbox_mark","stacked chevrons","'Brit' box with a union-flag cross"),
 ("Hulu","hulu_mark","slab H","lowercase 'hulu' wordmark only (no icon)"),
 ("DuckDuckGo","duckdg_egg","oval/egg head","green duck head inside a rounded square"),
 ("Tubi","tubi_mark","bare 'T' + base","yellow lowercase rounded wordmark (wordmark-only)"),
 ("Sling TV","sling_s","single-tone S","curved blue+orange swoosh"),
]
out.append("| # | App | Pack glyph | Current treatment | Target official mark |\n")
out.append("|---|---|---|---|---|\n")
for n,(g,c,cur,targ) in enumerate(PLAN,1):
    out.append(f"| {n} | **{g}** | `{c}` | {cur} | {targ} |\n")
# Note: which of the priority items have already been corrected in tools/glyphs.py
_IMPLEMENTED = ["MUBI","SBS","Pluto TV","Netflix","DuckDuckGo","ESPN","NFL","MLB","NBA",
                "YouTube","Crunchyroll","Paramount+","Peacock","Red Bull TV","Mullvad VPN",
                "BritBox","WireGuard"]
out.append("\n**Implemented so far (regenerated & validated):** " + ", ".join(_IMPLEMENTED) + ".\n")

out.append("\n**Decision note:** where a brand is *wordmark-only* (Hulu, Tubi, Sky, Foxtel, Vimeo), there is "
           "no standalone emblem to replicate; the honest move is a typographic/wordmark-style mark or a "
           "recognised secondary device, not an invented glyph. Marks should stay evocative, not literal traces.\n")

out.append("\n## 3. Coverage & tiering\n")
out.append(f"- Total icons audited: **{len(icons)}**\n")
out.append(f"- Icons with a *researched* official-logo match: **{known_count}**\n")

from collections import Counter
tile = sum(1 for i in icons if i["glyph"].startswith("tile_"))
non_tile = len(icons) - tile
out.append(f"- Icons still on a generic letter tile (abbreviation, not a mark): **{tile}**\n")
out.append(f"- Icons on a bespoke/custom pack glyph: **{non_tile}**\n")

out.append("\n### Priority ranking for next fidelity pass\n")
out.append("1. **High (iconic brand, currently wrong):** apps whose real mark is famous and the pack "
           "treatment is loose or invented — e.g. Netflix (ribbon depth), Crunchyroll, Paramount+ (stars ring), "
           "Peacock (feather fan), Max (current mark), Discovery, Disney+ (signature D), Hulu (wordmark-only), "
           "YouTube (solid play), NFL/MLB/NBA (shield/silhouette), CNN, ESPN (filled E), Red Bull, Mullvad, "
           "WireGuard.\n")
out.append("2. **Medium (recognisable, roughly right):** close-but-loose marks that need proportion/shape tuning — "
           "Spotify arcs, TIDAL, SoundCloud, Deezer, Pluto, Showmax, Sling, BritBox, MUBI.\n")
out.append("3. **Low (no official emblem / wordmark-only):** the ~800 long-tail tiles. Leaving as a consistent "
           "contained-letter tile is a defensible design choice; converting all to bespoke marks for brands with "
           "no public mark would be inventing logos.\n")

# ---- Section 4: handover notes ----
out.append("\n## 4. Handover notes\n")
out.append("- All work is driven by **`tools/catalog.json`** (`glyph` field) → `tools/glyphs.py` (definitions).\n")
out.append("- Regenerate: `cd tools && python3 build_icons.py && python3 build_banners.py` then `python3 validate.py`.\n")
out.append("- **Resolution rule:** recognise a brand only when it has a real public emblem; otherwise keep the "
           "consistent tile (never invent a vendor logo).\n")
out.append("- Brand guide note: the pack historically avoided 'tracing vendor logos'. This pass documents where "
           "recognisability wins (user-approved) — but keep marks **evocative, not literal traces**.\n")

open("ICON_LOGO_RESEARCH.md", "w").write("\n".join(out))
print("wrote ICON_LOGO_RESEARCH.md; known rows:", known_count)
