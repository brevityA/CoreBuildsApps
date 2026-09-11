#!/usr/bin/env python3
"""Contracts for the Codespaces / Dev Container configs.

No Docker in this environment — assert the JSON is well-formed, the Feature
ids are the ones we documented, and we did not sneak an emulator into a
machine with no KVM.
"""
from __future__ import annotations

import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DC = ROOT / ".devcontainer"


def load(rel: str) -> dict:
    return json.loads((DC / rel).read_text(encoding="utf-8"))


class DefaultPythonConfig(unittest.TestCase):
    def setUp(self):
        self.cfg = load("devcontainer.json")

    def test_is_the_default_config(self):
        self.assertTrue((DC / "devcontainer.json").is_file())

    def test_python_312_without_pylint_stack(self):
        py = self.cfg["features"]["ghcr.io/devcontainers/features/python:1"]
        self.assertEqual(py["version"], "3.12")
        self.assertFalse(py["installTools"])

    def test_node_22_for_core_line(self):
        node = self.cfg["features"]["ghcr.io/devcontainers/features/node:1"]
        self.assertEqual(node["version"], "22")

    def test_official_features_only_plus_cairo(self):
        keys = set(self.cfg["features"])
        self.assertEqual(
            keys,
            {
                "ghcr.io/devcontainers/features/common-utils:2",
                "ghcr.io/devcontainers/features/python:1",
                "ghcr.io/devcontainers/features/node:1",
                "ghcr.io/devcontainers/features/github-cli:1",
                "./features/cairo",
            },
        )

    def test_no_java_no_android_on_the_cheap_config(self):
        blob = json.dumps(self.cfg)
        self.assertNotIn("features/java", blob)
        self.assertNotIn("android-sdk", blob)
        self.assertNotIn("emulator", blob)

    def test_post_create_installs_pinned_requirements(self):
        self.assertIn("tools/requirements.txt", self.cfg["postCreateCommand"])
        self.assertIn("check_suite_truth.py", self.cfg["postCreateCommand"])


class AndroidConfig(unittest.TestCase):
    def setUp(self):
        self.cfg = load("android/devcontainer.json")

    def test_java_17_temurin_no_extra_gradle(self):
        java = self.cfg["features"]["ghcr.io/devcontainers/features/java:1"]
        self.assertEqual(java["version"], "17")
        self.assertEqual(java["jdkDistro"], "tem")
        self.assertFalse(java["installGradle"])

    def test_android_sdk_is_the_local_feature(self):
        sdk = self.cfg["features"]["../features/android-sdk"]
        self.assertIn("platforms;android-34", sdk["packages"])
        self.assertIn("platforms;android-35", sdk["packages"])
        self.assertNotIn("system-images", sdk["packages"])
        self.assertNotIn("emulator", sdk["packages"])

    def test_java_installs_before_android_sdk(self):
        order = self.cfg["overrideFeatureInstallOrder"]
        self.assertLess(order.index("ghcr.io/devcontainers/features/java"),
                        order.index("../features/android-sdk"))

    def test_heavier_host_requirements(self):
        host = self.cfg["hostRequirements"]
        self.assertGreaterEqual(host["cpus"], 8)
        self.assertGreaterEqual(int(str(host["memory"]).replace("gb", "")), 16)


class LocalFeatures(unittest.TestCase):
    def test_cairo_and_android_sdk_feature_manifests(self):
        cairo = load("features/cairo/devcontainer-feature.json")
        sdk = load("features/android-sdk/devcontainer-feature.json")
        self.assertEqual(cairo["id"], "cairo")
        self.assertEqual(sdk["id"], "android-sdk")
        self.assertIn("ghcr.io/devcontainers/features/java", sdk["installsAfter"])
        self.assertEqual(sdk["containerEnv"]["ANDROID_HOME"],
                         "/usr/local/lib/android/sdk")

    def test_install_scripts_exist_and_are_executable(self):
        for rel in (
            "features/cairo/install.sh",
            "features/android-sdk/install.sh",
            "android/post-create.sh",
        ):
            path = DC / rel
            self.assertTrue(path.is_file(), rel)
            self.assertTrue(path.stat().st_mode & 0o111, f"{rel} must be executable")

    def test_android_sdk_install_refuses_an_emulator(self):
        src = (DC / "features/android-sdk/install.sh").read_text(encoding="utf-8")
        code = "\n".join(
            line.split("#", 1)[0] for line in src.splitlines()
        )
        self.assertNotIn("system-images", code)
        self.assertIn("sdkmanager", code)
        self.assertIn("no emulator", (DC / "features/android-sdk/devcontainer-feature.json")
                      .read_text(encoding="utf-8").lower())


if __name__ == "__main__":
    unittest.main(verbosity=2)
