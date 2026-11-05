#!/usr/bin/env python3
"""Extract concise Gradle failure diagnostics without promoting Kotlin warnings.

The launcher CI reruns Gradle in plain-console mode after a failure, then uses
this helper to surface actual compiler/build errors, and the name and exception
of each failing unit test, as GitHub annotations.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

DIAGNOSTIC = re.compile(
    r"^(?:e: |FAILURE: |\* What went wrong:|Execution failed for task |"
    r"> Task .* FAILED|BUILD FAILED|.*\.kt:\d+(?::\d+)?: error: |"
    r".*: error: .+|.*: Error: .+)"
)
# A failing unit test, as Gradle's plain console prints it ("HomeScreenTest > rowEnds FAILED"),
# followed by an indented line naming the exception and where it was thrown.
TEST_FAILURE = re.compile(r"^[\w.$\[\]]+ > .+ FAILED$")
FALLBACK = re.compile(
    r"(?:\bFAILED\b|BUILD FAILED|: error: |Exception in thread|"
    r"(?:^|:\s*)Caused by: )",
    re.IGNORECASE,
)
GENERIC_FAILURE = "Gradle failed; inspect the full job log for details."
MAX_DIAGNOSTICS = 16


def _is_kotlin_warning(line: str) -> bool:
    """Kotlin's `w:` diagnostics are warnings, even if their prose says 'error'."""
    return line.startswith("w: ") or line.startswith("> w: ")


def select_diagnostics(lines: list[str]) -> list[str]:
    """Return a short error-only excerpt suitable for GitHub annotations."""
    selected: list[str] = []
    capture_cause = 0
    capture_exception = False

    for line in lines:
        stripped = line.strip()
        if _is_kotlin_warning(stripped):
            continue
        if TEST_FAILURE.search(stripped):
            if stripped not in selected:
                selected.append(stripped)
            capture_exception = True
            capture_cause = 0
            continue
        if capture_exception:
            if stripped and line[:1].isspace():
                if stripped not in selected:
                    selected.append(stripped)
                capture_exception = False
                continue
            if stripped:
                capture_exception = False
        if DIAGNOSTIC.search(stripped):
            if stripped and stripped not in selected:
                selected.append(stripped)
            capture_cause = 8 if (
                "What went wrong:" in stripped
                or stripped.startswith("Execution failed for task ")
            ) else 0
        elif capture_cause and stripped.startswith("> "):
            if stripped not in selected:
                selected.append(stripped)
            capture_cause -= 1
        elif capture_cause and not stripped:
            capture_cause -= 1

    if not selected:
        selected = [
            stripped
            for line in lines
            if (stripped := line.strip())
            and not _is_kotlin_warning(stripped)
            and FALLBACK.search(stripped)
        ][-12:]
    if not selected:
        selected = [GENERIC_FAILURE]
    return selected[:MAX_DIAGNOSTICS]


def github_escape(value: str) -> str:
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv
    path = Path(args[0] if args else "build_out.txt")
    lines = path.read_text(errors="replace").splitlines()
    for line in select_diagnostics(lines):
        print(f"::error title=Gradle diagnostic::{github_escape(line)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
