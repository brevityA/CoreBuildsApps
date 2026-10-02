#!/usr/bin/env python3
"""Every Android string resource must compile.

Why this exists
---------------
The 1.9.9 AT4K setup walk added a string written `didn\\\\'t` - a doubled
backslash. To aapt2 that is an escaped backslash followed by a bare
apostrophe, and a bare apostrophe in an unquoted string is a compile error
("Invalid unicode escape sequence in string" / "does not contain a valid
string resource"). The resource merge failed, so the icon pack APK was never
built and three CI jobs went red.

Nothing short of the Android build caught it, and the agents that write most
strings here work in sandboxes with no Android SDK: they watch the Python
gates pass and push. This gate needs nothing but the XML, so it runs
everywhere those gates do and fails on the line that broke.

The rules are aapt2's, for text outside double quotes:
- an apostrophe must be escaped as \\'
- a \\u escape must be followed by exactly four hex digits
- a backslash may only start a known escape

Run: `python3 tests/test_string_resources.py`
"""
from __future__ import annotations

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKIP_PARTS = {"build", "node_modules", ".gradle", "intermediates", "assets"}
KNOWN_ESCAPES = set("'\"\\@?nrtu")


def resource_files() -> list[Path]:
    out = []
    for path in ROOT.glob("**/src/*/res/values*/*.xml"):
        if SKIP_PARTS.intersection(path.relative_to(ROOT).parts):
            continue
        out.append(path)
    return sorted(out)


def problems_in(text: str) -> list[str]:
    """What aapt2 would reject in one string's text, outside quoted runs."""
    found = []
    quoted = False
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == "\\":
            nxt = text[i + 1] if i + 1 < len(text) else ""
            if nxt not in KNOWN_ESCAPES:
                found.append(f"unknown escape \\{nxt!s}")
            elif nxt == "u" and not re.fullmatch(r"[0-9a-fA-F]{4}", text[i + 2:i + 6]):
                found.append("\\u not followed by four hex digits")
            i += 2
            continue
        if ch == '"':
            quoted = not quoted
        elif ch == "'" and not quoted:
            found.append("unescaped apostrophe (write \\')")
        i += 1
    return found


class StringResources(unittest.TestCase):
    def test_finds_resources(self):
        # A glob that silently matches nothing would pass forever.
        files = resource_files()
        self.assertTrue(any(p.name == "strings.xml" for p in files), files[:5])

    def test_every_string_compiles(self):
        bad = []
        for path in resource_files():
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError as err:
                bad.append(f"{path.relative_to(ROOT)}: not well-formed XML ({err})")
                continue
            for el in root.iter():
                if el.tag not in ("string", "item"):
                    continue
                if el.tag == "item" and root.tag == "resources" and el.get("type") not in (None, "string"):
                    continue
                text = "".join(el.itertext())
                for problem in problems_in(text):
                    bad.append(f"{path.relative_to(ROOT)}: {el.get('name') or el.get('quantity') or '?'}: {problem}")
        self.assertEqual(bad, [], "\n" + "\n".join(bad))

    def test_the_rules_themselves(self):
        self.assertEqual(problems_in(r"didn\'t"), [])
        self.assertEqual(problems_in(r"didn\\'t"), ["unescaped apostrophe (write \\')"])
        self.assertEqual(problems_in("didn't"), ["unescaped apostrophe (write \\')"])
        self.assertEqual(problems_in('"didn\'t"'), [], "an apostrophe inside double quotes is fine")
        self.assertEqual(problems_in(r"café"), [])
        self.assertEqual(problems_in(r"\u12"), ["\\u not followed by four hex digits"])
        self.assertEqual(problems_in(r"50\% off"), ["unknown escape \\%"])
        self.assertEqual(problems_in(r"line\nbreak \@home \?attr"), [])


if __name__ == "__main__":
    unittest.main()
