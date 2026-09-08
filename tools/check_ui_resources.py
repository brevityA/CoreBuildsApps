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
    for mod, main in MODULES.items():
        res = main / "res"
        if not res.is_dir():
            fail(f"{mod}: no res/ tree")
            continue
        have = declared_resources(res)

        for xml in sorted(res.rglob("*.xml")):
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

    if FAIL:
        print(f"FAILED — {len(FAIL)} problem(s):\n")
        for f in FAIL:
            print("  \u2717 " + f)
        return 1
    print("OK — XML well-formed, every resource reference resolves in both "
          "modules, every R.* in the shared Kotlin exists, no duplicate ids.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
