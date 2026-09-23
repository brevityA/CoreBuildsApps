#!/usr/bin/env python3
"""Every gate in the repo must be wired into CI, or excused in writing here.

Why this exists
---------------
The 1.8.22 cycle wrote two gates - `tests/test_navigation_graph.py` (no dead
buttons, no orphan screens, no focus edges into the void) and
`tests/test_tv_scale.py` (the dp box normalises per panel) - ran them locally,
watched them pass, and shipped. Neither was in any workflow. The same was true
of `tools/check_ui_resources.py`, which has guarded resource references and
focus chains since 1.8.11, and of `tools/validate_motion_feed.py`.

The reason is structural, and it is the reason a reviewer cannot catch it by
reading a diff: `.github/workflows/build.yml` and the suite gate both list test
files by name, one `python tests/x.py` line each. A new test file lands in
`tests/`, `pytest` picks it up locally, and no workflow notices it exists. The
gate is then a local habit rather than a property of the repository - it runs
for whoever remembers to run it, which is nobody after a hand-off.

A gate nobody has watched fail in CI is a gate nobody can trust. So this file
is the meta-gate: it reads the workflows and asserts that everything in
`tests/` and every checker/validator in `tools/` is actually invoked
somewhere, that the mockup check runs in `--check` mode rather than
regenerating (which cannot fail), and that a workflow whose gates read all
three app modules also *triggers* on all three - `build.yml` gates `pop/` and
`pixel-neon/` resources while only watching `app/**`, which is how today's
Pixel Neon-only fix would have reached main without running a single check
against it.

Run: `python3 tests/test_ci_coverage.py`
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = sorted((ROOT / ".github" / "workflows").glob("*.yml"))

# Tools that are deliberately not CI gates, with the reason. Anything a human
# has to look at, or that takes a subject as an argument, belongs here rather
# than in a workflow that would pass by measuring nothing.
LOCAL_ONLY: dict[str, str] = {
    "check_glyph.py": (
        "interactive: renders one named glyph for a human eye, and every "
        "measurement it makes (safe area, rendered alpha, monoline contract) "
        "is already a CI gate against the shipped assets"
    ),
}

# The repo-truth gates. Each has caught a real defect before; each must be
# reachable from a push to main or a pull request, not only from a laptop.
REQUIRED_TOOLS = (
    "tools/check_suite_truth.py",
    "tools/check_appfilter_integrity.py",
    "tools/check_ui_resources.py",
    "tools/validate.py",
    "tools/validate_pop.py",
    "tools/validate_banners.py",
    "tools/validate_pixel_neon.py",
    "tools/validate_motion.py",
    "tools/validate_motion_feed.py",
    "tools/audit_contract.py",
    "tools/check_gradle_envelope.py",
    "tools/build_dependabot.py",
    "tools/build_issue_prefills.py",
    "tools/build_pop.py",
    "tools/build_banner_pack.py",
    "tools/build_pixel_neon.py",
)

# `build.yml` runs gates that read app/, pop/ and pixel-neon/ resources, so its
# path filter has to trigger on all three (plus docs/, where the generated UI
# mockups live and `--check` compares them). suite-ci.yml has no filter at all
# and therefore needs no entry here.
MODULE_WIDE = {
    "build.yml": {
        "app/**",
        "pop/**",
        "banners/**",
        "pixel-neon/**",
        "docs/**",
        "tests/**",
        "tools/**",
    },
}


def workflow_text() -> dict[str, str]:
    return {p.name: p.read_text(encoding="utf-8") for p in WORKFLOWS}


def invocations(text: str) -> set[str]:
    """Every `python[3] <path>` the workflows actually run."""
    return set(re.findall(r"python3?\s+(?:-\S+\s+)*([\w./-]+\.py)", text))


class CiCoverage(unittest.TestCase):
    def setUp(self) -> None:
        self.texts = workflow_text()
        self.all = "\n".join(self.texts.values())
        self.invoked = invocations(self.all)

    def test_workflows_exist(self) -> None:
        self.assertTrue(WORKFLOWS, "no workflows under .github/workflows/")

    def test_every_test_file_is_run_in_ci(self) -> None:
        """A test in tests/ that no workflow names is a test that only I ran."""
        tests = sorted(p.name for p in (ROOT / "tests").glob("test_*.py"))
        self.assertGreater(len(tests), 15, "test discovery looks broken")
        missing = [t for t in tests if f"tests/{t}" not in self.invoked]
        self.assertEqual(
            missing,
            [],
            "these tests are never invoked by any workflow, so they pass on a "
            f"laptop and not on main: {missing}. Add a `python tests/<name>.py` "
            "line to .github/workflows/suite-ci.yml (the gate with no path "
            "filter, so it runs on every push and PR).",
        )

    def test_repo_truth_gates_are_run_in_ci(self) -> None:
        missing = [t for t in REQUIRED_TOOLS if t not in self.invoked]
        self.assertEqual(
            missing, [], f"repo-truth gates never invoked by CI: {missing}"
        )

    def test_mockup_check_runs_in_check_mode(self) -> None:
        """`--check` compares committed frames; without it the step regenerates
        them and can only ever pass."""
        self.assertIn("build_app_ui_mockups.py --check", self.all)
        self.assertNotIn(
            "python tools/build_app_ui_mockups.py\n",
            self.all,
            "the mockup generator is invoked without --check somewhere: that "
            "step rewrites docs/ instead of verifying it",
        )

    def test_every_checker_and_validator_is_wired_or_excused(self) -> None:
        """tools/check_*.py and tools/validate*.py are gates by naming
        convention; each is either invoked by CI or excused above, in writing."""
        names = sorted(
            p.name
            for p in (ROOT / "tools").glob("*.py")
            if p.name.startswith(("check_", "validate"))
        )
        self.assertGreater(len(names), 8, "tool discovery looks broken")
        orphans = []
        for name in names:
            wired = f"tools/{name}" in self.invoked
            if not wired and name not in LOCAL_ONLY:
                orphans.append(name)
        self.assertEqual(
            orphans,
            [],
            "these gate-shaped tools run in no workflow and carry no written "
            f"excuse: {orphans}. Wire them into suite-ci.yml, or add them to "
            "LOCAL_ONLY in this file with the reason.",
        )
        stale = [n for n in LOCAL_ONLY if f"tools/{n}" in self.invoked]
        self.assertEqual(
            stale,
            [],
            f"excused in LOCAL_ONLY but actually wired into CI now: {stale} - "
            "drop the excuse",
        )

    def test_every_gate_is_visible_to_both_runners(self) -> None:
        """CI runs `python tests/x.py`; a local sweep runs `pytest tests/`.

        A file that is only a script with a `main()` - no `unittest.TestCase`,
        no `def test_*` - is collected by nothing under pytest, so the suite
        reports green without running it. `test_ui_wiring.py` and
        `test_icon_uniformity.py` were both like that, which is how the
        wallpapers focus chain drifted out of date under a passing local suite
        and only failed on the release PR's first CI run.
        """
        invisible = []
        for path in sorted((ROOT / "tests").glob("test_*.py")):
            src = path.read_text(encoding="utf-8")
            if not re.search(r"class \w+\(unittest\.TestCase\)", src) and not re.search(
                    r"(?m)^def test_", src):
                invisible.append(path.name)
        self.assertEqual(
            invisible,
            [],
            f"pytest collects nothing from {invisible}, so a local sweep "
            "passes without running them: wrap the gate in a "
            "unittest.TestCase that calls the same collector main() uses",
        )

    def test_module_wide_gates_trigger_on_every_module(self) -> None:
        """A gate that reads pop/ and pixel-neon/ is worthless in a workflow
        that only triggers on app/**."""
        for name, required in MODULE_WIDE.items():
            text = self.texts.get(name)
            self.assertIsNotNone(text, f"{name} vanished from .github/workflows/")
            # Quoted list items in these files are the `paths:` filters; the
            # other lists (branches, tags, files) are inline or unquoted.
            listed = set(re.findall(r"^\s*-\s*'([^']+)'", text, re.M))
            self.assertTrue(listed, f"{name}: found no path filter to check")
            self.assertEqual(
                required - listed,
                set(),
                f"{name} runs module-wide gates but does not trigger on "
                f"{sorted(required - listed)}, so a change confined to those "
                "paths reaches main unchecked",
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
