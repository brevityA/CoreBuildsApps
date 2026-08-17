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
        check(d in names, f"appfilter: drawable '{d}' has no catalog entry")
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

    # 8. unverified components are real components of their own icon
    # A stray entry here would silently claim verification for a mapping that
    # doesn't exist, which inverts the point of the field.
    unver_total = 0
    for i in icons:
        unver = i.get("unverified", [])
        check(isinstance(unver, list),
              f"{i['name']}: 'unverified' must be a list")
        for comp in (unver if isinstance(unver, list) else []):
            check(comp in i["components"],
                  f"{i['name']}: unverified '{comp}' is not one of its components")
        unver_total += len(unver) if isinstance(unver, list) else 0
    check(unver_total <= comp_total,
          f"unverified total {unver_total} exceeds component total {comp_total}")

    confirmed = comp_total - unver_total
    print(f"Validated {len(icons)} icons \u00b7 {comp_total} components "
          f"({confirmed} device-confirmed, {unver_total} best-known) \u00b7 "
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
