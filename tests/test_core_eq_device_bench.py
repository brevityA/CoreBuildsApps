#!/usr/bin/env python3
"""
`tools/core_eq_device_bench.sh`, run against a stub `adb` (milestone C).

What this proves: the script parses, refuses without exactly one device,
survives `set -euo pipefail` when a figure is missing, writes the sections the
runbook (`docs/CORE_EQ_DEVICE_BENCHMARK.md`) reads, keeps the device serial
out of the report, and names components that exist (the launcher activity
and the service whose `dump()` it reads, matched on the dump's first line).

What it does not prove: anything about a TV. The stub returns canned text;
only a run on a real device produces a benchmark, and none has been made.

Run: `python3 tests/test_core_eq_device_bench.py`
"""
from __future__ import annotations

import os
import re
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools/core_eq_device_bench.sh"
MANIFEST = ROOT / "coreeq/app/src/main/AndroidManifest.xml"
SERVICE = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/apply/EqService.kt"
RUNBOOK = ROOT / "docs/CORE_EQ_DEVICE_BENCHMARK.md"

SERIAL = "SERIAL-0123456789"

# A stub adb: canned answers keyed on the command line, and a log of what was asked.
STUB = r"""#!/usr/bin/env bash
echo "$*" >> "$ADB_LOG"
case "$*" in
  devices) printf 'List of devices attached\n%s\tdevice\n\n' "$STUB_DEVICES";;
  "shell pm path tv.corebuilds.eq") echo "package:/data/app/base.apk";;
  "shell getprop ro.product.manufacturer") echo "Maker";;
  "shell getprop ro.product.model") echo "Model X";;
  "shell getprop ro.build.version.release") echo "14";;
  "shell getprop ro.build.version.sdk") echo "34";;
  "shell getprop ro.build.fingerprint") echo "maker/model/x:14/AB12/1:user/release-keys";;
  "shell dumpsys package tv.corebuilds.eq") printf '    versionCode=30 minSdk=26\n    versionName=1.14.0\n';;
  "shell am start -W -n tv.corebuilds.eq/.MainActivity")
    if [ -n "$STUB_NO_TIMES" ]; then echo "Status: ok"; else printf 'Status: ok\nLaunchState: COLD\nTotalTime: 812\nWaitTime: 815\nComplete\n'; fi;;
  "shell dumpsys gfxinfo tv.corebuilds.eq") printf 'Total frames rendered: 120\nJanky frames: 3 (2.50%%)\n50th percentile: 6ms\n90th percentile: 11ms\n';;
  "shell dumpsys activity service tv.corebuilds.eq/.apply.EqService")
    printf 'SERVICE tv.corebuilds.eq/.apply.EqService 1a2b pid=4242\n  Client:\n    Core EQ 1.14.0 (30), since the process started\n    Timings:\n      apply: count=1\n';;
  *) :;;
esac
"""


class DeviceBenchTest(unittest.TestCase):

    def run_bench(self, devices: str = SERIAL, **env: str) -> tuple[subprocess.CompletedProcess, str, str]:
        tmp = Path(tempfile.mkdtemp(prefix="bench-"))
        adb = tmp / "adb"
        adb.write_text(STUB)
        adb.chmod(adb.stat().st_mode | stat.S_IEXEC)
        # The script settles between starts with sleep; a TV needs that, the stub does not.
        fake_sleep = tmp / "sleep"
        fake_sleep.write_text("#!/bin/sh\nexit 0\n")
        fake_sleep.chmod(fake_sleep.stat().st_mode | stat.S_IEXEC)
        report = tmp / "report.txt"
        log = tmp / "adb.log"
        full_env = {
            **os.environ,
            "PATH": f"{tmp}{os.pathsep}{os.environ['PATH']}",
            "ADB_LOG": str(log),
            "STUB_DEVICES": devices,
            "RUNS": "2",
            "SKIP_PROFILES": "1",
            **env,
        }
        done = subprocess.run(["bash", str(SCRIPT), str(report)], env=full_env, capture_output=True, text=True, timeout=120)
        return done, report.read_text() if report.exists() else "", log.read_text() if log.exists() else ""

    def test_syntax(self):
        subprocess.run(["bash", "-n", str(SCRIPT)], check=True)

    def test_report_has_every_section_and_no_serial(self):
        done, report, log = self.run_bench()
        self.assertEqual(0, done.returncode, done.stderr)
        for heading in ("## Cold starts", "## Warm starts", "## Home frames", "## Profiles frames",
                        "## Core EQ counters and timings", "## Notes"):
            self.assertIn(heading, report)
        self.assertIn("tv: Maker Model X", report)
        self.assertIn("app: versionName=1.14.0 versionCode=30", report)
        self.assertEqual(2, len(re.findall(r"^cold \d+: LaunchState: COLD TotalTime: 812", report, re.M)))
        self.assertIn("Total frames rendered: 120", report)
        self.assertIn("Core EQ 1.14.0 (30), since the process started", report)
        self.assertIn("skipped (SKIP_PROFILES=1)", report)
        self.assertIn("not a performance claim", report)
        self.assertNotIn(SERIAL, report)
        self.assertIn("shell am force-stop tv.corebuilds.eq", log)
        # Reads and launches only: nothing installed, no setting written.
        self.assertNotRegex(log, r"\b(install|uninstall|settings put|pm clear)\b")

    def test_a_missing_figure_does_not_abort_the_run(self):
        done, report, _ = self.run_bench(STUB_NO_TIMES="1")
        self.assertEqual(0, done.returncode, done.stderr)
        self.assertIn("cold 1: (no start figures reported)", report)

    def test_refuses_without_exactly_one_device(self):
        done, report, _ = self.run_bench(devices="")
        self.assertNotEqual(0, done.returncode)
        self.assertIn("expected exactly one connected device", done.stderr)
        self.assertEqual("", report)

    def test_names_components_that_exist(self):
        manifest = MANIFEST.read_text()
        self.assertIn('android:name=".MainActivity"', manifest)
        self.assertIn('android:name=".apply.EqService"', manifest)
        # The script finds the dump by its first line; the service must print it.
        self.assertIn('writer.println("Core EQ ${tv.corebuilds.eq.BuildConfig.VERSION_NAME}', SERVICE.read_text())
        self.assertIn("sed -n '/Core EQ /,$p'", SCRIPT.read_text())

    def test_runbook_exists_and_records_no_run(self):
        text = RUNBOOK.read_text()
        self.assertIn("tools/core_eq_device_bench.sh", text)
        self.assertIn("NOT RUN", text)


if __name__ == "__main__":
    unittest.main()
