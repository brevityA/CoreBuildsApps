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
import sys
import tempfile
import unittest
import zipfile
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

    def test_tag_build_verifies_the_feed_serves_the_build(self):
        # Ordering keeps the feed from *claiming* a build that is not out. This
        # keeps the claim honest about the bytes: the APK is fetched back from
        # the URL the feed names and the version inside it is compared. Users
        # met the gap as "1.9.5 is available but fails to install".
        wf = read(".github/workflows/build.yml")
        steps = re.findall(r"^      - name: (.+)$", wf, re.M)
        self.assertIn("Verify the feed serves this build", steps)
        self.assertLess(steps.index("Publish the update manifest"),
                        steps.index("Verify the feed serves this build"))
        body = wf.split("- name: Verify the feed serves this build", 1)[1]
        self.assertIn("python tools/check_published_update.py", body.split("      - name:")[0])


class PublishedFeedServesTheBuild(unittest.TestCase):
    """The tool that checks the feed's promise against the real APK."""

    def setUp(self):
        sys.path.insert(0, str(ROOT / "tools"))
        import check_published_update as gate
        self.gate = gate
        self.feed = json.loads(read("Latestrelease/version.json"))
        self.build = json.loads(read("app/src/main/assets/version.json"))

    def fake_apk(self, version_code: int, version_name: str) -> Path:
        tmp = Path(tempfile.mkdtemp()) / "iconpack-release.apk"
        with zipfile.ZipFile(tmp, "w") as archive:
            archive.writestr("assets/version.json", json.dumps(
                {"versionCode": version_code, "versionName": version_name}))
        return tmp

    def test_a_matching_apk_passes(self):
        apk = self.fake_apk(self.feed["versionCode"], self.feed["versionName"])
        self.assertEqual(self.gate.version_problems(
            self.feed, self.gate.apk_manifest(apk), "the APK at apkUrl"), [])

    def test_an_older_apk_is_the_reported_failure(self):
        # exactly the 1.9.5 report: the feed names the new build, the URL still
        # serves the previous one. Both fields are reported, because
        # UpdateInstaller compares both.
        apk = self.fake_apk(self.feed["versionCode"] - 1, "1.9.3")
        problems = self.gate.version_problems(
            self.feed, self.gate.apk_manifest(apk), "the APK at apkUrl")
        self.assertTrue(any("versionCode" in p for p in problems), problems)
        self.assertTrue(any("versionName" in p for p in problems), problems)

    def test_a_code_mismatch_alone_still_stops_the_install(self):
        # The version name is cosmetic to the updater; the code is not.
        apk = self.fake_apk(self.feed["versionCode"] - 1, self.feed["versionName"])
        problems = self.gate.version_problems(
            self.feed, self.gate.apk_manifest(apk), "the APK at apkUrl")
        self.assertTrue(any("versionCode" in p for p in problems), problems)

    def test_an_apk_without_a_build_manifest_is_refused(self):
        empty = Path(tempfile.mkdtemp()) / "not-a-pack.apk"
        with zipfile.ZipFile(empty, "w") as archive:
            archive.writestr("classes.dex", b"")
        with self.assertRaises(ValueError):
            self.gate.apk_manifest(empty)

    def test_feed_may_not_lead_the_build(self):
        ahead = dict(self.feed, versionCode=self.build["versionCode"] + 1)
        self.assertTrue(any("leads the build" in p
                            for p in self.gate.offline_problems(ahead, self.build)))

    def test_apk_url_must_be_a_release_download(self):
        for url in ("https://example.com/thing.apk",
                    "https://github.com/brevityA/CoreBuildsApps/releases/download/v9.9.9/x.apk"):
            self.assertTrue(self.gate.url_problems(dict(self.feed, apkUrl=url)))
        for url in ("https://github.com/brevityA/CoreBuildsApps/releases/download/iconpack/iconpack-release.apk",
                    f"https://github.com/brevityA/CoreBuildsApps/releases/download/v{self.feed['versionName']}/iconpack-release.apk"):
            self.assertEqual(self.gate.url_problems(dict(self.feed, apkUrl=url)), [])

    def test_the_shipped_feed_is_sound_offline(self):
        self.assertEqual(self.gate.offline_problems(self.feed, self.build), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
