#!/usr/bin/env python3
"""Static wiring + permission-contract checks for the screens and handoffs.

Companion to test_search_focus.py: that file locks the main screen's focus
behaviour, this one locks everything around it that a layout check cannot
see - the manifest's permission and package-visibility contract, and the
wiring of the wallpapers viewer, the export flow and the launcher handoffs.

Why these checks exist, one class at a time:

  permissions        The manifest is deliberately minimal (INTERNET,
                     ACCESS_NETWORK_STATE, REQUEST_INSTALL_PACKAGES,
                     SET_WALLPAPER for the API<=28 floor, and
                     WRITE_EXTERNAL_STORAGE capped at maxSdk 28 because
                     scoped storage needs nothing newer). A future feature
                     that reaches for a gated API without declaring - or
                     without capping - repeats the worst kind of bug here:
                     silent on the devices that need it, noise on the ones
                     that don't. Both manifests (app and Pop, which compiles
                     the same Kotlin) are checked.

  package visibility Android 11 filters resolveActivity/queryIntentActivities
                     by <queries>. Every probe in the code is component-
                     explicit into a package this block must name (Monet for
                     the share handoff, Projectivy for the background apply),
                     plus the HOME intent and the apply contracts the
                     detection row lists. Drop an entry and a handoff button
                     disappears on API 30+ with no error anywhere.

  wallpapers wiring  The same bug classes the main screen already paid for:
                     a chip row with the default item animator eats its own
                     highlight, a screen without a starting-focus guard opens
                     on the decor view, and a GONE selection bar strands the
                     cursor on a view nobody can see. All three are guarded
                     in WallpapersActivity; these checks keep them guarded.

  handoff flags      FileProvider content URIs die with a
                     SecurityException in the receiver unless the intent
                     carries FLAG_GRANT_READ_URI_PERMISSION. Every share /
                     apply intent that leaves the app with one is checked.

Usage:
    python tests/test_ui_wiring.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

APP_MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
POP_MANIFEST = ROOT / "pop" / "src" / "main" / "AndroidManifest.xml"
WALLPAPERS_KT = ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds" / "iconpack" / "WallpapersActivity.kt"
MAIN_KT = ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds" / "iconpack" / "MainActivity.kt"
SETTER_KT = ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds" / "iconpack" / "WallpaperSetter.kt"
PREVIEW_KT = ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds" / "iconpack" / "WallpaperPreviewActivity.kt"
EXPORT_KT = ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds" / "iconpack" / "ExportProgressActivity.kt"
WP_LAYOUT = ROOT / "app" / "src" / "main" / "res" / "layout" / "activity_wallpapers.xml"

PERMISSIONS = [
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.SET_WALLPAPER",
]
QUERIES_NEEDLES = [
    'package android:name="com.klevico.monet"',
    'package android:name="com.spocky.projengmenu"',
    'android.intent.category.HOME',
    'action android:name="org.adw.launcher.THEMES"',
    'action android:name="com.spocky.projengmenu.APPLY_ICONPACK"',
    'action android:name="ch.deletescape.lawnchair.APPLY_ICONS"',
]


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def main() -> int:
    problems: list[str] = []

    def check(ok: bool, label: str):
        if not ok:
            problems.append(label)

    app_manifest = read(APP_MANIFEST)
    pop_manifest = read(POP_MANIFEST)
    for name, manifest in (("app", app_manifest), ("pop", pop_manifest)):
        for perm in PERMISSIONS:
            check(f'android:name="{perm}"' in manifest,
                  f"{name} manifest: missing {perm}")
        check(re.search(r'android:name="android\.permission\.WRITE_EXTERNAL_STORAGE"[^>]*'
                        r'android:maxSdkVersion="28"', manifest, re.S) is not None,
              f"{name} manifest: WRITE_EXTERNAL_STORAGE must stay capped at maxSdk 28")
        for needle in QUERIES_NEEDLES:
            check(needle in manifest, f"{name} manifest: <queries> lost {needle}")

    setter = read(SETTER_KT)
    check("MONET_PACKAGE" in setter and "MONET_SHARE_ACTIVITY" in setter,
          "WallpaperSetter: Monet share target constants missing")
    check(setter.count("FLAG_GRANT_READ_URI_PERMISSION") >= 3,
          "WallpaperSetter: every FileProvider intent (send, send-multiple, "
          "projectivy apply) must carry FLAG_GRANT_READ_URI_PERMISSION")

    wp = read(WALLPAPERS_KT)
    check(wp.count("itemAnimator = null") >= 2,
          "WallpapersActivity: both RecyclerViews must disable the item animator")
    check("currentFocus == null || currentFocus === window.decorView" in wp,
          "WallpapersActivity: starting-focus guard missing")
    check("exitSelectionMode()" in wp and "R.id.wp_export).requestFocus()" in wp,
          "WallpapersActivity: exiting selection mode must hand focus to the header export")
    check("if (adapter.selectionMode) exitSelectionMode() else finish()" in wp,
          "WallpapersActivity: back must leave selection mode before finishing")

    layout = read(WP_LAYOUT)
    for edge in ('android:nextFocusDown="@id/wp_back"',
                 'android:nextFocusUp="@id/wp_export"',
                 'android:nextFocusDown="@id/wp_selection_bar"',
                 'android:nextFocusUp="@id/wp_back"',
                 'android:nextFocusDown="@id/wp_chips"',
                 'android:nextFocusUp="@id/wp_chips"'):
        check(edge in layout, f"activity_wallpapers.xml: focus edge missing: {edge}")

    main = read(MAIN_KT)
    check("WallpapersActivity::class.java" in main and "wpEntry.setOnClickListener" in main,
          "MainActivity: wallpapers row must open WallpapersActivity")
    check("apply_button" in main, "MainActivity: apply button wiring missing")

    preview = read(PREVIEW_KT)
    check("RequestPermission()" in preview and "requiredPermission()" in preview,
          "WallpaperPreviewActivity: set path must check then request the storage permission")

    export = read(EXPORT_KT)
    check("R.id.export_cancel).setOnClickListener { finish() }" in export,
          "ExportProgressActivity: cancel must finish")
    check("retry.requestFocus()" in export,
          "ExportProgressActivity: retry must take focus when offered")

    if problems:
        print(f"ui wiring gate: {len(problems)} problem(s)")
        for p in problems:
            print("  \u2717 " + p)
        return 1
    print("ui wiring gate ok - 2 manifests, 5 permissions, 6 visibility entries, "
          "wallpapers/export/preview wiring locked")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
