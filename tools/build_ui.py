#!/usr/bin/env python3
"""Core Builds UI Studio — generate TV screens for the icon packs.

  python tools/build_ui.py --list-presets
  python tools/build_ui.py --preset browser --name Showcase
  python tools/build_ui.py --preset details --name AppDetails --title Stremio
  python tools/build_ui.py --spec my_screen.json --out /tmp/ui
  python tools/build_ui.py --preset settings --name PackSettings --apply

Without --apply, files land under tools/ui/out/ for review. With --apply they
are copied into app/, strings are merged, the manifest is patched, and the
app -> pop mirror is refreshed so both packs compile the screen.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

from ui.generate import apply, build  # noqa: E402
from ui.spec import list_presets, load_preset  # noqa: E402

DEFAULT_OUT = ROOT / "tools" / "ui" / "out"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Generate a D-pad-correct TV screen for the icon packs.")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--preset", help="starter screen (see --list-presets)")
    group.add_argument("--spec", help="path to a screen spec JSON file")
    group.add_argument("--list-presets", action="store_true")
    parser.add_argument("--name", help='override the screen name, e.g. "Showcase"')
    parser.add_argument("--title", help="override the header title text")
    parser.add_argument("--out", default=str(DEFAULT_OUT),
                        help="staging dir (default: tools/ui/out)")
    parser.add_argument("--apply", action="store_true",
                        help="copy into app/, merge strings, patch manifest")
    parser.add_argument("--force", action="store_true",
                        help="overwrite existing files on --apply")
    parser.add_argument("--no-mirror", action="store_true",
                        help="skip the app -> pop mirror on --apply")
    args = parser.parse_args(argv)

    if args.list_presets:
        for preset in list_presets():
            raw = load_preset(preset)
            blocks = ", ".join(b["type"] for b in raw["blocks"])
            print(f"  {preset:12} {raw['screen']:14} [{blocks}]")
        return 0

    try:
        raw = load_preset(args.preset) if args.preset else json.loads(
            Path(args.spec).read_text(encoding="utf-8"))
        if args.name:
            raw["screen"] = args.name
        if args.title:
            for block in raw.get("blocks", []):
                if block.get("type") == "header":
                    block["title"] = args.title
                    break
        outdir = Path(args.out)
        if args.apply:
            from ui.spec import normalise
            receipt = apply(normalise(raw), outdir, force=args.force,
                            mirror=not args.no_mirror)
            print(f"staged + applied {raw['screen']} "
                  f"({receipt['focusable']} focusable, {receipt['links']} links, "
                  f"initial @{receipt['initial']})")
            for dst in receipt["copied"]:
                print(f"  wrote {dst}")
            print(f"  strings: {receipt['strings_added']} added, "
                  f"{receipt['strings_skipped']} already present")
            print("  manifest: declared" if receipt["manifest"]
                  else "  manifest: already declared")
            print(f"  pop: {receipt['mirror_note']}")
        else:
            spec, receipt = build(raw, outdir)
            print(f"staged {spec.activity} in {outdir} "
                  f"({receipt['focusable']} focusable, {receipt['links']} links, "
                  f"initial @{receipt['initial']})")
            for rel in receipt["files"]:
                print(f"  {rel}")
        for warn in receipt["warnings"]:
            print(f"  warn: {warn}")
        if receipt["errors"]:
            print("  ERRORS:")
            for err in receipt["errors"]:
                print(f"    {err}")
            return 1
        if not args.apply:
            print("\nreview, then re-run with --apply to install into app/")
        return 0
    except ValueError as e:
        print(f"error: {e}")
        return 2
    except SystemExit as e:
        print(f"error: {e}")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
