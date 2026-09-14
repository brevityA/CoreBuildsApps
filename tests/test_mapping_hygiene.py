#!/usr/bin/env python3
"""Mapping hygiene: the `unverified` flag can only shrink.

The catalog carries one honesty flag per component mapping: entries without
evidence stay `unverified` (design-upgrade §6 guardrail 4 — no invented
activities). The verification protocol
(docs/research/mapping-verification-2026-09.md) clears the one class that has
hard evidence — the reference-pack inheritances corroborated verbatim by the
published Projectivy 1.1.9 appfilter.

These tests ratchet the outcome:

1. every `unverified` entry must still parse as a component (pkg/activity);
2. no `unverified` entry may be corroborated verbatim by the reference pack
   (the clearing is complete — a future catalog edit that re-adds a
   corroborated component as `unverified` fails here);
3. the total count has a ceiling that may only move down.

Run: python -m unittest discover -s tests
"""
from __future__ import annotations

import json
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "tools" / "catalog.json"
REFERENCE = ROOT / "tools" / "reference" / "projectivy-1.1.9-appfilter.xml"

# Ratchet ceiling: unverified components remaining after the 2026-09
# verification tranche (464 of 533 cleared against Projectivy 1.1.9).
UNVERIFIED_CEILING = 69


def _canonical(component: str) -> str:
    match = re.match(r"^ComponentInfo\{([^/]+)/([^}]+)\}$", component)
    if not match:
        return component
    pkg, activity = match.groups()
    if activity.startswith("."):
        activity = pkg + activity
    return f"{pkg}/{activity}"


def _reference_components() -> tuple[set[str], set[str]]:
    raw: set[str] = set()
    root = ET.parse(REFERENCE).getroot()
    for item in root.findall("item"):
        value = item.get("component", "")
        if not value.startswith("ComponentInfo{"):
            value = f"ComponentInfo{{{value.rstrip('}')}}}"
        raw.add(value)
    return raw, {_canonical(v) for v in raw}


class MappingHygieneTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        cls.icons = cls.catalog["icons"]
        cls.ref_raw, cls.ref_canon = _reference_components()

    def _unverified(self):
        for icon in self.icons:
            for entry in icon.get("unverified") or []:
                yield icon["name"], entry

    def test_unverified_entries_are_wellformed_components(self):
        bad = [(name, e) for name, e in self._unverified()
               if not re.fullmatch(r"[^/\s{}]+/[^/\s{}]+", e)]
        self.assertEqual(bad, [],
                         f"{len(bad)} malformed unverified entries: {bad[:5]}")

    def test_no_unverified_entry_is_corroborated_by_the_reference(self):
        backed = [
            (name, entry) for name, entry in self._unverified()
            if f"ComponentInfo{{{entry}}}" in self.ref_raw
            or _canonical(f"ComponentInfo{{{entry}}}") in self.ref_canon
        ]
        self.assertEqual(backed, [],
                         f"{len(backed)} unverified entries are corroborated "
                         f"verbatim by the reference pack — clear them via "
                         f"tools/verify_mappings.py --clear: {backed[:5]}")

    def test_unverified_count_has_a_ratchet_ceiling(self):
        total = sum(1 for _ in self._unverified())
        self.assertLessEqual(
            total, UNVERIFIED_CEILING,
            f"{total} unverified components, above the {UNVERIFIED_CEILING} "
            "ratchet ceiling — the ceiling may only move down")


if __name__ == "__main__":
    unittest.main()
