#!/usr/bin/env python3
"""The in-app Support screens point where the repo's funding file points.

Why this exists
---------------
The icon pack (About -> Support) and Core EQ (Capability -> Support) each draw
two QR codes, GitHub Sponsors and Ko-fi, from URLs in their own strings.xml.
`.github/FUNDING.yml` names the same two accounts for the repo's Sponsor
button, and the README links them. Several copies of two URLs is how one goes
stale: a renamed Ko-fi page would leave a code on every TV that scans to a
404, and nothing in the build would notice. This holds every copy to
FUNDING.yml.

It also holds the screens to what they print. Each says "Opening this screen
sends nothing", which stays true only while the activity draws its codes and
touches no network or browser API. And the codes stay at QR version 3 (29
modules), the module size the 220dp views were sized for; a longer URL would
shrink every module without failing anything else.

Run: `python3 tests/test_support_links.py`
"""
from __future__ import annotations

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

import qrcode

ROOT = Path(__file__).resolve().parents[1]

# app -> (main source set, its SupportActivity relative to it)
APPS = {
    "icon pack": (ROOT / "app/src/main",
                  "java/tv/corebuilds/iconpack/SupportActivity.kt"),
    "Core EQ": (ROOT / "coreeq/app/src/main",
                "kotlin/tv/corebuilds/eq/SupportActivity.kt"),
}

# Anything that could make a request, or hand a URL to another app that would.
NETWORK_TOKENS = ("java.net", "HttpURLConnection", "openConnection", "okhttp",
                  "ACTION_VIEW", "Uri.parse", "WebView", "DownloadManager")


def funding() -> dict[str, str]:
    accounts = {}
    for line in (ROOT / ".github/FUNDING.yml").read_text(encoding="utf-8").splitlines():
        m = re.fullmatch(r"\s*(\w+):\s*([\w-]+)\s*(#.*)?", line)
        if m:
            accounts[m.group(1)] = m.group(2)
    return accounts


def strings(main: Path) -> dict[str, str]:
    root = ET.parse(main / "res/values/strings.xml").getroot()
    return {s.get("name"): (s.text or "") for s in root.iter("string")}


def expected_urls() -> dict[str, str]:
    accounts = funding()
    return {
        "support_sponsors_url": f"https://github.com/sponsors/{accounts['github']}",
        "support_kofi_url": f"https://ko-fi.com/{accounts['ko_fi']}",
    }


class SupportLinks(unittest.TestCase):

    def test_both_apps_match_funding_yml(self):
        for app, (main, _) in APPS.items():
            found = strings(main)
            for name, url in expected_urls().items():
                with self.subTest(app=app, string=name):
                    self.assertEqual(
                        found.get(name), url,
                        f"{app}: {name} must be the page .github/FUNDING.yml names")

    def test_readme_links_the_same_pages(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        for name, url in expected_urls().items():
            with self.subTest(string=name):
                self.assertIn(url, readme)

    def test_codes_stay_version_three(self):
        # One byte-mode segment, as the vendored encoder makes for these URLs
        # (QrCode.encodeText); coreeq's SupportQrTest checks that encoder itself.
        for name, url in expected_urls().items():
            qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M)
            qr.add_data(url, optimize=0)
            qr.make(fit=True)
            with self.subTest(string=name):
                self.assertEqual(qr.version, 3,
                                 f"{url} encodes at version {qr.version}; the "
                                 f"220dp support codes were sized for 3")

    def test_support_screens_send_nothing(self):
        for app, (main, activity) in APPS.items():
            source = (main / activity).read_text(encoding="utf-8")
            with self.subTest(app=app):
                self.assertIn("QrBitmap.render", source)
                for token in NETWORK_TOKENS:
                    self.assertNotIn(token, source,
                                     f"{app} SupportActivity says it sends nothing")

    def test_core_eq_encoder_is_the_vendored_one(self):
        # THIRD_PARTY_NOTICES.md records one set of upstream hashes for both
        # copies; that holds only while they stay byte-identical.
        pack = ROOT / "app/src/main/java/io/nayuki/qrcodegen"
        coreeq = ROOT / "coreeq/app/src/main/java/io/nayuki/qrcodegen"
        names = sorted(p.name for p in pack.glob("*.java"))
        self.assertEqual(names, sorted(p.name for p in coreeq.glob("*.java")))
        for name in names:
            with self.subTest(file=name):
                self.assertEqual((pack / name).read_bytes(), (coreeq / name).read_bytes())

    def test_core_eq_opens_support_from_capability(self):
        # The icon pack's entry is pinned by test_navigation_graph.py, which
        # walks every manifest activity back to MainActivity. Core EQ has no
        # such walk, so its one entry point is pinned here.
        main = APPS["Core EQ"][0]
        capability = (main / "kotlin/tv/corebuilds/eq/CapabilityActivity.kt").read_text(
            encoding="utf-8")
        manifest = (main / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn("Intent(this, SupportActivity::class.java)", capability)
        self.assertIn('android:name=".SupportActivity"', manifest)


if __name__ == "__main__":
    unittest.main()
