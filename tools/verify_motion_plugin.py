#!/usr/bin/env python3
"""Verify the Core Motion plugin still satisfies Projectivy's discovery contract.

Projectivy finds a wallpaper provider by running

    queryIntentServices(Intent("tv.projectivy.plugin.WALLPAPER_PROVIDER"), GET_META_DATA)

and then binding over the IWallpaperProviderService AIDL. Two classes of silent
breakage make a plugin undetectable, and neither shows up as a build failure:

  1. a manifest key drifts (renamed service, meta-data moved to <application>,
     uuid reset to CHANGE_ME, leanback hard-required again);
  2. the vendored copy of Spocky's API drifts from upstream, changing the AIDL
     interface descriptor so the bind succeeds but the transaction doesn't.

This checks both, offline, with no Android SDK. See docs/PROJECTIVY_DETECTION.md.

Usage: python tools/verify_motion_plugin.py
"""

from __future__ import annotations

import hashlib
import re
import sys
import uuid as uuidlib
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLUGIN = ROOT / "motion-plugin" / "app"
MANIFEST = PLUGIN / "src" / "main" / "AndroidManifest.xml"
STRINGS = PLUGIN / "src" / "main" / "res" / "values" / "strings.xml"
GRADLE = PLUGIN / "build.gradle.kts"
API_DIR = PLUGIN / "src" / "main"

ANDROID = "http://schemas.android.com/apk/res/android"
DISCOVERY_ACTION = "tv.projectivy.plugin.WALLPAPER_PROVIDER"
API_PACKAGE = "tv.projectivy.plugin.wallpaperprovider.api"

# Meta-data Projectivy reads off the <service>. apiVersion/uuid/name are what
# gate the plugin appearing in the list at all.
REQUIRED_META = {
    "apiVersion",
    "uuid",
    "name",
    "settingsActivity",
    "itemsCacheDurationMillis",
    "updateMode",
}

# sha256 of the API files as vendored from
# spocky/projectivy-plugin-wallpaper-provider @ main. Regenerate deliberately
# (and only when upstream actually changes) with --update-hashes.
UPSTREAM_SHA256 = {
    "aidl/tv/projectivy/plugin/wallpaperprovider/api/Event.aidl":
        "7408317c4f26dddc2c73eab4f5d9002d4d873b02dd26ab0140fbddfcfc945d91",
    "aidl/tv/projectivy/plugin/wallpaperprovider/api/Wallpaper.aidl":
        "58349bad403e525eeda0783b551a1673decf78aabdd9c1be61b1666acc07337d",
    "aidl/tv/projectivy/plugin/wallpaperprovider/api/IWallpaperProviderService.aidl":
        "f3344411bebe848bc0d7aefcc497ca46d0f3216ac049c6fe3378fdf180bfce65",
    "java/tv/projectivy/plugin/wallpaperprovider/api/Event.kt":
        "78c2cae1c5376a4dec9ee611717ffbe7419c3adb5180221fce50c6d846997589",
    "java/tv/projectivy/plugin/wallpaperprovider/api/Wallpaper.kt":
        "e9e250330874dcf13ba2810843362e0155fe773e8246e59f2adcf09b7698a2e6",
    "java/tv/projectivy/plugin/wallpaperprovider/api/WallpaperProviderContract.kt":
        "272d7faea65ce3b889c0158a59bd4b334965d844ade142af8ab69b3a213f0f03",
}

errors: list[str] = []
warnings: list[str] = []
oks: list[str] = []


def err(msg: str) -> None:
    errors.append(msg)


def warn(msg: str) -> None:
    warnings.append(msg)


def ok(msg: str) -> None:
    oks.append(msg)


def a(elem: ET.Element, name: str) -> str | None:
    return elem.get(f"{{{ANDROID}}}{name}")


def check_manifest() -> None:
    if not MANIFEST.exists():
        err(f"manifest not found: {MANIFEST.relative_to(ROOT)}")
        return

    tree = ET.parse(MANIFEST)
    root = tree.getroot()
    app = root.find("application")
    if app is None:
        err("manifest has no <application>")
        return

    # --- the provider service -------------------------------------------------
    services = app.findall("service")
    provider = None
    for svc in services:
        for f in svc.findall("intent-filter"):
            if any(a(act, "name") == DISCOVERY_ACTION for act in f.findall("action")):
                provider = svc
                break
        if provider is not None:
            break

    if provider is None:
        err(
            f"no <service> declares <action android:name=\"{DISCOVERY_ACTION}\"/>. "
            "Projectivy discovers plugins solely through this action — without it "
            "the plugin is invisible."
        )
        return
    ok(f"discovery intent action present on {a(provider, 'name')}")

    if a(provider, "exported") != "true":
        err("provider service is not android:exported=\"true\" — Projectivy cannot bind to it")
    else:
        ok("provider service is exported")

    if a(provider, "enabled") == "false":
        err("provider service is android:enabled=\"false\"")

    # --- service meta-data ----------------------------------------------------
    meta = {a(m, "name"): a(m, "value") for m in provider.findall("meta-data")}
    missing = REQUIRED_META - set(meta)
    if missing:
        err(f"provider service is missing meta-data: {', '.join(sorted(missing))}")
    else:
        ok(f"all {len(REQUIRED_META)} required meta-data keys present on the service")

    # A classic mistake: meta-data on <application> instead of <service>.
    app_meta = {a(m, "name") for m in app.findall("meta-data")}
    stray = app_meta & REQUIRED_META
    if stray:
        err(
            f"meta-data {', '.join(sorted(stray))} is on <application>; "
            "Projectivy only reads meta-data declared on the <service>"
        )

    if meta.get("apiVersion") != "1":
        err(f"apiVersion must be \"1\", found {meta.get('apiVersion')!r}")
    else:
        ok("apiVersion = 1")

    # --- uuid resolves to a real UUID v4 --------------------------------------
    raw_uuid = meta.get("uuid", "")
    resolved = raw_uuid
    if raw_uuid.startswith("@string/"):
        resolved = string_res(raw_uuid.removeprefix("@string/")) or ""
    if not resolved or resolved == "CHANGE_ME":
        err("plugin uuid is unset or still the template's CHANGE_ME")
    else:
        try:
            parsed = uuidlib.UUID(resolved)
            if parsed.version != 4:
                warn(f"plugin uuid is UUID v{parsed.version}; the template asks for v4")
            ok(f"plugin uuid is a valid UUID v{parsed.version} ({resolved})")
        except ValueError:
            err(f"plugin uuid is not a valid UUID: {resolved!r}")

    # --- settingsActivity actually exists -------------------------------------
    settings = meta.get("settingsActivity", "")
    activities = {a(act, "name") for act in app.findall("activity")}
    if settings and settings not in activities:
        err(
            f"settingsActivity {settings!r} is not declared as an <activity> "
            f"(declared: {', '.join(sorted(x for x in activities if x))})"
        )
    elif settings:
        ok(f"settingsActivity {settings} is declared")

    # --- updateMode is a sane bitmask ----------------------------------------
    mode = meta.get("updateMode", "")
    if mode.isdigit():
        m = int(mode)
        if not (1 <= m <= 31):
            err(f"updateMode {m} is outside the valid 1..31 bitmask range")
        else:
            ok(f"updateMode = {m}")
    elif mode and not mode.startswith("@"):
        err(f"updateMode {mode!r} is not an integer")

    # --- leanback must not hard-gate installation -----------------------------
    for feat in root.findall("uses-feature"):
        if a(feat, "name") == "android.software.leanback":
            if a(feat, "required") == "true":
                err(
                    "android.software.leanback is required=\"true\" — this blocks "
                    "installation on Google TV / Fire TV builds that don't report "
                    "the flag, which looks identical to 'Projectivy can't see it'. "
                    "Set required=\"false\"."
                )
            else:
                ok("android.software.leanback is required=false (installs everywhere)")

    if not any(
        a(p, "name") == "android.permission.INTERNET"
        for p in root.findall("uses-permission")
    ):
        warn("no INTERNET permission — the remote feed will always come back empty")


def string_res(name: str) -> str | None:
    if not STRINGS.exists():
        return None
    for s in ET.parse(STRINGS).getroot().findall("string"):
        if s.get("name") == name:
            return (s.text or "").strip()
    return None


def check_gradle() -> None:
    if not GRADLE.exists():
        err("motion-plugin/app/build.gradle.kts not found")
        return
    text = GRADLE.read_text()

    if not re.search(r"aidl\s*=\s*true", text):
        err(
            "buildFeatures { aidl = true } is missing — AGP 8+ does not compile "
            "AIDL by default, so IWallpaperProviderService.Stub never gets generated"
        )
    else:
        ok("AIDL compilation is enabled")

    if "parcelize" not in text:
        err("the kotlin-parcelize plugin is not applied — @Parcelize Wallpaper/Event won't marshal")
    else:
        ok("kotlin-parcelize is applied")

    # R8 will happily strip an exported service that nothing in-app references.
    minify = re.search(r"isMinifyEnabled\s*=\s*(\w+)", text)
    if minify and minify.group(1) == "true":
        proguard = PLUGIN / "proguard-rules.pro"
        rules = proguard.read_text() if proguard.exists() else ""
        if API_PACKAGE not in rules:
            err(
                "minify is on but proguard-rules.pro does not keep "
                f"{API_PACKAGE}.** — R8 will rename the AIDL stub and break the bind"
            )

    m = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if m:
        ok(f"plugin versionName = {m.group(1)}")


def check_api_parity() -> None:
    """The vendored API must stay byte-identical to upstream.

    The AIDL interface descriptor is derived from the package + interface name,
    and the parcelable field order is part of the wire format. Any edit here
    produces a plugin that binds and then fails every transaction.
    """
    for rel, expected in UPSTREAM_SHA256.items():
        path = API_DIR / rel
        if not path.exists():
            err(f"vendored API file missing: {rel}")
            continue
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if expected and digest != expected:
            err(
                f"{rel} has drifted from Spocky's upstream API "
                f"(sha256 {digest[:12]}… != {expected[:12]}…). "
                "This API is frozen — reverting is the fix."
            )
        elif expected:
            ok(f"{rel} matches upstream")

    # Package relocation is the single most common way people break discovery.
    for rel in UPSTREAM_SHA256:
        path = API_DIR / rel
        if not path.exists():
            continue
        head = path.read_text(errors="replace")[:400]
        if f"package {API_PACKAGE}" not in head:
            err(
                f"{rel} is not in package {API_PACKAGE}. The API package name is "
                "part of the AIDL descriptor and must never be relocated."
            )


def update_hashes() -> int:
    lines = []
    for rel in UPSTREAM_SHA256:
        path = API_DIR / rel
        digest = hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None
        lines.append(f'    "{rel}": {digest!r},')
    print("UPSTREAM_SHA256 = {")
    print("\n".join(lines))
    print("}")
    return 0


def main() -> int:
    if "--update-hashes" in sys.argv:
        return update_hashes()

    check_manifest()
    check_gradle()
    check_api_parity()

    for line in oks:
        print(f"  ok   {line}")
    for line in warnings:
        print(f"  warn {line}")
    for line in errors:
        print(f"  FAIL {line}")

    print()
    print(f"{len(oks)} passed, {len(warnings)} warnings, {len(errors)} failures")
    if errors:
        print("\nCore Motion would NOT be reliably detected by Projectivy.")
        return 1
    print("Core Motion satisfies Projectivy's discovery contract.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
