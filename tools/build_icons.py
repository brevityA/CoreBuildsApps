#!/usr/bin/env python3
"""
Core Builds Icon Pack — asset pipeline.

Reads tools/catalog.json and writes, deterministically:
  assets/svg/<drawable>.svg                    master vector
  app/src/main/res/drawable-nodpi/<d>.webp     512px transparent lossless WebP
  app/src/main/res/values/aliases.xml          dup-name -> canonical art
  app/src/main/res/raw/keep.xml                shrinker keep rules (generated)
  glyphs/src/main/res/xml/appfilter.xml        component -> square glyph
                                               (Core Builds Glyphs; + assets)
  glyphs/src/main/res/xml/drawable.xml         square icon browser grid
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
from glyphs import (GLYPHS, apply_secondary, family_body, family_glyph_for,  # noqa: E402
                    is_monogram, monoline, render_svg, secondary_color,
                    secondary_errors)
from icon_style import CORE_MONOLINE, core_monoline_errors, display_accent  # noqa: E402
from typeface import MIN_LOCKUP_CAP, lockup_cap  # noqa: E402
from brandmarks import load_source  # noqa: E402
from drawable_art import (ART_EXT, BRANDING_PNGS, alias_identical,
                          write_aliases_file)  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"
SVG_DIR = ROOT / "assets" / "svg"
PNG_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
XML_DIR = ROOT / "app" / "src" / "main" / "res" / "xml"
# Core Builds Glyphs, the square companion (glyphs/). Its appfilter and
# browser are written here; the icon pack's own, which map to banners, are
# derived from them by tools/build_banners_pack.py.
GLYPH_MAIN = ROOT / "glyphs" / "src" / "main"
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
        errors.extend(f"{n}: {e}" for e in secondary_errors(i))
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
                if i.get("secondary") and not secondary_errors(i):
                    body = apply_secondary(body, accent, i["secondary"])
                errors.extend(f"{n}: {e}" for e in core_monoline_errors(
                    body, accent, gradient=bool(i.get("gradient")), ink=i.get("ink"),
                    secondary=secondary_color(i)))
        for comp in i.get("components", []):
            if "/" not in comp:
                errors.append(f"{n}: component '{comp}' missing '/activity'")
            if comp in seen_c:
                errors.append(f"{n}: component '{comp}' duplicates {seen_c[comp]}")
            seen_c[comp] = n
    # Two icons that draw the same shape in the same display colour render the
    # same PNG — v1.8.14 counted what that costs, so it is a gate now, not a
    # phase. Every icon is checked, not only monograms: three file managers
    # once shared one folder. A monogram's letter is replaced by its mark, so
    # its shape is the shell family plus the mark (app_E "ET" and app_N "ET"
    # are one picture). Declared brand variants (same `brand`) are one
    # identity by rule and are supposed to be identical; an icon with no
    # brand is its own identity, so two brandless icons never excuse each
    # other (the old `None != None` check let 22 such groups through).
    seen_render = {}
    for i in icons:
        mark = i.get("mark") or ""
        glyph = i.get("glyph", "")
        shape = (glyph.rpartition("_")[0] + "_*"
                 if mark and family_glyph_for(glyph) is not None else glyph)
        render_key = (shape, mark, i.get("mark_style") or "",
                      display_accent(i.get("color", "#000000"),
                                     monochrome=i.get("color_note") == "monochrome"))
        identity = i.get("brand") or i["name"]
        prev = seen_render.get(render_key)
        if prev and prev[1] != identity:
            errors.append(f"{i['name']}: renders the same picture as {prev[0]} "
                          "(shape, mark and colour) — one of them needs a "
                          "different accent or mark")
        seen_render.setdefault(render_key, (i["name"], identity))
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
                         style=i.get("mark_style"), secondary=i.get("secondary")))
    print(f"\u2713 SVG masters written ({len(icons)}/{len(icons)}) \u2192 assets/svg/ "
          f"({wordmarked} adaptive wordmark monograms)")

    # 2. Glyph art: lossless WebP, presence applied in memory before the
    # encode. resvg cannot emit WebP, so the PNG exists only as bytes.
    art_written = 0
    rendered = False
    try:
        import io
        from PIL import Image
        from svg_renderer import svg2png
        from presence import apply_presence
        for i in icons:
            dest = PNG_DIR / f"{i['drawable']}{ART_EXT}"
            png = svg2png(
                url=str(SVG_DIR / f"{i['drawable']}.svg"),
                output_width=PNG_SIZE, output_height=PNG_SIZE,
                background_color=None)
            art = apply_presence(Image.open(io.BytesIO(png)))
            art.save(dest, "WEBP", lossless=True)
            art_written += 1
        rendered = True
        print(f"\u2713 WebP {PNG_SIZE}px transparent written "
              f"({art_written}/{len(icons)}) \u2192 res/drawable-nodpi/")
    except (ImportError, OSError):
        # Never continue with a partial asset set: an appfilter that names a
        # drawable the APK does not carry turns into letter tiles on the
        # launcher (seen in the wild: a missing tegrazone3 read as a
        # "T" card on a Tegra Zone install). If the art already exists on
        # disk from a previous run this is a no-op and we may continue.
        missing = [i["drawable"] for i in icons
                   if not (PNG_DIR / f"{i['drawable']}{ART_EXT}").exists()]
        if missing:
            shown = ", ".join(missing[:5])
            if len(missing) > 5:
                shown += ", \u2026"
            raise SystemExit(
                f"\u274c no SVG rasterizer AND {len(missing)} catalog art files are "
                f"missing ({shown}). Refusing to write an appfilter that "
                "references absent drawables. "
                "Run: pip install -r tools/requirements.txt")
        print("\u26a0 no SVG rasterizer \u2014 reusing existing art "
              "(all present). Run: pip install -r tools/requirements.txt "
              "to regenerate.")

    # 2b. Identical renders ship once; later names become aliases. Stale PNGs
    # from the pre-WebP tree are removed unless they are branding. Skipped
    # when the rasterizer is absent — the fallback reuses disk as-is.
    if rendered:
        glyph_files = [PNG_DIR / f"{i['drawable']}{ART_EXT}" for i in icons]
        glyph_files = [f for f in glyph_files if f.exists()]
        aliases = alias_identical(glyph_files)
        write_aliases_file(VAL_DIR / "aliases.xml", aliases,
                           "tools/build_icons.py")
        for stale in PNG_DIR.glob("*.png"):
            if stale.name not in BRANDING_PNGS:
                stale.unlink()
        print(f"\u2713 aliases.xml written ({len(aliases)} dup names \u2192 "
              f"canonical art); stale PNGs removed")

    # 3. appfilter.xml — what makes icons auto-assign
    #
    # 3a. Fallback furniture — iconback/iconmask/iconupon/scale. Apps the
    # catalog does not cover used to arrive as naked stock icons: launcher
    # launchers composite an unthemed app's own icon over an arbitrary
    # pack-supplied back, clipped by the pack's mask and topped by its upon,
    # so supply exactly those. The backs are the grid's card (#151923,
    # hairline-white stroke) nudged ten ways along the pack palette — near
    # night, distinguishable from a neighbour unthemed app, never competing
    # with the actual glyph rows; one shape card, no second zoom level at
    # 0.70 scale, matching the 352/512 ink box our own glyphs sit in.
    # Without furniture the "one container" claim dies the moment an app
    # outside the 961 lands on the home row.
    BACKS = {
        # one accent at 10% into the card fill (#151923): the palette's
        # blues, greens and violets, night-side; graphite-only variants bookend
        "night": "#151923", "blue": "#132039", "violet": "#211B39",
        "cyan": "#132C39", "green": "#1B3022", "ember": "#2C2322",
        "orchid": "#2B2032", "marine": "#132F35", "slate": "#21252F",
        "graphite": "#10141D",
    }
    def furniture_svg(inner):
        return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 '
                f'{PNG_SIZE} {PNG_SIZE}" width="{PNG_SIZE}" '
                f'height="{PNG_SIZE}">{inner}</svg>')

    try:
        import io
        from PIL import Image
        from svg_renderer import svg2png

        def emit(stem, inner):
            raw = svg2png(
                bytestring=furniture_svg(inner).encode(),
                output_width=PNG_SIZE, output_height=PNG_SIZE,
                background_color=None)
            Image.open(io.BytesIO(raw)).save(
                PNG_DIR / f"{stem}{ART_EXT}", "WEBP", lossless=True)

        for name, hexv in BACKS.items():
            emit(f"cb_back_{name}",
                 f'<rect width="{PNG_SIZE}" height="{PNG_SIZE}" rx="48" '
                 f'fill="{hexv}"/>')
        emit("cb_mask",
             f'<rect width="{PNG_SIZE}" height="{PNG_SIZE}" rx="48" '
             f'fill="#FFFFFF"/>')
        emit("cb_upon",
             f'<rect x="4" y="4" width="{PNG_SIZE - 8}" '
             f'height="{PNG_SIZE - 8}" rx="46" fill="none" '
             f'stroke="rgba(255,255,255,0.07)" stroke-width="8"/>')
        print(f"\u2713 fallback furniture written ({len(BACKS)} backs + mask + "
              f"upon) \u2192 res/drawable-nodpi/")
    except (ImportError, OSError, TypeError):
        missing = [f"cb_back_{n}{ART_EXT}" for n in BACKS
                   if not (PNG_DIR / f"cb_back_{n}{ART_EXT}").exists()]
        missing += [f for f in (f"cb_mask{ART_EXT}", f"cb_upon{ART_EXT}")
                    if not (PNG_DIR / f).exists()]
        if missing:
            raise SystemExit(f"fallback furniture art missing and the "
                             f"rasteriser is unavailable: {', '.join(missing[:4])}")
        print("\u2713 fallback furniture already on disk (rasteriser absent)")

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
    # Fallback furniture first — launchers that support the composite
    # schema read these before any <item>, and an unthemed app lands on one
    # of the card backs instead of arriving naked.
    backs = " ".join(f'img{n + 1}="cb_back_{name}"'
                     for n, name in enumerate(BACKS))
    lines += [f'    <iconback {backs}/>',
              '    <iconmask img1="cb_mask"/>',
              '    <iconupon img1="cb_upon"/>',
              '    <scale factor="0.70"/>']
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
                             f'drawable="{i["drawable"]}"/>')
                emitted_count += 1
    lines.append('</resources>')
    appfilter_text = "\n".join(lines) + "\n"
    # This is the square mapping, and it ships in Core Builds Glyphs. The
    # icon pack itself maps the same components to their banners - the
    # default style since 1.9.5 - and tools/build_banners_pack.py derives
    # that appfilter from this one, so the two cannot disagree.
    write(GLYPH_MAIN / "res" / "xml" / "appfilter.xml", appfilter_text)
    # The ADW convention permits res/xml, res/raw, or assets. Modern launchers
    # prefer res/xml, while several older picker/request implementations only
    # inspect assets. Generate identical files so mappings cannot drift.
    write(GLYPH_MAIN / "assets" / "appfilter.xml", appfilter_text)
    print(f"\u2713 appfilter.xml written \u2014 {comp_count} catalog components "
          f"\u2192 {emitted_count} entries (both name forms) "
          f"\u2192 {len(icons)} drawables (res/xml + assets)")

    # 4. drawable.xml — launcher icon picker, grouped by catalog category
    # so Projectivy's browser can jump a section instead of scrolling 500
    # untitled tiles. Glyphs only: this is Core Builds Glyphs' browser, the
    # same art its appfilter maps. The icon pack lists the same sections as
    # banners (tools/build_banners_pack.py), so each pack's icon browser
    # offers its own style and nothing else.
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
        d.append(f'    <category title="Square \u00b7 {esc(label)}" />')
        for i in by_cat[cat]:
            d.append(f'    <item drawable="{i["drawable"]}" />')
    d.append('</resources>')
    drawable_text = "\n".join(d) + "\n"
    write(GLYPH_MAIN / "res" / "xml" / "drawable.xml", drawable_text)
    write(GLYPH_MAIN / "assets" / "drawable.xml", drawable_text)

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

    # 6b. res/raw/keep.xml — resource shrinking would strip every drawable
    # that is only ever resolved by name (appfilter strings, getIdentifier),
    # which is all of them. The keep set is generated from the same catalog
    # as the art, so the two cannot drift apart. It lives in raw/, not
    # values/: aapt rejects <keep> as a values resource declaration. And the
    # rules are the tools:keep ATTRIBUTE of the root <resources> element:
    # the shrinker reads only the root's tools:keep / tools:discard /
    # tools:shrinkMode, so a child <keep tools:keep=...> element is silently
    # ignored - the build passes and the release APK loses the icons.
    k = ['<?xml version="1.0" encoding="utf-8"?>',
         '<!-- Generated by tools/build_icons.py. Do not edit by hand. -->']
    keep_names = ([i["drawable"] for i in icons]
                  + [f"{i['drawable']}_banner" for i in icons]
                  + [f"cb_back_{n}" for n in BACKS]
                  + ["cb_mask", "cb_upon", "cb_banner"])
    k.append('<resources xmlns:tools="http://schemas.android.com/tools"\n'
             '    tools:keep="' + ",".join(f"@drawable/{n}" for n in keep_names) + '" />')
    raw_dir = ROOT / "app" / "src" / "main" / "res" / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)
    write(raw_dir / "keep.xml", "\n".join(k) + "\n")
    print(f"\u2713 keep.xml written ({len(keep_names)} drawables pinned)")

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
        accent = display_accent(i["color"], monochrome=mono)
        inner = monoline(family_body(i["glyph"], accent,
                                     i.get("mark"), i.get("mark_style")))
        if secondary_color(i):
            inner = apply_secondary(inner, accent, i["secondary"])
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
          f"{art_written} WebP. Verified by re-read of the catalog.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
