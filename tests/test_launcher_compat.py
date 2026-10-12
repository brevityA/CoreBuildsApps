#!/usr/bin/env python3
"""The launcher compatibility data must stay honest.

Why this exists
---------------
The apply flow knows ten launchers and one it deliberately refuses (FLauncher),
and the compatibility screen projects that knowledge for a user troubleshooting
"my icon is not applying" or switching launchers. The screen is a projection of
`ApplyIconPack.COMPATIBILITY` - but only as long as every entry carries the
fields the projection reads, and the invariants that keep the fields telling the
truth:

  * a WALK entry must have `inboundApply = false` (the walk exists because the
    probe would only miss - a WALK that still probes is a UI lying about the
    mechanism);
  * a GLYPHS entry must carry the note that names Core Builds Glyphs (the row
    says "applies the square glyphs" and the note must say which pack to use,
    or a phone-launcher user is told a fact without the fix);
  * a NONE entry applies no art (a "not supported" row that still claims an
    art is the FLauncher mistake the 2026-09-24 changelog entry removed);
  * FLauncher's package must be in the manifest's `<queries>` or
    `isInstalled` could never see it on Android 11+ and the row would
    misreport "not installed" on every box that has it;
  * the not-applying form's launcher dropdown must cover every known launcher
    (plus Other), so a report can name the launcher the app itself knows.

The Kotlin is not importable from Python, so this parses the source the same
way test_live_wallpaper.py pins LiveLoop.kt's LOOP entries.
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPLY = ROOT / "app/src/main/java/tv/corebuilds/iconpack/ApplyIconPack.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
TEMPLATE = ROOT / ".github/ISSUE_TEMPLATE/2.icon_not_applying.yml"

ENTRY = re.compile(r"^\s{4}val (\w+) = Launcher\(", re.M)


def string_constants(text: str) -> dict[str, str]:
    """{NAME: value} for `private val NAME = "..."` (may wrap lines).

    Launcher entries name their notes with these constants
    (PHONE_LAUNCHER_NOTE), so the "does the note name Core Builds Glyphs"
    check has to resolve the reference, not just read the entry's body."""
    out = {}
    for m in re.finditer(r"private val (\w+)\s*=\s*(?:\n\s*)?\"((?:[^\"\\]|\\.)*)\"",
                         text, re.S):
        out[m.group(1)] = m.group(2)
    return out


def launcher_entries(text: str, consts: dict[str, str]) -> dict[str, dict[str, str]]:
    """{name: {field: value}} for every `val X = Launcher(` entry."""
    out = {}
    matches = list(ENTRY.finditer(text))
    for i, m in enumerate(matches):
        end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        body = text[m.end():end]
        # The notes a launcher actually carries: inline strings plus any
        # constant the listOf(...) references, resolved through `consts`.
        notes_text = " ".join(
            consts[c] for c in re.findall(r"[A-Z]\w+", body) if c in consts
        )
        display = re.search(r'displayName = "([^"]*)"', body)
        art = re.search(r"art = Art\.(\w+)", body)
        mode = re.search(r"applyMode = ApplyMode\.(\w+)", body)
        # The data class defaults (ApplyIconPack.Launcher): art BANNERS,
        # applyMode AUTO - an entry that omits them inherits those.
        out[m.group(1)] = {
            "display": display.group(1) if display else "",
            "art": art.group(1) if art else "BANNERS",
            "mode": mode.group(1) if mode else "AUTO",
            "inbound": "inboundApply = false" in body,
            "notes_named": "Core Builds Glyphs" in (body + " " + notes_text),
            "has_notes": "notes = listOf(" in body,
        }
    return out


def list_members(text: str, name: str) -> list[str]:
    m = re.search(rf"val {name} = listOf\(([^)]*)\)", text, re.S)
    assert m, f"{name} not found in ApplyIconPack.kt"
    return [t for t in re.findall(r"\b([A-Z]\w*)\b", m.group(1))]


class LauncherCompatData(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = APPLY.read_text(encoding="utf-8")
        cls.consts = string_constants(cls.text)
        cls.entries = launcher_entries(cls.text, cls.consts)

    def test_every_entry_declares_art_and_mode(self):
        for name, e in self.entries.items():
            self.assertIn(e["art"], {"BANNERS", "GLYPHS", "NONE"},
                          f"{name}: art = Art.{e['art']!r} - the compatibility "
                          "screen would render a blank art line")
            self.assertIn(e["mode"], {"AUTO", "WALK", "PER_APP", "NONE"},
                          f"{name}: applyMode = ApplyMode.{e['mode']!r}")

    def test_walk_entries_do_not_probe(self):
        for name, e in self.entries.items():
            if e["mode"] == "WALK":
                self.assertTrue(e["inbound"],
                                f"{name}: applyMode WALK but inboundApply is "
                                "not false - the screen would show the walk "
                                "while the apply button still fires a probe")

    def test_glyphs_entries_name_the_glyphs_pack(self):
        for name, e in self.entries.items():
            if e["art"] == "GLYPHS":
                self.assertTrue(e["has_notes"] and e["notes_named"],
                                f"{name}: art GLYPHS without the note naming "
                                "Core Builds Glyphs - the row tells the user "
                                "what applies without the pack to apply")

    def test_none_entries_apply_no_art(self):
        for name, e in self.entries.items():
            if e["mode"] == "NONE":
                self.assertEqual(e["art"], "NONE",
                                 f"{name}: not supported but still claims "
                                 f"art {e['art']} - the FLauncher mistake")

    def test_compatibility_covers_all_plus_not_supported(self):
        all_members = list_members(self.text, "ALL")
        not_supported = list_members(self.text, "NOT_SUPPORTED")
        # COMPATIBILITY is defined as `ALL + NOT_SUPPORTED`, not a listOf, so
        # verify that definition directly.
        m = re.search(r"val COMPATIBILITY = (.+)", self.text)
        self.assertIsNotNone(m, "COMPATIBILITY not found in ApplyIconPack.kt")
        self.assertEqual(m.group(1).strip(), "ALL + NOT_SUPPORTED",
                         "COMPATIBILITY must be ALL + NOT_SUPPORTED in order")
        compat = all_members + not_supported
        self.assertTrue(all_members, "ALL is empty")
        self.assertTrue(not_supported,
                        "NOT_SUPPORTED is empty - a known inapplicable "
                        "launcher would have no row, and a user on it would "
                        "be pointed at an apply that cannot land")
        # Every listed constant must be a launcher entry with a display name.
        for member in compat:
            self.assertIn(member, self.entries,
                          f"COMPATIBILITY lists {member}, which is not a "
                          "Launcher entry")
            self.assertTrue(self.entries[member]["display"],
                            f"{member} has no displayName")

    def test_flauncher_package_is_query_visible(self):
        entry = self.entries.get("FLAUNCHER")
        self.assertIsNotNone(entry, "FLauncher has no entry - its exclusion "
                                   "is deliberate and documented, and the "
                                   "compatibility screen needs the row")
        m = re.search(r"packages = listOf\(\"([^\"]+)\"\)",
                      self.text[self.text.index("val FLAUNCHER"):], re.S)
        self.assertIsNotNone(m, "FLAUNCHER declares no package")
        manifest = MANIFEST.read_text(encoding="utf-8")
        self.assertIn(f"<package android:name=\"{m.group(1)}\" />", manifest,
                      "FLauncher's package is not in <queries>: on Android "
                      "11+ isInstalled could never see it and the row would "
                      "misreport not-installed on every box that has it")

    def test_not_applying_form_covers_every_known_launcher(self):
        options = re.findall(r"^\s{8}- (.+)$", TEMPLATE.read_text(encoding="utf-8"),
                             re.M)
        # The dropdown is the only list of options in that file.
        self.assertIn("Other", options, "the launcher dropdown needs Other")
        for name, e in self.entries.items():
            display = e["display"]
            if not display:
                continue
            covered = any(display == o or display.startswith(o + " ")
                          or o.startswith(display)
                          for o in options)
            self.assertTrue(covered,
                            f"{name} ({display}) has no option in the "
                            "not-applying form's launcher dropdown - a report "
                            "from that launcher cannot name it")


if __name__ == "__main__":
    unittest.main()
