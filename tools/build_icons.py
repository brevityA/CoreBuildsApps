#!/usr/bin/env python3
"""
Core Builds Icon Pack — asset pipeline.

Reads tools/catalog.json and writes, deterministically:
  assets/svg/<drawable>.svg                    master vector
  app/src/main/res/drawable-nodpi/<d>.png      512px transparent PNG
  app/src/main/res/xml/appfilter.xml           component -> drawable mapping
  app/src/main/res/xml/drawable.xml            icon-pack browser grid
  app/src/main/res/xml/iconpack.xml            Projectivy/legacy pack list
  app/src/main/res/values/icon_pack.xml        pack metadata array
  docs/IconPackList.md                         human-readable supported list
  docs/preview.svg                             contact sheet

Receipts voice: prints exactly what was written, counted.
"""
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glyphs import GLYPHS, monoline, render_svg  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"
SVG_DIR = ROOT / "assets" / "svg"
PNG_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
XML_DIR = ROOT / "app" / "src" / "main" / "res" / "xml"
VAL_DIR = ROOT / "app" / "src" / "main" / "res" / "values"
DOC_DIR = ROOT / "docs"

PNG_SIZE = 512
DRAWABLE_RE = re.compile(r"^[a-z][a-z0-9_]*$")


def esc(s):
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
             .replace('"', "&quot;"))


def esc_android(s):
    """esc() plus Android-specific apostrophe escaping for values XML."""
    return esc(s).replace("'", "\\'")


def validate(icons):
    """Fail loudly and by name. No unnamed errors (Brand Guide §08)."""
    errors, seen_d, seen_c = [], {}, {}
    for i in icons:
        d, n = i.get("drawable", ""), i.get("name", "<unnamed>")
        if not DRAWABLE_RE.match(d):
            errors.append(f"{n}: drawable '{d}' must match [a-z][a-z0-9_]*")
        if d in seen_d:
            errors.append(f"{n}: drawable '{d}' already used by {seen_d[d]}")
        seen_d[d] = n
        if i.get("glyph") not in GLYPHS:
            errors.append(f"{n}: unknown glyph '{i.get('glyph')}'")
        if not re.match(r"^#[0-9A-Fa-f]{6}$", i.get("color", "")):
            errors.append(f"{n}: color '{i.get('color')}' must be #RRGGBB")
        if not i.get("components"):
            errors.append(f"{n}: no components — icon would never auto-assign")
        for comp in i.get("components", []):
            if "/" not in comp:
                errors.append(f"{n}: component '{comp}' missing '/activity'")
            if comp in seen_c:
                errors.append(f"{n}: component '{comp}' duplicates {seen_c[comp]}")
            seen_c[comp] = n
    return errors


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def main():
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())

    errs = validate(icons)
    if errs:
        print("Catalog rejected — %d problem(s):" % len(errs))
        for e in errs:
            print("  \u2717 " + e)
        return 1

    # 1. master SVGs
    for i in icons:
        write(SVG_DIR / f"{i['drawable']}.svg", render_svg(i["glyph"], i["color"]))
    print(f"\u2713 SVG masters written ({len(icons)}/{len(icons)}) \u2192 assets/svg/")

    # 2. PNGs
    png_written = 0
    try:
        import cairosvg
        for i in icons:
            cairosvg.svg2png(
                url=str(SVG_DIR / f"{i['drawable']}.svg"),
                write_to=str(PNG_DIR / f"{i['drawable']}.png"),
                output_width=PNG_SIZE, output_height=PNG_SIZE,
                background_color=None)
            png_written += 1
        print(f"\u2713 PNG {PNG_SIZE}px transparent written "
              f"({png_written}/{len(icons)}) \u2192 res/drawable-nodpi/")
    except ImportError:
        print("\u26a0 cairosvg not installed \u2014 PNGs skipped. "
              "Run: pip install cairosvg  (APK build needs them)")

    # 3. appfilter.xml — what makes icons auto-assign
    #
    # Auto-assignment maps to the 16:9 BANNER drawable, not the square icon.
    # Projectivy cards are 16:9 by default and the reference pack ships 1002
    # of its 1002 icons at 320x180, so a banner is what a card actually wants.
    # The square set still ships and stays selectable per app from the
    # launcher's icon browser via drawable.xml.
    #
    # Emit BOTH the shorthand (pkg/.Activity) and fully-qualified
    # (pkg/pkg.Activity) forms of every component. Launchers match on the
    # literal string inside ComponentInfo{...}; they do not all expand a
    # leading dot. The reference Projectivy pack ships 955 of 956 entries
    # fully-qualified for exactly this reason. Emitting both costs a few KB of
    # XML and removes an entire class of silent non-application.
    def expand(component):
        pkg, _, act = component.partition("/")
        if not act:
            return [component]
        if act.startswith("."):
            short, full = component, f"{pkg}/{pkg}{act}"
        elif act.startswith(pkg + "."):
            short, full = f"{pkg}/{act[len(pkg):]}", component
        else:
            # Activity lives in a different namespace than the package
            # (e.g. au.com.tenplay/com.tenplay.MainActivity) — no shorthand
            # form exists, so the literal is the only valid entry.
            return [component]
        return [full, short]

    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- Generated by tools/build_icons.py. Do not edit by hand. -->',
             '<resources>']
    comp_count = 0
    emitted_count = 0
    seen_emitted = set()
    for i in icons:
        lines.append(f'    <!-- {esc(i["name"])} -->')
        for comp in i["components"]:
            comp_count += 1
            for variant in expand(comp):
                if variant in seen_emitted:
                    continue
                seen_emitted.add(variant)
                lines.append(f'    <item component="ComponentInfo{{{esc(variant)}}}" '
                             f'drawable="{i["drawable"]}_banner"/>')
                emitted_count += 1
    lines.append('</resources>')
    write(XML_DIR / "appfilter.xml", "\n".join(lines) + "\n")
    print(f"\u2713 appfilter.xml written \u2014 {comp_count} catalog components "
          f"\u2192 {emitted_count} entries (both name forms) "
          f"\u2192 {len(icons)} drawables")

    # 4. drawable.xml — launcher icon picker, grouped by catalog category
    # so Projectivy's browser can jump a section instead of scrolling 500
    # untitled tiles. Banners head each group; squares follow as opt-in.
    CAT_LABEL = {
        "STREAM": "Streaming", "MEDIA": "Media centres", "VOD": "On demand",
        "LIVE": "Live TV", "PLAYER": "Players", "MUSIC": "Music",
        "SPORT": "Sport", "GAMING": "Gaming", "DEBRID": "Debrid",
        "FILES": "Files", "TOOL": "Tools", "STORE": "Stores",
        "LAUNCHER": "Launchers", "VPN": "VPN", "BROWSER": "Browsers",
        "REMOTE": "Remote", "SYSTEM": "System", "TRACK": "Tracking",
        "CORE": "Core Builds", "VIDEO": "Video", "APP": "Apps",
    }
    preferred = ["CORE", "STREAM", "MEDIA", "VOD", "LIVE", "PLAYER", "VIDEO",
                 "MUSIC", "SPORT", "GAMING", "DEBRID", "FILES", "TOOL",
                 "STORE", "LAUNCHER", "VPN", "BROWSER", "REMOTE", "SYSTEM",
                 "TRACK", "APP"]
    by_cat = {}
    for i in icons:
        by_cat.setdefault(i.get("category") or "APP", []).append(i)
    cat_order = [c for c in preferred if c in by_cat] + [
        c for c in by_cat if c not in preferred]

    d = ['<?xml version="1.0" encoding="utf-8"?>',
         '<!-- Generated by tools/build_icons.py. Do not edit by hand. -->',
         '<resources>']
    for cat in cat_order:
        label = CAT_LABEL.get(cat, cat.title())
        d.append(f'    <category title="Banners \u00b7 {esc(label)}" />')
        for i in by_cat[cat]:
            d.append(f'    <item drawable="{i["drawable"]}_banner" />')
    for cat in cat_order:
        label = CAT_LABEL.get(cat, cat.title())
        d.append(f'    <category title="Square \u00b7 {esc(label)}" />')
        for i in by_cat[cat]:
            d.append(f'    <item drawable="{i["drawable"]}" />')
    d.append('</resources>')
    write(XML_DIR / "drawable.xml", "\n".join(d) + "\n")

    # 5. iconpack.xml — legacy/alt launcher discovery
    p = ['<?xml version="1.0" encoding="utf-8"?>', '<iconpack>']
    for i in icons:
        p.append(f'    <item drawable="{i["drawable"]}" />')
    p.append('</iconpack>')
    write(XML_DIR / "iconpack.xml", "\n".join(p) + "\n")
    print("\u2713 drawable.xml + iconpack.xml written (browser grid, "
          f"{len(icons)} entries)")

    # 6. values arrays — drawables + display names + categories for the
    # in-app browser. Names used to live only in the catalog, so the grid
    # rendered 500 untitled tiles and you could not tell LocalSend from
    # Netflix without counting.
    v = ['<?xml version="1.0" encoding="utf-8"?>',
         '<!-- Generated by tools/build_icons.py. Do not edit by hand. -->',
         '<resources>', '    <string-array name="icon_pack">']
    for i in icons:
        v.append(f'        <item>{i["drawable"]}</item>')
    v.append('    </string-array>')
    v.append('    <string-array name="icon_names">')
    for i in icons:
        v.append(f'        <item>{esc_android(i["name"])}</item>')
    v.append('    </string-array>')
    v.append('    <string-array name="icon_categories">')
    for i in icons:
        v.append(f'        <item>{esc_android(i.get("category") or "APP")}</item>')
    v.append('    </string-array>')
    v.append(f'    <integer name="icon_count">{len(icons)}</integer>')
    v.append('</resources>')
    write(VAL_DIR / "icon_pack.xml", "\n".join(v) + "\n")

    # 7. supported list
    md = ["# Supported applications",
          "",
          f"`{len(icons)}` icons \u00b7 `{comp_count}` mapped components \u00b7 "
          f"pack v{data['meta']['version']}",
          "",
          "Every app below auto-assigns in Projectivy. If one doesn't, the app "
          "ships a different launcher activity on your device \u2014 open an issue "
          "with the component name and it gets added.",
          "",
          "| App | Drawable | Accent | Components |",
          "| --- | --- | --- | --- |"]
    for i in icons:
        comps = "<br>".join(f"`{c}`" for c in i["components"])
        md.append(f"| {i['name']} | `{i['drawable']}` | `{i['color']}` | {comps} |")
    write(DOC_DIR / "IconPackList.md", "\n".join(md) + "\n")
    print(f"\u2713 docs/IconPackList.md written ({len(icons)} rows)")

    # 8. contact sheet on night chrome
    cols, cell = 8, 150
    rows = (len(icons) + cols - 1) // cols
    w, h = cols * cell, rows * cell + 78
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
         f'viewBox="0 0 {w} {h}">',
         f'<rect width="{w}" height="{h}" fill="#0d1117"/>',
         f'<text x="24" y="44" fill="#e6edf3" font-family="Georgia,serif" '
         f'font-size="27">Core Builds Icon Pack</text>',
         f'<text x="24" y="66" fill="#8b949e" '
         f'font-family="ui-monospace,monospace" font-size="12.5">'
         f'{len(icons)} icons \u00b7 {comp_count} components \u00b7 '
         f'v{data["meta"]["version"]}</text>']
    for n, i in enumerate(icons):
        cx, cy = (n % cols) * cell, 78 + (n // cols) * cell
        inner = monoline(GLYPHS[i["glyph"]](i["color"]))
        s.append(f'<rect x="{cx + 9}" y="{cy + 5}" width="{cell - 18}" '
                 f'height="{cell - 34}" rx="16" fill="#151923" '
                 f'stroke="rgba(255,255,255,.06)"/>')
        s.append(f'<g transform="translate({cx + 33},{cy + 20}) '
                 f'scale({84 / 512})">{inner}</g>')
        label = esc(i["name"])[:17]
        s.append(f'<text x="{cx + cell / 2}" y="{cy + cell - 12}" '
                 f'fill="#8b949e" font-family="ui-monospace,monospace" '
                 f'font-size="10.5" text-anchor="middle">{label}</text>')
    s.append('</svg>')
    write(DOC_DIR / "preview.svg", "\n".join(s) + "\n")
    print("\u2713 docs/preview.svg written (contact sheet)")

    # 9. raster contact sheet for the README (GitHub won't render SVG text well)
    try:
        import cairosvg
        cairosvg.svg2png(url=str(DOC_DIR / "preview.svg"),
                         write_to=str(DOC_DIR / "preview.png"),
                         output_width=1200, background_color="#0d1117")
        print("\u2713 docs/preview.png written (README contact sheet)")
    except ImportError:
        pass

    print(f"\nBuild complete \u2014 {len(icons)} icons, {comp_count} components, "
          f"{png_written} PNGs. Verified by re-read of the catalog.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
