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

  sideload rows      The settings LAUNCHER and HELP rows, the FAQ screen
                     and the update bar's what's-new card each have one
                     silent failure mode: a row with no reader behind it,
                     a screen never registered in a manifest, a view the
                     parser never fills. These checks pin all three, plus
                     the Pop copy of every id and string the shared Kotlin
                     reaches for - Pop compiles the same sources against
                     its own resources, so an id missing there breaks only
                     the Pop build, quietly, after the app module passed.
                     The auditor's block reads the <queries> span itself -
                     the app's own intent-filter declares LEANBACK_LAUNCHER,
                     so a whole-manifest grep would pass on the wrong
                     occurrence while the scan came up empty on the box -
                     and pins the generated deep-link resource in both
                     packs plus the vendored encoder's notice entry.

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

    # Sideload round: the launcher tools, the FAQ door and the what's-new
    # card. Same bug classes as everything above - a row with no reader, a
    # screen with no registration, a view toggled by a field the parser
    # never reads - each of which is silent until someone presses it on a
    # TV that cannot show a stack trace.
    settings_kt = read(ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds"
                       / "iconpack" / "SettingsActivity.kt")
    settings_layout = read(ROOT / "app" / "src" / "main" / "res" / "layout"
                           / "activity_settings.xml")
    pop_settings_layout = read(ROOT / "pop" / "src" / "main" / "res" / "layout"
                               / "activity_settings.xml")
    faq_kt = read(ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds"
                  / "iconpack" / "FaqActivity.kt")
    faq_layout = read(ROOT / "app" / "src" / "main" / "res" / "layout"
                      / "activity_faq.xml")
    checker = read(ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds"
                   / "iconpack" / "UpdateChecker.kt")
    for rid in ("set_refresh_row", "set_appinfo_row", "set_faq_row"):
        check(f'android:id="@+id/{rid}"' in settings_layout,
              f"activity_settings.xml: missing {rid}")
        check(f'android:id="@+id/{rid}"' in pop_settings_layout,
              f"pop activity_settings.xml: missing {rid} (shared Kotlin compiles both)")
        check(f"R.id.{rid})" in settings_kt, f"SettingsActivity: {rid} not wired")
    check("ApplyIconPack.detectInstalled(this)" in settings_kt,
          "SettingsActivity: launcher rows must act on the detected launcher, not an assumed one")
    check("ApplyIconPack.apply(this, launcher)" in settings_kt,
          "SettingsActivity: refresh row must re-fire the apply contract through ApplyIconPack")
    check("ACTION_APPLICATION_DETAILS_SETTINGS" in settings_kt,
          "SettingsActivity: app-info row must open the system details page")
    check("FaqActivity::class.java" in settings_kt,
          "SettingsActivity: help row must open FaqActivity")
    check("faq_back).setOnClickListener { finish() }" in faq_kt,
          "FaqActivity: back must finish")
    check(faq_layout.count('android:focusable="true"') == 1,
          "activity_faq.xml: only the back button may be focusable - read-only "
          "cards that take focus are a D-pad trap")
    for name, manifest in (("app", app_manifest), ("pop", pop_manifest)):
        check('android:name=".FaqActivity"' in manifest,
              f"{name} manifest: FaqActivity not registered")
    check('optJSONArray("highlights")' in checker and "val highlights: List<String>" in checker,
          "UpdateChecker: release highlights must parse as an optional manifest field")
    check("R.id.update_highlights" in main and "update.highlights.isEmpty()" in main,
          "MainActivity: highlights view must stay gone unless the manifest carries them")
    check("chip_count_fmt" in main,
          "MainActivity: category chips must carry counts tallied from the catalog arrays")

    # On-device auditor: the visibility contract it scans under, the screen
    # it scans on, and the generated deep link it encodes. The queries check
    # reads the <queries> block itself rather than grepping the whole
    # manifest, because the app's own intent-filter declares
    # LEANBACK_LAUNCHER too - a grep would pass on the wrong occurrence and
    # the scan would come up empty on API 30+ with nothing logged anywhere.
    auditor = read(ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds"
                   / "iconpack" / "AuditorActivity.kt")
    for name, manifest in (("app", app_manifest), ("pop", pop_manifest)):
        queries = manifest.split("<queries>", 1)[1].split("</queries>", 1)[0]
        for category in ("android.intent.category.LEANBACK_LAUNCHER",
                         "android.intent.category.LAUNCHER"):
            check(category in queries,
                  f"{name} manifest: auditor visibility lost {category} in <queries>")
        check('android:name=".AuditorActivity"' in manifest,
              f"{name} manifest: AuditorActivity not registered")
    for res in (ROOT / "app" / "src" / "main" / "res" / "values" / "issue_prefill.xml",
                ROOT / "pop" / "src" / "main" / "res" / "values" / "issue_prefill.xml"):
        check(res.is_file(), f"{res.parent.parent.name}: generated issue_prefill.xml missing")
        if res.is_file():
            text = read(res)
            check('name="audit_issue_url_fmt"' in text
                  and "%1$s" in text and "%2$s" in text and "%3$s" in text,
                  f"{res}: audit_issue_url_fmt must carry the three positional args")
    check("if (qrPanel.visibility == View.VISIBLE) showList() else finish()" in auditor,
          "AuditorActivity: back must leave the QR panel before the screen")
    check("R.string.audit_issue_url_fmt" in auditor and "URLEncoder.encode" in auditor,
          "AuditorActivity: deep link must come from the generated fmt, URL-encoded")
    check('assets.open("appfilter.xml")' in auditor,
          "AuditorActivity: the diff target must be the bundled appfilter asset")
    check("queryIntentActivities" in auditor and "itemAnimator = null" in auditor,
          "AuditorActivity: scan via queryIntentActivities, list without item animator")
    check("R.id.set_audit_row)" in settings_kt,
          "SettingsActivity: audit row not wired")
    for layout in (settings_layout, pop_settings_layout):
        check('android:id="@+id/set_audit_row"' in layout,
              "activity_settings.xml: missing set_audit_row")
    for module in ("app", "pop", "pixel-neon"):
        base = ROOT / module if module != "pixel-neon" else ROOT / "pixel-neon" / "app"
        for lay in ("activity_auditor.xml", "item_audit.xml"):
            check((base / "src" / "main" / "res" / "layout" / lay).is_file(),
                  f"{module}: auditor layout {lay} missing")
    for java in ("QrCode.java", "QrSegment.java", "BitBuffer.java",
               "DataTooLongException.java"):
        check((ROOT / "app" / "src" / "main" / "java" / "io" / "nayuki"
               / "qrcodegen" / java).is_file(),
              f"vendored QR encoder missing {java}")
    notices = read(ROOT / "THIRD_PARTY_NOTICES.md")
    check("nayuki" in notices.lower(),
          "THIRD_PARTY_NOTICES.md must name the vendored QR encoder")

    # Inspector, suite hub and the bumper skips. Same failure classes as
    # everything else in this file: a button with no reader, a screen with no
    # manifest entry, a visibility probe that silently returns nothing.
    inspector = read(ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds"
                     / "iconpack" / "InspectorActivity.kt")
    exporter = read(ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds"
                    / "iconpack" / "IconExporter.kt")
    suite = read(ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds"
                 / "iconpack" / "SuiteActivity.kt")
    check("InspectorActivity::class.java" in main,
          "MainActivity: a tile press outside pick mode must open the inspector")
    check('assets.open("appfilter.xml")' in inspector,
          "InspectorActivity: mapped components must come from the bundled appfilter")
    check("IconExporter.export" in inspector and "requestStoragePermission" in inspector,
          "InspectorActivity: export must gate on the storage permission, then export")
    check("getLaunchIntentForPackage" in inspector and "isInstalled" in inspector,
          "InspectorActivity: launch must probe install state before starting")
    check("Pictures/CoreBuilds/Icons" in exporter and "IS_PENDING" in exporter,
          "IconExporter: icons land in their own Pictures subfolder, pending-safe")
    check("KEYCODE_CHANNEL_DOWN" in main and "grid.hasFocus()" in main,
          "MainActivity: bumper skips must exist and only fire from the grid")
    check("scrollToPositionWithOffset" in main,
          "MainActivity: letter jumps must land without animating 900 tiles")
    check("suite_hub_names" in suite and "getLaunchIntentForPackage" in suite,
          "SuiteActivity: rows come from the generated hub resource and launch")
    check("R.id.set_suite_row)" in settings_kt,
          "SettingsActivity: suite row not wired")
    for layout in (settings_layout, pop_settings_layout):
        check('android:id="@+id/set_suite_row"' in layout,
              "activity_settings.xml: missing set_suite_row")
    for name, manifest in (("app", app_manifest), ("pop", pop_manifest)):
        queries = manifest.split("<queries>", 1)[1].split("</queries>", 1)[0]
        for pkg in ("dev.corebuilds.shift", "dev.corebuilds.line",
                    "dev.corebuilds.doctor", "tv.corebuilds.motion",
                    "tv.corebuilds.iconpack.pop", "tv.corebuilds.pixelneon"):
            check(f'package android:name="{pkg}"' in queries,
                  f"{name} manifest: suite hub visibility lost {pkg}")
        for activity in (".InspectorActivity", ".SuiteActivity"):
            check(f'android:name="{activity}"' in manifest,
                  f"{name} manifest: {activity} not registered")
    for res in (ROOT / "app" / "src" / "main" / "res" / "values" / "suite_hub.xml",
                ROOT / "pop" / "src" / "main" / "res" / "values" / "suite_hub.xml"):
        check(res.is_file() and "suite_hub_pkgs" in read(res),
              f"{res}: generated suite hub resource missing")

    if problems:
        print(f"ui wiring gate: {len(problems)} problem(s)")
        for p in problems:
            print("  \u2717 " + p)
        return 1
    print("ui wiring gate ok - 2 manifests, 5 permissions, auditor visibility, "
          "wallpapers/export/preview wiring, launcher tools + FAQ + what's-new "
          "+ QR auditor locked")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
