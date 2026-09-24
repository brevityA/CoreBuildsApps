#!/usr/bin/env python3
"""The menu audit: no screen may carry a control that does nothing, a screen
nobody can open, or a focus edge that ends in the void.

Why this exists
---------------
The 1.8.22 redesign moved whole screens into a two-pane shell - rails, panes,
rows restacked, the export affordance relocated, chip rows stood on their side.
Every one of those moves is an opportunity to leave behind a button with no
listener, a screen with no entry point, or a nextFocus edge naming a view that
can never take the cursor: the class of defect a user reports as "the menu is
broken" and a layout diff does not show, because the id still resolves and the
XML still parses.

check_ui_resources.py proves references resolve and that the focus chain does
not hop over a stop; this file proves the other half - that the stops *do*
something, that every screen is reachable from the front door, and that every
named focus edge ends on a view that can hold the cursor in at least one state.

What is checked of the app module:

1. every `clickable="true"` view in every activity and item layout is named by
   a `setOnClickListener`/`R.id.` reference in the Kotlin that inflates it;
2. every activity in the manifest is reachable from MainActivity by following
   `Intent(..., X::class.java)` through the Kotlin, so no screen is orphaned;
3. every `nextFocus*="@id/x"` names a view that is focus-capable - focusable,
   clickable, a scroll container, a field, or a container that hands focus to
   its children - with one documented exception per screen where naming a
   non-focusable view is the deliberate "stay put" edge;
4. every `requestFocus()` in Kotlin names an id that exists in the module's
   layouts, so an initial-focus target cannot silently vanish in a refactor.
"""
from __future__ import annotations

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"

MODULES = {
    "app": {
        "layout": ROOT / "app/src/main/res/layout",
        "kotlin": ROOT / "app/src/main/java/tv/corebuilds/iconpack",
        "manifest": ROOT / "app/src/main/AndroidManifest.xml",
    },
}

# layout stem -> the Kotlin file that inflates it
ACTIVITY_OWNER = {
    "main": "MainActivity.kt",
    "wallpapers": "WallpapersActivity.kt",
    "wallpaper_preview": "WallpaperPreviewActivity.kt",
    "settings": "SettingsActivity.kt",
    "about": "AboutActivity.kt",
    "faq": "FaqActivity.kt",
    "auditor": "AuditorActivity.kt",
    "inspector": "InspectorActivity.kt",
    "suite": "SuiteActivity.kt",
    "export_progress": "ExportProgressActivity.kt",
    "whats_new": "WhatsNewActivity.kt",
}

# Naming a view that is not focusable is how a chain says "stay put" instead of
# leaping into a hidden container. Each entry is deliberate and commented at
# the site (MainActivity.syncFocusChain); anything new must earn a comment.
DELIBERATE_UNFOCUSABLE_TARGETS = {
    "app": {"picker_hint"},
}

FOCUS_CAPABLE_TAGS = {"RecyclerView", "EditText", "ScrollView", "NestedScrollView"}


def manifest_activities(manifest: Path) -> set[str]:
    text = manifest.read_text(encoding="utf-8")
    return {m.rsplit(".", 1)[-1]
            for m in re.findall(r'android:name="\.?([\w.]+)"', text)
            if m.rsplit(".", 1)[-1].endswith("Activity")}


def clickable_ids(path: Path) -> set[str]:
    """Ids of views declared clickable, in either attribute order."""
    text = path.read_text(encoding="utf-8")
    ids = re.findall(r'android:id="@\+id/(\w+)"[^>]*?android:clickable="true"',
                     text, re.S)
    ids += re.findall(r'android:clickable="true"[^>]*?android:id="@\+id/(\w+)"',
                      text, re.S)
    return set(ids)


def layout_elements(path: Path):
    root = ET.parse(path).getroot()
    out = {}
    for el in root.iter():
        raw = el.get(ANDROID + "id")
        if raw:
            out[raw.rsplit("/", 1)[-1]] = el
    return out


def is_focus_capable(el: ET.Element) -> bool:
    if el.get(ANDROID + "focusable") == "true":
        return True
    if el.get(ANDROID + "clickable") == "true":
        return True
    if el.get(ANDROID + "descendantFocusability") == "afterDescendants":
        return True
    if el.tag.rsplit(".", 1)[-1] in FOCUS_CAPABLE_TAGS:
        return True
    return False


class Menus(unittest.TestCase):
    def test_every_clickable_control_has_a_handler(self):
        for module, paths in MODULES.items():
            kotlin = "\n\n".join(p.read_text(encoding="utf-8")
                                 for p in paths["kotlin"].glob("*.kt"))
            declared = manifest_activities(paths["manifest"])
            for layout in sorted(paths["layout"].glob("activity_*.xml")):
                screen = layout.stem.replace("activity_", "")
                owner_name = ACTIVITY_OWNER[screen]
                if owner_name[:-3] not in declared:
                    # A mirrored layout for a screen the manifest does not
                    # ship is inert, not broken: nothing references it.
                    continue
                owner = paths["kotlin"] / owner_name
                code = owner.read_text(encoding="utf-8") if owner.is_file() else kotlin
                missing = sorted(i for i in clickable_ids(layout)
                                 if f"R.id.{i}" not in code)
                self.assertFalse(
                    missing,
                    f"{module}/{layout.name}: clickable with no handler in "
                    f"{owner.name} - a menu entry that does nothing: {missing}")
            for layout in sorted(paths["layout"].glob("item_*.xml")):
                missing = set(i for i in clickable_ids(layout)
                              if f"R.id.{i}" not in kotlin)
                # A row whose ROOT is the clickable: adapters bind the listener
                # to the itemView, so the id never appears in Kotlin at all
                # (ChipAdapter: holder.view.setOnClickListener).
                root = ET.parse(layout).getroot()
                root_id = (root.get(ANDROID + "id") or "").rsplit("/", 1)[-1]
                if root_id in missing and re.search(
                        r"(holder\.view|itemView)\.setOnClickListener", kotlin):
                    missing.discard(root_id)
                self.assertFalse(
                    sorted(missing),
                    f"{module}/{layout.name}: clickable row control with no "
                    f"adapter handler: {sorted(missing)}")

    def test_every_screen_is_reachable_from_the_front_door(self):
        for module, paths in MODULES.items():
            declared = manifest_activities(paths["manifest"])
            kotlin = {p.name: p.read_text(encoding="utf-8")
                      for p in paths["kotlin"].glob("*.kt")}
            reached = {"MainActivity"}
            frontier = ["MainActivity"]
            while frontier:
                name = frontier.pop()
                code = kotlin.get(name + ".kt", "")
                for target in re.findall(r"Intent\([^)]*?(\w+Activity)::class\.java",
                                         code, re.S):
                    if target not in reached:
                        reached.add(target)
                        frontier.append(target)
            orphaned = sorted(declared - reached)
            self.assertFalse(
                orphaned,
                f"{module}: activities no screen opens - a menu with no entry "
                f"point: {orphaned}")

    def test_no_focus_edge_ends_in_the_void(self):
        for module, paths in MODULES.items():
            allowed = DELIBERATE_UNFOCUSABLE_TARGETS[module]
            # Row templates name ids that live in the activity layout that
            # hosts them - RecyclerView.focusSearch resolves them against the
            # window at runtime - so targets are looked up module-wide.
            module_ids: dict[str, ET.Element] = {}
            for layout in paths["layout"].glob("*.xml"):
                module_ids.update(layout_elements(layout))
            for layout in sorted(paths["layout"].glob("*.xml")):
                elements = layout_elements(layout)
                for el in elements.values():
                    for attr, value in el.attrib.items():
                        if not attr.startswith(ANDROID + "nextFocus"):
                            continue
                        if not value.startswith("@id/"):
                            continue
                        target = value.rsplit("/", 1)[-1]
                        stop = module_ids.get(target)
                        self.assertIsNotNone(
                            stop, f"{module}/{layout.name}: {attr} names "
                            f"@id/{target}, which does not exist")
                        self.assertTrue(
                            is_focus_capable(stop) or target in allowed,
                            f"{module}/{layout.name}: {attr}=\"@id/{target}\" "
                            f"names a view that can never hold the cursor - "
                            f"the edge dead-ends")

    def test_initial_focus_targets_exist(self):
        for module, paths in MODULES.items():
            ids = set()
            for layout in paths["layout"].glob("*.xml"):
                ids |= set(layout_elements(layout))
            for kt in paths["kotlin"].glob("*.kt"):
                for target in re.findall(
                        r"findViewById<[\w<>]+\>\(R\.id\.(\w+)\)\.requestFocus",
                        kt.read_text(encoding="utf-8")):
                    self.assertIn(
                        target, ids,
                        f"{module}/{kt.name}: requestFocus() on R.id.{target}, "
                        f"which no layout declares")


if __name__ == "__main__":
    unittest.main()
