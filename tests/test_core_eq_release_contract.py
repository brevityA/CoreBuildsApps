#!/usr/bin/env python3
"""Core EQ install range and certificate continuity are release contracts."""
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "coreeq/app/build.gradle.kts"
WORKFLOW = ROOT / ".github/workflows/core-eq-apk.yml"
PUBLISHING = ROOT / "PUBLISHING.md"


class CoreEqReleaseContract(unittest.TestCase):
    def setUp(self) -> None:
        self.gradle = GRADLE.read_text(encoding="utf-8")
        self.workflow = WORKFLOW.read_text(encoding="utf-8")
        self.publishing = PUBLISHING.read_text(encoding="utf-8")

    def test_android_11_to_latest_is_the_declared_and_tested_range(self) -> None:
        self.assertRegex(self.gradle, r"compileSdk\s*=\s*37")
        self.assertRegex(self.gradle, r"minSdk\s*=\s*30")
        self.assertRegex(self.gradle, r"targetSdk\s*=\s*37")
        self.assertIn('"platforms;android-37" "build-tools;36.0.0"', self.workflow)
        self.assertIn("api-level: [30, 37]", self.workflow)
        self.assertIn("adb install -r", self.workflow)

    def test_test_package_is_distinct_but_uses_the_stable_signer_on_main(self) -> None:
        self.assertIn('applicationIdSuffix = ".debug"', self.gradle)
        self.assertIn('signingConfig = signingConfigs.getByName("release")', self.gradle)
        self.assertIn("Require signing secrets and decode release keystore", self.workflow)
        self.assertIn("github.ref == 'refs/heads/main'", self.workflow)
        self.assertIn("KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD", self.workflow)
        self.assertIn("tv.corebuilds.eq.debug", self.workflow)

    def test_signing_secrets_are_the_suite_shared_ones(self) -> None:
        """Core EQ must sign with the repo secrets the other apps already use.

        Routing Core EQ through its own credential names would need a second
        copy of the release key in GitHub, which is how two apps end up with
        two certificates and an app nobody can update over. The shared set is
        derived from the sibling workflows that decode the same keystore, so a
        new suite-wide secret has to reach Core EQ or this test fails.
        """
        shared: set[str] = set()
        for path in sorted((ROOT / ".github/workflows").glob("*.yml")):
            if path == WORKFLOW:
                continue
            body = path.read_text(encoding="utf-8")
            if "release.jks" not in body:
                continue
            shared |= set(re.findall(r"secrets\.((?:KEYSTORE|KEY)_[A-Z0-9_]+)", body))
        self.assertTrue(shared, "no sibling workflow decodes the shared keystore")
        eq_secrets = set(re.findall(r"secrets\.((?:KEYSTORE|KEY)_[A-Z0-9_]+)", self.workflow))
        self.assertEqual(shared, eq_secrets)

    def test_release_and_public_test_builds_pin_the_published_certificate(self) -> None:
        self.assertIn("Verify candidate certificate matches current stable APK", self.workflow)
        self.assertIn("gh release download coreeq", self.workflow)
        self.assertIn("Signer #1 certificate SHA-256 digest", self.workflow)
        self.assertIn('if [[ "$PREVIOUS_CERT" != "$CANDIDATE_CERT" ]]', self.workflow)
        self.assertIn("refusing to publish an APK users cannot install over it", self.workflow)

    def test_release_publication_waits_for_install_smokes(self) -> None:
        self.assertRegex(self.workflow, r"(?ms)^  install:\n.*?^    needs: apk$")
        self.assertRegex(self.workflow, r"(?ms)^  publish:\n    name: Publish verified APK\n    needs: \[apk, install\]")

    def test_first_test_signer_migration_is_documented(self) -> None:
        self.assertIn("uninstall only the test package once", self.publishing)
        self.assertIn("Production `tv.corebuilds.eq` is a separate package", self.publishing)


if __name__ == "__main__":
    unittest.main(verbosity=2)
