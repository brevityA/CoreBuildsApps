#!/usr/bin/env python3
"""
Core Builds Icon Pack — scan reporter.

Takes the raw output of tools/scan_device.sh and answers three questions:

  1. Which installed apps does the pack already cover?
  2. Which are covered, but mapped to a DIFFERENT component than this device
     actually uses? (the silent-failure case — an icon that never applies)
  3. Which installed apps have no icon at all, ranked so the useful ones
     come first?

Also emits ready-to-paste catalog entries for the gaps.

Run standalone against an existing scan:
    python tools/import_scan.py --json tools/device_scan.json --report
"""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "tools" / "catalog.json"

# Packages that are never worth an icon: they have no launcher presence a user
# cares about, or they are the pack/launcher itself.
SKIP_PREFIXES = (
    "com.android.", "com.google.android.gms", "com.google.android.gsf",
    "com.google.android.tts", "com.google.android.webview",
    "android.autoinstalls", "com.android.cts",
)
SKIP_EXACT = {"tv.corebuilds.iconpack"}


def norm(component: str) -> str:
    """
    Normalise a component to pkg/fully.qualified.Activity.

    ADB prints the shorthand form (pkg/.Activity) and the catalog carries both,
    so comparisons must expand the leading dot or genuine matches look like
    mismatches.
    """
    component = component.strip()
    if "/" not in component:
        return component
    pkg, act = component.split("/", 1)
    if act.startswith("."):
        act = pkg + act
    return f"{pkg}/{act}"


def load_catalog():
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_component, by_package = {}, {}
    for icon in data["icons"]:
        for comp in icon["components"]:
            by_component[norm(comp)] = icon
            by_package.setdefault(comp.split("/", 1)[0], icon)
    return data, by_component, by_package


def parse_raw(path: Path):
    rows = []
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        while len(parts) < 4:
            parts.append("")
        pkg, comp, leanback, ver = parts[:4]
        # The leanback activity is what a TV launcher actually starts, so it
        # wins when present.
        chosen = leanback.strip() or comp.strip()
        if not chosen or "/" not in chosen:
            continue
        rows.append({
            "package": pkg.strip(),
            "component": chosen,
            "resolved": comp.strip(),
            "leanback": leanback.strip(),
            "version": ver.strip(),
        })
    return rows


def interesting(pkg: str, in_catalog: bool = False) -> bool:
    # Never hide an app the catalog already claims — otherwise a mismatch on a
    # system app (Settings, Play Store) is filtered out of the very report
    # that exists to surface it.
    if in_catalog:
        return True
    if pkg in SKIP_EXACT:
        return False
    return not any(pkg.startswith(p) for p in SKIP_PREFIXES)


# Trailing segments that carry no identity — "com.weirdapp.tv" should suggest
# "weirdapp_tv", not "tv".
GENERIC_TAIL = {
    "tv", "app", "android", "androidtv", "mobile", "client", "player",
    "free", "pro", "plus", "beta", "main", "ui", "leanback", "google",
}


# TLD/vanity segments that never carry identity, at either end.
NOISE = {"com", "net", "org", "io", "co", "au", "uk", "us", "ca", "de", "tv2"}


def slug(pkg: str) -> str:
    """
    Suggest a drawable name from a package. Best-effort only — a human renames
    it. Must always return a valid [a-z][a-z0-9_]* token.
    """
    parts = [re.sub(r"[^a-z0-9]+", "_", p.lower()).strip("_")
             for p in pkg.split(".")]
    parts = [p for p in parts if p]
    # Drop leading TLD-ish noise (au.com.stan.and -> stan.and)
    while len(parts) > 1 and parts[0] in NOISE:
        parts.pop(0)
    # Drop trailing noise that isn't the whole name (stan.and -> stan)
    while len(parts) > 1 and (parts[-1] in NOISE or len(parts[-1]) <= 2):
        parts.pop()
    if not parts:
        return "app"

    # The brand is usually the longest segment that isn't a generic word
    # ("stan.and" -> stan, "stremio.one" -> stremio, "abc.iview" -> iview is
    # equally fine). Ties go to the later segment, which is more specific.
    candidates = [p for p in parts if p not in GENERIC_TAIL]
    if candidates:
        best = max(candidates, key=lambda p: (len(p), parts.index(p)))
    else:
        best = parts[-1]

    # Keep a meaningful qualifier when the brand alone is ambiguous.
    if best in GENERIC_TAIL and len(parts) >= 2:
        best = f"{parts[-2]}_{best}"

    best = re.sub(r"_+", "_", best).strip("_")
    if not best:
        return "app"
    # Drawable names must start with a letter.
    if best[0].isdigit():
        best = "a" + best
    return best


def display_name(pkg: str) -> str:
    base = slug(pkg).replace("_", " ").title()
    return base


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw")
    ap.add_argument("--json", default="tools/device_scan.json")
    ap.add_argument("--txt", default="tools/device_scan.txt")
    ap.add_argument("--model", default="unknown")
    ap.add_argument("--api", default="?")
    ap.add_argument("--report", action="store_true",
                    help="re-report from an existing --json scan")
    args = ap.parse_args()

    json_path = ROOT / args.json

    if args.report:
        payload = json.loads(json_path.read_text(encoding="utf-8"))
        rows = payload["apps"]
        model, api = payload.get("model", "?"), payload.get("api", "?")
    else:
        if not args.raw:
            ap.error("--raw is required unless --report is used")
        rows = parse_raw(Path(args.raw))
        model, api = args.model, args.api

    data, by_component, by_package = load_catalog()

    covered, mismatched, missing = [], [], []

    for r in rows:
        comp = norm(r["component"])
        icon = by_component.get(comp)
        known = icon is not None or r["package"] in by_package
        if not interesting(r["package"], in_catalog=known):
            continue
        if icon:
            covered.append((r, icon))
            continue
        pkg_icon = by_package.get(r["package"])
        if pkg_icon:
            # The app IS in the catalog, but this device launches a different
            # activity — the icon will silently never apply here.
            mismatched.append((r, pkg_icon))
        else:
            missing.append(r)

    missing.sort(key=lambda r: r["package"])

    # ---- machine-readable -------------------------------------------------
    payload = {
        "model": model,
        "api": api,
        "counts": {
            "scanned": len(rows),
            "covered": len(covered),
            "mismatched": len(mismatched),
            "missing": len(missing),
        },
        "apps": rows,
        "missing": missing,
        "mismatched": [
            {"package": r["package"], "device_component": r["component"],
             "catalog_name": i["name"],
             "catalog_components": i["components"]}
            for r, i in mismatched
        ],
    }
    json_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    # ---- human-readable ---------------------------------------------------
    out = []
    out.append(f"Device scan — {model} (API {api})")
    out.append("=" * 64)
    out.append(f"launchable apps scanned : {len(rows)}")
    out.append(f"covered by the pack     : {len(covered)}")
    out.append(f"MISMATCHED components   : {len(mismatched)}")
    out.append(f"no icon yet             : {len(missing)}")
    out.append("")

    if mismatched:
        out.append("MISMATCHED — the pack has this app, but maps a different")
        out.append("activity than this device uses. The icon will NOT apply.")
        out.append("Fix: add the device component to that icon in catalog.json.")
        out.append("-" * 64)
        for r, i in mismatched:
            out.append(f"  {i['name']}")
            out.append(f"    device : {r['component']}")
            out.append(f"    catalog: {', '.join(i['components'])}")
        out.append("")

    if covered:
        out.append(f"COVERED ({len(covered)})")
        out.append("-" * 64)
        for r, i in sorted(covered, key=lambda x: x[1]["name"].lower()):
            out.append(f"  {i['name']:<28} {r['component']}")
        out.append("")

    if missing:
        out.append(f"NO ICON YET ({len(missing)})")
        out.append("-" * 64)
        for r in missing:
            v = f"  v{r['version']}" if r["version"] else ""
            out.append(f"  {r['package']}{v}")
            out.append(f"    {r['component']}")
        out.append("")
        out.append("Paste-ready catalog entries (set name/color/glyph yourself):")
        out.append("-" * 64)
        entries = []
        for r in missing:
            entries.append({
                "name": display_name(r["package"]),
                "drawable": slug(r["package"]),
                "color": "#00D4FF",
                "glyph": "play_round",
                "components": [r["component"]],
            })
        out.append(json.dumps(entries, indent=2))

    text = "\n".join(out) + "\n"
    (ROOT / args.txt).write_text(text, encoding="utf-8")

    print(text[:4000])
    if len(text) > 4000:
        print(f"... truncated — full report in {args.txt}")
    print(f"\nWrote {args.json} and {args.txt}")
    if mismatched:
        print(f"\n{len(mismatched)} mismatched component(s) — those icons are "
              f"silently not applying on this device.")


if __name__ == "__main__":
    main()
