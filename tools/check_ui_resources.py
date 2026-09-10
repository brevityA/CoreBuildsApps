#!/usr/bin/env python3
"""Static check for the Core Builds in-app UI resources.

There is no Android SDK in this environment, so `assembleDebug` cannot run.
This does the part of resource linking that actually catches mistakes:

  1. every modified XML file is well-formed
  2. every @dimen/@color/@drawable/@string/@array reference in app/ and pop/
     resolves to a declared resource in that same module
  3. every R.id / R.string / R.drawable referenced from the shared Kotlin
     exists in the layouts and values of the module that compiles it
  4. every @+id declared in a layout is unique within that layout
  5. no focusable view is stranded by an explicit nextFocus chain that hops
     over it (the class of bug that made the wallpapers button unreachable)

Exits non-zero on the first class of failure, listing all of them.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"

# Pop compiles ../app/src/main/java, so its Kotlin must resolve against pop/.
MODULES = {
    "app": ROOT / "app" / "src" / "main",
    "pop": ROOT / "pop" / "src" / "main",
}
KOTLIN = ROOT / "app" / "src" / "main" / "java" / "tv" / "corebuilds" / "iconpack"

FAIL: list[str] = []


def fail(msg: str) -> None:
    FAIL.append(msg)


def declared_resources(res: Path) -> dict[str, set[str]]:
    """Collect resource names declared by a module's res/ tree."""
    out: dict[str, set[str]] = {
        "dimen": set(), "color": set(), "drawable": set(),
        "string": set(), "array": set(), "style": set(),
        "string-array": set(), "integer-array": set(),
    }
    for values in sorted(res.glob("values*/*.xml")):
        try:
            root = ET.parse(values).getroot()
        except ET.ParseError as e:
            fail(f"{values.relative_to(ROOT)}: not well-formed ({e})")
            continue
        for child in root:
            tag = child.tag
            name = child.get("name")
            if not name:
                continue
            if tag == "string":
                out["string"].add(name)
            elif tag == "color":
                out["color"].add(name)
            elif tag == "dimen":
                out["dimen"].add(name)
            elif tag == "style":
                out["style"].add(name)
            elif tag in ("string-array", "integer-array", "array"):
                out["array"].add(name)
                out[tag].add(name)
    # File-based resources.
    for d in res.glob("drawable*"):
        if d.is_dir():
            for f in d.iterdir():
                if f.is_file():
                    out["drawable"].add(f.stem)
    for d in res.glob("mipmap*"):
        if d.is_dir():
            for f in d.iterdir():
                if f.is_file():
                    out["drawable"].add(f.stem)
    # res/color/*.xml are color state lists, referenced as @color/name.
    cdir = res / "color"
    if cdir.is_dir():
        for f in cdir.iterdir():
            if f.is_file():
                out["color"].add(f.stem)
    return out


REF = re.compile(r'"@(?:\+)?(dimen|color|drawable|string|array|style)/([A-Za-z0-9_.]+)"')
# Framework / support refs we must not try to resolve locally.
SKIP_PREFIX = ("android:", "attr/")


def check_refs(path: Path, res: Path, have: dict[str, set[str]]) -> None:
    text = path.read_text(encoding="utf-8")
    for kind, name in REF.findall(text):
        if name.startswith(SKIP_PREFIX):
            continue
        # @style/Theme.* may be an AppCompat parent we don't declare.
        if kind == "style" and "." in name:
            continue
        if name not in have.get(kind, set()):
            fail(f"{path.relative_to(ROOT)}: @{kind}/{name} does not resolve in "
                 f"{path.parent.parent.parent.parent.name}/")


FOCUS_EXEMPT = {
    # empty_state and grid are mutually exclusive: when one is VISIBLE the
    # other is GONE, so chip_row's nextFocusDown="@id/grid" dead-ends on a
    # GONE view and FocusFinder falls back to the geometric search, which
    # finds these. Nothing routes around them while they are on screen.
    "empty_clear_search",
    "empty_clear_filter",
}


def focus_chain_gaps(path: Path) -> list[str]:
    """Report focusable views that an explicit nextFocus chain hops over.

    A view is only reachable by D-pad if some neighbour points at it. When a
    conditionally-visible view sits between two views whose chain names each
    other directly, the remote can never land on it — this is what made the
    wallpapers button, the update button, the 'Also <launcher>' row and the
    wallpaper selection bar unreachable. FocusFinder follows a GONE target's
    own nextFocus in the same direction, so the fix is always to route the
    chain *through* such a view rather than around it.
    """
    if path.name.startswith("item_"):
        return []  # RecyclerView row templates; the list owns their focus
    root = ET.parse(path).getroot()
    order: list[str] = []
    focusable: dict[str, str] = {}
    edges: list[tuple[str, str, str]] = []
    targets: set[str] = set()
    # Path of child indices from the root, with the index zeroed wherever the
    # parent lays its children out horizontally. Two views then share a path
    # exactly when they sit side by side in the same row (chips + search, or
    # the three selection-bar buttons), which is one vertical stop, not
    # several — comparing raw document order there reports false gaps.
    vpath: dict[str, tuple[int, ...]] = {}

    def horizontal(el) -> bool:
        tag = el.tag.split(".")[-1]
        if tag != "LinearLayout":
            return False
        # LinearLayout defaults to horizontal when orientation is omitted.
        return el.get(ANDROID + "orientation", "horizontal") == "horizontal"

    def walk(el, prefix: tuple[int, ...]) -> None:
        raw = el.get(ANDROID + "id")
        vid = raw.split("/")[-1] if raw else None
        # RecyclerView makes itself focusable in code, and anything carrying a
        # nextFocus attribute is by definition a stop on the chain — neither
        # shows up as android:focusable in the XML.
        is_focus = (el.get(ANDROID + "focusable") == "true"
                    or el.get(ANDROID + "clickable") == "true"
                    or el.get(ANDROID + "descendantFocusability")
                    == "afterDescendants"
                    or el.tag.split(".")[-1] == "RecyclerView"
                    or any(k.startswith(ANDROID + "nextFocus")
                           for k in el.attrib))
        if vid and is_focus:
            order.append(vid)
            focusable[vid] = el.tag.split(".")[-1]
            vpath[vid] = prefix
        for key, val in el.attrib.items():
            if not key.startswith(ANDROID + "nextFocus"):
                continue
            if not val.startswith("@id/"):
                continue
            targets.add(val.split("/")[-1])
            direction = key[len(ANDROID) + len("nextFocus"):]
            if vid and direction in ("Up", "Down"):
                edges.append((vid, direction, val.split("/")[-1]))
        side_by_side = horizontal(el)
        for idx, child in enumerate(el):
            walk(child, prefix + (0 if side_by_side else idx,))

    walk(root, ())

    # A container that is itself a focus target with afterDescendants hands
    # focus straight to its focusable children (update_bar -> update_button,
    # wp_selection_bar -> its three buttons), so those children are reachable
    # even though no chain names them directly.
    for el in root.iter():
        raw = el.get(ANDROID + "id")
        vid = raw.split("/")[-1] if raw else None
        if not vid or vid not in targets:
            continue
        if el.get(ANDROID + "descendantFocusability") != "afterDescendants":
            continue
        for child in el.iter():
            craw = child.get(ANDROID + "id")
            if craw:
                targets.add(craw.split("/")[-1])

    bands: list[tuple[int, ...]] = []
    for vid in order:
        if vpath[vid] not in bands:
            bands.append(vpath[vid])
    bands.sort()
    rank = {vid: bands.index(vpath[vid]) for vid in order}
    gaps = []
    for src, direction, dst in edges:
        if src not in rank or dst not in rank:
            continue
        lo, hi = sorted((rank[src], rank[dst]))
        for vid in order:
            if not lo < rank[vid] < hi:
                continue
            if vid in targets or vid in FOCUS_EXEMPT:
                continue
            gaps.append(
                f"{path.relative_to(ROOT)}: {vid} ({focusable[vid]}) is "
                f"unreachable — {src}'s nextFocus{direction}=\"@id/{dst}\" "
                f"hops over it and nothing points at it"
            )
    return sorted(set(gaps))


def layout_ids(path: Path) -> set[str]:
    root = ET.parse(path).getroot()
    ids: list[str] = []
    for el in root.iter():
        v = el.get(f"{ANDROID}id")
        if v and v.startswith("@+id/"):
            ids.append(v[len("@+id/"):])
    dupes = {i for i in ids if ids.count(i) > 1}
    if dupes:
        fail(f"{path.relative_to(ROOT)}: duplicate @+id {sorted(dupes)}")
    return set(ids)


def main() -> int:
    all_ids: dict[str, set[str]] = {}
    # Every file the loop below visits, parsed or not. Only app/ and pop/ go
    # through it; the focus pass also reads pixel-neon, so it has to report a
    # parse error there itself rather than assume this loop already did —
    # tracking only the successful parses would double-report the failures.
    visited: set[Path] = set()
    for mod, main in MODULES.items():
        res = main / "res"
        if not res.is_dir():
            fail(f"{mod}: no res/ tree")
            continue
        have = declared_resources(res)

        for xml in sorted(res.rglob("*.xml")):
            visited.add(xml)
            try:
                ET.parse(xml)
            except ET.ParseError as e:
                fail(f"{xml.relative_to(ROOT)}: not well-formed ({e})")
                continue
            check_refs(xml, res, have)
            if xml.parent.name == "layout":
                all_ids.setdefault(mod, set()).update(layout_ids(xml))

        # Kotlin references. Both modules compile this same source tree.
        for kt in sorted(KOTLIN.glob("*.kt")):
            src = kt.read_text(encoding="utf-8")
            for kind in ("id", "string", "drawable", "dimen", "color", "array"):
                for name in re.findall(rf"R\.{kind}\.([A-Za-z0-9_]+)", src):
                    if kind == "id":
                        if name not in all_ids.get(mod, set()):
                            fail(f"{kt.name} ({mod}): R.id.{name} is not declared "
                                 f"in any {mod} layout")
                    elif name not in have.get(
                            "string" if kind == "string" else
                            "drawable" if kind == "drawable" else
                            "dimen" if kind == "dimen" else
                            "color" if kind == "color" else "array", set()):
                        fail(f"{kt.name} ({mod}): R.{kind}.{name} does not resolve")

    # Focus reachability. Layout-only, so it covers pixel-neon too even though
    # that module keeps its own Kotlin and resources.
    layout_dirs = [
        ROOT / "app" / "src" / "main" / "res" / "layout",
        ROOT / "pop" / "src" / "main" / "res" / "layout",
        ROOT / "pixel-neon" / "app" / "src" / "main" / "res" / "layout",
    ]
    for layout_dir in layout_dirs:
        if not layout_dir.is_dir():
            continue
        for xml in sorted(layout_dir.glob("*.xml")):
            try:
                for gap in focus_chain_gaps(xml):
                    fail(gap)
            except ET.ParseError as e:
                if xml not in visited:
                    fail(f"{xml.relative_to(ROOT)}: not well-formed ({e})")
                continue

    if FAIL:
        print(f"FAILED — {len(FAIL)} problem(s):\n")
        for f in FAIL:
            print("  \u2717 " + f)
        return 1
    print("OK — XML well-formed, every resource reference resolves in both "
          "modules, every R.* in the shared Kotlin exists, no duplicate ids, "
          "no focusable view stranded by a nextFocus chain.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
