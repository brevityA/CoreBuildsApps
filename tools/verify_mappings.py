#!/usr/bin/env python3
"""
Mapping verification against the Projectivy 1.1.9 reference pack.

The catalog's `unverified` flag is the pack's honesty layer: a component
mapping without evidence stays `unverified` (design-upgrade §6, guardrail 4:
"do not inflate counts with invented activities"). One large class of entries
— the reference-pack inheritances carried over when the pack's mapping was
seeded from Projectivy — has hard evidence: they appear verbatim in the
published Projectivy Icon Pack 1.1.9 appfilter
(tools/reference/projectivy-1.1.9-appfilter.xml).

The pack emits both spellings of every component (leading-dot shorthand and
fully-qualified twin; validate.py §5a2 enforces the pair), so a component is
corroborated when the reference contains either spelling of its identity.

Usage:
  verify_mappings.py --report     # list corroborated `unverified` entries
  verify_mappings.py --clear      # drop corroborated entries from `unverified`

The ratchet lives in tests/test_mapping_hygiene.py: once an entry is cleared,
the ceiling may only move down.
"""
from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "tools" / "catalog.json"
REFERENCE = ROOT / "tools" / "reference" / "projectivy-1.1.9-appfilter.xml"


def canonical(component: str) -> str:
    """ComponentName identity: pkg/.Activity and pkg/pkg.Activity are the
    same component; compare that identity, not the spelling."""
    match = re.match(r"^ComponentInfo\{([^/]+)/([^}]+)\}$", component)
    if not match:
        return component
    pkg, activity = match.groups()
    if activity.startswith("."):
        activity = pkg + activity
    return f"{pkg}/{activity}"


def reference_components() -> tuple[set[str], set[str]]:
    """Return (raw strings, canonical identities) from the reference appfilter."""
    raw: set[str] = set()
    root = ET.parse(REFERENCE).getroot()
    for item in root.findall("item"):
        value = item.get("component", "")
        if not value.startswith("ComponentInfo{"):
            value = f"ComponentInfo{{{value.rstrip('}')}}}"
        raw.add(value)
    return raw, {canonical(v) for v in raw}


def corroborated(entry: str, raw: set[str], canon: set[str]) -> bool:
    wrapped = f"ComponentInfo{{{entry}}}"
    return wrapped in raw or canonical(wrapped) in canon


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--report", action="store_true",
                      help="list corroborated unverified entries, change nothing")
    mode.add_argument("--clear", action="store_true",
                      help="drop corroborated entries from `unverified`")
    args = parser.parse_args()

    raw, canon = reference_components()
    data = json.loads(CATALOG.read_text(encoding="utf-8"))

    total = cleared = 0
    per_icon = []
    for icon in data["icons"]:
        entries = icon.get("unverified") or []
        backed = [e for e in entries if corroborated(e, raw, canon)]
        if not entries:
            continue
        total += len(entries)
        cleared += len(backed)
        per_icon.append((icon["name"], entries, backed))

    if args.report:
        for name, entries, backed in per_icon:
            if backed:
                for e in backed:
                    print(f"  {name}: {e}")
        print(f"\n{cleared} of {total} unverified entries are corroborated "
              f"verbatim by the reference pack; {total - cleared} remain.")
        return 0

    # --clear
    changed = 0
    for icon in data["icons"]:
        entries = icon.get("unverified") or []
        if not entries:
            continue
        kept = [e for e in entries if not corroborated(e, raw, canon)]
        if kept:
            if len(kept) != len(entries):
                icon["unverified"] = kept
                changed += 1
        elif "unverified" in icon:
            del icon["unverified"]
            changed += 1
    CATALOG.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n",
                       encoding="utf-8")
    print(f"Cleared {cleared} corroborated entries from {changed} icons; "
          f"{total - cleared} unverified components remain.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
