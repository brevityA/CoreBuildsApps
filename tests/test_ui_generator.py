#!/usr/bin/env python3
"""Regressions for the Core Builds UI Studio generator.

Covers: every preset stages clean (0 errors, 0 warnings), generated layouts
carry no hardcoded copy, generated Kotlin wires motion + initial focus, the
auditor catches isolated views and dangling links, hidden bars stay exempt,
and spec validation rejects bad input with plain messages.

Run:  python tests/test_ui_generator.py
"""
from __future__ import annotations

import re
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

from ui.generate import build  # noqa: E402
from ui.spec import list_presets, load_preset, normalise  # noqa: E402
from validate_ui import audit_layout  # noqa: E402

LAYOUTS = ROOT / "app" / "src" / "main" / "res" / "layout"
HARDCODED = re.compile(r'android:(text|hint)="(?!@)')


def _write_tmp(suffix: str, content: str) -> Path:
    tmp = tempfile.NamedTemporaryFile(
        mode="w", suffix=suffix, delete=False, encoding="utf-8")
    tmp.write(content)
    tmp.close()
    return Path(tmp.name)


class TestPresets(unittest.TestCase):
    def test_every_preset_stages_clean(self):
        self.assertGreaterEqual(len(list_presets()), 5)
        for preset in list_presets():
            with self.subTest(preset=preset):
                with tempfile.TemporaryDirectory() as tmp:
                    spec, receipt = build(load_preset(preset), Path(tmp))
                    self.assertEqual(receipt["errors"], [])
                    self.assertEqual(receipt["warnings"], [])
                    self.assertGreater(receipt["focusable"], 0)
                    self.assertGreater(receipt["links"], 0)
                    # layout parses + carries no hardcoded copy
                    layout = Path(tmp) / f"res/layout/{spec.layout}.xml"
                    ET.parse(layout)
                    text = layout.read_text(encoding="utf-8")
                    self.assertIsNone(
                        HARDCODED.search(text),
                        f"hardcoded copy in {spec.layout}.xml")
                    # kotlin wires motion + initial focus
                    kotlin = (Path(tmp) / f"java/{spec.activity}.kt").read_text(
                        encoding="utf-8")
                    self.assertIn("TvFocus", kotlin)
                    self.assertIn("focusFirst()", kotlin)
                    self.assertIn(f"R.id.{receipt['initial']}", kotlin)
                    # custom grids ship an adapter + item layout
                    if any(b["type"] == "grid" and b.get("source") == "custom"
                           for b in spec.blocks):
                        self.assertIn(f"java/{spec.screen}Adapter.kt",
                                      receipt["files"])
                        self.assertIn(f"res/layout/item_{spec.prefix}.xml",
                                      receipt["files"])

    def test_name_and_title_overrides(self):
        raw = load_preset("browser")
        raw["screen"] = "Showcase"
        for block in raw["blocks"]:
            if block["type"] == "header":
                block["title"] = "Showcase"
        with tempfile.TemporaryDirectory() as tmp:
            spec, receipt = build(raw, Path(tmp))
            self.assertEqual(spec.activity, "ShowcaseActivity")
            self.assertEqual(receipt["errors"], [])
            strings = (Path(tmp) / "res/values/strings_showcase.xml").read_text(
                encoding="utf-8")
            self.assertIn(">Showcase</string>", strings)


class TestExistingLayouts(unittest.TestCase):
    def test_shipped_layouts_have_no_audit_errors(self):
        # The auditor must accept the hand-written screens it did not generate.
        # (Warnings are allowed: two buttons rely on spatial search today.)
        for path in sorted(LAYOUTS.glob("*.xml")):
            with self.subTest(layout=path.name):
                audit = audit_layout(path)
                self.assertEqual(audit.errors, [])


class TestSpecValidation(unittest.TestCase):
    def _base(self) -> dict:
        return {
            "screen": "Demo",
            "blocks": [
                {"type": "header", "title": "Demo",
                 "side": [{"id": "demo_ok", "style": "cta", "text": "OK"}]},
            ],
        }

    def test_rejects(self):
        cases = {
            "bad screen name": {"screen": "demo-screen"},
            "missing header": {"blocks": [{"type": "hint", "text": "x"}]},
            "unknown block": {"blocks": [{"type": "header", "title": "t"},
                                           {"type": "carousel"}]},
            "duplicate id": {"blocks": [
                {"type": "header", "title": "t",
                 "side": [{"id": "dup", "text": "a"}, {"id": "dup", "text": "b"}]}]},
            "bad initial": {"dpad": {"initialFocus": "nope"}},
            "initial in hidden bar": {
                "blocks": [
                    {"type": "header", "title": "t",
                     "side": [{"id": "demo_ok", "text": "OK"}]},
                    {"type": "updatebar", "id": "demo_up"},
                ],
                "dpad": {"initialFocus": "demo_up_button"}},
            "chips mismatch": {"blocks": [
                {"type": "header", "title": "t",
                 "side": [{"id": "demo_ok", "text": "OK"}]},
                {"type": "chips", "id": "demo_chips",
                 "keys": ["ALL", "X"], "labels": ["All"]}]},
            "fromCatalog without walls": {"blocks": [
                {"type": "header", "title": "t",
                 "side": [{"id": "demo_ok", "text": "OK"}]},
                {"type": "chips", "id": "demo_chips", "fromCatalog": True},
                {"type": "grid", "id": "demo_grid", "source": "icons"}]},
        }
        for name, override in cases.items():
            with self.subTest(case=name):
                raw = self._base()
                raw.update(override)
                with self.assertRaises(ValueError, msg=name):
                    normalise(raw)


class TestAuditor(unittest.TestCase):
    LAYOUT_OPEN = ('<?xml version="1.0" encoding="utf-8"?>\n'
                   '<LinearLayout xmlns:android='
                   '"http://schemas.android.com/apk/res/android"\n'
                   '    android:layout_width="match_parent"\n'
                   '    android:layout_height="match_parent">\n')
    LAYOUT_CLOSE = '</LinearLayout>\n'

    def _audit(self, body: str, **kwargs) -> object:
        path = _write_tmp(".xml", self.LAYOUT_OPEN + body + self.LAYOUT_CLOSE)
        try:
            return audit_layout(path, **kwargs)
        finally:
            path.unlink()

    def test_dangling_link_is_error(self):
        audit = self._audit(
            '    <TextView android:id="@+id/a"\n'
            '        android:focusable="true"\n'
            '        android:nextFocusDown="@id/ghost" />\n')
        self.assertTrue(any("ghost" in e for e in audit.errors))

    def test_isolated_view_is_error(self):
        audit = self._audit(
            '    <TextView android:id="@+id/a"\n'
            '        android:focusable="true"\n'
            '        android:focusableInTouchMode="true"\n'
            '        android:nextFocusDown="@id/b" />\n'
            '    <TextView android:id="@+id/b"\n'
            '        android:focusable="true"\n'
            '        android:focusableInTouchMode="true" />\n'
            '    <TextView android:id="@+id/lonely"\n'
            '        android:focusable="true"\n'
            '        android:focusableInTouchMode="true" />\n')
        self.assertTrue(any("lonely" in e and "isolated" in e
                            for e in audit.errors))

    def test_hidden_bar_is_exempt_but_code_checked(self):
        body = (
            '    <TextView android:id="@+id/a"\n'
            '        android:focusable="true"\n'
            '        android:focusableInTouchMode="true" />\n'
            '    <LinearLayout android:id="@+id/bar"\n'
            '        android:visibility="gone">\n'
            '        <TextView android:id="@+id/bar_btn"\n'
            '            android:focusable="true" />\n'
            '    </LinearLayout>\n')
        audit = self._audit(body)
        self.assertEqual(audit.errors, [])
        audit = self._audit(body, kotlin="findViewById(R.id.other)")
        self.assertTrue(any("bar_btn" in w for w in audit.warnings))
        audit = self._audit(body,
                            kotlin="TvFocus.reveal(findViewById(R.id.bar_btn))")
        self.assertFalse(any("bar_btn" in w for w in audit.warnings))

    def test_item_layout_rules(self):
        with tempfile.TemporaryDirectory() as tmp:
            good = Path(tmp) / "item_good.xml"
            good.write_text(self.LAYOUT_OPEN.replace(
                "<LinearLayout",
                '<LinearLayout android:focusable="true"', 1)
                + self.LAYOUT_CLOSE, encoding="utf-8")
            bad = Path(tmp) / "item_bad.xml"
            bad.write_text(self.LAYOUT_OPEN + self.LAYOUT_CLOSE,
                           encoding="utf-8")
            self.assertEqual(audit_layout(good).errors, [])
            self.assertTrue(audit_layout(bad).errors)


class TestStringsManifestHelpers(unittest.TestCase):
    def test_merge_and_insert(self):
        from ui.generate import _insert_manifest, _merge_strings
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "values").mkdir()
            (root / "values" / "strings.xml").write_text(
                '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                '    <string name="keep">Keep</string>\n</resources>\n',
                encoding="utf-8")
            (root / "AndroidManifest.xml").write_text(
                '<manifest>\n    <application>\n    </application>\n</manifest>\n',
                encoding="utf-8")
            added, skipped = _merge_strings(
                root, {"keep": "Keep", "fresh": "Fresh & <new>"})
            self.assertEqual((added, skipped), (1, 1))
            text = (root / "values" / "strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="fresh">Fresh &amp; &lt;new&gt;</string>',
                          text)
            entry = ('        <activity android:name=".DemoActivity" />\n')
            self.assertTrue(_insert_manifest(root, entry, "DemoActivity"))
            self.assertFalse(_insert_manifest(root, entry, "DemoActivity"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
