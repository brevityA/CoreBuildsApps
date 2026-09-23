#!/usr/bin/env python3
"""
Core Builds Banners — asset pipeline.

Same single source of truth as the flagship pack (`tools/catalog.json`),
providing 16:9 banner card art under base drawable names so launchers present
clean names without '_banner' suffixes in pickers.

Writes, deterministically:

  banners/src/main/res/drawable-nodpi/<d>.png    320x180 16:9 banner card
  banners/src/main/res/xml/appfilter.xml         component -> base drawable
  banners/src/main/res/xml/drawable.xml          icon-pack browser grid
  banners/src/main/res/xml/iconpack.xml          legacy pack list
  banners/src/main/res/values/icon_pack.xml      pack metadata arrays
  banners/src/main/res/values/strings.xml        pack identity (mirror + overrides)
  banners/src/main/res/{layout,drawable,xml,values}  mirrored from app/
  banners/src/main/assets/{appfilter,drawable,iconpack,icon_pack}.xml  assets copies
  docs/BannersIconList.md                        supported list

Receipts voice: prints exactly what was written, counted.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glyphs import is_monogram  # noqa: E402
from build_icons import validate  # noqa: E402 — shared catalog/style contract

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"

BANNERS = ROOT / "banners"
BANNERS_MAIN = BANNERS / "src" / "main"
BANNERS_RES = BANNERS_MAIN / "res"
BANNERS_PNG = BANNERS_RES / "drawable-nodpi"
BANNERS_XML = BANNERS_RES / "xml"
BANNERS_VAL = BANNERS_RES / "values"
BANNERS_ASSETS = BANNERS_MAIN / "assets"

DOC_DIR = ROOT / "docs"

APP_MAIN = ROOT / "app" / "src" / "main"
APP_RES = APP_MAIN / "res"
APP_PNG = APP_RES / "drawable-nodpi"
APP_ASSETS = APP_MAIN / "assets"

# Resources that belong to the app, not to either pack's art. Copied from app/
# so there is one editable copy and CI can prove the mirror is current.
MIRROR_DIRS = ["layout", "drawable", "values-v21", "color"]
MIRROR_FILES = [
    "xml/file_paths.xml",
    "values/colors.xml",
    "values/themes.xml",
    "values/issue_prefill.xml",
    "values/suite_hub.xml",
    "values/dimens.xml",
]

# Banners' own identity. Everything else in strings.xml is shared UI copy.
STRING_OVERRIDES = {
    "app_name": "Core Builds Banners",
    "kicker": "CORE BUILDS · BANNERS",
    "tagline": "16:9 banner app cards for Projectivy on Android TV.",
    "projectivy_opened":
        "Projectivy opened. Go to Appearance → Cards → Icon Pack → Core Builds Banners.",
    "cta_sub_apply_fmt":
        "Sets Core Builds Banners as the icon pack in %1$s. Reversible — pick another pack any time.",
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


def expand(component: str) -> list[str]:
    """Both the shorthand and fully-qualified forms of a component."""
    pkg, _, act = component.partition("/")
    if not act:
        return [component]
    if act.startswith("."):
        return [f"{pkg}/{pkg}{act}", component]
    if act.startswith(pkg + "."):
        return [component, f"{pkg}/{act[len(pkg):]}"]
    return [component]


def mirror_shared_resources() -> int:
    copied = 0
    for d in MIRROR_DIRS:
        src = APP_RES / d
        if not src.is_dir():
            continue
        dst = BANNERS_RES / d
        if dst.exists():
            shutil.rmtree(dst)
        shutil.copytree(src, dst)
        copied += sum(1 for _ in dst.rglob("*") if _.is_file())
    for f in MIRROR_FILES:
        src, dst = APP_RES / f, BANNERS_RES / f
        if not src.exists():
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        copied += 1
    # Wallpapers manifest + thumbs
    src_manifest = APP_ASSETS / "manifest" / "wallpapers.json"
    if src_manifest.is_file():
        dst_manifest = BANNERS_ASSETS / "manifest" / "wallpapers.json"
        dst_manifest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src_manifest, dst_manifest)
        copied += 1
    src_thumbs = APP_ASSETS / "wallpapers_thumbs"
    if src_thumbs.is_dir():
        dst_thumbs = BANNERS_ASSETS / "wallpapers_thumbs"
        if dst_thumbs.exists():
            shutil.rmtree(dst_thumbs)
        shutil.copytree(src_thumbs, dst_thumbs)
        copied += sum(1 for _ in dst_thumbs.rglob("*") if _.is_file())
    return copied


_STRING_RE = re.compile(r'(<string name="([^"]+)">)(.*?)(</string>)', re.S)


def mirror_manifest() -> None:
    """app/'s AndroidManifest, re-pointed at Banners."""
    src = (APP_MAIN / "AndroidManifest.xml").read_text(encoding="utf-8")
    out = src.replace(
        'android:authorities="${fileProviderAuthority}"',
        'android:authorities="tv.corebuilds.iconpack.banners.update"',
    ).replace(
        'android:authorities="tv.corebuilds.iconpack.update"',
        'android:authorities="tv.corebuilds.iconpack.banners.update"',
    )
    if out == src:
        raise SystemExit("mirror_manifest: FileProvider authority not found — "
                         "app/src/main/AndroidManifest.xml changed shape")
    header = ('<?xml version="1.0" encoding="utf-8"?>\n'
              '<!-- Generated by tools/build_banner_pack.py from\n'
              '     app/src/main/AndroidManifest.xml. Do not edit by hand:\n'
              '     edit the app/ copy and re-run the generator. -->\n')
    out = out.replace('<?xml version="1.0" encoding="utf-8"?>\n', header, 1)
    write(BANNERS_MAIN / "AndroidManifest.xml", out)


def mirror_strings() -> int:
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
        "         tools/build_banner_pack.py, with Banners' product identity substituted.\n"
        "         Edit the app/ copy, not this one. -->", 1)
    write(BANNERS_VAL / "strings.xml", out)
    missing = set(STRING_OVERRIDES) - set(
        m.group(2) for m in _STRING_RE.finditer(src))
    if missing:
        print(f"  \u26a0 override keys absent from app strings.xml: "
              f"{', '.join(sorted(missing))}")
    return hits["n"]


def mirror_branding() -> int:
    """Copy launcher mipmaps and Leanback banner."""
    copied = 0
    for dpi in ("xhdpi", "xxhdpi"):
        src_dir = APP_RES / f"mipmap-{dpi}"
        dst_dir = BANNERS_RES / f"mipmap-{dpi}"
        if src_dir.is_dir():
            dst_dir.mkdir(parents=True, exist_ok=True)
            for f in src_dir.glob("*.png"):
                shutil.copy2(f, dst_dir / f.name)
                copied += 1
    # Leanback banner
    cb_banner = APP_PNG / "cb_banner.png"
    if cb_banner.is_file():
        BANNERS_PNG.mkdir(parents=True, exist_ok=True)
        shutil.copy2(cb_banner, BANNERS_PNG / "cb_banner.png")
        copied += 1
    return copied


def main() -> int:
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())
    suite_json = json.loads((ROOT / "suite.json").read_text(encoding="utf-8"))
    version = suite_json["apps"]["banners"]["versionName"]

    errs = validate(icons, data.get("artwork"))
    if errs:
        print("Catalog rejected — %d problem(s):" % len(errs))
        for e in errs:
            print("  \u2717 " + e)
        return 1

    BANNERS_PNG.mkdir(parents=True, exist_ok=True)
    BANNERS_XML.mkdir(parents=True, exist_ok=True)
    BANNERS_VAL.mkdir(parents=True, exist_ok=True)
    BANNERS_ASSETS.mkdir(parents=True, exist_ok=True)

    # 1. Copy banner PNGs under BASE names
    png_copied = 0
    for i in icons:
        d = i["drawable"]
        src = APP_PNG / f"{d}_banner.png"
        if not src.is_file():
            raise SystemExit(f"Missing flagship banner art for {d}: {src}")
        dst = BANNERS_PNG / f"{d}.png"
        shutil.copy2(src, dst)
        png_copied += 1
    print(f"\u2713 {png_copied} banner PNGs copied under base names \u2192 banners/src/main/res/drawable-nodpi/")

    # 2. appfilter.xml (maps components -> base drawable names)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/build_banner_pack.py. Do not edit by hand. -->',
        '<resources>',
    ]
    comp_count = 0
    for i in icons:
        comps = []
        for c in i.get("components", []):
            comps.extend(expand(c))
        lines.append(f'    <!-- {esc(i["name"])} -->')
        for c in sorted(set(comps)):
            lines.append(f'    <item component="ComponentInfo{{{c}}}" '
                         f'drawable="{i["drawable"]}"/>')
            comp_count += 1
    lines.append('</resources>')
    appfilter_xml = "\n".join(lines) + "\n"
    write(BANNERS_XML / "appfilter.xml", appfilter_xml)
    write(BANNERS_ASSETS / "appfilter.xml", appfilter_xml)
    print(f"\u2713 appfilter.xml written ({comp_count} component mappings) "
          f"\u2192 res/xml/ + assets/")

    # 3. drawable.xml (browser grid grouped by category)
    by_cat: dict[str, list[dict]] = {}
    for i in icons:
        by_cat.setdefault(i.get("category") or "APP", []).append(i)
    order = ([c for c in CAT_ORDER if c in by_cat]
             + [c for c in by_cat if c not in CAT_ORDER])

    d = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/build_banner_pack.py. Do not edit by hand. -->',
        '<resources>',
    ]
    for cat in order:
        d.append(f'    <category title="{esc(CAT_LABEL.get(cat, cat.title()))}" />')
        for i in by_cat[cat]:
            d.append(f'    <item drawable="{i["drawable"]}" />')
    d.append('</resources>')
    drawable_xml = "\n".join(d) + "\n"
    write(BANNERS_XML / "drawable.xml", drawable_xml)
    write(BANNERS_ASSETS / "drawable.xml", drawable_xml)

    # 4. iconpack.xml
    p = ['<?xml version="1.0" encoding="utf-8"?>', '<iconpack>']
    p += [f'    <item drawable="{i["drawable"]}" />' for i in icons]
    p.append('</iconpack>')
    iconpack_xml = "\n".join(p) + "\n"
    write(BANNERS_XML / "iconpack.xml", iconpack_xml)
    write(BANNERS_ASSETS / "iconpack.xml", iconpack_xml)
    print(f"\u2713 drawable.xml + iconpack.xml written ({len(icons)} entries)")

    # 5. icon_pack.xml (values arrays for in-app browser)
    v = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Generated by tools/build_banner_pack.py. Do not edit by hand. -->',
        '<resources>',
        '    <string-array name="icon_pack">',
    ]
    v += [f'        <item>{i["drawable"]}</item>' for i in icons]
    v.append('    </string-array>')
    v.append('    <string-array name="icon_names">')
    v += [f'        <item>{esc_android(i["name"])}</item>' for i in icons]
    v.append('    </string-array>')
    v.append('    <string-array name="icon_categories">')
    v += [f'        <item>{esc_android(i.get("category") or "APP")}</item>' for i in icons]
    v.append('    </string-array>')
    v.append('    <integer-array name="icon_bespoke">')
    v += [f'        <item>{0 if is_monogram(i["glyph"]) else 1}</item>' for i in icons]
    v.append('    </integer-array>')
    v.append(f'    <integer name="icon_count">{len(icons)}</integer>')
    v.append('</resources>')
    icon_pack_val = "\n".join(v) + "\n"
    write(BANNERS_VAL / "icon_pack.xml", icon_pack_val)
    write(BANNERS_ASSETS / "icon_pack.xml", icon_pack_val)
    print(f"\u2713 icon_pack.xml written \u2192 res/values/ + assets/")

    # 6. Shared resources + strings + manifest + branding
    mirrored = mirror_shared_resources()
    overridden = mirror_strings()
    mirror_manifest()
    brand = mirror_branding()
    print(f"\u2713 mirrored {mirrored} shared resource file(s), {brand} branding files, "
          f"{overridden} string(s) overridden")

    # 7. Supported list documentation
    md = [
        "# Core Builds Banners — supported applications",
        "",
        f"`{len(icons)}` icons \u00b7 `{comp_count}` mapped components \u00b7 "
        f"pack v{version}",
        "",
        "Core Builds Banners is drawn from the same `tools/catalog.json` as the classic "
        "Core Builds Icon Pack, providing 16:9 banner card art mapped under base drawable "
        "names so Projectivy and other Android TV launchers display clean card pickers.",
        "",
        "| App | Drawable | Brand accent | Components |",
        "| --- | --- | --- | --- |",
    ]
    for i in icons:
        comps = "<br>".join(f"`{c}`" for c in i["components"])
        md.append(f"| {i['name']} | `{i['drawable']}` | `{i['color']}` | {comps} |")
    write(DOC_DIR / "BannersIconList.md", "\n".join(md) + "\n")
    print(f"\u2713 docs/BannersIconList.md written ({len(icons)} rows)")

    print(f"\nBuild complete \u2014 {len(icons)} icons, {comp_count} components. "
          f"Verified against catalog.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
