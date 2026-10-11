#!/usr/bin/env python3
"""Contract tests for tools/issue_triage.py (the icon-request auto-reply).

Run: python3 tests/test_issue_triage.py
"""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import issue_triage as it  # noqa: E402

FORMS = ROOT / ".github" / "ISSUE_TEMPLATE"

GOOD_NEW = """### App name

Pixee

### Component name

com.pixee.tv/com.pixee.tv.MainActivity

### Store or download link

https://play.google.com/store/apps/details?id=com.pixee.tv

### Device type

Android TV / Google TV

### Anything else

_No response_

### Confirm

- [x] I checked docs/IconPackList.md and this app isn't already covered.
"""

PACKAGE_ONLY = GOOD_NEW.replace(
    "com.pixee.tv/com.pixee.tv.MainActivity", "com.pixee.tv")

GOOD_MAP = """### App name

Stremio

### The component name on YOUR device

com.stremio.one/com.stremio.tv.SomeOtherActivity

### Device and OS

Nvidia Shield Pro · Android 11

### Launcher

Projectivy

### Confirm

- [x] The pack is selected in my launcher's icon-pack setting.
- [x] I restarted the launcher (or rebooted) after applying.
"""


class ParseTests(unittest.TestCase):
    def test_sections_and_blank_answers(self):
        s = it.parse_sections(GOOD_NEW)
        self.assertEqual(s["App name"], "Pixee")
        self.assertEqual(s["Anything else"], "_No response_")


class CheckTests(unittest.TestCase):
    def test_complete_new_request_passes(self):
        self.assertEqual(it.check(GOOD_NEW, "new"), [])

    def test_package_only_component_is_flagged_with_the_command(self):
        problems = it.check(PACKAGE_ONLY, "new")
        self.assertEqual(len(problems), 1)
        self.assertIn("package name only", problems[0])
        self.assertIn("resolve-activity", problems[0])

    def test_blank_component_is_allowed_for_new_request(self):
        body = GOOD_NEW.replace(
            "com.pixee.tv/com.pixee.tv.MainActivity", "_No response_")
        self.assertEqual(it.check(body, "new"), [])

    def test_bad_link_empty_device_and_unticked_confirm(self):
        body = GOOD_NEW.replace(
            "https://play.google.com/store/apps/details?id=com.pixee.tv",
            "play store pixee").replace(
            "Android TV / Google TV", "Toaster").replace(
            "- [x] I checked", "- [ ] I checked")
        problems = it.check(body, "new")
        joined = "\n".join(problems)
        self.assertIn("Store or download link", joined)
        self.assertIn("Device type", joined)
        self.assertIn("Confirm", joined)

    def test_required_field_missing_is_reported(self):
        body = GOOD_NEW.replace("Pixee", "_No response_")
        self.assertTrue(any("App name" in p and "required" in p
                            for p in it.check(body, "new")))

    def test_complete_mapping_passes(self):
        self.assertEqual(it.check(GOOD_MAP, "mapping"), [])

    def test_mapping_needs_both_ticks_and_a_real_launcher(self):
        body = GOOD_MAP.replace("- [x] I restarted", "- [ ] I restarted").replace(
            "Projectivy", "Kodi")
        joined = "\n".join(it.check(body, "mapping"))
        self.assertIn("Confirm", joined)
        self.assertIn("Launcher", joined)

    def test_component_with_no_slash_in_mapping_is_flagged(self):
        body = GOOD_MAP.replace(
            "com.stremio.one/com.stremio.tv.SomeOtherActivity", "com.stremio.one")
        self.assertIn("package name only", "\n".join(it.check(body, "mapping")))

    def test_overlong_app_name_is_flagged(self):
        body = GOOD_NEW.replace("Pixee", "x" * (it.MAX_APP_NAME + 1))
        self.assertTrue(any("longer than" in p for p in it.check(body, "new")))


class CommentTests(unittest.TestCase):
    def test_reply_points_at_the_pack_scanner_and_its_strings(self):
        text = it.comment_for(["x"], "new")
        self.assertIn("Missing icons", text)  # settings_audit_title / missing_entry
        self.assertIn("Request", text)        # audit_action_request
        self.assertIn("docs/ADB_SCANNING.md", text)
        self.assertTrue(text.startswith(it.MARKER))

    def test_scanner_strings_exist_in_the_app(self):
        strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
        self.assertIn(">Missing icons<", strings)
        self.assertIn(">REQUEST<", strings)
        self.assertTrue((ROOT / "docs" / "ADB_SCANNING.md").exists())


class TemplateRoutingTests(unittest.TestCase):
    def test_labels_and_titles_route_to_the_right_template(self):
        self.assertEqual(it.template_for(["icon request"], ""), "new")
        self.assertEqual(it.template_for(["mapping"], ""), "mapping")
        self.assertEqual(it.template_for([], "[Icon] Pixee"), "new")
        self.assertEqual(it.template_for([], "[Not applying] X"), "mapping")
        self.assertIsNone(it.template_for(["bug"], "Crash on launch"))


class FormDriftTests(unittest.TestCase):
    """The checker's headings and options must match the issue forms."""

    def _form(self, name: str) -> str:
        return (FORMS / name).read_text(encoding="utf-8")

    def test_new_request_headings_exist_in_form(self):
        text = self._form("1.new_icon_request.yml")
        for f in it.NEW_REQUEST:
            self.assertIn(f"label: {f.heading}", text, f.heading)

    def test_mapping_headings_exist_in_form(self):
        text = self._form("2.icon_not_applying.yml")
        for f in it.NOT_APPLYING:
            self.assertIn(f"label: {f.heading}", text, f.heading)

    def test_dropdown_options_match_form(self):
        new = self._form("1.new_icon_request.yml")
        mapping = self._form("2.icon_not_applying.yml")
        for f in it.NEW_REQUEST + it.NOT_APPLYING:
            if f.kind != "choice":
                continue
            src = new if f in it.NEW_REQUEST else mapping
            for opt in f.options:
                self.assertIn(f"- {opt}", src, opt)


class ActionTests(unittest.TestCase):
    def _event(self, body, labels=("icon request",), state="open", title="[Icon] Pixee"):
        return {
            "repository": {"full_name": "brevityA/CoreBuildsApps"},
            "issue": {"number": 282, "title": title, "body": body, "state": state,
                      "labels": [{"name": n} for n in labels]},
        }

    def _run(self, event, existing=None):
        calls = []

        def fake_gh(args, dry_run):
            calls.append(args)
            if args[:2] == ["api", f"repos/brevityA/CoreBuildsApps/issues/282/comments"]:
                return json.dumps(existing or [])
            return ""

        with mock.patch.object(it, "_gh", side_effect=fake_gh):
            rc = it.act(event, dry_run=False)
        return rc, calls

    def test_incomplete_request_gets_label_and_comment(self):
        rc, calls = self._run(self._event(PACKAGE_ONLY))
        self.assertEqual(rc, 0)
        self.assertIn(["issue", "edit", "282", "--add-label", it.LABEL,
                       "-R", "brevityA/CoreBuildsApps"], calls)
        self.assertTrue(any(c[:2] == ["issue", "comment"] for c in calls))

    def test_existing_marker_comment_is_edited_not_duplicated(self):
        existing = [{"id": 99, "body": it.MARKER + " old",
                     "user": {"type": "Bot"}}]
        _, calls = self._run(self._event(PACKAGE_ONLY), existing)
        self.assertFalse(any(c[:2] == ["issue", "comment"] for c in calls))
        self.assertTrue(any(c[:3] == ["api", "-X", "PATCH"]
                            and "comments/99" in c[3] for c in calls))

    def test_complete_request_only_clears_the_label(self):
        event = self._event(GOOD_NEW, labels=("icon request", it.LABEL))
        _, calls = self._run(event)
        self.assertIn(["issue", "edit", "282", "--remove-label", it.LABEL,
                       "-R", "brevityA/CoreBuildsApps"], calls)
        self.assertFalse(any(c[:2] == ["issue", "comment"] for c in calls))

    def test_closed_and_unrelated_issues_are_ignored(self):
        _, calls = self._run(self._event(PACKAGE_ONLY, state="closed"))
        self.assertEqual(calls, [])
        _, calls = self._run(self._event(PACKAGE_ONLY, labels=("bug",),
                                         title="Crash"))
        self.assertEqual(calls, [])

    def test_body_file_cli_exit_codes(self):
        with tempfile.TemporaryDirectory() as d:
            good = Path(d) / "good.md"
            good.write_text(GOOD_NEW, encoding="utf-8")
            bad = Path(d) / "bad.md"
            bad.write_text(PACKAGE_ONLY, encoding="utf-8")
            self.assertEqual(it.main(["--body-file", str(good)]), 0)
            self.assertEqual(it.main(["--body-file", str(bad)]), 1)


if __name__ == "__main__":
    unittest.main()
