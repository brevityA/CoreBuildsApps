#!/usr/bin/env python3
"""D-pad + motion audit for Android TV layouts.

Static proof that a screen navigates: every focusable view is declared once,
every nextFocus* points at a real id, nothing focusable is isolated from the
focus graph, hidden bars are exempt (they focus from code when revealed), and
the motion helpers are actually wired.

  python tools/validate_ui.py app/src/main/res/layout/activity_main.xml
  python tools/validate_ui.py out/res/layout/activity_x.xml --kotlin out/java/X.kt
  python tools/validate_ui.py --all            # every app/ layout
  python tools/validate_ui.py --all --strict   # warnings fail too

Exits non-zero on errors (or on warnings with --strict).
"""
from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"
DIRS = ("Up", "Down", "Left", "Right")


@dataclass
class Audit:
    path: Path
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    stats: dict = field(default_factory=dict)


def _id_name(value: str | None) -> tuple[str | None, bool]:
    """(name, declared) for an android:id value."""
    if not value:
        return None, False
    if value.startswith("@+id/"):
        return value[len("@+id/"):], True
    if value.startswith("@id/"):
        return value[len("@id/"):], False
    return None, False


def audit_layout(path: Path, kotlin: str | None = None,
                 initial: str | None = None) -> Audit:
    """Audit one layout file. Pure function — also used by tests."""
    audit = Audit(path=path)
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        audit.errors.append(f"not well-formed XML: {e}")
        audit.stats = {"focusable": 0, "links": 0, "recyclers": 0}
        return audit

    is_item = path.name.startswith("item_")
    declared: dict[str, ET.Element] = {}
    dupes: set[str] = set()
    focusable: list[str] = []          # visible, document order
    gone_focusable: list[str] = []
    links: dict[str, dict[str, str]] = {}
    inbound: dict[str, int] = {}
    recyclers = 0
    focusable_any = 0  # includes id-less roots (item layouts need no ids)

    def walk(node: ET.Element, parent_gone: bool) -> None:
        nonlocal recyclers, focusable_any
        gone = parent_gone or node.get(ANDROID + "visibility") == "gone"
        vid, is_declared = _id_name(node.get(ANDROID + "id"))
        if is_declared and vid:
            if vid in declared:
                dupes.add(vid)
            else:
                declared[vid] = node
        tag = node.tag.split("}")[-1]
        if "RecyclerView" in tag:
            recyclers += 1
            if node.get(ANDROID + "clipToPadding") != "false" and vid:
                audit.warnings.append(
                    f"@{vid}: RecyclerView without clipToPadding=false "
                    "(focus zoom clips at the row edge)")
        # Links are recorded from every declared id, focusable or not:
        # RecyclerViews are not focusable themselves, but nextFocus* pointing
        # at them works because ViewGroup.requestFocus() descends into the
        # first focusable child. Without container nodes the graph breaks.
        if vid and is_declared:
            for d in DIRS:
                target = node.get(ANDROID + f"nextFocus{d}")
                if target:
                    name, _ = _id_name(target)
                    if name:
                        links.setdefault(vid, {})[d.lower()] = name
                        inbound[name] = inbound.get(name, 0) + 1
        if node.get(ANDROID + "focusable") == "true":
            focusable_any += 1
        if node.get(ANDROID + "focusable") == "true" and vid:
            (gone_focusable if gone else focusable).append(vid)
            if not gone:
                if node.get(ANDROID + "focusableInTouchMode") != "true":
                    audit.warnings.append(
                        f"@{vid}: focusable without focusableInTouchMode "
                        "(D-pad is fine; touch/keyboard focus is not)")
                if (node.get(ANDROID + "clickable") == "true"
                        and node.get(ANDROID + "minHeight") is None):
                    audit.warnings.append(
                        f"@{vid}: clickable focusable without minHeight "
                        "(48dp floor lives in @dimen/cb_target_min)")
        for child in node:
            walk(child, gone)

    walk(root, False)

    for vid in sorted(dupes):
        audit.errors.append(f"@{vid}: declared twice (@+id must be unique)")

    for vid, dirs in links.items():
        for d, target in sorted(dirs.items()):
            if target not in declared:
                audit.errors.append(
                    f"@{vid}: nextFocus{DIRS[['up', 'down', 'left', 'right'].index(d)]} "
                    f"points at @id/{target}, which this layout never declares")

    if is_item:
        # Either the root takes focus (item_icon) or a card inside does
        # (item_wallpaper's FrameLayout wrapper). Zero focusables anywhere
        # means the tile is unreachable. Ids are irrelevant here — item
        # roots legitimately carry none.
        if focusable_any == 0:
            audit.errors.append(
                "no focusable view in this item layout "
                "(D-pad can never land on this tile)")
    else:
        start = initial or (focusable[0] if focusable else None)
        if initial and initial not in declared:
            audit.errors.append(
                f'initial focus "{initial}" is not declared in this layout')
            start = None
        elif initial and initial in gone_focusable:
            audit.errors.append(
                f'initial focus "{initial}" sits inside a hidden (gone) bar')
            start = None
        if start is None and not initial:
            audit.errors.append("no focusable view: D-pad has nowhere to land")
        elif start:
            # Reachability over declared links. Containers count as nodes:
            # reaching a RecyclerView means reaching its items.
            seen = {start}
            queue = [start]
            while queue:
                for nxt in links.get(queue.pop(), {}).values():
                    if nxt not in seen:
                        seen.add(nxt)
                        queue.append(nxt)
            for vid in focusable:
                if vid == start or vid in seen:
                    continue
                if not links.get(vid) and not inbound.get(vid):
                    if len(focusable) > 1:
                        audit.errors.append(
                            f"@{vid}: isolated — no nextFocus in or out "
                            "(D-pad can never arrive or leave deterministically)")
                else:
                    audit.warnings.append(
                        f"@{vid}: not reachable from initial focus @{start} "
                        "over declared links (relies on spatial focus search)")

    if kotlin is not None:
        for vid in gone_focusable:
            if vid not in kotlin:
                audit.warnings.append(
                    f"@{vid}: hidden bar button never referenced in the "
                    "Activity (nothing reveals or focuses it?)")
        if focusable and "TvFocus" not in kotlin and "animate()" not in kotlin:
            audit.warnings.append(
                "no focus motion wired (TvFocus.attach/zoomItems not found)")

    audit.stats = {
        "focusable": len(focusable),
        "links": sum(len(d) for d in links.values()),
        "recyclers": recyclers,
        "gone_exempt": len(gone_focusable),
    }
    return audit


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Audit D-pad wiring and motion of TV layouts.")
    parser.add_argument("layouts", nargs="*", help="layout XML files to audit")
    parser.add_argument("--all", action="store_true",
                        help="audit every layout in app/src/main/res/layout")
    parser.add_argument("--kotlin", default=None,
                        help="Activity source for hidden-bar/motion checks")
    parser.add_argument("--initial", default=None,
                        help="id that must hold initial focus")
    parser.add_argument("--strict", action="store_true",
                        help="warnings fail the audit")
    args = parser.parse_args(argv)

    paths: list[Path] = []
    if args.all:
        paths = sorted((ROOT / "app" / "src" / "main" / "res" / "layout").glob("*.xml"))
        if not paths:
            print("no layouts found under app/src/main/res/layout")
            return 1
    else:
        if not args.layouts:
            parser.error("give layout files or --all")
        paths = [Path(p) for p in args.layouts]

    kotlin = Path(args.kotlin).read_text(encoding="utf-8") if args.kotlin else None
    failures = 0
    for path in paths:
        audit = audit_layout(path, kotlin=kotlin, initial=args.initial)
        s = audit.stats
        status = "FAIL" if audit.errors else ("WARN" if audit.warnings else "OK")
        print(f"[{status}] {path}: {s.get('focusable', 0)} focusable, "
              f"{s.get('links', 0)} links, {s.get('recyclers', 0)} recyclers, "
              f"{s.get('gone_exempt', 0)} hidden-exempt")
        for msg in audit.errors:
            print(f"  error: {msg}")
        for msg in audit.warnings:
            print(f"  warn: {msg}")
        if audit.errors or (args.strict and audit.warnings):
            failures += 1
    clean = len(paths) - failures
    print(f"\n{clean}/{len(paths)} layouts clean"
          + (" (strict)" if args.strict else ""))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
