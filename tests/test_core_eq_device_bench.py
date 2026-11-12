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
    case "${STUB_START:-cold}" in
      cold) printf 'Status: ok\nLaunchState: COLD\nTotalTime: 812\nWaitTime: 815\nComplete\n';;
      warm) printf 'Status: ok\nLaunchState: WARM\nTotalTime: 240\nWaitTime: 243\nComplete\n';;
      none) echo "Status: ok";;
      error) printf 'Starting: Intent { cmp=tv.corebuilds.eq/.MainActivity }\nError type 3\nError: Activity class {tv.corebuilds.eq/tv.corebuilds.eq.MainActivity} does not exist.\n'; exit 1;;
      timeout) printf 'Status: timeout\nComplete\n';;
      front) printf 'Warning: Activity not started, its current task has been brought to the front\nStatus: ok\nLaunchState: HOT\nComplete\n';;
    esac;;
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
        for heading in ("## Starts after force-stop", "## Starts after the Home key", "## Home frames", "## Profiles frames",
                        "## Core EQ counters and timings", "## Notes"):
            self.assertIn(heading, report)
        self.assertIn("tv: Maker Model X", report)
        self.assertIn("app: versionName=1.14.0 versionCode=30", report)
        self.assertEqual(2, len(re.findall(r"^after force-stop \d+: LaunchState: COLD TotalTime: 812 WaitTime: 815$", report, re.M)))
        self.assertEqual(2, len(re.findall(r"^after Home key \d+: LaunchState: COLD", report, re.M)))
        self.assertEqual(2, report.count("reported: COLD 2, WARM 0, HOT 0, no LaunchState 0, failed or not started 0"))
        self.assertIn("clears them, so they cover only what ran after the last start after force-stop", report)
        self.assertIn("Total frames rendered: 120", report)
        self.assertIn("Core EQ 1.14.0 (30), since the process started", report)
        self.assertIn("skipped (SKIP_PROFILES=1)", report)
        self.assertIn("not a performance claim", report)
        self.assertNotIn(SERIAL, report)
        self.assertIn("shell am force-stop tv.corebuilds.eq", log)
        # Reads and launches only: nothing installed, no setting written.
        self.assertNotRegex(log, r"\b(install|uninstall|settings put|pm clear)\b")

    def test_a_missing_figure_does_not_abort_the_run(self):
        done, report, _ = self.run_bench(STUB_START="none")
        self.assertEqual(0, done.returncode, done.stderr)
        self.assertIn("after force-stop 1: launched, no start figures reported", report)
        self.assertIn("reported: COLD 0, WARM 0, HOT 0, no LaunchState 2, failed or not started 0", report)

    def test_a_failed_launch_is_not_reported_as_missing_figures(self):
        done, report, _ = self.run_bench(STUB_START="error")
        self.assertEqual(0, done.returncode, done.stderr)
        self.assertIn("after force-stop 1: launch failed: Error type 3", report)
        self.assertNotIn("no start figures", report)
        self.assertIn("reported: COLD 0, WARM 0, HOT 0, no LaunchState 0, failed or not started 2", report)
        # The Home frames step launches too; its failure is reported, not fatal.
        self.assertIn("(Home did not open, so no frames: launch failed: Error type 3)", report)
        self.assertIn("## Notes", report)

    def test_an_unfinished_or_refused_start_is_not_a_sample(self):
        _, report, _ = self.run_bench(STUB_START="timeout")
        self.assertIn("after force-stop 1: launch did not finish: Status: timeout", report)
        _, report, _ = self.run_bench(STUB_START="front")
        self.assertIn("after Home key 1: not started: Warning: Activity not started", report)
        self.assertNotIn("LaunchState: HOT", report)

    def test_android_launch_state_is_kept_whatever_the_section_meant(self):
        # A start after force-stop that Android reports as WARM did not start a process:
        # the line says WARM, and the tally counts it as WARM, not cold.
        _, report, _ = self.run_bench(STUB_START="warm")
        self.assertIn("after force-stop 1: LaunchState: WARM TotalTime: 240", report)
        self.assertIn("reported: COLD 0, WARM 2, HOT 0", report)

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
