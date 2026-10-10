#!/usr/bin/env python3
"""Auto-reply to icon requests that are missing or malformed.

Why this exists
---------------
The two icon issue forms (`.github/ISSUE_TEMPLATE/1.new_icon_request.yml` and
`2.icon_not_applying.yml`) ask for a component name, a store link, a device and
confirmation ticks. A form only checks that a box is filled in, not that it is
right, so requests land as "com.pixee.tv" with no activity, a link that is not a
URL, or unticked confirm boxes. Each one then waits for a human to ask for the
missing piece.

This script reads the issue body the way GitHub renders a form (`### Heading`
then the answer, `_No response_` for a blank field) and returns the list of
problems. The workflow `.github/workflows/issue-triage.yml` turns that list into
one comment (marker-tagged, edited in place) and a `needs-info` label. It never
closes an issue and never edits one.

Run locally (no network, no credentials):

    python tools/issue_triage.py --body-file body.md --template new
    python tools/issue_triage.py --event "$GITHUB_EVENT_PATH" --dry-run

The field headings below must match the form files exactly; the test in
`tests/test_issue_triage.py` reads the forms and fails on drift.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass

MARKER = "<!-- icon-triage -->"
LABEL = "needs-info"
NO_RESPONSE = "_No response_"
COMPONENT_RE = re.compile(r"^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")
URL_RE = re.compile(r"^https://[^\s/]+\S*$")
MAX_APP_NAME = 80


@dataclass(frozen=True)
class Field:
    heading: str
    required: bool = True
    options: tuple[str, ...] = ()
    checkboxes: int = 0  # number of ticks required (0 = not a checkbox field)
    kind: str = "text"   # text | component | url | choice | ticks


NEW_REQUEST = (
    Field("App name", kind="text"),
    Field("Component name", required=False, kind="component"),
    Field("Store or download link", kind="url"),
    Field("Device type", kind="choice", options=(
        "Android TV / Google TV", "Fire TV", "Nvidia Shield", "TV box (generic)", "Other")),
    Field("Anything else", required=False),
    Field("Confirm", kind="ticks", checkboxes=1),
)

NOT_APPLYING = (
    Field("App name", kind="text"),
    Field("The component name on YOUR device", kind="component"),
    Field("Device and OS"),
    Field("Launcher", kind="choice", options=(
        "Projectivy", "Nova", "Lawnchair", "FLauncher", "Other")),
    Field("Confirm", kind="ticks", checkboxes=2),
)

TEMPLATES = {"new": NEW_REQUEST, "mapping": NOT_APPLYING}
HINT = ("adb shell cmd package resolve-activity --brief <package> | tail -1")


def parse_sections(body: str) -> dict[str, str]:
    """Split a rendered issue form into {heading: answer}."""
    sections: dict[str, str] = {}
    current = None
    buf: list[str] = []
    for line in body.replace("\r\n", "\n").split("\n"):
        m = re.match(r"^###\s+(.+?)\s*$", line)
        if m:
            if current is not None:
                sections[current] = "\n".join(buf).strip()
            current, buf = m.group(1), []
        elif current is not None:
            buf.append(line)
    if current is not None:
        sections[current] = "\n".join(buf).strip()
    return sections


def _blank(value: str | None) -> bool:
    return value is None or value.strip() in ("", NO_RESPONSE)


def check(body: str, template: str) -> list[str]:
    """Return human-readable problems; an empty list means the request is fine."""
    spec = TEMPLATES[template]
    sections = parse_sections(body)
    problems: list[str] = []
    for f in spec:
        value = sections.get(f.heading)
        if f.kind == "ticks":
            ticks = len(re.findall(r"^\s*-\s*\[[xX]\]", value or "", re.M))
            if ticks < f.checkboxes:
                problems.append(
                    f"**Confirm**: tick every box under Confirm ({ticks} of {f.checkboxes} ticked).")
            continue
        if _blank(value):
            if f.required:
                problems.append(f"**{f.heading}** is empty. This field is required.")
            continue
        value = value.strip()
        if f.kind == "text":
            if "\n" in value:
                problems.append(f"**{f.heading}** should be one line.")
            elif len(value) > MAX_APP_NAME:
                problems.append(f"**{f.heading}** is longer than {MAX_APP_NAME} characters.")
        elif f.kind == "component":
            if not COMPONENT_RE.match(value):
                if "/" not in value:
                    problems.append(
                        f"**{f.heading}** `{value}` is a package name only. We need the "
                        f"package **and** the activity, in the form `package/activity`. "
                        f"On the TV run `{HINT}`, and paste the line it prints "
                        f"(it reads `package/activity`).")
                else:
                    problems.append(
                        f"**{f.heading}** `{value}` does not look like `package/activity`. "
                        f"Example: `com.example.tv/com.example.tv.MainActivity`.")
        elif f.kind == "url":
            if not URL_RE.match(value):
                problems.append(
                    f"**{f.heading}** must be a full `https://` link to the store page or APK.")
        elif f.kind == "choice":
            allowed = {o.lower(): o for o in f.options}
            if value.lower() not in allowed:
                problems.append(
                    f"**{f.heading}** must be one of: {', '.join(f.options)}.")
    return problems


SCANNER_TIP = (
    "**The quickest fix is the pack's own scanner.** On the TV, open **Missing icons** "
    "(the row on the home screen, or in Settings), wait for the scan, and press this app. "
    "The pack fills in the exact component and this device's details. Press **Request** "
    "and it files the request for you. Or photograph the screen and attach it to the issue. "
    "ADB steps are in `docs/ADB_SCANNING.md` if you can't use the TV."
)


def comment_for(problems: list[str], template: str) -> str:
    lines = [MARKER, "Thanks for the request. Before we can act on it, please fix:", ""]
    lines += [f"- {p}" for p in problems]
    lines += [
        "",
        SCANNER_TIP,
        "",
        "Edit the issue description to correct these. This comment updates itself, and the "
        "`needs-info` label comes off once the request is complete. Nothing else about the "
        "request has changed.",
    ]
    return "\n".join(lines)


def template_for(labels: list[str], title: str) -> str | None:
    if "icon request" in labels or title.startswith("[Icon]"):
        return "new"
    if "mapping" in labels or title.startswith("[Not applying]"):
        return "mapping"
    return None


def _gh(args: list[str], dry_run: bool) -> str:
    if dry_run:
        print("DRY-RUN gh " + " ".join(args))
        return ""
    return subprocess.run(["gh", *args], check=True, capture_output=True, text=True).stdout


def act(event: dict, dry_run: bool) -> int:
    issue = event.get("issue") or {}
    if issue.get("state") == "closed" or "pull_request" in issue:
        return 0
    labels = [lab.get("name", "") for lab in issue.get("labels", [])]
    template = template_for(labels, issue.get("title", ""))
    if template is None:
        return 0
    number = str(issue["number"])
    repo = (event.get("repository") or {}).get("full_name", "")
    problems = check(issue.get("body") or "", template)
    if not problems:
        if LABEL in labels:
            _gh(["issue", "edit", number, "--remove-label", LABEL, "-R", repo], dry_run)
        print(f"#{number}: complete")
        return 0

    _gh(["label", "create", LABEL, "--force", "--color", "FBCA04",
         "--description", "Waiting on details from the reporter", "-R", repo], dry_run)
    _gh(["issue", "edit", number, "--add-label", LABEL, "-R", repo], dry_run)
    body = comment_for(problems, template)
    existing = ""
    if not dry_run:
        raw = _gh(["api", f"repos/{repo}/issues/{number}/comments", "--paginate"], False)
        for c in json.loads(raw or "[]"):
            if MARKER in (c.get("body") or "") and c.get("user", {}).get("type") == "Bot":
                existing = str(c["id"])
                break
    if existing:
        _gh(["api", "-X", "PATCH", f"repos/{repo}/issues/comments/{existing}",
             "-f", f"body={body}"], dry_run)
    else:
        _gh(["issue", "comment", number, "--body", body, "-R", repo], dry_run)
    print(f"#{number}: {len(problems)} problem(s) reported")
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    ap.add_argument("--event", help="GitHub event JSON (GITHUB_EVENT_PATH)")
    ap.add_argument("--body-file", help="check a single body file instead")
    ap.add_argument("--template", choices=sorted(TEMPLATES), default="new")
    ap.add_argument("--dry-run", action="store_true",
                    help="print the gh calls instead of making them")
    args = ap.parse_args(argv)

    if args.body_file:
        with open(args.body_file, encoding="utf-8") as fh:
            problems = check(fh.read(), args.template)
        print("\n".join(problems) if problems else "complete")
        return 1 if problems else 0
    path = args.event or os.environ.get("GITHUB_EVENT_PATH", "")
    if not path:
        ap.error("need --event or GITHUB_EVENT_PATH")
    with open(path, encoding="utf-8") as fh:
        event = json.load(fh)
    return act(event, args.dry_run)


if __name__ == "__main__":
    sys.exit(main())
