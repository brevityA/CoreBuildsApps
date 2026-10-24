from __future__ import annotations

import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class SuiteApi37Migration(unittest.TestCase):
    def test_every_suite_app_and_module_targets_api_37(self):
        files = [
            "app/build.gradle.kts",
            "glyphs/build.gradle.kts",
            "ticker/android/app/build.gradle.kts",
            "shift/app/build.gradle.kts",
            "motion-plugin/app/build.gradle.kts",
            "doctor/app/build.gradle.kts",
            "coreeq/app/build.gradle.kts",
        ]
        for rel in files:
            source = (ROOT / rel).read_text(encoding="utf-8")
            with self.subTest(module=rel):
                self.assertRegex(source, r"\bcompileSdk\s*=\s*37\b")
                self.assertRegex(source, r"\btargetSdk\s*=\s*37\b")

    def test_core_line_declares_and_requests_lan_permission_for_pairing(self):
        manifest = ET.parse(ROOT / "ticker/android/app/src/main/AndroidManifest.xml").getroot()
        permissions = {
            node.get(f"{ANDROID}name")
            for node in manifest.findall("uses-permission")
        }
        self.assertIn("android.permission.ACCESS_LOCAL_NETWORK", permissions)

        activity = (ROOT / "ticker/android/app/src/main/java/dev/corebuilds/line/MainActivity.kt")
        source = activity.read_text(encoding="utf-8")
        self.assertIn("Build.VERSION.SDK_INT >= 37", source)
        self.assertIn("checkSelfPermission(android.Manifest.permission.ACCESS_LOCAL_NETWORK)", source)
        self.assertIn("requestPermissions(", source)
        self.assertIn("Allow local network access, then select Add from phone again.", source)
        self.assertIn("pairServer.start()", source)

    def test_core_doctor_gates_custom_addon_checks_on_lan_permission(self):
        manifest = ET.parse(ROOT / "doctor/app/src/main/AndroidManifest.xml").getroot()
        permissions = {
            node.get(f"{ANDROID}name")
            for node in manifest.findall("uses-permission")
        }
        self.assertIn("android.permission.ACCESS_LOCAL_NETWORK", permissions)

        source = (ROOT / "doctor/app/src/main/kotlin/dev/corebuilds/doctor/MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn("Build.VERSION.SDK_INT >= 37", source)
        self.assertIn("input.addonUrl.isNotBlank()", source)
        self.assertIn("localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)", source)
        self.assertIn("Permission denied; LAN-hosted add-on URLs may fail", source)

    def test_ci_and_release_builds_install_the_api_37_platform(self):
        matrix = (ROOT / ".github/workflows/suite-ci.yml").read_text(encoding="utf-8")
        self.assertIn('"platforms;android-${{ matrix.platformId || matrix.compileSdk }}"', matrix)
        self.assertEqual(matrix.count("platformId: 37.0"), 8)
        self.assertEqual(matrix.count("buildTools: 36"), 8)

        for rel in (
            ".github/workflows/build.yml",
            ".github/workflows/play.yml",
            ".github/workflows/core-line-apk.yml",
            ".github/workflows/core-shift-apk.yml",
            ".github/workflows/core-motion-apk.yml",
            ".github/workflows/core-doctor-apk.yml",
            ".github/workflows/core-eq-apk.yml",
            ".github/workflows/core-line-device-check.yml",
            ".github/workflows/device-check.yml",
            ".github/workflows/iconpack-test-apk.yml",
            ".github/workflows/suite-release.yml",
        ):
            source = (ROOT / rel).read_text(encoding="utf-8")
            with self.subTest(workflow=rel):
                self.assertIn('"platforms;android-37.0" "build-tools;36.0.0"', source)
                self.assertIn("uses: android-actions/setup-android@v3", source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
