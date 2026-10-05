#!/usr/bin/env python3
"""
The Core EQ updater's half of the update contract.

`tests/test_update_manifest_gate.py` holds the icon pack's: the published feed
may lag the build but never lead it, and the tag build publishes the feed only
after the release carries the APK. Core EQ shipped the same updater in 1.1.1,
so it needs the same two guarantees, plus the ones that are Core EQ's own:

  * the public test package (`tv.corebuilds.eq.debug`) carries **no** feed,
    because its package id means a production APK could never install over it;
  * the release build's URL is the suite's feed on `main` and nothing else;
  * the tag build writes the feed *after* the versioned release, the floating
    `coreeq` release and the test channel have the APK, exactly once, on main;
  * the shipped APK's feed is generated from the changelog section the tag
    names, with a release-notes URL that resolves (the icon pack's tags are
    plain `v*`; Core EQ's are `coreeq-v*`, so a copied URL would 404);
  * the app stays inside the suite's own updater gate (`tools/audit_contract.py`)
    rather than only inside this file, so the hardening markers are checked by
    the gate that already covers every other updater.

Run: `python3 tests/test_core_eq_update_contract.py`
"""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "coreeq/app/build.gradle.kts"
WORKFLOW = ROOT / ".github/workflows/core-eq-apk.yml"
FEED = ROOT / "Latestrelease/coreeq-version.json"
MANIFEST = ROOT / "coreeq/app/src/main/AndroidManifest.xml"
PATHS = ROOT / "coreeq/app/src/main/res/xml/file_paths.xml"
GENERATOR = ROOT / "tools/generate_release_metadata.py"
AUDIT = ROOT / "tools/audit_contract.py"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def gradle_value(source: str, name: str) -> str:
    match = re.search(rf"{name}\s*=\s*(?:\"([^\"]+)\"|(\d+))", source)
    assert match, f"missing {name}"
    return match.group(1) or match.group(2)


def debug_block(source: str) -> str:
    """The `debug { ... }` build type body, braces balanced."""
    start = re.search(r"\n\s*debug \{", source)
    assert start, "no debug buildType in coreeq/app/build.gradle.kts"
    open_brace = source.index("{", start.start())
    depth = 0
    for index in range(open_brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[open_brace + 1:index]
    raise AssertionError("unbalanced braces in the debug buildType")


class CoreEqUpdateContract(unittest.TestCase):
    def setUp(self) -> None:
        self.gradle = read(GRADLE)
        self.workflow = read(WORKFLOW)
        self.feed = json.loads(read(FEED))

    def test_feed_never_leads_the_build(self) -> None:
        code = int(gradle_value(self.gradle, "versionCode"))
        name = gradle_value(self.gradle, "versionName")
        published = self.feed["versionCode"]
        self.assertLessEqual(
            published,
            code,
            "Latestrelease/coreeq-version.json is ahead of the build - an "
            "installed copy would be told about an APK that is not published",
        )
        if published == code:
            self.assertEqual(self.feed["versionName"], name)

    def test_feed_points_only_at_the_suites_release_channel(self) -> None:
        self.assertTrue(
            self.feed["apkUrl"].startswith(
                "https://github.com/brevityA/CoreBuildsApps/releases/download/coreeq/"
            ),
            self.feed["apkUrl"],
        )
        self.assertTrue(self.feed["apkUrl"].endswith("coreeq-release.apk"))
        self.assertRegex(self.feed["apkSha256"], r"^[0-9a-f]{64}$")
        self.assertIn("coreeq-v", self.feed["releaseNotesUrl"])

    def test_release_build_polls_the_suite_feed(self) -> None:
        self.assertRegex(
            self.gradle,
            r'raw\.githubusercontent\.com/brevityA/CoreBuildsApps/" \+\s*\n\s*'
            r'"main/Latestrelease/coreeq-version\.json',
        )
        self.assertIn('"tv.corebuilds.eq.update"', self.gradle)

    def test_the_test_package_has_no_feed_and_its_own_authority(self) -> None:
        body = debug_block(self.gradle)
        self.assertRegex(
            body,
            r'buildConfigField\("String", "UPDATE_MANIFEST_URL", "\\"\\""\)',
            "the debug build must be handed a blank feed: it cannot install a "
            "production APK over tv.corebuilds.eq.debug",
        )
        self.assertIn('"tv.corebuilds.eq.debug.update"', body)
        self.assertIn('manifestPlaceholders["fileProviderAuthority"] = "tv.corebuilds.eq.debug.update"', body)

    def test_manifest_and_file_provider_are_wired(self) -> None:
        manifest = read(MANIFEST)
        for needle in (
            "android.permission.INTERNET",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            'android:authorities="${fileProviderAuthority}"',
            '@xml/file_paths',
        ):
            self.assertIn(needle, manifest)
        self.assertIn('<cache-path name="updates" path="updates/" />', read(PATHS))

    def test_generator_knows_core_eqs_tag_and_changelog(self) -> None:
        generator = read(GENERATOR)
        self.assertRegex(
            generator,
            r"'coreeq': \{[^}]*'metadata':'Latestrelease/coreeq-version\.json'",
        )
        self.assertRegex(generator, r"'coreeq': \{[^}]*'notesTag':'coreeq-v'")
        self.assertRegex(generator, r"'coreeq': \{[^}]*'changelog':'coreeq/CHANGELOG\.md'")
        changelog = read(ROOT / "coreeq/CHANGELOG.md")
        version = gradle_value(self.gradle, "versionName")
        self.assertRegex(
            changelog,
            rf"(?m)^## \[{re.escape(version)}\] — \d{{4}}-\d{{2}}-\d{{2}}$",
            f"the tag build reads highlights from the [ {version} ] section",
        )

    def test_the_suite_updater_gate_covers_core_eq(self) -> None:
        audit = read(AUDIT)
        entry = re.search(r"\n    \"coreeq\": \{(.*?)\n    \},", audit, re.S)
        self.assertIsNotNone(entry, "tools/audit_contract.py does not list coreeq")
        assert entry is not None
        self.assertIn('"checker": "coreeq/app/src/main/kotlin/tv/corebuilds/eq/update/UpdateChecker.kt"', entry.group(1))
        self.assertIn('"installer": "coreeq/app/src/main/kotlin/tv/corebuilds/eq/update/UpdateInstaller.kt"', entry.group(1))

    def test_tag_build_publishes_the_feed_after_the_releases(self) -> None:
        steps = re.findall(r"^      - name: (.+)$", self.workflow, re.M)
        self.assertIn("Publish the updater manifest", steps)
        manifest_step = steps.index("Publish the updater manifest")
        for earlier in (
            "Publish versioned release",
            "Point floating tag coreeq at this commit",
            "Publish stable release",
            "Publish to the coreeq-test prerelease",
        ):
            self.assertIn(earlier, steps)
            self.assertLess(
                steps.index(earlier),
                manifest_step,
                f"the feed must publish after '{earlier}': it may only name a "
                "build whose APK is already downloadable",
            )
        body = self.workflow.split("- name: Publish the updater manifest", 1)[1]
        self.assertIn("python tools/generate_release_metadata.py coreeq", body)
        self.assertIn("cp \"$RUNNER_TEMP/coreeq-version.json\" Latestrelease/coreeq-version.json", body)
        self.assertIn("if: startsWith(github.ref, 'refs/tags/coreeq-v')", body.split("- name:", 1)[0])

    def test_versioned_release_records_the_outstanding_m6a_gate(self) -> None:
        body = self.workflow.split("- name: Publish versioned release", 1)[1]
        body = body.split("\n      - name:", 1)[0]
        self.assertIn("M6a", body, "the release page must record the outstanding M6a gate")
        self.assertIn("not yet recorded on hardware", body)


if __name__ == "__main__":
    unittest.main(verbosity=2)
