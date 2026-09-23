#!/usr/bin/env python3
"""Build envelope regressions.

The point of this file is the mutant half. `tools/check_gradle_envelope.py`
exists because the AndroidX ignore rules in `dependabot.yml` were written once
(6434eabd), silently deleted when the file was restructured (9f5d266d), and the
four Dependabot PRs that followed all failed CI for the same three libraries.
A gate that nobody has watched fail is a gate nobody can trust, so each rule it
enforces is broken on purpose in a scratch copy of the repo here and the gate has
to notice.

Run: `python3 tests/test_gradle_envelope.py`
"""
from __future__ import annotations

import contextlib
import io
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import check_gradle_envelope as gate  # noqa: E402

# What the scratch repo needs: enough of the tree for the gate to walk it, and
# none of the ~50k icon assets it does not look at.
COPY_GLOBS = [
    "**/settings.gradle.kts",
    "**/build.gradle.kts",
    "**/gradle/wrapper/gradle-wrapper.properties",
    ".github/dependabot.yml",
    ".github/workflows/*.yml",
    "tools/gradle_envelope.json",
]
SKIP_PARTS = {"node_modules", "build", ".git", "dist", "out"}


def wrapper_on_disk(root_dir: Path) -> str:
    """The Gradle version a root's wrapper properties name, read independently.

    Deliberately not gate._wrapper_version: a test that compared the gate's
    parser with itself would pass whatever the parser did.
    """
    text = (root_dir / "gradle" / "wrapper" / "gradle-wrapper.properties").read_text()
    for line in text.splitlines():
        if line.startswith("distributionUrl="):
            return line.rsplit("/gradle-", 1)[1].rsplit("-", 1)[0]
    raise AssertionError(f"no distributionUrl in {root_dir}")


def snapshot(dest: Path) -> None:
    """Copy the build-relevant slice of the repo into `dest`."""
    for pattern in COPY_GLOBS:
        for src in sorted(ROOT.glob(pattern)):
            if not src.is_file():
                continue
            rel = src.relative_to(ROOT)
            if SKIP_PARTS & set(rel.parts):
                continue
            target = dest / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, target)


class ScratchRepo:
    """A disposable copy of the repo with the gate repointed at it.

    Used as a context manager so the module globals are always restored — the
    gate reads ROOT from module scope, and leaving it patched would poison every
    later test in the run.
    """

    def __enter__(self) -> "ScratchRepo":
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        snapshot(self.root)
        self._saved = {
            name: getattr(gate, name)
            for name in ("ROOT", "ENVELOPE", "DEPENDABOT", "SUITE_CI", "WORKFLOWS")
        }
        gate.ROOT = self.root
        gate.ENVELOPE = self.root / "tools" / "gradle_envelope.json"
        gate.DEPENDABOT = self.root / ".github" / "dependabot.yml"
        gate.SUITE_CI = self.root / ".github" / "workflows" / "suite-ci.yml"
        gate.WORKFLOWS = self.root / ".github" / "workflows"
        return self

    def __exit__(self, *exc) -> None:
        for name, value in self._saved.items():
            setattr(gate, name, value)
        self._tmp.cleanup()

    # -- mutation helpers ---------------------------------------------------

    def path(self, rel: str) -> Path:
        return self.root / rel

    def edit(self, rel: str, old: str, new: str, everywhere: bool = False) -> None:
        target = self.path(rel)
        text = target.read_text(encoding="utf-8")
        if old not in text:
            raise AssertionError(f"{rel} does not contain {old!r} — fixture has drifted")
        replaced = text.replace(old, new) if everywhere else text.replace(old, new, 1)
        target.write_text(replaced, encoding="utf-8")

    def envelope(self) -> dict:
        return json.loads(gate.ENVELOPE.read_text(encoding="utf-8"))

    def write_envelope(self, table: dict) -> None:
        gate.ENVELOPE.write_text(json.dumps(table, indent=2), encoding="utf-8")

    def run(self) -> tuple[int, str]:
        gate.problems.clear()
        gate.receipt.clear()
        # The gate prints a receipt; 13 mutants would make the test output
        # unreadable, so it goes to a buffer and only surfaces in a failure.
        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            code = gate.main()
        self.output = buffer.getvalue()
        return code, "\n".join(gate.problems)

    def assert_blocked(self, needle: str, context: str) -> None:
        # Raises rather than delegating to a TestCase: this helper is called from
        # inside a `with ScratchRepo()` block, and unittest reports a plain
        # AssertionError as a failure all the same.
        code, problems = self.run()
        if code != 1:
            raise AssertionError(f"{context}: expected the gate to fail, it passed\n{self.output}")
        if needle not in problems:
            raise AssertionError(
                f"{context}: the gate failed, but not for the expected reason "
                f"(wanted {needle!r}):\n{problems}"
            )


# ---------------------------------------------------------------------------
# version ordering
# ---------------------------------------------------------------------------

class VersionOrdering(unittest.TestCase):
    """Everything downstream depends on these comparing numerically."""

    def test_two_digit_components_are_not_lexicographic(self):
        # The single easiest way to get this wrong: "8.13.2" < "8.5.2" as text.
        self.assertTrue(gate.parse_version("8.13.2") > gate.parse_version("8.5.2"))
        self.assertTrue(gate.parse_version("1.19.0") > gate.parse_version("1.13.1"))
        self.assertTrue(gate.parse_version("2.4.20") > gate.parse_version("1.9.24"))

    def test_date_scheme_bom_orders_correctly(self):
        self.assertTrue(gate.parse_version("2026.09.00") > gate.parse_version("2024.06.00"))

    def test_prerelease_sits_below_its_release(self):
        self.assertTrue(gate.parse_version("1.4.0-alpha02") < gate.parse_version("1.4.0"))
        self.assertTrue(gate.parse_version("1.4.0-beta01") < gate.parse_version("1.4.0"))

    def test_a_bare_major_is_not_read_as_a_minor(self):
        # The rank element that puts alphas below releases would otherwise make
        # "9" parse as (9, 1) — i.e. 9.1 — and AGP 9.0.0 would slip under a
        # ">= 9" cap. Both the dependabot AGP rule and the envelope rely on this.
        self.assertFalse(gate.parse_version("9.0.0") < gate.parse_version("9"))
        self.assertTrue(gate.parse_version("8.13.2") < gate.parse_version("9"))
        self.assertTrue(gate.parse_version("9.1.0") > gate.parse_version("9"))

    def test_within_is_inclusive_at_the_ceiling(self):
        self.assertTrue(gate.within("1.13.1", "1.13.1"))
        self.assertFalse(gate.within("1.15.0", "1.13.1"))


# ---------------------------------------------------------------------------
# gradle parsing
# ---------------------------------------------------------------------------

class GradleParsing(unittest.TestCase):
    def setUp(self):
        self.roots = {r.rel: r for r in gate.discover_roots()}

    def test_finds_every_gradle_root_in_the_suite(self):
        self.assertEqual(
            set(self.roots),
            {"", "pixel-neon", "ticker/android", "shift", "motion-plugin", "doctor"},
        )

    def test_root_build_file_maps_to_two_modules(self):
        # The one deliberate exception to "one app, one Gradle root": pop/ is a
        # second module on the repo-root build, not its own root.
        self.assertEqual([m.name for m in self.roots[""].modules], ["app", "pop"])

    def test_reads_compile_and_min_sdk(self):
        app = self.roots[""].modules[0]
        self.assertEqual(app.compile_sdk, 34)
        self.assertEqual(app.min_sdk, 21)
        doctor = self.roots["doctor"].modules[0]
        self.assertEqual(doctor.compile_sdk, 35)

    def test_reads_the_toolchain_versions(self):
        for rel, root in self.roots.items():
            self.assertEqual(root.agp, "8.5.2", f"{rel} AGP")
            self.assertEqual(root.kotlin, "1.9.24", f"{rel} Kotlin")
        # Compared with the properties files themselves rather than literals:
        # the wrapper is a Dependabot-managed version, and a test that pins it
        # fails every wrapper bump for reasons that have nothing to do with the
        # gate (PR #160 went red on exactly this).
        for rel in ("ticker/android", "shift"):
            self.assertEqual(self.roots[rel].wrapper, wrapper_on_disk(ROOT / rel), rel)

    def test_dependencies_include_the_bom_and_test_configs(self):
        deps = self.roots["doctor"].modules[0].deps
        # `platform(...)` is not `implementation(...)`, and the BOM is the
        # coordinate that actually breaks doctor/ — missing it defeats the gate.
        self.assertIn("androidx.compose:compose-bom", deps)
        self.assertEqual(deps["androidx.compose:compose-bom"], "2024.06.00")
        self.assertEqual(deps["junit:junit"], "4.13.2")

    def test_sub_plugins_are_collected(self):
        self.assertIn("org.jetbrains.kotlin.plugin.serialization", self.roots["doctor"].plugin_deps)
        self.assertIn("org.jetbrains.kotlin.plugin.parcelize", self.roots["motion-plugin"].plugin_deps)

    def test_compose_wiring_is_visible(self):
        doctor = self.roots["doctor"].modules[0]
        self.assertTrue(doctor.compose_enabled)
        self.assertEqual(doctor.compose_extension, "1.5.14")
        self.assertFalse(self.roots[""].modules[0].compose_enabled)


class MatrixParsing(unittest.TestCase):
    def setUp(self):
        self.text = gate.read(gate.SUITE_CI)
        self.entries = [
            e for e in gate.parse_matrix_entries(self.text) if "gradle" in e and "workflow" in e
        ]

    def test_reads_exactly_the_matrix_entries(self):
        self.assertEqual(len(self.entries), 7)

    def test_step_names_are_not_mistaken_for_matrix_entries(self):
        # A whole-file search for `- name:` also matches every workflow step, and
        # a lazy match then pairs "Check suite truth" with a real gradle path.
        names = {e["name"] for e in self.entries}
        self.assertNotIn("Check suite truth", names)
        self.assertIn("Core Doctor", names)

    def test_every_entry_declares_a_compile_sdk(self):
        for entry in self.entries:
            self.assertIn("compileSdk", entry, f"{entry.get('name')} has no compileSdk")
            self.assertTrue(entry["compileSdk"].isdigit())


class DependabotParsing(unittest.TestCase):
    def setUp(self):
        self.entries = gate.parse_dependabot()
        self.gradle = {e.directory: e for e in self.entries if e.ecosystem == "gradle"}

    def test_reads_every_directory(self):
        self.assertEqual(
            set(self.gradle),
            {"/", "/pixel-neon", "/ticker/android", "/shift", "/motion-plugin", "/doctor"},
        )

    def test_non_gradle_ecosystems_survive(self):
        others = {e.directory: e.ecosystem for e in self.entries if e.ecosystem != "gradle"}
        self.assertEqual(others.get("/ticker"), "npm")
        self.assertIn("github-actions", {e.ecosystem for e in self.entries})

    def test_ignore_block_stops_at_the_next_key(self):
        # `groups:` sits at the same indent as `ignore:`. Reading past it would
        # swallow the group name as a dependency and hide a missing rule.
        ignores = self.gradle["/"].ignores
        self.assertNotIn("android-root", ignores)
        self.assertNotIn("patterns", ignores)

    def test_reads_both_ignore_shapes(self):
        # `versions:` is the toolchain shape (the wrapper rules); `update-types:`
        # is the ceiling shape. Both still have to parse.
        ignores = self.gradle["/"].ignores
        self.assertEqual(ignores["gradle-wrapper"], [">=9.6"])
        self.assertEqual(
            set(ignores["com.android.application"]),
            {
                "version-update:semver-patch",
                "version-update:semver-minor",
                "version-update:semver-major",
            },
        )
        self.assertEqual(
            set(ignores["androidx.core:core-ktx"]),
            {
                "version-update:semver-patch",
                "version-update:semver-minor",
                "version-update:semver-major",
            },
        )


# ---------------------------------------------------------------------------
# the committed envelope itself
# ---------------------------------------------------------------------------

class EnvelopeTable(unittest.TestCase):
    def setUp(self):
        self.table = json.loads((ROOT / "tools" / "gradle_envelope.json").read_text(encoding="utf-8"))
        self.roots = gate.discover_roots()
        self.declared = set()
        for root in self.roots:
            self.declared |= set(root.plugin_deps)
            for module in root.modules:
                self.declared |= set(module.deps)

    def test_every_ceiling_is_a_coordinate_the_suite_really_declares(self):
        # A cap on something nobody uses is worse than no cap: it silently stops
        # Dependabot proposing updates for a library, and reads as if the suite
        # were pinned by a decision nobody can find.
        dead = [c["coordinate"] for c in self.table["ceilings"] if c["coordinate"] not in self.declared]
        self.assertEqual(dead, [], f"ceilings with no declaring root: {dead}")

    def test_every_ceiling_says_why_and_cites_something(self):
        for ceiling in self.table["ceilings"]:
            coord = ceiling["coordinate"]
            self.assertGreater(len(ceiling["reason"]), 40, f"{coord} reason is too thin to act on")
            self.assertTrue(ceiling["evidence"], f"{coord} has no evidence link")
            # Patch included: every ceiling equals the version declared today,
            # and the gate rejects anything above a ceiling whichever digit
            # moved — so ignoring only minor+major leaves a live red-PR lane
            # (Kotlin 1.9.24 -> 1.9.25 is a patch).
            self.assertEqual(
                set(ceiling["blockedUpdateTypes"]),
                {
                    "version-update:semver-patch",
                    "version-update:semver-minor",
                    "version-update:semver-major",
                },
                f"{coord} must block patch+major+minor — the gate rejects a patch above the cap too",
            )

    def test_no_ceiling_is_below_what_the_suite_declares_today(self):
        # A cap tighter than the committed version would fail the gate on main.
        for root in self.roots:
            ceilings = {c["coordinate"]: c for c in self.table["ceilings"]}
            versions = dict(root.plugin_deps)
            for module in root.modules:
                versions.update(module.deps)
            for coord, version in versions.items():
                ceiling = ceilings.get(coord)
                if ceiling is None:
                    continue
                self.assertTrue(
                    gate.within(version, ceiling["max"]),
                    f"{root.label}: {coord} {version} is above its own ceiling {ceiling['max']}",
                )

    def test_migration_path_is_recorded(self):
        steps = self.table["migration"]["steps"]
        self.assertGreaterEqual(len(steps), 4)
        self.assertTrue(steps[0].lower().startswith("decide"))


# ---------------------------------------------------------------------------
# the repo as it stands
# ---------------------------------------------------------------------------

class RepoIsInsideItsEnvelope(unittest.TestCase):
    def test_the_gate_passes(self):
        gate.problems.clear()
        gate.receipt.clear()
        with contextlib.redirect_stdout(io.StringIO()):
            code = gate.main()
        self.assertEqual(code, 0, "build envelope gate failed:\n" + "\n".join(gate.problems))

    def test_dependabot_yml_matches_its_generator(self):
        import build_dependabot

        self.assertEqual(
            gate.DEPENDABOT.read_text(encoding="utf-8"),
            build_dependabot.render(),
            "dependabot.yml has drifted — run: python tools/build_dependabot.py",
        )

    def test_min_sdk_21_modules_are_the_reason_appcompat_is_capped(self):
        table = json.loads((ROOT / "tools" / "gradle_envelope.json").read_text(encoding="utf-8"))
        appcompat = next(c for c in table["ceilings"] if c["coordinate"] == "androidx.appcompat:appcompat")
        self.assertIn("minSdk", appcompat["reason"])
        at_21 = sorted(
            m.path.relative_to(ROOT).as_posix()
            for r in gate.discover_roots()
            for m in r.modules
            if m.min_sdk == 21
        )
        self.assertEqual(at_21, ["app", "pixel-neon/app", "pop"])

    def test_doctor_is_the_only_compose_module(self):
        composing = [
            m.path.relative_to(ROOT).as_posix()
            for r in gate.discover_roots()
            for m in r.modules
            if m.compose_enabled
        ]
        self.assertEqual(composing, ["doctor/app"])


# ---------------------------------------------------------------------------
# mutants — each one breaks a rule the gate claims to enforce
# ---------------------------------------------------------------------------

class Mutants(unittest.TestCase):
    def test_control_scratch_copy_passes(self):
        with ScratchRepo() as repo:
            code, problems = repo.run()
            self.assertEqual(code, 0, f"scratch copy of a green repo should pass:\n{problems}")

    def test_bumping_core_ktx_past_its_ceiling_is_caught(self):
        with ScratchRepo() as repo:
            repo.edit("app/build.gradle.kts", "core-ktx:1.13.1", "core-ktx:1.19.0")
            repo.assert_blocked("core-ktx:1.19.0", "core-ktx above ceiling")

    def test_dropping_one_ignore_rule_is_caught(self):
        # This is the actual 9f5d266d regression: the rules vanish from the YAML
        # and nothing notices until four PRs go red.
        with ScratchRepo() as repo:
            repo.edit(
                ".github/dependabot.yml",
                '      - dependency-name: "androidx.core:core-ktx"\n'
                '        update-types: ["version-update:semver-patch", '
                '"version-update:semver-minor", "version-update:semver-major"]\n',
                "",
            )
            repo.assert_blocked("does not ignore androidx.core:core-ktx", "dropped ignore rule")

    def test_a_new_gradle_root_without_a_dependabot_entry_is_caught(self):
        with ScratchRepo() as repo:
            text = repo.path(".github/dependabot.yml").read_text(encoding="utf-8")
            start = text.index('  # pixel-neon')
            end = text.index("  # ticker/android")
            repo.path(".github/dependabot.yml").write_text(text[:start] + text[end:], encoding="utf-8")
            repo.assert_blocked("no dependabot.yml entry", "unwatched gradle root")

    def test_a_suite_ci_platform_that_lies_is_caught(self):
        with ScratchRepo() as repo:
            repo.edit(
                ".github/workflows/suite-ci.yml",
                "gradle: doctor/app/build.gradle.kts\n            compileSdk: 35",
                "gradle: doctor/app/build.gradle.kts\n            compileSdk: 34",
            )
            repo.assert_blocked("compiles against 35", "matrix compileSdk disagrees with the build file")

    def test_hard_coding_the_platform_install_again_is_caught(self):
        # The regression this replaced: one platform installed for all seven
        # matrix entries, including the one that compiles against a newer API.
        with ScratchRepo() as repo:
            repo.edit(
                ".github/workflows/suite-ci.yml",
                '"platforms;android-${{ matrix.compileSdk }}"',
                '"platforms;android-34"',
            )
            repo.assert_blocked("hard-coded Android platform", "platform install no longer follows the matrix")

    DOCTOR_ROOT_PLUGINS_OLD = (
        '    id("org.jetbrains.kotlin.android") version "1.9.24" apply false\n'
        '    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false\n'
    )
    DOCTOR_ROOT_PLUGINS_K2 = (
        '    id("org.jetbrains.kotlin.android") version "2.4.20" apply false\n'
        '    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false\n'
    )

    # Not a prefix test: "org.jetbrains.kotlinx:..." also starts with
    # "org.jetbrains.kotlin", and silently lifting the kotlinx ceilings too would
    # let this mutant pass for the wrong reason.
    KOTLIN_PLUGIN_CEILINGS = (
        "org.jetbrains.kotlin.android",
        "org.jetbrains.kotlin.plugin.serialization",
        "org.jetbrains.kotlin.plugin.parcelize",
    )

    def _lift_kotlin_ceilings(self, repo: "ScratchRepo") -> None:
        """Pretend the migration was decided on, so only the wiring is under test."""
        table = repo.envelope()
        lifted = 0
        for ceiling in table["ceilings"]:
            if ceiling["coordinate"] in self.KOTLIN_PLUGIN_CEILINGS:
                ceiling["max"] = "2.4.20"
                lifted += 1
        self.assertEqual(lifted, len(self.KOTLIN_PLUGIN_CEILINGS), "ceiling table has drifted")
        repo.write_envelope(table)

    def test_kotlin_2_with_the_old_compose_dsl_is_caught(self):
        # PR #126's shape: Kotlin 2.4.20 while doctor/ still pins the
        # pre-2.0 Compose compiler through composeOptions.
        with ScratchRepo() as repo:
            repo.edit("doctor/build.gradle.kts", self.DOCTOR_ROOT_PLUGINS_OLD, self.DOCTOR_ROOT_PLUGINS_K2)
            self._lift_kotlin_ceilings(repo)
            code, problems = repo.run()
            self.assertEqual(code, 1, "Kotlin 2.x with composeOptions still set must fail")
            self.assertIn("org.jetbrains.kotlin.plugin.compose", problems)
            self.assertIn("kotlinCompilerExtensionVersion", problems)

    def test_kotlin_2_with_the_plugin_applied_is_allowed(self):
        # The same migration, done properly: plugin declared in the root, applied
        # in the module, composeOptions deleted. The gate must not be a permanent
        # veto — it has to go green once the wiring is right, or it will be
        # deleted the first time it gets in someone's way.
        with ScratchRepo() as repo:
            repo.edit(
                "doctor/build.gradle.kts",
                self.DOCTOR_ROOT_PLUGINS_OLD,
                self.DOCTOR_ROOT_PLUGINS_K2
                + '    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false\n',
            )
            repo.edit(
                "doctor/app/build.gradle.kts",
                'id("org.jetbrains.kotlin.plugin.serialization")\n',
                'id("org.jetbrains.kotlin.plugin.serialization")\n'
                '    id("org.jetbrains.kotlin.plugin.compose")\n',
            )
            repo.edit(
                "doctor/app/build.gradle.kts",
                '    composeOptions {\n        kotlinCompilerExtensionVersion = "1.5.14"\n    }\n\n',
                "",
            )
            self._lift_kotlin_ceilings(repo)
            code, problems = repo.run()
            self.assertEqual(code, 0, f"a correctly migrated Kotlin 2.x should pass:\n{problems}")

    def test_a_kotlin_sub_plugin_out_of_lockstep_is_caught(self):
        with ScratchRepo() as repo:
            repo.edit(
                "motion-plugin/build.gradle.kts",
                'id("org.jetbrains.kotlin.plugin.parcelize") version "1.9.24"',
                'id("org.jetbrains.kotlin.plugin.parcelize") version "1.9.25"',
            )
            repo.assert_blocked("must match Kotlin", "parcelize out of lockstep")

    def test_agp_9_is_caught(self):
        with ScratchRepo() as repo:
            repo.edit("build.gradle.kts", 'version "8.5.2"', 'version "9.0.0"')
            repo.assert_blocked("breaking major", "AGP 9")

    def test_an_agp_8x_bump_on_the_9_7_0_wrapper_is_caught(self):
        # The 2026-09-19 batch (#144 #145 #146 #147 #148 #149) proposed exactly
        # this and every root that kept the 9.7.0 wrapper failed at plugin
        # apply. It is a ceiling, not a warning: Dependabot must stop being
        # offered the combination at all.
        with ScratchRepo() as repo:
            repo.edit("build.gradle.kts", 'version "8.5.2"', 'version "8.13.2"')
            repo.assert_blocked("above its ceiling 8.5.2", "AGP 8.13.2 on the 9.7.0 wrapper")

    def test_a_wrapper_below_the_agp_floor_is_caught(self):
        with ScratchRepo() as repo:
            props = "ticker/android/gradle/wrapper/gradle-wrapper.properties"
            repo.edit(
                props,
                f"gradle-{wrapper_on_disk(repo.root / 'ticker/android')}-bin.zip",
                "gradle-8.2-bin.zip",
            )
            repo.assert_blocked("below 8.7", "Gradle under the AGP 8.x floor")

    def test_a_dead_ceiling_is_caught(self):
        with ScratchRepo() as repo:
            table = repo.envelope()
            table["ceilings"].append({
                "coordinate": "com.example:unused",
                "max": "1.0.0",
                "blockedUpdateTypes": [
                    "version-update:semver-minor",
                    "version-update:semver-major",
                ],
                "reason": "A ceiling on something the suite does not declare.",
                "evidence": ["https://example.com"],
            })
            repo.write_envelope(table)
            repo.assert_blocked("no Gradle root declares it", "dead ceiling")

    def test_a_release_workflow_with_the_wrong_platform_is_caught(self):
        with ScratchRepo() as repo:
            # core-doctor-apk.yml installs the platform in both of its jobs, so
            # both have to be downgraded — the gate unions the platforms a
            # workflow installs, and one job left at 35 would mask the other.
            repo.edit(
                ".github/workflows/core-doctor-apk.yml",
                '"platforms;android-35" "build-tools;35.0.0"',
                '"platforms;android-34" "build-tools;34.0.0"',
                everywhere=True,
            )
            repo.assert_blocked("installs only", "doctor workflow platform mismatch")


if __name__ == "__main__":
    unittest.main(verbosity=2)
