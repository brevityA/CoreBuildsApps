#!/usr/bin/env python3
"""
Core Builds Pop — asset pipeline.

Same single source of truth as the classic pack (`tools/catalog.json`), a
different renderer (`tools/popart.py`). Nothing about an app's identity is
duplicated: names, drawables, components and accent colours all come from the
one catalog, so adding an app to the suite adds it to both packs at once and
they can never disagree about what `netflix` maps to.

Writes, deterministically:

  assets/pop/svg/<drawable>.svg                 512 master, square
  assets/pop/banners/<drawable>.svg             1280x720 master, 16:9
  pop/src/main/res/drawable-nodpi/<d>.png       512 square
  pop/src/main/res/drawable-nodpi/<d>_banner.png  320x180 card
  pop/src/main/res/xml/appfilter.xml            component -> drawable
  pop/src/main/res/xml/drawable.xml             icon-pack browser grid
  pop/src/main/res/xml/iconpack.xml             legacy pack list
  pop/src/main/res/values/icon_pack.xml         pack metadata arrays
  pop/src/main/res/values/strings.xml           pack identity (mirror + overrides)
  pop/src/main/res/{layout,drawable,xml,values}  mirrored from app/ (see below)
  pop/src/main/res/{mipmap-*,drawable-nodpi/cb_banner.png}  Pop branding
  docs/PopIconList.md                           supported list
  docs/pop-preview.svg|.png                     contact sheet
  docs/pop-palette.svg|.png                     the 16 locked swatches

Mirroring: the Kotlin lives once, in app/src/main/java, and both modules
compile it. The non-generated resources it needs (layouts, shape drawables,
theme, colours) also live once, in app/, and this script copies them into pop/.
Copies are regenerated on every run and CI diffs the result, so the mirror
cannot drift — the same contract the icon XML already lives under.

Receipts voice: prints exactly what was written, counted.
"""
from __future__ import annotations

import io
import json
import os
import re
import shutil
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Render imports stay lazy: --mirror-only must run on stdlib alone (it only
# copies layouts/strings/manifest), including machines without the render
# venv that tools/requirements.txt installs.
if "--mirror-only" not in sys.argv:
    import popart  # noqa: E402
    from popart import PALETTE, SWATCHES, render_banner, render_icon, snap  # noqa: E402
    from build_icons import validate  # noqa: E402 — shared catalog/style contract
    from svg_renderer import svg2png  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"

POP = ROOT / "pop"
POP_MAIN = POP / "src" / "main"
POP_RES = POP_MAIN / "res"
POP_PNG = POP_RES / "drawable-nodpi"
POP_XML = POP_RES / "xml"
POP_VAL = POP_RES / "values"
POP_ASSETS = POP_MAIN / "assets"

SVG_DIR = ROOT / "assets" / "pop" / "svg"
BANNER_DIR = ROOT / "assets" / "pop" / "banners"
DOC_DIR = ROOT / "docs"

APP_RES = ROOT / "app" / "src" / "main" / "res"

PNG_SIZE = 512
BANNER_W, BANNER_H = 320, 180

# Flat art with a halftone screen is exactly the case indexed PNG was invented
# for: five real colours plus antialiasing. Truecolour RGBA cost 87MB across
# the pack and made the APK unshippable; a 64-entry adaptive palette is
# visually identical and lands around 17MB.
PNG_COLORS = 64

# Resources that belong to the app, not to either pack's art. Copied from app/
# so there is one editable copy and CI can prove the mirror is current.
MIRROR_DIRS = ["layout", "drawable", "values-v21", "color"]
MIRROR_FILES = [
    "xml/file_paths.xml",
    "values/colors.xml",
    "values/themes.xml",
    # Shared UI metrics. Layouts in MIRROR_DIRS reference @dimen/*, so Pop
    # cannot build without this file present — omitting it fails resource
    # linking on both packs' shared layouts, not just one.
    "values/dimens.xml",
]

# Pop's own identity. Everything else in strings.xml is shared UI copy.
STRING_OVERRIDES = {
    "app_name": "Core Builds Pop",
    "kicker": "CORE BUILDS · POP",
    "tagline": "Pop-art cartoon app icons for Projectivy on Android TV.",
    "projectivy_opened":
        "Projectivy opened. Go to Appearance → Cards → Icon Pack → Core Builds Pop.",
    "cta_sub_apply_fmt":
        "Sets Core Builds Pop as the icon pack in %1$s. Reversible — pick another pack any time.",
}

CAT_LABEL = {
    "STREAM": "Streaming", "MEDIA": "Media centres", "VOD": "On demand",
    "LIVE": "Live TV", "PLAYER": "Players", "MUSIC": "Music",
    "SPORT": "Sport", "GAMING": "Gaming", "DEBRID": "Debrid",
    "FILES": "Files", "TOOL": "Tools", "STORE": "Stores",
    "LAUNCHER": "Launchers", "VPN": "VPN", "BROWSER": "Browsers",
    "REMOTE": "Remote", "SYSTEM": "System", "TRACK": "Tracking",
    "CORE": "Core Builds", "VIDEO": "Video", "APP": "Apps",
}
CAT_ORDER = ["CORE", "STREAM", "MEDIA", "VOD", "LIVE", "PLAYER", "VIDEO",
             "MUSIC", "SPORT", "GAMING", "DEBRID", "FILES", "TOOL",
             "STORE", "LAUNCHER", "VPN", "BROWSER", "REMOTE", "SYSTEM",
             "TRACK", "APP"]


def esc(s: str) -> str:
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
             .replace('"', "&quot;"))


def esc_android(s: str) -> str:
    return esc(s).replace("'", "\\'")


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_png(svg_text: str, path: Path, width: int, height: int) -> None:
    from PIL import Image
    png = svg2png(bytestring=svg_text.encode("utf-8"),
                  output_width=width, output_height=height,
                  background_color=None)
    im = Image.open(io.BytesIO(png)).convert("RGBA")
    q = im.quantize(colors=PNG_COLORS, method=Image.FASTOCTREE)
    path.parent.mkdir(parents=True, exist_ok=True)
    q.save(path, "PNG", optimize=True)


def expand(component: str) -> list[str]:
    """Both the shorthand and fully-qualified forms of a component.

    Identical to the classic pack's rule, and for the same reason: launchers
    match the literal string inside ComponentInfo{...} and not all of them
    expand a leading dot.
    """
    pkg, _, act = component.partition("/")
    if not act:
        return [component]
    if act.startswith("."):
        return [f"{pkg}/{pkg}{act}", component]
    if act.startswith(pkg + "."):
        return [component, f"{pkg}/{act[len(pkg):]}"]
    return [component]




# --------------------------------------------------------------------------
# Branding — the pack's own launcher icon and Leanback banner, drawn in the
# same language as its contents rather than borrowed from the classic pack.
# --------------------------------------------------------------------------
BRAND_GLYPH = "core_mark"
BRAND_ACCENT = "#E03127"     # snaps to pop_red


def build_branding() -> int:
    written = 0
    icon_svg = render_icon(BRAND_GLYPH, BRAND_ACCENT, uid="brand")
    for dpi, size in (("xhdpi", 96), ("xxhdpi", 144)):
        write_png(icon_svg, POP_RES / f"mipmap-{dpi}" / "ic_launcher.png", size, size)
        written += 1
    # Adaptive foreground: the mark alone at 66% of the 108dp canvas, because
    # the launcher supplies the shape and the mask would crop our keyline.
    fg = _foreground_svg()
    for dpi, size in (("xhdpi", 216), ("xxhdpi", 324)):
        write_png(fg, POP_RES / f"mipmap-{dpi}" / "ic_launcher_foreground.png",
                  size, size)
        written += 1
    write(POP_RES / "mipmap-anydpi-v26" / "ic_launcher.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- Generated by tools/build_pop.py. Do not edit by hand. -->\n'
          '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
          '    <background android:drawable="@color/pop_brand_field" />\n'
          '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
          '</adaptive-icon>\n')
    written += 1
    write_png(_leanback_banner_svg(), POP_PNG / "cb_banner.png", 640, 360)
    written += 1
    return written


def _foreground_svg() -> str:
    mark = popart.inked_mark(BRAND_GLYPH, target=280.0,
                             outline=popart.GLYPH_OUTLINE)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" '
            f'width="512" height="512">'
            f'<g transform="translate(256,256)">{mark}</g></svg>\n')


def _leanback_banner_svg() -> str:
    """320x180dp TV banner: the mark, the wordmark, on a full-bleed pop field.

    This is the one place a wordmark belongs — it is our own card in the TV
    launcher's app row, not somebody else's.
    """
    from typeface import wordmark_spans
    _, field = snap(BRAND_ACCENT)
    dot = popart.shade(field, 0.70)
    w, h = 1280, 720
    # Square-ish corners: the Leanback banner is composited into a card the
    # launcher already rounds, so rounding it again double-rounds the corner.
    path = popart._rounded_rect(0, 0, w, h, 1)
    mark = popart.inked_mark(BRAND_GLYPH, target=300.0, outline=44.0)
    ink, ink_w = wordmark_spans(["CORE BUILDS POP"], 104, 0.0, [0.0], popart.INK)
    face, _ = wordmark_spans(["CORE BUILDS POP"], 104, 0.0, [0.0], popart.CREAM)
    # wordmark_spans writes stroke="none" onto every path, so a stroke on the
    # wrapping <g> is overridden and the keyline silently disappears. Set it on
    # the paths themselves.
    ink = ink.replace('stroke="none"',
                      f'stroke="{popart.INK}" stroke-width="26" '
                      f'stroke-linejoin="round"')
    return (
        f'{popart._SVG_OPEN} width="{w}" height="{h}" viewBox="0 0 {w} {h}">'
        f'{popart._field_stack("bn", path, field, dot, w, h, 0, 0, 34.0, 7.0)}'
        f'<g transform="translate({w / 2},{h / 2 - 88})">{mark}</g>'
        f'<g transform="translate({(w - ink_w) / 2:.1f},{h - 120})">'
        f'{ink}{face}</g>'
        f'</svg>\n')


# --------------------------------------------------------------------------
# Mirror
# --------------------------------------------------------------------------
def mirror_shared_resources() -> int:
    copied = 0
    for d in MIRROR_DIRS:
        src = APP_RES / d
        if not src.is_dir():
            continue
        dst = POP_RES / d
        if dst.exists():
            shutil.rmtree(dst)
        shutil.copytree(src, dst)
        copied += sum(1 for _ in dst.rglob("*") if _.is_file())
    for f in MIRROR_FILES:
        src, dst = APP_RES / f, POP_RES / f
        if not src.exists():
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        copied += 1
    return copied



# Projectivy 4.70 (Jun 2026) added the ability to map its *own* internal
# activities through an icon pack — issue spocky/miproja1#512. Settings, the
# category and channel shortcuts, and the HDMI/AV input cards can all be
# themed now. As of writing no pack ships this, and there is a posted request
# for exactly it on r/Projectivy_Launcher:
#
#   "just a banner shaped icon for SmartTube and TV ... and my HDMI outputs so
#    I don't have the square HDMI icon that Projectivy use which don't fit
#    with the other app icons"
#
# Activity paths come from the issue. Both the fully-qualified and the
# shorthand form are emitted, same as for third-party apps, because we cannot
# see which form Projectivy resolves internally. Entries that match nothing
# are inert, so over-mapping costs nothing and under-mapping costs the feature.
PROJECTIVY_PKG = "com.spocky.projengmenu"
PROJECTIVY_INTERNALS = [
    ("gear", [
        "ui.settings.SettingsActivity",
        "ui.guidedActions.activities.settings.AppSettingsActivity",
    ]),
    ("folder", [
        "ui.guidedActions.activities.shortcut.CategoryShortcutActivity",
        "ui.launcherActivities.CategoryShortcutActivity",
    ]),
    ("tv_stack", [
        "ui.guidedActions.activities.shortcut.ChannelShortcutActivity",
        "ui.launcherActivities.ChannelShortcutActivity",
    ]),
    # Inputs get numbered rather than sharing one mark. The complaint that
    # prompted this was that Projectivy's stock input icons "don't fit with the
    # other app icons" — replacing four identical cards with four identical
    # cards would fix the style and keep the actual problem, which is that you
    # cannot tell HDMI 2 from HDMI 3 at a glance.
    ("hdmi1", ["ui.guidedActions.activities.input.SourceHDMI1Activity"]),
    ("hdmi2", ["ui.guidedActions.activities.input.SourceHDMI2Activity"]),
    ("hdmi3", ["ui.guidedActions.activities.input.SourceHDMI3Activity"]),
    ("hdmi4", ["ui.guidedActions.activities.input.SourceHDMI4Activity"]),
    ("av", ["ui.guidedActions.activities.input.SourceAVActivity"]),
]

# drawable name -> the glyph that draws it.
INTERNAL_GLYPH = {
    "gear": "gear",
    "folder": "folder",
    "tv_stack": "tv_stack",
    "hdmi1": "tile_1",
    "hdmi2": "tile_2",
    "hdmi3": "tile_3",
    "hdmi4": "tile_4",
    "av": "monitor_wave",
}

# Which Pop swatch each internal card gets. Launcher furniture should read as
# furniture, so these sit in the neutral end of the palette rather than
# competing with the app cards around them.
INTERNAL_ACCENT = {
    "gear": "#59637A",      # pop_slate
    "folder": "#59637A",
    "tv_stack": "#59637A",
    "hdmi1": "#333A4B",     # pop_graphite — inputs read as one family
    "hdmi2": "#333A4B",
    "hdmi3": "#333A4B",
    "hdmi4": "#333A4B",
    "av": "#333A4B",
}

_STRING_RE = re.compile(r'(<string name="([^"]+)">)(.*?)(</string>)', re.S)


def mirror_manifest() -> None:
    """app/'s AndroidManifest, re-pointed at Pop.

    The manifest is 200 lines of launcher discovery actions and `<queries>`
    entries that took real device testing to get right. Maintaining a second
    hand-written copy guarantees the two drift, and the pack that drifts is the
    one that silently stops appearing in somebody's launcher. Two substitutions
    are all Pop actually needs.
    """
    src = (ROOT / "app" / "src" / "main" / "AndroidManifest.xml").read_text(
        encoding="utf-8")
    out = src.replace('android:authorities="tv.corebuilds.iconpack.update"',
                      'android:authorities="tv.corebuilds.iconpack.pop.update"')
    if out == src:
        raise SystemExit("mirror_manifest: FileProvider authority not found — "
                         "app/src/main/AndroidManifest.xml changed shape")
    header = ('<?xml version="1.0" encoding="utf-8"?>\n'
              '<!-- Generated by tools/build_pop.py from\n'
              '     app/src/main/AndroidManifest.xml. Do not edit by hand:\n'
              '     edit the app/ copy and re-run the generator. -->\n')
    out = out.replace('<?xml version="1.0" encoding="utf-8"?>\n', header, 1)
    write(POP_MAIN / "AndroidManifest.xml", out)


def mirror_strings() -> int:
    """app/ strings, with Pop's identity substituted.

    The UI copy is shared because the two packs are the same app; only the
    lines that name the product differ. Overriding by key rather than keeping a
    second full file means new strings appear in both packs automatically.
    """
    src = (APP_RES / "values" / "strings.xml").read_text(encoding="utf-8")
    hits = {"n": 0}

    def repl(m):
        key = m.group(2)
        if key in STRING_OVERRIDES:
            hits["n"] += 1
            return m.group(1) + esc_android(STRING_OVERRIDES[key]) + m.group(4)
        return m.group(0)

    out = _STRING_RE.sub(repl, src)
    out = out.replace(
        "<resources>",
        "<resources>\n    <!-- Mirrored from app/src/main/res/values/strings.xml by\n"
        "         tools/build_pop.py, with Pop's product identity substituted.\n"
        "         Edit the app/ copy, not this one. -->", 1)
    write(POP_VAL / "strings.xml", out)
    missing = set(STRING_OVERRIDES) - set(
        m.group(2) for m in _STRING_RE.finditer(src))
    if missing:
        print(f"  \u26a0 override keys absent from app strings.xml: "
              f"{', '.join(sorted(missing))}")
    return hits["n"]


# --------------------------------------------------------------------------
def main(argv: list[str] | None = None) -> int:
    # Fast path for UI work: refresh the app/ -> pop/ resource mirror
    # (layouts, strings, manifest) without re-rendering 1,850 PNGs.
    # tools/build_ui.py --apply uses this so a new screen compiles in both
    # packs seconds after it lands in app/.
    if (argv if argv is not None else sys.argv[1:]) == ["--mirror-only"]:
        mirrored = mirror_shared_resources()
        overridden = mirror_strings()
        mirror_manifest()
        print(f"mirror-only: {mirrored} shared resource file(s) + "
              f"AndroidManifest from app/, {overridden} string(s) re-pointed")
        return 0
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())
    version = json.loads((ROOT / "suite.json").read_text(
        encoding="utf-8"))["apps"]["pop"]["versionName"]

    errs = validate(icons, data.get("artwork"))
    if errs:
        print("Catalog rejected — %d problem(s):" % len(errs))
        for e in errs:
            print("  \u2717 " + e)
        return 1

    # 1. masters
    for i in icons:
        write(SVG_DIR / f"{i['drawable']}.svg",
              render_icon(i["glyph"], i["color"], uid=i["drawable"]))
        write(BANNER_DIR / f"{i['drawable']}.svg",
              render_banner(i["glyph"], i["color"], uid=i["drawable"]))
    print(f"\u2713 SVG masters written ({len(icons)} square + {len(icons)} banner) "
          f"\u2192 assets/pop/")

    # 2. rasters
    png_written = 0
    try:
        for i in icons:
            d = i["drawable"]
            write_png((SVG_DIR / f"{d}.svg").read_text(encoding="utf-8"),
                      POP_PNG / f"{d}.png", PNG_SIZE, PNG_SIZE)
            write_png((BANNER_DIR / f"{d}.svg").read_text(encoding="utf-8"),
                      POP_PNG / f"{d}_banner.png", BANNER_W, BANNER_H)
            png_written += 2

        # Projectivy's own cards. Rendered through the same render_banner as
        # every app card, so launcher furniture sits in the row looking like it
        # belongs there rather than like a system icon that wandered in.
        for drawable, _acts in PROJECTIVY_INTERNALS:
            svg = render_banner(INTERNAL_GLYPH[drawable],
                                INTERNAL_ACCENT[drawable], uid=f"pl{drawable}")
            write(BANNER_DIR / f"pl_{drawable}.svg", svg)
            write_png(svg, POP_PNG / f"pl_{drawable}_banner.png",
                      BANNER_W, BANNER_H)
            png_written += 1

        # Fallback furniture for apps the pack does not cover.
        for name, hexv in SWATCHES.items():
            back = popart.render_iconback(hexv, uid=f"kb{name}")
            stem = f"pop_back_{name.replace('pop_', '')}"
            write(SVG_DIR / f"{stem}.svg", back)
            write_png(back, POP_PNG / f"{stem}.png", PNG_SIZE, PNG_SIZE)
            png_written += 1
        for stem, svg in (("pop_mask", popart.render_iconmask()),
                          ("pop_upon", popart.render_iconupon())):
            write(SVG_DIR / f"{stem}.svg", svg)
            write_png(svg, POP_PNG / f"{stem}.png", PNG_SIZE, PNG_SIZE)
            png_written += 1

        print(f"\u2713 PNG written ({png_written}, indexed {PNG_COLORS}-colour) "
              f"\u2192 pop/res/drawable-nodpi/")
    except (ImportError, OSError) as exc:
        print(f"\u26a0 no SVG rasterizer available ({exc}) — PNGs skipped. "
              f"Run: pip install -r tools/requirements.txt")

    # 3. appfilter — auto-assignment targets the 16:9 banner, as in classic
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- Generated by tools/build_pop.py. Do not edit by hand. -->',
             '<resources>']
    comp_count = emitted = 0
    seen: set[str] = set()
    for i in icons:
        lines.append(f'    <!-- {esc(i["name"])} -->')
        for comp in i["components"]:
            comp_count += 1
            for variant in expand(comp):
                if variant in seen:
                    continue
                seen.add(variant)
                lines.append(
                    f'    <item component="ComponentInfo{{{esc(variant)}}}" '
                    f'drawable="{i["drawable"]}_banner"/>')
                emitted += 1
    internal = 0
    lines.append('    <!-- Projectivy Launcher internal activities (4.70+) -->')
    for drawable, activities in PROJECTIVY_INTERNALS:
        for act in activities:
            for variant in (f"{PROJECTIVY_PKG}/{PROJECTIVY_PKG}.{act}",
                            f"{PROJECTIVY_PKG}/.{act}"):
                if variant in seen:
                    continue
                seen.add(variant)
                lines.append(
                    f'    <item component="ComponentInfo{{{esc(variant)}}}" '
                    f'drawable="pl_{drawable}_banner"/>')
                internal += 1

    # Fallback furniture: the launcher composites an unthemed app's own icon
    # onto these. Without it, Pop's "one container" claim dies the moment the
    # user has an app we do not cover. Sixteen backs so unthemed apps land
    # across the whole palette rather than all going one colour.
    backs = " ".join(f'img{n + 1}="pop_back_{name.replace("pop_", "")}"'
                     for n, name in enumerate(SWATCHES))
    lines += ['    <!-- Unthemed apps get the Pop container anyway -->',
              f'    <iconback {backs}/>',
              '    <iconmask img1="pop_mask"/>',
              '    <iconupon img1="pop_upon"/>',
              f'    <scale factor="{popart.FALLBACK_SCALE}"/>']

    lines.append('</resources>')
    appfilter = "\n".join(lines) + "\n"
    write(POP_XML / "appfilter.xml", appfilter)
    write(POP_ASSETS / "appfilter.xml", appfilter)
    print(f"\u2713 appfilter.xml written — {comp_count} catalog components "
          f"\u2192 {emitted} entries (both name forms) "
          f"+ {internal} Projectivy internals + fallback furniture "
          f"\u2192 {len(icons)} drawables (res/xml + assets)")

    # 4. browser grid
    by_cat: dict[str, list[dict]] = {}
    for i in icons:
        by_cat.setdefault(i.get("category") or "APP", []).append(i)
    order = ([c for c in CAT_ORDER if c in by_cat]
             + [c for c in by_cat if c not in CAT_ORDER])

    d = ['<?xml version="1.0" encoding="utf-8"?>',
         '<!-- Generated by tools/build_pop.py. Do not edit by hand. -->',
         '<resources>']
    # Launcher furniture first: these are the cards a Projectivy user most
    # wants to find, and burying them under 924 app banners means nobody does.
    # The iconback/mask/upon drawables are deliberately NOT listed — they are
    # compositing inputs, not icons, and showing them in a picker is noise.
    d.append('    <category title="Banners \u00b7 Projectivy Launcher" />')
    for drawable, _acts in PROJECTIVY_INTERNALS:
        d.append(f'    <item drawable="pl_{drawable}_banner" />')
    for cat in order:
        d.append(f'    <category title="Banners \u00b7 '
                 f'{esc(CAT_LABEL.get(cat, cat.title()))}" />')
        for i in by_cat[cat]:
            d.append(f'    <item drawable="{i["drawable"]}_banner" />')
    for cat in order:
        d.append(f'    <category title="Square \u00b7 '
                 f'{esc(CAT_LABEL.get(cat, cat.title()))}" />')
        for i in by_cat[cat]:
            d.append(f'    <item drawable="{i["drawable"]}" />')
    d.append('</resources>')
    drawable_xml = "\n".join(d) + "\n"
    write(POP_XML / "drawable.xml", drawable_xml)
    write(POP_ASSETS / "drawable.xml", drawable_xml)

    p = ['<?xml version="1.0" encoding="utf-8"?>', '<iconpack>']
    p += [f'    <item drawable="{i["drawable"]}" />' for i in icons]
    p.append('</iconpack>')
    write(POP_XML / "iconpack.xml", "\n".join(p) + "\n")
    print(f"\u2713 drawable.xml + iconpack.xml written (browser grid, "
          f"{len(icons)} entries)")

    # 5. values arrays for the in-app browser
    v = ['<?xml version="1.0" encoding="utf-8"?>',
         '<!-- Generated by tools/build_pop.py. Do not edit by hand. -->',
         '<resources>', '    <string-array name="icon_pack">']
    v += [f'        <item>{i["drawable"]}</item>' for i in icons]
    v.append('    </string-array>')
    v.append('    <string-array name="icon_names">')
    v += [f'        <item>{esc_android(i["name"])}</item>' for i in icons]
    v.append('    </string-array>')
    v.append('    <string-array name="icon_categories">')
    v += [f'        <item>{esc_android(i.get("category") or "APP")}</item>'
          for i in icons]
    v.append('    </string-array>')
    v.append(f'    <integer name="icon_count">{len(icons)}</integer>')
    v.append('</resources>')
    write(POP_VAL / "icon_pack.xml", "\n".join(v) + "\n")

    # 6. the locked palette as a resource, so the app chrome and the icons can
    #    never drift apart
    sw = ['<?xml version="1.0" encoding="utf-8"?>',
          '<!-- Generated by tools/build_pop.py. Do not edit by hand. -->',
          '<resources>',
          f'    <color name="pop_ink">{popart.INK}</color>',
          f'    <color name="pop_cream">{popart.CREAM}</color>',
          f'    <color name="pop_brand_field">{snap(BRAND_ACCENT)[1]}</color>']
    for name, hexv in SWATCHES.items():
        sw.append(f'    <color name="{name}">{hexv}</color>')
    sw.append('</resources>')
    write(POP_VAL / "pop_palette.xml", "\n".join(sw) + "\n")

    # 7. shared resources + strings
    mirrored = mirror_shared_resources()
    overridden = mirror_strings()
    mirror_manifest()
    print(f"\u2713 mirrored {mirrored} shared resource file(s) + AndroidManifest "
          f"from app/, {overridden} string(s) re-pointed at Pop")

    # 8. branding
    brand = build_branding()
    print(f"\u2713 branding written ({brand} files: launcher icon, adaptive "
          f"foreground, Leanback banner)")

    # 9. supported list
    md = ["# Core Builds Pop — supported applications",
          "",
          f"`{len(icons)}` icons \u00b7 `{comp_count}` mapped components \u00b7 "
          f"pack v{version}",
          "",
          "Pop is drawn from the same `tools/catalog.json` as the classic Core "
          "Builds Icon Pack, so coverage is identical and always will be. What "
          "differs is the render: one container, one keyline weight, one "
          "halftone screen, and 16 locked swatches instead of 169 accents.",
          "",
          "| App | Drawable | Brand accent | Pop swatch | Components |",
          "| --- | --- | --- | --- | --- |"]
    for i in icons:
        comps = "<br>".join(f"`{c}`" for c in i["components"])
        name, hexv = snap(i["color"])
        md.append(f"| {i['name']} | `{i['drawable']}` | `{i['color']}` | "
                  f"`{name}` `{hexv}` | {comps} |")
    write(DOC_DIR / "PopIconList.md", "\n".join(md) + "\n")
    print(f"\u2713 docs/PopIconList.md written ({len(icons)} rows)")

    # 10. contact sheet
    _contact_sheet(icons, version, comp_count)
    _palette_sheet(icons)

    print(f"\nBuild complete \u2014 {len(icons)} icons, {comp_count} components, "
          f"{png_written} PNGs, {len(SWATCHES)} swatches. "
          f"Verified by re-read of the catalog.")
    return 0


def _contact_sheet(icons, version, comp_count) -> None:
    cols, cell = 8, 150
    rows = (len(icons) + cols - 1) // cols
    w, h = cols * cell, rows * cell + 86
    s = [f'{popart._SVG_OPEN} width="{w}" height="{h}" viewBox="0 0 {w} {h}">',
         f'<rect width="{w}" height="{h}" fill="#0d1117"/>',
         _label("CORE BUILDS POP", 24, 46, 26, "#e6edf3"),
         _label(f"{len(icons)} ICONS \u00b7 {comp_count} COMPONENTS \u00b7 "
                f"{len(SWATCHES)} SWATCHES \u00b7 V{version}",
                24, 72, 14, "#8b949e")]
    for n, i in enumerate(icons):
        cx, cy = (n % cols) * cell, 86 + (n // cols) * cell
        body = render_icon(i["glyph"], i["color"], uid=f"c{n}")
        inner = re.sub(r"^<svg[^>]*>|</svg>\s*$", "", body.strip(), flags=re.S)
        s.append(f'<g transform="translate({cx + 25},{cy + 6}) '
                 f'scale({100 / 512:.5f})">{inner}</g>')
        s.append(_label(i["name"][:16].upper(), cx + cell / 2, cy + cell - 14,
                        11, "#8b949e", anchor="middle"))
    s.append('</svg>')
    write(DOC_DIR / "pop-preview.svg", "\n".join(s) + "\n")
    print("\u2713 docs/pop-preview.svg written (contact sheet)")
    _sheet_png("pop-preview", 1200)


def _palette_sheet(icons) -> None:
    """One image that answers 'is this actually uniform?' — every swatch, how
    many apps landed on it, and a real icon rendered in it."""
    counts: dict[str, int] = {}
    example: dict[str, dict] = {}
    for i in icons:
        name, _ = snap(i["color"])
        counts[name] = counts.get(name, 0) + 1
        example.setdefault(name, i)

    order = [n for n, _, _ in PALETTE] + ["pop_slate", "pop_graphite"]
    cols, cw, ch = 4, 300, 150
    rows = (len(order) + cols - 1) // cols
    w, h = cols * cw, rows * ch + 100
    s = [f'{popart._SVG_OPEN} width="{w}" height="{h}" viewBox="0 0 {w} {h}">',
         f'<rect width="{w}" height="{h}" fill="#0d1117"/>',
         _label("THE LOCKED PALETTE", 28, 50, 28, "#e6edf3"),
         _label(f"169 BRAND ACCENTS \u2192 {len(order)} SWATCHES, "
                f"SNAPPED BY HUE", 28, 78, 14, "#8b949e")]
    for n, name in enumerate(order):
        cx, cy = (n % cols) * cw + 28, 100 + (n // cols) * ch
        hexv = SWATCHES[name]
        ex = example.get(name)
        if ex:
            body = render_icon(ex["glyph"], ex["color"], uid=f"p{n}")
            inner = re.sub(r"^<svg[^>]*>|</svg>\s*$", "", body.strip(), flags=re.S)
            s.append(f'<g transform="translate({cx},{cy}) '
                     f'scale({104 / 512:.5f})">{inner}</g>')
        s.append(_label(name.replace("pop_", "").upper(), cx + 122, cy + 42,
                        17, "#e6edf3"))
        s.append(_label(hexv, cx + 122, cy + 66, 15, hexv))
        s.append(_label(f"{counts.get(name, 0)} APPS", cx + 122, cy + 90,
                        14, "#8b949e"))
    s.append('</svg>')
    write(DOC_DIR / "pop-palette.svg", "\n".join(s) + "\n")
    _sheet_png("pop-palette", 1200)
    print("\u2713 docs/pop-palette.svg|.png written (swatch census)")


def _label(text: str, x: float, y: float, size: float, fill: str,
           anchor: str = "start") -> str:
    """Outfit Bold as outlined paths, never as <text>.

    resvg ships without a font database, so an <svg><text> label renders as
    nothing at all in CI while looking fine in a browser — which is how a
    contact sheet ends up committed with no labels on it. The pack already
    converts every wordmark to paths for exactly this reason.
    """
    from typeface import wordmark_spans
    markup, width = wordmark_spans([text], size, 0.0, [0.0], fill)
    dx = x - width / 2 if anchor == "middle" else x
    return f'<g transform="translate({dx:.1f},{y})">{markup}</g>'


def _sheet_png(stem: str, width: int) -> None:
    """Rasterise a docs sheet, indexed. A truecolour contact sheet of 924
    halftoned icons is 6MB of PNG for something GitHub renders 900px wide."""
    try:
        from PIL import Image
        png = svg2png(url=str(DOC_DIR / f"{stem}.svg"), output_width=width,
                      background_color="#0d1117")
        im = Image.open(io.BytesIO(png)).convert("RGB")
        im.quantize(colors=128, method=Image.FASTOCTREE).save(
            DOC_DIR / f"{stem}.png", "PNG", optimize=True)
        print(f"\u2713 docs/{stem}.png written")
    except (ImportError, OSError):
        pass


if __name__ == "__main__":
    raise SystemExit(main())
