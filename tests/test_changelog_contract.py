#!/usr/bin/env python3
"""The CHANGELOG is a machine-read file. This is the reader's contract.

Why this exists
---------------
`tools/prepare_release.py` parses `## [Unreleased]` with
`re.search(r"### Added\\n(.*?)(?=\\n### |\\Z)")` - one search per kind, first
match wins. In the 1.8.22 cycle a second `### Added` block was appended below
`### Fixed` instead of merged into the one at the top, and the stamper read the
first block only: the new bullet vanished from `Latestrelease/version.json`'s
highlights, which is what the in-app what's-new card renders. Nothing failed.
No gate read the CHANGELOG at all, and the file looked fine to a human because
both blocks were correctly formatted - the defect was their order and their
duplicated heading, which is precisely what a human skimming a diff does not
catch in a 1000-line changelog.

So the shape the stamper depends on is asserted here:

1. `## [Unreleased]` exists and is the first section;
2. no section carries a kind heading twice - the bug above - and every heading
   is one the vocabulary allows. The vocabulary is a ratchet (`VOCABULARY_FROM`,
   1.8.20): sections before it were written with free-form headings (Colour,
   Style, Verified, Known, Numbers, Internal, Notes ...) and 1.8.6 even carries
   two `### Fixed` blocks. Renaming shipped notes to satisfy a linter is churn,
   not correctness, so history is left exactly as it was published and the rule
   binds [Unreleased] and everything from 1.8.20 on;
3. inside `[Unreleased]`, the kinds run Added, Changed, Fixed, the order the
   stamper emits highlights in, so what a reader sees is what a user is shown;
4. every top-level bullet in `[Unreleased]` has a bold lead - `- **Name.** body`
   - because the lead *is* the highlight; a bullet without one can never reach
   the card. Released sections are not held to it: entries back to 1.0.0
   predate the convention, and rewriting shipped notes to satisfy a linter is
   churn, not correctness;
5. every released heading is `## [<label>] — <YYYY-MM-DD or Unreleased>`, the
   icon pack's bare-semver labels descend, dates do not run backwards, and the
   one section ever prepared without a tag (`1.8.7`, cut as versionCode 17 and
   deliberately never released) stays the only one - a second undated version
   section means someone stamped a release they did not publish;
6. `changelog_highlights()` on the current `[Unreleased]` returns between one
   and eight leads, none of them a receipt (a lead starting with a backticked
   `tests/` or `tools/` path is a receipt, deliberately kept off the card).

Run: `python3 tests/test_changelog_contract.py`
"""
from __future__ import annotations

import importlib.util
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHANGELOG = ROOT / "CHANGELOG.md"

KINDS = ("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security")
# House extension, used by released sections from 1.8.17 on for gate receipts
# ("Classic: Validated 943 icons ..."). Not a Keep a Changelog kind, so it is
# never allowed in [Unreleased] - the section the stamper reads, whose headings
# become the what's-new card.
HOUSE_KINDS = KINDS + ("Receipts",)
# The kind vocabulary is a ratchet, not a rewrite: sections before this one were
# written with free-form headings (Colour, Style, Verified, Known, Numbers,
# Android, Wallpapers, Internal, Notes, Unchanged, Rejected, Regression,
# Verification) and renaming shipped notes to satisfy a linter is churn. From
# 1.8.20 on, the convention holds and this file enforces it.
VOCABULARY_FROM = (1, 8, 20)
# The only version section ever prepared without a release tag. Pinned so a new
# one is a question, not a habit.
UNTAGGED_ALLOWED = {"1.8.7"}
# The order prepare_release.py emits highlights in, and therefore the order the
# section is written in.
CANONICAL = ("Added", "Changed", "Fixed")

# `## [1.8.21] — 2026-09-19`, and `## [Pop 1.0.0] — 2026-09-07` for the
# sections that belong to another app in the suite.
RELEASE_HEADING = re.compile(
    r"^## \[(?P<label>[^\]]+)\] — (?P<date>\d{4}-\d{2}-\d{2}|Unreleased)$"
)
SEMVER = re.compile(r"^\d+\.\d+\.\d+$")


def load_stamper():
    """prepare_release.py, imported for its highlight parser rather than run."""
    spec = importlib.util.spec_from_file_location(
        "prepare_release_under_test", ROOT / "tools" / "prepare_release.py"
    )
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def sections(text: str) -> list[tuple[str, str]]:
    """[(heading line, body)] for every `## [...]` section, in file order."""
    parts = re.split(r"(?m)^(## \[[^\]]+\][^\n]*)\n", text)
    out = []
    for i in range(1, len(parts), 2):
        out.append((parts[i].strip(), parts[i + 1]))
    return out


def kinds_in(body: str) -> list[str]:
    return re.findall(r"(?m)^### (\w+)", body)


def version_tuple(v: str) -> tuple[int, ...]:
    return tuple(int(n) for n in v.split("."))


class ChangelogContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.text = CHANGELOG.read_text(encoding="utf-8")
        cls.sections = sections(cls.text)

    def test_unreleased_is_the_first_section(self) -> None:
        self.assertTrue(self.sections, "no `## [...]` sections found at all")
        self.assertEqual(
            self.sections[0][0],
            "## [Unreleased]",
            "the stamper reads `## [Unreleased]`; it must exist and lead the "
            "file so a new entry cannot land under a released version",
        )

    def test_no_section_repeats_a_kind_heading(self) -> None:
        """The bug this file exists for: a second `### Added` is invisible to
        `re.search`, so its bullets never reach the highlights."""
        for heading, body in self.sections:
            allowed, why = self.vocabulary_for(heading)
            if allowed is None:
                continue  # free-form history; see VOCABULARY_FROM
            kinds = kinds_in(body)
            dupes = sorted({k for k in kinds if kinds.count(k) > 1})
            self.assertEqual(
                dupes,
                [],
                f"{heading} carries duplicate kind headings {dupes} - merge "
                "them into one block; prepare_release.py reads the first only",
            )
            unknown = [k for k in kinds if k not in allowed]
            self.assertEqual(
                unknown,
                [],
                f"{heading} has headings outside the vocabulary ({why}): "
                f"{unknown} - expected some of {allowed}",
            )

    @staticmethod
    def vocabulary_for(heading: str) -> tuple[tuple[str, ...] | None, str]:
        """Which kind headings a section may use, and why."""
        if heading == "## [Unreleased]":
            return KINDS, (
                "the stamper reads Added/Changed/Fixed out of this section and "
                "renders their leads as the what's-new card, so `Receipts` "
                "here would be stamped as a headline"
            )
        m = RELEASE_HEADING.match(heading)
        if not m:
            return None, ""
        label = m.group("label")
        if not SEMVER.match(label):
            return None, ""  # another app's section (Pop 1.0.0): its own notes
        if version_tuple(label) < VOCABULARY_FROM:
            return None, ""  # free-form history, left as shipped
        return HOUSE_KINDS, f"released sections from {'.'.join(map(str, VOCABULARY_FROM))} on"

    def test_unreleased_kinds_are_in_the_order_the_card_reads(self) -> None:
        body = self.sections[0][1]
        kinds = kinds_in(body)
        self.assertEqual(
            kinds,
            [k for k in CANONICAL if k in kinds],
            f"[Unreleased] runs {kinds}; the what's-new card is filled Added, "
            "then Changed, then Fixed, so the section is written that way too",
        )

    def test_unreleased_bullets_have_a_bold_lead(self) -> None:
        """The lead is the highlight. A bullet without one is a change no user
        is ever told about. History is exempt; the section being written is
        not."""
        offenders = [
            line[:72]
            for line in self.sections[0][1].splitlines()
            if line.startswith("- ") and not re.match(r"^- \*\*.+?\.\*\*", line)
        ]
        self.assertEqual(
            offenders,
            [],
            "every top-level [Unreleased] bullet must open "
            f"`- **Name of the change.**` - the stamper takes that lead as the "
            f"highlight, so a bullet without one is invisible to users: "
            f"{offenders}",
        )

    def test_released_headings_are_dated_and_descend(self) -> None:
        iconpack, untagged = [], []
        for heading, _ in self.sections[1:]:
            m = RELEASE_HEADING.match(heading)
            self.assertIsNotNone(
                m,
                f"{heading!r} is not `## [<label>] — <YYYY-MM-DD|Unreleased>` "
                "(em dash, ISO date) - docs/IconPackList.md and the release "
                "tooling read this shape",
            )
            label, date = m.group("label"), m.group("date")
            if date == "Unreleased":
                untagged.append(label)
            if SEMVER.match(label):
                iconpack.append((version_tuple(label), date))
        self.assertGreater(
            len(iconpack), 10, "icon-pack release history looks truncated"
        )
        dated = [(v, d) for v, d in iconpack if d != "Unreleased"]
        for (higher, date_a), (lower, date_b) in zip(dated, dated[1:]):
            self.assertGreater(
                higher, lower, f"versions ascend: {higher} then {lower}"
            )
            self.assertGreaterEqual(
                date_a, date_b, f"dates run backwards: {date_a} then {date_b}"
            )
        self.assertEqual(
            set(untagged),
            UNTAGGED_ALLOWED,
            f"version sections stamped without a release date: {untagged}. "
            f"{sorted(UNTAGGED_ALLOWED)} is history - prepared as versionCode "
            "17 and deliberately never tagged. A new one means a release was "
            "stamped and not published: tag it, or fold it back into "
            "[Unreleased].",
        )

    def test_highlights_the_card_will_render(self) -> None:
        """An [Unreleased] with bullets in it must yield highlights; an empty
        one - the state prepare_release.py leaves behind on a cut, with a fresh
        heading on top - must yield none, and version.json keeps the leads it
        was stamped with rather than blanking the card."""
        stamper = load_stamper()
        leads = stamper.changelog_highlights(self.text)
        bullets = [
            line
            for line in self.sections[0][1].splitlines()
            if line.startswith("- ")
        ]
        if not bullets:
            self.assertEqual(
                leads,
                [],
                "an empty [Unreleased] parsed highlights out of thin air: "
                f"{leads}",
            )
            return
        self.assertTrue(
            leads,
            f"{len(bullets)} top-level bullets in [Unreleased] and not one "
            "parsed as a highlight - the stamper needs `- **Lead.**`",
        )
        self.assertLessEqual(
            len(leads),
            8,
            f"{len(leads)} highlights: UpdateChecker renders eight before the "
            "update bar stops being a bar",
        )
        receipts = [x for x in leads if x.startswith("`")]
        self.assertEqual(
            receipts,
            [],
            "a gate or test file is a receipt, not a headline, and the stamper "
            f"is meant to skip it: {receipts}",
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
