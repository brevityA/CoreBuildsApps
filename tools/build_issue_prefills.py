#!/usr/bin/env python3
"""Prefilled icon-request issue links, generated from `.github/ISSUE_TEMPLATE/`.

Two things rot the moment they are hand-written in the README:

* the `template=` slug — renaming or renumbering a form file silently 404s
  every "click to report" link in the repo;
* the list of fields a link can prefill — GitHub fills only `input` and
  `textarea` fields from the query string, so a link that advertises the
  device-type dropdown as prefillable is lying, and a typo in a field id is a
  box that looks untouched to the reporter.

So every link is built from the forms themselves and `--check` fails on drift,
the same gate as the suite stamp.

```bash
python tools/build_issue_prefills.py         # write the README block
python tools/build_issue_prefills.py --check # CI gate: fail on drift
python tools/build_issue_prefills.py --list  # parsed forms, prefillable fields
python tools/build_issue_prefills.py --json   # manifest, for other generators
python tools/build_issue_prefills.py --app Stremio --template icon_not_applying \
    --component com.stremio.one/com.stremio.tv.MainActivity   # a link for one app
python tools/build_issue_prefills.py --app "Sparkle TV" --field notes="Fire TV build"
```

Field ids come from the form, so a form that gains a field gains a prefill.
`--template` takes the file name or any unique part of it, and `--field id=value`
reaches any other free-text field; GitHub answers 414 past ~2k, which is what the
length guard below is for.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlencode

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE_DIR = ROOT / ".github" / "ISSUE_TEMPLATE"
README = ROOT / "README.md"

# Same pinned slug as tools/audit_contract.py — the links are absolute because
# they get pasted into Reddit and issue replies, not only read on github.com.
REPO = "brevityA/CoreBuildsApps"
ISSUES_NEW = f"https://github.com/{REPO}/issues/new"

START = "<!-- issue-prefills:start -->"
END = "<!-- issue-prefills:end -->"
SECTION_HEADING = "### Request an icon"


def anchor_for(heading: str) -> str:
    """GitHub's heading slug, so the README's `(#…)` link to this section is
    derived from the heading instead of being a second copy of it. Markdown's
    `###` never reaches the slug, but an emoji in the heading does — it drops
    out and leaves the leading dash the suite-stamp table links with."""
    slug = re.sub(r"[^a-z0-9 -]", "", heading.lstrip("#").strip().lower())
    return "#" + slug.replace(" ", "-")


SECTION_ANCHOR = anchor_for(SECTION_HEADING)

# GitHub prefills the free-text fields: "URL query parameters to fill custom text
# fields". `dropdown`, `checkboxes`, `upload` and `markdown` are not filled, which
# is a feature — no link can answer a required box.
PREFILLABLE_TYPES = {"input", "textarea"}
# Query keys the new-issue route already owns; a field id may not shadow them.
RESERVED_KEYS = {
    "template", "title", "body", "labels", "assignees", "projects", "milestone",
}
# GitHub's form schema: an `id` may only use alpha-numeric characters, `-` and
# `_`, must be unique inside the form, and is the query key a prefill uses. So
# the two grammars are one grammar here — anything else is a link that fills
# nothing, silently.
FIELD_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]*$")
# Comfortable margin under the server limit that answers 414 URI Too Long.
MAX_URL = 1500
# Generator flags mapped to the field id both icon forms use.
ALIASES = {"app": "app_name", "component": "component"}
SAMPLE = {"app_name": "Stremio",
          "component": "com.stremio.one/com.stremio.tv.MainActivity"}


def fail(message: str) -> None:
    print(f"::error::{message}")
    raise SystemExit(1)


def unquote(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    return value


def flow_list(value: str) -> list[str]:
    value = value.strip()
    if value.startswith("[") and value.endswith("]"):
        value = value[1:-1]
    return [unquote(part) for part in value.split(",") if part.strip()]


def parse_template(path: Path) -> dict:
    """Read the handful of keys a prefill link cares about from one form file."""
    doc = {
        "file": path.name,
        "slug": path.stem,
        "name": "",
        "description": "",
        "title": "",
        "labels": [],
        "fields": [],
    }
    key: str | None = None      # current top-level key
    context: str | None = None  # attributes | validations, inside one body item
    field: dict | None = None

    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip())
        line = raw.strip()

        if indent == 0:
            name, _, value = line.partition(":")
            key = name.strip()
            value = value.strip()
            if key == "body":
                doc["fields"] = []
            elif key == "labels":
                doc["labels"] = flow_list(value) if value else []
            elif key in {"name", "description", "title"}:
                doc[key] = unquote(value)
            field, context = None, None
            continue

        if key == "labels" and line.startswith("- "):
            doc["labels"].append(unquote(line[2:]))
            continue

        if key != "body":
            continue

        if indent == 2 and line.startswith("- "):
            field = {"type": "", "id": "", "label": "", "required": False}
            doc["fields"].append(field)
            context = None
            name, _, value = line[2:].strip().partition(":")
            if name.strip() == "type":
                field["type"] = unquote(value)
            continue

        if field is None:
            continue

        if indent == 4:
            name, _, value = line.partition(":")
            name = name.strip()
            context = name if name in {"attributes", "validations"} else None
            if name == "id":
                field["id"] = unquote(value)
            elif name == "type":
                field["type"] = unquote(value)
            continue

        if indent == 6 and context == "attributes":
            name, _, value = line.partition(":")
            if name.strip() == "label":
                field["label"] = unquote(value)
            continue

        if indent == 6 and context == "validations":
            name, _, value = line.partition(":")
            if name.strip() == "required":
                field["required"] = unquote(value) == "true"
            continue
        # indent >= 8 is block scalars, option lists and nested notes: nothing a
        # prefill link cares about.

    return doc


def load_templates() -> list[dict]:
    """Parse every form, and refuse to link to one that cannot carry a prefill."""
    if not TEMPLATE_DIR.is_dir():
        fail(f"missing {TEMPLATE_DIR.relative_to(ROOT)}")
    paths = sorted(p for p in TEMPLATE_DIR.glob("*.yml") if p.name != "config.yml")
    if not paths:
        fail("no issue forms found in .github/ISSUE_TEMPLATE")

    templates: list[dict] = []
    for path in paths:
        doc = parse_template(path)
        seen: set[str] = set()
        for missing in ("name", "description", "title"):
            if not doc[missing]:
                fail(f"{doc['file']}: `{missing}:` is empty or unparseable")
        if not doc["labels"]:
            fail(f"{doc['file']}: no `labels:` — a prefill link cannot promise a label")
        for field in doc["fields"]:
            if field["type"] == "markdown":
                continue
            if not field["id"]:
                fail(f"{doc['file']}: {field['type'] or '?'} field has no `id:`, so a "
                     "prefill link has nothing to name")
            if not FIELD_ID.match(field["id"]):
                fail(f"{doc['file']}: field id {field['id']!r} is not valid in GitHub's "
                     "form schema (alpha-numeric, `-` and `_` only), so it could never "
                     "be filled from a URL")
            if field["id"] in RESERVED_KEYS:
                fail(f"{doc['file']}: field id {field['id']!r} shadows a GitHub query key")
            # ids are only shared across forms on purpose (`app_name` means the
            # same thing in both); a repeat inside one form is an authoring bug.
            if field["id"] in seen:
                fail(f"{doc['file']}: field id {field['id']!r} is used twice")
            seen.add(field["id"])
        doc["prefillable"] = [f["id"] for f in doc["fields"]
                              if f["type"] in PREFILLABLE_TYPES and f["id"]]
        doc["not_prefillable"] = [f["id"] for f in doc["fields"]
                                  if f["type"] not in PREFILLABLE_TYPES and f["id"]]
        if not doc["prefillable"]:
            fail(f"{doc['file']}: nothing to prefill — no input/textarea fields")
        templates.append(doc)
    return templates


def prefill_url(template: dict, values: dict[str, str] | None = None,
                title: str | None = None) -> str:
    """The canonical shape GitHub's own "Get started" link uses."""
    params: list[tuple[str, str]] = [
        ("assignees", ""),
        ("labels", ",".join(template["labels"])),
        ("projects", ""),
        ("template", template["file"]),
        ("title", template["title"] if title is None else title),
    ]
    for key, value in (values or {}).items():
        if key not in template["prefillable"]:
            fail(f"{template['file']}: `{key}` is not a prefillable field "
                 f"(prefillable: {', '.join(template['prefillable'])}; GitHub "
                 "ignores dropdowns, checkboxes and markdown)")
        params.append((key, value))
    url = f"{ISSUES_NEW}?{urlencode(params)}"
    if len(url) > MAX_URL:
        fail(f"prefill link is {len(url)} chars — GitHub answers 414 URI Too Long, "
             "shorten the note/component values")
    return url


def resolve(templates: list[dict], wanted: str) -> dict:
    for template in templates:
        if wanted in {template["file"], template["slug"]}:
            return template
    matches = [t for t in templates if wanted in t["slug"] or wanted in t["name"]]
    if len(matches) == 1:
        return matches[0]
    fail(f"unknown template {wanted!r} (choose one of: "
         f"{', '.join(t['file'] for t in templates)})")


def cell(text: str) -> str:
    """Table-cell safe: a form label with a `|` would otherwise cut the row."""
    return text.replace("|", "\\|")


def label_for(template: dict, field_id: str) -> str:
    for field in template["fields"]:
        if field["id"] == field_id:
            # "\*" so the asterisk renders literally inside a table cell.
            marker = "\\*" if field["required"] else ""
            label = cell(field["label"]) or f"`{field['id']}`"
            return f"{label}{marker}"
    return f"`{field_id}`"


def sample_form(templates: list[dict]) -> dict:
    """The form the README walks through: the mis-mapping one, if it exists."""
    return next((t for t in templates if "not_applying" in t["slug"]), templates[0])


def block(templates: list[dict]) -> str:
    """The README block between the issue-prefills markers."""
    form = sample_form(templates)
    values = {key: value for key, value in SAMPLE.items() if key in form["prefillable"]}
    app = values.get("app_name", "Stremio")
    parts = [f"--app {app}", f"--template {form['file']}"]
    if "component" in values:
        parts.append(f"--component {values['component']}")
    flags = " \\\n    ".join(parts)
    sample_url = prefill_url(form, values, title=form["title"] + app)

    lines = [
        START,
        SECTION_HEADING,
        "",
        "Both issue forms are deep-linked: a report opens on the right template with",
        "the right title and label already set — no chooser, no retyping the prefix.",
        "Field values ride on the same URL. Only `input` and `textarea` fields accept",
        "a prefill, so every required dropdown and confirm box is still answered by",
        "the reporter — a link can never tick a gate for them.",
        "",
        "| Open | Use it when | A link can prefill | Only in the form | Title / label |",
        "|---|---|---|---|---|",
    ]
    for template in templates:
        filled = " · ".join(label_for(template, fid) for fid in template["prefillable"])
        blocked = " · ".join(label_for(template, item)
                              for item in template["not_prefillable"]) or "—"
        lines.append(
            f"| [{template['name']}]({prefill_url(template)}) "
            f"| {cell(template['description'])} "
            f"| {filled} | {blocked} | `{template['title'].strip()}` · "
            f"`{', '.join(template['labels'])}` |"
        )
    lines += [
        "",
        "One app per issue, and check [docs/IconPackList.md](docs/IconPackList.md) by",
        "name, drawable and package first — a listed app that isn't applying belongs",
        "on the other form.",
        "",
        "For one specific app, let the generator build the link you paste into a reply:",
        "",
        "```bash",
        f"python tools/build_issue_prefills.py {flags}",
        "```",
        "",
        "```text",
        sample_url,
        "```",
        "",
        "Generated from `.github/ISSUE_TEMPLATE/` by `tools/build_issue_prefills.py`.",
        "Edit the forms, re-run the generator — `--check` fails this block on drift.",
        END,
    ]
    return "\n".join(lines)


def readme_with_block(templates: list[dict]) -> str:
    readme = README.read_text(encoding="utf-8")
    stamped = block(templates)
    if START in readme and END in readme:
        before, rest = readme.split(START, 1)
        _, after = rest.split(END, 1)
        return before + stamped + after
    anchor = "> **Note:** Designed and mapped against"
    if anchor not in readme:
        fail("README anchor line not found and no issue-prefills markers present — "
             f"place {START} / {END} in the Icon Pack section by hand first")
    line_end = readme.index("\n", readme.index(anchor))
    # The anchor line is followed by the section's own rule, so the stamp only
    # needs its own blank lines around it.
    return readme[:line_end + 1] + "\n" + stamped + "\n" + readme[line_end + 1:]


def check_readme(templates: list[dict]) -> None:
    readme = README.read_text(encoding="utf-8")
    if START not in readme or END not in readme:
        fail("README is missing the issue-prefills stamp; run "
             "python tools/build_issue_prefills.py")
    current = readme[readme.index(START):readme.index(END) + len(END)]
    # Before the generic staleness error: a renamed heading shows up as a dead
    # `(#…)` link in the prose above, and that is the more useful complaint.
    if SECTION_ANCHOR in readme.replace(current, "") and SECTION_HEADING not in current:
        fail(f"README links to {SECTION_ANCHOR} but the stamp no longer has that heading")
    if current != block(templates):
        fail("README icon-request links are stale; run "
             "python tools/build_issue_prefills.py and commit the block")
    prefills = sum(len(t["prefillable"]) for t in templates)
    print(f"icon-request stamp matches {len(templates)} issue forms · "
          f"{prefills} prefillable fields")


def field_pairs(args: argparse.Namespace) -> dict[str, str]:
    """`--app`/`--component`/`--field` as one {form field id: value} map."""
    pairs: dict[str, str] = {}
    for flag, field_id in ALIASES.items():
        if getattr(args, flag):
            pairs[field_id] = getattr(args, flag)
    for item in args.field:
        key, sep, value = item.partition("=")
        if not sep:
            fail(f"--field wants id=value, got {item!r}")
        pairs[key] = value
    return pairs


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Build the README's prefilled icon-request issue links "
                    "from .github/ISSUE_TEMPLATE, or print a prefill link for "
                    "one app.")
    parser.add_argument("--check", action="store_true",
                        help="fail if the README stamp does not match the forms")
    parser.add_argument("--print", dest="print_only", action="store_true",
                        help="write the block to stdout instead of README.md")
    parser.add_argument("--list", action="store_true",
                        help="show the parsed forms and their prefillable fields")
    parser.add_argument("--json", action="store_true",
                        help="dump the manifest other generators can read")
    parser.add_argument("--app", help="app name; also completes the issue title")
    parser.add_argument("--template", help="issue form file or slug (default: first)")
    parser.add_argument("--component", help="package/activity, when you have it")
    parser.add_argument("--field", action="append", default=[], metavar="ID=VALUE",
                        help="prefill one more text field by form id (repeatable); "
                             "--set is accepted as the same flag")
    parser.add_argument("--set", dest="set_", action="append", default=[],
                        metavar="ID=VALUE", help=argparse.SUPPRESS)
    args = parser.parse_args(argv)
    args.field = args.field + args.set_

    templates = load_templates()

    if args.json:
        print(json.dumps([{**t, "url": prefill_url(t)} for t in templates], indent=2))
        return 0
    if args.list:
        for template in templates:
            print(f"{template['file']}  {template['name']}")
            print(f"  title {template['title']!r} · labels {template['labels']}")
            print(f"  prefillable: {', '.join(template['prefillable'])}")
            print(f"  not via link: {', '.join(template['not_prefillable']) or '—'}")
        return 0

    if args.app or args.component or args.field:
        template = resolve(templates, args.template) if args.template else templates[0]
        values = field_pairs(args)
        print(prefill_url(template, values,
                          title=(template["title"] + args.app) if args.app else None))
        if args.app and "app_name" not in values:
            # The title still carries the app, but say so instead of letting a
            # silent no-op look like a filled-in field.
            print("note: this form has no `app_name` field, so only the title "
                  f"carries the name ({template['file']})", file=sys.stderr)
        return 0

    if args.check:
        check_readme(templates)
        return 0
    if args.print_only:
        print(block(templates))
        return 0
    if args.template:
        print(prefill_url(resolve(templates, args.template)))
        return 0

    README.write_text(readme_with_block(templates), encoding="utf-8")
    print(f"README icon-request block written from {len(templates)} issue forms "
          f"· {sum(len(t['prefillable']) for t in templates)} prefillable fields")
    return 0


if __name__ == "__main__":
    sys.exit(main())
