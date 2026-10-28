#!/usr/bin/env python3
"""Core Builds Plus "Request all": every missing app on the TV in one press.

The app sends up to ten apps to the broker's /v1/batch route
(tools/icon_request_broker), which refuses a batch unless Google confirms the
Plus purchase and files each app as a priority request. Two halves, one
contract; this file holds the halves together where a drift would be silent:

1. the app's limits are the broker's (BatchRequest.MAX / PER_DAY against
   BATCH_MAX / BATCH_PER_DAY), and the user is told the same numbers;
2. the batch payload carries exactly the single press's fields, so the
   privacy policy and Data safety answers stay true;
3. the button is a Plus control: hidden outside the Play build, locked
   without Plus, and two presses before anything is sent;
4. the policy, the Data safety answers and the Console walkthrough say so.

Run: python3 tests/test_batch_requests.py
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KT = ROOT / "app/src/main/java/tv/corebuilds/iconpack"
BROKER = ROOT / "tools/icon_request_broker"


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def broker_const(name: str) -> int:
    return int(re.search(rf"export const {name} = (\d+);", read(BROKER / "constants.mjs")).group(1))


class ContractTests(unittest.TestCase):
    def test_the_app_and_the_broker_share_their_limits(self):
        batch = read(KT / "BatchRequest.kt")
        self.assertIn(f"const val MAX = {broker_const('BATCH_MAX')}", batch)
        self.assertIn(f"const val PER_DAY = {broker_const('BATCH_PER_DAY')}", batch)
        # The explanation the first press shows takes both numbers from there.
        auditor = read(KT / "AuditorActivity.kt")
        self.assertIn("BatchRequest.MAX, BatchRequest.PER_DAY", auditor)

    def test_the_batch_route_is_the_one_the_broker_serves(self):
        self.assertIn('+ "/v1/batch"', read(KT / "BatchRequest.kt"))
        self.assertIn('url.pathname === "/v1/batch"', read(BROKER / "worker.mjs"))

    def test_the_payload_is_the_single_press_fields_and_nothing_more(self):
        sender = read(KT / "IconRequestSender.kt")
        batch = sender.split("private fun postBatch(", 1)[1].split("\n    /** Worker thread only.", 1)[0]
        keys = set(re.findall(r'\bput\("(\w+)"', batch))
        self.assertEqual(keys, {"app_name", "component", "mapped", "device", "plus_token", "apps"})
        worker = read(BROKER / "worker.mjs")
        self.assertIn('!["apps", "device", "plus_token"].includes(k)', worker)
        self.assertIn('!["app_name", "component", "mapped"].includes(k)', worker)


class ControlTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.auditor = read(KT / "AuditorActivity.kt")

    def test_the_button_is_a_plus_control(self):
        show = self.auditor.split("private fun showRequestAll()", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("Plus.shown(this)", show)
        self.assertIn('getString(R.string.audit_request_endpoint).isNotBlank()', show)
        self.assertIn("!Plus.owned(this) -> getString(R.string.audit_request_all_locked)", show)
        press = self.auditor.split("private fun requestAll()", 1)[1].split("\n    }\n\n", 1)[0]
        # The purchase, not the trial: the broker files a batch only on a
        # token Google confirms, so the trial must not arm it.
        self.assertIn("if (!Plus.unlocked(this, purchase = true) || requestInFlight) return", press)
        layout = read(ROOT / "app/src/main/res/layout/activity_auditor.xml")
        button = layout.split('android:id="@+id/audit_request_all"', 1)[1].split("/>", 1)[0]
        self.assertIn('android:visibility="gone"', button)

    def test_two_presses_before_anything_is_sent(self):
        press = self.auditor.split("private fun requestAll()", 1)[1].split("\n    }\n\n", 1)[0]
        self.assertLess(press.index("if (now > armedUntil)"), press.index("IconRequestSender.sendBatch("))
        arm = press.split("if (now > armedUntil)", 1)[1].split("\n        }\n", 1)[0]
        self.assertIn("return", arm)

    def test_each_filed_issue_reaches_my_requests(self):
        press = self.auditor.split("private fun requestAll()", 1)[1]
        self.assertIn("RequestLog.record(this, r.issue, item.label, item.component, item.mapped)", press)
        # Apps already in My requests are not sent again.
        self.assertIn("RequestLog.all(this).map { it.component }.toSet()", self.auditor)


class DisclosureTests(unittest.TestCase):
    def test_the_privacy_policy_names_the_batch(self):
        html = read(ROOT / "docs/privacy.html")
        self.assertIn("Request all", html)
        self.assertIn(f"up to {broker_const('BATCH_MAX')}", html)

    def test_the_data_safety_answer_covers_the_batch(self):
        listing = read(ROOT / "tools/build_play_listing.py")
        installed = listing.split('"type": "App activity > Installed apps"', 1)[1].split("},", 1)[0]
        self.assertIn("Request all", installed)

    def test_the_walkthrough_tests_it_before_anyone_pays(self):
        console = read(ROOT / "docs/PLAY-CONSOLE.md")
        step = console.split("8. **Internal test before anyone pays.**", 1)[1].split("\n\n", 1)[0]
        self.assertIn("Request all", step)


if __name__ == "__main__":
    unittest.main()
