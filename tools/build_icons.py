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
from glyphs import GLYPHS, family_body, family_glyph_for, is_monogram, monoline, render_svg  # noqa: E402
from icon_style import CORE_MONOLINE, core_monoline_errors, display_accent  # noqa: E402
from typeface import MIN_LOCKUP_CAP, lockup_cap  # noqa: E402
from brandmarks import load_source  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"
SVG_DIR = ROOT / "assets" / "svg"
PNG_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
XML_DIR = ROOT / "app" / "src" / "main" / "res" / "xml"
VAL_DIR = ROOT / "app" / "src" / "main" / "res" / "values"
DOC_DIR = ROOT / "docs"

PNG_SIZE = 512
DRAWABLE_RE = re.compile(r"^[a-z][a-z0-9_]*$")

# Brand-informed mark treatments that have shipped. A style joins this set
# the way a glyph joins the registry: one researched cue at a time.
MARK_STYLES = frozenset({"lower"})


def esc(s):
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
             .replace('"', "&quot;"))


def esc_android(s):
    """esc() plus Android-specific apostrophe escaping for values XML."""
    return esc(s).replace("'", "\\'")


def validate(icons, artwork=None):
    """Fail loudly and by name. No unnamed errors (Brand Guide §08)."""
    errors, seen_d, seen_c, seen_brand = [], {}, {}, {}
    for i in icons:
        d, n = i.get("drawable", ""), i.get("name", "<unnamed>")
        if not DRAWABLE_RE.match(d):
            errors.append(f"{n}: drawable '{d}' must match [a-z][a-z0-9_]*")
        if d in seen_d:
            errors.append(f"{n}: drawable '{d}' already used by {seen_d[d]}")
        seen_d[d] = n
        if i.get("glyph") not in GLYPHS:
            errors.append(f"{n}: unknown glyph '{i.get('glyph')}'")
        if i.get("banner_glyph") and i["banner_glyph"] not in GLYPHS:
            errors.append(f"{n}: unknown banner glyph '{i.get('banner_glyph')}'")
        if not re.match(r"^#[0-9A-Fa-f]{6}$", i.get("color", "")):
            errors.append(f"{n}: color '{i.get('color')}' must be #RRGGBB")
        grad = i.get("gradient")
        if grad is not None and not (
                isinstance(grad, list) and len(grad) == 2
                and all(re.fullmatch(r"#[0-9A-Fa-f]{6}", g) for g in grad)):
            errors.append(f"{n}: gradient must be exactly two #RRGGBB stops")
        if i.get("ink") is not None and not re.fullmatch(r"#[0-9A-Fa-f]{6}", i["ink"]):
            errors.append(f"{n}: ink must be #RRGGBB")
        mark = i.get("mark")
        if mark is not None:
            if not isinstance(mark, str) or not re.fullmatch(r"[A-Z0-9]{2,4}", mark):
                errors.append(f"{n}: mark '{mark}' must be 2-4 uppercase A-Z0-9 chars")
            elif family_glyph_for(i.get("glyph", "")) is None:
                errors.append(f"{n}: mark '{mark}' belongs on a category monogram "
                              f"(<family>_<L>), not glyph '{i.get('glyph')}'")
            else:
                _, cap_h, _, max_w = family_glyph_for(i["glyph"])
                shown = mark.lower() if i.get("mark_style") == "lower" else mark
                cap = lockup_cap(shown, cap_h, max_w)
                if cap < MIN_LOCKUP_CAP:
                    errors.append(f"{n}: mark '{shown}' sets at {cap:.0f}px in the "
                                  f"'{i['glyph'].rpartition('_')[0]}' shell — under the "
                                  f"{MIN_LOCKUP_CAP}px counter floor, it closes at a 48px tile")
        mstyle = i.get("mark_style")
        if mstyle is not None:
            if mark is None:
                errors.append(f"{n}: mark_style '{mstyle}' needs a mark to style")
            elif mstyle not in MARK_STYLES:
                errors.append(f"{n}: unknown mark_style '{mstyle}' "
                              f"(vocabulary: {', '.join(sorted(MARK_STYLES))}) — a style "
                              "ships only when a researched logotype cue needs it")
            elif not i.get("mark_style_source"):
                errors.append(f"{n}: mark_style '{mstyle}' carries no "
                              "mark_style_source — where was the cue seen?")
        if not i.get("components"):
            errors.append(f"{n}: no components — icon would never auto-assign")
        if brand := i.get("brand"):
            identity = (i.get("glyph"), i.get("color", "").upper())
            if brand in seen_brand and seen_brand[brand] != identity:
                errors.append(f"{n}: brand {brand!r} has inconsistent glyph/colour")
            seen_brand[brand] = identity
        style = i.get("style")
        if style is not None and style != CORE_MONOLINE:
            errors.append(f"{n}: unknown Classic style {style!r}")
        if style == CORE_MONOLINE:
            if i.get("banner_style", "standard") != "standard":
                errors.append(f"{n}: Core monoline apps must use the standard Outfit/category/rail banner")
            if i.get("glyph") in GLYPHS and re.fullmatch(r"#[0-9A-Fa-f]{6}", i.get("color", "")):
                accent = display_accent(i["color"],
                                       monochrome=i.get("color_note") == "monochrome")
                body = monoline(GLYPHS[i["glyph"]](accent))
                errors.extend(f"{n}: {e}" for e in core_monoline_errors(
                    body, accent, gradient=bool(i.get("gradient")), ink=i.get("ink")))
        for comp in i.get("components", []):
            if "/" not in comp:
                errors.append(f"{n}: component '{comp}' missing '/activity'")
            if comp in seen_c:
                errors.append(f"{n}: component '{comp}' duplicates {seen_c[comp]}")
            seen_c[comp] = n
    # Two icons sharing shell, mark and display colour render the same PNG —
    # v1.8.14 counted what that costs, so it is a gate now, not a phase.
    # Declared brand variants (same `brand`) are one identity by rule and
    # are supposed to be identical; the gate fires across brands only.
    seen_render = {}
    for i in icons:
        mark = i.get("mark")
        if not mark or family_glyph_for(i.get("glyph", "")) is None:
            continue
        render_key = (i["glyph"], mark, i.get("mark_style") or "",
                      display_accent(i.get("color", "#000000"),
                                     monochrome=i.get("color_note") == "monochrome"))
        prev = seen_render.get(render_key)
        # Identical twins across brands are the trap; declared variants of one
        # brand must stay identical by the glyph/accent rule above.
        if prev and prev[1] != i.get("brand"):
            errors.append(f"{i['name']}: shares shell, mark and colour with "
                          f"{prev[0]} — one of them needs a different accent")
        seen_render[render_key] = (i["name"], i.get("brand"))
    for glyph, spec in (artwork or {}).items():
        if spec.get("usage") != "reference-only":
            errors.append(f"{glyph}: brand artwork is reference-only, not a rendering override")
        matching = [i for i in icons if i.get("glyph") == glyph]
        if not matching or any(i.get("style") != CORE_MONOLINE for i in matching):
            errors.append(f"{glyph}: referenced brands must use the Core monoline contract")
        try:
            load_source(spec)
        except (ValueError, KeyError, OSError) as exc:
            errors.append(f"{glyph}: invalid artwork reference: {exc}")
    return errors


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def main():
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = sorted(data["icons"], key=lambda i: i["name"].lower())

    errs = validate(icons, data.get("artwork"))
    if errs:
        print("Catalog rejected — %d problem(s):" % len(errs))
        for e in errs:
            print("  \u2717 " + e)
        return 1

    # 1. master SVGs
    wordmarked = sum(1 for i in icons
                     if i.get("mark") and family_glyph_for(i["glyph"]))
    for i in icons:
        mono = i.get("color_note") == "monochrome"
        write(SVG_DIR / f"{i['drawable']}.svg",
              render_svg(i["glyph"], i["color"], monochrome=mono,
                         gradient=i.get("gradient"), mark=i.get("mark"),
                         style=i.get("mark_style")))
    print(f"\u2713 SVG masters written ({len(icons)}/{len(icons)}) \u2192 assets/svg/ "
          f"({wordmarked} adaptive wordmark monograms)")

    # 2. PNGs
    png_written = 0
    try:
        from svg_renderer import svg2png
        from presence import apply_presence_file
        for i in icons:
            dest = PNG_DIR / f"{i['drawable']}.png"
            svg2png(
                url=str(SVG_DIR / f"{i['drawable']}.svg"),
                write_to=str(dest),
                output_width=PNG_SIZE, output_height=PNG_SIZE,
                background_color=None)
            apply_presence_file(dest)
            png_written += 1
        print(f"\u2713 PNG {PNG_SIZE}px transparent written "
              f"({png_written}/{len(icons)}) \u2192 res/drawable-nodpi/")
    except (ImportError, OSError):
        # Never continue with a partial asset set: an appfilter that names a
        # drawable the APK does not carry turns into letter tiles on the
        # launcher (seen in the wild: a missing tegrazone3.png read as a
        # "T" card on a Tegra Zone install). If the PNGs already exist on
        # disk from a previous run this is a no-op and we may continue.
        missing = [i["drawable"] for i in icons
                   if not (PNG_DIR / f"{i['drawable']}.png").exists()]
        if missing:
            shown = ", ".join(missing[:5])
            if len(missing) > 5:
                shown += ", \u2026"
            raise SystemExit(
                f"\u274c no SVG rasterizer AND {len(missing)} catalog PNGs are "
                f"missing ({shown}). Refusing to write an appfilter that "
                "references absent drawables. "
                "Run: pip install -r tools/requirements.txt")
        print("\u26a0 no SVG rasterizer \u2014 reusing existing PNGs "
              "(all present). Run: pip install -r tools/requirements.txt "
              "to regenerate.")

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
    appfilter_text = "\n".join(lines) + "\n"
    write(XML_DIR / "appfilter.xml", appfilter_text)
    # The ADW convention permits res/xml, res/raw, or assets. Modern launchers
    # prefer res/xml, while several older picker/request implementations only
    # inspect assets. Generate identical files so mappings cannot drift.
    write(ROOT / "app" / "src" / "main" / "assets" / "appfilter.xml",
          appfilter_text)
    print(f"\u2713 appfilter.xml written \u2014 {comp_count} catalog components "
          f"\u2192 {emitted_count} entries (both name forms) "
          f"\u2192 {len(icons)} drawables (res/xml + assets)")

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
    drawable_text = "\n".join(d) + "\n"
    write(XML_DIR / "drawable.xml", drawable_text)
    write(ROOT / "app" / "src" / "main" / "assets" / "drawable.xml",
          drawable_text)

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
    # Which icons carry a drawn brandmark rather than a letter in a container.
    # 526 of 940 are monograms, so the grid badges the 414 that are not and
    # offers a chip to filter to them; without this the two are indistinguishable
    # until you recognise the mark. The classification comes from
    # glyphs.MONOGRAM_GLYPHS rather than a name pattern.
    v.append('    <integer-array name="icon_bespoke">')
    for i in icons:
        v.append(f'        <item>{0 if is_monogram(i["glyph"]) else 1}</item>')
    v.append('    </integer-array>')
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
          "Source accents are retained in the catalog. **On dark** is the shared square/banner colour; low-contrast accents use light ink rather than disappearing. Brand references guide the Core Builds monoline constructions; vendor silhouettes/wordmarks are not rendered directly. [Style and research](research/icon-fidelity-and-demand-2026-09.md).",
          "",
          "| App | Drawable | Source accent | On dark | Components |",
          "| --- | --- | --- | --- | --- |"]
    for i in icons:
        comps = "<br>".join(f"`{c}`" for c in i["components"])
        label = f"[{i['name']}]({i['download_url']})" if i.get("download_url") else i["name"]
        mono = i.get("color_note") == "monochrome"
        md.append(f"| {label} | `{i['drawable']}` | `{i['color']}` | `{display_accent(i['color'], monochrome=mono)}` | {comps} |")
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
        mono = i.get("color_note") == "monochrome"
        inner = monoline(family_body(i["glyph"],
                                     display_accent(i["color"], monochrome=mono),
                                     i.get("mark"), i.get("mark_style")))
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
        from svg_renderer import svg2png
        svg2png(url=str(DOC_DIR / "preview.svg"),
                         write_to=str(DOC_DIR / "preview.png"),
                         output_width=1200, background_color="#0d1117")
        print("\u2713 docs/preview.png written (README contact sheet)")
    except (ImportError, OSError):
        pass

    print(f"\nBuild complete \u2014 {len(icons)} icons, {comp_count} components, "
          f"{png_written} PNGs. Verified by re-read of the catalog.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
