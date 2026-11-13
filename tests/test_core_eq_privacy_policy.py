#!/usr/bin/env python3
"""
docs/coreeq-privacy.html is held to Core EQ's source, the way
tests/test_privacy_policy.py holds the icon pack's page to the icon pack.

A privacy page that drifts from the code is worse than none: Core EQ records
through a microphone, so the page has to stay exact. Each claim the page makes
about what leaves a device has a counterpart checked here:

  * the hosts the page names are exactly the hosts Core EQ can fetch from;
  * the set of source files that open a network connection is pinned, so new
    network code fails until the page is reviewed;
  * every permission in the manifest is explained on the page, and the page
    explains none that is not requested;
  * the TV-phone channel is AES-GCM, the phone really sends the recording, the
    companion stops when its screen closes, pairing data stays out of backups;
  * Google's code scanner is the only third-party data path: no analytics,
    crash or ad SDK is declared, and the scanner's auto-zoom is not enabled;
  * Core EQ opens a file only one the user picked to import, and its only
    writes outside its own storage are the exports in Downloads/CoreEQ, which
    clearing its data does not remove;
  * every on-screen label the page quotes exists in the app's strings.

What it cannot prove: what Google Play services itself sends. The page quotes
Google's own disclosure for that and says so.

Run: python3 tests/test_core_eq_privacy_policy.py
"""
from __future__ import annotations

import html
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "docs" / "coreeq-privacy.html"
APP = ROOT / "coreeq" / "app"
KT = APP / "src/main/kotlin/tv/corebuilds/eq"
MANIFEST = APP / "src/main/AndroidManifest.xml"
GRADLE = APP / "build.gradle.kts"
STRINGS = APP / "src/main/res/values/strings.xml"
BACKUP_RULES = APP / "src/main/res/xml/backup_rules.xml"

# The only Core EQ sources allowed to open a connection. Adding one means the
# page's table must be reviewed before this list is.
NETWORK_FILES = {
    "update/UpdateChecker.kt",
    "update/UpdateInstaller.kt",
    "companion/CompanionClient.kt",
    "companion/CompanionServer.kt",
    "companion/ListenerPort.kt",
    "companion/TvDiscovery.kt",
}
NETWORK_TOKENS = ("HttpURLConnection", "openConnection", "Socket(", "ServerSocket", "NsdManager",
                  "DatagramSocket", "okhttp", "WebView")

# The only sources that read a file from outside the app, each behind the
# system file picker, and the only one that writes to shared storage.
FILE_READERS = {"ProfilesActivity.kt", "MeasureActivity.kt", "ManualEqActivity.kt"}
FILE_READ_TOKENS = ("openInputStream", "openFileDescriptor")
SHARED_WRITERS = {"export/Formats.kt"}
SHARED_WRITE_TOKENS = ("MediaStore", "openOutputStream", "getExternalStoragePublicDirectory(")
EXPORT_CALLERS = {"ProfilesActivity.kt", "CapabilityActivity.kt", "ManualEqActivity.kt"}

# Every permission the manifest requests, and the words the page uses for it.
PERMISSION_WORDS = {
    "android.permission.RECORD_AUDIO": "Record audio",
    "android.permission.MODIFY_AUDIO_SETTINGS": "Modify audio settings",
    "android.permission.DUMP": "Dump",
    "android.permission.WRITE_SETTINGS": "Modify system settings",
    "android.permission.FOREGROUND_SERVICE": "Foreground service",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK": "media-playback",
    "android.permission.RECEIVE_BOOT_COMPLETED": "Run at startup",
    "android.permission.SYSTEM_ALERT_WINDOW": "Display over other apps",
    "android.permission.POST_NOTIFICATIONS": "Post notifications",
    "android.permission.INTERNET": "Internet",
    "android.permission.ACCESS_NETWORK_STATE": "network state",
    "android.permission.ACCESS_LOCAL_NETWORK": "Local network access",
    "android.permission.REQUEST_INSTALL_PACKAGES": "Install unknown apps",
    "android.permission.WRITE_EXTERNAL_STORAGE": "Write external storage",
}

# SDKs the page promises Core EQ does not declare.
FORBIDDEN_SDKS = ("firebase-analytics", "firebase-crashlytics", "crashlytics", "sentry",
                  "play-services-ads", "play-services-analytics", "appcenter", "bugsnag",
                  "mixpanel", "amplitude", "segment", "adjust", "appsflyer")

# On-screen labels the page quotes, and the string resources that must hold them.
QUOTED_LABELS = {
    "Check for updates on open": "update_auto_on",
    "Check for updates": "update_check_now",
    "Phone companion": "companion_tv_title",
    "Scan TV QR": "phone_scan_qr_button",
    "Diagnostics summary": "action_diagnostics",
    "Copy": "diagnostics_copy",
    "Unpair this phone on the TV": "phone_unpair_button",
    "Forget TV on this phone": "phone_forget_button",
    "Import backup": "action_import_profile",
    "Import REW measurement": "measure_import_rew",
    "Import target file": "measure_target_import_button",
    "Import preset or file": "manual_eq_import_file",
    "Export": "action_export",
    "Export for TV settings": "action_tv_export",
    "Export as a text file": "manual_eq_tv_values_export",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def page_text() -> str:
    raw = read(POLICY)
    raw = re.sub(r"<style.*?</style>", "", raw, flags=re.S)
    return html.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", raw)))


def code_hosts() -> set[str]:
    """Every host Core EQ's own code can fetch from."""
    hosts = set()
    rules = read(KT / "update/UpdateRules.kt")
    hosts |= set(re.findall(r'"https://([a-z0-9.-]+)/', rules))
    installer = read(KT / "update/UpdateInstaller.kt")
    allowed = re.search(r"ALLOWED_HOSTS\s*=\s*setOf\((.*?)\)", installer, re.S)
    assert allowed, "UpdateInstaller.ALLOWED_HOSTS not found"
    hosts |= set(re.findall(r'"([a-z0-9.-]+)"', allowed.group(1)))
    hosts |= set(re.findall(r'https://([a-z0-9.-]+)/', read(GRADLE)))
    return hosts


class CoreEqPrivacyPolicy(unittest.TestCase):

    def setUp(self) -> None:
        self.html = read(POLICY)
        self.text = page_text()

    def test_is_a_servable_page(self) -> None:
        self.assertTrue(self.html.lstrip().startswith("<!DOCTYPE html>"))
        self.assertIn("<title>Core EQ — Privacy Policy</title>", self.html)
        self.assertRegex(self.text, r"Effective: \d{1,2} [A-Z][a-z]+ \d{4}\.")
        self.assertIn("https://github.com/brevityA/CoreBuildsApps/issues", self.html)
        self.assertIn("tv.corebuilds.eq", self.text)

    def test_named_hosts_are_exactly_the_hosts_the_code_fetches(self) -> None:
        named = set(re.findall(r"<code>([a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|net|org|io))</code>", self.html))
        self.assertEqual(code_hosts(), named,
                         "the page's hosts must be exactly the hosts Core EQ can fetch from")

    def test_network_code_is_where_the_page_says(self) -> None:
        found = set()
        for path in KT.rglob("*.kt"):
            source = read(path)
            if any(token in source for token in NETWORK_TOKENS):
                found.add(str(path.relative_to(KT)))
        self.assertEqual(NETWORK_FILES, found,
                         "a new network path needs a row on docs/coreeq-privacy.html first")

    def test_files_are_read_only_when_the_user_picks_one(self) -> None:
        readers = set()
        for path in KT.rglob("*.kt"):
            source = read(path)
            if any(token in source for token in FILE_READ_TOKENS):
                readers.add(str(path.relative_to(KT)))
        self.assertEqual(FILE_READERS, readers,
                         "a new file read needs a line on docs/coreeq-privacy.html first")
        for name in FILE_READERS:
            with self.subTest(reader=name):
                self.assertIn("FilePicker.open(", read(KT / name),
                              "every file Core EQ reads must be one the user picked")
        self.assertIn("opens a file only when you choose one to import", self.text)
        self.assertIn("reads that one file and nothing else", self.text)

    def test_exports_are_the_only_shared_storage_writes(self) -> None:
        writers = set()
        callers = set()
        for path in KT.rglob("*.kt"):
            source = read(path)
            relative = str(path.relative_to(KT))
            if any(token in source for token in SHARED_WRITE_TOKENS):
                writers.add(relative)
            if "Formats.exportToDevice(" in source:
                callers.add(relative)
        self.assertEqual(SHARED_WRITERS, writers,
                         "a new write outside the app's storage needs a line on the page first")
        self.assertEqual(EXPORT_CALLERS, callers,
                         "a new export needs naming under 'What Core EQ keeps' first")
        formats = read(KT / "export/Formats.kt")
        self.assertIn('Environment.DIRECTORY_DOWNLOADS + "/CoreEQ"', formats)
        self.assertIn("MediaStore.Downloads.EXTERNAL_CONTENT_URI", formats)
        # Shared-storage files outlive the app's data and the app itself.
        self.assertIn("Exports are not removed, even when you uninstall Core EQ", self.text)
        self.assertIn("Downloads/CoreEQ", self.text)

    def test_every_permission_is_explained_and_none_invented(self) -> None:
        requested = set(re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', read(MANIFEST), re.S))
        self.assertEqual(set(PERMISSION_WORDS), requested,
                         "explain every manifest permission on the page, and only those")
        for permission, words in PERMISSION_WORDS.items():
            with self.subTest(permission=permission):
                self.assertIn(words, self.text)
        self.assertNotIn("android.permission.CAMERA", requested)
        self.assertIn("does not need the camera permission", self.text)

    def test_the_phone_link_claims(self) -> None:
        self.assertIn('Cipher.getInstance("AES/GCM/NoPadding")', read(KT / "companion/CompanionCrypto.kt"))
        self.assertIn("AES-GCM", self.text)
        # The phone really sends the recording; the page must not say otherwise.
        self.assertIn('"pcm16_le_base64"', read(KT / "companion/PhoneCompanionActivity.kt"))
        self.assertIn("the recording itself", self.text)
        self.assertIn('SERVICE_TYPE = "_coreeq._tcp."', read(KT / "companion/TvDiscovery.kt"))
        self.assertIn("_coreeq._tcp.", self.text)
        companion = read(KT / "companion/CompanionActivity.kt")
        self.assertRegex(companion, r"override fun onStop\(\)[^}]*closeVisibleServer\(\)", )
        self.assertRegex(companion, r"private fun closeVisibleServer\(\) \{\s*advertiser\.stop\(\)")
        rules = read(BACKUP_RULES)
        for prefs in ("core_eq_companion.xml", "core_eq_phone_pairing.xml"):
            self.assertIn(f'path="{prefs}"', rules)
        self.assertIn("pairing files are excluded", self.text)

    def test_google_scanner_is_the_only_third_party_path(self) -> None:
        gradle = read(GRADLE)
        for sdk in FORBIDDEN_SDKS:
            with self.subTest(sdk=sdk):
                self.assertNotIn(sdk, gradle)
        self.assertIn("play-services-code-scanner", gradle)
        self.assertIn("Google's code scanner", self.text)
        self.assertIn("https://developers.google.com/ml-kit/android-data-disclosure", self.html)
        sources = "".join(read(p) for p in KT.rglob("*.kt"))
        self.assertNotIn("enableAutoZoom", sources)
        self.assertIn("auto-zoom", self.text)

    def test_update_claims(self) -> None:
        gradle = read(GRADLE)
        debug = re.search(r"debug \{(.*?)\n        \}", gradle, re.S)
        assert debug
        self.assertIn('"UPDATE_MANIFEST_URL", "\\"\\""', debug.group(1),
                      "the page says the test package has no update check")
        prefs = read(KT / "update/UpdatePrefs.kt")
        self.assertRegex(prefs, r"getBoolean\(KEY_CHECKS_ENABLED, true\)",
                         "the page says update checks are on by default")
        self.assertIn("the default", self.text)

    def test_quoted_labels_exist_in_the_app(self) -> None:
        strings = read(STRINGS)
        for label, name in QUOTED_LABELS.items():
            with self.subTest(label=label):
                self.assertIn(label, self.text)
                value = re.search(rf'<string name="{name}">([^<]*)</string>', strings)
                self.assertIsNotNone(value, f"strings.xml has no {name}")
                assert value is not None
                self.assertIn(label, value.group(1).replace("\\'", "'"))

    def test_the_guide_links_the_page(self) -> None:
        self.assertIn("coreeq-privacy.html", read(ROOT / "docs/apps/core-eq.md"))


if __name__ == "__main__":
    unittest.main()
