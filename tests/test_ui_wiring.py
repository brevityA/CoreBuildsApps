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

  format specifiers  getString(id, args) runs the string through
                     java.util.Formatter, whose only literal percent is
                     %%; a bare %5B parses as width 5 plus a boolean
                     conversion and throws before any URL exists, which
                     is how every auditor row press killed the screen in
                     1.9.0. Every <string> in every values*/*.xml of all
                     three packs must be grammar the Formatter can honour
                     (formatted="false" is the documented exemption), and
                     the auditor's deep link must additionally decode
                     back, after the %% collapse the runtime performs, to
                     the prefilled issue shape with all three positional
                     fields and no %% left in what the screen builds.

Usage:
    python tests/test_ui_wiring.py
"""
from __future__ import annotations

import re
import sys
import os
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

APP_MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
POP_MANIFEST = ROOT / "pop" / "src" / "main" / "AndroidManifest.xml"
BANNERS_MANIFEST = ROOT / "banners" / "src" / "main" / "AndroidManifest.xml"
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


# The wallpapers screen's focus chain, as built: (owner id, direction) -> the
# id the cursor must land on. Rail first, then the pane, then the crossings.
WP_FOCUS_CHAIN = {
    # Rail, top to bottom.
    ("wp_back", "Down"): "wp_selection_bar",
    ("wp_selection_bar", "Up"): "wp_back",
    ("wp_selection_bar", "Down"): "wp_chips",
    # The selection bar's own buttons share its edges, so the bar's row never
    # traps the cursor when it is the visible stop.
    ("wp_select_all", "Up"): "wp_back",
    ("wp_select_all", "Down"): "wp_chips",
    ("wp_clear", "Up"): "wp_back",
    ("wp_clear", "Down"): "wp_chips",
    ("wp_export_selected", "Up"): "wp_back",
    ("wp_export_selected", "Down"): "wp_chips",
    # Series chips: a vertical list on the rail's side, so UP/DOWN walk the
    # rail and RIGHT hands the cursor to the grid.
    ("wp_chips", "Up"): "wp_selection_bar",
    ("wp_chips", "Down"): "wp_export",
    ("wp_chips", "Right"): "wp_grid",
    # The export pill sits under the paragraph that says what it does.
    ("wp_export", "Up"): "wp_chips",
    # Grid: UP leaves the pane for the rail's top stop, DOWN reaches the pill,
    # LEFT returns to the chips.
    ("wp_grid", "Up"): "wp_back",
    ("wp_grid", "Down"): "wp_export",
    ("wp_grid", "Left"): "wp_chips",
}


def wallpaper_focus_edges(layout: str) -> dict[tuple[str, str], str]:
    """{(owner id, direction): target id} for every nextFocus edge declared."""
    edges: dict[tuple[str, str], str] = {}
    for element in re.finditer(r"<\w+(?:[^>]*?)>", layout, re.S):
        attrs = element.group(0)
        owner = re.search(r'android:id="@\+id/(\w+)"', attrs)
        if not owner:
            continue
        for direction, target in re.findall(
                r'android:nextFocus(\w+)="@\+?id/(\w+)"', attrs):
            edges[(owner.group(1), direction)] = target
    return edges


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def collect() -> list[str]:
    """Every wiring problem in the tree, as a list of sentences."""
    problems: list[str] = []

    def check(ok: bool, label: str):
        if not ok:
            problems.append(label)

    app_manifest = read(APP_MANIFEST)
    pop_manifest = read(POP_MANIFEST)
    banners_manifest = read(BANNERS_MANIFEST)
    for name, manifest in (("app", app_manifest), ("pop", pop_manifest), ("banners", banners_manifest)):
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

    # The wallpapers screen's focus chain, as built. Pinned as parsed
    # (owner, direction) -> target edges rather than substrings, because a
    # substring test passes as soon as *any* view carries the edge: it could
    # not tell "Back leads down to the selection bar" from "the export pill
    # does". The two-pane rebuild moved this chain - the rail is now
    # Back -> selection bar -> series chips (a vertical list) -> export pill,
    # and the grid sits to the right of the chips, so LEFT/RIGHT cross the
    # panes and nothing lives below Back or above the pill any more. The two
    # edges this list used to demand (`down -> wp_back`, `up -> wp_export`)
    # described the portrait layout and were dropped by that rebuild; the gate
    # was script-only, so pytest never ran it and the disagreement surfaced in
    # CI on the release PR instead of locally. If the screen changes again,
    # change this table deliberately.
    for (owner, direction), target in WP_FOCUS_CHAIN.items():
        got = wallpaper_focus_edges(read(WP_LAYOUT)).get((owner, direction))
        check(got == target,
              f"activity_wallpapers.xml: {owner} nextFocus{direction} should "
              f"reach {target}, found {got or 'nothing'}")
    # And no edge the table does not know about: an unplanned edge is how a
    # cursor escapes the panel.
    unknown = sorted(set(wallpaper_focus_edges(read(WP_LAYOUT))) - set(WP_FOCUS_CHAIN))
    check(not unknown,
          f"activity_wallpapers.xml: focus edges outside the pinned chain: "
          f"{[f'{o} nextFocus{d}' for o, d in unknown]}")

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
    banners_settings_layout = read(ROOT / "banners" / "src" / "main" / "res" / "layout"
                                   / "activity_settings.xml")
    faq_kt = read(ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds"
                  / "iconpack" / "FaqActivity.kt")
    faq_layout = read(ROOT / "app" / "src" / "main" / "res" / "layout"
                      / "activity_faq.xml")
    checker = read(ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds"
                   / "iconpack" / "UpdateChecker.kt")
    for rid in ("set_refresh_row", "set_appinfo_row", "set_faq_row", "set_apply_art_row"):
        check(f'android:id="@+id/{rid}"' in settings_layout,
              f"activity_settings.xml: missing {rid}")
        check(f'android:id="@+id/{rid}"' in pop_settings_layout,
              f"pop activity_settings.xml: missing {rid} (shared Kotlin compiles both)")
        check(f'android:id="@+id/{rid}"' in banners_settings_layout,
              f"banners activity_settings.xml: missing {rid} (shared Kotlin compiles both)")
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
                ROOT / "pop" / "src" / "main" / "res" / "values" / "issue_prefill.xml",
                ROOT / "banners" / "src" / "main" / "res" / "values" / "issue_prefill.xml"):
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
    for layout in (settings_layout, pop_settings_layout, banners_settings_layout):
        check('android:id="@+id/set_audit_row"' in layout,
              "activity_settings.xml: missing set_audit_row")
    for module in ("app", "pop", "banners", "pixel-neon"):
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
    check('(_banner)?' in inspector or '(?:_banner)?' in inspector,
          "InspectorActivity: componentsFor must accept the banner drawable. "
          "appfilter maps every component to <name>_banner - the square glyph "
          "reaches launchers through drawable.xml - so matching the square name "
          "alone left the component list empty for every tile and made Launch "
          "toast \"not mapped\" on the whole pack")
    appfilter = read(ROOT / "app" / "src" / "main" / "assets" / "appfilter.xml")
    pack = re.findall(r"<item>([a-z0-9_]+)</item>",
                      read(ROOT / "app" / "src" / "main" / "res" / "values" / "icon_pack.xml")
                      .split('name="icon_pack"')[1].split("</string-array>")[0])
    unmapped = [d for d in pack
                if f'drawable="{d}"' not in appfilter
                and f'drawable="{d}_banner"' not in appfilter]
    check(not unmapped,
          f"icon_pack drawables with no appfilter component at all: {unmapped[:8]} - "
          f"the inspector would show them as unmapped on device")
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
    for layout in (settings_layout, pop_settings_layout, banners_settings_layout):
        check('android:id="@+id/set_suite_row"' in layout,
              "activity_settings.xml: missing set_suite_row")
    for name, manifest in (("app", app_manifest), ("pop", pop_manifest), ("banners", banners_manifest)):
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
                ROOT / "pop" / "src" / "main" / "res" / "values" / "suite_hub.xml",
                ROOT / "banners" / "src" / "main" / "res" / "values" / "suite_hub.xml"):
        check(res.is_file() and "suite_hub_pkgs" in read(res),
              f"{res}: generated suite hub resource missing")

    return problems


# java.util.Formatter's grammar, which String.format applies to every
# resource read with getString(id, args): a positional conversion
# (%1$s, %2$d, %3$f) or the %% escape for one literal percent. Anything
# else after a % is a parse, never text.
FORMAT_GRAMMAR = re.compile(r"%(?:%|\d+\$[sdf])")

# The auditor's built deep link, structurally: the issue form's title prefix
# URL-encoded exactly once (%5B...%5D for its brackets), a space, the label
# placeholder, then the three prefillable fields in the order
# AuditorActivity passes them, ending the string.
AUDIT_LINK_SHAPE = re.compile(
    r"title=%5B(?P<prefix>[^&%]*)%5D\+%1\$s"
    r"&app_name=%1\$s&component=%2\$s&notes=%3\$s\Z")


def first_unhonourable_percent(body: str) -> int | None:
    """Offset of the first % the Formatter would parse as anything but a
    placeholder or an escape, or None. Walks left to right the way the
    Formatter itself does, so the second % of a %% pair is consumed by the
    first rather than flagged."""
    i = 0
    while i < len(body):
        if body[i] == "%":
            matched = FORMAT_GRAMMAR.match(body, i)
            if not matched:
                return i
            i = matched.end()
        else:
            i += 1
    return None


def format_string_problems() -> list[str]:
    """Percent signs the runtime's format grammar cannot honour, pack-wide.

    getString(id, args) runs the resource through String.format, whose only
    literal percent is %%. A URL-encoded value written into a fmt string
    bare - %5B for a '[' - parses as width 5 plus conversion 'B' (boolean)
    and throws IllegalFormatConversionException on the first String
    argument, which is exactly how every auditor row press killed the
    screen in 1.9.0: the resource matched the issue forms byte-for-byte,
    every gate was green, and the Formatter still refused it. So every
    <string> in every values*/*.xml of all three packs must be grammar the
    Formatter can honour; formatted="false" is the documented exemption,
    because Android serves a string marked that way raw and nothing may
    ever pass args to it.

    The auditor's deep link gets a structural check on top of the scan:
    after the same %% -> % collapse the runtime performs, it must decode
    back to the prefilled issue shape - the form's title prefix encoded
    once, the three positional fields in order - with no %% left in what
    the screen would build. That is what QrBitmap is handed, so it is what
    a scan from the couch opens.
    """
    problems: list[str] = []
    for module, base in (
        ("app", ROOT / "app" / "src" / "main" / "res"),
        ("pop", ROOT / "pop" / "src" / "main" / "res"),
        ("banners", ROOT / "banners" / "src" / "main" / "res"),
        ("pixel-neon", ROOT / "pixel-neon" / "app" / "src" / "main" / "res"),
    ):
        values = sorted(base.glob("values*/*.xml"))
        if not values:
            problems.append(f"{module}: no values*/*.xml under {base}")
        for path in values:
            for element in re.finditer(r"<string\b([^>]*)>(.*?)</string>",
                                       read(path), re.S):
                attrs, body = element.groups()
                if 'formatted="false"' in attrs:
                    # The documented exemption: Android serves the string
                    # raw, so no formatting grammar ever applies to it.
                    continue
                offset = first_unhonourable_percent(body)
                if offset is not None:
                    name = re.search(r'name="([^"]+)"', attrs)
                    name = name.group(1) if name else "?"
                    problems.append(
                        f"{module} {path.relative_to(ROOT)}:{name}: percent at "
                        f"offset {offset} is not a placeholder or a %% escape - "
                        "String.format would parse it as a conversion "
                        f"({body[offset:offset + 7]!r}); double it, or mark the "
                        'string formatted="false" if nothing ever formats it')
    for res in (ROOT / "app" / "src" / "main" / "res" / "values" / "issue_prefill.xml",
                ROOT / "pop" / "src" / "main" / "res" / "values" / "issue_prefill.xml",
                ROOT / "banners" / "src" / "main" / "res" / "values" / "issue_prefill.xml"):
        if not res.is_file():
            continue  # the wiring block above already fails a missing file
        match = re.search(r'<string name="audit_issue_url_fmt">(.*?)</string>',
                          read(res), re.S)
        if not match:
            problems.append(f"{res.relative_to(ROOT)}: no audit_issue_url_fmt string")
            continue
        # What the Formatter leaves after handling literals: each %% becomes
        # one %, placeholders stay as tokens, and a percent the grammar
        # rejects stays too - the scan above is what catches that one.
        built = FORMAT_GRAMMAR.sub(
            lambda m: "%" if m.group(0) == "%%" else m.group(0),
            match.group(1)).replace("&amp;", "&")
        rel = res.relative_to(ROOT)
        if not built.startswith(
                "https://github.com/brevityA/CoreBuildsApps/issues/new?"):
            problems.append(f"{rel}: the built link must open the repo's "
                            f"new-issue route, got {built[:60]!r}")
        if "%%" in built:
            problems.append(f"{rel}: a %% escape survived into the built link "
                            "- an escape applied to a placeholder, or twice "
                            "to a literal, prints instead of resolving")
        if not AUDIT_LINK_SHAPE.search(built):
            problems.append(
                f"{rel}: the built link does not decode back to the prefilled "
                "shape (title=%5B...%5D+%1$s&app_name=%1$s&component=%2$s"
                "&notes=%3$s)")
    return problems


def main() -> int:
    problems = collect() + format_string_problems()
    if problems:
        print(f"ui wiring gate: {len(problems)} problem(s)")
        for p in problems:
            print("  \u2717 " + p)
        return 1
    print("ui wiring gate ok - 2 manifests, 5 permissions, auditor visibility, "
          "wallpapers/export/preview wiring, launcher tools + FAQ + what's-new "
          "+ QR auditor + format specifiers locked")
    return 0


class UiWiring(unittest.TestCase):
    """The same gate under pytest.

    This file was a script with a `main()` and no test functions, so
    `pytest tests/` collected nothing from it and reported the suite green
    while CI - which runs `python tests/test_ui_wiring.py` - was failing. Two
    runners, two answers, and the local one is the one everybody reads.
    """

    def test_wiring(self) -> None:
        self.assertEqual(collect(), [])

    def test_format_strings(self) -> None:
        """The gate 1.9.0's auditor crash paid for, under pytest too."""
        self.assertEqual(format_string_problems(), [])


if __name__ == "__main__":
    if len(sys.argv) > 1 or os.environ.get("PYTEST_CURRENT_TEST"):
        unittest.main()
    raise SystemExit(main())
