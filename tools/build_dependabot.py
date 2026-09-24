#!/usr/bin/env python3
"""Generate `.github/dependabot.yml` from the suite and its build envelope.

The ignore rules in this file are not decoration — they are the only thing
standing between Dependabot and a PR that cannot compile. Dependabot reads
version numbers; it cannot see that five of the seven modules compile against
API 34, that the icon pack still ships `minSdk 21` for Android 5.x Fire TV
hardware, or that `doctor/` wires the Compose compiler through a DSL Kotlin 2.0
deleted. So it proposes AndroidX releases whose AAR metadata demands a newer
platform, and the PR fails in CI.

They were hand-written once (6434eabd), silently deleted when the file was
restructured into per-directory groups (9f5d266d), and the four PRs that
followed — #122 #125 #126 #132 — all failed for the same three libraries.
Nothing in CI noticed the rules had gone.

So now the file is generated. Every Gradle root gets an entry whether or not
anyone remembered to add one, and every capped coordinate gets an ignore rule
in exactly the directories that declare it, derived from
`tools/gradle_envelope.json`.

    python tools/build_dependabot.py           # write the file
    python tools/build_dependabot.py --check   # fail on drift (CI)
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import json  # noqa: E402

from check_gradle_envelope import (  # noqa: E402
    DEPENDABOT,
    ENVELOPE,
    ROOT,
    discover_roots,
)

# Group names are part of Dependabot's identity for an open PR: renaming one
# makes it close the existing PR and open a fresh one, losing its review
# history. These five predate the generator and keep their names.
GROUP_NAMES = {
    "": "android-root",
    "ticker/android": "core-line-android",
    "shift": "core-shift",
    "doctor": "core-doctor",
    "motion-plugin": "core-motion",
}

# Rules that apply to every Gradle root regardless of what it declares. They are
# about the toolchain rather than a library, so there is no coordinate in a
# build file to key them off.
UNIVERSAL_IGNORES = [
    (
        "gradle-wrapper",
        'versions: [">=9.6"]',
        "Carried over from #121 — read the note in the header before trusting it.",
    ),
    (
        "org.gradle",
        'versions: [">=9.6"]',
        "The same wrapper under the other name Dependabot sometimes reports.",
    ),
]

HEADER = """\
# GENERATED FILE — do not edit by hand.
#
#     python tools/build_dependabot.py           # regenerate
#     python tools/build_dependabot.py --check   # what CI runs
#
# The `ignore:` blocks below are derived from tools/gradle_envelope.json and
# from which coordinates each Gradle root actually declares. They are the only
# thing stopping Dependabot proposing versions that cannot compile against this
# suite's compileSdk / minSdk / Kotlin — Dependabot reads version numbers and
# cannot see any of those. So they must not be able to drift or be quietly
# deleted, which is exactly what happened in 9f5d266d and what produced the four
# red PRs #122 #125 #126 #132.
#
# Every capped coordinate blocks patch, minor AND major. That looks stricter
# than it needs to be, and it is deliberate: every ceiling in
# gradle_envelope.json is exactly the version the suite declares today, and
# check_gradle_envelope.py rejects any declared version above its ceiling no
# matter which digit moved. So a patch above a cap is a PR the gate is
# guaranteed to reject - Kotlin 1.9.24 -> 1.9.25 is precisely that case, and it
# is why the six red PRs kept coming back. Ignoring only minor+major left the
# patch lane wide open. Nothing is lost by closing it: there is no patch the
# suite could accept today without lifting the ceiling on purpose, which is the
# documented workflow. Each cap records its reason and its evidence in
# gradle_envelope.json.
#
# To take a capped dependency forward: change the envelope first (migration
# steps are at the end of gradle_envelope.json), watch CI go green, then lift
# the ceiling and regenerate this file.
#
# Note on the `gradle-wrapper >= 9.6` rule and the `com.android.application`
# ceiling below. They are two halves of ONE constraint, and the 2026-09-19
# Dependabot batch (#144 #145 #146 #147 #148 #149) is what settled it. Gradle
# 9.6.0 removed org.gradle.api.problems.internal.InternalProblems
# (gradle/gradle#38073) and AGP 8.13.2 still binds it, so AGP 8.13.2 on the
# 9.7.0 wrapper that five of six roots run dies at plugin apply - before a
# single line compiles. #146 moved its wrapper to 9.5.1 with the same AGP
# 8.13.2 and built green, so AGP 8.13.2 is not broken by itself, only in that
# combination. AGP 8.5.2 runs on 9.7.0, which is why main is green today: that
# is one AGP version tolerating the removal, not AGP 8.x as a whole. The AGP
# ceiling is therefore 8.5.2, not 9, until the wrappers move or AGP 9 lands.
version: 2
updates:
"""

FOOTER = """\
  - package-ecosystem: "npm"
    directory: "/ticker"
    schedule:
      interval: "weekly"
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
"""


def directory_of(root_rel: str) -> str:
    return "/" + root_rel if root_rel else "/"


def group_of(root_rel: str) -> str:
    if root_rel in GROUP_NAMES:
        return GROUP_NAMES[root_rel]
    return root_rel.rsplit("/", 1)[-1].replace("/", "-")


def ceilings_for(root, table: dict) -> list[dict]:
    """Capped coordinates this root declares, in table order."""
    declared = set(root.plugin_deps)
    for module in root.modules:
        declared |= set(module.deps)
    return [c for c in table["ceilings"] if c["coordinate"] in declared]


def render_entry(root, table: dict) -> str:
    directory = directory_of(root.rel)
    where = "repo root" if not root.rel else root.rel + "/"
    lines = [
        f"  # {root.label} — {where}",
        '  - package-ecosystem: "gradle"',
        f'    directory: "{directory}"',
        "    schedule:",
        '      interval: "weekly"',
        "    ignore:",
    ]
    for name, spec, comment in UNIVERSAL_IGNORES:
        lines.append(f"      # {comment}")
        lines.append(f'      - dependency-name: "{name}"')
        lines.append(f"        {spec}")

    for ceiling in ceilings_for(root, table):
        coord = ceiling["coordinate"]
        modules = sorted({m.name for m in root.modules if coord in m.deps})
        declared = f" — declared by {', '.join(modules)}" if modules else ""
        lines.append(f"      # {coord} <= {ceiling['max']}{declared}")
        types = ", ".join(f'"{t}"' for t in ceiling["blockedUpdateTypes"])
        lines.append(f'      - dependency-name: "{coord}"')
        lines.append(f"        update-types: [{types}]")

    lines.append("    groups:")
    lines.append(f"      {group_of(root.rel)}:")
    lines.append('        patterns: ["*"]')
    return "\n".join(lines) + "\n"


def render() -> str:
    table = json.loads(ENVELOPE.read_text(encoding="utf-8"))
    roots = discover_roots()
    # Suite order, the way suite.json lists the apps, so the file reads the same
    # way the docs do rather than in whatever order rglob happened to walk.
    order = ["", "ticker/android", "shift", "motion-plugin", "doctor"]
    by_rel = {r.rel: r for r in roots}
    unknown = [rel for rel in by_rel if rel not in order]
    if unknown:
        # A new Gradle root still gets an entry, just after the known ones.
        order = order + sorted(unknown)
    missing = [rel for rel in order if rel not in by_rel]
    if missing:
        raise SystemExit(f"GROUP_NAMES lists roots that do not exist: {missing}")

    parts = [HEADER]
    for rel in order:
        parts.append(render_entry(by_rel[rel], table))
    parts.append(FOOTER)
    return "\n".join(parts)


def main() -> int:
    check = "--check" in sys.argv
    rendered = render()
    if check:
        current = DEPENDABOT.read_text(encoding="utf-8") if DEPENDABOT.is_file() else ""
        if current != rendered:
            print("::error::.github/dependabot.yml has drifted from its generator.")
            print("Run: python tools/build_dependabot.py")
            import difflib

            diff = difflib.unified_diff(
                current.splitlines(),
                rendered.splitlines(),
                fromfile="dependabot.yml (committed)",
                tofile="dependabot.yml (generated)",
                lineterm="",
            )
            for line in list(diff)[:80]:
                print(line)
            return 1
        roots = discover_roots()
        table = json.loads(ENVELOPE.read_text(encoding="utf-8"))
        capped = sum(len(ceilings_for(r, table)) for r in roots)
        print(f"dependabot.yml in sync: {len(roots)} gradle directories, {capped} capped coordinates")
        return 0
    DEPENDABOT.write_text(rendered, encoding="utf-8")
    print(f"Wrote {DEPENDABOT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
