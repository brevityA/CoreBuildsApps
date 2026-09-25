"""
The in-app updater must never announce a build that is not released.

Every installed copy polls Latestrelease/version.json on main. In the 1.9.5
cycle the version-bump PR stamped it, so the moment that PR merged 1.9.4
users were told 1.9.5 was out, and Download fetched the floating `iconpack`
release, still 1.9.4, which would not install over itself. The release-to-be
now lives in app/src/main/assets/version.json and the tag build publishes it.
"""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def gradle_code() -> int:
    return int(re.search(r"versionCode\s*=\s*(\d+)", read("app/build.gradle.kts")).group(1))


class UpdateManifestGate(unittest.TestCase):

    def test_published_manifest_never_leads_the_build(self):
        published = json.loads(read("Latestrelease/version.json"))
        self.assertLessEqual(published["versionCode"], gradle_code())

    def test_build_manifest_tracks_gradle(self):
        pending = json.loads(read("app/src/main/assets/version.json"))
        self.assertEqual(pending["versionCode"], gradle_code())

    def test_prepare_release_does_not_touch_the_published_manifest(self):
        src = read("tools/prepare_release.py")
        self.assertIn('VERSION_JSON = ROOT / "app/src/main/assets/version.json"', src)
        self.assertNotRegex(src, r'ROOT\s*/\s*"Latestrelease')

    def test_tag_build_publishes_the_manifest_after_the_release(self):
        wf = read(".github/workflows/build.yml")
        steps = re.findall(r"^      - name: (.+)$", wf, re.M)
        self.assertIn("Publish the update manifest", steps)
        manifest = steps.index("Publish the update manifest")
        for release_step in ("Publish versioned release",
                             "Publish stable Downloader release"):
            self.assertLess(steps.index(release_step), manifest,
                            f"manifest must publish after '{release_step}'")
        body = wf.split("- name: Publish the update manifest", 1)[1]
        self.assertIn("cp app/src/main/assets/version.json /tmp/version.json", body)


if __name__ == "__main__":
    unittest.main(verbosity=2)
