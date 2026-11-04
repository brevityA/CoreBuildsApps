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
import struct
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import check_gradle_envelope as gate  # noqa: E402
import extract_launcher_gradle_diagnostics as launcher_diagnostics  # noqa: E402

# What the scratch repo needs: enough of the tree for the gate to walk it, and
# none of the ~50k icon assets it does not look at.
COPY_GLOBS = [
    "**/settings.gradle.kts",
    "**/build.gradle.kts",
    "**/gradle.properties",
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
        # "10" parse as (10, 1) — i.e. 10.1 — and let the next breaking major
        # through an exclusive 10 cap.
        self.assertFalse(gate.parse_version("10.0.0") < gate.parse_version("10"))
        self.assertTrue(gate.parse_version("9.4.0") < gate.parse_version("10"))
        self.assertTrue(gate.parse_version("10.1.0") > gate.parse_version("10"))

    def test_within_is_inclusive_at_the_ceiling(self):
        self.assertTrue(gate.within("1.13.1", "1.13.1"))
        self.assertFalse(gate.within("1.15.0", "1.13.1"))


# ---------------------------------------------------------------------------
# gradle parsing
# ---------------------------------------------------------------------------

class GradleParsing(unittest.TestCase):
    def setUp(self):
        self.roots = {r.rel: r for r in gate.discover_roots()}

    def test_finds_every_gradle_root_in_the_repository(self):
        self.assertEqual(
            set(self.roots),
            {"", "ticker/android", "shift", "motion-plugin", "doctor", "coreeq", "launcher"},
        )

    def test_root_build_file_maps_to_two_modules(self):
        # The deliberate exception to "one app, one Gradle root": glyphs/
        # (the icon pack's square companion) is a further module on the
        # repo-root build, not a root of its own.
        self.assertEqual([m.name for m in self.roots[""].modules],
                         ["app", "glyphs"])

    def test_every_module_compiles_and_targets_api_37(self):
        for root in self.roots.values():
            for module in root.modules:
                self.assertEqual(module.compile_sdk, 37, module.path)
                self.assertEqual(module.target_sdk, 37, module.path)
        self.assertEqual(self.roots[""].modules[0].min_sdk, 21)
        self.assertEqual(self.roots["coreeq"].modules[0].min_sdk, 30)
        self.assertEqual(self.roots["launcher"].modules[0].min_sdk, 26)

    def test_reads_the_toolchain_versions(self):
        for rel, root in self.roots.items():
            self.assertEqual(root.agp, "9.4.0", f"{rel} AGP")
            self.assertEqual(root.wrapper, "9.7.0", f"{rel} wrapper")
            if rel == "motion-plugin":
                self.assertEqual(root.kotlin, "2.4.10")
                self.assertFalse(root.builtin_kotlin)
                self.assertRegex(root.properties, r"(?m)^android\.builtInKotlin=false$")
                self.assertRegex(root.properties, r"(?m)^android\.newDsl=false$")
            else:
                self.assertIsNone(root.kotlin, f"{rel} should use AGP built-in Kotlin")
                self.assertTrue(root.builtin_kotlin)
        self.assertEqual(
            self.roots["doctor"].kotlin_plugins["org.jetbrains.kotlin.plugin.serialization"],
            "2.2.10",
        )
        self.assertEqual(
            self.roots["doctor"].kotlin_plugins["org.jetbrains.kotlin.plugin.compose"],
            "2.2.10",
        )

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
        self.assertIsNone(doctor.compose_extension)
        self.assertIn("org.jetbrains.kotlin.plugin.compose", self.roots["doctor"].kotlin_plugins)
        self.assertFalse(self.roots[""].modules[0].compose_enabled)


class MatrixParsing(unittest.TestCase):
    def setUp(self):
        self.text = gate.read(gate.SUITE_CI)
        self.entries = [
            e for e in gate.parse_matrix_entries(self.text) if "gradle" in e and "workflow" in e
        ]

    def test_reads_exactly_the_matrix_entries(self):
        # Named, not merely counted: a bare count still passes if the parser
        # picks up a workflow step and drops a real entry. Seven since the
        # :app module's glyphs flavor (tv.corebuilds.glyphs) got an entry of
        # its own, so the standalone glyph app is compiled, linted and unit
        # tested on every PR and not only when a release is tagged. Core EQ is
        # the eighth: its own Gradle root on AGP 9.4.0 / API 37, wired here when
        # its updater landed so the suite gate compiles and tests it too.
        self.assertEqual(
            {e["name"] for e in self.entries},
            {"Icon Pack", "Core Builds Glyphs app", "Core Builds Glyphs",
             "Core Line", "Core Shift", "Core Doctor", "Core Motion plugin",
             "Core EQ"},
        )
        self.assertEqual(len(self.entries), 8)

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
            {"/", "/ticker/android", "/shift", "/motion-plugin", "/doctor", "/coreeq", "/launcher"},
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
            # (Kotlin sub-plugin 2.2.10 -> 2.2.11 is a patch).
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
                directory = "/" + root.rel if root.rel else "/"
                maximum = ceiling.get("maxByDirectory", {}).get(directory, ceiling["max"])
                self.assertTrue(
                    gate.within(version, maximum),
                    f"{root.label}: {coord} {version} is above its own ceiling {maximum}",
                )

    def test_agp9_toolchain_covers_every_root(self):
        agp = next(c for c in self.table["ceilings"] if c["coordinate"] == "com.android.application")
        self.assertEqual(agp["max"], "9.4.0")
        self.assertEqual(self.table["envelope"]["minGradleForAgp9"], "9.6.0")
        self.assertEqual(self.table["envelope"]["builtinKotlinVersionByAgp"]["9.4.0"], "2.2.10")
        self.assertIn("next suite-wide Android toolchain migration", agp["reason"])
        self.assertIn(
            "next suite-wide Android toolchain migration",
            agp["reasonByDirectory"]["/launcher"],
        )
        for root in self.roots:
            self.assertEqual(root.agp, "9.4.0", root.label)
            self.assertGreaterEqual(
                gate.parse_version(root.wrapper),
                gate.parse_version("9.6.0"),
                f"{root.label} is on AGP 9 but its wrapper is below the 9.6.0 floor",
            )

    def test_completed_migration_and_maintenance_boundaries_are_recorded(self):
        steps = self.table["migration"]["steps"]
        self.assertGreaterEqual(len(steps), 4)
        self.assertIn("target API 37", steps[0])
        self.assertIn("AGP 9.4.0", steps[1])


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
        # The modules that both floor at 21 and declare appcompat. glyphs/ is
        # also minSdk 21 but has no dependencies at all, so it is not part of
        # why the cap exists.
        at_21 = sorted(
            m.path.relative_to(ROOT).as_posix()
            for r in gate.discover_roots()
            for m in r.modules
            if m.min_sdk == 21 and "androidx.appcompat:appcompat" in m.deps
        )
        self.assertEqual(at_21, ["app"])

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
            start = text.index('  # shift')
            end = text.index("  # motion-plugin")
            repo.path(".github/dependabot.yml").write_text(text[:start] + text[end:], encoding="utf-8")
            repo.assert_blocked("no dependabot.yml entry", "unwatched gradle root")

    def test_a_suite_ci_platform_that_lies_is_caught(self):
        with ScratchRepo() as repo:
            repo.edit(
                ".github/workflows/suite-ci.yml",
                "gradle: doctor/app/build.gradle.kts\n            compileSdk: 37",
                "gradle: doctor/app/build.gradle.kts\n            compileSdk: 36",
            )
            repo.assert_blocked("declares compileSdk 36 but", "matrix compileSdk disagrees with the build file")

    def test_hard_coding_the_platform_install_again_is_caught(self):
        # The regression this replaced: one platform installed for all seven
        # matrix entries, including the one that compiles against a newer API.
        with ScratchRepo() as repo:
            repo.edit(
                ".github/workflows/suite-ci.yml",
                '"platforms;android-${{ matrix.platformId || matrix.compileSdk }}"',
                '"platforms;android-34"',
            )
            repo.assert_blocked("hard-coded Android platform", "platform install no longer follows the matrix")

    def test_a_platform_id_that_is_not_the_compile_sdk_is_caught(self):
        # The 37 entry states its package id as platformId because API 37
        # carries a minor version. That escape hatch must not let a job install
        # somebody else's platform while declaring compileSdk 37.
        with ScratchRepo() as repo:
            repo.edit(
                ".github/workflows/suite-ci.yml",
                "compileSdk: 37\n            platformId: 37.0",
                "compileSdk: 37\n            platformId: 36.1",
            )
            repo.assert_blocked("not a minor version of its compileSdk", "platformId drifts from compileSdk")

    def test_agp9_rejects_external_kotlin_android(self):
        with ScratchRepo() as repo:
            repo.edit(
                "doctor/build.gradle.kts",
                '    id("com.android.application") version "9.4.0" apply false\n',
                '    id("com.android.application") version "9.4.0" apply false\n'
                '    id("org.jetbrains.kotlin.android") version "2.2.10" apply false\n',
            )
            repo.assert_blocked("has built-in Kotlin; remove the external", "duplicate Kotlin plugin")

    def test_motion_external_kotlin_requires_the_explicit_builtin_opt_out(self):
        with ScratchRepo() as repo:
            repo.edit(
                "motion-plugin/gradle.properties",
                "android.builtInKotlin=false",
                "android.builtInKotlin=true",
            )
            repo.assert_blocked("external Kotlin is permitted only when gradle.properties", "Motion Kotlin opt-out")

    def test_motion_external_kotlin_requires_the_legacy_android_dsl(self):
        with ScratchRepo() as repo:
            repo.edit(
                "motion-plugin/gradle.properties",
                "android.newDsl=false",
                "android.newDsl=true",
            )
            repo.assert_blocked("requires android.newDsl=false", "Motion legacy DSL opt-out")

    def test_kotlin_2_with_the_old_compose_dsl_is_caught(self):
        with ScratchRepo() as repo:
            repo.edit(
                "doctor/app/build.gradle.kts",
                "    compileOptions {\n",
                '    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }\n\n'
                "    compileOptions {\n",
            )
            repo.assert_blocked("still sets kotlinCompilerExtensionVersion", "obsolete Compose DSL")

    def test_compose_without_its_compiler_plugin_is_caught(self):
        with ScratchRepo() as repo:
            repo.edit(
                "doctor/app/build.gradle.kts",
                '    id("org.jetbrains.kotlin.plugin.compose")\n',
                "",
            )
            repo.assert_blocked("needs `org.jetbrains.kotlin.plugin.compose` applied", "missing Compose plugin")

    def test_a_kotlin_sub_plugin_out_of_lockstep_with_builtin_is_caught(self):
        with ScratchRepo() as repo:
            repo.edit(
                "motion-plugin/build.gradle.kts",
                'id("org.jetbrains.kotlin.plugin.parcelize") version "2.4.10"',
                'id("org.jetbrains.kotlin.plugin.parcelize") version "2.4.11"',
            )
            table = repo.envelope()
            ceiling = next(c for c in table["ceilings"] if c["coordinate"] == "org.jetbrains.kotlin.plugin.parcelize")
            ceiling["maxByDirectory"]["/motion-plugin"] = "2.4.11"
            repo.write_envelope(table)
            repo.assert_blocked("must match Kotlin 2.4.10", "Parcelize out of lockstep")


    def test_agp_10_is_caught_everywhere(self):
        with ScratchRepo() as repo:
            repo.edit("build.gradle.kts", 'version "9.4.0"', 'version "10.0.0"')
            repo.assert_blocked("next breaking major", "AGP 10 on the repo root")

    def test_next_breaking_agp_major_is_caught(self):
        with ScratchRepo() as repo:
            repo.edit("build.gradle.kts", 'version "9.4.0"', 'version "10.0.0"')
            repo.assert_blocked("next breaking major", "AGP 10")

    def test_agp_beyond_the_tested_ceiling_is_caught(self):
        with ScratchRepo() as repo:
            repo.edit("shift/build.gradle.kts", 'version "9.4.0"', 'version "9.5.0"')
            repo.assert_blocked("above its ceiling 9.4.0", "AGP 9.5.0 beyond tested ceiling")

    def test_a_wrapper_below_the_agp_floor_is_caught(self):
        with ScratchRepo() as repo:
            props = "ticker/android/gradle/wrapper/gradle-wrapper.properties"
            repo.edit(
                props,
                f"gradle-{wrapper_on_disk(repo.root / 'ticker/android')}-bin.zip",
                "gradle-8.2-bin.zip",
            )
            repo.assert_blocked("below 9.6.0", "Gradle under the AGP 9.x floor")

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
            # Core Doctor installs the API 37 platform in both jobs. Downgrade
            # both direct SDK-manager requests and fallbacks so one remaining
            # correct job cannot mask the regression.
            repo.edit(
                ".github/workflows/core-doctor-apk.yml",
                '"platforms;android-37.0" "build-tools;36.0.0"',
                '"platforms;android-36" "build-tools;36.0.0"',
                everywhere=True,
            )
            repo.assert_blocked("installs only", "doctor workflow platform mismatch")


class LauncherGradleDiagnosticExtraction(unittest.TestCase):
    def test_kotlin_warnings_do_not_mask_actual_build_errors(self):
        warning = "w: src/Main.kt:17: this warning mentions error: only as prose"
        selected = launcher_diagnostics.select_diagnostics([
            warning,
            "e: src/Main.kt:24: unresolved reference: missingSymbol",
            "FAILURE: Build failed with an exception.",
            "* What went wrong:",
            "> Task :app:compileDebugKotlin FAILED",
        ])
        self.assertNotIn(warning, selected)
        self.assertIn("e: src/Main.kt:24: unresolved reference: missingSymbol", selected)
        self.assertIn("FAILURE: Build failed with an exception.", selected)

    def test_warning_only_log_falls_back_without_emitting_a_warning_annotation(self):
        warning = "w: src/Main.kt:17: this warning mentions error: only as prose"
        self.assertEqual(
            launcher_diagnostics.select_diagnostics([warning]),
            [launcher_diagnostics.GENERIC_FAILURE],
        )


class PrivateLauncherPrototypeContract(unittest.TestCase):
    ANDROID = "http://schemas.android.com/apk/res/android"
    MANIFEST = ROOT / "launcher/app/src/main/AndroidManifest.xml"
    ACTIVITY = ROOT / "launcher/app/src/main/java/tv/corebuilds/launcher/LauncherActivity.kt"
    APPS = ROOT / "launcher/app/src/main/java/tv/corebuilds/launcher/LauncherApps.kt"
    PREFERENCES = ROOT / "launcher/app/src/main/java/tv/corebuilds/launcher/LauncherPreferences.kt"
    UI = ROOT / "launcher/app/src/main/java/tv/corebuilds/launcher/LauncherUi.kt"

    def setUp(self):
        self.manifest = ET.parse(self.MANIFEST).getroot()

    def test_registers_home_tv_and_standard_launcher_entry_points(self):
        activity = next(
            node for node in self.manifest.findall("./application/activity")
            if node.get(f"{{{self.ANDROID}}}name") == ".LauncherActivity"
        )
        filters = []
        for node in activity.findall("intent-filter"):
            values = {
                child.get(f"{{{self.ANDROID}}}name")
                for child in list(node)
                if child.tag in {"action", "category"}
            }
            filters.append(values)
        self.assertIn(
            {
                "android.intent.action.MAIN",
                "android.intent.category.HOME",
                "android.intent.category.DEFAULT",
            },
            filters,
        )
        self.assertIn(
            {"android.intent.action.MAIN", "android.intent.category.LEANBACK_LAUNCHER"},
            filters,
        )
        self.assertIn(
            {"android.intent.action.MAIN", "android.intent.category.LAUNCHER"},
            filters,
        )

    def test_app_visibility_is_narrow_and_offline(self):
        query_pairs = set()
        for node in self.manifest.findall("queries/intent"):
            action = node.find("action")
            category = node.find("category")
            query_pairs.add((
                action.get(f"{{{self.ANDROID}}}name") if action is not None else None,
                category.get(f"{{{self.ANDROID}}}name") if category is not None else None,
            ))
        self.assertEqual(query_pairs, {
            ("android.intent.action.MAIN", "android.intent.category.LEANBACK_LAUNCHER"),
            ("android.intent.action.MAIN", "android.intent.category.LAUNCHER"),
        })
        permissions = {
            node.get(f"{{{self.ANDROID}}}name")
            for node in self.manifest.findall("uses-permission")
        }
        self.assertNotIn("android.permission.QUERY_ALL_PACKAGES", permissions)
        self.assertNotIn("android.permission.INTERNET", permissions)

    def test_focus_spotlight_uses_installed_art_and_a_distance_readable_name(self):
        activity = self.ACTIVITY.read_text(encoding="utf-8")
        apps = self.APPS.read_text(encoding="utf-8")
        ui = self.UI.read_text(encoding="utf-8")
        self.assertIn("activity.loadBanner(pm)", apps)
        self.assertIn("resolved.loadIcon(pm)", apps)
        self.assertIn("val banner: Drawable?", apps)
        self.assertIn("app.banner ?: app.icon", activity)
        self.assertIn("heroTitle = label(getString(R.string.hero_title_empty), 30f", activity)
        strings_root = ET.parse(ROOT / "launcher/app/src/main/res/values/strings.xml").getroot()
        strings = {node.get("name"): node.text for node in strings_root.findall("string")}
        self.assertEqual(strings["hero_title_empty"], "Your apps.\\nYour space.")
        self.assertIn("wideCard = true", activity)
        self.assertIn("onFocused(app)", ui)

    def test_launcher_branding_starts_from_core_builds_icon_pack_assets(self):
        application = self.manifest.find("application")
        self.assertEqual(application.get(f"{{{self.ANDROID}}}icon"), "@drawable/core_builds_mark")
        self.assertEqual(application.get(f"{{{self.ANDROID}}}banner"), "@drawable/core_builds_banner")
        self.assertTrue((ROOT / "launcher/app/src/main/res/drawable-nodpi/core_builds_mark.webp").is_file())
        self.assertTrue((ROOT / "launcher/app/src/main/res/drawable-nodpi/core_builds_banner.webp").is_file())

        colors = ET.parse(ROOT / "launcher/app/src/main/res/values/colors.xml").getroot()
        palette = {node.get("name"): node.text for node in colors.findall("color")}
        self.assertEqual(palette["cb_background"], "#04070F")
        self.assertEqual(palette["cb_night"], "#0D1117")
        self.assertEqual(palette["cb_panel"], "#151923")
        self.assertEqual(palette["cb_cyan"], "#00D4FF")

        ui = self.UI.read_text(encoding="utf-8")
        self.assertIn("shape(R.color.cb_night, R.color.cb_cyan, 2)", ui)
        self.assertIn("app.icon.copyForDisplay()", ui)

    def test_mockups_are_disclosed_design_illustrations_not_device_captures(self):
        note = (ROOT / "docs/mockups/README.md").read_text(encoding="utf-8")
        self.assertIn("NOT A SCREENSHOT", note)
        self.assertIn("not screenshots from an APK or Android TV device", note)
        self.assertIn("vendor logos or invented substitutes", note)
        for filename in ("core-launcher-home.png", "core-launcher-all-apps.png"):
            image = (ROOT / "docs/mockups" / filename).read_bytes()
            self.assertEqual(image[:8], b"\x89PNG\r\n\x1a\n")
            self.assertEqual(struct.unpack(">II", image[16:24]), (1920, 1080))

    def test_package_updates_refresh_labels_and_banner_art(self):
        activity = self.ACTIVITY.read_text(encoding="utf-8")
        apps = self.APPS.read_text(encoding="utf-8")
        self.assertIn("lastUpdateTime = packageLastUpdateTime(pm, packageName)", apps)
        self.assertIn("PackageManager.PackageInfoFlags.of(0L)", apps)
        self.assertIn("${it.label}:${it.tvOptimized}:${it.lastUpdateTime}", activity)

    def test_favourites_hide_uninstalled_apps_without_forgetting_the_choice(self):
        preferences = self.PREFERENCES.read_text(encoding="utf-8")
        self.assertIn("Keep a user's preference if an app is temporarily uninstalled", preferences)
        self.assertIn(".filterTo(linkedSetOf()) { it in installedPackages }", preferences)
        self.assertIn("Preserve temporarily unavailable packages when another favourite is edited", preferences)

    def test_recent_history_is_bounded_local_and_hidden_for_pinned_apps(self):
        preferences = self.PREFERENCES.read_text(encoding="utf-8")
        activity = self.ACTIVITY.read_text(encoding="utf-8")
        self.assertIn('private const val KEY_RECENTS = "recent_packages"', preferences)
        self.assertIn("private const val RECENTS_LIMIT = 5", preferences)
        self.assertIn("fun recordLaunch(context: Context, packageName: String)", preferences)
        self.assertIn("take(RECENTS_LIMIT)", preferences)
        self.assertIn("LauncherPreferences.recordLaunch(applicationContext, app.packageName)", activity)
        self.assertIn(".filterNot { it in pins }", activity)
        self.assertIn("recentSection.visibility = View.GONE", activity)
        self.assertIn("recentSection.visibility = View.VISIBLE", activity)

    def test_home_rebuild_clears_stale_tiles_and_reads_preferences_off_main_thread(self):
        activity = self.ACTIVITY.read_text(encoding="utf-8")
        refresh = activity[activity.index("private fun refreshApps()"):activity.index("private fun renderApps(")]
        render = activity[activity.index("private fun renderApps("):activity.index("private fun onAppFocused(")]
        self.assertLess(refresh.index("LauncherPreferences.pinnedPackages"), refresh.index("runOnUiThread"))
        self.assertLess(refresh.index("LauncherPreferences.recentPackages"), refresh.index("runOnUiThread"))
        self.assertNotIn("LauncherPreferences.pinnedPackages", render)
        self.assertIn("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE", activity)
        self.assertIn("registerOnBackInvokedCallback", activity)
        self.assertIn('@SuppressLint("GestureBackNavigation")', activity)
        for clear in ("appTiles.clear()", "favoritesRow.removeAllViews()",
                      "recentRow.removeAllViews()", "appGrid.removeAllViews()"):
            self.assertIn(clear, render)

        ui = self.UI.read_text(encoding="utf-8")
        self.assertIn("R.string.open_app_description", ui)
        self.assertIn("R.string.open_tv_app_description", ui)

        manage = (ROOT / "launcher/app/src/main/java/tv/corebuilds/launcher/ManageFavoritesActivity.kt").read_text(encoding="utf-8")
        self.assertIn("accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE", manage)
        load = manage[manage.index("private fun loadApps()"):manage.index("private fun renderRows()")]
        render_rows = manage[manage.index("private fun renderRows()"):manage.index("private fun togglePin(")]
        self.assertLess(load.index("LauncherPreferences.pinnedPackages"), load.index("runOnUiThread"))
        self.assertNotIn("LauncherPreferences.pinnedPackages", render_rows)

    def test_home_refresh_coalesces_mid_scan_requests_and_stops_safely_on_destroy(self):
        activity = self.ACTIVITY.read_text(encoding="utf-8")
        refresh = activity[activity.index("private fun refreshApps()"):activity.index("private fun renderApps(")]
        callback = refresh[refresh.index("runOnUiThread {"):]
        destroy = activity[activity.index("override fun onDestroy()"):activity.index("private fun createHomeScreen(")]

        self.assertIn("private var rescanRequested = false", activity)
        self.assertIn("if (scanning) {", refresh)
        self.assertIn("rescanRequested = true", refresh)
        self.assertLess(callback.index("if (isFinishing || isDestroyed)"), callback.index("if (rescanRequested)"))
        self.assertLess(callback.index("renderApps("), callback.index("if (rescanRequested)"))
        pending_rescan = callback[callback.index("if (rescanRequested)"):]
        self.assertLess(pending_rescan.index("rescanRequested = false"), pending_rescan.index("refreshApps()"))
        self.assertIn("if (!isFinishing && !isDestroyed) refreshApps()", pending_rescan)
        self.assertIn("catch (_: RejectedExecutionException)", refresh)
        self.assertLess(destroy.index("rescanRequested = false"), destroy.index("appLoader.shutdown()"))

    def test_render_refreshes_selected_spotlight_from_current_catalog(self):
        activity = self.ACTIVITY.read_text(encoding="utf-8")
        render = activity[activity.index("private fun renderApps("):activity.index("private fun onAppFocused(")]
        self.assertIn("val focusedApp = focusPackage?.let", render)
        self.assertIn("val currentSelectedApp = selectedApp?.packageName?.let", render)
        self.assertIn("apps.firstOrNull { it.packageName == selectedPackage }", render)
        self.assertIn("updateHero(focusedApp ?: currentSelectedApp ?: apps.firstOrNull())", render)

    def test_offline_preview_dpad_uses_rendered_columns_and_crosses_up_from_first_row(self):
        generator = (ROOT / "tools/build_launcher_offline_preview.py").read_text(encoding="utf-8")
        preview = (ROOT / "docs/mockups/core-launcher-offline.html").read_text(encoding="utf-8")
        expected = (
            "const columns = items.filter(item => item.offsetTop === items[0].offsetTop).length || 1;",
            'const next = index + (direction === "up" ? -columns : columns);',
            'if (direction === "down") return;',
            'const nextGroupIndex = groupIndex + (direction === "up" ? -1 : 1);',
        )
        move = preview[preview.index("function move(direction)"):preview.index("function focusSelected()")]
        for snippet in expected:
            self.assertIn(snippet, generator)
            self.assertIn(snippet, move)
        self.assertNotIn('direction === "up" ? -6 : 6', move)

    def test_offline_html_preview_is_self_contained_and_disclosed(self):
        preview = (ROOT / "docs/mockups/core-launcher-offline.html").read_text(encoding="utf-8")
        self.assertIn("DESIGN ILLUSTRATION · NOT A SCREENSHOT", preview)
        self.assertIn("data:image/webp;base64,", preview)
        self.assertIn("no network requests", preview)
        self.assertIn("connect-src 'none'", preview)
        self.assertNotRegex(preview.lower(), r"<script[^>]+src\s*=")
        self.assertNotRegex(preview.lower(), r"<link[^>]+href\s*=")
        self.assertNotIn("fonts.googleapis.com", preview)
        self.assertNotIn("http://", preview)
        self.assertNotIn("https://", preview)
        self.assertNotIn("fetch(", preview)
        preview_readme = (ROOT / "docs/mockups/README.md").read_text(encoding="utf-8")
        self.assertIn("rendered column count", preview_readme)
        self.assertIn("not evidence of Android remote behavior", preview_readme)

    def test_documentation_links_implementation_and_hardware_signoff_record(self):
        readme = (ROOT / "launcher/README.md").read_text(encoding="utf-8")
        for source in (
            "LauncherActivity.kt",
            "LauncherApps.kt",
            "LauncherPreferences.kt",
            "LauncherUi.kt",
            "ManageFavoritesActivity.kt",
        ):
            self.assertIn(source, readme)
        self.assertIn("test_gradle_envelope.py", readme)
        self.assertIn("**Current sign-off: BLOCKED — 0/8 hardware checks run.**", readme)
        self.assertIn("API level", readme)
        self.assertIn("single source of truth", readme)
        self.assertIn("Validation owner:** Not assigned", readme)
        self.assertIn("Target completion date:** Before the next prototype review", readme)
        self.assertIn("30–45 minutes per device", readme)
        self.assertIn("full commit SHA or APK SHA-256", readme)
        self.assertIn("| # | Procedure | PASS criterion | Status | Evidence / result |", readme)
        self.assertEqual(readme.count("| NOT RUN |"), 8)
        for check in ("D-pad", "HOME role", "Back", "TalkBack"):
            self.assertIn(check, readme)
        research = (ROOT / "docs/CORE_LAUNCHER_PROTOTYPE_RESEARCH.md").read_text(encoding="utf-8")
        self.assertIn("80% function-level KDoc threshold", research)
        self.assertIn("This documentation waiver does not waive the required physical-device validation gate", research)

    def test_private_debug_package_and_validation_artifact_workflow(self):
        build = (ROOT / "launcher/app/build.gradle.kts").read_text(encoding="utf-8")
        workflow = (ROOT / ".github/workflows/core-launcher-prototype-ci.yml").read_text(encoding="utf-8")
        self.assertIn('applicationId = "tv.corebuilds.launcher.prototype"', build)
        self.assertIn('applicationIdSuffix = ".debug"', build)
        self.assertIn(":app:assembleDebug", workflow)
        self.assertIn("github.event.pull_request.head.sha || github.sha", workflow)
        self.assertIn("actions/upload-artifact@v4", workflow)
        self.assertIn("core-builds-launcher-debug-${{ steps.revision.outputs.sha }}", workflow)
        self.assertIn("BUILD_COMMIT.txt", workflow)
        self.assertIn("retention-days: 14", workflow)
        self.assertIn("extract_launcher_gradle_diagnostics.py build_out.txt", workflow)
        self.assertNotIn("assembleRelease", workflow)
        self.assertNotRegex(workflow, r"(?m)^\s*tags:")


if __name__ == "__main__":
    unittest.main(verbosity=2)
