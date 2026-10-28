#!/usr/bin/env python3
"""A job that installs nothing must only run scripts that need nothing.

Why this exists
---------------
`suite-ci.yml` opens with `contracts`, the stdlib-only gate: it runs on a bare
`actions/setup-python` and never touches `tools/requirements.txt`. That is a
deliberate design - it answers "is the repository self-consistent?" before any
dependency can be blamed - and it is why the job is the first thing a red build
shows. It also means every `python tools/*.py` line added to it is a promise
that the script imports nothing third-party.

The Core Views wallpaper example page broke that promise. Its generator needs
Pillow and numpy, the check step was appended to `contracts` next to the
`--check` gates that are stdlib, and CI failed on `No module named 'PIL'` -
which reads, in the job summary, like "the page is out of date" and sends the
next person to regenerate a page that was never the problem. The steps after it
in the job never ran, including one that would have failed the same way.

Reading the diff does not catch this: `python tools/build_coreviews_wallpapers_html.py
--check` looks exactly like `python tools/build_coreviews_kt.py --check`, and
whether it imports PIL depends on a transitive import two files away. So this
test reads the workflows, works out which jobs install anything, and walks the
import graph of every script those jobs run.

Run: `python3 tests/test_ci_job_deps.py`
"""
from __future__ import annotations

import ast
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"

# Distributions the repo installs in the render jobs. A script importing one of
# these cannot run in a job that skipped `pip install`. Imports are matched by
# root module name, so `from PIL import Image` and `import numpy as np` both
# land here.
THIRD_PARTY = {
    "PIL": "Pillow",
    "numpy": "numpy",
    "qrcode": "qrcode",
    "cairosvg": "cairosvg",
    "resvg_py": "resvg-py",
    "fontTools": "fonttools",
    "imageio_ffmpeg": "imageio-ffmpeg",
    "pytest": "pytest",
}


def step_blocks(workflow: Path) -> dict[str, list[str]]:
    """{job id: [step text, ...]} from a workflow file, without a YAML dep.

    Hand-rolled on purpose: this test runs in the job it is policing, which has
    no YAML parser installed.
    """
    out: dict[str, list[str]] = {}
    job: str | None = None
    block: list[str] | None = None

    def flush() -> None:
        if job is not None and block:
            out[job].append("\n".join(block))

    for raw in workflow.read_text(encoding="utf-8").splitlines():
        job_line = re.fullmatch(r"  ([A-Za-z0-9_-]+):\s*", raw)
        if job_line:
            flush()
            block = None
            job = job_line.group(1)
            out.setdefault(job, [])
            continue
        if raw.startswith("      - ") or raw == "      -":
            flush()
            block = [raw]
            continue
        if block is not None and (raw.startswith("        ") or not raw.strip()):
            block.append(raw)
            continue
        flush()
        block = None
    flush()
    return out


def commands_in(step: str) -> list[str]:
    """Every shell command one step runs, inline `run:` or `run: |` block."""
    lines = step.splitlines()
    found: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        m = re.fullmatch(r"\s*(?:- )?run:\s*(.*)", line)
        i += 1
        if not m:
            continue
        value = m.group(1).strip()
        if value in ("|", "|-", ">", ">-"):
            block: list[str] = []
            while i < len(lines) and (not lines[i].strip()
                                      or lines[i].startswith("          ")):
                block.append(lines[i].strip())
                i += 1
            found.append(" ; ".join(b for b in block if b))
        elif value:
            found.append(value)
    return found


def jobs(workflow: Path) -> dict[str, list[str]]:
    """{job id: [shell command, ...]}."""
    return {
        job: [c for step in steps for c in commands_in(step)]
        for job, steps in step_blocks(workflow).items()
    }


def installs_anything(commands: list[str]) -> bool:
    return any("pip install" in c or "requirements.txt" in c for c in commands)


def scripts_in(command: str) -> list[Path]:
    """Every repo script a shell command runs."""
    found = []
    for token in command.split():
        token = token.strip("\"'")
        if not token.endswith(".py"):
            continue
        path = ROOT / token
        if path.is_file():
            found.append(path)
    return found


def imports_of(path: Path) -> set[str]:
    """Root module names a file imports *at import time*.

    Only module-level statements count, and a `try: import x / except
    ImportError` counts as guarded: `render_views.py` imports imageio_ffmpeg
    inside a function and falls back to `shutil.which("ffmpeg")`, which is
    exactly why its step can run in a job with no dependencies installed.
    """
    try:
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    except SyntaxError:                       # pragma: no cover - a broken file
        return set()

    roots: set[str] = set()

    def walk(statements):
        for node in statements:
            if isinstance(node, ast.Import):
                roots.update(a.name.split(".")[0] for a in node.names)
            elif isinstance(node, ast.ImportFrom):
                if node.level:                # relative import: local by
                    continue                      # definition
                if node.module:
                    roots.add(node.module.split(".")[0])
            elif isinstance(node, ast.Try):
                guarded = any(
                    getattr(h.type, "id", "") in
                    ("ImportError", "ModuleNotFoundError", "Exception")
                    for h in node.handlers
                )
                if not guarded:               # a bare try: import is a demand
                    walk(node.body)
                walk(node.orelse)
                walk(node.finalbody)
            elif isinstance(node, ast.If):
                walk(node.body)               # module-level conditionals run
                walk(node.orelse)
            elif isinstance(node, ast.With):
                walk(node.body)

    walk(tree.body)
    return roots


def local_module(name: str, near: Path) -> Path | None:
    """The sibling file a bare import resolves to, if it is in the repo."""
    for directory in (near.parent, ROOT / "tools", ROOT):
        candidate = directory / f"{name}.py"
        if candidate.is_file():
            return candidate
        package = directory / name / "__init__.py"
        if package.is_file():
            return package
    return None


def needs(entry: Path, seen: set[Path] | None = None) -> set[str]:
    """{distribution} the script cannot run without, through local imports."""
    seen = seen if seen is not None else set()
    if entry in seen:
        return set()
    seen.add(entry)
    out: set[str] = set()
    stdlib = getattr(sys, "stdlib_module_names", frozenset())
    for name in imports_of(entry):
        if name in THIRD_PARTY:
            out.add(THIRD_PARTY[name])
            continue
        if name in stdlib or name in ("__future__",):
            continue
        near = local_module(name, entry)
        if near is not None:
            out |= needs(near, seen)
    return out


class StdlibOnlyJobsTests(unittest.TestCase):
    def test_jobs_that_install_nothing_run_scripts_that_need_nothing(self):
        problems: list[str] = []
        checked = 0
        for workflow in sorted(WORKFLOWS.glob("*.yml")):
            for job, commands in jobs(workflow).items():
                if installs_anything(commands):
                    continue
                for command in commands:
                    for script in scripts_in(command):
                        checked += 1
                        missing = needs(script)
                        if missing:
                            problems.append(
                                f"{workflow.name}:{job} runs "
                                f"{script.relative_to(ROOT)} but the job installs "
                                f"nothing and that script needs "
                                f"{', '.join(sorted(missing))} - move the step to "
                                "a job that pip-installs tools/requirements.txt")
        self.assertGreater(checked, 10, "the parser found almost no scripts")
        self.assertEqual([], problems, "\n".join(problems))

    def test_the_parser_reads_block_run_steps(self):
        """A `run: |` block holds several commands; the parser has to keep
        them all, or it would silently skip the script that needed the deps."""
        parsed = jobs(WORKFLOWS / "suite-ci.yml")
        web = parsed["web-and-content"]
        joined = " ".join(web)
        self.assertIn("tests/test_privacy_policy.py", joined)

    def test_the_core_views_page_check_runs_where_pillow_is(self):
        """The step that broke CI, pinned: whoever moves it again should have
        to justify it in a test, not in a red build."""
        parsed = jobs(WORKFLOWS / "suite-ci.yml")
        holders = [
            job for job, commands in parsed.items()
            if any("build_coreviews_wallpapers_html.py" in c for c in commands)
        ]
        self.assertEqual(["web-and-content"], holders)
        self.assertTrue(installs_anything(parsed["web-and-content"]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
