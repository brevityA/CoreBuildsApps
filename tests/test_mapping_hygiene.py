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
# 69 -> 77: the Projectivy internal cards batch (2026-09-14) adds 8
# launcher-internal components (AV input, Settings, Categories, Channels)
# that cannot be device-verified without a Projectivy 4.70 install; they are
# expected to clear at the next ADB scan (docs/research/community-input-icons-
# 2026-09.md).
# 77 -> 83: the Projectivy input-marks batch (2026-09-15) adds 6 more
# launcher-internal components (TV input, source menu, media explorer)
# whose activity names follow the launcher's documented Source{X}Activity /
# {X}ShortcutActivity patterns but are not corroborated by any source; they
# clear at the next ADB scan.
# 83 -> 90: Televizo, OTT Navigator and NextPlayer (2 spellings each) plus
# SmartTube's beta channel. The packages come from a research document and
# the activity paths are inferred from it, not read off an APK or a device.
# They are declared here rather than shipped silently: an inferred activity
# that does not match is inert, but counting it as evidence is what guardrail
# 4 forbids. They clear at the next ADB scan, or the components come out.
# 90 -> 92: Arena4Viewer's AVActivity package variants from the requested
# package plus indexed APK manifest evidence. The app is present in the
# official site, but these exact TV launcher components still need a device
# scan before the flag can be cleared.
# 92 -> 76 (accuracy sweep 2026-10-18): 16 components were listed twice, once
# per name form (`pkg/.Act` and `pkg/pkg.Act`), both flagged. The build emits
# both forms from one entry, so the copies are folded; appfilter.xml is
# byte-identical and no flag changed hands.
# 76 -> 73 (2.5.0): Hi Browser's three MainActivity guesses came out. #354, a
# scanner report from a SHIELD, showed the app launches
# com.vt.hibrowser.activity.home.HomeActivity, which is now mapped; the guesses
# were never seen on any device.
# 73 -> 75 (2.5.0, same release): TVLok's two components were package guesses
# that carried no flag at all, which the catalog reads as device-confirmed.
# Its colour note said so ("package candidates unconfirmed", #155), and #352
# reports the icon does not apply. Flagging them is a correction, not a new
# guess: the release still ends one below the 76 it started from.
# The ceiling may only move back down.
UNVERIFIED_CEILING = 75


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

    def test_no_component_is_listed_in_both_name_forms(self):
        # build_icons.py writes both spellings (`pkg/.Act`, `pkg/pkg.Act`) from
        # one entry. Listing both counted 31 components twice in the
        # published total (1257 for 1226) and 16 unverified ones twice.
        def full(c):
            pkg, act = c.split("/", 1)
            return f"{pkg}/{pkg}{act}" if act.startswith(".") else c
        twice = [(icon["drawable"], c) for icon in self.icons
                 for c in {full(c) for c in icon["components"]}
                 if sum(full(x) == c for x in icon["components"]) > 1]
        self.assertEqual(twice, [], f"components listed in both name forms: {twice[:5]}")


def _reference_drawables() -> dict[str, set[str]]:
    """Canonical component -> the drawable(s) 1.1.9 maps it to."""
    out: dict[str, set[str]] = {}
    for item in ET.parse(REFERENCE).getroot().findall("item"):
        value = item.get("component", "")
        if not value.startswith("ComponentInfo{"):
            value = f"ComponentInfo{{{value.rstrip('}')}}}"
        out.setdefault(_canonical(value), set()).add(item.get("drawable", ""))
    return out


class ProvenanceTests(unittest.TestCase):
    """Every mapping says where it came from.

    2.5.0 backfilled the 521 icons that had no `mapping_source`, from evidence
    that can be checked: the decoded 1.1.9 appfilter, the suite's own
    manifests, and the commit that first carried a component. These tests
    check what can be checked offline; a commit hash needs the full history,
    so only its form is held here.
    """

    @classmethod
    def setUpClass(cls):
        cls.icons = json.loads(CATALOG.read_text(encoding="utf-8"))["icons"]
        cls.reference = _reference_drawables()

    @staticmethod
    def _canon(component: str) -> str:
        return _canonical(f"ComponentInfo{{{component}}}")

    def test_every_icon_says_where_its_mapping_came_from(self):
        missing = [i["drawable"] for i in self.icons
                   if not (i.get("mapping_source") or "").strip()]
        self.assertEqual(missing, [], f"{len(missing)} icons with no mapping_source")

    def test_a_whole_1_1_9_citation_covers_every_component(self):
        # A bare citation, or one that adds only the drawable 1.1.9 used,
        # claims every component came from the reference. Five such icons had
        # gained components since and said nothing about them (Launcher
        # Manager, RTVE Play, 7plus, Vewd, Vidio); 2.5.0 dated the additions.
        whole = re.compile(r'Projectivy Icon Pack 1\.1\.9(?:, as drawable (.+))?')
        cited = 0
        for icon in self.icons:
            m = whole.fullmatch(icon.get("mapping_source", ""))
            if not m:
                continue
            cited += 1
            comps = [self._canon(c) for c in icon["components"]]
            self.assertEqual([c for c in comps if c not in self.reference], [],
                             f"{icon['drawable']}: cites 1.1.9 for components it lacks")
            named = set(re.findall(r'"([^"]+)"', m.group(1) or "")) or {icon["drawable"]}
            there = set().union(*(self.reference[c] for c in comps))
            self.assertEqual(named, there, f"{icon['drawable']}: 1.1.9 drawable misnamed")
        self.assertGreater(cited, 800)

    def test_a_partial_1_1_9_citation_names_reference_components(self):
        partial = re.compile(r'Projectivy Icon Pack 1\.1\.9 \(([^)]+)\)')
        for icon in self.icons:
            m = partial.match(icon.get("mapping_source", ""))
            if not m:
                continue
            named = m.group(1).split(", ")
            if not all("/" in n for n in named):
                continue  # a hand-written note naming an activity, not a list
            for comp in named:
                self.assertIn(comp, icon["components"], icon["drawable"])
                self.assertIn(self._canon(comp), self.reference, icon["drawable"])

    def test_a_first_commit_is_cited_by_hash_and_date(self):
        stamp = re.compile(r"first committed in [0-9a-f]{9} "
                           r"\(\d{4}-\d{2}-\d{2}(?:, (?:v\d+\.\d+\.\d+|#\d+))?\)")
        for icon in self.icons:
            source = icon.get("mapping_source", "")
            self.assertEqual(source.count("first committed in"),
                             len(stamp.findall(source)), icon["drawable"])

    def test_an_own_app_citation_matches_its_manifest(self):
        own = re.compile(r"'s own (\S+/AndroidManifest\.xml)(?: \([^)]*\))?: (\.\w+) "
                         r"is the (LEANBACK_LAUNCHER|LAUNCHER) activity of ([\w.]+)")
        android = "{http://schemas.android.com/apk/res/android}"
        seen = 0
        for icon in self.icons:
            m = own.search(icon.get("mapping_source", ""))
            if not m:
                continue
            seen += 1
            path, activity, category, package = m.groups()
            root = ET.parse(ROOT / path).getroot()
            declared = {
                act.get(android + "name"): {c.get(android + "name", "").rsplit(".", 1)[-1]
                                            for c in act.iter("category")}
                for act in root.iter("activity")
            }
            self.assertIn(category, declared.get(activity, set()), icon["drawable"])
            self.assertIn(f"{package}/{activity}", icon["components"], icon["drawable"])
        self.assertGreaterEqual(seen, 4)  # Core EQ, Shift, Line, Doctor


if __name__ == "__main__":
    unittest.main()
