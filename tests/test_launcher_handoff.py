#!/usr/bin/env python3
"""
Contracts for the launcher setup walk (the 1.9.9 AT4K correction).

Ground truth is the r/AT4K thread of 2026-10-01: a user followed the
pack's apply-fallback toast into AT4K settings and found no icon-pack
screen ("there is no icon pack under settings!?"); AT4K's own developer
routes the manual pick to Settings > Themes > Icon packs. The toast
named a screen that does not exist, and a toast is the wrong shape for
a walk anyway - it expires on the way to following it.

The correction models AT4K exactly like Monet (tests/test_monet_handoff.py):
no incoming apply action, so the apply flow hands over a setup screen
that states the walk through the launcher's own settings, ends it on one
pack, and opens the launcher. And any launcher whose settings tree we
know gets that screen the moment its apply probe misses, instead of the
one-line toast.

These tests pin the wiring. Plain unittest, no SDK.
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def launcher_blocks(src: str) -> dict[str, str]:
    """Each `val NAME = Launcher(` block, keyed by NAME, up to the next
    member declaration at the same indent."""
    blocks = {}
    for m in re.finditer(r"^    val (\w+) = Launcher\(", src, re.M):
        start = m.start()
        nxt = re.search(r"\n    (val |private val |private fun |fun )", src[m.end():])
        end = m.end() + nxt.start() if nxt else len(src)
        blocks[m.group(1)] = src[start:end]
    return blocks


class At4kHandoffTests(unittest.TestCase):
    """AT4K: the walk, never a probe dressed up as an apply."""

    def setUp(self):
        self.src = read("app/src/main/java/tv/corebuilds/iconpack/ApplyIconPack.kt")
        self.at4k = launcher_blocks(self.src)["AT4K"]

    def test_at4k_has_no_inbound_apply(self):
        # The developer routes manual cases to his settings tree
        # (r/AT4K, 2026-10-01); no incoming apply action is documented.
        self.assertIn("inboundApply = false", self.at4k)
        self.assertIn("com.overdevs.at4k", self.at4k)

    def test_at4k_walk_is_the_devs_path(self):
        # Settings > Themes > Icon packs, outermost first, without the
        # pack - the handoff appends the pick so one pack is named.
        self.assertIn(
            'setupStops = listOf("AT4K Settings", "Themes", "Icon packs")',
            self.at4k)

    def test_at4k_manual_path_names_the_real_screen(self):
        self.assertRegex(
            self.at4k,
            r'manualPath = "AT4K Settings → Themes → Icon packs → ')
        # The 1.9.x dead end: a settings screen AT4K does not have.
        self.assertNotIn("Settings → Icon pack → Core Builds", self.at4k)


class StopsAndManualPathAgreeTests(unittest.TestCase):
    """The walk screen and the launcher table tell the same walk.

    setupStops is what the setup screen numbers; manualPath is the same
    tree in one line. If they ever disagree, one of the two surfaces
    sends users to a screen the other never named."""

    def setUp(self):
        self.blocks = launcher_blocks(
            read("app/src/main/java/tv/corebuilds/iconpack/ApplyIconPack.kt"))

    def test_every_stop_appears_in_the_manual_path(self):
        for name, block in self.blocks.items():
            stops = re.search(r'setupStops = listOf\(([^)]*)\)', block)
            if not stops:
                continue
            path = re.search(r'manualPath = "([^"]+)"', block)
            self.assertIsNotNone(path, f"{name} has stops but no manualPath")
            for stop in re.findall(r'"([^"]+)"', stops.group(1)):
                self.assertIn(stop, path.group(1),
                              f"{name}: stop '{stop}' missing from manualPath")

    def test_known_trees_cover_the_tv_launchers(self):
        with_stops = {n for n, b in self.blocks.items()
                      if "setupStops = listOf(" in b}
        self.assertTrue(
            {"PROJECTIVY", "MONET", "AT4K", "LTV", "CHILLHUB", "NOVA",
             "LAWNCHAIR", "APEX", "ADW"} <= with_stops,
            f"launchers missing a setup walk: "
            f"{sorted({'PROJECTIVY', 'MONET', 'AT4K', 'LTV', 'CHILLHUB', 'NOVA', 'LAWNCHAIR', 'APEX', 'ADW'} - with_stops)}")


class ProbeMissRoutingTests(unittest.TestCase):
    """A missed probe becomes the walk, not a toast, when a tree is known."""

    def setUp(self):
        self.src = read("app/src/main/java/tv/corebuilds/iconpack/ApplyIconPack.kt")

    def test_apply_routes_every_miss_through_probeMissed(self):
        apply_body = self.src[self.src.index("fun apply("):]
        apply_body = apply_body[:apply_body.index("fun openLauncher")]
        self.assertEqual(3, apply_body.count("probeMissed(context, launcher, manual)"),
                         "null intent, unresolved probe and thrown startActivity "
                         "are the three misses; each must route through probeMissed")

    def test_probeMissed_prefers_the_walk(self):
        self.assertRegex(
            self.src,
            r"private fun probeMissed\([^)]*\): Result =\s*\n\s*"
            r"if \(launcher\.setupStops\.isNotEmpty\(\)\) handoff\(context, launcher\)")


class SetupScreenTests(unittest.TestCase):
    """The screen stays open for any launcher that carries stops."""

    def setUp(self):
        self.src = read("app/src/main/java/tv/corebuilds/iconpack/LauncherSetupActivity.kt")

    def test_guard_is_the_stops_not_the_apply_flag(self):
        # An inbound-apply launcher whose probe missed (Projectivy on an
        # odd box) must get the walk too; the old guard finished() for it.
        self.assertIn("launcher.setupStops.isEmpty()", self.src)
        self.assertNotIn("launcher.inboundApply) {", self.src)

    def test_two_honest_reasons(self):
        self.assertIn("R.string.setup_why_probe_fmt", self.src)
        self.assertIn("R.string.setup_why_fmt", self.src)
        strings = read("app/src/main/res/values/strings.xml")
        self.assertRegex(strings, r'<string name="setup_why_probe_fmt"')


if __name__ == "__main__":
    unittest.main()
