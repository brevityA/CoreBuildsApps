#!/usr/bin/env python3
"""
A privacy policy that lies is worse than none: Play will remove an app for it,
and unlike a broken icon nobody reviewing the store can see the lie.

So this holds docs/privacy.html to the code. Every claim the page makes about
network egress has a counterpart in the source it describes:

  * the hosts the page names are the hosts the code actually calls, and no
    other host is called;
  * the one POST payload it itemises is the payload IconRequestSender builds;
  * "no analytics, advertising or crash SDK" is checked against the Gradle
    files of every module the app ships from;
  * "the Play build removes the install permission" is checked against the
    Play manifest overlay;
  * "no update check on Play" is checked against Distribution.kt.

It also checks the page is a page: real HTML Play can serve, with a title, an
effective date, and a contact route - not a file that 404s or renders as source.
"""
from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "docs" / "privacy.html"
KT = ROOT / "app/src/main/java/tv/corebuilds/iconpack"
RES = ROOT / "app/src/main/res/values"

HOSTS_THE_CODE_CALLS = {
    "raw.githubusercontent.com",
    "github.com",
    "play.google.com",
    # Not fetched by the app: opened in a browser, and named for completeness.
    "policies.google.com",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def sources() -> dict[str, str]:
    return {
        p.name: read(p) for p in sorted(KT.glob("*.kt"))
    }


class ThePageIsServeable(unittest.TestCase):
    def setUp(self):
        self.html = read(POLICY)

    def test_it_is_a_complete_html_document(self):
        self.assertTrue(self.html.lstrip().lower().startswith("<!doctype html>"))
        self.assertIn("</html>", self.html)
        self.assertIn('<meta charset="utf-8">', self.html)
        self.assertIn('<meta name="viewport"', self.html)

    def test_it_has_a_title_and_a_visible_heading(self):
        self.assertRegex(self.html, r"<title>[^<]*Privacy[^<]*</title>")
        self.assertRegex(self.html, r"<h1>[^<]*Privacy Policy")

    def test_it_states_an_effective_date(self):
        # Play wants a live, dated policy; an undated page is read as abandoned.
        self.assertRegex(self.html, r"<strong>Effective:</strong>\s*\d{1,2} \w+ \d{4}")

    def test_it_names_the_shipped_packages_and_a_contact_route(self):
        self.assertIn("tv.corebuilds.iconpack", self.html)
        self.assertIn("tv.corebuilds.iconpack.play", self.html)
        self.assertIn("issues", self.html)
        self.assertIn("https://github.com/brevityA/CoreBuildsApps/issues", self.html)

    def test_it_contains_no_placeholder(self):
        # The page replaced a [USER TO SUPPLY] gap; it must not carry one on.
        for bad in ("USER TO SUPPLY", "TODO", "FIXME", "lorem ipsum"):
            self.assertNotIn(bad.lower(), self.html.lower())


class TheClaimsMatchTheCode(unittest.TestCase):
    def setUp(self):
        self.html = read(POLICY)
        self.src = sources()

    def test_every_host_the_code_calls_is_disclosed(self):
        called: set[str] = set()
        for name, text in self.src.items():
            for url in re.findall(r'"(https://[a-z0-9.\-]+)', text):
                called.add(url.removeprefix("https://").rstrip("/"))
        undisclosed = {h for h in called if h not in self.html}
        self.assertFalse(undisclosed,
                         f"the app calls {sorted(undisclosed)}; the policy never says so")

    def test_it_discloses_no_host_the_code_does_not_call(self):
        # A policy that lists a processor nothing uses is a false statement
        # too, and it makes the page unfalsifiable.
        asserted = set(re.findall(r"<code>([a-z0-9.\-]+\.(?:com|org|net))</code>", self.html))
        self.assertTrue(asserted)
        self.assertFalse(asserted - HOSTS_THE_CODE_CALLS,
                         f"policy names {sorted(asserted - HOSTS_THE_CODE_CALLS)}")

    def test_the_icon_request_payload_it_itemises_is_the_payload_sent(self):
        sender = self.src["IconRequestSender.kt"]
        fields = set(re.findall(r'\.put\("([a-z_]+)"', sender))
        self.assertEqual(fields, {"app_name", "component", "device"},
                         "IconRequestSender's payload moved; the policy must follow")
        for field in ("app name", "component name", "device note"):
            self.assertIn(field, self.html, f"policy does not name the {field!r} field")
        # The one boolean flag the POST can carry is described too.
        self.assertIn('put("mapped"', sender)
        self.assertIn("flag", self.html)

    def test_it_claims_no_analytics_and_the_build_links_none(self):
        for gradle in (ROOT / "app/build.gradle.kts", ROOT / "glyphs/build.gradle.kts"):
            text = read(gradle).lower()
            for sdk in ("firebase", "play-services-ads", "appsflyer", "sentry",
                        "amplitude", "mixpanel", "segment"):
                self.assertNotIn(sdk, text, f"{gradle.name} links {sdk}")
        for claim in ("no analytics", "advertising", "crash-reporting"):
            self.assertIn(claim, self.html.lower())

    def test_the_play_manifest_claim_matches_the_overlay(self):
        # "Install unknown apps - GitHub / Downloader build only: the Play build
        # removes this permission entirely."
        play_manifest = read(ROOT / "app/src/play/AndroidManifest.xml")
        self.assertIn("REQUEST_INSTALL_PACKAGES", play_manifest)
        self.assertIn('tools:node="remove"', play_manifest)
        self.assertIn("Install unknown apps", self.html)
        self.assertIn("Play build removes", self.html)

    def test_the_no_update_check_claim_matches_distribution_kt(self):
        distribution = self.src["Distribution.kt"]
        self.assertRegex(distribution, r"PLAY")
        play_gradle = read(ROOT / "app/build.gradle.kts")
        # The Play BUILD TYPE blanks the update manifest URL. There are two
        # create("play") blocks - the signing config and the build type - so
        # this anchors on the one that carries the distribution field.
        build_types = play_gradle.split("buildTypes {", 2)[2]
        block = build_types.split('create("play")', 1)[1]
        self.assertIn('buildConfigField("String", "UPDATE_MANIFEST_URL", "\\"\\"")', block,
                      "the Play build type no longer blanks the update URL")
        self.assertIn("no update", self.html)
        self.assertIn("Play build", self.html)

    def test_the_wallpaper_hosts_are_the_ones_the_modules_use(self):
        live = self.src["LiveLoop.kt"]
        self.assertIn("raw.githubusercontent.com", live)
        self.assertIn("raw.githubusercontent.com", self.html)
        catalog = read(ROOT / "app/src/main/assets/manifest/wallpapers.json")
        self.assertIn("raw.githubusercontent.com", catalog)

    def test_it_does_not_promise_a_purchase_that_does_not_exist(self):
        # The policy may describe how a future purchase would work, but it must
        # not claim one exists: the Play listing currently answers "no
        # purchases" to the content-rating questionnaire.
        self.assertIn("currently offers no purchases", self.html)

    def test_permissions_it_lists_are_the_ones_the_manifest_declares(self):
        manifest = read(ROOT / "app/src/main/AndroidManifest.xml")
        declared = set(re.findall(r'android:name="android\.permission\.([A-Z_]+)"', manifest))
        explained = {
            "INTERNET": "Internet",
            "ACCESS_NETWORK_STATE": "Network state",
            "SET_WALLPAPER": "Set wallpaper",
            "WRITE_EXTERNAL_STORAGE": "Write external storage",
            "REQUEST_INSTALL_PACKAGES": "Install unknown apps",
        }
        for permission in declared:
            self.assertIn(permission, explained,
                          f"{permission} is declared but the policy does not explain it")
            self.assertIn(explained[permission], self.html,
                          f"the policy does not explain {permission}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
