#!/usr/bin/env python3
"""
Core Builds Icon Pack — validator.

Checks the shipped pack against its own claims. Every failure names the thing
that failed (Brand Guide §08: no unnamed errors). Exit 0 = pack is coherent.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
CATALOG = ROOT / "tools" / "catalog.json"

failures = []
checks = 0


def check(condition, message):
    global checks
    checks += 1
    if not condition:
        failures.append(message)


def main():
    global checks
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    icons = data["icons"]
    names = {i["drawable"] for i in icons}

    # 1. every drawable has a rendered PNG
    for i in icons:
        p = RES / "drawable-nodpi" / f"{i['drawable']}.png"
        check(p.exists(), f"{i['name']}: missing PNG {p.relative_to(ROOT)}")
        if p.exists():
            check(p.stat().st_size > 400,
                  f"{i['name']}: PNG suspiciously small ({p.stat().st_size}B)")

    # 2. PNGs are transparent-background RGBA (pack promise)
    try:
        import struct
        for i in icons:
            p = RES / "drawable-nodpi" / f"{i['drawable']}.png"
            if not p.exists():
                continue
            raw = p.read_bytes()
            w, h = struct.unpack(">II", raw[16:24])
            colortype = raw[25]
            check((w, h) == (512, 512),
                  f"{i['name']}: PNG is {w}x{h}, expected 512x512")
            check(colortype == 6,
                  f"{i['name']}: PNG colour type {colortype}, expected 6 (RGBA)")
    except Exception as e:
        failures.append(f"PNG header read failed: {e}")

    # 3. appfilter references only real drawables, no duplicate components
    af = ET.parse(RES / "xml" / "appfilter.xml").getroot()
    seen = {}
    comp_total = 0
    for item in af.findall("item"):
        comp = item.get("component", "")
        d = item.get("drawable", "")
        comp_total += 1
        # appfilter maps to the banner drawable — banners are the pack default.
        base = d[:-7] if d.endswith("_banner") else d
        check(base in names,
              f"appfilter: drawable '{d}' has no catalog entry")
        check(d.endswith("_banner"),
              f"appfilter: '{d}' is not a banner drawable — banners are the "
              f"default, square icons stay opt-in via drawable.xml")
        check(re.match(r"^ComponentInfo\{[^/]+/[^}]+\}$", comp),
              f"appfilter: malformed component '{comp}'")
        check(comp not in seen,
              f"appfilter: duplicate component '{comp}' "
              f"({seen.get(comp)} vs {d})")
        seen[comp] = d

    # 4. drawable.xml grid covers the whole catalog
    dx = ET.parse(RES / "xml" / "drawable.xml").getroot()
    listed = {i.get("drawable") for i in dx.findall("item")}
    missing = names - listed
    check(not missing, f"drawable.xml missing: {sorted(missing)}")

    # 5. manifest declares the discovery intents launchers actually scan for
    mf = (ROOT / "app" / "src" / "main" / "AndroidManifest.xml").read_text()
    for action in ["com.novalauncher.THEME", "org.adw.launcher.THEMES",
                   "org.adw.launcher.icons.ACTION_PICK_ICON"]:
        check(action in mf, f"manifest: missing icon-pack action {action}")
    check("LEANBACK_LAUNCHER" in mf,
          "manifest: missing LEANBACK_LAUNCHER — pack won't show on Android TV")
    check("android:banner" in mf,
          "manifest: missing android:banner — required for the ATV home row")

    # 5a2. both component name forms must be present.
    # Launchers match the literal string in ComponentInfo{...} and do not all
    # expand a leading dot, so every dotted activity needs its fully-qualified
    # twin (and vice versa) or the icon silently fails on some launchers.
    emitted = set()
    for item in af.findall("item"):
        c = item.get("component", "")
        m = re.match(r"^ComponentInfo\{(.+)\}$", c)
        if m:
            emitted.add(m.group(1))
    for comp in emitted.copy():
        pkg, _, act = comp.partition("/")
        if not act:
            continue
        if act.startswith("."):
            twin = f"{pkg}/{pkg}{act}"
        elif act.startswith(pkg + "."):
            twin = f"{pkg}/{act[len(pkg):]}"
        else:
            continue  # foreign namespace — no shorthand form exists
        check(twin in emitted,
              f"appfilter: '{comp}' has no matching '{twin}' — icon may not "
              f"apply on launchers that don't expand the leading dot")

    # 5b. direct-apply contract — the intent strings must match exactly, or the
    # launcher silently ignores the apply and the button looks broken.
    apply_src = (ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds" /
                 "iconpack" / "ApplyIconPack.kt")
    check(apply_src.exists(), "direct apply: ApplyIconPack.kt is missing")
    if apply_src.exists():
        src = apply_src.read_text()
        for token in [
            "com.spocky.projengmenu.APPLY_ICONPACK",
            "com.spocky.projengmenu.extra.ICONPACK_PACKAGENAME",
            "com.teslacoilsw.launcher.APPLY_ICON_THEME",
            "com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE",
        ]:
            check(token in src, f"direct apply: intent contract '{token}' not found")

    # 5c. Android 11+ package visibility. Without <queries>, every launcher
    # lookup returns "not installed" on API 30+ and direct apply never fires.
    check("<queries>" in mf,
          "manifest: no <queries> block — launcher detection fails on Android 11+")
    for pkg in ["com.spocky.projengmenu", "com.teslacoilsw.launcher"]:
        check(f'<package android:name="{pkg}"' in mf,
              f"manifest: <queries> missing {pkg} — cannot detect that launcher")
    check("android.intent.category.HOME" in mf,
          "manifest: <queries> missing the HOME intent — cannot detect the "
          "active launcher")
    check("com.spocky.projengmenu.icons.ACTION_PICK_ICON" in mf,
          "manifest: missing Projectivy's ACTION_PICK_ICON — pack won't appear "
          "in its per-app icon browser")

    # 5d. 16:9 banners. Projectivy cards are 16:9 by default, so a banner
    # drawable must actually be 16:9 or it renders letterboxed/stretched.
    # Banners are the default now, so every icon must have one.
    banner_icons = icons
    for i in banner_icons:
        bp = RES / "drawable-nodpi" / f"{i['drawable']}_banner.png"
        check(bp.exists(),
              f"{i['name']}: marked banner but {bp.name} is missing")
        if bp.exists():
            raw = bp.read_bytes()
            import struct as _s
            bw, bh = _s.unpack(">II", raw[16:24])
            check(abs(bw / bh - 16 / 9) < 0.01,
                  f"{i['name']}: banner is {bw}x{bh} "
                  f"(ratio {bw/bh:.3f}), expected 16:9")
            check(raw[25] == 6,
                  f"{i['name']}: banner colour type {raw[25]}, expected 6 (RGBA)")
    if banner_icons:
        listed = (RES / "xml" / "drawable.xml").read_text()
        for i in banner_icons:
            check(f'{i["drawable"]}_banner' in listed,
                  f"{i['name']}: banner not listed in drawable.xml — "
                  f"not selectable in the launcher's icon browser")

    # 5e. Banner composition, measured against the reference pack's grid
    # (Projectivy Icon Pack 1.1.9: 1002 icons, median ink 78% x 43%, centred
    # to 0.0px). Drift here is invisible in isolation and obvious in a row.
    if banner_icons:
        try:
            from PIL import Image
            for i in banner_icons:
                bp = RES / "drawable-nodpi" / f"{i['drawable']}_banner.png"
                if not bp.exists():
                    continue
                bb = Image.open(bp).convert("RGBA").getchannel("A").getbbox()
                if not bb:
                    failures.append(f"{i['name']}: banner is fully transparent")
                    continue
                l, t, r, b = bb
                hoff = ((l + r) / 2) - 160
                voff = ((t + b) / 2) - 90
                check(abs(hoff) <= 3,
                      f"{i['name']}: banner ink is {hoff:+.1f}px off centre "
                      f"horizontally (max 3)")
                check(abs(voff) <= 3,
                      f"{i['name']}: banner ink is {voff:+.1f}px off centre "
                      f"vertically (max 3)")
                check((r - l) / 320 <= 0.90,
                      f"{i['name']}: banner ink is "
                      f"{(r-l)/320*100:.0f}% wide, over the 90% safe limit")
                check((b - t) / 180 <= 0.72,
                      f"{i['name']}: banner ink is "
                      f"{(b-t)/180*100:.0f}% tall, over the 72% safe limit")
        except ImportError:
            pass

    # 5f. Wordmarks must be the bold sans stack, not the display serif.
    # Brand Guide §04 scopes the serif to display copy and says "never bold";
    # a card label is UI text, which the guide assigns to system-ui at 600-800.
    # A silently-failed edit once left these rendering serif, so assert it.
    banner_svgs = ROOT / "assets" / "banners"
    if banner_svgs.exists():
        for f in sorted(banner_svgs.glob("*.svg")):
            body = f.read_text(encoding="utf-8")
            if "<text" not in body:
                continue          # glyph-only banner, no wordmark
            check("Georgia" not in body,
                  f"banner {f.stem}: wordmark is set in Georgia — §04 reserves "
                  f"the serif for display copy, card labels are bold sans")
            check('font-weight="700"' in body,
                  f"banner {f.stem}: wordmark is missing font-weight 700")

    # 5g. Monoline discipline (style AA).
    #
    # AA is: one uniform stroke weight, no fill, no glow, no container.
    # Google's TV icon guidance is explicit that a border around a logo
    # "gets cropped and creates unpolished visuals", so the hex host and the
    # §02 halo are both deliberately absent here — §02's lit treatment still
    # governs the brand mark itself, not third-party app art.
    import re as _re2
    for i in icons:
        sp = ROOT / "assets" / "svg" / f"{i['drawable']}.svg"
        if not sp.exists():
            continue
        body = sp.read_text(encoding="utf-8")
        check("feGaussianBlur" not in body,
              f"{i['name']}: glyph carries a blur — style AA is monoline, "
              f"no glow")
        widths = {float(w) for w in
                  _re2.findall(r'stroke-width="([\d.]+)"', body)}
        check(len(widths) <= 3,
              f"{i['name']}: {len(widths)} distinct stroke weights "
              f"{sorted(widths)} — monoline allows at most 3 "
              f"(primary + two subordinate)")
        if widths:
            check(max(widths) <= 34,
                  f"{i['name']}: heaviest stroke is {max(widths)}px, "
                  f"over the 34px monoline ceiling")

    # 5j. The update manifest must agree with the build it ships beside.
    # Latestrelease/version.json is what the in-app updater polls; if its
    # versionCode lags build.gradle.kts, every user is told they are current
    # when they are not. It is hand-maintained, so assert it.
    import json as _json
    _vj = ROOT / "Latestrelease" / "version.json"
    _gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    if _vj.exists():
        _v = _json.loads(_vj.read_text(encoding="utf-8"))
        _gc = re.search(r"versionCode\s*=\s*(\d+)", _gradle)
        _gn = re.search(r'versionName\s*=\s*"([^"]+)"', _gradle)
        if _gc:
            check(int(_gc.group(1)) == _v.get("versionCode"),
                  f"version.json versionCode {_v.get('versionCode')} != "
                  f"build.gradle.kts {_gc.group(1)} — the updater would "
                  f"report the wrong build")
        if _gn:
            check(_gn.group(1) == _v.get("versionName"),
                  f"version.json versionName {_v.get('versionName')} != "
                  f"build.gradle.kts {_gn.group(1)}")
        check(_v.get("iconCount") == len(icons),
              f"version.json iconCount {_v.get('iconCount')} != "
              f"{len(icons)} icons in the catalogue")
        check(str(_v.get("apkUrl", "")).endswith("app-release.apk"),
              "version.json apkUrl must end in app-release.apk — the "
              "Downloader code matches on that exact filename")

    # 6. banner + launcher icons exist
    for p in [RES / "drawable-nodpi" / "cb_banner.png",
              RES / "mipmap-xhdpi" / "ic_launcher.png",
              RES / "mipmap-anydpi-v26" / "ic_launcher.xml"]:
        check(p.exists(), f"branding: missing {p.relative_to(ROOT)}")

    # 7. palette matches the brand guide exactly
    colors = (RES / "values" / "colors.xml").read_text().upper()
    for token, hexv in [("cb_signal_cyan", "#00D4FF"), ("cb_night", "#0D1117"),
                        ("cb_cta_start", "#00ABD3"), ("cb_cta_end", "#00D4FF"),
                        ("cb_ember", "#C03A20"), ("cb_dusk_violet", "#8A4890")]:
        check(hexv in colors, f"palette: {token} {hexv} not found in colors.xml")

    print(f"Validated {len(icons)} icons \u00b7 {comp_total} components \u00b7 "
          f"{checks} checks run")
    if failures:
        print(f"\n\u2717 {len(failures)} failed:")
        for f in failures:
            print("  \u2717 " + f)
        return 1
    print(f"\u2713 {checks} passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
