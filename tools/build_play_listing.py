#!/usr/bin/env python3
"""
Render the Google Play store listing assets and write the listing copy.

Why this exists
---------------
Play will not publish a listing without a 512 px icon, a 1024x500 feature
graphic and, for a TV app, a 1280x720 banner plus at least one 1920x1080 TV
screenshot. None of that existed in the tree, and the assets it did have (a
640x360 Leanback banner, 1080-grid launcher mipmaps, UI mockups with a caption
band under the panel) are the wrong shape and size for the store.

The alternative - exporting from a design tool - is the thing this repo already
refuses to do for icons, banners and UI frames, for a reason it has paid for:
hand-made art drifts from the app it advertises and nothing notices. So every
asset here is output:

  * the icon is the app's own adaptive background plus the same PACK_ICON mark
    tools/build_branding.py draws, rendered full-bleed at 512;
  * the TV banner is build_branding.banner_svg(), the exact composition behind
    the on-device cb_banner, rendered at 1280x720 instead of 640x360 - one
    drawing at two scales, so the store and the TV row cannot disagree;
  * the TV screenshots are the committed 1920x1080 UI panels with the mockup
    caption band cropped off, from docs/app-ui-*.png - the frames that
    build_app_ui_mockups.py already holds to the source they depict;
  * the copy's numbers (icons, walls, loops, series) are substituted from the
    same catalog and resource files the app reads, so the listing cannot claim
    a count the build does not ship.

What is impossible here, and said out loud
------------------------------------------
There are no phone screenshots. The layouts are TV-first and there is no phone
UI to photograph; a rendered one would be fiction, which is the exact failure
mode this file exists to prevent. The listing therefore targets **TV only**
today (`form_factors: ["tv"]`). Listing for phones needs real captures from a
phone, and the decision to support phones at all is a product one, not a
artefact one. TARGET the honest way: ship TV, add phones when a phone build is
tested.

Usage
-----
    python tools/build_play_listing.py            # write docs/play/*
    python tools/build_play_listing.py --check    # fail if the committed
                                                  #   assets or copy are stale

`--check` re-derives the asset specs and the digests of every input (the source
frames, the branding/tooling it renders through, and this file) and compares
them with docs/play/listing.json, then re-reads the committed PNGs to confirm
their real dimensions, colour type and byte size still meet Play's limits. It
does not compare PNG bytes: the frames are rasterised by a renderer whose text
antialiasing differs between machines (see build_app_ui_mockups.py), and a
store icon that moved one antialiased pixel is not the defect - a store listing
advertising numbers the build does not ship is.
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import build_branding as branding  # noqa: E402
import build_glyphs_branding as glyphs_branding  # noqa: E402
from PIL import Image  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
PLAY = DOCS / "play"
RES = ROOT / "app/src/main/res"
ASSETS = ROOT / "app/src/main/assets"

# Play's limits, quoted as constants so the test and the generator cannot
# drift from each other or from the platform help page.
ICON_PX = 512
ICON_MAX_BYTES = 1024 * 1024          # Play: 1024 KB
FEATURE_W, FEATURE_H = 1024, 500
BANNER_W, BANNER_H = 1280, 720
SHOT_W, SHOT_H = 1920, 1080
SHOT_MIN, SHOT_MAX = 320, 3840
TITLE_MAX = 30
SHORT_MAX = 80
FULL_MAX = 4000
MAX_SHOTS = 8

# The frames worth showing, in the order a buyer should meet them: what the app
# gives you, how you browse it, what it tells you about your own TV. Each name
# must exist in docs/ as a 1920x1144 frame (panel + 64px caption band).
SCREENSHOTS = [
    ("wallpapers", "every series, thumbnails bundled so the grid works offline"),
    ("catalogue", "the icon catalogue, searchable and sorted by category"),
    ("inspector", "any app's unfiltered icon, and the file the pack would draw"),
    ("auditor", "which installed apps are covered, and one-press icon requests"),
    ("launcher-compat", "which launchers on this TV can apply the pack"),
    ("suite", "the rest of the Core Builds apps on this TV"),
]

CAPTION_H = 64  # build_app_ui_mockups.py: panel is 1920x1080, band below it


# ---------------------------------------------------------------------------
# Sources
# ---------------------------------------------------------------------------

def icon_count() -> int:
    """Icons the pack actually maps, from the generated resource the app reads."""
    root = ET.parse(RES / "values/icon_pack.xml").getroot()
    node = root.find("integer[@name='icon_count']")
    if node is None:
        raise SystemExit("icon_pack.xml has no icon_count; run tools/build_icons.py")
    return int((node.text or "0").strip())


def catalog() -> list[dict]:
    data = json.loads((ASSETS / "manifest/wallpapers.json").read_text(encoding="utf-8"))
    return data["wallpapers"]


def loop_count() -> int:
    src = (ROOT / "app/src/main/java/tv/corebuilds/iconpack/LiveLoop.kt").read_text(
        encoding="utf-8")
    return len(re.findall(r"\n            stillSeries = ", src))


def plus_wall_count() -> int:
    """Spectra, the Core Builds Plus collection, as bundled into the Play build."""
    path = ROOT / "app/src/play/assets/manifest/plus-wallpapers.json"
    return len(json.loads(path.read_text(encoding="utf-8"))["wallpapers"])


def counts() -> dict:
    walls = catalog()
    return {
        "icons": icon_count(),
        "walls": len(walls),
        "series": len({w["series"] for w in walls}),
        "loops": loop_count(),
        "plus_walls": plus_wall_count(),
    }


def broker_enabled() -> bool:
    """Whether this tree bakes in the icon-request broker's URL.

    It is the one switch behind three store answers. With it set, a missing-
    icon press POSTs to the broker, which files a public issue (Data safety:
    collected); and Plus.shown goes true on Play, so Core Builds Plus is on
    sale (content rating: purchases; copy: what it buys). Without it, the
    press opens a prefilled GitHub form and the app itself sends nothing.
    Reading it here means baking the URL in fails --check until the listing
    and its answers are regenerated, instead of shipping last release's form.
    """
    xml = (RES / "values/issue_prefill.xml").read_text(encoding="utf-8")
    found = re.search(r'<string name="audit_request_endpoint">([^<]*)</string>', xml)
    return bool(found and found.group(1).strip())


# One-way latch for the store answers. Play keeps one Data safety form and one
# content rating per app, and they must cover every version still on a track,
# staged rollouts included. So once a broker build has shipped, "collected" and
# "purchases: yes" outlive the broker until no track serves such a build. Set
# this True in the change that bakes the broker URL in (--check refuses a
# broker-enabled tree without it), and set it back only once every track has
# moved past the last broker build.
BROKER_SHIPPED = False


def answers_cover_broker() -> bool:
    """Whether Data safety and the rating must describe a broker build."""
    return broker_enabled() or BROKER_SHIPPED


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


# ---------------------------------------------------------------------------
# Copy
# ---------------------------------------------------------------------------
#
# Written with {placeholders} and substituted from counts(), so a catalog that
# grows to 140 walls changes the listing or fails the build; it can never leave
# a stale number on the store page. Text is plain: Play's metadata policy has
# no patience for "best", rankings, or other apps' names used as bait, and the
# launchers are named because they are the ones the app is tested against.
COPY = {
    "title": "Core Builds Icon Pack",
    "short_description": (
        "{icons} icons and {walls} wallpapers built for Android TV. "
        "No ads, no account."
    ),
    "full_description": (
        "An icon and wallpaper pack made for the television, not a phone app "
        "stretched onto one.\n"
        "\n"
        "ICONS\n"
        "{icons} icons, mapped for the apps people actually run on a TV: "
        "streaming services, players, launchers, tools. Every app gets two "
        "shapes: a 16:9 banner, which this pack applies, and a square glyph, "
        "applied by the free Core Builds Glyphs Pack. The Art style switch "
        "moves between them, and the first time you pick square it takes you "
        "to the Glyphs Pack.\n"
        "\n"
        "WALLPAPERS\n"
        "{walls} stills across {series} series, from deep space to circuit "
        "boards, each with a matching thumbnail bundled in the app. The grid "
        "works offline; the full-size image downloads when you pick it, and "
        "saves to your own Pictures folder if you would rather set it "
        "yourself.\n"
        "\n"
        "LIVE LOOPS\n"
        "{loops} animated wallpapers, played by the system rather than a "
        "video app, so they keep moving while you browse.\n"
        "\n"
        "HONEST ABOUT YOUR TV\n"
        "The auditor lists which of your installed apps the pack covers, shows "
        "the exact icon file it would draw for any app, and can request a "
        "missing icon in one press. There is no account and no sign-in. "
        "Nothing is analysed in the background.\n"
        "\n"
        "WORKS WITH\n"
        "Launchers that accept icon packs, including Projectivy, FLauncher, "
        "AT4K, Leanback Launcher and Lawnchair variants. Where a launcher "
        "cannot be driven directly, the app says so and points you at the "
        "manual picker instead of pretending.\n"
        "\n"
        "PRIVACY\n"
        "No advertising, no analytics, no tracking, no account. Wallpapers are "
        "fetched from a public address; an icon request sends the app name, "
        "the component and a device note, and only when you press it."
    ),
}

# Added to the full description only while Plus is on sale (broker_enabled),
# just above PRIVACY. It says what the purchase buys and, in the same breath,
# that nothing the free listing promises is behind it.
PLUS_COPY = (
    "CORE BUILDS PLUS\n"
    "Optional, one purchase through Google Play, no subscription: your icon "
    "requests are filed as priority, My requests shows how far each one has "
    "got, the live wallpaper can rotate through your loops, and Spectra adds "
    "{plus_walls} more wallpapers. Every icon, all {walls} stills, every loop "
    "and every screen ship to everyone either way.\n"
    "\n"
)
PLUS_PRIVACY = (
    " With Plus, a request also carries the Google Play purchase token, which "
    "is checked with Google and never stored."
)


def copy(n: dict | None = None, plus: bool | None = None) -> dict:
    """The listing text for this tree: COPY, plus PLUS_COPY while it is sold."""
    n = counts() if n is None else n
    plus = broker_enabled() if plus is None else plus
    text = dict(COPY)
    if plus:
        full = text["full_description"]
        head, tail = full.split("PRIVACY\n", 1)
        text["full_description"] = head + PLUS_COPY + "PRIVACY\n" + tail + PLUS_PRIVACY
    return {k: v.format(**n) for k, v in text.items()}

# The shapes the app can honestly be listed under. Phones need phone
# screenshots (see the docstring) and a tested phone pass; neither exists.
FORM_FACTORS = ["tv"]

# Play's Data safety answers, with the evidence beside them. The test keeps the
# payload list equal to what IconRequestSender.kt actually sends.
#
# These are the answers for a tree WITHOUT the broker (broker_enabled false):
# a missing-icon press then opens a prefilled GitHub form that the user submits
# from their own browser, so the app transmits nothing. data_safety() moves
# them when the broker is baked in.
DATA_SAFETY = {
    "collects_user_data": False,
    "shares_user_data": False,
    "has_ads": False,
    "encrypted_in_transit": True,
    "deletion_request_url": None,
    "rationale": (
        "No analytics, advertising or crash SDKs are linked; the app has no "
        "account, no identifier and no background upload. The one POST the "
        "user can trigger (an icon request) carries the app name, the "
        "component and a device note - no device ID, no advertising ID, no "
        "IP-derived store beyond a rate-limit window on the broker - and the "
        "policy states it verbatim."
    ),
    "user_initiated_icon_request": {
        "endpoint_owner": "icon request broker (tools/icon_request_broker)",
        "fields": ["app_name", "component", "device"],
        "identifiers": [],
        "trigger": "the user presses Request icon on an auditor row",
    },
}

# With the broker baked in, the app itself transmits the request, and the broker
# files it as a public issue: stored off the device, so not ephemeral, and Play's
# form has no exemption from "collected" for a user-initiated send (that
# exemption is for "shared"). Each type is optional: nothing is sent unless the
# user presses an app, and the token only for a Plus owner.
BROKER_COLLECTED = [
    {
        "type": "App activity > Installed apps",
        "optional": True,
        "shared": False,
        "purposes": ["App functionality"],
        "what": "the one app the user presses under Missing icons (name and "
                "component) and the launcher in use, filed as a public GitHub "
                "issue",
    },
    {
        "type": "Financial info > Purchase history",
        "optional": True,
        "shared": False,
        "purposes": ["App functionality"],
        "what": "Plus owners only: the Google Play purchase token, checked with "
                "Google and never stored; a SHA-256 of it is cached for at most "
                "six hours",
    },
]


def data_safety(plus: bool | None = None) -> dict:
    plus = answers_cover_broker() if plus is None else plus
    if not plus:
        return DATA_SAFETY
    answers = json.loads(json.dumps(DATA_SAFETY))
    answers["collects_user_data"] = True
    answers["collected"] = BROKER_COLLECTED
    answers["deletion_request_url"] = "https://github.com/brevityA/CoreBuildsApps/issues"
    answers["rationale"] = (
        "No analytics, advertising or crash SDKs are linked; the app has no "
        "account, no identifier and no background upload. A missing-icon press "
        "posts the app name, the component and a device note to the request "
        "broker, which files them as a public GitHub issue; a Plus owner's "
        "press also carries the purchase token, which the broker checks with "
        "Google and never stores. Nothing is shared with a third party, and "
        "the policy states each field verbatim."
    )
    answers["user_initiated_icon_request"]["fields"] = [
        "app_name", "component", "device", "plus_token"]
    # The rest of the device note matches no type on Play's list: the list
    # covers identifiers, and a TV model or an Android version identifies no
    # user and no device, much like a browser's User-Agent. It is declared in
    # the privacy policy instead of filed under a type it does not fit.
    answers["not_a_listed_type"] = (
        "TV model, Android version and pack version, from the device note; "
        "stated in docs/privacy.html"
    )
    return answers


# IARC content-rating questionnaire answers that matter for this app.
# "online_content" is IARC's "content that isn't part of the initial app
# download but can be accessed from the app": the full-size wallpapers
# (wallpapers.json points them at raw.githubusercontent.com; only thumbs are
# bundled) and the live loops (LiveLoop.kt, Motion/live/). All of it is this
# repo's own art, so the follow-ups about rated content in it are "no".
CONTENT_RATING = {
    "category": "All Other App Types",
    "online_content": True,
    "violence": False,
    "sexuality": False,
    "language": False,
    "controlled_substances": False,
    "gambling": False,
    "user_generated_content_shared": False,
    "location_sharing": False,
    "purchases": False,
    "ads": False,
    "expected_rating": "Everyone / PEGI 3",
    "note": (
        "A wallpaper and icon pack. Nothing here is user-generated, shared "
        "or interactive with strangers. 'online_content' is the full-size "
        "wallpapers and live loops, downloaded after install from this repo; "
        "none of it is rated content. 'purchases' follows the broker: Core "
        "Builds Plus is on sale exactly when the broker is baked in, and the "
        "answer stays yes while a track still serves such a build "
        "(BROKER_SHIPPED)."
    ),
}


def content_rating(plus: bool | None = None) -> dict:
    plus = answers_cover_broker() if plus is None else plus
    return {**CONTENT_RATING, "purchases": plus}


# ---------------------------------------------------------------------------
# Art
# ---------------------------------------------------------------------------

def flatten(path: Path) -> Path:
    """Drop the alpha channel.

    Play requires 24-bit PNG (no alpha) for the feature graphic, the TV banner
    and every screenshot. The SVG renderer always emits RGBA, so the channel is
    removed after rasterising instead of assumed away - a listing rejected for
    a transparent channel is a silly way to lose a review cycle.
    """
    with Image.open(path) as im:
        if im.mode != "RGB":
            im.convert("RGB").save(path, format="PNG", optimize=True)
    return path


def render_icon() -> Path:
    """512x512, full-bleed, opaque: Play applies its own rounded mask, so a
    pre-rounded or transparent-cornered icon comes out double-cropped."""
    body = branding.launcher_background_body() + branding.PACK_ICON
    out = PLAY / "icon-512.png"
    branding.png(branding.svg(ICON_PX, ICON_PX, body), out, ICON_PX, ICON_PX)
    return out


def feature_graphic_svg(n: dict) -> str:
    """1024x500, no alpha. One focal point, generous negative space, nothing
    within 10% of an edge: Play crops this graphic differently per surface."""
    w, h = FEATURE_W, FEATURE_H
    mark = 232
    mx, my = 104, (h - mark) // 2
    scale = mark / 512.0

    name, _ = branding.wordmark_spans(["Core Builds"], 66, 400, [212], "#e6edf3")
    sub, _ = branding.wordmark_spans(["Icon Pack"], 40, 400, [286], "#00d4ff")
    strap = f"{n['icons']} ICONS \u00b7 {n['walls']} WALLPAPERS \u00b7 {n['loops']} LOOPS"
    tag, _ = branding.wordmark_spans([strap], 17, 402, [336], "#8b949e",
                                     font_path=branding.FONT_MONO)
    # A cyan hairline under the lockup, the same light-line the UI uses for
    # dividers; it also anchors the composition when the graphic is cropped
    # tall and thin on some surfaces.
    rule = (f'<rect x="402" y="368" width="330" height="2" fill="#00d4ff" '
            f'opacity="0.55"/>')
    return branding.svg(
        w, h,
        f'{branding.FIELD_DEFS}'
        f'<rect width="{w}" height="{h}" fill="url(#fieldBg)"/>'
        f'<g transform="translate({mx},{my}) scale({scale:.4f})">'
        f'{branding.PACK_ICON}</g>'
        f'{name}{sub}{tag}{rule}',
    )


def render_feature_graphic(n: dict) -> Path:
    out = PLAY / "feature-graphic-1024x500.png"
    # background_color fills the SVG's transparent areas after rasterising, so
    # the PNG is written without an alpha channel at all rather than with one
    # Play would reject.
    from svg_renderer import svg2png  # the renderer the branding tool uses
    out.parent.mkdir(parents=True, exist_ok=True)
    svg2png(bytestring=feature_graphic_svg(n).encode(), write_to=str(out),
            output_width=FEATURE_W, output_height=FEATURE_H,
            background_color="#0d1117")
    return flatten(out)


def render_tv_banner() -> Path:
    out = PLAY / "tv-banner-1280x720.png"
    branding.png(branding.banner_svg(), out, BANNER_W, BANNER_H)
    return flatten(out)


def render_screenshots() -> list[Path]:
    """Crop the caption band off the committed 1920x1080 UI panels.

    The band is what makes those frames honest documentation (it names the
    values a frame invents), and it is exactly what does not belong in a store
    screenshot, where a strip of explanatory English under the UI would read as
    part of the app.
    """
    written = []
    for index, (name, _) in enumerate(SCREENSHOTS, start=1):
        src = DOCS / f"app-ui-{name}.png"
        if not src.exists():
            raise SystemExit(f"missing frame {src}; run tools/build_app_ui_mockups.py")
        img = Image.open(src).convert("RGB")
        if img.width != SHOT_W or img.height < SHOT_H:
            raise SystemExit(f"{src} is {img.size}, expected at least {SHOT_W}x{SHOT_H}")
        out = PLAY / f"tv-{index}-{name}-{SHOT_W}x{SHOT_H}.png"
        img.crop((0, 0, SHOT_W, SHOT_H)).save(out, format="PNG", optimize=True)
        written.append(out)
    return written


def render_all() -> dict:
    PLAY.mkdir(parents=True, exist_ok=True)
    n = counts()
    text = copy(n)

    icon = render_icon()
    feature = render_feature_graphic(n)
    banner = render_tv_banner()
    shots = render_screenshots()

    payload = {
        "package": "tv.corebuilds.iconpack.play",
        "form_factors": FORM_FACTORS,
        "sideload_package": "tv.corebuilds.iconpack",
        "counts": n,
        "copy": text,
        "assets": {
            "icon": {"file": icon.name, "width": ICON_PX, "height": ICON_PX,
                     "mode": "RGBA", "max_bytes": ICON_MAX_BYTES},
            "feature_graphic": {"file": feature.name, "width": FEATURE_W,
                                "height": FEATURE_H, "mode": "RGB"},
            "tv_banner": {"file": banner.name, "width": BANNER_W,
                          "height": BANNER_H, "mode": "RGB"},
            "tv_screenshots": [
                {"file": p.name, "frame": SCREENSHOTS[i][0],
                 "depicts": SCREENSHOTS[i][1],
                 "width": SHOT_W, "height": SHOT_H, "mode": "RGB"}
                for i, p in enumerate(shots)
            ],
        },
        "data_safety": data_safety(),
        "content_rating": content_rating(),
        "sources": {
            "generator": digest(Path(__file__)),
            "branding": digest(Path(branding.__file__)),
            "icon_pack_xml": digest(RES / "values/icon_pack.xml"),
            "wallpapers_json": digest(ASSETS / "manifest/wallpapers.json"),
            "live_loop_kt": digest(
                ROOT / "app/src/main/java/tv/corebuilds/iconpack/LiveLoop.kt"),
            "frames": {
                f"app-ui-{name}.png": digest(DOCS / f"app-ui-{name}.png")
                for name, _ in SCREENSHOTS
            },
        },
    }
    (PLAY / "listing.json").write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    render_glyphs()
    return payload


# ---------------------------------------------------------------------------
# Check
# ---------------------------------------------------------------------------

def check() -> int:
    spec_path = PLAY / "listing.json"
    if not spec_path.exists():
        print("docs/play/listing.json missing; run tools/build_play_listing.py")
        return 1
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    problems: list[str] = []

    n = counts()
    if spec.get("counts") != n:
        problems.append(f"counts moved: listing says {spec.get('counts')}, tree says {n}")
    expected_copy = copy(n)
    if spec.get("copy") != expected_copy:
        for key in expected_copy:
            if spec.get("copy", {}).get(key) != expected_copy[key]:
                problems.append(f"copy.{key} is stale")
    # The store answers follow the build too: baking the broker in without
    # regenerating would leave Console holding "collects nothing, no purchases".
    if spec.get("data_safety") != data_safety():
        problems.append("data_safety does not match this build (broker_enabled moved?)")
    if spec.get("content_rating") != content_rating():
        problems.append("content_rating does not match this build (broker_enabled moved?)")
    if broker_enabled() and not BROKER_SHIPPED:
        problems.append("the broker URL is baked in but BROKER_SHIPPED is False: set it "
                        "in this change, so the store answers keep covering broker "
                        "builds while any track serves one")

    sources = spec.get("sources", {})
    fresh = {
        "generator": digest(Path(__file__)),
        "branding": digest(Path(branding.__file__)),
        "icon_pack_xml": digest(RES / "values/icon_pack.xml"),
        "wallpapers_json": digest(ASSETS / "manifest/wallpapers.json"),
        "live_loop_kt": digest(
            ROOT / "app/src/main/java/tv/corebuilds/iconpack/LiveLoop.kt"),
        "frames": {f"app-ui-{name}.png": digest(DOCS / f"app-ui-{name}.png")
                   for name, _ in SCREENSHOTS},
    }
    for key, value in fresh.items():
        if sources.get(key) != value:
            problems.append(f"source {key} changed since the assets were rendered")

    # The committed PNGs must still meet Play's limits - regenerating them is
    # not the point of --check, but a hand-edited or truncated asset is.
    assets = spec.get("assets", {})
    for key in ("icon", "feature_graphic", "tv_banner"):
        entry = assets.get(key, {})
        path = PLAY / entry.get("file", "")
        if not path.exists():
            problems.append(f"{key}: {path.name} missing")
            continue
        with Image.open(path) as im:
            if (im.width, im.height) != (entry["width"], entry["height"]):
                problems.append(f"{key}: {im.size} does not match {entry['file']}")
            if im.mode != entry["mode"]:
                problems.append(f"{key}: mode {im.mode}, expected {entry['mode']}")
        if key == "icon" and path.stat().st_size > ICON_MAX_BYTES:
            problems.append(f"icon is {path.stat().st_size} bytes, over Play's limit")

    shots = assets.get("tv_screenshots", [])
    if not shots:
        problems.append("no TV screenshots: Play will not publish a TV listing")
    for entry in shots:
        path = PLAY / entry.get("file", "")
        if not path.exists():
            problems.append(f"screenshot {entry.get('file')} missing")
            continue
        with Image.open(path) as im:
            if (im.width, im.height) != (SHOT_W, SHOT_H):
                problems.append(f"{path.name}: {im.size}, expected {SHOT_W}x{SHOT_H}")
            if im.mode != "RGB":
                problems.append(f"{path.name}: mode {im.mode}; Play wants no alpha")

    problems += check_glyphs()

    if problems:
        print("Play listing is stale:")
        for p in problems:
            print(f"  - {p}")
        print("\nRe-run: python tools/build_play_listing.py")
        return 1

    g = glyph_counts()
    print(f"Play listing ok - {len(shots)} TV screenshots, copy matches "
          f"{n['icons']} icons / {n['walls']} walls / {n['loops']} loops; "
          f"Glyphs Pack listing matches {g['icons']} icons / {g['components']} "
          f"components; every asset inside Play's limits")
    return 0



# ---------------------------------------------------------------------------
# Core Builds Glyphs Pack: the second Play listing
# ---------------------------------------------------------------------------
#
# The square companion (`glyphs/`, tv.corebuilds.iconpack.glyphs.play) is its
# own Play app with its own listing, and the icon pack's Art style switch opens
# that listing on Play. It gets the same treatment as the pack: every asset is
# output from what the app ships, and the copy's numbers come from its own
# resources. It lives in docs/play/glyphs/ beside the pack's.
#
# Its icon and TV banner are its own launcher icon and Leanback banner
# (tools/build_glyphs_branding.py) at store size - one drawing at two scales,
# as for the pack. Its one screenshot is its one screen, the glyphs-home frame
# of tools/build_app_ui_mockups.py with the caption band cropped off.

GLYPHS_RES = ROOT / "glyphs/src/main/res"
GLYPHS_PACKAGE = "tv.corebuilds.iconpack.glyphs.play"
GLYPHS_SIDELOAD = "tv.corebuilds.iconpack.glyphs"
GLYPHS_SCREENSHOTS = [
    ("glyphs-home", "the pack's one screen: its counts and the way to the icon pack"),
]


def glyphs_dir() -> Path:
    # Resolved per call, so a test that points PLAY at a copy moves this too.
    return PLAY / "glyphs"


def _count_items(path: Path, attribute: str) -> int:
    """GlyphsHomeActivity.countItems: <item> rows carrying the attribute."""
    return sum(1 for el in ET.parse(path).getroot().iter("item")
               if el.get(attribute) is not None)


def glyph_counts() -> dict:
    return {
        "icons": _count_items(GLYPHS_RES / "xml/drawable.xml", "drawable"),
        "components": _count_items(GLYPHS_RES / "xml/appfilter.xml", "component"),
    }


GLYPHS_COPY = {
    "title": "Core Builds Glyphs Pack",
    "short_description": (
        "{icons} square glyph icons for Android TV launchers. No ads, no account."
    ),
    "full_description": (
        "The square half of Core Builds Icon Pack: {icons} icons drawn as "
        "rounded-line glyphs with colour accents, for launchers that show square tiles.\n"
        "\n"
        "SAME APPS, SQUARE ART\n"
        "Core Builds Icon Pack draws 16:9 banners. This pack draws the same "
        "apps as square glyphs, mapped to the same {components} launcher "
        "components, so switching between the two changes what an icon looks "
        "like, never which apps get one.\n"
        "\n"
        "HOW TO USE IT\n"
        "Choose Core Builds Glyphs Pack in your launcher's icon pack list (on "
        "Projectivy: Settings, then Icon pack), or flip Art style inside Core "
        "Builds Icon Pack, which brings you here if this pack is missing.\n"
        "\n"
        "WHAT IT IS\n"
        "A resource pack with one screen, which shows how many icons it "
        "carries and opens the icon pack. Applying the pack works on its own. "
        "Picking a single icon by hand goes through Core Builds Icon Pack's "
        "picker, so that needs the icon pack installed, and the catalogue, "
        "the wallpapers and the settings live there too.\n"
        "\n"
        "PRIVACY\n"
        "No internet permission, no ads, no analytics, no account. This pack "
        "sends nothing at all."
    ),
}


def glyphs_copy(n: dict | None = None) -> dict:
    n = glyph_counts() if n is None else n
    return {k: v.format(**n) for k, v in GLYPHS_COPY.items()}


# No network code and no permissions at all (tests read both manifests), so
# there is nothing to collect and no switch to follow: these answers are fixed.
GLYPHS_DATA_SAFETY = {
    "collects_user_data": False,
    "shares_user_data": False,
    "has_ads": False,
    "encrypted_in_transit": None,
    "deletion_request_url": None,
    "rationale": (
        "The app declares no permissions, INTERNET included, and links no SDK: "
        "it is icon art, an appfilter and one screen. Nothing leaves the device."
    ),
}

GLYPHS_CONTENT_RATING = {
    **CONTENT_RATING,
    "online_content": False,
    "purchases": False,
    "note": (
        "A square icon pack. Nothing here is user-generated, shared or "
        "interactive with strangers, it sells nothing (Core Builds Plus is "
        "the icon pack's, not this app's), and with no INTERNET permission "
        "everything it shows is in the install."
    ),
}


def glyphs_feature_graphic_svg(n: dict) -> str:
    """The pack's feature-graphic composition with the companion's mark,
    name and violet accent, and its own counts."""
    w, h = FEATURE_W, FEATURE_H
    mark = 232
    mx, my = 104, (h - mark) // 2
    scale = mark / 512.0
    accent = glyphs_branding.ACCENT
    name, _ = branding.wordmark_spans(["Core Builds"], 66, 400, [212], "#e6edf3")
    sub, _ = branding.wordmark_spans(["Glyphs Pack"], 40, 400, [286], accent)
    strap = f"{n['icons']} SQUARE ICONS · {n['components']} COMPONENTS"
    tag, _ = branding.wordmark_spans([strap], 17, 402, [336], "#8b949e",
                                     font_path=branding.FONT_MONO)
    rule = f'<rect x="402" y="368" width="330" height="2" fill="{accent}" opacity="0.55"/>'
    return branding.svg(
        w, h,
        f'{branding.FIELD_DEFS}'
        f'<rect width="{w}" height="{h}" fill="url(#fieldBg)"/>'
        f'<g transform="translate({mx},{my}) scale({scale:.4f})">'
        f'{glyphs_branding.MARK}</g>'
        f'{name}{sub}{tag}{rule}',
    )


def render_glyphs() -> dict:
    out_dir = glyphs_dir()
    out_dir.mkdir(parents=True, exist_ok=True)
    n = glyph_counts()
    from svg_renderer import svg2png

    icon = out_dir / "icon-512.png"
    branding.png(glyphs_branding.icon_svg(), icon, ICON_PX, ICON_PX)

    feature = out_dir / "feature-graphic-1024x500.png"
    svg2png(bytestring=glyphs_feature_graphic_svg(n).encode(), write_to=str(feature),
            output_width=FEATURE_W, output_height=FEATURE_H, background_color="#0d1117")
    flatten(feature)

    banner = out_dir / "tv-banner-1280x720.png"
    branding.png(glyphs_branding.banner_svg(glyphs_branding.COMPANION_SUBTITLE),
                 banner, BANNER_W, BANNER_H)
    flatten(banner)

    shots = []
    for index, (name, _) in enumerate(GLYPHS_SCREENSHOTS, start=1):
        src = DOCS / f"app-ui-{name}.png"
        if not src.exists():
            raise SystemExit(f"missing frame {src}; run tools/build_app_ui_mockups.py")
        img = Image.open(src).convert("RGB")
        shot = out_dir / f"tv-{index}-{name}-{SHOT_W}x{SHOT_H}.png"
        img.crop((0, 0, SHOT_W, SHOT_H)).save(shot, format="PNG", optimize=True)
        shots.append(shot)

    payload = {
        "package": GLYPHS_PACKAGE,
        "form_factors": FORM_FACTORS,
        "sideload_package": GLYPHS_SIDELOAD,
        "counts": n,
        "copy": glyphs_copy(n),
        "assets": {
            "icon": {"file": icon.name, "width": ICON_PX, "height": ICON_PX,
                     "mode": "RGBA", "max_bytes": ICON_MAX_BYTES},
            "feature_graphic": {"file": feature.name, "width": FEATURE_W,
                                "height": FEATURE_H, "mode": "RGB"},
            "tv_banner": {"file": banner.name, "width": BANNER_W,
                          "height": BANNER_H, "mode": "RGB"},
            "tv_screenshots": [
                {"file": p.name, "frame": GLYPHS_SCREENSHOTS[i][0],
                 "depicts": GLYPHS_SCREENSHOTS[i][1],
                 "width": SHOT_W, "height": SHOT_H, "mode": "RGB"}
                for i, p in enumerate(shots)
            ],
        },
        "data_safety": GLYPHS_DATA_SAFETY,
        "content_rating": GLYPHS_CONTENT_RATING,
        "sources": glyphs_sources(),
    }
    (out_dir / "listing.json").write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return payload


def glyphs_sources() -> dict:
    return {
        "generator": digest(Path(__file__)),
        "branding": digest(Path(glyphs_branding.__file__)),
        "drawable_xml": digest(GLYPHS_RES / "xml/drawable.xml"),
        "appfilter_xml": digest(GLYPHS_RES / "xml/appfilter.xml"),
        "frames": {f"app-ui-{name}.png": digest(DOCS / f"app-ui-{name}.png")
                   for name, _ in GLYPHS_SCREENSHOTS},
    }


def check_glyphs() -> list[str]:
    """The glyphs listing's problems, in check()'s wording; empty when fresh."""
    path = glyphs_dir() / "listing.json"
    if not path.exists():
        return ["docs/play/glyphs/listing.json missing"]
    spec = json.loads(path.read_text(encoding="utf-8"))
    problems = []
    n = glyph_counts()
    if spec.get("counts") != n:
        problems.append(f"glyphs counts moved: listing says {spec.get('counts')}, tree says {n}")
    if spec.get("copy") != glyphs_copy(n):
        problems.append("glyphs copy is stale")
    if spec.get("data_safety") != GLYPHS_DATA_SAFETY:
        problems.append("glyphs data_safety is stale")
    if spec.get("content_rating") != GLYPHS_CONTENT_RATING:
        problems.append("glyphs content_rating is stale")
    if spec.get("sources") != glyphs_sources():
        problems.append("a glyphs source changed since its assets were rendered")
    assets = spec.get("assets", {})
    entries = [assets.get(k, {}) for k in ("icon", "feature_graphic", "tv_banner")]
    entries += assets.get("tv_screenshots", [])
    if not assets.get("tv_screenshots"):
        problems.append("glyphs: no TV screenshots: Play will not publish a TV listing")
    for entry in entries:
        asset = glyphs_dir() / entry.get("file", "")
        if not asset.is_file():
            problems.append(f"glyphs: {entry.get('file')} missing")
            continue
        with Image.open(asset) as im:
            if (im.width, im.height) != (entry["width"], entry["height"]):
                problems.append(f"glyphs: {asset.name} is {im.size}")
            if im.mode != entry["mode"]:
                problems.append(f"glyphs: {asset.name} mode {im.mode}, expected {entry['mode']}")
    return problems


def main(argv: list[str]) -> int:
    if "--check" in argv:
        return check()
    payload = render_all()
    assets = payload["assets"]
    print(f"wrote docs/play/{assets['icon']['file']} ({ICON_PX}x{ICON_PX})")
    print(f"wrote docs/play/{assets['feature_graphic']['file']} "
          f"({FEATURE_W}x{FEATURE_H})")
    print(f"wrote docs/play/{assets['tv_banner']['file']} ({BANNER_W}x{BANNER_H})")
    for entry in assets["tv_screenshots"]:
        print(f"wrote docs/play/{entry['file']} ({SHOT_W}x{SHOT_H}, {entry['frame']})")
    print(f"wrote docs/play/listing.json - title {len(payload['copy']['title'])} chars, "
          f"short {len(payload['copy']['short_description'])} chars, "
          f"full {len(payload['copy']['full_description'])} chars")
    g = json.loads((glyphs_dir() / "listing.json").read_text(encoding="utf-8"))
    print(f"wrote docs/play/glyphs/ - {len(g['assets']['tv_screenshots'])} TV screenshot, "
          f"title {len(g['copy']['title'])} chars, short "
          f"{len(g['copy']['short_description'])} chars, full "
          f"{len(g['copy']['full_description'])} chars")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
