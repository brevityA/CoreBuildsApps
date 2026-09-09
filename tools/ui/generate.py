"""Orchestration: spec -> staged files -> (optionally) the app tree.

Staging (`generate`) writes everything under an output dir for review:

  out/
    res/layout/activity_<screen>.xml   (+ item_<screen>.xml for custom grids)
    res/values/strings_<screen>.xml    (new strings only)
    java/<Screen>Activity.kt           (+ <Screen>Adapter.kt for custom grids)
    manifest_snippet.xml
    spec.json

Applying (`apply`) copies the staged files into app/, merges the strings
into values/strings.xml, declares the Activity in the manifest, and re-runs
the app -> pop mirror so both packs compile the new screen.
"""
from __future__ import annotations

import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent

from .render import (compute_links, manifest_entry, render_activity,
                     render_adapter, render_item_layout, render_layout,
                     render_strings)
from .spec import Spec, default_initial_focus, normalise

sys.path.insert(0, str(ROOT / "tools"))
from validate_ui import audit_layout  # noqa: E402


def stage(spec: Spec, outdir: Path) -> dict:
    """Render every file for a normalised spec into outdir. Returns receipt."""
    links = compute_links(spec)
    initial = spec.dpad.get("initialFocus") or default_initial_focus(spec)
    custom_grid = any(b["type"] == "grid" and b.get("source") == "custom"
                      for b in spec.blocks)

    files: dict[str, str] = {
        f"res/layout/{spec.layout}.xml": render_layout(spec, links),
        "res/values/strings_%s.xml" % spec.prefix: render_strings(spec),
        f"java/{spec.activity}.kt": render_activity(spec, links),
        "manifest_snippet.xml": manifest_entry(spec),
        "spec.json": json.dumps({
            "screen": spec.screen, "package": spec.package,
            "blocks": spec.blocks, "dpad": spec.dpad, "anim": spec.anim,
            "extracted_strings": spec.extracted,
        }, indent=2) + "\n",
    }
    if custom_grid:
        files[f"res/layout/item_{spec.prefix}.xml"] = render_item_layout(spec)
        files[f"java/{spec.screen}Adapter.kt"] = render_adapter(spec)

    if outdir.exists():
        for stale in outdir.rglob("*"):
            if stale.is_file():
                stale.unlink()
    for rel, content in files.items():
        dest = outdir / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content, encoding="utf-8")

    kotlin = files[f"java/{spec.activity}.kt"]
    audits = []
    for rel in files:
        if rel.startswith("res/layout/"):
            audits.append(audit_layout(outdir / rel, kotlin=kotlin,
                                       initial=initial if "activity_" in rel else None))
    errors = [f"{a.path.name}: {msg}" for a in audits for msg in a.errors]
    warnings = [f"{a.path.name}: {msg}" for a in audits for msg in a.warnings]
    focusable = sum(a.stats.get("focusable", 0) for a in audits)
    nlinks = sum(a.stats.get("links", 0) for a in audits)
    return {
        "files": sorted(files),
        "errors": errors,
        "warnings": warnings,
        "focusable": focusable,
        "links": nlinks,
        "initial": initial,
    }


def _merge_strings(app_res: Path, names_values: dict[str, str]) -> tuple[int, int]:
    """Merge new strings into app values/strings.xml. Returns (added, skipped)."""
    path = app_res / "values" / "strings.xml"
    text = path.read_text(encoding="utf-8")
    try:
        root = ET.fromstring(text)
    except ET.ParseError as e:
        raise SystemExit(f"cannot parse {path}: {e}")
    existing = {c.get("name") for c in root if c.tag == "string"}
    fresh = {k: v for k, v in names_values.items() if k not in existing}
    skipped = len(names_values) - len(fresh)
    if not fresh:
        return 0, skipped
    from xml.sax.saxutils import escape
    entities = {'"': "&quot;", "'": "&apos;"}
    block = "".join(
        '    <string name="%s">%s</string>\n' % (k, escape(v, entities))
        for k, v in sorted(fresh.items())
    )
    marker = "</resources>"
    idx = text.rfind(marker)
    if idx < 0:
        raise SystemExit(f"cannot merge strings: no </resources> in {path}")
    path.write_text(text[:idx] + block + text[idx:], encoding="utf-8")
    return len(fresh), skipped


def _insert_manifest(app_main: Path, entry: str, activity: str) -> bool:
    """Declare the Activity. Returns False if it was already declared."""
    path = app_main / "AndroidManifest.xml"
    text = path.read_text(encoding="utf-8")
    if f'android:name=".{activity}"' in text:
        return False
    marker = "    </application>"
    idx = text.find(marker)
    if idx < 0:
        raise SystemExit(f"cannot patch manifest: no {marker!r} in {path}")
    path.write_text(text[:idx] + entry + text[idx:], encoding="utf-8")
    return True


def apply(spec: Spec, outdir: Path, force: bool = False,
          mirror: bool = True) -> dict:
    """Stage, copy into app/, merge strings, patch manifest, mirror to pop."""
    receipt = stage(spec, outdir)
    if receipt["errors"]:
        raise SystemExit("refusing to apply: staged layout has audit errors:\n  "
                         + "\n  ".join(receipt["errors"]))

    app_main = ROOT / "app" / "src" / "main"
    java_dir = app_main / "java" / spec.package.replace(".", "/")
    java_dir.mkdir(parents=True, exist_ok=True)

    copies = []
    for rel in receipt["files"]:
        if rel.startswith("res/layout/"):
            copies.append((outdir / rel, app_main / "res" / "layout" / Path(rel).name))
        elif rel.startswith("java/"):
            copies.append((outdir / rel, java_dir / Path(rel).name))
    if not force:
        clashes = [str(dst) for _, dst in copies if dst.exists()]
        if clashes:
            raise SystemExit(
                "refusing to overwrite existing files (use --force):\n  "
                + "\n  ".join(clashes))
    for src, dst in copies:
        dst.write_text(src.read_text(encoding="utf-8"), encoding="utf-8")

    strings = dict(spec.extra_strings)
    strings.update(spec.extracted)
    added, skipped = _merge_strings(app_main / "res", strings)
    manifest_done = _insert_manifest(
        app_main, manifest_entry(spec), spec.activity)

    mirrored = False
    mirror_note = "skipped (--no-mirror)"
    if mirror:
        proc = subprocess.run(
            [sys.executable, str(ROOT / "tools" / "build_pop.py"), "--mirror-only"],
            capture_output=True, text=True, cwd=ROOT)
        if proc.returncode == 0:
            mirrored = True
            mirror_note = "pop mirror refreshed"
        else:
            mirror_note = ("MIRROR FAILED — run python tools/build_pop.py by hand:\n"
                           + (proc.stderr or proc.stdout).strip())

    receipt.update({
        "copied": [str(dst.relative_to(ROOT)) for _, dst in copies],
        "strings_added": added,
        "strings_skipped": skipped,
        "manifest": manifest_done,
        "mirrored": mirrored,
        "mirror_note": mirror_note,
    })
    return receipt


def build(raw: dict, outdir: Path) -> tuple[Spec, dict]:
    spec = normalise(raw)
    return spec, stage(spec, outdir)
