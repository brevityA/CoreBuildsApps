#!/usr/bin/env python3
"""The stack you render on must be the stack CI renders on.

Why this exists
---------------
`tools/build_app_ui_mockups.py --check` compares the frames committed in
`docs/` against a fresh render, byte for byte. That is only a meaningful
comparison if both sides used the same rasteriser: Pillow's text layout and PNG
encoding move between major versions, so a frame rendered on one version and
checked on another differs in bytes while depicting exactly the same screen.

That is what happened on the 1.9.0 release PR. The nine frames and
`docs/icon-fidelity-preview.png` were generated in a workspace carrying Pillow
12.3.0, against a pin of 10.4.0 in `tools/requirements.txt`. Every local check
passed - the frames matched the frames - and CI failed the comparison twice, in
two workflows, for a difference nobody could see. `tools/requirements.txt` says
"Pinned so PNG output stays reproducible across machines and CI"; nothing
checked that the machine doing the committing had installed them.

So this file reads the pins and compares them with what is actually installed,
using importlib.metadata rather than importing: `cairosvg` is pinned but cannot
be imported without libcairo, and its absence from the system is not a reason
to fail a check about versions.

Run: `python3 tests/test_render_stack.py`
"""
from __future__ import annotations

import re
import unittest
from importlib import metadata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIREMENTS = ROOT / "tools" / "requirements.txt"

# Distribution name -> the module whose behaviour the pin is really about.
# Only the rasterisers and the font/QR libraries matter: they are the ones
# whose output is committed and byte-compared.
PINNED = ("Pillow", "numpy", "fonttools", "qrcode", "resvg-py")


def pins() -> dict[str, str]:
    """{distribution: exact version} from tools/requirements.txt."""
    out: dict[str, str] = {}
    for line in REQUIREMENTS.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        m = re.fullmatch(r"([A-Za-z0-9_.\-]+)==([^\s;]+)", line)
        if m:
            out[m.group(1).lower()] = m.group(2)
    return out


def installed(name: str) -> str | None:
    try:
        return metadata.version(name)
    except metadata.PackageNotFoundError:
        return None


class RenderStack(unittest.TestCase):
    def test_requirements_are_pinned_exactly(self) -> None:
        """A range or an unpinned line is not a reproducibility claim."""
        found = pins()
        self.assertGreaterEqual(
            len(found), len(PINNED), f"requirements.txt pins look thin: {found}"
        )
        loose = [
            line.split("#", 1)[0].strip()
            for line in REQUIREMENTS.read_text(encoding="utf-8").splitlines()
            if line.split("#", 1)[0].strip()
            and not re.fullmatch(r"[A-Za-z0-9_.\-]+==[^\s;]+", line.split("#", 1)[0].strip())
        ]
        self.assertEqual(
            loose, [], f"requirements.txt must pin with ==: {loose}"
        )

    def test_installed_versions_match_the_pins(self) -> None:
        found = pins()
        mismatches, absent = [], []
        for name in PINNED:
            want = found.get(name.lower())
            self.assertIsNotNone(want, f"{name} is not pinned in requirements.txt")
            have = installed(name)
            if have is None:
                absent.append(name)
            elif have != want:
                mismatches.append(f"{name}: installed {have}, pinned {want}")
        self.assertEqual(
            mismatches,
            [],
            "committed rasters cannot be reproduced from this workspace, so "
            "every byte-compared gate (--check) will pass here and fail in CI. "
            f"Run: pip install -r tools/requirements.txt, then regenerate. {mismatches}",
        )
        # Not fatal - a machine without the renderer simply cannot generate -
        # but worth saying out loud, because "it passed here" then means nothing.
        if absent:
            print(f"note: not installed here, so nothing was rendered: {absent}")

    def test_the_byte_compared_gates_are_named(self) -> None:
        """If a new generator starts byte-comparing committed rasters, its
        dependencies belong in the pin list this file enforces."""
        self.assertTrue(REQUIREMENTS.is_file())
        text = REQUIREMENTS.read_text(encoding="utf-8")
        for name in PINNED:
            self.assertIn(
                name.lower(),
                text.lower(),
                f"{name} vanished from requirements.txt; the frames and the "
                "fidelity preview are rendered with it and compared by bytes",
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
