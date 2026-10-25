#!/usr/bin/env python3
"""A unit-test task a workflow names has to be a task that exists.

Why this exists
---------------
AGP 9.0 changed `android.onlyEnableUnitTestForTheTestedBuildType` from false to
true: "Only creates unit test components for the tested build type. In the
default project this results in a single unit test for debug, where the
previous behaviour was to have unit tests run for debug or release."

Every workflow that named a non-debug unit-test task therefore stopped
working at task selection, which fails before anything is compiled:

    * What went wrong:
    Selection failed
      Cannot locate tasks that match ':app:testBannersCandidateUnitTest' as
      task 'testBannersCandidateUnitTest' not found in project ':app'.

That is the icon pack test channel on 2026-10-24, the first dispatch after this
root moved to AGP 9.4.0: no error from a test, no error from the code, just a
task name that no longer refers to anything. The failure is also quiet in the
worst way - it looks like a broken build of whatever was being released, so the
obvious next move is to distrust the source instead of the toolchain.

Only roots on AGP 9 are affected: AGP 8 creates every `test<Variant>UnitTest`.
This root (app + glyphs) is on AGP 9; shift/, doctor/, motion-plugin/ and
ticker/android/ are still on 8.5.2, so the test asks for the opt-out only where
the toolchain needs it.

So this test reads the workflows, finds every `test<Variants>UnitTest` task they
ask Gradle for, and requires the opt-out in the gradle.properties of the root
that would build it. It is deliberately blunt about the mapping - a root is
implicated if any workflow that names a non-debug test task also names the
root's directory (`DIR=shift`, `working-directory: shift`, `cd shift`), or if
the task is asked for from the repository root.

Run: `python3 tests/test_gradle_unit_test_variants.py`
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = sorted((ROOT / ".github" / "workflows").glob("*.yml"))

PROPERTY = "android.onlyEnableUnitTestForTheTestedBuildType"

# `:app:testBannersCandidateUnitTest`, `testReleaseUnitTest`, `testDebugUnitTest`.
TASK = re.compile(r"\btest([A-Za-z0-9]+?)UnitTest\b")

# The build type is the last CamelCase word of the variant: BannersCandidate,
# BannersRelease, Debug, Release.
DEBUG = "Debug"


def roots() -> list[str]:
    """Every separate Gradle root, by path relative to the repo ('.' first)."""
    found = ["."]
    for settings in sorted(ROOT.glob("*/settings.gradle*")) + sorted(
            ROOT.glob("*/*/settings.gradle*")):
        rel = settings.parent.relative_to(ROOT).as_posix()
        if rel not in found:
            found.append(rel)
    return found


def variants(text: str) -> set[str]:
    """The variant names of every unit-test task a piece of text names."""
    return {m.group(1) for m in TASK.finditer(text)}


def non_debug_variants(text: str) -> set[str]:
    return {v for v in variants(text) if not v.endswith(DEBUG)}


def agp_major(root: str) -> int | None:
    """The Android Gradle plugin major version that root declares."""
    for name in ("build.gradle.kts", "build.gradle"):
        path = ROOT / root / name
        if not path.is_file():
            continue
        m = re.search(r'com\.android\.(?:application|library)"\)\s*version\s*"([0-9]+)',
                      path.read_text(encoding="utf-8"))
        if m:
            return int(m.group(1))
    return None


def property_value(root: str) -> str | None:
    """The property as set in that root's gradle.properties, or None."""
    path = ROOT / root / "gradle.properties"
    if not path.is_file():
        return None
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line.startswith(PROPERTY + "="):
            return line.split("=", 1)[1].strip()
    return None


def mentions(text: str, root: str) -> bool:
    """Whether a workflow touches this root at all."""
    if root == ".":
        return True
    return bool(re.search(rf"(?<![\w./-]){re.escape(root)}(?![\w/-])", text))


class UnitTestVariantsExist(unittest.TestCase):
    def test_the_agp_split_is_what_the_comment_says(self) -> None:
        """The gate only asks the AGP 9 roots, so which ones those are has to
        be a fact the test checks rather than a claim in a comment."""
        on_nine = {r for r in roots() if (agp_major(r) or 0) >= 9}
        self.assertEqual({".", "coreeq"}, on_nine)

    def test_roots_are_discovered(self) -> None:
        found = roots()
        # app+glyphs share the root; the rest are standalone.
        for expected in (".", "coreeq", "doctor", "motion-plugin", "shift",
                         "ticker/android"):
            self.assertIn(expected, found, f"{expected} is not a Gradle root")

    def test_every_named_unit_test_task_has_a_root_that_creates_it(self) -> None:
        problems: list[str] = []
        implicated: set[str] = set()
        for workflow in WORKFLOWS:
            text = workflow.read_text(encoding="utf-8")
            wanted = non_debug_variants(text)
            if not wanted:
                continue
            for root in roots():
                if not mentions(text, root):
                    continue
                implicated.add(root)
                if (agp_major(root) or 0) < 9:
                    continue        # AGP 8 creates the task on its own
                value = property_value(root)
                if value != "false":
                    problems.append(
                        f"{workflow.name} asks Gradle for "
                        f"{', '.join('test' + v + 'UnitTest' for v in sorted(wanted))}"
                        f" but {root}/gradle.properties sets {PROPERTY}"
                        f"={'absent' if value is None else value}. AGP 9 only creates"
                        " a unit-test component for the tested build type (debug),"
                        " so the task does not exist and the build fails at task"
                        " selection. Set it to false there.")
        self.assertTrue(implicated, "no workflow names a non-debug unit-test task")
        self.assertEqual([], problems, "\n".join(problems))

    def test_the_icon_pack_test_channel_names_the_candidate_build_type(self) -> None:
        """The incident, pinned: the test channel builds the `candidate` build
        type, which is neither debug nor release, so it needs the opt-out too."""
        text = (ROOT / ".github/workflows/iconpack-test-apk.yml").read_text(
            encoding="utf-8")
        self.assertIn("testBannersCandidateUnitTest", text)
        self.assertEqual("false", property_value("."))

    def test_the_property_is_never_set_true(self) -> None:
        """`true` is AGP 9's default, and writing it would read as a decision
        while behaving exactly like not having written it."""
        for root in roots():
            self.assertNotEqual(
                "true", property_value(root),
                f"{root}/gradle.properties sets {PROPERTY}=true; that is the AGP 9"
                " default and only restates the bug")


if __name__ == "__main__":
    unittest.main(verbosity=2)
